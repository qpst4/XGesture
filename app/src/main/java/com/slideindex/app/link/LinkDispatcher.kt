package com.slideindex.app.link

import android.content.Context
import android.util.Base64
import android.util.Log
import com.slideindex.app.overlay.pickresult.PickResultUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** 链接分发计算结果。 */
sealed class LinkDispatchResult {
    data class Routed(val rule: LinkDispatchRule, val url: String) : LinkDispatchResult()
    data class Exempted(val domain: String, val url: String) : LinkDispatchResult()
    data class SystemDefault(val url: String) : LinkDispatchResult()
    data object NoRule : LinkDispatchResult()
    data object Failed : LinkDispatchResult()
}

/** 单步短链解析与重分发追踪记录（用于沙盒测试展示完整链路）。 */
data class LinkRouteStep(
    val ruleName: String,
    val fromUrl: String,
    val resolvedUrl: String?,
)

/** 携带完整短链解析过程的路由结果。 */
data class LinkRouteTrace(
    val inputUrl: String,
    val steps: List<LinkRouteStep>,
    val finalResult: LinkDispatchResult,
)

/**
 * 链接匹配、重写与短链追踪分发核心引擎。
 */
object LinkDispatcher {
    private const val TAG = "LinkDispatcher"

    private val cachedPatterns = ConcurrentHashMap<String, Regex>()
    private val resolvedShortLinks = ConcurrentHashMap<String, String>()
    private const val SHORT_LINK_CACHE_LIMIT = 64

    fun matchRule(url: String, rule: LinkDispatchRule): Boolean {
        if (rule.pattern.isBlank()) return false
        return when (rule.matchType) {
            LinkMatchType.REGEX -> compile(rule.pattern)?.containsMatchIn(url) ?: false
            LinkMatchType.EXACT -> url.equals(rule.pattern, ignoreCase = true)
            LinkMatchType.CONTAINS -> matchesContains(url, rule.pattern)
        }
    }

    private fun matchesContains(url: String, pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (looksLikeDomain(trimmed)) {
            val host = runCatching { URL(url).host }.getOrNull() ?: return false
            return host.equals(trimmed, ignoreCase = true) ||
                    host.endsWith(".$trimmed", ignoreCase = true)
        }
        return url.contains(trimmed, ignoreCase = true)
    }

    private fun looksLikeDomain(value: String): Boolean =
        DOMAIN_SHAPE.containsMatchIn(value) && !value.contains('/') && !value.contains(' ')

    fun applyTemplate(
        url: String,
        rule: LinkDispatchRule,
        extractGroups: List<String> = emptyList()
    ): String {
        val template = rule.template?.takeIf { it.isNotBlank() } ?: return url
        val captureMatch = if (rule.matchType == LinkMatchType.REGEX && BACK_REFERENCE.containsMatchIn(template)) {
            val regex = compile(rule.pattern) ?: return url
            regex.find(url) ?: return url
        } else null

        return TEMPLATE_PLACEHOLDER.replace(template) { match ->
            val backReference = match.groupValues[1]
            if (backReference.isNotEmpty()) {
                return@replace captureMatch?.groupValues?.getOrNull(backReference.toInt()) ?: match.value
            }
            val key = match.groupValues[2]
            val raw = if (key == "url") url else
                key.toIntOrNull()?.takeIf { it > 0 }?.let { extractGroups.getOrNull(it - 1) } ?: return@replace ""
            applyTransform(raw, match.groupValues[3].ifEmpty { null })
        }
    }

    private fun applyTransform(value: String, transform: String?): String = when (transform) {
        "url_dec" -> runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        "b64_enc" -> Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)
        "b64_dec" -> runCatching {
            String(Base64.decode(value, Base64.DEFAULT))
        }.getOrDefault(value)

        "url_dec_b64_dec" -> runCatching {
            val decoded = URLDecoder.decode(value, "UTF-8")
            String(Base64.decode(decoded, Base64.DEFAULT))
        }.getOrDefault(value)

        else -> value
    }

    fun isExempt(url: String, sourcePackage: String, exempts: List<LinkExemptDomain>): String? {
        val host = runCatching { URL(url).host?.lowercase() }.getOrNull() ?: return null
        return exempts.firstOrNull { exempt ->
            if (!exempt.isEnabled || exempt.domain.isBlank()) return@firstOrNull false
            if (exempt.sourcePackage.isNotEmpty() &&
                !exempt.sourcePackage.equals(sourcePackage, ignoreCase = true)
            ) {
                return@firstOrNull false
            }
            val domain = exempt.domain.lowercase()
            host == domain || host.endsWith(".$domain")
        }?.domain
    }

    fun findRule(
        url: String,
        rules: List<LinkDispatchRule>,
    ): LinkDispatchRule? = rules
        .asSequence()
        .filter { it.isEnabled }
        .filter { rule ->
            if (rule.targetPackage.isBlank() &&
                !(rule.resolveShortLink && rule.resolveStrategy == LinkResolveStrategy.REDISPATCH)) {
                return@filter false
            }
            matchRule(url, rule)
        }
        .minByOrNull { it.priority }

    /**
     * 检查给定的 URL 是否会命中一条尚未缓存的短链解析规则（即需要发起网络探测）。
     * 若命中且未缓存，返回该短链规则名称；否则返回 null。
     */
    suspend fun pendingShortLinkRuleName(context: Context, rawUrl: String): String? = withContext(Dispatchers.IO) {
        val normalized = PickResultUrl.normalizeOpenableUrl(rawUrl) ?: rawUrl
        val rules = LinkRuleRepository.loadRules(context)
        val exempts = LinkRuleRepository.loadExemptDomains(context)
        if (isExempt(normalized, "", exempts) != null) return@withContext null
        val rule = findRule(normalized, rules) ?: return@withContext null
        if (rule.resolveShortLink && rule.resolveStrategy != LinkResolveStrategy.NONE && !resolvedShortLinks.containsKey(normalized)) {
            rule.name.ifBlank { rule.pattern }
        } else {
            null
        }
    }

    suspend fun routeWithTrace(
        context: Context,
        rawUrl: String,
        sourcePackage: String = "",
        sourceText: String? = null,
    ): LinkRouteTrace = withContext(Dispatchers.IO) {
        val normalized = PickResultUrl.normalizeOpenableUrl(rawUrl) ?: rawUrl
        val rules = LinkRuleRepository.loadRules(context)
        val exempts = LinkRuleRepository.loadExemptDomains(context)
        val steps = mutableListOf<LinkRouteStep>()

        var target = normalized
        val visited = mutableSetOf<String>()
        var finalResult: LinkDispatchResult = LinkDispatchResult.Failed
        var finished = false

        repeat(MAX_REDIRECTS + 1) {
            if (finished) return@repeat
            if (!visited.add(target)) {
                finalResult = LinkDispatchResult.Failed
                finished = true
                return@repeat
            }
            coroutineContext.ensureActive()
            val exemptDomain = isExempt(target, sourcePackage, exempts)
            if (exemptDomain != null) {
                finalResult = LinkDispatchResult.Exempted(exemptDomain, target)
                finished = true
                return@repeat
            }
            val rule = findRule(target, rules)
            if (rule == null) {
                finalResult = if (target == normalized) LinkDispatchResult.NoRule else LinkDispatchResult.SystemDefault(target)
                finished = true
                return@repeat
            }
            if (rule.resolveShortLink && rule.resolveStrategy != LinkResolveStrategy.NONE) {
                val beforeResolve = target
                val resolved = resolveShortLink(target)
                steps.add(
                    LinkRouteStep(
                        ruleName = rule.name.ifBlank { rule.pattern },
                        fromUrl = beforeResolve,
                        resolvedUrl = resolved,
                    )
                )
                if (resolved != null && resolved != target && rule.resolveStrategy == LinkResolveStrategy.REDISPATCH) {
                    target = resolved
                    return@repeat
                }
                target = resolved ?: target
            }
            if (rule.targetPackage.isBlank()) {
                finalResult = LinkDispatchResult.SystemDefault(target)
                finished = true
                return@repeat
            }
            val groups = rule.extractPattern?.let { pattern ->
                val regex = compile(pattern)
                val match = regex?.find(target) ?: sourceText?.let { regex?.find(it) }
                match?.groupValues?.drop(1)
            }.orEmpty()
            val finalUrl = applyTemplate(target, rule, groups)
            finalResult = if (finalUrl.isBlank()) LinkDispatchResult.Failed else LinkDispatchResult.Routed(rule, finalUrl)
            finished = true
        }

        LinkRouteTrace(
            inputUrl = normalized,
            steps = steps,
            finalResult = finalResult,
        )
    }

    suspend fun route(
        context: Context,
        rawUrl: String,
        sourcePackage: String = "",
        sourceText: String? = null,
        resolveShortLinks: Boolean = true,
    ): LinkDispatchResult = withContext(Dispatchers.IO) {
        val normalized = PickResultUrl.normalizeOpenableUrl(rawUrl) ?: rawUrl
        val rules = LinkRuleRepository.loadRules(context)
        val exempts = LinkRuleRepository.loadExemptDomains(context)

        if (resolveShortLinks) routeWithRules(normalized, rules, exempts, sourcePackage, sourceText)
        else routeWithRules(normalized, rules, exempts, sourcePackage, sourceText, resolver = { null })
    }

    internal suspend fun routeWithRules(
        url: String,
        rules: List<LinkDispatchRule>,
        exempts: List<LinkExemptDomain> = emptyList(),
        sourcePackage: String = "",
        sourceText: String? = null,
        resolver: suspend (String) -> String? = { resolveShortLink(it) }
    ): LinkDispatchResult {
        var target = url
        val visited = mutableSetOf<String>()
        repeat(MAX_REDIRECTS + 1) {
            if (!visited.add(target)) return LinkDispatchResult.Failed
            coroutineContext.ensureActive()
            isExempt(target, sourcePackage, exempts)?.let { return LinkDispatchResult.Exempted(it, target) }
            val rule = findRule(target, rules)
                ?: return if (target == url) LinkDispatchResult.NoRule else
                    LinkDispatchResult.SystemDefault(target)
            if (rule.resolveShortLink && rule.resolveStrategy != LinkResolveStrategy.NONE) {
                val resolved = resolver(target)
                if (resolved != null && resolved != target && rule.resolveStrategy == LinkResolveStrategy.REDISPATCH) {
                    target = resolved
                    return@repeat
                }
                target = resolved ?: target
            }
            if (rule.targetPackage.isBlank()) return LinkDispatchResult.SystemDefault(target)
            val groups = rule.extractPattern?.let { pattern ->
                val regex = compile(pattern)
                val match = regex?.find(target) ?: sourceText?.let { regex?.find(it) }
                match?.groupValues?.drop(1)
            }.orEmpty()
            val finalUrl = applyTemplate(target, rule, groups)
            return if (finalUrl.isBlank()) LinkDispatchResult.Failed else
                LinkDispatchResult.Routed(rule, finalUrl)
        }
        return LinkDispatchResult.Failed
    }

    suspend fun resolveShortLink(url: String): String? = withContext(Dispatchers.IO) {
        resolvedShortLinks[url]?.let { return@withContext it }
        val current = followRedirects(url) { headRedirect(it) }
        if (current != null) {
            if (resolvedShortLinks.size >= SHORT_LINK_CACHE_LIMIT) resolvedShortLinks.clear()
            resolvedShortLinks[url] = current
        }
        current
    }

    private suspend fun followRedirects(url: String, probe: (String) -> String?): String? {
        var current = url
        val visited = mutableSetOf(url)
        repeat(MAX_REDIRECTS + 1) { hop ->
            coroutineContext.ensureActive()
            val next = probe(current) ?: return null
            if (next == current) return current.takeIf { it != url }
            if (hop == MAX_REDIRECTS || !visited.add(next)) return null
            current = next
        }
        return null
    }

    private fun headRedirect(url: String): String? {
        return probeRedirectWithMethod(url, "HEAD") ?: probeRedirectWithMethod(url, "GET")
    }

    private fun probeRedirectWithMethod(url: String, method: String): String? {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                instanceFollowRedirects = false
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
        }.getOrNull() ?: return null

        return try {
            val code = connection.responseCode
            when (code) {
                301, 302, 303, 307, 308 -> connection.getHeaderField("Location")
                    ?.let { URL(URL(url), it).takeIf { next -> next.protocol in listOf("http", "https") }?.toString() }
                in 200..299 -> if (method == "GET") url else null
                else -> null
            }
        } catch (error: Exception) {
            Log.w(TAG, "Short link probe ($method) failed", error)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun compile(pattern: String): Regex? =
        cachedPatterns.getOrPut(pattern) {
            runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }
                .getOrDefault(INVALID_PATTERN)
        }.takeIf { it !== INVALID_PATTERN }

    private const val MAX_REDIRECTS = 5
    private const val NETWORK_TIMEOUT_MS = 5_000
    private val DOMAIN_SHAPE = Regex("^[A-Za-z0-9][A-Za-z0-9.-]*\\.[A-Za-z]{2,}$")
    private val INVALID_PATTERN = Regex("(?!)")
    private val BACK_REFERENCE = Regex("""\$(\d)""")
    private val TEMPLATE_PLACEHOLDER =
        Regex("""\$(\d)|\{(url|\d+)(?:_(url_dec|b64_enc|b64_dec|url_dec_b64_dec))?\}""")
}
