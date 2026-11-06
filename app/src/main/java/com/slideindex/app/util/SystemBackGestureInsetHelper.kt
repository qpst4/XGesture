package com.slideindex.app.util

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SystemBackGestureInsetHelper {
    const val KEY_LEFT = "back_gesture_inset_scale_left"
    const val KEY_RIGHT = "back_gesture_inset_scale_right"
    const val DEFAULT_SCALE = 1f
    const val MIN_SCALE = 0f
    const val MAX_SCALE = 1f

    fun readLeftScale(context: Context): Float = readScale(context, KEY_LEFT)

    fun readRightScale(context: Context): Float = readScale(context, KEY_RIGHT)

    private fun readScale(context: Context, key: String): Float =
        Settings.Secure.getFloat(context.contentResolver, key, DEFAULT_SCALE)
            .coerceIn(MIN_SCALE, MAX_SCALE)

    suspend fun writeLeftScale(context: Context, value: Float): Boolean =
        writeScale(context, KEY_LEFT, value)

    suspend fun writeRightScale(context: Context, value: Float): Boolean =
        writeScale(context, KEY_RIGHT, value)

    suspend fun resetDefaults(context: Context): Boolean {
        val leftOk = writeScale(context, KEY_LEFT, DEFAULT_SCALE)
        val rightOk = writeScale(context, KEY_RIGHT, DEFAULT_SCALE)
        return leftOk && rightOk
    }

    fun canWrite(context: Context): Boolean =
        SecureSettingsHelper.hasWriteSecureSettings(context) ||
            TaskManagerUtil.hasPrivilegedAccess()

    /**
     * 必须在后台线程判定结果：root 模式下 [TaskManagerUtil.runShellCommandLine] 在主线程上只会把
     * 命令丢给特权线程后立刻返回「主线程跳过特权任务」，调用方拿到的永远是失败 —— 表现为滑条每动
     * 一格就弹 ADB 授权框，而系统设置其实已经被后台写进去了。
     */
    private suspend fun writeScale(context: Context, key: String, value: Float): Boolean =
        withContext(Dispatchers.IO) {
            val clamped = value.coerceIn(MIN_SCALE, MAX_SCALE)
            // 1) 有 WRITE_SECURE_SETTINGS 就直接写：最快，结果也准。少数 ROM 会拒写（返回 false
            //    或抛 SecurityException），这时不能直接放弃，要继续走下面的特权 shell。
            if (SecureSettingsHelper.hasWriteSecureSettings(context) &&
                runCatching {
                    Settings.Secure.putFloat(context.contentResolver, key, clamped)
                }.getOrDefault(false)
            ) {
                return@withContext true
            }
            // 2) 特权 shell：Shizuku 模式用 shell 身份，root 模式内部自动走 root（useRoot 不参与判定）。
            if (!TaskManagerUtil.hasPrivilegedAccess()) return@withContext false
            TaskManagerUtil.runShellCommandLine(
                command = "settings put secure $key $clamped",
                useRoot = false,
            ).success
        }
}
