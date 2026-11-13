package com.slideindex.app.clipboard

/**
 * 截图入库去重。
 *
 * 同一次截图可能被**两条链路**各送一次：
 *
 * 1. 「保存图片」写完相册后显式调用 `ingestScreenshot(uri, name)` —— 拿到的 uri 是聚合卷别名
 *    `content://media/external/images/media/<id>`；
 * 2. 截图监听随后发现相册里多了一张截图，从**主卷**拿同一条媒体行
 *    `content://media/external_primary/images/media/<id>`。
 *
 * 两者指向同一张图，但 uri 字符串不同，而入库去重依据的是「内容指纹」（由 uri 算出），
 * 于是被判成两条不同内容，历史里就出现两条一模一样的记录（实测间隔 40~60ms）。
 *
 * 这里按「显示名 + 时间窗」兜底：[ClipboardEntry] 的显示名形如
 * `Screenshot_20261113-170223.png`，自带时间戳，因此**窗口内不可能出现两张不同的截图同名**。
 */
object ScreenshotIngestDedup {

    /**
     * 判定窗口。
     *
     * 实测两次入库相差 40~60ms，2 秒足够覆盖保存/监听的调度抖动，又不会误伤连续截图
     * （显示名精确到秒，两秒内的两张不同截图必然不同名）。
     */
    const val WINDOW_MS = 2_000L

    /**
     * [latest] 是否就是 [label] 这次要入库的同一张截图。
     *
     * @param latest 历史里最新的一条（没有则传 null）
     * @param label 本次截图的显示名
     * @param nowMs 本次入库的时间
     */
    fun isDuplicate(latest: ClipboardEntry?, label: String, nowMs: Long): Boolean {
        if (latest == null) return false
        if (latest.type != ClipboardEntryType.URI) return false
        if (latest.text.trim() != label.trim()) return false
        return nowMs - latest.createdAtEpochMs <= WINDOW_MS
    }
}
