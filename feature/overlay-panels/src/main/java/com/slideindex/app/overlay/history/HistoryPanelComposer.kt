package com.slideindex.app.overlay.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.clipboard.formatAudioDuration
import com.slideindex.app.voice.StashAudioRecordSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 面板内直接记一条（设计稿 `ui_demo_capsule.html` 的 `.fab` + `.composer`）。
 *
 * 几何照设计稿：
 * - `.fab { right:16px; bottom:92px; 54×54; 圆角 19px }` —— 打开时抬到输入条上方 48dp（`.fab.open{bottom:158px}`）
 * - `.composer { left/right:0; bottom:0; padding:12px 16px 28px; 顶部圆角 26px }`
 * - 输入条：48dp 高的胶囊（`.composer input{height:var(--hit);border-radius:var(--r-pill)}`）
 * - 送出键：48dp 圆形，有内容=主题色+白勾，空=`.dim`（灰底 + 次级色勾）
 *
 * ⚠️ 位置全部由**同一个 Column** 决定（FAB → 间隔 → 输入条），不要用绝对偏移量：
 * 这样 FAB 永远跟着输入条走，不需要量高度、也不会在展开动画里穿帮。
 *
 * ⚠️ IME：overlay 窗读不到 `WindowInsets.ime`，必须用 `rememberOverlayImeBottomHeight()`
 * 把整条抬到输入法上方（调用方传 [imeBottom]）。
 */
@Composable
fun HistoryComposerFabSlot(
    visible: Boolean,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(modifier = modifier) {
        HistoryComposerFab(open = open, onClick = { onOpenChange(!open) })
    }
}

/**
 * 「记一条」的**屏幕居中模态**（用户要求：别局限在面板里）。
 *
 * 形状与编辑条同一套：不透明玻璃底 + `glassBorder` 描边 + 12dp 投影 + 22dp 圆角；
 * ⚠️ overlay 窗读不到 `WindowInsets.ime`，用调用方传进来的 [imeBottom] 把整块**往上抬**
 * （居中浮窗被输入法盖住就白做了）。
 *
 * ---
 * ## §0.16.16：正文是**块编辑器**（文字与图片同一列、按顺序）
 *
 * 老实现是"一个 `BasicTextField` + 框下一排缩略图"：图永远在正文**外面**，
 * 只有存下时靠光标位置把正文切成两半、把图夹进去（§0.16.15）—— 用户在输入框里
 * **看不见图**，也表达不出"图 A、文字、图 B"这种交错。本次改成**纵向块流**：
 * 每个 `DraftBlock.Text` 一个输入框、每个 `DraftBlock.Image` 一张整宽的图，
 * 顺序就是 [blocks] 的顺序（图片真的在正文里了）。
 *
 * 为什么非这样不可（不是偷懒）：本仓库解析到的 foundation 1.13.0-alpha03 里
 * `BasicTextField(state = TextFieldState)` **没有** `inlineContent` 参数，
 * `TextFieldState` / `TextFieldBuffer` 上**没有** `appendInlineContent`（那是
 * `AnnotatedString.Builder` 的、只被 `BasicText` 消费）。也就是"文字流里嵌图"
 * 在本版本走不通，块编辑器是唯一能把图**放进正文里**的做法。
 *
 * ⚠️ 代价（刻意接受，别当 bug 改）：每块是各自的输入框，"一段连续正文"的观感靠
 * 统一字号/行高 + 块间 7dp 间距维持；跨块的连续选择/换行不如单个输入框顺滑。
 *
 * ⚠️ 仍然用**已废弃**的 `BasicTextField(value/onValueChange)` 重载：
 * 块内"退格删图"必须能读到 `selection`（`TextFieldValue`），而本版本没有 state 版重载。
 */
@Suppress("DEPRECATION")
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HistoryComposerModal(
    open: Boolean,
    /** 正文的块序列（唯一真相在 [StashComposerDraft.blocks]，这里传进来的是一份快照）。 */
    blocks: List<DraftBlock>,
    /** 改块序列：调用方落回 [StashComposerDraft.updateBlocks]（镜像同步也在那里）。 */
    onBlocksChange: ((List<DraftBlock>) -> List<DraftBlock>) -> Unit,
    onSubmit: () -> Unit,
    onVoiceError: (Int) -> Unit = {},
    availableTags: List<com.slideindex.app.stash.StashTag> = emptyList(),
    selectedTags: Set<String> = emptySet(),
    onToggleTag: (String) -> Unit = {},
    /** 已预设的提醒（null = 没设）；显示在标签行末尾那枚 ⏰ 胶囊上。 */
    reminderAtMs: Long? = null,
    /** 点那枚 ⏰ 胶囊：打开提醒时间选择器（§0.16.9）。 */
    onReminderClick: () -> Unit = {},
    /**
     * 点「＋ 图片」：走中转 Activity 选图（overlay 里不能直接拉系统选择器）。
     *
     * 回调带回来的每张图都由**本组件**插到"当前光标处"（见 [insertImagesAtCursor]）——
     * 调用方只负责拉起选图（以及窗的挂起/恢复），不用懂块怎么切。
     */
    onAddImage: (onPicked: (List<String>) -> Unit) -> Unit = { },
    /** 删掉一张图（✕ 或退格）：调用方要把 cache 里那份临时文件也删掉，别留垃圾。 */
    onRemoveImage: (String) -> Unit = {},
    /**
     * 删掉一段语音（✕ 或退格，§0.16.21）：与 [onRemoveImage] 同一件事 ——
     * 把 cache 里那份**刚录完还没落盘**的临时 m4a 删掉。已有文件的判据在
     * [DraftBlockEditorSurface] 的 `existingAudioFileNames` 里（弹窗这条路上它恒为空集）。
     */
    onRemoveAudio: (String) -> Unit = {},
    imeBottom: Dp,
    focusRequester: FocusRequester,
    /**
     * 每次"重新打开"时 +1。
     *
     * 为什么需要它：本组件在**长按指示条弹出的独立编辑窗**里是**常驻可见**的
     * （那个窗一次只开一次组合，`open` 恒为 true），第二次长按不会重建组合 ——
     * 只认 `open` 的 `LaunchedEffect` 就不会重跑，用户看到的是"第二次打开光标不在、输入法不弹"。
     * 面板那条路每次开关都会重组，传默认值 0 即可（行为与改动前完全一致）。
     */
    focusEpoch: Int = 0,
    onBarHeightChanged: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    // 与搜索框同一套节奏：先让展开动画起来再抢焦点，否则 overlay 窗里 IME 常常不弹。
    // ⚠️ `focusEpoch` 也要进 key：独立编辑窗里 `open` 恒为 true，只有它能触发"再抢一次焦点"。
    LaunchedEffect(open, focusEpoch) {
        if (open) {
            delay(HistoryComposerFocusDelayMs)
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }
    AnimatedVisibility(
        visible = open,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = modifier
                .padding(bottom = imeBottom)
                .shadow(18.dp, shape)
                .clip(shape)
                // 卡片本体吃点击：不然点在卡片空白处会穿到"点空白关闭"的遮罩上（§0.16.9）。
                .historyConsumeTaps()
                .background(theme.glassSolid)
                .border(1.dp, theme.glassBorder, shape)
                .padding(horizontal = 26.dp, vertical = 24.dp)
                .onGloballyPositioned {
                    onBarHeightChanged(with(density) { it.size.height.toDp() })
                },
        ) {
            // ---- 复用就地编辑条那一套壳：标题行 / 块编辑器 / 标签行 / 底部整行主按钮 ----
            Text(
                text = stringResource(R.string.stash_composer_fab),
                style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.meta),
                color = theme.sub,
            )
            DraftBlockEditorSurface(
                blocks = blocks,
                onBlocksChange = onBlocksChange,
                onAddImage = onAddImage,
                onRemoveNewImage = onRemoveImage,
                onRemoveNewAudio = onRemoveAudio,
                onVoiceError = onVoiceError,
                // §0.16.19（用户明确要求）：**弹窗也改成回车换行**，提交只走「存下」按钮。
                //
                // ⚠️ `null` ⇒ 那一层是 `ImeAction.Default` 且**完全不挂 onDone** —— 回车只会插入换行。
                // 与编辑条（同样传 null）行为完全一致，两处都不再有"回车 = 确认"。
                // 提示语必须跟着换（否则文案与行为矛盾）：用新 key `stash_composer_hint_multiline`
                // （旧的 `stash_composer_hint` 仍留着，别的地方可能还在用）。
                onSubmitKey = null,
                hint = stringResource(R.string.stash_composer_hint_multiline),
                focusRequester = focusRequester,
                // 弹窗要靠它把焦点从别处拉回正文（比如插完图接着打字）。
                focusDelegation = true,
            )
            // ⏰ 提醒胶囊**永远**在（标签可以为空），按用户建议塞在标签行的行尾、不新起一行（§0.16.9）。
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availableTags.forEach { tag ->
                    HistoryChip(
                        label = stashTagLabel(tag.name),
                        dotColor = Color(tag.colorArgb),
                        selected = tag.name in selectedTags,
                        onClick = { onToggleTag(tag.name) },
                    )
                }
                HistoryChip(
                    label = if (reminderAtMs == null) {
                        "⏰ " + stringResource(R.string.stash_remind_toggle)
                    } else {
                        "⏰ " + formatReminderTime(reminderAtMs)
                    },
                    dotColor = null,
                    selected = reminderAtMs != null,
                    onClick = onReminderClick,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HistoryEditActionButton(
                    label = stringResource(R.string.stash_composer_send),
                    icon = Icons.Default.Check,
                    onClick = onSubmit,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 「一块标题小行（「＋图片」+ 可选语音）+ 文字/图片交错的正文列」—— **两个编辑入口共用**的块编辑器
 * （§0.16.16 弹窗，§0.16.17 起就地编辑条也用同一份）。
 *
 * 为什么提出来共用而不是各写一份：光标处插块、退格删图、每块一个 `FocusRequester` 这套
 * 协作只应该有一个真源 —— 两份实现只要有一处走偏（比如一边忘了"位置 0 才认退格"），
 * 用户就会觉得"弹窗能删、编辑条不能删"，而那种 bug 没人愿意去查第二遍。
 *
 * 两个入口的差异**全部**收在参数里（不靠内部 `if (是不是弹窗)`）：
 * - [onSubmitKey]：有值 = 回车提交（弹窗，提示语就是"回车存下"）；**null = 回车换行**（编辑条，
 *   提交只走「保存」按钮）。见 [HistoryComposerTextBlock] 里 `ImeAction` 怎么跟着它走。
 * - [hint]：只有弹窗给占位提示。
 * - [focusDelegation]：块**已经**被聚焦、只要求"把光标挪过来"时要不要再抢一次焦点。
 *   编辑条**不要**（它的主输入框是打开时就抢好焦点的，插完图再抢一次等于把 IME 关掉）；
 *   弹窗要（它靠这个把焦点从别处拉回正文）。
 * - [resolveImagePath]：块里存的路径怎么变成"能解码的路径"。弹窗里本来就是绝对路径（原样返回）；
 *   编辑条里已有图片存的是**文件名**，得拼成暂存夹里的路径。
 * - [showVoiceButton]：编辑条的语音按钮在自己的标题行上，正文这一层不要重复摆一个。
 * - [onTextInsertedAtCursor]：语音识别结果插进某个文字块之后的**回调**。
 *   编辑条的语音按钮在标题行上（本层之外），只有本层知道"插进了哪一块、光标在哪"，
 *   所以用它把这次改动转给外面（弹窗不需要：它的语音按钮就在本层里，`insertVoiceAtCursor`
 *   已经在回调里把改动交给 `onBlocksChange` 了）。
 * - [existingImageFileNames]（§0.16.18）：**"哪些路径是已落盘的文件名"**。图片块里那两种路径
 *   （cache 绝对路径 / 暂存夹文件名）没法靠字符串判据可靠区分 —— Windows 风格的路径、
 *   将来换存储目录都会让 `startsWith("/")` 这类猜测失效。所以由调用方把"条目原本就有的文件名"
 *   传进来，本层只做**集合成员判断**。误判的代价很具体：把"新选的图"当"已有文件"，
 *   用户一保存这张图就**不会**落盘（块还在、打开却没有图）。
 */
@Composable
fun DraftBlockEditorSurface(
    blocks: List<DraftBlock>,
    onBlocksChange: ((List<DraftBlock>) -> List<DraftBlock>) -> Unit,
    onAddImage: ((onPicked: (List<String>) -> Unit) -> Unit)?,
    onRemoveNewImage: (String) -> Unit,
    onVoiceError: (Int) -> Unit,
    /** 有值 = 正文里的回车是"提交"；null = 回车换行（见函数 KDoc）。 */
    onSubmitKey: (() -> Unit)?,
    hint: String?,
    focusRequester: FocusRequester?,
    /** 块已经被聚焦时，插块/插字之后还要不要再抢一次焦点。 */
    focusDelegation: Boolean,
    /** 把块里的路径解析成"真能解码的路径"（默认恒等：弹窗里存的本来就是绝对路径）。 */
    resolveImagePath: (String) -> String = { it },
    showVoiceButton: Boolean = true,
    /** 语音识别结果被插到某块光标处之后回调（给"语音按钮在本层外面"的编辑条用）。 */
    onTextInsertedAtCursor: ((List<DraftBlock>) -> Unit)? = null,
    /**
     * 条目**原本就有**的图片文件名（§0.16.18）。
     *
     * 只用来回答一个问题：删掉某个图片块时，要不要顺手删它的文件？
     * 是"新选的 cache 临时文件"才删；是"暂存夹里的已有文件"**绝不能删**（那是用户的原图）。
     * 判据是**集合成员**而不是"路径长得像什么"，理由见上面 KDoc。
     * 默认空集 = 没有任何"已有文件名"（弹窗里本来就全是新选的图）。
     */
    existingImageFileNames: Set<String> = emptySet(),
    /**
     * 删掉一段**刚录完、还没落盘**的语音（§0.16.21）：调用方负责删 cache 里的临时 m4a。
     *
     * 与 [onRemoveNewImage] 完全对称 —— 判据也是"这个路径在不在
     * [existingAudioFileNames] 里"，不在才调本回调。
     */
    onRemoveNewAudio: (String) -> Unit = {},
    /**
     * 把语音块里的路径解析成"**能播的绝对路径**"（与 [resolveImagePath] 同一套分流）。
     *
     * 默认恒等：弹窗里本来就全是 cache 绝对路径。编辑条那边已有语音存的是**文件名**，
     * 由调用方拼成闪念音频目录下的路径。
     */
    resolveAudioPath: (String) -> String = { it },
    /**
     * 条目**原本就有**的语音文件名（§0.16.21）—— 与 [existingImageFileNames] 同一套理由：
     * 只用来回答"删掉这个语音块时要不要顺手删文件"（已有文件绝不能删）。
     */
    existingAudioFileNames: Set<String> = emptySet(),
    /**
     * 图片块角落那枚 **✎**（§0.16.22）：非 null = 显示，点了用它把**当前这一块**送进内置图片编辑器。
     *
     * 传 null（默认）= 不显示 —— 加号弹窗（`HistoryComposerModal`）走的就是这条路。
     * 这么分是因为"编辑一张已经落盘的图、再把结果**换回这一块**"只有就地编辑那条链有回传路径
     * （见 `HistoryPanelScreen` 的 `openImageEditorForBlock`），弹窗里那块图还没落盘、也没有
     * "属于哪一条条目"可言。
     *
     * 参数是**块 id**：块里的路径有两种含义（cache 绝对路径 / 条目里的文件名），
     * 只有调用方拿着 `existingImageFileNames` 能把它们翻成可解码的路径并写回正确的那一块。
     */
    onEditImage: ((blockId: String) -> Unit)? = null,
) {
    /**
     * 图片块解码的采样目标（§0.16.19）。
     *
     * ⚠️ 在这里算**一次**、传给每个图片块：目标只取决于"正文能有多宽"，
     * 与具体是哪张图无关；每个块各算一次会在重组时重复读 `LocalWindowInfo`。
     */
    val imageTargetPx = draftBlockImageTargetPx()
    val theme = historyTheme()
    /** 本层的协程作用域：给"滚进视口"用（滚动的 `animateScrollTo` 是挂起函数）。 */
    val coroutineScope = rememberCoroutineScope()
    /**
     * 每块的焦点句柄。`remember` 一次、按 id 取用：**不能**跟着块生灭 ——
     * 焦点衔接（删图后跳到上一个文字块）恰恰发生在"新块刚加进来、下一帧才要去要焦点"的那一刻。
     */
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    /**
     * 各文字块**最近一次**的选区。用途是"图片插到光标处"：点「＋图片」会让输入框失焦
     * （系统相册是另一个 Activity），那时再去问输入框要 `selection` 是拿不到的，
     * 只有这里留着的那一份是准的。
     */
    val latestValues = remember { mutableStateMapOf<String, TextFieldValue>() }
    /**
     * 焦点**将要**落到哪个块、光标放在第几个字符。
     *
     * 为什么要"将要"：插入/删除**当帧**那块还不存在（或还没测量），`requestFocus()`
     * 会静默失败（overlay 窗里尤其明显）；这里先写下来，等对应的
     * [HistoryComposerTextBlock] 组合出来之后由它自己消费。
     */
    val pendingCursor = remember { mutableStateMapOf<String, Int>() }
    /**
     * 最近一次被聚焦的文字块（**不**在失焦时清掉）。
     *
     * 点「＋图片」那一瞬间正文就失焦了，清掉的话"插到哪一块"就没了；保留它 =
     * "图插到我最后打字的那一块的光标处"，正好是用户的心智模型（[latestValues] 同理）。
     */
    var lastFocusedBlockId by remember { mutableStateOf<String?>(null) }
    /**
     * 正文区的滚动状态（§0.16.18）。
     *
     * 为什么必须有它：块一多（一张图就 120dp）就超过视口（`heightIn(160..320)`），
     * 没有滚动的话下半截**根本够不到** —— 用户看到的是"图底下的东西看不见也滑不到"。
     *
     * ⚠️ 视口高度**不改成无限高**：弹窗/编辑条都是"屏幕居中浮窗"，正文无限高会把
     * 「存下」/「保存」按钮顶出屏幕。所以是"固定视口 + 内部滚动"。
     */
    val scrollState = rememberScrollState()
    /**
     * 各块在**内容坐标系**里的 y（由每块 `onGloballyPositioned` 上报）。
     *
     * 用途是"插入之后把目标块滚进视口"：块高不是固定值（文字块随行数变、图片块 120dp），
     * 靠"index × 估算高度"一定会滚歪，所以这里记**实测**位置。
     *
     * ⚠️ 坐标来自 `positionInParent()`，而父节点是**块列表那个 Column**（滚动容器的子节点）——
     * 所以它是"内容坐标"，滚动时**不**变。判断可见性时必须跟 `scrollState.value` 相加后
     * 再和视口比（见 [scrollBlockIntoView]），**不能**把这个值当成"视口坐标"直接加 current。
     */
    val blockViewportOffsets = remember { mutableStateMapOf<String, Int>() }
    /**
     * 等位置上报的"待滚目标"（块 id）。
     *
     * 刚插进来的块在当帧还没有实测 y（`SideEffect` / 组合回调都跑在 layout 之前），
     * 这时**只记下它、不滚**，等它自己的 `onGloballyPositioned` 上报后由 [reportBlockPlaced]
     * 再滚一次。曾经的"位置未知就滚到底"兜底会把顶部插入的块和上方正文一起顶出视口
     * （用户报过：录完音后录音块和第一行都跑到上面看不见了）。
     */
    var pendingScrollBlockId by remember { mutableStateOf<String?>(null) }
    /** 正文视口高度（px，由容器 `onGloballyPositioned` 实测上报）；0 = 还没测到。 */
    var viewportHeightPx by remember { mutableStateOf(0) }
    /**
     * **块序列本身**的实测高度（px，§0.16.23）。
     *
     * 只用来算尾部那枚"空白落点"该占多高（[tailTapZoneHeight]）—— 它量的是**块**，
     * 不含落点自己，所以"量出来 → 改落点高度 → 再量"不构成回环（一轮就稳定）。
     */
    var blocksHeightPx by remember { mutableStateOf(0) }
    /**
     * 尾部"空白落点"的高度（§0.16.23）。
     *
     * = `正文视口最小高度 − 块序列高度`，下限 40dp（空文字块的最小高度）。
     *
     * 为什么这么算：正文视口是 `heightIn(160..320)`，内容不满 160dp 时**容器仍然有 160dp**
     * —— 最后一块下面那一片空白原来是死的（点它什么都不会发生）。按这个算式，落点正好把
     * "从最后一块底部到视口底部"整片吃掉；块本身已经超过 160dp 时（要滚动）就退回 40dp 的最小落点。
     *
     * ⚠️ 不要改用 `viewportHeightPx` 来算：那个值是**容器**高度，容器高度 = `clamp(内容高, 160, 320)`，
     * 而内容高里又含落点自己 → 会滚成"每次都把内容撑到 320dp"的正反馈（面板正文永远顶格）。
     */
    val tailTapZoneHeight: Dp =
        (HistoryComposerBodyMinHeightDp - with(LocalDensity.current) { blocksHeightPx.toDp() })
            .coerceAtLeast(HistoryComposerEmptyBlockMinHeight)

    /** 当前"插入/修改"的落点：优先最后聚焦那块（还在的话），否则退到最后一个文字块。 */
    fun targetTextBlockId(): String? =
        lastFocusedBlockId?.takeIf { id -> blocks.any { it.id == id && it is DraftBlock.Text } }
            ?: blocks.lastOrNull { it is DraftBlock.Text }?.id

    /** `HistoryComposerScrollSlack` 的 dp → px（[scrollBlockIntoView] 里的比较全是 px）。 */
    val scrollSlackPx = with(LocalDensity.current) { HistoryComposerScrollSlack.roundToPx() }

    /**
     * 把某个块滚进视口（§0.16.18）。
     *
     * 只在"确实看不到"时才滚（可见就什么都不做）：插一张图之后正文里光标那块本来就多半可见，
     * 每次都滚会把用户已经调好的位置晃走。
     *
     * ⚠️ 位置是**内容坐标**（见 [blockViewportOffsets]），所以"块在视口里吗"要跟
     * `scrollState.value` 相加后再判断；目标值本身**不要**再加 current。
     *
     * ⚠️ 刚插进来的块当帧还没测量 → 拿不到 y → 这时**什么都不做**，只记进
     * [pendingScrollBlockId]，等它自己的 `onGloballyPositioned` 上报后由 [reportBlockPlaced] 再滚。
     * 曾经的"位置未知就滚到底"兜底是错的：插入点在顶部时会把新块和上方正文一起顶出视口
     * （用户报过：录完音后录音块和第一行都跑到上面看不见了）。
     */
    fun scrollBlockIntoView(blockId: String) {
        // ⚠️ 用**实测**的视口高度，不用 `scrollState.viewportSize`：后者在"内容还不满一屏"
        // （maxValue == 0）时是 0，会把下面的可见性判断整个短路掉。
        val viewport = viewportHeightPx
        val target = blockViewportOffsets[blockId]
        if (target == null || viewport <= 0) {
            // 还没量到（新块 / 视口未测量）：等 [reportBlockPlaced] 那一拍，别猜。
            pendingScrollBlockId = blockId
            return
        }
        val current = scrollState.value
        // 上下各留一点余量：块正好贴住视口边沿时，光标那行其实已经贴边/被圆角切了。
        val top = current + scrollSlackPx
        val bottom = current + viewport - scrollSlackPx
        if (target < top || target > bottom) {
            val destination = (target - scrollSlackPx)
                .coerceIn(0, maxOf(scrollState.maxValue, 0))
            coroutineScope.launch { scrollState.animateScrollTo(destination) }
        }
    }

    /**
     * 各块 `onGloballyPositioned` 的收口：记下实测 y；如果它正是"等位置的待滚目标"，
     * 就在这一拍（layout 之后）把它滚进视口。
     */
    fun reportBlockPlaced(blockId: String, y: Int) {
        blockViewportOffsets[blockId] = y
        if (pendingScrollBlockId == blockId) {
            pendingScrollBlockId = null
            scrollBlockIntoView(blockId)
        }
    }

    /**
     * 把 [factory] 造出来的一批**新块**按顺序插到当前光标处（图 / 语音共用）。
     *
     * 为什么传的是"造块的 lambda"而不是现成的块：id 必须**在 transform 内部**发出来
     * （图片那条老注释说的"复用同一份实例会让两块撞 id"）。取消（空列表）时什么都不做。
     *
     * ⚠️ 它必须声明在 [insertImagesAtCursor] / [insertAudioAtCursor] **之前**：
     * Kotlin 的**局部函数不支持前向引用**（编译报 Unresolved reference）——
     * 与"局部 lambda 不能向前引用"是同一条规则。
     */
    fun insertBlocksAtCursor(factory: () -> List<DraftBlock>) {
        val targetId = targetTextBlockId()
        // ⚠️ `onBlocksChange` 的 transform 是在快照之外跑的纯函数，所以在**这里**（当帧）读
        // 光标与块顺序，别在 transform 里再读一次外层快照。
        val cursor = targetId?.let { latestValues[it]?.selection?.start } ?: 0
        /**
         * 这次插进来的**第一个**块（图 / 语音）：滚它，不滚后面那段文字。
         *
         * 为什么不滚 tail：用户想看的是刚插进来的东西；tail 只是"接着打字"的落点，
         * 把它滚进视口反而可能把刚插的媒体块顶出视口上沿。
         */
        var scrollTargetId: String? = null
        onBlocksChange { list ->
            val newBlocks = factory()
            if (newBlocks.isEmpty()) return@onBlocksChange list
            scrollTargetId = newBlocks.first().id
            // 落点的**块下标**在这里现算一次：外面那一帧读到的顺序可能已经被另一次改动
            // （比如刚删了一张图）挪过位置。
            val index = list.indexOfFirst { it.id == targetId }
            val block = list.getOrNull(index) as? DraftBlock.Text
            // 光标还给"新块后面那段文字的开头"，用户可以接着写。
            var cursorTargetId: String? = null
            val next = list.toMutableList().apply {
                if (block != null) {
                    // 光标处切块：前段留在原块、后段进新块、新块夹在中间 ——
                    // 这正就是"插到正文里的光标处"。光标在开头得到 [空文字, 图, 后段]，
                    // 在结尾得到 [前段, 图, 空文字]。
                    val cut = cursor.coerceIn(0, block.value.length)
                    val head = block.copy(value = block.value.substring(0, cut))
                    val tail = newEmptyDraftTextBlock().copy(value = block.value.substring(cut))
                    set(index, head)
                    addAll(index + 1, newBlocks)
                    add(index + 1 + newBlocks.size, tail)
                    cursorTargetId = tail.id
                } else {
                    // 兜底：没有文字块可切（理论上不可能，见空块不变式）→ 新块追加到最后，
                    // 并在**新块后面**补一个空文字块当落点，否则用户会发现图下面打不了字。
                    addAll(newBlocks)
                    if (lastOrNull() !is DraftBlock.Text) {
                        val tail = newEmptyDraftTextBlock()
                        add(tail)
                        cursorTargetId = tail.id
                    }
                }
                if (cursorTargetId == null) {
                    // 现在新块后面的那一块（有后段就是它；兜底路径里就是刚补的空文字块）。
                    val after = getOrNull(index + newBlocks.size)
                    if (after is DraftBlock.Text) cursorTargetId = after.id
                }
            }
            cursorTargetId?.let { id -> pendingCursor[id] = 0 }
            next
        }
        // 新块当帧还没测量：先记成待滚目标，等它自己的 onGloballyPositioned 上报位置后再滚。
        scrollTargetId?.let { id -> pendingScrollBlockId = id }
    }

    /** 把 [paths] 里的图**按顺序**插到当前光标处（取消选图 = 空列表 = 什么都不做）。 */
    fun insertImagesAtCursor(paths: List<String>) {
        if (paths.isEmpty()) return
        // 图片块在**每次** transform 里新建：id 必须由 `newDraftBlockId()` 单调发出来，
        // 复用同一份 `DraftBlock.Image` 实例会让两块撞 id（Compose 的 `key` 会当成同一块）。
        insertBlocksAtCursor { paths.map { path -> DraftBlock.Image(id = newDraftBlockId(), path = path) } }
    }

    /** 把一段刚录完的语音插到当前光标处（§0.16.21）。 */
    fun insertAudioAtCursor(path: String, durationMs: Long) {
        if (path.isBlank()) return
        insertBlocksAtCursor {
            listOf(DraftBlock.Audio(id = newDraftBlockId(), path = path, durationMs = durationMs))
        }
    }

    /**
     * 删掉一个图片块（✕ 或退格）：块的增删 + "要不要顺手删文件"收在**一处**。
     *
     * 判据是**集合成员**（[existingImageFileNames]）而不是"路径长得像什么"：
     * 只有"确实不是这条条目原有的文件"才删（§0.16.18）。
     */
    fun removeImageBlock(block: DraftBlock.Image) {
        onBlocksChange { list -> list.filterNot { it.id == block.id } }
        if (block.path !in existingImageFileNames) onRemoveNewImage(block.path)
    }

    /** 删掉一个语音块：与 [removeImageBlock] 完全对称（§0.16.21）。 */
    fun removeAudioBlock(block: DraftBlock.Audio) {
        if (HistoryAudioPlayback.isPlaying(resolveAudioPath(block.path))) HistoryAudioPlayback.stop()
        onBlocksChange { list -> list.filterNot { it.id == block.id } }
        if (block.path !in existingAudioFileNames) onRemoveNewAudio(block.path)
    }

    /* ---------------- 录音（§0.16.21） ---------------- */

    val context = LocalContext.current
    val recordState = StashAudioRecordSession.state
    val recordedPath = StashAudioRecordSession.finishedPath
    val recordErrorResId = StashAudioRecordSession.errorResId
    /**
     * 哪一层的块编辑器发起了录音，就由哪一层取结果（与 [HistoryVoiceMicButton] 认领识别结果
     * 是同一个套路）：只有"之后真的开了新会话"才认领，避免两个编辑入口互相抢。
     */
    var claimedRecordSessionId by remember { mutableIntStateOf(-1) }
    LaunchedEffect(claimedRecordSessionId, recordedPath, recordErrorResId) {
        if (claimedRecordSessionId < 0) return@LaunchedEffect
        if (StashAudioRecordSession.sessionId <= claimedRecordSessionId) return@LaunchedEffect
        if (recordedPath.isNotBlank()) {
            claimedRecordSessionId = -1
            insertAudioAtCursor(recordedPath, StashAudioRecordSession.finishedDurationMs)
            StashAudioRecordSession.consumeResult()
        } else if (recordErrorResId != 0) {
            claimedRecordSessionId = -1
            onVoiceError(recordErrorResId)
            StashAudioRecordSession.consumeResult()
        }
    }
    /** 录音计时用的"现在"：只在录音期间每 200ms 推进一次（不录音时一次都不写状态）。 */
    var recordElapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(recordState) {
        if (recordState != StashAudioRecordSession.State.Recording) {
            recordElapsedMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            recordElapsedMs = (System.currentTimeMillis() - StashAudioRecordSession.startedAtMs)
                .coerceAtLeast(0L)
            delay(200)
        }
    }

    /** 语音识别结果插到当前光标处（§0.16.16；老行为是一律追加到末尾）。 */
    fun insertVoiceAtCursor(recognized: String) {
        if (recognized.isEmpty()) return
        val targetId = targetTextBlockId() ?: return
        val current = blocks.lastOrNull { it.id == targetId } as? DraftBlock.Text ?: return
        val cursor = (latestValues[targetId]?.selection?.start ?: current.value.length)
            .coerceIn(0, current.value.length)
        onBlocksChange { list ->
            // ⚠️ 这个 lambda 必须**返回列表**（草稿的 `updateBlocks` 要拿它当新的块序列）——
            // 所以先算出新列表，再做"记下光标"这个副作用，别让赋值语句成为最后一个表达式。
            val next = list.map { item ->
                if (item.id == targetId && item is DraftBlock.Text) {
                    item.copy(value = item.value.take(cursor) + recognized + item.value.drop(cursor))
                } else {
                    item
                }
            }
            // 光标跟到识别结果后面：不然吐完一句，下一句会插到刚才那句前面去。
            pendingCursor[targetId] = cursor + recognized.length
            // §0.16.17：把这次改动转给"语音按钮在本层外面"的入口（编辑条）。
            // 传**算好的**新列表而不是让外面自己再算一遍：插入点是本层的私有状态
            // （每块最近一次选区），外面拿不到，重算必然算错地方。
            onTextInsertedAtCursor?.invoke(next)
            next
        }
        // 识别结果插在当前块的光标处：这一块本来就在视口里多半可见，只有被挤出视口时才滚回来。
        // （原来靠 `pendingCursor` 消费时的 `autoScroll`；现在滚动目标改成"插入的那一块"，
        //  图 / 语音不会再顺手把 tail 一起滚进来。）
        scrollBlockIntoView(targetId)
    }

    /**
     * §0.16.23：「点正文最后一块**下方的空白**」→ 把光标送进正文末尾，顺手滚过去。
     *
     * 两种情况分开处理（都满足"点空白就能在图片下面继续写"）：
     * - 末尾**已经是空文字块**（收尾规则 `normalizeDraftBlocks` 保证媒体块后面总有它）→
     *   **不再叠一个空块**，只把光标送进去。两个挨着的空块对用户没有任何意义，
     *   而 `pendingCursor` 那条既有机制已经负责"下一帧要焦点"，滚动由下面的
     *   [scrollBlockIntoView] 显式做（它对新块会等到位置上报后再滚）。
     * - 末尾是**有文字的文字块**（用户写满了最后一行、下面还有空白）→ 追加一个空文字块再送光标，
     *   这正是需求里的"点空白 → 追加一个空文本块"。
     *
     * 为什么用 `pendingCursor` 而不是直接 `focusRequester.requestFocus()`：新块当帧还不存在
     * （组合都没跑），`requestFocus()` 会静默失败 —— 见 [pendingCursor] 的 KDoc。
     */
    fun focusOrAppendTailTextBlock() {
        val last = blocks.lastOrNull()
        if (last is DraftBlock.Text && last.value.isEmpty()) {
            latestValues[last.id] = TextFieldValue("", selection = TextRange(0))
            pendingCursor[last.id] = 0
            scrollBlockIntoView(last.id)
            return
        }
        // ⚠️ id 必须由 `newDraftBlockId()` 在 transform **内部**发出来（与插图的规则同一条），
        // 但落光标要在这个块真的进列表之后（所以先记下来再写 pendingCursor）。
        var createdId: String? = null
        onBlocksChange { list ->
            val tail = newEmptyDraftTextBlock()
            createdId = tail.id
            list + tail
        }
        createdId?.let { id ->
            pendingCursor[id] = 0
            scrollBlockIntoView(id)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (onAddImage != null || showVoiceButton) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 「＋ 图片」：保持老位置（正文上方、标签行之前）—— 两个入口的位置一致，
                // 用户才不会以为"这里能加图、那里不能"。
                //
                // ⚠️ 这里**只**有那枚胶囊：老实现下面还跟着一排缩略图，现在图直接进正文（§0.16.16），
                // 再排一排"框外的图"就等于同一张图在界面上出现两次。
                if (onAddImage != null) {
                    HistoryChip(
                        label = stringResource(R.string.stash_composer_image_add),
                        dotColor = null,
                        selected = false,
                        onClick = {
                            // §0.16.12：overlay 里不能直接拉系统选图，走中转 Activity（回来的是本地文件路径）。
                            // §0.16.14：先把面板窗挂起（它是无障碍覆盖层，不挂起会盖在相册上面），
                            // 回调里**第一件事**就是恢复 —— 取消（picked 为空）也要恢复。
                            //
                            // 插入点由本层自己算（它手上有每块的光标/焦点）：这里**不能**
                            // `focusManager.clearFocus()`，那会把"要插到哪个块"这条信息抹掉。
                            onAddImage { picked -> insertImagesAtCursor(picked) }
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (showVoiceButton) {
                    // §0.16.21：块编辑器里的麦克风是**录音**（点按）/ 语音识别（长按）。
                    // 识别结果插到**当前光标处**（插入只有本层算得准，所以按钮也留在本层）。
                    if (recordState == StashAudioRecordSession.State.Recording) {
                        Text(
                            text = "⏺ " + formatAudioDuration(recordElapsedMs),
                            style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.meta),
                            color = MiuixTheme.colorScheme.error,
                            maxLines = 1,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    HistoryVoiceMicButton(
                        onResult = { recognized -> insertVoiceAtCursor(recognized) },
                        onError = onVoiceError,
                        size = 34.dp,
                        iconSize = 16.dp,
                        onTapRecord = {
                            if (StashAudioRecordSession.state == StashAudioRecordSession.State.Recording) {
                                com.slideindex.app.voice.StashVoiceRecorderAccess.recorder?.stop(context)
                            } else {
                                // 先认领"这一轮"，再开录：结果回来时才知道该由哪一层插块。
                                claimedRecordSessionId = StashAudioRecordSession.sessionId
                                // 互斥的另一半：录音期间不许有播放（播放器是单例，这里直接叫停）。
                                HistoryAudioPlayback.stop()
                                com.slideindex.app.voice.StashVoiceRecorderAccess.recorder?.start(context)
                            }
                        },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(
                    min = HistoryComposerBodyMinHeightDp,
                    max = HistoryComposerBodyMaxHeightDp,
                )
                // §0.16.18：块一多就超出视口，必须能滚（否则图底下的东西看不见也滑不到）。
                // 视口高度仍然是 160..320（见 KDoc）：无限高会把「存下」/「保存」顶出屏幕。
                //
                // 滚动条（用户要求"超出显示范围时右侧要有滚动条"）：
                // ⚠️ 必须放在 `.verticalScroll` **之前**（外层）—— 这样尺寸是**视口**，
                // 而它的 `drawContent()` 才画得到被滚动裁切后的内容；放在之后会拿到内容总高。
                .composerScrollbar(
                    scrollState = scrollState,
                    color = theme.sub.copy(alpha = 0.55f),
                )
                .verticalScroll(scrollState)
                // 记下**实测**视口高度：它比"按 dp 换算"准（窗口缩放 / 字体大小都会影响）。
                .onGloballyPositioned { coords -> viewportHeightPx = coords.size.height },
            // 块间距 7dp：太小看不出"这是两块"（尤其空块），太大就断成两个输入框了。
            verticalArrangement = Arrangement.spacedBy(HistoryComposerBlockGap),
        ) {
            // §0.16.23：块序列单独包一层 —— 只为了量出"块自己有多高"（`blocksHeightPx`），
            // 用来算下面那枚"尾部空白落点"该占多高。位置语义不变：第一层子项 y 仍从 0 开始，
            // `blockViewportOffsets`（`positionInParent()`）算出来的滚动目标与原来逐字一致。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords -> blocksHeightPx = coords.size.height },
                verticalArrangement = Arrangement.spacedBy(HistoryComposerBlockGap),
            ) {
                blocks.forEachIndexed { index, block ->
                    when (block) {
                        is DraftBlock.Text -> key(block.id) {
                            HistoryComposerTextBlock(
                                block = block,
                                // 整份正文只剩这一块时才显示占位提示（老实现的判据是 `text.isEmpty()`）。
                                showHint = hint != null && blocks.size == 1 && block.value.isEmpty(),
                                hint = hint,
                                onValueChange = { updated ->
                                    latestValues[block.id] = updated
                                    onBlocksChange { list ->
                                        list.map { item ->
                                            if (item.id == block.id && item is DraftBlock.Text) {
                                                item.copy(value = updated.text)
                                            } else {
                                                item
                                            }
                                        }
                                    }
                                },
                                onFocusChanged = { focused -> if (focused) lastFocusedBlockId = block.id },
                                onBackspaceOnLeadingEdge = {
                                    // 位置 0 的退格 = 想删掉**前一个块**；只有前一个块是媒体块（图 / 语音）时
                                    // 才需要这套特殊处理（文字块之间的退格交给 IME 自己）。
                                    val previous = blocks.getOrNull(index - 1)
                                    if (previous is DraftBlock.Image || previous is DraftBlock.Audio) {
                                        when (previous) {
                                            is DraftBlock.Image -> removeImageBlock(previous)
                                            is DraftBlock.Audio -> removeAudioBlock(previous)
                                            else -> Unit
                                        }
                                        // 焦点回到前一个文字块末尾（那块就是光标左边那段文字）。
                                        // 它很可能已经被删掉的媒体块顶到视口上方，顺手滚回来。
                                        val before = blocks.getOrNull(index - 2) as? DraftBlock.Text
                                        if (before != null) {
                                            pendingCursor[before.id] = before.value.length
                                            scrollBlockIntoView(before.id)
                                        }
                                    }
                                },
                                focusRequester = focusRequester,
                                // lint（RememberInComposition）：`FocusRequester()` 不能在组合期直接 new，
                                // 必须挂在 `remember` 上 —— 按 block.id 记住即可（这份映射本身只增不删，
                                // 所以 remember 里的实例不会和映射里的漂移）。
                                ownFocusRequester = remember(block.id) {
                                    focusRequesters.getOrPut(block.id) { FocusRequester() }
                                },
                                pendingCursor = pendingCursor,
                                onSubmitKey = onSubmitKey,
                                focusDelegation = focusDelegation,
                                // §0.16.18：上报本块在**内容坐标系**里的 y（"插入后滚到目标块"要用它算位置）。
                                // 新块首次上报时若正是"待滚目标"，[reportBlockPlaced] 会顺手把它滚进视口。
                                onBlockPlaced = { y -> reportBlockPlaced(block.id, y) },
                            )
                        }
    
                        is DraftBlock.Image -> key(block.id) {
                            DraftBlockEditorImage(
                                path = resolveImagePath(block.path),
                                onRemove = { removeImageBlock(block) },
                                // §0.16.18：图片块也要上报 y —— 插入后滚的**就是图片本身**
                                // （上面 `insertBlocksAtCursor` 的待滚目标是新插入的第一块，
                                //  不是后面补的空文字块；图片 120dp，不滚它就会被上沿切掉）。
                                onBlockPlaced = { y -> reportBlockPlaced(block.id, y) },
                                // §0.16.19：按显示宽度解码（原来固定 480px，整宽渲染时糊）。
                                imageTargetPx = imageTargetPx,
                                // §0.16.22：✎ 进内置编辑器（null = 这个入口不给这枚按钮）。
                                onEdit = onEditImage?.let { edit -> { edit(block.id) } },
                            )
                        }
    
                        // §0.16.21：语音块 —— 整宽胶囊、点了就地播、右侧 ✕ 删（与图片块平级）。
                        is DraftBlock.Audio -> key(block.id) {
                            DraftBlockEditorAudio(
                                path = resolveAudioPath(block.path),
                                durationMs = block.durationMs,
                                onRemove = { removeAudioBlock(block) },
                                onBlockPlaced = { y -> reportBlockPlaced(block.id, y) },
                            )
                        }
                    }
                }
            }
            // §0.16.23：「正文最后一块下方的空白」是一枚**落点**（用户报"图片在最底部时光标到不了图片下面"）。
            //
            // 高度 = 正文视口最小高度 − 块序列实测高度（下限 = 空文字块的最小高度 40dp）：
            // "内容不满一屏时，最后一块下面那一整片空白"于是**整片**都点得到 —— 而不是只有紧贴块的那几像素，
            // 也不是把整块正文都变成点击区（后者会连"点图/点文字块"都算成新建块）。
            //
            // 无缩进陷阱：`blocksHeightPx` 量的是**块本身**（本 Box 在外面），不构成"量了又改高度"的回环。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tailTapZoneHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { focusOrAppendTailTextBlock() },
                    ),
            )
        }
    }
}

/**
 * 正文滚动条：内容超出视口时，在正文右侧画一根 3dp 的圆角滑块（用户要求：超出显示范围要有滚动条）。
 *
 * 为什么用 `drawWithContent` 而不是叠一层 composable：
 * ① **不占布局**：正文块宽度、插入光标、块上报的 y 全不受影响；
 * ② 滚动时读的是 `scrollState.value`，而**绘制阶段**的状态读取只会让这一层失效重画，
 *    不会把整片正文（每块一个 `BasicTextField`）拖进重组。
 *
 * 位置：正文**外侧**的右内边距里（两个宿主 `HistoryComposerModal` / `HistoryPanelEditBar`
 * 都是 26dp 右内边距），不压住正文；卡片的 `clip(shape)` 在这之外，不会被裁掉。
 *
 * 刻意**不接手势**：给触摸带会抢走正文右缘"点一下落光标"的命中区；
 * 面板/弹窗都是小视口，滑动滚动已经够用，这里只做"还有内容"的指示。
 */
private fun Modifier.composerScrollbar(
    scrollState: ScrollState,
    color: Color,
): Modifier = drawWithContent {
    drawContent()
    val maxScroll = scrollState.maxValue
    // 内容没超出视口：不画（用户要的就是"超出显示范围才出现"）。
    if (maxScroll <= 0) return@drawWithContent
    val viewport = size.height
    if (viewport <= 0f) return@drawWithContent
    // 内容总高 = 视口 + 可滚距离；滑块长度按占比算，最短 [HistoryComposerScrollbarMinThumb]。
    val naturalThumb = viewport * (viewport / (viewport + maxScroll))
    val thumbHeight = naturalThumb.coerceIn(
        minimumValue = HistoryComposerScrollbarMinThumb.toPx().coerceAtMost(viewport),
        maximumValue = viewport,
    )
    val thumbOffset = (viewport - thumbHeight) *
        (scrollState.value / maxScroll.toFloat()).coerceIn(0f, 1f)
    val barWidth = HistoryComposerScrollbarWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width + HistoryComposerScrollbarGutter.toPx(), thumbOffset),
        size = Size(barWidth, thumbHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
    )
}

/**
 * 正文里的一个**文字块**（§0.16.16）—— 也就是一个 `BasicTextField`。
 *
 * 四件事都在这里收口（别挪到外面去）：
 * ① `TextFieldValue` 是**块内** state：一次输入的回环（打字 → 上抛 → 父级 → 回传）
 *    如果每次都拿父级的值重建内部值，光标会被重置到开头（中文输入法尤其明显），
 *    所以只认"父级的值和我们最后发出去的不一样"（真正的外部改动，比如保存成功清空）；
 * ② `pendingCursor`：插入/删除之后由 [DraftBlockEditorSurface] 写下的"下一帧光标该在哪"；
 * ③ 退格：**位置 0** 且这一帧"文本没变"时，把事件交给上一层的 [onBackspaceOnLeadingEdge]
 *    （删前一个图片块）。判据必须写在这里 —— 只有这里同时拿得到
 *    `TextFieldValue` 的 `selection` 与 `composition`；
 * ④ **回车策略**（§0.16.17 回归修复）：[onSubmitKey] 为 null 时是 `ImeAction.Default`
 *    **且完全不给 `onDone`** —— 于是回车就只是"插入一个换行"（编辑条要的就是这个）；
 *    有值时才挂 `ImeAction.Done` + `onDone`（弹窗要的"回车存下"）。
 *    ⚠️ 这里**不能**两处都硬编码：早先弹窗与编辑条共用一个写死 Done 的输入框时，
 *    编辑条的回车被吃掉、变成"确认"，用户原话就是"输入法没法换行了"。
 */
@Suppress("DEPRECATION")
@Composable
private fun HistoryComposerTextBlock(
    block: DraftBlock.Text,
    /** 是不是唯一一块且为空（占位提示）。 */
    showHint: Boolean,
    /** 占位提示的文案（null = 这个入口不给占位提示）。 */
    hint: String?,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    /**
     * 位置 0 退格。
     *
     * 为什么只给一个"发生了"的信号、不在这里直接删：切块/删块的规则只在
     * [DraftBlockEditorSurface] 那一层维护（它同时管焦点衔接），这里只当"事件源"。
     */
    onBackspaceOnLeadingEdge: () -> Unit,
    /** 弹窗那边"打开时抢一次焦点"用的共享句柄（编辑条传 null）。 */
    focusRequester: FocusRequester?,
    ownFocusRequester: FocusRequester,
    pendingCursor: MutableMap<String, Int>,
    /** 有值 = 回车提交；null = 回车换行（见函数 KDoc ④）。 */
    onSubmitKey: (() -> Unit)?,
    /** 块**已经**被聚焦时，要不要再抢一次焦点（弹窗要、编辑条不要，见 surface 的 KDoc）。 */
    focusDelegation: Boolean,
    /** 上报本块在**内容坐标系**里的 y（§0.16.18 的"插入后滚到目标"要用）。 */
    onBlockPlaced: (Int) -> Unit,
) {
    val theme = historyTheme()
    /** 与 `KeyboardOptions` 同一处决定：null ⇒ 回车换行（Default），非 null ⇒ 回车提交（Done）。 */
    var fieldValue by remember(block.id) {
        mutableStateOf(TextFieldValue(block.value, selection = TextRange(block.value.length)))
    }
    /** 上一次"我们自己发出去"的文本：用来分辨"输入回环"和"父级主动改动"。 */
    var lastEmitted by remember(block.id) { mutableStateOf(block.value) }
    /** 这个块当前有没有焦点（决定 [pendingCursor] 消费时要不要真的去抢焦点）。 */
    var isFocused by remember(block.id) { mutableStateOf(false) }
    if (block.value != lastEmitted) {
        // 外部改动（保存成功清空 / 撤销）：整块的值与光标一起重建。
        fieldValue = TextFieldValue(block.value, selection = TextRange(block.value.length))
        lastEmitted = block.value
    }
    // 消费"下一帧光标该在哪"：等这个块真的组合出来（`requestFocus` 才有节点可要焦点）再要。
    // ⚠️ 只在组合里**取走**（`remove` 有副作用但对同一个 id 幂等）：写成 `LaunchedEffect` 会晚一帧，
    // 那时用户可能已经在别处打字，光标会被拽回来。
    pendingCursor.remove(block.id)?.let { position ->
        val clamped = position.coerceIn(0, fieldValue.text.length)
        fieldValue = fieldValue.copy(selection = TextRange(clamped))
        if (!isFocused || focusDelegation) {
            SideEffect {
                // 组合结束、节点已经挂上去了，这时要焦点才要得到。
                runCatching { ownFocusRequester.requestFocus() }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // §0.16.18：上报本块在**内容坐标系**里的 y（`positionInParent` 相对块列表，
            // 滚动时不变；"插入后滚到目标块"由调用方换算视口位置）。
            .onGloballyPositioned { coords -> onBlockPlaced(coords.positionInParent().y.toInt()) },
    ) {
        if (showHint && hint != null) {
            Text(
                text = hint,
                style = androidx.compose.ui.text.TextStyle(fontSize = 14.5.sp),
                color = theme.sub,
                maxLines = 1,
            )
        }
        BasicTextField(
            // ⚠️ 用 `TextFieldValue` 的**已废弃**重载：选它纯粹是为了拿到 `selection`
            // （"图插到光标处"和"位置 0 退格"都靠它），本版本没有 state 版重载。
            value = fieldValue,
            onValueChange = { updated ->
                val previous = fieldValue
                onValueChange(updated)
                fieldValue = updated
                lastEmitted = updated.text
                // ---- 退格删图（§0.16.16）----
                // 光标在**位置 0**、且是折叠选区（没在选字）时按退格：IME 没有任何字符可删，
                // 于是回调里 `text` 与上一帧一模一样 —— 这个"空转的一次 onValueChange"
                // 就是"位置 0 的退格"。它比 `onKeyEvent` 稳：软键盘的退格在一些 IME 下
                // 根本不上报按键（只有 `onValueChange` 会来）。
                //
                // 为什么不直接用"文本没变"当判据：那会把**光标移动**也算成退格。三个附加条件排掉它：
                // ① 上一帧本来就非空 —— 空块上的退格必须是**静默无效**（否则两个空块之间会来回删）；
                // ② 没有输入法组合串 —— 组合期间 IME 会为了刷新组合区而空转，那时删图是误删；
                // ③ 上一帧光标不在 0 —— "上一帧就在 0 且文本没变"更像点选/移动光标，不认它。
                //
                // ⚠️ 没有把握的地方（简报里也写了）：② 是**两头都判**的，某些输入法在
                // "组合串刚被退格清空"和"这次退格事件"之间会有一帧仍带着组合串，
                // 那一帧会被这里放过 —— 表现就是"要再按一次退格才删掉图"。
                // 宁可多按一次，也不要在用户还在选词时把图删了。
                val backspaceOnLeadingEdge = updated.text == previous.text &&
                    updated.selection.collapsed &&
                    updated.selection.start == 0 &&
                    previous.text.isNotEmpty() &&
                    previous.selection.start != 0 &&
                    updated.composition == null &&
                    previous.composition == null
                if (backspaceOnLeadingEdge) onBackspaceOnLeadingEdge()
            },
            singleLine = false,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = theme.text,
            ),
            cursorBrush = SolidColor(theme.accentSolid),
            // §0.16.17：回车策略跟着 [onSubmitKey] 走 —— null 就是"允许换行、不挂 Done"。
            keyboardOptions = KeyboardOptions(
                imeAction = if (onSubmitKey == null) ImeAction.Default else ImeAction.Done,
            ),
            keyboardActions = if (onSubmitKey == null) {
                KeyboardActions()
            } else {
                // ⚠️ `onDone` 的类型是 `KeyboardActionScope.() -> Unit`（**不带参数**，
                // `KeyboardActionScope` 只是接收者）：写成 `{ _ -> … }` 编译不过。
                KeyboardActions(onDone = { onSubmitKey() })
            },
            modifier = Modifier
                .fillMaxWidth()
                // 空块的最小高度：不然图下面那一块只有 0 高，点不到光标（§0.16.16 的硬要求）。
                .heightIn(min = HistoryComposerEmptyBlockMinHeight)
                .focusRequester(ownFocusRequester)
                .then(
                    // 编辑条那边没有"打开时抢焦点"的共享句柄，不挂空 modifier 也行。
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                )
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    onFocusChanged(state.isFocused)
                },
        )
    }
}

/**
 * 正文里的一张**图片块**（§0.16.16）：占正文整宽、高 [HistoryComposerBodyImageHeight]、
 * 圆角同正文，右上角 ✕ 删除。
 *
 * 为什么不用老那枚 64dp 缩略图（[HistoryComposerThumbnail]）：那是"框外一排小图"的尺寸，
 * 放正文里既看不出是哪张、也表达不了"它和文字平级"。解码相应放大
 * （见 [decodeDraftImage]，仍然 `inSampleSize` 采样，不给内存添乱）。
 *
 * ⚠️ [path] 是**已经解析过**的可解码路径（暂存夹文件名 → 绝对路径那一步在
 * [DraftBlockEditorSurface] 的 `resolveImagePath` 里做完了）。
 *
 * §0.16.18：**解不出来时不再留一块空白** —— 空白块让用户以为"图插进去了"，实际上存下时会被跳过。
 * 现在中间给一个「加载失败」占位（复用现成的 `stash_save_failed` 文案，本仓库不许新增字符串 key），
 * ✕ 仍然在，用户能看见"这里是坏的"并自己删掉重选。
 *
 * §0.16.19：解码采样目标由调用方按**显示宽度**给（[imageTargetPx]），不再是写死的 480px ——
 * 这块图占正文整宽（≈面板宽），480px 放大到 1080px 就是用户说的"太糊"。
 */
@Composable
private fun DraftBlockEditorImage(
    path: String,
    onRemove: () -> Unit,
    /** 上报本块在**内容坐标系**里的 y（§0.16.18 的"插入后滚到目标"要用）。 */
    onBlockPlaced: (Int) -> Unit,
    /** 解码采样目标（长边像素，§0.16.19）。 */
    imageTargetPx: Int,
    /**
     * 角落那枚 **✎**（§0.16.22）：非 null = 显示，点了把它**这一块**送进内置图片编辑器。
     * 见 `DraftBlockEditorSurface.onEditImage` 的 KDoc（为什么只有就地编辑条给这枚按钮）。
     */
    onEdit: (() -> Unit)? = null,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.sm)
    // `remember(path, imageTargetPx)`：目标变了要重新解码（不同宽度的宿主）。
    val bitmap = remember(path, imageTargetPx) { decodeDraftImage(path, imageTargetPx) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HistoryComposerBodyImageHeight)
            .clip(shape)
            // 解码失败也留一块同样大小的底：用户至少知道"这里有张图"，而不是正文莫名断开。
            .background(theme.fieldBg)
            .onGloballyPositioned { coords -> onBlockPlaced(coords.positionInParent().y.toInt()) },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // ⚠️ 空白是最糟的反馈：用户会以为"图在这儿了"，直到保存后打开才发现没有。
            // 文案复用 `stash_save_failed`（本仓库不许新增字符串 key）。
            Text(
                text = stringResource(R.string.stash_save_failed),
                style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.meta),
                color = theme.sub,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(theme.text.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                // 无障碍文案复用现成的（本仓库不许新增字符串 key）。
                contentDescription = stringResource(R.string.stash_tag_delete),
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
        // §0.16.22：✎ 放在**另一角**（TopStart）而不是挨着 ✕ —— 这两枚一个是"改这张"、
        // 一个是"删这块"，紧挨着误触的代价是删掉用户刚选的那张图，不值得为省一个角去冒。
        if (onEdit != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(theme.text.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    // 无障碍文案复用现成的 `stash_action_edit`（"编辑"），不为这枚按钮新增 key。
                    contentDescription = stringResource(R.string.stash_action_edit),
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** 与 `MiuixExpandableSearch` 的 `ExpandableSearchFocusDelayMs` 同值。 */
private const val HistoryComposerFocusDelayMs = 180L

/** 正文块流的最小高度（沿用老输入框的 160dp：弹窗尺寸不该因为"正文变块"而跳）。 */
private val HistoryComposerBodyMinHeightDp = 160.dp

/** 正文块流的最大高度（沿用老输入框的 320dp：再多内容也先让"存下"键留在屏内）。 */
private val HistoryComposerBodyMaxHeightDp = 320.dp

/** 块间距（6–8dp 这条带里取中间：太小看不出是两块，太大就断成两个输入框了）。 */
private val HistoryComposerBlockGap = 7.dp

/** 空文字块的最小高度：保证"图下面那一块"也点得到光标。 */
private val HistoryComposerEmptyBlockMinHeight = 40.dp

/**
 * "滚到目标块"时上下留的余量（§0.16.18）。
 *
 * 为什么需要：块正好贴住视口上/下沿时，光标那一行其实已经被圆角/边缘切掉了。
 * 24dp 是"看得出整块、又不会为了露一行把整屏都滚走"的值。
 *
 * ⚠️ 类型必须是 [Dp]：比较的另一边（`viewportHeightPx` / 块 y）全是 **px**，
 * 早先它是裸 Int（24），在 3x 屏上只剩 8dp，等于余量被吃掉三分之二。
 */
private val HistoryComposerScrollSlack = 24.dp

/** 正文滚动条的宽度（dp）：细到不抢内容，又足够看清。 */
private val HistoryComposerScrollbarWidth = 3.dp

/**
 * 滚动条离正文右缘的距离（dp）。
 *
 * 两个宿主（`HistoryComposerModal` / `HistoryPanelEditBar`）的卡片右内边距都是 26dp，
 * 所以 8dp 还落在内边距里：不压正文、也不会被卡片的 `clip(shape)` 裁掉。
 */
private val HistoryComposerScrollbarGutter = 8.dp

/** 滚动条滑块的最短长度（dp）：内容再长也要看得见（太短会被当成脏点）。 */
private val HistoryComposerScrollbarMinThumb = 28.dp

/** 正文里图片块的高度。 */
private val HistoryComposerBodyImageHeight = 120.dp

/** 加号弹窗里图片缩略图的边长。 */
private val HistoryComposerThumbnailSize = 64.dp

/** 缩略图解码的采样目标（§0.16.19）：64dp 在 2.5~3x 屏上约 160~192px，留余量取 320。 */
private const val HistoryComposerThumbnailTargetPx = 320

/**
 * 图片块解码采样目标的**上限**（§0.16.19 引入，§0.16.20 从 1080 提到 **1440**）。
 *
 * 为什么提到 1440：正文图块是**整宽**渲染的，而"屏幕截图"这类**竖长图**高度很大，
 * 用户想看清细节时 1080 那一档仍偏软。常见手机（1080 宽）上目标由 `min(屏宽, 420dp)`
 * 决定（≈1080），**只有大屏/折叠屏才吃到 1440** —— 所以这次提档对小屏几乎零成本，
 * 对大屏才真正生效。
 *
 * 最坏情况估算（ARGB_8888 = 4 字节/像素）：
 * - 1440 宽、4:3 → 1440×1080 ≈ **6.2MB**（1080 那一档约 3.5MB）；
 * - 一条闪念塞 10 张图 → 上界 ~62MB（弹窗草稿里每张图一个 `remember(path, imageTargetPx)`）。
 *   这是**上界**：正文视口只有 160..320dp 高（≈2 张 120dp 图 + 文字），同时在屏的通常 1~3 张，
 *   滚出视口的块随 Composable 回收释放。
 * - 所以上限卡在 1440：**多图条目不会被一刀切地放大**，而竖长图的细节够看。
 */
private const val HistoryComposerImageTargetMaxPx = 1440

/** 与 `historyPanelWidthDpOf`（`HistoryPanelUi.kt`）里那个面板宽度上限保持同一个值。 */
private val HistoryComposerPanelMaxWidth = 420.dp

/**
 * 正文图片块的解码采样目标（§0.16.19 引入，§0.16.20 提高上限）。
 *
 * 依据（用户给的算式：**显示尺寸 × density × 1.5~2**）：
 * - 显示尺寸 = 正文图块的实际尺寸。它占**面板整宽**（`fillMaxWidth`），宽度上限是
 *   `HistoryComposerPanelMaxWidth`（420dp，与 `panelWidthOf` 同一个值）；
 *   高度是固定 120dp，所以对**竖长截图**来说"较大边"永远是宽 —— 按宽度给目标就够了；
 * - ×2 是因为 `inSampleSize` 只能取 **2 的幂**：目标要超前，否则会掉到一半（÷2）那一档，
 *   "掉一半"正是糊的来源（这条与卡片那套 [HistoryCardImageOversample] 同一个理由）；
 * - 上限 [HistoryComposerImageTargetMaxPx] 防内存（见那条 KDoc 的估算）。
 *
 * 为什么不直接用 `historyPreviewWidthPx()`（卡片缩略图用的那个）：它内部含
 * "容器旋转就取 min(w,h)" 那套卡片专用兜底，而这里要的是"弹窗正文的整宽上限"，
 * 两者算式不同（差 24dp 与 960 上限）；各自写清楚比互相借用更好维护。
 */
@Composable
private fun draftBlockImageTargetPx(): Int {
    val density = LocalDensity.current
    val container = LocalWindowInfo.current.containerSize
    val screenWidthPx = if (container.width > 0) container.width else container.height
    return with(density) {
        val panelMaxPx = HistoryComposerPanelMaxWidth.roundToPx()
        val displayWidthPx = if (screenWidthPx > 0) minOf(screenWidthPx, panelMaxPx) else panelMaxPx
        (displayWidthPx * HistoryComposerImageTargetOversample)
            .toInt()
            .coerceAtMost(HistoryComposerImageTargetMaxPx)
            .coerceAtLeast(HistoryComposerImageTargetMaxPx / 2)
    }
}

/**
 * 采样目标的"超前系数"（§0.16.19）。
 *
 * 2.0 = 用户给的 1.5~2 里的上界：`inSampleSize` 只有 2 的幂，目标按 1.5 算时
 * 4000px 的图会解到 2000px（够），但 1600px 的图会落到 1600（也够）—— 真正的风险是
 * **刚好卡在档位边界**（比如源图 2200px、目标 1100px → 采样 2 → 1100px，勉强；
 * 源图 2100px、目标 1050px → 采样 2 → 1050px，也勉强）。取 2.0 让"下一档"总是有余量。
 */
private const val HistoryComposerImageTargetOversample = 2.0f

/**
 * 加号弹窗里的一张已选图片（§0.16.12）。
 *
 * 图是 trampoline 解码后落在 cache 里的临时文件，所以这里只用**很小的采样**读缩略图
 * （目标 160px，`remember(path)` 缓存），避免每帧重解码。右上角那枚 ✕ 负责移除。
 *
 * `internal`（而不是 private）：就地编辑条"补图"（§0.16.14）要复用**同一个**缩略图，
 * 免得两份 ✕ 的无障碍文案/尺寸各写一遍走偏。
 *
 * ⚠️ §0.16.17 起**没有任何生产调用点**了：两个编辑入口（弹窗 / 编辑条）的正文都是块编辑器
 * （正文里的图是 [DraftBlockEditorImage]，整宽 120dp），"框外一排小图"那个设计已经不存在。
 * 留在这里而不是删掉，是因为它的 ✕ 无障碍文案 / 尺寸 / 解码采样是一份"缩略图"的现成实现，
 * 下次要做"框外小图"（比如多图预览条）可以直接复用，不必再写第三遍。
 */
@Composable
fun HistoryComposerThumbnail(path: String, onRemove: () -> Unit) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.sm)
    val thumbnail = remember(path) { decodeComposerThumbnail(path) }
    Box(modifier = Modifier.size(HistoryComposerThumbnailSize)) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(shape),
            )
        } else {
            Box(modifier = Modifier.matchParentSize().clip(shape).background(theme.fieldBg))
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(theme.text.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.stash_tag_delete),
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * 缩略图解码（[HistoryComposerThumbnail] 用，64dp 小图）。
 *
 * §0.16.19：采样目标 160 → **[HistoryComposerThumbnailTargetPx]**（按"64dp 显示 × 2.5 density"
 * 留余量）。160px 是"缩略图当图标"的量级，在 2.5~3x 屏上确实会发虚。
 *
 * ⚠️ 这是**唯一**还存在"写死小目标"的解码点，而它现在**没有任何生产调用方**
 * （两个编辑入口的正文都走块编辑器了，见 [HistoryComposerThumbnail] 的 KDoc）——
 * 所以这一处调高只是"以后复用别再发虚"，不是本次糊的根因。
 */
private fun decodeComposerThumbnail(path: String): android.graphics.Bitmap? =
    decodeComposerImage(path, targetPx = HistoryComposerThumbnailTargetPx)

/**
 * 块编辑器里图片块的解码（§0.16.19：采样目标由调用方按显示宽度给）。
 *
 * 仍然走 `inSampleSize`（2 的幂）—— 这是"看得清"和"不把内存喂爆"之间最省的折中；
 * 结果由调用方 `remember(path, imageTargetPx)` 缓存，不会每帧重解码。
 *
 * 两个编辑入口（弹窗 / 编辑条）共用它：一个吃 cache 临时文件、一个吃暂存夹文件，
 * 对 `BitmapFactory` 来说都是"一个路径"，所以没必要分两份。
 */
private fun decodeDraftImage(path: String, targetPx: Int): android.graphics.Bitmap? =
    decodeComposerImage(path, targetPx = targetPx)

/** 复用同一套采样逻辑：先只读尺寸、再按 2 的幂降采样解出来。失败一律给 null（调用方自己兜底）。 */
private fun decodeComposerImage(path: String, targetPx: Int): android.graphics.Bitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > targetPx || bounds.outHeight / sample > targetPx) {
        sample *= 2
    }
    android.graphics.BitmapFactory.decodeFile(
        path,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
    )
}.getOrNull()

/** FAB 距面板底边的距离（见 `HistoryComposerFab` 里的说明）。 */
private val HistoryComposerFabBottomPadding = 32.dp

/** 设计稿 `.fab`：54dp、圆角 19、主题色底 + 白加号；打开后变玻璃底 + 加号转 45°（成了 ×）。 */
@Composable
fun HistoryComposerFab(
    open: Boolean,
    onClick: () -> Unit,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(19.dp)
    val rotation by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = tween(280),
        label = "historyFabRotation",
    )
    val containerColor by animateColorAsState(
        // `.fab { background: var(--accent-solid) }` / `.fab.open { background: var(--g-fill); color: var(--text) }`
        targetValue = if (open) theme.glassFillTop else theme.accentSolid,
        animationSpec = tween(200),
        label = "historyFabContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (open) theme.text else Color.White,
        animationSpec = tween(200),
        label = "historyFabContent",
    )
    Box(
        modifier = Modifier
            // 设计稿 `.fab { right:16px; bottom:92px }`：92px 是当年"底部输入条"时代的坐标
            // （输入条现在是屏幕居中模态），但**底边偏移不能是 0** —— 之前只有 `end = 16.dp`，
            // FAB 直接贴住屏幕底边：一半压在系统 home 手势区里、右下角还被裁掉。
            // 32dp 是"离开手势区、又不飘到列表中间"的值（提示条那边用的是 104dp，属于更保守的一档）。
            .padding(end = 16.dp, bottom = HistoryComposerFabBottomPadding)
            .size(54.dp)
            .shadow(if (open) 10.dp else 18.dp, shape)
            .clip(shape)
            .background(containerColor)
            .then(
                if (open) {
                    Modifier.border(width = 1.dp, color = theme.glassBorder, shape = shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.stash_composer_fab),
            tint = contentColor,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun HistoryComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceError: (Int) -> Unit,
    availableTags: List<com.slideindex.app.stash.StashTag>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    blurActive: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = HistoryPanelCornerRadius,
                    topEnd = HistoryPanelCornerRadius,
                ),
            )
            // 设计稿的输入条是 `.composer.g` —— 和面板同一套**玻璃**（不是普通面色）。
            .background(theme.glassSolid)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 语音：设计稿里三个输入面都有麦克风，这里是面板输入条那个。
            HistoryVoiceMicButton(
                onResult = { recognized ->
                    onTextChange(if (text.isBlank()) recognized else "$text $recognized")
                },
                onError = onVoiceError,
                size = 40.dp,
            )
            HistoryComposerInput(
                text = text,
                onTextChange = onTextChange,
                onSubmit = onSubmit,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
            HistoryComposerSend(
                enabled = text.isNotBlank(),
                onClick = onSubmit,
            )
        }
        // 快速打标签（用户提的需求）：一排可多选的标签胶囊，存下时直接落到新条目上。
        if (availableTags.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availableTags.forEach { tag ->
                    HistoryChip(
                        label = stashTagLabel(tag.name),
                        dotColor = Color(tag.colorArgb),
                        selected = tag.name in selectedTags,
                        onClick = { onToggleTag(tag.name) },
                        mini = false,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.stash_composer_note),
            style = HistoryPanelTypography.meta(),
            color = theme.sub,
            modifier = Modifier.padding(top = 9.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/**
 * 设计稿 `.composer input`：48dp 高胶囊，`0 15px` 内边距，1dp 描边 + 浅底。
 *
 * ⚠️ 用的是 `BasicTextField(value/onValueChange)` 这个**已废弃**的重载：本仓库解析到的
 * foundation 版本里**没有** `TextFieldState` 那个重载（`rememberTextFieldState` /
 * `TextFieldLineLimits` 有，但 `BasicTextField(state = …)` 不存在），
 * 而 overlay 里既有的两处输入（`ClipboardFloatUi` / `PickResultInteractiveText`）也都是这个重载。
 * 升级 foundation 到有 state 重载后，这里应该换成 state 版（中文输入法的组合串更稳）。
 *
 * ⚠️ 这是**面板底部那条输入条**（现在只被 `HistoryComposerBar` 用），
 * 与「记一条」弹窗的块编辑器（[DraftBlockEditorSurface]）无关 —— 后者是 §0.16.16 的块流。
 */
@Suppress("DEPRECATION")
@Composable
private fun HistoryComposerInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    val hint = stringResource(R.string.stash_composer_hint)
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier
            .heightIn(min = 160.dp, max = 320.dp)
            .clip(shape)
            // 设计稿 `.composer input { border: 1px solid var(--btn-bd); background: var(--btn-bg);
            //                    font-size: var(--f-body) }`
            .background(theme.appElev)
            .border(width = 1.dp, color = theme.cardBorder, shape = shape)
            .focusRequester(focusRequester),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.5.sp,
            color = theme.text,
        ),
        cursorBrush = SolidColor(theme.accentSolid),
        singleLine = false,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = hint,
                        style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.body),
                        color = theme.sub,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** 设计稿 `.composer .ok`：48dp 圆形。空内容时 `.dim`（点它只提示"先写点什么"）。 */
@Composable
private fun HistoryComposerSend(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = historyTheme()
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(HistoryRadii.pill))
            // `.composer .ok { background: var(--accent-solid) }` / `.ok.dim { background: var(--btn-bg); color: var(--sub) }`
            .background(if (enabled) theme.accentSolid else theme.btnBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.stash_composer_send),
            tint = if (enabled) Color.White else theme.sub,
            modifier = Modifier.size(22.dp),
        )
    }
}
