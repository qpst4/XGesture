package com.slideindex.app.overlay

import android.content.Context
import java.util.LinkedHashMap

/**
 * 悬浮球 GIF 解码结果的共享缓存。
 *
 * 为什么必须有：一次 [FloatBallGifFrameDecoder.decode] 会把整只 GIF 预解码成最多
 * 72 张位图（`MAX_CACHED_FRAMES`），而球窗口、设置页预览、拖拽快照过去各自解码一份。
 * 同一个 (uri, 目标尺寸) 每解码一次就多几十张位图，而且旧序列在切换样式后并不真正释放
 * ——真机实测：切几次球样式就让进程位图从 138 MB 涨到 263 MB，其中 GIF 帧一项占 76 MB。
 *
 * 这里按 (uri, 目标尺寸) 只保留一份解码结果并做引用计数：
 * - 命中缓存直接复用，不再解码；
 * - 消费方拿到 [Handle]，用完 `close()`；
 * - 没人引用且超过 [MAX_ENTRIES] 的旧条目才真正 [FloatBallGifFrameDecoder.Sequence.recycle]。
 */
object FloatBallGifSequenceCache {

    /** 同时保留几份解码结果：球本身与设置页预览通常共用同一 key，2 份足够。 */
    private const val MAX_ENTRIES = 2

    internal data class Key(val uri: String, val targetPx: Int)

    private class Entry(val sequence: FloatBallGifFrameDecoder.Sequence) {
        var refs = 0
    }

    /** 一次使用会话。必须在不再使用该序列时 `close()`，否则缓存永远回收不了。 */
    class Handle internal constructor(
        private val key: Key,
        val sequence: FloatBallGifFrameDecoder.Sequence
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            release(key)
        }
    }

    private val lock = Any()
    private val entries = LinkedHashMap<Key, Entry>()

    /** 命中即复用；未命中会在调用线程解码（调用方自己放到 IO 线程）。 */
    fun acquire(context: Context, uri: String, targetPx: Int): Handle? {
        val key = Key(uri, targetPx)
        synchronized(lock) {
            entries[key]?.let { entry ->
                entry.refs++
                return Handle(key, entry.sequence)
            }
        }
        val decoded = FloatBallGifFrameDecoder.decode(context, uri, targetPx) ?: return null
        synchronized(lock) {
            val existing = entries[key]
            if (existing != null) {
                // 解码期间别的消费者先放进去了：丢掉自己这份，复用已有的。
                existing.refs++
                decoded.recycle()
                return Handle(key, existing.sequence)
            }
            val entry = Entry(decoded)
            entry.refs = 1
            entries[key] = entry
            evictLocked()
            return Handle(key, decoded)
        }
    }

    private fun release(key: Key) {
        synchronized(lock) {
            val entry = entries[key] ?: return
            if (entry.refs > 0) entry.refs--
            evictLocked()
        }
    }

    /** 只回收"没人引用"的条目；还在用的超限也先留着，避免把正在显示的帧回收掉。 */
    private fun evictLocked() {
        if (entries.size <= MAX_ENTRIES) return
        val iterator = entries.entries.iterator()
        while (entries.size > MAX_ENTRIES && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.refs > 0) continue
            entry.value.sequence.recycle()
            iterator.remove()
        }
    }
}
