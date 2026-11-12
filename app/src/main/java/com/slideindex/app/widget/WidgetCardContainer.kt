package com.slideindex.app.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isNotEmpty
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.graphics.Path
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Card shell wrapping [ScalableFrameLayout] with delete / resize chrome.
 */
@SuppressLint("ViewConstructor") // Programmatically created widget card
class WidgetCardContainer(
  context: Context,
  initialItem: WidgetPanelItem,
  hostView: AppWidgetHostView?,
) : FrameLayout(context) {

  var item: WidgetPanelItem = initialItem
    private set
  var onDelete: (() -> Unit)? = null
  var onResize: ((spanX: Int, spanY: Int) -> Unit)? = null
  var onConfigure: (() -> Unit)? = null
  var onInteractionActiveChange: ((Boolean) -> Unit)? = null

  private val scalableFrame: ScalableFrameLayout
  private val loadingPlaceholder: WidgetLoadingPlaceholder
  private val deleteButton: View
  private val resizeHandle: ResizeHandleView
  private val configureButton: View
  private var editModeEnabled = false
  private var previewSpanX = item.spanX
  private var previewSpanY = item.spanY
  private var previewWidthPx = 0
  private var previewHeightPx = 0
  private var previewingResize = false
  private var resizeSnapAnimator: ValueAnimator? = null
  private var hostEverBound = false
  private var pendingHostLayoutCommit: Runnable? = null
  private var lastDebouncedCommitSpanX = -1
  private var lastDebouncedCommitSpanY = -1
  /** 应用/快捷方式/动作格子里的「图标 + 名称」两层视图；小组件卡为 null。 */
  private var appContentView: android.widget.LinearLayout? = null
  private var appIconView: androidx.appcompat.widget.AppCompatImageView? = null
  private var appLabelView: android.widget.TextView? = null
  /** 是否显示名称（由 [WidgetCanvasLayout.appLabelsVisible] 下发）。 */
  private var showAppLabels: Boolean = true

  private val density = resources.displayMetrics.density
  private val cardCornerRadiusPx = 16f * density
  private val editOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x66FFFFFF
    style = Paint.Style.STROKE
    strokeWidth = 1.2f * density
  }

  init {
    clipChildren = false
    clipToPadding = false
    clipToOutline = false

    loadingPlaceholder = WidgetLoadingPlaceholder(context)
    scalableFrame = ScalableFrameLayout(context)

    if (item.itemType == ITEM_TYPE_APP || item.itemType == ITEM_TYPE_SHORTCUT || item.itemType == ITEM_TYPE_ACTION) {
      loadingPlaceholder.visibility = GONE
      scalableFrame.visibility = GONE
      val appView = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val iconView = object : androidx.appcompat.widget.AppCompatImageView(context) {
          override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (item.cloneUserId > 0) {
              com.slideindex.app.util.CloneBadge.draw(
                canvas,
                width / 2f,
                height / 2f,
                minOf(width, height).toFloat(),
                circular = false,
              )
            }
          }
        }.apply {
          val icon = if (item.itemType == ITEM_TYPE_ACTION) {
            val action = com.slideindex.app.launcher.QuickLauncherItemCodec.parseActionPayload(item.intentUri)
            val iconSizePx = (44 * density).roundToInt()
            if (action != null) {
              com.slideindex.app.util.GestureActionIconBitmap.get(
                action = action,
                sizePx = iconSizePx,
                tintArgb = Color.WHITE,
                outlined = true,
                withPlate = true,
              ).toDrawable(resources)
            } else null
          } else if (item.itemType == ITEM_TYPE_SHORTCUT && item.shortcutId.isNotEmpty()) {
            runCatching {
              val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
              val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
                setPackage(item.packageName)
                setShortcutIds(listOf(item.shortcutId))
                setQueryFlags(
                  android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                  android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                  android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
              }
              val info = launcherApps?.getShortcuts(query, android.os.Process.myUserHandle())?.firstOrNull()
              if (info != null) {
                launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
              } else null
            }.getOrNull()
          } else null
          setImageDrawable(icon)
          // 应用图标不在构造期同步取：PackageManager 在部分 OEM（Flyme）上会走主题引擎逐像素重绘，
          // 主线程同步调用会让面板首帧卡住数百毫秒。这里统一走缓存 + 后台线程。
          if (icon == null && item.packageName.isNotEmpty()) {
            val iconPackage = item.packageName
            tag = iconPackage
            WidgetAppIconCache.load(context, iconPackage) { bitmap ->
              if (tag == iconPackage) {
                setImageDrawable(bitmap.toDrawable(resources))
              }
            }
          }
        }
        val iconSize = (44 * density).roundToInt()
        addView(iconView, LayoutParams(iconSize, iconSize))

        val labelView = android.widget.TextView(context).apply {
          text = item.label
          setTextColor(Color.WHITE)
          setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
          gravity = Gravity.CENTER
          maxLines = 1
          ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val labelParams = LayoutParams(
          LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT,
        ).apply {
          topMargin = (4 * density).roundToInt()
        }
        addView(labelView, labelParams)
        appContentView = this
        appIconView = iconView
        appLabelView = labelView
      }
      addView(appView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
        gravity = Gravity.CENTER
      })
      appView.setOnClickListener {
        if (!editModeEnabled) {
          if (item.itemType == ITEM_TYPE_ACTION) {
            val action = com.slideindex.app.launcher.QuickLauncherItemCodec.parseActionPayload(item.intentUri)
            if (action != null) {
              com.slideindex.app.overlay.WidgetPopupOverlayWindow.dismiss()
              com.slideindex.app.service.SlideIndexAccessibilityService.perform(action)
            }
          } else if (item.itemType == ITEM_TYPE_SHORTCUT) {
            com.slideindex.app.overlay.WidgetPopupOverlayWindow.dismiss()
            val launched = runCatching {
              val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
              launcherApps?.startShortcut(item.packageName, item.shortcutId, null, null, android.os.Process.myUserHandle())
              true
            }.getOrDefault(false)
            if (!launched && item.intentUri.isNotEmpty()) {
              runCatching {
                val intent = android.content.Intent.parseUri(item.intentUri, 0).apply {
                  addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
              }
            }
          } else if (item.packageName.isNotEmpty()) {
            com.slideindex.app.overlay.WidgetPopupOverlayWindow.dismiss()
            if (item.cloneUserId > 0) {
              // 应用分身：那是另一个 user 下的实例，必须跨 user 启动
              // （有特权走 shell，没有特权走透明跳板），不能按主实例的 launch intent 起。
              runCatching {
                com.slideindex.app.util.CloneAppLauncher.launch(
                  context,
                  item.packageName,
                  item.cloneUserId,
                )
              }
            } else {
              runCatching {
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage(item.packageName)
                  ?: android.content.Intent().apply {
                    component = android.content.ComponentName(item.packageName, item.className)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                  }
                context.startActivity(launchIntent)
              }
            }
          }
        }
      }
    } else if (hostView != null) {
      scalableFrame.bindWidget(hostView, item.appWidgetId, item.spanX, item.spanY)
      loadingPlaceholder.visibility = GONE
      hostEverBound = true
    } else {
      // 单进程后宿主视图与编辑器同进程：调用方没传视图时自己取一次，
      // 否则编辑器永远停在占位卡（以前只有 :overlay 进程能创建宿主视图）。
      val selfHostView = runCatching {
        WidgetPopupHost.startListening(context)
        WidgetPopupHost.obtainHostView(context, item.appWidgetId)
      }.getOrNull()
      if (selfHostView != null && item.itemType == ITEM_TYPE_WIDGET) {
        scalableFrame.visibility = VISIBLE
        scalableFrame.bindWidget(selfHostView, item.appWidgetId, item.spanX, item.spanY)
        loadingPlaceholder.visibility = GONE
        hostEverBound = true
        updateScalableTargetFromContainer()
        scalableFrame.commitHostLayout()
      } else {
        loadingPlaceholder.visibility = VISIBLE
      }
    }

    addView(loadingPlaceholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    addView(scalableFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

    if (hostView != null && item.itemType == ITEM_TYPE_WIDGET) {
      attachHostWhenReady(hostView)
    }

    val chromeBtnSize = (20 * density).roundToInt()
    val chromeMargin = (2 * density).roundToInt()
    deleteButton = View(context).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFFE53935.toInt())
      }
      val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1.6f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
      }
      setWillNotDraw(false)
      tag = crossPaint
      setOnClickListener { onDelete?.invoke() }
    }
    addView(
      deleteButton,
      LayoutParams(chromeBtnSize, chromeBtnSize).apply {
        gravity = Gravity.TOP or Gravity.END
        topMargin = chromeMargin
        marginEnd = chromeMargin
      },
    )

    resizeHandle = ResizeHandleView(context).apply {
      @SuppressLint("ClickableViewAccessibility") // Resize drag handle; not a click target
      setOnTouchListener(ResizeTouchListener())
    }
    val handleSize = chromeBtnSize
    addView(
      resizeHandle,
      LayoutParams(handleSize, handleSize).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        bottomMargin = chromeMargin
        marginEnd = chromeMargin
      },
    )

    configureButton = View(context).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xCC333333.toInt())
      }
      setWillNotDraw(false)
      setOnClickListener { onConfigure?.invoke() }
    }
    addView(
      configureButton,
      LayoutParams(chromeBtnSize, chromeBtnSize).apply {
        gravity = Gravity.TOP or Gravity.START
        topMargin = chromeMargin
        marginStart = chromeMargin
      },
    )

    setEditMode(false)
  }

  /** 全局「显示图标名称」开关变化时由画布下发；只改自己的排版，不动 span/网格。 */
  fun setAppLabelsVisible(visible: Boolean) {
    if (showAppLabels == visible) return
    showAppLabels = visible
    requestLayout()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    // 必须在测量子视图之前算好：格子是正方形，图标 + 4dp 间距 + 一行 11sp 名称
    // 需要 63~64dp；格子更小时名称会被下一行图标裁掉（用户反馈的「名字被挡住」）。
    // 这里按实测行高缩小图标；缩到下限仍放不下就只画图标。
    applyAppCellSizing(
      cardWidthPx = MeasureSpec.getSize(widthMeasureSpec),
      cardHeightPx = MeasureSpec.getSize(heightMeasureSpec),
    )
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
  }

  private fun applyAppCellSizing(cardWidthPx: Int, cardHeightPx: Int) {
    val icon = appIconView ?: return
    val label = appLabelView ?: return
    val content = appContentView ?: return
    if (cardWidthPx <= 0 || cardHeightPx <= 0) return

    val spanX = item.spanX.coerceAtLeast(1)
    val cellWidthPx = (cardWidthPx / spanX).coerceAtLeast(1)
    val gapPx = (4 * density).roundToInt()
    val maxIconPx = minOf(
      (44 * density).roundToInt(),
      (cellWidthPx - (2 * density).roundToInt()).coerceAtLeast(1),
    )
    val minIconPx = (20 * density).roundToInt().coerceAtMost(maxIconPx)

    label.measure(
      MeasureSpec.makeMeasureSpec(cellWidthPx, MeasureSpec.AT_MOST),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
    )
    val labelHeightPx = label.measuredHeight
    var showLabel = showAppLabels && label.text.isNotEmpty() && labelHeightPx > 0
    var iconSizePx = maxIconPx
    if (showLabel) {
      val fitPx = cardHeightPx - gapPx - labelHeightPx
      if (fitPx >= minIconPx) {
        iconSizePx = minOf(fitPx, maxIconPx)
      } else {
        // 显示开关开着但格子确实放不下：宁可只画图标，也不让文字被裁。
        showLabel = false
      }
    }
    if (!showLabel) {
      iconSizePx = minOf(
        maxIconPx,
        (cardHeightPx - (4 * density).roundToInt()).coerceAtLeast(1),
      ).coerceAtLeast(minIconPx)
    }

    val params = icon.layoutParams
    if (params.width != iconSizePx || params.height != iconSizePx) {
      params.width = iconSizePx
      params.height = iconSizePx
      icon.layoutParams = params
    }
    val labelVisibility = if (showLabel) View.VISIBLE else View.GONE
    if (label.visibility != labelVisibility) label.visibility = labelVisibility
    // 隐藏文字后，名称改由容器朗读（否则无障碍只剩一个无名图标）。
    val description = if (showLabel) null else item.label.ifBlank { null }
    if (content.contentDescription != description) content.contentDescription = description
  }

  fun syncItem(newItem: WidgetPanelItem) {
    if (item == newItem) return
    item = newItem
    previewSpanX = newItem.spanX
    previewSpanY = newItem.spanY
    requestLayout()
  }

  fun attachHostView(hostView: AppWidgetHostView) {
    loadingPlaceholder.visibility = GONE
    scalableFrame.visibility = VISIBLE
    scalableFrame.bindWidget(hostView, item.appWidgetId, item.spanX, item.spanY)
    hostEverBound = true
    updateHostLongClickHandling(editModeEnabled)
    updateScalableTargetFromContainer()
    scalableFrame.commitHostLayout()
  }

  private fun ensureHostViewAttached() {
    if (scalableFrame.isNotEmpty()) return
    if (!hostEverBound) return
    val hostView = WidgetPopupHost.obtainHostView(context, item.appWidgetId) ?: return
    loadingPlaceholder.visibility = GONE
    scalableFrame.visibility = VISIBLE
    scalableFrame.bindWidget(hostView, item.appWidgetId, layoutSpanX(), layoutSpanY())
    updateHostLongClickHandling(editModeEnabled)
    updateScalableTargetFromContainer()
    scalableFrame.commitHostLayout(force = true)
  }

  private fun attachHostWhenReady(hostView: AppWidgetHostView) {
    loadingPlaceholder.visibility = GONE
    scalableFrame.visibility = VISIBLE
    hostEverBound = true
    updateScalableTargetFromContainer()
    scalableFrame.commitHostLayout()
  }

  fun setEditMode(enabled: Boolean) {
    editModeEnabled = enabled
    if (!enabled) clearResizePreview()
    deleteButton.visibility = if (enabled) VISIBLE else GONE
    if (item.itemType == ITEM_TYPE_APP || item.itemType == ITEM_TYPE_SHORTCUT) {
      resizeHandle.visibility = GONE
      configureButton.visibility = GONE
    } else {
      resizeHandle.visibility = if (enabled) VISIBLE else GONE
      scalableFrame.setWidgetTouchEnabled(true)
      updateHostLongClickHandling(enabled)

      val info = WidgetPopupHost.providerInfo(context, item.appWidgetId)
      val hasConfigure = info?.configure != null
      configureButton.visibility = if (enabled && hasConfigure) VISIBLE else GONE
    }
    invalidate()
  }

  private fun updateHostLongClickHandling(editMode: Boolean) {
    val hostView = if (scalableFrame.isNotEmpty()) scalableFrame.getChildAt(0) else null
    if (hostView == null) return
    // 浏览态不抢小组件自己的长按（有的 widget 长按会出菜单/配置），
    // 编辑态的拖动由画布处理，宿主这里也不再额外吞事件。
    hostView.isLongClickable = false
    hostView.setOnLongClickListener(null)
  }

  private fun drawEditOutline(canvas: Canvas) {
    val half = editOutlinePaint.strokeWidth / 2f
    val rect = RectF(half, half, width - half, height - half)
    canvas.drawRoundRect(rect, cardCornerRadiusPx, cardCornerRadiusPx, editOutlinePaint)
  }

  fun canContentScrollAtPoint(cardLocalX: Float, cardLocalY: Float, axis: Int, delta: Float): Boolean {
    val hostRoot = hostRoot() ?: return false
    val (hostX, hostY) = toHostLocalCoords(cardLocalX, cardLocalY) ?: return false
    return WidgetTouchScrollUtils.canScrollAtPoint(hostRoot, hostX, hostY, axis, delta)
  }

  fun shouldReceiveTouchAtPoint(cardLocalX: Float, cardLocalY: Float): Boolean {
    if (isTouchOnChrome(cardLocalX, cardLocalY)) return false
    val hostRoot = hostRoot() ?: return false
    val (hostX, hostY) = toHostLocalCoords(cardLocalX, cardLocalY) ?: return false
    return WidgetTouchScrollUtils.hasScrollableOrInteractiveContentAtPoint(hostRoot, hostX, hostY)
  }

  private fun hostRoot(): View? = if (scalableFrame.isNotEmpty()) scalableFrame.getChildAt(0) else null

  private fun toHostLocalCoords(cardLocalX: Float, cardLocalY: Float): Pair<Float, Float>? {
    val hostRoot = hostRoot() ?: return null
    val frameX = cardLocalX - scalableFrame.left + scalableFrame.scrollX
    val frameY = cardLocalY - scalableFrame.top + scalableFrame.scrollY
    val leftOffset = (scalableFrame.width - scalableFrame.renderWidthPx) / 2f
    val topOffset = (scalableFrame.height - scalableFrame.renderHeightPx) / 2f
    val scale = if (scalableFrame.scaleVal > 0f) scalableFrame.scaleVal else 1f
    // 旧算式（按原点缩放）保留一份，仅用于探针对比，确认后随探针一起删除。
    val oldHostX = (frameX - leftOffset - hostRoot.translationX) / scale
    val oldHostY = (frameY - topOffset - hostRoot.translationY) / scale
    // 子视图是「以自身中心为轴心」缩放的（见 ScalableFrameLayout.onLayout 的 pivot = renderWidthPx/2），
    // 逆变换必须绕中心算，不能按原点缩放再平移，否则 scale < 1 时会整体偏移。
    // 视觉中心 = leftOffset + renderWidthPx/2 = scalableFrame.width/2。
    val hostX = scalableFrame.renderWidthPx / 2f +
      (frameX - scalableFrame.width / 2f - hostRoot.translationX) / scale
    val hostY = scalableFrame.renderHeightPx / 2f +
      (frameY - scalableFrame.height / 2f - hostRoot.translationY) / scale
    WidgetPanelProbe.coordDelta(cardLocalX, cardLocalY, oldHostX, oldHostY, hostX, hostY, scale)
    return hostX to hostY
  }

  fun isTouchOnChrome(localX: Float, localY: Float): Boolean {
    if (deleteButton.isVisible && isPointInView(deleteButton, localX, localY)) return true
    if (resizeHandle.isVisible && isPointInView(resizeHandle, localX, localY)) return true
    if (configureButton.isVisible && isPointInView(configureButton, localX, localY)) return true
    return false
  }

  private fun isPointInView(view: View, localX: Float, localY: Float): Boolean {
    val pad = 6f * density
    return localX >= view.left - pad &&
      localX <= view.right + pad &&
      localY >= view.top - pad &&
      localY <= view.bottom + pad
  }

  private fun requestDisallowInterceptAllParents(disallow: Boolean) {
    var current: android.view.ViewParent? = parent
    while (current != null) {
      current.requestDisallowInterceptTouchEvent(disallow)
      current = current.parent
    }
  }

  fun isPreviewingResize(): Boolean = previewingResize

  fun previewLayoutWidthPx(gridStepPx: Int): Int {
    if (previewingResize && previewWidthPx > 0) return previewWidthPx
    return layoutSpanX() * gridStepPx.coerceAtLeast(1)
  }

  fun previewLayoutHeightPx(gridStepPx: Int): Int {
    if (previewingResize && previewHeightPx > 0) return previewHeightPx
    return layoutSpanY() * gridStepPx.coerceAtLeast(1)
  }

  fun updateScalableTargetFromContainer() {
    val totalCols = (parent as? WidgetCanvasLayout)?.pageColumnCount ?: 4
    scalableFrame.setTotalColumns(totalCols)
    scalableFrame.applySpan(layoutSpanX(), layoutSpanY())
  }

  fun applyResizePreview(spanX: Int, spanY: Int, widthPx: Int, heightPx: Int) {
    if (
      previewSpanX == spanX &&
      previewSpanY == spanY &&
      previewWidthPx == widthPx &&
      previewHeightPx == heightPx
    ) {
      return
    }
    previewSpanX = spanX
    previewSpanY = spanY
    previewWidthPx = widthPx
    previewHeightPx = heightPx
    if (!previewingResize) {
      previewingResize = true
      scalableFrame.setResizing(true)
    }
    (parent as? View)?.requestLayout()
    (parent as? WidgetCanvasLayout)?.notifyResizePreviewChanged()
    if (spanX != lastDebouncedCommitSpanX || spanY != lastDebouncedCommitSpanY) {
      lastDebouncedCommitSpanX = spanX
      lastDebouncedCommitSpanY = spanY
      scheduleDebouncedHostLayoutCommit()
    }
  }

  private fun scheduleDebouncedHostLayoutCommit() {
    pendingHostLayoutCommit?.let { removeCallbacks(it) }
    pendingHostLayoutCommit = Runnable {
      pendingHostLayoutCommit = null
      if (width <= 0 || height <= 0) return@Runnable
      updateScalableTargetFromContainer()
      scalableFrame.commitHostLayout(force = true)
    }.also { postDelayed(it, 80) }
  }

  private fun cancelPendingHostLayoutCommit() {
    pendingHostLayoutCommit?.let { removeCallbacks(it) }
    pendingHostLayoutCommit = null
    lastDebouncedCommitSpanX = -1
    lastDebouncedCommitSpanY = -1
  }

  fun previewResize(spanX: Int, spanY: Int, gridStepPx: Int) {
    val step = gridStepPx.coerceAtLeast(1)
    applyResizePreview(spanX, spanY, spanX * step, spanY * step)
  }

  fun clearResizePreview() {
    resizeSnapAnimator?.cancel()
    resizeSnapAnimator = null
    cancelPendingHostLayoutCommit()
    if (!previewingResize) return
    previewingResize = false
    previewSpanX = item.spanX
    previewSpanY = item.spanY
    previewWidthPx = 0
    previewHeightPx = 0
    scalableFrame.setResizing(false)
    scalableFrame.commitHostLayout()
    updateScalableTargetFromContainer()
    (parent as? View)?.requestLayout()
    (parent as? WidgetCanvasLayout)?.notifyResizePreviewChanged()
  }

  fun layoutSpanX(): Int = if (previewingResize) previewSpanX else item.spanX

  fun layoutSpanY(): Int = if (previewingResize) previewSpanY else item.spanY

  fun refreshWidgetLayout(force: Boolean = false) {
    ensureHostViewAttached()
    updateScalableTargetFromContainer()
    scalableFrame.commitHostLayout(force = force)
    requestLayout()
  }

  private fun runAfterNextLayout(block: () -> Unit) {
    if (isAttachedToWindow && width > 0 && height > 0 && !isLayoutRequested) {
      block()
      return
    }
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
      override fun onGlobalLayout() {
        viewTreeObserver.removeOnGlobalLayoutListener(this)
        block()
      }
    })
  }

  private fun finishResize(newSpanX: Int, newSpanY: Int, sizeChanged: Boolean, onComplete: (() -> Unit)? = null) {
    resizeSnapAnimator?.cancel()
    val canvas = parent as? WidgetCanvasLayout
    val step = canvas?.gridStepPx?.coerceAtLeast(1) ?: 1
    val targetW = newSpanX * step
    val targetH = newSpanY * step
    val startW = previewLayoutWidthPx(step)
    val startH = previewLayoutHeightPx(step)
    val needsSnapAnim = startW != targetW || startH != targetH

    if (needsSnapAnim) {
      previewSpanX = newSpanX
      previewSpanY = newSpanY
      resizeSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 120L
        interpolator = DecelerateInterpolator()
        addUpdateListener { anim ->
          val t = anim.animatedValue as Float
          previewWidthPx = (startW + (targetW - startW) * t).roundToInt()
          previewHeightPx = (startH + (targetH - startH) * t).roundToInt()
          (parent as? View)?.requestLayout()
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: android.animation.Animator) {
            resizeSnapAnimator = null
            completeFinishResize(newSpanX, newSpanY, sizeChanged, onComplete)
          }

          override fun onAnimationCancel(animation: android.animation.Animator) {
            resizeSnapAnimator = null
            completeFinishResize(newSpanX, newSpanY, sizeChanged, onComplete)
          }
        })
        start()
      }
      return
    }
    completeFinishResize(newSpanX, newSpanY, sizeChanged, onComplete)
  }

  private fun completeFinishResize(
    newSpanX: Int,
    newSpanY: Int,
    sizeChanged: Boolean,
    onComplete: (() -> Unit)? = null,
  ) {
    cancelPendingHostLayoutCommit()
    if (sizeChanged) {
      syncItem(item.copy(spanX = newSpanX, spanY = newSpanY))
    } else {
      previewSpanX = item.spanX
      previewSpanY = item.spanY
    }
    previewWidthPx = 0
    previewHeightPx = 0
    previewingResize = false
    scalableFrame.setResizing(false)
    scalableFrame.resetHostSizeCache()
    (parent as? View)?.requestLayout()
    (parent as? WidgetCanvasLayout)?.notifyResizePreviewChanged()
    runAfterNextLayout {
      ensureHostViewAttached()
      updateScalableTargetFromContainer()
      scalableFrame.commitHostLayout(force = true)
      if (sizeChanged) {
        onResize?.invoke(newSpanX, newSpanY)
      }
      onComplete?.invoke()
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (width <= 0 || height <= 0) return
    ensureHostViewAttached()
    updateScalableTargetFromContainer()
    if (!previewingResize) {
      scalableFrame.commitHostLayout()
    }
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    if (editModeEnabled) {
      drawEditOutline(canvas)
    }
    if (editModeEnabled && deleteButton.isVisible) {
      val paint = deleteButton.tag as? Paint ?: return
      val cx = deleteButton.left + deleteButton.width / 2f
      val cy = deleteButton.top + deleteButton.height / 2f
      val r = deleteButton.width * 0.16f
      canvas.drawLine(cx - r, cy - r, cx + r, cy + r, paint)
      canvas.drawLine(cx + r, cy - r, cx - r, cy + r, paint)
    }
    if (editModeEnabled && configureButton.isVisible) {
      drawGearIcon(
        canvas,
        configureButton.left + configureButton.width / 2f,
        configureButton.top + configureButton.height / 2f,
        configureButton.width * 0.28f,
      )
    }
  }

  private fun drawGearIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      style = Paint.Style.STROKE
      strokeWidth = 1.2f * density
      strokeCap = Paint.Cap.ROUND
    }
    val toothCount = 8
    val outerR = radius
    val innerR = radius * 0.72f
    val holeR = radius * 0.32f
    val path = Path()
    for (i in 0 until toothCount) {
      val angle = Math.PI * 2 * i / toothCount
      val next = Math.PI * 2 * (i + 0.5) / toothCount
      val x1 = cx + cos(angle).toFloat() * outerR
      val y1 = cy + sin(angle).toFloat() * outerR
      val x2 = cx + cos(next).toFloat() * innerR
      val y2 = cy + sin(next).toFloat() * innerR
      if (i == 0) path.moveTo(x1, y1) else path.lineTo(x1, y1)
      path.lineTo(x2, y2)
    }
    path.close()
    canvas.drawPath(path, paint)
    canvas.drawCircle(cx, cy, holeR, paint)
  }

  private inner class ResizeTouchListener : OnTouchListener {
    private var startSpanX = item.spanX
    private var startSpanY = item.spanY
    private var startWidthPx = 0
    private var startHeightPx = 0
    private var startRawX = 0f
    private var startRawY = 0f
    private var gridStepPx = 0
    private var previewSpanX = item.spanX
    private var previewSpanY = item.spanY

    @SuppressLint("ClickableViewAccessibility") // Resize drag gesture; not a click
    override fun onTouch(v: View, event: MotionEvent): Boolean {
      if (!editModeEnabled) return false
      val parent = parent as? WidgetCanvasLayout ?: return false
      gridStepPx = parent.gridStepPx
      val step = gridStepPx.coerceAtLeast(1)
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          resizeSnapAnimator?.cancel()
          resizeSnapAnimator = null
          startSpanX = item.spanX
          startSpanY = item.spanY
          previewSpanX = startSpanX
          previewSpanY = startSpanY
          startWidthPx = startSpanX * step
          startHeightPx = startSpanY * step
          startRawX = event.rawX
          startRawY = event.rawY
          scalableFrame.resetHostSizeCache()
          applyResizePreview(startSpanX, startSpanY, startWidthPx, startHeightPx)
          onInteractionActiveChange?.invoke(true)
          requestDisallowInterceptAllParents(true)
          return true
        }
        MotionEvent.ACTION_MOVE -> {
          val rawDy = event.rawY - startRawY
          val rawH = (startHeightPx + rawDy).roundToInt().coerceAtLeast(step)
          val candidateSpanYRaw = ((rawH + step - 1) / step).coerceAtLeast(1)
          parent.ensureBufferRowsBelow(item.y + candidateSpanYRaw)
          val maxSpanX = parent.pageColumnCount - item.x
          val maxSpanY = (parent.pageRowCount - item.y).coerceAtLeast(1)
          val maxW = maxSpanX * step
          val maxH = maxSpanY * step
          val rawDx = event.rawX - startRawX
          val newW = (startWidthPx + rawDx).roundToInt().coerceIn(step, maxW)
          val newH = (startHeightPx + rawDy).roundToInt().coerceIn(step, maxH)
          val candidateSpanX = ((newW + step - 1) / step).coerceIn(1, maxSpanX)
          val candidateSpanY = ((newH + step - 1) / step).coerceIn(1, maxSpanY)
          val page = parent.currentPage ?: return true
          if (WidgetPanelGridLogic.isAreaFree(
              page,
              item.x,
              item.y,
              candidateSpanX,
              candidateSpanY,
              item.appWidgetId,
            )
          ) {
            previewSpanX = candidateSpanX
            previewSpanY = candidateSpanY
            applyResizePreview(candidateSpanX, candidateSpanY, newW, newH)
          }
          return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          requestDisallowInterceptAllParents(false)
          val newSpanX = previewSpanX
          val newSpanY = previewSpanY
          val sizeChanged = newSpanX != item.spanX || newSpanY != item.spanY
          finishResize(newSpanX, newSpanY, sizeChanged)
          onInteractionActiveChange?.invoke(false)
          return true
        }
      }
      return false
    }
  }

  private class ResizeHandleView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val armPx = 8f * density
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xCC1A1A1A.toInt()
      strokeWidth = 2.8f * density
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      strokeWidth = 1.8f * density
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }

    init {
      setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      val right = width - 1.5f * density
      val bottom = height - 1.5f * density
      val top = bottom - armPx
      val left = right - armPx
      canvas.drawLine(right, top, right, bottom, outlinePaint)
      canvas.drawLine(left, bottom, right, bottom, outlinePaint)
      canvas.drawLine(right, top, right, bottom, paint)
      canvas.drawLine(left, bottom, right, bottom, paint)
    }
  }
}
