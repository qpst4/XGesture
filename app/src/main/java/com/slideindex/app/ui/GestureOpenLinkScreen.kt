package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slideindex.app.ui.quicklauncher.QuickLauncherEmbedParentConfirm
import com.slideindex.app.ui.quicklauncher.ReportQuickLauncherEmbedParentConfirm
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardItems
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.MiuixIcons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureOpenLinkScreen(
    initialUrl: String,
    initialLabel: String = "",
    onBack: () -> Unit,
    onConfirm: (url: String, label: String) -> Unit,
    embedInParentChrome: Boolean = false,
    overlayMode: Boolean = false,
    enableBackHandler: Boolean = true,
    onRegisterEmbedParentConfirm: (QuickLauncherEmbedParentConfirm?) -> Unit = {},
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }
    val canSave = url.trim().isNotBlank()

    val formCard = settingsCardItems {
        SettingsCardRow(key = "open_link_url_row") {
            MiuixLabeledTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.gesture_open_link_url_field),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        SettingsCardRow(key = "open_link_label_row") {
            MiuixLabeledTextField(
                value = label,
                onValueChange = { label = it },
                label = stringResource(R.string.gesture_open_link_label_field),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    if (embedInParentChrome) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            formCard.RenderRows()
            MiuixHintText(
                text = stringResource(R.string.gesture_open_link_config_hint),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        ReportQuickLauncherEmbedParentConfirm(
            enabled = canSave,
            onConfirm = { onConfirm(url.trim(), label.trim()) },
            onReport = onRegisterEmbedParentConfirm,
        )
        return
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_action_open_link),
        pageHint = stringResource(R.string.gesture_open_link_config_hint),
        onBack = onBack,
        enableBackHandler = enableBackHandler,
        overlayMode = overlayMode,
        actions = {
            MiuixTopBarIconButton(
                onClick = { onConfirm(url.trim(), label.trim()) },
                enabled = canSave,
            ) {
                Icon(
                    imageVector = MiuixIcons.Medium.Ok,
                    contentDescription = stringResource(R.string.confirm),
                )
            }
        },
    ) {
        LazySettingsItem(key = "open-link-form") {
            formCard.RenderRows()
        }
        LazySettingsItem(key = "open-link-hint") {
            MiuixHintText(
                text = stringResource(R.string.gesture_open_link_config_hint),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
