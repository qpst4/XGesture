package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.slideindex.app.ui.LocalMainNavContentStartInset
import com.slideindex.app.ui.mainNavStartInset
import com.slideindex.app.ui.LocalMainNavTitleAlpha
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * 非懒加载的 Miuix 子页骨架：顶栏/毛玻璃/insets 与 [MiuixSettingsScreenScaffold] 同一套，
 * 但内容区交给调用方自己布局（网格、分页、Canvas 这类塞不进 `LazyColumn` 的内容）。
 *
 * 主要用于「添加条目」这类既有整页、又有可滚动网格/列表的页面，避免它们各写一套
 * `Material3.Surface` + 手写状态栏 padding 的假页面壳。
 */
@Composable
fun MiuixContentScaffold(
    title: String,
    modifier: Modifier = Modifier,
    pageHint: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop()
    // 让顶栏 actions 与内容区的玻璃浮层都能取到页面背板（玻璃菜单采样用）。
    CompositionLocalProvider(LocalMiuixScreenBackdrop provides barBackdrop) {
        Scaffold(
        contentWindowInsets = MiuixScaffoldContentWindowInsets,
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .imePadding(),
            topBar = {
                MiuixGlassTopBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    backdrop = barBackdrop,
                    navigationIcon = {
                        if (onBack != null) MiuixGlassBackNavigationIcon(onBack)
                    },
                    actions = actions,
                    bottomContent = bottomContent,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .horizontalCutoutPadding(),
            ) {
                if (pageHint != null) {
                    SettingsPageHintCard(pageHint)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }
    }
}
