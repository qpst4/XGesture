package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import com.slideindex.app.overlay.ImeBoundsDetector

object ClipboardFloatImeDetector {
    fun detectImeBounds(service: AccessibilityService): Rect? =
        ImeBoundsDetector.detectImeBounds(service)

    /** 带"输入法是否挂在应用窗口上"的完整探测结果；见 [ImeBoundsDetector.ImeProbe]。 */
    internal fun detectImeProbe(service: AccessibilityService): ImeBoundsDetector.ImeProbe =
        ImeBoundsDetector.detectImeProbe(service)
}
