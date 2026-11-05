package com.slideindex.app.util

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Parses Android ShortcutService persistence XML (plain text or ABX binary).
 *
 * Layouts:
 * - Monolithic index: `/data/system_ce/0/shortcut_service/shortcuts.xml` (often ABX on Android 12+)
 * - Per-package: `/data/system_ce/0/shortcut_service/packages/{package}.xml`
 */
object ShortcutSystemXmlParser {
    private const val TAG = "ShortcutSystemXmlParser"

    // Keep in sync with android.content.pm.ShortcutInfo. The persisted flags
    // are the same bit layout as dumpsys: dynamic=1, pinned=2, manifest=4, cached=8.
    private const val FLAG_DYNAMIC = 1 shl 0
    private const val FLAG_PINNED = 1 shl 1
    private const val FLAG_MANIFEST = 1 shl 2
    private const val FLAG_CACHED = 1 shl 3

    const val SHORTCUT_PACKAGES_DIR = "/data/system_ce/0/shortcut_service/packages"

    /** Prefer Flyme/OEM per-package dir, then monolithic files, then AOSP stock paths. */
    val shortcutSystemXmlPaths = listOf(
        "/data/system_ce/0/shortcut_service/shortcuts.xml",
        "/data/system_ce/0/shortcuts.xml",
        "/data/system_ce/0/shortcut_services/shortcuts.xml",
    )

    fun packageXmlPath(packageName: String): String = "$SHORTCUT_PACKAGES_DIR/$packageName.xml"

    fun packageNameFromPath(path: String): String =
        path.substringAfterLast('/').removeSuffix(".xml")

    fun parseShortcutsBytes(
        data: ByteArray,
        fallbackPackage: String? = null,
    ): Map<String, List<SystemShortcutEntry>> {
        if (data.isEmpty()) return emptyMap()
        val parser = AbxXmlParser.openPullParser(data) ?: return emptyMap()
        return parseShortcutsFromPullParser(parser, fallbackPackage)
    }

    fun parseShortcutsXml(xml: String): Map<String, List<SystemShortcutEntry>> {
        if (xml.isBlank()) return emptyMap()
        if (!AbxXmlParser.looksLikeTextXml(xml)) {
            Log.d(TAG, "parseShortcutsXml: skipping non-text payload (${xml.length} chars)")
            return emptyMap()
        }
        return runCatching {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            parseShortcutsFromPullParser(parser)
        }.onFailure { error ->
            Log.w(TAG, "parseShortcutsXml failed", error)
        }.getOrDefault(emptyMap())
    }

    private fun parseShortcutsFromPullParser(
        parser: XmlPullParser,
        fallbackPackage: String? = null,
    ): Map<String, List<SystemShortcutEntry>> {
        val merged = linkedMapOf<String, LinkedHashMap<String, SystemShortcutEntry>>()
        try {
            var eventType = parser.eventType
            var currentPackage: String? = fallbackPackage
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "package" -> {
                            currentPackage = parser.getAttributeValue(null, "name")
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?: fallbackPackage
                        }
                        "shortcut" -> {
                            val packageName = currentPackage ?: continue
                            parseShortcut(parser, packageName)?.let { entry ->
                                merged.getOrPut(packageName) { linkedMapOf() }.putIfAbsent(entry.id, entry)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (error: Exception) {
            Log.w(TAG, "parseShortcutsFromPullParser failed", error)
        }
        return merged.mapValues { (_, shortcuts) -> shortcuts.values.toList() }
    }

    /** Parse one per-package document (text or ABX). */
    fun parsePackageDocumentBytes(data: ByteArray, fallbackPackage: String): List<SystemShortcutEntry> {
        val parsed = parseShortcutsBytes(data, fallbackPackage)
        if (parsed.isNotEmpty()) {
            return parsed[fallbackPackage]
                ?: parsed.values.flatten()
        }
        return emptyList()
    }

    /** Parse many per-package documents keyed by path. */
    fun parsePackageDocuments(
        documents: Map<String, ByteArray>,
    ): Map<String, List<SystemShortcutEntry>> {
        val merged = linkedMapOf<String, LinkedHashMap<String, SystemShortcutEntry>>()
        documents.forEach { (path, bytes) ->
            val packageName = packageNameFromPath(path)
            val entries = parsePackageDocumentBytes(bytes, packageName)
            if (entries.isEmpty()) return@forEach
            val bucket = merged.getOrPut(packageName) { linkedMapOf() }
            entries.forEach { bucket.putIfAbsent(it.id, it) }
        }
        return merged.mapValues { (_, shortcuts) -> shortcuts.values.toList() }
    }

    /** Parse output from [readAllPackageXmlShellCommand] (many `<package>` roots concatenated). */
    fun parseMultiplePackageDocuments(raw: String): Map<String, List<SystemShortcutEntry>> {
        if (raw.isBlank()) return emptyMap()
        val merged = linkedMapOf<String, LinkedHashMap<String, SystemShortcutEntry>>()
        raw.split(Regex("(?=<package\\s)")).forEach { chunk ->
            val trimmed = chunk.trim()
            if (trimmed.isEmpty() || !trimmed.contains("<shortcut")) return@forEach
            parseShortcutsXml(trimmed).forEach { (packageName, entries) ->
                val bucket = merged.getOrPut(packageName) { linkedMapOf() }
                entries.forEach { bucket.putIfAbsent(it.id, it) }
            }
        }
        return merged.mapValues { (_, shortcuts) -> shortcuts.values.toList() }
    }

    fun flattenByKind(
        parsed: Map<String, List<SystemShortcutEntry>>,
    ): Map<ShortcutKind, Map<String, List<Pair<String, String>>>> {
        val result = ShortcutKind.entries.associateWith { linkedMapOf<String, MutableList<Pair<String, String>>>() }
        parsed.forEach { (packageName, entries) ->
            entries.forEach { entry ->
                entry.kinds.forEach { kind ->
                    result.getValue(kind).getOrPut(packageName) { mutableListOf() }.add(entry.id to entry.label)
                }
            }
        }
        return result.mapValues { (_, byPackage) -> byPackage.mapValues { (_, rows) -> rows.toList() } }
    }

    private fun parseShortcut(parser: XmlPullParser, packageName: String): SystemShortcutEntry? {
        val id = parser.getAttributeValue(null, "id")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val flags = parser.getAttributeValue(null, "flags")?.toIntOrNull() ?: 0
        if (isShortcutDisabled(parser)) return null

        val kinds = resolveKinds(flags)
        val title = parser.getAttributeValue(null, "title")?.trim().orEmpty()
        val text = parser.getAttributeValue(null, "text")?.trim().orEmpty()
        val label = resolveLabel(id, title, text) ?: return null
        if (!ShortcutDisplayRules.isDisplayable(id, label)) return null

        val activityAttr = parser.getAttributeValue(null, "activity")?.trim()
        val targetComponent = activityAttr?.let(::normalizeActivityAttribute)
            ?: readShortcutIntentTarget(parser, packageName)

        return SystemShortcutEntry(
            id = id,
            label = label,
            kinds = kinds,
            targetComponent = targetComponent,
        )
    }

    private fun isShortcutDisabled(parser: XmlPullParser): Boolean {
        val disabledReason = parser.getAttributeValue(null, "disabled-reason")?.toIntOrNull()
        if (disabledReason != null) return disabledReason != 0
        // Do not use bit 0 here: that is FLAG_DYNAMIC, not a disabled bit.
        return parser.getAttributeValue(null, "enabled")
            ?.equals("false", ignoreCase = true) == true
    }

    private fun resolveKinds(flags: Int): Set<ShortcutKind> {
        val kinds = linkedSetOf<ShortcutKind>()
        if (flags and FLAG_MANIFEST != 0) kinds += ShortcutKind.STATIC
        if (flags and FLAG_DYNAMIC != 0) kinds += ShortcutKind.DYNAMIC
        if (flags and FLAG_PINNED != 0) kinds += ShortcutKind.PINNED
        if (kinds.isEmpty()) kinds += ShortcutKind.DYNAMIC
        return kinds
    }

    private fun normalizeActivityAttribute(activity: String): String? {
        val className = activity.substringAfter('/', activity).trim()
        return className.takeIf { it.isNotBlank() && it.contains('.') }
    }

    private fun readShortcutIntentTarget(parser: XmlPullParser, defaultPackage: String): String? {
        val depth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.name == "shortcut" && parser.depth == depth) {
                break
            }
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "intent") continue

            parser.getAttributeValue(null, "intent-base")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

            val targetPackage = parser.getAttributeValue(null, "targetPackage")?.trim()?.takeIf { it.isNotBlank() }
                ?: defaultPackage
            val targetClass = parser.getAttributeValue(null, "targetClass")?.trim()?.takeIf { it.isNotBlank() }
                ?: continue
            return if (targetClass.contains('.')) {
                targetClass
            } else {
                "$targetPackage.$targetClass"
            }
        }
        return null
    }

    private fun resolveLabel(id: String, title: String, text: String): String? {
        title.takeIf { it.isNotBlank() && !looksLikeResourceRef(it) }?.let { return it }
        text.takeIf { it.isNotBlank() && !looksLikeResourceRef(it) }?.let { return it }
        if (id.any { it in '\u4e00'..'\u9fff' }) return id
        return humanizeId(id)
    }

    private fun humanizeId(id: String): String? {
        val humanized = id
            .substringAfterLast('/')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        if (humanized.length < 2) return null
        if (humanized.all { it.isDigit() }) return null
        return humanized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun looksLikeResourceRef(value: String): Boolean {
        if (value.startsWith("0x", ignoreCase = true)) return true
        if (value.startsWith("@")) return true
        if (value.endsWith(".xml", ignoreCase = true)) return true
        return false
    }
}
