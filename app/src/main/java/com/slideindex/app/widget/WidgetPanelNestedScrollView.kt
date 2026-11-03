package com.slideindex.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/**
 * 小组件面板每一页的纵向滚动容器。
 *
 * 与原生 [NestedScrollView] 的唯一差别：当手指按在某个小组件卡片上、且卡片里的
 * 内容还能按这个方向滚动时，本容器**整段手势都不拦截**，纵向拖动完整留给小组件
 * （对齐一墨 `np5` 的语义：内层可滚 → 面板不参与）。
 *
 * 内层到边界/不可滚时照常 `super.onInterceptTouchEvent`，面板接手；画布那边也会用
 * 嵌套滚动补一次，保证边界处不会卡住。
 */
class WidgetPanelNestedScrollView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : NestedScrollView(context, attrs) {

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private var downY = 0f
  private var targetCard: WidgetCardContainer? = null
  private var yieldedToWidget = false

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downY = ev.y
        targetCard = findCardAt(ev.x, ev.y)
        yieldedToWidget = false
        return super.onInterceptTouchEvent(ev)
      }
      MotionEvent.ACTION_MOVE -> {
        if (yieldedToWidget) return false
        val card = targetCard
        if (card != null && abs(ev.y - downY) > touchSlop) {
          val canvas = getChildAt(0)
          val canvasX = ev.x + scrollX - (canvas?.left ?: 0)
          val canvasY = ev.y + scrollY - (canvas?.top ?: 0)
          val cardX = canvasX - card.left - card.translationX
          val cardY = canvasY - card.top - card.translationY
          if (card.canContentScrollAtPoint(cardX, cardY, 0, ev.y - downY)) {
            // 内层还能滚：本手势彻底让位，避免 NestedScrollView 抢跑。
            yieldedToWidget = true
            return false
          }
        }
        return super.onInterceptTouchEvent(ev)
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
        targetCard = null
        yieldedToWidget = false
      }
    }
    return super.onInterceptTouchEvent(ev)
  }

  private fun findCardAt(x: Float, y: Float): WidgetCardContainer? {
    val canvas = getChildAt(0) as? WidgetCanvasLayout ?: return null
    val canvasX = x + scrollX - canvas.left
    val canvasY = y + scrollY - canvas.top
    for (i in 0 until canvas.childCount) {
      val child = canvas.getChildAt(i) as? WidgetCardContainer ?: continue
      if (canvasX >= child.left && canvasX <= child.right &&
        canvasY >= child.top && canvasY <= child.bottom
      ) {
        return child
      }
    }
    return null
  }
}
