package com.slideindex.app.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「录音」会话的**共享状态**（前台服务与 UI 双方都读它）。
 *
 * 与 [StashVoiceSession]（语音识别）是**两条独立的状态机**，刻意不合并：
 * - 识别那条的产物是"一段文字"，录完就没了；
 * - 录音这条的产物是"一个文件 + 时长"，要作为**内容块**落进条目里，还得能播。
 *
 * 生命周期：`Idle → Recording →（finishedPath/finishedDurationMs 或 errorResId）→ Idle`。
 * **取走结果的那一方负责调 [consumeResult]**（否则别的输入面会重复插同一个块）。
 *
 * ⚠️ 为什么不用回调：服务是前台服务、UI 在 overlay 的 Compose 里且随时可能重组
 * （系统会把整个 overlay 窗摘掉重建），共享 Compose 状态是这套 overlay 里通行的做法
 * （同类：`StashVoiceSession` / `HistorySaveSignal` / `HistoryPanelReveal`）。
 */
object StashAudioRecordSession {
    enum class State { Idle, Recording, Error }

    var state by mutableStateOf(State.Idle)
        private set

    /** 本次录音的起始时刻（UI 用它算"⏺ 0:07"的计时，不靠服务推送）。 */
    var startedAtMs by mutableStateOf(0L)
        private set

    /** 录完时的时长（毫秒）；[State.Recording] 期间无意义。 */
    var finishedDurationMs by mutableStateOf(0L)
        private set

    /** 录完的音频文件**绝对路径**（还没落进闪念，仍在 cache 里），等输入面取走。 */
    var finishedPath by mutableStateOf("")
        private set

    /** 失败时的文案资源 id（0 = 没有错误）。 */
    var errorResId by mutableIntStateOf(0)
        private set

    /** 每次会话 +1：输入面用它区分"这是不是我这轮的结果"。 */
    var sessionId by mutableIntStateOf(0)
        private set

    fun beginSession(nowMs: Long) {
        sessionId++
        startedAtMs = nowMs
        finishedPath = ""
        finishedDurationMs = 0L
        errorResId = 0
        state = State.Recording
    }

    fun finishWithResult(path: String, durationMs: Long) {
        finishedPath = path
        finishedDurationMs = durationMs.coerceAtLeast(0L)
        errorResId = 0
        state = State.Idle
    }

    fun finishWithError(messageResId: Int) {
        errorResId = messageResId
        finishedPath = ""
        finishedDurationMs = 0L
        state = State.Error
    }

    /** 会话被取消 / 服务销毁：不留任何结果。 */
    fun cancelSession() {
        finishedPath = ""
        finishedDurationMs = 0L
        errorResId = 0
        state = State.Idle
    }

    /** 结果已经被某个输入面取走。 */
    fun consumeResult() {
        finishedPath = ""
        finishedDurationMs = 0L
        errorResId = 0
        if (state == State.Error) state = State.Idle
    }
}

/** 录音时长上限：5 分钟（约 2.4MB @ 64kbps，见 `StashVoiceInputService` 的编码参数）。 */
const val StashAudioMaxDurationMs: Long = 5L * 60L * 1000L
