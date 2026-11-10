package com.slideindex.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通用分段选择器。
 *
 * 组件本身不决定选项顺序：调用方通过 [items] 传入的顺序就是显示顺序。
 * [labelRes] 把每个选项映射到字符串资源，[onSelect] 回传被点击的选项本身，
 * 调用点不需要再做下标换算。
 */
@Composable
internal fun <T> SegmentedControl(
    items: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onSelect: (T) -> Unit,
    /** 容器底色；null 用默认的 surfaceContainerHigh。卡片内调用可传 surface 与页面同灰。 */
    containerColor: Color? = null,
    /** 选中 pill 底色；null 用默认的 surface。 */
    indicatorColor: Color? = null,
) {
    val labels = items.map { item -> stringResource(labelRes(item)) }
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor ?: MiuixTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.dividerLine,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(3.dp),
    ) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val dividerTotal = if (labels.isEmpty()) 0.dp else (labels.size - 1).dp
        val segmentWidth = if (labels.isEmpty()) 0.dp else (maxWidth - dividerTotal) / labels.size
        val indicatorOffset = remember { Animatable(0f) }
        var firstLayout by remember { mutableStateOf(true) }

        LaunchedEffect(selectedIndex, segmentWidth) {
            val target = with(density) { (segmentWidth + 1.dp).toPx() } * selectedIndex
            if (firstLayout) {
                indicatorOffset.snapTo(target)
                firstLayout = false
            } else {
                indicatorOffset.animateTo(
                    targetValue = target,
                    animationSpec = tween(200, easing = LinearEasing),
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorOffset.value.roundToInt(), 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .background(indicatorColor ?: MiuixTheme.colorScheme.surface),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                // 选中项两侧不画竖线：两个 tab 时中间自然没有线，三个以上也保持选中 pill 周围干净。
                if (index > 0 && index != selectedIndex && index - 1 != selectedIndex) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MiuixTheme.colorScheme.dividerLine),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            if (index != selectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onSelect(items[index])
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = label,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = if (index == selectedIndex) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (index == selectedIndex) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
            }
        }
    }
}
