package com.slideindex.app.settings

import com.slideindex.app.floatball.FloatBallGestureCodec
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.translate.TranslateTargetLanguages

/** 浮层、悬浮球、主题与 App 内外观相关设置子集（窄 Flow 订阅用）。 */
/** 悬浮窗里动作图标的底板配色：深色 / 浅色 / 跟随系统深色模式。 */
enum class IconPlateMode(val id: Int) {
    DARK(0),
    LIGHT(1),
    SYSTEM_DARK(2),
    ;

    companion object {
        fun fromId(id: Int): IconPlateMode = entries.firstOrNull { it.id == id } ?: SYSTEM_DARK
    }
}

data class OverlaySettings(
    val iconPlateModeId: Int = IconPlateMode.SYSTEM_DARK.id,
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val dynamicColorEnabled: Boolean = true,
    val themePaletteStyleId: Int = ThemePaletteStyle.TONAL_SPOT.id,
    val themeModeId: Int = AppThemeMode.SYSTEM.id,
    val customColorEnabled: Boolean = false,
    val darkBackgroundStyleId: Int = DarkBackgroundStyle.QUIET_BLUE.id,
    val themeColorSpecId: Int = AppColorSpec.SPEC_2025.id,
    val uiDensityScale: Float = UiDensityScaleLimits.DEFAULT_SCALE,
    val bottomNavStyleId: Int = BottomNavStyle.FLOATING_NAV.id,
    val bottomNavRailExpanded: Boolean = false,
    val bottomNavModeId: Int = BottomNavMode.ICON_AND_TEXT.id,
    val bottomNavGlassEnabled: Boolean = true,
    val topAppBarBlurStyleId: Int = TopAppBarBlurStyle.PROGRESSIVE.id,
    val bottomNavClassicBlurRadiusDp: Float = BottomNavBlurDefaults.DEFAULT_RADIUS_DP,
    val bottomNavLiquidGlassBlurRadiusDp: Float = BottomNavBlurDefaults.LIQUID_GLASS_DEFAULT_RADIUS_DP,
    val bottomNavFloatingNavBlurRadiusDp: Float = BottomNavBlurDefaults.FLOATING_NAV_DEFAULT_RADIUS_DP,
    val freeWindow: FreeWindowSettings = FreeWindowSettings(),
    val floatingPointer: FloatingPointerSettings = FloatingPointerSettings(),
    val floatBallEnabled: Boolean = false,
    val floatBallSizeDp: Float = 48f,
    val floatBallPickCrossArmDp: Float = 7.5f,
    val floatBallOpacity: Float = 0.88f,
    val floatBallHideWhenIdle: Boolean = true,
    val floatBallIdleFreezeGif: Boolean = true,
    val floatBallIdleOpacity: Float = 0.38f,
    val floatBallIdleDelayMs: Int = 3000,
    val floatBallVisibleFraction: Float = 1f,
    val floatBallCustomCenterXFraction: Float = 0.92f,
    val floatBallPositionYFraction: Float = 0.55f,
    val floatBallOcrFallbackEnabled: Boolean = true,
    val floatBallOcrModelId: String = "",
    val ocrDownloadWifiOnly: Boolean = true,
    val floatBallPointerSpeedFraction: Float = 0.35f,
    val floatBallPointerSpeedVerticalFraction: Float = 0.35f,
    val floatBallPickAccelZoneFraction: Float = 0.5f,
    val floatBallPositionMode: FloatBallPositionMode = FloatBallPositionMode.RIGHT,
    val floatBallActiveSide: FloatBallSide = FloatBallSide.RIGHT,
    val floatBallLineHeightFraction: Float = 0.08f,
    val floatBallLineWidthDp: Float = FloatBallSettings.DEFAULT_LINE_WIDTH_DP,
    val floatBallLineOpacity: Float = 0.9f,
    val floatBallGestureActions: Map<FloatBallGestureType, GestureAction> = FloatBallGestureCodec.defaultActions(),
    val floatBallStyleType: FloatBallStyleType = FloatBallStyleType.DEFAULT,
    val floatBallCustomImageUri: String = "",
    val floatBallCustomImageNoCrop: Boolean = false,
    val floatBallCustomImageAspect: Float = 0f,
    val floatBallSlideshowUris: List<String> = emptyList(),
    /** 幻灯片切换间隔（秒）。 */
    val floatBallSlideshowIntervalSeconds: Int = FloatBallSettings.DEFAULT_SLIDESHOW_INTERVAL_SECONDS,
    val floatBallGifUri: String = "",
    val floatBallPickOffsetDp: Float = 48f,
    val floatBallPickTextSizeSp: Float = 15f,
    val floatBallPickBottomTransitionFraction: Float = 0f,
    val floatBallPickTextFirstPanel: Boolean = false,
    val floatBallPickPanelStyle: PickResultPanelStyle = PickResultPanelStyle.TAB_PAGED,
    val floatBallPickPanelPlacement: PickResultPanelPlacement = PickResultPanelPlacement.BOTTOM_DOCKED,
    val floatBallPickSearchGridDefaultState: PickResultSearchGridDefaultState = PickResultSearchGridDefaultState.REMEMBER_LAST,
    val floatBallPickSearchGridLastExpanded: Boolean = false,
    val floatBallPickTextModeDefault: PickResultTextModeDefault = PickResultTextModeDefault.ALWAYS_ON,
    val floatBallPickTextModeLastMode: String = "",
    /** 点词粒度；CHARACTER = 不分词、无 chip 外观。 */
    val floatBallPickWordTapGranularity: PickResultWordTapGranularity =
        PickResultWordTapGranularity.WORD,
    /** 分词时把网址/链接收成一个词块（关 = 按 : / . 等标点拆开；按字模式不生效）。 */
    val floatBallPickMergeLinks: Boolean = true,
    val floatBallPickDefaultSearchEngineId: String? = null,
    val floatBallPickAutoSelectAll: Boolean = false,
    val floatBallPickCopyDismissPanel: Boolean = false,
    val floatBallPickCopyButtonPosition: PickResultCopyButtonPosition = PickResultCopyButtonPosition.LEFT,
    val floatBallPickImageToolbarPosition: PickResultImageToolbarPosition = PickResultImageToolbarPosition.LEFT,
    /** 底栏纯图标模式：文本区与图片区一起切换；开启后两项「位置」设置不再生效。 */
    val floatBallPickIconBarEnabled: Boolean = false,
    val floatBallPickTextBarOrder: List<String> = emptyList(),
    val floatBallPickTextBarHidden: Set<String> = emptySet(),
    val floatBallPickImageBarOrder: List<String> = emptyList(),
    val floatBallPickImageBarHidden: Set<String> = emptySet(),
    val floatBallPickHapticEnabled: Boolean = true,
    /** 拖动悬浮球是否取词（关 = 拖动只跟手，不显示取词光标/选区）。 */
    val floatBallDragPickEnabled: Boolean = true,
    val floatBallDragPasteEnabled: Boolean = true,
    val floatBallPickPanelEnterAnimationMs: Int = 64,
    val floatBallPickPanelExitAnimationMs: Int = 64,
    val floatBallPointerSlopDp: Float = 12f,
    val floatBallHoverPauseDelayMs: Int = 400,
    val floatBallLongPressMs: Int = 500,
    val floatBallRegionalCancelSlopDp: Float = 16f,
    val floatBallDownSwipeShortPercent: Float = 200f,
    val floatBallSideSwipeShortPercent: Float = 320f,
    val floatBallUpSwipeShortPercent: Float = 256f,
    val floatBallOverlayAnchorAtTouchDown: Boolean = false,
    val floatBallGestureHapticEnabled: Boolean = true,
    val floatBallBackGestureHapticEnabled: Boolean = true,
    val floatBallLongPressAutoSelectRingSlot: Boolean = false,
    val floatBallInstantTranslate: Boolean = true,
    val floatBallTranslateEngine: FloatBallTranslateEngine = FloatBallTranslateEngine.GOOGLE,
    val floatBallTranslateTargetLang: String = TranslateTargetLanguages.FOLLOW_APP,
    val floatBallTranslateAppPackage: String = "",
    val floatBallImageSearchPickPanelTransparency: Float = 0.65f,
    val shareImageOcrHistoryEnabled: Boolean = true,
    val clipboardBackgroundMonitoring: Boolean = true,
    val clipboardBackgroundMonitoringMode: ClipboardMonitoringMode = ClipboardMonitoringMode.FOLLOW_PRIVILEGE,
    val clipboardMonitoringChannel: ClipboardMonitoringChannel = ClipboardMonitoringChannel.FOLLOW_PRIVILEGE,
    val clipboardMonitoringCapture: ClipboardMonitoringCapture = ClipboardMonitoringCapture.LOGCAT,
    val clipboardLsposedWhitelist: Set<String> = setOf(CLIPBOARD_LSPOSED_SELF_PACKAGE),
    val clipboardOverlayEnabled: Boolean = false,
    val clipboardOverlayScalePercent: Int = ClipboardOverlayScale.DEFAULT_PERCENT,
    val clipboardScreenshotMonitoring: Boolean = false,
    val clipboardHistoryMaxEntries: Int = 100,
    val clipboardHistoryFloatEnabled: Boolean = false,
    val clipboardHistoryFloatEnabledLandscape: Boolean = false,
    val clipboardHistoryFloatLockPosition: Boolean = true,
    val clipboardHistoryFloatHandleSide: HistoryFloatHandleSide = HistoryFloatHandleSide.DEFAULT,
    val clipboardHistoryFloatHandleYPortrait: Int = HistoryFloatHandlePosition.UNSET_POSITION,
    val clipboardHistoryFloatHandleYLandscape: Int = HistoryFloatHandlePosition.UNSET_POSITION,
    val clipboardHistoryFloatHandleWidthDp: Float = HistoryFloatHandleAppearance.DEFAULT_WIDTH_DP,
    val clipboardHistoryFloatHandleHeightDp: Float = HistoryFloatHandleAppearance.DEFAULT_HEIGHT_DP,
    val clipboardHistoryFloatHandleAlpha: Float = HistoryFloatHandleAppearance.DEFAULT_ALPHA,
    val clipboardFloatEnabled: Boolean = false,
    val clipboardFloatShowChip: Boolean = true,
    val clipboardFloatChipFollowIme: Boolean = true,
    val clipboardFloatChipX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val clipboardFloatChipY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val clipboardFloatPanelPinPosition: Boolean = false,
    val clipboardFloatSingleLineEntryClickAction: ClipboardFloatEntryClickAction = ClipboardFloatEntryClickAction.PASTE,
    val clipboardFloatSingleLineEntryLongPressAction: ClipboardFloatEntryLongPressAction =
        ClipboardFloatEntryLongPressAction.WORD_TAP,
    val clipboardFloatCardEntryClickAction: ClipboardFloatEntryClickAction = ClipboardFloatEntryClickAction.PASTE,
    val clipboardFloatCardEntryLongPressAction: ClipboardFloatEntryLongPressAction =
        ClipboardFloatEntryLongPressAction.DRAG_DROP,
    val clipboardFloatListStyleId: Int = ClipboardFloatListStyle.SINGLE_LINE.id,
    val clipboardFloatPortraitGeometry: ClipboardFloatOrientationGeometry = ClipboardFloatOrientationGeometry(),
    val clipboardFloatLandscapeGeometry: ClipboardFloatOrientationGeometry = ClipboardFloatOrientationGeometry(),
    val clipboardFloatPanelWidthDp: Int = ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP,
    val clipboardFloatPanelHeightDp: Int = ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP,
    val clipboardFloatPanelX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val clipboardFloatPanelY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val clipboardFloatBlockedPackages: Set<String> = emptySet(),
    val clipboardFloatPasteHapticEnabled: Boolean = false,
    val clipboardPasteFvStyleEnabled: Boolean = false,
    val clipboardFloatPasteSuccessCount: Int = 0,
    val clipboardFloatPasteFailCount: Int = 0,
    val clipboardFloatAlpha: Float = 1.0f,
    val clipboardFloatAutoDimWhenUnfocused: Boolean = false,
    val clipboardFloatAutoCloseSeconds: Int = 0,
    val clipboardFloatBlurEnabled: Boolean = false,
    val clipboardFloatBlurRadiusDp: Int = AppSettings.CLIPBOARD_FLOAT_BLUR_RADIUS_DEFAULT_DP,
    val stashPanelBackgroundBlurEnabled: Boolean = false,
    val stashPanelBackgroundBlurRadiusDp: Int = AppSettings.STASH_PANEL_BLUR_RADIUS_DEFAULT_DP,
    /** 从贴边指示条打开收纳面板时先显示哪个页签，见 [StashPanelHandleOpenTab]。 */
    val stashPanelHandleOpenTab: StashPanelHandleOpenTab = StashPanelHandleOpenTab.CLIPBOARD,
    /** 侧拉面板宽度档位，见 [StashPanelWidthMode]。 */
    val stashPanelWidthMode: StashPanelWidthMode = StashPanelWidthMode.STANDARD,
    val stashPanelCustomWidthFraction: Float? = null,
    val defaultImageViewerPackage: String? = null,
    /** 单击图片执行的动作，见 [PickImageTapAction]。 */
    val pickImageTapActionId: String = PickImageTapAction.VIEW.id,
    val imageEditorDelayDeleteEnabled: Boolean = false,
    val searchPanel: SearchPanelSettings = SearchPanelSettings(),
    /** 拖拽流转栏里的文字目标（从搜索引擎卡片拖进去的独立副本）。 */
    val dragFlowTextEngines: List<SearchEngineConfig> = emptyList(),
    val cornerGestureSettings: CornerGestureSettings = CornerGestureSettings(),
    val fingertipRing: FingertipRingSettings = FingertipRingSettings(),
) {
    /** 当前底栏样式的生效模糊半径（派生，不落盘）。 */
    val bottomNavBlurRadiusDp: Float
        get() = when (BottomNavStyle.fromId(bottomNavStyleId)) {
            BottomNavStyle.CLASSIC -> bottomNavClassicBlurRadiusDp
            BottomNavStyle.LIQUID_GLASS -> bottomNavLiquidGlassBlurRadiusDp
            BottomNavStyle.FLOATING_NAV -> bottomNavFloatingNavBlurRadiusDp
        }

    val clipboardFloatListStyle: ClipboardFloatListStyle
        get() = ClipboardFloatListStyle.fromId(clipboardFloatListStyleId)

    // freeWindow 兼容 getters
    val freeWindowEnabled: Boolean get() = freeWindow.freeWindowEnabled
    val freeWindowModeId: Int get() = freeWindow.freeWindowModeId
    val freeWindowFollowTriggerSide: Boolean get() = freeWindow.freeWindowFollowTriggerSide
    val freeWindowWidthFraction: Float get() = freeWindow.freeWindowWidthFraction
    val freeWindowHeightFraction: Float get() = freeWindow.freeWindowHeightFraction
    val freeWindowLeftFraction: Float get() = freeWindow.freeWindowLeftFraction
    val freeWindowTopFraction: Float get() = freeWindow.freeWindowTopFraction
    val freeWindowLandWidthFraction: Float get() = freeWindow.freeWindowLandWidthFraction
    val freeWindowLandHeightFraction: Float get() = freeWindow.freeWindowLandHeightFraction
    val freeWindowLandLeftFraction: Float get() = freeWindow.freeWindowLandLeftFraction
    val freeWindowLandTopFraction: Float get() = freeWindow.freeWindowLandTopFraction

    // floatingPointer 兼容 getters
    val floatingPointerSensitivityFraction: Float get() = floatingPointer.floatingPointerSensitivityFraction
    val floatingPointerJoystickDiameterPx: Float get() = floatingPointer.floatingPointerJoystickDiameterPx
    val floatingPointerPointerDiameterPx: Float get() = floatingPointer.floatingPointerPointerDiameterPx
    val floatingPointerDesignId: String get() = floatingPointer.floatingPointerDesignId
    val floatingPointerRingThicknessPx: Float get() = floatingPointer.floatingPointerRingThicknessPx
    val floatingPointerDotDiameterPx: Float get() = floatingPointer.floatingPointerDotDiameterPx
    val floatingPointerRingColorArgb: Int get() = floatingPointer.floatingPointerRingColorArgb
    val floatingPointerFillColorArgb: Int get() = floatingPointer.floatingPointerFillColorArgb
    val floatingPointerDotColorArgb: Int get() = floatingPointer.floatingPointerDotColorArgb
    val floatingPointerClickVisualFeedbackEnabled: Boolean get() = floatingPointer.floatingPointerClickVisualFeedbackEnabled
    val floatingPointerClickHapticEnabled: Boolean get() = floatingPointer.floatingPointerClickHapticEnabled
    val floatingPointerRippleColorArgb: Int get() = floatingPointer.floatingPointerRippleColorArgb
    val floatingPointerRippleSizeDp: Float get() = floatingPointer.floatingPointerRippleSizeDp
    val floatingPointerRippleDurationMs: Int get() = floatingPointer.floatingPointerRippleDurationMs
    val floatingPointerTrailTypeId: Int get() = floatingPointer.floatingPointerTrailTypeId
    val floatingPointerTrailDurationMs: Int get() = floatingPointer.floatingPointerTrailDurationMs
    val floatingPointerTrailColorArgb: Int get() = floatingPointer.floatingPointerTrailColorArgb
    val floatingPointerHideWhenJoystickReleased: Boolean get() = floatingPointer.floatingPointerHideWhenJoystickReleased
    val floatingPointerClickDistanceThresholdDp: Float get() = floatingPointer.floatingPointerClickDistanceThresholdDp
    val floatingPointerJoystickInnerColorArgb: Int get() = floatingPointer.floatingPointerJoystickInnerColorArgb
    val floatingPointerJoystickOuterColorArgb: Int get() = floatingPointer.floatingPointerJoystickOuterColorArgb
    val floatingPointerJoystickGradientRadiusFraction: Float get() = floatingPointer.floatingPointerJoystickGradientRadiusFraction
    val floatingPointerHideOnOutsideClick: Boolean get() = floatingPointer.floatingPointerHideOnOutsideClick
    val floatingPointerHideOnQuickSwipe: Boolean get() = floatingPointer.floatingPointerHideOnQuickSwipe
    val floatingPointerHideWhenIdle: Boolean get() = floatingPointer.floatingPointerHideWhenIdle
    val floatingPointerIdleHideDelayMs: Int get() = floatingPointer.floatingPointerIdleHideDelayMs
    val floatingPointerReleaseClickAndDismiss: Boolean get() = floatingPointer.floatingPointerReleaseClickAndDismiss
    val floatingPointerReleaseClickKeepJoystick: Boolean get() = floatingPointer.floatingPointerReleaseClickKeepJoystick
    val floatingPointerHoverEnterSelect: Boolean get() = floatingPointer.floatingPointerHoverEnterSelect
    val floatingPointerJoystickLongPressAction: GestureAction get() = floatingPointer.floatingPointerJoystickLongPressAction
    val floatingPointerRadialAlwaysVisible: Boolean get() = floatingPointer.floatingPointerRadialAlwaysVisible
    val floatingPointerRadialLongPressMs: Int get() = floatingPointer.floatingPointerRadialLongPressMs
    val floatingPointerRadialOuterDiameterPx: Float get() = floatingPointer.floatingPointerRadialOuterDiameterPx
    val floatingPointerRadialInnerDiameterPx: Float get() = floatingPointer.floatingPointerRadialInnerDiameterPx
    val floatingPointerRadialOuterColorArgb: Int get() = floatingPointer.floatingPointerRadialOuterColorArgb
    val floatingPointerRadialInnerColorArgb: Int get() = floatingPointer.floatingPointerRadialInnerColorArgb
    val floatingPointerRadialDividerThicknessPx: Float get() = floatingPointer.floatingPointerRadialDividerThicknessPx
    val floatingPointerRadialDividerColorArgb: Int get() = floatingPointer.floatingPointerRadialDividerColorArgb
    val floatingPointerRadialIconSizeFraction: Float get() = floatingPointer.floatingPointerRadialIconSizeFraction
    val floatingPointerRadialIconColorArgb: Int get() = floatingPointer.floatingPointerRadialIconColorArgb
    val floatingPointerRadialSlotActions: List<GestureAction> get() = floatingPointer.floatingPointerRadialSlotActions
    val floatingPointerEdgeThresholdDp: Float get() = floatingPointer.floatingPointerEdgeThresholdDp
    val floatingPointerEdgePreviewSensitivity: Int get() = floatingPointer.floatingPointerEdgePreviewSensitivity
    val floatingPointerEdgePreviewGlowSize: Int get() = floatingPointer.floatingPointerEdgePreviewGlowSize
    val floatingPointerEdgePreviewShowIcon: Boolean get() = floatingPointer.floatingPointerEdgePreviewShowIcon
    val floatingPointerEdgeVisualSizeDp: Float get() = floatingPointer.floatingPointerEdgeVisualSizeDp
    val floatingPointerEdgeVisualOpacity: Int get() = floatingPointer.floatingPointerEdgeVisualOpacity
    val floatingPointerEdgeVisualColorArgb: Int get() = floatingPointer.floatingPointerEdgeVisualColorArgb
    val floatingPointerEdgeActionsConfig: FloatingPointerEdgeActionsConfig get() = floatingPointer.floatingPointerEdgeActionsConfig

    // searchPanel 兼容 getters
    val searchEngines: List<SearchEngineConfig> get() = searchPanel.searchEngines
    val searchEngineGridColumns: Int get() = searchPanel.searchEngineGridColumns
    val searchEngineGridRows: Int get() = searchPanel.searchEngineGridRows
    val searchEngineShowLabels: Boolean get() = searchPanel.searchEngineShowLabels
    val searchEngineIconShape: SearchEngineIconShape get() = searchPanel.searchEngineIconShape
    val searchPanelDefaultEngineId: String? get() = searchPanel.searchPanelDefaultEngineId
    val searchPanelInputBehavior: SearchPanelInputBehavior get() = searchPanel.searchPanelInputBehavior
    val searchPanelEnterAction: SearchPanelEnterAction get() = searchPanel.searchPanelEnterAction
    val searchPanelContactSearchEnabled: Boolean get() = searchPanel.searchPanelContactSearchEnabled
    val searchPanelFileSearchEnabled: Boolean get() = searchPanel.searchPanelFileSearchEnabled
    val searchPanelAppSearchEnabled: Boolean get() = searchPanel.searchPanelAppSearchEnabled
    val searchPanelSettingsSearchEnabled: Boolean get() = searchPanel.searchPanelSettingsSearchEnabled
    val searchPanelShortcutSearchEnabled: Boolean get() = searchPanel.searchPanelShortcutSearchEnabled
    val searchPanelClipboardSearchEnabled: Boolean get() = searchPanel.searchPanelClipboardSearchEnabled
    val searchPanelFileTypesEnabled: Set<String> get() = searchPanel.searchPanelFileTypesEnabled
    val searchPanelFileShowFolders: Boolean get() = searchPanel.searchPanelFileShowFolders
    val searchPanelFileShowSystemFiles: Boolean get() = searchPanel.searchPanelFileShowSystemFiles
    val searchPanelFilePreviewsEnabled: Boolean get() = searchPanel.searchPanelFilePreviewsEnabled
    val searchPanelFileFolderWhitelist: Set<String> get() = searchPanel.searchPanelFileFolderWhitelist
    val searchPanelFileFolderBlacklist: Set<String> get() = searchPanel.searchPanelFileFolderBlacklist
    val searchPanelBarPosition: SearchPanelBarPosition get() = searchPanel.searchPanelBarPosition
    val searchPanelListOrder: SearchPanelListOrder get() = searchPanel.searchPanelListOrder
    val searchPanelAppDisplayStyle: SearchPanelAppDisplayStyle get() = searchPanel.searchPanelAppDisplayStyle
    val searchPanelCalculatorEnabled: Boolean get() = searchPanel.searchPanelCalculatorEnabled
    val searchPanelBackgroundStyle: Int get() = searchPanel.searchPanelBackgroundStyle
    val searchPanelBlurRadiusDp: Int get() = searchPanel.searchPanelBlurRadiusDp
    val searchPanelDimPercent: Int get() = searchPanel.searchPanelDimPercent
    val searchPanelWebSuggestionsEnabled: Boolean get() = searchPanel.searchPanelWebSuggestionsEnabled
    val searchPanelWebSuggestionsCount: Int get() = searchPanel.searchPanelWebSuggestionsCount
    val searchPanelHistoryMaxEntries: Int get() = searchPanel.searchPanelHistoryMaxEntries
    val searchPanelSectionAliases: SearchPanelSectionAliasSettings get() = searchPanel.searchPanelSectionAliases
    val aggregatedImageSearchEngines: List<AggregatedImageSearchEngineConfig> get() = searchPanel.aggregatedImageSearchEngines
    val searchPanelShowAggregatedImageEntry: Boolean get() = searchPanel.searchPanelShowAggregatedImageEntry

    companion object {
        fun from(settings: AppSettings): OverlaySettings = OverlaySettings(
            themeColorArgb = settings.themeColorArgb,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            themePaletteStyleId = settings.themePaletteStyleId,
            themeModeId = settings.themeModeId,
            customColorEnabled = settings.customColorEnabled,
            darkBackgroundStyleId = settings.darkBackgroundStyleId,
            themeColorSpecId = settings.themeColorSpecId,
            uiDensityScale = settings.uiDensityScale,
            bottomNavStyleId = settings.bottomNavStyleId,
            bottomNavRailExpanded = settings.bottomNavRailExpanded,
            bottomNavModeId = settings.bottomNavModeId,
            bottomNavGlassEnabled = settings.bottomNavGlassEnabled,
            topAppBarBlurStyleId = settings.topAppBarBlurStyleId,
            bottomNavClassicBlurRadiusDp = settings.bottomNavClassicBlurRadiusDp,
            bottomNavLiquidGlassBlurRadiusDp = settings.bottomNavLiquidGlassBlurRadiusDp,
            bottomNavFloatingNavBlurRadiusDp = settings.bottomNavFloatingNavBlurRadiusDp,
            freeWindow = settings.freeWindow,
            floatingPointer = settings.floatingPointer,
            floatBallEnabled = settings.floatBallEnabled,
            floatBallSizeDp = settings.floatBallSizeDp,
            floatBallPickCrossArmDp = settings.floatBallPickCrossArmDp,
            floatBallOpacity = settings.floatBallOpacity,
            floatBallHideWhenIdle = settings.floatBallHideWhenIdle,
            floatBallIdleFreezeGif = settings.floatBallIdleFreezeGif,
            floatBallIdleOpacity = settings.floatBallIdleOpacity,
            floatBallIdleDelayMs = settings.floatBallIdleDelayMs,
            floatBallVisibleFraction = settings.floatBallVisibleFraction,
            floatBallCustomCenterXFraction = settings.floatBallCustomCenterXFraction,
            floatBallPositionYFraction = settings.floatBallPositionYFraction,
            floatBallOcrFallbackEnabled = settings.floatBallOcrFallbackEnabled,
            floatBallOcrModelId = settings.floatBallOcrModelId,
            ocrDownloadWifiOnly = settings.ocrDownloadWifiOnly,
            floatBallPointerSpeedFraction = settings.floatBallPointerSpeedFraction,
            floatBallPointerSpeedVerticalFraction = settings.floatBallPointerSpeedVerticalFraction,
            floatBallPickAccelZoneFraction = settings.floatBallPickAccelZoneFraction,
            floatBallPositionMode = settings.floatBallPositionMode,
            floatBallActiveSide = settings.floatBallActiveSide,
            floatBallLineHeightFraction = settings.floatBallLineHeightFraction,
            floatBallLineWidthDp = settings.floatBallLineWidthDp,
            floatBallLineOpacity = settings.floatBallLineOpacity,
            floatBallGestureActions = settings.floatBallGestureActions,
            floatBallStyleType = settings.floatBallStyleType,
            floatBallCustomImageUri = settings.floatBallCustomImageUri,
            floatBallCustomImageNoCrop = settings.floatBallCustomImageNoCrop,
            floatBallCustomImageAspect = settings.floatBallCustomImageAspect,
            floatBallSlideshowUris = settings.floatBallSlideshowUris,
            floatBallSlideshowIntervalSeconds = settings.floatBallSlideshowIntervalSeconds,
            floatBallGifUri = settings.floatBallGifUri,
            floatBallPickOffsetDp = settings.floatBallPickOffsetDp,
            floatBallPickTextSizeSp = settings.floatBallPickTextSizeSp,
            floatBallPickBottomTransitionFraction = settings.floatBallPickBottomTransitionFraction,
            floatBallPickTextFirstPanel = settings.floatBallPickTextFirstPanel,
            floatBallPickPanelStyle = settings.floatBallPickPanelStyle,
            floatBallPickPanelPlacement = settings.floatBallPickPanelPlacement,
            floatBallPickSearchGridDefaultState = settings.floatBallPickSearchGridDefaultState,
            floatBallPickSearchGridLastExpanded = settings.floatBallPickSearchGridLastExpanded,
            floatBallPickTextModeDefault = settings.floatBallPickTextModeDefault,
            floatBallPickTextModeLastMode = settings.floatBallPickTextModeLastMode,
            floatBallPickWordTapGranularity = settings.floatBallPickWordTapGranularity,
            floatBallPickMergeLinks = settings.floatBallPickMergeLinks,
            floatBallPickDefaultSearchEngineId = settings.floatBallPickDefaultSearchEngineId,
            floatBallPickAutoSelectAll = settings.floatBallPickAutoSelectAll,
            floatBallPickCopyDismissPanel = settings.floatBallPickCopyDismissPanel,
            floatBallPickCopyButtonPosition = settings.floatBallPickCopyButtonPosition,
            floatBallPickImageToolbarPosition = settings.floatBallPickImageToolbarPosition,
            floatBallPickIconBarEnabled = settings.floatBallPickIconBarEnabled,
            floatBallPickTextBarOrder = settings.floatBallPickTextBarOrder,
            floatBallPickTextBarHidden = settings.floatBallPickTextBarHidden,
            floatBallPickImageBarOrder = settings.floatBallPickImageBarOrder,
            floatBallPickImageBarHidden = settings.floatBallPickImageBarHidden,
            floatBallPickHapticEnabled = settings.floatBallPickHapticEnabled,
            floatBallDragPickEnabled = settings.floatBallDragPickEnabled,
            floatBallDragPasteEnabled = settings.floatBallDragPasteEnabled,
            floatBallPickPanelEnterAnimationMs = settings.floatBallPickPanelEnterAnimationMs,
            floatBallPickPanelExitAnimationMs = settings.floatBallPickPanelExitAnimationMs,
            floatBallPointerSlopDp = settings.floatBallPointerSlopDp,
            floatBallHoverPauseDelayMs = settings.floatBallHoverPauseDelayMs,
            floatBallLongPressMs = settings.floatBallLongPressMs,
            floatBallRegionalCancelSlopDp = settings.floatBallRegionalCancelSlopDp,
            floatBallDownSwipeShortPercent = settings.floatBallDownSwipeShortPercent,
            floatBallSideSwipeShortPercent = settings.floatBallSideSwipeShortPercent,
            floatBallUpSwipeShortPercent = settings.floatBallUpSwipeShortPercent,
            floatBallOverlayAnchorAtTouchDown = settings.floatBallOverlayAnchorAtTouchDown,
            floatBallGestureHapticEnabled = settings.floatBallGestureHapticEnabled,
            floatBallBackGestureHapticEnabled = settings.floatBallBackGestureHapticEnabled,
            floatBallLongPressAutoSelectRingSlot = settings.floatBallLongPressAutoSelectRingSlot,
            floatBallInstantTranslate = settings.floatBallInstantTranslate,
            floatBallTranslateEngine = settings.floatBallTranslateEngine,
            floatBallTranslateTargetLang = settings.floatBallTranslateTargetLang,
            floatBallTranslateAppPackage = settings.floatBallTranslateAppPackage,
            floatBallImageSearchPickPanelTransparency = settings.floatBallImageSearchPickPanelTransparency,
            shareImageOcrHistoryEnabled = settings.shareImageOcrHistoryEnabled,
            clipboardBackgroundMonitoring = settings.clipboardBackgroundMonitoring,
            clipboardBackgroundMonitoringMode = settings.clipboardBackgroundMonitoringMode,
            clipboardMonitoringChannel = settings.clipboardMonitoringChannel,
            clipboardMonitoringCapture = settings.clipboardMonitoringCapture,
            clipboardLsposedWhitelist = settings.clipboardLsposedWhitelist,
            clipboardOverlayEnabled = settings.clipboardOverlayEnabled,
            clipboardOverlayScalePercent = settings.clipboardOverlayScalePercent,
            clipboardScreenshotMonitoring = settings.clipboardScreenshotMonitoring,
            clipboardHistoryMaxEntries = settings.clipboardHistoryMaxEntries,
            clipboardHistoryFloatEnabled = settings.clipboardHistoryFloatEnabled,
            clipboardHistoryFloatEnabledLandscape = settings.clipboardHistoryFloatEnabledLandscape,
            clipboardHistoryFloatLockPosition = settings.clipboardHistoryFloatLockPosition,
            clipboardHistoryFloatHandleSide = settings.clipboardHistoryFloatHandleSide,
            clipboardHistoryFloatHandleYPortrait = settings.clipboardHistoryFloatHandleYPortrait,
            clipboardHistoryFloatHandleYLandscape = settings.clipboardHistoryFloatHandleYLandscape,
            clipboardHistoryFloatHandleWidthDp = settings.clipboardHistoryFloatHandleWidthDp,
            clipboardHistoryFloatHandleHeightDp = settings.clipboardHistoryFloatHandleHeightDp,
            clipboardHistoryFloatHandleAlpha = settings.clipboardHistoryFloatHandleAlpha,
            clipboardFloatEnabled = settings.clipboardFloatEnabled,
            clipboardFloatShowChip = settings.clipboardFloatShowChip,
            clipboardFloatChipFollowIme = settings.clipboardFloatChipFollowIme,
            clipboardFloatChipX = settings.clipboardFloatChipX,
            clipboardFloatChipY = settings.clipboardFloatChipY,
            clipboardFloatPanelPinPosition = settings.clipboardFloatPanelPinPosition,
            clipboardFloatSingleLineEntryClickAction = settings.clipboardFloatSingleLineEntryClickAction,
            clipboardFloatSingleLineEntryLongPressAction = settings.clipboardFloatSingleLineEntryLongPressAction,
            clipboardFloatCardEntryClickAction = settings.clipboardFloatCardEntryClickAction,
            clipboardFloatCardEntryLongPressAction = settings.clipboardFloatCardEntryLongPressAction,
            clipboardFloatListStyleId = settings.clipboardFloatListStyleId,
            clipboardFloatPortraitGeometry = settings.clipboardFloatPortraitGeometry,
            clipboardFloatLandscapeGeometry = settings.clipboardFloatLandscapeGeometry,
            clipboardFloatPanelWidthDp = settings.clipboardFloatPanelWidthDp,
            clipboardFloatPanelHeightDp = settings.clipboardFloatPanelHeightDp,
            clipboardFloatPanelX = settings.clipboardFloatPanelX,
            clipboardFloatPanelY = settings.clipboardFloatPanelY,
            clipboardFloatBlockedPackages = settings.clipboardFloatBlockedPackages,
            clipboardFloatPasteHapticEnabled = settings.clipboardFloatPasteHapticEnabled,
            clipboardPasteFvStyleEnabled = settings.clipboardPasteFvStyleEnabled,
            clipboardFloatPasteSuccessCount = settings.clipboardFloatPasteSuccessCount,
            clipboardFloatPasteFailCount = settings.clipboardFloatPasteFailCount,
            clipboardFloatAlpha = settings.clipboardFloatAlpha,
            clipboardFloatAutoDimWhenUnfocused = settings.clipboardFloatAutoDimWhenUnfocused,
            clipboardFloatAutoCloseSeconds = settings.clipboardFloatAutoCloseSeconds,
            clipboardFloatBlurEnabled = settings.clipboardFloatBlurEnabled,
            clipboardFloatBlurRadiusDp = settings.clipboardFloatBlurRadiusDp,
            stashPanelBackgroundBlurEnabled = settings.stashPanelBackgroundBlurEnabled,
            stashPanelBackgroundBlurRadiusDp = settings.stashPanelBackgroundBlurRadiusDp,
            stashPanelHandleOpenTab = settings.stashPanelHandleOpenTab,
            stashPanelWidthMode = settings.stashPanelWidthMode,
            stashPanelCustomWidthFraction = settings.stashPanelCustomWidthFraction,
            defaultImageViewerPackage = settings.defaultImageViewerPackage,
            pickImageTapActionId = settings.pickImageTapActionId,
            imageEditorDelayDeleteEnabled = settings.imageEditorDelayDeleteEnabled,
            searchPanel = settings.searchPanel,
            dragFlowTextEngines = settings.dragFlowTextEngines,
            cornerGestureSettings = settings.cornerGestureSettings,
            fingertipRing = settings.fingertipRing,
        )
    }
}

object BottomNavBlurDefaults {
    /** 经典底栏默认模糊半径；24dp 在 72dp 高的胶囊上过糊，透出的内容会失去结构。 */
    const val DEFAULT_RADIUS_DP = 16f
    const val LIQUID_GLASS_DEFAULT_RADIUS_DP = 4f
    const val FLOATING_NAV_DEFAULT_RADIUS_DP = 7f
    const val MIN_RADIUS_DP = 0f
    const val MAX_RADIUS_DP = 32f
}

object PickPanelSlideAnimationDefaults {
    const val MIN_MS = 0
    const val MAX_MS = 400
    const val DEFAULT_MS = 64

    fun coerceMs(value: Int): Int = value.coerceIn(MIN_MS, MAX_MS)
}

/** 是否走 Haze 采样/合成（毛玻璃总开关开启且模糊半径 > 0）。 */
fun OverlaySettings.usesBottomNavHaze(): Boolean =
    bottomNavGlassEnabled && bottomNavBlurRadiusDp > BottomNavBlurDefaults.MIN_RADIUS_DP
