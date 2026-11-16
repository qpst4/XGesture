package com.slideindex.app.util

import android.app.KeyguardManager
import android.content.Context
import android.view.accessibility.AccessibilityWindowInfo

object LockScreenState {
    fun detectActive(
        context: Context,
        windows: List<AccessibilityWindowInfo>? = null,
    ): Boolean = com.slideindex.app.perf.PerfProbe.probe("Lock.isKeyguard") {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard == null) {
            false
        } else if (keyguard.isKeyguardLocked) {
            true
        } else if (keyguard.isDeviceLocked) {
            true
        } else {
            false
        }
    }

    fun isActive(context: Context): Boolean {
        if (TriggerEnvironmentState.lockScreenActive) return true
        return detectActive(context)
    }
}
