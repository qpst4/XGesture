package com.slideindex.app.shortcut.port

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.slideindex.app.util.ShortcutKind
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.IdentityHashMap
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Root XML and `dumpsys shortcut` parsing.
 */
class ShortCutManager(private val context: Context) {

    private val rawData = ArrayList<ShortCutItem>()
    private val kindsByItem = IdentityHashMap<ShortCutItem, Set<ShortcutKind>>()
    private val intentByItem = IdentityHashMap<ShortCutItem, Intent>()

    fun fullData(): List<ShortCutItem> = rawData

    fun kindsOf(item: ShortCutItem): Set<ShortcutKind> = kindsByItem[item].orEmpty()

    fun intentOf(item: ShortCutItem): Intent? = intentByItem[item]?.let { Intent(it) }

    fun fromShellData(shellResult: String, callback: (String) -> Unit) {
        if (rawData.isEmpty()) {
            analyzeShortCutData(shellResult, callback)
        }
    }

    fun fromFileData(xmls: HashMap<String, String>, callback: (String) -> Unit) {
        if (rawData.isNotEmpty()) return
        var index = 0
        for ((name, xmlRaw) in xmls) {
            index++
            Log.d("pkg", name)
            Log.d("xml", xmlRaw)
            callback("Processing ${index * 100 / xmls.size}% ($index/${xmls.size})")
            val xml = xmlRaw
                .removePrefix("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>")
                .trim()
            if (xml.isNotEmpty()) {
                parseShortcutServiceXml(xml)
            }
        }
        callback("Done processing ${xmls.size} shortcuts.")
    }

    /**
     * C path: raw ABX/text bytes parsed in-process, no abx2xml and no DOM.
     */
    fun fromPackageBytes(documents: Map<String, ByteArray>, callback: (String) -> Unit) {
        if (rawData.isNotEmpty()) return
        val appNames = HashMap<String, String>()
        var index = 0
        for ((name, bytes) in documents) {
            index++
            callback("Parsing $index/${documents.size}")
            val parsed = ShortcutAbxParser.parse(bytes, name)
            for (entry in parsed) {
                entry.item.title = appNames.getOrPut(entry.item.pkg) {
                    ApkTools(context).getAppName(entry.item.pkg).orEmpty()
                }
                rawData.add(entry.item)
                kindsByItem[entry.item] = entry.kinds
                intentByItem[entry.item] = entry.intent
            }
        }
        callback("Done parsing ${rawData.size} shortcuts.")
    }

    private fun parseShortcutServiceXml(xml: String) {
        try {
            val document: Document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
            val packages = document.getElementsByTagName("package")
            for (i in 0 until packages.length) {
                val packageElement = packages.item(i) as? Element ?: continue
                val packageName = packageElement.getAttribute("name")
                val shortcuts = packageElement.getElementsByTagName("shortcut")
                for (j in 0 until shortcuts.length) {
                    val shortcut = shortcuts.item(j) as? Element ?: continue
                    val id = shortcut.getAttribute("id")
                    val activity = shortcut.getAttribute("activity")
                    val title = shortcut.getAttribute("title")
                    val text = shortcut.getAttribute("text")

                    val intentMap = HashMap<String, String>()
                    val intentElements = shortcut.getElementsByTagName("intent")
                    if (intentElements.length > 0) {
                        val intentElement = intentElements.item(0) as? Element
                        val intentBase = intentElement?.getAttribute("intent-base").orEmpty()
                        if (intentBase.isNotBlank()) {
                            runCatching { Intent.parseUri(intentBase, 0) }.getOrNull()?.let { parsed ->
                                parsed.action?.let { intentMap["act"] = it }
                                parsed.component?.let { intentMap["cmp"] = it.flattenToShortString() }
                                parsed.dataString?.let { intentMap["dat"] = it }
                            }
                        }
                    }

                    val extraMap = HashMap<String, String>()
                    val extraElements = shortcut.getElementsByTagName("extra")
                    for (k in 0 until extraElements.length) {
                        val extraElement = extraElements.item(k) as? Element ?: continue
                        val key = extraElement.getAttribute("name")
                        val value = extraElement.getAttribute("value")
                        if (key.isNotBlank()) extraMap[key] = value
                    }
                    val extrasElements = shortcut.getElementsByTagName("extras")
                    for (k in 0 until extrasElements.length) {
                        val extrasElement = extrasElements.item(k) as? Element ?: continue
                        appendStringExtras(extrasElement, extraMap)
                    }

                    val appName = ApkTools(context).getAppName(packageName).orEmpty()
                    val item = ShortCutItem(
                        pkg = packageName,
                        name = title,
                        shortcut = id,
                        activity = activity,
                        title = appName,
                        text = text,
                        intent = intentMap,
                        extra = extraMap,
                    )
                    rawData.add(item)
                    kindsByItem[item] = kindsFromFlags(parseFlags(shortcut.getAttribute("flags")))
                    intentByItem[item] = buildIntent(intentMap, extraMap)
                }
            }
        } catch (error: Throwable) {
            Log.e("parseShortcutServiceXml", "parse error: ${error.message}")
        }
    }

    private fun appendStringExtras(parent: Element, out: HashMap<String, String>) {
        // ShortCuts only reads <string> extras from <extras>; keep that behavior.
        val nodes = parent.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val key = element.getAttribute("name")
            if (key.isBlank()) continue
            out[key] = element.textContent.orEmpty()
        }
    }

    private fun analyzeShortCutData(rawResult: String, callback: (String) -> Unit) {
        if (!rawResult.contains("Shortcuts:")) return
        val start = rawResult.indexOf("Shortcuts:")
        val text = rawResult.substring(start)

        val pattern = Pattern.compile(
            "ShortcutInfo.*flags.*\\n\\s+packageName=(.*)\\n\\s+activity=(.*)\\n\\s+shortLabel=(.*), resId=.*\\n\\s+longLabel=(.*), resId=.*\\n\\s+disabledMessage=(.*), resId=.*\\n\\s+disabledReason=(.*)\\n\\s+categories=(.*)\\n\\s+persons=(.*)\\n\\s+icon=(.*)\\n\\s+rank=(.*), timestamp=.*\\n\\s+intents=([\\s\\S]*?)extras=(.*)\\n\\s+iconRes=",
        )
        val counter = pattern.matcher(text)
        var total = 0
        while (counter.find()) total++
        if (total == 0) return

        val matcher = pattern.matcher(text)
        val seen = HashSet<String>()
        var index = 0
        while (matcher.find()) {
            index++
            callback("Processing ${index * 100 / total}% ($index/$total)")
            try {
                val packageName = matcher.group(1).orEmpty()
                val activity = matcher.group(2).orEmpty()
                val shortLabel = matcher.group(3).orEmpty()
                val shortcutId = Regex("""ShortcutInfo\s*\{id=([^,]+),""")
                    .find(matcher.group(0).orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
                if (shortcutId.isNotBlank() && !seen.add("$packageName/$shortcutId")) {
                    continue
                }
                val intentsRaw = matcher.group(11).orEmpty()
                    .replace("\n\\s+", "")
                val extrasRaw = matcher.group(12).orEmpty()

                val (intentRaw, bundleRaw) = if (intentsRaw.contains("/null]")) {
                    intentsRaw.substring(9, intentsRaw.lastIndexOf("/null]") - 1) to ""
                } else {
                    val bundleStart = intentsRaw.indexOf("/PersistableBundle[")
                    val bundleEnd = intentsRaw.lastIndexOf("}]]")
                    if (bundleStart < 0 || bundleEnd < 0) {
                        "" to ""
                    } else {
                        intentsRaw.substring(9, bundleStart - 1) to
                            (intentsRaw.substring(bundleStart + 20, bundleEnd) + " ")
                    }
                }

                val intentMap = readData(intentRaw)
                val extraMap = readData(bundleRaw)
                val component = activity
                    .replace("ComponentInfo{", "")
                    .replace("}", "")
                val appName = ApkTools(context).getAppName(packageName).orEmpty()
                val item = ShortCutItem(
                    pkg = packageName,
                    name = shortLabel,
                    shortcut = shortcutId,
                    activity = component,
                    title = appName,
                    text = shortLabel,
                    intent = intentMap,
                    extra = extraMap,
                )
                rawData.add(item)
                val flags = Regex("""(?mi)\bflags=(0x[0-9a-f]+|\d+)""")
                    .find(matcher.group(0).orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
                kindsByItem[item] = kindsFromFlags(parseFlags(flags.orEmpty()))
                intentByItem[item] = buildIntent(intentMap, extraMap)
            } catch (error: Throwable) {
                Log.e("analyzeShortCutData", "parse error at item $index: ${error.message}")
            }
        }
        callback("Done processing $total shortcuts.")
    }

    private fun buildIntent(
        intentMap: Map<String, String>,
        extraMap: Map<String, String>,
    ): Intent {
        val intent = Intent()
        intent.action = intentMap["act"]
        intent.component = intentMap["cmp"]?.let { ComponentName.unflattenFromString(it) }
        intent.data = intentMap["dat"]?.let { Uri.parse(it) }
        for ((key, value) in extraMap) {
            intent.putExtra(key, value)
        }
        return intent
    }

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

    fun readData(rawData: String): HashMap<String, String> {
        val input = rawData.replace(", ", " ")
        val map = HashMap<String, String>()
        var key = StringBuilder()
        var value = StringBuilder()
        var readingKey = true
        var seenEquals = false

        for (c in input) {
            when {
                c == ' ' -> {
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        map[key.toString()] = value.toString()
                        key = StringBuilder()
                        value = StringBuilder()
                        readingKey = true
                        seenEquals = false
                    }
                }
                c == '=' && !seenEquals -> {
                    seenEquals = true
                    readingKey = false
                }
                readingKey -> key.append(c)
                else -> value.append(c)
            }
        }
        if (key.isNotEmpty() && value.isNotEmpty()) {
            map[key.toString()] = value.toString()
        }
        return map
    }
}
