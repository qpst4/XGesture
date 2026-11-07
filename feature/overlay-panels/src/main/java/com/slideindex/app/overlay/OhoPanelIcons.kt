package com.slideindex.app.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Bold filled glyphs for the OHO+ quick-tools panel (24dp viewport, high visual weight). */
object OhoPanelIcons {
    /** Rounded camera body + center play cutout + right lens (OHO+ screen record). */
    val ScreenRecord: ImageVector by lazy {
        ImageVector.Builder(
            name = "OhoScreenRecord",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // Rounded-square camera body.
                moveTo(6.8f, 6.5f)
                horizontalLineTo(12.8f)
                curveTo(14.2f, 6.5f, 15.3f, 7.6f, 15.3f, 9f)
                verticalLineTo(15f)
                curveTo(15.3f, 16.4f, 14.2f, 17.5f, 12.8f, 17.5f)
                horizontalLineTo(6.8f)
                curveTo(5.4f, 17.5f, 4.3f, 16.4f, 4.3f, 15f)
                verticalLineTo(9f)
                curveTo(4.3f, 7.6f, 5.4f, 6.5f, 6.8f, 6.5f)
                close()
                // Right lens trapezoid.
                moveTo(15.3f, 9.2f)
                lineTo(19.2f, 7.8f)
                lineTo(19.2f, 16.2f)
                lineTo(15.3f, 14.8f)
                close()
                // Center play triangle (even-odd hole).
                moveTo(8f, 9.8f)
                lineTo(11.4f, 12f)
                lineTo(8f, 14.2f)
                close()
            }
        }.build()
    }

    val NotificationShade: ImageVector by lazy {
        ImageVector.Builder(
            name = "OhoNotificationShade",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 5.5f)
                horizontalLineTo(21f)
                curveTo(21.8f, 5.5f, 22.5f, 6.2f, 22.5f, 7f)
                verticalLineTo(10f)
                curveTo(22.5f, 10.8f, 21.8f, 11.5f, 21f, 11.5f)
                horizontalLineTo(3f)
                curveTo(2.2f, 11.5f, 1.5f, 10.8f, 1.5f, 10f)
                verticalLineTo(7f)
                curveTo(1.5f, 6.2f, 2.2f, 5.5f, 3f, 5.5f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 13f)
                lineTo(14.8f, 15.8f)
                curveTo(15.4f, 16.4f, 15.4f, 17.3f, 14.8f, 17.9f)
                curveTo(14.2f, 18.5f, 13.3f, 18.5f, 12.7f, 17.9f)
                lineTo(12f, 17.2f)
                lineTo(11.3f, 17.9f)
                curveTo(10.7f, 18.5f, 9.8f, 18.5f, 9.2f, 17.9f)
                curveTo(8.6f, 17.3f, 8.6f, 16.4f, 9.2f, 15.8f)
                lineTo(12f, 13f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 16.2f)
                lineTo(14.8f, 19f)
                curveTo(15.4f, 19.6f, 15.4f, 20.5f, 14.8f, 21.1f)
                curveTo(14.2f, 21.7f, 13.3f, 21.7f, 12.7f, 21.1f)
                lineTo(12f, 20.4f)
                lineTo(11.3f, 21.1f)
                curveTo(10.7f, 21.7f, 9.8f, 21.7f, 9.2f, 21.1f)
                curveTo(8.6f, 20.5f, 8.6f, 19.6f, 9.2f, 19f)
                lineTo(12f, 16.2f)
                close()
            }
        }.build()
    }

    /** 3×4 tool-tile grid matching the quick-tools floating panel layout. */
    val QuickToolsGrid: ImageVector by lazy {
        ImageVector.Builder(
            name = "OhoQuickToolsGrid",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                quickToolsDot(7.5f, 6.5f)
                quickToolsDot(12f, 6.5f)
                quickToolsDot(16.5f, 6.5f)
                quickToolsDot(7.5f, 10.5f)
                quickToolsDot(12f, 10.5f)
                quickToolsDot(16.5f, 10.5f)
                quickToolsDot(7.5f, 14.5f)
                quickToolsDot(12f, 14.5f)
                quickToolsDot(16.5f, 14.5f)
                quickToolsDot(7.5f, 18.5f)
                quickToolsDot(12f, 18.5f)
                quickToolsDot(16.5f, 18.5f)
            }
        }.build()
    }

    /** Filled speaker icon for media card volume indicator. */
    val VolumeUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "OhoVolumeUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                horizontalLineTo(7f)
                lineTo(12f, 4f)
                verticalLineTo(20f)
                lineTo(7f, 15f)
                horizontalLineTo(3f)
                close()
                moveTo(16.5f, 12f)
                curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                verticalLineTo(16.02f)
                curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
                close()
                moveTo(14f, 3.23f)
                verticalLineTo(5.29f)
                curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
                curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
                verticalLineTo(20.77f)
                curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
                curveTo(21f, 7.72f, 18.01f, 4.14f, 14f, 3.23f)
                close()
            }
        }.build()
    }
}

private fun PathBuilder.quickToolsDot(cx: Float, cy: Float) {
    val half = 1.55f
    moveTo(cx - half, cy - half)
    horizontalLineTo(cx + half)
    verticalLineTo(cy + half)
    horizontalLineTo(cx - half)
    close()
}
