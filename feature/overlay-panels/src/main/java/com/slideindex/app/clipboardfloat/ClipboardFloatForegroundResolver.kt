package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService

object ClipboardFloatForegroundResolver {
    fun resolveHostPackage(service: AccessibilityService): String? =
        com.slideindex.app.clipboardfloat.ClipboardFloatHostAccess.host?.resolveForegroundPackage(service)
}
