package com.slideindex.app.settings

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SearchEngineType {
    DIRECT_LINK,
    SHARE_TO_APP,
    SHARE_IMAGE_TO_APP,
    SHARE_FILE_TO_APP,
    JUMP_TO_ACTIVITY,
    EXTERN_JUMP_LINK,
    SEARCH_GROUP,
}

/** 搜索组的成员类型：文字引擎或图片分享目标。 */
@Serializable
enum class SearchGroupKind {
    TEXT,
    IMAGE_SHARE,
}

/** 搜索组间隔的硬约束；UI 与校验共用。 */
object SearchGroupDefaults {
    const val DEFAULT_INTERVAL_MILLIS = 800
    const val MIN_INTERVAL_MILLIS = 200
    const val MAX_INTERVAL_MILLIS = 2000

    /** 滑条显示步进；存储值仍按毫秒整数保存。 */
    const val INTERVAL_STEP_MILLIS = 50
}

@Serializable
enum class SearchIconType {
    URI,
    TEXT,
    OTHER,
}

@Serializable
data class SearchEngineConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val engineType: SearchEngineType = SearchEngineType.DIRECT_LINK,
    val iconType: SearchIconType = SearchIconType.OTHER,
    /** Relative path under app files dir, e.g. search_icons/saveIcon-xxx */
    val iconPath: String? = null,
    /**
     * 「用应用图标」时跟随的应用包名。
     *
     * 非空时优先实时取该应用的当前图标（会走图标库的全局覆盖），不再依赖 [iconPath] 的快照；
     * 只有在取不到（应用被卸载、换设备没装）时才回退到 [iconPath]。
     */
    val iconSourcePackage: String? = null,
    val textIcon: String? = null,
    /** Prefix shortcut for search panel, e.g. "g" → Google; triggers after a trailing space. */
    val aliasCode: String? = null,
    val searchLink: String? = null,
    val externJumpLink: String? = null,
    val externJumpPackage: String? = null,
    val targetPackage: String? = null,
    val targetActivity: String? = null,
    val autoInputEnter: Boolean = true,
    val showInPickPanel: Boolean = true,
    val sortOrder: Int = 0,
    /** 仅 [SearchEngineType.SEARCH_GROUP] 使用。 */
    val groupKind: SearchGroupKind = SearchGroupKind.TEXT,
    val groupMemberIds: List<String> = emptyList(),
    val groupIntervalMillis: Int = SearchGroupDefaults.DEFAULT_INTERVAL_MILLIS,
) {
    fun isTextSearchEngine(): Boolean = when (engineType) {
        SearchEngineType.DIRECT_LINK,
        SearchEngineType.JUMP_TO_ACTIVITY,
        SearchEngineType.EXTERN_JUMP_LINK,
        SearchEngineType.SHARE_TO_APP,
        -> showInPickPanel
        SearchEngineType.SEARCH_GROUP -> groupKind == SearchGroupKind.TEXT && showInPickPanel
        SearchEngineType.SHARE_IMAGE_TO_APP,
        SearchEngineType.SHARE_FILE_TO_APP,
        -> false
    }

    fun isImageShareGroup(): Boolean =
        engineType == SearchEngineType.SEARCH_GROUP && groupKind == SearchGroupKind.IMAGE_SHARE
}
