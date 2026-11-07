package com.slideindex.app.clipboard

/**
 * 剪贴板页签的固定筛选（`docs/capsule-refactor-plan.md` §0.16.4 待办 3）。
 *
 * 分类**必须能可靠推出**，否则宁可不做。这里每一类都落在 `ClipboardEntry` 已经持久化的字段上
 * （`ClipboardHistoryStore` 的 `entry_type` / `has_image` 两个轻量列，两者都是入库时算好、
 * 老库升级时回填的）：
 *
 * | 筛选项 | 判据 | 依据 |
 * | --- | --- | --- |
 * | [Image] | `has_image = 1` | `ClipboardEntry.hasImageContent()`（图片文件 / mime 以 `image/` 开头 / HTML 里的 `<img>`） |
 * | [File] | `has_image = 0` 且 `entry_type = 'URI'` | `ClipboardReader` 只在「有 `content://`/`file://` 文件」和「有图片」两条路上产出 `URI`；`isLocalFileUriEntry()` 同义 |
 * | [RichText] | `has_image = 0` 且 `entry_type = 'HTML'` | `ClipboardReader` 只有「HTML + 文本/图片」那条路给 `HTML`，且必然带 `htmlText` |
 * | [Link] | `has_image = 0` 且 `entry_type ∈ {TEXT, HTML}` 且整条内容以 `http://` / `https://` / `www.` 开头 | 复制的链接由浏览器写成「纯文本 = 链接」或「HTML + 文本 = 链接」 |
 *
 * **刻意不做**的两件事（写了原因，不是漏了）：
 * 1. 长文**中间夹带**的链接不算 [Link] —— 判断得跑正则逐条扫文本，SQLite 侧做不到，
 *    只能在内存里扫「已加载的那几页」，条数就会随滚动变化（正是这次要修的病）。
 *    所以 [Link] 只认「整条就是一条链接」。
 * 2. `INTENT`（`intent://` / 分享出来的 app 链接）不单列一类：它既不是文件也不是网页链接，
 *    硬塞进 [Link] 会让「打开链接」的行为对不上。它只在「全部」里出现。
 *
 * 四类**互斥**（都带 `has_image` / `entry_type` 的限定）。注意「全部」**不等于**四类之和：
 * 纯文本条目（既不是链接也不是 HTML）不属于任何一类，它只在「全部」里出现 ——
 * 这四个筛选项是"看某一类"，不是把库切成分区。
 */
enum class ClipboardHistoryFilter {
    All,
    Image,
    Link,
    RichText,
    File,
}
