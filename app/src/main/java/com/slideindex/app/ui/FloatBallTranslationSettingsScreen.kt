package com.slideindex.app.ui

import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallTranslateEngine
import com.slideindex.app.translate.TranslateLanguageCatalog
import com.slideindex.app.translate.TranslateTargetLanguages
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.translate.translateTargetAutonym
import com.slideindex.app.translate.translateTargetFollowAppDropdownLabel

@Composable
fun FloatBallTranslationSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onInstantTranslateChange: (Boolean) -> Unit,
    onEngineChange: (FloatBallTranslateEngine) -> Unit,
    onTargetLangChange: (String) -> Unit,
    onOpenMlKitModels: () -> Unit,
    onOpenCloudTranslateSettings: () -> Unit,
    onOpenTranslateAppPicker: () -> Unit,
) {
    val engineEntries = FloatBallTranslateEngine.entries
    val engine = settings.floatBallTranslateEngine
    val localAppEngine = engine == FloatBallTranslateEngine.LOCAL_APP
    val langOptions = TranslateLanguageCatalog.options
    val storedTarget = settings.floatBallTranslateTargetLang
    val langIndex = if (TranslateTargetLanguages.isFollowApp(storedTarget)) {
        0
    } else {
        val catalogIndex = langOptions.indexOfFirst {
            it.code.equals(storedTarget, ignoreCase = true)
        }
        if (catalogIndex >= 0) 1 + catalogIndex else 0
    }
    val targetLangDropdownItems = buildList {
        add(translateTargetFollowAppDropdownLabel(settings.appUiLanguageTag))
        addAll(langOptions.map { translateTargetAutonym(it.code) })
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_translation_settings_title),
        pageHint = stringResource(R.string.pick_panel_translation_page_hint),
        onBack = onBack
    ) {
        groupedCardItems(
            keyPrefix = "fb-translation",
            items = buildList {
                add(
                    settingsCardScopeItem("engine") {
                        SettingDropdownRow(
                            icon = { label -> SymbolsIcon("Translate", contentDescription = label) },
                            title = stringResource(R.string.float_ball_translate_engine),
                            items = engineEntries.map { translateEngineLabel(it) },
                            selectedIndex = engineEntries.indexOf(engine).coerceAtLeast(0),
                            onSelectedIndexChange = { onEngineChange(engineEntries[it]) }
                        )
                    }
                )
                // 「本地 App」的译文语言由那个 App 自己决定，这一行对它没有意义。
                if (!localAppEngine) {
                    add(
                        settingsCardScopeItem("target-lang") {
                            SettingDropdownRow(
                                icon = { label -> SymbolsIcon("Translate", contentDescription = label) },
                                title = stringResource(R.string.float_ball_translate_target_lang),
                                items = targetLangDropdownItems,
                                selectedIndex = langIndex,
                                onSelectedIndexChange = { index ->
                                    if (index == 0) {
                                        onTargetLangChange(TranslateTargetLanguages.FOLLOW_APP)
                                    } else {
                                        onTargetLangChange(langOptions[index - 1].code)
                                    }
                                }
                            )
                        }
                    )
                }
                add(
                    settingsCardScopeItem("instant") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_instant_translate),
                            subtitle = if (localAppEngine) {
                                stringResource(R.string.float_ball_instant_translate_local_app_hint)
                            } else {
                                stringResource(R.string.float_ball_instant_translate_desc)
                            },
                            checked = settings.floatBallInstantTranslate && !localAppEngine,
                            enabled = !localAppEngine,
                            onCheckedChange = onInstantTranslateChange
                        )
                    }
                )
            }
        )
        if (localAppEngine) {
            groupedCardItems(
                keyPrefix = "fb-translation-local-app",
                items = buildList {
                    add(
                        settingsCardScopeItem("translate-app") {
                            SettingNavigationRow(
                                icon = { label -> SymbolsIcon("Apps", contentDescription = label) },
                                title = stringResource(R.string.float_ball_translate_app),
                                subtitle = translateAppSubtitle(settings.floatBallTranslateAppPackage),
                                enabled = true,
                                onClick = onOpenTranslateAppPicker,
                            )
                        }
                    )
                }
            )
        }
        if (engine == FloatBallTranslateEngine.ML_KIT) {
            groupedCardItems(
                keyPrefix = "fb-translation-mlkit",
                items = buildList {
                    add(
                        settingsCardScopeItem("mlkit-models") {
                            SettingNavigationRow(
                                icon = { label -> SymbolsIcon("Download", contentDescription = label) },
                                title = stringResource(R.string.float_ball_translate_mlkit_models),
                                subtitle = stringResource(R.string.float_ball_translate_mlkit_models_desc),
                                enabled = true,
                                onClick = onOpenMlKitModels
                            )
                        }
                    )
                }
            )
        }
        if (engine == FloatBallTranslateEngine.CLOUD_LLM) {
            groupedCardItems(
                keyPrefix = "fb-translation-cloud",
                items = buildList {
                    add(
                        settingsCardScopeItem("cloud-translate-config") {
                            SettingNavigationRow(
                                icon = { label -> SymbolsIcon("Cloud", contentDescription = label) },
                                title = stringResource(R.string.cloud_translate_settings_title),
                                subtitle = stringResource(R.string.float_ball_translate_cloud_config_desc),
                                enabled = true,
                                onClick = onOpenCloudTranslateSettings,
                            )
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun translateEngineLabel(engine: FloatBallTranslateEngine): String = when (engine) {
    FloatBallTranslateEngine.GOOGLE -> stringResource(R.string.float_ball_translate_engine_google)
    FloatBallTranslateEngine.ML_KIT -> stringResource(R.string.float_ball_translate_engine_mlkit)
    FloatBallTranslateEngine.CLOUD_LLM -> stringResource(R.string.float_ball_translate_engine_cloud)
    FloatBallTranslateEngine.LOCAL_APP -> stringResource(R.string.float_ball_translate_engine_local_app)
}

/** 显示所选的翻译 App 名；没选或已卸载时给出可读文案。 */
@Composable
internal fun translateAppSubtitle(packageName: String): String {
    val context = LocalContext.current
    if (packageName.isBlank()) {
        return stringResource(R.string.float_ball_translate_app_not_selected)
    }
    return remember(packageName) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }
}
