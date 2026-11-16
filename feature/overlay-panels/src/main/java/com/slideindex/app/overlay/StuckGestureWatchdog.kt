package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper

/**
 * "按住才成立"的手势兜底看门狗。
 *
 * 这类手势只有收到 UP/CANCEL 才会结束。一旦这两个事件丢了（系统抢走手势、
 * 输入层接管被打断、会话重置漏了一次等），承载触摸的浮层窗口会一直停在
 * 可触摸/全屏扩展状态，整屏触摸都被它吃掉。
 *
 * 这里以"最后一次输入时间"兜底：仍处于按住状态却超过 [timeoutMs] 收不到
 * 任何输入事件，就回调 [onStall] 强制收尾。手指真的在动时会不断续期，
 * 因此正常长按/连续调节不会被打断。
 *
 * **代次（token）必须在下发定时器的瞬间快照**：会话是共享的，上一轮会话遗留的
 * 定时器如果在下一轮才触发，读"当前代次"会重新对上，从而把正常手势复位掉
 * （真机日志：按住放大镜不到 2 秒就被复位）。快照代次不一致 = 遗留定时器，直接忽略。
 */
class StuckGestureWatchdog(
    private val timeoutMs: Long,
    private val isHolding: (token: Long) -> Boolean,
    private val onStall: () -> Unit,
    private val tokenProvider: () -> Long,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private var armedToken: Long? = null

    private val runnable = Runnable {
        val token = armedToken ?: return@Runnable
        armedToken = null
        if (isHolding(token)) onStall()
    }

    /** 每次收到输入事件后调用：按住状态自动续期，非按住状态自动撤销。 */
    fun onInput() {
        handler.removeCallbacks(runnable)
        val token = tokenProvider()
        if (isHolding(token)) {
            armedToken = token
            handler.postDelayed(runnable, timeoutMs)
        } else {
            armedToken = null
        }
    }

    /** 按住状态发生变化（开始/结束）时调用。 */
    fun onStateChanged() = onInput()

    fun cancel() {
        armedToken = null
        handler.removeCallbacks(runnable)
    }
}
