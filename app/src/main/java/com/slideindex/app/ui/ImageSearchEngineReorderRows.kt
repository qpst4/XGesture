package com.slideindex.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.search.ImageSearchEngine
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ImageShareEngineReorderRow(
    engine: SearchEngineConfig,
    segmentIndex: Int,
    segmentCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicComponent(
        modifier = modifier.miuixGroupedCardItem(segmentIndex, segmentCount),
        title = engine.name,
        summary = if (engine.engineType == SearchEngineType.SEARCH_GROUP) {
            stringResource(R.string.search_group_entry_title)
        } else {
            stringResource(R.string.search_engine_type_share_image)
        },
        onClick = onClick,
        startAction = {
            SearchEngineIcon(engine = engine, modifier = Modifier.size(24.dp))
        },
        endActions = {
            IconButton(onClick = onDelete) {
                MiuixIcon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.search_engine_delete_confirm),
                    tint = MiuixTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
fun AggregatedImageSearchEngineReorderRow(
    engine: ImageSearchEngine,
    subtitle: String,
    segmentIndex: Int,
    segmentCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArrowPreference(
        modifier = modifier.miuixGroupedCardItem(segmentIndex, segmentCount),
        title = engine.displayName,
        summary = subtitle,
        onClick = onClick,
        startAction = {
            AggregatedImageSearchEngineIcon(
                engine = engine,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}
