package com.slideindex.app.overlay.history

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardUnsupportedBlockText
import com.slideindex.app.stash.StashAccess
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HistoryImageSource {
    Clipboard,
    Stash,
}

/**
 * 卡片内图片的**解码超前系数**（§0.16.19 引入，§0.16.20 从 1.5 提到 **2.0**）。
 *
 * 为什么要超前：`inSampleSize` 只能取 **2 的幂**，目标卡在档位边界上就会掉到一半那一档
 * （掉一半 = 用户看到的"发糊"）。1.5 那一版用户实测仍嫌糊，所以取到 2.0。
 *
 * ⚠️ §0.16.20 的**关键修正其实不在这个系数**，而在**像素预算**：
 * 老的 `maxPixels = 目标宽 × 可见高 × 2` 对**竖长截图**（1080×2400、卡片 296dp 宽 × 150dp 高）
 * 只有约 47 万像素，会把 259 万像素的原图硬采到 1/4（解出 540px 宽，比目标 680px 还小 → 放大变糊）。
 * 现在两个解码器都改成按"**较大边**"给预算（`(2 × max(目标宽, 可见高))²`，上限 1200 万像素），
 * 高度维不再被饿死。**系数与预算两处都要对**才清晰，只改一个没用。
 *
 * 内存：卡片解码结果都进 `thumbnailCache` / `ClipboardThumbnailCache`（`LruCache`，1/8 堆），
 * 旧值会被换出。最坏一张：源 `(2×680)² ≈ 167 万像素` ≈ **6.7MB**（解出来还会被缩到 680px 宽、
 * 裁到 ≤150dp 高，长期驻留的是那个小图）；同时可见 3~4 张 → 瞬时约 20~27MB，
 * 在 1/8 堆（旗舰机约 32~64MB）之内。
 */
private const val HistoryCardImageOversample = 2.0f

@Composable
fun HistoryContentBlockView(
    block: ClipboardContentBlock,
    imageSource: HistoryImageSource,
    entryId: String,
    context: Context,
    previewWidthPx: Int,
    previewHeightPx: Int,
    expanded: Boolean,
    /**
     * 图片块上的**点击热区**（§0.16.22）：点了就地展开 / 收起这条卡片。
     *
     * 为什么要单独传进来而不能靠宿主那层的 `clickable`：折叠态的图在
     * `HorizontalPager`（那是 [HistoryImagePagerSection]，自己带热区）或
     * 本函数这条 `Image` 分支上，而**本分支的 `Image` 会把单击吃掉**
     * —— 用户看到的就是"点图没反应，只有点那行文件名才展开"。
     *
     * null = 宿主不给这个行为（例如块编辑器预览），那就什么也不挂。
     */
    onTapToggle: (() -> Unit)? = null,
    onLongPressDrag: (() -> Unit)? = null,
) {
    when (block.kind) {
        ClipboardBlockKind.TEXT -> {
            Text(
                text = block.text,
                style = HistoryPanelTypography.content(),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
        ClipboardBlockKind.IMAGE -> {
            // §0.16.20：解码目标 = **最终渲染尺寸里"较大的那一边" × density × 超前系数**。
            //
            // 渲染尺寸怎么算（两个 tab 同一条路径，参数由调用方给）：
            // - 宽：`previewWidthPx` = 卡片内容宽度 = `historyPreviewWidthPx()`
            //   （面板真实宽度 − 24dp；竖屏 411dp 屏 → 面板 320dp → 这里约 296dp ≈ 680px）；
            // - 高：`previewHeightPx` = 折叠时的高度上限（闪念 150dp ≈ 345px、剪贴板 120dp ≈ 276px）；
            //   展开时高度不设限，用 `historyExpandedImageMaxSidePx()`。
            //
            // ⚠️ **竖长截图（1080×2400，宽高比 0.45）是决定性场景**：它 `Fit` 进这个盒子时
            // **宽度是满的**（296dp ≈ 680px），高度被裁到 150dp。也就是说"较长边"是**宽**，
            // 目标宽必须给到 680px 以上才不糊 —— 而老实现虽然也传了宽度，却被解码器里那个
            // `目标宽 × 可见高 × 2` 的**像素预算**掐死（见 [HistoryCardImageOversample] 的说明）。
            //
            // 所以这里保持"按宽度给目标"，真正修清晰度的是解码器的预算改成按较大边算；
            // 展开态走 `historyExpandedImageMaxSidePx()`（≤2048），不受折叠这一套限制。
            val decodeWidthPx = if (expanded) {
                previewWidthPx
            } else {
                (previewWidthPx * HistoryCardImageOversample).toInt()
            }
            val decodeMaxSidePx = if (expanded) historyExpandedImageMaxSidePx() else decodeWidthPx
            var bitmap by remember(entryId, block.fileName, decodeMaxSidePx, previewHeightPx, expanded, imageSource) {
                mutableStateOf<Bitmap?>(null)
            }
            LaunchedEffect(entryId, block.fileName, decodeMaxSidePx, previewHeightPx, expanded, imageSource) {
                if (block.fileName.isBlank()) return@LaunchedEffect
                bitmap = withContext(Dispatchers.IO) {
                    when (imageSource) {
                        HistoryImageSource.Clipboard -> {
                            if (expanded) {
                                ClipboardThumbnailCache.loadBlockThumbnail(context, block.fileName, decodeMaxSidePx)
                            } else {
                                ClipboardThumbnailCache.loadBlockThumbnailForCard(
                                    context,
                                    block.fileName,
                                    decodeMaxSidePx,
                                    previewHeightPx,
                                )
                            }
                        }
                        HistoryImageSource.Stash -> {
                            StashAccess.repository?.loadThumbnailByFileName(
                                entryId,
                                block.fileName,
                                decodeMaxSidePx,
                            )
                        }
                    }
                }
            }
            val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
            // §0.16.22：整块图都是热区（含 `Fit` 留白那两条边）—— 判据交给 Compose：
            // `Image` 自己的尺寸已经按内容算好，外面这层 `clickable` 正好圈住它。
            val tapModifier = when {
                onTapToggle != null && onLongPressDrag != null -> {
                    Modifier.combinedClickable(
                        onClick = onTapToggle,
                        onLongClick = onLongPressDrag,
                    )
                }
                onTapToggle != null -> {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onTapToggle() }
                }
                onLongPressDrag != null -> {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongPressDrag,
                    )
                }
                else -> Modifier
            }
            if (imageBitmap != null) {
                if (expanded) {
                    HistoryExpandedImage(imageBitmap = imageBitmap, modifier = tapModifier)
                } else {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (imageSource == HistoryImageSource.Stash) 150.dp else 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(tapModifier),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.clipboard_image_unavailable),
                    style = HistoryPanelTypography.hint(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        ClipboardBlockKind.AUDIO -> {
            // §0.16.21：语音块在卡片里**点了就播**（与编辑器里的胶囊是同一个组件）。
            //
            // ⚠️ 只有闪念那边有音频目录：剪贴板条目里的 `fileName` 是剪贴板图片目录的文件名，
            // 拿它去音频目录拼路径只会拼出一个不存在的文件 —— 所以按来源分流，不猜。
            val path = if (imageSource == HistoryImageSource.Stash) {
                StashAccess.repository?.audioFilePath(block.fileName)
            } else {
                null
            }
            if (path.isNullOrBlank() || !File(path).exists()) {
                Text(
                    text = stringResource(R.string.stash_audio_playback_failed),
                    style = HistoryPanelTypography.hint(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                HistoryAudioBlockCapsule(
                    path = path,
                    durationMs = block.durationMs,
                    onRemove = null,
                    onLongClick = onLongPressDrag,
                )
            }
        }

        // 「别的设备 / 新版本写进来的块」：**显示成不支持的内容**，不崩、也不假装它不存在
        // （假装不存在 = 用户以为这条数据丢了；抛异常 = 旧版本整份 index.json 读不出来）。
        ClipboardBlockKind.UNKNOWN -> {
            Text(
                text = ClipboardUnsupportedBlockText,
                style = HistoryPanelTypography.hint(),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
fun HistoryExpandedImage(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
fun rememberHistoryImageBitmap(bitmap: Bitmap?): ImageBitmap? =
    remember(bitmap) { bitmap?.asImageBitmap() }
