package com.slideindex.app.pagescroll

import android.content.Context
import android.content.SharedPreferences

/**
 * 「页面滚动」的可调项。
 *
 * 触发方式没有开关：由该手势槽位解析出来的触发模式决定——「持续」走逐页拖动，
 * 其它模式弹出控制条持续滚动。这里只留真正影响执行的项。
 */
data class PageScrollSettings(
    val speedLevel: Int = DEFAULT_SPEED_LEVEL,
    /** 闲置 4 秒后控制条停在不透明度（0 = 完全看不见，1 = 不淡出）。 */
    val controlBarIdleAlpha: Float = DEFAULT_CONTROL_BAR_IDLE_ALPHA,
    val lastSideName: String = "",
) {
    /** 当前档位对应的滚动速度（dp/s）。 */
    val speedDpPerSecond: Float
        get() = SPEED_LEVELS[(speedLevel - 1).coerceIn(0, SPEED_LEVELS.lastIndex)]

    companion object {
        const val DEFAULT_SPEED_LEVEL = 3

        /** 7 档速度（dp/s）。数值是我们的档位设计，取"越往上一档越明显"的等比感觉。 */
        val SPEED_LEVELS = floatArrayOf(25f, 50f, 80f, 160f, 300f, 500f, 800f)

        val MIN_SPEED_LEVEL = 1
        val MAX_SPEED_LEVEL get() = SPEED_LEVELS.size

        /** 闲置淡出的默认值：沿用改之前写死的 55%。 */
        const val DEFAULT_CONTROL_BAR_IDLE_ALPHA = 0.55f
        const val MIN_CONTROL_BAR_IDLE_ALPHA = 0f
        const val MAX_CONTROL_BAR_IDLE_ALPHA = 1f
    }
}

/** 「页面滚动」偏好读写。 */
object PageScrollPrefs {

    private const val PREFS_NAME = "page_scroll"
    private const val KEY_SPEED_LEVEL = "speed_level"
    private const val KEY_BAR_IDLE_ALPHA = "control_bar_idle_alpha"
    private const val KEY_LAST_SIDE = "last_side"
    private const val KEY_BAR_Y_RATIO = "control_bar_y_ratio"

    fun read(context: Context): PageScrollSettings {
        val prefs = prefs(context)
        return PageScrollSettings(
            speedLevel = prefs.getInt(KEY_SPEED_LEVEL, PageScrollSettings.DEFAULT_SPEED_LEVEL)
                .coerceIn(PageScrollSettings.MIN_SPEED_LEVEL, PageScrollSettings.MAX_SPEED_LEVEL),
            controlBarIdleAlpha = prefs.getFloat(
                KEY_BAR_IDLE_ALPHA,
                PageScrollSettings.DEFAULT_CONTROL_BAR_IDLE_ALPHA,
            ).coerceIn(
                PageScrollSettings.MIN_CONTROL_BAR_IDLE_ALPHA,
                PageScrollSettings.MAX_CONTROL_BAR_IDLE_ALPHA,
            ),
            lastSideName = prefs.getString(KEY_LAST_SIDE, "").orEmpty(),
        )
    }

    fun saveSpeedLevel(context: Context, level: Int) {
        prefs(context).edit()
            .putInt(
                KEY_SPEED_LEVEL,
                level.coerceIn(PageScrollSettings.MIN_SPEED_LEVEL, PageScrollSettings.MAX_SPEED_LEVEL),
            )
            .apply()
    }

    fun saveLastSide(context: Context, sideName: String) {
        prefs(context).edit().putString(KEY_LAST_SIDE, sideName).apply()
    }

    /** 控制条竖直位置（按可移动范围的比例，0..1）；-1 表示还没记过。 */
    fun saveBarYRatio(context: Context, ratio: Float) {
        prefs(context).edit().putFloat(KEY_BAR_Y_RATIO, ratio.coerceIn(0f, 1f)).apply()
    }

    fun barYRatio(context: Context): Float = prefs(context).getFloat(KEY_BAR_Y_RATIO, -1f)

    fun saveControlBarIdleAlpha(context: Context, alpha: Float) {
        prefs(context).edit()
            .putFloat(
                KEY_BAR_IDLE_ALPHA,
                alpha.coerceIn(
                    PageScrollSettings.MIN_CONTROL_BAR_IDLE_ALPHA,
                    PageScrollSettings.MAX_CONTROL_BAR_IDLE_ALPHA,
                ),
            )
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
