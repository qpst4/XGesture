package com.slideindex.app.dragpress

/**
 * 触摸打断序列：一旦尝试了 DOWN，必须确保 CANCEL 得到执行，使目标应用完整释放触摸状态。
 */
internal object TouchInterruptionSequence {
    fun run(
        isCurrent: () -> Boolean,
        down: () -> Boolean,
        cancel: () -> Boolean,
        pause: () -> Unit,
    ): Boolean {
        if (!isCurrent()) return false
        var cancelled = false
        val accepted = try {
            down().also { if (it) pause() }
        } finally {
            cancelled = cancel()
        }
        return accepted && cancelled
    }
}
