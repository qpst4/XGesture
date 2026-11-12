package com.slideindex.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixExpandableSearchFieldStrip
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.DropdownEntry
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.util.ImageDecodeGuard
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixTabRowWithContourInCard
import java.io.File
import java.io.FileOutputStream
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton

/**
 * 按存储键渲染动作图标：字体图标（Lucide/Remix/Tabler）/ 文字图标 / 本地图片，
 * 都没有就回退到动作自带图标。
 */
@Composable
internal fun ActionIconView(
    action: GestureAction,
    iconKey: String?,
    sizeDp: Int,
    modifier: Modifier = Modifier,
    /** false = 不画底色（调用方容器已经有底，比如网格格子）。 */
    showBackground: Boolean = true,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape((sizeDp / 3).dp)

    val fontIcon = remember(iconKey) { IconFontCatalog.resolve(context, iconKey) }
    val textIcon = ActionIconKeyCodec.textOf(iconKey)
    val filePath = remember(iconKey) {
        iconKey?.takeIf { ActionIconKeyCodec.isFile(it) }?.removePrefix("file:")
    }
    val bitmap = remember(filePath) {
        filePath?.let { path ->
            if (File(path).exists()) {
                ImageDecodeGuard.decodeFile(path, ImageDecodeGuard.MAX_ICON_SIDE_PX)
            } else {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(shape)
            .let { if (showBackground) it.background(MiuixTheme.colorScheme.surfaceContainerHigh) else it },
        contentAlignment = Alignment.Center,
    ) {
        when {
            fontIcon != null -> Text(
                text = fontIcon.second,
                fontFamily = fontIcon.first,
                fontSize = (sizeDp * 0.75f).sp,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            textIcon != null -> Text(
                text = textIcon,
                fontSize = (sizeDp * 0.42f).sp,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(sizeDp.dp)
                    .clip(shape),
            )
            else -> Icon(
                imageVector = gestureActionIcon(action, outlined = false),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size((sizeDp * 0.5f).dp),
            )
        }
    }
}

/**
 * 「图标 + 来源选择」的完整宿主。
 *
 * - 点图标 → **Miuix 图标下拉菜单**（`OverlayIconDropdownMenu`），已设自定义图标时含「恢复默认图标」
 * - 「从图标库选择」→ 进入整页子页（带返回、搜索、三个库 tab）
 */
@Composable
internal fun ActionIconPickerHost(
    action: GestureAction,
    iconKey: String?,
    sizeDp: Int,
    modifier: Modifier = Modifier,
    /**
     * 图片落盘用的存储键（如 `global:panel:<id>`）。
     *
     * 为 null 时按 [action] 身份算文件名（动作自己的图标）；非 null 时走
     * 「全局图标」那套按任意 key 存图片的通道（面板图标、全局图标设置都用它）。
     */
    storageKey: String? = null,
    onIconChange: (String?) -> Unit,
    /** 恢复默认时的额外清理（全局图标用它删自己的文件）。为 null 时按动作身份删文件。 */
    onReset: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var textDialogOpen by remember { mutableStateOf(false) }
    var appDialogOpen by remember { mutableStateOf(false) }

    // 图标库 = 独立 Activity（真子页），结果通过 startActivityForResult 回传
    val libraryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val picked = result.data?.getStringExtra(
                com.slideindex.app.service.IconLibraryPickerTrampolineActivity.EXTRA_PICKED_KEY,
            )
            if (!picked.isNullOrBlank()) onIconChange(picked)
        }
    }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val savedPath = if (storageKey != null) {
                ActionIconStorage.saveFromUriForGlobalKey(context, storageKey, uri)
            } else {
                ActionIconStorage.saveFromUri(context, action, uri)
            }
            if (savedPath != null) onIconChange(ActionIconKeyCodec.file(savedPath))
        }
    }

    val menuEntry = DropdownEntry(
        items = buildList {
            add(
                DropdownItem(
                    text = stringResource(R.string.action_icon_from_library),
                    onClick = {
                        libraryLauncher.launch(
                            com.slideindex.app.service.IconLibraryPickerTrampolineActivity
                                .createIntent(context, iconKey),
                        )
                    },
                ),
            )
            add(
                DropdownItem(
                    text = stringResource(R.string.action_icon_text),
                    onClick = { textDialogOpen = true },
                ),
            )
            add(
                DropdownItem(
                    text = stringResource(R.string.action_icon_from_app),
                    onClick = { appDialogOpen = true },
                ),
            )
            add(
                DropdownItem(
                    text = stringResource(R.string.action_icon_from_gallery),
                    onClick = {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                ),
            )
            if (iconKey != null) {
                add(
                    DropdownItem(
                        text = stringResource(R.string.action_detail_icon_reset),
                        onClick = {
                            if (storageKey != null) {
                                ActionIconStorage.deleteForGlobalKey(context, storageKey)
                            } else {
                                ActionIconStorage.delete(context, action)
                            }
                            onIconChange(null)
                            onReset?.invoke()
                        },
                    ),
                )
            }
        },
    )

    WindowIconDropdownMenu(
        entry = menuEntry,
        minHeight = sizeDp.dp,
        minWidth = sizeDp.dp,
        backgroundColor = Color.Transparent,
        cornerRadius = (sizeDp / 3).dp,
    ) {
        Box(modifier = modifier) {
            ActionIconView(action = action, iconKey = iconKey, sizeDp = sizeDp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary)
                    .border(1.5.dp, MiuixTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }


    if (textDialogOpen) {
        ActionTextIconDialog(
            initial = iconKey,
            onPick = { picked ->
                textDialogOpen = false
                onIconChange(picked)
            },
            onDismiss = { textDialogOpen = false },
        )
    }

    if (appDialogOpen) {
        ActionAppIconPickerDialog(
            action = action,
            onPick = { picked ->
                appDialogOpen = false
                onIconChange(picked)
            },
            onDismiss = { appDialogOpen = false },
        )
    }
}

/**
 * 图标库页 —— 骨架照仓库既有的整页编辑器（`SearchEngineEditorScreen`）：
 * `SettingsScreenScaffold` 顶栏/背景/返回 + 分组小标题 + 分组卡片 + `MiuixLabeledTextField`。
 *
 * 唯一自绘的是 5 列图标网格（Miuix 没有现成网格组件），颜色也全部用 Miuix token。
 */
@Composable
internal fun IconLibraryPickerPage(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    initialKey: String? = null,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    // 顶栏搜索展开时，返回键先收起搜索（仓库惯例：consumeExpandableSearchBack）
    consumeExpandableSearchBack(
        expanded = searchExpanded,
        query = query,
        onExpandedChange = { searchExpanded = it },
        onQueryChange = { query = it },
    )
    var selectedLibraryIndex by remember(initialKey) {
        mutableStateOf(
            IconFontLibrary.fromKey(initialKey)?.let { IconFontLibrary.entries.indexOf(it) } ?: 0,
        )
    }
    var remixLineStyle by remember(initialKey) {
        mutableStateOf(initialKey?.endsWith("-fill") != true)
    }

    val libraries = IconFontLibrary.entries
    val selectedLibrary = libraries[selectedLibraryIndex.coerceIn(0, libraries.lastIndex)]

    // LazyListScope 里不能调用 stringResource，所以在这里预取
    val librarySectionTitle = stringResource(R.string.action_icon_library_title)
    val lineStyleLabel = stringResource(R.string.action_icon_style_line)
    val filledStyleLabel = stringResource(R.string.action_icon_style_filled)

    val items = remember(query, selectedLibraryIndex, remixLineStyle) {
        val base = IconFontCatalog.search(
            context = context,
            query = query,
            library = selectedLibrary,
            limit = 2400,
        )
        base.filter { item ->
            when (item.library) {
                IconFontLibrary.REMIX -> item.name.endsWith(if (remixLineStyle) "-line" else "-fill")
                else -> true
            }
        }
    }

    // 仓库封装：搜索按钮/搜索框/焦点/返回键都在 Scaffold 里，不用自己拼
    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.action_icon_library_title),
        subtitle = stringResource(R.string.action_icon_count, items.size),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onDismiss,
        hintResId = R.string.action_icon_search_hint,
    ) {
        settingsLazySmallTitle(key = "icon-library-title", title = librarySectionTitle)

        item(key = "icon-library-tabs") {
            MiuixTabRowWithContourInCard(
                tabs = libraries.map { it.label },
                selectedTabIndex = selectedLibraryIndex.coerceIn(0, libraries.lastIndex),
                onTabSelected = { selectedLibraryIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (selectedLibrary == IconFontLibrary.REMIX) {
            item(key = "icon-style-tabs") {
                MiuixTabRowWithContourInCard(
                    tabs = listOf(lineStyleLabel, filledStyleLabel),
                    selectedTabIndex = if (remixLineStyle) 0 else 1,
                    onTabSelected = { remixLineStyle = it == 0 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }


        // 单一滚动容器：按 5 个一行拆成行项交给外层 LazyColumn 懒加载。
        // 不能在 LazyColumn 里再套固定高度的 LazyVerticalGrid——两层滚动会互相推，滑动时底下露白。
        items(
            items = items.chunked(5),
            key = { row -> row.joinToString("|") { it.key } },
        ) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { item ->
                    IconGridCell(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(item.key) },
                    )
                }
                repeat(5 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StyleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 「文字图标」：输入 1~2 个字，直接当图标显示。 */
@Composable
internal fun ActionTextIconDialog(
    initial: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(ActionIconKeyCodec.textOf(initial).orEmpty()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_icon_text_title)) },
        text = {
            MiuixLabeledTextField(
                value = text,
                onValueChange = { text = it.take(2) },
                label = stringResource(R.string.action_icon_text_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onPick(ActionIconKeyCodec.text(text.trim())) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

internal data class InstalledAppIcon(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
)

internal fun loadInstalledAppIcons(context: Context): List<InstalledAppIcon> {
    val pm = context.packageManager
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val resolved = pm.queryIntentActivities(intent, 0)
    return resolved
        .map { info ->
            InstalledAppIcon(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(pm).toString(),
                icon = info.loadIcon(pm),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label }
}

/** 「选择应用图标」：列出已装应用，取它的图标落盘。 */
@Composable
internal fun ActionAppIconPickerDialog(
    action: GestureAction,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /** 全局图标场景传自己的 key，避免所有行写同一个文件。 */
    storageKey: String? = null,
) {
    val context = LocalContext.current
    val apps = remember { loadInstalledAppIcons(context) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_icon_from_app)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                gridItems(apps, key = { it.packageName }) { app ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val path = if (storageKey != null) {
                                    app.icon?.toBitmapSafe()?.let {
                                        ActionIconStorage.saveBitmapForGlobalKey(context, storageKey, it)
                                    }
                                } else {
                                    saveAppIcon(context, action, app)
                                }
                                if (path != null) onPick(ActionIconKeyCodec.file(path)) else onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val bitmap = remember(app.packageName) { app.icon?.toBitmapSafe() }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                            )
                        }
                        Text(
                            text = app.label,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun android.graphics.drawable.Drawable.toBitmapSafe(): Bitmap? {
    val width = (intrinsicWidth.takeIf { it > 0 } ?: 96).coerceAtMost(1024)
    val height = (intrinsicHeight.takeIf { it > 0 } ?: 96).coerceAtMost(1024)
    return runCatching {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
        bitmap
    }.getOrNull()
}

private fun saveAppIcon(
    context: Context,
    action: GestureAction,
    app: InstalledAppIcon,
): String? {
    val bitmap = app.icon?.toBitmapSafe() ?: return null
    val dir = File(context.filesDir, "action_icons").apply { if (!exists()) mkdirs() }
    val target = File(dir, ActionDisplayNameStore.actionKey(action).hashCode().toUInt().toString(16) + ".png")
    val ok = runCatching {
        FileOutputStream(target).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }.isSuccess
    return if (ok) target.absolutePath else null
}


/** 5 列网格中的一个图标格子（等宽，由外层 Row 的 weight 决定宽度）。 */
@Composable
private fun IconGridCell(
    item: IconFontItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = String(Character.toChars(item.codePoint)),
                fontFamily = item.library.fontFamily,
                fontSize = 26.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = item.displayName,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}
