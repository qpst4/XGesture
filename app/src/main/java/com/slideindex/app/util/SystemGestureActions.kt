package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import com.slideindex.app.privilege.PrivilegeGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SystemGestureActions {
    fun isMuted(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    fun toggleMute(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val nextMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_SILENT
        }
        return runCatching {
            audioManager.ringerMode = nextMode
            true
        }.getOrDefault(false)
    }

    /** Sets ringer to silent without affecting media volume. */
    fun silenceRinger(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return true
        return runCatching {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            true
        }.getOrDefault(false)
    }

    /** Silences ringer/notification alerts and sets media volume to 0. Alarm volume is unchanged. */
    fun muteAllVolumes(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        silenceRinger(context)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.MEDIA, 0f)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.RING, 0f)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.NOTIFICATION, 0f)
        return true
    }

    /**
     * 音量增加一级：跟随「最相关的流」，与按硬件音量键一致——通话中调通话、放歌时调媒体。
     *
     * 原实现写死 `STREAM_MUSIC`，通话中按音量手势只动媒体流（issue #44）。
     */
    fun volumeUp(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    /** 音量减小一级；同样跟随「最相关的流」（issue #44）。 */
    fun volumeDown(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    fun dispatchMediaKey(context: Context, keyCode: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        }.getOrDefault(false)
    }

    fun openNotificationPolicySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    /** 打开原生网络连接面板（Android 10+ Panel，低版本跳转设置）。 */
    fun openNativeInternetPanel(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * 弹出系统音量条（与音量键同款），但不改变音量。
     *
     * `adjustVolume(ADJUST_SAME, FLAG_SHOW_UI)` 的语义是"挑最相关的 stream、只弹 UI"，所以
     * 通话中弹通话条、放歌时弹媒体条，和按音量键一致。少数 ROM 会把 SAME 当 no-op 连 UI
     * 一起吞掉——这种情况没有可探测的返回值，只能靠真机反馈；异常路径回落到设置面板。
     */
    fun openNativeVolumeHud(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return openNativeVolumePanel(context)
        val shown = runCatching {
            audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            true
        }.getOrDefault(false)
        return shown || openNativeVolumePanel(context)
    }

    /** 调出系统原生声音调节面板（Settings.Panel.ACTION_VOLUME）。 */
    fun openNativeVolumePanel(context: Context): Boolean {
        val panelIntent = Intent(Settings.Panel.ACTION_VOLUME).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(panelIntent)
            true
        }.getOrDefault(false)
        if (started) return true

        // Fallback: 调出音量条或声音设置
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val adjusted = runCatching {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                true
            }.getOrDefault(false)
            if (adjusted) return true
        }

        return runCatching {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 打开当前前台应用系统详情页。 */
    fun openCurrentAppInfo(context: Context): Boolean {
        val foregroundPkg = com.slideindex.app.service.SlideIndexAccessibilityService.currentForegroundPackageName()
        if (foregroundPkg.isNullOrBlank()) {
            android.widget.Toast.makeText(context, com.slideindex.app.R.string.current_app_info_not_found, android.widget.Toast.LENGTH_SHORT).show()
            return false
        }
        val safePkg: String = foregroundPkg
        return runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", safePkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 模拟按键事件（支持长按）。支持 Root 或 Shizuku。 */
    fun simulateKeyEvent(context: Context, keyCode: Int, isLongPress: Boolean = false): Boolean {
        val cmd = if (isLongPress) {
            "input keyevent --longpress $keyCode"
        } else {
            "input keyevent $keyCode"
        }
        CoroutineScope(Dispatchers.IO).launch {
            val useRoot = PrivilegeGateway.isRootMode()
            val result = TaskManagerUtil.runShellCommandLine(cmd, useRoot = useRoot)
            if (!result.success) {
                val errorMsg = result.output.ifBlank { "ExitCode ${result.exitCode}" }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(com.slideindex.app.R.string.key_event_failed_hint, errorMsg),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        return true
    }
}
