package com.slideindex.app.design

/**
 * 主题解析的**纯输入**：不依赖 `AppSettings`。
 *
 * 为什么要有它：配色解析原先直接吃 `AppSettings`，而 `AppSettings` 在 `:feature:settings` 里，
 * 于是 `:app` 的 overlay 面板只要算一次主题色，就被钉死在 `:app` 里，搬不出去。
 * 现在面板侧只需要拿到这个值对象（调用方把 settings 映射过来），设计层可以独立。
 *
 * 字段都是原始类型（ID / ARGB），不强依赖 settings 的枚举，方便跨模块传递。
 */
data class AppThemeSpec(
    val customColorEnabled: Boolean = false,
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val dynamicColorEnabled: Boolean = false,
    /** `ThemePaletteStyle.id`。 */
    val paletteStyleId: Int = 0,
    /** `AppColorSpec.id`。 */
    val colorSpecId: Int = 0,
    /** `DarkBackgroundStyle.id`。 */
    val darkBackgroundStyleId: Int = 0,
    /** 已按 `AppThemeMode` 解析过的最终明暗。 */
    val dark: Boolean = false,
)
