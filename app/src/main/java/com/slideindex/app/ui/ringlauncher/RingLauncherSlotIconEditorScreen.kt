package com.slideindex.app.ui.ringlauncher

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.ringlauncher.FvRingLauncherSlotIconStorage
import com.slideindex.app.settings.FvRingLauncherSlotIconOverride
import com.slideindex.app.shell.ShellCommandIconResolver
import com.slideindex.app.util.ImageDecodeGuard
import com.slideindex.app.ui.SettingsScreenScaffold
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.settings.components.LazySettingsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Ok

internal data class RingLauncherSlotIconDraft(
    val iconPath: String? = null,
    val textIcon: String? = null,
    val pendingIconUri: Uri? = null,
) {
    fun toOverride(): FvRingLauncherSlotIconOverride? {
        val override = FvRingLauncherSlotIconOverride(iconPath = iconPath, textIcon = textIcon)
        return override.takeIf { it.isConfigured() }
    }

    fun hasSelection(initial: FvRingLauncherSlotIconOverride?): Boolean =
        pendingIconUri != null ||
            iconPath != null ||
            textIcon != null ||
            initial?.isConfigured() == true
}

internal fun ringLauncherSlotIconDraftFrom(
    override: FvRingLauncherSlotIconOverride?,
): RingLauncherSlotIconDraft = RingLauncherSlotIconDraft(
    iconPath = override?.iconPath,
    textIcon = override?.textIcon,
)

@Composable
internal fun RingLauncherSlotIconEditorHost(
    slotLabel: String,
    initialOverride: FvRingLauncherSlotIconOverride?,
    onBack: () -> Unit,
    onSave: (FvRingLauncherSlotIconOverride?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var iconDraft by remember(initialOverride) { mutableStateOf(ringLauncherSlotIconDraftFrom(initialOverride)) }
    var pickingAppIcon by remember { mutableStateOf(false) }
    var isSavingAppIcon by remember { mutableStateOf(false) }
    var showTextIconDialog by remember { mutableStateOf(false) }
    val pickAppIconFailedMessage = stringResource(R.string.search_engine_pick_app_icon_failed)

    val saveAction: () -> Unit = {
        scope.launch {
            val saved = finalizeSlotIconDraft(
                context = context,
                draft = iconDraft,
                initialIconPath = initialOverride?.iconPath,
            )
            onSave(saved)
        }
    }

    BackHandler(enabled = pickingAppIcon) {
        pickingAppIcon = false
    }

    if (pickingAppIcon) {
        ActivityShortcutPickAppScreen(
            titleResId = R.string.search_engine_pick_app_icon_title,
            selectedPackageName = "",
            // 仅取图标，不需要系统应用候选
            showSystemAppsOption = false,
            onBack = { pickingAppIcon = false },
            onSelectApp = { app ->
                if (isSavingAppIcon) return@ActivityShortcutPickAppScreen
                scope.launch {
                    isSavingAppIcon = true
                    val iconPath = withContext(Dispatchers.IO) {
                        FvRingLauncherSlotIconStorage.saveIconFromPackage(context, app.packageName)
                    }
                    isSavingAppIcon = false
                    if (iconPath != null) {
                        discardSlotPendingIconPath(context, iconDraft.iconPath, initialOverride?.iconPath)
                        iconDraft = iconDraft.copy(
                            pendingIconUri = null,
                            iconPath = iconPath,
                            textIcon = null,
                        )
                        pickingAppIcon = false
                    } else {
                        Toast.makeText(context, pickAppIconFailedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
        return
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.animation_style_custom_icon),
        subtitle = slotLabel.takeIf { it.isNotBlank() },
        onBack = onBack,
        actions = {
            IconButton(onClick = saveAction) {
                Icon(
                    imageVector = MiuixIcons.Medium.Ok,
                    contentDescription = stringResource(R.string.fv_ring_launcher_done),
                )
            }
        },
    ) {
        LazySettingsItem(key = "slot-icon-editor") {
            RingLauncherSlotIconEditorBody(
                slotLabel = slotLabel,
                initialOverride = initialOverride,
                iconDraft = iconDraft,
                onIconDraftChange = { iconDraft = it },
                onPickAppIcon = { pickingAppIcon = true },
                isSavingAppIcon = isSavingAppIcon,
                onShowTextIconDialog = { showTextIconDialog = true },
            )
        }
    }

    if (showTextIconDialog) {
        var textIconInput by remember { mutableStateOf(iconDraft.textIcon.orEmpty()) }
        MiuixFormDialog(
            show = true,
            onDismissRequest = { showTextIconDialog = false },
            title = stringResource(R.string.search_engine_text_icon_title),
            confirmEnabled = textIconInput.trim().isNotEmpty(),
            onConfirm = {
                val trimmed = textIconInput.trim()
                if (trimmed.isNotEmpty()) {
                    discardSlotPendingIconPath(context, iconDraft.iconPath, initialOverride?.iconPath)
                    iconDraft = iconDraft.copy(
                        pendingIconUri = null,
                        iconPath = null,
                        textIcon = trimmed,
                    )
                }
                showTextIconDialog = false
            },
        ) {
            MiuixLabeledTextField(
                value = textIconInput,
                onValueChange = { if (it.length <= 8) textIconInput = it },
                label = stringResource(R.string.search_engine_text_icon_hint),
            )
        }
    }
}

@Composable
private fun RingLauncherSlotIconEditorBody(
    slotLabel: String,
    initialOverride: FvRingLauncherSlotIconOverride?,
    iconDraft: RingLauncherSlotIconDraft,
    onIconDraftChange: (RingLauncherSlotIconDraft) -> Unit,
    onPickAppIcon: () -> Unit,
    isSavingAppIcon: Boolean,
    onShowTextIconDialog: () -> Unit,
) {
    val context = LocalContext.current
    var pendingPreviewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var savedPreviewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

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

    LaunchedEffect(iconDraft.iconPath) {
        savedPreviewBitmap = withContext(Dispatchers.IO) {
            ShellCommandIconResolver.loadUriBitmap(context, iconDraft.iconPath, 128)
        }
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        applyPickedIconUri(
            uri = uri,
            context = context,
            iconDraft = iconDraft,
            initialOverride = initialOverride,
            onDraftChange = onIconDraftChange,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
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
                SmallTitle(
                    text = stringResource(R.string.search_engine_pick_icon),
                    insideMargin = PaddingValues(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = pendingPreviewBitmap ?: savedPreviewBitmap
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = slotLabel,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (!iconDraft.textIcon.isNullOrBlank()) {
                            Text(
                                text = iconDraft.textIcon.orEmpty(),
                                style = MiuixTheme.textStyles.title4,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SlotIconSourceButton(
                                onClick = { iconPicker.launch("image/*") },
                                enabled = true,
                                isLoading = false,
                                icon = MiuixIcons.Image,
                                label = stringResource(R.string.search_engine_pick_icon),
                                modifier = Modifier.weight(1f),
                            )
                            SlotIconSourceButton(
                                onClick = onPickAppIcon,
                                enabled = !isSavingAppIcon,
                                isLoading = isSavingAppIcon,
                                icon = MiuixIcons.GridView,
                                label = stringResource(
                                    if (isSavingAppIcon) {
                                        R.string.search_engine_pick_app_icon_loading
                                    } else {
                                        R.string.search_engine_pick_app_icon
                                    },
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SlotIconSourceButton(
                                onClick = onShowTextIconDialog,
                                enabled = true,
                                isLoading = false,
                                icon = MiuixIcons.File,
                                label = stringResource(R.string.search_engine_text_icon),
                                modifier = Modifier.weight(1f),
                            )
                            if (iconDraft.hasSelection(initialOverride)) {
                                MiuixTextButton(
                                    text = stringResource(R.string.activity_shortcut_reset_icon),
                                    onClick = {
                                        discardSlotPendingIconPath(
                                            context,
                                            iconDraft.iconPath,
                                            initialOverride?.iconPath,
                                        )
                                        onIconDraftChange(RingLauncherSlotIconDraft())
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotIconSourceButton(
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
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
        )
    }
}

private fun discardSlotPendingIconPath(
    context: android.content.Context,
    pendingIconPath: String?,
    initialIconPath: String?,
) {
    pendingIconPath?.takeIf { it != initialIconPath }?.let { path ->
        FvRingLauncherSlotIconStorage.deleteIconIfOwned(context, path)
    }
}

private suspend fun finalizeSlotIconDraft(
    context: android.content.Context,
    draft: RingLauncherSlotIconDraft,
    initialIconPath: String?,
): FvRingLauncherSlotIconOverride? {
    val pendingUri = draft.pendingIconUri
    val savedPath = if (pendingUri != null) {
        withContext(Dispatchers.IO) {
            FvRingLauncherSlotIconStorage.saveIconFromUri(context, pendingUri)
        }
    } else {
        draft.iconPath
    }
    if (savedPath != null && savedPath != initialIconPath) {
        FvRingLauncherSlotIconStorage.deleteIconIfOwned(context, initialIconPath)
    }
    val override = FvRingLauncherSlotIconOverride(
        iconPath = savedPath,
        textIcon = draft.textIcon,
    )
    return override.takeIf { it.isConfigured() }
}

private fun applyPickedIconUri(
    uri: Uri,
    context: android.content.Context,
    iconDraft: RingLauncherSlotIconDraft,
    initialOverride: FvRingLauncherSlotIconOverride?,
    onDraftChange: (RingLauncherSlotIconDraft) -> Unit,
) {
    discardSlotPendingIconPath(context, iconDraft.iconPath, initialOverride?.iconPath)
    onDraftChange(
        iconDraft.copy(
            pendingIconUri = uri,
            iconPath = null,
            textIcon = null,
        ),
    )
}
