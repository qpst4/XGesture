package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * [MiuixSettingsFab] 需要为其预留的底部空间。
 *
 * **为什么必须显式给这个值**：`FloatingActionButton` 是 `Scaffold` 的悬浮槽，**不参与内容布局**，
 * 所以页面的滚动区会一直延伸到它下面。凡是「占满视口的列表项（`fillParentMaxSize`）+ 悬浮按钮」
 * 的页面，滚到底时最后一行/最后一张卡都会被压掉一截（冰箱网格被切约 1/4 行、命令面板被切一条边，
 * 都是这个原因）。`Scaffold` 不暴露 FAB 的实际高度，所以由使用方按这个常量预留。
 *
 * 取值 = FAB 本体（Miuix 默认 ≈56dp）+ 它与屏幕底部之间的呼吸（16dp）。
 */
val MiuixSettingsFabInset = 72.dp

@Composable
fun MiuixSettingsFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onPrimary,
        )
    }
}
