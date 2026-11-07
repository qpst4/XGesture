package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import com.slideindex.app.stash.StashTag
import top.yukonga.miuix.kmp.basic.Text

/**
 * 页签下方那一排筛选胶囊（设计稿 `.stream .chips`）。
 *
 * 两个页签**同一位置**、同一套 `HistoryChip`：
 * - 闪念 = 标签筛选（「全部」+ 各标签 + 末尾一枚「＋」进标签管理 + 选中时的「清除」/「同时·任一」）；
 * - 剪贴板 = 固定筛选（全部 / 图片 / 链接 / 富文本 / 文件）。
 *
 * 用 `FlowRow` 而不是横向滚动：横滑会把首尾胶囊切掉（用户反馈过"直接被截断 / 最后一个胶囊
 * 形态是什么鬼"），换行布局永远不会裁内容。多选之后行内可能同时出现"全部/各标签/＋/清除/模式开关"
 * 五项以上，也是靠 FlowRow 换行兜住（不裁、不撑破）。
 *
 * ---
 * ## 多选（本次）
 * - 点标签 = **切换**选中，可多选；再点已选中的 = 取消；
 * - 选中集为空时 = 「全部」，渲染与行为与老的单选实现**逐字相同**；
 * - 选中 ≥1：多一枚「清除」出口（一键清空）；
 * - 选中 ≥2：「清除」后面多一枚「同时 / 任一」分段开关（AND / OR），默认「同时」（同时含全部）。
 *
 * 文案分工（别把两者合并）：「全部」胶囊 = 清空筛选 / 不筛，`stash_tag_filter_match_all`（同时）
 * 与 `stash_tag_filter_any`（任一）= 多标签怎么算命中。
 *
 * 选中态沿用 `HistoryChip` 自己的 `.chip.sel`（实心 accent 底 + 白字白点），所以多选下
 * "哪几个亮着"一眼可见。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HistoryTagChips(
    tags: List<StashTag>,
    selectedTags: Set<String>,
    /** 多标签匹配方式当前值：true = 同时含全部（AND，默认）。 */
    matchAll: Boolean,
    /** 「收藏」筛选是否开启（内置标签：只看已加星标）。 */
    onlyStarred: Boolean,
    onTagToggled: (String) -> Unit,
    onMatchAllChange: (Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onManageTags: () -> Unit,
    onStarredToggled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 多选之后这枚「全部」= "一个都别筛"的出口（清空选中），所以它的选中条件是"选中集为空"。
        HistoryChip(
            label = stringResource(R.string.stash_tag_filter_all),
            dotColor = null,
            selected = selectedTags.isEmpty() && !onlyStarred,
            onClick = onClearSelection,
        )
        // 「收藏」= **内置标签**：加星标 = 自动进这里，取消星标 = 自动移出；与标签筛选 AND 叠加。
        // 排在「全部」之后、各标签之前，位置固定，不随用户自建标签增减而跑掉。
        HistoryChip(
            label = stringResource(R.string.stash_tag_filter_starred),
            dotColor = null,
            selected = onlyStarred,
            onClick = onStarredToggled,
        )
        tags.forEach { tag ->
            HistoryChip(
                label = stashTagLabel(tag.name),
                dotColor = Color(tag.colorArgb),
                selected = tag.name in selectedTags,
                onClick = { onTagToggled(tag.name) },
            )
        }
        // 标签行末尾的「＋」（§0.16.4 待办 2）：进标签管理浮窗。永远在「清除」之前，跟着换行走。
        HistoryChip(
            label = "＋",
            dotColor = null,
            selected = false,
            onClick = onManageTags,
        )
        // 选中 ≥1（含只开了「收藏」）：一键清空的出口。`ghost` 让它比标签胶囊弱一档（它是动作，不是筛选状态）。
        if (selectedTags.isNotEmpty() || onlyStarred) {
            HistoryChip(
                label = stringResource(R.string.stash_tag_filter_clear),
                dotColor = null,
                selected = false,
                onClick = onClearSelection,
                ghost = true,
            )
        }
        // 选中维度 ≥2（含自建标签与收藏）：才谈得上"同时含"还是"含任一"。
        val activeCriteriaCount = selectedTags.size + (if (onlyStarred) 1 else 0)
        if (activeCriteriaCount >= 2) {
            TagMatchModeSwitch(matchAll = matchAll, onMatchAllChange = onMatchAllChange)
        }
    }
}

/**
 * 「同时 / 任一」匹配方式开关（小胶囊分段控件）。
 *
 * 两段的文案是 [R.string.stash_tag_filter_match_all]（同时 = AND）与
 * [R.string.stash_tag_filter_any]（任一 = OR）—— ⚠️ **不用** `stash_tag_filter_all`（「全部」）：
 * 同一行里已经有一枚独立的「全部」胶囊，含义是"清空筛选 / 不筛"，与这里"同时含"完全是两回事，
 * 共用一个 key 会让同一行出现两枚写着「全部」的胶囊。视觉上也刻意做成**带轨道的双段开关**，
 * 与旁边那排独立胶囊形态不同。
 */
@Composable
private fun TagMatchModeSwitch(
    matchAll: Boolean,
    onMatchAllChange: (Boolean) -> Unit,
) {
    val theme = historyTheme()
    val trackShape = RoundedCornerShape(HistoryRadii.pill)
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(trackShape)
            .background(theme.segTrack)
            .border(1.dp, theme.segBorder, trackShape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TagMatchOption(
            label = stringResource(R.string.stash_tag_filter_match_all),
            selected = matchAll,
            onClick = { onMatchAllChange(true) },
        )
        TagMatchOption(
            label = stringResource(R.string.stash_tag_filter_any),
            selected = !matchAll,
            onClick = { onMatchAllChange(false) },
        )
    }
}

@Composable
private fun TagMatchOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(theme.accentSolid) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = HistoryFontSizes.meta,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            // 选中片 = 实心 accent 底 + 白字（和 `HistoryChip` 的选中态同一套观感）。
            color = if (selected) Color.White else theme.text,
            maxLines = 1,
        )
    }
}

/**
 * 剪贴板页签的固定筛选行（§0.16.4 待办 3）。
 *
 * 这里**不显示各筛选项的条数**：条数要靠 SQL `COUNT` 才算得准（内存里只有已加载的几页），
 * 5 枚胶囊各挂一个数字既挤又要多 5 次查询，而头部那个总数已经把"有没有东西"说清楚了。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HistoryClipboardFilterChips(
    selected: ClipboardHistoryFilter,
    onSelected: (ClipboardHistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClipboardHistoryFilter.entries.forEach { filter ->
            HistoryChip(
                label = stringResource(clipboardFilterLabelRes(filter)),
                dotColor = null,
                selected = filter == selected,
                onClick = { onSelected(filter) },
            )
        }
    }
}

private fun clipboardFilterLabelRes(filter: ClipboardHistoryFilter): Int = when (filter) {
    ClipboardHistoryFilter.All -> R.string.stash_tag_filter_all
    ClipboardHistoryFilter.Image -> R.string.clipboard_filter_image
    ClipboardHistoryFilter.Link -> R.string.clipboard_filter_link
    ClipboardHistoryFilter.RichText -> R.string.clipboard_filter_rich
    ClipboardHistoryFilter.File -> R.string.clipboard_filter_file
}
