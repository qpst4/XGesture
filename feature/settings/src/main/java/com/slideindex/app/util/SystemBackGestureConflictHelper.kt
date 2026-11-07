package com.slideindex.app.util

import android.content.Context
import android.provider.Settings
import com.slideindex.app.settings.AppSettings

object SystemBackGestureConflictHelper {
    fun isGestureNavigation(context: Context): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            "navigation_mode",
            0,
        ) == 2
    }

    fun hasPotentialConflict(settings: AppSettings, context: Context): Boolean {
        if (!isGestureNavigation(context)) return false
        if (settings.interceptSystemBackGesture) return false
        return settings.leftEdgeEnabled || settings.rightEdgeEnabled
    }
}
