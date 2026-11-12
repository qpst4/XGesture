package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandLabelResolver
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsCardScope.ShellCommandPanelShortcutPickSection(
    shellCommands: List<ShellCommand>,
    onPick: (ShellCommand) -> Unit,
) {
    if (shellCommands.isEmpty()) {
        return
    }
    shellCommands.forEach { cmd ->
        SettingNavigationRow(
            icon = { label ->
                if (cmd.hasCustomIcon()) {
                    ShellCommandIcon(
                        command = cmd,
                        modifier = Modifier.size(24.dp),
                        showDefaultCodeIcon = true,
                    )
                } else {
                    Icon(
                        ThinActionIcons.Code,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            title = cmd.label.ifBlank { gestureExecuteShellCommandPreview(cmd.command) },
            subtitle = gestureExecuteShellCommandPreview(cmd.command, maxLength = 64),
            onClick = { onPick(cmd) },
        )
    }
}

fun displayLabelForExecuteShellCommand(
    commandLine: String,
    shellCommands: List<ShellCommand>,
): String {
    val trimmed = commandLine.trim()
    return ShellCommandLabelResolver.customLabelFor(trimmed, shellCommands)
        ?: gestureExecuteShellCommandPreview(trimmed)
}
