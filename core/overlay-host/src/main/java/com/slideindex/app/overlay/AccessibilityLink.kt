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

    /** 弱证据（动作被拒）要连续出现几次才升级成“掉线”。 */
    private const val WEAK_BREAK_CONFIRMATIONS = 3

    @Volatile
    private var broken = false

    /** 弱证据的连续计数：任意一次成功调用（[noteAlive]）或复位都会清零。 */
    @Volatile
    private var weakBrokenCount = 0

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

    /**
     * 记录一次恢复结果，诊断报告会原样带出。
     *
     * 传**与语言无关的 ASCII 标识**（`ok` / `in_progress` / `failed(<outcome>)`）：这个值会进
     * 崩溃报告，既不该被当作可翻译的 UI 字符串，也更利于日志检索。
     */
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
        weakBrokenCount = 0
        lastAliveAtMs = SystemClock.elapsedRealtime()
    }

    /**
     * 记录一次**弱证据**：动作被系统拒绝，不等于连接已经断开。
     *
     * `performGlobalAction` 返回 false 的真实原因里，有一大半与链路无关：旧机型没有截图 /
     * 电源菜单 / 快捷设置这些动作、全屏视频里动作被系统拒绝、锁屏期间某些动作不可用。
     * 以前这种 false 会直接 [noteBroken]，于是"一次动作没做成"就升级成"无障碍断线"，
     * 看门狗随即拆掉整批浮层（触钮、悬浮球、轮盘全没），而重绑需要 WRITE_SECURE_SETTINGS，
     * 普通用户只能手动开关一次才能恢复。
     *
     * 所以弱证据必须**连续**出现 [WEAK_BREAK_CONFIRMATIONS] 次才升级成掉线。
     */
    fun noteSuspect(source: String, detail: String? = null) {
        weakBrokenCount += 1
        if (weakBrokenCount < WEAK_BREAK_CONFIRMATIONS) {
            Log.w(TAG, "suspect failure $weakBrokenCount/$WEAK_BREAK_CONFIRMATIONS: $source")
            return
        }
        weakBrokenCount = 0
        noteBroken(source, detail)
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
        lastRecoveryResult = "in_progress"
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
        weakBrokenCount = 0
    }

    @VisibleForTesting
    internal fun resetForTest() {
        broken = false
        brokenReason = null
        weakBrokenCount = 0
        lastAliveAtMs = 0L
        lastRecoveryAtMs = 0L
        recoveryHandler = null
    }
}
