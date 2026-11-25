package com.slideindex.app.overlay.pickresult

import android.content.Context

/**
 * 取词面板「平铺」开关（反馈 #401）。
 *
 * 默认关闭 = 现在的观感：面板是一张卡片，文字区 / 图片区各带底板。
 * 打开后：文字区与图片区不再套卡片底色与模糊，整块直接铺开，两段之间只留一条细线。
 *
 * 用独立 SharedPreferences：它只影响取词面板绘制，不参与手势执行 / 按应用分档，
 * 与放大镜、页面滚动那些同级别偏好保持一致。
 */
object PickResultFlatPrefs {
    const val PREFS_NAME = "pick_result_flat"
    const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }.getOrDefault(false)

    fun setEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}
