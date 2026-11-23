package com.slideindex.app.link

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * 链接分发全量配置的序列化与合并处理器。
 */
object LinkConfigCodec {

    private val jsonPretty = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val jsonNormal = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun export(
        rules: List<LinkDispatchRule>,
        exempts: List<LinkExemptDomain>,
        extracts: List<LinkExtractPattern>
    ): String {
        val backup = LinkConfigBackup(
            rules = rules,
            exempts = exempts,
            extracts = extracts
        )
        return jsonPretty.encodeToString(backup)
    }

    fun decode(json: String): LinkConfigBackup? = runCatching {
        val root = jsonNormal.parseToJsonElement(json).jsonObject
        val firstRuleKeys = root["rules"]
            ?.let { it as? kotlinx.serialization.json.JsonArray }
            ?.firstOrNull()
            ?.let { it as? kotlinx.serialization.json.JsonObject }
            ?.keys
            .orEmpty()
        val looksLikeOwnBackup =
            root["version"] != null ||
                root["extracts"] != null ||
                root["exempts"] != null ||
                "targetPackage" in firstRuleKeys ||
                "matchType" in firstRuleKeys ||
                "resolveShortLink" in firstRuleKeys ||
                "resolveStrategy" in firstRuleKeys
        // 一墨的 rules 使用 snake_case，LinkGo 使用 linkgo_rules，不能误认。
        if (!looksLikeOwnBackup) return null
        if (root["rules"] == null && root["exempts"] == null && root["extracts"] == null) {
            return null
        }
        jsonNormal.decodeFromString<LinkConfigBackup>(json)
            .takeIf { it.version == LinkConfigBackup.CURRENT_VERSION }
    }.getOrNull()

    /**
     * 合并外部导入的配置，自动去重并赋予全新 UUID 与顺延优先级。
     */
    fun merge(
        existingRules: List<LinkDispatchRule>,
        existingExempts: List<LinkExemptDomain>,
        existingExtracts: List<LinkExtractPattern>,
        incoming: LinkConfigBackup
    ): Pair<LinkConfigBackup, LinkImportResult> {
        val knownPatterns = existingRules.map { it.pattern }.toMutableSet()
        val knownDomains = existingExempts.map { it.domain.lowercase() to it.sourcePackage }.toMutableSet()
        val knownExtractIds = existingExtracts.map { it.id }.toMutableSet()
        val knownExtractPatterns = existingExtracts.map { it.pattern }.toMutableSet()

        val nextPriority = (existingRules.maxOfOrNull { it.priority } ?: -1) + 1
        val newRules = incoming.rules.filter { rule ->
            rule.pattern.isNotBlank() && knownPatterns.add(rule.pattern)
        }.mapIndexed { index, rule ->
            rule.copy(id = UUID.randomUUID().toString(), priority = nextPriority + index)
        }
        val newExempts = incoming.exempts.filter { exempt ->
            exempt.domain.isNotBlank() && knownDomains.add(exempt.domain.lowercase() to exempt.sourcePackage)
        }.map { it.copy(id = UUID.randomUUID().toString()) }
        val newExtracts = incoming.extracts.filter { pattern ->
            if (pattern.pattern.isBlank() || (pattern.isBuiltin && pattern.id in knownExtractIds)) return@filter false
            if (!knownExtractPatterns.add(pattern.pattern)) return@filter false
            if (pattern.isBuiltin) knownExtractIds.add(pattern.id)
            true
        }.map {
            if (it.isBuiltin) it else it.copy(id = UUID.randomUUID().toString())
        }

        val merged = LinkConfigBackup(
            rules = existingRules + newRules,
            exempts = existingExempts + newExempts,
            extracts = existingExtracts + newExtracts
        )
        val result = LinkImportResult(
            rulesAdded = newRules.size,
            exemptsAdded = newExempts.size,
            extractsAdded = newExtracts.size
        )
        return merged to result
    }
}
