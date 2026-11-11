package com.slideindex.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 页面里的行首图标，统一由 Material Symbols 字形转成的矢量渲染（[MaterialSymbolVectors]）。
 *
 * 为什么不是 `androidx.compose.material.icons`：那套是 2014 版 Material Icons，线宽写死 2dp、
 * 没有字重轴，和 `MiuixIcons` 混排时粗细对不齐。
 *
 * 为什么不是图标字体直接画文本：图标字体的垂直度量极不对称（ascent 1.1em、descent 0.1em），
 * 文本方式在 24dp 盒子里会偏移，做不到和现有 ImageVector 图标一样"天生居中"。
 *
 * 迁移写法：`Icon(Icons.Outlined.Add, contentDescription = label)` → `SymbolsIcon("Add", contentDescription = label)`。
 * 名字表见 [MaterialSymbolVectors]（沿用 Compose 图标名）。
 *
 * @param name [MaterialSymbolVectors] 里的名字；未知名字不画任何东西（不会崩）。
 * @param mirrored RTL 场景下镜像（原来用 `Icons.AutoMirrored.*` 的方向性图标要传 true）。
 */
@Composable
fun SymbolsIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
    mirrored: Boolean = false,
) {
    val vector = remember(name) { MaterialSymbolVectors.get(name) } ?: return
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(size)
            .then(if (mirrored) Modifier.scale(-1f, 1f) else Modifier),
    )
}
