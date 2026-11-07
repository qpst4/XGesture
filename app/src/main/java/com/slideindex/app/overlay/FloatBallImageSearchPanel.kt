package com.slideindex.app.overlay

/*
 * Portions derived from Circle To Search (https://github.com/AKS-Labs/CircleToSearch)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.slideindex.app.overlay.compositor.OverlaySceneController
import android.webkit.ValueCallback
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.search.ImageHostUploader
import com.slideindex.app.search.ImageSearchEngine
import com.slideindex.app.search.ImageSearchPostResult
import com.slideindex.app.search.ImageSearchPostUploader
import com.slideindex.app.search.ImageSearchUrlBuilder
import com.slideindex.app.search.toPanelImageSearchEngines
import com.slideindex.app.search.toPreloadImageSearchEngines
import com.slideindex.app.service.WebViewFileChooserBridge
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.overlay.pickresult.PickResultPanelCardShape
import com.slideindex.app.overlay.pickresult.pickResultPanelCard
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.MiuixIcons

private val PANEL_HORIZONTAL_PADDING = 12.dp
private val IMAGE_SEARCH_SCRIM_ALPHA = 0.35f
private const val WEBVIEW_LOG_TAG = "FloatBallImageSearchPanel"
private const val PRELOAD_STAGGER_MS = 300L
private val tagLoadGeneration: Int = "image_search_load_gen".hashCode()
private val tagExpectedEngine: Int = "image_search_expected_engine".hashCode()
private val tagLoadedKey: Int = "image_search_loaded_key".hashCode()

private fun isWaitingForImageUpload(
    engine: ImageSearchEngine,
    preloadedUrls: Map<ImageSearchEngine, String>,
    postResults: Map<ImageSearchEngine, ImageSearchPostResult>,
    uploadFailed: Map<ImageSearchEngine, Boolean>
): Boolean {
    if (uploadFailed[engine] == true) return false
    return when {
        engine.usesHostedUrl -> preloadedUrls[engine] == null
        engine.usesDirectPost -> postResults[engine] == null
        else -> false
    }
}

private enum class EngineTabLoadState {
    IDLE,
    LOADING,
    READY,
    FAILED,
}

private fun resolveEngineTabLoadState(
    engine: ImageSearchEngine,
    mountedEngines: Set<ImageSearchEngine>,
    preloadedUrls: Map<ImageSearchEngine, String>,
    postResults: Map<ImageSearchEngine, ImageSearchPostResult>,
    uploadFailed: Map<ImageSearchEngine, Boolean>,
    loadingByEngine: Map<ImageSearchEngine, Boolean>,
    readyByEngine: Map<ImageSearchEngine, Boolean>
): EngineTabLoadState {
    if (uploadFailed[engine] == true) return EngineTabLoadState.FAILED
    if (engine !in mountedEngines) return EngineTabLoadState.IDLE
    if (readyByEngine[engine] == true) return EngineTabLoadState.READY
    if (
        isWaitingForImageUpload(engine, preloadedUrls, postResults, uploadFailed) ||
        loadingByEngine[engine] == true
    ) {
        return EngineTabLoadState.LOADING
    }
    return EngineTabLoadState.LOADING
}

/**
 * Independent image-search overlay with one WebView per engine, preloaded in the background.
 */
object FloatBallImageSearchPanel {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val webViewsToDestroy = mutableListOf<WebView>()
    private val releasedWebViews = mutableSetOf<WebView>()

    internal fun releaseSearchWebView(webView: WebView) {
        if (!releasedWebViews.add(webView)) return
        webViewsToDestroy.remove(webView)
        safeReleaseWebView(webView)
    }

    private val destroyedWebViewTag: Int = "image_search_destroyed".hashCode()

    private fun safeReleaseWebView(webView: WebView) {
        if (webView.getTag(destroyedWebViewTag) == true) return
        webView.setTag(destroyedWebViewTag, true)
        runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
        runCatching {
            webView.webChromeClient = null
            webView.webViewClient = releaseSafeWebViewClient()
            webView.destroy()
        }
    }

    private fun destroyWebViews() {
        webViewsToDestroy.toList().forEach(::releaseSearchWebView)
        webViewsToDestroy.clear()
    }

    private var composeViewRef = WeakReference<ComposeView>(null)
    private var composeView: ComposeView?
        get() = composeViewRef.get()
        set(value) {
            composeViewRef = WeakReference(value)
        }
    private var owner: OverlayComposeOwner? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
    private var backHandlerRef = WeakReference<OverlayViewBackHandler>(null)
    private var backHandler: OverlayViewBackHandler?
        get() = backHandlerRef.get()
        set(value) {
            backHandlerRef = WeakReference(value)
        }
    private var appContext: android.app.Application? = null

    private var bitmapState: MutableState<Bitmap?>? = null
    private var ownedBitmap: Bitmap? = null
    private var retryTokenState: androidx.compose.runtime.MutableIntState? = null

    private var cachedSourceBitmap: Bitmap? = null
    private var cachedHostedUrl: String? = null

    internal val panelVisible = mutableStateOf(false)
    val isShowing: Boolean get() = panelVisible.value

    private var fileChooserSuppressed = false

    fun suppressForFileChooser() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForFileChooser() }
            return
        }
        if (composeView == null || fileChooserSuppressed) return
        fileChooserSuppressed = true
        composeView?.visibility = View.GONE
    }

    fun restoreAfterFileChooser() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterFileChooser() }
            return
        }
        if (!fileChooserSuppressed) return
        fileChooserSuppressed = false
        if (panelVisible.value) {
            composeView?.visibility = View.VISIBLE
        }
    }

    fun show(context: Context, bitmap: Bitmap) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, bitmap) }
            return
        }
        if (FloatBallTranslatePanel.isShowing) {
            FloatBallTranslatePanel.dismiss()
        }
        
        if (cachedSourceBitmap !== bitmap) {
            cachedSourceBitmap = bitmap
            cachedHostedUrl = null
        }

        val hostContext = overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        ownedBitmap?.recycle()
        val copied = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        ownedBitmap = copied
        bitmapState?.value = copied
        retryTokenState?.intValue = (retryTokenState?.intValue ?: 0) + 1
        panelVisible.value = true
        composeView?.visibility = View.VISIBLE
        FloatBallOverlay.scheduleChromeAbovePanels()
        composeView?.requestFocus()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        panelVisible.value = false
        destroyWebViews()
        WebViewFileChooserBridge.cancelPending()
        ownedBitmap?.recycle()
        ownedBitmap = null
        val view = composeView
        val wm = windowManager
        composeView = null
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        screenOffDismissReceiver.unregister()
        val currentOwner = owner
        OverlayCompose.teardownOverlayCompose(view, currentOwner)
        backHandler?.detach()
        backHandler = null
        owner = null
        layoutParams = null
        windowManager = null
        bitmapState = null
        retryTokenState = null
        appContext = null
        fileChooserSuppressed = false
    }

    private fun updateWindowFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun ensureWindow(context: Context) {
        if (composeView != null) return

        val bitmapHolder = mutableStateOf<Bitmap?>(null)
        val retryTokenHolder = mutableIntStateOf(0)
        bitmapState = bitmapHolder
        retryTokenState = retryTokenHolder

        val cachedUrlHolder = mutableStateOf(cachedHostedUrl)
        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val bitmap by bitmapHolder
                val retryToken by retryTokenHolder
                val cachedUrl by cachedUrlHolder
                val settingsHolder = remember { mutableStateOf(AppSettings()) }
                LaunchedEffect(overlayContext) {
                    val flow = OverlayDependencyAccess.overlayDependencies(overlayContext)
                        ?.settingsRepository
                        ?.settings
                        ?: return@LaunchedEffect
                    flow.collect { settingsHolder.value = it }
                }
                val settings by settingsHolder

                FloatBallImageSearchPanelContent(
                    webViewContext = context,
                    bitmap = bitmap,
                    retryToken = retryToken,
                    cachedHostedUrl = cachedUrl,
                    aggregatedEngineConfigs = settings.aggregatedImageSearchEngines,
                    onUrlUploaded = { url ->
                        cachedUrlHolder.value = url
                        cachedHostedUrl = url
                    },
                    onDismiss = { dismiss() },
                    onRegisterWebView = { webView ->
                        if (!webViewsToDestroy.contains(webView)) {
                            webViewsToDestroy.add(webView)
                        }
                    },
                    onOpenExternal = { url ->
                        runCatching {
                            overlayContext.startActivity(
                                Intent(Intent.ACTION_VIEW, url.toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure {
                            Toast.makeText(
                                overlayContext,
                                R.string.float_ball_action_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: run {
                dialogOwner.destroy()
                return
            }
        val params = buildLayoutParams(context)
        val added = runCatching { wm.addView(compose, params) }.isSuccess
        if (!added) {
            dialogOwner.destroy()
            Log.e(WEBVIEW_LOG_TAG, "failed to add image search panel")
            return
        }
        if (FloatBallOverlay.isShowing) {
            FloatBallOverlay.scheduleChromeAbovePanels(delayMs = 0L)
        }
        OverlayPanelSystemGestureExclusion.attach(compose)
        OverlaySceneController.onContentPanelShown()

        windowManager = wm
        composeView = compose
        owner = dialogOwner
        layoutParams = params
        appContext = context.applicationContext as android.app.Application
        panelVisible.value = true
        backHandler = OverlayViewBackHandler(compose, ::dismiss).also { it.attach() }
        screenOffDismissReceiver.register(context)
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

}

@Composable
private fun FloatBallImageSearchPanelContent(
    webViewContext: Context,
    bitmap: Bitmap?,
    retryToken: Int,
    cachedHostedUrl: String?,
    aggregatedEngineConfigs: List<com.slideindex.app.settings.AggregatedImageSearchEngineConfig>,
    onUrlUploaded: (String) -> Unit,
    onDismiss: () -> Unit,
    onRegisterWebView: (WebView) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val engines = remember(aggregatedEngineConfigs) {
        aggregatedEngineConfigs.toPanelImageSearchEngines()
    }
    val preloadEngines = remember(aggregatedEngineConfigs) {
        aggregatedEngineConfigs.toPreloadImageSearchEngines()
    }
    var selectedEngine by remember(engines) { mutableStateOf(engines.firstOrNull()) }
    val preloadedUrls = remember { mutableStateMapOf<ImageSearchEngine, String>() }
    val postResults = remember { mutableStateMapOf<ImageSearchEngine, ImageSearchPostResult>() }
    val refreshGeneration = remember { mutableStateMapOf<ImageSearchEngine, Int>() }
    val engineWebViews = remember { mutableStateMapOf<ImageSearchEngine, WebView>() }
    val loadingByEngine = remember { mutableStateMapOf<ImageSearchEngine, Boolean>() }
    val canGoBackByEngine = remember { mutableStateMapOf<ImageSearchEngine, Boolean>() }
    val uploadFailed = remember { mutableStateMapOf<ImageSearchEngine, Boolean>() }
    val readyByEngine = remember { mutableStateMapOf<ImageSearchEngine, Boolean>() }
    val retryTokenByEngine = remember { mutableStateMapOf<ImageSearchEngine, Int>() }
    var mountedEngines by remember { mutableStateOf(setOf<ImageSearchEngine>()) }
    var webViewSession by remember { mutableIntStateOf(0) }
    val maxPanelWidth = imageSearchPanelMaxWidth()
    val maxPanelHeight = imageSearchPanelMaxHeight()
    val dismissInteraction = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(engines) {
        if (selectedEngine !in engines) {
            selectedEngine = engines.firstOrNull()
        }
    }

    LaunchedEffect(bitmap, retryToken, engines, preloadEngines) {
        val source = bitmap ?: return@LaunchedEffect
        if (engines.isEmpty()) return@LaunchedEffect
        webViewSession++
        val session = webViewSession
        val orderedPreload = engines.filter { it in preloadEngines }
        val initialEngine = orderedPreload.firstOrNull() ?: engines.first()
        mountedEngines = setOf(initialEngine)
        selectedEngine = initialEngine
        engineWebViews.clear()
        loadingByEngine.clear()
        canGoBackByEngine.clear()
        uploadFailed.clear()
        readyByEngine.clear()
        retryTokenByEngine.clear()
        preloadedUrls.clear()
        postResults.clear()
        refreshGeneration.clear()

        val hostedPreload = orderedPreload.filter { it.usesHostedUrl }
        if (hostedPreload.isNotEmpty() && cachedHostedUrl == null) {
            hostedPreload.forEach { engine ->
                loadingByEngine[engine] = true
                uploadFailed[engine] = false
            }
        }
        val postPreload = orderedPreload.filter { it.usesDirectPost }
        postPreload.forEach { engine ->
            loadingByEngine[engine] = true
            uploadFailed[engine] = false
        }

        coroutineScope.launch {
            if (hostedPreload.isNotEmpty()) {
                ensureHostedSearchUrls(
                    source = source,
                    engines = hostedPreload,
                    cachedHostedUrl = cachedHostedUrl,
                    onUrlUploaded = onUrlUploaded,
                    preloadedUrls = preloadedUrls,
                    loadingByEngine = loadingByEngine,
                    uploadFailed = uploadFailed
                )
            }
        }

        coroutineScope.launch {
            if (postPreload.isNotEmpty()) {
                preloadPostSearchResults(
                    source = source,
                    engines = postPreload,
                    isSessionActive = { session == webViewSession },
                    postResults = postResults,
                    loadingByEngine = loadingByEngine,
                    uploadFailed = uploadFailed
                )
            }
        }

        coroutineScope.launch {
            orderedPreload.drop(1).forEach { engine ->
                delay(PRELOAD_STAGGER_MS)
                if (session != webViewSession) return@launch
                mountedEngines = mountedEngines + engine
            }
        }
    }

    LaunchedEffect(selectedEngine, engineWebViews.size) {
        val activeEngine = selectedEngine ?: return@LaunchedEffect
        engineWebViews.forEach { (engine, webView) ->
            if (engine == activeEngine) {
                webView.onResume()
            }
        }
    }

    mountedEngines.forEach { engine ->
        val webView = engineWebViews[engine]
        val refreshToken = refreshGeneration[engine] ?: 0
        val currentRetryToken = retryTokenByEngine[engine] ?: 0
        LaunchedEffect(
            engine,
            webView,
            preloadedUrls[engine],
            postResults[engine],
            refreshToken,
            currentRetryToken,
            webViewSession,
            bitmap
        ) {
            val view = engineWebViews[engine] ?: return@LaunchedEffect
            if (bitmap == null) return@LaunchedEffect
            val expectedKey = when {
                engine.usesHostedUrl -> {
                    val url = preloadedUrls[engine] ?: return@LaunchedEffect
                    "$webViewSession|${engine.name}|url|$url|$refreshToken|$currentRetryToken"
                }
                engine.usesDirectPost -> {
                    "$webViewSession|${engine.name}|post|$refreshToken|$currentRetryToken"
                }
                else -> return@LaunchedEffect
            }
            if (view.getTag(tagLoadedKey) == expectedKey) return@LaunchedEffect

            if (engine.usesDirectPost) {
                val result = postResults[engine] ?: return@LaunchedEffect
                if (uploadFailed[engine] == true) return@LaunchedEffect
                view.setTag(tagLoadedKey, expectedKey)
                view.loadSearchPostResultAfterLayout(
                    result = result,
                    engine = engine,
                    context = webViewContext,
                    loadId = webViewSession
                )
            } else {
                val url = preloadedUrls[engine] ?: return@LaunchedEffect
                view.setTag(tagLoadedKey, expectedKey)
                view.loadSearchUrlAfterLayout(
                    url = url,
                    engine = engine,
                    context = webViewContext,
                    loadId = webViewSession
                )
            }
        }
    }

    OverlayAwareModuleTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = IMAGE_SEARCH_SCRIM_ALPHA))
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = PANEL_HORIZONTAL_PADDING)
                    .fillMaxWidth()
                    .widthIn(max = maxPanelWidth)
                    .height(maxPanelHeight)
                    .pickResultPanelCard()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = PickResultPanelCardShape
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(vertical = 8.dp)
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.float_ball_image_search_panel_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(MiuixIcons.Medium.Close, contentDescription = stringResource(R.string.shell_panel_close))
                    }
                }

                EngineTabRow(
                    engines = engines,
                    selected = selectedEngine,
                    mountedEngines = mountedEngines,
                    preloadedUrls = preloadedUrls,
                    postResults = postResults,
                    uploadFailed = uploadFailed,
                    loadingByEngine = loadingByEngine,
                    readyByEngine = readyByEngine,
                    onSelected = { engine ->
                        if (engine == ImageSearchEngine.Ascii2d && engine != selectedEngine) {
                            bitmap?.let { source ->
                                coroutineScope.launch {
                                    val saved = withContext(Dispatchers.IO) {
                                        FloatBallTextPick.saveScreenshot(webViewContext, source)
                                    }
                                    Toast.makeText(
                                        webViewContext,
                                        if (saved) {
                                            webViewContext.getString(
                                                R.string.float_ball_image_search_ascii2d_screenshot_saved
                                            )
                                        } else {
                                            webViewContext.getString(R.string.float_ball_action_failed)
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        selectedEngine = engine
                        mountedEngines = mountedEngines + engine
                        val source = bitmap
                        if (source != null && engine.usesDirectPost && postResults[engine] == null) {
                            loadingByEngine[engine] = true
                            uploadFailed[engine] = false
                            coroutineScope.launch {
                                ensurePostSearchResult(
                                    source = source,
                                    engine = engine,
                                    postResults = postResults,
                                    loadingByEngine = loadingByEngine,
                                    uploadFailed = uploadFailed
                                )
                            }
                        }
                        if (source != null && engine.usesHostedUrl && preloadedUrls[engine] == null) {
                            loadingByEngine[engine] = true
                            uploadFailed[engine] = false
                            coroutineScope.launch {
                                ensureHostedSearchUrls(
                                    source = source,
                                    engines = listOf(engine),
                                    cachedHostedUrl = cachedHostedUrl,
                                    onUrlUploaded = onUrlUploaded,
                                    preloadedUrls = preloadedUrls,
                                    loadingByEngine = loadingByEngine,
                                    uploadFailed = uploadFailed
                                )
                            }
                        }
                    }
                )

                if (engines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.float_ball_image_search_no_engines),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                val activeEngine = selectedEngine ?: engines.first()
                val waitingImageUpload = isWaitingForImageUpload(
                    engine = activeEngine,
                    preloadedUrls = preloadedUrls,
                    postResults = postResults,
                    uploadFailed = uploadFailed
                )
                val pageLoading = loadingByEngine[activeEngine] == true && !waitingImageUpload

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(horizontal = 12.dp)
                ) {
                    engines.filter { it in mountedEngines }.forEach { engine ->
                        key(webViewSession, engine) {
                            val isSelected = activeEngine == engine
                            val engineWaitingUpload = isWaitingForImageUpload(
                                engine = engine,
                                preloadedUrls = preloadedUrls,
                                postResults = postResults,
                                uploadFailed = uploadFailed
                            )
                            AndroidView(
                                factory = {
                                    createSearchWebView(
                                        context = webViewContext,
                                        engine = engine,
                                        isFirstEngine = engine == engines.first(),
                                        onRegister = onRegisterWebView,
                                        onPageLoadingChanged = { loading ->
                                            loadingByEngine[engine] = loading
                                            if (loading) {
                                                readyByEngine.remove(engine)
                                            } else if (
                                                !isWaitingForImageUpload(
                                                    engine,
                                                    preloadedUrls,
                                                    postResults,
                                                    uploadFailed
                                                )
                                            ) {
                                                readyByEngine[engine] = true
                                            }
                                        },
                                        onCanGoBackChanged = { canGoBack ->
                                            canGoBackByEngine[engine] = canGoBack
                                        }
                                    ).also { engineWebViews[engine] = it }
                                },
                                update = { webView ->
                                    webView.isClickable = isSelected
                                    webView.isFocusable = isSelected
                                    webView.isFocusableInTouchMode = isSelected
                                },
                                onRelease = { webView ->
                                    if (engineWebViews[engine] == webView) {
                                        engineWebViews.remove(engine)
                                        loadingByEngine.remove(engine)
                                    }
                                    FloatBallImageSearchPanel.releaseSearchWebView(webView)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (isSelected) 1f else 0f)
                                    .alpha(
                                        if (isSelected &&
                                            !engineWaitingUpload &&
                                            readyByEngine[engine] == true
                                        ) {
                                            1f
                                        } else {
                                            0f
                                        }
                                    )
                            )
                        }
                    }

                    if (uploadFailed[activeEngine] == true) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .zIndex(2f)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.float_ball_image_search_upload_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.float_ball_image_search_retry),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    readyByEngine.remove(activeEngine)
                                    retryTokenByEngine[activeEngine] =
                                        (retryTokenByEngine[activeEngine] ?: 0) + 1
                                    val source = bitmap
                                    if (source != null && activeEngine.usesDirectPost) {
                                        coroutineScope.launch {
                                            ensurePostSearchResult(
                                                source = source,
                                                engine = activeEngine,
                                                postResults = postResults,
                                                loadingByEngine = loadingByEngine,
                                                uploadFailed = uploadFailed
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    } else if (waitingImageUpload || pageLoading) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .zIndex(2f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                            if (waitingImageUpload) {
                                Text(
                                    text = stringResource(R.string.float_ball_image_search_uploading),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val externalUrl = when {
                        activeEngine.usesHostedUrl -> preloadedUrls[activeEngine]
                        else -> activeEngine.externalPageUrl
                    }
                    IconButton(
                        onClick = {
                            engineWebViews[activeEngine]?.goBack()
                        },
                        enabled = canGoBackByEngine[activeEngine] == true
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.float_ball_image_search_panel_title))
                    }
                    Text(
                        text = stringResource(R.string.float_ball_image_search_privacy_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                readyByEngine.remove(activeEngine)
                                if (activeEngine == ImageSearchEngine.Google) {
                                    clearSearchSessionCookies()
                                }
                                if (activeEngine.usesDirectPost) {
                                    postResults.remove(activeEngine)
                                    engineWebViews[activeEngine]?.setTag(tagLoadedKey, null)
                                    refreshGeneration[activeEngine] =
                                        (refreshGeneration[activeEngine] ?: 0) + 1
                                    val source = bitmap
                                    if (source != null) {
                                        ensurePostSearchResult(
                                            source = source,
                                            engine = activeEngine,
                                            postResults = postResults,
                                            loadingByEngine = loadingByEngine,
                                            uploadFailed = uploadFailed
                                        )
                                    }
                                } else {
                                    engineWebViews[activeEngine]?.reload()
                                }
                            }
                        },
                        enabled = true
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.float_ball_image_search_refresh))
                    }
                    IconButton(
                        onClick = {
                            externalUrl?.let(onOpenExternal)
                        },
                        enabled = externalUrl != null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.float_ball_image_search_open_browser))
                    }
                }
                }
                }
            }
        }
    }
}

@Composable
private fun EngineTabRow(
    engines: List<ImageSearchEngine>,
    selected: ImageSearchEngine?,
    mountedEngines: Set<ImageSearchEngine>,
    preloadedUrls: Map<ImageSearchEngine, String>,
    postResults: Map<ImageSearchEngine, ImageSearchPostResult>,
    uploadFailed: Map<ImageSearchEngine, Boolean>,
    loadingByEngine: Map<ImageSearchEngine, Boolean>,
    readyByEngine: Map<ImageSearchEngine, Boolean>,
    onSelected: (ImageSearchEngine) -> Unit
) {
    val scrollState = rememberScrollState()
    val readyColor = Color(0xFF2E7D32)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        engines.forEach { engine ->
            val isSelected = engine == selected
            val loadState = resolveEngineTabLoadState(
                engine = engine,
                mountedEngines = mountedEngines,
                preloadedUrls = preloadedUrls,
                postResults = postResults,
                uploadFailed = uploadFailed,
                loadingByEngine = loadingByEngine,
                readyByEngine = readyByEngine
            )
            val statusLabel = when (loadState) {
                EngineTabLoadState.IDLE -> null
                EngineTabLoadState.LOADING -> stringResource(R.string.float_ball_image_search_tab_loading)
                EngineTabLoadState.READY -> stringResource(R.string.float_ball_image_search_tab_ready)
                EngineTabLoadState.FAILED -> stringResource(R.string.float_ball_image_search_tab_failed)
            }
            val tabColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                loadState == EngineTabLoadState.READY -> readyColor.copy(alpha = 0.16f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
            val tabBorder = when (loadState) {
                EngineTabLoadState.READY -> BorderStroke(2.dp, readyColor)
                EngineTabLoadState.FAILED -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                else -> null
            }
            Surface(
                onClick = { onSelected(engine) },
                shape = MaterialTheme.shapes.medium,
                color = tabColor,
                border = tabBorder
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = engine.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            loadState == EngineTabLoadState.READY -> readyColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    when (loadState) {
                        EngineTabLoadState.LOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        EngineTabLoadState.READY -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = statusLabel,
                                modifier = Modifier.size(16.dp),
                                tint = readyColor
                            )
                        }
                        EngineTabLoadState.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = statusLabel,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        EngineTabLoadState.IDLE -> Unit
                    }
                }
            }
        }
    }
}

private suspend fun preloadPostSearchResults(
    source: Bitmap,
    engines: List<ImageSearchEngine>,
    isSessionActive: () -> Boolean,
    postResults: MutableMap<ImageSearchEngine, ImageSearchPostResult>,
    loadingByEngine: MutableMap<ImageSearchEngine, Boolean>,
    uploadFailed: MutableMap<ImageSearchEngine, Boolean>
) {
    val postEngines = engines.filter { it.usesDirectPost }
    if (postEngines.isEmpty()) return
    postEngines.forEach { engine ->
        loadingByEngine[engine] = true
        uploadFailed[engine] = false
    }
    val results = withContext(Dispatchers.IO) {
        ImageSearchPostUploader.searchParallel(source, postEngines)
    }
    if (!isSessionActive()) return
    postEngines.forEach { engine ->
        val result = results[engine]
        if (result != null) {
            postResults[engine] = result
            uploadFailed[engine] = false
        } else {
            uploadFailed[engine] = true
            loadingByEngine[engine] = false
        }
    }
}

private suspend fun ensurePostSearchResult(
    source: Bitmap,
    engine: ImageSearchEngine,
    postResults: MutableMap<ImageSearchEngine, ImageSearchPostResult>,
    loadingByEngine: MutableMap<ImageSearchEngine, Boolean>,
    uploadFailed: MutableMap<ImageSearchEngine, Boolean>
) {
    if (!engine.usesDirectPost || postResults[engine] != null) return
    loadingByEngine[engine] = true
    uploadFailed[engine] = false
    val result = withContext(Dispatchers.IO) {
        ImageSearchPostUploader.search(source, engine)
    }
    if (result != null) {
        postResults[engine] = result
        uploadFailed[engine] = false
    } else {
        uploadFailed[engine] = true
        loadingByEngine[engine] = false
    }
}

private suspend fun ensureHostedSearchUrls(
    source: Bitmap,
    engines: List<ImageSearchEngine>,
    cachedHostedUrl: String?,
    onUrlUploaded: (String) -> Unit,
    preloadedUrls: MutableMap<ImageSearchEngine, String>,
    loadingByEngine: MutableMap<ImageSearchEngine, Boolean>,
    uploadFailed: MutableMap<ImageSearchEngine, Boolean>
) {
    val hostedEngines = engines.filter { it.usesHostedUrl }
    if (hostedEngines.isEmpty()) return
    hostedEngines.forEach { loadingByEngine[it] = true }
    val hostedUrl = cachedHostedUrl ?: withContext(Dispatchers.IO) {
        ImageHostUploader.upload(source)
    }
    if (hostedUrl.isNullOrBlank()) {
        hostedEngines.forEach {
            loadingByEngine[it] = false
            uploadFailed[it] = true
        }
        return
    }
    if (cachedHostedUrl == null) {
        onUrlUploaded(hostedUrl)
    }
    hostedEngines.forEach { engine ->
        preloadedUrls[engine] = ImageSearchUrlBuilder.build(engine, hostedUrl)
        loadingByEngine[engine] = false
        uploadFailed[engine] = false
    }
}

private suspend fun clearSearchSessionCookies() = kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().removeAllCookies {
        CookieManager.getInstance().flush()
        continuation.resumeWith(Result.success(Unit))
    }
}

private fun releaseSafeWebViewClient(
    onRenderProcessGone: (WebView) -> Unit = {},
): WebViewClient = object : RenderProcessSafeWebViewClient() {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRenderProcessGone(view)
        return true
    }
}

@SuppressLint("RestrictedApi", "SetJavaScriptEnabled")
private fun createSearchWebView(
    context: Context,
    engine: ImageSearchEngine,
    isFirstEngine: Boolean,
    onRegister: (WebView) -> Unit,
    onPageLoadingChanged: (Boolean) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit
): WebView {
    return WebView(context).apply {
        if (isFirstEngine) {
            clearCache(true)
        }
        syncSearchUserAgent(context, engine)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : RenderProcessSafeWebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                view?.let { onCanGoBackChanged(it.canGoBack()) }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                onPageLoadingChanged(true)
                view?.let { onCanGoBackChanged(it.canGoBack()) }
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                onPageLoadingChanged(false)
                view?.let { onCanGoBackChanged(it.canGoBack()) }
                Log.d(WEBVIEW_LOG_TAG, "page finished: $finishedUrl")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                Log.w(
                    WEBVIEW_LOG_TAG,
                    "main frame error: ${error?.description} url=${request.url}"
                )
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                response: WebResourceResponse?
            ) {
                val host = request?.url?.host.orEmpty()
                if (host.contains("google") || host.contains("catbox")) {
                    Log.w(
                        WEBVIEW_LOG_TAG,
                        "http ${response?.statusCode} ${request?.url}"
                    )
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null || !request.isForMainFrame) return false
                val targetUrl = request.url?.toString() ?: return false
                return when {
                    targetUrl.startsWith("http://") || targetUrl.startsWith("https://") -> false
                    targetUrl.startsWith("intent:") -> {
                        runCatching {
                            context.startActivity(Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME))
                        }
                        true
                    }
                    else -> false
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                view?.let(FloatBallImageSearchPanel::releaseSearchWebView)
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                return WebViewFileChooserBridge.launch(context, filePathCallback, fileChooserParams)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage ?: return false
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.w(
                        WEBVIEW_LOG_TAG,
                        "console: ${message.message()} @ ${message.sourceId()}:${message.lineNumber()}"
                    )
                }
                return false
            }
        }
        onRegister(this)
    }
}

private fun WebView.loadSearchPostResultAfterLayout(
    result: ImageSearchPostResult,
    engine: ImageSearchEngine,
    context: Context,
    loadId: Int
) {
    fun doLoad() {
        setTag(tagLoadGeneration, loadId)
        setTag(tagExpectedEngine, engine)
        stopLoading()
        syncSearchUserAgent(context, engine)
        onResume()
        CookieManager.getInstance().flush()
        when (result) {
            is ImageSearchPostResult.Html -> {
                Log.d(
                    WEBVIEW_LOG_TAG,
                    "loadData: engine=$engine id=$loadId base=${result.baseUrl} " +
                        "(size=${width}x$height, htmlLen=${result.html.length})"
                )
                loadDataWithBaseURL(result.baseUrl, result.html, "text/html", "UTF-8", null)
            }
            is ImageSearchPostResult.RedirectUrl -> {
                Log.d(
                    WEBVIEW_LOG_TAG,
                    "loadUrl redirect: ${result.url} engine=$engine id=$loadId " +
                        "(size=${width}x$height)"
                )
                loadUrl(result.url)
            }
        }
    }
    post {
        if (width > 0 && height > 0) {
            doLoad()
        } else {
            viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (width <= 0 || height <= 0) return
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                        doLoad()
                    }
                }
            )
        }
    }
}

private fun WebView.loadSearchUrlAfterLayout(
    url: String,
    engine: ImageSearchEngine,
    context: Context,
    loadId: Int
) {
    fun doLoad() {
        setTag(tagLoadGeneration, loadId)
        setTag(tagExpectedEngine, engine)
        stopLoading()
        syncSearchUserAgent(context, engine)
        onResume()
        CookieManager.getInstance().flush()
        Log.d(
            WEBVIEW_LOG_TAG,
            "loadUrl: $url engine=$engine id=$loadId " +
                "(size=${width}x$height, ua=${settings.userAgentString})"
        )
        loadUrl(url)
    }
    post {
        if (width > 0 && height > 0) {
            doLoad()
        } else {
            viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (width <= 0 || height <= 0) return
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                        doLoad()
                    }
                }
            )
        }
    }
}

private fun WebView.syncSearchUserAgent(context: Context, engine: ImageSearchEngine) {
    settings.userAgentString = ImageSearchUrlBuilder.userAgent(context, engine)
}
