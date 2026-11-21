package com.slideindex.app.link

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

/**
 * 链接雷达 HUD 视觉视图（1:1 对齐 GestureEVO LinkRadarView + ViewLinkRadarTargetBinding）：
 * - 在屏幕链接物理位置绘制圆角半透明线框（全量探测链接 alpha=140，当前命中高亮链接 alpha=255 加粗 2dp）；
 * - 支持两种胶囊模式：
 *   1. 指针/十字光标跟随单目标横幅（[show] / [clearTarget]）：在命中链接上方或下方居中弹出「[目标App图标] 在 XXX 中打开」；
 *   2. 屏幕内容/全局复制多目标常驻横幅（[showActionPrompts] / [updateActionPrompt]）：利用 [placeRadarActions] 四向防碰撞摆放所有链接直达胶囊。
 */
class LinkRadarView(context: Context) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val origin = IntArray(2)

    /** 指针/取词十字光标悬停时的单目标胶囊横幅 */
    private val targetBanner = TargetBannerView(context)

    /** 已探测到的所有屏幕链接 */
    private var detectedLinks = emptyList<LinkRegion>()

    /** 当前指针/十字光标命中的高亮链接 */
    private var highlightedRegions = emptyList<LinkRegion>()

    /** 屏幕内容雷达（全局复制）按 groupId 分组的常驻胶囊 */
    private var actionGroups = emptyMap<Int, List<LinkRegion>>()
    private val actionViews = mutableMapOf<Int, TargetBannerView>()
    private var actionBounds = emptyMap<Int, RadarBox>()
    private var singleActionLabel = "打开链接"
    private var multipleActionLabel = "选择链接"
    private var actionLayoutKey: List<Int>? = null

    var onLinkClickListener: ((LinkRegion) -> Unit)? = null
    var onLinksGroupClickListener: ((List<LinkRegion>) -> Unit)? = null

    val visibleActionKeys: Set<Int>
        get() = actionBounds.keys

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(
            targetBanner,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
        targetBanner.visibility = GONE
    }

    /**
     * 设置已探测到的所有链接（绘制基础线框）。
     */
    fun setLinks(links: List<LinkRegion>) {
        if (detectedLinks == links) return
        detectedLinks = links
        invalidate()
    }

    /**
     * 屏幕内容雷达（全局复制）：一次性挂载所有链接并启动四向防碰撞常驻胶囊。
     */
    fun showScreenContentLinks(links: List<LinkRegion>) {
        detectedLinks = links
        showActionPrompts(
            groups = links.groupBy { it.groupId },
            singleLabel = "打开链接",
            multipleLabel = "选择链接"
        )
        invalidate()
    }

    fun showActionPrompts(
        groups: Map<Int, List<LinkRegion>>,
        singleLabel: String = "打开链接",
        multipleLabel: String = "选择链接",
    ) {
        clearActionPrompts()
        actionGroups = groups
        singleActionLabel = singleLabel
        multipleActionLabel = multipleLabel
        requestLayout()
        invalidate()
    }

    fun updateActionPrompt(key: Int, label: String, icon: Drawable?, loading: Boolean = false) {
        val banner = actionViews[key] ?: return
        banner.bind(label, icon, loading)
        requestLayout()
        invalidate()
    }

    fun actionLinksAt(screenX: Float, screenY: Float): List<LinkRegion> {
        if (visibility != VISIBLE) return emptyList()
        getLocationOnScreen(origin)
        val x = screenX - origin[0]
        val y = screenY - origin[1]
        val key = actionBounds.entries.firstOrNull { (_, box) ->
            x >= box.left && x < box.right && y >= box.top && y < box.bottom
        }?.key ?: return emptyList()
        return actionGroups[key].orEmpty()
    }

    fun highlightLinks(links: List<LinkRegion>) {
        highlightedRegions = links
        invalidate()
    }

    /**
     * 指针/取词十字光标模式：高亮命中链接并在其上方/下方显示目标应用胶囊横幅。
     */
    fun show(regions: List<LinkRegion>, label: String, icon: Drawable? = null, loading: Boolean = false) {
        highlightedRegions = regions
        if (regions.isEmpty()) {
            targetBanner.visibility = GONE
        } else {
            targetBanner.visibility = VISIBLE
            targetBanner.bind(label, icon, loading)
            targetBanner.refreshTheme()
        }
        requestLayout()
        invalidate()
    }

    fun clearTarget() {
        show(emptyList(), "")
    }

    fun clear() {
        detectedLinks = emptyList()
        clearActionPrompts()
        clearTarget()
        invalidate()
    }

    fun refreshTheme() {
        targetBanner.refreshTheme()
        actionViews.values.forEach { it.refreshTheme() }
        invalidate()
    }

    private fun clearActionPrompts() {
        actionViews.values.forEach { removeView(it) }
        actionViews.clear()
        actionGroups = emptyMap()
        actionBounds = emptyMap()
        actionLayoutKey = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxLabelWidth = (MeasureSpec.getSize(widthMeasureSpec) - 88 * density).toInt().coerceAtLeast(1)
        targetBanner.setMaxLabelWidth(maxLabelWidth)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutActionPrompts()
        if (highlightedRegions.isEmpty() || targetBanner.visibility != VISIBLE) return
        getLocationOnScreen(origin)
        val anchor = Rect(highlightedRegions.first().frameBounds()).apply {
            highlightedRegions.drop(1).forEach { union(it.frameBounds()) }
        }
        val margin = (12 * density).toInt()
        val bubbleWidth = targetBanner.measuredWidth
        val bubbleHeight = targetBanner.measuredHeight
        val x = (anchor.centerX() - origin[0] - bubbleWidth / 2)
            .coerceIn(margin, (width - margin - bubbleWidth).coerceAtLeast(margin))
        val above = anchor.top - origin[1] - bubbleHeight - margin
        val preferredY = if (above >= margin) above else anchor.bottom - origin[1] + margin
        val y = preferredY.coerceIn(margin, (height - bubbleHeight - margin).coerceAtLeast(margin))
        targetBanner.layout(x, y, x + bubbleWidth, y + bubbleHeight)
    }

    private fun layoutActionPrompts() {
        if (actionGroups.isEmpty() || width == 0 || height == 0) return
        getLocationOnScreen(origin)
        val fontScale = resources.configuration.fontScale
        val actionWidth = minOf(232 * density, width - 24 * density).toInt().coerceAtLeast(1)
        val actionHeight = maxOf(46 * density, 24 * density + 20 * density * fontScale).toInt()
        val key = listOf(width, height, origin[0], origin[1], actionWidth, actionHeight, actionGroups.keys.hashCode())
        if (actionLayoutKey != key) {
            actionLayoutKey = key
            fun box(link: LinkRegion): RadarBox = link.frameBounds().toRadarBox(origin[0], origin[1])
            val anchors = actionGroups
                .mapValues { (_, links) -> enclosingRadarBox(links.map(::box)) }
                .mapNotNull { (id, bounds) -> bounds?.let { id to it } }
                .toMap()
            actionBounds = placeRadarActions(
                anchors = anchors,
                obstacles = detectedLinks.groupBy { it.groupId }.mapValues { (_, links) -> links.map(::box) },
                viewport = RadarBox(12 * density, 12 * density, width - 12 * density, height - 12 * density),
                actionWidth = actionWidth.toFloat(),
                actionHeight = actionHeight.toFloat(),
                gap = 8 * density,
            )
            actionViews.keys.filter { it !in actionBounds }.forEach { removed ->
                actionViews.remove(removed)?.let { removeView(it) }
            }
            for ((id, _) in actionBounds) {
                if (id in actionViews) continue
                val groupLinks = actionGroups.getValue(id)
                val initialText = if (groupLinks.size == 1) singleActionLabel else "$multipleActionLabel (${groupLinks.size})"
                val banner = TargetBannerView(context).apply {
                    bind(initialText, null)
                    refreshTheme()
                    setOnClickListener {
                        val groupListener = onLinksGroupClickListener
                        if (groupListener != null) {
                            groupListener.invoke(groupLinks)
                        } else {
                            onLinkClickListener?.invoke(groupLinks.first())
                        }
                    }
                }
                actionViews[id] = banner
                addView(banner, LayoutParams(actionWidth, actionHeight))
            }
        }
        for ((id, bounds) in actionBounds) {
            val child = actionViews[id] ?: continue
            child.setMaxLabelWidth((actionWidth - 68 * density).toInt().coerceAtLeast(1))
            child.measure(
                MeasureSpec.makeMeasureSpec(actionWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(actionHeight, MeasureSpec.EXACTLY),
            )
            val l = bounds.left.toInt()
            val t = bounds.top.toInt()
            child.layout(l, t, l + actionWidth, t + actionHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detectedLinks.isEmpty() && highlightedRegions.isEmpty()) return
        getLocationOnScreen(origin)
        canvas.save()
        canvas.translate(-origin[0].toFloat(), -origin[1].toFloat())

        val primaryColor = resolvePrimaryColor()
        borderPaint.color = primaryColor
        borderPaint.alpha = 155
        borderPaint.strokeWidth = 1.35f * density
        detectedLinks.map(LinkRegion::frameBounds).distinct().forEach { rect ->
            canvas.drawRoundRect(RectF(rect), 5f * density, 5f * density, borderPaint)
        }

        borderPaint.alpha = 255
        borderPaint.strokeWidth = 2.2f * density
        highlightedRegions.map(LinkRegion::frameBounds).distinct().forEach { rect ->
            canvas.drawRoundRect(RectF(rect), 5f * density, 5f * density, borderPaint)
        }
        canvas.restore()
    }

    /**
     * 检查屏幕坐标是否命中任意链接胶囊或链接线框区域。
     */
    fun findLinkAtScreen(screenX: Float, screenY: Float): LinkRegion? {
        val actionHits = actionLinksAt(screenX, screenY)
        if (actionHits.isNotEmpty()) return actionHits.first()
        val sx = screenX.toInt()
        val sy = screenY.toInt()
        return detectedLinks.firstOrNull { link ->
            link.rects.any { it.contains(sx, sy) }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (actionGroups.isEmpty() && onLinkClickListener == null) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = findLinkAtScreen(event.rawX, event.rawY)
                if (hit != null) {
                    highlightLinks(detectedLinks.filter { it.groupId == hit.groupId })
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val hit = findLinkAtScreen(event.rawX, event.rawY)
                highlightLinks(emptyList())
                if (hit != null) {
                    onLinkClickListener?.invoke(hit)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                highlightLinks(emptyList())
            }
        }
        return false
    }

    private fun isNightMode(): Boolean {
        val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask == Configuration.UI_MODE_NIGHT_YES
    }

    private fun resolvePrimaryColor(): Int {
        val night = isNightMode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return runCatching {
                context.getColor(
                    if (night) android.R.color.system_accent1_300
                    else android.R.color.system_accent1_600
                )
            }.getOrDefault(if (night) Color.parseColor("#8AB4F8") else Color.parseColor("#1A73E8"))
        }
        return if (night) Color.parseColor("#8AB4F8") else Color.parseColor("#1A73E8")
    }

    private fun resolveSurfaceContainerColor(): Int {
        val night = isNightMode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return runCatching {
                context.getColor(
                    if (night) android.R.color.system_neutral1_800
                    else android.R.color.system_neutral1_50
                )
            }.getOrDefault(if (night) Color.parseColor("#2B2D30") else Color.parseColor("#F5F7FA"))
        }
        return if (night) Color.parseColor("#2B2D30") else Color.parseColor("#F5F7FA")
    }

    private fun resolveOnPrimaryContainerColor(): Int {
        val night = isNightMode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return runCatching {
                context.getColor(
                    if (night) android.R.color.system_accent1_100
                    else android.R.color.system_accent1_900
                )
            }.getOrDefault(if (night) Color.parseColor("#E8F0FE") else Color.parseColor("#174EA6"))
        }
        return if (night) Color.parseColor("#E8F0FE") else Color.parseColor("#174EA6")
    }

    /**
     * 1:1 复刻 Miuix `InfiniteProgressIndicator` 视觉的轻量原生 View：
     * 外层为空心圆环轨道（Track），内层有一颗实心小圆球（Orbiting Dot）贴着圆环内壁匀速滚动旋转。
     */
    inner class MiuixOrbitProgressView(context: Context) : View(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private var currentAngleDeg = 0f
        private var animator: ValueAnimator? = null

        fun setIndicatorColor(color: Int) {
            ringPaint.color = color
            dotPaint.color = color
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (visibility == VISIBLE) {
                startOrbit()
            }
        }

        override fun onDetachedFromWindow() {
            stopOrbit()
            super.onDetachedFromWindow()
        }

        override fun setVisibility(visibility: Int) {
            super.setVisibility(visibility)
            if (visibility == VISIBLE && isAttachedToWindow) {
                startOrbit()
            } else {
                stopOrbit()
            }
        }

        fun startOrbit() {
            if (animator?.isRunning == true) return
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 950L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    currentAngleDeg = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun stopOrbit() {
            animator?.cancel()
            animator = null
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = minOf(width, height).toFloat()
            if (size <= 0f) return
            val cx = width / 2f
            val cy = height / 2f
            // 留出一点内边距让 22dp 的圆环居中在 30dp 的槽位里
            val indicatorRadius = (size * 0.37f).coerceAtLeast(4f * density)
            val ringStroke = (indicatorRadius * 0.22f).coerceAtLeast(1.8f * density)
            val dotRadius = (indicatorRadius * 0.24f).coerceAtLeast(2.0f * density)

            ringPaint.strokeWidth = ringStroke
            // 外层空心圆环
            canvas.drawCircle(cx, cy, indicatorRadius - ringStroke / 2f, ringPaint)

            // 内侧贴着圆环内壁滚动的小圆球（对齐 Miuix InfiniteProgressIndicator 几何比例）
            val orbitRadius = (indicatorRadius - ringStroke - dotRadius * 0.85f).coerceAtLeast(dotRadius)
            val rad = Math.toRadians(currentAngleDeg.toDouble())
            val dotX = cx + (orbitRadius * cos(rad)).toFloat()
            val dotY = cy + (orbitRadius * sin(rad)).toFloat()
            canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
        }
    }

    /**
     * 1:1 还原 GestureEVO `view_link_radar_target.xml`：
     * 24dp 大圆角胶囊 + 2dp 主题色描边 + 左侧 30dp 圆角应用图标（解析短链时显示 Miuix 空心圆环滚动小球，无图标时显示 🔗） + 右侧单行标签。
     */
    inner class TargetBannerView(context: Context) : LinearLayout(context) {
        private val iconContainer = FrameLayout(context)
        private val iconView = ImageView(context)
        private val loadingView = MiuixOrbitProgressView(context)
        private val fallbackLinkText = TextView(context)
        private val labelView = TextView(context)
        private var currentIcon: Drawable? = null
        private var isLoadingState: Boolean = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (12 * density).toInt()
            val padV = (8 * density).toInt()
            setPadding(padH, padV, padH, padV)
            elevation = 8f * density

            val iconSize = (30 * density).toInt()
            val iconRadius = 8f * density
            iconContainer.layoutParams = LayoutParams(iconSize, iconSize)
            iconContainer.clipToOutline = true
            iconContainer.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, iconRadius)
                }
            }

            iconView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            iconView.scaleType = ImageView.ScaleType.FIT_CENTER

            loadingView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            loadingView.visibility = GONE

            fallbackLinkText.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            fallbackLinkText.gravity = Gravity.CENTER
            fallbackLinkText.text = "🔗"
            fallbackLinkText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)

            iconContainer.addView(fallbackLinkText)
            iconContainer.addView(loadingView)
            iconContainer.addView(iconView)
            addView(iconContainer)

            labelView.layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            labelView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            labelView.maxLines = 1
            labelView.ellipsize = TextUtils.TruncateAt.END
            addView(labelView)

            refreshTheme()
        }

        fun setMaxLabelWidth(maxPx: Int) {
            labelView.maxWidth = maxPx
        }

        fun bind(label: String, icon: Drawable?, loading: Boolean = false) {
            labelView.text = label
            currentIcon = icon
            isLoadingState = loading
            when {
                loading -> {
                    iconView.setImageDrawable(null)
                    iconView.visibility = GONE
                    fallbackLinkText.visibility = GONE
                    loadingView.visibility = VISIBLE
                    iconContainer.background = null
                }
                icon != null -> {
                    loadingView.visibility = GONE
                    iconView.setImageDrawable(icon)
                    iconView.visibility = VISIBLE
                    fallbackLinkText.visibility = GONE
                    iconContainer.background = null
                }
                else -> {
                    loadingView.visibility = GONE
                    iconView.setImageDrawable(null)
                    iconView.visibility = GONE
                    fallbackLinkText.visibility = VISIBLE
                    styleFallbackIconBg()
                }
            }
        }

        fun refreshTheme() {
            val bgColor = resolveSurfaceContainerColor()
            val strokeColor = resolvePrimaryColor()
            val textColor = resolveOnPrimaryContainerColor()
            val night = isNightMode()
            val indicatorGray = if (night) Color.parseColor("#A8AEB8") else Color.parseColor("#8C939E")
            loadingView.setIndicatorColor(indicatorGray)
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(bgColor)
                setStroke((2f * density).toInt().coerceAtLeast(1), strokeColor)
            }
            labelView.setTextColor(textColor)
            if (currentIcon == null && !isLoadingState) {
                styleFallbackIconBg()
            }
        }

        private fun styleFallbackIconBg() {
            val strokeColor = resolvePrimaryColor()
            iconContainer.background = GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(Color.argb(36, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)))
            }
        }
    }
}
