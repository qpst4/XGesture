package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.settings.FreeWindowUiSettings
import com.slideindex.app.settings.GestureSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.settings.KeepAliveUiSettings
import com.slideindex.app.settings.OtpUiSettings
import com.slideindex.app.settings.OverlaySettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.ShakeUiSettings
import com.slideindex.app.settings.ThemeSettings
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.shake.ShakeWorkMode
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class SettingsViewModel(
    protected val settingsRepository: SettingsRepository,
    protected val userMessageBus: UserMessageBus,
    protected val appContext: Context,
) : ViewModel() {
    private val optimisticTransform = MutableStateFlow<((AppSettings) -> AppSettings)?>(null)

    val settings: StateFlow<AppSettings> = combine(
        settingsRepository.settings,
        optimisticTransform,
    ) { repositorySettings, transform ->
        mergeOptimisticSettings(repositorySettings, transform)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settingsRepository.readSnapshot(),
    )

    private val sliceFlows = mutableMapOf<String, StateFlow<*>>()

    val gestureSettings: StateFlow<GestureSettings> by lazy {
        settingsSlice("gesture", GestureSettings::from)
    }

    val overlaySettings: StateFlow<OverlaySettings> by lazy {
        settingsSlice("overlay", OverlaySettings::from)
    }

    val themeSettings: StateFlow<ThemeSettings> by lazy {
        settingsSlice("theme") { settings ->
            ThemeSettings(
                themeColorArgb = settings.themeColorArgb,
                dynamicColorEnabled = settings.dynamicColorEnabled,
            )
        }
    }

    val homeMainSettings: StateFlow<HomeMainSettings> by lazy {
        settingsSlice("homeMain", HomeMainSettings::from)
    }

    val extensionHubSettings: StateFlow<ExtensionHubSettings> by lazy {
        settingsSlice("extensionHub", ExtensionHubSettings::from)
    }

    val keepAliveUiSettings: StateFlow<KeepAliveUiSettings> by lazy {
        settingsSlice("keepAlive", KeepAliveUiSettings::from)
    }

    val shakeUiSettings: StateFlow<ShakeUiSettings> by lazy {
        settingsSlice("shake", ShakeUiSettings::from)
    }

    val freeWindowUiSettings: StateFlow<FreeWindowUiSettings> by lazy {
        settingsSlice("freeWindow", FreeWindowUiSettings::from)
    }

    val otpUiSettings: StateFlow<OtpUiSettings> by lazy {
        settingsSlice("otp", OtpUiSettings::from)
    }

    val messageReminderSettings: StateFlow<MessageSettings> by lazy {
        settingsSlice("messageReminder") { it.messageReminderSettings }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T> settingsSlice(
        key: String,
        selector: (AppSettings) -> T,
    ): StateFlow<T> = sliceFlows.getOrPut(key) {
        settings
            .map(selector)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = selector(settings.value),
            )
    } as StateFlow<T>

    protected fun launchSettingsWrite(
        @StringRes failureMessageRes: Int = R.string.settings_save_failed,
        block: suspend () -> Result<Unit>,
    ) = launchRepositoryWrite(failureMessageRes, block)

    protected fun launchOptimisticSettingsWrite(
        optimisticUpdate: (AppSettings) -> AppSettings,
        @StringRes failureMessageRes: Int = R.string.settings_save_failed,
        block: suspend () -> Result<Unit>,
    ) {
        optimisticTransform.value = optimisticUpdate
        viewModelScope.launch {
            block()
                .onSuccess { optimisticTransform.value = null }
                .onFailure { error ->
                    optimisticTransform.value = null
                    if (error is CancellationException) return@onFailure
                    userMessageBus.showError(appContext.getString(failureMessageRes))
                }
        }
    }

    protected fun launchRepositoryWrite(
        @StringRes failureMessageRes: Int = R.string.settings_save_failed,
        block: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            block().onFailure { error ->
                if (error is CancellationException) return@onFailure
                userMessageBus.showError(appContext.getString(failureMessageRes))
            }
        }
    }
}

@HiltViewModel
class ShakeHubViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeGesturesEnabled(enabled)
    }

    fun setWorkModes(modes: Set<ShakeWorkMode>) {
        // 全部取消等于把晃动静默关掉，这里保留原配置并提示，避免“开了总开关却不响应”。
        if (modes.isEmpty()) {
            userMessageBus.showError(appContext.getString(R.string.shake_work_mode_keep_one))
            return
        }
        launchSettingsWrite {
            settingsRepository.setShakeWorkModes(modes)
        }
    }

    fun setBasicAction(type: ShakeGestureType, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setShakeGestureAction(type, action)
    }

    fun setIndependentAppShakeEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setIndependentAppShakeEnabled(enabled)
    }

    fun setGlobalSensitivity(value: Float) = launchSettingsWrite {
        settingsRepository.setShakeGlobalSensitivity(value)
    }

    fun setIndependentSensitivityEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeIndependentSensitivityEnabled(enabled)
    }

    fun setAnimationFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeAnimationFeedbackEnabled(enabled)
    }

    fun setVibrationFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeVibrationFeedbackEnabled(enabled)
    }

    fun setAnimationColor(color: Int) = launchSettingsWrite {
        settingsRepository.setShakeAnimationColor(color)
    }

    fun setDisableInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShakeDisableInLandscape(enabled)
    }

    fun addShakeBlacklistedApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addShakeBlacklistedApp(packageName)
    }

    fun removeShakeBlacklistedApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removeShakeBlacklistedApp(packageName)
    }

    fun setLockScreenShakeAction(type: ShakeGestureType, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setLockScreenShakeAction(type, action)
    }

    fun setShakeDirectionSensitivity(type: ShakeGestureType, value: Float) = launchSettingsWrite {
        settingsRepository.setShakeDirectionSensitivity(type, value)
    }

    fun addPerAppShakeConfig(packageName: String) = launchSettingsWrite {
        settingsRepository.addPerAppShakeConfig(packageName)
    }

    fun removePerAppShakeConfig(packageName: String) = launchSettingsWrite {
        settingsRepository.removePerAppShakeConfig(packageName)
    }

    fun setPerAppShakeAction(packageName: String, type: ShakeGestureType, action: GestureAction) =
        launchSettingsWrite {
            settingsRepository.setPerAppShakeAction(packageName, type, action)
        }

    fun setFaceDownEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFaceDownGestureEnabled(enabled)
    }

    fun setFaceDownAction(action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFaceDownGestureAction(action)
    }

    fun setFaceDownHoldDurationMs(value: Long) = launchSettingsWrite {
        settingsRepository.setFaceDownHoldDurationMs(value)
    }

    fun setFaceDownCooldownMs(value: Long) = launchSettingsWrite {
        settingsRepository.setFaceDownCooldownMs(value)
    }

    fun setFaceDownRequireProximity(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFaceDownRequireProximity(enabled)
    }

    fun setFaceDownDisableInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFaceDownDisableInLandscape(enabled)
    }

    fun setFaceDownVibrationFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFaceDownVibrationFeedbackEnabled(enabled)
    }

    fun setFaceDownAudioFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFaceDownAudioFeedbackEnabled(enabled)
    }

    fun setFaceDownAudioFeedbackVolume(value: Int) = launchSettingsWrite {
        settingsRepository.setFaceDownAudioFeedbackVolume(value)
    }
}
