package com.slideindex.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutKind
import com.slideindex.app.activity.ActivityShortcutParams
import com.slideindex.app.activity.ActivityShortcutShellSupport
import com.slideindex.app.activity.ManagedShortcutIconResolver
import com.slideindex.app.activity.ShortcutIconStorage
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.viewmodel.ActivityShortcutPickerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 「应用内直达」参数编辑页：直接改包名 / 类名 / 快捷方式 ID / Shell 命令 / Intent URI，
 * 不走重新挑选的流程。
 *
 * 类型由条目现有字段决定，只读；保存时按类型清空其它字段（见 [ActivityShortcutParams.build]）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutEditScreen(
    shortcut: ActivityShortcut,
    allShortcuts: List<ActivityShortcut>,
    pickerResult: ActivityShortcutPickerResult?,
    onPickerResultConsumed: () -> Unit,
    onPickApp: () -> Unit,
    onPickActivity: (packageName: String) -> Unit,
    onPickIconApp: () -> Unit,
    onTest: (ActivityShortcut) -> Unit,
    onBack: () -> Unit,
    onSave: (List<ActivityShortcut>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kind = shortcut.kind
    val isShell = remember(shortcut.id) { ActivityShortcutShellSupport.isShellShortcut(shortcut) }
    val isAppShortcut = shortcut.isAppShortcut

    var label by rememberSaveable(shortcut.id) { mutableStateOf(shortcut.label) }
    var packageName by rememberSaveable(shortcut.id) { mutableStateOf(shortcut.packageName) }
    var activityClassName by rememberSaveable(shortcut.id) { mutableStateOf(shortcut.activityClassName) }
    var shortcutId by rememberSaveable(shortcut.id) { mutableStateOf(shortcut.shortcutId) }
    var iconPath by rememberSaveable(shortcut.id) { mutableStateOf(shortcut.iconPath) }
    var shellCommand by rememberSaveable(shortcut.id) {
        mutableStateOf(
            ActivityShortcutShellSupport.decodeCommand(shortcut.intentUris.firstOrNull().orEmpty()),
        )
    }
    var intentUrisText by rememberSaveable(shortcut.id) {
        mutableStateOf(if (isShell) "" else shortcut.intentUris.joinToString("\n"))
    }

    LaunchedEffect(pickerResult) {
        val result = pickerResult ?: return@LaunchedEffect
        result.iconPackageName?.let { packageName ->
            val path = withContext(Dispatchers.IO) {
                ShortcutIconStorage.saveIconFromPackage(context, packageName)
            }
            if (path != null) {
                iconPath = path
            }
        }
        result.packageName?.let { newPackage ->
            if (newPackage != packageName) {
                packageName = newPackage
                activityClassName = result.activityClassName.orEmpty()
            } else {
                result.activityClassName?.let { activityClassName = it }
            }
        } ?: result.activityClassName?.let { activityClassName = it }
        onPickerResultConsumed()
    }

    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                ShortcutIconStorage.saveIconFromUri(context, uri)
            } ?: return@launch
            // 旧图不在这里删：用户可能取消保存；等 commitSave 再清理。
            iconPath = path
        }
    }

    val intentUris = remember(intentUrisText) { ActivityShortcutParams.parseIntentUris(intentUrisText) }
    val inferredHost = remember(intentUris) {
        intentUris.firstNotNullOfOrNull(::intentHostPackage)
    }
    val effectivePackage = if (kind == ActivityShortcutKind.INTENT && !isShell) {
        packageName.trim().ifBlank { inferredHost.orEmpty() }
    } else {
        packageName.trim()
    }

    val draft = ActivityShortcutParams.build(
        base = shortcut,
        label = label,
        packageName = if (kind == ActivityShortcutKind.INTENT && !isShell) effectivePackage else packageName,
        activityClassName = activityClassName,
        shortcutId = shortcutId,
        shellCommand = shellCommand,
        intentUris = intentUris,
        isShell = isShell,
        isAppShortcut = isAppShortcut,
    ).copy(iconPath = iconPath)

    val nameInvalid = label.isBlank()
    val packageValue = packageName.trim()
    val classValue = activityClassName.trim()
    val packageInvalid = when {
        isShell -> false
        isAppShortcut || kind == ActivityShortcutKind.COMPONENT ->
            packageValue.isEmpty() || packageValue.any { it.isWhitespace() || it == '/' }
        else ->
            effectivePackage.isEmpty() ||
                effectivePackage.any { it.isWhitespace() || it == '/' }
    }
    val activityInvalid = kind == ActivityShortcutKind.COMPONENT && (
        classValue.isEmpty() ||
            classValue.any { it.isWhitespace() || it == '/' }
        )
    val shortcutIdInvalid = isAppShortcut && shortcutId.isBlank()
    val commandInvalid = isShell && shellCommand.isBlank()
    val intentEmpty = kind == ActivityShortcutKind.INTENT && !isShell && !isAppShortcut && intentUris.isEmpty()
    val invalidUriLine = if (kind == ActivityShortcutKind.INTENT && !isShell && !isAppShortcut) {
        intentUris.indexOfFirst { !isValidIntentUri(it) }.takeIf { it >= 0 }?.plus(1)
    } else {
        null
    }
    val hasTargetError = packageInvalid || activityInvalid ||
        shortcutIdInvalid || commandInvalid || intentEmpty || invalidUriLine != null
    val hasError = nameInvalid || hasTargetError

    val initialValues = remember(shortcut.id) {
        listOf(
            shortcut.label,
            shortcut.packageName,
            shortcut.activityClassName,
            shortcut.shortcutId,
            ActivityShortcutShellSupport.decodeCommand(shortcut.intentUris.firstOrNull().orEmpty()),
            if (isShell) "" else shortcut.intentUris.joinToString("\n"),
            shortcut.iconPath,
        )
    }
    val currentValues = listOf(
        label,
        packageName,
        activityClassName,
        shortcutId,
        shellCommand,
        intentUrisText,
        iconPath,
    )
    val isDirty = currentValues != initialValues

    var previewBitmap by remember(shortcut.id) { mutableStateOf<Bitmap?>(null) }
    var previewTitle by remember(shortcut.id) { mutableStateOf("") }
    var previewIssues by remember(shortcut.id) { mutableStateOf<List<ShortcutIssue>>(emptyList()) }
    var resolving by remember(shortcut.id) { mutableStateOf(false) }

    LaunchedEffect(draft, hasError) {
        if (hasError) {
            previewBitmap = null
            previewTitle = ""
            previewIssues = emptyList()
            resolving = false
            return@LaunchedEffect
        }
        resolving = true
        delay(300)
        val preview = withContext(Dispatchers.IO) { resolvePreview(context, draft) }
        previewBitmap = preview.bitmap
        previewTitle = preview.title
        previewIssues = preview.issues
        resolving = false
    }

    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun commitSave(updated: ActivityShortcut, duplicate: ActivityShortcut?) {
        val next = if (duplicate == null) {
            ActivityShortcutParams.replace(allShortcuts, updated)
        } else {
            val merged = ActivityShortcutParams.merge(updated, duplicate)
            val iconStillUsed = allShortcuts.any {
                it.id != duplicate.id && it.id != shortcut.id && it.iconPath == duplicate.iconPath
            }
            if (duplicate.iconPath != null &&
                duplicate.iconPath != merged.iconPath &&
                !iconStillUsed
            ) {
                ShortcutIconStorage.deleteIconIfOwned(context, duplicate.iconPath)
            }
            allShortcuts
                .filterNot { it.id == duplicate.id }
                .map { if (it.id == shortcut.id) merged else it }
        }
        val savedIcon = next.firstOrNull { it.id == shortcut.id }?.iconPath
        if (shortcut.iconPath != null && shortcut.iconPath != savedIcon) {
            val iconStillUsed = next.any {
                it.id != shortcut.id && it.iconPath == shortcut.iconPath
            }
            if (!iconStillUsed) {
                ShortcutIconStorage.deleteIconIfOwned(context, shortcut.iconPath)
            }
        }
        onSave(next)
    }

    val requestSave: () -> Unit = {
        if (!hasError) {
            val updated = draft
            val duplicate = ActivityShortcutParams.duplicateOf(allShortcuts, updated)
            pendingSave = PendingSave(updated, duplicate)
            when {
                duplicate != null -> showDuplicateDialog = true
                collectIssues(context, updated).isNotEmpty() -> showWarningDialog = true
                else -> commitSave(updated, null)
            }
        }
    }

    val handleBack: () -> Unit = {
        if (isDirty) showDiscardDialog = true else onBack()
    }

    val typeLabel = stringResource(
        when {
            isShell -> R.string.activity_shortcut_type_shell
            isAppShortcut -> R.string.activity_shortcut_type_dynamic
            kind == ActivityShortcutKind.COMPONENT -> R.string.activity_shortcut_type_component
            else -> R.string.activity_shortcut_type_intent
        },
    )

    SettingsScreenScaffold(
        title = stringResource(R.string.activity_shortcut_edit_title),
        onBack = handleBack,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onTest(draft.copy(label = draft.label.ifBlank { shortcut.label }))
                    },
                    enabled = !hasTargetError,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.activity_shortcut_test))
                }
                Button(
                    onClick = requestSave,
                    enabled = !hasError,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.activity_shortcut_save))
                }
            }
        },
    ) {
        LazySettingsItem(key = "activity-shortcut-edit-form") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShortcutPreviewRow(
                            bitmap = previewBitmap,
                            title = previewTitle.ifBlank { label },
                            typeLabel = typeLabel,
                            resolving = resolving,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconSourceAction(
                                icon = MiuixIcons.Image,
                                label = stringResource(R.string.activity_shortcut_icon_from_gallery),
                                onClick = { iconPicker.launch("image/*") },
                                modifier = Modifier.weight(1f),
                            )
                            IconSourceAction(
                                icon = MiuixIcons.GridView,
                                label = stringResource(R.string.activity_shortcut_icon_from_app),
                                onClick = onPickIconApp,
                                modifier = Modifier.weight(1f),
                            )
                            IconSourceAction(
                                icon = Icons.Default.Refresh,
                                label = stringResource(R.string.activity_shortcut_reset_icon),
                                onClick = { iconPath = null },
                                enabled = iconPath != null,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        MiuixLabeledTextField(
                            value = label,
                            onValueChange = { label = ActivityShortcutParams.stripControlChars(it) },
                            label = stringResource(R.string.activity_shortcut_name_hint),
                        )
                        if (nameInvalid) {
                            FieldError(stringResource(R.string.activity_shortcut_error_name))
                        }

                        when {
                            isShell -> {
                                MiuixLabeledTextField(
                                    value = shellCommand,
                                    onValueChange = {
                                        shellCommand = ActivityShortcutParams.stripControlChars(it)
                                    },
                                    label = stringResource(R.string.activity_shortcut_field_command),
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 4,
                                )
                                if (commandInvalid) {
                                    FieldError(stringResource(R.string.activity_shortcut_error_command))
                                }
                            }

                            isAppShortcut -> {
                                MiuixLabeledTextField(
                                    value = packageName,
                                    onValueChange = {
                                        packageName = ActivityShortcutParams.stripControlChars(it)
                                    },
                                    label = stringResource(R.string.activity_shortcut_field_package),
                                )
                                if (packageInvalid) {
                                    FieldError(stringResource(R.string.activity_shortcut_error_package))
                                }
                                MiuixLabeledTextField(
                                    value = shortcutId,
                                    onValueChange = {
                                        shortcutId = ActivityShortcutParams.stripControlChars(it)
                                    },
                                    label = stringResource(R.string.activity_shortcut_field_shortcut_id),
                                )
                                if (shortcutIdInvalid) {
                                    FieldError(stringResource(R.string.activity_shortcut_error_shortcut_id))
                                }
                            }

                            kind == ActivityShortcutKind.COMPONENT -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    MiuixLabeledTextField(
                                        value = packageName,
                                        onValueChange = { input ->
                                            val split = ActivityShortcutParams.splitComponent(input)
                                            if (split != null) {
                                                packageName = split.first
                                                activityClassName = split.second
                                            } else {
                                                packageName = ActivityShortcutParams.stripControlChars(input)
                                            }
                                        },
                                        label = stringResource(R.string.activity_shortcut_field_package),
                                        modifier = Modifier.weight(1f),
                                    )
                                    MiuixTextButton(
                                        text = stringResource(R.string.activity_shortcut_pick_app),
                                        onClick = onPickApp,
                                    )
                                }
                                if (packageInvalid) {
                                    FieldError(stringResource(R.string.activity_shortcut_error_package))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    MiuixLabeledTextField(
                                        value = activityClassName,
                                        onValueChange = {
                                            activityClassName = ActivityShortcutParams.stripControlChars(it)
                                        },
                                        label = stringResource(R.string.activity_shortcut_field_activity_class),
                                        modifier = Modifier.weight(1f),
                                    )
                                    MiuixTextButton(
                                        text = stringResource(R.string.activity_shortcut_pick_activity),
                                        onClick = { onPickActivity(packageName.trim()) },
                                        enabled = packageName.isNotBlank(),
                                    )
                                }
                                if (activityInvalid) {
                                    FieldError(stringResource(R.string.activity_shortcut_error_activity))
                                }
                            }

                            else -> {
                                MiuixLabeledTextField(
                                    value = packageName,
                                    onValueChange = {
                                        packageName = ActivityShortcutParams.stripControlChars(it)
                                    },
                                    label = stringResource(R.string.activity_shortcut_field_host_package),
                                )
                                if (packageInvalid) {
                                    FieldError(stringResource(R.string.activity_shortcut_error_host))
                                }
                                MiuixLabeledTextField(
                                    value = intentUrisText,
                                    onValueChange = {
                                        intentUrisText = ActivityShortcutParams.stripControlChars(it)
                                    },
                                    label = stringResource(R.string.activity_shortcut_field_intent_uris),
                                    singleLine = false,
                                    minLines = 4,
                                    maxLines = 10,
                                )
                                if (intentEmpty) {
                                    FieldError(stringResource(R.string.activity_shortcut_error_intent_empty))
                                }
                                invalidUriLine?.let { line ->
                                    FieldError(
                                        stringResource(R.string.activity_shortcut_error_intent_line, line),
                                    )
                                }
                            }
                        }

                        previewIssues.forEach { issue ->
                            IssueText(issue = issue)
                        }
                    }
                }
            }
        }
    }

    if (showDuplicateDialog) {
        val duplicate = pendingSave?.duplicate
        val duplicateLabel = duplicate?.let { it.label.ifBlank { it.packageName } }.orEmpty()
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.activity_shortcut_duplicate_title),
            message = stringResource(
                R.string.activity_shortcut_duplicate_message,
                duplicateLabel,
            ),
            confirmText = stringResource(R.string.activity_shortcut_merge),
            onConfirm = {
                showDuplicateDialog = false
                pendingSave?.let { pending ->
                    if (collectIssues(context, pending.updated).isNotEmpty()) {
                        showWarningDialog = true
                    } else {
                        commitSave(pending.updated, pending.duplicate)
                    }
                }
            },
            onDismissRequest = { showDuplicateDialog = false },
        )
    }

    if (showWarningDialog) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.activity_shortcut_warning_save_title),
            message = stringResource(R.string.activity_shortcut_warning_save_message),
            confirmText = stringResource(R.string.activity_shortcut_save_anyway),
            onConfirm = {
                showWarningDialog = false
                pendingSave?.let { commitSave(it.updated, it.duplicate) }
            },
            onDismissRequest = { showWarningDialog = false },
        )
    }

    if (showDiscardDialog) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.activity_shortcut_discard_title),
            message = stringResource(R.string.activity_shortcut_discard_message),
            confirmText = stringResource(R.string.activity_shortcut_discard_confirm),
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
            onDismissRequest = { showDiscardDialog = false },
        )
    }
}

private data class PendingSave(
    val updated: ActivityShortcut,
    val duplicate: ActivityShortcut?,
)

private data class ShortcutPreview(
    val bitmap: Bitmap?,
    val title: String,
    val issues: List<ShortcutIssue>,
)

private enum class ShortcutIssue {
    ACTIVITY_MISSING,
    NOT_EXPORTED,
    PACKAGE_MISSING,
    SHORTCUT_MISSING,
    INTENT_UNHANDLED,
}

@Composable
private fun ShortcutPreviewRow(
    bitmap: Bitmap?,
    title: String,
    typeLabel: String,
    resolving: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            when {
                resolving -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )

                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )

                else -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.Shortcut,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            Text(
                text = typeLabel,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun IconSourceAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MiuixButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
        )
    }
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.error,
    )
}

@Composable
private fun IssueText(issue: ShortcutIssue) {
    Text(
        text = when (issue) {
            ShortcutIssue.ACTIVITY_MISSING ->
                stringResource(R.string.activity_shortcut_warning_activity_missing)

            ShortcutIssue.NOT_EXPORTED ->
                stringResource(R.string.activity_shortcut_not_exported)

            ShortcutIssue.PACKAGE_MISSING ->
                stringResource(R.string.activity_shortcut_warning_package_missing)

            ShortcutIssue.SHORTCUT_MISSING ->
                stringResource(R.string.activity_shortcut_warning_shortcut_missing)

            ShortcutIssue.INTENT_UNHANDLED ->
                stringResource(R.string.activity_shortcut_warning_intent_unhandled)
        },
        style = MiuixTheme.textStyles.body2,
        color = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFB26A00),
    )
}

private fun isValidIntentUri(uri: String): Boolean =
    runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.isSuccess

private fun intentHostPackage(uri: String): String? = runCatching {
    val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
    intent.`package`?.takeIf { it.isNotBlank() }
        ?: intent.component?.packageName?.takeIf { it.isNotBlank() }
}.getOrNull()

private fun resolvePreview(context: Context, shortcut: ActivityShortcut): ShortcutPreview {
    val bitmap = runCatching {
        ManagedShortcutIconResolver.drawableForManaged(context, shortcut)?.toBitmap(96, 96)
    }.getOrNull()
    return ShortcutPreview(
        bitmap = bitmap,
        title = resolveTitle(context, shortcut),
        issues = collectIssues(context, shortcut),
    )
}

private fun resolveTitle(context: Context, shortcut: ActivityShortcut): String {
    val pm = context.packageManager
    if (shortcut.isAppShortcut) {
        return queryShortcut(context, shortcut.packageName, shortcut.shortcutId)
            ?.let { info ->
                runCatching { info.longLabel?.toString() ?: info.shortLabel?.toString() }.getOrNull()
            }
            ?.takeIf { it.isNotBlank() }
            ?: shortcut.label
    }
    return when (shortcut.kind) {
        ActivityShortcutKind.COMPONENT -> runCatching {
            pm.getActivityInfo(
                ComponentName(shortcut.packageName, shortcut.activityClassName),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            ).loadLabel(pm).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: shortcut.label

        ActivityShortcutKind.DYNAMIC -> queryShortcut(
            context,
            shortcut.packageName,
            shortcut.shortcutId,
        )?.let { info ->
            runCatching { info.longLabel?.toString() ?: info.shortLabel?.toString() }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: shortcut.label

        ActivityShortcutKind.INTENT -> {
            if (ActivityShortcutShellSupport.isShellShortcut(shortcut)) {
                shortcut.label
            } else {
                val resolved = shortcut.intentUris.firstOrNull()
                    ?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
                    ?.let { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) }
                resolved?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: shortcut.label
            }
        }
    }
}

private fun collectIssues(context: Context, shortcut: ActivityShortcut): List<ShortcutIssue> {
    val pm = context.packageManager
    if (shortcut.isAppShortcut) {
        return if (queryShortcut(context, shortcut.packageName, shortcut.shortcutId) == null) {
            listOf(ShortcutIssue.SHORTCUT_MISSING)
        } else {
            emptyList()
        }
    }
    return when (shortcut.kind) {
        ActivityShortcutKind.COMPONENT -> {
            if (shortcut.packageName.isBlank() || shortcut.activityClassName.isBlank()) {
                return listOf(ShortcutIssue.ACTIVITY_MISSING)
            }
            if (runCatching { pm.getApplicationInfo(shortcut.packageName, 0) }.isFailure) {
                return listOf(ShortcutIssue.PACKAGE_MISSING)
            }
            val info = runCatching {
                pm.getActivityInfo(
                    ComponentName(shortcut.packageName, shortcut.activityClassName),
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }.getOrNull() ?: return listOf(ShortcutIssue.ACTIVITY_MISSING)
            if (info.exported) emptyList() else listOf(ShortcutIssue.NOT_EXPORTED)
        }

        ActivityShortcutKind.DYNAMIC -> {
            if (queryShortcut(context, shortcut.packageName, shortcut.shortcutId) == null) {
                listOf(ShortcutIssue.SHORTCUT_MISSING)
            } else {
                emptyList()
            }
        }

        ActivityShortcutKind.INTENT -> {
            if (ActivityShortcutShellSupport.isShellShortcut(shortcut)) return emptyList()
            if (shortcut.intentUris.isEmpty()) return listOf(ShortcutIssue.INTENT_UNHANDLED)
            val unresolved = shortcut.intentUris.any { uri ->
                runCatching {
                    val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                    pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null
                }.getOrDefault(true)
            }
            if (unresolved) listOf(ShortcutIssue.INTENT_UNHANDLED) else emptyList()
        }
    }
}

private fun queryShortcut(
    context: Context,
    packageName: String,
    shortcutId: String,
): ShortcutInfo? {
    if (packageName.isBlank() || shortcutId.isBlank()) return null
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        ?: return null
    return runCatching {
        launcherApps.getShortcuts(
            LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setShortcutIds(listOf(shortcutId))
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            },
            Process.myUserHandle(),
        )?.firstOrNull()
    }.getOrNull()
}
