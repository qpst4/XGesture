package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R

/**
 * 行首图标统一用的字重：想整体调粗细只改这一个数（Material Symbols 的 wght 轴，100–700）。
 * 400 = 常规；300 更细，500 更重。
 */
const val SYMBOLS_ICON_WEIGHT: Int = 400

/**
 * 页面里的行首图标，统一由 Material Symbols 可变字体渲染。
 *
 * 为什么不是 `androidx.compose.material.icons`：那套是 2014 版 Material Icons，线宽写死 2dp、没有
 * 字重轴，和 `MiuixIcons` 混排时粗细对不齐；字体方案可以在 [weight] 上统一调，且覆盖全量字形。
 *
 * 迁移写法：`Icon(Icons.Outlined.Add, contentDescription = label)` → `SymbolsIcon("Add", contentDescription = label)`。
 * 名字表见 [MaterialSymbolsGlyphs]（取自 Compose 图标名，机械替换即可）。
 *
 * @param name [MaterialSymbolsGlyphs] 里的名字；未知名字不画任何东西（不会崩）。
 * @param weight Material Symbols 字重，默认 [SYMBOLS_ICON_WEIGHT]。
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun SymbolsIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    weight: Int = SYMBOLS_ICON_WEIGHT,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    val glyph = remember(name) { MaterialSymbolsGlyphs.glyph(name) } ?: return
    val density = LocalDensity.current
    val fontSize = with(density) { size.toSp() }
    val family = remember(weight) {
        FontFamily(
            Font(
                resId = R.font.material_symbols_outlined,
                weight = FontWeight.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        )
    }
    val semanticsModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Box(modifier = semanticsModifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            color = tint,
            fontFamily = family,
            fontSize = fontSize,
            lineHeight = fontSize,
            style = TextStyle(
                // 图标字形不需要字体额外内边距，否则 24dp 盒子里会偏心。
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
            ),
            maxLines = 1,
        )
    }
}
