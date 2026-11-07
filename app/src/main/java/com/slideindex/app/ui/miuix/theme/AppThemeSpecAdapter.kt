package com.slideindex.app.ui.miuix.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.toThemeSpec

/**
 * `:app` 侧的适配层：把 `AppSettings` 映射成设计层的 [AppThemeSpec]。
 *
 * 保留原来的函数签名（`context, settings, dark`），所以 6 个调用点一行都不用改；
 * 真正的解析逻辑在 `:core:design` 里，`settings → spec` 的映射在 `:feature:settings`。
 */

fun resolveAppMaterialColorScheme(
    context: Context,
    settings: AppSettings,
    darkTheme: Boolean,
): ColorScheme = resolveAppMaterialColorScheme(context, settings.toThemeSpec(darkTheme))

fun resolveAppAccentArgb(context: Context, settings: AppSettings): Int {
    val systemDark =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    return resolveAppAccentArgb(context, settings.toThemeSpec(systemDark))
}

fun resolveAppAccentArgb(context: Context, settings: AppSettings, dark: Boolean): Int =
    resolveAppAccentArgb(context, settings.toThemeSpec(dark))

/** Compose 侧入口：系统深色由 [isSystemInDarkTheme] 提供，并跟着设置变化重算。 */
@Composable
fun rememberAppAccentColor(settings: AppSettings): Color {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    return remember(
        context,
        settings.customColorEnabled,
        settings.themeColorArgb,
        settings.dynamicColorEnabled,
        settings.themeModeId,
        settings.darkBackgroundStyleId,
        settings.themePaletteStyleId,
        settings.themeColorSpecId,
        systemDark,
    ) {
        Color(resolveAppAccentArgb(context, settings, settings.darkFor(systemDark)))
    }
}

private fun AppSettings.darkFor(systemDark: Boolean): Boolean =
    AppThemeMode.fromId(themeModeId).resolveIsDark(systemDark)
