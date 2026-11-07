package com.slideindex.app.overlay

import android.graphics.Bitmap

/**
 * Pre-decoded GIF drag shell — avoids [ImageDecoder] on every drag start.
 */
object FloatBallGifDragSnapshot {
    private var keyUri: String? = null
    private var keyTargetPx: Int = 0
    private var snapshot: Bitmap? = null

    fun update(uri: String, targetPx: Int, sequence: FloatBallGifFrameDecoder.Sequence?) {
        clear()
        if (sequence == null) return
        val copied = when (sequence) {
            is FloatBallGifFrameDecoder.Sequence.Cached ->
                sequence.frames.firstOrNull()?.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
            is FloatBallGifFrameDecoder.Sequence.Streaming -> {
                val rendered = FloatBallGifFrameDecoder.renderStreamingFrame(
                    streaming = sequence,
                    elapsedMs = 0,
                    reuse = null
                )
                val copy = rendered.copy(Bitmap.Config.ARGB_8888, false)
                rendered.recycle()
                copy
            }
        } ?: return
        keyUri = uri
        keyTargetPx = targetPx
        snapshot = copied
    }

    fun copyForDrag(uri: String, targetPx: Int): Bitmap? {
        if (uri != keyUri || targetPx != keyTargetPx) return null
        val source = snapshot ?: return null
        if (source.isRecycled) return null
        return source.copy(Bitmap.Config.ARGB_8888, false)
    }

    fun clear() {
        snapshot?.recycle()
        snapshot = null
        keyUri = null
        keyTargetPx = 0
    }
}
