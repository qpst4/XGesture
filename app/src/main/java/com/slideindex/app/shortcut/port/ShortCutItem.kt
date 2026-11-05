package com.slideindex.app.shortcut.port

import android.util.Log
import java.util.regex.Pattern

/**
 * Shortcut data and `am start` command generation.
 *
 * Keeps the compact `toIntent()` string form, including the `--es key 'value'`
 * shape and the integer inference used by the system shortcut service.
 */
data class ShortCutItem(
    var pkg: String = "",
    var name: String = "",
    var shortcut: String = "",
    var activity: String = "",
    var title: String = "",
    var text: String = "",
    var intent: HashMap<String, String> = HashMap(),
    var extra: HashMap<String, String> = HashMap(),
) {

    fun toIntent(): String {
        val sb = StringBuilder("am start")

        intent["act"]?.let { sb.append(" -a ").append(it) }
        intent["cmp"]?.let { sb.append(" -n ").append(it) }
        intent["dat"]?.let { sb.append(" -d ").append(it) }

        for ((key, value) in extra) {
            val type = when {
                isBoolean(value) -> "ez"
                isInteger(value) && value.length < 3 -> "ei"
                isLong(value) -> "el"
                isFloat(value) -> "ef"
                else -> "es"
            }
            if (type == "es") {
                sb.append(" --").append(type).append(' ').append(key).append(" '").append(value).append('\'')
            } else {
                sb.append(" --").append(type).append(' ').append(key).append(' ').append(value)
            }
        }

        Log.d("intent", intent.toString())
        return sb.toString()
    }

    private fun isLong(value: String): Boolean = value.toLongOrNull() != null

    private fun isFloat(value: String): Boolean = value.toFloatOrNull() != null

    private fun isInteger(value: String): Boolean =
        Pattern.compile("^[-+]?\\d+$").matcher(value).matches()

    private fun isBoolean(value: String): Boolean =
        value == "true" || value == "false"
}
