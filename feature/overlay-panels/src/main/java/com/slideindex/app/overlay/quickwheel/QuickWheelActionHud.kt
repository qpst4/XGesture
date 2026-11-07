package com.slideindex.app.overlay.quickwheel

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.settings.QuickWheelIconSource
import com.slideindex.app.settings.QuickWheelSlot
import kotlin.math.roundToInt

/**
 * 动作提示条（HUD）。
 *
 * 为什么需要它：操作容器时**手指会遮住容器自身**，用户看不到自己悬停/按到了什么。
 * 于是在屏幕边缘固定显示一条"此刻松手会发生什么"。
 *
 * 方案 C（混合）：
 * - **未按满**：主行 = 容器外观（容器图标 + 容器名称；没名称时退化成单击动作名），
 *   副行预告长按（仅当长按动作与单击**实质不同**）；
 * - **按满后**：主行**换成长按动作**（长按动作图标 + "长按：…"），副行预告单击。
 *
 * 关键在于"长按动作的图标"这个冲突是**在时间维度上拆开的**：容器图标天生只反映单击动作
 * （见 `QuickWheelSlotIcon` 的注释），所以永远只有一个"当前动作"需要呈现，不需要在一个容器上
 * 同时表达两个图标。
 */
private const val HUD_EDGE_INSET_DP = 44

/**
 * 图标边长（**不再有白底方框**）。
 *
 * 位图（应用 / 快捷方式 / 图库）按系统图标**自身形状**绘制，四周不留白边；
 * 矢量 / 文字图标用浅色 [HudActionGlyph]，贴在深色 HUD 上。
 */
private val HudIconSize = 30.dp

/** 矢量 / 文字图标边长（无底座后略小一点，视觉重量才与位图图标相当）。 */
private val HudIconGlyphSize = 24.dp

/** 图标外框（比图标大一圈，留给环形进度条）。 */
private val HudIconSlot = 40.dp

private val HudRingStroke = 3.dp
private val HudPillShape = RoundedCornerShape(16.dp)
private val HudBackground = Color(0xE6202020)
private val HudPrimaryText = Color.White
private val HudSecondaryText = Color(0xB3FFFFFF)
private val HudRingTrack = Color(0x33FFFFFF)
private val HudRingFill = Color(0xFF64B5F6)
/** 矢量 / 文字图标在深色 HUD 上的颜色（白底已去掉，不能再是深色）。 */
private val HudActionGlyph = Color(0xE6FFFFFF)
private const val HUD_TEXT_MAX_WIDTH_DP = 200

/** 松手会执行哪一类动作（与 [QuickWheelOverlayContent] 的 `handleRelease` 判定保持一致）。 */
enum class QuickWheelHudMode {
    /** 松手执行该容器的**单击**动作。 */
    TAP,

    /** 松手执行该容器的**长按**动作（已按满；或二级展开时命中父容器）。 */
    LONG_PRESS,
}

/**
 * 决定 HUD 该呈现"单击"还是"长按"。
 *
 * 与 `handleRelease` 的语义逐条对齐：
 * - 二级展开时命中**父容器** → 松手执行的就是父容器的**长按动作**；
 * - 已按满（[armed]）→ 长按；
 * - 其余 → 单击。
 */
fun quickWheelHudModeFor(
    hitIsParentWhileExpanded: Boolean,
    armed: Boolean,
): QuickWheelHudMode =
    if (hitIsParentWhileExpanded || armed) QuickWheelHudMode.LONG_PRESS else QuickWheelHudMode.TAP

/** HUD 的纯内容描述（便于单测）。 */
data class QuickWheelHudContent(
    /** 主行是否代表长按动作（是 → 用长按动作图标 + "长按：…"）。 */
    val primaryIsLongPress: Boolean,
    val primaryText: String,
    /** 副行：另一个动作的预告；两者实质相同时为 null。 */
    val secondaryText: String?,
    /** 是否显示长按进度条（未按满、且"按满"确实有事发生：有长按动作或会展开二级）。 */
    val showProgress: Boolean,
)

/**
 * 方案 C 的内容推导（纯函数）。
 *
 * @param tapLine 已本地化的单击行文案（如 "单击：返回"；没配动作时传"未设置动作"）
 * @param longPressLine 已本地化的长按行文案（如 "长按：主屏幕"）
 */
fun quickWheelHudContent(
    slot: QuickWheelSlot,
    mode: QuickWheelHudMode,
    tapLine: String,
    longPressLine: String,
    hasSubSlots: Boolean,
): QuickWheelHudContent {
    val hasTap = slot.tapAction.type != GestureActionType.NONE
    val hasLongPress = slot.longPressAction.type != GestureActionType.NONE
    // 两个动作"实质不同"才值得预告：新建容器默认 tap == longPress，此时只显示一行。
    val distinct = hasLongPress && (!hasTap || slot.longPressAction != slot.tapAction)
    return when (mode) {
        QuickWheelHudMode.TAP -> QuickWheelHudContent(
            primaryIsLongPress = false,
            // 主行优先用容器名称（与屏幕上看到的一致），没名称才退化成动作名。
            primaryText = slot.name.ifBlank { tapLine },
            secondaryText = if (distinct) longPressLine else null,
            showProgress = hasLongPress || hasSubSlots,
        )

        QuickWheelHudMode.LONG_PRESS -> QuickWheelHudContent(
            primaryIsLongPress = true,
            primaryText = longPressLine,
            secondaryText = if (distinct && hasTap) tapLine else null,
            showProgress = false,
        )
    }
}

/**
 * HUD 专用动作文案：**打开应用 / 快捷方式只说名字**，不带"启动应用：/启动快捷方式："这种类型前缀
 * （HUD 空间小，且"松手会干嘛"由图标 + 上下文已经说清）。
 *
 * 其它动作沿用 [gestureActionLabelText]（它们本身就是裸名，如"返回 / 主屏幕 / 手电筒"）。
 */
private fun hudActionLabel(context: Context, action: GestureAction): String = when (action) {
    is GestureAction.LaunchApp -> {
        if (action.packageName.isBlank()) {
            context.getString(R.string.gesture_action_launch_app)
        } else {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(action.packageName, 0)).toString()
            }.getOrElse { action.packageName }
        }
    }

    is GestureAction.LaunchShortcut ->
        (com.slideindex.app.overlay.panels.PanelRuntimeHooks.launchShortcutLabel
            ?.invoke(action).orEmpty()).ifBlank {
            context.getString(R.string.gesture_action_launch_shortcut)
        }

    else -> com.slideindex.app.overlay.panels.PanelRuntimeHooks.gestureActionLabel
        ?.invoke(context, action).orEmpty()
}

/**
 * 动作的**真实图标**位图（打开应用 → 应用图标；快捷方式 → 快捷方式自身图标）。
 *
 * 与容器图标解析共用同一份实现（[QuickWheelIconResolver.actionBitmapFor]），
 * 保证 HUD 与屏幕上的容器看到的是同一个图标。
 *
 * ⚠️ 走无依赖注入的解析：浮层合成里没有提供 `LocalAppDependencies`，凡用
 * `rememberAppRepository()` 的可组合函数都会直接抛异常（曾导致"一点击容器就闪退"）。
 */
@Composable
private fun rememberActionIconBitmap(
    context: Context,
    action: GestureAction,
    sizePx: Int,
): ImageBitmap? = remember(context, action, sizePx) {
    QuickWheelIconResolver.actionBitmapFor(context, action, sizePx)
}

/**
 * 动作提示条本体：固定尺寸的图标 + 文字（不随容器尺寸 / 缩放 / 偏移变化）。
 *
 * 位置固定贴屏幕**上部**（不再跟随手指半屏互换）；长按进度画成**绕图标一圈的环**。
 *
 * @param progressKey 变化即重启长按进度动画（传悬停 token，与长按计时同源）
 */
@Composable
fun QuickWheelActionHud(
    slot: QuickWheelSlot,
    mode: QuickWheelHudMode,
    longPressMs: Int,
    hasSubSlots: Boolean,
    progressKey: Any?,
    modifier: Modifier = Modifier,
) {
    val hasTapAction = slot.tapAction.type != GestureActionType.NONE
    val hasLongPressAction = slot.longPressAction.type != GestureActionType.NONE
    val noAction = stringResource(R.string.quick_wheel_hud_no_action)
    // ⚠️ 这里**必须**用无依赖注入的 com.slideindex.app.overlay.panels.PanelRuntimeHooks.gestureActionLabel?.invoke(context, …)：
    // 可组合版 gestureActionLabel(…) 会读 `LocalAppDependencies`，而浮层合成里**没有**提供它
    // （该 CompositionLocal 的默认实现直接 error(...) 抛异常）→ 手指一碰到容器、
    // HUD 首次合成就会闪退（"一点击容器就闪退"）。
    val context = LocalContext.current
    val tapLine = if (hasTapAction) {
        stringResource(
            R.string.quick_wheel_hud_tap,
            hudActionLabel(context, slot.tapAction),
        )
    } else {
        noAction
    }
    val longPressLine = if (hasLongPressAction) {
        stringResource(
            R.string.quick_wheel_hud_long_press,
            hudActionLabel(context, slot.longPressAction),
        )
    } else {
        noAction
    }
    val content = quickWheelHudContent(
        slot = slot,
        mode = mode,
        tapLine = tapLine,
        longPressLine = longPressLine,
        hasSubSlots = hasSubSlots,
    )

    // 长按进度：与内容层的 `delay(longPressMs)` 同源（key 用悬停 token，目标一变就重来）。
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progressKey, content.showProgress) {
        if (!content.showProgress) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = longPressMs.coerceAtLeast(1),
                easing = LinearEasing,
            ),
        )
    }

    // 主行图标：容器自己配了图标（且"未按满"）→ 用容器图标；否则按"当前动作"取真实图标。
    val primaryAction = if (content.primaryIsLongPress) slot.longPressAction else slot.tapAction
    val useSlotIcon = !content.primaryIsLongPress && slot.iconSource != QuickWheelIconSource.NONE
    val density = LocalDensity.current
    // 位图按图标边长取像素：不再缩小（"缩小 + 白底"就是原来那圈边框间隙）。
    val bitmapSizePx = with(density) { HudIconSize.toPx().roundToInt() }
    val actionBitmap = if (useSlotIcon) {
        null
    } else {
        rememberActionIconBitmap(context = context, action = primaryAction, sizePx = bitmapSizePx)
    }

    // 进度环的**轮廓基准位图**：只有位图类图标才有真实形状可探。
    // 矢量 / 文字图标（没有位图）→ null，由 HudIconSilhouette 退回圆角矩形。
    val slotIconIsRaster = useSlotIcon &&
        (slot.iconSource == QuickWheelIconSource.APP_ICON ||
            slot.iconSource == QuickWheelIconSource.GALLERY)
    val outlineBitmap = when {
        slotIconIsRaster -> remember(slot.iconSource, slot.iconValue, bitmapSizePx) {
            QuickWheelIconResolver.bitmapFor(context, slot, bitmapSizePx)
        }

        useSlotIcon -> null

        else -> actionBitmap
    }

    // 环沿**图标自身轮廓**走（squircle / 圆 / 方 各自贴合），不再固定套一个圆。
    // 路径只在图标或尺寸变化时重建；绘制阶段只描边 + 取段，动画期间不重组。
    val iconPx = with(density) { HudIconSize.toPx() }
    val slotPx = with(density) { HudIconSlot.toPx() }
    val strokePx = with(density) { HudRingStroke.toPx() }
    val ringPath = remember(outlineBitmap, iconPx, slotPx, strokePx) {
        // 轮廓外扩到"外框内边 − 半个描边"：描边中心线正好落在图标外侧一圈。
        val inflate = (slotPx - iconPx) / 2f - strokePx / 2f
        val half = iconPx / 2f + inflate
        val center = slotPx / 2f
        HudIconSilhouette.outlineFor(
            bitmap = outlineBitmap,
            bounds = Rect(
                left = center - half,
                top = center - half,
                right = center + half,
                bottom = center + half,
            ),
        )
    }
    val ringMeasure = remember { PathMeasure() }
    val ringSegment = remember { Path() }

    Column(
        modifier = modifier
            .padding(top = HUD_EDGE_INSET_DP.dp)
            .clip(HudPillShape)
            .background(HudBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标外框比图标大一圈：长按进度就沿图标轮廓画成环。
            Box(
                modifier = Modifier
                    .size(HudIconSlot)
                    .drawBehind {
                        if (!content.showProgress) return@drawBehind
                        // ⚠️ 进度在**绘制阶段**读取：动画期间只触发重绘，不重组。
                        val value = progress.value
                        val stroke = HudRingStroke.toPx()
                        // 底环 = 整条轮廓；进度 = 从正上方起按周长取的一段（PathMeasure）。
                        drawPath(
                            path = ringPath,
                            color = HudRingTrack,
                            style = Stroke(width = stroke),
                        )
                        if (value > 0f) {
                            ringMeasure.setPath(ringPath, forceClosed = true)
                            val total = ringMeasure.length
                            if (total > 0f) {
                                ringSegment.reset()
                                ringMeasure.getSegment(
                                    startDistance = 0f,
                                    stopDistance = total * value.coerceIn(0f, 1f),
                                    destination = ringSegment,
                                    startWithMoveTo = true,
                                )
                                drawPath(
                                    path = ringSegment,
                                    color = HudRingFill,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // ⚠️ 这里**不加白底、不裁剪**：图标直接按自身形状绘制（位图保持系统图标轮廓，
                // 矢量 / 文字图标用浅色着色）。统一走容器那套渲染（QuickWheelSlotIcon）：
                // 打开应用 / 快捷方式显示真实图标，并自动带上与「动作选择器」一致的快捷方式角标。
                QuickWheelSlotIcon(
                    // 「按满」时图标换成长按动作（图标只跟"当前动作"走），角标同理。
                    slot = if (content.primaryIsLongPress) {
                        slot.copy(
                            iconSource = QuickWheelIconSource.NONE,
                            iconValue = "",
                            tapAction = slot.longPressAction,
                        )
                    } else {
                        slot
                    },
                    builtinDp = HudIconGlyphSize,
                    rasterDp = HudIconSize,
                    tint = HudActionGlyph,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = content.primaryText,
                color = HudPrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = HUD_TEXT_MAX_WIDTH_DP.dp),
            )
        }
        content.secondaryText?.let { secondary ->
            Text(
                text = secondary,
                color = HudSecondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = HUD_TEXT_MAX_WIDTH_DP.dp),
            )
        }
    }
}
