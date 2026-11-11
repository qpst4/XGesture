package com.slideindex.app.ui

import android.content.ClipData
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.DragDropFlowPosition
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

/** 下拉框按枚举顺序展示，文案走资源，别再在这里写死中文。 */
private val DragDropFlowPosition.labelRes: Int
    get() = when (this) {
        DragDropFlowPosition.BOTTOM -> R.string.drag_drop_flow_position_bottom
        DragDropFlowPosition.LEFT -> R.string.drag_drop_flow_position_left
        DragDropFlowPosition.RIGHT -> R.string.drag_drop_flow_position_right
        DragDropFlowPosition.RING -> R.string.drag_drop_flow_position_ring
    }

@Composable
fun DragDropFlowSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPositionChange: (DragDropFlowPosition) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onActionsInlineChange: (Boolean) -> Unit,
    onOpenTextSearchEngines: (() -> Unit)? = null,
    onOpenImageSearchEngines: (() -> Unit)? = null,
    onOpenFileShareEngines: (() -> Unit)? = null,
) {
    val currentPosition = settings.dragDropFlowPosition
    val positionLabels = DragDropFlowPosition.entries.map { stringResource(it.labelRes) }
    val positionIndex = currentPosition.id.coerceIn(0, DragDropFlowPosition.entries.lastIndex)
    val context = LocalContext.current

    // LazyListScope 顶层（settingsLazySmallTitle / settingsLazyTipCard）不是 @Composable 上下文，
    // 文案必须在这里先取出来。
    val sectionBasic = stringResource(R.string.drag_drop_flow_section_basic)
    val triggerHint = stringResource(R.string.drag_drop_flow_trigger_hint)
    val sectionLayout = stringResource(R.string.drag_drop_flow_section_layout)
    val sectionTargets = stringResource(R.string.drag_drop_flow_section_targets)
    val guideText = stringResource(R.string.drag_drop_flow_guide)

    SettingsScreenScaffold(
        title = stringResource(R.string.drag_drop_flow_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "drag_drop_flow_basic_section",
            title = sectionBasic,
        )
        groupedCardItems(
            keyPrefix = "drag_drop_flow_basic",
            items = buildList {
                add(
                    settingsCardScopeItem("switch") {
                        SettingSwitchRow(
                            title = stringResource(R.string.drag_drop_flow_enable_title),
                            subtitle = stringResource(R.string.drag_drop_flow_enable_summary),
                            icon = { label: String -> SymbolsIcon("PowerSettingsNew", contentDescription = label) },
                            checked = settings.dragDropFlowEnabled,
                            enabled = true,
                            onCheckedChange = { checked ->
                                onEnabledChange(checked)
                                if (checked) {
                                    Toast.makeText(
                                        context,
                                        R.string.drag_drop_flow_enabled_toast,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                )
            }
        )

        // 「怎么触发」就写在这里：大多数人是在扩展中心点进来，不会再往下翻到页尾的说明卡。
        settingsLazyHint(
            key = "drag_drop_flow_trigger_hint",
            text = triggerHint,
        )
        if (settings.dragDropFlowEnabled) {
            item(key = "drag_drop_flow_try_it") {
                DragFlowTryItCard()
            }
        }

        settingsLazySmallTitle(
            key = "drag_drop_flow_layout_section",
            title = sectionLayout,
        )
        groupedCardItems(
            keyPrefix = "drag_drop_flow_layout",
            items = buildList {
                add(
                    settingsCardScopeItem("position") {
                        SettingDropdownRow(
                            title = stringResource(R.string.drag_drop_flow_position_title),
                            subtitle = stringResource(R.string.drag_drop_flow_position_summary),
                            items = positionLabels,
                            selectedIndex = positionIndex,
                            enabled = settings.dragDropFlowEnabled,
                            icon = { label: String -> SymbolsIcon("OpenWith", contentDescription = label) },
                            onSelectedIndexChange = { index ->
                                val target = DragDropFlowPosition.fromId(index)
                                onPositionChange(target)
                            },
                        )
                    }
                )
                add(
                    settingsCardScopeItem("haptic") {
                        SettingSwitchRow(
                            title = stringResource(R.string.drag_drop_flow_haptic_title),
                            subtitle = stringResource(R.string.drag_drop_flow_haptic_summary),
                            icon = { label: String -> SymbolsIcon("Vibration", contentDescription = label) },
                            checked = settings.dragDropFlowHapticEnabled,
                            enabled = settings.dragDropFlowEnabled,
                            onCheckedChange = onHapticChange,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("actions_inline") {
                        SettingSwitchRow(
                            title = stringResource(R.string.drag_drop_flow_inline_actions_title),
                            subtitle = stringResource(R.string.drag_drop_flow_inline_actions_summary),
                            icon = { label: String -> SymbolsIcon("Apps", contentDescription = label) },
                            checked = settings.dragDropFlowActionsInline,
                            enabled = settings.dragDropFlowEnabled,
                            onCheckedChange = onActionsInlineChange,
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "drag_drop_flow_targets_section",
            title = sectionTargets,
        )
        groupedCardItems(
            keyPrefix = "drag_drop_flow_targets",
            items = buildList {
                if (onOpenTextSearchEngines != null) {
                    add(
                        settingsCardScopeItem("text_engines") {
                            SettingNavigationRow(
                                title = stringResource(R.string.drag_drop_flow_text_engines_title),
                                subtitle = stringResource(R.string.drag_drop_flow_text_engines_summary),
                                icon = { label: String -> SymbolsIcon("Search", contentDescription = label) },
                                enabled = settings.dragDropFlowEnabled,
                                onClick = onOpenTextSearchEngines,
                            )
                        }
                    )
                }
                if (onOpenImageSearchEngines != null) {
                    add(
                        settingsCardScopeItem("image_engines") {
                            SettingNavigationRow(
                                title = stringResource(R.string.drag_drop_flow_image_engines_title),
                                subtitle = stringResource(R.string.drag_drop_flow_image_engines_summary),
                                icon = { label: String -> SymbolsIcon("ImageSearch", contentDescription = label) },
                                enabled = settings.dragDropFlowEnabled,
                                onClick = onOpenImageSearchEngines,
                            )
                        }
                    )
                }
                if (onOpenFileShareEngines != null) {
                    add(
                        settingsCardScopeItem("file_targets") {
                            SettingNavigationRow(
                                title = stringResource(R.string.drag_drop_flow_file_engines_title),
                                subtitle = stringResource(R.string.drag_drop_flow_file_engines_summary),
                                icon = { label: String -> SymbolsIcon("FolderShared", contentDescription = label) },
                                enabled = settings.dragDropFlowEnabled,
                                onClick = onOpenFileShareEngines,
                            )
                        }
                    )
                }
            }
        )

        settingsLazyTipCard(
            key = "drag_drop_flow_guide_tip",
            text = guideText,
        )
    }
}

/**
 * 「试一试」：长按拖起一段示例文字，验证流转栏能不能被唤出。
 *
 * 走系统拖拽（[View.DRAG_FLAG_GLOBAL]），与真实场景是同一条链路：起拖后常驻的
 * 1px 锚点收到 ACTION_DRAG_STARTED，才会把流转栏展开。用户问"怎么触发"时，
 * 让他在这里拖一次比看十行说明快。
 */
@Composable
private fun DragFlowTryItCard() {
    val scheme = MaterialTheme.colorScheme
    val sample = stringResource(R.string.drag_drop_flow_demo_sample)
    val textColor = scheme.onSurface.toArgb()
    val chipColor = scheme.primary.copy(alpha = 0.12f).toArgb()

    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = stringResource(R.string.drag_drop_flow_demo_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.drag_drop_flow_demo_body),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.62f),
            )
            Spacer(Modifier.height(10.dp))
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        text = sample
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        setTextColor(textColor)
                        setPadding(28, 22, 28, 22)
                        background = GradientDrawable().apply {
                            cornerRadius = 24f
                            setColor(chipColor)
                        }
                        isClickable = true
                        isLongClickable = true
                        setOnLongClickListener { target ->
                            val clip = ClipData.newPlainText("text", text)
                            target.startDragAndDrop(
                                clip,
                                View.DragShadowBuilder(target),
                                null,
                                View.DRAG_FLAG_GLOBAL,
                            )
                        }
                    }
                },
                update = { view -> view.text = sample },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
