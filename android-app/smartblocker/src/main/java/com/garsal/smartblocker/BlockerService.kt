package com.garsal.smartblocker

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class BlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastSupabaseCheckMs = 0L
    private var blockWm: BlockWindowManager? = null

    private val checker = object : Runnable {
        override fun run() {
            checkSnooze()
            checkSupabaseQueueIfDue()
            handler.postDelayed(this, Config.CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartBlocker::WakeLock")
        wakeLock?.acquire()

        handler.post(checker)
        scheduleNextFallback()
        AppLogger.log(this, "SERVICE", "onCreate — servizio avviato, fallback alarm schedulato")

        if (Prefs.getDeviceToken(this).isEmpty()) {
            Thread {
                SupabaseApi(this).fetchAndCacheDeviceToken()
                AppLogger.log(this, "SERVICE", "device token recuperato: ${Prefs.getDeviceToken(this).take(8)}…")
            }.start()
        } else {
            AppLogger.log(this, "SERVICE", "device token presente: ${Prefs.getDeviceToken(this).take(8)}…")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE        -> handleSnooze()
            ACTION_UNBLOCK       -> handleUnblock()
            ACTION_CHECK_NOW     -> {
                AppLogger.log(this, "SERVICE", "ACTION_CHECK_NOW ricevuto (stato=${Prefs.getState(this)})")
                checkSnooze()
                if (Prefs.getState(this) == Prefs.STATE_NONE) {
                    lastSupabaseCheckMs = 0L
                    triggerSupabaseCheck()
                } else {
                    AppLogger.log(this, "SERVICE", "snooze scaduto → overlay mostrato, skip query Supabase")
                    BlockAlarmReceiver.releaseWakeLock()
                }
            }
            ACTION_SHOW_OVERLAY  -> showOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AppLogger.log(this, "SERVICE", "onDestroy — servizio distrutto (verrà riavviato da START_STICKY)")
        handler.removeCallbacks(checker)
        wakeLock?.release()
        blockWm?.dismiss()
        blockWm = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Logica principale ────────────────────────────────────────────────────

    private fun checkSnooze() {
        val now = System.currentTimeMillis()
        val state = Prefs.getState(this)
        val snoozeUntil = Prefs.getSnoozeUntil(this)
        if (state == Prefs.STATE_NONE && snoozeUntil > 0 && now >= snoozeUntil) {
            AppLogger.log(this, "SNOOZE", "snooze scaduto → mostro overlay")
            Prefs.setSnoozeUntil(this, 0)
            val count = Prefs.getSnoozeCount(this)
            val nextState = if (count >= Config.MAX_SNOOZES) Prefs.STATE_LOCKED else Prefs.STATE_TRIGGERED
            Prefs.setState(this, nextState)
            showOverlay()
        }
    }

    private fun handleSnooze() {
        val count = Prefs.getSnoozeCount(this)
        if (count < Config.MAX_SNOOZES) {
            val snoozeUntil = System.currentTimeMillis() + Config.SNOOZE_DURATION_MS
            Prefs.setSnoozeCount(this, count + 1)
            Prefs.setSnoozeUntil(this, snoozeUntil)
            Prefs.setState(this, Prefs.STATE_NONE)
            scheduleSnoozeAlarm(snoozeUntil)
            AppLogger.log(this, "SNOOZE", "rinviato (${count + 1}/${Config.MAX_SNOOZES}) — alarm snooze schedulato")
        } else {
            AppLogger.log(this, "SNOOZE", "rinvii esauriti, nessun alarm schedulato")
        }
        blockWm?.dismiss()
        blockWm = null
    }

    private fun handleUnblock() {
        Prefs.setState(this, Prefs.STATE_NONE)
        Prefs.setSnoozeCount(this, 0)
        Prefs.setSnoozeUntil(this, 0)
        Prefs.clearBlockEntities(this)
        Prefs.clearBlockTitle(this)
        Prefs.clearBlockDate(this)
        blockWm?.dismiss()
        blockWm = null
        cancelBlockNotification()
    }

    private fun checkSupabaseQueueIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastSupabaseCheckMs < 5 * 60 * 1000L) return
        lastSupabaseCheckMs = now
        triggerSupabaseCheck()
    }

    fun triggerSupabaseCheck() {
        if (Prefs.getState(this) != Prefs.STATE_NONE) {
            BlockAlarmReceiver.releaseWakeLock()
            return
        }
        AppLogger.log(this, "SUPABASE", "inizio query coda…")
        Thread {
            val result = SupabaseApi(this).queryQueue()
            if (result.errorMsg != null) {
                AppLogger.log(this, "SUPABASE", "ERRORE: ${result.errorMsg} (HTTP ${result.httpCode})")
                scheduleNextFallback()
                BlockAlarmReceiver.releaseWakeLock()
                return@Thread
            }
            AppLogger.log(this, "SUPABASE", "HTTP ${result.httpCode} — righe=${result.entries.size} due=${result.dueIds.size} nextFireAt=${result.nextFireAtMs?.let { java.util.Date(it) } ?: "nessuno"}")
            val dueIds = result.dueIds
            if (dueIds.isNotEmpty()) {
                AppLogger.log(this, "SUPABASE", "blocchi pronti: ${dueIds.joinToString()} — mostro overlay")
                dueIds.forEach { SupabaseApi(this).markSent(it) }
                val blockEntities = result.entries
                    .filter { it.id in dueIds && it.entityId.isNotBlank() }
                    .map { BlockedEntity(it.app, it.entityId) }
                val blockTitle = result.entries
                    .filter { it.id in dueIds }
                    .joinToString(" · ") { it.title.take(40) }
                val blockDate = result.entries
                    .firstOrNull { it.id in dueIds && it.fireAt.isNotBlank() }
                    ?.fireAt?.take(10) ?: ""
                handler.post {
                    Prefs.setBlockEntities(this, blockEntities)
                    Prefs.setBlockTitle(this, blockTitle)
                    Prefs.setBlockDate(this, blockDate)
                    Prefs.setState(this, Prefs.STATE_TRIGGERED)
                    Prefs.setSnoozeCount(this, 0)
                    Prefs.setSnoozeUntil(this, 0)
                    showOverlay()
                    BlockAlarmReceiver.releaseWakeLock()
                }
            } else {
                if (result.nextFireAtMs != null) {
                    AppLogger.log(this, "SUPABASE", "nessun blocco ora — alarm esatto per ${java.util.Date(result.nextFireAtMs)}")
                    scheduleExactAlarm(result.nextFireAtMs)
                } else {
                    AppLogger.log(this, "SUPABASE", "nessun blocco in coda")
                }
                scheduleNextFallback()
                BlockAlarmReceiver.releaseWakeLock()
            }
        }.start()
    }

    /**
     * Schedula un alarm che sveglia il dispositivo all'orario esatto, anche in Doze mode.
     * Su API 31+ usa setExactAndAllowWhileIdle se il permesso è concesso,
     * altrimenti fallback a setAndAllowWhileIdle (può avere ritardo fino a ~15 min).
     */
    private fun scheduleExactAlarm(fireAtMs: Long) {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(this, BlockAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, SupabaseApi.ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
            Log.w(TAG, "Permesso alarm esatto non concesso — uso alarm impreciso (ritardo possibile)")
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
            Log.d(TAG, "Alarm esatto schedulato per ${java.util.Date(fireAtMs)}")
        }
    }

    /**
     * Alarm periodico di fallback ogni FALLBACK_INTERVAL_MS (10 min).
     * Sopravvive al kill del servizio (gestito dall'OS) e funziona in Doze mode.
     * Garantisce che il servizio si risvegli anche se non era riuscito a schedulare
     * l'alarm esatto per il blocco.
     */
    private fun scheduleNextFallback() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            this, FALLBACK_ALARM_CODE,
            Intent(this, BlockAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + FALLBACK_INTERVAL_MS, pi)
        Log.d(TAG, "Fallback alarm schedulato tra ${FALLBACK_INTERVAL_MS / 60000} min")
    }

    /**
     * Alarm esatto per la scadenza dello snooze (request code separato da ALARM_REQUEST_CODE
     * per non sovrascrivere l'alarm del blocco originale).
     */
    private fun scheduleSnoozeAlarm(fireAtMs: Long) {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            this, SNOOZE_ALARM_CODE,
            Intent(this, BlockAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
        }
        Log.d(TAG, "Snooze alarm schedulato per ${java.util.Date(fireAtMs)}")
    }

    /**
     * Mostra la schermata di blocco tramite WindowManager (TYPE_APPLICATION_OVERLAY).
     * Richiede solo SYSTEM_ALERT_WINDOW — nessuna notifica, nessun permesso extra.
     * L'overlay appare sopra qualsiasi app e sopra la lock screen.
     */
    private fun showOverlay() {
        if (blockWm?.isShowing() == true) {
            AppLogger.log(this, "OVERLAY", "già visibile, skip")
            return
        }
        AppLogger.log(this, "OVERLAY", "show() — overlay=${Settings.canDrawOverlays(this)}")
        blockWm = BlockWindowManager(this)
        blockWm!!.show()
    }

    fun cancelBlockNotification() {
        getSystemService(NotificationManager::class.java).cancel(BLOCK_NOTIF_ID)
    }

    // ── Canali notifica ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)

        // Canale servizio (persistente, bassa priorità)
        nm.createNotificationChannel(
            NotificationChannel(CH_ID, "Smart Blocker — Servizio", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Servizio di monitoraggio attivo" }
        )

        // Canale blocco (alta priorità — necessario per full-screen intent)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Smart Blocker — Blocco", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "Notifica di blocco immediato"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
        )
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Smart Blocker attivo")
            .setContentText("Monitoraggio orari in corso")
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_SNOOZE       = "com.garsal.smartblocker.SNOOZE"
        const val ACTION_UNBLOCK      = "com.garsal.smartblocker.UNBLOCK"
        const val ACTION_CHECK_NOW    = "com.garsal.smartblocker.CHECK_NOW"
        const val ACTION_SHOW_OVERLAY = "com.garsal.smartblocker.SHOW_OVERLAY"
        private const val CH_ID              = "blocker_service"
        private const val CH_ALARM           = "blocker_alarm"
        private const val NOTIF_ID           = 1
        const val BLOCK_NOTIF_ID             = 2
        private const val FALLBACK_ALARM_CODE = 43
        private const val SNOOZE_ALARM_CODE   = 44
        private const val FALLBACK_INTERVAL_MS = 10 * 60 * 1000L
        private const val TAG                = "BlockerService"
    }
}
