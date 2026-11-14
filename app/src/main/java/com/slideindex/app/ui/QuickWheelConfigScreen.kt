package com.slideindex.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelOpenAnimation
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.overlay.layout.QuickWheelStyleSpec
import com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.settings.QuickWheelCodec
import com.slideindex.app.settings.withSectorToggled
import com.slideindex.app.ui.miuix.MiuixSettingsScreenScaffold
import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class QuickWheelStyleLevel { PRIMARY, SECONDARY }

/**
 * 调二级外观参数时，圆形样本二级的容器个数。
 *
 * 取 6：格位是从跨度起点开始排的，6 个正好关于水平轴对称（扇形），最容易看清半径 / 尺寸 /
 * 圆角 / 间距；取 4 会挤在一侧，取 12 则铺满整圈、偏拥挤。
 */
private const val PREVIEW_SECONDARY_CIRCLE_SLOTS = 6

/** 外观参数（形态 / 样式）控件被触摸时的回调，由配置页注入。 */
private val LocalQuickWheelAppearanceInteract = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 配置页草稿的持久化器。
 *
 * 必须能被保存：跳到「容器编辑子页」再返回时页面重组，普通 remember 会重建，
 * 不保存就会把刚调好的参数丢掉。
 *
 * 只存"本页可改的元数据"（名称 / 长按时间 / 形态 / 扇区 / 两套样式 / 背景模糊与遮罩 /
 * 呼出动画）——正好是 [QuickWheelCodec.encodeEntry] 的口径；容器相关字段不参与草稿，
 * 所以不需要存 slots。
 */
private val QuickWheelDraftSaver: Saver<QuickWheel, String> = Saver(
    save = { wheel -> QuickWheelCodec.encodeEntry(wheel) },
    restore = { raw -> QuickWheelCodec.decodeEntry(raw) },
)

/**
 * 页面 2：轮盘配置页。
 *
 * - 不预留常驻预览区；**只有开始调整外观参数时才浮出实时预览**，点轮盘外的空白区即收起
 *   （预览层吞掉触摸，不会穿透到底下的设置项）
 *   （该次按下仍照常操作下方控件，不会被打断）；
 * - 「轮盘预览」以真实浮层呼出（可执行动作试玩）；
 * - 「轮盘功能设置」以悬浮编辑层叠加在本页之上；
 * - **草稿模式**：本页改的字段（名称 / 长按时间 / 形态 / 扇区 / 两套样式）先只进 [draft]，
 *   右上「保存」才写回设置；带着未保存改动返回会先弹窗确认（保存后退出 / 直接退出）；
 * - 容器相关（编辑层增删排序、容器编辑子页）仍**立即写回**设置，因此 [effective] 的
 *   `slots` / `centerSlot` 永远取设置里的最新值——否则从容器编辑页返回时旧草稿会把
 *   那边刚保存的改动覆盖掉（同步 / 交换会"看似无效"）。
 * - 系统返回按「确认弹窗 → 编辑层 → 实时预览 → 轮盘预览浮层 → 未保存确认 → 退出本页」逐层处理。
 */
@Composable
fun QuickWheelConfigScreen(
    settingsWheel: QuickWheel,
    onBack: () -> Unit,
    onPatch: (QuickWheel) -> Unit,
    onPreview: (QuickWheel) -> Unit,
    onOpenSlot: (String) -> Unit,
    /**
     * 本次会话里刚新建、还没保存过的轮盘。
     *
     * 把"未保存"直接算进 `dirty`：即使一个字没改，退出也会先问"保存后退出 / 直接退出"，
     * 而不是静默丢弃（未保存退出时由导航层把这个轮盘删掉）。
     */
    isNewWheel: Boolean = false,
) {
    // 草稿：只承载本页可改的字段；容器字段一律忽略（见上方注释），不参与草稿。
    // 用 rememberSaveable：容器编辑子页往返后仍保留未保存的改动（见 QuickWheelDraftSaver）。
    var draft by rememberSaveable(settingsWheel.id, stateSaver = QuickWheelDraftSaver) {
        mutableStateOf(settingsWheel)
    }
    // 设置最新值 + 本页草稿。normalized() 让 primaryStyle / secondaryStyle 始终等于
    // "当前形态"那一套，切换形态后下方参数项立刻正确（否则会短暂沿用另一形态的样式）。
    val effective = remember(settingsWheel, draft) {
        settingsWheel.copy(
            name = draft.name,
            longPressMs = draft.longPressMs,
            // ⚠️ 背景模糊 / 遮罩同样属于本页草稿：不并进来的话这两个滑条会"拖不动"
            // （Slider 无状态，value 一直读设置里的旧值，拖动时被逐帧弹回），
            // 并且 dirty 检测不到、保存时也会被静默丢掉。
            backdropBlurDp = draft.backdropBlurDp,
            backdropDimPercent = draft.backdropDimPercent,
            // ⚠️ 动画效果 / 速度同理：不并进 effective 的话下拉与滑条都会"点了没反应、拖动被弹回"，
            // dirty 检测不到 → 保存时被静默丢掉。
            openAnimation = draft.openAnimation,
            animationSpeedPercent = draft.animationSpeedPercent,
            primaryShape = draft.primaryShape,
            secondaryShape = draft.secondaryShape,
            sectorMask = draft.sectorMask,
            primaryStyle = draft.primaryStyle,
            secondaryStyle = draft.secondaryStyle,
            primaryCircleStyle = draft.primaryCircleStyle,
            primaryRectStyle = draft.primaryRectStyle,
            secondaryCircleStyle = draft.secondaryCircleStyle,
            secondaryRectStyle = draft.secondaryRectStyle,
        ).normalized()
    }
    // ⚠️ 本页后续一律用 `wheel`（= 设置最新值 + 本页草稿），避免到处写 effective。
    val wheel = effective
    val normalizedSettings = remember(settingsWheel) { settingsWheel.normalized() }
    // 有未保存的改动。用 normalized() 比较，避免编解码规范化造成"永远显示未保存"。
    // 新建的轮盘本身就算"待保存"：一个字没改，退出也要先问（由导航层在未保存退出时删除它）。
    val dirty = isNewWheel || effective != normalizedSettings
    var showLeaveDialog by remember { mutableStateOf(false) }

    var nameInput by remember(settingsWheel.id) { mutableStateOf(settingsWheel.name) }
    var styleLevel by remember { mutableStateOf(QuickWheelStyleLevel.PRIMARY) }
    var editMode by rememberSaveable(settingsWheel.id) { mutableStateOf(false) }
    var previewVisible by remember(settingsWheel.id) { mutableStateOf(false) }
    // 页内实时预览的展开动画：预览每次浮出、或切换动画效果时播一遍。
    // ⚠️ 以"可见性 / 效果"为 key 重建、初值直接给 0：不会先按最终位置画一帧再跳回起点。
    // ⚠️ speed **不**参与 key：拖着速度滑条时每一档都从 0 重播反而会一直抖；
    // 想感受新速度请看真机呼出，或「轮盘预览」浮窗（每次打开都会按当前速度播一遍）。
    val previewAppear = remember(previewVisible, wheel.openAnimation) {
        Animatable(
            if (previewVisible && wheel.openAnimation != QuickWheelOpenAnimation.NONE) 0f else 1f,
        )
    }
    LaunchedEffect(previewAppear) {
        if (previewAppear.value < 1f) {
            previewAppear.animateTo(
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
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }

    /** 改动先写进草稿（不落盘）。 */
    fun patchDraft(transform: (QuickWheel) -> QuickWheel) {
        draft = transform(draft)
    }

    /** 右上「保存」：草稿写回设置（此后草稿与设置一致，未保存标记自动消除）。 */
    fun saveDraft() {
        onPatch(effective)
        draft = effective
    }

    /**
     * 系统返回 / 顶栏返回：按层级逐层处理。
     *
     * 先收浮层（确认弹窗 → 编辑层 → 实时预览 → 轮盘预览浮层），再在"有未保存改动"时询问，
     * 最后才真正退出本页——避免返回时把正在看的轮盘浮层一起带走。
     */
    fun handleBack() {
        when {
            showLeaveDialog -> showLeaveDialog = false
            editMode -> editMode = false
            previewVisible -> previewVisible = false
            // 「轮盘预览」是独立浮层窗口：点空白能收，返回键也要能收。
            QuickWheelOverlayWindow.isShowing -> QuickWheelOverlayWindow.dismiss()
            dirty -> showLeaveDialog = true
            else -> onBack()
        }
    }

    BackHandler { handleBack() }

    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val density = if (metrics.density > 0f) metrics.density else 1f
    val screenWidthPx = metrics.widthPixels.toFloat()
    val screenHeightPx = metrics.heightPixels.toFloat()

    // 扇区不再做展示归一化：选了什么扇区，编辑层 / 预览 / 列表就显示什么（WYSIWYG）。
    val previewWheel = wheel
    // 预览锚点：圆形按（归一化后的）扇区反推理论位置（选左半圆 → 出现在右边缘），矩形用屏幕正中；
    // 再扣掉本页在窗口中的偏移，保证「轮盘预览浮层」与「调参实时预览」位置一致。
    // 预览锚点（屏幕坐标）：编辑层也用同一个 → 两处的"形状 + 位置"完全一致。
    val previewAnchor = remember(previewWheel, density, screenWidthPx, screenHeightPx) {
        quickWheelPreviewAnchor(
            wheel = previewWheel,
            density = density,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    }
    // 正在调**二级**外观参数时：实时预览里放一组"样本二级容器"，参数一动就能看到效果。
    // 父容器优先取"没有子容器的格位"（不掩盖真实二级、也不会引起误解）；样本同样走
    // buildQuickWheelLayout 内的"二级按屏幕求解"，因此预览口径与真机一致。
    val previewSecondaryParent = if (styleLevel == QuickWheelStyleLevel.SECONDARY) {
        QuickWheelLayoutEngine.pickPreviewSecondaryParentIndex(
            hasSubSlots = wheel.slots.map { it.subSlots.isNotEmpty() },
        )
    } else {
        -1
    }
    val previewSecondarySlots = if (previewSecondaryParent >= 0) {
        when (wheel.secondaryShape) {
            QuickWheelShape.CIRCLE -> PREVIEW_SECONDARY_CIRCLE_SLOTS
            // 矩形：2 行（按列数）→ 行距与列距都能看清。
            QuickWheelShape.RECT -> wheel.secondaryStyle.columnCount * 2
        }
    } else {
        null
    }
    val previewLayout = remember(
        previewWheel,
        previewAnchor,
        hostOrigin,
        density,
        screenWidthPx,
        screenHeightPx,
        previewSecondaryParent,
        previewSecondarySlots,
    ) {
        buildQuickWheelLayout(
            wheel = previewWheel,
            expandedPrimaryIndex = previewSecondaryParent,
            anchorX = previewAnchor.first - hostOrigin.x,
            anchorY = previewAnchor.second - hostOrigin.y,
            density = density,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            screenOriginX = hostOrigin.x,
            screenOriginY = hostOrigin.y,
            previewSecondarySlots = previewSecondarySlots,
        )
    }

    val primaryLabel = stringResource(R.string.quick_wheel_level_primary)
    val secondaryLabel = stringResource(R.string.quick_wheel_level_secondary)
    val levelStyle = when (styleLevel) {
        QuickWheelStyleLevel.PRIMARY -> wheel.primaryStyle
        QuickWheelStyleLevel.SECONDARY -> wheel.secondaryStyle
    }
    // 当前正在配置的那一级的形态：决定下方显示哪一组专属参数。
    val levelShape = when (styleLevel) {
        QuickWheelStyleLevel.PRIMARY -> wheel.primaryShape
        QuickWheelStyleLevel.SECONDARY -> wheel.secondaryShape
    }

    // settingsLazySmallTitle 接收普通 String（非 @Composable），需先在此处解析。
    val sectionBasicTitle = stringResource(R.string.quick_wheel_section_basic)
    val sectionShapeTitle = stringResource(R.string.quick_wheel_section_shape)
    val sectionAnimationTitle = stringResource(R.string.quick_wheel_animation)
    val sectionStyleTitle = stringResource(R.string.quick_wheel_section_appearance)

    fun updateStyle(transform: (QuickWheelStyleSpec) -> QuickWheelStyleSpec) {
        // 当前形态对应的那套要同步写入"形态专属字段"，两套参数才能同时保存下来。
        patchDraft { current ->
            when (styleLevel) {
                QuickWheelStyleLevel.PRIMARY -> {
                    val updated = transform(current.primaryStyle)
                    current.copy(
                        primaryStyle = updated,
                        primaryCircleStyle = if (current.primaryShape == QuickWheelShape.CIRCLE) {
                            updated
                        } else {
                            current.primaryCircleStyle
                        },
                        primaryRectStyle = if (current.primaryShape == QuickWheelShape.RECT) {
                            updated
                        } else {
                            current.primaryRectStyle
                        },
                    )
                }

                QuickWheelStyleLevel.SECONDARY -> {
                    val updated = transform(current.secondaryStyle)
                    current.copy(
                        secondaryStyle = updated,
                        secondaryCircleStyle = if (current.secondaryShape == QuickWheelShape.CIRCLE) {
                            updated
                        } else {
                            current.secondaryCircleStyle
                        },
                        secondaryRectStyle = if (current.secondaryShape == QuickWheelShape.RECT) {
                            updated
                        } else {
                            current.secondaryRectStyle
                        },
                    )
                }
            }
        }
    }

    // 预览只由「形态 / 外观参数真的被改动」驱动：触摸、点击、滚动都不会再触发。
    // 用样式快照对比实现——拖滑条、切下拉、拨开关、点扇区都会改变它。
    val styleSnapshot = listOf(
        wheel.primaryShape,
        wheel.secondaryShape,
        wheel.sectorMask,
        // 「正在调哪一级」也算：切到二级立刻浮出带"样本二级"的预览，不必先拖一下滑条。
        styleLevel,
        // 背景模糊 / 遮罩也算"外观参数"：拖它们同样要浮出实时预览（遮罩页内可见）。
        wheel.backdropBlurDp,
        wheel.backdropDimPercent,
        wheel.primaryStyle.toString(),
        wheel.secondaryStyle.toString(),
    ).toString()
    var lastStyleSnapshot by remember(wheel.id) { mutableStateOf(styleSnapshot) }
    LaunchedEffect(styleSnapshot) {
        if (styleSnapshot != lastStyleSnapshot) {
            lastStyleSnapshot = styleSnapshot
            previewVisible = true
        }
    }

    CompositionLocalProvider(
        // 不再"触摸即预览"：这里留空，仅作为控件内部的占位回调。
        LocalQuickWheelAppearanceInteract provides { },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { hostOrigin = it.positionInWindow() },
        ) {
            MiuixSettingsScreenScaffold(
                title = stringResource(R.string.quick_wheel_config_title),
                // 实时预览"背景模糊"：页内做不了跨窗口模糊，这里用 Compose blur 近似，
                // 让拖动立刻有反馈（真实效果请看「轮盘预览」）。浮层画在 scaffold 之后，不会被糊。
                // ⚠️ 必须走 previewBlurSigmaDp：Compose 的 radius 是 sigma，系统的是半径，直接传会糊一倍。
                modifier = Modifier.blur(
                    radius = if (previewVisible) {
                        previewBlurSigmaDp(previewWheel.backdropBlurDp, density).dp
                    } else {
                        0.dp
                    },
                ),
                onBack = { handleBack() },
                actions = {
                    // 右上「保存」：文字按钮更醒目；有未保存改动时用强调色点亮，无改动时置灰。
                    TextButton(
                        onClick = { saveDraft() },
                        enabled = dirty,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = QuickWheelAccent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.38f),
                        ),
                    ) {
                        Text(stringResource(R.string.quick_wheel_save))
                    }
                },
            ) {
                settingsLazySmallTitle(key = "quick_wheel_basic", title = sectionBasicTitle)
                groupedCardItems(
                    keyPrefix = "quick_wheel_basic",
                    items = buildList {
                        add(
                            settingsCardScopeItem("name") {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { value ->
                                        nameInput = value
                                        patchDraft { it.copy(name = value) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.quick_wheel_name_hint)) },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("long-press-time") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_long_press_time),
                                    description = stringResource(R.string.quick_wheel_long_press_time_desc),
                                    value = wheel.longPressMs.toFloat(),
                                    range = QuickWheelLayoutEngine.MIN_LONG_PRESS_MS.toFloat()..
                                        QuickWheelLayoutEngine.MAX_LONG_PRESS_MS.toFloat(),
                                    unit = "ms",
                                    onValueChange = { value ->
                                        patchDraft { it.copy(longPressMs = value.toInt()) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("preview") {
                                QuickWheelNavRow(
                                    title = stringResource(R.string.quick_wheel_preview),
                                    description = stringResource(R.string.quick_wheel_preview_desc),
                                    onClick = { onPreview(wheel) },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("function") {
                                QuickWheelNavRow(
                                    title = stringResource(R.string.quick_wheel_function),
                                    description = stringResource(R.string.quick_wheel_function_desc),
                                    onClick = { editMode = true },
                                )
                            }
                        )
                    },
                )

                settingsLazySmallTitle(key = "quick_wheel_shape", title = sectionShapeTitle)
                groupedCardItems(
                    keyPrefix = "quick_wheel_shape",
                    items = buildList {
                        add(
                            settingsCardScopeItem("primary-shape") {
                                QuickWheelDropdownRow(
                                    title = stringResource(R.string.quick_wheel_primary_shape),
                                    description = stringResource(
                                        if (wheel.primaryShape == QuickWheelShape.CIRCLE) {
                                            R.string.quick_wheel_shape_circle_desc
                                        } else {
                                            R.string.quick_wheel_shape_rect_desc
                                        },
                                    ),
                                    value = wheel.primaryShape,
                                    options = QuickWheelShape.entries.toList(),
                                    labelOf = { quickWheelShapeLabel(it) },
                                    onSelect = { shape -> patchDraft { it.copy(primaryShape = shape) } },
                                )
                            }
                        )
                        if (wheel.primaryShape == QuickWheelShape.CIRCLE) {
                            add(
                                settingsCardScopeItem("sector-ring") {
                                    QuickWheelSectorRow(
                                        sectorMask = wheel.sectorMask,
                                        onToggleSector = { index ->
                                            patchDraft { it.withSectorToggled(index) }
                                        },
                                    )
                                }
                            )
                        }
                        add(
                            settingsCardScopeItem("secondary-shape") {
                                QuickWheelDropdownRow(
                                    title = stringResource(R.string.quick_wheel_secondary_shape),
                                    description = stringResource(
                                        if (wheel.secondaryShape == QuickWheelShape.CIRCLE) {
                                            R.string.quick_wheel_shape_circle_desc
                                        } else {
                                            R.string.quick_wheel_shape_rect_desc
                                        },
                                    ),
                                    value = wheel.secondaryShape,
                                    options = QuickWheelShape.entries.toList(),
                                    labelOf = { quickWheelShapeLabel(it) },
                                    onSelect = { shape -> patchDraft { it.copy(secondaryShape = shape) } },
                                )
                            }
                        )
                    },
                )

                settingsLazySmallTitle(
                    key = "quick_wheel_animation",
                    title = sectionAnimationTitle,
                )
                groupedCardItems(
                    keyPrefix = "quick_wheel_animation",
                    items = buildList {
                        add(
                            settingsCardScopeItem("open-animation") {
                                QuickWheelDropdownRow(
                                    title = stringResource(R.string.quick_wheel_open_animation),
                                    description = stringResource(
                                        R.string.quick_wheel_open_animation_desc,
                                    ),
                                    value = wheel.openAnimation,
                                    options = QuickWheelOpenAnimation.entries.toList(),
                                    labelOf = { animation ->
                                        stringResource(
                                            when (animation) {
                                                QuickWheelOpenAnimation.NONE ->
                                                    R.string.quick_wheel_open_animation_none

                                                QuickWheelOpenAnimation.EXPAND_FROM_CENTER ->
                                                    R.string.quick_wheel_open_animation_expand
                                            },
                                        )
                                    },
                                    onSelect = { animation ->
                                        patchDraft { it.copy(openAnimation = animation) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("animation-speed") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_animation_speed),
                                    description = stringResource(
                                        R.string.quick_wheel_animation_speed_desc,
                                    ),
                                    value = wheel.animationSpeedPercent.toFloat(),
                                    range = QuickWheelLayoutEngine.MIN_ANIMATION_SPEED_PERCENT
                                        .toFloat()..
                                        QuickWheelLayoutEngine.MAX_ANIMATION_SPEED_PERCENT
                                            .toFloat(),
                                    unit = "%",
                                    onValueChange = { value ->
                                        patchDraft {
                                            it.copy(animationSpeedPercent = value.toInt())
                                        }
                                    },
                                )
                            }
                        )
                    },
                )

                settingsLazySmallTitle(key = "quick_wheel_style", title = sectionStyleTitle)
                groupedCardItems(
                    keyPrefix = "quick_wheel_style",
                    items = buildList {
                        add(
                            settingsCardScopeItem("style-level") {
                                QuickWheelDropdownRow(
                                    title = stringResource(R.string.quick_wheel_style_level),
                                    description = stringResource(
                                        R.string.quick_wheel_style_level_desc,
                                        if (styleLevel == QuickWheelStyleLevel.PRIMARY) {
                                            primaryLabel
                                        } else {
                                            secondaryLabel
                                        },
                                    ),
                                    value = styleLevel,
                                    options = QuickWheelStyleLevel.entries.toList(),
                                    labelOf = {
                                        if (it == QuickWheelStyleLevel.PRIMARY) {
                                            primaryLabel
                                        } else {
                                            secondaryLabel
                                        }
                                    },
                                    onSelect = { styleLevel = it },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("show-labels") {
                                QuickWheelSwitchRow(
                                    title = stringResource(R.string.quick_wheel_show_labels),
                                    description = stringResource(R.string.quick_wheel_show_labels_desc),
                                    checked = levelStyle.showLabels,
                                    onCheckedChange = { value ->
                                        updateStyle { it.copy(showLabels = value) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("transparent-user-icons") {
                                QuickWheelSwitchRow(
                                    title = stringResource(R.string.quick_wheel_transparent_container),
                                    description = stringResource(
                                        R.string.quick_wheel_transparent_container_desc,
                                    ),
                                    checked = levelStyle.transparentUserIcons,
                                    // 一个开关管全轮盘：一级 / 二级、圆形 / 矩形四套样式一起写，
                                    // 避免"一级透明、二级还铺着底"这种半吊子状态。
                                    onCheckedChange = { value ->
                                        patchDraft { current ->
                                            current.copy(
                                                primaryCircleStyle = current.primaryCircleStyle
                                                    .copy(transparentUserIcons = value),
                                                primaryRectStyle = current.primaryRectStyle
                                                    .copy(transparentUserIcons = value),
                                                secondaryCircleStyle = current.secondaryCircleStyle
                                                    .copy(transparentUserIcons = value),
                                                secondaryRectStyle = current.secondaryRectStyle
                                                    .copy(transparentUserIcons = value),
                                            )
                                        }
                                    },
                                )
                            }
                        )
                        // 矩形形态专属：每行容器列数（2–10），超出自动换行。
                        if (levelShape == QuickWheelShape.RECT) {
                            add(
                                settingsCardScopeItem("column-count") {
                                    QuickWheelIntRow(
                                        title = stringResource(R.string.quick_wheel_column_count),
                                        description = stringResource(R.string.quick_wheel_column_count_desc),
                                        value = levelStyle.columnCount,
                                        range = QuickWheelLayoutEngine.MIN_COLUMN_COUNT..
                                            QuickWheelLayoutEngine.MAX_COLUMN_COUNT,
                                        onValueChange = { value ->
                                            updateStyle { it.copy(columnCount = value) }
                                        },
                                    )
                                }
                            )
                        }
                        // 圆形形态专属：初始半径（矩形形态由列数决定，不使用本项）。
                        if (levelShape == QuickWheelShape.CIRCLE) {
                            add(
                                settingsCardScopeItem("initial-radius") {
                                    QuickWheelSliderRow(
                                        title = stringResource(R.string.quick_wheel_initial_radius),
                                        description = stringResource(R.string.quick_wheel_initial_radius_desc),
                                        value = levelStyle.initialRadiusDp,
                                        // 下限按级别取：二级锚点 = 父容器中心（不画中心大圆），
                                        // 下限只需"不压住父容器"，比一级的 60 小得多。
                                        range = (if (styleLevel == QuickWheelStyleLevel.SECONDARY) {
                                            QuickWheelLayoutEngine.MIN_SECONDARY_INITIAL_RADIUS_DP
                                        } else {
                                            QuickWheelLayoutEngine.MIN_INITIAL_RADIUS_DP
                                        })..QuickWheelLayoutEngine.MAX_INITIAL_RADIUS_DP,
                                        unit = "dp",
                                        onValueChange = { value ->
                                            updateStyle { it.copy(initialRadiusDp = value) }
                                        },
                                    )
                                }
                            )
                        }
                        add(
                            settingsCardScopeItem("container-gap") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_container_gap),
                                    description = stringResource(R.string.quick_wheel_container_gap_desc),
                                    value = levelStyle.containerGapDp,
                                    range = QuickWheelLayoutEngine.MIN_CONTAINER_GAP_DP..
                                        QuickWheelLayoutEngine.MAX_CONTAINER_GAP_DP,
                                    unit = "dp",
                                    onValueChange = { value ->
                                        updateStyle { it.copy(containerGapDp = value) }
                                    },
                                )
                            }
                        )
                        // 圆形形态专属：环间距（矩形形态按行排列，不使用本项）。
                        if (levelShape == QuickWheelShape.CIRCLE) {
                            add(
                                settingsCardScopeItem("ring-gap") {
                                    QuickWheelSliderRow(
                                        title = stringResource(R.string.quick_wheel_ring_gap),
                                        description = stringResource(R.string.quick_wheel_ring_gap_desc),
                                        value = levelStyle.ringGapDp,
                                        range = QuickWheelLayoutEngine.MIN_RING_GAP_DP..
                                            QuickWheelLayoutEngine.MAX_RING_GAP_DP,
                                        unit = "dp",
                                        onValueChange = { value ->
                                            updateStyle { it.copy(ringGapDp = value) }
                                        },
                                    )
                                }
                            )
                        }
                        add(
                            settingsCardScopeItem("container-size") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_container_size),
                                    description = stringResource(R.string.quick_wheel_container_size_desc),
                                    value = levelStyle.containerSizeDp,
                                    range = QuickWheelLayoutEngine.MIN_CONTAINER_SIZE_DP..
                                        QuickWheelLayoutEngine.MAX_CONTAINER_SIZE_DP,
                                    unit = "dp",
                                    onValueChange = { value ->
                                        updateStyle { it.copy(containerSizeDp = value) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("container-corner") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_container_corner),
                                    description = stringResource(R.string.quick_wheel_container_corner_desc),
                                    value = levelStyle.containerCornerDp,
                                    range = QuickWheelLayoutEngine.MIN_CONTAINER_CORNER_DP..
                                        QuickWheelLayoutEngine.MAX_CONTAINER_CORNER_DP,
                                    unit = "dp",
                                    onValueChange = { value ->
                                        updateStyle { it.copy(containerCornerDp = value) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("icon-size") {
                                QuickWheelPercentRow(
                                    title = stringResource(R.string.quick_wheel_icon_size),
                                    description = stringResource(R.string.quick_wheel_icon_size_desc),
                                    value = levelStyle.iconSizePercent,
                                    onValueChange = { value ->
                                        updateStyle { it.copy(iconSizePercent = value) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("label-size-factor") {
                                QuickWheelPercentRow(
                                    title = stringResource(R.string.quick_wheel_label_size_factor),
                                    description = stringResource(R.string.quick_wheel_label_size_factor_desc),
                                    value = levelStyle.labelSizeFactorPercent,
                                    onValueChange = { value ->
                                        updateStyle { it.copy(labelSizeFactorPercent = value) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("builtin-icon-size") {
                                QuickWheelPercentRow(
                                    title = stringResource(R.string.quick_wheel_builtin_icon_size),
                                    description = stringResource(R.string.quick_wheel_builtin_icon_size_desc),
                                    value = levelStyle.builtinIconSizePercent,
                                    onValueChange = { value ->
                                        updateStyle { it.copy(builtinIconSizePercent = value) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("offset-x") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_offset_x),
                                    description = stringResource(R.string.quick_wheel_offset_x_desc),
                                    value = levelStyle.offsetXDp,
                                    range = QuickWheelLayoutEngine.MIN_OFFSET_DP..
                                        QuickWheelLayoutEngine.MAX_OFFSET_DP,
                                    unit = "dp",
                                    onValueChange = { value ->
                                        updateStyle { it.copy(offsetXDp = value) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("offset-y") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_offset_y),
                                    description = stringResource(R.string.quick_wheel_offset_y_desc),
                                    value = levelStyle.offsetYDp,
                                    range = QuickWheelLayoutEngine.MIN_OFFSET_DP..
                                        QuickWheelLayoutEngine.MAX_OFFSET_DP,
                                    unit = "dp",
                                    onValueChange = { value ->
                                        updateStyle { it.copy(offsetYDp = value) }
                                    },
                                )
                            }
                        )
                        // 背景模糊 / 遮罩也属于外观：放在外观设置最底下，拖动同样会浮出实时预览
                        // （遮罩页内可见；模糊是跨窗口效果，请看「轮盘预览」）。
                        add(
                            settingsCardScopeItem("backdrop-blur") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_backdrop_blur),
                                    description = stringResource(R.string.quick_wheel_backdrop_blur_desc),
                                    value = wheel.backdropBlurDp.toFloat(),
                                    range = QuickWheelLayoutEngine.MIN_BACKDROP_BLUR_DP.toFloat()..
                                        QuickWheelLayoutEngine.MAX_BACKDROP_BLUR_DP.toFloat(),
                                    unit = "dp",
                                    onValueChange = { value ->
                                        patchDraft { it.copy(backdropBlurDp = value.toInt()) }
                                    },
                                )
                            }
                        )
                        add(
                            settingsCardScopeItem("backdrop-dim") {
                                QuickWheelSliderRow(
                                    title = stringResource(R.string.quick_wheel_backdrop_dim),
                                    description = stringResource(R.string.quick_wheel_backdrop_dim_desc),
                                    value = wheel.backdropDimPercent.toFloat(),
                                    range = QuickWheelLayoutEngine.MIN_BACKDROP_DIM_PERCENT.toFloat()..
                                        QuickWheelLayoutEngine.MAX_BACKDROP_DIM_PERCENT.toFloat(),
                                    unit = "%",
                                    onValueChange = { value ->
                                        patchDraft { it.copy(backdropDimPercent = value.toInt()) }
                                    },
                                )
                            }
                        )
                    },
                )
            }

            if (editMode) {
                // 编辑模式：接管触摸，提供提示条与增删/拖动能力。
                QuickWheelEditorOverlay(
                    // 与「轮盘预览 / 调参实时预览」同一套扇区 + 同一个锚点：
                    // 形状与位置都一致（默认左半圆时，圆心同样落在屏幕右边缘）。
                    wheel = previewWheel,
                    anchorOverride = previewAnchor,
                    // 编辑层只改容器：写回时保留设置里的外观字段，
                    // 避免把本页还没保存的外观草稿一起落盘。
                    onChange = { updated ->
                        onPatch(
                            settingsWheel.copy(
                                slots = updated.slots,
                                centerSlot = updated.centerSlot,
                            ),
                        )
                    },
                    onOpenSlot = onOpenSlot,
                    onClose = { editMode = false },
                )
            } else if (previewVisible) {
                // 调参实时预览：只在调整外观参数时出现。
                //
                // ⚠️ 预览可见时**整层吞掉触摸**：它只是"呼出效果的示意"，不是真的轮盘，
                // 点它不能穿透到下面的设置项，也不执行任何动作。
                // 点轮盘之外的空白区 = 收起预览（与「轮盘预览」浮层点空白的表现一致）。
                // 正在进行中的拖动不受影响：Compose 的指针路径在按下时就已确定。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(previewVisible, previewLayout) {
                            detectTapGestures { position ->
                                // 只有点在轮盘之外（空白区）才收起；点在轮盘上只是"看"，
                                // 两种情况下都由这一层消费，不会穿透到底下的设置项。
                                val dx = position.x - previewLayout.centerX
                                val dy = position.y - previewLayout.centerY
                                if (hypot(dx, dy) > previewLayout.outerRadiusPx + 24f) {
                                    previewVisible = false
                                }
                            }
                        },
                )
                // 预览里把用户留出的空位显示为"空白阴影"（真实呼出时是全透明的）。
                // 背景遮罩按参数同步铺一层（页内做不了跨窗口模糊，模糊效果请看「轮盘预览」）。
                if (previewWheel.backdropDimPercent > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(
                                    alpha = previewWheel.backdropDimPercent / 100f,
                                ),
                            ),
                    )
                }
                QuickWheelWheelRenderer(
                    layout = previewLayout,
                    wheel = wheel,
                    // 页内预览也走同一套展开动画（lambda 读取，动画期间不重组）。
                    appearProgress = { previewAppear.value },
                    // 调二级外观参数时，布局里的"样本二级"没有对应的真实数据（样本父容器特意选的是
                    // "没有子容器"的格位，避免掩盖真实二级），必须允许无数据槽位兜底渲染，
                    // 否则样本会被整体跳过 —— 表现就是"调二级参数时预览里看不到样本"。
                    // 调一级参数时 previewSecondarySlots 为 null → 行为与原来完全一致。
                    allowDataLessSecondarySlots = previewSecondarySlots != null,
                    showPlaceholders = true,
                    // 轮盘预览里：有二级容器的一级容器右上角显示红点。
                    showSecondaryDots = true,
                )
            }

            if (showLeaveDialog) {
                // 居中弹窗：带未保存改动返回时询问「保存后退出 / 直接退出」。
                AlertDialog(
                    onDismissRequest = { showLeaveDialog = false },
                    title = { Text(stringResource(R.string.quick_wheel_unsaved_title)) },
                    text = { Text(stringResource(R.string.quick_wheel_unsaved_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLeaveDialog = false
                                saveDraft()
                                onBack()
                            },
                        ) {
                            Text(stringResource(R.string.quick_wheel_save_and_exit))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showLeaveDialog = false
                                // 直接退出：草稿不落盘，设置里的值保持原样。
                                onBack()
                            },
                        ) {
                            Text(stringResource(R.string.quick_wheel_exit_without_save))
                        }
                    },
                )
            }
        }
    }
}

/** 外观参数控件被按下时回调（[PointerEventPass.Initial]，不消费事件）。 */
@Composable
private fun Modifier.appearanceInteract(onInteract: () -> Unit): Modifier {
    val current by rememberUpdatedState(onInteract)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                current()
            }
        }
    }
}

/** 可点选的扇区配置行（一级轮盘为圆形时展示）。 */
@Composable
private fun QuickWheelSectorRow(
    sectorMask: Int,
    onToggleSector: (Int) -> Unit,
) {
    val interact = LocalQuickWheelAppearanceInteract.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appearanceInteract(interact)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.quick_wheel_sector_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.quick_wheel_sector_span,
                    QuickWheelLayoutEngine.sectorSpanDeg(sectorMask),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = QuickWheelAccent,
            )
        }
        Text(
            text = stringResource(R.string.quick_wheel_sector_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        QuickWheelSectorRing(
            sectorMask = sectorMask,
            onToggleSector = onToggleSector,
            activeColor = QuickWheelAccent,
            inactiveColor = MaterialTheme.colorScheme.outlineVariant,
            outlineColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        )
    }
}

@Composable
private fun quickWheelShapeLabel(shape: QuickWheelShape): String = stringResource(
    if (shape == QuickWheelShape.CIRCLE) R.string.quick_wheel_shape_circle else R.string.quick_wheel_shape_rect,
)

@Composable
private fun QuickWheelNavRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> QuickWheelDropdownRow(
    title: String,
    description: String,
    value: T,
    options: List<T>,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 只取按下位置的横向坐标：菜单横向落在「点击处」；纵向交给菜单自己紧贴本行上/下边缘展开
    // （若把按下点的纵坐标也加进去，向下展开时会与本行多出一段距离）。
    var anchorX by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val interact = LocalQuickWheelAppearanceInteract.current
    Box(
        modifier = Modifier
            .appearanceInteract(interact)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        anchorX = with(density) { down.position.x.toDp() }
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = labelOf(value),
                style = MaterialTheme.typography.bodyMedium,
                color = QuickWheelAccent,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // 纵向不加偏移：菜单自己紧贴本行上/下边缘展开，避免向下展开时出现空隙。
            offset = DpOffset(anchorX, 0.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == value) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = QuickWheelAccent,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** 开关行（本页与「呼出形态」页共用）：标题 + 说明 + 右侧开关。 */
@Composable
internal fun QuickWheelSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interact = LocalQuickWheelAppearanceInteract.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appearanceInteract(interact)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 参数滑条：统一用设置页标准的 [MiuixSliderRow]（Miuix `SliderPreference` 行），
 * 与「快速启动器」等页面的拉动横条外观一致。
 */
@Composable
private fun QuickWheelSliderRow(
    title: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
) {
    MiuixSliderRow(
        title = title,
        summary = description,
        value = value,
        valueRange = range,
        steps = quickWheelSliderSteps(range),
        formatLabel = { "${it.roundToInt()} $unit" },
        // 现在 1 个单位一档：存进样式的值也取整，避免滑条给出 100.9999 这类值
        // （显示已按 roundToInt 四舍五入，不取整会出现"显示 100、实际 100.99"）。
        onValueChange = { onValueChange(it.roundToInt().toFloat()) },
    )
}

/** 整数滑条（步进离散），用于「矩形列数」这类小范围整数参数。 */
@Composable
private fun QuickWheelIntRow(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    MiuixSliderRow(
        title = title,
        summary = description,
        value = value.toFloat(),
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
        formatLabel = { it.roundToInt().toString() },
        onValueChange = { onValueChange(it.roundToInt()) },
    )
}

@Composable
private fun QuickWheelPercentRow(
    title: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    MiuixSliderRow(
        title = title,
        summary = description,
        value = value.toFloat(),
        valueRange = QuickWheelLayoutEngine.MIN_PERCENT.toFloat()..
            QuickWheelLayoutEngine.MAX_PERCENT.toFloat(),
        // 1% 一档（区间 20–100 → 79 个中间档位）。
        steps = (QuickWheelLayoutEngine.MAX_PERCENT - QuickWheelLayoutEngine.MIN_PERCENT) - 1,
        formatLabel = { "${it.roundToInt()} %" },
        onValueChange = { onValueChange(it.roundToInt()) },
    )
}

/** 1 个单位一档（精度优先）：档位数 = 区间长度 − 1。 */
private fun quickWheelSliderSteps(range: ClosedFloatingPointRange<Float>): Int =
    ((range.endInclusive - range.start).roundToInt() - 1).coerceAtLeast(0)

/** 浮层跨窗口模糊半径（px）上限，与 QuickWheelOverlayWindow 的 coerceIn(1, 80) 保持一致。 */
private const val OVERLAY_BLUR_MAX_RADIUS_PX = 80f

/**
 * 页内"背景模糊"实时预览该用的 **sigma**（以 dp 表示，喂给 `Modifier.blur`）。
 *
 * 两处口径本来不同，必须换算，否则页内会比真实呼出糊约一倍：
 * - 浮层：`setBlurBehindRadius(min(blurDp × density, 80))` —— 这是**半径**（px），且 clamp 在 1..80；
 * - Compose：`Modifier.blur(radius)` 的参数实际是 **sigma**（内部直接进 `RenderEffect.createBlurEffect`），
 *   而半径 ≈ 2 × sigma。
 *
 * 所以先把 dp 还原成浮层那个半径（含同样的 80px 上限），再换算成 sigma。
 */
private fun previewBlurSigmaDp(blurDp: Int, density: Float): Float {
    if (blurDp <= 0) return 0f
    val safeDensity = if (density > 0f) density else 1f
    val radiusPx = (blurDp * safeDensity).coerceIn(1f, OVERLAY_BLUR_MAX_RADIUS_PX)
    return radiusPx / 2f / safeDensity
}
