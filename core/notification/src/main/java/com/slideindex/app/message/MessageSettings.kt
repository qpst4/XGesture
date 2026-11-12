package com.slideindex.app.message

data class MessageSettings(
    val enabled: Boolean = false,
    @Deprecated("Use per-style enabled flags")
    val styleId: String = MessageStyle.SideBubble.id,
    @Deprecated("Use per-style enabled flags")
    val primaryStyleEnabled: Boolean = true,
    val floatIconEnabled: Boolean = false,
    val sideBubbleEnabled: Boolean = true,
    val danmakuEnabled: Boolean = true,
    val cNoticeEnabled: Boolean = false,
    @Deprecated("Use sideThemeId")
    val themeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.SideBubble),
    val sideThemeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.SideBubble),
    val danmakuThemeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.Danmaku),
    val floatIconOpacity: Float = 0.95f,
    val sideBubbleOpacity: Float = 0.7f,
    val danmakuOpacity: Float = 0.7f,
    val cNoticeOpacity: Float = 1.0f,
    val cNoticeDimmedOpacity: Float = 0.4f,
    val cNoticeGhostOpacity: Float = 0.2f,
    val danmakuMaxLines: Int = 1,
    val sideMaxCount: Int = 3,
    val sideMaxWidthDp: Float = 168f,
    val sideMaxLines: Int = 2,
    val floatIconSizeDp: Float = 44f,
    val cNoticeIconSizeDp: Float = 44f,
    val cNoticeMaxCount: Int = 5,
    /** 悬浮球样式自动关闭时间，0 表示不自动关闭。 */
    val floatIconAutoDismissSeconds: Int = 5,
    /** 侧边气泡样式自动关闭时间，0 表示不自动关闭。 */
    val sideBubbleAutoDismissSeconds: Int = 5,
    /** C Notice 样式自动关闭时间，0 表示不自动关闭。 */
    val cNoticeAutoDismissSeconds: Int = 0,
    val cNoticeHorizontalEdge: SideBubbleHorizontalEdge = SideBubbleHorizontalEdge.Right,
    val cNoticeYFraction: Float = 0.5f,
    /** 贴边列表距屏幕左右边缘的距离（dp）。 */
    val cNoticeEdgeMarginDp: Float = 8f,
    val cNoticeDimDelayMs: Int = 300,
    val cNoticeGhostDelayMs: Int = 2500,
    val cNoticeDefaultCollapsed: Boolean = false,
    /** 新消息到达时，在对应悬浮球旁短暂显示内容横幅。 */
    val cNoticePeekBannerEnabled: Boolean = false,
    /** 横屏时是否显示贴边通知列表（与「横屏隐藏悬浮球与侧边」独立）。 */
    val cNoticeLandscapeEnabled: Boolean = true,
    val hideInLandscape: Boolean = false,
    val portraitDanmaku: Boolean = true,
    val landscapeDanmaku: Boolean = true,
    val sideBubbleHorizontalEdge: SideBubbleHorizontalEdge = SideBubbleHorizontalEdge.Right,
    val sideBubbleVerticalAnchor: SideBubbleVerticalAnchor = SideBubbleVerticalAnchor.Middle,
    val sideBubbleYFraction: Float = 0.5f,
    val floatIconCorner: MessageOverlayCorner = MessageOverlayCorner.BottomEnd,
    val floatIconYFraction: Float = MessagePlacementFractions.DEFAULT_BOTTOM_Y,
    val sideBubbleFontSizeLevel: Int = SideBubbleFontSize.NORMAL,
    val danmakuSpeedLevel: Int = DanmakuSpeed.NORMAL,
    val singleTapAction: MessageAction = MessageAction.Ignore,
    val swipeUpAction: MessageAction = MessageAction.Ignore,
    val swipeDownAction: MessageAction = MessageAction.Ignore,
    val swipeLeftAction: MessageAction = MessageAction.Ignore,
    val swipeRightAction: MessageAction = MessageAction.Ignore,
    val longPressAction: MessageAction = MessageAction.Ignore,
    val enabledPackages: Set<String> = emptySet(),
    val disabledPackages: Set<String> = emptySet(),
    val dndPackages: Set<String> = emptySet(),
    val suppressWhenSystemDnd: Boolean = false,
    val interceptNotifications: Boolean = false,
    val appFilterRules: Map<String, MessageAppFilterRule> = emptyMap(),
    /** 解锁屏幕后自动打开锁屏期间到达的最后一条消息。 */
    val openLastMessageOnUnlock: Boolean = false,
    /**
     * 解锁后等待多久再打开最后一条消息（毫秒），0 表示立即打开。
     *
     * 默认 [DEFAULT_UNLOCK_OPEN_DELAY_MS]：稍等一拍，避开解锁/桌面动画的竞争；
     * 上限 [MAX_UNLOCK_OPEN_DELAY_MS] 只作越界数据兜底。
     */
    val unlockOpenDelayMs: Int = DEFAULT_UNLOCK_OPEN_DELAY_MS,
    /** 解锁后无需询问即可打开消息的应用。 */
    val openLastMessageAlwaysPackages: Set<String> = emptySet(),
    /** 解锁确认卡片自动消失时间，0 表示不自动消失。 */
    val unlockConfirmationAutoDismissSeconds: Int = 3,
    /**
     * 锁屏期间到达的消息，其悬浮球提醒保留到解锁后再显示（仅悬浮球样式）。
     *
     * 开启后熄屏/锁屏不再删除悬浮球提醒，而是冻结其自动关闭计时并隐藏窗口，
     * 解锁后重新显示并按 [floatIconAutoDismissSeconds] 重新计时。
     */
    val keepFloatIconAfterUnlock: Boolean = false,
    /** 贴边通知卡：贴在屏幕侧缘的单条卡片，可展开/收起/上下滑消失。 */
    val edgeCardEnabled: Boolean = false,
    /** 收起态底板不透明度；文字与角标不跟随降透明。 */
    val edgeCardOpacity: Float = 0.55f,
    /** 展开后自动收起的等待时间（毫秒），0 表示不自动收起。 */
    val edgeCardAutoCollapseMs: Int = 3000,
    val edgeCardHorizontalEdge: SideBubbleHorizontalEdge = SideBubbleHorizontalEdge.Right,
    val edgeCardYFraction: Float = 0.5f,
    /** 同时显示的最大会话卡数量（1..5），超出时移除最久的一张。 */
    val edgeCardMaxCount: Int = 3,
    /** 正文显示行数（1..3）；每多一行卡片高度 +16dp。 */
    val edgeCardMaxLines: Int = 2,
    /** 锁屏/灭屏后保留卡片，解锁后补显。 */
    val edgeCardKeepAcrossLock: Boolean = true,
    /** 长消息滚动到底后自动收起（不受自动收起时长限制）。 */
    val edgeCardCollapseWhenScrollDone: Boolean = true,
    /**
     * 阅读消息后，在屏幕边缘显示「返回上一应用」胶囊（位置可拖动并记忆）。
     *
     * 只在「从消息提醒打开了目标应用、目标应用全屏、来源应用不可见」时显示；
     * 解锁后自动打开、小窗阅读、锁屏等路径不会显示。
     */
    val returnChipEnabled: Boolean = true,
    /**
     * 返回胶囊自动收起时间（秒），0 表示不按时间收起。
     *
     * 0 时胶囊仍会在离开目标应用 / 熄屏 / 点击返回后收起，不是永久常驻。
     */
    val returnChipAutoDismissSeconds: Int = 8,
    /** 返回胶囊贴哪一侧；纵向位置占可移动范围（屏幕高减胶囊高度）的比例。 */
    val returnChipOnRightEdge: Boolean = false,
    val returnChipYFraction: Float = 0.22f,
) {
    @Suppress("DEPRECATION")
    val style: MessageStyle get() = MessageStyle.fromId(styleId)

    fun isPackageAllowed(packageName: String): Boolean {
        if (packageName in disabledPackages) return false
        if (enabledPackages.isEmpty()) return true
        return packageName in enabledPackages
    }

    fun filterRuleFor(packageName: String): MessageAppFilterRule =
        appFilterRules[packageName] ?: MessageAppFilterRule.default(packageName)

    fun passesAppFilter(packageName: String, title: String, content: String): Boolean =
        MessageAppFilterMatcher.passes(filterRuleFor(packageName), title, content)

    fun hasAnyStyleEnabled(): Boolean =
        floatIconEnabled || sideBubbleEnabled || danmakuEnabled || cNoticeEnabled || edgeCardEnabled

    companion object {
        const val C_NOTICE_MAX_RETAINED_CONVERSATIONS = 20

        /** 「解锁后进入最后一条消息」的默认延迟：避免与解锁/桌面动画竞争。 */
        const val DEFAULT_UNLOCK_OPEN_DELAY_MS = 600

        /** 解锁后打开延迟的上限（毫秒）：再长也只是白白等。 */
        const val MAX_UNLOCK_OPEN_DELAY_MS = 1500
    }
}
