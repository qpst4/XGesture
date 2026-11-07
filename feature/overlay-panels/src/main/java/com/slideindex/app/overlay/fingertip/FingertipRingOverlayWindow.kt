package com.slideindex.app.overlay.fingertip

import com.slideindex.app.overlay.overlayHostContext

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FingertipRingCodec
import com.slideindex.app.settings.FingertipRingSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.settings.resolveHoneycombLongPressArmed
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("StaticFieldLeak")
object FingertipRingOverlayWindow {
    private const val TAG = "FingertipRing"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: FingertipRingOverlayView? = null
    private var windowManager: WindowManager? = null
    private var externalTracking = false
    private var persistAfterSessionEnd = false
    private var browseActionExecutor: com.slideindex.app.overlay.panels.GestureActionRunner? = null
    private var browseSettings: AppSettings? = null

    val isShowing: Boolean get() = overlayView != null

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        externalTracking: Boolean,
        executeGesture: com.slideindex.app.overlay.panels.GestureActionRunner? = null,
        highlightedSlot: Int = -1,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = CountDownLatch(1)
            mainHandler.post {
                result = show(
                    context,
                    settings,
                    anchorRawX,
                    anchorRawY,
                    externalTracking,
                    executeGesture,
                    highlightedSlot,
                )
                latch.countDown()
            }
            runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
            return result
        }

        val ringSettings = settings.fingertipRing
        val slots = FingertipRingCodec.activeSlots(ringSettings)
        if (slots.none { it !is GestureAction.None }) {
            Log.w(TAG, "show: no configured slots")
            return false
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility not enabled")
            return false
        }
        val hostContext = overlayHostContext()
            ?: run {
                Log.w(TAG, "show: overlay host unavailable")
                return false
            }

        dismissInternal()

        val wm = hostContext.getSystemService(WindowManager::class.java) ?: return false
        val view = FingertipRingOverlayView(hostContext)
        view.configure(
            appSettings = settings,
            settings = ringSettings,
            slotActions = slots,
            anchorX = anchorRawX,
            anchorY = anchorRawY,
            initialHighlight = highlightedSlot,
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.fullscreenLauncherWindowType(hostContext),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }

        return try {
            wm.addView(view, params)
            overlayView = view
            windowManager = wm
            this.externalTracking = externalTracking
            persistAfterSessionEnd = !externalTracking
            browseActionExecutor = if (externalTracking) null else executeGesture
            browseSettings = if (externalTracking) null else settings
            if (externalTracking) {
                view.updatePointer(anchorRawX, anchorRawY)
            } else {
                enableDirectTouch(view, wm)
                view.installBrowseTouchHandler(
                    onMove = { rawX, rawY -> updatePointer(rawX, rawY) },
                    onRelease = { rawX, rawY -> confirmBrowseSelection(rawX, rawY) },
                    onCancel = { dismiss() },
                )
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "show failed", t)
            dismissInternal()
            false
        }
    }

    fun updatePointer(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePointer(rawX, rawY) }
            return
        }
        overlayView?.updatePointer(rawX, rawY)
    }

    fun confirmSelection(
        rawX: Float,
        rawY: Float,
        settings: AppSettings,
        executeGesture: com.slideindex.app.overlay.panels.GestureActionRunner,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { confirmSelection(rawX, rawY, settings, executeGesture) }
            return
        }
        val view = overlayView ?: return
        updatePointer(rawX, rawY)
        val slot = view.selectionSlot()
        val selectionPressDurationMs = view.selectionPressDurationMs()
        val longPressArmed = view.longPressArmedForSelection()
        dismissInternal()
        executeSlot(
            slot = slot,
            settings = settings,
            executeGesture = executeGesture,
            selectionPressDurationMs = selectionPressDurationMs,
            longPressArmed = longPressArmed,
            anchorRawX = rawX,
            anchorRawY = rawY,
        )
    }

    fun onGestureSessionEnd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onGestureSessionEnd() }
            return
        }
        if (persistAfterSessionEnd) return
        if (!externalTracking) return
        dismissInternal()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        dismissInternal()
    }

    private fun confirmBrowseSelection(rawX: Float, rawY: Float) {
        val settings = browseSettings ?: return
        val runner = browseActionExecutor ?: return
        confirmSelection(rawX, rawY, settings, runner)
    }

    private fun enableDirectTouch(view: View, wm: WindowManager) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = params.flags and
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() and
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun executeSlot(
        slot: Int,
        settings: AppSettings,
        executeGesture: com.slideindex.app.overlay.panels.GestureActionRunner,
        selectionPressDurationMs: Long,
        longPressArmed: Boolean,
        anchorRawX: Float,
        anchorRawY: Float,
    ) {
        val slots = FingertipRingCodec.activeSlots(settings.fingertipRing)
        val action = slots.getOrNull(slot) ?: return
        if (action is GestureAction.None) return
        val armed = when (action) {
            is GestureAction.LaunchApp,
            is GestureAction.LaunchShortcut,
            -> longPressArmed ||
                settings.resolveHoneycombLongPressArmed(selectionPressDurationMs.coerceAtLeast(0L))
            else -> false
        }
        executeGesture.run(action, settings, armed, anchorRawX, anchorRawY, null)
    }

    private fun dismissInternal() {
        overlayView?.cancelSlotLongPress()
        val view = overlayView
        val wm = windowManager
        overlayView = null
        windowManager = null
        externalTracking = false
        persistAfterSessionEnd = false
        browseActionExecutor = null
        browseSettings = null
        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
        }
    }
}

private class FingertipRingOverlayView(context: Context) : View(context) {
    private var appSettings: AppSettings = AppSettings()
    private var ringSettings: FingertipRingSettings = FingertipRingSettings()
    private var slots: List<GestureAction> = emptyList()
    private var centerScreenX = 0f
    private var centerScreenY = 0f
    private var highlighted = -1
    private var highlightedSlotEnteredAtMs = 0L
    private var slotLongPressRunnable: Runnable? = null
    private var slotLongPressTrackingIndex = -1
    private var slotLongPressArmed = false
    private val viewLocationOnScreen = IntArray(2)

    fun configure(
        appSettings: AppSettings,
        settings: FingertipRingSettings,
        slotActions: List<GestureAction>,
        anchorX: Float,
        anchorY: Float,
        initialHighlight: Int,
    ) {
        this.appSettings = appSettings
        ringSettings = settings
        slots = slotActions
        centerScreenX = anchorX
        centerScreenY = anchorY
        setHighlighted(initialHighlight, haptic = false)
        invalidate()
    }

    fun installBrowseTouchHandler(
        onMove: (Float, Float) -> Unit,
        onRelease: (Float, Float) -> Unit,
        onCancel: () -> Unit,
    ) {
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    onMove(event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    onRelease(event.rawX, event.rawY)
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    onCancel()
                    true
                }
                else -> false
            }
        }
    }

    fun updatePointer(rawX: Float, rawY: Float) {
        val (localX, localY) = screenToLocal(rawX, rawY)
        val (centerX, centerY) = screenCenterLocal()
        val next = FingertipRingGeometry.slotIndexAtFinger(
            centerX = centerX,
            centerY = centerY,
            fingerX = localX,
            fingerY = localY,
            slotCount = slots.size,
            orbitRadiusPx = ringSettings.orbitRadiusPx,
            iconSizePx = ringSettings.iconSizePx,
        )
        setHighlighted(next, haptic = true)
        invalidate()
    }

    fun selectionSlot(): Int = highlighted.takeIf { it >= 0 } ?: -1

    fun selectionPressDurationMs(): Long {
        if (highlighted < 0) return 0L
        return (SystemClock.uptimeMillis() - highlightedSlotEnteredAtMs).coerceAtLeast(0L)
    }

    fun longPressArmedForSelection(): Boolean {
        if (highlighted < 0) return false
        val action = slots.getOrElse(highlighted) { GestureAction.None }
        if (!appSettings.launchPolicyLongPressEligible() || !action.usesLaunchPolicy()) return false
        return slotLongPressArmed ||
            selectionPressDurationMs() >= appSettings.effectiveLongPressDurationMs()
    }

    fun cancelSlotLongPress() {
        slotLongPressRunnable?.let { removeCallbacks(it) }
        slotLongPressRunnable = null
        slotLongPressTrackingIndex = -1
        slotLongPressArmed = false
    }

    private fun setHighlighted(next: Int, haptic: Boolean) {
        if (next == highlighted) return
        cancelSlotLongPress()
        highlighted = next
        if (next >= 0) {
            highlightedSlotEnteredAtMs = SystemClock.uptimeMillis()
            if (haptic) {
                HapticHelper.appTick(this, appSettings)
            }
            scheduleSlotLongPress(next)
        }
    }

    private fun scheduleSlotLongPress(slot: Int) {
        val action = slots.getOrElse(slot) { GestureAction.None }
        if (!appSettings.launchPolicyLongPressEligible() || !action.usesLaunchPolicy()) return
        slotLongPressTrackingIndex = slot
        val runnable = Runnable {
            if (slotLongPressTrackingIndex != slot) return@Runnable
            slotLongPressArmed = true
            HapticHelper.longThreshold(this, appSettings)
            invalidate()
        }
        slotLongPressRunnable = runnable
        postDelayed(runnable, appSettings.effectiveLongPressDurationMs().toLong())
    }

    private fun GestureAction.usesLaunchPolicy(): Boolean = when (this) {
        is GestureAction.LaunchApp -> windowMode.followsGlobalPolicy
        is GestureAction.LaunchShortcut -> true
        else -> false
    }

    private fun screenCenterLocal(): Pair<Float, Float> {
        getLocationOnScreen(viewLocationOnScreen)
        return centerScreenX - viewLocationOnScreen[0] to centerScreenY - viewLocationOnScreen[1]
    }

    private fun screenToLocal(screenX: Float, screenY: Float): Pair<Float, Float> {
        getLocationOnScreen(viewLocationOnScreen)
        return screenX - viewLocationOnScreen[0] to screenY - viewLocationOnScreen[1]
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (centerX, centerY) = screenCenterLocal()
        FingertipRingRenderer.draw(
            context = context,
            canvas = canvas,
            slots = slots,
            centerX = centerX,
            centerY = centerY,
            highlightedSlot = highlighted,
            orbitRadiusPx = ringSettings.orbitRadiusPx,
            iconSizePx = ringSettings.iconSizePx,
            density = resources.displayMetrics.density,
            activityShortcuts = appSettings.activityShortcuts,
            shellCommands = appSettings.shellCommands,
        )
    }
}
