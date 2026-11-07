package com.slideindex.app.overlay.fingertip

import com.slideindex.app.settings.FingertipRingCodec
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

internal object FingertipRingGeometry {
    fun iconCenterForSlot(
        centerX: Float,
        centerY: Float,
        slotIndex: Int,
        slotCount: Int,
        orbitRadiusPx: Float,
    ): Pair<Float, Float> {
        val sweep = 360f / slotCount
        val angleRad = Math.toRadians((slotIndex * sweep).toDouble())
        return centerX + (sin(angleRad) * orbitRadiusPx).toFloat() to
            centerY - (kotlin.math.cos(angleRad) * orbitRadiusPx).toFloat()
    }

    /** 按手指相对圆心的方向映射槽位；圆心处默认第 1 个（仅用于方向滑动手势）。 */
    fun slotIndexByAngle(
        centerX: Float,
        centerY: Float,
        fingerX: Float,
        fingerY: Float,
        slotCount: Int,
    ): Int {
        if (slotCount <= 0) return -1
        val dx = fingerX - centerX
        val dy = fingerY - centerY
        if (dx == 0f && dy == 0f) return 0
        val sweep = 360f / slotCount
        var angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        return ((angle + sweep / 2f) % 360f / sweep).toInt().coerceIn(0, slotCount - 1)
    }

    /**
     * 持续选槽：手指须落在图标圆形底范围内；圆心内侧与图标间隙均不选中。
     * 坐标系与绘制一致（通常为 overlay view 本地坐标）。
     */
    fun slotIndexAtFinger(
        centerX: Float,
        centerY: Float,
        fingerX: Float,
        fingerY: Float,
        slotCount: Int,
        orbitRadiusPx: Float,
        iconSizePx: Float,
    ): Int {
        if (slotCount <= 0) return -1
        val orbit = FingertipRingCodec.effectiveOrbitRadiusPx(orbitRadiusPx)
        val hitRadius = FingertipRingCodec.iconBackgroundRadiusPx(
            FingertipRingCodec.effectiveIconSizePx(iconSizePx),
        )
        var bestSlot = -1
        var bestDistance = Float.MAX_VALUE
        for (slot in 0 until slotCount) {
            val (iconX, iconY) = iconCenterForSlot(centerX, centerY, slot, slotCount, orbit)
            val distance = hypot(fingerX - iconX, fingerY - iconY)
            if (distance <= hitRadius && distance < bestDistance) {
                bestDistance = distance
                bestSlot = slot
            }
        }
        return bestSlot
    }

    fun nearestSlotIndexForSwipe(
        gestureStartX: Float,
        gestureStartY: Float,
        fingerX: Float,
        fingerY: Float,
        slotCount: Int,
    ): Int = slotIndexByAngle(gestureStartX, gestureStartY, fingerX, fingerY, slotCount)
}
