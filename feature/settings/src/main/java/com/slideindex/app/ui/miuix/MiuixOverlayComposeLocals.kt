package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled

/**
 * 无障碍 / WindowManager 浮层可能整窗走软件 Canvas（`ViewRootImpl.drawSoftware`），
 * 此时 RuntimeShader（顶栏 textureBlur、squircle 等）会在 `drawRect` 直接崩溃。
 */
@Composable
fun MiuixOverlayComposeLocals(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMiuixSquircleEnabled provides false,
        LocalMiuixBlurBackdropEnabled provides false,
        LocalSquircleEnabled provides false,
    ) {
        content()
    }
}
