package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.settings.HolographicAppMode
import com.slideindex.app.settings.HolographicLauncherSettings
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.ui.compose.collectPickerAppsAsState
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

/**
 * 全息启动器「球面应用」页。
 *
 * 版式照 Mishka 的「分应用代理」（`Mishka-main/.../AppProxyScreen.kt:354-420`）：
 * **上面一组三档单选 → 下面直接就是完整应用列表**，每行一个复选框切换"选中"。
 *
 * 不是「先挑进一个已选列表、再去另一个页面管理那个列表」：全程**只有一个集合**，
 * 「模式」决定它是白名单还是黑名单。所以不存在"两种模式都添加过、到底听谁的"这种状态。
 * 「允许所有应用」档不看该集合，因此那一档不显示列表，只给一句说明。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HolographicLauncherAppsScreen(
    selectedPackages: List<String>,
    mode: HolographicAppMode,
    onBack: () -> Unit,
    onAppModeChange: (HolographicAppMode) -> Unit,
    onAddApp: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onSetSelection: (List<String>) -> Unit,
) {
    // 全息启动器只是「列出来 → 点开启动」，不依赖前台识别，所以分身有意义：includeClones = true。
    // 选中列表存的仍是字符串，但允许是 包名\u0002userId 目标串（旧数据裸包名 = user 0）。
    val allApps by collectPickerAppsAsState(includeClones = true)
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(allApps) {
        if (allApps.isNotEmpty()) isLoading = false
    }

    val selectedSet = remember(selectedPackages) { selectedPackages.toSet() }
    val visibleApps = remember(allApps, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { app ->
                if (query.isEmpty()) return@filter true
                app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    app.pinyinKey.contains(query)
            }
            .sortedBy { it.pinyinKey }
    }

    val modes = HolographicAppMode.entries
    // `settingsLazySmallTitle` 是普通（非 @Composable）扩展函数，标题必须在 composable 作用域里先算好。
    val modeSectionTitle = stringResource(R.string.holographic_apps_mode_section)
    val appsSectionTitle = stringResource(
        R.string.holographic_apps_section_count,
        selectedSet.size,
        allApps.size,
    )
    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.holographic_apps_entry_title),
        pageHint = stringResource(R.string.holographic_apps_screen_desc),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
        extraActions = {
            // 一个按钮切换：全部选中 → 取消全选，否则 → 全选（作用范围是当前搜索结果）。
            if (mode != HolographicAppMode.ALL && visibleApps.isNotEmpty()) {
                val allSelected = visibleApps.all { holoAppKey(it) in selectedSet }
                MiuixTopBarIconButton(
                    onClick = {
                        if (allSelected) {
                            onSetSelection(selectedPackages.filterNot { key ->
                                visibleApps.any { holoAppKey(it) == key }
                            })
                        } else {
                            val visible = visibleApps.map { holoAppKey(it) }
                            onSetSelection((selectedPackages + visible).distinct())
                        }
                    },
                ) {
                    Icon(
                        if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        contentDescription = stringResource(
                            if (allSelected) {
                                R.string.holographic_apps_deselect_all
                            } else {
                                R.string.holographic_apps_select_all
                            }
                        ),
                    )
                }
            }
        },
    ) {
        settingsLazySmallTitle(
            key = "section-mode",
            title = modeSectionTitle,
        )
        items(modes.size, key = { "mode-${modes[it].id}" }) { index ->
            val entry = modes[index]
            RadioButtonPreference(
                modifier = Modifier.miuixGroupedCardItem(index, modes.size),
                title = stringResource(entry.titleRes()),
                summary = stringResource(entry.descRes()),
                selected = mode == entry,
                onClick = { onAppModeChange(entry) },
                radioButtonLocation = RadioButtonLocation.End,
            )
        }

        if (mode == HolographicAppMode.ALL) {
            item(key = "allow-all-note") {
                Text(
                    text = stringResource(R.string.holographic_apps_allow_all_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
            return@SettingsLazyScreenScaffoldWithExpandableSearch
        }

        settingsLazySmallTitle(
            key = "section-apps",
            title = appsSectionTitle,
        )
        when {
            isLoading -> {
                item(key = "loading") {
                    LoadingContent(
                        message = stringResource(R.string.loading),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            visibleApps.isEmpty() -> {
                item(key = "apps-empty") {
                    Text(
                        text = stringResource(R.string.no_apps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    )
                }
            }
            else -> {
                items(visibleApps.size, key = { holoAppKey(visibleApps[it]) }) { index ->
                    val app = visibleApps[index]
                    val appKey = holoAppKey(app)
                    val selected = appKey in selectedSet
                    val toggle = {
                        if (selected) onRemoveApp(appKey) else onAddApp(appKey)
                    }
                    // 每行**各自一张卡**（segmentIndex=0 / count=1 才会独立成卡）。
                    // 横向边距由 `miuixGroupedCardItem` 自带的 12dp 负责，**不要再叠一层** ——
                    // 叠了就是 24dp，比同一页的模式单选（12dp）更窄，看起来"边距不对"。
                    BasicComponent(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .miuixGroupedCardItem(0, 1),
                        title = app.label,
                        summary = app.packageName,
                        onClick = toggle,
                        startAction = { Md3PickerAppLeading(app) },
                        endActions = {
                            Checkbox(
                                state = if (selected) {
                                    ToggleableState.On
                                } else {
                                    ToggleableState.Off
                                },
                                onClick = toggle,
                            )
                        },
                    )
                }
            }
        }
    }
}

internal fun HolographicAppMode.titleRes(): Int = when (this) {
    HolographicAppMode.ALL -> R.string.holographic_app_mode_all
    HolographicAppMode.SELECTED_ONLY -> R.string.holographic_app_mode_selected
    HolographicAppMode.EXCLUDE_SELECTED -> R.string.holographic_app_mode_exclude
}

internal fun HolographicAppMode.descRes(): Int = when (this) {
    HolographicAppMode.ALL -> R.string.holographic_app_mode_all_desc
    HolographicAppMode.SELECTED_ONLY -> R.string.holographic_app_mode_selected_desc
    HolographicAppMode.EXCLUDE_SELECTED -> R.string.holographic_app_mode_exclude_desc
}

/** 球面布点上限：选中超过它的部分不会显示（与 `Ball3DView.MAX_BALL_APPS` 一致）。 */
@Suppress("unused")
internal const val HOLOGRAPHIC_BALLOON_LIMIT = HolographicLauncherSettings.BALLOON_APP_LIMIT
/** 全息选中列表的键：主实例 = 裸包名，分身 = 包名\u0002userId（与其它目标型入口一致）。 */
internal fun holoAppKey(app: com.slideindex.app.data.AppInfo): String =
    AppTarget.encode(app.packageName, app.cloneUserId)