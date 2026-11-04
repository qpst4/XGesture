package com.slideindex.app.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * Shared [AppWidgetHost] for the widget popup overlay and the settings editor.
 * Host id is arbitrary but must stay stable across process lifetime.
 */
object WidgetPopupHost {
  private const val TAG = "WidgetPopupHost"

  @Volatile
  private var host: SlideIndexAppWidgetHost? = null

  @Volatile
  private var listening = false

  private val cachedHostViews = java.util.concurrent.ConcurrentHashMap<Int, AppWidgetHostView>()

  fun appWidgetHost(context: Context): SlideIndexAppWidgetHost {
    val appContext = context.applicationContext
    return host ?: synchronized(this) {
      host ?: SlideIndexAppWidgetHost(appContext).also { host = it }
    }
  }

  fun appWidgetManager(context: Context): AppWidgetManager =
    AppWidgetManager.getInstance(context.applicationContext)

  fun startListening(context: Context) {
    synchronized(this) {
      if (listening) return
      runCatching {
        appWidgetHost(context).startListening()
        listening = true
      }.onFailure { Log.e(TAG, "startListening failed", it) }
    }
  }

  fun stopListening(context: Context) {
    synchronized(this) {
      if (!listening) return
      runCatching {
        appWidgetHost(context).stopListening()
        listening = false
      }.onFailure { Log.e(TAG, "stopListening failed", it) }
    }
  }

  fun allocateAppWidgetId(context: Context): Int =
    appWidgetHost(context).allocateAppWidgetId()

  fun deleteAppWidgetId(context: Context, appWidgetId: Int) {
    cachedHostViews.remove(appWidgetId)?.let { view ->
      runCatching { (view.parent as? ViewGroup)?.removeView(view) }
    }
    runCatching { appWidgetHost(context).deleteAppWidgetId(appWidgetId) }
      .onFailure { Log.w(TAG, "deleteAppWidgetId($appWidgetId) failed", it) }
  }

  fun obtainHostView(context: Context, appWidgetId: Int): AppWidgetHostView? {
    cachedHostViews[appWidgetId]?.let { cached ->
      WidgetPanelProbe.hostViewObtained(appWidgetId, fromCache = true)
      (cached.parent as? ViewGroup)?.removeView(cached)
      return cached
    }
    val created = createView(context, appWidgetId) as? AppWidgetHostView
    WidgetPanelProbe.hostViewObtained(appWidgetId, fromCache = false)
    return created
  }

  /**
   * 会话级缓存清理：面板关闭、重新打开前调用。
   *
   * 为什么不能跨会话复用：`AppWidgetHostView` 一旦 detach，集合型小组件
   * （日程/邮件这类 `RemoteViewsAdapter`）的行级点击绑定会失效或停在上一代
   * RemoteViews 上——表现就是“面板里看得见内容，但点行只开 app / 点了没反应”。
   * 每次面板会话重建宿主视图，是目前最稳的解法。
   */
  fun clearCachedHostViews() {
    val views = cachedHostViews.values.toList()
    cachedHostViews.clear()
    views.forEach { view ->
      runCatching { (view.parent as? ViewGroup)?.removeView(view) }
    }
  }

  fun providerInfo(context: Context, appWidgetId: Int): AppWidgetProviderInfo? =
    runCatching { appWidgetManager(context).getAppWidgetInfo(appWidgetId) }.getOrNull()

  fun createView(context: Context, appWidgetId: Int): View? {
    val manager = appWidgetManager(context)
    val info = manager.getAppWidgetInfo(appWidgetId)
    if (info == null) {
      Log.w(TAG, "createView($appWidgetId): getAppWidgetInfo returned null")
      return null
    }
    val widgetHost = appWidgetHost(context)
    return runCatching { widgetHost.createView(context, appWidgetId, info) }
      .onFailure { Log.e(TAG, "createView($appWidgetId) failed", it) }
      .getOrNull()
      ?.also { view ->
        cachedHostViews[appWidgetId] = view
      }
  }

  fun labelFor(context: Context, appWidgetId: Int): String {
    val info = providerInfo(context, appWidgetId) ?: return ""
    @Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
    return info.loadLabel(context.packageManager)?.toString().orEmpty()
  }
}
