package com.slideindex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.nativeengine.NativeEnginePackVersionState
import com.slideindex.app.ocr.OcrModelDownloadPhase
import com.slideindex.app.ocr.OcrModelDownloadState
import com.slideindex.app.ocr.OcrModelDownloadStep
import com.slideindex.app.ocr.OcrModelEntry
import com.slideindex.app.ocr.vlm.VlmFormulaOcrEngine
import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.ocr.vlm.VlmProvider
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
import com.slideindex.app.ui.miuix.MiuixProgressCard
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.miuixGroupedRowInsets
import com.slideindex.app.ui.miuix.MiuixGlassSegmentedTabRow
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrModelSettingsScreen(
    settings: AppSettings,
    catalogModels: List<OcrModelEntry>,
    installedModelIds: Set<String>,
    downloadState: OcrModelDownloadState?,
    ocrEngineInstalled: Boolean,
    ocrEngineSizeBytes: Long,
    ocrEngineVersionState: NativeEnginePackVersionState? = null,
    onBack: () -> Unit,
    onSelectModel: (String) -> Unit,
    onClearSelectedModel: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onDeleteOcrEngine: () -> Unit,
    onOpenEngineManagement: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    vlmConfigManager: VlmOcrConfigManager? = null,
    onNavigateToVlmSettings: (providerId: String?) -> Unit = {},
    onSelectProviderAndModel: (provider: VlmProvider, model: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val defaultSystemPrompt = remember(context) {
        VlmFormulaOcrEngine.defaultSystemPrompt(context)
    }
    val clearSelectionText = stringResource(R.string.ocr_models_clear_selection)
    val localSectionTitle = stringResource(R.string.ocr_models_section_available)
    val localHint = stringResource(R.string.ocr_models_hint)
    val cloudSectionTitle = stringResource(R.string.ocr_models_section_cloud)
    val cloudHint = stringResource(R.string.ocr_models_cloud_hint)
    // VlmOcrConfigManager 直接读写 SharedPreferences，不参与 Compose 的观察体系：
    // 改 prefs 不会让界面重组。订阅 configVersion 并在其变化时重新读取，勾号 /
    // 「已就绪」/「当前模型」才能即时刷新。
    //
    // 不订阅的话，界面只在"别的写操作恰好触发了重组"时才更新——在云端各家之间切换时，
    // selectProviderAndModel 里 selectModel 写入的是同一个常量 "vlm-formula-qwen"，
    // DataStore 去重后不发射，于是勾号会滞后到重进页面才更新。
    val configVersion = if (vlmConfigManager == null) {
        0L
    } else {
        vlmConfigManager.configVersion.collectAsState().value
    }
    val activeProviderId = remember(vlmConfigManager, configVersion) {
        vlmConfigManager?.activeProviderId
    }
    val isVlmSelected = settings.floatBallOcrModelId == "vlm-formula-qwen"

    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(if (isVlmSelected) 1 else 0)
    }

    var showPromptDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember(showPromptDialog) {
        mutableStateOf(vlmConfigManager?.commonPrompt ?: defaultSystemPrompt)
    }

    val tabs = listOf(
        stringResource(R.string.ocr_tab_local),
        stringResource(R.string.ocr_tab_cloud),
    )

    val localModels = remember(catalogModels) {
        catalogModels.filter { it.engine != "vlm_formula" && it.id != "vlm-formula-qwen" }
    }

    val cloudCommonConfigTitle = stringResource(R.string.ocr_common_config_title)

    SettingsScreenScaffold(
        title = stringResource(R.string.ocr_models_title),
        subtitle = stringResource(R.string.ocr_models_subtitle),
        onBack = onBack,

        bottomContent = {
            MiuixGlassSegmentedTabRow(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                contentHorizontalPadding = 12.dp,
                verticalPadding = 6.dp,
            )
        },
    ) {

        if (selectedTabIndex == 0) {
            // ==================== 本地 (离线) TAB ====================
            MiuixListSettingsCard(
                keyPrefix = "ocr-local-download",
                items = listOf(
                    CardItem("wifi-only") {
                        SwitchPreference(
                            title = stringResource(R.string.ocr_download_wifi_only),
                            summary = stringResource(R.string.ocr_download_wifi_only_desc),
                            checked = settings.ocrDownloadWifiOnly,
                            enabled = true,
                            onCheckedChange = onWifiOnlyChange,
                        )
                    },
                    CardItem("ocr-engine") {
                        OcrEngineStatusRow(
                            ocrEngineInstalled = ocrEngineInstalled,
                            ocrEngineSizeBytes = ocrEngineSizeBytes,
                            ocrEngineVersionState = ocrEngineVersionState,
                            onOpenEngineManagement = onOpenEngineManagement,
                        )
                    },
                ),
            )

            downloadState?.let { state ->
                if (state.phase != OcrModelDownloadPhase.READY && state.modelId != "vlm-formula-qwen") {
                    LazySettingsItem(key = "ocr-download-progress") {
                        OcrModelDownloadProgressCard(state = state)
                    }
                }
            }

            settingsLazySmallTitle(
                key = "ocr-local-models-section",
                title = localSectionTitle,
            )

            groupedCardItems(
                keyPrefix = "ocr-local-models",
                items = localModels.map { model ->
                    CardItem("local-model-${model.id}") {
                        OcrModelRow(
                            model = model,
                            installed = model.id in installedModelIds,
                            selected = settings.floatBallOcrModelId == model.id,
                            downloading = downloadState?.modelId == model.id &&
                                downloadState.phase != OcrModelDownloadPhase.READY &&
                                downloadState.phase != OcrModelDownloadPhase.FAILED &&
                                downloadState.phase != OcrModelDownloadPhase.CANCELLED,
                            onSelect = { onSelectModel(model.id) },
                            onDownload = { onDownloadModel(model.id) },
                            onDelete = { onDeleteModel(model.id) },
                        )
                    }
                },
            )

            if (settings.floatBallOcrModelId.isNotBlank() && !isVlmSelected) {
                LazySettingsItem(key = "ocr-local-clear-selection") {
                    TextButton(
                        text = clearSelectionText,
                        onClick = onClearSelectedModel,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            settingsLazyTipCard(key = "ocr-local-models-hint",
                text = localHint,
            )
        } else {
            // ==================== 云端 (多厂商大模型) TAB ====================
            settingsLazySmallTitle(
                key = "ocr-cloud-providers-section",
                title = cloudSectionTitle,
            )

            groupedCardItems(
                keyPrefix = "ocr-cloud-providers",
                items = VlmProvider.entries.map { provider ->
                    val isProviderConfigured = vlmConfigManager?.isProviderConfigured(provider) == true
                    val isProviderActive = isVlmSelected && activeProviderId == provider.id
                    val currentProviderModel = vlmConfigManager?.getModel(provider) ?: provider.defaultModel
                    val isCustomPromptEnabled = vlmConfigManager?.isProviderPromptEnabled(provider) == true

                    CardItem("cloud-provider-${provider.id}") {
                        OcrCloudProviderRow(
                            provider = provider,
                            isConfigured = isProviderConfigured,
                            isActive = isProviderActive,
                            currentModel = currentProviderModel,
                            isCustomPromptEnabled = isCustomPromptEnabled,
                            onSelect = { onSelectProviderAndModel(provider, currentProviderModel) },
                            onOpenSettings = { onNavigateToVlmSettings(provider.id) },
                        )
                    }
                },
            )

            if (isVlmSelected) {
                LazySettingsItem(key = "ocr-cloud-clear-selection") {
                    TextButton(
                        text = clearSelectionText,
                        onClick = onClearSelectedModel,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            settingsLazySmallTitle(
                key = "ocr-cloud-prompt-section",
                title = cloudCommonConfigTitle,
            )

            MiuixListSettingsCard(
                keyPrefix = "ocr-cloud-prompt",
                items = listOf(
                    CardItem("common-prompt") {
                        val commonPromptSummary = if (vlmConfigManager?.isCommonPromptCustomized() == true) {
                            stringResource(R.string.ocr_common_prompt_customized)
                        } else {
                            stringResource(R.string.ocr_common_prompt_default)
                        }
                        OcrCommonPromptRow(
                            summary = commonPromptSummary,
                            onEdit = {
                                editingPrompt = vlmConfigManager?.commonPrompt ?: defaultSystemPrompt
                                showPromptDialog = true
                            },
                        )
                    },
                ),
            )

            settingsLazyHint(
                key = "ocr-cloud-models-hint",
                text = cloudHint,
            )
        }
    }

    val isDefault = editingPrompt.trim() == defaultSystemPrompt.trim()
    MiuixFormDialog(
        show = showPromptDialog,
        onDismissRequest = { showPromptDialog = false },
        title = stringResource(R.string.ocr_global_system_prompt),
        confirmText = stringResource(R.string.gesture_angle_save),
        onConfirm = {
            vlmConfigManager?.commonPrompt = editingPrompt.trim()
        },
        dismissText = stringResource(R.string.cancel),
        secondaryConfirmText = stringResource(R.string.ocr_restore_default),
        onSecondaryConfirm = {
            editingPrompt = defaultSystemPrompt
        },
        secondaryConfirmEnabled = !isDefault,
        secondaryDismissOnConfirm = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixLabeledTextField(
                value = editingPrompt,
                onValueChange = { editingPrompt = it },
                label = stringResource(R.string.ocr_global_system_prompt_field),
                singleLine = false,
                minLines = 6,
                maxLines = 14,
            )
        }
    }
}

@Composable
private fun OcrModelDownloadProgressCard(state: OcrModelDownloadState) {
    MiuixProgressCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val fraction = state.progress
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = ocrDownloadProgressLabel(state),
                style = MiuixTheme.textStyles.body1,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OcrEngineStatusRow(
    ocrEngineInstalled: Boolean,
    ocrEngineSizeBytes: Long,
    ocrEngineVersionState: NativeEnginePackVersionState?,
    onOpenEngineManagement: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenEngineManagement)
            .miuixGroupedRowInsets(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.native_engine_pack_ocr),
                    style = MiuixTheme.textStyles.title4,
                )
                val statusText = if (ocrEngineInstalled) {
                    stringResource(R.string.ocr_engine_status_ready)
                } else {
                    stringResource(R.string.ocr_engine_status_not_installed)
                }
                val statusColor = if (ocrEngineInstalled) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                }
                Text(
                    text = statusText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = statusColor,
                    modifier = Modifier
                        .background(
                            color = statusColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            val revisionStr = ocrEngineVersionState?.installedRevision?.let { " (v$it)" } ?: ""
            Text(
                text = if (ocrEngineInstalled) {
                    stringResource(
                        R.string.ocr_engine_installed_summary,
                        formatMegabytes(ocrEngineSizeBytes),
                        revisionStr,
                    )
                } else {
                    stringResource(
                        R.string.ocr_engine_not_installed_summary,
                        formatMegabytes(ocrEngineSizeBytes),
                    )
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        Button(
            onClick = onOpenEngineManagement,
            colors = if (ocrEngineInstalled) {
                ButtonDefaults.buttonColors()
            } else {
                ButtonDefaults.buttonColorsPrimary()
            },
        ) {
            Text(
                if (ocrEngineInstalled) {
                    stringResource(R.string.ocr_engine_manage)
                } else {
                    stringResource(R.string.ocr_engine_install)
                },
            )
        }
    }
}

@Composable
private fun OcrCommonPromptRow(
    summary: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .miuixGroupedRowInsets(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ocr_global_system_prompt),
                style = MiuixTheme.textStyles.title4,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        Button(onClick = onEdit) {
            Text(stringResource(R.string.ocr_edit))
        }
    }
}

@Composable
private fun OcrModelRow(
    model: OcrModelEntry,
    installed: Boolean,
    selected: Boolean,
    downloading: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowClickableModifier = if (installed) {
        Modifier.clickable { onSelect() }
    } else if (!downloading) {
        Modifier.clickable { onDownload() }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowClickableModifier)
            .miuixGroupedRowInsets(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = installed,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ocrModelDisplayName(model.id),
                style = MiuixTheme.textStyles.title4,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.ocr_model_meta,
                    formatMegabytes(model.totalDownloadBytes),
                    ocrModelDisplayDescription(model.id),
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    selected -> stringResource(R.string.ocr_model_status_selected)
                    installed -> stringResource(R.string.ocr_model_status_installed)
                    downloading -> stringResource(R.string.ocr_model_status_downloading)
                    else -> stringResource(R.string.ocr_model_status_not_installed)
                },
                style = MiuixTheme.textStyles.body2,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        if (installed) {
            Button(
                onClick = onDelete,
            ) {
                Text(stringResource(R.string.ocr_model_delete))
            }
        } else {
            Button(
                onClick = onDownload,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.ocr_model_download))
            }
        }
    }
}

@Composable
private fun OcrCloudProviderRow(
    provider: VlmProvider,
    isConfigured: Boolean,
    isActive: Boolean,
    currentModel: String,
    isCustomPromptEnabled: Boolean,
    onSelect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val rowClickableModifier = if (isConfigured) {
        Modifier.clickable { onSelect() }
    } else {
        Modifier.clickable { onOpenSettings() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowClickableModifier)
            .miuixGroupedRowInsets(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = isActive,
            onClick = null,
            enabled = isConfigured,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = provider.displayName(context),
                    style = MiuixTheme.textStyles.title4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val (badgeText, badgeColor) = when {
                    isActive -> stringResource(R.string.ocr_model_status_active) to MiuixTheme.colorScheme.primary
                    isConfigured -> stringResource(R.string.ocr_engine_status_ready) to MiuixTheme.colorScheme.primary
                    else -> stringResource(R.string.ocr_model_status_not_configured) to MiuixTheme.colorScheme.error
                }
                Text(
                    text = badgeText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = badgeColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .background(
                            color = badgeColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    isConfigured && isCustomPromptEnabled -> stringResource(
                        R.string.ocr_model_summary_custom_prompt,
                        currentModel,
                    )
                    isConfigured -> stringResource(
                        R.string.ocr_model_summary_inherit_global,
                        currentModel,
                    )
                    else -> provider.description(context)
                },
                style = MiuixTheme.textStyles.body2,
                color = if (isActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isConfigured) {
            Button(
                onClick = onOpenSettings,
            ) {
                Text(stringResource(R.string.ocr_settings))
            }
        } else {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.ocr_configure))
            }
        }
    }
}

@Composable
private fun ocrModelDisplayName(modelId: String): String = when (modelId) {
    "mlkit-chinese" -> stringResource(R.string.ocr_model_mlkit_chinese)
    "tesseract-chi-sim-eng" -> stringResource(R.string.ocr_model_tesseract_chi_sim_eng)
    "ppocrv5-arabic" -> stringResource(R.string.ocr_model_ppocrv5_arabic)
    "ppocrv6-tiny" -> stringResource(R.string.ocr_model_ppocrv6_tiny)
    "ppocrv6-small" -> stringResource(R.string.ocr_model_ppocrv6_small)
    "ppocrv6-medium" -> stringResource(R.string.ocr_model_ppocrv6_medium)
    else -> modelId
}

@Composable
private fun ocrModelDisplayDescription(modelId: String): String = when (modelId) {
    "mlkit-chinese" -> stringResource(R.string.ocr_model_mlkit_chinese_desc)
    "tesseract-chi-sim-eng" -> stringResource(R.string.ocr_model_tesseract_chi_sim_eng_desc)
    "ppocrv5-arabic" -> stringResource(R.string.ocr_model_ppocrv5_arabic_desc)
    "ppocrv6-tiny" -> stringResource(R.string.ocr_model_ppocrv6_tiny_desc)
    "ppocrv6-small" -> stringResource(R.string.ocr_model_ppocrv6_small_desc)
    "ppocrv6-medium" -> stringResource(R.string.ocr_model_ppocrv6_medium_desc)
    else -> ""
}

@Composable
private fun ocrDownloadProgressLabel(state: OcrModelDownloadState): String {
    val modelName = ocrModelDisplayName(state.modelId)
    val stepPrefix = when (state.step) {
        OcrModelDownloadStep.ENGINE ->
            stringResource(R.string.ocr_download_step_engine, state.stepIndex, state.stepCount)
        OcrModelDownloadStep.MODEL ->
            stringResource(R.string.ocr_download_step_model, state.stepIndex, state.stepCount)
    }
    return when (state.phase) {
        OcrModelDownloadPhase.DOWNLOADING -> {
            val downloaded = formatMegabytes(state.bytesDownloaded)
            val total = state.totalBytes?.let(::formatMegabytes)
            val detail = if (total != null) {
                stringResource(R.string.ocr_download_progress_bytes, modelName, downloaded, total)
            } else {
                stringResource(R.string.ocr_download_progress_indeterminate, modelName, downloaded)
            }
            if (state.stepCount > 1) "$stepPrefix $detail" else detail
        }
        OcrModelDownloadPhase.VERIFYING ->
            stringResource(R.string.ocr_download_verifying, modelName)
        OcrModelDownloadPhase.FINALIZING ->
            stringResource(R.string.ocr_download_finalizing, modelName)
        OcrModelDownloadPhase.FAILED ->
            stringResource(R.string.ocr_download_failed, state.errorMessage.orEmpty())
        OcrModelDownloadPhase.CANCELLED ->
            stringResource(R.string.ocr_download_cancelled)
        OcrModelDownloadPhase.READY ->
            stringResource(R.string.ocr_download_ready, modelName)
    }
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb < 10.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        "${mb.roundToInt()} MB"
    }
}

