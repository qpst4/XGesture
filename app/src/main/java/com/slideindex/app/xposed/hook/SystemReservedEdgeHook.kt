package com.slideindex.app.xposed.hook

import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.MODULE_PACKAGE
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_LEFT
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_RIGHT
import com.slideindex.app.xposed.bridge.ModuleHookHandle
import com.slideindex.app.xposed.bridge.ModuleHookSnapshot
import com.slideindex.app.xposed.hookMethod
import com.slideindex.app.xposed.takeover.ReservedEdgeGeometry
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.function.Consumer
import kotlin.math.roundToInt

internal class SystemReservedEdgeHook(private val log: (String) -> Unit) {
  private data class Layout(val density: Float, val left: List<ModuleHookHandle>, val right: List<ModuleHookHandle>) {
    fun handles(side: Int): List<ModuleHookHandle> = if (side == SIDE_LEFT) left else right
    val isEmpty: Boolean get() = left.isEmpty() && right.isEmpty()
  }

  @Volatile private var layout = Layout(1f, emptyList(), emptyList())
  private val handler = Handler(Looper.getMainLooper())
  private val refresh = Runnable { refreshNativeWindows() }
  @Volatile private var display = WeakReference<Any>(null)
  @Volatile private var windowApi: WindowApi? = null
  @Volatile private var retired = false
  @Volatile var isInstalled = false
    private set

  @Synchronized
  fun install(xposed: XposedInterface, loader: ClassLoader): List<XposedInterface.HookHandle> {
    if (retired || isInstalled) return emptyList()
    val displayType = LibXposedReflect.findClass("com.android.server.wm.DisplayContent", loader)
    val windowType = LibXposedReflect.findClass("com.android.server.wm.WindowState", loader)
    val api = WindowApi(displayType, windowType)
    val installed = mutableListOf<XposedInterface.HookHandle>()
    val exclusion = method(displayType, "calculateSystemGestureExclusion", Region::class.java, Region::class.java)
    val touchable = method(windowType, "getTouchableRegion", Region::class.java)

    try {
      installed += xposed.hookMethod(exclusion, object : LibXposedMethodHook() {
        override fun afterHookedMethod(param: HookParam) {
          val owner = param.thisObject ?: return
          val requested = layout
          if (retired || (requested.isEmpty && display.get() === owner)) return
          runCatching {
            if (!rememberDisplay(api, owner) || requested.isEmpty) return
            val outputs = listOfNotNull(param.arg(0) as? Region, param.arg(1) as? Region)
            for (rect in regions(api, owner, requested)) {
              for (output in outputs) output.op(rect, Region.Op.UNION)
            }
          }.onFailure { log("reserved edge exclusion failed: ${it.message}") }
        }
      }, id = "reserved_edge_system_exclusion")
      installed += xposed.hookMethod(touchable, object : LibXposedMethodHook() {
        override fun afterHookedMethod(param: HookParam) {
          val requested = layout
          if (retired || (requested.isEmpty && display.get() != null)) return
          val window = param.thisObject ?: return
          val touchRegion = param.arg(0) as? Region ?: return
          runCatching {
            val attrs = api.attrs.get(window) as? WindowManager.LayoutParams ?: return
            val side = miuiBackSide(attrs)
            if (side == null && triggerSide(attrs) == null) return
            val owner = api.windowDisplay.invoke(window) ?: return
            if (!rememberDisplay(api, owner) || side == null || requested.handles(side).isEmpty()) return
            for (rect in regions(api, owner, requested, side)) touchRegion.op(rect, Region.Op.DIFFERENCE)
          }.onFailure { log("reserved edge window update failed: ${it.message}") }
        }
      }, id = "reserved_edge_miui_back_window")
    } catch (failure: Throwable) {
      installed.forEach { handle -> runCatching { handle.unhook() } }
      throw failure
    }

    windowApi = api
    isInstalled = true
    return installed
  }

  fun setSnapshot(snapshot: ModuleHookSnapshot?) {
    if (retired) return
    val updated = Layout(
      snapshot?.density ?: 1f,
      snapshot?.side(SIDE_LEFT)?.reservedHandles.orEmpty(),
      snapshot?.side(SIDE_RIGHT)?.reservedHandles.orEmpty(),
    )
    if (layout == updated) return
    layout = updated
    requestRefresh()
  }

  fun detach() {
    retired = true
    layout = Layout(1f, emptyList(), emptyList())
    isInstalled = false
    requestRefresh()
  }

  fun isEffective(): Boolean {
    val requested = layout
    if (retired || !isInstalled || requested.isEmpty) return false
    val api = windowApi ?: return false
    val owner = display.get() ?: return false
    return runCatching {
      synchronized(windowManagerLock(owner)) { regions(api, owner, requested).isNotEmpty() }
    }.getOrDefault(false)
  }

  private fun requestRefresh() {
    handler.removeCallbacks(refresh)
    handler.post(refresh)
  }

  private fun rememberDisplay(api: WindowApi, owner: Any): Boolean {
    if (display.get() === owner) return true
    if (api.displayId.invoke(owner) != Display.DEFAULT_DISPLAY) return false
    display = WeakReference(owner)
    return true
  }

  private fun regions(api: WindowApi, owner: Any, requested: Layout, onlySide: Int? = null): List<Rect> {
    val bounds = api.displayBounds.invoke(owner) as? Rect ?: return emptyList()
    if (bounds.isEmpty) return emptyList()
    val height = bounds.height().toFloat()
    val occupiedLeft = mutableListOf<Pair<Float, Float>>()
    val occupiedRight = mutableListOf<Pair<Float, Float>>()
    api.visitWindows.invoke(owner, Consumer<Any> { window ->
      val attrs = api.attrs.get(window) as? WindowManager.LayoutParams ?: return@Consumer
      if ((attrs.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) return@Consumer
      val occupied = when (triggerSide(attrs)) {
        SIDE_LEFT -> occupiedLeft
        SIDE_RIGHT -> occupiedRight
        else -> return@Consumer
      }
      if (api.windowVisible.invoke(window) != true) return@Consumer
      val frame = api.windowFrame.invoke(window) as? Rect ?: return@Consumer
      if (!frame.isEmpty) occupied += ((frame.top - bounds.top) / height) to ((frame.bottom - bounds.top) / height)
    }, true)

    return buildList {
      for (side in listOf(SIDE_LEFT, SIDE_RIGHT)) {
        if (onlySide != null && side != onlySide) continue
        val occupied = if (side == SIDE_LEFT) occupiedLeft else occupiedRight
        for (handle in requested.handles(side)) {
          val width = (handle.widthDp * requested.density).takeIf { it.isFinite() }?.roundToInt()
            ?.coerceIn(0, bounds.width() / 2) ?: continue
          if (width == 0) continue
          val left = if (side == SIDE_LEFT) bounds.left else bounds.right - width
          for ((start, end) in ReservedEdgeGeometry.intervals(handle.topFraction, handle.bottomFraction, occupied)) {
            val top = bounds.top + (height * start).roundToInt()
            val bottom = bounds.top + (height * end).roundToInt()
            if (top < bottom) add(Rect(left, top, left + width, bottom))
          }
        }
      }
    }
  }

  private fun refreshNativeWindows() {
    val owner = display.get() ?: return
    runCatching {
      synchronized(windowManagerLock(owner)) {
        LibXposedReflect.callMethod(owner, "updateSystemGestureExclusion")
        val monitor = LibXposedReflect.callMethod(owner, "getInputMonitor") ?: return@synchronized
        LibXposedReflect.callMethod(monitor, "setUpdateInputWindowsNeededLw")
        LibXposedReflect.callMethod(monitor, "updateInputWindowsLw", true)
      }
    }.onFailure { log("reserved edge refresh failed: ${it.message}") }
  }

  private fun windowManagerLock(owner: Any): Any {
    val service = requireNotNull(LibXposedReflect.getObjectField(owner, "mWmService"))
    return requireNotNull(LibXposedReflect.getObjectField(service, "mGlobalLock"))
  }

  private fun triggerSide(attrs: WindowManager.LayoutParams): Int? {
    if (attrs.packageName != MODULE_PACKAGE) return null
    return when (attrs.title?.toString()) {
      "XG:side-left" -> SIDE_LEFT
      "XG:side-right" -> SIDE_RIGHT
      else -> null
    }
  }

  private fun miuiBackSide(attrs: WindowManager.LayoutParams): Int? {
    if (attrs.packageName != "com.miui.home") return null
    return when (attrs.title?.toString()) {
      "GestureStubLeft" -> SIDE_LEFT
      "GestureStubRight" -> SIDE_RIGHT
      else -> null
    }
  }

  private class WindowApi(displayType: Class<*>, windowType: Class<*>) {
    val displayId = method(displayType, "getDisplayId")
    val displayBounds = method(displayType, "getBounds")
    val visitWindows = method(displayType, "forAllWindows", Consumer::class.java, Boolean::class.javaPrimitiveType!!)
    val attrs = generateSequence(windowType) { it.superclass }
      .firstNotNullOfOrNull { type -> runCatching { type.getDeclaredField("mAttrs") }.getOrNull() }
      ?.apply { isAccessible = true } ?: throw NoSuchFieldException("mAttrs")
    val windowDisplay = method(windowType, "getDisplayContent")
    val windowFrame = method(windowType, "getFrame")
    val windowVisible = method(windowType, "isVisible")
  }

  private companion object {
    fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method =
      generateSequence(type) { it.superclass }.firstNotNullOfOrNull { owner ->
        LibXposedReflect.findMethodExactIfExists(owner, name, *parameters)
      } ?: throw NoSuchMethodException("${type.name}.$name")
  }
}
