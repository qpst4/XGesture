package com.slideindex.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density

/** 界面缩放前的平台 [Density]，用于宽屏阈值等量宽逻辑（对齐 Mishka）。 */
val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }
