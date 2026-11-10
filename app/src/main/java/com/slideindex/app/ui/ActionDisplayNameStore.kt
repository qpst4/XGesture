package com.slideindex.app.ui

import android.content.Context
import android.content.SharedPreferences
import com.slideindex.app.gesture.GestureAction

/**
 * 动作的**自定义显示名**（动作详情页头部可重命名）。
 *
 * 为什么按动作内容存、而不是按槽位存：
 * 一个动作（"打开面板 · 默认"）在多个槽位里通常指的是同一件事，用户改一次名字就希望处处生效；
 * 按槽位存会让"同一个动作在不同地方叫不同名字"，反而更难理解。
 *
 * 存储用独立的 SharedPreferences（不进 `AppSettings`/DataStore 管线），原因：
 * 1. 它纯粹是展示层覆盖，不影响任何运行时行为（载荷、解析、执行都不读它）；
 * 2. 走 `AppSettings` 意味着要改设置模型 + 迁移 + 全量测试，收益与风险不成比例。
 *
 * key = `type.id` + 载荷，动作内容一变（换面板 / 改命令）名字自动失效，回到默认显示。
 */
object ActionDisplayNameStore {

    const val PREFS_NAME = "action_display_names"

    private fun sharedPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun keyOf(action: GestureAction): String =
        "${action.type.id}\u0001${action.payload}"

    /** 供图标存储共用同一个动作身份键。 */
    internal fun actionKey(action: GestureAction): String = keyOf(action)

    /** 图标的存储键：与名称同一份 prefs，加后缀区分。 */
    private fun iconKeyOf(action: GestureAction): String = keyOf(action) + "\u0001icon"

    /** 读自定义名；没设过或已失效返回 null。 */
    fun get(context: Context, action: GestureAction): String? {
        val stored: String? = sharedPrefs(context).getString(keyOf(action), null)
        return if (stored.isNullOrBlank()) null else stored
    }

    /** 写自定义名；传 null / 空白 = 清除（回到默认显示）。 */
    fun set(context: Context, action: GestureAction, name: String?) {
        val trimmed = (name ?: "").trim()
        val editor = sharedPrefs(context).edit()
        if (trimmed.isEmpty()) {
            editor.remove(keyOf(action))
        } else {
            editor.putString(keyOf(action), trimmed)
        }
        editor.apply()
    }

    /** 读自定义图标 key；没设过返回 null（用动作自带图标）。 */
    fun getIcon(context: Context, action: GestureAction): String? {
        val stored: String? = sharedPrefs(context).getString(iconKeyOf(action), null)
        return if (stored.isNullOrBlank()) null else stored
    }

    /** 写自定义图标 key；传 null = 恢复动作自带图标。 */
    fun setIcon(context: Context, action: GestureAction, iconKey: String?) {
        val editor = sharedPrefs(context).edit()
        if (iconKey.isNullOrBlank()) {
            editor.remove(iconKeyOf(action))
        } else {
            editor.putString(iconKeyOf(action), iconKey)
        }
        editor.apply()
    }
}
