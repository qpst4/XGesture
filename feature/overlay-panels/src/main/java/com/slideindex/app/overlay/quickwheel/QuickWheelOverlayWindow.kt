package com.slideindex.app.overlay.quickwheel

import com.slideindex.app.overlay.overlayHostContext

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.QuickWheelAnchorMode
import com.slideindex.app.gesture.QuickWheelLaunchShape
import com.slideindex.app.overlay.OverlayBlurGate
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.layout.QuickWheelAdaptiveScreen
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskExclusions
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.settings.QuickWheelLaunchMode
import com.slideindex.app.settings.QuickWheelSlot
import com.slideindex.app.settings.opensSomething
import com.slideindex.app.settings.withQuickWheelLaunchMode
import com.slideindex.app.settings.withoutLaunchWindowMode
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme

/**
 * 「快捷轮盘」浮层宿主（Compose）。
 *
 * 由手势动作 [com.slideindex.app.gesture.GestureAction.QuickWheel] 触发，
 * 或由配置页「轮盘预览」以草稿数据直接呼出。轮盘为**驻留式**：点容器执行动作后收起，点空白处收起。
 */
@SuppressLint("StaticFieldLeak")
object QuickWheelOverlayWindow {
    private const val TAG = "QuickWheelOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var composeView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private var windowManager: WindowManager? = null
    private var activeExecutor: com.slideindex.app.overlay.panels.GestureActionRunner? = null
    private var activeSettings: AppSettings = AppSettings()

    /** 本应用包名：容器选了"始终小窗"时用于硬排除判定（桌面 / 系统界面 / 自身 → 仍全屏）。 */
    private var activeSelfPackage: String = ""

    /** 持续触发：手指由边滑手势会话接管，浮层窗口不接收触摸。 */
    private var externalTracking = false

    /** 持续触发：由内容层注册的外部移动 / 抬起回调。 */
    private var externalMoveHandler: ((Float, Float) -> Unit)? = null
    private var externalUpHandler: ((Float, Float) -> Unit)? = null

    /**
     * 当前窗口的**代次**：每次 [dismiss] / 每次成功呼出都会 +1。
     *
     * 存在的理由：[dismiss] 在非主线程调用时会 `post` 到主线程；[onDismiss] 也来自内容层回调。
     * 这些延迟执行体如果无条件收起，就会把"这段时间里刚呼出的新窗口"一起关掉
     * （实测表现：轮盘闪一下就自己消失）。所以延迟体只在自己那一代仍是当前代时才生效。
     */
    @Volatile
    private var session = 0

    val isShowing: Boolean get() = composeView != null

    /**
     * 手势触发：按 [wheelId] 取已保存的轮盘（空串取第一个）。
     *
     * @param shape 动作绑定处选定的呼出形态；[QuickWheelLaunchShape.DEFAULT] 时沿用轮盘自身形态。
     */
    fun show(
        context: Context,
        settings: AppSettings,
        wheelId: String,
        anchorRawX: Float,
        anchorRawY: Float,
        actionExecutor: com.slideindex.app.overlay.panels.GestureActionRunner,
        externalTracking: Boolean = false,
        shape: QuickWheelLaunchShape = QuickWheelLaunchShape.DEFAULT,
        /** 动作绑定处手动固定的**一级圆形**扇区；`null`（默认）= 按触发位置自适应求解。 */
        manualSectorMask: Int? = null,
        /** 圆心锚定方式：跟手（默认）/ 贴边。 */
        anchorMode: QuickWheelAnchorMode = QuickWheelAnchorMode.FOLLOW_FINGER,
    ): Boolean {
        // 始终以「最新设置快照」为准：调用方（手势会话）传入的 settings 可能是旧快照，
        // 会出现"预览已改、调用仍是旧配置"，甚至取不到轮盘而不显示。
        val latestSettings = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsSnapshot
            ?.invoke(context)
        val effective = latestSettings ?: settings
        val resolved = resolveWheel(effective, wheelId) ?: resolveWheel(settings, wheelId) ?: run {
            Log.w(TAG, "show: 没有可用的轮盘配置（wheelId='$wheelId'）")
            return false
        }
        // 动作绑定处选定的形态优先；DEFAULT 才用轮盘自身形态。
        val wheel = applyLaunchShape(resolved, shape)
        Log.i(
            TAG,
            "show wheelId='$wheelId' shape=$shape callerWheels=${settings.launcher.quickWheels.size} " +
                "snapshotWheels=${latestSettings?.launcher?.quickWheels?.size ?: -1} " +
                "resolved=${wheel.id.take(8)} slots=${wheel.slots.size} " +
                "configured=${wheel.primaryActionCount} " +
                "primaryShape=${wheel.primaryShape} " +
                "containerSizeDp=${wheel.primaryStyle.containerSizeDp} " +
                "manualSector=${manualSectorMask ?: "auto"}",
        )
        return showInternal(
            context = context,
            settings = effective,
            wheel = wheel,
            anchorRawX = anchorRawX,
            anchorRawY = anchorRawY,
            actionExecutor = actionExecutor,
            externalTracking = externalTracking,
            // 真实手势呼出：圆形中心容器全透明不显示（其动作仍可命中触发）。
            showCenter = false,
            // 真实手势呼出：扇区 / 矩形基准角按"触发点 + 屏幕空间"自适应求解。
            adaptivePlacement = true,
            // 动作绑定处手动固定的一级圆形扇区（null = 自动）。
            primarySectorMaskOverride = manualSectorMask,
            // 圆心锚定方式（跟手 / 贴边）。
            anchorMode = anchorMode,
        )
    }

    /**
     * 按动作绑定的形态覆盖一级 / 二级轮盘形态。
     *
     * `copy(shape=…).normalized()` 会把 `primaryStyle` / `secondaryStyle` 同步为对应形态的那套参数，
     * 因此圆形 / 矩形各自的调参都能被沿用。未指定的一级（`FOLLOW`）保持轮盘自身配置不动——
     * 旧值 [QuickWheelLaunchShape.CIRCLE] / [QuickWheelLaunchShape.RECT] 就是"只覆盖一级"。
     */
    private fun applyLaunchShape(wheel: QuickWheel, shape: QuickWheelLaunchShape): QuickWheel {
        val primary = shape.primaryLevel.toWheelShapeOrNull()
        val secondary = shape.secondaryLevel.toWheelShapeOrNull()
        if (primary == null && secondary == null) return wheel
        return wheel.copy(
            primaryShape = primary ?: wheel.primaryShape,
            secondaryShape = secondary ?: wheel.secondaryShape,
        ).normalized()
    }

    /** 把呼出形态按级拆解成引擎形态；`null` = 该级跟随轮盘自身配置（`com.slideindex.app.ui`）。 */

    /** 配置页「轮盘预览」：直接用草稿轮盘呼出。 */
    fun showPreview(
        context: Context,
        settings: AppSettings,
        wheel: QuickWheel,
        anchorRawX: Float,
        anchorRawY: Float,
        actionExecutor: com.slideindex.app.overlay.panels.GestureActionRunner,
    ): Boolean = showInternal(
        context = context,
        settings = settings,
        wheel = wheel,
        anchorRawX = anchorRawX,
        anchorRawY = anchorRawY,
        actionExecutor = actionExecutor,
        externalTracking = false,
        // 配置页预览：显示中心大圆，便于查看与试玩中心动作。
        showCenter = true,
        // 配置页预览不做自适应：按"扇区测试选择"渲染在它的理论位置上（锚点由调用方给出）。
        adaptivePlacement = false,
    )

    private fun showInternal(
        context: Context,
        settings: AppSettings,
        wheel: QuickWheel,
        anchorRawX: Float,
        anchorRawY: Float,
        actionExecutor: com.slideindex.app.overlay.panels.GestureActionRunner,
        externalTracking: Boolean,
        showCenter: Boolean,
        adaptivePlacement: Boolean,
        /** 手动固定的一级圆形扇区（`null` = 自适应求解；动作页"空环"即 null）。 */
        primarySectorMaskOverride: Int? = null,
        /** 圆心锚定方式：跟手（默认）/ 贴边。 */
        anchorMode: QuickWheelAnchorMode = QuickWheelAnchorMode.FOLLOW_FINGER,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // ⚠️ 等待期间主线程可能已经 dismiss()（手势会话结束 / dismissAllPanels）。
            // 那时这次呼出必须作废：否则下面仍会 addView，而 composeView 等字段已被清空，
            // 结果是"窗口留在屏幕上、再没有任何人能移除它"，且 isShowing 永远是 true。
            val expected = session
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = if (session != expected) {
                    Log.i(TAG, "show: 等待主线程期间窗口已被收起，作废本次呼出")
                    false
                } else {
                    showInternal(
                        context = context,
                        settings = settings,
                        wheel = wheel,
                        anchorRawX = anchorRawX,
                        anchorRawY = anchorRawY,
                        actionExecutor = actionExecutor,
                        externalTracking = externalTracking,
                        showCenter = showCenter,
                        adaptivePlacement = adaptivePlacement,
                        primarySectorMaskOverride = primarySectorMaskOverride,
                        anchorMode = anchorMode,
                    )
                }
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }

        dismiss()
        // dismiss() 之后 session 就是"这一次呼出"的代号；本函数在主线程上是同步跑完的，
        // 中途不会再有人改它，记下来给 onDismiss / 延迟回调核对"要关的是不是我"。
        val mySession = session
        this.externalTracking = externalTracking
        if (wheel.slots.isEmpty() && !wheel.centerSlot.isConfigured) {
            Log.w(TAG, "show: 轮盘为空，忽略")
            return false
        }

        val hostContext = overlayHostContext() ?: context
        val wm = hostContext.getSystemService(WindowManager::class.java) ?: return false
        val metrics = hostContext.resources.displayMetrics
        val density = if (metrics.density > 0f) metrics.density else 1f
        val screenWidthPx = metrics.widthPixels.toFloat()
        val screenHeightPx = metrics.heightPixels.toFloat()

        // ⚠️ 锚点 = 调用方给的触发点（真实呼出 = 触发动作那一帧的手指位置；配置页预览 = 该扇区的"理论位置"）。
        // 以前这里会把轮盘强行摆到屏幕正中，导致"轮盘出现的位置和触发动作对不上、还容易缺容器"。
        val rawAnchorX = anchorRawX.coerceIn(0f, screenWidthPx)
        val rawAnchorY = anchorRawY.coerceIn(0f, screenHeightPx)
        // 「贴边」模式：圆心投影到最近的屏幕边线（沿边坐标不变）→ 整圆必然越界，
        // 运行时的自适应求解会自动收窄成半圆 / 90°，所以这里只需换锚点，不必动求解器。
        val (anchorX, anchorY) = if (anchorMode == QuickWheelAnchorMode.EDGE) {
            QuickWheelLayoutEngine.projectAnchorToEdge(
                anchorX = rawAnchorX,
                anchorY = rawAnchorY,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
        } else {
            rawAnchorX to rawAnchorY
        }
        // 真实呼出：扇区（圆形）/ 网格基准角（矩形）按"锚点 + 所有容器都在屏内"自适应求解。
        val adaptive = if (adaptivePlacement) {
            QuickWheelAdaptiveScreen(
                widthPx = screenWidthPx,
                heightPx = screenHeightPx,
                marginPx = QuickWheelLayoutEngine.ADAPTIVE_MARGIN_DP * density,
            )
        } else {
            null
        }
        // 配置页预览（一级非自适应）：**二级仍按屏幕求解** —— 一级保持"所见即所得"（用户配的扇区），
        // 二级与真机一致（锚点 = 被展开的父容器中心，保证二级容器都在屏内）。
        val secondaryAdaptive = if (adaptivePlacement) {
            null
        } else {
            QuickWheelAdaptiveScreen(
                widthPx = screenWidthPx,
                heightPx = screenHeightPx,
                marginPx = QuickWheelLayoutEngine.ADAPTIVE_MARGIN_DP * density,
            )
        }

        activeExecutor = actionExecutor
        activeSettings = settings
        activeSelfPackage = hostContext.packageName

        val owner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(hostContext, owner).apply {
            setContent {
                OverlayAwareModuleTheme {
                    QuickWheelOverlayContent(
                        wheel = wheel,
                        anchorX = anchorX,
                        anchorY = anchorY,
                        screenWidthPx = screenWidthPx,
                        onExecuteTap = { slot -> executeSlot(slot, longPress = false, anchorX, anchorY) },
                        onExecuteLongPress = { slot -> executeSlot(slot, longPress = true, anchorX, anchorY) },
                        onDismiss = { dismissForSession(mySession) },
                        externalTracking = externalTracking,
                        showCenter = showCenter,
                        adaptive = adaptive,
                        secondaryAdaptive = secondaryAdaptive,
                        primarySectorMaskOverride = primarySectorMaskOverride,
                        // 容器以外区域的黑色遮罩浓度（容器本身不透明，不受影响）。
                        backdropDimAlpha = QuickWheelLayoutEngine
                            .clampBackdropDimPercent(wheel.backdropDimPercent) / 100f,
                        shellCommands = settings.shellCommands,
                        onExternalHandlers = { move, up ->
                            externalMoveHandler = move
                            externalUpHandler = up
                        },
                    )
                }
            }
            // 与「圆环启动器」一致：显式声明视图不透明（View 默认即 1f，
            // AppSwitcherOverlayController.show 里同样显式写 next.alpha = 1f）。
            alpha = 1f
        }

        // ⚠️ 容器必须**完全不透明**：优先 TYPE_APPLICATION_OVERLAY。
        // 权限判定必须用 applicationContext —— 用浮层 host context 会误判成 false，
        // 一旦退回 TYPE_ACCESSIBILITY_OVERLAY，部分 OEM（如 Flyme）会强制叠加 ~0.8 alpha，
        // 整个轮盘（含容器）发灰半透明。
        val canHostOpaqueOverlay = runCatching {
            PermissionHelper.canDrawOverlays(hostContext.applicationContext)
        }.getOrDefault(false)
        val blurDp = OverlayBlurGate.effectiveBlurRadiusDp(
            settings.overlayBlurEnabled,
            QuickWheelLayoutEngine.clampBackdropBlurDp(wheel.backdropBlurDp),
        )
        val blurAvailable = blurDp > 0 &&
            OverlayBlurGate.isSystemBlurEnabled(wm)

        val params = OverlayWindowTypes.createPresentationParams(hostContext).apply {
            OverlayWindowTypes.applyFullScreen(this)
            // ⚠️ 不能用"放行触摸"的实现（= 加 FLAG_NOT_TOUCHABLE）：实测 Oplus/ColorOS（本机 RMX8899，
            // Realme UI）的 WindowManager 会把「type=2038 + FLAG_NOT_TOUCHABLE + alpha>0.8」的窗口
            // 强制压到 alpha=0.8，表现就是整个轮盘（含容器）发灰半透明。系统原话：
            //   App com.slideindex.app has a system alert window (type = 2038) with FLAG_NOT_TOUCHABLE
            //   and LayoutParams.alpha = 1.00 > 0.80, setting alpha to 0.80 to let touches pass through
            //   (if this is isn't desirable, remove flag FLAG_NOT_TOUCHABLE).
            // 因此持续触发与松手触发统一为「可触摸 + NOT_TOUCH_MODAL + NOT_FOCUSABLE」，
            // 与「圆环启动器」(AppSwitcherOverlayController) 完全一致。
            // 安全性：手势的 ACTION_DOWN 已被边滑捕获窗口锁定，之后再出现的窗口不会抢走进行中的
            // 事件流，所以跟手高亮 / 松手确认仍由外部事件（onExternalMove / confirmContinuousRelease）
            // 驱动；同机上的圆环启动器用的就是同一套模型（它持续触发时同样不带 NOT_TOUCHABLE）。
            OverlayWindowTypes.applyPresentationInteractiveFlags(this)
            type = if (canHostOpaqueOverlay) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                OverlayWindowTypes.overlayWindowType(hostContext)
            }
            if (blurAvailable) {
                // 容器**以外**区域的跨窗口实时模糊；容器自身不透明，不受影响。
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                setBlurBehindRadius((blurDp * density).toInt().coerceIn(1, 80))
            }
            // 显式声明窗口不透明（默认值即 1f）。参照「圆环启动器」
            // （AppSwitcherOverlayController.show 里同样显式写 params.alpha = 1f），
            // 用于排除部分 OEM 读取 / 继承窗口 alpha、导致整窗（含容器）发透的可能。
            alpha = 1f
            // 便于真机排查：adb shell dumpsys window windows | findstr SlideIndexQuickWheel
            title = "SlideIndexQuickWheel"
        }
        Log.i(
            TAG,
            "show params: type=${params.type} opaqueHost=$canHostOpaqueOverlay " +
                "flags=0x${Integer.toHexString(params.flags)} alpha=${params.alpha} " +
                "externalTracking=$externalTracking " +
                "blurDp=$blurDp blurAvailable=$blurAvailable dim=${wheel.backdropDimPercent}",
        )

        return runCatching {
            wm.addView(view, params)
            composeView = view
            composeOwner = owner
            windowManager = wm
            true
        }.getOrElse {
            Log.w(TAG, "show: addView 失败", it)
            OverlayCompose.disposeComposeView(view)
            owner.destroy()
            false
        }
    }

    /** 持续触发：边滑会话喂入的移动坐标（跟手高亮）。 */
    fun onExternalMove(rawX: Float, rawY: Float) {
        val handler = externalMoveHandler ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            handler(rawX, rawY)
        } else {
            mainHandler.post { externalMoveHandler?.invoke(rawX, rawY) }
        }
    }

    /** 持续触发：松手确认，按落点执行动作或收起。 */
    fun confirmContinuousRelease(rawX: Float, rawY: Float) {
        val handler = externalUpHandler ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            handler(rawX, rawY)
        } else {
            mainHandler.post { externalUpHandler?.invoke(rawX, rawY) }
        }
    }

    /** 边滑手势会话结束：外部模式下落空则收起（已确认执行时会自行 dismiss）。 */
    fun onGestureSessionEnd() {
        if (externalTracking) dismiss()
    }

    /** 内容层回调：只收起**属于 [expected] 代**的那个窗口（迟到的回调不再误伤新窗口）。 */
    private fun dismissForSession(expected: Int) {
        if (expected != session) return
        dismiss()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // ⚠️ 必须比对"投递那一刻"的代次：无条件执行的话，这个延迟 dismiss 会把
            // 投递之后新呼出的窗口一起关掉。
            val expected = session
            mainHandler.post { if (expected == session) dismiss() }
            return
        }
        // 作废所有在途的延迟 dismiss（它们持有的是旧代次）。
        session++
        val view = composeView
        val owner = composeOwner
        val wm = windowManager
        composeView = null
        composeOwner = null
        windowManager = null
        activeExecutor = null
        activeSettings = AppSettings()
        activeSelfPackage = ""
        externalTracking = false
        externalMoveHandler = null
        externalUpHandler = null
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        // ⚠️ 走仓库统一的拆除路径：它等 ComposeView 真正 detach 之后再 destroy owner，
        // 避免 layout 阶段 "ViewTreeLifecycleOwner not found"
        //（composition 本身由 DisposeOnDetachedFromWindow 释放）。
        // 以前这里是 disposeComposeView + owner.destroy() 立即执行，与其它浮层不一致。
        OverlayCompose.teardownOverlayCompose(view, owner)
    }

    private fun executeSlot(slot: QuickWheelSlot, longPress: Boolean, anchorX: Float, anchorY: Float) {
        val rawAction = if (longPress) slot.longPressAction else slot.tapAction
        if (rawAction.type == GestureActionType.NONE) return
        val executor = activeExecutor ?: return
        val settings = activeSettings
        // 容器「打开方式」：单击 / 长按**各自**一份。显式选了全屏 / 小窗时——
        //   ① 先把**动作自带**的启动形态归零（容器是唯一来源，动作不该再自带形态）；
        //   ② 再套一份**只改「应用启动方式」档位**的设置快照执行本次动作。
        // "跟随"（INHERIT）时两样都不做 → 完全交给「应用与启动」，行为与历史版本一致。
        // 只对真的会打开东西的动作生效：返回 / 面板 / 执行命令等不受影响。
        val launchMode = if (longPress) slot.longPressLaunchMode else slot.tapLaunchMode
        val appliesOverride = launchMode != QuickWheelLaunchMode.INHERIT && rawAction.opensSomething()
        val action = if (appliesOverride) rawAction.withoutLaunchWindowMode() else rawAction
        val effectiveSettings = if (!appliesOverride) {
            settings
        } else {
            settings.withQuickWheelLaunchMode(
                mode = launchMode,
                targetSupportsFreeWindow = rawAction.freeWindowTargetPackage()
                    ?.let { pkg -> !TaskExclusions.shouldSkipFreeWindow(pkg, activeSelfPackage) }
                    ?: true,
            )
        }
        runCatching {
            executor.run(
                action,
                effectiveSettings,
                longPress,
                anchorX,
                anchorY,
                null,
            )
        }.onFailure { Log.w(TAG, "executeSlot 失败", it) }
    }

    /** 目标包名（拿不到就返回 null：不参与"桌面 / 系统界面 / 自身"的硬排除判定）。 */
    private fun GestureAction.freeWindowTargetPackage(): String? =
        (this as? GestureAction.LaunchApp)?.packageName

    private fun resolveWheel(settings: AppSettings, wheelId: String): QuickWheel? {
        val wheels = settings.launcher.quickWheels
        if (wheels.isEmpty()) return null
        if (wheelId.isBlank()) return wheels.firstOrNull()
        return wheels.firstOrNull { it.id == wheelId } ?: wheels.firstOrNull()
    }
}
