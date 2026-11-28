package com.slideindex.app.gesture

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.graphics.RectF
import com.slideindex.app.overlay.KeyboardTriggerImeState
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.keyboardTriggerBehavior
import com.slideindex.app.settings.keyboardTriggerNarrowScale
import com.slideindex.app.settings.maxEdgeTriggerWidthDp
import com.slideindex.app.settings.triggerHandleEdgeWidthDp
import com.slideindex.app.settings.interceptWindowWidthDp
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.util.coerceSafe
import kotlin.math.roundToInt

data class CollapsedWindowBounds(
    val widthPx: Int,
    val heightPx: Int,
    val yPx: Int,
    val xPx: Int = 0
)

class GestureZoneLayout(
    private val side: PanelSide,
    private var settings: AppSettings = AppSettings(),
    private var viewWidth: Int = 0,
    private var viewHeight: Int = 0,
    private var density: Float = 1f,
    private var sessionActive: Boolean = false,
    private var previewMode: Boolean = false,
    private var layoutHeight: Int = 0,
    private var windowOffsetY: Float = 0f,
    private var screenWidthPx: Int = 0,
    private var screenHeightPx: Int = 0
) {
    fun update(
        settings: AppSettings,
        viewWidth: Int,
        viewHeight: Int,
        density: Float,
        sessionActive: Boolean,
        previewMode: Boolean,
        layoutHeight: Int = viewHeight,
        windowOffsetY: Float = 0f,
        screenWidthPx: Int = 0,
        screenHeightPx: Int = 0
    ) {
        this.settings = settings
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        this.density = density
        this.sessionActive = sessionActive
        this.previewMode = previewMode
        this.layoutHeight = layoutHeight
        this.windowOffsetY = windowOffsetY
        if (screenWidthPx > 0) this.screenWidthPx = screenWidthPx
        if (screenHeightPx > 0) this.screenHeightPx = screenHeightPx
    }

    fun triggerZoneRects(): List<Pair<String, RectF>> =
        settings.triggerHandles(side).map { handle ->
            handle.id to rectForHandle(handle)
        }

    fun triggerZoneRect(handleId: String? = null): RectF {
        if (handleId != null) {
            return settings.triggerHandle(side, handleId)?.let { rectForHandle(it) } ?: triggerZoneUnionRect()
        }
        return triggerZoneUnionRect()
    }

    fun triggerZoneUnionRect(): RectF {
        val rects = triggerZoneRects().map { it.second }
        if (rects.isEmpty()) return RectF()
        var union = RectF(rects.first())
        rects.drop(1).forEach { union.union(it) }
        return union
    }

    fun findTriggerHandleAt(localX: Float, localY: Float): String? {
        // Later handles (e.g. a second trigger pair) win when zones overlap.
        return settings.triggerHandles(side).asReversed().firstOrNull { handle ->
            hitTestRectForHandle(handle).contains(localX, localY)
        }?.id
    }

    /**
     * 字母索引条矩形。
     *
     * 纵向位置由 `settings.indexVerticalPositionFraction` 决定（0 = 顶边贴到上边距、1 = 底边贴到下边距、
     * 0.5 = 旧版的垂直居中）；`indexHeightFraction` 只管长度。两者都要参与夹取：条越长，可移动
     * 的行程越短，越靠近两端越会被 [coerceSafe] 收回。
     */
    fun indexRailRect(): RectF {
        if (side.isVerticalEdge) return RectF()
        val refHeight = referenceHeight()
        if (viewWidth <= 0 || refHeight <= 0) return RectF()
        val w = railVisualWidth().coerceAtMost(edgeWidthPx().toFloat())
        val indexH = refHeight * settings.indexHeightFraction
        val margin = dp(8f)
        val travel = (refHeight - indexH - margin * 2f).coerceAtLeast(0f)
        val positionFraction = settings.indexVerticalPositionFraction.coerceIn(0f, 1f)
        var top = margin + travel * positionFraction - windowOffsetY
        top = top.coerceSafe(margin - windowOffsetY, refHeight - indexH - margin - windowOffsetY)
        return when (side) {
            PanelSide.LEFT -> RectF(0f, top, w, top + indexH)
            PanelSide.RIGHT -> RectF(viewWidth - w, top, viewWidth.toFloat(), top + indexH)
            PanelSide.BOTTOM, PanelSide.TOP -> RectF()
        }
    }

    fun edgeWidthPx(): Int {
        return (settings.maxEdgeTriggerWidthDp(side) * density)
            .toInt()
            .coerceAtLeast(dp(TriggerHandle.MIN_EDGE_WIDTH_DP).toInt())
    }

    fun edgeWidthPxForHandle(handle: TriggerHandle): Int {
        return (settings.triggerHandleEdgeWidthDp(side, handle.id) * density)
            .toInt()
            .coerceAtLeast(dp(TriggerHandle.MIN_EDGE_WIDTH_DP).toInt())
    }

    fun interceptZoneRect(): RectF {
        if (side.isVerticalEdge || !settings.interceptSystemBackGesture) return triggerZoneUnionRect()
        val trigger = triggerZoneUnionRect()
        val interceptWidth = settings.interceptWindowWidthDp(side) * density
        return when (side) {
            PanelSide.LEFT -> RectF(0f, trigger.top, interceptWidth, trigger.bottom)
            PanelSide.RIGHT -> RectF(viewWidth - interceptWidth, trigger.top, viewWidth.toFloat(), trigger.bottom)
            PanelSide.BOTTOM, PanelSide.TOP -> trigger
        }
    }

    fun containsInterceptZone(localX: Float, localY: Float): Boolean =
        interceptZoneRect().contains(localX, localY)

    fun containsInterceptZoneAtScreen(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {
        if (side.isVerticalEdge || !settings.interceptSystemBackGesture) {
            return containsTriggerAtScreen(rawX, rawY, localX, localY)
        }
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            val interceptWidth = settings.interceptWindowWidthDp(side) * density
            val trigger = settings.triggerHandles(side)
            if (trigger.isEmpty()) return false
            var top = screenHeightPx.toFloat()
            var bottom = 0f
            trigger.forEach { handle ->
                top = minOf(top, screenHeightPx * handle.topFraction)
                bottom = maxOf(bottom, screenHeightPx * (handle.topFraction + handle.heightFraction))
            }
            val rect = when (side) {
                PanelSide.LEFT -> RectF(0f, top, interceptWidth, bottom)
                PanelSide.RIGHT -> RectF(
                    screenWidthPx - interceptWidth,
                    top,
                    screenWidthPx.toFloat(),
                    bottom
                )
                PanelSide.BOTTOM, PanelSide.TOP -> RectF()
            }
            return rect.contains(rawX, rawY)
        }
        return containsInterceptZone(localX, localY)
    }

    fun railVisualWidth(): Float = dp(22f)

    fun containsTrigger(localX: Float, localY: Float): Boolean =
        findTriggerHandleAt(localX, localY) != null

    /** Hit-test in screen coordinates so trigger detection stays correct while the overlay window moves/resizes. */
    fun containsTriggerAtScreen(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            return findTriggerHandleAtScreen(rawX, rawY) != null
        }
        return containsTrigger(localX, localY)
    }

    fun findTriggerHandleAtScreen(rawX: Float, rawY: Float): String? {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return null
        return settings.triggerHandles(side).asReversed().firstOrNull { handle ->
            val rect = screenHitRectForHandle(handle)
            rect.contains(rawX, rawY)
        }?.id
    }

    /** 与 [findTriggerHandleAtScreen] 一致的屏幕坐标条带，供路径识别使用。 */
    fun screenTriggerStripBounds(handleId: String? = null): RectF {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) {
            return triggerZoneRect(handleId)
        }
        val handle = when {
            handleId != null -> settings.triggerHandle(side, handleId)
            else -> null
        } ?: settings.triggerHandles(side).firstOrNull()
            ?: return RectF()
        return screenHitRectForHandle(handle)
    }

    /** 与 [SideOverlayWindowManager] 触钮 capture 窗口 bounds 一致（仅触钮宽度，不含光晕）。 */
    private fun captureWindowScreenRectForHandle(handle: TriggerHandle): RectF {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return RectF()
        val index = settings.triggerHandles(side).indexOfFirst { it.id == handle.id }
        if (index < 0) return RectF()
        return keyboardAdjustedCaptureBounds()
            .getOrNull(index)
            ?.toScreenRect(side, screenWidthPx, screenHeightPx)
            ?: RectF()
    }

    private fun keyboardAdjustedCaptureBounds(): List<CollapsedWindowBounds> {
        val raw = computeTouchCaptureWindowBounds(
            settings = settings,
            side = side,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density,
        )
        val isLandscape = screenWidthPx > screenHeightPx
        return KeyboardTriggerBoundsAdjuster.adjustCollapsedBounds(
            bounds = raw,
            side = side,
            behavior = settings.keyboardTriggerBehavior(isLandscape),
            imeTop = KeyboardTriggerImeState.imeTopOrNull(),
            density = density,
            narrowScale = settings.keyboardTriggerNarrowScale(isLandscape),
        )
    }

    private fun screenHitRectForHandle(handle: TriggerHandle): RectF =
        captureWindowScreenRectForHandle(handle)

    fun isInRailZone(localX: Float): Boolean {
        if (side.isVerticalEdge) return false
        val rail = indexRailRect()
        return when (side) {
            PanelSide.LEFT -> localX <= rail.right + dp(10f)
            PanelSide.RIGHT -> localX >= rail.left - dp(10f)
            PanelSide.BOTTOM, PanelSide.TOP -> false
        }
    }

    private fun rectForHandle(handle: TriggerHandle): RectF {
        if (viewWidth <= 0 || viewHeight <= 0) return RectF()
        if (side.isVerticalEdge) {
            val refWidth = effectiveScreenWidth().toFloat()
            val leftOnScreen = handle.topFraction.coerceIn(0f, 1f) * refWidth
            val rightOnScreen = handle.bottomFraction.coerceIn(0f, 1f) * refWidth
            val h = edgeWidthPxForHandle(handle).toFloat()
            return if (side == PanelSide.BOTTOM) {
                val bottom = effectiveScreenBottom()
                val top = (bottom - h).coerceAtLeast(0f)
                RectF(leftOnScreen, top, rightOnScreen, bottom)
            } else {
                RectF(leftOnScreen, 0f, rightOnScreen, h)
            }
        }
        val refHeight = referenceHeight().toFloat()
        val top = handle.topFraction.coerceIn(0f, 1f) * refHeight - windowOffsetY
        val bottom = handle.bottomFraction.coerceIn(0f, 1f) * refHeight - windowOffsetY
        val w = edgeWidthPxForHandle(handle).toFloat()
        return when (side) {
            PanelSide.LEFT -> RectF(0f, top, w, bottom)
            PanelSide.RIGHT -> RectF(viewWidth - w, top, viewWidth.toFloat(), bottom)
            PanelSide.BOTTOM, PanelSide.TOP -> RectF()
        }
    }

    private fun hitTestRectForHandle(handle: TriggerHandle): RectF {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return rectForHandle(handle)
        return captureWindowScreenRectForHandle(handle).apply {
            if (side.isHorizontalEdge) offset(0f, -windowOffsetY)
        }
    }

    private fun referenceHeight(): Int =
        layoutHeight.coerceAtLeast(viewHeight).coerceAtLeast(1)

    private fun dp(value: Float): Float = value * density

    private fun effectiveScreenWidth(): Int =
        screenWidthPx.coerceAtLeast(viewWidth).coerceAtLeast(1)

    private fun effectiveScreenBottom(): Float {
        val refHeight = screenHeightPx.coerceAtLeast(viewHeight).toFloat()
        return refHeight - windowOffsetY
    }

    companion object {
        private fun CollapsedWindowBounds.toScreenRect(
            side: PanelSide,
            screenWidthPx: Int,
            screenHeightPx: Int
        ): RectF = when (side) {
            PanelSide.LEFT -> RectF(
                0f,
                yPx.toFloat(),
                widthPx.toFloat(),
                (yPx + heightPx).toFloat()
            )
            PanelSide.RIGHT -> RectF(
                (screenWidthPx - widthPx).toFloat(),
                yPx.toFloat(),
                screenWidthPx.toFloat(),
                (yPx + heightPx).toFloat()
            )
            PanelSide.BOTTOM -> RectF(
                xPx.toFloat(),
                (screenHeightPx - heightPx).toFloat(),
                (xPx + widthPx).toFloat(),
                screenHeightPx.toFloat()
            )
            PanelSide.TOP -> RectF(
                xPx.toFloat(),
                0f,
                (xPx + widthPx).toFloat(),
                heightPx.toFloat()
            )
        }

        private fun computeGlowAwareEdgeWidthPx(
            edgeTriggerWidthDp: Float,
            handles: List<TriggerHandle>,
            density: Float
        ): Int {
            val edgeWidthPx = (edgeTriggerWidthDp * density)
                .toInt()
                .coerceAtLeast((TriggerHandle.MIN_EDGE_WIDTH_DP * density).toInt().coerceAtLeast(0))
            val maxHaloWidthPx = handles.maxOfOrNull { handle ->
                (handle.design.haloSizeDp * 2f * density).toInt()
            } ?: 0
            return edgeWidthPx.coerceAtLeast(maxHaloWidthPx)
        }

        private fun touchCaptureWidthPx(
            settings: AppSettings,
            side: PanelSide,
            handle: TriggerHandle,
            density: Float
        ): Int = (settings.triggerHandleEdgeWidthDp(side, handle.id) * density)
            .toInt()
            .coerceAtLeast((TriggerHandle.MIN_EDGE_WIDTH_DP * density).toInt().coerceAtLeast(0))

        private fun visualStripWidthPx(
            settings: AppSettings,
            side: PanelSide,
            handle: TriggerHandle,
            density: Float
        ): Int = computeGlowAwareEdgeWidthPx(
            edgeTriggerWidthDp = settings.triggerHandleEdgeWidthDp(side, handle.id),
            handles = listOf(handle),
            density = density
        )

        private fun maxVisualStripWidthPx(settings: AppSettings, side: PanelSide, density: Float): Int =
            settings.triggerHandles(side).maxOfOrNull { handle ->
                visualStripWidthPx(settings, side, handle, density)
            } ?: computeGlowAwareEdgeWidthPx(
                edgeTriggerWidthDp = settings.maxEdgeTriggerWidthDp(side),
                handles = emptyList(),
                density = density
            )

        private fun exclusionWidthPx(settings: AppSettings, side: PanelSide, density: Float): Int =
            (settings.interceptWindowWidthDp(side) * density)
                .toInt()
                .coerceAtLeast(maxVisualStripWidthPx(settings, side, density))

        /** Touchable edge strips: trigger width only; halo band passes through to the app below. */
        fun computeTouchCaptureWindowBounds(
            settings: AppSettings,
            side: PanelSide,
            screenWidthPx: Int,
            screenHeightPx: Int,
            density: Float
        ): List<CollapsedWindowBounds> {
            if (side.isVerticalEdge) {
                return computeHorizontalStripBounds(
                    settings = settings,
                    side = side,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    heightPxForHandle = { handle -> touchCaptureWidthPx(settings, side, handle, density) }
                )
            }
            return computeVerticalStripBounds(
                settings = settings,
                side = side,
                screenHeightPx = screenHeightPx,
                widthPxForHandle = { handle -> touchCaptureWidthPx(settings, side, handle, density) }
            )
        }

        /** Non-touchable visual strips (halo may extend beyond the touch capture width). */
        fun computeTriggerVisualWindowBounds(
            settings: AppSettings,
            side: PanelSide,
            screenWidthPx: Int,
            screenHeightPx: Int,
            density: Float
        ): List<CollapsedWindowBounds> = computeCaptureWindowBounds(
            settings,
            side,
            screenWidthPx,
            screenHeightPx,
            density
        )

        /** One capture window per enabled trigger handle so gaps along the edge stay interactive. */
        fun computeCaptureWindowBounds(
            settings: AppSettings,
            side: PanelSide,
            screenWidthPx: Int,
            screenHeightPx: Int,
            density: Float
        ): List<CollapsedWindowBounds> {
            if (side.isVerticalEdge) {
                return computeHorizontalStripBounds(
                    settings = settings,
                    side = side,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    heightPxForHandle = { handle -> visualStripWidthPx(settings, side, handle, density) }
                )
            }
            return computeVerticalStripBounds(
                settings = settings,
                side = side,
                screenHeightPx = screenHeightPx,
                widthPxForHandle = { handle -> visualStripWidthPx(settings, side, handle, density) }
            )
        }

        /**
         * Wide, non-touchable strips that opt out of the system back-gesture region without
         * blocking taps/scrolls in the app below (SideGesture-style exclusion rects).
         */
        fun computeSystemGestureExclusionBounds(
            settings: AppSettings,
            side: PanelSide,
            screenWidthPx: Int,
            screenHeightPx: Int,
            density: Float
        ): List<CollapsedWindowBounds> {
            if (side.isVerticalEdge || !settings.interceptSystemBackGesture) return emptyList()
            return computeVerticalStripBounds(
                settings = settings,
                side = side,
                screenHeightPx = screenHeightPx,
                widthPxForHandle = { exclusionWidthPx(settings, side, density) }
            )
        }

        private fun computeHorizontalStripBounds(
            settings: AppSettings,
            side: PanelSide,
            screenWidthPx: Int,
            screenHeightPx: Int,
            heightPxForHandle: (TriggerHandle) -> Int
        ): List<CollapsedWindowBounds> {
            val handles = settings.triggerHandles(side)
            val anchorTop = side == PanelSide.TOP
            if (screenWidthPx <= 0 || screenHeightPx <= 0 || handles.isEmpty()) {
                return emptyList()
            }
            return handles.map { handle ->
                val (left, right) = configuredSpanPx(handle, screenWidthPx)
                val heightPx = heightPxForHandle(handle)
                CollapsedWindowBounds(
                    widthPx = right - left,
                    heightPx = heightPx.coerceAtLeast(0),
                    yPx = if (anchorTop) 0 else (screenHeightPx - heightPx.coerceAtLeast(0)).coerceAtLeast(0),
                    xPx = left
                )
            }
        }

        private fun computeVerticalStripBounds(
            settings: AppSettings,
            side: PanelSide,
            screenHeightPx: Int,
            widthPxForHandle: (TriggerHandle) -> Int
        ): List<CollapsedWindowBounds> {
            if (screenHeightPx <= 0) {
                return emptyList()
            }
            val handles = settings.triggerHandles(side)
            if (handles.isEmpty()) {
                return emptyList()
            }
            return handles.map { handle ->
                val (top, bottom) = configuredSpanPx(handle, screenHeightPx)
                CollapsedWindowBounds(
                    widthPx = widthPxForHandle(handle),
                    heightPx = bottom - top,
                    yPx = top
                )
            }
        }

        private fun configuredSpanPx(handle: TriggerHandle, screenLengthPx: Int): Pair<Int, Int> {
            if (!handle.topFraction.isFinite() || !handle.bottomFraction.isFinite()) return 0 to 0
            val start = (handle.topFraction.coerceIn(0f, 1f) * screenLengthPx).roundToInt()
            val end = (handle.bottomFraction.coerceIn(0f, 1f) * screenLengthPx).roundToInt()
            return start to end.coerceAtLeast(start)
        }

        fun computeCollapsedWindowBounds(
            settings: AppSettings,
            side: PanelSide,
            screenWidthPx: Int,
            screenHeightPx: Int,
            density: Float
        ): CollapsedWindowBounds {
            val bounds = computeCaptureWindowBounds(settings, side, screenWidthPx, screenHeightPx, density)
            if (bounds.size == 1) return bounds.first()
            val widthPx = maxVisualStripWidthPx(settings, side, density)
            if (screenHeightPx <= 0) {
                return CollapsedWindowBounds(widthPx = widthPx, heightPx = 1, yPx = 0)
            }
            var top = screenHeightPx
            var bottom = 0
            bounds.forEach { bound ->
                top = minOf(top, bound.yPx)
                bottom = maxOf(bottom, bound.yPx + bound.heightPx)
            }
            return CollapsedWindowBounds(
                widthPx = widthPx,
                heightPx = (bottom - top).coerceAtLeast(1),
                yPx = top.coerceAtLeast(0)
            )
        }
    }
}
