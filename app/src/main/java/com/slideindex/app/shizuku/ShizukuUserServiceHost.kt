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
     * 65：多指手势回调带上触摸锚点（onGestureBatch / onPathGesture 追加 anchorU/anchorV），
     *     root 轮询事件同步扩展为 `g:kind:fingers:u:v` / `p:fingers:u:v:strokes`。
     *     回调 ABI 变了：旧进程会用旧协议调 app 的新 Stub，两个锚点参数会读成 0（坐标变成左上角），
     *     必须换进程名让新代码接管。
     * 64：新增 root 模式轮询接口（startMultiTouchPolling/pollMultiTouchEvents/stopMultiTouchPolling）。
     * 63：冲突状态首次强制上报（避免其它触控工具停止后 app 仍显示旧冲突）。
     * 62：触摸解除「仅双指及以上」限制，单指也会产生 TOUCH 候选（由条目匹配兜底）。
     * 61：候选携带诊断信息（旋转评分/罚项/半径比、捏合差值），日志可直接定位误判。
     * 60：点击改为「抬手立即产生 TAP/DOUBLE/TRIPLE 候选」，手指变化重置位移基准，tapSlop 收窄到 0.006。
     * 59：点击判定不再被几何候选取消；触摸补「两指先后按下」分支；手势尺寸默认 5。
     * 58：触摸改为「按下即触发、仅 ≥2 指」；滑动方向改为逐指分类众数（至少 2/3 同向）。
     * 57：滑动方向要求连续 3 帧一致；新增「触摸」（TOUCH）类型与两指轻点支持。
     * 56：识别器改为「每种类型独立判定、输出多候选」，AIDL 新增 onGestureBatch。
     * 55：旋转评分改为「|cross|/Σr²」，捏合改「半径差 ≥ max(1, 手势尺寸/4) mm」。
     * 54：旋转去掉半径变化惩罚项、阈值按角度近似下调（0.05 / 四指 0.04）。
     * 53：旋转评分改为「|cross| / (2·Σr²)」，阈值重新校准。
     * 52：多指手势改为「条目 + 1–5 指」识别，枚举扩展（点按/八方向/长按）。
     * 51：`startMultiTouchObservation` 增加路径优先参数，AIDL 签名变更。
     * 50：路径手势改为多轨迹（每指一条，2–5 条），回调协议同步改为 strokeLengths。
     * 49：冲突检测补充诊断日志（定位回调链路）。
     * 48：冲突检测改为独立线程轮询，不再依赖触摸事件触发。
     * 47：运行期每 5 秒重扫虚拟触摸设备，设备在启动后才出现时也能及时提示冲突。
     * 46：新增虚拟触摸设备冲突检测（onInputConflict），AIDL 接口变更。
     * 45：触摸设备枚举排除 relay/virtual 设备，避免选中其它程序创建的虚拟触摸屏。
     * 44：多指回调新增路径手势（onPathGesture），旧进程的 AIDL 接口没有这个方法，
     *     必须换进程名才能让新回调生效。
     * 43：多指识别器改为「相对质心的叉积/半径旋转判定 + RMS 半径捏合判定」，
     *     四指不再参与滑动；识别逻辑在 core:gesture，升号强制换进程才能加载新实现。
     * 42：多指识别器改为「逐帧累计旋转 + 四指放宽阈值」，识别逻辑在 core:gesture，
     *     用户服务进程加载的是安装时的 APK，升号强制换进程才能让新识别器生效。
     * 41：`ITaskManagerService` 新增多指手势观察（start/stop/isRunning）与回调接口，
     *     旧进程没有这些方法，必须换进程名才能让新包生效。
     * 39：root 侧改为在 init mount namespace 启动（`nsenter -t 1 -m`）。升号让覆盖安装后
     *     旧的、继承 App namespace 的 root 守护进程/用户服务自动失效重启，不需要用户手动杀进程。
     * 38：最近任务不再过滤本应用包名（任务切换器需要显示自己）。这是运行在用户服务进程里的
     *     `TaskExclusions` / reader 逻辑，必须换进程名才能让已安装的新包生效。
     * 37：`getRecentTasks()` 行格式增加第 5 列 userId（分身识别）。
     * 66：最近任务查询改为「首次探测 + 记住最小覆盖集」，只跑 1～2 条查询（原来每次跑满
     *     16 次候选调用）。查询逻辑在用户服务进程里，必须换进程名才能让新包生效。
     * 升号后进程名变成 `task_manager_v65`，安装新包后不必重启手机/Shizuku 也能拿到新代码。
     */
    const val SERVICE_BUILD = 66

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
