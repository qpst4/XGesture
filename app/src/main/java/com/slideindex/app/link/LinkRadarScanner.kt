package com.slideindex.app.link

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.text.Spanned
import android.text.style.URLSpan
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.util.SelfAppForeground
import java.util.ArrayDeque

/**
 * 屏幕链接雷达无障碍扫描引擎。
 * 深度遍历所有可见应用窗口的无障碍节点树，抽取标准链接与自定义规则链接，结合字符物理坐标生成屏幕选框。
 */
object LinkRadarScanner {

    fun scan(service: AccessibilityService, sourceWindowId: Int? = null): List<LinkRegion> {
        val patterns = kotlinx.coroutines.runBlocking {
            LinkRuleRepository.loadExtractPatterns(service)
        }
        val selfPackage = service.packageName
        val allowSelfApp = runCatching { SelfAppForeground.isForeground(service) }.getOrDefault(false)
        val roots = collectTargetRoots(service, selfPackage, allowSelfApp)
        if (roots.isEmpty()) return emptyList()

        val screenWidth = service.resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeight = service.resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val screenRect = Rect(0, 0, screenWidth, screenHeight)

        val regions = mutableListOf<LinkRegion>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        roots.forEach { queue.add(it) }

        var nodeCount = 0
        var groupIdSequence = 0
        val nodeBounds = Rect()

        while (queue.isNotEmpty() && nodeCount < 2500) {
            val node = queue.poll() ?: continue
            nodeCount++

            if (sourceWindowId != null && node.windowId != sourceWindowId) {
                enqueueChildren(node, queue)
                continue
            }
            val pkg = node.packageName?.toString().orEmpty()
            if (!allowSelfApp && pkg == selfPackage) {
                continue
            }

            node.getBoundsInScreen(nodeBounds)
            if (nodeBounds.width() <= 0 || nodeBounds.height() <= 0 || !Rect.intersects(nodeBounds, screenRect)) {
                enqueueChildren(node, queue)
                continue
            }

            if (node.isVisibleToUser || !node.text.isNullOrBlank()) {
                val primaryText = node.text?.toString()
                val candidateText = if (!primaryText.isNullOrBlank()) {
                    primaryText
                } else {
                    node.contentDescription?.toString()
                }

                if (!candidateText.isNullOrBlank()) {
                    val native = node.text as? Spanned
                    val spans = if (native == null || candidateText != primaryText) {
                        emptyList()
                    } else {
                        native.getSpans(0, native.length, URLSpan::class.java).mapNotNull { span ->
                            val start = native.getSpanStart(span)
                            val end = native.getSpanEnd(span)
                            span.url?.takeIf { start >= 0 && end in (start + 1)..candidateText.length }
                                ?.let { LinkTextMatch(it, start, end) }
                        }
                    }

                    val extracted = LinkExtractor.findAll(candidateText, patterns)
                    val matches = selectLinkTextMatches(spans, extracted)

                    if (matches.isNotEmpty()) {
                        val characters = if (candidateText == primaryText) {
                            readAccessibilityCharacterLocations(node, candidateText)
                        } else {
                            AccessibilityCharacterLocations(null, false)
                        }
                        val locations = characters.bounds
                        val currentGroupId = groupIdSequence++

                        for (match in matches) {
                            val rects = locations?.let { characterRegions(it, match.start, match.end, nodeBounds) }
                            val clippedNodeBounds = Rect(nodeBounds).apply { intersect(screenRect) }
                            val finalRects = if (!rects.isNullOrEmpty()) {
                                rects
                            } else if (!clippedNodeBounds.isEmpty) {
                                listOf(clippedNodeBounds)
                            } else {
                                emptyList()
                            }
                            if (finalRects.isNotEmpty()) {
                                regions.add(
                                    LinkRegion(
                                        url = match.url,
                                        rects = finalRects,
                                        isPrecise = !rects.isNullOrEmpty(),
                                        groupId = currentGroupId,
                                        textOffset = match.start,
                                        sourcePackage = pkg,
                                        sourceText = candidateText,
                                    )
                                )
                            }
                        }
                    }
                }
            }

            enqueueChildren(node, queue)
        }

        return regions
            .distinctBy { Triple(it.url, it.rects, it.sourcePackage) }
            .sortedWith(compareBy({ it.rects.first().top }, { it.rects.first().left }, { it.textOffset }))
    }

    private fun collectTargetRoots(
        service: AccessibilityService,
        selfPackage: String,
        allowSelfApp: Boolean,
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val seenWindowIds = HashSet<Int>()

        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            ) {
                continue
            }
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.type != AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER &&
                window.type != AccessibilityWindowInfo.TYPE_SYSTEM
            ) {
                continue
            }
            val root = runCatching { window.root }.getOrNull() ?: continue
            val pkg = root.packageName?.toString().orEmpty()
            if (!allowSelfApp && pkg == selfPackage) {
                continue
            }
            if (seenWindowIds.add(root.windowId)) {
                result.add(root)
            }
        }

        val activeRoot = runCatching { service.rootInActiveWindow }.getOrNull()
        if (activeRoot != null) {
            val pkg = activeRoot.packageName?.toString().orEmpty()
            if ((allowSelfApp || pkg != selfPackage) && seenWindowIds.add(activeRoot.windowId)) {
                result.add(activeRoot)
            }
        }

        return result
    }

    private fun enqueueChildren(node: AccessibilityNodeInfo, queue: ArrayDeque<AccessibilityNodeInfo>) {
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull()
            if (child != null) {
                queue.add(child)
            }
        }
    }

    private fun characterRegions(locations: Array<RectF?>, start: Int, end: Int, clip: Rect): List<Rect> {
        val lines = mutableListOf<RectF>()
        val clipF = RectF(clip)
        for (index in start until minOf(end, locations.size)) {
            val glyph = locations[index] ?: continue
            if (glyph.isEmpty) continue
            val visible = RectF(glyph)
            if (!visible.intersect(clipF)) continue
            val line = lines.lastOrNull()
            if (line != null && minOf(line.bottom, visible.bottom) > maxOf(line.top, visible.top)) {
                line.union(visible)
            } else {
                lines += visible
            }
        }
        return lines.map { Rect().also(it::roundOut) }
    }
}
