package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.iconpack.IconPackProvenanceStore
import com.slideindex.app.iconpack.IconPackTarget
import com.slideindex.app.iconpack.IconThemeStore
import com.slideindex.app.service.IconPackPickerTrampolineActivity
import com.slideindex.app.ui.miuix.MiuixTabRowWithContourInCard
import com.slideindex.app.ui.miuix.MiuixIconDropdownMenu
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.viewmodel.GlobalIconLibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 扩展页里的「图标库」：**全局**给动作类型 / 应用指定图标。
 *
 * 每一行都能设置完整来源（图标库 / 文字 / 应用图标 / 相册 / 恢复默认），
 * 未设置时显示**默认图标**（动作类型图标 / 应用自带图标）。
 */
@Composable
fun GlobalIconLibraryScreen(
    onBack: () -> Unit,
    viewModel: GlobalIconLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    // 与其它挑应用页面同源：这里要能列出分身（但图标覆盖按包名存，分身行只展示不可选）。
    val apps by com.slideindex.app.ui.compose.collectPickerAppsAsState(includeClones = true)

    // 只有切到「应用」tab 才去拿应用列表（列表在后台线程取，首帧不再扫全量）。
    LaunchedEffect(tab) {
        if (tab == 1) viewModel.ensureAppsLoaded()
    }

    // 用 catalog 的实例（而不是 enum）：这样有中文名和默认图标，同一类型只保留一个代表
    val actions = remember(context) {
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        val built = com.slideindex.app.ui.picker.GestureActionCatalog.build(
            scope = com.slideindex.app.ui.picker.GestureActionCatalogScope.GesturePicker,
            catalogPolicy = com.slideindex.app.gesture.ActionPickerCatalogPolicy.EdgeGesture,
        )
            .distinctBy { it.type }
        // 每项只取一次文案：放进 comparator 会按比较次数反复 getString（约 700 次）
        val labels = built.associateWith {
            com.slideindex.app.ui.gesturepicker.gestureActionLabelText(context, it)
        }
        // 按中文名的拼音排序（而不是英文枚举名）
        built.sortedWith(compareBy(collator) { labels.getValue(it) })
    }

    val actionsTabLabel = stringResource(R.string.global_icon_tab_actions)
    val appsTabLabel = stringResource(R.string.global_icon_tab_apps)

    SettingsScreenScaffold(
        title = stringResource(R.string.action_icon_library_title),
        onBack = onBack,
    ) {
        item(key = "global-icon-tabs") {
            MiuixTabRowWithContourInCard(
                tabs = listOf(actionsTabLabel, appsTabLabel),
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (tab == 0) {
            items(
                items = actions,
                key = { "action-${it.type.id}" },
            ) { action ->
                GlobalIconRow(
                    label = gestureActionLabel(action),
                    currentKey = GlobalIconStore.actionIcon(context, action.type.name),
                    defaultIcon = gestureActionIcon(action, outlined = true),
                    onPick = { key -> GlobalIconStore.setActionIcon(context, action.type.name, key) },
                    resetKey = "global:action:${action.type.name}",
                )
            }
        } else {
            if (apps.isEmpty()) {
                // 列表在后台取：先占位，不阻塞首帧
                // （原来是同步扫全量应用 + 逐包解码图标，实测首帧冻结 1.3–1.6 秒）。
                item(key = "global-icon-apps-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.loading),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            if (apps.any { it.isClone }) {
                item(key = "global-icon-clone-hint") {
                    Text(
                        text = stringResource(R.string.app_clone_package_only),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(
                count = apps.size,
                key = { index ->
                    val app = apps[index]
                    "app-" + com.slideindex.app.launcher.AppTarget.encode(app.packageName, app.cloneUserId)
                },
            ) { index ->
                val app = apps[index]
                if (app.isClone) {
                    // 分身：只展示，不给设图标（GlobalIconStore 的键是包名，设了会连主实例一起换）。
                    Md3PickerListRow(
                        segmentIndex = index,
                        segmentCount = apps.size,
                        title = app.label,
                        subtitle = app.packageName + " · " + stringResource(R.string.app_clone_package_only_short),
                        selected = false,
                        enabled = false,
                        onClick = null,
                        leadingContent = { Md3PickerAppLeading(app) },
                        trailingMode = PickerTrailingMode.None,
                    )
                } else {
                    GlobalIconRow(
                        label = app.label,
                        currentKey = GlobalIconStore.appIcon(context, app.packageName),
                        iconBitmap = viewModel.icons[app.packageName],
                        loadIcon = { viewModel.requestIcon(app.packageName) },
                        onPick = { key -> GlobalIconStore.setAppIcon(context, app.packageName, key) },
                        resetKey = "global:app:${app.packageName}",
                        appPackageName = app.packageName,
                    )
                }
            }
        }
    }
}

/**
 * 一行：Miuix 下拉行（整行可点）+ 左侧图标 + 「恢复默认」。
 *
 * 用本地 state 保证"选完立刻变"（写 store 不会触发重组）；
 * 用 `WindowDropdownMenu`：整行点击展开 Miuix 菜单，菜单项就是完整来源列表。
 *
 * 图标优先级：字体图标 → 相册/应用落盘的 PNG（后台解码）→ 应用图标（后台缓存）→
 * 默认矢量图标 → 首字。三种位图都**不在主线程解码**，那是首帧卡顿的来源。
 */
@Composable
private fun GlobalIconRow(
    label: String,
    currentKey: String?,
    onPick: (String?) -> Unit,
    resetKey: String,
    defaultIcon: ImageVector? = null,
    /** 应用图标（[GlobalIconLibraryViewModel] 已按 128px 在后台取好）。 */
    iconBitmap: ImageBitmap? = null,
    /** 应用图标未命中缓存时提交一次后台加载。 */
    loadIcon: (() -> Unit)? = null,
    /** 应用行才有：允许从已装图标包挑图标（动作类型行没有「对应的应用」，传 null）。 */
    appPackageName: String? = null,
) {
    val context = LocalContext.current
    var key by remember(label) { mutableStateOf(currentKey) }
    var textDialogOpen by remember { mutableStateOf(false) }
    var appDialogOpen by remember { mutableStateOf(false) }
    var provenance by remember(key) {
        mutableStateOf(
            appPackageName?.let { IconPackProvenanceStore.get(context, resetKey) },
        )
    }
    // 换成任何别的来源时，之前「来自 XX 图标包」的记录必须一起清掉
    val clearPackSource: () -> Unit = {
        IconPackProvenanceStore.clear(context, resetKey)
        provenance = null
    }

    // 全局图标包：没设例外的应用会显示「来自主题」
    val themePack = remember(IconThemeStore.revision) { IconThemeStore.pack(context) }
    val themeLabel = remember(themePack) { IconThemeStore.label(context) }
    val themeCovered by produceState(initialValue = false, appPackageName, themePack, IconThemeStore.revision) {
        value = if (appPackageName != null && themePack != null) {
            withContext(Dispatchers.IO) {
                val filter = com.slideindex.app.iconpack.IconPackRepository.filter(context, themePack)
                val activity = com.slideindex.app.iconpack.IconPackRepository
                    .launchActivityName(context, appPackageName)
                filter?.covers(appPackageName, activity) == true
            }
        } else {
            false
        }
    }

    val iconPackLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val path = result.data?.getStringExtra(
                IconPackPickerTrampolineActivity.EXTRA_PICKED_PATH,
            )
            if (!path.isNullOrBlank()) {
                val newKey = ActionIconKeyCodec.file(path)
                key = newKey
                provenance = IconPackProvenanceStore.get(context, resetKey)
                onPick(newKey)
            }
        }
    }

    val libraryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val picked = result.data?.getStringExtra(
                com.slideindex.app.service.IconLibraryPickerTrampolineActivity.EXTRA_PICKED_KEY,
            )
            if (!picked.isNullOrBlank()) {
                clearPackSource()
                key = picked
                onPick(picked)
            }
        }
    }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val saved = ActionIconStorage.saveFromUriForGlobalKey(context, resetKey, uri)
            if (saved != null) {
                val newKey = ActionIconKeyCodec.file(saved)
                clearPackSource()
                key = newKey
                onPick(newKey)
            }
        }
    }

    val fontIcon = remember(key) { IconFontCatalog.resolve(context, key) }
    // 相册/应用图标落盘的是 PNG：解码必须离开主线程（原来在组合期直接 decodeFile）
    val keySnapshot = key
    val fileBitmap by produceState<ImageBitmap?>(initialValue = null, keySnapshot) {
        value = if (keySnapshot != null && ActionIconKeyCodec.isFile(keySnapshot)) {
            withContext(Dispatchers.IO) {
                decodeIconFile(keySnapshot.removePrefix("file:"), GlobalIconLibraryViewModel.ICON_PX)
            }
        } else {
            null
        }
    }
    if (loadIcon != null && iconBitmap == null) {
        LaunchedEffect(Unit) { loadIcon() }
    }
    val shownBitmap = fileBitmap ?: iconBitmap

    val menuEntry = top.yukonga.miuix.kmp.basic.DropdownEntry(
        items = buildList {
            add(
                top.yukonga.miuix.kmp.basic.DropdownItem(
                    text = stringResource(R.string.action_icon_from_library),
                    onClick = {
                        libraryLauncher.launch(
                            com.slideindex.app.service.IconLibraryPickerTrampolineActivity
                                .createIntent(context, key),
                        )
                    },
                ),
            )
            add(
                top.yukonga.miuix.kmp.basic.DropdownItem(
                    text = stringResource(R.string.action_icon_text),
                    onClick = { textDialogOpen = true },
                ),
            )
            add(
                top.yukonga.miuix.kmp.basic.DropdownItem(
                    text = stringResource(R.string.action_icon_from_app),
                    onClick = { appDialogOpen = true },
                ),
            )
            // 只有应用行有「对应的应用」，动作类型行不显示这一项
            if (appPackageName != null) {
                add(
                    top.yukonga.miuix.kmp.basic.DropdownItem(
                        text = stringResource(R.string.action_icon_from_icon_pack),
                        onClick = {
                            iconPackLauncher.launch(
                                IconPackPickerTrampolineActivity.createIntent(
                                    context = context,
                                    target = IconPackTarget(
                                        packageName = appPackageName,
                                        activityName = null,
                                        label = label,
                                    ),
                                    storageKey = resetKey,
                                ),
                            )
                        },
                    ),
                )
            }
            add(
                top.yukonga.miuix.kmp.basic.DropdownItem(
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
            if (key != null) {
                add(
                    top.yukonga.miuix.kmp.basic.DropdownItem(
                        text = stringResource(R.string.action_detail_icon_reset),
                        onClick = {
                            ActionIconStorage.deleteForGlobalKey(context, resetKey)
                            clearPackSource()
                            key = null
                            onPick(null)
                        },
                    ),
                )
            }
        },
    )

    MiuixIconDropdownMenu(
        entry = menuEntry,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 64.dp,
        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        fontIcon != null -> Text(
                            text = fontIcon.second,
                            fontFamily = fontIcon.first,
                            fontSize = 27.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        shownBitmap != null -> Image(
                            bitmap = shownBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        defaultIcon != null -> Icon(
                            imageVector = defaultIcon,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(27.dp),
                        )
                        else -> Text(
                            text = label.take(1),
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                // 已自定义：图标右下角蓝点
                if (key != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MiuixTheme.colorScheme.primary)
                            .border(
                                width = 1.5.dp,
                                color = MiuixTheme.colorScheme.surface,
                                shape = androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = run {
                        val customKey = key
                        when {
                            customKey.isNullOrBlank() && themePack != null && themeCovered ->
                                stringResource(
                                    R.string.global_icon_from_theme_pack,
                                    themeLabel ?: themePack,
                                )
                            customKey.isNullOrBlank() ->
                                stringResource(R.string.global_icon_default)
                            provenance != null -> stringResource(
                                R.string.global_icon_from_pack,
                                provenance!!.packLabel,
                            )
                            else -> stringResource(R.string.global_icon_custom)
                        }
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 已自定义：右侧小标签（比副标题醒目）
            if (key != null) {
                Text(
                    text = stringResource(R.string.global_icon_custom),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }

    if (textDialogOpen) {
        ActionTextIconDialog(
            initial = key,
            onPick = { picked ->
                textDialogOpen = false
                clearPackSource()
                key = picked
                onPick(picked)
            },
            onDismiss = { textDialogOpen = false },
        )
    }

    if (appDialogOpen) {
        ActionAppIconPickerDialog(
            action = GestureAction.None,
            storageKey = resetKey,
            onPick = { picked ->
                appDialogOpen = false
                clearPackSource()
                key = picked
                onPick(picked)
            },
            onDismiss = { appDialogOpen = false },
        )
    }
}

/**
 * 解码「相册 / 应用图标」落盘的 PNG，按 [maxPx] 采样，**只在 IO 线程调用**。
 *
 * 这些文件往往是 512px 以上的原图，直接 `decodeFile` 到 40dp 的格子里既慢又占内存。
 */
private fun decodeIconFile(path: String, maxPx: Int): ImageBitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    val target = maxPx.coerceAtLeast(1)
    var sample = 1
    while (
        bounds.outWidth / (sample * 2) >= target &&
        bounds.outHeight / (sample * 2) >= target
    ) {
        sample *= 2
    }
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    android.graphics.BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()
