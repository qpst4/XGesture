package com.slideindex.app.ui

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutFileSupport
import com.slideindex.app.activity.FileShortcutConfig
import com.slideindex.app.activity.ShortcutIconStorage
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
import com.slideindex.app.ui.miuix.MiuixSwitchRow
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ActivityShortcutEditFileScreen(
    initialShortcut: ActivityShortcut?,
    allShortcuts: List<ActivityShortcut>,
    pickedPackageName: String? = null,
    pickedActivityName: String? = null,
    pickedAppLabel: String? = null,
    onPickerResultConsumed: () -> Unit = {},
    onPickApp: (mimeType: String, isShareMode: Boolean) -> Unit,
    onBack: () -> Unit,
    onSave: (List<ActivityShortcut>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialConfig = remember(initialShortcut?.id) {
        initialShortcut?.let { ActivityShortcutFileSupport.configFrom(it) } ?: FileShortcutConfig(fileUri = "")
    }

    var label by rememberSaveable(initialShortcut?.id) {
        mutableStateOf(initialShortcut?.label ?: context.getString(R.string.activity_shortcut_default_name))
    }
    var subtitle by rememberSaveable(initialShortcut?.id) { mutableStateOf("") }
    var iconPath by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialShortcut?.iconPath) }
    var fileUri by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.fileUri) }
    var mimeType by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.mimeType) }
    var isShareMode by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.isShareMode) }
    var isTextMode by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.isTextMode) }
    var targetPackage by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.targetPackage) }
    var targetActivity by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.targetActivity) }
    var appLabel by rememberSaveable(initialShortcut?.id) { mutableStateOf("") }

    // 处理应用选择回传
    LaunchedEffect(pickedPackageName, pickedActivityName) {
        if (!pickedPackageName.isNullOrBlank()) {
            targetPackage = pickedPackageName
            targetActivity = pickedActivityName.orEmpty()
            if (!pickedAppLabel.isNullOrBlank()) {
                appLabel = pickedAppLabel
            }
            onPickerResultConsumed()
        }
    }

    // SAF 文件选择器
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            fileUri = uri.toString()
            val resolvedType = context.contentResolver.getType(uri)
                ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                    ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                ?: "*/*"
            mimeType = resolvedType
        }
    }

    // 图标选择器
    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    ShortcutIconStorage.saveIconFromUri(context, uri)
                }
                if (path != null) {
                    iconPath = path
                }
            }
        }
    }

    val currentConfig = FileShortcutConfig(
        fileUri = fileUri,
        mimeType = mimeType,
        isShareMode = isShareMode,
        isTextMode = isTextMode,
        targetPackage = targetPackage,
        targetActivity = targetActivity,
    )

    val onTestClick: () -> Unit = {
        if (fileUri.isBlank()) {
            Toast.makeText(context, R.string.activity_shortcut_select_file, Toast.LENGTH_SHORT).show()
        } else {
            runCatching {
                val uri = Uri.parse(fileUri)
                val testIntent = if (isShareMode) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeType.ifBlank { "*/*" }
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(context.contentResolver, "file", uri)
                        if (isTextMode) putExtra(Intent.EXTRA_TEXT, fileUri)
                    }
                } else {
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType.ifBlank { "*/*" })
                    }
                }
                testIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                if (targetPackage.isNotBlank() && targetActivity.isNotBlank()) {
                    testIntent.component = ComponentName(targetPackage, targetActivity)
                } else if (targetPackage.isNotBlank()) {
                    testIntent.setPackage(targetPackage)
                }
                context.startActivity(testIntent)
            }.onFailure {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onSaveClick: () -> Unit = {
        if (label.isBlank()) {
            Toast.makeText(context, R.string.activity_shortcut_error_name, Toast.LENGTH_SHORT).show()
        } else if (fileUri.isBlank()) {
            Toast.makeText(context, R.string.activity_shortcut_select_file, Toast.LENGTH_SHORT).show()
        } else {
            val shortcut = ActivityShortcutFileSupport.toShortcut(
                label = label.trim(),
                config = currentConfig,
                iconPath = iconPath,
                id = initialShortcut?.id ?: java.util.UUID.randomUUID().toString(),
            )
            val updatedList = if (initialShortcut != null) {
                allShortcuts.map { if (it.id == initialShortcut.id) shortcut else it }
            } else {
                allShortcuts + shortcut
            }
            onSave(updatedList)
        }
    }

    SettingsScreenScaffold(
        title = if (initialShortcut == null) {
            stringResource(R.string.activity_shortcut_add_file)
        } else {
            stringResource(R.string.activity_shortcut_edit)
        },
        onBack = onBack,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixTextButton(
                    text = stringResource(R.string.activity_shortcut_test),
                    onClick = onTestClick,
                    enabled = fileUri.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(
                    onClick = onSaveClick,
                    enabled = label.isNotBlank() && fileUri.isNotBlank(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    MiuixText(
                        text = stringResource(R.string.activity_shortcut_save),
                        style = MiuixTheme.textStyles.button,
                    )
                }
            }
        },
    ) {
        LazySettingsItem(key = "file-edit-form") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 名称（图五）
                MiuixLabeledTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = stringResource(R.string.activity_shortcut_default_name),
                    modifier = Modifier.fillMaxWidth(),
                )

                // 图标 + 副标题（图五）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant)
                            .clickable { iconPicker.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        val iconBmp = remember(iconPath) {
                            ShortcutIconStorage.loadBitmap(context, iconPath, 128)
                        }
                        if (iconBmp != null) {
                            Image(
                                bitmap = iconBmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    MiuixLabeledTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = stringResource(R.string.activity_shortcut_subtitle_optional),
                        modifier = Modifier.weight(1f),
                    )
                }

                // 选择应用卡片（图五、图六）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    onClick = { onPickApp(mimeType, isShareMode) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.activity_shortcut_select_app),
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (targetPackage.isNotBlank()) {
                                    if (appLabel.isNotBlank()) appLabel else "$targetPackage/$targetActivity"
                                } else {
                                    stringResource(R.string.activity_shortcut_select_app_desc)
                                },
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // 选择文件卡片（图五、图六）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.activity_shortcut_select_file),
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (fileUri.isBlank()) {
                                IconButton(
                                    onClick = { filePicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { fileUri = "" },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    )
                                }
                            }
                        }

                        if (fileUri.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MiuixTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp),
                            ) {
                                Text(
                                    text = fileUri,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.activity_shortcut_select_file_desc),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            }
        }

        // 选项开关卡片（图五、图六）
        MiuixListSettingsCard(
            keyPrefix = "file-options",
            items = listOf(
                CardItem("text-mode") {
                    MiuixSwitchRow(
                        title = stringResource(R.string.activity_shortcut_text_mode),
                        summary = stringResource(R.string.activity_shortcut_text_mode_desc),
                        checked = isTextMode,
                        onCheckedChange = { isTextMode = it },
                    )
                },
                CardItem("share-mode") {
                    MiuixSwitchRow(
                        title = stringResource(R.string.activity_shortcut_share_mode),
                        summary = stringResource(R.string.activity_shortcut_share_mode_desc),
                        checked = isShareMode,
                        onCheckedChange = {
                            isShareMode = it
                            // 切换模式清空已选应用
                            targetPackage = ""
                            targetActivity = ""
                            appLabel = ""
                        },
                    )
                },
                CardItem("long-press") {
                    ArrowPreference(
                        title = stringResource(R.string.activity_shortcut_long_press_action),
                        summary = stringResource(R.string.activity_shortcut_long_press_edit),
                        onClick = {},
                    )
                },
            ),
        )
    }
}
