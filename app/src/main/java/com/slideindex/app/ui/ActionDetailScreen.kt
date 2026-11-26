package com.slideindex.app.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.external.AppLinks
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.overlay.MagnifierPrefs
import com.slideindex.app.overlay.floatnav.FloatNavFormSetting
import com.slideindex.app.overlay.floatnav.FloatNavPrefs
import com.slideindex.app.screendraw.ScreenDrawPrefs
import kotlin.math.round
import com.slideindex.app.gesture.KeyEventPresets
import com.slideindex.app.pagescroll.PageScrollPrefs
import com.slideindex.app.pagescroll.PageScrollSettings
import com.slideindex.app.settings.AppCarouselSwitcherSettings
import com.slideindex.app.settings.TaskSwitcherCloseMode
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 动作详情页：基本信息（描述 / 深链 / 参数 / 立即执行）+ 设置（打开配置或提示）。
 *
 * 与旧分支保持同一张「基本信息」卡；区别是深链换成真实格式，内容型动作的参数放在
 * 同一张卡里，「立即执行」用表单值而不是空动作。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionDetailScreen(
    action: GestureAction,
    entry: BuiltInActionEntry,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onTest: ((GestureAction) -> Unit)?,
    taskSwitcherWidthDp: Int? = null,
    onTaskSwitcherWidthChange: ((Int) -> Unit)? = null,
    taskSwitcherCloseMode: TaskSwitcherCloseMode? = null,
    onTaskSwitcherCloseModeChange: ((TaskSwitcherCloseMode) -> Unit)? = null,
    appCarouselSwitcher: AppCarouselSwitcherSettings? = null,
    onAppCarouselSwitcherChange: ((AppCarouselSwitcherSettings) -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val label = gestureActionLabel(action)
    val description = gestureActionDescription(action)
    val permissionHint = gestureActionPermissionHint(
        action,
        context,
        appCarouselSwitcher?.listSource,
    )
    val specialHint = entry.special?.let { stringResource(it.hintRes) }
        ?: when (entry.type) {
            GestureActionType.FINGERTIP_RING -> stringResource(R.string.action_center_special_fingertip)
            GestureActionType.REGIONAL_SCREENSHOT_PICK ->
                stringResource(R.string.action_center_special_regional)
            else -> null
        }
    val infoTitle = stringResource(R.string.action_center_info)
    val introTitle = stringResource(R.string.action_center_intro)
    val settingsTitle = stringResource(R.string.action_center_settings)
    val openSettingsTitle = stringResource(R.string.action_center_open_settings)
    val settingsDesc = stringResource(R.string.action_center_settings_desc)
    val deeplinkTitle = stringResource(R.string.action_center_deeplink)
    val runLabel = stringResource(R.string.action_center_test)
    val formNote = stringResource(R.string.action_center_test_note)
    val deeplink = AppLinks.actionUriString(action.type.name)
    val openSettings = onOpenSettings

    var url by rememberSaveable(action.type) { mutableStateOf("") }
    var linkLabel by rememberSaveable(action.type) { mutableStateOf("") }
    var command by rememberSaveable(action.type) { mutableStateOf("") }
    var keyCodeText by rememberSaveable(action.type) { mutableStateOf("82") }
    var isLongPress by rememberSaveable(action.type) { mutableStateOf(false) }
    var remindMinutes by rememberSaveable(action.type) { mutableIntStateOf(5) }
    var pageScroll by remember(context) { mutableStateOf(PageScrollPrefs.read(context)) }
    var screenDrawDefaultTool by remember(context) {
        mutableStateOf(com.slideindex.app.screendraw.ScreenDrawPrefs.readDefaultTool(context))
    }
    var screenDrawInitialOrientation by remember(context) {
        mutableStateOf(ScreenDrawPrefs.readInitialOrientation(context))
    }
    var floatNavInitialForm by remember(context) {
        mutableStateOf(FloatNavPrefs.readInitialForm(context))
    }
    var magnifierFullscreenScale by remember(context) {
        mutableFloatStateOf(MagnifierPrefs.readFullscreenScale(context))
    }
    var magnifierWindowScale by remember(context) {
        mutableFloatStateOf(MagnifierPrefs.readWindowScale(context))
    }
    val taskWidth = taskSwitcherWidthDp ?: 226

    val keyCode = keyCodeText.toIntOrNull()
    val builtAction: GestureAction? = when (entry.testKind) {
        ActionTestKind.OPEN_LINK -> {
            val trimmed = url.trim()
            if (trimmed.isBlank()) null else GestureAction.OpenLink(trimmed, linkLabel.trim())
        }
        ActionTestKind.SHELL -> {
            if (command.isBlank()) null else GestureAction.ExecuteShellCommand(command)
        }
        ActionTestKind.KEY -> {
            keyCode?.takeIf { it > 0 }?.let { code ->
                GestureAction.SimulateKeyEvent(
                    keyCode = code,
                    keyName = KeyEventPresets.getDisplayName(context, code),
                    isLongPress = isLongPress,
                )
            }
        }
        ActionTestKind.REMIND -> when (remindMinutes) {
            1 -> GestureAction.Remind1m
            3 -> GestureAction.Remind3m
            5 -> GestureAction.Remind5m
            10 -> GestureAction.Remind10m
            15 -> GestureAction.Remind15m
            else -> GestureAction.Remind
        }
        ActionTestKind.DIRECT -> entry.sample
        ActionTestKind.DISABLED -> null
    }
    val runEnabled = onTest != null &&
        entry.testKind != ActionTestKind.DISABLED &&
        builtAction != null
    val disabledReason = when {
        entry.testKind == ActionTestKind.DISABLED ->
            stringResource(entry.testDisabledReasonRes ?: R.string.action_center_test_unavailable)
        onTest == null -> stringResource(R.string.action_center_test_unavailable)
        else -> null
    }
    val settingsHint = when {
        entry.testKind == ActionTestKind.DISABLED ->
            stringResource(entry.testDisabledReasonRes ?: R.string.action_center_no_config)
        entry.needsParams -> stringResource(R.string.action_center_config_on_bind)
        else -> stringResource(R.string.action_center_no_config)
    }
    val showFormNote = entry.testKind == ActionTestKind.OPEN_LINK ||
        entry.testKind == ActionTestKind.SHELL ||
        entry.testKind == ActionTestKind.KEY ||
        entry.testKind == ActionTestKind.REMIND

    SettingsLazyScreenScaffold(
        title = label,
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "action-detail-basic",
            title = infoTitle,
        )
        groupedCardItems(
            keyPrefix = "action-detail-basic",
            items = listOf(
                settingsCardScopeItem("info") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        if (!description.isNullOrBlank()) {
                            Text(
                                text = introTitle,
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = description,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        if (specialHint != null) {
                            if (!description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = specialHint,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        if (permissionHint != null) {
                            if (specialHint != null || !description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = permissionHint,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = deeplinkTitle,
                                    style = MiuixTheme.textStyles.title4,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = deeplink,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(deeplink))
                                    Toast.makeText(
                                        context,
                                        R.string.action_center_deeplink_copied,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Medium.Copy,
                                    contentDescription = deeplinkTitle,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        when (entry.testKind) {
                            ActionTestKind.OPEN_LINK -> {
                                MiuixLabeledTextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    label = stringResource(R.string.gesture_open_link_url_field),
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                MiuixLabeledTextField(
                                    value = linkLabel,
                                    onValueChange = { linkLabel = it },
                                    label = stringResource(R.string.gesture_open_link_label_field),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            ActionTestKind.SHELL -> {
                                MiuixLabeledTextField(
                                    value = command,
                                    onValueChange = { command = it },
                                    label = stringResource(R.string.action_center_command),
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            ActionTestKind.KEY -> {
                                MiuixLabeledTextField(
                                    value = keyCodeText,
                                    onValueChange = { input ->
                                        keyCodeText = input.filter { it.isDigit() }
                                    },
                                    label = stringResource(R.string.key_event_code_label),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.key_event_presets_title),
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    KeyEventPresets.presets.forEach { preset ->
                                        RemindChip(
                                            label = preset.label(context),
                                            selected = keyCode == preset.keyCode,
                                            onClick = { keyCodeText = preset.keyCode.toString() },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.key_event_long_press_title),
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = isLongPress,
                                        onCheckedChange = { isLongPress = it },
                                    )
                                }
                            }
                            ActionTestKind.REMIND -> {
                                Text(
                                    text = stringResource(R.string.action_center_remind_duration),
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(1, 3, 5, 10, 15).forEach { minutes ->
                                        RemindChip(
                                            label = stringResource(
                                                R.string.action_center_remind_minutes,
                                                minutes,
                                            ),
                                            selected = remindMinutes == minutes,
                                            onClick = { remindMinutes = minutes },
                                        )
                                    }
                                    RemindChip(
                                        label = stringResource(R.string.action_center_remind_custom),
                                        selected = remindMinutes == 0,
                                        onClick = { remindMinutes = 0 },
                                    )
                                }
                            }
                            else -> Unit
                        }

                        if (showFormNote) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = formNote,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        if (disabledReason != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = disabledReason,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        if (showFormNote || disabledReason != null) {
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        Button(
                            onClick = { builtAction?.let { onTest?.invoke(it) } },
                            enabled = runEnabled,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = runLabel,
                                style = MiuixTheme.textStyles.button,
                            )
                        }
                    }
                },
            ),
        )

        settingsLazySmallTitle(
            key = "action-detail-settings",
            title = settingsTitle,
        )
        groupedCardItems(
            keyPrefix = "action-detail-settings",
            items = buildList {
                when {
                    entry.type == GestureActionType.TASK_SWITCHER -> {
                        add(
                            settingsCardScopeItem("task-switcher-width") {
                                SettingsSliderRow(
                                    title = stringResource(R.string.task_switcher_panel_width),
                                    summary = stringResource(R.string.task_switcher_panel_width_desc),
                                    value = taskWidth.toFloat(),
                                    valueRange = 180f..340f,
                                    steps = 0,
                                    enabled = onTaskSwitcherWidthChange != null,
                                    label = stringResource(
                                        R.string.task_switcher_panel_width_value,
                                        taskWidth,
                                    ),
                                    commitOnFinish = true,
                                    onValueChange = { onTaskSwitcherWidthChange?.invoke(it.roundToInt()) },
                                )
                            },
                        )
                        add(
                            settingsCardScopeItem("task-switcher-close-mode") {
                                val closeModes = listOf(
                                    TaskSwitcherCloseMode.DISMISS_RECENTS,
                                    TaskSwitcherCloseMode.FORCE_STOP,
                                    TaskSwitcherCloseMode.BOTH,
                                )
                                val options = listOf(
                                    stringResource(R.string.task_switcher_close_mode_dismiss_recents),
                                    stringResource(R.string.task_switcher_close_mode_force_stop),
                                    stringResource(R.string.task_switcher_close_mode_both),
                                )
                                val currentMode = taskSwitcherCloseMode ?: TaskSwitcherCloseMode.DISMISS_RECENTS
                                val selectedIndex = closeModes.indexOf(currentMode).coerceAtLeast(0)
                                SettingDropdownRow(
                                    title = stringResource(R.string.task_switcher_close_mode_title),
                                    subtitle = stringResource(R.string.task_switcher_close_mode_desc),
                                    items = options,
                                    selectedIndex = selectedIndex,
                                    enabled = onTaskSwitcherCloseModeChange != null,
                                    onSelectedIndexChange = { index ->
                                        closeModes.getOrNull(index)?.let {
                                            onTaskSwitcherCloseModeChange?.invoke(it)
                                        }
                                    },
                                )
                            },
                        )
                    }
                    entry.type == GestureActionType.PAGE_SCROLL -> {
                        add(
                            settingsCardScopeItem("page-scroll-speed") {
                                SettingsSliderRow(
                                    title = stringResource(R.string.page_scroll_speed_title),
                                    value = pageScroll.speedLevel.toFloat(),
                                    valueRange = PageScrollSettings.MIN_SPEED_LEVEL.toFloat()..
                                        PageScrollSettings.MAX_SPEED_LEVEL.toFloat(),
                                    steps = PageScrollSettings.MAX_SPEED_LEVEL -
                                        PageScrollSettings.MIN_SPEED_LEVEL - 1,
                                    enabled = true,
                                    label = stringResource(
                                        R.string.page_scroll_speed_value,
                                        pageScroll.speedLevel,
                                        PageScrollSettings.MAX_SPEED_LEVEL,
                                    ),
                                    commitOnFinish = true,
                                    onValueChange = { value ->
                                        val level = value.roundToInt().coerceIn(
                                            PageScrollSettings.MIN_SPEED_LEVEL,
                                            PageScrollSettings.MAX_SPEED_LEVEL,
                                        )
                                        if (level != pageScroll.speedLevel) {
                                            pageScroll = pageScroll.copy(speedLevel = level)
                                            PageScrollPrefs.saveSpeedLevel(context, level)
                                        }
                                    },
                                )
                            },
                        )
                        add(
                            settingsCardScopeItem("page-scroll-alpha") {
                                SettingsSliderRow(
                                    title = stringResource(R.string.page_scroll_bar_idle_alpha_title),
                                    summary = stringResource(R.string.page_scroll_bar_idle_alpha_summary),
                                    value = pageScroll.controlBarIdleAlpha,
                                    valueRange = PageScrollSettings.MIN_CONTROL_BAR_IDLE_ALPHA..
                                        PageScrollSettings.MAX_CONTROL_BAR_IDLE_ALPHA,
                                    enabled = true,
                                    label = idleAlphaPercentLabel(pageScroll.controlBarIdleAlpha),
                                    formatLabel = ::idleAlphaPercentLabel,
                                    keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                                    commitOnFinish = true,
                                    onValueChange = { alpha ->
                                        val value = alpha.coerceIn(
                                            PageScrollSettings.MIN_CONTROL_BAR_IDLE_ALPHA,
                                            PageScrollSettings.MAX_CONTROL_BAR_IDLE_ALPHA,
                                        )
                                        if (value != pageScroll.controlBarIdleAlpha) {
                                            pageScroll = pageScroll.copy(controlBarIdleAlpha = value)
                                            PageScrollPrefs.saveControlBarIdleAlpha(context, value)
                                        }
                                    },
                                )
                            },
                        )
                    }
                    entry.type == GestureActionType.MAGNIFIER -> {
                        add(
                            settingsCardScopeItem("magnifier-fullscreen-scale") {
                                SettingsSliderRow(
                                    title = stringResource(R.string.magnifier_fullscreen_scale_title),
                                    summary = stringResource(R.string.magnifier_fullscreen_scale_summary),
                                    value = magnifierFullscreenScale,
                                    valueRange = MagnifierPrefs.MIN_SCALE..MagnifierPrefs.MAX_SCALE,
                                    steps = 14,
                                    enabled = true,
                                    label = String.format(LocalConfiguration.current.locales[0], "%.2f×", magnifierFullscreenScale),
                                    commitOnFinish = true,
                                    onValueChange = { raw ->
                                        val step = 0.25f
                                        val rounded = (round(raw / step) * step)
                                            .coerceIn(MagnifierPrefs.MIN_SCALE, MagnifierPrefs.MAX_SCALE)
                                        if (rounded != magnifierFullscreenScale) {
                                            magnifierFullscreenScale = rounded
                                            MagnifierPrefs.writeFullscreenScale(context, rounded)
                                        }
                                    },
                                )
                            },
                        )
                        add(
                            settingsCardScopeItem("magnifier-window-scale") {
                                SettingsSliderRow(
                                    title = stringResource(R.string.magnifier_window_scale_title),
                                    summary = stringResource(R.string.magnifier_window_scale_summary),
                                    value = magnifierWindowScale,
                                    valueRange = MagnifierPrefs.MIN_SCALE..MagnifierPrefs.MAX_SCALE,
                                    steps = 14,
                                    enabled = true,
                                    label = String.format(LocalConfiguration.current.locales[0], "%.2f×", magnifierWindowScale),
                                    commitOnFinish = true,
                                    onValueChange = { raw ->
                                        val step = 0.25f
                                        val rounded = (round(raw / step) * step)
                                            .coerceIn(MagnifierPrefs.MIN_SCALE, MagnifierPrefs.MAX_SCALE)
                                        if (rounded != magnifierWindowScale) {
                                            magnifierWindowScale = rounded
                                            MagnifierPrefs.writeWindowScale(context, rounded)
                                        }
                                    },
                                )
                            },
                        )
                    }
                    entry.type == GestureActionType.APP_CAROUSEL_SWITCHER &&
                        appCarouselSwitcher != null &&
                        onAppCarouselSwitcherChange != null -> addAll(
                        appCarouselSwitcherSettingsCardItems(
                            keyPrefix = "action-center-app-carousel",
                            local = appCarouselSwitcher,
                            onUpdate = onAppCarouselSwitcherChange,
                        ),
                    )
                    entry.type == GestureActionType.FLOAT_NAV -> add(
                        settingsCardScopeItem("float-nav-initial-form") {
                            InitialFormSettingContent(
                                title = stringResource(R.string.action_initial_form_title),
                                summary = stringResource(R.string.float_nav_initial_form_summary),
                                items = listOf(
                                    FloatNavFormSetting.HORIZONTAL,
                                    FloatNavFormSetting.VERTICAL,
                                    FloatNavFormSetting.MEMORY,
                                ),
                                selected = floatNavInitialForm,
                                labelRes = { value ->
                                    when (value) {
                                        FloatNavFormSetting.HORIZONTAL -> R.string.action_form_horizontal
                                        FloatNavFormSetting.VERTICAL -> R.string.action_form_vertical
                                        FloatNavFormSetting.MEMORY -> R.string.action_form_memory
                                    }
                                },
                                onSelect = { value ->
                                    floatNavInitialForm = value
                                    FloatNavPrefs.writeInitialForm(context, value)
                                },
                            )
                        },
                    )
                    entry.type == GestureActionType.SCREEN_DRAW -> {
                        add(
                            settingsCardScopeItem("screen-draw-default-tool") {
                                val toolValues = listOf(
                                    com.slideindex.app.screendraw.ScreenDrawTool.PEN.name,
                                    com.slideindex.app.screendraw.ScreenDrawTool.SHAPE.name,
                                    com.slideindex.app.screendraw.ScreenDrawTool.TEXT.name,
                                    com.slideindex.app.screendraw.ScreenDrawTool.MOSAIC.name,
                                    com.slideindex.app.screendraw.ScreenDrawTool.NUMBER.name,
                                    com.slideindex.app.screendraw.ScreenDrawTool.BOX_SELECT.name,
                                    com.slideindex.app.screendraw.ScreenDrawPrefs.MEMORY,
                                )
                                val toolLabels = listOf(
                                    stringResource(R.string.inspire_image_edit_mode_doodle),
                                    stringResource(R.string.inspire_image_edit_mode_shape),
                                    stringResource(R.string.inspire_image_edit_mode_text),
                                    stringResource(R.string.inspire_image_edit_mode_mosaic),
                                    stringResource(R.string.inspire_image_edit_mode_number),
                                    stringResource(R.string.inspire_image_edit_mode_crop),
                                    stringResource(R.string.screen_draw_tool_memory),
                                )
                                val toolDescs = listOf(
                                    stringResource(R.string.screen_draw_tool_desc_doodle),
                                    stringResource(R.string.screen_draw_tool_desc_shape),
                                    stringResource(R.string.screen_draw_tool_desc_text),
                                    stringResource(R.string.screen_draw_tool_desc_mosaic),
                                    stringResource(R.string.screen_draw_tool_desc_number),
                                    stringResource(R.string.screen_draw_tool_desc_crop),
                                    stringResource(R.string.screen_draw_tool_desc_memory),
                                )
                                val selectedIndex = toolValues.indexOf(screenDrawDefaultTool)
                                    .coerceIn(0, toolValues.lastIndex)
                                SettingDropdownRow(
                                    title = stringResource(R.string.screen_draw_default_tool_title),
                                    subtitle = toolDescs[selectedIndex],
                                    items = toolLabels,
                                    selectedIndex = selectedIndex,
                                    enabled = true,
                                    onSelectedIndexChange = { index ->
                                        toolValues.getOrNull(index)?.let { value ->
                                            screenDrawDefaultTool = value
                                            com.slideindex.app.screendraw.ScreenDrawPrefs
                                                .writeDefaultTool(context, value)
                                        }
                                    },
                                )
                            },
                        )
                        add(
                            settingsCardScopeItem("screen-draw-initial-form") {
                                InitialFormSettingContent(
                                    title = stringResource(R.string.action_initial_form_title),
                                    summary = stringResource(R.string.screen_draw_initial_form_summary),
                                    items = listOf(
                                        ScreenDrawPrefs.InitialOrientation.HORIZONTAL,
                                        ScreenDrawPrefs.InitialOrientation.VERTICAL,
                                        ScreenDrawPrefs.InitialOrientation.MEMORY,
                                    ),
                                    selected = screenDrawInitialOrientation,
                                    labelRes = { value ->
                                        when (value) {
                                            ScreenDrawPrefs.InitialOrientation.HORIZONTAL ->
                                                R.string.action_form_horizontal
                                            ScreenDrawPrefs.InitialOrientation.VERTICAL ->
                                                R.string.action_form_vertical
                                            ScreenDrawPrefs.InitialOrientation.MEMORY ->
                                                R.string.action_form_memory
                                        }
                                    },
                                    onSelect = { value ->
                                        screenDrawInitialOrientation = value
                                        ScreenDrawPrefs.writeInitialOrientation(context, value)
                                    },
                                )
                            },
                        )
                    }
                    openSettings != null -> add(
                        settingsCardScopeItem("settings") {
                            SettingNavigationRow(
                                icon = { contentDescription ->
                                    Icon(
                                        imageVector = gestureActionTypeOutlinedIcon(entry.type),
                                        contentDescription = contentDescription,
                                    )
                                },
                                title = openSettingsTitle,
                                subtitle = settingsDesc,
                                onClick = openSettings,
                            )
                        },
                    )
                    else -> add(
                        settingsCardScopeItem("settings") {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = settingsHint,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun <T> InitialFormSettingContent(
    title: String,
    summary: String,
    items: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
        Text(
            text = summary,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        SegmentedControl(
            items = items,
            selected = selected,
            labelRes = labelRes,
            onSelect = onSelect,
            containerColor = MiuixTheme.colorScheme.surface,
            indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
        )
    }
}

@Composable
private fun RemindChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSurfaceContainer
            },
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
        )
    }
}

private fun idleAlphaPercentLabel(alpha: Float): String =
    "${(alpha.coerceIn(0f, 1f) * 100f).roundToInt()}%"

@get:StringRes
private val ActionSpecialMark.hintRes: Int
    get() = when (this) {
        ActionSpecialMark.POINTER -> R.string.action_center_special_pointer
        ActionSpecialMark.CORNER -> R.string.action_center_special_corner
        ActionSpecialMark.SINGLE_TAP -> R.string.action_center_special_single_tap
        ActionSpecialMark.SHAKE -> R.string.action_center_special_shake
    }
