package com.slideindex.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutImageSupport
import com.slideindex.app.activity.ImageShortcutConfig
import com.slideindex.app.activity.ImageSourceType
import com.slideindex.app.activity.ShortcutIconStorage
import com.slideindex.app.activity.ShortcutImagePreviewActivity
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
import com.slideindex.app.ui.miuix.MiuixPickerDialog
import com.slideindex.app.ui.miuix.MiuixRadioSelectDialog
import com.slideindex.app.ui.miuix.MiuixSwitchRow
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.util.ImageDecodeGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ActivityShortcutEditImageScreen(
    initialShortcut: ActivityShortcut?,
    allShortcuts: List<ActivityShortcut>,
    onBack: () -> Unit,
    onSave: (List<ActivityShortcut>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialConfig = remember(initialShortcut?.id) {
        initialShortcut?.let { ActivityShortcutImageSupport.configFrom(it) } ?: ImageShortcutConfig(uri = "")
    }

    val defaultLabel = stringResource(R.string.activity_shortcut_default_name)

    var label by rememberSaveable(initialShortcut?.id) {
        mutableStateOf(initialShortcut?.label ?: defaultLabel)
    }
    var subtitle by rememberSaveable(initialShortcut?.id) { mutableStateOf("") }
    var iconPath by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialShortcut?.iconPath) }
    var imageUri by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.uri) }
    var sourceType by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.sourceType) }
    var openInDialog by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.openInDialog) }
    var clickToDismiss by rememberSaveable(initialShortcut?.id) { mutableStateOf(initialConfig.clickToDismiss) }
    var orientation by rememberSaveable(initialShortcut?.id) { mutableIntStateOf(initialConfig.orientation) }

    var showSourceTypePicker by remember { mutableStateOf(false) }
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var inputUrlText by remember { mutableStateOf("") }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadingPreview by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        if (imageUri.isBlank()) {
            previewBitmap = null
            return@LaunchedEffect
        }
        loadingPreview = true
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    imageUri.startsWith("http://") || imageUri.startsWith("https://") -> {
                        val url = URL(imageUri)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 6000
                        conn.readTimeout = 6000
                        conn.inputStream.use { input ->
                            BitmapFactory.decodeStream(input)
                        }?.let { ImageDecodeGuard.fitForDisplay(it, 512) }
                    }
                    imageUri.startsWith("content://") -> {
                        ImageDecodeGuard.decodeUri(context, Uri.parse(imageUri), 512)
                    }
                    else -> {
                        ImageDecodeGuard.decodeFile(imageUri, 512)
                    }
                }
            }.getOrNull()
        }
        previewBitmap = loaded
        loadingPreview = false
    }

    // 本地相册选图
    val localImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            imageUri = uri.toString()
            sourceType = ImageSourceType.LOCAL
        }
    }

    // SAF 文件选图
    val fileImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            imageUri = uri.toString()
            sourceType = ImageSourceType.FILE
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

    val currentConfig = ImageShortcutConfig(
        uri = imageUri,
        sourceType = sourceType,
        openInDialog = openInDialog,
        clickToDismiss = clickToDismiss,
        orientation = orientation,
    )

    val onTestClick: () -> Unit = {
        if (imageUri.isBlank()) {
            Toast.makeText(context, R.string.activity_shortcut_pick_image_hint, Toast.LENGTH_SHORT).show()
        } else {
            ShortcutImagePreviewActivity.start(context, currentConfig)
        }
    }

    val onSaveClick: () -> Unit = {
        if (label.isBlank()) {
            Toast.makeText(context, R.string.activity_shortcut_error_name, Toast.LENGTH_SHORT).show()
        } else if (imageUri.isBlank()) {
            Toast.makeText(context, R.string.activity_shortcut_pick_image_hint, Toast.LENGTH_SHORT).show()
        } else {
            val shortcut = ActivityShortcutImageSupport.toShortcut(
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
            stringResource(R.string.activity_shortcut_add_image)
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
                    enabled = imageUri.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(
                    onClick = onSaveClick,
                    enabled = label.isNotBlank() && imageUri.isNotBlank(),
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
        LazySettingsItem(key = "image-edit-form") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 名称输入框（图二）
                MiuixLabeledTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = stringResource(R.string.activity_shortcut_default_name),
                    modifier = Modifier.fillMaxWidth(),
                )

                // 图标 + 副标题输入框（图二）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 图标展示与更换
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
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    // 副标题输入框
                    MiuixLabeledTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = stringResource(R.string.activity_shortcut_subtitle_optional),
                        modifier = Modifier.weight(1f),
                    )
                }

                // 图片添加卡片区域（图二、图三）
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    modifier = Modifier
                        .height(200.dp)
                        .clickable { showSourceTypePicker = true },
                    outerHorizontalPadding = 0.dp,
                    insidePadding = PaddingValues(0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showSourceTypePicker = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                            )
                        } else if (loadingPreview) {
                            CircularProgressIndicator(
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        } else {
                            // 空状态：虚线框加号（图二）
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.activity_shortcut_pick_image_hint),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 选项设置卡片（图二）
        MiuixListSettingsCard(
            keyPrefix = "image-options",
            items = listOf(
                CardItem("open-in-dialog") {
                    MiuixSwitchRow(
                        title = stringResource(R.string.activity_shortcut_open_in_dialog),
                        summary = stringResource(R.string.activity_shortcut_open_in_dialog_desc),
                        checked = openInDialog,
                        onCheckedChange = { openInDialog = it },
                    )
                },
                CardItem("click-to-dismiss") {
                    MiuixSwitchRow(
                        title = stringResource(R.string.activity_shortcut_click_to_dismiss),
                        summary = stringResource(R.string.activity_shortcut_click_to_dismiss_desc),
                        checked = clickToDismiss,
                        onCheckedChange = { clickToDismiss = it },
                    )
                },
                CardItem("orientation") {
                    val orientationText = when (orientation) {
                        1 -> stringResource(R.string.activity_shortcut_orientation_portrait)
                        2 -> stringResource(R.string.activity_shortcut_orientation_landscape)
                        else -> stringResource(R.string.activity_shortcut_orientation_default)
                    }
                    ArrowPreference(
                        title = stringResource(R.string.activity_shortcut_orientation),
                        summary = orientationText,
                        onClick = { showOrientationDialog = true },
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

    // 选图来源菜单（补充图：选择本地图片、从文件选择、图片链接）
    MiuixPickerDialog(
        show = showSourceTypePicker,
        onDismissRequest = { showSourceTypePicker = false },
        title = stringResource(R.string.activity_shortcut_add_image),
    ) {
        ArrowPreference(
            title = stringResource(R.string.activity_shortcut_pick_local_image),
            onClick = {
                showSourceTypePicker = false
                localImagePicker.launch("image/*")
            },
        )
        ArrowPreference(
            title = stringResource(R.string.activity_shortcut_pick_from_file),
            onClick = {
                showSourceTypePicker = false
                fileImagePicker.launch(arrayOf("image/*"))
            },
        )
        ArrowPreference(
            title = stringResource(R.string.activity_shortcut_pick_image_url),
            onClick = {
                showSourceTypePicker = false
                inputUrlText = if (imageUri.startsWith("http")) imageUri else ""
                showUrlInputDialog = true
            },
        )
    }

    // URL 输入弹窗
    MiuixConfirmDialog(
        show = showUrlInputDialog,
        title = stringResource(R.string.activity_shortcut_image_url_dialog_title),
        onDismissRequest = { showUrlInputDialog = false },
        onConfirm = {
            imageUri = inputUrlText.trim()
            sourceType = ImageSourceType.URL
            showUrlInputDialog = false
        },
        text = {
            MiuixLabeledTextField(
                value = inputUrlText,
                onValueChange = { inputUrlText = it },
                label = "https://...",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
    )

    // 横竖屏单选弹窗
    if (showOrientationDialog) {
        val options = listOf(0, 1, 2)
        MiuixRadioSelectDialog(
            show = true,
            title = stringResource(R.string.activity_shortcut_orientation),
            options = options,
            selected = orientation,
            optionLabel = { opt ->
                when (opt) {
                    1 -> stringResource(R.string.activity_shortcut_orientation_portrait)
                    2 -> stringResource(R.string.activity_shortcut_orientation_landscape)
                    else -> stringResource(R.string.activity_shortcut_orientation_default)
                }
            },
            onDismissRequest = { showOrientationDialog = false },
            onSelect = {
                orientation = it
                showOrientationDialog = false
            },
        )
    }
}
