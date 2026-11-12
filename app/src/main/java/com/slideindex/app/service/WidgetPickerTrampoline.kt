package com.slideindex.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.WidgetPickerOverlayWindow
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.ITEM_TYPE_APP
import com.slideindex.app.widget.ITEM_TYPE_SHORTCUT
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelMutator
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.widget.WidgetPopupHost

/**
 * Shows the widget picker (overlay when possible, otherwise a transparent Activity)
 * and delivers the picked widget id after [WidgetBindTrampolineActivity] completes.
 */
object WidgetPickerTrampoline {
  private const val TAG = "WidgetPickerTrampoline"
  private val mainHandler = Handler(Looper.getMainLooper())

  private data class WidgetPanelAddContext(
    val appContext: Context,
    val pageIndex: Int,
    val pagesProvider: () -> List<WidgetPanelPage>
  )

  @Volatile
  private var panelAddContext: WidgetPanelAddContext? = null

  @Volatile
  private var onResult: ((Int) -> Unit)? = null

  @Volatile
  private var onAppResult: ((String, String, String, Int) -> Unit)? = null

  @Volatile
  private var onShortcutResult: ((packageName: String, shortcutId: String, label: String, intentUri: String) -> Unit)? = null

  @Volatile
  private var onActionResult: ((actionPayload: String, label: String) -> Unit)? = null

  @Volatile
  private var onPagesChanged: ((List<WidgetPanelPage>) -> Unit)? = null

  @Volatile
  private var onCancel: (() -> Unit)? = null

  fun launch(
    context: Context,
    pageIndex: Int,
    pagesProvider: () -> List<WidgetPanelPage>,
    onAdded: (Int) -> Unit,
    onAppAdded: ((packageName: String, className: String, label: String, cloneUserId: Int) -> Unit)? = null,
    onShortcutAdded: ((packageName: String, shortcutId: String, label: String, intentUri: String) -> Unit)? = null,
    onActionAdded: ((actionPayload: String, label: String) -> Unit)? = null,
    onPagesChanged: ((List<WidgetPanelPage>) -> Unit)? = null,
    onCancelled: () -> Unit = {}
  ) {
    Log.d(TAG, "launch")
    panelAddContext = WidgetPanelAddContext(
      appContext = context.applicationContext,
      pageIndex = pageIndex,
      pagesProvider = pagesProvider
    )
    onResult = onAdded
    onAppResult = onAppAdded
    onShortcutResult = onShortcutAdded
    onActionResult = onActionAdded
    this.onPagesChanged = onPagesChanged
    onCancel = onCancelled

    val runLaunch = {
      WidgetPopupOverlayWindow.setWidgetAddFlowActive(true)
      // 优先透明 Activity：普通窗口的列表滚动/搜索/返回栈更可靠（与快速启动器、
      // 边角/圆环的“外部编辑页”同一形态）。启动失败（OEM 后台启动限制）才退回 overlay。
      if (!launchActivityPicker(context)) {
        Log.w(TAG, "activity picker launch failed, falling back to overlay")
        val canUseOverlay = PermissionHelper.isAccessibilityServiceEnabledForOverlays(context) &&
          SlideIndexAccessibilityService.overlayHostContext() != null
        if (canUseOverlay) {
          if (!WidgetPickerOverlayWindow.show(context)) {
            Log.w(TAG, "overlay picker also failed; cancel add flow")
            WidgetPickerTrampoline.deliverCancel()
          }
        } else {
          Log.w(TAG, "accessibility overlay host unavailable; cancel add flow")
          WidgetPickerTrampoline.deliverCancel()
        }
      }
    }

    if (Looper.myLooper() == Looper.getMainLooper()) {
      runLaunch()
    } else {
      mainHandler.post { runLaunch() }
    }
  }

  fun startBindFlow(context: Context, provider: ComponentName) {
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(true)
    val appContext = context.applicationContext
    // widgetId 与 AppWidgetHost 的注册都在 WidgetBindTrampolineActivity 所在的 :overlay 进程完成。
    val intent = WidgetBindTrampolineActivity.createIntent(appContext, provider)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
  }

  fun deliverSuccess(appWidgetId: Int) {
    Log.d(TAG, "deliverSuccess: id=$appWidgetId")
    persistWidgetAdd(appWidgetId)
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onResult
    clear()
    callback?.invoke(appWidgetId)
  }

  fun deliverAppSuccess(packageName: String, className: String, label: String, cloneUserId: Int = 0) {
    Log.d(TAG, "deliverAppSuccess: pkg=$packageName, cls=$className, uid=$cloneUserId")
    persistAppAdd(packageName, className, label, cloneUserId)
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onAppResult
    clear()
    callback?.invoke(packageName, className, label, cloneUserId)
  }

  fun deliverAppsSuccess(apps: List<com.slideindex.app.widget.InstalledAppEntry>) {
    Log.d(TAG, "deliverAppsSuccess: count=${apps.size}")
    val ctx = panelAddContext
    if (ctx != null) {
      var currentPages = ctx.pagesProvider()
      for (app in apps) {
        val next = WidgetPanelMutator.addAppToPage(
          ctx.appContext,
          currentPages,
          ctx.pageIndex,
          app.packageName,
          app.className,
          app.appLabel,
          app.cloneUserId,
        )
        if (next != null) {
          currentPages = next
        }
      }
      schedulePersist(ctx.appContext, currentPages)
    }
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onAppResult
    clear()
    if (apps.isNotEmpty()) {
      val first = apps.first()
      callback?.invoke(first.packageName, first.className, first.appLabel, first.cloneUserId)
    }
  }

  fun deliverShortcutSuccess(packageName: String, shortcutId: String, label: String, intentUri: String) {
    Log.d(TAG, "deliverShortcutSuccess: pkg=$packageName, id=$shortcutId")
    persistShortcutAdd(packageName, shortcutId, label, intentUri)
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onShortcutResult
    clear()
    callback?.invoke(packageName, shortcutId, label, intentUri)
  }

  fun deliverShortcutsSuccess(shortcuts: List<com.slideindex.app.widget.ShortcutEntry>) {
    Log.d(TAG, "deliverShortcutsSuccess: count=${shortcuts.size}")
    val ctx = panelAddContext
    if (ctx != null) {
      var currentPages = ctx.pagesProvider()
      for (sc in shortcuts) {
        val next = WidgetPanelMutator.addShortcutToPage(
          ctx.appContext,
          currentPages,
          ctx.pageIndex,
          sc.packageName,
          sc.shortcutId,
          sc.label,
          sc.intentUri
        )
        if (next != null) {
          currentPages = next
        }
      }
      schedulePersist(ctx.appContext, currentPages)
    }
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onShortcutResult
    clear()
    if (shortcuts.isNotEmpty()) {
      val first = shortcuts.first()
      callback?.invoke(first.packageName, first.shortcutId, first.label, first.intentUri)
    }
  }

  fun deliverActionSuccess(actionPayload: String, label: String) {
    Log.d(TAG, "deliverActionSuccess: action=$actionPayload")
    persistActionAdd(actionPayload, label)
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onActionResult
    clear()
    callback?.invoke(actionPayload, label)
  }

  fun getCurrentPageConfiguredItems(): Pair<Set<String>, Set<String>> {
    val ctx = panelAddContext ?: return emptySet<String>() to emptySet<String>()
    val effective = WidgetPanelDefaults.effectivePages(ctx.pagesProvider())
    val index = ctx.pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    // 勾选态按目标（包名 + user）走：主实例与分身各自独立，不会再互相顶掉。
    val appPkgs = page.items
      .filter { it.itemType == ITEM_TYPE_APP }
      .map { com.slideindex.app.launcher.AppTarget.encode(it.packageName, it.cloneUserId) }
      .toSet()
    val shortcutKeys = page.items.filter { it.itemType == ITEM_TYPE_SHORTCUT }.map {
      it.packageName + "/" + (it.shortcutId.ifBlank { it.intentUri })
    }.toSet()
    return appPkgs to shortcutKeys
  }

  fun toggleApp(packageName: String, className: String, label: String, cloneUserId: Int = 0): Boolean {
    val ctx = panelAddContext ?: return false
    val currentPages = ctx.pagesProvider()
    val index = ctx.pageIndex.coerceIn(0, currentPages.lastIndex)
    val page = currentPages[index]
    val exists = page.items.any {
      it.itemType == ITEM_TYPE_APP && it.packageName == packageName && it.cloneUserId == cloneUserId
    }
    val nextPages = if (exists) {
      WidgetPanelMutator.removeAppFromPage(currentPages, ctx.pageIndex, packageName, cloneUserId)
    } else {
      WidgetPanelMutator.addAppToPage(
        ctx.appContext, currentPages, ctx.pageIndex, packageName, className, label, cloneUserId,
      )
    }
    if (nextPages != null) {
      schedulePersist(ctx.appContext, nextPages)
      onPagesChanged?.invoke(nextPages)
      return !exists
    }
    return exists
  }

  fun toggleShortcut(packageName: String, shortcutId: String, label: String, intentUri: String): Boolean {
    val ctx = panelAddContext ?: return false
    val currentPages = ctx.pagesProvider()
    val index = ctx.pageIndex.coerceIn(0, currentPages.lastIndex)
    val page = currentPages[index]
    val exists = page.items.any {
      it.itemType == ITEM_TYPE_SHORTCUT && it.packageName == packageName &&
        (it.shortcutId == shortcutId || (intentUri.isNotBlank() && it.intentUri == intentUri))
    }
    val nextPages = if (exists) {
      WidgetPanelMutator.removeShortcutFromPage(currentPages, ctx.pageIndex, packageName, shortcutId, intentUri)
    } else {
      WidgetPanelMutator.addShortcutToPage(ctx.appContext, currentPages, ctx.pageIndex, packageName, shortcutId, label, intentUri)
    }
    if (nextPages != null) {
      schedulePersist(ctx.appContext, nextPages)
      onPagesChanged?.invoke(nextPages)
      return !exists
    }
    return exists
  }

  fun deliverCancel() {
    Log.d(TAG, "deliverCancel")
    panelAddContext = null
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onCancel
    clear()
    callback?.invoke()
  }

  private fun launchActivityPicker(context: Context): Boolean {
    val intent = WidgetPickerTrampolineActivity.createIntent(context).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }
      .onFailure { Log.e(TAG, "activity picker launch failed", it) }
      .isSuccess
  }

  private fun clear() {
    panelAddContext = null
    onResult = null
    onAppResult = null
    onShortcutResult = null
    onActionResult = null
    onCancel = null
  }

  private fun persistWidgetAdd(appWidgetId: Int) {
    val ctx = panelAddContext ?: return
    val updated = WidgetPanelMutator.addWidgetToPage(
      ctx.appContext,
      ctx.pagesProvider(),
      ctx.pageIndex,
      appWidgetId
    ) ?: return
    schedulePersist(ctx.appContext, updated)
  }

  private fun persistAppAdd(packageName: String, className: String, label: String, cloneUserId: Int) {
    val ctx = panelAddContext ?: return
    val updated = WidgetPanelMutator.addAppToPage(
      ctx.appContext,
      ctx.pagesProvider(),
      ctx.pageIndex,
      packageName,
      className,
      label,
      cloneUserId,
    ) ?: return
    schedulePersist(ctx.appContext, updated)
  }

  private fun persistShortcutAdd(
    packageName: String,
    shortcutId: String,
    label: String,
    intentUri: String
  ) {
    val ctx = panelAddContext ?: return
    val updated = WidgetPanelMutator.addShortcutToPage(
      ctx.appContext,
      ctx.pagesProvider(),
      ctx.pageIndex,
      packageName,
      shortcutId,
      label,
      intentUri
    ) ?: return
    schedulePersist(ctx.appContext, updated)
  }

  private fun persistActionAdd(actionPayload: String, label: String) {
    val ctx = panelAddContext ?: return
    val updated = WidgetPanelMutator.addActionToPage(
      ctx.appContext,
      ctx.pagesProvider(),
      ctx.pageIndex,
      actionPayload,
      label
    ) ?: return
    schedulePersist(ctx.appContext, updated)
  }

  private fun schedulePersist(context: Context, pages: List<WidgetPanelPage>) {
    OverlayDependencyAccess.overlayDependencies(context)
      ?.widgetPanelPersistence
      ?.schedulePersist(pages)
      ?: Log.w(TAG, "schedulePersist skipped: overlay dependencies unavailable")
  }
}
