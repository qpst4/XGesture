package com.slideindex.app.overlay.corner

import android.graphics.RectF
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.CornerGestureSettings
import kotlin.math.min

data class CornerShortcutSubMenuLayout(
    val itemRects: List<RectF>,
    val bounds: RectF,
) {
    fun containsFinger(x: Float, y: Float, slopPx: Float): Boolean {
        if (itemRects.isEmpty()) return false
        val hitBounds = RectF(bounds)
        hitBounds.inset(-slopPx, -slopPx)
        if (hitBounds.contains(x, y)) return true
        return itemRects.any { rect -> expand(rect, slopPx).contains(x, y) }
    }

    fun indexAt(x: Float, y: Float, slopPx: Float = 0f): Int =
        itemRects.indexOfFirst { rect -> expand(rect, slopPx).contains(x, y) }

    private fun expand(rect: RectF, slopPx: Float): RectF =
        RectF(rect.left - slopPx, rect.top - slopPx, rect.right + slopPx, rect.bottom + slopPx)
}

object CornerShortcutSubMenuLayoutCalculator {
    private const val PILL_HEIGHT_DP = 36f
    private const val PILL_MIN_TEXT_WIDTH_DP = 68f
    private const val PILL_MAX_WIDTH_DP = 220f
    private const val PILL_PADDING_RIGHT_DP = 12f
    private const val ICON_LEADING_DP = 10f
    private const val ICON_SIZE_DP = 20f
    private const val ICON_TEXT_GAP_DP = 8f
    private const val ITEM_GAP_DP = 6f
    private const val OFFSET_ABOVE_BUBBLE_DP = 10f

    fun iconContentWidthPx(density: Float): Float =
        (ICON_LEADING_DP + ICON_SIZE_DP + ICON_TEXT_GAP_DP) * density

    fun minPillWidthPx(density: Float): Float =
        iconContentWidthPx(density) +
            PILL_PADDING_RIGHT_DP * density +
            PILL_MIN_TEXT_WIDTH_DP * density

    fun build(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        slotIndex: Int,
        settings: CornerGestureSettings,
        density: Float,
        revealProgress: Float,
        items: List<GestureAction.LaunchShortcut>,
        textWidthsPx: List<Float>,
        screenWidth: Float,
    ): CornerShortcutSubMenuLayout {
        if (items.isEmpty()) {
            return CornerShortcutSubMenuLayout(emptyList(), RectF())
        }
        val bubbleCenter = CornerRadialMenuGeometry.bubbleCenterForSlot(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            slotIndex = slotIndex,
            settings = settings,
            density = density,
        )
        val centerX = anchorX + (bubbleCenter.x - anchorX) * revealProgress
        val centerY = anchorY + (bubbleCenter.y - anchorY) * revealProgress
        val bubbleRadius = CornerRadialMenuGeometry.bubbleRadiusPx(settings, density, slotIndex)
        val pillHeight = PILL_HEIGHT_DP * density
        val gap = ITEM_GAP_DP * density
        val paddingRight = PILL_PADDING_RIGHT_DP * density
        val iconContentWidth = iconContentWidthPx(density)
        val offset = OFFSET_ABOVE_BUBBLE_DP * density
        val margin = 10f * density
        val minWidth = minPillWidthPx(density)
        val maxWidth = min(PILL_MAX_WIDTH_DP * density, screenWidth - margin * 2f)

        val anchorBottom = centerY - bubbleRadius - offset
        val stackHeight = items.size * pillHeight + gap * maxOf(items.size - 1, 0)
        var topY = anchorBottom - stackHeight
        val itemRects = ArrayList<RectF>(items.size)
        for (index in items.indices) {
            val textWidth = textWidthsPx.getOrElse(index) {
                PILL_MIN_TEXT_WIDTH_DP * density
            }
            val pillWidth = (iconContentWidth + textWidth + paddingRight).coerceIn(minWidth, maxWidth)
            var left = centerX - pillWidth / 2f
            left = left.coerceIn(margin, screenWidth - pillWidth - margin)
            val top = topY
            val bottom = top + pillHeight
            itemRects.add(RectF(left, top, left + pillWidth, bottom))
            topY = bottom + gap
        }
        val bounds = RectF(itemRects.first())
        itemRects.drop(1).forEach { rect -> bounds.union(rect) }
        return CornerShortcutSubMenuLayout(itemRects, bounds)
    }
}
