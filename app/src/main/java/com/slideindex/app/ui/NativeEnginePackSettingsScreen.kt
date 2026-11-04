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
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState
import com.slideindex.app.ui.viewmodel.NativeEnginePackRowState
import com.slideindex.app.nativeengine.NativeEnginePackIds
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.MiuixProgressCard
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.miuixGroupedRowInsets
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NativeEnginePackSettingsScreen(
    settings: AppSettings,
    packRows: List<NativeEnginePackRowState>,
    downloadState: NativeEnginePackDownloadState?,
    onBack: () -> Unit,
    onDownloadPack: (String) -> Unit,
    onDeletePack: (String) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    val packsSectionTitle = stringResource(R.string.native_engine_packs_section)
    val packsHint = stringResource(R.string.native_engine_packs_hint)

    val downloadHeaderCard = settingsCardItems(settings.ocrDownloadWifiOnly, downloadState) {
        SettingSwitchRow(
            title = stringResource(R.string.ocr_download_wifi_only),
            subtitle = stringResource(R.string.ocr_download_wifi_only_desc),
            checked = settings.ocrDownloadWifiOnly,
            enabled = true,
            onCheckedChange = onWifiOnlyChange,
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.native_engine_packs_title),
        subtitle = stringResource(R.string.native_engine_packs_subtitle),
        onBack = onBack,
    ) {
        LazySettingsItem(key = "native-engine-download-header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                downloadHeaderCard.RenderRows()

                downloadState?.let { state ->
                    if (state.phase != NativeEnginePackDownloadPhase.READY) {
                        NativeEnginePackDownloadProgressCard(state = state)
                    }
                }
            }
        }

        settingsLazySmallTitle(key = "native-engine-packs-section", title = packsSectionTitle)

        settingsLazyTipCard(key = "native-engine-packs-tip", text = packsHint)

        groupedCardItems(
            keyPrefix = "native-engine-packs",
            items = packRows.map { row ->
                com.slideindex.app.ui.miuix.CardItem("pack-${row.entry.id}") {
                    NativeEnginePackRow(
                        row = row,
                        downloading = downloadState?.packId == row.entry.id &&
                            downloadState.phase != NativeEnginePackDownloadPhase.READY &&
                            downloadState.phase != NativeEnginePackDownloadPhase.FAILED &&
                            downloadState.phase != NativeEnginePackDownloadPhase.CANCELLED,
                        onDownload = { onDownloadPack(row.entry.id) },
                        onDelete = { onDeletePack(row.entry.id) },
                    )
                }
            },
        )
    }
}

@Composable
private fun NativeEnginePackRow(
    row: NativeEnginePackRowState,
    downloading: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val pack = row.entry
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .miuixGroupedRowInsets(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = packTitle(pack.id),
                style = MiuixTheme.textStyles.title4,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.native_engine_pack_size,
                    formatMegabytes(pack.sizeBytes),
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Text(
                text = nativeEnginePackVersionStatusText(
                    installed = row.installed,
                    installedRevision = row.installedRevision,
                    installedDisplayVersion = row.installedDisplayVersion,
                    latestRevision = pack.packRevision,
                    latestDisplayVersion = pack.displayVersion,
                    updateAvailable = row.updateAvailable,
                ),
                style = MiuixTheme.textStyles.body2,
                color = when {
                    row.updateAvailable -> MiuixTheme.colorScheme.primary
                    row.installed -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurfaceSecondary
                },
            )
        }
        if (!row.installed) {
            Button(
                onClick = onDownload,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.native_engine_pack_download))
            }
        } else {
            Button(
                onClick = onDelete,
                enabled = !downloading,
            ) {
                Text(stringResource(R.string.native_engine_pack_delete))
            }
        }
    }
}

@Composable
private fun NativeEnginePackDownloadProgressCard(state: NativeEnginePackDownloadState) {
    MiuixProgressCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = packTitle(state.packId),
                style = MiuixTheme.textStyles.title4,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val progress = state.progress
            if (progress != null) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "${(progress * 100f).roundToInt()}%",
                    style = MiuixTheme.textStyles.subtitle,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun packTitle(packId: String): String = when (packId) {
    NativeEnginePackIds.OCR -> stringResource(R.string.native_engine_pack_ocr)
    NativeEnginePackIds.TRANSLATE -> stringResource(R.string.native_engine_pack_translate)
    else -> packId
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
