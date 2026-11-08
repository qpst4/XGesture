package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.nativeengine.NativeEnginePackVersionState
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.translate.TranslateDownloadPhase
import com.slideindex.app.translate.TranslateDownloadState
import com.slideindex.app.translate.TranslateDownloadStep
import com.slideindex.app.translate.TranslateLanguageCatalog
import com.slideindex.app.ui.miuix.MiuixProgressCard
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.miuixGroupedRowInsets
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import java.util.Locale
import com.slideindex.app.translate.translateTargetAutonym
import com.slideindex.app.translate.translateTargetFollowAppDropdownLabel

@Composable
fun TranslateModelSettingsScreen(
    settings: AppSettings,
    installedLanguageCodes: Set<String>,
    downloadState: TranslateDownloadState?,
    translateEngineInstalled: Boolean,
    translateEngineSizeBytes: Long,
    translateEngineVersionState: NativeEnginePackVersionState? = null,
    onBack: () -> Unit,
    onDownloadLanguage: (String) -> Unit,
    onDeleteLanguage: (String) -> Unit,
    onDeleteTranslateEngine: () -> Unit,
    onOpenEngineManagement: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    val languagesSectionTitle = stringResource(R.string.float_ball_translate_languages_section)

    val downloadHeaderCard = settingsCardItems(
        settings.ocrDownloadWifiOnly,
        translateEngineInstalled,
        downloadState,
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.ocr_download_wifi_only),
            subtitle = stringResource(R.string.ocr_download_wifi_only_desc),
            checked = settings.ocrDownloadWifiOnly,
            enabled = true,
            onCheckedChange = onWifiOnlyChange,
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_translate_mlkit_models),
        subtitle = stringResource(R.string.float_ball_translate_mlkit_models_subtitle),
        onBack = onBack,
    ) {
        LazySettingsItem(key = "translate-download-header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                downloadHeaderCard.RenderRows()

                NativeEnginePackStatusBanner(
                    title = stringResource(R.string.native_engine_pack_translate),
                    installed = translateEngineInstalled,
                    sizeBytes = translateEngineSizeBytes,
                    installedRevision = translateEngineVersionState?.installedRevision,
                    installedDisplayVersion = translateEngineVersionState?.installedDisplayVersion,
                    latestRevision = translateEngineVersionState?.latestRevision ?: 1,
                    latestDisplayVersion = translateEngineVersionState?.latestDisplayVersion,
                    updateAvailable = translateEngineVersionState?.updateAvailable == true,
                    onManage = onOpenEngineManagement,
                    onDelete = if (translateEngineInstalled) onDeleteTranslateEngine else null,
                )

                downloadState?.let { state ->
                    if (state.phase == TranslateDownloadPhase.DOWNLOADING) {
                        TranslateDownloadProgressCard(state = state)
                    }
                }
            }
        }

        settingsLazySmallTitle(key = "translate-languages-section", title = languagesSectionTitle)

        groupedCardItems(
            keyPrefix = "translate-languages",
            items = TranslateLanguageCatalog.options.map { option ->
                val rowDownloadState = downloadState?.takeIf { it.languageCode == option.code }
                com.slideindex.app.ui.miuix.CardItem("lang-${option.code}") {
                    TranslateLanguageRow(
                        displayName = translateTargetAutonym(option.code),
                        installed = option.code in installedLanguageCodes,
                        downloadState = rowDownloadState,
                        onDownload = { onDownloadLanguage(option.code) },
                        onDelete = { onDeleteLanguage(option.code) },
                    )
                }
            },
        )
    }
}

@Composable
private fun TranslateDownloadProgressCard(state: TranslateDownloadState) {
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
                text = translateDownloadProgressLabel(state),
                style = MiuixTheme.textStyles.body1,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TranslateLanguageRow(
    displayName: String,
    installed: Boolean,
    downloadState: TranslateDownloadState?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloading = downloadState?.phase == TranslateDownloadPhase.DOWNLOADING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .miuixGroupedRowInsets(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = displayName, style = MiuixTheme.textStyles.title4)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    downloading -> translateDownloadProgressLabel(downloadState)
                    installed -> stringResource(R.string.ocr_model_status_installed)
                    else -> stringResource(R.string.ocr_model_status_not_installed)
                },
                style = MiuixTheme.textStyles.body2,
                color = if (downloading) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            downloading -> {
                val fraction = downloadState.progress
                if (fraction != null) {
                    Text(
                        text = "${(fraction * 100).roundToInt()}%",
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                }
            }
            installed -> {
                Button(
                    onClick = onDelete,
                ) {
                    Text(stringResource(R.string.ocr_model_delete))
                }
            }
            else -> {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.ocr_model_download))
                }
            }
        }
    }
}

@Composable
private fun translateDownloadProgressLabel(state: TranslateDownloadState): String {
    val languageName = translateTargetAutonym(state.languageCode)
    val stepPrefix = when (state.step) {
        TranslateDownloadStep.ENGINE ->
            stringResource(R.string.translate_download_step_engine, state.stepIndex, state.stepCount)
        TranslateDownloadStep.LANGUAGE ->
            stringResource(R.string.translate_download_step_language, state.stepIndex, state.stepCount)
    }
    return when (state.phase) {
        TranslateDownloadPhase.DOWNLOADING -> {
            val downloaded = formatMegabytes(state.bytesDownloaded)
            val total = state.totalBytes?.let(::formatMegabytes)
            val detail = if (total != null) {
                stringResource(R.string.ocr_download_progress_bytes, languageName, downloaded, total)
            } else {
                stringResource(R.string.ocr_download_progress_indeterminate, languageName, downloaded)
            }
            if (state.stepCount > 1) "$stepPrefix $detail" else detail
        }
        TranslateDownloadPhase.FAILED ->
            stringResource(R.string.ocr_download_failed, state.errorMessage.orEmpty())
        TranslateDownloadPhase.CANCELLED ->
            stringResource(R.string.ocr_download_cancelled)
        TranslateDownloadPhase.READY ->
            stringResource(R.string.ocr_download_ready, languageName)
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
