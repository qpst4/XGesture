package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.slideindex.app.di.OverlayDependencies
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.service.WidgetBindTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelGridLogic
import com.slideindex.app.widget.WidgetPanelLayoutMetrics
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.widget.WidgetClickClosePolicy
import com.slideindex.app.widget.WidgetPopupCardLayout
import com.slideindex.app.widget.WidgetPopupHost
import com.slideindex.app.widget.WidgetPopupRootLayout
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Samsung OHO+ style full-screen transparent overlay window hosting pure native widget popup card.
 */
@SuppressLint("StaticFieldLeak") // Overlay singleton; views/handlers cleared in cleanup()
object WidgetPopupOverlayWindow {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var windowManager: WindowManager? = null
  private var rootLayout: WidgetPopupRootLayout? = null
  private var cardLayout: WidgetPopupCardLayout? = null
  private var layoutParams: WindowManager.LayoutParams? = null

  private var isVisible = false
  private var blockingTouches = true
  private var currentSettings: AppSettings? = null
  private var currentSide: PanelSide? = null
  private var currentAnchorRawY: Float? = null
  private var isWidgetAddFlowActive = false

  @Volatile
  private var pendingPagesToSave: List<WidgetPanelPage>? = null
  private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
  private var chromeRaiseRunnable: Runnable? = null
  private var appContext: Context? = null
  private var overlayDeps: OverlayDependencies? = null
  private var settingsCollectJob: Job? = null
  private var suspendedForPicker = false
  private var savedFlagsBeforePickerSuspend: Int? = null
  private var backHandler: OverlayViewBackHandler? = null
  private var chromeRaiseToken = 0

  val isShowing: Boolean
    get() = rootLayout != null && isVisible && !suspendedForPicker

  val isAddFlowActive: Boolean
    get() = isWidgetAddFlowActive

  fun show(
    context: Context,
    settings: AppSettings,
    side: PanelSide? = null,
    anchorRawY: Float? = null
  ): Boolean {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      var result = false
      val latch = java.util.concurrent.CountDownLatch(1)
      mainHandler.post {
        result = show(context, settings, side, anchorRawY)
        latch.countDown()
      }
      runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
      return result
    }
    if (isShowing) {
      if (isVisible) return true
      cleanup()
    }
    if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
      Log.w(TAG, "show: accessibility service not enabled")
      return false
    }

    // 每次打开面板都重建宿主视图：跨会话复用 AppWidgetHostView 会让集合型
    // 小组件的行级点击绑定失效（表现为“看得见、点不动 / 只开 app”）。
    WidgetPopupHost.clearCachedHostViews()
    WidgetClickClosePolicy.mode = WidgetClickClosePolicy.modeFromSetting(
      settings.widgetPanelCloseOnClickMode,
    )

    val hostContext = overlayHostContext()
      ?: run {
        Log.w(TAG, "show: accessibility service not connected")
        return false
      }
    val deps = OverlayDependencyAccess.overlayDependencies(hostContext)
      ?: run {
        Log.w(TAG, "show: accessibility service deps unavailable")
        return false
      }

    val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
      ?: return false
    val density = hostContext.resources.displayMetrics.density
    val screenWidthPx = hostContext.resources.displayMetrics.widthPixels
    val effectivePages = WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
    val initialPage = effectivePages.firstOrNull() ?: WidgetPanelPage()
    val initialBox = WidgetPanelLayoutMetrics.computePanelBox(
      screenWidthPx = screenWidthPx,
      page = initialPage,
      density = density,
      pageCount = effectivePages.size,
    )

    val root = WidgetPopupRootLayout(hostContext, onDismissOutside = { dismiss() })
    val card = WidgetPopupCardLayout(
      context = hostContext,
      hostContext = hostContext,
      deps = deps,
      settings = settings,
      onDismiss = { dismiss() },
      onSavePages = { pages -> savePages(pages) }
    )
    root.cardView = card
    // 各页的列数/单元宽度/可见行数/上边距可以不同，切页或改设置时按当前页重算整卡尺寸。
    card.onPageMetricsChanged = { page -> applyCardBoxForPage(page) }

    val cardLp = FrameLayout.LayoutParams(initialBox.widthPx, initialBox.heightPx).apply {
      gravity = if (initialBox.leftMarginPx > 0) {
        Gravity.TOP or Gravity.START
      } else {
        Gravity.TOP or Gravity.CENTER_HORIZONTAL
      }
      marginStart = initialBox.leftMarginPx
      topMargin = initialBox.topMarginPx
    }
    root.addView(card, cardLp)

    val params = buildLayoutParams(hostContext)
    val added = runCatching { wm.addView(root, params) }
      .onFailure { Log.e(TAG, "addView failed", it) }
      .isSuccess
    if (!added) {
      return false
    }

    windowManager = wm
    rootLayout = root
    cardLayout = card
    layoutParams = params
    isVisible = true
    blockingTouches = true
    currentSettings = settings
    currentSide = side
    currentAnchorRawY = anchorRawY
    isWidgetAddFlowActive = false
    appContext = hostContext
    overlayDeps = deps

    OverlayPerformanceMonitorBinding.onOverlayShown(settings, hostContext)
    startSettingsSync(deps)
    screenOffDismissReceiver.register(hostContext)
    OverlayPanelSystemGestureExclusion.attach(root, excludeLeftBackEdge = false)
    if (FloatBallOverlay.isShowing) {
      FloatBallOverlay.notifyPanelAttachedAboveChrome()
    }
    OverlaySceneController.onContentPanelShown()

    WidgetPopupHost.startListening(hostContext)
    SlideIndexAccessibilityService.refreshTriggerVisuals()

    root.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
      override fun onPreDraw(): Boolean {
        root.viewTreeObserver.removeOnPreDrawListener(this)
        card.ensureBackgroundBlurAttached()
        return true
      }
    })

    root.post {
      activateBackHandling()
      scheduleBringChromeAbovePanels()
    }
    return true
  }

  /** Hides the popup while the full-screen widget picker overlay is on top. */
  fun suspendForPickerOverlay() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { suspendForPickerOverlay() }
      return
    }
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    if (suspendedForPicker) return
    deactivateBackHandling()
    suspendedForPicker = true
    savedFlagsBeforePickerSuspend = params.flags
    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    root.visibility = View.GONE
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "suspendForPickerOverlay updateViewLayout failed", it) }
  }

  fun resumeAfterPickerOverlay() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { resumeAfterPickerOverlay() }
      return
    }
    if (!suspendedForPicker) return
    suspendedForPicker = false
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    savedFlagsBeforePickerSuspend?.let { params.flags = it }
    savedFlagsBeforePickerSuspend = null
    if (!isWidgetAddFlowActive) {
      params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
      blockingTouches = true
    }
    if (isVisible && !isWidgetAddFlowActive) {
      root.visibility = View.VISIBLE
    }
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "resumeAfterPickerOverlay updateViewLayout failed", it) }
    if (isVisible && !isWidgetAddFlowActive) {
      activateBackHandling()
      scheduleBringChromeAbovePanels()
    }
  }

  /** Re-bind back handling after predictive-back setting changes while the popup is visible. */
  fun refreshBackHandlingIfVisible() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { refreshBackHandlingIfVisible() }
      return
    }
    if (!isVisible || suspendedForPicker || isWidgetAddFlowActive) return
    activateBackHandling()
  }

  /**
   * §0.16.25：**外部"返回"请求**（手势里的"返回"动作）的入口 —— 与
   * `FloatBallStashPanel.requestBackIfShowing()` 同语义。
   *
   * 为什么必须有它：本窗过去**没有接进手势返回链路**，全靠"系统注入 `KEYCODE_BACK`"来关
   * （见 [activateBackHandling] 的注释）。而在应用级预测性返回（`enableOnBackInvokedCallback=true`）
   * 的机器上，那次注入不再可靠，于是面板再也没有返回路径 —— 真机现象："小组件面板手势返回没反应"。
   *
   * ⚠️ **必须走本窗自己的返回漏斗** `OverlayViewBackHandler.dispatchBack()`（键盘优先在内），
   * 不要在调用侧直接 `dismiss()` —— 那就把 §0.16.7 的"键盘弹着先收键盘、面板留着"抄漏了。
   * handler 还没建起来才退回 [dismiss]（宁可关掉，也不要返回没反应）。
   *
   * @return true = 这次返回由本窗消费，调用方不要再落 `GLOBAL_ACTION_BACK`
   */
  fun requestBackIfVisible(): Boolean {
    if (!isVisible || suspendedForPicker || isWidgetAddFlowActive) return false
    val handler = backHandler
    if (handler != null) {
      handler.dispatchBack()
      return true
    }
    dismiss()
    return true
  }

  fun dismiss() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { dismiss() }
      return
    }
    if (!isVisible) {
      cleanup()
      return
    }
    deactivateBackHandling()
    isVisible = false
    ++chromeRaiseToken
    cleanup()
  }

  fun setWidgetAddFlowActive(active: Boolean) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { setWidgetAddFlowActive(active) }
      return
    }
    isWidgetAddFlowActive = active
    if (active) {
      deactivateBackHandling()
      blockingTouches = false
      hideForWidgetAddFlow()
    } else {
      resumeAfterWidgetAddFlow()
    }
  }

  private fun hideForWidgetAddFlow() {
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    updateOverlayTouchable(false)
    root.visibility = View.GONE
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "hideForWidgetAddFlow updateViewLayout failed", it) }
  }

  private fun resumeAfterWidgetAddFlow() {
    if (rootLayout == null || !isVisible || suspendedForPicker) return
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    root.visibility = View.VISIBLE
    blockingTouches = true
    updateOverlayTouchable(true)
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "resumeAfterWidgetAddFlow updateViewLayout failed", it) }
    activateBackHandling()
    scheduleBringChromeAbovePanels()
  }

  private fun scheduleBringChromeAbovePanels() {
    if (!FloatBallOverlay.isShowing) return
    chromeRaiseRunnable?.let { mainHandler.removeCallbacks(it) }
    chromeRaiseRunnable = null
    var token = ++chromeRaiseToken
    fun attempt() {
      if (token != chromeRaiseToken) return
      FloatBallOverlay.scheduleChromeAbovePanels(delayMs = 0L)
    }
    attempt()
    rootLayout?.post {
      attempt()
      rootLayout?.postOnAnimation { attempt() }
    }
    mainHandler.postDelayed(Runnable { attempt() }.also { chromeRaiseRunnable = it }, CHROME_RAISE_RETRY_MS)
  }

  /** Clear NOT_FOCUSABLE + OverlayViewBackHandler so system back reaches us. */
  private fun activateBackHandling() {
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    if (suspendedForPicker || root.parent == null) return
    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
    runCatching { wm.updateViewLayout(root, params) }
    root.isFocusable = true
    root.isFocusableInTouchMode = true
    root.requestFocus()
    backHandler?.detach()
    backHandler = OverlayViewBackHandler(root, ::dismiss).also { it.attach() }
  }

  private fun deactivateBackHandling() {
    backHandler?.detach()
    backHandler = null
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = layoutParams ?: return
    if (root.parent == null) return
    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    runCatching { wm.updateViewLayout(root, params) }
    root.clearFocus()
  }

  private fun updateOverlayTouchable(touchable: Boolean) {
    val root = rootLayout ?: return
    val wm = windowManager ?: return
    val params = root.layoutParams as? WindowManager.LayoutParams ?: return
    if (touchable) {
      params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    } else {
      params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
    runCatching { wm.updateViewLayout(root, params) }
      .onFailure { Log.w(TAG, "updateOverlayTouchable failed", it) }
  }

  private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
    val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
      WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    return WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      OverlayWindowTypes.contentPanelWindowType(context),
      flags,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      windowAnimations = android.R.style.Animation_Dialog
      layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      OverlayWindowTypes.ensureNoBrightnessOverride(this)
    }
  }

  private fun flushPendingPages() {
    val pending = pendingPagesToSave ?: return
    val deps = overlayDeps ?: return
    deps.widgetPanelPersistence.schedulePersist(pending)
    pendingPagesToSave = null
  }

  private fun savePages(pages: List<WidgetPanelPage>) {
    val deps = overlayDeps ?: return
    pendingPagesToSave = pages
    deps.widgetPanelPersistence.schedulePersist(pages)
  }

  /** 按 [page] 的几何重算卡片尺寸与上边距；数值没变则不动，避免多余布局。 */
  private fun applyCardBoxForPage(page: WidgetPanelPage) {
    val card = cardLayout ?: return
    val ctx = appContext ?: return
    val density = ctx.resources.displayMetrics.density
    val pageCount = WidgetPanelDefaults
      .effectivePages(currentSettings?.widgetPanelPages ?: emptyList())
      .size
    val box = WidgetPanelLayoutMetrics.computePanelBox(
      screenWidthPx = ctx.resources.displayMetrics.widthPixels,
      page = page,
      density = density,
      pageCount = pageCount,
    )
    val lp = card.layoutParams as? FrameLayout.LayoutParams ?: return
    val targetGravity = if (box.leftMarginPx > 0) {
      Gravity.TOP or Gravity.START
    } else {
      Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }
    if (lp.width == box.widthPx &&
      lp.height == box.heightPx &&
      lp.topMargin == box.topMarginPx &&
      lp.marginStart == box.leftMarginPx &&
      lp.gravity == targetGravity
    ) {
      return
    }
    lp.width = box.widthPx
    lp.height = box.heightPx
    lp.topMargin = box.topMarginPx
    lp.marginStart = box.leftMarginPx
    lp.gravity = targetGravity
    card.layoutParams = lp
  }

  private fun cleanup() {
    ++chromeRaiseToken
    chromeRaiseRunnable?.let { mainHandler.removeCallbacks(it) }
    chromeRaiseRunnable = null
    OverlayPerformanceMonitorBinding.onOverlayHidden(appContext)
    settingsCollectJob?.cancel()
    settingsCollectJob = null
    flushPendingPages()
    deactivateBackHandling()
    val root = rootLayout
    val wm = windowManager
    if (root != null) {
      if (wm != null) {
        runCatching { wm.removeView(root) }
      }
    }
    appContext?.let { ctx ->
      if (!WidgetPickerOverlayWindow.isShowing &&
        !WidgetBindTrampolineActivity.isActive()
      ) {
        WidgetPopupHost.stopListening(ctx)
      }
    }
    // 会话结束：宿主视图一起丢掉，下次打开面板重建。
    WidgetPopupHost.clearCachedHostViews()
    screenOffDismissReceiver.unregister()
    rootLayout = null
    cardLayout = null
    layoutParams = null
    windowManager = null
    suspendedForPicker = false
    savedFlagsBeforePickerSuspend = null
    OverlaySceneController.onContentPanelHidden()
    SlideIndexAccessibilityService.refreshTriggerVisuals()
    isVisible = false
    blockingTouches = false
    currentSettings = null
    currentSide = null
    currentAnchorRawY = null
    isWidgetAddFlowActive = false
    pendingPagesToSave = null
    appContext = null
    overlayDeps = null
  }

  private fun startSettingsSync(deps: OverlayDependencies) {
    settingsCollectJob?.cancel()
    settingsCollectJob = overlayScope.launch {
      deps.settingsRepository.settings.collectLatest { latest ->
        currentSettings = latest
        WidgetClickClosePolicy.mode = WidgetClickClosePolicy.modeFromSetting(
          latest.widgetPanelCloseOnClickMode,
        )
        cardLayout?.let { card ->
          val pages = WidgetPanelDefaults.effectivePages(latest.widgetPanelPages)
            .map { WidgetPanelGridLogic.fitPageToGrid(it) }
          card.updatePages(pages)
          card.updateSettings(latest)
        }
        OverlayPerformanceMonitorBinding.syncUserPreference(latest, appContext)
      }
    }
  }

  private const val CHROME_RAISE_RETRY_MS = 200L
  private const val TAG = "WidgetPopupOverlay"
}
