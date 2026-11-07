package com.slideindex.app.overlay.pickresult

import android.content.Context
import androidx.core.content.edit

/**
 * 取词面板「搜索按钮长按直搜」的窗口形态记忆：全屏 / 小窗。
 *
 * 这是手势的初始项而非独立设置项，所以不进设置页，只跟随面板偏好文件落盘。
 */
object PickResultSearchLaunchPrefs {
    const val PREFS_NAME = PickResultImageSharePrefs.PREFS_NAME
    private const val KEY_LAST_FULLSCREEN = "last_pick_search_launch_fullscreen"

    fun resolveLastFullscreen(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LAST_FULLSCREEN, true)

    fun rememberLastFullscreen(context: Context, fullscreen: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_LAST_FULLSCREEN, fullscreen) }
    }
}
