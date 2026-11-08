package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect

/** FV-style paste targets: visible editables, smallest rect wins when nested. */
object ClipboardPasteTargetFinder {

    fun findTargetRects(service: AccessibilityService): List<Rect> {
        val rects = com.slideindex.app.clipboardfloat.ClipboardFloatHostAccess.host
            ?.editableBounds(service).orEmpty()
        if (rects.isEmpty()) return emptyList()
        return dedupeNestedTargets(rects).distinctBy { rectKey(it) }
    }

    private fun rectKey(rect: Rect): String =
        "${rect.left},${rect.top},${rect.right},${rect.bottom}"

    fun dedupeNestedTargets(rects: List<Rect>): List<Rect> {
        if (rects.size <= 1) return rects
        val sorted = rects.sortedBy { it.width().coerceAtLeast(1) * it.height().coerceAtLeast(1) }
        val kept = ArrayList<Rect>(sorted.size)
        for (candidate in sorted) {
            // 由小到大遍历：候选若包住已保留的更小矩形，说明它是外层容器，丢弃（smallest wins）。
            if (kept.any { inner -> inner != candidate && candidate.contains(inner) }) {
                continue
            }
            kept.add(candidate)
        }
        return kept
    }
}
