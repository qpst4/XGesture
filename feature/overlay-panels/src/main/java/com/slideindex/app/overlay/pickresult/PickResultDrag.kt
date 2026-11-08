package com.slideindex.app.overlay.pickresult

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.content.FileProvider
import com.slideindex.app.overlay.dragdrop.DragFlowSource
import com.slideindex.app.overlay.history.HistoryDragPreview
import com.slideindex.app.overlay.history.HistoryEntryDragHelper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * 取词面板的「拖出去」：把当前图片 / 当前选中文字起成一次系统拖拽。
 *
 * 与搜索面板、二维码浮窗同一条链路：复用 [HistoryEntryDragHelper] 的 URI 重映射
 * （微信 / QQ 这类只认系统媒体库的宿主会自动镜像到 MediaStore）、前台包授权、
 * 拖影与拖放监听；再通过 [DragFlowSource] 把「结束后恢复还是关掉」交给流转栏：
 * 恢复 = 面板原样回来，取消 / 命中目标 = 关掉面板。
 *
 * 起拖时面板只隐藏不销毁（见 [FloatBallPickResultPanel.suspendForDrag]），
 * 所以中途取消能把文字、图片和滚动位置都原样带回来。
 */
object PickResultDrag {

    private const val TEXT_LABEL = "pick_text"
    private const val IMAGE_LABEL = "pick_image"

    /** 图片拖拽用的临时文件目录（cache 下，单独一层方便清理）。 */
    private const val CACHE_DIR = "pick_drag"

    /** 超过这个时间的缓存图片当作没人会再读：起新拖拽时顺手清掉。 */
    private const val CACHE_TTL_MS = 60 * 60 * 1000L

    /**
     * 文字拖拽：纯文本，成本为零。
     *
     * [dark] 只影响拖影配色——拖影要跟面板正文一套底色，浅色面板上拖出一块黑卡太突兀。
     */
    fun text(view: View, text: String, dark: Boolean = false): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // 与「分享」按钮同一个载荷：选中了就是选中的那段，没选就是整段正文。
        val clip = ClipData.newPlainText(TEXT_LABEL, trimmed)
        return start(
            view = view,
            clipData = clip,
            preview = HistoryDragPreview(text = trimmed),
            shadow = PickResultDragShadow.forText(view, trimmed, dark),
        )
    }

    /**
     * 图片拖拽：先把位图落成 FileProvider 能读的 PNG，再交给拖拽框架。
     *
     * 这一步必须在主线程之外想别的办法，但系统拖拽只能「手指按着时」起，所以这里
     * 同步压一次。面板侧按位图身份做了缓存（`FloatBallPickResultPanel.dragImageUriFor`）：
     * 同一张图只压一次，重复长按不再卡；换图 / 重开面板后缓存自然失效。
     */
    fun image(view: View, context: Context, bitmap: Bitmap, uri: Uri?): Boolean {
        if (bitmap.isRecycled) return false
        val imageUri = uri ?: writeImageUri(context, bitmap) ?: return false
        val clip = ClipData(
            ClipDescription(IMAGE_LABEL, arrayOf("image/*")),
            ClipData.Item(imageUri),
        )
        return start(
            view = view,
            clipData = clip,
            preview = HistoryDragPreview(bitmap = bitmap),
            shadow = PickResultDragShadow.forImage(view, bitmap),
        )
    }

    private fun start(
        view: View,
        clipData: ClipData,
        preview: HistoryDragPreview,
        shadow: View.DragShadowBuilder,
    ): Boolean = HistoryEntryDragHelper.startDrag(
        view = view,
        clipData = clipData,
        preview = preview,
        onDragStart = { com.slideindex.app.overlay.panels.PanelRuntimeHooks.suspendPickResultForDrag?.invoke() },
        // 没被流转栏认领（开关关闭 / MIME 不支持）时自己兜底把面板放回来。
        onDragEnd = { com.slideindex.app.overlay.panels.PanelRuntimeHooks.resumePickResultFromDrag?.invoke() },
        dragShadow = shadow,
        dragFlowSource = DragFlowSource(
            onRestore = { com.slideindex.app.overlay.panels.PanelRuntimeHooks.resumePickResultFromDrag?.invoke() },
            onDiscard = { com.slideindex.app.overlay.panels.PanelRuntimeHooks.dismissPickResultPanel?.invoke() },
        ),
    )

    /**
     * 位图 → cache 里的 PNG → FileProvider URI；失败返回 null（调用方给面板内提示）。
     *
     * ⚠️ `pick_drag/` 必须在 `res/xml/file_paths.xml` 里登记，否则 `getUriForFile`
     * 会抛 `IllegalArgumentException`（被 runCatching 吞掉后表现成「拖拽直接失败」）。
     */
    fun writeImageUri(context: Context, bitmap: Bitmap): Uri? {
        val app = context.applicationContext
        return runCatching {
            val dir = File(app.cacheDir, CACHE_DIR).apply { mkdirs() }
            purgeStale(dir)
            val file = File(dir, "pick_drag_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    error("compress failed")
                }
                output.flush()
            }
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        }.getOrNull()
    }

    /**
     * 只清「很久没用的」旧文件：刚起拖的那张可能还在被接收方读，不能跟着面板一起删。
     * 系统清理 cacheDir 时也会一并回收。
     */
    private fun purgeStale(dir: File) {
        val deadline = System.currentTimeMillis() - CACHE_TTL_MS
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < deadline) {
                    file.delete()
                }
            }
        }
    }
}

/**
 * 取词面板的拖影。
 *
 * - 图片：圆角缩略卡片 + 投影，保持原始宽高比（不裁切），和系统 Launcher 拿起一张图的观感一致；
 * - 文字：**和面板正文同一套配色**的圆角卡片，宽度按内容测量、最多 3 行、末尾省略，
 *   让人一眼看出拖的是哪一段；
 * - 都不拿整张面板做拖影——那会挡住落点。
 */
private class PickResultDragShadow private constructor(
    view: View,
    text: CharSequence?,
    private val bitmap: Bitmap?,
    dark: Boolean,
) : View.DragShadowBuilder(view) {

    private val density = view.resources.displayMetrics.density
    private val fontScale = view.resources.configuration.fontScale

    /** 四周留给投影的余量，否则阴影会被拖影位图裁掉。 */
    private val pad = (density * 12).toInt()

    // 拖影字号 = 正文基准 14sp × TEXT_SCALE。真机上 1 倍看不清（用户实测），
    // 先放大到 2 倍；再嫌小就调这一个常量。
    private val hPad = (14 * density * TEXT_SCALE).toInt()
    private val vPad = (10 * density * TEXT_SCALE).toInt()

    /**
     * 文字卡最大宽度：按字号等比放大，但必须留在屏幕内
     * （拖影位图 + 投影边距 + 内边距都不能超出屏幕）。
     */
    private val maxTextWidth = minOf(
        (260 * density * TEXT_SCALE).toInt(),
        (view.resources.displayMetrics.widthPixels - pad * 2 - hPad * 2 - (16 * density).toInt())
            .coerceAtLeast(1),
    )
    private val minTextWidth = (72 * density * TEXT_SCALE).toInt()

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) 0xFFECECED.toInt() else 0xFF1F1F23.toInt()
        textSize = BASE_TEXT_SP * TEXT_SCALE * fontScale * density
    }

    /** 静态排版：最多三行、超出省略；卡片宽度按实际行宽反推，短文本不会拖一条空卡。 */
    private val layout: StaticLayout? = text
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { value ->
            StaticLayout.Builder
                .obtain(value, 0, value.length, textPaint, maxTextWidth.coerceAtLeast(1))
                .setMaxLines(TEXT_MAX_LINES)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) 0xF21C1C1E.toInt() else 0xF2FFFFFF.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) 0x24FFFFFF else 0x14000000
        style = Paint.Style.STROKE
        strokeWidth = density * 0.6f
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val contentWidth: Int
    private val contentHeight: Int
    private val imageSrc = Rect()
    private val imageDst = RectF()
    private val cornerPx = density * 14f

    init {
        val source = bitmap
        if (source != null && !source.isRecycled) {
            // 等比缩进 200 × 200dp 的方框里，避免大截图把落点全挡住。
            val maxSide = density * 200f
            val ratio = source.width.toFloat() / source.height.toFloat()
            if (ratio >= 1f) {
                contentWidth = maxSide.toInt()
                contentHeight = (maxSide / ratio).toInt().coerceAtLeast(1)
            } else {
                contentHeight = maxSide.toInt()
                contentWidth = (maxSide * ratio).toInt().coerceAtLeast(1)
            }
        } else {
            val lines = layout
            val measured = if (lines == null || lines.lineCount == 0) {
                (minTextWidth - hPad * 2).toFloat()
            } else {
                (0 until lines.lineCount).maxOf { lines.getLineWidth(it) }
            }
            contentWidth = (ceil(measured).toInt() + hPad * 2)
                .coerceIn(minTextWidth, maxTextWidth + hPad * 2)
            contentHeight = ((lines?.height ?: 0) + vPad * 2)
                .coerceAtLeast((40 * density).toInt())
        }
    }

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(contentWidth + pad * 2, contentHeight + pad * 2)
        outShadowTouchPoint.set((contentWidth + pad * 2) / 2, (contentHeight + pad * 2) / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val left = pad.toFloat()
        val top = pad.toFloat()
        val right = left + contentWidth
        val bottom = top + contentHeight

        val source = bitmap
        if (source != null && !source.isRecycled) {
            drawImageCard(canvas, source, left, top, right, bottom)
            return
        }
        drawTextCard(canvas, left, top, right, bottom)
    }

    private fun drawImageCard(
        canvas: Canvas,
        source: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        cardPaint.color = 0xFFFFFFFF.toInt()
        cardPaint.setShadowLayer(density * 9f, 0f, density * 4f, SHADOW_COLOR)
        canvas.drawRoundRect(left, top, right, bottom, cornerPx, cornerPx, cardPaint)
        cardPaint.clearShadowLayer()

        // 卡片比例已经等于图片比例，这里只会等比铺满，不会裁掉内容。
        val scale = maxOf(
            contentWidth.toFloat() / source.width,
            contentHeight.toFloat() / source.height,
        )
        val drawWidth = (source.width * scale).toInt()
        val drawHeight = (source.height * scale).toInt()
        val offsetX = (contentWidth - drawWidth) / 2
        val offsetY = (contentHeight - drawHeight) / 2
        imageSrc.set(0, 0, source.width, source.height)
        imageDst.set(
            left + offsetX,
            top + offsetY,
            left + offsetX + drawWidth,
            top + offsetY + drawHeight,
        )
        val clip = android.graphics.Path().apply {
            addRoundRect(left, top, right, bottom, cornerPx, cornerPx, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(source, imageSrc, imageDst, imagePaint)
        canvas.restore()
    }

    private fun drawTextCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        cardPaint.setShadowLayer(density * 9f, 0f, density * 4f, SHADOW_COLOR)
        canvas.drawRoundRect(left, top, right, bottom, cornerPx, cornerPx, cardPaint)
        cardPaint.clearShadowLayer()
        canvas.drawRoundRect(left, top, right, bottom, cornerPx, cornerPx, borderPaint)

        val lines = layout ?: return
        canvas.save()
        canvas.translate(left + hPad, top + vPad)
        lines.draw(canvas)
        canvas.restore()
    }

    companion object {
        /** 30% 黑：够看出「浮起来」，又不至于在浅色面板上发脏。 */
        private const val SHADOW_COLOR = 0x4D000000.toInt()

        /** 正文基准字号（与面板默认一致）与拖影放大倍数。 */
        private const val BASE_TEXT_SP = 14f
        private const val TEXT_SCALE = 2f

        private const val TEXT_MAX_LINES = 3

        fun forText(view: View, text: String, dark: Boolean): View.DragShadowBuilder =
            PickResultDragShadow(view, text, null, dark)

        fun forImage(view: View, bitmap: Bitmap): View.DragShadowBuilder =
            PickResultDragShadow(view, null, bitmap, dark = false)
    }
}
