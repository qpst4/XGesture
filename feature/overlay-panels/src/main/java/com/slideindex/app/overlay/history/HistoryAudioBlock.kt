package com.slideindex.app.overlay.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.clipboard.formatAudioDuration
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/**
 * 语音块的**胶囊**（§0.16.21）：整宽、一行、左边一个 ▶/⏸、中间时长、可选右边一个 ✕。
 *
 * **一处实现、两处使用**（编辑器里 / 条目卡片里）：
 * 编辑器里带 ✕（可以删掉这一段），卡片里不带（卡片上删块要走编辑条）。
 * 这么分是因为"点了就播"在两处是同一件事，而"能不能删"是两处唯一的差别 ——
 * 写成两个组件，播放逻辑与文案迟早走偏。
 *
 * 播放本身交给单例 [HistoryAudioPlayback]（同一时刻只有一条在响）；
 * 本组件只做三件事：画状态、转点击、**在离开组合时停掉自己**。
 *
 * ⚠️ 后一件事很重要：它同时实现了"切条目 / 收起展开 / 关面板"时自动停 —— 这些场景在
 * Compose 里都表现为"这个音频块离开了组合"，靠宿主逐个去调 `stop()` 一定会漏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryAudioBlockCapsule(
    /** 已经解析过的**可播放绝对路径**（文件名 → 路径那一步由调用方做）。 */
    path: String,
    durationMs: Long,
    /** 非 null = 显示 ✕（编辑器里用）。 */
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** false = 暂时不能播（录音进行中：互斥）。 */
    enabled: Boolean = true,
    /** 长按触发（卡片拖拽）。 */
    onLongClick: (() -> Unit)? = null,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    val playing = HistoryAudioPlayback.isPlaying(path)
    val position = if (playing) HistoryAudioPlayback.positionMs else 0L
    var failed by remember(path) { mutableStateOf(false) }

    // 离开组合 = "这条/这块不再显示了" → 停掉（见 KDoc 里那一段）。
    DisposableEffect(path) {
        onDispose { if (HistoryAudioPlayback.isPlaying(path)) HistoryAudioPlayback.stop() }
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = {
                failed = false
                HistoryAudioPlayback.toggle(path) { failed = true }
            },
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
        ) {
            failed = false
            HistoryAudioPlayback.toggle(path) { failed = true }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (playing) theme.accentSoft else theme.btnBg)
            .border(
                width = 1.dp,
                color = if (playing) theme.accent.copy(alpha = 0.55f) else theme.btnBd,
                shape = shape,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .then(clickModifier)
                .padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (playing) theme.accentSolid else theme.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.stash_audio_pause else R.string.stash_audio_play,
                    ),
                    tint = if (playing) Color.White else theme.accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = if (failed) {
                    stringResource(R.string.stash_audio_playback_failed)
                } else if (playing) {
                    "${formatAudioDuration(position)} / ${formatAudioDuration(durationMs)}"
                } else {
                    formatAudioDuration(durationMs)
                },
                style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.sm),
                color = if (failed) theme.danger else theme.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                // 删掉正在播的这一块：先把播放停掉，否则播放器还捏着那个文件。
                                if (HistoryAudioPlayback.isPlaying(path)) HistoryAudioPlayback.stop()
                                onRemove()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        // 无障碍文案复用现成的（本仓库不许为"删除"再新增一个 key）。
                        contentDescription = stringResource(R.string.stash_tag_delete),
                        tint = theme.sub,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        // 播放进度：一条贴着底边的细线（不占布局高度，胶囊的高度因此始终是 40dp）。
        if (playing && durationMs > 0L) {
            val fraction = (position.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(theme.accent),
            )
        }
    }
}

/**
 * 块编辑器里的一个**语音块**（§0.16.21）—— 就是 [HistoryAudioBlockCapsule] 加上"上报位置"。
 *
 * 与 [DraftBlockEditorImage] 同一个理由：正文区是可滚动的固定视口（160..320dp），
 * 插进来的块要能被 `scrollBlockIntoView` 找到，所以每块都要上报自己在**内容坐标系**里的 y
 * （`positionInParent` 相对块列表，滚动时不变）。
 */
@Composable
fun DraftBlockEditorAudio(
    path: String,
    durationMs: Long,
    onRemove: () -> Unit,
    /** 上报本块在**内容坐标系**里的 y（§0.16.18 的"插入后滚到目标"要用）。 */
    onBlockPlaced: (Int) -> Unit,
) {
    HistoryAudioBlockCapsule(
        path = path,
        durationMs = durationMs,
        onRemove = onRemove,
        modifier = Modifier.onGloballyPositioned { coords ->
            onBlockPlaced(coords.positionInParent().y.toInt())
        },
    )
}
