package com.slideindex.app.overlay.ringlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.media.AudioManager
import android.os.SystemClock
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import com.slideindex.app.settings.FvRingLauncherAxis
import com.slideindex.app.settings.FvRingLauncherAxisMergeDirection
import com.slideindex.app.settings.toAxis
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.HoneycombRuntimeTarget
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.overlay.layout.FvRingLauncherSide
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.overlay.layout.FvIconShape
import com.slideindex.app.overlay.layout.FvPanelLayout
import com.slideindex.app.overlay.layout.FvToolbarButton
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FvRingLauncherLinkFlags
import com.slideindex.app.settings.FvRingLauncherSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.settings.moveFvRingLauncherSlot
import com.slideindex.app.util.HapticHelper
import kotlin.math.roundToInt
import com.slideindex.app.ui.miuix.theme.ModuleTheme

@SuppressLint("ViewConstructor")
class RingLauncherOverlayView(
    context: Context,
    private val onLaunch: (HoneycombRuntimeTarget, Boolean) -> Unit,
    private val onClosed: () -> Unit,
    private val onCircleCountChange: (Int) -> Unit,
    var onSettingsChange: (FvRingLauncherSettings) -> Unit = {},
    var onLinkAppearanceAxesChange: (Boolean, FvRingLauncherAxisMergeDirection?) -> Unit = { _, _ -> },
    var onLinkSlotAxesChange: (Boolean, FvRingLauncherAxisMergeDirection?) -> Unit = { _, _ -> },
    private val onEditModeChanged: (Boolean) -> Unit = {},
    private val onMenuVisualActiveChange: (Boolean) -> Unit = {},
    private val onPrepareDirectTouch: () -> Unit = {},
    private val onSlotsSwapped: (fromSlot: Int, toSlot: Int) -> Unit = { _, _ -> },
) : View(context) {

    private enum class SessionMode { NORMAL, EDIT }

    private val composeDialogHost = OverlayComposeDialogHost(
        context = context,
        applyTheme = { content -> ModuleTheme(settings = settings) { content() } },
    )

    private var settings = AppSettings()
    private var fvSettings = FvRingLauncherSettings()
    private var fvLinkAppearanceAxes = FvRingLauncherLinkFlags.DEFAULT_LINK_APPEARANCE_AXES
    private var fvLinkSlotAxes = FvRingLauncherLinkFlags.DEFAULT_LINK_SLOT_AXES
    private var density = 1f
    private var layoutScreenWidth = 0f
    private var targets: List<HoneycombRuntimeTarget?> = emptyList()
    private var appsByPackage: Map<String, AppInfo> = emptyMap()
    private var appRepository: AppRepository? = null

    private var activeSide: FvRingLauncherSide? = null
    private var screenAnchorX = 0f
    private var screenAnchorY = 0f
    private var panelPinned = false
    private var sessionMode = SessionMode.NORMAL
    private var sessionActive = false
    private var externalTracking = false
    private var highlightedSlot = -1
    /**
     * 长按呼出时自动选中的槽位（-1 = 不做自动选中）。
     *
     * 手指仍在圆环内圈死区时，画面上高亮它、松手也启动它，这样「长按 → 松手」就能直接启动
     * 悬浮球正下方的第一个槽位；手指一旦移到别的槽位，一切照旧以手指为准。
     */
    private var autoSelectedSlot = -1
    /**
     * 「圆环弹出那一刻手指的位置」——"动过没有"的判定基准（屏幕 raw 坐标）。
     *
     * 不能拿圆环锚点当基准：球侧锚点固定在贴边 20dp 处，与按下点天生差一段偏置
     * （48dp 球心在 24dp，按在球边更远），拿锚点算会把这段偏置先算进容差里，
     * 于是"只有按在球心才生效"。详见 [RingAutoSelectTracker]。
     */
    private var autoSelectOriginX = 0f
    private var autoSelectOriginY = 0f
    private var autoSelectSlopPx = 0f
    /**
     * 圆环弹出后手指是否移动过。一旦 [RingAutoSelectTracker.moved] 为 true 就永久作废
     * "自动选中"，之后空白/空隙一律不选（松手即取消）。
     */
    private var autoSelectTracker: RingAutoSelectTracker? = null
    private var highlightedToolbarButton: FvToolbarButton? = null
    private var lastHapticToolbarButton: FvToolbarButton? = null
    private var lastHapticHighlightedSlot = -1
    private var menuRevealProgress = 0f
    private var revealAnimator: ValueAnimator? = null
    private var panelLayout: FvPanelLayout? = null
    private var slotPressIndex = -1
    private var slotPressDownTime = 0L
    /** 提示卡片进度线自己记的"当前槽位 / 进入时间"（与 slotPress* 解耦，避免被清掉）。 */
    private var hintRingSlot = -1
    private var hintRingEnteredAt = 0L
    private var slotLongPressArmed = false
    private var slotLongPressTrackingIndex = -1
    private var slotLongPressRunnable: Runnable? = null
    private var editDragFromSlot = -1
    private var editDragX = 0f
    private var editDragY = 0f
    private var pendingEditDragSlot = -1
    private var pendingEditDragStartX = 0f
    private var pendingEditDragStartY = 0f
    private var editDragLongPressRunnable: Runnable? = null
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private var menuVisualActive = false

    private companion object {
        private const val EDIT_DRAG_LONG_PRESS_MS = 180L
        private const val EDIT_DRAG_SLOP_DP = 10f
        /** 手指离开"按下点"多远算"动过"（超过即永久取消自动选中）。 */
        private const val AUTO_SELECT_SLOP_DP = 16f
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
    }

    /** 图标库里改了应用图标：圆环是 Canvas 绘制，必须主动重绘。 */
    private val iconChangeListener: () -> Unit = { invalidate() }

    /** 系统铃声状态变化：圆环上的「声音模式 · 切换」图标要跟着变。 */
    private var ringerModeReceiver: BroadcastReceiver? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.addIconChangeListener?.invoke(iconChangeListener)
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) invalidate()
            }
        }
        ringerModeReceiver = receiver
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun configure(
        settings: AppSettings,
        fvSettings: FvRingLauncherSettings,
        fvLinkAppearanceAxes: Boolean,
        fvLinkSlotAxes: Boolean,
        targets: List<HoneycombRuntimeTarget?>,
        appsByPackage: Map<String, AppInfo>,
        appRepository: AppRepository?,
        side: FvRingLauncherSide,
        anchorX: Float,
        anchorY: Float,
        externalTracking: Boolean,
        layoutDensity: Float,
        screenWidth: Float,
        initialHighlightSlot: Int = -1,
        autoSelectOriginX: Float? = null,
        autoSelectOriginY: Float? = null,
    ) {
        this.settings = settings
        this.fvSettings = fvSettings
        this.fvLinkAppearanceAxes = fvLinkAppearanceAxes
        this.fvLinkSlotAxes = fvLinkSlotAxes
        this.targets = targets
        this.appsByPackage = appsByPackage
        this.appRepository = appRepository
        this.activeSide = side
        this.screenAnchorX = anchorX
        this.screenAnchorY = anchorY
        this.externalTracking = externalTracking
        this.density = layoutDensity
        this.layoutScreenWidth = screenWidth
        this.autoSelectedSlot = initialHighlightSlot
        // 基准点缺省退回圆环锚点：老调用方（线条侧、边缘手势、设置页预览）的锚点本来就是
        // 按下点，行为不变；悬浮球侧由调用方显式传入"按下那一刻的手指位置"。
        this.autoSelectOriginX = autoSelectOriginX ?: anchorX
        this.autoSelectOriginY = autoSelectOriginY ?: anchorY
        this.autoSelectSlopPx = AUTO_SELECT_SLOP_DP * layoutDensity
        resetAutoSelectTracking()
        rebuildLayout()
    }

    fun refreshTargets(
        fvSettings: FvRingLauncherSettings,
        targets: List<HoneycombRuntimeTarget?>,
        appsByPackage: Map<String, AppInfo>,
        appRepository: AppRepository?,
    ) {
        this.fvSettings = fvSettings
        this.targets = targets
        this.appsByPackage = appsByPackage
        this.appRepository = appRepository
        rebuildLayout()
        invalidate()
    }

    fun refreshSession(
        fvSettings: FvRingLauncherSettings,
        fvLinkAppearanceAxes: Boolean,
        fvLinkSlotAxes: Boolean,
        targets: List<HoneycombRuntimeTarget?>,
        appsByPackage: Map<String, AppInfo>,
    ) {
        this.fvSettings = fvSettings
        this.fvLinkAppearanceAxes = fvLinkAppearanceAxes
        this.fvLinkSlotAxes = fvLinkSlotAxes
        this.targets = targets
        this.appsByPackage = appsByPackage
        rebuildLayout()
        invalidate()
    }

    fun isVisibleSession(): Boolean = sessionActive

    fun isPinned(): Boolean = panelPinned && sessionActive

    fun beginSession() {
        rebuildLayout()
        sessionActive = true
        panelPinned = false
        val hasConfiguredSlots = fvSettings.configuredCount() > 0
        sessionMode = if (!hasConfiguredSlots) SessionMode.EDIT else SessionMode.NORMAL
        resetAutoSelectTracking()
        highlightedSlot = if (sessionMode == SessionMode.NORMAL && autoSelectedSlot >= 0) {
            autoSelectedSlot
        } else {
            -1
        }
        lastHapticHighlightedSlot = highlightedSlot
        highlightedToolbarButton = null
        menuRevealProgress = if (externalTracking) 1f else 0f
        cancelSlotLongPress()
        cancelEditDrag()
        revealAnimator?.cancel()
        if (!externalTracking) {
            animateMenuReveal()
        } else {
            setMenuVisualActive(true)
            HapticHelper.gestureStart(this, settings)
        }
        invalidate()
    }

    fun onExternalMove(rawX: Float, rawY: Float) {
        if (!sessionActive) return
        updateInteraction(rawX, rawY, System.currentTimeMillis())
        invalidate()
    }

    fun onExternalUp(rawX: Float, rawY: Float, cancelled: Boolean) {
        if (!sessionActive) return
        if (cancelled) {
            dismissPanel()
            return
        }
        handleRelease(rawX, rawY, System.currentTimeMillis(), fromPinned = false)
    }

    fun onExternalCancel() {
        dismissPanel()
    }

    fun enableDirectTouch() {
        externalTracking = false
    }

    fun pinForLeaveOpen() {
        if (!sessionActive || panelPinned) return
        pinPanel()
    }

    fun dismissNow() {
        dismissPanel()
    }

    private fun rebuildLayout() {
        val side = activeSide ?: return
        val screenWidth = if (layoutScreenWidth > 0f) {
            layoutScreenWidth
        } else {
            resources.displayMetrics.widthPixels.toFloat()
        }
        panelLayout = RingLauncherRenderer.buildLayout(
            circleCount = fvSettings.circleCount,
            side = side,
            anchorX = screenAnchorX,
            anchorY = screenAnchorY,
            screenWidth = screenWidth,
            density = density,
            iconSizeDp = fvSettings.iconSizeDp,
            iconShape = fvSettings.iconShape,
            baseRadiusDp = fvSettings.baseRadiusDp,
            layerGapDp = fvSettings.layerGapDp,
            endMarginDeg = fvSettings.endMarginDeg,
        )
    }

    private fun updateInteraction(localX: Float, localY: Float, eventTime: Long) {
        val layout = panelLayout ?: return
        val toolbarActive = fvSettings.showToolbar && (panelPinned || externalTracking)
        highlightedToolbarButton = if (toolbarActive) {
            FvCircleLayoutEngine.toolbarButtonAt(layout, localX, localY)
        } else {
            null
        }
        if (highlightedToolbarButton != lastHapticToolbarButton && highlightedToolbarButton != null) {
            lastHapticToolbarButton = highlightedToolbarButton
            HapticHelper.appTick(this, settings)
        } else if (highlightedToolbarButton == null) {
            lastHapticToolbarButton = null
        }
        val pointerSlot = if (highlightedToolbarButton == null &&
            !FvCircleLayoutEngine.isOutsidePanel(layout, localX, localY, layout.itemSizePx * 0.35f)
        ) {
            FvCircleLayoutEngine.slotIndexAt(layout, localX, localY)
        } else {
            -1
        }
        // 自动选中只属于"手指还没动"的那一刻：以**圆环弹出时手指所在的位置**为基准，
        // 走开超过阈值就永久作废（拿圆环锚点当基准会让"按在球边"天生就超阈值，
        // 表现为只有按球心才生效）。作废后一切以手指指向为准，空白/空隙一律不选（松手即取消）。
        autoSelectTracker?.update(localX, localY)
        val autoSelectAlive = autoSelectTracker?.moved == false
        val slot = if (pointerSlot < 0 &&
            autoSelectedSlot >= 0 &&
            autoSelectAlive &&
            sessionMode == SessionMode.NORMAL &&
            highlightedToolbarButton == null
        ) {
            autoSelectedSlot
        } else {
            pointerSlot
        }
        if (slot != highlightedSlot) {
            highlightedSlot = slot
            // 提示卡片的进度线自己记"进到这个槽位的时间"：不依赖 slotPressIndex，
            // 避免长按触发后底层把 press 状态清掉导致进度线消失。
            if (slot != hintRingSlot) {
                hintRingSlot = slot
                hintRingEnteredAt = SystemClock.uptimeMillis()
            }
            if (slot >= 0 && slot != lastHapticHighlightedSlot) {
                lastHapticHighlightedSlot = slot
                HapticHelper.appTick(this, settings)
            } else if (slot < 0) {
                lastHapticHighlightedSlot = -1
            }
        }
        if (sessionMode == SessionMode.EDIT) {
            syncEditDragTracking(slot, localX, localY)
        }
        syncSlotPressTracking(slot, eventTime)
    }

    /** 每次（重新）开始会话前复位"手指动过没有"：基准点还是配置时那个按下点。 */
    private fun resetAutoSelectTracking() {
        autoSelectTracker = if (autoSelectedSlot >= 0) {
            RingAutoSelectTracker(
                originX = autoSelectOriginX,
                originY = autoSelectOriginY,
                slopPx = autoSelectSlopPx,
            )
        } else {
            null
        }
    }

    private fun syncEditDragTracking(slot: Int, localX: Float, localY: Float) {
        if (editDragFromSlot >= 0) {
            editDragX = localX
            editDragY = localY
            return
        }
        if (pendingEditDragSlot >= 0 && slot != pendingEditDragSlot) {
            val slop = EDIT_DRAG_SLOP_DP * density
            val dx = localX - pendingEditDragStartX
            val dy = localY - pendingEditDragStartY
            if (dx * dx + dy * dy > slop * slop) {
                cancelPendingEditDrag()
            }
        }
    }

    private fun syncSlotPressTracking(slot: Int, eventTime: Long) {
        if (editDragFromSlot >= 0 || pendingEditDragSlot >= 0) return
        if (slot >= 0) {
            if (slot != slotPressIndex) {
                slotPressIndex = slot
                slotPressDownTime = eventTime
                slotLongPressArmed = false
                scheduleSlotLongPress(slot)
            }
        } else if (!slotLongPressArmed) {
            cancelSlotLongPress()
        } else {
            cancelSlotLongPressPending()
        }
    }

    private fun handleRelease(localX: Float, localY: Float, eventTime: Long, fromPinned: Boolean): Boolean {
        val layout = panelLayout ?: return false
        // 先记住"松手前画面上高亮的槽位"：长按不动时它就是自动选中的那一格。
        // 松手时再算一次交互可能因为手指最后几像素的抖动把高亮清掉，导致"看着选中了却没启动"。
        val highlightedBefore = highlightedSlot
        updateInteraction(localX, localY, eventTime)
        val toolbarButton = if (fvSettings.showToolbar) {
            FvCircleLayoutEngine.toolbarButtonAt(layout, localX, localY)
        } else {
            null
        }
        val slot = FvCircleLayoutEngine.slotIndexAt(layout, localX, localY)
        val outside = FvCircleLayoutEngine.isOutsidePanel(layout, localX, localY, layout.itemSizePx * 0.35f) &&
            toolbarButton == null
        // 只有"从圆环弹出到现在手指一直没动过"才允许松手启动自动选中格。
        // 动过的判断在 updateInteraction 里已经永久生效（autoSelectTracker），这里再以
        // "松手前画面上确实高亮着它"（highlightedBefore）确认一次，避免松手那一下的重算干扰。
        val autoActive = autoSelectedSlot >= 0 &&
            sessionMode == SessionMode.NORMAL &&
            !outside &&
            autoSelectTracker?.moved == false &&
            highlightedBefore == autoSelectedSlot
        val releaseSlot = when {
            slot >= 0 -> slot
            autoActive -> autoSelectedSlot
            else -> -1
        }

        when {
            toolbarButton == FvToolbarButton.PIN -> {
                prepareForToolbarAction()
                pinPanel()
                enterEditMode()
                return true
            }
            toolbarButton == FvToolbarButton.MOVE -> {
                prepareForToolbarAction()
                dismissPanel()
                // 「移动」＝把悬浮球切到自由位置模式（保持当前所在位置），之后在屏幕上拖动球即可移动它。
                // 原先这里只是 dismissPanel()，与「−」完全重复，等于没做功能。
                com.slideindex.app.overlay.panels.PanelFloatBallHooks.enterFreeMoveMode?.invoke()
                return true
            }
            toolbarButton == FvToolbarButton.SETTINGS -> {
                prepareForToolbarAction()
                pinPanel()
                post { showAppearanceDialog() }
                return true
            }
            toolbarButton == FvToolbarButton.KEYBOARD -> {
                val wasEdit = sessionMode == SessionMode.EDIT
                prepareForToolbarAction()
                pinPanel()
                if (wasEdit) enterEditMode()
                // 这里必须用 switchInputMethod（与圆环槽位/手势里的「切换输入法」同一入口）：
                // 裸调 InputMethodManager.showInputMethodPicker() 要求**当前窗口持有输入法焦点**，
                // 圆环是 overlay 窗口拿不到 IME 焦点，会被系统静默忽略（点了像没反应）。
                // switchInputMethod 内部有"临时挂一个可获焦点的 accessibility overlay"兜底。
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.switchInputMethod?.invoke(context)
                return true
            }
            sessionMode == SessionMode.EDIT && editDragFromSlot >= 0 -> {
                commitEditDrag(slot)
                return true
            }
            sessionMode == SessionMode.EDIT && slot >= 0 -> {
                prepareForToolbarAction()
                openSlotPicker(slot)
                return true
            }
            fromPinned && outside -> {
                dismissPanel()
                return true
            }
            fromPinned && slot >= 0 -> {
                launchSlot(slot, eventTime)
                return true
            }
            !fromPinned && releaseSlot >= 0 -> {
                launchSlot(releaseSlot, eventTime)
                return true
            }
            !fromPinned -> {
                val wasEdit = sessionMode == SessionMode.EDIT
                if (fvSettings.showToolbar && FvCircleLayoutEngine.isNearToolbar(layout, localX, localY)) {
                    prepareForToolbarAction()
                    pinPanel()
                    if (wasEdit) enterEditMode()
                } else {
                    dismissPanel()
                }
                return true
            }
            else -> {
                dismissPanel()
                return true
            }
        }
    }

    private fun prepareForToolbarAction() {
        if (!externalTracking) return
        onPrepareDirectTouch()
        externalTracking = false
    }

    private fun launchSlot(slot: Int, eventTime: Long) {
        val target = targets.getOrNull(slot) ?: return
        val longPressArmed = slotLongPressTriggered(slot, eventTime)
        if (longPressArmed) {
            HapticHelper.confirmLaunch(this, settings)
        }
        cancelSlotLongPress()
        dismissPanel()
        onLaunch(target, longPressArmed)
    }

    private fun slotLongPressTriggered(slot: Int, eventTime: Long): Boolean {
        if (slotLongPressArmed) return true
        if (!settings.launchPolicyLongPressEligible()) return false
        val target = targets.getOrNull(slot) ?: return false
        if (target.item.type != QuickLauncherItemType.APP) return false
        if (slotPressIndex < 0 || slotPressIndex != slot) return false
        return eventTime - slotPressDownTime >= settings.effectiveLongPressDurationMs()
    }

    private fun pinPanel() {
        val wasEdit = sessionMode == SessionMode.EDIT
        revealAnimator?.cancel()
        panelPinned = true
        sessionActive = true
        sessionMode = SessionMode.NORMAL
        setMenuVisualActive(true)
        menuRevealProgress = 1f
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedToolbarButton = null
        cancelSlotLongPress()
        cancelEditDrag()
        prepareForToolbarAction()
        if (wasEdit) {
            onEditModeChanged(false)
        }
        invalidate()
    }

    private fun enterEditMode() {
        panelPinned = true
        sessionMode = SessionMode.EDIT
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        cancelSlotLongPress()
        cancelEditDrag()
        onEditModeChanged(true)
        invalidate()
    }

    private fun showAppearanceDialog() {
        composeDialogHost.show {
            RingLauncherAppearanceDialogContent(
                currentSettings = fvSettings,
                activeAxis = activeSide?.toAxis() ?: FvRingLauncherAxis.VERTICAL,
                linkAppearanceAxes = fvLinkAppearanceAxes,
                linkSlotAxes = fvLinkSlotAxes,
                onSettingsChange = { next ->
                    fvSettings = next
                    rebuildLayout()
                    invalidate()
                    this@RingLauncherOverlayView.onSettingsChange(next)
                },
                onLinkAppearanceAxesChange = { enabled, mergeDirection ->
                    if (enabled) fvLinkAppearanceAxes = true
                    else fvLinkAppearanceAxes = false
                    this@RingLauncherOverlayView.onLinkAppearanceAxesChange(enabled, mergeDirection)
                },
                onLinkSlotAxesChange = { enabled, mergeDirection ->
                    if (enabled) fvLinkSlotAxes = true
                    else fvLinkSlotAxes = false
                    this@RingLauncherOverlayView.onLinkSlotAxesChange(enabled, mergeDirection)
                },
                onDismiss = {
                    composeDialogHost.dismiss()
                },
            )
        }
    }

    private fun dismissPanel() {
        revealAnimator?.cancel()
        composeDialogHost.dismiss()
        clearSessionState()
        onClosed()
    }

    private fun clearSessionState() {
        sessionActive = false
        panelPinned = false
        sessionMode = SessionMode.NORMAL
        setMenuVisualActive(false)
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedToolbarButton = null
        lastHapticToolbarButton = null
        menuRevealProgress = 0f
        cancelSlotLongPress()
        cancelEditDrag()
        invalidate()
    }

    private fun scheduleEditDrag(slot: Int, localX: Float, localY: Float) {
        cancelPendingEditDrag()
        if (targets.getOrNull(slot) == null) return
        pendingEditDragSlot = slot
        pendingEditDragStartX = localX
        pendingEditDragStartY = localY
        val runnable = Runnable {
            if (pendingEditDragSlot != slot) return@Runnable
            editDragFromSlot = slot
            editDragX = pendingEditDragStartX
            editDragY = pendingEditDragStartY
            pendingEditDragSlot = -1
            cancelSlotLongPress()
            HapticHelper.appTick(this, settings)
            invalidate()
        }
        editDragLongPressRunnable = runnable
        postDelayed(runnable, EDIT_DRAG_LONG_PRESS_MS)
    }

    private fun cancelPendingEditDrag() {
        editDragLongPressRunnable?.let { removeCallbacks(it) }
        editDragLongPressRunnable = null
        pendingEditDragSlot = -1
    }

    private fun cancelEditDrag() {
        cancelPendingEditDrag()
        editDragFromSlot = -1
        editDragX = 0f
        editDragY = 0f
    }

    private fun commitEditDrag(dropSlot: Int) {
        val fromSlot = editDragFromSlot
        val toSlot = if (dropSlot >= 0) dropSlot else highlightedSlot
        cancelEditDrag()
        if (fromSlot < 0 || toSlot < 0 || fromSlot == toSlot) return
        applyLocalSlotMove(fromSlot, toSlot)
        onSlotsSwapped(fromSlot, toSlot)
    }

    private fun applyLocalSlotMove(fromSlot: Int, toSlot: Int) {
        val mutableTargets = targets.toMutableList()
        if (!mutableTargets.indices.contains(fromSlot) || !mutableTargets.indices.contains(toSlot)) return
        val fromTarget = mutableTargets[fromSlot] ?: return
        val toTarget = mutableTargets[toSlot]
        mutableTargets[fromSlot] = toTarget
        mutableTargets[toSlot] = fromTarget
        targets = mutableTargets

        val mutableSlots = fvSettings.slots.toMutableMap()
        if (!mutableSlots.moveFvRingLauncherSlot(fromSlot, toSlot)) return
        fvSettings = fvSettings.copy(slots = mutableSlots)
        invalidate()
    }

    private fun setMenuVisualActive(active: Boolean) {
        if (menuVisualActive == active) return
        menuVisualActive = active
        onMenuVisualActiveChange(active)
    }

    private fun animateMenuReveal() {
        revealAnimator?.cancel()
        revealAnimator = ValueAnimator.ofFloat(menuRevealProgress, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                menuRevealProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    setMenuVisualActive(true)
                    HapticHelper.gestureStart(this@RingLauncherOverlayView, settings)
                }
            })
            start()
        }
    }

    private fun scheduleSlotLongPress(slot: Int) {
        cancelSlotLongPressPending()
        if (!settings.launchPolicyLongPressEligible()) return
        val target = targets.getOrNull(slot) ?: return
        if (target.item.type != QuickLauncherItemType.APP) return
        slotLongPressTrackingIndex = slot
        val runnable = Runnable {
            if (slotPressIndex == slotLongPressTrackingIndex && slotLongPressTrackingIndex >= 0) {
                slotLongPressArmed = true
                HapticHelper.longThreshold(this, settings)
                invalidate()
            }
        }
        slotLongPressRunnable = runnable
        postDelayed(runnable, settings.effectiveLongPressDurationMs().toLong())
    }

    private fun cancelSlotLongPressPending() {
        slotLongPressRunnable?.let { removeCallbacks(it) }
        slotLongPressRunnable = null
        slotLongPressTrackingIndex = -1
    }

    private fun cancelSlotLongPress() {
        cancelSlotLongPressPending()
        slotLongPressArmed = false
        slotPressIndex = -1
        slotPressDownTime = 0L
    }

    private fun openSlotPicker(slotIndex: Int) {
        RingLauncherOverlayWindow.openSlotPicker(slotIndex)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!sessionActive) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateInteraction(event.rawX, event.rawY, event.eventTime)
                if (sessionMode == SessionMode.EDIT && highlightedToolbarButton == null) {
                    val layout = panelLayout
                    val slot = if (layout != null) {
                        FvCircleLayoutEngine.slotIndexAt(layout, event.rawX, event.rawY)
                    } else {
                        -1
                    }
                    if (slot >= 0 && targets.getOrNull(slot) != null) {
                        scheduleEditDrag(slot, event.rawX, event.rawY)
                    }
                }
                invalidate()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                updateInteraction(event.rawX, event.rawY, event.eventTime)
                invalidate()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingEditDrag()
                val handled = handleRelease(event.rawX, event.rawY, event.eventTime, fromPinned = panelPinned)
                if (handled) performClick()
                handled
            }
            else -> false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = panelLayout ?: return
        val revealProgress = if (panelPinned || externalTracking) 1f else menuRevealProgress
        val hintRing = hintRingProgress()
        val (modeSuffix, modePip) = hintLongPressMode()
        // 按住期间逐帧刷新（进度线按"按住时长"实时推进）
        if (hintRing in 0f..0.999f) postInvalidateOnAnimation()
        drawBackgroundMask(canvas, revealProgress)
        RingLauncherRenderer.draw(
            context = context,
            canvas = canvas,
            layout = layout,
            targets = targets,
            editMode = sessionMode == SessionMode.EDIT,
            highlightedSlot = highlightedSlot,
            highlightedToolbarButton = highlightedToolbarButton,
            showToolbar = fvSettings.showToolbar && (panelPinned || externalTracking),
            density = density,
            revealProgress = revealProgress,
            appsByPackage = appsByPackage,
            activityShortcuts = settings.activityShortcuts,
            shellCommands = settings.shellCommands,
            appRepository = appRepository,
            editDragFromSlot = editDragFromSlot,
            editDragX = editDragX,
            editDragY = editDragY,
            slotIconOverrides = fvSettings.slotIconOverrides,
            hintRingProgress = hintRing,
            hintLabelSuffix = modeSuffix,
            hintPipArmed = modePip,
        )
    }

    /**
     * 提示卡片里图标外圈的进度（0..1）；**-1 = 不画**。
     *
     * 就是"按住这个图标多久了"的长按倒计时：按下开始走、走满停住、抬手消失。
     * 与快捷轮盘提示条里的那条线同源同时长（`effectiveLongPressDurationMs()`）。
     */
    private fun hintRingProgress(): Float {
        if (sessionMode == SessionMode.EDIT) return -1f
        // 只在小窗功能开着、且启动策略是「长按触发」那一档时才画。
        if (!settings.launchPolicyLongPressEligible()) return -1f
        val slot = hintRingSlot
        if (slot < 0 || slot != highlightedSlot) return -1f
        val target = targets.getOrNull(slot) ?: return -1f
        // 只给 App / 快捷方式画：内置动作、Shell 命令、控件、文件夹这些槽位
        // 长按没有"小窗/全屏"语义。
        val type = target.item.type
        if (type != QuickLauncherItemType.APP && type != QuickLauncherItemType.SHORTCUT) return -1f
        val elapsed = SystemClock.uptimeMillis() - hintRingEnteredAt
        if (elapsed < 0) return -1f
        val duration = settings.effectiveLongPressDurationMs().coerceAtLeast(1)
        return (elapsed.toFloat() / duration).coerceIn(0f, 1f)
    }

    /**
     * 长按到时间后，名字后面的后缀与"小窗"角标。
     *
     * 目标形态由启动策略决定（`shouldLaunchFullscreen(longPressTriggered = true)`）：
     * 全屏 → "-全屏"、不画角标；小窗 → "-小窗" + 图标右下角画中画角标。
     */
    private fun hintLongPressMode(): Pair<String, Boolean> {
        // 不再要求"小窗功能已开"：到时间就把目标形态写出来（小窗没开时就是 -全屏），
        // 这样用户能立刻看到长按到底会发生什么。PiP 角标只在目标是小窗时才画。
        return if (settings.shouldLaunchFullscreen(longPressTriggered = true)) {
            context.getString(R.string.fv_hint_suffix_fullscreen) to false
        } else {
            context.getString(R.string.fv_hint_suffix_free_window) to true
        }
    }

    private fun drawBackgroundMask(canvas: Canvas, progress: Float) {
        if (progress <= 0.01f) return
        val alpha = (255f * 0.58f * progress).toInt().coerceIn(0, 255)
        dimPaint.alpha = alpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }

    override fun onDetachedFromWindow() {
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.removeIconChangeListener?.invoke(iconChangeListener)
        ringerModeReceiver?.let { receiver ->
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
        }
        ringerModeReceiver = null
        super.onDetachedFromWindow()
        composeDialogHost.dismiss()
    }
}

@Composable
fun RingLauncherAppearanceDialogContent(
    currentSettings: FvRingLauncherSettings,
    activeAxis: FvRingLauncherAxis,
    linkAppearanceAxes: Boolean,
    linkSlotAxes: Boolean,
    onSettingsChange: (FvRingLauncherSettings) -> Unit,
    onLinkAppearanceAxesChange: (Boolean, FvRingLauncherAxisMergeDirection?) -> Unit,
    onLinkSlotAxesChange: (Boolean, FvRingLauncherAxisMergeDirection?) -> Unit,
    onDismiss: () -> Unit,
) {
    var settingsState by remember(currentSettings) { mutableStateOf(currentSettings) }
    var linkAppearanceState by remember(linkAppearanceAxes) { mutableStateOf(linkAppearanceAxes) }
    var linkSlotState by remember(linkSlotAxes) { mutableStateOf(linkSlotAxes) }
    var pendingMergeTarget by remember { mutableStateOf<FvRingLauncherLinkMergeTarget?>(null) }
    val scrollState = rememberScrollState()

    fun update(transform: (FvRingLauncherSettings) -> FvRingLauncherSettings) {
        val next = transform(settingsState)
        settingsState = next
        onSettingsChange(next)
    }

    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val isLandscape = windowSize.width > windowSize.height
    val dialogMaxHeight = with(density) { windowSize.height.toDp() * 0.9f }
    val surfaceVerticalPadding = if (isLandscape) 12.dp else 24.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .heightIn(max = dialogMaxHeight)
                .padding(vertical = surfaceVerticalPadding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    Text(
                        text = stringResource(R.string.fv_ring_launcher_appearance_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.fv_ring_launcher_circle_count_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FvRingLauncherLinkSwitchRow(
                                title = stringResource(R.string.fv_ring_launcher_link_appearance_axes_title),
                                description = stringResource(R.string.fv_ring_launcher_link_appearance_axes_desc),
                                checked = linkAppearanceState,
                                compact = true,
                                modifier = Modifier.weight(1f),
                                onCheckedChange = { checked ->
                                    if (checked) return@FvRingLauncherLinkSwitchRow
                                    linkAppearanceState = false
                                    onLinkAppearanceAxesChange(false, null)
                                },
                                onRequestEnable = { pendingMergeTarget = FvRingLauncherLinkMergeTarget.APPEARANCE },
                            )
                            FvRingLauncherLinkSwitchRow(
                                title = stringResource(R.string.fv_ring_launcher_link_slot_axes_title),
                                description = stringResource(R.string.fv_ring_launcher_link_slot_axes_desc),
                                checked = linkSlotState,
                                compact = true,
                                modifier = Modifier.weight(1f),
                                onCheckedChange = { checked ->
                                    if (checked) return@FvRingLauncherLinkSwitchRow
                                    linkSlotState = false
                                    onLinkSlotAxesChange(false, null)
                                },
                                onRequestEnable = { pendingMergeTarget = FvRingLauncherLinkMergeTarget.SLOTS },
                            )
                        }
                    } else {
                        FvRingLauncherLinkSwitchRow(
                            title = stringResource(R.string.fv_ring_launcher_link_appearance_axes_title),
                            description = stringResource(R.string.fv_ring_launcher_link_appearance_axes_desc),
                            checked = linkAppearanceState,
                            onCheckedChange = { checked ->
                                if (checked) return@FvRingLauncherLinkSwitchRow
                                linkAppearanceState = false
                                onLinkAppearanceAxesChange(false, null)
                            },
                            onRequestEnable = { pendingMergeTarget = FvRingLauncherLinkMergeTarget.APPEARANCE },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FvRingLauncherLinkSwitchRow(
                            title = stringResource(R.string.fv_ring_launcher_link_slot_axes_title),
                            description = stringResource(R.string.fv_ring_launcher_link_slot_axes_desc),
                            checked = linkSlotState,
                            onCheckedChange = { checked ->
                                if (checked) return@FvRingLauncherLinkSwitchRow
                                linkSlotState = false
                                onLinkSlotAxesChange(false, null)
                            },
                            onRequestEnable = { pendingMergeTarget = FvRingLauncherLinkMergeTarget.SLOTS },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.fv_ring_launcher_circle_count_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FvRingLauncherAppearanceChipGrid(
                        isLandscape = isLandscape,
                        labels = listOf(
                            stringResource(R.string.fv_ring_launcher_circle_1),
                            stringResource(R.string.fv_ring_launcher_circle_2),
                            stringResource(R.string.fv_ring_launcher_circle_3),
                            stringResource(R.string.fv_ring_launcher_circle_4),
                        ),
                        selectedIndex = settingsState.circleCount - 1,
                        onSelected = { index -> update { it.copy(circleCount = index + 1) } },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.fv_ring_launcher_icon_shape_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FvRingLauncherAppearanceChipGrid(
                        isLandscape = isLandscape,
                        labels = listOf(
                            stringResource(R.string.fv_icon_shape_rounded_rect),
                            stringResource(R.string.fv_icon_shape_circle),
                            stringResource(R.string.fv_icon_shape_squircle),
                            stringResource(R.string.fv_icon_shape_square),
                        ),
                        selectedIndex = listOf(
                            FvIconShape.ROUNDED_RECT,
                            FvIconShape.CIRCLE,
                            FvIconShape.SQUIRCLE,
                            FvIconShape.SQUARE,
                        ).indexOf(settingsState.iconShape).coerceAtLeast(0),
                        onSelected = { index ->
                            val shape = listOf(
                                FvIconShape.ROUNDED_RECT,
                                FvIconShape.CIRCLE,
                                FvIconShape.SQUIRCLE,
                                FvIconShape.SQUARE,
                            )[index]
                            update { it.copy(iconShape = shape) }
                        },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AppearanceSliderRow(
                        title = stringResource(R.string.fv_ring_launcher_icon_size_title),
                        valueText = "${settingsState.iconSizeDp.toInt()} dp",
                        value = settingsState.iconSizeDp,
                        range = FvRingLauncherSettings.MIN_ICON_SIZE_DP..FvRingLauncherSettings.MAX_ICON_SIZE_DP,
                        steps = ((FvRingLauncherSettings.MAX_ICON_SIZE_DP - FvRingLauncherSettings.MIN_ICON_SIZE_DP) / 2f).toInt() - 1,
                        onValueChange = { update { s -> s.copy(iconSizeDp = (it / 2f).roundToInt() * 2f) } },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AppearanceSliderRow(
                        title = stringResource(R.string.fv_ring_launcher_base_radius_title),
                        valueText = "${settingsState.baseRadiusDp.toInt()} dp",
                        value = settingsState.baseRadiusDp,
                        range = FvRingLauncherSettings.MIN_BASE_RADIUS_DP..FvRingLauncherSettings.MAX_BASE_RADIUS_DP,
                        steps = ((FvRingLauncherSettings.MAX_BASE_RADIUS_DP - FvRingLauncherSettings.MIN_BASE_RADIUS_DP) / 2f).toInt() - 1,
                        onValueChange = { update { s -> s.copy(baseRadiusDp = (it / 2f).roundToInt() * 2f) } },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AppearanceSliderRow(
                        title = stringResource(R.string.fv_ring_launcher_layer_gap_title),
                        valueText = "${settingsState.layerGapDp.toInt()} dp",
                        value = settingsState.layerGapDp,
                        range = FvRingLauncherSettings.MIN_LAYER_GAP_DP..FvRingLauncherSettings.MAX_LAYER_GAP_DP,
                        steps = ((FvRingLauncherSettings.MAX_LAYER_GAP_DP - FvRingLauncherSettings.MIN_LAYER_GAP_DP) / 2f).toInt() - 1,
                        onValueChange = { update { s -> s.copy(layerGapDp = (it / 2f).roundToInt() * 2f) } },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AppearanceSliderRow(
                        title = stringResource(R.string.fv_ring_launcher_end_margin_title),
                        valueText = "${settingsState.endMarginDeg.toInt()}°",
                        value = settingsState.endMarginDeg,
                        range = FvRingLauncherSettings.MIN_END_MARGIN_DEG..FvRingLauncherSettings.MAX_END_MARGIN_DEG,
                        steps = ((FvRingLauncherSettings.MAX_END_MARGIN_DEG - FvRingLauncherSettings.MIN_END_MARGIN_DEG) / 2f).toInt() - 1,
                        onValueChange = { update { s -> s.copy(endMarginDeg = (it / 2f).roundToInt() * 2f) } },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    FvRingLauncherLinkSwitchRow(
                        title = stringResource(R.string.fv_ring_launcher_show_toolbar_title),
                        description = stringResource(R.string.fv_ring_launcher_show_toolbar_desc),
                        checked = settingsState.showToolbar,
                        onCheckedChange = { checked -> update { it.copy(showToolbar = checked) } },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            val reset = FvRingLauncherSettings(
                                circleCount = settingsState.circleCount,
                                slots = settingsState.slots,
                            )
                            update { reset }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.fv_ring_launcher_reset_defaults),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.fv_ring_launcher_done),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        pendingMergeTarget?.let { target ->
            val mergeKindLabel = stringResource(
                when (target) {
                    FvRingLauncherLinkMergeTarget.APPEARANCE ->
                        R.string.fv_ring_launcher_link_merge_appearance_kind
                    FvRingLauncherLinkMergeTarget.SLOTS ->
                        R.string.fv_ring_launcher_link_merge_slot_kind
                },
            )
            FvRingLauncherLinkMergeOverlay(
                mergeKindLabel = mergeKindLabel,
                activeAxis = activeAxis,
                onDismiss = { pendingMergeTarget = null },
                onConfirm = { mergeDirection ->
                    when (target) {
                        FvRingLauncherLinkMergeTarget.APPEARANCE -> {
                            linkAppearanceState = true
                            onLinkAppearanceAxesChange(true, mergeDirection)
                        }
                        FvRingLauncherLinkMergeTarget.SLOTS -> {
                            linkSlotState = true
                            onLinkSlotAxesChange(true, mergeDirection)
                        }
                    }
                    pendingMergeTarget = null
                },
            )
        }
    }
}

private enum class FvRingLauncherLinkMergeTarget {
    APPEARANCE,
    SLOTS,
}

@Composable
private fun FvRingLauncherAppearanceChipGrid(
    isLandscape: Boolean,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    if (isLandscape) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (col in 0..1) {
                        val index = row * 2 + col
                        val selected = selectedIndex == index
                        FilterChip(
                            selected = selected,
                            onClick = { onSelected(index) },
                            label = {
                                Text(
                                    text = labels[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val selected = selectedIndex == index
                FilterChip(
                    selected = selected,
                    onClick = { onSelected(index) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FvRingLauncherLinkSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRequestEnable: () -> Unit = { onCheckedChange(true) },
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { enabled ->
                if (enabled) {
                    onRequestEnable()
                } else {
                    onCheckedChange(false)
                }
            },
        )
    }
}

@Composable
private fun FvRingLauncherLinkMergeOverlay(
    mergeKindLabel: String,
    activeAxis: FvRingLauncherAxis,
    onDismiss: () -> Unit,
    onConfirm: (FvRingLauncherAxisMergeDirection) -> Unit,
) {
    val currentAxisLabel = stringResource(
        if (activeAxis == FvRingLauncherAxis.VERTICAL) {
            R.string.fv_ring_launcher_axis_vertical
        } else {
            R.string.fv_ring_launcher_axis_horizontal
        },
    )
    val otherAxisLabel = stringResource(
        if (activeAxis == FvRingLauncherAxis.VERTICAL) {
            R.string.fv_ring_launcher_axis_horizontal
        } else {
            R.string.fv_ring_launcher_axis_vertical
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.88f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.fv_ring_launcher_link_merge_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.fv_ring_launcher_link_merge_message,
                        mergeKindLabel,
                        currentAxisLabel,
                        otherAxisLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { onConfirm(FvRingLauncherAxisMergeDirection.USE_OTHER_AXIS) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.fv_ring_launcher_link_merge_use_other))
                }
                TextButton(
                    onClick = { onConfirm(FvRingLauncherAxisMergeDirection.USE_CURRENT_AXIS) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.fv_ring_launcher_link_merge_use_current))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun AppearanceSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
