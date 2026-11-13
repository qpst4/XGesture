package com.slideindex.app.overlay.quickwheel

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import com.slideindex.app.ui.QuickWheelWheelRenderer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.overlay.layout.QuickWheelAdaptiveScreen
import com.slideindex.app.overlay.layout.QuickWheelHit
import com.slideindex.app.overlay.layout.QuickWheelHitKind
import com.slideindex.app.overlay.layout.QuickWheelLayout
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelOpenAnimation
import com.slideindex.app.overlay.layout.QuickWheelPlacedSlot
import com.slideindex.app.overlay.layout.QuickWheelStyleSpec
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.QuickWheelLongPressTrigger
import com.slideindex.app.settings.QuickWheelSlot
import com.slideindex.app.settings.QuickWheelTapTrigger
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.HapticHelper
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 划过容器的震动最小间隔：压在两个容器交界处时命中会来回切，不限制会变成"蜂鸣"。 */
private const val HAPTIC_TICK_MIN_INTERVAL_MS = 50L

/**
 * 轮盘浮层内容（驻留式），供「手势触发」与配置页「轮盘预览」共用。
 *
 * 位置与扇区：
 * - [adaptive] 非 null（真实手势呼出）：锚点 = 触发点，扇区（圆形）/ 网格基准角（矩形）由
 *   「所有容器必须落在屏幕内」自适应求解，**一级与二级各自求解**，所以轮盘只会出现在触发位置附近，
 *   且不会缺容器（右侧边触发 → 左半圆；右下角触发 → 左上 90°；矩形则贴对应的网格角）；
 * - [primarySectorMaskOverride] 非 null 时**一级圆形**改用动作绑定处手动选定的扇区（可能出屏，
 *   属用户显式选择、自己承担），二级与矩形基准角仍照常自适应求解；
 * - [adaptive] 为 null（配置页预览）：用调用方给的锚点 + 轮盘自身配置的扇区（扇区测试用）。
 *
 * 交互状态机（计时阈值 = [QuickWheel.longPressMs]，触摸与持续触发共用同一套）：
 * - 悬停在某容器满「长按时间」时：
 *   - 该容器配了长按动作且为**超时触发** → 立即执行长按动作；**即使它有二级子盘也不展开二级**
 *     （选了超时触发的容器进不去二级，这是该模式的固有代价，不额外提示）；
 *   - 否则：一级容器且有二级子盘 → 展开它的二级轮盘（此时不执行长按动作）；
 *   - 其余容器 → 松手触发，记为「已按满」。
 * - 松手时：
 *   - 二级已展开且松手在「被展开的那个一级容器」上 → 执行该一级的长按动作（未配置则直接收起）；
 *   - 二级已展开且松手在二级容器上 → 已按满则执行二级长按动作，否则执行二级单击；
 *   - 二级已展开且松手在其他位置 → 先收起二级（不退出轮盘）；
 *   - 未展开且松手在「已按满的同一容器」上 → 执行长按动作，否则执行单击。
 * - 二级展开后手指移到二级容器上，会以其为新的悬停目标重新开始计时；
 * - 二级展开后手指滑出「被展开的一级容器 + 它的二级环」这块**联合区域** → **立即**收起二级、
 *   回落一级选择模式。注意父容器与二级环之间的**空白属于该区域内部**（滑经它不会收起），
 *   真正收起只有两种情况：滑出区域之外，或滑到中心 / 其它一级容器上。
 *
 * 持续触发（[externalTracking] = true）：
 * - 浮层自身不接收触摸（窗口为 passthrough），由边滑手势会话喂入移动 / 抬起坐标；
 * - 与触摸模式复用同一套悬停 / 计时 / 松手逻辑。
 */
@Composable
fun QuickWheelOverlayContent(
    wheel: QuickWheel,
    anchorX: Float,
    anchorY: Float,
    screenWidthPx: Float,
    onExecuteTap: (QuickWheelSlot) -> Unit,
    onExecuteLongPress: (QuickWheelSlot) -> Unit,
    onDismiss: () -> Unit,
    externalTracking: Boolean = false,
    /**
     * 是否绘制中心大圆容器。
     *
     * 真实手势呼出时为 false：中心**全透明不显示**，但其动作仍可通过命中触发；
     * 配置页「轮盘预览」传 true，便于查看与试玩中心动作。
     */
    showCenter: Boolean = false,
    /**
     * 运行时自适应：非 null 时一级 / 二级的扇区（圆形）或网格基准角（矩形）由屏幕空间求解，
     * 保证所有容器都落在屏幕内且贴合触发位置；null（配置页预览）则用轮盘自身配置的扇区。
     */
    adaptive: QuickWheelAdaptiveScreen? = null,
    /**
     * 非 null 时**只**对二级做屏幕求解（配置页预览用：一级保持配置的扇区，二级与真机一致）。
     *
     * 真机呼出时 [adaptive] 已覆盖两级，无需再传。
     */
    secondaryAdaptive: QuickWheelAdaptiveScreen? = null,
    /**
     * 动作绑定处手动固定的**一级圆形**扇区（`null` = 自适应求解）。
     *
     * 只覆盖一级：二级的扇区 / 矩形基准角仍按触发位置自适应求解 —— 否则"手动固定一级扇区"
     * 会把二级一起拖下水（非自适应时二级恒为整圆，从角落展开会大半在屏外）。
     * 动作页"扇区环一个都不选"就是 `null`（= 程序自动）。
     */
    primarySectorMaskOverride: Int? = null,
    /**
     * 容器**以外**区域的黑色遮罩浓度（0f = 完全透出原屏幕内容）。
     *
     * 画在轮盘**下层**：容器自身不透明，所以容器所在位置看不到屏幕底部的内容。
     */
    backdropDimAlpha: Float = 0f,
    /** Shell 命令表：HUD 的动作名称按它回查用户自定义名。 */
    shellCommands: List<ShellCommand> = emptyList(),
    /**
     * 震动反馈用的设置快照；`null` = 不震（测试 / 不需要震动反馈的宿主）。
     *
     * 复用全局那套开关（总开关 + 轻/中/重 + 「槽位选择 / 手势触发 / 确认启动」三个子开关），
     * 与圆环启动器、底角轮盘完全一致；轮盘不单独加设置项。
     */
    hapticSettings: AppSettings? = null,
    onExternalHandlers: (move: (Float, Float) -> Unit, up: (Float, Float) -> Unit) -> Unit = { _, _ -> },
) {
    val densityFloat = LocalDensity.current.density
    val hapticView = LocalView.current
    // 上一次"划过容器"震动的时刻（uptimeMillis），用于限制交界处的高频抖动。
    var lastHapticTickAt by remember { mutableLongStateOf(0L) }

    var expandedPrimary by remember(wheel.id) { mutableIntStateOf(-1) }
    // 当前悬停（按下 / 移动到）的容器；变化即 [hoverToken] 自增，重启「长按时间」计时。
    var hoverHit by remember { mutableStateOf(QuickWheelHit.NONE) }
    var hoverToken by remember { mutableIntStateOf(0) }
    // 「已按满长按时间」的容器（主要用于松手触发型）：松手落在它上面才执行长按动作。
    var armedHit by remember { mutableStateOf(QuickWheelHit.NONE) }
    var lastRawX by remember { mutableFloatStateOf(0f) }
    var lastRawY by remember { mutableFloatStateOf(0f) }
    // 持续触发没有独立「按下」事件：首次移动视为按下。
    var externalPressed by remember { mutableStateOf(false) }

    val expandedSlotsForRender = wheel.slots.getOrNull(expandedPrimary)?.subSlots.orEmpty()

    val layout: QuickWheelLayout = remember(
        wheel,
        anchorX,
        anchorY,
        expandedPrimary,
        densityFloat,
        adaptive,
        secondaryAdaptive,
        primarySectorMaskOverride,
    ) {
        QuickWheelLayoutEngine.buildLayout(
            primaryCount = wheel.slots.size,
            secondaryCount = expandedSlotsForRender.size,
            expandedPrimaryIndex = expandedPrimary,
            primaryShape = wheel.primaryShape,
            secondaryShape = wheel.secondaryShape,
            primaryStyle = wheel.primaryStyle,
            secondaryStyle = wheel.secondaryStyle,
            anchorX = anchorX,
            anchorY = anchorY,
            density = densityFloat,
            screenWidthPx = screenWidthPx,
            sectorMask = wheel.sectorMask,
            primarySectorMaskOverride = primarySectorMaskOverride,
            adaptive = adaptive,
            secondaryAdaptive = secondaryAdaptive,
        )
    }
    // 自适应求解结果（诊断用）：便于确认"贴着触发点最终选了哪个扇区 / 哪个网格基准角"。
    LaunchedEffect(layout.sectorMask, layout.rectCorner, expandedPrimary) {
        if (adaptive != null) {
            Log.i(
                "QuickWheelOverlay",
                "自适应布局 anchor=(${anchorX.roundToInt()}, ${anchorY.roundToInt()}) " +
                    "mask=${layout.sectorMask} corner=${layout.rectCorner} expanded=$expandedPrimary",
            )
        }
    }

    // 外部回调是长生命周期闭包，必须读取最新的布局与展开态。
    val layoutState = rememberUpdatedState(layout)

    fun expandedSlotsNow(): List<QuickWheelSlot> =
        wheel.slots.getOrNull(expandedPrimary)?.subSlots.orEmpty()

    fun slotOf(hit: QuickWheelHit): QuickWheelSlot? = when (hit.kind) {
        QuickWheelHitKind.CENTER -> wheel.centerSlot
        QuickWheelHitKind.PRIMARY -> wheel.slots.getOrNull(hit.index)
        QuickWheelHitKind.SECONDARY -> expandedSlotsNow().getOrNull(hit.index)
        QuickWheelHitKind.NONE -> null
    }

    /** 「划过容器」的震动：只对配了动作的一/二级容器，中心与空位不震（中心真实呼出时不可见）。 */
    fun hapticSlotEnter(hit: QuickWheelHit) {
        val settings = hapticSettings ?: return
        if (hit.kind != QuickWheelHitKind.PRIMARY && hit.kind != QuickWheelHitKind.SECONDARY) return
        val slot = slotOf(hit) ?: return
        if (!slot.isConfigured) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHapticTickAt < HAPTIC_TICK_MIN_INTERVAL_MS) return
        lastHapticTickAt = now
        HapticHelper.appTick(hapticView, settings)
    }

    /** 「松手执行」的震动：与圆环启动器一致，确认音效之外再给一次触觉确认。 */
    fun hapticConfirm() {
        val settings = hapticSettings ?: return
        HapticHelper.confirmLaunch(hapticView, settings)
    }

    fun hitAt(x: Float, y: Float): QuickWheelHit = QuickWheelLayoutEngine.hitTestWhileExpanded(
        layout = layoutState.value,
        shape = wheel.primaryShape,
        rawX = x,
        rawY = y,
        // 与收起判定用同一个余量，避免"高亮 / 松手"与"收起"两套口径。
        slackPx = QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP * densityFloat,
    )

    fun dismissOrCollapse() {
        if (expandedPrimary >= 0) expandedPrimary = -1 else onDismiss()
    }

    fun plainRelease(upHit: QuickWheelHit) {
        val slot = slotOf(upHit)
        if (slot != null && slot.isConfigured) {
            hapticConfirm()
            onExecuteTap(slot)
            onDismiss()
        } else {
            dismissOrCollapse()
        }
    }

    /**
     * 悬停更新。
     *
     * ⚠️ **收起判定必须每次移动都跑**，不能只在"命中目标变化"时跑：手指在"无主区"里继续往外滑时
     * 命中会一直是 [QuickWheelHit.NONE] 不再变化，早期写法在 `hit == hoverHit` 处直接早退，
     * 于是"明明已经滑出二级区域，二级却不收起"（滑出去那一下命中没变 = 判定被整段跳过）。
     * 而"长按计时"仍只在命中目标**真的变化**时重启（同一容器内微动不重置计时）。
     */
    fun setHover(hit: QuickWheelHit) {
        if (hit != hoverHit) {
            hoverHit = hit
            armedHit = QuickWheelHit.NONE
            hoverToken++
            // 「划过容器」的触觉：只在命中目标真的变化时震，同一容器内微动不重复。
            hapticSlotEnter(hit)
        }
        // 二级展开时是否收起，交给引擎的纯判定（便于单测）：
        // 命中父容器 / 任意二级容器 → 保持；其余命中 → 只有真正离开「父容器 + 全部二级容器」
        // 的联合区域（凸包 + 余量）才收起。
        if (expandedPrimary >= 0) {
            val collapse = QuickWheelLayoutEngine.shouldCollapseExpandedSecondary(
                layout = layoutState.value,
                hit = hit,
                rawX = lastRawX,
                rawY = lastRawY,
                slackPx = QuickWheelLayoutEngine.EXPANDED_REGION_SLACK_DP * densityFloat,
            )
            if (collapse) expandedPrimary = -1
        }
    }

    fun clearHover() {
        hoverHit = QuickWheelHit.NONE
        armedHit = QuickWheelHit.NONE
        hoverToken++
    }

    /** 按下（或持续触发首次进入）：记录悬停，并处理「触摸即触发」的单击。@return true 表示已执行并收起。 */
    fun pressAt(x: Float, y: Float): Boolean {
        lastRawX = x
        lastRawY = y
        val hit = hitAt(x, y)
        hoverHit = hit
        armedHit = QuickWheelHit.NONE
        hoverToken++
        val slot = slotOf(hit)
        if (slot != null && slot.isConfigured &&
            slot.tapTrigger == QuickWheelTapTrigger.ON_TOUCH
        ) {
            hapticConfirm()
            onExecuteTap(slot)
            onDismiss()
            return true
        }
        return false
    }

    fun moveTo(x: Float, y: Float) {
        lastRawX = x
        lastRawY = y
        setHover(hitAt(x, y))
    }

    /** 悬停满「长按时间」后的判定：超时长按动作 / 展开二级 / 记为已按满。 */
    fun onHoverTimeout(hit: QuickWheelHit) {
        val slot = slotOf(hit) ?: return
        if (!slot.isConfigured) return
        // ⚠️ 「超时触发」优先于「展开二级」：用户显式选了超时触发，语义就是"按满即执行长按动作"，
        // 此时该容器的二级不再展开（二级自然进不去）——这是选择该模式时接受的代价，不额外提示。
        // 容器没配长按动作时没有可执行的东西，仍按老规则展开二级。
        val timeoutLongPress = slot.longPressAction.type != GestureActionType.NONE &&
            slot.longPressTrigger == QuickWheelLongPressTrigger.ON_TIMEOUT
        val willExpand = !timeoutLongPress &&
            hit.kind == QuickWheelHitKind.PRIMARY &&
            slot.subSlots.isNotEmpty() &&
            expandedPrimary != hit.index
        // 「按满」时只有真的会发生点什么才震：没配动作、也没有二级的容器按满了什么都不会发生，
        // 给了震动就等于承诺了一个不存在的动作。
        val willArm = !timeoutLongPress && !willExpand &&
            slot.longPressAction.type != GestureActionType.NONE &&
            slot.longPressTrigger == QuickWheelLongPressTrigger.ON_RELEASE
        if (timeoutLongPress || willExpand || willArm) {
            // 「长按振动」= 按住不动被判为长按：与设置页的语义一致（不是「长滑距离」那条）。
            hapticSettings?.let { HapticHelper.longPress(hapticView, it) }
        }
        when {
            timeoutLongPress -> {
                onExecuteLongPress(slot)
                onDismiss()
            }

            hit.kind == QuickWheelHitKind.PRIMARY &&
                slot.subSlots.isNotEmpty() &&
                expandedPrimary != hit.index -> {
                expandedPrimary = hit.index
                // 展开会改变布局：按当前手指位置重算悬停目标（若已移到二级上则重新开始计时）。
                setHover(hitAt(lastRawX, lastRawY))
            }

            else -> armedHit = hit
        }
    }

    fun handleRelease(up: QuickWheelHit, armed: QuickWheelHit) {
        // 二级已展开
        if (expandedPrimary >= 0) {
            val expandedIndex = expandedPrimary
            when {
                // 在「被展开的那个一级容器」上松手 → 执行一级长按动作（未配置则什么都不做并收起）。
                up.kind == QuickWheelHitKind.PRIMARY && up.index == expandedIndex -> {
                    val slot = wheel.slots.getOrNull(expandedIndex)
                    if (slot != null && slot.longPressAction.type != GestureActionType.NONE) {
                        hapticConfirm()
                        onExecuteLongPress(slot)
                    }
                    onDismiss()
                }

                up.kind == QuickWheelHitKind.SECONDARY -> {
                    val slot = expandedSlotsNow().getOrNull(up.index)
                    if (slot != null && slot.isConfigured) {
                        val armedHere =
                            armed.kind == QuickWheelHitKind.SECONDARY && armed.index == up.index
                        if (armedHere &&
                            slot.longPressTrigger == QuickWheelLongPressTrigger.ON_RELEASE &&
                            slot.longPressAction.type != GestureActionType.NONE
                        ) {
                            hapticConfirm()
                            onExecuteLongPress(slot)
                        } else {
                            hapticConfirm()
                            onExecuteTap(slot)
                        }
                        onDismiss()
                    } else {
                        expandedPrimary = -1
                    }
                }

                // 其他位置松手：先收起二级，轮盘保持打开。
                else -> expandedPrimary = -1
            }
            return
        }

        // 未展开：松手在「已按满的同一容器」上执行长按动作，否则单击。
        val upSlot = slotOf(up)
        if (upSlot != null && upSlot.isConfigured &&
            armed == up &&
            upSlot.longPressTrigger == QuickWheelLongPressTrigger.ON_RELEASE &&
            upSlot.longPressAction.type != GestureActionType.NONE
        ) {
            hapticConfirm()
            onExecuteLongPress(upSlot)
            onDismiss()
            return
        }
        plainRelease(up)
    }

    fun releaseAt(x: Float, y: Float) {
        val up = hitAt(x, y)
        val armed = armedHit
        clearHover()
        handleRelease(up, armed)
    }

    // ── 外部（持续触发）输入 ──────────────────────────────
    fun handleExternalMove(rawX: Float, rawY: Float) {
        if (externalPressed) {
            moveTo(rawX, rawY)
        } else {
            // 持续触发没有独立「按下」事件：首次移动视为按下。
            externalPressed = true
            pressAt(rawX, rawY)
        }
    }

    fun handleExternalUp(rawX: Float, rawY: Float) {
        externalPressed = false
        releaseAt(rawX, rawY)
    }

    DisposableEffect(externalTracking) {
        if (externalTracking) {
            onExternalHandlers(::handleExternalMove, ::handleExternalUp)
        }
        onDispose {
            if (externalTracking) {
                onExternalHandlers({ _, _ -> }, { _, _ -> })
            }
        }
    }

    // 悬停满「长按时间」→ 展开二级 / 执行超时长按动作 / 记为已按满。
    // hoverToken 每次悬停目标变化都会自增，从而取消上一轮计时并重新开始。
    LaunchedEffect(hoverToken) {
        if (hoverToken == 0) return@LaunchedEffect
        val hit = hoverHit
        if (hit.kind == QuickWheelHitKind.NONE) return@LaunchedEffect
        delay(wheel.longPressMs.toLong())
        onHoverTimeout(hit)
    }

    // 呼出动画：从轮盘锚点向外铺开。
    // ⚠️ 进度只在**布局 / 绘制阶段**读（渲染器拿的是 lambda），动画期间不重组。
    // ⚠️ 用 key 重建 Animatable、初值直接给 0/1，**不能**在 LaunchedEffect 里 snapTo(0)：
    // LaunchedEffect 要等这一帧提交之后才跑，先按"最终位置"画一两帧再跳回起点 = 闪一下 / 跳动
    // （配置页页内预览浮出时尤其明显）。
    val appear = remember(wheel.id, wheel.openAnimation, wheel.animationSpeedPercent) {
        Animatable(if (wheel.openAnimation == QuickWheelOpenAnimation.NONE) 1f else 0f)
    }
    LaunchedEffect(appear) {
        if (appear.value < 1f) {
            appear.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = QuickWheelLayoutEngine.openAnimationDurationMs(
                        wheel.animationSpeedPercent,
                    ),
                    easing = LinearOutSlowInEasing,
                ),
            )
        }
    }
    // 二级展开动画：与一级**共用同一个速度参数**（不单独设参数），只在"长按一级容器把它展开"
    // 那一刻播一次；起点 = 那个一级容器的中心（二级环围着它铺开）。
    //
    // ⚠️ 必须**用 key 重建 Animatable、初值直接给 0**，不能在 LaunchedEffect 里 snapTo(0)：
    // 展开那一帧的 layout 已经包含二级容器，而 LaunchedEffect 要等这帧提交之后才跑 ——
    // 于是会先按"最终位置 + 完整大小"画一两帧，再跳回父容器中心缩到 0.35 重播，表现为**跳动**。
    val appearSecondary = remember(
        expandedPrimary,
        wheel.openAnimation,
        wheel.animationSpeedPercent,
    ) {
        Animatable(
            if (expandedPrimary >= 0 && wheel.openAnimation != QuickWheelOpenAnimation.NONE) {
                0f
            } else {
                1f
            },
        )
    }
    LaunchedEffect(appearSecondary) {
        if (appearSecondary.value < 1f) {
            appearSecondary.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = QuickWheelLayoutEngine.openAnimationDurationMs(
                        wheel.animationSpeedPercent,
                    ),
                    easing = LinearOutSlowInEasing,
                ),
            )
        }
    }

    // 只有「已按满」（用户明确停在这个容器上、长按计时已走完）才立即到位。
    // ⚠️ 不能用 hover 作为条件：呼出瞬间手指本来就压在锚点 / 中心容器上，hover 必然立刻从 NONE
    // 变成容器，那样动画会在第一帧就被 snap 掉 —— 真机呼出"看不到动画"的根因。
    // 拖到别的容器时的视觉错位实践中可忽略：手指在锚点附近，附近容器本就最接近就位位置。
    LaunchedEffect(armedHit) {
        if (appear.value < 1f && armedHit.kind != QuickWheelHitKind.NONE) {
            appear.snapTo(1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 容器以外区域的黑纱（浓度可调）。画在轮盘下层，且容器自身不透明，
        // 所以"容器所在位置看不到屏幕底部内容"始终成立。
        if (backdropDimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backdropDimAlpha.coerceIn(0f, 1f))),
            )
        }
        val touchModifier = if (externalTracking) {
            Modifier
        } else {
            // key 只依赖 wheel.id：展开二级会改变布局，若把 layout / expandedPrimary 也当 key，
            // 手势会在展开瞬间被重启并丢失当前这一次按压。
            Modifier.pointerInput(wheel.id) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (pressAt(down.position.x, down.position.y)) {
                            return@awaitPointerEventScope
                        }

                        var upChange: PointerInputChange? = null
                        while (upChange == null) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                upChange = change
                            } else {
                                moveTo(change.position.x, change.position.y)
                            }
                        }
                        if (upChange != null) {
                            releaseAt(upChange.position.x, upChange.position.y)
                        } else {
                            clearHover()
                        }
                    }
                }
            }
        }

        // 与参考实现一致：不加深色遮罩，轮盘直接悬浮在页面之上。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(touchModifier),
        )

        // 与配置页预览 / 编辑层共用同一个渲染器，保证三处轮盘外形完全一致。
        QuickWheelWheelRenderer(
            layout = layout,
            wheel = wheel,
            // 呼出动画：从锚点向外铺开（lambda 读取，动画期间不重组）。
            appearProgress = { appear.value },
            // 二级展开动画：长按一级容器展开它时，二级从该容器中心向外铺开。
            appearSecondaryProgress = { appearSecondary.value },
            highlightCenter = hoverHit.kind == QuickWheelHitKind.CENTER,
            highlightPrimaryIndex = if (hoverHit.kind == QuickWheelHitKind.PRIMARY) {
                hoverHit.index
            } else {
                -1
            },
            highlightSecondaryIndex = if (hoverHit.kind == QuickWheelHitKind.SECONDARY) {
                hoverHit.index
            } else {
                -1
            },
            // 有二级容器的容器右上角红点（与配置页预览 / 功能设置编辑层保持一致）。
            showSecondaryDots = true,
            // 圆形中心容器：真实呼出时全透明（动作仍可命中）；预览时显示。
            showCenter = showCenter,
        )

        // 动作提示条：手指会遮住容器，这里固定显示"此刻松手会发生什么"（方案 C）。
        // 位置**固定贴屏幕上部**（不再跟随手指半屏互换）。
        val hudHit = if (armedHit.kind != QuickWheelHitKind.NONE) armedHit else hoverHit
        val hudSlot = slotOf(hudHit)
        if (hudSlot != null) {
            // 二级展开时命中父容器 → 松手执行的是父容器长按（与 handleRelease 一致）。
            val isParentWhileExpanded = expandedPrimary >= 0 &&
                hudHit.kind == QuickWheelHitKind.PRIMARY &&
                hudHit.index == expandedPrimary
            QuickWheelActionHud(
                slot = hudSlot,
                mode = quickWheelHudModeFor(
                    hitIsParentWhileExpanded = isParentWhileExpanded,
                    armed = armedHit.kind != QuickWheelHitKind.NONE,
                ),
                longPressMs = wheel.longPressMs,
                hasSubSlots = hudHit.kind == QuickWheelHitKind.PRIMARY &&
                    wheel.slots.getOrNull(hudHit.index)?.subSlots?.isNotEmpty() == true,
                // 与长按计时同源：悬停目标一变就重启进度。
                progressKey = hoverToken,
                modifier = Modifier.align(Alignment.TopCenter),
                shellCommands = shellCommands,
            )
        }
    }
}

/**
 * 把 [QuickWheelPlacedSlot] 摆到浮层坐标系里（px → IntOffset，布局阶段生效）。
 *
 * @param appearProgress 展开进度：0 = 挤在锚点且缩到最小，1 = 完全就位。
 *   传 **lambda**（不是 Float）：动画期间在布局 / 绘制阶段读取，不触发重组。
 * @param appearOriginX 展开起点（px，轮盘锚点）；null = 用容器自己的位置（→ 只做缩放，中心大圆用）。
 */
@Composable
fun PlacedContainer(
    placed: QuickWheelPlacedSlot?,
    slot: QuickWheelSlot,
    style: QuickWheelStyleSpec,
    highlighted: Boolean = false,
    appearProgress: () -> Float = { 1f },
    appearOriginX: Float? = null,
    appearOriginY: Float? = null,
    modifier: Modifier = Modifier,
    showPlaceholders: Boolean = false,
) {
    if (placed == null) return
    // 只有「用户主动留出的空位」在真实呼出时完全透明；其余容器（含未设动作的）照常绘制。
    if (slot.placeholder && !showPlaceholders) return
    val originX = appearOriginX ?: placed.centerX
    val originY = appearOriginY ?: placed.centerY
    // ⚠️ 顺序很重要：基础摆位必须在调用方手势修饰符**之前**（外层）。
    // 写成 `modifier.offset { }` 会让命中测试区域留在原点，出现「格子看得见但点不动」。
    Box(
        modifier = Modifier
            .offset {
                // ⚠️ 展开进度在**布局阶段**读取（lambda 里读），动画期间不触发重组。
                val p = appearProgress().coerceIn(0f, 1f)
                val cx = originX + (placed.centerX - originX) * p
                val cy = originY + (placed.centerY - originY) * p
                IntOffset(
                    (cx - placed.sizePx / 2f).roundToInt(),
                    (cy - placed.sizePx / 2f).roundToInt(),
                )
            }
            .then(modifier),
    ) {
        QuickWheelContainer(
            slot = slot,
            sizePx = placed.sizePx,
            cornerPx = placed.cornerPx,
            style = style,
            highlighted = highlighted,
            scale = if (highlighted) 1.08f else 1f,
            // 展开缩放（0.35 → 1）用 graphicsLayer：绘制阶段生效，动画期间不重排；
            // 与上面 sizePx 的"高亮放大"（1.08）互不干扰，相乘生效。
            modifier = Modifier.graphicsLayer {
                val s = 0.35f + 0.65f * appearProgress().coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
            },
        )
    }
}
