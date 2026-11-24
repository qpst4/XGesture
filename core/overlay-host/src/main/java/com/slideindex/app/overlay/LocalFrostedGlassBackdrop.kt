package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LocalFrostedGlassBackdrop(
    modifier: Modifier = Modifier,
    spec: OverlayMaterialSpec = rememberOverlayMaterialSpec(),
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    cornerRadiusPx: Float? = null,
    blurRadiusPx: Int? = null,
    @ColorInt tintColor: Int? = null,
    enabled: Boolean? = null,
    @ColorInt solidFallbackColor: Int? = null,
    drawBorder: Boolean = true
) {
    val density = LocalDensity.current.density
    val finalCornerRadius = cornerRadiusPx ?: spec.cornerRadiusPx(density)
    val finalBlurRadius = blurRadiusPx ?: spec.blurRadiusPx(density)
    val finalTint = tintColor ?: spec.tintColor(isDark)
    val finalEnabled = enabled ?: spec.blurEnabled
    val finalFallback = solidFallbackColor ?: spec.solidFallbackColor(isDark)

    if (!finalEnabled) return

    val strokeWidth = with(LocalDensity.current) { spec.innerBorderWidthPx(density).toDp() }
    val strokeColor = Color(spec.innerBorderColor(isDark))
    val corner = with(LocalDensity.current) { finalCornerRadius.toDp() }
    val borderModifier = if (drawBorder && finalCornerRadius > 0f) {
        Modifier.border(strokeWidth, strokeColor, RoundedCornerShape(corner))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .then(borderModifier)
    ) {
        if (!canBlurInOverlay()) {
            SolidPanelBackdrop(
                modifier = Modifier.matchParentSize(),
                cornerRadiusPx = finalCornerRadius,
                solidColor = finalFallback
            )
        } else {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(v: View, outline: Outline) {
                                outline.setRoundRect(0, 0, v.width, v.height, finalCornerRadius)
                            }
                        }
                        clipToOutline = true
                        viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                viewTreeObserver.removeOnPreDrawListener(this)
                                setupBackgroundBlur(this@apply, finalCornerRadius, finalBlurRadius, finalTint)
                                postInvalidateOnAnimation()
                                return true
                            }
                        })
                    }
                },
                update = { view ->
                    setupBackgroundBlur(view, finalCornerRadius, finalBlurRadius, finalTint)
                }
            )
        }

        val radialAlpha = spec.radialGlossAlpha(isDark)
        val verticalAlpha = spec.verticalGlossAlpha(isDark)
        if (radialAlpha > 0.001f || verticalAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        // 1. 左上角定向高光（径向渐变）
                        if (radialAlpha > 0.001f) {
                            val gloss = Color.White.copy(alpha = radialAlpha)
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
                        }
                        // 2. 顶部垂直微光（线性渐变）
                        if (verticalAlpha > 0.001f) {
                            val gloss = Color.White.copy(alpha = verticalAlpha)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to gloss,
                                        0.38f to Color.Transparent,
                                    ),
                                ),
                            )
                        }
                    }
            )
        }
    }
}

@Composable
fun LocalFrostedGlassBackdrop(
    modifier: Modifier = Modifier,
    cornerRadiusPx: Float? = null,
    blurRadiusPx: Int? = null,
    @ColorInt tintColor: Int? = null,
    enabled: Boolean? = null,
    @ColorInt solidFallbackColor: Int? = null,
    drawBorder: Boolean = true
) {
    LocalFrostedGlassBackdrop(
        modifier = modifier,
        spec = rememberOverlayMaterialSpec(),
        cornerRadiusPx = cornerRadiusPx,
        blurRadiusPx = blurRadiusPx,
        tintColor = tintColor,
        enabled = enabled,
        solidFallbackColor = solidFallbackColor,
        drawBorder = drawBorder
    )
}

/**
 * 模糊不可用（总开关关闭 / 系统不支持跨窗模糊 / 反射失败）时的实色降级。
 *
 * 这层 backdrop 往往是面板**唯一**的背景来源（取词等面板的卡片自身没有背景色），
 * 所以不能简单跳过，否则面板会塌成半透明 tint 甚至全透明。
 *
 * [solidColor] 为 null 时用主题容器色；写死白色前景的面板要显式传深色。
 */
@Composable
private fun SolidPanelBackdrop(
    modifier: Modifier,
    cornerRadiusPx: Float,
    @ColorInt solidColor: Int? = null
) {
    val corner = with(LocalDensity.current) { cornerRadiusPx.toDp() }
    val color = solidColor?.let { Color(it) } ?: MiuixTheme.colorScheme.surfaceContainer
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(color),
    )
}

/**
 * 面板内 backdrop 这条路上能否真的糊：**用户总开关 × 系统跨窗模糊能力 × 窗口是否硬件加速**，
 * 走 [OverlayBlurGate] 这个唯一决策点。
 *
 * 只问用户开关是不够的：ROM 关掉跨窗模糊时 [setupBackgroundBlur] 的反射会静默失败
 * （只打一条日志），面板就只剩 tint 甚至什么都没有 —— 与「关闭模糊后塌成浅色底」是同一类
 * 现象。所以系统那半边必须一起问，问不到就按实色降级。
 *
 * 还必须是硬件加速窗口：`BackgroundBlurDrawable.draw()` 走 `Canvas.drawRenderNode`，
 * 软件 Canvas（`ViewRootImpl.drawSoftware`）上会直接抛 `IllegalArgumentException`。
 * 浮窗完全可能整窗软件渲染（见 `MiuixOverlayComposeLocals` 的同款结论），此时必须降级实色。
 */
@Composable
fun canBlurInOverlay(): Boolean {
    if (!rememberOverlayBlurEnabled()) return false
    val view = LocalView.current
    // 未 attach 时 isHardwareAccelerated 恒为 false，不能据此判定；交给 setupBackgroundBlur 兜底。
    if (view.isAttachedToWindow && !view.isHardwareAccelerated) return false
    val context = LocalContext.current
    val windowManager = remember(context) {
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }
    return OverlayBlurGate.isSystemBlurEnabled(windowManager)
}

fun setupBackgroundBlur(
    view: View,
    cornerRadiusPx: Float,
    blurRadiusPx: Int,
    @ColorInt tintColor: Int,
    /** 拿不到 drawable 时最多补 post 重试几次（见下）。 */
    retriesLeft: Int = 1,
) {
    // 软件渲染的窗口不能挂 BackgroundBlurDrawable：绘制期 Canvas.drawRenderNode 会抛
    // IllegalArgumentException（崩溃在 View.draw() 里，外层的 runCatching 兜不住）。
    // 未 attach 时 isHardwareAccelerated 恒为 false，不能据此判定，留给下面的 post 重试。
    if (view.isAttachedToWindow && !view.isHardwareAccelerated) {
        Log.w(
            "LocalFrostedGlass",
            "setupBackgroundBlur skipped: window is software-rendered (${view.javaClass.simpleName})"
        )
        // 已经挂上的 blur 背景必须摘掉：窗口转软件渲染后它仍会走 drawRenderNode 崩掉。
        if (view.background?.javaClass?.name?.contains("BackgroundBlurDrawable") == true) {
            view.background = null
        }
        return
    }
    runCatching {
        val blurDrawable = view.background?.takeIf {
            it.javaClass.name.contains("BackgroundBlurDrawable")
        } ?: run {
            val created = LocalFrostedGlassDrawable.createBlurDrawable(view)
            if (created == null) {
                // 窗口刚 add、`ViewRootImpl` 还没就绪时只能拿到 null。update 只在重组时跑，
                // 不补这一次重试，这扇窗整个生命周期都不会有 backdrop（只剩半透明底色）。
                // 只补一次：`post` 一定跑在 add 之后，仍拿不到就是该窗口不支持，重试无意义。
                if (retriesLeft > 0) {
                    view.post {
                        if (view.isAttachedToWindow) {
                            setupBackgroundBlur(view, cornerRadiusPx, blurRadiusPx, tintColor, retriesLeft - 1)
                        }
                    }
                }
                return
            }
            view.background = created
            created
        }
        val setBlurRadius = blurDrawable.javaClass.getMethod("setBlurRadius", java.lang.Integer.TYPE)
        val setCornerRadius = blurDrawable.javaClass.getMethod("setCornerRadius", java.lang.Float.TYPE)
        val setColor = blurDrawable.javaClass.getMethod("setColor", java.lang.Integer.TYPE)
        setBlurRadius.invoke(blurDrawable, blurRadiusPx)
        setCornerRadius.invoke(blurDrawable, cornerRadiusPx)
        setColor.invoke(blurDrawable, tintColor)
    }.onFailure { Log.w("LocalFrostedGlass", "setupBackgroundBlur failed", it) }
}

/**
 * Reusable Canvas-level helper for drawing hardware BackgroundBlurDrawable
 * inside custom View onDraw(canvas) or overlay renderers.
 */
class LocalFrostedGlassDrawable(private val viewProvider: () -> View?) {
    constructor(view: View) : this({ view })
    private var blurDrawable: Drawable? = null
    private var lastViewRootImpl: Any? = null
    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val tmpBorderRect = RectF()
    fun reset() {
        blurDrawable = null
        lastViewRootImpl = null
    }

    /**
     * 让这张 drawable 立刻从合成里退出（alpha = 0 → 系统下次事务会移除它的模糊区域）。
     *
     * 为什么需要：模糊不是应用画出来的，而是把 RenderNode 交给 SurfaceFlinger，由它在该
     * drawable 的**矩形**上合成。一张 drawable 只有一个 RenderNode / 一个矩形，所以想在同一帧
     * 画多个"不同范围 + 不同透明度"的模糊，必须**每层各一张**；本帧用不到的层要主动清掉，
     * 否则会保留上一帧的区域继续参与合成。
     */
    fun clear() {
        runCatching { blurDrawable?.alpha = 0 }
    }

    // 反射取 ViewRootImpl：拿系统私有字段做模糊背景，失败已用 runCatching 兜住。
    @android.annotation.SuppressLint("PrivateApi")
    private fun ensureDrawable(): Drawable? {
        val view = viewProvider() ?: return null
        if (!view.isAttachedToWindow) return null
        val currentVri = runCatching {
            val getViewRootImplMethod = View::class.java.getDeclaredMethod("getViewRootImpl")
            getViewRootImplMethod.isAccessible = true
            getViewRootImplMethod.invoke(view)
        }.getOrNull() ?: return null

        if (blurDrawable != null && lastViewRootImpl === currentVri) {
            return blurDrawable
        }

        lastViewRootImpl = currentVri
        blurDrawable = runCatching {
            val createMethod = currentVri.javaClass.getMethod("createBackgroundBlurDrawable")
            createMethod.invoke(currentVri) as? Drawable
        }.getOrNull()
        return blurDrawable
    }

    // @JvmOverloads：蜂窝浮层（HoneycombOverlayView.java）仍按 6 参调用，默认 enabled = true。
    @JvmOverloads
    fun draw(
        canvas: Canvas,
        bounds: RectF,
        cornerRadiusPx: Float,
        blurRadiusPx: Int,
        @ColorInt tintColor: Int,
        alpha: Float = 1f,
        /** 调用点表达的意图（如全局模糊总开关）；false 时直接不画，由调用点走实色兜底。 */
        enabled: Boolean = true
    ): Boolean {
        if (!enabled) return false
        if (bounds.isEmpty || alpha <= 0.001f) return false
        val drawable = ensureDrawable() ?: return false
        return runCatching {
            val setBlurRadius = drawable.javaClass.getMethod("setBlurRadius", java.lang.Integer.TYPE)
            val setCornerRadius = drawable.javaClass.getMethod("setCornerRadius", java.lang.Float.TYPE)
            val setColor = drawable.javaClass.getMethod("setColor", java.lang.Integer.TYPE)
            setBlurRadius.invoke(drawable, blurRadiusPx)
            setCornerRadius.invoke(drawable, cornerRadiusPx)
            setColor.invoke(drawable, tintColor)
            drawable.setBounds(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt()
            )
            drawable.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
            drawable.draw(canvas)
            true
        }.getOrDefault(false)
    }

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        spec: OverlayMaterialSpec,
        density: Float,
        isDark: Boolean,
        cornerRadiusPx: Float? = null,
        blurRadiusPx: Int? = null,
        @ColorInt tintColor: Int? = null,
        alpha: Float = 1f,
        enabled: Boolean? = null,
        drawBorder: Boolean = true
    ): Boolean {
        val finalCornerRadius = cornerRadiusPx ?: spec.cornerRadiusPx(density)
        val finalBlurRadius = blurRadiusPx ?: spec.blurRadiusPx(density)
        val finalTint = tintColor ?: spec.tintColor(isDark)
        val finalEnabled = enabled ?: spec.blurEnabled
        val drawn = draw(
            canvas = canvas,
            bounds = bounds,
            cornerRadiusPx = finalCornerRadius,
            blurRadiusPx = finalBlurRadius,
            tintColor = finalTint,
            alpha = alpha,
            enabled = finalEnabled
        )
        if (drawn && finalCornerRadius > 0f) {
            val radialAlpha = spec.radialGlossAlpha(isDark) * alpha.coerceIn(0f, 1f)
            val verticalAlpha = spec.verticalGlossAlpha(isDark) * alpha.coerceIn(0f, 1f)
            if ((radialAlpha > 0.001f || verticalAlpha > 0.001f) && bounds.width() > 0f && bounds.height() > 0f) {
                clipPath.reset()
                clipPath.addRoundRect(bounds, finalCornerRadius, finalCornerRadius, Path.Direction.CW)
                val saveCount = canvas.save()
                canvas.clipPath(clipPath)
                // 1. 左上角定向高光（径向渐变）
                if (radialAlpha > 0.001f) {
                    val glossColor = android.graphics.Color.argb((radialAlpha * 255).toInt(), 255, 255, 255)
                    glossPaint.shader = RadialGradient(
                        bounds.left + bounds.width() * 0.16f,
                        bounds.top - bounds.height() * 0.22f,
                        bounds.width() * 1.30f,
                        intArrayOf(glossColor, android.graphics.Color.TRANSPARENT),
                        floatArrayOf(0f, 0.56f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, glossPaint)
                }
                // 2. 顶部垂直微光（线性渐变）
                if (verticalAlpha > 0.001f) {
                    val topGlossColor = android.graphics.Color.argb((verticalAlpha * 255).toInt(), 255, 255, 255)
                    glossPaint.shader = LinearGradient(
                        bounds.left, bounds.top, bounds.left, bounds.top + bounds.height() * 0.38f,
                        topGlossColor, android.graphics.Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, glossPaint)
                }
                canvas.restoreToCount(saveCount)
            }
            if (drawBorder) {
                val strokeWidth = spec.innerBorderWidthPx(density)
                innerBorderPaint.strokeWidth = strokeWidth
                innerBorderPaint.color = spec.innerBorderColor(isDark)
                val baseAlpha = (spec.innerBorderColor(isDark) ushr 24)
                innerBorderPaint.alpha = (baseAlpha * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
                val halfStroke = strokeWidth / 2f
                tmpBorderRect.set(
                    bounds.left + halfStroke,
                    bounds.top + halfStroke,
                    bounds.right - halfStroke,
                    bounds.bottom - halfStroke
                )
                canvas.drawRoundRect(tmpBorderRect, finalCornerRadius, finalCornerRadius, innerBorderPaint)
            }
        }
        return drawn
    }

    companion object {
        fun createBlurDrawable(view: View): Drawable? {
            return runCatching {
                val getViewRootImplMethod = View::class.java.getDeclaredMethod("getViewRootImpl")
                getViewRootImplMethod.isAccessible = true
                val viewRootImpl = getViewRootImplMethod.invoke(view) ?: return null
                val createMethod = viewRootImpl.javaClass.getMethod("createBackgroundBlurDrawable")
                createMethod.invoke(viewRootImpl) as? Drawable
            }.getOrNull()
        }
    }
}

/**
 * 专为 Android 原生 View / FrameLayout 设计的玻璃高光与描边前景 Drawable。
 *
 * 自动渲染：
 * 1. 左上角定向聚光（RadialGradient）
 * 2. 顶部垂直微光（LinearGradient）
 * 3. 0.75dp 透亮白描边
 */
class OverlayGlassForegroundDrawable(
    private val spec: OverlayMaterialSpec,
    private val isDark: Boolean,
    private val cornerRadiusPx: Float,
    private val density: Float = 1f,
    private val drawBorder: Boolean = true,
) : Drawable() {
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val clipPath = Path()
    private val boundsRect = RectF()
    private val borderRect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty || cornerRadiusPx <= 0f) return
        boundsRect.set(b)

        val radialAlpha = spec.radialGlossAlpha(isDark)
        val verticalAlpha = spec.verticalGlossAlpha(isDark)
        if ((radialAlpha > 0.001f || verticalAlpha > 0.001f) && boundsRect.width() > 0f && boundsRect.height() > 0f) {
            clipPath.reset()
            clipPath.addRoundRect(boundsRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            val saveCount = canvas.save()
            canvas.clipPath(clipPath)

            // 1. 左上角定向高光（径向渐变）
            if (radialAlpha > 0.001f) {
                val glossColor = android.graphics.Color.argb((radialAlpha * 255).toInt(), 255, 255, 255)
                glossPaint.shader = RadialGradient(
                    boundsRect.left + boundsRect.width() * 0.16f,
                    boundsRect.top - boundsRect.height() * 0.22f,
                    boundsRect.width() * 1.30f,
                    intArrayOf(glossColor, android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, 0.56f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(boundsRect, glossPaint)
            }

            // 2. 顶部垂直微光（线性渐变）
            if (verticalAlpha > 0.001f) {
                val topGlossColor = android.graphics.Color.argb((verticalAlpha * 255).toInt(), 255, 255, 255)
                glossPaint.shader = LinearGradient(
                    boundsRect.left, boundsRect.top, boundsRect.left, boundsRect.top + boundsRect.height() * 0.38f,
                    topGlossColor, android.graphics.Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(boundsRect, glossPaint)
            }
            canvas.restoreToCount(saveCount)
        }

        if (drawBorder) {
            val strokeWidth = spec.innerBorderWidthPx(density)
            borderPaint.strokeWidth = strokeWidth
            borderPaint.color = spec.innerBorderColor(isDark)
            val halfStroke = strokeWidth / 2f
            borderRect.set(
                boundsRect.left + halfStroke,
                boundsRect.top + halfStroke,
                boundsRect.right - halfStroke,
                boundsRect.bottom - halfStroke
            )
            canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
