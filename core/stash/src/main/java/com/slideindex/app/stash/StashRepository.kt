package com.slideindex.app.stash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.util.LruCache
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class StashRepository @Inject constructor(
    @ApplicationContext context: Context,
    /**
     * 元数据仓库（标签 / 完成态 / 追加 / 来源 / 提醒）。
     *
     * ⚠️ **这个形参不能省**：`StashMetaRepository` 是自注册的单例（`init` 里把自己挂到
     * `StashAccess.metaRepository`），但**没有任何一处注入它** —— 于是它从来没被构造过，
     * 元数据层在真机上一直是死的（没有标签 chip、没有完成态、来源也永远为空）。
     * 让它成为仓储的构造依赖，创建闪念仓储时就会一并创建它（Hilt 先造形参）。
     */
    private val metaRepository: StashMetaRepository,
) {
    private val appContext = context.applicationContext
    private val stashDir = File(appContext.filesDir, STASH_DIR_NAME).apply { mkdirs() }
    private val imageDir = File(stashDir, IMAGE_DIR_NAME).apply { mkdirs() }

    /**
     * 语音块的文件目录（§0.16.21）。
     *
     * ⚠️ **与图片分目录、不混放**：孤儿清理是"列目录 → 按引用集合删"，
     * 混在一起的话图片的引用集合会把音频文件当孤儿删掉（反之亦然）。分目录是最省心的隔离。
     */
    private val audioDir = File(stashDir, AUDIO_DIR_NAME).apply { mkdirs() }
    private val indexFile = File(stashDir, INDEX_FILE_NAME)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val thumbnailCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val _entries = MutableStateFlow<List<StashEntry>>(emptyList())
    val entries: StateFlow<List<StashEntry>> = _entries.asStateFlow()

    /**
     * 上一次读盘是否失败。失败期间拒绝一切写入，避免把坏文件当成空表覆盖掉。
     *
     * ⚠️ 必须声明在 `init` **之前**：Kotlin 按声明顺序执行属性初始化与 `init` 块，
     * 声明在后面的话它会在 `init` 跑完后再被赋一次 `false`，把读盘失败的标志冲掉。
     */
    @Volatile
    private var indexUnreadable = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 写操作统一入口：进程内 mutex + **跨进程文件锁**；写完广播通知其它进程重载。
     * 各写方法内部本来就是"锁内重新读盘再计算"，套上这层即可跨进程安全。
     */
    private suspend fun <T> withCrossProcessWrite(block: suspend () -> T): T =
        mutex.withLock {
            com.slideindex.app.util.CrossProcessStore.withFileLock(indexFile) {
                val result = block()
                com.slideindex.app.util.CrossProcessStore.notifyChanged(appContext, indexFile)
                result
            }
        }

    private suspend fun reloadFromDiskForExternalChange() {
        mutex.withLock { _entries.value = trimToMax(readFromDiskSync()) }
    }

    init {
        com.slideindex.app.util.CrossProcessStore.registerListener(appContext) { changed ->
            if (changed.absolutePath == indexFile.absolutePath) {
                scope.launch { reloadFromDiskForExternalChange() }
            }
        }
        val loaded = readFromDiskSync()
        val trimmed = trimToMax(loaded)
        if (trimmed.size != loaded.size) {
            writeToDisk(trimmed)
        }
        _entries.value = trimmed
        // 启动时收敛媒体文件：`delete` 为了支持撤销不再立刻删图/删录音，孤儿统一在这里清。
        if (!indexUnreadable) {
            pruneOrphanMedia(trimmed)
        }
        StashAccess.repository = this
    }

    suspend fun addText(text: String): StashEntry? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val entry = StashEntry(
                    id = UUID.randomUUID().toString(),
                    type = StashEntryType.TEXT,
                    text = trimmed,
                    createdAtEpochMs = System.currentTimeMillis()
                )
                val next = trimToMax(listOf(entry) + readFromDisk())
                writeToDisk(next)
                _entries.value = next
                entry
            }
        }
    }

    suspend fun addImage(
        bitmap: Bitmap,
        pinDisplayWidthPx: Int? = null,
        pinDisplayHeightPx: Int? = null
    ): StashEntry? {
        return withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val id = UUID.randomUUID().toString()
                val fileName = "$id.png"
                val saved = saveImage(fileName, bitmap) ?: return@withCrossProcessWrite null
                val entry = StashEntry(
                    id = id,
                    type = StashEntryType.IMAGE,
                    imageFileName = saved,
                    createdAtEpochMs = System.currentTimeMillis(),
                    pinDisplayWidthPx = pinDisplayWidthPx?.takeIf { it > 0 },
                    pinDisplayHeightPx = pinDisplayHeightPx?.takeIf { it > 0 }
                )
                val next = trimToMax(listOf(entry) + readFromDisk())
                writeToDisk(next)
                _entries.value = next
                entry
            }
        }
    }

    suspend fun addRich(
        parts: List<StashRichPart>,
        htmlText: String? = null
    ): StashEntry? {
        if (parts.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val id = UUID.randomUUID().toString()
                val imageTotal = parts.count { it is StashRichPart.Image }
                val audioTotal = parts.count { it is StashRichPart.Audio }
                var imageIndex = 0
                var audioIndex = 0
                val contentBlocks = mutableListOf<ClipboardContentBlock>()
                val textParts = mutableListOf<String>()
                /** 本次**新建**的文件（图片 + 复制进来的音频）：失败回滚时删掉它们。 */
                val createdFiles = mutableListOf<File>()

                for (part in parts) {
                    when (part) {
                        is StashRichPart.Text -> {
                            val trimmed = part.text.trim()
                            if (trimmed.isEmpty()) continue
                            contentBlocks += ClipboardContentBlock.text(trimmed)
                            textParts += trimmed
                        }
                        is StashRichPart.Image -> {
                            val fileName = if (imageTotal <= 1) {
                                "$id.png"
                            } else {
                                "${id}_${imageIndex++}.png"
                            }
                            val saved = saveImage(fileName, part.bitmap)
                            if (saved == null) continue
                            createdFiles += File(imageDir, saved)
                            contentBlocks += ClipboardContentBlock.image(saved)
                        }
                        is StashRichPart.Audio -> {
                            val preferred = if (audioTotal <= 1) {
                                "$id.m4a"
                            } else {
                                "${id}_${audioIndex++}.m4a"
                            }
                            val persisted = persistAudio(part.sourcePath, preferred) ?: continue
                            if (persisted.created) createdFiles += File(audioDir, persisted.fileName)
                            contentBlocks += ClipboardContentBlock.audio(
                                fileName = persisted.fileName,
                                durationMs = part.durationMs,
                            )
                        }
                    }
                }

                if (contentBlocks.isEmpty()) {
                    createdFiles.forEach { it.delete() }
                    return@withCrossProcessWrite null
                }

                val entry = StashEntry(
                    id = id,
                    type = StashEntryType.RICH,
                    text = textParts.joinToString("\n\n").ifBlank { null },
                    imageFileName = contentBlocks
                        .firstOrNull { it.kind == ClipboardBlockKind.IMAGE }
                        ?.fileName,
                    contentBlocks = contentBlocks,
                    htmlText = htmlText?.trim()?.takeIf { it.isNotEmpty() },
                    createdAtEpochMs = System.currentTimeMillis()
                )
                val next = trimToMax(listOf(entry) + readFromDisk())
                try {
                    writeToDisk(next)
                } catch (t: Throwable) {
                    createdFiles.forEach { it.delete() }
                    Log.w(TAG, "addRich: index write failed, rolled back", t)
                    return@withCrossProcessWrite null
                }
                _entries.value = next
                entry
            }
        }
    }

    /**
     * 给已有条目追加图片（§0.16.14）。返回是否成功。
     *
     * 与 [addRich] 的差别只在"写哪一条"：这里是**锁内重新读盘 → 定位那一条 → 只在末尾接图片块 → 整表写回**，
     * 所以和别的写路径（编辑正文 / 星标 / 另一个进程）不会互相覆盖。
     *
     * 两条"为什么不这样写"的说明：
     * - 起点取 `entry.resolvedContentBlocks()` 而不是 `entry.contentBlocks`：纯图条目（type=IMAGE）的
     *   图片只存在 `imageFileName` 里、`contentBlocks` 是空的；直接往空表上接图片会让
     *   `allImageFileNames()` 认不出原图（原图会变成孤儿文件并被 prune 掉）。
     * - **type 一律升到 [StashEntryType.RICH]**：卡片里的富图缩略图是 `enabled = entry.type == RICH && …`
     *   门控的，不升 type 就等于"存了但看不见"。正文 / 老图都已经进了 `contentBlocks`（见上一条），所以升级不丢内容。
     * - `createdAtEpochMs` / `pinDisplay*` / `starred` / `text` / `htmlText` 一概不动
     *   （完成态、标签、提醒在 `StashMetaRepository`，本方法不碰）。
     *
     * 落盘是"要么全成、要么不改文件"：任何一张图存不下来、或整表写不进去，都会把本次已经写下的图片文件删掉再返回 false。
     */
    suspend fun appendImages(entryId: String, bitmaps: List<Bitmap>): Boolean {
        if (entryId.isBlank() || bitmaps.isEmpty()) return false
        return withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val current = readFromDisk()
                val index = current.indexOfFirst { it.id == entryId }
                if (index < 0) return@withCrossProcessWrite false
                val entry = current[index]
                val existing = entry.resolvedContentBlocks()
                // 现有图片数只用来生成"看着顺眼"的序号；真正的唯一性由 [nextAppendImageFileName] 保证。
                val existingImageCount = existing.count { it.kind == ClipboardBlockKind.IMAGE }

                val savedFiles = mutableListOf<String>()
                val appendedBlocks = mutableListOf<ClipboardContentBlock>()
                for ((offset, bitmap) in bitmaps.withIndex()) {
                    val fileName = nextAppendImageFileName(entryId, existingImageCount + offset)
                    val saved = saveImage(fileName, bitmap)
                    if (saved == null) {
                        // 半路失败：把这一批已经落盘的文件收回，索引保持原样。
                        savedFiles.forEach { File(imageDir, it).delete() }
                        Log.w(TAG, "appendImages: saveImage failed, rolled back ${savedFiles.size} file(s)")
                        return@withCrossProcessWrite false
                    }
                    savedFiles += saved
                    appendedBlocks += ClipboardContentBlock.image(saved)
                }
                if (appendedBlocks.isEmpty()) return@withCrossProcessWrite false

                // 与 addRich 同一语义：imageFileName 指向新追加的第一张图；原值兜底 ——
                // 这样升级到 RICH 之后拖拽（`HistoryEntryDragHelper` 读这个字段）仍然指得到一个真实文件。
                val firstAppendedImageFileName = appendedBlocks.first().fileName
                    .takeIf { it.isNotBlank() } ?: entry.imageFileName
                val next = current.toMutableList().also {
                    it[index] = entry.copy(
                        // 不升 type 就是"存了但看不见"（见 KDoc）。
                        type = StashEntryType.RICH,
                        contentBlocks = existing + appendedBlocks,
                        imageFileName = firstAppendedImageFileName,
                    )
                }
                try {
                    writeToDisk(next)
                } catch (t: Throwable) {
                    savedFiles.forEach { File(imageDir, it).delete() }
                    Log.w(TAG, "appendImages: index write failed, rolled back", t)
                    return@withCrossProcessWrite false
                }
                _entries.value = next
                true
            }
        }
    }

    /**
     * 追加图片的文件名：`"${entryId}_append_${序号}.png"`，**绝不覆盖已有文件**。
     *
     * 序号从"现有图片数"起算只是为了名字连续；磁盘上已经存在同名文件时（例如上一次追加写盘失败留下的
     * 孤儿文件、或同一 id 上连着追加了两次）就往后挪，直到撞上一个没被占用的名字。
     */
    private fun nextAppendImageFileName(entryId: String, startIndex: Int): String {
        var index = startIndex.coerceAtLeast(0)
        while (true) {
            val candidate = "${entryId}_append_$index.png"
            if (!File(imageDir, candidate).exists()) return candidate
            index++
        }
    }

    /**
     * 暂存夹里某张图片的**绝对路径**（§0.16.17）。
     *
     * 用途只有"显示"：条目里的图片块存的是**文件名**（`ClipboardContentBlock.fileName`），
     * 而 `BitmapFactory` / Compose 需要一个真路径。就地编辑条的块编辑器靠它把
     * "已有图片"显示出来。
     *
     * 为什么在这里给一个方法、而不是让 UI 自己拼 `imageDir`：图片目录是仓储的实现细节
     * （`STASH_DIR_NAME/IMAGE_DIR_NAME`），UI 拼一次就等于把它写死在两个地方。
     *
     * ⚠️ **不**检查文件是否存在：调用方（Compose 侧）自己会按"解码失败"兜底，
     * 而这里只是"文件名 → 路径"的字符串拼接（IO 也不该在组合里做）。
     */
    fun imageFilePath(fileName: String?): String? {
        val name = fileName?.takeIf { it.isNotBlank() } ?: return null
        return File(imageDir, name).absolutePath
    }

    /**
     * **整体替换**某条目的块序列（§0.16.17）：就地编辑条保存时按块顺序写回。
     *
     * 语义（与 [addRich] / [appendImages] 同一套"要么全成、要么不改"）：
     * - 锁内**重新读盘 → 定位那一条 → 用传入的块顺序整体替换 → 整表写回**（不与别的写路径互相覆盖）；
     * - 文本块 `trim()` 后为空一律**丢弃**；图片块**按顺序**落盘成新文件，
     *   文件名用 `"${entryId}_edit_${序号}.png"` 且**绝不覆盖**已有文件（[nextEditImageFileName]）；
     * - 任何一张图存不下来、或整表写不进去 → 把本次已经写下的图片文件删掉、索引保持原样、返回 false；
     * - `imageFileName` 指向**第一个图片块**（没有图片块则保留原值），`type` 升到
     *   [StashEntryType.RICH]（卡片里的富图缩略图是 RICH 门控的，不升就等于"存了但看不见"）；
     * - `createdAtEpochMs` / `pinDisplay*` / `starred` / `htmlText` / `text` 之外的元数据一概不动
     *   （完成态、标签、提醒在 `StashMetaRepository`，本方法不碰）。
     *
     * ⚠️ 与 [updateText] 的分工：`updateText` 只改**第一个**文字块（老编辑条那条路），
     * 本方法是"整份正文 + 图片"都按块顺序替换。新代码（编辑条）只走这一条。
     */
    suspend fun replaceBlocks(entryId: String, parts: List<StashRichPart>): Boolean {
        if (entryId.isBlank()) return false
        return withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val current = readFromDisk()
                val index = current.indexOfFirst { it.id == entryId }
                if (index < 0) return@withCrossProcessWrite false
                val entry = current[index]
                val keptText = parts.filterIsInstance<StashRichPart.Text>()
                    .map { it.text.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n\n")
                    .ifBlank { null }
                replaceBlocksInternal(
                    current = current,
                    index = index,
                    entry = entry,
                    parts = parts,
                    keptText = keptText,
                )
            }
        }
    }

    /**
     * 与 [replaceBlocks] 同一件事，但图片块**只带文件名、不重新落盘**（§0.16.17 的撤销用）。
     *
     * 为什么需要它：撤销"改了一次正文"要能把块序列**原样**放回去。走 [replaceBlocks] 的话
     * 图片会被重新编码成一批新文件（慢、还平白多出一堆孤儿文件），而撤销要还原的本来就是
     * "原来那几张文件"。这里直接按文件名重建 `contentBlocks`，不碰任何图片文件。
     *
     * ⚠️ `block.fileName` 指向的文件**必须已经存在**（就是撤回来的那一批）；不存在就跳过该块，
     * 不报错、也不调用 `saveImage`（不覆盖、不新建）。
     */
    suspend fun replaceBlockFileNames(
        entryId: String,
        blocks: List<ClipboardContentBlock>,
    ): Boolean {
        if (entryId.isBlank()) return false
        return withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val current = readFromDisk()
                val index = current.indexOfFirst { it.id == entryId }
                if (index < 0) return@withCrossProcessWrite false
                val entry = current[index]
                val rebuilt = blocks.mapNotNull { block ->
                    when (block.kind) {
                        ClipboardBlockKind.TEXT -> block.text.trim().takeIf { it.isNotEmpty() }
                            ?.let { ClipboardContentBlock.text(it) }

                        ClipboardBlockKind.IMAGE -> block.fileName.takeIf { it.isNotBlank() }
                            ?.takeIf { File(imageDir, it).exists() }
                            ?.let { ClipboardContentBlock.image(it) }

                        // §0.16.21：语音块同样"只按文件名重建、不重新落盘"（撤销要用回原来那一段录音）。
                        ClipboardBlockKind.AUDIO -> block.fileName.takeIf { it.isNotBlank() }
                            ?.takeIf { File(audioDir, it).exists() }
                            ?.let { ClipboardContentBlock.audio(it, block.durationMs) }

                        // 未知块：本版本连它是什么都不知道，**不能**写回（写回等于把它固化下来）。
                        ClipboardBlockKind.UNKNOWN -> null
                    }
                }
                if (rebuilt.isEmpty()) return@withCrossProcessWrite false
                val firstImage = rebuilt.firstOrNull { it.kind == ClipboardBlockKind.IMAGE }?.fileName
                    ?.takeIf { it.isNotBlank() } ?: entry.imageFileName
                val next = current.toMutableList().also {
                    it[index] = entry.copy(
                        type = StashEntryType.RICH,
                        text = rebuilt.filter { b -> b.kind == ClipboardBlockKind.TEXT }
                            .joinToString("\n\n") { b -> b.text }
                            .ifBlank { null },
                        contentBlocks = rebuilt,
                        imageFileName = firstImage,
                    )
                }
                try {
                    writeToDisk(next)
                } catch (t: Throwable) {
                    Log.w(TAG, "replaceBlockFileNames: index write failed", t)
                    return@withCrossProcessWrite false
                }
                _entries.value = next
                true
            }
        }
    }

    /** [replaceBlocks] 的锁内实现（拆出来只为让主方法读起来是"语义"而不是"十行样板"）。 */
    private suspend fun replaceBlocksInternal(
        current: List<StashEntry>,
        index: Int,
        entry: StashEntry,
        parts: List<StashRichPart>,
        keptText: String?,
    ): Boolean {
        /** 本次**新建**的文件（图片 + 复制进来的音频）：失败回滚时删掉它们。 */
        val createdFiles = mutableListOf<File>()
        val contentBlocks = mutableListOf<ClipboardContentBlock>()
        var imageIndex = 0
        var audioIndex = 0
        for (part in parts) {
            when (part) {
                is StashRichPart.Text -> {
                    val trimmed = part.text.trim()
                    if (trimmed.isEmpty()) continue
                    contentBlocks += ClipboardContentBlock.text(trimmed)
                }

                is StashRichPart.Image -> {
                    val fileName = nextEditImageFileName(entry.id, imageIndex++)
                    val saved = saveImage(fileName, part.bitmap)
                    if (saved == null) {
                        // 半路失败：把这一批已经落盘的文件收回，索引保持原样（要么全成、要么不改）。
                        createdFiles.forEach { it.delete() }
                        Log.w(TAG, "replaceBlocks: saveImage failed, rolled back ${createdFiles.size} file(s)")
                        return false
                    }
                    createdFiles += File(imageDir, saved)
                    contentBlocks += ClipboardContentBlock.image(saved)
                }

                is StashRichPart.Audio -> {
                    // ⚠️ 与图片不同：音频**不重新编码**（没有这种事）。
                    // 已经在本仓库里的（编辑条回填的文件名）直接复用 —— 复用不算"本次新建"，
                    // 所以不进 createdFiles：回滚时绝不能删用户原来的那段录音。
                    val persisted = persistAudio(
                        sourcePath = part.sourcePath,
                        preferredFileName = nextEditAudioFileName(entry.id, audioIndex++),
                    ) ?: continue
                    if (persisted.created) createdFiles += File(audioDir, persisted.fileName)
                    contentBlocks += ClipboardContentBlock.audio(
                        fileName = persisted.fileName,
                        durationMs = part.durationMs,
                    )
                }
            }
        }
        if (contentBlocks.isEmpty()) {
            createdFiles.forEach { it.delete() }
            return false
        }
        val firstImage = contentBlocks.firstOrNull { it.kind == ClipboardBlockKind.IMAGE }?.fileName
            ?.takeIf { it.isNotBlank() } ?: entry.imageFileName
        val next = current.toMutableList().also {
            it[index] = entry.copy(
                // 不升 type 就是"存了但看不见"（与 appendImages 同一条理由）。
                type = StashEntryType.RICH,
                text = keptText,
                contentBlocks = contentBlocks,
                imageFileName = firstImage,
            )
        }
        try {
            writeToDisk(next)
        } catch (t: Throwable) {
            createdFiles.forEach { it.delete() }
            Log.w(TAG, "replaceBlocks: index write failed, rolled back", t)
            return false
        }
        _entries.value = next
        return true
    }

    /**
     * 整体替换时新图的文件名：`"${entryId}_edit_${序号}.png"`，**绝不覆盖已有文件**。
     *
     * 与 [nextAppendImageFileName] 同一套做法（含"跳过已占用名字"的兜底），
     * 只是前缀换成 `edit`：一眼能看出这批图是"编辑时替换进来的"。
     */
    private fun nextEditImageFileName(entryId: String, startIndex: Int): String {
        var index = startIndex.coerceAtLeast(0)
        while (true) {
            val candidate = "${entryId}_edit_$index.png"
            if (!File(imageDir, candidate).exists()) return candidate
            index++
        }
    }

    /** 整体替换时新录音的文件名：`"${entryId}_voice_${序号}.m4a"`，**绝不覆盖**已有文件。 */
    private fun nextEditAudioFileName(entryId: String, startIndex: Int): String {
        var index = startIndex.coerceAtLeast(0)
        while (true) {
            val candidate = "${entryId}_voice_$index.m4a"
            if (!File(audioDir, candidate).exists()) return candidate
            index++
        }
    }

    /** [persistAudio] 的结果：`fileName` 是音频目录里的文件名；`created` = 这个文件是**本次新建**的。 */
    private data class PersistedAudio(val fileName: String, val created: Boolean)

    /**
     * 把 [sourcePath] 变成"音频目录里的一个文件"（§0.16.21）。
     *
     * 三条路，按"先看它是不是已经在仓库里"排序（**绝不覆盖、绝不重复复制**）：
     * ① [sourcePath] 本身就是音频目录下的绝对路径（编辑条里若传了绝对路径）→ 直接用文件名；
     * ② [sourcePath] 是音频目录里的**文件名**（编辑条回填的那种）→ 直接复用；
     * ③ 其它（cache 里刚录完的临时文件）→ **复制**进音频目录，名字用 [preferredFileName]
     *    或它的第一个空闲变体（[nextFreeAudioFileName]）。
     *
     * 返回 null = 源文件不可读（没录成 / 已被删）→ 调用方**跳过这一块**，不让整次保存失败。
     */
    private fun persistAudio(sourcePath: String, preferredFileName: String): PersistedAudio? {
        if (sourcePath.isBlank()) return null
        val source = File(sourcePath)
        if (source.parentFile?.absolutePath == audioDir.absolutePath && source.exists()) {
            return PersistedAudio(source.name, created = false)
        }
        val byName = File(audioDir, sourcePath)
        if (byName.exists() && byName.isFile && byName.length() > 0L) {
            return PersistedAudio(sourcePath, created = false)
        }
        if (!source.exists() || !source.isFile || source.length() <= 0L) return null
        val targetName = nextFreeAudioFileName(preferredFileName)
        val target = File(audioDir, targetName)
        val copied = runCatching {
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.length() > 0L
        }.getOrElse { error ->
            Log.w(TAG, "persistAudio: copy failed from $sourcePath", error)
            false
        }
        if (!copied) {
            runCatching { target.delete() }
            return null
        }
        return PersistedAudio(targetName, created = true)
    }

    /** 音频目录里第一个没被占用的名字（`x.m4a` → `x_1.m4a` → `x_2.m4a` …）。 */
    private fun nextFreeAudioFileName(preferred: String): String {
        if (preferred.isBlank()) return "${UUID.randomUUID()}.m4a"
        if (!File(audioDir, preferred).exists()) return preferred
        val base = preferred.substringBeforeLast('.', preferred)
        val extension = preferred.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val candidate = if (extension.isEmpty()) "${base}_$index" else "${base}_$index.$extension"
            if (!File(audioDir, candidate).exists()) return candidate
            index++
        }
    }

    /**
     * 暂存夹里某段语音的**绝对路径**（§0.16.21）—— 与 [imageFilePath] 同一套理由：
     * 音频目录是仓储的实现细节，UI 不该自己拼。`MediaPlayer` 要的就是一个能读的路径。
     *
     * ⚠️ 同样**不**检查文件是否存在：调用方按"播不了"兜底，IO 不该在组合里做。
     */
    fun audioFilePath(fileName: String?): String? {
        val name = fileName?.takeIf { it.isNotBlank() } ?: return null
        return File(audioDir, name).absolutePath
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val current = readFromDisk()
                if (current.none { it.id == id }) return@withCrossProcessWrite
                val next = current.filterNot { it.id == id }
                writeToDisk(next)
                _entries.value = next
                // ⚠️ 这里**故意不删图片文件**：撤销（[restore]）还要用它。
                // 孤儿文件由 [pruneOrphanMedia] 在下次启动时按"还有没有条目引用"统一清掉。
                //
                // 顺手把它的元数据（标签 / 完成态 / 追加 / 来源）一起清掉。
                // 另有一层兜底：`StashMetaRepository.pruneOrphans`（面板打开时按有效 id 收敛）。
                forgetMeta(id)
            }
        }
    }

    /**
     * 把一条（刚被删掉的）条目放回原位置 —— 删除的"撤销"。
     *
     * 图片文件没被删（见 [delete]），所以图片条目也能真的恢复。
     * 原位置已经不存在（列表变短/被裁掉）时夹到表尾，不会丢。
     */
    suspend fun restore(entry: StashEntry, index: Int): Boolean {
        return withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val current = readFromDisk()
                if (current.any { it.id == entry.id }) return@withCrossProcessWrite false
                val next = current.toMutableList().also {
                    it.add(index.coerceIn(0, it.size), entry)
                }
                val trimmed = trimToMax(next)
                writeToDisk(trimmed)
                _entries.value = trimmed
                true
            }
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val current = readFromDisk()
                current.forEach { deleteEntryMedia(it) }
                writeToDisk(emptyList())
                _entries.value = emptyList()
                current.forEach { forgetMeta(it.id) }
            }
        }
    }

    /**
     * 删掉没有被任何条目引用的**媒体文件**（图片 + 语音，§0.16.21 起语音也纳入同一套）。
     *
     * 存在的意义：`delete` 不再立刻删图片/录音（撤销要用），于是需要一个**统一的**收敛点；
     * 顺带也清掉崩溃/异常路径留下的孤儿文件。只在启动时跑一次，成本是列两次目录。
     *
     * ⚠️ 两个目录**各清各的**：图片的引用集合绝不能拿去和音频目录比对（那会把所有录音删光）。
     */
    private fun pruneOrphanMedia(entries: List<StashEntry>) {
        runCatching {
            val referencedImages = entries.flatMapTo(HashSet()) { it.allImageFileNames() }
            imageDir.listFiles()?.forEach { file ->
                if (file.name !in referencedImages) file.delete()
            }
        }.onFailure { Log.w(TAG, "pruneOrphanMedia(images) failed", it) }
        runCatching {
            val referencedAudio = entries.flatMapTo(HashSet()) { it.allAudioFileNames() }
            audioDir.listFiles()?.forEach { file ->
                if (file.name !in referencedAudio) file.delete()
            }
        }.onFailure { Log.w(TAG, "pruneOrphanMedia(audio) failed", it) }
    }

    /**
     * 清一条条目的元数据。**它失败了也不该让删除本身失败**，所以整段吞掉。
     */
    private suspend fun forgetMeta(entryId: String) {
        runCatching { metaRepository.forget(entryId) }
    }

    suspend fun toggleStar(id: String) {
        withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val next = readFromDisk().map { entry ->
                    if (entry.id == id) entry.copy(starred = !entry.starred) else entry
                }
                writeToDisk(next)
                _entries.value = next
            }
        }
    }

    fun loadImage(entry: StashEntry): Bitmap? {
        val fileName = entry.imageFileName ?: return null
        return loadBitmapByFileName(fileName)
    }

    fun loadBitmapByFileName(fileName: String?): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun loadImageThumbnail(entry: StashEntry, maxSidePx: Int = STASH_PREVIEW_MAX_SIDE_PX): Bitmap? {
        val fileName = entry.imageFileName ?: return null
        return loadThumbnailByFileName(entry.id, fileName, maxSidePx)
    }

    fun loadEntryThumbnailsForPreview(
        entry: StashEntry,
        maxSidePx: Int = STASH_PREVIEW_MAX_SIDE_PX
    ): List<Bitmap> =
        entry.allImageFileNames().mapNotNull { fileName ->
            loadThumbnailByFileName(entry.id, fileName, maxSidePx)
        }

    fun loadThumbnailByFileName(
        entryId: String,
        fileName: String?,
        maxSidePx: Int = STASH_PREVIEW_MAX_SIDE_PX
    ): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        val cacheKey = "$entryId:$fileName:side$maxSidePx"
        thumbnailCache.get(cacheKey)?.let { return it }
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSidePx)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        thumbnailCache.put(cacheKey, bitmap)
        return bitmap
    }

    fun uriForFile(fileName: String?): Uri? {
        if (fileName.isNullOrBlank()) return null
        val file = File(imageDir, fileName).takeIf { it.exists() }
            ?: File(audioDir, fileName).takeIf { it.exists() }
            ?: return null
        return runCatching {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
        }.getOrNull()
    }

    fun uriForAudioFile(fileName: String?): Uri? {
        if (fileName.isNullOrBlank()) return null
        val file = File(audioDir, fileName).takeIf { it.exists() } ?: return null
        return runCatching {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
        }.getOrNull()
    }

    fun dataUriForFile(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/png;base64,$encoded"
        }.getOrNull()
    }

    fun imageDimensions(fileName: String?): Pair<Int, Int>? {
        if (fileName.isNullOrBlank()) return null
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            bounds.outWidth to bounds.outHeight
        } else {
            null
        }
    }

    /** 删一条条目的全部媒体文件（图片 + 语音）。只在"确定不要了"的路径上用（清空 / 截断列表）。 */
    private fun deleteEntryMedia(entry: StashEntry) {
        entry.allImageFileNames().forEach { File(imageDir, it).delete() }
        entry.allAudioFileNames().forEach { File(audioDir, it).delete() }
    }

    fun loadImageThumbnailForCard(
        entry: StashEntry,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap? {
        val fileName = entry.imageFileName ?: return null
        return loadThumbnailByFileNameForCard(entry.id, fileName, targetWidthPx, maxVisibleHeightPx)
    }

    fun loadEntryThumbnailsForCard(
        entry: StashEntry,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): List<Bitmap> =
        entry.allImageFileNames().mapNotNull { fileName ->
            loadThumbnailByFileNameForCard(entry.id, fileName, targetWidthPx, maxVisibleHeightPx)
        }

    fun loadThumbnailByFileNameForCard(
        entryId: String,
        fileName: String?,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap? {
        if (fileName.isNullOrBlank() || targetWidthPx <= 0 || maxVisibleHeightPx <= 0) return null
        val cacheKey = "$entryId:$fileName:w$targetWidthPx:h$maxVisibleHeightPx"
        thumbnailCache.get(cacheKey)?.let { return it }
        val file = File(imageDir, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetWidthPx * 2) {
            sampleSize *= 2
        }
        // ⚠️ §0.16.20：**不要再按 `targetWidthPx * maxVisibleHeightPx` 限制像素**。
        //
        // 老写法是 `maxPixels = targetWidthPx * maxVisibleHeightPx * 2`。对**竖长截图**
        // （1080×2400，卡片渲染 296dp 宽 × 150dp 高 → 目标宽约 680px、可见高约 345px）
        // 这个预算是 680×345×2 ≈ 47 万像素，而原图 259 万像素 → 采样被硬推到 **2**，
        // 解出来只有 540px 宽（比目标还小），再 `scale` 到 680px 就成了**放大** ——
        // 用户说的"屏幕截图缩略图明显发糊"就是这么来的（宽度够、**高度维**被预算饿死）。
        //
        // 新预算按"**较大边**"给：`side²`（side = max(目标宽, 可见高)，再乘 2 的平方），
        // 等价于允许"最长边 ≤ 2×side"的方形图。高度方向因此不再被单独掐死；
        // 上限仍是 [HistoryCardImageMaxSourcePixels]（1200 万像素 ≈ 48MB），防止 1 亿像素的
        // 全景图把内存吃光。
        val budgetSidePx = maxOf(targetWidthPx, maxVisibleHeightPx)
        val maxPixels = minOf(
            (budgetSidePx.toLong() * 2L) * (budgetSidePx.toLong() * 2L),
            HistoryCardImageMaxSourcePixels,
        )
        while (
            (bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight / sampleSize) > maxPixels
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        if (bitmap.width != targetWidthPx) {
            val scaledHeight = (
                bitmap.height.toFloat() * targetWidthPx / bitmap.width.coerceAtLeast(1)
                ).toInt().coerceAtLeast(1)
            val scaled = bitmap.scale(targetWidthPx, scaledHeight)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }

        if (bitmap.height > maxVisibleHeightPx) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, maxVisibleHeightPx)
            if (cropped !== bitmap) {
                bitmap.recycle()
                bitmap = cropped
            }
        }

        thumbnailCache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        var sampleSize = 1
        var longest = maxOf(width, height)
        while (longest / sampleSize > maxSidePx) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun saveImage(fileName: String, bitmap: Bitmap): String? {
        val file = File(imageDir, fileName)
        return runCatching {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            fileName
        }.getOrNull()
    }

    /**
     * 改一条记录的正文（**只改第一个文字块**）。
     *
     * ⚠️ §0.16.17 起**不再被就地编辑条使用**：编辑条改成块编辑器之后，正文/图片一律走
     * [replaceBlocks]（按块顺序整体替换）。这个方法保留有两个原因：
     * ① 它是"只想改第一个文字块、不想动别的块"的那种窄改（卡片上还有些只改正文的入口）；
     * ② 别的地方还有调用点，删掉会把改动面推出去。
     * 新代码要改正文请用 [replaceBlocks] —— 它才表达得出"文字与图片交错的顺序"。
     *
     * 之前仓储**完全没有**改正文的接口（只有 [toggleStar] 这一个窄改），所以设计稿里
     * 「点卡片 → 就地编辑」是新增能力，不是改造。
     *
     * ⚠️ 除了 `text` 字段，还要同步**第一个文字块**：RICH 条目的正文渲染 / 复制走的是
     * `contentBlocks`（`combinedText()` 优先用它），只改 `text` 的话用户看到的是"改了没生效"。
     * 这不是新问题 —— [appendImages] 会把 TEXT 条目升级成 RICH（正文因此被固化成一个文字块），
     * 所以从"给一条纯文字闪念补图"那一刻起就会撞上。其余块（图片、追加块）与顺序原样不动。
     *
     * @return 是否命中并写盘。
     */
    suspend fun updateText(id: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return withContext(Dispatchers.IO) {
            withCrossProcessWrite {
                val entries = readFromDisk()
                val index = entries.indexOfFirst { it.id == id }
                if (index < 0) return@withCrossProcessWrite false
                val entry = entries[index]
                val firstTextIndex = entry.contentBlocks.indexOfFirst { it.kind == ClipboardBlockKind.TEXT }
                val syncedBlocks = if (firstTextIndex < 0) {
                    entry.contentBlocks
                } else {
                    entry.contentBlocks.toMutableList().also {
                        it[firstTextIndex] = it[firstTextIndex].copy(text = trimmed)
                    }
                }
                // 不动 type，避免影响既有渲染与钉屏路径。
                val next = entries.toMutableList().also {
                    it[index] = entry.copy(text = trimmed, contentBlocks = syncedBlocks)
                }
                writeToDisk(next)
                _entries.value = next
                true
            }
        }
    }

    private fun readFromDiskSync(): List<StashEntry> = readFromDisk()

    private fun readFromDisk(): List<StashEntry> {
        if (!indexFile.exists()) {
            indexUnreadable = false
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<StashEntry>>(indexFile.readText())
        }.onSuccess {
            indexUnreadable = false
        }.getOrElse { cause ->
            // ⚠️ 解析失败**绝不能静默当空表**。
            // 所有写路径都是「读全表 → 改 → 整表写回」，一次解析失败 + 任意一次写入
            // 就会把用户的全部暂存覆盖成空表。这里置标志位，让 writeToDisk 拒绝写入；
            // 只要之后有一次成功读取，标志位自动清掉（能自愈，不会永久卡住）。
            indexUnreadable = true
            Log.w(TAG, "stash index unreadable; refusing to overwrite it", cause)
            emptyList()
        }
    }

    private fun writeToDisk(entries: List<StashEntry>) {
        if (indexUnreadable) {
            Log.w(TAG, "skip write: index unreadable, refusing to wipe user data")
            return
        }
        indexFile.writeText(json.encodeToString(entries))
    }

    private fun trimToMax(entries: List<StashEntry>): List<StashEntry> {
        if (entries.size <= MAX_ENTRIES) return entries
        entries.drop(MAX_ENTRIES).forEach { deleteEntryMedia(it) }
        return entries.take(MAX_ENTRIES)
    }

    private companion object {
        const val TAG = "StashRepository"
        const val STASH_DIR_NAME = "stash"
        const val IMAGE_DIR_NAME = "images"
        /** 语音块的文件目录（与图片**分开**，理由见 `audioDir` 的注释）。 */
        const val AUDIO_DIR_NAME = "audio"
        const val INDEX_FILE_NAME = "index.json"
        const val STASH_PREVIEW_MAX_SIDE_PX = 720
        const val MAX_ENTRIES = 200

        /**
         * 卡片缩略图解码的**源图像素上限**（§0.16.20）。
         *
         * 1200 万像素 ≈ 48MB（ARGB_8888）；这是"给竖长截图足够采样余量"和"别被全景图吃光内存"
         * 之间的折中。真正进缓存的还是缩放+裁切后的卡片图（几百 KB 级）。
         */
        const val HistoryCardImageMaxSourcePixels = 12_000_000L
    }
}
