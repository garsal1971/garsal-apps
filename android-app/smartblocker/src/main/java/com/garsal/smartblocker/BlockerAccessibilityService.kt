package com.garsal.smartblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class BlockerAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.also {
            it.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            it.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            it.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            it.notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val state = Prefs.getState(this)
        if (state == Prefs.STATE_NONE) return

        val pkg = event.packageName?.toString() ?: return
        // Se l'utente apre un'app diversa mentre il blocco è attivo, ri-lancia l'overlay
        if (pkg != packageName && pkg != "android" && pkg != "com.android.systemui") {
            startActivity(
                Intent(this, BlockOverlayActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }
    }

    override fun onInterrupt() {}
}
