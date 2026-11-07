package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults

/**
 * Card 已设 `insideMargin = 16.dp`（TabRowSection 模式 B）时，
 * Preference 行只消掉水平重复缩进，保留上下 16dp 行距。
 */
val MiuixInsetCardComponentMargin = PaddingValues(horizontal = 0.dp, vertical = 16.dp)

/** [groupedCardItems] / [MiuixListSettingsCard] 段内自定义 Row 的标准 inset。 */
fun Modifier.miuixGroupedRowInsets(): Modifier = padding(BasicComponentDefaults.InsideMargin)
