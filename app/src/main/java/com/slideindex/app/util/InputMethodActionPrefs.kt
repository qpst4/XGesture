package com.slideindex.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 「循环切换输入法」「切换输入法」两个动作的目标输入法偏好。
 *
 * 空串 = 未指定：循环动作按启用顺序轮换，切换动作弹系统选择器（两者都是加这个设置前的行为）。
 * 只存 [android.view.inputmethod.InputMethodInfo.id]，不存显示名。
 */
object InputMethodActionPrefs {
    const val PREFS_NAME = "input_method_actions"
    const val KEY_CYCLE_TARGET = "cycle_target_ime"
    const val KEY_CYCLE_PREVIOUS = "cycle_previous_ime"
    const val KEY_CYCLE_EXCLUDED = "cycle_excluded_imes"
    const val KEY_SWITCH_TARGET = "switch_target_ime"

    fun readCycleTarget(context: Context): String = read(context, KEY_CYCLE_TARGET)

    /** 换目标时顺手清掉「回切」记录，避免下次按回一个早就无关的输入法。 */
    fun writeCycleTarget(context: Context, imeId: String) {
        write(context) { editor ->
            editor.putString(KEY_CYCLE_TARGET, imeId)
            editor.putString(KEY_CYCLE_PREVIOUS, "")
        }
    }

    /** 上一次从哪个输入法切到目标，用于在目标上再按一下时切回去。 */
    fun readCyclePrevious(context: Context): String = read(context, KEY_CYCLE_PREVIOUS)

    fun writeCyclePrevious(context: Context, imeId: String) {
        write(context) { it.putString(KEY_CYCLE_PREVIOUS, imeId) }
    }

    /** 「全部」轮换时要跳过的输入法 id；只影响轮换，明确选了目标输入法时不受影响。 */
    fun readCycleExcluded(context: Context): Set<String> = runCatching {
        prefs(context).getStringSet(KEY_CYCLE_EXCLUDED, emptySet())?.toSet().orEmpty()
    }.getOrDefault(emptySet())

    fun writeCycleExcluded(context: Context, imeIds: Set<String>) {
        write(context) { it.putStringSet(KEY_CYCLE_EXCLUDED, imeIds) }
    }

    fun readSwitchTarget(context: Context): String = read(context, KEY_SWITCH_TARGET)

    fun writeSwitchTarget(context: Context, imeId: String) {
        write(context) { it.putString(KEY_SWITCH_TARGET, imeId) }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun read(context: Context, key: String): String = runCatching {
        prefs(context).getString(key, "").orEmpty()
    }.getOrDefault("")

    private fun write(context: Context, block: (SharedPreferences.Editor) -> Unit) {
        runCatching {
            val editor = prefs(context).edit()
            block(editor)
            editor.apply()
        }
    }
}
