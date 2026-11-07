package com.slideindex.app.overlay

import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import com.slideindex.app.overlay.panels.UI_NIGHT_MODE_KEY
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.settings.AppSettings

class AdjustPanelOverlayController(
    val host: Host
) {
    interface Host {
        val context: Context
        fun side(): PanelSide
        fun settings(): AppSettings
        fun actionHost(): com.slideindex.app.overlay.panels.PanelActionHost
        fun isAdjustMode(): Boolean
    fun currentAdjustMode(): com.slideindex.app.overlay.panels.PanelAdjustMode?
    fun adjustAnchorRawY(): Float?
    fun panelMode(): OverlayPanelMode
    fun endGestureSession()
        fun viewWidth(): Int
        fun viewHeight(): Int
        fun density(): Float
        fun screenWidthPx(): Int
        fun screenHeightPx(): Int
        fun viewLocationOnScreen(): IntArray
        fun anchorLocalY(rawY: Float): Float
        fun dp(value: Float): Float
        fun invalidate()
        fun post(action: () -> Unit)
        fun runAfterLayout(block: () -> Unit)
        fun hapticConfirmLaunch()
        fun onAdjustPanelDismiss()
        fun onSessionStart()
        fun notifyOverlayLayoutIfNeeded()
        fun notifyPresentationTouchRequirementChanged()
    }

    data class AdjustIndicatorVisual(
        val mode: com.slideindex.app.overlay.panels.PanelAdjustMode,
        val fraction: Float,
        val anchorRawY: Float
    )

    private val touchHandler = AdjustPanelTouchHandler(this)
    private val renderer = AdjustPanelRenderer(this)

    internal var adjustPanelState: AdjustPanelState? = null
    internal var adjustPanelDismissing = false
    internal var wasAdjustMode = false
    internal var adjustIndicatorReceding = false
    internal var adjustPanelExpandedForGesture = false
    internal var adjustPanelEntering = false
    internal var adjustIndicatorProgress = 0f
    internal var adjustIndicatorAnimator: ValueAnimator? = null
    internal var adjustIndicatorLayout: AdjustLevelIndicator.Layout? = null
    internal var adjustIndicatorHoldVisual: AdjustIndicatorVisual? = null
    internal var adjustIndicatorFrozenLayout: AdjustLevelIndicator.Layout? = null
    internal var volumeDragAnchorRawY = 0f
    internal var volumeDragBaseline = 0f

    private var volumeChangeReceiver: BroadcastReceiver? = null
    private var brightnessSettingsObserver: ContentObserver? = null
    private val brightnessSettingsHandler = Handler(Looper.getMainLooper())
    private var brightnessAccurateRefreshRunnable: Runnable? = null
    private var brightnessAccurateSyncGeneration = 0L
    internal var suppressBrightnessLevelSyncUntilMs = 0L

    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        if (hasAdjustPanel() && !adjustPanelDismissing) {
            dismissAdjustPanel(animated = true)
        }
    }

    fun scheduleAutoDismiss(timeoutMs: Long = ADJUST_PANEL_AUTO_DISMISS_TIMEOUT_MS) {
        cancelAutoDismiss()
        if (hasAdjustPanel() && !adjustPanelDismissing) {
            autoDismissHandler.postDelayed(autoDismissRunnable, timeoutMs)
        }
    }

    fun cancelAutoDismiss() {
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
    }

    fun hasAdjustPanel(): Boolean = adjustPanelState != null

    fun isDismissing(): Boolean = adjustPanelDismissing

    fun onSizeChanged() {
        adjustPanelState?.let { state ->
            if (!adjustPanelEntering) {
                renderer.updateAdjustIndicatorLayout(state.anchorRawY)
                host.invalidate()
            }
        }
    }

    fun onDetachedFromWindow() {
        cancelAutoDismiss()
        stopAdjustPanelLevelSync()
    }

    fun forceRecover() {
        cancelAutoDismiss()
        if (adjustPanelDismissing) return
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorProgress = 0f
        adjustPanelDismissing = false
        adjustPanelExpandedForGesture = false
        adjustPanelEntering = false
        renderer.clearAdjustIndicatorExitState()
        if (adjustPanelState != null) {
            stopAdjustPanelLevelSync()
            adjustPanelState = null
            host.onAdjustPanelDismiss()
        }
    }

    fun onSessionEnd() {
        adjustPanelState?.let {
            adjustIndicatorReceding = false
        }
        if (adjustPanelState == null) {
            adjustIndicatorAnimator?.cancel()
            adjustIndicatorProgress = 0f
            wasAdjustMode = false
            renderer.clearAdjustIndicatorExitState()
        }
    }

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean =
        touchHandler.handleTouch(event, localX, localY)

    fun updateAdjustIndicatorLayout(
        anchorRawY: Float,
        forceFullScreenAnchor: Boolean = false,
        mode: com.slideindex.app.overlay.panels.PanelAdjustMode? = null
    ): AdjustLevelIndicator.Layout? =
        renderer.updateAdjustIndicatorLayout(anchorRawY, forceFullScreenAnchor, mode)

    fun showAdjustPanel(
        mode: com.slideindex.app.overlay.panels.PanelAdjustMode,
        fraction: Float,
        anchorRawY: Float
    ) {
        if (mode == com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS) {
            host.actionHost().clearBrightnessPreview()
            suppressBrightnessLevelSync(BRIGHTNESS_LEVEL_SYNC_SUPPRESS_MS)
        }
        val executor = host.actionHost()
        adjustPanelState = if (mode == com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME) {
            AdjustPanelState(
                mode = mode,
                fraction = fraction,
                anchorRawY = anchorRawY,
                ringFraction = executor.readVolumeFraction(com.slideindex.app.overlay.panels.PanelVolumeStream.RING),
                notificationFraction = executor.readVolumeFraction(com.slideindex.app.overlay.panels.PanelVolumeStream.NOTIFICATION),
                ringerMode = executor.readRingerMode(),
                interruptionFilter = executor.readInterruptionFilter()
            )
        } else {
            AdjustPanelState(
                mode = mode,
                fraction = fraction,
                anchorRawY = anchorRawY,
                autoBrightnessEnabled = executor.readAutoBrightnessEnabled(),
                darkModeEnabled = executor.readDarkModeEnabled()
            )
        }
        adjustPanelDismissing = false
        adjustIndicatorAnimator?.cancel()
        val fromContinuousGesture = host.isAdjustMode()
        adjustPanelExpandedForGesture = true
        host.onSessionStart()
        host.notifyPresentationTouchRequirementChanged()
        if (fromContinuousGesture && adjustIndicatorProgress >= 1f) {
            wasAdjustMode = true
            adjustPanelEntering = false
            adjustIndicatorFrozenLayout = null
            renderer.updateAdjustIndicatorLayout(anchorRawY, forceFullScreenAnchor = true, mode = mode)
            host.invalidate()
        } else {
            host.runAfterLayout {
                if (adjustPanelState == null) return@runAfterLayout
                if (fromContinuousGesture && adjustIndicatorProgress > 0f) {
                    renderer.continueAdjustPanelEnterAnimation(anchorRawY)
                } else {
                    renderer.beginAdjustPanelEnterAnimation(anchorRawY)
                }
            }
        }
        startAdjustPanelLevelSync(mode)
        host.notifyPresentationTouchRequirementChanged()
        scheduleAutoDismiss()
    }

    fun dismissAdjustPanel(animated: Boolean = true) {
        cancelAutoDismiss()
        if (adjustPanelState == null || adjustPanelDismissing) return
        stopAdjustPanelLevelSync()
        adjustIndicatorHoldVisual = renderer.captureAdjustIndicatorVisualForDismiss()
        renderer.freezeAdjustIndicatorLayout(
            adjustIndicatorHoldVisual?.anchorRawY,
            adjustIndicatorHoldVisual?.mode
        )
        if (!animated || adjustIndicatorProgress <= 0f) {
            finishDismissAdjustPanel()
            return
        }
        adjustPanelDismissing = true
        // 退场动画期间 presentation 必须立刻让出触摸：
        // 旧实现要等 160ms 动画跑完才清 adjustPanelState，这期间全屏窗仍被判定为"需要直触"，
        // 落在条消失那一刻的点击全部被这扇窗吃掉（用户感受就是"点哪都没反应"）。
        EdgeDiag.log(
            "adjustPanel",
            "dismiss 开始：立即撤销 presentation 直触 animated=$animated " +
                "progress=${"%.2f".format(adjustIndicatorProgress)}"
        )
        host.notifyPresentationTouchRequirementChanged()
        renderer.animateAdjustIndicatorTo(
            target = 0f,
            durationMs = AdjustPanelRenderer.ADJUST_INDICATOR_EXIT_MS,
            interpolator = AccelerateInterpolator()
        ) {
            finishDismissAdjustPanel()
        }
    }

    fun drawVisibleIndicator(canvas: Canvas) = renderer.drawVisibleIndicator(canvas)

    fun syncAdjustIndicatorAnimation() {
        val active = host.isAdjustMode() || adjustPanelState != null
        if (active == wasAdjustMode) return
        wasAdjustMode = active
        if (active) {
            if (adjustPanelState != null) {
                wasAdjustMode = true
                return
            }
            renderer.startAdjustIndicatorEnterAnimationIfNeeded()
        } else if (adjustPanelState == null && adjustIndicatorProgress > 0f) {
            adjustIndicatorHoldVisual = renderer.captureAdjustIndicatorVisualForDismiss()
                ?: adjustIndicatorHoldVisual
            renderer.animateAdjustIndicatorTo(
                target = 0f,
                durationMs = AdjustPanelRenderer.ADJUST_INDICATOR_EXIT_MS,
                interpolator = AccelerateInterpolator()
            ) {
                renderer.clearAdjustIndicatorExitState()
                adjustIndicatorProgress = 0f
                host.invalidate()
                host.notifyOverlayLayoutIfNeeded()
            }
        }
    }

    fun onSessionStartAdjustMode() {
        wasAdjustMode = true
        renderer.startAdjustIndicatorEnterAnimationIfNeeded()
    }

    private fun finishDismissAdjustPanel() {
        cancelAutoDismiss()
        EdgeDiag.log("adjustPanel", "dismiss 完成：清状态并交回触摸")
        adjustPanelDismissing = false
        adjustPanelState = null
        adjustPanelExpandedForGesture = false
        adjustPanelEntering = false
        adjustIndicatorLayout = null
        adjustIndicatorFrozenLayout = null
        adjustIndicatorHoldVisual = null
        adjustIndicatorReceding = false
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorProgress = 0f
        wasAdjustMode = false
        if (host.panelMode() == OverlayPanelMode.NONE &&
            !host.isAdjustMode()
        ) {
            host.endGestureSession()
        }
        host.onAdjustPanelDismiss()
        host.invalidate()
    }

    private fun startAdjustPanelLevelSync(mode: com.slideindex.app.overlay.panels.PanelAdjustMode) {
        when (mode) {
            com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME -> startVolumeLevelSync()
            com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS -> startBrightnessSettingsSync()
        }
    }

    private fun stopAdjustPanelLevelSync() {
        stopVolumeLevelSync()
        stopBrightnessSettingsSync()
    }

    private fun startVolumeLevelSync() {
        if (volumeChangeReceiver != null) return
        volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    VOLUME_CHANGED_ACTION -> {
                        val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                        if (
                            streamType == AudioManager.STREAM_MUSIC ||
                            streamType == AudioManager.STREAM_RING ||
                            streamType == AudioManager.STREAM_NOTIFICATION
                        ) {
                            syncAdjustPanelVolumeFromSystem()
                        }
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> syncAdjustPanelVolumeFromSystem()
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED ->
                        syncAdjustPanelVolumeFromSystem()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            host.context.registerReceiver(volumeChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            host.context.registerReceiver(volumeChangeReceiver, filter)
        }
    }

    private fun stopVolumeLevelSync() {
        volumeChangeReceiver?.let { receiver ->
            runCatching { host.context.unregisterReceiver(receiver) }
        }
        volumeChangeReceiver = null
    }

    private fun startBrightnessSettingsSync() {
        if (brightnessSettingsObserver != null) return
        val observer = object : ContentObserver(brightnessSettingsHandler) {
            override fun onChange(selfChange: Boolean) {
                syncAdjustPanelLevelFromSystem(com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS)
            }
        }
        brightnessSettingsObserver = observer
        val resolver = host.context.contentResolver
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false,
            observer
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            observer
        )
        runCatching {
            resolver.registerContentObserver(
                Settings.System.getUriFor("screen_brightness_float"),
                false,
                observer
            )
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.System.getUriFor("screen_auto_brightness_adj"),
                false,
                observer
            )
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(UI_NIGHT_MODE_KEY),
            false,
            observer
        )
    }

    private fun stopBrightnessSettingsSync() {
        brightnessSettingsObserver?.let { observer ->
            runCatching { host.context.contentResolver.unregisterContentObserver(observer) }
        }
        brightnessSettingsObserver = null
    }

    private fun syncAdjustPanelVolumeFromSystem() {
        syncAdjustPanelLevelFromSystem(com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME)
        if (adjustPanelState?.dragTarget == null) {
            scheduleAutoDismiss()
        }
    }

    fun suppressBrightnessLevelSync(durationMs: Long = BRIGHTNESS_LEVEL_SYNC_SUPPRESS_MS) {
        suppressBrightnessLevelSyncUntilMs = SystemClock.uptimeMillis() + durationMs
    }

    fun syncBrightnessFractionAfterAutoToggle() {
        val state = adjustPanelState ?: return
        if (state.mode != com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS) return
        suppressBrightnessLevelSync(BRIGHTNESS_AUTO_TOGGLE_SYNC_SUPPRESS_MS)
        scheduleDebouncedBrightnessAccurateRefresh()
    }

    private fun scheduleDebouncedBrightnessAccurateRefresh() {
        val generation = ++brightnessAccurateSyncGeneration
        brightnessAccurateRefreshRunnable?.let { brightnessSettingsHandler.removeCallbacks(it) }
        val delayed = Runnable { runBrightnessAccurateRefresh(generation) }
        brightnessAccurateRefreshRunnable = delayed
        brightnessSettingsHandler.postDelayed(delayed, AUTO_BRIGHTNESS_SYNC_DEBOUNCE_MS)
    }

    private fun runBrightnessAccurateRefresh(expectedGeneration: Long) {
        Thread {
            val fraction = host.actionHost().readBrightnessFraction(host.context)
            brightnessSettingsHandler.post {
                if (brightnessAccurateSyncGeneration != expectedGeneration) return@post
                val state = adjustPanelState ?: return@post
                if (state.mode != com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS) return@post
                if (kotlin.math.abs(state.fraction - fraction) < LEVEL_SYNC_EPSILON) return@post
                state.fraction = fraction
                host.invalidate()
            }
        }.start()
    }

    private fun syncAdjustPanelLevelFromSystem(mode: com.slideindex.app.overlay.panels.PanelAdjustMode) {
        val state = adjustPanelState ?: return
        if (state.mode != mode) return
        val executor = host.actionHost()
        if (mode == com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME) {
            if (state.dragTarget != VolumeDragTarget.MEDIA) {
                val mediaFraction = executor.readCurrentAdjustFraction(mode)
                if (kotlin.math.abs(state.fraction - mediaFraction) >= LEVEL_SYNC_EPSILON) {
                    state.fraction = mediaFraction
                }
            }
            if (state.dragTarget != VolumeDragTarget.RING) {
                val ringFraction = executor.readVolumeFraction(com.slideindex.app.overlay.panels.PanelVolumeStream.RING)
                if (kotlin.math.abs(state.ringFraction - ringFraction) >= LEVEL_SYNC_EPSILON) {
                    state.ringFraction = ringFraction
                }
            }
            if (state.dragTarget != VolumeDragTarget.NOTIFICATION) {
                val notificationFraction = executor.readVolumeFraction(com.slideindex.app.overlay.panels.PanelVolumeStream.NOTIFICATION)
                if (kotlin.math.abs(state.notificationFraction - notificationFraction) >= LEVEL_SYNC_EPSILON) {
                    state.notificationFraction = notificationFraction
                }
            }
            state.ringerMode = executor.readRingerMode()
            val interruptionFilter = executor.readInterruptionFilter()
            if (state.interruptionFilter != interruptionFilter) {
                state.interruptionFilter = interruptionFilter
            }
            host.invalidate()
            return
        }
        if (mode == com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS) {
            if (host.isAdjustMode() && adjustPanelState == null) return
            if (SystemClock.uptimeMillis() < suppressBrightnessLevelSyncUntilMs) return
        }
        if (state.dragTarget != null) return
        val fraction = executor.readCurrentAdjustFraction(mode)
        if (kotlin.math.abs(state.fraction - fraction) < LEVEL_SYNC_EPSILON) {
            syncAdjustPanelBrightnessFlags(state)
            return
        }
        state.fraction = fraction
        syncAdjustPanelBrightnessFlags(state)
        host.invalidate()
    }

    private fun syncAdjustPanelBrightnessFlags(state: AdjustPanelState) {
        val executor = host.actionHost()
        val autoBrightnessEnabled = executor.readAutoBrightnessEnabled()
        val darkModeEnabled = executor.readDarkModeEnabled()
        var changed = false
        if (state.autoBrightnessEnabled != autoBrightnessEnabled) {
            state.autoBrightnessEnabled = autoBrightnessEnabled
            changed = true
        }
        if (state.darkModeEnabled != darkModeEnabled) {
            state.darkModeEnabled = darkModeEnabled
            changed = true
        }
        if (changed) host.invalidate()
    }

    companion object {
        const val ADJUST_PANEL_AUTO_DISMISS_TIMEOUT_MS = 3000L
        private const val LEVEL_SYNC_EPSILON = 0.002f
        private const val BRIGHTNESS_LEVEL_SYNC_SUPPRESS_MS = 400L
        private const val BRIGHTNESS_AUTO_TOGGLE_SYNC_SUPPRESS_MS = 450L
        private const val AUTO_BRIGHTNESS_SYNC_DEBOUNCE_MS = 180L
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
