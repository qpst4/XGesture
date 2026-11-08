package com.slideindex.app.overlay.screenshot

import com.slideindex.app.overlay.overlayHostContext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.Toast
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.barcode.ZxingBarcodeScanner
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.PickResultContentOrigin
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SmartScreenshotOcr {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun pickTextFromBitmap(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null
    ) {
        val hostContext = overlayHostContext() ?: context.applicationContext
        val settings = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsSnapshot
            ?.invoke(hostContext)
        val modelId = settings?.floatBallOcrModelId.orEmpty()
        val isModelInstalled = modelId.isNotBlank() &&
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.isOcrModelInstalled
                ?.invoke(hostContext, modelId) == true

        if (!isModelInstalled) {
            Toast.makeText(hostContext, R.string.share_image_ocr_model_required, Toast.LENGTH_LONG).show()
            return
        }

        val panelScreenshot = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        val ocrBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return

        com.slideindex.app.overlay.pickresult.PickResultHostAccess.host?.show(
            hostContext,
            screenRect?.centerX()?.toFloat() ?: (hostContext.resources.displayMetrics.widthPixels / 2f),
            screenRect?.centerY()?.toFloat() ?: (hostContext.resources.displayMetrics.heightPixels / 2f),
            result = FloatBallPickResult(
                a11yText = null,
                ocrText = null,
                screenshot = panelScreenshot,
                screenRect = screenRect,
                layoutMeta = layoutMeta,
                activeSource = PickResultTextSource.OCR,
                ocrAvailable = true,
                ocrPending = true,
                ocrPreferSwitchOnComplete = true,
                a11ySourceEnabled = false,
                contentOrigin = PickResultContentOrigin.SCREEN_PICK
            ),
            PickResultTextMode.WORD_TAP,
        )

        scope.launch(Dispatchers.IO) {
            // 令牌在主线程取：showResult 若被非主线程调用会先 post，这里能排到它后面。
            val panelToken = withContext(Dispatchers.Main.immediate) {
                com.slideindex.app.overlay.pickresult.PickResultHostAccess.host?.showToken()
            }
            val barcodeResults = runCatching {
                ZxingBarcodeScanner.scanBitmap(
                    panelScreenshot,
                    // 与浮球取词同一条取词链路：先扫 2D、命中即停；小图还会补白边/放大。
                    pickFastPath = true,
                    budgetMs = ZxingBarcodeScanner.PANEL_SCAN_BUDGET_MS,
                    shouldCancel = { !(com.slideindex.app.overlay.pickresult.PickResultHostAccess.host?.isStillShowing(panelToken ?: 0) == true) },
                )
            }.getOrDefault(emptyList())
            if (barcodeResults.isNotEmpty()) {
                com.slideindex.app.overlay.pickresult.PickResultHostAccess.host
                    ?.updateBarcodeResults(barcodeResults, panelToken ?: 0)
            }

            val ocrResult = runCatching {
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.recognizeBitmap
                    ?.invoke(hostContext, modelId, ocrBitmap) ?: com.slideindex.app.ocr.OcrRecognizeResult.Failure("ocr-unavailable")
            }.getOrElse {
                com.slideindex.app.ocr.OcrRecognizeResult.Failure(
                    hostContext.getString(
                        R.string.ocr_error_recognition_failed,
                        it.localizedMessage ?: it.message ?: hostContext.getString(R.string.ocr_error_unknown)
                    )
                )
            }
            ocrBitmap.recycle()

            withContext(Dispatchers.Main.immediate) {
                when (ocrResult) {
                    is com.slideindex.app.ocr.OcrRecognizeResult.Success -> {
                        val text = ocrResult.text.trim()
                        com.slideindex.app.overlay.pickresult.PickResultHostAccess.host
                            ?.updateOcrText(text, true, PickResultTextMode.WORD_TAP)
                    }
                    is com.slideindex.app.ocr.OcrRecognizeResult.Failure -> {
                        com.slideindex.app.overlay.pickresult.PickResultHostAccess.host?.finishOcrPending()
                        com.slideindex.app.overlay.pickresult.PickResultHostAccess.host
                            ?.showOcrError(hostContext, ocrResult.reason)
                    }
                }
            }
        }
    }
}
