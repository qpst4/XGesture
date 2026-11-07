package com.slideindex.app.overlay

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 「冰箱状态变了，浮层里的应用列表该重算了」的极简事件总线。
 *
 * 冻结/解冻只改包的 enabled 状态，`AppRepository.apps`（可启动列表）不一定马上变化，
 * 而应用索引里的冻结项是靠冰箱清单拼出来的——所以冻结操作完成后要主动踢一次重算，
 * 否则索引会停留在旧结果上（真机表现：重冻之后冻结项从索引里消失）。
 */
object OverlayRefreshBus {
    val freezerChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    fun notifyFreezerChanged() {
        freezerChanged.tryEmit(Unit)
    }

    /**
     * 分身空间变化（其他 user 下装/卸包、克隆空间增删）。
     *
     * 与冻结共用同一条「重算索引」链路：`freezerChanged` 的消费者会
     * `reloadApps()` + `refreshIndexApps()`，正是分身条目需要的那两步。
     * 单独开函数只为语义清楚，避免以后有人以为这里只管冰箱。
     */
    fun notifyCloneSpaceChanged() {
        freezerChanged.tryEmit(Unit)
    }
}
