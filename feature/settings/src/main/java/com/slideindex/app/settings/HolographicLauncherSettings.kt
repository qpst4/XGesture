package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.overlay.layout.PanelIconShape

/**
 * 全息启动器「球面上显示哪些应用」的三档模式。
 *
 * 与「分应用代理」那类设置同构：**模式**决定怎么用**那一份选中列表**，两者分开存。
 * 这样用户在「仅显示选中」里挑完之后切到「排除选中」，选择结果仍在，不会出现
 * 「两种模式都添加过、到底听谁的」这种说不清的状态。
 */
enum class HolographicAppMode(val id: Int) {
    /** 全部已安装应用（减去 [HolographicLauncherSettings.hiddenAppPackages] 的旧黑名单）。 */
    ALL(0),

    /** 只显示选中列表（为空时球面没有应用，会给出提示而不是"点了没反应"）。 */
    SELECTED_ONLY(1),

    /** 显示全部已安装应用，但排除选中列表。 */
    EXCLUDE_SELECTED(2),
    ;

    companion object {
        val DEFAULT = ALL

        fun fromId(id: Int?): HolographicAppMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class HolographicLauncherSettings(
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val rotationSensitivity: Float = DEFAULT_ROTATION_SENSITIVITY,
    val hapticLevel: Int = DEFAULT_HAPTIC_LEVEL,
    /** 球面应用来源的三档模式，决定 [selectedAppPackages] 怎么用。 */
    val appMode: HolographicAppMode = HolographicAppMode.DEFAULT,
    /**
     * 用户挑中的那一份应用（「仅显示选中」= 白名单 / 「排除选中」= 黑名单），按挑选顺序排列。
     *
     * 顺序有意义：球面按列表顺序用黄金角铺点，顺序稳定用户才能形成肌肉记忆。
     */
    val selectedAppPackages: List<String> = emptyList(),
    val backgroundStyle: Int = DEFAULT_BACKGROUND_STYLE,
    val iconShape: PanelIconShape = PanelIconShape.FOLLOW_GLOBAL,
    val blurDp: Int = DEFAULT_BLUR_DP,
    val dimPercent: Int = DEFAULT_DIM_PERCENT,
) {
    companion object {
        /** 选中列表的长度上限：球面本身只显示前 [BALLOON_APP_LIMIT] 个，存更多没有意义。 */
        const val MAX_APP_PACKAGES = 500

        /** 与 `Ball3DView.MAX_BALL_APPS` 一致。 */
        const val BALLOON_APP_LIMIT = 60
        const val BACKGROUND_BLUR = HoneycombDisplaySettings.BACKGROUND_BLUR
        const val BACKGROUND_BLACK = HoneycombDisplaySettings.BACKGROUND_BLACK
        const val BACKGROUND_WALLPAPER_BLUR = HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR

        const val DEFAULT_TIMEOUT_SECONDS = 30
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 300
        /** 按 dp 换算；跟手档约 0.012～0.016，MEIZU 21 上 100px 横向约 25°～35° */
        const val DEFAULT_ROTATION_SENSITIVITY = 0.014f
        const val MIN_ROTATION_SENSITIVITY = 0.001f
        const val MAX_ROTATION_SENSITIVITY = 0.05f
        const val DEFAULT_HAPTIC_LEVEL = 1

        const val DEFAULT_BACKGROUND_STYLE = BACKGROUND_BLUR
        const val DEFAULT_BLUR_DP = HoneycombDisplaySettings.DEFAULT_BLUR_DP
        const val MIN_BLUR_DP = HoneycombDisplaySettings.MIN_BLUR_DP
        const val MAX_BLUR_DP = HoneycombDisplaySettings.MAX_BLUR_DP
        const val DEFAULT_DIM_PERCENT = HoneycombDisplaySettings.DEFAULT_DIM_PERCENT
        const val MIN_DIM_PERCENT = HoneycombDisplaySettings.MIN_DIM_PERCENT
        const val MAX_DIM_PERCENT = HoneycombDisplaySettings.MAX_DIM_PERCENT

        fun fromPreferences(prefs: Preferences): HolographicLauncherSettings {
            val timeout = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_TIMEOUT_SECONDS] ?: DEFAULT_TIMEOUT_SECONDS
            val sensitivity = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_ROTATION_SENSITIVITY]
                ?: DEFAULT_ROTATION_SENSITIVITY
            val haptic = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_HAPTIC_LEVEL] ?: DEFAULT_HAPTIC_LEVEL
            val appMode = HolographicAppMode.fromId(
                prefs[SettingsPreferenceKeys.HOLOGRAPHIC_APP_MODE]
            )
            // 列表的顺序就是用户挑选的顺序，所以存成有序编码串而不是 Set（Set 会打乱顺序，
            // 会让球面上的应用位置每次启动都变）。
            val selected = HolographicLauncherAppPackagesCodec.decode(
                prefs[SettingsPreferenceKeys.HOLOGRAPHIC_LAUNCHER_APP_PACKAGES]
            )
            val backgroundStyle = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_BACKGROUND_STYLE]
                ?: DEFAULT_BACKGROUND_STYLE
            val blurDp = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_BLUR_DP] ?: DEFAULT_BLUR_DP
            val dimPercent = prefs[SettingsPreferenceKeys.HOLOGRAPHIC_DIM_PERCENT] ?: DEFAULT_DIM_PERCENT
            val iconShape = PanelIconShape.fromName(prefs[SettingsPreferenceKeys.HOLOGRAPHIC_ICON_SHAPE])
            return HolographicLauncherSettings(
                timeoutSeconds = timeout.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
                rotationSensitivity = sensitivity.coerceIn(MIN_ROTATION_SENSITIVITY, MAX_ROTATION_SENSITIVITY),
                hapticLevel = haptic.coerceIn(0, 3),
                appMode = appMode,
                selectedAppPackages = selected,
                backgroundStyle = backgroundStyle.coerceIn(BACKGROUND_BLUR, BACKGROUND_WALLPAPER_BLUR),
                iconShape = iconShape,
                blurDp = blurDp.coerceIn(MIN_BLUR_DP, MAX_BLUR_DP),
                dimPercent = dimPercent.coerceIn(MIN_DIM_PERCENT, MAX_DIM_PERCENT),
            )
        }
    }
}
