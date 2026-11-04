package com.slideindex.app.settings.macro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.ThinActionIcons
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope

/** 扩展 Hub 里的「手势宏」入口。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsCardScope.MacroEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(ThinActionIcons.PlayCircle, contentDescription = label) },
        title = stringResource(R.string.macro_entry_title),
        subtitle = stringResource(R.string.macro_entry_desc),
        onClick = onClick,
    )
}
