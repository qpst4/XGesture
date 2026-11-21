package com.slideindex.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通用分段选择器（高质感物理阻尼动效 + 悬浮微阴影 + 动态分割线消隐）。
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
    SegmentedControl(
        items = items,
        selected = selected,
        labels = items.map { item -> stringResource(labelRes(item)) },
        onSelect = onSelect,
        containerColor = containerColor,
        indicatorColor = indicatorColor,
    )
}

/**
 * 同 [SegmentedControl]，但标签直接给字符串。
 *
 * 用于标签不是字符串资源、或者需要动态拼接的场景（如「一级 / 二级」这种带名称的层级）。
 */
@Composable
internal fun <T> SegmentedControl(
    items: List<T>,
    selected: T,
    labels: List<String>,
    onSelect: (T) -> Unit,
    containerColor: Color? = null,
    indicatorColor: Color? = null,
) {
    if (items.isEmpty()) return

    val isDark = isSystemInDarkTheme()
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)

    // 对齐 Miuix TabRowContour 规范：底槽采用 surface，滑块采用 surfaceContainer
    val containerBg = containerColor ?: MiuixTheme.colorScheme.surface
    val containerBorderColor = MiuixTheme.colorScheme.dividerLine.copy(alpha = if (isDark) 0.5f else 0.7f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .border(
                width = 0.8.dp,
                color = containerBorderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .background(
                color = containerBg,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(3.5.dp),
    ) {
        val haptic = LocalHapticFeedback.current
        val totalWidth = maxWidth
        val tabWidth = totalWidth / items.size

        var isInitialized by remember(items) { mutableStateOf(false) }
        var lastSelectedIndex by remember(items) { mutableIntStateOf(selectedIndex) }

        val targetOffset = tabWidth * selectedIndex

        // 1. 物理阻尼平滑滑动药丸 (首次渲染时瞬时 snap 定位，点击切换时触发细腻阻尼回弹)
        val indicatorOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = if (!isInitialized || lastSelectedIndex == selectedIndex) {
                snap()
            } else {
                spring<Dp>(
                    dampingRatio = 0.84f,
                    stiffness = 420f,
                )
            },
            label = "segmentedIndicatorOffset",
        )

        LaunchedEffect(selectedIndex) {
            if (!isInitialized) {
                isInitialized = true
            }
            lastSelectedIndex = selectedIndex
        }

        val pillBg = indicatorColor ?: MiuixTheme.colorScheme.surfaceContainer
        val pillBorderColor = if (isDark) Color(0x28FFFFFF) else MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.35f)

        // 2. 选项之间的微垂直分割线 (饱满 18dp 矩形，100% Miuix dividerLine，未选中项之间清晰可见)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, _ ->
                if (index > 0) {
                    val shouldShow = index != selectedIndex && (index - 1) != selectedIndex
                    Box(
                        modifier = Modifier
                            .offset(x = (-0.5).dp)
                            .width(1.dp)
                            .height(18.dp)
                            .background(
                                color = if (shouldShow) MiuixTheme.colorScheme.dividerLine else Color.Transparent,
                            ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // 3. 选中实体滑块 (带立体环境弥散微阴影与 0.5dp 精细描边)
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = if (isDark) 1.5.dp else 2.dp,
                    shape = RoundedCornerShape(10.5.dp),
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x22000000),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.2f) else Color(0x14000000),
                )
                .border(
                    width = 0.5.dp,
                    color = pillBorderColor,
                    shape = RoundedCornerShape(10.5.dp),
                )
                .background(
                    color = pillBg,
                    shape = RoundedCornerShape(10.5.dp),
                ),
        )

        // 4. 上层文字与交互点击层 (平滑文本变色 + 触觉震动反馈)
        val selectedColor = MiuixTheme.colorScheme.primary
        val unselectedColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else unselectedColor,
                    animationSpec = if (!isInitialized) snap() else tween(durationMillis = 200),
                    label = "segmentedTextColor",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.5.dp))
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
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                    )
                }
            }
        }
    }
}
