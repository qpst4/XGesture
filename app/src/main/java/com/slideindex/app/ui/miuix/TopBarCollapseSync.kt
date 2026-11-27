package com.slideindex.app.ui.miuix

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/**
 * `TopAppBarState.heightOffsetLimit` 的初值哨兵（`-Float.MAX_VALUE`）与真实像素值的分界。
 *
 * 注意：哨兵本身也是有限浮点数，不能用 `isFinite()` 判断"layout 是否已经写回真实值"；
 * 屏幕像素不可能超过 10 万，用这个阈值区分即可。
 */
private const val HeightLimitSentinel = 100_000f

/**
 * 程序化滚动（`scrollToItem` / `initialFirstVisibleItemIndex`）之后，把顶栏的折叠态同步过去。
 *
 * 为什么需要：玻璃顶栏的材质带（band + 渐进模糊 + scrim）整层 alpha =
 * `collapsedFraction = heightOffset / heightOffsetLimit`，而 `heightOffset` **只由
 * nestedScroll 的 onPreScroll/onPostScroll 写**。程序化滚动不经过 nestedScroll，内容明明
 * 已经在中间（`canScrollBackward == true`、按钮也浮起来了），折叠态却还是 0 →
 * 顶栏整条透明。官方示例跳到搜索结果后也是页面自己手动同步折叠态的。
 *
 * 两个坑（都在这处理）：
 * 1. `heightOffsetLimit` 由首帧 layout 从大标题高度写回，初值是哨兵；必须等它离开哨兵再写，
 *    否则 `heightOffset` 会落在荒谬值上，之后"滚到顶就展开"再也回不来；
 * 2. 宽屏（`expandable = false`）时 limit 恒为 0、或者内容其实没滚起来，这时不能写。
 *
 * @param canCollapse 真正写入前的最后一道守卫（例如"用户还没手动拖过"、"列表确实可回滚"）。
 * @param timeoutMs 等 layout 写回 limit 的上限；超时就放弃（这一屏保持原样）。
 * @return 是否真的写入了折叠态（便于打点/测试）。
 */
suspend fun ScrollBehavior.syncCollapseAfterProgrammaticScroll(
    canCollapse: () -> Boolean = { true },
    timeoutMs: Long = 1_000L,
): Boolean {
    val barState = state
    val limit = withTimeoutOrNull(timeoutMs) {
        snapshotFlow { barState.heightOffsetLimit }
            .first { it > -HeightLimitSentinel && it < 0f }
    } ?: return false
    if (!canCollapse()) return false
    barState.heightOffset = limit
    return true
}
