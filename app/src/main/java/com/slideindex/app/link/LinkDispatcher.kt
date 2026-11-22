package com.slideindex.app.link

import android.content.Context
import android.util.Base64
import android.util.Log
import com.slideindex.app.overlay.pickresult.PickResultUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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

        // 1. 若匹配类型是正则，预先解析主 pattern 捕获组（供 $1..$9 消费，并在 extractGroups 为空时回退给 {1}..{9}）
        val rulePatternMatch = if (rule.matchType == LinkMatchType.REGEX) {
            val regex = compile(rule.pattern)
            regex?.find(url)
        } else null

        // 2. 确定有效捕获组：优先独立正则提取；若无独立提取则自动回退主正则捕获组（完全对齐 LinkGo 规范）
        val fallbackGroups = rulePatternMatch?.groupValues?.drop(1).orEmpty()
        val effectiveGroups = if (extractGroups.isNotEmpty()) extractGroups else fallbackGroups

        return TEMPLATE_PLACEHOLDER.replace(template) { match ->
            val backReference = match.groupValues[1]
            if (backReference.isNotEmpty()) {
                val index = backReference.toIntOrNull() ?: return@replace match.value
                return@replace rulePatternMatch?.groupValues?.getOrNull(index) ?: match.value
            }
            val key = match.groupValues[2]
            val raw = if (key == "url") {
                url
            } else {
                val index = key.toIntOrNull()?.takeIf { it > 0 } ?: return@replace ""
                effectiveGroups.getOrNull(index - 1) ?: return@replace ""
            }
            applyTransform(raw, match.groupValues[3].ifEmpty { null })
        }
    }

    private fun applyTransform(value: String, transformSuffix: String?): String {
        if (transformSuffix.isNullOrBlank()) return value
        var current = value
        // 将形如 "_url_enc"、"_url_dec_b64_dec" 等后缀按 '_' 切割依次流水线执行
        val ops = transformSuffix.split('_').filter { it.isNotBlank() }
        for (op in ops) {
            current = when (op.lowercase()) {
                "url_enc" -> runCatching { URLEncoder.encode(current, "UTF-8") }.getOrDefault(current)
                "url_dec" -> runCatching { URLDecoder.decode(current, "UTF-8") }.getOrDefault(current)
                "b64_enc" -> Base64.encodeToString(current.toByteArray(), Base64.NO_WRAP)
                "b64_dec" -> runCatching {
                    String(Base64.decode(current, Base64.DEFAULT))
                }.getOrDefault(current)
                else -> current
            }
        }
        return current
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
    ): LinkRouteTrace = routeRulesWithTrace(
        context = context,
        rawUrl = rawUrl,
        rules = LinkRuleRepository.loadRules(context),
        exempts = LinkRuleRepository.loadExemptDomains(context),
        sourcePackage = sourcePackage,
        sourceText = sourceText,
    )

    suspend fun routeCandidateWithTrace(
        context: Context,
        rawUrl: String,
        candidateRule: LinkDispatchRule,
        sourcePackage: String = "",
        sourceText: String? = null,
    ): LinkRouteTrace = withContext(Dispatchers.IO) {
        val normalized = PickResultUrl.normalizeOpenableUrl(rawUrl) ?: rawUrl.trim()
        val steps = mutableListOf<LinkRouteStep>()

        // 1. 严格针对当前候选规则进行判定，不匹配则绝不跨规则去全库盲配
        if (!matchRule(normalized, candidateRule)) {
            return@withContext LinkRouteTrace(
                inputUrl = normalized,
                steps = emptyList(),
                finalResult = LinkDispatchResult.NoRule,
            )
        }

        var currentUrl = normalized
        var resolvedUrl: String? = null

        // 2. 如果开启短链解析
        if (candidateRule.resolveShortLink && candidateRule.resolveStrategy != LinkResolveStrategy.NONE) {
            val before = currentUrl
            resolvedUrl = resolveShortLink(currentUrl)
            steps.add(
                LinkRouteStep(
                    ruleName = candidateRule.name.ifBlank { "当前规则" },
                    fromUrl = before,
                    resolvedUrl = resolvedUrl,
                )
            )
            if (resolvedUrl != null) {
                currentUrl = resolvedUrl
            }
        }

        // 3. 执行模板改写与正则捕获组提取
        val groups = candidateRule.extractPattern?.let { pattern ->
            val regex = compile(pattern)
            val match = regex?.find(currentUrl) ?: sourceText?.let { regex?.find(it) }
            match?.groupValues?.drop(1)
        }.orEmpty()
        val rewrittenUrl = applyTemplate(currentUrl, candidateRule, groups)
        val finalCandidateUrl = if (rewrittenUrl.isNotBlank()) rewrittenUrl else currentUrl

        // 4. 后续流向推演
        if (candidateRule.resolveStrategy == LinkResolveStrategy.REDISPATCH) {
            // 当前规则为短链重分发：以其产出结果继续去全库其余规则推演后续路由
            val remainingRules = LinkRuleRepository.loadRules(context).filterNot { it.id == candidateRule.id }
            val exempts = LinkRuleRepository.loadExemptDomains(context)
            val subTrace = routeRulesWithTrace(
                context = context,
                rawUrl = finalCandidateUrl,
                rules = remainingRules,
                exempts = exempts,
                sourcePackage = sourcePackage,
                sourceText = sourceText,
            )
            // 候选规则已命中；若后续规则库无接管者，应降级为系统默认打开流程，而非误判当前规则未命中
            val finalRes = when (subTrace.finalResult) {
                is LinkDispatchResult.NoRule -> LinkDispatchResult.SystemDefault(finalCandidateUrl)
                else -> subTrace.finalResult
            }
            LinkRouteTrace(
                inputUrl = normalized,
                steps = steps + subTrace.steps,
                finalResult = finalRes,
            )
        } else {
            // 普通直达跳转规则
            if (candidateRule.targetPackage.isBlank()) {
                LinkRouteTrace(
                    inputUrl = normalized,
                    steps = steps,
                    finalResult = LinkDispatchResult.SystemDefault(finalCandidateUrl),
                )
            } else {
                LinkRouteTrace(
                    inputUrl = normalized,
                    steps = steps,
                    finalResult = LinkDispatchResult.Routed(candidateRule, finalCandidateUrl),
                )
            }
        }
    }

    private suspend fun routeRulesWithTrace(
        context: Context,
        rawUrl: String,
        rules: List<LinkDispatchRule>,
        exempts: List<LinkExemptDomain>,
        sourcePackage: String = "",
        sourceText: String? = null,
    ): LinkRouteTrace = withContext(Dispatchers.IO) {
        val normalized = PickResultUrl.normalizeOpenableUrl(rawUrl) ?: rawUrl
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

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun resolveShortLink(url: String): String? = withContext(Dispatchers.IO) {
        resolvedShortLinks[url]?.let { return@withContext it }
        val resolved = resolveUrlInternal(url)
        val result = if (!resolved.isNullOrBlank() && resolved != url) resolved else null
        if (result != null) {
            if (resolvedShortLinks.size >= SHORT_LINK_CACHE_LIMIT) resolvedShortLinks.clear()
            resolvedShortLinks[url] = result
        }
        result
    }

    /**
     * 深度追踪短链重定向（对标 LinkGo 完整算法）：
     * 1. 现代完整 Chrome 120 UA + 中文语言头；
     * 2. 跨跳自动维持并透传 Set-Cookie，防止知乎/微博等平台风控；
     * 3. 兼容 HTTP 3xx Location 相对/绝对路径解析；
     * 4. 兼容 HTTP 200 下 HTML Meta Refresh 刷新标签嗅探；
     * 5. 兼容 HTTP 200 下 JavaScript window.location 重定向嗅探与字面量还原。
     */
    private suspend fun resolveUrlInternal(originalUrl: String): String = withContext(Dispatchers.IO) {
        var currentUrl = originalUrl
        var cookie: String? = null
        val maxRedirects = 10

        try {
            repeat(maxRedirects) {
                coroutineContext.ensureActive()
                val requestBuilder = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

                if (cookie != null) {
                    requestBuilder.header("Cookie", cookie)
                }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    response.header("Set-Cookie")?.let { setCookie ->
                        cookie = setCookie.split(";").getOrNull(0)
                    }

                    val code = response.code
                    val location = response.header("Location")

                    // 模式一：标准的 3xx 重定向
                    if (code in 300..399 && !location.isNullOrBlank()) {
                        currentUrl = resolveRelativeUrl(currentUrl, location)
                        return@repeat
                    }

                    // 模式二与三：200 OK，但内容里藏了跳转
                    if (code == 200) {
                        val body = response.body.string()

                        // A. 匹配 HTML Meta 刷新标签
                        val metaUrl = extractMetaRefreshUrl(body)
                        if (!metaUrl.isNullOrBlank()) {
                            currentUrl = resolveRelativeUrl(currentUrl, metaUrl)
                            return@repeat
                        }

                        // B. 匹配 JavaScript 常见跳转
                        val jsUrl = extractJavaScriptUrl(body)
                        if (!jsUrl.isNullOrBlank()) {
                            currentUrl = resolveRelativeUrl(currentUrl, jsUrl)
                            return@repeat
                        }

                        // C. 兜底奇葩情况：部分平台 200 + Location 模式
                        if (!location.isNullOrBlank()) {
                            currentUrl = resolveRelativeUrl(currentUrl, location)
                            return@repeat
                        }
                    }

                    return@withContext currentUrl
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析重定向异常: ${e.message}")
            return@withContext currentUrl
        }
        currentUrl
    }

    private fun resolveRelativeUrl(baseUrl: String, location: String): String {
        return try {
            val base = URL(baseUrl)
            val resolved = URL(base, location)
            resolved.toString()
        } catch (e: Exception) {
            location
        }
    }

    private fun extractMetaRefreshUrl(html: String): String? {
        try {
            val pattern = java.util.regex.Pattern.compile(
                "<meta[^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*url\\s*=\\s*[\"']?([^\"'>\\s]+)[\"']?",
                java.util.regex.Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val url = matcher.group(1)
                return url?.replace("&amp;", "&")?.let { decodeLiteral(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractMetaRefreshUrl 异常", e)
        }
        return null
    }

    private fun extractJavaScriptUrl(html: String): String? {
        try {
            val scriptPattern = java.util.regex.Pattern.compile(
                "<script[^>]*>(.*?)</script>",
                java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.DOTALL
            )
            val scriptMatcher = scriptPattern.matcher(html)

            while (scriptMatcher.find()) {
                val scriptContent = scriptMatcher.group(1)?.trim() ?: continue
                val locPattern = java.util.regex.Pattern.compile(
                    "window\\.location(?:\\.href)?\\s*=\\s*(?:decodeURIComponent\\()?([a-zA-Z0-9_\"'/.:?=~%-]+)\\)?\\s*;?",
                    java.util.regex.Pattern.CASE_INSENSITIVE
                )
                val locMatcher = locPattern.matcher(scriptContent)

                if (locMatcher.find()) {
                    val rightHandSide = locMatcher.group(1)?.trim() ?: continue

                    if ((rightHandSide.startsWith("\"") && rightHandSide.endsWith("\"")) ||
                        (rightHandSide.startsWith("'") && rightHandSide.endsWith("'"))) {
                        return rightHandSide.substring(1, rightHandSide.length - 1)
                    }

                    val varName = rightHandSide
                    val varPattern = java.util.regex.Pattern.compile(
                        "(?:var|let|const)?\\s*" + java.util.regex.Pattern.quote(varName) + "\\s*=\\s*[\"']([^\"']+)[\"']",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                    )
                    val varMatcher = varPattern.matcher(scriptContent)
                    if (varMatcher.find()) {
                        return varMatcher.group(1)?.let { decodeLiteral(it, percentDecode = true) }
                    }

                    if (rightHandSide.startsWith("http://") || rightHandSide.startsWith("https://")) {
                        return rightHandSide
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractJavaScriptUrl 异常", e)
        }
        return null
    }

    private fun decodeLiteral(raw: String, percentDecode: Boolean = false): String {
        val needPercent = percentDecode && raw.contains('%')
        if (!raw.contains('\\') && !needPercent) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                sb.append(c); i++; continue
            }
            when (val e = raw[i + 1]) {
                'u', 'x' -> {
                    val n = if (e == 'u') 4 else 2
                    val hex = raw.substring(i + 2, minOf(i + 2 + n, raw.length))
                    val code = if (hex.length == n) hex.toIntOrNull(16) else null
                    if (code != null) { sb.append(code.toChar()); i += 2 + n } else { sb.append(e); i += 2 }
                }
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                else -> { sb.append(e); i += 2 }
            }
        }
        val unescaped = sb.toString()
        if (!needPercent) return unescaped
        return if (unescaped.contains('%') && !unescaped.contains("://")) {
            try {
                URLDecoder.decode(unescaped.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                unescaped
            }
        } else unescaped
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
        Regex("""\$(\d)|\{(url|\d+)((?:_[a-zA-Z0-9]+)*)\}""")
}
