package com.slideindex.app.ui.ringlauncher

import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType

/**
 * 圆环槽位「无」的两种含义：
 * - [TRANSPARENT]：这个槽位固定留空，不参与「最近应用」自动填充；
 * - [AUTO_FILL]：未配置，交给自动填充（槽位 key 不存在）。
 *
 * 存储上 [TRANSPARENT] 用一个空 APP 条目表示（沿用历史约定），
 * [AUTO_FILL] 则是删掉该槽位的 key。
 */
enum class RingSlotNoneMode {
    TRANSPARENT,
    AUTO_FILL,
}

/** 当前槽位对应的「无」模式；已绑定具体条目时返回 null（菜单两项都不打勾）。 */
fun QuickLauncherItem?.ringSlotNoneMode(): RingSlotNoneMode? = when {
    this == null -> RingSlotNoneMode.AUTO_FILL
    type == QuickLauncherItemType.APP && payload.isBlank() -> RingSlotNoneMode.TRANSPARENT
    else -> null
}
