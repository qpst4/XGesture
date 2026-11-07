package com.slideindex.app.clipboard

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

/**
 * 拖拽给第三方宿主时，把源文件镜像到系统 MediaStore。
 *
 * 微信 / QQ 等宿主只认系统相册、视频、音频或下载集合的 `content://media/...` URI，
 * 不认应用自己的 FileProvider authority。跨应用拖拽前先落一份系统可读镜像，
 * 才能让这些宿主在松手后真正读取到文件。
 *
 * 代价是这份镜像会作为一张图短暂进相册，所以生命周期只服务这一次拖拽：
 * 谁起拖谁持有 [DragMirrorSession]，投放成功留 [DROP_RELEASE_GRACE_MS] 给宿主读完，
 * 没人接收就立即回收。偏好存储里的时间戳只是「进程被杀留下的孤儿」标记，
 * [purgeOrphans] 只按年龄判定清理，不依赖「哪个进程建的」这类隐含假设。
 */
object DragFileMirror {

    private const val TAG = "DragFileMirror"
    internal const val PREFS_NAME = "drag_file_mirror_records"
    private const val CACHE_DIR = "SlideIndex/DragCache"

    /** [CACHE_DIR] 的小写形式，方便判定 MediaStore 路径是不是拖拽镜像。 */
    val PATH_MARKER: String = CACHE_DIR.lowercase(Locale.ROOT)

    /** 投放成功后留给宿主读完镜像的时间：宿主松手时已拿到文件，这里只覆盖异步拷贝。 */
    const val DROP_RELEASE_GRACE_MS = 10_000L

    /**
     * 收不到拖拽结束回调（部分宿主不派发）时的硬上限。
     *
     * 必须长于任何一次真实拖拽手势，否则会把正在投放的镜像删掉；漏网的部分交给 [purgeOrphans]。
     */
    private const val MAX_HOLD_MS = 5 * 60 * 1000L

    /** 孤儿记录的最长保留时间，必须大于 [MAX_HOLD_MS]。 */
    private const val ORPHAN_MAX_AGE_MS = 10 * 60 * 1000L

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 将 [source] 复制到 MediaStore 并返回系统 URI。失败时返回 null。
     *
     * 图片进入相册集合，视频 / 音频进入各自集合，其余文件进入下载集合。
     */
    fun prepareForHost(
        context: Context,
        source: Uri,
        displayName: String?,
        mimeType: String?,
        hostPackage: String?,
    ): Uri? {
        purgeOrphans(context)
        val app = context.applicationContext
        val resolver = app.contentResolver
        val mime = resolveMime(app, source, displayName, mimeType)
        val name = ensureExtension(
            sanitizeFileName(displayName ?: queryDisplayName(app, source) ?: source.lastPathSegment ?: "file"),
            mime,
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathFor(mime))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching {
            resolver.insert(collectionFor(mime), values)
        }.getOrNull() ?: return null
        val copied = runCatching {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(target, "w")?.use { output ->
                    input.copyTo(output)
                    output.flush()
                } ?: return@runCatching false
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
        if (!copied || querySize(app, target) == 0L) {
            runCatching { resolver.delete(target, null, null) }
            return null
        }
        runCatching {
            resolver.update(
                target,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        if (!hostPackage.isNullOrBlank()) {
            runCatching {
                app.grantUriPermission(hostPackage, target, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val prefs = prefsOf(app)
        val key = target.toString()
        val createdAt = System.currentTimeMillis()
        prefs.edit().putLong(key, createdAt).apply()
        Log.i(TAG, "mirror created uri=$target name=$name mime=$mime")
        cleanupScope.launch {
            delay(MAX_HOLD_MS)
            deleteMirrors(app, prefs, listOf(target), reason = "hard-cap")
        }
        return target
    }

    /** 立即回收镜像：没有任何宿主接收这次投放，没人会去读它。 */
    fun releaseNow(appContext: Context, mirrors: List<Uri>, reason: String) {
        val app = appContext.applicationContext
        deleteMirrors(app, prefsOf(app), mirrors, reason)
    }

    /** 延迟回收镜像：投放成功，留一段宿主异步拷贝的时间。 */
    fun releaseAfter(appContext: Context, mirrors: List<Uri>, graceMs: Long, reason: String) {
        if (graceMs <= 0L) {
            releaseNow(appContext, mirrors, reason)
            return
        }
        val app = appContext.applicationContext
        cleanupScope.launch {
            delay(graceMs)
            deleteMirrors(app, prefsOf(app), mirrors, reason)
        }
    }

    /**
     * 清理进程被杀留下的孤儿镜像。
     *
     * 只按年龄判定：比 [ORPHAN_MAX_AGE_MS] 更老的记录不可能还有拖拽在用，
     * 所以任何进程、任何时刻调用都是安全的，不需要判断镜像由哪个进程创建。
     */
    fun purgeOrphans(context: Context) {
        val app = context.applicationContext
        val prefs = prefsOf(app)
        val now = System.currentTimeMillis()
        val stale = prefs.all.mapNotNull { (key, value) ->
            val createdAt = value as? Long ?: return@mapNotNull key
            key.takeIf { createdAt <= now - ORPHAN_MAX_AGE_MS }
        }
        if (stale.isEmpty()) return
        deleteMirrors(
            app = app,
            prefs = prefs,
            mirrors = stale.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() },
            reason = "orphan-sweep",
        )
    }

    private fun prefsOf(app: Context): SharedPreferences =
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun deleteMirrors(
        app: Context,
        prefs: SharedPreferences,
        mirrors: List<Uri>,
        reason: String,
    ) {
        mirrors.forEach { mirror ->
            val rows = runCatching { app.contentResolver.delete(mirror, null, null) }.getOrDefault(0)
            prefs.edit { remove(mirror.toString()) }
            Log.i(TAG, "mirror released reason=$reason rows=$rows uri=$mirror")
        }
    }

    private fun collectionFor(mime: String): Uri = when {
        mime.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    private fun relativePathFor(mime: String): String = when {
        mime.startsWith("image/") -> "${Environment.DIRECTORY_PICTURES}/$CACHE_DIR"
        mime.startsWith("video/") -> "${Environment.DIRECTORY_MOVIES}/$CACHE_DIR"
        mime.startsWith("audio/") -> "${Environment.DIRECTORY_MUSIC}/$CACHE_DIR"
        else -> "${Environment.DIRECTORY_DOWNLOADS}/$CACHE_DIR"
    }

    private fun resolveMime(
        context: Context,
        source: Uri,
        displayName: String?,
        provided: String?,
    ): String {
        val normalized = provided?.trim()?.takeIf { it.isNotEmpty() && it != "*/*" }
        val fromResolver = runCatching { context.contentResolver.getType(source) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "*/*" }
        val fromExtension = extensionOf(displayName ?: source.lastPathSegment)
            .let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        return when {
            normalized != null &&
                normalized != "image/*" &&
                normalized != "application/octet-stream" -> normalized
            !fromResolver.isNullOrBlank() -> fromResolver
            !fromExtension.isNullOrBlank() -> fromExtension
            normalized == "image/*" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private fun queryDisplayName(context: Context, source: Uri): String? = runCatching {
        context.contentResolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun querySize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull()

    private fun extensionOf(raw: String?): String =
        raw?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.length in 1..8 }
            .orEmpty()

    private fun ensureExtension(name: String, mime: String): String {
        if (name.substringAfterLast('.', "").isNotBlank()) return name
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: return name
        return "$name.$extension"
    }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.replace('\\', '/').substringAfterLast('/').trim()
        val cleaned = INVALID_NAME.matcher(trimmed).replaceAll("_").trim('.', ' ')
        return cleaned.ifBlank { "file" }
    }

    private val INVALID_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]")
}

/**
 * 一次拖拽会话持有的临时镜像。
 *
 * 由起拖的一方创建、拖拽结束时回收：投放成功用 [releaseAfter] 留一小段读取时间，
 * 没人接收用 [releaseNow] 立即清掉。除此之外只有 [DragFileMirror.purgeOrphans] 会碰它，
 * 而那里的年龄门槛长于任何一次拖拽，所以不会误删正在使用的镜像。
 */
class DragMirrorSession internal constructor(
    private val appContext: Context,
    val mirrors: List<Uri>,
) {
    val isEmpty: Boolean get() = mirrors.isEmpty()

    fun releaseAfter(graceMs: Long) =
        DragFileMirror.releaseAfter(appContext, mirrors, graceMs, reason = "drop-accepted")

    fun releaseNow() =
        DragFileMirror.releaseNow(appContext, mirrors, reason = "not-accepted")
}
