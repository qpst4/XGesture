package com.slideindex.app.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExcludedAppScopes

enum class OverlaySuppressionScope {
    TRIGGER,
    CORNER_WHEEL,
    FLOAT_BALL,
    MULTI_TOUCH,
}

object OverlaySuppression {
    fun shouldSuppress(
        settings: AppSettings,
        context: Context,
        foregroundPackage: String?,
        scope: OverlaySuppressionScope,
    ): Boolean {
        if (OverlaySnoozeController.isActive()) return true
        if (settings.hideTriggerOnLockScreen && isLockScreenActive(context)) return true
        if (scope == OverlaySuppressionScope.TRIGGER) {
            if (settings.hideTriggerInLandscape && isLandscape(context)) return true
            if (settings.hideTriggerOnLauncher) {
                val pkg = AccessibilityForegroundResolver.resolve(context)
                    ?: foregroundPackage
                    ?: return false
                if (LauncherUtils.isHomePackage(context, pkg)) return true
            }
        }
        val pkg = AccessibilityForegroundResolver.resolve(context)
            ?: foregroundPackage
            ?: return false
        val scopes = settings.excludedAppScopes[pkg] ?: return false
        return scopes.suppresses(scope)
    }

    fun isLandscape(context: Context): Boolean {
        val wm = context.getSystemService(WindowManager::class.java)
        val bounds = runCatching { wm?.currentWindowMetrics?.bounds }.getOrNull()
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            return bounds.width() > bounds.height()
        }
        val metrics = context.resources.displayMetrics
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            return metrics.widthPixels > metrics.heightPixels
        }
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun isLockScreenActive(context: Context): Boolean {
        if (TriggerEnvironmentState.lockScreenActive) return true
        val windows =
            if (context is AccessibilityService) {
                context.windows
            } else {
                null
            }
        return LockScreenState.detectActive(context, windows)
    }
}

private fun ExcludedAppScopes.suppresses(scope: OverlaySuppressionScope): Boolean =
    when (scope) {
        OverlaySuppressionScope.TRIGGER -> suppressTriggers
        OverlaySuppressionScope.CORNER_WHEEL -> suppressCornerWheel
        OverlaySuppressionScope.FLOAT_BALL -> suppressFloatBall
        OverlaySuppressionScope.MULTI_TOUCH -> suppressMultiTouch
    }
