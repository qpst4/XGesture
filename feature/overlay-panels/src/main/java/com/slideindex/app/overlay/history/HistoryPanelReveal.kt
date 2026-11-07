package com.slideindex.app.overlay.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「从把手跟手拉出面板」的共享状态。
 *
 * ## 为什么是共享状态，而不是把两个窗合成一个
 *
 * `docs/capsule-refactor-p0-findings.md` §1 确认：把手在 `HistoryFloatService` 自己的窗里，
 * 面板在无障碍服务的侧栏窗里，是**两个 window**。计划 §0.1 原本打算"合并成同窗"，
 * 但那条路要动**最常驻的那个窗**（把手窗的尺寸、坐标系、把手 Y 从 `params.y` 搬进 Compose 布局），
 * 还要把面板的后端设施（返回键拦截、输入焦点、玻璃、z-order 协同）整套搬过去 ——
 * 代价 2–3 人日，且真机出问题时回退面很大。
 *
 * 这里走同一份勘察里的方案 B：**两窗共享一个进度值**，把手窗在拖动时写、面板窗读着渲染。
 * 同进程共享 Compose 状态有先例（`FloatingPointerSession` + `FloatingPointerDisplay`：
 * 窗 A 写、窗 B 读渲染）。代价是两窗各自合成、极端情况下可能差一帧；
 * 好处是**完全不碰常驻窗的结构**，面板那套后端设施一样都不用搬。
 *
 * ## 字段分工
 *
 * - [open]：面板**应该**停在拉出态（拖动开始 / 点击打开都置 true；取消拖动与关闭置 false）。
 * - [dragging]：手指是否正按着把手横向拖 —— 决定进度由**手指**还是由**弹簧**决定。
 * - [dragProgress]：手指给出的进度（0 = 完全收起，1 = 完全拉出）。
 * - [dragSession]：本次打开是否**由拖动发起** —— 决定入场/退场要不要走宿主的滑动动画
 *   （拖动时不能走，否则会和手指的位移叠加）。弹簧收尾结束后由
 *   `rememberPanelRevealProgress` 清掉，下次"点击打开"就恢复正常滑入。
 * - [retractPending]：取消拖动后"等收回动画播完再让宿主关窗"的标志。
 */
object HistoryPanelReveal {
    var open by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var dragProgress by mutableFloatStateOf(0f)
    var dragSession by mutableStateOf(false)

    /**
     * **驱动窗口位置**的进度（0 = 面板整个在屏外，1 = 完全拉出）。
     *
     * 和 [dragProgress] 的区别：`dragProgress` 是**手指的原始值**，这个值经过弹簧（松手后的归位/收回），
     * 所以把它给宿主去移动窗口，窗口在松手后也会跟着弹簧走 ✓。
     * 面板侧每帧把它写进来（`snapshotFlow { revealProgress }`），宿主侧订阅它改 `params.x`。
     */
    var windowProgress by mutableFloatStateOf(1f)
    var retractPending by mutableStateOf(false)

    /** 手指给出的进度 → 面板位移距离（px）。 */
    fun offsetPx(progress: Float, spanPx: Float, gravityEnd: Boolean): Float {
        val fraction = (1f - progress).coerceIn(0f, 1f)
        return if (gravityEnd) fraction * spanPx else -fraction * spanPx
    }
}
