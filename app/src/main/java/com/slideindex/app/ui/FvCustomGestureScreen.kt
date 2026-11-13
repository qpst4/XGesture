/*
 * FV「自定义手势」列表页 + 录入弹窗的 Compose 入口。
 *
 * 行为照 FooView 1.6.4（证据见 docs/reverse/fv-custom-gesture/）：
 *   - 列表只显示**非镜像**手势（FV `q1.o.s()/o(i)` 都跳过 `id >= base+500` 的条目）；
 *   - 每项 = 动作图标 + 动作名 + 手绘轨迹缩略图（有对称手势时再画一张水平翻转的缩略图）+ 编辑 / 删除；
 *   - 右上角「＋」新增；删除前二次确认；删除时 `id` 与 `id+500` 一起删（FV `q1.o.J()`）；
 *   - 「执行」那一行的动作选择复用本仓库既有的动作选择页
 *     （`GestureActionPickerScreen` + `ActionPickerCatalogPolicy.Slot(SlotPickerKind.OverlayTap)`，
 *     与 FloatBallNavEntries.kt:387 悬浮球手势槽位同一套策略）。
 *
 * 与 FV 的一处结构性差异（有意）：
 *   FV 的动作选择页是独立页面，选中后写进 `i0.g` 动作表；本实现把「正在编辑的手势」放进一个内存草稿
 *   （[FvGestureDraft]），动作选择页作为本页的一个**内部阶段**呈现。原因：动作要绑到「尚未落盘的手绘
 *   轨迹」上，而轨迹对象（android.gesture.Gesture）塞不进 NavKey；若按仓库惯例再开一个导航目的地，
 *   选完动作回来草稿就丢了。选择页本身（同一 Composable + 同一 catalogPolicy）没有重写。
 *
 * 手势库是文件 IO：列表数据一律在 IO 线程读成 [FvGestureRow] 快照，组合期只做渲染。
 */
package com.slideindex.app.ui

import android.gesture.Gesture
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.fvgesture.FvGestureLibrary
import com.slideindex.app.fvgesture.FvGesturePrefs
import com.slideindex.app.fvgesture.gestureToBitmap
import com.slideindex.app.fvgesture.mirrorBitmap
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsGroupedCardItems
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 本页内部的阶段（列表 → 录入弹窗 → 动作选择 → 各种子选择页）。 */
private enum class FvGestureStage {
    List,
    PickActions,
    PickMyShortcuts,
    PickPresetShortcuts,
    PickApp,
    PickActivity,
    ShellCommand,
    KeyEvent,
}

/**
 * 「正在编辑的手势」草稿。跨阶段保留，所以轨迹、动作、对称开关都挂在它上面。
 *
 * @param editingId 编辑既有手势时的基 ID；新增为 0
 */
private class FvGestureDraft(
    val editingId: Int,
    var gesture: Gesture?,
    var action: GestureAction,
    var mirror: Boolean,
)

/** 列表一行的数据快照（轨迹与镜像轨迹都在 IO 线程取好，组合期不再读盘）。 */
private class FvGestureRow(
    val id: Int,
    val action: GestureAction,
    val gesture: Gesture?,
    val mirror: Gesture?,
)

/** 缩略图尺寸（FV 列表用 56dp 位图；这里按设置行的可用高度取小一点）。 */
private const val ThumbSizeDp = 36

@Composable
fun FvCustomGestureScreen(
    appSettings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(FvGestureStage.List) }
    var rows by remember { mutableStateOf<List<FvGestureRow>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<FvGestureDraft?>(null) }
    var deleteTarget by remember { mutableStateOf<FvGestureRow?>(null) }
    var pendingPackage by remember { mutableStateOf<String?>(null) }
    var shellInitial by remember { mutableStateOf("") }
    var keyEventInitial by remember { mutableStateOf<GestureAction.SimulateKeyEvent?>(null) }

    // revision 每次增删改后 +1，用来重新拉一次列表快照。
    LaunchedEffect(revision) {
        rows = withContext(Dispatchers.IO) { loadRows() }
    }
    val noneLabel = stringResource(R.string.fv_custom_gesture_action_none)
    val needGestureText = stringResource(R.string.fv_custom_gesture_need_gesture)
    val needActionText = stringResource(R.string.fv_custom_gesture_need_action)
    val saveFailedText = stringResource(R.string.fv_custom_gesture_save_failed)
    val conflictFailedTitle = stringResource(R.string.fv_custom_gesture_failed_title)
    val conflictStraightLine = stringResource(R.string.fv_custom_gesture_straight_line)
    val conflictOccupyFormat = stringResource(R.string.fv_custom_gesture_conflict_message)

    // 冲突预检结果：id == 0 → 直线手势；否则是「已被别人占用」的那个手势 ID。
    var conflict by remember { mutableStateOf<FvGestureConflict?>(null) }

    // ---------------------------------------------------------------- 录入弹窗（Miuix，Compose）
    val currentDraft = draft
    if (currentDraft != null && stage == FvGestureStage.List) {
        FvCustomGestureEditDialog(
            initialGesture = currentDraft.gesture,
            initialMirror = currentDraft.mirror,
            actionLabel = gestureActionLabelText(context, currentDraft.action).ifBlank { noneLabel },
            onGestureDrawn = { drawn ->
                // FV q1.m$c.onGestureEnded：抬手就做一次冲突预检（主动作；对称手势在确定时再查）
                currentDraft.gesture = drawn
                if (drawn != null) {
                    conflict = detectConflict(drawn, currentDraft.editingId)
                }
            },
            onActionRowClick = { gesture, mirror ->
                // 先把画板上的轨迹与开关收进草稿，再去选动作（选择页会把弹窗顶掉）
                currentDraft.gesture = gesture ?: currentDraft.gesture
                currentDraft.mirror = mirror
                stage = FvGestureStage.PickActions
            },
            onMirrorChanged = { currentDraft.mirror = it },
            onConfirm = { gesture, mirrored, mirror ->
                when {
                    gesture == null || gesture.strokesCount <= 0 ->
                        Toast.makeText(context, needGestureText, Toast.LENGTH_SHORT).show()

                    !currentDraft.action.isEffective() ->
                        Toast.makeText(context, needActionText, Toast.LENGTH_SHORT).show()

                    else -> {
                        // FV 顺序：先查主动作（⑤），再查对称动作（⑥）
                        val mainConflict = detectConflict(gesture, currentDraft.editingId)
                        val mirrorConflict = if (mirrored != null) {
                            detectConflict(mirrored, currentDraft.editingId)
                        } else {
                            null
                        }
                        val hit = mainConflict ?: mirrorConflict
                        if (hit != null) {
                            conflict = hit
                        } else {
                            currentDraft.gesture = gesture
                            currentDraft.mirror = mirror
                            // FV: type==1 时把「同时添加对称手势」记进偏好（下次打开弹窗沿用）
                            FvGesturePrefs.setAddMirrorGesture(context, mirror)
                            val editingId = currentDraft.editingId
                            val action = currentDraft.action
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    val library = FvGestureLibrary.of(FvGestureLibrary.TYPE_ICON)
                                    if (editingId > 0) {
                                        library.update(editingId, gesture, mirrored, action)
                                    } else {
                                        library.add(gesture, mirrored, action) > 0
                                    }
                                }
                                if (ok) {
                                    draft = null
                                    revision++
                                } else {
                                    Toast.makeText(context, saveFailedText, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            },
            onDismiss = { draft = null },
        )
    }

    // 冲突提示（FV: FooSettingCustomGuesture.p）
    val conflictInfo = conflict
    MiuixConfirmDialog(
        show = conflictInfo != null,
        onDismissRequest = { conflict = null },
        title = conflictFailedTitle,
        message = when {
            conflictInfo == null -> null
            conflictInfo.gestureId == 0 -> conflictStraightLine
            else -> conflictOccupyFormat.format(
                gestureActionLabelText(context, conflictInfo.occupiedAction).ifBlank { noneLabel }
            )
        },
        onConfirm = { conflict = null },
    )

    // ---------------------------------------------------------------- 各阶段
    when (stage) {
        FvGestureStage.List -> FvCustomGestureList(
            rows = rows,
            onBack = onBack,
            onAdd = {
                draft = FvGestureDraft(
                    editingId = 0,
                    gesture = null,
                    action = GestureAction.None,
                    mirror = FvGesturePrefs.isAddMirrorGesture(context),
                )
            },
            onEdit = { row ->
                draft = FvGestureDraft(
                    editingId = row.id,
                    gesture = row.gesture,
                    action = row.action,
                    mirror = row.mirror != null,
                )
            },
            onDelete = { deleteTarget = it },
        )

        FvGestureStage.PickActions -> GestureActionPickerScreen(
            // 悬浮球槽位同款触发类型与槽位策略（见 FloatBallNavEntries.kt:387 那一段）
            trigger = GestureTriggerType.SHORT_SINGLE_TAP,
            current = draft?.action ?: GestureAction.None,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.OverlayTap),
            onDismiss = { stage = FvGestureStage.List },
            onSelect = { action ->
                draft?.action = action
                stage = FvGestureStage.List
            },
            onOpenMyShortcuts = { stage = FvGestureStage.PickMyShortcuts },
            onOpenPresetShortcuts = { stage = FvGestureStage.PickPresetShortcuts },
            onOpenPickApp = { stage = FvGestureStage.PickApp },
            onOpenExecuteShellCommand = { command ->
                shellInitial = command
                stage = FvGestureStage.ShellCommand
            },
            onOpenSimulateKeyEvent = { action ->
                keyEventInitial = action
                stage = FvGestureStage.KeyEvent
            },
        )

        FvGestureStage.PickMyShortcuts -> MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { stage = FvGestureStage.PickActions },
            onBrowseNewShortcut = { stage = FvGestureStage.PickApp },
            currentAction = draft?.action,
            onSelectRadio = { action ->
                draft?.action = action
                stage = FvGestureStage.List
            },
        )

        FvGestureStage.PickPresetShortcuts -> PresetShortcutsFolderScreen(
            onBack = { stage = FvGestureStage.PickActions },
            currentAction = draft?.action,
            onSelectRadio = { action ->
                draft?.action = action
                stage = FvGestureStage.List
            },
        )

        FvGestureStage.PickApp -> ActivityShortcutPickAppScreen(
            onBack = { stage = FvGestureStage.PickActions },
            onSelectApp = { app ->
                pendingPackage = app.packageName
                stage = FvGestureStage.PickActivity
            },
        )

        FvGestureStage.PickActivity -> {
            val packageName = pendingPackage
            if (packageName == null) {
                // 没有选中包名（正常流程到不了这里）：用 effect 退回选应用，避免在组合期写状态
                LaunchedEffect(Unit) { stage = FvGestureStage.PickApp }
            } else {
                ActivityShortcutPickActivityScreen(
                    packageName = packageName,
                    onBack = { stage = FvGestureStage.PickApp },
                    onSelectActivity = { activity ->
                        draft?.action = GestureAction.LaunchShortcut.component(
                            "${activity.packageName}/${activity.className}",
                            activity.label,
                        )
                        stage = FvGestureStage.List
                    },
                )
            }
        }

        FvGestureStage.ShellCommand -> GestureExecuteShellCommandScreen(
            initialCommand = shellInitial,
            shellCommands = appSettings.shellCommands,
            onBack = { stage = FvGestureStage.PickActions },
            onConfirm = { command ->
                draft?.action = GestureAction.ExecuteShellCommand(command)
                stage = FvGestureStage.List
            },
        )

        FvGestureStage.KeyEvent -> GestureSimulateKeyEventScreen(
            initialAction = keyEventInitial,
            onBack = { stage = FvGestureStage.PickActions },
            onConfirm = { action ->
                draft?.action = action
                stage = FvGestureStage.List
            },
        )
    }

    // ---------------------------------------------------------------- 删除确认
    val deleteTitle = stringResource(R.string.fv_custom_gesture_delete_title)
    val deleteMessage = stringResource(R.string.fv_custom_gesture_delete_message)
    MiuixConfirmDialog(
        show = deleteTarget != null,
        onDismissRequest = { deleteTarget = null },
        title = deleteTitle,
        message = deleteMessage,
        onConfirm = {
            val target = deleteTarget ?: return@MiuixConfirmDialog
            deleteTarget = null
            scope.launch {
                // FV: q1.o.J(id) —— 同时删除 id 与 id+500
                withContext(Dispatchers.IO) {
                    FvGestureLibrary.of(FvGestureLibrary.TYPE_ICON).delete(target.id)
                }
                revision++
            }
        },
    )
}

/**
 * 冲突预检结果。
 *
 * @param gestureId FV `q1.o.H(g, true)` 的返回值：0 = 画的是直线（禁用）；> 0 = 命中了别人的手势
 * @param occupiedAction 被占用的那个手势当前绑定的动作（用来在提示里显示"已经给谁用了"）
 */
private class FvGestureConflict(
    val gestureId: Int,
    val occupiedAction: GestureAction,
)

/**
 * FV `FooSettingCustomGuesture.i(...)` 的冲突判定：
 *
 * - `id == 0`  → 直线手势，直接冲突（FV 文案 `input_gesture_error_1`）
 * - `id > 0` 且 base ID 不等于正在编辑的那条 → 冲突（`base != editingId` 就是 FV 的 `k10 != v10.f18220a`）
 * - `id < 0`（没识别到任何已有手势）→ 放行，可以新增
 */
private fun detectConflict(gesture: Gesture, editingId: Int): FvGestureConflict? {
    val library = FvGestureLibrary.of(FvGestureLibrary.TYPE_ICON)
    val id = library.recognize(gesture, rawId = true)
    if (id == 0) return FvGestureConflict(gestureId = 0, occupiedAction = GestureAction.None)
    val base = library.normalizeId(id)
    if (id > 0 && base != editingId) {
        return FvGestureConflict(
            gestureId = id,
            occupiedAction = library.actionOf(base) ?: GestureAction.None,
        )
    }
    return null
}

/** 在 IO 线程读一次列表快照（轨迹与镜像轨迹都取好，组合期不再读盘）。 */
private fun loadRows(): List<FvGestureRow> {    val library = FvGestureLibrary.of(FvGestureLibrary.TYPE_ICON)
    return library.userInfos().map { info ->
        FvGestureRow(
            id = info.id,
            action = info.action,
            gesture = library.gestureOf(info.id),
            mirror = library.mirrorGestureOf(info.id),
        )
    }
}

@Composable
private fun FvCustomGestureList(
    rows: List<FvGestureRow>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (FvGestureRow) -> Unit,
    onDelete: (FvGestureRow) -> Unit,
) {
    val context = LocalContext.current
    val addLabel = stringResource(R.string.fv_custom_gesture_add)
    val editLabel = stringResource(R.string.fv_custom_gesture_edit)
    val deleteLabel = stringResource(R.string.fv_custom_gesture_delete)
    val emptyLabel = stringResource(R.string.fv_custom_gesture_empty)
    val mirrorBadge = stringResource(R.string.fv_custom_gesture_mirror_badge)
    val noneLabel = stringResource(R.string.fv_custom_gesture_action_none)
    // 与悬浮球手势设置页同样的约束：settingsLazySmallTitle 的 title 是普通 String，必须在 Lazy 作用域外解析。
    val sectionTitle = stringResource(R.string.fv_custom_gesture_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.fv_custom_gesture_title),
        pageHint = stringResource(R.string.fv_custom_gesture_summary),
        onBack = onBack,
        actions = {
            MiuixTopBarIconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = addLabel)
            }
        },
    ) {
        if (rows.isEmpty()) {
            settingsLazyHint(key = "fv-custom-gesture-empty", text = emptyLabel)
            return@SettingsScreenScaffold
        }
        settingsLazySmallTitle(
            key = "fv-custom-gesture-section",
            title = sectionTitle,
        )
        settingsGroupedCardItems(
            keyPrefix = "fv-custom-gesture",
            items = rows.map { row ->
                settingsCardScopeItem("fv-custom-gesture-${row.id}") {
                    val actionLabel = gestureActionLabelText(context, row.action)
                        .ifBlank { noneLabel }
                    SettingNavigationRow(
                        icon = { label ->
                            Icon(
                                gestureActionIcon(row.action),
                                contentDescription = label,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        title = actionLabel,
                        subtitle = if (row.mirror != null) mirrorBadge else "",
                        onClick = { onEdit(row) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 轨迹可能读不出来（文件被外部破坏时 gestureOf 返回 null），那就只显示动作行
                                row.gesture?.let { gesture ->
                                    GestureThumbnail(
                                        gesture = gesture,
                                        mirrored = false,
                                        contentDescription = actionLabel,
                                    )
                                    // 对称缩略图 = **同一张位图水平翻转**（FV `q1.o` 列表里用 `n5.w0.A(j10)`，
                                    // 而不是去渲染 `t(id)` 存下来的那条镜像手势 —— 后者本身已经是镜像，
                                    // 再翻一次就变回原图了，会出现"两个缩略图一模一样"的假象）。
                                    // 翻转后能看到起点绿点/终点箭头换到另一侧。
                                    if (row.mirror != null) {
                                        GestureThumbnail(
                                            gesture = gesture,
                                            mirrored = true,
                                            contentDescription = mirrorBadge,
                                        )
                                    }
                                }
                                IconButton(onClick = { onEdit(row) }) {
                                    Icon(Icons.Default.Edit, contentDescription = editLabel)
                                }
                                IconButton(onClick = { onDelete(row) }) {
                                    Icon(Icons.Default.Delete, contentDescription = deleteLabel)
                                }
                            }
                        },
                    )
                }
            },
        )
    }
}

/**
 * 手绘轨迹缩略图。`mirrored = true` 时把同一张位图水平翻转（对应 FV `n5.w0.A()` 的
 * `Matrix.setScale(-1,1)` + `postTranslate(w,0)`），用于展示「对称手势」。
 */
@Composable
private fun GestureThumbnail(
    gesture: Gesture,
    mirrored: Boolean,
    contentDescription: String?,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { (ThumbSizeDp * density).toInt() }
    val key = System.identityHashCode(gesture)
    // 位图渲染放 Default 线程；key 只用手势身份，mirrored/sizePx 对同一行是常量
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key) {
        value = withContext(Dispatchers.Default) {
            val base = gestureToBitmap(
                gesture = gesture,
                widthPx = sizePx,
                heightPx = sizePx,
                // FV 的列表缩略图内边距是 10dp（`q1.o.j()` 传 q.b(ctx,10)），这里按尺寸等比取
                insetPx = (sizePx / 6).coerceAtLeast(1),
                color = 0xFFFF0000.toInt(),
                // FV 只在图标手势库（type == 1）画起点绿点，本页就是那个库
                showStart = true,
                context = context,
            ) ?: return@withContext null
            (if (mirrored) mirrorBitmap(base) else base)?.asImageBitmap()
        }
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = Modifier.size(ThumbSizeDp.dp),
        )
    }
}

