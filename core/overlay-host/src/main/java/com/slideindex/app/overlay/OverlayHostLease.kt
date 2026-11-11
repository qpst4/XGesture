package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * 浮层宿主的进程级租约兜底。
 *
 * 无障碍服务和它创建的浮层窗口都跑在 `:overlay` 进程里。一旦系统把无障碍服务解绑/回收，
 * 而拆卸流程没有走完（回调里抛异常、连接被直接丢弃等），窗口会继续留在 WindowManager 里：
 * 其中 presentation 是**全屏可触摸**的，会一直吞掉整屏的按下与滑动，表现就是
 * "整台手机点不动、滑不动"，而服务已经没了，没有任何代码路径能把它救回来。
 *
 * 这里做两层兜底：
 * 1. 无障碍服务存活期间定时 [renew] 续租，守护循环发现租约过期即判定"服务已经不在了"；
 * 2. 各浮层宿主启动时 [register] 一个幂等的拆卸句柄，租约过期时由守护循环
 *    在本进程主线程执行，把残留窗口摘干净。
 *
 * 守护循环和续租都在主线程空闲时才可能超时：主线程被卡住时两者一起停摆，
 * 不会出现"误判服务已死"的自我拆台。
 */
object OverlayHostLease {
    private const val TAG = "OverlayHostLease"

    /** 巡检间隔：单进程下只用来兜底"服务没了但窗口还在"这一种情况。 */
    private const val CHECK_INTERVAL_MS = 2_000L

    /** 连续多少次判定失联才真的拆卸（一次抖动不算，见 watchdog 里的说明）。 */
    private const val OWNER_LOST_CONFIRMATIONS = 2

    /**
     * 宿主失联时的诊断回调。
     *
     * 本模块不认识 `:app` 的诊断日志（EdgeDiag），由 Application 启动时把两者接起来；
     * 不接的话就只留下面那行 `Log.w`（行为不变，只是少一条可导出的诊断记录）。
     */
    @Volatile
    var onHostLost: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private class Registration(
        val isOwnerAlive: () -> Boolean,
        val teardown: () -> Unit
    )

    private val registrations = LinkedHashMap<String, Registration>()

    private var watchdogScheduled = false

    /** 连续判定“宿主失联”的次数：至少两次才动手拆，见 [OWNER_LOST_CONFIRMATIONS]。 */
    private var consecutiveOwnerFailures = 0

    private val watchdog = Runnable {
        watchdogScheduled = false
        if (registrations.isEmpty()) return@Runnable
        // 单进程：无障碍实例和窗口在同一个进程里，直接看实例存活即可，不再需要跨进程租约过期判定。
        val ownerAlive = registrations.values.any { registration ->
            runCatching { registration.isOwnerAlive() }.getOrDefault(true)
        }
        if (!ownerAlive) {
            consecutiveOwnerFailures += 1
            // 只失联一次不动手：重绑/窗口过渡的瞬时抖动里拆家，会把用户正在用的浮层全清掉，
            // 而重挂要等设置回流或 12s 自愈（没有 REBIND 权限时甚至要手动开关一次）。
            // 连续两次（约 4s）仍失联才认为是真的没了。
            if (consecutiveOwnerFailures < OWNER_LOST_CONFIRMATIONS) {
                Log.w(
                    TAG,
                    "浮层宿主疑似失联（$consecutiveOwnerFailures/$OWNER_LOST_CONFIRMATIONS），先观察再拆",
                )
                scheduleWatchdog()
                return@Runnable
            }
            val pending = registrations.values.map { it.teardown }
            registrations.clear()
            onHostLost?.invoke(
                "OverlayHostLease 判定宿主失联（无障碍实例为 null）→ 强制拆卸 ${pending.size} 个宿主",
            )
            Log.w(
                TAG,
                "浮层宿主失联（无障碍服务已不在本进程），强制拆卸 ${pending.size} 个宿主"
            )
            pending.forEach { teardown ->
                runCatching { teardown() }
                    .onFailure { Log.e(TAG, "租约超时拆卸失败", it) }
            }
            return@Runnable
        }
        consecutiveOwnerFailures = 0
        scheduleWatchdog()
    }

    /** 兼容旧调用点：单进程不需要续租。 */
    @Suppress("UNUSED_PARAMETER")
    fun renew() = Unit

    /**
     * 登记一个浮层宿主。同一 [key] 重复登记会覆盖旧的。
     *
     * [isOwnerAlive] 每 2s 复核一次宿主服务是否还在（例如读无障碍服务开关），
     * 返回 false 时立刻拆卸，不再等租约超时；[teardown] 必须是幂等的。
     *
     * 宿主正常 [unregister] 后守护循环自动停止。
     */
    fun register(key: String, isOwnerAlive: () -> Boolean, teardown: () -> Unit) {
        mainHandler.post {
            registrations[key] = Registration(isOwnerAlive, teardown)
            renew()
            scheduleWatchdog()
        }
    }

    fun unregister(key: String) {
        mainHandler.post {
            registrations.remove(key)
            if (registrations.isEmpty()) {
                watchdogScheduled = false
                mainHandler.removeCallbacks(watchdog)
            }
        }
    }

    /** 仅测试使用：清空状态。 */
    internal fun resetForTest() {
        mainHandler.removeCallbacks(watchdog)
        registrations.clear()
        watchdogScheduled = false
        consecutiveOwnerFailures = 0
    }

    /** 仅测试使用：手动触发一次守护检查。 */
    internal fun runWatchdogNowForTest() {
        mainHandler.removeCallbacks(watchdog)
        watchdogScheduled = false
        watchdog.run()
    }

    private fun scheduleWatchdog() {
        if (watchdogScheduled) return
        watchdogScheduled = true
        mainHandler.postDelayed(watchdog, CHECK_INTERVAL_MS)
    }
}
