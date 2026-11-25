package com.slideindex.app.data

import android.content.Context
import com.slideindex.app.launcher.AppTarget
import org.json.JSONObject

/**
 * 搜索面板里针对「某个应用」的两个本地偏好（反馈 #375）：
 *
 * 1. **从搜索中隐藏**：搜索结果里不再出现该应用（不影响启动器/桌面索引）；
 * 2. **应用别名**：给应用起一个自己的名字，按别名也能搜到它。
 *
 * 两者都按「目标」存（`包名` 或 `包名\u0002userId`），和冰箱/隐藏应用同一套编码，
 * 这样分身与主实例可以分别设置。用独立 SharedPreferences：它只影响搜索结果，
 * 不参与手势执行，也不需要按应用分档。
 */
object SearchAppPrefs {
    const val PREFS_NAME = "search_app_prefs"
    private const val KEY_HIDDEN = "hidden_targets"
    private const val KEY_ALIASES = "aliases"

    fun hiddenTargets(context: Context): Set<String> = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_HIDDEN, emptySet())
            .orEmpty()
            .toSet()
    }.getOrDefault(emptySet())

    fun isHidden(context: Context, packageName: String, cloneUserId: Int = 0): Boolean =
        AppTarget.encode(packageName, cloneUserId) in hiddenTargets(context)

    fun setHidden(context: Context, packageName: String, cloneUserId: Int = 0, hidden: Boolean) {
        runCatching {
            val target = AppTarget.encode(packageName, cloneUserId)
            val updated = hiddenTargets(context).toMutableSet()
            if (hidden) updated += target else updated -= target
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_HIDDEN, updated).apply()
        }
    }

    /** 全部别名：target → 别名。 */
    fun aliases(context: Context): Map<String, String> = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALIASES, null)
            ?: return emptyMap()
        val obj = JSONObject(raw)
        buildMap {
            obj.keys().forEach { key ->
                val value = obj.optString(key).trim()
                if (value.isNotEmpty()) put(key, value)
            }
        }
    }.getOrDefault(emptyMap())

    fun aliasOf(context: Context, packageName: String, cloneUserId: Int = 0): String? =
        aliases(context)[AppTarget.encode(packageName, cloneUserId)]?.takeIf { it.isNotBlank() }

    /** 写别名；空串 = 清除。 */
    fun setAlias(context: Context, packageName: String, cloneUserId: Int = 0, alias: String) {
        runCatching {
            val target = AppTarget.encode(packageName, cloneUserId)
            val updated = aliases(context).toMutableMap()
            val trimmed = alias.trim()
            if (trimmed.isEmpty()) updated.remove(target) else updated[target] = trimmed
            val obj = JSONObject()
            updated.forEach { (key, value) -> obj.put(key, value) }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_ALIASES, obj.toString()).apply()
        }
    }
}
