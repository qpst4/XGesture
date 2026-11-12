package com.slideindex.app.settings

import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageAppFilterCodec
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessagePlacementFractions
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.message.SideBubbleVerticalAnchor
import com.slideindex.app.message.SideBubbleFontSize
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageSettingsMutator @Inject constructor(
    private val editor: SettingsPreferencesEditor,
) {
    suspend fun setMessageReminderEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_REMINDER_ENABLED] = enabled }

    suspend fun setMessageInterceptNotifications(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_INTERCEPT_NOTIFICATIONS] = enabled }

    suspend fun setMessageFloatIconEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_ENABLED] = enabled }

    suspend fun setMessageSideBubbleEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_ENABLED] = enabled }

    suspend fun setMessageSideThemeId(themeId: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_THEME_ID] = themeId }

    suspend fun setMessageSideHorizontalEdge(edge: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_HORIZONTAL_EDGE] = edge }

    suspend fun setMessageSideVerticalAnchor(anchor: String) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_SIDE_VERTICAL_ANCHOR] = anchor
            it[SettingsPreferenceKeys.MESSAGE_SIDE_Y_FRACTION] =
                SideBubbleVerticalAnchor.defaultYFraction(
                    SideBubbleVerticalAnchor.fromId(anchor),
                )
        }

    suspend fun setMessageSideBubbleYFraction(fraction: Float) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_SIDE_Y_FRACTION] =
                MessagePlacementFractions.coerceY(fraction)
        }

    suspend fun setMessageFloatIconCorner(corner: String) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_CORNER] = corner
            it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_Y_FRACTION] =
                MessageOverlayCorner.fromId(corner).defaultYFraction()
        }

    suspend fun setMessageFloatIconYFraction(fraction: Float) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_Y_FRACTION] =
                MessagePlacementFractions.coerceY(fraction)
        }

    suspend fun setMessageSideFontSizeLevel(level: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_SIDE_FONT_SIZE_LEVEL] =
                SideBubbleFontSize.coerce(level)
        }

    suspend fun setMessageDanmakuSpeedLevel(level: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_DANMAKU_SPEED_LEVEL] = level.coerceIn(0, 2)
        }

    suspend fun setMessageStyleId(styleId: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_STYLE_ID] = styleId }

    suspend fun setMessagePrimaryStyleEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_PRIMARY_STYLE_ENABLED] = enabled }

    suspend fun setMessageDanmakuEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_ENABLED] = enabled }

    suspend fun setMessageCNoticeEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_ENABLED] = enabled }

    suspend fun setMessageCNoticeOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_OPACITY] = opacity.coerceIn(0f, 1f) }

    suspend fun setMessageCNoticeDimmedOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_DIMMED_OPACITY] = opacity.coerceIn(0f, 1f) }

    suspend fun setMessageCNoticeGhostOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_GHOST_OPACITY] = opacity.coerceIn(0f, 1f) }

    suspend fun setMessageCNoticeIconSizeDp(sizeDp: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_ICON_SIZE_DP] = sizeDp.coerceIn(32f, 64f) }

    suspend fun setMessageCNoticeMaxCount(count: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_MAX_COUNT] =
                count.coerceIn(1, MessageSettings.C_NOTICE_MAX_RETAINED_CONVERSATIONS)
        }

    suspend fun setMessageCNoticeAutoDismissSeconds(seconds: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_AUTO_DISMISS_SECONDS] =
                seconds.coerceIn(0, 60)
        }

    suspend fun setMessageCNoticeHorizontalEdge(edge: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_HORIZONTAL_EDGE] = edge }

    suspend fun setMessageCNoticeYFraction(fraction: Float) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_Y_FRACTION] =
                MessagePlacementFractions.coerceY(fraction)
        }

    suspend fun setMessageCNoticeEdgeMarginDp(marginDp: Float) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_EDGE_MARGIN_DP] = marginDp.coerceIn(0f, 32f)
        }

    suspend fun setMessageCNoticeDimDelayMs(delayMs: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_DIM_DELAY_MS] = delayMs.coerceIn(0, 5_000)
        }

    suspend fun setMessageCNoticeGhostDelayMs(delayMs: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_GHOST_DELAY_MS] = delayMs.coerceIn(0, 10_000)
        }

    suspend fun setMessageCNoticeDefaultCollapsed(collapsed: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_DEFAULT_COLLAPSED] = collapsed }

    suspend fun setMessageCNoticePeekBannerEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_PEEK_BANNER_ENABLED] = enabled }

    suspend fun setMessageCNoticeLandscapeEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_C_NOTICE_LANDSCAPE_ENABLED] = enabled }

    suspend fun setMessageEdgeCardEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_ENABLED] = enabled }

    suspend fun setMessageEdgeCardOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_OPACITY] = opacity.coerceIn(0f, 1f) }

    suspend fun setMessageEdgeCardAutoCollapseMs(collapseMs: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_AUTO_COLLAPSE_MS] = collapseMs.coerceIn(0, 30_000)
        }

    suspend fun setMessageEdgeCardHorizontalEdge(edge: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_HORIZONTAL_EDGE] = edge }

    suspend fun setMessageEdgeCardMaxLines(lines: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_MAX_LINES] = lines.coerceIn(1, 3)
        }

    suspend fun setMessageEdgeCardKeepAcrossLock(keep: Boolean) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_KEEP_ACROSS_LOCK] = keep
        }

    suspend fun setMessageEdgeCardCollapseWhenScrollDone(collapse: Boolean) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_COLLAPSE_WHEN_SCROLL_DONE] = collapse
        }

    suspend fun setMessageEdgeCardYFraction(fraction: Float) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_Y_FRACTION] =
                MessagePlacementFractions.coerceY(fraction)
        }

    suspend fun setMessageEdgeCardMaxCount(count: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_EDGE_CARD_MAX_COUNT] = count.coerceIn(1, 5)
        }

    suspend fun setMessageThemeId(themeId: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_THEME_ID] = themeId }

    suspend fun setMessageDanmakuThemeId(themeId: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_THEME_ID] = themeId }

    suspend fun setMessageFloatIconOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_OPACITY] = opacity.coerceIn(0f, 1f) }

    suspend fun setMessageSideBubbleOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_OPACITY] = opacity.coerceIn(0.1f, 1f) }

    suspend fun setMessageFloatIconSizeDp(sizeDp: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_SIZE_DP] = sizeDp.coerceIn(32f, 64f) }

    suspend fun setMessageDanmakuOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_OPACITY] = opacity.coerceIn(0.2f, 1f) }

    suspend fun setMessageDanmakuMaxLines(lines: Int) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageSideMaxCount(count: Int) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_COUNT] = count.coerceIn(1, 9) }

    suspend fun setMessageSideMaxWidthDp(widthDp: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_WIDTH_DP] = widthDp.coerceIn(120f, 320f) }

    suspend fun setMessageSideMaxLines(lines: Int) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageFloatIconAutoDismissSeconds(seconds: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_AUTO_DISMISS_SECONDS] =
                seconds.coerceIn(0, 60)
        }

    suspend fun setMessageSideBubbleAutoDismissSeconds(seconds: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_AUTO_DISMISS_SECONDS] =
                seconds.coerceIn(0, 60)
        }

    suspend fun setMessageReturnChipEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_RETURN_CHIP_ENABLED] = enabled }

    suspend fun setMessageReturnChipAutoDismissSeconds(seconds: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_RETURN_CHIP_AUTO_DISMISS_SECONDS] =
                seconds.coerceIn(0, 60)
        }

    suspend fun setMessageReturnChipPosition(onRightEdge: Boolean, yFraction: Float) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_RETURN_CHIP_ON_RIGHT_EDGE] = onRightEdge
            it[SettingsPreferenceKeys.MESSAGE_RETURN_CHIP_Y_FRACTION] =
                yFraction.coerceIn(0f, 1f)
        }

    suspend fun setMessageHideInLandscape(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_HIDE_IN_LANDSCAPE] = enabled }

    suspend fun setMessagePortraitDanmaku(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_PORTRAIT_DANMAKU] = enabled }

    suspend fun setMessageLandscapeDanmaku(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_LANDSCAPE_DANMAKU] = enabled }

    suspend fun setMessageGestureAction(slot: String, action: MessageAction) = editor.edit { prefs ->
        val current = MessageSettingsCodec.decodeGestureActions(
            prefs[SettingsPreferenceKeys.MESSAGE_GESTURE_ACTIONS] ?: emptySet(),
        ).toMutableMap()
        current[slot] = action
        val encoded = current.map { (key, value) ->
            MessageSettingsCodec.encodeGestureAction(key, value)
        }.toSet()
        prefs[SettingsPreferenceKeys.MESSAGE_GESTURE_ACTIONS] = encoded
    }

    suspend fun addMessageEnabledPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES] = current
    }

    suspend fun removeMessageEnabledPackage(packageName: String) = editor.edit { prefs ->
        val current = prefs[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES] = current
        val rules = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        rules.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(rules.values)
    }

    suspend fun addMessageDisabledPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES] = current
    }

    suspend fun removeMessageDisabledPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES] = current
    }

    suspend fun addMessageDndPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES] = current
    }

    suspend fun removeMessageDndPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES] = current
    }

    suspend fun setMessageSuppressWhenSystemDnd(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SUPPRESS_WHEN_SYSTEM_DND] = enabled }

    suspend fun setMessageOpenLastOnUnlock(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_OPEN_LAST_ON_UNLOCK] = enabled }

    suspend fun setMessageKeepFloatIconAfterUnlock(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_KEEP_FLOAT_ICON_AFTER_UNLOCK] = enabled }

    suspend fun setMessageUnlockConfirmationAutoDismissSeconds(seconds: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_UNLOCK_CONFIRMATION_AUTO_DISMISS_SECONDS] =
                seconds.coerceIn(0, 30)
        }

    suspend fun setMessageUnlockOpenDelayMs(millis: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_UNLOCK_OPEN_DELAY_MS] =
                millis.coerceIn(0, MessageSettings.MAX_UNLOCK_OPEN_DELAY_MS)
        }

    suspend fun setMessageOpenLastAlways(packageName: String, enabled: Boolean) = editor.edit { prefs ->
        val packages = (prefs[SettingsPreferenceKeys.MESSAGE_OPEN_LAST_ALWAYS_PACKAGES] ?: emptySet()).toMutableSet()
        if (enabled) packages.add(packageName) else packages.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_OPEN_LAST_ALWAYS_PACKAGES] = packages
    }

    suspend fun upsertMessageAppFilterRule(rule: MessageAppFilterRule) = editor.edit { prefs ->
        val current = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        if (rule.hasCustomFilter()) {
            current[rule.packageName] = rule
        } else {
            current.remove(rule.packageName)
        }
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(current.values)
    }

    suspend fun removeMessageAppFilterRule(packageName: String) = editor.edit { prefs ->
        val current = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        current.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(current.values)
    }
}
