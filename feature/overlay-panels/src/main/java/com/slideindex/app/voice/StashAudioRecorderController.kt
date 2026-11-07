package com.slideindex.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import com.slideindex.app.overlay.panels.R

/**
 * 「录音」的统一入口（§0.16.21）。
 *
 * 与 [StashVoiceController]（语音识别）并排，但**不合并**：识别的产物是一段文字，
 * 录音的产物是一个文件（要作为内容块落进条目、还要能播）。
 *
 * 流程：点麦克风 → 没有录音权限就先走**中转 Activity**（[StashVoicePermissionActivity]，
 * overlay 窗里不能弹运行时权限）→ 有权限就起 [StashVoiceInputService]（`microphone` 类型的
 * 前台服务，overlay 在后台拿麦克风必须如此）→ 录完把路径写进 [StashAudioRecordSession]，
 * 由块编辑器取走并插到光标处。
 */
object StashAudioRecorderController {

    fun isRecording(): Boolean = StashAudioRecordSession.state == StashAudioRecordSession.State.Recording

    /** 点一下麦克风：在录就停（保留已录部分），否则开始录。 */
    fun toggle(context: Context) {
        if (isRecording()) stop(context) else start(context)
    }

    fun start(context: Context) {
        val appContext = context.applicationContext
        if (!hasRecordAudioPermission(appContext)) {
            // ⚠️ 这里**必须**走中转 Activity：overlay 的 Compose 树里没有 `ActivityResultRegistryOwner`，
            // `rememberLauncherForActivityResult` 在 overlay 里根本注册不上（与选图同一条理由）。
            val launched = StashVoicePermissionActivity.launchForResult(appContext) { granted ->
                if (granted) {
                    start(appContext)
                } else {
                    // 被拒**不能静默**：一次可见提示 + 跳系统「应用信息」让用户自己开。
                    StashAudioPermissionNotice.showOnce(appContext)
                }
            }
            if (!launched) {
                StashAudioRecordSession.beginSession(System.currentTimeMillis())
                StashAudioRecordSession.finishWithError(R.string.stash_audio_record_failed)
            }
            return
        }
        val result = runCatching {
            startForegroundService(
                appContext,
                Intent(appContext, StashVoiceInputService::class.java)
                    .setAction(StashVoiceInputService.ACTION_RECORD_START),
            )
        }
        if (result.isFailure) {
            Log.w(TAG, "startForegroundService(record) failed", result.exceptionOrNull())
            // 起不来（后台启动受限等）：也要走一次"会话"，否则输入面认领不到这个失败。
            StashAudioRecordSession.beginSession(System.currentTimeMillis())
            StashAudioRecordSession.finishWithError(R.string.stash_audio_record_failed)
        }
    }

    /** 停止录音并**保留已录的那一段**（点麦克风停、以及被抢麦时的自动停，都是这一条）。 */
    fun stop(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            appContext.startService(
                Intent(appContext, StashVoiceInputService::class.java)
                    .setAction(StashVoiceInputService.ACTION_RECORD_STOP),
            )
        }.onFailure { Log.w(TAG, "startService(record stop) failed", it) }
    }

    fun hasRecordAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private const val TAG = "StashAudioRecorder"
}

/**
 * 录音权限**被拒**时的兜底反馈：一次 Toast + 跳系统「应用信息」页。
 *
 * 为什么还要关面板：收纳面板是**无障碍覆盖层**，它比普通 Activity 高一层 ——
 * 不关的话用户跳过去看到的还是面板盖在系统设置上面（点不着）。
 * 这与 `SearchPanelScreen` 跳系统设置前先 `dismissPanel()` 是同一个做法。
 *
 * 「一次」由进程内的 [shownOnce] 保证（进程重启后重置）：同一个 App 运行期里
 * 不会每次点麦克风都把用户甩去设置页。
 */
private object StashAudioPermissionNotice {
    @Volatile
    private var shownOnce = false

    fun showOnce(context: Context) {
        if (shownOnce) return
        shownOnce = true
        val appContext = context.applicationContext
        runCatching {
            Toast.makeText(appContext, R.string.stash_audio_permission_denied, Toast.LENGTH_LONG).show()
        }
        runCatching { com.slideindex.app.overlay.panels.PanelFloatBallHooks.dismissStashPanel?.invoke() }
        runCatching {
            appContext.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", appContext.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
