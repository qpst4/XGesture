package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.service.LaunchTrampolineActivity
import java.util.concurrent.ConcurrentHashMap

object AppShortcutLoader {
    private const val TAG = "AppShortcutLoader"
    private const val MAX_MENU_SHORTCUTS = 24
    private const val CATALOG_CACHE_TTL_MS = 5 * 60 * 1000L

    private val resolvedShortcutCache = ConcurrentHashMap<String, TaskSwitcherMenuItem>()

    data class ShortcutCatalog(
        val createHosts: List<CreateShortcutHost>,
        val staticGroups: List<AppShortcutGroup> = emptyList(),
        val dynamicGroups: List<AppShortcutGroup> = emptyList(),
        val pinnedGroups: List<AppShortcutGroup> = emptyList(),
    ) {
        val groups: List<AppShortcutGroup> =
            mergeShortcutGroups(staticGroups, dynamicGroups, pinnedGroups)
    }

    @Volatile
    private var catalogCache: ShortcutCatalog? = null

    @Volatile
    private var catalogCacheKey: Int = 0

    @Volatile
    private var catalogLoadedAt: Long = 0L

    fun invalidateShortcutCatalog() {
        catalogLoadedAt = 0L
        catalogCache = null
    }

    fun loadShortcutCatalog(
        context: Context,
        apps: List<AppInfo>,
        forceRefresh: Boolean = false,
        includeShell: Boolean = true,
        onProgress: ((ShortcutScanProgress) -> Unit)? = null,
    ): ShortcutCatalog {
        val now = System.currentTimeMillis()
        val cacheKey = apps.map { it.packageName }.sorted().hashCode()
        val cached = catalogCache
        if (!forceRefresh &&
            cached != null &&
            catalogCacheKey == cacheKey &&
            now - catalogLoadedAt < CATALOG_CACHE_TTL_MS
        ) {
            onProgress?.invoke(ShortcutScanProgress(ShortcutScanPhase.FINALIZING, 1, 1))
            return cached
        }

        onProgress?.invoke(ShortcutScanProgress(ShortcutScanPhase.APPS, 0, 1))
        val launchGroups = loadLaunchShortcutGroups(context, apps)
        onProgress?.invoke(ShortcutScanProgress(ShortcutScanPhase.FINALIZING, 0, 0))
        val createHosts = queryCreateShortcutActivities(context)
        val catalog = ShortcutCatalog(
            createHosts = createHosts,
            staticGroups = launchGroups,
            dynamicGroups = emptyList(),
            pinnedGroups = emptyList(),
        )
        catalogCache = catalog
        catalogCacheKey = cacheKey
        catalogLoadedAt = now
        onProgress?.invoke(ShortcutScanProgress(ShortcutScanPhase.FINALIZING, 1, 1))
        Log.i(
            TAG,
            "loadShortcutCatalog createHosts=${createHosts.size} launchShortcuts=${launchGroups.sumOf { it.shortcuts.size }}",
        )
        return catalog
    }

    private fun loadLaunchShortcutGroups(
        context: Context,
        apps: List<AppInfo>,
    ): List<AppShortcutGroup> {
        val appsByPackage = apps.associateBy { it.packageName }.toMutableMap()
        val grouped = linkedMapOf<String, LinkedHashMap<String, TaskSwitcherMenuItem>>()

        ShortcutUtils.getAllAppsWithShortcut(context).forEach { launcherInfo ->
            val packageName = launcherInfo.packageName
            if (packageName.isBlank()) return@forEach
            val bucket = grouped.getOrPut(packageName) { linkedMapOf() }
            launcherInfo.shortcuts.forEach { entry ->
                val item = entry.toMenuItem()
                bucket.putIfAbsent(item.shortcutId ?: item.label, item)
                resolvedShortcutCache[shortcutCacheKey(packageName, item.shortcutId ?: item.label)] = item
            }
            if (packageName !in appsByPackage) {
                resolveAppInfo(context, packageName)?.let { appsByPackage[packageName] = it }
            }
        }

        return grouped.mapNotNull { (packageName, shortcuts) ->
            val app = appsByPackage[packageName] ?: return@mapNotNull null
            val items = shortcuts.values
                .filter { isLaunchable(context, it) }
                .sortedBy { PinyinHelper.sortKey(it.label) }
            if (items.isEmpty()) null else AppShortcutGroup(app, items)
        }.sortedBy { PinyinHelper.sortKey(it.app.label) }
    }

    data class AppShortcutGroup(
        val app: AppInfo,
        val shortcuts: List<TaskSwitcherMenuItem>,
    )

    fun loadRegisteredShortcutGroups(
        context: Context,
        apps: List<AppInfo>,
        includeShell: Boolean = true,
    ): List<AppShortcutGroup> = loadLaunchShortcutGroups(context, apps)

    fun loadCategorizedShortcutGroups(
        context: Context,
        apps: List<AppInfo>,
        includeShell: Boolean = true,
        onProgress: ((ShortcutScanProgress) -> Unit)? = null,
    ): Map<ShortcutKind, List<AppShortcutGroup>> {
        onProgress?.invoke(ShortcutScanProgress(ShortcutScanPhase.APPS, 1, 1))
        val groups = loadLaunchShortcutGroups(context, apps)
        return ShortcutKind.entries.associateWith { kind ->
            when (kind) {
                ShortcutKind.STATIC -> groups
                ShortcutKind.DYNAMIC, ShortcutKind.PINNED -> emptyList()
            }
        }
    }

    fun resolveShortcutForLaunch(
        context: Context,
        packageName: String,
        shortcutId: String,
        label: String,
    ): TaskSwitcherMenuItem {
        // C dynamic/pinned entries store the real ShortcutService id; refresh it now.
        ShortcutServiceResolver.resolve(context, packageName, shortcutId)?.let { return it }
        lookupRegisteredShortcut(context, packageName, shortcutId)?.let { return it }
        return TaskSwitcherMenuItem(
            label = label,
            type = TaskSwitcherMenuItemType.SHORTCUT,
            shortcutId = shortcutId,
        )
    }

    fun TaskSwitcherMenuItem.toQuickLauncherItem(packageName: String): QuickLauncherItem {
        val serviceId = shortcutId?.takeIf { isServiceShortcut && it.isNotBlank() }
        if (serviceId != null) {
            return QuickLauncherItem.dynamicShortcut(packageName, serviceId, label)
        }
        val uris = intentUris
        if (!uris.isNullOrEmpty()) {
            val enrichedUris = uris.map { enrichIntentUriWithPackage(it, packageName) }
            return if (enrichedUris.size == 1) {
                QuickLauncherItem.intentShortcut(enrichedUris[0], label, packageName)
            } else {
                QuickLauncherItem.intentShortcuts(enrichedUris, label, packageName)
            }
        }
        val id = shortcutId?.takeIf { it.isNotBlank() } ?: label
        return QuickLauncherItem.dynamicShortcut(packageName, id, label)
    }

    private fun enrichIntentUriWithPackage(intentUri: String, packageName: String): String {
        return runCatching {
            val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
            if (intent.`package`.isNullOrBlank() && intent.component == null) {
                intent.setPackage(packageName)
            }
            intent.toUri(Intent.URI_INTENT_SCHEME)
        }.getOrDefault(intentUri)
    }

    fun cacheShortcutForLaunch(packageName: String, item: TaskSwitcherMenuItem) {
        if (item.isServiceShortcut) return
        val shortcutId = item.shortcutId?.takeIf { it.isNotBlank() } ?: return
        resolvedShortcutCache[shortcutCacheKey(packageName, shortcutId)] = item
    }

    fun peekResolvedShortcut(packageName: String, shortcutId: String): TaskSwitcherMenuItem? =
        resolvedShortcutCache[shortcutCacheKey(packageName, shortcutId)]

    fun loadRegisteredShortcuts(context: Context, packageName: String): List<TaskSwitcherMenuItem> =
        loadMenuShortcuts(context, packageName)

    fun warmQuickLauncherShortcuts(context: Context, items: List<QuickLauncherItem>) {
        items.filter { it.type == QuickLauncherItemType.SHORTCUT }.forEach { item ->
            QuickLauncherItemCodec.parseIntentPayload(item.payload)?.let { intentUri ->
                resolvedShortcutCache[shortcutCacheKey("intent", intentUri)] = TaskSwitcherMenuItem(
                    label = item.label,
                    type = TaskSwitcherMenuItemType.SHORTCUT,
                    intentUris = listOf(intentUri),
                    shortcutIntent = runCatching {
                        Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }.getOrNull(),
                )
                return@forEach
            }
            QuickLauncherItemCodec.parseIntentListPayload(item.payload)?.let { intentUris ->
                resolvedShortcutCache[shortcutCacheKey("intent", intentUris.joinToString())] =
                    TaskSwitcherMenuItem(
                        label = item.label,
                        type = TaskSwitcherMenuItemType.SHORTCUT,
                        intentUris = intentUris,
                    )
                return@forEach
            }
            val dynamic = QuickLauncherItemCodec.parseShortcutPayload(item.payload) ?: return@forEach
            lookupRegisteredShortcut(context, dynamic.first, dynamic.second)
        }
    }

    fun lookupRegisteredShortcut(
        context: Context,
        packageName: String,
        shortcutId: String,
    ): TaskSwitcherMenuItem? {
        val key = shortcutCacheKey(packageName, shortcutId)
        resolvedShortcutCache[key]?.let { return it }
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        ShortcutUtils.shortcutsForPackage(context, packageName)
            .map { it.toMenuItem() }
            .firstOrNull { it.shortcutId == shortcutId }
            ?.let { item ->
                resolvedShortcutCache[key] = item
                return item
            }
        return null
    }

    fun resolveRegisteredShortcut(
        context: Context,
        packageName: String,
        shortcutId: String,
    ): TaskSwitcherMenuItem? =
        lookupRegisteredShortcut(context, packageName, shortcutId)
            ?: loadRegisteredShortcuts(context, packageName).firstOrNull { it.shortcutId == shortcutId }

    data class CreateShortcutHost(
        val packageName: String,
        val className: String,
        val label: String,
        val pinyinKey: String = PinyinHelper.sortKey(label),
    ) {
        val qualifiedName: String get() = "$packageName/$className"

        fun createIntent(): Intent = Intent(Intent.ACTION_CREATE_SHORTCUT).setClassName(packageName, className)
    }

    data class CreatedShortcut(
        val hostPackageName: String,
        val label: String,
        val componentFlat: String? = null,
        val intentUri: String? = null,
        val shortcutIntent: Intent? = null,
    )

    fun queryCreateShortcutActivities(context: Context): List<CreateShortcutHost> {
        return ShortcutUtils.queryCreateShortcutActivities(context)
            .map { info ->
                CreateShortcutHost(
                    packageName = info.packageName,
                    className = info.className,
                    label = info.label,
                )
            }
            // 少数应用的同一个 Activity 声明了多条 CREATE_SHORTCUT filter，会被
            // queryIntentActivities 重复返回；picker 用 qualifiedName（包名/类名）做 key，
            // 重复会直接抛「Key ... was already used」把面板打崩。
            .distinctBy { it.qualifiedName }
            .sortedBy { it.pinyinKey }
    }

    fun parseCreateShortcutResult(
        hostPackageName: String,
        data: Intent?,
    ): CreatedShortcut? {
        if (data == null) return null
        @Suppress("DEPRECATION")
        val label = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)?.trim().orEmpty()
        @Suppress("DEPRECATION")
        val shortcutIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        } else {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
        } ?: return null
        val launchIntent = shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val component = launchIntent.component
        val componentFlat = component?.flattenToString()
            ?: launchIntent.`package`?.let { pkg ->
                launchIntent.component?.className?.let { cls ->
                    ComponentName(pkg, cls).flattenToString()
                }
            }
        val intentUri = runCatching {
            launchIntent.toUri(Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return null
        val finalLabel = label.ifBlank {
            componentFlat?.substringAfterLast('/') ?: hostPackageName
        }
        return CreatedShortcut(
            hostPackageName = hostPackageName,
            label = finalLabel,
            componentFlat = componentFlat,
            intentUri = intentUri,
            shortcutIntent = launchIntent,
        )
    }

    fun loadInstantShortcuts(context: Context, packageName: String): List<TaskSwitcherMenuItem> =
        KnownAppShortcuts.load(context, packageName)

    fun loadFastShortcuts(context: Context, packageName: String): List<TaskSwitcherMenuItem> {
        val manifest = ShortcutUtils.shortcutsForPackage(context, packageName)
            .map { it.toMenuItem() }
            .filter { isLaunchable(context, it) }
        if (manifest.isNotEmpty()) return limitShortcuts(manifest, MAX_MENU_SHORTCUTS)
        return limitShortcuts(KnownAppShortcuts.load(context, packageName), MAX_MENU_SHORTCUTS)
    }

    fun loadMenuShortcuts(context: Context, packageName: String): List<TaskSwitcherMenuItem> {
        val merged = linkedMapOf<String, TaskSwitcherMenuItem>()
        ShortcutUtils.shortcutsForPackage(context, packageName)
            .map { it.toMenuItem() }
            .filter { isLaunchable(context, it) }
            .forEach { item ->
                merged[item.shortcutId ?: item.label] = item
            }
        KnownAppShortcuts.load(context, packageName).forEach { item ->
            merged.putIfAbsent(item.shortcutId ?: item.label, item)
        }
        return limitShortcuts(merged.values.toList(), MAX_MENU_SHORTCUTS)
    }

    fun finalizeShortcuts(
        shortcuts: List<TaskSwitcherMenuItem>,
        packageName: String,
    ): List<TaskSwitcherMenuItem> = limitShortcuts(shortcuts.filter { isDisplayableShortcut(it) }, MAX_MENU_SHORTCUTS)

    fun isDisplayableShortcut(item: TaskSwitcherMenuItem): Boolean {
        if (item.type != TaskSwitcherMenuItemType.SHORTCUT) return true
        return ShortcutDisplayRules.isDisplayable(item.shortcutId, item.label)
    }

    fun launchShortcut(context: Context, packageName: String, item: TaskSwitcherMenuItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                launchShortcut(context, packageName, item)
            }
            return
        }
        if (item.isServiceShortcut) {
            item.shortcutIntent?.let { intent ->
                launchServiceShortcut(context, packageName, intent)
                return
            }
        }
        val intentUris = item.intentUris
        if (!intentUris.isNullOrEmpty()) {
            launchShortcutIntents(context, intentUris)
            return
        }
        item.shortcutIntent?.let { intent ->
            runCatching {
                context.startActivity(LaunchTrampolineActivity.createIntent(context, intent))
            }.onFailure { error ->
                Log.e(TAG, "launchShortcut($packageName) trampoline failed", error)
                runCatching {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { fallbackError ->
                    Log.e(TAG, "launchShortcut($packageName) direct failed", fallbackError)
                    Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 服务快捷方式优先保留完整 Intent；未导出组件必须走统一启动出口，
     * 不能再交给普通 startActivity / am start。
     */
    private fun launchServiceShortcut(
        context: Context,
        packageName: String,
        intent: Intent,
    ) {
        ShortcutLaunchDispatcher.launch(
            context = context,
            packageName = packageName,
            intent = intent,
            exportedLaunch = { target ->
                context.startActivity(LaunchTrampolineActivity.createIntent(context, target))
                true
            },
            onFailure = { failure ->
                ShortcutLaunchDispatcher.showFailure(context, failure)
            },
        )
    }

    fun CreatedShortcut.toQuickLauncherItem(): QuickLauncherItem {
        intentUri?.let {
            return QuickLauncherItem.intentShortcut(it, label, hostPackageName)
        }
        componentFlat?.let { return QuickLauncherItem.shortcut(it, label) }
        error("CreatedShortcut has no launch target")
    }

    private fun launchShortcutIntents(context: Context, intentUris: List<String>) {
        try {
            val intents = intentUris.map { uri ->
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }.toTypedArray()
            if (intents.size == 1) {
                context.startActivity(
                    LaunchTrampolineActivity.createIntent(context, intents[0]),
                )
            } else {
                context.startActivities(intents)
            }
        } catch (error: Exception) {
            Log.e(TAG, "launchShortcutIntents failed", error)
        }
    }

    private fun LauncherShortcutInfo.Entry.toMenuItem(): TaskSwitcherMenuItem {
        val parsedIntents = intents.mapNotNull { uri ->
            runCatching {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.getOrNull()
        }
        return TaskSwitcherMenuItem(
            label = label,
            type = TaskSwitcherMenuItemType.SHORTCUT,
            shortcutId = qualifiedNameWithIntents,
            shortcutIntent = parsedIntents.firstOrNull(),
            intentUris = intents,
            kind = ShortcutKind.STATIC,
            targetComponent = className,
        )
    }

    private fun isLaunchable(context: Context, item: TaskSwitcherMenuItem): Boolean {
        if (item.type != TaskSwitcherMenuItemType.SHORTCUT) return true
        item.intentUris?.firstOrNull()?.let { uri ->
            val intent = runCatching {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            }.getOrNull() ?: return false
            return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
        item.shortcutIntent?.let { intent ->
            return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
        return !item.shortcutId.isNullOrBlank()
    }

    private fun mergeShortcutGroups(vararg groupLists: List<AppShortcutGroup>): List<AppShortcutGroup> {
        val merged = linkedMapOf<String, LinkedHashMap<String, TaskSwitcherMenuItem>>()
        groupLists.forEach { groups ->
            groups.forEach { group ->
                val bucket = merged.getOrPut(group.app.packageName) { linkedMapOf() }
                group.shortcuts.forEach { item ->
                    bucket.putIfAbsent(item.shortcutId ?: item.label, item)
                }
            }
        }
        return merged.mapNotNull { (packageName, shortcuts) ->
            val firstGroup = groupLists.flatMap { it }.firstOrNull { it.app.packageName == packageName }
            val app = firstGroup?.app ?: return@mapNotNull null
            AppShortcutGroup(app, shortcuts.values.toList())
        }.sortedBy { PinyinHelper.sortKey(it.app.label) }
    }

    private fun resolveAppInfo(context: Context, packageName: String): AppInfo? {
        if (packageName.isBlank() || packageName == context.packageName) return null
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            AppInfo(
                packageName = packageName,
                label = label,
                letter = PinyinHelper.firstLetter(label),
                pinyinKey = PinyinHelper.sortKey(label),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun limitShortcuts(
        shortcuts: List<TaskSwitcherMenuItem>,
        max: Int,
    ): List<TaskSwitcherMenuItem> {
        if (max <= 0 || shortcuts.size <= max) return shortcuts
        return shortcuts.take(max)
    }

    private fun shortcutCacheKey(packageName: String, shortcutId: String): String =
        "$packageName\u0000$shortcutId"
}
