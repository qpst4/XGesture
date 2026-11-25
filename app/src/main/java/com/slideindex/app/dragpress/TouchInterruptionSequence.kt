/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/TouchInterruptionSequence.kt
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

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
