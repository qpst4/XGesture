package com.slideindex.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.iconpack.IconPackDrawableLoader
import com.slideindex.app.iconpack.IconPackFilter
import com.slideindex.app.iconpack.IconPackInfo
import com.slideindex.app.iconpack.IconPackProvenanceStore
import com.slideindex.app.iconpack.IconPackRepository
import com.slideindex.app.iconpack.IconPackTarget
import com.slideindex.app.ui.miuix.MiuixNavigationBackHandler
import com.slideindex.app.ui.miuix.MiuixSearchField
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.viewmodel.GlobalIconLibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「从手机已有 app 图标包中选择」。
 *
 * 两跳：
 * 1. 选图标包（已收录的排前面，未收录的也能进）
 * 2. 在该包内挑图标（命中自动定位并预选，未收录可从全包手动挑）
 *
 * 结果：把选中的图标落盘成 PNG（与「从应用图标选择」同一条路），
 * 并记一条来源信息给图标库那一行显示「来自 XX 图标包」。
 */
@Composable
internal fun IconPackPickerPage(
    target: IconPackTarget,
    storageKey: String,
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPack by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPackLabel by rememberSaveable { mutableStateOf("") }

    val pack = selectedPack
    if (pack == null) {
        IconPackListStage(
            target = target,
            onPick = { info ->
                selectedPack = info.packageName
                selectedPackLabel = info.label
            },
            onDismiss = onDismiss,
        )
    } else {
        IconPackIconsStage(
            target = target,
            packPackage = pack,
            packLabel = selectedPackLabel,
            storageKey = storageKey,
            onBack = { selectedPack = null },
            onPicked = onPicked,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 跳①：选图标包
// ---------------------------------------------------------------------------------------------

@Composable
private fun IconPackListStage(
    target: IconPackTarget,
    onPick: (IconPackInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var packs by remember { mutableStateOf<List<IconPackInfo>?>(null) }
    // null = 还在检查；true/false = 是否收录了这个应用
    val covers = remember { mutableStateMapOf<String, Boolean?>() }

    LaunchedEffect(target.packageName) {
        val loaded = withContext(Dispatchers.IO) { IconPackRepository.scan(context) }
        packs = loaded
        val activity = withContext(Dispatchers.IO) {
            IconPackRepository.launchActivityName(context, target.packageName)
        }
        for (info in loaded) {
            val filter = withContext(Dispatchers.IO) {
                IconPackRepository.filter(context, info.packageName)
            }
            covers[info.packageName] =
                filter != null && filter.covers(target.packageName, activity)
        }
        // 收录这个应用的包排前面，其余按名称；等检查完再排一次，避免列表跳动
        packs = loaded.sortedWith(
            compareByDescending<IconPackInfo> { covers[it.packageName] == true }
                .thenBy { it.label.lowercase() },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.icon_pack_picker_title),
        subtitle = stringResource(R.string.icon_pack_picker_subtitle, target.label),
        onBack = onDismiss,
    ) {
        val list = packs
        when {
            list == null -> item(key = "icon-pack-loading") {
                CenteredHint(stringResource(R.string.loading))
            }

            list.isEmpty() -> item(key = "icon-pack-empty") {
                EmptyIconPackHint()
            }

            else -> items(items = list, key = { it.packageName }) { info ->
                IconPackRow(
                    info = info,
                    covers = covers[info.packageName],
                    onClick = { onPick(info) },
                )
            }
        }
    }
}

@Composable
private fun IconPackRow(
    info: IconPackInfo,
    covers: Boolean?,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, info.packageName) {
        value = withContext(Dispatchers.IO) {
            info.icon?.let { IconPackDrawableLoader.toBitmap(it, 96) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val shown = bitmap
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = info.label,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = info.packageName,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = when (covers) {
                null -> stringResource(R.string.icon_pack_checking)
                true -> stringResource(R.string.icon_pack_covers)
                false -> stringResource(R.string.icon_pack_not_covers)
            },
            style = MiuixTheme.textStyles.footnote1,
            color = when (covers) {
                true -> MiuixTheme.colorScheme.primary
                null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                false -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 跳②：在包内挑图标
// ---------------------------------------------------------------------------------------------

@Composable
private fun IconPackIconsStage(
    target: IconPackTarget,
    packPackage: String,
    packLabel: String,
    storageKey: String,
    onBack: () -> Unit,
    onPicked: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<IconPackFilter?>(null) }
    var hit by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(packPackage) {
        val loaded = withContext(Dispatchers.IO) { IconPackRepository.filter(context, packPackage) }
        val activity = withContext(Dispatchers.IO) {
            IconPackRepository.launchActivityName(context, target.packageName)
        }
        val located = loaded?.resolve(target.packageName, activity)
        filter = loaded
        hit = located
        if (located != null) selected = located
        loading = false
    }

    val allIcons = filter?.allDrawables.orEmpty()
    val icons: List<String> = remember(allIcons, query, hit) {
        // 命中的图标排最前：打开就能看到「这个应用的图标」，同时不影响继续浏览全包。
        val located = hit
        val ordered = if (located != null && allIcons.contains(located)) {
            listOf(located) + allIcons.filter { it != located }
        } else {
            allIcons
        }
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) {
            ordered
        } else {
            ordered.filter { it.lowercase().contains(keyword) }
        }
    }

    val save: () -> Unit = {
        val name = selected
        if (name != null && !saving) {
            saving = true
            val pickedName: String = name
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    IconPackDrawableLoader.load(
                        context,
                        packPackage,
                        pickedName,
                        GlobalIconLibraryViewModel.ICON_PX,
                    )
                }
                val path = bitmap?.let {
                    ActionIconStorage.saveBitmapForGlobalKey(context, storageKey, it)
                }
                if (path != null) {
                    IconPackProvenanceStore.save(
                        context = context,
                        storageKey = storageKey,
                        packPackage = packPackage,
                        packLabel = packLabel.ifBlank { packPackage },
                        drawable = pickedName,
                    )
                    onPicked(path)
                } else {
                    saving = false
                    Toast.makeText(context, R.string.icon_pack_load_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    MiuixNavigationBackHandler(enabled = true) {
        if (query.isNotEmpty()) query = "" else onBack()
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.icon_pack_icons_title, packLabel.ifBlank { packPackage }),
        subtitle = stringResource(R.string.icon_pack_icons_count, icons.size),
        onBack = {
            if (query.isNotEmpty()) query = "" else onBack()
        },
        bottomBar = {
            MiuixButton(
                onClick = save,
                enabled = selected != null && !saving && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                MiuixText(stringResource(R.string.icon_pack_use))
            }
        },
    ) {
        item(key = "icon-pack-status") {
            val text = when {
                loading -> stringResource(R.string.icon_pack_checking)
                filter == null -> stringResource(R.string.icon_pack_cannot_parse)
                hit != null -> stringResource(R.string.icon_pack_icon_located, hit.orEmpty())
                else -> stringResource(R.string.icon_pack_icon_not_located)
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item(key = "icon-pack-search") {
            MiuixSearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                hintResId = R.string.icon_pack_icon_search_hint,
            )
        }

        if (!loading && icons.isEmpty()) {
            item(key = "icon-pack-empty") {
                CenteredHint(stringResource(R.string.icon_pack_icon_empty))
            }
        }

        // 5 个一行交给外层 LazyColumn 懒加载（仓库既有做法，见 IconLibraryPickerPage）
        items(
            items = icons.chunked(5),
            key = { row -> row.first() },
        ) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { name ->
                    IconPackCell(
                        packPackage = packPackage,
                        drawableName = name,
                        selected = name == selected,
                        onClick = { selected = name },
                        modifier = Modifier.weight(1f),
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
private fun IconPackCell(
    packPackage: String,
    drawableName: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, drawableName) {
        value = withContext(Dispatchers.IO) {
            IconPackDrawableLoader.load(context, packPackage, drawableName, 96)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                },
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MiuixTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = drawableName,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 小部件
// ---------------------------------------------------------------------------------------------

@Composable
private fun CenteredHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun EmptyIconPackHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.icon_pack_empty_title),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.icon_pack_empty_desc),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
