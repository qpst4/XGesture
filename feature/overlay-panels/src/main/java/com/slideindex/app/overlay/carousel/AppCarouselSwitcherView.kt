package com.slideindex.app.overlay.carousel

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.settings.AppCarouselSwitcherSettings
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import kotlin.math.abs
import kotlin.math.roundToInt

data class AppCarouselItem(
    val packageName: String,
    val label: String,
    val iconBitmap: Bitmap,
    val cardColors: AppCarouselCardColors,
    val isCloseAll: Boolean = false,
)

enum class CarouselVerticalAction {
    NONE,
    SPLIT_SCREEN,
    FLOATING,
    CLOSE_APP,
}

/**
 * 独立应用切换器视图（扩展支持分屏、小窗、关闭应用及全部关闭）：
 * 1. 最多显示 8 个最新使用过的 app，末尾固定为 ×（全部关闭）；
 * 2. 卡片行固定显示在屏幕纵向正中央；
 * 3. 手指在全屏任意位置左右滑动即可控制轮播，无需点在卡片上；
 * 4. 手指向上滑动依次激活：分屏（Split screen）、小窗（Floating）、关闭（Close app）；
 * 5. 向上越过关闭项或向下滑动均进入取消状态，松手关闭面板；
 * 6. 自适应 Palette 主色调 Squircle 卡片大图标 + 触感震动反馈。
 */
@SuppressLint("ViewConstructor")
class AppCarouselSwitcherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val onDismissRequest: () -> Unit,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val cardBaseWidth = 76f * density
    private val cardBaseHeight = 84f * density
    private val cardSpacing = 16f * density
    private val cornerRadius = 22f * density
    private var carouselSettings: AppCarouselSwitcherSettings = AppCarouselSwitcherSettings()
    private var appSettings: AppSettings? = null
    private val items = mutableListOf<AppCarouselItem>()
    private var selectedIndex = 0
    private var lastHapticIndex = 0

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var isCancelled = false
    private var isDownCancelled = false
    private var isUpOvershootCancelled = false
    private var selectedVerticalAction = CarouselVerticalAction.NONE
    /** 当前上滑距离（px），用于让上方三个操作按钮随手指上滑渐显。 */
    private var upDistForOptions = 0f
    private var scrollOffset = 0f
    private var targetScrollOffset = 0f
    private var scrollAnimator: ValueAnimator? = null

    private val bgDimPaint = Paint().apply { color = 0x88000000.toInt() }
    private val cardPlatePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f * density, 0f, 2f * density, 0x88000000.toInt())
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAFFFFFF.toInt()
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
    }

    private val optionPlatePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val optionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val optionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.LEFT
    }
    private val optionIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val cardRect = RectF()
    private val iconRect = RectF()
    private val strokeRect = RectF()
    private val optionRect = RectF()
    private val clipPath = Path()

    init {
        isFocusable = true
        isClickable = true
        loadCandidateApps()
    }

    private var isRightTrigger = false

    fun configure(settings: AppSettings, anchorX: Float, anchorY: Float) {
        this.appSettings = settings
        this.carouselSettings = settings.appCarouselSwitcher
        this.initialTouchX = anchorX
        this.initialTouchY = anchorY
        this.currentTouchX = anchorX
        this.currentTouchY = anchorY
        this.isCancelled = false
        this.isDownCancelled = false
        this.isUpOvershootCancelled = false
        this.selectedVerticalAction = CarouselVerticalAction.NONE
        this.upDistForOptions = 0f
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        this.isRightTrigger = anchorX > screenW / 2f
        alpha = 1.0f
        loadCandidateApps()
        // 默认预选上一应用（index 1）或当前应用（index 0）
        selectedIndex = if (items.size > 2) 1 else 0
        lastHapticIndex = selectedIndex
        targetScrollOffset = computeScrollOffsetForIndex(selectedIndex)
        scrollOffset = targetScrollOffset
        invalidate()
    }

    private fun createCloseAllBitmap(sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = sizePx * 0.12f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val pad = sizePx * 0.28f
        canvas.drawLine(pad, pad, sizePx - pad, sizePx - pad, paint)
        canvas.drawLine(sizePx - pad, pad, pad, sizePx - pad, paint)
        return bitmap
    }

    private fun loadCandidateApps() {
        val pm = context.packageManager
        val recentPackages = LinkedHashSet<String>()

        // 1. 优先通过 UsageStatsManager 查询最近使用的应用（最多 8 个）
        if (PermissionHelper.hasUsageAccess(context)) {
            val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            if (usageStatsManager != null) {
                val end = System.currentTimeMillis()
                val start = end - 1000L * 60 * 60 * 24 * 7
                val stats = runCatching {
                    usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
                }.getOrNull()
                if (!stats.isNullOrEmpty()) {
                    stats.asSequence()
                        .filter { it.lastTimeUsed > 0 }
                        .sortedByDescending { it.lastTimeUsed }
                        .map { it.packageName }
                        .distinct()
                        .filter { pm.getLaunchIntentForPackage(it) != null }
                        .take(8)
                        .forEach { recentPackages.add(it) }
                }
            }
        }

        // 2. 尝试从最近任务中补充
        if (recentPackages.size < 8) {
            val taskRefs = com.slideindex.app.overlay.panels.PanelRuntimeHooks.recentTaskIdentifiers?.invoke().orEmpty()
            for (ref in taskRefs) {
                val pkg = ref.substringBefore('/').trim()
                if (pkg.isNotEmpty() && pm.getLaunchIntentForPackage(pkg) != null) {
                    recentPackages.add(pkg)
                    if (recentPackages.size >= 8) break
                }
            }
        }

        // 3. 回退至常用/已安装可启动应用补充至 8 个
        if (recentPackages.size < 8) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pm.getLaunchIntentForPackage(pkg) != null) {
                    recentPackages.add(pkg)
                    if (recentPackages.size >= 8) break
                }
            }
        }

        val loaded = mutableListOf<AppCarouselItem>()
        for (pkg in recentPackages.take(8)) {
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            val label = pm.getApplicationLabel(appInfo).toString()
            val iconDrawable: Drawable = try {
                pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {
                pm.defaultActivityIcon
            }
            val iconSizePx = (48f * density).roundToInt().coerceAtLeast(32)
            val bitmap = com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideBitmap?.invoke(context, pkg, iconSizePx)
                ?: try {
                    iconDrawable.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
                } catch (_: Exception) {
                    createBitmap(iconSizePx, iconSizePx).apply {
                        eraseColor(Color.GRAY)
                    }
                }
            val colors = AppCarouselPaletteResolver.resolveCardColors(pkg, bitmap)
            loaded.add(AppCarouselItem(pkg, label, bitmap, colors, isCloseAll = false))
        }

        // 4. 末尾追加 ×（关闭所有应用）卡片
        val closeAllIconSizePx = (48f * density).roundToInt().coerceAtLeast(32)
        val closeAllBitmap = createCloseAllBitmap(closeAllIconSizePx)
        val closeAllColors = AppCarouselCardColors(
            baseCardColor = 0x33E53935.toInt(),
            highlightCardColor = 0xDDE53935.toInt(),
            strokeColor = 0xFFFF5252.toInt(),
            titleTextColor = Color.WHITE,
        )
        loaded.add(
            AppCarouselItem(
                packageName = "action.close_all",
                label = context.getString(R.string.app_carousel_close_all),
                iconBitmap = closeAllBitmap,
                cardColors = closeAllColors,
                isCloseAll = true,
            )
        )

        items.clear()
        items.addAll(loaded)
    }

    fun onExternalMove(rawX: Float, rawY: Float) {
        currentTouchX = rawX
        currentTouchY = rawY

        // 以手指落点 initialTouchY 为相对基准，向上为正，向下为正
        val upDist = initialTouchY - rawY
        upDistForOptions = upDist.coerceAtLeast(0f)
        val downDist = rawY - initialTouchY

        val downThreshold = (carouselSettings.cancelDistanceDownPx(density) * 0.75f)
            .coerceIn(60f * density, 110f * density)

        // 1. 向下滑动取消判定
        val shouldDownCancel = downDist > downThreshold

        // 2. 向上各档位区间与超出取消判定
        // 基准距离用设置里的「上滑显示距离」，三档按钮按固定间距递推，
        // 之前这里写死 20dp，导致上滑一丢丢就弹菜单、而设置里的距离完全没生效。
        val selectedItem = items.getOrNull(selectedIndex)
        val d1 = carouselSettings.cancelDistanceUpPx(density)
        val d2 = d1 + 45f * density
        val d3 = d1 + 90f * density
        val dCancel = d1 + 145f * density

        val (newAction, shouldUpCancel) = if (!shouldDownCancel && selectedItem != null) {
            if (!selectedItem.isCloseAll) {
                when {
                    upDist >= dCancel -> CarouselVerticalAction.NONE to true
                    upDist >= d3 -> CarouselVerticalAction.CLOSE_APP to false
                    upDist >= d2 -> CarouselVerticalAction.FLOATING to false
                    upDist >= d1 -> CarouselVerticalAction.SPLIT_SCREEN to false
                    else -> CarouselVerticalAction.NONE to false
                }
            } else {
                // 针对 Close All 项，超出一定高度也允许向上甩出取消
                if (upDist >= 110f * density) {
                    CarouselVerticalAction.NONE to true
                } else {
                    CarouselVerticalAction.NONE to false
                }
            }
        } else {
            CarouselVerticalAction.NONE to false
        }

        val shouldCancel = shouldDownCancel || shouldUpCancel
        if (shouldCancel != isCancelled) {
            isCancelled = shouldCancel
            isDownCancelled = shouldDownCancel
            isUpOvershootCancelled = shouldUpCancel
            animate().alpha(if (isCancelled) 0.35f else 1.0f).setDuration(120L).start()
            if (isCancelled) {
                appSettings?.let { HapticHelper.appTick(this, it) }
            }
        } else {
            isDownCancelled = shouldDownCancel
            isUpOvershootCancelled = shouldUpCancel
        }

        if (newAction != selectedVerticalAction) {
            selectedVerticalAction = newAction
            appSettings?.let { HapticHelper.appTick(this, it) }
        }

        // 3. 水平滑动切换应用（方向锁定）：
        // 上滑到「显示距离」的一半才锁横向，避免手指稍微一抬就滑不动。
        val verticalLockDistance = carouselSettings.cancelDistanceUpPx(density) * 0.5f
        val isVerticalGestureActive = upDist >= verticalLockDistance || isCancelled
        if (!isVerticalGestureActive && items.isNotEmpty()) {
            val deltaX = rawX - initialTouchX
            val baseIndex = if (items.size > 2) 1 else 0
            val maxSteps = items.lastIndex - baseIndex
            val deadZone = 0.05f

            // 手指朝"历史应用"方向滑 = forward；反方向滑 = 回退到当前应用
            // 「滑动方向反转」开关只翻转手指方向与进度的对应关系，不影响卡片排列方向。
            val rawForward = if (!isRightTrigger) deltaX <= 0f else deltaX >= 0f
            val forward = rawForward != carouselSettings.slideReversed
            // 行程只由「滑动灵敏度」决定，与触发点离边缘多远无关
            // （之前用 initialTouchX - edgeMargin，边缘触发时会被夹到 60dp，灵敏度完全失效）。
            val availableTravel = carouselSettings.slideTravelPx(density)
            val progress = (abs(deltaX) / availableTravel).coerceIn(0f, 1f)
            val active = ((progress - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)

            // 跟手：整排位置按手指进度线性插值，拖动期间不做动画，松手才吸附。
            scrollAnimator?.cancel()
            if (forward) {
                val endIndex = (baseIndex + maxSteps).coerceAtMost(items.lastIndex)
                val baseOffset = computeScrollOffsetForIndex(baseIndex)
                val endOffset = computeScrollOffsetForIndex(endIndex)
                scrollOffset = baseOffset + (endOffset - baseOffset) * active
                val newIndex = if (maxSteps <= 0) {
                    baseIndex
                } else {
                    (baseIndex + (active * maxSteps).roundToInt()).coerceIn(0, items.lastIndex)
                }
                if (newIndex != selectedIndex) {
                    selectedIndex = newIndex
                    if (selectedIndex != lastHapticIndex) {
                        lastHapticIndex = selectedIndex
                        appSettings?.let { HapticHelper.appTick(this, it) }
                    }
                }
            } else {
                val baseOffset = computeScrollOffsetForIndex(baseIndex)
                val firstOffset = computeScrollOffsetForIndex(0)
                scrollOffset = baseOffset + (firstOffset - baseOffset) * active
                val newIndex = if (progress > 0.08f) 0 else baseIndex
                if (newIndex != selectedIndex) {
                    selectedIndex = newIndex
                    if (selectedIndex != lastHapticIndex) {
                        lastHapticIndex = selectedIndex
                        appSettings?.let { HapticHelper.appTick(this, it) }
                    }
                }
            }
        }
        invalidate()
    }

    fun onExternalUp(rawX: Float, rawY: Float, cancelled: Boolean) {
        if (cancelled || isCancelled) {
            onDismissRequest()
            return
        }
        val target = items.getOrNull(selectedIndex)
        if (target == null) {
            onDismissRequest()
            return
        }

        // 1. 点击末尾 × 执行全部关闭
        if (target.isCloseAll) {
            appSettings?.let { HapticHelper.confirmLaunch(this, it) }
            executeCloseAll()
            onDismissRequest()
            return
        }

        // 2. 执行对应操作
        appSettings?.let { HapticHelper.confirmLaunch(this, it) }
        when (selectedVerticalAction) {
            CarouselVerticalAction.NONE -> {
                launchPackage(target.packageName)
            }
            CarouselVerticalAction.SPLIT_SCREEN -> {
                executeSplitScreen(target.packageName)
            }
            CarouselVerticalAction.FLOATING -> {
                executeFloating(target.packageName)
            }
            CarouselVerticalAction.CLOSE_APP -> {
                executeCloseApp(target.packageName)
            }
        }
        onDismissRequest()
    }

    fun onExternalCancel() {
        onDismissRequest()
    }

    private fun executeCloseAll() {
        val packages = items.filterNot { it.isCloseAll }.map { it.packageName }
        if (packages.isNotEmpty()) {
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.removeRecentPackages?.invoke(packages)
            Thread {
                for (pkg in packages) {
                    com.slideindex.app.overlay.panels.PanelRuntimeHooks.forceStopPackage?.invoke(pkg)
                }
            }.start()
        }
    }

    private fun executeCloseApp(packageName: String) {
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.removeRecentPackages?.invoke(listOf(packageName))
        Thread {
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.forceStopPackage?.invoke(packageName)
        }.start()
    }

    private fun executeFloating(packageName: String) {
        val effective = appSettings ?: AppSettings()
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.launchFreeWindow?.invoke(context, packageName, effective, onDismissRequest)
    }

    private fun executeSplitScreen(packageName: String) {
        val service = com.slideindex.app.overlay.panels.PanelRuntimeHooks.accessibilityService?.invoke()
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            if (ok) {
                postDelayed({
                    launchPackage(packageName)
                }, 350L)
                return
            }
        }
        launchPackage(packageName)
    }

    private fun smoothScrollToIndex(index: Int) {
        targetScrollOffset = computeScrollOffsetForIndex(index)
        scrollAnimator?.cancel()
        scrollAnimator = ValueAnimator.ofFloat(scrollOffset, targetScrollOffset).apply {
            duration = 160L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scrollOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun computeScrollOffsetForIndex(index: Int): Float {
        val centerX = width / 2f
        val itemStep = cardBaseWidth + cardSpacing
        return if (!isRightTrigger) {
            centerX + index * itemStep - cardBaseWidth / 2f
        } else {
            centerX - index * itemStep - cardBaseWidth / 2f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        targetScrollOffset = computeScrollOffsetForIndex(selectedIndex)
        scrollOffset = targetScrollOffset
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgDimPaint)

        if (items.isEmpty()) return

        val centerY = height / 2f
        val itemStep = cardBaseWidth + cardSpacing

        var selectedCenterX = 0f
        var selectedTopY = 0f
        var hasSelectedItem = false
        var selectedIsCloseAll = false

        for (i in items.indices) {
            val item = items[i]
            val itemCenterX = if (!isRightTrigger) {
                scrollOffset - i * itemStep + cardBaseWidth / 2f
            } else {
                scrollOffset + i * itemStep + cardBaseWidth / 2f
            }
            if (itemCenterX < -cardBaseWidth || itemCenterX > width + cardBaseWidth) continue

            val isSelected = i == selectedIndex && !isCancelled
            // 缩放按“距屏幕中心的距离”连续变化（0.62~1.0），对齐悬浮菜单的滑动切换观感：
            // 拖动时整排跟着手指走，靠近中心逐渐放大，而不是选中/未选中二值跳变。
            val distanceRatio = (1f - abs(width / 2f - itemCenterX) / width).coerceIn(0f, 1f)
            val distanceScale = 0.62f + 0.38f * distanceRatio
            val scale = if (isSelected) distanceScale * 1.22f else distanceScale
            val cardW = cardBaseWidth * scale
            val cardH = cardBaseHeight * scale
            val left = itemCenterX - cardW / 2f
            val top = centerY - cardH / 2f
            val right = left + cardW
            val bottom = top + cardH

            if (isSelected) {
                selectedCenterX = itemCenterX
                selectedTopY = top
                hasSelectedItem = true
                selectedIsCloseAll = item.isCloseAll
            }

            cardRect.set(left, top, right, bottom)
            val currentCorner = cornerRadius * scale

            // 卡片底色
            cardPlatePaint.color = if (isSelected) item.cardColors.highlightCardColor else item.cardColors.baseCardColor
            canvas.drawRoundRect(cardRect, currentCorner, currentCorner, cardPlatePaint)

            // 图标居中裁剪
            clipPath.reset()
            clipPath.addRoundRect(cardRect, currentCorner, currentCorner, Path.Direction.CW)
            val clipSaveCount = canvas.save()
            canvas.clipPath(clipPath)
            val iconPad = 13f * density * scale
            val iconSize = (minOf(cardW, cardH) - 2 * iconPad).coerceAtLeast(16f * density)
            val iconLeft = left + (cardW - iconSize) / 2f
            val iconTop = top + (cardH - iconSize) / 2f
            iconRect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            canvas.drawBitmap(item.iconBitmap, null, iconRect, iconPaint)
            canvas.restoreToCount(clipSaveCount)

            // 高亮外描边
            if (isSelected) {
                cardStrokePaint.color = item.cardColors.strokeColor
                cardStrokePaint.strokeWidth = 2.5f * density
                val strokePad = 1.5f * density
                strokeRect.set(left - strokePad, top - strokePad, right + strokePad, bottom + strokePad)
                canvas.drawRoundRect(strokeRect, currentCorner + strokePad, currentCorner + strokePad, cardStrokePaint)
            } else {
                cardStrokePaint.color = 0x22FFFFFF.toInt()
                cardStrokePaint.strokeWidth = 1f * density
                canvas.drawRoundRect(cardRect, currentCorner, currentCorner, cardStrokePaint)
            }

            // 绘制选中的应用名称
            if (isSelected) {
                val labelY = bottom + 26f * density
                canvas.drawText(item.label, itemCenterX, labelY, textPaint)
            }
        }

        // 绘制上方操作选项（Close, Floating, Split screen）
        // 未上滑时完全不显示；随手指上滑 0~「显示距离」渐显并上浮，避免三个按钮常驻抢视线。
        val optionsShowDistance = carouselSettings.cancelDistanceUpPx(density)
        val optionsAppear = (upDistForOptions / optionsShowDistance).coerceIn(0f, 1f)
        if (hasSelectedItem && !isDownCancelled && optionsAppear > 0.01f) {
            val layer = canvas.saveLayerAlpha(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                (optionsAppear * 255f).roundToInt(),
            )
            canvas.translate(0f, (1f - optionsAppear) * 12f * density)
            if (!selectedIsCloseAll) {
                val spacing1 = 16f * density
                val spacingNext = 8f * density
                val pillHNormal = 32f * density
                val pillHSelected = 36f * density

                // 最下：Split screen
                val bottom1 = selectedTopY - spacing1
                drawOptionPill(
                    canvas = canvas,
                    centerX = selectedCenterX,
                    bottomY = bottom1,
                    label = context.getString(R.string.app_carousel_action_split_screen),
                    action = CarouselVerticalAction.SPLIT_SCREEN,
                    isSelected = selectedVerticalAction == CarouselVerticalAction.SPLIT_SCREEN,
                    isDimmed = isUpOvershootCancelled,
                )

                // 中间：Floating
                val h1 = if (selectedVerticalAction == CarouselVerticalAction.SPLIT_SCREEN) pillHSelected else pillHNormal
                val bottom2 = bottom1 - h1 - spacingNext
                drawOptionPill(
                    canvas = canvas,
                    centerX = selectedCenterX,
                    bottomY = bottom2,
                    label = context.getString(R.string.app_carousel_action_floating),
                    action = CarouselVerticalAction.FLOATING,
                    isSelected = selectedVerticalAction == CarouselVerticalAction.FLOATING,
                    isDimmed = isUpOvershootCancelled,
                )

                // 最上：Close
                val h2 = if (selectedVerticalAction == CarouselVerticalAction.FLOATING) pillHSelected else pillHNormal
                val bottom3 = bottom2 - h2 - spacingNext
                drawOptionPill(
                    canvas = canvas,
                    centerX = selectedCenterX,
                    bottomY = bottom3,
                    label = context.getString(R.string.app_carousel_action_close),
                    action = CarouselVerticalAction.CLOSE_APP,
                    isSelected = selectedVerticalAction == CarouselVerticalAction.CLOSE_APP,
                    isDimmed = isUpOvershootCancelled,
                )

                // 如果向上滑出超过 Close 项进入取消：在最上方显示“松手取消”
                if (isUpOvershootCancelled) {
                    val cancelY = bottom3 - pillHNormal - 16f * density
                    canvas.drawText(context.getString(R.string.app_carousel_cancel_overshoot_hint), width / 2f, cancelY, hintPaint)
                }
            } else {
                // 选中 Close All 时的上方提示
                val hintY = selectedTopY - 24f * density
                val hintText = if (isUpOvershootCancelled) {
                    context.getString(R.string.app_carousel_cancel_overshoot_hint)
                } else {
                    context.getString(R.string.app_carousel_close_all_hint)
                }
                canvas.drawText(hintText, width / 2f, hintY, hintPaint)
            }
            canvas.restoreToCount(layer)
        }

        // 下滑取消提示
        if (isDownCancelled) {
            val hintY = centerY + cardBaseHeight * 0.9f
            canvas.drawText(context.getString(R.string.app_carousel_cancel_down_hint), width / 2f, hintY, hintPaint)
        }
    }

    private fun drawOptionPill(
        canvas: Canvas,
        centerX: Float,
        bottomY: Float,
        label: String,
        action: CarouselVerticalAction,
        isSelected: Boolean,
        isDimmed: Boolean = false,
    ) {
        val pillW = (if (isSelected) 140f else 130f) * density
        val pillH = (if (isSelected) 36f else 32f) * density
        val pillCorner = pillH / 2f
        val halfW = pillW / 2f

        val left = (centerX - halfW).coerceIn(12f * density, width - 12f * density - pillW)
        val right = left + pillW
        val top = bottomY - pillH
        val bottom = bottomY
        optionRect.set(left, top, right, bottom)

        // 背景
        optionPlatePaint.color = if (isDimmed) {
            0x44212121.toInt()
        } else if (isSelected) {
            when (action) {
                CarouselVerticalAction.SPLIT_SCREEN -> 0xEE00897B.toInt()
                CarouselVerticalAction.FLOATING -> 0xEE3949AB.toInt()
                CarouselVerticalAction.CLOSE_APP -> 0xEEE53935.toInt()
                else -> 0xCC333333.toInt()
            }
        } else {
            0x99212121.toInt()
        }
        canvas.drawRoundRect(optionRect, pillCorner, pillCorner, optionPlatePaint)

        // 描边
        if (isDimmed) {
            optionStrokePaint.color = 0x18FFFFFF.toInt()
            optionStrokePaint.strokeWidth = 1f * density
        } else if (isSelected) {
            optionStrokePaint.color = Color.WHITE
            optionStrokePaint.strokeWidth = 2f * density
        } else {
            optionStrokePaint.color = 0x33FFFFFF.toInt()
            optionStrokePaint.strokeWidth = 1f * density
        }
        canvas.drawRoundRect(optionRect, pillCorner, pillCorner, optionStrokePaint)

        // 图标
        val iconSize = 14f * density
        val iconLeft = left + 14f * density
        val iconCenterY = (top + bottom) / 2f
        val iconTop = iconCenterY - iconSize / 2f
        val iconBottom = iconCenterY + iconSize / 2f
        val iconRight = iconLeft + iconSize

        optionIconPaint.color = when {
            isDimmed -> 0x55FFFFFF.toInt()
            isSelected -> Color.WHITE
            else -> 0xCCFFFFFF.toInt()
        }
        when (action) {
            CarouselVerticalAction.SPLIT_SCREEN -> {
                val gap = 2f * density
                val halfH = (iconSize - gap) / 2f
                canvas.drawRect(iconLeft, iconTop, iconRight, iconTop + halfH, optionIconPaint)
                canvas.drawRect(iconLeft, iconTop + halfH + gap, iconRight, iconBottom, optionIconPaint)
            }
            CarouselVerticalAction.FLOATING -> {
                canvas.drawRect(iconLeft, iconTop, iconRight - 3f * density, iconBottom - 3f * density, optionIconPaint)
                val subLeft = iconLeft + 4f * density
                val subTop = iconTop + 4f * density
                canvas.drawRect(subLeft, subTop, iconRight, iconBottom, optionIconPaint)
            }
            CarouselVerticalAction.CLOSE_APP -> {
                val pad = 1.5f * density
                canvas.drawLine(iconLeft + pad, iconTop + pad, iconRight - pad, iconBottom - pad, optionIconPaint)
                canvas.drawLine(iconRight - pad, iconTop + pad, iconLeft + pad, iconBottom - pad, optionIconPaint)
            }
            else -> Unit
        }

        // 文字
        optionTextPaint.color = when {
            isDimmed -> 0x55FFFFFF.toInt()
            isSelected -> Color.WHITE
            else -> 0xCCFFFFFF.toInt()
        }
        optionTextPaint.isFakeBoldText = isSelected && !isDimmed
        val textX = iconRight + 10f * density
        val textY = iconCenterY - (optionTextPaint.descent() + optionTextPaint.ascent()) / 2f
        canvas.drawText(label, textX, textY, optionTextPaint)
    }

    private fun launchPackage(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (launchIntent != null) {
            runCatching { context.startActivity(launchIntent) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                onExternalMove(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                onExternalMove(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onExternalUp(event.rawX, event.rawY, false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onExternalCancel()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
