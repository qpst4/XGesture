package com.slideindex.app.settings

import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.otp.OtpCodeAlertPolicy
import com.slideindex.app.otp.OtpRecordLimits
import com.slideindex.app.otp.SmsBlacklistRuleSet
import com.slideindex.app.shake.FaceDownGestureSettings
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.widget.WidgetPanelPage

/** 首页主屏展示所需设置子集。 */
data class HomeMainSettings(
    val iconPlateModeId: Int = IconPlateMode.SYSTEM_DARK.id,
    val serviceEnabled: Boolean = false,
    val hideFromRecents: Boolean = false,
    val accessibilityKeepAliveEnabled: Boolean = false,
    val floatBallEnabled: Boolean = false,
    val floatBallSizeDp: Float = 48f,
    val floatBallOpacity: Float = 0.88f,
    val gestureHintEnabled: Boolean = true,
    val gestureHintStyleId: Int = GestureHintStyle.BUBBLE.id,
    val excludedAppScopes: Map<String, ExcludedAppScopes> = emptyMap(),
    val previousAppExcludedPackages: Set<String> = emptySet(),
    val hideTriggerInLandscape: Boolean = false,
    val hideTriggerOnLockScreen: Boolean = false,
    val hideTriggerOnLauncher: Boolean = false,
    val hapticEnabled: Boolean = true,
    val hapticStrengthLevel: Int = HapticStrength.MEDIUM.level,
    val hapticGestureTriggerEnabled: Boolean = true,
    val hapticLongThresholdEnabled: Boolean = true,
    val hapticShortThresholdEnabled: Boolean = true,
    val hapticCompoundTurnEnabled: Boolean = true,
    val hapticReturnSwipeEnabled: Boolean = true,
    val hapticHoverEnabled: Boolean = true,
    val hapticLongPressEnabled: Boolean = true,
    val hapticSlotLetterEnabled: Boolean = true,
    val hapticSlotAppEnabled: Boolean = true,
    val hapticActionConfirmEnabled: Boolean = true,
    val hapticSlotSelectionEnabled: Boolean = true,
    val hapticConfirmLaunchEnabled: Boolean = true,
    val dynamicColorEnabled: Boolean = true,
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val themePaletteStyleId: Int = ThemePaletteStyle.TONAL_SPOT.id,
    val themeModeId: Int = AppThemeMode.SYSTEM.id,
    val customColorEnabled: Boolean = false,
    val darkBackgroundStyleId: Int = DarkBackgroundStyle.QUIET_BLUE.id,
    val themeColorSpecId: Int = AppColorSpec.SPEC_2025.id,
    val uiDensityScale: Float = UiDensityScaleLimits.DEFAULT_SCALE,
    val bottomNavStyleId: Int = BottomNavStyle.FLOATING_NAV.id,
    val bottomNavModeId: Int = BottomNavMode.ICON_AND_TEXT.id,
    val bottomNavGlassEnabled: Boolean = true,
    val topAppBarBlurStyleId: Int = TopAppBarBlurStyle.PROGRESSIVE.id,
    val bottomNavClassicBlurRadiusDp: Float = BottomNavBlurDefaults.DEFAULT_RADIUS_DP,
    val bottomNavLiquidGlassBlurRadiusDp: Float = BottomNavBlurDefaults.LIQUID_GLASS_DEFAULT_RADIUS_DP,
    val bottomNavFloatingNavBlurRadiusDp: Float = BottomNavBlurDefaults.FLOATING_NAV_DEFAULT_RADIUS_DP,
    val predictiveBackEnabled: Boolean = false,
    val appUiLanguageTag: String = "",
    val swipeDismissEnabled: Boolean = true,
    /** 悬浮窗模糊总开关：关闭后所有浮层窗口/面板降级为实色底。 */
    val overlayBlurEnabled: Boolean = true,
) {
    val bottomNavBlurRadiusDp: Float
        get() = when (BottomNavStyle.fromId(bottomNavStyleId)) {
            BottomNavStyle.CLASSIC -> bottomNavClassicBlurRadiusDp
            BottomNavStyle.LIQUID_GLASS -> bottomNavLiquidGlassBlurRadiusDp
            BottomNavStyle.FLOATING_NAV -> bottomNavFloatingNavBlurRadiusDp
        }

    companion object {
        fun from(settings: AppSettings): HomeMainSettings = HomeMainSettings(
        iconPlateModeId = settings.iconPlateModeId,
            serviceEnabled = settings.serviceEnabled,
            hideFromRecents = settings.hideFromRecents,
            accessibilityKeepAliveEnabled = settings.accessibilityKeepAliveEnabled,
            floatBallEnabled = settings.floatBallEnabled,
            floatBallSizeDp = settings.floatBallSizeDp,
            floatBallOpacity = settings.floatBallOpacity,
            gestureHintEnabled = settings.gestureHintEnabled,
            gestureHintStyleId = settings.gestureHintStyleId,
            excludedAppScopes = settings.excludedAppScopes,
            previousAppExcludedPackages = settings.previousAppExcludedPackages,
            hideTriggerInLandscape = settings.hideTriggerInLandscape,
            hideTriggerOnLockScreen = settings.hideTriggerOnLockScreen,
            hideTriggerOnLauncher = settings.hideTriggerOnLauncher,
            hapticEnabled = settings.hapticEnabled,
            hapticStrengthLevel = settings.hapticStrengthLevel,
            hapticGestureTriggerEnabled = settings.hapticGestureTriggerEnabled,
            hapticLongThresholdEnabled = settings.hapticLongThresholdEnabled,
            hapticShortThresholdEnabled = settings.hapticShortThresholdEnabled,
            hapticCompoundTurnEnabled = settings.hapticCompoundTurnEnabled,
            hapticReturnSwipeEnabled = settings.hapticReturnSwipeEnabled,
            hapticHoverEnabled = settings.hapticHoverEnabled,
            hapticLongPressEnabled = settings.hapticLongPressEnabled,
            hapticSlotLetterEnabled = settings.hapticSlotLetterEnabled,
            hapticSlotAppEnabled = settings.hapticSlotAppEnabled,
            hapticActionConfirmEnabled = settings.hapticActionConfirmEnabled,
            hapticSlotSelectionEnabled = settings.hapticSlotSelectionEnabled,
            hapticConfirmLaunchEnabled = settings.hapticConfirmLaunchEnabled,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            themeColorArgb = settings.themeColorArgb,
            themePaletteStyleId = settings.themePaletteStyleId,
            themeModeId = settings.themeModeId,
            customColorEnabled = settings.customColorEnabled,
            darkBackgroundStyleId = settings.darkBackgroundStyleId,
            themeColorSpecId = settings.themeColorSpecId,
            uiDensityScale = settings.uiDensityScale,
            bottomNavStyleId = settings.bottomNavStyleId,
            bottomNavModeId = settings.bottomNavModeId,
            bottomNavGlassEnabled = settings.bottomNavGlassEnabled,
            topAppBarBlurStyleId = settings.topAppBarBlurStyleId,
            bottomNavClassicBlurRadiusDp = settings.bottomNavClassicBlurRadiusDp,
            bottomNavLiquidGlassBlurRadiusDp = settings.bottomNavLiquidGlassBlurRadiusDp,
            bottomNavFloatingNavBlurRadiusDp = settings.bottomNavFloatingNavBlurRadiusDp,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            appUiLanguageTag = settings.appUiLanguageTag,
            swipeDismissEnabled = settings.swipeDismissEnabled,
            overlayBlurEnabled = settings.overlayBlurEnabled,
        )
    }
}

/** 扩展 Hub 入口卡片所需设置子集。 */
data class ExtensionHubSettings(
    val appsPerRow: Int = 3,
    val quickLauncherPanelCount: Int = 0,
    val shellCommandCount: Int = 0,
    val activityShortcutCount: Int = 0,
    val honeycombLauncherCount: Int = 0,
    val widgetPanelPages: List<WidgetPanelPage> = emptyList(),
    val floatingPointerJoystickDiameterPx: Float = 275f,
    val floatingPointerPointerDiameterPx: Float = 100f,
    val floatingPointerSensitivityFraction: Float = 0.52f,
    val clipboardBackgroundMonitoring: Boolean = true,
    val clipboardBackgroundMonitoringMode: ClipboardMonitoringMode = ClipboardMonitoringMode.FOLLOW_PRIVILEGE,
    /**
     * 提权通道 / 采集方式 / 白名单也要带上：扩展 tab 的卡片靠它们判断"实际跑的是哪条通道"。
     *
     * 少了这三件时 [toMinimalAppSettings] 只能按默认的「跟随特权 + Shizuku」解析，
     * 选了 LSPosed 的机器上卡片会显示成 Shizuku（或"未就绪"），与暂存夹页不一致。
     */
    val clipboardMonitoringChannel: ClipboardMonitoringChannel = ClipboardMonitoringChannel.FOLLOW_PRIVILEGE,
    val clipboardMonitoringCapture: ClipboardMonitoringCapture = ClipboardMonitoringCapture.LOGCAT,
    val clipboardLsposedWhitelist: Set<String> = setOf(CLIPBOARD_LSPOSED_SELF_PACKAGE),
    val privilegeMode: PrivilegeMode = PrivilegeMode.SHIZUKU,
    /** 全息启动器选中列表的条目数（0 = 该模式下没有挑任何应用）。 */
    val holographicAppCount: Int = 0,
    /** 全息启动器的应用来源模式，用于 Hub 卡片副标题。 */
    val holographicAppModeId: Int = HolographicAppMode.DEFAULT.id,
    val fvRingLauncherConfiguredCount: Int = 0,
    val dragDropFlowEnabled: Boolean = false,
    val dragDropFlowPositionId: Int = DragDropFlowPosition.BOTTOM.id,
) {
    companion object {
        fun from(settings: AppSettings): ExtensionHubSettings = ExtensionHubSettings(
            appsPerRow = settings.appsPerRow,
            quickLauncherPanelCount = settings.quickLauncherPanels.size.coerceAtLeast(1),
            shellCommandCount = settings.shellCommands.size,
            activityShortcutCount = settings.activityShortcuts.size,
            honeycombLauncherCount = settings.honeycombLauncher.size,
            widgetPanelPages = settings.widgetPanelPages,
            floatingPointerJoystickDiameterPx = settings.floatingPointerJoystickDiameterPx,
            floatingPointerPointerDiameterPx = settings.floatingPointerPointerDiameterPx,
            floatingPointerSensitivityFraction = settings.floatingPointerSensitivityFraction,
            clipboardBackgroundMonitoring = settings.clipboardBackgroundMonitoring,
            clipboardBackgroundMonitoringMode = settings.clipboardBackgroundMonitoringMode,
            clipboardMonitoringChannel = settings.clipboardMonitoringChannel,
            clipboardMonitoringCapture = settings.clipboardMonitoringCapture,
            clipboardLsposedWhitelist = settings.clipboardLsposedWhitelist,
            privilegeMode = settings.privilegeMode,
            holographicAppCount = settings.holographicLauncher.selectedAppPackages.size,
            holographicAppModeId = settings.holographicLauncher.appMode.id,
            fvRingLauncherConfiguredCount = settings.launcher.fvRingLauncherVertical.configuredCount(),
            dragDropFlowEnabled = settings.dragDropFlowEnabled,
            dragDropFlowPositionId = settings.dragDropFlowPositionId,
        )
    }
}

data class KeepAliveUiSettings(
    val hideFromRecents: Boolean = false,
    val accessibilityKeepAliveEnabled: Boolean = false,
) {
    companion object {
        fun from(settings: AppSettings): KeepAliveUiSettings = KeepAliveUiSettings(
            hideFromRecents = settings.hideFromRecents,
            accessibilityKeepAliveEnabled = settings.accessibilityKeepAliveEnabled,
        )
    }
}

data class ShakeUiSettings(
    val shakeGestureSettings: ShakeGestureSettings = ShakeGestureSettings(),
    val faceDownGestureSettings: FaceDownGestureSettings = FaceDownGestureSettings(),
) {
    companion object {
        fun from(settings: AppSettings): ShakeUiSettings = ShakeUiSettings(
            shakeGestureSettings = settings.shakeGestureSettings,
            faceDownGestureSettings = settings.faceDownGestureSettings,
        )
    }
}

data class FreeWindowUiSettings(
    val freeWindowEnabled: Boolean = false,
    val freeWindowModeId: Int = FreeWindowMode.detectDefault().id,
    val freeWindowWidthFraction: Float = 0.8f,
    val freeWindowHeightFraction: Float = 0.55f,
    val freeWindowLeftFraction: Float = 0.1f,
    val freeWindowTopFraction: Float = 0.15f,
    val freeWindowFollowTriggerSide: Boolean = false,
    val freeWindowLandWidthFraction: Float = 0.68f,
    val freeWindowLandHeightFraction: Float = 0.4f,
    val freeWindowLandLeftFraction: Float = 0.16f,
    val freeWindowLandTopFraction: Float = 0.05f,
    val appLaunchPolicyId: Int = AppLaunchPolicy.ALWAYS_FULLSCREEN.id,
    val longPressLaunchDurationMs: Int = 450,
) {
    companion object {
        fun from(settings: AppSettings): FreeWindowUiSettings = FreeWindowUiSettings(
            freeWindowEnabled = settings.freeWindowEnabled,
            freeWindowModeId = settings.freeWindowModeId,
            freeWindowWidthFraction = settings.freeWindowWidthFraction,
            freeWindowHeightFraction = settings.freeWindowHeightFraction,
            freeWindowLeftFraction = settings.freeWindowLeftFraction,
            freeWindowTopFraction = settings.freeWindowTopFraction,
            freeWindowFollowTriggerSide = settings.freeWindowFollowTriggerSide,
            freeWindowLandWidthFraction = settings.freeWindowLandWidthFraction,
            freeWindowLandHeightFraction = settings.freeWindowLandHeightFraction,
            freeWindowLandLeftFraction = settings.freeWindowLandLeftFraction,
            freeWindowLandTopFraction = settings.freeWindowLandTopFraction,
            appLaunchPolicyId = settings.appLaunchPolicyId,
            longPressLaunchDurationMs = settings.longPressLaunchDurationMs,
        )
    }
}

data class OtpUiSettings(
    val otpCopyToClipboard: Boolean = false,
    val otpKeywordsRegex: String = OtpKeywords.DEFAULT_KEYWORDS_REGEX,
    val otpUserMatchRules: List<com.slideindex.app.otp.OtpMatchRule> = emptyList(),
    val otpDisabledOfficialRuleIds: Set<String> = emptySet(),
    val otpAutoInputEnabled: Boolean = false,
    val otpAutoConfirmEnabled: Boolean = false,
    val otpAutoInputDelayMs: Int = 0,
    val otpAutoInputIntervalMs: Int = 0,
    val otpLsposedSmsCaptureEnabled: Boolean = false,
    val otpLsposedSystemInjectEnabled: Boolean = true,
    val otpCodeNotificationEnabled: Boolean = false,
    val otpCodeNotificationRetentionSeconds: Int = OtpCodeAlertPolicy.DEFAULT_RETENTION_SECONDS,
    val otpShowCodeToast: Boolean = false,
    val otpSmsBlacklist: SmsBlacklistRuleSet = SmsBlacklistRuleSet.DISABLED,
    val otpBlockCodeSmsEnabled: Boolean = false,
    val otpMarkSmsReadEnabled: Boolean = false,
    val otpDeleteSmsAfterExtractEnabled: Boolean = false,
    val otpRecordCodeEnabled: Boolean = true,
    val otpRecordPlainSmsEnabled: Boolean = false,
    val otpRecordAppNotifyEnabled: Boolean = true,
    val otpRecordCodeLimit: Int = OtpRecordLimits.DEFAULT,
    val otpRecordPlainSmsLimit: Int = OtpRecordLimits.DEFAULT,
    val otpRecordAppNotifyLimit: Int = OtpRecordLimits.DEFAULT,
    val otpBlockedPackages: Set<String> = emptySet(),
) {
    companion object {
        fun from(settings: AppSettings): OtpUiSettings = OtpUiSettings(
            otpCopyToClipboard = settings.otpCopyToClipboard,
            otpKeywordsRegex = settings.otpKeywordsRegex,
            otpUserMatchRules = settings.otpUserMatchRules,
            otpDisabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
            otpAutoInputEnabled = settings.otpAutoInputEnabled,
            otpAutoConfirmEnabled = settings.otpAutoConfirmEnabled,
            otpAutoInputDelayMs = settings.otpAutoInputDelayMs,
            otpAutoInputIntervalMs = settings.otpAutoInputIntervalMs,
            otpLsposedSmsCaptureEnabled = settings.otpLsposedSmsCaptureEnabled,
            otpLsposedSystemInjectEnabled = settings.otpLsposedSystemInjectEnabled,
            otpCodeNotificationEnabled = settings.otpCodeNotificationEnabled,
            otpCodeNotificationRetentionSeconds = settings.otpCodeNotificationRetentionSeconds,
            otpShowCodeToast = settings.otpShowCodeToast,
            otpSmsBlacklist = settings.otpSmsBlacklist,
            otpBlockCodeSmsEnabled = settings.otpBlockCodeSmsEnabled,
            otpMarkSmsReadEnabled = settings.otpMarkSmsReadEnabled,
            otpDeleteSmsAfterExtractEnabled = settings.otpDeleteSmsAfterExtractEnabled,
            otpRecordCodeEnabled = settings.otpRecordCodeEnabled,
            otpRecordPlainSmsEnabled = settings.otpRecordPlainSmsEnabled,
            otpRecordAppNotifyEnabled = settings.otpRecordAppNotifyEnabled,
            otpRecordCodeLimit = settings.otpRecordCodeLimit,
            otpRecordPlainSmsLimit = settings.otpRecordPlainSmsLimit,
            otpRecordAppNotifyLimit = settings.otpRecordAppNotifyLimit,
            otpBlockedPackages = settings.otpBlockedPackages,
        )
    }
}

fun HomeMainSettings.gestureHintStyle(): GestureHintStyle = GestureHintStyle.fromId(gestureHintStyleId)
