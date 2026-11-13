package com.slideindex.app.gesture

/**
 * 「声音模式」的操作方式。
 *
 * 与其它开关的 [ToggleMode] 不同：它不是「开/关」，而是系统真实的三种铃声状态。
 *
 * - [TOGGLE]：在 [RING] → [VIBRATE] → [SILENT] → [RING] 之间循环（旧绑定的空 payload）；
 * - [RING] / [VIBRATE] / [SILENT]：幂等目标态。
 *
 * 编码进动作 payload：`TOGGLE` = 空串、`RING` = `"1"`、`VIBRATE` = `"2"`、`SILENT` = `"3"`。
 * 空串与旧数据一致（旧「响铃静音开关」= 切换），不需要迁移；只是「切换」本身从旧的二态
 * 取反改成了三模式循环。
 */
enum class RingerMode(val encoded: String) {
    TOGGLE(""),
    RING("1"),
    VIBRATE("2"),
    SILENT("3");

    companion object {
        /** 未知 / 空串一律按 [TOGGLE]（旧数据、手改存档都不会让动作失效）。 */
        fun decode(raw: String): RingerMode = when (raw.trim()) {
            RING.encoded -> RING
            VIBRATE.encoded -> VIBRATE
            SILENT.encoded -> SILENT
            else -> TOGGLE
        }
    }
}

/** 带「声音模式」的动作（目前只有「声音模式」一个）。 */
interface RingerCapable {
    val ringerMode: RingerMode

    fun withRingerMode(mode: RingerMode): GestureAction
}

/** 非声音模式动作返回 null。 */
fun GestureAction.ringerModeOrNull(): RingerMode? = (this as? RingerCapable)?.ringerMode

/** 非声音模式动作原样返回。 */
fun GestureAction.withRingerModeOrSelf(mode: RingerMode): GestureAction =
    (this as? RingerCapable)?.withRingerMode(mode) ?: this

/**
 * 是否带「操作方式」参数（三态或四态）。
 *
 * 面板添加时按这个判「动作族」去重：同一个开关只留一个模式。
 */
fun GestureAction.isModeCapable(): Boolean = this is ToggleCapable || this is RingerCapable
