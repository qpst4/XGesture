package com.slideindex.app.inspire

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.service.AccessibilityTextExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Prefetches preview-rect a11y text while the float-ball cursor is paused (yellow box).
 */
object PickPrefetchCache {
    private const val CONSUME_TIMEOUT_MS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var prefetchDeferred: Deferred<List<String>>? = null
    private var prefetchRect: Rect? = null
    private var prefetchGeneration: Int = -1

    fun invalidate() {
        prefetchDeferred?.cancel()
        prefetchDeferred = null
        prefetchRect = null
        prefetchGeneration = -1
    }

    fun startPreviewA11yPrefetch(
        service: AccessibilityService,
        rect: Rect,
        generation: Int
    ): Deferred<List<String>> {
        val normalized = Rect(rect)
        val cached = prefetchDeferred
        if (cached != null && prefetchRect == normalized && prefetchGeneration == generation) {
            return cached
        }
        prefetchDeferred?.cancel()
        prefetchGeneration = generation
        prefetchRect = normalized
        val capturedRect = Rect(normalized)
        return scope.async {
            val a11yStart = SystemClock.elapsedRealtime()
            PickPerf.mark("a11y_prefetch_start", "rect=$capturedRect gen=$generation")
            val words = runInterruptible {
                AccessibilityTextExtractor.collectTextForPreviewRect(service, capturedRect)
            }.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { listOf(it) }
                .orEmpty()
            PickPerf.markStepDuration("a11y_prefetch_end", a11yStart, "words=${words.size}")
            words
        }.also { prefetchDeferred = it }
    }

    suspend fun consumePreviewA11y(rect: Rect): List<String>? {
        val normalized = Rect(rect)
        val cachedRect = prefetchRect ?: return null
        if (cachedRect != normalized) return null
        val deferred = prefetchDeferred ?: return null
        val words = withTimeoutOrNull(CONSUME_TIMEOUT_MS) { deferred.await() }
        invalidate()
        if (words == null) {
            PickPerf.mark("a11y_prefetch_miss", "reason=timeout")
            return null
        }
        PickPerf.mark("a11y_prefetch_hit", "words=${words.size}")
        return words
    }
}
