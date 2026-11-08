package com.slideindex.app.overlay.searchpanel

import android.util.LruCache
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.data.AppInfo
import com.slideindex.app.search.contacts.ContactSearchEntry
import com.slideindex.app.search.files.DeviceFileEntry
import com.slideindex.app.search.shortcuts.ShortcutSearchEntry
import com.slideindex.app.search.settings.SystemSettingsSearchEntry

data class SearchPanelCandidateCacheEntry(
    val settings: List<SystemSettingsSearchEntry>,
    val contacts: List<ContactSearchEntry>,
    val files: List<DeviceFileEntry>,
    val apps: List<AppInfo>,
    val shortcuts: List<ShortcutSearchEntry> = emptyList(),
    val clipboard: List<ClipboardEntry> = emptyList(),
)

object SearchPanelCandidateCache {
    private const val MAX_ENTRIES = 48

    private val cache = LruCache<String, SearchPanelCandidateCacheEntry>(MAX_ENTRIES)

    fun get(key: String): SearchPanelCandidateCacheEntry? = cache.get(key)

    fun put(key: String, entry: SearchPanelCandidateCacheEntry) {
        cache.put(key, entry)
    }

    fun clear() {
        cache.evictAll()
    }
}
