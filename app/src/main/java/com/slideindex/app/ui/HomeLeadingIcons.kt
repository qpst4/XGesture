package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 首页列表行的行首图标。
 *
 * 已统一到 Material Symbols（wght=350，线性），与设置子页的 [SymbolsIcon] 同一套字形；
 * `outlined` 参数只为兼容调用方保留（Symbols 只有线性一档），不再影响返回值。
 *
 * 两个例外：方向性图标仍用 `Icons.AutoMirrored.*`（Symbols 不会按 RTL 自动镜像）。
 */
internal object HomeLeadingIcons {
    @Suppress("UNUSED_PARAMETER")
    fun gesture(outlined: Boolean) = symbol("Gesture")

    @Suppress("UNUSED_PARAMETER")
    fun batteryKeepAlive(outlined: Boolean) = HomeCustomIcons.AppKeepAlive

    @Suppress("UNUSED_PARAMETER")
    fun privilegeMode(outlined: Boolean) = symbol("Security")

    @Suppress("UNUSED_PARAMETER")
    fun cornerWheel(outlined: Boolean) = HomeCustomIcons.CornerWheel

    @Suppress("UNUSED_PARAMETER")
    fun triggerCollection(outlined: Boolean) = symbol("TouchApp")

    /**
     * 「按应用分档」。
     *
     * ⚠️ 不能和 [triggerCollection] 共用一个图标：两行在同一张卡里相邻，图标一样就分不出谁是谁
     * （用户提过）。也不能用 androidx 的 `Icons.*.DashboardCustomize`——旧 Material Icons 里那个
     * 画的是"九宫格 + 加号"，而用户要的是 Material Symbols 新版"九宫格 + 铅笔"，所以自绘见
     * [HomeCustomIcons.DashboardCustomizeOutlined]。
     */
    @Suppress("UNUSED_PARAMETER")
    fun gestureAppProfiles(outlined: Boolean) = HomeCustomIcons.DashboardCustomizeOutlined

    @Suppress("UNUSED_PARAMETER")
    fun floatBall(outlined: Boolean) = symbol("Circle")

    @Suppress("UNUSED_PARAMETER")
    fun gestureAngle(outlined: Boolean) = symbol("Tune")

    @Suppress("UNUSED_PARAMETER")
    fun gestureAnimation(outlined: Boolean) = symbol("Animation")

    /** 方向性图标：Symbols 不会自动镜像，继续用 AutoMirrored。 */
    @Suppress("UNUSED_PARAMETER")
    fun systemBackGestureWidth(outlined: Boolean): ImageVector =
        if (outlined) Icons.AutoMirrored.Outlined.ArrowBack else Icons.AutoMirrored.Filled.ArrowBack

    @Suppress("UNUSED_PARAMETER")
    fun excludedApps(outlined: Boolean) = symbol("Block")

    @Suppress("UNUSED_PARAMETER")
    fun previousAppBlacklist(outlined: Boolean) = symbol("SwapHoriz")

    @Suppress("UNUSED_PARAMETER")
    fun hideTriggerLandscape(outlined: Boolean) = symbol("ScreenRotation")

    @Suppress("UNUSED_PARAMETER")
    fun hideTriggerLock(outlined: Boolean) = symbol("Lock")

    @Suppress("UNUSED_PARAMETER")
    fun hideTriggerLauncher(outlined: Boolean) = symbol("Home")

    @Suppress("UNUSED_PARAMETER")
    fun freeWindow(outlined: Boolean) = symbol("PictureInPictureAlt")

    @Suppress("UNUSED_PARAMETER")
    fun keyboardTrigger(outlined: Boolean) = symbol("Keyboard")

    @Suppress("UNUSED_PARAMETER")
    fun haptic(outlined: Boolean) = symbol("Vibration")

    @Suppress("UNUSED_PARAMETER")
    fun themeMode(outlined: Boolean) = symbol("BrightnessMedium")

    @Suppress("UNUSED_PARAMETER")
    fun themeSeedColor(outlined: Boolean) = symbol("Colorize")

    @Suppress("UNUSED_PARAMETER")
    fun themePalette(outlined: Boolean) = symbol("Style")

    @Suppress("UNUSED_PARAMETER")
    fun themeColorSpec(outlined: Boolean) = symbol("Contrast")
}

private fun symbol(name: String): ImageVector =
    requireNotNull(MaterialSymbolVectors.get(name)) { "unknown Material Symbol: $name" }
