package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionFractions
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import java.util.Locale
import kotlin.math.roundToInt

private val floatBallOpacityRange = 0f..1f
private val floatBallIdleDelayRange = 0f..10f
private val floatBallLineHeightRange = 0.04f..0.4f
private val floatBallLineWidthRange =
    FloatBallSettings.MIN_LINE_WIDTH_DP..FloatBallSettings.MAX_LINE_WIDTH_DP

private fun fractionPercentSnap(
    range: ClosedFloatingPointRange<Float>
): (Float) -> Float = { value ->
    (value.coerceIn(range.start, range.endInclusive) * 1000f).roundToInt() / 1000f
}

private fun fractionPercentLabel(value: Float): String = "${(value * 100f).roundToInt()}%"

/** 「距边缘距离」数值文案：带正负，整数 dp。 */
private fun edgeInsetLabel(value: Float): String {
    val rounded = value.roundToInt()
    return if (rounded > 0) "+$rounded dp" else "$rounded dp"
}

/** 闲置判定时长的显示值：整秒不带小数，其余保留一位（0.5 秒粒度）。 */
private fun idleDelaySecondsLabel(seconds: Float): String {
    val rounded = (seconds.coerceIn(floatBallIdleDelayRange) * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

private fun lineHeightPercentLabel(value: Float): String =
    String.format(Locale.US, "%.1f%%", value * 100f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallAppearanceSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: () -> Unit,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onHideWhenIdleChange: (Boolean) -> Unit,
    onIdleFreezeGifChange: (Boolean) -> Unit,
    onIdleOpacityChange: (Float) -> Unit,
    onIdleDelayChange: (Int) -> Unit,
    onPositionModeChange: (FloatBallPositionMode) -> Unit,
    onEdgeInsetChange: (Int) -> Unit = {},
    onPositionYChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onLineWidthChange: (Float) -> Unit,
    onLineOpacityChange: (Float) -> Unit,
    onOpenStyleSettings: () -> Unit,
    onStripZonePreviewStart: () -> Unit = {},
    onStripZonePreviewStop: () -> Unit = {},
    onPositionYPreviewStart: () -> Unit = {},
    onPositionYPreviewChange: (Float) -> Unit = {},
    onPositionYPreviewStop: (restoreIfNeeded: Boolean) -> Unit = {},
    onPreviewAppearance: (
        sizeDp: Float?,
        opacity: Float?,
        /** 距边缘距离（dp，负值 = 往屏幕外挪）。 */
        edgeInsetDp: Float?,
        lineHeightFraction: Float?,
        lineWidthDp: Float?,
        lineOpacity: Float?
    ) -> Unit = { _, _, _, _, _, _ -> },
    onAppearancePreviewCommit: () -> Unit = {},
    onAppearancePreviewRestore: () -> Unit = {}
) {
    val appearanceSectionTitle = stringResource(R.string.float_ball_section_appearance)
    val positionSectionTitle = stringResource(R.string.float_ball_position)
    val positionXyHint = stringResource(R.string.float_ball_position_xy_hint)
    val lineSectionTitle = stringResource(R.string.float_ball_section_line)
    val lineWidthPreviewHint = stringResource(R.string.float_ball_line_width_preview_hint)
    val lineWidthValueFormat = stringResource(R.string.float_ball_line_width_value)
    val idleDelayValueFormat = stringResource(R.string.float_ball_idle_delay_value)

    val positionMode = settings.floatBallPositionMode
    val positionModeEntries = FloatBallPositionMode.selectable
    val positionModeIndex = positionModeEntries.indexOf(positionMode).coerceAtLeast(0)
    val showEdgeLineSettings = positionMode == FloatBallPositionMode.BOTH_EDGES ||
        positionMode == FloatBallPositionMode.BOTH_LINES
    // 「距边缘距离」上下限随屏幕宽度与球大小变化：最多挪到屏幕中央 / 最多露出半个球。
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val maxEdgeInsetDp = FloatBallPositionFractions.maxInsetDp(
        screenWidthDp = screenWidthDp,
        ballSizeDp = settings.floatBallSizeDp,
    )
    val minEdgeInsetDp = FloatBallPositionFractions.minInsetDp(settings.floatBallSizeDp)

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_appearance_settings_title),
        pageHint = stringResource(R.string.float_ball_entry_desc),
        onBack = onBack
    ) {
        settingsLazySmallTitle(
            key = "appearance_section",
            title = appearanceSectionTitle
        )
        groupedCardItems(
            keyPrefix = "fb-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("style-picker") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    Icons.Outlined.Palette,
                                    contentDescription = label,
                                    tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            },
                            title = stringResource(R.string.float_ball_style_picker_title),
                            subtitle = floatBallStyleLabel(settings.floatBallStyleType),
                            enabled = true,
                            onClick = onOpenStyleSettings
                        )
                    }
                )
                add(
                    settingsCardScopeItem("size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_size),
                            value = settings.floatBallSizeDp,
                            valueRange = 36f..96f,
                            steps = 59,
                            enabled = true,
                            label = stringResource(
                                R.string.float_ball_size_value,
                                settings.floatBallSizeDp
                            ),
                            triggersLayoutPreview = true,
                            onLayoutPreviewValueChange = { value ->
                                onPreviewAppearance(value, null, null, null, null, null)
                            },
                            onValueChange = { value ->
                                onAppearancePreviewCommit()
                                onSizeChange(value)
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("opacity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_opacity),
                            value = settings.floatBallOpacity,
                            valueRange = floatBallOpacityRange,
                            enabled = true,
                            label = fractionPercentLabel(settings.floatBallOpacity),
                            formatLabel = ::fractionPercentLabel,
                            keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                            triggersLayoutPreview = true,
                            onLayoutPreviewValueChange = { value ->
                                onPreviewAppearance(null, value, null, null, null, null)
                            },
                            onValueChange = { value ->
                                onAppearancePreviewCommit()
                                onOpacityChange(value)
                            }
                        )
                    }
                )
                // 闲置判定时长与「停止动图」都是独立功能，不能藏在「闲置隐身」的展开区里：
                // 展开区只在开关打开时渲染，用户会找不到、也调不了（见反馈）。
                add(
                    settingsCardScopeItem("idle-delay") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_idle_delay),
                            value = settings.floatBallIdleDelayMs / 1000f,
                            valueRange = floatBallIdleDelayRange,
                            steps = 19,
                            enabled = true,
                            label = idleDelayValueFormat.format(
                                idleDelaySecondsLabel(settings.floatBallIdleDelayMs / 1000f)
                            ),
                            formatLabel = { seconds ->
                                idleDelayValueFormat.format(idleDelaySecondsLabel(seconds))
                            },
                            onValueChange = { seconds ->
                                onIdleDelayChange((seconds * 1000f).roundToInt())
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("idle-freeze-gif") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_idle_freeze_gif),
                            subtitle = stringResource(R.string.float_ball_idle_freeze_gif_desc),
                            checked = settings.floatBallIdleFreezeGif,
                            enabled = true,
                            onCheckedChange = onIdleFreezeGifChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("hide-when-idle") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.float_ball_hide_when_idle),
                            subtitle = stringResource(R.string.float_ball_hide_when_idle_desc),
                            checked = settings.floatBallHideWhenIdle,
                            enabled = true,
                            onCheckedChange = onHideWhenIdleChange
                        ) {
                            SettingsSliderRow(
                                title = stringResource(R.string.float_ball_idle_opacity),
                                value = settings.floatBallIdleOpacity,
                                valueRange = floatBallOpacityRange,
                                enabled = true,
                                label = fractionPercentLabel(settings.floatBallIdleOpacity),
                                formatLabel = ::fractionPercentLabel,
                                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                                onValueChange = onIdleOpacityChange
                            )
                        }
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "position_section",
            title = positionSectionTitle
        )
        settingsLazyHint(key = "position_xy_hint", text = positionXyHint)
        groupedCardItems(
            keyPrefix = "fb-position",
            items = buildList {
                add(
                    settingsCardScopeItem("position-mode") {
                        SettingDropdownRow(
                            title = stringResource(R.string.float_ball_position),
                            subtitle = floatBallPositionModeLabel(positionMode),
                            items = positionModeEntries.map { floatBallPositionModeLabel(it) },
                            selectedIndex = positionModeIndex,
                            enabled = true,
                            onSelectedIndexChange = { onPositionModeChange(positionModeEntries[it]) }
                        )
                    }
                )
                if (positionMode != FloatBallPositionMode.BOTH_LINES) {
                    add(
                        settingsCardScopeItem("edge-inset") {
                            // 一个滑条同时管"露出多少"和"离边多远"：
                            // 负值往屏幕外挪（等价旧版 50%–99% 半隐藏），0 = 贴边，正值最多挪到屏幕中央。
                            SettingsSliderRow(
                                title = stringResource(R.string.float_ball_edge_inset),
                                value = settings.floatBallEdgeInsetDp.toFloat(),
                                valueRange = minEdgeInsetDp.toFloat()..maxEdgeInsetDp.toFloat(),
                                // 约 4dp 一格；范围随屏幕宽度变化，格数也要跟着算。
                                steps = (((maxEdgeInsetDp - minEdgeInsetDp) / 4) - 1).coerceAtLeast(1),
                                enabled = true,
                                label = edgeInsetLabel(settings.floatBallEdgeInsetDp.toFloat()),
                                formatLabel = ::edgeInsetLabel,
                                triggersLayoutPreview = true,
                                onLayoutPreviewValueChange = { value ->
                                    onPreviewAppearance(null, null, value, null, null, null)
                                },
                                // 拖动期间只走预览；松手才结束预览会话并落盘，避免每个 tick 都清恢复点。
                                onLayoutPreviewStop = { onAppearancePreviewCommit() },
                                onValueChange = { value ->
                                    onEdgeInsetChange(value.roundToInt())
                                }
                            )
                        }
                    )
                }
                add(
                    settingsCardScopeItem("position-y") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_position_y),
                            value = settings.floatBallPositionYFraction,
                            valueRange = FloatBallPositionFractions.MIN_Y..FloatBallPositionFractions.MAX_Y,
                            enabled = true,
                            label = "",
                            formatLabel = { "${(it * 100).roundToInt()}%" },
                            keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onPositionYPreviewStart,
                            // 松手只是"提交"：写入是异步的，这里再还原成拖动前的值会看到
                            // "先回原位、再跳到新位置"。还原留给离开页面/取消预览的路径。
                            onLayoutPreviewStop = { onPositionYPreviewStop(false) },
                            onLayoutPreviewValueChange = onPositionYPreviewChange,
                            onValueChange = { fraction ->
                                onPositionYPreviewStop(false)
                                onPositionYChange(fraction)
                            }
                        )
                    }
                )
            }
        )

        if (showEdgeLineSettings) {
            settingsLazySmallTitle(
                key = "line_section",
                title = lineSectionTitle
            )
            settingsLazyHint(key = "line_width_preview_hint", text = lineWidthPreviewHint)
            groupedCardItems(
                keyPrefix = "fb-line",
                items = buildList {
                    add(
                        settingsCardScopeItem("line-height") {
                            SettingsSliderRow(
                                title = stringResource(R.string.float_ball_line_height),
                                value = settings.floatBallLineHeightFraction,
                                valueRange = floatBallLineHeightRange,
                                enabled = true,
                                label = lineHeightPercentLabel(settings.floatBallLineHeightFraction),
                                formatLabel = ::lineHeightPercentLabel,
                                snapValue = { value ->
                                    (value.coerceIn(floatBallLineHeightRange.start, floatBallLineHeightRange.endInclusive) * 1000f).roundToInt() / 1000f
                                },
                                triggersLayoutPreview = true,
                                onLayoutPreviewValueChange = { value ->
                                    onPreviewAppearance(null, null, null, value, null, null)
                                },
                                onValueChange = { value ->
                                    onAppearancePreviewCommit()
                                    onLineHeightChange(value)
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("line-width") {
                            SettingsSliderRow(
                                title = stringResource(R.string.float_ball_line_width),
                                value = settings.floatBallLineWidthDp,
                                valueRange = floatBallLineWidthRange,
                                enabled = true,
                                label = String.format(
                                    Locale.US,
                                    lineWidthValueFormat,
                                    settings.floatBallLineWidthDp
                                ),
                                formatLabel = { value ->
                                    String.format(Locale.US, lineWidthValueFormat, value)
                                },
                                snapValue = { value ->
                                    value.roundToInt().toFloat()
                                        .coerceIn(floatBallLineWidthRange.start, floatBallLineWidthRange.endInclusive)
                                },
                                triggersLayoutPreview = true,
                                onLayoutPreviewStart = onStripZonePreviewStart,
                                onLayoutPreviewStop = onStripZonePreviewStop,
                                onLayoutPreviewValueChange = { value ->
                                    onPreviewAppearance(null, null, null, null, value, null)
                                },
                                onValueChange = { value ->
                                    onAppearancePreviewCommit()
                                    onLineWidthChange(value)
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("line-opacity") {
                            SettingsSliderRow(
                                title = stringResource(R.string.float_ball_line_opacity),
                                value = settings.floatBallLineOpacity,
                                valueRange = floatBallOpacityRange,
                                enabled = true,
                                label = fractionPercentLabel(settings.floatBallLineOpacity),
                                formatLabel = ::fractionPercentLabel,
                                snapValue = fractionPercentSnap(floatBallOpacityRange),
                                triggersLayoutPreview = true,
                                onLayoutPreviewValueChange = { value ->
                                    onPreviewAppearance(null, null, null, null, null, value)
                                },
                                onValueChange = { value ->
                                    onAppearancePreviewCommit()
                                    onLineOpacityChange(value)
                                }
                            )
                        }
                    )
                }
            )
        }
    }
}

@Composable
internal fun floatBallPositionModeLabel(mode: FloatBallPositionMode): String =
    when (mode) {
        FloatBallPositionMode.LEFT -> stringResource(R.string.float_ball_position_left)
        FloatBallPositionMode.RIGHT -> stringResource(R.string.float_ball_position_right)
        FloatBallPositionMode.BOTH_EDGES -> stringResource(R.string.float_ball_position_both_edges)
        FloatBallPositionMode.BOTH_LINES -> stringResource(R.string.float_ball_position_both_lines)
        FloatBallPositionMode.CUSTOM -> stringResource(R.string.float_ball_position_right)
    }
