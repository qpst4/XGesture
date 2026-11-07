package com.slideindex.app.overlay

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 消息提醒悬浮球的圆形投影。
 *
 * 不用 `Modifier.shadow`（elevation）：它的阴影画在节点外，而悬浮窗尺寸贴着球，
 * 阴影会被窗口矩形裁成直边；elevation 阴影本身也依赖 HWUI 的 outline 近似，
 * 放大后边缘是折线。这里改成手绘多层扩散圆（和 [TaskSwitcherRenderer] 的
 * `drawElevationShadow` 同一套做法），配合 [MessageOverlayLayout.FLOAT_ICON_SHADOW_PAD_DP]
 * 预留的窗口空间，形状与柔和度都可控，任何设备一致。
 */
object MessageBallShadow {

    /** 阴影模糊半径（dp）。 */
    private const val BLUR_DP = 2.5f

    /** 投影整体下移（dp）。0 = 四周均匀；下移会让顶部变细、底部变宽。 */
    private const val OFFSET_Y_DP = 0f

    /** 扩散层数：层数越多过渡越平滑，5 层在 44dp 球上已看不出台阶。 */
    private const val LAYERS = 5

    /** 最内层阴影的不透明度（0..255）。 */
    private const val ALPHA = 34

    /**
     * 在 [ballSize] 的圆外画一圈投影。
     *
     * 调用方必须在外层预留 [MessageOverlayLayout.FLOAT_ICON_SHADOW_PAD_DP] 的 padding，
     * 否则画到节点外的部分会被裁掉。
     *
     * @param ballSize 球的直径（不含阴影）
     * @param alpha 整体不透明度（跟随进场动画 / 不透明度设置）
     */
    fun Modifier.ballShadow(ballSize: Dp, alpha: Float = 1f): Modifier = drawBehind {
        if (alpha <= 0.001f) return@drawBehind
        val radius = ballSize.toPx() / 2f
        val blur = BLUR_DP.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f + OFFSET_Y_DP.dp.toPx())
        for (layer in LAYERS downTo 1) {
            val fraction = layer / LAYERS.toFloat()
            val layerAlpha = (ALPHA * fraction * fraction / LAYERS * alpha)
                .toInt()
                .coerceIn(1, 255)
            drawCircle(
                color = Color.Black.copy(alpha = layerAlpha / 255f),
                radius = radius + blur * fraction * 0.8f,
                center = center,
            )
        }
    }
}
