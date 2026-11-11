package com.slideindex.app.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.shell.ShellCommandIconStorage
import com.slideindex.app.util.ShellCommandRunner
import com.slideindex.app.ui.ShellCommandEditorScreen
import com.slideindex.app.ui.ShellCommandPanelScreen
import com.slideindex.app.ui.ShellOutputHistoryScreen
import com.slideindex.app.ui.ShellResultScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.ShellCommandViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NavEntryBuilder.shellCommandNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.ShellCommands> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        ShellCommandPanelScreen(
            settings = settings,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBack() },
            onSaveCommands = viewModel::setShellCommands,
            onRequestShizuku = { ctx.requestShizuku() },
            shellViewModel = shellViewModel,
            onOpenHistory = { ctx.navigate(AppNavKey.ShellCommandHistory) },
            onOpenEditor = { commandId ->
                ctx.navigate(AppNavKey.ShellCommandEditor(commandId.orEmpty()))
            },
            onOpenResult = { ctx.navigate(AppNavKey.ShellCommandResult) },
        )
    }

    hiltEntry<AppNavKey.ShellCommandHistory> {
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        ShellOutputHistoryScreen(
            repository = shellViewModel.historyRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ShellCommands) },
            onClear = shellViewModel::clearHistory,
        )
    }

    hiltEntry<AppNavKey.ShellCommandEditor> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val initial = key.commandId.takeIf { it.isNotEmpty() }
            ?.let { id -> settings.shellCommands.find { it.id == id } }
        ShellCommandEditorScreen(
            initial = initial,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.ShellCommands) },
            onSave = { draft ->
                val current = settings.shellCommands
                val updated = if (current.any { it.id == draft.id }) {
                    current.map { if (it.id == draft.id) draft else it }
                } else {
                    current + draft
                }
                viewModel.setShellCommands(updated)
                ctx.navigateBackTo(AppNavKey.ShellCommands)
            },
            onDelete = initial?.let { existing ->
                {
                    ShellCommandIconStorage.deleteIconIfOwned(context, existing.iconPath)
                    viewModel.setShellCommands(
                        settings.shellCommands.filter { it.id != existing.id },
                    )
                    ctx.navigateBackTo(AppNavKey.ShellCommands)
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
    }

    hiltEntry<AppNavKey.ShellCommandResult> {
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        val context = LocalContext.current
        val pending = shellViewModel.pendingResult
        if (pending == null) {
            LaunchedEffect(Unit) {
                ctx.navigateBackTo(AppNavKey.ShellCommands)
            }
        } else {
            ShellResultScreen(
                label = pending.label,
                command = pending.command,
                exitCode = pending.exitCode,
                output = pending.output,
                onBack = {
                    shellViewModel.clearPendingResult()
                    ctx.navigateBackTo(AppNavKey.ShellCommands)
                },
                onCopy = { copyShellOutputToClipboard(context, pending.output) },
            )
        }
    }
}

private fun copyShellOutputToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", text))
}
