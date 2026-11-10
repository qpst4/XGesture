package com.slideindex.app.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.SettingIconContainer
import com.slideindex.app.ui.miuix.MiuixArrowRow
import com.slideindex.app.ui.miuix.MiuixGroupedCard
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun Modifier.settingsGroupedRowBackground(
    index: Int,
    count: Int,
    color: Color? = null,
): Modifier {
    if (LocalSettingsCardSegmentMode.current) return fillMaxWidth()
    return if (color == null) {
        miuixGroupedCardItem(index, count)
    } else {
        miuixGroupedCardItem(index, count, color = color)
    }
}

@Composable
fun SettingsCardScope.SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        SwitchPreference(
            modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
            title = title,
            summary = subtitle,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            startAction = icon?.let { { SettingIconContainer { it(title) } } },
        )
    }
}

/** 纯展开行（不带开关）：点标题展开/收起子行，默认收起，用于「先收起、别碍事」的次级设置。 */
@Composable
fun SettingsCardScope.SettingExpandableRow(
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    enabled: Boolean = true,
    onExpandedChange: (Boolean) -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    SettingsCardRow(key = "$title-expandable") { position ->
        Column(modifier = Modifier.settingsGroupedRowBackground(position.index, position.count)) {
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
                animationSpec = tween(200),
            )
            BasicComponent(
                modifier = Modifier.fillMaxWidth(),
                title = title,
                summary = subtitle,
                enabled = enabled,
                onClick = { if (enabled) onExpandedChange(!expanded) },
                endActions = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation),
                    )
                },
            )
            AnimatedVisibility(
                visible = expanded && enabled,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    expandedContent()
                }
            }
        }
    }
}

/** Mishka-style switch with animated child rows in the same card segment. */
@Composable
fun SettingsCardScope.SettingExpandableSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        Column(modifier = Modifier.settingsGroupedRowBackground(position.index, position.count)) {
            SwitchPreference(
                title = title,
                summary = subtitle,
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                startAction = icon?.let { { SettingIconContainer { it(title) } } },
            )
            AnimatedVisibility(
                visible = checked && enabled,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    expandedContent()
                }
            }
        }
    }
}

@Composable
fun SwitchNavigationTrailingContent(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(start = 8.dp),
    ) {
        VerticalDivider(
            modifier = Modifier.height(32.dp),
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

@Composable
fun SettingsCardScope.SettingSwitchNavigationRow(
    title: String,
    subtitle: String,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /**
     * Extra content under title/subtitle in the center column (Quick Search alias pill).
     */
    subtitleContent: (@Composable () -> Unit)? = null,
) {
    SettingsCardRow(key = title) { position ->
        val deferredNavigate = rememberDeferredNavigationClick(onNavigate)
        val rowModifier = Modifier
            .settingsGroupedRowBackground(position.index, position.count)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = { if (enabled) deferredNavigate() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
        val onClick = if (onLongClick == null) {
            { if (enabled) deferredNavigate() }
        } else {
            null
        }
        if (subtitleContent == null) {
            BasicComponent(
                modifier = rowModifier,
                title = title,
                summary = subtitle.takeIf { it.isNotBlank() },
                enabled = enabled,
                startAction = icon?.let { iconLambda ->
                    { SettingIconContainer { iconLambda(title) } }
                },
                onClick = onClick,
                endActions = {
                    SwitchNavigationTrailingContent(
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = onCheckedChange,
                    )
                },
            )
        } else {
            BasicComponent(
                modifier = rowModifier,
                enabled = enabled,
                startAction = icon?.let { iconLambda ->
                    { SettingIconContainer { iconLambda(title) } }
                },
                onClick = onClick,
                endActions = {
                    SwitchNavigationTrailingContent(
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = onCheckedChange,
                    )
                },
                content = {
                    Text(
                        text = title,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) {
                            MiuixTheme.colorScheme.onBackground
                        } else {
                            MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        },
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = if (enabled) {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            } else {
                                MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            },
                        )
                    }
                    subtitleContent()
                },
            )
        }
    }
}

@Composable
fun SettingsCardScope.SettingLinkRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val deferredClick = rememberDeferredNavigationClick(onClick)
    SettingsCardRow(key = title) { position ->
        ArrowPreference(
            modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
            title = title,
            summary = subtitle,
            enabled = enabled,
            onClick = deferredClick,
        )
    }
}

@Composable
fun SettingsCardScope.SettingToggleRow(
    icon: @Composable (accessibilityLabel: String) -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        SwitchPreference(
            modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
            title = title,
            summary = subtitle,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            startAction = { SettingIconContainer { icon(title) } },
        )
    }
}

@Composable
fun SettingsCardScope.SettingDropdownRow(
    title: String,
    subtitle: String? = null,
    items: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        OverlayDropdownPreference(
            modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
            title = title,
            summary = subtitle,
            items = items,
            selectedIndex = selectedIndex.coerceIn(0, (items.lastIndex).coerceAtLeast(0)),
            enabled = enabled,
            startAction = icon?.let { { SettingIconContainer { it(title) } } },
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}

/** 下拉 + 条件子行（展开/收起动画），与 [SettingExpandableSwitchRow] 同结构。 */
@Composable
fun SettingsCardScope.SettingExpandableDropdownRow(
    title: String,
    subtitle: String? = null,
    items: List<String>,
    selectedIndex: Int,
    expanded: Boolean,
    enabled: Boolean = true,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    SettingsCardRow(key = "$title-expandable-dropdown") { position ->
        Column(modifier = Modifier.settingsGroupedRowBackground(position.index, position.count)) {
            OverlayDropdownPreference(
                title = title,
                summary = subtitle,
                items = items,
                selectedIndex = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                enabled = enabled,
                startAction = icon?.let { { SettingIconContainer { it(title) } } },
                onSelectedIndexChange = onSelectedIndexChange,
            )
            AnimatedVisibility(
                visible = expanded && enabled,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    expandedContent()
                }
            }
        }
    }
}

@Composable
fun SettingsCardScope.SettingSpinnerRow(
    title: String,
    subtitle: String? = null,
    dialogButtonText: String,
    items: List<DropdownItem>,
    selectedIndex: Int,
    enabled: Boolean = true,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        OverlaySpinnerPreference(
            modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
            title = title,
            summary = subtitle,
            dialogButtonString = dialogButtonText,
            items = items,
            selectedIndex = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
            enabled = enabled,
            startAction = icon?.let { { SettingIconContainer { it(title) } } },
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}

@Composable
fun SettingsCardScope.SettingNavigationRow(
    icon: @Composable (accessibilityLabel: String) -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    /** 行卡片底色；null 用默认 surfaceContainer（白）。bottom sheet 内容可传 secondaryContainer 拿灰卡。 */
    backgroundColor: Color? = null,
) {
    val deferredClick = rememberDeferredNavigationClick(onClick)
    SettingsCardRow(key = title) { position ->
        if (trailingContent != null) {
            BasicComponent(
                modifier = Modifier.settingsGroupedRowBackground(position.index, position.count, backgroundColor),
                insideMargin = insideMargin,
                title = title,
                summary = subtitle,
                enabled = enabled,
                startAction = { SettingIconContainer { icon(title) } },
                onClick = deferredClick,
                endActions = { trailingContent() },
            )
        } else {
            ArrowPreference(
                modifier = Modifier.settingsGroupedRowBackground(position.index, position.count, backgroundColor),
                insideMargin = insideMargin,
                title = title,
                summary = subtitle,
                enabled = enabled,
                startAction = { SettingIconContainer { icon(title) } },
                onClick = deferredClick,
            )
        }
    }
}

/** Hub / 设置入口：无 M3 图标，纯 Miuix 箭头行 + 分组圆角。 */
@Composable
fun SettingsCardScope.MiuixNavigationRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    rowKey: Any = title,
) {
    val deferredClick = rememberDeferredNavigationClick(onClick)
    SettingsCardRow(key = rowKey) { position ->
        if (LocalSettingsCardSegmentMode.current) {
            MiuixArrowRow(
                title = title,
                summary = summary,
                enabled = enabled,
                onClick = deferredClick,
            )
        } else {
            MiuixGroupedCard(index = position.index, count = position.count) {
                MiuixArrowRow(
                    title = title,
                    summary = summary,
                    enabled = enabled,
                    onClick = deferredClick,
                )
            }
        }
    }
}

@Composable
fun SettingsCardScope.SettingRadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    segmentKey: Any = title,
    onClick: () -> Unit,
) {
    SettingsCardRow(key = segmentKey) { position ->
        RadioButtonPreference(
            modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
            title = title,
            summary = subtitle,
            selected = selected,
            enabled = enabled,
            onClick = { if (enabled) onClick() },
            radioButtonLocation = RadioButtonLocation.End,
        )
    }
}

@Composable
fun SettingsCardScope.SettingCheckboxRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    segmentKey: Any = title,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCardRow(key = segmentKey) { position ->
        CheckboxPreference(
            modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
            title = title,
            summary = subtitle,
            checked = checked,
            enabled = enabled,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            checkboxLocation = CheckboxLocation.End,
        )
    }
}

/**
 * 双列网格行（Bento Grid Row）：在单个设置卡片段内横向放置两个均分的导航入口。
 *
 * 标题允许两行、副标题可选：一格只有一半宽度（文字区约 100dp），「Shell 命令面板」这类长标题
 * 一行必被省略号截断；不要副标题时把第二行让给标题，比压短标题更不容易丢语义。
 */
@Composable
fun SettingsCardScope.SettingGrid2Row(
    leftIcon: @Composable (accessibilityLabel: String) -> Unit,
    leftTitle: String,
    leftSubtitle: String? = null,
    leftEnabled: Boolean = true,
    onLeftClick: () -> Unit,
    rightIcon: @Composable (accessibilityLabel: String) -> Unit,
    rightTitle: String,
    rightSubtitle: String? = null,
    rightEnabled: Boolean = true,
    onRightClick: () -> Unit,
    rowKey: Any = "$leftTitle-$rightTitle",
) {
    val leftDeferredClick = rememberDeferredNavigationClick(onLeftClick)
    val rightDeferredClick = rememberDeferredNavigationClick(onRightClick)

    SettingsCardRow(key = rowKey) { position ->
        Row(
            modifier = Modifier
                .settingsGroupedRowBackground(position.index, position.count)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左半边
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = leftEnabled, onClick = leftDeferredClick)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIconContainer { leftIcon(leftTitle) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = leftTitle,
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = if (leftEnabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (leftSubtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = leftSubtitle,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                color = if (leftEnabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // 中间微弱垂直分割线
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 10.dp),
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 0.5.dp,
            )

            // 右半边
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = rightEnabled, onClick = rightDeferredClick)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIconContainer { rightIcon(rightTitle) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = rightTitle,
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = if (rightEnabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (rightSubtitle != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = rightSubtitle,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                color = if (rightEnabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}


