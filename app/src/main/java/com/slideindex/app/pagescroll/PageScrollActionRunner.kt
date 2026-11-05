package com.slideindex.app.pagescroll

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.PanelSide

/**
 * 「页面滚动」动作的落地点：把动作系统与 [PageScrollAutoScroller] 隔开一层。
 *
 * 引擎是**进程级单例**：控制条本身是常驻浮层，同一条控制条要么在跑（再触发则关闭），
 * 要么关着（触发则打开）。这样动作里不需要保存任何状态。
 */
object PageScrollActionRunner {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var scroller: PageScrollAutoScroller? = null

    @Volatile
    private var holdDrag: PageScrollHoldDragController? = null

    /**
     * 打开 / 关闭滚动条。
     *
     * 内部会切回主线程（挂窗口与派发手势都要求主线程）。
     *
     * @param anchorRawY 触发点（用来决定控制条初始的竖直位置）
     * @param side 触发边缘；左/右边决定控制条贴哪一侧，上/下边按右手侧处理
     * @return true 表示已受理
     */
    fun toggle(context: Context, side: PanelSide, anchorRawY: Float): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { toggle(context, side, anchorRawY) }
            return true
        }
        val engine = scroller ?: PageScrollAutoScroller(context.applicationContext) { }
            .also { scroller = it }
        val barSide = if (side == PanelSide.LEFT) {
            PageScrollControlBar.Side.LEFT
        } else {
            PageScrollControlBar.Side.RIGHT
        }
        return engine.toggle(barSide, anchorRawY)
    }

    /** 当前动作是否正在运行（供 UI 显示"打开/关闭"之类的状态）。 */
    fun isRunning(): Boolean = scroller?.isRunning == true

    // ---- 按住拖动执行方式：由手势会话在 开始/移动/抬起 三个时机驱动 ----

    fun beginHoldDrag(context: Context, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { beginHoldDrag(context, rawY) }
            return
        }
        val controller = holdDrag ?: PageScrollHoldDragController(context.applicationContext)
            .also { holdDrag = it }
        controller.begin(rawY)
    }

    fun updateHoldDrag(rawY: Float) {
        holdDrag?.update(rawY)
    }

    fun endHoldDrag() {
        holdDrag?.end()
    }

    /** 强制关闭（例如无障碍服务被关闭时）。 */
    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stop() }
            return
        }
        scroller?.stop()
        holdDrag?.end()
    }

    /** 传入的动作是否为本模块负责的动作。 */
    fun handles(action: GestureAction): Boolean = action is GestureAction.PageScroll
}
