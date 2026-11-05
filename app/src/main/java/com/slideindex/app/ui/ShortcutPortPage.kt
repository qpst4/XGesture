@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.shortcut.port.ApkTools
import com.slideindex.app.shortcut.port.RootShell
import com.slideindex.app.shortcut.port.ShizukuShell
import com.slideindex.app.shortcut.port.ShortCutItem
import com.slideindex.app.shortcut.port.ShortCutManager
import com.slideindex.app.shortcut.port.ShortcutPortEntry
import com.slideindex.app.shortcut.port.ShortcutRootBatchReader
import com.slideindex.app.shortcut.port.toCurrentIntent
import com.slideindex.app.service.LaunchTrampolineActivity
import com.slideindex.app.util.ShortcutKind
import com.slideindex.app.util.ShortcutLaunchDispatcher
import com.slideindex.app.ui.miuix.MiuixProgressCard
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsCardItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class PortSource { ROOT, SHIZUKU }

/**
 * Shortcut diagnostics and execution.
 *
 * The scan/parse/execute chain keeps the original command format: root XML or dumpsys,
 * then `toIntent()` -> root su / Shizuku `sh -c`. The visual shell is MIUIX.
 */
@Composable
fun ShortcutPortPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootShell = remember { RootShell() }
    val shizukuShell = remember { ShizukuShell() }

    var hasPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<ShortcutPortEntry>>(emptyList()) }
    var progressText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var scanSource by remember { mutableStateOf<PortSource?>(null) }
    var scanDurationMs by remember { mutableLongStateOf(0L) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var activeMode by remember { mutableStateOf(PrivilegeGateway.mode) }

    DisposableEffect(Unit) {
        onDispose { rootShell.close() }
    }

    fun switchMode(mode: PrivilegeMode) {
        if (mode == activeMode) return
        activeMode = mode
        hasPermission = false
        entries = emptyList()
        scanSource = null
        scanDurationMs = 0L
        progressText = ""
        isLoading = true
    }

    LaunchedEffect(activeMode) {
        if (
            activeMode == PrivilegeMode.SHIZUKU &&
            shizukuShell.isSupported() &&
            shizukuShell.isPermissionDenied()
        ) {
            shizukuShell.requestPermission()
        }
    }

    LaunchedEffect(activeMode, reloadKey) {
        isLoading = true
        scanDurationMs = 0L
        progressText = ""
        val mode = activeMode

        if (!hasPermission) {
            while (isActive && !hasPermission) {
                hasPermission = when (mode) {
                    PrivilegeMode.ROOT -> rootPermission(rootShell)
                    PrivilegeMode.SHIZUKU -> shizukuPermission(shizukuShell)
                }
                if (!hasPermission) delay(2000)
            }
        }
        if (!isActive || !hasPermission) {
            isLoading = false
            return@LaunchedEffect
        }

        val startedAt = SystemClock.elapsedRealtime()
        val manager = ShortCutManager(context)
        try {
            withContext(Dispatchers.IO) {
                when (mode) {
                    PrivilegeMode.ROOT -> {
                        scanSource = PortSource.ROOT
                        progressText = "Reading shortcut XML…"
                        val documents = ShortcutRootBatchReader.readAll(rootShell)
                        progressText = "Parsing ${documents.size} packages…"
                        manager.fromPackageBytes(documents) { progressText = it }
                    }
                    PrivilegeMode.SHIZUKU -> {
                        scanSource = PortSource.SHIZUKU
                        val dump = shizukuExec(shizukuShell, "dumpsys shortcut")
                        manager.fromShellData(dump) { progressText = it }
                    }
                }
                entries = manager.fullData().map {
                    ShortcutPortEntry(
                        item = it,
                        kinds = manager.kindsOf(it),
                        intent = manager.intentOf(it) ?: it.toCurrentIntent(),
                    )
                }
            }
            scanDurationMs = SystemClock.elapsedRealtime() - startedAt
        } catch (error: Exception) {
            Log.e("ShortcutPort", "load failed", error)
        } finally {
            // A cancelled effect (for example a previous page instance) must not
            // clear the loading flag of the effect that is still running.
            if (currentCoroutineContext().isActive) {
                isLoading = false
            }
        }
    }

    val filtered = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) {
            entries
        } else {
            entries.filter {
                it.item.title.contains(searchQuery, ignoreCase = true) ||
                    it.item.name.contains(searchQuery, ignoreCase = true) ||
                    it.item.pkg.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val tipText = stringResource(R.string.shortcut_port_tip)
    val countTitle = stringResource(R.string.shortcut_port_ready, filtered.size)

    fun copyItem(item: ShortCutItem) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            Toast.makeText(context, R.string.shortcut_test_copy_failed, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, item.toIntent()))
        }.onSuccess {
            Toast.makeText(context, R.string.shortcut_test_copied, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, R.string.shortcut_test_copy_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun executeItem(entry: ShortcutPortEntry) {
        ShortcutLaunchDispatcher.launch(
            context = context,
            packageName = entry.item.pkg,
            intent = entry.intent,
            exportedLaunch = { intent ->
                context.startActivity(LaunchTrampolineActivity.createIntent(context, intent))
                true
            },
            onResult = { success ->
                Toast.makeText(
                    context,
                    if (success) {
                        context.getString(R.string.shortcut_port_exec_success, "OK")
                    } else {
                        context.getString(R.string.shortcut_port_exec_failed, "launch failed")
                    },
                    Toast.LENGTH_LONG,
                ).show()
            },
            onFailure = { failure ->
                ShortcutLaunchDispatcher.showFailure(context, failure)
            },
        )
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.shortcut_test_title),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
        hintResId = R.string.shortcut_test_search_hint,
        extraActions = {
            IconButton(
                onClick = { if (!isLoading) reloadKey++ },
                enabled = !isLoading,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.shortcut_port_rescan),
                )
            }
        },
    ) {
        item(key = "shortcut-port-mode") {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                ShellSegmentedControl(
                    labels = listOf("Root", "Shizuku"),
                    selectedIndex = if (activeMode == PrivilegeMode.ROOT) 0 else 1,
                    onSelect = { index ->
                        switchMode(if (index == 0) PrivilegeMode.ROOT else PrivilegeMode.SHIZUKU)
                    },
                )
            }
        }

        settingsLazyTipCard(key = "shortcut-port-tip", text = tipText)

        item(key = "shortcut-port-status") {
            ShortcutPortStatusCard(
                hasPermission = hasPermission,
                isLoading = isLoading,
                progressText = progressText,
                entries = entries,
                source = scanSource,
                durationMs = scanDurationMs,
                onRescan = { if (!isLoading) reloadKey++ },
            )
        }

        if (hasPermission && !isLoading) {
            if (filtered.isEmpty()) {
                item(key = "shortcut-port-empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = stringResource(R.string.shortcut_test_empty),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else {
                settingsLazySmallTitle(key = "shortcut-port-results", title = countTitle)
                groupedCardItems(
                    keyPrefix = "shortcut-port",
                    items = filtered.mapIndexed { index, entry ->
                        settingsCardItem("${entry.item.pkg}/${entry.item.shortcut}/$index") {
                            ShortcutPortRow(
                                entry = entry,
                                onCopy = { copyItem(entry.item) },
                                onExecute = { executeItem(entry) },
                            )
                        }
                    },
                )
            }
        }

        item(key = "shortcut-port-bottom") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ShortcutPortStatusCard(
    hasPermission: Boolean,
    isLoading: Boolean,
    progressText: String,
    entries: List<ShortcutPortEntry>,
    source: PortSource?,
    durationMs: Long,
    onRescan: () -> Unit,
) {
    val appCount = entries.map { it.item.pkg }.distinct().size
    val pinned = entries.count { ShortcutKind.PINNED in it.kinds }
    val dynamic = entries.count { ShortcutKind.DYNAMIC in it.kinds }
    val static = entries.count { ShortcutKind.STATIC in it.kinds }
    val modeLabel = when (source) {
        PortSource.ROOT -> stringResource(R.string.shortcut_port_root)
        PortSource.SHIZUKU -> stringResource(R.string.shortcut_port_shizuku)
        null -> stringResource(R.string.shortcut_port_unknown)
    }

    MiuixProgressCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    MiuixText(
                        text = modeLabel,
                        style = MiuixTheme.textStyles.body1,
                    )
                    MiuixText(
                        text = when {
                            !hasPermission -> stringResource(R.string.shortcut_test_no_privilege)
                            isLoading -> stringResource(R.string.shortcut_port_loading)
                            else -> stringResource(R.string.shortcut_port_ready, entries.size)
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = if (!hasPermission) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
                if (hasPermission) {
                    MiuixTextButton(
                        text = stringResource(R.string.shortcut_port_rescan),
                        onClick = onRescan,
                        enabled = !isLoading,
                    )
                }
            }

            if (hasPermission) {
                MiuixText(
                    text = stringResource(R.string.shortcut_port_apps, appCount),
                    style = MiuixTheme.textStyles.body2,
                )
                MiuixText(
                    text = stringResource(R.string.shortcut_port_kinds, pinned, dynamic, static),
                    style = MiuixTheme.textStyles.body2,
                )
                if (durationMs > 0L) {
                    MiuixText(
                        text = stringResource(R.string.shortcut_port_duration, durationMs),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    MiuixText(
                        text = progressText.ifBlank { stringResource(R.string.shortcut_port_loading) },
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutPortRow(
    entry: ShortcutPortEntry,
    onCopy: () -> Unit,
    onExecute: () -> Unit,
) {
    val item = entry.item
    val pinnedLabel = stringResource(R.string.shortcut_test_kind_pinned)
    val dynamicLabel = stringResource(R.string.shortcut_test_kind_dynamic)
    val staticLabel = stringResource(R.string.shortcut_test_kind_static)
    val kindText = listOfNotNull(
        if (ShortcutKind.PINNED in entry.kinds) pinnedLabel else null,
        if (ShortcutKind.DYNAMIC in entry.kinds) dynamicLabel else null,
        if (ShortcutKind.STATIC in entry.kinds) staticLabel else null,
    ).joinToString(" / ")
    val summary = buildString {
        append(item.title.ifBlank { item.pkg })
        append(" · ")
        append(item.pkg)
        if (kindText.isNotBlank()) {
            append(" · ")
            append(kindText)
        }
        if (item.text.isNotBlank() && item.text != item.title) {
            append(" · ")
            append(item.text)
        }
    }
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = item.name.ifBlank { item.title.ifBlank { item.pkg } },
        summary = summary,
        startAction = { ShortcutPortIcon(packageName = item.pkg) },
        onClick = onExecute,
        endActions = {
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.shortcut_test_copy),
                )
            }
            IconButton(onClick = onExecute) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.shortcut_test_launch),
                )
            }
        },
    )
}

@Composable
private fun ShortcutPortIcon(
    packageName: String,
    size: Dp = 32.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    var bitmap by remember(packageName, sizePx) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName, sizePx) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ApkTools(context).getAppIcon(packageName)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Shortcut,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private fun rootPermission(rootShell: RootShell): Boolean = rootShell.openShell()

private fun shizukuPermission(shell: ShizukuShell): Boolean =
    shell.isSupported() && !shell.isPermissionDenied()

private suspend fun rootExec(rootShell: RootShell, command: String): String =
    withContext(Dispatchers.IO) {
        Log.d("Root执行命令", command)
        rootShell.execute(command)
    }

private suspend fun shizukuExec(shell: ShizukuShell, command: String): String {
    Log.d("shizuku执行命令", command)
    return shell.runAndGetOutput(command)
}

private fun looksLikeAmFailure(output: String): Boolean {
    val lower = output.lowercase()
    return lower.contains("exception") ||
        lower.contains("error") ||
        lower.contains("permission denial") ||
        lower.contains("unable to resolve") ||
        lower.contains("does not exist")
}

private fun looksLikeRootRequired(output: String): Boolean {
    val lower = output.lowercase()
    return lower.contains("permission denial") ||
        lower.contains("not exported") ||
        lower.contains("securityexception") ||
        lower.contains("unable to start activity")
}
