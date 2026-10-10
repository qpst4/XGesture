package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.barcode.joinDisplayText
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.overlay.compositor.OverlayCompositor
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.imageeditor.ImageEditorPickReturnContext
import com.slideindex.app.imageeditor.ImageEditorSavedImageDeleteScheduler
import com.slideindex.app.overlay.pickresult.FloatBallPickResultContent
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.overlay.pickresult.PickResultTextModeStore
import com.slideindex.app.overlay.pickresult.preloadPickResultSearchEngineIcons
import com.slideindex.app.overlay.pickresult.resolvePickResultEnterTextMode
import com.slideindex.app.overlay.pickresult.translateErrorMessage
import com.slideindex.app.overlay.searchpanel.SearchPanelQueryBridge
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.service.ShareImageOcrCoordinator
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.PickPanelSlideAnimationDefaults
import com.slideindex.app.settings.PickResultTextModeDefault
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashMetaRepository
import com.slideindex.app.util.HapticHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FloatBallPickResultPanel {
    private const val TAG = "FloatBallPickPanel"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val historyOcrScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var historyOcrJob: Job? = null
    private var historyOcrRequestId = 0

    /** 「记住上次」写回用；独立于面板生命周期，避免 dismiss 后协程被取消。 */
    private val textModePersistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 本进程内最近一次「点词」状态：DataStore 回流有延迟，重开面板时以它为准。 */
    private var rememberedTextMode: PickResultTextMode? = null

    /** 已交给 DataStore 的值，避免同一状态重复写盘。 */
    private var persistedTextMode: PickResultTextMode? = null

    private var composeViewRef = java.lang.ref.WeakReference<ComposeView>(null)
    private var composeView: ComposeView?
        get() = composeViewRef.get()
        set(value) {
            composeViewRef = java.lang.ref.WeakReference(value)
        }
    private var owner: OverlayComposeOwner? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var backHandlerRef = java.lang.ref.WeakReference<OverlayViewBackHandler>(null)
    private var backHandler: OverlayViewBackHandler?
        get() = backHandlerRef.get()
        set(value) {
            backHandlerRef = java.lang.ref.WeakReference(value)
        }
    private var appContext: android.app.Application? = null

    private var textState: MutableState<String?>? = null
    private var screenshotState: MutableState<Bitmap?>? = null
    private var panelImagesState: MutableState<List<Bitmap>>? = null
    private var currentImageIndexState: MutableState<Int>? = null
    private var contentOriginState: MutableState<PickResultContentOrigin>? = null
    private var ownsPanelImagesState: MutableState<Boolean>? = null
    private var ocrTextsByImageIndexState: MutableState<Map<Int, String>>? = null
    private var activeTextState: MutableState<String>? = null
    private var textModeState: MutableState<PickResultTextMode>? = null
    private var a11yTextState: MutableState<String?>? = null
    private var ocrTextState: MutableState<String?>? = null
    private var textSourceState: MutableState<PickResultTextSource>? = null
    private var ocrAvailableState: MutableState<Boolean>? = null
    private var ocrLoadingState: MutableState<Boolean>? = null
    private var a11ySourceEnabledState: MutableState<Boolean>? = null
    private var isShareImageOcrState: MutableState<Boolean>? = null
    private var screenRectState: MutableState<Rect?>? = null
    private var layoutMetaState: MutableState<ScreenshotLayoutMeta?>? = null
    private var barcodeResultsState: MutableState<List<BarcodeScanResult>>? = null
    private var showingTranslationState: MutableState<Boolean>? = null
    private var translateLoadingState: MutableState<Boolean>? = null
    private var ocrSwitchOnComplete = false
    private var captureSuppressed = false
    private var captureDetached = false
    private var pickPanelVisible = false
    private var panelDismissing = false
    private var panelVisibilityState: androidx.compose.animation.core.MutableTransitionState<Boolean>? = null
    private var panelShowTokenState: androidx.compose.runtime.MutableIntState? = null
    private var settingsState: MutableState<AppSettings>? = null
    private var panelRevealedState: MutableState<Boolean>? = null
    private var panelRevealGeneration = 0

    val isShowing: Boolean get() = pickPanelVisible

    private fun readPanelSettings(context: Context): AppSettings =
        OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
            ?: AppSettings()

    private fun preparePanelReveal(context: Context, onReady: () -> Unit) {
        val settings = readPanelSettings(context)
        settingsState?.value = settings
        val engines = SearchEngineStore.textPickPanelEngines(settings.searchEngines)
        if (engines.isEmpty()) {
            onReady()
            return
        }
        val hostContext = appContext ?: context.applicationContext
        Thread {
            preloadPickResultSearchEngineIcons(hostContext, engines)
            mainHandler.post(onReady)
        }.start()
    }

    private fun preparePanelWhileLoading(context: Context) {
        panelVisibilityState?.targetState = false
        panelRevealedState?.value = false
        preparePanelReveal(context) { }
    }

    private fun revealPanelAnimated(context: Context) {
        panelRevealGeneration++
        val generation = panelRevealGeneration
        panelVisibilityState?.targetState = true
        panelRevealedState?.value = false
        val view = composeView ?: return
        view.post {
            if (generation != panelRevealGeneration) return@post
            view.post {
                if (generation != panelRevealGeneration) return@post
                preparePanelReveal(context) {
                    if (generation != panelRevealGeneration) return@preparePanelReveal
                    panelRevealedState?.value = true
                }
            }
        }
    }

    fun warmUp(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { warmUp(context) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        if (!pickPanelVisible) {
            applyPanelShellPassive()
        }
        preparePanelReveal(hostContext) { }
    }

    /** Tear down invisible warm-up shell so it cannot block touches after drag cancel. */
    fun releaseWarmUpShell() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { releaseWarmUpShell() }
            return
        }
        if (pickPanelVisible || panelDismissing) return
        applyPanelShellPassive()
    }

    /** 应用内语言切换后销毁预热壳，下次取词时用新 Locale 重建（与 [SearchPanelOverlayWindow.releaseWarmUp] 对齐）。 */
    fun releaseWarmUpForLocale() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { releaseWarmUpForLocale() }
            return
        }
        destroy()
    }

    fun suppressForScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForScreenshotCapture() }
            return
        }
        if (composeView == null || captureSuppressed) return
        captureSuppressed = true
        val wm = windowManager ?: return
        composeView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { wm.removeViewImmediate(view) }
                captureDetached = true
            }
        }
    }

    fun restoreAfterScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterScreenshotCapture() }
            return
        }
        if (!captureSuppressed) return
        captureSuppressed = false
        if (captureDetached) {
            val wm = windowManager
            val view = composeView
            val params = layoutParams
            if (wm != null && view != null && params != null && !view.isAttachedToWindow && pickPanelVisible) {
                runCatching { wm.addView(view, params) }
            }
            captureDetached = false
        } else if (pickPanelVisible) {
            composeView?.visibility = View.VISIBLE
        }
    }

    private fun updateWindowFocusableForMode(mode: PickResultTextMode) {
        updateWindowFocusable(focusable = true)
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        @Suppress("DEPRECATION")
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun isPanelImeVisible(): Boolean {
        val view = composeView ?: return false
        val insets = ViewCompat.getRootWindowInsets(view) ?: return false
        return insets.isVisible(WindowInsetsCompat.Type.ime())
    }

    private fun hidePanelKeyboard() {
        val view = composeView ?: return
        val imm = appContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
        view.post { view.requestFocus() }
    }

    private fun exitEditModeFromBack() {
        hidePanelKeyboard()
        textModeState?.value = PickResultTextMode.WORD_TAP
        rememberTextModeForNextOpen(PickResultTextMode.WORD_TAP)
    }

    private fun handlePanelBack() {
        if (textModeState?.value == PickResultTextMode.EDIT) {
            if (isPanelImeVisible()) {
                hidePanelKeyboard()
                return
            }
            exitEditModeFromBack()
            return
        }
        when {
            FloatBallImageSearchPanel.isShowing -> FloatBallImageSearchPanel.dismiss()
            FloatBallTranslatePanel.isShowing -> FloatBallTranslatePanel.dismiss()
            else -> dismiss()
        }
    }

    /** Sidebar / accessibility Back while the pick panel is visible. */
    internal fun handleSidebarBack() {
        if (!isShowing) return
        handlePanelBack()
    }

    /** Re-bind back handling after predictive-back setting changes while the panel is visible. */
    internal fun refreshBackHandlingIfShowing() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshBackHandlingIfShowing() }
            return
        }
        if (!pickPanelVisible || composeView?.visibility != View.VISIBLE) return
        activatePanelBackHandling()
    }

    internal fun requestPanelFocus() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestPanelFocus() }
            return
        }
        composeView?.requestFocus()
    }

    fun showResult(
        context: Context,
        anchorX: Float = 0f,
        anchorY: Float = 0f,
        result: FloatBallPickResult,
        initialTextMode: PickResultTextMode? = null,
        ocrTextsByImageIndex: Map<Int, String>? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                showResult(context, anchorX, anchorY, result, initialTextMode, ocrTextsByImageIndex)
            }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        captureSuppressed = false
        captureDetached = false
        panelDismissing = false
        pickPanelVisible = true
        composeView?.visibility = View.VISIBLE
        val resolvedImages = result.resolvedImages()
        val awaitingDeferredScreenshot = resolvedImages.isEmpty() && result.screenshot == null
        if (awaitingDeferredScreenshot) {
            FloatBallOverlay.scheduleChromeAbovePanelsAfterDeferredPickScreenshot()
        } else {
            FloatBallOverlay.cancelPickPanelChromeRaiseDeferred()
            FloatBallOverlay.scheduleChromeAbovePanels()
        }
        OverlaySceneController.onContentPanelShown()
        OverlayCompositor.bringAboveContentPanels()
        composeView?.requestFocus()
        a11yTextState?.value = result.a11yText
        ocrTextState?.value = result.ocrText
        textSourceState?.value = result.activeSource
        ocrAvailableState?.value = result.ocrAvailable
        ocrLoadingState?.value = result.ocrPending
        a11ySourceEnabledState?.value = result.a11ySourceEnabled
        isShareImageOcrState?.value = result.isShareImageOcr
        ocrSwitchOnComplete = result.ocrPreferSwitchOnComplete
        val safeImageIndex = result.initialImageIndex.coerceIn(0, (resolvedImages.size - 1).coerceAtLeast(0))
        panelImagesState?.value = resolvedImages
        currentImageIndexState?.value = safeImageIndex
        contentOriginState?.value = result.contentOrigin
        ownsPanelImagesState?.value = result.ownsImages
        ocrTextsByImageIndexState?.value = ocrTextsByImageIndex
            ?: result.ocrText
                ?.takeIf { it.isNotBlank() }
                ?.let { mapOf(safeImageIndex to it) }
            ?: emptyMap()
        textState?.value = result.text
        activeTextState?.value = result.text.orEmpty()
        screenshotState?.value?.takeIf { it !in resolvedImages }?.recycle()
        screenshotState?.value = resolvedImages.getOrNull(safeImageIndex) ?: result.screenshot
        screenRectState?.value = result.screenRect?.let { Rect(it) }
        layoutMetaState?.value = result.layoutMeta
        barcodeResultsState?.value = result.barcodeResults
        clearTranslateState()
        val enterTextMode = initialTextMode ?: defaultTextModeFor(result.text)
        textModeState?.value = enterTextMode
        updateWindowFocusableForMode(enterTextMode)
        panelShowTokenState?.let { it.intValue++ }
        if (panelRevealedState?.value == true) {
            panelVisibilityState?.targetState = true
        } else {
            revealPanelAnimated(hostContext)
        }
        if (result.text.isNullOrBlank() && resolvedImages.isEmpty()) {
            Toast.makeText(hostContext, R.string.float_ball_text_not_found, Toast.LENGTH_SHORT).show()
            dismiss()
        }
        PickPerf.mark("panel_showResult_done", "source=${result.activeSource}")
    }

    fun capturePickReturnContextForEditor(): ImageEditorPickReturnContext? {
        if (!pickPanelVisible) return null
        val images = panelImagesState?.value.orEmpty().ifEmpty { listOfNotNull(screenshotState?.value) }
        if (images.isEmpty()) return null
        val index = (currentImageIndexState?.value ?: 0).coerceIn(0, images.lastIndex)
        val copies = images.mapNotNull { bitmap ->
            if (bitmap.isRecycled) null else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        if (copies.isEmpty()) return null
        val safeIndex = index.coerceIn(0, copies.lastIndex)
        return ImageEditorPickReturnContext(
            a11yText = a11yTextState?.value,
            ocrText = ocrTextState?.value,
            activeSource = textSourceState?.value ?: PickResultTextSource.A11Y,
            ocrAvailable = ocrAvailableState?.value == true,
            ocrPending = ocrLoadingState?.value == true,
            ocrPreferSwitchOnComplete = ocrSwitchOnComplete,
            a11ySourceEnabled = a11ySourceEnabledState?.value != false,
            isShareImageOcr = isShareImageOcrState?.value == true,
            barcodeResults = barcodeResultsState?.value.orEmpty(),
            contentOrigin = contentOriginState?.value ?: PickResultContentOrigin.SCREEN_PICK,
            contentKind = null,
            imageCopies = copies,
            editedImageIndex = safeIndex,
            ocrTextsByImageIndex = ocrTextsByImageIndexState?.value.orEmpty(),
            textMode = textModeState?.value,
        )
    }

    fun updatePickScreenshot(
        bitmap: Bitmap,
        screenRect: Rect?,
        layoutMeta: ScreenshotLayoutMeta? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePickScreenshot(bitmap, screenRect, layoutMeta) }
            return
        }
        if (!isShowing) {
            bitmap.recycle()
            return
        }
        screenshotState?.value?.let { current ->
            val owned = panelImagesState?.value.orEmpty()
            if (current !in owned) {
                current.recycle()
            }
        }
        recycleOwnedPanelImages()
        ownsPanelImagesState?.value = true
        panelImagesState?.value = listOf(bitmap)
        currentImageIndexState?.value = 0
        screenshotState?.value = bitmap
        screenRectState?.value = screenRect?.let { Rect(it) }
        layoutMetaState?.value = layoutMeta
        PickPerf.mark("panel_screenshot_updated")
        FloatBallOverlay.onPickPanelScreenshotApplied()
    }

    fun showLoading(
        context: Context,
        anchorX: Float = 0f,
        anchorY: Float = 0f,
        loadingSource: PickResultTextSource = PickResultTextSource.OCR
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showLoading(context, anchorX, anchorY, loadingSource) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        captureSuppressed = false
        captureDetached = false
        panelDismissing = false
        pickPanelVisible = true
        composeView?.visibility = View.GONE
        a11yTextState?.value = null
        ocrTextState?.value = null
        textSourceState?.value = loadingSource
        isShareImageOcrState?.value = false
        textState?.value = null
        activeTextState?.value = ""
        screenshotState?.value?.let { current ->
            val owned = panelImagesState?.value.orEmpty()
            if (current !in owned) {
                current.recycle()
            }
        }
        screenshotState?.value = null
        recycleOwnedPanelImages()
        historyOcrJob?.cancel()
        historyOcrRequestId++
        ocrTextsByImageIndexState?.value = emptyMap()
        contentOriginState?.value = PickResultContentOrigin.SCREEN_PICK
        currentImageIndexState?.value = 0
        screenRectState?.value = null
        layoutMetaState?.value = null
        barcodeResultsState?.value = emptyList()
        clearTranslateState()
        // 加载态先按设置的默认模式占位：OCR 完成时若未显式指定模式（updateOcrText 的 initialTextMode
        // 为 null），这里就是用户实际看到/使用的模式。
        textModeState?.value = defaultTextModeFor(null)
        when (loadingSource) {
            PickResultTextSource.A11Y -> {
                a11ySourceEnabledState?.value = true
                ocrAvailableState?.value = false
                ocrLoadingState?.value = false
                ocrSwitchOnComplete = false
            }
            PickResultTextSource.OCR -> {
                a11ySourceEnabledState?.value = false
                ocrAvailableState?.value = false
                ocrLoadingState?.value = true
                ocrSwitchOnComplete = true
            }
            PickResultTextSource.BARCODE -> {
                a11ySourceEnabledState?.value = false
                ocrAvailableState?.value = false
                ocrLoadingState?.value = false
                ocrSwitchOnComplete = false
            }
        }
        applyPanelShellPassive()
        preparePanelWhileLoading(hostContext)
        PickPerf.mark("panel_showLoading", "source=$loadingSource")
    }

    fun updateOcrText(
        ocrText: String,
        switchToOcr: Boolean = ocrSwitchOnComplete,
        initialTextMode: PickResultTextMode? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateOcrText(ocrText, switchToOcr, initialTextMode) }
            return
        }
        ocrLoadingState?.value = false
        ocrTextState?.value = ocrText
        ocrAvailableState?.value = true
        if (switchToOcr || textSourceState?.value == PickResultTextSource.OCR) {
            clearTranslateState()
            textSourceState?.value = PickResultTextSource.OCR
            textState?.value = ocrText
            activeTextState?.value = ocrText
        }
        initialTextMode?.let { mode ->
            textModeState?.value = mode
            updateWindowFocusableForMode(mode)
        }
        PickPerf.mark("panel_ocr_updated", "len=${ocrText.length}")
    }

    fun updateBarcodeResults(barcodeResults: List<BarcodeScanResult>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateBarcodeResults(barcodeResults) }
            return
        }
        if (!isShowing || barcodeResults.isEmpty()) return
        barcodeResultsState?.value = barcodeResults
        PickPerf.mark("panel_barcode_updated", "count=${barcodeResults.size}")
    }

    fun isShowingTranslation(): Boolean = showingTranslationState?.value == true

    fun showTranslateLoading() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateLoading() }
            return
        }
        if (!isShowing) return
        showingTranslationState?.value = false
        translateLoadingState?.value = true
    }

    fun showTranslateResult(translatedText: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateResult(translatedText) }
            return
        }
        if (!isShowing) return
        translateLoadingState?.value = false
        showingTranslationState?.value = true
        textState?.value = translatedText
        activeTextState?.value = translatedText
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    fun showTranslateError(context: Context, message: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateError(context, message) }
            return
        }
        if (!isShowing) return
        translateLoadingState?.value = false
        showingTranslationState?.value = false
        val hostContext = appContext ?: context.applicationContext
        Toast.makeText(
            hostContext,
            translateErrorMessage(hostContext, message),
            Toast.LENGTH_SHORT
        ).show()
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    fun restoreFromTranslation() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreFromTranslation() }
            return
        }
        if (!isShowing) return
        showingTranslationState?.value = false
        translateLoadingState?.value = false
        applyTextForCurrentSource()
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    private fun applyTextForCurrentSource() {
        val source = textSourceState?.value ?: PickResultTextSource.A11Y
        val text = textForSource(
            source = source,
            a11yText = a11yTextState?.value,
            ocrText = ocrTextState?.value,
            barcodeResults = barcodeResultsState?.value.orEmpty()
        )
        textState?.value = text
        activeTextState?.value = text
    }

    private fun textForSource(
        source: PickResultTextSource,
        a11yText: String?,
        ocrText: String?,
        barcodeResults: List<BarcodeScanResult>
    ): String = when (source) {
        PickResultTextSource.A11Y -> a11yText.orEmpty()
        PickResultTextSource.OCR -> ocrText.orEmpty()
        PickResultTextSource.BARCODE -> barcodeResults.joinDisplayText()
    }

    private fun clearTranslateState() {
        showingTranslationState?.value = false
        translateLoadingState?.value = false
    }

    fun finishOcrPending() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { finishOcrPending() }
            return
        }
        ocrLoadingState?.value = false
        PickPerf.mark("panel_ocr_pending_done", "empty=true")
    }

    fun showOcrError(context: Context, message: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showOcrError(context, message) }
            return
        }
        ocrLoadingState?.value = false
        val hostContext = appContext ?: context.applicationContext
        Toast.makeText(hostContext, message, Toast.LENGTH_SHORT).show()
    }

    fun requestHistoryImageOcr(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestHistoryImageOcr(context) }
            return
        }
        requestOcrForImageIndex(context, currentImageIndexState?.value ?: 0)
    }

    internal fun setCurrentImageIndex(context: Context, index: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setCurrentImageIndex(context, index) }
            return
        }
        val images = panelImagesState?.value.orEmpty()
        if (images.isEmpty()) return
        val safeIndex = index.coerceIn(0, images.lastIndex)
        if (currentImageIndexState?.value == safeIndex && screenshotState?.value === images[safeIndex]) {
            return
        }
        currentImageIndexState?.value = safeIndex
        screenshotState?.value = images[safeIndex]
        if (contentOriginState?.value == PickResultContentOrigin.STASH_CLIPBOARD &&
            textSourceState?.value == PickResultTextSource.OCR
        ) {
            applyHistoryOcrForIndex(context, safeIndex)
        }
    }

    private fun requestOcrForImageIndex(context: Context, index: Int) {
        if (contentOriginState?.value != PickResultContentOrigin.STASH_CLIPBOARD) return
        applyHistoryOcrForIndex(context, index)
    }

    private fun applyHistoryOcrForIndex(context: Context, index: Int) {
        val images = panelImagesState?.value.orEmpty()
        val bitmap = images.getOrNull(index) ?: return
        ocrTextsByImageIndexState?.value?.get(index)?.let { cached ->
            ocrLoadingState?.value = false
            ocrAvailableState?.value = true
            ocrTextState?.value = cached
            if (textSourceState?.value == PickResultTextSource.OCR) {
                clearTranslateState()
                textState?.value = cached
                activeTextState?.value = cached
            }
            return
        }
        val appContext = appContext ?: context.applicationContext
        val settings = OverlayDependencyAccess.overlayDependencies(appContext)
            ?.settingsRepository
            ?.readSnapshot()
            ?: return
        val modelId = settings.floatBallOcrModelId
        if (modelId.isBlank() || !settings.floatBallOcrFallbackEnabled) return
        if (OcrDependencyAccess.modelRepository(appContext)?.isInstalled(modelId) != true) return

        val requestId = ++historyOcrRequestId
        historyOcrJob?.cancel()
        ocrLoadingState?.value = true
        historyOcrJob = historyOcrScope.launch(Dispatchers.IO) {
            val result = runCatching {
                RegionalScreenshotOcr.recognizeBitmapPublic(appContext, modelId, bitmap)
            }.getOrElse {
                com.slideindex.app.ocr.OcrRecognizeResult.Failure(
                    appContext.getString(
                        R.string.ocr_error_recognition_failed,
                        it.localizedMessage ?: it.message ?: appContext.getString(R.string.ocr_error_unknown)
                    )
                )
            }
            withContext(Dispatchers.Main.immediate) {
                if (requestId != historyOcrRequestId) return@withContext
                when (result) {
                    is com.slideindex.app.ocr.OcrRecognizeResult.Success -> {
                        val recognized = result.text.trim()
                        ocrLoadingState?.value = false
                        ocrTextsByImageIndexState?.value =
                            (ocrTextsByImageIndexState?.value ?: emptyMap()) + (index to recognized)
                        ocrAvailableState?.value = true
                        ocrTextState?.value = recognized
                        if (textSourceState?.value == PickResultTextSource.OCR) {
                            clearTranslateState()
                            textState?.value = recognized
                            activeTextState?.value = recognized
                        }
                    }
                    is com.slideindex.app.ocr.OcrRecognizeResult.Failure -> {
                        ocrLoadingState?.value = false
                        showOcrError(context, result.reason)
                    }
                }
            }
        }
    }

    private fun recycleOwnedPanelImages() {
        if (ownsPanelImagesState?.value != true) return
        val images = panelImagesState?.value.orEmpty()
        images.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        panelImagesState?.value = emptyList()
        ownsPanelImagesState?.value = false
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        if (panelDismissing) return
        if (!pickPanelVisible) {
            releaseWarmUpShell()
            return
        }
        panelDismissing = true
        pickPanelVisible = false
        // 「记住上次」：面板隐藏时记录当前模式，下次进入按它还原。
        textModeState?.value?.let { rememberTextModeForNextOpen(it) }
        FloatBallOverlay.cancelPickPanelChromeRaiseDeferred()
        OverlaySceneController.onContentPanelHidden()
        panelRevealGeneration++
        panelRevealedState?.value = false

        val currentOwner = owner
        val view = composeView
        val wm = windowManager
        if (currentOwner != null && view != null && wm != null) {
            val exitAnimationMs = settingsState?.value?.floatBallPickPanelExitAnimationMs
                ?: PickPanelSlideAnimationDefaults.DEFAULT_MS
            currentOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.delay(exitAnimationMs.toLong())
                if (pickPanelVisible) {
                    panelDismissing = false
                    return@launch // Abort if re-shown
                }

                panelVisibilityState?.targetState = false
                if (pickPanelVisible) {
                    panelDismissing = false
                    return@launch // Abort if re-shown
                }

                screenshotState?.value?.let { current ->
                    val owned = panelImagesState?.value.orEmpty()
                    if (current !in owned) {
                        current.recycle()
                    }
                }
                screenshotState?.value = null
                recycleOwnedPanelImages()
                historyOcrJob?.cancel()
                historyOcrRequestId++
                ocrTextsByImageIndexState?.value = emptyMap()
                contentOriginState?.value = PickResultContentOrigin.SCREEN_PICK
                view.visibility = View.GONE
                if (FloatBallImageSearchPanel.isShowing) {
                    FloatBallImageSearchPanel.dismiss()
                }
                clearTranslateState()
                applyPanelShellPassive()
                panelDismissing = false
                com.slideindex.app.service.SlideIndexAccessibilityService.recoverTriggerInteraction(forceReAddChrome = true)
                com.slideindex.app.service.SlideIndexAccessibilityService.refreshOverlaySuppression()
            }
        } else {
            panelDismissing = false
        }
    }

    fun destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { destroy() }
            return
        }
        Log.i(TAG, "destroy invoked pickPanelVisible=$pickPanelVisible")
        val currentOwner = owner
        val view = composeView
        val wm = windowManager
        if (currentOwner != null && view != null && wm != null) {
            runCatching { wm.removeView(view) }
            screenOffReceiver?.let { receiver ->
                appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
            }
            runCatching { currentOwner.destroy() }

            backHandler?.detach()
            backHandler = null
            owner = null
            composeView = null
            layoutParams = null
            windowManager = null
            textState = null
            recycleOwnedPanelImages()
            screenshotState = null
            panelImagesState = null
            currentImageIndexState = null
            contentOriginState = null
            ownsPanelImagesState = null
            ocrTextsByImageIndexState = null
            historyOcrJob?.cancel()
            historyOcrRequestId = 0
            activeTextState = null
            textModeState = null
            a11yTextState = null
            ocrTextState = null
            textSourceState = null
            ocrAvailableState = null
            ocrLoadingState = null
            a11ySourceEnabledState = null
            isShareImageOcrState = null
            screenRectState = null
            layoutMetaState = null
            barcodeResultsState = null
            showingTranslationState = null
            translateLoadingState = null
            panelVisibilityState = null
            panelShowTokenState = null
            settingsState = null
            panelRevealedState = null
            ocrSwitchOnComplete = false
            screenOffReceiver = null
            appContext = null
            pickPanelVisible = false
            panelDismissing = false
            captureSuppressed = false
            captureDetached = false
        }
    }

    private fun updateWindowFocusable(focusable: Boolean) {
        if (focusable) {
            applyPanelShellActive(focusable = true)
        } else {
            applyPanelShellPassive()
        }
    }

    /** Invisible prefetch shell: must not intercept touches beneath float-ball chrome. */
    private fun applyPanelShellPassive() {
        deactivatePanelBackHandling()
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        view.clearFocus()
        view.visibility = View.GONE
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun applyPanelShellActive(focusable: Boolean = true) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
        if (focusable) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
            activatePanelBackHandling()
        } else {
            deactivatePanelBackHandling()
        }
    }

    private fun activatePanelBackHandling() {
        val view = composeView ?: return
        backHandler?.detach()
        backHandler = OverlayViewBackHandler(view, ::handlePanelBack).also { it.attach() }
    }

    private fun deactivatePanelBackHandling() {
        backHandler?.detach()
        backHandler = null
    }

    private fun ensureWindow(context: Context) {
        if (composeView != null) return

        val textHolder = mutableStateOf<String?>(null)
        val screenshotHolder = mutableStateOf<Bitmap?>(null)
        val panelImagesHolder = mutableStateOf<List<Bitmap>>(emptyList())
        val currentImageIndexHolder = mutableIntStateOf(0)
        val contentOriginHolder = mutableStateOf(PickResultContentOrigin.SCREEN_PICK)
        val ownsPanelImagesHolder = mutableStateOf(false)
        val ocrTextsByImageIndexHolder = mutableStateOf<Map<Int, String>>(emptyMap())
        val activeTextHolder = mutableStateOf("")
        val textModeHolder = mutableStateOf(PickResultTextMode.WORD_TAP)
        val a11yTextHolder = mutableStateOf<String?>(null)
        val ocrTextHolder = mutableStateOf<String?>(null)
        val textSourceHolder = mutableStateOf(PickResultTextSource.A11Y)
        val ocrAvailableHolder = mutableStateOf(false)
        val ocrLoadingHolder = mutableStateOf(false)
        val a11ySourceEnabledHolder = mutableStateOf(true)
        val isShareImageOcrHolder = mutableStateOf(false)
        val screenRectHolder = mutableStateOf<Rect?>(null)
        val layoutMetaHolder = mutableStateOf<ScreenshotLayoutMeta?>(null)
        val barcodeResultsHolder = mutableStateOf<List<BarcodeScanResult>>(emptyList())
        val showingTranslationHolder = mutableStateOf(false)
        val translateLoadingHolder = mutableStateOf(false)
        textState = textHolder
        screenshotState = screenshotHolder
        panelImagesState = panelImagesHolder
        currentImageIndexState = currentImageIndexHolder
        contentOriginState = contentOriginHolder
        ownsPanelImagesState = ownsPanelImagesHolder
        ocrTextsByImageIndexState = ocrTextsByImageIndexHolder
        activeTextState = activeTextHolder
        textModeState = textModeHolder
        a11yTextState = a11yTextHolder
        ocrTextState = ocrTextHolder
        textSourceState = textSourceHolder
        ocrAvailableState = ocrAvailableHolder
        ocrLoadingState = ocrLoadingHolder
        a11ySourceEnabledState = a11ySourceEnabledHolder
        isShareImageOcrState = isShareImageOcrHolder
        screenRectState = screenRectHolder
        layoutMetaState = layoutMetaHolder
        barcodeResultsState = barcodeResultsHolder
        showingTranslationState = showingTranslationHolder
        translateLoadingState = translateLoadingHolder
        val initialSettings = readPanelSettings(context)
        val settingsHolder = mutableStateOf(initialSettings)
        settingsState = settingsHolder
        val panelRevealedHolder = mutableStateOf(false)
        panelRevealedState = panelRevealedHolder

        panelVisibilityState = androidx.compose.animation.core.MutableTransitionState(false)
        val panelShowTokenHolder = mutableIntStateOf(0)
        panelShowTokenState = panelShowTokenHolder
        
        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val visibleState = panelVisibilityState ?: return@setContent
                if (!visibleState.currentState && !visibleState.targetState) return@setContent
                OverlayTextToolbarProvider {
                val localView = LocalView.current
                val panelShowToken = panelShowTokenHolder.intValue
                val panelRevealed by panelRevealedHolder
                val panelNotificationHolder = remember { mutableStateOf<String?>(null) }
                val showInPanelMessage: (String?) -> Unit = { msg ->
                    panelNotificationHolder.value = msg
                }
                val text by textHolder
                val screenshot by screenshotHolder
                val panelImages by panelImagesHolder
                val currentImageIndex by currentImageIndexHolder
                val contentOrigin by contentOriginHolder
                val activeText by activeTextHolder
                val textMode by textModeHolder
                val ocrText by ocrTextHolder
                val textSource by textSourceHolder
                val ocrAvailable by ocrAvailableHolder
                val ocrLoading by ocrLoadingHolder
                val a11ySourceEnabled by a11ySourceEnabledHolder
                val isShareImageOcr by isShareImageOcrHolder
                val screenRect by screenRectHolder
                val layoutMeta by layoutMetaHolder
                val barcodeResults by barcodeResultsHolder
                val showingTranslation by showingTranslationHolder
                val translateLoading by translateLoadingHolder
                val settings by settingsHolder
                LaunchedEffect(overlayContext) {
                    val flow = OverlayDependencyAccess.overlayDependencies(overlayContext)
                        ?.settingsRepository
                        ?.settings
                        ?: return@LaunchedEffect
                    flow.collect { settingsHolder.value = it }
                }
                LaunchedEffect(text) {
                    if (text != null) {
                        activeTextHolder.value = text.orEmpty()
                    }
                }
                FloatBallPickResultContent(
                    panelShowToken = panelShowToken,
                    panelRevealed = panelRevealed,
                    panelNotification = panelNotificationHolder.value,
                    onShowInPanelMessage = showInPanelMessage,
                    text = text,
                    screenshot = screenshot,
                    panelImages = panelImages,
                    currentImageIndex = currentImageIndex,
                    contentOrigin = contentOrigin,
                    activeText = activeText,
                    textMode = textMode,
                    textSource = textSource,
                    ocrAvailable = ocrAvailable,
                    a11yAvailable = a11ySourceEnabled,
                    ocrLoading = ocrLoading,
                    isShareImageOcr = isShareImageOcr,
                    barcodeResults = barcodeResults,
                    showingTranslation = showingTranslation,
                    translateLoading = translateLoading,
                    onBackgroundOcr = {
                        ShareImageOcrCoordinator.moveToBackground(overlayContext)
                    },
                    imageSearchPickPanelTransparency = settings.floatBallImageSearchPickPanelTransparency,
                    textSizeSp = settings.floatBallPickTextSizeSp,
                    searchEngines = settings.searchEngines,
                    searchEngineGridColumns = settings.searchEngineGridColumns,
                    searchEngineGridRows = settings.searchEngineGridRows,
                    searchEngineShowLabels = settings.searchEngineShowLabels,
                    appSettings = settings,
                    onImageClick = {
                        // 这个回调不能把异常抛回主线程：浮层与无障碍服务同在 :overlay 进程，
                        // 一个未捕获异常就会连无障碍一起带走（真机踩过：弹窗布局解析失败 → 无障碍掉线）。
                        runCatching {
                            screenshot?.let { bmp ->
                                val ctx = appContext ?: overlayContext
                                val meta = layoutMeta ?: buildScreenshotLayoutMeta(
                                    bitmap = bmp,
                                    screenWidthPx = overlayContext.resources.displayMetrics.widthPixels,
                                    screenHeightPx = overlayContext.resources.displayMetrics.heightPixels,
                                )
                                val pickReturnContext = capturePickReturnContextForEditor()
                                val opened = FloatBallTextPick.viewScreenshot(
                                    ctx,
                                    bmp,
                                    settings.defaultImageViewerPackage,
                                    screenRect,
                                    meta,
                                    pickReturnContext,
                                )
                                if (opened) {
                                    dismiss()
                                }
                            }
                        }.onFailure {
                            android.util.Log.w(TAG, "onImageClick failed", it)
                        }
                    },
                    onImageIndexChange = { index ->
                        setCurrentImageIndex(overlayContext, index)
                    },
                    onTextSourceChange = { source ->
                        if (source == PickResultTextSource.A11Y && !a11ySourceEnabledHolder.value) {
                            return@FloatBallPickResultContent
                        }
                        if (source == PickResultTextSource.OCR && !ocrAvailable) {
                            return@FloatBallPickResultContent
                        }
                        if (source == PickResultTextSource.BARCODE && barcodeResultsHolder.value.isEmpty()) {
                            return@FloatBallPickResultContent
                        }
                        clearTranslateState()
                        textSourceHolder.value = source
                        val switched = textForSource(
                            source = source,
                            a11yText = a11yTextHolder.value,
                            ocrText = ocrTextHolder.value,
                            barcodeResults = barcodeResultsHolder.value
                        )
                        textHolder.value = switched
                        activeTextHolder.value = switched
                        if (source == PickResultTextSource.OCR &&
                            contentOriginHolder.value == PickResultContentOrigin.STASH_CLIPBOARD
                        ) {
                            requestOcrForImageIndex(overlayContext, currentImageIndexHolder.intValue)
                        }
                    },
                    onActiveTextChange = { activeTextHolder.value = it },
                    onTextModeChange = { mode ->
                        val previousMode = textModeHolder.value
                        textModeHolder.value = mode
                        updateWindowFocusableForMode(mode)
                        rememberTextModeForNextOpen(mode)
                        if (previousMode == PickResultTextMode.EDIT && mode != PickResultTextMode.EDIT) {
                            requestPanelFocus()
                        }
                    },
                    onDismiss = {
                        when {
                            FloatBallImageSearchPanel.isShowing -> FloatBallImageSearchPanel.dismiss()
                            else -> dismiss()
                        }
                    },
                    onTextChange = { textHolder.value = it },
                    onCopy = { value, keepPanelOpen ->
                        FloatBallTextPick.copyText(context, value)
                        if (settingsHolder.value.floatBallPickHapticEnabled) {
                            val vibrator = context.getSystemService(android.os.Vibrator::class.java)
                                ?: (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
                            var vibrated = false
                            if (vibrator?.hasVibrator() == true) {
                                try {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                                        vibrated = true
                                    }
                                } catch (_: Throwable) {}
                                if (!vibrated) {
                                    try {
                                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(20L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                        vibrated = true
                                    } catch (_: Throwable) {}
                                }
                            }
                            if (!vibrated) {
                                composeView?.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            }
                        }
                        val autoDismiss = settingsHolder.value.floatBallPickCopyDismissPanel
                        if (autoDismiss && !keepPanelOpen) {
                            dismiss()
                        }
                    },
                    onShareText = {
                        FloatBallTextPick.shareText(context, it)
                        dismiss()
                    },
                    onTranslate = { FloatBallTranslateCoordinator.translate(context, it) },
                    onRemoveSpaces = { value, removeAll ->
                        textHolder.value = if (removeAll) {
                            value.replace(Regex("\\s+"), "")
                        } else {
                            value.trim()
                        }
                    },
                    onSaveScreenshot = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val delayDelete = settingsHolder.value.imageEditorDelayDeleteEnabled
                        if (delayDelete) {
                            val uri = FloatBallTextPick.saveScreenshotReturningUri(
                                overlayContext,
                                bitmap,
                                ingestToHistory = false,
                            )
                            if (uri != null) {
                                ImageEditorSavedImageDeleteScheduler.scheduleDeleteAfterMinutes(
                                    overlayContext,
                                    uri,
                                    5L,
                                )
                                val msg = overlayContext.getString(R.string.inspire_image_edit_save_auto_delete)
                                Toast.makeText(overlayContext.applicationContext, msg, Toast.LENGTH_SHORT).show()
                                showInPanelMessage(msg)
                            } else {
                                showInPanelMessage(overlayContext.getString(R.string.float_ball_action_failed))
                            }
                        } else {
                            val saved = FloatBallTextPick.saveScreenshot(context, bitmap)
                            showInPanelMessage(
                                context.getString(
                                    if (saved) R.string.float_ball_screenshot_saved else R.string.float_ball_action_failed
                                )
                            )
                        }
                    },
                    onSaveScreenshotLongClick = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val delayDelete = settingsHolder.value.imageEditorDelayDeleteEnabled
                        if (!delayDelete) {
                            val uri = FloatBallTextPick.saveScreenshotReturningUri(
                                overlayContext,
                                bitmap,
                                ingestToHistory = false,
                            )
                            if (uri != null) {
                                ImageEditorSavedImageDeleteScheduler.scheduleDeleteAfterMinutes(
                                    overlayContext,
                                    uri,
                                    5L,
                                )
                                val msg = overlayContext.getString(R.string.inspire_image_edit_save_auto_delete)
                                Toast.makeText(overlayContext.applicationContext, msg, Toast.LENGTH_SHORT).show()
                                showInPanelMessage(msg)
                            } else {
                                showInPanelMessage(overlayContext.getString(R.string.float_ball_action_failed))
                            }
                        } else {
                            val saved = FloatBallTextPick.saveScreenshot(context, bitmap)
                            val msg = context.getString(
                                if (saved) R.string.float_ball_screenshot_saved else R.string.float_ball_action_failed
                            )
                            Toast.makeText(overlayContext.applicationContext, msg, Toast.LENGTH_SHORT).show()
                            showInPanelMessage(msg)
                        }
                    },
                    onShareScreenshot = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        FloatBallTextPick.shareScreenshot(context, bitmap)
                        dismiss()
                    },
                    onImageShareEngineClick = { engine ->
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val launched = SearchEngineLauncher.launchImageShare(context, engine, bitmap)
                        if (launched) {
                            dismiss()
                        }
                    },
                    onImageSearch = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        FloatBallImageSearchPanel.show(context, bitmap)
                    },
                    onCopyImage = {
                        // 长按图片 = 复制当前页（翻页时 screenshotHolder 已同步成当前这张）。
                        val bitmap = screenshotHolder.value
                        if (bitmap == null || bitmap.isRecycled) {
                            showInPanelMessage(overlayContext.getString(R.string.float_ball_action_failed))
                        } else {
                            // 与面板内其它动作一致：受「取词面板触觉反馈」开关控制，长按受理时震一次。
                            if (settingsHolder.value.floatBallPickHapticEnabled) {
                                composeView?.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                                )
                            }
                            // 不关面板：长按是顺手动作，dismiss 还会回收面板持有的位图，与后台压缩抢时序。
                            FloatBallTextPick.copyImageAsync(overlayContext, bitmap) { copied ->
                                showInPanelMessage(
                                    overlayContext.getString(
                                        if (copied) {
                                            R.string.float_ball_image_copied
                                        } else {
                                            R.string.float_ball_action_failed
                                        }
                                    )
                                )
                            }
                        }
                    },
                    onSearchEngineClick = { engine, longPressTriggered ->
                        val query = activeTextHolder.value
                        val launched = when (engine.engineType) {
                            SearchEngineType.SHARE_TO_APP ->
                                SearchEngineLauncher.launchTextShare(context, engine, query)
                            else -> SearchEngineLauncher.launch(
                                context,
                                engine,
                                query,
                                settings,
                                longPressTriggered
                            )
                        }
                        if (launched) {
                            if (engine.engineType != SearchEngineType.SHARE_TO_APP) {
                                SearchPanelQueryBridge.rememberQuery(context, query)
                            }
                            dismiss()
                        }
                    },
                    onSearchQuickLaunch = { fullscreen ->
                        val engine = SearchEngineStore.findTextEngineById(
                            settings.searchEngines,
                            settings.floatBallPickDefaultSearchEngineId
                        )
                        if (engine != null) {
                            val query = activeTextHolder.value
                            val launched = when (engine.engineType) {
                                SearchEngineType.SHARE_TO_APP ->
                                    SearchEngineLauncher.launchTextShare(context, engine, query)
                                else -> SearchEngineLauncher.launchWithWindowMode(
                                    context,
                                    engine,
                                    query,
                                    settings,
                                    fullscreen
                                )
                            }
                            if (launched) {
                                if (engine.engineType != SearchEngineType.SHARE_TO_APP) {
                                    SearchPanelQueryBridge.rememberQuery(context, query)
                                }
                                dismiss()
                            }
                        }
                    },
                    onPinTextToScreen = { value ->
                        StashCoordinator.pinTextToScreen(overlayContext, value)
                        dismiss()
                    },
                    onStashText = { value ->
                        StashCoordinator.addText(
                            value,
                            source = StashMetaRepository.SOURCE_PICK,
                        ) { success ->
                            showInPanelMessage(
                                overlayContext.getString(
                                    if (success) R.string.stash_saved else R.string.stash_save_failed
                                )
                            )
                        }
                    },
                    onPinImageToScreen = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val meta = layoutMeta ?: buildScreenshotLayoutMeta(
                            bitmap = bitmap,
                            screenWidthPx = overlayContext.resources.displayMetrics.widthPixels,
                            screenHeightPx = overlayContext.resources.displayMetrics.heightPixels
                        )
                        StashCoordinator.pinImageToScreen(
                            overlayContext,
                            bitmap,
                            screenRect,
                            meta
                        )
                        dismiss()
                    },
                    onStashImage = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val metrics = overlayContext.resources.displayMetrics
                        val (displayW, displayH) = resolvePinImageDisplaySizePx(
                            bitmap = bitmap,
                            screenRect = screenRect,
                            layoutMeta = layoutMeta ?: buildScreenshotLayoutMeta(
                                bitmap = bitmap,
                                screenWidthPx = metrics.widthPixels,
                                screenHeightPx = metrics.heightPixels
                            ),
                            screenWidthPx = metrics.widthPixels,
                            screenHeightPx = metrics.heightPixels
                        )
                        StashCoordinator.addImage(
                            bitmap = bitmap,
                            pinDisplayWidthPx = displayW,
                            pinDisplayHeightPx = displayH,
                            source = StashMetaRepository.SOURCE_PICK,
                        ) { success ->
                            showInPanelMessage(
                                overlayContext.getString(
                                    if (success) R.string.stash_saved else R.string.stash_save_failed
                                )
                            )
                        }
                    },
                    screenRect = screenRect,
                    layoutMeta = layoutMeta
                )
                }
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
            Log.e(TAG, "failed to add pick result panel")
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
        registerScreenOffReceiver(context)
        applyPanelShellPassive()
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams =
        OverlayPanelLayoutParams.pickResultPanel(context)

    private fun registerScreenOffReceiver(context: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    /**
     * 没有显式 `initialTextMode` 时的进入模式：按「点词默认状态」设置计算
     * （记住上次 / 始终开启 / 始终关闭），显式入口不受影响。
     */
    private fun defaultTextModeFor(@Suppress("UNUSED_PARAMETER") text: String?): PickResultTextMode {
        val settings = settingsState?.value
        return resolvePickResultEnterTextMode(
            explicit = null,
            defaultState = settings?.floatBallPickTextModeDefault ?: PickResultTextModeDefault.ALWAYS_ON,
            lastStoredMode = rememberedTextMode?.let(PickResultTextModeStore::toStorageKey)
                ?: settings?.floatBallPickTextModeLastMode,
        )
    }

    /**
     * 记录「上次退出面板时的点词状态」。
     *
     * 同步更新内存值（DataStore 回流有延迟，短时间重开面板要立刻生效），
     * 同时异步写盘（跨进程/重启后仍能读到）。
     */
    private fun rememberTextModeForNextOpen(mode: PickResultTextMode) {
        rememberedTextMode = mode
        if (persistedTextMode == mode) return
        val context = appContext ?: return
        val repository = OverlayDependencyAccess.overlayDependencies(context)?.settingsRepository ?: return
        persistedTextMode = mode
        val storageKey = PickResultTextModeStore.toStorageKey(mode)
        textModePersistScope.launch {
            runCatching { repository.setFloatBallPickTextModeLastMode(storageKey) }
                .onFailure { Log.w(TAG, "persist pick text mode failed", it) }
        }
    }
}
