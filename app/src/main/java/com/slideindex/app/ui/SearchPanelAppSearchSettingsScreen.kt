package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.SearchAppPrefs
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.link.LinkAppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.slideindex.app.ui.miuix.MiuixGlassIconDropdown
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 搜索面板 →「应用」候选设置页。
 *
 * 承载反馈 #375 的两件事：
 * - **应用别名**：给应用起一个自己的名字，按别名也能搜到（点某一行即可编辑）；
 * - **从搜索中隐藏**：行尾的「眼睛」开关，隐藏后该应用不再出现在搜索结果里。
 *
 * 列表按需加载一次可搜索应用，改名/隐藏都是即时写本地偏好，不需要点保存。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelAppSearchSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    // 每次改名/隐藏后自增，用来重读偏好并重组列表。
    var revision by remember { mutableIntStateOf(0) }
    var aliasTarget by remember { mutableStateOf<AppInfo?>(null) }
    var aliasText by remember { mutableStateOf("") }
    // 只看已隐藏（反馈 #6）。
    var onlyHidden by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            runCatching { appRepository.getCachedAppsForSearch() }.getOrDefault(emptyList())
        }
    }

    val aliases = remember(revision) { SearchAppPrefs.aliases(context) }
    val hidden = remember(revision) { SearchAppPrefs.hiddenTargets(context) }
    val normalizedQuery = query.trim().lowercase()

    // 反馈 #6：隐藏项原来只能靠行尾按钮和一行小字分辨，长列表里根本找不到；
    // 这里补上搜索框 + 「只看已隐藏」筛选，并把已隐藏数量显示出来。
    val visibleApps = remember(apps, aliases, hidden, normalizedQuery, onlyHidden) {
        apps.filter { app ->
            val target = AppTarget.encode(app.packageName, app.cloneUserId)
            if (onlyHidden && target !in hidden) return@filter false
            if (normalizedQuery.isEmpty()) return@filter true
            val alias = aliases[target].orEmpty()
            app.label.lowercase().contains(normalizedQuery) ||
                app.packageName.lowercase().contains(normalizedQuery) ||
                alias.lowercase().contains(normalizedQuery)
        }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.search_panel_section_apps),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        pageHint = stringResource(R.string.search_panel_app_search_desc),
        hintResId = R.string.search_app_settings_search_label,
    ) {
        item(key = "app-search-filter") {
            // 反馈 #5：分类筛选（全部 / 已隐藏）+ 计数，不再靠一行开关。
            SearchAppFilterTabs(
                onlyHidden = onlyHidden,
                hiddenCount = hidden.size,
                onSelect = { onlyHidden = it },
            )
        }
        if (visibleApps.isEmpty()) {
            item(key = "app-search-empty") {
                SmallTitle(stringResource(R.string.search_app_settings_empty))
            }
        } else {
            groupedCardItems(
                keyPrefix = "search-app-settings",
                items = visibleApps.map { app ->
                    val target = AppTarget.encode(app.packageName, app.cloneUserId)
                    settingsCardScopeItem("app-${app.packageName}-${app.cloneUserId}") {
                        SearchAppPrefRow(
                            app = app,
                            alias = aliases[target].orEmpty(),
                            hidden = target in hidden,
                            onEditAlias = {
                                aliasTarget = app
                                aliasText = aliases[target].orEmpty()
                            },
                            onToggleHidden = { hide ->
                                SearchAppPrefs.setHidden(
                                    context,
                                    app.packageName,
                                    app.cloneUserId,
                                    hide,
                                )
                                revision++
                            },
                        )
                    }
                },
            )
        }
        if (hidden.isNotEmpty()) {
            item(key = "app-search-restore-all") {
                SearchAppRestoreAllRow(
                    count = hidden.size,
                    onRestoreAll = {
                        hidden.forEach { target ->
                            val parsed = AppTarget.parse(target)
                            SearchAppPrefs.setHidden(
                                context,
                                parsed.packageName,
                                parsed.cloneUserId,
                                false,
                            )
                        }
                        revision++
                    },
                )
            }
        }
    }

    val editing = aliasTarget
    if (editing != null) {
        MiuixFormDialog(
            show = true,
            title = stringResource(R.string.search_app_settings_alias_title),
            onDismissRequest = { aliasTarget = null },
            onConfirm = {
                SearchAppPrefs.setAlias(
                    context,
                    editing.packageName,
                    editing.cloneUserId,
                    aliasText,
                )
                revision++
                Toast.makeText(
                    context,
                    context.getString(R.string.search_app_settings_alias_saved, editing.label),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            confirmEnabled = true,
        ) {
            MiuixLabeledTextField(
                value = aliasText,
                onValueChange = { aliasText = it },
                label = stringResource(R.string.search_app_settings_alias_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = editing.label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun SearchAppPrefRow(
    app: AppInfo,
    alias: String,
    hidden: Boolean,
    onEditAlias: () -> Unit,
    onToggleHidden: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkAppIcon(packageName = app.packageName, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MiuixTheme.textStyles.title4,
                    color = if (hidden) {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceContainer
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hidden) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.search_app_settings_hidden_tag),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = when {
                    alias.isNotBlank() -> stringResource(
                        R.string.search_app_settings_alias_summary,
                        alias,
                    )
                    else -> app.packageName
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 行尾统一收进「…」菜单：设别名 / 隐藏或恢复，不再并排两个入口。
        MiuixGlassIconDropdown(
            entries = listOf(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = stringResource(R.string.search_app_settings_alias_title),
                            onClick = onEditAlias,
                        ),
                        DropdownItem(
                            text = if (hidden) {
                                stringResource(R.string.search_app_settings_show)
                            } else {
                                stringResource(R.string.search_app_settings_hide)
                            },
                            onClick = { onToggleHidden(!hidden) },
                        ),
                    ),
                ),
            ),
            icon = MiuixIcons.Medium.More,
            contentDescription = app.label,
            capsule = false,
        )
    }
}

@Composable
private fun SearchAppFilterTabs(
    onlyHidden: Boolean,
    hiddenCount: Int,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchAppFilterPill(
            label = stringResource(R.string.search_app_settings_filter_all),
            selected = !onlyHidden,
            onClick = { onSelect(false) },
        )
        SearchAppFilterPill(
            label = stringResource(
                R.string.search_app_settings_filter_hidden,
                hiddenCount,
            ),
            selected = onlyHidden,
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun SearchAppFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSurfaceContainer
            },
        )
    }
}

@Composable
private fun SearchAppRestoreAllRow(
    count: Int,
    onRestoreAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            text = stringResource(R.string.search_app_settings_restore_all, count),
            onClick = onRestoreAll,
            minHeight = 40.dp,
        )
    }
}
