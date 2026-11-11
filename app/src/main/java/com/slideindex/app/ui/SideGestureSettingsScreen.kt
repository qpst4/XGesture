package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SwipeDirectionFamily
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SwipeHoverDurationLimits
import com.slideindex.app.settings.slotAction
import com.slideindex.app.settings.defaultTriggerModeFor
import com.slideindex.app.settings.slotTriggerMode
import com.slideindex.app.settings.gestureConfigSide
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixInsetCardComponentMargin
import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.ui.miuix.MiuixTabSettingsCard
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

private enum class SideGestureDistanceTab {
    Short,
    Long,
    Compound,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SideGestureSettingsScreen(
    side: PanelSide,
    handleId: String,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDesignSettings: () -> Unit,
    onOpenDefaultModePick: () -> Unit,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
    onAlignOppositeGesturesChange: (enabled: Boolean, mirrorSourceSide: PanelSide?) -> Unit = { _, _ -> },
    onSwipeHoverDurationChange: (Int) -> Unit = {},
    onPreviewStart: () -> Unit = {},
    onPreviewStop: () -> Unit = {},
) {
    val pairIndex = settings.triggerCollectionEntries().indexOfFirst { it.handleId == handleId }.let {
        if (it >= 0) it + 1 else 1
    }
    val pairCount = settings.triggerCollectionEntries().size
    val selectedHandle = settings.triggerHandle(side, handleId)
        ?: settings.primaryTriggerHandle(side)
    val baseTitle = when (side) {
        PanelSide.LEFT -> stringResource(R.string.side_gestures_left_title)
        PanelSide.RIGHT -> stringResource(R.string.side_gestures_right_title)
        PanelSide.BOTTOM -> stringResource(R.string.side_gestures_bottom_title)
        PanelSide.TOP -> stringResource(R.string.side_gestures_top_title)
    }
    val title = if (pairCount > 1) "$baseTitle · $pairIndex" else baseTitle

    TriggerHandlePreviewLifecycle(
        enabled = serviceEnabled,
        side = side,
        handleId = handleId,
        onPreviewStart = { _, _ -> onPreviewStart() },
        onPreviewStop = onPreviewStop,
    )

    val slotSide = settings.gestureConfigSide(side, handleId)
    var showMirrorDirectionDialog by remember { mutableStateOf(false) }
    // 进子页再返回要停留原 tab：用 rememberSaveable，随返回栈条目一起保留。
    var selectedTab by rememberSaveable { mutableStateOf(SideGestureDistanceTab.Short) }
    val showAlignGesturesSwitch = side.isHorizontalEdge &&
        settings.triggerHandle(PanelSide.LEFT, handleId) != null &&
        settings.triggerHandle(PanelSide.RIGHT, handleId) != null

    val triggerModeSectionTitle = stringResource(R.string.side_gestures_trigger_mode_section)
    val behaviorSectionTitle = stringResource(R.string.side_gestures_behavior_section)
    val swipeDirectionsSectionTitle = stringResource(R.string.side_gestures_swipe_directions_section)
    val pressTapSectionTitle = stringResource(R.string.side_gestures_press_tap)
    val straightSectionTitle = stringResource(R.string.side_gestures_direction_straight_section)
    val hoverSectionTitle = stringResource(R.string.side_gestures_direction_pause_section)
    val hoverDurationSummary = stringResource(R.string.side_gestures_hover_duration_desc)
    val compoundHint = stringResource(R.string.side_gestures_inward_branch_hint)
    val compoundInwardGroupTitle = stringResource(R.string.side_gestures_compound_group_inward)
    val compoundAlongGroupTitle = stringResource(R.string.side_gestures_compound_group_along)
    val resources = LocalContext.current.resources

    val pressTapItems = sideGestureSlotCardItems(
        settings = settings,
        slotSide = slotSide,
        handleId = handleId,
        side = side,
        triggers = GestureTriggerType.pressTapEntries(),
        titleStyle = SideGestureSlotTitleStyle.TriggerLabel,
        onOpenSlotConfig = onOpenSlotConfig,
    )

    SettingsLazyScreenScaffold(
        title = title,
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    ) {
        settingsLazySmallTitle(
            key = "section-swipe-directions",
            title = swipeDirectionsSectionTitle,
        )

        item(key = "swipe-distance-tab-card") {
            val distanceTabs = SideGestureDistanceTab.entries.map { tab ->
                stringResource(
                    when (tab) {
                        SideGestureDistanceTab.Short -> R.string.side_gestures_tab_short_distance
                        SideGestureDistanceTab.Long -> R.string.side_gestures_tab_long_distance
                        SideGestureDistanceTab.Compound -> R.string.side_gestures_tab_compound
                    },
                )
            }
            MiuixTabSettingsCard(
                tabs = distanceTabs,
                selectedTabIndex = selectedTab.ordinal,
                onTabSelected = { selectedTab = SideGestureDistanceTab.entries[it] },
            ) {
                when (selectedTab) {
                    SideGestureDistanceTab.Short -> {
                        SmallTitle(
                            text = straightSectionTitle,
                            insideMargin = PaddingValues(bottom = 4.dp),
                        )
                        RenderSideGestureSlotItems(
                            sideGestureSlotCardItems(
                                settings = settings,
                                slotSide = slotSide,
                                handleId = handleId,
                                side = side,
                                triggers = SwipeDirectionFamily.orderedEntries().map { it.shortTrigger },
                                titleStyle = SideGestureSlotTitleStyle.SlotLabel,
                                onOpenSlotConfig = onOpenSlotConfig,
                                rowInsideMargin = MiuixInsetCardComponentMargin,
                            ),
                        )
                        SmallTitle(
                            text = hoverSectionTitle,
                            insideMargin = PaddingValues(top = 8.dp, bottom = 4.dp),
                        )
                        RenderSideGestureSlotItems(
                            sideGestureSlotCardItems(
                                settings = settings,
                                slotSide = slotSide,
                                handleId = handleId,
                                side = side,
                                triggers = SwipeDirectionFamily.orderedEntries().map { it.hoverTrigger },
                                titleStyle = SideGestureSlotTitleStyle.TriggerLabel,
                                onOpenSlotConfig = onOpenSlotConfig,
                                rowInsideMargin = MiuixInsetCardComponentMargin,
                            ),
                        )
                        // 悬停时长滑条放到卡片最底部，避免把「直向」和「悬停」两组槽位夹断。
                        MiuixSliderRow(
                            title = stringResource(R.string.side_gestures_hover_duration),
                            summary = hoverDurationSummary,
                            value = settings.swipeHoverDurationMs.toFloat(),
                            valueRange = SwipeHoverDurationLimits.MIN_MS.toFloat()..SwipeHoverDurationLimits.MAX_MS.toFloat(),
                            enabled = serviceEnabled,
                            insideMargin = MiuixInsetCardComponentMargin,
                            steps = 0,
                            showKeyPoints = true,
                            keyPoints = SwipeHoverDurationLimits.UI_KEY_POINTS,
                            commitOnFinish = true,
                            formatLabel = { ms ->
                                resources.getString(
                                    R.string.side_gestures_hover_duration_value,
                                    ms.roundToInt(),
                                )
                            },
                            onValueChange = { onSwipeHoverDurationChange(it.roundToInt()) },
                        )
                    }

                    SideGestureDistanceTab.Long -> {
                        RenderSideGestureSlotItems(
                            sideGestureSlotCardItems(
                                settings = settings,
                                slotSide = slotSide,
                                handleId = handleId,
                                side = side,
                                triggers = SwipeDirectionFamily.orderedEntries().map { it.longTrigger },
                                titleStyle = SideGestureSlotTitleStyle.SlotLabel,
                                onOpenSlotConfig = onOpenSlotConfig,
                                rowInsideMargin = MiuixInsetCardComponentMargin,
                            ),
                        )
                    }

                    SideGestureDistanceTab.Compound -> {
                        MiuixHintText(
                            text = compoundHint,
                            horizontalPadding = 0.dp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        SmallTitle(
                            text = compoundInwardGroupTitle,
                            insideMargin = PaddingValues(bottom = 4.dp),
                        )
                        RenderSideGestureSlotItems(
                            sideGestureCompoundPathItems(
                                settings = settings,
                                slotSide = slotSide,
                                handleId = handleId,
                                side = side,
                                paths = inwardCompoundPaths(),
                                onOpenSlotConfig = onOpenSlotConfig,
                            ),
                        )
                        SmallTitle(
                            text = compoundAlongGroupTitle,
                            insideMargin = PaddingValues(top = 8.dp, bottom = 4.dp),
                        )
                        RenderSideGestureSlotItems(
                            sideGestureCompoundPathItems(
                                settings = settings,
                                slotSide = slotSide,
                                handleId = handleId,
                                side = side,
                                paths = alongCompoundPaths(),
                                onOpenSlotConfig = onOpenSlotConfig,
                            ),
                        )
                    }
                }
            }
        }

        settingsLazySmallTitle(
            key = "section-press-tap",
            title = pressTapSectionTitle,
        )
        groupedCardItems("side-gesture-press-tap", pressTapItems)

        // 触发模式与「布局与行为」都不属于槽位本身：前者是设一次就不动的全局缺省，
        // 后者全是跳转子页的入口，一并放在槽位之后，让首屏直接给出槽位动作列表。
        settingsLazySmallTitle(
            key = "section-trigger-mode",
            title = triggerModeSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "side-gesture-trigger-mode",
            items = buildList {
                if (showAlignGesturesSwitch) {
                    add(
                        settingsCardScopeItem("align-opposite-gestures") {
                            SettingSwitchRow(
                                title = stringResource(R.string.align_opposite_gestures),
                                subtitle = stringResource(R.string.align_opposite_gestures_desc),
                                checked = selectedHandle.alignOppositeGestures,
                                enabled = serviceEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        showMirrorDirectionDialog = true
                                    } else {
                                        onAlignOppositeGesturesChange(false, null)
                                    }
                                },
                            )
                        },
                    )
                }
            },
        )

        settingsLazySmallTitle(
            key = "section-behavior",
            title = behaviorSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "side-gesture-behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("trigger-design") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("Brush", contentDescription = label) },
                            title = stringResource(R.string.trigger_design_title),
                            subtitle = triggerDesignSummary(selectedHandle.design),
                            onClick = onOpenDesignSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("trigger-appearance") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("Animation", contentDescription = label) },
                            title = stringResource(R.string.trigger_appearance_title),
                            subtitle = triggerAppearanceSummary(settings, side, handleId),
                            onClick = onOpenAppearanceSettings,
                        )
                    },
                )
            },
        )

        item(key = "side-gesture-bottom-spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showMirrorDirectionDialog) {
        AlignOppositeGesturesMirrorDialog(
            currentSide = side,
            onDismiss = { showMirrorDirectionDialog = false },
            onCopyCurrentToOpposite = {
                showMirrorDirectionDialog = false
                onAlignOppositeGesturesChange(true, side)
            },
            onCopyOppositeToCurrent = {
                showMirrorDirectionDialog = false
                onAlignOppositeGesturesChange(true, side.opposite())
            },
        )
    }
}

/** 组合页的一条路径：短滑档 + 可选长滑档（折返只有短滑档）。 */
private data class CompoundSlotPath(
    val shortTrigger: GestureTriggerType,
    val longTrigger: GestureTriggerType? = null,
) {
    val tiers: List<GestureTriggerType> get() = listOfNotNull(shortTrigger, longTrigger)
}

/** 先向内侧滑（内滑起手）的组合路径。 */
private fun inwardCompoundPaths(): List<CompoundSlotPath> = listOf(
    CompoundSlotPath(GestureTriggerType.SHORT_SWIPE_IN_UP, GestureTriggerType.LONG_SWIPE_IN_UP),
    CompoundSlotPath(GestureTriggerType.SHORT_SWIPE_IN_DOWN, GestureTriggerType.LONG_SWIPE_IN_DOWN),
    CompoundSlotPath(GestureTriggerType.SHORT_SWIPE_IN_AND_BACK),
)

/** 先沿边滑（上/下起手，顶底边为左/右）的组合路径。 */
private fun alongCompoundPaths(): List<CompoundSlotPath> = listOf(
    CompoundSlotPath(GestureTriggerType.SHORT_SWIPE_UP_IN, GestureTriggerType.LONG_SWIPE_UP_IN),
    CompoundSlotPath(GestureTriggerType.SHORT_SWIPE_DOWN_IN, GestureTriggerType.LONG_SWIPE_DOWN_IN),
    CompoundSlotPath(GestureTriggerType.SHORT_SWIPE_UP_AND_BACK),
    CompoundSlotPath(GestureTriggerType.SHORT_SWIPE_DOWN_AND_BACK),
)

private enum class SideGestureSlotTitleStyle {
    SlotLabel,
    TriggerLabel,
}

/** 组合页一行 = 一条路径，副标题同时给出短滑/长滑两档的动作摘要。 */
private fun sideGestureCompoundPathItems(
    settings: AppSettings,
    slotSide: PanelSide,
    handleId: String,
    side: PanelSide,
    paths: List<CompoundSlotPath>,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
): List<CardItem> = buildList {
    paths.forEach { path ->
        add(
            settingsCardScopeItem("compound-path-${path.shortTrigger.name}") {
                val actions = path.tiers.associateWith { settings.slotAction(slotSide, it, handleId) }
                // 默认打开已配置的那一档；两档都空则打开短滑档。
                val target = path.tiers.firstOrNull { actions[it] !is GestureAction.None }
                    ?: path.shortTrigger
                val summaries = path.tiers.map { tier ->
                    val prefix = if (path.tiers.size > 1) {
                        stringResource(
                            if (tier.isLongDistance) {
                                R.string.side_gestures_direction_long_slot
                            } else {
                                R.string.side_gestures_direction_short_slot
                            },
                        ) + "："
                    } else {
                        ""
                    }
                    prefix + compoundTierSummary(settings, slotSide, tier, handleId)
                }
                val subtitle = summaries.joinToString(" ｜ ")
                GestureSlotRow(
                    side = side,
                    trigger = target,
                    label = triggerLabel(side, path.shortTrigger),
                    action = actions[target] ?: GestureAction.None,
                    modeLabel = null,
                    subtitleOverride = subtitle,
                    onClick = { onOpenSlotConfig(target) },
                    insideMargin = MiuixInsetCardComponentMargin,
                )
            },
        )
    }
}

@Composable
private fun compoundTierSummary(
    settings: AppSettings,
    slotSide: PanelSide,
    trigger: GestureTriggerType,
    handleId: String,
): String {
    val actionText = gestureActionSettingSubtitle(settings.slotAction(slotSide, trigger, handleId))
    val mode = settings.slotTriggerMode(slotSide, trigger, handleId)
    return if (mode == com.slideindex.app.gesture.GestureTriggerMode.DEFAULT) {
        actionText
    } else {
        "$actionText · ${triggerModeLabel(mode, includeDefault = false)}"
    }
}

private fun sideGestureSlotCardItems(
    settings: AppSettings,
    slotSide: PanelSide,
    handleId: String,
    side: PanelSide,
    triggers: List<GestureTriggerType>,
    titleStyle: SideGestureSlotTitleStyle,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
    rowInsideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
): List<CardItem> = buildList {
    triggers.forEach { trigger ->
        add(
            settingsCardScopeItem("slot-${trigger.name}") {
                val label = when (titleStyle) {
                    SideGestureSlotTitleStyle.SlotLabel -> triggerSlotLabel(side, trigger)
                    SideGestureSlotTitleStyle.TriggerLabel -> triggerLabel(side, trigger)
                }
                GestureSlotRow(
                    side = side,
                    trigger = trigger,
                    label = label,
                    action = settings.slotAction(slotSide, trigger, handleId),
                    // 单击/双击固定松手触发、不可选，行里就不再挂触发模式标签，避免显示"默认"误导。
                    modeLabel = if (trigger.isTapFixedOnRelease) {
                        null
                    } else {
                        triggerModeLabel(settings.slotTriggerMode(slotSide, trigger, handleId))
                    },
                    onClick = { onOpenSlotConfig(trigger) },
                    insideMargin = rowInsideMargin,
                )
            },
        )
    }
}

@Composable
private fun ColumnScope.RenderSideGestureSlotItems(items: List<CardItem>) {
    items.forEach { cardItem ->
        key(cardItem.key) {
            cardItem.content(this)
        }
    }
}

@Composable
private fun AlignOppositeGesturesMirrorDialog(
    currentSide: PanelSide,
    onDismiss: () -> Unit,
    onCopyCurrentToOpposite: () -> Unit,
    onCopyOppositeToCurrent: () -> Unit,
) {
    val currentSideLabel = horizontalGestureSideLabel(currentSide)
    val oppositeSideLabel = horizontalGestureSideLabel(currentSide.opposite())

    top.yukonga.miuix.kmp.window.WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.align_opposite_gestures_mirror_title),
        summary = stringResource(R.string.align_opposite_gestures_mirror_message),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(R.string.align_opposite_gestures_use_this_side, currentSideLabel),
                onClick = onCopyCurrentToOpposite,
                modifier = Modifier.fillMaxWidth(),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
            )
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(R.string.align_opposite_gestures_use_opposite_side, oppositeSideLabel),
                onClick = onCopyOppositeToCurrent,
                modifier = Modifier.fillMaxWidth(),
            )
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun horizontalGestureSideLabel(side: PanelSide): String = when (side) {
    PanelSide.LEFT -> stringResource(R.string.trigger_side_left_item)
    PanelSide.RIGHT -> stringResource(R.string.trigger_side_right_item)
    else -> side.name
}

@Composable
private fun SettingsCardScope.GestureSlotRow(
    side: PanelSide,
    trigger: GestureTriggerType,
    label: String,
    action: GestureAction,
    modeLabel: String?,
    onClick: () -> Unit,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    subtitleOverride: String? = null,
) {
    val subtitle = subtitleOverride ?: if (modeLabel == null) {
        gestureActionSettingSubtitle(action)
    } else {
        listOf(gestureActionSettingSubtitle(action), modeLabel).joinToString(" · ")
    }
    SettingNavigationRow(
        icon = { contentDescription ->
            GestureTriggerIcon(
                side = side,
                trigger = trigger,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        },
        title = label,
        subtitle = subtitle,
        onClick = onClick,
        insideMargin = insideMargin,
    )
}

@Composable
fun SettingsCardScope.SideGesturesEntryCard(onOpenLeft: () -> Unit, onOpenRight: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("ArrowForward", contentDescription = label, mirrored = true) },
        title = stringResource(R.string.side_gestures_entry_left),
        subtitle = stringResource(R.string.side_gestures_entry_desc),
        onClick = onOpenLeft,
    )
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("ArrowForward", contentDescription = label, mirrored = true) },
        title = stringResource(R.string.side_gestures_entry_right),
        subtitle = stringResource(R.string.side_gestures_entry_desc),
        onClick = onOpenRight,
    )
}
