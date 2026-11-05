package com.slideindex.app.util

object ShortcutShellParser {
    // Keep in sync with android.content.pm.ShortcutInfo:
    // FLAG_DYNAMIC=1<<0, FLAG_PINNED=1<<1, FLAG_MANIFEST=1<<2, FLAG_CACHED=1<<3.
    // The dump prints raw ShortcutInfo flags; mapping manifest/cached as 4/8
    // (the previous values) mislabels every shortcut kind.
    private const val FLAG_DYNAMIC = 1 shl 0
    private const val FLAG_PINNED = 1 shl 1
    private const val FLAG_MANIFEST = 1 shl 2
    private const val FLAG_CACHED = 1 shl 3

    private val idPattern = Regex("""(?mi)\bid=([^,\s}]+)""")
    private val packagePattern = Regex("""(?mi)\bpackageName=([^,\s}]+)""")
    private val flagsPattern = Regex("""(?mi)\bflags=(0x[0-9a-f]+|\d+)""")
    private val activityPattern = Regex("""(?mi)\bactivity=([^,\s}]+)""")
    private val labelPatterns = listOf(
        Regex("""(?mi)\btitle=([^,}]+)"""),
        Regex("""(?mi)\bshortLabel=([^,\n}]+)"""),
        Regex("""(?mi)\blongLabel=([^,\n}]+)"""),
        Regex("""(?mi)\blabel=([^,}]+)"""),
        Regex("""(?mi)\btext=([^,}]+)"""),
        Regex("""(?mi)\bname=([^,}]+)"""),
    )
    private val disabledPattern = Regex("""(?mi)\[Dis\]|(?:^|\s)enabled=false\b""")
    private val packageHeaderPattern = Regex("""(?mi)^\s*Package:\s*(\S+)\s*$""")
    private val pinnedLinePattern = Regex("""(?mi)^\s*Pinned:\s*(\S+)\s*$""")

    /** Package names that appear in a full `dumpsys shortcut` dump. */
    fun parsePackageNames(dump: String): Set<String> {
        if (dump.isBlank()) return emptySet()
        val packages = linkedSetOf<String>()
        dump.lineSequence().forEach { line ->
            packageHeaderPattern.matchEntire(line.trim())?.groupValues?.getOrNull(1)?.let { pkg ->
                if (pkg.isNotBlank() && pkg != "Package") packages += pkg
            }
        }
        parseAllPackages(dump).keys.forEach { packages += it }
        return packages
    }

    fun parse(dump: String, packageName: String? = null): List<Pair<String, String>> {
        if (dump.isBlank()) return emptyList()
        val merged = linkedMapOf<String, String>()
        dump.lineSequence().forEach { line ->
            if (!line.contains("ShortcutInfo", ignoreCase = true)) return@forEach
            parseEntry(line, packageName)?.let { (id, label) ->
                merged.putIfAbsent(id, label)
            }
        }
        extractShortcutInfoBlocks(dump).forEach { block ->
            parseEntry(block, packageName)?.let { (id, label) ->
                merged.putIfAbsent(id, label)
            }
        }
        return merged.map { it.key to it.value }
    }

    /** Parse every ShortcutInfo block with kind flags from a `dumpsys shortcut -p` dump. */
    fun parseAllPackagesEntries(dump: String): Map<String, List<SystemShortcutEntry>> {
        if (dump.isBlank()) return emptyMap()
        val merged = linkedMapOf<String, LinkedHashMap<String, SystemShortcutEntry>>()
        fun putEntry(packageName: String, entry: SystemShortcutEntry) {
            if (!isValidId(entry.id) || !ShortcutDisplayRules.isDisplayable(entry.id, entry.label)) return
            merged.getOrPut(packageName) { linkedMapOf() }.putIfAbsent(entry.id, entry)
        }
        dump.lineSequence().forEach { line ->
            if (!line.contains("ShortcutInfo", ignoreCase = true)) return@forEach
            parseEntryWithDetails(line)?.let { (pkg, entry) -> putEntry(pkg, entry) }
        }
        extractShortcutInfoBlocks(dump).forEach { block ->
            parseEntryWithDetails(block)?.let { (pkg, entry) -> putEntry(pkg, entry) }
        }
        return merged.mapValues { (_, entries) -> entries.values.toList() }
    }

    /** Parse every ShortcutInfo block in a full `dumpsys shortcut` dump, grouped by package. */
    fun parseAllPackages(dump: String): Map<String, List<Pair<String, String>>> {
        if (dump.isBlank()) return emptyMap()
        val merged = linkedMapOf<String, LinkedHashMap<String, String>>()
        fun putEntry(packageName: String, id: String, label: String) {
            if (!isValidId(id) || !ShortcutDisplayRules.isDisplayable(id, label)) return
            merged.getOrPut(packageName) { linkedMapOf() }.putIfAbsent(id, label)
        }
        dump.lineSequence().forEach { line ->
            if (!line.contains("ShortcutInfo", ignoreCase = true)) return@forEach
            parseEntryWithPackage(line)?.let { (pkg, id, label) -> putEntry(pkg, id, label) }
        }
        extractShortcutInfoBlocks(dump).forEach { block ->
            parseEntryWithPackage(block)?.let { (pkg, id, label) -> putEntry(pkg, id, label) }
        }
        return merged.mapValues { (_, shortcuts) -> shortcuts.map { it.key to it.value } }
    }

    fun parsePackage(dump: String, packageName: String): List<Pair<String, String>> =
        parse(dump, packageName)

    /** Pinned shortcut IDs grouped by target app package for [launcherPackage]. */
    fun parseAllLauncherPinned(dump: String, launcherPackage: String): Map<String, List<String>> {
        if (dump.isBlank() || launcherPackage.isBlank()) return emptyMap()
        val result = linkedMapOf<String, LinkedHashSet<String>>()
        var inLauncher = false
        var currentPackage: String? = null
        for (line in dump.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Launcher:") -> {
                    inLauncher = trimmed.substringAfter("Launcher:").trim() == launcherPackage
                    currentPackage = null
                }
                inLauncher && trimmed.startsWith("Package:") -> {
                    currentPackage = trimmed.substringAfter("Package:").trim().takeIf { it.isNotBlank() }
                }
                inLauncher && currentPackage != null -> {
                    val packageName = currentPackage
                    pinnedLinePattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
                        if (isValidId(id)) {
                            result.getOrPut(packageName) { linkedSetOf() } += id
                        }
                    }
                }
            }
        }
        return result.mapValues { it.value.toList() }
    }

    /** Pinned shortcut IDs that the default launcher shows for [targetPackage]. */
    fun parseLauncherPinnedIds(
        dump: String,
        launcherPackage: String,
        targetPackage: String,
    ): List<String> {
        if (dump.isBlank() || launcherPackage.isBlank() || targetPackage.isBlank()) return emptyList()
        val lines = dump.lineSequence().toList()
        var inLauncher = false
        var inTargetPackage = false
        val ids = linkedSetOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Launcher:") -> {
                    val launcher = trimmed.substringAfter("Launcher:").trim()
                    inLauncher = launcher == launcherPackage
                    inTargetPackage = false
                }
                inLauncher && trimmed.startsWith("Package:") -> {
                    val pkg = trimmed.substringAfter("Package:").trim()
                    inTargetPackage = pkg == targetPackage
                }
                inLauncher && inTargetPackage -> {
                    pinnedLinePattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
                        if (isValidId(id)) ids += id
                    }
                    if (trimmed.startsWith("Package:") && !trimmed.endsWith(targetPackage)) {
                        inTargetPackage = false
                    }
                }
                trimmed.startsWith("Launcher:") && inLauncher -> {
                    // next launcher section
                }
            }
        }
        return ids.toList()
    }

    private fun extractShortcutInfoBlocks(dump: String): List<String> {
        val blocks = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < dump.length) {
            val start = dump.indexOf("ShortcutInfo {", searchFrom, ignoreCase = true)
            if (start < 0) break
            val braceStart = dump.indexOf('{', start)
            if (braceStart < 0) break
            var depth = 0
            var end = -1
            for (i in braceStart until dump.length) {
                when (dump[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }
            if (end < 0) break
            blocks += dump.substring(start, end + 1)
            searchFrom = end + 1
        }
        return blocks
    }

    private fun parseEntry(text: String, packageName: String?): Pair<String, String>? {
        if (text.isBlank() || disabledPattern.containsMatchIn(text)) return null
        if (packageName != null && text.contains("packageName=")) {
            val pkg = packagePattern.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (pkg != null && pkg != packageName) return null
        }
        val id = idPattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { isValidId(it) } ?: return null
        val label = readLabel(text, id) ?: return null
        return id to label
    }

    private fun parseEntryWithPackage(text: String): Triple<String, String, String>? {
        val entry = parseEntry(text, packageName = null) ?: return null
        val pkg = packagePattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return Triple(pkg, entry.first, entry.second)
    }

    private fun parseEntryWithDetails(text: String): Pair<String, SystemShortcutEntry>? {
        val (pkg, id, label) = parseEntryWithPackage(text) ?: return null
        val flagsRaw = flagsPattern.find(text)?.groupValues?.getOrNull(1)
        val activityRaw = activityPattern.find(text)?.groupValues?.getOrNull(1)?.trim()
        val targetComponent = activityRaw
            ?.substringAfter('/')
            ?.takeIf { it.isNotBlank() && it.contains('.') }
        return pkg to SystemShortcutEntry(
            id = id,
            label = label,
            kinds = resolveKinds(flagsRaw),
            targetComponent = targetComponent,
        )
    }

    private fun resolveKinds(flagsRaw: String?): Set<ShortcutKind> {
        val flags = when {
            flagsRaw == null -> 0
            flagsRaw.startsWith("0x", ignoreCase = true) -> flagsRaw.drop(2).toLongOrNull(16) ?: 0L
            else -> flagsRaw.toLongOrNull() ?: 0L
        }.toInt()
        val kinds = linkedSetOf<ShortcutKind>()
        if (flags and FLAG_MANIFEST != 0) kinds += ShortcutKind.STATIC
        if (flags and FLAG_DYNAMIC != 0) kinds += ShortcutKind.DYNAMIC
        if (flags and FLAG_PINNED != 0) kinds += ShortcutKind.PINNED
        if (kinds.isEmpty()) kinds += ShortcutKind.DYNAMIC
        return kinds
    }

    private fun readLabel(text: String, id: String): String? {
        val rawLabel = labelPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
        }?.trim('"')?.takeIf { it.isNotEmpty() }
        val cleaned = rawLabel
            ?.substringBefore(" characters=")
            ?.substringBefore(" chars:")
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() && !looksLikeResourceRef(it) }
        if (cleaned != null) {
            if (ShortcutDisplayRules.isInternalKey(cleaned)) return null
            return cleaned
        }
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

    private fun isValidId(id: String): Boolean {
        if (id.isBlank() || id == "null") return false
        if (id.startsWith("***")) return false
        return !id.contains('=')
    }
}
