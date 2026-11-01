package com.slideindex.app.overlay.browser

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tab
import com.slideindex.app.overlay.ScreenPinManager
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.service.WebViewFileChooserBridge

/**
 * 浮层浏览器：单窗口 + 多标签 + 自绘 chrome。
 *
 * 布局：顶部满宽 44dp 窄条（域名 + 关闭），网页内容从它下方开始；
 * 底部胶囊工具条浮在网页之上，向下滚动自动隐藏。
 *
 * 调试入口：`adb shell am broadcast -a com.slideindex.app.BROWSER --es url https://example.com`
 */
object FloatingBrowserPanel {

    private const val PREFS = "xg_browser"

    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var composeView: ComposeView? = null
    private var owner: BrowserComposeOwner? = null
    private var webHost: FrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var fullscreenView: View? = null

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId = 0L
    private val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId }
    private var nextTabId = 1L
    private var cardWidthPx = 0
    private var cardHeightPx = 0

    private var tick by mutableIntStateOf(0)
    private val tabLabels = mutableStateListOf<String>()
    private var addressText by mutableStateOf("")
    private var addressEditing by mutableStateOf(false)
    private var tabsVisible by mutableStateOf(false)
    private var progress by mutableIntStateOf(0)
    private var currentUrl by mutableStateOf("")
    private var toolbarVisible by mutableStateOf(true)
    private var maximized by mutableStateOf(false)
    private var lastScrollY = 0
    private var scrollAccum = 0
    private var lastScrollAt = 0L

    private var selectedText by mutableStateOf("")
    private val selectionRects = mutableStateListOf<FloatArray>()
    private var selectionScale = 1f
    private var contextMenuTarget by mutableStateOf<ContextMenuTarget?>(null)

    sealed interface ContextMenuTarget {
        data class Link(val url: String) : ContextMenuTarget
        data class Image(val imageUrl: String) : ContextMenuTarget
    }

    val isShowing: Boolean
        get() = rootView != null

    fun show(context: Context, initialUrl: String?) {
        if (rootView != null) {
            initialUrl?.let { openUrl(it) }
            return
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        cardWidthPx = (metrics.widthPixels * 0.92f).toInt()
        cardHeightPx = (metrics.heightPixels * 0.78f).toInt()

        val root = FrameLayout(context)
        val host = FrameLayout(context).apply {
            setPadding(0, (46 * density).toInt(), 0, 0)
            clipToPadding = true
        }
        root.addView(host, FrameLayout.LayoutParams(-1, -1))
        webHost = host

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        maximized = prefs.getBoolean("max", false)

        val cornerRadius = 20f * density
        if (!maximized) {
            root.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                this.cornerRadius = cornerRadius
            }
            root.clipToOutline = true
            root.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
            root.elevation = 16f * density
        }

        val params = WindowManager.LayoutParams(
            if (maximized) WindowManager.LayoutParams.MATCH_PARENT else cardWidthPx,
            if (maximized) WindowManager.LayoutParams.MATCH_PARENT else cardHeightPx,
            OverlayWindowTypes.contentPanelWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            title = "XG:floating-browser"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
            if (!maximized) {
                width = prefs.getInt("w", cardWidthPx).takeIf { it > 0 } ?: cardWidthPx
                height = prefs.getInt("h", cardHeightPx).takeIf { it > 0 } ?: cardHeightPx
                x = prefs.getInt("x", 0)
                y = prefs.getInt("y", 0)
                val maxX = (metrics.widthPixels / 2 - width / 2).coerceAtLeast(0)
                val maxY = (metrics.heightPixels / 2 - height / 2).coerceAtLeast(0)
                x = x.coerceIn(-maxX, maxX)
                y = y.coerceIn(-maxY, maxY)
            }
        }
        windowParams = params

        val composeOwner = BrowserComposeOwner()
        root.setViewTreeLifecycleOwner(composeOwner)
        root.setViewTreeViewModelStoreOwner(composeOwner)
        root.setViewTreeSavedStateRegistryOwner(composeOwner)

        val composeHost = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeViewModelStoreOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setContent { BrowserChrome() }
        }
        root.addView(composeHost, FrameLayout.LayoutParams(-1, -1))
        composeView = composeHost
        owner = composeOwner

        // 原生拖动条：Compose 手势在浮层里容易被抢，顶栏拖动用 View 处理更可靠
        val dragBar = View(context).apply { isClickable = true }
        var lastRawX = 0f
        var lastRawY = 0f
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleMaximize()
                return true
            }
        })
        dragBar.setOnTouchListener { _, event ->
            val gestureHandled = gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    moveWindowBy(event.rawX - lastRawX, event.rawY - lastRawY)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    clampWindow()
                    true
                }

                else -> gestureHandled
            }
        }
        root.addView(
            dragBar,
            FrameLayout.LayoutParams(-1, (46 * density).toInt()).apply {
                rightMargin = (84 * density).toInt()
            },
        )

        rootView = root
        if (runCatching { wm.addView(root, params) }.isFailure) {
            rootView = null
            webHost = null
            windowParams = null
            return
        }

        if (tabs.isEmpty()) newTab(initialUrl ?: "https://www.bing.com") else initialUrl?.let { openUrl(it) }
        refreshUi()
    }

    fun requestBackIfShowing(): Boolean {
        if (!isShowing) return false
        return handleBack()
    }

    fun handleBack(): Boolean {
        if (!isShowing) return false
        // 1. 若长按上下文菜单正显示，先关菜单
        if (contextMenuTarget != null) {
            contextMenuTarget = null
            refreshUi()
            return true
        }
        // 2. 若选词胶囊正显示，先清选词
        if (selectedText.isNotEmpty()) {
            clearSelection()
            return true
        }
        // 3. 若地址栏在编辑中，退出编辑
        if (addressEditing) {
            addressEditing = false
            refreshUi()
            return true
        }
        // 4. 若标签页列表展开中，收起标签页列表
        if (tabsVisible) {
            tabsVisible = false
            refreshUi()
            return true
        }
        // 5. 若全屏播放视频中，退出全屏
        val fsView = fullscreenView
        if (fsView != null) {
            rootView?.let { runCatching { it.removeView(fsView) } }
            fullscreenView = null
            refreshUi()
            return true
        }
        // 6. 当前标签页在网页内部后退
        val active = activeTab
        if (active != null && active.canGoBack()) {
            active.goBack()
            return true
        }
        // 7. 当前标签页无法后退，但还有其它标签页：关闭当前标签页，回退到父标签页
        if (tabs.size > 1 && active != null) {
            closeTab(active.id)
            return true
        }
        // 8. 唯一标签页且网页到顶：关闭整个浮窗
        dismiss()
        return true
    }

    fun dismiss() {
        persistWindow()
        tabs.forEach { it.destroy() }
        tabs.clear()
        tabLabels.clear()
        webHost?.removeAllViews()
        webHost = null
        composeView = null
        owner?.destroy()
        owner = null
        fullscreenView = null
        windowParams = null
        rootView?.let { view -> windowManager?.let { wm -> runCatching { wm.removeViewImmediate(view) } } }
        rootView = null
        windowManager = null
        WebViewFileChooserBridge.cancelPending()
    }

    private fun persistWindow() {
        val params = windowParams ?: return
        val context = rootView?.context ?: return
        runCatching {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("max", maximized)
            if (!maximized) {
                editor.putInt("w", params.width)
                    .putInt("h", params.height)
                    .putInt("x", params.x)
                    .putInt("y", params.y)
            }
            editor.apply()
        }
    }

    private fun moveWindowBy(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val root = rootView ?: return
        val params = windowParams ?: return
        if (maximized) return
        params.x += dx.toInt()
        params.y += dy.toInt()
        clampToScreen(params, root)
        runCatching { wm.updateViewLayout(root, params) }
    }

    private fun clampWindow() {
        val wm = windowManager ?: return
        val root = rootView ?: return
        val params = windowParams ?: return
        if (maximized) return
        clampToScreen(params, root)
        runCatching { wm.updateViewLayout(root, params) }
    }

    private fun clampToScreen(params: WindowManager.LayoutParams, root: View) {
        val metrics = root.resources.displayMetrics
        val maxX = (metrics.widthPixels / 2 - params.width / 2).coerceAtLeast(0)
        val maxY = (metrics.heightPixels / 2 - params.height / 2).coerceAtLeast(0)
        params.x = params.x.coerceIn(-maxX, maxX)
        params.y = params.y.coerceIn(-maxY, maxY)
    }

    fun resizeWindowBy(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val root = rootView ?: return
        val params = windowParams ?: return
        if (maximized) return
        val density = root.resources.displayMetrics.density
        val metrics = root.resources.displayMetrics
        params.width = (params.width + dx.toInt()).coerceIn((320 * density).toInt(), metrics.widthPixels)
        params.height = (params.height + dy.toInt()).coerceIn((400 * density).toInt(), metrics.heightPixels)
        params.x = 0
        params.y = 0
        runCatching { wm.updateViewLayout(root, params) }
    }

    fun toggleMaximize() {
        val wm = windowManager ?: return
        val root = rootView ?: return
        val params = windowParams ?: return
        maximized = !maximized
        val density = root.resources.displayMetrics.density
        val metrics = root.resources.displayMetrics
        if (maximized) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.x = 0
            params.y = 0
            root.background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE) }
            root.outlineProvider = null
            root.clipToOutline = false
            root.elevation = 0f
        } else {
            params.width = cardWidthPx.coerceAtMost(metrics.widthPixels)
            params.height = cardHeightPx.coerceAtMost(metrics.heightPixels)
            params.x = 0
            params.y = 0
            val cornerRadius = 20f * density
            root.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                this.cornerRadius = cornerRadius
            }
            root.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
            root.clipToOutline = true
            root.elevation = 16f * density
        }
        runCatching { wm.updateViewLayout(root, params) }
        refreshUi()
    }

    // ---------- 标签与交互 ----------

    private fun createTabCallbacks(tabId: Long): BrowserTab.Callbacks = object : BrowserTab.Callbacks {
        override fun onUrlChanged(url: String, title: String) {
            syncTabLabel(tabId, title, url)
            if (activeTabId == tabId) {
                currentUrl = url
                if (!addressEditing) addressText = url
            }
            refreshUi()
        }

        override fun onProgress(progress: Int) {
            this@FloatingBrowserPanel.progress = progress
            refreshUi()
        }

        override fun onPageStarted(url: String) {
            if (activeTabId == tabId) {
                currentUrl = url
                if (!addressEditing) addressText = url
            }
            refreshUi()
        }

        override fun onPageFinished(url: String) = refreshUi()

        override fun onNewWindowRequested(url: String) {
            newTab(if (url == "about:blank") "https://www.bing.com" else url)
            refreshUi()
        }

        override fun onFileChooserOpened() {
            rootView?.visibility = View.GONE
        }

        override fun onFileChooserClosed() {
            rootView?.visibility = View.VISIBLE
        }

        override fun onCreateTabForWindow(): BrowserTab? = newTab(null, parentTabId = activeTabId)

        override fun onShowFullscreenView(view: View) {
            val root = rootView ?: return
            fullscreenView?.let { runCatching { root.removeView(it) } }
            fullscreenView = view
            root.addView(view, FrameLayout.LayoutParams(-1, -1))
        }

        override fun onHideFullscreenView() {
            val root = rootView ?: return
            fullscreenView?.let { runCatching { root.removeView(it) } }
            fullscreenView = null
        }

        override fun onPageScrolled(scrollY: Int) {
            val delta = scrollY - lastScrollY
            lastScrollY = scrollY

            // 网页在顶部（10px以内），底栏强制常驻显示
            if (scrollY <= 10) {
                if (!toolbarVisible) {
                    toolbarVisible = true
                    refreshUi()
                }
                return
            }

            // 一滑动立即开始隐藏/显示（过滤 2px 误触抖动）
            if (delta > 2 && toolbarVisible) {
                toolbarVisible = false
                refreshUi()
            } else if (delta < -2 && !toolbarVisible) {
                toolbarVisible = true
                refreshUi()
            }
        }

        override fun onSelectionRectsChanged(rects: List<FloatArray>, text: String, scale: Float) {
            android.util.Log.i("XGBrowser", "panel.onSelectionRectsChanged n=" + rects.size + " text=" + text.take(20))
            selectionScale = scale
            selectionRects.clear()
            selectionRects.addAll(rects)
            selectedText = text.trim()
            refreshUi()
        }

        override fun onTextSelected(text: String) {
            android.util.Log.i("XGBrowser", "onTextSelected -> text: '$text'")
            if (text.isNotBlank()) {
                selectedText = text.trim()
                refreshUi()
            }
        }

        override fun onTextSelectionCleared() {
            if (selectedText.isNotEmpty()) {
                selectedText = ""
                refreshUi()
            }
        }

        override fun onLinkContextMenu(url: String) {
            contextMenuTarget = ContextMenuTarget.Link(url)
            refreshUi()
        }

        override fun onImageContextMenu(imageUrl: String) {
            contextMenuTarget = ContextMenuTarget.Image(imageUrl)
            refreshUi()
        }
    }

    private fun newTab(url: String? = null, parentTabId: Long? = null): BrowserTab? {
        val host = webHost ?: return null
        val tabId = nextTabId++
        val tab = BrowserTab(
            context = host.context,
            id = tabId,
            parentTabId = parentTabId,
            callbacks = createTabCallbacks(tabId),
        )
        tabs.add(tab)
        tabLabels.add("新标签")
        activateTab(tab.id)
        if (!url.isNullOrBlank()) {
            tab.load(url)
        }
        return tab
    }

    private fun newBackgroundTab(url: String, parentTabId: Long? = null) {
        val host = webHost ?: return
        val tabId = nextTabId++
        val tab = BrowserTab(
            context = host.context,
            id = tabId,
            parentTabId = parentTabId,
            callbacks = createTabCallbacks(tabId),
        )
        tabs.add(tab)
        tabLabels.add("新标签")
        tab.pause()
        tab.load(url)
        refreshUi()
        android.widget.Toast.makeText(host.context, "已在后台打开新标签页", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(text: String, toastMsg: String = "已复制") {
        val host = webHost ?: return
        val cm = host.context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("xg_browser", text))
        android.widget.Toast.makeText(host.context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun openExternal(url: String) {
        val host = webHost ?: return
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            host.context.startActivity(intent)
        }.onFailure {
            android.widget.Toast.makeText(host.context, "无法唤起外部应用", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadImage(url: String) {
        val host = webHost ?: return
        runCatching {
            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    android.webkit.URLUtil.guessFileName(url, null, "image/*"),
                )
            }
            val dm = host.context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            android.widget.Toast.makeText(host.context, "开始下载图片...", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure {
            android.widget.Toast.makeText(host.context, "下载失败: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearSelection() {
        android.util.Log.i("XGBrowser", "panel.clearSelection from " + android.util.Log.getStackTraceString(Throwable()).lines().getOrNull(1))
        selectedText = ""
        tabs.firstOrNull { it.id == activeTabId }?.clearSelection()
        refreshUi()
    }

    private fun activateTab(id: Long) {
        val host = webHost ?: return
        tabs.forEach { tab ->
            if (tab.id == id) {
                tab.attachTo(host)
                tab.resume()
            } else {
                tab.pause()
                tab.detachFrom(host)
            }
        }
        activeTabId = id
        val active = tabs.firstOrNull { it.id == id }
        currentUrl = active?.url.orEmpty()
        addressText = currentUrl
        lastScrollY = 0
        scrollAccum = 0
        refreshUi()
    }

    private fun closeTab(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs.removeAt(index)
        if (index in tabLabels.indices) tabLabels.removeAt(index)
        webHost?.let { tab.detachFrom(it) }
        tab.destroy()
        if (tabs.isEmpty()) {
            dismiss()
            return
        }
        if (activeTabId == id) {
            val parentId = tab.parentTabId
            val target = if (parentId != null && tabs.any { it.id == parentId }) {
                tabs.first { it.id == parentId }
            } else {
                tabs.last()
            }
            activateTab(target.id)
        }
        refreshUi()
    }

    private fun syncTabLabel(tabId: Long, title: String, url: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val label = title.ifBlank { url }
        if (index in tabLabels.indices) tabLabels[index] = label
    }

    private fun openUrl(target: String) {
        val tab = tabs.firstOrNull { it.id == activeTabId } ?: newTab(target) ?: return
        tab.load(target)
        addressEditing = false
        refreshUi()
    }

    private fun refreshUi() {
        tick++
    }

    private fun displayHost(): String {
        val url = currentUrl.ifBlank { tabs.firstOrNull { it.id == activeTabId }?.url.orEmpty() }
        return url.removePrefix("https://").removePrefix("http://").substringBefore('/')
    }

    // ---------- Compose ----------

    @Composable
    private fun BrowserChrome() {
        @Suppress("UNUSED_VARIABLE")
        val redraw = tick

        val activeTab = tabs.firstOrNull { it.id == activeTabId }
        val canGoBack = activeTab?.canGoBack() == true
        val canGoForward = activeTab?.canGoForward() == true
        val isHttps = currentUrl.startsWith("https://", ignoreCase = true)

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(
                            ComposeColor(0xF5F6F8FA),
                            if (maximized) {
                                RoundedCornerShape(0.dp)
                            } else {
                                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                            },
                        ),
                ) {
                    if (!maximized) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                                .width(36.dp)
                                .height(3.5.dp)
                                .background(ComposeColor(0x33000000), RoundedCornerShape(2.dp)),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { addressEditing = true }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Public,
                                contentDescription = if (isHttps) "Secure" else "Site",
                                tint = if (isHttps) ComposeColor(0xFF2E7D32) else ComposeColor(0xFF5F6368),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = displayHost().ifBlank { "新建标签页" },
                                color = ComposeColor(0xFF202124),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = { toggleMaximize() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (maximized) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = if (maximized) "Restore" else "Maximize",
                                tint = ComposeColor(0xFF444746),
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        IconButton(
                            onClick = { dismiss() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = ComposeColor(0xFF444746),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(ComposeColor(0x18000000)),
                    )
                }

                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = ComposeColor(0xFF1A73E8),
                        trackColor = ComposeColor.Transparent,
                    )
                }
            }

            AnimatedVisibility(
                visible = toolbarVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .shadow(10.dp, RoundedCornerShape(26.dp), ambientColor = ComposeColor(0x14000000), spotColor = ComposeColor(0x28000000))
                        .background(ComposeColor(0xF6FFFFFF), RoundedCornerShape(26.dp))
                        .border(0.8.dp, ComposeColor(0x24000000), RoundedCornerShape(26.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolbarIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        enabled = canGoBack || tabs.size > 1,
                    ) {
                        if (canGoBack) {
                            activeTab?.goBack()
                        } else if (tabs.size > 1 && activeTab != null) {
                            closeTab(activeTab!!.id)
                        }
                    }

                    ToolbarIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Forward",
                        enabled = canGoForward,
                    ) {
                        activeTab?.goForward()
                    }

                    ToolbarIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Address",
                    ) {
                        addressEditing = true
                    }

                    TabsBadgeButton(tabCount = tabs.size) {
                        tabsVisible = !tabsVisible
                    }

                    ToolbarIconButton(
                        icon = if (progress in 1..99) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (progress in 1..99) "Stop" else "Reload",
                    ) {
                        if (progress in 1..99) {
                            activeTab?.webView?.stopLoading()
                        } else {
                            activeTab?.reload()
                        }
                    }
                }
            }

            if (!maximized) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, ComposeColor(0x26000000), RoundedCornerShape(20.dp)),
                )
                BrowserResizeCornerHandle(onResizeWindow = ::resizeWindowBy)
            }

            if (selectedText.isNotBlank() && selectionRects.isNotEmpty()) {
                var l = Float.MAX_VALUE
                var t = Float.MAX_VALUE
                var r = -Float.MAX_VALUE
                var b = -Float.MAX_VALUE
                selectionRects.forEach { rect ->
                    l = minOf(l, rect[0] * selectionScale)
                    t = minOf(t, rect[1] * selectionScale)
                    r = maxOf(r, rect[2] * selectionScale)
                    b = maxOf(b, rect[3] * selectionScale)
                }
                SelectionActionPill(
                    text = selectedText,
                    anchorLeft = l,
                    anchorTop = t,
                    anchorRight = r,
                    anchorBottom = b,
                    onSearch = {
                        newTab("https://www.bing.com/search?q=" + android.net.Uri.encode(it), parentTabId = activeTabId)
                        clearSelection()
                    },
                    onTranslate = {
                        newTab("https://www.bing.com/dict/search?q=" + android.net.Uri.encode(it), parentTabId = activeTabId)
                        clearSelection()
                    },
                    onCopy = {
                        copyToClipboard(it)
                        clearSelection()
                    },
                    onPin = {
                        webHost?.context?.let { ctx -> ScreenPinManager.pinText(ctx, it) }
                        clearSelection()
                    },
                    onClose = { clearSelection() },
                )
            }

            if (tabsVisible) TabsSheet()
            if (addressEditing) AddressSheet()


            contextMenuTarget?.let { target ->
                ContextMenuSheet(
                    target = target,
                    onDismiss = { contextMenuTarget = null },
                    onOpenInNewTab = { url ->
                        contextMenuTarget = null
                        newTab(url, parentTabId = activeTabId)
                    },
                    onOpenInBackground = { url ->
                        contextMenuTarget = null
                        newBackgroundTab(url, parentTabId = activeTabId)
                    },
                    onCopy = { text, msg ->
                        contextMenuTarget = null
                        copyToClipboard(text, msg)
                    },
                    onOpenExternal = { url ->
                        contextMenuTarget = null
                        openExternal(url)
                    },
                    onDownloadImage = { url ->
                        contextMenuTarget = null
                        downloadImage(url)
                    },
                    onSearchByImage = { imgUrl ->
                        contextMenuTarget = null
                        newTab("https://www.bing.com/images/search?view=detailv2&iss=sbi&FORM=SBIHMP&sbisrc=UrlPaste&q=imgurl:" + Uri.encode(imgUrl), parentTabId = activeTabId)
                    },
                )
            }
        }
    }

    @Composable
    private fun ToolbarIconButton(
        icon: ImageVector,
        contentDescription: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) ComposeColor(0xFF202124) else ComposeColor(0x38000000),
                modifier = Modifier.size(20.dp),
            )
        }
    }

    @Composable
    private fun TabsBadgeButton(
        tabCount: Int,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .border(1.6.dp, ComposeColor(0xFF202124), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tabCount.toString(),
                    color = ComposeColor(0xFF202124),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    @Composable
    private fun TabsSheet() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor(0x66000000))
                .clickable { tabsVisible = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = ComposeColor(0x18000000), spotColor = ComposeColor(0x30000000))
                    .clip(RoundedCornerShape(22.dp))
                    .background(ComposeColor(0xFBFDFEFF))
                    .border(0.8.dp, ComposeColor(0x20000000), RoundedCornerShape(22.dp))
                    .padding(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "标签页 (${tabs.size})",
                        color = ComposeColor(0xFF202124),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(ComposeColor(0x141A73E8))
                            .clickable {
                                tabsVisible = false
                                newTab("https://www.bing.com")
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Tab",
                            tint = ComposeColor(0xFF1A73E8),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "新建",
                            color = ComposeColor(0xFF1A73E8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(tabs.toList()) { tab ->
                        val isActive = tab.id == activeTabId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) ComposeColor(0x121A73E8) else ComposeColor(0x08000000))
                                .border(
                                    1.dp,
                                    if (isActive) ComposeColor(0x661A73E8) else ComposeColor(0x10000000),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    activateTab(tab.id)
                                    tabsVisible = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.title.ifBlank { tab.url },
                                    color = if (isActive) ComposeColor(0xFF1A73E8) else ComposeColor(0xFF202124),
                                    fontSize = 13.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = tab.url.removePrefix("https://").removePrefix("http://"),
                                    color = ComposeColor(0xFF757575),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { closeTab(tab.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close tab",
                                    tint = ComposeColor(0xFF757575),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AddressSheet() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor(0x66000000))
                .clickable { addressEditing = false },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 56.dp)
                    .fillMaxWidth(0.92f)
                    .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = ComposeColor(0x18000000), spotColor = ComposeColor(0x30000000))
                    .clip(RoundedCornerShape(20.dp))
                    .background(ComposeColor(0xFBFDFEFF))
                    .border(0.8.dp, ComposeColor(0x20000000), RoundedCornerShape(20.dp))
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                Text(
                    text = "网址或搜索",
                    color = ComposeColor(0xFF5F6368),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ComposeColor(0x0A000000))
                        .border(0.8.dp, ComposeColor(0x1A000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = ComposeColor(0xFF5F6368),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = addressText,
                        onValueChange = { addressText = it },
                        textStyle = TextStyle(color = ComposeColor(0xFF202124), fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(ComposeColor(0xFF1A73E8)),
                        modifier = Modifier.weight(1f),
                    )
                    if (addressText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { addressText = "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = ComposeColor(0xFF757575),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchEngineChip(label = "必应") {
                        val query = addressText.trim()
                        if (query.isNotEmpty()) {
                            addressEditing = false
                            openUrl("https://www.bing.com/search?q=" + Uri.encode(query))
                        }
                    }
                    SearchEngineChip(label = "百度") {
                        val query = addressText.trim()
                        if (query.isNotEmpty()) {
                            addressEditing = false
                            openUrl("https://www.baidu.com/s?wd=" + Uri.encode(query))
                        }
                    }
                    SearchEngineChip(label = "Google") {
                        val query = addressText.trim()
                        if (query.isNotEmpty()) {
                            addressEditing = false
                            openUrl("https://www.google.com/search?q=" + Uri.encode(query))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "取消",
                        color = ComposeColor(0xFF5F6368),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { addressEditing = false }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ComposeColor(0xFF1A73E8))
                            .clickable {
                                val target = addressText.trim()
                                addressEditing = false
                                if (target.isNotEmpty()) openUrl(target)
                            }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "前往",
                            color = ComposeColor.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchEngineChip(label: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(ComposeColor(0x0F000000))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(label, color = ComposeColor(0xFF3C4043), fontSize = 12.sp)
        }
    }

    @Composable
    private fun BoxScope.BrowserResizeCornerHandle(onResizeWindow: (Float, Float) -> Unit) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(36.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResizeWindow(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.BottomEnd,
        ) {
            Canvas(modifier = Modifier.size(20.dp).padding(end = 4.dp, bottom = 4.dp)) {
                val color = ComposeColor(0x6678909C)
                val strokeWidth = 2.dp.toPx()
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.45f, size.height),
                    end = Offset(size.width, size.height * 0.45f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.75f, size.height),
                    end = Offset(size.width, size.height * 0.75f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }


    @Composable
    private fun BoxScope.SelectionActionPill(
        text: String,
        anchorLeft: Float,
        anchorTop: Float,
        anchorRight: Float,
        anchorBottom: Float,
        onSearch: (String) -> Unit,
        onTranslate: (String) -> Unit,
        onCopy: (String) -> Unit,
        onPin: (String) -> Unit,
        onClose: () -> Unit,
    ) {
        val density = LocalDensity.current
        val topInsetPx = with(density) { 46.dp.toPx() }
        var pillSize by androidx.compose.runtime.remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val gap = with(density) { 8.dp.toPx() }
        val minTop = topInsetPx + with(density) { 4.dp.toPx() }

        // 优先放在选区上方；上方空间不够就放到选区下方
        val aboveY = anchorTop + topInsetPx - pillSize.height - gap
        val belowY = anchorBottom + topInsetPx + gap
        val useAbove = aboveY >= minTop
        val targetY = if (useAbove) aboveY else belowY
        val targetX = (anchorLeft + anchorRight) / 2f - pillSize.width / 2f

        Row(
            modifier = Modifier
                .offset {
                    IntOffset(
                        targetX.roundToInt(),
                        targetY.roundToInt(),
                    )
                }
                .onSizeChanged { pillSize = it }
                .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = ComposeColor(0x18000000), spotColor = ComposeColor(0x30000000))
                .background(ComposeColor(0xFBFDFEFF), RoundedCornerShape(22.dp))
                .border(0.8.dp, ComposeColor(0x24000000), RoundedCornerShape(22.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionActionPillItem(icon = Icons.Rounded.Search, label = "搜索") { onSearch(text) }
            SelectionActionPillItem(icon = Icons.Outlined.Translate, label = "翻译") { onTranslate(text) }
            SelectionActionPillItem(icon = Icons.Outlined.ContentCopy, label = "复制") { onCopy(text) }
            SelectionActionPillItem(icon = Icons.Outlined.PushPin, label = "钉屏") { onPin(text) }
        }
    }

    @Composable
    private fun SelectionActionPillItem(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ComposeColor(0xFF1A73E8),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = ComposeColor(0xFF202124),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    @Composable
    private fun ContextMenuSheet(
        target: ContextMenuTarget,
        onDismiss: () -> Unit,
        onOpenInNewTab: (String) -> Unit,
        onOpenInBackground: (String) -> Unit,
        onCopy: (String, String) -> Unit,
        onOpenExternal: (String) -> Unit,
        onDownloadImage: (String) -> Unit,
        onSearchByImage: (String) -> Unit,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor(0x66000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(bottom = 20.dp)
                    .shadow(20.dp, RoundedCornerShape(22.dp), ambientColor = ComposeColor(0x18000000), spotColor = ComposeColor(0x30000000))
                    .clip(RoundedCornerShape(22.dp))
                    .background(ComposeColor(0xFBFDFEFF))
                    .border(0.8.dp, ComposeColor(0x20000000), RoundedCornerShape(22.dp))
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                when (target) {
                    is ContextMenuTarget.Link -> {
                        Text(
                            text = "链接操作",
                            color = ComposeColor(0xFF202124),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = target.url,
                            color = ComposeColor(0xFF757575),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ComposeColor(0x1A000000)))
                        Spacer(Modifier.height(8.dp))

                        ContextMenuItem(
                            icon = Icons.Rounded.OpenInNew,
                            label = "在新标签页打开",
                        ) { onOpenInNewTab(target.url) }

                        ContextMenuItem(
                            icon = Icons.Rounded.Tab,
                            label = "在后台打开新标签",
                        ) { onOpenInBackground(target.url) }

                        ContextMenuItem(
                            icon = Icons.Outlined.ContentCopy,
                            label = "复制链接地址",
                        ) { onCopy(target.url, "已复制链接地址") }

                        ContextMenuItem(
                            icon = Icons.Rounded.Launch,
                            label = "在外部浏览器打开",
                        ) { onOpenExternal(target.url) }
                    }

                    is ContextMenuTarget.Image -> {
                        Text(
                            text = "图片操作",
                            color = ComposeColor(0xFF202124),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = target.imageUrl,
                            color = ComposeColor(0xFF757575),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ComposeColor(0x1A000000)))
                        Spacer(Modifier.height(8.dp))

                        ContextMenuItem(
                            icon = Icons.Rounded.ImageSearch,
                            label = "以图搜图",
                        ) { onSearchByImage(target.imageUrl) }

                        ContextMenuItem(
                            icon = Icons.Rounded.Image,
                            label = "在新标签页查看原图",
                        ) { onOpenInNewTab(target.imageUrl) }

                        ContextMenuItem(
                            icon = Icons.Rounded.Download,
                            label = "保存图片",
                        ) { onDownloadImage(target.imageUrl) }

                        ContextMenuItem(
                            icon = Icons.Outlined.ContentCopy,
                            label = "复制图片链接",
                        ) { onCopy(target.imageUrl, "已复制图片链接") }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "取消",
                        color = ComposeColor(0xFF5F6368),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun ContextMenuItem(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ComposeColor(0xFF444746),
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                color = ComposeColor(0xFF202124),
                fontSize = 14.sp,
            )
        }
    }

    // ---------- Compose owner ----------

    private class BrowserComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}
