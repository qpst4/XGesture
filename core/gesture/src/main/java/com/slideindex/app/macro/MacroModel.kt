package com.slideindex.app.macro

/**
 * 手势宏（Macro）：把一串「等待 / 点击 / 滑动」记录下来，之后用一个手势在目标 App 里重放。
 *
 * 这一层是**纯数据**（不依赖 Android），方便单测与导入导出。
 * 坐标一律使用屏幕宽/高的**比例**（0f..1f），重放时再乘以真实分辨率 —— 这样换分辨率/方向也不会跑偏。
 */
sealed interface MacroStep {

    /** 纯等待，用于给目标 App 留出加载/动画时间。 */
    data class Wait(val durationMs: Long = DEFAULT_WAIT_MS) : MacroStep

    /**
     * 单击一下。
     *
     * 坐标取**按下点**（与录制时用户手指落下的位置一致）。
     */
    data class Tap(
        val x: Float = 0.5f,
        val y: Float = 0.5f,
        val durationMs: Long = DEFAULT_TAP_MS,
    ) : MacroStep

    /**
     * 滑动。
     *
     * [points] 是完整轨迹，**包含首尾**：`[0]` 是按下点、最后一个点是抬起点。
     * 重放时首点会被起点覆盖，所以实际连线用的是 `points.drop(1)`；
     * 只有当 `points.size < 2` 时才退化成「起点 → 终点」的直线。
     */
    data class Swipe(
        val startX: Float = 0.5f,
        val startY: Float = 0.8f,
        val endX: Float = 0.5f,
        val endY: Float = 0.2f,
        val durationMs: Long = DEFAULT_SWIPE_MS,
        val points: List<PathPoint> = emptyList(),
    ) : MacroStep {
        data class PathPoint(val x: Float = 0.5f, val y: Float = 0.5f)
    }

    companion object {
        const val DEFAULT_WAIT_MS = 200L
        const val DEFAULT_TAP_MS = 80L
        const val DEFAULT_SWIPE_MS = 250L
    }
}

/** 宏的作用域（反馈 #394）。 */
enum class MacroScope {
    /** 只在目标 App 内跑：执行前把它拉到前台，中途离开就中止（历史行为）。 */
    APP,

    /** 全局宏：不绑定 App，直接在当前界面回放。 */
    GLOBAL,
    ;

    companion object {
        fun fromName(raw: String?): MacroScope =
            entries.firstOrNull { it.name == raw } ?: APP
    }
}

/** 一条宏：绑定一个目标 App，由若干步按顺序组成。 */
data class Macro(
    val id: String,
    val name: String,
    /** 宏只在它自己的目标 App 处于前台时执行（执行前会把它拉到前台）。 */
    val targetPackage: String,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val steps: List<MacroStep> = emptyList(),
    /** 作用域；缺省 APP = 老数据行为不变。 */
    val scope: MacroScope = MacroScope.APP,
) {

    /**
     * 是否全局宏。
     *
     * 除了显式声明 GLOBAL，**没有目标应用**的宏也按全局处理 —— 否则它既不会拉起 App
     * 也不会命中前台校验，等于永远跑不起来。
     */
    val isGlobal: Boolean
        get() = scope == MacroScope.GLOBAL || targetPackage.isBlank()

    /** 会真正产生手势的步数（不含等待）—— 列表里显示「名字 - N 个动作」用这个。 */
    val actionCount: Int
        get() = steps.count { it !is MacroStep.Wait }

    /** 等待步数。 */
    val waitCount: Int
        get() = steps.count { it is MacroStep.Wait }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2

        /** 名字为空时的兜底名。 */
        const val FALLBACK_NAME = "未命名宏"
    }
}

/** 执行期的取值范围（解析阶段不裁剪，只有执行时才收进这些区间）。 */
object MacroLimits {
    const val WAIT_MIN_MS = 0L
    const val WAIT_MAX_MS = 10_000L
    const val TAP_MIN_MS = 1L
    const val TAP_MAX_MS = 2_000L
    const val SWIPE_MIN_MS = 50L
    const val SWIPE_MAX_MS = 5_000L

    fun clampWait(durationMs: Long): Long = durationMs.coerceIn(WAIT_MIN_MS, WAIT_MAX_MS)

    fun clampTap(durationMs: Long): Long = durationMs.coerceIn(TAP_MIN_MS, TAP_MAX_MS)

    fun clampSwipe(durationMs: Long): Long = durationMs.coerceIn(SWIPE_MIN_MS, SWIPE_MAX_MS)

    /** 坐标比例收进 0..1。 */
    fun clampFraction(value: Float): Float = value.coerceIn(0f, 1f)
}
