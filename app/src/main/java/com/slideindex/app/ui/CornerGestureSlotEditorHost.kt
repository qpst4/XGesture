package com.slideindex.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.corner.resolveHostPackageName
import com.slideindex.app.service.CornerGestureSlotPickTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import com.slideindex.app.settings.CornerSlotSubMenuConfig
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.util.AppShortcutLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface CornerSlotEditorPage {
    data object SlotSettings : CornerSlotEditorPage
    data object ActionPick : CornerSlotEditorPage
    data object ActionPickMyShortcuts : CornerSlotEditorPage
    data object ActionPickPresetShortcuts : CornerSlotEditorPage
    data object ActionPickPickApp : CornerSlotEditorPage
    data class ActionPickPickActivity(val packageName: String) : CornerSlotEditorPage
    /** 反馈 #385：从二级菜单里挑一个任意动作（应用 / 内置动作）。 */
    data object SubMenuActionPick : CornerSlotEditorPage
    data object SubMenuMyShortcuts : CornerSlotEditorPage
    data object SubMenuPresetShortcuts : CornerSlotEditorPage
    data object SubMenuPickApp : CornerSlotEditorPage
    data class SubMenuPickActivity(val packageName: String) : CornerSlotEditorPage
    data class ShellCommand(val initialCommand: String) : CornerSlotEditorPage
}

internal fun CornerSlotEditorPage.navDepth(): Int = when (this) {
    CornerSlotEditorPage.SlotSettings -> 0
    CornerSlotEditorPage.ActionPick,
    CornerSlotEditorPage.SubMenuActionPick -> 1
    CornerSlotEditorPage.ActionPickMyShortcuts,
    CornerSlotEditorPage.ActionPickPresetShortcuts,
    is CornerSlotEditorPage.ShellCommand,
    CornerSlotEditorPage.SubMenuMyShortcuts,
    CornerSlotEditorPage.SubMenuPresetShortcuts,
    -> 2
    CornerSlotEditorPage.ActionPickPickApp, CornerSlotEditorPage.SubMenuPickApp -> 3
    is CornerSlotEditorPage.ActionPickPickActivity, is CornerSlotEditorPage.SubMenuPickActivity -> 4
}

private fun CornerSlotEditorPage.contentKey(): Any = when (this) {
    CornerSlotEditorPage.SlotSettings -> "slotSettings"
    CornerSlotEditorPage.ActionPick -> "actionPick"
    CornerSlotEditorPage.ActionPickMyShortcuts -> "actionPickMyShortcuts"
    CornerSlotEditorPage.ActionPickPresetShortcuts -> "actionPickPresetShortcuts"
    CornerSlotEditorPage.ActionPickPickApp -> "actionPickPickApp"
    is CornerSlotEditorPage.ActionPickPickActivity -> "actionPickPickActivity:$packageName"
    CornerSlotEditorPage.SubMenuActionPick -> "subMenuActionPick"
    CornerSlotEditorPage.SubMenuMyShortcuts -> "subMenuMyShortcuts"
    CornerSlotEditorPage.SubMenuPresetShortcuts -> "subMenuPresetShortcuts"
    CornerSlotEditorPage.SubMenuPickApp -> "subMenuPickApp"
    is CornerSlotEditorPage.SubMenuPickActivity -> "subMenuPickActivity:$packageName"
    is CornerSlotEditorPage.ShellCommand -> "shellCommand"
}

@Composable
fun CornerGestureSlotEditorHost(
    corner: String,
    slotIndex: Int,
    cornerTitle: String,
    onExit: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf(AppSettings()) }
    var draftAction by remember { mutableStateOf<GestureAction>(GestureAction.None) }
    var draftSubMenu by remember { mutableStateOf(CornerSlotSubMenuConfig()) }
    var page by remember { mutableStateOf<CornerSlotEditorPage>(CornerSlotEditorPage.SlotSettings) }

    LaunchedEffect(corner, slotIndex) {
        val overlay = settingsRepository.overlaySettings.first()
        val cornerSettings = overlay.cornerGestureSettings
        draftAction = loadCornerSlotAction(corner, slotIndex, cornerSettings)
        draftSubMenu = loadCornerSlotSubMenu(corner, slotIndex, cornerSettings)
        appSettings = settingsRepository.settings.first()
    }

    val updateDraftAction: (GestureAction) -> Unit = { action ->
        draftAction = action
        page = CornerSlotEditorPage.SlotSettings
    }

    val updateDraftSubMenu: (CornerSlotSubMenuConfig) -> Unit = { config ->
        draftSubMenu = config
    }

    val commitDraftAndExit: () -> Unit = {
        scope.launch {
            persistCornerSlotDraft(
                settingsRepository = settingsRepository,
                corner = corner,
                slotIndex = slotIndex,
                action = draftAction,
                subMenuConfig = draftSubMenu,
            )
            onExit()
        }
    }

    // 反馈 #385：二级菜单现在能放任意动作（应用 / 内置动作 / 快捷方式），统一按动作载荷去重。
    val appendSubMenuItem: (GestureAction) -> Unit = { action ->
        val payload = QuickLauncherItemCodec.encodeActionPayload(action)
        val existing = draftSubMenu.items.map { QuickLauncherItemCodec.encodeActionPayload(it) }
        if (payload !in existing) {
            updateDraftSubMenu(
                draftSubMenu.copy(
                    enabled = true,
                    items = draftSubMenu.items + action,
                ),
            )
        }
    }

    /**
     * 与相邻槽位交换动作（反馈 #385 ①）。
     *
     * 槽位是按固定几何位置编号的，所以"排序"就是交换两个位置上的动作；
     * 先把当前草稿落盘，再交换，避免用户刚改的动作被覆盖。
     */
    val swapWithNeighbor: (Int) -> Unit = { delta ->
        scope.launch {
            persistCornerSlotDraft(
                settingsRepository = settingsRepository,
                corner = corner,
                slotIndex = slotIndex,
                action = draftAction,
                subMenuConfig = draftSubMenu,
            )
            val overlay = settingsRepository.overlaySettings.first()
            val cornerSettings = overlay.cornerGestureSettings
            val unified = cornerSettings.unifiedSlots
            val isLeft = unified || corner != "right"
            val slots = if (isLeft) cornerSettings.leftSlots else cornerSettings.rightSlots
            val neighborIndex = slotIndex + delta
            if (neighborIndex in slots.indices) {
                val mine = slots.getOrElse(slotIndex) { GestureAction.None }
                val theirs = slots[neighborIndex]
                if (isLeft) {
                    settingsRepository.setCornerGestureLeftSlotAction(slotIndex, theirs)
                    settingsRepository.setCornerGestureLeftSlotAction(neighborIndex, mine)
                } else {
                    settingsRepository.setCornerGestureRightSlotAction(slotIndex, theirs)
                    settingsRepository.setCornerGestureRightSlotAction(neighborIndex, mine)
                }
            }
            onExit()
        }
    }

    val hostPackage = draftAction.resolveHostPackageName()

    val existingPayloadKeys = remember(draftSubMenu.items) {
        draftSubMenu.items.filterIsInstance<GestureAction.LaunchShortcut>()
            .map { it.payloadKey }
            .toSet()
    }
    val configuredSubMenuShortcutKeys = remember(draftSubMenu.items) {
        draftSubMenu.items.filterIsInstance<GestureAction.LaunchShortcut>()
            .mapNotNull { GestureShortcutPayload.shortcutToggleKey(it.payloadKey) }
            .toSet()
    }

    BackHandler {
        when (page) {
            CornerSlotEditorPage.SlotSettings -> onExit()
            CornerSlotEditorPage.ActionPick -> page = CornerSlotEditorPage.SlotSettings
            CornerSlotEditorPage.ActionPickMyShortcuts -> page = CornerSlotEditorPage.ActionPick
            CornerSlotEditorPage.ActionPickPresetShortcuts -> page = CornerSlotEditorPage.ActionPick
            CornerSlotEditorPage.ActionPickPickApp -> page = CornerSlotEditorPage.ActionPick
            is CornerSlotEditorPage.ActionPickPickActivity -> page = CornerSlotEditorPage.ActionPickPickApp
            CornerSlotEditorPage.SubMenuActionPick -> page = CornerSlotEditorPage.SlotSettings
            CornerSlotEditorPage.SubMenuMyShortcuts -> page = CornerSlotEditorPage.SubMenuActionPick
            CornerSlotEditorPage.SubMenuPresetShortcuts -> page = CornerSlotEditorPage.SubMenuActionPick
            CornerSlotEditorPage.SubMenuPickApp -> page = CornerSlotEditorPage.SubMenuActionPick
            is CornerSlotEditorPage.SubMenuPickActivity -> page = CornerSlotEditorPage.SubMenuPickApp
            is CornerSlotEditorPage.ShellCommand -> page = CornerSlotEditorPage.ActionPick
        }
    }

    AnimatedContent(
        targetState = page,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(CornerSlotEditorPage::navDepth) },
        contentKey = { it.contentKey() },
        label = "cornerSlotEditorNav",
    ) { screen ->
    when (screen) {
        CornerSlotEditorPage.SlotSettings -> {
            CornerGestureSlotSettingsScreen(
                slotIndex = slotIndex,
                cornerTitle = cornerTitle,
                currentAction = draftAction,
                subMenuConfig = draftSubMenu,
                appSettings = appSettings,
                onBack = onExit,
                onSave = commitDraftAndExit,
                onPickMainAction = { page = CornerSlotEditorPage.ActionPick },
                onSubMenuEnabledChange = { enabled ->
                    updateDraftSubMenu(draftSubMenu.copy(enabled = enabled))
                },
                onRemoveSubMenuItem = { index ->
                    val items = draftSubMenu.items.toMutableList()
                    if (index in items.indices) {
                        items.removeAt(index)
                        updateDraftSubMenu(draftSubMenu.copy(items = items))
                    }
                },
                onAddSubMenuAction = { page = CornerSlotEditorPage.SubMenuActionPick },
                onSwapWithPrevious = { swapWithNeighbor(-1) },
                onSwapWithNext = { swapWithNeighbor(1) },
                canSwapWithPrevious = slotIndex > 0,
                canSwapWithNext = slotIndex < CornerRadialMenuCodec.SLOT_COUNT - 1,
                onImportFromApp = {
                    scope.launch {
                        val pkg = draftAction.resolveHostPackageName() ?: return@launch
                        val loaded = withContext(Dispatchers.IO) {
                            AppShortcutLoader.loadFastShortcuts(context, pkg)
                        }
                        val existing = draftSubMenu.items
                            .filterIsInstance<GestureAction.LaunchShortcut>()
                            .map { it.payloadKey }
                        val shortcuts = loaded.map { item ->
                            taskSwitcherItemToLaunchShortcut(item, pkg)
                        }.filter { it.payloadKey !in existing }
                        updateDraftSubMenu(
                            draftSubMenu.copy(
                                enabled = true,
                                items = draftSubMenu.items + shortcuts,
                            ),
                        )
                    }
                },
                canImportFromHost = hostPackage != null,
            )
        }

        CornerSlotEditorPage.ActionPick -> {
            GestureActionPickerScreen(
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                current = draftAction,
                catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.CornerWheel),
                onDismiss = { page = CornerSlotEditorPage.SlotSettings },
                onSelect = { action ->
                    updateDraftAction(action)
                },
                onOpenMyShortcuts = { page = CornerSlotEditorPage.ActionPickMyShortcuts },
                onOpenPresetShortcuts = { page = CornerSlotEditorPage.ActionPickPresetShortcuts },
                onOpenPickApp = { page = CornerSlotEditorPage.ActionPickPickApp },
                onOpenExecuteShellCommand = { command ->
                    page = CornerSlotEditorPage.ShellCommand(command)
                },
            )
        }

        CornerSlotEditorPage.ActionPickMyShortcuts -> {
            MyShortcutsFolderScreen(
                activityShortcuts = appSettings.activityShortcuts,
                onBack = { page = CornerSlotEditorPage.ActionPick },
                onBrowseNewShortcut = { page = CornerSlotEditorPage.ActionPickPickApp },
                currentAction = draftAction,
                onSelectRadio = updateDraftAction,
                overlayMode = true,
            )
        }

        CornerSlotEditorPage.ActionPickPresetShortcuts -> {
            PresetShortcutsFolderScreen(
                onBack = { page = CornerSlotEditorPage.ActionPick },
                currentAction = draftAction,
                onSelectRadio = { action ->
                    if (action is GestureAction.LaunchShortcut) {
                        updateDraftAction(action)
                    }
                },
                overlayMode = true,
            )
        }

        CornerSlotEditorPage.ActionPickPickApp -> {
            ActivityShortcutPickAppScreen(
                onBack = { page = CornerSlotEditorPage.ActionPick },
                onSelectApp = { app ->
                    page = CornerSlotEditorPage.ActionPickPickActivity(app.packageName)
                },
            )
        }

        is CornerSlotEditorPage.ActionPickPickActivity -> {
            ActivityShortcutPickActivityScreen(
                packageName = screen.packageName,
                onBack = { page = CornerSlotEditorPage.ActionPickPickApp },
                onSelectActivity = { activity ->
                    val component = "${activity.packageName}/${activity.className}"
                    updateDraftAction(
                        GestureAction.LaunchShortcut.component(component, activity.label),
                    )
                },
            )
        }


        CornerSlotEditorPage.SubMenuActionPick -> {
            // 反馈 #385：二级菜单也能放应用/内置动作（任意 GestureAction）。
            GestureActionPickerScreen(
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                current = GestureAction.None,
                catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.CornerWheel),
                onDismiss = { page = CornerSlotEditorPage.SlotSettings },
                onSelect = { action ->
                    appendSubMenuItem(action)
                    page = CornerSlotEditorPage.SlotSettings
                },
                onOpenMyShortcuts = { page = CornerSlotEditorPage.SubMenuMyShortcuts },
                onOpenPresetShortcuts = { page = CornerSlotEditorPage.SubMenuPresetShortcuts },
                onOpenPickApp = { page = CornerSlotEditorPage.SubMenuPickApp },
                onOpenExecuteShellCommand = { command ->
                    appendSubMenuItem(GestureAction.ExecuteShellCommand(command))
                    page = CornerSlotEditorPage.SlotSettings
                },
            )
        }

        CornerSlotEditorPage.SubMenuMyShortcuts -> {
            MyShortcutsFolderScreen(
                activityShortcuts = appSettings.activityShortcuts,
                onBack = { page = CornerSlotEditorPage.SubMenuActionPick },
                onBrowseNewShortcut = { page = CornerSlotEditorPage.SubMenuPickApp },
                configuredShortcutKeys = configuredSubMenuShortcutKeys,
                onToggle = { item, added ->
                    if (!added && item.type == QuickLauncherItemType.SHORTCUT) {
                        val action = activityShortcutFromQuickLauncherItem(item)?.toLaunchShortcut()
                        if (action != null) {
                            appendSubMenuItem(action)
                        }
                    }
                },
                overlayMode = true,
            )
        }

        CornerSlotEditorPage.SubMenuPresetShortcuts -> {
            PresetShortcutsFolderScreen(
                onBack = { page = CornerSlotEditorPage.SubMenuActionPick },
                configuredShortcutKeys = configuredSubMenuShortcutKeys,
                onToggle = { item, added ->
                    if (!added && item.type == QuickLauncherItemType.SHORTCUT) {
                        val action = activityShortcutFromQuickLauncherItem(item)?.toLaunchShortcut()
                            ?: run {
                                val uri = QuickLauncherItemCodec.parseIntentPayload(item.payload)
                                val host = QuickLauncherItemCodec.resolveHostPackageName(item.payload)
                                if (uri != null) {
                                    GestureAction.LaunchShortcut.intent(uri, item.label, host)
                                } else {
                                    null
                                }
                            }
                        if (action != null && action.payloadKey !in existingPayloadKeys) {
                            appendSubMenuItem(action)
                        }
                    }
                },
                overlayMode = true,
            )
        }

        CornerSlotEditorPage.SubMenuPickApp -> {
            ActivityShortcutPickAppScreen(
                onBack = { page = CornerSlotEditorPage.SubMenuActionPick },
                onSelectApp = { app ->
                    page = CornerSlotEditorPage.SubMenuPickActivity(app.packageName)
                },
            )
        }

        is CornerSlotEditorPage.SubMenuPickActivity -> {
            ActivityShortcutPickActivityScreen(
                packageName = screen.packageName,
                onBack = { page = CornerSlotEditorPage.SubMenuPickApp },
                onSelectActivity = { activity ->
                    val component = "${activity.packageName}/${activity.className}"
                    appendSubMenuItem(
                        GestureAction.LaunchShortcut.component(component, activity.label),
                    )
                },
            )
        }

        is CornerSlotEditorPage.ShellCommand -> {
            GestureExecuteShellCommandScreen(
                initialCommand = screen.initialCommand,
                shellCommands = appSettings.shellCommands,
                overlayMode = true,
                onBack = { page = CornerSlotEditorPage.ActionPick },
                onConfirm = { command ->
                    updateDraftAction(GestureAction.ExecuteShellCommand(command))
                },
            )
        }
    }
    }
}

private fun loadCornerSlotAction(
    corner: String,
    slotIndex: Int,
    cornerSettings: CornerGestureSettings,
): GestureAction = when (corner) {
    CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT -> {
        if (cornerSettings.unifiedSlots) {
            cornerSettings.leftSlots
        } else {
            cornerSettings.rightSlots
        }.getOrElse(slotIndex) { GestureAction.None }
    }
    else -> cornerSettings.leftSlots.getOrElse(slotIndex) { GestureAction.None }
}

private fun loadCornerSlotSubMenu(
    corner: String,
    slotIndex: Int,
    cornerSettings: CornerGestureSettings,
): CornerSlotSubMenuConfig = when {
    corner == CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT && !cornerSettings.unifiedSlots ->
        cornerSettings.rightSlotSubMenus.getOrElse(slotIndex) { CornerSlotSubMenuConfig() }
    else ->
        cornerSettings.leftSlotSubMenus.getOrElse(slotIndex) { CornerSlotSubMenuConfig() }
}

private suspend fun persistCornerSlotDraft(
    settingsRepository: SettingsRepository,
    corner: String,
    slotIndex: Int,
    action: GestureAction,
    subMenuConfig: CornerSlotSubMenuConfig,
) {
    val overlay = settingsRepository.overlaySettings.first()
    val unified = overlay.cornerGestureSettings.unifiedSlots
    when {
        unified -> settingsRepository.setCornerGestureLeftSlotAction(slotIndex, action)
        corner == CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT ->
            settingsRepository.setCornerGestureRightSlotAction(slotIndex, action)
        else -> settingsRepository.setCornerGestureLeftSlotAction(slotIndex, action)
    }
    val isLeft = unified || corner != CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT
    settingsRepository.setCornerSlotSubMenu(isLeft, slotIndex, subMenuConfig)
}
