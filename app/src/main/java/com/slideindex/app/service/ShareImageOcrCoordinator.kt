package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.core.graphics.scale
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.barcode.ZxingBarcodeScanner
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.perf.PickPerf
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Handles shared images: decode, show pick-result panel, run on-device OCR. */
object ShareImageOcrCoordinator {
    private const val MAX_DECODE_SIDE_PX = 4096
    private const val CACHE_DIR_NAME = "share_image_ocr"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ocrDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ShareImageOcr").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private data class ActiveSession(
        val appContext: Context,
        val screenshot: Bitmap,
        val tiled: Boolean,
        var backgroundRequested: Boolean,
        val cachedImageUri: Uri
    )

    private var activeSession: ActiveSession? = null

    val canMoveToBackground: Boolean
        get() = activeSession != null && FloatBallPickResultPanel.isShowing

    /**
     * 这条 Intent 是不是「把一张图交给 XGesture」。
     *
     * 分享面板走 SEND / SEND_MULTIPLE，系统「打开方式」走 VIEW（部分相册/文件管理器的
     * 「编辑」入口走 EDIT）。两个入口共用同一套 trampoline，所以这里只按 action 放行，
     * 真正的图片 URI 由 [resolveImageUri] 取。
     */
    fun isImageIntent(intent: Intent?): Boolean {
        return when (intent?.action) {
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE,
            Intent.ACTION_VIEW,
            Intent.ACTION_EDIT -> true
            else -> false
        }
    }

    /**
     * 从分享 / 打开方式 Intent 里取出图片 URI。
     *
     * - 分享：图在 `EXTRA_STREAM`（或 clipData）。
     * - 打开方式：图在 `data`；个别 ROM 会把它塞进 clipData，所以都兜一下。
     */
    fun resolveImageUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_SEND -> readSendUri(intent)
            Intent.ACTION_SEND_MULTIPLE -> readSendMultipleUri(intent)
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> readViewUri(intent)
            else -> null
        }
    }

    private fun readViewUri(intent: Intent): Uri? =
        intent.data
            ?: intent.clipData?.getItemAt(0)?.uri
            ?: readSendUri(intent)

    fun moveToBackground(context: Context) {
        val session = activeSession ?: return
        if (!FloatBallPickResultPanel.isShowing) return
        session.backgroundRequested = true
        FloatBallPickResultPanel.dismiss()
        Toast.makeText(
            session.appContext,
            R.string.share_image_ocr_background_started,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun showHistoryEntry(context: Context, entry: ShareImageOcrHistoryEntry) {
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        if (!SlideIndexAccessibilityService.isConnected()) {
            Toast.makeText(context, R.string.share_image_ocr_service_required, Toast.LENGTH_LONG).show()
            return
        }
        val repository = ShareImageOcrDependencyAccess.historyRepository(context) ?: return
        val thumbnail = repository.loadThumbnail(entry)
        val screenshotCopy = thumbnail?.copy(thumbnail.config ?: Bitmap.Config.ARGB_8888, false)
        thumbnail?.recycle()
        val barcodeResults = screenshotCopy?.let { bitmap ->
            ZxingBarcodeScanner.scanBitmap(bitmap)
        }.orEmpty()
        FloatBallPickResultPanel.showResult(
            context = hostContext,
            result = FloatBallPickResult(
                a11yText = null,
                ocrText = entry.ocrText,
                screenshot = screenshotCopy,
                screenRect = null,
                activeSource = PickResultTextSource.OCR,
                ocrAvailable = true,
                ocrPending = false,
                ocrPreferSwitchOnComplete = true,
                a11ySourceEnabled = false,
                isShareImageOcr = true,
                barcodeResults = barcodeResults
            ),
            initialTextMode = PickResultTextMode.WORD_TAP
        )
    }

    suspend fun handleSharedImage(
        context: Context,
        uri: Uri,
        modelId: String
    ): Boolean {
        PickPerf.mark("share_image_ocr_start", "model=$modelId")
        val appContext = context.applicationContext
        if (!SlideIndexAccessibilityService.isConnected()) {
            Toast.makeText(appContext, R.string.share_image_ocr_service_required, Toast.LENGTH_LONG).show()
            return false
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: appContext
        val modelReady = modelId.isNotBlank() &&
            OcrDependencyAccess.modelRepository(appContext)?.isInstalled(modelId) == true
        if (!modelReady) {
            Toast.makeText(appContext, R.string.share_image_ocr_model_required, Toast.LENGTH_LONG).show()
            return false
        }

        val cachedUri = cacheSharedImageToAppStorage(context, uri)
            ?: run {
                Toast.makeText(appContext, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
                return false
            }

        val bounds = withContext(Dispatchers.IO) {
            ShareImageLongImageOcr.readImageBounds(appContext, cachedUri)
                ?: decodeShareImage(appContext, cachedUri)?.let { bitmap ->
                    ShareImageLongImageOcr.ImageBounds(bitmap.width, bitmap.height).also {
                        bitmap.recycle()
                    }
                }
        }
        if (bounds == null) {
            cleanupCachedUri(cachedUri)
            Toast.makeText(appContext, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            if (ShareImageLongImageOcr.shouldTile(bounds)) {
                handleTiledSharedImage(hostContext, appContext, cachedUri, bounds, modelId)
            } else {
                handleSingleSharedImage(hostContext, appContext, cachedUri, modelId)
            }
        } catch (error: Throwable) {
            cleanupCachedUri(cachedUri)
            throw error
        }
    }

    private suspend fun handleSingleSharedImage(
        hostContext: Context,
        appContext: Context,
        cachedUri: Uri,
        modelId: String
    ): Boolean {
        val bitmap = withContext(Dispatchers.IO) { decodeShareImage(appContext, cachedUri) }
        if (bitmap == null) {
            cleanupCachedUri(cachedUri)
            Toast.makeText(appContext, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        val sessionScreenshot = duplicateBitmap(bitmap)
        if (sessionScreenshot == null) {
            bitmap.recycle()
            cleanupCachedUri(cachedUri)
            Toast.makeText(appContext, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        val panelScreenshot = duplicateBitmap(bitmap)
        if (panelScreenshot == null) {
            sessionScreenshot.recycle()
            bitmap.recycle()
            cleanupCachedUri(cachedUri)
            Toast.makeText(appContext, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        bitmap.recycle()

        val barcodeResults = withContext(Dispatchers.IO) {
            ZxingBarcodeScanner.scanBitmap(panelScreenshot)
        }
        beginSession(
            appContext = appContext,
            screenshot = sessionScreenshot,
            tiled = false,
            cachedImageUri = cachedUri
        )
        showPendingResult(hostContext, panelScreenshot, barcodeResults)
        launchSingleOcr(modelId, cachedUri)
        PickPerf.mark(
            "share_image_ocr_panel_shown",
            "mode=single size=${panelScreenshot.width}x${panelScreenshot.height}"
        )
        return true
    }

    private suspend fun handleTiledSharedImage(
        hostContext: Context,
        appContext: Context,
        cachedUri: Uri,
        bounds: ShareImageLongImageOcr.ImageBounds,
        modelId: String
    ): Boolean {
        val thumbnail = withContext(Dispatchers.IO) {
            ShareImageLongImageOcr.decodeThumbnail(appContext, cachedUri, bounds)
        }
        if (thumbnail == null) {
            cleanupCachedUri(cachedUri)
            Toast.makeText(appContext, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        val sessionScreenshot = duplicateBitmap(thumbnail)
        val panelScreenshot = duplicateBitmap(thumbnail)
        thumbnail.recycle()
        if (sessionScreenshot == null || panelScreenshot == null) {
            sessionScreenshot?.recycle()
            panelScreenshot?.recycle()
            cleanupCachedUri(cachedUri)
            Toast.makeText(appContext, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return false
        }

        val tileCount = ShareImageLongImageOcr.planTileRanges(bounds.height).size
        val barcodeResults = withContext(Dispatchers.IO) {
            ZxingBarcodeScanner.scanBitmap(panelScreenshot)
        }
        beginSession(
            appContext = appContext,
            screenshot = sessionScreenshot,
            tiled = true,
            cachedImageUri = cachedUri
        )
        showPendingResult(hostContext, panelScreenshot, barcodeResults)
        launchTiledOcr(cachedUri, bounds, modelId)
        PickPerf.mark(
            "share_image_ocr_panel_shown",
            "mode=tiled source=${bounds.width}x${bounds.height} tiles=$tileCount"
        )
        return true
    }

    private fun beginSession(
        appContext: Context,
        screenshot: Bitmap,
        tiled: Boolean,
        cachedImageUri: Uri
    ) {
        endSession(activeSession)
        activeSession = ActiveSession(
            appContext = appContext,
            screenshot = screenshot,
            tiled = tiled,
            backgroundRequested = false,
            cachedImageUri = cachedImageUri
        )
    }

    private fun showPendingResult(
        hostContext: Context,
        screenshotCopy: Bitmap,
        barcodeResults: List<com.slideindex.app.barcode.BarcodeScanResult> = emptyList()
    ) {
        FloatBallPickResultPanel.showResult(
            context = hostContext,
            result = FloatBallPickResult(
                a11yText = null,
                ocrText = null,
                screenshot = screenshotCopy,
                screenRect = null,
                activeSource = PickResultTextSource.OCR,
                ocrAvailable = true,
                ocrPending = true,
                ocrPreferSwitchOnComplete = true,
                a11ySourceEnabled = false,
                isShareImageOcr = true,
                barcodeResults = barcodeResults
            )
        )
    }

    private fun launchSingleOcr(modelId: String, cachedUri: Uri) {
        if (activeSession == null) return
        scope.launch(ocrDispatcher) {
            val session = activeSession
            val (ocrText, failureReason) = try {
                if (session == null) {
                    null to null
                } else {
                    val bitmap = decodeShareImage(session.appContext, cachedUri)
                    if (bitmap == null) {
                        null to null
                    } else {
                        try {
                            when (
                                val result = RegionalScreenshotOcr.recognizeBitmapPublic(
                                    session.appContext,
                                    modelId,
                                    bitmap
                                )
                            ) {
                                is com.slideindex.app.ocr.OcrRecognizeResult.Success ->
                                    result.textOrNull() to null
                                is com.slideindex.app.ocr.OcrRecognizeResult.Failure ->
                                    null to result.reason
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            } catch (error: Throwable) {
                null to (error.localizedMessage ?: error.message)
            }
            deliverOcrResult(ocrText, failureReason)
        }
    }

    private fun launchTiledOcr(
        cachedUri: Uri,
        bounds: ShareImageLongImageOcr.ImageBounds,
        modelId: String
    ) {
        if (activeSession == null) return
        scope.launch(ocrDispatcher) {
            val session = activeSession
            val ocrText = try {
                if (session == null) {
                    null
                } else {
                    ShareImageLongImageOcr.recognizeTiled(
                        context = session.appContext,
                        uri = cachedUri,
                        bounds = bounds,
                        modelId = modelId
                    )
                }
            } catch (_: Throwable) {
                null
            }
            deliverOcrResult(ocrText)
        }
    }

    private suspend fun deliverOcrResult(ocrText: String?, failureReason: String? = null) {
        val session = activeSession ?: return
        val reopenAfterBackground = session.backgroundRequested
        val historyEnabled = OverlayDependencyAccess.overlayDependencies(session.appContext)
            ?.settingsRepository
            ?.readSnapshot()
            ?.shareImageOcrHistoryEnabled == true

        withContext(Dispatchers.Main.immediate) {
            if (!ocrText.isNullOrBlank()) {
                var delivered = false
                if (FloatBallPickResultPanel.isShowing) {
                    FloatBallPickResultPanel.updateOcrText(
                        ocrText,
                        switchToOcr = true,
                        initialTextMode = null
                    )
                    delivered = true
                } else if (reopenAfterBackground) {
                    delivered = reopenCompletedResult(session, ocrText)
                }

                if (!delivered && session.tiled && historyEnabled) {
                    Toast.makeText(
                        session.appContext,
                        R.string.share_image_ocr_saved_to_history,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                if (session.tiled && historyEnabled) {
                    val historyThumbnail = duplicateBitmap(session.screenshot)
                    if (historyThumbnail != null) {
                        scope.launch(Dispatchers.IO) {
                            saveHistory(
                                appContext = session.appContext,
                                ocrText = ocrText,
                                thumbnail = historyThumbnail,
                                tiled = session.tiled
                            )
                        }
                    }
                }
                if (reopenAfterBackground && delivered) {
                    Toast.makeText(
                        session.appContext,
                        R.string.share_image_ocr_background_complete,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val message = failureReason
                    ?: session.appContext.getString(R.string.float_ball_text_not_found)
                if (FloatBallPickResultPanel.isShowing) {
                    FloatBallPickResultPanel.finishOcrPending()
                    FloatBallPickResultPanel.showOcrError(session.appContext, message)
                } else {
                    Toast.makeText(
                        session.appContext,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            endSession(session)
        }
    }

    private fun reopenCompletedResult(
        session: ActiveSession,
        ocrText: String
    ): Boolean {
        if (!SlideIndexAccessibilityService.isConnected()) {
            return false
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: session.appContext
        val screenshotCopy = duplicateBitmap(session.screenshot) ?: return false
        val barcodeResults = ZxingBarcodeScanner.scanBitmap(screenshotCopy)
        FloatBallPickResultPanel.showResult(
            context = hostContext,
            result = FloatBallPickResult(
                a11yText = null,
                ocrText = ocrText,
                screenshot = screenshotCopy,
                screenRect = null,
                activeSource = PickResultTextSource.OCR,
                ocrAvailable = true,
                ocrPending = false,
                ocrPreferSwitchOnComplete = true,
                a11ySourceEnabled = false,
                isShareImageOcr = true,
                barcodeResults = barcodeResults
            ),
            initialTextMode = PickResultTextMode.WORD_TAP
        )
        return true
    }

    private suspend fun saveHistory(
        appContext: Context,
        ocrText: String,
        thumbnail: Bitmap,
        tiled: Boolean
    ) {
        val repository = ShareImageOcrDependencyAccess.historyRepository(appContext) ?: run {
            thumbnail.recycle()
            return
        }
        try {
            repository.append(
                ocrText = ocrText,
                thumbnail = thumbnail,
                tiled = tiled
            )
        } finally {
            thumbnail.recycle()
        }
    }

    private fun endSession(session: ActiveSession?) {
        if (session == null) return
        session.screenshot.recycle()
        cleanupCachedUri(session.cachedImageUri)
        if (activeSession === session) {
            activeSession = null
        }
    }

    private suspend fun cacheSharedImageToAppStorage(context: Context, uri: Uri): Uri? =
        withContext(Dispatchers.IO) {
            // 「打开方式」传进来的 URI 不一定带读授权（不是所有相册/文件管理器都会加
            // FLAG_GRANT_READ_URI_PERMISSION）。这里必须吞掉 SecurityException，
            // 交给调用方走「图片加载失败」提示，不能让异常带走整个进程。
            runCatching {
                val dir = File(context.applicationContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
                dir.listFiles()?.forEach { file ->
                    runCatching { file.delete() }
                }
                val outFile = File(dir, "current_share.img")
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
                if (!copied || outFile.length() <= 0L) {
                    outFile.delete()
                    null
                } else {
                    Uri.fromFile(outFile)
                }
            }.getOrNull()
        }

    private fun cleanupCachedUri(uri: Uri) {
        val path = uri.path ?: return
        runCatching { File(path).delete() }
    }

    private fun duplicateBitmap(bitmap: Bitmap): Bitmap? =
        bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

    private fun readSendUri(intent: Intent): Uri? {
        val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return streamUri ?: intent.clipData?.getItemAt(0)?.uri
    }

    private fun readSendMultipleUri(intent: Intent): Uri? {
        val streams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        return streams?.firstOrNull() ?: intent.clipData?.getItemAt(0)?.uri
    }

    internal fun decodeShareImage(context: Context, uri: Uri): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val maxSide = maxOf(info.size.width, info.size.height)
                if (maxSide > MAX_DECODE_SIDE_PX) {
                    val scale = MAX_DECODE_SIDE_PX.toFloat() / maxSide
                    val targetW = (info.size.width * scale).toInt().coerceAtLeast(1)
                    val targetH = (info.size.height * scale).toInt().coerceAtLeast(1)
                    decoder.setTargetSize(targetW, targetH)
                }
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            downscaleIfNeeded(decoded)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeShareImageLegacy(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxSide / sampleSize > MAX_DECODE_SIDE_PX) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null
        return downscaleIfNeeded(decoded)
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_DECODE_SIDE_PX) return bitmap
        val scale = MAX_DECODE_SIDE_PX.toFloat() / maxSide
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = bitmap.scale(targetW, targetH)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }
}
