package com.slideindex.app.barcode

/** 归一化坐标点（0..1，相对位图宽高）。 */
data class BarcodePoint(val x: Float, val y: Float)

/** 归一化外接矩形（0..1）。 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class BarcodeScanResult(
    val text: String,
    val format: String,
    /**
     * 码在图上的定位点，**归一化**到 0..1（相对"准备后"的位图宽高）。
     *
     * 为什么用归一化而不是像素：扫描前位图会被**等比缩放**（`scaleDownIfNeeded`），
     * 归一化坐标在缩放前后一致，界面上直接乘显示尺寸即可，不必反算缩放比例。
     *
     * ⚠️ 例外：`scanBitmap(..., pickFastPath = true)` 那条路会先**留白外扩**再缩放，
     * 中心点会整体偏移。快捷菜单的三条路都传 `false`，因此不受影响；若将来有人用那条路，
     * 这里的坐标需要先减去外扩量再归一化。
     *
     * 空列表 = 引擎没给点位（此时只能走列表，不能画在图上）。
     */
    val points: List<BarcodePoint> = emptyList(),
)

fun List<BarcodeScanResult>.joinDisplayText(): String =
    joinToString(separator = "\n") { it.text }

/**
 * 一次扫描的**结果集合**该怎么用。
 *
 * 抽成纯函数是为了可单测：界面的分支完全由它决定——**只有一个码时走"直接回填"这条老路**
 * （最常见的场景不该因为支持多码而变复杂），多个码时才列出来让用户点选。
 */
sealed interface ScanOutcome {
    /** 什么也没扫到（或全是空串）。 */
    data object NoCode : ScanOutcome

    /** 只有一个码：与支持多码之前的行为完全一致。 */
    data class Single(val text: String) : ScanOutcome

    /** 多个码：交给界面列出候选。 */
    data class Multiple(val texts: List<String>) : ScanOutcome
}

/** @param texts 原始结果，允许含空串与重复（例如同一内容被两种码制各报一次）。 */
fun scanOutcome(texts: List<String>): ScanOutcome {
    val distinct = texts.filter { it.isNotBlank() }.distinct()
    return when (distinct.size) {
        0 -> ScanOutcome.NoCode
        1 -> ScanOutcome.Single(distinct.first())
        else -> ScanOutcome.Multiple(distinct)
    }
}

/**
 * 合并两批识别结果：**按文本去重，保留先出现那条**。
 *
 * 用途：相机路径先出"触发那一帧"的结果（便宜的单码读取器，用于立刻给用户反馈），随后再对同一张图做
 * 重量级重扫（全码制 + TRY_HARDER）。重扫走的是另一套策略，**可能先命中别的码**——直接替换会让用户
 * 看到"刚标出来的码又消失了、改成标别的码"（真机反馈）。所以这里取并集：
 *
 * - 两批都有同一个码：保留**先出现**那条（触发那条与"已经是显示朝向"的定格图同坐标系，点位最准）；
 * - 只有一批有：照原样并进来，谁也不丢；
 * - 空白文本丢弃。
 */
fun mergeScanResults(
    existing: List<BarcodeScanResult>,
    incoming: List<BarcodeScanResult>,
): List<BarcodeScanResult> {
    if (incoming.isEmpty()) return existing.filter { it.text.isNotBlank() }
    if (existing.isEmpty()) return incoming.filter { it.text.isNotBlank() }.distinctBy { it.text }

    val merged = ArrayList<BarcodeScanResult>(existing.size + incoming.size)
    val seen = HashSet<String>(existing.size + incoming.size)
    for (result in existing) {
        if (result.text.isBlank()) continue
        if (seen.add(result.text)) merged += result
    }
    for (result in incoming) {
        if (result.text.isBlank()) continue
        if (seen.add(result.text)) merged += result
    }
    return merged
}

/**
 * 把定位点收成一个外接矩形，供界面在图上画标记。
 *
 * @param minSize 每个方向的最小尺寸（归一化）。**必须有**：一维条码只有两个端点，外接矩形高度为 0，
 *   不兜底就画不出可点的标记。小于该尺寸时以原中心向四周补足。
 * @return 点数不足 2 个时返回 null（画不出有意义的框）。
 */
fun List<BarcodePoint>.boundingBoxOrNull(minSize: Float = 0f): NormalizedRect? {
    if (size < 2) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (point in this) {
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
    }
    if (maxX < minX || maxY < minY) return null

    val safeMin = minSize.coerceAtLeast(0f)
    if (maxX - minX < safeMin) {
        val center = (minX + maxX) / 2f
        minX = center - safeMin / 2f
        maxX = center + safeMin / 2f
    }
    if (maxY - minY < safeMin) {
        val center = (minY + maxY) / 2f
        minY = center - safeMin / 2f
        maxY = center + safeMin / 2f
    }
    // 夹回 0..1：补足尺寸可能把框推到图外，画到图外没有意义（界面也按图片边界裁剪）。
    return NormalizedRect(
        left = minX.coerceIn(0f, 1f),
        top = minY.coerceIn(0f, 1f),
        right = maxX.coerceIn(0f, 1f),
        bottom = maxY.coerceIn(0f, 1f),
    )
}
