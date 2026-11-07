package com.slideindex.app.overlay

import com.slideindex.app.overlay.overlayHostContext

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Offset
import com.slideindex.app.inspire.PickPrefetchCache
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.util.PermissionHelper
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight edge-gesture regional pick: one NOT_TOUCHABLE display window only.
 * Touch events are forwarded from edge capture — never a second full-screen touch window.
 */
@SuppressLint("StaticFieldLeak")
object RegionalPickOverlay {
    private const val TAG = "RegionalPickOverlay"
    private const val REGIONAL_RECT_MIN_SIDE_DP = 3f
    /** FV I0: rebuild preview bounds cache after finger moves. */
    private const val CACHE_REFRESH_MS = 100L
    private const val CACHE_REFRESH_MOVE_DP = 3f
    private const val EDGE_MARGIN_DP = 8f
    /** Defer first a11y bounds scan — avoids stacking work with WM attach (~300ms crash window). */
    private const val INITIAL_CACHE_DELAY_MS = 500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dragSession = FloatBallDragSession()

    private var windowManager: WindowManager? = null
    private var displayHost: FrameLayout? = null
    private var cursorPreviewView: FloatBallCursorPreviewView? = null
    private var ballDragVisualView: FloatBallDragVisualView? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
    private var appContext: Context? = null
    private var settings: AppSettings? = null

    private var screenWidth = 0f
    private var screenHeight = 0f

    private var pickAnchor = Offset.Zero
    private var ballCenter = Offset.Zero
    private var lastFingerX = 0f
    private var lastFingerY = 0f
    private var dragSessionArmed = false
    private var selectionStart: Offset? = null
    private var selectionPreviewBounds: Rect? = null
    private var paused = false
    private var regionalActive = false
    private var sessionActive = false

    private var pauseRunnable: Runnable? = null
    private var cacheRefreshRunnable: Runnable? = null
    private var initialCacheRunnable: Runnable? = null
    private var boundsLookupGeneration = 0
    private var lastPauseScheduleX = Float.NaN
    private var lastPauseScheduleY = Float.NaN
    private var lastCacheRefreshX = Float.NaN
    private var lastCacheRefreshY = Float.NaN
    private var liveBoundsLookupGeneration = 0

    private var ballVisualDockSide: FloatBallSide? = null
    private var edgePickDockSide: FloatBallSide = FloatBallSide.RIGHT

    var continuedGestureActive = false
        private set

    val isActive: Boolean get() = sessionActive

    fun isConsumingEdgeGestureTouch(): Boolean = continuedGestureActive

    fun armContinuedHandoff() {
        continuedGestureActive = true
    }

    fun onConfigurationChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onConfigurationChanged() }
            return
        }
        appContext?.let { refreshScreenMetrics(it) }
    }

    fun launchFromEdge(
        context: Context,
        appSettings: AppSettings,
        gestureStartRawY: Float,
        edgeSide: FloatBallSide,
        rawX: Float,
        rawY: Float
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                launchFromEdge(context, appSettings, gestureStartRawY, edgeSide, rawX, rawY)
            }
            return
        }
        Log.i(TAG, "launchFromEdge startY=$gestureStartRawY at ($rawX, $rawY) side=$edgeSide")
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "launchFromEdge: accessibility not enabled")
            continuedGestureActive = false
            EdgeContinuedOverlayLaunchCoordinator.clearAfterHandoffEnd()
            return
        }
        val hostContext = overlayHostContext()
            ?: run {
                Log.w(TAG, "launchFromEdge: host unavailable")
                continuedGestureActive = false
                EdgeContinuedOverlayLaunchCoordinator.clearAfterHandoffEnd()
                return
            }
        if (sessionActive) dismiss()
        if (!ensureDisplayWindow(hostContext, appSettings)) {
            Log.e(TAG, "launchFromEdge: display window unavailable")
            continuedGestureActive = false
            EdgeContinuedOverlayLaunchCoordinator.clearAfterHandoffEnd()
            return
        }
        FloatBallOverlay.hideChromeForEdgeRegionalPick()
        resetSessionState()
        sessionActive = true
        continuedGestureActive = true
        displayHost?.visibility = View.VISIBLE
        beginSessionAt(gestureStartRawY, edgeSide, rawX, rawY)
    }

    fun show(
        context: Context,
        appSettings: AppSettings,
        anchorRawX: Float?,
        anchorRawY: Float?,
        continueTouch: Boolean,
        gestureStartRawY: Float? = null,
        edgeSide: FloatBallSide? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, appSettings, anchorRawX, anchorRawY, continueTouch) }
            return
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility not enabled")
            return
        }
        val hostContext = overlayHostContext()
            ?: run {
                Log.w(TAG, "show: host unavailable")
                return
            }
        if (!continueTouch) return
        val rawX = anchorRawX ?: return
        val rawY = anchorRawY ?: return
        val startY = gestureStartRawY ?: rawY
        val side = edgeSide ?: if (rawX < screenWidthForContext(hostContext) / 2f) {
            FloatBallSide.LEFT
        } else {
            FloatBallSide.RIGHT
        }
        launchFromEdge(hostContext, appSettings, startY, side, rawX, rawY)
    }

    private fun screenWidthForContext(context: Context): Float =
        FloatBallScreenMetrics.bounds(context, windowManager).width

    private fun refreshScreenMetrics(context: Context) {
        val bounds = FloatBallScreenMetrics.bounds(context, windowManager)
        screenWidth = bounds.width
        screenHeight = bounds.height
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        continuedGestureActive = false
        sessionActive = false
        EdgeContinuedOverlayHandoff.clearIfInactive()
        cancelPauseTimer()
        cancelCacheRefresh()
        cancelInitialCacheRefresh()
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        FloatBallPreviewBoundsCache.invalidate()
        cursorPreviewView?.visibility = View.GONE
        ballDragVisualView?.release()
        ballVisualDockSide = null
        displayHost?.visibility = View.GONE
        mainHandler.post { FloatBallOverlay.restoreChromeAfterRegionalPick() }
        screenOffDismissReceiver.unregister()
        FloatBallPickResultPanel.releaseWarmUpShell()
    }

    fun forwardContinuedTouch(event: MotionEvent): Boolean {
        if (!continuedGestureActive) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                handleTouch(event.rawX, event.rawY, MotionEvent.ACTION_MOVE)
                true
            }
            MotionEvent.ACTION_UP -> {
                handleTouch(event.rawX, event.rawY, MotionEvent.ACTION_UP)
                continuedGestureActive = false
                EdgeContinuedOverlayHandoff.clearIfInactive()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                dismiss()
                continuedGestureActive = false
                EdgeContinuedOverlayHandoff.clearIfInactive()
                true
            }
            else -> false
        }
    }

    private fun ensureDisplayWindow(hostContext: Context, appSettings: AppSettings): Boolean {
        settings = appSettings
        if (displayHost != null) {
            refreshScreenMetrics(hostContext)
            return true
        }

        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val overlayContext = OverlayCompose.themedContext(hostContext)
        refreshScreenMetrics(hostContext)

        val preview = FloatBallCursorPreviewView(overlayContext)
        val ballVisual = FloatBallDragVisualView(overlayContext)
        val display = FrameLayout(overlayContext).apply {
            addView(
                preview,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                ballVisual,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            visibility = View.GONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(hostContext),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val added = runCatching { wm.addView(display, params) }.isSuccess
        if (!added) return false

        windowManager = wm
        displayHost = display
        cursorPreviewView = preview
        ballDragVisualView = ballVisual
        appContext = hostContext
        screenOffDismissReceiver.register(hostContext)
        return true
    }

    private fun handleTouch(rawX: Float, rawY: Float, action: Int) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val side = if (rawX < screenWidth / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT
                beginSessionAt(rawY, side, rawX, rawY)
            }
            MotionEvent.ACTION_MOVE -> onFingerMove(rawX, rawY)
            MotionEvent.ACTION_UP -> finishSession()
            MotionEvent.ACTION_CANCEL -> dismiss()
        }
    }

    private fun beginSessionAt(
        gestureStartRawY: Float,
        edgeSide: FloatBallSide,
        rawX: Float,
        rawY: Float
    ) {
        appContext?.let { refreshScreenMetrics(it) }
        val currentSettings = settings ?: return
        val view = cursorPreviewView ?: return
        val density = view.resources.displayMetrics.density
        val ballSize = FloatBallLayout.ballSize(currentSettings, density)
        val marginPx = (EDGE_MARGIN_DP * density).roundToInt()

        dragSession.reset()
        dragSessionArmed = false
        edgePickDockSide = edgeSide
        FloatBallEdgePickReplay.replayToTrigger(
            session = dragSession,
            settings = currentSettings,
            edgeSide = edgeSide,
            gestureStartRawY = gestureStartRawY,
            triggerRawX = rawX,
            triggerRawY = rawY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            marginPx = marginPx
        )
        lastFingerX = rawX
        lastFingerY = rawY
        dragSessionArmed = true
        applyPickStateFromSession(currentSettings, ballSize, marginPx, density)
        selectionStart = null
        selectionPreviewBounds = null
        paused = false
        regionalActive = false
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        FloatBallPreviewBoundsCache.invalidate()
        cursorPreviewView?.visibility = View.VISIBLE
        lastPauseScheduleX = pickAnchor.x
        lastPauseScheduleY = pickAnchor.y
        lastCacheRefreshX = Float.NaN
        lastCacheRefreshY = Float.NaN
        schedulePauseTimer()
        scheduleInitialPreviewBoundsCache()
        applyPreviewBoundsFromCache()
        syncCursorAppearance()
    }

    private fun onFingerMove(rawX: Float, rawY: Float) {
        updatePickFromFinger(rawX, rawY)
        val start = selectionStart
        if (start != null) {
            updateRegionalModeOnMove(start)
        } else if (paused) {
            paused = false
            selectionPreviewBounds = null
        }
        schedulePauseTimerIfMoved()
        schedulePreviewCacheRefresh()
        applyPreviewBoundsFromCache()
        syncCursorAppearance()
    }

    private fun updateRegionalModeOnMove(start: Offset) {
        val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
        val cancelSlopDp = settings?.floatBallRegionalCancelSlopDp ?: 16f
        val movePx = cancelSlopDp * density
        val distFromStart = hypot(pickAnchor.x - start.x, pickAnchor.y - start.y)
        if (regionalActive) {
            if (distFromStart < movePx) {
                regionalActive = false
                paused = false
                selectionStart = null
                selectionPreviewBounds = null
                boundsLookupGeneration++
                PickPrefetchCache.invalidate()
            }
            return
        }
        if (distFromStart >= movePx) {
            regionalActive = true
            boundsLookupGeneration++
            PickPrefetchCache.invalidate()
            selectionPreviewBounds = null
        }
    }

    private fun finishSession() {
        val host = appContext
        val currentSettings = settings
        if (host == null || currentSettings == null) {
            dismiss()
            return
        }
        val hadPickIntent = paused || selectionStart != null || regionalActive
        if (hadPickIntent) {
            if (!regionalActive) {
                ensurePreviewBoundsForPick()
            }
            submitPick(host, currentSettings)
        }
        dismiss()
    }

    private fun submitPick(host: Context, currentSettings: AppSettings) {
        val end = pickAnchor
        val start = selectionStart ?: end
        val dragRect = rectBetween(start, end)
        val isRegionalDrag = regionalActive
        val previewBounds = selectionPreviewBounds
        if (!isRegionalDrag && previewBounds == null) return

        val ocrFallbackEnabled = currentSettings.floatBallOcrFallbackEnabled
        val ocrModelId = currentSettings.floatBallOcrModelId

        when {
            isRegionalDrag -> {
                val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
                val minSidePx = (REGIONAL_RECT_MIN_SIDE_DP * density).roundToInt()
                if (dragRect.width() < minSidePx || dragRect.height() < minSidePx) return
                PickPerf.beginSession("regional_rect_gesture")
                val panelAnchorX = dragRect.centerX().toFloat()
                val panelAnchorY = dragRect.bottom.toFloat()
                FloatBallPickResultPanel.showLoading(
                    host,
                    panelAnchorX,
                    panelAnchorY,
                    PickResultTextSource.OCR
                )
                SlideIndexAccessibilityService.pickFloatBallOnRelease(
                    context = host,
                    startX = start.x,
                    startY = start.y,
                    endX = end.x,
                    endY = end.y,
                    regionalRect = true,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId
                ) { result ->
                    FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                    PickPerf.endSession("END", "regional_rect_gesture")
                }
            }
            else -> {
                val bounds = previewBounds ?: return
                val panelAnchorX = bounds.centerX().toFloat()
                val panelAnchorY = bounds.bottom.toFloat()
                PickPerf.beginSession("preview_bounds_gesture")
                FloatBallPickResultPanel.showLoading(
                    host,
                    panelAnchorX,
                    panelAnchorY,
                    PickResultTextSource.A11Y
                )
                SlideIndexAccessibilityService.pickFloatBallTextInRect(
                    context = host,
                    rect = bounds,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    previewBoundsPick = true
                ) { result ->
                    FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                    PickPerf.endSession("END", "preview_bounds_gesture")
                }
            }
        }
    }

    private fun updatePickFromFinger(rawX: Float, rawY: Float) {
        val currentSettings = settings ?: return
        val view = cursorPreviewView ?: return
        val density = view.resources.displayMetrics.density
        val ballSize = FloatBallLayout.ballSize(currentSettings, density)
        val marginPx = (EDGE_MARGIN_DP * density).roundToInt()

        if (!dragSessionArmed) {
            val edgeSide = if (rawX < screenWidth / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT
            beginSessionAt(rawY, edgeSide, rawX, rawY)
            return
        }

        dragSession.onFingerMove(rawX - lastFingerX, rawY - lastFingerY)
        lastFingerX = rawX
        lastFingerY = rawY
        applyPickStateFromSession(currentSettings, ballSize, marginPx, density)
        applyPreviewBoundsFromCache()
    }

    private fun applyPickStateFromSession(
        currentSettings: AppSettings,
        ballSize: FloatBallSize,
        marginPx: Int,
        density: Float
    ) {
        pickAnchor = dragSession.computePick(
            settings = currentSettings,
            ballSize = ballSize,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            marginPx = marginPx
        )
        ballCenter = dragSession.clampedBallCenter(
            ballSize = ballSize,
            marginPx = marginPx,
            screenWidth = screenWidth.roundToInt(),
            screenHeight = screenHeight.roundToInt()
        )
        val ballDockSide = FloatBallPickAnchor.dockSideForBallCenter(
            ballCenterX = ballCenter.x,
            screenWidth = screenWidth,
            fallbackDockSide = edgePickDockSide
        )
        updateBallLayout(currentSettings, ballDockSide, ballSize)
    }

    private fun updateBallLayout(
        currentSettings: AppSettings,
        dockSide: FloatBallSide,
        ballSize: FloatBallSize
    ) {
        val visual = ballDragVisualView ?: return
        if (visual.visibility != View.VISIBLE || ballVisualDockSide != dockSide) {
            visual.show(currentSettings, composeSnapshot = null, activeSide = dockSide)
            ballVisualDockSide = dockSide
        }
        visual.x = ballCenter.x - ballSize.widthPx / 2f
        visual.y = ballCenter.y - ballSize.heightPx / 2f
    }

    private fun onCursorPaused() {
        if (!sessionActive || paused) return
        cancelPauseTimer()
        regionalActive = false
        val anchor = pickAnchor
        selectionStart = anchor
        paused = true
        val bounds = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
        if (bounds != null) {
            selectionPreviewBounds = bounds
            maybeStartPickPrefetch()
        } else {
            launchPreviewBoundsLookupFallback(anchor)
        }
        syncCursorAppearance()
    }

    private fun launchPreviewBoundsLookupFallback(anchor: Offset) {
        val generation = ++boundsLookupGeneration
        overlayScope.launch(Dispatchers.Default) {
            val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
                rawX = anchor.x,
                rawY = anchor.y
            )
            withContext(Dispatchers.Main) {
                if (generation != boundsLookupGeneration) return@withContext
                if (!sessionActive || !paused || regionalActive) return@withContext
                if (bounds != null) {
                    selectionPreviewBounds = bounds
                    maybeStartPickPrefetch()
                    syncCursorAppearance()
                }
            }
        }
    }

    private fun ensurePreviewBoundsForPick() {
        if (selectionPreviewBounds != null) return
        val anchor = pickAnchor
        val cached = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
        if (cached != null) {
            selectionPreviewBounds = cached
            return
        }
        val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
            rawX = anchor.x,
            rawY = anchor.y
        )
        if (bounds != null) {
            selectionPreviewBounds = bounds
        }
    }

    /** Prefetch a11y text only — do not warmUp pick panel WM during drag (causes ~300ms crash). */
    private fun maybeStartPickPrefetch() {
        if (!paused) return
        val bounds = selectionPreviewBounds ?: return
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return
        PickPrefetchCache.startPreviewA11yPrefetch(
            service = service,
            rect = bounds,
            generation = boundsLookupGeneration
        )
    }

    private fun schedulePauseTimerIfMoved() {
        val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
        val cancelSlopDp = settings?.floatBallRegionalCancelSlopDp ?: 16f
        val movePx = cancelSlopDp * density
        if (!lastPauseScheduleX.isNaN() && !lastPauseScheduleY.isNaN()) {
            if (hypot(pickAnchor.x - lastPauseScheduleX, pickAnchor.y - lastPauseScheduleY) < movePx) {
                return
            }
        }
        lastPauseScheduleX = pickAnchor.x
        lastPauseScheduleY = pickAnchor.y
        schedulePauseTimer()
    }

    private fun schedulePauseTimer() {
        cancelPauseTimer()
        val runnable = Runnable { onCursorPaused() }
        pauseRunnable = runnable
        val pauseMs = (settings?.floatBallHoverPauseDelayMs ?: 400).toLong()
        mainHandler.postDelayed(runnable, pauseMs)
    }

    private fun cancelPauseTimer() {
        pauseRunnable?.let { mainHandler.removeCallbacks(it) }
        pauseRunnable = null
    }

    private fun scheduleInitialPreviewBoundsCache() {
        cancelInitialCacheRefresh()
        val runnable = Runnable {
            initialCacheRunnable = null
            if (!sessionActive) return@Runnable
            startPreviewBoundsCache()
        }
        initialCacheRunnable = runnable
        mainHandler.postDelayed(runnable, INITIAL_CACHE_DELAY_MS)
    }

    private fun cancelInitialCacheRefresh() {
        initialCacheRunnable?.let { mainHandler.removeCallbacks(it) }
        initialCacheRunnable = null
    }

    private fun startPreviewBoundsCache() {
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return
        FloatBallPreviewBoundsCache.refresh(
            service = service,
            onReady = {
                if (!sessionActive) return@refresh
                applyPreviewBoundsFromCache()
            }
        )
    }

    private fun schedulePreviewCacheRefresh() {
        val anchor = pickAnchor
        val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
        val movePx = CACHE_REFRESH_MOVE_DP * density
        if (!lastCacheRefreshX.isNaN() && !lastCacheRefreshY.isNaN()) {
            if (hypot(anchor.x - lastCacheRefreshX, anchor.y - lastCacheRefreshY) < movePx) {
                return
            }
        }
        lastCacheRefreshX = anchor.x
        lastCacheRefreshY = anchor.y
        cancelCacheRefresh()
        val runnable = Runnable {
            cacheRefreshRunnable = null
            if (!sessionActive) return@Runnable
            startPreviewBoundsCache()
        }
        cacheRefreshRunnable = runnable
        mainHandler.postDelayed(runnable, CACHE_REFRESH_MS)
    }

    private fun cancelCacheRefresh() {
        cacheRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        cacheRefreshRunnable = null
    }

    private fun applyPreviewBoundsFromCache() {
        if (!sessionActive) return
        if (regionalActive) return
        if (paused && selectionStart != null) return
        val cached = FloatBallPreviewBoundsCache.hitTestAt(pickAnchor.x, pickAnchor.y)
        if (cached != null) {
            updatePreviewBoundsIfChanged(cached)
            return
        }
        scheduleLivePreviewBoundsLookup()
    }

    private fun updatePreviewBoundsIfChanged(bounds: Rect) {
        val density = cursorPreviewView?.resources?.displayMetrics?.density ?: 1f
        val slopPx = (2f * density).roundToInt()
        val current = selectionPreviewBounds
        if (current == null || !previewBoundsStableEquals(current, bounds, slopPx)) {
            selectionPreviewBounds = bounds
            syncCursorAppearance()
        }
    }

    private fun scheduleLivePreviewBoundsLookup() {
        if (!sessionActive || regionalActive || (paused && selectionStart != null)) return
        val generation = ++liveBoundsLookupGeneration
        val x = pickAnchor.x
        val y = pickAnchor.y
        overlayScope.launch(Dispatchers.Default) {
            val bounds = SlideIndexAccessibilityService.findControlBoundsAt(rawX = x, rawY = y)
            withContext(Dispatchers.Main) {
                if (generation != liveBoundsLookupGeneration) return@withContext
                if (!sessionActive || regionalActive || (paused && selectionStart != null)) return@withContext
                if (bounds != null) {
                    updatePreviewBoundsIfChanged(bounds)
                }
            }
        }
    }

    private fun previewBoundsStableEquals(current: Rect, next: Rect, slopPx: Int): Boolean =
        kotlin.math.abs(current.left - next.left) <= slopPx &&
            kotlin.math.abs(current.top - next.top) <= slopPx &&
            kotlin.math.abs(current.right - next.right) <= slopPx &&
            kotlin.math.abs(current.bottom - next.bottom) <= slopPx

    private fun resetSessionState() {
        cancelPauseTimer()
        cancelCacheRefresh()
        cancelInitialCacheRefresh()
        boundsLookupGeneration++
        liveBoundsLookupGeneration++
        dragSession.reset()
        dragSessionArmed = false
        ballVisualDockSide = null
        edgePickDockSide = FloatBallSide.RIGHT
        lastFingerX = 0f
        lastFingerY = 0f
        pickAnchor = Offset.Zero
        ballCenter = Offset.Zero
        selectionStart = null
        selectionPreviewBounds = null
        paused = false
        regionalActive = false
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        lastCacheRefreshX = Float.NaN
        lastCacheRefreshY = Float.NaN
    }

    private fun syncCursorAppearance() {
        val view = cursorPreviewView ?: return
        val currentSettings = settings
        val hintMode = when {
            !paused -> FloatBallCursorPreviewView.HintMode.HIDDEN
            regionalActive -> FloatBallCursorPreviewView.HintMode.SCREENSHOT
            else -> FloatBallCursorPreviewView.HintMode.TEXT
        }
        view.setChromeState(
            visible = sessionActive,
            paused = paused,
            selectionStart = selectionStart,
            selectionPreviewBounds = selectionPreviewBounds,
            pickAnchor = pickAnchor,
            regionalDragActive = regionalActive,
            crossVisible = sessionActive,
            crossAlpha = 1f,
            crossPaused = paused,
            crossArmDp = currentSettings?.floatBallPickCrossArmDp?.coerceIn(4f, 16f) ?: 7.5f,
            hintMode = hintMode
        )
    }

    private fun rectBetween(start: Offset, end: Offset): Rect {
        val left = min(start.x, end.x).roundToInt()
        val top = min(start.y, end.y).roundToInt()
        val right = max(start.x, end.x).roundToInt()
        val bottom = max(start.y, end.y).roundToInt()
        return Rect(left, top, right, bottom)
    }
}
