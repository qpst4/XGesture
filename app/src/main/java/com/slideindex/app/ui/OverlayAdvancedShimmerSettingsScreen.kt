package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import kotlin.math.roundToInt

/**
 * 悬浮面板高级光影独立微调三级设置页。
 *
 * 专注于 3 个细分方向的独立调控：
 * 1. 边缘微光高光（勾勒边框的高亮）
 * 2. 角落定向聚光（角落的光斑聚光）
 * 3. 顶部垂直微光（从上至下的漫反射光）
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverlayAdvancedShimmerSettingsScreen(
    onBack: () -> Unit,
    overlayBorderHighlightPercent: Int,
    onOverlayBorderHighlightPercentChange: (Int) -> Unit,
    overlayRadialGlossPercent: Int,
    onOverlayRadialGlossPercentChange: (Int) -> Unit,
    overlayVerticalGlossPercent: Int,
    onOverlayVerticalGlossPercentChange: (Int) -> Unit,
) {
    val pageTitle = stringResource(R.string.overlay_advanced_shimmer_title)
    val pageHint = stringResource(R.string.overlay_advanced_shimmer_desc)

    val borderHighlightTitle = stringResource(R.string.overlay_border_highlight_title)
    val radialGlossTitle = stringResource(R.string.overlay_radial_gloss_title)
    val verticalGlossTitle = stringResource(R.string.overlay_vertical_gloss_title)

    SettingsScreenScaffold(
        title = pageTitle,
        pageHint = pageHint,
        onBack = onBack,
    ) {
        MiuixListSettingsCard(
            keyPrefix = "overlay-advanced-shimmer-card",
            items = buildList {
                add(
                    settingsCardScopeItem("border-highlight") {
                        SettingsSliderRow(
                            title = borderHighlightTitle,
                            value = overlayBorderHighlightPercent.toFloat(),
                            valueRange = 0f..100f,
                            enabled = true,
                            label = "${overlayBorderHighlightPercent}%",
                            formatLabel = { "${it.roundToInt()}%" },
                            onValueChange = { onOverlayBorderHighlightPercentChange(it.roundToInt()) },
                        )
                    }
                )
                add(
                    settingsCardScopeItem("radial-gloss") {
                        SettingsSliderRow(
                            title = radialGlossTitle,
                            value = overlayRadialGlossPercent.toFloat(),
                            valueRange = 0f..100f,
                            enabled = true,
                            label = "${overlayRadialGlossPercent}%",
                            formatLabel = { "${it.roundToInt()}%" },
                            onValueChange = { onOverlayRadialGlossPercentChange(it.roundToInt()) },
                        )
                    }
                )
                add(
                    settingsCardScopeItem("vertical-gloss") {
                        SettingsSliderRow(
                            title = verticalGlossTitle,
                            value = overlayVerticalGlossPercent.toFloat(),
                            valueRange = 0f..100f,
                            enabled = true,
                            label = "${overlayVerticalGlossPercent}%",
                            formatLabel = { "${it.roundToInt()}%" },
                            onValueChange = { onOverlayVerticalGlossPercentChange(it.roundToInt()) },
                        )
                    }
                )
            },
        )
    }
}
