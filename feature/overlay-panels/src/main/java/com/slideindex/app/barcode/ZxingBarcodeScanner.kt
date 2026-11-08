package com.slideindex.app.barcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.slideindex.app.overlay.FloatBallOcrRegions
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.Binarizer
import com.google.zxing.NotFoundException
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ZxingBarcodeScanner {
    private const val MAX_SCAN_DIMENSION = 2000
    private const val PICK_UPSCALE_MIN_DIMENSION = 960
    private const val PICK_UPSCALE_FACTOR = 2f

    /**
     * 超过这个边长的图不再补白边。整屏截图四周本来就有网页/窗口留出的空隙，
     * 再补 12% 白边只会把 1080×2340 撑到 1340×2902，逼出更狠的缩放、白占内存。
     */
    private const val PAD_MAX_DIMENSION = 1200

    /**
     * 取词面板后台自动识码的预算。第一遍 2D 在整屏图上大约 0.2~1 秒（命中就返回），
     * 这里留足余量；预算主要用来砍掉"图里根本没码"时最后那遍全码制（一维条码 / PDF417）。
     */
    const val PANEL_SCAN_BUDGET_MS = 2_500L

    private val allFormatHints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODABAR,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.ITF
        )
    )

    private val qrHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX
        )
    )

    private val qrTryHarderHints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC
        )
    )

    /**
     * 定格图（截图 / 分享图 / 取词裁剪块）的识码入口。
     *
     * [pickFastPath] 的取词路径先跑一遍 2D（TRY_HARDER），命中立刻返回；
     * 取词之外的入口（分享图片等）仍只跑一遍全码制——那些链路是**先识码再出面板**，
     * 多跑一遍只会把面板拖晚。
     *
     * @param pickFastPath 取词裁剪块：补白边（仅小图）+ 小图放大，弥补用户框得太紧、码太小的场景；
     *   并启用"2D 优先"的短路顺序。
     * @param budgetMs 整次扫描的时间预算，[Long.MAX_VALUE] 表示不限。超预算立刻停，
     *   省得后台识码在低端机上拖住 CPU、和 OCR 抢核。
     * @param shouldCancel 每个解码尝试前询问一次；面板已经关掉时调用方可以直接放弃。
     */
    fun scanBitmap(
        bitmap: Bitmap,
        pickFastPath: Boolean = false,
        budgetMs: Long = Long.MAX_VALUE,
        shouldCancel: (() -> Boolean)? = null,
    ): List<BarcodeScanResult> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val startedAt = SystemClock.elapsedRealtime()
        val prepared = prepareScanBitmap(bitmap, pickFastPath)
        val shouldRecyclePrepared = prepared !== bitmap
        return try {
            val width = prepared.width
            val height = prepared.height
            val pixels = IntArray(width * height)
            prepared.getPixels(pixels, 0, width, 0, 0, width, height)
            // 4 种亮度变体按需生成：整屏图每份就有 2~4MB，没必要一开始全建出来。
            var standard: ByteArray? = null
            var contrast: ByteArray? = null

            fun scanWith(hints: Map<DecodeHintType, Any>): List<BarcodeScanResult>? {
                for (variant in 0 until 4) {
                    if (scanShouldStop(startedAt, budgetMs, shouldCancel)) return null
                    val luminance = when (variant) {
                        0 -> rgbLuminance(pixels).also { standard = it }
                        1 -> invertLuminance(standard ?: rgbLuminance(pixels).also { standard = it })
                        2 -> contrastStretchedLuminance(pixels).also { contrast = it }
                        else -> invertLuminance(contrast ?: contrastStretchedLuminance(pixels).also { contrast = it })
                    }
                    val source = ByteArrayLuminanceSource(width, height, luminance)
                    for (binarizer in BinarizerStrategy.entries) {
                        if (scanShouldStop(startedAt, budgetMs, shouldCancel)) return null
                        tryDecode(source, hints, binarizer, width, height)
                            .takeIf { it.isNotEmpty() }
                            ?.let { return it }
                    }
                }
                return null
            }

            if (pickFastPath) {
                // 直接上 TRY_HARDER：实测非 TRY_HARDER 那一遍在整屏图上很容易"抽行跳过"定位图案，
                // 白跑一遍还要多花几百毫秒，不如把这一遍省掉。
                scanWith(qrTryHarderHints)?.let { return it }
                if (scanShouldStop(startedAt, budgetMs, shouldCancel)) return emptyList()
            }
            scanWith(allFormatHints).orEmpty()
        } finally {
            if (shouldRecyclePrepared) {
                prepared.recycle()
            }
        }
    }

    private fun prepareScanBitmap(bitmap: Bitmap, pickFastPath: Boolean): Bitmap {
        if (!pickFastPath) {
            return scaleDownIfNeeded(bitmap, MAX_SCAN_DIMENSION)
        }
        val padded = padQuietZone(bitmap, FloatBallOcrRegions.PICK_CROP_EXPAND_FRACTION)
        val upscaled = scaleUpIfSmall(padded)
        if (upscaled !== padded) {
            padded.recycle()
        }
        val limited = scaleDownIfNeeded(upscaled, MAX_SCAN_DIMENSION)
        if (limited !== upscaled) {
            upscaled.recycle()
        }
        return limited
    }

    /** Simulates screen-space quiet-zone expand for pick crops without changing the UI bitmap. */
    private fun padQuietZone(bitmap: Bitmap, fraction: Float): Bitmap {
        if (fraction <= 0f) return bitmap
        // 大图（整屏截图）不补：本身四周就有留白，补了只会把图撑大、压缩更狠。
        if (max(bitmap.width, bitmap.height) > PAD_MAX_DIMENSION) return bitmap
        val padX = (bitmap.width * fraction).roundToInt()
        val padY = (bitmap.height * fraction).roundToInt()
        if (padX <= 0 && padY <= 0) return bitmap
        val padded = createBitmap(
            bitmap.width + padX * 2,
            bitmap.height + padY * 2
        )
        Canvas(padded).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, padX.toFloat(), padY.toFloat(), null)
        }
        return padded
    }

    /** 预算到点或调用方要求取消：下一个解码尝试前就会停。 */
    private fun scanShouldStop(
        startedAt: Long,
        budgetMs: Long,
        shouldCancel: (() -> Boolean)?,
    ): Boolean {
        if (shouldCancel?.invoke() == true) return true
        return budgetMs != Long.MAX_VALUE &&
            SystemClock.elapsedRealtime() - startedAt >= budgetMs
    }

    /**
     * **触发专用**的轻量检查（相机预览帧流）：只回答"这一帧里有没有码、是哪个"。
     *
     * 与 [scanBitmap] 的分工，是被真机实测数据定下来的：
     *
     * - 这里**每帧都要跑**，所以刻意用最便宜的组合——**单码读取器 + 只有主二值化 + 只试标准亮度**。
     *   实测日志显示"多码读取器 + 两种二值化"在 1280×960 上要 **130~157ms/帧**，这个量级足以把
     *   预览拖成"一直一顿一顿"；而 [GenericMultipleBarcodeReader] 更是"找到一个→涂掉→递归再找"，
     *   单帧成本高得多，触发阶段根本不需要它。
     * - 权威的"把一屏里的码都找出来"交给 [scanBitmap]，它只在**定格图上跑一次**（全码制 +
     *   TRY_HARDER + 全部亮度变体），既准又不在预览路径上。
     *
     * @param qrOnly 只试二维码类码制（QR / DataMatrix）；调用方可每隔若干帧传 false 兼顾一维条码。
     */
    fun scanLuminanceForTrigger(
        luminance: ByteArray,
        width: Int,
        height: Int,
        qrOnly: Boolean = true,
    ): List<BarcodeScanResult> {
        if (width <= 0 || height <= 0 || luminance.size < width * height) return emptyList()
        val source = ByteArrayLuminanceSource(width, height, luminance)
        val hints = if (qrOnly) qrHints else allFormatHints
        return try {
            val result = MultiFormatReader()
                .apply { setHints(hints) }
                .decode(BinaryBitmap(HybridBinarizer(source)))
            listOfNotNull(toScanResult(result, width, height))
        } catch (_: ReaderException) {
            emptyList()
        }
    }

    /** [Result] → 领域结果；文本为空返回 null。定位点归一化到 0..1。 */
    private fun toScanResult(result: Result, width: Int, height: Int): BarcodeScanResult? {
        val text = result.text?.trim().orEmpty()
        if (text.isEmpty()) return null
        return BarcodeScanResult(
            text = text,
            format = result.barcodeFormat.name,
            points = result.resultPoints
                ?.filterNotNull()
                ?.map { point ->
                    BarcodePoint(
                        x = (point.x / width).coerceIn(0f, 1f),
                        y = (point.y / height).coerceIn(0f, 1f)
                    )
                }
                .orEmpty()
        )
    }

    /** RGB 像素 → 亮度（ITU-R BT.601）。 */
    private fun rgbLuminance(pixels: IntArray): ByteArray {
        val output = ByteArray(pixels.size)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            output[index] = (0.299 * red + 0.587 * green + 0.114 * blue).roundToInt()
                .coerceIn(0, 255)
                .toByte()
        }
        return output
    }

    /** 对比度拉伸的 IntArray 版本（位图路径专用）。 */
    private fun contrastStretchedLuminance(pixels: IntArray): ByteArray {
        val gray = IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            (0.299 * red + 0.587 * green + 0.114 * blue).roundToInt()
        }
        var min = 255
        var max = 0
        for (value in gray) {
            min = min(min, value)
            max = max(max, value)
        }
        if (max <= min) {
            return gray.map { it.coerceIn(0, 255).toByte() }.toByteArray()
        }
        return ByteArray(gray.size) { index ->
            (((gray[index] - min) * 255f) / (max - min)).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    private fun invertLuminance(luminance: ByteArray): ByteArray =
        ByteArray(luminance.size) { index ->
            (255 - (luminance[index].toInt() and 0xFF)).toByte()
        }

    private enum class BinarizerStrategy {
        Hybrid {
            override fun create(source: LuminanceSource): Binarizer = HybridBinarizer(source)
        },
        GlobalHistogram {
            override fun create(source: LuminanceSource): Binarizer = GlobalHistogramBinarizer(source)
        },
        ;

        abstract fun create(source: LuminanceSource): Binarizer
    }

    private fun tryDecode(
        source: LuminanceSource,
        hints: Map<DecodeHintType, Any>,
        binarizer: BinarizerStrategy,
        width: Int,
        height: Int
    ): List<BarcodeScanResult> {
        val binaryBitmap = BinaryBitmap(binarizer.create(source))
        val reader = GenericMultipleBarcodeReader(
            MultiFormatReader().apply { setHints(hints) }
        )
        return try {
            // hints 必须一路传进去。单参 decodeMultiple(bitmap) 内部是 decode(bitmap, null)，
            // MultiFormatReader 会拿这 null 重新 setHints，把 TRY_HARDER / POSSIBLE_FORMATS 全清掉：
            // 大图退化成"隔十几行采一次"（FinderPatternFinder 的 iSkip 随图高变大），
            // 定位图案正好被跳过，整屏图就会漏掉明明很清楚的二维码；限定码制也会失效、冒出一维码误报。
            reader.decodeMultiple(binaryBitmap, hints)
                .mapNotNull { toScanResult(it, width, height) }
                .distinctBy { "${it.format}:${it.text}" }
        } catch (_: NotFoundException) {
            emptyList()
        }
    }

    private fun scaleUpIfSmall(bitmap: Bitmap): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim >= PICK_UPSCALE_MIN_DIMENSION) return bitmap
        val targetMax = min(
            (maxDim * PICK_UPSCALE_FACTOR).roundToInt(),
            MAX_SCAN_DIMENSION
        ).coerceAtLeast(maxDim)
        if (targetMax <= maxDim) return bitmap
        val scale = targetMax.toFloat() / maxDim
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(targetWidth, targetHeight)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxDim
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(targetWidth, targetHeight)
    }

    private class ByteArrayLuminanceSource(
        private val width: Int,
        private val height: Int,
        private val luminance: ByteArray
    ) : LuminanceSource(width, height) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val output = row ?: ByteArray(width)
            System.arraycopy(luminance, y * width, output, 0, width)
            return output
        }

        override fun getMatrix(): ByteArray = luminance

        override fun isCropSupported(): Boolean = true

        override fun crop(left: Int, top: Int, width: Int, height: Int): LuminanceSource {
            require(left >= 0 && top >= 0)
            require(left + width <= this.width && top + height <= this.height)
            val cropped = ByteArray(width * height)
            val rowStride = this.width
            for (y in 0 until height) {
                System.arraycopy(
                    luminance,
                    (top + y) * rowStride + left,
                    cropped,
                    y * width,
                    width
                )
            }
            return ByteArrayLuminanceSource(width, height, cropped)
        }

        override fun isRotateSupported(): Boolean = false
    }
}
