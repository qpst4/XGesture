package com.slideindex.app.translate

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.translate.R
import com.slideindex.app.translate.TranslateTargetLanguages
import com.slideindex.app.translate.TranslateTargetResolver

@Composable
fun translateTargetAutonym(catalogCode: String): String =
    when (catalogCode.lowercase()) {
        "zh-cn" -> stringResource(R.string.settings_app_language_label_zh)
        "zh-tw" -> stringResource(R.string.translate_target_label_zh_tw)
        "en" -> stringResource(R.string.settings_app_language_label_en)
        "ja" -> stringResource(R.string.settings_app_language_label_ja)
        "ar" -> stringResource(R.string.settings_app_language_label_ar)
        "ko" -> stringResource(R.string.translate_target_label_ko)
        "fr" -> stringResource(R.string.translate_target_label_fr)
        "de" -> stringResource(R.string.translate_target_label_de)
        "id" -> stringResource(R.string.translate_target_label_id)
        "pt-br" -> stringResource(R.string.translate_target_label_pt_br)
        "pt-pt" -> stringResource(R.string.translate_target_label_pt_pt)
        "es" -> stringResource(R.string.translate_target_label_es)
        "ru" -> stringResource(R.string.translate_target_label_ru)
        "tr" -> stringResource(R.string.translate_target_label_tr)
        else -> catalogCode
    }

@Composable
fun translateTargetFollowAppDropdownLabel(appUiLanguageTag: String): String {
    val followLabel = stringResource(R.string.translate_target_follow_app)
    val resolvedCode = TranslateTargetResolver.resolve(
        TranslateTargetLanguages.FOLLOW_APP,
        appUiLanguageTag,
    )
    val autonym = translateTargetAutonym(resolvedCode)
    return stringResource(R.string.translate_target_follow_app_summary, followLabel, autonym)
}
