package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import com.slideindex.app.service.AccessibilityTextExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * FV-style preview bounds cache: async full-tree scan (G4 / u0), per-frame point hit-test (o1.r).
 * Scan order: active root, then other windows (IME/overlay skipped).
 */
object FloatBallPreviewBoundsCache {
    private const val STACK_OVERFLOW_RETRY_MS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entriesRef = AtomicReference<List<AccessibilityTextExtractor.PreviewBoundsEntry>>(emptyList())
    private val editableEntriesRef =
        AtomicReference<List<AccessibilityTextExtractor.PreviewBoundsEntry>>(emptyList())
    @Volatile
    private var cacheEra = 0

    fun invalidate() {
        cacheEra++
        entriesRef.set(emptyList())
        editableEntriesRef.set(emptyList())
    }

    fun isReady(): Boolean = entriesRef.get().isNotEmpty()

    fun hitTestAt(rawX: Float, rawY: Float): Rect? = hitTestEntryAt(rawX, rawY)?.rect

    fun hitTestEntryAt(rawX: Float, rawY: Float): AccessibilityTextExtractor.PreviewBoundsEntry? {
        return AccessibilityTextExtractor.hitTestPreviewBounds(
            entries = entriesRef.get(),
            px = rawX.toInt(),
            py = rawY.toInt()
        )
    }

    fun hitTestEditableAt(rawX: Float, rawY: Float): Rect? {
        return AccessibilityTextExtractor.hitTestEditableBounds(
            entries = editableEntriesRef.get(),
            px = rawX.toInt(),
            py = rawY.toInt()
        )
    }

    fun refresh(
        service: AccessibilityService,
        includeEditable: Boolean = false,
        onReady: (() -> Unit)? = null
    ) {
        val era = cacheEra
        scope.launch {
            val built = buildCaches(service, includeEditable, era) ?: return@launch
            entriesRef.set(built.preview)
            editableEntriesRef.set(if (includeEditable) built.editable else emptyList())
            if (onReady != null) {
                withContext(Dispatchers.Main) {
                    if (era == cacheEra) {
                        onReady()
                    }
                }
            }
        }
    }

    /** FV a3: retry once after StackOverflow on deep a11y trees. */
    private suspend fun buildCaches(
        service: AccessibilityService,
        includeEditable: Boolean,
        era: Int
    ): AccessibilityTextExtractor.FloatBallBoundsCacheSnapshot? {
        suspend fun scanOnce(): AccessibilityTextExtractor.FloatBallBoundsCacheSnapshot {
            return runInterruptible {
                AccessibilityTextExtractor.collectFloatBallBoundsCache(
                    service = service,
                    collectPreview = true,
                    collectEditable = includeEditable
                )
            }
        }
        val first = try {
            scanOnce()
        } catch (_: StackOverflowError) {
            if (era != cacheEra) return null
            delay(STACK_OVERFLOW_RETRY_MS)
            if (era != cacheEra) return null
            try {
                scanOnce()
            } catch (_: StackOverflowError) {
                return null
            }
        }
        return if (era == cacheEra) first else null
    }
}
