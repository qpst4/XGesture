package com.slideindex.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Owns Shizuku UserService bind / unbind / restart lifecycle.
 *
 * Do not pin [TaskManagerUserService] to a fixed android:process in the manifest —
 * Shizuku uses [Shizuku.UserServiceArgs.processNameSuffix] to pick the remote process.
 */
object ShizukuUserServiceHost {

    private const val TAG = "ShizukuUserServiceHost"
    private const val BIND_TIMEOUT_MS = 2_500L
    private const val RESTART_ATTEMPTS = 2
    private const val RESTART_DELAY_MS = 300L

    /**
     * Bump when the remote service code/ABI changes; drives processNameSuffix.
     *
     * 39：root 侧改为在 init mount namespace 启动（`nsenter -t 1 -m`）。升号让覆盖安装后
     *     旧的、继承 App namespace 的 root 守护进程/用户服务自动失效重启，不需要用户手动杀进程。
     * 38：最近任务不再过滤本应用包名（任务切换器需要显示自己）。这是运行在用户服务进程里的
     *     `TaskExclusions` / reader 逻辑，必须换进程名才能让已安装的新包生效。
     * 37：`getRecentTasks()` 行格式增加第 5 列 userId（分身识别）。
     * 升号后进程名变成 `task_manager_v39`，安装新包后不必重启手机/Shizuku 也能拿到新代码。
     */
    const val SERVICE_BUILD = 40

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bindLock = Any()

    @Volatile
    private var service: ITaskManagerService? = null

    @Volatile
    private var cachedArgs: Shizuku.UserServiceArgs? = null

    @Volatile
    private var pendingBindLatch: CountDownLatch? = null

    @Volatile
    private var ensureLatch: CountDownLatch? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                ITaskManagerService.Stub.asInterface(binder)
            } else {
                Log.e(TAG, "connected with dead binder")
                null
            }
            if (service != null) {
                val api = readApi(service)
                Log.i(TAG, "connected api=$api build=$SERVICE_BUILD")
            }
            finishBindAttempt()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "disconnected")
            service = null
            finishBindAttempt()
        }
    }

    fun peek(): ITaskManagerService? =
        service?.takeIf { it.asBinder().pingBinder() }

    fun readApi(taskService: ITaskManagerService?): Int =
        taskService?.let { runCatching { it.apiVersion }.getOrDefault(0) } ?: 0

    fun hasShizukuPermission(@Suppress("UNUSED_PARAMETER") context: Context): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false) &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    /**
     * Returns a live UserService whose [ITaskManagerService.getApiVersion] is at least [minApi],
     * or null when Shizuku is unavailable / the remote process cannot be started.
     */
    fun ensure(context: Context, minApi: Int = 0): ITaskManagerService? {
        if (!hasShizukuPermission(context)) return null

        synchronized(bindLock) {
            peek()?.let { live ->
                if (readApi(live) >= minApi) return live
                Log.w(TAG, "stale api=${readApi(live)} need=$minApi, dropping")
                dropLocked(context)
            }
        }

        var latch: CountDownLatch
        var isLeader = false
        synchronized(bindLock) {
            val inFlight = ensureLatch
            if (inFlight != null) {
                latch = inFlight
            } else {
                latch = CountDownLatch(1)
                ensureLatch = latch
                isLeader = true
            }
        }

        if (!isLeader) {
            val maxWaitMs = BIND_TIMEOUT_MS * RESTART_ATTEMPTS + RESTART_DELAY_MS * RESTART_ATTEMPTS
            runCatching { latch.await(maxWaitMs, TimeUnit.MILLISECONDS) }
            return synchronized(bindLock) {
                peek()?.takeIf { readApi(it) >= minApi }
            }
        }

        return try {
            repeat(RESTART_ATTEMPTS) { attempt ->
                val bound = bindBlocking(context) ?: run {
                    Log.w(TAG, "bind attempt #$attempt returned null")
                    Thread.sleep(RESTART_DELAY_MS)
                    return@repeat
                }
                val api = readApi(bound)
                if (api >= minApi) return bound

                Log.w(TAG, "bound api=$api still below $minApi on attempt #$attempt, dropping")
                synchronized(bindLock) { dropLocked(context) }
                Thread.sleep(RESTART_DELAY_MS)
            }
            null
        } finally {
            synchronized(bindLock) {
                ensureLatch = null
                latch.countDown()
            }
        }
    }

    /** Force-kill remote process and bind a fresh one. Returns connected api, or -1. */
    fun restart(context: Context): Int {
        synchronized(bindLock) { dropLocked(context) }
        val bound = ensure(context, minApi = 0)
        return readApi(bound).takeIf { it > 0 } ?: -1
    }

    fun drop(context: Context) {
        synchronized(bindLock) { dropLocked(context) }
    }

    private fun dropLocked(context: Context) {
        runCatching { service?.destroy() }
        unbindAllKnownBuilds(context)
        service = null
        cachedArgs = null
        finishBindAttempt()
    }

    private fun unbindAllKnownBuilds(context: Context) {
        runCatching {
            cachedArgs?.let { Shizuku.unbindUserService(it, connection, true) }
        }
        // Legacy manifest pinned :task_manager_v10 — tear down once, not every historical build.
        runCatching {
            Shizuku.unbindUserService(
                buildArgs(context, "task_manager_v10"),
                connection,
                true
            )
        }
    }

    private fun bindBlocking(context: Context): ITaskManagerService? {
        peek()?.let { return it }

        var latch: CountDownLatch
        var shouldBind = false

        synchronized(bindLock) {
            peek()?.let { return it }
            val inFlight = pendingBindLatch
            if (inFlight != null) {
                latch = inFlight
            } else {
                latch = CountDownLatch(1)
                pendingBindLatch = latch
                shouldBind = true
            }
        }

        if (shouldBind) {
            val bindAction = Runnable {
                runCatching {
                    Shizuku.bindUserService(currentArgs(context), connection)
                }.onFailure { error ->
                    Log.e(TAG, "bindUserService failed", error)
                    finishBindAttempt()
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                bindAction.run()
            } else {
                mainHandler.post(bindAction)
            }
        }

        return try {
            if (!latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "bind timed out after ${BIND_TIMEOUT_MS}ms")
                finishBindAttempt()
            }
            peek()
        } catch (error: InterruptedException) {
            Log.e(TAG, "bind interrupted", error)
            finishBindAttempt()
            null
        }
    }

    private fun currentArgs(context: Context): Shizuku.UserServiceArgs {
        cachedArgs?.let { return it }
        return buildArgs(context, "task_manager_v$SERVICE_BUILD")
            .also { cachedArgs = it }
    }

    private fun buildArgs(context: Context, suffix: String): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, TaskManagerUserService::class.java.name)
        )
            .daemon(true)
            .processNameSuffix(suffix)
            .tag("TaskManagerUserService_$suffix")
            .version(SERVICE_BUILD)

    private fun finishBindAttempt() {
        synchronized(bindLock) {
            pendingBindLatch?.countDown()
            pendingBindLatch = null
        }
    }
}
