@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.ui.messagestyle.cNoticePlacementSection
import com.slideindex.app.ui.messagestyle.cNoticeSettingsCardItems
import com.slideindex.app.ui.messagestyle.cNoticeSettingsSection
import com.slideindex.app.ui.messagestyle.danmakuSettingsCardItems
import com.slideindex.app.ui.messagestyle.danmakuSettingsSection
import com.slideindex.app.ui.messagestyle.edgeCardPlacementSection
import com.slideindex.app.ui.messagestyle.edgeCardSettingsCardItems
import com.slideindex.app.ui.messagestyle.edgeCardSettingsSection
import com.slideindex.app.ui.messagestyle.floatIconPlacementSection
import com.slideindex.app.ui.messagestyle.floatIconSettingsCardItems
import com.slideindex.app.ui.messagestyle.floatIconSettingsSection
import com.slideindex.app.ui.messagestyle.messageStyleLabel
import com.slideindex.app.ui.messagestyle.primaryDisplayCardItems
import com.slideindex.app.ui.messagestyle.sideBubblePlacementSection
import com.slideindex.app.ui.messagestyle.sideStyleSettingsSection

@Composable
fun MessageStyleDetailSettingsScreen(
    style: MessageStyle,
    settings: MessageSettings,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onOpenSideCountPick: () -> Unit = {},
    onSideThemeIdChange: (String) -> Unit,
    onDanmakuThemeIdChange: (String) -> Unit,
    onFloatIconOpacityChange: (Float) -> Unit,
    onSideBubbleOpacityChange: (Float) -> Unit,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuMaxLinesChange: (Int) -> Unit,
    onSideMaxCountChange: (Int) -> Unit,
    onSideMaxLinesChange: (Int) -> Unit,
    onFloatIconSizeDpChange: (Float) -> Unit,
    onFloatIconAutoDismissSecondsChange: (Int) -> Unit,
    onFloatIconKeepAfterUnlockChange: (Boolean) -> Unit,
    onSideBubbleAutoDismissSecondsChange: (Int) -> Unit,
    onSideBubbleShowOnLockScreenChange: (Boolean) -> Unit = {},
    onSideHorizontalEdgeChange: (SideBubbleHorizontalEdge) -> Unit,
    onSideBubbleYFractionPreviewChange: (Float) -> Unit = {},
    onSideBubbleYFractionPreviewCommit: () -> Unit = {},
    onSideBubbleYFractionChange: (Float) -> Unit = {},
    onFloatIconCornerChange: (MessageOverlayCorner) -> Unit = {},
    onFloatIconYFractionPreviewChange: (Float) -> Unit = {},
    onFloatIconYFractionPreviewCommit: () -> Unit = {},
    onFloatIconYFractionChange: (Float) -> Unit = {},
    onSideFontSizeLevelChange: (Int) -> Unit,
    onDanmakuSpeedLevelChange: (Int) -> Unit,
    onCNoticeOpacityChange: (Float) -> Unit = {},
    onCNoticeDimmedOpacityChange: (Float) -> Unit = {},
    onCNoticeGhostOpacityChange: (Float) -> Unit = {},
    onCNoticeIconSizeDpChange: (Float) -> Unit = {},
    onCNoticeMaxCountChange: (Int) -> Unit = {},
    onCNoticeAutoDismissSecondsChange: (Int) -> Unit = {},
    onCNoticeDefaultCollapsedChange: (Boolean) -> Unit = {},
    onCNoticePeekBannerEnabledChange: (Boolean) -> Unit = {},
    onCNoticeLandscapeEnabledChange: (Boolean) -> Unit = {},
    onCNoticeHorizontalEdgeChange: (SideBubbleHorizontalEdge) -> Unit = {},
    onCNoticeYFractionPreviewChange: (Float) -> Unit = {},
    onCNoticeYFractionPreviewCommit: () -> Unit = {},
    onCNoticeYFractionChange: (Float) -> Unit = {},
    onCNoticeEdgeMarginPreviewChange: (Float) -> Unit = {},
    onCNoticeEdgeMarginPreviewCommit: () -> Unit = {},
    onCNoticeEdgeMarginChange: (Float) -> Unit = {},
    onEdgeCardOpacityChange: (Float) -> Unit = {},
    onEdgeCardAutoCollapseMsChange: (Int) -> Unit = {},
    onEdgeCardMaxCountChange: (Int) -> Unit = {},
    onEdgeCardMaxLinesChange: (Int) -> Unit = {},
    onEdgeCardKeepAcrossLockChange: (Boolean) -> Unit = {},
    onEdgeCardCollapseWhenScrollDoneChange: (Boolean) -> Unit = {},
    onEdgeCardHorizontalEdgeChange: (SideBubbleHorizontalEdge) -> Unit = {},
    onEdgeCardYFractionPreviewChange: (Float) -> Unit = {},
    onEdgeCardYFractionPreviewCommit: () -> Unit = {},
    onEdgeCardYFractionChange: (Float) -> Unit = {},
    onMessagePreviewChange: (MessageSettings) -> Unit = {},
    onMessagePreviewCommit: () -> Unit = {},
) {
    val controlsEnabled = settings.enabled
    val subtitle = when (style) {
        MessageStyle.FloatIcon -> stringResource(R.string.message_style_float_icon_desc)
        MessageStyle.SideBubble -> stringResource(R.string.message_style_side_bubble_desc)
        MessageStyle.Danmaku -> stringResource(R.string.message_style_danmaku_desc)
        MessageStyle.CNotice -> stringResource(R.string.message_style_c_notice_desc)
        MessageStyle.EdgeCard -> stringResource(R.string.message_style_edge_card_desc)
    }
    val cNoticeSectionTitle = stringResource(R.string.message_style_section_c_notice_settings)
    val cNoticePlacementTitle = stringResource(R.string.message_style_c_notice_position)
    val cNoticePlacementHint = stringResource(R.string.message_style_c_notice_position_hint)
    val edgeCardSectionTitle = stringResource(R.string.message_style_section_edge_card_settings)
    val edgeCardPlacementTitle = stringResource(R.string.message_style_edge_card_position)
    val edgeCardPlacementHint = stringResource(R.string.message_style_edge_card_position_hint)
    val floatIconSectionTitle = stringResource(R.string.message_style_section_float_settings)
    val floatIconPlacementTitle = stringResource(R.string.message_style_float_position)
    val sideThemeSectionTitle = stringResource(R.string.message_style_section_side_theme)
    val displaySectionTitle = stringResource(R.string.message_style_section_display)
    val fontSizeSectionTitle = stringResource(R.string.message_style_side_font_size)
    val fontSizeHint = stringResource(R.string.message_style_side_font_size_desc)
    val placementSectionTitle = stringResource(R.string.message_style_side_position)
    val placementHint = stringResource(R.string.message_style_side_position_hint)
    val danmakuThemeSectionTitle = stringResource(R.string.message_reminder_danmaku_theme)
    val danmakuOverlayHint = stringResource(R.string.message_style_danmaku_overlay_hint)

    val floatIconItems = floatIconSettingsCardItems(
        settings = settings,
        enabled = controlsEnabled,
        onOpacityChange = onFloatIconOpacityChange,
        onFloatIconSizeDpChange = onFloatIconSizeDpChange,
        onAutoDismissSecondsChange = onFloatIconAutoDismissSecondsChange,
        onKeepAfterUnlockChange = onFloatIconKeepAfterUnlockChange,
        onPreviewChange = onMessagePreviewChange,
        onPreviewCommit = onMessagePreviewCommit,
    )
    val sideDisplayItems = primaryDisplayCardItems(
        settings = settings,
        enabled = controlsEnabled,
        maxLines = settings.sideMaxLines,
        opacity = settings.sideBubbleOpacity,
        opacityTitleRes = R.string.message_reminder_side_opacity,
        onOpacityChange = onSideBubbleOpacityChange,
        onMaxLinesChange = onSideMaxLinesChange,
        autoDismissSeconds = settings.sideBubbleAutoDismissSeconds,
        onAutoDismissSecondsChange = onSideBubbleAutoDismissSecondsChange,
        lockScreenEnabled = settings.sideBubbleShowOnLockScreen,
        onLockScreenChange = onSideBubbleShowOnLockScreenChange,
        onPickSideCount = onOpenSideCountPick,
        sideMaxCount = settings.sideMaxCount,
        opacitySteps = 0,
        opacityRange = 0.1f..1f,
        onPreviewChange = onMessagePreviewChange,
        onPreviewCommit = onMessagePreviewCommit,
    )
    val cNoticeItems = cNoticeSettingsCardItems(
        settings = settings,
        enabled = controlsEnabled,
        onOpacityChange = onCNoticeOpacityChange,
        onDimmedOpacityChange = onCNoticeDimmedOpacityChange,
        onGhostOpacityChange = onCNoticeGhostOpacityChange,
        onIconSizeDpChange = onCNoticeIconSizeDpChange,
        onMaxCountChange = onCNoticeMaxCountChange,
        onDefaultCollapsedChange = onCNoticeDefaultCollapsedChange,
        onPeekBannerEnabledChange = onCNoticePeekBannerEnabledChange,
        onLandscapeEnabledChange = onCNoticeLandscapeEnabledChange,
        onPreviewChange = onMessagePreviewChange,
        onPreviewCommit = onMessagePreviewCommit,
    )
    val danmakuItems = danmakuSettingsCardItems(
        settings = settings,
        controlsEnabled = controlsEnabled,
        onDanmakuOpacityChange = onDanmakuOpacityChange,
        onDanmakuMaxLinesChange = onDanmakuMaxLinesChange,
        onDanmakuSpeedLevelChange = onDanmakuSpeedLevelChange,
    )
    val edgeCardItems = edgeCardSettingsCardItems(
        settings = settings,
        enabled = controlsEnabled,
        onOpacityChange = onEdgeCardOpacityChange,
        onAutoCollapseSecondsChange = onEdgeCardAutoCollapseMsChange,
        onMaxCountChange = onEdgeCardMaxCountChange,
        onMaxLinesChange = onEdgeCardMaxLinesChange,
        onKeepAcrossLockChange = onEdgeCardKeepAcrossLockChange,
        onCollapseWhenScrollDoneChange = onEdgeCardCollapseWhenScrollDoneChange,
        onPreviewChange = onMessagePreviewChange,
        onPreviewCommit = onMessagePreviewCommit,
    )

    SettingsScreenScaffold(
        title = messageStyleLabel(style),
        subtitle = subtitle,
        onBack = onBack,
    ) {
        when (style) {
            MessageStyle.FloatIcon -> {
                floatIconSettingsSection(
                    items = floatIconItems,
                    sectionTitle = floatIconSectionTitle,
                )
                floatIconPlacementSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    sectionTitle = floatIconPlacementTitle,
                    onCornerChange = onFloatIconCornerChange,
                    onYFractionPreviewChange = onFloatIconYFractionPreviewChange,
                    onYFractionPreviewCommit = onFloatIconYFractionPreviewCommit,
                    onYFractionChange = onFloatIconYFractionChange,
                )
            }
            MessageStyle.SideBubble -> {
                sideStyleSettingsSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    displayItems = sideDisplayItems,
                    themeSectionTitle = sideThemeSectionTitle,
                    displaySectionTitle = displaySectionTitle,
                    fontSizeSectionTitle = fontSizeSectionTitle,
                    fontSizeHint = fontSizeHint,
                    onThemeIdChange = onSideThemeIdChange,
                    onFontSizeLevelChange = onSideFontSizeLevelChange,
                )
                sideBubblePlacementSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    sectionTitle = placementSectionTitle,
                    positionHint = placementHint,
                    onHorizontalEdgeChange = onSideHorizontalEdgeChange,
                    onYFractionPreviewChange = onSideBubbleYFractionPreviewChange,
                    onYFractionPreviewCommit = onSideBubbleYFractionPreviewCommit,
                    onYFractionChange = onSideBubbleYFractionChange,
                )
            }
            MessageStyle.Danmaku -> {
                danmakuSettingsSection(
                    settings = settings,
                    controlsEnabled = controlsEnabled,
                    items = danmakuItems,
                    bottomContentPadding = bottomContentPadding,
                    themeSectionTitle = danmakuThemeSectionTitle,
                    overlayHint = danmakuOverlayHint,
                    onDanmakuThemeIdChange = onDanmakuThemeIdChange,
                )
            }
            MessageStyle.CNotice -> {
                cNoticeSettingsSection(
                    items = cNoticeItems,
                    sectionTitle = cNoticeSectionTitle,
                )
                cNoticePlacementSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    sectionTitle = cNoticePlacementTitle,
                    positionHint = cNoticePlacementHint,
                    onHorizontalEdgeChange = onCNoticeHorizontalEdgeChange,
                    onYFractionPreviewChange = onCNoticeYFractionPreviewChange,
                    onYFractionPreviewCommit = onCNoticeYFractionPreviewCommit,
                    onYFractionChange = onCNoticeYFractionChange,
                    onEdgeMarginPreviewChange = onCNoticeEdgeMarginPreviewChange,
                    onEdgeMarginPreviewCommit = onCNoticeEdgeMarginPreviewCommit,
                    onEdgeMarginChange = onCNoticeEdgeMarginChange,
                )
            }
            MessageStyle.EdgeCard -> {
                edgeCardSettingsSection(
                    items = edgeCardItems,
                    sectionTitle = edgeCardSectionTitle,
                )
                edgeCardPlacementSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    sectionTitle = edgeCardPlacementTitle,
                    positionHint = edgeCardPlacementHint,
                    onHorizontalEdgeChange = onEdgeCardHorizontalEdgeChange,
                    onYFractionPreviewChange = onEdgeCardYFractionPreviewChange,
                    onYFractionPreviewCommit = onEdgeCardYFractionPreviewCommit,
                    onYFractionChange = onEdgeCardYFractionChange,
                )
            }
        }
    }
}
