package com.slideindex.app.overlay.searchpanel

import android.net.Uri
import androidx.core.net.toUri
import android.util.Size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import android.content.ClipData
import android.icu.text.SimpleDateFormat
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.platform.LocalConfiguration
import com.slideindex.app.overlay.CloneBadgeOverlay
import com.slideindex.app.util.ImageDecodeGuard
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.freezer.FreezerAppState
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHtmlParser
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.search.contacts.ContactSearchEntry
import com.slideindex.app.search.files.DeviceFileEntry
import com.slideindex.app.search.files.FileThumbnailCache
import com.slideindex.app.search.files.FileType
import com.slideindex.app.search.files.FileTypeUtils
import com.slideindex.app.search.shortcuts.ShortcutSearchEntry
import com.slideindex.app.search.settings.SystemSettingsSearchEntry
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchPanelAppDisplayStyle
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INITIAL_VISIBLE_COUNT = 1
private const val HISTORY_INITIAL_VISIBLE_COUNT = 3
private const val APP_LIST_INITIAL_VISIBLE_COUNT = 3
private val ExpandedCardMaxHeight = 280.dp
private val LeadingSlotSize = 40.dp
private val ContactActionButtonSize = 36.dp
private val ContactActionIconSize = 20.dp
private val AppIconCandidateSize = 48.dp
private val AppIconCandidateItemWidth = 64.dp
private val AppIconCandidateCorner = RoundedCornerShape(12.dp)
private const val THUMBNAIL_LOAD_SIZE_PX = 256

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchPanelCalculatorCard(
    expression: String,
    result: String,
    modifier: Modifier = Modifier,
    /**
     * 长按拖拽：带上「计算式 = 结果」整串；null / 返回 false = 维持旧的"长按也复制"。
     *
     * 计算结果被截断时界面上会带一个 `…`（见 `CalculatorUtils.formatResult`），
     * 复制和拖拽都必须去掉，否则粘到别的应用里就是无效数字。
     */
    onLongPressDrag: ((expression: String, result: String) -> Boolean)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val cleanResult = result.trimEnd('…')
    fun copyResult() {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText("calculator", cleanResult).toClipEntry())
        }
    }
    // 拖拽没接上（流转栏关闭 / 起拖失败）时退回复制，别让长按变成没反应。
    val longClick: () -> Unit = {
        if (onLongPressDrag?.invoke(expression, result) != true) copyResult()
    }
    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_calculator),
        itemCount = 1,
        scrollWhenExpanded = false,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = ::copyResult,
                    onLongClick = longClick,
                )
                .padding(horizontal = 14.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "= $result",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = expression,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun SearchPanelLinkResultCards(
    urls: List<String>,
    onOpenUrl: (url: String, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (urls.isEmpty()) return
    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_links),
        itemCount = urls.size,
        scrollWhenExpanded = urls.size > 4,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        val hosts = remember(urls) { PickResultUrl.linkDisplayLabels(urls) }
        urls.forEachIndexed { index, url ->
            val host = hosts[index]
            SearchPanelResultCard(
                title = stringResource(R.string.search_panel_open_link_host, host),
                subtitle = url,
                leadingIcon = Icons.Outlined.Link,
                longPressEnabled = longPressEnabled,
                onClick = { onOpenUrl(url, false) },
                onLongClick = { onOpenUrl(url, true) },
            )
            if (index < urls.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelWebSuggestionsCard(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(suggestions) { mutableStateOf(false) }
    SearchPanelQueryListCard(
        queries = suggestions,
        leadingIcon = Icons.Rounded.NorthWest,
        onQueryClick = onSuggestionClick,
        sectionTitle = stringResource(R.string.search_panel_card_web_suggestions),
        expanded = expanded,
        onExpandedChange = { expanded = it },
        collapseThreshold = 4,
        modifier = modifier,
    )
}

@Composable
fun SearchPanelSearchHistoryCard(
    queries: List<String>,
    onQueryClick: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchPanelQueryListCard(
        queries = queries,
        leadingIcon = Icons.Outlined.History,
        onQueryClick = onQueryClick,
        sectionTitle = stringResource(R.string.search_panel_card_search_history),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        collapseThreshold = HISTORY_INITIAL_VISIBLE_COUNT,
        modifier = modifier,
    )
}

@Composable
private fun SearchPanelQueryListCard(
    queries: List<String>,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onQueryClick: (String) -> Unit,
    sectionTitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    collapseThreshold: Int,
    modifier: Modifier = Modifier,
) {
    if (queries.isEmpty()) return
    val displayQueries = if (expanded) queries else queries.take(collapseThreshold)
    val canExpand = queries.size > collapseThreshold
    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = sectionTitle,
        itemCount = queries.size,
        expanded = expanded,
        onToggleExpand = if (canExpand) {
            { onExpandedChange(!expanded) }
        } else {
            null
        },
        collapseThreshold = collapseThreshold,
        scrollWhenExpanded = expanded && queries.size > collapseThreshold,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        displayQueries.forEachIndexed { index, query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQueryClick(query) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            CircleShape,
                        )
                        .padding(6.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index < queries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchPanelAppResultCards(
    apps: List<AppInfo>,
    style: SearchPanelAppDisplayStyle,
    settings: AppSettings,
    onLaunchApp: (AppInfo, longPressTriggered: Boolean) -> Unit,
    onAppQuickAction: (AppInfo, SearchPanelAppQuickAction) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    longPressEnabled: Boolean = false,
) {
    if (apps.isEmpty()) return
    when (style) {
        SearchPanelAppDisplayStyle.ICONS -> SearchPanelAppIconStrip(
            apps = apps,
            settings = settings,
            onLaunchApp = onLaunchApp,
            onAppQuickAction = onAppQuickAction,
            modifier = modifier,
            longPressEnabled = longPressEnabled,
        )
        SearchPanelAppDisplayStyle.LIST -> SearchPanelAppListCards(
            apps = apps,
            settings = settings,
            onLaunchApp = onLaunchApp,
            onAppQuickAction = onAppQuickAction,
            modifier = modifier,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            longPressEnabled = longPressEnabled,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPanelAppIconStrip(
    apps: List<AppInfo>,
    settings: AppSettings,
    onLaunchApp: (AppInfo, longPressTriggered: Boolean) -> Unit,
    onAppQuickAction: (AppInfo, SearchPanelAppQuickAction) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_local_apps),
        itemCount = apps.size,
        expanded = true,
        onToggleExpand = null,
        collapseThreshold = apps.size,
        scrollWhenExpanded = false,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            apps.forEach { app ->
                val context = LocalContext.current
                val appState = com.slideindex.app.overlay.searchpanel.SearchPanelHostAccess.host?.freezerState(context, app)
        ?: com.slideindex.app.freezer.FreezerAppState.ACTIVE
                SearchPanelAppQuickActionTarget(
                    packageName = app.packageName,
                    cloneUserId = app.cloneUserId,
                    enabled = true,
                    settings = settings,
                    onAction = { onAppQuickAction(app, it) },
                    modifier = Modifier.width(AppIconCandidateItemWidth),
                ) {
                    Column(
                        modifier = Modifier.combinedClickable(onClick = { onLaunchApp(app, false) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SearchPanelAppIcon(
                            packageName = app.packageName,
                            contentDescription = app.label,
                            size = AppIconCandidateSize,
                            corner = AppIconCandidateCorner,
                            dimmed = !appState.isActive,
                            state = appState,
                            isClone = app.isClone,
                        )
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = if (appState.isActive) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPanelAppListCards(
    apps: List<AppInfo>,
    settings: AppSettings,
    onLaunchApp: (AppInfo, longPressTriggered: Boolean) -> Unit,
    onAppQuickAction: (AppInfo, SearchPanelAppQuickAction) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    longPressEnabled: Boolean = false,
) {
    val displayApps = if (expanded) apps else apps.take(APP_LIST_INITIAL_VISIBLE_COUNT)

    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_local_apps),
        itemCount = apps.size,
        expanded = expanded,
        onToggleExpand = if (apps.size > APP_LIST_INITIAL_VISIBLE_COUNT) {
            { onExpandedChange(!expanded) }
        } else {
            null
        },
        collapseThreshold = APP_LIST_INITIAL_VISIBLE_COUNT,
        scrollWhenExpanded = expanded && apps.size > APP_LIST_INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        displayApps.forEachIndexed { index, app ->
            val context = LocalContext.current
            val appState = com.slideindex.app.overlay.searchpanel.SearchPanelHostAccess.host?.freezerState(context, app)
        ?: com.slideindex.app.freezer.FreezerAppState.ACTIVE
            SearchPanelResultCard(
                title = app.label,
                subtitle = null,
                titleColor = if (appState.isActive) {
                    null
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                leading = {
                    SearchPanelAppQuickActionTarget(
                        packageName = app.packageName,
                        cloneUserId = app.cloneUserId,
                        enabled = true,
                        settings = settings,
                        onAction = { onAppQuickAction(app, it) },
                    ) {
                        SearchPanelAppIcon(
                            packageName = app.packageName,
                            contentDescription = app.label,
                            size = LeadingSlotSize,
                            corner = RoundedCornerShape(8.dp),
                            dimmed = !appState.isActive,
                            state = appState,
                            isClone = app.isClone,
                        )
                    }
                },
                longPressEnabled = longPressEnabled,
                onClick = { onLaunchApp(app, false) },
                onLongClick = { onLaunchApp(app, true) },
            )
            if (index < displayApps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun SearchPanelAppIcon(
    packageName: String,
    contentDescription: String,
    size: Dp,
    corner: RoundedCornerShape,
    dimmed: Boolean = false,
    state: com.slideindex.app.freezer.FreezerAppState = com.slideindex.app.freezer.FreezerAppState.ACTIVE,
    isClone: Boolean = false,
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) {
        mutableStateOf(
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.peekPickerIconBitmap?.invoke(packageName),
        )
    }
    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            iconBitmap = com.slideindex.app.overlay.panels.PanelRuntimeHooks.pickerIconBitmap
                        ?.invoke(context, packageName)
        }
    }
    val grayscale = if (dimmed) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    } else {
        null
    }
    Box(modifier = Modifier.size(size)) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(corner),
                contentScale = ContentScale.Crop,
                colorFilter = grayscale,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        corner,
                    )
                    .padding(8.dp)
                    .then(if (dimmed) Modifier.alpha(0.38f) else Modifier),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        // 分身标记：与索引 / 任务切换器 / 功能环共用同一套位置与尺寸。
        if (isClone) {
            CloneBadgeOverlay(
                iconSize = size,
                circular = false,
            )
        }
        // 冻结 / 暂停标记：右下角，与冰箱网格一致（暂停 = 双竖杠，冻结 = 雪花）。
        if (state.isPaused || state.isFrozen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.isPaused) Icons.Default.Pause else Icons.Default.AcUnit,
                    contentDescription = stringResource(
                        if (state.isPaused) R.string.freezer_status_paused else R.string.freezer_status_frozen,
                    ),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun SearchPanelSettingsResultCards(
    entries: List<SystemSettingsSearchEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLaunchEntry: (SystemSettingsSearchEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (entries.isEmpty()) return
    val displayEntries = if (expanded) entries else entries.take(INITIAL_VISIBLE_COUNT)

    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_settings),
        itemCount = entries.size,
        expanded = expanded,
        onToggleExpand = if (entries.size > INITIAL_VISIBLE_COUNT) {
            { onExpandedChange(!expanded) }
        } else {
            null
        },
        collapseThreshold = INITIAL_VISIBLE_COUNT,
        scrollWhenExpanded = expanded && entries.size > INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        displayEntries.forEachIndexed { index, entry ->
            SearchPanelResultCard(
                title = entry.title,
                subtitle = entry.subtitle,
                leadingIcon = Icons.Default.Settings,
                longPressEnabled = longPressEnabled,
                onClick = { onLaunchEntry(entry, false) },
                onLongClick = { onLaunchEntry(entry, true) },
            )
            if (index < displayEntries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelContactResultCards(
    contacts: List<ContactSearchEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLaunchContact: (ContactSearchEntry, longPressTriggered: Boolean) -> Unit,
    onCallContact: (ContactSearchEntry) -> Unit,
    onSmsContact: (ContactSearchEntry) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (contacts.isEmpty()) return
    val displayContacts = if (expanded) contacts else contacts.take(INITIAL_VISIBLE_COUNT)

    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_contacts),
        itemCount = contacts.size,
        expanded = expanded,
        onToggleExpand = if (contacts.size > INITIAL_VISIBLE_COUNT) {
            { onExpandedChange(!expanded) }
        } else {
            null
        },
        collapseThreshold = INITIAL_VISIBLE_COUNT,
        scrollWhenExpanded = expanded && contacts.size > INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        displayContacts.forEachIndexed { index, contact ->
            SearchPanelContactResultRow(
                contact = contact,
                longPressEnabled = longPressEnabled,
                onLaunchContact = onLaunchContact,
                onCallContact = onCallContact,
                onSmsContact = onSmsContact,
            )
            if (index < displayContacts.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelFileResultCards(
    files: List<DeviceFileEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenFile: (DeviceFileEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (files.isEmpty()) return
    val displayFiles = if (expanded) files else files.take(INITIAL_VISIBLE_COUNT)

    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_files),
        itemCount = files.size,
        expanded = expanded,
        onToggleExpand = if (files.size > INITIAL_VISIBLE_COUNT) {
            { onExpandedChange(!expanded) }
        } else {
            null
        },
        collapseThreshold = INITIAL_VISIBLE_COUNT,
        scrollWhenExpanded = expanded && files.size > INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        displayFiles.forEachIndexed { index, file ->
            SearchPanelResultCard(
                title = file.displayName,
                subtitle = file.relativePath?.trimEnd('/'),
                leading = {
                    SearchPanelFileLeadingIcon(file = file)
                },
                longPressEnabled = longPressEnabled,
                onClick = { onOpenFile(file, false) },
                onLongClick = { onOpenFile(file, true) },
            )
            if (index < displayFiles.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelPermissionResultCard(
    label: String,
    leadingIcon: ImageVector,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchPanelFrostedCard(modifier = modifier) {
        SearchPanelResultCard(
            title = label,
            subtitle = null,
            leadingIcon = leadingIcon,
            longPressEnabled = false,
            onClick = onRequestPermission,
            onLongClick = {},
        )
    }
}

@Composable
private fun SearchPanelGroupedResultCard(
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
    itemCount: Int = 0,
    expanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
    collapseThreshold: Int = INITIAL_VISIBLE_COUNT,
    scrollWhenExpanded: Boolean,
    maxHeight: Dp,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    val showExpandControl = sectionTitle != null &&
        onToggleExpand != null &&
        itemCount > collapseThreshold
    SearchPanelFrostedCard(modifier = modifier) {
        if (sectionTitle != null) {
            SearchPanelSectionCardHeader(
                title = sectionTitle,
                count = itemCount,
                expanded = expanded,
                showExpandControl = showExpandControl,
                onToggleExpand = onToggleExpand ?: {},
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                    ),
                )
                .then(
                    if (scrollWhenExpanded) {
                        Modifier
                            .heightIn(max = maxHeight)
                            .verticalScroll(scrollState)
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPanelContactResultRow(
    contact: ContactSearchEntry,
    longPressEnabled: Boolean,
    onLaunchContact: (ContactSearchEntry, longPressTriggered: Boolean) -> Unit,
    onCallContact: (ContactSearchEntry) -> Unit,
    onSmsContact: (ContactSearchEntry) -> Unit,
) {
    val hasNumber = contact.phoneNumber.isNotBlank()
    val openWithLabel = stringResource(R.string.search_panel_contact_open_with)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = openWithLabel }
                .then(
                    if (longPressEnabled) {
                        Modifier.combinedClickable(
                            onClick = { onLaunchContact(contact, false) },
                            onLongClick = { onLaunchContact(contact, true) },
                        )
                    } else {
                        Modifier.clickable { onLaunchContact(contact, false) }
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchPanelContactAvatar(
                photoUri = contact.photoUri,
                displayName = contact.name,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contact.formattedPhone.isNotBlank()) {
                    Text(
                        text = contact.formattedPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        SearchPanelContactActionButton(
            icon = Icons.Rounded.Call,
            contentDescription = stringResource(R.string.search_panel_contact_action_call),
            enabled = hasNumber,
            onClick = { onCallContact(contact) },
        )
        SearchPanelContactActionButton(
            icon = Icons.Rounded.Sms,
            contentDescription = stringResource(R.string.search_panel_contact_action_sms),
            enabled = hasNumber,
            onClick = { onSmsContact(contact) },
        )
    }
}

@Composable
private fun SearchPanelContactActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Surface(
        modifier = Modifier
            .size(ContactActionButtonSize)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.06f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ContactActionIconSize),
                tint = tint,
            )
        }
    }
}

@Composable
private fun SearchPanelContactAvatar(
    photoUri: String?,
    displayName: String,
) {
    val context = LocalContext.current
    var contactPhoto by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoUri) {
        contactPhoto = photoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                ImageDecodeGuard.decodeUri(
                    context = context,
                    uri = uri.toUri(),
                    maxSidePx = ImageDecodeGuard.MAX_ICON_SIDE_PX,
                )?.asImageBitmap()
            }
        }
    }
    val initials = remember(displayName) { contactInitials(displayName) }
    Surface(
        modifier = Modifier.size(LeadingSlotSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (contactPhoto != null) {
                Image(
                    bitmap = contactPhoto!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SearchPanelFileLeadingIcon(file: DeviceFileEntry) {
    val context = LocalContext.current
    val fileType = remember(file.uri) { FileTypeUtils.getFileType(file) }
    val showThumbnail = !file.isDirectory &&
        (fileType == FileType.PICTURES || fileType == FileType.VIDEOS)
    val uriString = file.uri.toString()
    var thumbnailBitmap by remember(uriString) { mutableStateOf(com.slideindex.app.search.files.FileThumbnailCache.get(uriString)) }

    if (showThumbnail) {
        LaunchedEffect(uriString) {
            if (thumbnailBitmap != null) return@LaunchedEffect
            val imageBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .loadThumbnail(file.uri, Size(THUMBNAIL_LOAD_SIZE_PX, THUMBNAIL_LOAD_SIZE_PX), null)
                        .asImageBitmap()
                }.getOrNull()
            }
            if (imageBitmap != null) {
                com.slideindex.app.search.files.FileThumbnailCache.put(uriString, imageBitmap)
                thumbnailBitmap = imageBitmap
            }
        }
    }

    val fallbackIcon = when {
        file.isDirectory -> Icons.Default.Folder
        fileType == FileType.PICTURES -> Icons.Default.Image
        fileType == FileType.VIDEOS -> Icons.Default.VideoLibrary
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    if (thumbnailBitmap != null) {
        val alpha = remember(uriString) { Animatable(0f) }
        LaunchedEffect(uriString) {
            alpha.animateTo(1f, animationSpec = tween(200))
        }
        Box(modifier = Modifier.size(LeadingSlotSize)) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(LeadingSlotSize)
                    .alpha(1f - alpha.value),
                tint = MaterialTheme.colorScheme.primary,
            )
            Image(
                bitmap = thumbnailBitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .size(LeadingSlotSize)
                    .clip(RoundedCornerShape(8.dp))
                    .alpha(alpha.value),
                contentScale = ContentScale.Crop,
            )
        }
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            modifier = Modifier
                .size(LeadingSlotSize)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp),
                )
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchPanelResultCard(
    title: String,
    subtitle: String?,
    leadingIcon: ImageVector,
    longPressEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleEmphasized: Boolean = false,
) {
    SearchPanelResultCard(
        title = title,
        subtitle = subtitle,
        leading = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(LeadingSlotSize)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        longPressEnabled = longPressEnabled,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        titleEmphasized = titleEmphasized,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPanelResultCard(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    longPressEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleEmphasized: Boolean = false,
    titleColor: Color? = null,
) {
    val clickModifier = if (longPressEnabled) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.combinedClickable(onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (titleEmphasized) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (titleEmphasized) FontWeight.SemiBold else FontWeight.Medium,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = if (titleEmphasized) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun contactInitials(displayName: String): String {
    val parts = displayName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@Composable
fun SearchPanelShortcutResultCards(
    shortcuts: List<ShortcutSearchEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLaunchShortcut: (ShortcutSearchEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (shortcuts.isEmpty()) return
    val displayShortcuts = if (expanded) shortcuts else shortcuts.take(INITIAL_VISIBLE_COUNT)

    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_shortcuts),
        itemCount = shortcuts.size,
        expanded = expanded,
        onToggleExpand = if (shortcuts.size > INITIAL_VISIBLE_COUNT) {
            { onExpandedChange(!expanded) }
        } else {
            null
        },
        collapseThreshold = INITIAL_VISIBLE_COUNT,
        scrollWhenExpanded = expanded && shortcuts.size > INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        displayShortcuts.forEachIndexed { index, shortcut ->
            SearchPanelResultCard(
                title = shortcut.label,
                subtitle = shortcut.appLabel.takeIf { it.isNotBlank() },
                leading = {
                    SearchPanelAppIcon(
                        packageName = shortcut.packageName,
                        contentDescription = shortcut.appLabel,
                        size = LeadingSlotSize,
                        corner = RoundedCornerShape(8.dp),
                    )
                },
                longPressEnabled = longPressEnabled,
                onClick = { onLaunchShortcut(shortcut, false) },
                onLongClick = { onLaunchShortcut(shortcut, true) },
            )
            if (index < displayShortcuts.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelClipboardResultCards(
    entries: List<ClipboardEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClipboardEntryAction: (ClipboardEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (entries.isEmpty()) return
    val displayEntries = if (expanded) entries else entries.take(INITIAL_VISIBLE_COUNT)

    SearchPanelGroupedResultCard(
        modifier = modifier,
        sectionTitle = stringResource(R.string.search_panel_card_clipboard),
        itemCount = entries.size,
        expanded = expanded,
        onToggleExpand = if (entries.size > INITIAL_VISIBLE_COUNT) {
            { onExpandedChange(!expanded) }
        } else {
            null
        },
        collapseThreshold = INITIAL_VISIBLE_COUNT,
        scrollWhenExpanded = expanded && entries.size > INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
    ) {
        displayEntries.forEachIndexed { index, entry ->
            SearchPanelResultCard(
                title = clipboardEntryTitle(entry),
                subtitle = clipboardEntryRelativeTime(entry.createdAtEpochMs),
                leading = {
                    val hasImage = entry.hasImageContent()
                    Icon(
                        imageVector = if (hasImage) Icons.Default.Image else Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier
                            .size(LeadingSlotSize)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                longPressEnabled = longPressEnabled,
                onClick = { onClipboardEntryAction(entry, false) },
                onLongClick = { onClipboardEntryAction(entry, true) },
            )
            if (index < displayEntries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun clipboardEntryTitle(entry: ClipboardEntry): String {
    if (entry.hasImageContent()) {
        val text = entry.text.trim()
        if (text.isEmpty() || ClipboardHtmlParser.isImageSrc(text)) {
            return stringResource(R.string.search_panel_clipboard_image_entry)
        }
    }
    val raw = entry.text.trim().ifBlank { entry.uri?.trim().orEmpty() }
        .ifBlank { entry.intentUri?.trim().orEmpty() }
    if (raw.isEmpty()) return stringResource(R.string.search_panel_clipboard_image_entry)
    return raw.replace(CLIPBOARD_WHITESPACE, " ").take(CLIPBOARD_TITLE_MAX_CHARS)
}

@Composable
private fun clipboardEntryRelativeTime(epochMs: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> stringResource(R.string.stash_time_just_now)
        diffMs < 3_600_000L ->
            stringResource(R.string.stash_time_minutes_ago, (diffMs / 60_000L).toInt())
        diffMs < 86_400_000L ->
            stringResource(R.string.stash_time_hours_ago, (diffMs / 3_600_000L).toInt())
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = epochMs }
            val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
                if (locale.language == "zh") "M月d日" else "MMM d"
            } else {
                "yyyy/M/d"
            }
            SimpleDateFormat(pattern, locale).format(Date(epochMs))
        }
    }
}

private val CLIPBOARD_WHITESPACE = "\\s+".toRegex()
private const val CLIPBOARD_TITLE_MAX_CHARS = 200
