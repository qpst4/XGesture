package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.util.Locale

/**
 * 一次拖拽的剪贴项准备结果。
 *
 * [mirrorSession] 持有本次为宿主临时镜像到系统媒体库的文件，拖拽结束时按结果回收。
 */
data class DragClipPreparation(
    val clipData: ClipData,
    val mirrorSession: DragMirrorSession,
)

/**
 * 跨应用拖放前按前台宿主调整 URI。
 *
 * 微信 / QQ 等宿主会忽略应用私有 FileProvider URI，只认系统 MediaStore URI；
 * 对这些宿主先把文件镜像到 MediaStore，再交给系统拖拽框架。
 *
 * 注意镜像只服务于「拖拽」这一条链路：分享兜底用的是原始 clip，
 * 不受镜像回收时间影响。
 */
object ClipboardDragClipHelper {

    private const val TAG = "ClipboardDragClip"

    fun remapForHostIfNeeded(
        context: Context,
        clipData: ClipData,
        hostPackage: String?,
    ): DragClipPreparation? {
        if (hostPackage.isNullOrBlank() || hostPackage == context.packageName) {
            return DragClipPreparation(clipData, emptySession(context))
        }
        return remapUrisForHost(context, clipData, hostPackage)
    }

    /**
     * 只有 `content://media/...` 这类系统媒体库 URI 宿主本来就能读，可以直接用原件；
     * 应用私有 FileProvider、`file://` 等宿主读不到的 URI 都要先镜像一份系统 URI。
     */
    fun needsSystemMirror(uri: Uri): Boolean =
        uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY

    private fun remapUrisForHost(
        context: Context,
        base: ClipData,
        hostPackage: String,
    ): DragClipPreparation? {
        val requiresMirror = (0 until base.itemCount).any { index ->
            base.getItemAt(index).uri?.let(::needsSystemMirror) == true
        }
        if (!requiresMirror) return DragClipPreparation(base, emptySession(context))
        val items = mutableListOf<ClipData.Item>()
        val mimeTypes = linkedSetOf<String>()
        for (index in 0 until base.description.mimeTypeCount) {
            mimeTypes += base.description.getMimeType(index)
        }
        val mirroredUris = mutableListOf<Uri>()
        var deliverableCount = 0
        for (index in 0 until base.itemCount) {
            val item = base.getItemAt(index)
            val sourceUri = item.uri
            if (sourceUri == null) {
                items.add(item)
                continue
            }
            val mime = resolveMime(context, sourceUri)
            val sendUri = if (needsSystemMirror(sourceUri)) {
                val mirrored = DragFileMirror.prepareForHost(
                    context = context,
                    source = sourceUri,
                    displayName = resolveDisplayName(context, sourceUri, mime),
                    mimeType = mime,
                    hostPackage = hostPackage,
                )
                if (mirrored == null) {
                    Log.w(TAG, "mirror failed, drop item uri=$sourceUri host=$hostPackage")
                    continue
                }
                mirroredUris += mirrored
                mirrored
            } else {
                sourceUri
            }
            items += ClipData.Item(sendUri)
            mimeTypes += mime
            mimeTypes += wildcardFor(mime)
            if (!mime.startsWith("image/") && !mime.startsWith("video/") && !mime.startsWith("audio/")) {
                mimeTypes += "*/*"
            }
            deliverableCount++
        }
        if (deliverableCount == 0 || items.isEmpty()) return null
        val label = base.description.label?.toString()?.takeIf { it.isNotBlank() } ?: "clipboard"
        val description = ClipDescription(label, mimeTypes.toTypedArray())
        val clip = ClipData(description, items.first())
        items.drop(1).forEach { clip.addItem(it) }
        return DragClipPreparation(clip, DragMirrorSession(context.applicationContext, mirroredUris))
    }

    private fun emptySession(context: Context): DragMirrorSession =
        DragMirrorSession(context.applicationContext, emptyList())

    private fun resolveMime(context: Context, uri: Uri): String {
        val fromResolver = runCatching { context.contentResolver.getType(uri) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "*/*" }
        if (fromResolver != null && fromResolver != "application/octet-stream") return fromResolver
        val fromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
        )
        return fromExtension ?: fromResolver ?: "application/octet-stream"
    }

    private fun resolveDisplayName(context: Context, uri: Uri, mime: String): String {
        val queried = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val raw = queried ?: uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "drag"
        if (raw.substringAfterLast('.', "").isNotBlank()) return raw
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        return "$raw.$extension"
    }

    private fun wildcardFor(mime: String): String =
        if (mime.contains('/')) "${mime.substringBefore('/')}/*" else "*/*"
}
