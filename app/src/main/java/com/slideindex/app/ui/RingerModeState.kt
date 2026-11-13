package com.slideindex.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.slideindex.app.gesture.RingerMode

/** 读取系统当前铃声模式；拿不到时按“响铃”处理（与 [AudioManager] 默认一致）。 */
fun readSystemRingerMode(context: Context): Int =
    (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode
        ?: AudioManager.RINGER_MODE_NORMAL

/** `AudioManager.RINGER_MODE_*` → 动作层的 [RingerMode]。 */
fun Int.toRingerMode(): RingerMode = when (this) {
    AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
    AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
    else -> RingerMode.RING
}

/**
 * 系统当前铃声模式（响铃 / 振动 / 静音），随系统变化实时更新。
 *
 * 只给 **Compose 的动作选择页** 用：让「声音模式 · 切换」的图标跟着手机实际状态走。
 * 面板 / 轮盘是 Canvas + 位图缓存，暂时仍用静态图标（缓存 key 没有状态维度）。
 *
 * 广播是 `AudioManager.RINGER_MODE_CHANGED_ACTION`，动态注册、不需要权限；
 * 快捷工具的声音磁贴早在用它（`OhoQuickToolsPanelState`）。
 */
@Composable
fun rememberSystemRingerMode(): Int {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(readSystemRingerMode(context)) }
    DisposableEffect(context) {
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    mode = readSystemRingerMode(appContext)
                }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }
    return mode
}
