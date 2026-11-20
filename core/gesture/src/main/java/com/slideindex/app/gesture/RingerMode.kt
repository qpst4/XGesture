package com.slideindex.app.gesture

/**
 * 「声音模式」的操作方式。
 *
 * 与其它开关的 [ToggleMode] 不同：它不是「开/关」，而是系统真实的三种铃声状态。
 *
 * - [TOGGLE]：在 [RING] → [VIBRATE] → [SILENT] → [RING] 之间循环（旧绑定的空 payload）；
 * - [RING] / [VIBRATE] / [SILENT]：**开关式目标档**——不在该档时切到该档，已在该档时
 *   再触发一次就退出（退出目标见 [resolve]）。
 *
 * 显式档做成「进/出」而不是幂等锁定：用户把它当静音开关用，第二下要能取消静音，
 * 而不是按了没反应。
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

/**
 * 触发一次之后要切到的档位（不返回 [RingerMode.TOGGLE]，它是操作方式而不是系统状态）。
 *
 * - [RingerMode.TOGGLE]：三档循环的下一档，与旧「切换」行为一致；
 * - 三个显式档：[current] 不是该档 → 进入该档；已经是该档 → 退出到「响铃」
 *   （[RingerMode.RING] 自己就是响铃，所以它退出到 [RingerMode.SILENT]，永远往安静的方向走）。
 *
 * 退出目标固定、不记录「上一档」：没有跨进程状态，进程被杀也不会让行为漂移。
 */
fun RingerMode.resolve(current: RingerMode): RingerMode = when (this) {
    RingerMode.TOGGLE -> current.cycleNext()
    RingerMode.RING, RingerMode.VIBRATE, RingerMode.SILENT ->
        if (current == this) exitRingerMode() else this
}

/** 退出某个显式档：静音 / 振动回到响铃；响铃只能往安静的方向走到静音。 */
fun RingerMode.exitRingerMode(): RingerMode =
    if (this == RingerMode.RING) RingerMode.SILENT else RingerMode.RING

/** 三档循环的下一档；传入 [RingerMode.TOGGLE] 时按「响铃」处理（它不是系统状态）。 */
fun RingerMode.cycleNext(): RingerMode = when (this) {
    RingerMode.RING -> RingerMode.VIBRATE
    RingerMode.VIBRATE -> RingerMode.SILENT
    RingerMode.SILENT, RingerMode.TOGGLE -> RingerMode.RING
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
