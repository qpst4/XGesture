package com.slideindex.app.ui



import top.yukonga.miuix.kmp.basic.SmallTitle
import android.content.ClipData

import android.content.ClipboardManager

import android.content.Context

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.grid.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Code


import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixSettingsFabInset
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.slideindex.app.R

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.service.ShellCommandResultTrampoline
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellResultDisplay

import com.slideindex.app.shell.ShellTemplateContextFactory

import com.slideindex.app.shizuku.ShizukuUserServiceHost

import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.PermissionGatedFeature
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

import com.slideindex.app.ui.viewmodel.ShellCommandResultState
import com.slideindex.app.ui.viewmodel.ShellCommandViewModel

import com.slideindex.app.util.ShellResultFormatter
import com.slideindex.app.util.TaskManagerUtil

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import kotlinx.coroutines.withTimeout
import top.yukonga.miuix.kmp.icon.extended.Recent



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun ShellCommandPanelScreen(

    settings: AppSettings,

    shizukuGranted: Boolean,

    onBack: () -> Unit,

    onSaveCommands: (List<ShellCommand>) -> Unit,

    onRequestShizuku: () -> Unit,

    shellViewModel: ShellCommandViewModel,

    onOpenHistory: () -> Unit,

    onOpenEditor: (String?) -> Unit,

    onOpenResult: () -> Unit,

) {

    val context = LocalContext.current
    val resources = LocalResources.current

    val shellTimeoutMessage = stringResource(R.string.shell_panel_execute_timeout)
    val restartFailedMessage = stringResource(R.string.shell_panel_restart_failed)

    val scope = rememberCoroutineScope()

    var commands by remember(settings.shellCommands) { mutableStateOf(settings.shellCommands) }

    var runningCommandId by remember { mutableStateOf<String?>(null) }

    var rootAvailable by remember { mutableStateOf<Boolean?>(null) }

    var checkingRoot by remember { mutableStateOf(false) }

    fun deliverResult(
        item: ShellCommand,
        expandedCommand: String,
        exitCode: Int,
        output: String,
    ) {
        when (item.resultDisplay) {
            ShellResultDisplay.DIALOG -> {
                shellViewModel.setPendingResult(
                    ShellCommandResultState(
                        label = item.label,
                        command = expandedCommand,
                        exitCode = exitCode,
                        output = output,
                    ),
                )
                ShellCommandResultTrampoline.launch(
                    context = context,
                    result = ShellCommandResultTrampoline.Payload(
                        label = item.label,
                        command = expandedCommand,
                        exitCode = exitCode,
                        output = output,
                    ),
                    onPrepare = {},
                    onDismiss = { shellViewModel.clearPendingResult() },
                )
            }
            ShellResultDisplay.TOAST -> {
                android.widget.Toast.makeText(
                    context,
                    ShellResultFormatter.toast(context, exitCode, output),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            ShellResultDisplay.NONE -> {
                if (exitCode != 0) {
                    android.widget.Toast.makeText(
                        context,
                        ShellResultFormatter.toast(context, exitCode, output),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun runCommand(item: ShellCommand) {

        scope.launch {

            runningCommandId = item.id

            try {

                val result = withTimeout(SHELL_UI_TIMEOUT_MS) {

                    shellViewModel.execute(item)

                }

                deliverResult(
                    item = item,
                    expandedCommand = result.expandedCommand,
                    exitCode = result.exitCode,
                    output = result.output,
                )

            } catch (_: Exception) {

                deliverResult(
                    item = item,
                    expandedCommand = item.command,
                    exitCode = -1,
                    output = shellTimeoutMessage,
                )

            } finally {

                runningCommandId = null

            }

        }

    }



    SettingsScreenScaffold(
        title = stringResource(R.string.shell_panel_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    MiuixIcons.Medium.Recent,
                    contentDescription = stringResource(R.string.shell_panel_history_action),
                )
            }
        },
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = { onOpenEditor(null) },
                icon = MiuixIcons.Add,
                contentDescription = stringResource(R.string.shell_panel_add),
            )
        },
        fabInset = MiuixSettingsFabInset,
    ) {
        item(key = "shell-environment") {
            var restartingService by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                if (rootAvailable == null && !checkingRoot) {
                    checkingRoot = true
                    rootAvailable = withContext(Dispatchers.IO) {
                        runCatching {
                            com.slideindex.app.privilege.PrivilegeGateway.probeDirectRootAvailable()
                        }.getOrDefault(false)
                    }
                    checkingRoot = false
                }
            }

            ShellExecutionEnvironmentCard(
                rootAvailable = rootAvailable,
                checkingRoot = checkingRoot,
                onCheckRoot = {
                    if (!checkingRoot) {
                        checkingRoot = true
                        scope.launch {
                            rootAvailable = withContext(Dispatchers.IO) {
                                runCatching {
                                    com.slideindex.app.privilege.PrivilegeGateway.probeDirectRootAvailable()
                                }.getOrDefault(false)
                            }
                            checkingRoot = false
                        }
                    }
                },
                shizukuGranted = shizukuGranted,
                restartingService = restartingService,
                onRequestShizuku = onRequestShizuku,
                onRestartService = {
                    if (!restartingService) {
                        restartingService = true
                        scope.launch {
                            val api = withContext(Dispatchers.IO) {
                                runCatching { TaskManagerUtil.restartShellService() }.getOrDefault(-1)
                            }
                            restartingService = false
                            val message = when {
                                api >= ShizukuUserServiceHost.SERVICE_BUILD -> {
                                    resources.getString(R.string.shell_panel_restart_success, api)
                                }
                                api > 0 -> {
                                    resources.getString(R.string.shell_panel_restart_outdated, api)
                                }
                                else -> restartFailedMessage
                            }
                            android.widget.Toast.makeText(
                                context,
                                message,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            )
        }

        item(key = "shell-commands-header") {
            SmallTitle(
                text = if (commands.isEmpty()) {
                    stringResource(R.string.shell_panel_commands_section)
                } else {
                    stringResource(R.string.shell_panel_commands_section_count, commands.size)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
            )
        }

        if (commands.isEmpty()) {
            item(key = "shell-commands-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.shell_panel_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        } else {
            commands.chunked(2).forEachIndexed { index, rowItems ->
                item(key = "shell-command-row-$index") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                ShellCommandCard(
                                    item = item,
                                    running = runningCommandId == item.id,
                                    enabled = runningCommandId == null,
                                    onEdit = { onOpenEditor(item.id) },
                                    onRun = { runCommand(item) },
                                )
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item(key = "shell-panel-bottom") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable

fun SettingsCardScope.ShellCommandEntryCard(

    commandCount: Int,

    outlinedLeadingIcons: Boolean = false,

    onClick: () -> Unit,

) {

    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.shellCommand(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.shell_panel_entry_title),
        subtitle = if (commandCount > 0) {
            stringResource(R.string.shell_panel_entry_desc_count, commandCount)
        } else {
            stringResource(R.string.shell_panel_entry_desc)
        },
        onClick = onClick,
    )

}



private const val SHELL_UI_TIMEOUT_MS = 40_000L



private fun copyToClipboard(context: Context, text: String) {

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", text))

}

