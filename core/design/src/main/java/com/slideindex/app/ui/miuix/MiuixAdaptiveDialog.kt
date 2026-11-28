package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 自适应对话框：**有整页背板就走玻璃 `GlassDialog`，没有就回退 `WindowDialog`**。
 *
 * 存在的意义：仓库里曾有 9 处直接调 `WindowDialog`，于是无论机器上模糊能不能用，
 * 它们永远是不透明窗口风格。以后要弹自定义对话框一律用这个，不要直接写 `WindowDialog`。
 *
 * 玻璃分支会额外下发 [LocalMiuixGlassSurface]，让内容里的卡片/输入框自动换成玻璃件。
 *
 * @param show 与 `WindowDialog` 同义：控制进出场动画，内容需保持组合直到退场结束。
 * @param actions 底部动作行；非空时内部渲染 [MiuixDialogActionsRow]，
 *   材质与排布由它统一决定（玻璃面 `GlassDialogButton`、非玻璃面 `TextButton`）。
 *   传空表示调用方自己在 [content] 里画。
 */
@Composable
fun MiuixAdaptiveDialog(
    onDismissRequest: () -> Unit,
    show: Boolean = true,
    title: String? = null,
    summary: String? = null,
    modifier: Modifier = Modifier,
    actions: List<MiuixDialogAction> = emptyList(),
    /** 退出动画播完后回调；调用方在这之后再清数据。 */
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val body: @Composable () -> Unit = {
        content()
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            MiuixDialogActionsRow(actions = actions)
        }
    }
    val backdrop = LocalMiuixPageBackdrop.current
    if (backdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = show,
            backdrop = backdrop,
            modifier = modifier,
            title = title,
            summary = summary,
            content = { MiuixGlassDialogScope(backdrop) { body() } },
        )
        return
    }
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        body()
    }
}
