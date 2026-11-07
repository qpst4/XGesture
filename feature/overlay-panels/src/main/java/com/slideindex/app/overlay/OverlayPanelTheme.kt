package com.slideindex.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

object OverlayPanelTheme {

    data class Colors(
        val panelBackground: Int,
        val panelShadow: Int,
        val cardBackground: Int,
        val cardBorder: Int,
        val cardHighlight: Int,
        val titleAccent: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textMuted: Int,
        val accent: Int,
        val accentSoft: Int,
        val accentOnSoft: Int,
        val onAccent: Int,
        val divider: Int,
        val scrimHeavy: Int,
        val scrimLight: Int,
        val rowHighlight: Int,
        val iconMuted: Int,
        val grip: Int,
        val error: Int
    )

    fun isDarkTheme(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun colors(context: Context): Colors =
        if (isDarkTheme(context)) dark() else light()

    private fun light() = Colors(
        panelBackground = Color.rgb(248, 249, 252),
        panelShadow = Color.argb(30, 0, 0, 0),
        cardBackground = Color.WHITE,
        cardBorder = Color.argb(56, 30, 136, 229),
        cardHighlight = Color.argb(48, 30, 136, 229),
        titleAccent = Color.rgb(30, 136, 229),
        textPrimary = Color.argb(230, 30, 30, 30),
        textSecondary = Color.argb(180, 50, 50, 80),
        textMuted = Color.argb(140, 80, 80, 80),
        accent = Color.rgb(0, 122, 255),
        accentSoft = Color.argb(28, 0, 122, 255),
        accentOnSoft = Color.rgb(0, 122, 255),
        onAccent = Color.WHITE,
        divider = Color.argb(40, 0, 0, 0),
        scrimHeavy = Color.argb(170, 0, 0, 0),
        scrimLight = Color.argb(90, 0, 0, 0),
        rowHighlight = Color.argb(28, 0, 0, 0),
        iconMuted = Color.argb(160, 60, 60, 60),
        grip = Color.argb(120, 120, 120, 120),
        error = Color.rgb(211, 47, 47)
    )

    private fun dark() = Colors(
        panelBackground = Color.rgb(34, 36, 42),
        panelShadow = Color.argb(72, 0, 0, 0),
        cardBackground = Color.rgb(48, 51, 58),
        cardBorder = Color.argb(72, 120, 180, 255),
        cardHighlight = Color.argb(56, 120, 180, 255),
        titleAccent = Color.rgb(130, 190, 255),
        textPrimary = Color.argb(235, 235, 240, 240),
        textSecondary = Color.argb(170, 180, 195, 210),
        textMuted = Color.argb(130, 140, 155, 170),
        accent = Color.rgb(64, 156, 255),
        accentSoft = Color.argb(36, 64, 156, 255),
        accentOnSoft = Color.rgb(130, 190, 255),
        onAccent = Color.rgb(16, 24, 36),
        divider = Color.argb(48, 255, 255, 255),
        scrimHeavy = Color.argb(190, 0, 0, 0),
        scrimLight = Color.argb(110, 0, 0, 0),
        rowHighlight = Color.argb(36, 255, 255, 255),
        iconMuted = Color.argb(180, 190, 200, 210),
        grip = Color.argb(100, 180, 180, 190),
        error = Color.rgb(255, 120, 120)
    )
}
