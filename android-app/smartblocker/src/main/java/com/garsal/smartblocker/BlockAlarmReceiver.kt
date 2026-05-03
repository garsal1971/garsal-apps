package com.garsal.smartblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

class BlockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Acquisisce WakeLock statico prima che onReceive() ritorni: senza questo
        // il CPU può riaddormentarsi prima che il servizio finisca la query Supabase.
        acquireWakeLock(context)
        ContextCompat.startForegroundService(context,
            Intent(context, BlockerService::class.java).apply {
                action = BlockerService.ACTION_CHECK_NOW
            })
    }

    companion object {
        @Volatile private var wl: PowerManager.WakeLock? = null

        fun acquireWakeLock(context: Context) {
            val pm = context.applicationContext
                .getSystemService(Context.POWER_SERVICE) as PowerManager
            synchronized(this) {
                wl?.let { if (it.isHeld) it.release() }
                wl = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SmartBlocker::AlarmWakeLock"
                ).also { it.acquire(60_000L) }
            }
        }

        fun releaseWakeLock() {
            synchronized(this) {
                wl?.let { if (it.isHeld) it.release() }
                wl = null
            }
        }
    }
}
