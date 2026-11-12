package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 取词面板的图片全屏查看（面板内浮层，不新开窗）。
 *
 * 规则：
 * - **大图 / 竖长图**：宽度铺满，高度按原图宽高比；超出屏幕时从顶部开始、纵向滚动。
 * - **普通图 / 小图**：按原始尺寸居中显示，不强行拉满宽度，也不贴顶。
 *
 * 不做缩放 / 旋转 / 1:1 —— 看竖图只需上下滑。
 * 关闭：右上角按钮、点空白、系统返回（由面板的 back handler 先关这一层）。
 */
@Composable
fun PickResultFullscreenImage(
    bitmap: Bitmap,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            // 点空白关闭；纵向滚动会把手势消费掉，不会误触发。
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val ratio = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
            val naturalWidth = with(density) { bitmap.width.toDp() }
            val naturalHeight = with(density) { bitmap.height.toDp() }
            // 小图 / 普通图保持原始尺寸；只有大图或竖长图才铺满宽度。
            val fitsAtNaturalSize = naturalWidth <= maxWidth && naturalHeight <= maxHeight
            val displayWidth = if (fitsAtNaturalSize) naturalWidth else maxWidth
            val displayHeight = displayWidth * ratio

            if (displayHeight <= maxHeight) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(displayWidth)
                        .height(displayHeight),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(displayHeight),
                    )
                }
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                // 浮窗是全屏坐标（含状态栏），给顶部留一点安全距离。
                .padding(top = 28.dp, end = 10.dp)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}
