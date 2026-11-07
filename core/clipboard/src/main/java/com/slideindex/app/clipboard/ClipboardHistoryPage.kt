package com.slideindex.app.clipboard

data class ClipboardHistoryPage(
    val entries: List<ClipboardEntry>,
    val totalCount: Int,
    val hasMore: Boolean,
    /** Keyset cursor: pass as [createdBeforeMs] for the next page (oldest item on this page). */
    val nextCursor: Long? = entries.lastOrNull()?.createdAtEpochMs
)
