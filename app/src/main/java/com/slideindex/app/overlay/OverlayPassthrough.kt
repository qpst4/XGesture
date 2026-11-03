package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import com.slideindex.app.util.InputTapUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * fv-style passthrough: detach trigger overlays → inject tap → restore.
 */
object OverlayPassthrough {
    private const val TAG = "OverlayPassthrough"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val echoGuard = PassthroughEchoGuard()

    /** 触发器恢复后再留一小段给注入事件落地，然后解除回声窗口（见 [PassthroughEchoGuard]）。 */
    private val disarmRunnable = Runnable { echoGuard.disarm() }

    fun run(
        hideTriggers: () -> Unit,
        showTriggers: () -> Unit,
        rawX: Float,
        rawY: Float,
        onComplete: () -> Unit,
        framesBeforeInject: Int = DEFAULT_FRAMES_BEFORE_INJECT,
        restoreDelayMs: Long = DEFAULT_RESTORE_DELAY_MS
    ) {
        // 上一次注入的回声如果又落回触发器，就不要再放行一次，否则隐藏/恢复会自持成抽搐。
        if (echoGuard.isEcho(rawX, rawY)) {
            Log.i(TAG, "passthrough echo suppressed at ($rawX, $rawY)")
            onComplete()
            return
        }
        // 事务从「隐藏触发器」这一拍就开始：窗口真正从输入派发里消失之前落到同一点的点击
        // 也必须拦住，否则会并行出两条放行事务、注入两次点击。
        mainHandler.removeCallbacks(disarmRunnable)
        echoGuard.arm(rawX, rawY)
        hideTriggers()
        val restored = AtomicBoolean(false)
        var safetyRestore: Runnable? = null

        val safeRestore = {
            if (restored.compareAndSet(false, true)) {
                safetyRestore?.let { mainHandler.removeCallbacks(it) }
                runCatching { showTriggers() }
                    .onFailure { Log.e(TAG, "showTriggers failed during passthrough restore", it) }
                // 触发器已恢复：再给注入事件留一小段落地宽限就彻底解除。
                // 旧实现是「注入后固定 400ms」，会把用户紧接着的第二次点击也当成回声吞掉，
                // 真机表现就是「点一下没反应，再点还是没反应」。
                mainHandler.postDelayed(disarmRunnable, ECHO_GRACE_MS)
                onComplete()
            }
        }

        val safety = Runnable {
            Log.w(TAG, "Passthrough safety restore at ($rawX, $rawY)")
            safeRestore()
        }
        safetyRestore = safety
        mainHandler.postDelayed(safety, SAFETY_RESTORE_MS)

        val scheduleInject = {
            runAfterNextFrames(frames = framesBeforeInject) {
                // 安全恢复已经收尾（主线程卡顿超过 2s）：不再补一次没人守着的注入。
                if (restored.get()) return@runAfterNextFrames
                try {
                    // 先盖章再注入：模块桥看到「注入窗口」应放行，注入事件才到得了目标应用；
                    // 万一还是被转发回来，[ModuleForwardedTouchGate.isInjectedEcho] 会在 app 侧丢掉它。
                    ModuleForwardedTouchGate.markInjected(rawX, rawY)
                    val injectStartMs = SystemClock.elapsedRealtime()
                    com.slideindex.app.diagnostic.EdgeDiag.log(
                        "passthrough",
                        "注入点击 at=($rawX,$rawY) frames=$framesBeforeInject " +
                            "restoreDelay=$restoreDelayMs armedAfter=$injectStartMs"
                    )
                    InputTapUtil.dispatchTapAsync(rawX, rawY, onFinished = { ok ->
                        com.slideindex.app.diagnostic.EdgeDiag.log(
                            "passthrough",
                            "注入回调 ok=$ok at=($rawX,$rawY) " +
                                "cost=${SystemClock.elapsedRealtime() - injectStartMs}ms"
                        )
                        if (!ok) {
                            Log.w(TAG, "dispatchTapAsync failed at ($rawX, $rawY)")
                        }
                        if (restoreDelayMs <= 0L) {
                            mainHandler.post(safeRestore)
                        } else {
                            mainHandler.postDelayed(safeRestore, restoreDelayMs)
                        }
                    })
                } catch (e: Throwable) {
                    Log.e(TAG, "InputTapUtil.dispatchTapAsync failed during passthrough", e)
                    safeRestore()
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            scheduleInject()
        } else {
            mainHandler.post(scheduleInject)
        }
    }

    private fun runAfterNextFrames(frames: Int, action: () -> Unit) {
        if (frames <= 0) {
            action()
            return
        }
        Choreographer.getInstance().postFrameCallback {
            runAfterNextFrames(frames - 1, action)
        }
    }

    private const val DEFAULT_FRAMES_BEFORE_INJECT = 5
    private const val DEFAULT_RESTORE_DELAY_MS = 120L
    private const val SAFETY_RESTORE_MS = 2000L

    /** 触发器恢复后保留回声窗口的落地宽限：只覆盖注入事件的投递抖动，不吞用户的新点击。 */
    private const val ECHO_GRACE_MS = 48L
}
