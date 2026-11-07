package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.stash.StashTag
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 就地编辑条（设计稿 `.editbar`）：改正文、改标签、追加一段、补图、标记完成、删除。
 *
 * 与设计稿的差异（**刻意**）：
 * - 设计稿挂在卡片旁边（`openEdit` 里按卡片的 `getBoundingClientRect` 定位），这里做成
 *   **面板底部的一条**：面板是 `LazyColumn` + 输入法抬升，跟着卡片定位在滚动/键盘下很容易跑偏。
 * - 设计稿把"追加"折叠进正文输入框（提示"光标已在末尾，可直接追加"），这里**单独给一个追加框
 *   —— 数据层本来就是分开的（`StashMetaRepository.appendText`），追加块也因此永远不覆盖原文。
 *
 * ---
 * ## §0.16.17：正文改成与「记一条」弹窗**同一套块编辑器**
 *
 * 老实现是"一个纯文字 `BasicTextField` + 框外一排**本次新选**的缩略图"：条目里**已有**的
 * 图片块一个都看不见（用户原话："已经存过图的闪念再次编辑发现文本框中不显示图片了"），
 * 而且那个输入框写死了 `ImeAction.Done`，回车被吃掉、**换不了行**。
 *
 * 现在正文是 [DraftBlock] 的有序块流（[DraftBlockEditorSurface]，与弹窗共用一份实现）：
 * - **回车换行**：`onSubmitKey = null` 时那一层就是 `ImeAction.Default` 且不挂 `onDone`；
 *   提交只走「保存」按钮（用户明确要求，弹窗那边才是"回车存下"）。
 * - **已有图片显示出来**：块里存的是暂存夹**文件名**，靠 [resolveImagePath] 拼成路径，
 *   见调用方（`HistoryPanelScreen`）传进来的实现。
 * - **光标处插图 / 退格删图 / 空块**：全部复用弹窗那一套（§0.16.16）。
 */
data class HistoryEditTarget(
    val entryId: String,
    val text: String,
    val tagNames: List<String>,
    val done: Boolean,
    val createdAtEpochMs: Long,
    /** 已设的提醒时间（null = 没设）。 */
    val reminderAtMs: Long? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Suppress("DEPRECATION") // BasicTextField 的 state 版重载在这个 foundation 版本里不存在，见输入条的同类注释
@Composable
fun HistoryPanelEditBar(
    target: HistoryEditTarget,
    availableTags: List<StashTag>,
    imeBottom: Dp,
    blurActive: Boolean,
    /**
     * 正文/图片块**每次变化**都回调（打字 / 语音 / 插图 / 删图；给的是新的块序列）。
     *
     * 为什么需要它（§0.16.14）：面板是 overlay 窗，切前台 App / 拉起系统相册时会被系统整个摘掉，
     * 下次打开是**全新的组合** —— 这个文件里的 `remember` 块会丢。调用方拿这个回调把块
     * 实时写进**进程级草稿**（[EditSessionDraft]），窗回来时才接得上。
     *
     * ⚠️ 只通知，**不参与取初值**：初值仍然是"打开时由调用方回填进 [EditSessionDraft]"，
     * 所以传进来的 `blocks` 就是唯一真相（本组件不自己 `remember` 一份块序列 —— 那就成了两个真相）。
     */
    blocks: List<DraftBlock>,
    onBlocksChange: ((List<DraftBlock>) -> List<DraftBlock>) -> Unit,
    /**
     * §0.16.17：保存时给的是**标签 + 新选图片的 cache 路径**。
     *
     * 正文不再从参数里拿：它就在 [blocks] 里，由调用方从草稿读（这样"正文/图片按块顺序写回"
     * 与"块编辑器显示的内容"必然是同一份）。
     *
     * `imagePaths` 只装**本次新选的**图（cache 临时文件，调用方保存时 decode + 落盘；
     * 已有的图在 `blocks` 里、按文件名复用，不重新落盘）。
     */
    onSave: (tags: List<String>, imagePaths: List<String>) -> Unit,
    /** 标签**每次勾选/取消**都回调（整份新集合）。同上：只通知，不改初值语义。 */
    onTagsChange: (Set<String>) -> Unit = {},
    onAppend: (String) -> Unit,
    onToggleDone: () -> Unit,
    onToggleReminder: () -> Unit,
    onDelete: () -> Unit,
    onVoiceError: (Int) -> Unit = {},
    /** 点「＋ 图片」：走中转 Activity 选图（overlay 里不能直接拉系统选择器）。 */
    onAddImage: (onPicked: (List<String>) -> Unit) -> Unit = {},
    /** 删掉一个**新选**的图片块（✕ 或退格）：调用方要把 cache 里那份临时文件也删掉。 */
    onRemoveNewImage: (String) -> Unit = {},
    /**
     * 删掉一段**刚录完**的语音（✕ 或退格，§0.16.21）：调用方要把 cache 里那份临时 m4a 也删掉。
     * 与 [onRemoveNewImage] 完全对称。
     */
    onRemoveNewAudio: (String) -> Unit = {},
    /**
     * 块里的路径 → 能解码的路径。
     *
     * ⚠️ 必须有：已有图片块存的是**文件名**（`ClipboardContentBlock.fileName`），
     * 直接丢给 `BitmapFactory` 是解不出来的（当前目录里没有这个文件）。
     * 调用方用 `StashAccess.repository?.imageFilePath(name)` 拼。
     *
     * ⚠️ §0.16.18：**新选的图是绝对路径，必须原样直通**（不能再拼一次目录 —— 否则拼出一个
     * 不存在的路径、块变空白、保存时图被跳过，用户看到的就是"加图没成功"）。
     * 调用方（`HistoryPanelScreen`）走的是共用的 `resolveEditBlockImagePath`，
     * 它按 [existingImageFileNames] 分流；本组件只负责把集合透传下去。
     */
    resolveImagePath: (String) -> String,
    /**
     * 条目**原本就有**的图片文件名（§0.16.18）。
     *
     * 只用来回答"删掉这个图块时要不要顺手删文件"：新选的 cache 临时文件才删，
     * 已有文件（用户的原图）绝不能删。判据是**集合成员**，不是"路径长得像什么"。
     */
    existingImageFileNames: Set<String> = emptySet(),
    /**
     * 语音块里的路径 → **能播的绝对路径**（§0.16.21，与 [resolveImagePath] 同一套分流）。
     */
    resolveAudioPath: (String) -> String = { it },
    /** 条目**原本就有**的语音文件名（§0.16.21）：判据与 [existingImageFileNames] 同理。 */
    existingAudioFileNames: Set<String> = emptySet(),
    /**
     * 图片块角落那枚 **✎**（§0.16.22）：点了把**这一块**的当前图片送进内置编辑器。
     *
     * 就地编辑条**一定**要传它（这是"就地改图"的唯一入口）；加号弹窗不传 = 不显示那枚按钮
     * （理由见 `DraftBlockEditorSurface.onEditImage` 的 KDoc）。
     *
     * 参数是块 id —— 由 `HistoryPanelScreen.openImageEditorForBlock` 翻成可解码路径、
     * 起中转 Activity、拿到编辑结果后**替换那一块**。
     */
    onEditImage: ((blockId: String) -> Unit)? = null,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val textFocusRequester = remember { FocusRequester() }
    var append by remember(target.entryId) { mutableStateOf("") }
    var tags by remember(target.entryId) { mutableStateOf(target.tagNames) }

    LaunchedEffect(target.entryId) {
        delay(EDIT_BAR_FOCUS_DELAY_MS)
        textFocusRequester.requestFocus()
    }

    val theme = historyTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = imeBottom)
            .onGloballyPositioned { onHeightChanged(with(density) { it.size.height.toDp() }) }
            .clip(
                // 现在是**屏幕居中的大浮窗**（不再是贴底抽屉）：四角都圆。
                RoundedCornerShape(28.dp),
            )
            // 卡片本体吃点击：不然点在卡片空白处会穿到"点空白关闭"的遮罩上（§0.16.9）。
            .historyConsumeTaps()
            // 设计稿的就地编辑条是 `.editbar.g` —— 同样用玻璃底。
            .background(theme.glassSolid)
            // 设计稿编辑条是 `padding: 14px 14px 12px`；这里是贴底抽屉，所以底部留出安全边。
            .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            top.yukonga.miuix.kmp.basic.Text(
                text = formatHistoryRelativeTime(target.createdAtEpochMs),
                style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.tiny),
                color = scheme.onSurfaceVariantSummary,
            )
            top.yukonga.miuix.kmp.basic.Text(
                text = stringResource(R.string.stash_edit_meta_hint),
                style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.tiny),
                color = scheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            // ⚠️ §0.16.17：语音按钮**不再**放在这行 —— 它跟着块编辑器走
            // （见下面 `DraftBlockEditorSurface` 的 `showVoiceButton = true`）。
            // 理由是"识别结果要插到光标处"：光标只在块编辑器内部，按钮放外面就只能"追加到末尾"
            // （老行为），或者需要在两层之间来回传一个"这次该插哪"的请求。
            // 已设提醒时把时间摆出来（点击按钮可取消）。
            target.reminderAtMs?.let { at ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    top.yukonga.miuix.kmp.basic.Text(
                        text = formatReminderTime(at),
                        style = HistoryPanelTypography.meta(),
                        color = scheme.primary,
                    )
                }
            }
        }
        // §0.16.17：正文交给**与弹窗共用**的块编辑器。
        //
        // ⚠️ 四处参数是"编辑条与弹窗的差异"，都写在这儿而不是藏进共用组件里：
        // ① `onSubmitKey = null` ⇒ **回车换行**（回归修复，用户明确要求；提交只走「保存」）；
        // ② `hint = null` ⇒ 编辑条不给占位提示（老实现也没有）；
        // ③ `focusDelegation = false` ⇒ 主输入框的焦点在打开时就抢好了，插块之后**不要**再抢
        //    （抢了会把 IME 收起来，用户得再点一次输入框）；弹窗那边则相反；
        // ④ `onTextInsertedAtCursor` ⇒ 语音插进哪一块只有本层知道，插完把**算好的**新块序列
        //    转给调用方（不重算：重算必然插错块）。
        DraftBlockEditorSurface(
            blocks = blocks,
            onBlocksChange = onBlocksChange,
            onAddImage = onAddImage,
            onRemoveNewImage = onRemoveNewImage,
            onRemoveNewAudio = onRemoveNewAudio,
            onVoiceError = onVoiceError,
            onSubmitKey = null,
            hint = null,
            focusRequester = textFocusRequester,
            focusDelegation = false,
            resolveImagePath = resolveImagePath,
            showVoiceButton = true,
            onTextInsertedAtCursor = { updated -> onBlocksChange { updated } },
            existingImageFileNames = existingImageFileNames,
            resolveAudioPath = resolveAudioPath,
            existingAudioFileNames = existingAudioFileNames,
            onEditImage = onEditImage,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            availableTags.forEach { tag ->
                val selected = tag.name in tags
                HistoryEditChip(
                    label = stashTagLabel(tag.name),
                    dotColor = Color(tag.colorArgb),
                    selected = selected,
                    onClick = {
                        val next = if (selected) tags - tag.name else tags + tag.name
                        tags = next
                        // 勾/取消都实时上报（§0.16.14）：调用方写进程级草稿，窗被摘掉也不丢。
                        onTagsChange(next.toSet())
                    },
                )
            }
        }
        HistoryEditAppendRow(
            value = append,
            onValueChange = { append = it },
            onSubmit = {
                val trimmed = append.trim()
                if (trimmed.isNotEmpty()) {
                    append = ""
                    onAppend(trimmed)
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryEditActionButton(
                label = stringResource(R.string.stash_remind_toggle),
                icon = Icons.Outlined.Schedule,
                onClick = onToggleReminder,
                active = target.reminderAtMs != null,
                modifier = Modifier.weight(1f),
            )
            HistoryEditActionButton(
                label = stringResource(
                    if (target.done) R.string.stash_action_mark_undone else R.string.stash_action_mark_done,
                ),
                icon = Icons.Outlined.CheckCircle,
                onClick = onToggleDone,
                modifier = Modifier.weight(1f),
            )
            HistoryEditActionButton(
                label = stringResource(R.string.stash_action_delete),
                icon = Icons.Default.Delete,
                onClick = onDelete,
                danger = true,
                modifier = Modifier.weight(1f),
            )
            HistoryEditActionButton(
                label = stringResource(R.string.stash_edit_save),
                icon = Icons.Default.Check,
                // §0.16.17：正文/图片都在 [blocks] 里，由调用方从草稿读；这里只交标签与
                // **本次新选**的图片路径（已有的图在 blocks 里、按文件名复用）。
                onClick = {
                    onSave(
                        tags,
                        blocks.filterIsInstance<DraftBlock.Image>()
                            .map { it.path }
                            .filter { it != resolveImagePath(it) },
                    )
                },
                primary = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoryEditChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            // 选中 = accent-soft 实底，未选中 = 玻璃渐变（Color 与 Brush 不能混在一个 if 里）。
            .then(
                if (selected) {
                    Modifier.background(theme.accentSoft)
                } else {
                    Modifier.background(theme.glassFill)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) theme.accent.copy(alpha = 0.55f) else theme.glassBorder,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(HistoryRadii.pill))
                .background(dotColor),
        )
        top.yukonga.miuix.kmp.basic.Text(
            text = label,
            // `.chip { font-size: var(--f-meta) }`
            style = androidx.compose.ui.text.TextStyle(
                fontSize = HistoryFontSizes.meta,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = theme.text,
        )
    }
}

/** 「追加一句」：回车即追加，不覆盖原文（数据层是独立的追加列表）。 */
@Suppress("DEPRECATION") // 同上：state 版 BasicTextField 重载在这个 foundation 版本里不存在
@Composable
private fun HistoryEditAppendRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer)
            .border(width = 1.dp, color = scheme.dividerLine, shape = shape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = scheme.onSurfaceVariantSummary,
            modifier = Modifier.size(16.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                lineHeight = 21.6.sp,
                color = scheme.onSurface,
            ),
            cursorBrush = SolidColor(scheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = stringResource(R.string.stash_edit_append_hint),
                            style = HistoryPanelTypography.content(),
                            color = scheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (value.isNotBlank()) scheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSubmit,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.stash_edit_append_hint),
                tint = if (value.isNotBlank()) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}


/** 与搜索框/输入条同一套抢焦点节奏。 */
private const val EDIT_BAR_FOCUS_DELAY_MS = 180L
