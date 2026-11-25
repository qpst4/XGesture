package com.slideindex.app.overlay.quickwheel

import android.content.Context

/**
 * 「快捷轮盘 · 上方提示」开关。
 *
 * 反馈 #380：轮盘呼出时屏幕上边缘会固定显示一条"此刻松手会发生什么"的动作提示条（HUD），
 * 配置页编辑层上方也有一条操作提示胶囊；部分用户觉得挡视线，要求可关闭。
 *
 * 这里用一个全局开关同时管这两处"上方提示"：它们都是同一个诉求（上方提示），
 * 分成两个开关只会让设置页更碎。默认开启，保持历史观感。
 *
 * 用独立 SharedPreferences 而不是进 [com.slideindex.app.settings.AppSettings]：
 * 它只影响绘制，不参与手势执行、不需要按应用分档 / 归档到备份，和放大镜、页面滚动
 * 那几个同级别偏好保持一致。
 */
object QuickWheelHintPrefs {
    const val PREFS_NAME = "quick_wheel_hint"
    const val KEY_ENABLED = "enabled"

    /** 默认开启（历史行为）。 */
    fun isEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }.getOrDefault(true)

    fun setEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}
