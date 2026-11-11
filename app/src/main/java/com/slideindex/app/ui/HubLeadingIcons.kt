package com.slideindex.app.ui

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 消息 / 扩展 Hub 列表项的 leading icon（与首页同一套 Material Symbols，wght=350）。
 *
 * 与 [HomeLeadingIcons] 一样：`outlined` 参数只为兼容调用方保留，不再影响返回值。
 */
internal object HubLeadingIcons {
    @Suppress("UNUSED_PARAMETER")
    fun notifications(outlined: Boolean) = symbol("Notifications")

    @Suppress("UNUSED_PARAMETER")
    fun notificationHistory(outlined: Boolean) = symbol("History")

    @Suppress("UNUSED_PARAMETER")
    fun otpHub(outlined: Boolean) = symbol("Password")

    @Suppress("UNUSED_PARAMETER")
    fun layoutSettings(outlined: Boolean) = symbol("SortByAlpha")

    @Suppress("UNUSED_PARAMETER")
    fun quickLauncher(outlined: Boolean) = symbol("Apps")

    @Suppress("UNUSED_PARAMETER")
    fun honeycombLauncher(outlined: Boolean) = symbol("Hive")

    /** 圆环启动器：只有一张线稿（半圆 + 两道环 + 外带槽位格），没有 Material 对应字形。 */
    @Suppress("UNUSED_PARAMETER")
    fun ringLauncher(outlined: Boolean) = ThinActionIcons.RingLauncher

    @Suppress("UNUSED_PARAMETER")
    fun holographicLauncher(outlined: Boolean) = symbol("Globe")

    /** 应用内直达：窗口 + 外跳箭头（1.75 描边，替代旧 Material Launch 的 2dp）。 */
    @Suppress("UNUSED_PARAMETER")
    fun activityShortcut(outlined: Boolean): ImageVector = ThinActionIcons.OpenInNew

    @Suppress("UNUSED_PARAMETER")
    fun shellCommand(outlined: Boolean) = symbol("Code")

    @Suppress("UNUSED_PARAMETER")
    fun widgetPanel(outlined: Boolean) = symbol("Widgets")

    @Suppress("UNUSED_PARAMETER")
    fun floatingPointer(outlined: Boolean) = symbol("MyLocation")

    @Suppress("UNUSED_PARAMETER")
    fun stashClipboard(outlined: Boolean) = symbol("ContentPaste")

    @Suppress("UNUSED_PARAMETER")
    fun searchPanel(outlined: Boolean) = symbol("Search")

    @Suppress("UNUSED_PARAMETER")
    fun settingsBackup(outlined: Boolean) = symbol("Backup")

    @Suppress("UNUSED_PARAMETER")
    fun about(outlined: Boolean) = symbol("Info")
}

private fun symbol(name: String): ImageVector =
    requireNotNull(MaterialSymbolVectors.get(name)) { "unknown Material Symbol: $name" }
