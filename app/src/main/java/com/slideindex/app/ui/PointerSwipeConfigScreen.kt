package com.slideindex.app.ui

import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.gesture.PointerSwipeDirection
import com.slideindex.app.gesture.PointerSwipeDistance
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsGroupedRowBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PointerSwipeConfigScreen(
    initialConfig: PointerSwipeConfig,
    onBack: () -> Unit,
    onConfirm: (PointerSwipeConfig) -> Unit,
) {
    var direction by remember { mutableStateOf(initialConfig.direction) }
    var distance by remember { mutableStateOf(initialConfig.distance) }
    var durationMs by remember { mutableIntStateOf(initialConfig.durationMs) }
    var pointerCount by remember { mutableIntStateOf(initialConfig.pointerCount) }

    val directionSectionTitle = stringResource(R.string.pointer_swipe_direction_title)

    val directionCard = settingsCardItems(direction) {
        SettingsCardRow(key = "direction") { position ->
            Column(
                modifier = Modifier
                    .settingsGroupedRowBackground(position.index, position.count)
                    .padding(16.dp),
            ) {
                SegmentedControl(
                    items = PointerSwipeDirection.entries,
                    selected = direction,
                    labelRes = { option ->
                        when (option) {
                            PointerSwipeDirection.LEFT -> R.string.pointer_swipe_direction_left
                            PointerSwipeDirection.UP -> R.string.pointer_swipe_direction_up
                            PointerSwipeDirection.RIGHT -> R.string.pointer_swipe_direction_right
                            PointerSwipeDirection.DOWN -> R.string.pointer_swipe_direction_down
                        }
                    },
                    onSelect = { direction = it },
                    containerColor = MiuixTheme.colorScheme.surface,
                    indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
                )
            }
        }
    }

    val optionsCard = settingsCardItems(distance, durationMs, pointerCount) {
        SettingsSliderRow(
            title = stringResource(R.string.pointer_swipe_distance_title),
            value = distance.id.toFloat(),
            valueRange = 0f..2f,
            steps = 1,
            enabled = true,
            label = pointerSwipeDistanceLabel(distance),
            onValueChange = { distance = PointerSwipeDistance.fromId(it.roundToInt()) },
        )
        SettingsSliderRow(
            title = stringResource(R.string.pointer_swipe_duration_title),
            value = durationMs.toFloat(),
            valueRange = 20f..500f,
            steps = 23,
            enabled = true,
            label = stringResource(R.string.pointer_swipe_duration_value, durationMs),
            onValueChange = { durationMs = it.roundToInt() },
        )
        SettingsSliderRow(
            title = stringResource(R.string.pointer_swipe_pointer_count_title),
            value = pointerCount.toFloat(),
            valueRange = 1f..5f,
            steps = 3,
            enabled = true,
            label = pointerCount.toString(),
            onValueChange = { pointerCount = it.roundToInt().coerceIn(1, 5) },
        )
        Text(
            text = stringResource(R.string.pointer_swipe_config_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    SettingsFormScreen(
        title = stringResource(R.string.pointer_swipe_config_title),
        pageHint = stringResource(R.string.pointer_swipe_config_desc),
        onBack = onBack,
        onConfirm = {
            onConfirm(
                PointerSwipeConfig(
                    direction = direction,
                    distance = distance,
                    durationMs = durationMs,
                    pointerCount = pointerCount,
                ),
            )
        },
    ) {
        SmallTitle(
            text = directionSectionTitle,
            modifier = Modifier.fillMaxWidth(),
        )
        directionCard.RenderRows()
        optionsCard.RenderRows()
    }
}

@Composable
private fun pointerSwipeDistanceLabel(distance: PointerSwipeDistance): String = when (distance) {
    PointerSwipeDistance.SHORT -> stringResource(R.string.pointer_swipe_distance_short)
    PointerSwipeDistance.MEDIUM -> stringResource(R.string.pointer_swipe_distance_medium)
    PointerSwipeDistance.LONG -> stringResource(R.string.pointer_swipe_distance_long)
}
