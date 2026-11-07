package com.slideindex.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 「支付码 / 扫码」四个动作的图标（实心填充版）。
 *
 * 矢量数据逆向自 MyGesture 7.2194P 的 `res/drawable/ic_wechat_scan|wecode3|ic_pay_code|ic_ali_scan`，
 * 逐条 path 与 fill 规则保持一致（`ic_pay_code` 的外框靠正反绕向掏空，不能改成多边形并集）。
 * 说明与出处见 `docs/reverse/mygesture-pay-actions/README.md`。
 *
 * 与 [ThinActionIcons] 的差别：这里是 1024 / 47.789 viewport 的实心图形（与 MyGesture 一致），
 * 不是 24×24 + stroke 1.5 的细线风格；渲染时统一由 `Icon(tint = …)` 上色。
 */
object PayQrActionIcons {

    /** 微信扫一扫（微信双气泡造型） */
    val WechatScan: ImageVector by lazy {
        ImageVector.Builder(
            name = "PayQrWechatScan",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 1024f,
            viewportHeight = 1024f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(206.2f, 416.2f)
                curveTo(205.1f, 476f, 215.9f, 566.1f, 145.7f, 566.1f)
                curveTo(97.1f, 566.1f, 96f, 507.4f, 96f, 507.4f)
                lineTo(64.8f, 219.5f)
                curveTo(64.8f, 219.5f, 42.1f, 68.5f, 257f, 64.1f)
                curveTo(471.9f, 59.8f, 494.6f, 247.7f, 494.6f, 247.7f)
                lineTo(676f, 247.7f)
                curveTo(676f, 247.7f, 718.1f, 256.4f, 717f, 307.4f)
                curveTo(715.9f, 358.5f, 681.4f, 357.4f, 681.4f, 357.4f)
                lineTo(258.1f, 357.4f)
                curveTo(258.1f, 357.5f, 207.3f, 356.4f, 206.2f, 416.2f)
                close()
                moveTo(764.5f, 966f)
                curveTo(549.6f, 970.3f, 527f, 782.3f, 527f, 782.3f)
                lineTo(345.5f, 782.3f)
                curveTo(345.5f, 782.3f, 303.4f, 773.6f, 304.5f, 722.5f)
                curveTo(305.6f, 671.4f, 340.1f, 672.5f, 340.1f, 672.5f)
                lineTo(763.4f, 672.5f)
                curveTo(763.4f, 672.5f, 814.2f, 673.6f, 816.3f, 613.8f)
                curveTo(817.4f, 554f, 806.6f, 463.9f, 876.8f, 463.9f)
                curveTo(925.4f, 463.9f, 926.5f, 522.6f, 926.5f, 522.6f)
                lineTo(957.8f, 810.5f)
                curveTo(956.8f, 810.6f, 979.5f, 961.6f, 764.5f, 966f)
                close()
                moveTo(764.5f, 966f)
            }
        }.build()
    }

    /** 微信付款码（扫描框+对勾） */
    val WechatPayCode: ImageVector by lazy {
        ImageVector.Builder(
            name = "PayQrWechatPayCode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 47.79f,
            viewportHeight = 47.79f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 18.16f)
                curveTo(4f, 19.35f, 4.97f, 20.32f, 6.16f, 20.32f)
                curveTo(7.35f, 20.32f, 8.31f, 19.35f, 8.31f, 18.16f)
                lineTo(8.31f, 12.21f)
                lineTo(14.57f, 12.21f)
                curveTo(15.76f, 12.21f, 16.72f, 11.24f, 16.72f, 10.05f)
                curveTo(16.72f, 8.86f, 15.76f, 7.9f, 14.57f, 7.9f)
                lineTo(6.16f, 7.9f)
                curveTo(4.97f, 7.9f, 4f, 8.86f, 4f, 10.05f)
                lineTo(4f, 18.16f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(16.9f, 22.47f)
                curveTo(16.06f, 21.63f, 14.7f, 21.63f, 13.86f, 22.47f)
                curveTo(13.02f, 23.31f, 13.02f, 24.67f, 13.86f, 25.51f)
                lineTo(20.12f, 31.77f)
                lineTo(20.12f, 31.77f)
                curveTo(20.96f, 32.61f, 22.32f, 32.61f, 23.16f, 31.77f)
                lineTo(23.18f, 31.76f)
                lineTo(34.85f, 20.09f)
                curveTo(35.69f, 19.25f, 35.69f, 17.89f, 34.85f, 17.04f)
                curveTo(34.01f, 16.2f, 32.64f, 16.2f, 31.8f, 17.04f)
                lineTo(21.64f, 27.2f)
                lineTo(16.91f, 22.47f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(39.48f, 18.16f)
                curveTo(39.48f, 19.35f, 40.44f, 20.32f, 41.63f, 20.32f)
                curveTo(42.82f, 20.32f, 43.79f, 19.35f, 43.79f, 18.16f)
                lineTo(43.79f, 10.05f)
                curveTo(43.79f, 8.86f, 42.82f, 7.9f, 41.63f, 7.9f)
                lineTo(33.22f, 7.9f)
                curveTo(32.03f, 7.9f, 31.06f, 8.86f, 31.06f, 10.05f)
                curveTo(31.06f, 11.24f, 32.03f, 12.21f, 33.22f, 12.21f)
                lineTo(39.48f, 12.21f)
                lineTo(39.48f, 18.16f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(8.31f, 29.63f)
                curveTo(8.31f, 28.44f, 7.35f, 27.47f, 6.16f, 27.47f)
                curveTo(4.97f, 27.47f, 4f, 28.44f, 4f, 29.63f)
                lineTo(4f, 37.74f)
                curveTo(4f, 38.93f, 4.97f, 39.89f, 6.16f, 39.89f)
                lineTo(14.57f, 39.89f)
                curveTo(15.76f, 39.89f, 16.72f, 38.93f, 16.72f, 37.74f)
                curveTo(16.72f, 36.55f, 15.76f, 35.58f, 14.57f, 35.58f)
                lineTo(8.31f, 35.58f)
                lineTo(8.31f, 29.63f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(43.79f, 29.63f)
                curveTo(43.79f, 28.44f, 42.82f, 27.47f, 41.63f, 27.47f)
                curveTo(40.44f, 27.47f, 39.48f, 28.44f, 39.48f, 29.63f)
                lineTo(39.48f, 35.58f)
                lineTo(33.22f, 35.58f)
                curveTo(32.03f, 35.58f, 31.06f, 36.55f, 31.06f, 37.74f)
                curveTo(31.06f, 38.93f, 32.03f, 39.89f, 33.22f, 39.89f)
                lineTo(41.63f, 39.89f)
                curveTo(42.82f, 39.89f, 43.79f, 38.93f, 43.79f, 37.74f)
                lineTo(43.79f, 29.63f)
                close()
            }
        }.build()
    }

    /** 支付宝付款码（条码框） */
    val AlipayPayCode: ImageVector by lazy {
        ImageVector.Builder(
            name = "PayQrAlipayPayCode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 1024f,
            viewportHeight = 1024f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(64.2f, 117.7f)
                lineTo(64.2f, 909.7f)
                lineTo(958.9f, 909.7f)
                lineTo(958.9f, 117.7f)
                lineTo(64.2f, 117.7f)
                close()
                moveTo(870.9f, 821.7f)
                lineTo(152.2f, 821.7f)
                lineTo(152.2f, 205.7f)
                lineTo(870.9f, 205.7f)
                lineTo(870.9f, 821.7f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(254.9f, 337.7f)
                lineTo(313.6f, 337.7f)
                lineTo(313.6f, 689.7f)
                lineTo(254.9f, 689.7f)
                close()
                moveTo(460.2f, 337.7f)
                lineTo(518.9f, 337.7f)
                lineTo(518.9f, 689.7f)
                lineTo(460.2f, 689.7f)
                close()
                moveTo(650.9f, 337.7f)
                lineTo(738.9f, 337.7f)
                lineTo(738.9f, 689.7f)
                lineTo(650.9f, 689.7f)
                close()
                moveTo(584.9f, 337.7f)
                lineTo(599.6f, 337.7f)
                lineTo(599.6f, 689.7f)
                lineTo(584.9f, 689.7f)
                close()
                moveTo(357.5f, 337.7f)
                lineTo(386.8f, 337.7f)
                lineTo(386.8f, 689.7f)
                lineTo(357.5f, 689.7f)
                close()
            }
        }.build()
    }

    /** 支付宝扫一扫（取景框） */
    val AlipayScan: ImageVector by lazy {
        ImageVector.Builder(
            name = "PayQrAlipayScan",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 1024f,
            viewportHeight = 1024f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(332.8f, 890.88f)
                lineTo(156.67f, 890.88f)
                curveTo(143.36f, 890.88f, 133.12f, 880.64f, 133.12f, 867.33f)
                lineTo(133.12f, 688.13f)
                curveTo(133.12f, 666.11f, 117.76f, 650.75f, 95.74f, 650.75f)
                curveTo(73.72f, 650.75f, 58.36f, 666.11f, 58.36f, 688.13f)
                lineTo(58.36f, 867.33f)
                curveTo(58.36f, 921.09f, 102.39f, 965.12f, 156.15f, 965.12f)
                lineTo(332.8f, 965.12f)
                curveTo(350.72f, 965.12f, 370.18f, 950.78f, 370.18f, 927.74f)
                curveTo(370.18f, 906.24f, 354.82f, 890.88f, 332.8f, 890.88f)
                close()
                moveTo(867.33f, 58.88f)
                lineTo(688.13f, 58.88f)
                curveTo(666.11f, 58.88f, 650.75f, 74.24f, 650.75f, 96.26f)
                curveTo(650.75f, 118.28f, 666.11f, 133.64f, 688.13f, 133.64f)
                lineTo(867.33f, 133.64f)
                curveTo(880.64f, 133.64f, 890.88f, 143.88f, 890.88f, 157.19f)
                lineTo(890.88f, 332.8f)
                curveTo(890.88f, 354.82f, 906.24f, 370.18f, 928.26f, 370.18f)
                curveTo(950.28f, 370.18f, 965.64f, 354.82f, 965.64f, 332.8f)
                lineTo(965.64f, 156.67f)
                curveTo(965.13f, 102.91f, 921.1f, 58.88f, 867.34f, 58.88f)
                close()
                moveTo(96.26f, 369.66f)
                curveTo(118.28f, 369.66f, 133.64f, 354.3f, 133.64f, 332.28f)
                lineTo(133.64f, 156.67f)
                curveTo(133.64f, 143.36f, 143.88f, 133.12f, 157.19f, 133.12f)
                lineTo(332.8f, 133.12f)
                curveTo(354.82f, 133.12f, 370.18f, 117.76f, 370.18f, 95.74f)
                curveTo(370.18f, 73.72f, 354.82f, 58.36f, 332.8f, 58.36f)
                lineTo(156.67f, 58.36f)
                curveTo(102.91f, 58.36f, 58.88f, 102.39f, 58.88f, 156.15f)
                lineTo(58.88f, 332.8f)
                curveTo(58.88f, 354.82f, 74.24f, 369.66f, 96.26f, 369.66f)
                close()
                moveTo(928.26f, 650.75f)
                curveTo(906.24f, 650.75f, 890.88f, 666.11f, 890.88f, 688.13f)
                lineTo(890.88f, 867.33f)
                curveTo(890.88f, 880.64f, 880.64f, 890.88f, 867.33f, 890.88f)
                lineTo(685.06f, 890.88f)
                curveTo(663.04f, 890.88f, 647.68f, 906.24f, 647.68f, 928.26f)
                curveTo(647.68f, 950.28f, 663.04f, 965.64f, 685.06f, 965.64f)
                lineTo(867.33f, 965.64f)
                curveTo(921.09f, 965.64f, 965.12f, 921.61f, 965.12f, 867.85f)
                lineTo(965.12f, 688.65f)
                curveTo(965.12f, 666.12f, 949.76f, 650.76f, 928.26f, 650.76f)
                close()
                moveTo(928.26f, 474.62f)
                lineTo(96.26f, 474.62f)
                curveTo(74.24f, 474.62f, 58.88f, 489.98f, 58.88f, 512f)
                curveTo(58.88f, 534.02f, 74.24f, 549.38f, 96.26f, 549.38f)
                lineTo(928.26f, 549.38f)
                curveTo(950.28f, 549.38f, 965.64f, 534.02f, 965.64f, 512f)
                curveTo(965.13f, 489.98f, 949.77f, 474.62f, 928.26f, 474.62f)
                close()
            }
        }.build()
    }
}
