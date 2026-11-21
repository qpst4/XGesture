@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.service.LaunchTrampolineActivity
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.shortcut.port.ApkTools
import com.slideindex.app.shortcut.port.RootShell
import com.slideindex.app.shortcut.port.ShizukuShell
import com.slideindex.app.shortcut.port.ShortCutManager
import com.slideindex.app.shortcut.port.ShortcutPortEntry
import com.slideindex.app.shortcut.port.ShortcutRootBatchReader
import com.slideindex.app.shortcut.port.toCurrentIntent
import com.slideindex.app.shortcuts.DynamicShortcutsManager
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixProgressCard
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.picker.invalidateActionPickerShortcutServiceCache
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsCardItem
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.ShortcutKind
import com.slideindex.app.util.ShortcutLaunchDispatcher
import com.slideindex.app.util.ShortcutUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.ConcurrentHashMap

enum class ShortcutTestMode {
    STATIC,
    PRIVILEGE,
    DYNAMIC,
}

private enum class PortSource { ROOT, SHIZUKU }

/** 统一的快捷方式测试与展示实体。 */
private data class UnifiedShortcutRowData(
    val key: String,
    val title: String,
    val summary: String,
    val packageName: String,
    val shortcutId: String = "",
    val userSerial: Long = 0L,
    val intentUri: String = "",
    val badgeKinds: Set<ShortcutKind> = emptySet(),
    val isAlreadySaved: Boolean = false,
    val onExecute: () -> Unit,
    val onCopy: () -> Unit,
    val onSaveToDirect: () -> Unit,
)

@Composable
fun ShortcutPortPage(
    savedShortcuts: List<ActivityShortcut> = emptyList(),
    onSaveShortcuts: (List<ActivityShortcut>) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val rootShell = remember { RootShell() }
    val shizukuShell = remember { ShizukuShell() }

    remember(context) {
        DynamicShortcutsManager.ensureInitialized(context)
        true
    }
    val testMode by DynamicShortcutsManager.testModeFlow.collectAsState()
    val privilegeMode by DynamicShortcutsManager.privilegeSubModeFlow.collectAsState()
    val pinnedRecords by DynamicShortcutsManager.pinnedRecordsFlow.collectAsState()

    var hasPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var progressText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var scanSource by remember { mutableStateOf<PortSource?>(null) }
    var scanDurationMs by remember { mutableLongStateOf(0L) }
    var reloadKey by remember { mutableIntStateOf(0) }

    // 各模式独立数据缓存
    var privilegeEntries by remember { mutableStateOf<List<ShortcutPortEntry>>(emptyList()) }
    var staticEntries by remember { mutableStateOf<List<UnifiedShortcutRowData>>(emptyList()) }
    var dynamicCandidates by remember { mutableStateOf<List<DynamicShortcutsManager.DiscoveredDynamicShortcut>>(emptyList()) }
    var showDynamicImportSheet by remember { mutableStateOf(false) }
    var hasHomePermission by remember { mutableStateOf(DynamicShortcutsManager.hasPermission(context)) }
    var homeRequestLaunchedAt by remember { mutableLongStateOf(0L) }
    var waitingAutoPinAfterHomeGrant by remember { mutableStateOf(false) }

    // 桌面固化管理/编辑模式状态
    var isEditMode by remember { mutableStateOf(false) }
    val selectedEditKeys = remember { mutableStateListOf<String>() }

    LaunchedEffect(testMode) {
        if (testMode != ShortcutTestMode.DYNAMIC) {
            isEditMode = false
            selectedEditKeys.clear()
        }
    }

    suspend fun performAutoFullPin() {
        isLoading = true
        val startedAt = SystemClock.elapsedRealtime()
        val discovered = withContext(Dispatchers.IO) {
            DynamicShortcutsManager.queryDynamicShortcuts(context)
                .distinctBy { "${it.packageName}/${it.userSerial}/${it.shortcutId}" }
        }
        dynamicCandidates = discovered
        if (discovered.isNotEmpty()) {
            DynamicShortcutsManager.pinShortcuts(context, discovered)
            AppShortcutLoader.invalidateShortcutCatalog()
            invalidateActionPickerShortcutServiceCache()
            scanDurationMs = SystemClock.elapsedRealtime() - startedAt
            isLoading = false
            Toast.makeText(
                context,
                "已自动全量固化 ${discovered.size} 项快捷方式！现在可切回原默认桌面",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            isLoading = false
            Toast.makeText(context, R.string.dynamic_shortcuts_empty, Toast.LENGTH_SHORT).show()
        }
    }

    val homeRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = DynamicShortcutsManager.hasPermission(context)
        hasHomePermission = granted
        if (granted) {
            waitingAutoPinAfterHomeGrant = false
            scope.launch { performAutoFullPin() }
        } else if (SystemClock.elapsedRealtime() - homeRequestLaunchedAt < 400L) {
            runCatching {
                homeSettingsLauncherRef.value?.launch(DynamicShortcutsManager.openHomeSettingsIntent())
                    ?: context.startActivity(DynamicShortcutsManager.openHomeSettingsIntent())
            }.onFailure {
                Toast.makeText(context, R.string.dynamic_shortcuts_home_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val homeSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = DynamicShortcutsManager.hasPermission(context)
        val wasWaiting = waitingAutoPinAfterHomeGrant
        hasHomePermission = granted
        if (granted && wasWaiting) {
            waitingAutoPinAfterHomeGrant = false
            scope.launch { performAutoFullPin() }
        }
    }

    DisposableEffect(homeSettingsLauncher) {
        homeSettingsLauncherRef.value = homeSettingsLauncher
        onDispose {
            if (homeSettingsLauncherRef.value === homeSettingsLauncher) {
                homeSettingsLauncherRef.value = null
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = DynamicShortcutsManager.hasPermission(context)
                val becameGranted = !hasHomePermission && nowGranted
                hasHomePermission = nowGranted
                if (nowGranted && (becameGranted || waitingAutoPinAfterHomeGrant)) {
                    waitingAutoPinAfterHomeGrant = false
                    scope.launch { performAutoFullPin() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            rootShell.close()
        }
    }

    val execSuccessText = stringResource(R.string.shortcut_port_exec_success, "OK")
    val execFailedText = stringResource(R.string.shortcut_port_exec_failed, "launch failed")
    val saveSuccessMsg = stringResource(R.string.shortcut_test_save_success)
    val alreadySavedMsg = stringResource(R.string.shortcut_test_already_saved)
    val portTipText = stringResource(R.string.shortcut_port_tip)
    val dynamicTipText = stringResource(R.string.shortcut_test_dynamic_tip)
    val staticTipText = "免特权静态模式直接解析各应用 AndroidManifest.xml 中预先声明的静态快捷方式，无需 Root 或 Shizuku 特权即可随时启动。"
    val emptyText = stringResource(R.string.shortcut_test_empty)

    fun copyText(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            Toast.makeText(context, R.string.shortcut_test_copy_failed, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        }.onSuccess {
            Toast.makeText(context, R.string.shortcut_test_copied, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, R.string.shortcut_test_copy_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun saveDirectShortcut(target: ActivityShortcut) {
        val exists = savedShortcuts.any {
            (it.packageName == target.packageName && it.shortcutId.isNotBlank() && it.shortcutId == target.shortcutId) ||
                (it.packageName == target.packageName && it.intentUris.isNotEmpty() && it.intentUris == target.intentUris) ||
                (it.packageName == target.packageName && it.label == target.label)
        }
        if (exists) {
            Toast.makeText(context, alreadySavedMsg, Toast.LENGTH_SHORT).show()
        } else {
            onSaveShortcuts(savedShortcuts + target)
            Toast.makeText(context, saveSuccessMsg, Toast.LENGTH_SHORT).show()
        }
    }

    fun executePrivilegeItem(entry: ShortcutPortEntry) {
        val intent = entry.intent
        val component = intent.component
        val exported = component == null || runCatching {
            context.packageManager.getActivityInfo(component, 0).exported
        }.getOrDefault(false)

        if (exported) {
            runCatching {
                context.startActivity(LaunchTrampolineActivity.createIntent(context, intent))
                Toast.makeText(context, execSuccessText, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, execFailedText, Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 未导出组件通过当前选中的特权模式启动
        scope.launch(Dispatchers.IO) {
            val success = when (privilegeMode) {
                PrivilegeMode.ROOT -> {
                    val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
                    rootShell.execute("am start -d '$uri'").isNotBlank()
                }
                PrivilegeMode.SHIZUKU -> {
                    val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
                    shizukuExec(shizukuShell, "am start -d '$uri'").isNotBlank()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (success) execSuccessText else execFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // PRIVILEGE 模式加载
    LaunchedEffect(testMode, privilegeMode, reloadKey) {
        if (testMode != ShortcutTestMode.PRIVILEGE) return@LaunchedEffect
        isLoading = true
        scanDurationMs = 0L
        progressText = ""
        val mode = privilegeMode

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
                privilegeEntries = manager.fullData().map {
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
            if (currentCoroutineContext().isActive) {
                isLoading = false
            }
        }
    }

    // STATIC 模式加载
    LaunchedEffect(testMode, reloadKey) {
        if (testMode != ShortcutTestMode.STATIC) return@LaunchedEffect
        isLoading = true
        val startedAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) {
            val appsWithShortcuts = ShortcutUtils.getAllAppsWithShortcut(context)
            val list = mutableListOf<UnifiedShortcutRowData>()
            for (app in appsWithShortcuts) {
                for (sc in app.shortcuts) {
                    val intentUri = sc.intents.firstOrNull().orEmpty()
                    val shortcutTarget = if (sc.className.isNotBlank()) {
                        ActivityShortcut.component(
                            label = sc.label,
                            packageName = sc.packageName,
                            activityClassName = sc.className,
                        )
                    } else {
                        ActivityShortcut.intent(
                            label = sc.label,
                            packageName = sc.packageName,
                            intentUris = sc.intents,
                        )
                    }
                    val isSaved = savedShortcuts.any {
                        it.packageName == sc.packageName && (
                            (it.activityClassName.isNotBlank() && it.activityClassName == sc.className) ||
                            (it.intentUris.isNotEmpty() && it.intentUris == sc.intents)
                        )
                    }
                    list += UnifiedShortcutRowData(
                        key = "static:${sc.packageName}/${sc.className}/${sc.label}",
                        title = sc.label,
                        summary = "${app.label} · ${sc.packageName}",
                        packageName = sc.packageName,
                        shortcutId = sc.className,
                        intentUri = intentUri,
                        badgeKinds = setOf(ShortcutKind.STATIC),
                        isAlreadySaved = isSaved,
                        onExecute = {
                            val intent = if (intentUri.isNotBlank()) {
                                runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()
                            } else {
                                Intent(Intent.ACTION_MAIN).apply {
                                    setClassName(sc.packageName, sc.className)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            if (intent != null) {
                                runCatching { context.startActivity(intent) }
                                    .onSuccess { Toast.makeText(context, execSuccessText, Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(context, execFailedText, Toast.LENGTH_SHORT).show() }
                            }
                        },
                        onCopy = { copyText(intentUri.ifBlank { sc.className }) },
                        onSaveToDirect = { saveDirectShortcut(shortcutTarget) },
                    )
                }
            }
            staticEntries = list
            scanDurationMs = SystemClock.elapsedRealtime() - startedAt
        }
        isLoading = false
    }

    // DYNAMIC 模式加载（若当前已是默认桌面且尚未固化任何记录，自动全量固化）
    LaunchedEffect(testMode, reloadKey, hasHomePermission) {
        if (testMode != ShortcutTestMode.DYNAMIC) return@LaunchedEffect
        hasHomePermission = DynamicShortcutsManager.hasPermission(context)
        if (!hasHomePermission) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val startedAt = SystemClock.elapsedRealtime()
        val discovered = withContext(Dispatchers.IO) {
            DynamicShortcutsManager.queryDynamicShortcuts(context)
                .distinctBy { "${it.packageName}/${it.userSerial}/${it.shortcutId}" }
        }
        dynamicCandidates = discovered
        if (discovered.isNotEmpty() && pinnedRecords.isEmpty()) {
            DynamicShortcutsManager.pinShortcuts(context, discovered)
            AppShortcutLoader.invalidateShortcutCatalog()
            invalidateActionPickerShortcutServiceCache()
            Toast.makeText(
                context,
                "已自动固化全部 ${discovered.size} 项快捷方式",
                Toast.LENGTH_SHORT,
            ).show()
        }
        scanDurationMs = SystemClock.elapsedRealtime() - startedAt
        isLoading = false
    }

    // 根据模式构建统一的展示列表
    val unifiedRows: List<UnifiedShortcutRowData> = remember(
        testMode,
        privilegeEntries,
        staticEntries,
        pinnedRecords,
        savedShortcuts,
    ) {
        when (testMode) {
            ShortcutTestMode.STATIC -> staticEntries
            ShortcutTestMode.PRIVILEGE -> privilegeEntries.mapIndexed { idx, entry ->
                val item = entry.item
                val intentStr = entry.intent.toUri(Intent.URI_INTENT_SCHEME)
                val target = ActivityShortcut.intent(
                    label = item.name.ifBlank { item.title.ifBlank { item.pkg } },
                    packageName = item.pkg,
                    intentUris = listOf(intentStr),
                )
                val isSaved = savedShortcuts.any { it.packageName == item.pkg && it.intentUris.contains(intentStr) }
                val appLabel = runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(item.pkg, 0)).toString()
                }.getOrNull()?.ifBlank { item.name } ?: item.name.ifBlank { item.pkg }
                val title = item.title.ifBlank { item.name.ifBlank { item.pkg } }
                val badgeSet = entry.kinds.ifEmpty { setOf(ShortcutKind.STATIC) }
                UnifiedShortcutRowData(
                    key = "priv:${item.pkg}/${item.shortcut}/$idx",
                    title = title,
                    summary = "$appLabel · ${item.pkg}${if (item.text.isNotBlank() && item.text != title) " · ${item.text}" else ""}",
                    packageName = item.pkg,
                    shortcutId = item.shortcut,
                    intentUri = intentStr,
                    badgeKinds = badgeSet,
                    isAlreadySaved = isSaved,
                    onExecute = { executePrivilegeItem(entry) },
                    onCopy = { copyText(intentStr) },
                    onSaveToDirect = { saveDirectShortcut(target) },
                )
            }
            ShortcutTestMode.DYNAMIC -> pinnedRecords.map { record ->
                val target = DynamicShortcutsManager.toActivityShortcut(record)
                val isSaved = savedShortcuts.any { it.packageName == record.packageName && it.shortcutId == record.shortcutId }
                val recordKey = "${record.packageName}/${record.userSerial}/${record.shortcutId}"
                UnifiedShortcutRowData(
                    key = recordKey,
                    title = record.label,
                    summary = "${record.appLabel} · ${record.packageName}",
                    packageName = record.packageName,
                    shortcutId = record.shortcutId,
                    userSerial = record.userSerial,
                    intentUri = record.intentUris.firstOrNull().orEmpty(),
                    badgeKinds = setOf(ShortcutKind.PINNED),
                    isAlreadySaved = isSaved,
                    onExecute = {
                        val ok = DynamicShortcutsManager.launchShortcut(
                            context = context,
                            packageName = record.packageName,
                            shortcutId = record.shortcutId,
                            userSerial = record.userSerial,
                        )
                        Toast.makeText(context, if (ok) execSuccessText else execFailedText, Toast.LENGTH_SHORT).show()
                    },
                    onCopy = { copyText(record.intentUris.firstOrNull() ?: record.shortcutId) },
                    onSaveToDirect = { saveDirectShortcut(target) },
                )
            }
        }
    }

    val filteredRows = remember(unifiedRows, searchQuery) {
        if (searchQuery.isBlank()) unifiedRows
        else unifiedRows.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true) ||
                it.summary.contains(searchQuery, ignoreCase = true)
        }
    }
    val countTitle = if (testMode == ShortcutTestMode.DYNAMIC) {
        "已固化快捷方式 (${filteredRows.size} 项 · 点击执行，长按复制命令)"
    } else {
        "${stringResource(R.string.shortcut_port_ready, filteredRows.size)} · 点击执行，长按复制命令"
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.shortcut_test_title),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
        hintResId = R.string.shortcut_test_search_hint,
        extraActions = {
            if (testMode == ShortcutTestMode.DYNAMIC && pinnedRecords.isNotEmpty()) {
                MiuixTopBarIconButton(
                    onClick = {
                        isEditMode = !isEditMode
                        if (!isEditMode) selectedEditKeys.clear()
                    },
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (isEditMode) "完成管理" else "编辑管理",
                    )
                }
            }
            MiuixTopBarIconButton(
                onClick = { if (!isLoading) reloadKey++ },
                enabled = !isLoading,
            ) {
                Icon(
                    imageVector = MiuixIcons.Medium.Refresh,
                    contentDescription = stringResource(R.string.shortcut_port_rescan),
                )
            }
        },
    ) {
        // 顶部一级分段控制器：免特权静态 | 特权抓取 | 桌面固化（切换即持久化并联动全局选择器）
        item(key = "shortcut-test-main-mode") {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                SegmentedControl(
                    items = listOf(
                        ShortcutTestMode.STATIC,
                        ShortcutTestMode.PRIVILEGE,
                        ShortcutTestMode.DYNAMIC,
                    ),
                    selected = testMode,
                    labelRes = { mode ->
                        when (mode) {
                            ShortcutTestMode.STATIC -> R.string.shortcut_test_mode_static
                            ShortcutTestMode.PRIVILEGE -> R.string.shortcut_test_mode_privilege
                            ShortcutTestMode.DYNAMIC -> R.string.shortcut_test_mode_dynamic
                        }
                    },
                    onSelect = { mode ->
                        if (testMode != mode) {
                            DynamicShortcutsManager.setTestMode(context, mode)
                            AppShortcutLoader.invalidateShortcutCatalog()
                            invalidateActionPickerShortcutServiceCache()
                            searchQuery = ""
                        }
                    },
                )
            }
        }

        // 针对特权模式下的二级切换 (Shizuku / Root)
        if (testMode == ShortcutTestMode.PRIVILEGE) {
            item(key = "shortcut-port-privilege-submode") {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    SegmentedControl(
                        items = listOf(PrivilegeMode.SHIZUKU, PrivilegeMode.ROOT),
                        selected = privilegeMode,
                        labelRes = { mode ->
                            when (mode) {
                                PrivilegeMode.SHIZUKU -> R.string.shell_panel_mode_shizuku
                                PrivilegeMode.ROOT -> R.string.shell_panel_mode_root
                            }
                        },
                        onSelect = { mode ->
                            if (mode != privilegeMode) {
                                DynamicShortcutsManager.setPrivilegeSubMode(context, mode)
                                AppShortcutLoader.invalidateShortcutCatalog()
                                invalidateActionPickerShortcutServiceCache()
                                hasPermission = false
                                privilegeEntries = emptyList()
                                scanSource = null
                                scanDurationMs = 0L
                                progressText = ""
                                isLoading = true
                            }
                        },
                    )
                }
            }
            settingsLazyTipCard(key = "shortcut-port-tip", text = portTipText)
            item(key = "shortcut-port-status") {
                ShortcutPortStatusCard(
                    hasPermission = hasPermission,
                    isLoading = isLoading,
                    progressText = progressText,
                    entries = privilegeEntries,
                    source = scanSource,
                    durationMs = scanDurationMs,
                    onRescan = { if (!isLoading) reloadKey++ },
                )
            }
        } else if (testMode == ShortcutTestMode.DYNAMIC) {
            settingsLazyTipCard(key = "shortcut-dynamic-tip", text = dynamicTipText)
            groupedCardItems(
                keyPrefix = "dynamic_home_flow",
                items = listOf(
                    settingsCardScopeItem("home_status_hint") {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = "桌面宿主权限",
                            summary = if (hasHomePermission) {
                                "当前已设为默认桌面，已固化 ${pinnedRecords.size} 项动态快捷方式"
                            } else {
                                "已永久固化 ${pinnedRecords.size} 项（免特权随时可用）"
                            },
                            endActions = {
                                HomePermissionStatusPill(isReady = hasHomePermission)
                            },
                        )
                    },
                    settingsCardScopeItem("step_grant_home") {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = if (hasHomePermission) "1. 一键全量扫描并固化全部快捷方式" else "1. 临时设为默认桌面（授权后自动全量固化）",
                            summary = if (hasHomePermission) {
                                "当前已就绪，点击立即将本机全部动态快捷方式一键导入并固化"
                            } else {
                                "点击调起系统默认桌面设置，选中本应用后将自动一键固化本机所有快捷方式"
                            },
                            onClick = {
                                if (hasHomePermission) {
                                    scope.launch { performAutoFullPin() }
                                } else {
                                    waitingAutoPinAfterHomeGrant = true
                                    homeRequestLaunchedAt = SystemClock.elapsedRealtime()
                                    val intent = DynamicShortcutsManager.createRequestHomeIntent(context)
                                    runCatching {
                                        homeRoleLauncher.launch(intent)
                                    }.onFailure {
                                        runCatching {
                                            homeSettingsLauncher.launch(DynamicShortcutsManager.openHomeSettingsIntent())
                                        }.onFailure {
                                            Toast.makeText(context, R.string.dynamic_shortcuts_home_unavailable, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                        )
                    },
                    settingsCardScopeItem("step_restore_home") {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = "2. 恢复原默认桌面",
                            summary = "自动固化完成后点击切回原系统桌面，已固化的快捷方式在全局所有页面中永久免特权生效",
                            onClick = {
                                waitingAutoPinAfterHomeGrant = false
                                val intent = DynamicShortcutsManager.openHomeSettingsIntent()
                                runCatching {
                                    homeSettingsLauncher.launch(intent)
                                }.onFailure {
                                    Toast.makeText(context, R.string.dynamic_shortcuts_home_unavailable, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    },
                    settingsCardScopeItem("step_manual_select") {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = "手动挑选要固化的快捷方式",
                            summary = if (hasHomePermission) "打开弹窗按需勾选要导入固化的快捷方式" else "需在临时设为默认桌面期间使用",
                            enabled = hasHomePermission && !isLoading,
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    val discovered = withContext(Dispatchers.IO) {
                                        DynamicShortcutsManager.queryDynamicShortcuts(context)
                                            .distinctBy { "${it.packageName}/${it.userSerial}/${it.shortcutId}" }
                                    }
                                    dynamicCandidates = discovered
                                    isLoading = false
                                    if (discovered.isEmpty()) {
                                        Toast.makeText(context, R.string.dynamic_shortcuts_empty, Toast.LENGTH_SHORT).show()
                                    } else {
                                        showDynamicImportSheet = true
                                    }
                                }
                            },
                        )
                    },
                )
            )
        } else {
            // 静态模式提示卡片
            settingsLazyTipCard(
                key = "shortcut-static-tip",
                text = staticTipText,
            )
        }

        // 桌面固化编辑模式工具栏
        if (testMode == ShortcutTestMode.DYNAMIC && isEditMode && pinnedRecords.isNotEmpty()) {
            item(key = "shortcut-dynamic-edit-toolbar") {
                MiuixProgressCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixText(
                                text = "编辑模式 · 已选 ${selectedEditKeys.size} / ${filteredRows.size} 项",
                                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val allKeys = filteredRows.map { it.key }
                            val isAllSelected = allKeys.isNotEmpty() && selectedEditKeys.containsAll(allKeys)
                            MiuixTextButton(
                                text = if (isAllSelected) "全不选" else "全选",
                                onClick = {
                                    if (isAllSelected) {
                                        selectedEditKeys.clear()
                                    } else {
                                        selectedEditKeys.clear()
                                        selectedEditKeys.addAll(allKeys)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            MiuixTextButton(
                                text = "删除选中 (${selectedEditKeys.size})",
                                enabled = selectedEditKeys.isNotEmpty(),
                                onClick = {
                                    val toRemove = selectedEditKeys.toSet()
                                    selectedEditKeys.clear()
                                    scope.launch {
                                        DynamicShortcutsManager.removePinnedRecords(context, toRemove)
                                        AppShortcutLoader.invalidateShortcutCatalog()
                                        invalidateActionPickerShortcutServiceCache()
                                        Toast.makeText(context, "已删除 ${toRemove.size} 项固化快捷方式", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1.3f),
                            )
                            MiuixTextButton(
                                text = "完成",
                                onClick = {
                                    isEditMode = false
                                    selectedEditKeys.clear()
                                },
                                modifier = Modifier.weight(0.9f),
                            )
                        }
                    }
                }
            }
        }

        // 统一列表呈现
        if (isLoading) {
            item(key = "shortcut-loading-indicator") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        } else if (filteredRows.isEmpty()) {
            item(key = "shortcut-port-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = emptyText,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            settingsLazySmallTitle(
                key = "shortcut-port-results",
                title = countTitle,
            )
            groupedCardItems(
                keyPrefix = "shortcut-unified-list",
                items = filteredRows.map { row ->
                    settingsCardItem(row.key) {
                        val isDynamicTab = testMode == ShortcutTestMode.DYNAMIC
                        val isSelectedInEdit = row.key in selectedEditKeys
                        UnifiedShortcutPortRow(
                            data = row,
                            isEditMode = isDynamicTab && isEditMode,
                            isSelectedInEdit = isSelectedInEdit,
                            onToggleEditSelect = {
                                if (isSelectedInEdit) selectedEditKeys.remove(row.key)
                                else selectedEditKeys.add(row.key)
                            },
                            onDeleteSingle = {
                                scope.launch {
                                    DynamicShortcutsManager.removePinnedRecords(context, setOf(row.key))
                                    selectedEditKeys.remove(row.key)
                                    AppShortcutLoader.invalidateShortcutCatalog()
                                    invalidateActionPickerShortcutServiceCache()
                                }
                            },
                            onSave = row.onSaveToDirect,
                        )
                    }
                },
            )
        }

        item(key = "shortcut-port-bottom") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    DynamicShortcutSelectionBottomSheet(
        show = showDynamicImportSheet,
        candidates = dynamicCandidates,
        existingPinned = pinnedRecords,
        onDismissRequest = { showDynamicImportSheet = false },
        onConfirmImport = { selected ->
            scope.launch {
                DynamicShortcutsManager.pinShortcuts(context, selected)
                AppShortcutLoader.invalidateShortcutCatalog()
                invalidateActionPickerShortcutServiceCache()
                Toast.makeText(context, "已成功固化并导入 ${selected.size} 项动态快捷方式", Toast.LENGTH_SHORT).show()
            }
        },
    )
}

private val homeSettingsLauncherRef = mutableStateOf<androidx.activity.result.ActivityResultLauncher<Intent>?>(null)

@Composable
private fun HomePermissionStatusPill(isReady: Boolean) {
    val bgColor = if (isReady) {
        Color(0xFF10B981).copy(alpha = 0.15f)
    } else {
        Color(0xFFF59E0B).copy(alpha = 0.15f)
    }
    val contentColor = if (isReady) {
        Color(0xFF059669)
    } else {
        Color(0xFFD97706)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(contentColor),
        )
        MiuixText(
            text = if (isReady) "已就绪" else "未设为默认桌面",
            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
        )
    }
}

@Composable
private fun DynamicShortcutSelectionBottomSheet(
    show: Boolean,
    candidates: List<DynamicShortcutsManager.DiscoveredDynamicShortcut>,
    existingPinned: List<DynamicShortcutsManager.PinnedDynamicShortcutRecord>,
    onDismissRequest: () -> Unit,
    onConfirmImport: (List<DynamicShortcutsManager.DiscoveredDynamicShortcut>) -> Unit,
) {
    if (!show) return

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val listMaxHeight = (screenHeightDp * 0.56f).coerceIn(360f, 540f).dp
    val selectedKeys = remember(candidates) {
        val existingSet = existingPinned.map { "${it.packageName}/${it.userSerial}/${it.shortcutId}" }.toSet()
        mutableStateListOf<String>().apply {
            candidates.forEach { item ->
                val key = "${item.packageName}/${item.userSerial}/${item.shortcutId}"
                if (key !in existingSet) add(key)
            }
            if (isEmpty()) {
                candidates.forEach { item ->
                    add("${item.packageName}/${item.userSerial}/${item.shortcutId}")
                }
            }
        }
    }

    MiuixBottomSheet(
        show = true,
        title = "选择要固化的快捷方式",
        onDismissRequest = onDismissRequest,
    ) {
        val itemShape = RoundedCornerShape(16.dp)
        val primaryColor = MiuixTheme.colorScheme.primary

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiuixText(
                text = "已勾选本机检测到的动态快捷方式，确认后将固化到本地供全局使用。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(
                    items = candidates,
                    key = { "${it.packageName}/${it.userSerial}/${it.shortcutId}" },
                ) { item ->
                    val itemKey = "${item.packageName}/${item.userSerial}/${item.shortcutId}"
                    val isSelected = itemKey in selectedKeys
                    val cardBgColor = if (isSelected) {
                        primaryColor.copy(alpha = 0.12f)
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer
                    }
                    val cardBorderColor = if (isSelected) {
                        primaryColor.copy(alpha = 0.55f)
                    } else {
                        Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .background(cardBgColor)
                            .border(width = 1.dp, color = cardBorderColor, shape = itemShape)
                            .clickable {
                                if (isSelected) selectedKeys.remove(itemKey)
                                else selectedKeys.add(itemKey)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ShortcutPortIcon(packageName = item.packageName, size = 40.dp)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            MiuixText(
                                text = item.label,
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MiuixText(
                                text = "${item.appLabel} · ${item.packageName}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(
                            state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                            onClick = {
                                if (isSelected) selectedKeys.remove(itemKey)
                                else selectedKeys.add(itemKey)
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    text = "已选 ${selectedKeys.size} / ${candidates.size} 项",
                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                    color = primaryColor,
                )
                val selectAllLabel = if (selectedKeys.size == candidates.size && candidates.isNotEmpty()) "全不选" else "全选"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = candidates.isNotEmpty()) {
                            if (selectedKeys.size == candidates.size && candidates.isNotEmpty()) {
                                selectedKeys.clear()
                            } else {
                                selectedKeys.clear()
                                candidates.forEach {
                                    selectedKeys.add("${it.packageName}/${it.userSerial}/${it.shortcutId}")
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    MiuixText(
                        text = selectAllLabel,
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = primaryColor,
                    )
                }
            }

            HorizontalDivider(
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f),
                thickness = 0.5.dp,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (selectedKeys.isNotEmpty()) primaryColor.copy(alpha = 0.16f)
                        else MiuixTheme.colorScheme.surfaceContainer
                    )
                    .clickable(enabled = selectedKeys.isNotEmpty()) {
                        val keySet = selectedKeys.toSet()
                        val selected = candidates.filter {
                            "${it.packageName}/${it.userSerial}/${it.shortcutId}" in keySet
                        }
                        if (selected.isNotEmpty()) {
                            onConfirmImport(selected)
                        }
                        onDismissRequest()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                MiuixText(
                    text = "导入并固化 (${selectedKeys.size})",
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                    color = if (selectedKeys.isNotEmpty()) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ShortcutKindPillBadge(kind: ShortcutKind) {
    val label = when (kind) {
        ShortcutKind.PINNED -> "已固化"
        ShortcutKind.DYNAMIC -> "动态"
        ShortcutKind.STATIC -> "静态"
    }
    val color = when (kind) {
        ShortcutKind.PINNED -> MiuixTheme.colorScheme.primary
        ShortcutKind.DYNAMIC -> Color(0xFF0D9488)
        ShortcutKind.STATIC -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    MiuixText(
        text = label,
        style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Medium),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedShortcutPortRow(
    data: UnifiedShortcutRowData,
    isEditMode: Boolean = false,
    isSelectedInEdit: Boolean = false,
    onToggleEditSelect: () -> Unit = {},
    onDeleteSingle: () -> Unit = {},
    onSave: () -> Unit,
) {
    BasicComponent(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isEditMode) {
                        onToggleEditSelect()
                    } else {
                        data.onExecute()
                    }
                },
                onLongClick = if (isEditMode) null else {
                    { data.onCopy() }
                },
            ),
        title = data.title,
        summary = data.summary,
        startAction = {
            Box(modifier = Modifier.padding(end = 8.dp)) {
                ShortcutPortIcon(packageName = data.packageName, size = 36.dp)
            }
        },
        onClick = null,
        endActions = {
            if (isEditMode) {
                IconButton(onClick = onDeleteSingle) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Checkbox(
                    state = if (isSelectedInEdit) ToggleableState.On else ToggleableState.Off,
                    onClick = onToggleEditSelect,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val orderedKinds = buildList {
                        if (ShortcutKind.STATIC in data.badgeKinds) add(ShortcutKind.STATIC)
                        if (ShortcutKind.DYNAMIC in data.badgeKinds) add(ShortcutKind.DYNAMIC)
                        if (ShortcutKind.PINNED in data.badgeKinds) add(ShortcutKind.PINNED)
                    }
                    orderedKinds.forEach { kind ->
                        ShortcutKindPillBadge(kind = kind)
                    }
                    IconButton(onClick = onSave) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shortcut_bookmark_add),
                            contentDescription = stringResource(R.string.shortcut_test_save_to_direct),
                            tint = if (data.isAlreadySaved) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        },
    )
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

private val iconMemoryCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
private fun ShortcutPortIcon(
    packageName: String,
    size: Dp = 36.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }.coerceAtLeast(1)
    var bitmap by remember(packageName, sizePx) { mutableStateOf(iconMemoryCache[packageName]) }

    LaunchedEffect(packageName, sizePx) {
        if (packageName.isBlank()) return@LaunchedEffect
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
                        ?: ApkTools(context).getAppIcon(packageName)
                    drawable?.toBitmap(sizePx, sizePx)?.asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) {
                iconMemoryCache[packageName] = loaded
                bitmap = loaded
            }
        }
    }

    val image = bitmap
    val iconShape = RoundedCornerShape(10.dp)
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(iconShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(iconShape)
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

private suspend fun shizukuExec(shell: ShizukuShell, command: String): String {
    Log.d("shizuku执行命令", command)
    return shell.runAndGetOutput(command)
}
