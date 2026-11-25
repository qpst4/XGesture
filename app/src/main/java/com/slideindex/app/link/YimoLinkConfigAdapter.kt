package com.slideindex.app.link

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 一墨（floatwidget）链接配置适配器。
 *
 * 支持两个独立导出根：
 * - `extract_patterns`：链接提取规则
 * - `rules`：链接分发规则（snake_case）
 */
object YimoLinkConfigAdapter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): LinkConfigBackup? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return null
        val extracts = root["extract_patterns"]?.jsonArray?.mapNotNull(::mapExtract).orEmpty()
        val rules = root["rules"]?.jsonArray?.mapNotNull { element ->
            runCatching { mapRule(element.jsonObject) }.getOrNull()
        }.orEmpty()
        if (extracts.isEmpty() && rules.isEmpty()) return null
        return LinkConfigBackup(rules = rules, extracts = extracts)
    }

    private fun mapExtract(element: kotlinx.serialization.json.JsonElement): LinkExtractPattern? {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        val pattern = obj.text("pattern")?.takeIf { it.isNotBlank() } ?: return null
        return LinkExtractPattern(
            id = obj.text("id").orEmpty(),
            name = obj.text("name").orEmpty(),
            pattern = pattern,
            isEnabled = obj.bool("is_enabled") ?: true,
            isBuiltin = obj.bool("is_builtin") ?: false,
        )
    }

    private fun mapRule(obj: JsonObject): LinkDispatchRule? {
        val pattern = obj.text("pattern")?.takeIf { it.isNotBlank() } ?: return null
        // 一墨目前导出的 action 全是 OPEN；其他 action 没有对应执行语义，跳过。
        val action = obj.text("action").orEmpty().uppercase()
        if (action.isNotBlank() && action != "OPEN") return null
        val targetPackage = obj.text("target_package").orEmpty()
        val targetClass = obj.text("target_class").orEmpty()
        val (pkg, componentFromPackage) = LinkGoRuleConverter.splitTarget(targetPackage)
        val component = componentFromPackage ?: targetClass.takeIf { it.isNotBlank() }
        return LinkDispatchRule(
            id = obj.text("id").orEmpty(),
            name = obj.text("name").orEmpty(),
            pattern = pattern,
            targetPackage = pkg,
            matchType = when (obj.text("match_type").orEmpty().uppercase()) {
                "REGEX" -> LinkMatchType.REGEX
                "EXACT" -> LinkMatchType.EXACT
                else -> LinkMatchType.CONTAINS
            },
            template = obj.text("template")?.takeIf { it.isNotBlank() },
            isEnabled = obj.bool("is_enabled") ?: true,
            resolveShortLink = obj.bool("resolve_short_link") ?: false,
            resolveStrategy = when (obj.text("resolve_strategy").orEmpty().uppercase()) {
                "DIRECT" -> LinkResolveStrategy.DIRECT
                "RE_DISPATCH", "REDISPATCH" -> LinkResolveStrategy.REDISPATCH
                else -> LinkResolveStrategy.NONE
            },
            excludeFromRecents = obj.bool("exclude_from_recents") ?: false,
            component = component,
        )
    }

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
}
