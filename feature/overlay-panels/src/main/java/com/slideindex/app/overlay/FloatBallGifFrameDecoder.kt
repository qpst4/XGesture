@file:Suppress("DEPRECATION")

package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import androidx.core.graphics.createBitmap
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * FV-style GIF decode: downscale to ball size, pre-cache frames when small enough.
 */
object FloatBallGifFrameDecoder {
    private const val MAX_CACHE_PIXELS = 16_000_000
    private const val MIN_FRAME_DELAY_MS = 33
    private const val MAX_FRAME_DELAY_MS = 1_000
    private const val DEFAULT_FRAME_DELAY_MS = 100
    private const val DEFAULT_DURATION_MS = 1_000
    private const val MIN_FRAME_SAMPLE_STEP_MS = 33
    private const val MAX_CACHED_FRAMES = 72

    data class Frame(
        val bitmap: Bitmap,
        val delayMs: Int
    )

    sealed class Sequence {
        abstract val frameCount: Int

        data class Cached(
            val frames: List<Frame>
        ) : Sequence() {
            override val frameCount: Int get() = frames.size
        }

        data class Streaming(
            val movie: Movie,
            val durationMs: Int,
            val width: Int,
            val height: Int
        ) : Sequence() {
            override val frameCount: Int get() = 1
        }

        fun recycle() {
            when (this) {
                is Cached -> frames.forEach { it.bitmap.recycle() }
                is Streaming -> Unit
            }
        }
    }

    fun decode(context: Context, uriString: String, targetPx: Int): Sequence? {
        val uri = FloatBallStyleAssetStore.resolveReadableUri(context, uriString) ?: return null
        val safeTarget = targetPx.coerceAtLeast(1)
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            decodeStream(stream, safeTarget)
        }
    }

    private fun decodeStream(stream: InputStream, targetPx: Int): Sequence? {
        val movie = Movie.decodeStream(stream) ?: return null
        val srcW = movie.width()
        val srcH = movie.height()
        if (srcW <= 0 || srcH <= 0) return null

        val durationMs = movie.duration().takeIf { it > 0 } ?: DEFAULT_DURATION_MS
        val outW = scaleLength(srcW, srcH, targetPx)
        val outH = scaleLength(srcH, srcW, targetPx)
        val sampleStepMs = frameSampleStepMs(durationMs)
        val extracted = extractSampledFrames(movie, durationMs, outW, outH, sampleStepMs)
        if (extracted.isEmpty()) return null

        val cachePixels = extracted.size.toLong() * outW * outH
        return if (cachePixels <= MAX_CACHE_PIXELS) {
            Sequence.Cached(extracted)
        } else {
            extracted.forEach { it.bitmap.recycle() }
            Sequence.Streaming(movie = movie, durationMs = durationMs, width = outW, height = outH)
        }
    }

    private fun scaleLength(srcLen: Int, otherLen: Int, targetPx: Int): Int {
        val scale = min(targetPx.toFloat() / srcLen, targetPx.toFloat() / otherLen)
        return max(1, (srcLen * scale).roundToInt())
    }

    private fun frameSampleStepMs(durationMs: Int): Int {
        val budgetStep = (durationMs + MAX_CACHED_FRAMES - 1) / MAX_CACHED_FRAMES
        return max(MIN_FRAME_SAMPLE_STEP_MS, budgetStep)
    }

    private fun extractSampledFrames(
        movie: Movie,
        durationMs: Int,
        outW: Int,
        outH: Int,
        sampleStepMs: Int
    ): List<Frame> {
        val frames = ArrayList<Frame>()
        var lastPixels: IntArray? = null
        var lastBitmap: Bitmap? = null
        var accumulatedDelayMs = 0

        var timeMs = 0
        while (timeMs < durationMs) {
            movie.setTime(timeMs)
            val bitmap = renderFrame(movie, outW, outH)
            val pixels = IntArray(outW * outH)
            bitmap.getPixels(pixels, 0, outW, 0, 0, outW, outH)

            if (lastPixels != null && lastPixels.contentEquals(pixels)) {
                bitmap.recycle()
                accumulatedDelayMs += sampleStepMs
            } else {
                lastBitmap?.let { kept ->
                    frames.add(
                        Frame(
                            bitmap = kept,
                            delayMs = normalizeDelay(accumulatedDelayMs.coerceAtLeast(sampleStepMs))
                        )
                    )
                }
                lastBitmap = bitmap
                lastPixels = pixels
                accumulatedDelayMs = sampleStepMs
            }
            timeMs += sampleStepMs
        }

        lastBitmap?.let { kept ->
            frames.add(
                Frame(
                    bitmap = kept,
                    delayMs = normalizeDelay(accumulatedDelayMs.coerceAtLeast(sampleStepMs))
                )
            )
        }
        if (frames.isEmpty()) {
            movie.setTime(0)
            frames.add(Frame(renderFrame(movie, outW, outH), DEFAULT_FRAME_DELAY_MS))
        }
        return frames
    }

    fun renderFrame(movie: Movie, outW: Int, outH: Int): Bitmap {
        val bitmap = createBitmap(outW, outH)
        val canvas = Canvas(bitmap)
        val scaleX = outW.toFloat() / movie.width().coerceAtLeast(1)
        val scaleY = outH.toFloat() / movie.height().coerceAtLeast(1)
        canvas.scale(scaleX, scaleY)
        movie.draw(canvas, 0f, 0f)
        return bitmap
    }

    fun renderStreamingFrame(
        streaming: Sequence.Streaming,
        elapsedMs: Int,
        reuse: Bitmap?
    ): Bitmap {
        val bitmap = if (
            reuse != null &&
            !reuse.isRecycled &&
            reuse.width == streaming.width &&
            reuse.height == streaming.height
        ) {
            reuse
        } else {
            createBitmap(streaming.width, streaming.height)
        }
        streaming.movie.setTime(elapsedMs % streaming.durationMs)
        val canvas = Canvas(bitmap)
        val scaleX = streaming.width.toFloat() / streaming.movie.width().coerceAtLeast(1)
        val scaleY = streaming.height.toFloat() / streaming.movie.height().coerceAtLeast(1)
        canvas.scale(scaleX, scaleY)
        streaming.movie.draw(canvas, 0f, 0f)
        return bitmap
    }

    private fun normalizeDelay(rawMs: Int): Int =
        rawMs.coerceIn(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
}
