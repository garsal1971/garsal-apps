package com.garsal.smartblocker

import android.app.*
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
        wakeLock?.acquire(10 * 60 * 1000L) // max 10 min, si rinnova al prossimo check

        handler.post(checker)
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

        // Rinnova wake lock
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10 * 60 * 1000L)
        }

        // Snooze scaduto → ripristina blocco
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
    }

    private fun checkSupabaseQueueIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastSupabaseCheckMs < 5 * 60 * 1000L) return   // ogni 5 minuti
        lastSupabaseCheckMs = now
        triggerSupabaseCheck()
    }

    fun triggerSupabaseCheck() {
        if (Prefs.getState(this) != Prefs.STATE_NONE) return       // già bloccato
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
            }
        }.start()
    }

    private fun showOverlay() {
        startActivity(
            Intent(this, BlockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }

    // ── Notifica foreground ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CH_ID, "Smart Blocker", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Servizio di blocco schedulato attivo" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
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
        private const val CH_ID    = "blocker_service"
        private const val NOTIF_ID = 1
    }
}
