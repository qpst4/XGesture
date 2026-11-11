package com.slideindex.app.ui



import androidx.compose.foundation.Image

import androidx.compose.foundation.layout.size


import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate

import androidx.compose.material.icons.outlined.ViewAgenda

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.Icon

import androidx.compose.runtime.Composable

import androidx.compose.runtime.remember

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.slideindex.app.R

import com.slideindex.app.settings.AggregatedImageSearchEnginePreferencesStore
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore

import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.searchengine.defaultSearchEngineCandidates
import com.slideindex.app.ui.searchengine.defaultSearchEngineItemLabel

import com.slideindex.app.ui.settings.components.settingsCardScopeItem

import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

import com.slideindex.app.ui.settings.components.settingsLazySectionIntro

import com.slideindex.app.ui.settings.components.settingsLazyTipCard

import com.slideindex.app.ui.settings.components.SettingNavigationRow

import com.slideindex.app.ui.settings.components.SettingDropdownRow

import com.slideindex.app.ui.settings.components.SettingSpinnerRow

import com.slideindex.app.ui.settings.components.SettingSwitchRow

import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

import com.slideindex.app.ui.viewmodel.ImageViewerDropdownOption

import com.slideindex.app.ui.viewmodel.ImageViewerOptionsState

import top.yukonga.miuix.kmp.basic.DropdownItem



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun FloatBallPickSettingsScreen(

    settings: AppSettings,

    historyCount: Int,

    imageViewerOptions: ImageViewerOptionsState,

    onOcrFallbackChange: (Boolean) -> Unit,

    onShareImageOcrHistoryEnabledChange: (Boolean) -> Unit,

    onDefaultImageViewerPackageChange: (String?) -> Unit,

    onImageEditorDelayDeleteChange: (Boolean) -> Unit = {},

    onOpenOcrModels: () -> Unit,

    onOpenShareImageOcrHistory: () -> Unit,

    onOpenTranslationSettings: () -> Unit,

    onOpenPanelLayoutBehaviorSettings: () -> Unit,

    onOpenPickHandfeelSettings: () -> Unit,

    onOpenSearchEngineSettings: () -> Unit,

    onOpenImageSearchEngineSettings: () -> Unit,

    onDefaultSearchEngineChange: (String?) -> Unit,

    onBack: () -> Unit,

) {

    val askEveryTimeLabel = stringResource(R.string.image_viewer_ask_every_time)

    val readyOptions = (imageViewerOptions as? ImageViewerOptionsState.Ready)?.options

    val imageViewerItems = remember(readyOptions) {

        readyOptions?.map { option -> option.toDropdownItem() }

            ?: listOf(DropdownItem(text = askEveryTimeLabel))

    }

    val selectedImageViewerIndex = remember(settings.defaultImageViewerPackage, readyOptions) {

        settings.defaultImageViewerPackage?.let { pkg ->

            readyOptions?.indexOfFirst { it.packageName == pkg }

                ?.takeIf { it >= 0 }

        } ?: 0

    }

    val imageViewerSubtitle = when (imageViewerOptions) {

        ImageViewerOptionsState.Loading -> stringResource(R.string.loading)

        is ImageViewerOptionsState.Ready ->

            imageViewerItems

                .getOrNull(selectedImageViewerIndex.coerceIn(0, imageViewerItems.lastIndex))

                ?.text

                ?: askEveryTimeLabel

    }

    val ocrTranslationSectionTitle = stringResource(R.string.pick_settings_section_ocr_translation)

    val longImageHistorySectionTitle = stringResource(R.string.pick_settings_section_long_image_history)

    val imageOpenEditSectionTitle = stringResource(R.string.pick_settings_section_image_open_edit)

    val panelLayoutBehaviorTitle = stringResource(R.string.pick_settings_panel_layout_behavior_title)

    val panelLayoutBehaviorNavSubtitle = stringResource(R.string.pick_settings_panel_layout_behavior_nav_subtitle)

    val howToTriggerTip = stringResource(R.string.pick_settings_how_to_trigger_tip)

    val pickHandfeelTitle = stringResource(R.string.pick_handfeel_settings_title)

    val pickHandfeelNavSubtitle = stringResource(R.string.pick_settings_advanced_nav_subtitle)

    val imageOpenEditHint = stringResource(R.string.pick_settings_image_open_edit_hint)

    val searchSectionTitle = stringResource(R.string.pick_settings_section_search)

    val defaultEngineCandidates = remember(settings.searchEngines) {
        defaultSearchEngineCandidates(settings.searchEngines)
    }

    val noneEngineLabel = stringResource(R.string.search_panel_default_engine_none)

    val defaultEngineItems = listOf(noneEngineLabel) +
        defaultEngineCandidates.map { defaultSearchEngineItemLabel(it) }

    val defaultEngineIndex = if (settings.floatBallPickDefaultSearchEngineId == null) {

        0

    } else {

        defaultEngineCandidates.indexOfFirst { it.id == settings.floatBallPickDefaultSearchEngineId }.let { idx ->

            if (idx >= 0) idx + 1 else 0

        }

    }



    SettingsScreenScaffold(

        title = stringResource(R.string.float_ball_pick_settings_title),

        pageHint = stringResource(R.string.pick_settings_page_hint),

        onBack = onBack,

    ) {

        settingsLazyTipCard(

            key = "pick-how-to-trigger-tip",

            text = howToTriggerTip,

        )

        // 布局与行为、拾取与手感都是取词页的子页入口，合成一张卡：面板在前、手感在后。
        groupedCardItems(

            keyPrefix = "fb-pick-hub",

            items = listOf(

                settingsCardScopeItem("panel-layout-behavior") {

                    SettingNavigationRow(

                        icon = { label -> SymbolsIcon("ViewAgenda", contentDescription = label) },

                        title = panelLayoutBehaviorTitle,

                        subtitle = panelLayoutBehaviorNavSubtitle,

                        enabled = true,

                        onClick = onOpenPanelLayoutBehaviorSettings,

                    )

                },

                settingsCardScopeItem("pick-handfeel") {

                    SettingNavigationRow(

                        icon = { label -> SymbolsIcon("TouchApp", contentDescription = label) },

                        title = pickHandfeelTitle,

                        subtitle = pickHandfeelNavSubtitle,

                        enabled = true,

                        onClick = onOpenPickHandfeelSettings,

                    )

                },

            ),

        )

        settingsLazySmallTitle(

            key = "ocr-translation-section",

            title = ocrTranslationSectionTitle,

        )

        groupedCardItems(

            keyPrefix = "fb-pick-ocr-translation",

            items = listOf(

                settingsCardScopeItem("ocr") {

                    SettingSwitchNavigationRow(

                        title = stringResource(R.string.ocr_recognize_title),

                        subtitle = ocrModelSelectionSubtitle(settings.floatBallOcrModelId),

                        icon = { label -> SymbolsIcon("DocumentScanner", contentDescription = label) },

                        checked = settings.floatBallOcrFallbackEnabled,

                        enabled = true,

                        onCheckedChange = onOcrFallbackChange,

                        onNavigate = onOpenOcrModels,

                    )

                },

                settingsCardScopeItem("panel-translation") {

                    SettingNavigationRow(

                        icon = { label -> SymbolsIcon("Translate", contentDescription = label) },

                        title = stringResource(R.string.float_ball_translation_settings_title),

                        subtitle = pickPanelTranslationNavSubtitle(settings),

                        enabled = true,

                        onClick = onOpenTranslationSettings,

                    )

                },

            ),

        )

        settingsLazySmallTitle(

            key = "pick-search-section",

            title = searchSectionTitle,

        )

        groupedCardItems(

            keyPrefix = "fb-pick-search",

            items = buildList {

                add(

                    settingsCardScopeItem("search-engines") {

                        SettingNavigationRow(

                            icon = { label -> SymbolsIcon("Search", contentDescription = label) },

                            title = stringResource(R.string.search_engine_settings_title),

                            subtitle = pluralStringResource(

                                R.plurals.search_engine_settings_summary,

                                SearchEngineStore.textPickPanelEngines(settings.searchEngines).size,

                                SearchEngineStore.textPickPanelEngines(settings.searchEngines).size,

                            ),

                            enabled = true,

                            onClick = onOpenSearchEngineSettings,

                        )

                    }

                )

                add(

                    settingsCardScopeItem("default-search-engine") {

                        SettingDropdownRow(

                            title = stringResource(R.string.search_panel_default_engine_title),

                            subtitle = stringResource(R.string.float_ball_pick_default_engine_desc),

                            icon = { label -> SymbolsIcon("StarBorder", contentDescription = label) },

                            items = defaultEngineItems,

                            selectedIndex = defaultEngineIndex,

                            enabled = defaultEngineCandidates.isNotEmpty(),

                            onSelectedIndexChange = { index ->

                                onDefaultSearchEngineChange(

                                    if (index == 0) null else defaultEngineCandidates[index - 1].id,

                                )

                            },

                        )

                    }

                )

                add(

                    settingsCardScopeItem("image-search-engines") {

                        SettingNavigationRow(

                            icon = { label -> SymbolsIcon("ImageSearch", contentDescription = label) },

                            title = stringResource(R.string.image_search_engine_settings_title),

                            subtitle = pluralStringResource(

                                R.plurals.image_search_engine_settings_summary,

                                SearchEngineStore.imageSharePanelEngines(settings.searchEngines).size,

                                SearchEngineStore.imageSharePanelEngines(settings.searchEngines).size,

                                AggregatedImageSearchEnginePreferencesStore.panelConfigs(

                                    settings.aggregatedImageSearchEngines,

                                ).size,

                            ),

                            enabled = true,

                            onClick = onOpenImageSearchEngineSettings,

                        )

                    }

                )

            },

        )

        settingsLazySectionIntro(
            key = "image-open-edit-header",
            title = imageOpenEditSectionTitle,
            hint = imageOpenEditHint,
        )

        groupedCardItems(

            keyPrefix = "fb-pick-image-open-edit",

            items = buildList {

                add(

                    settingsCardScopeItem("image-viewer") {

                        SettingSpinnerRow(

                            title = stringResource(R.string.image_viewer_default_title),

                            subtitle = imageViewerSubtitle,

                            dialogButtonText = stringResource(R.string.cancel),

                            items = imageViewerItems,

                            selectedIndex = selectedImageViewerIndex,

                            enabled = imageViewerOptions is ImageViewerOptionsState.Ready,

                            onSelectedIndexChange = { index ->

                                val option = readyOptions?.getOrNull(index) ?: return@SettingSpinnerRow

                                onDefaultImageViewerPackageChange(option.packageName)

                            }

                        )

                    }

                )

                add(

                    settingsCardScopeItem("image-editor-delay-delete") {

                        SettingSwitchRow(

                            title = stringResource(R.string.image_editor_delay_delete_title),

                            subtitle = stringResource(R.string.image_editor_delay_delete_desc),

                            checked = settings.imageEditorDelayDeleteEnabled,

                            enabled = true,

                            onCheckedChange = onImageEditorDelayDeleteChange,

                        )

                    }

                )

            },

        )

        settingsLazySmallTitle(

            key = "long-image-history-section",

            title = longImageHistorySectionTitle,

        )

        groupedCardItems(

            keyPrefix = "fb-pick-long-image-history",

            items = buildList {

                add(

                    settingsCardScopeItem("history-enabled") {

                        SettingSwitchRow(

                            title = stringResource(R.string.share_image_ocr_history_enabled),

                            subtitle = stringResource(R.string.share_image_ocr_history_enabled_desc),

                            checked = settings.shareImageOcrHistoryEnabled,

                            enabled = true,

                            onCheckedChange = onShareImageOcrHistoryEnabledChange

                        )

                    }

                )

                add(

                    settingsCardScopeItem("history-entry") {

                        ShareImageOcrHistoryEntryRow(

                            historyCount = historyCount,

                            enabled = true,

                            onClick = onOpenShareImageOcrHistory

                        )

                    }

                )

            },

        )

    }

}



private fun ImageViewerDropdownOption.toDropdownItem(): DropdownItem =

    DropdownItem(

        text = label,

        icon = { modifier ->

            iconBitmap?.let { bitmap ->

                Image(

                    bitmap = bitmap,

                    contentDescription = null,

                    modifier = modifier.size(24.dp)

                )

            }

        }

    )



@Composable

internal fun ocrModelSelectionSubtitle(modelId: String): String {

    if (modelId.isBlank()) {

        return stringResource(R.string.ocr_model_status_not_installed)

    }

    return when (modelId) {

        "mlkit-chinese" -> stringResource(R.string.ocr_model_mlkit_chinese)

        "tesseract-chi-sim-eng" -> stringResource(R.string.ocr_model_tesseract_chi_sim_eng)

        "ppocrv5-arabic" -> stringResource(R.string.ocr_model_ppocrv5_arabic)

        "ppocrv6-tiny" -> stringResource(R.string.ocr_model_ppocrv6_tiny)

        "ppocrv6-small" -> stringResource(R.string.ocr_model_ppocrv6_small)

        "ppocrv6-medium" -> stringResource(R.string.ocr_model_ppocrv6_medium)

        else -> modelId

    }

}


