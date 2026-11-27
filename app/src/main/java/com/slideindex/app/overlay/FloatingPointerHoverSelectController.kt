package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import com.slideindex.app.inspire.PickPrefetchCache
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
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
 * RegionalPick-style pause → element select / region screenshot for continued floating-pointer
 * handoff. Publishes chrome to [FloatingPointerSession.hoverSelectChrome].
 */
internal class FloatingPointerHoverSelectController(
    private val session: FloatingPointerSession,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var active = false
    private var paused = false
    private var regionalActive = false
    private var selectionStart: Offset? = null
    private var selectionPreviewBounds: Rect? = null
    private var pickAnchor = Offset.Zero
    private var lastPauseScheduleX = Float.NaN
    private var lastPauseScheduleY = Float.NaN
    private var pauseRunnable: Runnable? = null
    private var boundsLookupGeneration = 0
    private var initialCacheRunnable: Runnable? = null

    val hasPickIntent: Boolean
        get() = paused || selectionStart != null || regionalActive

    fun begin() {
        reset()
        active = true
        pickAnchor = Offset(session.pointerX.floatValue, session.pointerY.floatValue)
        lastPauseScheduleX = pickAnchor.x
        lastPauseScheduleY = pickAnchor.y
        PickPrefetchCache.invalidate()
        FloatBallPreviewBoundsCache.invalidate()
        schedulePauseTimer()
        scheduleInitialPreviewBoundsCache()
        applyPreviewBoundsFromCache()
        publishChrome()
    }

    fun onPointerMoved(pointerX: Float, pointerY: Float, density: Float) {
        if (!active) return
        pickAnchor = Offset(pointerX, pointerY)
        val start = selectionStart
        if (start != null) {
            updateRegionalModeOnMove(start, density)
        } else if (paused) {
            paused = false
            selectionPreviewBounds = null
        }
        schedulePauseTimerIfMoved(density)
        applyPreviewBoundsFromCache()
        publishChrome()
    }

    fun cancel() {
        reset()
    }

    /**
     * @return true if a pick was submitted.
     */
    fun finishAndSubmit(host: Context, settings: AppSettings): Boolean {
        if (!active || !hasPickIntent) {
            reset()
            return false
        }
        if (!regionalActive) {
            ensurePreviewBoundsForPick()
        }
        val submitted = submitPick(host, settings)
        reset()
        return submitted
    }

    private fun updateRegionalModeOnMove(start: Offset, density: Float) {
        val movePx = session.regionalCancelSlopPx()
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

    private fun onCursorPaused() {
        if (!active || paused) return
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
        publishChrome()
    }

    /**
     * 第二根手指按下：撤销区域截图框，**立即**回到取词态（不用等十字退回起点、也不用再等一次超时）。
     *
     * 锚点重置成**当前**十字位置，而不是最初的超时点：指针与手指是 1:1 位移，把十字挪回起点
     * 会在下一帧 MOVE 被立刻拉回，视觉上闪一下；重置到当前位置则天然带滞回 —— 要再移出
     * [FloatingPointerSession.regionalCancelSlopPx] 才会重新进区域态，不会按下第二指就弹回去。
     *
     * @return true 表示确实从区域态回退了（此时事件应当被消费）。
     */
    fun revertRegionalToPick(): Boolean {
        if (!active || !regionalActive) return false
        regionalActive = false
        cancelPauseTimer()
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        selectionPreviewBounds = null
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
        publishChrome()
        return true
    }

    private fun launchPreviewBoundsLookupFallback(anchor: Offset) {
        val generation = ++boundsLookupGeneration
        scope.launch(Dispatchers.Default) {
            val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
                rawX = anchor.x,
                rawY = anchor.y
            )
            withContext(Dispatchers.Main) {
                if (generation != boundsLookupGeneration) return@withContext
                if (!active || !paused || regionalActive) return@withContext
                if (bounds != null) {
                    selectionPreviewBounds = bounds
                    maybeStartPickPrefetch()
                    publishChrome()
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

    private fun submitPick(host: Context, settings: AppSettings): Boolean {
        val end = pickAnchor
        val start = selectionStart ?: end
        val dragRect = rectBetween(start, end)
        val isRegionalDrag = regionalActive
        val previewBounds = selectionPreviewBounds
        if (!isRegionalDrag && previewBounds == null) return false

        val ocrFallbackEnabled = settings.floatBallOcrFallbackEnabled
        val ocrModelId = settings.floatBallOcrModelId

        return when {
            isRegionalDrag -> {
                val density = session.density
                val minSidePx = (REGIONAL_RECT_MIN_SIDE_DP * density).roundToInt()
                if (dragRect.width() < minSidePx || dragRect.height() < minSidePx) return false
                PickPerf.beginSession("fp_hover_regional")
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
                    PickPerf.endSession("END", "fp_hover_regional")
                }
                true
            }
            else -> {
                val bounds = previewBounds ?: return false
                val panelAnchorX = bounds.centerX().toFloat()
                val panelAnchorY = bounds.bottom.toFloat()
                PickPerf.beginSession("fp_hover_preview")
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
                    PickPerf.endSession("END", "fp_hover_preview")
                }
                true
            }
        }
    }

    private fun schedulePauseTimerIfMoved(density: Float) {
        val movePx = session.regionalCancelSlopPx()
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
        mainHandler.postDelayed(runnable, session.hoverPauseDelayMs())
    }

    private fun cancelPauseTimer() {
        pauseRunnable?.let { mainHandler.removeCallbacks(it) }
        pauseRunnable = null
    }

    private fun scheduleInitialPreviewBoundsCache() {
        cancelInitialCacheRefresh()
        val runnable = Runnable {
            initialCacheRunnable = null
            if (!active) return@Runnable
            val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return@Runnable
            FloatBallPreviewBoundsCache.refresh(
                service = service,
                onReady = {
                    if (!active) return@refresh
                    applyPreviewBoundsFromCache()
                    publishChrome()
                }
            )
        }
        initialCacheRunnable = runnable
        mainHandler.postDelayed(runnable, INITIAL_CACHE_DELAY_MS)
    }

    private fun cancelInitialCacheRefresh() {
        initialCacheRunnable?.let { mainHandler.removeCallbacks(it) }
        initialCacheRunnable = null
    }

    private fun applyPreviewBoundsFromCache() {
        if (!active || regionalActive) return
        if (paused && selectionStart != null) return
        val cached = FloatBallPreviewBoundsCache.hitTestAt(pickAnchor.x, pickAnchor.y)
        if (cached != null) {
            selectionPreviewBounds = cached
        }
    }

    private fun publishChrome() {
        val hintMode = when {
            regionalActive -> FloatBallCursorPreviewView.HintMode.SCREENSHOT
            paused -> FloatBallCursorPreviewView.HintMode.TEXT
            else -> FloatBallCursorPreviewView.HintMode.HIDDEN
        }
        val start = selectionStart
        session.hoverSelectChrome.value = FloatingPointerHoverSelectChrome(
            visible = active && (paused || regionalActive || selectionPreviewBounds != null),
            paused = paused,
            regionalActive = regionalActive,
            selectionStartX = start?.x ?: 0f,
            selectionStartY = start?.y ?: 0f,
            hasSelectionStart = start != null,
            previewLeft = selectionPreviewBounds?.left ?: 0,
            previewTop = selectionPreviewBounds?.top ?: 0,
            previewRight = selectionPreviewBounds?.right ?: 0,
            previewBottom = selectionPreviewBounds?.bottom ?: 0,
            hasPreviewBounds = selectionPreviewBounds != null,
            pickAnchorX = pickAnchor.x,
            pickAnchorY = pickAnchor.y,
            hintMode = hintMode
        )
    }

    private fun reset() {
        cancelPauseTimer()
        cancelInitialCacheRefresh()
        boundsLookupGeneration++
        active = false
        paused = false
        regionalActive = false
        selectionStart = null
        selectionPreviewBounds = null
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        session.hoverSelectChrome.value = FloatingPointerHoverSelectChrome()
    }

    private fun rectBetween(start: Offset, end: Offset): Rect {
        val left = min(start.x, end.x).roundToInt()
        val top = min(start.y, end.y).roundToInt()
        val right = max(start.x, end.x).roundToInt()
        val bottom = max(start.y, end.y).roundToInt()
        return Rect(left, top, right, bottom)
    }

    private companion object {
        const val REGIONAL_RECT_MIN_SIDE_DP = 3f
        const val INITIAL_CACHE_DELAY_MS = 500L
    }
}

internal data class FloatingPointerHoverSelectChrome(
    val visible: Boolean = false,
    val paused: Boolean = false,
    val regionalActive: Boolean = false,
    val selectionStartX: Float = 0f,
    val selectionStartY: Float = 0f,
    val hasSelectionStart: Boolean = false,
    val previewLeft: Int = 0,
    val previewTop: Int = 0,
    val previewRight: Int = 0,
    val previewBottom: Int = 0,
    val hasPreviewBounds: Boolean = false,
    val pickAnchorX: Float = 0f,
    val pickAnchorY: Float = 0f,
    val hintMode: FloatBallCursorPreviewView.HintMode = FloatBallCursorPreviewView.HintMode.HIDDEN
) {
    fun previewBoundsOrNull(): Rect? =
        if (hasPreviewBounds) Rect(previewLeft, previewTop, previewRight, previewBottom) else null
}
