package com.slideindex.app.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerEdgeBar
import com.slideindex.app.settings.FloatingPointerEdgeSide
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

data class FloatingPointerEdgeActionSegment(
    val side: FloatingPointerEdgeSide,
    val action: GestureAction,
    val wedgeRect: Rect,
    val glowRect: Rect,
    val segmentStart: Offset,
    val segmentEnd: Offset,
    val iconAnchor: Offset,
    val colorArgb: Int,
    var proximity: Float = 0f
)

object FloatingPointerEdgeActionsEngine {
    private const val EDGE_INSET_DP = 1f
    private const val WEDGE_CORNER_INSET_DP = 1.35f

    fun previewRadiusPx(settings: AppSettings, density: Float): Float {
        if (settings.floatingPointerEdgePreviewSensitivity <= 0) return 0f
        return settings.floatingPointerEdgePreviewSensitivity * 25f * density
    }

    fun previewGlowWidthPx(settings: AppSettings, density: Float): Float {
        if (settings.floatingPointerEdgePreviewGlowSize <= 0) return 0f
        return settings.floatingPointerEdgePreviewGlowSize * 10f * density
    }

    fun thresholdPx(settings: AppSettings, density: Float): Float =
        settings.floatingPointerEdgeThresholdDp.coerceIn(5f, 160f) * density

    fun buildSegments(
        settings: AppSettings,
        screenWidth: Float,
        screenHeight: Float,
        density: Float
    ): List<FloatingPointerEdgeActionSegment> {
        val config = settings.floatingPointerEdgeActionsConfig
        val glowWidth = previewGlowWidthPx(settings, density)
        val wedgeWidth = settings.floatingPointerEdgeVisualSizeDp.coerceAtLeast(0f) * density
        val edgeInset = EDGE_INSET_DP * density
        val cornerInset = wedgeWidth / WEDGE_CORNER_INSET_DP
        val color = settings.floatingPointerEdgeVisualColorArgb
        val segments = mutableListOf<FloatingPointerEdgeActionSegment>()

        fun appendBar(
            side: FloatingPointerEdgeSide,
            bar: FloatingPointerEdgeBar,
            axisLength: Float,
            alongAxis: (start: Float, end: Float) -> Pair<Offset, Offset>,
            wedgeForRange: (start: Float, end: Float) -> Rect,
            glowForRange: (start: Float, end: Float) -> Rect,
            iconForMidpoint: (mid: Float) -> Offset
        ) {
            if (!bar.enabled) return
            val slots = bar.layoutSlots()
            val totalWeight = bar.totalWeight()
            var weightOffset = 0
            slots.forEach { slot ->
                val segmentLength = axisLength * slot.sizeWeight / totalWeight.toFloat()
                val start = weightOffset * axisLength / totalWeight.toFloat()
                val end = start + segmentLength
                weightOffset += slot.sizeWeight
                if (slot.action.type == com.slideindex.app.gesture.GestureActionType.NONE) return@forEach
                val (segmentStart, segmentEnd) = alongAxis(start, end)
                segments += FloatingPointerEdgeActionSegment(
                    side = side,
                    action = slot.action,
                    wedgeRect = wedgeForRange(start, end),
                    glowRect = glowForRange(start, end),
                    segmentStart = segmentStart,
                    segmentEnd = segmentEnd,
                    iconAnchor = iconForMidpoint((start + end) / 2f),
                    colorArgb = color
                )
            }
        }

        appendBar(
            side = FloatingPointerEdgeSide.LEFT,
            bar = config.left,
            axisLength = screenHeight,
            alongAxis = { start, end ->
                Offset(0f, start) to Offset(0f, end)
            },
            wedgeForRange = { start, end ->
                Rect(
                    left = -wedgeWidth,
                    top = if (start <= edgeInset) cornerInset else start + edgeInset,
                    right = wedgeWidth,
                    bottom = if (end >= screenHeight - edgeInset) screenHeight - cornerInset else end - edgeInset
                )
            },
            glowForRange = { start, end ->
                Rect(left = -glowWidth, top = start, right = 0f, bottom = end)
            },
            iconForMidpoint = { mid -> Offset(0f, mid) }
        )

        appendBar(
            side = FloatingPointerEdgeSide.RIGHT,
            bar = config.right,
            axisLength = screenHeight,
            alongAxis = { start, end ->
                Offset(screenWidth, start) to Offset(screenWidth, end)
            },
            wedgeForRange = { start, end ->
                Rect(
                    left = screenWidth - wedgeWidth,
                    top = if (start <= edgeInset) cornerInset else start + edgeInset,
                    right = screenWidth + wedgeWidth,
                    bottom = if (end >= screenHeight - edgeInset) screenHeight - cornerInset else end - edgeInset
                )
            },
            glowForRange = { start, end ->
                Rect(
                    left = screenWidth,
                    top = start,
                    right = screenWidth + glowWidth,
                    bottom = end
                )
            },
            iconForMidpoint = { mid -> Offset(screenWidth, mid) }
        )

        appendBar(
            side = FloatingPointerEdgeSide.TOP,
            bar = config.top,
            axisLength = screenWidth,
            alongAxis = { start, end ->
                Offset(start, 0f) to Offset(end, 0f)
            },
            wedgeForRange = { start, end ->
                Rect(
                    left = if (start <= edgeInset) cornerInset else start + edgeInset,
                    top = -wedgeWidth,
                    right = if (end >= screenWidth - edgeInset) screenWidth - cornerInset else end - edgeInset,
                    bottom = wedgeWidth
                )
            },
            glowForRange = { start, end ->
                Rect(left = start, top = -glowWidth, right = end, bottom = 0f)
            },
            iconForMidpoint = { mid -> Offset(mid, 0f) }
        )

        appendBar(
            side = FloatingPointerEdgeSide.BOTTOM,
            bar = config.bottom,
            axisLength = screenWidth,
            alongAxis = { start, end ->
                Offset(start, screenHeight) to Offset(end, screenHeight)
            },
            wedgeForRange = { start, end ->
                Rect(
                    left = if (start <= edgeInset) cornerInset else start + edgeInset,
                    top = screenHeight - wedgeWidth,
                    right = if (end >= screenWidth - edgeInset) screenWidth - cornerInset else end - edgeInset,
                    bottom = screenHeight + wedgeWidth
                )
            },
            glowForRange = { start, end ->
                Rect(
                    left = start,
                    top = screenHeight,
                    right = end,
                    bottom = screenHeight + glowWidth
                )
            },
            iconForMidpoint = { mid -> Offset(mid, screenHeight) }
        )

        return segments
    }

    fun updateProximity(
        segments: List<FloatingPointerEdgeActionSegment>,
        rawX: Float,
        rawY: Float,
        previewRadiusPx: Float
    ) {
        if (previewRadiusPx <= 0f) {
            segments.forEach { it.proximity = 0f }
            return
        }
        val point = Offset(rawX, rawY)
        segments.forEach { segment ->
            val nearest = nearestPointOnSegment(point, segment.segmentStart, segment.segmentEnd)
            val distance = hypot(
                (point.x - nearest.x).toDouble(),
                (point.y - nearest.y).toDouble()
            ).toFloat()
            segment.proximity = if (distance > previewRadiusPx) {
                0f
            } else {
                (1f - distance / previewRadiusPx).coerceIn(0f, 1f)
            }
        }
    }

    fun shouldShowPreview(
        settings: AppSettings,
        segments: List<FloatingPointerEdgeActionSegment>,
        gestureCaptureActive: Boolean,
        gestureReplayActive: Boolean,
        armedAction: GestureAction?
    ): Boolean {
        if (gestureCaptureActive || gestureReplayActive) return false
        if (previewRadiusPx(settings, 1f) <= 0f && previewGlowWidthPx(settings, 1f) <= 0f) return false
        if (armedAction != null) return false
        return segments.any { it.proximity > 0f }
    }

    fun resolveTriggeredAction(
        settings: AppSettings,
        screenWidth: Float,
        screenHeight: Float,
        clampedX: Float,
        clampedY: Float,
        rawX: Float,
        rawY: Float,
        density: Float
    ): GestureAction? {
        val threshold = thresholdPx(settings, density)
        val overscrollX = clampedX - rawX
        val overscrollY = clampedY - rawY
        val config = settings.floatingPointerEdgeActionsConfig
        var candidate: GestureAction? = null

        fun pick(side: FloatingPointerEdgeSide, coordinate: Float, axisLength: Float) {
            val bar = config.bar(side)
            if (!bar.enabled) return
            val slots = bar.layoutSlots()
            if (slots.isEmpty()) return
            val index = ceil((coordinate / (axisLength / bar.totalWeight().toFloat())).toDouble()).toInt() - 1
            val slot = slots.getOrNull(index.coerceAtLeast(0).coerceAtMost(slots.lastIndex)) ?: return
            if (slot.action.type == com.slideindex.app.gesture.GestureActionType.NONE) return
            candidate = slot.action
        }

        if (config.left.enabled && overscrollX > threshold) {
            pick(FloatingPointerEdgeSide.LEFT, clampedY, screenHeight)
        }
        if (config.right.enabled && overscrollX < -threshold) {
            pick(FloatingPointerEdgeSide.RIGHT, clampedY, screenHeight)
        }
        if (config.top.enabled && overscrollY > threshold &&
            (candidate == null || abs(overscrollY) > abs(overscrollX))
        ) {
            pick(FloatingPointerEdgeSide.TOP, clampedX, screenWidth)
        }
        if (config.bottom.enabled && overscrollY < -threshold &&
            (candidate == null || abs(overscrollY) > abs(overscrollX))
        ) {
            pick(FloatingPointerEdgeSide.BOTTOM, clampedX, screenWidth)
        }
        return candidate
    }

    private fun nearestPointOnSegment(point: Offset, start: Offset, end: Offset): Offset {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= 0f) return start
        val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSq).coerceIn(0f, 1f)
        return Offset(start.x + dx * t, start.y + dy * t)
    }
}
