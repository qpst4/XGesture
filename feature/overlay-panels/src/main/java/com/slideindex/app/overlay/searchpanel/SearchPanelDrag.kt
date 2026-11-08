package com.slideindex.app.overlay.searchpanel

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.overlay.dragdrop.DragFlowSource
import com.slideindex.app.overlay.history.HistoryDragPreview
import com.slideindex.app.overlay.history.HistoryEntryDragHelper
import com.slideindex.app.search.files.DeviceFileEntry

/**
 * 搜索面板结果的统一拖拽入口。
 *
 * 与侧边面板 / 剪贴板浮窗同一套链路：复用 [HistoryEntryDragHelper] 的 URI 重映射、
 * 授权、拖影与拖放监听；再通过 [DragFlowSource] 把"结束后恢复还是关掉"交给流转栏。
 * 搜索面板本身在起拖时只 [SearchPanelOverlayWindow.suspendForDrag] 藏起来，不销毁，
 * 所以【恢复】能把搜索词、结果和滚动位置原样带回来。
 */
object SearchPanelDrag {

    /** 计算卡片 / 联系人这类纯文本。 */
    fun text(view: View, text: String, title: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val clip = ClipData.newPlainText("text", trimmed)
        return start(view, clip, HistoryDragPreview(text = trimmed), title)
    }

    /** 剪贴板条目：文本 / HTML / 图片 URI 都由 [ClipboardWriter] 统一打包。 */
    fun clipboard(view: View, context: Context, entry: ClipboardEntry): Boolean {
        val clip = com.slideindex.app.overlay.panels.PanelStashWriteHooks
        .buildClipForEntry?.invoke(context, entry) ?: return false
        val preview = HistoryEntryDragHelper.previewForClipboardEntry(entry, emptyList())
        val title = clip.description.label?.toString()?.takeIf { it.isNotBlank() }
            ?: entry.text.trim().lineSequence().firstOrNull()?.take(40)
            ?: ""
        return start(view, clip, preview, title)
    }

    /** 文件搜索结果：直接用条目自带的 URI（MediaStore / SAF），不需要再走 FileProvider。 */
    fun file(view: View, context: Context, file: DeviceFileEntry): Boolean {
        if (file.isDirectory) return false
        val mime = file.mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "*/*" }
            ?: runCatching { context.contentResolver.getType(file.uri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            ?: "*/*"
        val clip = ClipData(
            ClipDescription(file.displayName, arrayOf(mime)),
            ClipData.Item(file.uri),
        )
        return start(view, clip, HistoryDragPreview(text = file.displayName), file.displayName)
    }

    private fun start(
        view: View,
        clip: ClipData,
        preview: HistoryDragPreview,
        title: String,
    ): Boolean {
        // 恢复 = 面板原样回来；关掉 = 关掉搜索面板（查询词会持久化）。
        val source = DragFlowSource(
            onRestore = { SearchPanelOverlayWindow.resumeFromDrag() },
            onDiscard = { SearchPanelOverlayWindow.dismiss() },
        )
        return HistoryEntryDragHelper.startDrag(
            view = view,
            clipData = clip,
            preview = preview,
            dragShadow = SearchPanelDragShadow(view, title),
            onDragStart = { SearchPanelOverlayWindow.suspendForDrag() },
            // 没被流转栏认领（开关关闭 / MIME 不支持）时自己兜底把面板放回来。
            onDragEnd = { SearchPanelOverlayWindow.resumeFromDrag() },
            dragFlowSource = source,
        )
    }
}

/**
 * 搜索面板的拖影：一块紧凑的深色玻璃胶囊 + 一行标题。
 *
 * 不拿整张卡片做拖影：文件 / 联系人卡片可能很高，整卡拖起来会挡住目标区域；
 * 计算卡片又是大号数字，同样不适合。胶囊只显示"在拖什么"。
 */
private class SearchPanelDragShadow(
    view: View,
    private val label: String,
) : View.DragShadowBuilder(view) {

    private val density = view.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF01F1F23.toInt() }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6C8CFF.toInt() }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 13f * view.resources.configuration.fontScale * density
    }

    private val widthPx = (216 * density).toInt().coerceAtLeast(1)
    private val heightPx = (46 * density).toInt().coerceAtLeast(1)
    private val padPx = 14f * density
    private val dotRadiusPx = 4.5f * density

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(widthPx, heightPx)
        outShadowTouchPoint.set(widthPx / 2, heightPx / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val radius = heightPx / 2f
        canvas.drawRoundRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), radius, radius, bgPaint)

        val centerY = heightPx / 2f
        canvas.drawCircle(padPx + dotRadiusPx, centerY, dotRadiusPx, accentPaint)

        val textLeft = padPx + dotRadiusPx * 2 + 9f * density
        val maxWidth = widthPx - textLeft - padPx
        if (maxWidth <= 0f) return
        val shown = TextUtils.ellipsize(
            label.ifBlank { "·" },
            textPaint,
            maxWidth,
            TextUtils.TruncateAt.END,
        )
        val baseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(shown, 0, shown.length, textLeft, baseline, textPaint)
    }
}
