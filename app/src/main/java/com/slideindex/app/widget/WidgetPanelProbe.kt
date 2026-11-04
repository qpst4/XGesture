package com.slideindex.app.widget

import android.util.Log

/**
 * 临时诊断探针：定位「小组件面板点击无反应」用。
 *
 * 统一 TAG，方便 `adb logcat -s WidgetPanelProbe` 过滤，也方便确认后整体拆除
 * （删本文件 + grep `WidgetPanelProbe` 清掉调用点即可）。
 *
 * 只挂在事件级路径（一次点击 / 一次绑定 / 一次页重建 / 一次手势按下），
 * **不进入** ACTION_MOVE、onDraw、onLayout 等每帧路径，所以对性能与耗电没有可测影响。
 *
 * 用 WARN 级别而不是 DEBUG：不需要 `setprop log.tag.*`，装上就能抓，省一次重装。
 */
internal object WidgetPanelProbe {
  private const val TAG = "WidgetPanelProbe"

  /** ① 点击是否到达宿主。没有这一行 = 事件根本没到，或那一代 RemoteViews 没有点击绑定。 */
  fun clickArrived(viewClass: String) {
    Log.w(TAG, "① click arrived: view=$viewClass")
  }

  /** ② 点击结果：发没发出去、走的是哪条路、目标是不是 Activity。 */
  fun clickResult(started: Boolean, via: String, isActivity: Boolean, creator: String?) {
    Log.w(TAG, "② click result: started=$started via=$via activity=$isActivity creator=$creator")
  }

  /**
   * ③ 坐标换算：旧公式与「中心轴心」公式的差值。
   *
   * 默认配置（4 列 / 62dp 单元格 / 90dp 标准格）下 dx、dy 应接近
   * `renderWidthPx / 2 × (1 - 1/scale)` ≈ 100dp，修好后应接近 0。
   */
  fun coordDelta(
    cardX: Float,
    cardY: Float,
    oldHostX: Float,
    oldHostY: Float,
    newHostX: Float,
    newHostY: Float,
    scale: Float,
  ) {
    Log.w(
      TAG,
      "③ coord: card=($cardX,$cardY) old=($oldHostX,$oldHostY) new=($newHostX,$newHostY) " +
        "dx=${newHostX - oldHostX} dy=${newHostY - oldHostY} scale=$scale",
    )
  }

  /** ④ 宿主视图来源：新建还是复用（复用已 detach 的实例是「点不动」的重点嫌疑）。 */
  fun hostViewObtained(appWidgetId: Int, fromCache: Boolean) {
    Log.w(TAG, "④ host view: id=$appWidgetId fromCache=$fromCache")
  }

  /** ⑤ 页面重建：内容换了一代的时间点。 */
  fun canvasRebind(pageId: Long, cardCount: Int) {
    Log.w(TAG, "⑤ canvas rebind: page=$pageId cards=$cardCount")
  }

  /** ⑥ 手势按下：命中哪张卡、画布有没有接住这次手势。 */
  fun gestureDown(editMode: Boolean, foundCard: Boolean, handled: Boolean) {
    Log.w(TAG, "⑥ gesture down: editMode=$editMode foundCard=$foundCard handled=$handled")
  }
}
