package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.CardItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem

import com.slideindex.app.ui.miuix.MiuixHintText

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.managedAppListDescription(
    key: String,
    text: @Composable () -> String,
) {
    item(key = key) {
        MiuixHintText(text = text())
    }
}

fun LazyListScope.managedAppListSectionTitle(
    key: String,
    title: @Composable () -> String,
) {
    item(key = key) {
        SmallTitle(
            title(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun LazyListScope.managedAppListEmpty(key: String, text: @Composable () -> String) {
    item(key = key) {
        Text(
            text = text(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.managedAppPackageRows(
    keyPrefix: String,
    entries: List<AppPackageEntry>,
    actionIcon: ImageVector,
    actionDescription: @Composable () -> String,
    missingIcon: ImageVector,
    onAction: (AppPackageEntry) -> Unit,
    subtitle: @Composable (AppPackageEntry) -> String? = { null },
    onRowClick: ((AppPackageEntry) -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    items(
        entries.size,
        key = { "$keyPrefix-${entries[it].packageName}" },
    ) { index ->
        val entry = entries[index]
        AppPackageListRow(
            entry = entry,
            segmentIndex = index,
            segmentCount = entries.size,
            actionIcon = actionIcon,
            actionDescription = actionDescription(),
            missingIcon = missingIcon,
            subtitle = subtitle(entry),
            onAction = { onAction(entry) },
            onRowClick = onRowClick?.let { click -> { click(entry) } },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.managedAppListAddRow(
    key: String = "managed-add-app",
    title: @Composable () -> String,
    subtitle: @Composable () -> String = { "" },
    onClick: () -> Unit,
) {
    groupedCardItems(
        keyPrefix = key,
        items = listOf(
            settingsCardScopeItem("nav") {
                SettingNavigationRow(
                    icon = { label ->
                        Icon(Icons.Default.Add, contentDescription = label)
                    },
                    title = title(),
                    subtitle = subtitle(),
                    onClick = onClick,
                )
            },
        ),
    )
}
