package com.slideindex.app.overlay.quickmenu


import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.OverlayFullScreenPanelHost
import com.slideindex.app.overlay.OverlayPanelLayoutParams
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.filebrowser.FileBrowserOverlayWindow
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 「快捷菜单」浮层面板：顶层 [android.view.WindowManager] 窗口 + Compose 内容。
 *
 * 与 `OhoQuickToolsOverlayWindow`（「快速工具（悬浮窗）」）是并列的两个面板，共用
 * [OverlayFullScreenPanelHost] 的基建（窗口类型、Compose owner、熄屏自收、系统摘窗复位）。
 *
 * ## 为什么状态独立于窗口（[Session]）
 *
 * 拉起相机 / 选图 / 截屏授权这类**外部 Activity** 时，浮层必须先收起来——否则它盖在外部
 * Activity 之上（无障碍覆盖层永远在最上层），相机预览根本看不见；截屏时还会把面板自己截进去。
 *
 * 但仅"隐藏"不够：部分机型（Flyme 等，见宿主里 §0.16.13 的记录）在切换前台 Activity 时会直接
 * 把我们的覆盖层窗口摘掉。如果面板状态挂在窗口上，摘掉的那一刻状态就没了，于是外部 Activity
 * 返回后**"扫到了、页面退了、却什么都没发生"**。
 *
 * 因此状态放在 [Session] 里：窗口被摘掉只丢引用，[resumeFromExternalActivity] 会用同一个
 * Session 把窗口重建出来，页面位置与扫到的原文原样恢复。
 */
object QuickMenuOverlayWindow {

    private const val TAG = "QuickMenuOverlay"

    /** 关面板时留给 Compose 退场动画的时间，之后拆窗口。 */
    private const val DISMISS_ANIMATION_MS = 200L

    /** 面板状态；生命周期独立于窗口。 */
    private class Session {
        val visible: MutableState<Boolean> = mutableStateOf(false)
        val page: MutableState<QuickMenuTool?> = mutableStateOf(null)
        val query: MutableState<String> = mutableStateOf("")
        val recents: MutableState<List<QuickMenuTool>> = mutableStateOf(emptyList())

        /** 识码页的待解析原文。外部 Activity 期间必须由这里承载，否则扫码结果会丢。 */
        val scanContent: MutableState<String> = mutableStateOf("")

        /** 识码页的候选列表（含归一化定位点）。只有一个码时为空列表。 */
        val scanResults: MutableState<List<BarcodeScanResult>> = mutableStateOf(emptyList())

        /**
         * 被扫描的那张图（用来在图上画标记）。
         *
         * **刻意不调 `Bitmap.recycle()`**：API 26+ 起位图像素由 GC 托管，丢引用就会释放；
         * 显式回收则有"界面还在画它"的时序风险，会抛 using a recycled bitmap。
         */
        val scanImage: MutableState<Bitmap?> = mutableStateOf(null)

        /**
         * 「屏幕取色」的状态（冻结帧、最近取到的色、历史、错误文案）。
         *
         * 和其它工具页不同，取色要**隐藏本窗口去截图、再把冻结帧铺满整屏**。这段过程里窗口可能
         * 被系统摘掉重建（见类注释里的 Flyme 记录），状态放页面里会跟着丢；放在 Session 里，
         * 恢复后仍能接着显示冻结帧与历史。
         */
        val colorPicker = QuickMenuColorPickerState()

        var settings: AppSettings = AppSettings()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 「最近使用」的落盘是异步的：状态先乐观更新，再后台写 DataStore。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val host = OverlayFullScreenPanelHost(
        tag = TAG,
        layoutParamsFactory = { context, focusable ->
            // 满屏窗：内容自己控制留白与位置；搜索框需要窗口可获得焦点，否则输入法打不进字。
            OverlayPanelLayoutParams.fullScreenOverlay(
                context = context,
                focusable = focusable,
                touchable = true,
                // ⚠️ 必须显式传"内容面板"类型。host 的默认值是 overlayWindowType()，在无障碍服务
                // 做宿主时那是 TYPE_ACCESSIBILITY_OVERLAY —— 会把悬浮球/边缘触钮**压到面板下面**
                // （用户反馈的现象）。仓库里所有内容面板都显式传这个类型，见
                // OverlayWindowTypes.contentPanelWindowType 的注释；顺带也绕开 Flyme 对无障碍层
                // 强制 80% alpha 的坑。
                windowType = OverlayWindowTypes.contentPanelWindowType(context),
            )
        },
        onScreenOff = { dismiss() },
        // 只记日志：**不清 Session**。外部 Activity 期间被系统摘窗后要靠它恢复。
        onViewDetached = {
            Log.w(TAG, "panel window detached by system; session kept for resume")
            detachBackHandler()
        },
    )

    private var session: Session? = null

    /** 返回手势/返回键处理；用仓库既有的 [OverlayViewBackHandler]（同时覆盖 OnBackInvokedCallback 与 BACK 键注入两条路）。 */
    private var backHandler: OverlayViewBackHandler? = null

    /** 是否正因外部 Activity 而处于挂起状态（挂起期间不响应恢复以外的操作）。 */
    private var suspendedForExternal = false

    val isShowing: Boolean get() = host.isAttached && session?.visible?.value == true

    /**
     * 打开面板；已打开时直接返回 true（幂等，避免绑了手势连点两下叠出两个窗口）。
     *
     * @param side / [anchorRawY] 仅为了与其它面板保持一致的调用形状（本面板满屏展示，不使用锚点）。
     */
    @Suppress("UNUSED_PARAMETER")
    fun show(
        context: Context,
        settings: AppSettings,
        side: PanelSide? = null,
        anchorRawY: Float? = null,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = CountDownLatch(1)
            mainHandler.post {
                result = show(context, settings, side, anchorRawY)
                latch.countDown()
            }
            runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
            return result
        }

        if (isShowing) return true

        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }
        val hostContext = com.slideindex.app.overlay.panels.PanelRuntimeHooks.overlayHostContext
            ?.invoke() ?: run {
            Log.w(TAG, "show: accessibility service not connected")
            return false
        }

        val current = session ?: Session().also { session = it }
        current.settings = settings
        // 刻意**不重置** current.page：挂起（相机/选图/分享）期间保留所在工具页，用户回来还在原处。
        // 想让面板回到工具列表，用 X / 点罩层关闭（dismiss 会丢弃 Session）。
        suspendedForExternal = false

        QuickMenuTimerRuntime.attach(hostContext)
        QuickMenuPreferences.attach(hostContext)

        if (!attachWindowIfNeeded(hostContext)) return false

        current.visible.value = true
        host.setViewVisible(true)
        host.setTouchable(true)
        host.setInputActive(true)

        scope.launch {
            current.recents.value = QuickMenuRecentsStore.read(hostContext.applicationContext)
        }
        return true
    }

    /**
     * 因即将拉起外部 Activity 而**挂起**面板：隐藏窗口但**不销毁 Session**。
     *
     * 同步执行（已在主线程时直接跑），这样调用方可以紧接着 `startActivity`，
     * 不会出现"外部 Activity 先起来、面板还没收起"的中间帧。
     */
    fun suspendForExternalActivity() {
        runOnMainSync {
            val current = session ?: return@runOnMainSync
            if (!host.isAttached) return@runOnMainSync
            suspendedForExternal = true
            current.visible.value = false
            host.setInputActive(false)
            host.setTouchable(false)
            host.setViewVisible(false)
        }
    }

    /**
     * 外部 Activity 返回后恢复面板。
     *
     * 窗口若在期间被系统摘掉，会用**同一个 Session** 重建，因此所在工具页与已扫到的原文都不会丢。
     */
    fun resumeFromExternalActivity() {
        runOnMainSync {
            val current = session ?: return@runOnMainSync
            suspendedForExternal = false
            val hostContext = com.slideindex.app.overlay.panels.PanelRuntimeHooks.overlayHostContext
                ?.invoke()
            if (hostContext == null) {
                Log.w(TAG, "resume: accessibility service not connected")
                return@runOnMainSync
            }
            if (!attachWindowIfNeeded(hostContext)) return@runOnMainSync

            QuickMenuTimerRuntime.attach(hostContext)
            QuickMenuPreferences.attach(hostContext)

            current.visible.value = true
            host.setViewVisible(true)
            host.setTouchable(true)
            host.setInputActive(true)
        }
    }

    /**
     * 分享等"发出就不一定回来"的外部动作：隐藏面板，**不自动恢复**。
     *
     * 系统分享面板没有可靠的回调（用户可能选完 App 直接留在那边），自动恢复只会在错误时机把
     * 面板糊回屏幕上层。所以这里只收起；Session 保留，用户下次唤出面板仍在原来的工具页。
     */
    fun hideForExternalShare() {
        runOnMainSync {
            val current = session ?: return@runOnMainSync
            if (!host.isAttached) return@runOnMainSync
            suspendedForExternal = true
            current.visible.value = false
            host.setInputActive(false)
            host.setTouchable(false)
            host.setViewVisible(false)
        }
    }

    /**
     * 关闭面板：先播退场动画，再拆窗口。
     *
     * **刻意不丢弃 Session**：它承载"当前在哪一页 / 搜索词 / 最近使用 / 识码结果与定格图"。
     * 动作列表里那条是「打开/收起快捷菜单」（同一个手势切来切去），收起就等于清空的话，
     * 每次打开都回到根页、刚扫到的东西也没了（真机反馈）。
     */
    fun dismiss() {
        mainHandler.post {
            if (!host.isAttached) {
                session = null
                suspendedForExternal = false
                return@post
            }
            session?.visible?.value = false
            // 全屏取色层带着一帧整屏位图（~10MB）：关面板就丢掉，别让"收起再打开"还停在一张旧截图上。
            session?.colorPicker?.picking = null
            host.setInputActive(false)
            host.setTouchable(false)
            mainHandler.postDelayed({
                detachBackHandler()
                host.destroy()
                // Session 留着：下次 show 会复用，于是"收起再打开"仍停在原页、原搜索词、原识码结果。
                suspendedForExternal = false
            }, DISMISS_ANIMATION_MS)
        }
    }

    /** 窗口不在就（重新）挂上去，内容绑定到当前 [Session]。 */
    private fun attachWindowIfNeeded(hostContext: Context): Boolean {
        if (host.isAttached) return true
        val current = session ?: return false
        val owner = host.ensureWindow(hostContext, focusable = true) {
            OverlayAwareModuleTheme(current.settings) {
                QuickMenuPanel(
                    visible = current.visible.value,
                    page = current.page.value,
                    query = current.query.value,
                    recents = current.recents.value,
                    scanContent = current.scanContent.value,
                    scanResults = current.scanResults.value,
                    scanImage = current.scanImage.value,
                    colorPicker = current.colorPicker,
                    onQueryChange = { current.query.value = it },
                    onScanContentChange = { current.scanContent.value = it },
                    onScanResultsChange = { current.scanResults.value = it },
                    onScanImageChange = { current.scanImage.value = it },
                    onOpenTool = { tool ->
                        // **预置入口**（前台活动探测、冰箱、文件浏览器…）：点一下就直接打开对应面板，
                        // 并收起本菜单。刻意**不切页**：这类项没有自己的界面，切过去只会留一个空白子页面。
                        // 打开失败（无障碍宿主刚断等）时也留在列表页，而不是进入空白子页。
                        if (com.slideindex.app.overlay.quickmenu.QuickMenuHostAccess.host
                    ?.runEntry(hostContext, tool, current.settings) == true
                ) {
                            hideForExternalShare()
                        } else if (!tool.opensStandalonePanel) {
                            current.page.value = tool
                            // 乐观更新：先用本地顺序刷 UI，再异步落盘（写失败也不影响本次使用）。
                            current.recents.value = QuickMenuRecentsCodec.record(current.recents.value, tool)
                            scope.launch {
                                val saved = QuickMenuRecentsStore.record(
                                    hostContext.applicationContext,
                                    tool,
                                )
                                if (saved.isNotEmpty()) current.recents.value = saved
                            }
                        }
                    },
                    onOpenFileBrowser = { initialPath ->
                        hideForExternalShare()
                        FileBrowserOverlayWindow.show(
                            context = hostContext,
                            settings = current.settings,
                            initialPath = initialPath,
                        )
                    },
                    onBack = { current.page.value = null },
                    onDismiss = { dismiss() },
                )
            }
        }
        if (owner == null) return false
        attachBackHandler()
        return true
    }

    /**
     * 返回键/返回手势：先退出全屏取色，再退回工具列表；已经在列表上则关闭面板。
     *
     * 复用 [OverlayViewBackHandler] 而不是自己监听按键——它已经把"系统走 OnBackInvokedCallback
     * 还是走兼容 BACK 键注入"这个坑处理掉了（见该类的 §0.16.25 注释）。
     */
    private fun handleBack() {
        val current = session ?: return
        // 全屏取色是"盖在工具页上的一层"：返回只退出这一层，回到取色结果页，不连菜单一起关。
        if (current.colorPicker.picking != null) {
            current.colorPicker.picking = null
            return
        }
        if (current.page.value != null) {
            current.page.value = null
        } else {
            dismiss()
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
