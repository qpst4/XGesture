package com.slideindex.app.overlay.history

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import com.slideindex.app.util.AccessibilityForegroundResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.text.TextPaint
import android.view.DragEvent
import android.view.View
import android.view.View.DragShadowBuilder
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardDragClipHelper
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.DragFileMirror
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.stash.exportText
import com.slideindex.app.stash.resolvedContentBlocks

internal data class HistoryDragPreview(
    val text: String = "",
    val bitmap: Bitmap? = null,
    /**
     * "**整张卡**"的快照（[HistoryCardSnapshot.capture] 出来的那一张）。
     *
     * 有它 = 拖影就是这张卡本身（圆角 + 约 92% 不透明 + 轻投影），跟 iOS 的 drag preview /
     * Android 自家 Launcher 的观感一致；没有（老调用点、或抓图失败）= 退回 [text]/[bitmap]
     * 那套旧画法（几行文字 / 一张缩略图）。
     */
    val snapshot: Bitmap? = null,
)

internal object HistoryEntryDragHelper {

    fun buildClipForStashEntry(context: Context, entry: StashEntry, repo: StashRepository?): ClipData? {
        return when (entry.type) {
            StashEntryType.TEXT -> {
                val text = entry.text.orEmpty().trim()
                if (text.isBlank()) null else ClipData.newPlainText("stash", text)
            }
            StashEntryType.IMAGE -> {
                val uri = repo?.uriForFile(entry.imageFileName) ?: return null
                ClipData.newUri(context.contentResolver, "image/*", uri)
            }
            StashEntryType.RICH -> {
                val blocks = entry.resolvedContentBlocks()
                if (blocks.isEmpty()) return null
                ClipboardWriter.buildClipForBlocks(
                    htmlText = entry.htmlText,
                    blocks = blocks,
                    resolveDataUri = { fileName -> repo?.dataUriForFile(fileName) },
                    resolveContentUri = { fileName -> repo?.uriForFile(fileName) },
                    resolveDimensions = { fileName -> repo?.imageDimensions(fileName) },
                )
            }
        }
    }

    fun previewForClipboardEntry(entry: ClipboardEntry, thumbnails: List<Bitmap>): HistoryDragPreview {
        val text = entry.text.trim().ifBlank { entry.resolvedContentBlocks().joinToString("\n") { block ->
            block.text.trim()
        }.trim() }
        val bitmap = thumbnails.firstOrNull()
        return HistoryDragPreview(text = text, bitmap = bitmap)
    }

    fun previewForStashEntry(
        entry: StashEntry,
        singleThumb: Bitmap?,
        richThumbnails: List<Bitmap>,
    ): HistoryDragPreview {
        val text = when (entry.type) {
            StashEntryType.TEXT -> entry.text.orEmpty()
            // §0.16.21：用 exportText（含 `[语音 0:12]`）—— 只录了音的条目用 combinedText 会是空标签。
            StashEntryType.RICH -> entry.exportText()
            else -> ""
        }
        val bitmap = when (entry.type) {
            StashEntryType.IMAGE -> singleThumb
            StashEntryType.RICH -> richThumbnails.firstOrNull()
            else -> null
        }
        return HistoryDragPreview(text = text, bitmap = bitmap)
    }

    fun startDrag(
        view: View,
        clipData: ClipData,
        preview: HistoryDragPreview,
        onDragStart: () -> Unit,
        onDragEnd: () -> Unit,
        onDropRejected: (() -> Unit)? = null,
    ): Boolean {
        val context = view.context
        val targetPackage = AccessibilityForegroundResolver.resolve(context)
        val preparation = ClipboardDragClipHelper.remapForHostIfNeeded(
            context = context,
            clipData = clipData,
            hostPackage = targetPackage,
        ) ?: return false
        val effectiveClip = preparation.clipData
        view.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> onDragStart()
                DragEvent.ACTION_DRAG_ENDED -> {
                    val accepted = event.result
                    Log.i(
                        TAG,
                        "drag ended accepted=$accepted target=$targetPackage " +
                            "mirrors=${preparation.mirrorSession.mirrors.size}",
                    )
                    if (accepted) {
                        // 宿主接下了这次投放：它松手时已经拿到文件，只留异步拷贝的余量。
                        preparation.mirrorSession.releaseAfter(DragFileMirror.DROP_RELEASE_GRACE_MS)
                    } else {
                        // 没有任何接收方会读这份镜像（分享兜底用的是原始 clip），立即回收。
                        preparation.mirrorSession.releaseNow()
                        onDropRejected?.invoke()
                    }
                    onDragEnd()
                    view.setOnDragListener(null)
                }
            }
            true
        }
        grantDragUriPermissionsToForegroundHost(context, effectiveClip, targetPackage)
        val flags = buildDragFlags(effectiveClip)
        view.startDragAndDrop(effectiveClip, EntryDragShadowBuilder(view, preview), null, flags)
        return true
    }

    /**
     * 部分宿主（如微信）除 [View.DRAG_FLAG_GLOBAL_URI_READ] 外仍要求对前台包显式 [Context.grantUriPermission]。
     */
    private fun grantDragUriPermissionsToForegroundHost(
        context: Context,
        clipData: ClipData,
        targetPackage: String?,
    ) {
        if (targetPackage.isNullOrBlank()) return
        if (targetPackage == context.packageName) return
        for (index in 0 until clipData.itemCount) {
            val uri = clipData.getItemAt(index).uri ?: continue
            if (uri.scheme != "content") continue
            try {
                context.grantUriPermission(
                    targetPackage,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "grant drag uri failed target=$targetPackage uri=$uri", t)
            }
        }
    }

    private fun buildDragFlags(clipData: ClipData): Int {
        var flags = View.DRAG_FLAG_GLOBAL
        for (index in 0 until clipData.itemCount) {
            if (clipData.getItemAt(index).uri != null) {
                flags = flags or View.DRAG_FLAG_GLOBAL_URI_READ
                break
            }
        }
        return flags
    }

    private const val TAG = "HistoryEntryDrag"
}

private class EntryDragShadowBuilder(
    view: View,
    private val preview: HistoryDragPreview,
) : DragShadowBuilder(view) {
    private val density = view.resources.displayMetrics.density
    private val hasBitmap = preview.bitmap != null
    /** 有快照时，四周留一圈给投影（否则阴影会被拖影位图裁掉）。 */
    private val shadowPad = (density * 14).toInt()
    private val width = preview.snapshot?.let { it.width + shadowPad * 2 }
        ?: (density * if (hasBitmap) 160 else 180).toInt()
    private val height = preview.snapshot?.let { it.height + shadowPad * 2 }
        ?: (density * if (hasBitmap) 108 else 72).toInt()

    private val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(17, 24, 39)
        textSize = density * view.resources.configuration.fontScale * 14
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    /** 卡片轮廓：圆角 + 柔和投影（把整张卡"拿起来"的那层影子）。 */
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snapshotPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cardCornerPx = density * 18f

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height)
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val snapshot = preview.snapshot
        if (snapshot != null) {
            drawCardSnapshot(canvas, snapshot)
            return
        }

        val bitmap = preview.bitmap
        if (bitmap != null) {
            val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val dstRatio = width.toFloat() / height.toFloat()
            val src = if (srcRatio > dstRatio) {
                val srcWidth = (bitmap.height * dstRatio).toInt()
                val left = (bitmap.width - srcWidth) / 2
                android.graphics.Rect(left, 0, left + srcWidth, bitmap.height)
            } else {
                val srcHeight = (bitmap.width / dstRatio).toInt()
                val top = (bitmap.height - srcHeight) / 2
                android.graphics.Rect(0, top, bitmap.width, top + srcHeight)
            }
            canvas.drawBitmap(bitmap, src, RectF(0f, 0f, width.toFloat(), height.toFloat()), imagePaint)
            return
        }

        val normalized = preview.text.replace(Regex("\\s+"), " ").take(84)
        val firstLine = normalized.take(24)
        val secondLine = normalized.drop(24).take(24)
        val thirdLine = normalized.drop(48).take(24)
        val baseY = contentPaint.textSize
        canvas.drawText(firstLine, 0f, baseY, contentPaint)
        if (secondLine.isNotBlank()) {
            canvas.drawText(secondLine, 0f, baseY + contentPaint.textSize + 8, contentPaint)
        }
        if (thirdLine.isNotBlank()) {
            canvas.drawText(thirdLine, 0f, baseY + (contentPaint.textSize + 8) * 2, contentPaint)
        }
    }

    /**
     * 整张卡的拖影：**圆角 + 约 92% 不透明 + 一层轻投影**。
     *
     * 圆角矩形先铺一层"卡片底色"，再盖快照。底色取**快照正中心那一个像素**——卡片底色现在
     * 是单色（见 `HistoryTheme.cardColor`），中心像素就等于整张卡的底色；这样快照四个透明角
     * 不会露出脏边，深色模式也不用另配颜色。
     */
    private fun drawCardSnapshot(canvas: Canvas, snapshot: Bitmap) {
        val left = shadowPad.toFloat()
        val top = shadowPad.toFloat()
        val right = left + snapshot.width
        val bottom = top + snapshot.height
        val fill = runCatching {
            snapshot.getPixel(snapshot.width / 2, snapshot.height / 2)
        }.getOrDefault(android.graphics.Color.WHITE)

        cardPaint.color = fill
        cardPaint.setShadowLayer(density * 10f, 0f, density * 5f, SHADOW_COLOR)
        canvas.drawRoundRect(left, top, right, bottom, cardCornerPx, cardCornerPx, cardPaint)
        cardPaint.clearShadowLayer()

        snapshotPaint.alpha = CARD_ALPHA
        canvas.drawBitmap(snapshot, left, top, snapshotPaint)
    }

    private companion object {
        /** ≈92%：系统拖影就是"半透明地跟着手指"，不是实心图。 */
        const val CARD_ALPHA = 235

        /** 30% 黑：够看出"浮起来"，又不至于在浅色面板上发脏。 */
        const val SHADOW_COLOR = 0x4D000000.toInt()
    }
}
