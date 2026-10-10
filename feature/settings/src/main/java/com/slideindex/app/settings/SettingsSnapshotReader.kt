package com.slideindex.app.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.slideindex.app.floatball.FloatBallGestureCodec
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureRuleCodec
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.SideGestureDefaults
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleCodec
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessagePlacementFractions
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.message.SideBubbleVerticalAnchor
import com.slideindex.app.message.SideBubbleFontSize
import com.slideindex.app.message.DanmakuSpeed
import com.slideindex.app.message.MessageAppFilterCodec
import com.slideindex.app.message.MessageThemeIds
import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.otp.OtpCodeAlertPolicy
import com.slideindex.app.otp.OtpMatchRuleCodec
import com.slideindex.app.otp.OtpRecordLimits
import com.slideindex.app.otp.SmsBlacklistRuleSetCodec
import com.slideindex.app.shake.FaceDownGestureCodec
import com.slideindex.app.shake.FaceDownGestureSettings
import com.slideindex.app.shake.ShakeGestureCodec
import com.slideindex.app.shake.ShakeSensitivityScale
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.translate.TranslateTargetLanguages
import com.slideindex.app.backtap.BackTapMode
import com.slideindex.app.backtap.BackTapSettings
import com.slideindex.app.shell.ShellCommandCodec
import com.slideindex.app.activity.ActivityShortcutCodec
import com.slideindex.app.widget.WidgetPanelCodec

internal object SettingsSnapshotReader {
    private const val LEGACY_MESSAGE_AUTO_DISMISS_SECONDS_KEY = "message_auto_dismiss_seconds"

    fun read(prefs: Preferences, context: Context): AppSettings {
        val legacyWidth = prefs[SettingsPreferenceKeys.EDGE_TRIGGER_WIDTH] ?: 20f
        val legacyTop = prefs[SettingsPreferenceKeys.TRIGGER_TOP] ?: 0.30f
        val legacyHeight = prefs[SettingsPreferenceKeys.TRIGGER_HEIGHT] ?: 0.38f
        val leftTop = prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] ?: legacyTop
        val rightTop = prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] ?: legacyTop
        val leftHeight = prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] ?: legacyHeight
        val rightHeight = prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] ?: legacyHeight
        val legacyShortSwipe = prefs[SettingsPreferenceKeys.SHORT_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP
        val legacyLongSwipe = prefs[SettingsPreferenceKeys.LONG_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP
        val legacyEdgeWidthInherit = prefs.legacyTriggerEdgeWidthInherits()
        val leftHandles = prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES]?.let {
            TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe, legacyEdgeWidthInherit)
        } ?: listOf(TriggerHandle.default(leftTop, leftHeight))
        val rightHandles = prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES]?.let {
            TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe, legacyEdgeWidthInherit)
        } ?: listOf(TriggerHandle.default(rightTop, rightHeight))
        val bottomHandles = prefs[SettingsPreferenceKeys.BOTTOM_TRIGGER_HANDLES]?.let { raw ->
            if (raw.isEmpty()) {
                emptyList()
            } else {
                TriggerHandleCodec.decodeAll(raw, legacyShortSwipe, legacyLongSwipe, legacyEdgeWidthInherit)
            }
        } ?: listOf(TriggerHandle.bottomDefault())
        val topHandles = prefs[SettingsPreferenceKeys.TOP_TRIGGER_HANDLES]?.let { raw ->
            if (raw.isEmpty()) {
                emptyList()
            } else {
                TriggerHandleCodec.decodeAll(raw, legacyShortSwipe, legacyLongSwipe, legacyEdgeWidthInherit)
            }
        } ?: listOf(TriggerHandle.topDefault())
        val leftHandlesLandscape = decodeLandscapeHandles(
            prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES_LANDSCAPE],
            legacyShortSwipe,
            legacyLongSwipe,
            legacyEdgeWidthInherit,
        )
        val rightHandlesLandscape = decodeLandscapeHandles(
            prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES_LANDSCAPE],
            legacyShortSwipe,
            legacyLongSwipe,
            legacyEdgeWidthInherit,
        )
        val bottomHandlesLandscape = decodeLandscapeHandles(
            prefs[SettingsPreferenceKeys.BOTTOM_TRIGGER_HANDLES_LANDSCAPE],
            legacyShortSwipe,
            legacyLongSwipe,
            legacyEdgeWidthInherit,
        )
        val topHandlesLandscape = decodeLandscapeHandles(
            prefs[SettingsPreferenceKeys.TOP_TRIGGER_HANDLES_LANDSCAPE],
            legacyShortSwipe,
            legacyLongSwipe,
            legacyEdgeWidthInherit,
        )
        val legacyAngleConfig = readGestureAngleConfig(prefs)
        return AppSettings(
            serviceEnabled = prefs[SettingsPreferenceKeys.SERVICE_ENABLED] ?: false,
            edgeTrigger = EdgeTriggerSettings(
            leftEdgeEnabled = prefs[SettingsPreferenceKeys.LEFT_EDGE_ENABLED] ?: true,
            rightEdgeEnabled = prefs[SettingsPreferenceKeys.RIGHT_EDGE_ENABLED] ?: true,
            leftEdgeTriggerWidthDp = prefs[SettingsPreferenceKeys.LEFT_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            rightEdgeTriggerWidthDp = prefs[SettingsPreferenceKeys.RIGHT_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            bottomEdgeTriggerWidthDp = prefs[SettingsPreferenceKeys.BOTTOM_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            topEdgeTriggerWidthDp = prefs[SettingsPreferenceKeys.TOP_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            leftTriggerTopFraction = leftTop,
            rightTriggerTopFraction = rightTop,
            leftTriggerHeightFraction = leftHeight,
            rightTriggerHeightFraction = rightHeight,
            leftTriggerHandles = leftHandles,
            rightTriggerHandles = rightHandles,
            bottomTriggerHandles = bottomHandles,
            topTriggerHandles = topHandles,
            leftTriggerHandlesLandscape = leftHandlesLandscape,
            rightTriggerHandlesLandscape = rightHandlesLandscape,
            bottomTriggerHandlesLandscape = bottomHandlesLandscape,
            topTriggerHandlesLandscape = topHandlesLandscape,
            landscapeTriggersInitialized = prefs[SettingsPreferenceKeys.LANDSCAPE_TRIGGERS_INITIALIZED]
                ?: hasAnyLandscapeHandleStorage(prefs),
            gestureRulesLandscape = readGestureRulesLandscape(prefs),
            leftDefaultTriggerModeLandscape = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE_LANDSCAPE]
                    ?: prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE]
                    ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            rightDefaultTriggerModeLandscape = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE_LANDSCAPE]
                    ?: prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE]
                    ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            bottomDefaultTriggerModeLandscape = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.BOTTOM_DEFAULT_TRIGGER_MODE_LANDSCAPE]
                    ?: prefs[SettingsPreferenceKeys.BOTTOM_DEFAULT_TRIGGER_MODE]
                    ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            topDefaultTriggerModeLandscape = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.TOP_DEFAULT_TRIGGER_MODE_LANDSCAPE]
                    ?: prefs[SettingsPreferenceKeys.TOP_DEFAULT_TRIGGER_MODE]
                    ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            interceptSystemBackGesture = prefs[SettingsPreferenceKeys.INTERCEPT_SYSTEM_BACK] ?: false,
            systemGestureTakeoverTop = prefs[SettingsPreferenceKeys.SYSTEM_GESTURE_TAKEOVER_TOP] ?: false,
            systemGestureTakeoverSides = prefs[SettingsPreferenceKeys.SYSTEM_GESTURE_TAKEOVER_SIDES] ?: false,
            systemGestureTakeoverBottom = prefs[SettingsPreferenceKeys.SYSTEM_GESTURE_TAKEOVER_BOTTOM] ?: false,
            limitMaxInterceptLength = prefs[SettingsPreferenceKeys.LIMIT_MAX_INTERCEPT_LENGTH] ?: false,
            leftDefaultTriggerMode = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE] ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            rightDefaultTriggerMode = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE] ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            bottomDefaultTriggerMode = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.BOTTOM_DEFAULT_TRIGGER_MODE] ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            topDefaultTriggerMode = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.TOP_DEFAULT_TRIGGER_MODE] ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            shortSwipeDistanceDp = prefs[SettingsPreferenceKeys.SHORT_SWIPE_DISTANCE_DP] ?: 60f,
            longSwipeDistanceDp = prefs[SettingsPreferenceKeys.LONG_SWIPE_DISTANCE_DP] ?: 120f,
            gestureHintEnabled = prefs[SettingsPreferenceKeys.GESTURE_HINT_ENABLED] ?: true,
            gestureHintStyleId = prefs[SettingsPreferenceKeys.GESTURE_HINT_STYLE] ?: GestureHintStyle.BUBBLE.id,
            gestureHintFingerOffsetDp = prefs[SettingsPreferenceKeys.GESTURE_HINT_FINGER_OFFSET_DP] ?: 0f,
            androidBackColorSourceId = prefs[SettingsPreferenceKeys.ANDROID_BACK_COLOR_SOURCE]
                ?: AndroidBackColorSource.APP_THEME.id,
            swipeHoverDurationMs = prefs[SettingsPreferenceKeys.SWIPE_HOVER_DURATION_MS]
                ?: SwipeHoverDurationLimits.DEFAULT_MS,
            triggerDoubleTapIntervalMs =
                prefs[SettingsPreferenceKeys.TRIGGER_DOUBLE_TAP_INTERVAL_MS]?.coerceIn(0, 800) ?: 300,
            animationStyles = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES]),
            gestureAngles = GestureAnglesCodec.read(prefs, legacyAngleConfig),
            keyboardTriggerBehaviorPortrait = KeyboardTriggerBehavior.fromStorageKey(
                prefs[SettingsPreferenceKeys.KEYBOARD_TRIGGER_BEHAVIOR_PORTRAIT],
            ),
            keyboardTriggerBehaviorLandscape = KeyboardTriggerBehavior.fromStorageKey(
                prefs[SettingsPreferenceKeys.KEYBOARD_TRIGGER_BEHAVIOR_LANDSCAPE],
            ),
            keyboardTriggerNarrowPercentPortrait = KeyboardTriggerNarrowPercents.coerce(
                prefs[SettingsPreferenceKeys.KEYBOARD_TRIGGER_NARROW_PERCENT_PORTRAIT]
                    ?: KeyboardTriggerNarrowPercents.DEFAULT,
            ),
            keyboardTriggerNarrowPercentLandscape = KeyboardTriggerNarrowPercents.coerce(
                prefs[SettingsPreferenceKeys.KEYBOARD_TRIGGER_NARROW_PERCENT_LANDSCAPE]
                    ?: KeyboardTriggerNarrowPercents.DEFAULT,
            ),
            ),
            indexHeightFraction = prefs[SettingsPreferenceKeys.INDEX_HEIGHT] ?: 0.42f,
            hideEmptyIndexLetters = prefs[SettingsPreferenceKeys.HIDE_EMPTY_INDEX_LETTERS] ?: true,
            appsPerRow = prefs[SettingsPreferenceKeys.APPS_PER_ROW] ?: 3,
            quickLauncherColumnsPerPage = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_COLUMNS_PER_PAGE]
                ?: prefs[SettingsPreferenceKeys.APPS_PER_ROW]
                ?: 3,
            quickLauncherRowsPerPage = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ROWS_PER_PAGE] ?: 4,
            panelOpacity = prefs[SettingsPreferenceKeys.PANEL_OPACITY] ?: 0.95f,
            hapticEnabled = prefs[SettingsPreferenceKeys.HAPTIC_ENABLED] ?: true,
            hapticStrengthLevel = prefs[SettingsPreferenceKeys.HAPTIC_STRENGTH] ?: HapticStrength.MEDIUM.level,
            hapticGestureTriggerEnabled = prefs[SettingsPreferenceKeys.HAPTIC_GESTURE_TRIGGER_ENABLED] ?: true,
            hapticSlotSelectionEnabled = prefs[SettingsPreferenceKeys.HAPTIC_SLOT_SELECTION_ENABLED] ?: true,
            hapticConfirmLaunchEnabled = prefs[SettingsPreferenceKeys.HAPTIC_CONFIRM_LAUNCH_ENABLED] ?: true,
            hideFromRecents = prefs[SettingsPreferenceKeys.HIDE_FROM_RECENTS] ?: false,
            privilegeMode = PrivilegeMode.fromStorage(prefs[SettingsPreferenceKeys.PRIVILEGE_MODE]),
            predictiveBackEnabled = prefs[SettingsPreferenceKeys.PREDICTIVE_BACK_ENABLED] ?: false,
            appUiLanguageTag = prefs[SettingsPreferenceKeys.APP_UI_LANGUAGE_TAG] ?: "",
            swipeDismissEnabled = prefs[SettingsPreferenceKeys.SWIPE_DISMISS_ENABLED] ?: true,
            accessibilityKeepAliveEnabled = prefs[SettingsPreferenceKeys.ACCESSIBILITY_KEEP_ALIVE] ?: false,
            hideTriggerInLandscape = prefs[SettingsPreferenceKeys.HIDE_TRIGGER_LANDSCAPE] ?: false,
            hideTriggerOnLockScreen = prefs[SettingsPreferenceKeys.HIDE_TRIGGER_LOCK_SCREEN] ?: false,
            hideTriggerOnLauncher = prefs[SettingsPreferenceKeys.HIDE_TRIGGER_LAUNCHER] ?: false,
            dynamicColorEnabled = prefs[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            freeWindow = FreeWindowSettings(
                freeWindowEnabled = prefs[SettingsPreferenceKeys.FREE_WINDOW_ENABLED] ?: false,
                freeWindowModeId = prefs[SettingsPreferenceKeys.FREE_WINDOW_MODE] ?: FreeWindowMode.detectDefault().id,
                freeWindowWidthFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_WIDTH] ?: 0.8f,
                freeWindowHeightFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_HEIGHT] ?: 0.55f,
                freeWindowLeftFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_LEFT] ?: 0.1f,
                freeWindowTopFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_TOP] ?: 0.15f,
                freeWindowLandWidthFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_LAND_WIDTH] ?: 0.68f,
                freeWindowLandHeightFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_LAND_HEIGHT] ?: 0.4f,
                freeWindowLandLeftFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_LAND_LEFT] ?: 0.16f,
                freeWindowLandTopFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_LAND_TOP] ?: 0.05f,
            ),
            launcher = LauncherSettings(
            appLaunchPolicyId = prefs[SettingsPreferenceKeys.APP_LAUNCH_POLICY] ?: legacyLaunchPolicy(prefs),
            longPressLaunchDurationMs = prefs[SettingsPreferenceKeys.LONG_PRESS_LAUNCH_DURATION] ?: 450,
            hiddenAppPackages = prefs[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES] ?: emptySet(),
            freezerAppPackages = prefs[SettingsPreferenceKeys.FREEZER_APP_PACKAGES] ?: emptySet(),
            freezerBootstrapExcludedPackages =
                prefs[SettingsPreferenceKeys.FREEZER_BOOTSTRAP_EXCLUDED_PACKAGES] ?: emptySet(),
            freezerShowInLauncher = prefs[SettingsPreferenceKeys.FREEZER_SHOW_IN_LAUNCHER] ?: false,
            freezerWorkModeId = prefs[SettingsPreferenceKeys.FREEZER_WORK_MODE]
                ?: FreezerWorkMode.DEFAULT.id,
            freezerAppIntents = FreezerAppIntentCodec.decode(
                prefs[SettingsPreferenceKeys.FREEZER_APP_INTENTS],
            ),
            expandPanelSlotActions = readExpandPanelSlotActions(prefs),
            previousAppExcludedPackages =
                prefs[SettingsPreferenceKeys.PREVIOUS_APP_EXCLUDED_PACKAGES] ?: emptySet(),
            excludedAppScopes = readExcludedAppScopes(prefs),
            excludedAppDefaultScopes = readExcludedAppDefaultScopes(prefs),
            gestureRules = readGestureRules(prefs),
            quickLauncherPanels = readQuickLauncherPanels(prefs),
            quickLauncherDisplay = QuickLauncherDisplaySettings.fromPreferences(prefs),
            honeycombLauncher = QuickLauncherItemCodec.decodeAll(
                prefs[SettingsPreferenceKeys.HONEYCOMB_LAUNCHER] ?: emptySet(),
            ),
            honeycombDisplay = HoneycombDisplaySettings.fromPreferences(prefs),
            appCarouselSwitcher = AppCarouselSwitcherSettings.fromPreferences(prefs),
            quickWheels = QuickWheelCodec.decode(prefs),
            fvRingLauncherVertical = FvRingLauncherSettings.fromPreferences(prefs, FvRingLauncherAxis.VERTICAL),
            fvRingLauncherHorizontal = FvRingLauncherSettings.fromPreferences(prefs, FvRingLauncherAxis.HORIZONTAL),
            fvRingLauncherLinkAppearanceAxes = FvRingLauncherSettings.linkFlagsFromPreferences(prefs).linkAppearanceAxes,
            fvRingLauncherLinkSlotAxes = FvRingLauncherSettings.linkFlagsFromPreferences(prefs).linkSlotAxes,
            holographicLauncher = HolographicLauncherSettings.fromPreferences(prefs),
            shellCommands = ShellCommandCodec.decodeAll(prefs[SettingsPreferenceKeys.SHELL_COMMANDS] ?: emptySet()),
            activityShortcuts = ActivityShortcutCodec.decodeAll(
                prefs[SettingsPreferenceKeys.ACTIVITY_SHORTCUTS] ?: emptySet(),
            ),
            launcherShortcutMenuOrder = LauncherShortcutMenuCodec.decode(
                prefs[SettingsPreferenceKeys.LAUNCHER_SHORTCUT_MENU_ORDER],
            ),
            launcherShortcutMenuDisabled =
                prefs[SettingsPreferenceKeys.LAUNCHER_SHORTCUT_MENU_DISABLED] ?: emptySet(),
            ),
            themeColorArgb = prefs[SettingsPreferenceKeys.THEME_COLOR] ?: 0xFF6750A4.toInt(),
            themePaletteStyleId = prefs[SettingsPreferenceKeys.THEME_PALETTE_STYLE]
                ?: ThemePaletteStyle.TONAL_SPOT.id,
            themeModeId = prefs[SettingsPreferenceKeys.THEME_MODE] ?: AppThemeMode.SYSTEM.id,
            customColorEnabled = prefs[SettingsPreferenceKeys.CUSTOM_COLOR_ENABLED] ?: false,
            darkBackgroundStyleId = prefs[SettingsPreferenceKeys.DARK_BACKGROUND_STYLE]
                ?: DarkBackgroundStyle.QUIET_BLUE.id,
            themeColorSpecId = prefs[SettingsPreferenceKeys.THEME_COLOR_SPEC] ?: AppColorSpec.SPEC_2025.id,
            uiDensityScale = UiDensityScaleLimits.normalize(
                prefs[SettingsPreferenceKeys.UI_DENSITY_SCALE] ?: UiDensityScaleLimits.DEFAULT_SCALE,
            ),
            bottomNavStyleId = prefs[SettingsPreferenceKeys.BOTTOM_NAV_STYLE]
                ?: BottomNavStyle.FLOATING_NAV.id,
            bottomNavModeId = prefs[SettingsPreferenceKeys.BOTTOM_NAV_MODE]
                ?: BottomNavMode.ICON_AND_TEXT.id,
            bottomNavGlassEnabled = prefs[SettingsPreferenceKeys.BOTTOM_NAV_GLASS_ENABLED] ?: true,
            topAppBarBlurStyleId = prefs[SettingsPreferenceKeys.TOP_APP_BAR_BLUR_STYLE]
                ?: TopAppBarBlurStyle.PROGRESSIVE.id,
            bottomNavClassicBlurRadiusDp =
                prefs[SettingsPreferenceKeys.BOTTOM_NAV_CLASSIC_BLUR_RADIUS_DP]
                    ?: prefs[SettingsPreferenceKeys.BOTTOM_NAV_BLUR_RADIUS_DP]
                    ?: BottomNavBlurDefaults.DEFAULT_RADIUS_DP,
            bottomNavLiquidGlassBlurRadiusDp =
                prefs[SettingsPreferenceKeys.BOTTOM_NAV_LIQUID_GLASS_BLUR_RADIUS_DP]
                    ?: prefs[SettingsPreferenceKeys.BOTTOM_NAV_BLUR_RADIUS_DP]
                    ?: BottomNavBlurDefaults.LIQUID_GLASS_DEFAULT_RADIUS_DP,
            bottomNavFloatingNavBlurRadiusDp =
                prefs[SettingsPreferenceKeys.BOTTOM_NAV_FLOATING_NAV_BLUR_RADIUS_DP]
                    ?: BottomNavBlurDefaults.FLOATING_NAV_DEFAULT_RADIUS_DP,
            widgetPanel = WidgetPanelSettings(
                widgetPanelPages = WidgetPanelCodec.decodeAll(prefs[SettingsPreferenceKeys.WIDGET_PANEL_PAGES] ?: emptySet()),
                widgetPanelWidthFraction = prefs[SettingsPreferenceKeys.WIDGET_PANEL_WIDTH] ?: 0.8f,
                widgetPanelHeightFraction = prefs[SettingsPreferenceKeys.WIDGET_PANEL_HEIGHT] ?: 0.55f,
                widgetPanelTopFraction = prefs[SettingsPreferenceKeys.WIDGET_PANEL_TOP] ?: 0.15f,
                widgetPanelBlurEnabled = prefs[SettingsPreferenceKeys.WIDGET_PANEL_BLUR] ?: true,
                widgetPanelBlurRadiusDp = prefs[SettingsPreferenceKeys.WIDGET_PANEL_BLUR_RADIUS_DP]
                    ?: AppSettings.WIDGET_PANEL_BLUR_RADIUS_DEFAULT_DP,
            ),
            floatingPointer = FloatingPointerSettings(
            floatingPointerSensitivityFraction = readFloatingPointerSensitivityFraction(prefs),
            floatingPointerJoystickDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_SIZE] ?: 275f,
            floatingPointerPointerDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_POINTER_SIZE] ?: 100f,
            floatingPointerDesignId = prefs[SettingsPreferenceKeys.FLOATING_POINTER_DESIGN_ID] ?: FloatingPointerDesignIds.RING,
            floatingPointerRingThicknessPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RING_THICKNESS] ?: 12f,
            floatingPointerDotDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_DOT_DIAMETER] ?: 15f,
            floatingPointerRingColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RING_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerFillColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_FILL_COLOR] ?: 0x19000000,
            floatingPointerDotColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_DOT_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerClickVisualFeedbackEnabled = prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_VISUAL_FEEDBACK] ?: true,
            floatingPointerClickHapticEnabled = prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_HAPTIC] ?: true,
            floatingPointerRippleColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_COLOR] ?: 0xFFFD746C.toInt(),
            floatingPointerRippleSizeDp = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_SIZE_DP] ?: 80f,
            floatingPointerRippleDurationMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_DURATION_MS] ?: 500,
            floatingPointerTrailTypeId = prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_TYPE] ?: FloatingPointerTrailType.HIGH_DETAIL.id,
            floatingPointerTrailDurationMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_DURATION] ?: 150,
            floatingPointerTrailColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_COLOR] ?: 0x66FF5252,
            floatingPointerHideWhenJoystickReleased = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_ON_RELEASE] ?: false,
            floatingPointerClickDistanceThresholdDp =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_DISTANCE_THRESHOLD_DP] ?: 6f,
            floatingPointerJoystickInnerColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_INNER_COLOR] ?: 0x80FFFFFF.toInt(),
            floatingPointerJoystickOuterColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_OUTER_COLOR] ?: 0x80C0C0C0.toInt(),
            floatingPointerJoystickGradientRadiusFraction = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_GRADIENT] ?: 1f,
            floatingPointerHideOnOutsideClick = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_OUTSIDE_CLICK] ?: true,
            floatingPointerHideOnQuickSwipe = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_QUICK_SWIPE] ?: true,
            floatingPointerHideWhenIdle = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_IDLE] ?: true,
            floatingPointerIdleHideDelayMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_IDLE_DELAY] ?: 3000,
            floatingPointerReleaseClickAndDismiss =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_RELEASE_CLICK_AND_DISMISS] ?: true,
            floatingPointerHoverEnterSelect =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_HOVER_ENTER_SELECT] ?: false,
            floatingPointerJoystickLongPressAction = readFloatingPointerJoystickLongPressAction(prefs),
            floatingPointerRadialAlwaysVisible = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ALWAYS_VISIBLE] ?: false,
            floatingPointerRadialLongPressMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_LONG_PRESS_MS] ?: 500,
            floatingPointerRadialOuterDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_SIZE] ?: 440f,
            floatingPointerRadialInnerDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_SIZE] ?: 192f,
            floatingPointerRadialOuterColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_COLOR] ?: 0xE62B3D4F.toInt(),
            floatingPointerRadialInnerColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_COLOR] ?: 0xE61A1A28.toInt(),
            floatingPointerRadialDividerThicknessPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_SIZE] ?: 4f,
            floatingPointerRadialDividerColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_COLOR] ?: 0x22FFFFFF,
            floatingPointerRadialIconSizeFraction = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_SIZE] ?: 0.85f,
            floatingPointerRadialIconColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerRadialSlotActions = FloatingPointerRadialMenuCodec.decode(
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_SLOTS] ?: emptySet(),
            ),
            floatingPointerEdgeThresholdDp = prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_THRESHOLD_DP] ?: 30f,
            floatingPointerEdgePreviewSensitivity =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_SENSITIVITY] ?: 3,
            floatingPointerEdgePreviewGlowSize =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_GLOW_SIZE] ?: 4,
            floatingPointerEdgePreviewShowIcon =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_SHOW_ICON] ?: true,
            floatingPointerEdgeVisualSizeDp =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_SIZE_DP] ?: 0f,
            floatingPointerEdgeVisualOpacity =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_OPACITY] ?: 75,
            floatingPointerEdgeVisualColorArgb =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_COLOR] ?: 0xFFFD746C.toInt(),
            floatingPointerEdgeActionsConfig = FloatingPointerEdgeActionsCodec.decode(
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] ?: emptySet(),
            ),
            ),
            otp = OtpSettings(
                otpCopyToClipboard = prefs[SettingsPreferenceKeys.OTP_COPY_TO_CLIPBOARD] ?: false,
                otpKeywordsRegex = resolveOtpKeywordsRegex(prefs[SettingsPreferenceKeys.OTP_KEYWORDS_REGEX]),
                otpUserMatchRules = OtpMatchRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.OTP_USER_MATCH_RULES] ?: emptySet()),
                otpDisabledOfficialRuleIds = prefs[SettingsPreferenceKeys.OTP_DISABLED_OFFICIAL_RULE_IDS] ?: emptySet(),
                otpAutoInputEnabled = prefs[SettingsPreferenceKeys.OTP_AUTO_INPUT_ENABLED] ?: false,
                otpAutoConfirmEnabled = prefs[SettingsPreferenceKeys.OTP_AUTO_CONFIRM_ENABLED] ?: false,
                otpAutoInputDelayMs = prefs[SettingsPreferenceKeys.OTP_AUTO_INPUT_DELAY_MS] ?: 0,
                otpAutoInputIntervalMs = prefs[SettingsPreferenceKeys.OTP_AUTO_INPUT_INTERVAL_MS] ?: 0,
                otpLsposedSmsCaptureEnabled = prefs[SettingsPreferenceKeys.OTP_LSPOSED_SMS_CAPTURE_ENABLED] ?: false,
                otpLsposedSystemInjectEnabled = prefs[SettingsPreferenceKeys.OTP_LSPOSED_SYSTEM_INJECT_ENABLED] ?: true,
                otpCodeNotificationEnabled = prefs[SettingsPreferenceKeys.OTP_CODE_NOTIFICATION_ENABLED] ?: false,
                otpCodeNotificationRetentionSeconds = OtpCodeAlertPolicy.normalizeRetentionSeconds(
                    prefs[SettingsPreferenceKeys.OTP_CODE_NOTIFICATION_RETENTION_SECONDS]
                        ?: OtpCodeAlertPolicy.DEFAULT_RETENTION_SECONDS,
                ),
                otpShowCodeToast = prefs[SettingsPreferenceKeys.OTP_SHOW_CODE_TOAST] ?: false,
                otpSmsBlacklist = SmsBlacklistRuleSetCodec.decode(
                    prefs[SettingsPreferenceKeys.OTP_SMS_BLACKLIST],
                ),
                otpBlockCodeSmsEnabled = prefs[SettingsPreferenceKeys.OTP_BLOCK_CODE_SMS_ENABLED] ?: false,
                otpMarkSmsReadEnabled = prefs[SettingsPreferenceKeys.OTP_MARK_SMS_READ_ENABLED] ?: false,
                otpDeleteSmsAfterExtractEnabled =
                prefs[SettingsPreferenceKeys.OTP_DELETE_SMS_AFTER_EXTRACT_ENABLED] ?: false,
                otpRecordCodeEnabled = prefs[SettingsPreferenceKeys.OTP_RECORD_CODE_ENABLED] ?: true,
                otpRecordPlainSmsEnabled = prefs[SettingsPreferenceKeys.OTP_RECORD_PLAIN_SMS_ENABLED] ?: false,
                otpRecordAppNotifyEnabled =
                prefs[SettingsPreferenceKeys.OTP_RECORD_APP_NOTIFY_ENABLED] ?: true,
                otpRecordCodeLimit = OtpRecordLimits.normalize(
                    prefs[SettingsPreferenceKeys.OTP_RECORD_CODE_LIMIT] ?: OtpRecordLimits.DEFAULT,
                ),
                otpRecordPlainSmsLimit = OtpRecordLimits.normalize(
                    prefs[SettingsPreferenceKeys.OTP_RECORD_PLAIN_SMS_LIMIT] ?: OtpRecordLimits.DEFAULT,
                ),
                otpRecordAppNotifyLimit = OtpRecordLimits.normalize(
                    prefs[SettingsPreferenceKeys.OTP_RECORD_APP_NOTIFY_LIMIT] ?: OtpRecordLimits.DEFAULT,
                ),
                otpBlockedPackages = prefs[SettingsPreferenceKeys.OTP_BLOCKED_PACKAGES] ?: emptySet(),
            ),
            shakeGestureSettings = readShakeGestureSettings(prefs),
            backTapSettings = readBackTapSettings(prefs),
            faceDownGestureSettings = readFaceDownGestureSettings(prefs),
            cornerGestureSettings = readCornerGestureSettings(prefs),
            fingertipRing = readFingertipRingSettings(prefs),
            messageReminderSettings = readMessageReminderSettings(prefs),
            debugPerformanceMonitorEnabled = prefs[SettingsPreferenceKeys.DEBUG_PERFORMANCE_MONITOR] ?: false,
            onboardingCompleted = prefs[SettingsPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
            floatBall = FloatBallSettings(
            floatBallEnabled = prefs[SettingsPreferenceKeys.FLOAT_BALL_ENABLED] ?: false,
            floatBallSizeDp = prefs[SettingsPreferenceKeys.FLOAT_BALL_SIZE_DP] ?: 48f,
            floatBallPickCrossArmDp = prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_CROSS_ARM_DP] ?: 7.5f,
            floatBallOpacity = prefs[SettingsPreferenceKeys.FLOAT_BALL_OPACITY] ?: 0.88f,
            floatBallVisibleFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_VISIBLE_FRACTION] ?: 1f,
            floatBallCustomCenterXFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_POSITION_X_FRACTION] ?: 0.92f,
            floatBallPositionYFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_POSITION_Y_FRACTION] ?: 0.55f,
            floatBallOcrFallbackEnabled = prefs[SettingsPreferenceKeys.FLOAT_BALL_OCR_FALLBACK_ENABLED] ?: true,
            floatBallOcrModelId = prefs[SettingsPreferenceKeys.FLOAT_BALL_OCR_MODEL_ID].orEmpty(),
            ocrDownloadWifiOnly = prefs[SettingsPreferenceKeys.OCR_DOWNLOAD_WIFI_ONLY] ?: true,
            floatBallPointerSpeedFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SPEED_FRACTION]
                ?: 0.35f,
            floatBallPointerSpeedVerticalFraction =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SPEED_VERTICAL_FRACTION] ?: 0.35f,
            floatBallPositionMode = FloatBallPositionMode.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_POSITION_MODE],
            ),
            floatBallActiveSide = FloatBallSide.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_ACTIVE_SIDE],
            ),
            floatBallLineHeightFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_LINE_HEIGHT_FRACTION] ?: 0.08f,
            floatBallLineWidthFraction = (prefs[SettingsPreferenceKeys.FLOAT_BALL_LINE_WIDTH_FRACTION] ?: 0.04f)
                .coerceIn(0.01f, 0.50f),
            floatBallLineOpacity = prefs[SettingsPreferenceKeys.FLOAT_BALL_LINE_OPACITY] ?: 0.9f,
            floatBallGestureActions = readFloatBallGestureActions(prefs),
            floatBallStyleType = FloatBallStyleType.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_STYLE_TYPE],
            ),
            floatBallCustomImageUri = prefs[SettingsPreferenceKeys.FLOAT_BALL_CUSTOM_IMAGE_URI].orEmpty(),
            floatBallSlideshowUris = prefs[SettingsPreferenceKeys.FLOAT_BALL_SLIDESHOW_URIS]
                ?.filter { it.isNotBlank() }
                ?.toList()
                ?: emptyList(),
            floatBallGifUri = prefs[SettingsPreferenceKeys.FLOAT_BALL_GIF_URI].orEmpty(),
            floatBallPickOffsetDp = prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_OFFSET_DP] ?: 48f,
            floatBallPickTextSizeSp =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_TEXT_SIZE_SP]?.coerceIn(12f, 22f) ?: 15f,
            floatBallPickBottomTransitionFraction =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_BOTTOM_TRANSITION_FRACTION]?.coerceIn(0.04f, 0.25f)
                    ?: 0.22f,
            floatBallPickTextFirstPanel =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_TEXT_FIRST_PANEL] ?: false,
            floatBallPickPanelStyle =
                PickResultPanelStyle.fromStorageKey(prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_PANEL_STYLE]),
            floatBallPickPanelPlacement = PickResultPanelPlacement.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_PANEL_PLACEMENT],
            ),
            floatBallPickSearchGridDefaultState =
                PickResultSearchGridDefaultState.fromStorageKey(prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_SEARCH_GRID_DEFAULT_STATE]),
            floatBallPickSearchGridLastExpanded =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_SEARCH_GRID_LAST_EXPANDED] ?: false,
            floatBallPickTextModeDefault =
                PickResultTextModeDefault.fromStorageKey(
                    prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_TEXT_MODE_DEFAULT],
                ),
            floatBallPickTextModeLastMode =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_TEXT_MODE_LAST_MODE].orEmpty(),
            floatBallPickDefaultSearchEngineId =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_DEFAULT_SEARCH_ENGINE_ID],
            floatBallPickAutoSelectAll =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_AUTO_SELECT_ALL] ?: false,
            floatBallPickCopyDismissPanel =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_COPY_DISMISS_PANEL] ?: false,
            floatBallPickCopyButtonPosition =
                PickResultCopyButtonPosition.fromStorageKey(
                    prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_COPY_BUTTON_POSITION],
                ),
            floatBallPickImageToolbarPosition =
                PickResultImageToolbarPosition.fromStorageKey(
                    prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_IMAGE_TOOLBAR_POSITION],
                ),
            floatBallPickHapticEnabled =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_HAPTIC_ENABLED] ?: true,
            floatBallDragPasteEnabled =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_DRAG_PASTE_ENABLED] ?: true,
            floatBallPickPanelEnterAnimationMs =
                PickPanelSlideAnimationDefaults.coerceMs(
                    prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_PANEL_ENTER_ANIMATION_MS]
                        ?: PickPanelSlideAnimationDefaults.DEFAULT_MS,
                ),
            floatBallPickPanelExitAnimationMs =
                PickPanelSlideAnimationDefaults.coerceMs(
                    prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_PANEL_EXIT_ANIMATION_MS]
                        ?: PickPanelSlideAnimationDefaults.DEFAULT_MS,
                ),
            floatBallPointerSlopDp = prefs[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SLOP_DP] ?: 4f,
            floatBallHoverPauseDelayMs =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_HOVER_PAUSE_DELAY_MS]?.coerceIn(200, 1000) ?: 400,
            floatBallLongPressMs =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_LONG_PRESS_MS]?.coerceIn(200, 2000) ?: 500,
            floatBallRegionalCancelSlopDp =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_REGIONAL_CANCEL_SLOP_DP]?.coerceIn(3f, 30f) ?: 16f,
            floatBallDownSwipeShortPercent =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_DOWN_SWIPE_SHORT_PERCENT] ?: 200f,
            floatBallSideSwipeShortPercent =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_SIDE_SWIPE_SHORT_PERCENT] ?: 320f,
            floatBallUpSwipeShortPercent =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_UP_SWIPE_SHORT_PERCENT] ?: 256f,
            floatBallOverlayAnchorAtTouchDown =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_OVERLAY_ANCHOR_AT_TOUCH_DOWN] ?: false,
            floatBallInstantTranslate = prefs[SettingsPreferenceKeys.FLOAT_BALL_INSTANT_TRANSLATE] ?: true,
            floatBallTranslateEngine = FloatBallTranslateEngine.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_ENGINE],
            ),
            floatBallTranslateTargetLang = prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_TARGET_LANG]
                ?: TranslateTargetLanguages.FOLLOW_APP,
            floatBallTranslateAppPackage =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_APP_PACKAGE] ?: "",
            floatBallImageSearchPickPanelTransparency =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_IMAGE_SEARCH_PICK_PANEL_TRANSPARENCY]?.coerceIn(0f, 1f)
                    ?: prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_PICK_PANEL_TRANSPARENCY]?.coerceIn(0f, 1f)
                    ?: prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_PICK_PANEL_ALPHA]
                        ?.let { alpha -> (1f - alpha).coerceIn(0f, 1f) }
                    ?: 0.65f,
            shareImageOcrHistoryEnabled = prefs[SettingsPreferenceKeys.SHARE_IMAGE_OCR_HISTORY_ENABLED] ?: true,
            ),
            clipboard = ClipboardSettings(
            clipboardBackgroundMonitoring = prefs[SettingsPreferenceKeys.CLIPBOARD_BACKGROUND_MONITORING] ?: true,
            clipboardBackgroundMonitoringMode = ClipboardMonitoringMode.fromStorage(
                prefs[SettingsPreferenceKeys.CLIPBOARD_BACKGROUND_MONITORING_PATH],
            ),
            // 旧的单一模式值做迁移兜底：没写过新键时按旧值推导通道与采集方式。
            clipboardMonitoringChannel = ClipboardMonitoringChannel.fromStorage(
                prefs[SettingsPreferenceKeys.CLIPBOARD_MONITORING_CHANNEL],
            ).takeIf { prefs[SettingsPreferenceKeys.CLIPBOARD_MONITORING_CHANNEL] != null }
                ?: channelOf(
                    ClipboardMonitoringMode.fromStorage(
                        prefs[SettingsPreferenceKeys.CLIPBOARD_BACKGROUND_MONITORING_PATH],
                    ),
                ),
            clipboardMonitoringCapture = ClipboardMonitoringCapture.fromStorage(
                prefs[SettingsPreferenceKeys.CLIPBOARD_MONITORING_CAPTURE],
            ).takeIf { prefs[SettingsPreferenceKeys.CLIPBOARD_MONITORING_CAPTURE] != null }
                ?: captureOf(
                    ClipboardMonitoringMode.fromStorage(
                        prefs[SettingsPreferenceKeys.CLIPBOARD_BACKGROUND_MONITORING_PATH],
                    ),
                ),
            clipboardLsposedWhitelist =
                prefs[SettingsPreferenceKeys.CLIPBOARD_LSPOSED_WHITELIST]
                    ?: setOf(CLIPBOARD_LSPOSED_SELF_PACKAGE),
            clipboardOverlayEnabled = prefs[SettingsPreferenceKeys.CLIPBOARD_OVERLAY_ENABLED] ?: false,
            clipboardOverlayScalePercent = ClipboardOverlayScale.coerce(
                prefs[SettingsPreferenceKeys.CLIPBOARD_OVERLAY_SCALE_PERCENT]
                    ?: ClipboardOverlayScale.DEFAULT_PERCENT,
            ),
            clipboardScreenshotMonitoring = prefs[SettingsPreferenceKeys.CLIPBOARD_SCREENSHOT_MONITORING] ?: false,
            clipboardHistoryMaxEntries = ClipboardHistoryCapacity.coerce(
                prefs[SettingsPreferenceKeys.CLIPBOARD_HISTORY_MAX_ENTRIES] ?: 100,
            ),
            clipboardHistoryFloatEnabled = prefs[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_ENABLED] ?: false,
            clipboardHistoryFloatEnabledLandscape =
                prefs[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_ENABLED_LANDSCAPE] ?: false,
            clipboardHistoryFloatLockPosition =
                prefs[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_LOCK_POSITION] ?: true,
            clipboardHistoryFloatHandleWidthDp = HistoryFloatHandleWidth.coerce(
                prefs[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_WIDTH_DP] ?: 32,
            ),
            clipboardHistoryFloatHandleYPortrait =
                prefs[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_Y_PORTRAIT]
                    ?: HistoryFloatHandlePosition.UNSET_POSITION,
            clipboardHistoryFloatHandleYLandscape =
                prefs[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_Y_LANDSCAPE]
                    ?: HistoryFloatHandlePosition.UNSET_POSITION,
            clipboardFloatEnabled = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENABLED] ?: false,
            clipboardFloatShowChip = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_SHOW_CHIP] ?: true,
            clipboardFloatChipFollowIme =
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_FOLLOW_IME] ?: true,
            clipboardFloatChipX = ClipboardFloatGeometryPrefs.readOrientationGeometry(prefs, landscape = false).chipX,
            clipboardFloatChipY = ClipboardFloatGeometryPrefs.readOrientationGeometry(prefs, landscape = false).chipY,
            clipboardFloatPanelPinPosition =
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_PIN_POSITION]
                    ?: prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PIN_POSITION]
                    ?: false,
            clipboardFloatSingleLineEntryClickAction = ClipboardFloatEntryGesturePrefs.readClickAction(
                prefs,
                ClipboardFloatListStyle.SINGLE_LINE,
            ),
            clipboardFloatSingleLineEntryLongPressAction = ClipboardFloatEntryGesturePrefs.readLongPressAction(
                prefs,
                ClipboardFloatListStyle.SINGLE_LINE,
            ),
            clipboardFloatCardEntryClickAction = ClipboardFloatEntryGesturePrefs.readClickAction(
                prefs,
                ClipboardFloatListStyle.CARD,
            ),
            clipboardFloatCardEntryLongPressAction = ClipboardFloatEntryGesturePrefs.readLongPressAction(
                prefs,
                ClipboardFloatListStyle.CARD,
            ),
            clipboardFloatListStyleId = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_LIST_STYLE]
                ?: ClipboardFloatListStyle.SINGLE_LINE.id,
            clipboardFloatPortraitGeometry = ClipboardFloatGeometryPrefs.readOrientationGeometry(
                prefs,
                landscape = false,
            ),
            clipboardFloatLandscapeGeometry = ClipboardFloatGeometryPrefs.readOrientationGeometry(
                prefs,
                landscape = true,
            ),
            clipboardFloatPanelWidthDp = ClipboardFloatGeometryPrefs.readOrientationGeometry(
                prefs,
                landscape = false,
            ).panelWidthDp,
            clipboardFloatPanelHeightDp = ClipboardFloatGeometryPrefs.readOrientationGeometry(
                prefs,
                landscape = false,
            ).panelHeightDp,
            clipboardFloatPanelX = ClipboardFloatGeometryPrefs.readOrientationGeometry(
                prefs,
                landscape = false,
            ).panelX,
            clipboardFloatPanelY = ClipboardFloatGeometryPrefs.readOrientationGeometry(
                prefs,
                landscape = false,
            ).panelY,
            clipboardFloatBlockedPackages =
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_BLOCKED_PACKAGES] ?: emptySet(),
            clipboardFloatPasteHapticEnabled =
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PASTE_HAPTIC_ENABLED] ?: false,
            clipboardPasteFvStyleEnabled =
                prefs[SettingsPreferenceKeys.CLIPBOARD_PASTE_FV_STYLE_ENABLED] ?: false,
            clipboardFloatPasteSuccessCount =
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PASTE_SUCCESS_COUNT] ?: 0,
            clipboardFloatPasteFailCount =
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PASTE_FAIL_COUNT] ?: 0,
            clipboardFloatAlpha =
                (prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_ALPHA] ?: 1.0f).coerceIn(0.2f, 1.0f),
            clipboardFloatAutoDimWhenUnfocused =
                prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_AUTO_DIM_UNFOCUSED] ?: false,
            clipboardFloatAutoCloseSeconds =
                (prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_AUTO_CLOSE_SECONDS] ?: 0).coerceAtLeast(0),
            stashPanelBackgroundBlurEnabled =
                prefs[SettingsPreferenceKeys.STASH_PANEL_BACKGROUND_BLUR_ENABLED] ?: false,
            stashPanelBackgroundBlurRadiusDp = (
                prefs[SettingsPreferenceKeys.STASH_PANEL_BACKGROUND_BLUR_RADIUS_DP]
                    ?: AppSettings.STASH_PANEL_BLUR_RADIUS_DEFAULT_DP
                ).coerceIn(
                AppSettings.STASH_PANEL_BLUR_RADIUS_MIN_DP,
                AppSettings.STASH_PANEL_BLUR_RADIUS_MAX_DP,
            ),
            ),
            overlayBlur = OverlayBlurSettings(
                overlayBlurEnabled = prefs[SettingsPreferenceKeys.OVERLAY_BLUR_ENABLED] ?: true,
            ),
            defaultImageViewerPackage = prefs[SettingsPreferenceKeys.DEFAULT_IMAGE_VIEWER_PACKAGE],
            imageEditorDelayDeleteEnabled = prefs[SettingsPreferenceKeys.IMAGE_EDITOR_DELAY_DELETE_ENABLED] ?: false,
            searchPanel = SearchPanelSettings(
            searchEngines = readSearchEngines(prefs, context),
            searchEngineGridColumns = prefs[SettingsPreferenceKeys.SEARCH_ENGINE_GRID_COLUMNS]?.coerceIn(3, 8) ?: 5,
            searchEngineGridRows = prefs[SettingsPreferenceKeys.SEARCH_ENGINE_GRID_ROWS]?.coerceIn(1, 4) ?: 2,
            searchEngineShowLabels = prefs[SettingsPreferenceKeys.SEARCH_ENGINE_SHOW_LABELS] ?: true,
            searchPanelDefaultEngineId = prefs[SettingsPreferenceKeys.SEARCH_PANEL_DEFAULT_ENGINE_ID],
            searchPanelInputBehavior = prefs[SettingsPreferenceKeys.SEARCH_PANEL_INPUT_BEHAVIOR]
                ?.let { name -> runCatching { SearchPanelInputBehavior.valueOf(name) }.getOrNull() }
                ?: SearchPanelInputBehavior.KEEP,
            searchPanelEnterAction = prefs[SettingsPreferenceKeys.SEARCH_PANEL_ENTER_ACTION]
                ?.let { name -> runCatching { SearchPanelEnterAction.valueOf(name) }.getOrNull() }
                ?: SearchPanelEnterAction.SEARCH_ENGINE,
            searchPanelContactSearchEnabled = prefs[SettingsPreferenceKeys.SEARCH_PANEL_CONTACT_SEARCH_ENABLED] ?: true,
            searchPanelFileSearchEnabled = prefs[SettingsPreferenceKeys.SEARCH_PANEL_FILE_SEARCH_ENABLED] ?: true,
            searchPanelAppSearchEnabled = prefs[SettingsPreferenceKeys.SEARCH_PANEL_APP_SEARCH_ENABLED] ?: true,
            searchPanelSettingsSearchEnabled = prefs[SettingsPreferenceKeys.SEARCH_PANEL_SETTINGS_SEARCH_ENABLED] ?: true,
            searchPanelShortcutSearchEnabled = prefs[SettingsPreferenceKeys.SEARCH_PANEL_SHORTCUT_SEARCH_ENABLED] ?: true,
            searchPanelClipboardSearchEnabled = prefs[SettingsPreferenceKeys.SEARCH_PANEL_CLIPBOARD_SEARCH_ENABLED] ?: true,
            searchPanelFileTypesEnabled =
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_FILE_TYPES_ENABLED] ?: emptySet(),
            searchPanelFileShowFolders =
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_FILE_SHOW_FOLDERS] ?: false,
            searchPanelFileShowSystemFiles =
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_FILE_SHOW_SYSTEM] ?: false,
            searchPanelFilePreviewsEnabled =
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_FILE_PREVIEWS_ENABLED] ?: true,
            searchPanelFileFolderWhitelist =
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_FILE_FOLDER_WHITELIST] ?: emptySet(),
            searchPanelFileFolderBlacklist =
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_FILE_FOLDER_BLACKLIST] ?: emptySet(),
            searchPanelBarPosition = SearchPanelBarPosition.fromId(
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_BAR_POSITION],
            ),
            searchPanelListOrder = SearchPanelListOrder.fromPrefs(
                orderId = prefs[SettingsPreferenceKeys.SEARCH_PANEL_LIST_ORDER],
                oneHandedLegacy = prefs[SettingsPreferenceKeys.SEARCH_PANEL_ONE_HANDED_MODE],
            ),
            searchPanelAppDisplayStyle = SearchPanelAppDisplayStyle.fromId(
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_APP_DISPLAY_STYLE],
            ),
            searchPanelCalculatorEnabled = prefs[SettingsPreferenceKeys.SEARCH_PANEL_CALCULATOR_ENABLED] ?: true,
            searchPanelBackgroundStyle = SearchPanelBackgroundStyle.fromPrefs(
                styleId = prefs[SettingsPreferenceKeys.SEARCH_PANEL_BACKGROUND_STYLE],
                wallpaperBlurLegacy = prefs[SettingsPreferenceKeys.SEARCH_PANEL_WALLPAPER_BLUR_ENABLED],
            ),
            searchPanelBlurRadiusDp = (
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_BLUR_RADIUS_DP]
                    ?: AppSettings.SEARCH_PANEL_BLUR_RADIUS_DEFAULT_DP
                ).coerceIn(
                AppSettings.SEARCH_PANEL_BLUR_RADIUS_MIN_DP,
                AppSettings.SEARCH_PANEL_BLUR_RADIUS_MAX_DP,
            ),
            searchPanelDimPercent = (
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_DIM_PERCENT]
                    ?: AppSettings.SEARCH_PANEL_DIM_DEFAULT_PERCENT
                ).coerceIn(
                AppSettings.SEARCH_PANEL_DIM_MIN_PERCENT,
                AppSettings.SEARCH_PANEL_DIM_MAX_PERCENT,
            ),
            searchPanelWebSuggestionsEnabled =
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_WEB_SUGGESTIONS_ENABLED] ?: true,
            searchPanelWebSuggestionsCount = (
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_WEB_SUGGESTIONS_COUNT] ?: 5
                ).coerceIn(
                AppSettings.SEARCH_PANEL_WEB_SUGGESTIONS_COUNT_MIN,
                AppSettings.SEARCH_PANEL_WEB_SUGGESTIONS_COUNT_MAX,
            ),
            searchPanelHistoryMaxEntries = SearchPanelHistoryCapacity.coerce(
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_HISTORY_MAX_ENTRIES]
                    ?: SearchPanelHistoryCapacity.DEFAULT,
            ),
            searchPanelSectionAliases = SearchPanelSectionAliasSettings.fromJson(
                prefs[SettingsPreferenceKeys.SEARCH_PANEL_SECTION_ALIASES_JSON],
            ),
            aggregatedImageSearchEngines = readAggregatedImageSearchEngines(prefs),
            ),
        ).withResolvedHandleEdgeWidths()
    }

    private fun readAggregatedImageSearchEngines(prefs: Preferences): List<AggregatedImageSearchEngineConfig> {
        val initialized = prefs[SettingsPreferenceKeys.AGGREGATED_IMAGE_SEARCH_ENGINES_INITIALIZED] ?: false
        if (!initialized) return AppSettings.defaultAggregatedImageSearchEngines()
        return AggregatedImageSearchEnginePreferencesStore.decode(
            prefs[SettingsPreferenceKeys.AGGREGATED_IMAGE_SEARCH_ENGINES_JSON],
        )
    }

    private fun readSearchEngines(prefs: Preferences, context: Context): List<SearchEngineConfig> {
        val initialized = prefs[SettingsPreferenceKeys.SEARCH_ENGINES_INITIALIZED] ?: false
        if (!initialized) return SearchEngineCatalog.defaultEngines(context)
        return SearchEngineStore.decode(context, prefs[SettingsPreferenceKeys.SEARCH_ENGINES_JSON])
    }

    fun readFloatBallGestureActions(prefs: Preferences): Map<FloatBallGestureType, GestureAction> {
        val decoded = FloatBallGestureCodec.decodeAll(
            prefs[SettingsPreferenceKeys.FLOAT_BALL_GESTURE_ACTIONS] ?: emptySet(),
        )
        return decoded.ifEmpty { FloatBallGestureCodec.defaultActions() }
    }

    fun readShakeGestureSettings(prefs: Preferences): ShakeGestureSettings {
        val isV2Migrated = prefs[SettingsPreferenceKeys.SHAKE_SENSITIVITY_V2_MIGRATED] ?: false
        val isV3Migrated = prefs[SettingsPreferenceKeys.SHAKE_SENSITIVITY_V3_MIGRATED] ?: false
        val rawGlobal = prefs[SettingsPreferenceKeys.SHAKE_GLOBAL_SENSITIVITY]
        val rawPerDirection = if (isV2Migrated) {
            ShakeGestureCodec.decodePerDirectionSensitivity(
                prefs[SettingsPreferenceKeys.SHAKE_PER_DIRECTION_SENSITIVITY] ?: emptySet(),
            )
        } else {
            emptyMap()
        }
        return ShakeGestureSettings(
            enabled = prefs[SettingsPreferenceKeys.SHAKE_GESTURES_ENABLED] ?: false,
            basicActions = ShakeGestureCodec.decodeAllActions(prefs[SettingsPreferenceKeys.SHAKE_GESTURE_ACTIONS] ?: emptySet()),
            lockScreenShakeEnabled = prefs[SettingsPreferenceKeys.LOCK_SCREEN_SHAKE_ENABLED] ?: false,
            lockScreenActions = ShakeGestureCodec.decodeAllActions(prefs[SettingsPreferenceKeys.SHAKE_LOCK_SCREEN_ACTIONS] ?: emptySet()),
            independentAppShakeEnabled = prefs[SettingsPreferenceKeys.INDEPENDENT_APP_SHAKE_ENABLED] ?: false,
            perAppActions = ShakeGestureCodec.decodePerAppActions(prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] ?: emptySet()),
            globalSensitivity = readShakeSensitivityUi(
                rawUi = rawGlobal,
                isV2Migrated = isV2Migrated,
                isV3Migrated = isV3Migrated,
            ),
            independentSensitivityEnabled = if (isV2Migrated) {
                prefs[SettingsPreferenceKeys.SHAKE_INDEPENDENT_SENSITIVITY_ENABLED] ?: false
            } else {
                false
            },
            perDirectionSensitivity = rawPerDirection.mapValues { (_, value) ->
                readShakeSensitivityUi(
                    rawUi = value,
                    isV2Migrated = isV2Migrated,
                    isV3Migrated = isV3Migrated,
                )
            },
            vibrationFeedbackEnabled = prefs[SettingsPreferenceKeys.SHAKE_VIBRATION_FEEDBACK_ENABLED] ?: true,
            animationFeedbackEnabled = prefs[SettingsPreferenceKeys.SHAKE_ANIMATION_FEEDBACK_ENABLED] ?: false,
            animationColorArgb = prefs[SettingsPreferenceKeys.SHAKE_ANIMATION_COLOR] ?: 0xFF424242.toInt(),
            disableInLandscape = prefs[SettingsPreferenceKeys.SHAKE_DISABLE_IN_LANDSCAPE] ?: false,
            blacklistedPackages = prefs[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES] ?: emptySet(),
        )
    }

    fun readBackTapSettings(prefs: Preferences): BackTapSettings {
        val actionType = intPreference(prefs, SettingsPreferenceKeys.BACK_TAP_ACTION_TYPE, GestureActionType.NONE.id)
        val actionPayload = prefs[SettingsPreferenceKeys.BACK_TAP_ACTION_PAYLOAD].orEmpty()
        return BackTapSettings(
            enabled = prefs[SettingsPreferenceKeys.BACK_TAP_ENABLED] ?: false,
            sensitivity = intPreference(prefs, SettingsPreferenceKeys.BACK_TAP_SENSITIVITY, 5).coerceIn(1, 10),
            range = intPreference(prefs, SettingsPreferenceKeys.BACK_TAP_RANGE, 5).coerceIn(1, 10),
            mode = BackTapMode.fromId(intPreference(prefs, SettingsPreferenceKeys.BACK_TAP_MODE, 0)),
            pauseWhileCharging = prefs[SettingsPreferenceKeys.BACK_TAP_PAUSE_CHARGING] ?: false,
            vibrationFeedbackEnabled = prefs[SettingsPreferenceKeys.BACK_TAP_VIBRATION_FEEDBACK_ENABLED] ?: true,
            action = GestureAction.from(GestureActionType.fromId(actionType), actionPayload)
                .sanitizeForSlotPicker(SlotPickerKind.OverlayTap),
        )
    }

    fun readFaceDownGestureSettings(prefs: Preferences): FaceDownGestureSettings =
        FaceDownGestureSettings(
            enabled = prefs[SettingsPreferenceKeys.FACE_DOWN_GESTURE_ENABLED] ?: false,
            action = FaceDownGestureCodec.decodeAction(prefs[SettingsPreferenceKeys.FACE_DOWN_GESTURE_ACTION]),
            holdDurationMs = FaceDownGestureSettings.clampHoldDurationMs(
                prefs[SettingsPreferenceKeys.FACE_DOWN_HOLD_DURATION_MS] ?: 800L,
            ),
            requireProximity = prefs[SettingsPreferenceKeys.FACE_DOWN_REQUIRE_PROXIMITY] ?: false,
            cooldownMs = FaceDownGestureSettings.clampCooldownMs(
                prefs[SettingsPreferenceKeys.FACE_DOWN_COOLDOWN_MS] ?: 4_000L,
            ),
            disableInLandscape = prefs[SettingsPreferenceKeys.FACE_DOWN_DISABLE_IN_LANDSCAPE] ?: false,
            vibrationFeedbackEnabled = prefs[SettingsPreferenceKeys.FACE_DOWN_VIBRATION_FEEDBACK_ENABLED] ?: true,
            audioFeedbackEnabled = prefs[SettingsPreferenceKeys.FACE_DOWN_AUDIO_FEEDBACK_ENABLED] ?: true,
            audioFeedbackVolume = FaceDownGestureSettings.clampAudioFeedbackVolume(
                prefs[SettingsPreferenceKeys.FACE_DOWN_AUDIO_FEEDBACK_VOLUME]
                    ?: FaceDownGestureSettings.DEFAULT_AUDIO_FEEDBACK_VOLUME,
            ),
        )

    fun readCornerGestureSettings(prefs: Preferences): CornerGestureSettings {
        val inner = prefs[SettingsPreferenceKeys.CORNER_GESTURE_INNER_DIAMETER_DP] ?: 320f
        val legacyWidth = prefs[SettingsPreferenceKeys.CORNER_GESTURE_ZONE_WIDTH_DP]
        val legacyHeight = prefs[SettingsPreferenceKeys.CORNER_GESTURE_ZONE_HEIGHT_DP]
        // 轮盘层数：3 层是历史默认，越界值（坏存档/手改）在这里钳回来。
        val wheelLayerCount = CornerGestureSettings.clampWheelLayerCount(
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_WHEEL_LAYER_COUNT]
                ?: CornerRadialMenuCodec.BASE_LAYER_COUNT,
        )
        val bubbleSizeDp = CornerGestureSettings.clampBubbleSizeDp(
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_BUBBLE_SIZE_DP] ?: 24f,
        )
        // 环间距下限跟着气泡直径，历史/手改出来的过小值在这里抬回来。
        val ringSpacingDp = CornerGestureSettings.clampRingSpacingDp(
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_RING_SPACING_DP]
                ?: CornerGestureSettings.DEFAULT_RING_SPACING_DP,
            bubbleSizeDp,
        )
        return CornerGestureSettings(
            enabled = prefs[SettingsPreferenceKeys.CORNER_GESTURE_ENABLED] ?: false,
            leftEnabled = prefs[SettingsPreferenceKeys.CORNER_GESTURE_LEFT_ENABLED] ?: true,
            rightEnabled = prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_ENABLED] ?: true,
            verticalEdgeWidthDp = CornerGestureSettings.clampVerticalEdgeWidthDp(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_VERTICAL_EDGE_WIDTH_DP]
                    ?: legacyWidth
                    ?: 16f,
            ),
            verticalEdgeHeightDp = CornerGestureSettings.clampVerticalEdgeHeightDp(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_VERTICAL_EDGE_HEIGHT_DP]
                    ?: legacyHeight
                    ?: 147f,
            ),
            horizontalEdgeWidthDp = CornerGestureSettings.clampHorizontalEdgeWidthDp(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_HORIZONTAL_EDGE_WIDTH_DP]
                    ?: legacyWidth
                    ?: 98f,
            ),
            horizontalEdgeHeightDp = CornerGestureSettings.clampHorizontalEdgeHeightDp(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_HORIZONTAL_EDGE_HEIGHT_DP]
                    ?: legacyHeight
                    ?: 13f,
            ),
            triggerSlopDp = CornerGestureSettings.clampTriggerSlopDp(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_TRIGGER_SLOP_DP] ?: 40f,
            ),
            hideInLandscape = prefs[SettingsPreferenceKeys.CORNER_GESTURE_HIDE_LANDSCAPE] ?: true,
            landscapePreventFalseTouch = prefs[SettingsPreferenceKeys.CORNER_GESTURE_LANDSCAPE_PREVENT_FALSE_TOUCH]
                ?: true,
            overrideSystemNav = prefs[SettingsPreferenceKeys.CORNER_GESTURE_OVERRIDE_SYSTEM_NAV] ?: false,
            innerDiameterDp = CornerGestureSettings.clampInnerDiameterDp(inner),
            ringSpacingDp = ringSpacingDp,
            bubbleSizeDp = bubbleSizeDp,
            wheelLayerCount = wheelLayerCount,
            cancelOutsideWheel = prefs[SettingsPreferenceKeys.CORNER_GESTURE_CANCEL_OUTSIDE_WHEEL] ?: true,
            progressiveLayers = prefs[SettingsPreferenceKeys.CORNER_GESTURE_PROGRESSIVE_LAYERS] ?: false,
            slotHapticEnabled = prefs[SettingsPreferenceKeys.CORNER_GESTURE_SLOT_HAPTIC] ?: true,
            showSelectedName = prefs[SettingsPreferenceKeys.CORNER_GESTURE_SHOW_SELECTED_NAME] ?: true,
            showEditButton = prefs[SettingsPreferenceKeys.CORNER_GESTURE_SHOW_EDIT_BUTTON] ?: true,
            selectedHintIconSizeDp = SelectedHintMetrics.clampIconSizeDp(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_SELECTED_HINT_ICON_SIZE_DP]
                    ?: prefs[SettingsPreferenceKeys.SELECTED_HINT_ICON_SIZE_DP]
                    ?: SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
            ),
            backgroundStyle = CornerGestureSettings.clampBackgroundStyle(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_BACKGROUND_STYLE]
                    ?: if (prefs[SettingsPreferenceKeys.CORNER_GESTURE_WALLPAPER_BLUR_ENABLED] == true) {
                        CornerGestureSettings.BACKGROUND_BLUR
                    } else {
                        CornerGestureSettings.BACKGROUND_NONE
                    },
            ),
            blurDp = CornerGestureSettings.clampBlurDp(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_BLUR_DP] ?: CornerGestureSettings.DEFAULT_BLUR_DP,
            ),
            dimPercent = CornerGestureSettings.clampDimPercent(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_DIM_PERCENT]
                    ?: CornerGestureSettings.DEFAULT_DIM_PERCENT,
            ),
            unifiedSlots = prefs[SettingsPreferenceKeys.CORNER_GESTURE_UNIFIED_SLOTS] ?: true,
            innerZoneAction = CornerInnerZoneActionCodec.decode(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_INNER_ZONE_ACTION_PAYLOAD],
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_INNER_ZONE_ACTION_ID],
            ),
            leftSlots = CornerRadialMenuCodec.decode(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_LEFT_SLOTS] ?: emptySet(),
                CornerRadialMenuCodec.defaultLeftSlots(),
            ),
            rightSlots = CornerRadialMenuCodec.decode(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOTS] ?: emptySet(),
                CornerRadialMenuCodec.defaultRightSlots(),
            ),
            leftSlotSubMenus = CornerSlotSubMenuCodec.decode(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_LEFT_SLOT_SUB_MENUS] ?: emptySet(),
                CornerSlotSubMenuCodec.defaultSlotSubMenus(),
            ),
            rightSlotSubMenus = CornerSlotSubMenuCodec.decode(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOT_SUB_MENUS] ?: emptySet(),
                CornerSlotSubMenuCodec.defaultSlotSubMenus(),
            ),
        )
    }

    private fun readFloatingPointerSensitivityFraction(prefs: Preferences): Float {
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_SENSITIVITY]?.let { stored ->
            return stored.coerceIn(0.2f, 0.75f)
        }
        val legacyWidth = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_WIDTH] ?: 703f
        val legacyZoom = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_ZOOM] ?: 0.8f
        val legacyTravelPx = legacyWidth.coerceIn(120f, 800f) * legacyZoom.coerceIn(0.1f, 1f)
        val travelFraction =
            (legacyTravelPx / LEGACY_POINTER_TRAVEL_REFERENCE_WIDTH_PX).coerceIn(0.2f, 0.75f)
        return 0.2f + 0.75f - travelFraction
    }

    private fun readFloatingPointerJoystickLongPressAction(prefs: Preferences): com.slideindex.app.gesture.GestureAction {
        val encoded = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_LONG_PRESS_ACTION]
        if (encoded.isNullOrBlank()) return com.slideindex.app.gesture.GestureAction.OpenFloatingPointerRadialMenu
        return com.slideindex.app.launcher.QuickLauncherItemCodec.parseActionPayload(encoded)
            ?: com.slideindex.app.gesture.GestureAction.OpenFloatingPointerRadialMenu
    }

    private fun legacyLaunchPolicy(prefs: Preferences): Int {
        return if (prefs[SettingsPreferenceKeys.FREE_WINDOW_ENABLED] == true) {
            AppLaunchPolicy.ALWAYS_FREE_WINDOW.id
        } else {
            AppLaunchPolicy.ALWAYS_FULLSCREEN.id
        }
    }

    private fun readGestureAngleConfig(prefs: Preferences): GestureAngleConfig =
        GestureAngleConfig(
            upDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_UP] ?: GestureAngleConfig.DEFAULT_UP,
            upRightDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_UP_RIGHT] ?: GestureAngleConfig.DEFAULT_UP_RIGHT,
            inDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_IN] ?: GestureAngleConfig.DEFAULT_IN,
            downRightDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_DOWN_RIGHT] ?: GestureAngleConfig.DEFAULT_DOWN_RIGHT,
            downDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_DOWN] ?: GestureAngleConfig.DEFAULT_DOWN,
        ).normalized()

    private fun readMessageReminderSettings(prefs: Preferences): MessageSettings {
        val base = MessageSettings()
        val withGestures = MessageSettingsCodec.applyGestureActions(
            base,
            prefs[SettingsPreferenceKeys.MESSAGE_GESTURE_ACTIONS] ?: emptySet(),
        )
        val legacyStyleId = prefs[SettingsPreferenceKeys.MESSAGE_STYLE_ID]
            ?: MessageStyle.SideBubble.id
        val wasLegacyCard = legacyStyleId == "dark_card"
        val primaryStyleEnabled = prefs[SettingsPreferenceKeys.MESSAGE_PRIMARY_STYLE_ENABLED] ?: true
        val legacyThemeId = MessageThemeIds.normalizeThemeId(
            prefs[SettingsPreferenceKeys.MESSAGE_THEME_ID]
                ?: MessageThemeIds.defaultThemeIdFor(MessageStyle.SideBubble),
        )
        val legacyCardThemeId = prefs[SettingsPreferenceKeys.MESSAGE_CARD_THEME_ID]
        val floatIconEnabled = prefs[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_ENABLED]
            ?: (legacyStyleId == MessageStyle.FloatIcon.id)
        val sideBubbleEnabled = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_ENABLED]
            ?: (((legacyStyleId == MessageStyle.SideBubble.id || wasLegacyCard) && primaryStyleEnabled))
        val danmakuEnabled = prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_ENABLED] ?: true
        val cNoticeEnabled = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_ENABLED] ?: false
        // 总开关只认自己的键：提醒样式是否默认开启不再影响它（新装即为关）。
        val enabled = prefs[SettingsPreferenceKeys.MESSAGE_REMINDER_ENABLED] ?: false
        val hasInterceptKey = SettingsPreferenceKeys.MESSAGE_INTERCEPT_NOTIFICATIONS in prefs
        val interceptNotifications = if (hasInterceptKey) {
            prefs[SettingsPreferenceKeys.MESSAGE_INTERCEPT_NOTIFICATIONS] ?: false
        } else {
            // 老版本没有独立的拦截键时沿用总开关（迁移用），新装读 false。
            enabled
        }
        return withGestures.copy(
            enabled = enabled,
            interceptNotifications = interceptNotifications,
            styleId = legacyStyleId,
            primaryStyleEnabled = primaryStyleEnabled,
            floatIconEnabled = floatIconEnabled,
            sideBubbleEnabled = sideBubbleEnabled,
            danmakuEnabled = danmakuEnabled,
            cNoticeEnabled = cNoticeEnabled,
            themeId = legacyThemeId,
            sideThemeId = MessageThemeIds.normalizeThemeId(
                prefs[SettingsPreferenceKeys.MESSAGE_SIDE_THEME_ID]
                    ?: legacyCardThemeId
                    ?: legacyThemeId,
            ),
            danmakuThemeId = MessageThemeIds.normalizeThemeId(
                prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_THEME_ID] ?: base.danmakuThemeId,
            ),
            floatIconOpacity = prefs[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_OPACITY]
                ?: prefs[SettingsPreferenceKeys.MESSAGE_OPACITY]
                ?: base.floatIconOpacity,
            sideBubbleOpacity = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_OPACITY]
                ?: prefs[SettingsPreferenceKeys.MESSAGE_CARD_OPACITY]
                ?: prefs[SettingsPreferenceKeys.MESSAGE_OPACITY]
                ?: base.sideBubbleOpacity,
            danmakuOpacity = prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_OPACITY] ?: base.danmakuOpacity,
            cNoticeOpacity = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_OPACITY] ?: base.cNoticeOpacity,
            cNoticeDimmedOpacity = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_DIMMED_OPACITY]
                ?: base.cNoticeDimmedOpacity,
            cNoticeGhostOpacity = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_GHOST_OPACITY]
                ?: base.cNoticeGhostOpacity,
            danmakuMaxLines = prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_MAX_LINES] ?: base.danmakuMaxLines,
            sideMaxCount = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_COUNT] ?: base.sideMaxCount,
            sideMaxWidthDp = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_WIDTH_DP] ?: base.sideMaxWidthDp,
            sideMaxLines = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_LINES] ?: base.sideMaxLines,
            floatIconSizeDp = prefs[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_SIZE_DP] ?: base.floatIconSizeDp,
            cNoticeIconSizeDp = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_ICON_SIZE_DP]
                ?: base.cNoticeIconSizeDp,
            cNoticeMaxCount = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_MAX_COUNT] ?: base.cNoticeMaxCount,
            floatIconAutoDismissSeconds = resolveMessageAutoDismissSeconds(
                prefs = prefs,
                styleKey = SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_AUTO_DISMISS_SECONDS,
                fallback = base.floatIconAutoDismissSeconds,
            ),
            sideBubbleAutoDismissSeconds = resolveMessageAutoDismissSeconds(
                prefs = prefs,
                styleKey = SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_AUTO_DISMISS_SECONDS,
                fallback = base.sideBubbleAutoDismissSeconds,
            ),
            cNoticeAutoDismissSeconds = resolveMessageAutoDismissSeconds(
                prefs = prefs,
                styleKey = SettingsPreferenceKeys.MESSAGE_C_NOTICE_AUTO_DISMISS_SECONDS,
                fallback = base.cNoticeAutoDismissSeconds,
            ),
            hideInLandscape = prefs[SettingsPreferenceKeys.MESSAGE_HIDE_IN_LANDSCAPE] ?: false,
            portraitDanmaku = prefs[SettingsPreferenceKeys.MESSAGE_PORTRAIT_DANMAKU] ?: true,
            landscapeDanmaku = prefs[SettingsPreferenceKeys.MESSAGE_LANDSCAPE_DANMAKU] ?: true,
            sideBubbleHorizontalEdge = SideBubbleHorizontalEdge.fromId(
                prefs[SettingsPreferenceKeys.MESSAGE_SIDE_HORIZONTAL_EDGE],
            ),
            sideBubbleVerticalAnchor = SideBubbleVerticalAnchor.fromId(
                prefs[SettingsPreferenceKeys.MESSAGE_SIDE_VERTICAL_ANCHOR],
            ),
            sideBubbleYFraction = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_Y_FRACTION]
                ?: SideBubbleVerticalAnchor.defaultYFraction(
                    SideBubbleVerticalAnchor.fromId(
                        prefs[SettingsPreferenceKeys.MESSAGE_SIDE_VERTICAL_ANCHOR],
                    ),
                ),
            cNoticeHorizontalEdge = SideBubbleHorizontalEdge.fromId(
                prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_HORIZONTAL_EDGE],
            ),
            cNoticeYFraction = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_Y_FRACTION]
                ?: base.cNoticeYFraction,
            cNoticeEdgeMarginDp = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_EDGE_MARGIN_DP]
                ?: base.cNoticeEdgeMarginDp,
            cNoticeDimDelayMs = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_DIM_DELAY_MS]
                ?: base.cNoticeDimDelayMs,
            cNoticeGhostDelayMs = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_GHOST_DELAY_MS]
                ?: base.cNoticeGhostDelayMs,
            cNoticeDefaultCollapsed = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_DEFAULT_COLLAPSED]
                ?: base.cNoticeDefaultCollapsed,
            cNoticePeekBannerEnabled = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_PEEK_BANNER_ENABLED]
                ?: base.cNoticePeekBannerEnabled,
            cNoticeLandscapeEnabled = prefs[SettingsPreferenceKeys.MESSAGE_C_NOTICE_LANDSCAPE_ENABLED]
                ?: base.cNoticeLandscapeEnabled,
            floatIconCorner = MessageOverlayCorner.fromId(
                prefs[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_CORNER],
            ),
            floatIconYFraction = prefs[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_Y_FRACTION]
                ?: MessagePlacementFractions.DEFAULT_BOTTOM_Y,
            sideBubbleFontSizeLevel = SideBubbleFontSize.coerce(
                prefs[SettingsPreferenceKeys.MESSAGE_SIDE_FONT_SIZE_LEVEL] ?: SideBubbleFontSize.NORMAL,
            ),
            danmakuSpeedLevel = prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_SPEED_LEVEL]
                ?: DanmakuSpeed.NORMAL,
            enabledPackages = prefs[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES] ?: emptySet(),
            disabledPackages = prefs[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES] ?: emptySet(),
            dndPackages = prefs[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES] ?: emptySet(),
            suppressWhenSystemDnd = prefs[SettingsPreferenceKeys.MESSAGE_SUPPRESS_WHEN_SYSTEM_DND] ?: false,
            appFilterRules = MessageAppFilterCodec.decodeAll(
                prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet(),
            ),
            openLastMessageOnUnlock =
                prefs[SettingsPreferenceKeys.MESSAGE_OPEN_LAST_ON_UNLOCK] ?: false,
            openLastMessageAlwaysPackages =
                prefs[SettingsPreferenceKeys.MESSAGE_OPEN_LAST_ALWAYS_PACKAGES] ?: emptySet(),
            unlockConfirmationAutoDismissSeconds =
                (prefs[SettingsPreferenceKeys.MESSAGE_UNLOCK_CONFIRMATION_AUTO_DISMISS_SECONDS] ?: 3)
                    .coerceIn(0, 30),
            keepFloatIconAfterUnlock =
                prefs[SettingsPreferenceKeys.MESSAGE_KEEP_FLOAT_ICON_AFTER_UNLOCK] ?: false,
        )
    }

    private fun resolveOtpKeywordsRegex(stored: String?): String {
        if (stored == null) {
            return OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
        if (stored == OtpKeywords.LEGACY_DEFAULT_KEYWORDS_REGEX) {
            return OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
        return stored
    }

    private fun readQuickLauncherPanels(prefs: Preferences): List<com.slideindex.app.launcher.QuickLauncherPanel> {
        val encoded = com.slideindex.app.launcher.QuickLauncherPanelCodec.decodeAll(
            prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_PANELS] ?: emptySet(),
        )
        if (encoded.isNotEmpty()) return encoded
        val legacyItems = readLegacyQuickLauncherItems(prefs)
        val columns = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_COLUMNS_PER_PAGE] ?: 3
        val rows = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ROWS_PER_PAGE] ?: 4
        return com.slideindex.app.launcher.QuickLauncherPanelDefaults.migrateFromLegacyItems(
            items = legacyItems,
            columnsPerPage = columns,
            rowsPerPage = rows,
        )
    }

    private fun readLegacyQuickLauncherItems(prefs: Preferences): List<com.slideindex.app.launcher.QuickLauncherItem> {
        val unified = QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER] ?: emptySet())
        if (unified.isNotEmpty()) return unified
        val left = QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_LEFT] ?: emptySet())
        if (left.isNotEmpty()) return left
        return QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_RIGHT] ?: emptySet())
    }

    private fun readGestureRules(prefs: Preferences): List<GestureRule> {
        val raw = prefs[SettingsPreferenceKeys.GESTURE_RULES]
        if (raw == null || raw.isEmpty()) return SideGestureDefaults.defaultRules()
        val decoded = GestureRuleCodec.decodeAll(raw)
        return if (decoded.isEmpty()) SideGestureDefaults.defaultRules() else decoded
    }

    private fun readGestureRulesLandscape(prefs: Preferences): List<GestureRule> {
        val raw = prefs[SettingsPreferenceKeys.GESTURE_RULES_LANDSCAPE]
        if (raw == null || raw.isEmpty()) return SideGestureDefaults.defaultRules()
        val decoded = GestureRuleCodec.decodeAll(raw)
        return if (decoded.isEmpty()) SideGestureDefaults.defaultRules() else decoded
    }

    private fun readExcludedAppScopes(prefs: Preferences): Map<String, ExcludedAppScopes> {
        val decoded = ExcludedAppScopesCodec.decode(prefs[SettingsPreferenceKeys.EXCLUDED_APP_SCOPES])
        if (decoded.isNotEmpty()) return decoded
        val legacyPackages = prefs[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] ?: emptySet()
        if (legacyPackages.isEmpty()) return emptyMap()
        val defaults = readExcludedAppDefaultScopes(prefs)
        return legacyPackages.associateWith { defaults }
    }

    private fun readExcludedAppDefaultScopes(prefs: Preferences): ExcludedAppScopes {
        prefs[SettingsPreferenceKeys.EXCLUDED_APP_DEFAULT_SCOPES]?.let(::decodeDefaultScopesFlagString)?.let { return it }
        return ExcludedAppScopes(
            suppressTriggers = prefs[LEGACY_EXCLUDED_APP_SUPPRESS_TRIGGERS] ?: true,
            suppressCornerWheel = prefs[LEGACY_EXCLUDED_APP_SUPPRESS_CORNER_WHEEL] ?: true,
            suppressFloatBall = prefs[LEGACY_EXCLUDED_APP_SUPPRESS_FLOAT_BALL] ?: true,
        )
    }

    private fun decodeDefaultScopesFlagString(value: String): ExcludedAppScopes? {
        val flags = value.split(',')
        if (flags.size != 3) return null
        return ExcludedAppScopes(
            suppressTriggers = flags[0] == "1",
            suppressCornerWheel = flags[1] == "1",
            suppressFloatBall = flags[2] == "1",
        )
    }

    private val LEGACY_EXCLUDED_APP_SUPPRESS_TRIGGERS =
        booleanPreferencesKey("excluded_app_suppress_triggers")
    private val LEGACY_EXCLUDED_APP_SUPPRESS_CORNER_WHEEL =
        booleanPreferencesKey("excluded_app_suppress_corner_wheel")
    private val LEGACY_EXCLUDED_APP_SUPPRESS_FLOAT_BALL =
        booleanPreferencesKey("excluded_app_suppress_float_ball")

    private const val LEGACY_POINTER_TRAVEL_REFERENCE_WIDTH_PX = 1080f

    private fun decodeLandscapeHandles(
        raw: Set<String>?,
        defaultShortSwipe: Float,
        defaultLongSwipe: Float,
        legacyZeroMeansInherit: Boolean,
    ): List<TriggerHandle> {
        if (raw.isNullOrEmpty()) return emptyList()
        return TriggerHandleCodec.decodeAll(raw, defaultShortSwipe, defaultLongSwipe, legacyZeroMeansInherit)
    }

    private fun hasAnyLandscapeHandleStorage(prefs: Preferences): Boolean =
        !prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES_LANDSCAPE].isNullOrEmpty() ||
            !prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES_LANDSCAPE].isNullOrEmpty() ||
            !prefs[SettingsPreferenceKeys.BOTTOM_TRIGGER_HANDLES_LANDSCAPE].isNullOrEmpty() ||
            !prefs[SettingsPreferenceKeys.TOP_TRIGGER_HANDLES_LANDSCAPE].isNullOrEmpty() ||
            !prefs[SettingsPreferenceKeys.GESTURE_RULES_LANDSCAPE].isNullOrEmpty()

    private fun readExpandPanelSlotActions(prefs: Preferences): List<com.slideindex.app.gesture.GestureAction?> =
        com.slideindex.app.launcher.ExpandPanelSlotCodec.decode(
            prefs[SettingsPreferenceKeys.EXPAND_PANEL_SHORTCUTS],
        )

    private fun readShakeSensitivityUi(
        rawUi: Float?,
        isV2Migrated: Boolean,
        isV3Migrated: Boolean,
    ): Float {
        if (isV3Migrated) {
            return ShakeSensitivityScale.clampUi(rawUi ?: ShakeSensitivityScale.DEFAULT_UI)
        }
        val legacyUi = if (isV2Migrated) {
            rawUi ?: ShakeSensitivityScale.LEGACY_DEFAULT_UI
        } else {
            ShakeSensitivityScale.LEGACY_DEFAULT_UI
        }
        return ShakeSensitivityScale.migrateUiFromV2(legacyUi)
    }

    fun readFingertipRingSettings(prefs: Preferences): FingertipRingSettings {
        val slotCount = prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOT_COUNT]
            ?: FingertipRingCodec.DEFAULT_SLOT_COUNT
        return FingertipRingSettings(
            slotCount = slotCount,
            slotActions = FingertipRingCodec.decode(
                prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOTS] ?: emptySet(),
                slotCount,
            ),
            orbitRadiusPx = prefs[SettingsPreferenceKeys.FINGERTIP_RING_ORBIT_RADIUS_PX]
                ?: FingertipRingCodec.DEFAULT_ORBIT_RADIUS_PX,
            iconSizePx = prefs[SettingsPreferenceKeys.FINGERTIP_RING_ICON_SIZE_PX]
                ?: FingertipRingCodec.DEFAULT_ICON_SIZE_PX,
        )
    }

    private fun resolveMessageAutoDismissSeconds(
        prefs: Preferences,
        styleKey: Preferences.Key<Int>,
        fallback: Int,
    ): Int {
        val legacy = intPreference(
            prefs,
            intPreferencesKey(LEGACY_MESSAGE_AUTO_DISMISS_SECONDS_KEY),
            -1,
        ).takeIf { it >= 0 }
        return (prefs[styleKey] ?: legacy ?: fallback).coerceIn(0, 60)
    }

    private fun intPreference(prefs: Preferences, key: Preferences.Key<Int>, default: Int): Int {
        runCatching { prefs[key] }.getOrNull()?.let { return it }
        val raw = prefs.asMap().entries.firstOrNull { it.key.name == key.name }?.value ?: return default
        return when (raw) {
            is Int -> raw
            is Long -> raw.toInt()
            is Float -> raw.toInt()
            is Double -> raw.toInt()
            else -> default
        }
    }
}
