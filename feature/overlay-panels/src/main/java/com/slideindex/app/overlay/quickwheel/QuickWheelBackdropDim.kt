package com.slideindex.app.overlay.quickwheel

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.overlay.LocalFrostedGlassDrawable
import com.slideindex.app.settings.QuickWheelBackdropScope
import kotlin.math.ceil
import kotlin.math.max

/**
 * 轮盘的背景层：**模糊与色调各一层、各管各的范围**，边缘共用同一个羽化参数。
 *
 * 模糊 = 系统 `BackgroundBlurDrawable`（与快速启动器等面板同一套）。它只能画**硬边**圆角矩形
 * （`setBounds` + `setCornerRadius`，没有遮罩接口），所以「仅轮盘区域」时清晰↔模糊的分界
 * 会像刀切一样：背景纯色看不出来，背景有图标 / 文字时非常刺眼。
 *
 * 解法 = **同心阶梯**：把同一个模糊 drawable 由外到内画 N 次，范围逐级内缩、alpha 逐级抬高
 * （最外 1/N … 最内 1）。外层低 alpha ⇒ 只混入少量模糊，越往里越糊，累积到中心即全糊 ——
 * 等于给模糊也做了一条浓度渐变带，宽度就是羽化宽度。级宽约 4dp，N 取 2–8，肉眼基本无缝。
 *
 * 色调 = Compose 纯色形状整体过一遍 `blur`，边缘同样是浓度渐变。
 * 两层因此都在**同一条羽化带**里收边，不会再出现"着色和谐、模糊切一刀"。
 *
 * @param alpha 色调浓度（0–1）
 * @param colorArgb 色调（不透明 ARGB；实际透明度只用 [alpha]）
 * @param blurRadiusPx 模糊半径（px，0 = 不糊）
 * @param blurScope 模糊的作用范围
 * @param dimScope 色调的作用范围
 * @param featherPx 边缘羽化宽度（px，0 = 硬边）；模糊与色调共用
 * @param region 「仅轮盘区域」的几何（圆形 / 圆角矩形）
 */
@Composable
fun QuickWheelBackdropDim(
    alpha: Float,
    colorArgb: Int,
    blurRadiusPx: Int,
    blurScope: QuickWheelBackdropScope,
    dimScope: QuickWheelBackdropScope,
    featherPx: Float,
    region: QuickWheelBackdropRegion,
    modifier: Modifier = Modifier,
) {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    val radius = blurRadiusPx.coerceAtLeast(0)
    if (safeAlpha <= 0f && radius <= 0) return
    val feather = featherPx.coerceAtLeast(0f)

    Box(modifier = modifier.fillMaxSize()) {
        // 先模糊、再叠色：色调压在被糊掉的背景之上。
        if (radius > 0) {
            BackdropBlurLayer(
                radiusPx = radius,
                scope = blurScope,
                region = region,
                featherPx = feather,
            )
        }
        if (safeAlpha > 0f) {
            BackdropTintLayer(
                alpha = safeAlpha,
                colorArgb = colorArgb,
                scope = dimScope,
                region = region,
                featherPx = feather,
            )
        }
    }
}

// ── 模糊层：同心阶梯 ────────────────────────────────────

/**
 * 阶梯每级宽度（dp）：越小越平滑，绘制次数也越多。
 *
 * ⚠️ 别为了省 GPU 把它调大：台阶变宽 = 环纹更明显（详见 `onDraw` 里的结论注释）。
 */
private const val BLUR_FEATHER_STEP_DP = 4f

/**
 * 阶梯层数上限：再多看不出差别，只会白费 GPU（每层都是一条 SF 模糊区域）。
 *
 * ⚠️ 只在「仅轮盘 + 羽化 > 0」时生效；整屏与羽化 0 走单层。别按机型削减：
 * 常用档位（24dp = 6 层）省不下来，只会在 48dp+ 加宽台阶、让可用机型看到环纹。
 */
private const val BLUR_FEATHER_MAX_STEPS = 8

private const val TAG = "QuickWheelBlur"

@Composable
private fun BackdropBlurLayer(
    radiusPx: Int,
    scope: QuickWheelBackdropScope,
    region: QuickWheelBackdropRegion,
    featherPx: Float,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> QuickWheelBlurView(ctx) },
        update = { view ->
            view.blurRadiusPx = radiusPx
            view.featherPx = featherPx
            view.blurScope = scope
            view.region = region
            view.invalidate()
        },
    )
}

/**
 * 铺满窗口的透明 View，把系统模糊 drawable 画成**浓度渐变**。
 *
 * ⚠️ 每一层必须用**独立的 drawable**（不能复用同一个实例）。模糊由 SurfaceFlinger 按
 * `BackgroundBlurDrawable` 的矩形合成，而一张 drawable 只有一个 RenderNode / 一个矩形：
 * 同一帧里反复 `setBounds + draw` 只会保留**最后一次**的矩形和 alpha，阶梯会被压成一层
 * （表现就是"改了代码但边界还是硬的"）。每层一张 drawable 才能上报 N 条 BlurRegion。
 */
private class QuickWheelBlurView(context: Context) : View(context) {

    var blurRadiusPx: Int = 0
    var featherPx: Float = 0f
    var blurScope: QuickWheelBackdropScope = QuickWheelBackdropScope.FULL_SCREEN
    var region: QuickWheelBackdropRegion = QuickWheelBackdropRegion.None

    /** 每层一张 drawable（懒创建），下标 0 = 最内层（alpha 最大）。 */
    private val blurLayers = ArrayList<LocalFrostedGlassDrawable>(BLUR_FEATHER_MAX_STEPS)
    private val rect = RectF()

    /** 只在层数变化时打一条日志，便于真机排查（`adb logcat -s QuickWheelBlur`）。 */
    private var loggedSteps = -1

    override fun onDraw(canvas: Canvas) {
        if (blurRadiusPx <= 0 || width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val feather = if (blurScope == QuickWheelBackdropScope.WHEEL_ONLY) {
            featherPx.coerceAtLeast(0f)
        } else {
            // 整屏模糊没有边界，不需要阶梯。
            0f
        }
        // 【TEMP 诊断】只画 1 层，alpha 固定 0.35：测系统是否真的按 region alpha 缩放模糊。
        // 若整块明显"变清晰/变淡" → alpha 有效；若与 alpha=1 完全一致 → alpha 被忽略。
        // ── 阶梯的启用条件与开销（结论，别再随手调参数）────────────────────────
        // 1) 只有「模糊范围 = 仅轮盘」且「羽化 > 0」时才画多层：上面 feather 在整屏模式下已置 0，
        //    所以「整屏」与「羽化 0」自动退化成 1 层 —— 零额外开销，不需要额外判断。
        // 2) 台阶宽度 = 羽化 / 层数，**看得见的环纹由台阶宽度决定，不是层数**：
        //    24dp 羽化 = 6 层 × 4dp；若为了省 GPU 改成 6dp/级、封顶 6 层，常用档位省不下来，
        //    只会在 48dp+ 把台阶加宽（8dp/级），让"能吃这个效果"的机型更容易看到环纹。
        //    所以这里**不做机型判断、也不减层**：能生效的生效，不能生效的（实测 ColorOS 忽略
        //    BlurRegion.alpha）自然退化成一层硬边。
        // 3) 开销量级：每次呼出建 N 张 blur drawable（几十 µs/张，一次性）；显示期间每帧 N 次
        //    模糊区域采样 + 少量反射（约 10–50µs）。真正的大头是"每次呼出新建浮层窗口"，
        //    那是既有设计，与背景层无关。
        val steps = if (feather <= 0f) {
            1
        } else {
            ceil(feather / (BLUR_FEATHER_STEP_DP * density))
                .toInt()
                .coerceIn(2, BLUR_FEATHER_MAX_STEPS)
        }
        if (steps != loggedSteps) {
            loggedSteps = steps
            Log.d(
                TAG,
                "blur ladder: steps=$steps featherPx=$feather blurPx=$blurRadiusPx scope=$blurScope",
            )
        }
        // 由外向内：外圈 alpha 最低（≈ 半糊），内圈叠加到不透明（全糊），累积成渐变带。
        // ⚠️ 部分 ROM（实测 ColorOS）会忽略 BlurRegion.alpha，此时阶梯退化成一层、边界仍硬 ——
        // 这是系统差异，不做机型判断（能生效的生效，不生效的保持现状）。
        for (index in steps - 1 downTo 0) {
            val inset = feather * index / steps
            val layerAlpha = (steps - index).toFloat() / steps
            if (!drawLayer(layerAt(index), canvas, inset, layerAlpha)) {
                // 拿不到 drawable（窗口不支持 / 非硬件加速）：把所有层都清掉，别留残影。
                for (i in 0 until blurLayers.size) blurLayers[i].clear()
                return
            }
        }
        // 本帧用不到的层（羽化刚被调小）要主动清掉，否则上一帧的区域会继续参与合成。
        for (i in steps until blurLayers.size) blurLayers[i].clear()
    }

    private fun layerAt(index: Int): LocalFrostedGlassDrawable {
        while (blurLayers.size <= index) blurLayers.add(LocalFrostedGlassDrawable(this))
        return blurLayers[index]
    }

    /** 画一层；false = 拿不到 blur drawable（窗口不支持），后面的层也不用再试。 */
    private fun drawLayer(
        layer: LocalFrostedGlassDrawable,
        canvas: Canvas,
        inset: Float,
        layerAlpha: Float,
    ): Boolean {
        if (blurScope != QuickWheelBackdropScope.WHEEL_ONLY) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            return layer.draw(canvas, rect, 0f, blurRadiusPx, 0, layerAlpha)
        }
        return when (val r = region) {
            is QuickWheelBackdropRegion.Circle -> {
                val side = (r.radiusPx - inset) * 2f
                if (side <= 0f) return true
                rect.set(
                    r.centerX - side / 2f,
                    r.centerY - side / 2f,
                    r.centerX + side / 2f,
                    r.centerY + side / 2f,
                )
                // 圆角 = 半个边长 ⇒ 正圆。
                layer.draw(canvas, rect, side / 2f, blurRadiusPx, 0, layerAlpha)
            }

            is QuickWheelBackdropRegion.RoundedBox -> {
                if (r.right - r.left <= inset * 2f || r.bottom - r.top <= inset * 2f) return true
                rect.set(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset)
                layer.draw(canvas, rect, max(r.cornerPx - inset, 0f), blurRadiusPx, 0, layerAlpha)
            }

            QuickWheelBackdropRegion.None -> return true
        }
    }
}

// ── 色调层 ──────────────────────────────────────────────

@Composable
private fun BackdropTintLayer(
    alpha: Float,
    colorArgb: Int,
    scope: QuickWheelBackdropScope,
    region: QuickWheelBackdropRegion,
    featherPx: Float,
) {
    val tintColor = Color(colorArgb).copy(alpha = alpha)
    val density = LocalDensity.current

    if (scope != QuickWheelBackdropScope.WHEEL_ONLY) {
        Box(modifier = Modifier.fillMaxSize().background(tintColor))
        return
    }

    // 羽化半径取宽度的一半：高斯衰减的可见范围约为半径的 2 倍，正好覆盖设定的宽度。
    val featherDp = with(density) { (featherPx.coerceAtLeast(0f) / 2f).toDp() }
    val featherModifier = if (featherPx > 0f) {
        Modifier.blur(radius = featherDp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (region) {
            is QuickWheelBackdropRegion.Circle -> {
                val sidePx = region.radiusPx * 2f
                if (sidePx <= 0f) return@Box
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (region.centerX - region.radiusPx).toDp() },
                            y = with(density) { (region.centerY - region.radiusPx).toDp() },
                        )
                        .size(with(density) { sidePx.toDp() })
                        .then(featherModifier),
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(tintColor, CircleShape))
                }
            }

            is QuickWheelBackdropRegion.RoundedBox -> {
                val widthPx = (region.right - region.left).coerceAtLeast(0f)
                val heightPx = (region.bottom - region.top).coerceAtLeast(0f)
                if (widthPx <= 0f || heightPx <= 0f) return@Box
                val shape = RoundedCornerShape(with(density) { region.cornerPx.coerceAtLeast(0f).toDp() })
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { region.left.toDp() },
                            y = with(density) { region.top.toDp() },
                        )
                        .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                        .then(featherModifier),
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(tintColor, shape))
                }
            }

            QuickWheelBackdropRegion.None -> Unit
        }
    }
}
