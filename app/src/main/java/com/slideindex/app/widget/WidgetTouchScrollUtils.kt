package com.slideindex.app.widget

import android.view.View
import android.view.ViewGroup

internal object WidgetTouchScrollUtils {
  fun findDeepestViewAt(root: View, localX: Float, localY: Float): View? {
    if (root.visibility != View.VISIBLE) return null
    if (root !is ViewGroup) {
      return if (localX >= 0 && localY >= 0 && localX < root.width && localY < root.height) root else null
    }

    val scrolledX = localX + root.scrollX
    val scrolledY = localY + root.scrollY

    for (i in root.childCount - 1 downTo 0) {
      val child = root.getChildAt(i)
      if (child.visibility != View.VISIBLE) continue
      val childX = scrolledX - child.left
      val childY = scrolledY - child.top
      val transformedX = childX - child.translationX
      val transformedY = childY - child.translationY
      if (transformedX < 0 || transformedY < 0 ||
        transformedX >= child.width || transformedY >= child.height
      ) {
        continue
      }
      val deeper = findDeepestViewAt(child, transformedX, transformedY)
      if (deeper != null) return deeper
      return child
    }
    return if (localX >= 0 && localY >= 0 && localX < root.width && localY < root.height) root else null
  }

  fun isScrollableOrFlipperContainer(view: View): Boolean {
    val className = view.javaClass.name
    if (className.contains("StackView") ||
      className.contains("AdapterViewFlipper") ||
      className.contains("ViewFlipper") ||
      className.contains("AdapterViewAnimator") ||
      className.contains("ViewPager") ||
      className.contains("ScrollView") ||
      className.contains("ListView") ||
      className.contains("RecyclerView") ||
      className.contains("GridView")
    ) {
      return true
    }
    if (view is android.widget.AdapterView<*>) return true
    return false
  }

  fun canScrollAtPoint(root: View, localX: Float, localY: Float, axis: Int, delta: Float): Boolean {
    val hit = findDeepestViewAt(root, localX, localY) ?: return false
    val direction = if (delta < 0) 1 else -1
    var current: View? = hit
    while (current != null) {
      // 只把「翻转类」容器当作无边界滚动（它们没有 canScrollVertically 的边界语义）；
      // 普通列表/滚动容器必须按边界判断，否则到顶/到底也无法把滚动交还给面板。
      if (isFlipContainer(current)) {
        return true
      }
      val canScroll = when (axis) {
        0 -> current.canScrollVertically(direction)
        else -> current.canScrollHorizontally(direction)
      }
      if (canScroll) return true
      if (current === root) break
      current = current.parent as? View
    }
    return false
  }

  /** 翻转类容器（StackView / ViewFlipper 等）没有滚动边界，只能靠类名识别。 */
  private fun isFlipContainer(view: View): Boolean {
    val className = view.javaClass.name
    return className.contains("StackView") ||
      className.contains("AdapterViewFlipper") ||
      className.contains("ViewFlipper") ||
      className.contains("AdapterViewAnimator")
  }

  fun hasScrollableOrInteractiveContentAtPoint(root: View, localX: Float, localY: Float): Boolean {
    val hit = findDeepestViewAt(root, localX, localY) ?: return false
    var current: View? = hit
    while (current != null) {
      if (isScrollableOrFlipperContainer(current)) {
        return true
      }
      if (current.canScrollVertically(-1) || current.canScrollVertically(1) ||
        current.canScrollHorizontally(-1) || current.canScrollHorizontally(1)
      ) {
        return true
      }
      if (current.isClickable || current.isLongClickable || current.hasOnClickListeners()) {
        return true
      }
      if (current.isFocusableInTouchMode) return true
      if (current === root) break
      current = current.parent as? View
    }
    return false
  }

  fun requestDisallowInterceptAllParents(view: View, disallow: Boolean) {
    var current = view.parent
    while (current != null) {
      current.requestDisallowInterceptTouchEvent(disallow)
      current = current.parent
    }
  }
}
