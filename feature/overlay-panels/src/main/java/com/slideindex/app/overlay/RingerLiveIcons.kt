package com.slideindex.app.overlay

import android.content.Context
import android.media.AudioManager
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.RingerMode

/**
 * 圆环 / 边角轮盘是 Canvas 绘制，图标由各自己的缓存对象解析。
 *
 * 「声音模式 · 切换」的图标要跟系统当前铃声状态走：这里提供两件事——
 * 1. [liveAction]：把 TOGGLE 合成带当前档位的动作，交给 `:app` 的图标渲染；
 * 2. [keyPart]：给位图缓存 key 加状态维度，否则状态切换后仍命中旧图。
 *
 * 显式档位（响铃/振动/静音）原样返回：图标表达动作目标，不跟设备状态走。
 */
internal object RingerLiveIcons {

    fun liveAction(context: Context, action: GestureAction): GestureAction {
        if (action !is GestureAction.ToggleMute || action.ringerMode != RingerMode.TOGGLE) return action
        return GestureAction.ToggleMute(
            when (rawRingerMode(context)) {
                AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
                AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
                else -> RingerMode.RING
            },
        )
    }

    fun keyPart(context: Context, action: GestureAction?): String {
        if (action !is GestureAction.ToggleMute || action.ringerMode != RingerMode.TOGGLE) return "-"
        return rawRingerMode(context).toString()
    }

    private fun rawRingerMode(context: Context): Int =
        (context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.ringerMode
            ?: AudioManager.RINGER_MODE_NORMAL
}
