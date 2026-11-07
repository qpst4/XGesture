package com.slideindex.app.settings

import com.slideindex.app.design.AppThemeSpec

/**
 * `AppSettings` → 设计层的 [AppThemeSpec]。
 *
 * 放在 `:feature:settings` 而不是 `:app`：面板模块（`:feature:overlay-panels`）要算主题色，
 * 但不可能依赖 `:app`。`:app` 里的同名扩展只是转发，行为不变。
 */
fun AppSettings.toThemeSpec(systemDark: Boolean): AppThemeSpec = AppThemeSpec(
    customColorEnabled = customColorEnabled,
    themeColorArgb = themeColorArgb,
    dynamicColorEnabled = dynamicColorEnabled,
    paletteStyleId = themePaletteStyleId,
    colorSpecId = themeColorSpecId,
    darkBackgroundStyleId = darkBackgroundStyleId,
    dark = AppThemeMode.fromId(themeModeId).resolveIsDark(systemDark),
)
