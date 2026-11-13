package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一顶栏：设备支持 RuntimeShader 且有 backdrop 时走 OS4 玻璃栏（[GlassTopAppBar]），
 * 否则回退到原有的 [MiuixBlurredTopBar] + [AdaptiveTopAppBar]。
 *
 * 悬浮窗里的页面拿不到页面 backdrop，[backdrop] 为 null，自然走回退分支。
 */
@Composable
fun MiuixTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    color: Color = MiuixTheme.colorScheme.surface,
    scrollBehavior: ScrollBehavior? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    largeTitle: String = title,
    titleAlpha: (() -> Float)? = null,
    backgroundAlpha: (() -> Float)? = null,
    defaultWindowInsetsPadding: Boolean = true,
) {
    val isDark = LocalAppDarkTheme.current
    val useGlass = backdrop != null && isRuntimeShaderSupported()

    if (useGlass) {
        CompositionLocalProvider(LocalMiuixGlassTopBar provides true) {
            GlassTopAppBar(
                title = title,
                modifier = modifier,
                progressiveBlur = true,
                backdrop = backdrop,
                largeTitle = largeTitle,
                scrollBehavior = scrollBehavior,
                style = GlassDefaults.style(isDark),
                defaultWindowInsetsPadding = defaultWindowInsetsPadding,
                titleAlpha = titleAlpha ?: { 1f },
                backgroundAlpha = backgroundAlpha ?: { 1f },
                navigationIcon = {
                    when {
                        onBack != null -> MiuixGlassBackButton(onBack)
                        navigationIcon != null -> navigationIcon()
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                },
                bottomContent = bottomContent,
            )
        }
    } else {
        CompositionLocalProvider(LocalMiuixGlassTopBar provides false) {
            MiuixBlurredTopBar(backdrop = backdrop) {
                AdaptiveTopAppBar(
                    modifier = modifier,
                    color = color,
                    title = title,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        when {
                            onBack != null -> MiuixBackNavigationIcon(onBack)
                            navigationIcon != null -> navigationIcon()
                        }
                    },
                    actions = actions,
                    bottomContent = bottomContent,
                )
            }
        }
    }
}

/** 玻璃胶囊返回键；[GlassIconButton] 在顶栏槽位里会自动继承顶栏材质。 */
@Composable
internal fun MiuixGlassBackButton(onClick: () -> Unit) {
    GlassIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_navigate_back),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}
