package com.slideindex.app.overlay.layout

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QuickWheelLayoutEngine] 的纯几何单测。
 *
 * 引擎刻意不依赖 Android / `:feature:settings`，因此这里无需 Robolectric：
 * 全部是「输入数值 → 断言几何」的确定性用例。
 */
class QuickWheelLayoutEngineTest {

    private val defaultStyle = QuickWheelStyleSpec()

    // ── 样式归一化 ──────────────────────────────────────────

    @Test
    fun normalize_clampsOutOfRangeValues() {
        val normalized = QuickWheelLayoutEngine.normalize(
            QuickWheelStyleSpec(
                initialRadiusDp = 9_999f,
                containerGapDp = -5f,
                ringGapDp = 9_999f,
                containerSizeDp = 1f,
                containerCornerDp = 9_999f,
                iconSizePercent = 999,
                labelSizeFactorPercent = 0,
                builtinIconSizePercent = -3,
                offsetXDp = 999f,
                offsetYDp = -999f,
                columnCount = 99,
            ),
        )
        assertEquals(QuickWheelLayoutEngine.MAX_INITIAL_RADIUS_DP, normalized.initialRadiusDp, 0.001f)
        assertEquals(QuickWheelLayoutEngine.MIN_CONTAINER_GAP_DP, normalized.containerGapDp, 0.001f)
        assertEquals(QuickWheelLayoutEngine.MAX_RING_GAP_DP, normalized.ringGapDp, 0.001f)
        assertEquals(QuickWheelLayoutEngine.MIN_CONTAINER_SIZE_DP, normalized.containerSizeDp, 0.001f)
        assertEquals(QuickWheelLayoutEngine.MAX_CONTAINER_CORNER_DP, normalized.containerCornerDp, 0.001f)
        assertEquals(QuickWheelLayoutEngine.MAX_PERCENT, normalized.iconSizePercent)
        assertEquals(QuickWheelLayoutEngine.MIN_PERCENT, normalized.labelSizeFactorPercent)
        assertEquals(QuickWheelLayoutEngine.MIN_PERCENT, normalized.builtinIconSizePercent)
        assertEquals(QuickWheelLayoutEngine.MAX_OFFSET_DP, normalized.offsetXDp, 0.001f)
        assertEquals(QuickWheelLayoutEngine.MIN_OFFSET_DP, normalized.offsetYDp, 0.001f)
        assertEquals(QuickWheelLayoutEngine.MAX_COLUMN_COUNT, normalized.columnCount)
    }

    @Test
    fun normalize_keepsInRangeValuesUntouched() {
        val style = QuickWheelStyleSpec(
            showLabels = true,
            initialRadiusDp = 120f,
            containerGapDp = 24f,
            ringGapDp = 40f,
            containerSizeDp = 56f,
            containerCornerDp = 18f,
            iconSizePercent = 60,
            labelSizeFactorPercent = 40,
            builtinIconSizePercent = 55,
            offsetXDp = 10f,
            offsetYDp = -12f,
            columnCount = 6,
        )
        assertEquals(style, QuickWheelLayoutEngine.normalize(style))
    }

    @Test
    fun clampLongPressMs_boundsToRange() {
        assertEquals(
            QuickWheelLayoutEngine.MIN_LONG_PRESS_MS,
            QuickWheelLayoutEngine.clampLongPressMs(0),
        )
        assertEquals(
            QuickWheelLayoutEngine.MAX_LONG_PRESS_MS,
            QuickWheelLayoutEngine.clampLongPressMs(99_999),
        )
        assertEquals(800, QuickWheelLayoutEngine.clampLongPressMs(800))
        assertEquals(
            QuickWheelLayoutEngine.SECOND_LEVEL_LONG_PRESS_MS.toInt(),
            QuickWheelLayoutEngine.DEFAULT_LONG_PRESS_MS,
        )
    }

    @Test
    fun clampBackdropParams_boundToRange() {
        // 呼出时背景（容器以外区域）模糊 / 遮罩：默认均为 0（不改变现状）。
        assertEquals(0, QuickWheelLayoutEngine.DEFAULT_BACKDROP_BLUR_DP)
        assertEquals(0, QuickWheelLayoutEngine.DEFAULT_BACKDROP_DIM_PERCENT)
        assertEquals(0, QuickWheelLayoutEngine.clampBackdropBlurDp(-5))
        assertEquals(
            QuickWheelLayoutEngine.MAX_BACKDROP_BLUR_DP,
            QuickWheelLayoutEngine.clampBackdropBlurDp(9_999),
        )
        assertEquals(0, QuickWheelLayoutEngine.clampBackdropDimPercent(-1))
        assertEquals(
            QuickWheelLayoutEngine.MAX_BACKDROP_DIM_PERCENT,
            QuickWheelLayoutEngine.clampBackdropDimPercent(9_999),
        )
    }

    // ── 扇区 ────────────────────────────────────────────────

    @Test
    fun normalizeSectorMask_emptyFallsBackToAll() {
        assertEquals(QuickWheelLayoutEngine.SECTOR_ALL, QuickWheelLayoutEngine.normalizeSectorMask(0))
        assertEquals(QuickWheelLayoutEngine.SECTOR_ALL, QuickWheelLayoutEngine.normalizeSectorMask(0b10000))
    }

    @Test
    fun normalizeSectorMask_masksToFourBits() {
        assertEquals(0b0001, QuickWheelLayoutEngine.normalizeSectorMask(0b1_0001))
    }

    @Test
    fun normalizeSectorMask_keepsSingleSectorAndAdjacentPair() {
        // 只有「90°」和「半圆」两种非整圆形态，相邻判定要含 3 ↔ 0 的环绕。
        assertEquals(0b0001, QuickWheelLayoutEngine.normalizeSectorMask(0b0001))
        assertEquals(0b1000, QuickWheelLayoutEngine.normalizeSectorMask(0b1000))
        assertEquals(0b0011, QuickWheelLayoutEngine.normalizeSectorMask(0b0011))
        assertEquals(0b0110, QuickWheelLayoutEngine.normalizeSectorMask(0b0110))
        assertEquals(0b1001, QuickWheelLayoutEngine.normalizeSectorMask(0b1001))
        assertEquals(0b1100, QuickWheelLayoutEngine.normalizeSectorMask(0b1100))
    }

    @Test
    fun normalizeSectorMask_diagonalPairBecomesFullCircle() {
        // 对角两扇区拼不出连续可用区，一律当整圆。
        assertEquals(QuickWheelLayoutEngine.SECTOR_ALL, QuickWheelLayoutEngine.normalizeSectorMask(0b0101))
        assertEquals(QuickWheelLayoutEngine.SECTOR_ALL, QuickWheelLayoutEngine.normalizeSectorMask(0b1010))
    }

    @Test
    fun normalizeSectorMask_threeSectorsBecomeFullCircle() {
        // 点满三个 = 想要整圆（只是还没点第四个）。
        for (mask in 0..QuickWheelLayoutEngine.SECTOR_ALL) {
            if (Integer.bitCount(mask) == 3) {
                assertEquals(
                    "掩码 $mask 应归并为整圆",
                    QuickWheelLayoutEngine.SECTOR_ALL,
                    QuickWheelLayoutEngine.normalizeSectorMask(mask),
                )
            }
        }
    }

    @Test
    fun sectorSpanDeg_scalesWithSelectedSectors() {
        assertEquals(360, QuickWheelLayoutEngine.sectorSpanDeg(QuickWheelLayoutEngine.SECTOR_ALL))
        assertEquals(90, QuickWheelLayoutEngine.sectorSpanDeg(0b0001))
        assertEquals(180, QuickWheelLayoutEngine.sectorSpanDeg(0b0011))
        assertEquals(180, QuickWheelLayoutEngine.sectorSpanDeg(0b1001))
        // 对角两扇区 / 三扇区按整圆算：文案不能写 180°/270°，那是画不出来的形态。
        assertEquals(360, QuickWheelLayoutEngine.sectorSpanDeg(0b0101))
        assertEquals(360, QuickWheelLayoutEngine.sectorSpanDeg(0b0111))
    }

    @Test
    fun selectedSectorCount_usesMeaningfulMask() {
        // 空掩码 = 自动，按整圆处理（与 normalizeSectorMask 一致）。
        assertEquals(4, QuickWheelLayoutEngine.selectedSectorCount(0))
        assertEquals(1, QuickWheelLayoutEngine.selectedSectorCount(0b0001))
        assertEquals(2, QuickWheelLayoutEngine.selectedSectorCount(0b0011))
        assertEquals(4, QuickWheelLayoutEngine.selectedSectorCount(0b0111))
        assertEquals(4, QuickWheelLayoutEngine.selectedSectorCount(QuickWheelLayoutEngine.SECTOR_ALL))
    }

    @Test
    fun toggleSector_cannotClearLastSelectedSector() {
        // 只剩一个扇区时再点它应无效，避免轮盘塌缩成 0°。
        assertEquals(0b0001, QuickWheelLayoutEngine.toggleSector(0b0001, 0))
    }

    @Test
    fun toggleSector_addsAndRemovesBits() {
        assertEquals(0b0010, QuickWheelLayoutEngine.toggleSector(0b0011, 0))
        assertEquals(0b0111, QuickWheelLayoutEngine.toggleSector(0b0011, 2))
    }

    @Test
    fun mapVirtualAngleDeg_fullCircleIsIdentity() {
        assertEquals(-90f, QuickWheelLayoutEngine.mapVirtualAngleDeg(QuickWheelLayoutEngine.SECTOR_ALL, 0f), 0.01f)
        assertEquals(-45f, QuickWheelLayoutEngine.mapVirtualAngleDeg(QuickWheelLayoutEngine.SECTOR_ALL, 45f), 0.01f)
    }

    @Test
    fun mapVirtualAngleDeg_skipsUnselectedSectors() {
        // 仅保留第 2 个扇区（索引 2，覆盖 90°..180°），虚拟 10° 应落到 100°。
        assertEquals(100f, QuickWheelLayoutEngine.mapVirtualAngleDeg(0b0100, 10f), 0.01f)
    }

    // ── 自适应求解（真实调用按触发位置选扇区 / 矩形基准角）──────

    /** 用"逻辑像素"模拟一台 400×800 的手机：density = 1，默认样式即照抄真实手感。 */
    private val screenWidthPx = 400f
    private val screenHeightPx = 800f
    private val screenMarginPx = 10f

    private fun solveMaskAt(anchorX: Float, anchorY: Float, slotCount: Int = 6): Int =
        QuickWheelLayoutEngine.solveSectorMaskForAnchor(
            anchorX = anchorX,
            anchorY = anchorY,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            marginPx = screenMarginPx,
            style = defaultStyle,
            slotCount = slotCount,
            density = 1f,
        )

    @Test
    fun solveSectorMask_rightEdgeMiddlePicksLeftHalf() {
        // 右边缘中点：整圆 / 270° 总有容器探出屏幕 → 退到左半圆（扇区 2+3）。
        assertEquals(0b1100, solveMaskAt(anchorX = screenWidthPx, anchorY = screenHeightPx / 2f))
    }

    @Test
    fun solveSectorMask_bottomRightCornerPicksTopLeftSector() {
        // 右下角：只有"左上"的 90°（扇区 3）能尽量收进屏幕。
        assertEquals(0b1000, solveMaskAt(anchorX = screenWidthPx, anchorY = screenHeightPx))
    }

    @Test
    fun solveSectorMask_screenCenterKeepsFullCircle() {
        assertEquals(QuickWheelLayoutEngine.SECTOR_ALL, solveMaskAt(screenWidthPx / 2f, screenHeightPx / 2f, slotCount = 4))
    }

    @Test
    fun solveSectorMask_screenCenterFitsEverything() {
        // 屏幕正中触发：整圆放得下 → 必须 0 溢出（自适应"保证全在屏内"在这里成立）。
        val mask = solveMaskAt(screenWidthPx / 2f, screenHeightPx / 2f, slotCount = 4)
        assertEquals(
            0f,
            QuickWheelLayoutEngine.slotsOverflow(
                slots = QuickWheelLayoutEngine.layoutRing(
                    slotCount = 4,
                    shape = QuickWheelShape.CIRCLE,
                    style = defaultStyle,
                    anchorX = screenWidthPx / 2f,
                    anchorY = screenHeightPx / 2f,
                    density = 1f,
                    availableWidthPx = screenWidthPx,
                    sectorMask = mask,
                ),
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                marginPx = screenMarginPx,
            ),
            0.01f,
        )
    }

    @Test
    fun solveSectorMask_returnsLeastOverflowCandidate() {
        // ⚠️ 角落 / 边缘触发时**可能没有任何候选能完全放进屏幕**：格位按"单环容量"等分，
        // 容器落在格位中心，因此 90° 扇区里最靠近 X 轴的那个容器会有半个身位探出屏幕边缘
        //（左上角 (0,0) + 默认样式时约 5.1px）。此时求解器的契约是
        // **返回所有候选中越界最小的那个**，而不是保证 0 溢出。
        listOf(0f, screenWidthPx / 2f, screenWidthPx).forEach { anchorX ->
            listOf(0f, screenHeightPx / 2f, screenHeightPx).forEach { anchorY ->
                fun overflowOf(maskToTry: Int): Float = QuickWheelLayoutEngine.slotsOverflow(
                    slots = QuickWheelLayoutEngine.layoutRing(
                        slotCount = 4,
                        shape = QuickWheelShape.CIRCLE,
                        style = defaultStyle,
                        anchorX = anchorX,
                        anchorY = anchorY,
                        density = 1f,
                        availableWidthPx = screenWidthPx,
                        sectorMask = maskToTry,
                    ),
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    marginPx = screenMarginPx,
                )
                val solved = solveMaskAt(anchorX, anchorY, slotCount = 4)
                val best = (1..QuickWheelLayoutEngine.SECTOR_ALL).minOf { overflowOf(it) }
                assertEquals(
                    "anchor=($anchorX, $anchorY) solved=$solved",
                    best,
                    overflowOf(solved),
                    0.01f,
                )
            }
        }
    }

    @Test
    fun buildLayout_primarySectorMaskOverrideWinsOverSolver() {
        // 右边缘中点触发：自适应本来会解出"左半圆"（扇区 2+3）。
        val auto = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 4,
            secondaryCount = 0,
            expandedPrimaryIndex = -1,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = screenWidthPx,
            anchorY = screenHeightPx / 2f,
            density = 1f,
            screenWidthPx = screenWidthPx,
            adaptive = QuickWheelAdaptiveScreen(
                widthPx = screenWidthPx,
                heightPx = screenHeightPx,
                marginPx = screenMarginPx,
            ),
        )
        assertEquals(0b1100, auto.sectorMask)

        // 手动指定"左上 90°"（扇区 3）→ 覆盖求解结果（哪怕它会出屏）。
        val manual = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 4,
            secondaryCount = 0,
            expandedPrimaryIndex = -1,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = screenWidthPx,
            anchorY = screenHeightPx / 2f,
            density = 1f,
            screenWidthPx = screenWidthPx,
            primarySectorMaskOverride = 0b1000,
            adaptive = QuickWheelAdaptiveScreen(
                widthPx = screenWidthPx,
                heightPx = screenHeightPx,
                marginPx = screenMarginPx,
            ),
        )
        assertEquals(0b1000, manual.sectorMask)
    }

    @Test
    fun buildLayout_primarySectorMaskOverride_ignoredForRectPrimary() {
        // 矩形一级没有"扇区"概念：即使（历史遗留的）载荷带着手动掩码，也一律忽略 → 排布完全一致。
        fun build(maskOverride: Int?) = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 4,
            secondaryCount = 0,
            expandedPrimaryIndex = -1,
            primaryShape = QuickWheelShape.RECT,
            secondaryShape = QuickWheelShape.RECT,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
            primarySectorMaskOverride = maskOverride,
        )
        val plain = build(maskOverride = null)
        val withOverride = build(maskOverride = 0b1000)
        assertEquals(plain.sectorMask, withOverride.sectorMask)
        plain.primarySlots.zip(withOverride.primarySlots).forEach { (a, b) ->
            assertEquals(a.centerX, b.centerX, 0.01f)
            assertEquals(a.centerY, b.centerY, 0.01f)
        }
    }

    @Test
    fun buildLayout_secondaryAdaptive_solvesSecondaryOnlyKeepingPrimaryAsConfigured() {
        // 配置页预览 / 编辑层：一级保持"所见即所得"（按配置扇区），二级按屏幕求解 → 二级全在屏内。
        val screen = QuickWheelAdaptiveScreen(
            widthPx = screenWidthPx,
            heightPx = screenHeightPx,
            marginPx = screenMarginPx,
        )
        fun build(secondarySolve: QuickWheelAdaptiveScreen?): QuickWheelLayout =
            QuickWheelLayoutEngine.buildLayout(
                primaryCount = 4,
                secondaryCount = 6,
                expandedPrimaryIndex = 0,
                primaryShape = QuickWheelShape.CIRCLE,
                secondaryShape = QuickWheelShape.CIRCLE,
                primaryStyle = defaultStyle,
                secondaryStyle = defaultStyle,
                anchorX = 320f,
                anchorY = 400f,
                density = 1f,
                screenWidthPx = screenWidthPx,
                secondaryAdaptive = secondarySolve,
            )
        val plain = build(secondarySolve = null)
        val solved = build(secondarySolve = screen)

        // 一级逐个一致（扇区仍按配置，不受二级求解影响）。
        assertEquals(plain.sectorMask, solved.sectorMask)
        assertEquals(plain.primarySlots, solved.primarySlots)
        // 二级：不求解时是整圆 → 越界；求解后全部落在屏幕内。
        assertTrue(
            QuickWheelLayoutEngine.slotsOverflow(
                slots = plain.secondarySlots,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                marginPx = screenMarginPx,
            ) > 0f,
        )
        assertEquals(
            0f,
            QuickWheelLayoutEngine.slotsOverflow(
                slots = solved.secondarySlots,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                marginPx = screenMarginPx,
            ),
            0.01f,
        )
    }

    @Test
    fun buildLayout_editorSecondaryAddSlot_staysOnScreen() {
        // 编辑层：二级按**真实数量**求解（与真机一致）；只有当「+」因此落到屏外时才把它计入重解
        // → 无论哪种情况，「+」与全部真实二级容器都必须留在屏内。
        val screen = QuickWheelAdaptiveScreen(
            widthPx = screenWidthPx,
            heightPx = screenHeightPx,
            marginPx = screenMarginPx,
        )
        listOf(0, 1, 3, 6).forEach { count ->
            val layout = QuickWheelLayoutEngine.buildLayout(
                primaryCount = 4,
                secondaryCount = count,
                expandedPrimaryIndex = 0,
                primaryShape = QuickWheelShape.CIRCLE,
                secondaryShape = QuickWheelShape.CIRCLE,
                primaryStyle = defaultStyle,
                secondaryStyle = defaultStyle,
                anchorX = 320f,
                anchorY = 400f,
                density = 1f,
                screenWidthPx = screenWidthPx,
                secondaryAdaptive = screen,
                appendSecondaryAddSlot = true,
            )
            assertEquals("count=$count", count + 1, layout.secondarySlots.size)
            assertEquals(
                "count=$count 的「+」必须在屏内",
                0f,
                QuickWheelLayoutEngine.slotsOverflow(
                    slots = listOf(layout.secondarySlots.last()),
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    marginPx = screenMarginPx,
                ),
                0.01f,
            )
            assertEquals(
                "count=$count 的真实二级容器必须在屏内",
                0f,
                QuickWheelLayoutEngine.slotsOverflow(
                    slots = layout.secondarySlots.dropLast(1),
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    marginPx = screenMarginPx,
                ),
                0.01f,
            )
        }
    }

    @Test
    fun buildLayout_primarySectorMaskOverride_keepsSecondaryAdaptive() {
        // 手动固定一级扇区后，二级仍按"父容器 + 屏幕"自适应求解：
        // 6 个二级容器若退化成整圆（非自适应时就是整圆）从右下角展开必有容器出屏。
        val layout = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 4,
            secondaryCount = 3,
            expandedPrimaryIndex = 0,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = screenWidthPx,
            anchorY = screenHeightPx,
            density = 1f,
            screenWidthPx = screenWidthPx,
            primarySectorMaskOverride = 0b1000,
            adaptive = QuickWheelAdaptiveScreen(
                widthPx = screenWidthPx,
                heightPx = screenHeightPx,
                marginPx = screenMarginPx,
            ),
        )
        assertEquals(0b1000, layout.sectorMask)
        assertEquals(3, layout.secondarySlots.size)
        assertEquals(
            "手动一级扇区不应把二级拖下水（二级仍须自适应求解）",
            0f,
            QuickWheelLayoutEngine.slotsOverflow(
                slots = layout.secondarySlots,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                marginPx = screenMarginPx,
            ),
            0.01f,
        )
    }

    @Test
    fun solveRectCorner_bottomRightTriggerAnchorsGridBottomRightCorner() {
        val corner = QuickWheelLayoutEngine.solveRectCornerForAnchor(
            anchorX = screenWidthPx - screenMarginPx,
            anchorY = screenHeightPx - screenMarginPx,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            marginPx = screenMarginPx,
            style = defaultStyle,
            slotCount = 3,
            density = 1f,
        )
        assertEquals(QuickWheelRectCorner.BOTTOM_RIGHT, corner)
    }

    @Test
    fun solveRectCorner_flipsToTopRightWhenTriggerMovesUp() {
        // 触发点上移后"向下铺开"才放得下 → 基准点变成"第一行最右容器的右上角"。
        val corner = QuickWheelLayoutEngine.solveRectCornerForAnchor(
            anchorX = screenWidthPx - screenMarginPx,
            anchorY = 300f,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            marginPx = screenMarginPx,
            style = defaultStyle,
            slotCount = 3,
            density = 1f,
        )
        assertEquals(QuickWheelRectCorner.TOP_RIGHT, corner)
    }

    @Test
    fun layoutRing_rectCornerAnchorsGridCornerToPoint() {
        val style = defaultStyle.copy(columnCount = 3)
        val bottomRight = QuickWheelLayoutEngine.layoutRing(
            slotCount = 3,
            shape = QuickWheelShape.RECT,
            style = style,
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 1_000f,
            rectCorner = QuickWheelRectCorner.BOTTOM_RIGHT,
        )
        // 网格右下角贴 (0, 0)：右沿与下沿都落在锚点上。
        assertEquals(0f, bottomRight.maxOf { it.centerX + it.sizePx / 2f }, 0.01f)
        assertEquals(0f, bottomRight.maxOf { it.centerY + it.sizePx / 2f }, 0.01f)

        val topRight = QuickWheelLayoutEngine.layoutRing(
            slotCount = 3,
            shape = QuickWheelShape.RECT,
            style = style,
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 1_000f,
            rectCorner = QuickWheelRectCorner.TOP_RIGHT,
        )
        // 网格右上角贴 (0, 0)：右沿与上沿都落在锚点上。
        assertEquals(0f, topRight.maxOf { it.centerX + it.sizePx / 2f }, 0.01f)
        assertEquals(0f, topRight.minOf { it.centerY - it.sizePx / 2f }, 0.01f)
    }

    @Test
    fun pickPreviewSecondaryParentIndex_prefersEmptySlotThenLast() {
        // 优先最后一个"没有子容器"的格位（不掩盖真实二级）。
        assertEquals(
            2,
            QuickWheelLayoutEngine.pickPreviewSecondaryParentIndex(listOf(true, true, false, true)),
        )
        // 全都有子容器 → 退回末位。
        assertEquals(2, QuickWheelLayoutEngine.pickPreviewSecondaryParentIndex(listOf(true, true, true)))
        // 没有格位 → -1（不显示样本二级）。
        assertEquals(-1, QuickWheelLayoutEngine.pickPreviewSecondaryParentIndex(emptyList()))
    }

    @Test
    fun projectAnchorToEdge_snapsToNearestEdgeKeepingAlongEdgeCoordinate() {
        // 左 / 右 / 上 / 下：投到最近那条边，沿边坐标保持不变。
        assertEquals(
            0f to 300f,
            QuickWheelLayoutEngine.projectAnchorToEdge(40f, 300f, screenWidthPx, screenHeightPx),
        )
        assertEquals(
            screenWidthPx to 300f,
            QuickWheelLayoutEngine.projectAnchorToEdge(screenWidthPx - 40f, 300f, screenWidthPx, screenHeightPx),
        )
        assertEquals(
            200f to 0f,
            QuickWheelLayoutEngine.projectAnchorToEdge(200f, 30f, screenWidthPx, screenHeightPx),
        )
        assertEquals(
            200f to screenHeightPx,
            QuickWheelLayoutEngine.projectAnchorToEdge(200f, screenHeightPx - 30f, screenWidthPx, screenHeightPx),
        )
    }

    @Test
    fun projectAnchorToEdge_cornerTakesFirstOfEqualDistance_andOnEdgeIsIdempotent() {
        // 右下角：到右边与到下边距离相同（5px）→ 按 左→右→上→下 取"右"；沿边坐标仍是 795（不平移到角点）。
        assertEquals(
            screenWidthPx to (screenHeightPx - 5f),
            QuickWheelLayoutEngine.projectAnchorToEdge(
                screenWidthPx - 5f,
                screenHeightPx - 5f,
                screenWidthPx,
                screenHeightPx,
            ),
        )
        // 已经在边线上 → 幂等（左下角取"左"）。
        assertEquals(
            0f to screenHeightPx,
            QuickWheelLayoutEngine.projectAnchorToEdge(0f, screenHeightPx, screenWidthPx, screenHeightPx),
        )
    }

    @Test
    fun theoreticalAnchor_mapsSectorToWhereItWouldAppear() {
        // 左半圆 → 屏幕右边缘中点。
        val leftHalf = QuickWheelLayoutEngine.theoreticalAnchorForMask(
            sectorMask = 0b1100,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
        assertEquals(screenWidthPx, leftHalf.first, 0.01f)
        assertEquals(screenHeightPx / 2f, leftHalf.second, 0.01f)

        // 左下 90° → 右上角。
        val bottomLeft = QuickWheelLayoutEngine.theoreticalAnchorForMask(
            sectorMask = 0b0100,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
        assertEquals(screenWidthPx, bottomLeft.first, 0.01f)
        assertEquals(0f, bottomLeft.second, 0.01f)

        // 整圆 → 屏幕正中。
        val full = QuickWheelLayoutEngine.theoreticalAnchorForMask(
            sectorMask = QuickWheelLayoutEngine.SECTOR_ALL,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
        assertEquals(screenWidthPx / 2f, full.first, 0.01f)
        assertEquals(screenHeightPx / 2f, full.second, 0.01f)
    }

    // ── 环容量与估算 ────────────────────────────────────────

    @Test
    fun ringCapacity_circleUsesAngularBudget() {
        val capacity = QuickWheelLayoutEngine.ringCapacity(
            radiusPx = 110f,
            containerSizePx = 50f,
            containerGapPx = 20f,
            shape = QuickWheelShape.CIRCLE,
            availableWidthPx = 1080f,
        )
        assertEquals(9, capacity)
    }

    @Test
    fun ringCapacity_circleDegeneratesToSingleSlotWhenRadiusTooSmall() {
        val capacity = QuickWheelLayoutEngine.ringCapacity(
            radiusPx = 20f,
            containerSizePx = 50f,
            containerGapPx = 20f,
            shape = QuickWheelShape.CIRCLE,
            availableWidthPx = 1080f,
        )
        assertEquals(1, capacity)
    }

    @Test
    fun ringCapacity_rectUsesConfiguredColumnCount() {
        // 矩形形态每行固定列数，由用户设置决定，与可用宽度无关。
        val capacity = QuickWheelLayoutEngine.ringCapacity(
            radiusPx = 110f,
            containerSizePx = 50f,
            containerGapPx = 20f,
            shape = QuickWheelShape.RECT,
            availableWidthPx = 0f,
        )
        assertEquals(QuickWheelLayoutEngine.DEFAULT_COLUMN_COUNT, capacity)
    }

    @Test
    fun ringCapacity_rectClampsColumnCountToRange() {
        assertEquals(
            QuickWheelLayoutEngine.MIN_COLUMN_COUNT,
            QuickWheelLayoutEngine.ringCapacity(110f, 50f, 20f, QuickWheelShape.RECT, 1080f, columnCount = 1),
        )
        assertEquals(
            QuickWheelLayoutEngine.MAX_COLUMN_COUNT,
            QuickWheelLayoutEngine.ringCapacity(110f, 50f, 20f, QuickWheelShape.RECT, 1080f, columnCount = 99),
        )
        assertEquals(
            7,
            QuickWheelLayoutEngine.ringCapacity(110f, 50f, 20f, QuickWheelShape.RECT, 1080f, columnCount = 7),
        )
    }

    @Test
    fun estimateRingCount_fullCirclePacksTwoRingsForTwentySlots() {
        val rings = QuickWheelLayoutEngine.estimateRingCount(
            slotCount = 20,
            shape = QuickWheelShape.CIRCLE,
            style = defaultStyle,
            availableWidthPx = 1080f,
        )
        // 第 1 环 9 个 + 第 2 环 19 个 ≥ 20。
        assertEquals(2, rings)
    }

    @Test
    fun estimateRingCount_zeroSlotsIsZeroRings() {
        assertEquals(
            0,
            QuickWheelLayoutEngine.estimateRingCount(0, QuickWheelShape.CIRCLE, defaultStyle, 1080f),
        )
    }

    // ── 环布点 ──────────────────────────────────────────────

    @Test
    fun layoutRing_circlePlacesAllSlotsOnSameRingAroundAnchor() {
        val anchorX = 540f
        val anchorY = 960f
        // 填满单环容量（默认样式下整圆单环容量为 9），此时才在整圆上均匀且对称。
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 9,
            shape = QuickWheelShape.CIRCLE,
            style = defaultStyle,
            anchorX = anchorX,
            anchorY = anchorY,
            density = 1f,
            availableWidthPx = 1080f,
        )
        assertEquals(9, slots.size)
        assertTrue(slots.all { it.ringIndex == 0 })
        slots.forEach { slot ->
            val radius = hypot(slot.centerX - anchorX, slot.centerY - anchorY)
            assertEquals(defaultStyle.initialRadiusDp, radius, 0.5f)
        }
        // 固定格位：格位按**单环容量**（默认样式下整圆 = 12 个格位）等分，容器落在第 0..8 个格位上；
        // 未布满时**不做"整组居中"补偿** → 不再关于竖直轴对称（这是"新增容器不移动已有容器"的代价）。
        val capacity = QuickWheelLayoutEngine.ringCapacity(
            radiusPx = defaultStyle.initialRadiusDp,
            containerSizePx = defaultStyle.containerSizeDp,
            containerGapPx = defaultStyle.containerGapDp,
            shape = QuickWheelShape.CIRCLE,
            availableWidthPx = 1080f,
        )
        // 相邻格位角距 = 360 / capacity → 弦长 = 2 × 半径 × sin(180° / capacity)。
        val expectedChord = 2f * defaultStyle.initialRadiusDp *
            kotlin.math.sin(Math.toRadians(180.0 / capacity)).toFloat()
        slots.zipWithNext { a, b ->
            assertEquals(
                "capacity=$capacity",
                expectedChord,
                hypot(a.centerX - b.centerX, a.centerY - b.centerY),
                0.5f,
            )
        }
    }

    @Test
    fun layoutRing_rectKeepsOneRowWithEvenSpacing() {
        // 默认列数 5，放 4 个仍在一行内。
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 4,
            shape = QuickWheelShape.RECT,
            style = defaultStyle,
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 1080f,
        )
        assertEquals(4, slots.size)
        assertTrue(slots.all { it.ringIndex == 0 })
        // 矩形第 0 行紧贴呼出点：仅留一个容器间距 → 容器半宽 + 间距 = 25 + 20 = 45。
        assertEquals(
            defaultStyle.containerSizeDp / 2f + defaultStyle.containerGapDp,
            slots[0].centerY,
            0.01f,
        )
        assertEquals(slots[0].centerY, slots[1].centerY, 0.01f)
        // 行内步长 = 容器尺寸 + 容器间距。
        assertEquals(
            defaultStyle.containerSizeDp + defaultStyle.containerGapDp,
            slots[1].centerX - slots[0].centerX,
            0.01f,
        )
    }

    @Test
    fun layoutRing_rectWrapsToNextRowOnColumnOverflow() {
        // 默认列数 5：7 个容器 = 首行 5 + 次行 2。
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 7,
            shape = QuickWheelShape.RECT,
            style = defaultStyle,
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 1080f,
        )
        assertEquals(7, slots.size)
        assertEquals(QuickWheelLayoutEngine.DEFAULT_COLUMN_COUNT, slots.count { it.ringIndex == 0 })
        assertEquals(2, slots.count { it.ringIndex == 1 })
        // 次行在首行基础上按「容器尺寸 + 容器间距」下移：45 + (50 + 20) = 115。
        assertEquals(
            defaultStyle.containerSizeDp / 2f + defaultStyle.containerGapDp +
                (defaultStyle.containerSizeDp + defaultStyle.containerGapDp),
            slots.first { it.ringIndex == 1 }.centerY,
            0.01f,
        )
    }

    @Test
    fun layoutRing_rectHonorsConfiguredColumnCount() {
        // 列数 3：7 个容器 = 首行 3 + 次行 3 + 三行 1。
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 7,
            shape = QuickWheelShape.RECT,
            style = defaultStyle.copy(columnCount = 3),
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 1080f,
        )
        assertEquals(3, slots.count { it.ringIndex == 0 })
        assertEquals(3, slots.count { it.ringIndex == 1 })
        assertEquals(1, slots.count { it.ringIndex == 2 })
    }

    @Test
    fun layoutRing_circleSmallContainerWithZeroGapPacksAdjacent() {
        // 容器 32dp + 间隙 0：步距 = 容器尺寸 → 相邻容器紧挨着。
        // （旧实现按固定 50dp 基准排布，即使间隙为 0 也会剩约 18dp 空档。）
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 8,
            shape = QuickWheelShape.CIRCLE,
            style = defaultStyle.copy(containerSizeDp = 32f, containerGapDp = 0f),
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            availableWidthPx = 1080f,
        )
        assertEquals(8, slots.size)
        assertTrue(slots.all { it.ringIndex == 0 })
        assertEquals(32f, slots[0].sizePx, 0.01f)
        // 相邻中心距 ≈ 容器尺寸（整环容量取整只会让它略大一点）。
        val adjacent = hypot(
            slots[1].centerX - slots[0].centerX,
            slots[1].centerY - slots[0].centerY,
        )
        assertTrue("相邻中心距应贴近容器尺寸 32，实际 $adjacent", adjacent < 33f)
        // 净间隙 = 中心距 − 容器尺寸 ≈ 0。
        assertTrue("相邻容器应紧挨着，实际净间隙 ${adjacent - 32f}", adjacent - 32f < 1f)
    }

    @Test
    fun layoutRing_circleSmallerContainerPacksMorePerRing() {
        // 容器调小 → 单环容量变大（同样的 12 个容器，小容器仍能全放第 1 环）。
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 12,
            shape = QuickWheelShape.CIRCLE,
            style = defaultStyle.copy(containerSizeDp = 32f),
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            availableWidthPx = 1080f,
        )
        assertEquals(12, slots.size)
        assertTrue(slots.all { it.ringIndex == 0 })
    }

    @Test
    fun layoutRing_rectGeometryFollowsActualContainerSize() {
        // 矩形同样按实际容器尺寸排布：首行贴锚点的距离、行内步距、行距都用「尺寸 + 间隙」。
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 7,
            shape = QuickWheelShape.RECT,
            style = defaultStyle.copy(containerSizeDp = 32f, columnCount = 3),
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 1080f,
        )
        // 首行紧贴锚点：半容器 + 容器间距 = 16 + 10 = 26。
        assertEquals(26f, slots[0].centerY, 0.01f)
        // 行内步距 = 容器尺寸 + 间距 = 32 + 10 = 42。
        assertEquals(42f, slots[1].centerX - slots[0].centerX, 0.01f)
        // 行距 = 容器尺寸 + 间距 = 42 → 次行 = 26 + 42 = 68。
        assertEquals(68f, slots.first { it.ringIndex == 1 }.centerY, 0.01f)
    }

    @Test
    fun layoutRing_stopsGrowingBeyondMaxRings() {
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 1_000,
            shape = QuickWheelShape.CIRCLE,
            style = defaultStyle,
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 320f,
        )
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it.ringIndex in 0 until QuickWheelLayoutEngine.MAX_RINGS })
    }

    @Test
    fun layoutRing_zeroSlotsIsEmpty() {
        assertTrue(
            QuickWheelLayoutEngine.layoutRing(
                slotCount = 0,
                shape = QuickWheelShape.CIRCLE,
                style = defaultStyle,
                anchorX = 0f,
                anchorY = 0f,
                density = 1f,
                availableWidthPx = 1080f,
            ).isEmpty(),
        )
    }

    // ── 中心容器 ────────────────────────────────────────────

    @Test
    fun layoutCenter_circleIsPerfectRoundAndInsetFromFirstRing() {
        val center = QuickWheelLayoutEngine.layoutCenter(
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            primaryStyle = defaultStyle,
            primaryShape = QuickWheelShape.CIRCLE,
        )
        // 默认参数（初始半径 100、容器 42、环间距 15）：大圆半径 = (100 − 21) − 15 = 64
        //（未撞下限 0.75 × 42 = 31.5，也未撞上限 79）→ 直径 = 128。（与环间距的关系见下一条用例。）
        assertEquals(128f, center.sizePx, 0.01f)
        assertEquals(64f, center.cornerPx, 0.01f)
        assertEquals(540f, center.centerX, 0.01f)
        assertEquals(960f, center.centerY, 0.01f)
        assertEquals(QuickWheelLayoutEngine.CENTER_INDEX, center.index)
    }

    @Test
    fun layoutCenter_circleSizeIsIndependentOfContainerGap() {
        // 「容器间距」只改角度步距 / 单环容量（单环放几个、一共几环），**不改变环半径**
        // → 中心圆尺寸必须恒定不变。（旧实现把留白写成容器间距，间距 +1dp 圆盘半径就 −1dp。）
        // 用较大的初始半径（160dp）避开下限，确保"不变"不是因为被下限托住。
        val style = defaultStyle.copy(initialRadiusDp = 160f)
        val gaps = listOf(0f, 20f, 40f, 60f, 160f)
        val sizes = gaps.map { gap ->
            QuickWheelLayoutEngine.layoutCenter(
                anchorX = 540f,
                anchorY = 960f,
                density = 1f,
                primaryStyle = style.copy(containerGapDp = gap),
                primaryShape = QuickWheelShape.CIRCLE,
            ).sizePx
        }
        assertTrue("中心圆直径应恒定，实际 $gaps -> $sizes", sizes.distinct().size == 1)
        // 直径 = (初始半径 − 容器半宽 − 环间距) × 2。
        assertEquals(
            (style.initialRadiusDp - style.containerSizeDp / 2f - style.ringGapDp) * 2f,
            sizes.first(),
            0.01f,
        )
    }

    @Test
    fun layoutCenter_clearanceToFirstRingFollowsRingGap() {
        // 大圆 = 「第 0 环」：它与第一环容器内缘的间隙 = 「环间距」；未撞下限时 1:1 联动。
        val style = defaultStyle.copy(initialRadiusDp = 160f, ringGapDp = 20f)
        val center = QuickWheelLayoutEngine.layoutCenter(
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            primaryStyle = style,
            primaryShape = QuickWheelShape.CIRCLE,
        )
        val ring = QuickWheelLayoutEngine.layoutRing(
            slotCount = 3,
            shape = QuickWheelShape.CIRCLE,
            style = style,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            availableWidthPx = 1080f,
        )
        val ringRadius = hypot(ring[0].centerX - 540f, ring[0].centerY - 960f)
        // initialRadius 是第一圈容器**中心**到锚点的半径（不是大圆半径）。
        assertEquals(style.initialRadiusDp, ringRadius, 0.01f)
        assertEquals(
            style.ringGapDp,
            ringRadius - style.containerSizeDp / 2f - center.sizePx / 2f,
            0.01f,
        )
        // 环间距 +10dp → 大圆半径 −10dp（同一套参数的联动）。
        val wider = QuickWheelLayoutEngine.layoutCenter(
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            primaryStyle = style.copy(ringGapDp = style.ringGapDp + 10f),
            primaryShape = QuickWheelShape.CIRCLE,
        )
        assertEquals(center.sizePx / 2f - 10f, wider.sizePx / 2f, 0.01f)
    }

    @Test
    fun layoutCenter_circleNeverOverlapsFirstRingEvenForLargeContainers() {
        // 大容器（或极小初始半径）时大圆会被下限托住，但**绝不能**压到第一环容器内缘。
        listOf(
            defaultStyle,
            defaultStyle.copy(initialRadiusDp = QuickWheelLayoutEngine.MIN_INITIAL_RADIUS_DP),
        ).forEach { style ->
            listOf(32f, 50f, 88f).forEach { size ->
                val center = QuickWheelLayoutEngine.layoutCenter(
                    anchorX = 540f,
                    anchorY = 960f,
                    density = 1f,
                    primaryStyle = style.copy(containerSizeDp = size),
                    primaryShape = QuickWheelShape.CIRCLE,
                )
                val ringInnerEdge = style.initialRadiusDp - size / 2f
                assertTrue(
                    "初始半径 ${style.initialRadiusDp} / 容器 $size：大圆半径 ${center.sizePx / 2f} " +
                        "不应超过第一环内缘 $ringInnerEdge",
                    center.sizePx / 2f <= ringInnerEdge + 0.01f,
                )
                // 中心点始终不动。
                assertEquals(540f, center.centerX, 0.01f)
                assertEquals(960f, center.centerY, 0.01f)
            }
        }
    }

    @Test
    fun layoutCenter_circleShrinksAsContainerGrowsWhenNotFloored() {
        // 未撞下限时（初始半径取大）：容器越大 → 可用空间越小 → 大圆越小。
        val style = defaultStyle.copy(initialRadiusDp = 160f)
        val sizes = listOf(32f, 50f, 88f).map { size ->
            QuickWheelLayoutEngine.layoutCenter(
                anchorX = 540f,
                anchorY = 960f,
                density = 1f,
                primaryStyle = style.copy(containerSizeDp = size),
                primaryShape = QuickWheelShape.CIRCLE,
            ).sizePx
        }
        assertTrue("容器变大时大圆应变小，实际 $sizes", sizes[0] > sizes[1] && sizes[1] > sizes[2])
    }

    @Test
    fun layoutCenter_circleFollowsOffset() {
        // 中心大圆必须跟外围小容器一起被横/纵向偏移平移。
        val offsetStyle = defaultStyle.copy(offsetXDp = 30f, offsetYDp = -20f)
        val center = QuickWheelLayoutEngine.layoutCenter(
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            primaryStyle = offsetStyle,
            primaryShape = QuickWheelShape.CIRCLE,
        )
        assertEquals(570f, center.centerX, 0.01f)
        assertEquals(940f, center.centerY, 0.01f)
    }

    @Test
    fun layoutCenter_rectHasNoCenterContainer() {
        val center = QuickWheelLayoutEngine.layoutCenter(
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            primaryStyle = defaultStyle,
            primaryShape = QuickWheelShape.RECT,
        )
        // 矩形形态没有中心容器：几何尺寸恒为 0。
        assertEquals(0f, center.sizePx, 0.01f)
        assertEquals(0f, center.cornerPx, 0.01f)
    }

    // ── 布局装配与命中 ──────────────────────────────────────

    private fun buildSimpleLayout(primaryCount: Int = 4): QuickWheelLayout =
        QuickWheelLayoutEngine.buildLayout(
            primaryCount = primaryCount,
            secondaryCount = 0,
            expandedPrimaryIndex = -1,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )

    @Test
    fun hitTest_centerWinsInsideCenterCircle() {
        val layout = buildSimpleLayout()
        val hit = QuickWheelLayoutEngine.hitTest(layout, QuickWheelShape.CIRCLE, layout.centerX, layout.centerY)
        assertEquals(QuickWheelHitKind.CENTER, hit.kind)
        assertEquals(QuickWheelLayoutEngine.CENTER_INDEX, hit.index)
    }

    @Test
    fun hitTest_rectIgnoresCenter() {
        // 矩形形态没有中心容器：呼出点区域不应命中 CENTER。
        val layout = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 4,
            secondaryCount = 0,
            expandedPrimaryIndex = -1,
            primaryShape = QuickWheelShape.RECT,
            secondaryShape = QuickWheelShape.RECT,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )
        val hit = QuickWheelLayoutEngine.hitTest(layout, QuickWheelShape.RECT, layout.centerX, layout.centerY)
        assertTrue(hit.kind != QuickWheelHitKind.CENTER)
    }

    @Test
    fun buildLayout_offsetMovesCenterAndPrimaryTogether() {
        val offsetStyle = defaultStyle.copy(offsetXDp = 40f, offsetYDp = 25f)
        val layout = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 4,
            secondaryCount = 0,
            expandedPrimaryIndex = -1,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = offsetStyle,
            secondaryStyle = offsetStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )
        assertEquals(580f, layout.centerX, 0.01f)
        assertEquals(985f, layout.centerY, 0.01f)
    }

    @Test
    fun hitTest_returnsPrimaryIndexOnContainerCenter() {
        val layout = buildSimpleLayout()
        val expected = layout.primarySlots[0]
        val hit = QuickWheelLayoutEngine.hitTest(
            layout = layout,
            shape = QuickWheelShape.CIRCLE,
            rawX = expected.centerX,
            rawY = expected.centerY,
        )
        assertEquals(QuickWheelHitKind.PRIMARY, hit.kind)
        assertEquals(0, hit.index)
    }

    @Test
    fun hitTest_missesEmptyArea() {
        val layout = buildSimpleLayout()
        val hit = QuickWheelLayoutEngine.hitTest(layout, QuickWheelShape.CIRCLE, 5f, 5f)
        assertEquals(QuickWheelHitKind.NONE, hit.kind)
    }

    @Test
    fun hitTest_prefersSecondaryWhenExpanded() {
        val layout = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 5,
            secondaryCount = 3,
            expandedPrimaryIndex = 2,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )
        assertEquals(5, layout.primarySlots.size)
        assertEquals(3, layout.secondarySlots.size)
        assertEquals(2, layout.expandedPrimaryIndex)

        // 取离呼出圆心最远的二级容器（二级现在本就优先于中心，这里只是避开争议点）。
        val farthest = layout.secondarySlots.maxByOrNull {
            hypot(it.centerX - layout.centerX, it.centerY - layout.centerY)
        }!!
        val hit = QuickWheelLayoutEngine.hitTest(
            layout = layout,
            shape = QuickWheelShape.CIRCLE,
            rawX = farthest.centerX,
            rawY = farthest.centerY,
        )
        assertEquals(QuickWheelHitKind.SECONDARY, hit.kind)
    }

    @Test
    fun hitTest_secondaryWinsOverCenterWhenOverlapping() {
        // 二级环的圆心 = 父容器中心，父容器距呼出圆心 = 一级初始半径（默认 100dp），二级初始半径
        // 也是 100dp。环上格位从**正上方**起排，因此只有二级铺得足够多、覆盖到"背向父容器"的那一节
        // 格位时，才会出现**落进中心大圆内部**的二级容器（大圆半径 = 100−21−15 = 64dp；
        // 默认格位步距 30°、容量 12，第 9 个格位正好指向父容器的反方向 → 落在呼出圆心上）。
        // 视觉上二级画在中心之上，因此命中也必须归二级，否则"点二级容器"会被中心抢走，
        // 还会连带把二级收起（shouldCollapseExpandedSecondary 对 CENTER 返回 true）。
        val layout = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 5,
            secondaryCount = 12,
            expandedPrimaryIndex = 2,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )
        // 前提校验：本用例确实存在"落在中心圆内"的二级容器（否则用例名不副实）。
        val overlapping = layout.secondarySlots.filter {
            hypot(it.centerX - layout.centerX, it.centerY - layout.centerY) <=
                layout.centerSizePx / 2f
        }
        assertTrue("默认参数下应有二级容器落进中心大圆", overlapping.isNotEmpty())
        overlapping.forEach { slot ->
            val hit = QuickWheelLayoutEngine.hitTest(
                layout = layout,
                shape = QuickWheelShape.CIRCLE,
                rawX = slot.centerX,
                rawY = slot.centerY,
            )
            assertEquals(
                "二级画在中心之上 → 命中必须归二级",
                QuickWheelHitKind.SECONDARY,
                hit.kind,
            )
        }
    }

    @Test
    fun buildLayout_collapsesExpandedIndexWhenNoSecondary() {
        val layout = buildSimpleLayout()
        assertTrue(layout.secondarySlots.isEmpty())
        assertEquals(-1, layout.expandedPrimaryIndex)
    }

    @Test
    fun isOutsideWheel_reportsFarPointsAsOutside() {
        val layout = buildSimpleLayout()
        assertFalse(QuickWheelLayoutEngine.isOutsideWheel(layout, layout.centerX, layout.centerY))
        assertTrue(QuickWheelLayoutEngine.isOutsideWheel(layout, layout.centerX + 1_000f, layout.centerY))
    }

    // ── 二级展开时的收起判定 ────────────────────────────────

    // ── 「+」追加位（编辑层用） ─────────────────────────────

    @Test
    fun layoutRing_addingSlotNeverMovesExistingSlots() {
        // 「固定格位」的核心不变量：再新增一个容器，已有容器的位置**一格都不动**。
        // （位置记忆 / 加号不挤占 / 编辑层与预览一致，全部建立在这条上。）
        val rectCorners = listOf(
            QuickWheelRectCorner.CENTER_TOP,
            QuickWheelRectCorner.TOP_LEFT,
            QuickWheelRectCorner.TOP_RIGHT,
            QuickWheelRectCorner.BOTTOM_LEFT,
            QuickWheelRectCorner.BOTTOM_RIGHT,
        )
        listOf(QuickWheelShape.CIRCLE, QuickWheelShape.RECT).forEach { shape ->
            val corners = if (shape == QuickWheelShape.RECT) rectCorners else rectCorners.take(1)
            corners.forEach { corner ->
                (1..12).forEach { count ->
                    val before = QuickWheelLayoutEngine.layoutRing(
                        slotCount = count,
                        shape = shape,
                        style = defaultStyle,
                        anchorX = 540f,
                        anchorY = 960f,
                        density = 1f,
                        availableWidthPx = 1080f,
                        rectCorner = corner,
                    )
                    val after = QuickWheelLayoutEngine.layoutRing(
                        slotCount = count + 1,
                        shape = shape,
                        style = defaultStyle,
                        anchorX = 540f,
                        anchorY = 960f,
                        density = 1f,
                        availableWidthPx = 1080f,
                        rectCorner = corner,
                    )
                    assertEquals(count + 1, after.size)
                    before.forEachIndexed { index, slot ->
                        assertEquals(
                            "$shape/$corner：$count → ${count + 1} 第 $index 个 centerX 不应变化",
                            slot.centerX,
                            after[index].centerX,
                            0.001f,
                        )
                        assertEquals(
                            "$shape/$corner：$count → ${count + 1} 第 $index 个 centerY 不应变化",
                            slot.centerY,
                            after[index].centerY,
                            0.001f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun layoutRing_addSlotIsExactlyTheNextRealSlot() {
        // 「+」的追加位必须**严格等于**"再新增一个容器时它会出现的格位"。
        listOf(QuickWheelShape.CIRCLE, QuickWheelShape.RECT).forEach { shape ->
            (1..10).forEach { count ->
                val appended = QuickWheelLayoutEngine.layoutRing(
                    slotCount = count,
                    shape = shape,
                    style = defaultStyle,
                    anchorX = 540f,
                    anchorY = 960f,
                    density = 1f,
                    availableWidthPx = 1080f,
                    appendAddSlot = true,
                )
                val next = QuickWheelLayoutEngine.layoutRing(
                    slotCount = count + 1,
                    shape = shape,
                    style = defaultStyle,
                    anchorX = 540f,
                    anchorY = 960f,
                    density = 1f,
                    availableWidthPx = 1080f,
                )
                assertEquals(count + 1, appended.size)
                assertEquals(
                    "$shape/$count：追加位应等于下一个真实格位（x）",
                    next[count].centerX,
                    appended[count].centerX,
                    0.001f,
                )
                assertEquals(
                    "$shape/$count：追加位应等于下一个真实格位（y）",
                    next[count].centerY,
                    appended[count].centerY,
                    0.001f,
                )
            }
        }
    }

    @Test
    fun layoutRing_appendAddSlot_keepsRealSlotsIdentical() {
        // 追加位（编辑层的「+」）不得影响真实槽位：追加与否，真实槽位位置必须逐个一致。
        // 这正是「编辑层布局 = 轮盘预览 / 真实呼出布局」的前提
        // （旧实现把「+」算进 count，环内居中偏移会让所有真实容器平移半个步距）。
        listOf(QuickWheelShape.CIRCLE, QuickWheelShape.RECT).forEach { shape ->
            listOf(3, 5, 6, 9, 10).forEach { count ->
                val plain = QuickWheelLayoutEngine.layoutRing(
                    slotCount = count,
                    shape = shape,
                    style = defaultStyle,
                    anchorX = 540f,
                    anchorY = 960f,
                    density = 1f,
                    availableWidthPx = 1080f,
                )
                val appended = QuickWheelLayoutEngine.layoutRing(
                    slotCount = count,
                    shape = shape,
                    style = defaultStyle,
                    anchorX = 540f,
                    anchorY = 960f,
                    density = 1f,
                    availableWidthPx = 1080f,
                    appendAddSlot = true,
                )
                assertEquals(count + 1, appended.size)
                plain.zip(appended).forEachIndexed { index, (a, b) ->
                    assertEquals("$shape/$count 第 $index 个 centerX", a.centerX, b.centerX, 0.001f)
                    assertEquals("$shape/$count 第 $index 个 centerY", a.centerY, b.centerY, 0.001f)
                }
                val add = appended.last()
                assertEquals(count, add.index)
                // 追加位不能压住最后一个真实容器（至少隔开一个容器尺寸）。
                val last = appended[count - 1]
                assertTrue(
                    "$shape/$count 追加位与最后容器重叠",
                    hypot(add.centerX - last.centerX, add.centerY - last.centerY) >=
                        defaultStyle.containerSizeDp,
                )
            }
        }
    }

    @Test
    fun layoutRing_appendAddSlot_onEmptyLayoutGivesOneSlot() {
        // 空轮盘（0 容器）也要有「+」：给出"第一环第一个位置"。
        val appended = QuickWheelLayoutEngine.layoutRing(
            slotCount = 0,
            shape = QuickWheelShape.CIRCLE,
            style = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            availableWidthPx = 1080f,
            appendAddSlot = true,
        )
        assertEquals(1, appended.size)
        assertEquals(0, appended[0].index)
        assertEquals(0, appended[0].ringIndex)
        assertEquals(
            defaultStyle.initialRadiusDp,
            hypot(appended[0].centerX - 540f, appended[0].centerY - 960f),
            0.01f,
        )
        // 不追加时仍然是空列表。
        assertTrue(
            QuickWheelLayoutEngine.layoutRing(
                slotCount = 0,
                shape = QuickWheelShape.CIRCLE,
                style = defaultStyle,
                anchorX = 540f,
                anchorY = 960f,
                density = 1f,
                availableWidthPx = 1080f,
            ).isEmpty(),
        )
    }

    @Test
    fun layoutRing_rectPlacesAllSlotsBeyondEightRows() {
        // 矩形行数不再共用 MAX_RINGS：列数 2 时 30 个容器 = 15 行，必须全部排出来。
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 30,
            shape = QuickWheelShape.RECT,
            style = defaultStyle.copy(columnCount = 2),
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 1080f,
        )
        assertEquals(30, slots.size)
        assertEquals(14, slots.last().ringIndex)
    }

    @Test
    fun layoutRing_appendAddSlot_survivesFullEightRows() {
        // 列数 2 排满 8 行（16 个）后，末尾「+」仍要有位置：旧实现共用 MAX_RINGS 会直接丢掉，
        // 真机表现就是"切到矩形后加号消失、点空白占位没反应"。
        val slots = QuickWheelLayoutEngine.layoutRing(
            slotCount = 16,
            shape = QuickWheelShape.RECT,
            style = defaultStyle.copy(columnCount = 2),
            anchorX = 0f,
            anchorY = 0f,
            density = 1f,
            availableWidthPx = 1080f,
            appendAddSlot = true,
        )
        assertEquals(17, slots.size)
        assertEquals(16, slots.last().index)
        assertEquals(8, slots.last().ringIndex)
    }

    @Test
    fun layoutRing_appendAddSlot_whenRingFullGoesToNextRing() {
        val capacity = QuickWheelLayoutEngine.ringCapacity(
            radiusPx = defaultStyle.initialRadiusDp,
            containerSizePx = defaultStyle.containerSizeDp,
            containerGapPx = defaultStyle.containerGapDp,
            shape = QuickWheelShape.CIRCLE,
            availableWidthPx = 1080f,
            sectorMask = QuickWheelLayoutEngine.SECTOR_ALL,
        )
        assertTrue("默认容量应大于 1，本用例才有意义", capacity > 1)
        val appended = QuickWheelLayoutEngine.layoutRing(
            slotCount = capacity,
            shape = QuickWheelShape.CIRCLE,
            style = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            availableWidthPx = 1080f,
            appendAddSlot = true,
        )
        assertEquals(capacity + 1, appended.size)
        val add = appended.last()
        assertEquals(capacity, add.index)
        // 末环刚好放满 → 追加位落到下一环的第一个位置。
        assertEquals(1, add.ringIndex)
        assertEquals(0, add.indexInRing)
    }

    /** 二级展开态：一级 5 个、二级 4 个，展开第 2 个一级容器。 */
    private fun buildExpandedLayout(): QuickWheelLayout =
        QuickWheelLayoutEngine.buildLayout(
            primaryCount = 5,
            secondaryCount = 4,
            expandedPrimaryIndex = 2,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )

    private fun shouldCollapse(
        layout: QuickWheelLayout,
        hit: QuickWheelHit,
        rawX: Float,
        rawY: Float,
    ): Boolean = QuickWheelLayoutEngine.shouldCollapseExpandedSecondary(
        layout = layout,
        hit = hit,
        rawX = rawX,
        rawY = rawY,
        slackPx = QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP,
    )

    @Test
    fun shouldCollapse_gapBetweenParentAndSecondary_keepsSecondaryOpen() {
        val layout = buildExpandedLayout()
        val parent = layout.primarySlots[layout.expandedPrimaryIndex]
        val child = layout.secondarySlots.first()
        // 父容器中心与二级容器中心的**中点**：默认参数下两者相距 110dp，
        // 而容器命中范围只有「尺寸/2 + 容差」≈ 29dp → 中点必然是"空白"。
        val x = (parent.centerX + child.centerX) / 2f
        val y = (parent.centerY + child.centerY) / 2f

        // 前提：这里确实是"空白"——正是旧实现会误收起二级的位置。
        val hit = QuickWheelLayoutEngine.hitTest(layout, QuickWheelShape.CIRCLE, x, y)
        assertEquals(QuickWheelHitKind.NONE, hit.kind)
        // 空白仍在「父容器 + 二级容器」联合区域内部 → 必须保持展开。
        assertFalse("滑经父子之间的空白不应收起二级", shouldCollapse(layout, hit, x, y))
    }

    @Test
    fun shouldCollapse_farEmptyArea_collapses() {
        val layout = buildExpandedLayout()
        val parent = layout.primarySlots[layout.expandedPrimaryIndex]
        // 二级容器离父容器中心最远约「二级初始半径 + 半容器」；沿该方向再远出 200dp，
        // 必然在任何形态的联合区域（外接矩形）之外。
        val secondaryReach = layout.secondarySlots.maxOf {
            hypot(it.centerX - parent.centerX, it.centerY - parent.centerY) + it.sizePx / 2f
        }
        val distance = secondaryReach + QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP + 200f
        // 沿「父容器相对呼出圆心」的方向外移，确保越过联合区域、且落在所有容器之外。
        val dirX = parent.centerX - layout.centerX
        val dirY = parent.centerY - layout.centerY
        val dirLen = hypot(dirX, dirY)
        val x = parent.centerX + dirX / dirLen * distance
        val y = parent.centerY + dirY / dirLen * distance
        val hit = QuickWheelLayoutEngine.hitTest(layout, QuickWheelShape.CIRCLE, x, y)
        assertEquals(QuickWheelHitKind.NONE, hit.kind)
        assertTrue("真正离开联合区域应收起二级", shouldCollapse(layout, hit, x, y))
    }

    /** 二级展开态：矩形一 / 二级（密集网格：缝下面几乎必然压着一级容器）。 */
    private fun buildExpandedRectLayout(): QuickWheelLayout =
        QuickWheelLayoutEngine.buildLayout(
            primaryCount = 10,
            secondaryCount = 6,
            expandedPrimaryIndex = 1,
            primaryShape = QuickWheelShape.RECT,
            secondaryShape = QuickWheelShape.RECT,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )

    @Test
    fun shouldCollapse_otherContainerInsideRegion_keepsSecondaryOpen() {
        // 「二级优先」：只要手指还在「父容器 + 全部二级容器」区域内，即使它落在**别的一级容器 /
        // 中心**上也不收起二级 —— 否则二级容器之间的缝下面压着一级容器时，滑过缝隙就会被一级
        // 抢走 / 收起（旧规则正是如此，矩形一 / 二级的密集网格尤其明显）。
        val layout = buildExpandedLayout()
        val parent = layout.primarySlots[layout.expandedPrimaryIndex]
        val otherIndex = layout.primarySlots
            .first { it.index != layout.expandedPrimaryIndex }
            .index
        assertFalse(
            "区域内命中别的一级容器不应收起二级",
            shouldCollapse(
                layout = layout,
                hit = QuickWheelHit(QuickWheelHitKind.PRIMARY, otherIndex),
                rawX = parent.centerX,
                rawY = parent.centerY,
            ),
        )
        assertFalse(
            "区域内命中中心不应收起二级",
            shouldCollapse(
                layout = layout,
                hit = QuickWheelHit(QuickWheelHitKind.CENTER, QuickWheelLayoutEngine.CENTER_INDEX),
                rawX = parent.centerX,
                rawY = parent.centerY,
            ),
        )
    }

    @Test
    fun shouldCollapse_otherContainerOutsideRegion_collapses() {
        // 真正离开联合区域后，即便仍命中某个一级容器，也应收起二级。
        val layout = buildExpandedLayout()
        val parent = layout.primarySlots[layout.expandedPrimaryIndex]
        val otherIndex = layout.primarySlots
            .first { it.index != layout.expandedPrimaryIndex }
            .index
        val dirX = parent.centerX - layout.centerX
        val dirY = parent.centerY - layout.centerY
        val dirLen = hypot(dirX, dirY)
        val distance = layout.outerRadiusPx + QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP + 400f
        assertTrue(
            shouldCollapse(
                layout = layout,
                hit = QuickWheelHit(QuickWheelHitKind.PRIMARY, otherIndex),
                rawX = parent.centerX + dirX / dirLen * distance,
                rawY = parent.centerY + dirY / dirLen * distance,
            ),
        )
    }

    @Test
    fun hitTestWhileExpanded_rectSecondaryGapOverPrimaryStaysWithSecondary() {
        // 矩形一 / 二级：二级网格的缝（行列间隙）下面压着一级容器 —— 正是"滑过缝隙被一级抢走"
        // 的真实场景。区域内的这些命中必须并成 NONE（保持二级、不跳高亮）。
        val layout = buildExpandedRectLayout()
        val expanded = layout.expandedPrimaryIndex
        val slack = QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP
        val otherInside = layout.primarySlots.firstOrNull { slot ->
            slot.index != expanded && !QuickWheelLayoutEngine.isOutsideExpandedRegion(
                layout = layout,
                expandedPrimaryIndex = expanded,
                rawX = slot.centerX,
                rawY = slot.centerY,
                slackPx = slack,
            )
        }
        val slot = otherInside ?: error("用例前提：应有别的一级容器落在二级联合区域内")
        // 纯几何命中确实是那个一级容器（旧行为的来源）。
        assertEquals(
            QuickWheelHitKind.PRIMARY,
            QuickWheelLayoutEngine.hitTest(
                layout, QuickWheelShape.RECT, slot.centerX, slot.centerY,
            ).kind,
        )
        // 二级优先 → 区域内并成 NONE，二级保持展开。
        val effective = QuickWheelLayoutEngine.hitTestWhileExpanded(
            layout = layout,
            shape = QuickWheelShape.RECT,
            rawX = slot.centerX,
            rawY = slot.centerY,
            slackPx = slack,
        )
        assertEquals(QuickWheelHitKind.NONE, effective.kind)
        assertFalse(shouldCollapse(layout, effective, slot.centerX, slot.centerY))
    }

    @Test
    fun hitTestWhileExpanded_keepsSecondaryAndParentHits() {
        val layout = buildExpandedLayout()
        val slack = QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP
        val child = layout.secondarySlots.first()
        assertEquals(
            QuickWheelHitKind.SECONDARY,
            QuickWheelLayoutEngine.hitTestWhileExpanded(
                layout, QuickWheelShape.CIRCLE, child.centerX, child.centerY, slack,
            ).kind,
        )
        val parent = layout.primarySlots[layout.expandedPrimaryIndex]
        val parentHit = QuickWheelLayoutEngine.hitTestWhileExpanded(
            layout, QuickWheelShape.CIRCLE, parent.centerX, parent.centerY, slack,
        )
        assertEquals(QuickWheelHitKind.PRIMARY, parentHit.kind)
        assertEquals(layout.expandedPrimaryIndex, parentHit.index)
    }

    @Test
    fun hitTestWhileExpanded_withoutExpandedSecondary_isIdenticalToHitTest() {
        // 未展开二级时必须与纯几何命中完全等价（不影响一级的正常操作）。
        val layout = buildSimpleLayout()
        val slot = layout.primarySlots[0]
        assertEquals(
            QuickWheelLayoutEngine.hitTest(layout, QuickWheelShape.CIRCLE, slot.centerX, slot.centerY),
            QuickWheelLayoutEngine.hitTestWhileExpanded(
                layout, QuickWheelShape.CIRCLE, slot.centerX, slot.centerY,
            ),
        )
    }

    @Test
    fun shouldCollapse_parentOrSecondaryHit_keepsOpen() {
        val layout = buildExpandedLayout()
        val expanded = layout.expandedPrimaryIndex
        assertFalse(
            shouldCollapse(
                layout = layout,
                hit = QuickWheelHit(QuickWheelHitKind.PRIMARY, expanded),
                rawX = layout.centerX,
                rawY = layout.centerY,
            ),
        )
        assertFalse(
            shouldCollapse(
                layout = layout,
                hit = QuickWheelHit(QuickWheelHitKind.SECONDARY, 0),
                rawX = layout.centerX + 1_000f,
                rawY = layout.centerY,
            ),
        )
    }

    @Test
    fun shouldCollapse_withoutExpandedSecondary_neverCollapses() {
        val layout = buildSimpleLayout()
        assertEquals(-1, layout.expandedPrimaryIndex)
        assertFalse(
            shouldCollapse(
                layout = layout,
                hit = QuickWheelHit.NONE,
                rawX = 5f,
                rawY = 5f,
            ),
        )
    }

    @Test
    fun isOutsideExpandedRegion_coversParentAndEverySecondaryContainer() {
        val layout = buildExpandedLayout()
        val expanded = layout.expandedPrimaryIndex
        val parent = layout.primarySlots[expanded]
        // 父容器中心 + 每个二级容器中心都在区域内。
        assertFalse(
            QuickWheelLayoutEngine.isOutsideExpandedRegion(
                layout = layout,
                expandedPrimaryIndex = expanded,
                rawX = parent.centerX,
                rawY = parent.centerY,
            ),
        )
        layout.secondarySlots.forEach { slot ->
            assertFalse(
                "二级容器 ${slot.index} 应在区域内",
                QuickWheelLayoutEngine.isOutsideExpandedRegion(
                    layout = layout,
                    expandedPrimaryIndex = expanded,
                    rawX = slot.centerX,
                    rawY = slot.centerY,
                ),
            )
        }
        // 区域边界贴着容器外沿（凸包）：取 y 最大的那个二级容器，用它自己的 x 做垂直探针，
        // 保证该处边界就是它的下沿。
        val slack = QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP
        val lowest = layout.secondarySlots.maxByOrNull { it.centerY }!!
        val edge = lowest.centerY + lowest.sizePx / 2f
        assertFalse(
            "贴着容器下沿、在余量之内 → 不算离开",
            QuickWheelLayoutEngine.isOutsideExpandedRegion(
                layout = layout,
                expandedPrimaryIndex = expanded,
                rawX = lowest.centerX,
                rawY = edge + slack - 1f,
                slackPx = slack,
            ),
        )
        assertTrue(
            "离开容器下沿超过余量 → 算离开",
            QuickWheelLayoutEngine.isOutsideExpandedRegion(
                layout = layout,
                expandedPrimaryIndex = expanded,
                rawX = lowest.centerX,
                rawY = edge + slack + 1f,
                slackPx = slack,
            ),
        )
    }

    @Test
    fun isOutsideExpandedRegion_aabbCornerIsOutside() {
        // 凸包（"绳子绕一圈"）贴着容器外沿 → 外接矩形的**死角空白**必须算"已离开"。
        // 旧实现用外接矩形，这些角落被算在区域内 → 手指早已离开所有容器却仍不收起二级。
        val layout = buildExpandedLayout()
        val expanded = layout.expandedPrimaryIndex
        val slack = QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP
        val rects = listOf(layout.primarySlots[expanded]) + layout.secondarySlots
        val minX = rects.minOf { it.centerX - it.sizePx / 2f }
        val minY = rects.minOf { it.centerY - it.sizePx / 2f }
        // 取外接矩形的"左上角"再往内 1dp：旧判定认为它在区域内，实际是纯空白。
        // （另外三个角会紧邻一级 / 二级容器，只有这个角能保证"纯空白"这个前提。）
        val cornerX = minX + 1f
        val cornerY = minY + 1f
        assertEquals(
            QuickWheelHitKind.NONE,
            QuickWheelLayoutEngine.hitTest(layout, QuickWheelShape.CIRCLE, cornerX, cornerY).kind,
        )
        assertTrue(
            "外接矩形的死角必须算\"已离开\"（凸包不含它）",
            QuickWheelLayoutEngine.isOutsideExpandedRegion(
                layout = layout,
                expandedPrimaryIndex = expanded,
                rawX = cornerX,
                rawY = cornerY,
                slackPx = slack,
            ),
        )
    }

    @Test
    fun hitTest_gapBetweenAdjacentRingContainersIsUnowned() {
        // 同环相邻容器之间：旧实现靠"半径带 + 角度扇区"兜底把缝按角度判给邻居，
        // 现在统一为"只有手指落入容器边界才命中" → 缝里是 NONE（无主区）。
        // 容器间距取 60dp：相邻中心距 110dp，远超"半尺寸 + 容差"（≈29dp），缝足够明确。
        val gapStyle = defaultStyle.copy(containerGapDp = 60f)
        val layout = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 4,
            secondaryCount = 0,
            expandedPrimaryIndex = -1,
            primaryShape = QuickWheelShape.CIRCLE,
            secondaryShape = QuickWheelShape.CIRCLE,
            primaryStyle = gapStyle,
            secondaryStyle = gapStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )
        val a = layout.primarySlots[0]
        val b = layout.primarySlots[1]
        val hit = QuickWheelLayoutEngine.hitTest(
            layout = layout,
            shape = QuickWheelShape.CIRCLE,
            rawX = (a.centerX + b.centerX) / 2f,
            rawY = (a.centerY + b.centerY) / 2f,
        )
        assertEquals(QuickWheelHitKind.NONE, hit.kind)
    }

    @Test
    fun shouldCollapse_rectSecondaryGapBetweenContainers_keepsSecondaryOpen() {
        // 矩形二级是「网格」：同一行相邻容器之间也是空白
        // （列距 70dp > 2 × 命中半宽「半尺寸 + 容差」≈ 29dp）。
        // 手指在二级容器之间慢慢滑过这类空白时，不能把二级收起。
        val layout = QuickWheelLayoutEngine.buildLayout(
            primaryCount = 3,
            secondaryCount = 7,
            expandedPrimaryIndex = 1,
            primaryShape = QuickWheelShape.RECT,
            secondaryShape = QuickWheelShape.RECT,
            primaryStyle = defaultStyle,
            secondaryStyle = defaultStyle,
            anchorX = 540f,
            anchorY = 960f,
            density = 1f,
            screenWidthPx = 1080f,
        )
        // 默认 5 列：7 个二级容器 = 第 1 行 5 个 + 第 2 行 2 个。
        val row1 = layout.secondarySlots.filter { it.ringIndex == 1 }.sortedBy { it.centerX }
        assertTrue("二级矩形应有第 2 行且至少 2 个容器", row1.size >= 2)
        val x = (row1[0].centerX + row1[1].centerX) / 2f
        val y = row1[0].centerY
        val hit = QuickWheelLayoutEngine.hitTest(layout, QuickWheelShape.RECT, x, y)
        assertEquals(QuickWheelHitKind.NONE, hit.kind)
        assertFalse("矩形二级容器之间的空白不能收起二级", shouldCollapse(layout, hit, x, y))
    }

    // ── 锚点 ────────────────────────────────────────────────

    @Test
    fun clampAnchor_keepsWheelInsideMargins() {
        val (x, y) = QuickWheelLayoutEngine.clampAnchor(
            anchorX = 50f,
            anchorY = 30f,
            radiusPx = 200f,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            marginPx = 10f,
        )
        assertEquals(210f, x, 0.01f)
        assertEquals(210f, y, 0.01f)
    }

    @Test
    fun clampAnchor_fallsBackToCenteredWhenScreenTooSmall() {
        val (x, _) = QuickWheelLayoutEngine.clampAnchor(
            anchorX = 540f,
            anchorY = 500f,
            radiusPx = 600f,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
            marginPx = 10f,
        )
        assertEquals(540f, x, 0.01f)
    }

    @Test
    fun resolveAnchor_centersWhenThereIsRoom() {
        val (x, _) = QuickWheelLayoutEngine.resolveAnchor(
            primaryStyle = defaultStyle,
            density = 1f,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f,
        )
        assertEquals(540f, x, 0.01f)
    }

    @Test
    fun estimatedPrimaryRadiusPx_isPositive() {
        assertTrue(QuickWheelLayoutEngine.estimatedPrimaryRadiusPx(defaultStyle, 1f) > 0f)
    }
}
