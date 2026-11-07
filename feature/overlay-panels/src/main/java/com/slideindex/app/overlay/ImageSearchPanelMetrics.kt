package com.slideindex.app.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Centered image-search card: wider than legacy 400.dp pick cap, but not full screen. */
private val ImageSearchPanelMaxWidthCap = 520.dp
private const val ImageSearchPanelWidthScreenFraction = 0.9f

@Composable
fun imageSearchPanelMaxWidth(): Dp {
    val screenWidth = overlayContainerWidthDp()
    return minOf(ImageSearchPanelMaxWidthCap, screenWidth * ImageSearchPanelWidthScreenFraction)
}

@Composable
fun imageSearchPanelMaxHeight(): Dp =
    overlayContainerHeightDp() * overlayBottomPanelMaxHeightFraction()
