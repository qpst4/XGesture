package com.slideindex.app.overlay.history

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * 截图期临时隐藏「贴边收纳把手」（进程级把手）。
 *
 * 为什么需要它：把手是 [com.slideindex.app.service.HistoryFloatService] 自己的**常驻窗**，
 * 取词 / 屏内搜索的 `AccessibilityService.takeScreenshot` 会把右侧那条把手一起拍进全屏截图
 * （和边缘触钮是同一类漏网）。而两个链路之间**零引用**（见
 * `docs/capsule-refactor-p0-findings.md` §1：面板链路对把手零协同），所以这里用一个进程级对象当桥：
 * Service 挂窗时注册 [setApplier]，截图侧调 [suppress] / [restore]。
 *
 * ## 为什么带租约（看门狗）
 *
 * 恢复一旦丢失，把手会**永远不可见**（用户"找不到收纳把手"），这比"多拍进一条把手"严重得多
 * —— 和 [StashPanelExternalUi] 那次的教训同类（挂起后恢复丢了 = 面板冻死）。
 * 所以 [suppress] 只买一张 [LEASE_TIMEOUT_MS] 的租约：到期没人 [restore]，自己恢复。
 * 正常路径是亚秒级（截图链路在 `finally` 里恢复），永远不会走到租约。
 *
 * 线程约定：只隐藏绘制（`view.alpha`），所有动作都落在主线程；截图链路本身就在主线程调用，
 * 所以 [suppress] 返回时把手已经不可见了（这才是"截不到"的前提）。
 */
object HistoryHandleCaptureVisibility {

    /** 租约时长：截图链路正常 200ms 内恢复，超过这个时间一定是恢复丢了。 */
    private const val LEASE_TIMEOUT_MS = 3_000L

    /** 租约检查间隔（比超时小一个量级：过期后最多晚这么久被发现）。 */
    private const val TICK_MS = 500L

    private val handler = Handler(Looper.getMainLooper())

    /** Service 注册的"重新算一次把手可见性"（见 `HistoryFloatService.applyFloatVisibility`）。 */
    @Volatile
    private var applier: (() -> Unit)? = null

    @Volatile
    private var suppressed = false

    /** 租约到期时刻（uptime）；0 = 没有在租。 */
    @Volatile
    private var leaseDeadlineMs = 0L

    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            if (!suppressed) {
                ticking = false
                return
            }
            if (SystemClock.uptimeMillis() >= leaseDeadlineMs) {
                restore()
                return
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** 把手当前是否因截图被隐藏（Service 算可见性时读它）。 */
    val isSuppressed: Boolean get() = suppressed

    /** Service 挂窗后注册；[detachApplier] 清空。 */
    fun setApplier(action: () -> Unit) {
        applier = action
    }

    /** Service 销毁：窗已经没了，状态一并复位，避免下次启动带着陈旧的隐藏态。 */
    fun detachApplier() {
        applier = null
        suppressed = false
        leaseDeadlineMs = 0L
        ticking = false
        handler.removeCallbacks(tick)
    }

    /** 截图前调用（幂等；重复调用只续租）。返回时若 Service 在跑，把手已经不可见。 */
    fun suppress() {
        leaseDeadlineMs = SystemClock.uptimeMillis() + LEASE_TIMEOUT_MS
        if (suppressed) return
        suppressed = true
        startTick()
        applyNow()
    }

    /** 截图后调用（幂等）。 */
    fun restore() {
        ticking = false
        handler.removeCallbacks(tick)
        leaseDeadlineMs = 0L
        if (!suppressed) return
        suppressed = false
        applyNow()
    }

    private fun startTick() {
        if (ticking) return
        ticking = true
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
    }

    private fun applyNow() {
        val action = applier ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }
}
