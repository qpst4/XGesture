package com.slideindex.app.overlay.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * 面板拉出进度 0..1：**拖动时贴手指、其它时候用弹簧**。
 *
 * - 手指拖动（[HistoryPanelReveal.dragging]）：直接 `snapTo(手指进度)` —— 这就是"跟手"；
 * - 松手 / 点击打开：`animateTo(目标)`，弹簧与宿主原来的滑入动画同参数（0.8 / 300）。
 *
 * 收回结束（进度回到 0）时做两件收尾：
 * 1. 清 [HistoryPanelReveal.dragSession]（下次"点击打开"恢复正常滑入动画）；
 * 2. 若这次是**取消拖动**（[HistoryPanelReveal.retractPending]），叫宿主关窗 ——
 *    必须等动画播完再关，否则面板是"啪"地消失而不是滑回去。
 *
 * ⚠️ [panelTargetVisible] 要用 `rememberUpdatedState` 包一层再在 `snapshotFlow` 里读：
 * 它是普通入参（源头是宿主的 `MutableState`），直接在块里读**不会被观察**，
 * 拿到的会是首次组合时的旧值。
 */
@Composable
fun rememberPanelRevealProgress(
    panelTargetVisible: Boolean,
    onRetracted: () -> Unit,
): Float {
    val progress = remember { Animatable(0f) }
    val latestTargetVisible = rememberUpdatedState(panelTargetVisible)
    LaunchedEffect(Unit) {
        snapshotFlow {
            RevealInputs(
                dragging = HistoryPanelReveal.dragging,
                dragProgress = HistoryPanelReveal.dragProgress,
                target = HistoryPanelReveal.open && latestTargetVisible.value,
            )
        }.collectLatest { inputs ->
            if (inputs.dragging) {
                progress.snapTo(inputs.dragProgress)
                return@collectLatest
            }
            progress.animateTo(
                targetValue = if (inputs.target) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 300f,
                ),
            )
            if (inputs.target) {
                // 归位完成：结束拖动会话 —— 之后再关闭就走宿主正常的滑出动画，
                // 此时位移本来就是 0，所以不会有跳变。
                HistoryPanelReveal.dragSession = false
            } else {
                // 收回完成：**先**关窗（此刻 dragSession 还是 true，退场是瞬间的，
                // 而面板已经在屏幕外，所以看不见任何跳变），**再**结束会话。
                if (HistoryPanelReveal.retractPending) {
                    HistoryPanelReveal.retractPending = false
                    onRetracted()
                }
                HistoryPanelReveal.dragSession = false
            }
        }
    }
    return progress.value
}

/** [snapshotFlow] 的输入三元组（用 data class 而不是 Triple，读起来清楚）。 */
private data class RevealInputs(
    val dragging: Boolean,
    val dragProgress: Float,
    val target: Boolean,
)
