package com.slideindex.app.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Outline
import android.os.Bundle
import android.util.AttributeSet
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import kotlin.math.roundToInt

/**
 * Hosts [AppWidgetHostView] mapped proportionally from panel columns/rows to standard Android
 * desktop dimensions (AOSP Standard Launcher 320dp full width, 70dp/row), ensuring 100% universal
 * fidelity for all widget providers on all devices, scaled seamlessly to card slot.
 */
class ScalableFrameLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

  var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private set

  private var spanX: Int = 1
  private var spanY: Int = 1
  private var totalColumns: Int = 4
  private var slotWidthPx: Int = 0
  private var slotHeightPx: Int = 0
  internal var renderWidthPx: Int = 0
  internal var renderHeightPx: Int = 0
  /** 上报给 provider 的渲染尺寸（dp），= max(桌面同 span 尺寸, 面板实际槽位)。 */
  private var renderWidthDp: Int = 0
  private var renderHeightDp: Int = 0
  internal var scaleVal: Float = 1f

  private var resizing: Boolean = false
  private var widgetTouchEnabled: Boolean = true
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private var interceptDownX = 0f
  private var interceptDownY = 0f
  private var scrollingInsideWidget = false
  private val cornerRadiusPx = 16f * context.resources.displayMetrics.density

  private var lastAppliedWidthPx = 0
  private var lastAppliedHeightPx = 0
  /** 已真正下发给 provider 的尺寸（dp），用于去重；未下发前保持 -1。 */
  private var lastPushedDpWidth = -1
  private var lastPushedDpHeight = -1
  private var lastSlotWidthPx = 0
  private var lastSlotHeightPx = 0
  private var pendingOptionsPush: Runnable? = null

  companion object {
    /**
     * 桌面单元格基准（dp）。
     *
     * 曾经的 80dp 在 4 列 / 400~420dp 宽的手机上偏小（桌面单格约 95~100dp），
     * 一个 4×2 的 widget 会少报约 40dp，正好可能把 provider 挤进「紧凑版」布局——
     * 表现为面板里点日程/便签只打开 app、不进具体页。
     * 这里取 90dp（贴近主流桌面），并且渲染尺寸不会再小于该 span 的桌面档。
     */
    private const val STANDARD_CELL_WIDTH_DP = 90
    private const val STANDARD_CELL_HEIGHT_DP = 90
    private const val CELL_GAP_DP = 8
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  }

  init {
    clipChildren = true
    clipToPadding = true
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (w <= 0 || h <= 0) return
    outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, w, h, cornerRadiusPx)
      }
    }
    clipToOutline = true
  }

  fun setTotalColumns(columns: Int) {
    val cols = columns.coerceAtLeast(1)
    if (totalColumns == cols) return
    totalColumns = cols
    if (slotWidthPx > 0 && slotHeightPx > 0) {
      recalculateRenderAndScale(slotWidthPx, slotHeightPx)
      requestLayout()
      invalidate()
    }
  }

  fun bindWidget(hostView: AppWidgetHostView, appWidgetId: Int, spanX: Int, spanY: Int) {
    val sx = spanX.coerceAtLeast(1)
    val sy = spanY.coerceAtLeast(1)
    if (childCount == 1 && getChildAt(0) === hostView && this.appWidgetId == appWidgetId) {
      this.spanX = sx
      this.spanY = sy
      requestLayout()
      invalidate()
      return
    }
    (hostView.parent as? ViewGroup)?.removeView(hostView)
    this.appWidgetId = appWidgetId
    this.spanX = sx
    this.spanY = sy
    slotWidthPx = 0
    slotHeightPx = 0
    renderWidthPx = 0
    renderHeightPx = 0
    scaleVal = 1f
    lastAppliedWidthPx = 0
    lastAppliedHeightPx = 0
    removeAllViews()

    hostView.clipToOutline = false
    hostView.clipChildren = false
    hostView.clipToPadding = false
    hostView.scaleX = 1f
    hostView.scaleY = 1f
    if (hostView is RoundedAppWidgetHostView) {
      hostView.setWidgetClippingEnabled(true)
    }

    addView(hostView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    requestLayout()
  }

  fun setWidgetTouchEnabled(enabled: Boolean) {
    widgetTouchEnabled = enabled
    if (!enabled) scrollingInsideWidget = false
  }

  fun setResizing(active: Boolean) {
    if (resizing == active) return
    resizing = active
  }

  fun applySpan(spanX: Int, spanY: Int) {
    val sx = spanX.coerceAtLeast(1)
    val sy = spanY.coerceAtLeast(1)
    if (this.spanX == sx && this.spanY == sy) return
    this.spanX = sx
    this.spanY = sy
    resetHostSizeCache()
    if (slotWidthPx > 0 && slotHeightPx > 0) {
      recalculateRenderAndScale(slotWidthPx, slotHeightPx)
      if (!resizing) {
        flushHostSizeToProvider(force = true)
      }
      requestLayout()
      invalidate()
    }
  }

  fun updateSpan(spanX: Int, spanY: Int) {
    applySpan(spanX, spanY)
  }

  fun commitHostLayout(force: Boolean = false) {
    flushHostSizeToProvider(force)
  }

  fun resetHostSizeCache() {
    lastAppliedWidthPx = 0
    lastAppliedHeightPx = 0
    lastPushedDpWidth = -1
    lastPushedDpHeight = -1
  }

  private fun flushHostSizeToProvider(force: Boolean = false) {
    if (slotWidthPx <= 0 || slotHeightPx <= 0) return
    applyWidgetSizeToHost(force)
  }

  private fun syncSlotAndRecalculate(widthPx: Int, heightPx: Int): Boolean {
    if (widthPx <= 0 || heightPx <= 0) return false
    val changed = slotWidthPx != widthPx || slotHeightPx != heightPx
    slotWidthPx = widthPx
    slotHeightPx = heightPx
    recalculateRenderAndScale(widthPx, heightPx)
    return changed
  }

  /**
   * 该 span 在桌面上的标准尺寸（dp）。
   */
  private fun spanToStandardSizeDp(spanX: Int, spanY: Int): Pair<Int, Int> {
    val wDp = spanX * STANDARD_CELL_WIDTH_DP + (spanX - 1) * CELL_GAP_DP
    val hDp = spanY * STANDARD_CELL_HEIGHT_DP + (spanY - 1) * CELL_GAP_DP
    return wDp to hDp
  }

  /**
   * 重新计算虚拟渲染像素与等比缩放因子。
   *
   * 渲染尺寸 = max(桌面同 span 标准尺寸, 面板实际槽位)：
   * - 槽位比桌面窄时，仍按桌面档渲染再缩小，避免 provider 因为“尺寸太小”给紧凑版；
   * - 槽位更宽时按槽位走，别把内容放大到超过可用空间。
   */
  private fun recalculateRenderAndScale(slotW: Int, slotH: Int) {
    if (slotW <= 0 || slotH <= 0) {
      renderWidthPx = 0
      renderHeightPx = 0
      renderWidthDp = 0
      renderHeightDp = 0
      scaleVal = 1f
      return
    }
    val density = resources.displayMetrics.density
    val (standardWDp, standardHDp) = spanToStandardSizeDp(spanX, spanY)
    val slotWDp = (slotW / density).roundToInt()
    val slotHDp = (slotH / density).roundToInt()
    renderWidthDp = maxOf(standardWDp, slotWDp)
    renderHeightDp = maxOf(standardHDp, slotHDp)

    renderWidthPx = (renderWidthDp * density).roundToInt()
    renderHeightPx = (renderHeightDp * density).roundToInt()

    if (renderWidthPx > 0 && renderHeightPx > 0) {
      val scaleX = slotW.toFloat() / renderWidthPx.toFloat()
      val scaleY = slotH.toFloat() / renderHeightPx.toFloat()
      // 取较小值进行等比缩放，彻底杜绝按钮/文字压扁拉伸
      scaleVal = kotlin.math.min(scaleX, scaleY)
    } else {
      scaleVal = 1f
    }
  }

  private fun slotSizeDp(): Pair<Int, Int> {
    // 上报的就是实际渲染尺寸，保证 provider 看到的 layout 尺寸与画面一致。
    if (renderWidthDp > 0 && renderHeightDp > 0) return renderWidthDp to renderHeightDp
    return spanToStandardSizeDp(spanX, spanY)
  }

  private fun applyWidgetSizeToHost(force: Boolean = false) {
    val child = if (isNotEmpty()) getChildAt(0) as? AppWidgetHostView else null
    if (child == null || slotWidthPx <= 0 || slotHeightPx <= 0) return
    val (widthDp, heightDp) = slotSizeDp()
    if (!force && widthDp == lastAppliedWidthPx && heightDp == lastAppliedHeightPx) return
    lastAppliedWidthPx = widthDp
    lastAppliedHeightPx = heightDp
    // 布局期间只会被安排一次，真正下发在下一帧；避免一次布局里反复
    // updateAppWidgetOptions（实测 3 秒能给 provider 打出上百条广播）。
    scheduleOptionsPush(child, widthDp, heightDp)
  }

  private fun scheduleOptionsPush(child: AppWidgetHostView, widthDp: Int, heightDp: Int) {
    pendingOptionsPush?.let { handler.removeCallbacks(it) }
    pendingOptionsPush = Runnable {
      pendingOptionsPush = null
      if (child.parent == null) {
        // 这帧里卡片已经被摘掉：把“已安排”的标记清掉，等重新挂载后再推。
        lastAppliedWidthPx = 0
        lastAppliedHeightPx = 0
        return@Runnable
      }
      if (widthDp == lastPushedDpWidth && heightDp == lastPushedDpHeight) return@Runnable
      lastPushedDpWidth = widthDp
      lastPushedDpHeight = heightDp
      val sizeF = SizeF(widthDp.toFloat(), heightDp.toFloat())
      val options = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        putParcelableArrayList(
          AppWidgetManager.OPTION_APPWIDGET_SIZES,
          arrayListOf(sizeF),
        )
      }
      // updateAppWidgetSize 内部会调用 updateAppWidgetOptions，只此一次，别再重复推。
      child.updateAppWidgetSize(options, listOf(sizeF))
      child.requestLayout()
    }.also { handler.post(it) }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val specW = MeasureSpec.getSize(widthMeasureSpec)
    val specH = MeasureSpec.getSize(heightMeasureSpec)
    setMeasuredDimension(specW, specH)

    if (specW <= 0 || specH <= 0 || isEmpty()) return

    syncSlotAndRecalculate(specW, specH)

    // 对内部子视图（AppWidgetHostView）使用虚拟标准大尺寸测量
    val child = getChildAt(0)
    if (renderWidthPx > 0 && renderHeightPx > 0) {
      child.measure(
        MeasureSpec.makeMeasureSpec(renderWidthPx, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(renderHeightPx, MeasureSpec.EXACTLY),
      )
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    val child = getChildAt(0)
    if (child == null) {
      super.onLayout(changed, left, top, right, bottom)
      return
    }

    val slotW = right - left
    val slotH = bottom - top
    syncSlotAndRecalculate(slotW, slotH)

    if (renderWidthPx <= 0 || renderHeightPx <= 0) {
      super.onLayout(changed, left, top, right, bottom)
      return
    }

    // 计算居中偏移
    val leftOffset = (slotW - renderWidthPx) / 2
    val topOffset = (slotH - renderHeightPx) / 2

    child.layout(
      leftOffset,
      topOffset,
      leftOffset + renderWidthPx,
      topOffset + renderHeightPx,
    )

    // 以子视图中心为锚点进行等比硬件加速缩放
    child.pivotX = renderWidthPx / 2.0f
    child.pivotY = renderHeightPx / 2.0f
    child.scaleX = scaleVal
    child.scaleY = scaleVal

    if (!resizing && (lastSlotWidthPx != slotW || lastSlotHeightPx != slotH)) {
      lastSlotWidthPx = slotW
      lastSlotHeightPx = slotH
      flushHostSizeToProvider()
    }
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    if (!widgetTouchEnabled) return false
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        interceptDownX = ev.x
        interceptDownY = ev.y
        scrollingInsideWidget = false
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = ev.x - interceptDownX
        val dy = ev.y - interceptDownY
        if (!scrollingInsideWidget) {
          val hostRoot = if (isEmpty()) null else getChildAt(0)
          if (hostRoot != null) {
            val leftOffset = (width - renderWidthPx) / 2f
            val topOffset = (height - renderHeightPx) / 2f
            val scale = if (scaleVal > 0f) scaleVal else 1f
            val hostLocalX = (interceptDownX - leftOffset) / scale
            val hostLocalY = (interceptDownY - topOffset) / scale
            // 与一墨 np5 同款：按主轴判定，竖向为主才交给内层竖向滚动，
            // 横向为主才交给内层横向滚动；斜向不再误判，其余交给面板。
            val absDx = kotlin.math.abs(dx)
            val absDy = kotlin.math.abs(dy)
            val innerClaims = when {
              absDy > touchSlop && absDy > absDx ->
                WidgetTouchScrollUtils.canScrollAtPoint(hostRoot, hostLocalX, hostLocalY, 0, dy)
              absDx > touchSlop && absDx > absDy ->
                WidgetTouchScrollUtils.canScrollAtPoint(hostRoot, hostLocalX, hostLocalY, 1, dx)
              else -> false
            }
            if (innerClaims) {
              scrollingInsideWidget = true
              WidgetTouchScrollUtils.requestDisallowInterceptAllParents(this, true)
            }
          }
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        scrollingInsideWidget = false
      }
    }
    return super.onInterceptTouchEvent(ev)
  }
}
