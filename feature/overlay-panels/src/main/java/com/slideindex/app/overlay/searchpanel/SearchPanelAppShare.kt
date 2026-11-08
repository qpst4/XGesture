package com.slideindex.app.overlay.searchpanel

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import com.slideindex.app.overlay.panels.BuildConfig
import com.slideindex.app.overlay.share.ShareSheetLauncher
import com.slideindex.app.overlay.share.ShareSheetPayload
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分享已安装应用的安装包。
 *
 * FileProvider 只会授权 `res/xml/file_paths.xml` 里声明过的目录，`/data/app/.../base.apk`
 * 不在其中，所以不能把系统路径直接交给它 —— 先把安装包复制到 `cache/apk_share/`，
 * 再分享这份副本。
 *
 * 带 split 的应用（AAB / Play 安装）把 base + splits 打成一个 `.apks` zip；
 * 只分享 base.apk 会缺 so/资源，对方装不上或闪退。单 APK 应用仍然分享 `.apk`。
 */
object SearchPanelAppShare {
    private const val TAG = "SearchPanelAppShare"
    private const val SHARE_DIR = "apk_share"
    private const val MAX_STEM_LENGTH = 60
    private const val COPY_BUFFER_SIZE = 64 * 1024
    private const val MIN_FREE_SPACE_BYTES = 16L * 1024 * 1024
    private const val MAX_CACHE_AGE_MS = 60L * 60 * 1000

    private const val MIME_APK = "application/vnd.android.package-archive"
    private const val MIME_APKS = "application/zip"

    private val INVALID_FILE_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]")

    suspend fun shareApk(
        context: Context,
        packageName: String,
        dismissSource: (() -> Unit)? = null,
    ): Boolean {
        val prepared = withContext(Dispatchers.IO) {
            runCatching { prepare(context, packageName) }
                .onFailure { Log.w(TAG, "prepare failed: $packageName", it) }
                .getOrNull()
        } ?: return false

        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                prepared.file,
            )
        }.onFailure { Log.w(TAG, "FileProvider uri failed: ${prepared.file}", it) }
            .getOrNull() ?: return false

        return withContext(Dispatchers.Main) {
            runCatching {
                ShareSheetLauncher.share(
                    context = context,
                    payload = ShareSheetPayload.Files(
                        uris = listOf(uri),
                        mimeType = prepared.mimeType,
                    ),
                    dismissSource = dismissSource,
                )
            }.onFailure { Log.w(TAG, "share sheet failed", it) }.getOrDefault(false)
        }
    }

    private data class PreparedShare(val file: File, val mimeType: String)

    @Suppress("DEPRECATION")
    private fun prepare(context: Context, packageName: String): PreparedShare? {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val sources = buildList {
            appInfo.sourceDir?.let { add(File(it)) }
            appInfo.splitSourceDirs?.forEach { path ->
                if (path.isNotBlank()) add(File(path))
            }
        }.filter { it.isFile }.distinctBy { it.absolutePath }
        if (sources.isEmpty()) {
            Log.w(TAG, "no readable apk for $packageName")
            return null
        }

        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        pruneOldFiles(dir)

        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrNull()
            ?: packageName
        val versionName = runCatching { pm.getPackageInfo(packageName, 0).versionName }.getOrNull()
        val isSplit = sources.size > 1
        val extension = if (isSplit) "apks" else "apk"
        val dest = File(dir, "${buildFileStem(label, versionName)}.$extension")

        val requiredBytes = sources.sumOf { it.length() }
        val freeBytes = dir.usableSpace
        // usableSpace 在极少数设备上会返回 0（未知）；只在明确不够时才拒绝。
        if (freeBytes > 0 && freeBytes < requiredBytes + MIN_FREE_SPACE_BYTES) {
            Log.w(TAG, "not enough cache space: need=$requiredBytes free=$freeBytes")
            return null
        }
        if (isSplit) {
            writeZip(sources, dest)
        } else {
            copyFile(sources.first(), dest)
        }
        return PreparedShare(dest, if (isSplit) MIME_APKS else MIME_APK)
    }

    /** 文件名里保留中文，只清掉文件系统不接受/容易出问题的字符。 */
    private fun buildFileStem(label: String, versionName: String?): String {
        val raw = listOfNotNull(
            label.trim().ifBlank { null },
            versionName?.trim()?.ifBlank { null },
        ).joinToString("_")
        val cleaned = INVALID_FILE_NAME.matcher(raw).replaceAll("_")
            .trim('.', ' ')
            .replace(Regex("_+"), "_")
            .trim('_')
        return cleaned.ifBlank { "app" }.take(MAX_STEM_LENGTH)
    }

    private fun pruneOldFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    private fun copyFile(source: File, dest: File) {
        val part = partFile(dest)
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(part).use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            }
            moveReplacing(part, dest)
        } finally {
            part.delete()
        }
    }

    private fun writeZip(sources: List<File>, dest: File) {
        val part = partFile(dest)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(part))).use { zip ->
                // APK 本身就是压缩包，再 deflate 一遍只费 CPU，体积也不会明显变小。
                zip.setLevel(Deflater.NO_COMPRESSION)
                sources.forEach { source ->
                    zip.putNextEntry(ZipEntry(source.name))
                    FileInputStream(source).use { input ->
                        input.copyTo(zip, COPY_BUFFER_SIZE)
                    }
                    zip.closeEntry()
                }
            }
            moveReplacing(part, dest)
        } finally {
            part.delete()
        }
    }

    private fun partFile(dest: File): File =
        File(dest.parentFile, "${dest.name}.${System.nanoTime()}.part")

    private fun moveReplacing(source: File, dest: File) {
        runCatching {
            Files.move(
                source.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            // 极少数 ROM 的 java.nio 实现对同目录 move 也会抛异常，退回 rename/copy。
            dest.delete()
            if (!source.renameTo(dest)) {
                source.copyTo(dest, overwrite = true)
                source.delete()
            }
        }
    }
}
