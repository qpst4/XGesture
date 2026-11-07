package com.slideindex.app.barcode

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.charset.StandardCharsets

/**
 * 二维码生成（「快捷菜单 → 二维码」与 Wi-Fi 分享等场景的编码核心）。
 *
 * 背景：本仓库原本只有解码能力（[ZxingBarcodeScanner] 用 `MultiFormatReader`），没有任何
 * 编码能力。本类补上「文本 → 二维码位图」。
 *
 * 参考：QuickDash（https://github.com/balajitechlabs/quickdash，GPL-3.0）的 QRCodeGenerator
 * 提供过同类能力（ZXing 编码 + 自定义前景色 + 中央徽章 + 可选品牌页脚）。本实现按其行为规格
 * 独立重写，并修掉它三处失效实现（这三处在参考代码里都存在，但都不生效或互相矛盾）：
 * 1. 它的 `width` / `height` 参数被硬编码的 512 完全忽略，调用方传 1024 / 800 都无效；
 *    本实现严格按 [Style.sizePx] 出图（超出范围时夹取，不会静默改变尺寸）。
 * 2. 它的双色渐变给 `Paint.shader` 设了 `LinearGradient`，但实际绘制走 `setPixels` 路径，
 *    于是渐变**从未生效**；本实现把颜色处理收敛到「一份像素数组」这一条路径上，不存在
 *    画笔与像素路径不一致的可能。
 * 3. 它的 `useCircularDots` 参数被接收但函数体内从未使用（圆点样式未实现）；本实现不提供
 *    只会误导的空参数，改为真正可验证的能力：[generate]（纯二维码）与 [generateWithLogo]
 *    （中央徽章）。
 *
 * 设计约束：**所有渲染都只经过一份 `IntArray`**，不依赖 `Canvas` / `Paint`，因此
 * 尺寸、前景色、背景色、徽章是否真的生效都可以直接对像素断言（见同目录测试）。
 *
 * 依赖：ZXing `core`（Apache-2.0，本仓库已直接依赖 3.5.4）。
 */
object QrCodeGenerator {

    /** 默认出图边长（像素）。 */
    const val DEFAULT_SIZE_PX = 512

    /** 允许的最小边长；过小会导致模块被采样掉、扫不出来。 */
    const val MIN_SIZE_PX = 96

    /** 允许的最大边长；再大对扫码没有帮助，只是浪费内存。 */
    const val MAX_SIZE_PX = 2048

    /** 中央徽章的默认占比（占二维码边长）。 */
    const val DEFAULT_LOGO_SCALE = 0.22f

    /** 徽章占比下限：再小就看不清徽章内容了。 */
    const val MIN_LOGO_SCALE = 0.10f

    /**
     * 徽章占比上限 25%。
     *
     * 依据：纠错级别 H 约可恢复 30% 的码字，中央徽章按面积遮挡远小于按边长占比，
     * 25% 边长是常用的安全上限；再大就有识别不出二维码的风险。
     */
    const val MAX_LOGO_SCALE = 0.25f

    private const val COLOR_BLACK = 0xFF000000.toInt()
    private const val COLOR_WHITE = 0xFFFFFFFF.toInt()

    /**
     * 出图样式。
     *
     * @param sizePx 边长（像素）；会在 [MIN_SIZE_PX] ~ [MAX_SIZE_PX] 之间夹取。
     * @param foregroundColor 模块颜色（不透明 ARGB）。
     * @param backgroundColor 底色与徽章衬底颜色（不透明 ARGB）。
     * @param errorCorrectionLevel 纠错级别；叠加徽章时强制使用 H（见 [generateWithLogo]）。
     * @param quietZoneModules 静区宽度（单位：模块）。规范要求至少 1 个模块，默认 2 兼顾
     *   扫码可靠性与屏幕占位；UI 若已提供留白可降到 1。
     */
    data class Style(
        val sizePx: Int = DEFAULT_SIZE_PX,
        val foregroundColor: Int = COLOR_BLACK,
        val backgroundColor: Int = COLOR_WHITE,
        val errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.H,
        val quietZoneModules: Int = 2,
    )

    /**
     * 生成二维码位图。
     *
     * @return 边长恰为夹取后 `sizePx` 的 ARGB_8888 位图；内容为空 **或超出二维码容量** 时返回 null。
     */
    fun generate(content: String, style: Style = Style()): Bitmap? {
        val size = style.sizePx.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)
        val pixels = generatePixels(content, style) ?: return null
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /**
     * 生成带中央徽章的二维码位图。
     *
     * 为保证可扫性，本方法**忽略** [Style.errorCorrectionLevel] 并强制使用
     * [ErrorCorrectionLevel.H]：徽章会遮挡中央模块，只有最高纠错级别能可靠还原。
     * [logoScale] 同样会被夹取到 [MIN_LOGO_SCALE] ~ [MAX_LOGO_SCALE]。
     *
     * @return 边长恰为夹取后 `sizePx` 的 ARGB_8888 位图；内容为空或超出容量时返回 null。
     */
    fun generateWithLogo(
        content: String,
        logo: Bitmap,
        style: Style = Style(),
        logoScale: Float = DEFAULT_LOGO_SCALE,
    ): Bitmap? {
        val size = style.sizePx.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)
        val pixels = generatePixelsWithLogo(content, logo, style, logoScale) ?: return null
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /**
     * 只产出像素数组（行优先、长度 `size * size`、ARGB），不创建 [Bitmap]。
     *
     * 单独暴露的两个理由：一是让「尺寸/颜色是否真的生效」可以在不依赖 Android 图形的条件下
     * 直接断言；二是调用方若要直接编码 PNG 流或写入其他位图目标，不必先绕一圈 Bitmap。
     */
    fun generatePixels(content: String, style: Style = Style()): IntArray? =
        generatePixelsInternal(content, null, style, DEFAULT_LOGO_SCALE)

    /** [generatePixels] 的徽章版本；纠错级别与 [logoScale] 的夹取规则同 [generateWithLogo]。 */
    fun generatePixelsWithLogo(
        content: String,
        logo: Bitmap,
        style: Style = Style(),
        logoScale: Float = DEFAULT_LOGO_SCALE,
    ): IntArray? = generatePixelsInternal(content, logo, style, logoScale)

    private fun generatePixelsInternal(
        content: String,
        logo: Bitmap?,
        style: Style,
        logoScale: Float,
    ): IntArray? {
        if (content.isBlank()) return null
        val size = style.sizePx.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)
        val level = if (logo != null) ErrorCorrectionLevel.H else style.errorCorrectionLevel
        val matrix = encode(content, size, level, style.quietZoneModules) ?: return null
        val pixels = rasterize(matrix, size, style)
        if (logo != null) {
            drawLogo(
                pixels = pixels,
                size = size,
                logo = logo,
                logoScale = logoScale,
                padColor = style.backgroundColor,
            )
        }
        return pixels
    }

    private fun encode(
        content: String,
        size: Int,
        level: ErrorCorrectionLevel,
        quietZoneModules: Int,
    ): BitMatrix? = try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to level,
            EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
            EncodeHintType.MARGIN to quietZoneModules.coerceAtLeast(0),
        )
        // 用最高纠错级别时，同样的内容模块数更多；请求尺寸过小时 ZXing 会自增矩阵尺寸，
        // 交由 rasterize 的最近邻采样缩回到请求尺寸（见下方注释）。
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    } catch (_: WriterException) {
        // 内容超出二维码容量
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * 把模块矩阵铺成 `size × size` 的像素数组。
     *
     * ZXing 返回的矩阵边长 ≥ 请求尺寸且模块按整数倍放大，因此常见情况下
     * `matrix.width == size`，此时采样是**一一对应**的（模块边缘锐利、不糊）。
     * 只有内容很长、模块数逼近请求尺寸时才会真正缩放，退化为最近邻采样。
     */
    private fun rasterize(matrix: BitMatrix, size: Int, style: Style): IntArray {
        val pixels = IntArray(size * size)
        val sourceWidth = matrix.width
        val sourceHeight = matrix.height
        for (y in 0 until size) {
            val sourceY = y * sourceHeight / size
            val rowOffset = y * size
            for (x in 0 until size) {
                val sourceX = x * sourceWidth / size
                pixels[rowOffset + x] =
                    if (matrix.get(sourceX, sourceY)) style.foregroundColor else style.backgroundColor
            }
        }
        return pixels
    }

    /**
     * 把徽章画到像素数组中央：先铺一块背景色衬底，再把徽章等比缩放居中放进去。
     *
     * 全程通过像素数组读写（不经过 `Canvas`），因此可以逐像素断言。
     */
    private fun drawLogo(
        pixels: IntArray,
        size: Int,
        logo: Bitmap,
        logoScale: Float,
        padColor: Int,
    ) {
        if (logo.width <= 0 || logo.height <= 0) return
        val scale = logoScale.coerceIn(MIN_LOGO_SCALE, MAX_LOGO_SCALE)
        val pad = (size * scale).toInt().coerceAtLeast(1)
        val padLeft = (size - pad) / 2
        val padTop = (size - pad) / 2

        fillRect(pixels, size, padLeft, padTop, pad, pad, padColor)

        // 四周留 1 像素，避免徽章紧贴衬底边缘和二维码模块糊在一起。
        val inner = (pad - 2).coerceAtLeast(1)
        val ratio = minOf(inner.toFloat() / logo.width, inner.toFloat() / logo.height)
        val drawWidth = (logo.width * ratio).toInt().coerceAtLeast(1)
        val drawHeight = (logo.height * ratio).toInt().coerceAtLeast(1)
        val left = padLeft + (pad - drawWidth) / 2
        val top = padTop + (pad - drawHeight) / 2

        val logoPixels = IntArray(logo.width * logo.height)
        logo.getPixels(logoPixels, 0, logo.width, 0, 0, logo.width, logo.height)

        for (dy in 0 until drawHeight) {
            val destY = top + dy
            if (destY !in 0 until size) continue
            val sourceY = dy * logo.height / drawHeight
            val rowOffset = destY * size
            for (dx in 0 until drawWidth) {
                val destX = left + dx
                if (destX !in 0 until size) continue
                val sourceX = dx * logo.width / drawWidth
                val source = logoPixels[sourceY * logo.width + sourceX]
                val alpha = (source ushr 24) and 0xFF
                if (alpha == 0) continue
                pixels[rowOffset + destX] =
                    if (alpha == 0xFF) source else blend(source, pixels[rowOffset + destX], alpha)
            }
        }
    }

    /** 把带透明度的 [source] 叠到不透明的 [background] 上（线性插值）。 */
    private fun blend(source: Int, background: Int, alpha: Int): Int {
        val ratio = alpha / 255f
        val inverse = 1f - ratio
        fun channel(shift: Int): Int {
            val mixed = ((source ushr shift) and 0xFF) * ratio + ((background ushr shift) and 0xFF) * inverse
            return mixed.toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun fillRect(
        pixels: IntArray,
        size: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        for (y in top until top + height) {
            if (y !in 0 until size) continue
            val rowOffset = y * size
            for (x in left until left + width) {
                if (x !in 0 until size) continue
                pixels[rowOffset + x] = color
            }
        }
    }
}
