@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchGroupKind
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixSettingsFabInset
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference

@Composable
fun SearchGroupMemberPickerScreen(
    kind: SearchGroupKind,
    candidates: List<SearchEngineConfig>,
    selectedIds: Set<String>,
    onBack: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val isImage = kind == SearchGroupKind.IMAGE_SHARE
    var query by remember { mutableStateOf("") }
    var selected by remember(selectedIds) { mutableStateOf(selectedIds) }
    val filtered = remember(candidates, query) {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            candidates
        } else {
            candidates.filter {
                it.name.contains(normalized, ignoreCase = true) ||
                    it.aliasCode?.contains(normalized, ignoreCase = true) == true
            }
        }
    }
    val memberTypeLabel = stringResource(
        if (isImage) R.string.search_group_member_type_image else R.string.search_group_member_type_text,
    )
    val pickerEmpty = stringResource(R.string.search_group_picker_empty)
    val confirmAction = {
        if (selected.isNotEmpty()) onConfirm(selected)
    }

    SettingsScreenScaffold(
        title = stringResource(
            if (isImage) R.string.search_group_image_picker_title else R.string.search_group_picker_title,
        ),
        onBack = onBack,
        floatingActionButton = {
            if (selected.isNotEmpty()) {
                MiuixSettingsFab(
                    onClick = confirmAction,
                    icon = MiuixIcons.Glass.Normal.Ok,
                    contentDescription = stringResource(R.string.search_group_picker_action, selected.size),
                )
            }
        },
        fabInset = MiuixSettingsFabInset,
    ) {
        LazySettingsItem(key = "search-group-picker-search") {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                hintResId = R.string.search_group_picker_search_hint,
            )
        }
        if (filtered.isEmpty()) {
            settingsLazyHint(
                key = "search-group-picker-empty",
                text = pickerEmpty,
            )
        } else {
            groupedCardItems(
                keyPrefix = "search-group-picker",
                items = filtered.map { engine ->
                    settingsCardItem("pick-${engine.id}") {
                        CheckboxPreference(
                            title = engine.name,
                            summary = memberTypeLabel,
                            checked = engine.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) {
                                    selected + engine.id
                                } else {
                                    selected - engine.id
                                }
                            },
                            startAction = {
                                SearchEngineIcon(
                                    engine = engine,
                                    modifier = Modifier.size(28.dp),
                                )
                            },
                            checkboxLocation = CheckboxLocation.End,
                        )
                    }
                },
            )
        }
    }
}
