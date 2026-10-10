package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.TriggerRectanglePresetLogic
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.settings.BubbleStyle
import com.slideindex.app.settings.CapsuleStyle
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.ExcludedAppScopes
import com.slideindex.app.settings.AppCarouselSwitcherSettings
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AndroidBackColorSource
import com.slideindex.app.settings.CornerSlotSubMenuConfig
import com.slideindex.app.settings.FreeWindowLayoutFractions
import com.slideindex.app.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import com.slideindex.app.settings.KeyboardTriggerBehavior
import com.slideindex.app.settings.WaveStyle
import com.slideindex.app.settings.withAddedBottomTriggerHandle
import com.slideindex.app.settings.withAddedTopTriggerHandle
import com.slideindex.app.settings.withAddedTriggerHandlePair
import com.slideindex.app.settings.withDefaultTriggerModeSynced
import com.slideindex.app.settings.withGestureSlotsMirroredFromSide
import com.slideindex.app.settings.withRemovedTriggerHandle
import com.slideindex.app.settings.withRemovedTriggerHandleLayoutOnly
import com.slideindex.app.settings.withSlotConfigSynced
import com.slideindex.app.settings.withTriggerAlignOppositeGestures
import com.slideindex.app.settings.forLandscapeEditing
import com.slideindex.app.settings.forLandscapeHandleEditing
import com.slideindex.app.settings.mergeLandscapeEdits
import com.slideindex.app.ui.trigger.TriggerSettingsLandscapeSession
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class HomeDetailSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val triggerDesignWriteMutex = Mutex()
    private val landscapeInitMutex = Mutex()

    private fun landscapeEditing(): Boolean = TriggerSettingsLandscapeSession.active

    private fun mergeLandscapeOptimistic(settings: AppSettings, edited: AppSettings): AppSettings =
        if (landscapeEditing()) settings.mergeLandscapeEdits(edited) else edited

    private fun landscapeWorking(settings: AppSettings): AppSettings =
        if (landscapeEditing()) settings.forLandscapeEditing() else settings

    private fun applyLandscapeOptimistic(settings: AppSettings, block: (AppSettings) -> AppSettings): AppSettings {
        val working = landscapeWorking(settings)
        val edited = block(working)
        return mergeLandscapeOptimistic(settings, edited)
    }

    /** 首次进入横屏触钮时复制竖屏配置；后台静默执行，失败时不弹 snackbar。 */
    fun ensureLandscapeTriggerHandlesInitialized() {
        viewModelScope.launch {
            landscapeInitMutex.withLock {
                settingsRepository.ensureLandscapeTriggerHandlesInitialized()
            }
        }
    }

    /** 「复制竖屏设置」：把竖屏当前的触钮与手势动作合并进横屏；失败会弹 snackbar。 */
    fun copyPortraitToLandscape() {
        viewModelScope.launch {
            landscapeInitMutex.withLock {
                settingsRepository.copyPortraitToLandscape().onFailure {
                    userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
                }
            }
        }
    }

    fun setKeyboardTriggerBehavior(behavior: KeyboardTriggerBehavior, landscape: Boolean) = launchSettingsWrite {
        if (landscape) {
            settingsRepository.setKeyboardTriggerBehaviorLandscape(behavior)
        } else {
            settingsRepository.setKeyboardTriggerBehaviorPortrait(behavior)
        }
    }

    fun setIndexHeightFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setIndexHeightFraction(value)
    }

    fun setAppsPerRow(value: Int) = launchSettingsWrite {
        settingsRepository.setAppsPerRow(value)
    }

    fun setPanelOpacity(value: Float) = launchSettingsWrite {
        settingsRepository.setPanelOpacity(value)
    }

    fun setHideEmptyIndexLetters(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHideEmptyIndexLetters(enabled)
    }

    fun setDebugPerformanceMonitorEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setDebugPerformanceMonitorEnabled(enabled)
    }

    fun addHiddenApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addHiddenApp(packageName)
    }

    fun removeHiddenApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removeHiddenApp(packageName)
    }

    fun addPreviousAppExcludedApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addPreviousAppExcludedPackage(packageName)
    }

    fun removePreviousAppExcludedApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removePreviousAppExcludedPackage(packageName)
    }

    fun addExcludedTriggerApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addExcludedTriggerApp(packageName)
    }

    fun removeExcludedTriggerApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removeExcludedTriggerApp(packageName)
    }

    fun setExcludedAppSuppressTriggers(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setExcludedAppSuppressTriggers(enabled)
    }

    fun setExcludedAppSuppressCornerWheel(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setExcludedAppSuppressCornerWheel(enabled)
    }

    fun setExcludedAppSuppressFloatBall(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setExcludedAppSuppressFloatBall(enabled)
    }

    fun setExcludedAppScopes(packageName: String, scopes: ExcludedAppScopes) = launchSettingsWrite {
        settingsRepository.setExcludedAppScopes(packageName, scopes)
    }

    fun setFreeWindowEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFreeWindowEnabled(enabled)
    }

    fun setAppLaunchPolicyId(policyId: Int) = launchSettingsWrite {
        settingsRepository.setAppLaunchPolicyId(policyId)
    }

    fun setLongPressLaunchDurationMs(durationMs: Int) = launchSettingsWrite {
        settingsRepository.setLongPressLaunchDurationMs(durationMs)
    }

    fun setFreeWindowModeId(modeId: Int) = launchSettingsWrite {
        settingsRepository.setFreeWindowModeId(modeId)
    }

    /**
     * 一次性保存竖屏与横屏两套小窗尺寸位置；两套互不影响，运行时按当前显示方向取用。
     */
    fun setFreeWindowLayout(
        portrait: FreeWindowLayoutFractions,
        landscape: FreeWindowLayoutFractions,
    ) = launchSettingsWrite {
        settingsRepository.setFreeWindowLayout(portrait = portrait, landscape = landscape)
    }

    fun addBottomTriggerHandle() = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            applyLandscapeOptimistic(settings) {
                it.withAddedBottomTriggerHandle()
            }
        },
    ) {
        settingsRepository.addBottomTriggerHandle(landscapeEditing())
    }

    fun addTopTriggerHandle() = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            applyLandscapeOptimistic(settings) {
                it.withAddedTopTriggerHandle()
            }
        },
    ) {
        settingsRepository.addTopTriggerHandle(landscapeEditing())
    }

    fun addTriggerHandlePair() = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            applyLandscapeOptimistic(settings) {
                it.withAddedTriggerHandlePair()
            }
        },
    ) {
        settingsRepository.addTriggerHandlePair(landscapeEditing())
    }

    fun removeTriggerHandle(side: PanelSide, handleId: String) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            applyLandscapeOptimistic(settings) { working ->
                if (landscapeEditing()) {
                    working.withRemovedTriggerHandle(side, handleId)
                } else {
                    settings.withRemovedTriggerHandle(side, handleId)
                }
            }
        },
    ) {
        settingsRepository.removeTriggerHandle(side, handleId, landscapeEditing())
    }

    fun setSlotConfig(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        mode: GestureTriggerMode,
        handleId: String,
    ) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            applyLandscapeOptimistic(settings) {
                // 组合手势的短滑/长滑是两档独立配置：只写目标档位，不做任何连带写入。
                it.withSlotConfigSynced(side, trigger, action, mode, handleId)
            }
        },
    ) {
        settingsRepository.setSlotConfig(
            side,
            trigger,
            action,
            mode,
            handleId,
            landscapeEditing(),
        )
    }

    fun setDefaultTriggerMode(side: PanelSide, mode: GestureTriggerMode, handleId: String) =
        launchOptimisticSettingsWrite(
            optimisticUpdate = { settings ->
                applyLandscapeOptimistic(settings) {
                    it.withDefaultTriggerModeSynced(side, mode, handleId)
                }
            },
        ) {
            settingsRepository.setDefaultTriggerMode(side, mode, handleId, landscapeEditing())
        }

    fun setTriggerAlignOppositeGestures(handleId: String, sourceSide: PanelSide, enabled: Boolean) =
        launchOptimisticSettingsWrite(
            optimisticUpdate = { settings ->
                applyLandscapeOptimistic(settings) { working ->
                    var updated = working.withTriggerAlignOppositeGestures(handleId, enabled)
                    if (enabled && sourceSide.isHorizontalEdge) {
                        updated = updated.withGestureSlotsMirroredFromSide(sourceSide, handleId)
                    }
                    updated
                }
            },
        ) {
            settingsRepository.setTriggerAlignOppositeGestures(
                handleId,
                sourceSide,
                enabled,
                landscapeEditing(),
            )
        }

    fun setShortSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) = launchSettingsWrite {
        settingsRepository.setShortSwipeDistanceDp(side, handleId, value, landscapeEditing())
    }

    fun setLongSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) = launchSettingsWrite {
        settingsRepository.setLongSwipeDistanceDp(side, handleId, value, landscapeEditing())
    }

    fun setSwipeHoverDurationMs(value: Int) = launchSettingsWrite {
        settingsRepository.setSwipeHoverDurationMs(value)
    }

    fun setTriggerDoubleTapIntervalMs(value: Int) = launchSettingsWrite {
        settingsRepository.setTriggerDoubleTapIntervalMs(value)
    }

    fun setEdgeTriggerWidthDp(side: PanelSide, value: Float) = launchSettingsWrite {
        settingsRepository.setEdgeTriggerWidthDp(side, value)
    }

    fun setTriggerEdgeWidthDp(side: PanelSide, handleId: String, value: Float) = launchSettingsWrite {
        settingsRepository.setTriggerEdgeWidthDp(side, handleId, value, landscapeEditing())
    }

    fun setTriggerVerticalRange(side: PanelSide, handleId: String, top: Float, bottom: Float) =
        launchSettingsWrite {
            settingsRepository.setTriggerVerticalRange(side, handleId, top, bottom, landscapeEditing())
        }

    fun setTriggerHandleEnabled(side: PanelSide, handleId: String, enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setTriggerHandleEnabled(side, handleId, enabled, landscapeEditing())
    }

    fun setTriggerAlignOppositeSide(handleId: String, sourceSide: PanelSide, enabled: Boolean) =
        launchSettingsWrite {
            settingsRepository.setTriggerAlignOppositeSide(
                handleId = handleId,
                sourceSide = sourceSide,
                enabled = enabled,
                landscape = landscapeEditing(),
            )
        }

    fun setTriggerAlignOppositeDesign(handleId: String, sourceSide: PanelSide, enabled: Boolean) =
        launchSettingsWrite {
            settingsRepository.setTriggerAlignOppositeDesign(
                handleId = handleId,
                sourceSide = sourceSide,
                enabled = enabled,
                landscape = landscapeEditing(),
            )
        }

    fun setInterceptSystemBackGesture(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setInterceptSystemBackGesture(enabled)
    }

    fun setSystemGestureTakeoverTop(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSystemGestureTakeoverTop(enabled)
    }

    fun setSystemGestureTakeoverSides(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSystemGestureTakeoverSides(enabled)
    }

    fun setSystemGestureTakeoverBottom(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSystemGestureTakeoverBottom(enabled)
    }

    fun setLimitMaxInterceptLength(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setLimitMaxInterceptLength(enabled)
    }

    fun setTriggerHandleDesign(side: PanelSide, handleId: String, design: TriggerHandleDesign) =
        launchRepositoryWrite {
            triggerDesignWriteMutex.withLock {
                settingsRepository.setTriggerHandleDesign(side, handleId, design, landscapeEditing())
            }
        }

    fun applyTriggerDesignPreset(side: PanelSide, handleId: String, preset: TriggerDesignPreset) =
        launchRepositoryWrite {
            triggerDesignWriteMutex.withLock {
                settingsRepository.applyTriggerDesignPreset(side, handleId, preset, landscapeEditing())
            }
        }

    suspend fun saveGestureAngles(angles: GestureAngles): Boolean =
        settingsRepository.setGestureAngles(angles)
            .onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
            .isSuccess

    fun setGestureHintStyle(style: GestureHintStyle) = launchSettingsWrite {
        settingsRepository.setGestureHintStyle(style)
    }

    fun setGestureHintFingerOffsetDp(value: Float) = launchSettingsWrite {
        settingsRepository.setGestureHintFingerOffsetDp(value)
    }

    fun updateWaveStyle(style: WaveStyle) = launchSettingsWrite {
        settingsRepository.updateWaveStyle(style)
    }

    fun updateCapsuleStyle(style: CapsuleStyle) = launchSettingsWrite {
        settingsRepository.updateCapsuleStyle(style)
    }

    fun updateBubbleStyle(style: BubbleStyle) = launchSettingsWrite {
        settingsRepository.updateBubbleStyle(style)
    }

    fun setAndroidBackColorSource(source: AndroidBackColorSource) = launchSettingsWrite {
        settingsRepository.setAndroidBackColorSource(source)
    }

    fun setCornerGestureEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureEnabled(enabled)
    }

    fun setCornerGestureLeftEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureLeftEnabled(enabled)
    }

    fun setCornerGestureRightEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureRightEnabled(enabled)
    }

    fun setCornerGestureVerticalEdgeWidthDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureVerticalEdgeWidthDp(value)
    }

    fun setCornerGestureVerticalEdgeHeightDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureVerticalEdgeHeightDp(value)
    }

    fun setCornerGestureHorizontalEdgeWidthDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureHorizontalEdgeWidthDp(value)
    }

    fun setCornerGestureHorizontalEdgeHeightDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureHorizontalEdgeHeightDp(value)
    }

    fun setCornerGestureTriggerSlopDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureTriggerSlopDp(value)
    }

    fun setCornerGestureHideInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureHideInLandscape(enabled)
    }

    fun setCornerGestureLandscapePreventFalseTouch(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureLandscapePreventFalseTouch(enabled)
    }

    fun setCornerGestureOverrideSystemNav(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureOverrideSystemNav(enabled)
    }

    fun setCornerGestureInnerDiameterDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureInnerDiameterDp(value)
    }

    fun setCornerGestureRingSpacingDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureRingSpacingDp(value)
    }

    fun setCornerGestureBubbleSizeDp(value: Float) = launchSettingsWrite {
        settingsRepository.setCornerGestureBubbleSizeDp(value)
    }

    fun setCornerGestureWheelLayerCount(value: Int) = launchSettingsWrite {
        settingsRepository.setCornerGestureWheelLayerCount(value)
    }

    fun setCornerGestureCancelOutsideWheel(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureCancelOutsideWheel(enabled)
    }

    fun setCornerGestureProgressiveLayers(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureProgressiveLayers(enabled)
    }

    fun setCornerGestureSlotHaptic(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureSlotHaptic(enabled)
    }

    fun setCornerGestureShowSelectedName(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureShowSelectedName(enabled)
    }

    fun setCornerGestureShowEditButton(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureShowEditButton(enabled)
    }

    fun setCornerGestureSelectedHintIconSizeDp(value: Int) = launchSettingsWrite {
        settingsRepository.setCornerGestureSelectedHintIconSizeDp(value)
    }

    fun setCornerGestureBackgroundStyle(style: Int) = launchSettingsWrite {
        settingsRepository.setCornerGestureBackgroundStyle(style)
    }

    fun setCornerGestureBlurDp(value: Int) = launchSettingsWrite {
        settingsRepository.setCornerGestureBlurDp(value)
    }

    fun setCornerGestureDimPercent(value: Int) = launchSettingsWrite {
        settingsRepository.setCornerGestureDimPercent(value)
    }

    fun setCornerGestureUnifiedSlots(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCornerGestureUnifiedSlots(enabled)
    }

    fun setCornerGestureInnerZoneAction(action: GestureAction) = launchSettingsWrite {
        settingsRepository.setCornerGestureInnerZoneAction(action)
    }

    fun setCornerGestureLeftSlotAction(index: Int, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setCornerGestureLeftSlotAction(index, action)
    }

    fun setCornerGestureRightSlotAction(index: Int, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setCornerGestureRightSlotAction(index, action)
    }

    fun setCornerSlotAction(corner: String, slotIndex: Int, action: GestureAction) = launchSettingsWrite {
        val overlay = settingsRepository.overlaySettings.first()
        val unified = overlay.cornerGestureSettings.unifiedSlots
        when {
            unified -> settingsRepository.setCornerGestureLeftSlotAction(slotIndex, action)
            corner == "right" -> settingsRepository.setCornerGestureRightSlotAction(slotIndex, action)
            else -> settingsRepository.setCornerGestureLeftSlotAction(slotIndex, action)
        }
    }

    fun setCornerSlotSubMenu(corner: String, slotIndex: Int, config: CornerSlotSubMenuConfig) = launchSettingsWrite {
        val overlay = settingsRepository.overlaySettings.first()
        val unified = overlay.cornerGestureSettings.unifiedSlots
        val isLeft = unified || corner != "right"
        settingsRepository.setCornerSlotSubMenu(isLeft, slotIndex, config)
    }

    fun setFingertipRingSlotCount(count: Int) = launchSettingsWrite {
        settingsRepository.setFingertipRingSlotCount(count)
    }

    fun setFingertipRingSlotAction(index: Int, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFingertipRingSlotAction(index, action)
    }

    fun setFingertipRingOrbitRadiusPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFingertipRingOrbitRadiusPx(value)
    }

    fun setFingertipRingIconSizePx(value: Float) = launchSettingsWrite {
        settingsRepository.setFingertipRingIconSizePx(value)
    }

    fun setAppCarouselSwitcherSettings(settings: AppCarouselSwitcherSettings) = launchSettingsWrite {
        settingsRepository.setAppCarouselSwitcherSettings(settings)
    }
}
