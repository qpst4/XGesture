package com.slideindex.app.clipboard

import kotlinx.serialization.Serializable

/** 一条剪贴板历史。 */
@Serializable
data class ClipboardEntry(
    val id: String,
    val type: ClipboardEntryType = ClipboardEntryType.TEXT,
    val text: String,
    val uri: String? = null,
    val intentUri: String? = null,
    val htmlText: String? = null,
    val mimeType: String? = null,
    val imageFileName: String? = null,
    val imageFileNames: List<String> = emptyList(),
    val contentBlocks: List<ClipboardContentBlock> = emptyList(),
    val createdAtEpochMs: Long,
) {
    fun contentKey(): String = ClipboardContentKey.forEntry(this)

    fun matchesQuery(query: String): Boolean {
        val lower = query.lowercase()
        return text.contains(lower, ignoreCase = true) ||
            uri?.contains(lower, ignoreCase = true) == true ||
            intentUri?.contains(lower, ignoreCase = true) == true
    }
}
