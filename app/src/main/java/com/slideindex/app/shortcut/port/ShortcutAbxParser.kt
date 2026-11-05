package com.slideindex.app.shortcut.port

import android.content.Intent
import com.slideindex.app.util.AbxXmlParser
import com.slideindex.app.util.ShortcutKind
import org.xmlpull.v1.XmlPullParser

/**
 * Parse raw ShortcutService XML bytes (text or ABX) in-process.
 *
 * Keeps the original 1:1 fields and additionally builds a full Intent with
 * scalar typed extras from <int>/<long>/<boolean>/<float>.
 */
object ShortcutAbxParser {

    data class Parsed(
        val item: ShortCutItem,
        val kinds: Set<ShortcutKind>,
        val intent: Intent,
    )

    fun parse(data: ByteArray, fallbackPackage: String): List<Parsed> {
        if (data.isEmpty()) return emptyList()
        val parser = AbxXmlParser.openPullParser(data) ?: return emptyList()
        val result = ArrayList<Parsed>()
        var packageName = fallbackPackage
        var current: Builder? = null
        var insideExtras = false
        var stringKey: String? = null
        var stringValue: StringBuilder? = null
        var pendingScalarTag: String? = null
        var pendingScalarKey: String? = null

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "package" -> {
                            parser.attr("name")?.takeIf { it.isNotBlank() }?.let { packageName = it }
                        }
                        "shortcut" -> {
                            current = Builder(
                                packageName = packageName,
                                id = parser.attr("id").orEmpty(),
                                activity = parser.attr("activity").orEmpty(),
                                name = parser.attr("title").orEmpty(),
                                text = parser.attr("text").orEmpty(),
                                flags = parseFlags(parser.attr("flags").orEmpty()),
                            )
                            insideExtras = false
                        }
                        "intent" -> {
                            val target = current ?: continue
                            parser.attr("intent-base")?.takeIf { it.isNotBlank() }?.let { base ->
                                runCatching { Intent.parseUri(base, 0) }.getOrNull()?.let { intent ->
                                    target.baseIntent = intent
                                    intent.action?.let { target.intent["act"] = it }
                                    intent.component?.let { target.intent["cmp"] = it.flattenToShortString() }
                                    intent.dataString?.let { target.intent["dat"] = it }
                                }
                            }
                        }
                        "extra" -> {
                            val target = current ?: continue
                            val key = parser.attr("name").orEmpty()
                            if (key.isNotBlank()) {
                                val value = parser.attr("value").orEmpty()
                                target.extra[key] = value
                                target.fullIntent.putExtra(key, value)
                            }
                        }
                        "extras" -> {
                            if (current != null) insideExtras = true
                        }
                        "string" -> {
                            if (insideExtras && current != null) {
                                stringKey = parser.attr("name")
                                stringValue = StringBuilder()
                            }
                        }
                        "int", "long", "float", "boolean", "double" -> {
                            val target = current
                            if (insideExtras && target != null) {
                                val key = parser.attr("name")
                                if (!key.isNullOrBlank()) {
                                    pendingScalarTag = parser.name
                                    pendingScalarKey = key
                                    val value = parser.attr("value").orEmpty()
                                    target.extra[key] = value
                                    applyScalar(target.fullIntent, parser.name, key, value)
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideExtras && stringKey != null) {
                            stringValue?.append(parser.text.orEmpty())
                        } else if (insideExtras && pendingScalarTag != null && pendingScalarKey != null) {
                            // Some XML variants put scalar values in the text node.
                            val target = current
                            val key = pendingScalarKey
                            val tag = pendingScalarTag
                            val text = parser.text.orEmpty()
                            if (target != null && key != null && tag != null && text.isNotBlank()) {
                                target.extra[key] = text
                                applyScalar(target.fullIntent, tag, key, text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "string" -> {
                            val key = stringKey
                            val shortcut = current
                            if (insideExtras && shortcut != null && !key.isNullOrBlank()) {
                                val value = stringValue?.toString().orEmpty()
                                shortcut.extra[key] = value
                                shortcut.fullIntent.putExtra(key, value)
                            }
                            stringKey = null
                            stringValue = null
                        }
                        "int", "long", "float", "boolean", "double" -> {
                            pendingScalarTag = null
                            pendingScalarKey = null
                        }
                        "extras" -> insideExtras = false
                        "shortcut" -> {
                            current?.build()?.let(result::add)
                            current = null
                            insideExtras = false
                            stringKey = null
                            stringValue = null
                            pendingScalarTag = null
                            pendingScalarKey = null
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Broken/unsupported document: keep whatever was parsed so far.
        }
        return result
    }

    private class Builder(
        val packageName: String,
        val id: String,
        val activity: String,
        val name: String,
        val text: String,
        val flags: Int,
    ) {
        val intent = HashMap<String, String>()
        val extra = HashMap<String, String>()
        var baseIntent: Intent? = null
        val fullIntent: Intent = Intent()

        fun build(): Parsed {
            val full = baseIntent?.let { Intent(it) } ?: Intent()
            full.putExtras(fullIntent)
            return Parsed(
                item = ShortCutItem(
                    pkg = packageName,
                    name = name,
                    shortcut = id,
                    activity = activity,
                    title = "",
                    text = text,
                    intent = intent,
                    extra = extra,
                ),
                kinds = kindsFromFlags(flags),
                intent = full,
            )
        }
    }

    private fun applyScalar(intent: Intent, tag: String, key: String, raw: String) {
        val value = raw.trim()
        when (tag) {
            "int" -> value.toIntOrNull()?.let { intent.putExtra(key, it) }
            "long" -> value.toLongOrNull()?.let { intent.putExtra(key, it) }
            "float" -> value.toFloatOrNull()?.let { intent.putExtra(key, it) }
            "boolean" -> when (value.lowercase()) {
                "true", "1" -> intent.putExtra(key, true)
                "false", "0" -> intent.putExtra(key, false)
            }
            "double" -> value.toDoubleOrNull()?.let { intent.putExtra(key, it) }
            else -> intent.putExtra(key, raw)
        }
    }

    private fun XmlPullParser.attr(name: String): String? =
        getAttributeValue(null, name)?.trim()

    private fun parseFlags(raw: String): Int {
        val value = raw.trim()
        if (value.isEmpty()) return 0
        return if (value.startsWith("0x", ignoreCase = true)) {
            value.substring(2).toIntOrNull(16) ?: 0
        } else {
            value.toIntOrNull() ?: 0
        }
    }

    private fun kindsFromFlags(flags: Int): Set<ShortcutKind> {
        val kinds = linkedSetOf<ShortcutKind>()
        if (flags and (1 shl 0) != 0) kinds += ShortcutKind.DYNAMIC
        if (flags and (1 shl 1) != 0) kinds += ShortcutKind.PINNED
        if (flags and (1 shl 2) != 0) kinds += ShortcutKind.STATIC
        return kinds
    }
}
