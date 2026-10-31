package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.OverlayFullScreenPanelHost
import com.slideindex.app.overlay.OverlayPanelLayoutParams
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.searchpanel.FilePermissionTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import rikka.shizuku.Shizuku

/**
 * 文件浏览器悬浮窗宿主。
 *
 * 与 QuickMenu 同族：满屏 TYPE_APPLICATION_OVERLAY 窗口 + 居中玻璃卡片；Session 独立于
 * 窗口，系统摘窗/关闭面板后仍保留路径、排序与隐藏开关。目录读取、权限判断全部在
 * [FileBrowserController]；这里只处理窗口、返回层级与外部授权页的挂起/恢复。
 */
internal object FileBrowserOverlayWindow {
    private const val TAG = "FileBrowserOverlay"
    private const val DISMISS_ANIMATION_MS = 180L
    /** 起拖后若收不到 [android.view.DragEvent.ACTION_DRAG_ENDED] 的兜底恢复 */
    private const val FILE_DRAG_RESTORE_TIMEOUT_MS = 10_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val host = OverlayFullScreenPanelHost(
        tag = TAG,
        layoutParamsFactory = { context, focusable ->
            OverlayPanelLayoutParams.fullScreenOverlay(
                context = context,
                focusable = focusable,
                touchable = true,
                windowType = OverlayWindowTypes.contentPanelWindowType(context),
                title = "XG:file-browser",
            )
        },
        onScreenOff = { dismiss() },
        onViewDetached = { detachBackHandler() },
    )

    private class Session(
        context: Context,
        scope: CoroutineScope,
    ) {
        val visible: MutableState<Boolean> = mutableStateOf(false)
        val controller = FileBrowserController(context.applicationContext, scope)
        var settings: AppSettings = AppSettings()
    }

    private var session: Session? = null
    private var appContext: Context? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var dismissToken = 0
    private var externalRequestToken = 0
    private var fileDragSuspended = false
    private var dragRestoreWatchdog: Runnable? = null
    private var shizukuListenerRegistered = false

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            mainHandler.post {
                if (!isShowing) return@post
                session?.controller?.refreshAccessAndReload()
            }
        }
    }

    val isShowing: Boolean
        get() = isUserVisible()

    fun isServiceReady(): Boolean = isShellAttached()

    fun show(
        context: Context,
        settings: AppSettings,
        initialPath: String? = null,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = CountDownLatch(1)
            mainHandler.post {
                result = show(context, settings, initialPath)
                latch.countDown()
            }
            runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
            return result
        }

        reconcileStaleShell()

        if (isUserVisible()) {
            if (!initialPath.isNullOrBlank()) {
                session?.controller?.navigateTo(initialPath)
            }
            return true
        }

        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: run {
            Log.w(TAG, "show: accessibility service not connected")
            return false
        }

        dismissToken++
        appContext = context.applicationContext
        val current = session ?: Session(context, scope).also { session = it }
        current.settings = settings

        if (!isShellAttached()) {
            if (!ensureWindowAttached(hostContext, current)) return false
            attachBackHandler()
            registerShizukuListener()
        }

        activatePanel(current, initialPath)

        host.composeView?.post {
            val view = host.composeView ?: return@post
            if (view.isAttachedToWindow && view.width > 0) return@post
            Log.w(TAG, "show: shell not attached after first frame, rebuilding")
            tearDownHostShell(resetVisible = true)
            if (!ensureWindowAttached(hostContext, current)) return@post
            attachBackHandler()
            registerShizukuListener()
            activatePanel(current, initialPath)
        }
        return host.composeView != null
    }

    /** 系统文件拖放会话开始：藏窗但保留 [Session]（与授权页挂起同类）。 */
    fun suspendForFileDrag() {
        mainHandler.post { suspendForFileDragOnMain() }
    }

    /** 拖放结束或超时兜底：恢复浮窗。 */
    fun resumeFromFileDrag() {
        mainHandler.post { resumeFromFileDragOnMain() }
    }

    fun dismiss() {
        val token = ++dismissToken
        externalRequestToken++
        cancelDragRestoreWatchdog()
        fileDragSuspended = false
        mainHandler.post {
            if (token != dismissToken) return@post
            val current = session ?: return@post
            current.visible.value = false
            if (!host.isAttached) {
                detachBackHandler()
                unregisterShizukuListener()
                current.controller.cancel()
                return@post
            }
            host.setInputActive(false)
            host.setTouchable(false)
            mainHandler.postDelayed({
                if (dismissToken != token) return@postDelayed
                detachBackHandler()
                unregisterShizukuListener()
                current.controller.cancel()
                host.destroy()
            }, DISMISS_ANIMATION_MS)
        }
    }

    private fun handleBack() {
        val current = session ?: return
        val state = current.controller.state
        if (state.mediaSelectedIds.isNotEmpty()) {
            current.controller.clearMediaSelection()
            return
        }
        if (state.mediaPreview != null) {
            current.controller.hideMediaPreview()
            return
        }
        if (state.details != null) {
            current.controller.hideDetails()
            return
        }
        // 勾选态是「临时模式」：返回先退出多选，而不是直接回上级目录（否则选中项会被导航清空）。
        if (state.selectionMode || state.selectedPaths.isNotEmpty()) {
            current.controller.exitSelection()
            return
        }
        if (state.query.isNotEmpty()) {
            current.controller.setQuery("")
            return
        }
        val parent = FileBrowserPaths.parent(state.path, current.controller.internalRoot)
        val tab = state.tabs.firstOrNull { it.id == state.activeTabId }
        val internal = FileBrowserPaths.normalize(
            current.controller.internalRoot,
            current.controller.internalRoot,
        )
        // 内部存储是用户心智里的顶层；没有历史时返回应直接退出，不能再走 parent 回 /。
        if (tab?.backStack.isNullOrEmpty() && state.path == internal) {
            dismiss()
            return
        }
        if (parent == null && tab?.backStack.isNullOrEmpty()) {
            dismiss()
        } else {
            current.controller.goBack()
        }
    }

    private fun attachBackHandler() {
        val view = host.composeView ?: return
        backHandler?.detach()
        backHandler = OverlayViewBackHandler(view, ::handleBack).also { it.attach() }
    }

    private fun detachBackHandler() {
        backHandler?.detach()
        backHandler = null
    }

    /**
     * 拉起系统设置页前必须把浮窗藏掉：无障碍覆盖层会在设置页之上，用户看不到授权按钮。
     * 隐藏但**不销毁**宿主，回调里用同一个 Session 恢复。
     */
    private fun suspendForExternalActivity() {
        runOnMainSync {
            cancelDragRestoreWatchdog()
            fileDragSuspended = false
            val current = session ?: return@runOnMainSync
            if (!host.isAttached) return@runOnMainSync
            hidePanelShell(current)
        }
    }

    private fun suspendForFileDragOnMain() {
        cancelDragRestoreWatchdog()
        val current = session ?: return
        if (!host.isAttached || !isUserVisible()) return
        fileDragSuspended = true
        hidePanelShell(current)
        val runnable = Runnable { resumeFromFileDragOnMain(force = true) }
        dragRestoreWatchdog = runnable
        mainHandler.postDelayed(runnable, FILE_DRAG_RESTORE_TIMEOUT_MS)
    }

    private fun resumeFromFileDragOnMain(force: Boolean = false) {
        cancelDragRestoreWatchdog()
        if (!fileDragSuspended && !force) return
        fileDragSuspended = false
        val current = session ?: return
        val hostContext = OverlayDependencyAccess.overlayHostContext()
        if (hostContext == null) {
            Log.w(TAG, "resumeFromFileDrag: accessibility service not connected")
            return
        }
        reconcileStaleShell()
        if (!isShellAttached()) {
            if (!ensureWindowAttached(hostContext, current)) return
            attachBackHandler()
            registerShizukuListener()
        }
        restorePanelInteractivity(current)
    }

    private fun cancelDragRestoreWatchdog() {
        dragRestoreWatchdog?.let(mainHandler::removeCallbacks)
        dragRestoreWatchdog = null
    }

    private fun hidePanelShell(current: Session) {
        current.visible.value = false
        host.setInputActive(false)
        host.setTouchable(false)
        host.setViewVisible(false)
    }

    private fun restorePanelInteractivity(current: Session) {
        current.visible.value = true
        host.setViewVisible(true)
        host.setTouchable(true)
        host.setInputActive(true)
    }

    private fun resumeFromExternalActivity() {
        runOnMainSync {
            val current = session ?: return@runOnMainSync
            val hostContext = OverlayDependencyAccess.overlayHostContext()
            if (hostContext == null) {
                Log.w(TAG, "resume: accessibility service not connected")
                return@runOnMainSync
            }
            reconcileStaleShell()
            if (!isShellAttached()) {
                if (!ensureWindowAttached(hostContext, current)) return@runOnMainSync
                attachBackHandler()
                registerShizukuListener()
            }
            activatePanel(current, initialPath = null)
        }
    }

    /** 用户眼里「面板正开着」：Session 要可见，且壳子真在 WindowManager 上。 */
    private fun isUserVisible(): Boolean {
        if (session?.visible?.value != true) return false
        val view = host.composeView ?: return false
        if (!view.isAttachedToWindow) return false
        return view.visibility == View.VISIBLE
    }

    private fun isShellAttached(): Boolean =
        host.composeView?.isAttachedToWindow == true

    /** 内存里还留着 ComposeView，但已被系统摘掉 → 先拆壳再建，避免 isShowing 早退死循环。 */
    private fun reconcileStaleShell() {
        val view = host.composeView ?: return
        if (view.isAttachedToWindow) return
        Log.w(TAG, "show: stale shell not attached, rebuilding window")
        tearDownHostShell(resetVisible = true)
    }

    private fun tearDownHostShell(resetVisible: Boolean) {
        detachBackHandler()
        unregisterShizukuListener()
        if (resetVisible) session?.visible?.value = false
        if (host.isAttached) host.destroy()
    }

    private fun activatePanel(current: Session, initialPath: String?) {
        restorePanelInteractivity(current)
        if (initialPath.isNullOrBlank()) {
            current.controller.onShow()
        } else {
            current.controller.navigateTo(initialPath)
        }
    }

    private fun ensureWindowAttached(hostContext: Context, current: Session): Boolean =
        host.ensureWindow(hostContext, focusable = true) {
            OverlayAwareModuleTheme(current.settings) {
                FileBrowserPanel(
                    visible = current.visible.value,
                    controller = current.controller,
                    onDismiss = { dismiss() },
                    onRequestAllFiles = { requestAllFilesAccess() },
                    onRequestShizuku = { requestShizukuAccess() },
                    onRequestMediaPermission = { requestMediaPermissions() },
                )
            }
        } != null

    private fun requestAllFilesAccess() {
        val context = appContext ?: return
        if (Environment.isExternalStorageManager()) {
            session?.controller?.refreshAccessAndReload()
            return
        }
        suspendForExternalActivity()
        val token = ++externalRequestToken
        val launched = runCatching {
            FilePermissionTrampolineActivity.launch(context) { _ ->
                mainHandler.post {
                    if (token != externalRequestToken) return@post
                    resumeFromExternalActivity()
                }
            }
        }.isSuccess
        if (!launched) {
            resumeFromExternalActivity()
        }
    }

    private fun requestShizukuAccess() {
        val context = appContext ?: return
        if (TaskManagerUtil.hasShizukuPermission()) {
            session?.controller?.refreshAccessAndReload()
            return
        }
        TaskManagerUtil.requestPermission(context)
    }

    private fun requestMediaPermissions() {
        val context = appContext ?: return
        val category = session?.controller?.state?.mediaCategory ?: return
        val permissions = FileBrowserMediaSource.requiredPermissions(category)
        if (permissions.isEmpty()) {
            requestAllFilesAccess()
            return
        }
        if (FileBrowserMediaSource.hasPermission(context, category)) {
            session?.controller?.refreshAccessAndReload()
            return
        }
        val launched = runCatching {
            FileBrowserMediaPermissionActivity.launch(context, permissions) {
                mainHandler.post {
                    session?.controller?.refreshAccessAndReload()
                }
            }
        }.isSuccess
        if (!launched) {
            requestAllFilesAccess()
        }
    }

    private fun registerShizukuListener() {
        if (shizukuListenerRegistered) return
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuListener) }
        shizukuListenerRegistered = true
    }

    private fun unregisterShizukuListener() {
        if (!shizukuListenerRegistered) return
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
        shizukuListenerRegistered = false
    }

    private fun runOnMainSync(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        mainHandler.post {
            runCatching { block() }
            latch.countDown()
        }
        runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
    }
}
