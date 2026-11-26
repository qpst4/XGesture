package com.slideindex.app.overlay.pickresult

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 取词面板「平铺」开关（反馈 #401 / #5）。
 *
 * 默认关闭 = 现在的观感：文字区、图片区各自套一张 16dp 圆角卡片。
 * 打开后：这两张**分段卡片**不再有底色/描边/圆角与左右 16dp 外扩，
 * 内容直接铺满面板；两段之间只留一条 1dp 细线（外层面板材质保持不变）。
 *
 * 用独立 SharedPreferences：只影响取词面板绘制，不参与手势执行 / 按应用分档，
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

/** 组合期读取「平铺」开关（偏好变化在面板下次打开时生效）。 */
@Composable
fun rememberPickResultFlatMode(): Boolean {
    val context = LocalContext.current
    return remember(context) { PickResultFlatPrefs.isEnabled(context) }
}
