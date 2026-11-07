package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.graphics.Rect
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.barcode.joinDisplayText

enum class PickResultTextSource {
    A11Y,
    OCR,
    BARCODE,
}

enum class PickResultContentOrigin {
    SCREEN_PICK,
    SHARE_IMAGE,
    STASH_CLIPBOARD,
}

enum class PickContentKind {
    TEXT_ONLY,
    IMAGE_ONLY,
    MIXED,
}

data class FloatBallPickResult(
    val a11yText: String?,
    val ocrText: String?,
    val screenshot: Bitmap?,
    val screenRect: Rect?,
    val layoutMeta: ScreenshotLayoutMeta? = null,
    val activeSource: PickResultTextSource = PickResultTextSource.A11Y,
    /** True when an OCR model was installed and recognition was attempted for this pick. */
    val ocrAvailable: Boolean = false,
    /** OCR is running asynchronously after the panel was shown. */
    val ocrPending: Boolean = false,
    /** When async OCR completes, switch the visible tab to OCR (regional screenshot). */
    val ocrPreferSwitchOnComplete: Boolean = false,
    /** False for external share-image OCR (no accessibility source). */
    val a11ySourceEnabled: Boolean = true,
    /** Shared image OCR session; enables background processing while pending. */
    val isShareImageOcr: Boolean = false,
    /** Barcode / QR codes detected from the screenshot. */
    val barcodeResults: List<BarcodeScanResult> = emptyList(),
    val contentOrigin: PickResultContentOrigin = PickResultContentOrigin.SCREEN_PICK,
    val contentKind: PickContentKind? = null,
    /** Multi-image sessions from stash / clipboard; [screenshot] should match [initialImageIndex]. */
    val images: List<Bitmap> = emptyList(),
    val initialImageIndex: Int = 0,
    /** Panel should recycle [images] on dismiss when true. */
    val ownsImages: Boolean = false
) {
    val text: String?
        get() = textFor(activeSource)

    fun textFor(source: PickResultTextSource): String? {
        return when (source) {
            PickResultTextSource.A11Y -> a11yText?.takeIf { it.isNotBlank() }
            PickResultTextSource.OCR -> ocrText?.takeIf { it.isNotBlank() }
            PickResultTextSource.BARCODE -> barcodeResults.joinDisplayText().takeIf { it.isNotBlank() }
        }
    }

    fun hasA11ySource(): Boolean = !a11yText.isNullOrBlank()

    fun canToggleSource(): Boolean = hasA11ySource() && ocrAvailable

    fun resolvedImages(): List<Bitmap> =
        images.ifEmpty { listOfNotNull(screenshot) }

    fun resolvedContentKind(): PickContentKind {
        contentKind?.let { return it }
        val hasText = hasA11ySource()
        val hasImages = resolvedImages().isNotEmpty()
        return when {
            hasText && hasImages -> PickContentKind.MIXED
            hasImages -> PickContentKind.IMAGE_ONLY
            else -> PickContentKind.TEXT_ONLY
        }
    }
}
