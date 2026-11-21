package com.slideindex.app.link

import kotlinx.serialization.Serializable
import java.util.UUID

/** 链接匹配规则类型。 */
@Serializable
enum class LinkMatchType {
    CONTAINS,
    REGEX,
    EXACT
}

/** 短链接解析策略。 */
@Serializable
enum class LinkResolveStrategy {
    NONE,
    DIRECT,
    REDISPATCH
}

/**
 * 链接分发规则模型。
 */
@Serializable
data class LinkDispatchRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val pattern: String = "",
    val targetPackage: String = "",
    val matchType: LinkMatchType = LinkMatchType.CONTAINS,
    val template: String? = null,
    val extractPattern: String? = null,
    val isEnabled: Boolean = true,
    val resolveShortLink: Boolean = false,
    val resolveStrategy: LinkResolveStrategy = LinkResolveStrategy.NONE,
    val excludeFromRecents: Boolean = false,
    val priority: Int = 0,
    val iconPath: String? = null,
    val hitCount: Int = 0,
    val lastHitAt: Long = 0L,
    val component: String? = null
)

/**
 * 用于从长文本中抽取目标链接的正则模式。
 */
@Serializable
data class LinkExtractPattern(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val pattern: String = "",
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = false
) {
    companion object {
        const val BUILTIN_HTTP_ID = "builtin_http"
        const val BUILTIN_MAGNET_ID = "builtin_magnet"
        const val BUILTIN_CODE_ID = "builtin_code"

        fun builtinDefaults(): List<LinkExtractPattern> = listOf(
            LinkExtractPattern(
                id = BUILTIN_HTTP_ID,
                name = "标准链接",
                pattern = "(?i)(?:[a-zA-Z0-9+.-]+://)[^\\s\\u4e00-\\u9fa5]+(?<![.,!?])",
                isBuiltin = true
            ),
            LinkExtractPattern(
                id = BUILTIN_MAGNET_ID,
                name = "磁力链接",
                pattern = "(?i)magnet:\\?xt=urn:btih:[a-zA-Z0-9]+[^\\s\\u4e00-\\u9fa5<>\\[\\]{}|^]*",
                isBuiltin = true
            ),
            LinkExtractPattern(
                id = BUILTIN_CODE_ID,
                name = "带提取码链接",
                pattern = "(?i)https?://[^\\s\\u4e00-\\u9fa5<>\\[\\]{}|^]+(?:\\s*(?:提取码|访问码)[：:\\s]\\s*[a-zA-Z0-9]+|\\?pwd=[a-zA-Z0-9]+)",
                isBuiltin = true
            )
        )
    }
}

/**
 * 免分发域名白名单。
 */
@Serializable
data class LinkExemptDomain(
    val id: String = UUID.randomUUID().toString(),
    val domain: String = "",
    val sourcePackage: String = "",
    val isEnabled: Boolean = true
)

/**
 * 链接分发规则导入导出备份。
 */
@Serializable
data class LinkConfigBackup(
    val version: Int = CURRENT_VERSION,
    val rules: List<LinkDispatchRule> = emptyList(),
    val exempts: List<LinkExemptDomain> = emptyList(),
    val extracts: List<LinkExtractPattern> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * 规则导入结果。
 */
data class LinkImportResult(
    val rulesAdded: Int,
    val exemptsAdded: Int,
    val extractsAdded: Int,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null
}
