package com.slideindex.app.screencontrol

import android.content.Context
import android.content.SharedPreferences

/** 「屏幕控制」面板的尺寸与位置（尺寸单位 dp，位置用可移动范围的比例）。 */
data class ScreenControlGeometry(
    val widthDp: Int = ScreenControlPrefs.DEFAULT_WIDTH_DP,
    val heightDp: Int = ScreenControlPrefs.DEFAULT_HEIGHT_DP,
    /** -1 表示还没记录过，首次打开用默认右边距。 */
    val xRatio: Float = -1f,
    val yRatio: Float = -1f,
)

object ScreenControlPrefs {

    const val MIN_WIDTH_DP = 164
    const val MAX_WIDTH_DP = 264
    const val MIN_HEIGHT_DP = 252
    const val MAX_HEIGHT_DP = 672

    const val DEFAULT_WIDTH_DP = 176
    const val DEFAULT_HEIGHT_DP = 352

    const val PREFS_NAME = "screen_control"
    private const val KEY_VERSION = "geometry_version"
    private const val KEY_WIDTH_DP = "panel_width_dp"
    private const val KEY_HEIGHT_DP = "panel_height_dp"
    private const val KEY_X_RATIO = "panel_x_ratio"
    private const val KEY_Y_RATIO = "panel_y_ratio"

    /** v2 起默认尺寸对齐一墨：176×352dp。旧版本存过 208×460 的设备直接回到新默认。 */
    private const val CURRENT_VERSION = 2

    fun read(context: Context): ScreenControlGeometry {
        val prefs = prefs(context)
        val version = prefs.getInt(KEY_VERSION, 0)
        val xRatio = prefs.getFloat(KEY_X_RATIO, -1f).coerceIn(-1f, 1f)
        val yRatio = prefs.getFloat(KEY_Y_RATIO, -1f).coerceIn(-1f, 1f)
        if (version < CURRENT_VERSION) {
            return ScreenControlGeometry(xRatio = xRatio, yRatio = yRatio)
        }
        return ScreenControlGeometry(
            widthDp = prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP)
                .coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP),
            heightDp = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP)
                .coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
            xRatio = xRatio,
            yRatio = yRatio,
        )
    }

    fun saveGeometry(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        xRatio: Float,
        yRatio: Float,
    ) {
        prefs(context).edit()
            .putInt(KEY_VERSION, CURRENT_VERSION)
            .putInt(KEY_WIDTH_DP, widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP))
            .putInt(KEY_HEIGHT_DP, heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP))
            .putFloat(KEY_X_RATIO, xRatio.coerceIn(0f, 1f))
            .putFloat(KEY_Y_RATIO, yRatio.coerceIn(0f, 1f))
            .apply()
    }

    fun savePosition(context: Context, xRatio: Float, yRatio: Float) {
        prefs(context).edit()
            .putFloat(KEY_X_RATIO, xRatio.coerceIn(0f, 1f))
            .putFloat(KEY_Y_RATIO, yRatio.coerceIn(0f, 1f))
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
