package com.slideindex.app.overlay.quickwheel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.launcher.isCloneTarget
import com.slideindex.app.launcher.showsShellActivityShortcutBadge
import com.slideindex.app.overlay.ShellCommandBadgeOverlay
import com.slideindex.app.overlay.ShortcutBadgeOverlay
import com.slideindex.app.overlay.layout.QuickWheelStyleSpec
import com.slideindex.app.settings.QuickWheelIconSource
import com.slideindex.app.settings.QuickWheelSlot
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.theme.LocalAppDarkTheme

/** 「新增容器」的白底（编辑层的加号格，不属于图标底板）。 */
private val ContainerSurface = Color(0xFFFFFFFF)
/** 内置矢量图标的默认线条色（浅色底板上用）。 */
private val ContainerIconTint = Color(0xDE000000)
private val ContainerHighlight = Color(0xFF3478F6)

// ── 图标底板（只给**内置矢量图标**铺）────────────────────────────────────────
// 色值与圆环启动器的底板一致：深色 #303034、浅色 #F1F3F7。
// 深浅取应用主题（`LocalAppDarkTheme`），与全项目的界面配色同源，不再单独判断。
private val PlateDark = Color(0xFF303034)
private val PlateLight = Color(0xFFF1F3F7)
private val IconOnPlateDark = Color(0xFFFFFFFF)
private val IconOnPlateLight = Color(0xDE000000)
private val LabelOnPlateDark = Color(0xCCFFFFFF)
private val LabelOnPlateLight = Color(0xCC000000)
/** 不铺底板时（应用图标 / 图库 / 文字图标）标签的投影，避免贴在壁纸上读不清。 */
private val LabelShadow = Shadow(color = Color(0x73000000), blurRadius = 4f)
/**
 * 用户主动留出的空位（「空白占位」）在**预览 / 配置**里的"空白阴影"外观。
 * 运行时浮层不会绘制这类位置（完全透明），只有预览与配置页看得到这层提示。
 */
private val ContainerPlaceholderSurface = Color(0x14000000)
private val ContainerPlaceholderBorder = Color(0x1F000000)
private val ContainerAddBorder = Color(0x1F000000)
private val ContainerEmptyIcon = Color(0x99000000)
private const val PLUS_STROKE_DP = 1.8f

/**
 * 单个轮盘容器的视觉实现（浮层 / 画布 / 配置页预览复用）。
 *
 * 手势由调用方通过 [modifier] 施加；本组件只负责圆角、图标、文字标签与选中高亮。
 * 图标解析顺序：内置图标库矢量 → 应用图标 / 图库位图 → 回退到该容器单击动作的图标。
 */
@Composable
fun QuickWheelContainer(
    slot: QuickWheelSlot,
    sizePx: Float,
    cornerPx: Float,
    style: QuickWheelStyleSpec,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    scale: Float = 1f,
) {
    val density = LocalDensity.current
    val sizeDp = with(density) { sizePx.toDp() }
    val cornerDp = with(density) { cornerPx.toDp() }
    // 「图标大小」= 基准比例：为文字标签与内置图标提供基准大小（占容器比例）。
    val iconBasePx = sizePx * style.iconSizePercent / 100f
    // 内置图标（矢量图标库 / 动作回退图标 / 文字图标）= 基准 × 内置图标大小系数。
    val builtinIconDp = with(density) { (iconBasePx * style.builtinIconSizePercent / 100f).toDp() }
    // 已安装应用图标 / 图库图片：基准恒为容器的 100%，只受该系数影响。
    val rasterIconDp = with(density) { (sizePx * style.builtinIconSizePercent / 100f).toDp() }
    val labelSize = with(density) {
        (iconBasePx * style.labelSizeFactorPercent / 100f).toSp().value
    }.coerceIn(6f, 16f).sp
    val shape = RoundedCornerShape(cornerDp)

    // placeholder：用户主动留出的空位（预览 / 配置显示"空白阴影"，运行时由 PlacedContainer 跳过）。
    // 其余容器一律是真实容器——哪怕点击 / 长按都没设动作，也渲染成"空图标"。
    val isEmptyGap = !slot.isConfigured && slot.placeholder
    // 内置矢量图标（图标库 / 内置动作 / 文字图标 / 名称首字母）**始终**铺底板；应用图标 /
    // 图库图片这类用户提供的位图由 [QuickWheelStyleSpec.transparentUserIcons] 决定：
    // 开启后不铺底色、不裁切，拟物类异形图标才能保持原样。
    // 底板的深浅跟着应用主题走，与全项目的界面配色同源。
    val plated = !isEmptyGap && (slot.usesBuiltinVectorPlate() || !style.transparentUserIcons)
    val darkPlate = LocalAppDarkTheme.current
    val plateColor = if (darkPlate) PlateDark else PlateLight
    val iconTint = if (darkPlate) IconOnPlateDark else IconOnPlateLight
    val labelColor = if (darkPlate) LabelOnPlateDark else LabelOnPlateLight
    Box(
        modifier = modifier
            .size(sizeDp * scale)
            .then(
                when {
                    // 「空白占位」的"空白阴影"外观：只在预览 / 配置里可见；
                    // 真实呼出时由 PlacedContainer 直接跳过绘制，此处只是一个全透明占位。
                    isEmptyGap -> Modifier
                        .shadow(elevation = 2.dp, shape = shape)
                        .clip(shape)
                        .background(ContainerPlaceholderSurface)
                        .border(width = 1.dp, color = ContainerPlaceholderBorder, shape = shape)

                    plated -> Modifier
                        .shadow(elevation = if (highlighted) 10.dp else 4.dp, shape = shape)
                        .clip(shape)
                        .background(plateColor)
                        // 普通态不再描边：图标贴满时那 1dp 会压在图标边缘上，看着像图标自带一圈边框。
                        // 只保留"高亮"（拖动 / 悬停）时的 2dp 蓝框作为选中提示。
                        .then(
                            if (highlighted) {
                                Modifier.border(2.dp, ContainerHighlight, shape)
                            } else {
                                Modifier
                            },
                        )

                    // 无底板：不铺任何底色、也不裁切图标（拟物图标包的透明边角要原样保留），
                    // 只在选中时画一圈描边。阴影也不加——透明方块投出的圆角阴影很脏。
                    else -> if (highlighted) {
                        Modifier.border(2.dp, ContainerHighlight, shape)
                    } else {
                        Modifier
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isEmptyGap) {
            return@Box
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // ⚠️ 这里**不能**加水平内边距：图标一旦被挤窄，`ContentScale.Fit` 会等比缩小，
            // 容器白底就会在四周露出一圈白边（应用图标圆角与容器圆角一致时尤其明显）。
            // 文字标签要留边距的话，加在文字自己身上即可。
            modifier = Modifier.fillMaxSize(),
        ) {
            // 「A 方案」：快捷方式角标的最外缘会探出图标本体，而容器带圆角裁剪 ——
            // 由几何解出"角标完整落在圆角内"的图标上限，超了就等比缩小图标：
            //   角标最外缘 = 0.6158 × 图标 + 1.5dp 白边
            //   圆角边界（沿 45°）= √2 × (半容器 − 圆角) + 圆角
            // 只有铺底板（带圆角裁剪）时才需要让位；无底板的容器不裁切，角标不会被切。
            val badgeShrink = if (plated && slot.tapAction is GestureAction.LaunchShortcut) {
                val boundaryPx = (sizePx / 2f - cornerPx) * 1.4142135f + cornerPx
                val maxIconPx = (boundaryPx - 1.5f * density.density) / 0.6158f
                (maxIconPx / sizePx).coerceIn(0.6f, 1f)
            } else {
                1f
            }
            QuickWheelSlotIcon(
                slot = slot,
                builtinDp = builtinIconDp * badgeShrink,
                rasterDp = rasterIconDp * badgeShrink,
                // 矢量 / 文字图标跟底板反色（深底白线、浅底黑线）；位图图标不受 tint 影响。
                tint = iconTint,
            )
            if (style.showLabels && slot.name.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = slot.name,
                    // 有底板时跟底板反色；无底板时贴在壁纸上，用白色 + 投影保证可读。
                    color = if (plated) labelColor else Color.White,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = if (plated) TextStyle.Default else TextStyle(shadow = LabelShadow),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/**
 * 该容器的图标是不是**内置绘制**（矢量 / 文字）—— 这类容器**始终铺底板**，不受"底色全透明"开关影响。
 *
 * - 铺（不受开关影响）：内置图标库的矢量、内置动作的矢量图标（返回 / 主页 / 截图……）、
 *   文字图标、名称首字母 —— 都是我们自己画的细线 / 字符，没有底板在壁纸上会读不清；
 * - 受开关控制：应用图标、图库图片 —— 用户提供的位图，拟物类图标包是异形 + 透明边角，
 *   铺方形底板会从轮廓外露出底色。
 *
 * ⚠️ 只看「图标来源 + 动作类型」，**不去解析位图**（解析要读磁盘、也可能失败退回矢量）。
 * 因此「打开应用 / 快捷方式 / 宏」即使位图没取到、退回矢量，也按"用户选的是应用图标"处理。
 */
internal fun QuickWheelSlot.usesBuiltinVectorPlate(): Boolean = when (iconSource) {
    QuickWheelIconSource.ICON_LIBRARY,
    QuickWheelIconSource.TEXT,
    -> true
    QuickWheelIconSource.APP_ICON,
    QuickWheelIconSource.GALLERY,
    -> false
    QuickWheelIconSource.NONE -> when (tapAction) {
        is GestureAction.LaunchApp,
        is GestureAction.LaunchShortcut,
        is GestureAction.RunMacro,
        -> false
        // 其余内置动作 → 矢量图标；没有动作时由名称首字母兜底（同样是内置绘制）。
        else -> tapAction.type != GestureActionType.NONE || name.isNotBlank()
    }
}

/**
 * 编辑层专用的「新增容器」：白底 + 细线加号。
 *
 * 轮盘（浮层 / 预览 / 画布）末尾**只会出现一个**，点它进入空白容器编辑页；
 * 保存后轮盘多一个容器，它自动顺延到新的末尾。
 */
@Composable
fun QuickWheelAddContainer(
    sizePx: Float,
    cornerPx: Float,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val density = LocalDensity.current
    val sizeDp = with(density) { sizePx.toDp() }
    val cornerDp = with(density) { cornerPx.toDp() }
    val iconSizeDp = with(density) { (sizePx * 0.4f).toDp() }
    val shape = RoundedCornerShape(cornerDp)

    Box(
        modifier = modifier
            .size(sizeDp)
            .shadow(elevation = if (highlighted) 10.dp else 4.dp, shape = shape)
            .clip(shape)
            .background(ContainerSurface)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) ContainerHighlight else ContainerAddBorder,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ThinPlusIcon(
            sizeDp = iconSizeDp,
            color = ContainerEmptyIcon,
            strokeDp = PLUS_STROKE_DP,
        )
    }
}

/**
 * 单个槽位的图标（浮层容器 / 预览 / 配置页 / 编辑层 / HUD 复用）。
 *
 * 图标本体外包一层 `Box`，用于叠放**动作角标**（快捷方式 / Shell 直达）：角标与图标同尺寸、
 * 由 [QuickWheelIconBadge] 按比例画在右下角，与「动作选择器」列表同一套外观 ——
 * 这样在轮盘、配置页预览、编辑层、HUD 上都能一眼看出"启动应用"还是"启动某个快捷方式"。
 *
 * @param builtinDp 矢量 / 文字图标边长（受「内置图标大小系数」影响）
 * @param rasterDp 位图（应用图标 / 图库 / 动作真实图标）边长
 * @param tint 矢量 / 文字图标颜色。默认深色（白底容器上用）；深色背景（HUD）传浅色即可，
 *   位图图标不受 tint 影响
 * @param badgeAction 角标依据的动作；默认 = 单击动作（图标只跟单击动作走；HUD 在「按满」时传长按动作）
 */
@Composable
fun QuickWheelSlotIcon(
    slot: QuickWheelSlot,
    builtinDp: Dp,
    rasterDp: Dp,
    tint: Color = ContainerIconTint,
    badgeAction: GestureAction = slot.tapAction,
) {
    val context = LocalContext.current
    val targetPx = with(LocalDensity.current) { rasterDp.toPx() }.toInt()

    // 读 revision：图标库里改完应用图标，轮盘里的槽位要立刻换图
    val iconRevision = com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconCacheRevision?.invoke().orEmpty()

    val libraryVector = if (slot.iconSource == QuickWheelIconSource.ICON_LIBRARY) {
        QuickWheelIconResolver.vectorFor(slot.iconValue)
    } else {
        null
    }
    val bitmap = when (slot.iconSource) {
        QuickWheelIconSource.APP_ICON, QuickWheelIconSource.GALLERY ->
            remember(slot, targetPx, iconRevision) {
                QuickWheelIconResolver.bitmapFor(context, slot, targetPx)
            }
        else -> null
    }

    // ⚠️ 只回退到「单击动作」：容器图标必须**只受单击动作影响**，改长按动作不得改变图标，
    // 也不应该被长按动作"代持"。代价：只设了长按动作、又没名称 / 手动图标的容器，
    // 会落到下面的"名称首字母 → 全白空图标"两级兜底。
    val fallbackAction = slot.tapAction.takeIf { it.type != GestureActionType.NONE }

    // 动作的**真实图标**（打开应用 → 应用图标；快捷方式 → 快捷方式自身图标）：
    // 与「动作选择器」列表里显示的一致。只在容器自身没配图标、且单击动作存在时解析
    //（配了图标就以用户的为准）。取不到才退回下面的矢量动作图标。
    val actionBitmap = if (slot.iconSource == QuickWheelIconSource.NONE && fallbackAction != null) {
        remember(fallbackAction, targetPx, iconRevision) {
            QuickWheelIconResolver.actionBitmapFor(context, fallbackAction, targetPx)
        }
    } else {
        null
    }

    Box(contentAlignment = Alignment.Center) {
        when {
            // 文字图标：把用户输入的文字直接当图标绘制（内置类，走 builtin 尺寸）。
            slot.iconSource == QuickWheelIconSource.TEXT && slot.iconValue.isNotBlank() -> Text(
                text = slot.iconValue,
                color = tint,
                fontSize = (builtinDp.value * 0.62f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 内置矢量图标：基准 × 内置图标大小系数。
            libraryVector != null -> Icon(
                imageVector = libraryVector,
                contentDescription = slot.name.ifBlank { null },
                tint = tint,
                modifier = Modifier.size(builtinDp),
            )

            // 已安装应用图标 / 图库图片：基准恒为容器 100%，只受图标大小系数影响。
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = slot.name.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(rasterDp),
            )

            // 动作的真实图标优先于矢量动作图标（打开应用 / 快捷方式）。
            actionBitmap != null -> Image(
                bitmap = actionBitmap,
                contentDescription = slot.name.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(rasterDp),
            )

            fallbackAction != null ->
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.actionIconSlot
                    ?.content(fallbackAction, builtinDp.value.toInt())

            slot.name.isNotBlank() -> Text(
                text = slot.name.take(1),
                color = tint,
                fontSize = (builtinDp.value * 0.9f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )

            // 点击 / 长按都没设动作、也没有名称与图标：渲染成"全白空图标"（白底容器上什么都不画）。
            else -> Unit
        }

        // 角标必须与图标本体**同尺寸**叠放（位图分支按 rasterDp、矢量 / 文字分支按 builtinDp），
        // 否则位置对不上：角标的位置/大小是按"图标尺寸"的比例算出来的。
        QuickWheelIconBadge(
            action = badgeAction,
            iconSize = if (bitmap != null || actionBitmap != null) rasterDp else builtinDp,
        )
        // 分身黄点：绘制期叠在图标上，避免被容器圆形裁剪（烘进位图会掉一半）
        if (fallbackAction?.isCloneTarget(context) == true) {
            com.slideindex.app.overlay.CloneBadgeOverlay(
                iconSize = if (bitmap != null || actionBitmap != null) rasterDp else builtinDp,
                circular = true,
            )
        }
    }
}

/**
 * 动作角标：与「动作选择器」列表**同一套规则与外观**
 * （[ShortcutBadgeOverlay] / [ShellCommandBadgeOverlay] 同一位置与比例）。
 *
 * - 快捷方式 → 蓝色快捷方式角标；
 * - 「应用内直达」(cebianshell) 快捷方式 → Shell 角标；
 * - 其它动作（含打开应用）→ 不画，让"有无角标"本身成为"快捷方式"的判据（与列表一致）。
 *
 * ⚠️ 角标最外缘会探出图标本体（偏移 0.34×直径 + 半径 0.135×直径 + 1.5dp 白边）；
 * 带圆角裁剪的容器（`QuickWheelContainer`）为此会等比缩小图标，见那边的说明。
 */
@Composable
fun QuickWheelIconBadge(action: GestureAction, iconSize: Dp) {
    // 「运行宏」格子显示目标 App 图标：加宏角标，和「打开应用」区分
    if (action is GestureAction.RunMacro) {
        com.slideindex.app.overlay.MacroBadgeOverlay(iconSize = iconSize)
        return
    }
    val shortcut = action as? GestureAction.LaunchShortcut ?: return
    val context = LocalContext.current
    val isShell = remember(shortcut) {
        shortcut.showsShellActivityShortcutBadge(
            QuickWheelIconResolver.activityShortcuts(context),
        )
    }
    if (isShell) {
        ShellCommandBadgeOverlay(iconSize = iconSize)
    } else {
        ShortcutBadgeOverlay(iconSize = iconSize)
    }
}

/** 细线「+」：参考实现里的新增容器图标。 */
@Composable
private fun ThinPlusIcon(sizeDp: Dp, color: Color, strokeDp: Float) {
    val strokePx = with(LocalDensity.current) { strokeDp.dp.toPx() }
    Canvas(modifier = Modifier.size(sizeDp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val arm = size.minDimension / 2f * 0.62f
        drawLine(
            color = color,
            start = Offset(centerX - arm, centerY),
            end = Offset(centerX + arm, centerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, centerY - arm),
            end = Offset(centerX, centerY + arm),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}
