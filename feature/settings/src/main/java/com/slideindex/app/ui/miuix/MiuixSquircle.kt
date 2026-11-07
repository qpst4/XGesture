package com.slideindex.app.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleSurface

/**
 * 浮层 / graphicsLayer 录制等场景可能落到软件 Canvas，RuntimeShader squircle 会崩溃。
 * 在 overlay 根节点设为 false，或依赖 [canUseMiuixSquircle] 自动回退圆角矩形。
 */
val LocalMiuixSquircleEnabled = compositionLocalOf { true }

@Composable
fun canUseMiuixSquircle(): Boolean =
    LocalMiuixSquircleEnabled.current &&
        isRuntimeShaderSupported() &&
        LocalView.current.isHardwareAccelerated

@Composable
fun Modifier.miuixSquircleSurface(
    color: Color,
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Modifier {
    if (topStart == 0.dp && topEnd == 0.dp && bottomEnd == 0.dp && bottomStart == 0.dp) {
        return background(color)
    }
    return if (canUseMiuixSquircle()) {
        squircleSurface(
            color = color,
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
        )
    } else {
        clip(
            RoundedCornerShape(
                topStart = topStart,
                topEnd = topEnd,
                bottomEnd = bottomEnd,
                bottomStart = bottomStart,
            ),
        ).background(color)
    }
}
