package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.PredictiveBackStartupStore
import com.slideindex.app.util.ServiceEnabledStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editor: SettingsPreferencesEditor,
    private val backupManager: SettingsBackupManager,
    private val edge: EdgeSettingsMutator,
    private val overlay: OverlaySettingsMutator,
    private val shake: ShakeSettingsMutator,
    private val message: MessageSettingsMutator,
    private val otp: OtpSettingsMutator,
) {
    @Volatile
    private var cachedSettings: AppSettings = AppSettings()

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: Flow<AppSettings> = editor.settings

    val themeSettings: Flow<ThemeSettings> = editor.themeSettings

    val appRootSettings: Flow<AppRootSettings> = editor.appRootSettings

    val gestureSettings: Flow<GestureSettings> = editor.gestureSettings

    val overlaySettings: Flow<OverlaySettings> = editor.overlaySettings

    val homeMainSettings: Flow<HomeMainSettings> = editor.homeMainSettings

    val extensionHubSettings: Flow<ExtensionHubSettings> = editor.extensionHubSettings

    val keepAliveUiSettings: Flow<KeepAliveUiSettings> = editor.keepAliveUiSettings

    val shakeUiSettings: Flow<ShakeUiSettings> = editor.shakeUiSettings

    val freeWindowUiSettings: Flow<FreeWindowUiSettings> = editor.freeWindowUiSettings

    val otpUiSettings: Flow<OtpUiSettings> = editor.otpUiSettings

    val messageReminderSettings: Flow<MessageSettings> = editor.messageReminderSettings

    init {
        cacheScope.launch {
            // 修复动作失败不能拖死快照收集：否则 readSnapshot() 永远停在默认值
            // （历史上表现为 overlay 进程按默认手势动作执行，例如短滑=返回）。
            runCatching {
                editor.cleanupLegacyClipboardKeysOnce()
                overlay.migrateFvRingLauncherPreferenceKeysOnce()
                overlay.migrateFloatBallInstantTranslateDefaultOnOnce()
                edge.persistOppositeGestureSlotRepairIfNeeded()
            }.onFailure {
                android.util.Log.w(
                    "SettingsRepository",
                    "repair failed (${com.slideindex.app.util.AppProcess.name()})",
                    it,
                )
            }
            runCatching {
                settings.collect { snapshot ->
                    cachedSettings = snapshot
                }
            }.onFailure {
                android.util.Log.e(
                    "SettingsRepository",
                    "collect failed (${com.slideindex.app.util.AppProcess.name()})",
                    it,
                )
            }
        }
        cacheScope.launch {
            settings
                .map { it.serviceEnabled }
                .distinctUntilChanged()
                .collect { enabled -> ServiceEnabledStore.write(context, enabled) }
        }
        // §0.16.25：预测性返回开关也落一个同步镜像：它在 Application.attachBaseContext
        // 就要被读到（用来写 ApplicationInfo 的 FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK），
        // 而那个时点**不能**同步读 DataStore。详见 PredictiveBackStartupStore 的 KDoc。
        cacheScope.launch {
            settings
                .map { it.predictiveBackEnabled }
                .distinctUntilChanged()
                .collect { enabled -> PredictiveBackStartupStore.write(context, enabled) }
        }
        // 圆环槽位面板引用的一次性归一化：只碰自己的键，不阻塞快照收集（上面那批修复会先写盘，
        // 若把这条也串进去会推迟 collect 启动，readSnapshot() 读到旧值的窗口跟着变大）。
        cacheScope.launch {
            runCatching {
                overlay.migrateFvRingLauncherQuickLauncherPanelsOnce()
            }.onFailure {
                android.util.Log.w(
                    "SettingsRepository",
                    "fv panel migration failed (${com.slideindex.app.util.AppProcess.name()})",
                    it,
                )
            }
        }
    }

    fun readSnapshot(): AppSettings = cachedSettings

    suspend fun readFreshSnapshot(): AppSettings =
        SettingsSnapshotReader.read(editor.readRawPreferences(), context)

    suspend fun exportSettings(
        appVersionName: String,
        sensitive: SensitiveBackupSections? = null,
        outputStream: java.io.OutputStream,
    ): Result<Unit> =
        backupManager.exportToZip(appVersionName, sensitive, outputStream)

    suspend fun importSettings(
        inputStream: java.io.InputStream,
        replaceExisting: Boolean = true,
    ): Result<SettingsBackupImportResult> =
        backupManager.importFromZip(inputStream, replaceExisting)
        
    suspend fun previewImport(
        inputStream: java.io.InputStream,
    ): Result<SettingsBackupPreview> =
        backupManager.previewZipImport(inputStream)



    suspend fun setOnboardingCompleted(completed: Boolean) = edge.setOnboardingCompleted(completed)

    suspend fun setServiceEnabled(enabled: Boolean) = edge.setServiceEnabled(enabled)
    suspend fun setLeftEdgeEnabled(enabled: Boolean) = edge.setLeftEdgeEnabled(enabled)
    suspend fun setRightEdgeEnabled(enabled: Boolean) = edge.setRightEdgeEnabled(enabled)
    suspend fun setEdgeTriggerWidthDp(side: PanelSide, value: Float) = edge.setEdgeTriggerWidthDp(side, value)
    suspend fun setTriggerEdgeWidthDp(side: PanelSide, handleId: String, value: Float, landscape: Boolean = false) =
        edge.setTriggerEdgeWidthDp(side, handleId, value, landscape)
    suspend fun setTriggerTopFraction(side: PanelSide, value: Float) = edge.setTriggerTopFraction(side, value)
    suspend fun setTriggerHeightFraction(side: PanelSide, value: Float) = edge.setTriggerHeightFraction(side, value)
    suspend fun setTriggerVerticalRange(
        side: PanelSide,
        handleId: String,
        topFraction: Float,
        bottomFraction: Float,
        landscape: Boolean = false,
    ) = edge.setTriggerVerticalRange(side, handleId, topFraction, bottomFraction, landscape)
    suspend fun setTriggerHandleEnabled(
        side: PanelSide,
        handleId: String,
        enabled: Boolean,
        landscape: Boolean = false,
    ) = edge.setTriggerHandleEnabled(side, handleId, enabled, landscape)
    suspend fun addBottomTriggerHandle(landscape: Boolean = false) = edge.addBottomTriggerHandle(landscape)
    suspend fun addTopTriggerHandle(landscape: Boolean = false) = edge.addTopTriggerHandle(landscape)
    suspend fun addTriggerHandlePair(landscape: Boolean = false) = edge.addTriggerHandlePair(landscape)
    suspend fun removeTriggerHandle(side: PanelSide, handleId: String, landscape: Boolean = false) =
        edge.removeTriggerHandle(side, handleId, landscape)
    suspend fun ensureLandscapeTriggerHandlesInitialized() = edge.ensureLandscapeTriggerHandlesInitialized()
    suspend fun copyPortraitToLandscape() = edge.copyPortraitToLandscape()
    suspend fun setTriggerAlignOppositeSide(
        handleId: String,
        sourceSide: PanelSide,
        enabled: Boolean,
        landscape: Boolean = false,
    ) = edge.setTriggerAlignOppositeSide(handleId, sourceSide, enabled, landscape)
    suspend fun setTriggerAlignOppositeDesign(
        handleId: String,
        sourceSide: PanelSide,
        enabled: Boolean,
        landscape: Boolean = false,
    ) = edge.setTriggerAlignOppositeDesign(handleId, sourceSide, enabled, landscape)
    suspend fun setTriggerAlignOppositeGestures(
        handleId: String,
        sourceSide: PanelSide,
        enabled: Boolean,
        landscape: Boolean = false,
    ) = edge.setTriggerAlignOppositeGestures(handleId, sourceSide, enabled, landscape)
    suspend fun setTriggerHandleDesign(
        side: PanelSide,
        handleId: String,
        design: TriggerHandleDesign,
        landscape: Boolean = false,
    ) = edge.setTriggerHandleDesign(side, handleId, design, landscape)
    suspend fun applyTriggerDesignPreset(
        side: PanelSide,
        handleId: String,
        preset: TriggerDesignPreset,
        landscape: Boolean = false,
    ) = edge.applyTriggerDesignPreset(side, handleId, preset, landscape)
    suspend fun setInterceptSystemBackGesture(enabled: Boolean) = edge.setInterceptSystemBackGesture(enabled)

    suspend fun setSystemGestureTakeoverTop(enabled: Boolean) = edge.setSystemGestureTakeoverTop(enabled)

    suspend fun setSystemGestureTakeoverSides(enabled: Boolean) = edge.setSystemGestureTakeoverSides(enabled)

    suspend fun setSystemGestureTakeoverBottom(enabled: Boolean) = edge.setSystemGestureTakeoverBottom(enabled)
    suspend fun setLimitMaxInterceptLength(enabled: Boolean) = edge.setLimitMaxInterceptLength(enabled)
    suspend fun setDefaultTriggerMode(
        side: PanelSide,
        mode: GestureTriggerMode,
        handleId: String = TriggerHandle.DEFAULT_ID,
        landscape: Boolean = false,
    ) = edge.setDefaultTriggerMode(side, mode, handleId, landscape)
    suspend fun setShortSwipeDistanceDp(
        side: PanelSide,
        handleId: String,
        value: Float,
        landscape: Boolean = false,
    ) = edge.setShortSwipeDistanceDp(side, handleId, value, landscape)
    suspend fun setLongSwipeDistanceDp(
        side: PanelSide,
        handleId: String,
        value: Float,
        landscape: Boolean = false,
    ) = edge.setLongSwipeDistanceDp(side, handleId, value, landscape)
    suspend fun setGestureHintEnabled(enabled: Boolean) = edge.setGestureHintEnabled(enabled)
    suspend fun setGestureHintStyle(style: GestureHintStyle) = edge.setGestureHintStyle(style)
    suspend fun setGestureHintFingerOffsetDp(value: Float) = edge.setGestureHintFingerOffsetDp(value)
    suspend fun setAndroidBackColorSource(source: AndroidBackColorSource) =
        edge.setAndroidBackColorSource(source)
    suspend fun setSwipeHoverDurationMs(value: Int) = edge.setSwipeHoverDurationMs(value)

    suspend fun setTriggerDoubleTapIntervalMs(value: Int) = edge.setTriggerDoubleTapIntervalMs(value)
    suspend fun setAnimationStyles(styles: AnimationStyles) = edge.setAnimationStyles(styles)
    suspend fun updateWaveStyle(style: WaveStyle) = edge.updateWaveStyle(style)
    suspend fun updateCapsuleStyle(style: CapsuleStyle) = edge.updateCapsuleStyle(style)
    suspend fun updateBubbleStyle(style: BubbleStyle) = edge.updateBubbleStyle(style)
    suspend fun setGestureAngles(angles: GestureAngles) = edge.setGestureAngles(angles)
    suspend fun setIndexHeightFraction(value: Float) = edge.setIndexHeightFraction(value)
    suspend fun setHideEmptyIndexLetters(enabled: Boolean) = edge.setHideEmptyIndexLetters(enabled)
    suspend fun setAppsPerRow(value: Int) = edge.setAppsPerRow(value)
    suspend fun setQuickLauncherColumnsPerPage(value: Int) = edge.setQuickLauncherColumnsPerPage(value)
    suspend fun setQuickLauncherRowsPerPage(value: Int) = edge.setQuickLauncherRowsPerPage(value)
    suspend fun setPanelOpacity(value: Float) = edge.setPanelOpacity(value)
    suspend fun setHapticEnabled(enabled: Boolean) = edge.setHapticEnabled(enabled)
    suspend fun setHideFromRecents(enabled: Boolean) = edge.setHideFromRecents(enabled)

    suspend fun setPrivilegeMode(mode: PrivilegeMode): Result<Unit> =
        editor.edit { prefs ->
            prefs[SettingsPreferenceKeys.PRIVILEGE_MODE] = mode.storageValue
        }
    suspend fun setPredictiveBackEnabled(enabled: Boolean) = edge.setPredictiveBackEnabled(enabled)
    suspend fun setAppUiLanguage(language: AppUiLanguage) = edge.setAppUiLanguage(language)
    suspend fun setSwipeDismissEnabled(enabled: Boolean) = edge.setSwipeDismissEnabled(enabled)
    suspend fun setAccessibilityKeepAliveEnabled(enabled: Boolean) = edge.setAccessibilityKeepAliveEnabled(enabled)
    suspend fun setHapticStrengthLevel(level: Int) = edge.setHapticStrengthLevel(level)
    suspend fun setHapticGestureTriggerEnabled(enabled: Boolean) = edge.setHapticGestureTriggerEnabled(enabled)
    suspend fun setHapticSlotSelectionEnabled(enabled: Boolean) = edge.setHapticSlotSelectionEnabled(enabled)
    suspend fun setHapticConfirmLaunchEnabled(enabled: Boolean) = edge.setHapticConfirmLaunchEnabled(enabled)
    suspend fun addHiddenApp(packageName: String) = edge.addHiddenApp(packageName)
    suspend fun addFreezerApp(packageName: String) = edge.addFreezerApp(packageName)
    suspend fun removeFreezerApp(packageName: String) = edge.removeFreezerApp(packageName)
    suspend fun setFreezerShowInLauncher(enabled: Boolean) = edge.setFreezerShowInLauncher(enabled)
    suspend fun setFreezerWorkMode(modeId: Int) = edge.setFreezerWorkMode(modeId)
    suspend fun setFreezerAppPackages(packages: Set<String>) = edge.setFreezerAppPackages(packages)
    suspend fun setFreezerAppIntent(packageName: String, intent: FreezerAppIntent?) =
        edge.setFreezerAppIntent(packageName, intent)
    suspend fun clearFreezerAppIntents(packageNames: Set<String>) =
        edge.clearFreezerAppIntents(packageNames)
    suspend fun setExpandPanelSlotAction(index: Int, action: com.slideindex.app.gesture.GestureAction?) =
        edge.setExpandPanelSlotAction(index, action)
    suspend fun removeHiddenApp(packageName: String) = edge.removeHiddenApp(packageName)
    suspend fun addPreviousAppExcludedPackage(packageName: String) =
        edge.addPreviousAppExcludedPackage(packageName)
    suspend fun removePreviousAppExcludedPackage(packageName: String) =
        edge.removePreviousAppExcludedPackage(packageName)
    suspend fun addExcludedTriggerApp(packageName: String) = edge.addExcludedTriggerApp(packageName)
    suspend fun removeExcludedTriggerApp(packageName: String) = edge.removeExcludedTriggerApp(packageName)
    suspend fun setExcludedAppSuppressTriggers(enabled: Boolean) = edge.setExcludedAppDefaultSuppressTriggers(enabled)
    suspend fun setExcludedAppSuppressCornerWheel(enabled: Boolean) = edge.setExcludedAppDefaultSuppressCornerWheel(enabled)
    suspend fun setExcludedAppSuppressFloatBall(enabled: Boolean) = edge.setExcludedAppDefaultSuppressFloatBall(enabled)
    suspend fun setExcludedAppScopes(packageName: String, scopes: ExcludedAppScopes) =
        edge.setExcludedAppScopes(packageName, scopes)
    suspend fun setKeyboardTriggerBehaviorPortrait(behavior: KeyboardTriggerBehavior) =
        edge.setKeyboardTriggerBehaviorPortrait(behavior)

    suspend fun setKeyboardTriggerBehaviorLandscape(behavior: KeyboardTriggerBehavior) =
        edge.setKeyboardTriggerBehaviorLandscape(behavior)

    suspend fun setKeyboardTriggerNarrowPercentPortrait(percent: Int) =
        edge.setKeyboardTriggerNarrowPercentPortrait(percent)

    suspend fun setKeyboardTriggerNarrowPercentLandscape(percent: Int) =
        edge.setKeyboardTriggerNarrowPercentLandscape(percent)

    suspend fun setHideTriggerInLandscape(enabled: Boolean) = edge.setHideTriggerInLandscape(enabled)
    suspend fun setHideTriggerOnLockScreen(enabled: Boolean) = edge.setHideTriggerOnLockScreen(enabled)
    suspend fun setHideTriggerOnLauncher(enabled: Boolean) = edge.setHideTriggerOnLauncher(enabled)
    suspend fun upsertGestureRule(rule: GestureRule) = edge.upsertGestureRule(rule)
    suspend fun removeGestureRule(id: String) = edge.removeGestureRule(id)
    suspend fun setSlotAction(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        landscape: Boolean = false,
    ) = edge.setSlotAction(side, trigger, action, landscape)
    suspend fun setSlotTriggerMode(
        side: PanelSide,
        trigger: GestureTriggerType,
        triggerMode: GestureTriggerMode,
        landscape: Boolean = false,
    ) = edge.setSlotTriggerMode(side, trigger, triggerMode, landscape)
    suspend fun setSlotConfig(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        triggerMode: GestureTriggerMode,
        handleId: String = TriggerHandle.DEFAULT_ID,
        landscape: Boolean = false,
    ) = edge.setSlotConfig(side, trigger, action, triggerMode, handleId, landscape)

    suspend fun setThemeColor(argb: Int) = overlay.setThemeColor(argb)
    suspend fun setDynamicColorEnabled(enabled: Boolean) = overlay.setDynamicColorEnabled(enabled)
    suspend fun setThemePaletteStyle(style: ThemePaletteStyle) = overlay.setThemePaletteStyle(style)
    suspend fun setThemeMode(mode: AppThemeMode) = overlay.setThemeMode(mode)
    suspend fun setCustomColorEnabled(enabled: Boolean) = overlay.setCustomColorEnabled(enabled)
    suspend fun setDarkBackgroundStyle(style: DarkBackgroundStyle) = overlay.setDarkBackgroundStyle(style)
    suspend fun setThemeColorSpec(spec: AppColorSpec) = overlay.setThemeColorSpec(spec)
    suspend fun setBottomNavStyle(style: BottomNavStyle) = overlay.setBottomNavStyle(style)
    suspend fun setBottomNavMode(mode: BottomNavMode) = overlay.setBottomNavMode(mode)
    suspend fun setBottomNavGlassEnabled(enabled: Boolean) = overlay.setBottomNavGlassEnabled(enabled)
    suspend fun setTopAppBarBlurStyle(style: TopAppBarBlurStyle) = overlay.setTopAppBarBlurStyle(style)
    suspend fun setUiDensityScale(scale: Float) = overlay.setUiDensityScale(scale)

    suspend fun setBottomNavBlurRadiusDp(value: Float) =
        overlay.setBottomNavBlurRadiusDp(value)
    suspend fun setFreeWindowEnabled(enabled: Boolean) = overlay.setFreeWindowEnabled(enabled)
    suspend fun setFreeWindowModeId(id: Int) = overlay.setFreeWindowModeId(id)
    suspend fun setFreeWindowLayout(
        portrait: FreeWindowLayoutFractions,
        landscape: FreeWindowLayoutFractions,
    ) = overlay.setFreeWindowLayout(portrait = portrait, landscape = landscape)
    suspend fun setAppLaunchPolicyId(id: Int) = overlay.setAppLaunchPolicyId(id)
    suspend fun setLongPressLaunchDurationMs(value: Int) = overlay.setLongPressLaunchDurationMs(value)
    suspend fun setFloatingPointerSensitivityFraction(value: Float) =
        overlay.setFloatingPointerSensitivityFraction(value)
    suspend fun setFloatingPointerJoystickDiameterPx(value: Float) = overlay.setFloatingPointerJoystickDiameterPx(value)
    suspend fun setFloatingPointerPointerDiameterPx(value: Float) = overlay.setFloatingPointerPointerDiameterPx(value)
    suspend fun setFloatingPointerDesignId(designId: String) = overlay.setFloatingPointerDesignId(designId)
    suspend fun setFloatingPointerRingThicknessPx(value: Float) = overlay.setFloatingPointerRingThicknessPx(value)
    suspend fun setFloatingPointerDotDiameterPx(value: Float) = overlay.setFloatingPointerDotDiameterPx(value)
    suspend fun setFloatingPointerRingColor(argb: Int) = overlay.setFloatingPointerRingColor(argb)
    suspend fun setFloatingPointerFillColor(argb: Int) = overlay.setFloatingPointerFillColor(argb)
    suspend fun setFloatingPointerDotColor(argb: Int) = overlay.setFloatingPointerDotColor(argb)
    suspend fun setFloatingPointerClickVisualFeedbackEnabled(enabled: Boolean) =
        overlay.setFloatingPointerClickVisualFeedbackEnabled(enabled)
    suspend fun setFloatingPointerClickHapticEnabled(enabled: Boolean) = overlay.setFloatingPointerClickHapticEnabled(enabled)
    suspend fun setFloatingPointerRippleColor(argb: Int) = overlay.setFloatingPointerRippleColor(argb)
    suspend fun setFloatingPointerRippleSizeDp(value: Float) = overlay.setFloatingPointerRippleSizeDp(value)
    suspend fun setFloatingPointerRippleDurationMs(value: Int) = overlay.setFloatingPointerRippleDurationMs(value)
    suspend fun setFloatingPointerTrailType(type: FloatingPointerTrailType) = overlay.setFloatingPointerTrailType(type)
    suspend fun setFloatingPointerTrailDurationMs(value: Int) = overlay.setFloatingPointerTrailDurationMs(value)
    suspend fun setFloatingPointerTrailColor(argb: Int) = overlay.setFloatingPointerTrailColor(argb)
    suspend fun setFloatingPointerHideWhenJoystickReleased(enabled: Boolean) =
        overlay.setFloatingPointerHideWhenJoystickReleased(enabled)

    suspend fun setFloatingPointerClickDistanceThresholdDp(value: Float) =
        overlay.setFloatingPointerClickDistanceThresholdDp(value)
    suspend fun setFloatingPointerJoystickInnerColor(argb: Int) = overlay.setFloatingPointerJoystickInnerColor(argb)
    suspend fun setFloatingPointerJoystickOuterColor(argb: Int) = overlay.setFloatingPointerJoystickOuterColor(argb)
    suspend fun setFloatingPointerJoystickGradientRadiusFraction(value: Float) =
        overlay.setFloatingPointerJoystickGradientRadiusFraction(value)
    suspend fun setFloatingPointerHideOnOutsideClick(enabled: Boolean) = overlay.setFloatingPointerHideOnOutsideClick(enabled)
    suspend fun setFloatingPointerHideOnQuickSwipe(enabled: Boolean) = overlay.setFloatingPointerHideOnQuickSwipe(enabled)
    suspend fun setFloatingPointerHideWhenIdle(enabled: Boolean) = overlay.setFloatingPointerHideWhenIdle(enabled)
    suspend fun setFloatingPointerIdleHideDelayMs(value: Int) = overlay.setFloatingPointerIdleHideDelayMs(value)
    suspend fun setFloatingPointerReleaseClickAndDismiss(enabled: Boolean) =
        overlay.setFloatingPointerReleaseClickAndDismiss(enabled)
    suspend fun setFloatingPointerHoverEnterSelect(enabled: Boolean) =
        overlay.setFloatingPointerHoverEnterSelect(enabled)
    suspend fun setFloatingPointerJoystickLongPressAction(action: GestureAction) = overlay.setFloatingPointerJoystickLongPressAction(action)
    suspend fun setFloatingPointerRadialAlwaysVisible(enabled: Boolean) = overlay.setFloatingPointerRadialAlwaysVisible(enabled)
    suspend fun setFloatingPointerRadialLongPressMs(value: Int) = overlay.setFloatingPointerRadialLongPressMs(value)
    suspend fun setFloatingPointerRadialOuterDiameterPx(value: Float) = overlay.setFloatingPointerRadialOuterDiameterPx(value)
    suspend fun setFloatingPointerRadialInnerDiameterPx(value: Float) = overlay.setFloatingPointerRadialInnerDiameterPx(value)
    suspend fun setFloatingPointerRadialOuterColor(argb: Int) = overlay.setFloatingPointerRadialOuterColor(argb)
    suspend fun setFloatingPointerRadialInnerColor(argb: Int) = overlay.setFloatingPointerRadialInnerColor(argb)
    suspend fun setFloatingPointerRadialDividerThicknessPx(value: Float) =
        overlay.setFloatingPointerRadialDividerThicknessPx(value)
    suspend fun setFloatingPointerRadialDividerColor(argb: Int) = overlay.setFloatingPointerRadialDividerColor(argb)
    suspend fun setFloatingPointerRadialIconSizeFraction(value: Float) = overlay.setFloatingPointerRadialIconSizeFraction(value)
    suspend fun setFloatingPointerRadialIconColor(argb: Int) = overlay.setFloatingPointerRadialIconColor(argb)
    suspend fun setFloatingPointerRadialSlotAction(index: Int, action: GestureAction) =
        overlay.setFloatingPointerRadialSlotAction(index, action)
    suspend fun resetFloatingPointerRadialDesignDefaults() = overlay.resetFloatingPointerRadialDesignDefaults()

    suspend fun setFingertipRingSlotCount(count: Int) = overlay.setFingertipRingSlotCount(count)
    suspend fun setFingertipRingSlotAction(index: Int, action: GestureAction) =
        overlay.setFingertipRingSlotAction(index, action)
    suspend fun setFingertipRingOrbitRadiusPx(value: Float) = overlay.setFingertipRingOrbitRadiusPx(value)
    suspend fun setFingertipRingIconSizePx(value: Float) = overlay.setFingertipRingIconSizePx(value)

    suspend fun setFloatingPointerEdgeThresholdDp(value: Float) = overlay.setFloatingPointerEdgeThresholdDp(value)
    suspend fun setFloatingPointerEdgePreviewSensitivity(value: Int) =
        overlay.setFloatingPointerEdgePreviewSensitivity(value)
    suspend fun setFloatingPointerEdgePreviewGlowSize(value: Int) = overlay.setFloatingPointerEdgePreviewGlowSize(value)
    suspend fun setFloatingPointerEdgePreviewShowIcon(enabled: Boolean) =
        overlay.setFloatingPointerEdgePreviewShowIcon(enabled)
    suspend fun setFloatingPointerEdgeVisualSizeDp(value: Float) = overlay.setFloatingPointerEdgeVisualSizeDp(value)
    suspend fun setFloatingPointerEdgeVisualOpacity(value: Int) = overlay.setFloatingPointerEdgeVisualOpacity(value)
    suspend fun setFloatingPointerEdgeVisualColor(argb: Int) = overlay.setFloatingPointerEdgeVisualColor(argb)
    suspend fun setFloatingPointerEdgeBarEnabled(side: FloatingPointerEdgeSide, enabled: Boolean) =
        overlay.setFloatingPointerEdgeBarEnabled(side, enabled)
    suspend fun setFloatingPointerEdgeBarSlotAction(
        side: FloatingPointerEdgeSide,
        slotIndex: Int,
        action: GestureAction,
    ) = overlay.setFloatingPointerEdgeBarSlotAction(side, slotIndex, action)
    suspend fun addFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide) =
        overlay.addFloatingPointerEdgeBarSlot(side)
    suspend fun removeFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide, slotIndex: Int) =
        overlay.removeFloatingPointerEdgeBarSlot(side, slotIndex)
    suspend fun resetFloatingPointerEdgeDefaults() = overlay.resetFloatingPointerEdgeDefaults()
    suspend fun resetFloatingPointerVisualDefaults() = overlay.resetFloatingPointerVisualDefaults()
    suspend fun resetFloatingPointerJoystickVisualDefaults() = overlay.resetFloatingPointerJoystickVisualDefaults()
    suspend fun resetFloatingPointerJoystickBehaviorDefaults() = overlay.resetFloatingPointerJoystickBehaviorDefaults()
    suspend fun setQuickLauncherPanels(
        panels: List<com.slideindex.app.launcher.QuickLauncherPanel>,
    ) = overlay.setQuickLauncherPanels(panels)

    suspend fun updateQuickLauncherPanelItems(
        panelId: String,
        items: List<com.slideindex.app.launcher.QuickLauncherItem>,
    ) = overlay.updateQuickLauncherPanelItems(panelId, items)

    suspend fun setQuickLauncherItems(items: List<com.slideindex.app.launcher.QuickLauncherItem>) =
        overlay.setQuickLauncherItems(items)
    suspend fun setHoneycombLauncherItems(items: List<com.slideindex.app.launcher.QuickLauncherItem>) =
        overlay.setHoneycombLauncherItems(items)

    suspend fun setQuickWheels(wheels: List<QuickWheel>) = overlay.setQuickWheels(wheels)

    suspend fun setFvRingLauncherSettings(
        axis: FvRingLauncherAxis,
        settings: FvRingLauncherSettings,
    ) = overlay.setFvRingLauncherSettings(axis, settings)

    suspend fun setFvRingLauncherSlot(
        axis: FvRingLauncherAxis,
        index: Int,
        item: com.slideindex.app.launcher.QuickLauncherItem,
    ) = overlay.setFvRingLauncherSlot(axis, index, item)

    suspend fun setFvRingLauncherSlotIconOverride(
        axis: FvRingLauncherAxis,
        index: Int,
        override: FvRingLauncherSlotIconOverride?,
    ) = overlay.setFvRingLauncherSlotIconOverride(axis, index, override)

    suspend fun swapFvRingLauncherSlots(
        axis: FvRingLauncherAxis,
        fromIndex: Int,
        toIndex: Int,
    ) = overlay.swapFvRingLauncherSlots(axis, fromIndex, toIndex)

    suspend fun setFvRingLauncherCircleCount(
        axis: FvRingLauncherAxis,
        circleCount: Int,
    ) = overlay.setFvRingLauncherCircleCount(axis, circleCount)

    suspend fun setFvRingLauncherLinkAppearanceAxes(
        enabled: Boolean,
        activeAxis: FvRingLauncherAxis,
        mergeDirection: FvRingLauncherAxisMergeDirection?,
    ) = overlay.setFvRingLauncherLinkAppearanceAxes(enabled, activeAxis, mergeDirection)

    suspend fun setFvRingLauncherLinkSlotAxes(
        enabled: Boolean,
        activeAxis: FvRingLauncherAxis,
        mergeDirection: FvRingLauncherAxisMergeDirection?,
    ) = overlay.setFvRingLauncherLinkSlotAxes(enabled, activeAxis, mergeDirection)

    suspend fun setQuickLauncherDisplaySettings(settings: QuickLauncherDisplaySettings) =
        overlay.setQuickLauncherDisplaySettings(settings)

    suspend fun setHoneycombDisplaySettings(settings: HoneycombDisplaySettings) =
        overlay.setHoneycombDisplaySettings(settings)

    suspend fun setAppCarouselSwitcherSettings(settings: AppCarouselSwitcherSettings) =
        overlay.setAppCarouselSwitcherSettings(settings)

    suspend fun setHolographicLauncherTimeoutSeconds(value: Int) =
        overlay.setHolographicLauncherTimeoutSeconds(value)

    suspend fun setHolographicRotationSensitivity(value: Float) =
        overlay.setHolographicRotationSensitivity(value)

    suspend fun setHolographicHapticLevel(value: Int) =
        overlay.setHolographicHapticLevel(value)

    suspend fun setHolographicBackgroundStyle(value: Int) =
        overlay.setHolographicBackgroundStyle(value)

    suspend fun setHolographicBlurDp(value: Int) =
        overlay.setHolographicBlurDp(value)

    suspend fun setHolographicDimPercent(value: Int) =
        overlay.setHolographicDimPercent(value)

    suspend fun addHolographicHiddenApp(packageName: String) =
        overlay.addHolographicHiddenApp(packageName)

    suspend fun removeHolographicHiddenApp(packageName: String) =
        overlay.removeHolographicHiddenApp(packageName)

    suspend fun setShellCommands(items: List<ShellCommand>) = overlay.setShellCommands(items)
    suspend fun setActivityShortcuts(items: List<com.slideindex.app.activity.ActivityShortcut>) =
        overlay.setActivityShortcuts(items)
    suspend fun setWidgetPanelPages(pages: List<com.slideindex.app.widget.WidgetPanelPage>) = overlay.setWidgetPanelPages(pages)
    suspend fun setWidgetPanelBlurEnabled(enabled: Boolean) = overlay.setWidgetPanelBlurEnabled(enabled)
    suspend fun setWidgetPanelBlurRadiusDp(radiusDp: Int) = overlay.setWidgetPanelBlurRadiusDp(radiusDp)
    suspend fun setWidgetPanelWidthFraction(fraction: Float) = overlay.setWidgetPanelWidthFraction(fraction)
    suspend fun setDebugPerformanceMonitorEnabled(enabled: Boolean) = overlay.setDebugPerformanceMonitorEnabled(enabled)

    suspend fun setFloatBallEnabled(enabled: Boolean) = overlay.setFloatBallEnabled(enabled)
    suspend fun setFloatBallSizeDp(value: Float) = overlay.setFloatBallSizeDp(value)
    suspend fun setFloatBallPickCrossArmDp(value: Float) = overlay.setFloatBallPickCrossArmDp(value)
    suspend fun setFloatBallOpacity(value: Float) = overlay.setFloatBallOpacity(value)
    suspend fun setFloatBallPosition(customCenterXFraction: Float, yFraction: Float) =
        overlay.setFloatBallPosition(customCenterXFraction, yFraction)

    suspend fun setFloatBallPositionYFraction(yFraction: Float) =
        overlay.setFloatBallPositionYFraction(yFraction)

    suspend fun setFloatBallVisibleFraction(visibleFraction: Float) =
        overlay.setFloatBallVisibleFraction(visibleFraction)

    suspend fun setFloatBallOcrFallbackEnabled(enabled: Boolean) =
        overlay.setFloatBallOcrFallbackEnabled(enabled)

    suspend fun setFloatBallOcrModelId(modelId: String) =
        overlay.setFloatBallOcrModelId(modelId)

    suspend fun setOcrDownloadWifiOnly(enabled: Boolean) =
        overlay.setOcrDownloadWifiOnly(enabled)

    suspend fun setFloatBallPointerSpeedFraction(value: Float) =
        overlay.setFloatBallPointerSpeedFraction(value)

    suspend fun setFloatBallPointerSpeedVerticalFraction(value: Float) =
        overlay.setFloatBallPointerSpeedVerticalFraction(value)

    suspend fun setFloatBallPositionMode(mode: FloatBallPositionMode) =
        overlay.setFloatBallPositionMode(mode)

    suspend fun setFloatBallActiveSide(side: FloatBallSide) =
        overlay.setFloatBallActiveSide(side)

    suspend fun setFloatBallLineHeightFraction(value: Float) =
        overlay.setFloatBallLineHeightFraction(value)

    suspend fun setFloatBallLineWidthFraction(value: Float) =
        overlay.setFloatBallLineWidthFraction(value)

    suspend fun setFloatBallLineOpacity(value: Float) =
        overlay.setFloatBallLineOpacity(value)

    suspend fun setFloatBallGestureAction(type: FloatBallGestureType, action: GestureAction) =
        overlay.setFloatBallGestureAction(type, action)

    suspend fun setFloatBallStyleType(type: FloatBallStyleType) =
        overlay.setFloatBallStyleType(type)

    suspend fun setFloatBallCustomImageUri(uri: String) =
        overlay.setFloatBallCustomImageUri(uri)

    suspend fun setFloatBallSlideshowUris(uris: List<String>) =
        overlay.setFloatBallSlideshowUris(uris)

    suspend fun setFloatBallGifUri(uri: String) =
        overlay.setFloatBallGifUri(uri)

    suspend fun setFloatBallPickOffsetDp(value: Float) =
        overlay.setFloatBallPickOffsetDp(value)

    suspend fun setFloatBallPickTextSizeSp(value: Float) =
        overlay.setFloatBallPickTextSizeSp(value)

    suspend fun setFloatBallPickBottomTransitionFraction(value: Float) =
        overlay.setFloatBallPickBottomTransitionFraction(value)

    suspend fun setFloatBallPickTextFirstPanel(enabled: Boolean) =
        overlay.setFloatBallPickTextFirstPanel(enabled)

    suspend fun setFloatBallPickPanelStyle(style: PickResultPanelStyle) =
        overlay.setFloatBallPickPanelStyle(style)

    suspend fun setFloatBallPickPanelPlacement(placement: PickResultPanelPlacement) =
        overlay.setFloatBallPickPanelPlacement(placement)

    suspend fun setFloatBallPickSearchGridDefaultState(state: PickResultSearchGridDefaultState) =
        overlay.setFloatBallPickSearchGridDefaultState(state)

    suspend fun setFloatBallPickSearchGridLastExpanded(expanded: Boolean) =
        overlay.setFloatBallPickSearchGridLastExpanded(expanded)

    suspend fun setFloatBallPickTextModeDefault(state: PickResultTextModeDefault) =
        overlay.setFloatBallPickTextModeDefault(state)

    suspend fun setFloatBallPickTextModeLastMode(storageKey: String) =
        overlay.setFloatBallPickTextModeLastMode(storageKey)

    suspend fun setFloatBallPickDefaultSearchEngineId(id: String?) =
        overlay.setFloatBallPickDefaultSearchEngineId(id)

    suspend fun setFloatBallPickAutoSelectAll(enabled: Boolean) =
        overlay.setFloatBallPickAutoSelectAll(enabled)

    suspend fun setFloatBallPickCopyDismissPanel(enabled: Boolean) =
        overlay.setFloatBallPickCopyDismissPanel(enabled)

    suspend fun setFloatBallPickCopyButtonPosition(position: PickResultCopyButtonPosition) =
        overlay.setFloatBallPickCopyButtonPosition(position)

    suspend fun setFloatBallPickImageToolbarPosition(position: PickResultImageToolbarPosition) =
        overlay.setFloatBallPickImageToolbarPosition(position)

    suspend fun setFloatBallPickHapticEnabled(enabled: Boolean) =
        overlay.setFloatBallPickHapticEnabled(enabled)

    suspend fun setFloatBallDragPasteEnabled(enabled: Boolean) =
        overlay.setFloatBallDragPasteEnabled(enabled)

    suspend fun setFloatBallPickPanelEnterAnimationMs(value: Int) =
        overlay.setFloatBallPickPanelEnterAnimationMs(value)

    suspend fun setFloatBallPickPanelExitAnimationMs(value: Int) =
        overlay.setFloatBallPickPanelExitAnimationMs(value)

    suspend fun setFloatBallPointerSlopDp(value: Float) =
        overlay.setFloatBallPointerSlopDp(value)

    suspend fun setFloatBallHoverPauseDelayMs(value: Int) =
        overlay.setFloatBallHoverPauseDelayMs(value)

    suspend fun setFloatBallLongPressMs(value: Int) =
        overlay.setFloatBallLongPressMs(value)

    suspend fun setFloatBallRegionalCancelSlopDp(value: Float) =
        overlay.setFloatBallRegionalCancelSlopDp(value)

    suspend fun setFloatBallDownSwipeShortPercent(value: Float) =
        overlay.setFloatBallDownSwipeShortPercent(value)

    suspend fun setFloatBallSideSwipeShortPercent(value: Float) =
        overlay.setFloatBallSideSwipeShortPercent(value)

    suspend fun setFloatBallUpSwipeShortPercent(value: Float) =
        overlay.setFloatBallUpSwipeShortPercent(value)

    suspend fun setFloatBallOverlayAnchorAtTouchDown(enabled: Boolean) =
        overlay.setFloatBallOverlayAnchorAtTouchDown(enabled)

    suspend fun setFloatBallInstantTranslate(enabled: Boolean) =
        overlay.setFloatBallInstantTranslate(enabled)

    suspend fun setFloatBallTranslateEngine(engine: FloatBallTranslateEngine) =
        overlay.setFloatBallTranslateEngine(engine)

    suspend fun setFloatBallTranslateTargetLang(languageCode: String) =
        overlay.setFloatBallTranslateTargetLang(languageCode)

    suspend fun setFloatBallTranslateAppPackage(packageName: String) =
        overlay.setFloatBallTranslateAppPackage(packageName)

    suspend fun setFloatBallImageSearchPickPanelTransparency(value: Float) =
        overlay.setFloatBallImageSearchPickPanelTransparency(value)

    suspend fun setShareImageOcrHistoryEnabled(enabled: Boolean) = overlay.setShareImageOcrHistoryEnabled(enabled)

    suspend fun setClipboardBackgroundMonitoring(enabled: Boolean) = overlay.setClipboardBackgroundMonitoring(enabled)

    suspend fun setClipboardBackgroundMonitoringMode(mode: ClipboardMonitoringMode) =
        overlay.setClipboardBackgroundMonitoringMode(mode)

    suspend fun setClipboardOverlayEnabled(enabled: Boolean) =
        overlay.setClipboardOverlayEnabled(enabled)

    suspend fun setClipboardOverlayScalePercent(percent: Int) =
        overlay.setClipboardOverlayScalePercent(percent)

    suspend fun setClipboardScreenshotMonitoring(enabled: Boolean) =
        overlay.setClipboardScreenshotMonitoring(enabled)

    suspend fun setClipboardHistoryMaxEntries(maxEntries: Int) =
        overlay.setClipboardHistoryMaxEntries(maxEntries)

    suspend fun setClipboardHistoryFloatEnabled(enabled: Boolean) =
        overlay.setClipboardHistoryFloatEnabled(enabled)

    suspend fun setClipboardHistoryFloatEnabledLandscape(enabled: Boolean) =
        overlay.setClipboardHistoryFloatEnabledLandscape(enabled)

    suspend fun setClipboardHistoryFloatLockPosition(lock: Boolean) =
        overlay.setClipboardHistoryFloatLockPosition(lock)

    suspend fun setClipboardHistoryFloatHandleWidthDp(widthDp: Int) =
        overlay.setClipboardHistoryFloatHandleWidthDp(widthDp)

    suspend fun setClipboardHistoryFloatHandleY(y: Int, landscape: Boolean) =
        overlay.setClipboardHistoryFloatHandleY(y, landscape)

    suspend fun setClipboardFloatEnabled(enabled: Boolean) =
        overlay.setClipboardFloatEnabled(enabled)

    suspend fun setClipboardFloatShowChip(showChip: Boolean) =
        overlay.setClipboardFloatShowChip(showChip)

    suspend fun setClipboardFloatPinPosition(pin: Boolean) =
        overlay.setClipboardFloatPinPosition(pin)

    suspend fun setClipboardFloatSingleLineEntryClickAction(action: ClipboardFloatEntryClickAction) =
        overlay.setClipboardFloatSingleLineEntryClickAction(action)

    suspend fun setClipboardFloatSingleLineEntryLongPressAction(action: ClipboardFloatEntryLongPressAction) =
        overlay.setClipboardFloatSingleLineEntryLongPressAction(action)

    suspend fun setClipboardFloatCardEntryClickAction(action: ClipboardFloatEntryClickAction) =
        overlay.setClipboardFloatCardEntryClickAction(action)

    suspend fun setClipboardFloatCardEntryLongPressAction(action: ClipboardFloatEntryLongPressAction) =
        overlay.setClipboardFloatCardEntryLongPressAction(action)

    suspend fun setClipboardFloatListStyle(style: ClipboardFloatListStyle) =
        overlay.setClipboardFloatListStyle(style)

    suspend fun setClipboardFloatAlpha(alpha: Float) =
        overlay.setClipboardFloatAlpha(alpha)

    suspend fun setClipboardFloatAutoDimWhenUnfocused(autoDim: Boolean) =
        overlay.setClipboardFloatAutoDimWhenUnfocused(autoDim)

    suspend fun setClipboardFloatAutoCloseSeconds(seconds: Int) =
        overlay.setClipboardFloatAutoCloseSeconds(seconds)

    suspend fun setClipboardFloatGeometry(
        x: Int,
        y: Int,
        widthDp: Int,
        heightDp: Int,
        landscape: Boolean,
    ) = overlay.setClipboardFloatGeometry(x, y, widthDp, heightDp, landscape)

    suspend fun resetClipboardFloatGeometry() = overlay.resetClipboardFloatGeometry()

    suspend fun setClipboardFloatChipGeometry(
        x: Int,
        y: Int,
        followIme: Boolean,
        landscape: Boolean,
    ) = overlay.setClipboardFloatChipGeometry(x, y, followIme, landscape)

    suspend fun setClipboardFloatOrientationGeometry(
        landscape: Boolean,
        geometry: ClipboardFloatOrientationGeometry,
        chipFollowIme: Boolean,
    ) = overlay.setClipboardFloatOrientationGeometry(landscape, geometry, chipFollowIme)

    suspend fun addClipboardFloatBlockedPackage(packageName: String) =
        overlay.addClipboardFloatBlockedPackage(packageName)

    suspend fun removeClipboardFloatBlockedPackage(packageName: String) =
        overlay.removeClipboardFloatBlockedPackage(packageName)

    suspend fun addClipboardLsposedWhitelistPackage(packageName: String) =
        overlay.addClipboardLsposedWhitelistPackage(packageName)

    suspend fun setClipboardMonitoringChannel(channel: ClipboardMonitoringChannel) =
        overlay.setClipboardMonitoringChannel(channel)

    suspend fun setClipboardMonitoringCapture(capture: ClipboardMonitoringCapture) =
        overlay.setClipboardMonitoringCapture(capture)

    suspend fun removeClipboardLsposedWhitelistPackage(packageName: String) =
        overlay.removeClipboardLsposedWhitelistPackage(packageName)

    suspend fun setClipboardFloatPasteHapticEnabled(enabled: Boolean) =
        overlay.setClipboardFloatPasteHapticEnabled(enabled)

    suspend fun setClipboardPasteFvStyleEnabled(enabled: Boolean) =
        overlay.setClipboardPasteFvStyleEnabled(enabled)

    suspend fun recordClipboardFloatPasteResult(success: Boolean) =
        overlay.recordClipboardFloatPasteResult(success)

    suspend fun setStashPanelBackgroundBlurEnabled(enabled: Boolean) =
        overlay.setStashPanelBackgroundBlurEnabled(enabled)

    suspend fun setStashPanelBackgroundBlurRadiusDp(value: Int) =
        overlay.setStashPanelBackgroundBlurRadiusDp(value)

    suspend fun setOverlayBlurEnabled(enabled: Boolean) =
        overlay.setOverlayBlurEnabled(enabled)

    suspend fun setDefaultImageViewerPackage(packageName: String?) = overlay.setDefaultImageViewerPackage(packageName)
    suspend fun setImageEditorDelayDeleteEnabled(enabled: Boolean) = overlay.setImageEditorDelayDeleteEnabled(enabled)

    suspend fun setSearchEngines(engines: List<SearchEngineConfig>) =
        overlay.setSearchEngines(engines)

    suspend fun setLauncherShortcutMenuOrder(ids: List<String>) =
        overlay.setLauncherShortcutMenuOrder(ids)

    suspend fun setLauncherShortcutMenuDisabled(ids: Set<String>) =
        overlay.setLauncherShortcutMenuDisabled(ids)

    suspend fun setSearchEngineGridColumns(value: Int) =
        overlay.setSearchEngineGridColumns(value)

    suspend fun setSearchEngineGridRows(value: Int) =
        overlay.setSearchEngineGridRows(value)

    suspend fun setSearchEngineShowLabels(enabled: Boolean) =
        overlay.setSearchEngineShowLabels(enabled)

    suspend fun setSearchPanelDefaultEngineId(id: String?) =
        overlay.setSearchPanelDefaultEngineId(id)

    suspend fun setSearchPanelInputBehavior(behavior: SearchPanelInputBehavior) =
        overlay.setSearchPanelInputBehavior(behavior)

    suspend fun setSearchPanelEnterAction(action: SearchPanelEnterAction) =
        overlay.setSearchPanelEnterAction(action)

    suspend fun setSearchPanelContactSearchEnabled(enabled: Boolean) =
        overlay.setSearchPanelContactSearchEnabled(enabled)

    suspend fun setSearchPanelFileSearchEnabled(enabled: Boolean) =
        overlay.setSearchPanelFileSearchEnabled(enabled)

    suspend fun setSearchPanelAppSearchEnabled(enabled: Boolean) =
        overlay.setSearchPanelAppSearchEnabled(enabled)

    suspend fun setSearchPanelSettingsSearchEnabled(enabled: Boolean) =
        overlay.setSearchPanelSettingsSearchEnabled(enabled)

    suspend fun setSearchPanelShortcutSearchEnabled(enabled: Boolean) =
        overlay.setSearchPanelShortcutSearchEnabled(enabled)

    suspend fun setSearchPanelClipboardSearchEnabled(enabled: Boolean) =
        overlay.setSearchPanelClipboardSearchEnabled(enabled)

    suspend fun setSearchPanelFileTypesEnabled(types: Set<String>) =
        overlay.setSearchPanelFileTypesEnabled(types)

    suspend fun setSearchPanelFileShowFolders(enabled: Boolean) =
        overlay.setSearchPanelFileShowFolders(enabled)

    suspend fun setSearchPanelFileShowSystemFiles(enabled: Boolean) =
        overlay.setSearchPanelFileShowSystemFiles(enabled)

    suspend fun setSearchPanelFilePreviewsEnabled(enabled: Boolean) =
        overlay.setSearchPanelFilePreviewsEnabled(enabled)

    suspend fun setSearchPanelFileFolderWhitelist(patterns: Set<String>) =
        overlay.setSearchPanelFileFolderWhitelist(patterns)

    suspend fun setSearchPanelFileFolderBlacklist(patterns: Set<String>) =
        overlay.setSearchPanelFileFolderBlacklist(patterns)

    suspend fun setSearchPanelBarPosition(position: SearchPanelBarPosition) =
        overlay.setSearchPanelBarPosition(position)

    suspend fun setSearchPanelListOrder(order: SearchPanelListOrder) =
        overlay.setSearchPanelListOrder(order)

    suspend fun setSearchPanelAppDisplayStyle(style: SearchPanelAppDisplayStyle) =
        overlay.setSearchPanelAppDisplayStyle(style)

    suspend fun setSearchPanelCalculatorEnabled(enabled: Boolean) =
        overlay.setSearchPanelCalculatorEnabled(enabled)

    suspend fun setSearchPanelBackgroundStyle(style: Int) =
        overlay.setSearchPanelBackgroundStyle(style)

    suspend fun setSearchPanelBlurRadiusDp(value: Int) =
        overlay.setSearchPanelBlurRadiusDp(value)

    suspend fun setSearchPanelDimPercent(value: Int) =
        overlay.setSearchPanelDimPercent(value)

    suspend fun setSearchPanelWebSuggestionsEnabled(enabled: Boolean) =
        overlay.setSearchPanelWebSuggestionsEnabled(enabled)

    suspend fun setSearchPanelWebSuggestionsCount(count: Int) =
        overlay.setSearchPanelWebSuggestionsCount(count)

    suspend fun setSearchPanelHistoryMaxEntries(maxEntries: Int) =
        overlay.setSearchPanelHistoryMaxEntries(maxEntries)

    suspend fun setSearchPanelSectionAliases(aliases: SearchPanelSectionAliasSettings) =
        overlay.setSearchPanelSectionAliases(aliases)

    suspend fun setAggregatedImageSearchEngines(configs: List<AggregatedImageSearchEngineConfig>) =
        overlay.setAggregatedImageSearchEngines(configs)

    suspend fun setOtpCopyToClipboard(enabled: Boolean) = otp.setOtpCopyToClipboard(enabled)
    suspend fun setOtpKeywordsRegex(value: String) = otp.setOtpKeywordsRegex(value)
    suspend fun setOtpUserMatchRules(rules: List<com.slideindex.app.otp.OtpMatchRule>) = otp.setOtpUserMatchRules(rules)
    suspend fun setOtpDisabledOfficialRuleIds(ids: Set<String>) = otp.setOtpDisabledOfficialRuleIds(ids)
    suspend fun setOtpOfficialRuleEnabled(ruleId: String, enabled: Boolean) = otp.setOtpOfficialRuleEnabled(ruleId, enabled)
    suspend fun setOtpAutoInputEnabled(enabled: Boolean) = otp.setOtpAutoInputEnabled(enabled)
    suspend fun setOtpAutoConfirmEnabled(enabled: Boolean) = otp.setOtpAutoConfirmEnabled(enabled)
    suspend fun setOtpAutoInputDelayMs(value: Int) = otp.setOtpAutoInputDelayMs(value)
    suspend fun setOtpAutoInputIntervalMs(value: Int) = otp.setOtpAutoInputIntervalMs(value)
    suspend fun setOtpLsposedSmsCaptureEnabled(enabled: Boolean) = otp.setOtpLsposedSmsCaptureEnabled(enabled)
    suspend fun setOtpLsposedSystemInjectEnabled(enabled: Boolean) = otp.setOtpLsposedSystemInjectEnabled(enabled)
    suspend fun setOtpCodeNotificationEnabled(enabled: Boolean) = otp.setOtpCodeNotificationEnabled(enabled)
    suspend fun setOtpCodeNotificationRetentionSeconds(value: Int) =
        otp.setOtpCodeNotificationRetentionSeconds(value)
    suspend fun setOtpShowCodeToast(enabled: Boolean) = otp.setOtpShowCodeToast(enabled)
    suspend fun setOtpSmsBlacklist(rules: com.slideindex.app.otp.SmsBlacklistRuleSet) =
        otp.setOtpSmsBlacklist(rules)
    suspend fun setOtpBlockCodeSmsEnabled(enabled: Boolean) = otp.setOtpBlockCodeSmsEnabled(enabled)
    suspend fun setOtpMarkSmsReadEnabled(enabled: Boolean) = otp.setOtpMarkSmsReadEnabled(enabled)
    suspend fun setOtpDeleteSmsAfterExtractEnabled(enabled: Boolean) =
        otp.setOtpDeleteSmsAfterExtractEnabled(enabled)
    suspend fun setOtpRecordCodeEnabled(enabled: Boolean) = otp.setOtpRecordCodeEnabled(enabled)
    suspend fun setOtpRecordPlainSmsEnabled(enabled: Boolean) = otp.setOtpRecordPlainSmsEnabled(enabled)
    suspend fun setOtpRecordAppNotifyEnabled(enabled: Boolean) = otp.setOtpRecordAppNotifyEnabled(enabled)
    suspend fun setOtpRecordCodeLimit(value: Int) = otp.setOtpRecordCodeLimit(value)
    suspend fun setOtpRecordPlainSmsLimit(value: Int) = otp.setOtpRecordPlainSmsLimit(value)
    suspend fun setOtpRecordAppNotifyLimit(value: Int) = otp.setOtpRecordAppNotifyLimit(value)
    suspend fun setOtpBlockedPackages(packages: Set<String>) = otp.setOtpBlockedPackages(packages)

    suspend fun setShakeGesturesEnabled(enabled: Boolean) = shake.setShakeGesturesEnabled(enabled)
    suspend fun setShakeGestureAction(type: ShakeGestureType, action: GestureAction) = shake.setShakeGestureAction(type, action)
    suspend fun setLockScreenShakeAction(type: ShakeGestureType, action: GestureAction) = shake.setLockScreenShakeAction(type, action)
    suspend fun setPerAppShakeAction(packageName: String, type: ShakeGestureType, action: GestureAction) =
        shake.setPerAppShakeAction(packageName, type, action)
    suspend fun addPerAppShakeConfig(packageName: String) = shake.addPerAppShakeConfig(packageName)
    suspend fun removePerAppShakeConfig(packageName: String) = shake.removePerAppShakeConfig(packageName)
    suspend fun setShakeDirectionSensitivity(type: ShakeGestureType, value: Float) =
        shake.setShakeDirectionSensitivity(type, value)
    suspend fun setLockScreenShakeEnabled(enabled: Boolean) = shake.setLockScreenShakeEnabled(enabled)
    suspend fun setIndependentAppShakeEnabled(enabled: Boolean) = shake.setIndependentAppShakeEnabled(enabled)
    suspend fun setShakeGlobalSensitivity(value: Float) = shake.setShakeGlobalSensitivity(value)
    suspend fun setShakeIndependentSensitivityEnabled(enabled: Boolean) = shake.setShakeIndependentSensitivityEnabled(enabled)
    suspend fun setShakeVibrationFeedbackEnabled(enabled: Boolean) = shake.setShakeVibrationFeedbackEnabled(enabled)
    suspend fun setShakeAnimationFeedbackEnabled(enabled: Boolean) = shake.setShakeAnimationFeedbackEnabled(enabled)
    suspend fun setShakeAnimationColor(argb: Int) = shake.setShakeAnimationColor(argb)
    suspend fun setShakeDisableInLandscape(enabled: Boolean) = shake.setShakeDisableInLandscape(enabled)
    suspend fun setBackTapEnabled(enabled: Boolean) = shake.setBackTapEnabled(enabled)
    suspend fun setBackTapSensitivity(value: Int) = shake.setBackTapSensitivity(value)
    suspend fun setBackTapRange(value: Int) = shake.setBackTapRange(value)
    suspend fun setBackTapMode(mode: com.slideindex.app.backtap.BackTapMode) = shake.setBackTapMode(mode)
    suspend fun setBackTapPauseWhileCharging(enabled: Boolean) = shake.setBackTapPauseWhileCharging(enabled)
    suspend fun setBackTapVibrationFeedbackEnabled(enabled: Boolean) = shake.setBackTapVibrationFeedbackEnabled(enabled)
    suspend fun setBackTapAction(action: GestureAction) = shake.setBackTapAction(action)
    suspend fun addShakeBlacklistedApp(packageName: String) = shake.addShakeBlacklistedApp(packageName)
    suspend fun removeShakeBlacklistedApp(packageName: String) = shake.removeShakeBlacklistedApp(packageName)

    suspend fun setCornerGestureEnabled(enabled: Boolean) = overlay.setCornerGestureEnabled(enabled)
    suspend fun setCornerGestureLeftEnabled(enabled: Boolean) = overlay.setCornerGestureLeftEnabled(enabled)
    suspend fun setCornerGestureRightEnabled(enabled: Boolean) = overlay.setCornerGestureRightEnabled(enabled)
    suspend fun setCornerGestureVerticalEdgeWidthDp(value: Float) =
        overlay.setCornerGestureVerticalEdgeWidthDp(value)
    suspend fun setCornerGestureVerticalEdgeHeightDp(value: Float) =
        overlay.setCornerGestureVerticalEdgeHeightDp(value)
    suspend fun setCornerGestureHorizontalEdgeWidthDp(value: Float) =
        overlay.setCornerGestureHorizontalEdgeWidthDp(value)
    suspend fun setCornerGestureHorizontalEdgeHeightDp(value: Float) =
        overlay.setCornerGestureHorizontalEdgeHeightDp(value)
    suspend fun setCornerGestureTriggerSlopDp(value: Float) = overlay.setCornerGestureTriggerSlopDp(value)
    suspend fun setCornerGestureHideInLandscape(enabled: Boolean) = overlay.setCornerGestureHideInLandscape(enabled)
    suspend fun setCornerGestureLandscapePreventFalseTouch(enabled: Boolean) =
        overlay.setCornerGestureLandscapePreventFalseTouch(enabled)
    suspend fun setCornerGestureOverrideSystemNav(enabled: Boolean) = overlay.setCornerGestureOverrideSystemNav(enabled)
    suspend fun setCornerGestureInnerDiameterDp(value: Float) = overlay.setCornerGestureInnerDiameterDp(value)
    suspend fun setCornerGestureRingSpacingDp(value: Float) = overlay.setCornerGestureRingSpacingDp(value)
    suspend fun setCornerGestureBubbleSizeDp(value: Float) = overlay.setCornerGestureBubbleSizeDp(value)
    suspend fun setCornerGestureWheelLayerCount(value: Int) =
        overlay.setCornerGestureWheelLayerCount(value)
    suspend fun setCornerGestureCancelOutsideWheel(enabled: Boolean) =
        overlay.setCornerGestureCancelOutsideWheel(enabled)
    suspend fun setCornerGestureProgressiveLayers(enabled: Boolean) =
        overlay.setCornerGestureProgressiveLayers(enabled)
    suspend fun setCornerGestureSlotHaptic(enabled: Boolean) =
        overlay.setCornerGestureSlotHaptic(enabled)
    suspend fun setCornerGestureShowSelectedName(enabled: Boolean) =
        overlay.setCornerGestureShowSelectedName(enabled)
    suspend fun setCornerGestureShowEditButton(enabled: Boolean) =
        overlay.setCornerGestureShowEditButton(enabled)

    suspend fun setCornerGestureSelectedHintIconSizeDp(value: Int) =
        overlay.setCornerGestureSelectedHintIconSizeDp(value)
    suspend fun setCornerGestureBackgroundStyle(style: Int) =
        overlay.setCornerGestureBackgroundStyle(style)
    suspend fun setCornerGestureBlurDp(value: Int) =
        overlay.setCornerGestureBlurDp(value)
    suspend fun setCornerGestureDimPercent(value: Int) =
        overlay.setCornerGestureDimPercent(value)
    suspend fun setCornerGestureUnifiedSlots(enabled: Boolean) =
        overlay.setCornerGestureUnifiedSlots(enabled)
    suspend fun setCornerGestureInnerZoneAction(action: GestureAction) =
        overlay.setCornerGestureInnerZoneAction(action)
    suspend fun setCornerGestureLeftSlotAction(index: Int, action: GestureAction) =
        overlay.setCornerGestureLeftSlotAction(index, action)
    suspend fun setCornerGestureRightSlotAction(index: Int, action: GestureAction) =
        overlay.setCornerGestureRightSlotAction(index, action)
    suspend fun setCornerSlotSubMenu(isLeft: Boolean, index: Int, config: CornerSlotSubMenuConfig) =
        overlay.setCornerSlotSubMenu(isLeft, index, config)

    suspend fun setFaceDownGestureEnabled(enabled: Boolean) = shake.setFaceDownGestureEnabled(enabled)
    suspend fun setFaceDownGestureAction(action: GestureAction) = shake.setFaceDownGestureAction(action)
    suspend fun setFaceDownHoldDurationMs(value: Long) = shake.setFaceDownHoldDurationMs(value)
    suspend fun setFaceDownRequireProximity(enabled: Boolean) = shake.setFaceDownRequireProximity(enabled)
    suspend fun setFaceDownCooldownMs(value: Long) = shake.setFaceDownCooldownMs(value)
    suspend fun setFaceDownDisableInLandscape(enabled: Boolean) = shake.setFaceDownDisableInLandscape(enabled)
    suspend fun setFaceDownVibrationFeedbackEnabled(enabled: Boolean) = shake.setFaceDownVibrationFeedbackEnabled(enabled)

    suspend fun setFaceDownAudioFeedbackEnabled(enabled: Boolean) = shake.setFaceDownAudioFeedbackEnabled(enabled)
    suspend fun setFaceDownAudioFeedbackVolume(value: Int) = shake.setFaceDownAudioFeedbackVolume(value)

    suspend fun setMessageReminderEnabled(enabled: Boolean) = message.setMessageReminderEnabled(enabled)
    suspend fun setMessageInterceptNotifications(enabled: Boolean) =
        message.setMessageInterceptNotifications(enabled)
    suspend fun setMessageFloatIconEnabled(enabled: Boolean) = message.setMessageFloatIconEnabled(enabled)
    suspend fun setMessageSideBubbleEnabled(enabled: Boolean) = message.setMessageSideBubbleEnabled(enabled)
    suspend fun setMessageStyleId(styleId: String) = message.setMessageStyleId(styleId)
    suspend fun setMessagePrimaryStyleEnabled(enabled: Boolean) = message.setMessagePrimaryStyleEnabled(enabled)
    suspend fun setMessageDanmakuEnabled(enabled: Boolean) = message.setMessageDanmakuEnabled(enabled)
    suspend fun setMessageCNoticeEnabled(enabled: Boolean) = message.setMessageCNoticeEnabled(enabled)
    suspend fun setMessageCNoticeOpacity(opacity: Float) = message.setMessageCNoticeOpacity(opacity)
    suspend fun setMessageCNoticeDimmedOpacity(opacity: Float) = message.setMessageCNoticeDimmedOpacity(opacity)
    suspend fun setMessageCNoticeGhostOpacity(opacity: Float) = message.setMessageCNoticeGhostOpacity(opacity)
    suspend fun setMessageCNoticeIconSizeDp(sizeDp: Float) = message.setMessageCNoticeIconSizeDp(sizeDp)
    suspend fun setMessageCNoticeMaxCount(count: Int) = message.setMessageCNoticeMaxCount(count)
    suspend fun setMessageCNoticeAutoDismissSeconds(seconds: Int) =
        message.setMessageCNoticeAutoDismissSeconds(seconds)
    suspend fun setMessageCNoticeHorizontalEdge(edge: String) = message.setMessageCNoticeHorizontalEdge(edge)
    suspend fun setMessageCNoticeYFraction(fraction: Float) = message.setMessageCNoticeYFraction(fraction)
    suspend fun setMessageCNoticeEdgeMarginDp(marginDp: Float) = message.setMessageCNoticeEdgeMarginDp(marginDp)
    suspend fun setMessageCNoticeDimDelayMs(delayMs: Int) = message.setMessageCNoticeDimDelayMs(delayMs)
    suspend fun setMessageCNoticeGhostDelayMs(delayMs: Int) = message.setMessageCNoticeGhostDelayMs(delayMs)
    suspend fun setMessageCNoticeDefaultCollapsed(collapsed: Boolean) =
        message.setMessageCNoticeDefaultCollapsed(collapsed)

    suspend fun setMessageCNoticePeekBannerEnabled(enabled: Boolean) =
        message.setMessageCNoticePeekBannerEnabled(enabled)

    suspend fun setMessageCNoticeLandscapeEnabled(enabled: Boolean) =
        message.setMessageCNoticeLandscapeEnabled(enabled)
    suspend fun setMessageThemeId(themeId: String) = message.setMessageThemeId(themeId)
    suspend fun setMessageSideThemeId(themeId: String) = message.setMessageSideThemeId(themeId)
    suspend fun setMessageDanmakuThemeId(themeId: String) = message.setMessageDanmakuThemeId(themeId)
    suspend fun setMessageFloatIconOpacity(opacity: Float) = message.setMessageFloatIconOpacity(opacity)
    suspend fun setMessageSideBubbleOpacity(opacity: Float) = message.setMessageSideBubbleOpacity(opacity)
    suspend fun setMessageFloatIconSizeDp(sizeDp: Float) = message.setMessageFloatIconSizeDp(sizeDp)
    suspend fun setMessageDanmakuOpacity(opacity: Float) = message.setMessageDanmakuOpacity(opacity)
    suspend fun setMessageDanmakuMaxLines(lines: Int) = message.setMessageDanmakuMaxLines(lines)
    suspend fun setMessageSideMaxCount(count: Int) = message.setMessageSideMaxCount(count)
    suspend fun setMessageSideMaxWidthDp(widthDp: Float) = message.setMessageSideMaxWidthDp(widthDp)
    suspend fun setMessageSideMaxLines(lines: Int) = message.setMessageSideMaxLines(lines)
    suspend fun setMessageFloatIconAutoDismissSeconds(seconds: Int) =
        message.setMessageFloatIconAutoDismissSeconds(seconds)

    suspend fun setMessageSideBubbleAutoDismissSeconds(seconds: Int) =
        message.setMessageSideBubbleAutoDismissSeconds(seconds)
    suspend fun setMessageHideInLandscape(enabled: Boolean) = message.setMessageHideInLandscape(enabled)
    suspend fun setMessagePortraitDanmaku(enabled: Boolean) = message.setMessagePortraitDanmaku(enabled)
    suspend fun setMessageLandscapeDanmaku(enabled: Boolean) = message.setMessageLandscapeDanmaku(enabled)
    suspend fun setMessageSideHorizontalEdge(edge: String) = message.setMessageSideHorizontalEdge(edge)
    suspend fun setMessageSideVerticalAnchor(anchor: String) = message.setMessageSideVerticalAnchor(anchor)
    suspend fun setMessageSideBubbleYFraction(fraction: Float) = message.setMessageSideBubbleYFraction(fraction)
    suspend fun setMessageFloatIconCorner(corner: String) = message.setMessageFloatIconCorner(corner)
    suspend fun setMessageFloatIconYFraction(fraction: Float) = message.setMessageFloatIconYFraction(fraction)
    suspend fun setMessageSideFontSizeLevel(level: Int) = message.setMessageSideFontSizeLevel(level)
    suspend fun setMessageDanmakuSpeedLevel(level: Int) = message.setMessageDanmakuSpeedLevel(level)
    suspend fun setMessageGestureAction(slot: String, action: MessageAction) = message.setMessageGestureAction(slot, action)
    suspend fun addMessageEnabledPackage(packageName: String) = message.addMessageEnabledPackage(packageName)
    suspend fun removeMessageEnabledPackage(packageName: String) = message.removeMessageEnabledPackage(packageName)
    suspend fun addMessageDisabledPackage(packageName: String) = message.addMessageDisabledPackage(packageName)
    suspend fun removeMessageDisabledPackage(packageName: String) = message.removeMessageDisabledPackage(packageName)
    suspend fun addMessageDndPackage(packageName: String) = message.addMessageDndPackage(packageName)
    suspend fun removeMessageDndPackage(packageName: String) = message.removeMessageDndPackage(packageName)
    suspend fun setMessageSuppressWhenSystemDnd(enabled: Boolean) = message.setMessageSuppressWhenSystemDnd(enabled)
    suspend fun setMessageOpenLastOnUnlock(enabled: Boolean) = message.setMessageOpenLastOnUnlock(enabled)
    suspend fun setMessageKeepFloatIconAfterUnlock(enabled: Boolean) =
        message.setMessageKeepFloatIconAfterUnlock(enabled)
    suspend fun setMessageUnlockConfirmationAutoDismissSeconds(seconds: Int) =
        message.setMessageUnlockConfirmationAutoDismissSeconds(seconds)
    suspend fun setMessageOpenLastAlways(packageName: String, enabled: Boolean) =
        message.setMessageOpenLastAlways(packageName, enabled)
    suspend fun upsertMessageAppFilterRule(rule: MessageAppFilterRule) = message.upsertMessageAppFilterRule(rule)
    suspend fun removeMessageAppFilterRule(packageName: String) = message.removeMessageAppFilterRule(packageName)
}
