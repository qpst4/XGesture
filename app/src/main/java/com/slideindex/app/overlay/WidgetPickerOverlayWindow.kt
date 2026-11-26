package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.ui.WidgetPickerScreen
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetPopupHost
import kotlinx.coroutines.delay

@SuppressLint("StaticFieldLeak") // Overlay singleton; views/handlers cleared in cleanup()
object WidgetPickerOverlayWindow {
  private const val TAG = "WidgetPickerOverlay"
  private val mainHandler = Handler(Looper.getMainLooper())
  private var windowManager: WindowManager? = null
  private var composeView: ComposeView? = null
  private var owner: OverlayComposeOwner? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  private var backHandler: OverlayViewBackHandler? = null
  private var requestAnimatedDismiss: (() -> Unit)? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismissFromBack() }
    private var appContext: Context? = null
  @Volatile
  private var dismissing = false

  val isShowing: Boolean get() = composeView != null && !dismissing

  private fun handlePanelBack() {
    requestAnimatedDismiss?.invoke() ?: dismissFromBack()
  }

  fun show(context: Context): Boolean {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      error("WidgetPickerOverlayWindow.show must be called on the main thread")
    }
    if (isShowing) {
      val existing = composeView
      if (existing != null && existing.isAttachedToWindow) {
        existing.requestFocus()
        return true
      }
      forceCleanupStalePicker()
    }
    dismissing = false
    if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
      Log.w(TAG, "accessibility service not enabled")
      WidgetPickerTrampoline.deliverCancel()
      return false
    }

    val hostContext = SlideIndexAccessibilityService.overlayHostContext()
    if (hostContext == null) {
      Log.w(TAG, "accessibility service not connected")
      WidgetPickerTrampoline.deliverCancel()
      return false
    }

    WidgetPopupOverlayWindow.suspendForPickerOverlay()
    WidgetPopupHost.startListening(hostContext)
    val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
      WidgetPopupOverlayWindow.resumeAfterPickerOverlay()
      return false
    }
    val dialogOwner = OverlayComposeOwner()
    var pendingView: ComposeView? = null

    return try {
      val appDeps = runCatching {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
          hostContext.applicationContext,
          com.slideindex.app.di.AppGraphEntryPoint::class.java
        ).dependencies()
      }.getOrNull()

      val view = OverlayCompose.createComposeView(hostContext, dialogOwner).also {
        pendingView = it
      }.apply {
        isFocusable = true
        isFocusableInTouchMode = true
        setContent {
          androidx.compose.runtime.CompositionLocalProvider(
            *(listOfNotNull(
              appDeps?.let { com.slideindex.app.ui.compose.LocalAppDependencies provides it }
            ).toTypedArray())
          ) {
            OverlayAwareModuleTheme {
                var picked by remember { mutableStateOf(false) }
                WidgetPickerOverlayRoot(
                onAnimatedDismissReady = { handler -> requestAnimatedDismiss = handler },
                onDismissRequest = {
                  if (!picked) {
                    WidgetPickerTrampoline.deliverCancel()
                  }
                  dismiss()
                },
                onWidgetSelected = { entry ->
                  picked = true
                  WidgetPickerTrampoline.startBindFlow(hostContext, entry.provider.provider)
                  mainHandler.post {
                    requestAnimatedDismiss?.invoke() ?: dismiss()
                  }
                }
                )
            }
          }
        }
      }

      val params = OverlayPanelLayoutParams.fullScreenOverlay(
        context = hostContext,
        focusable = true
      )

      val added = runCatching { wm.addView(view, params) }
        .onFailure { Log.e(TAG, "addView failed", it) }
        .isSuccess
      if (!added) {
        abortPickerShow(wm, view, dialogOwner)
        WidgetPickerTrampoline.deliverCancel()
        return false
      }

      windowManager = wm
      composeView = view
      owner = dialogOwner
      layoutParams = params
      appContext = hostContext.applicationContext
      backHandler = OverlayViewBackHandler(view, ::handlePanelBack).also { it.attach() }
      OverlayPanelSystemGestureExclusion.attach(view, excludeLeftBackEdge = false)
      if (FloatBallOverlay.isShowing) {
        FloatBallOverlay.notifyPanelAttachedAboveChrome()
      }
      OverlaySceneController.onContentPanelShown()
      screenOffDismissReceiver.register(hostContext)
      view.requestFocus()
      view.post { view.requestFocus() }
      true
    } catch (t: Throwable) {
      Log.e(TAG, "show failed", t)
      abortPickerShow(wm, pendingView, dialogOwner)
      composeView = null
      windowManager = null
      owner = null
      layoutParams = null
      appContext = null
      backHandler = null
      requestAnimatedDismiss = null
      WidgetPickerTrampoline.deliverCancel()
      false
    }
  }

  private fun forceCleanupStalePicker() {
    val view = composeView
    val wm = windowManager
    val dialogOwner = owner
    composeView = null
    windowManager = null
    layoutParams = null
    owner = null
    appContext = null
    backHandler?.detach()
    backHandler = null
    requestAnimatedDismiss = null
    screenOffDismissReceiver.unregister()
    if (view != null && wm != null) {
      runCatching { wm.removeView(view) }
      OverlayCompose.teardownOverlayCompose(view, dialogOwner)
    } else {
      OverlayCompose.teardownOverlayCompose(view, dialogOwner)
    }
    dismissing = false
  }

  private fun abortPickerShow(
    wm: WindowManager?,
    view: android.view.View?,
    dialogOwner: OverlayComposeOwner?
  ) {
    screenOffDismissReceiver.unregister()
    backHandler?.detach()
    backHandler = null
    requestAnimatedDismiss = null
    if (view != null && wm != null) {
      runCatching { wm.removeView(view) }
      if (view is ComposeView) {
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
      } else {
        dialogOwner?.destroy()
      }
    } else {
      OverlayCompose.teardownOverlayCompose(view as? ComposeView, dialogOwner)
    }
    WidgetPopupOverlayWindow.resumeAfterPickerOverlay()
  }

  /**
   * §0.16.25：**外部"返回"请求**（手势里的"返回"动作）入口。
   *
   * ⚠️ **必须走本窗自己的返回漏斗** `OverlayViewBackHandler.dispatchBack()`，不能在调用侧直接
   * [dismissFromBack] —— 那样会绕过**键盘优先（§0.16.7）**：搜索框弹着键盘时这一次返回应该只收键盘、
   * 页面留着，直接关页面就是把规则抄漏了（真机回归：添加小组件页里点搜索框弹键盘后手势返回，
   * 页面被整个关掉）。
   *
   * handler 还没建起来（窗口正在建/正在拆）才退回 [dismissFromBack]：宁可关掉，也不要返回没反应。
   *
   * @return true = 这次返回由本窗消费，调用方不要再落 `GLOBAL_ACTION_BACK`
   */
  fun requestBack(): Boolean {
    if (!isShowing) return false
    val handler = backHandler
    if (handler != null) {
      handler.dispatchBack()
      return true
    }
    dismissFromBack()
    return true
  }

  fun dismissFromBack() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { dismissFromBack() }
      return
    }
    WidgetPickerTrampoline.deliverCancel()
    dismiss()
  }

  fun dismiss() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { dismiss() }
      return
    }
    if (dismissing) return
    dismissing = true
    backHandler?.detach()
    backHandler = null
    requestAnimatedDismiss = null
    screenOffDismissReceiver.unregister()
    val view = composeView
    val wm = windowManager
    val dialogOwner = owner
    composeView = null
    windowManager = null
    layoutParams = null
    owner = null
    appContext = null
    if (view != null && wm != null) {
      runCatching { wm.removeView(view) }
      OverlayCompose.teardownOverlayCompose(view, dialogOwner)
      dismissing = false
      OverlaySceneController.onContentPanelHidden()
      WidgetPopupOverlayWindow.resumeAfterPickerOverlay()
    } else {
      OverlayCompose.teardownOverlayCompose(view, dialogOwner)
      dismissing = false
      OverlaySceneController.onContentPanelHidden()
      WidgetPopupOverlayWindow.resumeAfterPickerOverlay()
    }
  }
}

private const val PICKER_ANIM_IN_MS = 280
private const val PICKER_ANIM_OUT_MS = 240

@Composable
fun WidgetPickerOverlayRoot(
  onAnimatedDismissReady: ((() -> Unit)?) -> Unit,
  onDismissRequest: () -> Unit,
  onWidgetSelected: (com.slideindex.app.widget.WidgetProviderEntry) -> Unit
) {
  var visible by remember { mutableStateOf(false) }
  var hasOpened by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    visible = true
    hasOpened = true
  }

  val dismissAnimated = remember { { visible = false } }

  DisposableEffect(Unit) {
    onAnimatedDismissReady(dismissAnimated)
    onDispose { onAnimatedDismissReady(null) }
  }

  val scrimEnterSpec = tween<Float>(PICKER_ANIM_IN_MS, easing = LinearOutSlowInEasing)
  val scrimExitSpec = tween<Float>(PICKER_ANIM_OUT_MS, easing = FastOutLinearInEasing)
  val panelEnterSpec = tween<IntOffset>(PICKER_ANIM_IN_MS, easing = LinearOutSlowInEasing)
  val panelExitSpec = tween<IntOffset>(PICKER_ANIM_OUT_MS, easing = FastOutLinearInEasing)

  LaunchedEffect(visible, hasOpened) {
    if (!visible && hasOpened) {
      delay(PICKER_ANIM_OUT_MS.toLong())
      onDismissRequest()
    }
  }

  val dismiss = dismissAnimated

  Box(modifier = Modifier.fillMaxSize()) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn(scrimEnterSpec),
      exit = fadeOut(scrimExitSpec),
      modifier = Modifier.fillMaxSize()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.5f))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = dismiss
          )
      )
    }

    BoxWithConstraints(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.BottomCenter
    ) {
      val sheetHeight = maxHeight * 0.85f
      val slideOffset = with(LocalDensity.current) { sheetHeight.roundToPx() }

      AnimatedVisibility(
        visible = visible,
        enter = fadeIn(scrimEnterSpec) + slideInVertically(panelEnterSpec) { slideOffset },
        exit = fadeOut(scrimExitSpec) + slideOutVertically(panelExitSpec) { slideOffset },
        modifier = Modifier.fillMaxWidth()
      ) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = {}
            ),
          shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
          color = Color(0xFFF7F7F7)
        ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val (initialAppPkgs, initialShortcutKeys, initialActionKeys) = remember {
          WidgetPickerTrampoline.getCurrentPageConfiguredItems()
        }
        WidgetPickerScreen(
          onBack = dismiss,
          onWidgetSelected = onWidgetSelected,
          configuredAppPackages = initialAppPkgs,
          configuredShortcutKeys = initialShortcutKeys,
          configuredActionKeys = initialActionKeys,
          onToggleApp = { app, _ ->
            WidgetPickerTrampoline.toggleApp(app.packageName, app.className, app.appLabel, app.cloneUserId)
          },
          onToggleShortcut = { sc, _ ->
            WidgetPickerTrampoline.toggleShortcut(sc.packageName, sc.shortcutId, sc.label, sc.intentUri)
          },
          onToggleAction = { action, _ ->
            val label = com.slideindex.app.ui.gesturepicker.gestureActionLabelText(context, action)
            WidgetPickerTrampoline.toggleAction(action, label)
          },
          onAppSelected = { app ->
            WidgetPickerTrampoline.toggleApp(app.packageName, app.className, app.appLabel, app.cloneUserId)
          },
          onShortcutSelected = { sc ->
            WidgetPickerTrampoline.toggleShortcut(sc.packageName, sc.shortcutId, sc.label, sc.intentUri)
          },
          onActionSelected = { action ->
            val label = com.slideindex.app.ui.gesturepicker.gestureActionLabelText(context, action)
            WidgetPickerTrampoline.toggleAction(action, label)
          },
          launchCreateShortcut = { createHost ->
            val hostContext = SlideIndexAccessibilityService.overlayHostContext() ?: context.applicationContext
            if (hostContext != null) {
              com.slideindex.app.service.CreateShortcutTrampoline.launch(
                context = hostContext,
                host = createHost,
                onPrepare = {
                  dismiss()
                },
                onResult = { created ->
                  if (created != null) {
                    // 和快速启动器一致：创建结果回传时先把自定义图标抓下来存好，
                    // 否则面板 item 只有包名/shortcutId，解析时只能退回 App 图标。
                    val capturedIcon = com.slideindex.app.activity.ShortcutIconCapture
                      .captureFromIntent(
                        hostContext,
                        created.iconProviderIntent ?: created.shortcutIntent,
                      )
                    val overrideKey = created.intentUri
                      ?.takeIf { it.isNotBlank() }
                      ?.let { "intent:$it" }
                      ?: created.componentFlat?.takeIf { it.isNotBlank() }
                    com.slideindex.app.activity.ShortcutIconOverrideStore.put(
                      hostContext,
                      overrideKey,
                      capturedIcon,
                    )
                    WidgetPickerTrampoline.deliverShortcutSuccess(
                      packageName = created.hostPackageName,
                      shortcutId = "created_${created.label.hashCode()}",
                      label = created.label,
                      intentUri = created.intentUri.orEmpty()
                    )
                  } else {
                    WidgetPickerTrampoline.deliverCancel()
                  }
                }
              )
            }
          },
          // Overlay ComposeView 没有 OnBackPressedDispatcherOwner；返回由 OverlayViewBackHandler 处理。
          enableBackHandler = false,
          overlayMode = true
        )
        }
      }
    }
  }
}
