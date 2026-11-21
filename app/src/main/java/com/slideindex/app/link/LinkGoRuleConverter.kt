package com.slideindex.app.link

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 解析并导入 LinkGo 格式的规则导出文件（`{"linkgo_rules":[...]}` 或 `[...]`）。
 */
object LinkGoRuleConverter {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable
    data class LinkGoExport(val linkgo_rules: List<LinkGoRule> = emptyList())

    @Serializable
    data class LinkGoRule(
        val id: String = "",
        val name: String = "",
        val pattern: String = "",
        val matchType: String = "REGEX",
        val targetPackage: String = "",
        val template: String? = null,
        val extractPattern: String? = null,
        val isEnabled: Boolean = true,
        val resolveShortLink: Boolean = false,
        val resolveStrategy: String = "NONE",
        val excludeFromRecents: Boolean = false
    )

    /** 解析导出的 LinkGo JSON 文本。 */
    fun parse(json: String): List<LinkDispatchRule> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        val goRules: List<LinkGoRule> = runCatching {
            if (trimmed.startsWith('[')) {
                jsonParser.decodeFromString<List<LinkGoRule>>(trimmed)
            } else {
                jsonParser.decodeFromString<LinkGoExport>(trimmed).linkgo_rules
            }
        }.getOrDefault(emptyList())
        return goRules.mapIndexed { index, rule -> convert(rule, index) }
    }

    /** 从 assets 中加载内置的 LinkGo 规则库。 */
    fun loadBuiltin(context: Context): List<LinkDispatchRule> = runCatching {
        val json = context.assets.open("linkgo_builtin_rules.json")
            .bufferedReader().use { it.readText() }
        parse(json)
    }.getOrDefault(emptyList())

    private fun convert(rule: LinkGoRule, index: Int): LinkDispatchRule {
        val (rawPkg, rawComponent) = splitTarget(rule.targetPackage)
        val strategy = parseResolveStrategy(rule.resolveStrategy)
        val isSelfRedispatch = rawPkg.equals("com.moting.linkgo", ignoreCase = true) ||
            (strategy == LinkResolveStrategy.REDISPATCH && rawPkg.contains("linkgo", ignoreCase = true))
        val pkg = if (isSelfRedispatch) "" else rawPkg
        val component = if (isSelfRedispatch) null else rawComponent
        return LinkDispatchRule(
            name = rule.name,
            pattern = rule.pattern,
            targetPackage = pkg,
            component = component,
            matchType = parseEnum(rule.matchType, LinkMatchType.CONTAINS),
            template = rule.template?.takeIf { it.isNotEmpty() },
            extractPattern = rule.extractPattern?.takeIf { it.isNotEmpty() },
            isEnabled = rule.isEnabled,
            resolveShortLink = rule.resolveShortLink || strategy != LinkResolveStrategy.NONE,
            resolveStrategy = strategy,
            excludeFromRecents = rule.excludeFromRecents,
            priority = index
        )
    }

    private fun parseResolveStrategy(value: String): LinkResolveStrategy {
        return when (value.trim().uppercase()) {
            "RE_DISPATCH", "REDISPATCH" -> LinkResolveStrategy.REDISPATCH
            "DIRECT" -> LinkResolveStrategy.DIRECT
            "NONE", "" -> LinkResolveStrategy.NONE
            else -> parseEnum(value, LinkResolveStrategy.NONE)
        }
    }

    /** 拆分 `pkg/activity` 格式为包名与组件名。 */
    fun splitTarget(target: String): Pair<String, String?> {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) return "" to null
        val slash = trimmed.indexOf('/')
        if (slash <= 0 || slash == trimmed.length - 1) return trimmed to null
        return trimmed.substring(0, slash) to trimmed.substring(slash + 1)
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}
