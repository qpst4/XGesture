package com.slideindex.app.overlay.pickresult

sealed class PickResultOpenLinkAction {
    data class Open(val url: String) : PickResultOpenLinkAction()
    data class Choose(val urls: List<String>) : PickResultOpenLinkAction()
}

object PickResultUrl {
    /**
     * 不允许出现在网址内部的字符。
     *
     * 除 ASCII 空白与常见定界符外，还必须排除中文标点、全角符号和 CJK 文字：中文排版里
     * 链接之间往往没有空格（「油管：https://t.co/a，微博：https://t.co/b」），只按空白切分
     * 会把整段吞成一条超长网址，于是只看得到第一条链接。
     */
    private const val URL_STOP_CHARS =
        "\\s<>\"')\\]},;" +
            "\u2010-\u2027\u2030-\u205E" + // 通用标点：— – … “ ” 等
            "\u3000-\u303F\u3040-\u30FF" + // CJK 标点与假名
            "\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF" + // 汉字
            "\uFE10-\uFE6F\uFF00-\uFFEF" + // 竖排标点与全角字符
            "\uFFFD" // 替换字符，乱码文本里常见

    /** 网址起始处的前一个字符不能是同类字符，避免从更长的 token 中间截出网址。 */
    private const val URL_START_BOUNDARY = """(?<![\w.@/-])"""

    private val anyUrlStopCharRegex = Regex("[$URL_STOP_CHARS]")
    private val urlBody = "[^$URL_STOP_CHARS]"

    private val httpUrlRegex = Regex("https?://$urlBody+", RegexOption.IGNORE_CASE)
    private val wwwUrlRegex = Regex(
        "$URL_START_BOUNDARY((?:www\\.)$urlBody+)",
        RegexOption.IGNORE_CASE,
    )
    private val schemeUrlRegex = Regex(
        "$URL_START_BOUNDARY((?:[a-z][a-z0-9+.-]*://)$urlBody+)",
        RegexOption.IGNORE_CASE,
    )
    private val systemUriRegex = Regex(
        "$URL_START_BOUNDARY((?:tel|mailto|sms|geo):$urlBody+)",
        RegexOption.IGNORE_CASE,
    )
    private val intentUriRegex = Regex(
        "$URL_START_BOUNDARY(intent:(?://[^\\s\"'<>]*?)?#Intent;[^\\s\"'<>]*?;end)",
        RegexOption.IGNORE_CASE,
    )
    private val bareHostRegex = Regex(
        """^[\w\-.]+\.[a-zA-Z]{2,}([\w./?#=&+%\-]*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val androidPackageRegex = Regex(
        """^(com|org|net|cn|io|tw|hk|co|edu|gov|app|me|android|androidx|java|javax|kotlin|kotlinx)\.[a-zA-Z0-9_.]+$""",
        RegexOption.IGNORE_CASE,
    )
    private val blockedSchemes = setOf("javascript", "data")

    fun resolveOpenLinkAction(
        fullText: String,
        activeText: String,
        hasSelection: Boolean,
    ): PickResultOpenLinkAction? {
        val target = if (hasSelection) activeText else fullText
        normalizeOpenableUrl(target.trim())?.let { return PickResultOpenLinkAction.Open(it) }
        val urls = extractOpenableUrls(target)
        return when {
            urls.isEmpty() -> null
            urls.size == 1 -> PickResultOpenLinkAction.Open(urls.single())
            else -> PickResultOpenLinkAction.Choose(urls)
        }
    }

    fun extractOpenableUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val found = linkedSetOf<String>()
        val intentMatches = intentUriRegex.findAll(text).toList()
        intentMatches.forEach { match ->
            normalizeOpenableUrl(match.groupValues[1])?.let { found.add(it) }
        }
        // 把 intent URI 整段挖空，避免后续正则再从里面截出半条链接（例如 intent://... 被第一个 ; 截断）。
        val masked = if (intentMatches.isEmpty()) {
            text
        } else {
            val chars = text.toCharArray()
            intentMatches.forEach { match ->
                for (index in match.range) chars[index] = ' '
            }
            String(chars)
        }
        httpUrlRegex.findAll(masked).forEach { match ->
            normalizeOpenableUrl(match.value)?.let { found.add(it) }
        }
        wwwUrlRegex.findAll(masked).forEach { match ->
            normalizeOpenableUrl(match.groupValues[1])?.let { found.add(it) }
        }
        schemeUrlRegex.findAll(masked).forEach { match ->
            normalizeOpenableUrl(match.groupValues[1])?.let { found.add(it) }
        }
        systemUriRegex.findAll(masked).forEach { match ->
            normalizeOpenableUrl(match.groupValues[1])?.let { found.add(it) }
        }
        return found.toList()
    }

    /**
     * 分词用：链接在原文里的下标区间（原样返回，不做规范化）。
     *
     * 与 [extractOpenableUrls] 同一套识别规则，但保留原始位置 —— 取词面板要把整条链接收成
     * 一个词块，必须知道它从哪到哪；[extractOpenableUrls] 返回的是规范化后的网址（`www.x.com`
     * 会被补成 `https://www.x.com`），拿它去原文里反查是找不到的。
     */
    fun findOpenableUrlRanges(text: String): List<IntRange> {
        if (text.isBlank()) return emptyList()
        val found = ArrayList<IntRange>(4)
        val intentMatches = intentUriRegex.findAll(text).toList()
        intentMatches.forEach { match ->
            addUrlRange(text, match.groups[1]?.range, found)
        }
        val masked = if (intentMatches.isEmpty()) {
            text
        } else {
            val chars = text.toCharArray()
            intentMatches.forEach { match ->
                for (index in match.range) chars[index] = ' '
            }
            String(chars)
        }
        httpUrlRegex.findAll(masked).forEach { match ->
            addUrlRange(text, match.range, found)
        }
        wwwUrlRegex.findAll(masked).forEach { match ->
            addUrlRange(text, match.groups[1]?.range, found)
        }
        schemeUrlRegex.findAll(masked).forEach { match ->
            addUrlRange(text, match.groups[1]?.range, found)
        }
        systemUriRegex.findAll(masked).forEach { match ->
            addUrlRange(text, match.groups[1]?.range, found)
        }
        return withoutOverlappingRanges(found)
    }

    private fun addUrlRange(text: String, range: IntRange?, out: MutableList<IntRange>) {
        if (range == null || range.isEmpty()) return
        val raw = text.substring(range.first, range.last + 1)
        // 正则会把句尾的 . , ) 一起吞进来；分词要按「链接本体」收口，标点留给后面的词块。
        val trimmed = trimTrailingPunctuation(raw)
        if (trimmed.isEmpty()) return
        if (normalizeOpenableUrl(trimmed) == null) return
        out += range.first..(range.first + trimmed.length - 1)
    }

    /**
     * 同一条链接会被多条正则同时命中（`https://` 也满足自定义协议），
     * 还会出现 `www.` 落在 `https://` 里面的情况：只留最外层那一段。
     */
    private fun withoutOverlappingRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size <= 1) return ranges
        val kept = ArrayList<IntRange>(ranges.size)
        ranges.distinct()
            .sortedWith(compareBy({ it.first }, { -it.last }))
            .forEach { range ->
                val overlaps = kept.any { it.first <= range.last && it.last >= range.first }
                if (!overlaps) kept += range
            }
        return kept
    }

    fun linkDisplayLabel(uri: String): String {
        val normalized = normalizeOpenableUrl(uri) ?: uri.trim()
        return when {
            normalized.startsWith("tel:", ignoreCase = true) -> {
                normalized.removePrefix("tel:").substringBefore('?').ifBlank { "tel" }
            }
            normalized.startsWith("mailto:", ignoreCase = true) -> {
                normalized.removePrefix("mailto:").substringBefore('?').ifBlank { "mailto" }
            }
            normalized.startsWith("sms:", ignoreCase = true) -> {
                normalized.removePrefix("sms:").substringBefore('?').ifBlank { "sms" }
            }
            isIntentUri(normalized) -> "Intent"
            else -> {
                val scheme = normalized.substringBefore("://", missingDelimiterValue = "")
                val afterScheme = normalized.substringAfter("://", missingDelimiterValue = normalized)
                val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                when {
                    host.contains('.') -> host
                    scheme.isNotBlank() -> scheme
                    host.isNotBlank() -> host
                    else -> normalized
                }
            }
        }
    }

    /**
     * 同域名多条链接（例如三条 t.co 短链）只显示 host 会看起来像同一条，
     * 这里补上路径片段，例如 t.co/WfO3vZ0ygY。
     */
    fun linkCompactLabel(uri: String): String {
        val normalized = normalizeOpenableUrl(uri) ?: uri.trim()
        val host = linkDisplayLabel(normalized)
        val segment = normalized
            .substringAfter("://", missingDelimiterValue = "")
            .substringAfter('/', missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (segment.isBlank()) return host
        val short = if (segment.length > 12) segment.take(12) + "…" else segment
        return "$host/$short"
    }

    /** 批量取显示标签，遇到重复 host 时才带上路径片段做区分。 */
    fun linkDisplayLabels(uris: List<String>): List<String> {
        val hosts = uris.map(::linkDisplayLabel)
        val duplicatedHosts = hosts
            .groupingBy { it.lowercase() }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        return uris.mapIndexed { index, uri ->
            if (hosts[index].lowercase() in duplicatedHosts) linkCompactLabel(uri) else hosts[index]
        }
    }

    fun isIntentUri(uri: String): Boolean =
        uri.startsWith("intent://", ignoreCase = true) || uri.startsWith("intent:", ignoreCase = true)

    fun normalizeOpenableUrl(raw: String): String? {
        val candidate = trimTrailingPunctuation(raw.trim())
        if (candidate.isBlank()) return null
        if (isBlockedScheme(candidate)) return null
        // intent: URI 用 ; 分隔且没有 ://，必须在 stop-char 判定之前放行
        if (isIntentUri(candidate)) {
            return candidate.takeIf { value -> value.none { it.isWhitespace() } }
        }
        // 含空白、中文标点或 CJK 文字的串不可能是一条网址，多半是整段文本
        if (anyUrlStopCharRegex.containsMatchIn(candidate)) return null
        return when {
            isSystemUri(candidate) -> candidate
            hasCustomScheme(candidate) -> candidate
            else -> normalizeWebUrl(candidate)
        }
    }

    private fun normalizeWebUrl(candidate: String): String? {
        val withScheme = when {
            candidate.startsWith("http://", ignoreCase = true) -> candidate
            candidate.startsWith("https://", ignoreCase = true) -> candidate
            candidate.startsWith("www.", ignoreCase = true) -> "https://$candidate"
            bareHostRegex.matches(candidate) -> {
                if (androidPackageRegex.matches(candidate)) return null
                "https://$candidate"
            }
            else -> return null
        }
        val sanitized = trimTrailingPunctuation(withScheme)
        if (!isPlausibleWebUrl(sanitized)) return null
        return sanitized
    }

    private fun isBlockedScheme(candidate: String): Boolean {
        val scheme = candidate.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme in blockedSchemes
    }

    private fun isSystemUri(candidate: String): Boolean {
        val scheme = candidate.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme in setOf("tel", "mailto", "sms", "geo") &&
            candidate.length > scheme.length + 1
    }

    private fun hasCustomScheme(candidate: String): Boolean {
        val colonIndex = candidate.indexOf("://")
        if (colonIndex <= 0) return false
        val scheme = candidate.substring(0, colonIndex)
        if (!scheme.matches(Regex("""[a-z][a-z0-9+.-]*""", RegexOption.IGNORE_CASE))) return false
        if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
            return false
        }
        return candidate.length > colonIndex + 3
    }

    private fun isPlausibleWebUrl(url: String): Boolean {
        val withoutScheme = url.substringAfter("://", missingDelimiterValue = url)
        val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.contains('.') && host.none { it.isWhitespace() }
    }

    private fun trimTrailingPunctuation(value: String): String {
        return value.trimEnd { ch ->
            ch in ".,;:!?)」》\"'、，。；：！？"
        }
    }
}
