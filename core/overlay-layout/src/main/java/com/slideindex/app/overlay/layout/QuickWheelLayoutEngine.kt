package com.slideindex.app.overlay.layout

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** 快速轮盘的环形态。 */
enum class QuickWheelShape {
    /** 圆形：同心圆环阶梯式展开。 */
    CIRCLE,

    /** 矩形：按环分行、逐行排列。 */
    RECT,
    ;

    companion object {
        fun fromName(value: String?): QuickWheelShape =
            entries.firstOrNull { it.name == value } ?: CIRCLE
    }
}

/**
 * 轮盘呼出时的展开动画效果。
 *
 * 只影响**视觉**：命中判定与长按计时都不受它影响（长按进度必须严格等于「长按时间」）。
 * 时长由速度百分比换算，见 [QuickWheelLayoutEngine.openAnimationDurationMs]。
 */
enum class QuickWheelOpenAnimation {
    /** 不动画：容器直接出现在最终位置（与旧行为一致）。 */
    NONE,

    /**
     * 从呼出点（轮盘锚点）向外展开：容器位置由锚点插值到各自的就位点，同时等比放大。
     *
     * 中心大圆锚点就是它自己的位置 → 表现为"原地弹出"。
     */
    EXPAND_FROM_CENTER,
    ;

    companion object {
        fun fromName(value: String?): QuickWheelOpenAnimation =
            entries.firstOrNull { it.name == value } ?: EXPAND_FROM_CENTER
    }
}

/**
 * 矩形网格"贴住锚点"的那个角 —— 矩形版的「往哪个方向铺开」。
 *
 * 圆形靠扇区决定方向，矩形没有扇区，改由"网格的哪个角贴着触发点"决定整体落在哪个象限：
 * 例如右下角触发 → [BOTTOM_RIGHT]（网格整体向左上铺开），触发点上移到放不下时自动翻成
 * [TOP_RIGHT]（= 网格第一行最右容器的右上角贴住触发点，整体向左下铺开）。
 */
enum class QuickWheelRectCorner {
    /** 现状：首行水平居中于锚点、整体向下展开（编辑层 / 配置页预览用）。 */
    CENTER_TOP,

    /** 网格左上角贴锚点 → 向右下铺开。 */
    TOP_LEFT,

    /** 网格右上角贴锚点 → 向左下铺开。 */
    TOP_RIGHT,

    /** 网格左下角贴锚点 → 向右上铺开。 */
    BOTTOM_LEFT,

    /** 网格右下角贴锚点 → 向左上铺开。 */
    BOTTOM_RIGHT,
}

/** 单级轮盘样式（一级 / 二级各一套），对应设置页「轮盘样式」的 12 项。 */
data class QuickWheelStyleSpec(
    /** 在图标正下方显示名称（图标与文字整体居中）。 */
    val showLabels: Boolean = false,
    /** 第 1 环距离呼出圆心的初始半径（dp）。 */
    val initialRadiusDp: Float = QuickWheelLayoutEngine.DEFAULT_INITIAL_RADIUS_DP,
    /**
     * 环上相邻容器之间的**实际净间隙**（dp）。
     *
     * 排布步距 = [containerSizeDp] + 本值，因此 `0` 表示相邻容器**紧挨着**；
     * 容器尺寸调小时，同样的本值会得到同样宽度的缝（而不是被固定基准撑开）。
     */
    val containerGapDp: Float = QuickWheelLayoutEngine.DEFAULT_CONTAINER_GAP_DP,
    /** 超出单环容量时，相邻环层之间的间隙（dp）。 */
    val ringGapDp: Float = QuickWheelLayoutEngine.DEFAULT_RING_GAP_DP,
    /**
     * 单个容器的边长（dp）。
     *
     * 它同时决定排布步距（步距 = 本值 + [containerGapDp]）与单环容量：调小容器会让整个轮盘
     * **更紧凑**（容器挨得更近、单环能放更多、环间距也按更小的尺寸外扩）。
     */
    val containerSizeDp: Float = QuickWheelLayoutEngine.DEFAULT_CONTAINER_SIZE_DP,
    /** 容器四角圆角半径（dp）。 */
    val containerCornerDp: Float = QuickWheelLayoutEngine.DEFAULT_CONTAINER_CORNER_DP,
    /** 「图标大小」基准（百分比）：为内置图标与文字标签提供基准大小 = 容器尺寸 × 本比例。 */
    val iconSizePercent: Int = QuickWheelLayoutEngine.DEFAULT_ICON_SIZE_PERCENT,
    /** 文字标签大小系数（百分比）：字号 = 基准 × 系数。 */
    val labelSizeFactorPercent: Int = QuickWheelLayoutEngine.DEFAULT_LABEL_SIZE_FACTOR_PERCENT,
    /**
     * 图标大小系数（百分比）。
     *
     * - 内置图标（矢量图标库 / 动作回退图标 / 文字图标）：实际 = 基准 × 系数；
     * - 已安装应用图标 / 图库图片：基准恒为容器的 100%，实际 = 容器 × 系数。
     */
    val builtinIconSizePercent: Int = QuickWheelLayoutEngine.DEFAULT_BUILTIN_ICON_SIZE_PERCENT,
    /** 横向偏移（dp）：正值向屏内推入，负值贴边收缩。 */
    val offsetXDp: Float = 0f,
    /** 纵向偏移（dp）：正值向屏内推入，负值贴边收缩。 */
    val offsetYDp: Float = 0f,
    /**
     * 矩形形态专用：容器**列数**。
     *
     * - 圆形形态不使用本字段（圆形由 [initialRadiusDp] / [ringGapDp] 决定环半径与层次）；
     * - 矩形形态不使用 [initialRadiusDp] / [ringGapDp]：第 0 行紧贴呼出点，每行固定 [columnCount] 个，
     *   行 / 列间距都用 [containerGapDp]。
     */
    val columnCount: Int = QuickWheelLayoutEngine.DEFAULT_COLUMN_COUNT,
    /**
     * **应用图标 / 图库图片**这类"用户提供的位图"的容器是否全透明（不铺底色、不裁切）。
     *
     * - `false`（默认）：所有容器都有底板，观感统一（原行为）；
     * - `true`：这些容器不铺任何底色，图标保持原样 —— 拟物风格图标包是异形 + 透明边角，
     *   铺方形底板会从轮廓外露出底色。
     *
     * 内置矢量图标（图标库 / 内置动作 / **文字图标** / 名称首字母）**不受本开关影响，始终铺底板**：
     * 细线图标与文字没有底板，在壁纸上会读不清。
     */
    val transparentUserIcons: Boolean = false,
    )

/** 单个容器的几何位置（屏幕坐标，px）。 */
data class QuickWheelPlacedSlot(
    val index: Int,
    val centerX: Float,
    val centerY: Float,
    val sizePx: Float,
    val cornerPx: Float,
    val ringIndex: Int,
    val indexInRing: Int,
    val startAngleDeg: Float,
    val endAngleDeg: Float,
    val minRadiusPx: Float,
    val maxRadiusPx: Float,
)

data class QuickWheelLayout(
    /** 中心容器的圆心（跟随横/纵向偏移；偏移为 0 时即呼出原点）。 */
    val centerX: Float,
    val centerY: Float,
    val centerSizePx: Float,
    val centerCornerPx: Float,
    val primarySlots: List<QuickWheelPlacedSlot>,
    /** 已展开的二级子盘；空表示当前未展开二级。 */
    val secondarySlots: List<QuickWheelPlacedSlot>,
    /** 展开二级的那个一级容器索引；-1 表示无。 */
    val expandedPrimaryIndex: Int,
    /** 一级容器实际占用的半径上界（px），供浮层判断是否越界。 */
    val outerRadiusPx: Float,
    /** 本次布局真正生效的一级扇区掩码（自适应求解结果；非自适应时 = 调用方传入值）。 */
    val sectorMask: Int = QuickWheelLayoutEngine.SECTOR_ALL,
    /** 本次布局真正生效的一级矩形基准角（自适应求解结果）。 */
    val rectCorner: QuickWheelRectCorner = QuickWheelRectCorner.CENTER_TOP,
)

/**
 * 运行时自适应输入：给出屏幕可用区域（含边距），由引擎自行决定
 * 一级 / 二级该用哪个扇区集合（圆形）或网格基准角（矩形），以保证所有容器都落在屏幕内。
 *
 * 配置页预览 / 编辑层传 null（按配置里的固定扇区 / [QuickWheelRectCorner.CENTER_TOP] 布局）。
 */
data class QuickWheelAdaptiveScreen(
    val widthPx: Float,
    val heightPx: Float,
    val marginPx: Float,
)

enum class QuickWheelHitKind { NONE, CENTER, PRIMARY, SECONDARY }

data class QuickWheelHit(val kind: QuickWheelHitKind, val index: Int) {
    companion object {
        val NONE = QuickWheelHit(QuickWheelHitKind.NONE, -1)
    }
}

/**
 * 「快捷轮盘」几何引擎。
 *
 * 语义（对齐参考实现）：
 * - **中心容器**固定在呼出圆心；
 * - **一级容器**沿半径 `一级.初始半径` 的环分布，单环放不下时按「容器尺寸 + 环间距」逐圈外扩；
 * - **长按某个一级容器**时，以该容器的圆心为新圆心，按 `二级.初始半径` 展开它自己的**二级子盘**；
 * - 一级 / 二级各用一套 [QuickWheelStyleSpec]；
 * - 圆形张开整 360°，矩形按环分行（每行固定 [QuickWheelStyleSpec.columnCount] 列）；
 * - 横向 / 纵向偏移为屏幕坐标位移，正值向屏内推入。
 *
 * 不依赖 `:feature:settings`，只接收纯数值参数，便于单测。
 */
object QuickWheelLayoutEngine {
    const val DEFAULT_INITIAL_RADIUS_DP = 100f
    const val DEFAULT_CONTAINER_GAP_DP = 10f

    const val DEFAULT_RING_GAP_DP = 15f
    const val DEFAULT_CONTAINER_SIZE_DP = 42f
    const val DEFAULT_CONTAINER_CORNER_DP = 14f
    const val DEFAULT_ICON_SIZE_PERCENT = 50
    const val DEFAULT_LABEL_SIZE_FACTOR_PERCENT = 38
    const val DEFAULT_BUILTIN_ICON_SIZE_PERCENT = 100

    const val MIN_INITIAL_RADIUS_DP = 60f
    const val MAX_INITIAL_RADIUS_DP = 260f

    /**
     * **二级**初始半径的下限（dp）—— 与一级**不同**。
     *
     * 一级的 60 是按"中心大圆不得压住第一环容器"定的（见 `layoutCenter` 与相关单测）；
     * 二级的锚点是**父容器中心**、且二级不画中心大圆，唯一约束是"别和父容器重叠"：
     * `initialRadius ≥ 父半宽 + 二级容器半宽`（默认 44/2 + 44/2 = 44），这里留 2dp 余量。
     *
     * 沿用一级的 60 会让二级环与父容器之间留下 60 − 22 − 22 = 16dp 的径向空隙，
     * 且该径向距离**只有**本参数能调（`containerGapDp` 是沿环的缝、`ringGapDp` 是环间距离）。
     */
    const val MIN_SECONDARY_INITIAL_RADIUS_DP = 46f
    const val MIN_CONTAINER_GAP_DP = 0f
    const val MAX_CONTAINER_GAP_DP = 160f
    const val MIN_RING_GAP_DP = 0f
    const val MAX_RING_GAP_DP = 160f
    const val MIN_CONTAINER_SIZE_DP = 32f
    const val MAX_CONTAINER_SIZE_DP = 88f
    const val MIN_CONTAINER_CORNER_DP = 0f
    const val MAX_CONTAINER_CORNER_DP = 44f
    const val MIN_PERCENT = 20
    const val MAX_PERCENT = 100
    const val MIN_OFFSET_DP = -200f
    const val MAX_OFFSET_DP = 200f

    /** 矩形形态：容器列数（范围 + 默认值）。 */
    const val MIN_COLUMN_COUNT = 2
    const val MAX_COLUMN_COUNT = 10
    const val DEFAULT_COLUMN_COUNT = 5

    /** 最大环数上限，防止极小半径导致的死循环。 */
    const val MAX_RINGS = 8

    /**
     * 矩形网格的最大行数。
     *
     * 矩形每行容量 = 列数（2–10 个），和圆形的"环"不是一回事：共用 [MAX_RINGS] 会让
     * 容器数超过 8×列数 的部分（以及编辑层末尾的「+」）被静默丢掉——表现就是
     * "切到矩形后加号消失、点空白占位没反应"。48 个容器的极限只需 24 行，
     * 64 行仅是防死循环的硬上限。
     */
    const val MAX_RECT_ROWS = 64

    /**
     * 自适应求解时容器离屏幕边缘预留的边距（dp）。
     *
     * 运行时浮层与配置页预览 / 编辑层共用同一数值，避免"预览里刚好贴边、真机却出屏"。
     */
    const val ADAPTIVE_MARGIN_DP = 10f

    // 中心大圆**没有**"相对容器尺寸的放大倍数"这种常量：它的尺寸由 [layoutCenter] 按
    // 「初始半径 − 容器半宽 − 环间距」推导（下限 0.75 × 容器尺寸、上界 = 第一环容器内缘），
    // 与初始半径 / 容器尺寸 / 环间距联动，恒为正圆。
    // （旧的 CENTER_SIZE_SCALE = 2f 从未被任何代码使用，已删除以免误导。）

    /** 长按一级容器展开二级的判定阈值（ms）。 */
    const val SECOND_LEVEL_LONG_PRESS_MS = 400L

    /**
     * 「长按时间」可配置范围与默认值（ms）。
     *
     * 同时作用于：一级容器的长按动作判定、长按展开二级轮盘的判定、二级容器的长按动作判定。
     */
    const val MIN_LONG_PRESS_MS = 200
    const val MAX_LONG_PRESS_MS = 1500
    const val DEFAULT_LONG_PRESS_MS = SECOND_LEVEL_LONG_PRESS_MS.toInt()

    fun clampLongPressMs(value: Int): Int = value.coerceIn(MIN_LONG_PRESS_MS, MAX_LONG_PRESS_MS)

    // ── 呼出时的背景（容器以外区域）模糊 / 遮罩 ────────────────
    /**
     * 轮盘呼出时**容器以外区域**的背景模糊强度（dp，0 = 不模糊）。
     *
     * 走系统跨窗口模糊（`FLAG_BLUR_BEHIND`）；系统关闭"跨窗口模糊"时自动退化为不模糊。
     * 容器本身**始终不透明**，不受这两个参数影响。
     */
    const val MIN_BACKDROP_BLUR_DP = 0
    const val MAX_BACKDROP_BLUR_DP = 72
    const val DEFAULT_BACKDROP_BLUR_DP = 0

    /** 轮盘呼出时**容器以外区域**的黑色遮罩浓度（%，0 = 完全透出原屏幕、60 = 最重）。 */
    const val MIN_BACKDROP_DIM_PERCENT = 0
    const val MAX_BACKDROP_DIM_PERCENT = 60
    const val DEFAULT_BACKDROP_DIM_PERCENT = 0

    fun clampBackdropBlurDp(value: Int): Int =
        value.coerceIn(MIN_BACKDROP_BLUR_DP, MAX_BACKDROP_BLUR_DP)

    fun clampBackdropDimPercent(value: Int): Int =
        value.coerceIn(MIN_BACKDROP_DIM_PERCENT, MAX_BACKDROP_DIM_PERCENT)

    // ── 呼出动画 ────────────────────────────────────────
    /** 呼出动画默认效果：新轮盘默认「从中心向外展开」。 */
    val DEFAULT_OPEN_ANIMATION = QuickWheelOpenAnimation.EXPAND_FROM_CENTER

    /**
     * 动画速度（%，相对 [BASE_OPEN_ANIMATION_MS] 基准；默认 150%，上限 300%）。
     *
     * 只作用于**展开那一段**：`时长 = 基准 / (速度 / 100)`，所以 300% ≈ 三分之一时长、50% = 两倍。
     * 基准 220ms 只是一个刻度参考：默认 150% ≈ 147ms（"再快一点"的默认手感）。
     * ⚠️ 不影响长按进度（那必须严格等于「长按时间」），也不影响编辑层的让位动画。
     */
    const val MIN_ANIMATION_SPEED_PERCENT = 50
    const val MAX_ANIMATION_SPEED_PERCENT = 300
    const val DEFAULT_ANIMATION_SPEED_PERCENT = 150

    /** 速度基准时长（ms）：100% → 220ms、默认 150% → ≈147ms、300% → 73ms、50% → 440ms。 */
    const val BASE_OPEN_ANIMATION_MS = 220

    fun clampAnimationSpeedPercent(value: Int): Int =
        value.coerceIn(MIN_ANIMATION_SPEED_PERCENT, MAX_ANIMATION_SPEED_PERCENT)

    /** 由速度百分比换算展开时长（ms）。 */
    fun openAnimationDurationMs(speedPercent: Int): Int =
        (BASE_OPEN_ANIMATION_MS * 100f / clampAnimationSpeedPercent(speedPercent))
            .roundToInt()
            .coerceAtLeast(1)

    /**
     * 初始半径夹紧。
     *
     * [min] 默认取**全局最低** [MIN_SECONDARY_INITIAL_RADIUS_DP]：引擎内部的布局 / 求解
     * （[layoutRing] / [solveSectorMaskForAnchor] …）拿不到"这是哪一级"，只做防御性夹紧；
     * 真正的**按级下限**由数据层显式传入（`QuickWheel.normalized()`：一级 [MIN_INITIAL_RADIUS_DP]、
     * 二级 [MIN_SECONDARY_INITIAL_RADIUS_DP]）。
     */
    fun clampInitialRadiusDp(value: Float, min: Float = MIN_SECONDARY_INITIAL_RADIUS_DP): Float =
        value.coerceIn(min, MAX_INITIAL_RADIUS_DP)

    fun clampContainerGapDp(value: Float): Float =
        value.coerceIn(MIN_CONTAINER_GAP_DP, MAX_CONTAINER_GAP_DP)

    fun clampRingGapDp(value: Float): Float =
        value.coerceIn(MIN_RING_GAP_DP, MAX_RING_GAP_DP)

    fun clampContainerSizeDp(value: Float): Float =
        value.coerceIn(MIN_CONTAINER_SIZE_DP, MAX_CONTAINER_SIZE_DP)

    fun clampContainerCornerDp(value: Float): Float =
        value.coerceIn(MIN_CONTAINER_CORNER_DP, MAX_CONTAINER_CORNER_DP)

    fun clampPercent(value: Int): Int = value.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun clampOffsetDp(value: Float): Float = value.coerceIn(MIN_OFFSET_DP, MAX_OFFSET_DP)

    /**
     * 规范化一套样式。
     *
     * @param initialRadiusMin 初始半径下限：**数据层按级别显式传**（一级 [MIN_INITIAL_RADIUS_DP]、
     *   二级 [MIN_SECONDARY_INITIAL_RADIUS_DP]）；不传 = 引擎内部布局 / 求解用的全局最低值。
     *
     * ⚠️ 这里刻意**不**用"是不是二级"这种布尔开关：布局 / 求解路径（[layoutRing] /
     * [solveSectorMaskForAnchor] …）拿不到级别、只能用默认值 —— 一旦默认值是一级的 60，
     * 二级环就会被静默顶到 60（"二级环离父容器永远 16dp"就是这么来的）。
     * 默认值设成全局最低后，漏传最多是"夹得松一点"，不会把布局算错。
     */
    fun normalize(
        style: QuickWheelStyleSpec,
        initialRadiusMin: Float = MIN_SECONDARY_INITIAL_RADIUS_DP,
    ): QuickWheelStyleSpec = style.copy(
        showLabels = style.showLabels,
        initialRadiusDp = clampInitialRadiusDp(style.initialRadiusDp, initialRadiusMin),
        containerGapDp = clampContainerGapDp(style.containerGapDp),
        ringGapDp = clampRingGapDp(style.ringGapDp),
        containerSizeDp = clampContainerSizeDp(style.containerSizeDp),
        containerCornerDp = clampContainerCornerDp(style.containerCornerDp),
        iconSizePercent = clampPercent(style.iconSizePercent),
        labelSizeFactorPercent = clampPercent(style.labelSizeFactorPercent),
        builtinIconSizePercent = clampPercent(style.builtinIconSizePercent),
        offsetXDp = clampOffsetDp(style.offsetXDp),
        offsetYDp = clampOffsetDp(style.offsetYDp),
        columnCount = style.columnCount.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT),
    )

    // ── 扇区（一级轮盘角度范围）────────────────────────────
    // 4 个 90° 扇区，从正上方（-90°）顺时针编号 0..3。
    // 全选（默认）时可用角度 = 360°，布点结果与"整圆"完全一致，保证向后兼容。

    const val SECTOR_COUNT = 4
    const val SECTOR_ALL = 0b1111
    const val SECTOR_ANGLE_DEG = 90f
    const val SECTOR_START_DEG = -90f

    fun isSectorSelected(sectorMask: Int, index: Int): Boolean =
        index in 0 until SECTOR_COUNT && (sectorMask shr index) and 1 == 1

    /**
     * 已选扇区个数（至少 1，避免全不选时轮盘塌缩）。
     *
     * 走 [normalizeSectorMask]：对角两扇区 / 三扇区这些无意义的组合已被并成整圆，
     * 这里要是还数原始位数，"可用角度"文案就会写 270°，而轮盘实际画的是整圆。
     */
    fun selectedSectorCount(sectorMask: Int): Int =
        Integer.bitCount(normalizeSectorMask(sectorMask)).coerceAtLeast(1)

    /** 可用角度总跨度（度）。 */
    fun sectorSpanDeg(sectorMask: Int): Int = selectedSectorCount(sectorMask) * SECTOR_ANGLE_DEG.toInt()

    /** 可用角度总跨度（弧度）。 */
    fun sectorAngleRad(sectorMask: Int): Double = Math.toRadians(sectorSpanDeg(sectorMask).toDouble())

    /** 切换某个扇区；不允许把最后一个选中扇区也取消。 */
    fun toggleSector(sectorMask: Int, index: Int): Int {
        if (index !in 0 until SECTOR_COUNT) return sectorMask
        val next = sectorMask xor (1 shl index)
        return if (next and SECTOR_ALL == 0) sectorMask else next and SECTOR_ALL
    }

    /**
     * **只做位域裁剪**（空 ⇒ 全选），保留用户点选的原样，**不做形态归一化**。
     *
     * 设置 / 编解码等**存储路径**用它：用户点了几段就存几段，扇区环才能照原样高亮，
     * 也才退得回半圆。渲染路径（布局 / 预览锚点 / 角度）才用 [normalizeSectorMask]。
     */
    fun clampSectorMask(sectorMask: Int): Int =
        if (sectorMask and SECTOR_ALL == 0) SECTOR_ALL else sectorMask and SECTOR_ALL

    /**
     * 归一到**有意义的**扇区掩码。
     *
     * 只有三种形态成立：**单扇区 90°**、**相邻两扇区 180°（半圆）**、**整圆 360°**。
     * 因此：
     * - 空 ⇒ 整圆（全选，与旧行为一致）；
     * - **对角两扇区**（如 `0b0101`）⇒ 整圆：两段相反的弧拼不出连续可用区，
     *   合方向还会互相抵消，画出来既不是半圆也不是整圆；
     * - **三扇区**（如 `0b0111`）⇒ 整圆：用户点满三个，实际想要的就是整圆；
     * - 其余按位裁到 4 位后原样返回。
     *
     * ⚠️ 这里只影响"怎么画 / 怎么算角度 / 预览锚点"，**不改用户点的原始掩码**（设置 / 编解码
     * 走 [clampSectorMask]，照旧存原始位）。否则整圆状态下取消一个扇区 → 剩三个 → 又被归一成
     * 整圆，就永远退不回半圆；扇区环也会点不出三扇区、照不亮对角两扇区。
     * 扇区环的高亮按原始掩码画（见 `QuickWheelSectorRing`）。
     */
    fun normalizeSectorMask(sectorMask: Int): Int {
        val mask = sectorMask and SECTOR_ALL
        return when (Integer.bitCount(mask)) {
            0 -> SECTOR_ALL
            1 -> mask
            2 -> {
                val indices = (0 until SECTOR_COUNT).filter { isSectorSelected(mask, it) }
                if (areSectorsAdjacent(indices[0], indices[1])) mask else SECTOR_ALL
            }
            // 3 个 / 4 个：整圆。
            else -> SECTOR_ALL
        }
    }

    /**
     * 新轮盘的默认扇区：**左半圆**（扇区 2+3）。
     *
     * 绝大多数场景是从屏幕**右侧边缘**的手势呼出，自适应求解最常给出的正是"左半圆"，
     * 所以把它作为进入新轮盘设置时的默认预览形态（运行时仍由触发位置自适应决定）。
     */
    const val DEFAULT_SECTOR_MASK = 0b1100

    /** 两个扇区是否相邻（含 3 ↔ 0 的环绕）。 */
    fun areSectorsAdjacent(a: Int, b: Int): Boolean =
        a != b && ((a + 1) % SECTOR_COUNT == b || (b + 1) % SECTOR_COUNT == a)

    /**
     * 把"虚拟角度轴"上的角度映射回真实角度。
     *
     * 虚拟轴 = 把所有选中扇区首尾相接连成的一条连续轴，长度 = 已选扇区总跨度；
     * 因此同一段角度在不同扇区组合下都能均匀容纳容器，且全选时映射为恒等。
     */
    fun mapVirtualAngleDeg(sectorMask: Int, virtualDeg: Float): Float {
        val mask = normalizeSectorMask(sectorMask)
        var remaining = virtualDeg.coerceAtLeast(0f)
        for (index in 0 until SECTOR_COUNT) {
            if (!isSectorSelected(mask, index)) continue
            if (remaining < SECTOR_ANGLE_DEG) {
                return SECTOR_START_DEG + SECTOR_ANGLE_DEG * index + remaining
            }
            remaining -= SECTOR_ANGLE_DEG
        }
        return SECTOR_START_DEG + SECTOR_ANGLE_DEG * SECTOR_COUNT
    }

    /** 掩码里恰好 [count] 个选中扇区的全部组合（掩码升序；count 为 0 时返回空）。 */
    fun sectorMasksOfSize(count: Int): List<Int> {
        val safe = count.coerceIn(0, SECTOR_COUNT)
        if (safe == 0) return emptyList()
        val result = ArrayList<Int>(SECTOR_COUNT)
        for (mask in 0..SECTOR_ALL) {
            if (Integer.bitCount(mask) == safe) result += mask
        }
        return result
    }

    /** 选中弧段的"合力方向"（单位向量）；方向退化（整圆 / 两个相对扇区）时返回 (0, 0)。 */
    private fun sectorCentroidDir(sectorMask: Int): Pair<Float, Float> {
        val mask = normalizeSectorMask(sectorMask)
        var sumX = 0.0
        var sumY = 0.0
        for (index in 0 until SECTOR_COUNT) {
            if (!isSectorSelected(mask, index)) continue
            val midDeg = SECTOR_START_DEG + SECTOR_ANGLE_DEG * index + SECTOR_ANGLE_DEG / 2f
            val rad = Math.toRadians(midDeg.toDouble())
            sumX += cos(rad)
            sumY += sin(rad)
        }
        val length = hypot(sumX, sumY)
        if (length < 1e-3) return 0f to 0f
        return (sumX / length).toFloat() to (sumY / length).toFloat()
    }

    /** 容器外沿是否都落在屏幕内（含 [marginPx] 边距）。 */
    fun isSlotInsideScreen(
        slot: QuickWheelPlacedSlot,
        screenWidthPx: Float,
        screenHeightPx: Float,
        marginPx: Float,
    ): Boolean {
        val half = slot.sizePx / 2f
        return slot.centerX - half >= marginPx &&
            slot.centerX + half <= screenWidthPx - marginPx &&
            slot.centerY - half >= marginPx &&
            slot.centerY + half <= screenHeightPx - marginPx
    }

    /** 越界程度（0 = 全部在屏内，越小越好）：全部候选都放不下时用它挑兜底方案。 */
    fun slotsOverflow(
        slots: List<QuickWheelPlacedSlot>,
        screenWidthPx: Float,
        screenHeightPx: Float,
        marginPx: Float,
    ): Float {
        var worst = 0f
        slots.forEach { slot ->
            val half = slot.sizePx / 2f
            val over = maxOf(
                marginPx - (slot.centerX - half),
                (slot.centerX + half) - (screenWidthPx - marginPx),
                marginPx - (slot.centerY - half),
                (slot.centerY + half) - (screenHeightPx - marginPx),
            )
            if (over > worst) worst = over
        }
        return worst
    }

    /** 同跨度候选按"是否正对屏幕内部"排序：越朝向屏幕内侧越优先。 */
    private fun orderMasksByOpenDirection(
        masks: List<Int>,
        anchorX: Float,
        anchorY: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
    ): List<Int> {
        val openX = screenWidthPx / 2f - anchorX
        val openY = screenHeightPx / 2f - anchorY
        val length = hypot(openX.toDouble(), openY.toDouble())
        if (length < 1e-3) return masks
        val ux = (openX / length).toFloat()
        val uy = (openY / length).toFloat()
        return masks.sortedByDescending { mask ->
            val (cx, cy) = sectorCentroidDir(mask)
            cx * ux + cy * uy
        }
    }

    /**
     * 正向求解（真实调用）：以 ([anchorX], [anchorY]) 为锚点挑一个扇区集合，
     * 让一级圆形容器**全部落在屏幕内**。
     *
     * 优先"跨度尽可能大"——跨度越大 → 单环容量越大 → 环数越少 → 径向占用越小，越容易全显示；
     * 同跨度内按"朝向屏幕内部"排序。例如右边缘中点 → 整圆放不下 → 左半圆；
     * 右下角 → 只剩左上的 90°。全部候选都放不下时返回越界最小的兜底。
     */
    fun solveSectorMaskForAnchor(
        anchorX: Float,
        anchorY: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        marginPx: Float,
        style: QuickWheelStyleSpec,
        slotCount: Int,
        density: Float,
    ): Int {
        if (slotCount <= 0) return SECTOR_ALL
        var fallbackMask = SECTOR_ALL
        var fallbackOverflow = Float.MAX_VALUE
        for (count in SECTOR_COUNT downTo 1) {
            val candidates = orderMasksByOpenDirection(
                // 只评测**有意义**的掩码（整圆 / 相邻半圆 / 90°）。三扇区和对角两扇区会被
                // normalizeSectorMask 并成整圆，跟 count = 4 的那一轮完全等价，评测它们只是重复；
                // 更重要的是别让求解结果落回这种"画不出对应形态"的掩码。
                masks = sectorMasksOfSize(count).filter { normalizeSectorMask(it) == it },
                anchorX = anchorX,
                anchorY = anchorY,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
            candidates.forEach { mask ->
                val slots = layoutRing(
                    slotCount = slotCount,
                    shape = QuickWheelShape.CIRCLE,
                    style = style,
                    anchorX = anchorX,
                    anchorY = anchorY,
                    density = density,
                    availableWidthPx = screenWidthPx,
                    sectorMask = mask,
                )
                val overflow = slotsOverflow(slots, screenWidthPx, screenHeightPx, marginPx)
                if (overflow <= 0f) return mask
                if (overflow < fallbackOverflow) {
                    fallbackOverflow = overflow
                    fallbackMask = mask
                }
            }
        }
        return fallbackMask
    }

    /**
     * 正向求解（真实调用，矩形）：挑一个"网格基准角"，让矩形网格全部落在屏幕内。
     *
     * 顺序 = 先按两轴余量定方向（余量大的一侧优先，平局取"上"与"左"），逐个试排；
     * 例如右下角触发 → 网格右下角贴点（向左上铺开），触发点上移到放不下 → 翻成右上角贴点
     * （= 第一行最右容器的右上角，向左下铺开）。全都不行时返回越界最小的兜底。
     */
    fun solveRectCornerForAnchor(
        anchorX: Float,
        anchorY: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        marginPx: Float,
        style: QuickWheelStyleSpec,
        slotCount: Int,
        density: Float,
    ): QuickWheelRectCorner {
        if (slotCount <= 0) return QuickWheelRectCorner.CENTER_TOP
        val upFirst = anchorY >= screenHeightPx - anchorY
        val leftFirst = anchorX >= screenWidthPx - anchorX
        val verticalOrder = if (upFirst) listOf(true, false) else listOf(false, true)
        val horizontalOrder = if (leftFirst) listOf(true, false) else listOf(false, true)
        val ordered = ArrayList<QuickWheelRectCorner>(4)
        verticalOrder.forEach { up ->
            horizontalOrder.forEach { left ->
                ordered += when {
                    up && left -> QuickWheelRectCorner.BOTTOM_RIGHT
                    up -> QuickWheelRectCorner.BOTTOM_LEFT
                    left -> QuickWheelRectCorner.TOP_RIGHT
                    else -> QuickWheelRectCorner.TOP_LEFT
                }
            }
        }
        var fallback = ordered.first()
        var fallbackOverflow = Float.MAX_VALUE
        ordered.forEach { corner ->
            val slots = layoutRing(
                slotCount = slotCount,
                shape = QuickWheelShape.RECT,
                style = style,
                anchorX = anchorX,
                anchorY = anchorY,
                density = density,
                availableWidthPx = screenWidthPx,
                rectCorner = corner,
            )
            val overflow = slotsOverflow(slots, screenWidthPx, screenHeightPx, marginPx)
            if (overflow <= 0f) return corner
            if (overflow < fallbackOverflow) {
                fallbackOverflow = overflow
                fallback = corner
            }
        }
        return fallback
    }

    /**
     * 反向求解（配置页"预览测试"用）：给定扇区集合，返回它**理论上会出现在屏幕哪里**的锚点。
     *
     * 规则：用选中弧段的合力方向取反 → 锚点落到该方向的屏幕边界。
     * 方向接近坐标轴时落到"边中点"（左半圆 → 右边缘中点），呈对角时落到"角"
     * （左下 90° → 右上角）；整圆或方向退化（如 0+2 两个相对扇区）时退回屏幕中心。
     */
    fun theoreticalAnchorForMask(
        sectorMask: Int,
        screenWidthPx: Float,
        screenHeightPx: Float,
    ): Pair<Float, Float> {
        val mask = normalizeSectorMask(sectorMask)
        val centerX = screenWidthPx / 2f
        val centerY = screenHeightPx / 2f
        if (mask == SECTOR_ALL) return centerX to centerY
        val (dirX, dirY) = sectorCentroidDir(mask)
        if (dirX == 0f && dirY == 0f) return centerX to centerY
        // 反方向：选中弧段朝屏幕内，锚点就退到它对面的屏幕边界上。
        val outX = -dirX
        val outY = -dirY
        val anchorX = when {
            outX > 0.25f -> screenWidthPx
            outX < -0.25f -> 0f
            else -> centerX
        }
        val anchorY = when {
            outY > 0.25f -> screenHeightPx
            outY < -0.25f -> 0f
            else -> centerY
        }
        return anchorX to anchorY
    }

    /**
     * 「调二级外观参数」时实时预览用的父容器格位。
     *
     * 规则：**优先最后一个没有子容器的格位**（这样不会在预览里掩盖任何真实二级，也不会让人
     * 误以为某个容器凭空多了几个子容器）；全都有子容器时退回最后一个格位；
     * 没有格位时返回 -1（此时不显示样本二级）。
     */
    fun pickPreviewSecondaryParentIndex(hasSubSlots: List<Boolean>): Int {
        if (hasSubSlots.isEmpty()) return -1
        val empty = hasSubSlots.indexOfLast { !it }
        return if (empty >= 0) empty else hasSubSlots.lastIndex
    }

    /**
     * 「贴边」锚点：把呼出点投影到**最近的一条屏幕边**（保留沿边坐标）。
     *
     * 供动作绑定处的「贴边」模式使用：圆心恒落在边线上，轮盘像"从边缘长出来"。
     * 四条边距离相同时按 **左 → 右 → 上 → 下** 的顺序取（确定性）。
     *
     * ⚠️ 投影后整圆必然越界 → 运行时的自适应求解会自动把扇区收窄成半圆 / 90°，
     * 因此调用方只需换锚点，不必动求解器。
     */
    fun projectAnchorToEdge(
        anchorX: Float,
        anchorY: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
    ): Pair<Float, Float> {
        val toLeft = anchorX
        val toRight = screenWidthPx - anchorX
        val toTop = anchorY
        val toBottom = screenHeightPx - anchorY
        val nearest = minOf(toLeft, toRight, toTop, toBottom)
        val x = anchorX.coerceIn(0f, screenWidthPx)
        val y = anchorY.coerceIn(0f, screenHeightPx)
        return when (nearest) {
            toLeft -> 0f to y
            toRight -> screenWidthPx to y
            toTop -> x to 0f
            else -> x to screenHeightPx
        }
    }

    fun ringRadiusPx(
        initialRadiusPx: Float,
        containerSizePx: Float,
        ringGapPx: Float,
        ringIndex: Int,
    ): Float = initialRadiusPx + ringIndex.coerceAtLeast(0) * (containerSizePx + ringGapPx)

    /**
     * 某一环 / 行中心到锚点的距离（px）。
     *
     * - **圆形**：由 [initialRadiusPx] 与 [ringGapPx] 决定，即「初始半径 + 层次 × (容器尺寸 + 环间距)」；
     * - **矩形**：不使用初始半径 / 环间距——第 0 行紧贴呼出点（仅留一个 [containerGapPx]），
     *   之后逐行以「容器尺寸 + 容器间距」下移，与中心容器「一行高 + 间距」的规则对齐。
     */
    fun ringRowRadiusPx(
        shape: QuickWheelShape,
        initialRadiusPx: Float,
        containerSizePx: Float,
        containerGapPx: Float,
        ringGapPx: Float,
        ringIndex: Int,
    ): Float = when (shape) {
        QuickWheelShape.CIRCLE ->
            ringRadiusPx(initialRadiusPx, containerSizePx, ringGapPx, ringIndex)

        QuickWheelShape.RECT ->
            containerSizePx / 2f + containerGapPx +
                ringIndex.coerceAtLeast(0) * (containerSizePx + containerGapPx)
    }

    /** 单环容量：圆形受 [sectorMask] 限定可用角度；矩形固定为 [columnCount] 列。 */
    fun ringCapacity(
        radiusPx: Float,
        containerSizePx: Float,
        containerGapPx: Float,
        shape: QuickWheelShape,
        availableWidthPx: Float,
        sectorMask: Int = SECTOR_ALL,
        columnCount: Int = DEFAULT_COLUMN_COUNT,
    ): Int {
        val step = containerSizePx + containerGapPx
        if (step <= 0f) return 1
        return when (shape) {
            // 矩形：每行固定列数（由用户设置），不再按可用宽度推算。
            QuickWheelShape.RECT -> columnCount.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)

            QuickWheelShape.CIRCLE -> {
                if (radiusPx <= containerSizePx / 2f) return 1
                val halfChord = (containerSizePx / 2f / radiusPx).coerceIn(0f, 1f)
                val slotAngle = asin(halfChord.toDouble()) * 2.0
                val gapAngle = (containerGapPx / radiusPx).toDouble()
                val total = slotAngle + gapAngle
                if (total <= 0.0) 1 else floor(sectorAngleRad(sectorMask) / total).toInt().coerceAtLeast(1)
            }
        }
    }

    /** 预算某个数量在给定样式/形态下会占用几环（列表摘要用）。 */
    fun estimateRingCount(
        slotCount: Int,
        shape: QuickWheelShape,
        style: QuickWheelStyleSpec,
        availableWidthPx: Float,
        density: Float = 1f,
        sectorMask: Int = SECTOR_ALL,
    ): Int {
        if (slotCount <= 0) return 0
        val safeDensity = if (density > 0f) density else 1f
        val normalized = normalize(style)
        // 几何用**实际**容器尺寸：步距 = 容器尺寸 + 间隙，容器调小就会挨得更紧。
        val sizePx = normalized.containerSizeDp * safeDensity
        val gapPx = normalized.containerGapDp * safeDensity
        val ringGapPx = normalized.ringGapDp * safeDensity
        val initialPx = normalized.initialRadiusDp * safeDensity

        var remaining = slotCount
        var rings = 0
        val maxRings = if (shape == QuickWheelShape.RECT) MAX_RECT_ROWS else MAX_RINGS
        while (remaining > 0 && rings < maxRings) {
            val radiusPx = ringRowRadiusPx(shape, initialPx, sizePx, gapPx, ringGapPx, rings)
            val capacity = ringCapacity(
                radiusPx = radiusPx,
                containerSizePx = sizePx,
                containerGapPx = gapPx,
                shape = shape,
                availableWidthPx = availableWidthPx,
                sectorMask = sectorMask,
                columnCount = normalized.columnCount,
            )
            remaining -= minOf(capacity, remaining)
            rings++
        }
        return rings
    }

    /**
     * 围绕 ([anchorX], [anchorY]) 铺一环组容器（一级或二级共用）。
     *
     * @param style 该级样式；偏移按屏幕坐标直接施加，不做逐容器裁剪（由调用方决定锚点是否越界）。
     * @param rectCorner 矩形形态下"网格哪个角贴住锚点"（= 矩形版方向选择）；圆形忽略。
     */
    fun layoutRing(
        slotCount: Int,
        shape: QuickWheelShape,
        style: QuickWheelStyleSpec,
        anchorX: Float,
        anchorY: Float,
        density: Float,
        availableWidthPx: Float,
        sectorMask: Int = SECTOR_ALL,
        rectCorner: QuickWheelRectCorner = QuickWheelRectCorner.CENTER_TOP,
        appendAddSlot: Boolean = false,
    ): List<QuickWheelPlacedSlot> {
        if (slotCount <= 0 && !appendAddSlot) return emptyList()
        val safeDensity = if (density > 0f) density else 1f
        val normalized = normalize(style)
        // 容器边长（px）：既是绘制尺寸，也是排布步距的基准（步距 = sizePx + gapPx）。
        // 因此容器调小时，环上 / 行内相邻容器会真的挨得更近（间隙 = containerGapDp 本身）。
        val sizePx = normalized.containerSizeDp * safeDensity
        val gapPx = normalized.containerGapDp * safeDensity
        val ringGapPx = normalized.ringGapDp * safeDensity
        val cornerPx = normalized.containerCornerDp * safeDensity
        val initialPx = normalized.initialRadiusDp * safeDensity
        val offsetXPx = normalized.offsetXDp * safeDensity
        val offsetYPx = normalized.offsetYDp * safeDensity

        // 矩形：网格整体按"贴住锚点的那个角"平移（圆形不用）。
        // ⚠️ CENTER_TOP（编辑层 / 配置页预览）完全沿用历史布局：行宽按列数、首行水平居中、逐行向下。
        val rectColumnCount = normalized.columnCount.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
        // ⚠️ 列数固定为设定值，**不随槽数收缩**：旧写法 min(columnCount, slotCount) 会让
        // "新增一个容器"改变网格宽度 → 整行平移，破坏"格位固定、新增不移位"。
        // 未用到的列只是留白；贴角模式的行定位不依赖网格宽度（见 place()）。
        val rectGridColumns = rectColumnCount
        val rectGridWidthPx = rectGridColumns * sizePx +
            (rectGridColumns - 1).coerceAtLeast(0) * gapPx
        // 行的纵向位置改为在 place() 里按 index 逐行定位（上角向下 / 下角向上），
        // 因此不再需要"整网格高度"——旧写法会随行数变化，新增一行就把已有行整体平移。
        // 行内 / 行的定位全部在 place() 里按 index 逐格算：贴角模式只用 index，
        // 不依赖网格宽度或本行数量 → "新增容器不移位"。
        // （历史布局 CENTER_TOP 除外：它按设定列数整行居中、逐行向下，沿用旧行为。）

        val result = ArrayList<QuickWheelPlacedSlot>(slotCount + if (appendAddSlot) 1 else 0)
        var remaining = slotCount
        var ringIndex = 0
        var globalIndex = 0

        /**
         * 算出一个槽位。
         *
         * ⚠️ 位置只由 `indexInRing` 决定，**与同环（行）容器数量无关**（固定格位）：
         * 某环未布满时，容器就落在第 0..N−1 个格位上，**不做"整组居中"补偿**。
         * 这样新增容器不会移动任何已有容器（位置记忆成立），「+」的追加位也正好是
         * "下一个容器会出现的格位"，不需要再为越界做夹取。
         *
         */
        fun place(
            ringIndex: Int,
            radiusPx: Float,
            indexInRing: Int,
            capacity: Int,
            globalIndex: Int,
        ): QuickWheelPlacedSlot {
            val stepRad = sectorAngleRad(sectorMask) / capacity.toDouble()
            val startRad = -PI / 2.0
            val centerDeg: Float
            val halfStepDeg: Float
            val baseX: Float
            val baseY: Float
            when (shape) {
                QuickWheelShape.CIRCLE -> {
                    centerDeg = mapVirtualAngleDeg(
                        sectorMask,
                        Math.toDegrees((indexInRing + 0.5) * stepRad).toFloat(),
                    )
                    halfStepDeg = Math.toDegrees(stepRad / 2.0).toFloat()
                    val centerRad = Math.toRadians(centerDeg.toDouble())
                    baseX = (cos(centerRad) * radiusPx).toFloat()
                    baseY = (sin(centerRad) * radiusPx).toFloat()
                }

                QuickWheelShape.RECT -> {
                    val offsetInRow = indexInRing * (sizePx + gapPx)
                    baseX = when (rectCorner) {
                        // 历史布局（编辑层 / 配置页预览）：整行按设定列数水平居中、行内从左往右。
                        QuickWheelRectCorner.CENTER_TOP ->
                            -rectGridWidthPx / 2f + sizePx / 2f + offsetInRow

                        // 贴角模式：从被贴的那个角**向外铺**，只用 indexInRing 定位
                        //（不依赖网格宽度 / 本行数量）→ 新增容器不会移动已有容器。
                        QuickWheelRectCorner.TOP_LEFT,
                        QuickWheelRectCorner.BOTTOM_LEFT,
                        -> sizePx / 2f + offsetInRow

                        QuickWheelRectCorner.TOP_RIGHT,
                        QuickWheelRectCorner.BOTTOM_RIGHT,
                        -> -(sizePx / 2f + offsetInRow)
                    }
                    // 行位置同样只由行号决定：上角首行贴锚点下方、逐行向下；下角反号
                    //（首行贴锚点上方、逐行向上）→ 新增一行不会平移已有行。
                    baseY = when (rectCorner) {
                        QuickWheelRectCorner.CENTER_TOP -> radiusPx

                        QuickWheelRectCorner.TOP_LEFT,
                        QuickWheelRectCorner.TOP_RIGHT,
                        -> radiusPx - gapPx

                        QuickWheelRectCorner.BOTTOM_LEFT,
                        QuickWheelRectCorner.BOTTOM_RIGHT,
                        -> -(radiusPx - gapPx)
                    }
                    val rectStepRad = 2.0 * PI / capacity.toDouble()
                    centerDeg = Math.toDegrees(startRad + (indexInRing + 0.5) * rectStepRad).toFloat()
                    halfStepDeg = Math.toDegrees(rectStepRad / 2.0).toFloat()
                }
            }
            return QuickWheelPlacedSlot(
                index = globalIndex,
                centerX = anchorX + baseX + offsetXPx,
                centerY = anchorY + baseY + offsetYPx,
                sizePx = sizePx,
                cornerPx = cornerPx,
                ringIndex = ringIndex,
                indexInRing = indexInRing,
                startAngleDeg = centerDeg - halfStepDeg,
                endAngleDeg = centerDeg + halfStepDeg,
                minRadiusPx = radiusPx - sizePx / 2f,
                maxRadiusPx = radiusPx + sizePx / 2f,
            )
        }

        // 矩形按"行"铺，每行 = 列数：行数上限不能用圆形的 MAX_RINGS（否则 >8 行的容器直接消失）。
        val maxRings = if (shape == QuickWheelShape.RECT) MAX_RECT_ROWS else MAX_RINGS
        while (ringIndex < maxRings) {
            val radiusPx = ringRowRadiusPx(shape, initialPx, sizePx, gapPx, ringGapPx, ringIndex)
            val capacity = ringCapacity(
                radiusPx = radiusPx,
                containerSizePx = sizePx,
                containerGapPx = gapPx,
                shape = shape,
                availableWidthPx = availableWidthPx,
                sectorMask = sectorMask,
                columnCount = normalized.columnCount,
            )
            val inRing = minOf(capacity, remaining)
            for (indexInRing in 0 until inRing) {
                result += place(ringIndex, radiusPx, indexInRing, capacity, globalIndex)
                globalIndex++
            }
            remaining -= inRing
            if (remaining <= 0) {
                // 真实槽位到此结束 → 追加「+」的位置。
                // ⚠️ 它不参与上面的排布：真实槽位位置与不追加时逐个完全一致
                // （编辑层因此与「轮盘预览 / 真实呼出」共用同一套位置）。
                if (appendAddSlot) {
                    if (inRing < capacity) {
                        // 同一环（行）的下一个位置：沿用真实槽位的居中偏移 → 正好差一个完整步距。
                        result += place(
                            ringIndex = ringIndex,
                            radiusPx = radiusPx,
                            indexInRing = if (inRing <= 0) 0 else inRing,
                            capacity = capacity,
                            globalIndex = globalIndex,
                        )
                    } else if (ringIndex + 1 < maxRings) {
                        // 末环已满 → 下一环的第一个位置。
                        val nextRadius = ringRowRadiusPx(
                            shape,
                            initialPx,
                            sizePx,
                            gapPx,
                            ringGapPx,
                            ringIndex + 1,
                        )
                        val nextCapacity = ringCapacity(
                            radiusPx = nextRadius,
                            containerSizePx = sizePx,
                            containerGapPx = gapPx,
                            shape = shape,
                            availableWidthPx = availableWidthPx,
                            sectorMask = sectorMask,
                            columnCount = normalized.columnCount,
                        )
                        result += place(
                            ringIndex = ringIndex + 1,
                            radiusPx = nextRadius,
                            indexInRing = 0,
                            capacity = nextCapacity,
                            globalIndex = globalIndex,
                        )
                    }
                }
                break
            }
            ringIndex++
        }
        return result
    }

    /**
     * 中心容器几何。
     *
     * - **圆形**：中心大圆**视作「第 0 环」**——它的边缘到第一环容器内缘的间隙 = 「环间距」
     *   （[QuickWheelStyleSpec.ringGapDp]），与外面环与环之间的间隙是**同一套参数**：
     *   外面环越疏远，大圆与第一环也越疏远。
     *
     *   ⚠️ 驱动关系：[QuickWheelStyleSpec.initialRadiusDp] 是**第一圈容器中心**到呼出原点的半径
     *   （不是大圆半径）；大圆半径 = 初始半径 − 容器半宽 − 环间距，并有下限
     *   （`0.75 × 容器尺寸`，初始半径较小时托住大圆，避免过小 / 与外环重叠）。
     *   因此：调「容器间距」**不影响**大圆尺寸（它只改角度步距 / 单环容量）；
     *   调「环间距」会同时改变大圆与第一环的间隙（未撞下限时 1:1）。
     *
     *   中心**恒为正圆**；中心同样跟随 [QuickWheelStyleSpec.offsetXDp] / [QuickWheelStyleSpec.offsetYDp]
     *   平移，与四周小容器保持一致。
     * - **矩形**：**没有**中心容器——几何尺寸恒为 0（渲染 / 命中均按形态跳过）。
     */
    fun layoutCenter(
        anchorX: Float,
        anchorY: Float,
        density: Float,
        primaryStyle: QuickWheelStyleSpec,
        primaryShape: QuickWheelShape,
    ): QuickWheelPlacedSlot {
        val safeDensity = if (density > 0f) density else 1f
        val normalized = normalize(primaryStyle)
        val itemSizePx = normalized.containerSizeDp * safeDensity
        val initialPx = normalized.initialRadiusDp * safeDensity
        val ringGapPx = normalized.ringGapDp * safeDensity
        val offsetXPx = normalized.offsetXDp * safeDensity
        val offsetYPx = normalized.offsetYDp * safeDensity
        val sizePx: Float
        val cornerPx: Float
        when (primaryShape) {
            QuickWheelShape.CIRCLE -> {
                // ⚠️ 大圆 = 「第 0 环」：大圆边缘到第一环容器内缘的间隙 = **环间距**，
                // 与外面环与环之间同一套参数（环间距越疏远 → 大圆离第一环也越疏远）。
                // 下限：初始半径较小时托住大圆（保证中心容器可点）；上界：**绝不**超过第一环
                // 容器内缘（初始半径 − 容器半宽），否则会与大容器重叠。
                val ringInnerEdgePx = initialPx - itemSizePx / 2f
                val centerRadiusPx = (ringInnerEdgePx - ringGapPx)
                    .coerceAtLeast(itemSizePx * 0.75f)
                    .coerceAtMost(ringInnerEdgePx)
                sizePx = centerRadiusPx * 2f
                cornerPx = sizePx / 2f
            }

            // 矩形形态没有中心容器：尺寸置 0，渲染 / 命中会按形态直接跳过。
            QuickWheelShape.RECT -> {
                sizePx = 0f
                cornerPx = 0f
            }
        }
        return QuickWheelPlacedSlot(
            index = CENTER_INDEX,
            centerX = anchorX + offsetXPx,
            centerY = anchorY + offsetYPx,
            sizePx = sizePx,
            cornerPx = cornerPx,
            ringIndex = -1,
            indexInRing = -1,
            startAngleDeg = 0f,
            endAngleDeg = 360f,
            minRadiusPx = 0f,
            maxRadiusPx = sizePx / 2f,
        )
    }

    const val CENTER_INDEX = -1

    fun buildLayout(
        primaryCount: Int,
        secondaryCount: Int,
        expandedPrimaryIndex: Int,
        primaryShape: QuickWheelShape,
        secondaryShape: QuickWheelShape,
        primaryStyle: QuickWheelStyleSpec,
        secondaryStyle: QuickWheelStyleSpec,
        anchorX: Float,
        anchorY: Float,
        density: Float,
        screenWidthPx: Float,
        sectorMask: Int = SECTOR_ALL,
        /**
         * 非 null 时**强制**一级圆形的扇区（动作绑定处用户手动选定的方案）。
         *
         * 与 [adaptive] **不冲突**：只覆盖一级圆形，二级的扇区 / 矩形基准角仍照常自适应求解 ——
         * 否则"手动固定一级扇区"会把二级一起拖下水（非自适应时二级恒为整圆，
         * 从屏幕角落展开二级就会是一个大半在屏外的整圆）。
         * 为 null 时行为完全不变（自适应求解，或按 [sectorMask] 渲染）。矩形一级忽略该值。
         */
        primarySectorMaskOverride: Int? = null,
        /** 非 null 时启用运行时自适应：一级 / 二级的扇区（圆形）或网格基准角（矩形）由屏幕空间求解。 */
        adaptive: QuickWheelAdaptiveScreen? = null,
        /**
         * 非 null 时**只**对二级做屏幕求解（一级仍按 [sectorMask] / [rectCorner] 渲染）。
         *
         * 配置页预览 / 编辑层用它：一级保持"所见即所得"（用户配置的扇区），二级则与真机一致
         * ——锚点 = 被展开的一级容器中心，求解到"所有二级容器都在屏内"（都放不下时取越界最小）。
         * [adaptive] 非 null 时二级本来就会求解，此时无需再传本参数。
         */
        secondaryAdaptive: QuickWheelAdaptiveScreen? = null,
        /** 矩形形态下网格贴住锚点的角（非自适应时生效；[adaptive] 非 null 时会被求解结果覆盖）。 */
        rectCorner: QuickWheelRectCorner = QuickWheelRectCorner.CENTER_TOP,
        // 末尾是否额外算出一个「+」追加位（编辑层用）；追加位**不影响**真实容器位置。
        appendPrimaryAddSlot: Boolean = false,
        appendSecondaryAddSlot: Boolean = false,
    ): QuickWheelLayout {
        val availableWidthPx = (screenWidthPx - 32f * density).coerceAtLeast(0f)
        val center = layoutCenter(anchorX, anchorY, density, primaryStyle, primaryShape)
        // 一级：自适应时按"锚点 + 全部容器在屏内"求解扇区（圆形）或网格基准角（矩形）。
        val autoPrimaryMask = if (adaptive != null && primaryShape == QuickWheelShape.CIRCLE) {
            solveSectorMaskForAnchor(
                anchorX = anchorX,
                anchorY = anchorY,
                screenWidthPx = adaptive.widthPx,
                screenHeightPx = adaptive.heightPx,
                marginPx = adaptive.marginPx,
                style = primaryStyle,
                slotCount = primaryCount,
                density = density,
            )
        } else {
            normalizeSectorMask(sectorMask)
        }
        // 一级扇区：动作绑定处手动指定的方案优先（**仅圆形**；二级仍自适应求解）。
        // 矩形没有"扇区"概念 → 即使载荷里带着历史手动值也一律忽略，只按基准角排布。
        val primaryMask = primarySectorMaskOverride
            ?.takeIf { primaryShape == QuickWheelShape.CIRCLE }
            ?: autoPrimaryMask
        val primaryCorner = if (adaptive != null && primaryShape == QuickWheelShape.RECT) {
            solveRectCornerForAnchor(
                anchorX = anchorX,
                anchorY = anchorY,
                screenWidthPx = adaptive.widthPx,
                screenHeightPx = adaptive.heightPx,
                marginPx = adaptive.marginPx,
                style = primaryStyle,
                slotCount = primaryCount,
                density = density,
            )
        } else {
            rectCorner
        }
        val primary = layoutRing(
            slotCount = primaryCount,
            shape = primaryShape,
            style = primaryStyle,
            anchorX = anchorX,
            anchorY = anchorY,
            density = density,
            availableWidthPx = availableWidthPx,
            sectorMask = primaryMask,
            rectCorner = primaryCorner,
            appendAddSlot = appendPrimaryAddSlot,
        )
        val expandedAnchor = primary.getOrNull(expandedPrimaryIndex)
        // 二级求解用的屏幕范围：真机（adaptive）与配置页预览 / 编辑层（secondaryAdaptive）共用同一套逻辑。
        val secondaryScreen = adaptive ?: secondaryAdaptive
        // ⚠️ 空二级层级也要能出「+」：编辑层进入一个还没有子容器的容器时，
        // 二级真实数量为 0，但仍需要一个追加位给「+」。
        val secondary = if (expandedAnchor != null &&
            (secondaryCount > 0 || appendSecondaryAddSlot)
        ) {
            /**
             * 按给定槽位数排二级：锚点固定为被展开的一级容器（父容器）中心，只求解扇区 / 基准角。
             *
             * [slotCountForSolve] 只影响"求解"（选扇区 / 基准角），真实容器数量恒为 [secondaryCount]。
             */
            fun buildSecondary(slotCountForSolve: Int): List<QuickWheelPlacedSlot> {
                val mask = if (secondaryScreen != null && secondaryShape == QuickWheelShape.CIRCLE) {
                    solveSectorMaskForAnchor(
                        anchorX = expandedAnchor.centerX,
                        anchorY = expandedAnchor.centerY,
                        screenWidthPx = secondaryScreen.widthPx,
                        screenHeightPx = secondaryScreen.heightPx,
                        marginPx = secondaryScreen.marginPx,
                        style = secondaryStyle,
                        slotCount = slotCountForSolve,
                        density = density,
                    )
                } else {
                    SECTOR_ALL
                }
                val corner = if (secondaryScreen != null && secondaryShape == QuickWheelShape.RECT) {
                    solveRectCornerForAnchor(
                        anchorX = expandedAnchor.centerX,
                        anchorY = expandedAnchor.centerY,
                        screenWidthPx = secondaryScreen.widthPx,
                        screenHeightPx = secondaryScreen.heightPx,
                        marginPx = secondaryScreen.marginPx,
                        style = secondaryStyle,
                        slotCount = slotCountForSolve,
                        density = density,
                    )
                } else {
                    QuickWheelRectCorner.CENTER_TOP
                }
                return layoutRing(
                    slotCount = secondaryCount,
                    shape = secondaryShape,
                    style = secondaryStyle,
                    anchorX = expandedAnchor.centerX,
                    anchorY = expandedAnchor.centerY,
                    density = density,
                    availableWidthPx = availableWidthPx,
                    sectorMask = mask,
                    rectCorner = corner,
                    appendAddSlot = appendSecondaryAddSlot,
                )
            }

            val first = buildSecondary(secondaryCount)
            // 🔸「+」= "下一个容器会出现的格位"：
            // - 求解按**真实数量**做 → 真实容器与真机逐格一致（这是首要目标）；
            // - 只有当这个格位会落到屏幕外（例如本环刚好排满、「+」被挤到更外一环）时，才把「+」
            //   也算作一个容器重解一次，保证编辑层永远点得到「+」。必要时才牺牲那一点一致性。
            val addSlot = if (appendSecondaryAddSlot) first.lastOrNull() else null
            val addSlotOutside = addSlot != null && secondaryScreen != null &&
                slotsOverflow(
                    slots = listOf(addSlot),
                    screenWidthPx = secondaryScreen.widthPx,
                    screenHeightPx = secondaryScreen.heightPx,
                    marginPx = secondaryScreen.marginPx,
                ) > 0f
            if (addSlotOutside) buildSecondary(secondaryCount + 1) else first
        } else {
            emptyList()
        }
        // 中心跟随偏移：整个参照点以中心容器的圆心为准（偏移为 0 时即呼出原点）。
        val centerX = center.centerX
        val centerY = center.centerY
        val outerRadiusPx = primary.maxOfOrNull { hypot(it.centerX - centerX, it.centerY - centerY) + it.sizePx / 2f }
            ?.coerceAtLeast(center.sizePx / 2f)
            ?: (center.sizePx / 2f)
        return QuickWheelLayout(
            centerX = centerX,
            centerY = centerY,
            centerSizePx = center.sizePx,
            centerCornerPx = center.cornerPx,
            primarySlots = primary,
            secondarySlots = secondary,
            expandedPrimaryIndex = if (secondary.isEmpty()) -1 else expandedPrimaryIndex,
            outerRadiusPx = outerRadiusPx,
            sectorMask = primaryMask,
            rectCorner = primaryCorner,
        )
    }

    /** 推荐锚点：把整轮盘收进屏幕内（参考实现按触发点自适应）。 */
    fun clampAnchor(
        anchorX: Float,
        anchorY: Float,
        radiusPx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        marginPx: Float,
    ): Pair<Float, Float> {
        val minX = marginPx + radiusPx
        val maxX = screenWidthPx - marginPx - radiusPx
        val minY = marginPx + radiusPx
        val maxY = screenHeightPx - marginPx - radiusPx
        val x = if (maxX >= minX) anchorX.coerceIn(minX, maxX) else screenWidthPx / 2f
        val y = if (maxY >= minY) anchorY.coerceIn(minY, maxY) else screenHeightPx / 2f
        return x to y
    }

    /**
     * 浮层 / 编辑层 / 预览共用的锚点解析。
     *
     * 三处必须完全一致，否则「轮盘预览」与「轮盘功能设置」里的轮盘位置会对不上。
     * 以屏幕正中为基准，并把整轮盘收进屏幕内。
     */
    fun resolveAnchor(
        primaryStyle: QuickWheelStyleSpec,
        density: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        marginDp: Float = 10f,
    ): Pair<Float, Float> = clampAnchor(
        anchorX = screenWidthPx / 2f,
        anchorY = screenHeightPx / 2f,
        radiusPx = estimatedPrimaryRadiusPx(primaryStyle, density),
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        marginPx = marginDp * density,
    )

    /** 一级环的估算占位半径，供 [clampAnchor] 使用（按实际容器尺寸估算，越小的容器占位越小）。 */
    fun estimatedPrimaryRadiusPx(style: QuickWheelStyleSpec, density: Float): Float {
        val safeDensity = if (density > 0f) density else 1f
        val normalized = normalize(style)
        return (normalized.initialRadiusDp + normalized.containerSizeDp * 1.5f) * safeDensity
    }

    /** 二级环的估算占位半径（同样按实际容器尺寸）。 */
    fun estimatedSecondaryRadiusPx(style: QuickWheelStyleSpec, density: Float): Float {
        val safeDensity = if (density > 0f) density else 1f
        val normalized = normalize(style)
        return (normalized.initialRadiusDp + normalized.containerSizeDp * 1.5f) * safeDensity
    }

    /**
     * 命中容差（相对容器尺寸）：0.08 → 默认 50dp 容器约 4dp。
     *
     * 给手指落点一点余量；同时保证"容器之间的缝"（间距 > 2 × 容差，默认 > 8dp）依旧是**无主区**，
     * 只有间距很小（两个容器的命中范围重叠）时才由"取最近者"决定归属。
     */
    private const val HIT_TOLERANCE_SIZE_RATIO = 0.08f

    /**
     * 命中测试：**二级 → 中心 → 一级**（顺序 = 绘制层级：谁画在上谁优先）。
     *
     * ⚠️ 二级必须排在最前：二级画在中心 / 一级**之上**，而二级环的圆心是父容器中心 ——
     * 父容器距呼出圆心 = 一级初始半径，二级初始半径与之相近时，**朝内侧的那个二级容器会落进
     * 中心大圆内部**。曾经中心排在最前，于是"看得见的是二级容器、点到的却是中心"：动作错，
     * 而且会连带把二级收起（[shouldCollapseExpandedSecondary] 对 CENTER 一律返回 true）。
     *
     * 未被二级覆盖时，中心照常优先于一级（两者几何上相切、不会重叠，此顺序只是保持既有行为）。
     * 返回的 index 是「该级列表内的顺序索引」，中心命中时 index 为 [CENTER_INDEX]。
     */
    fun hitTest(
        layout: QuickWheelLayout,
        shape: QuickWheelShape,
        rawX: Float,
        rawY: Float,
    ): QuickWheelHit {
        // 二级画在最上层 → 先判二级。
        hitIndex(layout.secondarySlots, rawX, rawY)?.let {
            return QuickWheelHit(QuickWheelHitKind.SECONDARY, it)
        }
        // 中心容器只在圆形形态存在；矩形形态下即使命中该区域也不算中心。
        if (shape == QuickWheelShape.CIRCLE) {
            val centerHalf = layout.centerSizePx / 2f
            if (hypot(rawX - layout.centerX, rawY - layout.centerY) <= centerHalf) {
                return QuickWheelHit(QuickWheelHitKind.CENTER, CENTER_INDEX)
            }
        }
        hitIndex(layout.primarySlots, rawX, rawY)?.let {
            return QuickWheelHit(QuickWheelHitKind.PRIMARY, it)
        }
        return QuickWheelHit.NONE
    }

    /**
     * 二级展开时的"有效命中"：在 [hitTest] 基础上，把**落在联合区域内部**的
     * 中心 / 其它一级容器命中一律并成 [QuickWheelHit.NONE]。
     *
     * 依据「二级优先」：二级展开后，只要手指还在「父容器 + 全部二级容器」的区域内，
     * 就不该把目标判给别的容器 —— 否则会同时坏两件事：
     * - 高亮跳到一级容器 / 中心上（视觉上二级明明盖着它）；
     * - 松手时按那次命中把二级收起（"缝下面的一级容器抢走二级"）。
     *
     * 区域**之外**的命中照常返回（它会触发 [shouldCollapseExpandedSecondary] 收起二级，符合预期）；
     * 未展开二级时与 [hitTest] 完全等价。
     */
    fun hitTestWhileExpanded(
        layout: QuickWheelLayout,
        shape: QuickWheelShape,
        rawX: Float,
        rawY: Float,
        slackPx: Float = 0f,
    ): QuickWheelHit {
        val hit = hitTest(layout, shape, rawX, rawY)
        val expanded = layout.expandedPrimaryIndex
        if (expanded < 0) return hit
        if (hit.kind == QuickWheelHitKind.SECONDARY) return hit
        if (hit.kind == QuickWheelHitKind.PRIMARY && hit.index == expanded) return hit
        // 已离开联合区域 → 保留原命中，交给收起判定。
        if (isOutsideExpandedRegion(layout, expanded, rawX, rawY, slackPx)) return hit
        return QuickWheelHit.NONE
    }

    /**
     * 落在哪个容器上 —— **统一规则，与形态无关**。
     *
     * 只有手指落入容器**边界**（容器是圆角方块，见 [isInsideSlot]）才算命中；多个容器都覆盖时取最近者；
     * 落在容器之间的缝里 → `null`（无主区）。
     *
     * 旧实现对圆形额外有一层"半径带 + 角度扇区"兜底：同环相邻容器的扇区首尾相接，
     * 于是**缝里也会命中隔壁容器**（圆形"同环无缝"就是它带来的）。那与"只有手指真的落在
     * 容器边界内才算命中"的预期不符，也让圆形 / 矩形行为不一致，故删除。
     */
    private fun hitIndex(
        slots: List<QuickWheelPlacedSlot>,
        rawX: Float,
        rawY: Float,
    ): Int? {
        var bestIndex: Int? = null
        var bestDistSq = Float.MAX_VALUE
        for (slot in slots) {
            if (!isInsideSlot(slot, rawX, rawY)) continue
            val dx = slot.centerX - rawX
            val dy = slot.centerY - rawY
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestIndex = slot.index
            }
        }
        return bestIndex
    }

    /**
     * 手指是否落在容器**边界**内（含 [HIT_TOLERANCE_SIZE_RATIO] 的外扩容差）。
     *
     * 容器视觉上是**圆角方块**（圆角半径 = [QuickWheelPlacedSlot.cornerPx]），所以按圆角矩形判定：
     * 先判"去掉圆角后的十字带"，再在四角按圆判定。用普通方形判定会让四个角多出一块
     * （对角方向最多多出 `半尺寸 × (√2 − 1)`），圆形排列时相邻容器的命中范围会斜着搭上，
     * 缝隙里就能命中隔壁容器 —— 与"真实落入容器边界"不符。
     */
    private fun isInsideSlot(slot: QuickWheelPlacedSlot, rawX: Float, rawY: Float): Boolean {
        val tolerance = slot.sizePx * HIT_TOLERANCE_SIZE_RATIO
        val half = slot.sizePx / 2f + tolerance
        val dx = abs(slot.centerX - rawX)
        val dy = abs(slot.centerY - rawY)
        if (dx > half || dy > half) return false
        val cornerRadius = slot.cornerPx.coerceIn(0f, slot.sizePx / 2f) + tolerance
        val straight = half - cornerRadius
        if (dx <= straight || dy <= straight) return true
        val cx = dx - straight
        val cy = dy - straight
        return cx * cx + cy * cy <= cornerRadius * cornerRadius
    }

    /** 是否落在轮盘之外（用于「点空白处收起」）。 */
    fun isOutsideWheel(
        layout: QuickWheelLayout,
        rawX: Float,
        rawY: Float,
        marginPx: Float = 0f,
    ): Boolean {
        val dx = rawX - layout.centerX
        val dy = rawY - layout.centerY
        val limitSq = (layout.outerRadiusPx + marginPx) * (layout.outerRadiusPx + marginPx)
        if (dx * dx + dy * dy <= limitSq) return false
        // 二级展开时，以被展开的一级容器为中心也算在轮盘内
        val expanded = layout.primarySlots.getOrNull(layout.expandedPrimaryIndex)
        if (expanded != null && layout.secondarySlots.isNotEmpty()) {
            val ex = rawX - expanded.centerX
            val ey = rawY - expanded.centerY
            val secondaryLimit = (layout.secondarySlots.maxOfOrNull { it.maxRadiusPx } ?: 0f) + marginPx
            if (ex * ex + ey * ey <= secondaryLimit * secondaryLimit) return false
        }
        return true
    }

    /**
     * 二级展开后「离开联合区域」判定的余量（dp）。
     *
     * 手指在半径带边缘来回抖动时给一点余量，避免二级反复收起 / 展开。
     */
    const val EXPANDED_REGION_SLACK_DP = 12f

    /**
     * 坐标是否已落在「被展开的一级容器 + 它的全部二级容器」这块**联合区域之外**。
     *
     * 联合区域 = 父容器与全部二级容器的**凸包**（想象用一根绳子绕着这些容器缠一圈绷紧），
     * 再向外放 [slackPx] 的余量。
     *
     * 为什么是凸包而不是外接矩形（AABB）：
     * - 两者都是**凸**的 → **任意两个容器之间的直线滑动路径全程在区域内**（滑经容器的缝不会
     *   误收二级）。这是必须用凸区域的原因：旧实现用"以父容器为圆心的圆"，盖不住矩形网格
     *   最外行列之间的角落；
     * - 但 AABB 把四个角的**大片空白**也算在区域内（圆形二级环尤其明显），手指早已离开所有
     *   容器却仍"不算离开"→ 二级迟迟不收起。凸包贴着容器外沿走，是"绕一圈"的最小凸形状，
     *   既保住凸性、又不含多余空白。
     *
     * ⚠️ 父容器与二级容器之间的空白（圆形：径向空白；矩形：行列间隙）**属于**区域内部。
     */
    fun isOutsideExpandedRegion(
        layout: QuickWheelLayout,
        expandedPrimaryIndex: Int,
        rawX: Float,
        rawY: Float,
        slackPx: Float = 0f,
    ): Boolean {
        val parent = layout.primarySlots.getOrNull(expandedPrimaryIndex) ?: return true
        val secondarySlots = layout.secondarySlots
        if (secondarySlots.isEmpty()) return true

        val rects = listOf(parent) + secondarySlots
        // 快速排除：凸包一定含于外接矩形之内 → 连外接矩形（含余量）都出不去，就不可能离开凸包。
        val minX = rects.minOf { it.centerX - it.sizePx / 2f }
        val maxX = rects.maxOf { it.centerX + it.sizePx / 2f }
        val minY = rects.minOf { it.centerY - it.sizePx / 2f }
        val maxY = rects.maxOf { it.centerY + it.sizePx / 2f }
        if (rawX < minX - slackPx || rawX > maxX + slackPx ||
            rawY < minY - slackPx || rawY > maxY + slackPx
        ) {
            return true
        }

        val hull = convexHullOfRects(rects)
        if (hull.size < 3) return true
        if (isInsidePolygon(hull, rawX, rawY)) return false
        return distanceToPolygon(hull, rawX, rawY) > slackPx
    }

    /** 一组轴对齐方块的凸包顶点（"绳子绕一圈"）；返回逆时针顺序。 */
    private fun convexHullOfRects(
        slots: List<QuickWheelPlacedSlot>,
    ): List<Pair<Float, Float>> {
        val points = ArrayList<Pair<Float, Float>>(slots.size * 4)
        slots.forEach { slot ->
            val h = slot.sizePx / 2f
            val l = slot.centerX - h
            val r = slot.centerX + h
            val t = slot.centerY - h
            val b = slot.centerY + h
            points += l to t
            points += r to t
            points += l to b
            points += r to b
        }
        val sorted = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (sorted.size < 3) return sorted

        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)

        val lower = ArrayList<Pair<Float, Float>>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) {
                lower.removeAt(lower.size - 1)
            }
            lower += p
        }
        val upper = ArrayList<Pair<Float, Float>>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) {
                upper.removeAt(upper.size - 1)
            }
            upper += p
        }
        // 首尾各去掉一个重复点（lower 末点 == upper 首点，upper 末点 == lower 首点）。
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** 点是否在凸多边形内（顺时针 / 逆时针都接受：看各边叉积是否同号）。 */
    private fun isInsidePolygon(
        hull: List<Pair<Float, Float>>,
        x: Float,
        y: Float,
    ): Boolean {
        var sign = 0
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            val cross = (b.first - a.first) * (y - a.second) - (b.second - a.second) * (x - a.first)
            if (abs(cross) <= 1e-3f) continue
            val s = if (cross > 0f) 1 else -1
            if (sign == 0) sign = s else if (sign != s) return false
        }
        return true
    }

    /** 点到凸多边形边界的最短距离（"还差一点点"的余量判定用）。 */
    private fun distanceToPolygon(
        hull: List<Pair<Float, Float>>,
        x: Float,
        y: Float,
    ): Float {
        var best = Float.MAX_VALUE
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            best = minOf(best, distanceToSegment(x, y, a.first, a.second, b.first, b.second))
        }
        return best
    }

    private fun distanceToSegment(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-6f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * 二级已展开时，手指移动到 (`rawX`, `rawY`)（命中 [hit] 为 [hitTest] 的结果）后是否应**收起二级**。
     *
     * 规则（「二级优先」）：
     * - 命中**父容器**或**任意二级容器** → 保持展开；
     * - 其它任何命中（中心 / 其它一级容器 / 空白）→ 一律以**是否离开联合区域**为准：
     *   区域内 → 保持；区域外（含 [slackPx] 余量）→ 收起。
     *
     * ⚠️ 旧规则有两处会把二级"抢走"：
     * - 把"落在空白"直接当成离开 → 从父容器滑向二级容器必然经过空白（圆形 = 径向空白；
     *   矩形 = 行列间隙），二级会在半路被收起；
     * - 把"命中其它容器"当成"用户换了目标"立即收起 → 但**二级容器之间的缝下面常常压着一级容器**
     *   （矩形一 / 二级都是密集网格时几乎必然），滑过缝隙就被一级容器抢走 / 收起。
     * 现在统一为"只有真的离开联合区域才收起"，与"二级画在最上层"一致。
     */
    fun shouldCollapseExpandedSecondary(
        layout: QuickWheelLayout,
        hit: QuickWheelHit,
        rawX: Float,
        rawY: Float,
        slackPx: Float = 0f,
    ): Boolean {
        val expanded = layout.expandedPrimaryIndex
        if (expanded < 0) return false
        if (hit.kind == QuickWheelHitKind.SECONDARY) return false
        if (hit.kind == QuickWheelHitKind.PRIMARY && hit.index == expanded) return false
        return isOutsideExpandedRegion(layout, expanded, rawX, rawY, slackPx)
    }
}