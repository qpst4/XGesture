package com.slideindex.app.overlay.quickmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 工具页统一内边距。 */
internal val QuickMenuToolContentPadding = 16.dp

/**
 * 工具页脚手架：整页纵向滚动 + 统一内边距。
 *
 * 从 `QuickMenuToolScreens` 抽出到独立文件：计算器 / 番茄钟 / 提醒等屏幕
 * 都只用这一个脚手架，不必把 2,136 行的工具屏整块搬进模块。
 */
@Composable
fun ToolScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(QuickMenuToolContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
