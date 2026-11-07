package com.slideindex.app.overlay.corner

import androidx.compose.ui.geometry.Offset
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object CornerWheelLayout {
    private const val ARC_DEGREES = 90f
    private const val EDGE_PADDING_DEG = 10f
    fun bubbleRadiusPx(settings: CornerGestureSettings, density: Float): Float =
        settings.bubbleSizeDp * density

    /**
     * 环心半径（dp）：第一环紧贴内径空洞外缘（内径/2 + 气泡半径 + 8dp），
     * 之后每环按 [CornerGestureSettings.ringSpacingDp] 往外推。
     *
     * 关键性质：**层数不参与计算**。加层只是往外接圈，已有环一格不动、环间距也不变
     * （环间距下限由设置钳到 ≥ 气泡直径，所以径向永远不叠）。
     */
    fun layerRadiusDp(settings: CornerGestureSettings, layer: Int): Float {
        val firstRing = settings.innerDiameterDp / 2f +
            settings.bubbleSizeDp +
            CornerGestureSettings.FIRST_RING_MARGIN_DP
        val index = layer.coerceIn(0, CornerRadialMenuCodec.LAYER_SLOT_COUNTS.lastIndex)
        return firstRing + index * settings.ringSpacingDp
    }

    /** 轮盘外沿到角的距离（dp）：最外层气泡中心 + 一个气泡半径。设置页用它显示"轮盘伸出多远"。 */
    fun wheelOuterEdgeDp(settings: CornerGestureSettings): Float =
        layerRadiusDp(settings, settings.enabledLayerCount - 1) + settings.bubbleSizeDp

    fun layerRadiusPx(settings: CornerGestureSettings, density: Float, layer: Int): Float =
        layerRadiusDp(settings, layer) * density

    /** 当前已启用的最外层序号（第 4/5 层关着时就是 2）。 */
    private fun outermostLayer(settings: CornerGestureSettings): Int = settings.enabledLayerCount - 1

    fun editButtonCenter(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        density: Float,
    ): Offset {
        val topOuterSlotIndex = CornerRadialMenuCodec.layerStartIndex(outermostLayer(settings))
        val topSlot = bubbleCenterForSlot(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            globalIndex = topOuterSlotIndex,
            settings = settings,
            density = density,
        )
        val visualOuter =
            layerRadiusPx(settings, density, outermostLayer(settings)) + bubbleRadiusPx(settings, density)
        val editR = editButtonRadius(density)
        val centerX = when (anchor) {
            CornerAnchor.LEFT -> anchorX + visualOuter + editR * 0.42f
            CornerAnchor.RIGHT -> anchorX - visualOuter - editR * 0.42f
        }
        return Offset(centerX, topSlot.y)
    }

    /** 轮盘实际外缘（最外层气泡中心 + 余量），用于「轮盘外取消」判定。 */
    fun wheelOuterHitRadiusPx(settings: CornerGestureSettings, density: Float): Float =
        layerRadiusPx(settings, density, outermostLayer(settings)) + bubbleRadiusPx(settings, density) * 1.5f

    fun editButtonRadius(density: Float): Float = 24f * density

    fun isEditButtonHit(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        fingerX: Float,
        fingerY: Float,
        density: Float,
    ): Boolean {
        if (!settings.showEditButton) return false
        val center = editButtonCenter(anchor, anchorX, anchorY, settings, density)
        val radius = editButtonRadius(density)
        return hypot(fingerX - center.x, fingerY - center.y) <= radius * 1.55f
    }

    /**
     * 渐进展开时已显示的层数，上限是当前启用的层数。
     * 逐环比较手指到锚点的距离，越靠外的层需要滑得越远才出现。
     */
    fun activeLayerCount(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        settings: CornerGestureSettings,
        density: Float,
        progressive: Boolean,
        activationRadDist: Float? = null,
    ): Int {
        val layerCount = settings.enabledLayerCount
        if (!progressive) return layerCount
        val dist = hypot(fingerX - anchorX, fingerY - anchorY)
        val bubble = bubbleRadiusPx(settings, density)
        val slop = bubble * 0.35f
        val radii = List(layerCount) { layerRadiusPx(settings, density, it) }
        var revealed = 1
        if (activationRadDist != null) {
            val expand = (dist - activationRadDist).coerceAtLeast(0f)
            var cumulative = 0f
            for (index in 1 until layerCount) {
                cumulative += (radii[index] - radii[index - 1]).coerceAtLeast(bubble)
                if (expand > cumulative + slop) revealed = index + 1
            }
            return revealed
        }
        for (index in 1 until layerCount) {
            if (dist > radii[index - 1] + slop) revealed = index + 1
        }
        return revealed
    }

    fun bubbleCenterForSlot(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        globalIndex: Int,
        settings: CornerGestureSettings,
        density: Float,
    ): Offset {
        val layer = CornerRadialMenuCodec.layerOf(globalIndex)
        val localIndex = CornerRadialMenuCodec.layerLocalIndex(globalIndex)
        val slotCount = CornerRadialMenuCodec.slotCountInLayer(layer)
        val radius = layerRadiusPx(settings, density, layer)
        val usableArc = ARC_DEGREES - EDGE_PADDING_DEG * 2f
        val sweep = usableArc / slotCount
        val start = arcStartDegrees(anchor) + EDGE_PADDING_DEG
        val visualIndex = when (anchor) {
            CornerAnchor.LEFT -> localIndex
            CornerAnchor.RIGHT -> slotCount - 1 - localIndex
        }
        val angle = start + (visualIndex + 0.5f) * sweep
        return polarOffset(anchorX, anchorY, radius, angle)
    }

    fun isInInnerZone(
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        settings: CornerGestureSettings,
        density: Float,
    ): Boolean {
        val inner = settings.innerDiameterDp * density / 2f
        return hypot(fingerX - anchorX, fingerY - anchorY) < inner
    }

    fun isOutsideWheel(
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        fingerX: Float,
        fingerY: Float,
        screenWidth: Float,
        screenHeight: Float,
        settings: CornerGestureSettings,
        density: Float,
    ): Boolean {
        if (isEditButtonHit(anchor, anchorX, anchorY, settings, fingerX, fingerY, density)) {
            return false
        }
        val dist = hypot(fingerX - anchorX, fingerY - anchorY)
        if (dist > wheelOuterHitRadiusPx(settings, density)) return true
        var angle = Math.toDegrees(
            kotlin.math.atan2(
                (fingerY - anchorY).toDouble(),
                (fingerX - anchorX).toDouble(),
            ),
        ).toFloat()
        if (angle < 0f) angle += 360f
        return !isAngleInArc(anchor, angle)
    }

    private fun arcStartDegrees(anchor: CornerAnchor): Float = when (anchor) {
        CornerAnchor.LEFT -> 270f
        CornerAnchor.RIGHT -> 180f
    }

    private fun isAngleInArc(anchor: CornerAnchor, angle: Float): Boolean = when (anchor) {
        CornerAnchor.LEFT -> angle >= 270f - EDGE_PADDING_DEG || angle <= EDGE_PADDING_DEG
        CornerAnchor.RIGHT -> angle in (180f - EDGE_PADDING_DEG)..(270f + EDGE_PADDING_DEG)
    }

    private fun polarOffset(cx: Float, cy: Float, radius: Float, angleDegrees: Float): Offset {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return Offset(
            x = cx + (cos(radians) * radius).toFloat(),
            y = cy + (sin(radians) * radius).toFloat(),
        )
    }
}
