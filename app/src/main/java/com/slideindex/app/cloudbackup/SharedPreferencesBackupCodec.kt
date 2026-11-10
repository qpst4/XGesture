package com.slideindex.app.cloudbackup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 独立 `SharedPreferences` 文件的备份编解码。
 *
 * 只支持 SharedPreferences 自身支持的六种类型（boolean/int/long/float/string/string-set）。
 * 存储的是**键值表**而不是 XML 文件：跨版本恢复时不受 XML 格式、文件权限位影响，
 * 也能顺手把已删除的键清掉（导入先 clear 再写）。
 */
internal object SharedPreferencesBackupCodec {

    private const val TYPE = "t"
    private const val VALUE = "v"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "string_set"

    /** 导出整个 prefs 文件；空文件返回 `{}`。 */
    fun export(context: Context, name: String): String {
        val all = context.applicationContext
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .all
        val root = JSONObject()
        for ((key, value) in all) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> {
                    entry.put(TYPE, TYPE_BOOLEAN)
                    entry.put(VALUE, value)
                }
                is Int -> {
                    entry.put(TYPE, TYPE_INT)
                    entry.put(VALUE, value)
                }
                is Long -> {
                    entry.put(TYPE, TYPE_LONG)
                    entry.put(VALUE, value)
                }
                is Float -> {
                    entry.put(TYPE, TYPE_FLOAT)
                    entry.put(VALUE, value.toString())
                }
                is String -> {
                    entry.put(TYPE, TYPE_STRING)
                    entry.put(VALUE, value)
                }
                is Set<*> -> {
                    val strings = value.filterIsInstance<String>()
                    if (strings.size != value.size) continue
                    entry.put(TYPE, TYPE_STRING_SET)
                    entry.put(VALUE, JSONArray(strings))
                }
                else -> continue
            }
            root.put(key, entry)
        }
        return root.toString()
    }

    /**
     * 用备份内容整份替换 prefs。
     *
     * 用 `commit()` 而不是 `apply()`：调用方紧接着会刷新内存缓存/重绘，异步落盘会读到旧值。
     */
    fun import(context: Context, name: String, raw: String) {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val editor = context.applicationContext
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit()
            .clear()
        for (key in root.keys()) {
            val entry = root.optJSONObject(key) ?: continue
            when (entry.optString(TYPE)) {
                TYPE_BOOLEAN -> editor.putBoolean(key, entry.optBoolean(VALUE))
                TYPE_INT -> editor.putInt(key, entry.optInt(VALUE))
                TYPE_LONG -> editor.putLong(key, entry.optLong(VALUE))
                TYPE_FLOAT -> entry.optString(VALUE).toFloatOrNull()
                    ?.let { editor.putFloat(key, it) }
                TYPE_STRING -> editor.putString(key, entry.optString(VALUE))
                TYPE_STRING_SET -> {
                    val array = entry.optJSONArray(VALUE) ?: JSONArray()
                    val values = buildSet {
                        for (index in 0 until array.length()) {
                            add(array.optString(index))
                        }
                    }
                    editor.putStringSet(key, values)
                }
            }
        }
        editor.commit()
    }
}
