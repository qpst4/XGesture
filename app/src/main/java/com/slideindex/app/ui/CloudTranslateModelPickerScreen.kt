package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.ocr.vlm.VlmProvider
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.translate.CloudTranslateRemoteModelsUiState
import top.yukonga.miuix.kmp.basic.Button
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.groupedCardItems
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CloudTranslateModelPickerScreen(
    provider: VlmProvider,
    vlmConfigManager: VlmOcrConfigManager,
    remoteModelsState: CloudTranslateRemoteModelsUiState,
    onLoadRemoteModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(provider) {
        onLoadRemoteModels()
    }
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val currentModel = remember(provider) { vlmConfigManager.getTranslateModel(provider) }
    val trimmedQuery = searchQuery.trim()
    val baseList = remoteModelsState.modelIds
    val filtered = remember(baseList, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            baseList
        } else {
            baseList.filter { it.contains(trimmedQuery, ignoreCase = true) }
        }
    }
    val canUseCustomId = trimmedQuery.isNotEmpty() &&
        baseList.none { it.equals(trimmedQuery, ignoreCase = true) }

    val title = stringResource(R.string.cloud_translate_model_picker_title)
    val searchLabel = stringResource(R.string.cloud_translate_model_picker_search)
    val useCustomId = stringResource(R.string.cloud_translate_model_picker_use_custom, trimmedQuery)
    val emptyText = stringResource(R.string.cloud_translate_model_picker_empty)
    val loadingText = stringResource(R.string.cloud_translate_remote_models_loading)
    val listCountTitle = stringResource(
        R.string.cloud_translate_model_picker_list_count,
        filtered.size,
    )

    SettingsScreenScaffold(
        title = title,
        subtitle = provider.displayName(context),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "model-picker-search", title = searchLabel)
        item(key = "model-picker-search-card") {
            CardSegment(
                isFirst = true,
                isLast = true,
                insidePadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixLabeledTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = searchLabel,
                        singleLine = true,
                    )
                    if (canUseCustomId) {
                        Button(onClick = { onSelectModel(trimmedQuery) }) {
                            Text(useCustomId)
                        }
                    }
                }
            }
        }

        if (remoteModelsState.loading) {
            LazySettingsItem(key = "model-picker-loading") {
                Text(
                    text = loadingText,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        } else if (filtered.isEmpty()) {
            LazySettingsItem(key = "model-picker-empty") {
                Text(
                    text = emptyText,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        } else {
            settingsLazySmallTitle(
                key = "model-picker-list",
                title = listCountTitle,
            )
            groupedCardItems(
                keyPrefix = "model-picker",
                items = filtered.map { modelId ->
                    CardItem(modelId) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(modelId) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = modelId,
                                style = MiuixTheme.textStyles.body1,
                                modifier = Modifier.weight(1f),
                            )
                            if (modelId == currentModel) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}
