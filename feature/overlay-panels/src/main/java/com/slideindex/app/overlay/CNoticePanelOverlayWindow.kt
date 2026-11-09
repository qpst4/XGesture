package com.slideindex.app.overlay



import android.content.Context

import android.os.Handler

import android.os.Looper

import android.util.Log

import android.view.WindowManager

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.core.MutableTransitionState

import androidx.compose.animation.core.tween

import androidx.compose.animation.fadeIn

import androidx.compose.animation.fadeOut

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.width

import androidx.compose.runtime.Composable

import androidx.compose.runtime.MutableIntState

import androidx.compose.runtime.MutableState

import androidx.compose.runtime.SideEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.mutableStateOf

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.ComposeView

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.platform.LocalWindowInfo

import androidx.core.view.ViewCompat

import androidx.core.view.WindowInsetsCompat

import com.slideindex.app.message.SideBubbleHorizontalEdge

import com.slideindex.app.ui.theme.OverlayAwareModuleTheme



/**

 * C Notice 详情面板：独立窗口，与贴边列表分离；竖屏全屏模态，横屏贴边 sheet。

 */

object CNoticePanelOverlayWindow {

    private const val TAG = "CNoticePanel"

    private const val DISMISS_MS = 280L



    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null

    private var composeViewRef = java.lang.ref.WeakReference<ComposeView>(null)

    private var composeView: ComposeView?
        get() = composeViewRef.get()
        set(value) {
            composeViewRef = java.lang.ref.WeakReference(value)
        }

    private var owner: OverlayComposeOwner? = null

    private var backHandlerRef = java.lang.ref.WeakReference<OverlayViewBackHandler>(null)

    private var backHandler: OverlayViewBackHandler?
        get() = backHandlerRef.get()
        set(value) {
            backHandlerRef = java.lang.ref.WeakReference(value)
        }

    private var panelVisibilityState: MutableTransitionState<Boolean>? = null

    private var dismissToken = 0

    private var edgeMarginDpState = mutableFloatStateOf(8f)



    private var panelEntriesProvider: (() -> List<CNoticePanelEntryUi>)? = null

    private var selectedKeyState: MutableState<String>? = null

    private var horizontalEdgeState: MutableState<SideBubbleHorizontalEdge>? = null

    private var historyLoadingProvider: (() -> Boolean)? = null

    private var historyExhaustedProvider: ((String) -> Boolean)? = null

    private var onSelectConversation: ((String) -> Unit)? = null

    private var onOpenApp: ((String) -> Unit)? = null

    private var onMarkRead: ((String) -> Unit)? = null

    private var onSendReply: ((String, String) -> Boolean)? = null

    private var onLoadOlderHistory: ((String) -> Unit)? = null

    private var contentRefreshState: MutableIntState? = null

    private var onPanelClosed: (() -> Unit)? = null



    val isShowing: Boolean

        get() = composeView != null && (panelVisibilityState?.targetState == true)



    fun show(

        context: Context,

        horizontalEdge: SideBubbleHorizontalEdge,

        edgeMarginDp: Float,

        selectedKey: String,

        panelEntriesProvider: () -> List<CNoticePanelEntryUi>,

        historyLoading: () -> Boolean,

        historyExhausted: (String) -> Boolean,

        onSelectConversation: (String) -> Unit,

        onOpenApp: (String) -> Unit,

        onMarkRead: (String) -> Unit,

        onSendReply: (String, String) -> Boolean,

        onLoadOlderHistory: (String) -> Unit,

        contentRefreshState: MutableIntState,

        onPanelClosed: () -> Unit,

    ) {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post {

                show(

                    context,

                    horizontalEdge,

                    edgeMarginDp,

                    selectedKey,

                    panelEntriesProvider,

                    historyLoading,

                    historyExhausted,

                    onSelectConversation,

                    onOpenApp,

                    onMarkRead,

                    onSendReply,

                    onLoadOlderHistory,

                    contentRefreshState,

                    onPanelClosed,

                )

            }

            return

        }



        this.panelEntriesProvider = panelEntriesProvider

        this.contentRefreshState = contentRefreshState

        this.onPanelClosed = onPanelClosed

        this.historyLoadingProvider = historyLoading

        this.historyExhaustedProvider = historyExhausted

        this.onSelectConversation = onSelectConversation

        this.onOpenApp = onOpenApp

        this.onMarkRead = onMarkRead

        this.onSendReply = onSendReply

        this.onLoadOlderHistory = onLoadOlderHistory

        edgeMarginDpState.floatValue = edgeMarginDp.coerceIn(0f, 32f)



        if (selectedKeyState == null) {

            selectedKeyState = mutableStateOf(selectedKey)

        } else {

            selectedKeyState?.value = selectedKey

        }

        if (horizontalEdgeState == null) {

            horizontalEdgeState = mutableStateOf(horizontalEdge)

        } else {

            horizontalEdgeState?.value = horizontalEdge

        }



        val resolvedHostContext = context.applicationContext

            ?: com.slideindex.app.overlay.panels.PanelRuntimeHooks.overlayHostContext?.invoke()

            ?: return

        if (composeView == null) {

            ensureWindow(resolvedHostContext)

        } else {

            applyPanelWindowPlacement(

                landscape = MessageOverlayLayout.isLandscapeDisplay(resolvedHostContext),

            )

        }

        dismissToken++

        panelVisibilityState?.targetState = true

        composeView?.requestFocus()

    }



    fun updateSelectedKey(key: String) {

        selectedKeyState?.value = key

    }



    fun updateHorizontalEdge(edge: SideBubbleHorizontalEdge) {

        horizontalEdgeState?.value = edge

        applyPanelWindowPlacement()

    }



    fun dismiss() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { dismiss() }

            return

        }

        if (composeView == null) return

        if (isPanelImeVisible()) {

            hidePanelIme()

            return

        }

        val token = ++dismissToken

        panelVisibilityState?.targetState = false

        mainHandler.postDelayed({

            if (token != dismissToken) return@postDelayed

            if (panelVisibilityState?.targetState == true) return@postDelayed

            teardown()

        }, DISMISS_MS)

    }



    fun dismissImmediate() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { dismissImmediate() }

            return

        }

        dismissToken++

        panelVisibilityState?.targetState = false

        teardown()

    }



    private fun ensureWindow(hostContext: Context) {

        val overlayContext = OverlayCompose.themedContext(hostContext)

        val dialogOwner = OverlayComposeOwner()

        val visibilityState = MutableTransitionState(false)

        panelVisibilityState = visibilityState



        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {

            setContent {

                contentRefreshState?.intValue

                val selectedKey = selectedKeyState?.value.orEmpty()

                val horizontalEdge = horizontalEdgeState?.value ?: SideBubbleHorizontalEdge.Right

                val entries = panelEntriesProvider?.invoke().orEmpty()

                val historyLoading = historyLoadingProvider?.invoke() == true

                val historyExhausted = historyExhaustedProvider?.invoke(selectedKey) == true

                val windowSize = LocalWindowInfo.current.containerSize

                val isLandscape = windowSize.width > windowSize.height

                SideEffect {

                    applyPanelWindowPlacement(isLandscape)

                }

                CNoticePanelOverlayRoot(

                    visibilityState = visibilityState,

                    isLandscape = isLandscape,

                    entries = entries,

                    selectedKey = selectedKey,

                    horizontalEdge = horizontalEdge,

                    historyLoading = historyLoading,

                    historyExhausted = historyExhausted,

                    onSelectConversation = { onSelectConversation?.invoke(it) },

                    onClosePanel = { dismiss() },

                    onOpenApp = { onOpenApp?.invoke(it) },

                    onMarkRead = { onMarkRead?.invoke(it) },

                    onSendReply = { key, text -> onSendReply?.invoke(key, text) == true },

                    onLoadOlderHistory = { onLoadOlderHistory?.invoke(it) },

                )

            }

        }



        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val isLandscape = MessageOverlayLayout.isLandscapeDisplay(hostContext)

        val params = MessageOverlayLayout.buildCNoticePanelLayoutParams(

            context = hostContext,

            horizontalEdge = horizontalEdgeState?.value ?: SideBubbleHorizontalEdge.Right,

            edgeMarginDp = edgeMarginDpState.floatValue,

            landscape = isLandscape,

        )

        val added = runCatching { wm.addView(view, params) }

            .onFailure { Log.e(TAG, "addView failed", it) }

            .isSuccess

        if (!added) {

            dialogOwner.destroy()

            panelVisibilityState = null

            return

        }



        windowManager = wm

        composeView = view

        owner = dialogOwner

        backHandler = OverlayViewBackHandler(view) { handleBack() }.also { it.attach() }

    }



    private fun applyPanelWindowPlacement(landscape: Boolean? = null) {

        val view = composeView ?: return

        val ctx = view.context

        val wm = windowManager ?: return

        val isLandscape = landscape ?: MessageOverlayLayout.isLandscapeDisplay(ctx)

        val params = MessageOverlayLayout.buildCNoticePanelLayoutParams(

            context = ctx,

            horizontalEdge = horizontalEdgeState?.value ?: SideBubbleHorizontalEdge.Right,

            edgeMarginDp = edgeMarginDpState.floatValue,

            landscape = isLandscape,

        )

        runCatching { wm.updateViewLayout(view, params) }

            .onFailure { Log.w(TAG, "updateViewLayout failed", it) }

    }



    private fun handleBack() {

        if (isPanelImeVisible()) {

            hidePanelIme()

            return

        }

        dismiss()

    }



    private fun isPanelImeVisible(): Boolean {

        val view = composeView ?: return false

        val insets = ViewCompat.getRootWindowInsets(view) ?: return false

        return insets.isVisible(WindowInsetsCompat.Type.ime())

    }



    private fun hidePanelIme() {

        val view = composeView ?: return

        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)

            as? android.view.inputmethod.InputMethodManager ?: return

        imm.hideSoftInputFromWindow(view.windowToken, 0)

        view.clearFocus()

    }



    private fun teardown() {

        backHandler?.detach()

        backHandler = null

        val wm = windowManager

        val view = composeView

        val dialogOwner = owner

        val closedCallback = onPanelClosed

        hidePanelIme()

        windowManager = null

        composeView = null

        owner = null

        panelVisibilityState = null

        panelEntriesProvider = null

        historyLoadingProvider = null

        historyExhaustedProvider = null

        contentRefreshState = null

        onSelectConversation = null

        onOpenApp = null

        onMarkRead = null

        onSendReply = null

        onLoadOlderHistory = null

        onPanelClosed = null

        if (view != null && wm != null) {

            runCatching { wm.removeView(view) }

        }

        OverlayCompose.teardownOverlayCompose(view, dialogOwner)

        closedCallback?.invoke()

    }

}



@Composable

private fun CNoticePanelOverlayRoot(

    visibilityState: MutableTransitionState<Boolean>,

    isLandscape: Boolean,

    entries: List<CNoticePanelEntryUi>,

    selectedKey: String,

    horizontalEdge: SideBubbleHorizontalEdge,

    historyLoading: Boolean,

    historyExhausted: Boolean,

    onSelectConversation: (String) -> Unit,

    onClosePanel: () -> Unit,

    onOpenApp: (String) -> Unit,

    onMarkRead: (String) -> Unit,

    onSendReply: (String, String) -> Boolean,

    onLoadOlderHistory: (String) -> Unit,

) {

    val fadeSpec = tween<Float>(280)

    val hostContext = LocalContext.current.applicationContext

    val landscapePanelWidth = with(LocalDensity.current) {

        MessageOverlayLayout.cNoticeLandscapeSheetWidthPx(hostContext).toDp()

    }

    val rootModifier = if (isLandscape) {

        Modifier.width(landscapePanelWidth)

    } else {

        Modifier.fillMaxSize()

    }

    OverlayAwareModuleTheme {

        AnimatedVisibility(

            visibleState = visibilityState,

            enter = fadeIn(fadeSpec),

            exit = fadeOut(fadeSpec),

            modifier = rootModifier,

        ) {

            if (entries.isNotEmpty()) {

                CNoticePanelContent(

                    entries = entries,

                    selectedKey = selectedKey,

                    horizontalEdge = horizontalEdge,

                    historyLoading = historyLoading,

                    historyExhausted = historyExhausted,

                    onSelectConversation = onSelectConversation,

                    onClosePanel = onClosePanel,

                    onOpenApp = onOpenApp,

                    onMarkRead = onMarkRead,

                    onSendReply = onSendReply,

                    onLoadOlderHistory = onLoadOlderHistory,

                    modifier = rootModifier,

                )

            }

        }

    }

}


