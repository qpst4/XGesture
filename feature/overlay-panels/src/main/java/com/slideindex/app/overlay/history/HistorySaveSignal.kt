package com.slideindex.app.overlay.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「刚存下一条」的跨组件信号。
 *
 * 为什么需要它：把手窗与面板窗**是两个 window**（见 `docs/capsule-refactor-p0-findings.md` §1），
 * 存下的动作发生在面板侧，而"把手脉冲"与"存下预览 peek"都发生在把手侧/独立小窗。
 * 这里用一个 Compose 可观察的计数（+ 最近一次正文）把事件带过去 —— 同一进程内的静态量，
 * 与 `StashAccess` / `OverlayDependencyAccess` 是同一套做法。
 *
 * 读这两个属性即参与 Compose 订阅（它们是 Compose 状态，不是普通字段）。
 */
object HistorySaveSignal {
    /** 每次成功存进闪念 +1。 */
    var saveCount by mutableIntStateOf(0)
        private set

    /** 最近一次存下的正文（peek 预览用；保留全文，截断交给渲染侧）。 */
    var lastSavedText by mutableStateOf("")
        private set

    /**
     * 普通回调（不走 Compose 的消费者用）。
     *
     * 把手的脉冲是 Compose 侧（读状态即可），但 peek 小窗是 `HistoryFloatService` 直接管的
     * —— Service 里没有组合上下文，用一个显式监听列表最省事，也不用在 Service 里引 Compose 快照流。
     */
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    fun addListener(listener: (String) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners -= listener
    }

    fun notifySaved(text: String) {
        lastSavedText = text
        saveCount++
        listeners.forEach { listener -> runCatching { listener(text) } }
    }
}
