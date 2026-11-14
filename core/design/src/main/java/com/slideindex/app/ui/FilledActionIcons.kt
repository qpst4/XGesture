package com.slideindex.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material Symbols 实心图标（path 数据取自 fonts.google.com/icons）。
 *
 * 只用于网络相关的四个动作：Wi-Fi / 热点 / 移动数据 / 网络连接面板。
 * 其余动作用 [ThinActionIcons] 的 1.5 描边细线风格；这里刻意保持同组一致，
 * 避免同一排里一半细线一半实心。
 */
object FilledActionIcons {

    private fun createFilledIcon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(pathData),
                fill = SolidColor(Color.Black),
            )
        }.build()

    val Wifi: ImageVector by lazy {
        createFilledIcon(
            "FilledWifi",
            "M10.57,20.13q-0.59-0.59-0.59-1.43t0.59-1.43T12,16.67t1.43,0.59t0.59,1.43t-0.59,1.43" +
                "T12,20.71t-1.43-0.59ZM6.35,15.35L4.79,13.76q1.39-1.39 3.23-2.19T12,10.77t3.98,0.81" +
                "t3.23,2.21l-1.56,1.56q-1.1-1.1-2.55-1.72T12,13T8.9,13.63T6.35,15.35ZM2.1,11.1L0.54,9.54" +
                "q2.19-2.22 5.12-3.5T12,4.77t6.34,1.27t5.12,3.5L21.9,11.1q-1.93-1.93-4.46-3.01T12,7" +
                "T6.56,8.09T2.1,11.1Z",
        )
    }

    val WifiTethering: ImageVector by lazy {
        createFilledIcon(
            "FilledWifiTethering",
            "M5.46,19.89q-1.37-1.31-2.16-3.08T2.5,13q0-1.97 0.75-3.71T5.28,6.28T8.29,4.25T12,3.5" +
                "T15.7,4.25t3.02,2.03T20.75,9.3T21.5,13q0,2.04-0.79,3.82t-2.16,3.07L17.5,18.84" +
                "q1.15-1.1 1.83-2.61T20,13q0-3.35-2.32-5.68T12,5T6.33,7.32T4,13q0,1.72 0.67,3.23" +
                "t1.85,2.6L5.46,19.89Zm2.83-2.83q-0.82-0.76-1.3-1.8T6.5,13q0-2.29 1.6-3.9T12,7.5" +
                "t3.9,1.61T17.5,13q0,1.22-0.48,2.27t-1.3,1.8L14.65,16q0.63-0.58 0.99-1.35T16,13" +
                "q0-1.65-1.17-2.83T12,9T9.18,10.17T8,13q0,0.9 0.36,1.66T9.35,16L8.28,17.07Zm2.66-3.01" +
                "Q10.5,13.61 10.5,13t0.44-1.06T12,11.5t1.06,0.44T13.5,13t-0.44,1.06T12,14.5t-1.06-0.44Z",
        )
    }

    val MobileData: ImageVector by lazy {
        createFilledIcon(
            "FilledMobileData",
            "M5.5,19.5v-5H7.81v5H5.5Zm5.9,0V9.5h2.31v10H11.4Zm5.79,0V4.5H19.5v15H17.19Z",
        )
    }

    val InternetPanel: ImageVector by lazy {
        createFilledIcon(
            "FilledInternetPanel",
            "M8.3,20.75Q6.56,20 5.28,18.72T3.25,15.71T2.5,12T3.25,8.3T5.28,5.28T8.29,3.25T12,2.5" +
                "T15.7,3.25t3.02,2.03t2.03,3.02T21.5,12T20.75,15.7t-2.03,3.02t-3.02,2.03T12,21.5" +
                "T8.3,20.75Zm2.7-0.8V18q-0.82,0-1.41-0.59T9,16V15L4.2,10.2q-0.08,0.45-0.14,0.9T4,12" +
                "q0,3.03 1.99,5.3T11,19.95Zm6.9-2.55q1.02-1.13 1.56-2.51T20,12q0-2.46-1.36-4.49T15,4.58" +
                "V5q0,0.82-0.59,1.41T13,7H11V9q0,0.42-0.29,0.71T10,10H8v2h6q0.43,0 0.71,0.29T15,13v3h1" +
                "q0.65,0 1.18,0.39T17.9,17.4Z",
        )
    }
}
