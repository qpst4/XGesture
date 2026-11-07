package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.graphics.scale
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.ClipboardPayload
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.clipboard.resolvedImageFileNames
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.allImageFileNames
import com.slideindex.app.stash.combinedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PICK_IMAGE_SIDE_PX = 4096

/** Opens pick-result panel from stash or clipboard history entries. */
object PickResultFromHistoryCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun openFromStash(
        context: Context,
        entry: StashEntry,
        initialImageIndex: Int = 0
    ) {
        scope.launch {
            val appContext = context.applicationContext
            val hostContext = overlayHostContext() ?: appContext
            val repo = StashAccess.repository
            val text = when (entry.type) {
                StashEntryType.TEXT -> entry.text?.trim().orEmpty().takeIf { it.isNotEmpty() }
                StashEntryType.RICH -> entry.combinedText().takeIf { it.isNotEmpty() }
                else -> entry.combinedText().takeIf { it.isNotEmpty() }
            }
            val images = withContext(Dispatchers.IO) {
                if (repo == null) {
                    emptyList()
                } else {
                    loadStashImages(repo, entry)
                }
            }
            openLoaded(
                context = context,
                hostContext = hostContext,
                text = text,
                images = images,
                initialImageIndex = initialImageIndex
            )
        }
    }

    fun openFromClipboard(
        context: Context,
        entry: ClipboardEntry,
        initialImageIndex: Int = 0
    ) {
        scope.launch {
            val appContext = context.applicationContext
            val hostContext = overlayHostContext() ?: appContext
            val text = entry.combinedText().takeIf { it.isNotEmpty() }
            val images = withContext(Dispatchers.IO) {
                loadClipboardImages(appContext, entry)
            }
            openLoaded(
                context = context,
                hostContext = hostContext,
                text = text,
                images = images,
                initialImageIndex = initialImageIndex
            )
        }
    }

    /** Opens pick panel from the current system clipboard payload (text / image / mixed). */
    fun openFromClipboardPayload(
        context: Context,
        payload: ClipboardPayload?,
        autoTranslate: Boolean = false,
    ) {
        scope.launch {
            val appContext = context.applicationContext
            val hostContext = overlayHostContext() ?: appContext
            if (payload == null) {
                Toast.makeText(context, R.string.pick_from_history_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val text = payload.text.trim().takeIf { it.isNotEmpty() }
            val images = withContext(Dispatchers.IO) {
                loadClipboardPayloadImages(appContext, payload)
            }
            openLoaded(
                context = context,
                hostContext = hostContext,
                text = text,
                images = images,
                initialImageIndex = 0
            )
            if (autoTranslate && !text.isNullOrBlank()) {
                FloatBallTranslateCoordinator.translate(hostContext, text)
            }
        }
    }

    private suspend fun openLoaded(
        context: Context,
        hostContext: Context,
        text: String?,
        images: List<Bitmap>,
        initialImageIndex: Int
    ) {
        val kind = when {
            !text.isNullOrBlank() && images.isNotEmpty() -> PickContentKind.MIXED
            images.isNotEmpty() -> PickContentKind.IMAGE_ONLY
            !text.isNullOrBlank() -> PickContentKind.TEXT_ONLY
            else -> {
                Toast.makeText(context, R.string.pick_from_history_empty, Toast.LENGTH_SHORT).show()
                images.forEach { it.recycle() }
                return
            }
        }
        val ocrModelReady = isOcrModelReady(hostContext)
        val safeIndex = initialImageIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
        val activeSource = when (kind) {
            PickContentKind.TEXT_ONLY -> PickResultTextSource.A11Y
            PickContentKind.IMAGE_ONLY -> PickResultTextSource.OCR
            PickContentKind.MIXED -> PickResultTextSource.A11Y
        }
        val a11yEnabled = kind != PickContentKind.IMAGE_ONLY
        val ocrEnabled = kind != PickContentKind.TEXT_ONLY && ocrModelReady
        val result = FloatBallPickResult(
            a11yText = text,
            ocrText = null,
            screenshot = images.getOrNull(safeIndex),
            screenRect = null,
            activeSource = activeSource,
            ocrAvailable = ocrEnabled,
            ocrPending = kind == PickContentKind.IMAGE_ONLY && ocrModelReady,
            ocrPreferSwitchOnComplete = kind == PickContentKind.IMAGE_ONLY,
            a11ySourceEnabled = a11yEnabled,
            isShareImageOcr = false,
            contentOrigin = PickResultContentOrigin.STASH_CLIPBOARD,
            contentKind = kind,
            images = images,
            initialImageIndex = safeIndex,
            ownsImages = images.isNotEmpty()
        )
        FloatBallStashPanel.dismiss()
        FloatBallPickResultPanel.showResult(hostContext, result = result)
        if (ocrEnabled && (kind == PickContentKind.IMAGE_ONLY || activeSource == PickResultTextSource.OCR)) {
            FloatBallPickResultPanel.requestHistoryImageOcr(hostContext)
        }
    }

    private fun loadStashImages(
        repo: com.slideindex.app.stash.StashRepository,
        entry: StashEntry
    ): List<Bitmap> =
        entry.allImageFileNames().mapNotNull { fileName ->
            repo.loadBitmapByFileName(fileName)?.let { bitmap ->
                scaleDownIfNeeded(bitmap, MAX_PICK_IMAGE_SIDE_PX)
            }
        }

    private fun loadClipboardImages(context: Context, entry: ClipboardEntry): List<Bitmap> {
        val fileNames = entry.resolvedImageFileNames()
        if (fileNames.isNotEmpty()) {
            return fileNames.mapNotNull { fileName ->
                ClipboardImageStore.loadBitmapScaled(context, fileName, MAX_PICK_IMAGE_SIDE_PX)
            }
        }
        return ClipboardImageStore.loadEntryThumbnails(context, entry).map { bitmap ->
            scaleDownIfNeeded(bitmap, MAX_PICK_IMAGE_SIDE_PX)
        }
    }

    private fun loadClipboardPayloadImages(context: Context, payload: ClipboardPayload): List<Bitmap> =
        ClipboardImageStore.collectImageSources(payload).mapNotNull { src ->
            ClipboardImageStore.loadUriBitmapScaled(context, src, MAX_PICK_IMAGE_SIDE_PX)
        }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxSidePx: Int): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= maxSidePx) return bitmap
        val scale = maxSidePx.toFloat() / maxDim
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return bitmap.scale(targetW, targetH).also { scaled ->
            if (scaled !== bitmap) {
                bitmap.recycle()
            }
        }
    }

    private fun isOcrModelReady(context: Context): Boolean {
        val settings = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
            ?: return false
        val modelId = settings.floatBallOcrModelId
        if (modelId.isBlank() || !settings.floatBallOcrFallbackEnabled) return false
        return OcrDependencyAccess.modelRepository(context)?.isInstalled(modelId) == true
    }
}

private fun ClipboardEntry.combinedText(): String =
    resolvedContentBlocks()
        .filter { it.kind == com.slideindex.app.clipboard.ClipboardBlockKind.TEXT }
        .joinToString("\n\n") { it.text.trim() }
        .trim()
        .ifBlank { text.trim() }
