package com.slideindex.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboardfloat.ClipboardFloatDisplayMode
import com.slideindex.app.clipboardfloat.ClipboardFloatRoot
import com.slideindex.app.clipboardfloat.ClipboardFloatWindowFlags
import com.slideindex.app.clipboardfloat.ClipboardPasteCoordinator
import com.slideindex.app.clipboardfloat.ClipboardPasteHelper
import com.slideindex.app.clipboardfloat.PasteFailureReason
import com.slideindex.app.clipboardfloat.PasteResult
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.PickResultFromHistoryCoordinator
import com.slideindex.app.overlay.PickResultContentOrigin
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.settings.ClipboardFloatEntryClickAction
import com.slideindex.app.settings.ClipboardFloatEntryLongPressAction
import com.slideindex.app.settings.ClipboardFloatListStyle
import com.slideindex.app.settings.ClipboardFloatOrientationGeometry
import com.slideindex.app.settings.ClipboardFloatWindowMetrics
import com.slideindex.app.util.LockScreenState
import com.slideindex.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ClipboardFloatService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject lateinit var deps: AppDependencies

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var composeView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private lateinit var listController: com.slideindex.app.clipboardfloat.ClipboardFloatListController
    private var backHandler: OverlayViewBackHandler? = null

    private var viewAdded = false

    private var displayMode by mutableStateOf(ClipboardFloatDisplayMode.Chip)
    private var listStyle by mutableStateOf(ClipboardFloatListStyle.SINGLE_LINE)
    private var rememberPosition by mutableStateOf(false)
    private var panelPinned by mutableStateOf(false)
    private var showChipPref by mutableStateOf(true)
    private var singleLineClickAction by mutableStateOf(ClipboardFloatEntryClickAction.PASTE)
    private var singleLineLongPressAction by mutableStateOf(ClipboardFloatEntryLongPressAction.WORD_TAP)
    private var cardClickAction by mutableStateOf(ClipboardFloatEntryClickAction.PASTE)
    private var cardLongPressAction by mutableStateOf(ClipboardFloatEntryLongPressAction.DRAG_DROP)
    private var pasteHapticEnabled by mutableStateOf(false)
    private var panelAlpha by mutableFloatStateOf(1.0f)
    private var autoDimWhenUnfocused by mutableStateOf(false)
    private var autoCloseSeconds by mutableIntStateOf(0)
    /**
     * 玻璃底（背景模糊）的开/关与半径。
     *
     * 必须**建窗前**从快照读好、作为首帧入参交给 [ClipboardFloatRoot]：这扇窗是冷启动的
     * （弹键盘 → `startService` → 新建窗 + 新 Surface），等 Compose 里那条设置 Flow 首次
     * emit 再挂磨砂层的话，首帧只剩 0.42 半透明底，且窗口就位后完全静止、没有任何事务，
     * 那一帧会一直停在屏幕上，直到用户碰一下窗口。
     */
    private var blurEnabled by mutableStateOf(false)
    private var blurRadiusDp by mutableIntStateOf(0)
    private var isDimmed = false
    private var screenOffReceiver: BroadcastReceiver? = null
    private var isLandscape = false
    private var orientationGeometryLoaded = false
    private var imeTop = 0
    private var chipPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var chipPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
    /** 最近一次已知的 chip 屏幕坐标，大窗关窗写盘时兜底（避免只抓 panel 把 chip 写成 -1）。 */
    private var rememberedChipPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var rememberedChipPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelWidthDp by mutableIntStateOf(ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP)
    private var panelHeightDp by mutableIntStateOf(ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP)

    private var idleGeometryPersistRunnable: Runnable? = null
    private var isDraggingWindow = false
    /** 起拖后若迟迟收不到 ACTION_DRAG_ENDED 的兜底恢复任务 */
    private var entryDragRestoreWatchdog: Runnable? = null
    private var searchActive by mutableStateOf(false)
    private var chipRetainedAfterManualCollapse = false
    /** 搜索收起后大窗在无键盘时仍保持展开，直到用户手动收起或关闭 */
    private var expandedRetainedWithoutIme = false
    /** 本次会话内用户拖动/收起调整过 chip 位置（记住位置关时仍生效，但不写盘） */
    private var chipPositionCustomizedThisSession = false
    /** 本次会话内用户拖动过大窗位置 */
    private var panelPositionCustomizedThisSession = false

    private val autoCloseRunnable = Runnable {
        if (viewAdded && displayMode == ClipboardFloatDisplayMode.Expanded && !panelPinned) {
            closeWindow()
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        listController = com.slideindex.app.clipboardfloat.ClipboardFloatListController(
            repository = com.slideindex.app.clipboardfloat.ClipboardFloatHistoryAccess.source
                ?.invoke(this),
            scope = lifecycleScope
        )
        registerScreenOffReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        if (LockScreenState.isActive(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_HIDE -> {
                if (!shouldStayVisibleWithoutIme()) {
                    hideWindow()
                    return START_NOT_STICKY
                }
                persistGeometryOnClose(blocking = false)
                return START_STICKY
            }
            ACTION_RECREATE_FOR_LOCALE -> {
                recreateWindowForLocale()
                return if (viewAdded) START_STICKY else START_NOT_STICKY
            }
            ACTION_SHOW_EXPANDED -> {
                if (viewAdded && displayMode == ClipboardFloatDisplayMode.Chip) {
                    captureCurrentWindowPosition()
                    persistGeometryOnClose(blocking = false)
                }
                expandedRetainedWithoutIme = true
                displayMode = ClipboardFloatDisplayMode.Expanded
                catchUpLatestClipboard()
                if (viewAdded) {
                    applyWindowGeometry(forceDefaultPosition = forceDefaultPositionForMode())
                    resetAutoCloseTimer()
                    return START_STICKY
                }
            }
            ACTION_UPDATE_IME -> {
                imeTop = intent.getIntExtra(EXTRA_IME_TOP, imeTop)
                if (viewAdded && shouldFollowIme() && !isDraggingWindow) {
                    applyWindowGeometry(forceDefaultPosition = false)
                }
                return START_STICKY
            }
        }

        val resolvedHost = MessageOverlayHost.resolveHostContext(this) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        windowManager = resolvedHost.getSystemService(WINDOW_SERVICE) as WindowManager

        if (!viewAdded) {
            loadSettingsSnapshot()
        }
        imeTop = intent?.getIntExtra(EXTRA_IME_TOP, imeTop) ?: imeTop
        showChipPref = intent?.getBooleanExtra(EXTRA_SHOW_CHIP, showChipPref) ?: showChipPref

        val isExplicitShowExpanded = intent?.action == ACTION_SHOW_EXPANDED
        if (!viewAdded) {
            chipRetainedAfterManualCollapse = false
            chipPositionCustomizedThisSession = false
            panelPositionCustomizedThisSession = false
            displayMode = if (isExplicitShowExpanded) {
                ClipboardFloatDisplayMode.Expanded
            } else if (showChipPref) {
                ClipboardFloatDisplayMode.Chip
            } else {
                ClipboardFloatDisplayMode.Expanded
            }
            createAndAttachWindow(resolvedHost)
            if (displayMode == ClipboardFloatDisplayMode.Expanded) {
                resetAutoCloseTimer()
            }
        } else if (shouldFollowIme() && !isDraggingWindow) {
            applyWindowGeometry(forceDefaultPosition = forceDefaultPositionForMode())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterScreenOffReceiver()
        mainHandler.removeCallbacks(autoCloseRunnable)
        cancelEntryDragRestoreWatchdog()
        backHandler = null
        if (activeInstance === this) {
            activeInstance = null
        }
        // hideWindow() 已落盘；此处仅处理未走 hideWindow 的异常销毁，且需能 capture 当前窗口
        if (viewAdded) {
            persistGeometryOnClose(blocking = false)
        }
        if (viewAdded) {
            composeView?.let { runCatching { windowManager.removeView(it) } }
            viewAdded = false
        }
        OverlayCompose.teardownOverlayCompose(composeView, composeOwner)
        composeOwner = null
        composeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (viewAdded) {
            maybeSwitchOrientationGeometry()
            applyWindowGeometry(forceDefaultPosition = forceDefaultPositionForMode())
        }
    }

    private fun loadSettingsSnapshot() {
        val snapshot = deps.settingsRepository.readSnapshot()
        rememberPosition = snapshot.clipboardFloatPanelPinPosition
        showChipPref = snapshot.clipboardFloatShowChip
        singleLineClickAction = snapshot.clipboardFloatSingleLineEntryClickAction
        singleLineLongPressAction = snapshot.clipboardFloatSingleLineEntryLongPressAction
        cardClickAction = snapshot.clipboardFloatCardEntryClickAction
        cardLongPressAction = snapshot.clipboardFloatCardEntryLongPressAction
        listStyle = snapshot.clipboardFloatListStyle
        pasteHapticEnabled = snapshot.clipboardFloatPasteHapticEnabled
        panelAlpha = snapshot.clipboardFloatAlpha
        blurEnabled = snapshot.clipboardFloatBlurEnabled
        blurRadiusDp = snapshot.clipboardFloatBlurRadiusDp
        autoDimWhenUnfocused = snapshot.clipboardFloatAutoDimWhenUnfocused
        autoCloseSeconds = snapshot.clipboardFloatAutoCloseSeconds
        isLandscape = isLandscapeNow()
        orientationGeometryLoaded = false
        applyGeometryFromSnapshot(snapshot)
        orientationGeometryLoaded = true
    }

    private fun applyGeometryFromSnapshot(snapshot: com.slideindex.app.settings.AppSettings) {
        val geometry = orientationGeometry(snapshot, isLandscape)
        panelWidthDp = geometry.panelWidthDp
        panelHeightDp = geometry.panelHeightDp
        rememberPosition = snapshot.clipboardFloatPanelPinPosition
        if (rememberPosition) {
            chipPosX = geometry.chipX
            chipPosY = geometry.chipY
            panelPosX = geometry.panelX
            panelPosY = geometry.panelY
            syncRememberedChipFromChipPos()
        } else {
            chipPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
            chipPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
            rememberedChipPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
            rememberedChipPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
            panelPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
            panelPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
        }
    }

    private fun refreshRememberPositionFromDisk() {
        rememberPosition = deps.settingsRepository.readSnapshot().clipboardFloatPanelPinPosition
    }

    private fun syncRememberedChipPosition(x: Int, y: Int) {
        rememberedChipPosX = x
        rememberedChipPosY = y
    }

    private fun syncRememberedChipFromChipPos() {
        if (hasSavedChipPosition()) {
            syncRememberedChipPosition(chipPosX, chipPosY)
        }
    }

    private fun hasRememberedChipPosition(): Boolean =
        rememberedChipPosX != ClipboardFloatWindowMetrics.UNSET_POSITION &&
            rememberedChipPosY != ClipboardFloatWindowMetrics.UNSET_POSITION

    private fun persistableChipX(): Int = when {
        hasSavedChipPosition() -> chipPosX
        hasRememberedChipPosition() -> rememberedChipPosX
        else -> ClipboardFloatWindowMetrics.UNSET_POSITION
    }

    private fun persistableChipY(): Int = when {
        hasSavedChipPosition() -> chipPosY
        hasRememberedChipPosition() -> rememberedChipPosY
        else -> ClipboardFloatWindowMetrics.UNSET_POSITION
    }

    private fun orientationGeometry(
        snapshot: com.slideindex.app.settings.AppSettings,
        landscape: Boolean
    ): ClipboardFloatOrientationGeometry =
        if (landscape) snapshot.clipboardFloatLandscapeGeometry else snapshot.clipboardFloatPortraitGeometry

    private fun isLandscapeNow(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun maybeSwitchOrientationGeometry() {
        val landscape = isLandscapeNow()
        if (orientationGeometryLoaded && landscape == isLandscape) return
        if (orientationGeometryLoaded) {
            persistCurrentOrientationGeometry(isLandscape, blocking = false)
        }
        isLandscape = landscape
        applyGeometryFromSnapshot(deps.settingsRepository.readSnapshot())
        orientationGeometryLoaded = true
    }

    private fun currentOrientationGeometry(): ClipboardFloatOrientationGeometry =
        ClipboardFloatOrientationGeometry(
            panelX = if (rememberPosition) persistablePanelX() else ClipboardFloatWindowMetrics.UNSET_POSITION,
            panelY = if (rememberPosition) persistablePanelY() else ClipboardFloatWindowMetrics.UNSET_POSITION,
            panelWidthDp = panelWidthDp,
            panelHeightDp = panelHeightDp,
            chipX = if (rememberPosition) persistableChipX() else ClipboardFloatWindowMetrics.UNSET_POSITION,
            chipY = if (rememberPosition) persistableChipY() else ClipboardFloatWindowMetrics.UNSET_POSITION
        )

    private fun persistablePanelX(): Int = when {
        hasSavedPanelPosition() -> panelPosX
        else -> ClipboardFloatWindowMetrics.UNSET_POSITION
    }

    private fun persistablePanelY(): Int = when {
        hasSavedPanelPosition() -> panelPosY
        else -> ClipboardFloatWindowMetrics.UNSET_POSITION
    }

    private fun captureCurrentWindowPosition() {
        if (!viewAdded || !::params.isInitialized) return
        when (displayMode) {
            ClipboardFloatDisplayMode.Chip -> {
                chipPosX = params.x
                chipPosY = params.y
                syncRememberedChipPosition(chipPosX, chipPosY)
            }
            ClipboardFloatDisplayMode.Expanded -> {
                panelPosX = params.x
                panelPosY = params.y
            }
        }
        syncRememberedChipFromChipPos()
    }

    private fun persistCurrentOrientationGeometry(landscape: Boolean, blocking: Boolean = false) {
        captureCurrentWindowPosition()
        val geometry = currentOrientationGeometry()
        persistOrientationGeometry(landscape, geometry, blocking)
    }

    private fun hasSavedChipPosition(): Boolean =
        chipPosX != ClipboardFloatWindowMetrics.UNSET_POSITION &&
            chipPosY != ClipboardFloatWindowMetrics.UNSET_POSITION

    private fun hasSavedPanelPosition(): Boolean =
        panelPosX != ClipboardFloatWindowMetrics.UNSET_POSITION &&
            panelPosY != ClipboardFloatWindowMetrics.UNSET_POSITION

    private fun shouldUseRememberedChipPosition(): Boolean =
        rememberPosition && (hasSavedChipPosition() || hasRememberedChipPosition())

    private fun shouldUseRememberedPanelPosition(): Boolean =
        rememberPosition && hasSavedPanelPosition()

    private fun usesCustomChipPosition(): Boolean =
        chipRetainedAfterManualCollapse ||
            chipPositionCustomizedThisSession ||
            shouldUseRememberedChipPosition()

    private fun effectiveChipPosX(): Int = when {
        hasSavedChipPosition() -> chipPosX
        hasRememberedChipPosition() -> rememberedChipPosX
        else -> ClipboardFloatWindowMetrics.UNSET_POSITION
    }

    private fun effectiveChipPosY(): Int = when {
        hasSavedChipPosition() -> chipPosY
        hasRememberedChipPosition() -> rememberedChipPosY
        else -> ClipboardFloatWindowMetrics.UNSET_POSITION
    }

    private fun usesCustomPanelPosition(): Boolean =
        panelPositionCustomizedThisSession || shouldUseRememberedPanelPosition()

    private fun shouldUseDefaultChipPosition(forceDefaultPosition: Boolean): Boolean =
        forceDefaultPosition || !usesCustomChipPosition()

    private fun shouldUseDefaultPanelPosition(forceDefaultPosition: Boolean): Boolean =
        forceDefaultPosition || !usesCustomPanelPosition()

    private fun shouldStayVisibleWithoutIme(): Boolean =
        (displayMode == ClipboardFloatDisplayMode.Expanded &&
            (panelPinned || searchActive || expandedRetainedWithoutIme)) ||
            (displayMode == ClipboardFloatDisplayMode.Chip && chipRetainedAfterManualCollapse)

    private fun shouldFollowIme(): Boolean {
        if (searchActive) return false
        return when (displayMode) {
            ClipboardFloatDisplayMode.Chip -> !usesCustomChipPosition()
            ClipboardFloatDisplayMode.Expanded -> !usesCustomPanelPosition()
        }
    }

    private fun forceDefaultPositionForMode(): Boolean = when (displayMode) {
        ClipboardFloatDisplayMode.Chip -> shouldUseDefaultChipPosition(forceDefaultPosition = false)
        ClipboardFloatDisplayMode.Expanded -> shouldUseDefaultPanelPosition(forceDefaultPosition = false)
    }

    private fun createAndAttachWindow(context: Context) {
        val overlayContext = OverlayCompose.themedContext(context)
        params = WindowManager.LayoutParams().apply {
            type = OverlayWindowTypes.overlayWindowType(context)
            format = PixelFormat.RGBA_8888
            flags = ClipboardFloatWindowFlags.forMode(
                expanded = displayMode == ClipboardFloatDisplayMode.Expanded,
                keyboardFocus = searchActive
            )
            alpha = panelAlpha
            gravity = Gravity.TOP or Gravity.START
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        val owner = OverlayComposeOwner()
        composeOwner = owner
        val contentView = OverlayCompose.createComposeView(overlayContext, owner).apply {
            setContent {
                ClipboardFloatRoot(
                    mode = displayMode,
                    pinned = panelPinned,
                    panelAlpha = panelAlpha,
                    blurEnabled = blurEnabled,
                    blurRadiusDp = blurRadiusDp,
                    listController = listController,
                    windowWidthDp = panelWidthDp,
                    listStyle = listStyle,
                    entryLongPressAction = entryLongPressActionForListStyle(),
                    onOpenExpanded = ::expandWindow,
                    onTogglePin = ::togglePin,
                    onAlphaChange = ::updateAlpha,
                    onOpenStashPanel = ::openStashPanel,
                    onToggleListStyle = ::toggleListStyle,
                    onCollapse = ::collapseWindow,
                    onClose = ::closeWindow,
                    onDragWindow = ::onDragWindow,
                    onDragWindowStart = ::onDragWindowStart,
                    onDragWindowEnd = ::onDragWindowEnd,
                    onResizeWindow = ::onResizeWindow,
                    onSearchActiveChanged = ::onSearchActiveChanged,
                    onEntryClick = ::onEntryClick,
                    onEntryLongClick = ::onEntryLongClick,
                    onEntryDragStart = ::onEntryDragStart,
                    onEntryDragEnd = ::onEntryDragEnd,
                    onUserInteraction = ::onUserInteraction
                )
            }
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_OUTSIDE -> {
                        if (displayMode == ClipboardFloatDisplayMode.Expanded) {
                            if (!panelPinned) {
                                closeWindow()
                            } else if (autoDimWhenUnfocused) {
                                dimWindow()
                            }
                        }
                    }
                    MotionEvent.ACTION_DOWN -> {
                        resetAutoCloseTimer()
                        if (isDimmed) {
                            undimWindow()
                        }
                    }
                    MotionEvent.ACTION_UP -> view.performClick()
                }
                false
            }
        }
        backHandler = OverlayViewBackHandler(contentView) {
            if (displayMode == ClipboardFloatDisplayMode.Expanded) {
                if (!panelPinned) {
                    closeWindow()
                } else {
                    collapseWindow()
                }
            }
        }.also { it.attach() }
        composeView = contentView
        applyWindowGeometry(forceDefaultPosition = forceDefaultPositionForMode())
        try {
            windowManager.addView(composeView, params)
            viewAdded = true
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        } catch (e: WindowManager.BadTokenException) {
            if (params.type != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY &&
                PermissionHelper.canDrawOverlays(this)
            ) {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                try {
                    val appWm = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
                    windowManager = appWm
                    appWm.addView(composeView, params)
                    viewAdded = true
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                } catch (t: Throwable) {
                    stopSelf()
                }
            } else {
                stopSelf()
            }
        } catch (t: Throwable) {
            stopSelf()
        }
    }

    private fun applyWindowGeometry(
        forceDefaultPosition: Boolean
    ) {
        if (isDraggingWindow) return
        if (!viewAdded && !::params.isInitialized) return
        maybeSwitchOrientationGeometry()
        val density = resources.displayMetrics.density
        val marginPx = (ClipboardFloatWindowMetrics.EDGE_MARGIN_DP * density).roundToInt()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        if (displayMode == ClipboardFloatDisplayMode.Chip) {
            params.flags = ClipboardFloatWindowFlags.withOutsideTouch(params.flags, enabled = false)
            val chipWindowPx = ClipboardFloatWindowMetrics.chipWindowSizePx(density)
            params.width = chipWindowPx
            params.height = chipWindowPx
            if (shouldUseDefaultChipPosition(forceDefaultPosition)) {
                applyChipDefaultPosition(screenWidth, marginPx, density)
            } else {
                params.x = effectiveChipPosX()
                params.y = effectiveChipPosY().coerceAtLeast(marginPx)
            }
        } else {
            params.flags = ClipboardFloatWindowFlags.withOutsideTouch(params.flags, enabled = true)
            params.width = ClipboardFloatWindowMetrics.expandedPanelWindowWidthPx(panelWidthDp, density)
            params.height = ClipboardFloatWindowMetrics.expandedPanelWindowHeightPx(panelHeightDp, density)
            if (shouldUseDefaultPanelPosition(forceDefaultPosition)) {
                applyPanelDefaultPosition(screenWidth, screenHeight, marginPx, density)
            } else {
                params.x = panelPosX
                params.y = panelPosY.coerceAtLeast(marginPx)
            }
        }
        params.alpha = if (isDimmed) (panelAlpha * 0.35f).coerceAtLeast(0.2f) else panelAlpha

        if (viewAdded) {
            composeView?.let { windowManager.updateViewLayout(it, params) }
        }
    }

    private fun applyChipDefaultPosition(screenWidth: Int, marginPx: Int, density: Float) {
        val chipExtraPx = (ClipboardFloatWindowMetrics.CHIP_ABOVE_IME_EXTRA_DP * density).roundToInt()
        params.x = (screenWidth - params.width - marginPx).coerceAtLeast(marginPx)
        val y = imeTop - params.height - marginPx - chipExtraPx
        params.y = y.coerceAtLeast(marginPx)
    }

    private fun applyPanelDefaultPosition(
        screenWidth: Int,
        screenHeight: Int,
        marginPx: Int,
        density: Float
    ) {
        val aboveCenterPx =
            (ClipboardFloatWindowMetrics.PANEL_DEFAULT_ABOVE_CENTER_DP * density).roundToInt()
        params.x = ((screenWidth - params.width) / 2).coerceAtLeast(marginPx)
        val y = (screenHeight - params.height) / 2 - aboveCenterPx
        params.y = y.coerceAtLeast(marginPx)
    }

    private fun onDragWindowStart() {
        isDraggingWindow = true
        cancelIdleGeometryPersist()
        resetAutoCloseTimer()
        if (isDimmed) {
            undimWindow()
        }
    }

    private fun onDragWindow(dx: Float, dy: Float) {
        if (!viewAdded) return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        when (displayMode) {
            ClipboardFloatDisplayMode.Chip -> {
                chipPosX = params.x
                chipPosY = params.y
                syncRememberedChipPosition(chipPosX, chipPosY)
                chipPositionCustomizedThisSession = true
            }
            ClipboardFloatDisplayMode.Expanded -> {
                panelPosX = params.x
                panelPosY = params.y
                panelPositionCustomizedThisSession = true
            }
        }
        composeView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun onDragWindowEnd() {
        isDraggingWindow = false
        persistGeometryOnClose(blocking = false)
        resetAutoCloseTimer()
    }

    private fun onResizeWindow(dw: Float, dh: Float) {
        if (!viewAdded || displayMode != ClipboardFloatDisplayMode.Expanded) return
        cancelIdleGeometryPersist()
        resetAutoCloseTimer()
        val density = resources.displayMetrics.density
        val insetPx = ClipboardFloatWindowMetrics.panelShadowInsetPx(density) * 2
        val nextWidthDp = ClipboardFloatWindowMetrics.coerceWidth(
            ((params.width + dw.roundToInt() - insetPx) / density).roundToInt()
        )
        val nextHeightDp = ClipboardFloatWindowMetrics.coerceHeight(
            ((params.height + dh.roundToInt() - insetPx) / density).roundToInt()
        )
        panelWidthDp = nextWidthDp
        panelHeightDp = nextHeightDp
        params.width = ClipboardFloatWindowMetrics.expandedPanelWindowWidthPx(nextWidthDp, density)
        params.height = ClipboardFloatWindowMetrics.expandedPanelWindowHeightPx(nextHeightDp, density)
        composeView?.let { windowManager.updateViewLayout(it, params) }
        scheduleIdleGeometryPersist()
    }

    private fun scheduleIdleGeometryPersist() {
        idleGeometryPersistRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable { persistGeometryOnClose() }
        idleGeometryPersistRunnable = runnable
        mainHandler.postDelayed(runnable, IDLE_GEOMETRY_PERSIST_MS)
    }

    private fun cancelIdleGeometryPersist() {
        idleGeometryPersistRunnable?.let(mainHandler::removeCallbacks)
        idleGeometryPersistRunnable = null
    }

    private fun persistGeometryOnClose(blocking: Boolean = false) {
        cancelIdleGeometryPersist()
        refreshRememberPositionFromDisk()
        captureCurrentWindowPosition()
        val landscape = isLandscapeNow()
        val geometry = currentOrientationGeometry()
        persistOrientationGeometry(landscape, geometry, blocking)
    }

    private fun persistOrientationGeometry(
        landscape: Boolean,
        geometry: ClipboardFloatOrientationGeometry,
        blocking: Boolean
    ) {
        val write: suspend () -> Unit = {
            val snapshot = deps.settingsRepository.readFreshSnapshot()
            val existing = if (landscape) {
                snapshot.clipboardFloatLandscapeGeometry
            } else {
                snapshot.clipboardFloatPortraitGeometry
            }
            val toWrite = if (rememberPosition) {
                geometry.mergePreservingUnset(existing)
            } else {
                existing.copy(
                    panelWidthDp = geometry.panelWidthDp,
                    panelHeightDp = geometry.panelHeightDp
                )
            }
            deps.settingsRepository.setClipboardFloatOrientationGeometry(
                landscape = landscape,
                geometry = toWrite,
                chipFollowIme = !rememberPosition
            )
        }
        deps.applicationScope.launch(Dispatchers.IO) { write() }
    }

    private fun expandWindow() {
        captureCurrentWindowPosition()
        persistGeometryOnClose(blocking = false)
        chipRetainedAfterManualCollapse = false
        expandedRetainedWithoutIme = false
        displayMode = ClipboardFloatDisplayMode.Expanded
        applyWindowGeometry(forceDefaultPosition = !shouldUseRememberedPanelPosition())
        captureCurrentWindowPosition()
        persistGeometryOnClose(blocking = false)
        resetAutoCloseTimer()
        catchUpLatestClipboard()
    }

    /**
     * 打开剪贴板浮窗时补读一次系统剪贴板：监听失效期间（Shizuku 未启动、监听服务被停）
     * 复制的内容，打开浮窗后也能看到。监听正常时内部会直接返回。
     */
    private fun catchUpLatestClipboard() {
        runCatching { deps.clipboardHistoryRepository.catchUpLatestClipboard(this) }
    }

    private fun collapseWindow() {
        collapseToChip(
            retainWhenKeyboardHides = true,
            anchorChipToPanelPosition = false
        )
    }

    private fun collapseAfterEntryAction() {
        collapseToChip(
            retainWhenKeyboardHides = false,
            anchorChipToPanelPosition = false
        )
    }

    private fun collapseToChip(
        retainWhenKeyboardHides: Boolean,
        anchorChipToPanelPosition: Boolean
    ) {
        if (!showChipPref) {
            closeWindow()
            return
        }
        captureCurrentWindowPosition()
        mainHandler.removeCallbacks(autoCloseRunnable)
        expandedRetainedWithoutIme = false
        clearSearchState()
        chipRetainedAfterManualCollapse = retainWhenKeyboardHides
        if (anchorChipToPanelPosition) {
            chipPosX = params.x
            chipPosY = params.y
            syncRememberedChipPosition(chipPosX, chipPosY)
            chipPositionCustomizedThisSession = true
        }
        displayMode = ClipboardFloatDisplayMode.Chip
        applyWindowGeometry(
            forceDefaultPosition = !anchorChipToPanelPosition && !shouldUseRememberedChipPosition()
        )
    }

    private fun clearSearchState() {
        searchActive = false
        listController.setSearchQuery("")
        if (viewAdded) {
            updateWindowFocusForSearch(false)
        }
    }

    private fun closeWindow() {
        hideWindow()
    }

    private fun hideWindow() {
        mainHandler.removeCallbacks(autoCloseRunnable)
        chipRetainedAfterManualCollapse = false
        expandedRetainedWithoutIme = false
        clearSearchState()
        persistGeometryOnClose(blocking = false)
        if (viewAdded) {
            composeView?.let { runCatching { windowManager.removeView(it) } }
            viewAdded = false
        }
        stopSelf()
    }

    private fun recreateWindowForLocale() {
        if (!viewAdded) return
        val host = MessageOverlayHost.resolveHostContext(this) ?: return
        backHandler?.detach()
        backHandler = null
        composeView?.let { runCatching { windowManager.removeView(it) } }
        OverlayCompose.teardownOverlayCompose(composeView, composeOwner)
        composeOwner = null
        composeView = null
        viewAdded = false
        createAndAttachWindow(host)
    }

    private fun togglePin() {
        panelPinned = !panelPinned
        if (panelPinned) {
            mainHandler.removeCallbacks(autoCloseRunnable)
        } else {
            undimWindow()
            resetAutoCloseTimer()
        }
    }

    private fun updateAlpha(newAlpha: Float) {
        val clamped = newAlpha.coerceIn(0.2f, 1.0f)
        panelAlpha = clamped
        if (viewAdded && !isDimmed) {
            params.alpha = clamped
            runCatching { windowManager.updateViewLayout(composeView, params) }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            deps.settingsRepository.setClipboardFloatAlpha(clamped)
        }
    }

    private fun dimWindow() {
        if (!autoDimWhenUnfocused || isDimmed) return
        isDimmed = true
        if (viewAdded) {
            params.alpha = (panelAlpha * 0.35f).coerceAtLeast(0.2f)
            runCatching { windowManager.updateViewLayout(composeView, params) }
        }
    }

    private fun undimWindow() {
        if (!isDimmed) return
        isDimmed = false
        if (viewAdded) {
            params.alpha = panelAlpha
            runCatching { windowManager.updateViewLayout(composeView, params) }
        }
    }

    private fun onUserInteraction() {
        resetAutoCloseTimer()
        if (isDimmed) {
            undimWindow()
        }
    }

    private fun resetAutoCloseTimer() {
        mainHandler.removeCallbacks(autoCloseRunnable)
        if (autoCloseSeconds > 0 && displayMode == ClipboardFloatDisplayMode.Expanded && !panelPinned) {
            mainHandler.postDelayed(autoCloseRunnable, autoCloseSeconds * 1000L)
        }
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> hideWindow()
                    Intent.ACTION_USER_PRESENT -> {
                        if (LockScreenState.isActive(this@ClipboardFloatService)) {
                            hideWindow()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenOffReceiver = receiver
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let {
            runCatching { unregisterReceiver(it) }
            screenOffReceiver = null
        }
    }

    private fun openStashPanel() {
        val host = MessageOverlayHost.resolveHostContext(this) ?: return
        val query = listController.searchQuery.value.trim().takeIf { searchActive && it.isNotEmpty() }
        FloatBallStashPanel.show(
            context = host,
            initialTab = StashPanelInitialTab.Clipboard,
            searchQuery = query
        )
    }

    private fun entryClickActionForListStyle(): ClipboardFloatEntryClickAction = when (listStyle) {
        ClipboardFloatListStyle.SINGLE_LINE -> singleLineClickAction
        ClipboardFloatListStyle.CARD -> cardClickAction
    }

    private fun entryLongPressActionForListStyle(): ClipboardFloatEntryLongPressAction = when (listStyle) {
        ClipboardFloatListStyle.SINGLE_LINE -> singleLineLongPressAction
        ClipboardFloatListStyle.CARD -> cardLongPressAction
    }

    private fun onEntryClick(entry: com.slideindex.app.clipboard.ClipboardEntry) {
        resetAutoCloseTimer()
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (service == null) {
            Toast.makeText(this, R.string.search_engine_accessibility_required, Toast.LENGTH_SHORT).show()
            return
        }
        val clickAction = entryClickActionForListStyle()
        when (clickAction) {
            ClipboardFloatEntryClickAction.COPY -> {
                when (
                    val result = ClipboardPasteHelper.performEntryAction(
                        service = service,
                        context = this,
                        entry = entry,
                        action = clickAction
                    )
                ) {
                    PasteResult.Success -> {
                        if (!panelPinned) collapseAfterEntryAction()
                    }
                    is PasteResult.Failure -> showPasteFailureToast(result)
                }
            }
            ClipboardFloatEntryClickAction.PASTE,
            ClipboardFloatEntryClickAction.COPY_AND_PASTE -> {
                val fvStyle = deps.settingsRepository.readSnapshot().clipboardPasteFvStyleEnabled
                ClipboardPasteCoordinator.pasteEntry(
                    service = service,
                    context = this,
                    entry = entry,
                    writeToClipboardFirst = clickAction == ClipboardFloatEntryClickAction.COPY_AND_PASTE,
                    fvStyle = fvStyle,
                ) { result -> handleClipboardFloatPasteResult(result) }
            }
        }
    }

    private fun handleClipboardFloatPasteResult(result: PasteResult?) {
        when (result) {
            null -> Unit
            PasteResult.Success -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    deps.settingsRepository.recordClipboardFloatPasteResult(success = true)
                }
                performPasteHapticIfEnabled()
                if (!panelPinned) collapseAfterEntryAction()
            }
            is PasteResult.Failure -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    deps.settingsRepository.recordClipboardFloatPasteResult(success = false)
                }
                showPasteFailureToast(result)
            }
        }
    }

    private fun showPasteFailureToast(result: PasteResult.Failure) {
        val messageRes = when (result.reason) {
            PasteFailureReason.NO_ACTIVE_WINDOW ->
                R.string.clipboard_float_paste_no_window
            PasteFailureReason.NO_EDITABLE_FOCUS ->
                R.string.clipboard_float_paste_failed
            PasteFailureReason.PASTE_AND_INSERT_FAILED ->
                R.string.clipboard_float_paste_insert_failed
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun toggleListStyle() {
        val newStyle = if (listStyle == ClipboardFloatListStyle.SINGLE_LINE) {
            ClipboardFloatListStyle.CARD
        } else {
            ClipboardFloatListStyle.SINGLE_LINE
        }
        listStyle = newStyle
        lifecycleScope.launch(Dispatchers.IO) {
            deps.settingsRepository.setClipboardFloatListStyle(newStyle)
        }
    }

    private fun onEntryLongClick(entry: com.slideindex.app.clipboard.ClipboardEntry) {
        // 图片条目直接打开取词面板并显示图片（与收纳面板「进入取词」一致），
        // 避免只把图片文件名当作正文。
        if (entry.hasImageContent()) {
            PickResultFromHistoryCoordinator.openFromClipboard(this, entry)
        } else {
            val rawText = entry.text.trim().ifBlank { entry.uri.orEmpty() }
            if (rawText.isNotBlank()) {
                FloatBallPickResultPanel.showResult(
                    context = this,
                    result = FloatBallPickResult(
                        a11yText = rawText,
                        ocrText = null,
                        screenshot = null,
                        screenRect = null,
                        activeSource = PickResultTextSource.A11Y,
                        contentOrigin = PickResultContentOrigin.STASH_CLIPBOARD
                    ),
                    initialTextMode = PickResultTextMode.WORD_TAP
                )
            }
        }
        if (!panelPinned) {
            collapseAfterEntryAction()
        }
    }

    private fun onEntryDragStart() {
        setEntryDragHidden(hidden = true)
    }

    private fun onEntryDragEnd() {
        setEntryDragHidden(hidden = false)
    }

    private fun setEntryDragHidden(hidden: Boolean) {
        cancelEntryDragRestoreWatchdog()
        if (!viewAdded) return
        val view = composeView ?: return
        if (hidden) {
            view.visibility = View.INVISIBLE
            params.flags = ClipboardFloatWindowFlags.withNotTouchable(params.flags, notTouchable = true)
            windowManager.updateViewLayout(view, params)
            // 个别 ROM / 宿主上跨应用拖放可能不回调 ACTION_DRAG_ENDED，兜底恢复可见与可点击。
            val runnable = Runnable { setEntryDragHidden(hidden = false) }
            entryDragRestoreWatchdog = runnable
            mainHandler.postDelayed(runnable, ENTRY_DRAG_RESTORE_TIMEOUT_MS)
            return
        }
        view.visibility = View.VISIBLE
        // 只清掉起拖时加的 FLAG_NOT_TOUCHABLE。这里若整体重建 flags（原先走 updateWindowFocusForSearch），
        // 会一并抹掉 FLAG_WATCH_OUTSIDE_TOUCH，于是拖放后「点窗外关闭」失效，只能用返回键或关闭按钮。
        params.flags = ClipboardFloatWindowFlags.withNotTouchable(params.flags, notTouchable = false)
        windowManager.updateViewLayout(view, params)
    }

    private fun cancelEntryDragRestoreWatchdog() {
        entryDragRestoreWatchdog?.let(mainHandler::removeCallbacks)
        entryDragRestoreWatchdog = null
    }

    private fun performPasteHapticIfEnabled() {
        if (!pasteHapticEnabled) return
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun onSearchActiveChanged(active: Boolean) {
        if (searchActive == active) return
        searchActive = active
        if (!active && displayMode == ClipboardFloatDisplayMode.Expanded) {
            expandedRetainedWithoutIme = true
        }
        if (!viewAdded) return
        updateWindowFocusForSearch(active)
    }

    private fun updateWindowFocusForSearch(active: Boolean) {
        params.flags = ClipboardFloatWindowFlags.forMode(
            expanded = displayMode == ClipboardFloatDisplayMode.Expanded,
            keyboardFocus = active
        )
        composeView?.let { view ->
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            windowManager.updateViewLayout(view, params)
        }
    }

    companion object {
        const val ACTION_SHOW_IME = "com.slideindex.app.clipboard_float.SHOW_IME"
        const val ACTION_SHOW_EXPANDED = "com.slideindex.app.clipboard_float.SHOW_EXPANDED"
        const val ACTION_HIDE = "com.slideindex.app.clipboard_float.HIDE"
        const val ACTION_RECREATE_FOR_LOCALE = "com.slideindex.app.clipboard_float.RECREATE_FOR_LOCALE"
        const val ACTION_UPDATE_IME = "com.slideindex.app.clipboard_float.UPDATE_IME"
        const val EXTRA_IME_TOP = "ime_top"
        const val EXTRA_SHOW_CHIP = "show_chip"

        private const val IDLE_GEOMETRY_PERSIST_MS = 1500L

        /** 起拖后等待 ACTION_DRAG_ENDED 的上限，超时则兜底恢复浮窗可见与可点击 */
        private const val ENTRY_DRAG_RESTORE_TIMEOUT_MS = 10_000L

        @Volatile
        private var activeInstance: ClipboardFloatService? = null

        fun isExpandedShowing(): Boolean {
            val inst = activeInstance ?: return false
            return inst.viewAdded && inst.displayMode == ClipboardFloatDisplayMode.Expanded
        }

        fun isPinned(): Boolean {
            return activeInstance?.panelPinned ?: false
        }

        fun closeFromBack(): Boolean {
            val inst = activeInstance ?: return false
            if (inst.viewAdded && inst.displayMode == ClipboardFloatDisplayMode.Expanded) {
                inst.closeWindow()
                return true
            }
            return false
        }

        fun hideWindowFromStatic() {
            activeInstance?.hideWindow()
        }

        /**
         * 拖拽流转栏判定"内容已经发出"：先把起拖时藏起来的浮窗恢复可见，
         * 再按普通条目动作收起（与点击 / 粘贴成功后的行为一致）。
         * 置顶（[panelPinned]）时保持可见，不替用户做收起决定。
         */
        fun discardAfterDragTransfer() {
            val inst = activeInstance ?: return
            inst.mainHandler.post {
                if (!inst.viewAdded) return@post
                inst.setEntryDragHidden(hidden = false)
                if (!inst.panelPinned) inst.collapseAfterEntryAction()
            }
        }

        fun updateImeTop(context: Context, imeTop: Int) {
            context.applicationContext.startService(
                Intent(context.applicationContext, ClipboardFloatService::class.java).apply {
                    action = ACTION_UPDATE_IME
                    putExtra(EXTRA_IME_TOP, imeTop)
                }
            )
        }
    }
}
