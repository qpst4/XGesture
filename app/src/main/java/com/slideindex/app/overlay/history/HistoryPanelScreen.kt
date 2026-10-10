package com.slideindex.app.overlay.history

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.ui.miuix.MiuixBlurredTopBar
import com.slideindex.app.ui.miuix.miuixAppBarColor
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.StashPanelExternalUi
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.StashReminderScheduler
import com.slideindex.app.stash.StashRichPart
import com.slideindex.app.service.StashComposerImageTrampolineActivity
import com.slideindex.app.service.decodeStashImageFile
import com.slideindex.app.stash.allImageFileNames
import com.slideindex.app.stash.allAudioFileNames
import com.slideindex.app.stash.combinedText
import com.slideindex.app.stash.exportText
import com.slideindex.app.ui.miuix.MiuixSearchField
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

data class HistorySearchBootstrap(
    val tabOrdinal: Int,
    val query: String,
)

/**
 * 图片块里的路径 → **能解码的绝对路径**（§0.16.18）。
 *
 * 图片块的 `path` 有两种来源（见 [DraftBlock.Image] 的 KDoc），这个函数就是那个"唯一的分流点"：
 * - **绝对路径**（新选的图：trampoline 落在 cache 里的临时文件）→ **原样返回**。
 *   不要再去拼暂存夹目录：`File(imageDir, "/data/.../cache/x.jpg")` 会拼出一个不存在的路径，
 *   于是 `BitmapFactory` 解不出来 → 块变空白、保存时图被跳过。**这就是用户报的"加图没成功"的根因。**
 * - **文件名**（条目里已有的图：`ClipboardContentBlock.fileName`）→ 拼暂存夹目录。
 *
 * ⚠️ 为什么用 `existingImageFileNames`（集合成员）而不是 `startsWith("/")` 判绝对路径：
 * 字符串判据在"路径风格变了 / 换了存储目录"时会静默失效，而误判的代价很具体 ——
 * 把"新选的图"当"已有文件名"去拼目录，就又会解不出来。集合成员判断不依赖路径长什么样。
 * （`isAbsolute` 那套只留给"这个路径该不该被删"的删除逻辑，见 `discardCurrent`。）
 *
 * @param repository 为 null（仓储还没起来）时退化成"原样返回"，至少不会拼出更坏的路径。
 */
private fun resolveEditBlockImagePath(
    path: String,
    existingImageFileNames: Set<String>,
    repository: com.slideindex.app.stash.StashRepository?,
): String = if (path in existingImageFileNames) {
    repository?.imageFilePath(path) ?: path
} else {
    path
}

/**
 * 语音块里的路径 → **能播的绝对路径**（§0.16.21）—— 与 [resolveEditBlockImagePath] 逐字同构。
 *
 * 为什么不合并成一个函数：两个集合的语义不同（图片文件名 vs 音频文件名），
 * 合成一个就得同时传两个集合并按"哪个集合命中"猜类型，那是把歧义藏进参数里。
 */
private fun resolveEditBlockAudioPath(
    path: String,
    existingAudioFileNames: Set<String>,
    repository: com.slideindex.app.stash.StashRepository?,
): String = if (path in existingAudioFileNames) {
    repository?.audioFilePath(path) ?: path
} else {
    path
}

/**
 * 把编辑条的一份块序列转成"**只带文件名**"的落盘块序列（§0.16.17 撤销用）。
 *
 * 为什么需要这个转换：撤销要走 `StashRepository.replaceBlockFileNames`（按文件名重建、
 * **不**重新落盘、不覆盖文件），它只认 `ClipboardContentBlock` 的文件名。
 * 而草稿里的图片块有两种路径（见 [DraftBlock.Image] 的 KDoc）：
 * - **暂存夹文件名**（条目里原本就有的图）→ 能按名字还原，收进结果；
 * - **cache 绝对路径**（用户本次新选、还没落盘过的图）→ 没有文件名可还原，跳过
 *   （撤销后正文里不再有它；它的临时文件由调用方保存成功时删掉/由 `pruneOrphanMedia` 收敛）。
 *
 * @param existingImageFileNames 条目**保存前**就有的图片文件名集合：判据用**集合成员**而不是
 *   "路径长得像不像绝对路径"（理由见 [resolveEditBlockImagePath] 的 KDoc）——
 *   只有"确实在这条条目里"的名字才敢交给 `replaceBlockFileNames` 去引用。
 */
private fun blockFileNamesOf(
    blocks: List<DraftBlock>,
    existingImageFileNames: Set<String>,
    existingAudioFileNames: Set<String> = emptySet(),
): List<com.slideindex.app.clipboard.ClipboardContentBlock> =
    blocks.mapNotNull { block ->
        when (block) {
            is DraftBlock.Text -> block.value.trim().takeIf { it.isNotEmpty() }
                ?.let { com.slideindex.app.clipboard.ClipboardContentBlock.text(it) }

            is DraftBlock.Image -> block.path.takeIf { it in existingImageFileNames }
                ?.let { com.slideindex.app.clipboard.ClipboardContentBlock.image(it) }

            // §0.16.21：与图片同一条规则 —— 只有"条目里原本就有的语音文件名"能按名字还原；
            // 本次新录的（cache 绝对路径）没有文件名可还原，撤销后正文里不再有它。
            is DraftBlock.Audio -> block.path.takeIf { it in existingAudioFileNames }
                ?.let { com.slideindex.app.clipboard.ClipboardContentBlock.audio(it, block.durationMs) }
        }
    }

/**
 * 就地图块那枚 **✎** 的落地实现（§0.16.22）。
 *
 * 链路（每一步都必须在这一层，组件里做不了）：
 * ① **挂起面板窗**（[StashPanelExternalUi.suspendForExternalUi]）：面板是比编辑器更高一层的无障碍覆盖窗，
 *    不挂起就会盖在编辑器上面；
 * ② 起 `StashEditImageTrampolineActivity`（overlay 的 Compose 树没有 `ActivityResultRegistryOwner`，
 *    拿不到编辑器保存后的结果 —— 与选图走 trampoline 是同一个理由）；
 * ③ 结果回来**第一件事就是恢复面板**，然后：
 *    - 取消（null）→ **什么都不做**；
 *    - 有结果 → 把 [blockId] 那一块的路径换成新图，并删掉被替换掉的**临时**文件
 *      （`existingImageFileNames` 里的原图**绝不删**：那是用户自己的图，见
 *      [resolveEditBlockImagePath] 的"集合成员"判据）。
 *
 * 为什么替换的判据是**块 id** 而不是"第几张"：同一张图可能在正文里出现两次，
 * 而用户点的是**那一块**上的 ✎（块顺序随时会因为插字/删块变化，用序号一定会改错地方）。
 *
 * ---
 * ## §0.16.23：每一步一行判读日志（tag = [StashEditImageTrampolineActivity.LOG_TAG]）
 *
 * `adb logcat -s StashImageEdit` 应该能按顺序看到：
 * `openImageEditorForBlock …` → `launch trampoline …` → `trampoline onCreate …` →
 * `trampoline launch editor … extra=true` → `editor onCreate …` → `editor save→output …` →
 * `trampoline onActivityResult …` → `trampoline deliver …` → `面板侧回调 delivered …` →
 * `replaceImageBlockPath …` → `resumeAfterExternalUi 调用点=…`。
 * 哪一步断在日志里，就是哪一步的根因。
 *
 * ## 两道防"静默消失"的闸（§0.16.23）
 * 1. **已有一次编辑在途**（`editInFlight`，由本函数**同步**置位）→ 忽略这次点击 + 一次可见提示：
 *    否则会叠起第二个 trampoline，而第二次挂起会被幂等忽略、第一次的结果回来才恢复，
 *    用户看到的就是"点了没反应"。
 * 2. **面板仍处于外部 UI 挂起态** → 同样忽略 + 可见提示。这个状态有两种来源：
 *    别的外部 UI（相册选择器）正在前台（**不能**在这里抢着恢复，那会让面板盖住相册），
 *    或者上一次挂起漏了恢复 —— 后者由 `StashPanelExternalUi` 的 5s 看门狗自愈，
 *    所以两种都不该"静默消失"。
 */
private fun openImageEditorForBlock(
    context: android.content.Context,
    blockId: String,
    blockPath: String,
    existingImageFileNames: Set<String>,
    repository: com.slideindex.app.stash.StashRepository?,
    /** 面板内提示条（复用 `HistoryPanelScreen` 里那条 `.toast`）。 */
    onMessage: (Int) -> Unit,
) {
    val tag = com.slideindex.app.service.StashEditImageTrampolineActivity.LOG_TAG
    val decodedPath = resolveEditBlockImagePath(
        path = blockPath,
        existingImageFileNames = existingImageFileNames,
        repository = repository,
    )
    val sourceFile = File(decodedPath)
    Log.i(
        tag,
        "openImageEditorForBlock blockId=$blockId blockPath=$blockPath 解析后源路径=$decodedPath " +
            "源文件存在=${sourceFile.isFile} 大小=${sourceFile.length()} 在途=${com.slideindex.app.service.StashEditImageTrampolineActivity.editInFlight}",
    )
    // 闸 1：已经有一次编辑在途（用户连点 / 上一次还没回来）→ 直接忽略，别再叠一个 trampoline。
    // ⚠️ 判据是"在途"标志，它由**发起方同步置位**（见 markEditInFlight），所以连点两下也挡得住。
    if (com.slideindex.app.service.StashEditImageTrampolineActivity.editInFlight) {
        Log.w(tag, "openImageEditorForBlock 忽略：已有一次图片编辑在途 blockId=$blockId（不静默：给一次提示）")
        onMessage(R.string.stash_image_edit_busy)
        return
    }
    // 闸 2：面板还停在挂起态 —— 可能是别的外部 UI（相册选择器）正在前台，也可能是上一次 resume 丢了。
    // 两种都**忽略这次点击**（放弃与看门狗已经分别保证"别叠外部 UI"与"最多 5s 自己回来"），
    // 但绝不静默：给一次提示 + 一行日志。
    if (StashPanelExternalUi.isSuspended) {
        Log.w(
            tag,
            "openImageEditorForBlock 忽略：面板正处于外部 UI 挂起态 blockId=$blockId " +
                "（看门狗会在租约过期后自动恢复，见 StashPanelExternalUi）",
        )
        onMessage(R.string.stash_image_edit_busy)
        return
    }
    // 源文件不可读：起编辑器也只会立刻"取消"回来（trampoline 会早退），这里直接给定论 + 可见提示。
    if (!sourceFile.isFile) {
        Log.w(tag, "openImageEditorForBlock 放弃：源图不可读（路径=$decodedPath）blockId=$blockId")
        onMessage(R.string.inspire_image_edit_load_failed)
        return
    }
    // 先同步置"在途"，再挂起、再起 trampoline：这段窗口里第二次点击必须被闸 1 挡住。
    com.slideindex.app.service.StashEditImageTrampolineActivity.markEditInFlight(true)
    StashPanelExternalUi.suspendForExternalUi(caller = "edit-image-block blockId=$blockId")
    com.slideindex.app.service.StashEditImageTrampolineActivity.launch(context, decodedPath) { edited ->
        com.slideindex.app.service.StashEditImageTrampolineActivity.markEditInFlight(false)
        // ⚠️ 恢复必须是**第一件事**（取消也要恢复，否则面板再也弹不出来）。
        StashPanelExternalUi.resumeAfterExternalUi(caller = "编辑器结果回调")
        if (edited.isNullOrBlank()) {
            Log.i(tag, "openImageEditorForBlock 回调 cancelled blockId=$blockId（面板已恢复，块不动）")
            return@launch
        }
        val applied = EditSessionDraft.replaceImageBlockPath(blockId, edited)
        val file = File(edited)
        Log.i(
            tag,
            "replaceImageBlockPath blockId=$blockId found=$applied newPath=$edited " +
                "新文件存在=${file.isFile} 大小=${file.length()}",
        )
        if (applied) {
            // 换掉了才删旧文件，而且只删"本来就不属于这条条目"的那份（cache 临时图）。
            // 删除本身失败无所谓（cache 会被系统清），所以 runCatching 吞掉。
            if (blockPath !in existingImageFileNames) {
                runCatching { File(decodedPath).delete() }
            }
            // 两级保存的用户可见提示：编辑器「保存」只改了**草稿**，还要按编辑浮窗的「保存」才落条目。
            onMessage(R.string.stash_image_edit_replaced_hint)
        } else {
            // 没换上（用户把那一块删了 / 编辑中途关掉了编辑条）：结果文件没人引用，别留在 cache 里。
            Log.w(tag, "replaceImageBlockPath 失败：块 id 对不上（用户删了那块 / 编辑条已换条目）blockId=$blockId")
            runCatching { file.delete() }
        }
    }
}

@Composable
internal fun HistoryPanelScreen(
    gravityEnd: Boolean,
    panelTargetVisible: Boolean,
    panelBlurActive: Boolean = false,
    blurRadiusDp: Int = 57,
    onDismiss: () -> Unit,
    onToggleSide: () -> Unit,
    requestedTabOrdinal: MutableIntState,
    searchBootstrapEpoch: MutableIntState,
    onSearchFocusChanged: (Boolean) -> Unit,
    onRegisterBackInterceptor: ((() -> Boolean)?) -> Unit,
) {
    val savedStateOwner = LocalSavedStateRegistryOwner.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel: HistoryPanelViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = remember(savedStateOwner) { HistoryPanelViewModelFactory(savedStateOwner) },
    )
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val stashRepo = StashAccess.repository
    val clipboardRepo = ClipboardAccess.repository

    val stashEntries by viewModel.stashEntries.collectAsStateWithLifecycle()
    val filteredStashEntries by viewModel.filteredStashEntries.collectAsStateWithLifecycle()
    val stashSearchQuery by viewModel.stashSearchQuery.collectAsStateWithLifecycle()
    /** 剪贴板**完整**条数（SQL COUNT，含当前固定筛选），不是"已加载条数"。 */
    val clipboardViewCount by viewModel.clipboardViewCount.collectAsStateWithLifecycle()
    val clipboardFilter by viewModel.clipboardFilter.collectAsStateWithLifecycle()
    val filteredClipboardEntries by viewModel.filteredClipboardEntries.collectAsStateWithLifecycle()
    val clipboardSearchQuery by viewModel.clipboardSearchQuery.collectAsStateWithLifecycle()
    val clipboardListLoading by viewModel.clipboardListLoading.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val selectedTags by viewModel.selectedTags.collectAsStateWithLifecycle()
    val tagMatchAll by viewModel.tagMatchAll.collectAsStateWithLifecycle()
    val stashMeta by viewModel.stashMeta.collectAsStateWithLifecycle()
    // 标签名 -> 颜色：卡片底部的标签 chip 与筛选行用的是同一份定义。
    val tagColors = remember(availableTags) { availableTags.associate { it.name to it.colorArgb } }
    val haptics = rememberHistoryHaptics()

    val expandedEntryIds by viewModel.expandedEntryIds.collectAsStateWithLifecycle()
    val selectedImageIndices by viewModel.selectedImageIndices.collectAsStateWithLifecycle()
    var searchFocused by remember { mutableStateOf(false) }
    /**
     * 当前页的列表是否已经滑离顶部 —— 决定头部第一行收不收起来（§0.16.5）。
     *
     * 由**两个页签各自的列表**上报（只有"自己这一页是当前页"时才报，否则两页会互相打架），
     * 因为 `LazyListState` 现在还留在各自的 tab body 里（它是 load-more 判断、新条目回顶等
     * 逻辑的锚点，搬出来动的地方比这个功能本身还多）。
     */
    var listScrolled by remember { mutableStateOf(false) }
    /** 深链带 query 进来时把光标请进搜索框（现在搜索框是常驻的，不再有"展开"这回事）。 */
    val searchFocusRequester = remember { FocusRequester() }
    val searchFocusScope = rememberCoroutineScope()

    /* ---- 面板内直接记（设计稿 `.fab` + `.composer`） ---- */
    var composerOpen by remember { mutableStateOf(false) }
    /**
     * 输入条里快速选中的标签（存下时落到新条目）。
     *
     * ⚠️ 草稿（正文 / 标签 / 提醒 / 已选图）放在**进程级单例** [StashComposerDraft] 里（§0.16.14）：
     * 面板是 overlay 窗，切前台 App / 拉起系统相册时会被系统整个摘掉，下次打开是**全新的组合**，
     * `remember` 的草稿那时全丢（用户感受就是"选完图回来草稿没了"）。这里用
     * `by StashComposerDraft.xxx` 直接代理到单例的 `MutableState` —— 下面所有读写点与以前
     * **一字不差**，只是不再随组合生灭。
     *
     * ⚠️ 唯一的例外是正文 [composerBlocks]（§0.16.16）：它是 `SnapshotStateList`、**不是** `MutableState`，
     * 代理不了，只能直接引用（理由见那条自己的注释）。
     */
    var composerTags by StashComposerDraft.tags
    /** 加号弹窗里预设的提醒时间（null = 没设）；存下时写到新条目上（§0.16.9）。 */
    var composerReminderAt by StashComposerDraft.reminderAtMs
    /**
     * 「记一条」弹窗的正文草稿（§0.16.16 起是**有序块序列**：文字块与图片块交错）。
     *
     * ⚠️ 它与下面 [composerImagePaths] / [composerText] 两条的关系，就是
     * `StashComposerDraft` KDoc 里写的"**保留镜像**"：
     * - 改正文**只经 `StashComposerDraft.updateBlocks`**（弹窗把 `onBlocksChange` 直接交给它）；
     * - [composerText] / [composerImagePaths] 是它的**只读投影**，由 `updateBlocks` 同步，
     *   本文件**不要**再写它们（写回去会被下一次同步覆盖，看起来就是"图忽然回来了"）。
     *
     * 之所以不把本文件所有读点都改成按块读：那会把"空内容判断 / 存下 / 关窗清理"三处
     * 一起推倒重写，而本次要的是"图片进正文"；镜像只多一层同步、语义与老 `text` 完全一致（非空文字块 `\n` 连接）。
     *
     * ⚠️ **直接引用，不要用 `by`**：`SnapshotStateList` 实现的是 `StateObject`、**不是** `State` ——
     * `by` 需要 `getValue(Nothing?, KMutableProperty0<*>)`，写 `by` 直接编译不过
     * （`val blocks: SnapshotStateList<…>` 也一样，别把它声明成 `MutableState<…>`）。
     * 好在它本身就**是** snapshot-aware 的：组合里读它会被正常追踪、`add/remove/set` 也会触发重组，
     * 所以这里一把 `val` 就够；**所有**改动都走 `StashComposerDraft.updateBlocks`（含下面的 `resetComposerBlocks`）。
     */
    val composerBlocks = StashComposerDraft.blocks
    /**
     * 加号弹窗里已选图片（trampoline 解码后落在 cache 的临时文件路径，§0.16.12）。
     *
     * ⚠️ §0.16.16 起它是 [composerBlocks] 里图片块的**只读投影** —— 写它没有意义，
     * 图片现在只活在正文里（插入/删除都在块序列上做）。留这个代理是因为"关窗清临时图"
     * 那一处读它最顺，也方便以后按"有没有图"做判断。
     */
    var composerImagePaths by StashComposerDraft.imagePaths
    /**
     * 加号弹窗里已录语音（cache 临时文件路径，§0.16.21）。
     *
     * 与 [composerImagePaths] 完全同一套：它是 [composerBlocks] 里语音块的只读投影，
     * 读它最顺的地方是"关窗时把临时音频删掉"。
     */
    var composerAudioPaths by StashComposerDraft.audioPaths
    /**
     * 提醒时间选择器为谁而开：`entryId = null` = 加号弹窗里"还没存下的那条"，
     * 非 null = 已经在编辑的某条。
     */
    var reminderPicker by remember { mutableStateOf<HistoryReminderPickerTarget?>(null) }
    var composerText by StashComposerDraft.text
    var composerBarHeight by remember { mutableStateOf(0.dp) }
    /**
     * 把正文草稿清回"一个空文字块"（§0.16.16）。
     *
     * 为什么必须走 `StashComposerDraft.updateBlocks` 而不是 `composerBlocks = emptyList()`：
     * ① 空块不变式（"至少一个文字块"）只在那里维护 —— 直接清空会让弹窗没有任何可放光标的地方；
     * ② `text` / `imagePaths` 两条镜像的同步也挂在那儿，绕过它就会留下"块空了、正文串还在"的假状态。
     */
    val resetComposerBlocks: () -> Unit = {
        StashComposerDraft.updateBlocks { listOf(StashComposerDraft.newEmptyTextBlock()) }
    }
    /**
     * 就地编辑条（设计稿 `.editbar`）：非 null 就是打开着，且是打开时的快照
     * （正文 / 标签 / 完成态 / 提醒时间的**原值**）。
     *
     * ⚠️ 它**允许**继续是组合内的 state：它整份都能从数据层重建（`entry` + `stashMeta`），
     * 窗被摘掉之后重开编辑条就是重新快照一次而已。真正必须活得比组合长的是
     * "用户改过的那部分" —— 正文 / 标签 / 已选图，那些在 [EditSessionDraft] 里（§0.16.14），
     * 而且**实时**写进去（编辑条的 `onBlocksChange` / `onTagsChange` —— 正文/图片现在是块序列，
     * 见 [EditSessionDraft.blocks]）。
     *
     * 喂回去的路（窗被系统摘掉 → 组合重建 → 用户再点这条编辑）：
     * ① `openEdit` → [EditSessionDraft.begin]（同 id 保留草稿）+ [EditSessionDraft.seedFromEntry]
     *    （这条还没有草稿时，把条目现有的块按原顺序铺进去，§0.16.17）；
     * ② 正文 / 图片走 [editBlocks]；标签走 [EditSessionDraft.tags]。
     */
    var editTarget by remember { mutableStateOf<HistoryEditTarget?>(null) }
    var editBarHeight by remember { mutableStateOf(0.dp) }
    /**
     * 就地编辑条的**正文块序列**（§0.16.17，与加号弹窗同一套块编辑器）。
     *
     * ⚠️ 与 [composerBlocks] 一样：`SnapshotStateList` **不是** `State`，只能直接引用、不能 `by`。
     * 草稿本体在进程级单例 [EditSessionDraft] 里（窗被系统摘掉也不丢），这里只是接上它。
     */
    val editBlocks = EditSessionDraft.blocks
    /** 标签管理浮窗（§0.16.4 待办 2）：与输入条/编辑条**同一套居中模态壳**。 */
    var tagManagerOpen by remember { mutableStateOf(false) }
    val composerFocusRequester = remember { FocusRequester() }
    // overlay 窗里 WindowInsets.ime 常常是 0，必须用这个（设计稿里的 IME 说明也点了名）。
    val overlayImeBottom = com.slideindex.app.overlay.rememberOverlayImeBottomHeight()
    /**
     * 关面板 / 面板被系统摘掉 → **自动停止语音播放**（§0.16.21）。
     *
     * 单例播放器活得比组合长（这是它能被外部统一叫停的前提），但这也意味着
     * "窗没了"时没人替它收尾 —— 所以在这里补一刀。音频块自己那条 `DisposableEffect`
     * 管的是"切条目 / 收起展开"，这一条管的是"整块面板都没了"。
     */
    DisposableEffect(Unit) {
        onDispose { HistoryAudioPlayback.stop() }
    }
    // 只在闪念页签有 FAB（设计稿 `canAdd = ptab === 'stream'`）；编辑条开着时让位给它。
    val composerVisible = selectedTab == HistoryPanelTab.Stash && editTarget == null
    LaunchedEffect(selectedTab) {
        if (selectedTab != HistoryPanelTab.Stash) {
            composerOpen = false
            // 切页签只是把编辑条藏起来：草稿（正文/标签/已选图）留在 [EditSessionDraft] 里，
            // 切回闪念页再点同一条编辑还能接着改。
            editTarget = null
        }
    }
    // 底部给输入条/编辑条让位（两者互斥，取较大者）。
    val bottomSheetHeight = maxOf(
        if (composerOpen) composerBarHeight else 0.dp,
        if (editTarget != null) editBarHeight else 0.dp,
    )

    val activeSearchQuery = when (selectedTab) {
        HistoryPanelTab.Stash -> stashSearchQuery
        HistoryPanelTab.Clipboard -> clipboardSearchQuery
    }
    val onActiveSearchQueryChange: (String) -> Unit = when (selectedTab) {
        HistoryPanelTab.Stash -> viewModel::setStashSearchQuery
        HistoryPanelTab.Clipboard -> viewModel::setClipboardSearchQuery
    }
    val searchHintResId = when (selectedTab) {
        HistoryPanelTab.Stash -> R.string.stash_search_hint
        HistoryPanelTab.Clipboard -> R.string.clipboard_search_hint
    }
    // 条数（设计稿 `.srchrow .n`）：没搜索也没筛标签时给「N 条 · 今天 M」，否则只给「N 条」。
    //
    // ⚠️ N 一律是**完整条数**，不是"已经加载的条数"（§0.16.4 待办 1）：
    // - 闪念：列表整份在内存里（`StashRepository.MAX_ENTRIES = 200`），所以 `stashEntries.size`
    //   就是库总数，"今天 M"也从**全量**数（筛标签时若从筛选结果里数，数字会跟着筛选跳）；
    // - 剪贴板：分页加载，必须问数据库（`clipboardViewCount` = 当前筛选下的 SQL COUNT），
    //   否则往下滑数字会一直涨（用户实测："条数越滑越大"）。
    //
    // 筛标签 / 搜索时给的是**命中条数**（同样是完整值，不随滚动变）：这时若还显示库总数，
    // 用户筛出 3 条却看到"200 条"，只会更困惑。
    val countLabel = when (selectedTab) {
        HistoryPanelTab.Stash -> {
            val todayCount = stashEntries.count {
                historyDayGroupOf(it.createdAtEpochMs, System.currentTimeMillis()) == HistoryDayGroup.Today
            }
            if (stashSearchQuery.isBlank() && selectedTags.isEmpty()) {
                stringResource(R.string.stash_count_today, stashEntries.size, todayCount)
            } else {
                stringResource(R.string.stash_count, filteredStashEntries.size)
            }
        }
        HistoryPanelTab.Clipboard -> {
            stringResource(R.string.stash_count, clipboardViewCount)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = requestedTabOrdinal.intValue,
        pageCount = { HistoryPanelTab.entries.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setSelectedTab(HistoryPanelTab.entries[pagerState.currentPage])
        // 顺手回写「当前页」：外部用来判断「再触发一次该收起还是切页」（FloatBallStashPanel.toggle）。
        // 不回写的话手势滑动切页后，外部仍以为停在旧页，会继续切页而不是收起。
        requestedTabOrdinal.intValue = pagerState.currentPage
        // 「记住上次页签」那一档读的就是它（进程级，见 StashPanelTabMemory）。
        StashPanelTabMemory.remember(HistoryPanelTab.entries[pagerState.currentPage])
    }
    LaunchedEffect(requestedTabOrdinal.intValue) {
        val target = requestedTabOrdinal.intValue
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage, panelTargetVisible) {
        if (panelTargetVisible && pagerState.settledPage == HistoryPanelTab.Clipboard.ordinal) {
            viewModel.onClipboardTabActivated(context)
        }
    }
    // 搜索框与输入条/编辑条都需要窗口临时可聚焦（overlay 窗默认 FLAG_NOT_FOCUSABLE）。
    LaunchedEffect(searchFocused, composerOpen, editTarget, tagManagerOpen) {
        onSearchFocusChanged(
            searchFocused || composerOpen || editTarget != null || tagManagerOpen,
        )
    }

    LaunchedEffect(searchBootstrapEpoch.intValue, panelTargetVisible) {
        // 退出动画期间旧组合仍在：绝不能 consume，否则会偷走深链 ?q=。
        if (!panelTargetVisible) return@LaunchedEffect
        if (searchBootstrapEpoch.intValue == 0) return@LaunchedEffect
        val pending = StashPanelLaunchState.consumePendingSearch() ?: return@LaunchedEffect
        val tab = HistoryPanelTab.entries.getOrNull(pending.tabOrdinal) ?: HistoryPanelTab.Stash
        // 先落到目标 Tab，再写 query / 展开，避免搜索框短暂绑到错误 Tab 空串并把 VM 写空。
        requestedTabOrdinal.intValue = pending.tabOrdinal
        if (pagerState.currentPage != pending.tabOrdinal) {
            pagerState.scrollToPage(pending.tabOrdinal)
        }
        viewModel.setSelectedTab(tab)
        when (tab) {
            HistoryPanelTab.Stash -> viewModel.setStashSearchQuery(pending.query)
            HistoryPanelTab.Clipboard -> viewModel.setClipboardSearchQuery(pending.query)
        }
        // 深链带 query 进来：等窗口就绪后把光标请进搜索框（和点击搜索框同一条路）。
        onSearchFocusChanged(true)
        searchFocusScope.launch {
            delay(180)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    /*
     * ======================= 「返回层级」注册表（唯一一份）=======================
     *
     * §0.16.24：**系统返回键 / 手势"返回"动作 / 各浮窗的返回入口，全部走这里这一份判定**，
     * 顺序**从上到下、命中即消费并停止**。以后在面板里新增任何"覆盖正文的临时层"，
     * **必须登记到本表**（并加进下面的 `DisposableEffect` keys）—— 否则那层开着时返回会直接
     * 掉到最后一档"收面板"，用户看到的就是"返回把整个面板关了"。
     *
     * | 序 | 层 | 判据 | 关闭动作 | 判定位置 |
     * |---|---|---|---|---|
     * | 0 | **输入法** | `isImeVisible()` | 收键盘，面板/弹窗/展开态都不动 | `OverlayViewBackHandler.dispatchBack()`（**不在本文件**） |
     * | 1 | 提醒时间选择器 `HistoryReminderPicker` | `reminderPicker != null` | `reminderPicker = null` | 下面 `when` 第 1 档 |
     * | 2 | 标签管理浮窗 `HistoryTagManagerModal` | `tagManagerOpen` | `tagManagerOpen = false` | 第 2 档 |
     * | 3 | 就地编辑条 `HistoryPanelEditBar` | `editTarget != null` | `editTarget = null`（**刻意不清草稿**） | 第 3 档 |
     * | 4 | 「记一条」加号弹窗 `HistoryComposerModal` | `composerOpen` | `composerOpen = false` | 第 4 档 |
     * | 5 | **展开的卡片** `HistoryExpandableContentSection` | `expandedEntryIds` 非空 | 收起**最近展开**的那张 | 第 5 档 |
     * | 6 | 常驻搜索框的查询词 | `activeSearchQuery.isNotBlank()` | 清空查询（§"返回键优先收起搜索"） | 第 6 档（`consumeExpandableSearchBack`） |
     * | 7 | 窗口输入态（为输入法聚焦） | `OverlaySidePanelHost.clipboardInputActive` | 交回浏览态 | `OverlaySidePanelHost.handlePanelBack()`（**不在本文件**） |
     * | 8 | **面板本身** | 面板显示中 | `dismiss()` | 同上（最后一档） |
     *
     * ⚠️ 顺序按"**后开的先关**"排：弹窗/编辑条只可能盖在列表之上（点不到卡片），所以展开的卡片
     * 排在这些模态之下；而它们都排在"清搜索词"与"收面板"之上。
     * ⚠️ 顺序 0（键盘优先）**只有一份**，在 `dispatchBack()` 里 —— 本文件与调用侧都**不要**再判 IME。
     * ⚠️ 本表只覆盖**面板窗内**的层。另有**独立窗**不在本表：`HistoryNoteSlotWindow`（长按把手弹的
     * 输入槽）、`HistorySavePeekWindow`（存下后的预览）—— 它们各自有 `OverlayViewBackHandler`，
     * **系统返回**按焦点路由到它们（谁在上面就给谁），而**手势"返回"**走面板入口、不经过它们
     * （两种情况都还没纳入本次分层，真机如撞上再单独处理）。
     */
    DisposableEffect(
        activeSearchQuery,
        selectedTab,
        composerOpen,
        editTarget,
        tagManagerOpen,
        reminderPicker,
        // 第 5 档的判据也要当 key：否则闭包里捕获的是旧集合，卡片展开了按返回却收不回来。
        expandedEntryIds,
    ) {
        onRegisterBackInterceptor {
            // 与上面注册表一一对应（层 → 动作）。
            //
            // §0.16.24 诊断：每一档命中时打**一行**统一格式的决策日志（tag 沿用 `OverlayBack`，
            // 不新建），供真机 `grep 'back decision'` 一眼判读"这次返回被哪一档吃掉/有没有漏给系统"：
            //   `back decision consumed=true branch=interceptor:<档位>`
            // 没打这行 = 本档没命中（继续往下：clipboardInput → dismiss → 或交回系统）。
            when {
                reminderPicker != null -> {
                    reminderPicker = null
                    Log.i(BackDecisionLogTag, "back decision consumed=true branch=interceptor:reminderPicker")
                    true
                }
                tagManagerOpen -> {
                    tagManagerOpen = false
                    Log.i(BackDecisionLogTag, "back decision consumed=true branch=interceptor:tagManager")
                    true
                }
                editTarget != null -> {
                    // ⚠️ **刻意不碰 [EditSessionDraft]**：关掉编辑条 != 放弃这份草稿。
                    // 理由：这条路上"关"的成因太多了 —— 换页签、点空白、把面板收起、
                    // 甚至只是被系统摘掉窗（那时这个 lambda 根本不会被调用），代码分不清
                    // "用户不要了"还是"窗没了"。而**留着的代价只是下次打开这条编辑条看到
                    // 上次改到一半的内容**（数据层没被写过，用户再点保存才生效），
                    // 清掉的代价却是"窗被摘掉回来草稿没了"—— 正是这次要修的毛病。
                    // 清草稿只有两种时机：①保存成功（[EditSessionDraft.clear]）；
                    // ②这条条目本身不在了 —— 删掉它（[EditSessionDraft.clear]），
                    // 或者改去编辑另一条（[EditSessionDraft.begin] 换 entryId 时会作废旧草稿）。
                    editTarget = null
                    Log.i(BackDecisionLogTag, "back decision consumed=true branch=interceptor:editBar")
                    true
                }
                composerOpen -> {
                    composerOpen = false
                    Log.i(BackDecisionLogTag, "back decision consumed=true branch=interceptor:composer")
                    true
                }
                // 第 5 档：展开的卡片。只收**最近展开**的那一张（LIFO）——
                // `expandedEntryIds` 是 `Set`，写入用的是 `Set.plus`（LinkedHashSet，保留插入顺序），
                // 所以 `last()` 就是"最后被展开的那张"。若以后换成无序集合，这里会退化成
                // "收起其中一张"，届时需要改成有序结构（**别**改成"一次全收"：那就不是"只关自己"了）。
                expandedEntryIds.isNotEmpty() -> {
                    viewModel.toggleExpanded(expandedEntryIds.last())
                    Log.i(BackDecisionLogTag, "back decision consumed=true branch=interceptor:expanded")
                    true
                }
                else -> {
                    // 第 6 档：清搜索词。**命中才打日志**（没命中就什么都不打，继续往下走
                    // clipboardInput → dismiss，由那两档打自己的行）。
                    val consumed = consumeExpandableSearchBack(
                        // 搜索框现在是常驻的：只有"有内容"才需要返回键介入（清空查询）。
                        expanded = activeSearchQuery.isNotBlank(),
                        query = activeSearchQuery,
                        onExpandedChange = {},
                        onQueryChange = onActiveSearchQueryChange,
                    )
                    if (consumed) {
                        Log.i(BackDecisionLogTag, "back decision consumed=true branch=interceptor:search")
                    }
                    consumed
                }
            }
        }
        onDispose { onRegisterBackInterceptor(null) }
    }

    val resources = androidx.compose.ui.platform.LocalResources.current
    // 提示条（设计稿 `.toast`）：**自己实现**，不用 miuix 的 Snackbar —— demo 的位置/配色/时长都不一样：
    // 居中对齐**整块屏幕**的底部 104dp、玻璃底、圆角 14、撤销按钮 h30；
    // 而且**有撤销 4200ms、无撤销 2200ms**（miuix Snackbar 是固定 ~几秒）。
    var toast by remember { mutableStateOf<HistoryToastState?>(null) }
    val scheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val tabLabels = listOf(
        stringResource(R.string.stash_panel_tab),
        stringResource(R.string.clipboard_panel_tab),
    )
    val toastJob = remember { mutableStateOf<Job?>(null) }
    val showPanelMessage: (Int) -> Unit = { messageResId ->
        toastJob.value?.cancel()
        toast = HistoryToastState(resources.getString(messageResId), onUndo = null)
        toastJob.value = scope.launch {
            delay(HistoryToastPlainDurationMs)
            toast = null
        }
    }
    /**
     * 提示 + 撤销。
     *
     * 撤销只有**一个槽**（跟设计稿的 `undoFn` 一致：那里的「撤销」永远指"上一步"）——
     * 计划里写的"撤销栈"在 demo 里就是这个单槽，`SnackbarHostState` 也只暴露一个动作按钮。
     */
    val undoLabel = stringResource(R.string.stash_undo)
    // 上一条提示的协程：新动作来时**取消它**，`showSnackbar` 的清理会顺手把旧提示撤掉。
    // 不用 `newestSnackbarData()`：那是 suspend 方法，语义（没有提示时是返回 null 还是挂着等）
    // 不适合放在"每次动作都要调一次"的路径上，取消协程的行为是确定的。
    val messageJob = remember { mutableStateOf<Job?>(null) }
    val showUndoMessage: (Int, (() -> Unit)?) -> Unit = { messageResId, onUndo ->
        val message = resources.getString(messageResId)
        messageJob.value?.cancel()
        toastJob.value?.cancel()
        toast = HistoryToastState(message, onUndo = onUndo)
        messageJob.value = scope.launch {
            delay(if (onUndo != null) HistoryToastUndoDurationMs else HistoryToastPlainDurationMs)
            toast = null
        }
    }
    val metaRepo = StashAccess.metaRepository

    /* ---------------- 标签管理（§0.16.4 待办 2） ---------------- */

    /** 打开标签管理：输入条/编辑条与它互斥（都是"屏幕居中模态"，同时开着会叠在一起）。 */
    val openTagManager: () -> Unit = {
        composerOpen = false
        editTarget = null
        tagManagerOpen = true
    }
    val addTag: (String, Long) -> Unit = { name, colorArgb ->
        scope.launch {
            val added = metaRepo?.addTag(name, colorArgb) ?: false
            // 重名时数据层什么都不做（不会有两枚同名标签），这里给个为什么没反应。
            if (added) haptics.tick() else showPanelMessage(R.string.stash_tag_exists)
        }
    }
    val renameTag: (String, String) -> Unit = { oldName, newName ->
        haptics.confirm()
        scope.launch { metaRepo?.renameTag(oldName, newName) }
    }
    val setTagColor: (String, Long) -> Unit = { name, colorArgb ->
        haptics.tick()
        scope.launch { metaRepo?.setTagColor(name, colorArgb) }
    }
    /**
     * 拖拽排序落盘（§0.16.5 数据层 + §0.16.6 UI）。
     *
     * 浮窗那边是**本地实时换位**、落下时给最终下标；`moveTag` 的语义（移除后插到第 N 位）
     * 与拖拽过程每一步一致，所以不会出现"看着落在第 2 位、落盘跑到别处"。
     */
    val moveTag: (String, Int) -> Unit = { name, targetIndex ->
        haptics.confirm()
        scope.launch { metaRepo?.moveTag(name, targetIndex) }
    }
    /**
     * 删除标签 + 撤销。
     *
     * `removeTag` 会**连带清掉所有条目的绑定**，所以撤销不能只把标签定义加回来 ——
     * 还要把"原来哪些条目挂着它"原样补回去（快照在删除前取）。
     * 「待办」是硬编码关键字（完成态 / `isTodo` / 把手 `pendingTodoCount` 全靠它），这里兜底拒绝。
     */
    val deleteTag: (String) -> Unit = { name ->
        val tag = availableTags.firstOrNull { it.name == name }
        if (tag == null || com.slideindex.app.stash.StashTagEdits.isProtected(name)) {
            showPanelMessage(R.string.stash_tag_protected_hint)
        } else {
            val affected = stashMeta.assignments.filterValues { name in it }.keys.toList()
            haptics.confirm()
            scope.launch {
                metaRepo?.removeTag(name)
                showUndoMessage(R.string.stash_tag_deleted) {
                    scope.launch {
                        metaRepo?.addTag(name, tag.colorArgb)
                        affected.forEach { entryId ->
                            val restored = metaRepo?.tagsOf(entryId).orEmpty() + name
                            metaRepo?.setTags(entryId, restored.distinct())
                        }
                    }
                }
            }
        }
    }

    /**
     * 打开就地编辑条：快照当前正文 / 标签 / 完成态（设计稿 `openEdit`）。
     *
     * §0.16.14：先 [EditSessionDraft.begin] —— 换条目时旧草稿作废（连带删掉它遗留的 cache 临时图），
     * 同一条重复打开**保持**已有草稿（这正是"窗被摘掉再回来，改了一半的正文/勾过的标签/刚选的图
     * 还在"的关键）。
     *
     * §0.16.17：紧接着 [EditSessionDraft.seedFromEntry] 把条目**现有的块**按原顺序铺进编辑器
     * —— 这就是"已有图片的闪念，再次编辑能看到图"的那一步。它内部只在"这条还没有任何内容块"
     * 时才铺，所以同一条重复打开**不会**用条目原值盖掉用户改到一半的草稿。
     */
    val openEdit: (com.slideindex.app.stash.StashEntry) -> Unit = { entry ->
        composerOpen = false
        EditSessionDraft.begin(entry.id)
        EditSessionDraft.seedFromEntry(entry)
        editTarget = HistoryEditTarget(
            entryId = entry.id,
            text = entry.text.orEmpty(),
            tagNames = stashMeta.tagsOf(entry.id),
            done = stashMeta.isDone(entry.id),
            createdAtEpochMs = entry.createdAtEpochMs,
            reminderAtMs = stashMeta.reminderOf(entry.id),
        )
    }

    /**
     * 删除 + 撤销：撤销靠 [com.slideindex.app.stash.StashRepository.restore] 放回**原位置**
     * （`delete` 故意不删图片文件，所以图片条目也能真的恢复）。提醒闹钟跟着一起撤/补。
     */
    val deleteEntry: (com.slideindex.app.stash.StashEntry) -> Unit = { entry ->
        val index = stashEntries.indexOfFirst { it.id == entry.id }
        val reminderAt = stashMeta.reminderOf(entry.id)
        haptics.confirm()
        scope.launch {
            StashReminderScheduler.cancel(appContext, entry.id)
            stashRepo?.delete(entry.id)
            showUndoMessage(R.string.stash_deleted) {
                scope.launch {
                    stashRepo?.restore(entry, index.coerceAtLeast(0))
                    // 撤销删除时把提醒也排回来（meta 里的 reminders 已被 forget 清掉，所以重设）
                    if (reminderAt != null) {
                        metaRepo?.setReminder(entry.id, reminderAt)
                        if (reminderAt > System.currentTimeMillis()) {
                            StashReminderScheduler.schedule(
                                context = appContext,
                                entryId = entry.id,
                                atEpochMs = reminderAt,
                                text = entry.text.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
    val toggleStar: (com.slideindex.app.stash.StashEntry) -> Unit = { entry ->
        haptics.confirm()
        scope.launch {
            stashRepo?.toggleStar(entry.id)
            showUndoMessage(
                if (entry.starred) R.string.stash_star_cleared else R.string.stash_star_marked,
            ) {
                scope.launch { stashRepo?.toggleStar(entry.id) }
            }
        }
    }
    /** 设/清提醒（§0.16.9 起：时间由 [reminderPicker] 选，不再只有"明天 09:00"一档）。 */
    val applyReminder: (Long?) -> Unit = applyReminder@{ at ->
        val target = reminderPicker ?: return@applyReminder
        reminderPicker = null
        val entryId = target.entryId
        if (entryId == null) {
            // 加号弹窗那条还没存下的新条目：先记在本地，存下后再落盘。
            composerReminderAt = at
            return@applyReminder
        }
        val entry = stashEntries.firstOrNull { it.id == entryId }
        val before = stashMeta.reminderOf(entryId)
        haptics.tick()
        scope.launch {
            metaRepo?.setReminder(entryId, at)
            if (at == null) {
                StashReminderScheduler.cancel(appContext, entryId)
            } else {
                StashReminderScheduler.schedule(
                    context = appContext,
                    entryId = entryId,
                    atEpochMs = at,
                    text = entry?.text.orEmpty(),
                )
            }
            // 编辑条的胶囊要立刻反映新时间（它读的是打开时的快照）。
            if (editTarget?.entryId == entryId) editTarget = editTarget?.copy(reminderAtMs = at)
            // 撤销 = 把原来那个时间设回去（注意别递归引用自己：局部 val 不能引用自身）。
            showUndoMessage(if (at == null) R.string.stash_remind_cleared else R.string.stash_remind_set) {
                scope.launch {
                    metaRepo?.setReminder(entryId, before)
                    if (before != null && before > System.currentTimeMillis()) {
                        StashReminderScheduler.schedule(
                            context = appContext,
                            entryId = entryId,
                            atEpochMs = before,
                            text = entry?.text.orEmpty(),
                        )
                    } else {
                        StashReminderScheduler.cancel(appContext, entryId)
                    }
                    if (editTarget?.entryId == entryId) {
                        editTarget = editTarget?.copy(reminderAtMs = before)
                    }
                }
            }
        }
    }
    val setDone: (com.slideindex.app.stash.StashEntry, Boolean) -> Unit = { entry, done ->
        val reminderAt = stashMeta.reminderOf(entry.id)
        haptics.confirm()
        scope.launch {
            if (done) {
                // 设计稿：`if (s.done) s.remind = null` —— 完成即取消提醒，否则到点还响。
                StashReminderScheduler.cancel(appContext, entry.id)
                if (reminderAt != null) metaRepo?.setReminder(entry.id, null)
            }
            metaRepo?.setDone(entry.id, done)
            showUndoMessage(
                if (done) R.string.stash_done_marked else R.string.stash_undone_marked,
            ) {
                scope.launch {
                    metaRepo?.setDone(entry.id, !done)
                    if (done && reminderAt != null && reminderAt > System.currentTimeMillis()) {
                        metaRepo?.setReminder(entry.id, reminderAt)
                        StashReminderScheduler.schedule(
                            context = appContext,
                            entryId = entry.id,
                            atEpochMs = reminderAt,
                            text = entry.text.orEmpty(),
                        )
                    }
                }
            }
        }
    }
    // 存下：设计稿 `addFromComposer()` —— 空内容只提示；成功后清空输入、**清掉搜索与标签筛选**
    // （否则新条目可能被筛掉、看不见），并保持输入条打开以便连着记。
    //
    // §0.16.12：带图片时走 `addRich`（一条多图 —— 条目本身早就支持多图块）。
    // 三个 lambda **先声明后引用**：Kotlin 的局部 lambda 不能向前引用。
    val onComposerSaved: (String, String) -> Unit = { newEntryId, text ->
        haptics.confirm()
        // 快速选的标签落到新条目上（有选才写，避免无谓的元数据写入）。
        if (composerTags.isNotEmpty()) {
            val tagsToApply = composerTags.toList()
            scope.launch { metaRepo?.setTags(newEntryId, tagsToApply) }
        }
        // §0.16.9：加号弹窗里预设的提醒，也在拿到新条目 id 之后落盘 + 排闹钟。
        composerReminderAt?.let { at ->
            scope.launch {
                metaRepo?.setReminder(newEntryId, at)
                StashReminderScheduler.schedule(
                    context = appContext,
                    entryId = newEntryId,
                    atEpochMs = at,
                    text = text,
                )
            }
        }
        showUndoMessage(R.string.stash_saved) {
            scope.launch { stashRepo?.delete(newEntryId) }
        }
    }
    val onComposerDone: (Boolean) -> Unit = { success ->
        if (success) {
            // §0.16.16：清空的判据从"正文串"换成"块序列"—— 回到"一个空文字块"，
            // 镜像（composerText / composerImagePaths）由 `updateBlocks` 一起归零。
            resetComposerBlocks()
            composerTags = emptySet()
            composerReminderAt = null
            // 设计稿 `addFromComposer()` 里 `filter = null; query = ''`：
            // 不清筛选的话新条目可能正好落在筛选之外，用户会以为没存上。
            //
            // ⚠️ 这里**只清查询、不收起搜索框** —— 收起会触发搜索框自己的
            // `focusManager.clearFocus()`，把刚拿到焦点的输入条连输入法一起踢掉。
            viewModel.setStashSearchQuery("")
            viewModel.clearTagFilter()
        } else {
            showPanelMessage(R.string.stash_save_failed)
        }
    }
    val submitComposerText: (String) -> Unit = { value ->
        StashCoordinator.addText(
            value,
            onSaved = { newEntryId -> onComposerSaved(newEntryId, value) },
            onDone = onComposerDone,
        )
    }
    /**
     * 存下"一条新闪念"，**按正文里的块顺序**落成有序块（§0.16.12 多图条目 + §0.16.16 块编辑器）。
     *
     * **块顺序**（这才是用户要的"按顺序"）：[StashCoordinator.addRich] 的 `parts` 就是**有序块**，
     * 落盘的 `contentBlocks` 与它一一对应，卡片展开时也按这个顺序画 —— 正文里是什么顺序，
     * 存下来就是什么顺序（老实现要靠光标把正文切成两半再把图夹进去，现在切块在插入那一刻就做完了）。
     *
     * 与老实现的三个差别：
     * - 正文不再有"整段字符串"可言：非空文字块各自成一个 `StashRichPart.Text`（**trim 后**判空）；
     * - 空文字块**跳过**（它是"图片上下还能点到光标"的落点，不是内容）；
     * - 解码失败的图**跳过**并计数（`decodeStashImageFile` 对损坏/已删的临时文件返回 null）。
     *
     * ⚠️ 这里**不**再自己清块：清块交给 [onComposerDone]（它同时负责标签/提醒/筛选）。
     */
    suspend fun submitComposerBlocks() {
        val snapshot = composerBlocks.toList()
        // 解码离开主线程：一张长边 2048 的图解码不便宜。
        // `failedImages` 是普通计数器：它在**同一个** withContext 块里写、块外才读，
        // 不存在跨线程可见性问题（别为它引入 Atomic）。
        var failedImages = 0
        val parts = withContext(Dispatchers.IO) {
            buildList<StashRichPart> {
                // ⚠️ 按**块顺序**走：文字与图片的交错顺序就是用户看到的正文顺序。
                snapshot.forEach { block ->
                    when (block) {
                        is DraftBlock.Text -> {
                            // ⚠️ 空串/纯空白的段不要加：`addRich` 自己也会丢空文本块并 trim，
                            // 但在这里先判一次能让下面的 `parts.isEmpty()` 判断更准。
                            val text = block.value.trim()
                            if (text.isNotEmpty()) add(StashRichPart.Text(text))
                        }

                        is DraftBlock.Image -> {
                            val bitmap = decodeStashImageFile(block.path)
                            if (bitmap == null) failedImages++ else add(StashRichPart.Image(bitmap))
                        }

                        // §0.16.21：语音**不做解码**（也解不了），只把 cache 里的绝对路径交出去，
                        // 由仓储复制进闪念的音频目录（`persistAudio` 负责唯一文件名 + 不覆盖 + 回滚）。
                        is DraftBlock.Audio -> add(StashRichPart.Audio(block.path, block.durationMs))
                    }
                }
            }
        }
        // §0.16.18：**只要有一张图解不出来就整次不存**（文字也不存），并明确告诉用户。
        //
        // 为什么不做"跳过坏图、把剩下的存下"（老行为）：那正是用户报的"加图没成功"——
        // 图被静默丢掉、条目还是建出来了，用户下次打开才发现图不见了，而且正文位置也乱了。
        // 现在的行为是"要么按你看到的原样存下，要么什么都不动"：
        // 草稿（含那块坏图）留在编辑区，块内显示「加载失败」占位，用户删掉它再存即可。
        if (failedImages > 0) {
            showPanelMessage(R.string.stash_save_failed)
            return
        }
        if (parts.isEmpty()) {
            // 走到这里说明**没有**解码失败的图，那就是"全是空块"（没内容可存）。
            showPanelMessage(R.string.stash_composer_empty)
            return
        }
        val summary = snapshot.filterIsInstance<DraftBlock.Text>()
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        StashCoordinator.addRich(
            parts = parts,
            onSaved = { newEntryId -> onComposerSaved(newEntryId, summary) },
            onDone = { success ->
                onComposerDone(success)
                if (success) {
                    // 图/录音已经拷进仓库了，cache 里这份临时文件可以删（块的清空在 onComposerDone 里）。
                    snapshot.filterIsInstance<DraftBlock.Image>()
                        .forEach { image -> runCatching { File(image.path).delete() } }
                    snapshot.filterIsInstance<DraftBlock.Audio>()
                        .forEach { audio -> runCatching { File(audio.path).delete() } }
                }
            },
        )
    }
    val submitComposer: () -> Unit = {
        // §0.16.16：判空按**块序列**来 —— 有图块/语音块就算有内容（老实现是 `composerImagePaths.isEmpty()`）。
        val hasMedia = composerBlocks.any { it is DraftBlock.Image || it is DraftBlock.Audio }
        val plainText = composerText.trim()
        when {
            plainText.isEmpty() && !hasMedia -> showPanelMessage(R.string.stash_composer_empty)
            // 没有媒体块：走老的单文本落库路径（`addText`），正文语义与以前完全一致。
            !hasMedia -> submitComposerText(plainText)
            else -> scope.launch { submitComposerBlocks() }
        }
    }
    // 重启/更新后 AlarmManager 里的提醒会丢：进面板时补排一次（`StashReminderBootReceiver` 也会在开机时补）。
    // 同一处还负责两件对账（§0.16.15）：把「稍后 10 分钟」写回显示、清掉已过期的提醒。
    /**
     * 提醒的"对账 + 补排"，两处 LaunchedEffect（数据变化 / 面板变可见）共用同一份实现。
     *
     * 顺序不能反（这是这个方法存在的第一理由）：先并回 snooze override，再清过期 ——
     * 反了会把"有效提醒"连 override 一起清掉、救不回来（见 `clearExpiredReminders` 的注释）。
     *
     * ⚠️ 内部的 [stashEntries] / [stashMeta] 是**每次调用时现读**的（它们是 Compose 侧快照，
     * 会随重组更新）：补排要用最新一批条目的正文。
     */
    suspend fun reconcileReminders() {
        // ⚠️ 顺序不能反：先并回 snooze override，再清过期 —— 反了会把"有效提醒"连 override 一起清掉、救不回来。
        val overrides = com.slideindex.app.stash.StashReminderMirror.snoozeOverrides(appContext)
        metaRepo?.mergeSnoozeOverrides(overrides)
        metaRepo?.clearExpiredReminders()
        // 用 `pendingReminders()` 读刚更新过的 store：`stashMeta` 是 Compose 侧快照，拿它会按旧时间再排一遍。
        val reminders = metaRepo?.pendingReminders() ?: stashMeta.reminders
        if (reminders.isEmpty()) return
        val texts = stashEntries.associate { entry ->
            entry.id to (entry.text ?: entry.combinedText())
        }
        StashReminderScheduler.rescheduleAll(appContext, reminders) { entryId ->
            texts[entryId].orEmpty()
        }
    }

    LaunchedEffect(stashEntries.isNotEmpty()) {
        if (stashEntries.isEmpty()) return@LaunchedEffect
        reconcileReminders()
        StashReminderPendingState.refresh(appContext)
    }

    /**
     * 面板**每次变成可见**都跑一次对账（§0.16.15 的第 3 件事）。
     *
     * 为什么必须单独挂一个 effect：上面那条挂在 `stashEntries.isNotEmpty()` 上，
     * 而面板**一直开着**时这个 key 不会变 —— 用户在通知上点了「稍后」（数据层此刻写不进去、
     * 只落在 `StashReminderMirror` 的 snooze override 里），回来一看时间还是旧的；
     * 过期提醒也一直挂着不清理。可见性这个 key 才是"用户现在要看数据了"的正确信号。
     *
     * ⚠️ key 只用 `panelTargetVisible`（一个 Boolean）：它一变只跑一次，不会每帧重跑。
     */
    LaunchedEffect(panelTargetVisible) {
        if (!panelTargetVisible) return@LaunchedEffect
        reconcileReminders()
        StashReminderPendingState.refresh(appContext)
    }

    /**
     * 面板可见期间**慢轮询**指示条状态（§0.16.15）。
     *
     * 为什么还需要它：`hasPending` 的两条判据里，"通知栏里有没有我们那条通知"这件事
     * **不会让 Compose 重组**（它既不来自数据层、也不是 state）—— 提醒在面板开着的时候到点、
     * 通知弹出来，屏幕上唯一的信号就是这条轮询。2 秒是"用户几乎察觉不到延迟"和
     * "别把面板拖慢"之间的折中；一次循环只是一次 `getActiveNotifications` + 一遍提醒表。
     *
     * ⚠️ 轮询体在**协程**里跑（不在组合里）：这里读 `stashMeta` 只取值、不会订阅，所以
     * 不会把面板拖进重组；但**写** state 必须有节制 —— `refresh` 只在值真的变了时才写
     * `hasPending`，`reminderClockMs` 也只在"还有未来提醒"时才推进（见下）。
     *
     * [reminderClockMs] 是同一拍里顺手推进的"卡片用时钟"：卡片那行 ⏰ 是不是该画成灰色
     * 「已提醒」取决于"现在有没有过点"，而过点这件事**不会改任何数据**（`clearExpiredReminders`
     * 要等下一次对账），所以必须有个东西让那一行重组。**只在"还有一条未来的提醒"时才推进**：
     * 没有提醒、或提醒全都已经过点时一次都不写 —— 那两种情况下这一行不需要跟着时钟走。
     */
    var reminderClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(panelTargetVisible) {
        if (!panelTargetVisible) return@LaunchedEffect
        while (true) {
            delay(ReminderPendingPollIntervalMs)
            StashReminderPendingState.refresh(appContext)
            if (stashMeta.reminders.values.any { it > reminderClockMs }) {
                reminderClockMs = System.currentTimeMillis()
            }
        }
    }

    // ⚠️ 宽度用**布局约束**（maxWidth），不用 `LocalWindowInfo.containerSize`：
    // 真机上后者会返回旋转过的显示尺寸（实测 2340x1080 vs 窗口 1080x2340），
    // 算出来比窗口还宽 → 面板被裁成满屏。见 `panelWidthOf` 的注释。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = if (gravityEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // 设计稿是 26dp（--r-2xl），且只圆内侧。
        val panelShape = if (gravityEnd) {
            RoundedCornerShape(
                topStart = HistoryPanelCornerRadius,
                bottomStart = HistoryPanelCornerRadius,
            )
        } else {
            RoundedCornerShape(
                topEnd = HistoryPanelCornerRadius,
                bottomEnd = HistoryPanelCornerRadius,
            )
        }
        val isLocalBlurActive = panelBlurActive
        val density = LocalDensity.current
        val cornerPx = with(density) { HistoryPanelCornerRadius.toPx() }
        val blurRadiusPx = with(density) { blurRadiusDp.toFloat().dp.toPx() }.roundToInt()
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val frostedTint = if (isDark) 0x661C1C1E.toInt() else 0x66F5F5F7.toInt()
        // 跟手拉出的进度（0..1）：拖动时贴手指、松手后弹簧收尾。
        // 收起动画播完才叫宿主关窗（见 rememberPanelRevealProgress）。
        val revealProgress = rememberPanelRevealProgress(
            panelTargetVisible = panelTargetVisible,
            onRetracted = onDismiss,
        )
        val theme = historyTheme()
        // 面板宽度 = 窗口宽 × 78%（窗口又回到满屏了，见 `OverlayPanelLayoutParams`）。
        val panelWidth = panelWidthOf(maxWidth)
        val panelWidthPx = with(density) { panelWidth.toPx() }
        // 遮罩：整屏压暗 20%，跟手时按进度淡入（拖动期间不压暗，避免"手指刚动整屏先暗"）。
        val scrimAlpha by animateFloatAsState(
            targetValue = if (HistoryPanelReveal.dragging) 0f else theme.scrim.alpha * revealProgress,
            animationSpec = tween(HistoryDurations.d2),
            label = "panelScrim",
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(theme.scrim.copy(alpha = scrimAlpha)),
        )

        // 提示条挂在**全屏那一层**：设计稿 `.toast { left:50%; bottom:104px }` 是相对整块手机屏居中的，
        // 不是相对面板 —— 面板只有 78% 宽，放里面就会贴着面板左缘。
        Box(
            modifier = Modifier
                // 必须压在面板之上：它和面板是兄弟节点，只靠声明顺序会被面板盖住（用户实测）。
                .zIndex(2f)
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = HistoryToastBottomPadding +
                        if (bottomSheetHeight > 0.dp) bottomSheetHeight else 0.dp,
                ),
        ) {
            HistoryToast(
                state = toast,
                onUndo = { toast = null },
                onSwipeDismiss = {
                    toast = null
                    messageJob.value?.cancel()
                    toastJob.value?.cancel()
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(panelWidth)
                // ⚠️ 位移必须放在 background/border **之前**：`graphicsLayer` 只包住它之后的绘制，
                // 放在后面面板底色会留在最终位置不动，只有内容跟着手指跑（踩过）。
                .graphicsLayer {
                    translationX = if (HistoryPanelReveal.dragSession) {
                        HistoryPanelReveal.offsetPx(
                            progress = revealProgress,
                            spanPx = panelWidthPx,
                            gravityEnd = gravityEnd,
                        )
                    } else {
                        0f
                    }
                }
                // 设计稿 `.g`：玻璃底 + 白描边 + 上下内高光 + 两层投影。
                .shadow(
                    elevation = 18.dp,
                    shape = panelShape,
                    ambientColor = theme.glassShadow,
                    spotColor = theme.glassShadow,
                )
                .clip(panelShape)
                // 设计稿 `[data-glass="off"] .g { background: var(--g-solid) }`：
                // 毛玻璃关掉时必须换成**不透明**底，否则底下的 App 会清清楚楚透出来。
                .background(theme.glassFill)
                .border(width = 1.dp, color = theme.glassBorder, shape = panelShape)
                // `.g::before`：左上一团白色高光（径向渐变）+ 顶部一层竖向高光。
                .drawWithContent {
                    drawContent()
                    val gloss = Color.White.copy(alpha = if (theme.isDark) 0.13f else 0.55f)
                    // `.g::before` 第一条：`radial-gradient(130% 86% at 16% -22%, g-gloss, transparent 56%)`
                    // ⚠️ 那个 **56% 的透明停靠点不能省**：Compose 的 radialGradient 只给两个颜色时是
                    // 从圆心线性淡到半径，白纱会罩满整块面板，**所有文字都会发灰**（这就是"该黑的地方发灰"的根因）。
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to gloss,
                                0.56f to Color.Transparent,
                            ),
                            center = Offset(size.width * 0.16f, -size.height * 0.22f),
                            radius = size.width * 1.30f,
                        ),
                    )
                    // 第二条：`linear-gradient(180deg, g-gloss 55%, transparent 38%)` —— 顶部一层很淡的竖向高光。
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to gloss.copy(alpha = gloss.alpha * 0.55f),
                                0.38f to Color.Transparent,
                            ),
                        ),
                    )
                }

                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            // 自绘磨砂罩（App 层 RenderEffect）：窗口稳定时是真模糊，拖动中只剩 tint（那层"雾"）——
            // 这是用户认可的那一版观感，别再用系统模糊替换（系统模糊那版已被打回，见 §0.16.3）。
            if (isLocalBlurActive) {
                com.slideindex.app.overlay.LocalFrostedGlassBackdrop(
                    modifier = Modifier.matchParentSize(),
                    cornerRadiusPx = cornerPx,
                    blurRadiusPx = blurRadiusPx,
                    tintColor = frostedTint,
                    enabled = true,
                )
            }
            val density = LocalDensity.current
            // 设计稿的头部是**独立的 flex 行**（`.head { flex: 0 0 auto }`），列表在它下面滚动，
            // 不是"内容从顶栏下面穿过去"。所以这里用 Column，不做顶栏覆盖，也就不需要
            // 顶栏毛玻璃（那层毛玻璃是 App 自己的旧做法，见 plan §0.16）。
            Column(modifier = Modifier.fillMaxSize()) {
                HistoryPanelHeader(
                    tabLabels = tabLabels,
                    selectedTabIndex = selectedTab.ordinal,
                    onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    searchQuery = activeSearchQuery,
                    onSearchQueryChange = onActiveSearchQueryChange,
                    searchHint = stringResource(searchHintResId),
                    searchFocusRequester = searchFocusRequester,
                    countLabel = countLabel,
                    onSearchFocusChanged = { searchFocused = it },
                    // ⚠️ 不走"计数器 + LaunchedEffect"：真机上那个 effect 压根没被触发（日志实测），
                    // 直接在回调里做「让窗口可聚焦 → 延时抢焦点」，和输入条那条路一样。
                    // 只负责把窗口切成可聚焦；抢焦点由搜索框自己在 `LaunchedEffect(editorEnabled)` 里做。
                    onSearchRequestFocus = { onSearchFocusChanged(true) },
                    onDismiss = onDismiss,
                    // 收起条件：滑离顶部 **且** 没在搜索（有查询词时那颗词得一直看得见，
                    // 输入框聚焦时更不能收 —— 收起会把输入框从组合里摘掉，输入法会当场掉）。
                    collapsed = listScrolled && activeSearchQuery.isBlank() && !searchFocused,
                    chipRow = {
                        // 两个页签同一位置的一行胶囊：闪念 = 标签（末尾 ＋ 进管理），剪贴板 = 固定筛选。
                        when (selectedTab) {
                            HistoryPanelTab.Stash -> HistoryTagChips(
                                tags = availableTags,
                                selectedTags = selectedTags,
                                matchAll = tagMatchAll,
                                onTagToggled = viewModel::toggleTagFilter,
                                onMatchAllChange = viewModel::setTagMatchAll,
                                onClearSelection = viewModel::clearTagFilter,
                                onManageTags = openTagManager,
                            )
                            HistoryPanelTab.Clipboard -> HistoryClipboardFilterChips(
                                selected = clipboardFilter,
                                onSelected = viewModel::setClipboardFilter,
                            )
                        }
                    },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    beyondViewportPageCount = 0,
                ) { page ->
                    // 换页动效（设计稿 `.scroll.tabin-r/.tabin-l`）：位移 16dp + 淡入。
                    // 翻页比例在 graphicsLayer 的块里读 —— 那是「绘制阶段」的读取，
                    // 只会重绘不会重组（拿它当普通值读会每帧重组整页）。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pageOffset = (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                                alpha = (1f - abs(pageOffset) * PAGE_SWITCH_FADE).coerceIn(
                                    PAGE_SWITCH_MIN_ALPHA,
                                    1f,
                                )
                                translationX = -pageOffset * PAGE_SWITCH_SLIDE.toPx()
                            },
                    ) {
                        when (HistoryPanelTab.entries[page]) {
                            HistoryPanelTab.Stash -> HistoryStashTabBody(
                                allEntries = stashEntries,
                                filteredEntries = filteredStashEntries,
                                searchQuery = stashSearchQuery,
                                selectedTags = selectedTags,
                                tagMatchAll = tagMatchAll,
                                meta = stashMeta,
                                nowMs = reminderClockMs,
                                tagColors = tagColors,
                                haptics = haptics,
                                isActive = selectedTab == HistoryPanelTab.Stash,
                                panelBlurActive = panelBlurActive,
                                listBottomPadding = bottomSheetHeight,
                                repo = stashRepo,
                                expandedEntryIds = expandedEntryIds,
                                selectedImageIndices = selectedImageIndices,
                                onToggleExpanded = viewModel::toggleExpanded,
                                onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                                onSetDone = setDone,
                                onToggleStar = toggleStar,
                                onDeleteEntry = deleteEntry,
                                onEditEntry = openEdit,
                                onClearSearch = { viewModel.setStashSearchQuery("") },
                                onClearTagFilter = viewModel::clearTagFilter,
                                onShowMessage = showPanelMessage,
                                onListScrolledChange = { listScrolled = it },
                            )
                            HistoryPanelTab.Clipboard -> HistoryClipboardTabBody(
                                totalCount = clipboardViewCount,
                                filter = clipboardFilter,
                                filteredEntries = filteredClipboardEntries,
                                searchQuery = clipboardSearchQuery,
                                haptics = haptics,
                                isActive = selectedTab == HistoryPanelTab.Clipboard,
                                panelBlurActive = panelBlurActive,
                                loading = clipboardListLoading,
                                clipboardRepo = clipboardRepo,
                                expandedEntryIds = expandedEntryIds,
                                selectedImageIndices = selectedImageIndices,
                                onToggleExpanded = viewModel::toggleExpanded,
                                onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                                onEnsureLoaded = viewModel::ensureClipboardPagesLoaded,
                                onLoadMore = viewModel::loadMoreClipboard,
                                onShowMessage = showPanelMessage,
                                onListScrolledChange = { listScrolled = it },
                            )
                        }
                    }
                }
            }
            // 浮层（提示条 / 输入条 / 编辑条）叠在列表之上；它们要 BottomCenter 对齐，
            // 所以必须待在 BoxScope 里（Column 没有 align）。
            Box(modifier = Modifier.fillMaxSize()) {
                // 设计稿的提示条是**相对整屏**底部 104dp 居中（不是相对面板）；
                // 输入条/编辑条打开时再顶上去，免得被盖住。
                // 面板内只留 FAB（右下角）。输入条与编辑条都搬到"全屏居中模态层"了
                // （用户要求：别局限在面板里）。
                HistoryComposerFabSlot(
                    visible = composerVisible,
                    open = composerOpen,
                    onOpenChange = {
                        composerOpen = it
                        if (!it) {
                            composerTags = emptySet()
                            composerReminderAt = null
                            // 关掉弹窗时把**没存下**的正文草稿一起丢掉（老实现只丢图、留正文，
                            // §0.16.16 起正文里就有图块了，而图块的 cache 文件正要被删掉 ——
                            // 只留文字会是"半份草稿"，见 `StashComposerDraft` 的 KDoc）：
                            // 临时图先删文件（cache 里不留垃圾），再经 `updateBlocks` 把块序列
                            // 与镜像（composerText / composerImagePaths）一起归零。
                            //
                            // ⚠️ 顺序不能反：`composerImagePaths` / `composerAudioPaths` 是 `composerBlocks`
                            // 的投影，先清块就再也拿不到那批路径，文件会留在 cache 里。
                            composerImagePaths.forEach { path -> runCatching { File(path).delete() } }
                            composerAudioPaths.forEach { path -> runCatching { File(path).delete() } }
                            resetComposerBlocks()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        // ---------------- 全屏居中模态层 ----------------
        // 窗口是满屏的，所以输入条 / 编辑条 / 标签管理都能真正居中在**屏幕**上（而不是面板那 78% 里）；
        // 底下那层压暗同样是满屏的，点空白即关闭当前浮窗。
        val modalOpen = composerOpen || editTarget != null || tagManagerOpen || reminderPicker != null
        if (modalOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(3f)
                    .background(theme.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // 点空白 = 主动取消编辑：**保留** [EditSessionDraft]（理由见返回键那条路
                        // 的注释：分不清"不要了"和"窗被摘掉"，而保留的代价只是下次接着改）。
                        when {
                            reminderPicker != null -> reminderPicker = null
                            tagManagerOpen -> tagManagerOpen = false
                            editTarget != null -> editTarget = null
                            else -> composerOpen = false
                        }
                    },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(4f)
                .width(maxWidth * 0.96f)
                .widthIn(max = 720.dp),
        ) {
            // 提醒选择器开着时，其它几块浮窗先不渲染：它们全是"屏幕居中卡片"，叠在一起会糊成一片。
            val showPanelLayers = reminderPicker == null
            HistoryTagManagerModal(
                open = tagManagerOpen && showPanelLayers,
                tags = availableTags,
                imeBottom = overlayImeBottom,
                haptics = haptics,
                onDismiss = { tagManagerOpen = false },
                onAdd = addTag,
                onRename = renameTag,
                onSetColor = setTagColor,
                onDelete = deleteTag,
                onMove = moveTag,
                modifier = Modifier.fillMaxWidth(),
            )
            HistoryComposerModal(
                open = composerOpen && showPanelLayers,
                // §0.16.16：正文按**块序列**进出 —— 弹窗内部的插入/切块/退格删图都通过
                // `onBlocksChange` 落回单例的 `updateBlocks`（它同时刷新 text / imagePaths 两条镜像）。
                blocks = composerBlocks,
                onBlocksChange = { transform -> StashComposerDraft.updateBlocks(transform) },
                onSubmit = submitComposer,
                onVoiceError = showPanelMessage,
                availableTags = availableTags,
                selectedTags = composerTags,
                onToggleTag = { name ->
                    composerTags = if (name in composerTags) composerTags - name else composerTags + name
                },
                reminderAtMs = composerReminderAt,
                onReminderClick = {
                    reminderPicker = HistoryReminderPickerTarget(
                        entryId = null,
                        initialAtMs = composerReminderAt,
                    )
                },
                onAddImage = { onPicked ->
                    // §0.16.12：overlay 里不能直接拉系统选图，走中转 Activity（回来的是本地文件路径）。
                    // §0.16.14：先把面板窗挂起（它是无障碍覆盖层，不挂起会盖在相册上面），
                    // 回调里**第一件事**就是恢复 —— 取消（picked 为空）也要恢复。
                    //
                    // §0.16.16：**不在这里插块** —— 路径原样交回弹窗，由它按"当前光标"切块插入
                    // （光标/焦点只活在弹窗里，这里插只能追加到末尾，那就退回老行为了）。
                    StashPanelExternalUi.suspendForExternalUi(caller = "composer-add-image")
                    StashComposerImageTrampolineActivity.launch(appContext) { picked ->
                        StashPanelExternalUi.resumeAfterExternalUi(caller = "选图回调（弹窗）")
                        if (picked.isNotEmpty()) onPicked(picked.distinct())
                    }
                },
                onRemoveImage = { path ->
                    // 块的增删已经由弹窗做完了（这里是"删了之后要干什么"）：只负责把 cache 里
                    // 那份临时文件删掉，别留垃圾。`composerImagePaths` 是块的投影，不用再手动减。
                    runCatching { File(path).delete() }
                },
                onRemoveAudio = { path ->
                    // §0.16.21：与删图同一条 —— 弹窗里语音块存的永远是 cache 临时文件的绝对路径
                    // （条目里已有的语音只会出现在编辑条那条路上），所以这里直接删。
                    runCatching { File(path).delete() }
                },
                imeBottom = overlayImeBottom,
                focusRequester = composerFocusRequester,
                onBarHeightChanged = { composerBarHeight = it },
            )                // 就地编辑条（设计稿 `.editbar`）：改正文 / 改标签 / 追加 / 完成 / 删除。
                if (showPanelLayers) editTarget?.let { target ->
                    /**
                     * 草稿只有属于**当前这条**时才算数：`EditSessionDraft` 是进程级单例，
                     * 万一残留的是别的条目的（理论上 `begin` 已经处理过，这里再兜一道），
                     * 拿它当初始值就会把别人的正文/标签灌进这一条 —— 那是数据串条，比丢草稿严重得多。
                     */
                    val draft = EditSessionDraft.takeIf { it.entryId.value == target.entryId }
                    /**
                     * 喂给编辑条的**初始值**：有草稿用草稿，没有就用条目原值。
                     *
                     * ⚠️ 只能走"换一个 target 实例"这条缝 —— `HistoryPanelEditBar` 不允许改，
                     * 它的正文/标签是内部的 `remember(target.entryId)`（只在 entryId 变时重新取初值），
                     * 所以这里的 copy **必须保持 entryId 不变**：否则每打一个字都会重组出一个新
                     * entryId 快照，把输入框里的内容整段冲掉（比丢草稿还糟）。
                     *
                     * 这也正是"窗被系统摘掉后重开"能自愈的原因：组合重建 = 编辑条重新 `remember`，
                     * 它就会把草稿里那份正文/标签读回去。
                     */
                    val barTarget = target.copy(
                        text = draft?.text?.value ?: target.text,
                        tagNames = draft?.tags?.value?.toList() ?: target.tagNames,
                    )
                    /**
                     * 这条条目**原本就有**的图片文件名（§0.16.18）。
                     *
                     * ⚠️ **必须声明在这一层**（`editTarget?.let` 里，`HistoryPanelEditBar` 调用的外面）：
                     * 它下面有三个读者 —— 保存链的 decode 分流、保存链的撤销还原、以及块编辑器的
                     * `resolveImagePath` / `existingImageFileNames` 两个参数。声明在 `onSave` 回调**里面**
                     * 只有回调自己能看见，外面那两个参数就"找不到符号"（这正是上次那一处编译错）。
                     *
                     * 取值 = 条目**当前**的图片文件名（`allImageFileNames()` 按 `contentBlocks` 顺序）。
                     * 用户本次新选的图这时只是 cache 绝对路径、**不在**这个集合里 —— 这正是分流的依据。
                     */
                    val editEntryExistingImageNames = stashEntries
                        .firstOrNull { it.id == target.entryId }
                        ?.allImageFileNames()
                        .orEmpty()
                        .toSet()
                    /**
                     * 这条条目**原本就有**的语音文件名（§0.16.21）—— 与
                     * [editEntryExistingImageNames] 逐字同构：用户本次新录的语音这时只是
                     * cache 绝对路径、**不在**这个集合里，这正是"删块时要不要删文件"的分流依据。
                     */
                    val editEntryExistingAudioNames = stashEntries
                        .firstOrNull { it.id == target.entryId }
                        ?.allAudioFileNames()
                        .orEmpty()
                        .toSet()
                    HistoryPanelEditBar(
                        target = barTarget,
                        availableTags = availableTags,
                        imeBottom = overlayImeBottom,
                        blurActive = panelBlurActive,
                        /**
                         * §0.16.17：正文是**块序列**（唯一真相在 [EditSessionDraft.blocks]，这里直接引用）。
                         *
                         * 老实现是"正文串走 `onTextChange` 实时进草稿"—— 现在块编辑器把整份新块序列
                         * 交回来（打字 / 插图 / 删图 / 语音都走它），落到草稿的那一步就是 `updateBlocks`
                         * （它同时刷新 `text` / `imagePaths` 两条投影）。
                         */
                        blocks = editBlocks,
                        onBlocksChange = { transform -> EditSessionDraft.updateBlocks(transform) },
                        // §0.16.22：图片块角落的 ✎ → 内置图片编辑器 → 保存后**换掉这一块的那张图**。
                        // 块路径的两种含义（cache 绝对路径 / 条目里的文件名）由 openImageEditorForBlock
                        // 按 existingImageFileNames 分流，与保存链用的是同一份集合。
                        onEditImage = { blockId ->
                            val block = editBlocks.firstOrNull { it.id == blockId } as? DraftBlock.Image
                            if (block != null) {
                                openImageEditorForBlock(
                                    context = appContext,
                                    blockId = blockId,
                                    blockPath = block.path,
                                    existingImageFileNames = editEntryExistingImageNames,
                                    repository = stashRepo,
                                    onMessage = showPanelMessage,
                                )
                            } else {
                                // 静默返回是上一轮的坑：块已经被删掉时也要在日志里留下定论（§0.16.23）。
                                Log.w(
                                    com.slideindex.app.service.StashEditImageTrampolineActivity.LOG_TAG,
                                    "✎ 忽略：块已不在草稿里 blockId=$blockId（不是图片块）",
                                )
                            }
                        },
                        onTagsChange = { value -> EditSessionDraft.tags.value = value },
                        onSave = { tags, newImagePaths ->
                            /**
                             * §0.16.17：保存**按块顺序**整体写回。
                             *
                             * 为什么不再走"`updateText` + `appendImages`"那两刀：
                             * ① `updateText` 只会改**第一个**文字块，用户在中间插的图、拆开的段落全对不上；
                             * ② `appendImages` 只会把图**追加到末尾**，而现在图是插在正文中间的。
                             * 所以这里把 `editBlocks` 拍成快照 → 解码新图 → `replaceBlocks` 一次写完。
                             *
                             * ⚠️ 快照必须**在这里**取（`scope.launch` 之外）：下面 `onDone` 里会
                             * `EditSessionDraft.clear()`，取晚了就读到空的了。
                             */
                            val beforeBlocks = editBlocks.toList()
                            val beforeTags = stashMeta.tagsOf(target.entryId)
                            // ⚠️ "哪些是已有图片文件名"不在这里算：它下面的**块编辑器参数**
                            // （`resolveImagePath` / `existingImageFileNames`）也要用同一个值，
                            // 所以声明在 `editTarget?.let` 那一层（见上面的 KDoc）。
                            scope.launch {
                                // §0.16.18：图片解码失败要**明确失败**，不能静默把图丢掉。
                                // 与弹窗那条链同一个判据：解不出来就整次不存，草稿（含那块坏图）留着，
                                // 块内也会显示「加载失败」占位 —— 用户删掉它再存即可。
                                var failedImages = 0
                                val parts = withContext(Dispatchers.IO) {
                                    beforeBlocks.mapNotNull { block ->
                                        when (block) {
                                            is DraftBlock.Text -> block.value.trim()
                                                .takeIf { it.isNotEmpty() }
                                                ?.let { StashRichPart.Text(it) }

                                            is DraftBlock.Image -> {
                                                // 已有的图（暂存夹**文件名**）与本次新选的图（cache **绝对路径**）
                                                // 走同一套解析 + decode：解析规则在下面 `resolveImagePath` 里
                                                // （绝对路径直通，文件名才拼暂存夹目录）。
                                                val path = resolveEditBlockImagePath(
                                                    path = block.path,
                                                    existingImageFileNames = editEntryExistingImageNames,
                                                    repository = stashRepo,
                                                )
                                                val bitmap = decodeStashImageFile(path)
                                                if (bitmap == null) {
                                                    failedImages++
                                                    null
                                                } else {
                                                    StashRichPart.Image(bitmap)
                                                }
                                            }

                                            // §0.16.21：语音不做解码，只交路径 —— 已有的传**文件名**
                                            // （仓储按名字复用、不复制），新录的传 cache 绝对路径
                                            // （仓储复制进音频目录）。
                                            is DraftBlock.Audio ->
                                                block.path.takeIf { it.isNotBlank() }
                                                    ?.let { StashRichPart.Audio(it, block.durationMs) }
                                        }
                                    }
                                }
                                if (failedImages > 0) {
                                    // 一张都没解出来、或者有坏图：不谎称成功，草稿留着可重试。
                                    showPanelMessage(R.string.stash_save_failed)
                                    return@launch
                                }
                                if (parts.isEmpty()) {
                                    // 没有坏图却一个块都没有 → 正文是空的，与弹窗同一条提示。
                                    showPanelMessage(R.string.stash_composer_empty)
                                    return@launch
                                }
                                val beforeBlockFiles = blockFileNamesOf(
                                    beforeBlocks,
                                    existingImageFileNames = editEntryExistingImageNames,
                                    existingAudioFileNames = editEntryExistingAudioNames,
                                )
                                StashCoordinator.replaceBlocks(target.entryId, parts) { ok ->
                                    if (ok) {
                                        // ⚠️ `metaRepo?.setTags` 是 **suspend fun**，而这个 `onDone`
                                        // （`StashCoordinator.replaceBlocks` 的回调）是**普通** lambda：
                                        // 直接调会报 "Suspension functions can only be called within
                                        // coroutine body"。所以这里**必须**再包一层 `scope.launch`
                                        // （下面 `showUndoMessage` 里那条 undo 也是同一个道理）。
                                        //
                                        // 只用 `scope.launch` 包住这一句、不把整段挪进去：标签写库是"随后就到"
                                        // 的副作用，而 `editTarget = null`（收起编辑条）/ 触感 / 清草稿
                                        // 必须**当场**发生，晚一帧会让用户看到编辑条闪一下、甚至被重组刷回去。
                                        scope.launch { metaRepo?.setTags(target.entryId, tags) }
                                        editTarget = null
                                        haptics.confirm()
                                        // 图/录音已经拷进仓库了：cache 里那批新选的临时文件可以删，草稿整份清掉。
                                        EditSessionDraft.clear()
                                        newImagePaths.forEach { path -> runCatching { File(path).delete() } }
                                        // §0.16.21：本次**新录**的语音（cache 绝对路径）同样删掉。
                                        // 判据用 `isAbsolute`（与 `EditSessionDraft.discardCurrent` 一致）：
                                        // 已有语音在块里存的是文件名，绝不能拿去 delete。
                                        beforeBlocks.filterIsInstance<DraftBlock.Audio>()
                                            .map { it.path }
                                            .filter { File(it).isAbsolute }
                                            .forEach { path -> runCatching { File(path).delete() } }
                                        showUndoMessage(R.string.stash_edit_saved) {
                                            scope.launch {
                                                // 撤销：把**保存前**的块序列放回去。
                                                //
                                                // ⚠️ 走 `replaceBlockFileNames`（只按文件名重建、**不**重新落盘）
                                                // 而不是 `replaceBlocks`：后者会把那几张图重新编码成一批新文件，
                                                // 撤销一次就多一堆孤儿文件，而且原来的文件还留着。
                                                // 代价是"保存前刚**新选**、还没落盘过"的图/录音没有文件名可还原 ——
                                                // 那种块在 `blockFileNamesOf` 里被跳过（撤销后它们不在正文里了，
                                                // 但仍是正常的仓库文件，由启动时的 `pruneOrphanMedia` 收敛）。
                                                stashRepo?.replaceBlockFileNames(
                                                    target.entryId,
                                                    beforeBlockFiles,
                                                )
                                                metaRepo?.setTags(target.entryId, beforeTags)
                                            }
                                        }
                                    } else {
                                        // 失败：草稿**留着**（临时图也不删），用户可以再点一次保存重试。
                                        showPanelMessage(R.string.stash_save_failed)
                                    }
                                }
                            }
                        },
                        onAppend = { appended ->
                            scope.launch {
                                metaRepo?.appendText(target.entryId, appended)
                                haptics.tick()
                                showUndoMessage(R.string.stash_appended) {
                                    scope.launch { metaRepo?.removeLastAppend(target.entryId) }
                                }
                            }
                        },
                        onToggleDone = {
                            val next = !target.done
                            editTarget = target.copy(done = next)
                            stashEntries.firstOrNull { it.id == target.entryId }
                                ?.let { setDone(it, next) }
                        },
                        onToggleReminder = {
                            // §0.16.9：不再"一点就明天 09:00 / 再点清掉"，改成打开时间选择器。
                            reminderPicker = HistoryReminderPickerTarget(
                                entryId = target.entryId,
                                initialAtMs = target.reminderAtMs,
                            )
                        },
                        onDelete = {
                            editTarget = null
                            // 这条都没了，草稿（含 cache 临时图）留着只会挡住下一条 —— 立即作废。
                            EditSessionDraft.clear()
                            stashEntries.firstOrNull { it.id == target.entryId }
                                ?.let { deleteEntry(it) }
                        },
                        onVoiceError = showPanelMessage,
                        onAddImage = { onPicked ->
                            // §0.16.14：与加号弹窗同一条路 —— 先挂起面板窗，再走中转 Activity 选图；
                            // 回调里第一件事是恢复（取消也要恢复，否则面板一直不可见）。
                            //
                            // §0.16.17：**不在这里插块** —— 路径原样交回编辑条，由它按"当前光标"切块插入
                            // （与弹窗完全同一套；在这里插只能追加到末尾，那就退回老行为了）。
                            StashPanelExternalUi.suspendForExternalUi(caller = "editbar-add-image")
                            StashComposerImageTrampolineActivity.launch(appContext) { picked ->
                                StashPanelExternalUi.resumeAfterExternalUi(caller = "选图回调（编辑条）")
                                if (picked.isNotEmpty()) onPicked(picked.distinct())
                            }
                        },
                        onRemoveNewImage = { path ->
                            // 块的增删由编辑条做完（这里是"删了之后要干什么"）：只删 **cache 临时文件**
                            // —— 正文里**已有**的图存的是暂存夹文件名，拿它去 delete 会删掉用户的原图。
                            //
                            // ⚠️ 这里另有一道更可靠的闸：`DraftBlockEditorSurface` 只在
                            // `path !in existingImageFileNames` 时才调本回调（§0.16.18），
                            // 所以"已有图"根本到不了这里；`isAbsolute` 只是第二层保险。
                            if (File(path).isAbsolute) runCatching { File(path).delete() }
                        },
                        // §0.16.21：语音与图片同一套 —— 同样只删 cache 临时文件，
                        // 同样有"已有的不在 existingAudioFileNames 里才回调"那道闸。
                        onRemoveNewAudio = { path ->
                            if (File(path).isAbsolute) runCatching { File(path).delete() }
                        },
                        // §0.16.18：**同一个分流规则**给显示用（绝对路径直通 / 文件名拼暂存夹目录）。
                        // 之前这里无条件过 `imageFilePath`，把新选图的绝对路径又拼了一次目录 →
                        // 解不出图 → 块空白、保存时被跳过（用户报的"加图没成功"）。
                        resolveImagePath = { path ->
                            resolveEditBlockImagePath(
                                path = path,
                                existingImageFileNames = editEntryExistingImageNames,
                                repository = stashRepo,
                            )
                        },
                        // §0.16.21：语音的显示/播放走同构的分流（新录的直通、已有的拼音频目录）。
                        resolveAudioPath = { path ->
                            resolveEditBlockAudioPath(
                                path = path,
                                existingAudioFileNames = editEntryExistingAudioNames,
                                repository = stashRepo,
                            )
                        },
                        // §0.16.18：把"条目原本就有的图片文件名"递给块编辑器 ——
                        // 它靠这个集合决定"删块时要不要顺手删文件"（已有图绝不能删）。
                        existingImageFileNames = editEntryExistingImageNames,
                        existingAudioFileNames = editEntryExistingAudioNames,
                        onHeightChanged = { editBarHeight = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            // 提醒时间选择器（§0.16.9）：编辑条的「提醒」与加号弹窗的 ⏰ 胶囊都打开它。
            HistoryReminderPickerModal(
                open = reminderPicker != null,
                currentAtMs = reminderPicker?.initialAtMs,
                imeBottom = overlayImeBottom,
                onPick = applyReminder,
                onDismiss = { reminderPicker = null },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 提醒时间选择器"为谁而开"（§0.16.9）。
 *
 * `entryId == null` = 加号弹窗里那条**还没存下**的新条目：选到的时间先记在草稿里
 * （`StashComposerDraft.reminderAtMs`，§0.16.14 起它活得比组合长），存下拿到 id 之后再落盘。
 */
private data class HistoryReminderPickerTarget(
    val entryId: String?,
    val initialAtMs: Long?,
)

/**
 * 卡片外那行提醒要怎么画（§0.16.15）：`null` = 这条没有可画的提醒。
 *
 * 用 data class 而不是 `Pair<Long, Boolean>`：那两个字段都是"时间/布尔"，`Pair` 的
 * `.first` / `.second` 在调用点读起来是"猜"，而这个规则本身已经有 4 条分支了。
 */
private data class HistoryReminderRow(
    /** 要显示的时间（过期时就是"响过的那个时间"）。 */
    val atMs: Long,
    /** 已经过期：灰掉 + 前缀「已提醒」。 */
    val overdue: Boolean,
)

/**
 * 把 `reminders` 与 `firedAt` 两个 key 合起来，算出**这一行该怎么画**。
 *
 * 为什么需要两个 key 才知道事实：
 * - [reminderAtMs]（`StashMetaStore.reminders`）= **还没响**的提醒；
 * - [firedAtMs]（`StashMetaStore.firedAt`）= **响过了**的提醒 —— 记录它的原因是数据层
 *   `clearExpiredReminders` 会把过点的那条从 `reminders` 里删掉（不然闹钟一直挂着），
 *   而**删掉不等于没发生过**：用户要看到「已提醒」。
 *
 * 取值规则：
 * 1. 有 `reminders` 条目 → 画它的时间；`<= now` 而且已经响过（说明处在"到点"与"面板对账"
 *    之间那段窗口里）也算过期，这样"刚响、面板还开着"时就已经是灰的了；
 * 2. 只有 `firedAt` → 画"响过的时间"（用户看到的是"已提醒 昨天 21:30"）。
 *
 * 纯函数放在屏幕这一层（而不是塞进 `HistoryTimelineEntryRow`）：卡片那一层只该关心
 * "画成什么颜色"，"什么算过期"是数据语义，留在数据边上更好查。
 */
private fun stashReminderRow(
    reminderAtMs: Long?,
    firedAtMs: Long?,
    nowMs: Long,
): HistoryReminderRow? {
    if (reminderAtMs != null) {
        val overdue = firedAtMs != null || reminderAtMs <= nowMs
        return HistoryReminderRow(atMs = reminderAtMs, overdue = overdue)
    }
    firedAtMs?.let { return HistoryReminderRow(atMs = it, overdue = true) }
    return null
}

@Composable
private fun HistoryStashTabBody(
    allEntries: List<com.slideindex.app.stash.StashEntry>,
    filteredEntries: List<com.slideindex.app.stash.StashEntry>,
    searchQuery: String,
    /** 标签筛选的选中集合（多选；空集 = 「全部」）。 */
    selectedTags: Set<String>,
    /** 多标签匹配方式：true = 同时含全部（AND，默认），false = 含任一（OR）。 */
    tagMatchAll: Boolean,
    meta: com.slideindex.app.stash.StashMetaStore,
    /**
     * 卡片那行 ⏰ 的"现在"（§0.16.15）。由宿主每 2 秒推进一次（只在"还有未来提醒"时），
     * 这样提醒到点的那一刻，这一行**当场**从主题色变成灰色「已提醒」，而不是等到下次重组。
     */
    nowMs: Long,
    tagColors: Map<String, Long>,
    haptics: HistoryHaptics,
    isActive: Boolean,
    panelBlurActive: Boolean,
    /** 输入条开着时给它让位，否则最后一条会被盖住。 */
    listBottomPadding: Dp,
    repo: com.slideindex.app.stash.StashRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onSetDone: (com.slideindex.app.stash.StashEntry, Boolean) -> Unit,
    onToggleStar: (com.slideindex.app.stash.StashEntry) -> Unit,
    onDeleteEntry: (com.slideindex.app.stash.StashEntry) -> Unit,
    onEditEntry: (com.slideindex.app.stash.StashEntry) -> Unit,
    onClearSearch: () -> Unit,
    onClearTagFilter: () -> Unit,
    onShowMessage: (Int) -> Unit,
    /** 列表滑离顶部 → 上报给宿主决定头部收不收（§0.16.5）。 */
    onListScrolledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val collapseThresholdPx = with(LocalDensity.current) { HistoryHeaderCollapseThreshold.roundToPx() }
    // 只有"这一页是当前页"时才上报：两个页签各有一条列表，同时上报会让头部抖。
    LaunchedEffect(isActive, listState) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
            .distinctUntilChanged()
            .collect(onListScrolledChange)
    }
    val topEntryId = allEntries.firstOrNull()?.id
    var previousTopId by remember { mutableStateOf<String?>(null) }
    var previousIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 刚存下的那条：闪一下高亮环（设计稿 `.item.flash` + `setTimeout(1500)`）。
    var flashEntryId by remember { mutableStateOf<String?>(null) }
    // 首屏错开淡入：只在"这一页刚变成当前页"的这段时间里给行加延迟，
    // 过了窗口就不加 —— 否则往下滚出来的每一行都会再演一遍进场。
    var staggerPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        staggerPlaying = true
        delay(HistoryRowStaggerStepMs * HistoryRowStaggerMaxCount + STAGGER_TAIL_MS)
        staggerPlaying = false
    }
    LaunchedEffect(flashEntryId) {
        val id = flashEntryId ?: return@LaunchedEffect
        delay(HistoryCardFlashVisibleMs)
        if (flashEntryId == id) flashEntryId = null
    }
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || searchQuery.isNotBlank()) {
            previousTopId = topEntryId
            previousIds = allEntries.mapTo(HashSet()) { it.id }
            return@LaunchedEffect
        }
        val prevTop = previousTopId
        val prevIds = previousIds
        previousTopId = topEntryId
        previousIds = allEntries.mapTo(HashSet()) { it.id }
        if (prevTop != null && topEntryId != prevTop && topEntryId !in prevIds) {
            listState.animateScrollToItem(0)
            flashEntryId = topEntryId
        }
    }
    when {
        filteredEntries.isEmpty() -> {
            // 三种成因给三种文案 + 出口（设计稿 `emptyHtml()`）。
            val emptyModifier = Modifier
                .fillMaxSize()
                .padding(top = 2.dp, bottom = listBottomPadding)
                
            when {
                allEntries.isEmpty() -> HistoryEmptyState(
                    title = stringResource(R.string.stash_empty_all_title),
                    hint = stringResource(R.string.stash_empty_all_hint),
                    showArrow = true,
                    modifier = emptyModifier,
                )
                searchQuery.isNotBlank() -> HistoryEmptyState(
                    title = stringResource(R.string.stash_empty_search_title, searchQuery),
                    hint = stringResource(R.string.stash_empty_search_hint),
                    actionLabel = stringResource(R.string.stash_empty_search_action),
                    onAction = onClearSearch,
                    modifier = emptyModifier,
                )
                selectedTags.size >= 2 -> HistoryEmptyState(
                    // 多标签筛不出东西（需求 6）：AND 说"**同时**含这些标签"，这才是用户
                    // 真正需要看到的那句 —— 他可能以为选两个标签是"或"。
                    // OR 模式下不能这么说（那会是"没有含任一"却写成"没有同时含"），
                    // 所以沿用旧的单标签文案 key，把选中的几个标签名连起来。
                    //
                    // ⚠️ 连接符 " / " 是**硬编码分隔符，不是本地化资源** —— 这是有意接受的取舍：
                    // 只为"OR 且多标签且筛不出东西"这一种边角成因新增一条 `%1$s+%2$s` 式 key
                    // 不划算（四套 locale 都要为一句极罕见的提示再译一遍），而 `" / "` 在四种
                    // 语言里都读得通。**别把它当 bug 修**。
                    title = if (tagMatchAll) {
                        stringResource(R.string.stash_tag_filter_empty)
                    } else {
                        stringResource(
                            R.string.stash_empty_tag_title,
                            selectedTags.joinToString(" / "),
                        )
                    },
                    hint = stringResource(R.string.stash_empty_tag_hint),
                    actionLabel = stringResource(R.string.stash_tag_filter_clear),
                    onAction = onClearTagFilter,
                    modifier = emptyModifier,
                )
                // 只选中一个：标题 / 说明 / 出口**逐字沿用老的单选实现**（向后兼容）。
                selectedTags.isNotEmpty() -> HistoryEmptyState(
                    title = stringResource(R.string.stash_empty_tag_title, selectedTags.first()),
                    hint = stringResource(R.string.stash_empty_tag_hint),
                    actionLabel = stringResource(R.string.stash_empty_tag_action),
                    onAction = onClearTagFilter,
                    modifier = emptyModifier,
                )
                // 兜底：三段都不成立（理论上到不了），沿用既有文案。
                else -> HistoryEmptyState(
                    title = stringResource(R.string.stash_search_empty),
                    modifier = emptyModifier,
                )
            }
        }
        else -> {
            val scheme = MiuixTheme.colorScheme
            // 时间轴槽：分组表头 + 条目平铺成同一条 Lazy 列表（见 HistoryTimelineGrouping.kt）。
            val timelineRows = remember(filteredEntries) { buildHistoryTimelineRows(filteredEntries) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    // 左侧不留白：时间轴槽自己就是左边距（设计稿 `.stream .scroll{padding-left:0}`
                    // + `.grp{padding-left:62px}`）。
                    start = 0.dp,
                    end = 12.dp,
                    top = 2.dp,
                    bottom = 8.dp + listBottomPadding,
                ),
                // 行间距必须为 0：竖线是逐行画的，用 verticalArrangement 留缝会让线断口。
                // 间距改由行内的 padding 提供（分组表头 18dp / 条目 10dp，照设计稿）。
            ) {
                itemsIndexed(timelineRows, key = { _, row -> row.key }) { index, row ->
                    // 首屏错开淡入：只在这段时间里给前若干行加延迟（第 0 行也延一拍，
                    // 这样第一行同样有进场，而不是"只有后面几行在动"）。
                    val staggerDelay = if (staggerPlaying && index < HistoryRowStaggerMaxCount) {
                        (index + 1) * HistoryRowStaggerStepMs
                    } else {
                        0
                    }
                    when (row) {
                        is HistoryTimelineRow.GroupHeader -> HistoryTimelineGroupHeader(
                            group = row.group,
                            lineAlpha = row.lineAlpha,
                            staggerDelayMs = staggerDelay,
                        )
                        is HistoryTimelineRow.Entry -> {
                            val entry = row.entry
                            val expanded = entry.id in expandedEntryIds
                            val selectedIndex = selectedImageIndices[entry.id] ?: 0
                            // 提醒那一行（§0.16.15）：**过期的提醒也画**，只是灰掉 + 「已提醒」。
                            // 值来自两个 key：`reminders`（还没响的）与 `firedAt`（响过了、数据层已把
                            // `reminders` 里那条收尾删掉）。只读前者的话，提醒一响这行就整行消失，
                            // 用户看到的就是"我设的提醒不见了"。
                            val reminder = stashReminderRow(
                                reminderAtMs = meta.reminderOf(entry.id),
                                firedAtMs = meta.firedAtOf(entry.id),
                                nowMs = nowMs,
                            )
                            HistoryTimelineEntryRow(
                                entry = entry,
                                group = row.group,
                                lineAlpha = row.lineAlpha,
                                staggerDelayMs = staggerDelay,
                                reminderAtMs = reminder?.atMs,
                                reminderOverdue = reminder?.overdue == true,
                            ) {
                                HistoryStashEntryCard(
                                    entry = entry,
                                    expanded = expanded,
                                    onExpandedChange = { onToggleExpanded(entry.id) },
                                    selectedImageIndex = selectedIndex,
                                    onSelectedImageIndexChange = { onSelectedImageIndexChange(entry.id, it) },
                                    meta = meta,
                                    tagColors = tagColors,
                                    onSetDone = { done -> onSetDone(entry, done) },
                                    flash = entry.id == flashEntryId,
                                    dayGroup = row.group,
                                    haptics = haptics,
                                    onEdit = { onEditEntry(entry) },
                                    onShowMessage = onShowMessage,
                                    onPin = {
                                        when (entry.type) {
                                            StashEntryType.TEXT -> StashCoordinator.pinTextToScreen(context, entry.text.orEmpty())
                                            StashEntryType.IMAGE -> {
                                                val bitmap = repo?.loadImage(entry) ?: return@HistoryStashEntryCard
                                                StashCoordinator.pinImageFromStash(context, entry, bitmap)
                                            }
                                            StashEntryType.RICH -> StashCoordinator.pinRichFromStash(context, entry)
                                        }
                                    },
                                    onCopy = {
                                        val ok = StashCoordinator.copyStashEntry(context, entry)
                                        if (ok && entry.type != StashEntryType.IMAGE) {
                                            onShowMessage(R.string.float_ball_text_copied)
                                        }
                                    },
                                    onShare = {
                                        when (entry.type) {
                                            StashEntryType.TEXT -> FloatBallTextPick.shareText(context, entry.text.orEmpty())
                                            StashEntryType.IMAGE -> {
                                                val bitmap = repo?.loadImage(entry) ?: return@HistoryStashEntryCard
                                                FloatBallTextPick.shareScreenshot(context, bitmap)
                                            }
                                            StashEntryType.RICH -> {
                                                // §0.16.21：用 `exportText()` 而不是 `combinedText()` ——
                                                // 后者是**正文**（也是搜索语料），语音块不在里面；
                                                // 一条只录了音的闪念用 combinedText 分享出去会是空的。
                                                val combined = entry.exportText()
                                                if (combined.isNotBlank()) {
                                                    FloatBallTextPick.shareText(context, combined)
                                                } else {
                                                    val fileName = entry.allImageFileNames().firstOrNull()
                                                    val bitmap = fileName?.let { repo?.loadBitmapByFileName(it) }
                                                        ?: return@HistoryStashEntryCard
                                                    FloatBallTextPick.shareScreenshot(context, bitmap)
                                                }
                                            }
                                        }
                                    },
                                    onToggleStar = { onToggleStar(entry) },
                                    onDelete = { onDeleteEntry(entry) },
                                )
                            }
                        }
                    }
                }
                item(key = "stash_list_footer") {
                    Spacer(modifier = Modifier.height(HistoryListFooterPadding))
                }
            }
        }
    }
}

@Composable
private fun HistoryClipboardTabBody(
    /** 当前筛选下的**完整**条数（SQL COUNT），与"已加载条数"无关。 */
    totalCount: Int,
    /** 当前固定筛选：只影响空状态的文案（列表本身已经是筛过的）。 */
    filter: ClipboardHistoryFilter,
    filteredEntries: List<com.slideindex.app.clipboard.ClipboardEntry>,
    searchQuery: String,
    haptics: HistoryHaptics,
    isActive: Boolean,
    panelBlurActive: Boolean,
    loading: Boolean,
    clipboardRepo: com.slideindex.app.clipboard.ClipboardHistoryRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onEnsureLoaded: () -> Unit,
    onLoadMore: () -> Unit,
    onShowMessage: (Int) -> Unit,
    /** 列表滑离顶部 → 上报给宿主决定头部收不收（§0.16.5）。 */
    onListScrolledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val collapseThresholdPx = with(LocalDensity.current) { HistoryHeaderCollapseThreshold.roundToPx() }
    LaunchedEffect(isActive, listState) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
            .distinctUntilChanged()
            .collect(onListScrolledChange)
    }
    val scheme = MiuixTheme.colorScheme
    val previewWidthPx = historyPreviewWidthPx()
    val previewHeightPx = historyClipboardCardPreviewHeightPx()
    val isSearching = searchQuery.isNotBlank()
    val topEntryId = filteredEntries.firstOrNull()?.id
    val shouldLoadMore by remember {
        derivedStateOf {
            if (isSearching || loading) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            filteredEntries.isNotEmpty() && lastVisible >= filteredEntries.lastIndex - 2
        }
    }
    LaunchedEffect(isActive) {
        if (isActive && !isSearching) {
            onEnsureLoaded()
        }
    }
    var previousTopId by remember { mutableStateOf<String?>(null) }
    var previousIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || isSearching) {
            previousTopId = topEntryId
            previousIds = filteredEntries.mapTo(HashSet()) { it.id }
            return@LaunchedEffect
        }
        val prevTop = previousTopId
        val prevIds = previousIds
        previousTopId = topEntryId
        previousIds = filteredEntries.mapTo(HashSet()) { it.id }
        if (prevTop != null && topEntryId != prevTop && topEntryId !in prevIds) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
    when {
        filteredEntries.isEmpty() && !loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 2.dp)
                    ,
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // 只有"真的什么都没有"才是空历史；筛了分类却一条都没有 = 筛不出来，
                    // 该说"没有匹配"而不是"还没有记录"（闪念页签同款三选一）。
                    text = stringResource(
                        if (totalCount == 0 && filter == ClipboardHistoryFilter.All) {
                            R.string.clipboard_empty
                        } else {
                            R.string.clipboard_search_empty
                        },
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
        }
        filteredEntries.isEmpty() && loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 2.dp)
                    ,
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary,
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    // 顶部只留 2dp：上面那行筛选胶囊已经给出了间距（这里原本有 18dp 的"补偿"，
                    // 那是剪贴板页**没有**筛选行时的权宜，现在两个页签都有行了，补偿要撤掉）。
                    top = 2.dp,
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = filteredEntries,
                    key = { it.id },
                    contentType = { "clipboard_entry" },
                ) { entry ->
                    val expanded = entry.id in expandedEntryIds
                    val selectedIndex = selectedImageIndices[entry.id] ?: 0
                    HistoryClipboardEntryCard(
                        entry = entry,
                        expanded = expanded,
                        onExpandedChange = { onToggleExpanded(entry.id) },
                        selectedImageIndex = selectedIndex,
                        onSelectedImageIndexChange = { onSelectedImageIndexChange(entry.id, it) },
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        // 剪贴板卡片也按时间分档（设计稿 `clipItemHtml` 用的是同一套 .fresh/.mid/.old）。
                        dayGroup = historyDayGroupOf(entry.createdAtEpochMs, System.currentTimeMillis()),
                        haptics = haptics,
                        onShowMessage = onShowMessage,
                        onCopy = {
                            ClipboardWriter.write(context, entry)
                            onShowMessage(R.string.float_ball_text_copied)
                        },
                        onStash = {
                            StashCoordinator.addFromClipboard(context, entry) { success ->
                                onShowMessage(if (success) R.string.stash_saved else R.string.stash_save_failed)
                            }
                        },
                        onDelete = {
                            ClipboardThumbnailCache.evictEntry(entry)
                            scope.launch { clipboardRepo?.delete(entry.id) }
                        },
                    )
                }
                if (!isSearching && totalCount > 0) {
                    item(key = "clipboard_record_count") {
                        Text(
                            text = pluralStringResource(
                                R.plurals.clipboard_history_float_record_count,
                                totalCount,
                                totalCount,
                            ),
                            style = MiuixTheme.textStyles.body2,
                            color = scheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                if (!isSearching) {
                    item(key = "clipboard_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = scheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- 动效常量（P4） ---------------- */

/** 换页位移（设计稿 `.tabin-r/.tabin-l` 的 16px）。 */
private val PAGE_SWITCH_SLIDE = 16.dp

/** 换页时页内容淡到多透（= 1 - 位移比例 × 这个系数，下限见下）。 */
private const val PAGE_SWITCH_FADE = 0.55f
private const val PAGE_SWITCH_MIN_ALPHA = 0.45f

/** 新条目的高亮保持多久再清状态（设计稿 `setTimeout(() => flashId = null, 1500)`）。 */
private const val HistoryCardFlashVisibleMs = 1_500L

/** 错开淡入窗口的尾量：最后一行延迟之外再留一点动画时间。 */
private const val STAGGER_TAIL_MS = 400L

/** 列表滑离顶部多少距离后收起头部第一行（太小会"一碰就收"，大了又像没收）。 */
private val HistoryHeaderCollapseThreshold = 16.dp

/**
 * 面板可见期间重新计算「有没有已提醒未处理的条目」的间隔（§0.16.15，给指示条用）。
 *
 * 为什么是轮询而不是事件：那条判据里"通知栏里有我们那条通知"读的是系统 API，**不会**触发
 * Compose 重组（它既不是数据层、也不是 state）。2 秒 = 用户几乎感觉不到的延迟，
 * 而一次循环只是一次 `getActiveNotifications` + 一遍提醒表，代价可以忽略。
 */
private const val ReminderPendingPollIntervalMs = 2_000L

/**
 * §0.16.24：返回决策日志的 tag —— **沿用 `OverlayViewBackHandler` 那个 `OverlayBack`，不新建 tag**。
 *
 * 面板这条返回链的每一档都用**同一行格式**报自己（见 `onRegisterBackInterceptor` 里的注释）：
 * `back decision consumed=<true|false> branch=<ime|interceptor:*|clipboardInput|dismiss|fallthrough>`，
 * 真机上 `adb logcat -s OverlayBack` 一眼就能看出这次返回被谁吃掉、有没有漏给下层 App。
 */
private const val BackDecisionLogTag = "OverlayBack"