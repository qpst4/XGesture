package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.slideindex.app.settings.TopAppBarBlurStyle
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 顶栏毛玻璃算法，由 [ModuleTheme] 从用户设置注入。 */
val LocalTopAppBarBlurStyle = staticCompositionLocalOf { TopAppBarBlurStyle.PROGRESSIVE }

/**
 * 浮层等场景由 [MiuixOverlayComposeLocals] 设为 false，避免软件 Canvas 上绘制 RuntimeShader。
 */
val LocalMiuixBlurBackdropEnabled = compositionLocalOf { true }

/** 与当前设置页顶栏 [rememberMiuixBlurBackdrop] 共用，供 `scrollContent = false` 时嵌套列表参与毛玻璃采样。 */
val LocalMiuixScreenBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
fun Modifier.miuixScreenListBackdrop(): Modifier {
    val backdrop = LocalMiuixScreenBackdrop.current
    return then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
}

@Composable
fun rememberMiuixBlurBackdrop(enabled: Boolean = true): LayerBackdrop? {
    // 与 Mishka 一致：真正 textureBlur 依赖 RuntimeShader（API 33+），更低版本返回 null → 纯色栏。
    if (
        !enabled ||
        !LocalMiuixBlurBackdropEnabled.current ||
        !isRuntimeShaderSupported() ||
        !LocalView.current.isHardwareAccelerated
    ) {
        return null
    }
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun LayerBackdrop?.miuixAppBarColor(): Color =
    if (this != null) Color.Transparent else MiuixTheme.colorScheme.surface

/**
 * 顶栏毛玻璃容器：Gaussian 或 Progressive。
 *
 * Progressive 采用 Mishka `BlurredBar` 的行为：模糊层常驻，不按滚动位置淡入淡出。
 * miuix 示例里的 `-contentOffset / 48dp` alpha 门槛只认手指滚动增量，遇到
 * `initialFirstVisibleItemIndex` / `scrollToItem` 这类程序化定位时 contentOffset 仍是 0，
 * 会把整层模糊淡没（「选择操作类型」页返回后重进即如此）。这里不再接滚动状态。
 */
@Composable
fun MiuixBlurredTopBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    blurStyle: TopAppBarBlurStyle = LocalTopAppBarBlurStyle.current,
    content: @Composable () -> Unit,
) {
    val blurActive = enabled && backdrop != null
    val progressive = blurStyle == TopAppBarBlurStyle.PROGRESSIVE
    Box(
        modifier = modifier.then(
            if (blurActive && !progressive) {
                Modifier.miuixGaussianAppBarBlur(backdrop = backdrop)
            } else {
                Modifier
            },
        ),
    ) {
        if (blurActive && progressive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .miuixProgressiveAppBarBlur(backdrop = backdrop),
            )
        }
        content()
    }
}

@Composable
private fun Modifier.miuixGaussianAppBarBlur(
    backdrop: LayerBackdrop?,
    blurRadius: Float = 25f,
    blendAlpha: Float = 0.82f,
    shape: Shape = RectangleShape,
): Modifier {
    if (backdrop == null) return this
    return miuixAppBarBlurColors(progressive = false).let { colors ->
        then(
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = blurRadius,
                colors = colors,
            ),
        )
    }
}

@Composable
private fun Modifier.miuixProgressiveAppBarBlur(
    backdrop: LayerBackdrop?,
    blurRadius: Float = 10f,
    shape: Shape = RectangleShape,
): Modifier {
    if (backdrop == null) return this
    return miuixAppBarBlurColors(progressive = true).let { colors ->
        then(
            Modifier.progressiveTextureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = blurRadius,
                gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                colors = colors,
            ),
        )
    }
}

@Composable
private fun miuixAppBarBlurColors(progressive: Boolean): BlurColors {
    val blendAlpha = if (progressive) 0.3f else 0.82f
    val blendColor = MiuixTheme.colorScheme.surface.copy(alpha = blendAlpha)
    return BlurColors(
        blendColors = listOf(
            BlendColorEntry(color = blendColor),
        ),
    )
}

/** @deprecated 请改用 [MiuixBlurredTopBar] 以支持 Progressive 顶栏模糊。 */
@Composable
fun Modifier.miuixAppBarBlur(
    backdrop: LayerBackdrop?,
    enabled: Boolean = true,
    blurRadius: Float = 25f,
    blendAlpha: Float = 0.82f,
    shape: Shape = RectangleShape,
): Modifier {
    if (!enabled || backdrop == null) return this
    val blendColor = MiuixTheme.colorScheme.surface.copy(alpha = blendAlpha)
    return then(
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = blendColor),
                ),
            ),
        ),
    )
}
