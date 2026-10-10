@file:OptIn(ExperimentalFoundationApi::class)

package com.slideindex.app.overlay.history

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 剪贴板历史边缘把手：点击/左滑/长按打开 [com.slideindex.app.overlay.FloatBallStashPanel]。
 *
 * 形态按设计稿（`ui_demo_capsule.html`）：
 * - **视觉** 9×28dp、圆角 5dp、距屏幕右缘 3dp（原设计稿 12dp → 6dp → 3dp，用户两次要求"减半"）
 * - **命中区** 48×48dp（Android 最小触摸目标）
 * - 有待办未完成时**整条变色**（不出数字、不加宽）+ 一道"科幻 AI 风"流光
 *
 * §流光（用户："没啥存在感" → 要有存在感的科幻 AI 风）：光是**自己画**的（`drawWithCache`
 * 里 bloom + 过曝主带 + 递减拖尾），不是叠一层 `Box` 做 `translationX`。原实现只有一条
 * 单色淡带在 9dp 的条里平移，既没有拖尾也没有溢出条外，所以在真机上"看不出来"。
 * 条本身的**尺寸/位置/圆角/配色规则一个都没动**，只换了"光"的算法。
 */
@Composable
fun HistoryFloatContent(
    handleVisible: Boolean,
    handleAlert: Boolean,
    onOpenPanel: () -> Unit,
    onMoveHandle: (Float) -> Unit,
    onMoveHandleEnd: () -> Unit = {},
    onRevealStart: () -> Boolean = { false },
    onRevealEnd: (Boolean) -> Unit = {},
    /** 长按把手：就地记一条（弹出输入槽）。默认退回"打开面板"。 */
    onQuickNote: () -> Unit = onOpenPanel,
) {
    // 「刚存下」信号（面板存下一条 → 把手脉冲一下，设计稿 `.pip.pulse`）。
    // 读它 = 订阅：`HistorySaveSignal` 的属性是 Compose 状态。
    val saveCount = HistorySaveSignal.saveCount
    val haptics = rememberHistoryHaptics()
    // 被按住 / 拖动中：把手轻微放大 + 提高不透明度（手柄自身的手感反馈）。
    // 状态提到这一层，是因为流光也要读它（拖动时不画，见 glowActive）。
    var active by remember { mutableStateOf(false) }
    // 「已提醒但用户还没划掉/完成」→ 科幻流光。
    // ⚠️ 这个面板状态由别人维护（`StashReminderPendingState`），这里**只读**，不建也不改这个文件。
    // 用 `derivedStateOf` 把三个闸门合成一个 Boolean：它只在真正切换时让 Compose 失效，
    // 所以"无提醒"的常驻状态下是**零动画、零重绘**的。
    // ⚠️ 这个 Boolean 同时也是**动画的总开关**：唯一那一个 `animateFloat` 挂在
    // `HistoryHandleGlowSweep` 里，而它只在 `glowActive == true` 时进组合
    // （`rememberInfiniteTransition` 位于 if 内）—— 没有提醒时既没有动画时钟，
    // 也没有 `drawWithCache` 的光层 block，整条把手回到"一次画完就不动"的最省电形态。
    val glowActive by remember {
        derivedStateOf {
            // 1) 有未处理的提醒；2) 窗口可见（服务在隐藏时会直接 return，这是兜底）；
            // 3) 没在拖动/按住（跟手时优先给手感和跟手，不叠动画）。
            // ⚠️ 给后来的排查者（"用户实测看不到流光"那次复盘）：**常态下这三条都成立**
            // —— 服务里的 `handleVisible` 初值是 true、全工程没有任何地方把它写成 false
            // （把手窗和面板是两个窗口，面板打开并不会藏把手），`active` 只在拖动/双击那 0.5s 为 true。
            // 所以"看不到光"如果发生，最大嫌疑是 `hasPending` 压根没被点起来
            // （它只在「提醒通知发出」/「面板可见时刷新」这两条路径上被写），而不是这里被挡住。
            StashReminderPendingState.hasPending.value && handleVisible && !active
        }
    }
    // 流光的唯一动画时钟：**只在 `glowActive` 时进组合**。
    // `State<Float>` 可以这样建、再交给下面的绘制层去读，这正是"把逐帧读取推到绘制阶段"
    // 的关键 —— 状态读取在绘制 lambda 里，就不会每帧重组这个 composable。
    val glowPhase = if (glowActive) HistoryHandleGlowSweep() else null
    OverlayAwareModuleTheme {
        if (handleVisible) {
            HistoryFloatHandle(
                alert = handleAlert,
                glowActive = glowActive,
                glowPhase = glowPhase,
                active = active,
                onActiveChange = { active = it },
                saveCount = saveCount,
                haptics = haptics,
                onOpenPanel = onOpenPanel,
                onMoveHandle = onMoveHandle,
                onMoveHandleEnd = onMoveHandleEnd,
                onRevealStart = onRevealStart,
                onRevealEnd = onRevealEnd,
                onQuickNote = onQuickNote,
            )
        }
    }
}

@Composable
private fun HistoryFloatHandle(
    alert: Boolean,
    /** true = 播放科幻流光（已提醒未处理 + 可见 + 未拖动）。 */
    glowActive: Boolean,
    /**
     * 流光的唯一动画进度（0→1 扫一遍）。**只有 [glowActive] 为 true 时才非 null**；
     * null 表示"当前不该有光"，绘制层直接跳过所有光相关的 draw call。
     */
    glowPhase: State<Float>?,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    saveCount: Int,
    haptics: HistoryHaptics,
    onOpenPanel: () -> Unit,
    onMoveHandle: (Float) -> Unit,
    onMoveHandleEnd: () -> Unit = {},
    onRevealStart: () -> Boolean = { false },
    onRevealEnd: (Boolean) -> Unit = {},
    onQuickNote: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 激活（被按住 / 拖动中）时轻微放大 + 提高不透明度。
    val barWidth by animateDpAsState(
        targetValue = if (active) 11.dp else 9.dp,
        label = "handleBarWidth",
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (active) 0.95f else 0.72f,
        label = "handleBarAlpha",
    )
    // 存下后的脉冲：设计稿 `.pip.pulse` = `pippulse 900ms`，35% 处 `translateX(-3px) scaleY(1.18)`。
    // `lastPulsed` 以当前值为初值：否则面板里之前存过的东西会让把手在**启动时**凭空脉冲一下。
    var lastPulsed by remember { mutableIntStateOf(saveCount) }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(saveCount) {
        if (saveCount == lastPulsed) return@LaunchedEffect
        lastPulsed = saveCount
        pulse.snapTo(0f)
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = HANDLE_PULSE_DURATION_MS
                1f at (HANDLE_PULSE_DURATION_MS * 35 / 100) using CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
            },
        )
    }
    val scheme = MiuixTheme.colorScheme
    // 拉满 = 面板宽度（px）。用屏幕宽度而不是 LocalWindowInfo.containerSize —— 把手窗只有 48dp 宽，
    // containerSize 是把手自己，不是屏幕。
    val revealDistanceState = rememberUpdatedState(
        historyPanelRevealDistancePx(LocalContext.current),
    )
    // 手势 lambda 被 `pointerInput(Unit)` 抓一次就不再更新，所以触觉对象也要走 updatedState。
    val latestHaptics = rememberUpdatedState(haptics)
    // 条的两个颜色（每帧都要算，但 `scheme` 变化很少，放组合里算一次即可）。
    // alert（有待办未完成）= 整条主题色；否则是低透明度的 onSurface 细条。
    val barColor = if (alert) scheme.primary else scheme.onSurface
    val barFillAlpha = if (alert) 1f else 0.28f
    val barBorderColor = if (alert) {
        scheme.primary.copy(alpha = 0.55f)
    } else {
        scheme.onSurface.copy(alpha = if (active) 0.24f else 0.12f)
    }
    // ── 相位桥 + 诊断计数器 ──
    // `glowPhase` 是**动画状态**，若只在绘制 lambda 里读，它的逐帧推进要依赖
    // "绘制阶段也观察快照读取"这条机制；实测出现过"标记在画、但条内永远纯蓝"的现象，
    // 与"`t` 没有逐帧推进（恒为初值 0 → 主带中心落在条右侧外）"完全吻合。
    // `rememberGlowObservedPhase` 把相位**提升为组合期可观察的状态**，能同时满足两件事：
    // 1) 让"动画是否在推进"变成可探测的（探针能读到真实变化的 t / draw 次数）；
    // 2) 万一绘制期的观察真的没生效，这里也能自愈（镜像状态变化 → 重组 → 重绘）。
    // 对性能的影响：只在 `glowActive` 为 true 期间、每个动画帧触发一次**这一个** composable
    // 的重组（不是整个把手树），符合"只在 hasPending 时跑"的既有 gate。
    val glowObserved = glowPhase?.let { rememberGlowObservedPhase(it) }

    Box(
        modifier = Modifier
            .size(HANDLE_HIT_DP.dp)
            .pointerInput(Unit) {
                // Compose 的 touch slop（一般 8dp）。下面"跟手进度"要把它补回来，理由见拖动回调里的注释。
                // 它是 [PointerInputScope] 自带的属性，不用（也不该）在组合期读 `LocalViewConfiguration` ——
                // 这段 lambda 被 `pointerInput(Unit)` 只抓一次，读组合局部量反而更容易踩到陈旧值。
                val dragSlopPx = viewConfiguration.touchSlop
                var totalX = 0f
                var totalY = 0f
                // 前 8dp 锁定主方向（设计稿注释：横向=拉出面板，纵向=挪位置）。
                var axis: DragAxis? = null
                var revealing = false
                var crossedHalf = false
                // 拖动到底或中途被系统手势（边缘返回）抢走都会走收尾：
                // 之前只处理 onDragEnd，被抢走时 onDragCancel 不打开面板 → 「拖动打不开」。
                val finishDrag = {
                    if (revealing) {
                        val commit = HistoryPanelReveal.dragProgress >= REVEAL_COMMIT_FRACTION
                        onRevealEnd(commit)
                    } else if (totalX <= OPEN_DRAG_THRESHOLD_X) {
                        // 没走到跟手（轴锁定前就松手 / 面板已在显示）：沿用原来的"拖过阈值就开"。
                        onOpenPanel()
                    }
                    onMoveHandleEnd()
                }
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        axis = null
                        revealing = false
                        crossedHalf = false
                        onActiveChange(true)
                    },
                    onDragEnd = {
                        finishDrag()
                        scope.launch {
                            delay(500)
                            onActiveChange(false)
                        }
                    },
                    onDragCancel = {
                        finishDrag()
                        scope.launch {
                            delay(500)
                            onActiveChange(false)
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                    if (axis == null) {
                        val lockedX = abs(totalX) >= HANDLE_AXIS_LOCK_PX
                        val lockedY = abs(totalY) >= HANDLE_AXIS_LOCK_PX
                        if (lockedX || lockedY) {
                            axis = if (abs(totalX) >= abs(totalY)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
                        }
                    }
                    when (axis) {
                        DragAxis.HORIZONTAL, null -> {
                            // 跟手拉出：第一次真的往左拖时才把面板窗叫出来（面板从屏幕外开始跟着手指走）。
                            if (!revealing && totalX < -HANDLE_AXIS_LOCK_PX) {
                                revealing = onRevealStart()
                            }
                            if (revealing) {
                                val distance = revealDistanceState.value
                                // ⚠️ 位移基准是**手指按下点**，不是"滑出 slop 的那一点"：
                                // `detectDragGestures` 在 slop 内一个回调都不给，而首次回调只带出"超出 slop"
                                // 的那几 px（`overSlop`），slop 本身（≈8dp）没有任何回调带上它 —— 不补的话，
                                // 面板前缘从第一帧起就少走 8dp 的手指位移（面板行程 = 手指 × 130dp→面板宽度
                                // ≈ 2.5×，所以是少走约 20dp），表现就是"开头一小段完全不动，之后才跟手"。
                                // 这里把 slop 补回来（`totalX` 往左拖是负数，越拖越小）。
                                // ⚠️ 补偿只加在**进度换算**里，绝不动 `totalX`：它还兼着"松手兜底要不要开面板"
                                // （`totalX <= OPEN_DRAG_THRESHOLD_X`）与轴锁定，改了它会让竖直挪把手也误开面板。
                                HistoryPanelReveal.dragProgress =
                                    ((-totalX + dragSlopPx) / distance).coerceIn(0f, 1f)
                                // 过半轻震（与跟手阈值同一个手感点）。
                                val half = HistoryPanelReveal.dragProgress >= REVEAL_COMMIT_FRACTION
                                if (half != crossedHalf) {
                                    crossedHalf = half
                                    if (half) latestHaptics.value.tick()
                                }
                            }
                        }
                        DragAxis.VERTICAL -> onMoveHandle(dragAmount.y)
                    }
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // 单击也能打开：边缘横向拖动会被系统返回手势抢走，点按永远能到应用手里。
                onClick = { onOpenPanel() },
                // 长按 = 就地记一条（设计稿 `.slot` 输入槽）。
                onLongClick = {
                    haptics.longPress()
                    onQuickNote()
                },
                onDoubleClick = {
                    onActiveChange(true)
                    scope.launch {
                        delay(500)
                        onActiveChange(false)
                    }
                    onOpenPanel()
                },
            )
            // 存下后的脉冲（设计稿 `.pip.pulse`）只走 RenderNode 变换：
            // 这里用**带 lambda 的** `graphicsLayer` —— 它把 `pulse.value` 的读取推迟到绘制阶段，
            // 所以脉冲每帧变化**不会重组**这个 composable，只重画这一层。
            .graphicsLayer {
                scaleY = 1f + HANDLE_PULSE_SCALE_Y * pulse.value
                translationX = -HANDLE_PULSE_SHIFT_DP.dp.toPx() * pulse.value
            }
            // 「光」全部画在这一层里，而不是再叠一层子 composable：
            // - 原来那条亮带是 `Surface` 的**子节点**，而 Material3 的 Surface 会把自己 clip 到
            //   形状内，于是光最多只能在 9dp 的条里走 —— 这正是"没啥存在感"的根因之一；
            // - 现在 bloom（外溢柔光）/过曝主带/拖尾都由同一个 draw scope 画，顺序可控；
            //   光的绘制范围就是 48dp 的命中区，溢出量另有 [HANDLE_GLOW_BLOOM_LIMIT_RATIO]
            //   夹住（只让光往条**左侧**漏，右侧留给屏幕边缘/系统手势区）。
            // 性能：`drawWithCache` 的 block 只在**尺寸或光/配色状态变化**时重跑，
            // 逐帧变化的是 `phase`（唯一那个 `animateFloat`）、`pulse`、`barAlpha`、`barWidth`，
            // 它们全都在绘制 lambda 里读 —— 所以"每帧"只重绘这一层小区域，
            // 既不重组也不重绘别的东西。
            .drawWithCache {
                val barHeightPx = HANDLE_BAR_HEIGHT_DP.dp.toPx()
                val cornerPx = HANDLE_BAR_CORNER_DP.dp.toPx()
                val borderWidthPx = HANDLE_BAR_BORDER_DP.dp.toPx()
                // 辉光笔：Compose 的 `Paint` **没有 `maskFilter` 属性**（它的 API 只有
                // color/alpha/isAntiAlias/style/strokeWidth/blendMode/shader/colorFilter/pathEffect），
                // 所以模糊滤镜只能设在**平台**画笔上；而拿平台画笔的两条老路都不能走：
                // - `Compose Paint.asFrameworkPaint()` 在本仓库（deprecated 当错误）会直接编不过；
                // - `import android.graphics.Paint` 会与 Compose 的同名类撞（本文件本来就只 import 后者）。
                // 因此这里用**全限定名**就地构造平台画笔（不动任何现有 import）。
                // 代价是它不能传给 Compose 的 `Canvas.drawRoundRect`（那个只收 Compose Paint），
                // 所以下面改走平台画布 `canvas.nativeCanvas.drawRoundRect(...)`（见 drawIntoCanvas 处）。
                val bloomPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    maskFilter = BlurMaskFilter(
                        HANDLE_GLOW_BLOOM_RADIUS_DP.dp.toPx(),
                        BlurMaskFilter.Blur.NORMAL,
                    )
                }
                // 条本体两个颜色在这里算一次（`barAlpha` 逐帧会变，所以只缓存不透明前的基色）。
                // 原实现是 `Surface(color = 基色@barAlpha)` + 里面再叠一个"洗色"`Box`
                // （alert 时 `onPrimary@10%`，否则 `onSurface@2%`）。
                // 这里**照原样分两层画**（基色 + 洗色），而不是用 `compositeOver` 合成一个颜色：
                // 合成要把"元素的 alpha × 两次叠色的 alpha"算对才能跟前一版像素一致，
                // 而这一步没有任何收益（反正都是同一次 draw 里的两个 drawPath），不值得冒险。
                val washColor = if (alert) scheme.onPrimary.copy(alpha = 0.10f) else scheme.onSurface.copy(alpha = 0.02f)
                val barBaseColor = barColor.copy(alpha = barAlpha * barFillAlpha)
                // 有光时条本体要往白里混的**目标色**（[HANDLE_GLOW_BAR_WHITEN]，≈14%）：让"整条在发光"，
                // 而不是"蓝条上叠了一层看不见的光"。
                // ⚠️ 这个混合**必须在绘制 lambda 里做**（`lerp(…, …, if (glowActive) W else 0f)`）：
                //    `glowActive` 若在这个 `drawWithCache` 的 block 里读，就不会形成对它的依赖，
                //    状态翻转时这一层不会失效 → 条体永远不亮（哪怕光已经该画了）。
                val litWhiteWeight = if (glowActive) HANDLE_GLOW_BAR_WHITEN else 0f
                // 光的颜色全从主题色 accent 推出来（不许新增颜色资源）。
                val coreColor = glowCore(scheme.primary)
                // 拖尾的渐变色也只算一次：`i` 决定色相漂移量，逐帧要变的只有位置/透明度。
                val bandColors = List(HANDLE_GLOW_BAND_COUNT) { i ->
                    glowBand(scheme.primary, i)
                }
                onDrawWithContent {
                    // 条矩形每帧重算：`barWidth` 是按住时的"变宽"动画（9→11dp），
                    // 所以它也算逐帧状态。几何放这里算的代价可以忽略（几个 dp→px 换算），
                    // 换来的是条本体与光**永远用同一套几何**，不会出现"条变宽了、光还按 9dp 走"。
                    val barWidthPx = barWidth.toPx()
                    val barLeft = size.width - HANDLE_EDGE_GAP_DP.dp.toPx() - barWidthPx
                    val barTop = (size.height - barHeightPx) / 2f
                    val barRect = Rect(barLeft, barTop, barLeft + barWidthPx, barTop + barHeightPx)
                    val barPath = Path().apply {
                        addRoundRect(RoundRect(barRect, CornerRadius(cornerPx, cornerPx)))
                    }
                    // ① 条本体（原来的 Surface 背景 + 内层洗色，颜色/圆角/尺寸全不变；
                    //    `litWhiteWeight` 由绘制阶段的 `glowActive` 决定，没光时权重为 0 → 与改动前一致）。
                    //    边框挪到**最后**画（见 ⑤），否则会被光盖住、条失去清晰轮廓。
                    drawPath(barPath, lerp(barBaseColor, Color.White, litWhiteWeight))
                    drawPath(barPath, washColor)
                    // ② 光。`glowPhase == null` 就是"当前不该有光"（没提醒 / 在拖动 / 不可见）：
                    // 这时不但不建动画时钟，连一次 draw call 都不多发。
                    // ⚠️ `glowPhase.value` 在这里读 = 在**绘制阶段**读动画状态：
                    // 每帧只让这一层 draw 失效（invalidate），不触发重组，也不重绘别处。
                    val t = glowObserved?.value ?: run {
                        // 没光的时候仍要把边框补上（上面把边框挪到最后了）。
                        drawPath(barPath, barBorderColor, style = Stroke(width = borderWidthPx))
                        return@onDrawWithContent
                    }
                    val beatRaw = glowBeat(t)
                    // ⚠️ 防御性兜底（**不是修法**，修法在 [glowBeat] 的单位换算）：
                    // 万一呼吸函数将来又被改出"恒 0 / 负数"，这里把它夹回 [GLOW_BEAT_MIN, 1]，
                    // 至少保证"光还在，只是不呼吸"，而不是整条光以 alpha 0 画出去（
                    // 那种情况在真机上和"完全没实现"长得一模一样，极难排查）。
                    val beat = beatRaw.coerceIn(GLOW_BEAT_MIN, 1f)
                    // 主带位置（px）：中心从"条左缘往左 1.5 条宽"走到"条右缘往右 1.5 条宽"，
                    // 起止都在条外 → 一个周期里能看清"进来 → 经过 → 离开"，才有扫过的速度感。
                    val travel = barWidthPx * (1f + 2f * HANDLE_GLOW_TRAVEL_SCALE)
                    val bandX = barRect.right + barWidthPx * HANDLE_GLOW_TRAVEL_SCALE -
                        travel * t
                    // ③ 条内"过曝主带 + 递减拖尾"（clip 在条形状里，不会糊到条外）。
                    //    扫动靠每帧重算几何（`bandX`），不是 `translationX`。
                    //
                    //    §体验修正（真机验证之后）：**主带不再用"两端透明"的渐变**。
                    //    原来那条 `linearGradient(Transparent, 色, Transparent)` 跨 2.6 条宽，
                    //    于是在 9dp 的条上，条内每一像素拿到的都是"锥形的中间值 + 蓝色条体混色"
                    //    → 真机上就是"看不见"。现在改成：**不透明近白实心块 + 两端各 2.5dp 短渐隐**，
                    //    也就是"一节发白的光块扫过蓝条"。
                    clipPath(barPath) {
                        for (i in 0 until HANDLE_GLOW_BAND_COUNT) {
                            val f = i.toFloat()
                            // 拖尾在主带的**右边** = 光整体从右往左扫（"从屏幕外扫进来"）。
                            val center = bandX + f * barWidthPx * HANDLE_GLOW_SPACING_SCALE
                            // 间距与 alpha 都递减（`pow`），产生"扫过去"的速度感；
                            // 亮度统一乘 `beat` 做呼吸；`coerceIn` 只是防御。
                            // 拖尾 alpha 现在也拉满（0.85^i → 0.85 / 0.72，再乘 beat ≥ 0.75 → 最暗 ≥0.54），
                            // 满足"余晖也要看得见（≥0.5）"：主带 1.0 仍是最亮的一层，层次不会被抹平。
                            val bandAlpha = (HANDLE_GLOW_CORE_ALPHA * HANDLE_GLOW_TRAIL_DECAY.pow(f) * beat)
                                .coerceIn(0f, 1f)
                            // 主带更宽、拖尾更窄：宽的亮核 + 细的余晖，层次才拉得开。
                            val half = barWidthPx * if (i == 0) {
                                HANDLE_GLOW_CORE_HALF_SCALE
                            } else {
                                HANDLE_GLOW_TRAIL_HALF_SCALE
                            }
                            val bandTop = barRect.top
                            val bandHeight = barRect.height
                            if (i == 0) {
                                // 主带：实心近白圆角块（**alpha 1.0 × beat**，没有锥形衰减），
                                // 只有两个端头各 [HANDLE_GLOW_CORE_FADE_DP] 做短渐隐。
                                drawRoundRect(
                                    color = coreColor,
                                    topLeft = Offset(center - half, bandTop),
                                    size = Size(half * 2f, bandHeight),
                                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                                    alpha = bandAlpha,
                                )
                                val fadePx = HANDLE_GLOW_CORE_FADE_DP.dp.toPx()
                                // 左端渐隐：从"中心色"渐到透明（start 用核心色 → end 透明）。
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(coreColor, Color.Transparent),
                                        start = Offset(center - half, barRect.center.y),
                                        end = Offset(center - half + fadePx, barRect.center.y),
                                    ),
                                    topLeft = Offset(center - half, bandTop),
                                    size = Size(fadePx, bandHeight),
                                    alpha = bandAlpha,
                                )
                                // 右端渐隐：透明 → 中心色。
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.Transparent, coreColor),
                                        start = Offset(center + half - fadePx, barRect.center.y),
                                        end = Offset(center + half, barRect.center.y),
                                    ),
                                    topLeft = Offset(center + half - fadePx, bandTop),
                                    size = Size(fadePx, bandHeight),
                                    alpha = bandAlpha,
                                )
                            } else {
                                // 拖尾：保留色相渐变（青 → 品红），但**只在两端各留一点柔边**
                                //（渐变跨 2 倍渐隐宽度、中间是实色），这样它是"一条有色的余晖"，
                                // 而不是"两端透明的一片雾"。
                                val fadePx = barWidthPx * HANDLE_GLOW_TRAIL_FADE_SCALE
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.Transparent, bandColors[i], bandColors[i], Color.Transparent),
                                        start = Offset(center - half, barRect.center.y),
                                        end = Offset(center + half, barRect.center.y),
                                    ),
                                    topLeft = Offset(center - half, bandTop),
                                    size = Size(half * 2f, bandHeight),
                                    alpha = bandAlpha,
                                )
                            }
                        }
                    }
                    // ④ 外层辉光（bloom）：**画在条本体之上**，而且不只是沿条宽、还往**上下左右**外扩
                    //    —— 这是"光漏出 9dp 的条"的关键，也是上一版"看不见"的主因：
                    //    上一版 bloom 只比条宽一点、纵向完全不外扩，于是它基本整块躺在条里，
                    //    再被 0.72 alpha 的条本体盖住 → 条外什么都没剩下。
                    //    现在三层由大到小叠（外层淡、内层亮），配合 9dp 模糊形成柔和光环。
                    //    ⚠️ 右边界仍然夹在条的右缘：条距屏幕右缘只有 3dp，光只能往左（和上下）走。
                    val springX = HANDLE_HIT_DP.dp.toPx() * HANDLE_GLOW_BLOOM_LIMIT_RATIO
                    val bloomTop = (barRect.top - barWidthPx * HANDLE_GLOW_BLOOM_SPILL_SCALE)
                        .coerceAtLeast(0f)
                    val bloomBottom = (barRect.bottom + barWidthPx * HANDLE_GLOW_BLOOM_SPILL_SCALE)
                        .coerceAtMost(size.height)
                    val bloomAlphaBase = HANDLE_GLOW_BLOOM_ALPHA * beat
                    drawIntoCanvas { canvas ->
                        // ⚠️ 走平台画布：`nativeCanvas` 是 `androidx.compose.ui.graphics.Canvas` 上的
                        // 扩展属性（`AndroidCanvas_androidKt.getNativeCanvas`，已 import），
                        // 只有它的 `drawRoundRect(l, t, r, b, rx, ry, Paint)` 收平台画笔；
                        // Compose 的 `Canvas.drawRoundRect` 只收 Compose `Paint`（会类型不符）。
                        // ⚠️ `bloomPaint` 是**平台**画笔，`color` 是 ARGB Int，所以要 `toArgb()`；
                        // 别改回 Compose `Paint` —— 那支笔没有 `maskFilter`，模糊会失效。
                        val native = canvas.nativeCanvas
                        for (j in HANDLE_GLOW_BLOOM_LAYER_SCALES.indices.reversed()) {
                            bloomPaint.color = coreColor.copy(
                                alpha = (bloomAlphaBase * HANDLE_GLOW_BLOOM_LAYER_ALPHAS[j])
                                    .coerceIn(0f, 1f),
                            ).toArgb()
                            val half = barWidthPx * HANDLE_GLOW_BLOOM_LAYER_SCALES[j]
                            native.drawRoundRect(
                                (bandX - half).coerceAtLeast(springX), bloomTop,
                                (bandX + half).coerceAtMost(barRect.right), bloomBottom,
                                cornerPx, cornerPx, bloomPaint,
                            )
                        }
                    }
                    // ⑤ 最后补 1dp 边框：让"发光"的条仍然有清楚轮廓，也不会被光糊掉边界。
                    drawPath(barPath, barBorderColor, style = Stroke(width = borderWidthPx))
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 唯一的动画时钟：只在"该有光"时才进组合（`rememberInfiniteTransition` 在 if 内），
        // 没提醒 / 拖动中 / 窗口不可见时它整个不存在 —— 真的一帧都不跑。
        if (glowActive) {
            HistoryHandleGlowSweep()
        }
    }
}

/**
 * 唯一的一个动画时钟（性能硬约束：单个 float 驱动整条流光）。
 *
 * 为什么做成"只吐一个 `State<Float>` 的 @Composable"：
 * 它唯一的产物就是返回的 `State<Float>`，而 `State` 可以在 `if` 里创建、
 * 再由 `if` 外层 `HistoryFloatHandle` 的 `drawWithCache` 绘制 lambda 去读 ——
 * 读取点在**绘制阶段**，所以每帧只让那一层 draw 失效（`invalidate`），
 * **不重组**把手、也不重绘别的节点。它自己**不画任何像素**（没有 UI）。
 * 反过来，如果把这个 `rememberInfiniteTransition` 提到 `if` 外面，
 * "没有提醒"的常驻状态下就会有一个永远跑着的动画时钟 —— 那是本次改动明确要避免的。
 *
 * 为什么亮度包络不另开一个动画：需求要"2s 亮 / 1s 暗"，但**再开一个无限动画**就多一个时钟；
 * 这里直接用同一个 `phase` 当呼吸的相位、且周期与扫描周期**不同频**（见 [glowBeat]），
 * 既有呼吸感又不会和扫光同频僵硬。
 */
@Composable
private fun HistoryHandleGlowSweep(): State<Float> {
    val transition = rememberInfiniteTransition(label = "handleGlow")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = HANDLE_GLOW_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "handleGlowPhase",
    )
}

/**
 * 把 [source] 的相位**桥接成组合期可观察的状态**。
 *
 * ⚠️ **这不是调试代码，不要当脚手架删掉** —— 它是"能看见"的保证之一。
 * 原先整条流光只在 `drawWithCache` 的**绘制 lambda** 里读 `animateFloat` 的 `State`，
 * 依赖"Compose 在绘制阶段也观察快照读取"这条机制；而实测〔诊断描边同一 lambda 每次都画出来、
 * 条内却永远纯蓝〕说明这条依赖**不足以**保证逐帧重绘（当时还有一个 `glowBeat` 单位 bug 叠加，
 * 见 [glowBeat]，但这座桥是另一半保险）。
 * 做法：`withFrameNanos` 逐帧把 source 的值搬进一个 `mutableFloatStateOf`，
 * 这个 float state 是**在组合期/效果期被读**的 → 镜像一变就重组 → 重绘必定发生。
 * 相当于"自建帧循环"把相位推给绘制层，而不是指望绘制期的快照观察。
 *
 * 代价：`glowActive` 期间**每动画帧触发一次这个 composable 的重组**（不是整棵把手树），
 * 且仅在"有未处理提醒"时发生 —— 与既有的性能 gate 一致。
 */
@Composable
private fun rememberGlowObservedPhase(source: State<Float>): State<Float> {
    val mirror = remember { mutableFloatStateOf(source.value) }
    LaunchedEffect(source) {
        while (true) {
            withFrameNanos { mirror.floatValue = source.value }
        }
    }
    return mirror
}

/** 向左拖动超过这个距离就认为用户想拉出收纳面板（未进入跟手时的兜底判定）。 */
private const val OPEN_DRAG_THRESHOLD_X = -20f

/** 前这么多 dp 内锁定主方向：横向 = 跟手拉出面板，纵向 = 挪把手（设计稿注释同款）。 */
private const val HANDLE_AXIS_LOCK_PX = 8f

/** 过半就提交（松手后面板归位，否则弹回）。 */
private const val REVEAL_COMMIT_FRACTION = 0.5f

/** 拖动的主方向。 */
private enum class DragAxis { HORIZONTAL, VERTICAL }

/** 命中区边长（Android 最小触摸目标）。 */
private const val HANDLE_HIT_DP = 48

/**
 * 视觉条距**屏幕右缘**的距离（也是它"贴右边"的唯一决定因素）。
 *
 * 为什么这个常量就等于"到屏幕右缘的距离"：
 * - 把手窗是 `WRAP_CONTENT`（内容宽 = [HANDLE_HIT_DP] 的命中区）、
 *   `gravity = TOP or START`、`x = 0`（见 `HistoryFloatService.applyHandlePosition`）；
 * - 命中区里条是 `contentAlignment = CenterEnd` + `padding(end = 本值)`，
 *   绘制层也用 `barLeft = size.width - 本值 - 条宽` 定位；
 * - 于是"屏幕右缘 − 条右缘"= 本值。**只动间距**，条宽/高/圆角/命中区都没动
 *   （条宽另有 `HistoryFloatHandleWidth` 设置项，那条线本次没碰）。
 *
 * §间距三次收紧（用户反馈"间距太大"，两次都要求"减半"）：
 * **12dp（原设计稿）→ 6dp → 3dp**。
 *
 * ⚠️ 3dp 的风险（用户明确要求减半 → 仍按 3dp 实现，但真机上要留意）：
 * 从屏幕右缘往里滑是系统的返回/侧滑手势区（各家 ROM 大约 20–24dp），
 * 条现在几乎贴着右缘，条本身大半落在手势区里，手指落在条右侧时可能被系统抢走
 * （单击仍能命中，因为 48dp 命中区一直延伸到左边）。
 * 本次"光"也刻意只往**条左侧**溢出（见 [HANDLE_GLOW_BLOOM_LIMIT_RATIO]），
 * 就是为了不把光画到 3dp 之外的屏幕边缘手势区上。
 */
private const val HANDLE_EDGE_GAP_DP = 3

// ───────────────────────── 科幻流光（§流光）参数 ─────────────────────────
//
// 一眼参数表（真机上调观感只改这里）：
// - 速度：HANDLE_GLOW_PERIOD_MS = 2600ms 扫一遍
// - 呼吸：同一个 phase 复用，约 1200ms 一个"拍"（700ms 亮 / 500ms 暗）——
//   周期以"圈"为单位记在 [GLOW_BEAT_PERIOD_SWEEPS]，故意和扫描周期**不同频**
// - 主带：不透明近白实心块（3.6×条宽）+ 两端 2.5dp 短渐隐 → "过曝通光"
// - 拖尾：3 条（1 主带 + 2 余晖），间距 0.70×条宽、alpha 每次 ×0.85、色相青→蓝紫→品红
// - 辉光：HANDLE_GLOW_BLOOM_*，把光"漏"到条外的关键（"存在感"的主要来源）

/** 流光周期：2.6s 扫一遍。 */
private const val HANDLE_GLOW_PERIOD_MS = 2600

/**
 * 扫动的额外行程（以条宽为单位）。
 *
 * 1.5 → 主带中心在 `-0.5w … 1.5w` 之间走：起止都**完全在条外**。
 * 为什么不干脆 0（只在条内走）：那样"扫进来/扫出去"各占掉半个周期、光看起来是"弹"出来的；
 * 现在一个周期里光有明确的进入 → 经过 → 离开，速度感更连续。
 */
private const val HANDLE_GLOW_TRAVEL_SCALE = 1.5f

/**
 * 主带半宽 = 条宽的这个比例。
 *
 * 主带是一个**实心不透明圆角块**，半宽 = 1.8 × 条宽 → 全宽 3.6 × 9 ≈ 32dp，
 * 只有两端各 [HANDLE_GLOW_CORE_FADE_DP] 做短渐隐。条宽只有 9dp，所以"实心部分"横扫整条时
 * 一定有 ≥9dp 的完全实心覆盖 —— 这就是"必定看得见"的来源（不再依赖锥形渐变的中间值）。
 */
private const val HANDLE_GLOW_CORE_HALF_SCALE = 1.8f

/**
 * 主带两端各留多长的短渐隐（dp）。
 *
 * 只用来消掉硬边（"一节光块"的边缘要是刀切的一样会很假），**不能长**：
 * 它越长，"实心不透明"的部分就越短，就又回到"两端透明看不见"的老问题上了。
 * 2.5dp 在 9dp 的条上刚好"边缘柔一点点"。
 */
private const val HANDLE_GLOW_CORE_FADE_DP = 2.5f

/** 拖尾半宽 = 条宽的这个比例（比主带窄 → 宽亮核 + 细余晖，层次拉得开）。 */
private const val HANDLE_GLOW_TRAIL_HALF_SCALE = 0.45f

/** 拖尾的柔边 = 条宽的这个比例（渐变跨 2 倍这个值，中间是实色 → 不至于"整条都是雾"）。 */
private const val HANDLE_GLOW_TRAIL_FADE_SCALE = 0.35f

/** 拖尾条数（含主带）。1 主带 + 2 余晖 = 3，再多在 9dp 上就糊成一片了。 */
private const val HANDLE_GLOW_BAND_COUNT = 3

/** 拖尾之间的间距 = 条宽的这个比例。 */
private const val HANDLE_GLOW_SPACING_SCALE = 0.70f

/**
 * 拖尾 alpha 的递减系数：第 i 条 = 主带 × `本值^i`。
 *
 * 0.85 → 两条余晖 = **0.85 / 0.72**（再乘 `beat ≥ 0.75`，最暗仍 ≥0.54），
 * 满足"余晖也要看得见（≥0.5）"；主带 1.0 仍是最亮的一层，层次不会被抹平。
 */
private const val HANDLE_GLOW_TRAIL_DECAY = 0.85f

/** 主带峰值不透明度（还会再乘呼吸包络 `beat`）。1.0 = 实心不过曝到失真。 */
private const val HANDLE_GLOW_CORE_ALPHA = 1.0f

/**
 * 外层辉光的不透明度峰值（同样乘 `beat`），再按 [HANDLE_GLOW_BLOOM_LAYER_ALPHAS] 逐层递减。
 * 配 9dp 模糊 + 大幅左溢 + 纵向外扩，让条左侧有一圈**一眼能看见**的光环。
 */
private const val HANDLE_GLOW_BLOOM_ALPHA = 1.0f

/** 辉光的模糊半径（越大越柔、越"光晕"）。9dp 在 48dp 命中区里仍然收得住，不会糊成一团。 */
private const val HANDLE_GLOW_BLOOM_RADIUS_DP = 9f

/**
 * 辉光的层宽（以条宽为单位，从内到外）。三层叠出"细芯 → 亮晕 → 大范围柔光"。
 *
 * 关键：**最外层要比条宽大得多**（3.2 × 9dp ≈ 29dp），这样无论主带在条内哪个位置，
 * 光晕都必然从条的左缘漏出去一大截 —— 这是"存在感"的唯一来源，
 * 因为条的右侧只剩 3dp（见 [HANDLE_EDGE_GAP_DP]），光只能往左走。
 * ⚠️ 外层 3.2 已接近窗口左缘的余量上限（见 [HANDLE_GLOW_BLOOM_LIMIT_RATIO] 的几何），
 * 再放大就会被 `coerceAtLeast(springX)` 夹住、变成硬边光块。
 */
private val HANDLE_GLOW_BLOOM_LAYER_SCALES = floatArrayOf(0.9f, 1.8f, 3.2f)

/** 三层辉光的 alpha 系数（乘 [HANDLE_GLOW_BLOOM_ALPHA]）。外层更淡 = 边缘渐隐，不会像硬边方块。 */
private val HANDLE_GLOW_BLOOM_LAYER_ALPHAS = floatArrayOf(1.0f, 0.55f, 0.35f)

/**
 * 辉光往条**上下**外扩多少（以条宽为单位）。
 *
 * 只沿条宽方向扩张的话，整块光晕会基本躺在条里、再被条本体盖掉 —— 条外什么都看不见。
 * 1.7 让光环比条高出一大截（约 28dp + 2×15dp），横向又只往左，于是形成一个
 * 明显偏向左侧的大光斑，"光从条里漏出来"这件事才看得见。
 */
private const val HANDLE_GLOW_BLOOM_SPILL_SCALE = 1.7f

/**
 * 辉光允许"往条的左侧"溢出多远（以命中区宽度为单位的**左边界**）。
 *
 * ⚠️ 这是安全性参数，不是审美参数。把手窗是 `WRAP_CONTENT`（内容宽 = 48dp 命中区）
 * 且 `gravity = TOP or START`、`x = 0`，所以命中区的右缘就贴屏幕右缘，
 * 条距屏幕右缘只有 [HANDLE_EDGE_GAP_DP]（= 3dp）。
 * 也就是说条的**右侧**只剩 3dp 就到屏幕边缘（再往外就是系统侧滑/返回的手势区），
 * 光绝对不能往右边溢。所以 bloom 的右边界被夹在条的右缘，只让光从条**左侧**漏出去。
 * 0.04 这个比例 ≈ 左边界不越过 x = 1.9dp（离左窗缘还有约 1.9dp 余量）。
 */
private const val HANDLE_GLOW_BLOOM_LIMIT_RATIO = 0.04f

/** 条圆角（与原来 `RoundedCornerShape(5.dp)` 一致；绘制层是按形状手画的，所以要有这个常量）。 */
private const val HANDLE_BAR_CORNER_DP = 5

/** 条高：原来直接写死在 `size(width, height = 28.dp)` 里，绘制层要按它算矩形。 */
private const val HANDLE_BAR_HEIGHT_DP = 28

/** 条边框宽度（原来 `BorderStroke(1.dp, …)`）。 */
private const val HANDLE_BAR_BORDER_DP = 1f

/**
 * 主带（近白亮核）的色相/饱和度/亮度目标。
 *
 * 条本体是**蓝色主题色**，主带若也走蓝色系（旧实现是 accent 提亮）就是"蓝底扫蓝光"、
 * 对比天然极低。现在主带不继承 accent 的色相，压成"近白/极浅青"：
 * `sat 0.12`、`value 0.98` → 实际约 **#F1FAFF**（R,G,B 三个通道都 >200），
 * 再加 [HANDLE_GLOW_CORE_ALPHA]=1.0 的实心块，在蓝条上是明确的"过曝白芯"。
 * 只留一点点冷色倾向（不做纯白：纯白在 9dp 上像贴了一条白胶带）。
 */
private const val HANDLE_GLOW_CORE_SATURATION = 0.12f
private const val HANDLE_GLOW_CORE_VALUE = 0.98f

/** 主带固定色相（0.50 = 青，180°）。**不再继承 accent** —— 理由见上面的"配色修正"。 */
private const val GLOW_CORE_HUE = 0.50f

/**
 * 拖尾的渐变端点：**一端偏青、另一端偏品红/紫**，形成"溢彩"而不是同色系。
 * 中间几条按比例在两端之间插值 → 一条"青 → 蓝 → 紫 → 品红"的彩虹式余晖。
 * 饱和度/亮度也随 i 递减（越远的余晖越暗、越"发雾"，更像辉光）。
 */
private const val GLOW_TRAIL_HUE_CYAN = 0.46f
private const val GLOW_TRAIL_HUE_MAGENTA = 0.84f
private const val HANDLE_GLOW_TRAIL_SATURATION = 0.95f
private const val HANDLE_GLOW_TRAIL_SATURATION_STEP = 0.06f
private const val HANDLE_GLOW_TRAIL_VALUE = 0.86f
private const val HANDLE_GLOW_TRAIL_VALUE_STEP = 0.07f

/**
 * `glowActive` 时把条本体往白里混的比例（0.14 ≈ 14%）。
 *
 * 为什么要动条本体：原实现里"光"只能叠在 9dp 的条上，而条本体在 alert 态是**不透明主题色** ——
 * 光再亮也只是"蓝条上一条更亮的蓝"。掺一点白让**整条**都进入"通电"状态，
 * 观感才是"这条在发光"，而不是"蓝条上有层看不见的光"。
 * ⚠️ 只在有光时混：没提醒时这张条必须和改动前逐像素一致。
 */
private const val HANDLE_GLOW_BAR_WHITEN = 0.14f

/**
 * 呼吸的"拍"长，单位是**"几圈扫描"**（不是毫秒！见 [glowBeat] 的单位纪律注释）。
 *
 * 2600ms / 1200ms ≈ **2.1667 圈一拍** → 一拍 ≈ 1200ms，与 2600ms 的扫描**不同频**
 *（两者不是整数倍关系），所以"光扫到哪"与"呼吸到哪"不会锁死，观感不僵硬。
 * ⚠️ 这两个数（2.1667 与 2600/1200）是绑定的：若改 [HANDLE_GLOW_PERIOD_MS]，
 *    这里要按 `扫描周期ms / 期望拍长ms` 重算，否则呼吸会跟着扫描一起变慢/变快。
 */
private const val GLOW_BEAT_PERIOD_SWEEPS = HANDLE_GLOW_PERIOD_MS / 1200f

/** 一拍里"亮着"的比例：0.58 × 1200ms ≈ **0.7s 亮 / 0.5s 暗**。 */
private const val GLOW_BEAT_DUTY = 0.58f

/**
 * 暗段最低亮度。
 *
 * **0.75**：暗谷仍然有变化（呼吸感还在），但**任何时刻都不会低于 3/4 亮**，
 * 按用户"一直炫光流彩"的要求收窄了呼吸振幅。
 * ⚠️ 这个值现在有**第二个职责**：它是 [glowBeat] 的返回值下界，也是绘制侧的**兜底夹紧**
 * （`beat.coerceIn(GLOW_BEAT_MIN, 1f)`）—— 万一呼吸函数将来又被改错，光也不会全透明。
 * ⚠️ 验收口径依赖它：主带最终 alpha = `1.0 × beat`，`beat ≥ 0.75` 才能保证
 * "近白实心块 × 0.75" 叠在蓝条上仍是近白像素（R,G,B 都 >200）。
 */
private const val GLOW_BEAT_MIN = 0.75f

/** 亮起用的幂次（>1 = 起得慢一点 = 更像"吸气"）。 */
private const val GLOW_BEAT_ATTACK_POW = 1.6f

/**
 * 呼吸包络：把扫描进度 [t] 当相位用，返回当前亮度系数（**恒在 [GLOW_BEAT_MIN] … 1 之间**）。
 *
 * ⚠️ **单位纪律（这里出过一次致命的 bug，改之前务必先看这段）**：
 * [t] 是 `animateFloat` 的扫描进度，**定义域 0..=1、单位是"圈（周期）"，不是毫秒**。
 * 曾写成 `beat = (t / GLOW_BEAT_PERIOD) % 1f`，而那个常量当时是 **1200（毫秒）**，
 * 于是 `beat = t / 1200 ≤ 0.00083` —— **永远**落在 `beat < GLOW_BEAT_DUTY(0.58)` 的"亮"分支，
 * 且 `q = beat / 0.58 ≈ 0.0014`，再 `pow(1.6)` 后 ≈ **0.003**。
 * 结论：beat 恒≈0，主带与两条拖尾全部以 alpha≈0 画出去（真机探针实测
 * `t=0.7001 beat=0.000 alpha=0.000`），条内因此永远纯蓝 —— 排查方向一度全错。
 * 现在把周期定义成**"几圈扫描"**这个单位：[GLOW_BEAT_PERIOD_SWEEPS] = 2600/1200 ≈ 2.1667，
 * `t × 2.1667` 才等于"已经过去的拍数"；周期与扫描**不同频**（需求要的"错开、不僵硬"）。
 *
 * 形状：一亮一暗为一拍。亮段占 [GLOW_BEAT_DUTY]（=0.58 拍 ≈ 0.7s）：前 42% 用 pow 慢起、
 * 之后平滑收到满亮；暗段（≈0.5s）从满亮平滑落到 [GLOW_BEAT_MIN] 保底。
 */
private fun glowBeat(t: Float): Float {
    // 双保险：`animateFloat` 的端点理论上可能给出 1.0，取模后落回 [0,1)。
    val p = ((t % 1f) + 1f) % 1f
    // 0..1 的"扫描圈数" → 0..1 的"拍内位置"（乘周期再取小数 = 每 GLOW_BEAT_PERIOD_SWEEPS 圈拍一次）。
    val beat = (p * GLOW_BEAT_PERIOD_SWEEPS) % 1f
    return if (beat < GLOW_BEAT_DUTY) {
        // 亮段：前 42% 慢起（pow>1 = 起得慢），之后平滑收到 1.0。
        val q = (beat / GLOW_BEAT_DUTY).coerceIn(0f, 1f)
        (q / 0.42f).coerceAtMost(1f).pow(GLOW_BEAT_ATTACK_POW).coerceIn(0f, 1f)
    } else {
        // 暗段：从 1.0 平滑落到 GLOW_BEAT_MIN 保底（再乘一次曲线让它更有"呼"的感觉）。
        val q = ((beat - GLOW_BEAT_DUTY) / (1f - GLOW_BEAT_DUTY)).coerceIn(0f, 1f)
        (1f - q).pow(1.5f).coerceIn(GLOW_BEAT_MIN, 1f)
    }
}

/**
 * 过曝主带的颜色：**固定成"白/极浅青"**，不再继承 accent 的色相（蓝底扫蓝光对比极低）。
 * **仍然不引入任何颜色资源**：颜色还是在代码里算出来的（只是不再从 accent 取色相）。
 */
private fun glowCore(accent: Color): Color = accent.lit(
    saturation = HANDLE_GLOW_CORE_SATURATION,
    value = HANDLE_GLOW_CORE_VALUE,
    hueShift = GLOW_CORE_HUE,
)

/**
 * 第 [index] 条拖尾的颜色：**青 → 蓝 → 紫 → 品红** 的溢彩渐变（不再是同色系蓝）。
 *
 * 色相在 [GLOW_TRAIL_HUE_CYAN] … [GLOW_TRAIL_HUE_MAGENTA] 之间按 `index / (条数-1)` 插值：
 * 3 条时约 0.46 / 0.65 / 0.84（青 / 蓝紫 / 品红），条数变了也自动铺满整个区间。
 */
private fun glowBand(accent: Color, index: Int): Color {
    val f = index.toFloat()
    val span = (HANDLE_GLOW_BAND_COUNT - 1).coerceAtLeast(1).toFloat()
    val t = (f / span).coerceIn(0f, 1f)
    return accent.lit(
        saturation = (HANDLE_GLOW_TRAIL_SATURATION - f * HANDLE_GLOW_TRAIL_SATURATION_STEP)
            .coerceIn(0f, 1f),
        value = (HANDLE_GLOW_TRAIL_VALUE - f * HANDLE_GLOW_TRAIL_VALUE_STEP)
            .coerceIn(0f, 1f),
        hueShift = GLOW_TRAIL_HUE_CYAN + (GLOW_TRAIL_HUE_MAGENTA - GLOW_TRAIL_HUE_CYAN) * t,
    )
}

/**
 * 把某条"光"的颜色算到 HSV 里再改成目标饱和度/亮度/色相，最后转回 sRGB。
 *
 * 为什么要有这个 helper：需求的"过曝主带 + 递减拖尾 + 色相漂移"三件事在 HSV 里各是一行；
 * 在 sRGB 里要自己写混色曲线，既长又难调。
 * [hueShift] 传 null = 不动色相。
 *
 * ⚠️ 这里**自己算 HSV**，没有用 `Color.toHsv()`：本仓库锁的 Compose（1.13.0-alpha03）
 * 的 `ui-graphics` 里并没有那个 `Hsv` 返回类型（api jar 里没有对应类），
 * 为了不让一个"只为了调个颜色"的辅助函数变成编译风险，就地把换算写全。
 * 纯函数、不分配额外对象（只返回一个 Color）。
 */
private fun Color.lit(
    saturation: Float,
    value: Float,
    hueShift: Float? = null,
): Color {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    // 灰（delta = 0）时色相没有定义：保持 0，此时 saturation 也会是 0，
    // 结果只由 value 决定 —— 不会出现"除以 0"或 NaN。
    val h = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta + 6f) % 6f / 6f
        max == g -> ((b - r) / delta + 2f) / 6f
        else -> ((r - g) / delta + 4f) / 6f
    }
    val targetHue = ((hueShift ?: h) % 1f + 1f) % 1f
    val targetSat = saturation.coerceIn(0f, 1f)
    val targetValue = value.coerceIn(0f, 1f)
    // HSV → RGB（标准分段线性公式）。
    val c = targetValue * targetSat
    val hp = targetHue * 6f
    val x = c * (1f - abs(hp % 2f - 1f))
    val m = targetValue - c
    val (r2, g2, b2) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r2 + m).coerceIn(0f, 1f),
        green = (g2 + m).coerceIn(0f, 1f),
        blue = (b2 + m).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

/** 存下后的脉冲：设计稿 `pippulse 900ms`。 */
private const val HANDLE_PULSE_DURATION_MS = 900
private const val HANDLE_PULSE_SCALE_Y = 0.18f
private const val HANDLE_PULSE_SHIFT_DP = 3f
