package com.slideindex.app.overlay.history

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardDragShareFallback
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardEntryType
import com.slideindex.app.clipboard.ClipboardImageLabel
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.displayTypeLabelKey
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.hasRichPinContent
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.clipboard.shouldOfferExpand
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.PickResultFromHistoryCoordinator
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.StashMetaRepository
import com.slideindex.app.stash.StashMetaStore
import com.slideindex.app.stash.allImageFileNames
import com.slideindex.app.stash.combinedText
import com.slideindex.app.stash.exportText
import com.slideindex.app.stash.resolvedContentBlocks
import com.slideindex.app.stash.shouldOfferExpand
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HistoryClipboardEntryCard(
    entry: ClipboardEntry,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    selectedImageIndex: Int,
    onSelectedImageIndexChange: (Int) -> Unit,
    previewWidthPx: Int,
    previewHeightPx: Int,
    /** 这条的时间档（设计稿 `clipItemHtml` 用的是和闪念同一套 `.fresh/.mid/.old`）。 */
    dayGroup: HistoryDayGroup? = null,
    onShowMessage: (Int) -> Unit,
    onCopy: () -> Unit,
    onStash: () -> Unit,
    onDelete: () -> Unit,
    haptics: HistoryHaptics = rememberHistoryHaptics(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    // 长按拖拽：把**整张卡**当拖影（见 [HistoryCardSnapshot]）。抓图是 suspend 的，所以要起协程。
    val dragSnapshot = rememberHistoryCardSnapshot()
    val dragScope = rememberCoroutineScope()
    val hasImageContent = entry.hasImageContent()
    val contentBlocks = remember(entry.id, entry.contentBlocks, entry.text, entry.htmlText, entry.imageFileNames) {
        entry.resolvedContentBlocks()
    }
    val canExpand = remember(entry.id, contentBlocks) { entry.shouldOfferExpand() }
    val (thumbnails, imageLoadFailed) = rememberLoadedThumbnails(
        entryId = entry.id,
        loadKey = listOf(
            entry.imageFileName,
            entry.imageFileNames,
            entry.uri,
            entry.mimeType,
            entry.htmlText,
            previewWidthPx,
            previewHeightPx,
            hasImageContent,
        ),
        enabled = hasImageContent,
        loader = {
            ClipboardThumbnailCache.loadEntryThumbnailsForCard(
                context,
                entry,
                previewWidthPx,
                previewHeightPx,
            )
        },
    )
    val hasImages = thumbnails.isNotEmpty()
    val selectedBitmap = thumbnails.getOrNull(selectedImageIndex)
    val bodyText = entry.text.trim()
    val showBodyText = bodyText.isNotEmpty() && bodyText != entry.uri
    val summaryText = when {
        showBodyText -> bodyText
        !hasImages && !imageLoadFailed -> entry.uri ?: entry.intentUri.orEmpty()
        else -> ""
    }
    val pinLabel = stringResource(R.string.stash_action_pin)
    val shareLabel = stringResource(R.string.float_ball_action_share)
    val saveImageLabel = stringResource(R.string.clipboard_action_save_image)
    val deleteLabel = stringResource(R.string.stash_action_delete)
    val moreLabel = stringResource(R.string.notification_filter_more_menu)
    val onLongPressDrag: () -> Unit = {
        val clipData = ClipboardWriter.buildClipForEntry(context, entry)
        if (clipData == null) {
            onShowMessage(R.string.history_drag_unsupported)
        } else {
            // ⚠️ 必须先起协程：抓"整张卡"的快照是 suspend 的（得先让这一帧画进 GraphicsLayer）。
            dragScope.launch {
                val started = HistoryEntryDragHelper.startDrag(
                    view = view,
                    clipData = clipData,
                    preview = HistoryEntryDragHelper.previewForClipboardEntry(entry, thumbnails)
                        .copy(snapshot = dragSnapshot.capture()),
                    onDragStart = { FloatBallStashPanel.setDragHidden(true) },
                    onDragEnd = { FloatBallStashPanel.setDragHidden(false) },
                    onDropRejected = {
                        if (ClipboardDragShareFallback.hasShareableContent(clipData) &&
                            !ClipboardDragShareFallback.shareToForegroundHost(context, clipData)
                        ) {
                            onShowMessage(R.string.history_drag_unsupported)
                        }
                    },
                )
                if (!started) {
                    if (!ClipboardDragShareFallback.shareToForegroundHost(context, clipData)) {
                        onShowMessage(R.string.history_drag_unsupported)
                    }
                }
            }
        }
    }

    HistoryEntryCardShell(
        entryId = entry.id,
        createdAtEpochMs = entry.createdAtEpochMs,
        starred = false,
        dayGroup = dayGroup,
        snapshot = dragSnapshot,
        headerTrailing = {
            IconButton(
                onClick = {
                    PickResultFromHistoryCoordinator.openFromClipboard(
                        context,
                        entry,
                        selectedImageIndex,
                    )
                },
                modifier = Modifier.size(32.dp),
            ) {
                MiuixIcon(
                    imageVector = Icons.Outlined.TextFields,
                    contentDescription = stringResource(R.string.stash_action_open_pick),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = clipboardEntryTypeLabel(entry.displayTypeLabelKey()),
                style = HistoryPanelTypography.meta(),
                color = MiuixTheme.colorScheme.primary,
            )
        },
        content = {
            HistoryExpandableContentSection(
                entryId = entry.id,
                canExpand = canExpand,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                contentBlocks = contentBlocks,
                imageSource = HistoryImageSource.Clipboard,
                previewWidthPx = previewWidthPx,
                previewHeightPx = previewHeightPx,
                onLongPressDrag = onLongPressDrag,
                collapsedContent = {
                    if (hasImages) {
                        HistoryImagePagerSection(
                            thumbnails = thumbnails,
                            selectedIndex = selectedImageIndex,
                            onSelectedIndexChange = onSelectedImageIndexChange,
                            onLongPressDrag = onLongPressDrag,
                            // §0.16.22：点图 = 就地展开（剪贴板卡片与闪念卡片同一条交互）。
                            onTapExpand = if (canExpand) onExpandedChange else null,
                        )
                    } else if (imageLoadFailed) {
                        Text(
                            text = stringResource(R.string.clipboard_image_unavailable),
                            style = HistoryPanelTypography.hint(),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    HistoryCollapsedSummaryText(text = summaryText)
                },
            )
        },
        actions = {
            HistoryCardActionIcon(
                icon = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.clipboard_history_float_copy),
                onClick = {
                    haptics.confirm()
                    onCopy()
                },
            )
            HistoryCardActionIcon(
                icon = Icons.Outlined.Archive,
                contentDescription = stringResource(R.string.float_ball_action_stash),
                onClick = {
                    haptics.confirm()
                    onStash()
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            HistoryCardOverflowMenu(
                contentDescription = moreLabel,
                actions = buildList {
                    if (entry.hasRichPinContent() || hasImages || showBodyText) {
                        add(
                            HistoryCardMenuAction(
                                label = pinLabel,
                                icon = Icons.Default.PushPin,
                                onClick = {
                                    haptics.tick()
                                    when {
                                        entry.hasRichPinContent() -> StashCoordinator.pinRichFromClipboard(context, entry)
                                        hasImages && selectedBitmap != null -> {
                                            StashCoordinator.pinImageToScreen(context, selectedBitmap)
                                        }
                                        showBodyText -> StashCoordinator.pinTextToScreen(context, bodyText)
                                    }
                                },
                            ),
                        )
                    }
                    if (!expanded && hasImages && selectedBitmap != null) {
                        add(
                            HistoryCardMenuAction(
                                label = shareLabel,
                                icon = Icons.Default.Share,
                                onClick = {
                                    haptics.tick()
                                    FloatBallTextPick.shareScreenshot(context, selectedBitmap)
                                },
                            ),
                        )
                        add(
                            HistoryCardMenuAction(
                                label = saveImageLabel,
                                icon = Icons.Outlined.Save,
                                onClick = {
                                    haptics.tick()
                                    val saved = FloatBallTextPick.saveScreenshot(context, selectedBitmap)
                                    onShowMessage(
                                        if (saved) R.string.float_ball_screenshot_saved else R.string.float_ball_action_failed,
                                    )
                                },
                            ),
                        )
                    } else if (!expanded && showBodyText) {
                        add(
                            HistoryCardMenuAction(
                                label = shareLabel,
                                icon = Icons.Default.Share,
                                onClick = {
                                    haptics.tick()
                                    FloatBallTextPick.shareText(context, bodyText)
                                },
                            ),
                        )
                    }
                    add(
                        HistoryCardMenuAction(
                            label = deleteLabel,
                            icon = Icons.Default.Delete,
                            onClick = onDelete,
                            iconTint = MiuixTheme.colorScheme.error,
                            destructive = true,
                        ),
                    )
                },
            )
        },
    )
}

@Composable
internal fun HistoryStashEntryCard(
    entry: StashEntry,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    selectedImageIndex: Int,
    onSelectedImageIndexChange: (Int) -> Unit,
    onShowMessage: (Int) -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
    /** 打开就地编辑条（设计稿 `.editbar`）。 */
    onEdit: () -> Unit = {},
    /** 卡片要展示的元数据（完成态 / 标签 / 追加 / 来源），见 `StashMetaRepository`。 */
    meta: StashMetaStore = StashMetaStore(),
    /** 标签名 -> 颜色（来自 `HistoryPanelViewModel.availableTags`）。 */
    tagColors: Map<String, Long> = emptyMap(),
    onSetDone: (Boolean) -> Unit = {},
    /** 刚存下的那条：播一次高亮环（设计稿 `.item.flash`）。 */
    flash: Boolean = false,
    /** 这条的时间档（今天 / 昨天 / 更早）：设计稿按档换卡片观感，见 `HistoryPanelColors`。 */
    dayGroup: HistoryDayGroup? = null,
    haptics: HistoryHaptics = rememberHistoryHaptics(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    // 长按拖拽：把**整张卡**当拖影（见 [HistoryCardSnapshot]）。抓图是 suspend 的，所以要起协程。
    val dragSnapshot = rememberHistoryCardSnapshot()
    val dragScope = rememberCoroutineScope()
    val repo = StashAccess.repository
    val previewWidthPx = historyPreviewWidthPx()
    val previewHeightPx = historyStashPreviewHeightPx()
    val richPreviewHeightPx = historyClipboardCardPreviewHeightPx()
    val richBlocks = remember(entry.id, entry.contentBlocks, entry.type, entry.text, entry.imageFileName) {
        entry.resolvedContentBlocks()
    }
    val canExpand = remember(entry.id, entry.type, richBlocks) { entry.shouldOfferExpand() }
    val richImageFileNames = remember(entry.id, entry.contentBlocks, entry.imageFileName) {
        entry.allImageFileNames()
    }
    /**
     * 卡片上显示的正文 —— 已经把「图片文件名」这层噪声去掉（§0.16.22）。
     *
     * 从相册/文件管理器复制的图片，剪贴板里往往**同时**带了文件名（`IMG_20240101_123456.jpg`）——
     * 它会被存进正文，于是闪念卡片上就冒出一行文件名：那张图就在卡片上，这行字纯属噪声。
     * 判据复用剪贴板那边那份 [ClipboardImageLabel.isMetadataText]（"这是图片的标签文字，不是内容"），
     * **不是**自己写一个"像不像文件名"的正则 —— 两处判据分家，迟早会出现"剪贴板页签不显示、
     * 闪念卡片显示"这种不一致。
     *
     * ⚠️ **只在有图的时候过滤**：没有图的条目里那串文字就是用户自己打的，绝不能吞。
     * 剪贴板页签与编辑浮窗**不过滤**（用户明确要求那两处保留），所以它只作用在这张卡片上。
     */
    val cardBodyText = remember(entry.id, entry.type, entry.text, richBlocks, richImageFileNames) {
        when (entry.type) {
            StashEntryType.TEXT -> entry.text.orEmpty()
            StashEntryType.RICH -> entry.combinedText()
            else -> ""
        }.let { body ->
            if (body.isBlank() || richImageFileNames.isEmpty()) {
                body
            } else {
                ClipboardImageLabel.stripMetadataText(
                    text = body,
                    imageSources = richImageFileNames,
                    uri = null,
                )
            }
        }
    }
    /**
     * 分享用的纯文本（§0.16.21）：比卡片的摘要多一样东西 —— **语音块**。
     *
     * `cardBodyText` 走 `combinedText()`（正文语义，也是搜索语料），里面没有"语音 0:12"；
     * 一条只录了音的闪念用它分享出去会是空的。`exportText()` 才是"导出"该有的语义。
     * §0.16.22 起这里也顺手去掉图片文件名那层噪声（与卡片显示同一套判据）。
     */
    val exportText = remember(entry.id, entry.type, entry.text, richBlocks, richImageFileNames) {
        entry.exportText().let { body ->
            if (body.isBlank() || richImageFileNames.isEmpty()) {
                body
            } else {
                ClipboardImageLabel.stripMetadataText(body, richImageFileNames, null)
            }
        }
    }
    val singleThumb = rememberLoadedSingleThumb(
        entryId = entry.id,
        loadKey = listOf(previewWidthPx, previewHeightPx, entry.type),
        enabled = entry.type == StashEntryType.IMAGE,
        loader = { repo?.loadImageThumbnailForCard(entry, previewWidthPx, previewHeightPx) },
    )
    val (richThumbnails, richImageLoadFailed) = rememberLoadedThumbnails(
        entryId = entry.id,
        loadKey = listOf(richImageFileNames, previewWidthPx, richPreviewHeightPx, entry.type),
        enabled = entry.type == StashEntryType.RICH && richImageFileNames.isNotEmpty(),
        loader = {
            repo?.loadEntryThumbnailsForCard(entry, previewWidthPx, richPreviewHeightPx).orEmpty()
        },
    )
    val richHasImages = richThumbnails.isNotEmpty()
    val richSelectedBitmap = richThumbnails.getOrNull(selectedImageIndex)
    val pinLabel = stringResource(R.string.stash_action_pin)
    val shareLabel = stringResource(R.string.float_ball_action_share)
    val saveImageLabel = stringResource(R.string.clipboard_action_save_image)
    val deleteLabel = stringResource(R.string.stash_action_delete)
    val moreLabel = stringResource(R.string.notification_filter_more_menu)
    val pickLabel = stringResource(R.string.stash_action_open_pick)

    /* ---- 卡片重排要用的元数据（完成态 / 标签 / 追加 / 来源），见 StashMetaRepository ---- */
    val done = meta.isDone(entry.id)
    val tagNames = meta.tagsOf(entry.id)
    val appends = meta.appendsOf(entry.id)
    val sourceLabel = stashSourceLabelRes(meta.sourceOf(entry.id))?.let { stringResource(it) }
    val tagChips = tagNames.mapNotNull { name -> tagColors[name]?.let { name to Color(it) } }
    // 设计稿 `isTodo`：主状态动作在「待办」条目上是「完成」，其它条目上是「星标」。
    // 已完成的条目即使后来摘掉「待办」标签也保留这个按钮，否则没法取消完成。
    val isTodo = StashMetaRepository.TODO_TAG_NAME in tagNames || done
    val openInPick: () -> Unit = {
        val imageIndex = when (entry.type) {
            StashEntryType.RICH -> selectedImageIndex
            else -> 0
        }
        PickResultFromHistoryCoordinator.openFromStash(context, entry, imageIndex)
    }
    val shareEntry: () -> Unit = {
        when {
            entry.type == StashEntryType.RICH && !expanded && richHasImages && richSelectedBitmap != null -> {
                FloatBallTextPick.shareScreenshot(context, richSelectedBitmap)
            }
            entry.type == StashEntryType.RICH && !expanded && exportText.isNotBlank() -> {
                FloatBallTextPick.shareText(context, exportText)
            }
            else -> onShare()
        }
    }

    val onLongPressDrag: () -> Unit = {
        val clipData = HistoryEntryDragHelper.buildClipForStashEntry(context, entry, repo)
        if (clipData == null) {
            onShowMessage(R.string.history_drag_unsupported)
        } else {
            // ⚠️ 必须先起协程：抓"整张卡"的快照是 suspend 的（得先让这一帧画进 GraphicsLayer）。
            dragScope.launch {
                val started = HistoryEntryDragHelper.startDrag(
                    view = view,
                    clipData = clipData,
                    preview = HistoryEntryDragHelper.previewForStashEntry(entry, singleThumb, richThumbnails)
                        .copy(snapshot = dragSnapshot.capture()),
                    onDragStart = { FloatBallStashPanel.setDragHidden(true) },
                    onDragEnd = { FloatBallStashPanel.setDragHidden(false) },
                    onDropRejected = {
                        if (ClipboardDragShareFallback.hasShareableContent(clipData) &&
                            !ClipboardDragShareFallback.shareToForegroundHost(context, clipData)
                        ) {
                            onShowMessage(R.string.history_drag_unsupported)
                        }
                    },
                )
                if (!started) {
                    if (!ClipboardDragShareFallback.shareToForegroundHost(context, clipData)) {
                        onShowMessage(R.string.history_drag_unsupported)
                    }
                }
            }
        }
    }

    HistoryEntryCardShell(
        entryId = entry.id,
        createdAtEpochMs = entry.createdAtEpochMs,
        starred = entry.starred,
        done = done,
        // 闪念的时间画在卡片外的左侧时间轴槽里（`HistoryTimelineEntryRow`）。
        showTimestamp = false,
        // 头部原来那两个图标（取词 / 星标）都搬走了：星标成为操作行的主状态动作，
        // 取词进 ⋮ 菜单 —— 于是头部整行不再存在（设计稿正是如此）。
        flash = flash,
        dayGroup = dayGroup,
        snapshot = dragSnapshot,
        content = {
            when (entry.type) {
                StashEntryType.TEXT -> {
                    HistoryExpandableContentSection(
                        entryId = entry.id,
                        canExpand = canExpand,
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        contentBlocks = richBlocks,
                        imageSource = HistoryImageSource.Stash,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        onLongPressDrag = onLongPressDrag,
                        collapsedContent = {
                            HistoryCollapsedSummaryText(
                                text = entry.text.orEmpty(),
                                maxLines = if (canExpand) 3 else Int.MAX_VALUE,
                                strikethrough = done,
                            )
                        },
                    )
                }
                StashEntryType.IMAGE -> {
                    HistoryExpandableContentSection(
                        entryId = entry.id,
                        canExpand = canExpand,
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        contentBlocks = entry.resolvedContentBlocks(),
                        imageSource = HistoryImageSource.Stash,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        onLongPressDrag = onLongPressDrag,
                        collapsedContent = {
                            HistorySingleImageThumb(bitmap = singleThumb)
                        },
                    )
                }
                StashEntryType.RICH -> {
                    HistoryExpandableContentSection(
                        entryId = entry.id,
                        canExpand = canExpand,
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        contentBlocks = richBlocks,
                        imageSource = HistoryImageSource.Stash,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = richPreviewHeightPx,
                        onLongPressDrag = onLongPressDrag,
                        collapsedContent = {
                            if (richHasImages) {
                                HistoryImagePagerSection(
                                    thumbnails = richThumbnails,
                                    selectedIndex = selectedImageIndex,
                                    onSelectedIndexChange = onSelectedImageIndexChange,
                                    onLongPressDrag = onLongPressDrag,
                                    // §0.16.22：点图 = 就地展开当前这张（不跳图、不开全屏查看器）。
                                    onTapExpand = if (canExpand) onExpandedChange else null,
                                )
                            } else if (richImageLoadFailed) {
                                Text(
                                    text = stringResource(R.string.clipboard_image_unavailable),
                                    style = HistoryPanelTypography.hint(),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            HistoryCollapsedSummaryText(
                            text = cardBodyText,
                            strikethrough = done,

                        )
                        },
                    )
                }
            }
            // 追加块（设计稿 `.item .box .append`）：数据层存的是一个列表，一条一段。
            appends.forEach { append ->
                HistoryCardAppendBlock(text = append.text)
            }
            // 底部一行（设计稿 `.item .box .foot`）：来源 chip + 标签 chip。
            HistoryCardFootRow(sourceLabel = sourceLabel, tags = tagChips)
        },
        actions = {
            // 设计稿 `.acts` 的行内动作只有「主状态 + 复制」，其余进 ⋮：
            // 主状态 = 待办条目给「完成」，其它条目给「星标」。
            if (isTodo) {
                HistoryCardActionIcon(
                    icon = if (done) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(
                        if (done) R.string.stash_action_mark_undone else R.string.stash_action_mark_done,
                    ),
                    // 触觉在这些动作的落地处（`HistoryPanelScreen` 的 setDone/toggleStar/deleteEntry）
                    // 统一给，卡片这里不再重复震 —— 否则同一次点击会震两下。
                    onClick = { onSetDone(!done) },
                    tint = if (done) MiuixTheme.colorScheme.primary else null,
                    modifier = Modifier.offset(x = -HistoryActsOffsetX),
                )
            } else {
                HistoryCardActionIcon(
                    icon = if (entry.starred) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(
                        if (entry.starred) R.string.stash_action_unstar else R.string.stash_action_star,
                    ),
                    onClick = onToggleStar,
                    tint = if (entry.starred) MiuixTheme.colorScheme.primary else null,
                    modifier = Modifier.offset(x = -HistoryActsOffsetX),
                )
            }
            HistoryCardActionIcon(
                icon = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.clipboard_history_float_copy),
                onClick = {
                    haptics.confirm()
                    onCopy()
                },
                modifier = Modifier.offset(x = -HistoryActsOffsetX),
            )
            Spacer(modifier = Modifier.weight(1f))
            HistoryCardOverflowMenu(
                contentDescription = moreLabel,
                actions = buildList {
                    // 设计稿的 ⋮：加星标 / 进入取词 / 钉在屏幕 / 分享 / 保存图片 / —— / 删除。
                    // 我们多一项「编辑」：设计稿是从卡片本体点开编辑条，那和"点卡片展开/收起"
                    // 冲突（App 既有行为），所以搬到 ⋮ 里更稳妥。
                    add(
                        HistoryCardMenuAction(
                            label = stringResource(R.string.stash_action_edit),
                            icon = Icons.Outlined.Edit,
                            onClick = {
                                haptics.tick()
                                onEdit()
                            },
                        ),
                    )
                    add(
                        HistoryCardMenuAction(
                            label = stringResource(
                                if (entry.starred) R.string.stash_action_unstar else R.string.stash_action_star,
                            ),
                            icon = if (entry.starred) Icons.Default.Star else Icons.Outlined.StarOutline,
                            onClick = onToggleStar,
                            iconTint = if (entry.starred) MiuixTheme.colorScheme.primary else null,
                        ),
                    )
                    add(
                        HistoryCardMenuAction(
                            label = pickLabel,
                            icon = Icons.Outlined.TextFields,
                            onClick = {
                                haptics.tick()
                                openInPick()
                            },
                        ),
                    )
                    add(
                        HistoryCardMenuAction(
                            label = pinLabel,
                            icon = Icons.Default.PushPin,
                            onClick = {
                                haptics.tick()
                                onPin()
                            },
                        ),
                    )
                    add(
                        HistoryCardMenuAction(
                            label = shareLabel,
                            icon = Icons.Default.Share,
                            onClick = {
                                haptics.tick()
                                shareEntry()
                            },
                        ),
                    )
                    if (entry.type == StashEntryType.RICH && !expanded && richHasImages && richSelectedBitmap != null) {
                        add(
                            HistoryCardMenuAction(
                                label = saveImageLabel,
                                icon = Icons.Outlined.Save,
                                onClick = {
                                    haptics.tick()
                                    val saved = FloatBallTextPick.saveScreenshot(context, richSelectedBitmap)
                                    onShowMessage(
                                        if (saved) R.string.float_ball_screenshot_saved else R.string.float_ball_action_failed,
                                    )
                                },
                            ),
                        )
                    }
                    add(
                        HistoryCardMenuAction(
                            label = deleteLabel,
                            icon = Icons.Default.Delete,
                            onClick = {
                                haptics.confirm()
                                onDelete()
                            },
                            iconTint = MiuixTheme.colorScheme.error,
                            destructive = true,
                        ),
                    )
                },
            )
        },
    )
}

@Composable
private fun clipboardEntryTypeLabel(type: ClipboardEntryType): String = when (type) {
    ClipboardEntryType.TEXT -> stringResource(R.string.clipboard_entry_type_text)
    ClipboardEntryType.URI -> stringResource(R.string.clipboard_entry_type_uri)
    ClipboardEntryType.INTENT -> stringResource(R.string.clipboard_entry_type_intent)
    ClipboardEntryType.HTML -> stringResource(R.string.clipboard_entry_type_html)
}
