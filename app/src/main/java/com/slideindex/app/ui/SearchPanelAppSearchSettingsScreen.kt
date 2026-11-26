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

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_apps),
        subtitle = stringResource(R.string.search_panel_app_search_desc),
        onBack = onBack,
    ) {
        item(key = "app-search-field") {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                MiuixLabeledTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.search_app_settings_search_label),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        groupedCardItems(
            keyPrefix = "search-app-filter",
            items = listOf(
                settingsCardScopeItem("filter-hidden") {
                    SettingSwitchRow(
                        title = stringResource(R.string.search_app_settings_only_hidden),
                        subtitle = stringResource(
                            R.string.search_app_settings_hidden_count,
                            hidden.size,
                        ),
                        checked = onlyHidden,
                        enabled = true,
                        onCheckedChange = { onlyHidden = it },
                    )
                },
            ),
        )
        if (visibleApps.isEmpty()) {
            item(key = "app-search-empty") {
                SmallTitle(stringResource(R.string.search_app_settings_empty))
            }
        } else {
            item(key = "app-search-title") {
                SmallTitle(stringResource(R.string.search_app_settings_alias_section))
            }
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
                            onClick = {
                                aliasTarget = app
                                aliasText = aliases[target].orEmpty()
                            },
                        )
                    }
                },
            )
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkAppIcon(packageName = app.packageName, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    hidden -> stringResource(R.string.search_app_settings_hidden_summary)
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
        TextButton(
            text = if (hidden) {
                stringResource(R.string.search_app_settings_show)
            } else {
                stringResource(R.string.search_app_settings_hide)
            },
            onClick = { onToggleHidden(!hidden) },
            minHeight = 36.dp,
        )
    }
}
