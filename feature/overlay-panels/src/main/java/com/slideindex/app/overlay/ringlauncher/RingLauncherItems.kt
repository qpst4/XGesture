package com.slideindex.app.overlay.ringlauncher

import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.FvRingLauncherSettings

fun Map<Int, QuickLauncherItem>.fvRingLauncherRuntimeItems(): List<QuickLauncherItem> =
    entries.sortedBy { it.key }
        .map { it.value }
        .filter {
            it.type == QuickLauncherItemType.APP ||
                it.type == QuickLauncherItemType.SHORTCUT ||
                it.type == QuickLauncherItemType.ACTION
        }

fun FvRingLauncherSettings.runtimeItems(): List<QuickLauncherItem?> {
    val count = slotCount()
    return List(count) { index -> itemAt(index)?.takeIf { it.payload.isNotBlank() } }
}

/**
 * 圆环弹出时是否直接进入编辑模式。
 *
 * 只有「一个槽位都没固定」**且**「没有任何可用于自动填充的应用」时才进编辑模式。
 * 只看前者的老逻辑会让槽位全空的用户（新装用户，或槽位被清空的用户）掉进编辑模式，
 * 同时 autoFill 被关掉，于是圆环一个图标都不显示——必须先手动钉一个槽位，其余槽位才一起填充。
 *
 * [autoFillCandidateCount] 传「可用于自动填充的应用数量」（即缓存的已安装应用数）。
 * 它为 0 时 [FvRingLauncherSettings] 的自动填充队列必然为空，判定与解析结果一致。
 */
fun shouldStartRingLauncherInEditMode(
    configuredSlotCount: Int,
    autoFillCandidateCount: Int,
): Boolean = configuredSlotCount == 0 && autoFillCandidateCount == 0
