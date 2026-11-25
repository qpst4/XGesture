package com.slideindex.app.link

import android.content.Context
import com.slideindex.app.overlay.pickresult.PickResultUrl
import java.util.concurrent.ConcurrentHashMap

data class LinkTextMatch(val url: String, val start: Int, val end: Int)

/**
 * 链接提取引擎。
 * 优先使用配置的抽取正则（涵盖磁力链接、提取码等非标准协议），
 * 未命中时回退到 PickResultUrl 规范化抽取。
 */
object LinkExtractor {

    private val cachedRegex = ConcurrentHashMap<String, Regex>()
    private val INVALID_PATTERN = Regex("(?!)")

    fun extract(text: String, patterns: List<LinkExtractPattern>): String? {
        if (text.isBlank()) return null
        val fromPatterns = configuredMatches(text, patterns).maxByOrNull { it.url.length }?.url
        if (fromPatterns != null) return fromPatterns
        return PickResultUrl.extractOpenableUrls(text).firstOrNull()
    }

    suspend fun extractWithStoredPatterns(context: Context, text: String): String? {
        val patterns = LinkRuleRepository.loadExtractPatterns(context)
        return extract(text, patterns)
    }

    fun findAll(text: String, patterns: List<LinkExtractPattern>): List<LinkTextMatch> {
        val selected = selectLinkTextMatches(emptyList(), configuredMatches(text, patterns).toList())
        var offset = 0
        val detected = PickResultUrl.extractOpenableUrls(text).mapNotNull { url ->
            val start = text.indexOf(url, offset)
            if (start < 0) null else {
                offset = start + url.length
                LinkTextMatch(url, start, offset)
            }
        }
        return selectLinkTextMatches(selected, detected)
    }

    /** 只使用启用的配置规则，不附加内置 URL 识别兜底。测试页必须用这个。 */
    fun findAllConfigured(text: String, patterns: List<LinkExtractPattern>): List<LinkTextMatch> =
        selectLinkTextMatches(emptyList(), configuredMatches(text, patterns).toList())

    private fun configuredMatches(text: String, patterns: List<LinkExtractPattern>): Sequence<LinkTextMatch> =
        patterns.asSequence().filter { it.isEnabled && it.pattern.isNotBlank() }.flatMap { pattern ->
            compile(pattern.pattern)?.findAll(text)?.mapNotNull { match ->
                val value = match.value.trim()
                value.takeIf { it.isNotEmpty() }?.let {
                    val start = match.range.first + match.value.indexOf(value)
                    LinkTextMatch(value, start, start + value.length)
                }
            } ?: emptySequence()
        }

    private fun compile(pattern: String): Regex? =
        cachedRegex.getOrPut(pattern) {
            runCatching { Regex(pattern) }.getOrDefault(INVALID_PATTERN)
        }.takeIf { it !== INVALID_PATTERN }
}

fun selectLinkTextMatches(
    preferred: List<LinkTextMatch>,
    candidates: List<LinkTextMatch>
): List<LinkTextMatch> {
    val selected = preferred.filter { it.start >= 0 && it.end > it.start && it.url.isNotBlank() }.toMutableList()
    candidates.sortedByDescending { it.end - it.start }.forEach { candidate ->
        if (candidate.start >= 0 && candidate.end > candidate.start && candidate.url.isNotBlank() &&
            selected.none { candidate.start < it.end && candidate.end > it.start }) {
            selected += candidate
        }
    }
    return selected.sortedBy { it.start }
}
