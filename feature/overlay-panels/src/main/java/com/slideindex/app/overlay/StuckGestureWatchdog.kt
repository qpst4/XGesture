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
 */
class StuckGestureWatchdog(
    private val timeoutMs: Long,
    private val isHolding: () -> Boolean,
    private val onStall: () -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private val runnable = Runnable {
        if (isHolding()) onStall()
    }

    /** 每次收到输入事件后调用：按住状态自动续期，非按住状态自动撤销。 */
    fun onInput() {
        handler.removeCallbacks(runnable)
        if (isHolding()) handler.postDelayed(runnable, timeoutMs)
    }

    /** 按住状态发生变化（开始/结束）时调用。 */
    fun onStateChanged() = onInput()

    fun cancel() {
        handler.removeCallbacks(runnable)
    }
}
