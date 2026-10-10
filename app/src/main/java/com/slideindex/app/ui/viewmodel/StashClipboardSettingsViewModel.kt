package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.HistoryFloatLifecycle
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardFloatEntryClickAction
import com.slideindex.app.settings.ClipboardFloatEntryLongPressAction
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.ClipboardMonitoringCapture
import com.slideindex.app.settings.ClipboardMonitoringChannel
import com.slideindex.app.settings.ClipboardOverlayScale
import com.slideindex.app.settings.HistoryFloatHandleWidth
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.StashPanelHandleOpenTab
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StashClipboardSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    val clipboardHistoryRepository: ClipboardHistoryRepository,
    val stashRepository: StashRepository,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setClipboardBackgroundMonitoring(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardBackgroundMonitoring = enabled)) },
    ) {
        settingsRepository.setClipboardBackgroundMonitoring(enabled).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardBackgroundMonitoringMode(mode: ClipboardMonitoringMode) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardBackgroundMonitoringMode = mode)) },
    ) {
        settingsRepository.setClipboardBackgroundMonitoringMode(mode).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardOverlayEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardOverlayEnabled = enabled)) },
    ) {
        settingsRepository.setClipboardOverlayEnabled(enabled).also { result ->
            if (result.isSuccess && !enabled) {
                com.slideindex.app.clipboardoverlay.ClipboardOverlayWindow.dismiss()
            }
        }
    }

    fun setClipboardOverlayScalePercent(percent: Int) = launchOptimisticSettingsWrite(
        optimisticUpdate = {
            it.copy(
                clipboard = it.clipboard.copy(
                    clipboardOverlayScalePercent = ClipboardOverlayScale.coerce(percent),
                ),
            )
        },
    ) {
        settingsRepository.setClipboardOverlayScalePercent(percent)
    }

    fun setClipboardScreenshotMonitoring(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardScreenshotMonitoring = enabled)) },
    ) {
        settingsRepository.setClipboardScreenshotMonitoring(enabled).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardPasteFvStyleEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardPasteFvStyleEnabled = enabled)) },
    ) {
        settingsRepository.setClipboardPasteFvStyleEnabled(enabled)
    }

    fun setClipboardHistoryMaxEntries(maxEntries: Int) = launchSettingsWrite {
        settingsRepository.setClipboardHistoryMaxEntries(maxEntries).also { result ->
            if (result.isSuccess) {
                clipboardHistoryRepository.trimToConfiguredMax()
            }
        }
    }

    fun setClipboardHistoryFloatEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardHistoryFloatEnabled = enabled)) },
    ) {
        settingsRepository.setClipboardHistoryFloatEnabled(enabled).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun setClipboardHistoryFloatLockPosition(lock: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardHistoryFloatLockPosition = lock)) },
    ) {
        settingsRepository.setClipboardHistoryFloatLockPosition(lock).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.applyRuntimeConfig(
                    context = appContext,
                    handleWidthDp = settingsRepository.readSnapshot().clipboardHistoryFloatHandleWidthDp,
                    lockPosition = lock,
                    landscapeEnabled = settingsRepository.readSnapshot().clipboardHistoryFloatEnabledLandscape,
                )
            }
        }
    }

    fun setClipboardHistoryFloatHandleWidthDp(widthDp: Int) = launchOptimisticSettingsWrite(
        optimisticUpdate = {
            it.copy(
                clipboard = it.clipboard.copy(
                    clipboardHistoryFloatHandleWidthDp = HistoryFloatHandleWidth.coerce(widthDp),
                ),
            )
        },
    ) {
        settingsRepository.setClipboardHistoryFloatHandleWidthDp(widthDp).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.applyRuntimeConfig(
                    context = appContext,
                    handleWidthDp = widthDp,
                    lockPosition = settingsRepository.readSnapshot().clipboardHistoryFloatLockPosition,
                    landscapeEnabled = settingsRepository.readSnapshot().clipboardHistoryFloatEnabledLandscape,
                )
            }
        }
    }

    fun setClipboardHistoryFloatEnabledLandscape(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardHistoryFloatEnabledLandscape = enabled)) },
    ) {
        settingsRepository.setClipboardHistoryFloatEnabledLandscape(enabled).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.applyRuntimeConfig(
                    context = appContext,
                    handleWidthDp = settingsRepository.readSnapshot().clipboardHistoryFloatHandleWidthDp,
                    lockPosition = settingsRepository.readSnapshot().clipboardHistoryFloatLockPosition,
                    landscapeEnabled = enabled,
                )
            }
        }
    }

    fun setStashPanelBackgroundBlurEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(stashPanelBackgroundBlurEnabled = enabled)) },
    ) {
        settingsRepository.setStashPanelBackgroundBlurEnabled(enabled)
    }

    fun setStashPanelBackgroundBlurRadiusDp(value: Int) = launchOptimisticSettingsWrite(
        optimisticUpdate = {
            it.copy(
                clipboard = it.clipboard.copy(
                    stashPanelBackgroundBlurRadiusDp = value.coerceIn(
                        AppSettings.STASH_PANEL_BLUR_RADIUS_MIN_DP,
                        AppSettings.STASH_PANEL_BLUR_RADIUS_MAX_DP,
                    ),
                ),
            )
        },
    ) {
        settingsRepository.setStashPanelBackgroundBlurRadiusDp(value)
    }

    fun setStashPanelHandleOpenTab(tab: StashPanelHandleOpenTab) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(stashPanelHandleOpenTab = tab)) },
    ) {
        settingsRepository.setStashPanelHandleOpenTab(tab)
    }

    fun syncHistoryFloatFromSettings() {
        viewModelScope.launch {
            HistoryFloatLifecycle.syncFromSettings(appContext, settingsRepository)
        }
    }

    fun setClipboardFloatEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatEnabled = enabled)) },
    ) {
        settingsRepository.setClipboardFloatEnabled(enabled).also { result ->
            if (result.isSuccess) {
                ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun setClipboardFloatShowChip(showChip: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatShowChip = showChip)) },
    ) {
        settingsRepository.setClipboardFloatShowChip(showChip).also { result ->
            if (result.isSuccess) {
                ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun setClipboardFloatPinPosition(pin: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatPanelPinPosition = pin)) },
    ) {
        settingsRepository.setClipboardFloatPinPosition(pin)
    }

    fun setClipboardFloatSingleLineEntryClickAction(action: ClipboardFloatEntryClickAction) = launchOptimisticSettingsWrite(
        optimisticUpdate = {
            it.copy(clipboard = it.clipboard.copy(clipboardFloatSingleLineEntryClickAction = action))
        },
    ) {
        settingsRepository.setClipboardFloatSingleLineEntryClickAction(action)
    }

    fun setClipboardFloatSingleLineEntryLongPressAction(action: ClipboardFloatEntryLongPressAction) =
        launchOptimisticSettingsWrite(
            optimisticUpdate = {
                it.copy(clipboard = it.clipboard.copy(clipboardFloatSingleLineEntryLongPressAction = action))
            },
        ) {
            settingsRepository.setClipboardFloatSingleLineEntryLongPressAction(action)
        }

    fun setClipboardFloatCardEntryClickAction(action: ClipboardFloatEntryClickAction) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatCardEntryClickAction = action)) },
    ) {
        settingsRepository.setClipboardFloatCardEntryClickAction(action)
    }

    fun setClipboardFloatCardEntryLongPressAction(action: ClipboardFloatEntryLongPressAction) =
        launchOptimisticSettingsWrite(
            optimisticUpdate = {
                it.copy(clipboard = it.clipboard.copy(clipboardFloatCardEntryLongPressAction = action))
            },
        ) {
            settingsRepository.setClipboardFloatCardEntryLongPressAction(action)
        }

    fun setClipboardFloatListStyle(style: com.slideindex.app.settings.ClipboardFloatListStyle) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatListStyleId = style.id)) },
    ) {
        settingsRepository.setClipboardFloatListStyle(style)
    }

    fun setClipboardFloatPasteHapticEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatPasteHapticEnabled = enabled)) },
    ) {
        settingsRepository.setClipboardFloatPasteHapticEnabled(enabled)
    }

    fun setClipboardFloatAlpha(alpha: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatAlpha = alpha)) },
    ) {
        settingsRepository.setClipboardFloatAlpha(alpha)
    }

    fun setClipboardFloatAutoDimWhenUnfocused(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatAutoDimWhenUnfocused = enabled)) },
    ) {
        settingsRepository.setClipboardFloatAutoDimWhenUnfocused(enabled)
    }

    fun setClipboardFloatAutoCloseSeconds(seconds: Int) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatAutoCloseSeconds = seconds)) },
    ) {
        settingsRepository.setClipboardFloatAutoCloseSeconds(seconds)
    }

    fun addClipboardFloatBlockedPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.addClipboardFloatBlockedPackage(packageName).also { result ->
            if (result.isSuccess) {
                ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun removeClipboardFloatBlockedPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.removeClipboardFloatBlockedPackage(packageName).also { result ->
            if (result.isSuccess) {
                ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun setClipboardMonitoringChannel(channel: ClipboardMonitoringChannel) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardMonitoringChannel = channel)) },
    ) {
        settingsRepository.setClipboardMonitoringChannel(channel).also { result ->
            // 通道变了要让监听按新模式重启，否则状态行与常驻通知还停在旧通道。
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardMonitoringCapture(capture: ClipboardMonitoringCapture) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardMonitoringCapture = capture)) },
    ) {
        settingsRepository.setClipboardMonitoringCapture(capture).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun addClipboardLsposedWhitelistPackage(packageName: String) = launchSettingsWrite {
        // 白名单变化由 ModuleHookConfigSync 的签名比对自动重新下发，无需额外同步。
        settingsRepository.addClipboardLsposedWhitelistPackage(packageName)
    }

    fun removeClipboardLsposedWhitelistPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.removeClipboardLsposedWhitelistPackage(packageName)
    }

    fun resetClipboardFloatLayout() = launchSettingsWrite {
        settingsRepository.resetClipboardFloatGeometry()
    }

    fun syncClipboardFloatFromSettings() {
        viewModelScope.launch {
            ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
        }
    }

    fun clearClipboardHistory() = launchRepositoryWrite {
        runCatching { clipboardHistoryRepository.clearAll() }
    }

    fun clearStash() = launchRepositoryWrite {
        runCatching { stashRepository.clearAll() }
    }

    private fun restartMonitoring() {
        clipboardHistoryRepository.restartClipboardMonitoringFromSettings()
        com.slideindex.app.overlay.OverlayStatePort.sendCommand(
            appContext,
            com.slideindex.app.overlay.OverlayStatePort.COMMAND_SYNC_SCREENSHOT_MONITORING,
        )
    }
}
