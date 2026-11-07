package com.slideindex.app.overlay.ringlauncher

import android.content.Context
import android.graphics.Bitmap
import com.slideindex.app.settings.FvRingLauncherSlotIconOverride
import com.slideindex.app.shell.ShellCommandIconResolver

object FvRingLauncherSlotIconResolver {
    fun resolveBitmap(
        context: Context,
        override: FvRingLauncherSlotIconOverride,
        fallbackLabel: String,
        sizePx: Int,
    ): Bitmap? {
        if (!override.iconPath.isNullOrBlank()) {
            return ShellCommandIconResolver.loadUriBitmap(context, override.iconPath, sizePx)
        }
        if (!override.textIcon.isNullOrBlank()) {
            return ShellCommandIconResolver.renderTextBitmap(override.textIcon, fallbackLabel, sizePx)
        }
        return null
    }
}
