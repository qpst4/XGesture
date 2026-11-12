package com.slideindex.app.overlay.pickresult

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 「查看图片」按钮图标：按用户给的 SVG 路径原样绘制（24dp viewport，两角展开）。
 *
 * 不用 Material 的近似图标：`OpenInFull` / `Fullscreen` 的笔画与这张都不一样。
 */
val PickResultViewImageIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PickResultViewImage",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser()
            .parsePathString("M5,19V13H7v4h4v2H5Zm12-8V7H13V5h6v6H17Z")
            .toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}
