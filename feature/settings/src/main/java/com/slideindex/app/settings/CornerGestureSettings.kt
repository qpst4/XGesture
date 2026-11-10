package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.overlay.layout.FvIconShape

data class CornerGestureSettings(
    val enabled: Boolean = false,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
    /** 竖边宽度：贴屏幕竖直边缘的触发区厚度（dp） */
    val verticalEdgeWidthDp: Float = 16f,
    /** 竖边高度：竖直触发区向上延伸的长度（dp） */
    val verticalEdgeHeightDp: Float = 147f,
    /** 横边宽度：水平触发区沿底边延伸的长度（dp） */
    val horizontalEdgeWidthDp: Float = 98f,
    /** 横边高度：贴屏幕水平边缘的触发区厚度（dp） */
    val horizontalEdgeHeightDp: Float = 13f,
    val triggerSlopDp: Float = 40f,
    val hideInLandscape: Boolean = true,
    val landscapePreventFalseTouch: Boolean = true,
    val overrideSystemNav: Boolean = false,
    /**
     * 轮盘内径（dp）：轮盘中间那块空的直径，第一环紧贴它的外缘。
     * 量程固定，不再跟别的设置联动。
     */
    val innerDiameterDp: Float = 320f,
    /**
     * 层与层之间的环间距（dp）：第一环之后每层按这个值往外推。
     * 下限跟着 [bubbleSizeDp] 走（见 [clampRingSpacingDp]），否则径向会叠。
     */
    val ringSpacingDp: Float = 58f,
    val bubbleSizeDp: Float = 24f,
    /** 槽位气泡/图标的裁切形状；默认圆形 = 历史观感。 */
    val slotIconShape: FvIconShape = FvIconShape.CIRCLE,
    /**
     * 轮盘层数（3 / 4 / 5）。第 4 层是槽位 16–24，第 5 层是槽位 25–35。
     * 层数只决定"画几圈"，不改变任何已有环的半径与间距（见 CornerWheelLayout.layerRadiusPx）。
     */
    val wheelLayerCount: Int = CornerRadialMenuCodec.BASE_LAYER_COUNT,
    val cancelOutsideWheel: Boolean = true,
    val progressiveLayers: Boolean = false,
    /** 手指移到新槽位时是否震动（仍受全局触感反馈总开关约束） */
    val slotHapticEnabled: Boolean = true,
    /** 高亮槽位时在屏幕上部显示图标与名称（类似蜂窝启动）。 */
    val showSelectedName: Boolean = true,
    /** 是否在轮盘外侧显示快捷编辑小铅笔按钮。 */
    val showEditButton: Boolean = true,
    val selectedHintIconSizeDp: Int = SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
    /**
     * 轮盘背景：
     * [BACKGROUND_NONE] 透明；
     * [BACKGROUND_BLUR] 实时跨窗口高斯模糊（FLAG_BLUR_BEHIND）；
     * [BACKGROUND_BLACK] 纯色遮罩。
     */
    val backgroundStyle: Int = BACKGROUND_NONE,
    val blurDp: Int = DEFAULT_BLUR_DP,
    val dimPercent: Int = DEFAULT_DIM_PERCENT,
    /** 左右轮盘共用同一套槽位配置。关闭后可分别配置左/右轮盘。 */
    val unifiedSlots: Boolean = true,
    val innerZoneAction: GestureAction = GestureAction.CornerInnerCancel,
    val leftSlots: List<GestureAction> = CornerRadialMenuCodec.defaultLeftSlots(),
    val rightSlots: List<GestureAction> = CornerRadialMenuCodec.defaultRightSlots(),
    val leftSlotSubMenus: List<CornerSlotSubMenuConfig> = CornerSlotSubMenuCodec.defaultSlotSubMenus(),
    val rightSlotSubMenus: List<CornerSlotSubMenuConfig> = CornerSlotSubMenuCodec.defaultSlotSubMenus(),
) {
    /**
     * 当前启用的轮盘层数（3–5）。层数不参与半径计算，所以改动它不会挪动已有各环，
     * 只是决定外层那几圈画不画；槽位索引始终按固定映射。
     */
    val enabledLayerCount: Int
        get() = clampWheelLayerCount(wheelLayerCount)

    fun hasActiveTriggerZone(): Boolean =
        (verticalEdgeWidthDp > 0f && verticalEdgeHeightDp > 0f) ||
            (horizontalEdgeWidthDp > 0f && horizontalEdgeHeightDp > 0f)

    fun isActiveInCurrentOrientation(landscape: Boolean): Boolean {
        if (!enabled) return false
        if (landscape && hideInLandscape) return false
        return leftEnabled || rightEnabled
    }

    companion object {
        /** 第一环紧贴内径外缘之后再多留的余量（dp）。 */
        const val FIRST_RING_MARGIN_DP = 8f

        /** 环间距的默认值与上限（dp）；下限跟着气泡大小走，见 [clampRingSpacingDp]。 */
        const val DEFAULT_RING_SPACING_DP = 58f
        const val MAX_RING_SPACING_DP = 160f

        /** 内径滑块的取值范围（dp）：固定不联动，沿用历史可达上限。 */
        val INNER_DIAMETER_RANGE_DP = 40f..400f

        /** 轮盘层数的取值范围；滑块用它算档位。 */
        val LAYER_COUNT_RANGE = CornerRadialMenuCodec.BASE_LAYER_COUNT..CornerRadialMenuCodec.layerCount()

        const val BACKGROUND_NONE = 0
        const val BACKGROUND_BLUR = 1
        const val BACKGROUND_BLACK = 2

        const val DEFAULT_BLUR_DP = 36
        const val MIN_BLUR_DP = 0
        const val MAX_BLUR_DP = 72

        const val DEFAULT_DIM_PERCENT = 22
        const val MIN_DIM_PERCENT = 0
        const val MAX_DIM_PERCENT = 60

        fun clampVerticalEdgeWidthDp(value: Float): Float = value.coerceIn(0f, 120f)
        fun clampVerticalEdgeHeightDp(value: Float): Float = value.coerceIn(0f, 200f)
        fun clampHorizontalEdgeWidthDp(value: Float): Float = value.coerceIn(0f, 160f)
        fun clampHorizontalEdgeHeightDp(value: Float): Float = value.coerceIn(0f, 160f)
        fun clampTriggerSlopDp(value: Float): Float = value.coerceIn(24f, 96f)
        fun clampWheelLayerCount(value: Int): Int =
            value.coerceIn(CornerRadialMenuCodec.BASE_LAYER_COUNT, CornerRadialMenuCodec.layerCount())
        fun clampInnerDiameterDp(value: Float): Float =
            value.coerceIn(INNER_DIAMETER_RANGE_DP.start, INNER_DIAMETER_RANGE_DP.endInclusive)
        /** 环间距下限 = 气泡直径，低于它相邻两环必然重叠。 */
        fun clampRingSpacingDp(value: Float, bubbleSizeDp: Float): Float =
            value.coerceIn(clampBubbleSizeDp(bubbleSizeDp) * 2f, MAX_RING_SPACING_DP)
        fun clampBubbleSizeDp(value: Float): Float = value.coerceIn(12f, 28f)
        fun clampBlurDp(value: Int): Int = value.coerceIn(MIN_BLUR_DP, MAX_BLUR_DP)
        fun clampDimPercent(value: Int): Int = value.coerceIn(MIN_DIM_PERCENT, MAX_DIM_PERCENT)
        fun clampBackgroundStyle(value: Int): Int = when (value) {
            BACKGROUND_BLUR, BACKGROUND_BLACK -> value
            else -> BACKGROUND_NONE
        }
    }
}
