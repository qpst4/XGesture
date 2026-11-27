package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun MiuixBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    allowDismiss: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    /** 钉在标题下方、头部内部的一行（rc01 的 `headerBottomContent`），会随浮动头部一起模糊。 */
    headerBottomContent: @Composable () -> Unit = {},
    /** 内容区四边留白；null 用 miuix 默认。sheet 内放带自身外边距的行（如 settingsCardItems）时传小值避免双层缩进。 */
    insideMargin: DpSize? = null,
    /** 是否统一补"实心导航条"（三键导航）的底部留白；需要内容铺到导航栏后面的 sheet 可传 false。 */
    applyNavigationBarInset: Boolean = true,
    /** 关闭动画播完后回调；需先让 [show] 变 false 才会触发，关闭被中途取消时不触发。 */
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val insetContent: @Composable () -> Unit = {
        NavigationBarInsetBox(enabled = applyNavigationBarInset) { content() }
    }

    val overlayBackdrop = LocalMiuixPageBackdrop.current
    if (overlayBackdrop != null) {
        GlassBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            backdrop = overlayBackdrop,
            modifier = modifier,
            title = title,
            allowDismiss = allowDismiss,
            startAction = startAction,
            endAction = endAction,
            headerBottomContent = headerBottomContent,
            // rc01 起玻璃抽屉的内容留白改成 12dp（`GlassBottomSheetDefaults.InsideMargin`），
            // 与全站 12dp 页边基线一致；旧版这里是 `BottomSheetDefaults.insideMargin`（24dp）。
            insideMargin = insideMargin ?: GlassBottomSheetDefaults.InsideMargin,
            onDismissFinished = onDismissFinished,
            content = { headerPadding ->
                // rc01 的头部是**浮**在内容上的（progressiveBlur），实测头部高度以 PaddingValues 下发。
                // 不消费它，首行内容就会被盖在头部的模糊带下面。
                Box(Modifier.padding(top = headerPadding.calculateTopPadding())) {
                    insetContent()
                }
            },
        )
        return
    }

    WindowBottomSheet(
        show = show,
        modifier = modifier,
        title = title,
        allowDismiss = allowDismiss,
        startAction = startAction,
        endAction = endAction,
        onDismissRequest = onDismissRequest,
        insideMargin = insideMargin ?: BottomSheetDefaults.insideMargin,
        onDismissFinished = onDismissFinished,
        content = insetContent,
    )
}

/**
 * miuix 的 BottomSheet 只内建输入法内边距，导航栏留白明确交给调用方
 * （`docs/zh_CN/components/glass/glassbottomsheet.md`：主体导航栏内边距由调用方提供），
 * 这里在包装层统一补上，避免 30+ 个调用点各写一遍。
 *
 * 两条守卫：
 * - 只认"实心导航条"（三键导航，底部 inset ≥ [SolidNavigationBarMinHeight]）。手势导航的底部
 *   只有细横条，抽屉要保持贴底观感，不能平白多出一条留白。
 * - 键盘弹出时不补：miuix 已在抽屉容器上做 `imePadding`，两者叠加会多抬一条导航栏的高度。
 */
@Composable
private fun NavigationBarInsetBox(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val imeBottom = WindowInsets.ime.getBottom(density)
    val needsInset = enabled &&
        navigationBarBottom >= with(density) { SolidNavigationBarMinHeight.roundToPx() } &&
        imeBottom == 0
    if (needsInset) {
        Box(Modifier.navigationBarsPadding()) { content() }
    } else {
        content()
    }
}

/** 三键导航条的高度下限（约 48dp）；手势导航底部导航区约 24dp，落在下限以下。 */
private val SolidNavigationBarMinHeight = 30.dp
