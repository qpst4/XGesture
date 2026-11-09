package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * 无障碍链路可用性：比“Service 实例还在不在”更诚实的那把尺子。
 *
 * 背景：系统可以在**不给任何生命周期回调**的情况下把无障碍连接摘掉
 * （`AccessibilityService` 收到 `init(connectionId = NO_ID, windowToken = null)`，
 * 既不回调 `onUnbind` 也不回调 `onDestroy`）。从那一刻起：
 *  - `instance` 仍然非空，所有 `isConnected() = instance != null` 的判断都在说谎；
 *  - `performGlobalAction` 静默返回 false（返回/Home/最近任务全部失效）；
 *  - 新建 `TYPE_ACCESSIBILITY_OVERLAY` 窗口抛 `BadTokenException: token null`
 *    （悬浮球、加号弹窗、角轮盘、取词预览、结果面板全部挂不上）。
 *
 * 本对象的职责只有一件：**把上面这些真实失败记下来**，并在状态由“可用”翻到“不可用”的那一次
 * 通知唯一的恢复入口。恢复入口由 `EdgeOverlayHost` 在 start/stop 里显式 attach/detach，
 * 不在这里做生命周期管理（对应 AGENTS 4.1 的静态入口例外：不可变接口 + 显式生命周期）。
 */
object AccessibilityLink {
    const val TAG = "AccessibilityLink"

    /** 恢复尝试的最小间隔：避免“拆窗 → 重绑 → 再失败”打成死循环。 */
    private const val RECOVERY_THROTTLE_MS = 60_000L

    @Volatile
    private var broken = false

    @Volatile
    private var brokenReason: String? = null

    @Volatile
    private var lastAliveAtMs = 0L

    @Volatile
    private var lastRecoveryAtMs = 0L

    /** 累计掉线次数 / 恢复尝试次数 / 最近一次恢复结果，供诊断报告使用。 */
    @Volatile
    private var brokenCount = 0

    @Volatile
    private var recoveryAttempts = 0

    @Volatile
    private var lastRecoveryResult: String = "-"

    /**
     * 掉线恢复入口。必须由持有浮层宿主的一方在 start/stop 里成对设置与清空；
     * 没有设置时本对象只记日志，不做任何事。
     */
    @Volatile
    private var recoveryHandler: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun attachRecovery(handler: (String) -> Unit) {
        recoveryHandler = handler
    }

    fun detachRecovery() {
        recoveryHandler = null
    }

    /** 链路现在是否可信。默认 true：只有拿到真实失败证据才会翻成 false。 */
    fun isUsable(): Boolean = !broken

    fun isBroken(): Boolean = broken

    fun brokenReason(): String? = brokenReason

    fun lastAliveAtMs(): Long = lastAliveAtMs

    fun brokenCount(): Int = brokenCount

    fun recoveryAttempts(): Int = recoveryAttempts

    fun lastRecoveryResult(): String = lastRecoveryResult

    /** 记录一次恢复结果（成功 / 失败原因），诊断报告会原样带出。 */
    fun noteRecoveryResult(result: String) {
        lastRecoveryResult = result
    }

    /** 记录一次“连接确实还能用”的证据（任意成功调用 / 探活成功都会走这里）。 */
    fun noteAlive(source: String) {
        if (broken) {
            Log.i(TAG, "accessibility link recovered: $source")
        }
        broken = false
        brokenReason = null
        lastAliveAtMs = SystemClock.elapsedRealtime()
    }

    /**
     * 记录一次“连接已经不可用”的证据。
     *
     * @return true 表示这次调用真的触发了恢复入口（只在“可用 → 不可用”的跳变、
     *         且距上次恢复尝试超过 [RECOVERY_THROTTLE_MS] 时）。
     */
    fun noteBroken(source: String, detail: String? = null): Boolean {
        val reason = if (detail.isNullOrBlank()) source else "$source ($detail)"
        val wasBroken = broken
        broken = true
        brokenReason = reason
        if (!wasBroken) {
            brokenCount++
        }
        if (wasBroken) return false

        Log.e(TAG, "accessibility link broken: $reason")
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoveryAtMs < RECOVERY_THROTTLE_MS) {
            Log.w(TAG, "recovery throttled (${now - lastRecoveryAtMs}ms since last attempt)")
            return false
        }
        lastRecoveryAtMs = now
        recoveryAttempts++
        lastRecoveryResult = "进行中"
        val handler = recoveryHandler
        if (handler == null) {
            Log.w(TAG, "no recovery handler attached, only logging")
            return false
        }
        // 必须延后一拍：noteBroken 经常在“建窗失败的 catch 里 / binder 线程”被调用，
        // 恢复动作要拆掉整批窗口，绝不能插在调用方的调用栈中间执行（重入 + 死锁风险）。
        mainHandler.post {
            runCatching { handler(reason) }
                .onFailure { Log.e(TAG, "recovery handler failed", it) }
        }
        return true
    }

    /** 服务正常连接 / 销毁时复位，避免把上一代的状态带到这一代。 */
    fun reset() {
        broken = false
        brokenReason = null
    }

    @VisibleForTesting
    internal fun resetForTest() {
        broken = false
        brokenReason = null
        lastAliveAtMs = 0L
        lastRecoveryAtMs = 0L
        recoveryHandler = null
    }
}
