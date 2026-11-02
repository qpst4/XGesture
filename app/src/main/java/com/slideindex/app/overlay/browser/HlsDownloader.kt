package com.slideindex.app.overlay.browser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * HLS（m3u8）下载：拉取播放列表 → 依次下载分片 → 顺序拼接为 .ts 文件
 * → 通过 MediaStore 放进系统「下载」目录。
 *
 * 只处理明文 m3u8（未加密的 #EXT-X-KEY 情况会放弃并提示）。
 */
internal object HlsDownloader {

    /** 下载并合并；回调 (已下载分片, 总分片)。 */
    suspend fun download(
        context: Context,
        m3u8Url: String,
        fileName: String,
        onProgress: (Int, Int) -> Unit,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val text = fetchText(m3u8Url).getOrThrow()
            if (text.contains("#EXT-X-KEY")) {
                error("该视频已加密（EXT-X-KEY），暂不支持")
            }
            val base = m3u8Url.substringBeforeLast('/') + "/"
            val segments = mutableListOf<String>()
            var variantPlaylist: String? = null

            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXT-X-STREAM-INF") -> Unit
                    line.startsWith("#") -> Unit
                    line.endsWith(".m3u8") -> variantPlaylist = if (line.startsWith("http")) line else base + line
                    else -> segments.add(if (line.startsWith("http")) line else base + line)
                }
            }

            // 主播放列表：取第一个子播放列表再拉一次
            val list = if (segments.isEmpty() && variantPlaylist != null) {
                val subText = fetchText(variantPlaylist!!).getOrThrow()
                val subBase = variantPlaylist!!.substringBeforeLast('/') + "/"
                subText.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { if (it.startsWith("http")) it else subBase + it }
                    .toList()
            } else {
                segments
            }
            if (list.isEmpty()) error("播放列表里没有分片")

            val temp = File(context.cacheDir, "hls_${System.currentTimeMillis()}.ts")
            temp.outputStream().use { out ->
                list.forEachIndexed { index, segment ->
                    val bytes = fetchBytes(segment).getOrThrow()
                    out.write(bytes)
                    onProgress(index + 1, list.size)
                }
            }

            val savedUri = saveToDownloads(context, temp, fileName)
            temp.delete()
            savedUri
        }
    }

    private fun fetchText(url: String): Result<String> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun fetchBytes(url: String): Result<ByteArray> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.inputStream.use { it.readBytes() }
    }

    private fun saveToDownloads(context: Context, file: File, fileName: String): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp2t")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建下载文件")
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("无法写入下载文件")
            return uri
        }
        val target = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName,
        )
        file.copyTo(target, overwrite = true)
        return Uri.fromFile(target)
    }
}
