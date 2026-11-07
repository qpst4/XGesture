package com.slideindex.app.overlay

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver

/**
 * 给悬浮窗指定「局部可触摸区域」：窗口可以比内容大（为了容纳投影），
 * 但只有 [regionProvider] 返回的矩形接收触摸，其余部分由系统穿透给下层 app。
 *
 * Android 没有公开的窗口触摸区域 API，只能走 framework 的
 * `ViewTreeObserver.OnComputeInternalInsetsListener`（hidden）。项目已经通过
 * [com.slideindex.app.util.HiddenApiBootstrap] 豁免了整个 framework 包；
 * 反射失败时保持原行为（整窗可触摸），只多一圈死区，不影响功能。
 */
object OverlayTouchableRegion {
    private const val TAG = "OverlayTouchRegion"

    /**
     * @param view 已 attach 到窗口的根 View
     * @param regionProvider 返回窗口坐标系下的可触摸矩形（null 表示不限制）
     */
    fun install(view: View, regionProvider: () -> Rect?) {
        val installed = runCatching {
            val listenerType =
                Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val infoType = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val regionField = infoType.getField("touchableRegion")
            val insetsRegion = infoType.getField("TOUCHABLE_INSETS_REGION").getInt(null)
            val setTouchableInsets =
                infoType.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerType.classLoader,
                arrayOf(listenerType)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args?.size == 1) {
                    val info = args[0]
                    val region = regionField.get(info) as? android.graphics.Region
                    val rect = regionProvider()
                    if (region != null && rect != null) {
                        region.set(rect)
                        setTouchableInsets.invoke(info, insetsRegion)
                    }
                }
                null
            }
            ViewTreeObserver::class.java
                .getMethod("addOnComputeInternalInsetsListener", listenerType)
                .invoke(view.viewTreeObserver, listener)
        }
        installed.onFailure { Log.w(TAG, "touchable region unavailable", it) }
    }
}
