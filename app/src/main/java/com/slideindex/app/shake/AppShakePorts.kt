package com.slideindex.app.shake

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TriggerVisibility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class AppShakeRuntimePort @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ShakeRuntimePort {
    override fun isAccessibilityServiceEnabled(): Boolean =
        PermissionHelper.isAccessibilityServiceEnabled(appContext)

    override fun isAccessibilityConnected(): Boolean =
        SlideIndexAccessibilityService.isConnected()

    override fun performAccessibilityAction(action: GestureAction): Boolean =
        SlideIndexAccessibilityService.perform(action)

    override fun captureGestureForegroundPackage() {
        OverlayService.captureGestureForegroundPackage()
    }

    override fun foregroundPackage(): String? =
        OverlayService.foregroundPackage ?: SlideIndexAccessibilityService.currentForegroundPackage()

    override fun isLockScreenActive(): Boolean {
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        return keyguard?.isKeyguardLocked == true
    }

    override fun isLandscape(): Boolean = TriggerVisibility.isLandscape(appContext)

    override fun overlayActionContext(): Context =
        SlideIndexAccessibilityService.overlayHostContext() ?: appContext

    override fun screenCenterX(): Float = screenMetrics().widthPixels / 2f

    override fun screenCenterY(): Float = screenMetrics().heightPixels / 2f

    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        runCatching { wm.defaultDisplay.getRealMetrics(metrics) }
        return metrics
    }
}

@Singleton
class AppShakeActionPort @Inject constructor(
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val applicationScope: CoroutineScope,
    private val runtimePort: ShakeRuntimePort
) : ShakeActionPort {
    override fun execute(
        action: GestureAction,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float
    ): Boolean {
        val executor = ActionExecutor(
            context = runtimePort.overlayActionContext(),
            appRepository = appRepository,
            onShellCommandsPersist = { commands ->
                applicationScope.launch {
                    settingsRepository.setShellCommands(commands)
                }
            }
        )
        return executor.execute(
            action = action,
            settings = settings,
            anchorRawX = anchorRawX,
            anchorRawY = anchorRawY
        )
    }
}

@Singleton
class AppShakeFeedbackPort @Inject constructor() : ShakeFeedbackPort {
    override fun vibrate(context: Context) {
        ShakeVibrationHelper.vibrate(context)
    }

    override fun playActionSound(
        context: Context,
        forceAudible: Boolean,
        volume: Int
    ) {
        TriggerFeedbackAudio.playActionAck(context, forceAudible, volume)
    }

    override fun showGestureFeedback(
        context: Context,
        gestureType: ShakeGestureType,
        action: GestureAction,
        colorArgb: Int
    ) {
        ShakeFeedbackOverlay.showGestureFeedback(context, gestureType, action, colorArgb)
    }

    override fun detachFeedbackOverlay() {
        ShakeFeedbackOverlay.detach()
    }

    override fun showMenuOverlay(context: Context, menu: ShakeMenu) {
        ShakeMenuOverlay.show(context, menu)
    }

    override fun detachMenuOverlay() {
        ShakeMenuOverlay.detach()
    }
}
