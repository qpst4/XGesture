@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.external.ExternalInvocationCatalog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun ExternalInvocationHelpScreen(
    onOpenLauncherShortcutMenu: () -> Unit,
    onBack: () -> Unit,
) {
    val copiedMessage = stringResource(R.string.external_invocation_copied)
    val hintText = stringResource(R.string.external_invocation_hint)
    val queryHintText = stringResource(R.string.external_invocation_query_hint)
    val deeplinkSectionTitle = stringResource(R.string.external_invocation_deeplink_section)
    val actionSectionTitle = stringResource(R.string.external_invocation_action_section)
    val actionHintText = stringResource(R.string.external_invocation_action_hint)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.external_invocation_title),
        pageHint = stringResource(R.string.external_invocation_entry_desc),
        onBack = onBack
    ) {
        settingsLazyTipCard(
            key = "external_invocation_hint",
            text = hintText
        )
        settingsLazyTipCard(
            key = "external_invocation_query_hint",
            text = queryHintText
        )

        groupedCardItems(
            keyPrefix = "external_invocation_launcher_shortcut",
            items = listOf(
                settingsCardScopeItem("launcher-shortcut-menu") {
                    SettingNavigationRow(
                        icon = { label ->
                            Icon(
                                Icons.AutoMirrored.Filled.Shortcut,
                                contentDescription = label,
                            )
                        },
                        title = stringResource(R.string.launcher_shortcut_menu_entry_title),
                        subtitle = stringResource(R.string.launcher_shortcut_menu_entry_desc),
                        onClick = onOpenLauncherShortcutMenu,
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "external_invocation_deeplink_section",
            title = deeplinkSectionTitle
        )
        groupedCardItems(
            keyPrefix = "external_invocation_deeplink",
            items = ExternalInvocationCatalog.deeplinks.map { entry ->
                settingsCardScopeItem("deeplink-${entry.path}") {
                    ExternalInvocationDeeplinkRow(
                        entry = entry,
                        copiedMessage = copiedMessage
                    )
                }
            }
        )

        settingsLazySmallTitle(
            key = "external_invocation_action_section",
            title = actionSectionTitle
        )
        settingsLazyTipCard(
            key = "external_invocation_action_hint",
            text = actionHintText
        )
        groupedCardItems(
            keyPrefix = "external_invocation_action",
            items = ExternalInvocationCatalog.actions.map { entry ->
                settingsCardScopeItem("action-${entry.action}") {
                    ExternalInvocationActionRow(
                        entry = entry,
                        copiedMessage = copiedMessage
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsCardScope.ExternalInvocationDeeplinkRow(
    entry: ExternalInvocationCatalog.DeeplinkEntry,
    copiedMessage: String
) {
    val uri = ExternalInvocationCatalog.deeplinkUri(entry.path)
    val title = stringResource(entry.titleRes)
    val description = stringResource(entry.descriptionRes)
    val subtitle = buildString {
        append(description)
        append('\n')
        append(uri)
        if (entry.supportsQuery) {
            append('\n')
            val keywordExample = stringResource(R.string.external_invocation_keyword_example).trim().trim('"', '«', '»')
            append(stringResource(R.string.external_invocation_query_example, "${QUERY_PARAM}=$keywordExample"))
        }
    }
    ExternalInvocationCopyRow(
        title = title,
        subtitle = subtitle,
        copyText = uri,
        copiedMessage = copiedMessage
    )
}

@Composable
private fun SettingsCardScope.ExternalInvocationActionRow(
    entry: ExternalInvocationCatalog.ActionEntry,
    copiedMessage: String
) {
    val context = LocalContext.current
    val title = stringResource(entry.titleRes)
    val description = stringResource(entry.descriptionRes)
    val adbCommand = ExternalInvocationCatalog.adbActionCommand(context, entry)
    val subtitle = buildString {
        append(description)
        append('\n')
        append(entry.action)
        append('\n')
        append(stringResource(R.string.external_invocation_action_component, entry.componentClass))
        if (entry.supportsQuery) {
            append('\n')
            append(stringResource(R.string.external_invocation_action_query_extra, QUERY_PARAM))
        }
    }
    ExternalInvocationCopyRow(
        title = title,
        subtitle = subtitle,
        copyText = adbCommand,
        copiedMessage = copiedMessage
    )
}

@Composable
private fun SettingsCardScope.ExternalInvocationCopyRow(
    title: String,
    subtitle: String,
    copyText: String,
    copiedMessage: String
) {
    val context = LocalContext.current
    MiuixNavigationRow(
        title = title,
        summary = subtitle,
        onClick = {
            copyToClipboard(context, copyText)
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
        rowKey = copyText
    )
}

@Composable
fun SettingsCardScope.ExternalInvocationEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("Link", contentDescription = label) },
        title = stringResource(R.string.external_invocation_entry_title),
        subtitle = stringResource(R.string.external_invocation_entry_desc),
        onClick = onClick
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("external_invocation", text))
}

private const val QUERY_PARAM = ExternalInvocationCatalog.QUERY_PARAM
