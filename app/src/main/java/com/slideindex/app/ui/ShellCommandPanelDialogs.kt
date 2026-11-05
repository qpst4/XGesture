package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon as Material3Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellExecutionMode
import com.slideindex.app.shell.ShellResultDisplay
import com.slideindex.app.shell.normalizeShellCommand
import com.slideindex.app.shell.resolveShellCommandLabel
import com.slideindex.app.util.ShellResultFormatter
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.MiuixIcons

private data class ShellTestResultState(
    val exitCode: Int,
    val output: String,
)

private fun copyShellOutputWithToast(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", text))
    Toast.makeText(context, R.string.shell_panel_copied, Toast.LENGTH_SHORT).show()
}

private fun buildShellCommandDraft(
    context: Context,
    initial: ShellCommand?,
    label: String,
    command: String,
    iconDraft: ShellCommandIconDraft,
    executionMode: ShellExecutionMode,
    resultDisplay: ShellResultDisplay,
): ShellCommand {
    val finalizedIcon = finalizeShellCommandIconDraft(context, initial, iconDraft)
    return applyIconDraft(
        ShellCommand(
            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
            label = resolveShellCommandLabel(label, command),
            command = normalizeShellCommand(command),
            executionMode = executionMode,
            resultDisplay = resultDisplay,
        ),
        finalizedIcon,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShellInlineTestResultCard(
    exitCode: Int,
    output: String,
    onCopyOutput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (exitCode == 0) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onCopyOutput,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.shell_panel_exit_code, exitCode),
                style = MaterialTheme.typography.labelLarge,
                color = if (exitCode == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = output.ifBlank { stringResource(R.string.shell_panel_no_output) },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun ShellSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.dividerLine,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(3.dp),
    ) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val dividerTotal = if (labels.isEmpty()) 0.dp else (labels.size - 1).dp
        val segmentWidth = if (labels.isEmpty()) 0.dp else (maxWidth - dividerTotal) / labels.size
        val indicatorOffset = remember { Animatable(0f) }
        var firstLayout by remember { mutableStateOf(true) }

        LaunchedEffect(selectedIndex, segmentWidth) {
            val target = with(density) { (segmentWidth + 1.dp).toPx() } * selectedIndex
            if (firstLayout) {
                indicatorOffset.snapTo(target)
                firstLayout = false
            } else {
                indicatorOffset.animateTo(
                    targetValue = target,
                    animationSpec = tween(200, easing = LinearEasing),
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorOffset.value.roundToInt(), 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .background(MiuixTheme.colorScheme.surface),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MiuixTheme.colorScheme.dividerLine),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            if (index != selectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = label,
                        style = MiuixTheme.textStyles.body2,
                        color = if (index == selectedIndex) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellExecutionModeSelector(
    executionMode: ShellExecutionMode,
    onExecutionModeChange: (ShellExecutionMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.shell_panel_execution_mode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShellSegmentedControl(
            labels = listOf(
                stringResource(R.string.shell_panel_mode_root),
                stringResource(R.string.shell_panel_mode_shizuku),
            ),
            selectedIndex = if (executionMode == ShellExecutionMode.ROOT) 0 else 1,
            onSelect = { index ->
                onExecutionModeChange(
                    if (index == 0) ShellExecutionMode.ROOT else ShellExecutionMode.SHIZUKU,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellCommandEditorActionsRow(
    canSave: Boolean,
    canTest: Boolean,
    testing: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onTest: (() -> Unit)? = null,
    saveAsConfirm: Boolean = false,
) {
    val saveLabel = if (saveAsConfirm) {
        stringResource(R.string.confirm)
    } else {
        stringResource(R.string.shell_panel_save)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MiuixIcons.Medium.Delete,
                    contentDescription = stringResource(R.string.shell_panel_delete),
                    tint = MiuixTheme.colorScheme.error,
                )
            }
        }
        if (onTest != null) {
            if (testing) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                }
            } else {
                MiuixTextButton(
                    text = stringResource(R.string.shell_panel_test),
                    onClick = onTest,
                    enabled = canTest,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        MiuixTextButton(
            text = saveLabel,
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun ShellCommandEditorFields(
    command: String,
    onCommandChange: (String) -> Unit,
    resultDisplay: ShellResultDisplay,
    onResultDisplayChange: (ShellResultDisplay) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiuixLabeledTextField(
            value = command,
            onValueChange = onCommandChange,
            label = stringResource(R.string.shell_panel_command_field),
            useLabelAsPlaceholder = false,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
        )
        MiuixHintText(
            text = stringResource(R.string.shell_panel_edit_hint),
            horizontalPadding = 0.dp,
        )
        Text(
            text = stringResource(R.string.shell_panel_result_display),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShellSegmentedControl(
            labels = listOf(
                stringResource(R.string.shell_panel_result_none),
                stringResource(R.string.shell_panel_result_toast),
                stringResource(R.string.shell_panel_result_dialog),
            ),
            selectedIndex = when (resultDisplay) {
                ShellResultDisplay.NONE -> 0
                ShellResultDisplay.TOAST -> 1
                ShellResultDisplay.DIALOG -> 2
            },
            onSelect = { index ->
                onResultDisplayChange(
                    when (index) {
                        0 -> ShellResultDisplay.NONE
                        1 -> ShellResultDisplay.TOAST
                        else -> ShellResultDisplay.DIALOG
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandEditorScreen(
    initial: ShellCommand?,
    shizukuGranted: Boolean,
    onBack: () -> Unit,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)? = null,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)? = null,
) {
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var command by remember(initial) { mutableStateOf(initial?.command.orEmpty()) }
    var executionMode by remember(initial) {
        mutableStateOf(
            initial?.executionMode ?: if (PrivilegeGateway.isRootMode()) {
                ShellExecutionMode.ROOT
            } else {
                ShellExecutionMode.SHIZUKU
            },
        )
    }
    var resultDisplay by remember(initial) {
        mutableStateOf(initial?.resultDisplay ?: ShellResultDisplay.DIALOG)
    }
    var iconDraft by remember(initial) { mutableStateOf(shellCommandIconDraftFrom(initial)) }
    var pickingAppIcon by remember(initial?.id) { mutableStateOf(false) }
    var isSavingAppIcon by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ShellTestResultState?>(null) }
    var testDialogResult by remember { mutableStateOf<ShellTestResultState?>(null) }
    val canSave = command.isNotBlank()
    val canTest = canSave && !testing
    val context = LocalContext.current

    BackHandler(enabled = pickingAppIcon) { pickingAppIcon = false }

    if (pickingAppIcon) {
        ShellCommandAppIconPickerScreen(
            initial = initial,
            iconDraft = iconDraft,
            isSavingAppIcon = isSavingAppIcon,
            onBack = { pickingAppIcon = false },
            onIconDraftChange = { iconDraft = it },
            onSavingChange = { isSavingAppIcon = it },
        )
        return
    }

    fun buildDraft(): ShellCommand = buildShellCommandDraft(
        context = context,
        initial = initial,
        label = label,
        command = command,
        iconDraft = iconDraft,
        executionMode = executionMode,
        resultDisplay = resultDisplay,
    )

    val title = if (initial == null) {
        stringResource(R.string.shell_panel_add)
    } else {
        stringResource(R.string.shell_panel_edit)
    }

    SettingsScreenScaffold(
        title = title,
        onBack = onBack,
    ) {
        LazySettingsItem(key = "shell-editor-body") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShellExecutionModeSelector(
                    executionMode = executionMode,
                    onExecutionModeChange = {
                        executionMode = it
                        testResult = null
                    },
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SmallTitle(
                            text = stringResource(R.string.shell_panel_icon_and_name),
                            insideMargin = PaddingValues(bottom = 0.dp),
                        )
                        ShellCommandEditorIconSection(
                            label = label,
                            onLabelChange = {
                                label = it
                                testResult = null
                            },
                            initial = initial,
                            iconDraft = iconDraft,
                            onIconDraftChange = { iconDraft = it },
                            onPickAppIcon = { pickingAppIcon = true },
                            isSavingAppIcon = isSavingAppIcon,
                        )
                        ShellCommandEditorFields(
                            command = command,
                            onCommandChange = {
                                command = it
                                testResult = null
                            },
                            resultDisplay = resultDisplay,
                            onResultDisplayChange = {
                                resultDisplay = it
                                testResult = null
                            },
                        )
                    }
                }
                testResult?.let { result ->
                    ShellInlineTestResultCard(
                        exitCode = result.exitCode,
                        output = result.output,
                        onCopyOutput = { copyShellOutputWithToast(context, result.output) },
                    )
                }
                testDialogResult?.let { result ->
                    MiuixFormDialog(
                        show = true,
                        onDismissRequest = { testDialogResult = null },
                        title = stringResource(R.string.shell_panel_test_result),
                        confirmText = stringResource(R.string.confirm),
                        onConfirm = { testDialogResult = null },
                        dismissText = null,
                    ) {
                        ShellInlineTestResultCard(
                            exitCode = result.exitCode,
                            output = result.output,
                            onCopyOutput = { copyShellOutputWithToast(context, result.output) },
                        )
                    }
                }
                ShellCommandEditorActionsRow(
                    canSave = canSave,
                    canTest = canTest,
                    testing = testing,
                    onCancel = onBack,
                    onSave = { onSave(buildDraft()) },
                    onDelete = onDelete,
                    onTest = onTest?.let { test ->
                        {
                            val draft = buildDraft()
                            testing = true
                            testResult = null
                            test.invoke(draft) { exitCode, output ->
                                testing = false
                                testResult = ShellTestResultState(exitCode, output)
                                when (draft.resultDisplay) {
                                    ShellResultDisplay.TOAST -> {
                                        Toast.makeText(
                                            context,
                                            ShellResultFormatter.toast(context, exitCode, output),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    ShellResultDisplay.DIALOG -> {
                                        testDialogResult = ShellTestResultState(exitCode, output)
                                    }
                                    ShellResultDisplay.NONE -> Unit
                                }
                            }
                        }
                    },
                    saveAsConfirm = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellResultScreen(
    label: String,
    command: String,
    exitCode: Int,
    output: String,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    executedAtLabel: String? = null,
) {
    val outputText = output.ifBlank { stringResource(R.string.shell_panel_no_output) }
    val exitSucceeded = exitCode == 0
    val scrollState = rememberScrollState()

    SettingsScreenScaffold(
        title = label,
        subtitle = command,
        onBack = onBack,
        scrollContent = false,
        actions = {
            IconButton(onClick = onCopy) {
                Material3Icon(
                    imageVector = MiuixIcons.Medium.Copy,
                    contentDescription = stringResource(R.string.shell_panel_copy),
                )
            }
        },
    ) {
        LazySettingsItem(key = "shell-result-card") {
        CardSegment(isFirst = true, isLast = true) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                    if (executedAtLabel != null) {
                        Text(
                            text = executedAtLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.shell_panel_result_section_output),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (exitSucceeded) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.shell_panel_history_exit_code, exitCode),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (exitSucceeded) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 480.dp)
                            .verticalScroll(scrollState),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = outputText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
        LazySettingsItem(key = "shell-result-actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixTextButton(
                    text = stringResource(R.string.shell_panel_close),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                MiuixTextButton(
                    text = stringResource(R.string.shell_panel_copy),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandResultOverlaySheet(
    label: String,
    command: String,
    exitCode: Int,
    output: String,
    onDismissComplete: () -> Unit,
    onCopy: () -> Unit,
    onWindowReady: (() -> Unit)? = null,
    registerBackHandler: ((() -> Unit) -> Unit)? = null,
) {
    OverlayAnimatedDialogContent(
        onDismissComplete = onDismissComplete,
        onWindowReady = onWindowReady,
        registerBackHandler = registerBackHandler,
    ) { requestDismiss ->
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiuixText(
                    text = label,
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                MiuixText(
                    text = command,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                MiuixText(
                    text = stringResource(R.string.shell_panel_exit_code, exitCode),
                    style = MiuixTheme.textStyles.subtitle,
                    color = if (exitCode == 0) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.error
                    },
                )
                val scrollState = rememberScrollState()
                MiuixText(
                    text = output.ifBlank { stringResource(R.string.shell_panel_no_output) },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.shell_panel_copy),
                        onClick = onCopy,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.shell_panel_close),
                        onClick = requestDismiss,
                    )
                }
            }
        }
    }
}

@Composable
fun ShellCommandEditorDialog(
    initial: ShellCommand?,
    onDismiss: () -> Unit,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)? = null,
    shizukuGranted: Boolean = true,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)? = null,
) {
    ShellCommandEditorScreen(
        initial = initial,
        shizukuGranted = shizukuGranted,
        onBack = onDismiss,
        onSave = onSave,
        onDelete = onDelete,
        onTest = onTest,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandEditorOverlaySheet(
    onDismissComplete: () -> Unit,
    initial: ShellCommand?,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)? = null,
    shizukuGranted: Boolean = true,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)? = null,
    onWindowReady: (() -> Unit)? = null,
    registerBackHandler: ((() -> Unit) -> Unit)? = null,
) {
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var command by remember(initial) { mutableStateOf(initial?.command.orEmpty()) }
    var executionMode by remember(initial) {
        mutableStateOf(
            initial?.executionMode ?: if (PrivilegeGateway.isRootMode()) {
                ShellExecutionMode.ROOT
            } else {
                ShellExecutionMode.SHIZUKU
            },
        )
    }
    var resultDisplay by remember(initial) {
        mutableStateOf(initial?.resultDisplay ?: ShellResultDisplay.DIALOG)
    }
    var iconDraft by remember(initial) { mutableStateOf(shellCommandIconDraftFrom(initial)) }
    var pickingAppIcon by remember(initial?.id) { mutableStateOf(false) }
    var isSavingAppIcon by remember { mutableStateOf(false) }

    OverlayAnimatedDialogContent(
        onDismissComplete = onDismissComplete,
        onWindowReady = onWindowReady,
        registerBackHandler = registerBackHandler,
    ) { requestDismiss ->
        if (pickingAppIcon) {
            Box(modifier = Modifier.fillMaxSize()) {
                ShellCommandAppIconPickerScreen(
                    initial = initial,
                    iconDraft = iconDraft,
                    isSavingAppIcon = isSavingAppIcon,
                    onBack = { pickingAppIcon = false },
                    onIconDraftChange = { iconDraft = it },
                    onSavingChange = { isSavingAppIcon = it },
                )
            }
            return@OverlayAnimatedDialogContent
        }
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            ShellCommandEditorOverlayBody(
                initial = initial,
                shizukuGranted = shizukuGranted,
                label = label,
                onLabelChange = { label = it },
                command = command,
                onCommandChange = { command = it },
                executionMode = executionMode,
                onExecutionModeChange = { executionMode = it },
                resultDisplay = resultDisplay,
                onResultDisplayChange = { resultDisplay = it },
                iconDraft = iconDraft,
                onIconDraftChange = { iconDraft = it },
                onPickAppIcon = { pickingAppIcon = true },
                isSavingAppIcon = isSavingAppIcon,
                onDismiss = requestDismiss,
                onSave = {
                    onSave(it)
                    requestDismiss()
                },
                onDelete = onDelete?.let { delete ->
                    {
                        delete()
                        requestDismiss()
                    }
                },
                onTest = onTest,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellCommandEditorOverlayBody(
    initial: ShellCommand?,
    shizukuGranted: Boolean,
    label: String,
    onLabelChange: (String) -> Unit,
    command: String,
    onCommandChange: (String) -> Unit,
    executionMode: ShellExecutionMode,
    onExecutionModeChange: (ShellExecutionMode) -> Unit,
    resultDisplay: ShellResultDisplay,
    onResultDisplayChange: (ShellResultDisplay) -> Unit,
    iconDraft: ShellCommandIconDraft,
    onIconDraftChange: (ShellCommandIconDraft) -> Unit,
    onPickAppIcon: () -> Unit,
    isSavingAppIcon: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)?,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)?,
) {
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ShellTestResultState?>(null) }
    var testDialogResult by remember { mutableStateOf<ShellTestResultState?>(null) }
    val canSave = command.isNotBlank()
    val canTest = canSave && !testing
    val context = LocalContext.current

    fun buildDraft(): ShellCommand = buildShellCommandDraft(
        context = context,
        initial = initial,
        label = label,
        command = command,
        iconDraft = iconDraft,
        executionMode = executionMode,
        resultDisplay = resultDisplay,
    )

    val title = if (initial == null) {
        stringResource(R.string.shell_panel_add)
    } else {
        stringResource(R.string.shell_panel_edit)
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLargeEmphasized)
        ShellExecutionModeSelector(
            executionMode = executionMode,
            onExecutionModeChange = {
                onExecutionModeChange(it)
                testResult = null
            },
        )
        ShellCommandEditorIconSection(
            label = label,
            onLabelChange = {
                onLabelChange(it)
                testResult = null
            },
            initial = initial,
            iconDraft = iconDraft,
            onIconDraftChange = onIconDraftChange,
            onPickAppIcon = onPickAppIcon,
            isSavingAppIcon = isSavingAppIcon,
            useWindowMenu = true,
        )
        ShellCommandEditorFields(
            command = command,
            onCommandChange = {
                onCommandChange(it)
                testResult = null
            },
            resultDisplay = resultDisplay,
            onResultDisplayChange = {
                onResultDisplayChange(it)
                testResult = null
            },
        )
        testResult?.let { result ->
            ShellInlineTestResultCard(
                exitCode = result.exitCode,
                output = result.output,
                onCopyOutput = { copyShellOutputWithToast(context, result.output) },
            )
        }
        testDialogResult?.let { result ->
            MiuixFormDialog(
                show = true,
                onDismissRequest = { testDialogResult = null },
                title = stringResource(R.string.shell_panel_test_result),
                confirmText = stringResource(R.string.confirm),
                onConfirm = { testDialogResult = null },
                dismissText = null,
            ) {
                ShellInlineTestResultCard(
                    exitCode = result.exitCode,
                    output = result.output,
                    onCopyOutput = { copyShellOutputWithToast(context, result.output) },
                )
            }
        }
        ShellCommandEditorActionsRow(
            canSave = canSave,
            canTest = canTest,
            testing = testing,
            onCancel = onDismiss,
            onSave = { onSave(buildDraft()) },
            onDelete = onDelete,
            onTest = onTest?.let { test ->
                {
                    val draft = buildDraft()
                    testing = true
                    testResult = null
                    test.invoke(draft) { exitCode, output ->
                        testing = false
                        testResult = ShellTestResultState(exitCode, output)
                        when (draft.resultDisplay) {
                            ShellResultDisplay.TOAST -> {
                                Toast.makeText(
                                    context,
                                    ShellResultFormatter.toast(context, exitCode, output),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            ShellResultDisplay.DIALOG -> {
                                testDialogResult = ShellTestResultState(exitCode, output)
                            }
                            ShellResultDisplay.NONE -> Unit
                        }
                    }
                }
            },
        )
    }
}
