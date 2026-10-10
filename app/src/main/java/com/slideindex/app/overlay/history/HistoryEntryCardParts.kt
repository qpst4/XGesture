@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.slideindex.app.overlay.history

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.ui.miuix.CardSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HistoryImagePagerSection(
    thumbnails: List<Bitmap>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLongPressDrag: (() -> Unit)? = null,
    /**
     * 点图 = **就地展开这条卡片**（§0.16.22）：null = 这个入口不给"点图展开"。
     *
     * 为什么要专门给一个回调、不靠外面那层 `clickable`：这里的 `HorizontalPager`
     * 自己要吃水平拖拽手势，它会把落到图上的**单击**一并吞掉 —— 外层 `clickable`
     * 收不到事件，用户感受到的就是"点图没反应，只有点旁边那行字才展开"。
     * 所以热区必须挂在**图自己**身上（每一页、每一张缩略图都挂）。
     */
    onTapExpand: (() -> Unit)? = null,
) {
    if (thumbnails.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, thumbnails.lastIndex.coerceAtLeast(0)),
        pageCount = { thumbnails.size },
    )
    LaunchedEffect(pagerState.settledPage) {
        if (thumbnails.isNotEmpty()) {
            onSelectedIndexChange(pagerState.settledPage.coerceIn(0, thumbnails.lastIndex))
        }
    }
    LaunchedEffect(selectedIndex) {
        if (thumbnails.isNotEmpty() && pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex.coerceIn(0, thumbnails.lastIndex))
        }
    }
    /**
     * 图上的热区（§0.16.22）。
     *
     * ⚠️ 长按拖拽优先：给了 [onLongPressDrag] 时用 `combinedClickable`（**同一次**手势里
     * 短按 = 展开、长按 = 拖出），不要叠两个 clickable —— 叠了以后长按会先被
     * `clickable` 认领成"按下"，拖拽就起不来了（那是既有能力，不能为了点图弄丢）。
     */
    val tapModifier: Modifier = when {
        onTapExpand != null && onLongPressDrag != null -> Modifier.combinedClickable(
            onClick = onTapExpand,
            onLongClick = onLongPressDrag,
        )
        onTapExpand != null -> Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onTapExpand() }
        onLongPressDrag != null -> Modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongPressDrag,
        )
        else -> Modifier
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(tapModifier),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                val bitmap = thumbnails.getOrNull(page) ?: return@HorizontalPager
                val imageBitmap = rememberHistoryImageBitmap(bitmap) ?: return@HorizontalPager
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        // 每一页都要有自己的热区：翻到第 3 张再点它，也该展开这条卡片。
                        .then(tapModifier),
                    contentScale = ContentScale.Crop,
                )
            }
            if (thumbnails.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1}/${thumbnails.size}",
                    style = HistoryPanelTypography.meta(),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (thumbnails.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(thumbnails.size, key = { it }) { index ->
                    val selected = index == selectedIndex
                    val thumbBitmap = thumbnails[index]
                    val thumbImage = rememberHistoryImageBitmap(thumbBitmap)
                    val scheme = MiuixTheme.colorScheme
                    if (thumbImage != null) {
                        Image(
                            bitmap = thumbImage,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        scheme.primary
                                    } else {
                                        scheme.dividerLine.copy(alpha = 0.6f)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                )
                                // §0.16.22：缩略图先切页码（它自己的语义），并且**必须**用
                                // `clickable` 的既有"子节点先消费"把它吃掉 —— 交给外层的话，
                                // 点缩略图会变成"展开卡片"，用户就再也切不了第 2、3 张了。
                                .clickable { onSelectedIndexChange(index) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryExpandableContentSection(
    entryId: String,
    canExpand: Boolean,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    contentBlocks: List<ClipboardContentBlock>,
    imageSource: HistoryImageSource,
    previewWidthPx: Int,
    previewHeightPx: Int,
    collapsedContent: @Composable () -> Unit,
    onLongPressDrag: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // §0.16.22：点图 = 就地展开这条卡片（**只**挂了图 / 语音 / 未知块这些自绘内容上）。
    //
    // 为什么不能只靠下面 Column 的 `gestureModifier`：图片那块的 `HorizontalPager`
    // 自己要吃拖拽手势，落进它的**单击**收不到（用户原话："只有那行文件名能点"）。
    // 所以把热区补在图上（见 [HistoryImagePagerSection] 的 `onTapExpand` 与
    // `HistoryContentBlockView` 的 `onTapToggle`）。
    //
    // ⚠️ 折叠态是"展开"，展开态是"收起" —— 与 Column 那层同一个语义，不要在两处各写一半。
    // 链接文本块不挂热区：它的点按要留给"选中/长按复制"，那是既有行为。
    val imageTapToggle: (() -> Unit)? = if (canExpand) onExpandedChange else null
    val gestureModifier = when {
        canExpand && onLongPressDrag != null -> {
            Modifier.combinedClickable(
                onClick = onExpandedChange,
                onLongClick = onLongPressDrag,
            )
        }
        canExpand -> {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onExpandedChange() }
        }
        onLongPressDrag != null -> {
            Modifier.combinedClickable(
                onClick = {},
                onLongClick = onLongPressDrag,
            )
        }
        else -> Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(gestureModifier)
            .then(
                if (canExpand) {
                    Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                contentBlocks.forEach { block ->
                    HistoryContentBlockView(
                        block = block,
                        imageSource = imageSource,
                        entryId = entryId,
                        context = context,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        expanded = true,
                        // §0.16.22：展开态点图 = 收起（与点卡片别处同一个语义）。
                        onTapToggle = imageTapToggle,
                    )
                }
            }
        } else {
            collapsedContent()
            // §0.16.21：**折叠态也要看得见、点得到**语音块与"不支持的内容"块。
            //
            // 为什么不能只在展开态画：折叠时显示的是 `collapsedContent()`（正文摘要 / 图片轮播），
            // 而一条**只有一段录音**的闪念摘要永远是空的 —— 用户看到一张空白卡片，
            // 根本不知道里面有没有内容，更别说点开播放。
            val inlineBlocks = contentBlocks.filter {
                it.kind == ClipboardBlockKind.AUDIO || it.kind == ClipboardBlockKind.UNKNOWN
            }
            if (inlineBlocks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    inlineBlocks.forEach { block ->
                        HistoryContentBlockView(
                            block = block,
                            imageSource = imageSource,
                            entryId = entryId,
                            context = context,
                            previewWidthPx = previewWidthPx,
                            previewHeightPx = previewHeightPx,
                            expanded = false,
                            // 语音胶囊自己吃点击（点了就播），这里传下去也不会生效；
                            // 传它是为了"将来这里再放别的块"时行为一致。
                            onTapToggle = imageTapToggle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryEntryCardShell(
    entryId: String,
    createdAtEpochMs: Long,
    starred: Boolean = false,
    /**
     * 已完成：卡片内容整体淡到 [HistoryPanelColors.DONE_CONTENT_ALPHA]
     * （设计稿 `.item.done .box { opacity: .62 }`）。
     *
     * 只淡**内容**（文字 / 图标），不淡卡片底色 —— 底色那边已经由
     * `HistoryPanelColors.cardBackground(done = true)` 处理过，两边都淡会灰得过头。
     */
    done: Boolean = false,
    /**
     * 时间是否画在卡片里。
     *
     * 闪念页签传 `false`：设计稿把时间移到了卡片**外面**的左侧时间轴槽（`.item .when` 在
     * `.box` 之前），由 `HistoryTimelineEntryRow` 渲染。剪贴板页签仍是卡片内（设计稿
     * `clipItemHtml` 里 `when` 就在 `.box` 内）。
     */
    showTimestamp: Boolean = true,
    /** 头部右侧内容。为 null 且不显示时间时，整个头部行都不出现。 */
    headerTrailing: (@Composable () -> Unit)? = null,
    /** 刚存下的那条：播一次高亮环（设计稿 `.item.flash`）。 */
    flash: Boolean = false,
    /**
     * 这条属于哪一档时间（今天 / 昨天 / 更早）。
     *
     * 设计稿**按档换卡片观感**：今天实心+亮边+投影、昨天半透明、更早完全透明。
     * 传 null（例如剪贴板页签）就退回"实心卡片"。
     */
    dayGroup: HistoryDayGroup? = null,
    content: @Composable () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val theme = historyTheme()
    // 设计稿 `.box { border-radius: var(--r-lg) }` = 18dp。
    val cardShape = RoundedCornerShape(HistoryRadii.lg)
    // ⚠️ **不再按时间分档**（用户明确："第一点我是不想要有区分"）。
    // 所有条目同一套外观：半透明**单色**底 + 一条 1dp **内描边**；**不给每张都加投影**
    // （整屏几十张卡都投影会脏）。demo 那套"今天玻璃/昨天半透/更早透明"的三档全部作废。
    // 唯一还变的是**状态**：星标（accent 底 + 描边）、完成（整卡淡 + 划掉）。
    //
    // ⚠️ 底色用 [HistoryTheme.cardFill]（**卡片专用**：比面板底 [HistoryTheme.glassFill] 实一档），
    // 不要换回 `theme.glassFill` —— 面板底就是那一组，卡片叠上去等于"白压白"，观感发灰。
    val background: Brush = when {
        starred -> SolidColor(theme.accent.copy(alpha = 0.09f))
        else -> theme.cardFill
    }
    val borderColor = when {
        starred -> theme.accent.copy(alpha = 0.45f)
        else -> theme.cardBorder
    }
    // 投影只留给"新存下那条"的闪环（见下面 flash），常规卡片一律不投影。
    val cardElevation = 0.dp
    val flashProgress = rememberHistoryCardFlash(flash)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // ⚠️ `.item.done .box { opacity: .62 }` 要**整张卡**（含底色/描边/投影）一起淡。
            // Compose 的 `Modifier.alpha` 是一个 layer：它只包住**它之后**的绘制，所以必须放在
            // background/border 之**前** —— 放在后面就只淡了文字，看起来"颜色没变化"（踩过）。
            .then(
                if (done) Modifier.alpha(HistoryPanelColors.DONE_CONTENT_ALPHA) else Modifier,
            )
            .then(
                if (cardElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = cardElevation,
                        shape = cardShape,
                        clip = false,
                        ambientColor = theme.glassShadow,
                        spotColor = theme.glassShadow,
                    )
                } else {
                    Modifier
                },
            )
            .clip(cardShape)
            .background(brush = background, shape = cardShape)
            // 描边与顶边高光都**在这里自绘**（不再用 `Modifier.border`），两段原因都是真机截图
            // 逐像素量出来的：
            //
            // ⚠️ 为什么不用 `Modifier.border`：它是**骑在卡片边界上**画的（一半在卡内、一半在卡外），
            // 而卡外那半被上面的 `clip` 切掉 —— 每张卡实际只剩约半个像素的线，且落点差一点点就
            // 变成"只剩一半深度"。实测同一根线，有的卡亮度 236（实）、有的 243（虚）——这就是
            // "各卡片顶边线颜色不一致 / 有些淡"的来源。改成**内描边 + 宽度取整像素**后，
            // 每张卡的墨量完全相同，线也不用再被切掉一半。
            //
            // ⚠️ 顶边那条白线（demo 的 `inset 0 1px 0 var(--g-rim)`）**只在深色模式画**：浅色下
            // 卡片本身接近纯白，白线画在白卡上完全看不见（实测顶边那 236 全部来自描边，白线零贡献）。
            .drawWithContent {
                drawContent()
                val borderPx = 1.dp.roundToPx().toFloat()
                val cornerPx = HistoryRadii.lg.toPx() - borderPx / 2f
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(borderPx / 2f, borderPx / 2f),
                    size = Size(size.width - borderPx, size.height - borderPx),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = borderPx),
                )
                // 星标卡不画顶边高光：底色被 accent 盖住，再叠白线会显脏（用户实测过那根"莫名其妙的横白条"）。
                if (starred || !theme.isDark) return@drawWithContent
                drawLine(
                    color = theme.glassRim,
                    start = Offset(borderPx, borderPx + 0.5f),
                    end = Offset(size.width - borderPx, borderPx + 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            // 设计稿 `.item.flash .box { animation: flashin 1.4s }`：
            // `0 0 0 2px accent` + `0 0 0 8px accent-soft` → 全程淡到无。
            .then(
                if (flashProgress > 0f) {
                    Modifier
                        .shadow(
                            elevation = (HistoryCardFlashGlowDp * flashProgress).dp,
                            shape = cardShape,
                            clip = false,
                            ambientColor = theme.accent,
                            spotColor = theme.accent,
                        )
                        .border(
                            width = (HistoryCardFlashRingDp * flashProgress).dp,
                            color = theme.accent.copy(alpha = flashProgress),
                            shape = cardShape,
                        )
                } else {
                    Modifier
                },
            )
            // `.box { padding: 13px 14px 6px }`。
            .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            // 设计稿里各块的间距是**块自己带的 margin**（`.thumb`/`.append`/`.foot` 都 margin-top:10px，
            // `.acts` 是 2px），所以这里不再统一加行距。
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (showTimestamp || headerTrailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showTimestamp) {
                        Text(
                            text = formatHistoryRelativeTime(createdAtEpochMs),
                            style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.tiny),
                            color = theme.sub,
                        )
                    }
                    // 只显示时间时也要把右侧内容推到行尾（SpaceBetween 在只剩一个孩子时
                    // 会把它放到行首）。
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        headerTrailing?.invoke()
                    }
                }
            }
            content()
            // ⚠️ 这里**不画**"正文 / 操作行"之间的分隔线：设计稿两张卡（`itemHtml` / `clipItemHtml`）
            // 都没有它，`.acts` 只有 `margin-top:2px`（整份 demo 里唯一带上下分隔线的是 `.editbar .acts`，
            // 那是就地编辑条）。之前剪贴板卡片这里画了一条 `theme.hair` 的发丝线，闪念卡片又显式关掉了，
            // 同一套 shell 两种观感 —— 用户看图后确认删掉（§0.16.6）。
            Row(
                // `.acts { margin-top: 2px }`；左侧那 13dp 的负 margin 由行首图标各自
                // `offset(x = -HistoryActsOffsetX)` 实现（padding 不收负值，见 token 注释）。
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

/**
 * 卡片操作行的一个图标按钮。
 *
 * 尺寸按设计稿：**48dp 命中区 + 22dp 字形**（Android 最小触摸目标）。
 * 原先这里是 32dp / 20dp，低于规范。
 *
 * [tint] 传 null = 跟随正文色；主状态动作（星标 / 完成）在"已开启"时传主题色。
 */
@Composable
internal fun HistoryCardActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color? = null,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    // 设计稿 `.acts button { width:48px; height:48px; border-radius: var(--r-sm); opacity:.84 }`
    // + `.acts button.on { color: var(--accent-solid); opacity: 1 }`。
    val on = tint != null
    Box(
        modifier = modifier
            .size(CARD_ACTION_HIT_DP.dp)
            .clip(RoundedCornerShape(HistoryRadii.sm))
            .alpha(if (on) 1f else 0.84f)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        top.yukonga.miuix.kmp.basic.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(CARD_ACTION_GLYPH_DP.dp),
            tint = tint ?: theme.text,
        )
    }
}

/** 操作行命中区边长（Android 最小触摸目标）。 */
internal const val CARD_ACTION_HIT_DP = 48

/** 操作行图标字形边长（设计稿 22dp）。 */
internal const val CARD_ACTION_GLYPH_DP = 22

/** 设计稿 `.item.flash`：`flashin 1.4s` 的高亮环（2dp 环 + 8dp 柔光）。 */
private const val HistoryCardFlashDurationMs = 1_400
private const val HistoryCardFlashRingDp = 2f
private const val HistoryCardFlashGlowDp = 8f

/**
 * 新条目那一下高亮：进度 1 → 0（1.4s），不播时恒为 0。
 *
 * 用 [Animatable] 而不是 `animateFloatAsState`：前者能在 `flash = true` 时**从头播一次**，
 * 后者只会盯着目标值 —— 滚出屏幕再滚回来、或者同一条被复用时都不会重播。
 */
@Composable
private fun rememberHistoryCardFlash(flash: Boolean): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash) {
            progress.snapTo(1f)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = HistoryCardFlashDurationMs),
            )
        } else {
            progress.snapTo(0f)
        }
    }
    return progress.value
}

internal data class HistoryCardMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val iconTint: Color? = null,
    /** 危险动作（删除）：给它单独排一条分隔线，照设计稿的 `['sep']` 那一行。 */
    val destructive: Boolean = false,
)

@Composable
internal fun HistoryCardOverflowMenu(
    contentDescription: String,
    actions: List<HistoryCardMenuAction>,
) {
    if (actions.isEmpty()) return
    val scheme = MiuixTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(CARD_ACTION_HIT_DP.dp),
        ) {
            top.yukonga.miuix.kmp.basic.Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = contentDescription,
                modifier = Modifier.size(CARD_ACTION_GLYPH_DP.dp),
                tint = scheme.onBackground,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEachIndexed { index, action ->
                if (action.destructive && index > 0) {
                    HorizontalDivider(color = scheme.dividerLine)
                }
                DropdownMenuItem(
                    text = { Text(action.label, style = HistoryPanelTypography.content()) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = action.iconTint ?: scheme.onBackground,
                        )
                    },
                )
            }
        }
    }
}

/**
 * 正文（折叠态摘要）。[strikethrough] = 已完成，设计稿
 * `.item.done .box .body { text-decoration: line-through }`。
 */
@Composable
internal fun HistoryCollapsedSummaryText(
    text: String,
    maxLines: Int = 3,
    strikethrough: Boolean = false,

) {
    if (text.isBlank()) return
    val theme = historyTheme()
    Text(
        text = text,
        // `.body { font-size: var(--f-sm); line-height: 1.6; letter-spacing: .1px }`
        style = androidx.compose.ui.text.TextStyle(
            fontSize = HistoryFontSizes.sm,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
        ),
        color = theme.text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 「追加块」（设计稿 `.item .box .append`）：虚线分隔 + `＋ 内容`。
 *
 * 设计稿一条记录只挂一段追加；我们数据层存的是列表（`StashAppend`），所以调用方每条
 * 各渲染一块 —— 比"只显示最后一条"信息更全。
 */
@Composable
internal fun HistoryCardAppendBlock(text: String) {
    if (text.isBlank()) return
    val theme = historyTheme()
    // `.append { border-top: 1px dashed var(--line); margin-top:10px; padding-top:9px;
    //            font-size: var(--f-meta); color: var(--sub) }`
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        HistoryCardDashedDivider(color = theme.line)
        Text(
            text = "＋ $text",
            style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.meta),
            color = theme.sub,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}

/** 设计稿 `.append { border-top: 1px dashed var(--line) }` —— 虚线用 drawBehind 画。 */
@Composable
private fun HistoryCardDashedDivider(color: Color) {
    val strokePx = with(LocalDensity.current) { 1.dp.toPx() }
    val dashPx = with(LocalDensity.current) { 4.dp.toPx() }
    val gapPx = with(LocalDensity.current) { 3.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = strokePx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx)),
                )
            },
    )
}

/**
 * 卡片底部一行（设计稿 `.item .box .foot`）：来源 chip + 各标签 chip。
 *
 * 来源 = 这条是从哪儿来的（剪贴板 / 取词 / 图片）；用户自己记的纯闪念没有来源，不显示。
 */
@Composable
internal fun HistoryCardFootRow(
    sourceLabel: String?,
    tags: List<Pair<String, Color>>,
) {
    if (sourceLabel == null && tags.isEmpty()) return
    val theme = historyTheme()
    // 设计稿是 flex-wrap；标签多了必须能换行，所以用 FlowRow 而不是 Row。
    // `.foot { display:flex; gap:6px; margin-top:10px }`
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sourceLabel != null) {
            HistoryCardMiniChip(label = sourceLabel, dotColor = null, labelColor = theme.sub)
        }
        tags.forEach { (name, color) ->
            HistoryCardMiniChip(label = name, dotColor = color, labelColor = theme.text)
        }
    }
}

/** 设计稿 `.chip.mini`：22dp 高、10.5sp、圆点 4.5dp，玻璃底 + 白描边；来源那枚是 `.ghost`（sub 色）。 */
@Composable
private fun HistoryCardMiniChip(
    label: String,
    dotColor: Color?,
    labelColor: Color,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(shape)
            .background(theme.glassFill)
            .border(width = 1.dp, color = theme.glassBorder, shape = shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(4.5.dp)
                    .clip(RoundedCornerShape(HistoryRadii.pill))
                    .background(dotColor),
            )
        }
        Text(
            text = label,
            color = labelColor,
            fontSize = HistoryFontSizes.tiny,
            maxLines = 1,
        )
    }
}

@Composable
internal fun HistorySingleImageThumb(
    bitmap: Bitmap?,
    maxHeightDp: androidx.compose.ui.unit.Dp = 150.dp,
) {
    val imageBitmap = rememberHistoryImageBitmap(bitmap)
    if (imageBitmap == null) return
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightDp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
internal fun rememberLoadedThumbnails(
    entryId: String,
    loadKey: Any,
    enabled: Boolean,
    loader: suspend () -> List<Bitmap>,
): Pair<List<Bitmap>, Boolean> {
    var thumbnails by remember(entryId) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var failed by remember(entryId) { mutableStateOf(false) }
    LaunchedEffect(entryId, loadKey, enabled) {
        if (!enabled) {
            thumbnails = emptyList()
            failed = false
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { loader() }
        thumbnails = loaded
        failed = loaded.isEmpty()
    }
    return thumbnails to failed
}

@Composable
internal fun rememberLoadedSingleThumb(
    entryId: String,
    loadKey: Any,
    enabled: Boolean,
    loader: suspend () -> Bitmap?,
): Bitmap? {
    var thumb by remember(entryId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entryId, loadKey, enabled) {
        if (!enabled) {
            thumb = null
            return@LaunchedEffect
        }
        thumb = withContext(Dispatchers.IO) { loader() }
    }
    return thumb
}
