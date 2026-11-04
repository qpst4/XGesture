package com.slideindex.app.widget

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.view.View
import android.widget.RemoteViews
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 小组件宿主。
 *
 * 面板里的小组件点击必须先经过这里，原因只有一个：要能在点击后收起面板。
 * 但「收起」必须发生在 PendingIntent **发送之后**——发送前拆窗口会让
 * `RemoteViews.RemoteResponse#getLaunchOptions(view)` 拿到已脱离窗口的视图
 * （sourceBounds / display 丢失），也会把启动放进「无可见窗口」的后台上下文。
 *
 * 所以流程固定为：
 * 1) 视图仍 attached 时取 launch options；
 * 2) `RemoteViews.startPendingIntent(view, pi, options)`（框架等价路径）；
 * 3) 失败才走带 fill-in / ActivityOptions 的兜底 `PendingIntent.send`；
 * 4) 最后在下一帧按 [WidgetClickClosePolicy] 收面板。
 */
class SlideIndexAppWidgetHost(private val appContext: Context) : AppWidgetHost(appContext, HOST_ID) {

  private val mainHandler = Handler(Looper.getMainLooper())

  /**
   * 反射缓存：每次点击都查方法会明显拖慢首帧，这里只解析一次。
   * 解析失败 = 该机型/版本不允许，后续直接走兜底。
   */
  private val startPendingIntentMethod: Method? by lazy {
    runCatching {
      RemoteViews::class.java.getMethod(
        "startPendingIntent",
        View::class.java,
        PendingIntent::class.java,
        Pair::class.java,
      )
    }.onFailure {
      Log.w(TAG, "RemoteViews.startPendingIntent unavailable: ${it.message}")
    }.getOrNull()
  }

  init {
    injectInteractionHandlerIfSupported()
  }

  override fun onCreateView(
    context: Context,
    appWidgetId: Int,
    appWidget: AppWidgetProviderInfo?,
  ): AppWidgetHostView {
    return RoundedAppWidgetHostView(context)
  }

  @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
  private fun injectInteractionHandlerIfSupported() {
    try {
      val handlerClass = Class.forName("android.widget.RemoteViews\$InteractionHandler")
      val proxy = Proxy.newProxyInstance(
        handlerClass.classLoader,
        arrayOf(handlerClass),
        InteractionInvocationHandler(),
      )
      val setMethod = AppWidgetHost::class.java.getDeclaredMethod(
        "setInteractionHandler",
        handlerClass,
      )
      setMethod.isAccessible = true
      setMethod.invoke(this, proxy)
      Log.d(TAG, "InteractionHandler injected")
    } catch (t: Throwable) {
      Log.w(TAG, "InteractionHandler injection failed: ${t.message}", t)
    }
  }

  private inner class InteractionInvocationHandler : InvocationHandler {
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
      if (method.name != "onInteraction" || args == null || args.size < 2) return false
      val view = args[0] as? View ?: return false
      val pendingIntent = args[1] as? PendingIntent ?: return false
      val response = if (args.size > 2) args[2] else null
      return handleInteraction(view, pendingIntent, response)
    }
  }

  /**
   * @return 是否真的把 PendingIntent 送出去了；返回 false 时调用方（RemoteViews）会
   *         认为这次点击没被消费，保持面板不动，用户可以再点。
   */
  private fun handleInteraction(
    view: View,
    pendingIntent: PendingIntent,
    response: Any?,
  ): Boolean {
    WidgetPanelProbe.clickArrived(view.javaClass.simpleName)

    // ① 取 launch options：此时 view 仍在窗口上，sourceBounds / display 才是对的。
    val launchOptions = runCatching { readLaunchOptions(view, response) }
      .onFailure { Log.w(TAG, "getLaunchOptions failed: ${it.message}") }
      .getOrNull()

    // ② 先发送（框架等价路径），失败再兜底。
    var started = false
    var via = "reflection-unavailable"
    val method = startPendingIntentMethod
    if (method != null) {
      via = "startPendingIntent"
      started = runCatching {
        (method.invoke(null, view, pendingIntent, launchOptions) as? Boolean) ?: true
      }.onFailure {
        Log.w(TAG, "startPendingIntent threw: ${it.message}")
      }.getOrDefault(false)
    }
    if (!started) {
      via = "fallback"
      started = sendFallback(pendingIntent, launchOptions)
    }

    WidgetPanelProbe.clickResult(started, via, pendingIntent.isActivity, pendingIntent.creatorPackage)

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, buildClickLog(pendingIntent, launchOptions, started))
    }

    // ③ 最后（下一帧）再收面板，别在点击分发栈里拆视图树。
    if (started && WidgetClickClosePolicy.shouldClose(pendingIntent)) {
      mainHandler.post { WidgetPopupOverlayWindow.dismiss() }
    }
    return started
  }

  /** 反射 `RemoteViews.RemoteResponse#getLaunchOptions(View)`，拿到的就是 fill-in + ActivityOptions。 */
  private fun readLaunchOptions(view: View, response: Any?): Any? {
    val method = response?.javaClass?.getMethod("getLaunchOptions", View::class.java) ?: return null
    return method.invoke(response, view)
  }

  /**
   * 兜底通道：把 fill-in intent 与 ActivityOptions 原样带上。
   * 旧实现传 `null` + 自造 makeBasic()，会把「点的是哪一行/哪一天」丢掉，
   * 目标 app 只能回到默认入口页。
   */
  private fun sendFallback(pendingIntent: PendingIntent, launchOptions: Any?): Boolean {
    val pair = launchOptions as? Pair<*, *>
    val fillInIntent = pair?.first as? Intent
    val options = pair?.second as? ActivityOptions ?: buildFallbackOptions()
    return runCatching {
      pendingIntent.send(appContext, 0, fillInIntent, null, null, null, options.toBundle())
      true
    }.onFailure {
      Log.w(TAG, "fallback PendingIntent.send failed: ${it.message}", it)
    }.getOrDefault(false)
  }

  private fun buildFallbackOptions(): ActivityOptions = ActivityOptions.makeBasic().apply {
    if (Build.VERSION.SDK_INT >= 36) {
      // Android 16 上 ALLOWED 在部分 ROM 不足以从浮层拉起外部 Activity。
      setPendingIntentBackgroundActivityStartMode(
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
      )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      setPendingIntentBackgroundActivityStartMode(
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
      )
    }
  }

  private fun buildClickLog(
    pendingIntent: PendingIntent,
    launchOptions: Any?,
    started: Boolean,
  ): String {
    val pair = launchOptions as? Pair<*, *>
    val fillIn = pair?.first as? Intent
    val extras = fillIn?.extras
    val extrasInfo = if (extras == null) "none" else extras.keySet().joinToString(",")
    return "widget click: started=$started activity=${pendingIntent.isActivity} " +
      "creator=${pendingIntent.creatorPackage} closeMode=${WidgetClickClosePolicy.mode} " +
      "action=${fillIn?.action} data=${fillIn?.data} extras=[$extrasInfo]"
  }

  companion object {
    private const val TAG = "SlideIndexAppWidgetHost"
    const val HOST_ID = 0x534944
  }
}
