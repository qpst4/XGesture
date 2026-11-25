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
        val rules = kotlinx.coroutines.runBlocking {
            LinkRuleRepository.loadRules(service)
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
                    val allMatches = selectLinkTextMatches(spans, extracted)
                    val matches = allMatches.filter { isEligibleRadarTarget(it, candidateText, pkg, rules) }

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

        return dropContainedDuplicateRegions(regions)
            .distinctBy { Triple(it.url, it.rects, it.sourcePackage) }
            .sortedWith(compareBy({ it.rects.first().top }, { it.rects.first().left }, { it.textOffset }))
    }

    /**
     * 剔除「同一 URL 被上级聚合节点重复上报」的区域。
     *
     * 现象：某些页面（Chrome/WebView）既在外层容器节点上暴露了整块文本（text 覆盖了子节点内容），
     * 又给每个链接单独建了子节点。扫描会把同一链接产出两次：一次是**整块大框**（无字符坐标），
     * 一次是**贴合文字的小框**。大框在命中时会走「整组全返回」的降级分支，
     * 于是指针压住任意一条都会同时命中多条 → 只能弹「释放以选择链接」。
     *
     * 处理：同一 (url, 来源包名) 下，若某个区域的矩形被另一个**面积更小**的同 URL 区域完全包含，
     * 说明它是粗粒度的重复上报，丢弃它、保留更贴合的那份几何。
     */
    private fun dropContainedDuplicateRegions(regions: List<LinkRegion>): List<LinkRegion> {
        if (regions.size < 2) return regions
        val result = ArrayList<LinkRegion>(regions.size)
        regions.groupBy { it.url to it.sourcePackage }.values.forEach { group ->
            if (group.size == 1) {
                result += group[0]
                return@forEach
            }
            // 面积小的优先保留；后面的（更大的）若被已保留的完全包住，就视为重复。
            val kept = ArrayList<LinkRegion>(group.size)
            group.sortedBy(::regionArea).forEach { candidate ->
                val duplicated = kept.any { small ->
                    small.rects.isNotEmpty() && small.rects.all { inner ->
                        candidate.rects.any { outer -> outer.contains(inner) }
                    }
                }
                if (!duplicated) kept += candidate
            }
            result += kept
        }
        return result
    }

    private fun regionArea(region: LinkRegion): Long =
        region.rects.sumOf { it.width().toLong() * it.height().toLong() }

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

    private fun isEligibleRadarTarget(
        match: LinkTextMatch,
        candidateText: String,
        sourcePackage: String,
        rules: List<LinkDispatchRule>,
    ): Boolean {
        val matchedText = runCatching {
            candidateText.substring(match.start, match.end).trim()
        }.getOrDefault("")

        // 1. 话题/标签文本拦截：如 "#小米从小鹏挖来欧洲汽车负责人#"、"【#...#】"
        val cleanTopic = matchedText.removePrefix("【").removeSuffix("】").trim()
        if (cleanTopic.startsWith("#") && cleanTopic.endsWith("#") && cleanTopic.length > 2) {
            val rawUrl = match.url.lowercase()
            if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                return false
            }
            if (rawUrl.contains("huati") || rawUrl.contains("topic") || rawUrl.contains("pageinfo")) {
                return false
            }
        }

        val url = match.url.trim()
        if (url.isBlank()) return false
        val lower = url.lowercase()

        // 2. 宿主同源保护：微博自身内部协议不生成雷达热区抢占焦点
        if (sourcePackage.contains("weibo", ignoreCase = true) &&
            (lower.startsWith("sinaweibo://") || lower.startsWith("weibo://"))
        ) {
            return false
        }

        // 3. 通用标准公开协议直接放行
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.") || lower.startsWith("magnet:?")) {
            return true
        }

        // 4. 应用内部私有协议（如 intent:、特定 scheme）：必须命中用户启用的分发规则才放行
        return LinkDispatcher.findRule(url, rules) != null
    }
}
