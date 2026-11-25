package com.slideindex.app.overlay.layout

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class FvRingLauncherSide { LEFT, RIGHT, BOTTOM, TOP }

data class FvSlotLayout(
    val index: Int,
    val centerX: Float,
    val centerY: Float,
    val angleMaxDeg: Int,
    val angleMinDeg: Int,
    val minDistSq: Int,
    val maxDistSq: Int,
)

data class FvPanelLayout(
    val anchorX: Float,
    val anchorY: Float,
    val side: FvRingLauncherSide,
    val itemSizePx: Float,
    val toolbarButtonRadiusPx: Float,
    val slots: List<FvSlotLayout>,
    val toolbarCenterX: Float,
    val toolbarButtonCenters: List<Pair<Float, Float>>,
    val outerRadiusPx: Float = 0f,
    val cornerRadiusRatio: Float = 0.22f,
    val iconShape: FvIconShape = FvIconShape.ROUNDED_RECT,
)

/** 按 FV CircleAppContainer 几何移植的半圆槽位布局（支持动态外观与半径自定义）。 */
object FvCircleLayoutEngine {
    const val ICON_SIZE_DP = 36f
    const val DEFAULT_BASE_RADIUS_DP = 88f
    const val DEFAULT_LAYER_GAP_DP = 50f
    const val DEFAULT_END_MARGIN_DEG = 6f
    val LAYER_RADIUS_DP = floatArrayOf(88f, 138f, 188f, 238f)
    val DEFAULT_LAYER_RADII_DP = LAYER_RADIUS_DP
    private const val TOOLBAR_BUTTON_RADIUS_DP = 18f
    private val LAYER_SLOT_COUNTS = intArrayOf(5, 8, 11, 14)
    private const val END_MARGIN_RAD = PI / 180.0 * 6.0
    private const val TOOLBAR_GAP_DP = 10f
    private const val TOOLBAR_EDGE_GAP_DP = 15f
    // 工具栏按钮数量：必须与 FvToolbarButton.entries.size 保持一致（按钮中心按此数量等距排布）。
    // 已移除多余的「−」（HIDE）：它只是收起面板，与点空白处重复，而 MOVE 现在承担"自由移动悬浮球"。
    private const val TOOLBAR_BUTTON_COUNT = 4
    private const val TOOLBAR_HIT_SCALE = 1.35f

    data class SlotGeo(
        val offsetX: Float,
        val offsetY: Float,
        val angleMaxDeg: Int,
        val angleMinDeg: Int,
        val layerIndex: Int,
    )

    private val allSlotGeo: List<SlotGeo> = buildAllSlotGeometry()
    private val allBottomSlotGeo: List<SlotGeo> = buildBottomAllSlotGeometry()

    fun layout(
        circleCount: Int,
        side: FvRingLauncherSide,
        anchorX: Float,
        anchorY: Float,
        screenWidth: Float,
        density: Float,
        iconSizeDp: Float = ICON_SIZE_DP,
        iconShape: FvIconShape = FvIconShape.ROUNDED_RECT,
        baseRadiusDp: Float = DEFAULT_BASE_RADIUS_DP,
        layerGapDp: Float = DEFAULT_LAYER_GAP_DP,
        endMarginDeg: Float = DEFAULT_END_MARGIN_DEG,
    ): FvPanelLayout {
        val safeIconSizeDp = iconSizeDp.coerceIn(24f, 56f)
        val safeBaseRadiusDp = baseRadiusDp.coerceIn(60f, 130f)
        val safeLayerGapDp = layerGapDp.coerceIn(35f, 75f)
        val safeEndMarginDeg = endMarginDeg.coerceIn(0f, 30f)
        val layerRadiiDp = floatArrayOf(
            safeBaseRadiusDp,
            safeBaseRadiusDp + safeLayerGapDp,
            safeBaseRadiusDp + safeLayerGapDp * 2f,
            safeBaseRadiusDp + safeLayerGapDp * 3f,
        )

        val itemSizePx = safeIconSizeDp * density
        val toolbarButtonRadiusPx = TOOLBAR_BUTTON_RADIUS_DP * density
        val slotCount = slotCountForCircleCount(circleCount)
        val slotGeometries = when (side) {
            FvRingLauncherSide.BOTTOM, FvRingLauncherSide.TOP -> {
                if (
                    safeIconSizeDp == ICON_SIZE_DP &&
                    safeBaseRadiusDp == DEFAULT_BASE_RADIUS_DP &&
                    safeLayerGapDp == DEFAULT_LAYER_GAP_DP &&
                    safeEndMarginDeg == DEFAULT_END_MARGIN_DEG
                ) {
                    allBottomSlotGeo
                } else {
                    buildBottomAllSlotGeometry(safeIconSizeDp, layerRadiiDp, safeEndMarginDeg)
                }
            }
            FvRingLauncherSide.LEFT, FvRingLauncherSide.RIGHT -> {
                if (
                    safeIconSizeDp == ICON_SIZE_DP &&
                    safeBaseRadiusDp == DEFAULT_BASE_RADIUS_DP &&
                    safeLayerGapDp == DEFAULT_LAYER_GAP_DP &&
                    safeEndMarginDeg == DEFAULT_END_MARGIN_DEG
                ) {
                    allSlotGeo
                } else {
                    buildAllSlotGeometry(safeIconSizeDp, layerRadiiDp, safeEndMarginDeg)
                }
            }
        }

        val slots = slotGeometries.take(slotCount).mapIndexed { index, geo ->
            val (cx, cy) = toScreenOffset(geo.offsetX, geo.offsetY, side, anchorX, anchorY, density)
            val (minDistSq, maxDistSq) = radialBandSq(geo.layerIndex, density, safeIconSizeDp, layerRadiiDp)
            FvSlotLayout(
                index = index,
                centerX = cx,
                centerY = cy,
                angleMaxDeg = geo.angleMaxDeg,
                angleMinDeg = geo.angleMinDeg,
                minDistSq = minDistSq,
                maxDistSq = maxDistSq,
            )
        }
        val outerRadius = outerRadiusPx(slotCount, itemSizePx, density, layerRadiiDp)
        val toolbar = buildToolbar(
            side = side,
            anchorX = anchorX,
            anchorY = anchorY,
            screenWidth = screenWidth,
            density = density,
            toolbarRadiusPx = toolbarButtonRadiusPx,
            outerRadiusPx = outerRadius,
        )
        return FvPanelLayout(
            anchorX = anchorX,
            anchorY = anchorY,
            side = side,
            itemSizePx = itemSizePx,
            toolbarButtonRadiusPx = toolbarButtonRadiusPx,
            slots = slots,
            toolbarCenterX = toolbar.first,
            toolbarButtonCenters = toolbar.second,
            outerRadiusPx = outerRadius,
            cornerRadiusRatio = iconShape.cornerRadiusRatio,
            iconShape = iconShape,
        )
    }

    fun slotIndexAt(layout: FvPanelLayout, rawX: Float, rawY: Float): Int {
        val density = if (layout.itemSizePx > 0f) layout.itemSizePx / ICON_SIZE_DP else 1f
        val hitRadius = maxOf(layout.itemSizePx * 0.55f, 18f * density)
        val hitRadiusSq = hitRadius * hitRadius
        val relX: Float
        val relY: Float
        when (layout.side) {
            FvRingLauncherSide.LEFT -> {
                relX = rawX - layout.anchorX
                relY = rawY - layout.anchorY
            }
            FvRingLauncherSide.RIGHT -> {
                relX = layout.anchorX - rawX
                relY = rawY - layout.anchorY
            }
            FvRingLauncherSide.BOTTOM -> {
                relX = rawX - layout.anchorX
                relY = layout.anchorY - rawY
            }
            FvRingLauncherSide.TOP -> {
                relX = rawX - layout.anchorX
                relY = rawY - layout.anchorY
            }
        }
        val distSq = (relX * relX + relY * relY).toInt()
        val angleDeg = toAngleDeg(atan2(-relY.toDouble(), relX.toDouble()))

        var bestIndex = -1
        var bestDistSq = Float.MAX_VALUE
        for (slot in layout.slots) {
            if (!isWithinRadialBand(distSq, slot)) continue
            val dx = slot.centerX - rawX
            val dy = slot.centerY - rawY
            val slotDistSq = dx * dx + dy * dy
            if (slotDistSq <= hitRadiusSq && slotDistSq < bestDistSq) {
                bestDistSq = slotDistSq
                bestIndex = slot.index
            }
        }
        if (bestIndex >= 0) return bestIndex

        var bestBandIndex = -1
        var bestBandDistSq = Float.MAX_VALUE
        for (slot in layout.slots) {
            if (!isWithinAngularBand(angleDeg, slot) || !isWithinRadialBand(distSq, slot)) continue
            val dx = slot.centerX - rawX
            val dy = slot.centerY - rawY
            val slotDistSq = dx * dx + dy * dy
            if (slotDistSq <= hitRadiusSq && slotDistSq < bestBandDistSq) {
                bestBandDistSq = slotDistSq
                bestBandIndex = slot.index
            }
        }
        return bestBandIndex
    }

    fun toolbarButtonAt(layout: FvPanelLayout, rawX: Float, rawY: Float): FvToolbarButton? {
        val hitRadius = toolbarHitRadius(layout)
        var bestButton: FvToolbarButton? = null
        var bestDistSq = Float.MAX_VALUE
        for (button in FvToolbarButton.entries) {
            val (cx, cy) = layout.toolbarButtonCenters.getOrNull(button.ordinal) ?: continue
            val dx = rawX - cx
            val dy = rawY - cy
            val distSq = dx * dx + dy * dy
            if (distSq <= hitRadius * hitRadius && distSq < bestDistSq) {
                bestDistSq = distSq
                bestButton = button
            }
        }
        return bestButton
    }

    fun isNearToolbar(layout: FvPanelLayout, rawX: Float, rawY: Float): Boolean {
        if (toolbarButtonAt(layout, rawX, rawY) != null) return true
        val hitRadius = toolbarHitRadius(layout)
        val centers = layout.toolbarButtonCenters
        if (centers.isEmpty()) return false
        val minY = centers.minOf { it.second } - hitRadius
        val maxY = centers.maxOf { it.second } + hitRadius
        val toolbarX = layout.toolbarCenterX
        val horizontalSlop = hitRadius * 1.2f
        return rawY in minY..maxY && kotlin.math.abs(rawX - toolbarX) <= horizontalSlop
    }

    private fun toolbarHitRadius(layout: FvPanelLayout): Float =
        layout.toolbarButtonRadiusPx * TOOLBAR_HIT_SCALE

    fun isOutsidePanel(layout: FvPanelLayout, rawX: Float, rawY: Float, extraMarginPx: Float): Boolean {
        if (toolbarButtonAt(layout, rawX, rawY) != null) return false
        val outerRadius = (if (layout.outerRadiusPx > 0f) layout.outerRadiusPx else outerRadiusPx(layout.slots.size, layout.itemSizePx)) + extraMarginPx
        when (layout.side) {
            FvRingLauncherSide.BOTTOM -> {
                if (rawY > layout.anchorY + extraMarginPx) return true
                val relX = rawX - layout.anchorX
                val relY = layout.anchorY - rawY
                if (relY < -extraMarginPx) return true
                return relX * relX + relY * relY > outerRadius * outerRadius
            }
            FvRingLauncherSide.TOP -> {
                if (rawY < layout.anchorY - extraMarginPx) return true
                val relX = rawX - layout.anchorX
                val relY = rawY - layout.anchorY
                if (relY < -extraMarginPx) return true
                return relX * relX + relY * relY > outerRadius * outerRadius
            }
            else -> {
                val dx = rawX - layout.anchorX
                val dy = rawY - layout.anchorY
                return dx * dx + dy * dy > outerRadius * outerRadius
            }
        }
    }

    fun slotCountForCircleCount(circleCount: Int): Int = when (circleCount.coerceIn(1, 4)) {
        1 -> 5
        2 -> 13
        3 -> 24
        else -> 38
    }

    fun buildAllSlotGeometry(
        iconSizeDp: Float = ICON_SIZE_DP,
        layerRadiiDp: FloatArray = DEFAULT_LAYER_RADII_DP,
        endMarginDeg: Float = DEFAULT_END_MARGIN_DEG,
    ): List<SlotGeo> {
        val endMarginRad = PI / 180.0 * endMarginDeg
        val halfPi = PI / 2.0
        val result = ArrayList<SlotGeo>(38)

        fun addLayer(layerIndex: Int, radiusDp: Float, slotCount: Int, slotWidthRad: Double, gapRad: Double) {
            var angleMax = halfPi - endMarginRad
            var boundary = (angleMax - slotWidthRad) - (gapRad / 2.0)
            var centerAngle = angleMax - (slotWidthRad / 2.0)
            repeat(slotCount) { slotInLayer ->
                val offsetX = (radiusDp * cos(centerAngle)).toFloat()
                val offsetY = (radiusDp * sin(centerAngle)).toFloat()
                val angleMaxDeg = toAngleDeg(angleMax)
                val angleMinDeg = if (slotInLayer == slotCount - 1) {
                    toAngleDeg(boundary + (gapRad / 2.0))
                } else {
                    toAngleDeg(boundary)
                }
                result += SlotGeo(offsetX, offsetY, angleMaxDeg, angleMinDeg, layerIndex)
                val nextBoundary = (boundary - slotWidthRad) - gapRad
                centerAngle = (boundary - (slotWidthRad / 2.0)) - (gapRad / 2.0)
                angleMax = boundary
                boundary = nextBoundary
            }
        }

        layerRadiiDp.forEachIndexed { layerIndex, radiusDp ->
            val slotCount = LAYER_SLOT_COUNTS[layerIndex]
            val slotWidthRad = asin((iconSizeDp * 0.5f) / radiusDp) * 2.0
            val gapRad = ((PI - (endMarginRad * 2.0)) - (slotWidthRad * slotCount)) / (slotCount - 1)
            addLayer(layerIndex, radiusDp, slotCount, slotWidthRad, gapRad)
        }
        return result
    }

    /** 底边触钮：180° 半圆，弦与屏幕底边平行，锚点在底边中点。 */
    fun buildBottomAllSlotGeometry(
        iconSizeDp: Float = ICON_SIZE_DP,
        layerRadiiDp: FloatArray = DEFAULT_LAYER_RADII_DP,
        endMarginDeg: Float = DEFAULT_END_MARGIN_DEG,
    ): List<SlotGeo> {
        val endMarginRad = PI / 180.0 * endMarginDeg
        val result = ArrayList<SlotGeo>(38)

        fun addLayer(layerIndex: Int, radiusDp: Float, slotCount: Int, slotWidthRad: Double, gapRad: Double) {
            var thetaMax = PI - endMarginRad
            var boundary = (thetaMax - slotWidthRad) - (gapRad / 2.0)
            var centerTheta = thetaMax - (slotWidthRad / 2.0)
            repeat(slotCount) { slotInLayer ->
                val offsetX = (radiusDp * cos(centerTheta)).toFloat()
                val offsetY = (radiusDp * sin(centerTheta)).toFloat()
                val angleMaxDeg = bottomThetaToHitDeg(thetaMax)
                val angleMinDeg = if (slotInLayer == slotCount - 1) {
                    bottomThetaToHitDeg(boundary + (gapRad / 2.0))
                } else {
                    bottomThetaToHitDeg(boundary)
                }
                result += SlotGeo(offsetX, offsetY, angleMaxDeg, angleMinDeg, layerIndex)
                val nextBoundary = (boundary - slotWidthRad) - gapRad
                centerTheta = (boundary - (slotWidthRad / 2.0)) - (gapRad / 2.0)
                thetaMax = boundary
                boundary = nextBoundary
            }
        }

        layerRadiiDp.forEachIndexed { layerIndex, radiusDp ->
            val slotCount = LAYER_SLOT_COUNTS[layerIndex]
            val slotWidthRad = asin((iconSizeDp * 0.5f) / radiusDp) * 2.0
            val gapRad = ((PI - (endMarginRad * 2.0)) - (slotWidthRad * slotCount)) / (slotCount - 1)
            addLayer(layerIndex, radiusDp, slotCount, slotWidthRad, gapRad)
        }
        return result
    }

    /** 顶边触钮：180° 半圆，弦与屏幕顶边平行，锚点在顶边中点（与底边几何镜像）。 */
    fun buildTopAllSlotGeometry(
        iconSizeDp: Float = ICON_SIZE_DP,
        layerRadiiDp: FloatArray = DEFAULT_LAYER_RADII_DP,
        endMarginDeg: Float = DEFAULT_END_MARGIN_DEG,
    ): List<SlotGeo> = buildBottomAllSlotGeometry(iconSizeDp, layerRadiiDp, endMarginDeg)

    private fun bottomThetaToHitDeg(thetaRad: Double): Int =
        toAngleDeg(atan2(-sin(thetaRad), cos(thetaRad)))

    fun radialBandSq(
        layerIndex: Int,
        density: Float,
        iconSizeDp: Float = ICON_SIZE_DP,
        layerRadiiDp: FloatArray = DEFAULT_LAYER_RADII_DP,
    ): Pair<Int, Int> {
        val iconHalfPx = (iconSizeDp * 0.5f * density).toInt()
        val r0 = (layerRadiiDp[0] * density).toInt()
        val r1 = (layerRadiiDp[1] * density).toInt()
        val r2 = (layerRadiiDp[2] * density).toInt()
        val r3 = (layerRadiiDp[3] * density).toInt()
        val mid01 = (r0 + r1) / 2
        val mid12 = (r1 + r2) / 2
        val mid23 = (r2 + r3) / 2
        val gapHalf01 = (mid01 - (r0 + iconHalfPx)).coerceAtLeast(0)
        val gapHalf12 = (mid12 - (r1 + iconHalfPx)).coerceAtLeast(0)
        val gapHalf23 = (mid23 - (r2 + iconHalfPx)).coerceAtLeast(0)
        return when (layerIndex) {
            0 -> {
                val innerThreshold = (r0 - iconHalfPx).coerceAtLeast(0)
                val min = innerThreshold * innerThreshold
                val outer = mid01 - gapHalf01
                val max = outer * outer
                min to max
            }
            1 -> {
                val inner = mid01 + gapHalf01
                val min = inner * inner
                val outer = mid12 - gapHalf12
                val max = outer * outer
                min to max
            }
            2 -> {
                val inner = mid12 + gapHalf12
                val min = inner * inner
                val outer = mid23 - gapHalf23
                val max = outer * outer
                min to max
            }
            else -> {
                val inner = mid23 + gapHalf23
                val min = inner * inner
                val outer = r3 + iconHalfPx
                val max = outer * outer
                min to max
            }
        }
    }

    private fun isWithinRadialBand(distSq: Int, slot: FvSlotLayout): Boolean =
        distSq >= slot.minDistSq && distSq <= slot.maxDistSq

    private fun isWithinAngularBand(angleDeg: Int, slot: FvSlotLayout): Boolean =
        angleDeg <= slot.angleMaxDeg && angleDeg >= slot.angleMinDeg

    private fun buildToolbar(
        side: FvRingLauncherSide,
        anchorX: Float,
        anchorY: Float,
        screenWidth: Float,
        density: Float,
        toolbarRadiusPx: Float,
        outerRadiusPx: Float,
    ): Pair<Float, List<Pair<Float, Float>>> {
        val gapPx = TOOLBAR_GAP_DP * density
        val edgeGapPx = TOOLBAR_EDGE_GAP_DP * density
        val buttonDiameterPx = toolbarRadiusPx * 2f
        // FV CircleAppContainer：左贴边→工具列 gravity=TOP|RIGHT；右贴边→TOP|LEFT（对侧贴边）
        val toolbarX = when (side) {
            FvRingLauncherSide.LEFT -> screenWidth - edgeGapPx - toolbarRadiusPx
            FvRingLauncherSide.RIGHT -> edgeGapPx + toolbarRadiusPx
            FvRingLauncherSide.BOTTOM -> anchorX
            FvRingLauncherSide.TOP -> anchorX
        }
        val toolbarRowCenterY = when (side) {
            FvRingLauncherSide.BOTTOM -> anchorY - outerRadiusPx - edgeGapPx - toolbarRadiusPx
            FvRingLauncherSide.TOP -> anchorY + outerRadiusPx + edgeGapPx + toolbarRadiusPx
            else -> 0f
        }
        val centers = when (side) {
            FvRingLauncherSide.BOTTOM, FvRingLauncherSide.TOP -> {
                val totalWidth = (TOOLBAR_BUTTON_COUNT * buttonDiameterPx) + ((TOOLBAR_BUTTON_COUNT - 1) * gapPx)
                val leftX = anchorX - totalWidth / 2f + toolbarRadiusPx
                List(TOOLBAR_BUTTON_COUNT) { index ->
                    val x = leftX + index * (buttonDiameterPx + gapPx)
                    x to toolbarRowCenterY
                }
            }
            else -> {
                val totalHeight = (TOOLBAR_BUTTON_COUNT * buttonDiameterPx) + ((TOOLBAR_BUTTON_COUNT - 1) * gapPx)
                val topY = anchorY - totalHeight / 2f + toolbarRadiusPx
                List(TOOLBAR_BUTTON_COUNT) { index ->
                    val y = topY + index * (buttonDiameterPx + gapPx)
                    toolbarX to y
                }
            }
        }
        val toolbarCenter = when (side) {
            FvRingLauncherSide.BOTTOM, FvRingLauncherSide.TOP -> {
                val avgX = centers.map { it.first }.average().toFloat()
                avgX to toolbarRowCenterY
            }
            else -> toolbarX to anchorY
        }
        return toolbarCenter.first to centers
    }

    private fun toScreenOffset(
        offsetX: Float,
        offsetY: Float,
        side: FvRingLauncherSide,
        anchorX: Float,
        anchorY: Float,
        density: Float,
    ): Pair<Float, Float> {
        val scaledX = offsetX * density
        val scaledY = offsetY * density
        return when (side) {
            FvRingLauncherSide.LEFT -> anchorX + scaledX to anchorY + scaledY
            FvRingLauncherSide.RIGHT -> anchorX - scaledX to anchorY + scaledY
            FvRingLauncherSide.BOTTOM -> anchorX + scaledX to anchorY - scaledY
            FvRingLauncherSide.TOP -> anchorX + scaledX to anchorY + scaledY
        }
    }

    private fun relativeX(layout: FvPanelLayout, rawX: Float): Float =
        when (layout.side) {
            FvRingLauncherSide.LEFT -> rawX - layout.anchorX
            FvRingLauncherSide.RIGHT -> layout.anchorX - rawX
            FvRingLauncherSide.BOTTOM, FvRingLauncherSide.TOP -> rawX - layout.anchorX
        }

    private fun toAngleDeg(radians: Double): Int = (radians * 180.0 / PI).toInt()

    private fun outerRadiusPx(
        slotCount: Int,
        itemSizePx: Float,
        density: Float = if (itemSizePx > 0f) itemSizePx / ICON_SIZE_DP else 1f,
        layerRadiiDp: FloatArray = DEFAULT_LAYER_RADII_DP,
    ): Float {
        val layerIndex = when {
            slotCount <= 5 -> 0
            slotCount <= 13 -> 1
            slotCount <= 24 -> 2
            else -> 3
        }
        val radiusDp = layerRadiiDp.getOrElse(layerIndex) { DEFAULT_LAYER_RADII_DP[layerIndex] }
        return radiusDp * density + itemSizePx
    }
}

enum class FvToolbarButton {
    MOVE,
    PIN,
    SETTINGS,
    KEYBOARD,
}
