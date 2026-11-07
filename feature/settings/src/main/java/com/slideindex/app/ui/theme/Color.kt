package com.slideindex.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Purple40 = Color(0xFF6750A4)
val PurpleGrey40 = Color(0xFF625B71)
val SurfaceDark = Color(0xFF1C1B1F)
val SurfaceLight = Color(0xFFFEF7FF)

fun expressiveLightColorScheme(seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = seed.copy(alpha = 0.14f),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = PurpleGrey40,
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1B1F),
    // Stronger surface-container steps so SegmentedListItem / cards read clearly on
    // surface in light mode (parity with dark theme elevation contrast).
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2ECF6),
    surfaceContainer = Color(0xFFE8E2EC),
    surfaceContainerHigh = Color(0xFFDED8E2),
    surfaceContainerHighest = Color(0xFFD4CED8),
    surfaceVariant = Color(0xFFDDD6E2),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFB8B2BE),
)

fun expressiveDarkColorScheme(seed: Color) = darkColorScheme(
    primary = seed.copy(alpha = 0.88f).let { Color(it.red, it.green, it.blue, 1f) },
    onPrimary = Color(0xFF381E72),
    primaryContainer = seed.copy(alpha = 0.24f),
    onPrimaryContainer = Purple80,
    secondary = PurpleGrey80,
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)
