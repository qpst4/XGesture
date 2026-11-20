package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.ocr.vlm.VlmCustomPreset
import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.ocr.vlm.VlmProvider
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.translate.CloudTranslateRemoteModelsUiState
import com.slideindex.app.ui.viewmodel.CloudTranslateConnectionTestState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.slideindex.app.ui.miuix.CardSegment
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudTranslateSettingsScreen(
    vlmConfigManager: VlmOcrConfigManager,
    remoteModelsState: CloudTranslateRemoteModelsUiState,
    connectionTestState: CloudTranslateConnectionTestState,
    onLoadRemoteModels: (VlmProvider, forceRefresh: Boolean) -> Unit,
    onTestConnection: (VlmProvider) -> Unit,
    onApiCredentialsChanged: (VlmProvider) -> Unit,
    onBack: () -> Unit,
    onOpenModelPicker: (providerId: String) -> Unit,
) {
    val context = LocalContext.current
    val providerEntries = remember { VlmProvider.entries }
    var translateProvider by remember {
        mutableStateOf(vlmConfigManager.translateActiveProvider)
    }
    var translateModel by remember(translateProvider) {
        mutableStateOf(vlmConfigManager.getTranslateModel(translateProvider))
    }
    var apiKey by remember(translateProvider) {
        mutableStateOf(vlmConfigManager.getApiKey(translateProvider))
    }
    var baseUrl by remember(translateProvider) {
        mutableStateOf(vlmConfigManager.getBaseUrl(translateProvider))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, translateProvider) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onLoadRemoteModels(translateProvider, false)
                translateModel = vlmConfigManager.getTranslateModel(translateProvider)
                apiKey = vlmConfigManager.getApiKey(translateProvider)
                baseUrl = vlmConfigManager.getBaseUrl(translateProvider)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onSelectProvider: (VlmProvider) -> Unit = { provider ->
        translateProvider = provider
        vlmConfigManager.setTranslateActiveProvider(provider)
        translateModel = vlmConfigManager.getTranslateModel(provider)
        apiKey = vlmConfigManager.getApiKey(provider)
        baseUrl = vlmConfigManager.getBaseUrl(provider)
        onLoadRemoteModels(provider, false)
    }

    val onSaveApiKey: (String) -> Unit = { value ->
        apiKey = value
        vlmConfigManager.setApiKey(translateProvider, value.trim())
        onApiCredentialsChanged(translateProvider)
    }
    val onSaveBaseUrl: (String) -> Unit = { value ->
        baseUrl = value
        vlmConfigManager.setBaseUrl(translateProvider, value.trim())
        onApiCredentialsChanged(translateProvider)
    }
    // 一键预设：立即落盘并刷新远程模型列表，与手动改 baseUrl 的行为保持一致。
    val onApplyCustomPreset: (VlmCustomPreset) -> Unit = { preset ->
        vlmConfigManager.applyCustomTranslatePreset(preset)
        baseUrl = vlmConfigManager.getBaseUrl(translateProvider)
        translateModel = vlmConfigManager.getTranslateModel(translateProvider)
        onLoadRemoteModels(translateProvider, true)
    }

    val screenTitle = stringResource(R.string.cloud_translate_settings_title)
    val screenSubtitle = stringResource(R.string.cloud_translate_settings_subtitle)
    val providerSectionTitle = stringResource(R.string.cloud_translate_active_provider_section)
    val apiSectionTitle = stringResource(R.string.cloud_translate_api_section)
    val modelSectionTitle = stringResource(R.string.cloud_translate_model_section)
    val testConnectionLabel = stringResource(R.string.vlm_ocr_test_connection)
    val testingConnectionText = stringResource(R.string.vlm_ocr_testing_connection)
    val connectionSuccessPrefix = stringResource(R.string.vlm_ocr_connection_success_prefix)
    val remoteModelsNeedsApi = stringResource(R.string.cloud_translate_remote_models_needs_api)
    val hintText = stringResource(R.string.cloud_translate_settings_hint)
    val providerDropdownTitle = stringResource(R.string.cloud_translate_provider_dropdown)
    val modelPickerSubtitle = stringResource(R.string.cloud_translate_model_picker_entry_subtitle)
    val testModelHint = stringResource(
        R.string.cloud_translate_api_test_model_hint,
        translateModel,
    )
    val defaultEndpointHint = stringResource(
        R.string.vlm_ocr_default_endpoint,
        translateProvider.defaultBaseUrl,
    )
    val modelListStatus = when {
        remoteModelsState.loading -> stringResource(R.string.cloud_translate_remote_models_loading)
        !vlmConfigManager.isProviderConfigured(translateProvider) -> remoteModelsNeedsApi
        remoteModelsState.fromNetwork -> stringResource(
            R.string.cloud_translate_api_models_loaded,
            remoteModelsState.modelIds.size,
        )
        else -> stringResource(R.string.cloud_translate_remote_models_fallback)
    }

    val providerLabels = remember(providerEntries, context) {
        providerEntries.map { it.displayName(context) }
    }
    val selectedProviderIndex = providerEntries.indexOf(translateProvider).coerceAtLeast(0)
    val apiConfigured = vlmConfigManager.isProviderConfigured(translateProvider)

    SettingsScreenScaffold(
        title = screenTitle,
        subtitle = screenSubtitle,
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "cloud-translate-provider",
            title = providerSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "cloud-translate-provider-pick",
            items = buildList {
                add(
                    settingsCardScopeItem("provider-select") {
                        SettingDropdownRow(
                            icon = { label ->
                                Icon(
                                    Icons.Outlined.Key,
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            title = providerDropdownTitle,
                            items = providerLabels,
                            selectedIndex = selectedProviderIndex,
                            onSelectedIndexChange = { index ->
                                onSelectProvider(providerEntries[index])
                            },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "cloud-translate-api",
            title = apiSectionTitle,
        )
        item(key = "cloud-translate-api-card") {
            CardSegment(
                isFirst = true,
                isLast = true,
                insidePadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = translateProvider.websiteHint(context),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    if (translateProvider == VlmProvider.CUSTOM) {
                        Text(
                            text = stringResource(R.string.vlm_custom_preset_title),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VlmCustomPreset.translatePresets().forEach { preset ->
                                val isPresetSelected = vlmConfigManager
                                    .matchesCustomPreset(translateProvider, preset)
                                Button(
                                    onClick = { onApplyCustomPreset(preset) },
                                    colors = if (isPresetSelected) {
                                        ButtonDefaults.buttonColorsPrimary()
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    },
                                ) {
                                    Text(preset.displayName, style = MiuixTheme.textStyles.body2)
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.vlm_custom_preset_hint),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    MiuixLabeledTextField(
                        value = apiKey,
                        onValueChange = onSaveApiKey,
                        label = stringResource(R.string.vlm_ocr_api_key_label),
                        singleLine = true,
                    )
                    MiuixLabeledTextField(
                        value = baseUrl,
                        onValueChange = onSaveBaseUrl,
                        label = stringResource(R.string.vlm_ocr_base_url_label),
                        singleLine = true,
                    )
                    Text(
                        text = testModelHint,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    Text(
                        text = modelListStatus,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { onTestConnection(translateProvider) },
                            enabled = apiConfigured && !connectionTestState.testing,
                        ) {
                            if (connectionTestState.testing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                if (connectionTestState.testing) {
                                    testingConnectionText
                                } else {
                                    testConnectionLabel
                                },
                            )
                        }
                        connectionTestState.message?.let { status ->
                            Text(
                                text = status,
                                style = MiuixTheme.textStyles.body2,
                                color = if (status.startsWith(connectionSuccessPrefix)) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.error
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        settingsLazyTipCard(
            key = "cloud-translate-api-endpoint-hint",
            text = defaultEndpointHint,
        )

        settingsLazySmallTitle(
            key = "cloud-translate-model",
            title = modelSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "cloud-translate-model-pick",
            items = buildList {
                add(
                    settingsCardScopeItem("model-nav") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            title = translateModel.ifBlank {
                                stringResource(R.string.cloud_translate_model_label)
                            },
                            subtitle = modelPickerSubtitle,
                            onClick = { onOpenModelPicker(translateProvider.id) },
                        )
                    },
                )
            },
        )

        settingsLazyHint(
            key = "cloud-translate-hint",
            text = hintText,
        )
    }
}
