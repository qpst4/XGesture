package com.slideindex.app.overlay.panels

import android.content.Context
import android.graphics.Bitmap
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.stash.StashEntry

/**
 * 暂存/剪贴板的"写出"能力（`:app` 的 `StashCoordinator` / `ScreenPinManager` /
 * `FloatBallTextPick` / `FloatBallStashPanel`）。
 *
 * 数据层（`:core:stash`）只负责落盘，真正"贴到屏幕上、开面板、复制到剪贴板"这些
 * 需要浮窗/服务的能力由 `:app` 注入。
 */
object PanelStashWriteHooks {

    /** 复制文本到剪贴板。 */
    @Volatile
    var copyText: ((Context, String) -> Unit)? = null

    /** 复制位图到剪贴板。 */
    @Volatile
    var copyImage: ((Context, Bitmap) -> Unit)? = null

    /** 打开暂存面板。 */
    @Volatile
    var showStashPanel: ((Context, PanelSide?) -> Unit)? = null

    /** 打开剪贴板面板。 */
    @Volatile
    var showClipboardPanel: ((Context, PanelSide?) -> Unit)? = null

    /** 文本贴屏。 */
    @Volatile
    var pinText: ((Context, String) -> Unit)? = null

    /** 位图贴屏（`layoutMeta` 为 `:app` 的 `ScreenshotLayoutMeta`，可为 null）。 */
    @Volatile
    var pinImage: ((Context, Bitmap, android.graphics.Rect?, Any?) -> Unit)? = null

    /** 剪贴板条目贴屏。 */
    @Volatile
    var pinClipboardEntry: ((Context, ClipboardEntry) -> Unit)? = null

    /** 暂存图片贴屏。 */
    @Volatile
    var pinFromStashImage: ((Context, StashEntry, Bitmap) -> Unit)? = null

    /** 跟手拉出暂存面板：`tab` 为 `StashPanelInitialTab` 序号（0=Stash, 1=Clipboard）。 */
    var beginHandleReveal: ((Context, Int, PanelSide?) -> Boolean)? = null

    /** 把富内容块写入剪贴板（`:app` 的 ClipboardWriter.writeBlocks）。 */
    var writeRichBlocks:
        ((
            Context,
            List<com.slideindex.app.clipboard.ClipboardContentBlock>,
            String?,
            (String) -> String?,
            (String) -> android.net.Uri?,
            (String) -> Pair<Int, Int>?,
        ) -> Unit)? = null

    /** 跟手拉出松手。 */
    var endHandleReveal: ((Boolean) -> Unit)? = null

    /** 暂存富内容贴屏。 */
    @Volatile
    var pinStashRich: ((Context, StashEntry) -> Unit)? = null
}
