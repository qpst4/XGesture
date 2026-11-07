package com.slideindex.app.overlay.backpanel

import android.content.Context
import android.view.ViewGroup
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.gestureHintStyle
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerHandle

class BackPanelOverlayController(
    val side: PanelSide,
) {
    private var panel: BackPanel? = null
    private var controller: BackPanelController? = null
    private var parent: ViewGroup? = null
    private var appContext: Context? = null
    private var enabled = false
    var onSettled: (() -> Unit)? = null
        set(value) {
            field = value
            controller?.onSettled = value
        }

    val isShowing: Boolean
        get() = controller?.isShowing == true

    fun attach(parent: ViewGroup, context: Context) {
        appContext = context.applicationContext
        this.parent = parent
        val host = panel ?: BackPanel(context).also { panel = it }
        val ctrl = controller ?: BackPanelController(host).also { created ->
            created.onSettled = onSettled
            controller = created
        }
        ctrl.attach(parent)
        host.z = GESTURE_ANIMATION_Z_INDEX
    }

    fun applySettings(settings: AppSettings, handleId: String? = null) {
        enabled = settings.gestureHintEnabled &&
            settings.gestureHintStyle() == GestureHintStyle.ANDROID
        val density = appContext?.resources?.displayMetrics?.density ?: 3f
        val handle = if (handleId != null) {
            settings.triggerHandle(side, handleId) ?: settings.primaryTriggerHandle(side)
        } else {
            settings.primaryTriggerHandle(side)
        }
        controller?.extraFingerOffsetPx = settings.gestureHintFingerOffsetDp * density
        controller?.setActivationThresholdPx(handle.shortSwipeDistanceDp * density)
        panel?.let { host ->
            val paints = AndroidBackPanelColors.resolve(host.context, settings)
            host.setPaintColors(paints.background, paints.arrow)
        }
        if (!enabled) {
            hide()
        }
    }

    /** 顶/底边：整块面板旋转 90°，触摸坐标按轴转置后复用左右边那套逻辑。 */
    private val isRotatedEdge: Boolean
        get() = side == PanelSide.TOP || side == PanelSide.BOTTOM

    /** 屏幕高度缓存（底边的内滑坐标要换算成「距底边多远」）。 */
    private var displayHeightPx = 0

    private fun rotationDegrees(): Float = when (side) {
        PanelSide.TOP -> 90f
        PanelSide.BOTTOM -> -90f
        else -> 0f
    }

    /**
     * 内滑轴坐标：**向内单调递增**，0 = 贴屏边。
     *
     * 和左右边一样要单调增：面板内部用速度符号判断 fling，底边若直接用 -rawY，
     * 向内滑动会得到负速度、永远判不成 flung。
     */
    private fun inwardCoordinate(rawX: Float, rawY: Float): Float = when (side) {
        PanelSide.TOP -> rawY
        PanelSide.BOTTOM -> displayHeightPx - rawY
        else -> rawX
    }

    /** 沿边轴：左右边是 Y；顶/底边是 X。 */
    private fun alongCoordinate(rawX: Float, rawY: Float): Float = when (side) {
        PanelSide.TOP, PanelSide.BOTTOM -> rawX
        else -> rawY
    }

    fun onDown(
        rawX: Float,
        rawY: Float,
        translationOriginX: Float = rawX,
        activationThresholdPx: Float? = null,
    ) {
        if (!enabled) return
        val ctrl = controller ?: return
        val ctx = appContext ?: return
        val metrics = OverlayScreenMetrics.snapshot(ctx)
        displayHeightPx = metrics.heightPx
        ctrl.setDisplaySize(metrics.widthPx, metrics.heightPx)
        ctrl.setIsLeftPanel(if (isRotatedEdge) true else side == PanelSide.LEFT)
        ctrl.setEdgeRotation(rotationDegrees(), metrics.heightPx)
        activationThresholdPx?.let { ctrl.setActivationThresholdPx(it) }
        ctrl.onDown(
            x = inwardCoordinate(rawX, rawY),
            y = alongCoordinate(rawX, rawY),
            // 旋转后的内滑坐标 0 就是屏幕边，与左右边传 stripOriginX 等价。
            translationOriginX = if (isRotatedEdge) 0f else translationOriginX,
        )
    }

    fun onMove(rawX: Float, rawY: Float) {
        if (!enabled) return
        controller?.onMove(
            x = inwardCoordinate(rawX, rawY),
            y = alongCoordinate(rawX, rawY),
        )
    }

    fun onUp(rawX: Float, rawY: Float) {
        if (!enabled) {
            hide()
            return
        }
        controller?.onUp(
            x = inwardCoordinate(rawX, rawY),
            y = alongCoordinate(rawX, rawY),
        )
    }

    fun onCancel() {
        controller?.onCancel()
    }

    fun hide() {
        controller?.hideImmediately()
    }

    fun detach() {
        hide()
        controller?.detach()
        panel = null
        controller = null
        parent = null
        appContext = null
    }

    private companion object {
        const val GESTURE_ANIMATION_Z_INDEX = 40f
    }
}

object BackPanelOverlayRegistry {
    private val controllers = mutableMapOf<PanelSide, BackPanelOverlayController>()

    fun controller(side: PanelSide): BackPanelOverlayController =
        controllers.getOrPut(side) { BackPanelOverlayController(side) }

    fun isAnyShowing(): Boolean = controllers.values.any { it.isShowing }

    fun detachAll() {
        controllers.values.forEach { it.detach() }
        controllers.clear()
    }
}
