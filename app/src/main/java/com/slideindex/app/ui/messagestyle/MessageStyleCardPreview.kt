@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageThemeCatalog
import com.slideindex.app.message.MessageThemeSpec
import com.slideindex.app.message.messageThemeBackground
import com.slideindex.app.ui.SettingLinkRow
import com.slideindex.app.ui.SettingSwitchRow
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun autoDismissSettingsCardItem(
    autoDismissSeconds: Int,
    enabled: Boolean,
    onAutoDismissSecondsChange: (Int) -> Unit,
): CardItem {
    val autoDismissOffLabel = stringResource(R.string.message_reminder_auto_dismiss_off)
    return settingsCardScopeItem("auto-dismiss") {
        SettingsSliderRow(
            title = stringResource(R.string.message_reminder_auto_dismiss),
            value = autoDismissSeconds.toFloat(),
            valueRange = 0f..30f,
            steps = 29,
            enabled = enabled,
            label = if (autoDismissSeconds <= 0) {
                autoDismissOffLabel
            } else {
                stringResource(
                    R.string.message_reminder_auto_dismiss_seconds,
                    autoDismissSeconds,
                )
            },
            formatLabel = { value ->
                val seconds = value.toInt()
                if (seconds <= 0) autoDismissOffLabel else "$seconds s"
            },
            onValueChange = { onAutoDismissSecondsChange(it.toInt()) },
        )
    }
}

@Composable
fun primaryDisplayCardItems(
    settings: MessageSettings,
    enabled: Boolean,
    maxLines: Int,
    opacity: Float,
    opacityTitleRes: Int,
    onOpacityChange: (Float) -> Unit,
    onMaxLinesChange: (Int) -> Unit,
    autoDismissSeconds: Int,
    onAutoDismissSecondsChange: (Int) -> Unit,
    /** 非 null 时显示"锁屏也显示"开关（侧边气泡用）。 */
    lockScreenEnabled: Boolean? = null,
    onLockScreenChange: ((Boolean) -> Unit)? = null,
    onPickSideCount: (() -> Unit)? = null,
    sideMaxCount: Int = 3,
    opacitySteps: Int = 7,
    opacityRange: ClosedFloatingPointRange<Float> = 0.2f..1f,
    onPreviewChange: (MessageSettings) -> Unit = {},
    onPreviewCommit: () -> Unit = {},
): List<CardItem> = buildList {
        add(
            settingsCardScopeItem("opacity") {
                SettingsSliderRow(
                    title = stringResource(opacityTitleRes),
                    value = opacity,
                    valueRange = opacityRange,
                    steps = opacitySteps,
                    enabled = enabled,
                    label = "${(opacity * 100).toInt()}%",
                    formatLabel = { "${(it * 100).toInt()}%" },
                    triggersLayoutPreview = true,
                    onLayoutPreviewValueChange = { previewOpacity ->
                        onPreviewChange(settings.copy(sideBubbleOpacity = previewOpacity))
                    },
                    onValueChange = { committedOpacity ->
                        onPreviewCommit()
                        onOpacityChange(committedOpacity)
                    },
                )
            },
        )
        add(
            settingsCardScopeItem("max-lines") {
                SettingsSliderRow(
                    title = stringResource(R.string.message_style_max_lines),
                    value = maxLines.toFloat(),
                    valueRange = 1f..3f,
                    steps = 1,
                    enabled = enabled,
                    label = maxLines.toString(),
                    formatLabel = { it.toInt().toString() },
                    onValueChange = { onMaxLinesChange(it.toInt()) },
                )
            },
        )
        if (onPickSideCount != null) {
            add(
                settingsCardScopeItem("side-count") {
                    SettingLinkRow(
                        title = stringResource(R.string.message_style_side_count),
                        subtitle = stringResource(R.string.message_style_side_count_option, sideMaxCount),
                        enabled = enabled,
                        onClick = { if (enabled) onPickSideCount() },
                    )
                },
            )
        }
        if (lockScreenEnabled != null && onLockScreenChange != null) {
            add(
                settingsCardScopeItem("lock-screen") {
                    SettingSwitchRow(
                        title = stringResource(R.string.message_side_bubble_lock_screen_title),
                        subtitle = stringResource(R.string.message_side_bubble_lock_screen_desc),
                        checked = lockScreenEnabled,
                        enabled = enabled,
                        onCheckedChange = onLockScreenChange,
                    )
                },
            )
        }
        add(
            autoDismissSettingsCardItem(
                autoDismissSeconds = autoDismissSeconds,
                enabled = enabled,
                onAutoDismissSecondsChange = onAutoDismissSecondsChange,
            ),
        )
    }

fun LazyListScope.primaryDisplaySection(
    items: List<CardItem>,
    sectionTitle: String,
) {
    settingsLazySmallTitle(key = "message-display-section", title = sectionTitle)
    groupedCardItems("message-primary-display", items)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageThemeGrid(
    themes: List<MessageThemeSpec>,
    selectedThemeId: String,
    enabled: Boolean,
    onThemeSelected: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val normalizedSelectedId = MessageThemeCatalog.normalizeThemeId(selectedThemeId)
        themes.forEach { theme ->
            val selected = theme.id == normalizedSelectedId
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
            Column(
                modifier = Modifier
                    .size(width = 108.dp, height = 88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onThemeSelected(theme.id) }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (theme.cornerRadiusDp > 0f) {
                                Modifier.clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
                            } else {
                                Modifier
                            },
                        )
                        .messageThemeBackground(theme),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Aa",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = Color(theme.titleColorArgb),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = stringResource(theme.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
