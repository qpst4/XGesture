package com.slideindex.app.util

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager

/**
 * 一个已启用输入法的展示快照。
 *
 * [id] 是系统 `Settings.Secure.DEFAULT_INPUT_METHOD` 使用的稳定标识（`包名/.服务名`），
 * 配置只存它；[label] 仅用于界面展示，输入法改名/换语言都不影响已保存的配置。
 */
data class EnabledInputMethod(val id: String, val label: String)

/** 系统输入法列表的只读入口：配置页展示与执行器共用同一份口径。 */
object InputMethodCatalog {
    private const val TAG = "InputMethodCatalog"

    /** 当前系统默认输入法 id；读不到时返回空串。 */
    fun currentId(context: Context): String = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )
    }.getOrNull().orEmpty()

    /**
     * 已启用输入法，**保持系统列表顺序**（「全部」轮换也按这个顺序，
     * 避免配置页看到的顺序和实际切换顺序不一致）。
     *
     * 同名输入法（同一应用暴露多个 IME 服务）追加包名区分。
     */
    fun enabled(context: Context): List<EnabledInputMethod> {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        val pm = context.packageManager
        val entries = runCatching {
            imm.enabledInputMethodList.orEmpty().map { info ->
                EnabledInputMethod(
                    id = info.id,
                    label = runCatching { info.loadLabel(pm).toString() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: info.id,
                )
            }
        }.getOrElse { error ->
            Log.w(TAG, "read enabled input methods failed", error)
            return emptyList()
        }
        val duplicatedLabels = entries.groupingBy { it.label }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        return entries.map { entry ->
            if (entry.label !in duplicatedLabels) {
                entry
            } else {
                entry.copy(label = "${entry.label}（${entry.id.substringBefore('/')}）")
            }
        }
    }

    /** 指定输入法当前是否仍处于启用状态。 */
    fun isEnabled(context: Context, imeId: String): Boolean =
        imeId.isNotBlank() && enabled(context).any { it.id == imeId }
}
