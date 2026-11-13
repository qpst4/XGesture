package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsGroupedCardItems
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton

/**
 * 「快速启动器」动作的参数配置页（在动作绑定处以 Dialog 形式弹出）。
 *
 * 选择三件事：
 * 1. 打开**哪个面板**（写入 [com.slideindex.app.gesture.GestureAction.QuickLauncher.panelId]）；
 * 2. 是否**钉住**（写入 `pinned`）：勾上后，呼出的面板会常驻 —— 点面板里的动作不再收起、
 *    点面板外部也不收起（外部触摸直接落到下面的应用），只有再划一次同一手势或点工具栏「×」才关。
 * 3. 钉住时是否**只钉在当前应用**（写入 `pinCurrentApp`）：切到别的应用自动隐藏，切回来原样恢复。
 *
 * 钉住是**动作级**参数而不是面板属性：同一个面板完全可以被两个手势分别以「普通 / 钉住」呼出。
 */
@Composable
fun GestureQuickLauncherActionScreen(
    panels: List<QuickLauncherPanel>,
    initialPanelId: String,
    initialPinned: Boolean,
    initialPinCurrentApp: Boolean,
    onBack: () -> Unit,
    onConfirm: (panelId: String, pinned: Boolean, pinCurrentApp: Boolean) -> Unit,
    overlayMode: Boolean = false,
) {
    val effectivePanels = remember(panels) { QuickLauncherPanelDefaults.effectivePanels(panels) }
    var selectedPanelId by remember(initialPanelId, effectivePanels) {
        mutableStateOf(
            QuickLauncherPanelDefaults.resolvePanelId(effectivePanels, initialPanelId),
        )
    }
    var pinned by remember(initialPinned) { mutableStateOf(initialPinned) }
    // 不钉住时"仅当前应用"没有意义，一律按关处理（也顺手把 UI 状态收敛回干净值）。
    var pinCurrentApp by remember(initialPinned, initialPinCurrentApp) {
        mutableStateOf(initialPinned && initialPinCurrentApp)
    }

    val panelSectionTitle = stringResource(R.string.quick_launcher_action_panel_section)
    val pinSectionTitle = stringResource(R.string.quick_launcher_action_pin_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.quick_launcher_action_config_title),
        pageHint = stringResource(R.string.quick_launcher_action_config_hint),
        onBack = onBack,
        overlayMode = overlayMode,
        actions = {
            MiuixTopBarIconButton(
                // 没有面板可选时禁止确认：否则会写入 panelId="" 的绑定（运行期等价于第一个面板，
                // 面板顺序一变就漂移，用户看着像"设了没反应"）。
                enabled = selectedPanelId.isNotBlank(),
                onClick = {
                    onConfirm(
                        selectedPanelId,
                        pinned,
                        pinned && pinCurrentApp,
                    )
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Medium.Ok,
                    contentDescription = stringResource(R.string.confirm),
                )
            }
        },
    ) {
        settingsLazySmallTitle(
            key = "ql_action_panel",
            title = panelSectionTitle,
        )
        settingsGroupedCardItems(
            keyPrefix = "ql_action_panel",
            items = buildList {
                if (effectivePanels.isEmpty()) {
                    add(
                        settingsCardScopeItem("empty") {
                            Text(
                                text = stringResource(R.string.quick_launcher_action_panel_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        },
                    )
                } else {
                    effectivePanels.forEach { panel ->
                        add(
                            settingsCardScopeItem("panel-${panel.id}") {
                                QuickLauncherActionChoiceRow(
                                    title = panel.name.ifBlank {
                                        stringResource(R.string.quick_launcher_label_fallback)
                                    },
                                    subtitle = stringResource(
                                        R.string.quick_launcher_action_panel_cells,
                                        panel.columnsPerPage,
                                        panel.rowsPerPage,
                                    ),
                                    selected = selectedPanelId == panel.id,
                                    onClick = { selectedPanelId = panel.id },
                                )
                            },
                        )
                    }
                }
            },
        )

        settingsLazySmallTitle(
            key = "ql_action_pin",
            title = pinSectionTitle,
        )
        settingsGroupedCardItems(
            keyPrefix = "ql_action_pin",
            items = buildList {
                add(
                    settingsCardScopeItem("pin-toggle") {
                        QuickWheelSwitchRow(
                            title = stringResource(R.string.quick_launcher_action_pin_row),
                            description = stringResource(R.string.quick_launcher_action_pin_row_desc),
                            checked = pinned,
                            onCheckedChange = { pinned = it },
                        )
                    },
                )
                // 子选项只在钉住时才有意义：不钉住就自动隐藏，避免出现「只在本应用可见但不钉住」这种矛盾组合。
                if (pinned) {
                    add(
                        settingsCardScopeItem("pin-current-app") {
                            QuickWheelSwitchRow(
                                title = stringResource(R.string.quick_launcher_action_pin_current_app_row),
                                description = stringResource(
                                    R.string.quick_launcher_action_pin_current_app_row_desc,
                                ),
                                checked = pinCurrentApp,
                                onCheckedChange = { pinCurrentApp = it },
                            )
                        },
                    )
                }
            },
        )
    }
}

/** 单选行：选中时右侧显示对勾（与「呼出形态」页的单选行同一视觉）。 */
@Composable
private fun QuickLauncherActionChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
