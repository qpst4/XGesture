package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.slideindex.app.R
import com.slideindex.app.overlay.quickwheel.toWheelShapeOrNull
import com.slideindex.app.gesture.QuickWheelAnchorMode
import com.slideindex.app.gesture.QuickWheelLaunchLevelShape
import com.slideindex.app.gesture.QuickWheelLaunchShape
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsGroupedCardItems
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * 「快捷轮盘」动作的参数配置页（在动作绑定处以 Dialog 形式弹出）。
 *
 * 选择三件事：
 * 1. 触发**哪个轮盘**（写入 [com.slideindex.app.gesture.GestureAction.QuickWheel.wheelId]）；
 * 2. 以什么**形态呼出**（写入 [com.slideindex.app.gesture.GestureAction.QuickWheel.shape]）：
 *    一行开关「使用轮盘自身配置的形态」（默认开 = [QuickWheelLaunchShape.DEFAULT]），
 *    关掉后才出现"一级轮盘形态 / 二级轮盘形态"两行二选一，对应四个「一级 + 二级」组合值；
 *    旧数据里"只覆盖一级"的 [QuickWheelLaunchShape.CIRCLE] / [QuickWheelLaunchShape.RECT]
 *    会被展开成与当下行为等价的组合（二级按该轮盘自身的二级形态补齐）；
 * 3. 一级为**圆形**时的**扇区**（写入
 *    [com.slideindex.app.gesture.GestureAction.QuickWheel.manualSectorMask]）：
 *    扇区环**一个都不选 = 自动**（运行时按触发位置求解），与开关默认态一样表示"不想操心，
 *    程序自动"；选了扇区才手动固定该跨度（可能出屏，属用户显式选择）。二级与矩形基准角始终自动。
 * 4. 圆心**锚定方式**（[QuickWheelAnchorMode]）：跟手（默认，圆心 = 触发那一帧的手指位置）/
 *    贴边（圆心投影到最近的屏幕边缘）。**不受第 2 点的开关管辖** —— 锚点与形态是两条正交的轴。
 *
 * 确认后由调用方构造 `GestureAction.QuickWheel` 并写回。
 */
@Composable
fun GestureQuickWheelActionScreen(
    wheels: List<QuickWheel>,
    initialWheelId: String,
    initialShape: QuickWheelLaunchShape,
    initialSectorMask: Int? = null,
    initialAnchorMode: QuickWheelAnchorMode = QuickWheelAnchorMode.FOLLOW_FINGER,
    onBack: () -> Unit,
    onConfirm: (
        wheelId: String,
        shape: QuickWheelLaunchShape,
        manualSectorMask: Int?,
        anchorMode: QuickWheelAnchorMode,
    ) -> Unit,
    overlayMode: Boolean = false,
) {
    // 初始未绑定具体轮盘（空串）时默认选中第一个，给出明确的选择结果。
    var selectedWheelId by remember(initialWheelId, wheels) {
        mutableStateOf(initialWheelId.ifBlank { wheels.firstOrNull()?.id.orEmpty() })
    }
    // 形态选择三行：① 开关（默认开 = 用轮盘自身配置的形态）②③ 关掉后才出现的"一级 / 二级"二选一。
    var useWheelShape by remember(initialShape) {
        mutableStateOf(initialShape == QuickWheelLaunchShape.DEFAULT)
    }
    // 自定义形态的初值取"当前生效形态"；编辑旧绑定（二级 FOLLOW）时按所选轮盘自身的形态补齐。
    var primaryShapeChoice by remember(initialShape) {
        mutableStateOf(
            initialShape.primaryLevel.toWheelShapeOrNull()
                ?: wheels.firstOrNull { it.id == selectedWheelId }?.primaryShape
                ?: QuickWheelShape.CIRCLE,
        )
    }
    var secondaryShapeChoice by remember(initialShape) {
        mutableStateOf(
            initialShape.secondaryLevel.toWheelShapeOrNull()
                ?: wheels.firstOrNull { it.id == selectedWheelId }?.secondaryShape
                ?: QuickWheelShape.CIRCLE,
        )
    }
    // 一级圆形的扇区：**一个都不选（0）= 自动**（运行时按触发位置求解），与"使用轮盘自身配置的
    // 形态"一样表示"我不想操心，程序自动"；选了扇区才手动固定。
    // ⚠️ 与轮盘配置页不同：这里允许取消到空，否则回不到"自动"。
    var sectorMask by remember(initialSectorMask) { mutableStateOf(initialSectorMask ?: 0) }
    // 圆心锚定方式：跟手（默认）/ 贴边。与形态开关无关，始终可选。
    var anchorMode by remember(initialAnchorMode) { mutableStateOf(initialAnchorMode) }
    // 正在弹"二选一"的那一级；null = 没有弹框。
    var pickingLevel by remember { mutableStateOf<QuickWheelLevelTarget?>(null) }
    var pickingAnchor by remember { mutableStateOf(false) }

    // LazyListScope 内容不是 composable 作用域，段标题需在此处先解析成 String。
    val wheelSectionTitle = stringResource(R.string.quick_wheel_action_wheel_section)
    val shapeSectionTitle = stringResource(R.string.quick_wheel_action_shape_section)
    val anchorSectionTitle = stringResource(R.string.quick_wheel_action_anchor_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.quick_wheel_action_config_title),
        pageHint = stringResource(R.string.quick_wheel_action_config_hint),
        onBack = onBack,
        overlayMode = overlayMode,
        actions = {
            IconButton(
                // ⚠️ 没有轮盘可选时必须禁用确认：wheels 为空时 selectedWheelId 会是空串，
                // 点确认会落库成 GestureAction.QuickWheel(wheelId = "")，
                // 这个绑定运行时永远找不到轮盘、静默什么都不做（界面上看着像"设了没反应"）。
                enabled = selectedWheelId.isNotBlank(),
                onClick = {
                    onConfirm(
                        selectedWheelId,
                        if (useWheelShape) {
                            QuickWheelLaunchShape.DEFAULT
                        } else {
                            quickWheelLaunchShapeOf(primaryShapeChoice, secondaryShapeChoice)
                        },
                        // 空环 = 自动（null）；**跟随轮盘自身配置、或一级不是圆形时一律不指定** ——
                        // 矩形没有"扇区"概念，否则"先选圆形+扇区、再改成矩形保存"会把无效掩码写进载荷。
                        if (useWheelShape || primaryShapeChoice != QuickWheelShape.CIRCLE) {
                            null
                        } else {
                            sectorMask.takeIf { it != 0 }
                        },
                        anchorMode,
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
            key = "qw_action_wheel",
            title = wheelSectionTitle,
        )
        settingsGroupedCardItems(
            keyPrefix = "qw_action_wheel",
            items = buildList {
                if (wheels.isEmpty()) {
                    add(
                        settingsCardScopeItem("empty") {
                            Text(
                                text = stringResource(R.string.quick_wheel_action_wheel_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        },
                    )
                } else {
                    wheels.forEach { wheel ->
                        add(
                            settingsCardScopeItem("wheel-${wheel.id}") {
                                QuickWheelActionChoiceRow(
                                    title = wheel.name.ifBlank {
                                        stringResource(R.string.quick_wheel_label_fallback)
                                    },
                                    subtitle = stringResource(
                                        R.string.quick_wheel_action_wheel_default_shape,
                                        quickWheelShapeLabel(wheel.primaryShape),
                                        quickWheelShapeLabel(wheel.secondaryShape),
                                    ),
                                    selected = selectedWheelId == wheel.id,
                                    onClick = { selectedWheelId = wheel.id },
                                )
                            },
                        )
                    }
                }
            },
        )

        settingsLazySmallTitle(
            key = "qw_action_shape",
            title = shapeSectionTitle,
        )
        settingsGroupedCardItems(
            keyPrefix = "qw_action_shape",
            items = buildList {
                add(
                    settingsCardScopeItem("shape-follow-wheel") {
                        QuickWheelSwitchRow(
                            title = stringResource(R.string.quick_wheel_action_shape_follow_wheel),
                            description = stringResource(
                                R.string.quick_wheel_action_shape_follow_wheel_desc,
                            ),
                            checked = useWheelShape,
                            onCheckedChange = { useWheelShape = it },
                        )
                    },
                )
                // 关掉开关才出现：一级 / 二级各自二选一。
                if (!useWheelShape) {
                    add(
                        settingsCardScopeItem("shape-primary") {
                            QuickWheelShapeChoiceRow(
                                title = stringResource(R.string.quick_wheel_action_shape_primary_row),
                                value = quickWheelShapeLabel(primaryShapeChoice),
                                onClick = { pickingLevel = QuickWheelLevelTarget.PRIMARY },
                            )
                        },
                    )
                    // 只有一级圆形才有扇区可选（矩形没有"扇区"概念，始终由网格基准角自适应）。
                    if (primaryShapeChoice == QuickWheelShape.CIRCLE) {
                        add(
                            settingsCardScopeItem("shape-sector") {
                                QuickWheelActionSectorRow(
                                    sectorMask = sectorMask,
                                    onToggleSector = { index ->
                                        // ⚠️ 与轮盘配置页的 toggleSector 不同：这里**允许取消到空** ——
                                        // 全空 = 自动，必须能回到空态（配置页会拦住"取消最后一个扇区"）。
                                        sectorMask = (sectorMask xor (1 shl index)) and
                                            QuickWheelLayoutEngine.SECTOR_ALL
                                    },
                                )
                            },
                        )
                    }
                    add(
                        settingsCardScopeItem("shape-secondary") {
                            QuickWheelShapeChoiceRow(
                                title = stringResource(R.string.quick_wheel_action_shape_secondary_row),
                                value = quickWheelShapeLabel(secondaryShapeChoice),
                                onClick = { pickingLevel = QuickWheelLevelTarget.SECONDARY },
                            )
                        },
                    )
                }
            },
        )

        // 呼出位置（圆心锚定方式）：**不受"使用轮盘自身配置的形态"开关管辖** ——
        // 锚点与形态是两条正交的轴，想要"贴边"不该被迫放弃"跟随轮盘形态"。
        settingsLazySmallTitle(
            key = "qw_action_anchor",
            title = anchorSectionTitle,
        )
        settingsGroupedCardItems(
            keyPrefix = "qw_action_anchor",
            items = listOf(
                settingsCardScopeItem("anchor-mode") {
                    QuickWheelShapeChoiceRow(
                        title = stringResource(R.string.quick_wheel_action_anchor_row),
                        value = quickWheelAnchorModeLabel(anchorMode),
                        onClick = { pickingAnchor = true },
                    )
                },
            ),
        )
    }

    // 形态二选一弹框（一级 / 二级共用同一个）。
    pickingLevel?.let { level ->
        val isPrimary = level == QuickWheelLevelTarget.PRIMARY
        QuickWheelShapePickerDialog(
            title = stringResource(
                if (isPrimary) {
                    R.string.quick_wheel_action_shape_primary_row
                } else {
                    R.string.quick_wheel_action_shape_secondary_row
                },
            ),
            current = if (isPrimary) primaryShapeChoice else secondaryShapeChoice,
            onPick = { picked ->
                if (isPrimary) {
                    primaryShapeChoice = picked
                } else {
                    secondaryShapeChoice = picked
                }
                pickingLevel = null
            },
            onDismiss = { pickingLevel = null },
        )
    }

    // 锚点方式二选一弹框（跟手 / 贴边）。
    if (pickingAnchor) {
        QuickWheelAnchorModePickerDialog(
            current = anchorMode,
            onPick = { picked ->
                anchorMode = picked
                pickingAnchor = false
            },
            onDismiss = { pickingAnchor = false },
        )
    }
}

/**
 * 一级圆形的手动扇区行：**一个扇区都不选 = 自动**（运行时按触发位置求解）。
 *
 * 与轮盘配置页的扇区行是同一种环，但语义不同：这里的"空"是有意义的状态，所以空态不显示
 * "可用角度 N°"（空掩码会被 `selectedSectorCount` 兜底算成 90°，显示出来是误导）。
 */
@Composable
private fun QuickWheelActionSectorRow(
    sectorMask: Int,
    onToggleSector: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.quick_wheel_action_sector_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (sectorMask == 0) {
                    stringResource(R.string.quick_wheel_action_sector_auto)
                } else {
                    stringResource(
                        R.string.quick_wheel_sector_span,
                        QuickWheelLayoutEngine.sectorSpanDeg(sectorMask),
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = QuickWheelAccent,
            )
        }
        Text(
            text = stringResource(R.string.quick_wheel_action_sector_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        QuickWheelSectorRing(
            sectorMask = sectorMask,
            onToggleSector = onToggleSector,
            activeColor = QuickWheelAccent,
            inactiveColor = MaterialTheme.colorScheme.outlineVariant,
            outlineColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        )
    }
}

/** 单选行：选中时右侧显示对勾。 */
@Composable
private fun QuickWheelActionChoiceRow(
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
                tint = QuickWheelAccent,
            )
        }
    }
}

@Composable
private fun quickWheelShapeLabel(shape: QuickWheelShape): String = stringResource(
    if (shape == QuickWheelShape.CIRCLE) {
        R.string.quick_wheel_shape_circle
    } else {
        R.string.quick_wheel_shape_rect
    },
)

/**
 * 呼出形态的展示名（如「一级圆形 · 二级矩形」）。
 *
 * 非 private：动作列表的摘要文案（`gestureActionSettingSubtitle`）也用它，保证同一选项
 * 在配置页与列表里读法一致。
 */
@Composable
fun quickWheelLaunchShapeLabel(shape: QuickWheelLaunchShape): String = when (shape) {
    QuickWheelLaunchShape.DEFAULT -> stringResource(R.string.quick_wheel_action_shape_default)
    // 四组合：按「一级 + 二级」拼出两个形态名。
    QuickWheelLaunchShape.CIRCLE_CIRCLE,
    QuickWheelLaunchShape.CIRCLE_RECT,
    QuickWheelLaunchShape.RECT_CIRCLE,
    QuickWheelLaunchShape.RECT_RECT,
    -> stringResource(
        R.string.quick_wheel_action_shape_combo,
        quickWheelLevelShapeLabel(shape.primaryLevel),
        quickWheelLevelShapeLabel(shape.secondaryLevel),
    )
    // 旧值：只覆盖一级，二级跟随设置。
    QuickWheelLaunchShape.CIRCLE,
    QuickWheelLaunchShape.RECT,
    -> stringResource(
        R.string.quick_wheel_action_shape_primary_only,
        quickWheelLevelShapeLabel(shape.primaryLevel),
    )
}

/** 二选一弹框正在为哪一级选形态。 */
private enum class QuickWheelLevelTarget { PRIMARY, SECONDARY }
/** 一级 + 二级形态 → 呼出形态的四组合值（映射放 app 层：`:core:gesture` 不依赖 `:core:overlay-layout`）。 */
internal fun quickWheelLaunchShapeOf(
    primary: QuickWheelShape,
    secondary: QuickWheelShape,
): QuickWheelLaunchShape = when (primary) {
    QuickWheelShape.CIRCLE -> when (secondary) {
        QuickWheelShape.CIRCLE -> QuickWheelLaunchShape.CIRCLE_CIRCLE
        QuickWheelShape.RECT -> QuickWheelLaunchShape.CIRCLE_RECT
    }

    QuickWheelShape.RECT -> when (secondary) {
        QuickWheelShape.CIRCLE -> QuickWheelLaunchShape.RECT_CIRCLE
        QuickWheelShape.RECT -> QuickWheelLaunchShape.RECT_RECT
    }
}

/** 一级 / 二级形态选择行：左边标题、右边当前值（+ 箭头），整行可点。 */
@Composable
private fun QuickWheelShapeChoiceRow(
    title: String,
    value: String,
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = QuickWheelAccent,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 锚点方式展示名（如「跟手（触发点）」）。 */
@Composable
private fun quickWheelAnchorModeLabel(mode: QuickWheelAnchorMode): String = stringResource(
    when (mode) {
        QuickWheelAnchorMode.FOLLOW_FINGER -> R.string.quick_wheel_action_anchor_follow
        QuickWheelAnchorMode.EDGE -> R.string.quick_wheel_action_anchor_edge
    },
)

/** 锚点方式二选一弹框（居中卡片：跟手 / 贴边）。 */
@Composable
private fun QuickWheelAnchorModePickerDialog(
    current: QuickWheelAnchorMode,
    onPick: (QuickWheelAnchorMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(300.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.quick_wheel_action_anchor_row),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = 4.dp,
                        ),
                    )
                    QuickWheelActionChoiceRow(
                        title = stringResource(R.string.quick_wheel_action_anchor_follow),
                        subtitle = stringResource(R.string.quick_wheel_action_anchor_follow_desc),
                        selected = current == QuickWheelAnchorMode.FOLLOW_FINGER,
                        onClick = { onPick(QuickWheelAnchorMode.FOLLOW_FINGER) },
                    )
                    QuickWheelActionChoiceRow(
                        title = stringResource(R.string.quick_wheel_action_anchor_edge),
                        subtitle = stringResource(R.string.quick_wheel_action_anchor_edge_desc),
                        selected = current == QuickWheelAnchorMode.EDGE,
                        onClick = { onPick(QuickWheelAnchorMode.EDGE) },
                    )
                }
            }
        }
    }
}

/** 形态二选一弹框（居中卡片：圆形 / 矩形）。 */
@Composable
private fun QuickWheelShapePickerDialog(
    title: String,
    current: QuickWheelShape,
    onPick: (QuickWheelShape) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(300.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = 4.dp,
                        ),
                    )
                    QuickWheelActionChoiceRow(
                        title = quickWheelShapeLabel(QuickWheelShape.CIRCLE),
                        subtitle = stringResource(R.string.quick_wheel_shape_circle_desc),
                        selected = current == QuickWheelShape.CIRCLE,
                        onClick = { onPick(QuickWheelShape.CIRCLE) },
                    )
                    QuickWheelActionChoiceRow(
                        title = quickWheelShapeLabel(QuickWheelShape.RECT),
                        subtitle = stringResource(R.string.quick_wheel_shape_rect_desc),
                        selected = current == QuickWheelShape.RECT,
                        onClick = { onPick(QuickWheelShape.RECT) },
                    )
                }
            }
        }
    }
}

/**
 * 单个层级的形态名（圆形 / 矩形）。
 *
 * [QuickWheelLaunchLevelShape.FOLLOW] 只在 [QuickWheelLaunchShape.DEFAULT] 上出现，
 * 而默认值不走这两个拼接分支，这里退化为默认文案只是为了穷尽分支。
 */
@Composable
private fun quickWheelLevelShapeLabel(level: QuickWheelLaunchLevelShape): String = stringResource(
    when (level) {
        QuickWheelLaunchLevelShape.CIRCLE -> R.string.quick_wheel_shape_circle
        QuickWheelLaunchLevelShape.RECT -> R.string.quick_wheel_shape_rect
        QuickWheelLaunchLevelShape.FOLLOW -> R.string.quick_wheel_action_shape_default
    },
)
