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
    /** 构造一条剪贴板历史对应的 ClipData（`:app` 的 ClipboardWriter.buildClipForEntry）。 */
    @Volatile
    var buildClipForEntry: ((Context, ClipboardEntry) -> android.content.ClipData?)? = null

    /** 为"粘贴"准备剪贴板（`:app` 的 ClipboardWriter.writeForPaste）。 */
    @Volatile
    var writeClipboardForPaste: ((Context, ClipboardEntry) -> Unit)? = null

    /** 把一条剪贴板历史写回系统剪贴板（`:app` 的 ClipboardWriter.write）。 */
    @Volatile
    var writeClipboardEntry: ((Context, ClipboardEntry) -> Unit)? = null
    /** 拖动卡片时隐藏/恢复暂存浮窗。 */
    @Volatile
    var setStashDragHidden: ((Boolean) -> Unit)? = null

    /** 分享文本。 */
    @Volatile
    var shareText: ((Context, String, () -> Unit) -> Unit)? = null

    /** 分享截屏。 */
    @Volatile
    var shareScreenshot: ((Context, Bitmap, () -> Unit) -> Unit)? = null

    /** 保存截屏；返回是否成功。 */
    @Volatile
    var saveScreenshot: ((Context, Bitmap) -> Boolean)? = null

    /** 从剪贴板条目打开取词面板。 */
    @Volatile
    var openPickResultFromClipboard: ((Context, ClipboardEntry, Int) -> Unit)? = null

    /** 从暂存条目打开取词面板。 */
    @Volatile
    var openPickResultFromStash: ((Context, StashEntry, Int) -> Unit)? = null

    /** 写入暂存面板宽度档位（0=COMPACT, 1=STANDARD）。 */
    @Volatile
    var setStashPanelWidthMode: ((Int) -> Unit)? = null

    /** 写入暂存面板自定义宽度比例。 */
    @Volatile
    var setStashPanelCustomWidthFraction: ((Float) -> Unit)? = null
    /** 取消一条提醒。 */
    @Volatile
    var reminderCancel: ReminderCancelHook? = null

    /** 排定一条提醒。 */
    @Volatile
    var reminderSchedule: ReminderScheduleHook? = null

    /** 批量重排提醒。 */
    @Volatile
    var reminderRescheduleAll: ReminderRescheduleAllHook? = null
}

/** 取消一条提醒。 */
fun interface ReminderCancelHook {
    fun cancel(context: Context, entryId: String)
}

/** 排定一条提醒。 */
fun interface ReminderScheduleHook {
    fun schedule(context: Context, entryId: String, atEpochMs: Long, text: String)
}

/** 批量重排提醒。 */
fun interface ReminderRescheduleAllHook {
    fun rescheduleAll(context: Context, reminders: Map<String, Long>, textOf: (String) -> String)
}
