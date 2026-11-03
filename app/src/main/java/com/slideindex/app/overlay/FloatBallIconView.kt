package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FloatBallStyleType
import com.slideindex.app.ui.miuix.theme.resolveAppAccentArgb

/**
 * Unified native float-ball surface for idle + drag (FV FloatIconView-style).
 * Parent positions this view; [bind] only manages appearance and inner layout gravity.
 */
internal class FloatBallIconView(context: Context) : FrameLayout(context) {

    private enum class ContentMode {
        STATIC,
        GIF,
        BUILTIN,
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val staticImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val gifView = FloatBallGifView(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val builtinAnimView = FloatBallBuiltinAnimView(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val gifPlayer = FloatBallGifPlayer()

    private var contentMode = ContentMode.STATIC
    private var boundKey: String? = null
    /** 最近一次 bind 的设置：图片读取失败时用来渲染兜底球。 */
    private var boundSettings: AppSettings? = null
    private var ownedBitmap: Bitmap? = null
    private var slideshowUris: List<String> = emptyList()
    private var slideshowIndex = 0
    private var gifDecodeToken = 0
    private var gifSequenceHandle: FloatBallGifSequenceCache.Handle? = null

    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (contentMode != ContentMode.STATIC || slideshowUris.size <= 1) return
            slideshowIndex = (slideshowIndex + 1) % slideshowUris.size
            showStaticUri(slideshowUris[slideshowIndex], currentOpacity())
            mainHandler.postDelayed(this, SLIDESHOW_INTERVAL_MS)
        }
    }

    private var currentOpacityValue = 1f

    /** 「闲置时停止动图」的冻结状态：setDragging 等路径不能把它顶掉。 */
    private var gifFrozen = false

    /** 闲置隐身：非 null 时覆盖根 View 的 alpha（null = 跟随设置不透明度）。 */
    private var alphaOverride: Float? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipChildren = true
        addView(staticImage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(gifView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(builtinAnimView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        gifPlayer.attach(gifView)
        showContentMode(ContentMode.STATIC)
    }

    fun bind(
        settings: AppSettings,
        activeSide: FloatBallSide,
        styleGeneration: Int
    ) {
        val density = resources.displayMetrics.density
        val ballSize = FloatBallLayout.ballSize(settings, density)
        // 「不处理」：按原图比例显示，不裁剪也不套圆。只有自定义图片会走到这里。
        val noCrop = FloatBallLayout.usesOriginalImageGeometry(settings)
        val opacity = settings.floatBallOpacity.coerceIn(0f, 1f)
        currentOpacityValue = opacity
        // 有效 alpha：闲置隐身期间用覆盖值。GIF 是 SurfaceView，不吃父级 alpha，必须单独喂给它。
        val effectiveOpacity = alphaOverride ?: opacity
        alpha = effectiveOpacity
        boundSettings = settings

        val key = buildBindKey(settings, activeSide, styleGeneration, ballSize)
        if (key == boundKey) {
            applyChildGravity(settings, activeSide)
            applyBallClip(circular = !noCrop)
            // 兜底：内置动画的画面完全依赖 View 内部的"当前样式"。窗口被摘掉再挂回（取词/截图）时，
            // 即使样式没变也必须把样式重新喂给它，否则它没有内容可画（球会变透明）。setStyle 同样式是幂等的。
            if (FloatBallBuiltinAnimCatalog.isBuiltinAnimated(settings.floatBallStyleType)) {
                builtinAnimView.setStyle(settings.floatBallStyleType)
            }
            return
        }
        boundKey = key
        applyChildGravity(settings, activeSide)
        applyBallClip(circular = !noCrop)
        staticImage.scaleType = if (noCrop) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP

        when (settings.floatBallStyleType) {
            FloatBallStyleType.ANIMATED_PLANE,
            FloatBallStyleType.ANIMATED_PULSE,
            FloatBallStyleType.ANIMATED_ORBIT,
            -> showBuiltin(settings.floatBallStyleType, opacity)

            FloatBallStyleType.GIF -> showGif(settings, effectiveOpacity, ballSize.heightPx)

            FloatBallStyleType.CUSTOM_IMAGE -> {
                stopSlideshow()
                showStaticUri(settings.floatBallCustomImageUri, opacity)
            }

            FloatBallStyleType.SLIDESHOW -> {
                val uris = settings.floatBallSlideshowUris
                if (uris.isEmpty()) {
                    stopSlideshow()
                    showStaticBitmap(FloatBallDragVisualRenderer.render(context, settings), owns = true)
                } else {
                    slideshowUris = uris
                    slideshowIndex = 0
                    showStaticUri(uris.first(), opacity)
                    stopSlideshow()
                    if (uris.size > 1) {
                        mainHandler.postDelayed(slideshowRunnable, SLIDESHOW_INTERVAL_MS)
                    }
                }
            }

            FloatBallStyleType.DEFAULT -> {
                stopSlideshow()
                showStaticBitmap(FloatBallDragVisualRenderer.render(context, settings), owns = true)
            }
        }
    }

    /**
     * 闲置隐身：把球直接渲染成 [value]（0 = 完全看不见），null = 恢复「跟随设置不透明度」。
     *
     * 只改根 View 的 alpha，不动命中、也不动子 View 的 alpha（避免和设置不透明度叠两次）。
     * GIF 例外：它是 SurfaceView，画面在独立图层里，根 View 的 alpha 管不到，
     * 所以必须把同一个有效 alpha 再喂给 [FloatBallGifView.setFrameAlpha]，否则隐身对 GIF 无效。
     */
    fun setAlphaOverride(value: Float?) {
        alphaOverride = value?.coerceIn(0f, 1f)
        val effective = alphaOverride ?: currentOpacityValue
        val wasInvisible = gifView.isFrameInvisible()
        alpha = effective
        gifView.setFrameAlpha(effective)
        // 从“完全透明”恢复：隐藏期间播放器已退化成每秒一次探测，这里立刻叫醒它。
        if (wasInvisible && !gifView.isFrameInvisible() && contentMode == ContentMode.GIF) {
            gifPlayer.wake()
        }
    }

    /** 闲置定格：停止 GIF 出帧但保留当前画面；false 恢复播放。 */
    fun setGifFrozen(frozen: Boolean) {
        if (contentMode != ContentMode.GIF) return
        gifFrozen = frozen
        gifPlayer.setPaused(frozen)
    }

    fun setDragging(dragging: Boolean) {
        when (contentMode) {
            // 冻结期间不能被布局刷新顺手解冻（否则「闲置时停止动图」会被每帧顶掉）。
            ContentMode.GIF -> if (!gifFrozen) gifPlayer.setPaused(false)
            ContentMode.BUILTIN -> builtinAnimView.setPaused(false)
            ContentMode.STATIC -> Unit
        }
    }

    fun release() {
        stopSlideshow()
        gifDecodeToken++
        gifPlayer.detach()
        releaseGifSequence()
        builtinAnimView.releaseAnimation()
        releaseOwnedBitmap()
        staticImage.setImageDrawable(null)
        gifView.clearFrame()
        boundKey = null
        boundSettings = null
        slideshowUris = emptyList()
    }

    private fun buildBindKey(
        settings: AppSettings,
        activeSide: FloatBallSide,
        styleGeneration: Int,
        ballSize: FloatBallSize
    ): String = buildString {
        append(styleGeneration)
        append('|')
        append(settings.floatBallStyleType.name)
        append('|')
        append(activeSide.name)
        append('|')
        append(ballSize.widthPx)
        append('x')
        append(ballSize.heightPx)
        append('|')
        // ⚠️ 这里必须是**解析后的强调色**（含"自定义色开关 / 动态取色 / 色板 / 深浅"的影响），
        // 不能只放 themeColorArgb：开关一翻而色值没变时，键不变 → 球不会重画 → 还是旧颜色。
        append(resolveAppAccentArgb(context, settings))
        append('|')
        append(settings.floatBallOpacity)
        append('|')
        append(settings.floatBallCustomImageUri)
        append('|')
        // 不处理开关与宽高比都要进键：否则改了开关/换了图（同 URI 不会被换，但比例会变）球不重绘。
        append(settings.floatBallCustomImageNoCrop)
        append('|')
        append(settings.floatBallCustomImageAspect)
        append('|')
        append(settings.floatBallGifUri)
        append('|')
        append(settings.floatBallSlideshowUris.joinToString(","))
    }

    private fun currentOpacity(): Float = currentOpacityValue

    private fun showContentMode(mode: ContentMode) {
        contentMode = mode
        staticImage.visibility = if (mode == ContentMode.STATIC) View.VISIBLE else View.GONE
        gifView.visibility = if (mode == ContentMode.GIF) View.VISIBLE else View.GONE
        builtinAnimView.visibility = if (mode == ContentMode.BUILTIN) View.VISIBLE else View.GONE
    }

    private fun showBuiltin(styleType: FloatBallStyleType, opacity: Float) {
        stopSlideshow()
        gifPlayer.release()
        releaseGifSequence()
        releaseOwnedBitmap()
        showContentMode(ContentMode.BUILTIN)
        builtinAnimView.alpha = opacity
        builtinAnimView.setStyle(styleType)
        builtinAnimView.setPaused(false)
    }

    private fun showGif(settings: AppSettings, opacity: Float, decodePx: Int) {
        // 防御：gifView 是球自己的常驻子 View，任何时候进 GIF 分支都重新挂一次（幂等）。
        // 之前只有在构造时 attach 过一次，一旦被 release 置空，切回 GIF 就永远画不出来。
        gifPlayer.attach(gifView)
        stopSlideshow()
        releaseOwnedBitmap()
        builtinAnimView.releaseAnimation()
        showContentMode(ContentMode.GIF)
        gifView.setFrameAlpha(opacity)

        val uri = settings.floatBallGifUri
        Log.i(
            TAG,
            "showGif: decodePx=$decodePx opacity=$opacity gifView=${gifView.visibility} " +
                "playerView=${gifPlayer.hasView()} uri=${uri.takeLast(40)}"
        )
        if (uri.isBlank() || !FloatBallStyleAssetStore.canRead(context, uri)) {
            showStaticBitmap(FloatBallDragVisualRenderer.render(context, settings), owns = true)
            return
        }

        // 软件 surface 播放：帧解码后由 FloatBallGifView 在自己的渲染线程 lockCanvas 画上去，
        // 不再经过 HWUI 的 Vulkan 提交与逐帧纹理上传（对齐 FV 的做法）。
        val token = ++gifDecodeToken
        Thread {
            // 策略：小 GIF（能全帧缓存）走“预解码帧 + tick”——实测运行成本最低；
            // 大 GIF（只能流式）或解码失败才用 ImageDecoder / AnimatedImageDrawable。
            val decodeStartedAt = android.os.SystemClock.uptimeMillis()
            val handle = FloatBallGifSequenceCache.acquire(context, uri, decodePx)
            Log.i(
                TAG,
                "gif decode: ${android.os.SystemClock.uptimeMillis() - decodeStartedAt}ms " +
                    "token=$token seq=${describeSequence(handle?.sequence)}"
            )
            val cachedSequence = handle?.sequence
            if (cachedSequence !is FloatBallGifFrameDecoder.Sequence.Cached) {
                val animated = runCatching {
                    val source = android.graphics.ImageDecoder.createSource(
                        context.contentResolver,
                        android.net.Uri.parse(uri),
                    )
                    android.graphics.ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                        decoder.setTargetSize(decodePx.coerceAtLeast(1), decodePx.coerceAtLeast(1))
                    }
                }.getOrNull()
                if (animated is android.graphics.drawable.AnimatedImageDrawable) {
                    runCatching {
                        animated.repeatCount =
                            android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                    }
                    handle?.close()
                    mainHandler.post {
                        if (token != gifDecodeToken) return@post
                        gifPlayer.setAnimatedDrawable(animated)
                    }
                    return@Thread
                }
            }
            mainHandler.post {
                if (token != gifDecodeToken) {
                    Log.i(TAG, "gif apply dropped: token=$token current=$gifDecodeToken")
                    handle?.close()
                    return@post
                }
                if (handle != null) {
                    retainGifSequence(handle)
                    FloatBallGifDragSnapshot.update(uri, decodePx, handle.sequence)
                    gifPlayer.setSequence(handle.sequence)
                    // 重新绑定后仍要尊重冻结状态（否则改设置/重建视图会把冻结顶掉）。
                    gifPlayer.setPaused(gifFrozen)
                    gifPlayer.start()
                    Log.i(
                        TAG,
                        "gif applied: token=$token playerView=${gifPlayer.hasView()} " +
                            "gif=${gifView.debugState()}"
                    )
                } else {
                    Log.w(TAG, "gif apply fallback: decode returned null")
                    showStaticBitmap(FloatBallDragVisualRenderer.render(context, settings), owns = true)
                }
            }
        }.start()
    }

    private fun showStaticUri(uri: String, opacity: Float) {
        if (uri.isBlank()) {
            releaseOwnedBitmap()
            staticImage.setImageDrawable(null)
            staticImage.alpha = opacity
            showContentMode(ContentMode.STATIC)
            return
        }
        val bitmap = FloatBallImageLoader.loadBitmap(context, uri)
        if (bitmap != null) {
            showStaticBitmap(bitmap, owns = false)
            staticImage.alpha = opacity
        } else {
            // 图片读不出来（文件被删/权限失效）：画兜底球而不是留一块透明窗口。
            // 「不处理」模式下窗口可能很宽，全透明会变成"看不见但点得到"的死区。
            val fallbackSettings = boundSettings
            if (fallbackSettings != null) {
                showStaticBitmap(
                    FloatBallDragVisualRenderer.render(context, fallbackSettings),
                    owns = true
                )
                staticImage.alpha = opacity
            } else {
                releaseOwnedBitmap()
                staticImage.setImageDrawable(null)
                staticImage.alpha = opacity
                showContentMode(ContentMode.STATIC)
            }
        }
    }

    private fun showStaticBitmap(bitmap: Bitmap, owns: Boolean) {
        gifPlayer.release()
        releaseGifSequence()
        builtinAnimView.releaseAnimation()
        releaseOwnedBitmap()
        ownedBitmap = if (owns) bitmap else null
        staticImage.setImageBitmap(bitmap)
        staticImage.alpha = currentOpacity()
        showContentMode(ContentMode.STATIC)
    }

    private fun releaseOwnedBitmap() {
        ownedBitmap?.recycle()
        ownedBitmap = null
    }

    /** 记下当前持有的共享序列；换样式时把旧的还回缓存——这才是真正释放的时机。 */
    private fun retainGifSequence(handle: FloatBallGifSequenceCache.Handle) {
        if (gifSequenceHandle === handle) return
        gifSequenceHandle?.close()
        gifSequenceHandle = handle
    }

    private fun releaseGifSequence() {
        gifSequenceHandle?.close()
        gifSequenceHandle = null
    }

    private fun stopSlideshow() {
        mainHandler.removeCallbacks(slideshowRunnable)
    }

    private fun applyChildGravity(settings: AppSettings, activeSide: FloatBallSide) {
        val gravity = layoutGravity(settings, activeSide)
        listOf(staticImage, gifView, builtinAnimView).forEach { child ->
            val params = child.layoutParams as LayoutParams
            params.gravity = gravity
            child.layoutParams = params
        }
    }

    /**
     * 球体裁剪形状。
     *
     * - 圆形样式（默认）：内切椭圆轮廓，方形窗口下就是正圆。
     * - 「不处理」：彻底不裁，图片四角/边缘按原样显示。
     */
    private fun applyBallClip(circular: Boolean) {
        if (!circular) {
            listOf(staticImage, gifView, builtinAnimView).forEach { child ->
                child.clipToOutline = false
                child.outlineProvider = null
            }
            return
        }
        val clipper = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
            }
        }
        listOf(staticImage, gifView, builtinAnimView).forEach { child ->
            child.clipToOutline = true
            child.outlineProvider = clipper
        }
    }

    private fun layoutGravity(settings: AppSettings, activeSide: FloatBallSide): Int {
        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            return Gravity.CENTER
        }
        return when (activeSide) {
            FloatBallSide.LEFT -> Gravity.CENTER_VERTICAL or Gravity.START
            FloatBallSide.RIGHT -> Gravity.CENTER_VERTICAL or Gravity.END
        }
    }

    /** 诊断用：把解码结果压成一行，便于 logcat 里直接判断走的哪条分支。 */
    private fun describeSequence(sequence: FloatBallGifFrameDecoder.Sequence?): String = when (sequence) {
        null -> "null"
        is FloatBallGifFrameDecoder.Sequence.Cached -> "Cached(frames=${sequence.frameCount})"
        is FloatBallGifFrameDecoder.Sequence.Streaming ->
            "Streaming(${sequence.width}x${sequence.height} ${sequence.durationMs}ms)"
    }

    companion object {
        private const val TAG = "FloatBallIcon"
        private const val SLIDESHOW_INTERVAL_MS = 3_000L
    }
}
