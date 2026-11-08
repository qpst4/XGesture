package com.slideindex.app.overlay.volumepanel

import com.slideindex.app.overlay.overlayHostContext

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.findForLaunchShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerLaunchShortcutLeading
import com.slideindex.app.ui.Md3PickerPackageLeading
import com.slideindex.app.ui.gestureActionIcon
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val ExpandPanelCornerRadius = 20.dp

@Composable
fun VolumePanelContent(
    onDismiss: () -> Unit,
    /** overlay 窗里的键盘高度：面板内容要按它收短，配合根布局的整体抬起。 */
    imeBottom: Dp = 0.dp,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settingsFlow = remember(context) {
        com.slideindex.app.settings.SettingsFlowHolder.settingsFlow(context)
    }
    val scope = rememberCoroutineScope()
    var slotActions by remember { mutableStateOf(List<GestureAction?>(8) { null }) }
    var activityShortcuts by remember { mutableStateOf<List<ActivityShortcut>>(emptyList()) }
    var appSettings by remember { mutableStateOf(AppSettings()) }
    var isPickerMode by remember { mutableStateOf(false) }
    var isShortcutsEditMode by remember { mutableStateOf(false) }
    var pickerSlot by remember { mutableIntStateOf(-1) }
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val overlayContext = remember(context) {
        overlayHostContext() ?: context
    }
    val actionExecutor = remember { com.slideindex.app.overlay.panels.PanelRuntimeHooks.defaultGestureRunner }

    LaunchedEffect(settingsFlow) {
        settingsFlow?.collect { settings ->
            appSettings = settings
            slotActions = settings.expandPanelSlotActions
            activityShortcuts = settings.activityShortcuts
        }
    }
    LaunchedEffect(context) {
        val repository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.appRepository?.invoke(context)
            ?: return@LaunchedEffect
        val targets = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsSnapshot
            ?.invoke(context)?.freezerAppPackages.orEmpty()
        allApps = com.slideindex.app.overlay.panels.PanelRuntimeHooks.pickerAppsLoad
            ?.invoke(context, repository, targets).orEmpty()
    }

    fun runSlotAction(action: GestureAction, longPressArmed: Boolean = false) {
        onDismiss()
        val metrics = overlayContext.resources.displayMetrics
        actionExecutor?.run(
            action,
            appSettings,
            longPressArmed,
            metrics.widthPixels / 2f,
            metrics.heightPixels / 2f,
            null,
        )
    }

    fun saveSlotAction(index: Int, action: GestureAction?) {
        scope.launch {
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.setExpandPanelSlotAction
                ?.invoke(index, action)
        }
    }

    ExpandPanelFrostedSurface {
        AnimatedContent(
            targetState = isPickerMode,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    ) + fadeIn()).togetherWith(
                        slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut(),
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    ) + fadeIn()).togetherWith(
                        slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    )
                }
            },
            label = "expandPanelPicker",
        ) { pickerMode ->
            if (pickerMode) {
                val editingAction = slotActions.getOrNull(pickerSlot)
                val editingSubtitle = if (pickerSlot >= 0) {
                    val slotLabel = editingAction?.let {
                        expandPanelSlotLabel(context, it, activityShortcuts)
                    } ?: stringResource(R.string.expand_panel_slot_add_label)
                    stringResource(R.string.expand_panel_editing_slot, pickerSlot + 1, slotLabel)
                } else {
                    null
                }
                com.slideindex.app.overlay.volumepanel.PanelSlotPickerHook.picker?.content(
                    com.slideindex.app.overlay.volumepanel.SlotPickerRequest(
                        allApps = allApps,
                        activityShortcuts = activityShortcuts,
                        current = editingAction,
                        editingSubtitle = editingSubtitle,
                        wheels = appSettings.launcher.quickWheels,
                        panels = appSettings.quickLauncherPanels,
                        imeBottom = imeBottom,
                        onSelect = { action ->
                            if (pickerSlot in slotActions.indices) {
                                saveSlotAction(pickerSlot, action)
                            }
                            isPickerMode = false
                            pickerSlot = -1
                        },
                        onCancel = {
                            isPickerMode = false
                            pickerSlot = -1
                        },
                        onClearSlot = if (editingAction != null) {
                            {
                                if (pickerSlot in slotActions.indices) {
                                    saveSlotAction(pickerSlot, null)
                                }
                                isPickerMode = false
                                pickerSlot = -1
                            }
                        } else {
                            null
                        },
                    ),
                )
            } else {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { isShortcutsEditMode = !isShortcutsEditMode })
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.gesture_action_volume_panel),
                        style = MiuixTheme.textStyles.title4,
                    )
                    ExpandPanelSlidersSection()
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpandPanelShortcutsSection(
                        slotActions = slotActions,
                        activityShortcuts = activityShortcuts,
                        isEditMode = isShortcutsEditMode,
                        settings = appSettings,
                        onRun = ::runSlotAction,
                        onAssign = { index ->
                            pickerSlot = index
                            isPickerMode = true
                        },
                        onClear = { index -> saveSlotAction(index, null) },
                        // 长按槽位进/出编辑模式（"空转才借用"）：长按没被「启动策略」占用时才走这条。
                        // 编辑态里长按同样退出，否则进了编辑模式只能去长按空白才能出来（用户反馈）。
                        onToggleEditMode = {
                            isShortcutsEditMode = !isShortcutsEditMode
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandPanelFrostedSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(ExpandPanelCornerRadius)
    val density = LocalDensity.current
    val cornerPx = with(density) { ExpandPanelCornerRadius.toPx() }
    val blurRadiusPx = with(density) { 48.dp.toPx() }.roundToInt()
    val frostedStyle = expandPanelFrostedStyle()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .shadow(16.dp, shape, clip = false)
            .clip(shape)
            .border(1.dp, frostedStyle.borderColor, shape),
    ) {
        LocalFrostedGlassBackdrop(
            modifier = Modifier.matchParentSize(),
            cornerRadiusPx = cornerPx,
            blurRadiusPx = blurRadiusPx,
            tintColor = frostedStyle.tintColor,
            enabled = true,
        )
        if (frostedStyle.innerScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = frostedStyle.innerScrimAlpha)),
            )
        }
        content()
    }
}

@Composable
private fun ExpandPanelSlidersSection() {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var brightness by remember {
        mutableFloatStateOf(com.slideindex.app.overlay.panels.PanelVolumeHooks.readBrightness?.invoke() ?: 0f)
    }
    val maxAlarm = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1) }
    var alarmVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat() / maxAlarm)
    }
    val maxNotification = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1)
    }
    var notificationVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION).toFloat() / maxNotification)
    }
    val maxRing = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1) }
    var ringVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_RING).toFloat() / maxRing)
    }
    val maxMusic = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var mediaVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxMusic)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .height(188.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_brightness),
            icon = Icons.Default.LightMode,
            value = brightness,
            onValueChange = {
                brightness = it
                com.slideindex.app.overlay.panels.PanelVolumeHooks.applyBrightness?.invoke(it)
            },
        )
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_alarm),
            icon = Icons.Default.Alarm,
            value = alarmVolume,
            onValueChange = {
                alarmVolume = it
                com.slideindex.app.overlay.panels.PanelVolumeHooks.setVolume?.invoke(0, it)
            },
        )
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_notification),
            icon = Icons.Default.NotificationsActive,
            value = notificationVolume,
            onValueChange = {
                notificationVolume = it
                com.slideindex.app.overlay.panels.PanelVolumeHooks.setVolume?.invoke(2, it)
            },
        )
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_ring),
            icon = Icons.Default.Notifications,
            value = ringVolume,
            onValueChange = {
                ringVolume = it
                com.slideindex.app.overlay.panels.PanelVolumeHooks.setVolume?.invoke(1, it)
            },
        )
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_media),
            icon = Icons.Default.MusicNote,
            value = mediaVolume,
            onValueChange = {
                mediaVolume = it
                com.slideindex.app.overlay.panels.PanelVolumeHooks.setVolume?.invoke(3, it)
            },
        )
    }
}

@Composable
private fun ExpandPanelSliderItem(
    label: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    var currentValue by remember { mutableFloatStateOf(value) }
    val cornerRadius = 14.dp
    val trackColor = expandPanelSliderTrackColor()
    val fillColor = MiuixTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(148.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / size.height
                            currentValue = (currentValue + delta).coerceIn(0f, 1f)
                            onValueChange(currentValue)
                        },
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radiusPx = cornerRadius.toPx()
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                    )
                    val fillHeight = size.height * currentValue
                    if (fillHeight > 0f) {
                        drawRect(
                            color = fillColor,
                            topLeft = Offset(0f, size.height - fillHeight),
                            size = Size(size.width, fillHeight),
                        )
                    }
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .size(22.dp),
                tint = if (currentValue > 0.18f) Color.White else fillColor.copy(alpha = 0.75f),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = expandPanelSecondaryTextColor(),
        )
    }
}

@Composable
private fun ExpandPanelShortcutsSection(
    slotActions: List<GestureAction?>,
    activityShortcuts: List<ActivityShortcut>,
    isEditMode: Boolean,
    settings: AppSettings,
    onRun: (GestureAction, Boolean) -> Unit,
    onAssign: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onToggleEditMode: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.expand_panel_shortcuts_title),
            style = MiuixTheme.textStyles.subtitle,
            color = expandPanelSecondaryTextColor(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        ExpandPanelShortcutsGrid(
            slotActions = slotActions,
            activityShortcuts = activityShortcuts,
            isEditMode = isEditMode,
            settings = settings,
            onRun = onRun,
            onAssign = onAssign,
            onClear = onClear,
            onToggleEditMode = onToggleEditMode,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isEditMode) 36.dp else 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isEditMode) {
                Text(
                    text = stringResource(R.string.expand_panel_edit_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = expandPanelSecondaryTextColor(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpandPanelShortcutsGrid(
    slotActions: List<GestureAction?>,
    activityShortcuts: List<ActivityShortcut>,
    isEditMode: Boolean,
    settings: AppSettings,
    onRun: (GestureAction, Boolean) -> Unit,
    onAssign: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onToggleEditMode: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(4) { col ->
                    val index = row * 4 + col
                    val action = slotActions.getOrNull(index)
                    ExpandPanelShortcutCell(
                        modifier = Modifier.weight(1f),
                        action = action,
                        activityShortcuts = activityShortcuts,
                        isEditMode = isEditMode,
                        settings = settings,
                        onClick = {
                            if (isEditMode || action == null) {
                                onAssign(index)
                            } else {
                                onRun(action, false)
                            }
                        },
                        onLongClick = {
                            if (!isEditMode && action != null) {
                                onRun(action, true)
                            }
                        },
                        onLongPressEnterEditMode = onToggleEditMode,
                        onRemove = { onClear(index) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandPanelShortcutCell(
    action: GestureAction?,
    activityShortcuts: List<ActivityShortcut>,
    isEditMode: Boolean,
    settings: AppSettings,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** 长按没被「启动策略」占用时：进/出编辑模式（以前完全没反应，只能去戳空白）。 */
    onLongPressEnterEditMode: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actionLabel = remember(action, activityShortcuts) {
        action?.let { expandPanelSlotLabel(context, it, activityShortcuts) }
    }
    val addLabel = stringResource(R.string.expand_panel_slot_add_label)
    val cellInteraction = remember { MutableInteractionSource() }
    val removeInteraction = remember { MutableInteractionSource() }
    val displayLabel = when {
        action != null -> actionLabel.orEmpty()
        isEditMode -> addLabel
        else -> ""
    }
    // app / 快捷方式槽位：长按弹「小横条」（打开 + 编辑模式），交互与排布照抄搜索面板的 app 候选卡片；
    // 其它槽位与空槽位：长按直接进编辑模式（编辑态里则是退出）。见 ExpandPanelSlotQuickActions.kt。
    val isAppSlot = action != null &&
        (action is GestureAction.LaunchApp || action is GestureAction.LaunchShortcut)
    val quickActionStripEnabled = !isEditMode && isAppSlot
    // 「打开」这一格是按「应用启动方式」策略解析出来的形态（与原来长按直接打开等价）。
    val openFullscreen = settings.shouldLaunchFullscreen(longPressTriggered = true)
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(ExpandPanelSlotIconSize),
            contentAlignment = Alignment.Center,
        ) {
            ExpandPanelSlotQuickActionTarget(
                enabled = quickActionStripEnabled,
                openFullscreen = openFullscreen,
                settings = settings,
                onAction = { quickAction ->
                    when (quickAction) {
                        // 松手不动 = 默认落在「打开」上，与原来"长按直接打开"等价。
                        ExpandPanelSlotQuickAction.OPEN -> onLongClick()
                        ExpandPanelSlotQuickAction.EDIT_MODE -> onLongPressEnterEditMode()
                    }
                },
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (quickActionStripEnabled) {
                                // 长按由上面的横条接管，这里只处理点击。
                                Modifier.clickable(
                                    interactionSource = cellInteraction,
                                    indication = null,
                                    onClick = onClick,
                                )
                            } else {
                                // 编辑态 / 非 app 槽位 / 空槽位：长按进（编辑态里是出）编辑模式。
                                // combinedClickable 会在长按后吞掉 UP，松手不会再把槽位动作跑一遍。
                                Modifier.combinedClickable(
                                    interactionSource = cellInteraction,
                                    indication = null,
                                    onClick = onClick,
                                    onLongClick = onLongPressEnterEditMode,
                                )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (action != null) {
                        ExpandPanelSlotIcon(
                            action = action,
                            activityShortcuts = activityShortcuts,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.expand_panel_shortcut_add),
                            modifier = Modifier.size(ExpandPanelSlotIconInner),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (isEditMode && action != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.error)
                        .clickable(
                            interactionSource = removeInteraction,
                            indication = null,
                            onClick = onRemove,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.expand_panel_clear_slot),
                        modifier = Modifier.size(14.dp),
                        tint = Color.White,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ExpandPanelSlotLabelHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (displayLabel.isNotEmpty()) {
                Text(
                    text = displayLabel,
                    style = MiuixTheme.textStyles.footnote2,
                    color = expandPanelSecondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun expandPanelSlotLabel(
    context: Context,
    action: GestureAction,
    activityShortcuts: List<ActivityShortcut>,
): String = when (action) {
    is GestureAction.LaunchApp -> {
        runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(action.packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrElse { action.packageName }
    }
    is GestureAction.LaunchShortcut -> {
        activityShortcuts.findForLaunchShortcut(action.payloadKey)?.label
            ?.takeIf { it.isNotBlank() }
            ?: com.slideindex.app.overlay.panels.PanelRuntimeHooks.launchShortcutLabel
                ?.invoke(action)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.gesture_action_launch_shortcut)
    }
    else -> com.slideindex.app.overlay.panels.PanelRuntimeHooks.gestureActionLabel
        ?.invoke(context, action).orEmpty()
}

private val ExpandPanelSlotIconSize = 40.dp
private val ExpandPanelSlotIconInner = 24.dp
private val ExpandPanelSlotLabelHeight = 18.dp

@Composable
private fun ExpandPanelSlotIcon(
    action: GestureAction,
    activityShortcuts: List<ActivityShortcut>,
) {
    val context = LocalContext.current
    when (action) {
        is GestureAction.LaunchApp -> {
            // 走统一的 leading：分身条目右上角黄点（主实例原样）。
            Md3PickerAppLeading(
                com.slideindex.app.data.AppInfo(
                    packageName = action.packageName,
                    label = action.packageName,
                    letter = '#',
                    pinyinKey = action.packageName,
                    cloneUserId = action.cloneUserId,
                ),
            )
        }
        is GestureAction.LaunchShortcut -> {
            Md3PickerLaunchShortcutLeading(
                action = action,
                activityShortcuts = activityShortcuts,
                selected = false,
            )
        }
        else -> {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = false,
                contentDescription = com.slideindex.app.overlay.panels.PanelRuntimeHooks.gestureActionLabel?.invoke(context, action),
            )
        }
    }
}
