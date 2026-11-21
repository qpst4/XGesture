package com.slideindex.app.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.DragDropFlowPosition
import com.slideindex.app.ui.link.LinkAppIcon
import com.slideindex.app.ui.link.linkAppNameCache
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    onGlobalPressEnabledChange: (Boolean) -> Unit = {},
    onGlobalPressTimeoutChange: (Int) -> Unit = {},
    onGlobalPressVibrateChange: (Boolean) -> Unit = {},
    onGlobalPressImmediateChange: (Boolean) -> Unit = {},
    onGlobalPressEarlyTextChange: (Boolean) -> Unit = {},
    onGlobalPressWhitelistChange: (String) -> Unit = {},
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
    val sectionGlobalPress = stringResource(R.string.drag_drop_flow_section_global_press)
    val guideText = stringResource(R.string.drag_drop_flow_guide)
    val hasShizuku = com.slideindex.app.shizuku.ShizukuUserServiceHost.hasShizukuPermission(context)
    val timeoutValues = listOf(300, 500, 700, 1000, 1250, 1500, 1750, 2000)
    val timeoutLabels = listOf("300 ms", "500 ms", "700 ms", "1 秒", "1.25 秒", "1.5 秒", "1.75 秒", "2 秒")
    val currentTimeoutIndex = timeoutValues.indexOf(settings.dragDropFlowGlobalPressTimeoutMs).let {
        if (it < 0) 3 else it
    }
    var showWhitelistSheet by remember { mutableStateOf(false) }
    val whitelistPackages = remember(settings.dragDropFlowGlobalPressWhitelist) {
        if (settings.dragDropFlowGlobalPressWhitelist.isBlank()) {
            emptySet()
        } else {
            settings.dragDropFlowGlobalPressWhitelist
                .split('|', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }

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

        settingsLazySmallTitle(
            key = "drag_drop_flow_global_press_section",
            title = sectionGlobalPress,
        )
        groupedCardItems(
            keyPrefix = "drag_drop_flow_global_press",
            items = buildList {
                add(
                    settingsCardScopeItem("global_press_switch") {
                        SettingSwitchRow(
                            title = stringResource(R.string.drag_drop_flow_global_press_title),
                            subtitle = if (!hasShizuku) {
                                stringResource(R.string.drag_drop_flow_global_press_need_shizuku)
                            } else {
                                stringResource(R.string.drag_drop_flow_global_press_summary)
                            },
                            icon = { label: String -> SymbolsIcon("TouchApp", contentDescription = label) },
                            checked = settings.dragDropFlowGlobalPressEnabled,
                            enabled = settings.dragDropFlowEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !hasShizuku) {
                                    Toast.makeText(context, R.string.drag_drop_flow_global_press_need_shizuku, Toast.LENGTH_SHORT).show()
                                }
                                onGlobalPressEnabledChange(checked)
                            },
                        )
                    }
                )
                if (settings.dragDropFlowGlobalPressEnabled) {
                    add(
                        settingsCardScopeItem("global_press_timeout") {
                            SettingDropdownRow(
                                title = stringResource(R.string.drag_drop_flow_global_press_timeout_title),
                                subtitle = stringResource(R.string.drag_drop_flow_global_press_timeout_summary, settings.dragDropFlowGlobalPressTimeoutMs),
                                items = timeoutLabels,
                                selectedIndex = currentTimeoutIndex,
                                enabled = settings.dragDropFlowEnabled,
                                icon = { label: String -> SymbolsIcon("Timer", contentDescription = label) },
                                onSelectedIndexChange = { index ->
                                    onGlobalPressTimeoutChange(timeoutValues[index])
                                },
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("global_press_vibrate") {
                            SettingSwitchRow(
                                title = stringResource(R.string.drag_drop_flow_global_press_vibrate_title),
                                subtitle = stringResource(R.string.drag_drop_flow_global_press_vibrate_summary),
                                icon = { label: String -> SymbolsIcon("Vibration", contentDescription = label) },
                                checked = settings.dragDropFlowGlobalPressVibrate,
                                enabled = settings.dragDropFlowEnabled,
                                onCheckedChange = onGlobalPressVibrateChange,
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("global_press_immediate") {
                            SettingSwitchRow(
                                title = stringResource(R.string.drag_drop_flow_global_press_immediate_title),
                                subtitle = stringResource(R.string.drag_drop_flow_global_press_immediate_summary),
                                icon = { label: String -> SymbolsIcon("Bolt", contentDescription = label) },
                                checked = settings.dragDropFlowGlobalPressImmediate,
                                enabled = settings.dragDropFlowEnabled,
                                onCheckedChange = onGlobalPressImmediateChange,
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("global_press_early_text") {
                            SettingSwitchRow(
                                title = stringResource(R.string.drag_drop_flow_global_press_early_text_title),
                                subtitle = stringResource(R.string.drag_drop_flow_global_press_early_text_summary),
                                icon = { label: String -> SymbolsIcon("AutoAwesome", contentDescription = label) },
                                checked = settings.dragDropFlowGlobalPressEarlyText,
                                enabled = settings.dragDropFlowEnabled,
                                onCheckedChange = onGlobalPressEarlyTextChange,
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("global_press_whitelist") {
                            val whitelistSummary = if (whitelistPackages.isEmpty()) {
                                "全部应用生效（点击可限定生效应用范围）"
                            } else {
                                "仅在已选的 ${whitelistPackages.size} 个应用中生效"
                            }
                            SettingNavigationRow(
                                title = "生效应用白名单",
                                subtitle = whitelistSummary,
                                icon = { label: String -> SymbolsIcon("Apps", contentDescription = label) },
                                enabled = settings.dragDropFlowEnabled,
                                onClick = { showWhitelistSheet = true },
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

    GlobalPressWhitelistBottomSheet(
        show = showWhitelistSheet,
        selectedPackages = whitelistPackages,
        onDismissRequest = { showWhitelistSheet = false },
        onSave = { selected ->
            onGlobalPressWhitelistChange(selected.joinToString("|"))
            showWhitelistSheet = false
        },
    )
}

private data class WhitelistAppItem(
    val packageName: String,
    val label: String,
)

@Composable
private fun GlobalPressWhitelistBottomSheet(
    show: Boolean,
    selectedPackages: Set<String>,
    onDismissRequest: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val listMaxHeight = (screenHeightDp * 0.52f).coerceIn(320f, 500f).dp
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var allApps by remember { mutableStateOf<List<WhitelistAppItem>>(emptyList()) }
    val currentSelection = remember(selectedPackages) {
        mutableStateListOf<String>().apply { addAll(selectedPackages) }
    }

    LaunchedEffect(selectedPackages) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = runCatching { pm.queryIntentActivities(mainIntent, 0) }.getOrDefault(emptyList())
            val list = resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { pkg }
                    linkAppNameCache[pkg] = label
                    WhitelistAppItem(packageName = pkg, label = label)
                }
                .distinctBy { it.packageName }
                .sortedWith(
                    compareByDescending<WhitelistAppItem> { it.packageName in selectedPackages }
                        .thenBy { it.label.lowercase() }
                )
            allApps = list
        }
        isLoading = false
    }

    val filteredApps = remember(allApps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) allApps
        else allApps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    MiuixBottomSheet(
        show = true,
        title = "生效应用白名单",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiuixText(
                text = "未勾选任何应用时默认在全部应用生效；勾选后仅在所选应用中启用全局长按强行拖放。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            MiuixLabeledTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "搜索应用名称或包名",
                useLabelAsPlaceholder = false,
                singleLine = true,
            )

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    MiuixText(
                        text = "正在加载已安装应用…",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                val selectedCount = currentSelection.size
                val itemShape = RoundedCornerShape(16.dp)
                val primaryColor = MiuixTheme.colorScheme.primary

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName in currentSelection
                        val cardBgColor = if (isSelected) {
                            primaryColor.copy(alpha = 0.12f)
                        } else {
                            MiuixTheme.colorScheme.surfaceContainer
                        }
                        val cardBorderColor = if (isSelected) {
                            primaryColor.copy(alpha = 0.55f)
                        } else {
                            Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(cardBgColor)
                                .border(width = 1.dp, color = cardBorderColor, shape = itemShape)
                                .clickable {
                                    if (isSelected) currentSelection.remove(app.packageName)
                                    else currentSelection.add(app.packageName)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LinkAppIcon(packageName = app.packageName, size = 40.dp)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                MiuixText(
                                    text = app.label,
                                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MiuixText(
                                    text = app.packageName,
                                    style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Checkbox(
                                state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                                onClick = {
                                    if (isSelected) currentSelection.remove(app.packageName)
                                    else currentSelection.add(app.packageName)
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = if (selectedCount == 0) "当前：全部应用生效" else "已选 $selectedCount 个应用",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                        color = primaryColor,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (selectedCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { currentSelection.clear() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                MiuixText(
                                    text = "清空（全部生效）",
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                                    color = primaryColor,
                                )
                            }
                        } else if (filteredApps.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        currentSelection.clear()
                                        filteredApps.forEach { currentSelection.add(it.packageName) }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                MiuixText(
                                    text = "全选",
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                                    color = primaryColor,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f),
                    thickness = 0.5.dp,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(primaryColor.copy(alpha = 0.16f))
                        .clickable {
                            onSave(currentSelection.toSet())
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = "保存",
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
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

    CardSegment(
        isFirst = true,
        isLast = true,
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
