package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ThinActionIcons {
    private const val STROKE_WIDTH = 1.5f
    private val strokeBrush = SolidColor(Color.White)

    private fun createThinIcon(
        name: String,
        builder: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(builder).build()

    val Block: ImageVector by lazy {
        createThinIcon("ThinBlock") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4.5f)
                curveTo(7.86f, 4.5f, 4.5f, 7.86f, 4.5f, 12f)
                curveTo(4.5f, 16.14f, 7.86f, 19.5f, 12f, 19.5f)
                curveTo(16.14f, 19.5f, 19.5f, 16.14f, 19.5f, 12f)
                curveTo(19.5f, 7.86f, 16.14f, 4.5f, 12f, 4.5f)
                close()
                moveTo(6.7f, 6.7f)
                lineTo(17.3f, 17.3f)
            }
        }
    }

    val SortByAlpha: ImageVector by lazy {
        createThinIcon("ThinSortByAlpha") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 左上 A
                moveTo(4f, 9f)
                lineTo(6.5f, 4f)
                lineTo(9f, 9f)
                moveTo(5.2f, 7f)
                lineTo(7.8f, 7f)
                // 左下 Z
                moveTo(4f, 13f)
                lineTo(9f, 13f)
                lineTo(4f, 19f)
                lineTo(9f, 19f)
                // 右侧贯穿双向箭头
                moveTo(16.5f, 4f)
                lineTo(16.5f, 20f)
                moveTo(13.5f, 7f)
                lineTo(16.5f, 4f)
                lineTo(19.5f, 7f)
                moveTo(13.5f, 17f)
                lineTo(16.5f, 20f)
                lineTo(19.5f, 17f)
            }
        }
    }

    val Apps: ImageVector by lazy {
        createThinIcon("ThinApps") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 4.5f); lineTo(8.5f, 4.5f); lineTo(8.5f, 8.5f); lineTo(4.5f, 8.5f); close()
                moveTo(11.5f, 4.5f); lineTo(15.5f, 4.5f); lineTo(15.5f, 8.5f); lineTo(11.5f, 8.5f); close()
                moveTo(18.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 8.5f); lineTo(18.5f, 8.5f); close()
                moveTo(4.5f, 11.5f); lineTo(8.5f, 11.5f); lineTo(8.5f, 15.5f); lineTo(4.5f, 15.5f); close()
                moveTo(11.5f, 11.5f); lineTo(15.5f, 11.5f); lineTo(15.5f, 15.5f); lineTo(11.5f, 15.5f); close()
                moveTo(18.5f, 11.5f); lineTo(19.5f, 11.5f); lineTo(19.5f, 15.5f); lineTo(18.5f, 15.5f); close()
                moveTo(4.5f, 18.5f); lineTo(8.5f, 18.5f); lineTo(8.5f, 19.5f); lineTo(4.5f, 19.5f); close()
                moveTo(11.5f, 18.5f); lineTo(15.5f, 18.5f); lineTo(15.5f, 19.5f); lineTo(11.5f, 19.5f); close()
                moveTo(18.5f, 18.5f); lineTo(19.5f, 18.5f); lineTo(19.5f, 19.5f); lineTo(18.5f, 19.5f); close()
            }
        }
    }

    /** 1:1 官方蜂窝六边形网格 (Material Honeycomb) */
    val Hive: ImageVector by lazy {
        createThinIcon("ThinHive") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 中心六边形
                moveTo(10.95f, 14f); lineTo(13.05f, 14f); lineTo(14.18f, 12f); lineTo(13.05f, 10f); lineTo(10.95f, 10f); lineTo(9.82f, 12f); close()
                // 2. 正上六边形
                moveTo(10.95f, 8f); lineTo(13.05f, 8f); lineTo(14.18f, 6f); lineTo(13.05f, 4f); lineTo(10.95f, 4f); lineTo(9.82f, 6f); close()
                // 3. 正下六边形
                moveTo(10.95f, 20f); lineTo(13.05f, 20f); lineTo(14.18f, 18f); lineTo(13.05f, 16f); lineTo(10.95f, 16f); lineTo(9.82f, 18f); close()
                // 4. 左上六边形
                moveTo(5.92f, 11f); lineTo(8.03f, 11f); lineTo(9.15f, 9f); lineTo(8.03f, 7f); lineTo(5.92f, 7f); lineTo(4.79f, 9f); close()
                // 5. 左下六边形
                moveTo(5.92f, 17f); lineTo(8.03f, 17f); lineTo(9.15f, 15f); lineTo(8.03f, 13f); lineTo(5.92f, 13f); lineTo(4.79f, 15f); close()
                // 6. 右上六边形
                moveTo(15.97f, 11f); lineTo(18.08f, 11f); lineTo(19.21f, 9f); lineTo(18.08f, 7f); lineTo(15.97f, 7f); lineTo(14.85f, 9f); close()
                // 7. 右下六边形
                moveTo(15.97f, 17f); lineTo(18.08f, 17f); lineTo(19.21f, 15f); lineTo(18.08f, 13f); lineTo(15.97f, 13f); lineTo(14.85f, 15f); close()
            }
        }
    }

    /**
     * 圆环启动器：贴边弹出的 180° 半圆面板。
     *
     * 真实面板是「180° 半圆 + 1~4 道同心环 + 每环 5/8/11/14 个槽位」（见 FvCircleLayoutEngine），
     * 24dp 画不下那么多：这里取两道同心弧 + 外带 4 个槽位格，弦（=屏幕边）在下方。
     * 内带留白保住「两环」的读法；分隔只落在外带，避免被读成放射光芒（与「屏幕常亮」区分）。
     */
    val RingLauncher: ImageVector by lazy {
        createThinIcon("ThinRingLauncher") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 外弧 r=9.4：半圆，圆心 (12, 19.2)
                moveTo(2.6f, 19.2f)
                curveTo(2.6f, 14.009f, 6.809f, 9.8f, 12f, 9.8f)
                curveTo(17.191f, 9.8f, 21.4f, 14.009f, 21.4f, 19.2f)
                // 内弧 r=6.0：第二道环
                moveTo(6f, 19.2f)
                curveTo(6f, 15.886f, 8.686f, 13.2f, 12f, 13.2f)
                curveTo(15.314f, 13.2f, 18f, 15.886f, 18f, 19.2f)
                // 弦 = 屏幕边（长度恰为直径，与两道弧的端点重合）
                moveTo(2.6f, 19.2f)
                lineTo(21.4f, 19.2f)
                // 外带 3 条径向分隔 => 4 个槽位格
                moveTo(16.24f, 14.96f)
                lineTo(18.65f, 12.55f)
                moveTo(12f, 13.2f)
                lineTo(12f, 9.8f)
                moveTo(7.76f, 14.96f)
                lineTo(5.35f, 12.55f)
            }
        }
    }

    /**
     * 快捷轮盘：中心一个大圆 + 外围四个小圆（对应轮盘"中心盘 + 一圈容器"的结构）。
     *
     * 与「指尖环」等区分：中心圆明显更大，小圆只在四个正方向，整体是一个"环形阵列"的读法。
     */
    val QuickWheel: ImageVector by lazy {
        createThinIcon("ThinQuickWheel") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 中心大圆：圆心 (12,12)、半径 3.0
                moveTo(12f, 9f)
                curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
                curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
                curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
                curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
                close()
                // 上：圆心 (12,4.9)、半径 1.8
                moveTo(12f, 3.1f)
                curveTo(12.99f, 3.1f, 13.8f, 3.91f, 13.8f, 4.9f)
                curveTo(13.8f, 5.89f, 12.99f, 6.7f, 12f, 6.7f)
                curveTo(11.01f, 6.7f, 10.2f, 5.89f, 10.2f, 4.9f)
                curveTo(10.2f, 3.91f, 11.01f, 3.1f, 12f, 3.1f)
                close()
                // 下：圆心 (12,19.1)
                moveTo(12f, 17.3f)
                curveTo(12.99f, 17.3f, 13.8f, 18.11f, 13.8f, 19.1f)
                curveTo(13.8f, 20.09f, 12.99f, 20.9f, 12f, 20.9f)
                curveTo(11.01f, 20.9f, 10.2f, 20.09f, 10.2f, 19.1f)
                curveTo(10.2f, 18.11f, 11.01f, 17.3f, 12f, 17.3f)
                close()
                // 左：圆心 (4.9,12)
                moveTo(4.9f, 10.2f)
                curveTo(5.89f, 10.2f, 6.7f, 11.01f, 6.7f, 12f)
                curveTo(6.7f, 12.99f, 5.89f, 13.8f, 4.9f, 13.8f)
                curveTo(3.91f, 13.8f, 3.1f, 12.99f, 3.1f, 12f)
                curveTo(3.1f, 11.01f, 3.91f, 10.2f, 4.9f, 10.2f)
                close()
                // 右：圆心 (19.1,12)
                moveTo(19.1f, 10.2f)
                curveTo(20.09f, 10.2f, 20.9f, 11.01f, 20.9f, 12f)
                curveTo(20.9f, 12.99f, 20.09f, 13.8f, 19.1f, 13.8f)
                curveTo(18.11f, 13.8f, 17.3f, 12.99f, 17.3f, 12f)
                curveTo(17.3f, 11.01f, 18.11f, 10.2f, 19.1f, 10.2f)
                close()
            }
        }
    }

  /** 3D 球 / 全息启动器 */
    val Globe: ImageVector by lazy {
        createThinIcon("ThinGlobe") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, false, 12f, 3f)
                arcTo(9f, 9f, 0f, true, false, 12f, 21f)
                close()
                moveTo(3.6f, 9f)
                lineTo(20.4f, 9f)
                moveTo(3.6f, 15f)
                lineTo(20.4f, 15f)
                moveTo(12f, 3f)
                arcTo(5.4f, 9f, 0f, false, true, 12f, 21f)
                moveTo(12f, 3f)
                arcTo(5.4f, 9f, 0f, false, false, 12f, 21f)
            }
        }
    }

    val ViewCarousel: ImageVector by lazy {
        createThinIcon("ThinViewCarousel") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.5f, 4.5f)
                lineTo(15.5f, 4.5f)
                curveTo(16.6f, 4.5f, 17.5f, 5.4f, 17.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                curveTo(17.5f, 18.6f, 16.6f, 19.5f, 15.5f, 19.5f)
                lineTo(8.5f, 19.5f)
                curveTo(7.4f, 19.5f, 6.5f, 18.6f, 6.5f, 17.5f)
                lineTo(6.5f, 6.5f)
                curveTo(6.5f, 5.4f, 7.4f, 4.5f, 8.5f, 4.5f)
                close()
                moveTo(3f, 7f); lineTo(6.5f, 7f)
                moveTo(3f, 17f); lineTo(6.5f, 17f)
                moveTo(3f, 7f); lineTo(3f, 17f)
                moveTo(17.5f, 7f); lineTo(21f, 7f)
                moveTo(17.5f, 17f); lineTo(21f, 17f)
                moveTo(21f, 7f); lineTo(21f, 17f)
            }
        }
    }

    val Code: ImageVector by lazy {
        createThinIcon("ThinCode") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.5f, 6f)
                lineTo(3.5f, 12f)
                lineTo(8.5f, 18f)
                moveTo(15.5f, 6f)
                lineTo(20.5f, 12f)
                lineTo(15.5f, 18f)
                moveTo(14f, 4.5f)
                lineTo(10f, 19.5f)
            }
        }
    }

    val PlayCircle: ImageVector by lazy {
        createThinIcon("ThinPlayCircle") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.5f)
                curveTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f)
                curveTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
                curveTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f)
                curveTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
                close()
                moveTo(10f, 8.5f)
                lineTo(16f, 12f)
                lineTo(10f, 15.5f)
                close()
            }
        }
    }

    val QuickMenu: ImageVector by lazy {
        createThinIcon("ThinQuickMenu") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 外框计算器/小工具机身
                moveTo(6.5f, 2.5f)
                lineTo(17.5f, 2.5f)
                curveTo(18.6f, 2.5f, 19.5f, 3.4f, 19.5f, 4.5f)
                lineTo(19.5f, 19.5f)
                curveTo(19.5f, 20.6f, 18.6f, 21.5f, 17.5f, 21.5f)
                lineTo(6.5f, 21.5f)
                curveTo(5.4f, 21.5f, 4.5f, 20.6f, 4.5f, 19.5f)
                lineTo(4.5f, 4.5f)
                curveTo(4.5f, 3.4f, 5.4f, 2.5f, 6.5f, 2.5f)
                close()
                // 顶部显示屏
                moveTo(8f, 6.5f)
                lineTo(16f, 6.5f)
                // 按键阵列：小圆点 + 竖条操作键，通透留白不粘连
                moveTo(8f, 10.5f); lineTo(8.05f, 10.5f)
                moveTo(12f, 10.5f); lineTo(12.05f, 10.5f)
                moveTo(16f, 10.5f); lineTo(16.05f, 10.5f)
                moveTo(8f, 14.5f); lineTo(8.05f, 14.5f)
                moveTo(12f, 14.5f); lineTo(12.05f, 14.5f)
                moveTo(8f, 18f); lineTo(8.05f, 18f)
                moveTo(12f, 18f); lineTo(12.05f, 18f)
                moveTo(16f, 14.5f); lineTo(16f, 18f)
            }
        }
    }

    val Widgets: ImageVector by lazy {
        createThinIcon("ThinWidgets") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 3.5f); lineTo(9.5f, 3.5f); lineTo(9.5f, 9.5f); lineTo(3.5f, 9.5f); close()
                moveTo(14.5f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 9.5f); lineTo(14.5f, 9.5f); close()
                moveTo(3.5f, 14.5f); lineTo(9.5f, 14.5f); lineTo(9.5f, 20.5f); lineTo(3.5f, 20.5f); close()
                moveTo(17.5f, 13.5f); lineTo(21f, 17f); lineTo(17.5f, 20.5f); lineTo(14f, 17f); close()
            }
        }
    }

    val Inventory: ImageVector by lazy {
        createThinIcon("ThinInventory") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 8.5f); lineTo(4.5f, 8.5f); close()
                moveTo(5.5f, 8.5f); lineTo(5.5f, 19.5f); lineTo(18.5f, 19.5f); lineTo(18.5f, 8.5f)
                moveTo(10f, 12f); lineTo(14f, 12f)
            }
        }
    }

    val ContentPaste: ImageVector by lazy {
        createThinIcon("ThinContentPaste") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 6.5f)
                curveTo(18.6f, 6.5f, 19.5f, 7.4f, 19.5f, 8.5f)
                lineTo(19.5f, 19.5f)
                curveTo(19.5f, 20.6f, 18.6f, 21.5f, 17.5f, 21.5f)
                lineTo(6.5f, 21.5f)
                curveTo(5.4f, 21.5f, 4.5f, 20.6f, 4.5f, 19.5f)
                lineTo(4.5f, 8.5f)
                curveTo(4.5f, 7.4f, 5.4f, 6.5f, 6.5f, 6.5f)
                close()
                moveTo(9f, 6.5f)
                curveTo(9f, 5.4f, 9.9f, 4.5f, 11f, 4.5f)
                lineTo(13f, 4.5f)
                curveTo(14.1f, 4.5f, 15f, 5.4f, 15f, 6.5f)
                close()
            }
        }
    }

    val MyLocation: ImageVector by lazy {
        createThinIcon("ThinMyLocation") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 6.5f)
                curveTo(8.96f, 6.5f, 6.5f, 8.96f, 6.5f, 12f)
                curveTo(6.5f, 15.04f, 8.96f, 17.5f, 12f, 17.5f)
                curveTo(15.04f, 17.5f, 17.5f, 15.04f, 17.5f, 12f)
                curveTo(17.5f, 8.96f, 15.04f, 6.5f, 12f, 6.5f)
                close()
                moveTo(12f, 3f); lineTo(12f, 5f)
                moveTo(12f, 19f); lineTo(12f, 21f)
                moveTo(3f, 12f); lineTo(5f, 12f)
                moveTo(19f, 12f); lineTo(21f, 12f)
            }
            path(fill = strokeBrush) {
                moveTo(13f, 12f)
                curveTo(13f, 12.55f, 12.55f, 13f, 12f, 13f)
                curveTo(11.45f, 13f, 11f, 12.55f, 11f, 12f)
                curveTo(11f, 11.45f, 11.45f, 11f, 12f, 11f)
                curveTo(12.55f, 11f, 13f, 11.45f, 13f, 12f)
                close()
            }
        }
    }

    val TouchApp: ImageVector by lazy {
        createThinIcon("ThinTouchApp") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 6f)
                lineTo(10f, 13f)
                moveTo(10f, 10f)
                curveTo(8.3f, 10f, 7f, 11.3f, 7f, 13f)
                lineTo(7f, 17f)
                curveTo(7f, 19.2f, 8.8f, 21f, 11f, 21f)
                lineTo(14f, 21f)
                curveTo(16.8f, 21f, 19f, 18.8f, 19f, 16f)
                lineTo(19f, 12f)
                moveTo(14f, 4f)
                curveTo(15.7f, 4.8f, 17f, 6.5f, 17f, 8.5f)
            }
        }
    }

    val Back: ImageVector by lazy {
        createThinIcon("ThinBack") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(11f, 6f)
                lineTo(5f, 12f)
                lineTo(11f, 18f)
            }
        }
    }

    val Home: ImageVector by lazy {
        createThinIcon("ThinHome") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 10.5f)
                lineTo(12f, 3.5f)
                lineTo(20.5f, 10.5f)
                moveTo(5.5f, 9.5f)
                lineTo(5.5f, 19.5f)
                lineTo(18.5f, 19.5f)
                lineTo(18.5f, 9.5f)
            }
        }
    }

    val Recents: ImageVector by lazy {
        createThinIcon("ThinRecents") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.5f)
                lineTo(21f, 7.5f)
                lineTo(12f, 11.5f)
                lineTo(3f, 7.5f)
                close()
                moveTo(3f, 12f)
                lineTo(12f, 16f)
                lineTo(21f, 12f)
                moveTo(3f, 16.5f)
                lineTo(12f, 20.5f)
                lineTo(21f, 16.5f)
            }
        }
    }

    val Close: ImageVector by lazy {
        createThinIcon("ThinClose") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    /** 圈内叉：强行停止当前应用（区别于 NONE 的圈内斜杠与 Close 的裸叉）。 */
    val ForceStopApp: ImageVector by lazy {
        createThinIcon("ThinForceStopApp") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4.5f)
                curveTo(7.86f, 4.5f, 4.5f, 7.86f, 4.5f, 12f)
                curveTo(4.5f, 16.14f, 7.86f, 19.5f, 12f, 19.5f)
                curveTo(16.14f, 19.5f, 19.5f, 16.14f, 19.5f, 12f)
                curveTo(19.5f, 7.86f, 16.14f, 4.5f, 12f, 4.5f)
                close()
                moveTo(9.2f, 9.2f)
                lineTo(14.8f, 14.8f)
                moveTo(14.8f, 9.2f)
                lineTo(9.2f, 14.8f)
            }
        }
    }

    /** 100% 精准对齐：FreeWindow 箭头尾部恰好位于左竖线 (X:2.5) 与上横线 (Y:4.5) 的延伸交点 (2.5, 4.5) */
    val FreeWindow: ImageVector by lazy {
        createThinIcon("ThinFreeWindow") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 单向右下拉伸箭头 (尾部精确对齐左竖线与上横线延伸交点 2.5, 4.5 -> 右下 Pip 窗口 11.5, 13.5)
                moveTo(2.5f, 4.5f)
                lineTo(11.5f, 13.5f)
                // 右下方向箭尖 (指向右下方 11.5, 13.5)
                moveTo(8.5f, 13.5f)
                lineTo(11.5f, 13.5f)
                lineTo(11.5f, 10.5f)
                // 2. 左下角框架 (左侧竖线 4dp 延长)
                moveTo(2.5f, 9f); lineTo(2.5f, 19f)
                curveTo(2.5f, 20f, 3.5f, 20.5f, 4.5f, 20.5f)
                lineTo(11.5f, 20.5f)
                // 3. 右上角框架 (顶端横线 4dp 延长)
                moveTo(7.5f, 4.5f); lineTo(19.5f, 4.5f)
                curveTo(20.5f, 4.5f, 21.5f, 5.5f, 21.5f, 6.5f)
                lineTo(21.5f, 13f)
                // 4. 右下角 Pip 悬浮迷你小窗口
                moveTo(13.5f, 14.5f)
                lineTo(21.5f, 14.5f)
                lineTo(21.5f, 20.5f)
                lineTo(13.5f, 20.5f)
                close()
            }
        }
    }

    val Flashlight: ImageVector by lazy {
        createThinIcon("ThinFlashlight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 11f)
                lineTo(9f, 20f)
                lineTo(15f, 20f)
                lineTo(15f, 11f)
                lineTo(18f, 6f)
                lineTo(18f, 4f)
                lineTo(6f, 4f)
                lineTo(6f, 6f)
                close()
                moveTo(6f, 6f)
                lineTo(18f, 6f)
                moveTo(12f, 12f)
                lineTo(12f, 14f)
            }
        }
    }

    val VolumeUp: ImageVector by lazy {
        createThinIcon("ThinVolumeUp") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 9f)
                lineTo(8.5f, 9f)
                lineTo(13f, 5.5f)
                lineTo(13f, 18.5f)
                lineTo(8.5f, 15f)
                lineTo(5f, 15f)
                close()
                moveTo(16.5f, 8.5f)
                curveTo(17.8f, 9.8f, 17.8f, 14.2f, 16.5f, 15.5f)
                moveTo(19f, 6f)
                curveTo(21.5f, 9f, 21.5f, 15f, 19f, 18f)
            }
        }
    }

    /** 扬声器 + 加号：音量增加。 */
    val VolumeIncrease: ImageVector by lazy {
        createThinIcon("ThinVolumeIncrease") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 9f)
                lineTo(7.5f, 9f)
                lineTo(12f, 5.5f)
                lineTo(12f, 18.5f)
                lineTo(7.5f, 15f)
                lineTo(4f, 15f)
                close()
                moveTo(18.5f, 9.5f)
                lineTo(18.5f, 14.5f)
                moveTo(16f, 12f)
                lineTo(21f, 12f)
            }
        }
    }

    /** 扬声器 − 减号：音量减小。 */
    val VolumeDecrease: ImageVector by lazy {
        createThinIcon("ThinVolumeDecrease") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 9f)
                lineTo(7.5f, 9f)
                lineTo(12f, 5.5f)
                lineTo(12f, 18.5f)
                lineTo(7.5f, 15f)
                lineTo(4f, 15f)
                close()
                moveTo(16f, 12f)
                lineTo(21f, 12f)
            }
        }
    }

    val VolumeOff: ImageVector by lazy {
        createThinIcon("ThinVolumeOff") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 9f)
                lineTo(8.5f, 9f)
                lineTo(13f, 5.5f)
                lineTo(13f, 18.5f)
                lineTo(8.5f, 15f)
                lineTo(5f, 15f)
                close()
                moveTo(16f, 9f)
                lineTo(21f, 15f)
                moveTo(21f, 9f)
                lineTo(16f, 15f)
            }
        }
    }

    val Brightness: ImageVector by lazy {
        createThinIcon("ThinBrightness") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 7f)
                curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
                curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
                curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
                curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
                close()
                moveTo(12f, 3f); lineTo(12f, 4.5f)
                moveTo(12f, 19.5f); lineTo(12f, 21f)
                moveTo(3f, 12f); lineTo(4.5f, 12f)
                moveTo(19.5f, 12f); lineTo(21f, 12f)
                moveTo(5.64f, 5.64f); lineTo(6.7f, 6.7f)
                moveTo(17.3f, 17.3f); lineTo(18.36f, 18.36f)
                moveTo(5.64f, 18.36f); lineTo(6.7f, 17.3f)
                moveTo(17.3f, 6.7f); lineTo(18.36f, 5.64f)
            }
        }
    }

    val Assistant: ImageVector by lazy {
        createThinIcon("ThinAssistant") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 魔法棒主体 (斜向杖身)
                moveTo(2.5f, 21.5f)
                lineTo(14.5f, 9.5f)
                // 杖尖分界标线
                moveTo(11f, 13f)
                lineTo(13f, 11f)
                // 杖尖周围迸发的 7 道星芒光束 (Bootstrap Magic)
                moveTo(14.5f, 2f); lineTo(14.5f, 4.8f)
                moveTo(20.5f, 3.5f); lineTo(18.5f, 5.5f)
                moveTo(22f, 9.5f); lineTo(19.2f, 9.5f)
                moveTo(20.5f, 15.5f); lineTo(18.5f, 13.5f)
                moveTo(8.5f, 3.5f); lineTo(10.5f, 5.5f)
                moveTo(7f, 9.5f); lineTo(9.8f, 9.5f)
                moveTo(14.5f, 17f); lineTo(14.5f, 14.2f)
            }
        }
    }

    val VoiceSearch: ImageVector by lazy {
        createThinIcon("ThinVoiceSearch") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 麦克风收音胶囊 (Bootstrap Mic)
                moveTo(12f, 3f)
                curveTo(10.34f, 3f, 9f, 4.34f, 9f, 6f)
                lineTo(9f, 12f)
                curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
                curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
                lineTo(15f, 6f)
                curveTo(15f, 4.34f, 13.66f, 3f, 12f, 3f)
                close()
                // U 型托架
                moveTo(5.5f, 10.5f)
                curveTo(5.5f, 14.8f, 8.5f, 18f, 12f, 18f)
                curveTo(15.5f, 18f, 18.5f, 14.8f, 18.5f, 10.5f)
                // 支架竖线与底座横线
                moveTo(12f, 18f); lineTo(12f, 21.5f)
                moveTo(8f, 21.5f); lineTo(16f, 21.5f)
            }
        }
    }

    val VoiceAssistant: ImageVector by lazy {
        createThinIcon("ThinVoiceAssistant") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 主四角星 (中心偏右下 - Bootstrap Stars)
                moveTo(12.5f, 8f)
                curveTo(12.5f, 12f, 9.5f, 14.5f, 5.5f, 14.5f)
                curveTo(9.5f, 14.5f, 12.5f, 17f, 12.5f, 21f)
                curveTo(12.5f, 17f, 15.5f, 14.5f, 19.5f, 14.5f)
                curveTo(15.5f, 14.5f, 12.5f, 12f, 12.5f, 8f)
                close()
                // 2. 左上次级星 (Bootstrap 中星)
                moveTo(6.5f, 2.5f)
                curveTo(6.5f, 4.8f, 4.8f, 6.5f, 2.5f, 6.5f)
                curveTo(4.8f, 6.5f, 6.5f, 8.2f, 6.5f, 10.5f)
                curveTo(6.5f, 8.2f, 8.2f, 6.5f, 10.5f, 6.5f)
                curveTo(8.2f, 6.5f, 6.5f, 4.8f, 6.5f, 2.5f)
                close()
                // 3. 右上小星 (Bootstrap 小星)
                moveTo(18f, 2f)
                curveTo(18f, 3.5f, 16.5f, 4.5f, 15f, 4.5f)
                curveTo(16.5f, 4.5f, 18f, 5.5f, 18f, 7f)
                curveTo(18f, 5.5f, 19.5f, 4.5f, 21f, 4.5f)
                curveTo(19.5f, 4.5f, 18f, 3.5f, 18f, 2f)
                close()
            }
        }
    }

    /** 自动旋转：官方 Material Symbols ScreenRotation 矢量图标 */
    val ToggleAutoRotate: ImageVector get() = Icons.Outlined.ScreenRotation

    val ForcePortrait: ImageVector by lazy {
        createThinIcon("ThinForcePortrait") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 竖屏外框 (Bootstrap Phone)
                moveTo(7.5f, 3f)
                lineTo(16.5f, 3f)
                curveTo(17.6f, 3f, 18.5f, 3.9f, 18.5f, 5f)
                lineTo(18.5f, 19f)
                curveTo(18.5f, 20.1f, 17.6f, 21f, 16.5f, 21f)
                lineTo(7.5f, 21f)
                curveTo(6.4f, 21f, 5.5f, 20.1f, 5.5f, 19f)
                lineTo(5.5f, 5f)
                curveTo(5.5f, 3.9f, 6.4f, 3f, 7.5f, 3f)
                close()
                // 底部 Home 标/下巴点
                moveTo(11.5f, 17.5f)
                lineTo(12.5f, 17.5f)
            }
        }
    }

    val ForceLandscape: ImageVector by lazy {
        createThinIcon("ThinForceLandscape") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 横屏外框 (Bootstrap Phone Landscape)
                moveTo(5f, 5.5f)
                lineTo(19f, 5.5f)
                curveTo(20.1f, 5.5f, 21f, 6.4f, 21f, 7.5f)
                lineTo(21f, 16.5f)
                curveTo(21f, 17.6f, 20.1f, 18.5f, 19f, 18.5f)
                lineTo(5f, 18.5f)
                curveTo(3.9f, 18.5f, 3f, 17.6f, 3f, 16.5f)
                lineTo(3f, 7.5f)
                curveTo(3f, 6.4f, 3.9f, 5.5f, 5f, 5.5f)
                close()
                // 右侧 Home 标/侧巴点
                moveTo(17.5f, 11.5f)
                lineTo(17.5f, 12.5f)
            }
        }
    }

    val PlayPause: ImageVector by lazy {
        createThinIcon("ThinPlayPause") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 7.5f)
                lineTo(5.5f, 16.5f)
                lineTo(10.5f, 12f)
                close()
                moveTo(13f, 7.5f)
                lineTo(13f, 16.5f)
                moveTo(16f, 7.5f)
                lineTo(16f, 16.5f)
            }
        }
    }

    val SkipPrevious: ImageVector by lazy {
        createThinIcon("ThinSkipPrevious") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 6.5f); lineTo(7f, 17.5f)
                moveTo(17f, 6.5f); lineTo(9.5f, 12f); lineTo(17f, 17.5f); close()
            }
        }
    }

    val SkipNext: ImageVector by lazy {
        createThinIcon("ThinSkipNext") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(17f, 6.5f); lineTo(17f, 17.5f)
                moveTo(7f, 6.5f); lineTo(14.5f, 12f); lineTo(7f, 17.5f); close()
            }
        }
    }

    /** 绝无撞线的 Android 机器人 + 右下角独立避让 History 历史指针 (Restore) */
    val Restore: ImageVector by lazy {
        createThinIcon("ThinRestore") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. Android 机器人触角
                moveTo(6.5f, 6f); lineTo(8.5f, 9.5f)
                moveTo(15.5f, 6f); lineTo(13.5f, 9.5f)
                // Android 机器人圆弧头顶 (右下避让)
                moveTo(4.5f, 16f)
                curveTo(4.5f, 10f, 7.5f, 9.5f, 11f, 9.5f)
                curveTo(14.5f, 9.5f, 16.5f, 10.5f, 16.5f, 15f)
                // 机器人眼睛
                moveTo(8f, 12.5f); lineTo(8f, 12.6f)
                moveTo(13.5f, 12.5f); lineTo(13.5f, 12.6f)
                // 2. 右下角独立避让 History 历史指针角标 (X: 13.5..21.5, Y: 13.5..21.5)
                moveTo(17.5f, 14.5f)
                curveTo(15.5f, 14.5f, 14f, 16f, 14f, 18f)
                curveTo(14f, 20f, 15.5f, 21.5f, 17.5f, 21.5f)
                curveTo(19.5f, 21.5f, 21f, 20f, 21f, 18f)
                curveTo(21f, 16f, 19.5f, 14.5f, 17.5f, 14.5f)
                close()
                // 指针
                moveTo(17.5f, 16f); lineTo(17.5f, 18f); lineTo(19f, 18f)
                // 回溯箭尖
                moveTo(16f, 14f); lineTo(14.5f, 15.5f); lineTo(16f, 17f)
            }
        }
    }

    val Notifications: ImageVector by lazy {
        createThinIcon("ThinNotifications") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4f)
                curveTo(9.2f, 4f, 7f, 6.2f, 7f, 9f)
                lineTo(7f, 14f)
                lineTo(5f, 16f)
                lineTo(19f, 16f)
                lineTo(17f, 14f)
                lineTo(17f, 9f)
                curveTo(17f, 6.2f, 14.8f, 4f, 12f, 4f)
                close()
                moveTo(10f, 18f)
                curveTo(10f, 19.1f, 10.9f, 20f, 12f, 20f)
                curveTo(13.1f, 20f, 14f, 19.1f, 14f, 18f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        createThinIcon("ThinSettings") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 8.5f)
                curveTo(10.07f, 8.5f, 8.5f, 10.07f, 8.5f, 12f)
                curveTo(8.5f, 13.93f, 10.07f, 15.5f, 12f, 15.5f)
                curveTo(13.93f, 15.5f, 15.5f, 13.93f, 15.5f, 12f)
                curveTo(15.5f, 10.07f, 13.93f, 8.5f, 12f, 8.5f)
                close()
                moveTo(12f, 4.5f); lineTo(12f, 6.5f)
                moveTo(12f, 17.5f); lineTo(12f, 19.5f)
                moveTo(4.5f, 12f); lineTo(6.5f, 12f)
                moveTo(17.5f, 12f); lineTo(19.5f, 12f)
                moveTo(6.7f, 6.7f); lineTo(8.1f, 8.1f)
                moveTo(15.9f, 15.9f); lineTo(17.3f, 17.3f)
                moveTo(6.7f, 17.3f); lineTo(8.1f, 15.9f)
                moveTo(15.9f, 8.1f); lineTo(17.3f, 6.7f)
            }
        }
    }

    /** 快捷设置 / 控制中心：三竖滑条，与太阳亮度图标区分。 */
    val QuickSettings: ImageVector by lazy {
        createThinIcon("ThinQuickSettings") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8f, 6f); lineTo(8f, 18f)
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(16f, 7f); lineTo(16f, 17f)
                moveTo(9.35f, 10f)
                arcTo(1.35f, 1.35f, 0f, false, true, 6.65f, 10f)
                arcTo(1.35f, 1.35f, 0f, false, true, 9.35f, 10f)
                moveTo(13.35f, 14f)
                arcTo(1.35f, 1.35f, 0f, false, true, 10.65f, 14f)
                arcTo(1.35f, 1.35f, 0f, false, true, 13.35f, 14f)
                moveTo(17.35f, 9f)
                arcTo(1.35f, 1.35f, 0f, false, true, 14.65f, 9f)
                arcTo(1.35f, 1.35f, 0f, false, true, 17.35f, 9f)
            }
        }
    }

    /** 屏幕常亮：圆环 + 中心锁 + 八向光芒（与 [Brightness] 纯太阳、[Lock] 纯锁区分）。 */
    val KeepScreenOn: ImageVector by lazy {
        createThinIcon("ThinKeepScreenOn") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5.5f)
                curveTo(15.58f, 5.5f, 18.5f, 8.42f, 18.5f, 12f)
                curveTo(18.5f, 15.58f, 15.58f, 18.5f, 12f, 18.5f)
                curveTo(8.42f, 18.5f, 5.5f, 15.58f, 5.5f, 12f)
                curveTo(5.5f, 8.42f, 8.42f, 5.5f, 12f, 5.5f)
                close()
                moveTo(10f, 11f)
                lineTo(10f, 9.25f)
                curveTo(10f, 8.1f, 10.9f, 7.25f, 12f, 7.25f)
                curveTo(13.1f, 7.25f, 14f, 8.1f, 14f, 9.25f)
                lineTo(14f, 11f)
                moveTo(9.75f, 11f)
                lineTo(14.25f, 11f)
                lineTo(14.25f, 14.75f)
                lineTo(9.75f, 14.75f)
                close()
                moveTo(12f, 12.35f)
                lineTo(12f, 13.45f)
                moveTo(12f, 3f)
                lineTo(12f, 5.5f)
                moveTo(12f, 21f)
                lineTo(12f, 18.5f)
                moveTo(3f, 12f)
                lineTo(5.5f, 12f)
                moveTo(21f, 12f)
                lineTo(18.5f, 12f)
                moveTo(5.64f, 5.64f)
                lineTo(7.35f, 7.35f)
                moveTo(18.36f, 18.36f)
                lineTo(16.65f, 16.65f)
                moveTo(5.64f, 18.36f)
                lineTo(7.35f, 16.65f)
                moveTo(18.36f, 5.64f)
                lineTo(16.65f, 7.35f)
            }
        }
    }

    val Alarm: ImageVector by lazy {
        createThinIcon("ThinAlarm") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 6.5f)
                curveTo(8.41f, 6.5f, 5.5f, 9.41f, 5.5f, 13f)
                curveTo(5.5f, 16.59f, 8.41f, 19.5f, 12f, 19.5f)
                curveTo(15.59f, 19.5f, 18.5f, 16.59f, 18.5f, 13f)
                curveTo(18.5f, 9.41f, 15.59f, 6.5f, 12f, 6.5f)
                close()
                moveTo(12f, 9.5f)
                lineTo(12f, 13f)
                lineTo(14.75f, 15.25f)
                moveTo(4.5f, 4.5f)
                lineTo(6.75f, 6.75f)
                moveTo(19.5f, 4.5f)
                lineTo(17.25f, 6.75f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        createThinIcon("ThinLock") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 10.5f)
                lineTo(17.5f, 10.5f)
                curveTo(18.6f, 10.5f, 19.5f, 11.4f, 19.5f, 12.5f)
                lineTo(19.5f, 19.5f)
                curveTo(19.5f, 20.6f, 18.6f, 21.5f, 17.5f, 21.5f)
                lineTo(6.5f, 21.5f)
                curveTo(5.4f, 21.5f, 4.5f, 20.6f, 4.5f, 19.5f)
                lineTo(4.5f, 12.5f)
                curveTo(4.5f, 11.4f, 5.4f, 10.5f, 6.5f, 10.5f)
                close()
                moveTo(8f, 10.5f)
                lineTo(8f, 6.5f)
                curveTo(8f, 4.3f, 9.8f, 2.5f, 12f, 2.5f)
                curveTo(14.2f, 2.5f, 16f, 4.3f, 16f, 6.5f)
                lineTo(16f, 10.5f)
            }
        }
    }

    /** 锁屏并静音响铃：大锁屏外框 + 方框内上下左右几何居中 (Y: 15.75f) 干净带斜杠静音铃铛 */
    val LockSilenceRing: ImageVector by lazy {
        createThinIcon("ThinLockSilenceRing") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 大锁屏外框
                moveTo(5f, 10f)
                lineTo(19f, 10f)
                curveTo(19.8f, 10f, 20.5f, 10.7f, 20.5f, 11.5f)
                lineTo(20.5f, 20f)
                curveTo(20.5f, 20.8f, 19.8f, 21.5f, 19f, 21.5f)
                lineTo(5f, 21.5f)
                curveTo(4.2f, 21.5f, 3.5f, 20.8f, 3.5f, 20f)
                lineTo(3.5f, 11.5f)
                curveTo(3.5f, 10.7f, 4.2f, 10f, 5f, 10f)
                close()
                // 锁顶钩
                moveTo(8.5f, 10f)
                lineTo(8.5f, 6.5f)
                curveTo(8.5f, 4.5f, 10f, 3f, 12f, 3f)
                curveTo(14f, 3f, 15.5f, 4.5f, 15.5f, 6.5f)
                lineTo(15.5f, 10f)
                // 2. 方框内部上下居中 (Y 轴中心 15.75) 的清晰静音铃铛
                moveTo(12f, 12.5f)
                curveTo(11.2f, 12.5f, 10.5f, 13.2f, 10.5f, 14f)
                lineTo(10.5f, 16.5f)
                lineTo(9.5f, 17.5f)
                lineTo(14.5f, 17.5f)
                lineTo(13.5f, 16.5f)
                lineTo(13.5f, 14f)
                curveTo(13.5f, 13.2f, 12.8f, 12.5f, 12f, 12.5f)
                close()
                // 摆锤
                moveTo(11.5f, 18.5f)
                curveTo(11.5f, 18.8f, 11.7f, 19f, 12f, 19f)
                curveTo(12.3f, 19f, 12.5f, 18.8f, 12.5f, 18.5f)
                // 静音斜杠 (直观易懂)
                moveTo(9f, 13f)
                lineTo(15f, 18.5f)
            }
        }
    }

    /** 锁屏并全部静音：大锁屏外框 + 喇叭与右侧静音叉号精确保留 2dp 完美间距 */
    val LockMuteAll: ImageVector by lazy {
        createThinIcon("ThinLockMuteAll") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 大锁屏外框
                moveTo(5f, 10f)
                lineTo(19f, 10f)
                curveTo(19.8f, 10f, 20.5f, 10.7f, 20.5f, 11.5f)
                lineTo(20.5f, 20f)
                curveTo(20.5f, 20.8f, 19.8f, 21.5f, 19f, 21.5f)
                lineTo(5f, 21.5f)
                curveTo(4.2f, 21.5f, 3.5f, 20.8f, 3.5f, 20f)
                lineTo(3.5f, 11.5f)
                curveTo(3.5f, 10.7f, 4.2f, 10f, 5f, 10f)
                close()
                // 锁顶钩
                moveTo(8.5f, 10f)
                lineTo(8.5f, 6.5f)
                curveTo(8.5f, 4.5f, 10f, 3f, 12f, 3f)
                curveTo(14f, 3f, 15.5f, 4.5f, 15.5f, 6.5f)
                lineTo(15.5f, 10f)
                // 2. 喇叭口 (X: 7.5..11.0)
                moveTo(7.5f, 14.5f)
                lineTo(9f, 14.5f)
                lineTo(11f, 12.5f)
                lineTo(11f, 19f)
                lineTo(9f, 17f)
                lineTo(7.5f, 17f)
                close()
                // 3. 静音叉号 x (X: 13.0..16.0，与喇叭口保留精准 2dp 完美留白)
                moveTo(13f, 14f)
                lineTo(16f, 17f)
                moveTo(16f, 14f)
                lineTo(13f, 17f)
            }
        }
    }

    /** 1:1 依照用户指定 SVG (screenshot) 重绘：手机边框 + 顶底横杠 + 单线精细 L 裁切角标 (Screenshot) */
    val Screenshot: ImageVector by lazy {
        createThinIcon("ThinScreenshot") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 手机外框 (5..19, 2.5..21.5)
                moveTo(6.5f, 2.5f)
                lineTo(17.5f, 2.5f)
                curveTo(18.5f, 2.5f, 19f, 3f, 19f, 4f)
                lineTo(19f, 20f)
                curveTo(19f, 21f, 18.5f, 21.5f, 17.5f, 21.5f)
                lineTo(6.5f, 21.5f)
                curveTo(5.5f, 21.5f, 5f, 21f, 5f, 20f)
                lineTo(5f, 4f)
                curveTo(5f, 3f, 5.5f, 2.5f, 6.5f, 2.5f)
                close()
                // 2. 顶杠与底杠区分线
                moveTo(5f, 5f); lineTo(19f, 5f)
                moveTo(5f, 19f); lineTo(19f, 19f)
                // 3. 干净单线条 1.5dp L 型截切角标 (绝不粗重)
                moveTo(11.5f, 8f); lineTo(8.5f, 8f); lineTo(8.5f, 11f)
                moveTo(12.5f, 16f); lineTo(15.5f, 16f); lineTo(15.5f, 13f)
            }
        }
    }

    val TextFields: ImageVector by lazy {
        createThinIcon("ThinTextFields") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 6f); lineTo(14f, 6f)
                moveTo(9f, 6f); lineTo(9f, 19f)
                moveTo(14f, 11f); lineTo(20f, 11f)
                moveTo(17f, 11f); lineTo(17f, 19f)
            }
        }
    }

    /** 1:1 官方 Material crop_free 区域截图/裁剪+ (ScreenshotRegion) */
    val ScreenshotRegion: ImageVector by lazy {
        createThinIcon("ThinScreenshotRegion") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 左上 L
                moveTo(5f, 10f); lineTo(5f, 5f); lineTo(10f, 5f)
                // 右上 L
                moveTo(14f, 5f); lineTo(19f, 5f); lineTo(19f, 10f)
                // 左下 L
                moveTo(5f, 14f); lineTo(5f, 19f); lineTo(10f, 19f)
                // 右下加号 +
                moveTo(16.5f, 13.5f); lineTo(16.5f, 19.5f)
                moveTo(13.5f, 16.5f); lineTo(19.5f, 16.5f)
            }
        }
    }

    val Search: ImageVector by lazy {
        createThinIcon("ThinSearch") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10.5f, 4.5f)
                curveTo(13.8f, 4.5f, 16.5f, 7.2f, 16.5f, 10.5f)
                curveTo(16.5f, 13.8f, 13.8f, 16.5f, 10.5f, 16.5f)
                curveTo(7.2f, 16.5f, 4.5f, 13.8f, 4.5f, 10.5f)
                curveTo(4.5f, 7.2f, 7.2f, 4.5f, 10.5f, 4.5f)
                close()
                moveTo(15f, 15f)
                lineTo(19.5f, 19.5f)
            }
        }
    }

    val Power: ImageVector by lazy {
        createThinIcon("ThinPower") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4f); lineTo(12f, 11f)
                moveTo(6.34f, 7.34f)
                curveTo(4.56f, 9.12f, 4f, 11.8f, 4.8f, 14.2f)
                curveTo(6f, 17.8f, 9.6f, 20f, 13.5f, 19.5f)
                curveTo(17f, 19f, 19.8f, 16.2f, 20f, 12.6f)
                curveTo(20.2f, 9.8f, 18.8f, 7.4f, 17.66f, 7.34f)
            }
        }
    }

    val ArrowUp: ImageVector by lazy {
        createThinIcon("ThinArrowUp") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 19f)
                lineTo(12f, 5f)
                moveTo(6f, 11f)
                lineTo(12f, 5f)
                lineTo(18f, 11f)
            }
        }
    }

    val ArrowDown: ImageVector by lazy {
        createThinIcon("ThinArrowDown") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(6f, 13f)
                lineTo(12f, 19f)
                lineTo(18f, 13f)
            }
        }
    }

    /** 快速回到顶部：箭头尖端带横线。 */
    val ScrollToTop: ImageVector by lazy {
        createThinIcon("ThinScrollToTop") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 5f)
                lineTo(19f, 5f)
                moveTo(12f, 19f)
                lineTo(12f, 5f)
                moveTo(6f, 11f)
                lineTo(12f, 5f)
                lineTo(18f, 11f)
            }
        }
    }

    /** 快速回到底部：箭头尖端带横线。 */
    val ScrollToBottom: ImageVector by lazy {
        createThinIcon("ThinScrollToBottom") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 19f)
                lineTo(19f, 19f)
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(6f, 13f)
                lineTo(12f, 19f)
                lineTo(18f, 13f)
            }
        }
    }

    val ArrowRight: ImageVector by lazy {
        createThinIcon("ThinArrowRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
                moveTo(13f, 6f)
                lineTo(19f, 12f)
                lineTo(13f, 18f)
            }
        }
    }

    /** 长距离滑动：左侧轨迹线 + 右侧双 chevron（>>），与短划 [ArrowRight] 同 viewport 跨度。 */
    val DoubleArrowRight: ImageVector by lazy {
        createThinIcon("ThinDoubleArrowRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12f)
                lineTo(13.5f, 12f)
                moveTo(12.5f, 7f)
                lineTo(16.5f, 12f)
                lineTo(12.5f, 17f)
                moveTo(15.5f, 7f)
                lineTo(19f, 12f)
                lineTo(15.5f, 17f)
            }
        }
    }

    val CornerArrowUpRight: ImageVector by lazy {
        createThinIcon("ThinCornerArrowUpRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 17f)
                lineTo(15f, 17f)
                lineTo(15f, 7f)
                moveTo(11f, 11f)
                lineTo(15f, 7f)
                lineTo(19f, 11f)
            }
        }
    }

    val CornerArrowDownRight: ImageVector by lazy {
        createThinIcon("ThinCornerArrowDownRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 7f)
                lineTo(15f, 7f)
                lineTo(15f, 17f)
                moveTo(11f, 13f)
                lineTo(15f, 17f)
                lineTo(19f, 13f)
            }
        }
    }

    val DoubleCornerArrowUpRight: ImageVector by lazy {
        createThinIcon("ThinDoubleCornerArrowUpRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 17f)
                lineTo(15f, 17f)
                lineTo(15f, 10.5f)
                moveTo(11.5f, 14f)
                lineTo(15f, 10.5f)
                lineTo(18.5f, 14f)
                moveTo(11.5f, 9.5f)
                lineTo(15f, 6f)
                lineTo(18.5f, 9.5f)
            }
        }
    }

    val DoubleCornerArrowDownRight: ImageVector by lazy {
        createThinIcon("ThinDoubleCornerArrowDownRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 7f)
                lineTo(15f, 7f)
                lineTo(15f, 13.5f)
                moveTo(11.5f, 10f)
                lineTo(15f, 13.5f)
                lineTo(18.5f, 10f)
                moveTo(11.5f, 14.5f)
                lineTo(15f, 18f)
                lineTo(18.5f, 14.5f)
            }
        }
    }

    val SwipeReturn: ImageVector by lazy {
        createThinIcon("ThinSwipeReturn") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 16f)
                lineTo(14f, 16f)
                curveTo(16.8f, 16f, 18.5f, 14.2f, 18.5f, 12f)
                curveTo(18.5f, 9.8f, 16.8f, 8f, 14f, 8f)
                lineTo(6f, 8f)
                moveTo(9.5f, 5f)
                lineTo(5.5f, 8f)
                lineTo(9.5f, 11f)
            }
        }
    }

    val DoNotDisturb: ImageVector by lazy {
        createThinIcon("ThinDoNotDisturb") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4.5f)
                curveTo(7.86f, 4.5f, 4.5f, 7.86f, 4.5f, 12f)
                curveTo(4.5f, 16.14f, 7.86f, 19.5f, 12f, 19.5f)
                curveTo(16.14f, 19.5f, 19.5f, 16.14f, 19.5f, 12f)
                curveTo(19.5f, 7.86f, 16.14f, 4.5f, 12f, 4.5f)
                close()
                moveTo(7.5f, 12f)
                lineTo(16.5f, 12f)
            }
        }
    }

    /** 极度具辨识度的 1:1 Material Videocam / 摄像机录屏图标 (ScreenRecord) */
    val ScreenRecord: ImageVector by lazy {
        createThinIcon("ThinScreenRecord") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 摄像机主框架
                moveTo(4.5f, 7.5f)
                lineTo(14.5f, 7.5f)
                curveTo(15.6f, 7.5f, 16.5f, 8.4f, 16.5f, 9.5f)
                lineTo(16.5f, 16.5f)
                curveTo(16.5f, 17.6f, 15.6f, 18.5f, 14.5f, 18.5f)
                lineTo(4.5f, 18.5f)
                curveTo(3.4f, 18.5f, 2.5f, 17.6f, 2.5f, 16.5f)
                lineTo(2.5f, 9.5f)
                curveTo(2.5f, 8.4f, 3.4f, 7.5f, 4.5f, 7.5f)
                close()
                // 2. 右侧摄像镜头梯形
                moveTo(16.5f, 11f)
                lineTo(20.5f, 8.5f)
                lineTo(20.5f, 17.5f)
                lineTo(16.5f, 15f)
                close()
                // 3. REC 录制红点
                moveTo(9.5f, 11.5f)
                lineTo(9.5f, 14.5f)
            }
        }
    }

    val Wifi: ImageVector by lazy {
        createThinIcon("ThinWifi") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 7.5f)
                curveTo(8.5f, 3.5f, 15.5f, 3.5f, 20.5f, 7.5f)
                moveTo(6.5f, 11.5f)
                curveTo(9.8f, 8.8f, 14.2f, 8.8f, 17.5f, 11.5f)
                moveTo(9.5f, 15.5f)
                curveTo(11f, 14.2f, 13f, 14.2f, 14.5f, 15.5f)
                moveTo(12f, 19.5f)
                lineTo(12f, 19.6f)
            }
        }
    }

    val Cellular: ImageVector by lazy {
        createThinIcon("ThinCellular") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 17.5f); lineTo(4.5f, 19.5f)
                moveTo(9.5f, 13.5f); lineTo(9.5f, 19.5f)
                moveTo(14.5f, 9.5f); lineTo(14.5f, 19.5f)
                moveTo(19.5f, 5.5f); lineTo(19.5f, 19.5f)
            }
        }
    }

    val Keyboard: ImageVector by lazy {
        createThinIcon("ThinKeyboard") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 6.5f)
                lineTo(19.5f, 6.5f)
                curveTo(20.6f, 6.5f, 21.5f, 7.4f, 21.5f, 8.5f)
                lineTo(21.5f, 15.5f)
                curveTo(21.5f, 16.6f, 20.6f, 17.5f, 19.5f, 17.5f)
                lineTo(4.5f, 17.5f)
                curveTo(3.4f, 17.5f, 2.5f, 16.6f, 2.5f, 15.5f)
                lineTo(2.5f, 8.5f)
                curveTo(2.5f, 7.4f, 3.4f, 6.5f, 4.5f, 6.5f)
                close()
                moveTo(6f, 9.5f); lineTo(8f, 9.5f)
                moveTo(11f, 9.5f); lineTo(13f, 9.5f)
                moveTo(16f, 9.5f); lineTo(18f, 9.5f)
                moveTo(6f, 12f); lineTo(8f, 12f)
                moveTo(11f, 12f); lineTo(13f, 12f)
                moveTo(16f, 12f); lineTo(18f, 12f)
                moveTo(8f, 14.5f); lineTo(16f, 14.5f)
            }
        }
    }

    val Gesture: ImageVector by lazy {
        createThinIcon("ThinGesture") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 16.5f)
                curveTo(4.5f, 16.5f, 8.5f, 6.5f, 12.5f, 11.5f)
                curveTo(16.5f, 16.5f, 19.5f, 7.5f, 19.5f, 7.5f)
            }
        }
    }

    val MenuOpen: ImageVector by lazy {
        createThinIcon("ThinMenuOpen") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(10f, 12f); lineTo(21f, 12f)
                moveTo(3f, 18f); lineTo(21f, 18f)
                moveTo(6f, 9f); lineTo(3f, 12f); lineTo(6f, 15f)
            }
        }
    }

    val Shortcut: ImageVector by lazy {
        createThinIcon("ThinShortcut") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 10f)
                moveTo(20f, 5f)
                lineTo(11f, 14f)
                curveTo(7.5f, 17.5f, 4f, 19f, 4f, 19f)
            }
        }
    }

    /** 应用内直达：标准 OpenInNew（方框 + 右上外跳箭头，约 1.5 填充）。 */
    val OpenInNew: ImageVector by lazy {
        createThinIcon("ThinOpenInNew") {
            addPath(
                pathData = PathParser().parsePathString(OPEN_IN_NEW_PATH).toNodes(),
                fill = SolidColor(Color.White),
            )
        }
    }

    private const val OPEN_IN_NEW_PATH =
        "M5.31,20.5q-0.76,0-1.28-0.52T3.5,18.69V5.31q0-0.76 0.52-1.28T5.31,3.5h6.31V5H5.31Q5.19,5 5.1,5.1T5," +
        "5.31V18.69q0,0.12 0.1,0.21T5.31,19H18.69q0.12,0 0.21-0.1T19,18.69V12.38h1.5v6.31q0,0.76-0.52," +
        "1.28T18.69,20.5H5.31Zm4.41-5.17L8.67,14.28L17.95,5H14V3.5h6.5V10H19V6.05L9.72,15.33Z"

    val VisibilityOff: ImageVector by lazy {
        createThinIcon("ThinVisibilityOff") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 12f)
                curveTo(7f, 7.8f, 17f, 7.8f, 19.5f, 12f)
                curveTo(17f, 16.2f, 7f, 16.2f, 4.5f, 12f)
                close()
                moveTo(12f, 14.25f)
                curveTo(13.38f, 14.25f, 14.5f, 13.13f, 14.5f, 11.75f)
                curveTo(14.5f, 10.37f, 13.38f, 9.25f, 12f, 9.25f)
                curveTo(10.62f, 9.25f, 9.5f, 10.37f, 9.5f, 11.75f)
                curveTo(9.5f, 13.13f, 10.62f, 14.25f, 12f, 14.25f)
                close()
                moveTo(3.5f, 3.5f)
                lineTo(20.5f, 20.5f)
            }
        }
    }

    val ClickPassthrough: ImageVector by lazy { MaterialTouchIcons.SingleTap }

    /** 雪花：重冻应用（Material AcUnit） */
    val Snowflake: ImageVector get() = Icons.Outlined.AcUnit

    /** 自动亮度 */
    val BrightnessAuto: ImageVector get() = Icons.Outlined.BrightnessAuto

    /** 冰箱：打开冰箱管理 */
    val Fridge: ImageVector by lazy {
        createThinIcon("ThinFridge") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 4f)
                lineTo(17f, 4f)
                lineTo(17f, 20f)
                lineTo(7f, 20f)
                close()
                moveTo(7f, 10f)
                lineTo(17f, 10f)
                moveTo(13f, 6f)
                lineTo(13f, 8f)
                moveTo(13f, 12f)
                lineTo(13f, 17f)
            }
        }
    }

    /** 钉到屏幕 */
    val Pin: ImageVector by lazy {
        createThinIcon("ThinPin") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(16f, 4f)
                lineTo(20f, 8f)
                moveTo(14f, 6f)
                lineTo(9f, 11f)
                lineTo(5f, 12f)
                lineTo(12f, 19f)
                lineTo(13f, 15f)
                lineTo(18f, 10f)
                moveTo(8.5f, 15.5f)
                lineTo(4f, 20f)
            }
        }
    }

    /** 伪息屏 / 保持唤醒挂机 */
    val ScreenOffKeepAwake: ImageVector by lazy {
        createThinIcon("ThinScreenOffKeepAwake") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 4f)
                lineTo(18f, 4f)
                lineTo(18f, 20f)
                lineTo(6f, 20f)
                close()
                moveTo(12f, 9f)
                curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
                curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
                curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
                curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
                close()
                moveTo(12f, 17f)
                lineTo(12f, 17.5f)
            }
        }
    }

    /** 独立应用切换器（卡片轮播） */
    val AppCarouselSwitcher: ImageVector by lazy {
        createThinIcon("ThinAppCarouselSwitcher") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Central main card
                moveTo(8f, 5f)
                lineTo(16f, 5f)
                lineTo(16f, 19f)
                lineTo(8f, 19f)
                close()
                // Left partial card
                moveTo(5f, 7f)
                lineTo(5f, 17f)
                // Right partial card
                moveTo(19f, 7f)
                lineTo(19f, 17f)
            }
        }
    }

    /**
     * 悬浮球：球 + 下方一道投影弧（读作"浮在界面之上、可以拖着走"）。
     *
     * 原来借的是 `Icons.Outlined.Circle`：一个空心圆读不出「球」，
     * 还会和定位 / 圆点类图标混在一起，所以自绘一张。
     */
    val FloatBall: ImageVector by lazy {
        createThinIcon("ThinFloatBall") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 球：r = 3.6，圆心 (12, 10.6)
                moveTo(15.6f, 10.6f)
                curveTo(15.6f, 12.588f, 13.988f, 14.2f, 12f, 14.2f)
                curveTo(10.012f, 14.2f, 8.4f, 12.588f, 8.4f, 10.6f)
                curveTo(8.4f, 8.612f, 10.012f, 7f, 12f, 7f)
                curveTo(13.988f, 7f, 15.6f, 8.612f, 15.6f, 10.6f)
                close()
                // 投影弧
                moveTo(6.6f, 19.1f)
                curveTo(8.4f, 20.2f, 15.6f, 20.2f, 17.4f, 19.1f)
            }
        }
    }

    val RemoteControl2: ImageVector by lazy {
        createThinIcon("ThinRemoteControl2") {
            addPath(
                pathData = PathParser().parsePathString(
                    "M18 2C18.5523 2 19 2.44772 19 3V21C19 21.5523 18.5523 22 18 22H6C5.44772 22 5 21.5523 5 21V3C5 2.44772 5.44772 2 6 2H18ZM17 4H7V20H17V4ZM15 15V17H13V15H15ZM11 15V17H9V15H11ZM13 6V8H15V10H12.999L13 12H11L10.999 10H9V8H11V6H13Z",
                ).toNodes(),
                fill = strokeBrush,
            )
        }
    }

    val Joystick: ImageVector by lazy {
        createThinIcon("ThinJoystick") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 底座
                moveTo(4f, 19.5f)
                lineTo(20f, 19.5f)
                // 摇杆杆
                moveTo(12f, 19.5f)
                lineTo(12f, 13.5f)
                // 顶帽
                moveTo(12f, 6f)
                curveTo(14.2f, 6f, 16f, 7.8f, 16f, 10f)
                curveTo(16f, 12.2f, 14.2f, 14f, 12f, 14f)
                curveTo(9.8f, 14f, 8f, 12.2f, 8f, 10f)
                curveTo(8f, 7.8f, 9.8f, 6f, 12f, 6f)
                close()
            }
        }
    }

    val PenTablet: ImageVector by lazy {
        createThinIcon("ThinPenTablet") {
            // 与细线集同宽（原来是写死的 2f，比同组粗 33%）
            val strokeWidth = STROKE_WIDTH
            val paths = listOf(
                "M22 5V19C22 20.1046 21.1046 21 20 21H4C2.89543 21 2 20.1046 2 19V5C2 3.89543 2.89543 3 4 3H20C21.1046 3 22 3.89543 22 5Z",
                "M2 12H6",
                "M6 3V21",
                "M15.5 11.5L12 14.5",
                "M17 10.01L17.01 9.99889",
            )
            paths.forEach { pathData ->
                addPath(
                    pathData = PathParser().parsePathString(pathData).toNodes(),
                    stroke = strokeBrush,
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }

    val Drag: ImageVector by lazy {
        createThinIcon("ThinDrag") {
            addPath(
                pathData = PathParser().parsePathString(
                    "M12.0006 16.4998C12.3803 16.4998 12.6941 16.782 12.7437 17.148L12.7506 17.2498V19.4378L13.4703 18.7195C13.7365 18.4532 14.1532 18.429 14.4468 18.6469L14.5309 18.7195C14.7972 18.9857 14.8214 19.4024 14.6035 19.696L14.5309 19.7801L12.5309 21.7801L12.4928 21.8157L12.4206 21.8713L12.3258 21.9259L12.2392 21.961L12.1369 21.9874L12.052 21.9981H11.949L11.8292 21.9801L11.7613 21.9608L11.7024 21.9382L11.6315 21.9029L11.5803 21.871L11.549 21.8486C11.5212 21.8276 11.4949 21.8047 11.4703 21.7801L9.47025 19.7801C9.17736 19.4872 9.17736 19.0124 9.47025 18.7195C9.73652 18.4532 10.1532 18.429 10.4468 18.6469L10.5309 18.7195L11.2506 19.4388V17.2498C11.2506 16.8701 11.5327 16.5563 11.8988 16.5066L12.0006 16.4998ZM12.0006 8.9998C13.6574 8.9998 15.0006 10.3429 15.0006 11.9998C15.0006 13.6567 13.6574 14.9998 12.0006 14.9998C10.3437 14.9998 9.00058 13.6567 9.00058 11.9998C9.00058 10.3429 10.3437 8.9998 12.0006 8.9998ZM18.7203 9.46947C18.9865 9.20321 19.4032 9.179 19.6968 9.39685L19.7809 9.46947L21.7809 11.4695L21.8165 11.5076L21.8721 11.5798L21.9267 11.6746L21.9618 11.7611L21.9882 11.8635L21.9988 11.9484V12.0512L21.9808 12.1712L21.9616 12.2391L21.939 12.2979L21.9037 12.3688L21.8718 12.4201L21.8443 12.4581L21.7809 12.5301L19.7809 14.5301C19.488 14.823 19.0131 14.823 18.7203 14.5301C18.454 14.2639 18.4298 13.8472 18.6476 13.5536L18.7203 13.4695L19.4386 12.7498H17.2506C16.8709 12.7498 16.5571 12.4676 16.5074 12.1016L16.5006 11.9998C16.5006 11.6201 16.7827 11.3063 17.1488 11.2566L17.2506 11.2498H19.4396L18.7203 10.5301C18.454 10.2639 18.4298 9.8472 18.6476 9.55359L18.7203 9.46947ZM4.22025 9.46947C4.51315 9.17658 4.98802 9.17658 5.28091 9.46947C5.54718 9.73574 5.57139 10.1524 5.35353 10.446L5.28091 10.5301L4.56058 11.2498H6.75058C7.13028 11.2498 7.44407 11.532 7.49374 11.898L7.50058 11.9998C7.50058 12.3795 7.21843 12.6933 6.85235 12.743L6.75058 12.7498H4.56158L5.28091 13.4695C5.54718 13.7357 5.57139 14.1524 5.35353 14.446L5.28091 14.5301C5.01465 14.7964 4.59798 14.8206 4.30437 14.6028L4.22025 14.5301L2.22025 12.5301L2.12908 12.4199L2.07451 12.325L2.0394 12.2385L2.013 12.1361L2.00195 12.0452L2.00205 11.9527L2.02032 11.8284L2.03956 11.7605L2.06219 11.7017L2.09744 11.6308L2.12935 11.5795L2.15175 11.5482C2.17275 11.5204 2.19565 11.4941 2.22025 11.4695L4.22025 9.46947ZM12.0006 10.4998C11.1722 10.4998 10.5006 11.1714 10.5006 11.9998C10.5006 12.8282 11.1722 13.4998 12.0006 13.4998C12.829 13.4998 13.5006 12.8282 13.5006 11.9998C13.5006 11.1714 12.829 10.4998 12.0006 10.4998ZM11.8643 2.01222L11.9312 2.00297L12.018 2L12.078 2.00378L12.172 2.01954L12.2399 2.03878L12.2987 2.0614L12.3696 2.09666L12.4209 2.12857L12.4589 2.15607L12.5309 2.21947L14.5309 4.21947C14.8238 4.51237 14.8238 4.98724 14.5309 5.28013C14.2646 5.5464 13.848 5.57061 13.5544 5.35275L13.4703 5.28013L12.7506 4.5608V6.7498C12.7506 7.1295 12.4684 7.44329 12.1024 7.49296L12.0006 7.4998C11.6209 7.4998 11.3071 7.21765 11.2574 6.85157L11.2506 6.7498V4.5598L10.5309 5.28013C10.2646 5.5464 9.84798 5.57061 9.55437 5.35275L9.47025 5.28013C9.20399 5.01387 9.17978 4.5972 9.39764 4.30359L9.47025 4.21947L11.4703 2.21947L11.5805 2.1283L11.6754 2.07373L11.7619 2.03862L11.8643 2.01222Z",
                ).toNodes(),
                fill = strokeBrush,
            )
        }
    }

    val Favicon: ImageVector by lazy {
        createThinIcon("ThinFavicon") {
            val strokeWidth = 1.75f
            val paths = listOf(
                "M2 8a3 3 0 0 1 3 -3h14a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-14a3 3 0 0 1 -3 -3l0 -8",
                "M6 10v4",
                "M11 10a2 2 0 1 0 0 4",
                "M14 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            )
            paths.forEach { pathData ->
                addPath(
                    pathData = PathParser().parsePathString(pathData).toNodes(),
                    stroke = strokeBrush,
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }

    /**
     * Tabler `icons`：圆 / 三角 / 叉 / 方块四种形状，表达「一套图标」。
     * 用于扩展页「图标包」入口；描边 1.75f，与同组 Material Symbols 行观感一致。
     */
    val IconSet: ImageVector by lazy {
        createThinIcon("ThinIconSet") {
            val strokeWidth = 1.75f
            val paths = listOf(
                "M3 6.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0",
                "M2.5 21h8l-4 -7l-4 7",
                "M14 3l7 7",
                "M14 10l7 -7",
                "M14 14h7v7h-7l0 -7",
            )
            paths.forEach { pathData ->
                addPath(
                    pathData = PathParser().parsePathString(pathData).toNodes(),
                    stroke = strokeBrush,
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }

    /** 「阅读消息」：聊天气泡（与「打开通知栏」的铃铛区分开）。 */
    val ChatBubble: ImageVector by lazy {
        createThinIcon("ThinChatBubble") {
            addPath(
                pathData = PathParser().parsePathString(
                    "M7 5h10a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1 -2.5 2.5" +
                        "h-5.5l-3.5 3v-3h-1a2.5 2.5 0 0 1 -2.5 -2.5v-7a2.5 2.5 0 0 1 2.5 -2.5z",
                ).toNodes(),
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }

    /** 「清空通知」：垃圾桶（清空语义，避免和「关闭」的叉号混淆）。 */
    val ClearNotifications: ImageVector by lazy {
        createThinIcon("ThinClearNotifications") {
            val paths = listOf(
                "M4.5 6.5h15",
                "M9.5 6.5v-2a1 1 0 0 1 1 -1h3a1 1 0 0 1 1 1v2",
                "M6.5 6.5l0.8 12.2a1.6 1.6 0 0 0 1.6 1.3h6.2a1.6 1.6 0 0 0 1.6 -1.3l0.8 -12.2",
                "M10 10.5v6",
                "M14 10.5v6",
            )
            paths.forEach { pathData ->
                addPath(
                    pathData = PathParser().parsePathString(pathData).toNodes(),
                    stroke = strokeBrush,
                    strokeLineWidth = STROKE_WIDTH,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }
}
