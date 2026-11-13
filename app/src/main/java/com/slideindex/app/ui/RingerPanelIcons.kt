package com.slideindex.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 快速工具面板专用的「声音模式」图标（空心加粗版）。
 *
 * 面板旁边的图标是 Material Filled（约 2dp），动作图标那套 weight=300 的细线放进去偏轻。
 * 这里保持 **空心（FILL=0）**，只把字重实例化到 **wght=400**（约 2.0 线宽），
 * 振动波也用 2.0 描边，和面板的笔画重量对齐。
 *
 * 与 [MaterialSymbolThinIcons] 的关系：字形同源，只是字重不同；
 * 动作列表、快速启动器、圆环、边角仍用 300 那套细线。
 */
object RingerPanelIcons {

    private const val NOTIFICATIONS_PATH =
        "M4 19V17H6V10Q6 7.93 7.25 6.31Q8.5 4.7 10.5 4.2V3.5Q10.5 2.88 10.94 2.44Q11.38 2 12 2Q12.62 2 13.06 " +
            "2.44Q13.5 2.88 13.5 3.5V4.2Q15.5 4.7 16.75 6.31Q18 7.93 18 10V17H20V19ZM12 22Q11.18 22 10.59 " +
            "21.41Q10 20.82 10 20H14Q14 20.82 13.41 21.41Q12.83 22 12 22ZM8 17H16V10Q16 8.35 14.82 7.17Q13.65 " +
            "6 12 6Q10.35 6 9.18 7.17Q8 8.35 8 10Z"

    private const val NOTIFICATIONS_OFF_PATH =
        "M4 19V17H6V10Q6 9.17 6.21 8.38Q6.43 7.57 6.85 6.85L8.35 8.35Q8.18 8.75 8.09 9.16Q8 9.57 8 " +
            "10V17H14.2L1.4 4.2L2.8 2.8L21.2 21.2L19.8 22.6L16.15 19ZM18 15.15 16 13.15V10Q16 8.35 14.82 " +
            "7.17Q13.65 6 12 6Q11.35 6 10.75 6.2Q10.15 6.4 9.65 6.8L8.2 5.35Q8.7 4.95 9.28 4.65Q9.85 4.35 10.5 " +
            "4.2V3.5Q10.5 2.88 10.94 2.44Q11.38 2 12 2Q12.62 2 13.06 2.44Q13.5 2.88 13.5 3.5V4.2Q15.5 4.7 16.75 " +
            "6.31Q18 7.93 18 10ZM12 22Q11.18 22 10.59 21.41Q10 20.82 10 20H14Q14 20.82 13.41 21.41Q12.83 " +
            "22 12 22Z"

    /** `notifications_off` 缩到 0.82 并居中，给两侧振动波让位。 */
    private const val NOTIFICATIONS_OFF_SCALED_PATH =
        "M5.85 17.45V15.81H7.49V10.07Q7.49 9.39 7.66 8.74Q7.84 8.08 8.19 7.49L9.42 8.72Q9.27 9.04 9.2 " +
            "9.38Q9.13 9.72 9.13 10.07V15.81H14.21L3.72 5.31L4.87 4.17L19.95 19.25L18.81 20.4L15.81 17.45ZM17.33 " +
            "14.29 15.69 12.65V10.07Q15.69 8.72 14.73 7.75Q13.76 6.79 12.41 6.79Q11.88 6.79 11.39 6.95Q10.89 " +
            "7.12 10.48 7.45L9.29 6.26Q9.7 5.93 10.18 5.68Q10.65 5.44 11.18 5.31V4.74Q11.18 4.23 11.54 3.87Q11.9 " +
            "3.51 12.41 3.51Q12.92 3.51 13.28 3.87Q13.64 4.23 13.64 4.74V5.31Q15.28 5.72 16.3 7.05Q17.33 8.37 " +
            "17.33 10.07ZM12.41 19.91Q11.73 19.91 11.25 19.43Q10.77 18.95 10.77 18.27H14.05Q14.05 18.95 13.57 " +
            "19.43Q13.09 19.91 12.41 19.91Z"

    private const val VIBRATE_WAVE_LEFT = "M3.0 7.5L2.2 9.5L3.0 11.5L2.2 13.5L3.0 15.5L2.2 17.5"
    private const val VIBRATE_WAVE_RIGHT = "M21.0 7.5L21.8 9.5L21.0 11.5L21.8 13.5L21.0 15.5L21.8 17.5"

    private fun hollow(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.White),
            )
        }.build()

    /** 响铃：空心铃铛（wght 400）。 */
    val Ringer: ImageVector by lazy { hollow("RingerPanelRinger", NOTIFICATIONS_PATH) }

    /** 静音：空心铃铛 + 斜杠。 */
    val Silent: ImageVector by lazy { hollow("RingerPanelSilent", NOTIFICATIONS_OFF_PATH) }

    /** 振动：空心铃铛 + 斜杠 + 两侧 2.0 描边波。 */
    val Vibrate: ImageVector by lazy {
        ImageVector.Builder(
            name = "RingerPanelVibrate",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(NOTIFICATIONS_OFF_SCALED_PATH).toNodes(),
                fill = SolidColor(Color.White),
            )
            addPath(
                pathData = PathParser().parsePathString(VIBRATE_WAVE_LEFT).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            addPath(
                pathData = PathParser().parsePathString(VIBRATE_WAVE_RIGHT).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
}
