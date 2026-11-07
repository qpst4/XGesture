package com.slideindex.app.overlay.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text

/** 一条提示的内容（设计稿 `.toast` 的单槽 `undoFn`）。 */
data class HistoryToastState(
    val message: String,
    val onUndo: (() -> Unit)?,
)

/** 设计稿：`.toast { bottom: 104px; max-width: 342px }`。 */
val HistoryToastBottomPadding = 104.dp

/** 有撤销 4200ms、无撤销 2200ms（设计稿 `showToast` 的两个 `setTimeout`）。 */
const val HistoryToastUndoDurationMs = 4_200L
const val HistoryToastPlainDurationMs = 2_200L

/**
 * 提示条 + 「撤销」——照设计稿 `.toast` 自己画，不用 miuix Snackbar：
 *
 * ```
 * .toast { left:50%; bottom:104px; max-width:342px; padding:10px 10px 10px 16px;
 *          border-radius: var(--r-md)=14; gap:12px; font-size: var(--f-meta) }
 * .toast .undo { height:30px; padding:0 12px; border-radius: var(--r-xs)=8;
 *                background: color-mix(accent 16%); color: var(--accent-solid); font-weight:650 }
 * ```
 *
 * ⚠️ demo 里它是**相对整块手机屏**居中（不是相对面板），所以调用方要把它放在
 * 全屏那一层（面板窗是满屏的，`BottomCenter` 即可）。
 */
@Composable
fun HistoryToast(
    state: HistoryToastState?,
    onUndo: () -> Unit,
    /** 左右滑动消除（不执行撤销）。 */
    onSwipeDismiss: () -> Unit = {},
) {
    val theme = historyTheme()
    // 左右滑动消除：拖过自身宽度 30% 就消失，否则弹回原位。
    val scope = rememberCoroutineScope()
    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    // ⚠️ 每条新提示都要**复位滑动位移**：滑动消除后 Animatable 停在偏移上，
    // 下一条提示复用同一份状态就会"半截在屏幕外"（用户实测到的 bug）。
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state != null) slide.snapTo(0f)
    }
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(tween(HistoryDurations.d2)) +
            slideInVertically(tween(HistoryDurations.d3, easing = HistoryEasing.out)) { it / 3 },
        exit = fadeOut(tween(HistoryDurations.d2)) +
            slideOutVertically(tween(HistoryDurations.d3)) { it / 3 },
    ) {
        val current = state ?: return@AnimatedVisibility
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(slide.value.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                                scope.launch { slide.snapTo(slide.value + dragAmount) }
                            },
                            onDragEnd = {
                                if (kotlin.math.abs(slide.value) > size.width * 0.3f) {
                                    onSwipeDismiss()
                                } else {
                                    scope.launch {
                                        slide.animateTo(
                                            0f,
                                            androidx.compose.animation.core.spring(
                                                dampingRatio = 0.8f,
                                                stiffness = 400f,
                                            ),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { slide.animateTo(0f) }
                            },
                        )
                    }
                    .widthIn(max = 342.dp)
                    .shadow(12.dp, RoundedCornerShape(HistoryRadii.md))
                    .clip(RoundedCornerShape(HistoryRadii.md))
                    .background(theme.glassFill)
                    .border(1.dp, theme.glassBorder, RoundedCornerShape(HistoryRadii.md))
                    .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = current.message,
                    style = TextStyle(fontSize = HistoryFontSizes.meta),
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (current.onUndo != null) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(HistoryRadii.xs))
                            .background(theme.accent.copy(alpha = 0.16f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                current.onUndo.invoke()
                                onUndo()
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                com.slideindex.app.overlay.panels.R.string.stash_undo,
                            ),
                            style = TextStyle(
                                fontSize = HistoryFontSizes.meta,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = theme.accentSolid,
                        )
                    }
                }
            }
        }
    }
}
