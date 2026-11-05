package com.slideindex.app.ui.picker

import android.content.Context
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.shortcut.port.RootShell
import com.slideindex.app.shortcut.port.ShizukuShell
import com.slideindex.app.shortcut.port.ShortCutManager
import com.slideindex.app.shortcut.port.ShortcutRootBatchReader
import com.slideindex.app.shortcut.port.toIntentUri
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ActionPickerShortcutMode {
    ROOT,
    SHIZUKU,
}

data class ActionPickerShortcutServiceResult(
    val catalog: AppShortcutLoader.ShortcutCatalog,
    val source: String,
    val total: Int,
    val error: String? = null,
)

private data class ShortcutCatalogCacheEntry(
    val appsKey: Int,
    val mode: ActionPickerShortcutMode,
    val loadedAt: Long,
    val result: ActionPickerShortcutServiceResult,
)

private const val CATALOG_CACHE_TTL_MS = 5 * 60 * 1000L
private var catalogCache: ShortcutCatalogCacheEntry? = null

/**
 * Shared ShortcutService catalog loader.
 *
 * Root: C batch reader (one root command, no abx2xml).
 * Shizuku: dumpsys; ShortCutManager keeps the real shortcutId.
 * Fallback: manifest static catalog, so pages still work without privilege.
 */
suspend fun loadActionPickerShortcutService(
    context: Context,
    apps: List<AppInfo>,
    mode: ActionPickerShortcutMode,
    forceRefresh: Boolean = false,
): ActionPickerShortcutServiceResult {
    val appsKey = apps.map { it.packageName }.sorted().hashCode()
    val now = System.currentTimeMillis()
    catalogCache?.takeIf {
        !forceRefresh &&
            it.appsKey == appsKey &&
            it.mode == mode &&
            now - it.loadedAt < CATALOG_CACHE_TTL_MS
    }?.let { return it.result }

    val result = withContext(Dispatchers.IO) {
        val manager = ShortCutManager(context)
        var error: String? = null

        when (mode) {
            ActionPickerShortcutMode.ROOT -> {
                val rootShell = RootShell()
                try {
                    if (!rootShell.openShell()) {
                        error = "Root 不可用"
                    } else {
                        val documents = ShortcutRootBatchReader.readAll(rootShell)
                        if (documents.isEmpty()) {
                            error = "Root 未读到快捷方式"
                        } else {
                            manager.fromPackageBytes(documents) { }
                        }
                    }
                } catch (throwable: Throwable) {
                    error = throwable.message ?: "Root 读取失败"
                } finally {
                    rootShell.close()
                }
            }

            ActionPickerShortcutMode.SHIZUKU -> {
                val shizukuShell = ShizukuShell()
                try {
                    when {
                        !shizukuShell.isSupported() -> error = "Shizuku 不可用"
                        shizukuShell.isPermissionDenied() -> error = "Shizuku 未授权"
                        else -> {
                            val dump = shizukuShell.runAndGetOutput("dumpsys shortcut")
                            if (dump.isBlank()) {
                                error = "Shizuku 未读到快捷方式"
                            } else {
                                manager.fromShellData(dump) { }
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    error = throwable.message ?: "Shizuku 读取失败"
                }
            }
        }

        val appsByPackage = apps.associateBy { it.packageName }
        val grouped = linkedMapOf<String, LinkedHashMap<String, TaskSwitcherMenuItem>>()
        manager.fullData().forEach { item ->
            if (item.shortcut.isBlank()) return@forEach
            if (appsByPackage[item.pkg] == null) return@forEach
            val bucket = grouped.getOrPut(item.pkg) { linkedMapOf() }
            if (bucket.containsKey(item.shortcut)) return@forEach
            val kinds = manager.kindsOf(item)
            val kind = when {
                ShortcutKind.PINNED in kinds -> ShortcutKind.PINNED
                ShortcutKind.DYNAMIC in kinds -> ShortcutKind.DYNAMIC
                else -> ShortcutKind.STATIC
            }
            bucket[item.shortcut] = TaskSwitcherMenuItem(
                label = item.name.ifBlank { item.title.ifBlank { item.pkg } },
                type = TaskSwitcherMenuItemType.SHORTCUT,
                shortcutId = item.shortcut,
                shortcutIntent = manager.intentOf(item),
                intentUris = listOfNotNull(item.toIntentUri()),
                kind = kind,
                isServiceShortcut = true,
            )
        }

        val groups = grouped.mapNotNull { (packageName, shortcuts) ->
            val app = appsByPackage[packageName] ?: return@mapNotNull null
            val items = shortcuts.values.sortedBy { PinyinHelper.sortKey(it.label) }
            if (items.isEmpty()) null else AppShortcutLoader.AppShortcutGroup(app, items)
        }.sortedBy { PinyinHelper.sortKey(it.app.label) }

        if (groups.isEmpty()) {
            val fallback = runCatching {
                AppShortcutLoader.loadShortcutCatalog(
                    context = context,
                    apps = apps,
                    forceRefresh = true,
                )
            }.getOrNull()
            if (fallback != null) {
                return@withContext ActionPickerShortcutServiceResult(
                    catalog = fallback,
                    source = "静态",
                    total = fallback.groups.sumOf { it.shortcuts.size },
                    error = error,
                )
            }
        }

        val createHosts = AppShortcutLoader.queryCreateShortcutActivities(context)
        val catalog = AppShortcutLoader.ShortcutCatalog(
            createHosts = createHosts,
            staticGroups = groups,
        )
        ActionPickerShortcutServiceResult(
            catalog = catalog,
            source = if (mode == ActionPickerShortcutMode.ROOT) "Root" else "Shizuku",
            total = groups.sumOf { it.shortcuts.size },
            error = error,
        )
    }

    catalogCache = ShortcutCatalogCacheEntry(
        appsKey = appsKey,
        mode = mode,
        loadedAt = now,
        result = result,
    )
    return result
}
