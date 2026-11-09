package com.slideindex.app.voice

import android.content.Context

/**
 * 语音录制端口（`:app` 的 `StashAudioRecorderController` 实现）。
 *
 * 面板只表达"开始/停止/切换录音"，权限申请与前台服务由 `:app` 处理。
 */
interface StashVoiceRecorder {
    fun isRecording(): Boolean
    fun toggle(context: Context)
    fun start(context: Context)
    fun stop(context: Context)
    fun hasRecordAudioPermission(context: Context): Boolean
}

/** 模块内服务定位点；未注入时面板按"没权限/不可用"降级。 */
object StashVoiceRecorderAccess {
    @Volatile
    var recorder: StashVoiceRecorder? = null
}