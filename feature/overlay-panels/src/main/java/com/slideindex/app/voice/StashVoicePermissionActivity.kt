package com.slideindex.app.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.slideindex.app.util.finishWithoutTransition

/**
 * 录音权限的瞬发跳板。
 *
 * overlay 窗**不能**弹运行时权限（权限对话框只能由 Activity 发起），所以两个输入面
 * （语音识别 / 录音）第一次点麦克风都是走这里。
 *
 * 两条用法（**不要合并**，语义不一样）：
 * - [ensureGranted]（语音识别的老路）：已授权时什么都不做；未授权时拉系统对话框，
 *   **授权后直接开始听** —— 老注释原话是"这样第一次点麦克风也是一步到位，不用点两次"；
 * - [launchForResult]（§0.16.21 录音用）：授权/拒绝**都要回调**，因为拒绝之后必须给
 *   可见提示并跳系统设置（不能静默失败），而"授权后干什么"由调用方决定（这里是开始录音）。
 */
class StashVoicePermissionActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        val callback = pendingResult
        pendingResult = null
        if (callback != null) {
            callback(granted)
        } else if (granted) {
            // 老路（语音识别）：没有回调 = 谁调 [ensureGranted] 谁要"授权后直接开始听"。
            StashVoiceController.start(applicationContext)
        }
        finishWithoutTransition()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (StashVoiceController.hasRecordAudioPermission(this)) {
            // 走到这里说明"检查那一刻还没有权限、拉起跳板之前恰好被别的路径授予了"。
            val callback = pendingResult
            pendingResult = null
            if (callback != null) {
                callback(true)
            } else {
                StashVoiceController.start(applicationContext)
            }
            finishWithoutTransition()
            return
        }
        requestPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        /**
         * 带结果的请求入口（§0.16.21）：**授权与拒绝都会回调**，且只回调一次。
         *
         * ⚠️ 回调是**进程内**静态量（与 `FilePermissionTrampolineActivity.onPermissionResult`
         * 同一套做法）：本 Activity 与调用方（overlay 面板）在同一个进程里，
         * 用 `TrampolineResultPort` 那套跨进程广播在这里是多余的。
         */
        @Volatile
        private var pendingResult: ((Boolean) -> Unit)? = null

        /** 权限确实缺失时才跳；已授权直接返回 true（不产生任何跳转）。 */
        fun ensureGranted(context: Context): Boolean {
            if (StashVoiceController.hasRecordAudioPermission(context)) return true
            val intent = Intent(context, StashVoicePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching { context.startActivity(intent) }.isSuccess
        }

        /**
         * 拉系统权限对话框，结果回调 [onResult]（true = 已授权）。
         *
         * @return 是否成功把跳板 Activity 拉起来了；false 时 [onResult] **不会被调用**
         *   （调用方要自己给失败反馈）。
         */
        fun launchForResult(context: Context, onResult: (Boolean) -> Unit): Boolean {
            if (StashVoiceController.hasRecordAudioPermission(context)) {
                onResult(true)
                return true
            }
            pendingResult = onResult
            val intent = Intent(context, StashVoicePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching { context.startActivity(intent) }.isSuccess
            if (!started) pendingResult = null
            return started
        }
    }
}
