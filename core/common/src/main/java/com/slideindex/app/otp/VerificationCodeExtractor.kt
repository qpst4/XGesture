package com.slideindex.app.otp

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

data class OtpExtractionConfig(
    val keywordsRegex: String = OtpKeywords.DEFAULT_KEYWORDS_REGEX,
    val matchRules: List<OtpMatchRule> = emptyList(),
) {
    companion object {
        fun build(
            keywordsRegex: String,
            officialRules: List<OtpMatchRule>,
            userRules: List<OtpMatchRule>,
            disabledOfficialRuleIds: Set<String>,
        ): OtpExtractionConfig {
            val enabledOfficial = officialRules.map { rule ->
                rule.copy(enabled = rule.id !in disabledOfficialRuleIds)
            }
            return OtpExtractionConfig(
                keywordsRegex = keywordsRegex,
                matchRules = enabledOfficial + userRules,
            )
        }
    }
}

data class OtpExtractionResult(
    val code: String?,
    val attempted: Boolean,
    val ruleName: String? = null,
)

object VerificationCodeExtractor {
    const val LEGACY_DEFAULT_KEYWORDS_REGEX = OtpKeywords.LEGACY_DEFAULT_KEYWORDS_REGEX

    const val DEFAULT_KEYWORDS_REGEX = OtpKeywords.DEFAULT_KEYWORDS_REGEX

    private val CODE_PATTERN = Regex("(?<!\\p{Nd})(\\p{Nd}{4,8})(?!\\p{Nd})")
    private val AFTER_KEYWORD_CODE_PATTERN = Regex("^[：:\\s为、，,.-]*(\\p{Nd}{4,8})")
    private const val KEYWORD_PROXIMITY_CHARS = 30

    // Some ROMs keep only the tail of an SMS notification body, e.g. "…证码是：123456…".
    // Anchors must be a whole keyword stem (never a bare "码") to avoid matching
    // 密码 / 身份证码 / 订单码 and producing a false positive.
    private val TRUNCATED_KEYWORD_ANCHOR = Regex(
        "(?:(?<!份)证码|验证码|校验码|检验码|确认码|激活码|动态码|安全码|认证码|识别码|" +
            "验证|校验|检验|确认|激活|动态|安全|认证|识别)(?:是|为)?[：:\\s]{0,3}",
    )
    private val AGGREGATED_NOTIFICATION_PREFIX = Regex("(?:^|\\n)\\[\\d+[条则]\\]")
    private val VALIDITY_CONTEXT = Regex("(?:有效|失效|过期|分钟|秒内)")
    private val DATE_TIME_PATTERN = Regex(
        "(?:20\\d{2}[-/年.]\\d{1,2}[-/月.]\\d{1,2}日?" +
            "|\\d{1,2}:\\d{2}(?::\\d{2})?" +
            "|\\d{4}年\\d{1,2}月\\d{1,2}日)",
    )
    private val SEPARATED_DIGITS = Regex("(?<![\\d])(\\d(?:\\s+\\d){3,7})(?![\\d])")
    private val WHITESPACE = Regex("\\s+")

    fun extract(
        packageName: String,
        title: String,
        text: String,
        config: OtpExtractionConfig,
    ): OtpExtractionResult {
        val combined = buildString {
            if (title.isNotBlank()) {
                append(title)
                append('\n')
            }
            if (text.isNotBlank()) append(text)
        }.trim()
        if (combined.isBlank()) return OtpExtractionResult(code = null, attempted = false)

        val enabledRules = config.matchRules.filter { it.enabled }
        for (rule in enabledRules) {
            if (!ruleAppliesToPackage(rule, packageName)) continue
            extractWithRule(combined, rule)?.let { code ->
                return OtpExtractionResult(code = code, attempted = true, ruleName = rule.name)
            }
        }

        val keywordRegex = compileRegex(config.keywordsRegex) ?: return OtpExtractionResult(
            code = null,
            attempted = false,
        )
        if (keywordRegex.containsMatchIn(combined)) {
            val code = findCodeDirectlyAfterKeywords(combined, keywordRegex)
                ?: findCodeNearKeywords(combined, keywordRegex)
            if (code != null) {
                return OtpExtractionResult(code = normalizeDigits(code), attempted = true)
            }
        }

        // Fallback 1: ROM-truncated keyword stem followed immediately by a code.
        findCodeAfterTruncatedAnchor(combined)?.let { code ->
            return OtpExtractionResult(code = normalizeDigits(code), attempted = true)
        }
        // Fallback 2: aggregated notification with an explicit validity hint and one candidate.
        findCodeInAggregatedNotification(combined)?.let { code ->
            return OtpExtractionResult(code = normalizeDigits(code), attempted = true)
        }
        return OtpExtractionResult(code = null, attempted = true)
    }

    private fun ruleAppliesToPackage(rule: OtpMatchRule, packageName: String): Boolean {
        val rulePackage = rule.packageName ?: return true
        return rulePackage == packageName
    }

    private fun extractWithRule(text: String, rule: OtpMatchRule): String? {
        if (!text.contains(rule.keyword, ignoreCase = false)) return null
        val regex = compileRegex(rule.regex) ?: return null
        val match = regex.find(text) ?: return null
        for (index in 1 until match.groupValues.size) {
            val group = match.groupValues[index]
            if (isValidCode(group)) return group
        }
        return normalizeCode(match.value)
    }

    private fun isValidCode(value: String): Boolean =
        value.length in 4..8 && value.all { it.isDigit() }

    private fun normalizeCode(value: String): String? {
        val digits = normalizeDigits(value.filter { it.isDigit() })
        return if (isValidCode(digits)) digits else null
    }

    /** Arabic-Indic / Extended Arabic-Indic digits are normalized to ASCII before use. */
    private fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            append(ch.digitToIntOrNull()?.let { ('0'.code + it).toChar() } ?: ch)
        }
    }

    private fun isDateTimeCandidate(text: String, codeRange: IntRange): Boolean =
        DATE_TIME_PATTERN.findAll(text).any { dateTime ->
            codeRange.first >= dateTime.range.first && codeRange.last <= dateTime.range.last
        }

    /**
     * Recovers a code from a truncated keyword stem such as "…证码是：123456…".
     * Only accepts a code immediately after the anchor, and only when it is the
     * sole 4-8 digit candidate in the window, so order numbers nearby are ignored.
     */
    private fun findCodeAfterTruncatedAnchor(text: String): String? {
        for (anchor in TRUNCATED_KEYWORD_ANCHOR.findAll(text)) {
            val afterStart = anchor.range.last + 1
            if (afterStart >= text.length) continue
            val windowEnd = minOf(text.length, afterStart + KEYWORD_PROXIMITY_CHARS)
            val window = text.substring(afterStart, windowEnd)
            val match = AFTER_KEYWORD_CODE_PATTERN.find(window) ?: continue
            val codeRange = match.groups[1]?.range ?: continue
            val code = match.groupValues[1]
            if (!isValidCode(code) || isDateTimeCandidate(window, codeRange)) continue
            val candidateCount = CODE_PATTERN.findAll(window).count { isValidCode(it.groupValues[1]) }
            if (candidateCount <= 1) return code
        }
        return null
    }

    /**
     * Aggregated notifications ("[2条]…123456（有效期5分钟）…") lose the keyword
     * entirely. Accept only with a validity hint and exactly one candidate.
     */
    private fun findCodeInAggregatedNotification(text: String): String? {
        if (!AGGREGATED_NOTIFICATION_PREFIX.containsMatchIn(text)) return null
        if (!VALIDITY_CONTEXT.containsMatchIn(text)) return null
        val candidates = CODE_PATTERN.findAll(text)
            .mapNotNull { match ->
                val value = match.groupValues[1]
                val range = match.groups[1]?.range ?: return@mapNotNull null
                if (isValidCode(value) && !isDateTimeCandidate(text, range)) value else null
            }
            .toList()
        return candidates.singleOrNull()
    }

    private fun compileRegex(pattern: String): Regex? =
        runCatching { Regex(pattern) }.getOrNull()

    private fun findCodeDirectlyAfterKeywords(text: String, keywordRegex: Regex): String? {
        for (keywordMatch in keywordRegex.findAll(text)) {
            val afterStart = keywordMatch.range.last + 1
            if (afterStart >= text.length) continue
            val windowEnd = minOf(text.length, afterStart + KEYWORD_PROXIMITY_CHARS)
            val window = text.substring(afterStart, windowEnd)
            AFTER_KEYWORD_CODE_PATTERN.find(window)?.let { match ->
                val code = match.groupValues[1]
                val range = match.groups[1]?.range
                if (isValidCode(code) && range != null && !isDateTimeCandidate(window, range)) {
                    return code
                }
            }
            // A code split by spaces, e.g. "1 2 3 4 5 6".
            SEPARATED_DIGITS.find(window)?.let { separated ->
                val compact = separated.groupValues[1].replace(WHITESPACE, "")
                if (isValidCode(compact)) return compact
            }
        }
        return null
    }

    private fun findCodeNearKeywords(text: String, keywordRegex: Regex): String? {
        val keywordRanges = keywordRegex.findAll(text).map { it.range }.toList()
        if (keywordRanges.isEmpty()) return null

        val candidates = CODE_PATTERN.findAll(text).mapNotNull { match ->
            val value = match.groupValues[1]
            val range = match.groups[1]?.range ?: return@mapNotNull null
            if (!isValidCode(value) || isDateTimeCandidate(text, range)) return@mapNotNull null
            val distance = keywordRanges.minOf { keywordRange ->
                minDistance(match.range, keywordRange)
            }
            value to distance
        }.filter { (_, distance) -> distance <= KEYWORD_PROXIMITY_CHARS }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenByDescending { it.first.length })

        return candidates.firstOrNull()?.first
    }

    private fun minDistance(a: IntRange, b: IntRange): Int {
        return when {
            a.last < b.first -> b.first - a.last
            b.last < a.first -> a.first - b.last
            else -> 0
        }
    }
}
