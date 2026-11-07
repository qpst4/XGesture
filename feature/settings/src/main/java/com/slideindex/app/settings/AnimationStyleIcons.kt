package com.slideindex.app.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.slideindex.app.ui.ThinActionIcons

@Composable
fun waveStyleIconPainter(iconType: Int, isLong: Boolean = false): Painter = when (iconType) {
    WaveStyle.ICON_TYPE_TRIANGLE ->
        if (isLong) rememberVectorPainter(ThinActionIcons.DoubleArrowRight)
        else rememberVectorPainter(Icons.Default.PlayArrow)
    WaveStyle.ICON_TYPE_ANGLE ->
        if (isLong) rememberVectorPainter(ThinActionIcons.DoubleArrowRight)
        else rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForwardIos)
    WaveStyle.ICON_TYPE_ARROW_NEW ->
        if (isLong) rememberVectorPainter(ThinActionIcons.DoubleArrowRight)
        else rememberVectorPainter(Icons.AutoMirrored.Filled.Forward)
    else ->
        if (isLong) rememberVectorPainter(ThinActionIcons.DoubleArrowRight)
        else rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward)
}

@Composable
fun WaveStyle.painterIcon(isLong: Boolean = false): Painter = waveStyleIconPainter(iconType, isLong)

@Composable
fun CapsuleStyle.painterIcon(isLong: Boolean = false): Painter = waveStyleIconPainter(iconType, isLong)

@Composable
fun BubbleStyle.painterIcon(isLong: Boolean = false): Painter = waveStyleIconPainter(iconType, isLong)
