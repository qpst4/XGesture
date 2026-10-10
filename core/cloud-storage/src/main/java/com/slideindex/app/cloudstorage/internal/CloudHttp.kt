package com.slideindex.app.cloudstorage.internal

import com.slideindex.app.cloudstorage.CloudStorageException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Response

/**
 * 阻塞式 OkHttp 调用与协程的桥接。
 *
 * 与 ClipShare 的关键差异：`invokeOnCancellation` 里会 `call.cancel()`，
 * 因此用户点"取消"时**真正中断网络传输**（ClipShare 的取消只覆盖打包/入库阶段，
 * HTTP 上传下载没有 abort 通道，必须等它跑完）。
 */
internal suspend fun <T> Call.awaitResponse(
    action: String,
    block: (Response) -> T,
): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    try {
        execute().use { response ->
            val value = block(response)
            if (continuation.isActive) continuation.resume(value)
        }
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        if (continuation.isActive) continuation.cancel(cancellation) else continuation.cancel()
    } catch (error: Throwable) {
        if (continuation.isActive) {
            continuation.resumeWithException(error.asCloudStorageException(action))
        } else {
            continuation.cancel()
        }
    }
}

/** 网络层异常统一包装成带操作名的 [CloudStorageException]，避免界面上只看到 `timeout`。 */
internal fun Throwable.asCloudStorageException(action: String): CloudStorageException =
    when (this) {
        is CloudStorageException -> this
        is IOException -> CloudStorageException("$action 失败：网络错误（${message ?: this::class.simpleName}）", this)
        else -> CloudStorageException("$action 失败：${message ?: this::class.simpleName}", this)
    }

/**
 * 读取错误响应体（截断），用于把服务端错误码带进异常信息。
 *
 * 服务端可能返回巨大/畸形内容，所以限制长度并吞掉读取异常——
 * 拿不到细节时也应该抛"请求失败"，而不是把读取异常当成根因。
 */
internal fun Response.readErrorDetail(limit: Int = 8 * 1024): String = runCatching {
    if (body.contentLength() in 0 until limit.toLong()) {
        body.string()
    } else {
        // 不用 InputStream.readNBytes：它到 Android API 33 才可用，minSdk 31 会被 lint 拦下。
        body.byteStream().use { stream ->
            val buffer = ByteArray(limit)
            var filled = 0
            while (filled < buffer.size) {
                val count = stream.read(buffer, filled, buffer.size - filled)
                if (count == -1) break
                filled += count
            }
            String(buffer, 0, filled, Charsets.UTF_8)
        }
    }
}.getOrDefault("")
