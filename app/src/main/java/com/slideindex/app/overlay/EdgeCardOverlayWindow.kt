package com.slideindex.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.hardware.display.DisplayManager
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.MessagePlacementFractions
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.NotificationContentGate
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.SideBubbleHorizontalEdge
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 贴边通知卡：贴在屏幕侧缘的一组卡片，每个会话一张。
 *
 * 纯 View 实现（不使用 Compose），与浮岛（悬浮菜单）侧边样式同一套思路：
 * - **窗口宽度固定为展开宽度**，收起/展开只平移 [WindowManager.LayoutParams.x]
 * - 收起 = 窗口大部分移出屏幕，只露头像那 48dp
 * - 动画只改 x 和整体 alpha，从不改窗口尺寸，因此没有 measure/layout 抖动
 * - 多张卡按最新在前纵向堆叠
 */
object EdgeCardOverlayWindow {
    private const val TAG = "EdgeCardOverlay"
    private const val PREVIEW_KEY = "__edge_card_preview__"

    private const val CARD_HEIGHT_DP = 42f
    /** 每多一行正文，卡片高度增加的高度（与悬浮菜单侧边样式一致）。 */
    private const val LINE_HEIGHT_DP = 16f
    private const val MAX_LINES = 3
    private const val EXPANDED_WIDTH_DP = 126f
    private const val WIDE_WIDTH_DP = 184f
    private const val VISIBLE_COLLAPSED_DP = 42f
    private const val ICON_DP = 40f
    private const val BADGE_DP = 13f
    private const val CARD_GAP_DP = 8f
    private const val TEXT_GAP_DP = 8f

    private const val HORIZONTAL_SWIPE_DP = 24f
    private const val VERTICAL_DISMISS_DP = 48f
    private const val ANIM_MS = 240L
    private const val USER_INTERACTION_GRACE_MS = 1_500L

    /** 同一会话的连续通知合并窗口，参考悬浮菜单的 200ms 防抖。 */
    private const val SHOW_DEBOUNCE_MS = 200L

    /** 与悬浮菜单侧边样式一致：stiffness = 300、damping = 0.75（欠阻尼，收尾有轻微回弹）。 */
    internal const val SPRING_STIFFNESS = 300f
    internal const val SPRING_DAMPING = 0.75f

    /** 直接驱动窗口 x 的弹簧属性。 */
    private val cardXProperty = object : FloatPropertyCompat<EdgeCardEntry>("edgeCardX") {
        override fun getValue(entry: EdgeCardEntry): Float =
            (entry.params?.x ?: 0).toFloat()

        override fun setValue(entry: EdgeCardEntry, value: Float) {
            entry.params?.x = value.roundToInt()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val entries = mutableListOf<EdgeCardEntry>()
    private val pendingShows = mutableMapOf<String, Runnable>()

    private var windowManager: WindowManager? = null
    private var appContext: Context? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    val isShowing: Boolean get() = entries.isNotEmpty()

    fun containsNotification(data: NotificationData): Boolean = entries.any { it.matches(data) }

    fun show(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, plan, onAction, onDismiss) }
            return
        }
        // 会话级 200ms 防抖：TG 一次刷新会连发多条同一会话的通知，合并成最后一次处理。
        val key = plan.data.key
        pendingShows.remove(key)?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingShows.remove(key)
            showNow(context, plan, onAction, onDismiss)
        }
        pendingShows[key] = runnable
        mainHandler.postDelayed(runnable, SHOW_DEBOUNCE_MS)
    }

    private fun showNow(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val hostContext = MessageOverlayHost.resolveHostContext(context) ?: run {
            Log.w(TAG, "overlay host unavailable")
            return
        }
        if (appContext == null || windowManager == null) {
            appContext = hostContext
            windowManager = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        }
        // 每次 show 都确保监听已挂上（幂等）：窗口在更早的 show 里创建过时，上面的 if 不进，
        // 之前就没有任何回调在旋转后重算 x，表现为卡片停在旧坐标或被推出屏幕。
        registerDisplayListener(hostContext)
        entries.filter { it.preview }.forEach { removeEntry(it, notifyDismiss = false) }

        val existing = entries.firstOrNull { it.matches(plan.data) && !it.preview }
        if (existing != null) {
            // 窗口可能在上一次锁屏挂起 / 建窗失败后变成空壳：这里必须重建，否则该会话后续
            // 每条消息都只更新内容、永远不再建窗（表现为「日志一直打 autoCollapse，但屏幕没卡」）。
            val rebuilt = if (existing.view == null || existing.params == null) {
                if (!ensureWindow(existing, hostContext)) {
                    Log.w(TAG, "rebuild window failed, dropping entry key=${existing.key}")
                    removeEntry(existing, notifyDismiss = false)
                    return
                }
                relayout()
                true
            } else {
                false
            }
            val changed = NotificationContentGate.signature(existing.plan.data) != NotificationContentGate.signature(plan.data)
            // 角标按“本会话收到的条数”累计，不再取通知的 number：微信/QQ 把 number 设成
            // 整个 App 的未读总数，混进来会显示成 99+ 并且永远减不下来。
            val unread = if (changed) {
                existing.state.unreadCount + 1
            } else {
                existing.state.unreadCount
            }
            existing.plan = plan
            existing.actionCallback = onAction
            existing.dismissCallback = onDismiss
            existing.state = if (changed) {
                existing.state.onNewMessage(unread, autoExpand = !existing.interacting)
            } else {
                existing.state.withUnread(unread)
            }
            if (changed) moveToTop(existing)
            // 同会话来新消息：窗口直接瞬移到展开位，再播一次“从屏外滑入”的入场动画。
            applyEntry(existing, animated = false)
            if (changed || rebuilt) {
                existing.view?.playEntrance(1f)
                scheduleAutoCollapse(existing)
            }
            return
        }

        val entry = EdgeCardEntry(key = plan.data.key)
        entry.plan = plan
        entry.state = EdgeCardUiState(
            phase = EdgeCardPhase.Expanded,
            // 新卡从 1 开始，之后每来一条同会话消息 +1（对齐悬浮菜单的做法）。
            unreadCount = 1,
        )
        entry.actionCallback = onAction
        entry.dismissCallback = onDismiss
        entries.add(0, entry)
        trimToCount(plan.settings)
        if (!ensureWindow(entry, hostContext)) {
            Log.w(TAG, "create window failed, dropping entry key=${entry.key}")
            removeEntry(entry, notifyDismiss = false)
            return
        }
        relayout()
        // 先挂计时器再展开：展开会启动滚动，滚动启动时会把计时器取消掉（内容溢出场景）。
        scheduleAutoCollapse(entry)
        applyEntry(entry, animated = false)
        entry.view?.playEntrance(1f)
    }

    /** 设置页预览：示例数据常驻，不自动收起。 */
    fun installPreview(context: Context, settings: MessageSettings) {
        Log.d(TAG, "installPreview enter main=${Looper.myLooper() == Looper.getMainLooper()}")
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { installPreview(context, settings) }
            return
        }
        // 必须先清场：dismissAllInternal 会把 windowManager 置空并注销 display 监听，
        // 之后再取 host / 重新注册，否则 ensureWindow 里 wm == null 会静默 return，预览永远不出现。
        dismissAllInternal(notifyDismiss = false)
        val hostContext = MessageOverlayHost.resolveHostContext(context)
        Log.d(TAG, "installPreview host=${hostContext != null} wm=${windowManager != null}")
        if (hostContext == null) return
        appContext = hostContext
        windowManager = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        registerDisplayListener(hostContext)

        val appCtx = context.applicationContext
        val appLabel = runCatching {
            appCtx.packageManager.getApplicationLabel(
                appCtx.packageManager.getApplicationInfo(appCtx.packageName, 0),
            ).toString()
        }.getOrDefault(appCtx.packageName)
        val previewData = NotificationData(
            packageName = appCtx.packageName,
            key = PREVIEW_KEY,
            title = appLabel,
            content = context.getString(com.slideindex.app.R.string.message_preview_content),
            largeIcon = null,
            appIcon = null,
            contentIntent = null,
            postTime = -1L,
            badgeCount = 3,
        )
        val previewSettings = settings.copy(edgeCardAutoCollapseMs = 0)
        val previewPlan = MessageDisplayPlan(
            data = previewData,
            showFloatIcon = false,
            showSideBubble = false,
            showDanmaku = false,
            showCNotice = false,
            sideTheme = null,
            danmakuTheme = null,
            settings = previewSettings,
            showEdgeCard = true,
        )
        val entry = EdgeCardEntry(key = PREVIEW_KEY, preview = true)
        entry.plan = previewPlan
        entry.state = EdgeCardUiState(phase = EdgeCardPhase.Expanded, unreadCount = 3)
        entry.actionCallback = {}
        entry.dismissCallback = {}
        entries.add(entry)
        if (!ensureWindow(entry, hostContext)) {
            entries.remove(entry)
            return
        }
        Log.d(
            TAG,
            "installPreview done entries=${entries.size} view=${entry.view != null} " +
                "params=${entry.params != null}",
        )
        relayout()
        applyEntry(entry, animated = false)
    }

    fun updatePreviewPlan(plan: MessageDisplayPlan) {
        entries.firstOrNull { it.preview }?.let {
            it.plan = plan
            bindView(it)
        }
    }

    fun dismissPreview() {
        entries.filter { it.preview }.forEach { removeEntry(it, notifyDismiss = false) }
    }

    /** 灭屏/锁屏：按「锁屏后保留」设置决定清掉还是挂起，解锁后补显。 */
    fun handleLockScreenBoundary() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { handleLockScreenBoundary() }
            return
        }
        if (entries.isEmpty()) {
            Log.d(TAG, "lock boundary: no entries")
            return
        }
        val keep = entries.filter { !it.preview }.all { it.plan.settings.edgeCardKeepAcrossLock }
        Log.d(TAG, "lock boundary: entries=${entries.size} keep=$keep")
        if (!keep) {
            Log.d(TAG, "lock boundary: dismiss (keep disabled)")
            dismiss()
            return
        }
        Log.d(TAG, "lock boundary: suspend ${entries.size} cards")
        suspendForLockScreen()
    }

    /** 只摘窗口，保留条目数据（消息、未读数、时间戳）。 */
    private fun suspendForLockScreen() {
        Log.d(TAG, "suspendForLockScreen: ${entries.size} cards")
        entries.forEach { entry ->
            cancelAutoCollapse(entry)
            entry.spring?.cancel()
            entry.spring = null
            entry.view?.let { view ->
                runCatching { windowManager?.removeView(view) }
                    .onFailure { Log.w(TAG, "removeView failed on lock", it) }
            }
            entry.view = null
            entry.params = null
        }
    }

    /** 解锁后按挂起的条目重建窗口；过期的直接丢弃。 */
    fun replayAfterUnlock(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { replayAfterUnlock(context) }
            return
        }
        if (entries.none { it.view == null }) {
            Log.d(TAG, "replay skipped: nothing suspended")
            return
        }
        val hostContext = MessageOverlayHost.resolveHostContext(context)
        if (hostContext == null) {
            Log.d(TAG, "replay skipped: host null")
            return
        }
        Log.d(TAG, "replayAfterUnlock: entries=${entries.size}")
        appContext = hostContext
        windowManager = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        registerDisplayListener(hostContext)
        // createdAt 用的是 uptimeMillis，这里必须同一基准；混用 currentTimeMillis
        // 会把所有条目算成“几十年前创建”，解锁时被当成过期全部删掉。
        val now = android.os.SystemClock.uptimeMillis()
        val expired = entries.filter { !it.preview && now - it.createdAt > LOCK_SUSPEND_TTL_MS }
        expired.forEach { cancelAutoCollapse(it) }
        Log.d(TAG, "replay expired=${expired.size} entries=${entries.size}")
        if (expired.isNotEmpty()) entries.removeAll(expired.toSet())
        if (entries.isEmpty()) {
            Log.d(TAG, "replay skipped: all expired")
            return
        }
        trimToCount(entries.first().plan.settings)
        entries.toList().forEach { entry ->
            if (entry.view == null) {
                if (ensureWindow(entry, hostContext)) {
                    applyEntry(entry, animated = false)
                } else {
                    // 重建失败：丢掉空壳，避免留下永远不显示的卡。
                    cancelAutoCollapse(entry)
                    entries.remove(entry)
                    Log.w(TAG, "replay drop entry key=${entry.key}")
                }
            }
        }
        relayout()
        Log.d(TAG, "replayAfterUnlock: ${entries.size} cards")
    }

    fun updateWindowPlacement(context: Context, settings: MessageSettings) {
        entries.forEach { it.plan = it.plan.copy(settings = settings) }
        val hostContext = MessageOverlayHost.resolveHostContext(context) ?: return
        appContext = hostContext
        windowManager = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        entries.forEach { entry ->
            val view = entry.view ?: return@forEach
            val params = entry.params ?: return@forEach
            applyHorizontalGravity(settings, params)
            view.exclusionEdge = settings.edgeCardHorizontalEdge
            applyEntry(entry, animated = false)
        }
        relayout()
    }

    fun dismissEntry(key: String, postTime: Long) {
        entries.filter { entry ->
            val data = entry.plan.data
            data.key == key && (postTime < 0 || data.postTime == postTime)
        }.forEach { removeEntry(it, notifyDismiss = false) }
    }

    fun dismissEntriesForKey(key: String) {
        entries.filter { it.plan.data.key == key }.forEach { removeEntry(it, notifyDismiss = false) }
    }

    fun dismissSameSource(sourceKey: String) {
        entries.filter { it.plan.data.conversationSourceKey == sourceKey }
            .forEach { removeEntry(it, notifyDismiss = false) }
    }

    fun dismiss() = dismissAllInternal(notifyDismiss = false)

    fun dismissImmediate() = dismiss()

    /** 正文行数（1..3）。 */
    internal fun lineCount(settings: MessageSettings): Int =
        settings.edgeCardMaxLines.coerceIn(1, MAX_LINES)

    /** 卡片高度随行数增长：42dp 起步，每多一行 +16dp。 */
    internal fun cardHeightDp(settings: MessageSettings): Float =
        CARD_HEIGHT_DP + (lineCount(settings) - 1) * LINE_HEIGHT_DP

    fun snapshotDisplayedKeys(): Set<String> =
        entries.map { "${it.plan.data.key}|${it.plan.data.postTime}" }.toSet()

    fun snapshotDisplayedKeysForSource(sourceKey: String): Set<String> =
        entries.filter { it.plan.data.conversationSourceKey == sourceKey }
            .map { "${it.plan.data.key}|${it.plan.data.postTime}" }
            .toSet()

    fun pauseAutoDismiss(key: String, postTime: Long) {
        entries.filter {
            val data = it.plan.data
            data.key == key && (postTime < 0 || data.postTime == postTime)
        }.forEach { cancelAutoCollapse(it) }
    }

    fun resumeAutoDismiss(key: String, postTime: Long) {
        entries.filter {
            val data = it.plan.data
            data.key == key && (postTime < 0 || data.postTime == postTime)
        }.forEach { scheduleAutoCollapse(it) }
    }

    // ---- 布局与动画 ----

    private fun density(): Float = appContext?.resources?.displayMetrics?.density ?: 1f

    private fun dp(value: Float): Int = (value * density()).roundToInt()

    private fun expandedWidthDp(state: EdgeCardUiState): Float =
        if (state.unreadCount > 1) WIDE_WIDTH_DP else EXPANDED_WIDTH_DP

    /** 当前屏幕宽度：优先用 display 的实时 metrics，旋转后 resources 里的值可能滞后一帧。 */
    private fun screenWidth(): Int {
        val ctx = appContext ?: return 0
        val display = (ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)
        val width = metrics.widthPixels
        return if (width > 0) width else ctx.resources.displayMetrics.widthPixels
    }

    /**
     * 窗口统一用 TOP|START，`params.x` 就是窗口左边缘的屏幕坐标（允许负值、可超出右侧）。
     * 这样左右贴边的位置计算只需一套绝对坐标，避免 END gravity 下 x 含义反转。
     */
    private fun expandedX(entry: EdgeCardEntry): Int {
        val width = entry.view?.width?.takeIf { it > 0 } ?: entry.windowWidthPx
        return when (entry.plan.settings.edgeCardHorizontalEdge) {
            SideBubbleHorizontalEdge.Right -> screenWidth() - width
            SideBubbleHorizontalEdge.Left -> 0
        }
    }

    /** 收起位置：窗口大部分移出屏幕，只露头像那一截。 */
    private fun collapsedX(entry: EdgeCardEntry): Int {
        val width = entry.view?.width?.takeIf { it > 0 } ?: entry.windowWidthPx
        return when (entry.plan.settings.edgeCardHorizontalEdge) {
            SideBubbleHorizontalEdge.Right -> screenWidth() - dp(VISIBLE_COLLAPSED_DP)
            SideBubbleHorizontalEdge.Left -> dp(VISIBLE_COLLAPSED_DP) - width
        }
    }

    /** 手指动过但没到阈值（或手势被系统取消）：只把窗口弹回当前形态的目标位，不重绑内容。 */
    private fun settleBack(entry: EdgeCardEntry) {
        val view = entry.view ?: return
        val params = entry.params ?: return
        val expanded = entry.state.isExpanded
        val targetX = if (expanded) expandedX(entry) else collapsedX(entry)
        val targetAlpha =
            if (expanded) 1f else entry.plan.settings.edgeCardOpacity.coerceIn(0f, 1f)
        animateTo(entry, view, params, targetX, targetAlpha, animated = true)
    }

    private fun applyEntry(entry: EdgeCardEntry, animated: Boolean) {
        val view = entry.view ?: return
        val params = entry.params ?: return
        bindView(entry)
        // 内容刚换，View 的宽度要等下一帧布局才更新；同帧算 x 会用到旧宽度，
        // 表现为卡片右端离屏幕边缘差一截（内容变短时）或被推出屏幕（变长时）。
        view.post {
            if (entry.view !== view) return@post
            val expanded = entry.state.isExpanded
            val targetX = if (expanded) expandedX(entry) else collapsedX(entry)
            val targetAlpha = if (expanded) 1f else entry.plan.settings.edgeCardOpacity.coerceIn(0f, 1f)
            animateTo(entry, view, params, targetX, targetAlpha, animated)
        }
    }

    /** 旋转等屏幕变化后，x/y 都要按新的屏幕尺寸重算，否则卡片会停在旧坐标上。 */
    private fun registerDisplayListener(context: Context) {
        if (displayListener != null) return
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    mainHandler.post { onDisplayChanged() }
                    return
                }
                onDisplayChanged()
            }

            private fun onDisplayChanged() {
                if (entries.isEmpty()) return
                Log.d(TAG, "display changed: screenW=${screenWidth()} entries=${entries.size}")
                relayout()
                entries.toList().forEach { entry -> applyEntry(entry, animated = false) }
            }
        }
        manager.registerDisplayListener(listener, mainHandler)
        displayListener = listener
    }

    private fun unregisterDisplayListener() {
        val listener = displayListener ?: return
        runCatching {
            (appContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.unregisterDisplayListener(listener)
        }
        displayListener = null
    }

    private fun animateTo(
        entry: EdgeCardEntry,
        view: EdgeCardView,
        params: WindowManager.LayoutParams,
        targetX: Int,
        targetAlpha: Float,
        animated: Boolean,
    ) {
        entry.spring?.cancel()
        entry.spring = null
        val startAlpha = view.alpha
        if (!animated || (params.x == targetX && abs(startAlpha - targetAlpha) < 0.01f)) {
            params.x = targetX
            view.alpha = targetAlpha
            view.marqueeActive = entry.state.isExpanded
            if (view.isAttachedToWindow) {
                runCatching { windowManager?.updateViewLayout(view, params) }
            }
            return
        }
        view.marqueeActive = false
        val from = collapsedX(entry)
        val to = expandedX(entry)
        val span = (to - from).toFloat()
        val force = SpringForce(targetX.toFloat()).apply {
            stiffness = SPRING_STIFFNESS
            dampingRatio = SPRING_DAMPING
        }
        val spring = SpringAnimation(entry, cardXProperty).apply {
            // 注意：SpringAnimation(object, property) 这个构造器不会初始化 spring force，
            // 必须显式 setSpring，否则 spring 为 null，设置 stiffness 时直接 NPE。
            setSpring(force)
            setMinimumVisibleChange(1f)
            addUpdateListener { _, _, _ ->
                // alpha 跟 x 的进度走，和浮岛侧边样式一致，不单独做一条动画曲线。
                val params = entry.params ?: return@addUpdateListener
                val x = params.x.toFloat()
                val p = if (span == 0f) 1f else ((x - from) / span).coerceIn(0f, 1f)
                view.alpha = entry.plan.settings.edgeCardOpacity.coerceIn(0f, 1f) * (1f - p) + p
                if (view.isAttachedToWindow) {
                    runCatching { windowManager?.updateViewLayout(view, params) }
                }
            }
            addEndListener { _, _, _, _ ->
                entry.spring = null
                view.marqueeActive = entry.state.isExpanded
            }
        }
        entry.spring = spring
        spring.animateToFinalPosition(targetX.toFloat())
        view.alpha = startAlpha
    }

    private fun bindView(entry: EdgeCardEntry) {
        entry.view?.bind(entry.plan, entry.state)
    }

    private fun applyHorizontalGravity(
        @Suppress("UNUSED_PARAMETER") settings: MessageSettings,
        params: WindowManager.LayoutParams,
    ) {
        // 位置全部由 x 的绝对坐标决定，gravity 只固定到左上，左右贴边在 x 里算。
        params.gravity = Gravity.TOP or Gravity.START
    }

    /** 建窗口。返回 false 表示这次没挂上，调用方必须丢弃该 entry，避免留下永远不显示的僵尸卡。 */
    private fun ensureWindow(entry: EdgeCardEntry, hostContext: Context): Boolean {
        val settings = entry.plan.settings
        val view = EdgeCardView(hostContext).apply {
            exclusionEdge = settings.edgeCardHorizontalEdge
            setOnTouchListener(EdgeCardTouchListener(entry))
            // 滚动期间暂停自动收起；滚到最后一行后收起（内容看完才走）。
            onScrollStarted = {
                if (entry.plan.settings.edgeCardCollapseWhenScrollDone) cancelAutoCollapse(entry)
            }
            onScrollFinished = {
                Log.d(
                    TAG,
                    "scroll finished: collapseWhenDone=" +
                        entry.plan.settings.edgeCardCollapseWhenScrollDone,
                )
                if (entry.plan.settings.edgeCardCollapseWhenScrollDone) collapseEntry(entry)
            }
        }
        // 先绑内容再测量：宽高都由内容决定（正文行数多则卡片更高）。
        view.bind(entry.plan, entry.state)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(
                dp(120f) + dp(52f),
                View.MeasureSpec.AT_MOST,
            ),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val widthPx = view.measuredWidth.coerceAtLeast(dp(1f))
        val measuredHeight = view.measuredHeight.coerceAtLeast(dp(CARD_HEIGHT_DP))
        view.layout(0, 0, widthPx, measuredHeight)

        entry.view = view
        entry.windowWidthPx = widthPx
        entry.heightPx = measuredHeight
        entry.onHeightChanged = { newHeight ->
            if (entry.heightPx != newHeight) {
                entry.heightPx = newHeight
                relayout()
            }
        }
        entry.onWidthChanged = { newWidth ->
            entry.windowWidthPx = newWidth
            val params = entry.params
            if (params != null) {
                val target = if (entry.state.isExpanded) expandedX(entry) else collapsedX(entry)
                val running = entry.spring
                if (running != null) {
                    running.animateToFinalPosition(target.toFloat())
                } else {
                    params.x = target
                    if (view.isAttachedToWindow) {
                        runCatching { windowManager?.updateViewLayout(view, params) }
                    }
                }
            }
        }
        entry.y = computeTops(settings, entries.size)
            .getOrElse(entries.indexOf(entry).coerceAtLeast(0)) { 0 }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(hostContext),WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            applyHorizontalGravity(settings, this)
            x = collapsedX(entry)
            y = entry.y
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        entry.params = params
        view.alpha = settings.edgeCardOpacity.coerceIn(0f, 1f)

        val wm = windowManager
        if (wm == null) {
            Log.d(TAG, "ensureWindow skipped: windowManager null key=${entry.key}")
            return false
        }
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed key=${entry.key}", it) }
            .isSuccess
        if (!added) {
            entry.view = null
            entry.params = null
            return false
        }
        Log.d(
            TAG,
            "window added key=${entry.key} preview=${entry.preview} " +
                "x=${params.x} y=${params.y} w=${entry.windowWidthPx} h=${measuredHeight} " +
                "screenW=${screenWidth()}",
        )
        view.post { relayout() }
        return true
    }

    private fun computeTops(settings: MessageSettings, count: Int): List<Int> {
        val ctx = appContext ?: return emptyList()
        if (count <= 0) return emptyList()
        val metrics = ctx.resources.displayMetrics
        val gapPx = dp(CARD_GAP_DP)
        // 每张卡行数可能不同，高度各自算。
        val heights = entries.map {
            it.heightPx.takeIf { h -> h > 0 } ?: dp(CARD_HEIGHT_DP)
        }
        val total = heights.sum() + gapPx * (count - 1).coerceAtLeast(0)
        val anchor = (MessagePlacementFractions.coerceY(settings.edgeCardYFraction) *
            metrics.heightPixels).roundToInt()
        val top = (anchor - total / 2).coerceIn(0, (metrics.heightPixels - total).coerceAtLeast(0))
        var cursor = top
        return heights.map { height ->
            val y = cursor
            cursor += height + gapPx
            y
        }
    }

    private fun relayout() {
        val wm = windowManager ?: return
        if (entries.isEmpty()) return
        val settings = entries.firstOrNull()?.plan?.settings ?: return
        val tops = computeTops(settings, entries.size)
        entries.forEachIndexed { index, entry ->
            entry.y = tops.getOrElse(index) { entry.y }
            val view = entry.view ?: return@forEachIndexed
            val params = entry.params ?: return@forEachIndexed
            if (!view.isAttachedToWindow) return@forEachIndexed
            params.y = entry.y
            runCatching { wm.updateViewLayout(view, params) }
                .onFailure { Log.w(TAG, "updateViewLayout failed", it) }
        }
    }

    private fun moveToTop(entry: EdgeCardEntry) {
        if (entries.remove(entry)) entries.add(0, entry)
        // 顺序变了，堆叠的 y 要重排，否则新卡会压在旧卡的坐标上。
        relayout()
    }

    private fun trimToCount(settings: MessageSettings) {
        val max = settings.edgeCardMaxCount.coerceIn(1, 5)
        while (entries.count { !it.preview } > max) {
            val newest = entries.firstOrNull { !it.preview } ?: break
            val group = newest.plan.data.packageName
            val pool = entries.filter { !it.preview && it !== newest }
            // 同分组优先淘汰最旧的：避免两个群互挤，导致还有未读的会话被顶掉。
            val victim = pool.filter { it.plan.data.packageName == group }
                .minByOrNull { it.createdAt }
                ?: pool.minByOrNull { it.createdAt }
                ?: break
            removeEntry(victim, notifyDismiss = false)
        }
    }

    private fun removeEntry(entry: EdgeCardEntry, notifyDismiss: Boolean) {
        cancelAutoCollapse(entry)
        entry.spring?.cancel()
        entry.spring = null
        entry.view?.cancelEntrance()
        entries.remove(entry)
        val view = entry.view
        val callback = entry.dismissCallback
        entry.view = null
        entry.params = null
        entry.dismissCallback = null
        entry.actionCallback = null
        if (view != null) {
            runCatching { windowManager?.removeView(view) }
                .onFailure { Log.w(TAG, "removeView failed", it) }
        }
        if (notifyDismiss) callback?.invoke()
        relayout()
    }

    private fun dismissAllInternal(notifyDismiss: Boolean) {
        unregisterDisplayListener()
        pendingShows.values.forEach { mainHandler.removeCallbacks(it) }
        pendingShows.clear()
        entries.toList().forEach { removeEntry(it, notifyDismiss) }
        appContext = null
        windowManager = null
    }

    // ---- 手势 ----

    private fun onGesture(entry: EdgeCardEntry, totalX: Float, totalY: Float, swipe: EdgeCardSwipe) {
        val settings = entry.plan.settings
        val expanded = entry.state.isExpanded
        when (swipe) {
            EdgeCardSwipe.None -> {
                if (!expanded) {
                    entry.state = entry.state.onTap()
                    scheduleAutoCollapse(entry)
                    applyEntry(entry, animated = true)
                } else {
                    // 展开态单击严格按「手势动作 → 单击」执行：Ignore 就是忽略（关卡片、不跳转）。
                    // 以前把 Ignore 当"没配过"的哨兵直接打开会话，而它恰好也是默认值，
                    // 于是设置页显示"忽略"、实际却跳转 APP。
                    dispatch(entry, settings.singleTapAction)
                }
            }
            EdgeCardSwipe.Expand -> {
                if (!expanded) {
                    entry.state = entry.state.onSwipeLeft()
                    scheduleAutoCollapse(entry)
                    applyEntry(entry, animated = true)
                } else {
                    dispatchOrKeep(entry, settings.swipeLeftAction)
                }
            }
            EdgeCardSwipe.Collapse -> {
                if (expanded) {
                    cancelAutoCollapse(entry)
                    entry.state = entry.state.onSwipeRight()
                    applyEntry(entry, animated = true)
                } else {
                    dispatchOrKeep(entry, settings.swipeRightAction)
                }
            }
            EdgeCardSwipe.Dismiss -> {
                if (!expanded) {
                    mainHandler.post { removeEntry(entry, notifyDismiss = true) }
                } else {
                    val action = if (totalY < 0f) settings.swipeUpAction else settings.swipeDownAction
                    if (action == MessageAction.Ignore) {
                        mainHandler.post { removeEntry(entry, notifyDismiss = true) }
                    } else {
                        dispatch(entry, action)
                    }
                }
            }
        }
    }

    /** 拖动过程中直接跟手改窗口 x，不触发任何测量或布局。 */
    private fun dragTo(entry: EdgeCardEntry, dx: Float) {
        val view = entry.view ?: return
        val params = entry.params ?: return
        entry.spring?.cancel()
        entry.spring = null
        val from = collapsedX(entry)
        val to = expandedX(entry)
        val baseX = if (entry.state.isExpanded) to else from
        params.x = (baseX + dx).roundToInt().coerceIn(minOf(from, to), maxOf(from, to))
        val span = (to - from).toFloat()
        val p = if (span == 0f) 1f else ((params.x - from) / span).coerceIn(0f, 1f)
        val collapsedAlpha = entry.plan.settings.edgeCardOpacity.coerceIn(0f, 1f)
        view.alpha = collapsedAlpha + (1f - collapsedAlpha) * p
        view.marqueeActive = false
        if (view.isAttachedToWindow) {
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
    }

    private fun dispatchOrKeep(entry: EdgeCardEntry, action: MessageAction) {
        if (action == MessageAction.Ignore) return
        dispatch(entry, action)
    }

    private fun dispatch(entry: EdgeCardEntry, action: MessageAction) {
        val callback = entry.actionCallback
        mainHandler.post { removeEntry(entry, notifyDismiss = false) }
        callback?.invoke(action)
    }

    // ---- 计时 ----

    private fun scheduleAutoCollapse(entry: EdgeCardEntry) {
        cancelAutoCollapse(entry)
        val delayMs = entry.plan.settings.edgeCardAutoCollapseMs.coerceIn(0, 30_000)
        if (delayMs <= 0 || !entry.state.isExpanded) return
        // 内容溢出时收起完全交给滚动结束回调，避免计时器在滚动中途把卡片收走
        // （新消息卡片是非动画展开，滚动启动早于这里 arm，没人再取消）。
        if (entry.plan.settings.edgeCardCollapseWhenScrollDone &&
            entry.view?.isContentOverflowing == true
        ) {
            Log.d(TAG, "autoCollapse skipped: waiting scroll end")
            return
        }
        Log.d(TAG, "autoCollapse armed: ${delayMs}ms")
        val runnable = object : Runnable {
            override fun run() {
                entry.autoCollapse = null
                if (entry.interacting) {
                    mainHandler.postDelayed(this, USER_INTERACTION_GRACE_MS)
                    return
                }
                entry.state = entry.state.collapse()
                applyEntry(entry, animated = true)
            }
        }
        entry.autoCollapse = runnable
        mainHandler.postDelayed(runnable, delayMs.toLong())
    }

    private fun cancelAutoCollapse(entry: EdgeCardEntry) {
        entry.autoCollapse?.let { mainHandler.removeCallbacks(it) }
        entry.autoCollapse = null
    }

    /** 收起卡片：内容滚完、或定时到点时都走这里。 */
    private fun collapseEntry(entry: EdgeCardEntry) {
        cancelAutoCollapse(entry)
        if (!entry.state.isExpanded) return
        entry.state = entry.state.collapse()
        applyEntry(entry, animated = true)
    }

    // ---- 触摸 ----

    private class EdgeCardTouchListener(
        private val entry: EdgeCardEntry,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var totalX = 0f
        private var totalY = 0f
        private var touchSlop = 0f
        private var moved = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    totalX = 0f
                    totalY = 0f
                    touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
                    moved = false
                    entry.interacting = true
                    EdgeCardOverlayWindow.cancelAutoCollapse(entry)
                    entry.spring?.cancel()
                    entry.spring = null
                }
                MotionEvent.ACTION_MOVE -> {
                    totalX = event.rawX - downX
                    totalY = event.rawY - downY
                    if (abs(totalX) > abs(totalY) &&
                        abs(totalX) > EdgeCardOverlayWindow.dp(4f)
                    ) {
                        moved = true
                        EdgeCardOverlayWindow.dragTo(entry, totalX)
                    } else if (!moved &&
                        (abs(totalX) > touchSlop || abs(totalY) > touchSlop)
                    ) {
                        moved = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    entry.interacting = false
                    val swipe = EdgeCardUiState.resolveSwipe(
                        totalX = totalX,
                        totalY = totalY,
                        horizontalThresholdPx =
                            EdgeCardOverlayWindow.dp(EdgeCardOverlayWindow.HORIZONTAL_SWIPE_DP).toFloat(),
                        verticalThresholdPx =
                            EdgeCardOverlayWindow.dp(EdgeCardOverlayWindow.VERTICAL_DISMISS_DP).toFloat(),
                    )
                    // 必须以 resolveSwipe 为准：以前只有横拖才算“滑动”，纵向滑动一律当成点击
                    // （下滑 → 展开态单击 → 打开会话），配置的上下滑动作永远不生效。
                    when {
                        swipe != EdgeCardSwipe.None ->
                            EdgeCardOverlayWindow.onGesture(entry, totalX, totalY, swipe)
                        // 手指动过但没过阈值：回位，不当作点击。
                        moved -> EdgeCardOverlayWindow.settleBack(entry)
                        else -> EdgeCardOverlayWindow.onGesture(
                            entry,
                            totalX,
                            totalY,
                            EdgeCardSwipe.None,
                        )
                    }
                    if (entry.view != null && entry.state.isExpanded) {
                        EdgeCardOverlayWindow.scheduleAutoCollapse(entry)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    // 系统截走手势不是点击：回位即可，别把取消当成“打开会话”。
                    entry.interacting = false
                    EdgeCardOverlayWindow.settleBack(entry)
                    if (entry.view != null && entry.state.isExpanded) {
                        EdgeCardOverlayWindow.scheduleAutoCollapse(entry)
                    }
                }
            }
            return true
        }
    }
}

private class EdgeCardEntry(
    val key: String,
    val preview: Boolean = false,
) {
    var plan: MessageDisplayPlan = placeholderPlan()
    var state: EdgeCardUiState = EdgeCardUiState()
    var view: EdgeCardView? = null
    var params: WindowManager.LayoutParams? = null
    var spring: SpringAnimation? = null
    var autoCollapse: Runnable? = null
    var actionCallback: ((MessageAction) -> Unit)? = null
    var dismissCallback: (() -> Unit)? = null
    var interacting: Boolean = false
    var y: Int = 0
    var windowWidthPx: Int = 0
    /** 卡片实际高度（随正文行数变化），用于多卡堆叠定位。 */
    var heightPx: Int = 0
    var onHeightChanged: ((Int) -> Unit)? = null
    var onWidthChanged: ((Int) -> Unit)? = null
    val createdAt: Long = android.os.SystemClock.uptimeMillis()

    fun matches(data: NotificationData): Boolean {
        if (plan.data.key == data.key) return true
        val a = NotificationData.conversationIdentityKey(plan.data)
        val b = NotificationData.conversationIdentityKey(data)
        return a.isNotBlank() && a == b
    }
}

private fun placeholderPlan(): MessageDisplayPlan = MessageDisplayPlan(
    data = NotificationData(
        packageName = "",
        key = "",
        title = "",
        content = "",
        largeIcon = null,
        appIcon = null,
        contentIntent = null,
        postTime = 0L,
    ),
    showFloatIcon = false,
    showSideBubble = false,
    showDanmaku = false,
    showCNotice = false,
    sideTheme = null,
    danmakuTheme = null,
    settings = MessageSettings(),
)

/**
 * 单张卡片的 View：固定展开宽度，头像 + 两行文字 + 角标。
 * 收起时整窗移出屏幕，内容本身不做任何尺寸或透明度动画。
 */
private const val SCROLL_HOLD_MIN_MS = 1_200L
private const val SCROLL_HOLD_PER_LINE_MS = 28L
private const val SCROLL_HOLD_MAX_MS = 4_500L
private const val SCROLL_ANIM_MS = 350L
private const val SCROLL_STEP_PAUSE_MS = 800L
/** 滚到最后一行后停留多久再收起。 */
private const val SCROLL_FINISH_HOLD_MS = 1_000L

/** 挂起超过这个时长就不再补显，避免解锁瞬间弹出一堆过期卡片。 */
private const val LOCK_SUSPEND_TTL_MS = 10 * 60 * 1000L

private class EdgeCardView(context: Context) : ViewGroup(context) {
    var exclusionEdge: SideBubbleHorizontalEdge = SideBubbleHorizontalEdge.Right
        set(value) {
            field = value
            updateGestureExclusion()
        }

    /** 动效开关：展开时启用（单行走跑马灯 / 多行走竖向慢滚），收起立即停止并复位。 */
    var marqueeActive: Boolean
        get() = contentView.isSelected
        set(value) {
            if (contentView.isSelected == value) return
            contentView.isSelected = value
            if (value) startVerticalScroll() else stopVerticalScroll()
        }

    private val scrollScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var scrollJob: Job? = null
    private var scrollAnimator: ValueAnimator? = null

    /** 开始竖向滚动（外层据此暂停自动收起计时）。 */
    var onScrollStarted: (() -> Unit)? = null

    /** 滚到最后一行并停留够了（外层据此收起卡片）。 */
    var onScrollFinished: (() -> Unit)? = null

    /** 正文是否有溢出（需要滚动）。外层据此决定要不要挂自动收起计时器。 */
    var isContentOverflowing: Boolean = false
        private set

    /**
     * 多行溢出时的竖向慢滚（对齐悬浮菜单侧边样式）：
     * 停 1.2s 起 → 350ms 上移一行 → 停 800ms → … 滚到最后一行后停 1s 结束，不再回顶。
     * 偏移按 [Layout] 的真实行坐标算，避免 lineHeight 乘法误差把最后一行切掉。
     */
    private fun startVerticalScroll() {
        stopVerticalScroll()
        val layout = contentView.layout
        val overflowLines = if (layout == null || maxLines <= 1) 0 else layout.lineCount - maxLines
        isContentOverflowing = overflowLines > 0
        if (layout == null || overflowLines <= 0) return
        // 每页的目标偏移：行首坐标；最后一页对齐到“滚到底”，保证末行完整可见。
        val maxOffset = (layout.height - contentView.height).coerceAtLeast(0)
        val offsets = ArrayList<Int>(overflowLines + 1)
        for (index in 0 until overflowLines) {
            offsets.add(layout.getLineTop(index).coerceAtMost(maxOffset))
        }
        offsets.add(maxOffset)
        if (offsets.size < 2) return
        onScrollStarted?.invoke()
        scrollJob = scrollScope.launch {
            try {
                for (index in 0 until overflowLines) {
                    if (!isActive) break
                    val hold = (index.toLong() * SCROLL_HOLD_PER_LINE_MS)
                        .coerceIn(SCROLL_HOLD_MIN_MS, SCROLL_HOLD_MAX_MS)
                    delay(hold)
                    animateScroll(offsets[index], offsets[index + 1])
                    delay(SCROLL_STEP_PAUSE_MS)
                }
                if (!isActive) return@launch
                delay(SCROLL_FINISH_HOLD_MS)
                if (isActive) onScrollFinished?.invoke()
            } finally {
                if (!isActive) contentView.scrollTo(0, 0)
            }
        }
    }

    private fun stopVerticalScroll() {
        scrollJob?.cancel()
        scrollJob = null
        scrollAnimator?.cancel()
        scrollAnimator = null
        contentView.scrollTo(0, 0)
    }

    private suspend fun animateScroll(from: Int, to: Int) = suspendCancellableCoroutine { cont ->
        val animator = ValueAnimator.ofInt(from, to).apply {
            duration = SCROLL_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { contentView.scrollTo(0, it.animatedValue as Int) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (cont.isActive) cont.resume(Unit)
                }
            })
        }
        scrollAnimator = animator
        cont.invokeOnCancellation { animator.cancel() }
        animator.start()
    }

    override fun onDetachedFromWindow() {
        stopVerticalScroll()
        scrollScope.cancel()
        super.onDetachedFromWindow()
    }

    private var entranceSpring: SpringAnimation? = null

    /**
     * 入场动画：整卡从屏幕外侧滑入、由透明变实，图标顺带转一圈。
     * 只改 View 的 translationX/alpha，不动窗口位置，所以不会触发任何 resize。
     */
    fun playEntrance(targetAlpha: Float) {
        val span = width.toFloat().coerceAtLeast(1f)
        entranceSpring?.cancel()
        entranceSpring = null
        translationX = if (exclusionEdge == SideBubbleHorizontalEdge.Right) span else -span
        alpha = 0f
        iconView.rotation = 0f
        val spring = SpringAnimation(this, DynamicAnimation.TRANSLATION_X).apply {
            setSpring(
                SpringForce(0f).apply {
                    stiffness = EdgeCardOverlayWindow.SPRING_STIFFNESS
                    dampingRatio = EdgeCardOverlayWindow.SPRING_DAMPING
                },
            )
            setMinimumVisibleChange(1f)
            addUpdateListener { _, value, _ ->
                val p = 1f - (abs(value) / span).coerceIn(0f, 1f)
                alpha = targetAlpha * p
                iconView.rotation = p * 360f
            }
            addEndListener { _, _, _, _ ->
                entranceSpring = null
                translationX = 0f
                alpha = targetAlpha
                iconView.rotation = 0f
            }
        }
        entranceSpring = spring
        spring.animateToFinalPosition(0f)
    }

    fun cancelEntrance() {
        entranceSpring?.cancel()
        entranceSpring = null
    }

    private val iconView = ImageView(context)
    /** 连 App 图标都取不到时的占位：首字圆形底。 */
    private val fallbackView = TextView(context)
    private val badgeView = TextView(context)
    private val titleView = TextView(context)
    private val senderView = TextView(context)
    /** 标题行：群名（左，可截断）+ 发言人（右，灰色小字）。 */
    private val headerRow = LinearLayout(context)
    private val contentView = TextView(context)
    /** 38dp 头像容器：图标 30dp 缩进 4dp，角标也挂在这一层，正好压在圆形图标左上角。 */
    private val avatarContainer = FrameLayout(context)
    private val textLayer = LinearLayout(context)
    private val cardLayer = FrameLayout(context)


    init {
        clipChildren = false
        clipToPadding = false

        iconView.scaleType = ImageView.ScaleType.CENTER_CROP
        iconView.clipToOutline = true
        iconView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        badgeView.gravity = Gravity.CENTER
        badgeView.setTextColor(Color.WHITE)
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
        badgeView.includeFontPadding = false
        badgeView.setBackgroundDrawable(circleDrawable(Color.parseColor("#E74749")))
        fallbackView.gravity = Gravity.CENTER
        fallbackView.setTextColor(Color.WHITE)
        fallbackView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        fallbackView.includeFontPadding = false
        fallbackView.setBackgroundDrawable(circleDrawable(Color.parseColor("#9AA0A6")))
        fallbackView.visibility = View.GONE
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.setTextColor(Color.parseColor("#2B2B2B"))
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        titleView.setTypeface(titleView.typeface, android.graphics.Typeface.BOLD)
        titleView.includeFontPadding = false
        // 群聊时在标题右侧显示发言人（来自 MessagingStyle 的 sender，不用标题里的冒号猜）。
        senderView.maxLines = 1
        senderView.ellipsize = TextUtils.TruncateAt.END
        senderView.setTextColor(Color.parseColor("#9AA0A6"))
        senderView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        senderView.includeFontPadding = false
        senderView.visibility = View.GONE
        contentView.maxLines = 1
        contentView.isSingleLine = true
        contentView.ellipsize = TextUtils.TruncateAt.MARQUEE
        contentView.marqueeRepeatLimit = -1
        contentView.setTextColor(Color.parseColor("#6B6B6B"))
        contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        contentView.includeFontPadding = false

        textLayer.orientation = LinearLayout.VERTICAL
        textLayer.gravity = Gravity.CENTER_VERTICAL
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL
        headerRow.addView(
            titleView,
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        headerRow.addView(
            senderView,
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) },
        )
        textLayer.addView(headerRow, LinearLayout.LayoutParams(-1, -2))
        textLayer.addView(
            contentView,
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) },
        )
        avatarContainer.clipChildren = false
        avatarContainer.clipToPadding = false
        avatarContainer.addView(
            iconView,
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER).apply {
                leftMargin = dp(4)
                topMargin = dp(4)
                rightMargin = dp(4)
                bottomMargin = dp(4)
            },
        )
        avatarContainer.addView(
            fallbackView,
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER).apply {
                leftMargin = dp(4)
                topMargin = dp(4)
                rightMargin = dp(4)
                bottomMargin = dp(4)
            },
        )
        avatarContainer.addView(
            badgeView,
            FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(4)
                topMargin = dp(4)
            },
        )
        cardLayer.addView(
            avatarContainer,
            FrameLayout.LayoutParams(dp(38), dp(38), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(2)
            },
        )
        cardLayer.addView(
            textLayer,
            FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(44)
                rightMargin = dp(8)
            },
        )
        addView(cardLayer, LayoutParams(-1, -1))
    }

    /**
     * 卡片皮肤：复用侧边气泡主题的左右背景 drawable（形状与贴边方向一致），
     * 文字色也取自主题；主题没有侧边资源时退回白底 + 1px 描边。
     */
    /** 固定外观：半圆 + 矩形，白底 + 1 物理像素描边。圆角按实际高度算，多行时仍是半圆。 */
    private fun applyCardBackground(right: Boolean, heightPx: Int) {
        val corner = (heightPx.coerceAtLeast(dp(40)) / 2).toFloat()
        cardLayer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            setStroke(1, Color.parseColor("#A1A2A3"))
            cornerRadii = if (right) {
                floatArrayOf(corner, corner, 0f, 0f, 0f, 0f, corner, corner)
            } else {
                floatArrayOf(0f, 0f, corner, corner, corner, corner, 0f, 0f)
            }
        }
    }

    fun bind(plan: MessageDisplayPlan, state: EdgeCardUiState) {
        val data = plan.data
        val settings = plan.settings
        val title = NotificationData.overlayHeaderTitle(data)
        // 三级回落：会话/通知大图 → App 图标 → 首字占位，避免 Threads 这类无大图通知出现白板。
        val icon = data.conversationIcon ?: data.largeIcon ?: data.appIcon
        if (icon != null) {
            iconView.setImageBitmap(icon)
            iconView.visibility = View.VISIBLE
            fallbackView.visibility = View.GONE
        } else {
            iconView.setImageBitmap(null)
            iconView.visibility = View.GONE
            fallbackView.text = title.trim().firstOrNull()?.toString().orEmpty()
            fallbackView.visibility = View.VISIBLE
        }
        titleView.text = title
        val sender = NotificationData.overlaySenderName(data)
        senderView.text = sender
        senderView.visibility = if (sender.isEmpty()) View.GONE else View.VISIBLE
        contentView.text = data.content
        val badge = state.badgeText()
        if (badge != null) {
            badgeView.text = badge
            badgeView.visibility = View.VISIBLE
        } else {
            badgeView.visibility = View.GONE
        }

        val right = settings.edgeCardHorizontalEdge == SideBubbleHorizontalEdge.Right
        alignedRight = right
        applyCardBackground(right, if (height > 0) height else dp(42))

        // 行数上限：完全照悬浮菜单侧边样式——
        // 单行：singleLine + 省略号；多行：不限行、不省略，靠 onMeasure 里钉死的高度裁切。
        maxLines = EdgeCardOverlayWindow.lineCount(settings)
        contentView.isSingleLine = maxLines == 1
        if (maxLines == 1) {
            contentView.maxLines = 1
            contentView.ellipsize = TextUtils.TruncateAt.MARQUEE
        } else {
            contentView.maxLines = Int.MAX_VALUE
            contentView.ellipsize = null
        }

        val avatarParams = avatarContainer.layoutParams as FrameLayout.LayoutParams
        avatarParams.gravity = (if (right) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        avatarParams.leftMargin = if (right) dp(2) else 0
        avatarParams.rightMargin = if (right) 0 else dp(2)
        avatarContainer.layoutParams = avatarParams

        val textParams = textLayer.layoutParams as FrameLayout.LayoutParams
        textParams.gravity = (if (right) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        textParams.leftMargin = if (right) dp(44) else dp(8)
        textParams.rightMargin = if (right) dp(8) else dp(44)
        textLayer.layoutParams = textParams

        val badgeParams = badgeView.layoutParams as FrameLayout.LayoutParams
        badgeParams.gravity = (if (right) Gravity.START else Gravity.END) or Gravity.TOP
        badgeView.layoutParams = badgeParams

        // 卡片背景铺满整个窗口，贴边侧才能顶到屏幕边缘。
        setPadding(0, 0, 0, 0)
        requestLayout()
        // 内容换了之后溢出情况可能变化，展开态下重启竖向滚动。
        scrollJob?.cancel()
        scrollJob = null
        contentView.scrollTo(0, 0)
        if (contentView.isSelected) startVerticalScroll()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 宽度按内容算：图标区固定，文字区取标题/正文里较宽的那个，限制在 40..120dp。
        val maxText = dp(120)
        val minText = dp(40)
        // 群聊时把发言人宽度一起算进去，避免标题把发送者挤没。
        val senderW = if (senderView.visibility == View.VISIBLE) {
            measureTextWidth(senderView, dp(60))
        } else {
            0
        }
        val titleAvail = (maxText - senderW - dp(4)).coerceAtLeast(dp(24))
        val textW = maxOf(
            measureTextWidth(titleView, titleAvail) + senderW + dp(4),
            measureTextWidth(contentView, maxText),
        ).coerceIn(minText, maxText)
        val totalW = dp(48) + textW + dp(8)
        // 始终用内容算出的宽度：窗口传 EXACTLY（上一帧宽度）时也不能锁死，
        // 否则文字变短后卡片缩不回去，右侧会留一片空白。
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            minOf(totalW, MeasureSpec.getSize(widthMeasureSpec))
        } else {
            totalW
        }
        // 照悬浮菜单的做法：先把正文按最终宽度排一遍拿到行数（maxLines 已生效），
        // 再把正文高度钉死成 lineHeight × 行数（行高不低于 16dp）。
        // 高度钉死之后，多出来的行会被裁在固定高度里，不会露第三行，也不会裁到字。
        val exactText = MeasureSpec.makeMeasureSpec(textW, MeasureSpec.EXACTLY)
        val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        titleView.measure(exactText, unbounded)
        contentView.measure(exactText, unbounded)
        val lines = (contentView.layout?.lineCount ?: 1).coerceIn(1, maxLines)
        val lineHeight = maxOf(contentView.lineHeight, dp(16))
        val contentHeight = lines * lineHeight
        (contentView.layoutParams as? LinearLayout.LayoutParams)?.let {
            if (it.height != contentHeight) {
                it.height = contentHeight
                contentView.layoutParams = it
            }
        }
        val textHeight = maxOf(titleView.measuredHeight, senderView.measuredHeight) +
            dp(2) + contentHeight
        val cardHeight = maxOf(textHeight + dp(8), dp(42))
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            minOf(cardHeight, MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: cardHeight)
        } else {
            cardHeight
        }
        setMeasuredDimension(w, h)
        cardLayer.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
        )
    }

    private fun measureTextWidth(view: TextView, maxWidth: Int): Int {
        view.measure(
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        return view.measuredWidth
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        cardLayer.layout(0, 0, r - l, b - t)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGestureExclusion()
        if (w != oldw) onWidthChanged?.invoke(w)
        if (h != oldh && h > 0) {
            // 高度随正文行数变化：圆角要重算，外层也要重排堆叠位置。
            applyCardBackground(alignedRight, h)
            onHeightChanged?.invoke(h)
        }
    }

    /** 卡片内容宽度变化（文字长短变了）时通知外层重算贴边位置。 */
    var onWidthChanged: ((Int) -> Unit)? = null

    /** 卡片高度变化（正文行数变了）时通知外层重排。 */
    var onHeightChanged: ((Int) -> Unit)? = null

    private var alignedRight = true
    private var maxLines = 1

    private fun updateGestureExclusion() {
        if (width <= 0 || height <= 0) return
        val band = dp(24).coerceAtMost(width)
        if (band <= 0) return
        val rect = if (exclusionEdge == SideBubbleHorizontalEdge.Right) {
            Rect(width - band, 0, width, height)
        } else {
            Rect(0, 0, band, height)
        }
        ViewCompat.setSystemGestureExclusionRects(this, listOf(rect))
    }

    private fun circleDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()
}
