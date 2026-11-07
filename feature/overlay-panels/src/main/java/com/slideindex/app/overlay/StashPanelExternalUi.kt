package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * 「拉起外部 UI（相册选择器 / 内置图片编辑器）」期间挂起 / 恢复收纳面板窗的进程级把手（§0.16.14）。
 *
 * 为什么需要它：面板是**无障碍覆盖层**，比系统相册/编辑器高一层 —— 不挂起的话，用户选图/涂鸦时我们的面板
 * 就盖在上面（点不着外部界面）。而发起的地方是 overlay 里的 Compose 代码（`HistoryPanelScreen`），
 * 它拿不到 [FloatBallStashPanel] 手里的 `sideHost`，所以由 [FloatBallStashPanel] 在窗口挂上之后把两个
 * 动作注册到这里（[suspend] / [resume]）。
 *
 * ---
 * ## §0.16.23：**没有任何路径能让 resume 丢失**（本对象存在的全部理由）
 *
 * 用户实测的严重 bug：「进一次编辑器 → 点编辑器工具栏返回（不是保存）→ 回面板再点 ✎ → 面板消失、
 * 进不去」—— 根因就是挂起之后那条恢复调用**根本没发生**（结果回调压根没来，见下面第 2 条那道闸：
 * 结果只能经 trampoline 的 `registerForActivityResult` 回来，而那一版代码压根没走 launcher），
 * 面板于是永远停在 `INVISIBLE + FLAG_NOT_TOUCHABLE` 上。
 *
 * 四道保险，缺一不可：
 * 1. **幂等**：[suspendForExternalUi] / [resumeAfterExternalUi] 重复调用无害（第二次只打一行日志），
 *    所以"回调恢复 + 兜底恢复"可以都调，不会互相打架；[isSuspended] 可随时查询。
 * 2. **看门狗**：挂起时起一个 5s 的租约；**外部 UI 活着的时候由它自己续租**
 *    （[renewExternalUiLease]，两个 trampoline 每 2s 调一次）。租约过期 = 外部 UI 已经不在了
 *    或者结果/生命周期丢了 → 自动恢复并打日志，绝不把面板冻死。
 * 3. **宿主自报**：面板窗被重新呼出时（`FloatBallStashPanel.show` 里那句直接 resume）会经
 *    [onHostWindowResumed] 把状态拉回"未挂起"，免得陈旧的挂起标记挡掉用户下一次 ✎。
 * 4. **收起即作废**：面板被收起（[onHostWindowDismissed]）后，"恢复"不再成立 —— 见 [resumeAfterExternalUi]
 *    的 KDoc（无条件点亮一个已 `GONE` 的全屏窗 = "整屏点不动"）。
 *
 * ⚠️ 调用方一律写成 [suspendForExternalUi] / [resumeAfterExternalUi]（**不要**再直接 `suspend?.invoke()`）：
 * 那两句话里包含了状态、看门狗和日志。面板可能已经被系统摘掉（[clear] 把两个字段置回 null），
 * 这时外部 UI 照常走，只是没什么可挂起的。
 */
object StashPanelExternalUi {
    /**
     * 本链路统一日志 tag（字符串与 [StashEditImageTrampolineActivity.LOG_TAG] **同一个值**）：
     * `adb logcat -s StashImageEdit` 能把"点 ✎ → 起中转 → 编辑器 → 结果 → 替换 → 恢复"一次看全。
     */
    private const val TAG = "StashImageEdit"

    /** 拉起外部 UI **之前**调用：挂起面板窗（不可见 + 不吃触摸）。由 [FloatBallStashPanel] 注册。 */
    var suspend: (() -> Unit)? = null

    /** 外部 UI 的**回调里第一件事**：恢复面板窗。由 [FloatBallStashPanel] 注册。 */
    var resume: (() -> Unit)? = null

    /**
     * 看门狗期限（ms）。
     *
     * 5s 的依据：正常路径上"挂起 → 外部 UI 起来"是**亚秒级**（一次 `startActivity`），
     * 而 trampoline 在 `onCreate` 里立刻续一次租、之后每 [LeaseRenewIntervalMs] 续一次，
     * 所以真正长的编辑（用户涂鸦几分钟）走的是续租，不会被误伤。
     */
    private const val WatchdogTimeoutMs = 5_000L

    /** 看门狗检查间隔（比超时小一个量级：过期后最多晚这么久被发现）。 */
    private const val WatchdogTickMs = 500L

    /** 外部 UI 存活期间的续租间隔（必须显著小于 [WatchdogTimeoutMs]）。 */
    private const val LeaseRenewIntervalMs = 2_000L

    /** 续租日志的最短间隔：每 2s 一行会把上面的判读日志淹掉。 */
    private const val LeaseLogIntervalMs = 10_000L

    private val handler = Handler(Looper.getMainLooper())

    /** 当前是否处于"为外部 UI 挂起面板窗"的状态（**幂等判据 + 可查询状态**）。 */
    @Volatile
    private var suspended = false

    /** 是谁挂起的（日志里判读用：`composer-add-image` / `edit-image-block` / `宿主自报` …）。 */
    @Volatile
    private var suspendedCaller: String? = null

    @Volatile
    private var suspendedAtMs = 0L

    /** 租约到期时刻（uptime）；外部 UI 每次续租把它往后推 [WatchdogTimeoutMs]。 */
    @Volatile
    private var leaseDeadlineMs = 0L

    @Volatile
    private var lastLeaseLogMs = 0L

    private var watchdogScheduled = false

    /** 看门狗：每 [WatchdogTickMs] 看一眼租约是否过期，过期就恢复面板并打日志（幂等，重复无害）。 */
    private val watchdogTick = object : Runnable {
        override fun run() {
            if (!suspended) {
                watchdogScheduled = false
                return
            }
            val now = SystemClock.uptimeMillis()
            if (now >= leaseDeadlineMs) {
                Log.w(
                    TAG,
                    "看门狗：挂起超过 ${now - suspendedAtMs}ms 仍未恢复（caller=$suspendedCaller）→ 自动恢复",
                )
                resumeAfterExternalUi(caller = "看门狗")
                return
            }
            handler.postDelayed(this, WatchdogTickMs)
        }
    }

    /** 是否处于挂起态（调用方判"面板现在不可交互"用）。 */
    val isSuspended: Boolean get() = suspended

    /**
     * 挂起面板窗（幂等）。[caller] 只进日志：`adb logcat -s StashImageEdit` 里要能一眼看出是谁挂的。
     */
    fun suspendForExternalUi(caller: String) {
        val now = SystemClock.uptimeMillis()
        if (suspended) {
            Log.i(
                TAG,
                "suspendForExternalUi 重复调用（幂等忽略） caller=$caller 已在 caller=$suspendedCaller 挂起 ${now - suspendedAtMs}ms",
            )
            return
        }
        suspended = true
        suspendedCaller = caller
        suspendedAtMs = now
        leaseDeadlineMs = now + WatchdogTimeoutMs
        Log.i(
            TAG,
            "suspendForExternalUi caller=$caller 看门狗=${WatchdogTimeoutMs}ms 宿主钩子=${suspend != null}",
        )
        suspend?.invoke()
        startWatchdog()
    }

    /**
     * 恢复面板窗（幂等）。
     *
     * ⚠️ **只有在真的处于挂起态时才去动窗口**（`resume?.invoke()` = `setDragHidden(false)`）。
     * 为什么不能无条件调：
     * - 正常路径上"回调恢复 + trampoline `onDestroy` 兜底恢复"会连着来两次，第二次本来就不需要动窗口；
     * - 更重要的是**面板已经被收起**（用户/息屏 `dismiss()` → [onHostWindowDismissed]）时，
     *   无条件 `setDragHidden(false)` 会把一个已经 `GONE`/内容已滑出的 MATCH_PARENT 窗重新变成
     *   `VISIBLE + 可触摸` —— 用户看到的是"整屏点不动"（比"面板不见了"更糟）。
     *
     * 状态与窗口标志的同步由三处保证（这也是"可以只看状态决定要不要动窗"的依据）：
     * [suspendForExternalUi] → 隐藏；[onHostWindowResumed] / 本函数 → 显示；
     * [onHostWindowDismissed] / [clear] → 面板本来就不该显示。
     */
    fun resumeAfterExternalUi(caller: String) {
        val wasSuspended = suspended
        val heldMs = if (wasSuspended) SystemClock.uptimeMillis() - suspendedAtMs else 0L
        // ⚠️ 先把这段拼好再进日志：`"…" + if (…) "…" else "…" + "…"` 里 `else` 分支会把后面的 `+` 一起吃掉，
        // 结果"宿主钩子=…"在挂起过的那些行里会整段消失（判读日志最忌讳这种"某些行少了字段"）。
        val stateTail = if (wasSuspended) " 挂起时长=${heldMs}ms" else "（幂等空转，不动窗口）"
        suspended = false
        suspendedCaller = null
        leaseDeadlineMs = 0L
        stopWatchdog()
        Log.i(
            TAG,
            "resumeAfterExternalUi 调用点=$caller 之前挂起=$wasSuspended$stateTail 宿主钩子=${resume != null}",
        )
        if (wasSuspended) resume?.invoke()
    }

    /**
     * **外部 UI 还活着**的续租（trampoline 每 [LeaseRenewIntervalMs] 调一次）。
     *
     * 这就是"看门狗不会误伤正常的长编辑"的全部依据：用户在编辑器里涂鸦十分钟，trampoline 一直
     * 在后台（paused）续租，看门狗永远不会到点；反过来 trampoline 一旦销毁/结果丢了，续租停止，
     * 最多 [WatchdogTimeoutMs] 之后面板自己回来。
     */
    fun renewExternalUiLease(owner: String) {
        if (!suspended) return
        val now = SystemClock.uptimeMillis()
        leaseDeadlineMs = now + WatchdogTimeoutMs
        if (now - lastLeaseLogMs >= LeaseLogIntervalMs) {
            lastLeaseLogMs = now
            Log.i(TAG, "外部 UI 租约续期中 owner=$owner 已挂起 ${now - suspendedAtMs}ms")
        }
    }

    /**
     * 面板窗**自己**报告"我已经被挂起"（宿主 `suspendForExternalUi` 里调）。
     *
     * 正常路径上状态已经由 [suspendForExternalUi] 置好了，这里只兜"有人绕过本对象直接叫宿主"的情况。
     */
    fun onHostWindowSuspended() {
        if (suspended) return
        val now = SystemClock.uptimeMillis()
        Log.i(TAG, "suspendForExternalUi caller=宿主自报（面板窗被挂起）")
        suspended = true
        suspendedCaller = "宿主自报"
        suspendedAtMs = now
        leaseDeadlineMs = now + WatchdogTimeoutMs
        startWatchdog()
    }

    /**
     * 面板窗**自己**报告"我已经恢复可见"（宿主 `resumeAfterExternalUi` 里调）。
     *
     * 为什么必须有它：`FloatBallStashPanel.show` 里有句 `sideHost.resumeAfterExternalUi()`（用户重新呼出
     * 面板时的兜底），它**不经过本对象**。没有这条自报，陈旧的 `suspended=true` 会拦住用户下一次 ✎。
     */
    fun onHostWindowResumed() {
        if (!suspended) return
        Log.i(
            TAG,
            "resumeAfterExternalUi 调用点=面板重新呼出（宿主自报） 之前挂起时长=${SystemClock.uptimeMillis() - suspendedAtMs}ms",
        )
        suspended = false
        suspendedCaller = null
        leaseDeadlineMs = 0L
        stopWatchdog()
    }

    /**
     * 面板窗**被收起**（用户主动关 / `dismiss()`，含息屏那条路）时调用。
     *
     * 为什么必须有它：收起之后"外部 UI 回来要恢复面板"这件事已经**不再成立** —— 恢复动作要是
     * 还把窗口从 `GONE` 点亮成 `VISIBLE + 可触摸`（内容却已经滑出去了），用户看到的就是
     * "整屏点不动"。所以这里先把挂起态作废（连带撤销看门狗），随后的 resume 只会打一行幂等日志。
     *
     * ⚠️ 不动 [suspend] / [resume] 两个钩子：面板下次 `show` 会重新注册，留着也不会有副作用。
     */
    fun onHostWindowDismissed() {
        if (!suspended) return
        Log.i(
            TAG,
            "resumeAfterExternalUi 调用点=面板被收起（宿主自报） 之前挂起时长=${SystemClock.uptimeMillis() - suspendedAtMs}ms → 作废挂起态",
        )
        suspended = false
        suspendedCaller = null
        leaseDeadlineMs = 0L
        stopWatchdog()
    }

    /** 面板窗被摘掉 / 销毁时清空（不残留指向已失效窗口的动作），状态与看门狗一并复位。 */
    fun clear() {
        Log.i(
            TAG,
            "clear 面板窗已摘除：清空挂起/恢复钩子（之前挂起=$suspended caller=$suspendedCaller）",
        )
        suspend = null
        resume = null
        suspended = false
        suspendedCaller = null
        leaseDeadlineMs = 0L
        stopWatchdog()
    }

    private fun startWatchdog() {
        if (watchdogScheduled) return
        watchdogScheduled = true
        handler.removeCallbacks(watchdogTick)
        handler.postDelayed(watchdogTick, WatchdogTickMs)
    }

    private fun stopWatchdog() {
        watchdogScheduled = false
        handler.removeCallbacks(watchdogTick)
    }
}
