package com.slideindex.app.overlay.history

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * 长按拖拽时"**把整张卡片当拖影**"用的快照抓取器。
 *
 * 为什么要有它：系统的拖影（`View.DragShadowBuilder`）只能拿到一张 [Bitmap]。以前我们是在
 * `onDrawShadow` 里**手绘**"几行文字 / 一张缩略图"，所以拖出去的东西**不是卡片的样子** ——
 * 没有白色底板、没有圆角、没有半透明，跟 iOS 的 drag preview、Android 自家 Launcher/文件的
 * "把整张卡拿起来"观感差得很远（用户原话："别的 app 长按卡片拖拽会整个卡片作为外观拖出"）。
 *
 * 用法（两条缺一不可）：
 * 1. 卡片组合时 `val snapshot = rememberHistoryCardSnapshot()`；
 * 2. 把它挂到卡片**修饰符链**上：`Modifier.recordHistoryCardSnapshot(snapshot)` ——
 *    位置很讲究：要在 `clip` **之内**（这样录进去的四个角是圆的），但在 `background`
 *    **之前**（否则录不到卡片底色/描边，快照会是一张透明底 + 文字的图）。
 * 3. 长按拖拽时 `snapshot.capture()` 取位图（suspend；失败返回 null，拖影会退回旧画法）。
 */
@Stable
internal class HistoryCardSnapshot internal constructor() {
    private var layer: GraphicsLayer? = null

    internal val graphicsLayer: GraphicsLayer? get() = layer

    internal fun attach(layer: GraphicsLayer) {
        this.layer = layer
    }

    /** 抓"卡片当前这一帧"。层还没被画过、或当前不是主线（无 RenderNode）时返回 null。 */
    suspend fun capture(): Bitmap? = runCatching {
        layer?.toImageBitmap()?.asAndroidBitmap()
    }.getOrNull()
}

@Composable
internal fun rememberHistoryCardSnapshot(): HistoryCardSnapshot {
    val layer = rememberGraphicsLayer()
    return remember(layer) { HistoryCardSnapshot().also { it.attach(layer) } }
}

/**
 * 把卡片"顺手录一份"进 [snapshot] 的那一层（屏幕上照旧显示，只是多录一路）。
 *
 * ⚠️ 位置必须放在 `Modifier.clip(形状)` **之后**、`Modifier.background(...)` **之前**：
 * - 放在 `clip` 之后 → 录进去的内容被圆角裁过，快照自带圆角；
 * - 放在 `background` 之前 → `drawContent()` 会把下游的底色、描边、正文一起画进这一层。
 */
internal fun Modifier.recordHistoryCardSnapshot(snapshot: HistoryCardSnapshot): Modifier =
    drawWithContent {
        val layer = snapshot.graphicsLayer
        if (layer == null) {
            drawContent()
        } else {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    }
