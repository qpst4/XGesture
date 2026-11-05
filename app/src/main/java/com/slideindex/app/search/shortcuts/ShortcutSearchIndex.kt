package com.slideindex.app.search.shortcuts

import android.content.Context
import android.util.Log
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.ui.picker.ActionPickerShortcutMode
import com.slideindex.app.ui.picker.loadActionPickerShortcutService
import com.slideindex.app.util.ShortcutDisplayRules
import com.slideindex.app.util.ShortcutKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 搜索面板「应用快捷方式」索引。
 *
 * 与选择器共用 [loadActionPickerShortcutService]：Root/Shizuku 时包含
 * dynamic/pinned/static，无权限时回退 manifest 静态目录。
 */
object ShortcutSearchIndex {
    private const val TAG = "ShortcutSearchIndex"

    private val mutex = Mutex()

    @Volatile
    private var cached: List<ShortcutSearchEntry>? = null

    @Volatile
    private var cachedAppsKey: Int = 0

    fun invalidate() {
        cached = null
    }

    suspend fun ensureLoaded(
        context: Context,
        apps: List<AppInfo>,
    ): List<ShortcutSearchEntry> {
        val appsKey = appsKey(apps)
        cached?.takeIf { cachedAppsKey == appsKey }?.let { return it }
        return mutex.withLock {
            cached?.takeIf { cachedAppsKey == appsKey } ?: withContext(Dispatchers.IO) {
                load(context.applicationContext, apps)
            }.also {
                cached = it
                cachedAppsKey = appsKey
            }
        }
    }

    suspend fun search(
        context: Context,
        apps: List<AppInfo>,
        query: String,
        limit: Int,
    ): List<ShortcutSearchEntry> =
        ShortcutSearchMatcher.search(ensureLoaded(context, apps), query, limit)

    private fun appsKey(apps: List<AppInfo>): Int = apps.map { it.packageName }.sorted().hashCode()

    private suspend fun load(context: Context, apps: List<AppInfo>): List<ShortcutSearchEntry> {
        val mode = if (PrivilegeGateway.isRootMode()) {
            ActionPickerShortcutMode.ROOT
        } else {
            ActionPickerShortcutMode.SHIZUKU
        }
        val catalog = runCatching {
            loadActionPickerShortcutService(context, apps, mode).catalog
        }.onFailure { error ->
            Log.w(TAG, "loadShortcutCatalog failed: ${error.message}")
        }.getOrNull() ?: return emptyList()

        val entries = mutableListOf<ShortcutSearchEntry>()
        catalog.groups.forEach { group ->
            group.shortcuts.forEach { item ->
                if (item.type != TaskSwitcherMenuItemType.SHORTCUT) return@forEach
                val label = item.label.trim()
                if (label.isEmpty()) return@forEach
                val shortcutId = item.shortcutId?.trim().orEmpty().ifEmpty { label }
                if (!ShortcutDisplayRules.isDisplayable(shortcutId, label)) return@forEach
                entries += ShortcutSearchEntry(
                    packageName = group.app.packageName,
                    appLabel = group.app.label,
                    label = label,
                    shortcutId = shortcutId,
                    intentUris = item.intentUris.orEmpty(),
                    kind = item.kind ?: ShortcutKind.STATIC,
                )
            }
        }
        return entries.distinctBy { it.dedupeKey }
    }
}
