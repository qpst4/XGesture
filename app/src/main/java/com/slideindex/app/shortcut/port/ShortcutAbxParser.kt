package com.slideindex.app.shortcut.port

import android.content.Intent
import com.slideindex.app.util.AbxXmlParser
import com.slideindex.app.util.ShortcutKind
import org.xmlpull.v1.XmlPullParser

/**
 * Parse raw ShortcutService XML bytes (text or ABX) in-process.
 *
 * Reads shortcut attributes, intent-base and <string> extras. Typed
 * <int>/<long> extras are intentionally not read.
 */
object ShortcutAbxParser {

    data class Parsed(
        val item: ShortCutItem,
        val kinds: Set<ShortcutKind>,
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
                                target.extra[key] = parser.attr("value").orEmpty()
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
                    }
                    XmlPullParser.TEXT -> {
                        if (insideExtras && stringKey != null) {
                            stringValue?.append(parser.text.orEmpty())
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "string" -> {
                            val key = stringKey
                            val shortcut = current
                            if (insideExtras && shortcut != null && !key.isNullOrBlank()) {
                                shortcut.extra[key] = stringValue?.toString().orEmpty()
                            }
                            stringKey = null
                            stringValue = null
                        }
                        "extras" -> insideExtras = false
                        "shortcut" -> {
                            current?.build()?.let(result::add)
                            current = null
                            insideExtras = false
                            stringKey = null
                            stringValue = null
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

        fun build(): Parsed = Parsed(
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
        )
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
