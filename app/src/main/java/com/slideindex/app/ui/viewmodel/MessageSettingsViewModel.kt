package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessageOverlayPort
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class MessageSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    private val overlayPort: MessageOverlayPort,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setMessageReminderEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageReminderEnabled(enabled)
    }

    fun setMessageInterceptNotifications(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageInterceptNotifications(enabled)
    }

    fun setMessageHideInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageHideInLandscape(enabled)
    }

    fun setMessagePortraitDanmaku(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessagePortraitDanmaku(enabled)
    }

    fun setMessageLandscapeDanmaku(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageLandscapeDanmaku(enabled)
    }

    fun setMessageGestureAction(slot: String, action: MessageAction) = launchSettingsWrite {
        settingsRepository.setMessageGestureAction(slot, action)
    }

    fun setMessageSuppressWhenSystemDnd(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageSuppressWhenSystemDnd(enabled)
    }

    fun setMessageOpenLastOnUnlock(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageOpenLastOnUnlock(enabled)
    }

    fun setMessageKeepFloatIconAfterUnlock(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageKeepFloatIconAfterUnlock(enabled)
    }

    fun setMessageUnlockConfirmationAutoDismissSeconds(seconds: Int) = launchSettingsWrite {
        settingsRepository.setMessageUnlockConfirmationAutoDismissSeconds(seconds)
    }

    fun setMessageUnlockOpenDelayMs(millis: Int) = launchSettingsWrite {
        settingsRepository.setMessageUnlockOpenDelayMs(millis)
    }

    fun setMessageOpenLastAlways(packageName: String, enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageOpenLastAlways(packageName, enabled)
    }

    fun addMessageEnabledPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.addMessageEnabledPackage(packageName)
    }

    fun removeMessageEnabledPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.removeMessageEnabledPackage(packageName)
    }

    fun upsertMessageAppFilterRule(rule: MessageAppFilterRule) = launchSettingsWrite {
        settingsRepository.upsertMessageAppFilterRule(rule)
    }

    fun addMessageDndPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.addMessageDndPackage(packageName)
    }

    fun removeMessageDndPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.removeMessageDndPackage(packageName)
    }

    fun setMessageFloatIconEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageFloatIconEnabled(enabled)
    }

    fun setMessageSideBubbleEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageSideBubbleEnabled(enabled)
    }

    fun setMessageStyleId(styleId: String) = launchSettingsWrite {
        settingsRepository.setMessageStyleId(styleId)
    }

    fun setMessageThemeId(themeId: String) = launchSettingsWrite {
        settingsRepository.setMessageThemeId(themeId)
    }

    fun setMessageSideThemeId(themeId: String) = launchSettingsWrite {
        settingsRepository.setMessageSideThemeId(themeId)
    }

    fun setMessagePrimaryStyleEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessagePrimaryStyleEnabled(enabled)
    }

    fun setMessageDanmakuEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuEnabled(enabled)
    }

    fun setMessageCNoticeEnabled(enabled: Boolean) = launchSettingsWrite {
        val result = settingsRepository.setMessageCNoticeEnabled(enabled)
        if (result.isSuccess && !enabled) {
            overlayPort.dismissImmediate(MessageStyle.CNotice)
        }
        result
    }

    fun setMessageCNoticeOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeOpacity(opacity)
    }

    fun setMessageCNoticeDimmedOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeDimmedOpacity(opacity)
    }

    fun setMessageCNoticeGhostOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeGhostOpacity(opacity)
    }

    fun setMessageCNoticeIconSizeDp(sizeDp: Float) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeIconSizeDp(sizeDp)
    }

    fun setMessageCNoticeMaxCount(count: Int) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeMaxCount(count)
    }

    fun setMessageCNoticeAutoDismissSeconds(seconds: Int) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeAutoDismissSeconds(seconds)
    }

    fun setMessageCNoticeDefaultCollapsed(collapsed: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeDefaultCollapsed(collapsed)
    }

    fun setMessageCNoticePeekBannerEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageCNoticePeekBannerEnabled(enabled)
    }

    fun setMessageCNoticeLandscapeEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeLandscapeEnabled(enabled)
    }

    fun setMessageCNoticeHorizontalEdge(edge: String) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeHorizontalEdge(edge)
    }

    fun setMessageCNoticeYFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeYFraction(fraction)
    }

    fun setMessageCNoticeEdgeMarginDp(marginDp: Float) = launchSettingsWrite {
        settingsRepository.setMessageCNoticeEdgeMarginDp(marginDp)
    }

    fun setMessageEdgeCardEnabled(enabled: Boolean) = launchSettingsWrite {
        val result = settingsRepository.setMessageEdgeCardEnabled(enabled)
        if (result.isSuccess && !enabled) {
            overlayPort.dismissImmediate(MessageStyle.EdgeCard)
        }
        result
    }

    fun setMessageEdgeCardOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageEdgeCardOpacity(opacity)
    }

    fun setMessageEdgeCardAutoCollapseMs(collapseMs: Int) = launchSettingsWrite {
        settingsRepository.setMessageEdgeCardAutoCollapseMs(collapseMs)
    }

    fun setMessageEdgeCardHorizontalEdge(edge: String) = launchSettingsWrite {
        settingsRepository.setMessageEdgeCardHorizontalEdge(edge)
    }

    fun setMessageEdgeCardYFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setMessageEdgeCardYFraction(fraction)
    }

    fun setMessageEdgeCardMaxCount(count: Int) = launchSettingsWrite {
        settingsRepository.setMessageEdgeCardMaxCount(count)
    }

    fun setMessageEdgeCardMaxLines(lines: Int) = launchSettingsWrite {
        settingsRepository.setMessageEdgeCardMaxLines(lines)
    }

    fun setMessageEdgeCardKeepAcrossLock(keep: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageEdgeCardKeepAcrossLock(keep)
    }

    fun setMessageEdgeCardCollapseWhenScrollDone(collapse: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageEdgeCardCollapseWhenScrollDone(collapse)
    }

    fun setMessageDanmakuThemeId(themeId: String) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuThemeId(themeId)
    }

    fun setMessageFloatIconOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageFloatIconOpacity(opacity)
    }

    fun setMessageSideBubbleOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageSideBubbleOpacity(opacity)
    }

    fun setMessageDanmakuOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuOpacity(opacity)
    }

    fun setMessageDanmakuMaxLines(lines: Int) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuMaxLines(lines)
    }

    fun setMessageSideMaxCount(count: Int) = launchSettingsWrite {
        settingsRepository.setMessageSideMaxCount(count)
    }

    fun setMessageSideMaxWidthDp(width: Float) = launchSettingsWrite {
        settingsRepository.setMessageSideMaxWidthDp(width)
    }

    fun setMessageSideMaxLines(lines: Int) = launchSettingsWrite {
        settingsRepository.setMessageSideMaxLines(lines)
    }

    fun setMessageFloatIconAutoDismissSeconds(seconds: Int) = launchSettingsWrite {
        settingsRepository.setMessageFloatIconAutoDismissSeconds(seconds)
    }

    fun setMessageSideBubbleAutoDismissSeconds(seconds: Int) = launchSettingsWrite {
        settingsRepository.setMessageSideBubbleAutoDismissSeconds(seconds)
    }

    fun setMessageReturnChipEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageReturnChipEnabled(enabled)
    }

    fun setMessageReturnChipAutoDismissSeconds(seconds: Int) = launchSettingsWrite {
        settingsRepository.setMessageReturnChipAutoDismissSeconds(seconds)
    }

    fun setMessageFloatIconSizeDp(sizeDp: Float) = launchSettingsWrite {
        settingsRepository.setMessageFloatIconSizeDp(sizeDp)
    }

    fun setMessageSideHorizontalEdge(edge: String) = launchSettingsWrite {
        settingsRepository.setMessageSideHorizontalEdge(edge)
    }

    fun setMessageSideVerticalAnchor(anchor: String) = launchSettingsWrite {
        settingsRepository.setMessageSideVerticalAnchor(anchor)
    }

    fun setMessageSideBubbleYFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setMessageSideBubbleYFraction(fraction)
    }

    fun setMessageFloatIconCorner(corner: MessageOverlayCorner) = launchSettingsWrite {
        settingsRepository.setMessageFloatIconCorner(corner.id)
    }

    fun setMessageFloatIconYFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setMessageFloatIconYFraction(fraction)
    }

    fun setMessageSideFontSizeLevel(level: Int) = launchSettingsWrite {
        settingsRepository.setMessageSideFontSizeLevel(level)
    }

    fun setMessageDanmakuSpeedLevel(level: Int) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuSpeedLevel(level)
    }
}
