package com.slideindex.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 几个「实心」动作图标（放大镜 / 实时缩放 / 实时滚动）。
 *
 * 与 [ThinActionIcons] 的 1.5dp 描边风格不同：这三枚是用户指定的 Material 实心图标，
 * 放大镜里放加号 / 斜箭头，实时滚动用四向箭头。填充色固定白色，实际颜色由 `Icon(tint=…)` 决定。
 */
object ActionFilledIcons {

    private val fillBrush = SolidColor(Color.White)

    private const val MAX_MAGNIFIER = "M19.54,20.58L13.26,14.3q-0.75,0.62-1.72,0.97T9.52,15.62" +
        "q-2.56,0-4.34-1.78T3.4,9.5T5.18,5.16T9.52,3.38t4.34,1.78T15.63,9.5" +
        "q0,1.07-0.36,2.05t-0.96,1.7l6.28,6.28l-1.05,1.05Z" +
        "M9.52,14.12q1.93,0 3.27-1.34T14.13,9.5T12.79,6.23T9.52,4.88T6.25,6.23T4.9,9.5" +
        "t1.34,3.27t3.27,1.34Z"

    /** 放大镜 + 加号。 */
    private const val MAGNIFIER_ZOOM_IN = "$MAX_MAGNIFIER" +
        "M8.77,12.19V10.25H6.83V8.75H8.77V6.81h1.5V8.75h1.94v1.5H10.27v1.94H8.77Z"

    /** 放大镜 + 四向箭头（任意方向滚动）。 */
    private const val SCROLL_ANY_DIRECTION = "M12,21.65l-3.9-3.9l1.07-1.07l2.08,2.08V12.75h-6" +
        "l2.08,2.07L6.25,15.9L2.35,12L6.23,8.11L7.3,9.18L5.23,11.25h6.02V5.23L9.17,7.32" +
        "L8.1,6.25L12,2.35l3.9,3.9L14.83,7.32L12.75,5.23v6.02h6L16.67,9.18L17.75,8.1" +
        "l3.9,3.9l-3.9,3.9l-1.07-1.07l2.08-2.08H12.75v6l2.07-2.08l1.08,1.08L12,21.65Z"

    private fun filledIcon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                pathFillType = PathFillType.EvenOdd,
                // 内外两圈靠 evenOdd 挖出镜片，不依赖路径绕向。
                fill = fillBrush,
            )
        }.build()

    val ZoomIn: ImageVector by lazy { filledIcon("ActionZoomIn", MAGNIFIER_ZOOM_IN) }

    /** 放大镜 + 两段折角箭头：与 [ZoomIn] 区分开，表示"捏合缩放"而不是"看大图"。 */
    val ZoomPinch: ImageVector by lazy {
        ImageVector.Builder(
            name = "ActionZoomPinch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(MAX_MAGNIFIER).toNodes(),
                pathFillType = PathFillType.EvenOdd,
                fill = fillBrush,
            )
            // 镜片里手画一对小折角箭头 + 短连线：不缩放整张原图，否则箭头和中间的线会一起变长。
            // 描边 1.2：比镜片细一档，避免小尺寸下糊成一团。
            path(
                stroke = fillBrush,
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 左下折角箭头
                moveTo(7.2f, 11.8f)
                lineTo(9.2f, 11.8f)
                moveTo(7.2f, 11.8f)
                lineTo(7.2f, 9.8f)
                // 右上折角箭头
                moveTo(11.8f, 7.2f)
                lineTo(9.8f, 7.2f)
                moveTo(11.8f, 7.2f)
                lineTo(11.8f, 9.2f)
                // 两个箭头之间的短连线（不到角落）
                moveTo(8.5f, 10.5f)
                lineTo(10.5f, 8.5f)
            }
        }.build()
    }

    val ScrollAnyDirection: ImageVector by lazy {
        filledIcon("ActionScrollAnyDirection", SCROLL_ANY_DIRECTION)
    }
}
