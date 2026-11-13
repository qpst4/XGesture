package com.slideindex.app.gesture

/**
 * 「切换类动作」的操作方式。
 *
 * - [TOGGLE]：看当前状态取反（历史行为，默认值）；
 * - [ON] / [OFF]：幂等目标态——已经是该状态时什么都不做，不是则设成该状态。
 *
 * 编码进动作 payload：`TOGGLE` = 空串、`ON` = `"1"`、`OFF` = `"0"`。
 * **空串与旧数据完全一致**（旧动作的 payload 本来就是空），因此不需要任何设置迁移。
 */
enum class ToggleMode(val encoded: String) {
    TOGGLE(""),
    ON("1"),
    OFF("0");

    companion object {
        /** 未知 / 空串一律按 [TOGGLE]（旧数据、手改存档都不会让动作失效）。 */
        fun decode(raw: String): ToggleMode = when (raw.trim()) {
            ON.encoded -> ON
            OFF.encoded -> OFF
            else -> TOGGLE
        }
    }
}

/** 把「切换/打开/关闭」换算成"设成开还是关"；[current] 只在 [ToggleMode.TOGGLE] 时用得上。 */
fun ToggleMode.resolve(current: Boolean): Boolean = when (this) {
    ToggleMode.ON -> true
    ToggleMode.OFF -> false
    ToggleMode.TOGGLE -> !current
}

/**
 * 带「操作方式」的动作（WiFi / 移动数据 / 蓝牙 / NFC）。
 *
 * 做成接口而不是给 [GestureAction] 基类加字段：只有这几个动作有这个概念，
 * 其它动作的 payload 语义各不相同，塞到基类会让所有序列化路径都要多带一个字段。
 */
interface ToggleCapable {
    val mode: ToggleMode

    /** 同一动作换一个模式（保留其它参数）。 */
    fun withToggleMode(mode: ToggleMode): GestureAction
}

/** 非切换类动作返回 null。 */
fun GestureAction.toggleModeOrNull(): ToggleMode? = (this as? ToggleCapable)?.mode

/** 非切换类动作原样返回。 */
fun GestureAction.withToggleModeOrSelf(mode: ToggleMode): GestureAction =
    (this as? ToggleCapable)?.withToggleMode(mode) ?: this
