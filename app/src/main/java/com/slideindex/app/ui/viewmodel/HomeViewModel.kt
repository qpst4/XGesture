package com.slideindex.app.ui.viewmodel

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.slideindex.app.settings.AppColorSpec
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.AppUiLanguage
import com.slideindex.app.settings.BottomNavBlurDefaults
import com.slideindex.app.settings.BottomNavMode
import com.slideindex.app.settings.BottomNavStyle
import com.slideindex.app.settings.DarkBackgroundStyle
import com.slideindex.app.settings.KeyboardTriggerBehavior
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.settings.UiDensityScaleLimits
import com.slideindex.app.ui.feedback.UserMessageBus

interface HomeScreenEffects {
    fun refreshServiceState()
    fun requestNotificationPermission()
    fun requestShizuku()
    fun openAccessibilitySettings()
    fun previewHaptic(enabled: Boolean = true, strengthLevel: Int? = null)
}

@HiltViewModel(assistedFactory = HomeViewModel.Factory::class)
class HomeViewModel @AssistedInject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    @Assisted private val effects: HomeScreenEffects,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setServiceEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setServiceEnabled(enabled).also { result ->
            if (result.isSuccess) {
                effects.refreshServiceState()
            }
        }
    }

    fun setHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticEnabled(enabled).also { result ->
            if (result.isSuccess && enabled) {
                effects.previewHaptic()
            }
        }
    }

    fun setHapticStrength(level: Int) = launchSettingsWrite {
        settingsRepository.setHapticStrengthLevel(level).also { result ->
            if (result.isSuccess) {
                effects.previewHaptic(strengthLevel = level)
            }
        }
    }

    fun setHapticShortThresholdEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticShortThresholdEnabled(enabled)
    }

    fun setHapticCompoundTurnEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticCompoundTurnEnabled(enabled)
    }

    fun setHapticReturnSwipeEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticReturnSwipeEnabled(enabled)
    }

    fun setHapticHoverEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticHoverEnabled(enabled)
    }

    fun setHapticLongPressEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticLongPressEnabled(enabled)
    }

    fun setHapticLongThresholdEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticLongThresholdEnabled(enabled)
    }

    fun setHapticSlotLetterEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticSlotLetterEnabled(enabled)
    }

    fun setHapticSlotAppEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticSlotAppEnabled(enabled)
    }

    fun setHapticActionConfirmEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticActionConfirmEnabled(enabled)
    }

    fun setHapticSlotSelectionEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticSlotSelectionEnabled(enabled)
    }

    fun setHapticConfirmLaunchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHapticConfirmLaunchEnabled(enabled)
    }

    fun setGestureHintEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setGestureHintEnabled(enabled)
    }

    fun setHideTriggerInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHideTriggerInLandscape(enabled)
    }

    fun setHideTriggerOnLockScreen(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHideTriggerOnLockScreen(enabled)
    }

    fun setHideTriggerOnLauncher(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setHideTriggerOnLauncher(enabled)
    }

    fun setKeyboardTriggerBehavior(behavior: KeyboardTriggerBehavior, landscape: Boolean) = launchSettingsWrite {
        if (landscape) {
            settingsRepository.setKeyboardTriggerBehaviorLandscape(behavior)
        } else {
            settingsRepository.setKeyboardTriggerBehaviorPortrait(behavior)
        }
    }

    fun setKeyboardTriggerNarrowPercent(percent: Int, landscape: Boolean) = launchSettingsWrite {
        if (landscape) {
            settingsRepository.setKeyboardTriggerNarrowPercentLandscape(percent)
        } else {
            settingsRepository.setKeyboardTriggerNarrowPercentPortrait(percent)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setDynamicColorEnabled(enabled)
    }

    fun setThemeColor(color: Int) = launchSettingsWrite {
        settingsRepository.setThemeColor(color)
    }

    fun setThemePaletteStyle(style: ThemePaletteStyle) = launchSettingsWrite {
        settingsRepository.setThemePaletteStyle(style)
    }

    fun setThemeMode(mode: AppThemeMode) = launchSettingsWrite {
        settingsRepository.setThemeMode(mode)
    }

    fun setCustomColorEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setCustomColorEnabled(enabled)
    }

    fun setDarkBackgroundStyle(style: DarkBackgroundStyle) = launchSettingsWrite {
        settingsRepository.setDarkBackgroundStyle(style)
    }

    fun setIconPlateMode(mode: com.slideindex.app.settings.IconPlateMode) = launchSettingsWrite {
        com.slideindex.app.ui.IconPlateModeState.mode = mode
        // 清图标缓存并让两个悬浮窗立即重绘，否则面板上还是旧底色
        runCatching { com.slideindex.app.util.GestureActionIconBitmap.clear() }
        runCatching { com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow.refreshFromSettings() }
        settingsRepository.setIconPlateMode(mode)
    }

    fun setThemeColorSpec(spec: AppColorSpec) = launchSettingsWrite {
        settingsRepository.setThemeColorSpec(spec)
    }

    fun setBottomNavStyle(style: BottomNavStyle) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings -> settings.copy(bottomNavStyleId = style.id) },
        block = { settingsRepository.setBottomNavStyle(style) },
    )

    fun setBottomNavMode(mode: BottomNavMode) = launchSettingsWrite {
        settingsRepository.setBottomNavMode(mode)
    }

    fun setBottomNavGlassEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setBottomNavGlassEnabled(enabled)
    }

    fun setOverlayBlurEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOverlayBlurEnabled(enabled)
    }

    fun setTopAppBarBlurStyle(style: TopAppBarBlurStyle) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings -> settings.copy(topAppBarBlurStyleId = style.id) },
        block = { settingsRepository.setTopAppBarBlurStyle(style) },
    )

    fun setPredictiveBackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setPredictiveBackEnabled(enabled)
    }

    fun setAppUiLanguage(language: AppUiLanguage) = launchSettingsWrite {
        settingsRepository.setAppUiLanguage(language)
    }

    fun setSwipeDismissEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSwipeDismissEnabled(enabled)
    }

    fun setUiDensityScale(scale: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(uiDensityScale = UiDensityScaleLimits.normalize(scale))
        },
        block = { settingsRepository.setUiDensityScale(scale) },
    )

    fun setBottomNavBlurRadiusDp(value: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            val coerced = value.coerceIn(
                BottomNavBlurDefaults.MIN_RADIUS_DP,
                BottomNavBlurDefaults.MAX_RADIUS_DP,
            )
            when (BottomNavStyle.fromId(settings.bottomNavStyleId)) {
                BottomNavStyle.CLASSIC -> settings.copy(bottomNavClassicBlurRadiusDp = coerced)
                BottomNavStyle.LIQUID_GLASS -> settings.copy(bottomNavLiquidGlassBlurRadiusDp = coerced)
                BottomNavStyle.FLOATING_NAV -> settings.copy(bottomNavFloatingNavBlurRadiusDp = coerced)
            }
        },
        block = {
            settingsRepository.setBottomNavBlurRadiusDp(value)
        },
    )

    fun requestNotificationPermission() = effects.requestNotificationPermission()

    fun requestShizuku() = effects.requestShizuku()

    fun openAccessibilitySettings() = effects.openAccessibilitySettings()

    fun setPrivilegeMode(mode: PrivilegeMode) = launchSettingsWrite {
        settingsRepository.setPrivilegeMode(mode)
    }

    @AssistedFactory
    interface Factory {
        fun create(effects: HomeScreenEffects): HomeViewModel
    }
}
