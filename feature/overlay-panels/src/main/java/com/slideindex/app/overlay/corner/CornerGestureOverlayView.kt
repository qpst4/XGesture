package com.slideindex.app.overlay.corner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.isCornerInnerZoneOnly
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.HapticHelper
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
class CornerGestureOverlayView(
    context: Context,
    private val appRepository: AppRepository,
    private val onSessionEnd: () -> Unit,
    private val onReleaseCapture: () -> Unit = {},
    private val onOpenSlotPicker: (CornerAnchor, Int) -> Unit = { _, _ -> },
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit,
    private val gestureRunner: com.slideindex.app.overlay.panels.GestureActionRunner,
    private val onMenuVisualActiveChange: (Boolean) -> Unit = {},
) : View(context) {

    private enum class SessionMode { NORMAL, EDIT }

    private var settings = AppSettings()
    private var cornerSettings = CornerGestureSettings()
    private var density = 1f

    private var activeAnchor: CornerAnchor? = null
    private var wheelPinned = false
    private var sessionMode = SessionMode.NORMAL
    private var menuActive = false
    private var activated = false
    private var startRawX = 0f
    private var startRawY = 0f
    private var highlightedSlot = -1
    private var lastHapticHighlightedSlot = -1
    private var highlightedEditButton = false
    private var activeLayerCount = 1
    private var menuActivationRadDist = 0f
    private var maxInwardSlop = 0f
    private var menuRevealProgress = 0f
    private var revealAnimator: ValueAnimator? = null
    private var editModeEntered = false
    private var slotPressIndex = -1
    private var slotPressDownTime = 0L
    /** 提示卡片进度线自己记的"当前槽位 / 进入时间"（与 slotPress* 解耦）。 */
    private var hintRingSlot = -1
    private var hintRingEnteredAt = 0L
    private var slotLongPressArmed = false
    private var slotLongPressTrackingIndex = -1
    private var slotLongPressRunnable: Runnable? = null
    private var shortcutSubMenuVisible = false
    private var shortcutSubMenuSlot = -1
    private var shortcutSubMenuLayout: CornerShortcutSubMenuLayout? = null
    private var highlightedShortcutIndex = -1
    private var lastHapticHighlightedShortcutIndex = -1
    private var shortcutSubMenuRevealProgress = 0f
    private var subMenuRevealAnimator: ValueAnimator? = null
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private var menuVisualActive = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
    }

    /** 系统铃声状态变化：轮盘上的「声音模式 · 切换」图标要跟着变，Canvas 需要主动重绘。 */
    private var ringerModeReceiver: BroadcastReceiver? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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

    override fun onDetachedFromWindow() {
        ringerModeReceiver?.let { receiver ->
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
        }
        ringerModeReceiver = null
        super.onDetachedFromWindow()
    }

    fun applySettings(settings: AppSettings, density: Float) {
        this.settings = settings
        this.cornerSettings = settings.cornerGestureSettings
        this.density = density
        if (menuVisualActive) {
            onMenuVisualActiveChange(true)
        }
        invalidate()
    }

    fun isSessionActive(): Boolean = activeAnchor != null && !wheelPinned

    fun isWheelPinned(): Boolean = wheelPinned && activeAnchor != null

    fun shouldPassthroughTap(): Boolean =
        activeAnchor != null && !wheelPinned && !menuActive && !activated

    fun isOverlayVisible(): Boolean = activeAnchor != null

    fun dismissPinnedWheel() = dismissWheel()

    fun beginSession(anchor: CornerAnchor, event: MotionEvent) {
        if (wheelPinned) dismissWheel()
        activeAnchor = anchor
        wheelPinned = false
        sessionMode = SessionMode.NORMAL
        menuActive = false
        activated = false
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 1
        menuActivationRadDist = 0f
        maxInwardSlop = 0f
        menuRevealProgress = 0f
        editModeEntered = false
        cancelSlotLongPress()
        revealAnimator?.cancel()
        startRawX = event.rawX
        startRawY = event.rawY
    }

    fun handleTouch(event: MotionEvent): Boolean {
        val anchor = activeAnchor ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val slopPx = cornerSettings.triggerSlopDp * density * landscapeSlopScale()
                val inward = CornerRadialMenuGeometry.inwardSlopDistance(
                    anchor = anchor,
                    startX = startRawX,
                    startY = startRawY,
                    currentX = event.rawX,
                    currentY = event.rawY,
                )
                maxInwardSlop = maxOf(maxInwardSlop, inward)
                if (!menuActive && maxInwardSlop >= slopPx) {
                    menuActive = true
                    activated = true
                    if (!isProgressiveReveal()) {
                        activeLayerCount = cornerSettings.enabledLayerCount
                    } else {
                        val (anchorX, anchorY) = anchorCenter(anchor)
                        menuActivationRadDist = hypot(event.rawX - anchorX, event.rawY - anchorY)
                    }
                    setMenuVisualActive(true)
                    HapticHelper.gestureStart(this, settings)
                    animateMenuReveal()
                }
                if (menuActive) {
                    updateInteraction(anchor, event.rawX, event.rawY, event.eventTime)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!menuActive) {
                    dismissWheel()
                    return activated
                }
                return handleRelease(anchor, event, fromPinned = false)
            }
        }
        return false
    }

    fun cancelSession() {
        revealAnimator?.cancel()
        clearSessionState()
    }

    private fun handleRelease(anchor: CornerAnchor, event: MotionEvent, fromPinned: Boolean): Boolean {
        val rawX = event.rawX
        val rawY = event.rawY
        val mode = sessionMode
        val (anchorX, anchorY) = anchorCenter(anchor)
        val onEditButton = CornerRadialMenuGeometry.isEditButtonHit(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            settings = cornerSettings,
            fingerX = rawX,
            fingerY = rawY,
            density = density,
        )
        val resolvedSlot = resolveSlotAt(anchor, rawX, rawY)
        val slot = when {
            shortcutSubMenuVisible && shortcutSubMenuSlot >= 0 && resolvedSlot < 0 -> shortcutSubMenuSlot
            else -> resolvedSlot
        }
        val innerRelease = isFingerInInnerZone(anchor, rawX, rawY)
        val outsideRelease = isFingerOutsideWheel(anchor, rawX, rawY)
        val innerAction = cornerSettings.innerZoneAction

        when {
            onEditButton -> {
                pinWheelForEdit(anchor)
                return true
            }
            mode == SessionMode.EDIT && slot >= 0 -> {
                onOpenSlotPicker(anchor, slot)
                return true
            }
            innerRelease && innerAction is GestureAction.CornerInnerPinWheel -> {
                pinWheel(anchor)
                return true
            }
            innerRelease && innerAction is GestureAction.CornerInnerCancel -> {
                dismissWheel()
                return activated || fromPinned
            }
            innerRelease && innerAction.isCornerInnerZoneOnly() -> {
                dismissWheel()
                return activated || fromPinned
            }
            innerRelease && innerAction.isEffective() -> {
                executeAction(innerAction, anchor, rawX, rawY)
                dismissWheel()
                return true
            }
            fromPinned && outsideRelease -> {
                dismissWheel()
                return true
            }
            fromPinned && innerRelease -> {
                dismissWheel()
                return true
            }
            mode == SessionMode.NORMAL && isFingerOnShortcutSubMenu(rawX, rawY) -> {
                resolveShortcutSubMenuRelease(anchor, rawX, rawY)?.let { shortcut ->
                    executeAction(shortcut, anchor, rawX, rawY, longPressArmed = false)
                }
                cancelSlotLongPress()
                hideShortcutSubMenu()
                dismissWheel()
                return activated || fromPinned
            }
            mode == SessionMode.NORMAL && slot >= 0 -> {
                val action = cornerSettings.slotsFor(anchor).getOrElse(slot) { GestureAction.None }
                if (action.isEffective()) {
                    val longPress = slotLongPressTriggered(event, slot, action)
                    if (longPress) {
                        HapticHelper.confirmLaunch(this, settings)
                    }
                    executeAction(action, anchor, rawX, rawY, longPressArmed = longPress)
                }
                cancelSlotLongPress()
                dismissWheel()
                return activated || fromPinned
            }
            fromPinned -> {
                dismissWheel()
                return true
            }
            outsideRelease && cornerSettings.cancelOutsideWheel -> {
                dismissWheel()
                return activated
            }
            else -> {
                dismissWheel()
                return activated
            }
        }
    }

    private fun pinWheel(anchor: CornerAnchor) {
        revealAnimator?.cancel()
        wheelPinned = true
        activeAnchor = anchor
        sessionMode = SessionMode.NORMAL
        menuActive = true
        setMenuVisualActive(true)
        activated = false
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = cornerSettings.enabledLayerCount
        menuRevealProgress = 1f
        editModeEntered = false
        maxInwardSlop = 0f
        cancelSlotLongPress()
        hideShortcutSubMenu()
        invalidate()
    }

    private fun pinWheelForEdit(anchor: CornerAnchor) {
        revealAnimator?.cancel()
        wheelPinned = true
        activeAnchor = anchor
        sessionMode = SessionMode.EDIT
        menuActive = true
        setMenuVisualActive(true)
        activated = true
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = cornerSettings.enabledLayerCount
        menuRevealProgress = 1f
        editModeEntered = true
        maxInwardSlop = 0f
        cancelSlotLongPress()
        hideShortcutSubMenu()
        invalidate()
    }

    private fun hideShortcutSubMenu() {
        subMenuRevealAnimator?.cancel()
        subMenuRevealAnimator = null
        shortcutSubMenuVisible = false
        shortcutSubMenuSlot = -1
        shortcutSubMenuLayout = null
        shortcutSubMenuRevealProgress = 0f
        highlightedShortcutIndex = -1
        lastHapticHighlightedShortcutIndex = -1
    }

    private fun showShortcutSubMenu(anchor: CornerAnchor, slot: Int) {
        shortcutSubMenuVisible = true
        shortcutSubMenuSlot = slot
        rebuildShortcutSubMenuLayout(anchor, slot)
        shortcutSubMenuRevealProgress = 0f
        subMenuRevealAnimator?.cancel()
        subMenuRevealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                shortcutSubMenuRevealProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun fingerSubMenuSlopPx(): Float = 10f * density

    private fun isFingerOnShortcutSubMenu(rawX: Float, rawY: Float): Boolean =
        shortcutSubMenuVisible &&
            shortcutSubMenuLayout?.containsFinger(rawX, rawY, fingerSubMenuSlopPx()) == true

    private fun resolveShortcutSubMenuRelease(
        anchor: CornerAnchor,
        rawX: Float,
        rawY: Float,
    ): GestureAction.LaunchShortcut? {
        if (!shortcutSubMenuVisible || shortcutSubMenuSlot < 0) return null
        val layout = shortcutSubMenuLayout ?: return null
        val index = layout.indexAt(rawX, rawY, fingerSubMenuSlopPx())
        if (index < 0) return null
        return cornerSettings.slotSubMenuFor(anchor, shortcutSubMenuSlot).items.getOrNull(index)
    }

    private fun resolveSlotAtFinger(
        anchor: CornerAnchor,
        rawX: Float,
        rawY: Float,
        editMode: Boolean,
        slots: List<GestureAction>,
    ): Int? = CornerRadialMenuGeometry.slotIndexAt(
        anchor = anchor,
        anchorX = anchorCenter(anchor).first,
        anchorY = anchorCenter(anchor).second,
        fingerX = rawX,
        fingerY = rawY,
        settings = cornerSettings,
        density = density,
        slots = slots,
        editMode = editMode,
        activeLayerCount = menuActiveLayerCount(),
        revealProgress = menuRevealProgress,
    )

    private fun dismissWheel() {
        revealAnimator?.cancel()
        clearSessionState()
        onSessionEnd()
    }

    private fun clearSessionState() {
        activeAnchor = null
        wheelPinned = false
        sessionMode = SessionMode.NORMAL
        menuActive = false
        setMenuVisualActive(false)
        activated = false
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 1
        menuActivationRadDist = 0f
        menuRevealProgress = 0f
        editModeEntered = false
        cancelSlotLongPress()
        hideShortcutSubMenu()
        invalidate()
    }

    private fun setMenuVisualActive(active: Boolean) {
        if (menuVisualActive == active) return
        menuVisualActive = active
        onMenuVisualActiveChange(active)
    }

    private fun animateMenuReveal() {
        revealAnimator?.cancel()
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                menuRevealProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lastHapticHighlightedSlot = -1
                }
            })
            start()
        }
    }

    private fun isMenuRevealing(): Boolean = menuRevealProgress < 1f

    private fun isProgressiveReveal(): Boolean =
        cornerSettings.progressiveLayers &&
            sessionMode == SessionMode.NORMAL &&
            !wheelPinned

    private fun menuActiveLayerCount(): Int = when {
        wheelPinned || sessionMode == SessionMode.EDIT -> cornerSettings.enabledLayerCount
        !isProgressiveReveal() -> cornerSettings.enabledLayerCount
        else -> activeLayerCount
    }

    private fun updateActiveLayerCount(anchor: CornerAnchor, anchorX: Float, anchorY: Float, rawX: Float, rawY: Float) {
        if (!isProgressiveReveal()) {
            activeLayerCount = cornerSettings.enabledLayerCount
            return
        }
        if (isFingerInInnerZone(anchor, rawX, rawY)) {
            activeLayerCount = 1
            return
        }
        activeLayerCount = CornerWheelLayout.activeLayerCount(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = rawX,
            fingerY = rawY,
            settings = cornerSettings,
            density = density,
            progressive = true,
            activationRadDist = menuActivationRadDist,
        )
    }

    private fun updateInteraction(anchor: CornerAnchor, rawX: Float, rawY: Float, eventTime: Long) {
        val (anchorX, anchorY) = anchorCenter(anchor)
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val editMode = sessionMode == SessionMode.EDIT
        val slots = cornerSettings.slotsFor(anchor)

        updateActiveLayerCount(anchor, anchorX, anchorY, rawX, rawY)

        val onEditButton = CornerRadialMenuGeometry.isEditButtonHit(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            settings = cornerSettings,
            fingerX = rawX,
            fingerY = rawY,
            density = density,
        )
        highlightedEditButton = onEditButton
        if (onEditButton) {
            highlightedSlot = -1
            lastHapticHighlightedSlot = -1
            highlightedShortcutIndex = -1
            lastHapticHighlightedShortcutIndex = -1
            hideShortcutSubMenu()
            syncSlotPressTracking(-1, eventTime)
            if (!editModeEntered) {
                sessionMode = SessionMode.EDIT
                editModeEntered = true
                activeLayerCount = cornerSettings.enabledLayerCount
                HapticHelper.gestureStart(this, settings)
            }
            return
        }

        if (shortcutSubMenuVisible && shortcutSubMenuSlot >= 0) {
            val layout = shortcutSubMenuLayout
            val slop = fingerSubMenuSlopPx()
            val slotAtFinger = resolveSlotAtFinger(anchor, rawX, rawY, editMode, slots)
            val onSubMenu = layout?.containsFinger(rawX, rawY, slop) == true
            val onSlot = slotAtFinger == shortcutSubMenuSlot
            if (onSubMenu || onSlot) {
                highlightedSlot = shortcutSubMenuSlot
                highlightedShortcutIndex = if (onSubMenu) {
                    layout.indexAt(rawX, rawY, slop)
                } else {
                    -1
                }
                if (highlightedShortcutIndex >= 0) {
                    maybeHapticForShortcutChange(highlightedShortcutIndex)
                }
                syncSlotPressTracking(shortcutSubMenuSlot, eventTime)
                return
            }
            hideShortcutSubMenu()
        }

        val slot = resolveSlotAtFinger(
            anchor = anchor,
            rawX = rawX,
            rawY = rawY,
            editMode = editMode,
            slots = slots,
        )
        if (slot != null) {
            if (shortcutSubMenuVisible && slot != shortcutSubMenuSlot) {
                hideShortcutSubMenu()
            }
            highlightedSlot = slot
            if (slot != hintRingSlot) {
                hintRingSlot = slot
                hintRingEnteredAt = SystemClock.uptimeMillis()
            }
            highlightedShortcutIndex = -1
            lastHapticHighlightedShortcutIndex = -1
            maybeHapticForSlotChange(slot)
            syncSlotPressTracking(slot, eventTime)
            return
        }

        highlightedSlot = if (shortcutSubMenuVisible && shortcutSubMenuSlot >= 0) {
            shortcutSubMenuSlot
        } else {
            -1
        }
        lastHapticHighlightedSlot = -1
        highlightedShortcutIndex = -1
        lastHapticHighlightedShortcutIndex = -1
        if (!shortcutSubMenuVisible) {
            syncSlotPressTracking(-1, eventTime)
        }
        val inSubMenu = shortcutSubMenuVisible &&
            shortcutSubMenuLayout?.containsFinger(rawX, rawY, fingerSubMenuSlopPx()) == true
        if (cornerSettings.cancelOutsideWheel &&
            !editMode &&
            !inSubMenu &&
            CornerWheelLayout.isOutsideWheel(
                anchor = anchor,
                anchorX = anchorX,
                anchorY = anchorY,
                fingerX = rawX,
                fingerY = rawY,
                screenWidth = screenW,
                screenHeight = screenH,
                settings = cornerSettings,
                density = density,
            )
        ) {
            highlightedEditButton = false
            return
        }

        if (isFingerInInnerZone(anchor, rawX, rawY)) {
            return
        }
    }

    private fun releaseLayerCount(): Int {
        var count = menuActiveLayerCount()
        if (isProgressiveReveal() && highlightedSlot >= 0) {
            count = maxOf(count, CornerRadialMenuCodec.layerOf(highlightedSlot) + 1)
        }
        return count
    }

    private fun maybeHapticForSlotChange(slot: Int) {
        if (isMenuRevealing()) return
        if (slot == lastHapticHighlightedSlot) return
        lastHapticHighlightedSlot = slot
        if (slot < 0) return
        HapticHelper.appTick(this, settings)
    }

    private fun maybeHapticForShortcutChange(index: Int) {
        if (isMenuRevealing()) return
        if (index == lastHapticHighlightedShortcutIndex) return
        lastHapticHighlightedShortcutIndex = index
        if (index < 0) return
        HapticHelper.appTick(this, settings)
    }

    private fun rebuildShortcutSubMenuLayout(anchor: CornerAnchor, slot: Int) {
        val items = cornerSettings.slotSubMenuFor(anchor, slot).items
        if (items.isEmpty()) {
            shortcutSubMenuLayout = null
            return
        }
        val (anchorX, anchorY) = anchorCenter(anchor)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * density }
        val textWidths = items.map { shortcut ->
            val label = (com.slideindex.app.overlay.panels.PanelRuntimeHooks.launchShortcutLabel?.invoke(shortcut).orEmpty()).ifBlank {
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.gestureActionLabel?.invoke(context, shortcut).orEmpty()
            }
            textPaint.measureText(label)
        }
        shortcutSubMenuLayout = CornerShortcutSubMenuLayoutCalculator.build(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            slotIndex = slot,
            settings = cornerSettings,
            density = density,
            revealProgress = menuRevealProgress,
            items = items,
            textWidthsPx = textWidths,
            screenWidth = width.toFloat(),
        )
    }

    private fun resolveSlotAt(anchor: CornerAnchor, rawX: Float, rawY: Float): Int {
        val (anchorX, anchorY) = anchorCenter(anchor)
        return CornerRadialMenuGeometry.slotIndexAt(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = rawX,
            fingerY = rawY,
            settings = cornerSettings,
            density = density,
            slots = cornerSettings.slotsFor(anchor),
            editMode = sessionMode == SessionMode.EDIT,
            activeLayerCount = releaseLayerCount(),
            revealProgress = menuRevealProgress,
        ) ?: -1
    }

    private fun isFingerInInnerZone(anchor: CornerAnchor, rawX: Float, rawY: Float): Boolean {
        val (anchorX, anchorY) = anchorCenter(anchor)
        return CornerWheelLayout.isInInnerZone(anchorX, anchorY, rawX, rawY, cornerSettings, density)
    }

    private fun isFingerOutsideWheel(anchor: CornerAnchor, rawX: Float, rawY: Float): Boolean {
        if (shortcutSubMenuVisible &&
            shortcutSubMenuLayout?.containsFinger(rawX, rawY, fingerSubMenuSlopPx()) == true
        ) {
            return false
        }
        val (anchorX, anchorY) = anchorCenter(anchor)
        return CornerWheelLayout.isOutsideWheel(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = rawX,
            fingerY = rawY,
            screenWidth = width.toFloat(),
            screenHeight = height.toFloat(),
            settings = cornerSettings,
            density = density,
        )
    }

    private fun executeAction(
        action: GestureAction,
        anchor: CornerAnchor,
        rawX: Float,
        rawY: Float,
        longPressArmed: Boolean = false,
    ) {
        gestureRunner.run(action, settings, longPressArmed, rawX, rawY, anchor.toPanelSide())
    }

    private fun syncSlotPressTracking(slot: Int, eventTime: Long) {
        if (isMenuRevealing()) {
            if (slot < 0) {
                cancelSlotLongPress()
                slotPressIndex = -1
                slotPressDownTime = 0L
            }
            return
        }
        if (slot >= 0) {
            if (slot != slotPressIndex) {
                slotPressIndex = slot
                slotPressDownTime = eventTime
                scheduleSlotLongPress(slot)
            }
        } else {
            cancelSlotLongPress()
            if (!shortcutSubMenuVisible) {
                slotPressIndex = -1
                slotPressDownTime = 0L
            }
        }
    }

    private fun scheduleSlotLongPress(slot: Int) {
        cancelSlotLongPress()
        if (isMenuRevealing()) return
        if (sessionMode == SessionMode.EDIT) return
        val anchor = activeAnchor ?: return
        val subMenu = cornerSettings.slotSubMenuFor(anchor, slot)
        val hasSubMenu = subMenu.isActive()
        val action = cornerSettings.slotsFor(anchor).getOrElse(slot) { GestureAction.None }
        val launchPolicyTrack = settings.launchPolicyLongPressEligible() && action.usesLaunchPolicy()
        if (!hasSubMenu && !launchPolicyTrack) return
        slotLongPressTrackingIndex = slot
        val runnable = Runnable {
            val tracking = slotLongPressTrackingIndex
            if (tracking < 0) return@Runnable
            if (hasSubMenu) {
                showShortcutSubMenu(anchor, tracking)
            }
            if (launchPolicyTrack) {
                slotLongPressArmed = true
            }
            HapticHelper.longThreshold(this, settings)
            invalidate()
        }
        slotLongPressRunnable = runnable
        postDelayed(runnable, settings.effectiveLongPressDurationMs().toLong())
    }

    private fun cancelSlotLongPress() {
        slotLongPressRunnable?.let { removeCallbacks(it) }
        slotLongPressRunnable = null
        slotLongPressTrackingIndex = -1
        slotLongPressArmed = false
    }

    private fun slotLongPressTriggered(event: MotionEvent, slot: Int, action: GestureAction): Boolean {
        if (slotLongPressArmed) return true
        if (!settings.launchPolicyLongPressEligible() || !action.usesLaunchPolicy()) return false
        if (slotPressIndex < 0 || slotPressIndex != slot) return false
        return event.eventTime - slotPressDownTime >= settings.effectiveLongPressDurationMs()
    }

    private fun GestureAction.usesLaunchPolicy(): Boolean = when (this) {
        is GestureAction.LaunchApp -> windowMode.followsGlobalPolicy
        is GestureAction.LaunchShortcut -> true
        else -> false
    }

    private fun anchorCenter(anchor: CornerAnchor): Pair<Float, Float> = when (anchor) {
        CornerAnchor.LEFT -> 0f to height.toFloat()
        CornerAnchor.RIGHT -> width.toFloat() to height.toFloat()
    }

    fun handlePinnedTouchEvent(event: MotionEvent): Boolean = handlePinnedTouch(event)

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = when {
            wheelPinned -> handlePinnedTouch(event)
            !isSessionActive() -> false
            else -> handleTouch(event)
        }
        if (handled && event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return handled
    }

    private fun handlePinnedTouch(event: MotionEvent): Boolean {
        val anchor = activeAnchor ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateInteraction(anchor, event.rawX, event.rawY, event.eventTime)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateInteraction(anchor, event.rawX, event.rawY, event.eventTime)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return handleRelease(anchor, event, fromPinned = true)
            }
        }
        return false
    }

    private fun landscapeSlopScale(): Float {
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (landscape && cornerSettings.landscapePreventFalseTouch) 1.35f else 1f
    }

    private fun drawBackgroundMask(canvas: Canvas, progress: Float) {
        if (cornerSettings.backgroundStyle == CornerGestureSettings.BACKGROUND_NONE) return
        if (progress <= 0.01f) return
        val dim = cornerSettings.dimPercent.coerceIn(
            CornerGestureSettings.MIN_DIM_PERCENT,
            CornerGestureSettings.MAX_DIM_PERCENT,
        )
        val alpha = (255f * dim / 100f * progress).toInt().coerceIn(0, 255)
        if (alpha <= 0) return
        dimPaint.alpha = alpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!menuActive) return
        val anchor = activeAnchor ?: return
        val (anchorX, anchorY) = anchorCenter(anchor)
        val hintRing = hintRingProgress()
        val (modeSuffix, modePip) = hintLongPressMode()
        // 按住期间逐帧刷新（进度线按"按住时长"实时推进）
        if (hintRing in 0f..0.999f) postInvalidateOnAnimation()
        drawBackgroundMask(canvas, menuRevealProgress)
        val subMenuItems = if (shortcutSubMenuVisible && shortcutSubMenuSlot >= 0) {
            cornerSettings.slotSubMenuFor(anchor, shortcutSubMenuSlot).items
        } else {
            emptyList()
        }
        CornerRadialMenuRenderer.draw(
            context = context,
            canvas = canvas,
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            settings = cornerSettings,
            slots = cornerSettings.slotsFor(anchor),
            highlightedSlot = highlightedSlot,
            highlightedEditButton = highlightedEditButton,
            editMode = sessionMode == SessionMode.EDIT,
            activeLayerCount = CornerRadialMenuGeometry.displayLayerCount(
                activeLayerCount = menuActiveLayerCount(),
                highlightedSlot = highlightedSlot,
                maxLayerCount = cornerSettings.enabledLayerCount,
            ),
            density = density,
            revealProgress = menuRevealProgress,
            hintIconSizeDp = cornerSettings.selectedHintIconSizeDp,
            activityShortcuts = settings.activityShortcuts,
            shellCommands = settings.shellCommands,
            appRepository = appRepository,
            shortcutSubMenuItems = subMenuItems,
            shortcutSubMenuLayout = if (shortcutSubMenuVisible) shortcutSubMenuLayout else null,
            highlightedShortcutIndex = highlightedShortcutIndex,
            shortcutSubMenuRevealProgress = shortcutSubMenuRevealProgress,
            longPressRingProgress = hintRing,
            hintLabelSuffix = modeSuffix,
            hintPipArmed = modePip,
        )
    }

    /**
     * 长按到时间后名字后缀与"小窗"角标：目标形态由启动策略决定。
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

    /**
     * 提示卡片里图标外圈的进度（0..1）；**-1 = 不画**。
     *
     * 就是"按住这个槽位多久了"的长按倒计时：按下开始走、走满停住、抬手消失。
     * 与快捷轮盘提示条里那条线同源同时长（`effectiveLongPressDurationMs()`）。
     */
    private fun hintRingProgress(): Float {
        if (sessionMode == SessionMode.EDIT) return -1f
        // 只在小窗功能开着、且启动策略是「长按触发」那一档时才画：
        // 策略为"始终全屏/始终小窗"时，长按根本不会改变启动形态，这条线没有意义。
        if (!settings.launchPolicyLongPressEligible()) return -1f
        val slot = hintRingSlot
        if (slot < 0 || slot != highlightedSlot) return -1f
        val anchor = activeAnchor ?: return -1f
        val action = cornerSettings.slotsFor(anchor).getOrElse(slot) { GestureAction.None }
        if (action is GestureAction.None) return -1f
        // 只给"长按会改变启动方式"的槽位（App 跟随全局策略 / 快捷方式）——
        // 内置动作、Shell 命令这类槽位长按没有"小窗/全屏"语义，不画。
        if (!action.usesLaunchPolicy()) return -1f
        val elapsed = SystemClock.uptimeMillis() - hintRingEnteredAt
        if (elapsed < 0) return -1f
        val duration = settings.effectiveLongPressDurationMs().coerceAtLeast(1)
        return (elapsed.toFloat() / duration).coerceIn(0f, 1f)
    }
}
