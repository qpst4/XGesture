package com.slideindex.app.overlay.dragdrop

import android.os.SystemClock

/**
 * 一次系统拖拽的"源"会话。
 *
 * 背景：侧边面板 / 剪贴板浮窗 / 文件浏览器 / 屏幕绘制 / 全局复制起拖时都会把自己藏起来，
 * 过去各自在 `ACTION_DRAG_ENDED` 里**无条件**恢复。于是"拖到恢复"和"拖到目标应用"
 * 对源来说没有任何区别，恢复按钮对它们等于空操作。
 *
 * 这里把"拖拽结束后源怎么办"的决策权收给流转栏：
 * 1. 源在 `startDragAndDrop` **之前** [DragFlowSession.begin] 一次，带上
 *    [DragFlowSource.restore]（原样放回）与 [DragFlowSource.discard]（关掉）；
 * 2. 流转栏在 `ACTION_DRAG_STARTED` 时 [DragFlowSession.claim]；被认领后，
 *    源自己的 drag-end 不再自动恢复；
 * 3. 流转栏按落点调用 `restore()` / `discard()`：
 *    恢复药丸 → restore，取消药丸 / 命中目标 / 被别的应用接收 → discard，
 *    拖拽被取消（没有接收方）→ restore；
 * 4. 任一方的拖拽结束都调 [DragFlowSession.end]，避免残留会话被下一次拖拽误认领。
 *
 * ⚠️ 目前所有宿主都在同一进程，所以进程级单例就够；将来若再把浮层拆进程，
 * 这里必须换成 binder / 广播，不能继续用静态字段。
 */
class DragFlowSource(
    private val onRestore: () -> Unit,
    private val onDiscard: () -> Unit,
) {
    /** 已被流转栏接管：源自己的 drag-end 必须让位。 */
    @Volatile
    var claimed: Boolean = false
        private set

    fun claim() {
        claimed = true
    }

    fun restore() = onRestore()

    fun discard() = onDiscard()
}

object DragFlowSession {

    /**
     * 会话只在一次拖拽期间有效。超过这个时长还没被认领，说明流转栏没开/没收到，
     * 当作陈旧会话丢掉，避免下一次外部拖拽误领上一个源的恢复动作。
     */
    private const val MAX_AGE_MS = 60_000L

    private var current: DragFlowSource? = null
    private var begunAtMs = 0L

    fun begin(source: DragFlowSource) {
        current = source
        begunAtMs = SystemClock.elapsedRealtime()
    }

    /** 流转栏认领；返回 null = 这次拖拽不是本应用注册过的源。 */
    fun claim(): DragFlowSource? {
        val source = current ?: return null
        if (SystemClock.elapsedRealtime() - begunAtMs > MAX_AGE_MS) {
            current = null
            return null
        }
        source.claim()
        return source
    }

    fun end(source: DragFlowSource?) {
        if (source != null && current === source) {
            current = null
        }
    }
}
