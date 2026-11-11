@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.viewmodel.CrashReportDetailState
import com.slideindex.app.ui.viewmodel.DiagnosticLogViewModel
import com.slideindex.app.util.DiagnosticReportExporter
import com.slideindex.app.util.LocalCrashHandler
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.MiuixIcons

@Composable
fun DiagnosticLogListScreen(
    viewModel: DiagnosticLogViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    val crashReports by viewModel.crashReports.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var showClearCrashesConfirm by remember { mutableStateOf(false) }
    val filteredReports = remember(crashReports, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            crashReports
        } else {
            crashReports.filter { entry ->
                entry.fileName.contains(query, ignoreCase = true) ||
                    entry.previewLine.contains(query, ignoreCase = true)
            }
        }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.diagnostic_log_title),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
        hintResId = R.string.diagnostic_log_search_hint,
        extraActions = {
            IconButton(onClick = { DiagnosticReportExporter.shareOrCopy(context) }) {
                Icon(
                    imageVector = MiuixIcons.Medium.Share,
                    contentDescription = stringResource(R.string.diagnostic_log_share)
                )
            }
            if (crashReports.isNotEmpty()) {
                IconButton(onClick = { showClearCrashesConfirm = true }) {
                    Text(
                        text = stringResource(R.string.diagnostic_log_clear),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
    ) {
        if (crashReports.isEmpty()) {
            item(key = "crashes-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.diagnostic_log_crash_empty_list),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredReports.isEmpty()) {
            item(key = "crashes-search-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.diagnostic_log_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item(key = "crash-reports-top-spacer") {
                Spacer(Modifier.padding(top = 8.dp))
            }
            groupedCardItems(
                keyPrefix = "crash-reports",
                items = filteredReports.map { entry ->
                    settingsCardScopeItem(entry.fileName) {
                        CrashReportRow(
                            entry = entry,
                            onClick = { onOpenDetail(entry.fileName) }
                        )
                    }
                }
            )
        }

        item(key = "bottom-spacer") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    MiuixConfirmDialog(
        show = showClearCrashesConfirm,
        onDismissRequest = { showClearCrashesConfirm = false },
        title = stringResource(R.string.diagnostic_log_clear_crash_title),
        message = stringResource(R.string.diagnostic_log_clear_crash_message),
        confirmText = stringResource(R.string.diagnostic_log_clear),
        onConfirm = {
            viewModel.clearCrashReports()
            showClearCrashesConfirm = false
        }
    )
}

@Composable
fun DiagnosticLogDetailScreen(
    fileName: String,
    viewModel: DiagnosticLogViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val crashReports by viewModel.crashReports.collectAsStateWithLifecycle()
    val crashReportDetails by viewModel.crashReportDetails.collectAsStateWithLifecycle()
    LaunchedEffect(fileName) {
        viewModel.loadCrashReportDetail(fileName)
    }
    val detail = crashReportDetails[fileName] ?: CrashReportDetailState()
    val lines = remember(detail.text) { detail.text.lines() }
    val timeLabel = remember(fileName, crashReports) {
        crashReports.find { it.fileName == fileName }?.timestampMs?.let { ts ->
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))
        }
    }

    SettingsScreenScaffold(
        title = fileName,
        subtitle = timeLabel,
        onBack = onBack,
        scrollContent = true,
        actions = {
            IconButton(
                onClick = { DiagnosticReportExporter.shareCrashReport(context, fileName) },
                enabled = !detail.loading && detail.text.isNotBlank(),
            ) {
                Icon(
                    imageVector = MiuixIcons.Medium.Share,
                    contentDescription = stringResource(R.string.diagnostic_log_share),
                )
            }
        }
    ) {
        if (detail.loading) {
            item(key = "crash-detail-loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        } else if (detail.text.isBlank()) {
            item(key = "crash-detail-empty") {
                Text(
                    text = stringResource(R.string.diagnostic_log_crash_empty),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            if (detail.truncated) {
                item(key = "crash-detail-truncated") {
                    Text(
                        text = stringResource(
                            R.string.diagnostic_log_crash_truncated,
                            formatFileSize(detail.totalBytes),
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(
                count = lines.size,
                key = { index -> "crash-line-$index-${lines[index].hashCode()}" },
            ) { index ->
                Text(
                    text = lines[index],
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            item(key = "crash-detail-bottom-pad") {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun SettingsCardScope.CrashReportRow(
    entry: LocalCrashHandler.CrashReportEntry,
    onClick: () -> Unit
) {
    val timeLabel = remember(entry.timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.timestampMs))
    }
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("BugReport", contentDescription = label) },
        title = entry.fileName,
        subtitle = buildString {
            append(timeLabel)
            if (entry.previewLine.isNotBlank()) {
                append("\n")
                append(entry.previewLine)
            }
        },
        onClick = onClick
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
