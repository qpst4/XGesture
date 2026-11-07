package com.slideindex.app.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 语音输入会话的**共享状态**（面板窗 / 把手窗 / 服务三方都读它）。
 *
 * 为什么不是回调：会话由前台服务驱动（见 [StashVoiceInputService]），而输入面在
 * Compose 里、随时可能重组；共享 Compose 状态是这套 overlay 里通行的做法
 * （同类：`HistorySaveSignal`、`HistoryPanelReveal`）。
 *
 * 生命周期：`Idle → Listening →（finalText/errorResId）→ Idle`。
 * **取走结果的那一方负责调 [consumeResult]**，否则别的输入面会重复插入同一段文字。
 */
object StashVoiceSession {
    enum class State { Idle, Listening, Error }

    var state by mutableStateOf(State.Idle)
        private set

    /** 还在识别中的临时结果（只用来做"正在听"的反馈，不写进输入框）。 */
    var partialText by mutableStateOf("")
        private set

    /** 最终识别结果，等输入面取走。 */
    var finalText by mutableStateOf("")
        private set

    /** 失败时的文案资源 id（0 = 没有错误）。 */
    var errorResId by mutableIntStateOf(0)
        private set

    /** 每次会话 +1：输入面用它区分"这是不是我这轮的结果"。 */
    var sessionId by mutableIntStateOf(0)
        private set

    fun beginSession() {
        sessionId++
        partialText = ""
        finalText = ""
        errorResId = 0
        state = State.Listening
    }

    fun updatePartial(text: String) {
        partialText = text
    }

    fun finishWithResult(text: String) {
        finalText = text
        partialText = ""
        state = State.Idle
    }

    fun finishWithError(messageResId: Int) {
        errorResId = messageResId
        partialText = ""
        state = State.Error
    }

    /** 会话被取消/服务销毁：不留任何结果。 */
    fun cancelSession() {
        partialText = ""
        finalText = ""
        errorResId = 0
        state = State.Idle
    }

    /** 结果已经被某个输入面取走。 */
    fun consumeResult() {
        finalText = ""
        errorResId = 0
        if (state == State.Error) state = State.Idle
    }
}
