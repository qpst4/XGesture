package com.slideindex.app.overlay.layout

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 编辑层挂件（「空白占位 / 删除」药丸组、顶部提示条）的**纵向**位置。
 *
 * 背景：这两个挂件原来是固定贴顶 / 贴底的，轮盘靠边呼出时会被容器压住。
 * 这里只调纵向（横向仍居中），但选位时必须保证**不压任何容器**：
 *
 * 1. 先取「横向和挂件相交」的容器（不是所有容器）——半圆贴边时上下两段容器之间
 *    往往正好有空档，那才是离轮盘最近的位置；
 * 2. 把这些容器的纵向区间（含间距）合并，从安全区里减掉，得到若干**空档**；
 * 3. 在放得下挂件高度的空档里，选离参照点最近的那个：
 *    - 药丸组：离轮盘纵向中心最近（同距离取靠下，拇指更顺）；
 *    - 提示条：离屏幕顶部最近（保持"贴边"的观感）；
 * 4. 实在没有空档：药丸保持现状叠在底部，提示条贴顶并允许半透明压在容器上
 *    （由调用方根据 [QuickWheelEditorChromeY.hintOverlapping] 降低不透明度）。
 *
 * 纯函数，不依赖 Compose / Android，便于单测。
 */
object QuickWheelEditorChromePlacement {

    fun place(
        /** 当前层级的容器（一级或二级；末尾的「+」也算一个）。 */
        slots: List<QuickWheelPlacedSlot>,
        /** 挂件的横向中心（= 编辑层可视宽度的一半）。 */
        horizontalCenterPx: Float,
        pillsWidthPx: Float,
        pillsHeightPx: Float,
        hintWidthPx: Float,
        hintHeightPx: Float,
        /** 安全区上下边（已含状态栏 / 导航栏）。 */
        safeTopPx: Float,
        safeBottomPx: Float,
        /** 挂件与容器之间、挂件与挂件之间的最小间距。 */
        gapPx: Float,
        /** 轮盘当前层级的纵向中心，药丸组尽量靠近它。 */
        wheelCenterY: Float,
    ): QuickWheelEditorChromeY {
        val pillsBlockers = blockedSpans(
            slots = slots,
            xFrom = horizontalCenterPx - pillsWidthPx / 2f,
            xTo = horizontalCenterPx + pillsWidthPx / 2f,
            gapPx = gapPx,
        )
        val pillsTop = chooseTop(
            blockers = pillsBlockers,
            heightPx = pillsHeightPx,
            safeTopPx = safeTopPx,
            safeBottomPx = safeBottomPx,
            targetY = wheelCenterY,
            preferLowerOnTie = true,
        ) ?: (safeBottomPx - pillsHeightPx)

        // 提示条：药丸占的那一段也算遮挡，避免两者叠在一起。
        val hintBlockers = blockedSpans(
            slots = slots,
            xFrom = horizontalCenterPx - hintWidthPx / 2f,
            xTo = horizontalCenterPx + hintWidthPx / 2f,
            gapPx = gapPx,
        ) + Span(pillsTop - gapPx, pillsTop + pillsHeightPx + gapPx)
        val hintTop = chooseTop(
            blockers = hintBlockers,
            heightPx = hintHeightPx,
            safeTopPx = safeTopPx,
            safeBottomPx = safeBottomPx,
            targetY = safeTopPx,
            preferLowerOnTie = false,
        )

        return QuickWheelEditorChromeY(
            pillsTop = pillsTop,
            hintTop = hintTop ?: safeTopPx,
            hintOverlapping = hintTop == null,
        )
    }

    /** 一次计算的结果（px，相对编辑层根容器顶部）。 */
    data class QuickWheelEditorChromeY(
        val pillsTop: Float,
        val hintTop: Float,
        /** true = 提示条没找到空档，只能压容器显示（调用方降低不透明度）。 */
        val hintOverlapping: Boolean,
    )

    private data class Span(val top: Float, val bottom: Float)

    /** 横向与挂件相交的容器 → 它们占用的纵向区间（已含 [gapPx]）。 */
    private fun blockedSpans(
        slots: List<QuickWheelPlacedSlot>,
        xFrom: Float,
        xTo: Float,
        gapPx: Float,
    ): List<Span> = slots.mapNotNull { slot ->
        val half = slot.sizePx / 2f
        val left = slot.centerX - half
        val right = slot.centerX + half
        if (right < xFrom || left > xTo) return@mapNotNull null
        Span(slot.centerY - half - gapPx, slot.centerY + half + gapPx)
    }

    /** 把占用区间从安全区里减掉，得到可用空档。 */
    private fun freeSpans(
        blockers: List<Span>,
        safeTopPx: Float,
        safeBottomPx: Float,
    ): List<Span> {
        if (safeBottomPx <= safeTopPx) return emptyList()
        val merged = merge(blockers.sortedBy { it.top })
        val result = ArrayList<Span>(merged.size + 1)
        var cursor = safeTopPx
        for (span in merged) {
            if (span.bottom <= cursor) continue
            if (span.top > cursor) {
                result += Span(cursor, min(span.top, safeBottomPx))
            }
            cursor = max(cursor, span.bottom)
            if (cursor >= safeBottomPx) break
        }
        if (cursor < safeBottomPx) {
            result += Span(cursor, safeBottomPx)
        }
        return result
    }

    private fun merge(sorted: List<Span>): List<Span> {
        if (sorted.isEmpty()) return emptyList()
        val result = ArrayList<Span>(sorted.size)
        var current = sorted.first()
        for (span in sorted.drop(1)) {
            current = if (span.top <= current.bottom) {
                Span(current.top, max(current.bottom, span.bottom))
            } else {
                result += current
                span
            }
        }
        result += current
        return result
    }

    /**
     * 在空档里挑一个放 [heightPx] 高的挂件，返回它的顶边。
     *
     * 空档内尽量靠近 [targetY]：目标在上方就贴空档下沿，目标在下方就贴空档上沿，
     * 目标落在空档内部就居中。同距离时按 [preferLowerOnTie] 决定上下。
     */
    private fun chooseTop(
        blockers: List<Span>,
        heightPx: Float,
        safeTopPx: Float,
        safeBottomPx: Float,
        targetY: Float,
        preferLowerOnTie: Boolean,
    ): Float? {
        val free = freeSpans(blockers, safeTopPx, safeBottomPx)
        var bestTop: Float? = null
        var bestScore = Float.MAX_VALUE
        for (span in free) {
            if (span.bottom - span.top < heightPx) continue
            val top = (targetY - heightPx / 2f).coerceIn(span.top, span.bottom - heightPx)
            val score = abs(top + heightPx / 2f - targetY)
            val better = when {
                bestTop == null -> true
                score < bestScore - TIE_EPSILON -> true
                score <= bestScore + TIE_EPSILON && preferLowerOnTie && top > bestTop -> true
                else -> false
            }
            if (better) {
                bestTop = top
                bestScore = score
            }
        }
        return bestTop
    }

    private const val TIE_EPSILON = 0.5f
}
