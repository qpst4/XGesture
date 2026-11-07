package com.slideindex.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService

/**
 * 语音输入的统一入口（三个输入面都调这里）。
 *
 * 流程：点麦克风 → 没有录音权限就先拉权限跳板（[StashVoicePermissionActivity]）
 * → 有权限就起 [StashVoiceInputService] 那个前台服务（`microphone` 类型，overlay 在后台
 * 拿麦克风必须是前台服务）→ 识别结果写进 [StashVoiceSession] → 输入面自己取走。
 */
object StashVoiceController {
    fun isListening(): Boolean = StashVoiceSession.state == StashVoiceSession.State.Listening

    /** 点一下麦克风：在听就停，没权限就申请，否则开始听。 */
    fun toggle(context: Context) {
        when {
            isListening() -> stop(context)
            !hasRecordAudioPermission(context) -> {
                StashVoicePermissionActivity.ensureGranted(context)
            }
            else -> start(context)
        }
    }

    fun start(context: Context) {
        val appContext = context.applicationContext
        val result = runCatching {
            startForegroundService(
                appContext,
                Intent(appContext, StashVoiceInputService::class.java)
                    .setAction(StashVoiceInputService.ACTION_START),
            )
        }
        if (result.isFailure) {
            // 起不来（后台启动受限等）：也要走一次"会话"，否则输入面认领不到这个失败。
            StashVoiceSession.beginSession()
            StashVoiceSession.finishWithError(com.slideindex.app.overlay.panels.R.string.stash_voice_error_generic)
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            appContext.startService(
                Intent(appContext, StashVoiceInputService::class.java)
                    .setAction(StashVoiceInputService.ACTION_STOP),
            )
        }
    }

    fun hasRecordAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
}
