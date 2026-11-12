package com.slideindex.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.overlay.quickwheel.QuickWheelIconResolver
import com.slideindex.app.overlay.quickwheel.QuickWheelSlotIcon
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.settings.QuickWheelIconSource
import com.slideindex.app.settings.QuickWheelLaunchMode
import com.slideindex.app.settings.QuickWheelLongPressTrigger
import com.slideindex.app.settings.QuickWheelSlot
import com.slideindex.app.settings.QuickWheelTapTrigger
import com.slideindex.app.settings.slotAt
import com.slideindex.app.settings.opensSomething
import com.slideindex.app.settings.toLaunchWindowMode
import com.slideindex.app.settings.toQuickWheelLaunchMode
import com.slideindex.app.settings.withoutLaunchWindowMode
import com.slideindex.app.shell.ShellCommandLabelResolver
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.gesturepicker.launchShortcutDisplayLabel
import com.slideindex.app.ui.miuix.MiuixSettingsScreenScaffold
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 容器编辑页里正在配置的动作槽位。 */
enum class QuickWheelSlotActionTarget { TAP, LONG }

/** 容器编辑页的页内子页（动作选择复用现成页面，结果写进本地草稿）。 */
internal sealed interface QuickWheelSlotEditorPage {
    data object SlotSettings : QuickWheelSlotEditorPage
    data object ActionPick : QuickWheelSlotEditorPage
    data object ActionPickMyShortcuts : QuickWheelSlotEditorPage
    data object ActionPickPresetShortcuts : QuickWheelSlotEditorPage
    data object ActionPickPickApp : QuickWheelSlotEditorPage
    /** 选应用图标：复用软件现成的应用列表页。 */
    data object IconPickApp : QuickWheelSlotEditorPage
    data class ActionPickPickActivity(val packageName: String) : QuickWheelSlotEditorPage
    data class ShellCommand(val initialCommand: String) : QuickWheelSlotEditorPage
    data class SimulateKeyEvent(
        val keyCode: Int,
        val keyName: String,
        val isLongPress: Boolean,
    ) : QuickWheelSlotEditorPage
}

internal fun QuickWheelSlotEditorPage.navDepth(): Int = when (this) {
    QuickWheelSlotEditorPage.SlotSettings -> 0
    QuickWheelSlotEditorPage.ActionPick,
    QuickWheelSlotEditorPage.IconPickApp,
    -> 1
    QuickWheelSlotEditorPage.ActionPickMyShortcuts,
    QuickWheelSlotEditorPage.ActionPickPresetShortcuts,
    is QuickWheelSlotEditorPage.ShellCommand,
    -> 2

    QuickWheelSlotEditorPage.ActionPickPickApp,
    is QuickWheelSlotEditorPage.SimulateKeyEvent,
    -> 3

    is QuickWheelSlotEditorPage.ActionPickPickActivity -> 4
}

private fun QuickWheelSlotEditorPage.contentKey(): Any = when (this) {
    QuickWheelSlotEditorPage.SlotSettings -> "slotSettings"
    QuickWheelSlotEditorPage.ActionPick -> "actionPick"
    QuickWheelSlotEditorPage.ActionPickMyShortcuts -> "actionPickMyShortcuts"
    QuickWheelSlotEditorPage.ActionPickPresetShortcuts -> "actionPickPresetShortcuts"
    QuickWheelSlotEditorPage.ActionPickPickApp -> "actionPickPickApp"
    QuickWheelSlotEditorPage.IconPickApp -> "iconPickApp"
    is QuickWheelSlotEditorPage.ActionPickPickActivity -> "actionPickPickActivity:$packageName"
    is QuickWheelSlotEditorPage.ShellCommand -> "shellCommand"
    is QuickWheelSlotEditorPage.SimulateKeyEvent -> "simulateKeyEvent:$keyCode:$isLongPress"
}

/**
 * 页面 4：编辑轮盘容器。
 *
 * **草稿 + 底部「取消 / 保存」**（与 [CornerGestureSlotEditorHost] 同一范式）：
 * 所有改动（触发方式 / 图标 / 名称 / 动作）先写本地 [draft]，点「保存」才交回上层落盘，
 * 因此「选择动作」可以复用现成的 [GestureActionPickerScreen]，且选择结果同样只进草稿。
 *
 * 图标与名称属于容器本身（轮盘上一个容器只显示一个图标），因此只在「单击动作」区展示一次。
 */
@Composable
fun QuickWheelSlotEditorScreen(
    wheel: QuickWheel,
    path: String,
    appSettings: AppSettings,
    onExit: () -> Unit,
    onSave: (QuickWheelSlot) -> Unit,
) {
    val context = LocalContext.current
    // ⚠️ 资源文案必须走 stringResource：`LocalContext.current.getString(...)` 不是配置感知的，
    // 会被 Compose lint 判成 **Error**（LocalContextGetResourceValueCall）并让 lintDebug 直接失败。
    val launchShortcutFallbackLabel = stringResource(R.string.gesture_action_launch_shortcut)
    // Shell 命令优先用命令面板里的自定义名；其余动作仍走通用文案。
    fun labelFor(action: GestureAction): String =
        (action as? GestureAction.ExecuteShellCommand)
            ?.let { ShellCommandLabelResolver.customLabelFor(it.command, appSettings.shellCommands) }
            ?: gestureActionLabelText(context, action)
    val scope = rememberCoroutineScope()
    val original = remember(wheel, path) { wheel.slotAt(path) ?: QuickWheelSlot() }
    val isExisting = original.isConfigured

    var draft by remember(wheel.id, path) { mutableStateOf(original) }
    var page by remember { mutableStateOf<QuickWheelSlotEditorPage>(QuickWheelSlotEditorPage.SlotSettings) }
    var pickTarget by remember { mutableStateOf(QuickWheelSlotActionTarget.TAP) }
    var iconMenuExpanded by remember { mutableStateOf(false) }
    var showIconLibrary by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pendingGalleryPick by remember { mutableStateOf(false) }

    fun patch(transform: (QuickWheelSlot) -> QuickWheelSlot) {
        draft = transform(draft)
    }

    /** 动作选择结果只写入草稿，并回到主设置页。 */
    fun applyPickedAction(picked: GestureAction) {
        // 上游那个「启动方式」弹窗的结果 = **本容器的打开方式**（它就是容器的首次设置入口）：
        // 收编进容器字段，并把动作那一段清成"跟随" —— 容器是唯一来源，动作不再自带形态。
        val pickedMode = (picked as? GestureAction.LaunchApp)?.windowMode?.toQuickWheelLaunchMode()
        val action = picked.withoutLaunchWindowMode()
        val previousAction = if (pickTarget == QuickWheelSlotActionTarget.LONG) {
            draft.longPressAction
        } else {
            draft.tapAction
        }
        val previousLabel = labelFor(previousAction)
        // 「打开应用」类动作：名称用应用名（不带"启动应用："前缀），图标直接用应用图标。
        val appPackage = (action as? GestureAction.LaunchApp)?.payload
        val appLabel = appPackage?.let { pkg ->
            OverlayDependencyAccess.overlayDependencies(context)
                ?.appRepository
                ?.getCachedApps()
                ?.firstOrNull { it.packageName == pkg }
                ?.label
        }
        // 「打开应用」→ 应用名；「快捷方式」→ 快捷方式自身名称（**都不带"启动X："前缀**，
        // 与动作选择器列表、HUD 文案保持一致）；其它动作沿用动作类型文案。
        val newLabel = when (action) {
            is GestureAction.LaunchApp -> appLabel ?: labelFor(action)

            is GestureAction.LaunchShortcut ->
                launchShortcutDisplayLabel(action).ifBlank { launchShortcutFallbackLabel }

            else -> labelFor(action)
        }
        val newIconSource = if (appPackage != null) {
            QuickWheelIconSource.APP_ICON
        } else {
            QuickWheelIconSource.NONE
        }
        val fillsLongPress = draft.longPressAction == GestureAction.None
        draft = if (pickTarget == QuickWheelSlotActionTarget.LONG) {
            // ⚠️ 长按动作**只**改长按：容器图标与名称一律不动。
            // 图标只跟「单击动作」或用户手动配置的图标走，改长按不应产生任何视觉变化。
            draft.copy(
                longPressAction = action,
                longPressLaunchMode = pickedMode ?: draft.longPressLaunchMode,
            )
        } else {
            draft.copy(
                tapAction = action,
                tapLaunchMode = pickedMode ?: draft.tapLaunchMode,
                // 单击动作同时作为长按的默认动作：未单独配置长按时，该容器单击与长按都能用
                //（打开方式一并带过去，避免同一个应用的两行显示不一致）。
                longPressAction = if (fillsLongPress) action else draft.longPressAction,
                longPressLaunchMode = if (fillsLongPress && pickedMode != null) {
                    pickedMode
                } else {
                    draft.longPressLaunchMode
                },
                iconSource = newIconSource,
                iconValue = appPackage.orEmpty(),
                name = if (draft.name.isBlank() || draft.name == previousLabel) newLabel else draft.name,
            )
        }
        page = QuickWheelSlotEditorPage.SlotSettings
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val shouldApply = pendingGalleryPick
        pendingGalleryPick = false
        if (uri == null || !shouldApply) return@rememberLauncherForActivityResult
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) { copyIntoGalleryDir(context, uri) }
            if (savedPath != null) {
                QuickWheelIconResolver.clearBitmapCache()
                patch {
                    it.copy(
                        iconSource = QuickWheelIconSource.GALLERY,
                        iconValue = savedPath,
                    )
                }
            }
        }
    }

    BackHandler {
        when (page) {
            QuickWheelSlotEditorPage.SlotSettings -> onExit()
            QuickWheelSlotEditorPage.ActionPick -> page = QuickWheelSlotEditorPage.SlotSettings
            QuickWheelSlotEditorPage.ActionPickMyShortcuts ->
                page = QuickWheelSlotEditorPage.ActionPick

            QuickWheelSlotEditorPage.ActionPickPresetShortcuts ->
                page = QuickWheelSlotEditorPage.ActionPick

            QuickWheelSlotEditorPage.ActionPickPickApp ->
                page = QuickWheelSlotEditorPage.ActionPick

            is QuickWheelSlotEditorPage.ActionPickPickActivity ->
                page = QuickWheelSlotEditorPage.ActionPickPickApp

            is QuickWheelSlotEditorPage.ShellCommand -> page = QuickWheelSlotEditorPage.ActionPick
            is QuickWheelSlotEditorPage.SimulateKeyEvent -> page = QuickWheelSlotEditorPage.ActionPick
            QuickWheelSlotEditorPage.IconPickApp -> page = QuickWheelSlotEditorPage.SlotSettings
        }
    }

    AnimatedContent(
        targetState = page,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(QuickWheelSlotEditorPage::navDepth) },
        contentKey = { it.contentKey() },
        label = "quickWheelSlotEditorNav",
    ) { screen ->
        when (screen) {
            QuickWheelSlotEditorPage.SlotSettings -> {
                QuickWheelSlotSettingsPage(
                    draft = draft,
                    appSettings = appSettings,
                    isExisting = isExisting,
                    onExit = onExit,
                    onSave = { onSave(draft) },
                    onPatch = ::patch,
                    onPickAction = { target ->
                        pickTarget = target
                        page = QuickWheelSlotEditorPage.ActionPick
                    },
                    iconMenuExpanded = iconMenuExpanded,
                    onIconMenuExpandedChange = { iconMenuExpanded = it },
                    onOpenIconLibrary = { showIconLibrary = true },
                    // 选应用图标：直接用软件现成的应用列表（页内子页，带搜索）。
                    onOpenAppPicker = { page = QuickWheelSlotEditorPage.IconPickApp },
                    onOpenGallery = {
                        pendingGalleryPick = true
                        galleryLauncher.launch("image/*")
                    },
                )
            }

            QuickWheelSlotEditorPage.IconPickApp -> {
                ActivityShortcutPickAppScreen(
                    onBack = { page = QuickWheelSlotEditorPage.SlotSettings },
                    onSelectApp = { app ->
                        patch {
                            it.copy(
                                iconSource = QuickWheelIconSource.APP_ICON,
                                iconValue = app.packageName,
                            )
                        }
                        page = QuickWheelSlotEditorPage.SlotSettings
                    },
                )
            }

            QuickWheelSlotEditorPage.ActionPick -> {
                GestureActionPickerScreen(
                    trigger = GestureTriggerType.SHORT_SWIPE_IN,
                    // 把容器当前的打开方式**投影**给动作选择器：这样上游那个启动方式弹窗会预选容器
                    // 当前设置、应用行副标题也显示它（选完再被 applyPickedAction 收编回容器）。
                    current = (
                        if (pickTarget == QuickWheelSlotActionTarget.LONG) {
                            draft.longPressAction
                        } else {
                            draft.tapAction
                        }
                    ).withLaunchMode(
                        if (pickTarget == QuickWheelSlotActionTarget.LONG) {
                            draft.longPressLaunchMode
                        } else {
                            draft.tapLaunchMode
                        },
                    ),
                    catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.CornerWheel),
                    onDismiss = { page = QuickWheelSlotEditorPage.SlotSettings },
                    onSelect = ::applyPickedAction,
                    onOpenMyShortcuts = {
                        page = QuickWheelSlotEditorPage.ActionPickMyShortcuts
                    },
                    onOpenPresetShortcuts = {
                        page = QuickWheelSlotEditorPage.ActionPickPresetShortcuts
                    },
                    onOpenPickApp = { page = QuickWheelSlotEditorPage.ActionPickPickApp },
                    onOpenExecuteShellCommand = { command ->
                        page = QuickWheelSlotEditorPage.ShellCommand(command)
                    },
                    onOpenSimulateKeyEvent = { keyEvent ->
                        page = QuickWheelSlotEditorPage.SimulateKeyEvent(
                            keyCode = keyEvent.keyCode,
                            keyName = keyEvent.keyName,
                            isLongPress = keyEvent.isLongPress,
                        )
                    },
                )
            }

            QuickWheelSlotEditorPage.ActionPickMyShortcuts -> {
                MyShortcutsFolderScreen(
                    activityShortcuts = appSettings.activityShortcuts,
                    onBack = { page = QuickWheelSlotEditorPage.ActionPick },
                    onBrowseNewShortcut = { page = QuickWheelSlotEditorPage.ActionPickPickApp },
                    currentAction = if (pickTarget == QuickWheelSlotActionTarget.LONG) {
                        draft.longPressAction
                    } else {
                        draft.tapAction
                    },
                    onSelectRadio = ::applyPickedAction,
                    overlayMode = true,
                )
            }

            QuickWheelSlotEditorPage.ActionPickPresetShortcuts -> {
                PresetShortcutsFolderScreen(
                    onBack = { page = QuickWheelSlotEditorPage.ActionPick },
                    currentAction = if (pickTarget == QuickWheelSlotActionTarget.LONG) {
                        draft.longPressAction
                    } else {
                        draft.tapAction
                    },
                    onSelectRadio = { action ->
                        if (action is GestureAction.LaunchShortcut) {
                            applyPickedAction(action)
                        }
                    },
                    overlayMode = true,
                )
            }

            QuickWheelSlotEditorPage.ActionPickPickApp -> {
                ActivityShortcutPickAppScreen(
                    onBack = { page = QuickWheelSlotEditorPage.ActionPick },
                    onSelectApp = { app ->
                        page = QuickWheelSlotEditorPage.ActionPickPickActivity(app.packageName)
                    },
                )
            }

            is QuickWheelSlotEditorPage.ActionPickPickActivity -> {
                ActivityShortcutPickActivityScreen(
                    packageName = screen.packageName,
                    onBack = { page = QuickWheelSlotEditorPage.ActionPickPickApp },
                    onSelectActivity = { activity ->
                        val component = "${activity.packageName}/${activity.className}"
                        applyPickedAction(
                            GestureAction.LaunchShortcut.component(component, activity.label),
                        )
                    },
                )
            }

            is QuickWheelSlotEditorPage.ShellCommand -> {
                GestureExecuteShellCommandScreen(
                    initialCommand = screen.initialCommand,
                    shellCommands = appSettings.shellCommands,
                    overlayMode = true,
                    onBack = { page = QuickWheelSlotEditorPage.ActionPick },
                    onConfirm = { command ->
                        applyPickedAction(GestureAction.ExecuteShellCommand(command))
                    },
                )
            }

            is QuickWheelSlotEditorPage.SimulateKeyEvent -> {
                GestureSimulateKeyEventScreen(
                    initialAction = GestureAction.SimulateKeyEvent(
                        keyCode = screen.keyCode,
                        keyName = screen.keyName,
                        isLongPress = screen.isLongPress,
                    ),
                    onBack = { page = QuickWheelSlotEditorPage.ActionPick },
                    onConfirm = { action -> applyPickedAction(action) },
                )
            }
        }
    }

    if (showIconLibrary) {
        // 「文字图标」：用户输入的文字直接作为容器图标（存放在 iconValue）。
        var textInput by remember { mutableStateOf(draft.iconValue) }
        AlertDialog(
            onDismissRequest = { showIconLibrary = false },
            title = { Text(stringResource(R.string.quick_wheel_text_icon_title)) },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.quick_wheel_text_icon_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        patch {
                            it.copy(
                                iconSource = QuickWheelIconSource.TEXT,
                                iconValue = textInput.trim(),
                            )
                        }
                        showIconLibrary = false
                    },
                ) {
                    Text(stringResource(R.string.quick_wheel_slot_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIconLibrary = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAppPicker) {
        QuickWheelAppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPick = { packageName ->
                patch {
                    it.copy(
                        iconSource = QuickWheelIconSource.APP_ICON,
                        iconValue = packageName,
                    )
                }
                showAppPicker = false
            },
        )
    }
}

@Composable
private fun QuickWheelSlotSettingsPage(
    draft: QuickWheelSlot,
    /** 只用于「打开方式」的说明行（读总开关与「应用与启动」的启动方式），不参与保存。 */
    appSettings: AppSettings,
    isExisting: Boolean,
    onExit: () -> Unit,
    onSave: () -> Unit,
    onPatch: ((QuickWheelSlot) -> QuickWheelSlot) -> Unit,
    onPickAction: (QuickWheelSlotActionTarget) -> Unit,
    iconMenuExpanded: Boolean,
    onIconMenuExpandedChange: (Boolean) -> Unit,
    onOpenIconLibrary: () -> Unit,
    onOpenAppPicker: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val sectionTapTitle = stringResource(R.string.quick_wheel_tap_section)
    val sectionSyncTitle = stringResource(R.string.quick_wheel_sync_hint)
    val sectionLongTitle = stringResource(R.string.quick_wheel_long_section)

    MiuixSettingsScreenScaffold(
        title = stringResource(
            if (isExisting) R.string.quick_wheel_edit_container else R.string.quick_wheel_new_container,
        ),
        onBack = onExit,
        // 取消 / 保存固定在页面最底部，不随内容滚动。
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.quick_wheel_slot_save))
                    }
                }
            }
        },
    ) {
        settingsLazySmallTitle(key = "quick_wheel_tap", title = sectionTapTitle)
        groupedCardItems(
            keyPrefix = "quick_wheel_tap",
            items = buildList {
                add(
                    settingsCardScopeItem("tap-trigger") {
                        QuickWheelChipRow(
                            label = stringResource(R.string.quick_wheel_trigger_mode),
                            options = listOf(
                                stringResource(R.string.quick_wheel_tap_trigger_release) to
                                    QuickWheelTapTrigger.ON_RELEASE,
                                stringResource(R.string.quick_wheel_tap_trigger_touch) to
                                    QuickWheelTapTrigger.ON_TOUCH,
                            ),
                            selected = draft.tapTrigger,
                            onSelect = { value -> onPatch { it.copy(tapTrigger = value) } },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("icon-name") {
                        QuickWheelIconNameRow(
                            slot = draft,
                            onNameChange = { value -> onPatch { it.copy(name = value) } },
                            onOpenIconMenu = { onIconMenuExpandedChange(true) },
                            // 菜单与图标框同父：DropdownMenu 只按父节点定位，这样才会贴着图标弹出。
                            menuContent = {
                                DropdownMenu(
                                    expanded = iconMenuExpanded,
                                    onDismissRequest = { onIconMenuExpandedChange(false) },
                                ) {
                                    DropdownMenuItem(
                                        // 「图标库」改为「文字图标」：容器以文字形式展示图标，文字由用户输入。
                                        text = {
                                            Text(stringResource(R.string.quick_wheel_icon_pick_text))
                                        },
                                        onClick = {
                                            onIconMenuExpandedChange(false)
                                            onOpenIconLibrary()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.quick_wheel_icon_pick_app))
                                        },
                                        onClick = {
                                            onIconMenuExpandedChange(false)
                                            onOpenAppPicker()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.quick_wheel_icon_pick_gallery),
                                            )
                                        },
                                        onClick = {
                                            onIconMenuExpandedChange(false)
                                            onOpenGallery()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.quick_wheel_slot_icon_clear),
                                            )
                                        },
                                        onClick = {
                                            onIconMenuExpandedChange(false)
                                            onPatch {
                                                it.copy(
                                                    iconSource = QuickWheelIconSource.NONE,
                                                    iconValue = "",
                                                )
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("tap-action") {
                        QuickWheelActionRow(
                            title = stringResource(R.string.quick_wheel_function_type),
                            action = draft.tapAction,
                            onClick = { onPickAction(QuickWheelSlotActionTarget.TAP) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("tap-launch-mode") {
                        QuickWheelLaunchModeRow(
                            mode = draft.tapLaunchMode,
                            freeWindowEnabled = appSettings.freeWindowEnabled,
                            launchesSomething = draft.tapAction.opensSomething(),
                            onSelect = { value -> onPatch { it.copy(tapLaunchMode = value) } },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "quick_wheel_sync", title = sectionSyncTitle)
        groupedCardItems(
            keyPrefix = "quick_wheel_sync",
            items = buildList {
                add(
                    settingsCardScopeItem("sync-buttons") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    onPatch {
                                        it.copy(
                                            longPressAction = it.tapAction,
                                            longPressLaunchMode = it.tapLaunchMode,
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.quick_wheel_sync_down))
                            }
                            TextButton(
                                onClick = {
                                    onPatch {
                                        it.copy(
                                            tapAction = it.longPressAction,
                                            longPressAction = it.tapAction,
                                            tapLaunchMode = it.longPressLaunchMode,
                                            longPressLaunchMode = it.tapLaunchMode,
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.quick_wheel_sync_swap))
                            }
                            TextButton(
                                onClick = {
                                    onPatch {
                                        it.copy(
                                            tapAction = it.longPressAction,
                                            tapLaunchMode = it.longPressLaunchMode,
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.quick_wheel_sync_up))
                            }
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "quick_wheel_long", title = sectionLongTitle)
        groupedCardItems(
            keyPrefix = "quick_wheel_long",
            items = buildList {
                add(
                    settingsCardScopeItem("long-trigger") {
                        QuickWheelChipRow(
                            label = stringResource(R.string.quick_wheel_trigger_mode),
                            options = listOf(
                                stringResource(R.string.quick_wheel_long_trigger_release) to
                                    QuickWheelLongPressTrigger.ON_RELEASE,
                                stringResource(R.string.quick_wheel_long_trigger_timeout) to
                                    QuickWheelLongPressTrigger.ON_TIMEOUT,
                            ),
                            selected = draft.longPressTrigger,
                            onSelect = { value -> onPatch { it.copy(longPressTrigger = value) } },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("long-action") {
                        QuickWheelActionRow(
                            title = stringResource(R.string.quick_wheel_function_type),
                            action = draft.longPressAction,
                            onClick = { onPickAction(QuickWheelSlotActionTarget.LONG) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("long-launch-mode") {
                        QuickWheelLaunchModeRow(
                            mode = draft.longPressLaunchMode,
                            freeWindowEnabled = appSettings.freeWindowEnabled,
                            launchesSomething = draft.longPressAction.opensSomething(),
                            onSelect = { value -> onPatch { it.copy(longPressLaunchMode = value) } },
                        )
                    },
                )
            },
        )

    }

}

@Composable
private fun <T> QuickWheelChipRow(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    /** 逐项是否可点（默认全可点）：用于"总开关关闭时禁用『全屏 / 小窗』"这类情形。 */
    optionEnabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (text, value) ->
                FilterChip(
                    selected = selected == value,
                    enabled = optionEnabled(value),
                    onClick = { onSelect(value) },
                    label = { Text(text, style = MaterialTheme.typography.bodyMedium) },
                )
            }
        }
    }
}

/**
 * 一个动作的「打开方式」行：三档 Chip +（必要时）一行说明。
 *
 * 单击动作 / 长按动作各有一份（容器里分开设置），互不影响。
 *
 * 置灰的两种情况（**不隐藏整行**：位置稳定、也顺便让用户知道"这个动作没有打开方式"）：
 * - 该动作根本不涉及打开（返回 / 主屏幕 / 执行命令…）→ 三档全灰 + 说明；
 * - 「应用与启动」没开自由窗口 → 只灰「全屏 / 小窗」（"跟随"始终可点，便于把小窗改回来），
 *   此时容器设置整体失效、一律全屏。
 */
@Composable
private fun QuickWheelLaunchModeRow(
    mode: QuickWheelLaunchMode,
    freeWindowEnabled: Boolean,
    /** 该动作是否真的会打开某个东西（参见 `GestureAction.opensSomething`）。 */
    launchesSomething: Boolean,
    onSelect: (QuickWheelLaunchMode) -> Unit,
) {
    val hint = when {
        !launchesSomething -> stringResource(R.string.quick_wheel_launch_unsupported)
        !freeWindowEnabled -> stringResource(R.string.quick_wheel_launch_gate_off)
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        QuickWheelChipRow(
            label = stringResource(R.string.quick_wheel_launch_mode),
            options = listOf(
                stringResource(R.string.quick_wheel_launch_inherit) to QuickWheelLaunchMode.INHERIT,
                stringResource(R.string.quick_wheel_launch_fullscreen) to
                    QuickWheelLaunchMode.FULLSCREEN,
                stringResource(R.string.quick_wheel_launch_free_window) to
                    QuickWheelLaunchMode.FREE_WINDOW,
            ),
            selected = mode,
            optionEnabled = { option ->
                launchesSomething &&
                    (option == QuickWheelLaunchMode.INHERIT || freeWindowEnabled)
            },
            onSelect = onSelect,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            )
        }
    }
}

/**
 * 把容器的「打开方式」投影到动作自带的启动形态上。
 *
 * **只用于动作选择器的显示与预选**（含上游那个"启动方式"弹窗的初始选中项与已选应用行的副标题）——
 * 选中后会被 `applyPickedAction` 收编回容器字段，动作本身仍然保持"跟随"。
 */
private fun GestureAction.withLaunchMode(mode: QuickWheelLaunchMode): GestureAction =
    if (this is GestureAction.LaunchApp) copy(windowMode = mode.toLaunchWindowMode()) else this

@Composable
private fun QuickWheelIconNameRow(
    slot: QuickWheelSlot,
    onNameChange: (String) -> Unit,
    onOpenIconMenu: () -> Unit,
    /**
     * 图标选择菜单。
     *
     * ⚠️ 必须挂在这个 56dp 图标框里：`DropdownMenu` 只按**父布局节点**定位，
     * 之前它挂在页面根节点上，于是弹在屏幕左上角、离图标很远。
     */
    menuContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable { onOpenIconMenu() },
            contentAlignment = Alignment.Center,
        ) {
            // 菜单只是 Popup，不参与这里的排版，所以不会影响图标居中。
            menuContent()
            val preview = slot.copy(name = slot.name.ifBlank { " " })
            val vector = if (preview.iconSource == QuickWheelIconSource.ICON_LIBRARY) {
                QuickWheelIconResolver.vectorFor(preview.iconValue)
            } else {
                null
            }
            val bitmap = when (preview.iconSource) {
                QuickWheelIconSource.APP_ICON, QuickWheelIconSource.GALLERY ->
                    QuickWheelIconResolver.bitmapFor(context, preview, 48)

                else -> null
            }
            when {
                // 文字图标：预览框里直接显示文字。
                preview.iconSource == QuickWheelIconSource.TEXT && preview.iconValue.isNotBlank() ->
                    Text(
                        text = preview.iconValue,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )

                vector != null -> Icon(
                    imageVector = vector,
                    contentDescription = stringResource(R.string.quick_wheel_slot_icon),
                    modifier = Modifier.size(28.dp),
                )

                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.quick_wheel_slot_icon),
                    modifier = Modifier.size(40.dp),
                )

                // 容器自身没配图标 → 与轮盘 / HUD **同一套渲染**（QuickWheelSlotIcon）：
                // 打开应用 / 快捷方式显示**真实图标**，不再是一律的通用矢量箭头。
                preview.iconSource == QuickWheelIconSource.NONE &&
                    preview.tapAction.type != GestureActionType.NONE -> QuickWheelSlotIcon(
                    slot = preview,
                    builtinDp = 28.dp,
                    rasterDp = 40.dp,
                    // 页面跟随主题（深浅色），不能沿用容器上写死的深色 tint。
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 图标库 key 已失效等极端情况：退回通用动作图标。
                preview.tapAction.type != GestureActionType.NONE -> Icon(
                    imageVector = gestureActionIcon(preview.tapAction, outlined = true),
                    contentDescription = stringResource(R.string.quick_wheel_slot_icon),
                    modifier = Modifier.size(28.dp),
                )

                else -> Text(
                    text = stringResource(R.string.quick_wheel_slot_icon),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            // 名称为空时，默认显示所选动作的名字（应用名 / 功能名等）；手动输入即成为自定义名称。
            value = slot.name.ifBlank { gestureActionLabelText(context, slot.tapAction) },
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stringResource(R.string.quick_wheel_slot_label)) },
        )
    }
}

@Composable
private fun QuickWheelActionRow(
    title: String,
    action: GestureAction,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 与容器 / HUD 同一套渲染：打开应用 / 快捷方式显示真实图标（原来这里只画通用矢量箭头）。
        QuickWheelSlotIcon(
            slot = QuickWheelSlot(tapAction = action),
            builtinDp = 24.dp,
            rasterDp = 24.dp,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = gestureActionLabelText(context, action),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun QuickWheelAppPickerDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember {
        OverlayDependencyAccess.overlayDependencies(context)
            ?.appRepository
            ?.getCachedApps()
            .orEmpty()
            .sortedBy { it.pinyinKey }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) {
            apps
        } else {
            val needle = query.lowercase(Locale.getDefault())
            apps.filter {
                it.label.lowercase(Locale.getDefault()).contains(needle) ||
                    it.packageName.lowercase(Locale.getDefault()).contains(needle)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_wheel_icon_pick_app)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.quick_wheel_pick_app)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        TextButton(
                            onClick = { onPick(app.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(app.label.ifBlank { app.packageName })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 图库图片复制到应用私有目录，返回落盘绝对路径。 */
private fun copyIntoGalleryDir(context: Context, uri: Uri): String? = runCatching {
    val dir = QuickWheelIconResolver.galleryDir(context)
    val file = File(dir, "icon_${System.currentTimeMillis()}.png")
    val stream = context.contentResolver.openInputStream(uri) ?: return null
    stream.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    file.absolutePath
}.getOrNull()
