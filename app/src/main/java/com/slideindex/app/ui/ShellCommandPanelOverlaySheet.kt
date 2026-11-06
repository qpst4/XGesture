package com.slideindex.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellExecutionMode
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.util.ShellCommandRunner
import com.slideindex.app.util.ShellResultFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Refresh
private const val SHELL_PANEL_OVERLAY_TIMEOUT_MS = 40_000L

private class ContinuousDismissGate {
    var editorOpen: Boolean = false
    var resultOpen: Boolean = false
    fun canDismiss(): Boolean = !editorOpen && !resultOpen
}

private data class ShellPanelResultState(
    val label: String,
    val command: String,
    val exitCode: Int,
    val output: String,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandPanelOverlaySheet(
    initialCommands: List<ShellCommand>,
    shizukuGranted: Boolean,
    onRequestShizuku: () -> Unit,
    onDismissComplete: () -> Unit,
    onPersistCommands: (List<ShellCommand>) -> Unit,
    onWindowReady: (() -> Unit)? = null,
    registerBackHandler: ((() -> Unit) -> Unit)? = null,
    registerContinuousDismissHandler: ((() -> Unit) -> Unit)? = null,
    onCopyOutput: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val shellTimeoutMessage = stringResource(R.string.shell_panel_execute_timeout)
    val modeUnavailableMessage = stringResource(R.string.shell_panel_mode_unavailable)
    val scope = rememberCoroutineScope()
    var commands by remember(initialCommands) { mutableStateOf(initialCommands) }
    var editorTarget by remember { mutableStateOf<ShellCommand?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var runningCommandId by remember { mutableStateOf<String?>(null) }
    var resultDialog by remember { mutableStateOf<ShellPanelResultState?>(null) }
    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
    var checkingRoot by remember { mutableStateOf(false) }
    val dismissGate = remember { ContinuousDismissGate() }
    dismissGate.editorOpen = showEditor
    dismissGate.resultOpen = resultDialog != null

    LaunchedEffect(Unit) {
        if (rootAvailable == null && !checkingRoot) {
            checkingRoot = true
            rootAvailable = withContext(Dispatchers.IO) {
                runCatching {
                    PrivilegeGateway.probeDirectRootAvailable()
                }.getOrDefault(false)
            }
            checkingRoot = false
        }
    }

    fun canRun(item: ShellCommand): Boolean = when (item.executionMode) {
        ShellExecutionMode.ROOT -> rootAvailable == true
        ShellExecutionMode.SHIZUKU -> shizukuGranted
        null -> false
    }

    SideEffect {
        registerContinuousDismissHandler?.invoke {
            if (dismissGate.canDismiss()) {
                onDismissComplete()
            }
        }
    }

    if (showEditor) {
        ShellCommandEditorOverlaySheet(
            onDismissComplete = {
                showEditor = false
                editorTarget = null
            },
            initial = editorTarget,
            shizukuGranted = shizukuGranted,
            onSave = { saved ->
                commands = if (editorTarget == null) {
                    commands + saved
                } else {
                    commands.map { if (it.id == saved.id) saved else it }
                }
                onPersistCommands(commands)
                showEditor = false
                editorTarget = null
            },
            onDelete = editorTarget?.let { target ->
                {
                    commands = commands.filterNot { it.id == target.id }
                    onPersistCommands(commands)
                    showEditor = false
                    editorTarget = null
                }
            },
            onTest = { command, callback ->
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        ShellCommandRunner.execute(context, command)
                    }
                    callback(outcome.exitCode, outcome.output)
                }
            },
        )
        return
    }

    if (resultDialog != null) {
        val state = resultDialog!!
        ShellCommandResultOverlaySheet(
            label = state.label,
            command = state.command,
            exitCode = state.exitCode,
            output = state.output,
            onDismissComplete = { resultDialog = null },
            onCopy = { onCopyOutput(state.output) },
        )
        return
    }

    OverlayAnimatedDialogContent(
        onDismissComplete = onDismissComplete,
        onWindowReady = onWindowReady,
        registerBackHandler = registerBackHandler,
    ) { requestDismiss ->
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                ShellOverlayEnvironmentRow(
                    rootAvailable = rootAvailable,
                    checkingRoot = checkingRoot,
                    onCheckRoot = {
                        if (!checkingRoot) {
                            checkingRoot = true
                            scope.launch {
                                rootAvailable = withContext(Dispatchers.IO) {
                                    runCatching {
                                        PrivilegeGateway.probeDirectRootAvailable()
                                    }.getOrDefault(false)
                                }
                                checkingRoot = false
                            }
                        }
                    },
                    shizukuGranted = shizukuGranted,
                    onRequestShizuku = onRequestShizuku,
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (commands.isEmpty()) {
                    Text(
                        text = stringResource(R.string.shell_panel_overlay_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp).padding(top = 12.dp, bottom = 64.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        contentPadding = PaddingValues(bottom = 72.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(commands, key = { it.id }) { item ->
                            ShellCommandCard(
                                item = item,
                                running = runningCommandId == item.id,
                                enabled = canRun(item) && runningCommandId == null,
                                onEdit = {
                                    editorTarget = item
                                    showEditor = true
                                },
                                onRun = {
                                    if (!canRun(item)) {
                                        resultDialog = ShellPanelResultState(
                                            label = item.label,
                                            command = item.command,
                                            exitCode = -1,
                                            output = modeUnavailableMessage,
                                        )
                                        return@ShellCommandCard
                                    }
                                    scope.launch {
                                        runningCommandId = item.id
                                        try {
                                            val outcome = withTimeout(SHELL_PANEL_OVERLAY_TIMEOUT_MS) {
                                                withContext(Dispatchers.IO) {
                                                    ShellCommandRunner.execute(context, item)
                                                }
                                            }
                                            resultDialog = ShellPanelResultState(
                                                label = item.label,
                                                command = outcome.expandedCommand,
                                                exitCode = outcome.exitCode,
                                                output = outcome.output,
                                            )
                                        } catch (_: Exception) {
                                            resultDialog = ShellPanelResultState(
                                                label = item.label,
                                                command = item.command,
                                                exitCode = -1,
                                                output = shellTimeoutMessage,
                                            )
                                        } finally {
                                            runningCommandId = null
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                }
                MiuixSettingsFab(
                    onClick = {
                        editorTarget = null
                        showEditor = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp),
                    icon = MiuixIcons.Add,
                    contentDescription = stringResource(R.string.shell_panel_add),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellOverlayEnvironmentRow(
    rootAvailable: Boolean?,
    checkingRoot: Boolean,
    onCheckRoot: () -> Unit,
    shizukuGranted: Boolean,
    onRequestShizuku: () -> Unit,
) {
    val rootText = when {
        checkingRoot -> stringResource(R.string.shell_panel_root_unknown)
        rootAvailable == true -> stringResource(R.string.shell_panel_root_ready)
        rootAvailable == false -> stringResource(R.string.shell_panel_root_unavailable)
        else -> stringResource(R.string.shell_panel_root_unknown)
    }
    val shizukuText = if (shizukuGranted) {
        stringResource(R.string.shell_panel_shizuku_ready)
    } else {
        stringResource(R.string.shell_panel_shizuku_unavailable)
    }
    val statusColor = when {
        checkingRoot -> MaterialTheme.colorScheme.outline
        rootAvailable == false || !shizukuGranted -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.shell_panel_environment_title),
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            Text(
                text = stringResource(
                    R.string.shell_panel_environment_summary,
                    shizukuText,
                    rootText,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!shizukuGranted) {
            MiuixTextButton(
                text = stringResource(R.string.permission_shizuku_grant),
                onClick = onRequestShizuku,
            )
        }
        if (checkingRoot) {
            LoadingIndicator(modifier = Modifier.size(20.dp))
        } else {
            IconButton(
                onClick = onCheckRoot,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Medium.Refresh,
                    contentDescription = stringResource(R.string.shell_panel_root_check),
                )
            }
        }
    }
}
