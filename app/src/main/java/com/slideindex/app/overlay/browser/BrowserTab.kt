package com.slideindex.app.overlay.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.slideindex.app.service.WebViewFileChooserBridge

/**
 * 一个标签页：WebView + 自绘弹窗/表单接管 + 状态回调。
 *
 * 设计要点（来自 FV 逆向 + P0 实测）：
 * - 浮层里系统 JS 弹窗、`<select>`、`<input type=date>` 全部不可用 → 自绘 + JS 接管；
 * - 文件选择器能被挡在浮层下面 → 交给外层 suppress；
 * - 非激活标签调用 `onPause()`，内存按站点计（不按标签数）。
 */
internal class BrowserTab(
    val context: Context,
    val id: Long,
    val parentTabId: Long? = null,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onUrlChanged(url: String, title: String)
        fun onProgress(progress: Int)
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onNewWindowRequested(url: String)

        /** 文件选择期间把浮层藏起来，避免选择器被压住。 */
        fun onFileChooserOpened()

        fun onFileChooserClosed()

        /** window.open：给调用方一个可用的新标签 WebView。 */
        fun onCreateTabForWindow(): BrowserTab?

        /** 网页全屏视频。 */
        fun onShowFullscreenView(view: android.view.View)

        fun onHideFullscreenView()

        /** 页面滚动位置变化（用于自动隐藏底部工具条）。 */
        fun onPageScrolled(scrollY: Int)

        /** 新页面开始加载：清空页面级状态。 */
        fun onPageStartedReset()

        /** 页面上探测到可直接下载的视频地址。 */
        fun onVideoFound(url: String, kind: String)

        /** 页面加载完成：记录历史。 */
        fun onHistoryRecord(url: String, title: String)

        /** 网页声明的 theme-color（用于顶栏取色）。 */
        fun onThemeColorChanged(color: Int)

        /** WebView 渲染进程崩溃，需要重建标签。 */
        fun onRenderProcessGone()

        /** 选词监听（P0）。 */
        fun onTextSelected(text: String)

        /** 选区矩形变化（CSS px，相对网页内容左上角）。 */
        fun onSelectionRectsChanged(rects: List<FloatArray>, text: String, scale: Float)

        /** 选词清空。 */
        fun onTextSelectionCleared()

        /** 长按链接菜单（P1）。 */
        fun onLinkContextMenu(url: String)

        /** 长按图片菜单（P1）。 */
        fun onImageContextMenu(imageUrl: String)
    }

    /** 懒加载：冷启动恢复时先只记住 URL，等用户切到该标签才真正 load。 */
    private var pendingLazyUrl: String? = null
    val isLazyPending: Boolean get() = pendingLazyUrl != null

    var desktopMode: Boolean = false
        private set

    var url: String = ""
        private set
    var title: String = ""
        private set
    var progress: Int = 0
        private set

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastSelectionScale = 1f
    private var lastNonEmptySelectionAt = 0L
    private var blockedRequestCount = 0
    var thumbnail: android.graphics.Bitmap? = null
        private set

    val webView: WebView = createWebView()

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return object : WebView(context) {
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                return super.onTouchEvent(event)
            }

            override fun startActionMode(
                callback: android.view.ActionMode.Callback?,
                type: Int,
            ): android.view.ActionMode? {
                // 浮层窗口没有 Activity 令牌，系统浮动工具栏弹不出来且会清掉选区；
                // 文字选区交给我们自绘（手柄 + 工具条）。
                if (type == android.view.ActionMode.TYPE_FLOATING) {
                    // 占位 ActionMode：让 Chromium 保留选区，UI 由我们自绘
                    return DummyActionMode(context)
                }
                return super.startActionMode(callback, type)
            }

            override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                super.onScrollChanged(l, t, oldl, oldt)
                callbacks.onPageScrolled(t)
            }





        }.apply {
            setBackgroundColor(Color.WHITE)
            setOnLongClickListener { v ->
                val hit = (v as? WebView)?.hitTestResult
                if (hit != null) {
                    when (hit.type) {
                        WebView.HitTestResult.SRC_ANCHOR_TYPE,
                        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                            val linkUrl = hit.extra.orEmpty()
                            if (linkUrl.isNotBlank()) {
                                callbacks.onLinkContextMenu(linkUrl)
                                return@setOnLongClickListener true
                            }
                        }
                        WebView.HitTestResult.IMAGE_TYPE -> {
                            val imageUrl = hit.extra.orEmpty()
                            if (imageUrl.isNotBlank()) {
                                callbacks.onImageContextMenu(imageUrl)
                                return@setOnLongClickListener true
                            }
                        }
                    }
                }
                // 普通文字：交给系统原生选区（手柄 + 菜单都标准化）
                false
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                builtInZoomControls = true
                displayZoomControls = false
                // 关掉“必须用户手势才播放”：否则 <video> 会停在 Chromium 的大播放按钮遮罩上
                // （相机预览、自动播放的网页视频都会卡在这里）。
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            // 去掉 WebView 标记（"; wv"），否则很多站点（Bing 相机、部分登录页）会判定不可用
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(" Version/4.0", "")
            rememberDefaultUa(settings.userAgentString)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            webViewClient = createClient()
            webChromeClient = createChromeClient()
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                runCatching {
                    val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimeType)
                        addRequestHeader("User-Agent", userAgent)
                        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
                        setNotificationVisibility(
                            android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                        )
                        setDestinationInExternalPublicDir(
                            android.os.Environment.DIRECTORY_DOWNLOADS,
                            android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType),
                        )
                    }
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                    dm.enqueue(request)
                }.onFailure {
                }
            }
            addJavascriptInterface(JsBridge(this), "xgBrowser")
        }
    }

    /** 拖动选区手柄：参数是**窗口坐标**，这里换算成网页 CSS 坐标。 */
    private fun createClient(): WebViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            blockedRequestCount = 0
            callbacks.onPageStartedReset()
            super.onPageStarted(view, url, favicon)
            this@BrowserTab.url = url.orEmpty()
            callbacks.onPageStarted(url.orEmpty())
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: android.webkit.SslErrorHandler,
            error: android.net.http.SslError,
        ) {
            BrowserDialogs.confirm(
                context,
                "此网站证书有问题（code=${error.primaryError}），继续访问可能有风险。是否继续？",
                onOk = { runCatching { handler.proceed() } },
                onCancel = { runCatching { handler.cancel() } },
            )
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail,
        ): Boolean {
            callbacks.onRenderProcessGone()
            return true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            if (!url.isNullOrBlank()) callbacks.onHistoryRecord(url, view.title.orEmpty())
            this@BrowserTab.url = url.orEmpty()
            title = view.title.orEmpty()
            callbacks.onPageFinished(url.orEmpty())
            callbacks.onUrlChanged(url.orEmpty(), title)
            injectFormTakeover(view)
            view.evaluateJavascript(
                "(function(){try{var m=document.querySelector('meta[name=theme-color]');" +
                    "return m&&m.content?m.content:'';}catch(e){return '';}})()",
            ) { color ->
                val parsed = parseThemeColor(color)
                if (parsed != 0) callbacks.onThemeColorChanged(parsed)
            }
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): android.webkit.WebResourceResponse? {
            val host = request.url?.host?.lowercase().orEmpty()
            detectMediaRequest(request.url?.toString().orEmpty())
            if (host.isNotEmpty() && AdBlockHosts.isBlocked(host)) {
                blockedRequestCount++
                return android.webkit.WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    java.io.ByteArrayInputStream(ByteArray(0)),
                )
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url?.toString().orEmpty()
            if (target.startsWith("http://") || target.startsWith("https://")) return false
            // 非 http(s)：交给系统（电话、市场、intent:// 等）
            return runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(target))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(true)
        }
    }

    private fun createChromeClient(): WebChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
            return true
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            progress = newProgress
            callbacks.onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, t: String?) {
            super.onReceivedTitle(view, t)
            title = t.orEmpty()
            callbacks.onUrlChanged(view.url.orEmpty(), title)
        }

        // --- 自绘 JS 弹窗 ---
        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: android.webkit.JsResult,
        ): Boolean {
            BrowserDialogs.alert(context, message.orEmpty()) { result.confirm() }
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: android.webkit.JsResult,
        ): Boolean {
            BrowserDialogs.confirm(
                context,
                message.orEmpty(),
                onOk = { result.confirm() },
                onCancel = { result.cancel() },
            )
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: android.webkit.JsPromptResult,
        ): Boolean {
            BrowserDialogs.prompt(
                context,
                message.orEmpty(),
                defaultValue.orEmpty(),
                onOk = { result.confirm(it) },
                onCancel = { result.cancel() },
            )
            return true
        }

        override fun onShowFileChooser(
            view: WebView,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            if (filePathCallback == null || fileChooserParams == null) return false
            val wrapped = ValueCallback<Array<Uri>> { uris ->
                runCatching { callbacks.onFileChooserClosed() }
                filePathCallback.onReceiveValue(uris)
            }
            callbacks.onFileChooserOpened()
            val launched = WebViewFileChooserBridge.launch(context, wrapped, fileChooserParams)
            if (!launched) callbacks.onFileChooserClosed()
            return launched
        }

        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
            val req = request ?: return
            val host = req.origin?.host.orEmpty()
            val resources = req.resources ?: emptyArray()
            val granted = mutableListOf<String>()
            var askCamera = false
            var askMic = false

            resources.forEach { resource ->
                when (resource) {
                    android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                        val remembered = BrowserPermissionMemory.get(context, host, "camera")
                        val hasSystem =
                            com.slideindex.app.util.PermissionHelper.hasCameraPermission(context)
                        when {
                            remembered == false -> Unit // 用户之前拒绝过该站点，静默拒绝
                            remembered == true && hasSystem -> granted += resource
                            hasSystem -> askCamera = true
                            else -> askCamera = true
                        }
                    }

                    android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                        val remembered = BrowserPermissionMemory.get(context, host, "mic")
                        val hasSystem =
                            com.slideindex.app.voice.StashVoiceController.hasRecordAudioPermission(context)
                        when {
                            remembered == false -> Unit
                            remembered == true && hasSystem -> granted += resource
                            hasSystem -> askMic = true
                            else -> askMic = true
                        }
                    }

                    else -> granted += resource
                }
            }

            if (granted.isNotEmpty()) runCatching { req.grant(granted.toTypedArray()) }
            if (!askCamera && !askMic) {
                runCatching { req.deny() }
                return
            }

            runCatching { req.deny() }
            val what = buildString {
                if (askCamera) append(context.getString(com.slideindex.app.R.string.browser_camera))
                if (askCamera && askMic) append(context.getString(com.slideindex.app.R.string.browser_and))
                if (askMic) append(context.getString(com.slideindex.app.R.string.browser_mic))
            }
            BrowserDialogs.confirm(
                context,
                context.getString(com.slideindex.app.R.string.browser_permission_request, what),
                onOk = {
                    // 记住该站点的允许
                    if (askCamera) BrowserPermissionMemory.set(context, host, "camera", true)
                    if (askMic) BrowserPermissionMemory.set(context, host, "mic", true)
                    if (askCamera &&
                        !com.slideindex.app.util.PermissionHelper.hasCameraPermission(context)
                    ) {
                        com.slideindex.app.overlay.searchpanel.CameraPermissionTrampolineActivity
                            .ensureGranted(context)
                    }
                    if (askMic &&
                        !com.slideindex.app.voice.StashVoiceController
                            .hasRecordAudioPermission(context)
                    ) {
                        com.slideindex.app.voice.StashVoicePermissionActivity.ensureGranted(context)
                    }
                },
                onCancel = {
                    if (askCamera) BrowserPermissionMemory.set(context, host, "camera", false)
                    if (askMic) BrowserPermissionMemory.set(context, host, "mic", false)
                },
            )
        }

        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
            if (view == null) return
            callbacks.onShowFullscreenView(view)
        }

        override fun onHideCustomView() {
            callbacks.onHideFullscreenView()
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?,
        ): Boolean {
            // target=_blank / window.open：真正把新标签的 WebView 交给 transport
            return runCatching {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val tab = callbacks.onCreateTabForWindow() ?: return false
                transport.webView = tab.webView
                resultMsg.sendToTarget()
                true
            }.getOrElse { e ->
                false
            }
        }
    }

    /** 注入：接管 `<select>` 与日期/时间输入。 */
    private fun injectFormTakeover(view: WebView) {
        view.evaluateJavascript(
            """
            (function(){
              if (window.__xgHooked) { if (window.xgHookAll) window.xgHookAll(); return; }
              window.__xgHooked = true;
              function hookSelects(){
                var list = document.querySelectorAll('select');
                for (var i=0;i<list.length;i++){
                  (function(sel, idx){
                    if (sel.__xgSelect) return;
                    sel.__xgSelect = true;
                    sel.addEventListener('click', function(e){
                      e.preventDefault(); e.stopPropagation();
                      var opts = [];
                      for (var j=0;j<sel.options.length;j++) opts.push(sel.options[j].text);
                      xgBrowser.onSelect(idx, JSON.stringify(opts), sel.selectedIndex);
                    }, true);
                  })(list[i], i);
                }
              }
              function hookDates(){
                var list = document.querySelectorAll('input[type=date],input[type=time],input[type=datetime-local]');
                for (var i=0;i<list.length;i++){
                  (function(inp, idx){
                    if (inp.__xgDate) return;
                    inp.__xgDate = true;
                    inp.readOnly = true;
                    inp.addEventListener('click', function(e){
                      e.preventDefault(); e.stopPropagation();
                      xgBrowser.onDateInput(idx, inp.type, inp.value || '');
                    }, true);
                  })(list[i], i);
                }
              }
              var selTimer = null;
              document.addEventListener('selectionchange', function(){
                clearTimeout(selTimer);
                selTimer = setTimeout(function(){
                  try {
                    var sel = window.getSelection ? window.getSelection() : null;
                    var text = (sel && !sel.isCollapsed) ? sel.toString().trim() : '';
                    if (text && text.length > 0) {
                      xgBrowser.onTextSelected(text);
                    } else {
                      xgBrowser.onSelectionCleared();
                    }
                  } catch(e) {}
                }, 120);
              }, true);
              try {
                var s = document.createElement('style');
                s.innerHTML = '* { -webkit-user-select: text !important; user-select: text !important; -webkit-touch-callout: default !important; }';
                (document.head || document.documentElement).appendChild(s);
              } catch(e) {}
              window.__xgAnchorNode = null;
              window.__xgAnchorOffset = 0;
              window.__xgFocusNode = null;
              window.__xgFocusOffset = 0;
              window.xgSelectWordAt = function(x, y) {
                var r = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
                if (!r) return '';
                try { r.expand('word'); } catch(e) {}
                window.__xgAnchorNode = r.startContainer;
                window.__xgAnchorOffset = r.startOffset;
                window.__xgFocusNode = r.endContainer;
                window.__xgFocusOffset = r.endOffset;
                var sel = window.getSelection();
                sel.removeAllRanges();
                sel.addRange(r);
                window.xgReportSelection();
                return sel.toString();
              };
              window.xgExtendSelection = function(x, y, fromStart) {
                if (!window.__xgAnchorNode) return;
                var r = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
                if (!r) return;
                var sel = window.getSelection();
                try {
                  if (fromStart) {
                    sel.setBaseAndExtent(window.__xgFocusNode, window.__xgFocusOffset,
                                         r.startContainer, r.startOffset);
                    window.__xgAnchorNode = r.startContainer;
                    window.__xgAnchorOffset = r.startOffset;
                  } else {
                    sel.setBaseAndExtent(window.__xgAnchorNode, window.__xgAnchorOffset,
                                         r.startContainer, r.startOffset);
                    window.__xgFocusNode = r.startContainer;
                    window.__xgFocusOffset = r.startOffset;
                  }
                } catch (e) {}
                window.xgReportSelection();
              };
              window.xgReportSelection = function() {
                try {
                  var vp = (window.visualViewport && window.visualViewport.width)
                              ? window.visualViewport.width
                              : (window.innerWidth || 1);
                  var sel = window.getSelection();
                  if (!sel || sel.rangeCount === 0 || sel.isCollapsed || !sel.toString().trim()) {
                    if (window.xgBrowser) window.xgBrowser.onSelectionRects('[]', '', vp);
                    return;
                  }
                  var rects = [];
                  for (var i = 0; i < sel.rangeCount; i++) {
                    var cr = sel.getRangeAt(i).getClientRects();
                    for (var j = 0; j < cr.length; j++) {
                      rects.push([cr[j].left, cr[j].top, cr[j].right, cr[j].bottom]);
                    }
                  }
                  if (window.xgBrowser) {
                    window.xgBrowser.onSelectionRects(JSON.stringify(rects), sel.toString(), vp);
                  }
                } catch (e) {}
              };
              document.addEventListener('selectionchange', window.xgReportSelection, true);
              // 页面滚动后选区矩形会变，需要重报，否则工具条不跟随
              var __xgScrollTimer = null;
              window.addEventListener('scroll', function() {
                if (__xgScrollTimer) return;
                __xgScrollTimer = setTimeout(function() {
                  __xgScrollTimer = null;
                  try {
                    var sel = window.getSelection();
                    if (sel && !sel.isCollapsed && sel.toString().trim()) window.xgReportSelection();
                  } catch (e) {}
                }, 80);
              }, true);
              window.xgHookAll = function(){ hookSelects(); hookDates(); };
              window.xgHookAll();
              // 视频嗅探：上报 <video>/<source> 的 http(s) 地址（blob/HLS 交给页面自身）
              window.xgReportVideos = function(){
                try {
                  var seen = {};
                  var list = document.querySelectorAll('video');
                  for (var i = 0; i < list.length; i++) {
                    var v = list[i];
                    var urls = [v.currentSrc, v.src];
                    var sources = v.querySelectorAll('source');
                    for (var j = 0; j < sources.length; j++) urls.push(sources[j].src);
                    for (var k = 0; k < urls.length; k++) {
                      var u = urls[k];
                      if (!u || seen[u] || u.indexOf('http') !== 0) continue;
                      seen[u] = 1;
                      window.xgBrowser.onVideoFound(u, u.indexOf('.m3u8') >= 0 ? 'hls' : 'file');
                    }
                  }
                } catch (e) {}
              };
              try { window.xgReportVideos(); } catch (e) {}
              // 手势：双击缩放 / 顶部下拉刷新 / 左边缘右滑后退
              (function(){
                var lastTap = 0, zoomed = false;
                document.addEventListener('touchend', function(e){
                  var now = Date.now();
                  if (now - lastTap < 300) {
                    try {
                      zoomed = !zoomed;
                      document.documentElement.style.zoom = zoomed ? '1.6' : '';
                    } catch (err) {}
                  }
                  lastTap = now;
                }, true);
                var startX = 0, startY = 0, tracking = false;
                document.addEventListener('touchstart', function(e){
                  if (!e.touches || !e.touches.length) return;
                  startX = e.touches[0].clientX;
                  startY = e.touches[0].clientY;
                  tracking = true;
                }, {passive: true, capture: true});
                document.addEventListener('touchend', function(e){
                  if (!tracking) return;
                  tracking = false;
                  if (!e.changedTouches || !e.changedTouches.length) return;
                  var dx = e.changedTouches[0].clientX - startX;
                  var dy = e.changedTouches[0].clientY - startY;
                  if (dy > 120 && Math.abs(dx) < 60 && (window.scrollY || 0) <= 2) {
                    try { window.xgBrowser.onPullRefresh(); } catch (err) {}
                  } else if (dx > 90 && Math.abs(dy) < 60 && startX < 30) {
                    try { window.xgBrowser.onEdgeBack(); } catch (err) {}
                  }
                }, {passive: true, capture: true});
              })();
              document.addEventListener('play', function(){ try { window.xgReportVideos(); } catch (e) {} }, true);
            })();
            """.trimIndent(),
        ) { }
    }

    /** 页面提供的 JS 桥。 */
    /** 占位 ActionMode：只让 WebView 保留选区，不显示任何界面。 */
    private class DummyActionMode(context: android.content.Context) : android.view.ActionMode() {
        private var title: CharSequence? = null
        private var subtitle: CharSequence? = null
        private val menu: android.view.Menu = android.widget.PopupMenu(context, null).menu

        override fun finish() = Unit
        override fun getMenu(): android.view.Menu = menu
        override fun getTitle(): CharSequence? = title
        override fun setTitle(title: CharSequence?) { this.title = title }
        override fun getSubtitle(): CharSequence? = subtitle
        override fun setSubtitle(subtitle: CharSequence?) { this.subtitle = subtitle }
        override fun setTitle(resId: Int) = Unit
        override fun setSubtitle(resId: Int) = Unit
        override fun getMenuInflater(): android.view.MenuInflater? = null
        override fun getCustomView(): android.view.View? = null
        override fun setCustomView(view: android.view.View?) = Unit
        override fun invalidate() = Unit
    }

    private inner class JsBridge(private val host: WebView) {
        @android.webkit.JavascriptInterface
        fun onPullRefresh() {
            webView.post { runCatching { webView.reload() } }
        }

        @android.webkit.JavascriptInterface
        fun onEdgeBack() {
            webView.post { runCatching { if (webView.canGoBack()) webView.goBack() } }
        }

        @android.webkit.JavascriptInterface
        fun onVideoFound(url: String, kind: String) {
            callbacks.onVideoFound(url, kind)
        }

        fun onSelectionRects(rectsJson: String, text: String, viewportCssWidth: Float) {
            // 真实比例 = WebView 物理宽度 / 视觉视口 CSS 宽度（对无 viewport meta 的页面也成立）
            val viewWidth = webView.width.coerceAtLeast(1)
            lastSelectionScale = if (viewportCssWidth > 1f) {
                viewWidth / viewportCssWidth
            } else {
                context.resources.displayMetrics.density
            }
            val rects = runCatching {
                val arr = org.json.JSONArray(rectsJson)
                List(arr.length()) { i ->
                    val r = arr.getJSONArray(i)
                    floatArrayOf(
                        r.getDouble(0).toFloat(),
                        r.getDouble(1).toFloat(),
                        r.getDouble(2).toFloat(),
                        r.getDouble(3).toFloat(),
                    )
                }
            }.getOrDefault(emptyList())
            val now = android.os.SystemClock.uptimeMillis()
            if (rects.isEmpty()) {
                // 系统销毁 ActionMode 时会清一次选区，忽略这种“刚选中就变空”的抖动
                if (now - lastNonEmptySelectionAt < 1000L) return
            } else {
                lastNonEmptySelectionAt = now
            }
            callbacks.onSelectionRectsChanged(rects, text, lastSelectionScale)
        }

        @JavascriptInterface
        fun onScroll(scrollY: Int) {
            callbacks.onPageScrolled(scrollY)
        }

        @JavascriptInterface
        fun onTextSelected(text: String) {
            if (text.isNotBlank()) {
                host.post { callbacks.onTextSelected(text) }
            }
        }

        @JavascriptInterface
        fun onSelectionCleared() {
            host.post { callbacks.onTextSelectionCleared() }
        }

        @JavascriptInterface
        fun onSelect(index: Int, optionsJson: String, selected: Int) {
            val items = runCatching {
                val arr = org.json.JSONArray(optionsJson)
                List(arr.length()) { arr.optString(it) }
            }.getOrDefault(emptyList())
            host.post {
                BrowserDialogs.choose(context, "选择", items, selected) { which ->
                    host.evaluateJavascript(
                        "javascript:(function(){var s=document.querySelectorAll('select')[$index];" +
                            "if(s){s.selectedIndex=$which;" +
                            "s.dispatchEvent(new Event('change',{bubbles:true}));}})()",
                    ) { }
                }
            }
        }

        @JavascriptInterface
        fun onDateInput(index: Int, type: String, value: String) {
            host.post {
                BrowserDialogs.pickDateOrTime(context, type, value) { result ->
                    host.evaluateJavascript(
                        "javascript:(function(){var l=document.querySelectorAll(" +
                            "'input[type=date],input[type=time],input[type=datetime-local]');" +
                            "var e=l[$index];if(e){e.value='$result';" +
                            "e.dispatchEvent(new Event('input',{bubbles:true}));" +
                            "e.dispatchEvent(new Event('change',{bubbles:true}));}})()",
                    ) { }
                }
            }
        }
    }

    /** 懒加载：只记住 URL，不真正加载。 */
    fun setLazy(target: String) {
        pendingLazyUrl = target
        url = target
    }

    /** 切换到该标签时调用：如果是懒加载状态就真正加载。 */
    fun loadIfLazy() {
        val pending = pendingLazyUrl ?: return
        pendingLazyUrl = null
        load(pending)
    }

    fun load(target: String) {
        if (target == "xg://imetest") {
            pendingLazyUrl = null
            url = target
            webView.loadDataWithBaseURL(
                "https://example.com",
                IME_TEST_HTML,
                "text/html",
                "utf-8",
                null,
            )
            return
        }
        val normalized = normalizeUrl(target)
        webView.loadUrl(normalized)
    }

    /** 暗色模式：优先用 WebView 的算法暗化，失败则注入 CSS 滤镜。 */
    fun setDarkMode(enabled: Boolean) {
        val applied = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                webView.settings.isAlgorithmicDarkeningAllowed = enabled
                true
            } else if (android.os.Build.VERSION.SDK_INT >= 29) {
                @Suppress("DEPRECATION")
                webView.settings.forceDark = if (enabled) {
                    android.webkit.WebSettings.FORCE_DARK_ON
                } else {
                    android.webkit.WebSettings.FORCE_DARK_OFF
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (!applied) {
            evaluate(
                if (enabled) {
                    "(function(){document.documentElement.style.filter='invert(1) hue-rotate(180deg)';" +
                        "document.documentElement.style.background='#111';})()"
                } else {
                    "(function(){document.documentElement.style.filter='';" +
                        "document.documentElement.style.background='';})()"
                },
            )
        }
        runCatching { webView.reload() }
    }

    /** 桌面 / 移动 UA 切换（wp 生效于后续请求）。 */
    fun setDesktopMode(desktop: Boolean) {
        desktopMode = desktop
        val settings = webView.settings
        settings.userAgentString = if (desktop) DESKTOP_UA else DEFAULT_UA
        runCatching { webView.reload() }
    }

    fun evaluate(js: String) {
        webView.evaluateJavascript(js) { }
    }

    fun canGoBack(): Boolean = webView.canGoBack()

    fun canGoForward(): Boolean = webView.canGoForward()

    fun goBack(): Boolean {
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    fun goForward(): Boolean {
        if (!webView.canGoForward()) return false
        webView.goForward()
        return true
    }

    fun reload() = webView.reload()

    fun pause() { runCatching { webView.onPause() } }

    fun resume() { runCatching { webView.onResume() } }

    private var lastMediaUrl: String? = null

    /** 从网络请求里嗅探视频：直链与 HLS 都算（blob/MSE 无法从这里拿到）。 */
    private fun detectMediaRequest(url: String) {
        if (url.isBlank()) return
        val lower = url.lowercase()
        val path = lower.substringBefore('?')
        val kind = when {
            path.endsWith(".m3u8") -> "hls"
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m4v") ||
                path.endsWith(".mov") -> "file"
            else -> return
        }
        if (url == lastMediaUrl) return
        lastMediaUrl = url
        callbacks.onVideoFound(url, kind)
    }

    /** 离开该标签前抓一张缩略图（此时 WebView 还挂在窗口里，能正常绘制）。 */
    fun captureThumbnail() {
        runCatching {
            val w = webView.width
            val h = webView.height
            if (w <= 0 || h <= 0) return
            val targetW = 240
            val targetH = (h.toFloat() / w * targetW).toInt().coerceAtLeast(1)
            val full = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            webView.draw(android.graphics.Canvas(full))
            thumbnail = android.graphics.Bitmap.createScaledBitmap(full, targetW, targetH, true)
                .also { if (it !== full) full.recycle() }
        }
    }

    fun attachTo(parent: ViewGroup) {
        if (webView.parent === parent) return
        (webView.parent as? ViewGroup)?.removeView(webView)
        parent.addView(
            webView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    fun detachFrom(parent: ViewGroup) {
        if (webView.parent === parent) parent.removeView(webView)
    }

    fun destroy() {
        runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
        runCatching { webView.stopLoading() }
        runCatching { webView.destroy() }
    }

    fun clearSelection() {
        webView.evaluateJavascript(
            "(function(){ try { if (window.getSelection) window.getSelection().removeAllRanges();" +
                "window.__xgAnchorNode=null;" +
                "if (window.xgBrowser) window.xgBrowser.onSelectionRects('[]',''); } catch(e){} })()",
        ) { }
    }

    private fun querySelectionText(retryIfBlank: Boolean = true) {
        webView.evaluateJavascript("(function(){ try { return window.getSelection ? window.getSelection().toString() : ''; } catch(e){ return ''; } })()") { result ->
            val text = cleanJsResult(result)
            if (text.isNotBlank()) {
                webView.post { callbacks.onTextSelected(text) }
            } else if (retryIfBlank) {
                webView.postDelayed({ querySelectionText(retryIfBlank = false) }, 150)
            }
        }
    }

    /** 解析 #RGB / #RRGGBB / rgb() 形式的 theme-color，失败返回 0。 */
    private fun parseThemeColor(raw: String?): Int {
        val value = raw?.removeSurrounding("\"")?.trim().orEmpty()
        if (value.isEmpty()) return 0
        return runCatching {
            when {
                value.startsWith("#") -> {
                    val hex = value.removePrefix("#")
                    when (hex.length) {
                        6 -> android.graphics.Color.parseColor("#" + hex)
                        3 -> android.graphics.Color.parseColor(
                            "#" + hex.map { ch -> "" + ch + ch }.joinToString(""),
                        )
                        else -> 0
                    }
                }
                value.startsWith("rgb") -> {
                    val nums = value.substringAfter('(').substringBefore(')')
                        .split(',').mapNotNull { it.trim().toIntOrNull() }
                    if (nums.size >= 3) {
                        android.graphics.Color.rgb(nums[0], nums[1], nums[2])
                    } else {
                        0
                    }
                }
                else -> 0
            }
        }.getOrDefault(0)
    }

    private fun cleanJsResult(raw: String?): String {
        if (raw == null || raw == "null" || raw == "\"\"") return ""
        return runCatching {
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                org.json.JSONTokener(raw).nextValue().toString()
            } else raw
        }.getOrDefault(raw.trim('"'))
    }

    /** 极简广告域名拦截：命中即返回空资源。 */
    private object AdBlockHosts {
        private val blocked = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "amazon-adsystem.com",
            "adnxs.com",
            "adsrvr.org",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            "scorecardresearch.com",
            "quantserve.com",
            "moatads.com",
            "zedo.com",
            "adcolony.com",
            "applovin.com",
            "umeng.com",
            "cnzz.com",
            "hm.baidu.com",
            "pos.baidu.com",
            "tongji.baidu.com",
            "cnzz.com",
            "51.la",
            "growingio.com",
            "hotjar.com",
            "mixpanel.com",
            "segment.io",
            "sentry.io",
            "bugly.qq.com",
            "pingjs.qq.com",
            "umengcloud.com",
            "alog.umeng.com",
            "ad.google.com",
            "ads.youtube.com",
            "googletagmanager.com",
            "googletagservices.com",
            "2mdn.net",
            "serving-sys.com",
            "adsafeprotected.com",
            "rubiconproject.com",
            "pubmatic.com",
            "openx.net",
            "casalemedia.com",
            "smartadserver.com",
            "yieldmo.com",
            "sharethrough.com",
            "triplelift.com",
            "indexww.com",
            "33across.com",
            "adform.net",
            "1rx.io",
            "bidswitch.net",
            "travelaudience.com",
            "adroll.com",
            "mathtag.com",
            "turn.com",
            "tapad.com",
            "demdex.net",
            "omtrdc.net",
            "everesttech.net",
            "mc.yandex.ru",
            "an.yandex.ru",
            "adfox.ru",
            "criteo.net",
            "gumgum.com",
            "lijit.com",
            "sovrn.com",
            "media.net",
            "contextweb.com",
            "zedo.com",
            "adcolony.com",
            "vungle.com",
            "chartboost.com",
            "unityads.unity3d.com",
            "ironsrc.com",
            "supertonic.com",
            "applovin.com",
            "fyber.com",
            "inmobi.com",
            "mopub.com",
        )

        fun isBlocked(host: String): Boolean {
            if (blocked.contains(host)) return true
            return blocked.any { host.endsWith(".$it") }
        }
    }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

        private var DEFAULT_UA: String = ""

        internal fun rememberDefaultUa(ua: String) {
            if (DEFAULT_UA.isBlank()) DEFAULT_UA = ua
        }

        private val IME_TEST_HTML = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="font:16px sans-serif;padding:16px">
              <h3>输入法避让测试页</h3>
              <p>下面塞一大段占位文字，把输入框推到页面下方。</p>
              <p>${"占位文字。".repeat(120)}</p>
              <p>单行输入：<input style="width:100%;font-size:16px;padding:8px" placeholder="点这里"></p>
              <p>多行输入：</p>
              <textarea style="width:100%;height:80px;font-size:16px;padding:8px"></textarea>
              <p style="height:120px"></p>
            </body></html>
        """.trimIndent()

        fun normalizeUrl(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return "about:blank"
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                trimmed.startsWith("about:") || trimmed.startsWith("file:")
            ) {
                return trimmed
            }
            val looksLikeHost = !trimmed.contains(' ') && trimmed.contains('.')
            return if (looksLikeHost) "https://$trimmed" else "https://www.bing.com/search?q=${Uri.encode(trimmed)}"
        }
    }
}
