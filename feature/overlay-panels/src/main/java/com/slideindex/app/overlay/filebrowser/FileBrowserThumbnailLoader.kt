package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.privilege.root.RootNamespace
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 可直读文件的缩略图加载器：图片、视频首帧、APK 图标、PDF 首页。
 *
 * Root 路径只对图片做“按需 base64”缩略图；视频/APK/PDF 仍用类型图标，避免滚动时大量 su。
 */
object FileBrowserThumbnailLoader {
    private const val MAX_EDGE_PX = 128
    private const val MAX_IMAGE_BYTES = 12L * 1024L * 1024L
    private const val MAX_ROOT_IMAGE_BYTES = 4L * 1024L * 1024L
    private const val MAX_ROOT_COPY_BYTES = 12L * 1024L * 1024L
    private const val ROOT_TIMEOUT_SECONDS = 8L

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceAtLeast(4 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(
        context: Context,
        path: String,
        modifiedEpochSeconds: Long,
        kind: FileKind,
        access: FileBrowserAccessInfo? = null,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isEmpty()) return@withContext null
        val key = "$kind|$path:$modifiedEpochSeconds"
        cache.get(key)?.let { return@withContext it }
        val file = File(path)
        val readable = file.isFile && file.canRead()
        if (!readable && access?.mode != AccessMode.ROOT) {
            return@withContext null
        }
        val bitmap = when (kind) {
            FileKind.IMAGE -> if (readable) image(file) else rootImage(context, file)
            FileKind.VIDEO -> if (readable) video(file) else rootCopied(context, file, kind)
            FileKind.APK -> if (readable) apk(context, file) else rootCopied(context, file, kind)
            FileKind.PDF -> if (readable) pdf(file) else rootCopied(context, file, kind)
            else -> null
        } ?: return@withContext null
        cache.put(key, bitmap)
        bitmap
    }

    private fun image(file: File): Bitmap? {
        if (file.length() > MAX_IMAGE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE_PX * 2 ||
            bounds.outHeight / sample > MAX_EDGE_PX * 2
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    private fun rootCopied(context: Context, file: File, kind: FileKind): Bitmap? {
        val temp = runCatching {
            File.createTempFile("xg_thumb_copy_", ".bin", context.cacheDir)
        }.getOrNull() ?: return null
        return try {
            if (!rootCopy(file.absolutePath, temp)) return null
            when (kind) {
                FileKind.VIDEO -> video(temp)
                FileKind.APK -> apk(context, temp)
                FileKind.PDF -> pdf(temp)
                else -> null
            }
        } finally {
            runCatching { temp.delete() }
        }
    }

    private fun rootCopy(path: String, destination: File): Boolean {
        val size = rootSize(path) ?: return false
        if (size <= 0L || size > MAX_ROOT_COPY_BYTES) return false
        val sh = TaskManagerShellExecutor.resolveShPath()
        val command = "cp -f ${TaskManagerShellExecutor.shellQuote(path)} " +
            TaskManagerShellExecutor.shellQuote(destination.absolutePath)
        val launcher = if (RootNamespace.nsenterBinaryExists()) {
            "${RootNamespace.NSENTER_PATH} -t 1 -m -- $sh -c " +
                TaskManagerShellExecutor.shellQuote(command)
        } else {
            command
        }
        val process = runCatching {
            ProcessBuilder(TaskManagerShellExecutor.resolveSuInvocation(), "-c", launcher)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return false
        return try {
            process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS) &&
                destination.isFile && destination.length() > 0L
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun rootSize(path: String): Long? {
        val sh = TaskManagerShellExecutor.resolveShPath()
        val command = "stat -c %s ${TaskManagerShellExecutor.shellQuote(path)} 2>/dev/null"
        val launcher = if (RootNamespace.nsenterBinaryExists()) {
            "${RootNamespace.NSENTER_PATH} -t 1 -m -- $sh -c " +
                TaskManagerShellExecutor.shellQuote(command)
        } else {
            command
        }
        val process = runCatching {
            ProcessBuilder(TaskManagerShellExecutor.resolveSuInvocation(), "-c", launcher)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null
        return try {
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            process.inputStream.bufferedReader().use { it.readText() }
                .lineSequence()
                .mapNotNull { it.trim().toLongOrNull() }
                .lastOrNull()
        } finally {
            runCatching { process.destroy() }
        }
    }

    /** Root 图片：一次性 base64 读取，限制 4MB，失败回退类型图标。 */
    private fun rootImage(context: Context, file: File): Bitmap? {
        val bytes = readRootBase64(context, file.absolutePath, MAX_ROOT_IMAGE_BYTES) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE_PX * 2 ||
            bounds.outHeight / sample > MAX_EDGE_PX * 2
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    private fun readRootBase64(context: Context, path: String, maxBytes: Long): ByteArray? {
        val sh = TaskManagerShellExecutor.resolveShPath()
        val temp = runCatching {
            File.createTempFile("xg_thumb_", ".b64", context.cacheDir)
        }.getOrNull() ?: return null
        try {
            val command = "base64 -w 0 ${TaskManagerShellExecutor.shellQuote(path)} " +
                "> ${TaskManagerShellExecutor.shellQuote(temp.absolutePath)} 2>/dev/null"
            val launcher = if (RootNamespace.nsenterBinaryExists()) {
                "${RootNamespace.NSENTER_PATH} -t 1 -m -- $sh -c " +
                    TaskManagerShellExecutor.shellQuote(command)
            } else {
                command
            }
            val process = runCatching {
                ProcessBuilder(TaskManagerShellExecutor.resolveSuInvocation(), "-c", launcher)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: return null
            try {
                if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return null
                }
                if (temp.length() <= 0L || temp.length() > maxBytes * 4 / 3) return null
                val encoded = temp.readText().trim()
                if (encoded.length > maxBytes * 4 / 3) return null
                return Base64.decode(encoded, Base64.DEFAULT)
            } finally {
                runCatching { process.destroy() }
            }
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { temp.delete() }
        }
    }

    private fun video(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )?.let { scale(it) }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun apk(context: Context, file: File): Bitmap? {
        val packageManager = context.packageManager
        val info = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            0,
        ) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath
        val drawable = runCatching { appInfo.loadIcon(packageManager) }.getOrNull()
            ?: return null
        return runCatching {
            drawable.toBitmap(MAX_EDGE_PX, MAX_EDGE_PX)
        }.getOrNull()
    }

    private fun pdf(file: File): Bitmap? {
        val descriptor = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return null
        val renderer = runCatching { PdfRenderer(descriptor) }.getOrNull()
            ?: run {
                runCatching { descriptor.close() }
                return null
            }
        return try {
            if (renderer.pageCount <= 0) return null
            renderer.openPage(0).use { page ->
                val width = page.width.coerceAtLeast(1)
                val height = page.height.coerceAtLeast(1)
                val scale = (MAX_EDGE_PX.toFloat() / maxOf(width, height)).coerceAtMost(1f)
                val bitmap = Bitmap.createBitmap(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.RGB_565,
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }

    private fun scale(source: Bitmap): Bitmap {
        val maxEdge = maxOf(source.width, source.height)
        if (maxEdge <= MAX_EDGE_PX) return source
        val ratio = MAX_EDGE_PX.toFloat() / maxEdge
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== source) source.recycle()
        return scaled
    }
}
