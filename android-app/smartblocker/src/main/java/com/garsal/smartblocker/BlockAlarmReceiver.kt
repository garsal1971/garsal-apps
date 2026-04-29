package com.garsal.smartblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BlockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, BlockerService::class.java).apply {
            action = BlockerService.ACTION_CHECK_NOW
        }
        ContextCompat.startForegroundService(context, svc)
    }
}
