package com.slideindex.app.overlay.corner

import androidx.compose.ui.geometry.Offset
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import kotlin.math.hypot
import kotlin.math.max

object CornerRadialMenuGeometry {
    fun bubbleRadiusPx(settings: CornerGestureSettings, density: Float, @Suppress("UNUSED_PARAMETER") slotIndex: Int): Float =
        CornerWheelLayout.bubbleRadiusPx(settings, density)

    fun bubbleCenterForSlot(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        slotIndex: Int,
        settings: CornerGestureSettings,
        density: Float,
    ): Offset = CornerWheelLayout.bubbleCenterForSlot(
        anchor = anchor,
        anchorX = anchorX,
        anchorY = anchorY,
        globalIndex = slotIndex,
        settings = settings,
        density = density,
    )

    /** [activeLayerCount] 层以内最后一个可见槽位；层数上限是 5（第 4/5 层需用户开启）。 */
    fun lastVisibleSlotIndex(activeLayerCount: Int): Int =
        CornerRadialMenuCodec.lastSlotIndexInLayerCount(activeLayerCount)

    fun slotIndexAt(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        settings: CornerGestureSettings,
        density: Float,
        slots: List<GestureAction>,
        editMode: Boolean,
        activeLayerCount: Int,
        revealProgress: Float = 1f,
    ): Int? {
        // 编辑模式下调用方传入的是"已启用的层数"，因此空槽位也只在启用层内可点。
        val lastSlot = lastVisibleSlotIndex(activeLayerCount)
        val progress = revealProgress.coerceIn(0f, 1f)
        val hitScale = if (editMode) 1.55f else 1.35f
        var bestSlot = -1
        var bestDistance = Float.MAX_VALUE
        // 由外向内遍历，避免内层气泡在重叠区抢走外层命中。
        for (index in lastSlot downTo 0) {
            val action = slots.getOrElse(index) { GestureAction.None }
            if (!editMode && action is GestureAction.None) continue
            val bubbleRadius = bubbleRadiusPx(settings, density, index)
            val hitSlop = bubbleRadius * hitScale
            val target = bubbleCenterForSlot(anchor, anchorX, anchorY, index, settings, density)
            val centerX = anchorX + (target.x - anchorX) * progress
            val centerY = anchorY + (target.y - anchorY) * progress
            val distance = hypot(fingerX - centerX, fingerY - centerY)
            if (distance <= hitSlop && distance < bestDistance) {
                bestDistance = distance
                bestSlot = index
            }
        }
        return bestSlot.takeIf { it >= 0 }
    }

    fun displayLayerCount(
        activeLayerCount: Int,
        highlightedSlot: Int,
        maxLayerCount: Int = CornerRadialMenuCodec.BASE_LAYER_COUNT,
    ): Int {
        val upperBound = maxLayerCount.coerceIn(1, CornerRadialMenuCodec.layerCount())
        if (highlightedSlot < 0) return activeLayerCount.coerceIn(1, upperBound)
        val highlightLayer = CornerRadialMenuCodec.layerOf(highlightedSlot) + 1
        return max(activeLayerCount, highlightLayer).coerceIn(1, upperBound)
    }

    fun isEditButtonHit(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        fingerX: Float,
        fingerY: Float,
        density: Float,
    ): Boolean = CornerWheelLayout.isEditButtonHit(
        anchor = anchor,
        anchorX = anchorX,
        anchorY = anchorY,
        settings = settings,
        fingerX = fingerX,
        fingerY = fingerY,
        density = density,
    )

    fun inwardSlopDistance(
        @Suppress("UNUSED_PARAMETER") anchor: CornerAnchor,
        startX: Float,
        startY: Float,
        currentX: Float,
        currentY: Float,
    ): Float = hypot(currentX - startX, currentY - startY)
}
