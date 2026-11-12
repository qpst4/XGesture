package com.slideindex.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconStorage
import com.slideindex.app.shell.ShellCommandIconType
import com.slideindex.app.util.ImageDecodeGuard
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class ShellCommandIconDraft(
    val iconType: ShellCommandIconType = ShellCommandIconType.OTHER,
    val iconPath: String? = null,
    val textIcon: String? = null,
    val pendingIconUri: Uri? = null,
)

internal fun buildShellCommandPreview(
    label: String,
    iconDraft: ShellCommandIconDraft,
    initial: ShellCommand?,
): ShellCommand =
    ShellCommand(
        id = initial?.id.orEmpty().ifBlank { "preview" },
        label = label.ifBlank { initial?.label.orEmpty().ifBlank { "?" } },
        command = initial?.command.orEmpty(),
        iconType = when {
            iconDraft.pendingIconUri != null || iconDraft.iconPath != null -> ShellCommandIconType.URI
            iconDraft.textIcon != null -> ShellCommandIconType.TEXT
            else -> iconDraft.iconType
        },
        iconPath = iconDraft.iconPath,
        textIcon = iconDraft.textIcon,
    )

internal fun finalizeShellCommandIconDraft(
    context: android.content.Context,
    initial: ShellCommand?,
    draft: ShellCommandIconDraft,
): ShellCommandIconDraft {
    if (draft.iconType == ShellCommandIconType.OTHER &&
        draft.iconPath == null &&
        draft.textIcon == null &&
        draft.pendingIconUri == null
    ) {
        ShellCommandIconStorage.deleteIconIfOwned(context, initial?.iconPath)
        return ShellCommandIconDraft(iconType = ShellCommandIconType.OTHER)
    }
    val savedPath = draft.pendingIconUri?.let { uri ->
        ShellCommandIconStorage.saveIconFromUri(context, uri)
    } ?: draft.iconPath
    if (savedPath != null && savedPath != initial?.iconPath) {
        ShellCommandIconStorage.deleteIconIfOwned(context, initial?.iconPath)
    }
    if (savedPath != null) {
        return ShellCommandIconDraft(
            iconType = ShellCommandIconType.URI,
            iconPath = savedPath,
            textIcon = null,
        )
    }
    if (!draft.textIcon.isNullOrBlank()) {
        ShellCommandIconStorage.deleteIconIfOwned(context, initial?.iconPath)
        return ShellCommandIconDraft(
            iconType = ShellCommandIconType.TEXT,
            textIcon = draft.textIcon,
            iconPath = null,
        )
    }
    ShellCommandIconStorage.deleteIconIfOwned(context, initial?.iconPath)
    return ShellCommandIconDraft(iconType = ShellCommandIconType.OTHER)
}

internal fun discardShellPendingIconPath(
    context: android.content.Context,
    pendingIconPath: String?,
    initialIconPath: String?,
) {
    pendingIconPath?.takeIf { it != initialIconPath }?.let { path ->
        ShellCommandIconStorage.deleteIconIfOwned(context, path)
    }
}

@Composable
internal fun ShellCommandAppIconPickerScreen(
    initial: ShellCommand?,
    iconDraft: ShellCommandIconDraft,
    isSavingAppIcon: Boolean,
    onBack: () -> Unit,
    onIconDraftChange: (ShellCommandIconDraft) -> Unit,
    onSavingChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickAppIconFailedMessage = stringResource(R.string.search_engine_pick_app_icon_failed)

    ActivityShortcutPickAppScreen(
        titleResId = R.string.search_engine_pick_app_icon_title,
        selectedPackageName = "",
        // 仅取图标，不需要系统应用候选
        showSystemAppsOption = false,
        onBack = onBack,
        onSelectApp = { app ->
            if (isSavingAppIcon) return@ActivityShortcutPickAppScreen
            scope.launch {
                onSavingChange(true)
                val iconPath = withContext(Dispatchers.IO) {
                    ShellCommandIconStorage.saveIconFromPackage(context, app.packageName)
                }
                onSavingChange(false)
                if (iconPath != null) {
                    discardShellPendingIconPath(context, iconDraft.iconPath, initial?.iconPath)
                    onIconDraftChange(
                        iconDraft.copy(
                            pendingIconUri = null,
                            iconPath = iconPath,
                            textIcon = null,
                            iconType = ShellCommandIconType.URI,
                        ),
                    )
                    onBack()
                } else {
                    Toast.makeText(context, pickAppIconFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

@Composable
internal fun ShellCommandEditorIconSection(
    label: String,
    onLabelChange: (String) -> Unit,
    initial: ShellCommand?,
    iconDraft: ShellCommandIconDraft,
    onIconDraftChange: (ShellCommandIconDraft) -> Unit,
    onPickAppIcon: () -> Unit,
    modifier: Modifier = Modifier,
    isSavingAppIcon: Boolean = false,
    useWindowMenu: Boolean = false,
) {
    val context = LocalContext.current
    var showTextIconDialog by remember { mutableStateOf(false) }
    val preview = remember(label, iconDraft, initial?.id) {
        buildShellCommandPreview(label, iconDraft, initial)
    }
    var pendingPreviewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(iconDraft.pendingIconUri) {
        pendingPreviewBitmap = iconDraft.pendingIconUri?.let { uri ->
            withContext(Dispatchers.IO) {
                ImageDecodeGuard.decodeUri(
                    context = context,
                    uri = uri,
                    maxSidePx = ImageDecodeGuard.MAX_ICON_SIDE_PX,
                )
            }
        }
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        discardShellPendingIconPath(context, iconDraft.iconPath, initial?.iconPath)
        onIconDraftChange(
            iconDraft.copy(
                pendingIconUri = uri,
                iconPath = null,
                textIcon = null,
                iconType = ShellCommandIconType.URI,
            ),
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pickImageText = stringResource(R.string.search_engine_pick_icon)
            val pickAppText = stringResource(R.string.search_engine_pick_app_icon)
            val textIconText = stringResource(R.string.search_engine_text_icon)
            val resetIconText = stringResource(R.string.shell_panel_reset_icon)
            val menuItems = buildList {
                add(
                    DropdownItem(
                        text = pickImageText,
                        onClick = { iconPicker.launch("image/*") },
                    ),
                )
                add(
                    DropdownItem(
                        text = pickAppText,
                        onClick = { onPickAppIcon() },
                    ),
                )
                add(
                    DropdownItem(
                        text = textIconText,
                        onClick = { showTextIconDialog = true },
                    ),
                )
                if (iconDraft.hasSelection(initial)) {
                    add(
                        DropdownItem(
                            text = resetIconText,
                            onClick = {
                                discardShellPendingIconPath(context, iconDraft.iconPath, initial?.iconPath)
                                onIconDraftChange(ShellCommandIconDraft())
                            },
                        ),
                    )
                }
            }

            val iconButtonContent: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val pendingBitmap = pendingPreviewBitmap
                    if (pendingBitmap != null) {
                        Image(
                            bitmap = pendingBitmap.asImageBitmap(),
                            contentDescription = label,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        ShellCommandIcon(
                            command = preview,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (isSavingAppIcon) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixCircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (useWindowMenu) {
                WindowIconDropdownMenu(
                    entry = DropdownEntry(items = menuItems),
                    modifier = Modifier.size(56.dp),
                    minWidth = 56.dp,
                    minHeight = 56.dp,
                    cornerRadius = 16.dp,
                    backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                    maxHeight = 320.dp,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                ) {
                    iconButtonContent()
                }
            } else {
                OverlayIconDropdownMenu(
                    entry = DropdownEntry(items = menuItems),
                    modifier = Modifier.size(56.dp),
                    minWidth = 56.dp,
                    minHeight = 56.dp,
                    cornerRadius = 16.dp,
                    backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                    maxHeight = 320.dp,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                ) {
                    iconButtonContent()
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MiuixLabeledTextField(
                    value = label,
                    onValueChange = onLabelChange,
                    label = stringResource(R.string.shell_panel_label_field),
                    useLabelAsPlaceholder = false,
                )
            }
        }
    }

    if (showTextIconDialog) {
        ShellTextIconDialog(
            initialText = iconDraft.textIcon ?: label.take(1),
            onDismiss = { showTextIconDialog = false },
            onConfirm = { text ->
                discardShellPendingIconPath(context, iconDraft.iconPath, initial?.iconPath)
                onIconDraftChange(
                    iconDraft.copy(
                        pendingIconUri = null,
                        iconPath = null,
                        textIcon = text,
                        iconType = ShellCommandIconType.TEXT,
                    ),
                )
                showTextIconDialog = false
            },
        )
    }
}

private fun ShellCommandIconDraft.hasSelection(initial: ShellCommand?): Boolean =
    pendingIconUri != null ||
        iconPath != null ||
        textIcon != null ||
        initial?.hasCustomIcon() == true

@Composable
private fun ShellIconSourceButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    MiuixButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
    ) {
        if (isLoading) {
            MiuixCircularProgressIndicator(modifier = Modifier.size(18.dp))
        } else {
            MiuixIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        MiuixText(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShellTextIconDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember(initialText) { mutableStateOf(initialText) }
    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.search_engine_text_icon_title),
        confirmEnabled = input.trim().isNotEmpty(),
        onConfirm = { onConfirm(input.trim()) },
    ) {
        MiuixLabeledTextField(
            value = input,
            onValueChange = { if (it.length <= 8) input = it },
            label = stringResource(R.string.search_engine_text_icon_hint),
        )
        Text(
            text = stringResource(R.string.search_engine_text_icon_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun shellCommandIconDraftFrom(initial: ShellCommand?): ShellCommandIconDraft =
    ShellCommandIconDraft(
        iconType = initial?.iconType ?: ShellCommandIconType.OTHER,
        iconPath = initial?.iconPath,
        textIcon = initial?.textIcon,
    )

internal fun applyIconDraft(base: ShellCommand, draft: ShellCommandIconDraft): ShellCommand =
    base.copy(
        iconType = draft.iconType,
        iconPath = draft.iconPath,
        textIcon = draft.textIcon,
    )
