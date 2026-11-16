package com.slideindex.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
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

    /** 实心、铺满 24×24 的"两段折角箭头"（展开/捏合缩放手势）。 */
    private const val EXPAND_TWO_ARROWS = "M3.5,20.5V13H5v4.96L17.96,5H13V3.5h7.5V11H19V6.04" +
        "L6.04,19H11v1.5H3.5Z"

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
            // 把铺满 24×24 的双折角箭头缩到镜片里（中心 9.5,9.5，约 7 单位宽）。
            group(
                scaleX = 0.42f,
                scaleY = 0.42f,
                translationX = 4.48f,
                translationY = 4.46f,
            ) {
                addPath(
                    pathData = PathParser().parsePathString(EXPAND_TWO_ARROWS).toNodes(),
                    fill = fillBrush,
                )
            }
        }.build()
    }

    val ScrollAnyDirection: ImageVector by lazy {
        filledIcon("ActionScrollAnyDirection", SCROLL_ANY_DIRECTION)
    }
}
