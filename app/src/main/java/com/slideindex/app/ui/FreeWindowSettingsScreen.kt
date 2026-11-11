package com.slideindex.app.ui

import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppLaunchPolicy
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.ignoresLaunchBounds
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.resolvedLaunchPolicy
import com.slideindex.app.settings.titleRes
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreeWindowSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLongPressDurationChange: (Int) -> Unit,
    onLaunchPolicyChange: (Int) -> Unit,
    onFollowTriggerSideChange: (Boolean) -> Unit,
    onOpenMode: () -> Unit,
    onOpenLayoutEditor: () -> Unit
) {
    val selectedMode = settings.resolvedFreeWindowMode()
    // 米窗这类模式自己决定窗口几何，尺寸 / 位置 / 跟随对它无效 → 一并灰掉并说明原因。
    val layoutApplies = !selectedMode.ignoresLaunchBounds()
    val layoutRowEnabled = settings.freeWindowEnabled && layoutApplies
    // 系统二次缩放后的"可靠区间"（小米竖屏 70% / 横屏 55.5%，其它机型 100%）。
    // 只用来提示，不限制用户能配多大、也不改写已存配置——它还是未在真机验证过的假设。
    val portraitCap = FreeWindowLauncher.deliverableLimitFraction(selectedMode, isLandscape = false)
    val landscapeCap = FreeWindowLauncher.deliverableLimitFraction(selectedMode, isLandscape = true)
    val capApplies = layoutApplies && (portraitCap < 1f || landscapeCap < 1f)
    // 提示文案要在 lazy 作用域外算好：`settingsLazyTipCard` 是 LazyListScope 扩展，里面不能调 @Composable。
    val capHint = if (capApplies) {
        stringResource(
            R.string.free_window_cap_hint,
            (portraitCap * 100).roundToInt(),
            (landscapeCap * 100).roundToInt(),
        )
    } else {
        null
    }
    val selectedPolicy = settings.resolvedLaunchPolicy()
    val longPressDuration = settings.effectiveLongPressDurationMs()
    val showLongPressDuration = selectedPolicy.usesLongPress()
    val resources = LocalResources.current
    val formatDurationLabel = remember(resources) {
        { ms: Float ->
            resources.getString(R.string.long_press_launch_duration_value, ms.roundToInt())
        }
    }
    val launchPolicyEntries = AppLaunchPolicy.entries
    val launchPolicyIndex = launchPolicyEntries.indexOf(selectedPolicy).coerceAtLeast(0)

    val launchSectionTitle = stringResource(R.string.settings_section_launch)
    val freeWindowSectionTitle = stringResource(R.string.settings_section_free_window)
    val freeWindowModeHint = stringResource(R.string.free_window_mode_hint)

    SettingsScreenScaffold(
        title = stringResource(R.string.free_window_entry_title),
        pageHint = stringResource(R.string.free_window_entry_desc),
        onBack = onBack
    ) {
        settingsLazySmallTitle(
            key = "section-launch",
            title = launchSectionTitle
        )
        groupedCardItems(
            keyPrefix = "free-window-launch",
            items = buildList {
                add(
                    settingsCardScopeItem("free-window-enabled") {
                        SettingToggleRow(
                            icon = { label -> SymbolsIcon("PowerSettingsNew", contentDescription = label) },
                            title = stringResource(R.string.free_window_enabled),
                            subtitle = stringResource(R.string.free_window_enabled_desc),
                            checked = settings.freeWindowEnabled,
                            onCheckedChange = onEnabledChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("launch-policy") {
                        SettingDropdownRow(
                            icon = { label -> SymbolsIcon("Launch", contentDescription = label, mirrored = true)
                            },
                            title = stringResource(R.string.launch_policy_title),
                            subtitle = null,
                            items = launchPolicyEntries.map { stringResource(it.titleRes) },
                            selectedIndex = launchPolicyIndex,
                            enabled = settings.freeWindowEnabled,
                            onSelectedIndexChange = { onLaunchPolicyChange(launchPolicyEntries[it].id) }
                        )
                    }
                )
                if (showLongPressDuration) {
                    add(
                        settingsCardScopeItem("long-press-duration") {
                            SettingsSliderRow(
                                title = stringResource(R.string.long_press_launch_duration),
                                value = longPressDuration.toFloat(),
                                valueRange = 250f..900f,
                                steps = 12,
                                enabled = settings.freeWindowEnabled,
                                label = formatDurationLabel(longPressDuration.toFloat()),
                                snapValue = { value ->
                                    ((value / 50f).roundToInt() * 50).toFloat().coerceIn(250f, 900f)
                                },
                                formatLabel = formatDurationLabel,
                                onValueChange = { value -> onLongPressDurationChange(value.roundToInt()) }
                            )
                        }
                    )
                }
            }
        )
        settingsLazySmallTitle(
            key = "section-free-window",
            title = freeWindowSectionTitle
        )
        settingsLazyTipCard(key = "free-window-mode-tip", text = freeWindowModeHint)
        // 小米会被 MIUI 二次缩放：只把"可靠区间"讲清楚，不限制用户能设多大。
        capHint?.let { hint -> settingsLazyTipCard(key = "free-window-cap-tip", text = hint) }
        groupedCardItems(
            keyPrefix = "free-window-mode",
            items = buildList {
                add(
                    settingsCardScopeItem("free-window-launch-mode") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("Layers", contentDescription = label) },
                            title = stringResource(R.string.free_window_launch_mode),
                            subtitle = stringResource(selectedMode.titleRes),
                            enabled = settings.freeWindowEnabled,
                            onClick = onOpenMode
                        )
                    }
                )
                add(
                    settingsCardScopeItem("free-window-adjust-layout") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("AspectRatio", contentDescription = label) },
                            title = stringResource(R.string.free_window_adjust_layout),
                            subtitle = if (layoutApplies) {
                                stringResource(
                                    R.string.free_window_layout_summary,
                                    (settings.freeWindowWidthFraction * 100).roundToInt(),
                                    (settings.freeWindowHeightFraction * 100).roundToInt(),
                                    (settings.freeWindowLandWidthFraction * 100).roundToInt(),
                                    (settings.freeWindowLandHeightFraction * 100).roundToInt(),
                                )
                            } else {
                                stringResource(R.string.free_window_layout_unsupported)
                            },
                            enabled = layoutRowEnabled,
                            onClick = onOpenLayoutEditor
                        )
                    }
                )
                add(
                    settingsCardScopeItem("free-window-follow-trigger") {
                        SettingToggleRow(
                            icon = { label -> SymbolsIcon("TouchApp", contentDescription = label) },
                            title = stringResource(R.string.free_window_follow_trigger),
                            subtitle = stringResource(R.string.free_window_follow_trigger_desc),
                            checked = settings.freeWindowFollowTriggerSide,
                            enabled = layoutRowEnabled,
                            onCheckedChange = onFollowTriggerSideChange
                        )
                    }
                )
            }
        )
    }
}

@Composable
fun SettingsCardScope.FreeWindowEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.freeWindow(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.free_window_entry_title),
        subtitle = stringResource(R.string.free_window_entry_desc),
        onClick = onClick
    )
}
