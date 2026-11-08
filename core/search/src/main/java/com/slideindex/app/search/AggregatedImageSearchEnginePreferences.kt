package com.slideindex.app.search

import com.slideindex.app.settings.AggregatedImageSearchEngineConfig
import com.slideindex.app.settings.AggregatedImageSearchEnginePreferencesStore

fun List<AggregatedImageSearchEngineConfig>.toPanelImageSearchEngines(): List<ImageSearchEngine> =
    AggregatedImageSearchEnginePreferencesStore.panelConfigs(this)
        .mapNotNull { config -> ImageSearchEngine.entries.find { it.name == config.engineId } }

fun List<AggregatedImageSearchEngineConfig>.toPreloadImageSearchEngines(): List<ImageSearchEngine> =
    AggregatedImageSearchEnginePreferencesStore.preloadConfigs(this)
        .mapNotNull { config -> ImageSearchEngine.entries.find { it.name == config.engineId } }
