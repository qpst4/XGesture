package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo


@Composable
fun AppPackageListRow(
    entry: AppPackageEntry,
    segmentIndex: Int,
    segmentCount: Int,
    actionIcon: ImageVector,
    actionDescription: String?,
    missingIcon: ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    showAction: Boolean = true,
    title: String? = null,
    subtitle: String? = null,
    onRowClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val displayTitle = when (entry) {
        is AppPackageEntry.Installed -> title ?: entry.app.label
        is AppPackageEntry.Missing -> title ?: entry.packageName
    }
    val displaySubtitle = when (entry) {
        is AppPackageEntry.Installed -> subtitle ?: entry.app.packageName
        is AppPackageEntry.Missing -> subtitle
            ?: if (title == null) stringResource(R.string.app_package_uninstalled) else null
    }
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = displayTitle,
        subtitle = displaySubtitle,
        selected = false,
        onClick = onRowClick ?: if (showAction) onAction else null,
        modifier = modifier,
        leadingContent = { Md3PickerAppEntryLeading(entry = entry, missingIcon = missingIcon) },
        trailingMode = if (showAction) PickerTrailingMode.Icon else PickerTrailingMode.None,
        trailingIcon = if (showAction) actionIcon else null,
        trailingIconDescription = actionDescription,
        onTrailingClick = if (showAction) onAction else null,
        enabled = enabled
    )
}
