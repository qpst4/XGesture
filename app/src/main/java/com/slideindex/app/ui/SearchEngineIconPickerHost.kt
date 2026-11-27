package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixDialogAction
import com.slideindex.app.ui.miuix.MiuixAdaptiveDialog
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.search.SearchEngineIconStorage
import com.slideindex.app.service.IconLibraryPickerTrampolineActivity
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.ui.miuix.MiuixGlassDropdownMenu
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 搜索引擎 / 搜索组的图标编辑宿主：
 * 点图标 → 下拉菜单（图标库 / 文字 / 应用 / 相册 / 恢复默认），右下角带编辑角标。
 *
 * 图标库结果会立刻栅格化并存到 `search_icons/`，这样 [SearchEngineIcon]
 * 不需要依赖 `:app` 的图标库渲染，也不会引入 `iconKey` 字段。
 */
@Composable
fun SearchEngineIconPickerHost(
    engine: SearchEngineConfig,
    size: Dp,
    onIconPath: (String?) -> Unit,
    onTextIcon: (String?) -> Unit,
    onIconSourcePackage: (String?) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconTint = MiuixTheme.colorScheme.onSurface.toArgb()
    var textDialogOpen by remember { mutableStateOf(false) }
    var appDialogOpen by remember { mutableStateOf(false) }

    val libraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val key = result.data?.getStringExtra(
                IconLibraryPickerTrampolineActivity.EXTRA_PICKED_KEY,
            )
            if (!key.isNullOrBlank()) {
                val bitmap = ActionIconBitmap.loadByKey(
                    context = context,
                    key = key,
                    sizePx = 128,
                    tintArgb = iconTint,
                )
                val path = bitmap?.let { SearchEngineIconStorage.saveIconFromBitmap(context, it) }
                if (path != null) {
                    onIconPath(path)
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val path = SearchEngineIconStorage.saveIconFromUri(context, uri)
            if (path != null) {
                onIconPath(path)
            }
        }
    }

    val hasCustomIcon = engine.iconType != SearchIconType.OTHER ||
        engine.iconPath != null ||
        engine.textIcon != null ||
        engine.iconSourcePackage != null
    val menuEntry = DropdownEntry(
        items = buildList {
            add(
                DropdownItem(
                    text = stringResource(R.string.action_icon_from_library),
                    onClick = {
                        libraryLauncher.launch(
                            IconLibraryPickerTrampolineActivity.createIntent(context, null),
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
                    onClick = { galleryLauncher.launch("image/*") },
                ),
            )
            if (hasCustomIcon) {
                add(
                    DropdownItem(
                        text = stringResource(R.string.action_detail_icon_reset),
                        onClick = onReset,
                    ),
                )
            }
        },
    )

    MiuixGlassDropdownMenu(entry = menuEntry) {
        Box(modifier = modifier.size(size)) {
            SearchEngineIcon(
                engine = engine,
                modifier = Modifier.size(size),
            )
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
        TextIconDialog(
            initialText = engine.textIcon.orEmpty(),
            onDismiss = { textDialogOpen = false },
            onConfirm = { text ->
                textDialogOpen = false
                onTextIcon(text)
            },
        )
    }

    if (appDialogOpen) {
        SearchEngineAppIconPickerDialog(
            onPick = { packageName ->
                appDialogOpen = false
                onIconSourcePackage(packageName)
            },
            onDismiss = { appDialogOpen = false },
        )
    }
}

@Composable
private fun SearchEngineAppIconPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { loadInstalledAppIcons(context) }
    MiuixAdaptiveDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.action_icon_from_app),
        actions = listOf(
            MiuixDialogAction(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            ),
        ),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(app.packageName) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val bitmap = remember(app.packageName) {
                        app.icon?.toBitmap(40, 40)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
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
    }
}
