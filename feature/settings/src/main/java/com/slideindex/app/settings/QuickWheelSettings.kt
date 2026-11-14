package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelOpenAnimation
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.overlay.layout.QuickWheelStyleSpec

/** 单击触发方式：松手触发 / 触摸触发。 */
enum class QuickWheelTapTrigger {
    /** 松手触发：手指抬起时执行（默认）。 */
    ON_RELEASE,

    /**
     * 触摸触发：**按下的瞬间**手指已在容器内即执行（滑过不算）。
     *
     * 按下即执行并收起，因此该容器的长按动作与二级子盘都不会再触发 —— 这是本模式的固有代价，
     * 用户手动切换时即视为接受，界面上不做提示。
     */
    ON_TOUCH,
    ;

    companion object {
        fun fromName(value: String?): QuickWheelTapTrigger =
            entries.firstOrNull { it.name == value } ?: ON_RELEASE
    }
}

/** 长按触发方式：松手触发 / 超时触发。 */
enum class QuickWheelLongPressTrigger {
    /** 松手触发：长按成立后抬起执行（默认；带二级子盘的一级容器此时会展开二级）。 */
    ON_RELEASE,

    /**
     * 超时触发：长按达到阈值即执行，不必抬手。
     *
     * 优先于「展开二级」：一级容器选了它（且配了长按动作）后满时长直接执行长按动作，
     * 其二级子盘不会再展开 —— 同样是本模式的固有代价，界面上不做提示。
     * 若该容器没有长按动作，则没有可执行的东西，仍按老规则展开二级。
     */
    ON_TIMEOUT,
    ;

    companion object {
        fun fromName(value: String?): QuickWheelLongPressTrigger =
            entries.firstOrNull { it.name == value } ?: ON_RELEASE
    }
}

/**
 * 容器「打开方式」。
 *
 * 只有"打开应用 / 启动快捷方式 / 打开链接"这类**真的会打开某个东西**的动作才有意义；
 * 其余动作（返回、主屏幕、执行命令…）选了也不影响执行。
 */
enum class QuickWheelLaunchMode {
    /**
     * 跟随启动形态的既有体系（默认，也是历史数据的行为）。
     *
     * 先看**动作自带**的启动形态（挑应用时选的那个），它也"跟随"时才落到「应用与启动」的四档策略。
     * 轮盘的**单击动作**按"非长按"、**长按动作**按"长按"参与判定；但"长按切换"用的长按时长仍是
     * 那一页自己的设定 —— 轮盘的长按判定恒用轮盘自己的长按时长。
     */
    INHERIT,

    /** 始终全屏（不看动作自带设置，也不看「应用与启动」）。 */
    FULLSCREEN,

    /**
     * 始终小窗。
     *
     * 「应用与启动」未开启自由窗口时不生效（回落为全屏）：总开关是能力开关，容器只表达意图。
     */
    FREE_WINDOW,
    ;

    companion object {
        fun fromName(value: String?): QuickWheelLaunchMode =
            entries.firstOrNull { it.name == value } ?: INHERIT
    }
}

/** 容器图标来源。 */
enum class QuickWheelIconSource {
    /** 未设置图标。 */
    NONE,

    /** 内置图标库（复用项目自绘 ThinActionIcons）。 */
    ICON_LIBRARY,

    /** 已安装应用图标（值 = 包名）。 */
    APP_ICON,

    /** 图库图片（值 = 应用私有目录下的文件路径）。 */
    GALLERY,

    /** 文字图标（值 = 用户输入的文字）。 */
    TEXT,
    ;

    companion object {
        fun fromName(value: String?): QuickWheelIconSource =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

/** 一个轮盘容器（格子）。 */
data class QuickWheelSlot(
    val iconSource: QuickWheelIconSource = QuickWheelIconSource.NONE,
    /** 图标库 key / 应用包名 / 图片路径。 */
    val iconValue: String = "",
    val name: String = "",
    val tapAction: GestureAction = GestureAction.None,
    val longPressAction: GestureAction = GestureAction.None,
    val tapTrigger: QuickWheelTapTrigger = QuickWheelTapTrigger.ON_RELEASE,
    val longPressTrigger: QuickWheelLongPressTrigger = QuickWheelLongPressTrigger.ON_RELEASE,
    /**
     * 打开方式：**单击动作 / 长按动作各一份**（跟随 / 始终全屏 / 始终小窗）。
     *
     * 只对"打开应用 / 启动快捷方式 / 打开链接"这类动作有效；[QuickWheelLaunchMode.INHERIT] 时
     * 行为与历史版本完全一致（旧数据读回也是它）：单击按"非长按"、长按按"长按"参与启动形态判定。
     */
    val tapLaunchMode: QuickWheelLaunchMode = QuickWheelLaunchMode.INHERIT,
    val longPressLaunchMode: QuickWheelLaunchMode = QuickWheelLaunchMode.INHERIT,
    /** 二级子盘；只有一级容器会持有（中心容器与二级容器恒为空）。 */
    val subSlots: List<QuickWheelSlot> = emptyList(),
    /**
     * 用户主动留出的空位（「空白占位」）。
     *
     * 运行时该位置**完全透明**（不绘制任何东西，但仍占位），只在预览 / 配置里显示为"空白阴影"占位。
     * 普通容器（哪怕点击 / 长按都没设动作）不属于这一类，会照常渲染成"全白空图标"。
     */
    val placeholder: Boolean = false,
) {
    /**
     * 是否已配置内容。
     *
     * 由内容推导而非单独存储：此前用独立 `isEmpty` 标志，一旦某条写入路径漏了清除，
     * 只设了动作 / 名称的容器就会被误判为空白占位，在预览与浮层里渲染成占位图标
     * （即"设置动作后图标不显示"）。
     */
    val isConfigured: Boolean
        get() = iconSource != QuickWheelIconSource.NONE ||
            name.isNotBlank() ||
            tapAction.type != GestureActionType.NONE ||
            longPressAction.type != GestureActionType.NONE

    /** 空白占位：占位置但不绑定任何内容。 */
    val isEmpty: Boolean get() = !isConfigured

    fun normalized(): QuickWheelSlot = copy(
        name = name.trim(),
        subSlots = subSlots.take(QuickWheel.MAX_SUB_SLOTS).map { it.copy(subSlots = emptyList()) },
    )
}

/** 一个命名的自定义轮盘。 */
data class QuickWheel(
    val id: String,
    val name: String = "",
    /** 一级轮盘形状（出厂默认：圆形 —— 与二级组成「圆 + 圆」）。 */
    val primaryShape: QuickWheelShape = QuickWheelShape.CIRCLE,
    /** 二级轮盘形状（出厂默认：圆形）。 */
    val secondaryShape: QuickWheelShape = QuickWheelShape.CIRCLE,
    /**
     * 一级「**当前形态**」那套样式：只作运行时持有者 —— `normalized()` 会让它恒等于
     * [primaryCircleStyle] / [primaryRectStyle] 里当前形态的那一套。
     *
     * ⚠️ 求出厂值请改下面那两套显式参数，单改这里不会生效（会被 normalized() 覆盖）。
     */
    val primaryStyle: QuickWheelStyleSpec = QuickWheelStyleSpec(
        initialRadiusDp = 80f,
        containerGapDp = 0f,
        ringGapDp = 5f,
        containerSizeDp = 42.18182f,
        containerCornerDp = 44f,
        columnCount = 5,
    ),
    /**
     * 二级外观：与一级**同一套口径**（容器间距 / 环间距都是"实际净间隙"），
     * 只把容器尺寸放大到 44（子容器压在父容器上更清楚）。
     *
     * ⚠️ `initialRadiusDp` 的可用下限是 [QuickWheelLayoutEngine.MIN_SECONDARY_INITIAL_RADIUS_DP]（46）：
     * 二级锚点 = 父容器中心，只需"不压住父容器"（父半宽 + 子半宽，默认 44）。
     * 写更小的值会被 `normalize()` 静默夹到下限，滑条也拖不到 —— 那是个不生效的死值。
     *
     * 与 [primaryStyle] 一样，这里只是「当前形态」的持有者；
     * **出厂值请看下面的 [secondaryCircleStyle] / [secondaryRectStyle]**。
     */
    val secondaryStyle: QuickWheelStyleSpec = QuickWheelStyleSpec(
        initialRadiusDp = 46f,
        containerGapDp = 0f,
        ringGapDp = 10f,
        containerSizeDp = 37.090908f,
        containerCornerDp = 44f,
        columnCount = 5,
    ),
    /** 圆心固定容器。 */
    val centerSlot: QuickWheelSlot = QuickWheelSlot(),
    /** 一级容器。 */
    val slots: List<QuickWheelSlot> = emptyList(),
    /**
     * 一级 · **圆形**形态的出厂参数（12 项显式写全，直接改这里即可）。
     *
     * ⚠️ 四套（一级圆 / 一级矩 / 二级圆 / 二级矩）**彼此独立**：早先写成 `= primaryStyle`
     * 只是"初值相同"，圆形与矩形永远共用一套、无法分别调；现在拆成四份显式值。
     * 圆形用 [QuickWheelStyleSpec.initialRadiusDp] / [QuickWheelStyleSpec.ringGapDp]；
     * [QuickWheelStyleSpec.columnCount] 对圆形无效。
     */
    val primaryCircleStyle: QuickWheelStyleSpec = QuickWheelStyleSpec(
        showLabels = false,
        initialRadiusDp = 80f,
        containerGapDp = 0f,
        ringGapDp = 5f,
        containerSizeDp = 42.18182f,
        containerCornerDp = 44f,
        iconSizePercent = 50,
        labelSizeFactorPercent = 38,
        builtinIconSizePercent = 100,
        offsetXDp = 0f,
        offsetYDp = 0f,
        columnCount = 5,
    ),
    /**
     * 一级 · **矩形**形态的出厂参数。
     *
     * 矩形不使用 [QuickWheelStyleSpec.initialRadiusDp] / [QuickWheelStyleSpec.ringGapDp]
     * （第 0 行紧贴呼出点，行 / 列间距都用 [QuickWheelStyleSpec.containerGapDp]），
     * 真正生效的是 [QuickWheelStyleSpec.columnCount] + 间距 / 尺寸 / 圆角 / 图标比例。
     */
    val primaryRectStyle: QuickWheelStyleSpec = QuickWheelStyleSpec(
        showLabels = false,
        initialRadiusDp = 80f,
        containerGapDp = 5f,
        ringGapDp = 5f,
        containerSizeDp = 42.18182f,
        containerCornerDp = 11f,
        iconSizePercent = 50,
        labelSizeFactorPercent = 38,
        builtinIconSizePercent = 100,
        offsetXDp = 0f,
        offsetYDp = 0f,
        columnCount = 5,
    ),
    /** 二级 · **圆形**形态的出厂参数（与一级的差别：初始半径 100→48、容器尺寸 42→44）。 */
    val secondaryCircleStyle: QuickWheelStyleSpec = QuickWheelStyleSpec(
        showLabels = false,
        initialRadiusDp = 46f,
        containerGapDp = 0f,
        ringGapDp = 10f,
        containerSizeDp = 37.090908f,
        containerCornerDp = 44f,
        iconSizePercent = 50,
        labelSizeFactorPercent = 38,
        builtinIconSizePercent = 100,
        offsetXDp = 0f,
        offsetYDp = 0f,
        columnCount = 5,
    ),
    /** 二级 · **矩形**形态的出厂参数（矩形不用初始半径 / 环间距）。 */
    val secondaryRectStyle: QuickWheelStyleSpec = QuickWheelStyleSpec(
        showLabels = false,
        initialRadiusDp = 46f,
        containerGapDp = 5f,
        ringGapDp = 10f,
        containerSizeDp = 37.090908f,
        containerCornerDp = 11f,
        iconSizePercent = 50,
        labelSizeFactorPercent = 38,
        builtinIconSizePercent = 100,
        offsetXDp = 0f,
        offsetYDp = 0f,
        columnCount = 4,
    ),
    /**
     * 「长按时间」（ms）。
     *
     * 同时作用于：一级容器的长按动作判定、长按展开二级轮盘的判定、二级容器的长按动作判定。
     */
    val longPressMs: Int = QuickWheelLayoutEngine.DEFAULT_LONG_PRESS_MS,
    /**
     * 一级轮盘可用扇区掩码（4 位，bit0 = 自正上方起顺时针）。
     *
     * 默认 **左半圆**（扇区 2+3）：绝大多数呼出来自屏幕右侧边缘的手势，自适应求解最常给出的
     * 就是左半圆，所以默认预览即"右边缘的半圆轮盘"。该值仅用于配置页的预览测试，
     * 真实呼出时由触发位置自适应决定。
     */
    val sectorMask: Int = QuickWheelLayoutEngine.DEFAULT_SECTOR_MASK,
    /**
     * 呼出时**容器以外区域**的背景模糊强度（dp，0 = 不模糊）。
     *
     * 走系统跨窗口模糊（`FLAG_BLUR_BEHIND`）；系统关闭"跨窗口模糊"时该项无效。
     * 容器本身始终不透明，不受该参数影响。
     */
    val backdropBlurDp: Int = 10,
    /**
     * 呼出时**容器以外区域**的黑色遮罩浓度（%，0 = 完全透出原屏幕内容）。
     *
     * 容器本身始终不透明，不受该参数影响。
     */
    val backdropDimPercent: Int = 25,
    /**
     * 呼出时的展开动画效果。
     *
     * 只影响视觉：命中判定、长按计时都不受它影响。
     */
    val openAnimation: QuickWheelOpenAnimation = QuickWheelLayoutEngine.DEFAULT_OPEN_ANIMATION,
    /**
     * 展开动画速度（%，100 = 基准时长）。
     *
     * 只作用于**展开那一段**；长按进度（= 长按时间）与编辑层让位动画都不受它影响。
     */
    val animationSpeedPercent: Int = QuickWheelLayoutEngine.DEFAULT_ANIMATION_SPEED_PERCENT,
    /** 列表排序位置。 */
    val order: Int = 0,
) {
    val primaryActionCount: Int get() = slots.count { it.isConfigured }

    val primaryEmptyCount: Int get() = slots.count { !it.isConfigured }

    val secondaryTotalCount: Int get() = slots.sumOf { it.subSlots.size }

    /** 一级可用角度总跨度（度）。 */
    val sectorSpanDeg: Int get() = QuickWheelLayoutEngine.sectorSpanDeg(sectorMask)

    /** 一级容器个数（含用户主动留出的空位）。 */
    val renderedSlotCount: Int get() = slots.size

    fun normalized(): QuickWheel {
        // ⚠️ 初始半径下限**按级别显式传**：一级 60（要给中心大圆留余量）、二级 46（只需不压住父容器）。
        // 引擎内部的布局 / 求解拿不到级别、用的是全局最低 46 —— 所以一级这 60 必须在这里传，
        // 否则一级的"极小半径"就会一路走到布局里。
        val primaryCircle = QuickWheelLayoutEngine.normalize(
            primaryCircleStyle,
            initialRadiusMin = QuickWheelLayoutEngine.MIN_INITIAL_RADIUS_DP,
        )
        val primaryRect = QuickWheelLayoutEngine.normalize(
            primaryRectStyle,
            initialRadiusMin = QuickWheelLayoutEngine.MIN_INITIAL_RADIUS_DP,
        )
        // 二级：锚点 = 父容器中心、不画中心大圆，下限只需"不压住父容器"
        //（默认 44/2 + 44/2 = 44，留 2dp 余量 = 46）；用一级的 60 会让二级环离父容器留 16dp 空隙。
        val secondaryCircle = QuickWheelLayoutEngine.normalize(
            secondaryCircleStyle,
            initialRadiusMin = QuickWheelLayoutEngine.MIN_SECONDARY_INITIAL_RADIUS_DP,
        )
        val secondaryRect = QuickWheelLayoutEngine.normalize(
            secondaryRectStyle,
            initialRadiusMin = QuickWheelLayoutEngine.MIN_SECONDARY_INITIAL_RADIUS_DP,
        )
        return copy(
            name = name.trim(),
            // 只裁位，不归一化：草稿里保留用户真实点选，扇区环 / 预览各按各的口径（见方法注释）。
            sectorMask = QuickWheelLayoutEngine.clampSectorMask(sectorMask),
            // 两套形态参数都保存下来……
            primaryCircleStyle = primaryCircle,
            primaryRectStyle = primaryRect,
            secondaryCircleStyle = secondaryCircle,
            secondaryRectStyle = secondaryRect,
            // ……而 primaryStyle / secondaryStyle 始终等于"当前形态"那一套，
            // 这样引擎 / 渲染 / 列表摘要等既有代码无需改动。
            primaryStyle = if (primaryShape == QuickWheelShape.CIRCLE) primaryCircle else primaryRect,
            secondaryStyle = if (secondaryShape == QuickWheelShape.CIRCLE) secondaryCircle else secondaryRect,
            centerSlot = centerSlot.normalized(),
            longPressMs = QuickWheelLayoutEngine.clampLongPressMs(longPressMs),
            backdropBlurDp = QuickWheelLayoutEngine.clampBackdropBlurDp(backdropBlurDp),
            backdropDimPercent =
                QuickWheelLayoutEngine.clampBackdropDimPercent(backdropDimPercent),
            animationSpeedPercent =
                QuickWheelLayoutEngine.clampAnimationSpeedPercent(animationSpeedPercent),
            slots = slots.take(MAX_SLOTS).map { it.normalized() },
        )
    }

    companion object {
        const val MAX_WHEELS = 10

        /** 新轮盘预置的导航键容器数量：返回 / 主屏幕 / 多任务。 */
        const val DEFAULT_SLOT_COUNT = 3
        const val MAX_SLOTS = 48
        const val MAX_SUB_SLOTS = 24
        const val UNTITLED_NAME_PREFIX = "轮盘"

        /**
         * 新轮盘的初始容器：返回 / 主屏幕 / 多任务（三个导航键动作）。
         *
         * 轮盘上除编辑层末尾**唯一**的「+」之外，所有位置都是真实容器；
         * 用户主动留出的空位由 [QuickWheelSlot.placeholder] 表示，不存在"空占位容器"。
         *
         * ⚠️ [labelOf] 必须由调用方传入**与容器编辑页同一套**的动作文案（app 层传
         * `gestureActionLabelText(context, it)`）：容器编辑页选中动作时会**把名称自动填成动作文案**，
         * 默认容器若留空名，HUD / 容器标签就会与"用户自己新建一个同样动作的容器"显示不一致
         * （前者退化成"点击：返回"，后者是"返回"）。默认容器除了"是新建轮盘自带的"之外，
         * 不该有任何其它特征。
         */
        fun defaultSlots(labelOf: (GestureAction) -> String = { "" }): List<QuickWheelSlot> =
            listOf(GestureAction.Back, GestureAction.Home, GestureAction.Recents).map { action ->
                // 与「新增容器」同一条规则：默认点击 = 长按，除非用户之后手动改长按。
                QuickWheelSlot(
                    name = labelOf(action),
                    tapAction = action,
                    longPressAction = action,
                )
            }

        fun defaultName(ordinal: Int): String = "$UNTITLED_NAME_PREFIX$ordinal"
    }
}

/**
 * 「快捷轮盘」持久化编解码。
 *
 * 采用**扁平路径**表达层级，避免递归编解码：
 * - `C`：中心容器
 * - `0:<i>`：一级第 i 个容器
 * - `0:<i>>1:<j>`：一级第 i 个容器的二级第 j 个容器
 *
 * 分隔符：`\u001D` 顶层字段、`\u001E` 动作载荷、`\u001F` 样式子字段。
 * 名称 / 图标路径均放在各记录的最后，动作载荷单独用 `\u001E` 切分，避开载荷自身可能含有的分隔符。
 */
object QuickWheelCodec {
    private const val FIELD_SEP = '\u001D'
    private const val ACTION_SEP = '\u001E'
    private const val STYLE_SEP = '\u001F'

    const val PATH_CENTER = "C"
    private const val SECONDARY_MARK = ">1:"

    fun primaryPath(index: Int): String = "0:$index"

    fun secondaryPath(primaryIndex: Int, index: Int): String = "0:$primaryIndex$SECONDARY_MARK$index"

    fun isCenterPath(path: String): Boolean = path == PATH_CENTER

    fun isSecondaryPath(path: String): Boolean = path.contains(SECONDARY_MARK)

    /** `0:3` → 3 */
    fun parsePrimaryIndex(path: String): Int? {
        if (isCenterPath(path) || isSecondaryPath(path)) return null
        return path.substringAfter("0:", "").toIntOrNull()
    }

    /** `0:3>1:1` → 3 */
    fun parseSecondaryParent(path: String): Int? =
        path.substringBefore(SECONDARY_MARK, "")
            .substringAfter("0:", "")
            .toIntOrNull()

    /** `0:3>1:1` → 1 */
    fun parseSecondaryIndex(path: String): Int? =
        path.substringAfter(SECONDARY_MARK, "").toIntOrNull()

    fun newWheel(
        id: String,
        ordinal: Int,
        order: Int,
        /** 默认容器的名称来源（与容器编辑页同一套动作文案），见 [QuickWheel.defaultSlots]。 */
        slotLabel: (GestureAction) -> String = { "" },
    ): QuickWheel = QuickWheel(
        id = id,
        name = QuickWheel.defaultName(ordinal),
        slots = QuickWheel.defaultSlots(slotLabel),
        order = order,
    )

    // ── 样式 ────────────────────────────────────────────

    private fun encodeStyle(style: QuickWheelStyleSpec): String = listOf(
        if (style.showLabels) "1" else "0",
        style.initialRadiusDp.toString(),
        style.containerGapDp.toString(),
        style.ringGapDp.toString(),
        style.containerSizeDp.toString(),
        style.containerCornerDp.toString(),
        style.iconSizePercent.toString(),
        style.labelSizeFactorPercent.toString(),
        style.builtinIconSizePercent.toString(),
        style.offsetXDp.toString(),
        style.offsetYDp.toString(),
    ).joinToString(STYLE_SEP.toString())

    private fun decodeStyle(raw: String): QuickWheelStyleSpec {
        val parts = raw.split(STYLE_SEP)
        if (parts.size < 11) return QuickWheelStyleSpec()
        return QuickWheelStyleSpec(
            showLabels = parts[0] == "1",
            initialRadiusDp = parts[1].toFloatOrNull() ?: QuickWheelLayoutEngine.DEFAULT_INITIAL_RADIUS_DP,
            containerGapDp = parts[2].toFloatOrNull() ?: QuickWheelLayoutEngine.DEFAULT_CONTAINER_GAP_DP,
            ringGapDp = parts[3].toFloatOrNull() ?: QuickWheelLayoutEngine.DEFAULT_RING_GAP_DP,
            containerSizeDp = parts[4].toFloatOrNull() ?: QuickWheelLayoutEngine.DEFAULT_CONTAINER_SIZE_DP,
            containerCornerDp = parts[5].toFloatOrNull() ?: QuickWheelLayoutEngine.DEFAULT_CONTAINER_CORNER_DP,
            iconSizePercent = parts[6].toIntOrNull() ?: QuickWheelLayoutEngine.DEFAULT_ICON_SIZE_PERCENT,
            labelSizeFactorPercent = parts[7].toIntOrNull()
                ?: QuickWheelLayoutEngine.DEFAULT_LABEL_SIZE_FACTOR_PERCENT,
            builtinIconSizePercent = parts[8].toIntOrNull()
                ?: QuickWheelLayoutEngine.DEFAULT_BUILTIN_ICON_SIZE_PERCENT,
            offsetXDp = parts[9].toFloatOrNull() ?: 0f,
            offsetYDp = parts[10].toFloatOrNull() ?: 0f,
            // 第 12 段（索引 11）为矩形列数；旧记录没有这段 → 用默认列数（向后兼容）。
            columnCount = parts.getOrNull(11)?.toIntOrNull()
                ?: QuickWheelLayoutEngine.DEFAULT_COLUMN_COUNT,
        )
    }

    // ── 轮盘元数据 ──────────────────────────────────────

    fun encodeEntry(wheel: QuickWheel): String = buildString {
        append(wheel.id)
        append(FIELD_SEP)
        append(wheel.primaryShape.name)
        append(FIELD_SEP)
        append(wheel.secondaryShape.name)
        append(FIELD_SEP)
        // 每级同时存"当前形态"与"另一形态"两套参数 → 切换形态不丢参数。
        val primaryAlt = if (wheel.primaryShape == QuickWheelShape.CIRCLE) {
            wheel.primaryRectStyle
        } else {
            wheel.primaryCircleStyle
        }
        val secondaryAlt = if (wheel.secondaryShape == QuickWheelShape.CIRCLE) {
            wheel.secondaryRectStyle
        } else {
            wheel.secondaryCircleStyle
        }
        append(
            encodeStyleWithColumn(wheel.primaryStyle) + STYLE_ALT_SEP +
                encodeStyleWithColumn(primaryAlt),
        )
        append(FIELD_SEP)
        append(
            encodeStyleWithColumn(wheel.secondaryStyle) + STYLE_ALT_SEP +
                encodeStyleWithColumn(secondaryAlt),
        )
        append(FIELD_SEP)
        append(wheel.order)
        append(FIELD_SEP)
        // 存储用"原样掩码"：归一化只用于渲染，写进编码里会把用户选的三扇区 / 对角两扇区吃掉。
        append(QuickWheelLayoutEngine.clampSectorMask(wheel.sectorMask))
        append(FIELD_SEP)
        append(QuickWheelLayoutEngine.clampLongPressMs(wheel.longPressMs))
        append(FIELD_SEP)
        // 呼出时的背景（容器以外区域）模糊 / 遮罩；名称必须保持最后一段以容纳分隔符。
        append(QuickWheelLayoutEngine.clampBackdropBlurDp(wheel.backdropBlurDp))
        append(FIELD_SEP)
        append(QuickWheelLayoutEngine.clampBackdropDimPercent(wheel.backdropDimPercent))
        append(FIELD_SEP)
        // 呼出动画：效果 + 速度（名称仍必须是最后一段）
        append(wheel.openAnimation.name)
        append(FIELD_SEP)
        append(QuickWheelLayoutEngine.clampAnimationSpeedPercent(wheel.animationSpeedPercent))
        append(FIELD_SEP)
        // 名称放最后：允许用户名称里出现分隔符
        append(wheel.name)
    }

    fun decodeEntry(raw: String): QuickWheel? {
        val parts = raw.split(FIELD_SEP, limit = 13)
        if (parts.size < 7) return null
        val id = parts[0].takeIf { it.isNotBlank() } ?: return null
        // 兼容旧记录（按段数判有无；名称恒为最后一段）：
        // 13 段 = 含 openAnimation / animationSpeedPercent（当前）；
        // 11 段 = 含 backdropBlurDp / backdropDimPercent；
        // 9 段 = 含 longPressMs；8 段 = 含 sectorMask；7 段 = 无（视为整圆全选 + 默认长按时间）。
        val hasLongPress = parts.size >= 9
        val hasSector = parts.size >= 8
        val hasBackdrop = parts.size >= 11
        val hasAnimation = parts.size >= 13
        val primaryShape = QuickWheelShape.fromName(parts[1])
        val secondaryShape = QuickWheelShape.fromName(parts[2])
        val (primaryStyle, primaryAltStyle) = decodeStylePair(parts[3])
        val (secondaryStyle, secondaryAltStyle) = decodeStylePair(parts[4])
        return QuickWheel(
            id = id,
            name = when {
                hasAnimation -> parts[12]
                hasBackdrop -> parts[10]
                hasLongPress -> parts[8]
                hasSector -> parts[7]
                else -> parts[6]
            },
            primaryShape = primaryShape,
            secondaryShape = secondaryShape,
            primaryStyle = primaryStyle,
            secondaryStyle = secondaryStyle,
            // 当前形态那套 = active，另一形态 = alt（旧记录两者相同）。
            primaryCircleStyle = if (primaryShape == QuickWheelShape.CIRCLE) {
                primaryStyle
            } else {
                primaryAltStyle
            },
            primaryRectStyle = if (primaryShape == QuickWheelShape.CIRCLE) {
                primaryAltStyle
            } else {
                primaryStyle
            },
            secondaryCircleStyle = if (secondaryShape == QuickWheelShape.CIRCLE) {
                secondaryStyle
            } else {
                secondaryAltStyle
            },
            secondaryRectStyle = if (secondaryShape == QuickWheelShape.CIRCLE) {
                secondaryAltStyle
            } else {
                secondaryStyle
            },
            order = parts[5].toIntOrNull() ?: 0,
            sectorMask = if (hasSector) {
                // 读回来也原样保留（只裁位），形态归一化交给渲染路径。
                QuickWheelLayoutEngine.clampSectorMask(
                    parts[6].toIntOrNull() ?: QuickWheelLayoutEngine.SECTOR_ALL,
                )
            } else {
                QuickWheelLayoutEngine.SECTOR_ALL
            },
            longPressMs = if (hasLongPress) {
                parts[7].toIntOrNull() ?: QuickWheelLayoutEngine.DEFAULT_LONG_PRESS_MS
            } else {
                QuickWheelLayoutEngine.DEFAULT_LONG_PRESS_MS
            },
            backdropBlurDp = if (hasBackdrop) {
                QuickWheelLayoutEngine.clampBackdropBlurDp(parts[8].toIntOrNull() ?: 0)
            } else {
                QuickWheelLayoutEngine.DEFAULT_BACKDROP_BLUR_DP
            },
            backdropDimPercent = if (hasBackdrop) {
                QuickWheelLayoutEngine.clampBackdropDimPercent(parts[9].toIntOrNull() ?: 0)
            } else {
                QuickWheelLayoutEngine.DEFAULT_BACKDROP_DIM_PERCENT
            },
            openAnimation = if (hasAnimation) {
                QuickWheelOpenAnimation.fromName(parts[10])
            } else {
                QuickWheelLayoutEngine.DEFAULT_OPEN_ANIMATION
            },
            animationSpeedPercent = if (hasAnimation) {
                QuickWheelLayoutEngine.clampAnimationSpeedPercent(
                    parts[11].toIntOrNull()
                        ?: QuickWheelLayoutEngine.DEFAULT_ANIMATION_SPEED_PERCENT,
                )
            } else {
                QuickWheelLayoutEngine.DEFAULT_ANIMATION_SPEED_PERCENT
            },
            slots = emptyList(),
        )
    }

    // ── 容器 ────────────────────────────────────────────

    fun encodeSlot(wheelId: String, path: String, slot: QuickWheelSlot): String {
        val header = buildString {
            append(wheelId)
            append(FIELD_SEP)
            append(path)
            append(FIELD_SEP)
            // 2 = 用户主动留出的空位（placeholder）；1 = 历史遗留的空容器（读回后按普通空位处理，会被 normalized 清掉）。
            append(if (slot.placeholder) 2 else if (slot.isEmpty) 1 else 0)
            append(FIELD_SEP)
            append(slot.iconSource.name)
            append(FIELD_SEP)
            append(slot.iconValue)
            append(FIELD_SEP)
            append(slot.tapTrigger.name)
            append(FIELD_SEP)
            append(slot.longPressTrigger.name)
            append(FIELD_SEP)
            // 容器「打开方式」（单击 / 长按各一份）：**只要有一项不是默认**就写这两段，保证列位置固定；
            // 两项都是"跟随"时不写 —— 默认容器 / 旧记录因此不会多出字段，
            // 旧版本读到的名字仍是干净的名字（新版按段数判有无，与 decodeEntry 同一套约定）。
            // 名称仍必须是 header 最后一段。
            if (slot.tapLaunchMode != QuickWheelLaunchMode.INHERIT ||
                slot.longPressLaunchMode != QuickWheelLaunchMode.INHERIT
            ) {
                append(slot.tapLaunchMode.name)
                append(FIELD_SEP)
                append(slot.longPressLaunchMode.name)
                append(FIELD_SEP)
            }
            // 名称放 header 最后
            append(slot.name)
        }
        return buildString {
            append(header)
            append(ACTION_SEP)
            append(QuickLauncherItemCodec.encodeActionPayload(slot.tapAction))
            append(ACTION_SEP)
            append(QuickLauncherItemCodec.encodeActionPayload(slot.longPressAction))
        }
    }

    /** @return (wheelId, path, slot) */
    fun decodeSlot(raw: String): Triple<String, String, QuickWheelSlot>? {
        // 用「首个 / 末个」ACTION_SEP 定位，而不是 split——动作载荷自身可能含有 ACTION_SEP
        // （例如包名 / 路径里混入控制字符），split 会把它切碎导致动作回退成 None。
        val firstSep = raw.indexOf(ACTION_SEP)
        val lastSep = raw.lastIndexOf(ACTION_SEP)
        if (firstSep <= 0 || lastSep <= firstSep) return null
        val header = raw.substring(0, firstSep).split(FIELD_SEP, limit = 10)
        if (header.size < 8) return null
        val wheelId = header[0].takeIf { it.isNotBlank() } ?: return null
        val path = header[1].takeIf { it.isNotBlank() } ?: return null
        val tapRaw = raw.substring(firstSep + 1, lastSep)
        val longRaw = raw.substring(lastSep + 1)
        // 第 3 列（header[2]）：2 = 用户主动留出的空位（placeholder）；1 = 历史遗留空容器（按普通空位处理，会被 normalized 清掉）。
        // 第 8 段起（header[7..]）只有"容器自定义了打开方式"时才有：
        // 10 段 = 单击 / 长按各一份打开方式，名称顺延到第 10 段；
        //  9 段 = 早期"整容器一份打开方式"的记录（两项同值，名称在第 9 段）；
        //  8 段 = 旧记录（两项均为"跟随"）。
        val hasLaunchMode = header.size >= 9
        val storedTapMode = if (hasLaunchMode) {
            QuickWheelLaunchMode.fromName(header[7])
        } else {
            QuickWheelLaunchMode.INHERIT
        }
        val storedLongMode = when {
            header.size >= 10 -> QuickWheelLaunchMode.fromName(header[8])
            hasLaunchMode -> storedTapMode
            else -> QuickWheelLaunchMode.INHERIT
        }
        // 容器是「打开方式」的**唯一**来源：早期版本把"打开应用"的形态存在**动作**上（上游那套），
        // 读回时把它"收编"进容器字段，并把动作那一段清成"跟随"，避免两处各说各话。
        val tapActionRaw = QuickLauncherItemCodec.parseActionPayload(tapRaw) ?: GestureAction.None
        val longActionRaw = QuickLauncherItemCodec.parseActionPayload(longRaw) ?: GestureAction.None
        val tapLaunchMode = storedTapMode.takeIf { it != QuickWheelLaunchMode.INHERIT }
            ?: (tapActionRaw as? GestureAction.LaunchApp)?.windowMode?.toQuickWheelLaunchMode()
            ?: QuickWheelLaunchMode.INHERIT
        val longPressLaunchMode = storedLongMode.takeIf { it != QuickWheelLaunchMode.INHERIT }
            ?: (longActionRaw as? GestureAction.LaunchApp)?.windowMode?.toQuickWheelLaunchMode()
            ?: QuickWheelLaunchMode.INHERIT
        val slot = QuickWheelSlot(
            placeholder = header[2] == "2",
            iconSource = QuickWheelIconSource.fromName(header[3]),
            iconValue = header[4],
            tapTrigger = QuickWheelTapTrigger.fromName(header[5]),
            longPressTrigger = QuickWheelLongPressTrigger.fromName(header[6]),
            tapLaunchMode = tapLaunchMode,
            longPressLaunchMode = longPressLaunchMode,
            name = when {
                header.size >= 10 -> header[9]
                hasLaunchMode -> header[8]
                else -> header[7]
            },
            tapAction = tapActionRaw.withoutLaunchWindowMode(),
            longPressAction = longActionRaw.withoutLaunchWindowMode(),
            subSlots = emptyList(),
        )
        return Triple(wheelId, path, slot)
    }

    /** 一级/二级的"另一形态"样式分隔符（与 [STYLE_SEP] 不同，避免解析冲突）。 */
    private const val STYLE_ALT_SEP = "\u001C"

    /** 编码一套样式，并在末尾追加矩形列数。 */
    private fun encodeStyleWithColumn(style: QuickWheelStyleSpec): String =
        encodeStyle(style) + STYLE_SEP + style.columnCount

    /**
     * 解码"当前形态样式 + 另一形态样式"。
     *
     * 旧记录只有一段 → 另一形态直接回落到当前样式（保证升级后不丢当前效果）。
     */
    private fun decodeStylePair(raw: String): Pair<QuickWheelStyleSpec, QuickWheelStyleSpec> {
        val parts = raw.split(STYLE_ALT_SEP, limit = 2)
        val active = decodeStyle(parts[0])
        val alternate = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(::decodeStyle) ?: active
        return active to alternate
    }

    /** 把一棵轮盘摊平成 (path, slot) 列表。 */
    fun flatten(wheel: QuickWheel): List<Pair<String, QuickWheelSlot>> = buildList {
        add(PATH_CENTER to wheel.centerSlot.copy(subSlots = emptyList()))
        wheel.slots.forEachIndexed { index, slot ->
            add(primaryPath(index) to slot.copy(subSlots = emptyList()))
            slot.subSlots.forEachIndexed { subIndex, sub ->
                add(secondaryPath(index, subIndex) to sub.copy(subSlots = emptyList()))
            }
        }
    }

    fun decode(prefs: Preferences): List<QuickWheel> {
        val rawEntries = prefs[SettingsPreferenceKeys.QUICK_WHEEL_ENTRIES] ?: emptySet()
        if (rawEntries.isEmpty()) return emptyList()
        val metas = rawEntries
            .mapNotNull(::decodeEntry)
            .sortedBy { it.order }
            .take(QuickWheel.MAX_WHEELS)
        if (metas.isEmpty()) return emptyList()

        val records = HashMap<String, MutableList<Pair<String, QuickWheelSlot>>>()
        (prefs[SettingsPreferenceKeys.QUICK_WHEEL_SLOTS] ?: emptySet()).forEach { raw ->
            val parsed = decodeSlot(raw) ?: return@forEach
            records.getOrPut(parsed.first) { mutableListOf() } += parsed.second to parsed.third
        }

        return metas.mapIndexed { position, meta ->
            val list = records[meta.id].orEmpty()
            val center = list.firstOrNull { isCenterPath(it.first) }?.second ?: QuickWheelSlot()
            val primary = mutableListOf<QuickWheelSlot>()
            val secondary = HashMap<Int, MutableList<Pair<Int, QuickWheelSlot>>>()

            list.forEach { (path, slot) ->
                when {
                    isCenterPath(path) -> Unit

                    isSecondaryPath(path) -> {
                        val parent = parseSecondaryParent(path)
                        val subIndex = parseSecondaryIndex(path)
                        // 越界 / 负数一律丢弃：这里是把持久化数据摊成内存结构的唯一入口，
                        // 旧版本或损坏的备份里出现 `0:0>1:-1` 之类不能按原样收下。
                        if (parent != null && parent in 0 until QuickWheel.MAX_SLOTS &&
                            subIndex != null && subIndex >= 0
                        ) {
                            secondary.getOrPut(parent) { mutableListOf() } += subIndex to slot
                        }
                    }

                    else -> {
                        val index = parsePrimaryIndex(path) ?: return@forEach
                        // ⚠️ 必须先判范围再按索引补位：`0:2000000000` 会在这里分配二十亿个容器
                        //（OOM），`0:-1` 会让 primary[-1] 抛 IndexOutOfBounds。两者都只来自
                        // 被改坏的存档 / 导入的备份，但 decode 每次读快照都会跑，一旦落盘就是
                        // 「打开设置即崩」的死循环，所以宁可丢弃也不放大。
                        if (index !in 0 until QuickWheel.MAX_SLOTS) return@forEach
                        while (primary.size <= index) primary += QuickWheelSlot()
                        primary[index] = slot
                    }
                }
            }

            val slots = primary.mapIndexed { index, slot ->
                val subs = secondary[index]?.sortedBy { it.first }?.map { it.second }.orEmpty()
                if (subs.isEmpty()) slot else slot.copy(subSlots = subs)
            }

            meta.copy(
                order = position,
                centerSlot = center,
                slots = slots,
            ).normalized()
        }
    }

    fun writeToPreferences(wheels: List<QuickWheel>, prefs: MutablePreferences) {
        val limited = wheels.take(QuickWheel.MAX_WHEELS).mapIndexed { index, wheel ->
            wheel.copy(order = index).normalized()
        }
        prefs[SettingsPreferenceKeys.QUICK_WHEEL_ENTRIES] = limited.map(::encodeEntry).toSet()
        prefs[SettingsPreferenceKeys.QUICK_WHEEL_SLOTS] = limited.flatMap { wheel ->
            flatten(wheel).map { (path, slot) -> encodeSlot(wheel.id, path, slot) }
        }.toSet()
    }
}

// ── 按路径读写容器 ──────────────────────────────────────
// path 语义见 [QuickWheelCodec]：`C` / `0:<i>` / `0:<i>>1:<j>`

/** 取 [path] 对应的容器；不存在返回 null。 */
fun QuickWheel.slotAt(path: String): QuickWheelSlot? = when {
    QuickWheelCodec.isCenterPath(path) -> centerSlot
    QuickWheelCodec.isSecondaryPath(path) -> {
        val parent = QuickWheelCodec.parseSecondaryParent(path)
        val index = QuickWheelCodec.parseSecondaryIndex(path)
        if (parent == null || index == null) null
        else slots.getOrNull(parent)?.subSlots?.getOrNull(index)
    }

    else -> QuickWheelCodec.parsePrimaryIndex(path)?.let { slots.getOrNull(it) }
}

/** 就地更新 [path] 对应的容器。 */
fun QuickWheel.updateSlot(path: String, transform: (QuickWheelSlot) -> QuickWheelSlot): QuickWheel = when {
    QuickWheelCodec.isCenterPath(path) -> copy(centerSlot = transform(centerSlot))

    QuickWheelCodec.isSecondaryPath(path) -> {
        val parent = QuickWheelCodec.parseSecondaryParent(path)
        val index = QuickWheelCodec.parseSecondaryIndex(path)
        if (parent == null || index == null) {
            this
        } else {
            copy(
                slots = slots.mapIndexed { slotIndex, slot ->
                    if (slotIndex != parent) {
                        slot
                    } else if (
                        index == slot.subSlots.size &&
                        slot.subSlots.size < QuickWheel.MAX_SUB_SLOTS
                    ) {
                        // 末尾「+」新建二级容器 → 追加
                        slot.copy(subSlots = slot.subSlots + transform(QuickWheelSlot()))
                    } else {
                        slot.copy(
                            subSlots = slot.subSlots.mapIndexed { subIndex, sub ->
                                if (subIndex == index) transform(sub) else sub
                            },
                        )
                    }
                },
            )
        }
    }

    else -> {
        val index = QuickWheelCodec.parsePrimaryIndex(path)
        when {
            index == null -> this
            // `index == slots.size`：编辑层末尾的「+」新建容器 → 追加到末尾
            index == slots.size && slots.size < QuickWheel.MAX_SLOTS ->
                copy(slots = slots + transform(QuickWheelSlot()))

            index !in slots.indices -> this
            else -> copy(
                slots = slots.mapIndexed { slotIndex, slot ->
                    if (slotIndex == index) transform(slot) else slot
                },
            )
        }
    }
}

/** 追加一个一级空白占位。 */
fun QuickWheel.withPrimaryPlaceholderAppended(): QuickWheel =
    if (slots.size >= QuickWheel.MAX_SLOTS) this else copy(slots = slots + QuickWheelSlot())

/**
 * 追加一个「空白占位」——紧跟在最后一个容器之后。
 *
 * 该位置在真实呼出时完全透明（不绘制任何东西），只在预览 / 配置里显示为"空白阴影"，
 * 因此用 [QuickWheelSlot.placeholder] 标记，避免被 [QuickWheel.normalized] 当作历史遗留空容器清掉。
 */
fun QuickWheel.withGapInserted(): QuickWheel =
    if (slots.size >= QuickWheel.MAX_SLOTS) {
        this
    } else {
        copy(slots = slots + QuickWheelSlot(placeholder = true))
    }

/** 删除末尾的一级容器。 */
fun QuickWheel.withLastPrimaryRemoved(): QuickWheel =
    if (slots.isEmpty()) this else copy(slots = slots.dropLast(1))

/** 删除指定一级容器。 */
fun QuickWheel.withPrimaryRemovedAt(index: Int): QuickWheel =
    if (index !in slots.indices) this else copy(slots = slots.filterIndexed { i, _ -> i != index })

/** 一级容器换位。 */
fun QuickWheel.withPrimaryMoved(from: Int, to: Int): QuickWheel {
    if (from == to || from !in slots.indices || to !in slots.indices) return this
    val mutable = slots.toMutableList()
    val moved = mutable.removeAt(from)
    mutable.add(to, moved)
    return copy(slots = mutable)
}

/** 给某个一级容器追加一个二级空白占位。 */
fun QuickWheel.withSecondaryPlaceholderAppended(primaryIndex: Int): QuickWheel {
    if (primaryIndex !in slots.indices) return this
    return copy(
        slots = slots.mapIndexed { index, slot ->
            if (index != primaryIndex || slot.subSlots.size >= QuickWheel.MAX_SUB_SLOTS) {
                slot
            } else {
                slot.copy(subSlots = slot.subSlots + QuickWheelSlot())
            }
        },
    )
}

/** 删除某个一级容器下末尾的二级容器。 */
fun QuickWheel.withLastSecondaryRemoved(primaryIndex: Int): QuickWheel {
    if (primaryIndex !in slots.indices) return this
    return copy(
        slots = slots.mapIndexed { index, slot ->
            if (index != primaryIndex || slot.subSlots.isEmpty()) slot
            else slot.copy(subSlots = slot.subSlots.dropLast(1))
        },
    )
}

/** 删除某个一级容器下的指定二级容器。 */
fun QuickWheel.withSecondaryRemovedAt(primaryIndex: Int, index: Int): QuickWheel {
    if (primaryIndex !in slots.indices) return this
    return copy(
        slots = slots.mapIndexed { slotIndex, slot ->
            if (slotIndex != primaryIndex || index !in slot.subSlots.indices) {
                slot
            } else {
                slot.copy(subSlots = slot.subSlots.filterIndexed { subIndex, _ -> subIndex != index })
            }
        },
    )
}

/** 某个一级容器下的二级容器换位。 */
fun QuickWheel.withSecondaryMoved(primaryIndex: Int, from: Int, to: Int): QuickWheel {
    if (primaryIndex !in slots.indices) return this
    return copy(
        slots = slots.mapIndexed { slotIndex, slot ->
            if (slotIndex != primaryIndex) return@mapIndexed slot
            if (from == to || from !in slot.subSlots.indices || to !in slot.subSlots.indices) {
                return@mapIndexed slot
            }
            val mutable = slot.subSlots.toMutableList()
            mutable.add(to, mutable.removeAt(from))
            slot.copy(subSlots = mutable)
        },
    )
}

/** 切换一级轮盘的某个扇区。 */
fun QuickWheel.withSectorToggled(index: Int): QuickWheel =
    copy(sectorMask = QuickWheelLayoutEngine.toggleSector(sectorMask, index))
