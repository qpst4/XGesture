package com.slideindex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ocr.vlm.VlmCustomPreset
import com.slideindex.app.ocr.vlm.VlmFormulaOcrEngine
import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.ocr.vlm.VlmProvider
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.MiuixIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VlmOcrSettingsScreen(
    vlmConfigManager: VlmOcrConfigManager,
    providerId: String? = null,
    onBack: () -> Unit,
    onUpdateVlmConfig: (
        apiKey: String,
        baseUrl: String,
        model: String,
        promptEnabled: Boolean,
        promptDraft: String,
    ) -> Unit,
    onUpdateProviderConfig: ((
        provider: com.slideindex.app.ocr.vlm.VlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String,
        promptEnabled: Boolean,
        promptDraft: String,
    ) -> Unit)? = null,
    onNotifyWarning: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val targetProvider = remember(providerId) {
        com.slideindex.app.ocr.vlm.VlmProvider.fromId(providerId ?: vlmConfigManager.activeProviderId)
    }

    var apiKey by remember(targetProvider) { mutableStateOf(vlmConfigManager.getApiKey(targetProvider)) }
    var baseUrl by remember(targetProvider) { mutableStateOf(vlmConfigManager.getBaseUrl(targetProvider)) }
    var model by remember(targetProvider) { mutableStateOf(vlmConfigManager.getModel(targetProvider)) }
    var useCustomPrompt by remember(targetProvider) {
        mutableStateOf(vlmConfigManager.isProviderPromptEnabled(targetProvider))
    }
    var customPrompt by remember(targetProvider) {
        mutableStateOf(vlmConfigManager.getProviderPromptDraft(targetProvider))
    }
    val savedCustomModels = remember(targetProvider) {
        vlmConfigManager.getCustomModels(targetProvider)
            .filter { it !in targetProvider.presetModels }
            .sorted()
    }

    var isTesting by remember { mutableStateOf(false) }
    var testStatusText by remember { mutableStateOf<String?>(null) }
    var lastTestSucceeded by remember { mutableStateOf<Boolean?>(null) }

    val connectionTestFailedSaved = stringResource(R.string.vlm_ocr_connection_test_failed_saved)
    val connectionSuccessPrefix = stringResource(R.string.vlm_ocr_connection_success_prefix)
    val testingConnectionText = stringResource(R.string.vlm_ocr_testing_connection)
    val sectionApiTitle = stringResource(R.string.vlm_ocr_section_api)
    val sectionModelTitle = stringResource(R.string.vlm_ocr_section_model)
    val sectionPromptTitle = stringResource(R.string.vlm_ocr_section_prompt)
    val customPromptHint = stringResource(R.string.vlm_ocr_custom_prompt_hint, targetProvider.displayName(context))
    val defaultEndpointHint = stringResource(R.string.vlm_ocr_default_endpoint, targetProvider.defaultBaseUrl)

    val onSaveAndBack = {
        if (lastTestSucceeded == false) {
            onNotifyWarning?.invoke(connectionTestFailedSaved)
        }
        val promptDraft = customPrompt.trim()
        if (onUpdateProviderConfig != null) {
            onUpdateProviderConfig(
                targetProvider,
                apiKey.trim(),
                baseUrl.trim(),
                model.trim(),
                useCustomPrompt,
                promptDraft,
            )
        } else {
            vlmConfigManager.setApiKey(targetProvider, apiKey.trim())
            vlmConfigManager.setBaseUrl(targetProvider, baseUrl.trim())
            vlmConfigManager.setModel(targetProvider, model.trim())
            vlmConfigManager.setProviderPromptConfig(targetProvider, useCustomPrompt, promptDraft)
            vlmConfigManager.setActiveProvider(targetProvider)
            onUpdateVlmConfig(apiKey.trim(), baseUrl.trim(), model.trim(), useCustomPrompt, promptDraft)
        }
        onBack()
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.vlm_ocr_provider_config_title, targetProvider.displayName(context)),
        subtitle = targetProvider.description(context),
        onBack = onSaveAndBack,
        actions = {
            MiuixTopBarIconButton(onClick = onSaveAndBack) {
                Icon(
                    imageVector = MiuixIcons.Medium.Ok,
                    contentDescription = stringResource(R.string.gesture_angle_save),
                )
            }
        },
    ) {
        settingsLazySmallTitle(
            key = "section-api",
            title = sectionApiTitle,
        )

        LazySettingsItem(key = "api-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = targetProvider.websiteHint(context),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )

                    if (targetProvider == VlmProvider.CUSTOM) {
                        Text(
                            text = stringResource(R.string.vlm_custom_preset_title),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VlmCustomPreset.visionPresets().forEach { preset ->
                                val isPresetSelected =
                                    baseUrl.trim().trimEnd('/') == preset.baseUrl.trimEnd('/')
                                Button(
                                    onClick = {
                                        baseUrl = preset.baseUrl
                                        model = preset.visionModel
                                        testStatusText = null
                                        lastTestSucceeded = null
                                    },
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
                        onValueChange = {
                            apiKey = it
                            testStatusText = null
                            lastTestSucceeded = null
                        },
                        label = stringResource(R.string.vlm_ocr_api_key_label),
                        singleLine = true,
                    )

                    MiuixLabeledTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            testStatusText = null
                            lastTestSucceeded = null
                        },
                        label = stringResource(R.string.vlm_ocr_base_url_label),
                        singleLine = true,
                    )
                }
            }
        }

        settingsLazyTipCard(key = "api-hint",
            text = defaultEndpointHint,
        )

        settingsLazySmallTitle(
            key = "section-model",
            title = sectionModelTitle,
        )

        LazySettingsItem(key = "model-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixLabeledTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            testStatusText = null
                            lastTestSucceeded = null
                        },
                        label = stringResource(R.string.vlm_ocr_model_label),
                        singleLine = true,
                    )

                    Text(
                        text = stringResource(R.string.vlm_ocr_recommended_models),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        targetProvider.presetModels.forEach { presetModelName ->
                            val isSelected = model.trim() == presetModelName
                            Button(
                                onClick = {
                                    model = presetModelName
                                    testStatusText = null
                                    lastTestSucceeded = null
                                },
                                colors = if (isSelected) {
                                    ButtonDefaults.buttonColorsPrimary()
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                            ) {
                                Text(presetModelName, style = MiuixTheme.textStyles.body2)
                            }
                        }
                    }

                    if (savedCustomModels.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.vlm_ocr_saved_custom_models),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            savedCustomModels.forEach { customModelName ->
                                val isSelected = model.trim() == customModelName
                                Button(
                                    onClick = {
                                        model = customModelName
                                        testStatusText = null
                                        lastTestSucceeded = null
                                    },
                                    colors = if (isSelected) {
                                        ButtonDefaults.buttonColorsPrimary()
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    },
                                ) {
                                    Text(customModelName, style = MiuixTheme.textStyles.body2)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                isTesting = true
                                testStatusText = testingConnectionText
                                coroutineScope.launch {
                                    val (success, msg) = VlmFormulaOcrEngine.testConnection(
                                        context = context,
                                        apiKey = apiKey.trim(),
                                        baseUrl = baseUrl.trim(),
                                        model = model.trim(),
                                    )
                                    testStatusText = msg
                                    lastTestSucceeded = success
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(stringResource(R.string.vlm_ocr_test_connection))
                        }

                        testStatusText?.let { status ->
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

        settingsLazySmallTitle(
            key = "section-prompt",
            title = sectionPromptTitle,
        )

        LazySettingsItem(key = "prompt-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.vlm_ocr_custom_prompt_title),
                        summary = if (useCustomPrompt) {
                            stringResource(R.string.vlm_ocr_custom_prompt_enabled)
                        } else {
                            stringResource(R.string.vlm_ocr_custom_prompt_disabled)
                        },
                        checked = useCustomPrompt,
                        onCheckedChange = { useCustomPrompt = it },
                    )

                    AnimatedVisibility(
                        visible = useCustomPrompt,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MiuixLabeledTextField(
                                value = customPrompt,
                                onValueChange = { customPrompt = it },
                                label = stringResource(R.string.vlm_ocr_custom_prompt_label),
                                singleLine = false,
                                minLines = 5,
                                maxLines = 12,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    text = stringResource(R.string.vlm_ocr_load_generic_template),
                                    onClick = {
                                        customPrompt = vlmConfigManager.commonPrompt
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        settingsLazyHint(
            key = "prompt-hint",
            text = customPromptHint,
        )
    }
}
