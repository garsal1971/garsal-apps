package com.garsal.smartblocker

import android.app.*
import android.content.Intent
import android.os.Build
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
        startForeground(NOTIF_ID, buildNotification())

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartBlocker::WakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        handler.post(checker)

        if (Prefs.getDeviceToken(this).isEmpty()) {
            Thread { SupabaseApi(this).fetchAndCacheDeviceToken() }.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE     -> handleSnooze()
            ACTION_UNBLOCK    -> handleUnblock()
            ACTION_CHECK_NOW  -> { lastSupabaseCheckMs = 0L; triggerSupabaseCheck() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checker)
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Logica principale ────────────────────────────────────────────────────

    private fun checkSnooze() {
        val now = System.currentTimeMillis()
        val state = Prefs.getState(this)

        if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L)

        val snoozeUntil = Prefs.getSnoozeUntil(this)
        if (state == Prefs.STATE_NONE && snoozeUntil > 0 && now >= snoozeUntil) {
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
            Prefs.setSnoozeCount(this, count + 1)
            Prefs.setSnoozeUntil(this, System.currentTimeMillis() + Config.SNOOZE_DURATION_MS)
            Prefs.setState(this, Prefs.STATE_NONE)
        }
    }

    private fun handleUnblock() {
        Prefs.setState(this, Prefs.STATE_NONE)
        Prefs.setSnoozeCount(this, 0)
        Prefs.setSnoozeUntil(this, 0)
        Prefs.clearBlockEntityIds(this)
        cancelBlockNotification()
    }

    private fun checkSupabaseQueueIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastSupabaseCheckMs < 5 * 60 * 1000L) return
        lastSupabaseCheckMs = now
        triggerSupabaseCheck()
    }

    fun triggerSupabaseCheck() {
        if (Prefs.getState(this) != Prefs.STATE_NONE) return
        Thread {
            val result = SupabaseApi(this).queryQueue()
            val dueIds = result.dueIds
            if (dueIds.isNotEmpty()) {
                dueIds.forEach { SupabaseApi(this).markSent(it) }
                val entityIds = result.entries
                    .filter { it.id in dueIds && it.entityId.isNotBlank() }
                    .map { it.entityId }
                handler.post {
                    Prefs.setBlockEntityIds(this, entityIds)
                    Prefs.setState(this, Prefs.STATE_TRIGGERED)
                    Prefs.setSnoozeCount(this, 0)
                    Prefs.setSnoozeUntil(this, 0)
                    showOverlay()
                }
            } else if (result.nextFireAtMs != null) {
                // Nessun blocco ora, ma ce n'è uno futuro: schedula alarm esatto
                scheduleExactAlarm(result.nextFireAtMs)
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
     * Mostra la schermata di blocco tramite full-screen notification.
     * Su Android 10+ startActivity() da background viene bloccato dal sistema;
     * setFullScreenIntent() è il metodo ufficiale (come le sveglie) e funziona:
     *  - schermo acceso / app in foreground → activity si apre direttamente
     *  - schermo spento / Doze → accende lo schermo e apre l'activity
     */
    private fun showOverlay() {
        val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, BLOCK_NOTIF_ID, overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("🔐 Blocco attivato")
            .setContentText("È ora di smettere di usare il telefono")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)   // apre l'activity anche da background/sleep
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        getSystemService(NotificationManager::class.java).notify(BLOCK_NOTIF_ID, notification)
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
        const val ACTION_SNOOZE    = "com.garsal.smartblocker.SNOOZE"
        const val ACTION_UNBLOCK   = "com.garsal.smartblocker.UNBLOCK"
        const val ACTION_CHECK_NOW = "com.garsal.smartblocker.CHECK_NOW"
        private const val CH_ID       = "blocker_service"
        private const val CH_ALARM    = "blocker_alarm"
        private const val NOTIF_ID    = 1
        const val BLOCK_NOTIF_ID      = 2
        private const val TAG         = "BlockerService"
    }
}
