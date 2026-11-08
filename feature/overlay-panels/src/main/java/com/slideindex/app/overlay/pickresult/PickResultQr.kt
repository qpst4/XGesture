package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.slideindex.app.barcode.QrCodeGenerator
import java.nio.charset.StandardCharsets

/**
 * 取词面板「生成二维码」的编码入口。
 *
 * 固定用 H 纠错只有约 1273 字节容量，整段 OCR 文本经常装不下；这里按 H → Q → M → L
 * 依次降级：先保可读性，装不下再用容量换成功率和更大的模块（屏幕上也更好扫）。
 * 出图固定 1024px，比快捷菜单默认的 512 更适合长文本。
 */
object PickResultQr {

    /** 出图边长。 */
    const val SIZE_PX = 1024

    /** QR v40-L 的字节容量上界；再长连 L 也装不下，提前判失败。 */
    const val MAX_BYTES = 2953

    private val LEVELS = listOf(
        ErrorCorrectionLevel.H,
        ErrorCorrectionLevel.Q,
        ErrorCorrectionLevel.M,
        ErrorCorrectionLevel.L,
    )

    fun isTooLong(content: String): Boolean =
        content.toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES

    /** 依次尝试四级纠错；内容为空、超容量或全部失败时返回 null。 */
    fun generate(content: String): Bitmap? {
        if (content.isBlank() || isTooLong(content)) return null
        for (level in LEVELS) {
            val bitmap = QrCodeGenerator.generate(
                content = content,
                style = QrCodeGenerator.Style(
                    sizePx = SIZE_PX,
                    errorCorrectionLevel = level,
                ),
            )
            if (bitmap != null) return bitmap
        }
        return null
    }
}
