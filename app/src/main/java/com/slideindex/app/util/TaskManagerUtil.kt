package com.slideindex.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.privilege.RootPrivilegedOperations
import com.slideindex.app.privilege.root.RootTaskManagerServiceHost
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.shell.ShellExecutionMode
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import rikka.shizuku.Shizuku

object TaskManagerUtil {

    private const val TAG = "TaskManagerUtil"
    private const val MIN_SWITCH_TO_TASK_API = 12
    private const val MIN_REMOVE_TASK_API = 1
    private const val MIN_TASK_IDS_API = 3
    private const val MIN_FORCE_STOP_API = 8
    /** 最近任务行带 userId（第 5 列）的 UserService 构建号；分身识别依赖它。 */
    private const val MIN_RECENTS_USER_API = 37
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val MIN_SHORTCUTS_API = 9
    const val ROOT_PROBE_BINDER_TIMEOUT_MS = 45_000L
    const val REQUEST_CODE = 1001

    private val SHIZUKU_PACKAGES = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager",
    )

    data class RecentTaskRef(
        val taskId: Int,
        val identifier: String,
        val title: String? = null,
        val topComponent: String? = null,
        /** 任务所属用户；-1 = 未知。多开/分身任务用它区分同包的多个实例。 */
        val userId: Int = -1,
    )

    data class ShellCommandResult(
        val exitCode: Int,
        val output: String,
    ) {
        val success: Boolean get() = exitCode == 0
    }

    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun applicationContext(): Context = appContext()

    @Volatile
    private var warmUpInFlight = false

    @Volatile
    private var privilegedWarmUpInFlight = false

    @Volatile
    private var cachedDirectRootAccess: Boolean? = null

    @Volatile
    private var cachedDirectRootAccessAtMs = 0L

    private val taskWorkerLock = Any()

    private val privilegedOpsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PrivilegedTaskOps").apply { isDaemon = true }
    }

    private const val PRIVILEGED_OPS_TIMEOUT_MS = 8_000L

    private const val MAIN_THREAD_SKIP_LOG_INTERVAL_MS = 5_000L

    @Volatile
    private var lastMainThreadSkipLogAtMs = 0L

    private val onPrivilegedOpsThread = ThreadLocal.withInitial { false }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> privilegedMainThreadFallback(): T = when (T::class) {
        Boolean::class -> false as T
        ShellCommandResult::class -> ShellCommandResult(
            -1,
            appContext().getString(R.string.privileged_task_skipped_main_thread),
        ) as T
        Int::class -> 0 as T
        String::class -> "" as T
        Unit::class -> Unit as T
        else -> when {
            Map::class.java.isAssignableFrom(T::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                emptyMap<Any, Any>() as T
            }
            else -> throw IllegalStateException(
                "Privileged task cannot run on main thread for ${T::class.java.simpleName}",
            )
        }
    }

    private inline fun <reified T> runPrivilegedOpsBlocking(noinline block: () -> T): T {
        if (onPrivilegedOpsThread.get() == true) {
            return block()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 主线程不可能拿到结果：命令丢给特权线程后这里立刻返回「失败」。调用方若把返回值当成
            // 真实执行结果，就会误判（写 secure 设置曾因此每拖动一次滑条弹一次 ADB 授权框）。
            val now = SystemClock.elapsedRealtime()
            if (now - lastMainThreadSkipLogAtMs >= MAIN_THREAD_SKIP_LOG_INTERVAL_MS) {
                lastMainThreadSkipLogAtMs = now
                Log.w(
                    TAG,
                    "privileged op called on main thread: dispatched to PrivilegedTaskOps, " +
                        "returning failure to caller",
                )
            }
            privilegedOpsExecutor.execute {
                onPrivilegedOpsThread.set(true)
                try {
                    runCatching { block() }
                } finally {
                    onPrivilegedOpsThread.set(false)
                }
            }
            return privilegedMainThreadFallback()
        }
        return try {
            privilegedOpsExecutor.submit(
                Callable {
                    onPrivilegedOpsThread.set(true)
                    try {
                        block()
                    } finally {
                        onPrivilegedOpsThread.set(false)
                    }
                },
            ).get(PRIVILEGED_OPS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            Log.e(TAG, "privileged task timed out", error)
            privilegedMainThreadFallback()
        } catch (error: Exception) {
            Log.e(TAG, "privileged task failed", error)
            privilegedMainThreadFallback()
        }
    }

    private fun peekBoundService(): ITaskManagerService? = ShizukuUserServiceHost.peek()

    private fun bindService(context: Context): ITaskManagerService? =
        ShizukuUserServiceHost.ensure(context, minApi = 0)

    private fun bindFreshService(minApi: Int = 0): ITaskManagerService? =
        ShizukuUserServiceHost.ensure(appContext(), minApi)

    private fun forceRestartUserService(context: Context) {
        ShizukuUserServiceHost.drop(context)
    }

    private fun readServiceApi(taskService: ITaskManagerService): Int =
        ShizukuUserServiceHost.readApi(taskService)

    private fun privilegedTaskService(minApi: Int = 0): ITaskManagerService? {
        if (!hasPrivilegedAccess()) return null
        return when {
            PrivilegeGateway.isShizukuMode() && hasShizukuPermission() ->
                bindFreshService(minApi)
            PrivilegeGateway.isRootMode() ->
                RootTaskManagerServiceHost.peek()
                    ?: RootTaskManagerServiceHost.ensure(appContext(), minApi)
            else -> null
        }
    }

    private fun forceRestartPrivilegedTaskService() {
        if (PrivilegeGateway.isRootMode()) {
            RootTaskManagerServiceHost.drop(appContext())
            return
        }
        forceRestartUserService(appContext())
    }

    fun isShizukuRunning(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasShizukuPermission(): Boolean =
        isShizukuRunning() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private const val ROOT_ACCESS_CACHE_MS = 30_000L

    @Volatile
    private var cachedRootAccess: Boolean? = null

    @Volatile
    private var cachedRootAccessAtMs = 0L

    fun invalidatePrivilegedAccessCache() {
        cachedRootAccess = null
        cachedRootAccessAtMs = 0L
        cachedDirectRootAccess = null
        cachedDirectRootAccessAtMs = 0L
        runCatching {
            applicationContext?.let { RootTaskManagerServiceHost.drop(it) }
        }
    }

    fun hasPrivilegedAccess(): Boolean =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> cachedRootAccess()
            PrivilegeMode.SHIZUKU -> hasShizukuPermission()
        }

    fun peekPrivilegedAccess(): Boolean =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> cachedRootAccess ?: true
            PrivilegeMode.SHIZUKU -> peekShizukuPermission()
        }

    /**
     * 只读探测：**不**触发"取 Shizuku binder"。
     *
     * 取 binder 会顺带把主进程拉起来，并让本进程成为"依赖主进程里 provider"的进程；
     * 覆盖安装后主进程启动慢被 AMS 判死时，本进程（:overlay）会被系统连带杀掉，
     * 表现为悬浮球消失。所以启动期/界面渲染期的判断一律用这个被动版本，
     * 只有真正要执行特权操作时才用 [hasPrivilegedAccess]（它会惰性取 binder）。
     */
    fun peekShizukuPermission(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false) &&
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)

    private fun cachedRootAccess(): Boolean {
        val now = SystemClock.elapsedRealtime()
        cachedRootAccess?.let { cached ->
            if (now - cachedRootAccessAtMs < ROOT_ACCESS_CACHE_MS) return cached
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scheduleRootAccessProbe()
            return cachedRootAccess ?: true
        }
        val live = runPrivilegedOpsBlocking { RootPrivilegedOperations.probeRootAvailable() }
        cachedRootAccess = live
        cachedRootAccessAtMs = now
        return live
    }

    private fun scheduleRootAccessProbe() {
        privilegedOpsExecutor.execute {
            onPrivilegedOpsThread.set(true)
            try {
                val live = RootPrivilegedOperations.probeRootAvailable()
                cachedRootAccess = live
                cachedRootAccessAtMs = SystemClock.elapsedRealtime()
            } finally {
                onPrivilegedOpsThread.set(false)
            }
        }
    }

    fun hasPermission(): Boolean = hasPrivilegedAccess()

    fun checkAndRequestPermission(activity: Activity): Boolean {
        if (hasPrivilegedAccess()) return true
        if (PrivilegeGateway.isRootMode()) return false
        requestPermission(activity)
        return false
    }

    fun openShizukuApp(context: Context): Boolean {
        val packageManager = context.packageManager
        for (packageName in SHIZUKU_PACKAGES) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: continue
            val flags = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent.addFlags(flags))
            return true
        }
        return false
    }

    fun requestPermission(context: Context) {
        if (PrivilegeGateway.isRootMode()) return
        if (hasShizukuPermission()) return
        if (!isShizukuRunning()) {
            if (openShizukuApp(context)) {
                showShizukuToast(context, R.string.shizuku_start_service_hint)
            } else {
                showShizukuToast(context, R.string.shizuku_not_installed)
            }
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    private fun showShizukuToast(context: Context, messageResId: Int) {
        val appContext = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(appContext, messageResId, Toast.LENGTH_LONG).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, messageResId, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun warmUp() {
        if (!PrivilegeGateway.isShizukuMode() || !hasShizukuPermission() || warmUpInFlight) return
        warmUpInFlight = true
        Thread {
            try {
                bindService(appContext())
            } catch (error: Exception) {
                Log.w(TAG, "warmUp failed", error)
            } finally {
                warmUpInFlight = false
            }
        }.start()
    }

    fun warmUpPrivilegedBackend() {
        when {
            PrivilegeGateway.isShizukuMode() && hasShizukuPermission() -> warmUp()
            PrivilegeGateway.isRootMode() && !privilegedWarmUpInFlight -> {
                privilegedWarmUpInFlight = true
                privilegedOpsExecutor.execute {
                    onPrivilegedOpsThread.set(true)
                    try {
                        val live = RootPrivilegedOperations.probeRootAvailable()
                        cachedRootAccess = live
                        cachedRootAccessAtMs = SystemClock.elapsedRealtime()
                        if (live) {
                            runCatching { RootTaskManagerServiceHost.ensure(appContext(), minApi = 0) }
                                .onFailure { error ->
                                    Log.w(TAG, "warmUp root task daemon failed", error)
                                }
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "warmUpPrivilegedBackend failed", error)
                    } finally {
                        onPrivilegedOpsThread.set(false)
                        privilegedWarmUpInFlight = false
                    }
                }
            }
        }
    }

    fun probeDirectRootAvailable(): Boolean {
        val now = SystemClock.elapsedRealtime()
        cachedDirectRootAccess?.let { cached ->
            if (now - cachedDirectRootAccessAtMs < ROOT_ACCESS_CACHE_MS) return cached
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scheduleDirectRootProbe()
            return cachedDirectRootAccess ?: false
        }
        val live = runPrivilegedOpsBlocking {
            com.slideindex.app.shizuku.TaskManagerShellExecutor.probeRootAvailable()
        }
        cachedDirectRootAccess = live
        cachedDirectRootAccessAtMs = now
        return live
    }

    private fun scheduleDirectRootProbe() {
        privilegedOpsExecutor.execute {
            onPrivilegedOpsThread.set(true)
            try {
                val live = com.slideindex.app.shizuku.TaskManagerShellExecutor.probeRootAvailable()
                cachedDirectRootAccess = live
                cachedDirectRootAccessAtMs = SystemClock.elapsedRealtime()
            } finally {
                onPrivilegedOpsThread.set(false)
            }
        }
    }

    fun prefetchRecentTasks(force: Boolean = false) {
        warmRecentTasksAsync(delayMs = if (force) 0L else RECENTS_WARM_DELAY_MS)
    }

    // ---- 最近任务查询的“空闲预热” ---------------------------------------------------------------
    //
    // 第一次真正取最近任务时，用户服务进程会做一次「探测」：把十几种候选调用各跑一遍，
    // 挑出能覆盖全部任务的最小集合并记住（之后每次只用 1～2 条查询）。那一次注定比平时慢。
    //
    // 这跟“缓存任务列表”是两回事：这里只是把**查询方案的探测**挪到用户没在等的时候，
    // 不保存、也不复用任何任务数据；面板打开时仍然现取，实时性不变。

    @Volatile
    private var recentsWarmScheduled = false

    @Volatile
    private var recentsWarmed = false

    @Volatile
    private var recentsWarmAttempts = 0

    fun warmRecentTasksAsync(delayMs: Long = RECENTS_WARM_DELAY_MS) {
        if (recentsWarmed || recentsWarmAttempts >= MAX_RECENTS_WARM_ATTEMPTS) return
        synchronized(recentsWarmLock) {
            if (recentsWarmScheduled || recentsWarmed) return
            recentsWarmScheduled = true
        }
        val runnable = Runnable { startRecentsWarmUp() }
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun startRecentsWarmUp() {
        Thread({
            var attempted = false
            try {
                if (!recentsWarmed && hasPrivilegedAccess()) {
                    attempted = true
                    val startedAt = SystemClock.elapsedRealtime()
                    val refs = refreshRecentTasks()
                    Log.i(
                        TAG,
                        "warmRecentTasks ${refs.size} tasks in ${SystemClock.elapsedRealtime() - startedAt}ms"
                    )
                    recentsWarmed = refs.isNotEmpty()
                }
            } catch (error: Throwable) {
                Log.w(TAG, "warmRecentTasks failed", error)
            } finally {
                // 还没权限的情况不计入失败次数：等 Shizuku 权限/binder 到位后还会再触发一次。
                if (attempted) recentsWarmAttempts++
                synchronized(recentsWarmLock) { recentsWarmScheduled = false }
            }
        }, "RecentsWarmUp").start()
    }

    private val recentsWarmLock = Any()

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    private const val RECENTS_WARM_DELAY_MS = 8_000L
    private const val MAX_RECENTS_WARM_ATTEMPTS = 2

    fun refreshRecentTaskPackages(): List<String> {
        return refreshRecentTasks().map { it.identifier }
    }

    fun ensureServiceBound() {
        when {
            PrivilegeGateway.isRootMode() && hasPrivilegedAccess() -> {
                if (RootTaskManagerServiceHost.peek() != null) return
                Thread {
                    runCatching { RootTaskManagerServiceHost.ensure(appContext(), minApi = 0) }
                        .onFailure { error -> Log.w(TAG, "ensureServiceBound root daemon failed", error) }
                }.start()
            }
            PrivilegeGateway.isShizukuMode() && hasShizukuPermission() && peekBoundService() == null -> {
                Thread {
                    runCatching { bindService(appContext()) }
                        .onFailure { error -> Log.w(TAG, "ensureServiceBound failed", error) }
                }.start()
            }
        }
    }

    fun refreshRecentTasks(): List<RecentTaskRef> {
        if (!hasPrivilegedAccess()) return emptyList()
        if (PrivilegeGateway.isRootMode()) {
            val taskService = RootTaskManagerServiceHost.peek()
                ?: RootTaskManagerServiceHost.ensureQuick(appContext(), minApi = 0)
            if (taskService != null) {
                return TaskManagerTaskQueries.fetchRecentTasksFromService(taskService)
            }
            return runPrivilegedTask("refreshRecentTasks") {
                TaskManagerTaskQueries.parseRecentTaskRows(RootPrivilegedOperations.getRecentTasks())
            } ?: emptyList()
        }
        // 分身识别需要新服务返回第 5 列 userId；服务太旧时退回默认绑定（行为同旧版）。
        val taskService = privilegedTaskService(MIN_RECENTS_USER_API) ?: privilegedTaskService()
        if (taskService != null) {
            return TaskManagerTaskQueries.fetchRecentTasksFromService(taskService)
        }
        return emptyList()
    }

    fun resolveTaskIdForIdentifier(identifier: String): Int? {
        if (identifier.isBlank() || !hasPrivilegedAccess()) return null
        val service = privilegedTaskService(MIN_TASK_IDS_API) ?: privilegedTaskService()
        if (service != null) {
            val ids = runCatching { service.getTaskIdsForPackage(identifier.trim()) }
                .getOrDefault(emptyArray())
            return TaskManagerTaskQueries.resolveTaskIdFromIds(ids)
        }
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("resolveTaskIdForIdentifier") {
                val ids = RootPrivilegedOperations.getTaskIdsForPackage(identifier.trim())
                TaskManagerTaskQueries.resolveTaskIdFromIds(ids)
            }
        }
        return null
    }

    fun removeTaskById(taskId: Int): Boolean {
        if (taskId <= 0 || !hasPrivilegedAccess()) return false
        return runOnTaskWorker {
            val service = privilegedTaskService(MIN_REMOVE_TASK_API) ?: privilegedTaskService()
            if (service != null) {
                return@runOnTaskWorker service.removeTaskById(taskId.toString())
            }
            if (PrivilegeGateway.isRootMode()) {
                RootPrivilegedOperations.removeTaskById(taskId.toString())
            } else {
                false
            }
        }
    }

    fun switchToTask(
        taskId: Int,
        identifier: String = "",
        topComponent: String = "",
    ): Boolean {
        if (!hasPrivilegedAccess()) {
            Log.w(TAG, "switchToTask skipped: no privileged access")
            return false
        }
        if (taskId <= 0 && identifier.isBlank()) {
            Log.w(TAG, "switchToTask skipped: no taskId or identifier")
            return false
        }
        val service = privilegedTaskService(MIN_SWITCH_TO_TASK_API) ?: privilegedTaskService()
        if (service != null) {
            val api = runCatching { service.apiVersion }.getOrDefault(0)
            if (PrivilegeGateway.isShizukuMode() && api < MIN_SWITCH_TO_TASK_API) {
                Log.w(TAG, "switchToTask skipped: UserService api=$api lacks switch support")
                return false
            }
            return runCatching {
                service.switchToTask(
                    if (taskId > 0) taskId.toString() else "",
                    identifier,
                    topComponent,
                )
            }.getOrElse { error ->
                Log.e(
                    TAG,
                    "switchToTask binder error taskId=$taskId identifier=$identifier component=$topComponent",
                    error,
                )
                false
            }
        }
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("switchToTask:$taskId") {
                RootPrivilegedOperations.switchToTask(
                    if (taskId > 0) taskId.toString() else "",
                    identifier,
                    topComponent,
                )
            } ?: false
        }
        Log.w(TAG, "switchToTask failed: privileged task service unavailable")
        return false
    }

    fun removeCurrentFrontAppTask(): Boolean {
        val packageName = OverlayService.foregroundPackage
        if (!packageName.isNullOrBlank()) {
            return removeTaskByPackage(packageName)
        }
        val taskService = privilegedTaskService() ?: return false
        return try {
            val taskId = taskService.getFrontTaskId().takeIf { it.isNotBlank() } ?: return false
            taskService.removeTaskById(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "removeCurrentFrontAppTask failed", e)
            false
        }
    }

    fun removeTaskByPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (TaskSwitcherLockStore.isLocked(appContext(), packageName)) return false
        return try {
            val taskService = privilegedTaskService(MIN_TASK_IDS_API) ?: privilegedTaskService() ?: return false
            val taskIds = taskService.getTaskIdsForPackage(packageName)
            if (taskIds.isEmpty()) return false
            taskIds.any { taskService.removeTaskById(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeTaskByPackage($packageName) failed", e)
            false
        }
    }

    /**
     * 强行停止当前前台应用（真杀进程）。
     *
     * 与 [removeCurrentFrontAppTask] 的语义差别：后者只把任务从最近任务列表移除（等同 OHO+ 划掉
     * 卡片），持有前台服务或正在播放媒体的应用进程仍会存活；本方法走 force-stop，进程立即结束，
     * 代价是该包进入 Android 的 stopped 状态，重新手动启动前收不到广播 / 闹钟。
     *
     * 自身进程、SystemUI 与当前桌面始终跳过，避免误杀。
     */
    fun forceStopCurrentFrontApp(): Boolean {
        val packageName = OverlayService.foregroundPackage?.takeIf { it.isNotBlank() } ?: return false
        if (packageName == appContext().packageName) return false
        if (packageName == SYSTEM_UI_PACKAGE) return false
        if (packageName == currentHomePackage()) return false
        return forceStopPackage(packageName)
    }

    private fun currentHomePackage(): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        appContext().packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    }.getOrNull()

    fun forceStopPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!hasPrivilegedAccess()) return false
        return runOnTaskWorker {
            val service = privilegedTaskService(MIN_FORCE_STOP_API) ?: privilegedTaskService()
            if (service != null) {
                return@runOnTaskWorker service.forceStopPackage(packageName)
            }
            if (PrivilegeGateway.isRootMode()) {
                RootPrivilegedOperations.forceStopPackage(packageName)
            } else {
                false
            }
        }
    }

    fun movePackageToFreeWindow(packageName: String, settings: AppSettings): Boolean {
        if (!hasPrivilegedAccess()) return false
        if (privilegedTaskService() != null) {
            return TaskManagerUtilFreeWindow.movePackageToFreeWindow(
                packageName = packageName,
                settings = settings,
                hasPermission = true,
                bindFreshService = { minApi -> privilegedTaskService(minApi) },
            )
        }
        if (PrivilegeGateway.isRootMode()) {
            return runOnTaskWorker {
                val taskId = RootPrivilegedOperations.getTaskIdsForPackage(packageName).firstOrNull()
                    ?: return@runOnTaskWorker false
                RootPrivilegedOperations.moveTaskToFreeWindow(taskId, settings, appContext())
            }
        }
        return TaskManagerUtilFreeWindow.movePackageToFreeWindow(
            packageName = packageName,
            settings = settings,
            hasPermission = true,
            bindFreshService = ::bindFreshService,
        )
    }

    fun getPublishedShortcuts(packageName: String): List<Pair<String, String>> {
        if (packageName.isBlank() || !hasPrivilegedAccess()) return emptyList()
        return runOnTaskWorker {
            val service = privilegedTaskService(MIN_SHORTCUTS_API) ?: privilegedTaskService()
            val rows = if (service != null) {
                service.getPublishedShortcuts(packageName).orEmpty()
            } else if (PrivilegeGateway.isRootMode()) {
                RootPrivilegedOperations.getPublishedShortcuts(packageName)
            } else {
                emptyArray()
            }
            rows.mapNotNull { row ->
                val parts = row.split('\t', limit = 2)
                val id = parts.getOrNull(0)?.trim().orEmpty()
                if (id.isEmpty()) return@mapNotNull null
                val label = parts.getOrNull(1)?.trim().orEmpty().ifBlank { id }
                id to label
            }
        }
    }

    fun startPublishedShortcut(packageName: String, shortcutId: String): Boolean {
        if (packageName.isBlank() || shortcutId.isBlank() || !hasPrivilegedAccess()) return false
        return runOnTaskWorker {
            val service = privilegedTaskService(MIN_SHORTCUTS_API) ?: privilegedTaskService()
            when {
                service != null ->
                    service.startPublishedShortcut(packageName, shortcutId)
                PrivilegeGateway.isRootMode() ->
                    RootPrivilegedOperations.startPublishedShortcut(packageName, shortcutId)
                else -> false
            }
        }
    }

    fun loadCategorizedSystemShortcutMap(
        onProgress: ((ShortcutScanProgress) -> Unit)? = null,
    ): Map<ShortcutKind, Map<String, List<SystemShortcutEntry>>> {
        if (PrivilegeGateway.isRootMode() && hasPrivilegedAccess()) {
            return runOnTaskWorker {
                val service = privilegedTaskService(MIN_SHORTCUTS_API)
                val rows = if (service != null) {
                    service.getAllPublishedShortcuts().toList()
                } else {
                    RootPrivilegedOperations.getAllPublishedShortcuts().toList()
                }
                TaskManagerUtilShortcuts.loadCategorizedSystemShortcutMapFromRows(rows, onProgress)
            }
        }
        return TaskManagerUtilShortcuts.loadCategorizedSystemShortcutMap(
            hasPermission = hasShizukuPermission(),
            bindFreshService = ::bindFreshService,
            readServiceApi = ::readServiceApi,
            onProgress = onProgress,
        )
    }

    fun showVoiceAssistant(): Boolean {
        if (!hasPrivilegedAccess()) return false
        return runOnTaskWorker {
            val service = privilegedTaskService()
            if (service != null) {
                return@runOnTaskWorker service.showVoiceAssistant()
            }
            if (PrivilegeGateway.isRootMode()) {
                RootPrivilegedOperations.showVoiceAssistant()
            } else {
                false
            }
        }
    }

    fun runShellCommand(vararg cmd: String): Boolean {
        if (!hasPrivilegedAccess()) return false
        return runOnTaskWorker {
            val service = privilegedTaskService()
            if (service != null) {
                return@runOnTaskWorker service.runShellCommand(cmd)
            }
            if (PrivilegeGateway.isRootMode()) {
                RootPrivilegedOperations.runShellCommand(*cmd)
            } else {
                false
            }
        }
    }

    fun runShellCommandOutput(vararg cmd: String): ShellCommandResult {
        if (PrivilegeGateway.isRootMode()) {
            if (!hasPrivilegedAccess()) {
                return ShellCommandResult(
                    exitCode = -1,
                    output = appContext().getString(R.string.privileged_no_root),
                )
            }
            return runOnTaskWorker {
                val service = privilegedTaskService()
                if (service != null) {
                    val formatted = service.runShellCommandOutput(cmd)
                    return@runOnTaskWorker parseShellServiceOutput(formatted)
                }
                RootPrivilegedOperations.runShellCommandOutput(*cmd)
            }
        }
        return TaskManagerUtilShell.runShellCommandOutput(hasShizukuPermission(), ::bindFreshService, *cmd)
    }

    private fun parseShellServiceOutput(formatted: String): ShellCommandResult {
        val parts = formatted.split("\n---\n", limit = 2)
        val exitCode = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: -1
        val output = parts.getOrNull(1) ?: ""
        return ShellCommandResult(exitCode, output)
    }

    fun probeRootAvailable(): Boolean =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT ->
                runOnTaskWorker {
                    val service = privilegedTaskService()
                    if (service != null) {
                        runCatching { service.probeRootAvailable() }.getOrDefault(false)
                    } else {
                        RootPrivilegedOperations.probeRootAvailable()
                    }
                }
            PrivilegeMode.SHIZUKU ->
                TaskManagerUtilShell.probeRootAvailable(hasShizukuPermission(), ::bindFreshService, ::readServiceApi)
        }

    fun runShellCommandLine(
        command: String,
        useRoot: Boolean,
        timeoutMs: Long = 35_000L,
    ): ShellCommandResult {
        if (PrivilegeGateway.isRootMode()) {
            if (!hasPrivilegedAccess()) {
                return ShellCommandResult(
                    exitCode = -1,
                    output = appContext().getString(R.string.privileged_no_root),
                )
            }
            return runOnTaskWorker {
                val service = privilegedTaskService()
                if (service != null) {
                    val formatted = service.runShellCommandLine(command, useRoot, false)
                    return@runOnTaskWorker parseShellServiceOutput(formatted)
                }
                RootPrivilegedOperations.runShellCommandLine(command, timeoutMs)
            }
        }
        return TaskManagerUtilShell.runShellCommandLine(
            hasPermission = hasShizukuPermission(),
            bindFreshService = ::bindFreshService,
            readServiceApi = ::readServiceApi,
            command = command,
            useRoot = useRoot,
            timeoutMs = timeoutMs,
        )
    }

    /**
     * Explicit per-command routing. Never falls back to the other privilege mode.
     */
    fun runShellCommandLineExplicit(
        command: String,
        mode: ShellExecutionMode,
        timeoutMs: Long = 35_000L,
    ): ShellCommandResult = when (mode) {
        ShellExecutionMode.ROOT ->
            RootPrivilegedOperations.runShellCommandLine(command, timeoutMs)
        ShellExecutionMode.SHIZUKU ->
            TaskManagerUtilShell.runShellCommandLine(
                hasPermission = hasShizukuPermission(),
                bindFreshService = ::bindFreshService,
                readServiceApi = ::readServiceApi,
                command = command,
                useRoot = false,
                timeoutMs = timeoutMs,
            )
    }

    internal inline fun <reified T> runOnTaskWorker(noinline block: () -> T): T {
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedOpsBlocking(block)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // bindService runs inline on main; never block main on a background worker lock.
            return block()
        }
        synchronized(taskWorkerLock) {
            return block()
        }
    }

    private fun <T> runPrivilegedTask(label: String, block: () -> T): T? {
        if (onPrivilegedOpsThread.get() == true) {
            return block()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            privilegedOpsExecutor.execute {
                onPrivilegedOpsThread.set(true)
                try {
                    runCatching { block() }
                } finally {
                    onPrivilegedOpsThread.set(false)
                }
            }
            Log.w(TAG, "$label dispatched on main thread")
            return null
        }
        return try {
            privilegedOpsExecutor.submit(
                Callable {
                    onPrivilegedOpsThread.set(true)
                    try {
                        block()
                    } finally {
                        onPrivilegedOpsThread.set(false)
                    }
                },
            ).get(PRIVILEGED_OPS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            Log.e(TAG, "$label timed out", error)
            null
        } catch (error: Exception) {
            Log.e(TAG, "$label failed", error)
            null
        }
    }

    fun moveFrontTaskToFreeWindow(settings: AppSettings): Boolean {
        if (!hasPrivilegedAccess()) return false
        if (privilegedTaskService() != null) {
            return TaskManagerUtilFreeWindow.moveFrontTaskToFreeWindow(
                settings = settings,
                hasPermission = true,
                appContext = appContext(),
                bindFreshService = { minApi -> privilegedTaskService(minApi) },
                forceRestartUserService = { forceRestartPrivilegedTaskService() },
            )
        }
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("moveFrontTaskToFreeWindow") {
                val taskId = RootPrivilegedOperations.getFrontTaskId().takeIf { it.isNotBlank() }
                    ?: return@runPrivilegedTask false
                val frontPackage = RootPrivilegedOperations.getFrontTaskPackage()
                if (frontPackage.isNotBlank() &&
                    TaskExclusions.shouldSkipFreeWindow(frontPackage, appContext().packageName)
                ) {
                    return@runPrivilegedTask false
                }
                RootPrivilegedOperations.moveTaskToFreeWindow(taskId, settings, appContext())
            } ?: false
        }
        return TaskManagerUtilFreeWindow.moveFrontTaskToFreeWindow(
            settings = settings,
            hasPermission = true,
            appContext = appContext(),
            bindFreshService = ::bindFreshService,
            forceRestartUserService = ::forceRestartUserService,
        )
    }

    fun restartShellService(): Int {
        if (PrivilegeGateway.isRootMode()) {
            return RootTaskManagerServiceHost.restart(appContext())
        }
        return ShizukuUserServiceHost.restart(appContext())
    }

    fun getRecentTaskPackages(): List<String>? {
        if (!hasPrivilegedAccess()) return null
        val taskService = privilegedTaskService()
        if (taskService != null) {
            return try {
                taskService.getRecentTaskPackages().toList()
            } catch (e: Exception) {
                Log.e(TAG, "getRecentTaskPackages failed", e)
                null
            }
        }
        if (PrivilegeGateway.isRootMode()) {
            return runPrivilegedTask("getRecentTaskPackages") {
                RootPrivilegedOperations.getRecentTaskPackages().toList()
            }
        }
        return null
    }

    private fun appContext(): Context =
        applicationContext ?: error("TaskManagerUtil.initialize() must be called before use")
}
