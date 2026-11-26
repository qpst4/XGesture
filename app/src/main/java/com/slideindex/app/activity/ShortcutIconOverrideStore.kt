package com.slideindex.app.activity

import android.content.Context

/**
 * 「创建快捷方式」得到、但不在「应用内直达」目录里的快捷方式，按身份 key 单独存图标。
 *
 * key 的取值与 [ManagedShortcutIconResolver] 匹配用的身份一致：
 * - LaunchShortcut：`payloadKey`（intent:... / package/component / dynamic）
 * - QuickLauncherItem：`QuickLauncherItemCodec.shortcutItemKey(item)`
 */
object ShortcutIconOverrideStore {

    private const val PREFS = "shortcut_icon_overrides"

    fun iconPath(context: Context, key: String?): String? {
        if (key.isNullOrBlank()) return null
        return prefs(context).getString(key, null)?.takeIf { it.isNotBlank() }
    }

    fun put(context: Context, key: String?, path: String?) {
        if (key.isNullOrBlank() || path.isNullOrBlank()) return
        prefs(context).edit().putString(key, path).apply()
    }

    fun remove(context: Context, key: String?) {
        if (key.isNullOrBlank()) return
        prefs(context).edit().remove(key).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
