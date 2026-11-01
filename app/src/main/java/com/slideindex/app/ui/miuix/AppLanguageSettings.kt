package com.slideindex.app.ui.miuix

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppUiLanguage
import com.slideindex.app.ui.settings.components.settingsCardItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun appLanguageSettingsCardItems(
    appUiLanguageTag: String,
    onAppUiLanguageChange: (AppUiLanguage) -> Unit,
): List<CardItem> {
    val currentLanguage = AppUiLanguage.fromStorageTag(appUiLanguageTag)
    val followSystemLabel = stringResource(R.string.settings_app_language_system)
    val zhLabel = stringResource(R.string.settings_app_language_label_zh)
    val enLabel = stringResource(R.string.settings_app_language_label_en)
    val jaLabel = stringResource(R.string.settings_app_language_label_ja)
    val arLabel = stringResource(R.string.settings_app_language_label_ar)
    val ruLabel = stringResource(R.string.settings_app_language_label_ru)
    val languageOptions = remember(followSystemLabel, zhLabel, enLabel, jaLabel, arLabel, ruLabel) {
        listOf(followSystemLabel, zhLabel, enLabel, jaLabel, arLabel, ruLabel)
    }
    val languageEntries = remember {
        listOf(
            AppUiLanguage.SYSTEM,
            AppUiLanguage.ZH,
            AppUiLanguage.EN,
            AppUiLanguage.JA,
            AppUiLanguage.AR,
            AppUiLanguage.RU,
        )
    }

    return listOf(
        settingsCardItem("app-language") {
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_app_language_title),
                items = languageOptions,
                selectedIndex = languageEntries.indexOf(currentLanguage).coerceAtLeast(0),
                startAction = {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = stringResource(R.string.settings_app_language_title),
                    )
                },
                onSelectedIndexChange = { index ->
                    onAppUiLanguageChange(languageEntries[index])
                },
            )
        },
    )
}
