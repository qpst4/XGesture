package com.slideindex.app.tasks

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.R
import com.slideindex.app.data.AppRepository
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.CloneAppsIndex
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.RecentPackageResolver
import com.slideindex.app.util.TaskActivityLabelResolver
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TaskSwitcherLockStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side task switcher: live system recents via Shizuku, mapped to overlay UI entries.
 */
object TaskSwitcherRepository {

    private const val TAG = "TaskSwitcherRepository"

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 标识符（`包名/Activity` 或裸包名）→ 包名 的解析缓存。
     *
     * 只缓存**成功**的映射：包名不随图标/标题变化，换图标不影响它；应用卸载后对应任务本来
     * 也不会再出现在最近任务里，所以不需要为它做过期策略。
     */
    private val packageCache = ConcurrentHashMap<String, String>(64)

    @Volatile
    private var cachedEntries: List<RecentAppEntry> = emptyList()

    fun getCachedEntries(): List<RecentAppEntry> = cachedEntries

    @Volatile
    private var refreshInFlight = false

    private val pendingCallbacks = mutableListOf<(List<RecentAppEntry>) -> Unit>()

    fun refreshAsync(appRepository: AppRepository, onComplete: (List<RecentAppEntry>) -> Unit) {
        if (!TaskManagerUtil.hasPermission()) {
            onComplete(emptyList())
            return
        }
        TaskManagerUtil.ensureServiceBound()
        synchronized(this) {
            pendingCallbacks += onComplete
            if (refreshInFlight) return
            refreshInFlight = true
        }
        Thread {
            val startedAt = SystemClock.elapsedRealtime()
            val entries = runCatching { loadEntries(appRepository) }
                .getOrElse { error ->
                    Log.e(TAG, "refresh failed", error)
                    emptyList()
                }
            val loadMs = SystemClock.elapsedRealtime() - startedAt
            val callbacks = synchronized(this) {
                refreshInFlight = false
                pendingCallbacks.toList().also { pendingCallbacks.clear() }
            }
            mainHandler.post {
                Log.i(TAG, "refreshAsync delivered ${entries.size} entries in ${loadMs}ms")
                callbacks.forEach { it(entries) }
            }
        }.start()
    }

    fun syncFromSystem(appRepository: AppRepository): List<RecentAppEntry> =
        loadEntries(appRepository)

    fun requestRefreshAfterSwitch(appRepository: AppRepository) {
        mainHandler.postDelayed({ refreshAsync(appRepository) { } }, 400L)
    }

    fun removePackages(packages: Collection<String>) {
        // Session-only optimistic updates are handled in EdgeGestureOverlayView.recentApps.
    }

    fun removeTaskIds(taskIds: Collection<Int>) {
        // Session-only optimistic updates are handled in EdgeGestureOverlayView.recentApps.
    }

    private fun loadEntries(appRepository: AppRepository): List<RecentAppEntry> {
        val startedAt = SystemClock.elapsedRealtime()
        // 数据源不再过滤自身包名：任务切换器需要显示 XGesture 自己；
        // 不需要自身卡片的消费方（如圆环启动器的最近应用）在各自那一层过滤。
        val refsStartedAt = SystemClock.elapsedRealtime()
        val refs = TaskManagerUtil.refreshRecentTasks()
        val refsMs = SystemClock.elapsedRealtime() - refsStartedAt
        val context = TaskManagerUtil.applicationContext()
        val locked = TaskSwitcherLockStore.lockedPackages(context)
        val myUserId = runCatching { android.os.Process.myUserHandle().hashCode() }.getOrDefault(0)
        val seenIds = LinkedHashSet<Int>()
        val seenPackages = LinkedHashSet<String>()
        val entries = mutableListOf<RecentAppEntry>()

        for (ref in refs) {
            val packageName = resolvePackage(appRepository, ref) ?: continue
            if (ref.taskId > 0) {
                if (!seenIds.add(ref.taskId)) continue
            } else if (!seenPackages.add(packageName)) {
                continue
            }

            val appInfo = appRepository.ensureAppInfo(packageName) ?: continue
            // 多开/分身（例如 MIUI 的 user 999）与主实例共用包名与图标：加后缀把两者区分开，
            // 否则任务切换器里会出现两个一模一样、分不清的卡片。
            val isClone = ref.userId >= 0 && ref.userId != myUserId
            val resolvedLabel = resolveLabel(ref, appInfo)
            val label = if (isClone) {
                CloneAppsIndex.labelForUser(context, ref.userId, resolvedLabel)
            } else {
                resolvedLabel
            }
            entries += RecentAppEntry(
                app = appInfo.copy(
                    label = label,
                    letter = PinyinHelper.firstLetter(label)
                ),
                lastUsed = 0L,
                isLocked = packageName in locked,
                taskId = ref.taskId,
                rawIdentifier = ref.identifier,
                topComponent = ref.topComponent.orEmpty(),
                isClone = isClone,
            )
        }

        Log.i(
            TAG,
            "loadEntries ${refs.size} -> ${entries.size} refs=${refsMs}ms " +
                "total=${SystemClock.elapsedRealtime() - startedAt}ms"
        )
        val result = entries.toList()
        cachedEntries = result
        return result
    }

    private fun resolvePackage(
        appRepository: AppRepository,
        ref: TaskManagerUtil.RecentTaskRef
    ): String? {
        if (RecentPackageResolver.isQuickShareIdentifier(ref.identifier) ||
            RecentPackageResolver.isQuickShareIdentifier(ref.topComponent.orEmpty())
        ) {
            return appRepository.resolveInstalledPackage(ref.identifier) ?: "com.google.android.gms"
        }
        val candidates = linkedSetOf(ref.identifier, ref.topComponent.orEmpty())
            .filter { it.isNotBlank() }

        // 常见格式就是 `包名/Activity`：切一刀 + 确认包存在即可，不再「从右往左逐级砍」。
        // 后者每条任务要连问 PackageManager 好几次，而且前面几次必然失败（抛 NameNotFound）。
        for (candidate in candidates) {
            fastPackageOf(candidate, appRepository)?.let { return it }
        }

        // 兜底：类名式标识符（Flyme 那种没有 `/` 的）才走逐级试探。
        for (candidate in candidates) {
            appRepository.resolveInstalledPackage(candidate)?.let { packageName ->
                packageCache[candidate] = packageName
                return packageName
            }
            val normalized = RecentPackageResolver.normalizeIdentifier(candidate)
            if (normalized.contains('.')) {
                appRepository.resolveInstalledPackage(normalized)?.let { packageName ->
                    packageCache[candidate] = packageName
                    return packageName
                }
                if (!normalized.contains('/')) return normalized
            }
        }
        return null
    }

    /** `包名/Activity` → 包名；要求包真的存在，结果按标识符缓存。 */
    private fun fastPackageOf(raw: String, appRepository: AppRepository): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        packageCache[trimmed]?.let { return it }
        val slash = trimmed.indexOf('/')
        if (slash <= 0) return null
        val packageName = trimmed.substring(0, slash).trim()
        if (!packageName.contains('.')) return null
        val known = appRepository.isKnownPackage(packageName) ||
            appRepository.packageExists(packageName)
        if (!known) return null
        packageCache[trimmed] = packageName
        return packageName
    }

    private fun resolveLabel(ref: TaskManagerUtil.RecentTaskRef, appInfo: AppInfo): String {
        val context = TaskManagerUtil.applicationContext()
        TaskActivityLabelResolver.resolveDisplayTitle(
            context,
            ref.topComponent.orEmpty(),
            ref.title
        )?.let { return it }
        TaskActivityLabelResolver.resolveDisplayTitle(context, ref.identifier, null)?.let { return it }
        if (RecentPackageResolver.isSettingsAppInfoIdentifier(ref.identifier)) {
            return context.getString(R.string.task_switcher_app_info)
        }
        if (RecentPackageResolver.isQuickShareIdentifier(ref.identifier)) {
            return context.getString(R.string.task_switcher_quick_share)
        }
        return appInfo.label
    }
}
