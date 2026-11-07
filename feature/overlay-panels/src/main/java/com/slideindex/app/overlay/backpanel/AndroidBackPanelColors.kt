package com.slideindex.app.overlay.backpanel

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.slideindex.app.settings.AndroidBackColorSource
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.androidBackColorSource
import com.slideindex.app.settings.toThemeSpec
import com.slideindex.app.ui.miuix.theme.resolveAppMaterialColorScheme

internal object AndroidBackPanelColors {
    data class Paints(val background: Int, val arrow: Int)

    fun resolve(context: Context, settings: AppSettings): Paints {
        val systemDark = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val scheme = when (settings.androidBackColorSource()) {
            AndroidBackColorSource.SYSTEM ->
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            AndroidBackColorSource.APP_THEME -> {
                val dark = AppThemeMode.fromId(settings.themeModeId).resolveIsDark(systemDark)
                resolveAppMaterialColorScheme(context, settings.toThemeSpec(dark))
            }
        }
        return Paints(
            background = scheme.secondaryContainer.toArgb(),
            arrow = scheme.onSecondaryContainer.toArgb(),
        )
    }
}
