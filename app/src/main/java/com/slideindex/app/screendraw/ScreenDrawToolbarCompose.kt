package com.slideindex.app.screendraw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.overlay.rememberOverlayBlurEnabled
import kotlin.math.roundToInt

internal enum class ScreenDrawOrientation { HORIZONTAL, VERTICAL }

internal enum class ScreenDrawFinishAction {
    EDIT,       // 进入内置图片编辑器编辑
    OCR,        // 当前屏幕绘制进入取词面板
    COPY,       // 复制到剪贴板
    SAVE,       // 保存
    SAVE_TEMP,  // 临时保存（长按）
    SHARE,      // 分享
    PIN,        // 钉住
}

private val PRESET_COLORS = intArrayOf(
    0xFFFFFFFF.toInt(), // 白
    0xFF000000.toInt(), // 黑
    0xFF8E8E93.toInt(), // 灰
    0xFFFF3B30.toInt(), // 红
    0xFFFF9500.toInt(), // 橙
    0xFFFFCC00.toInt(), // 黄
    0xFF34C759.toInt(), // 绿
    0xFF0A84FF.toInt(), // 蓝
    0xFFAF52DE.toInt(), // 紫
)

/**
 * 一墨 1:1 风格的屏幕绘制工具栏与上下文属性悬浮面板：
 * - 2 行 × 7 列工具栏（14 个按钮）
 * - 选中当前画笔再次点击弹出专属毛玻璃配置面板
 */
/**
 * 主工具栏（纯净胶囊，尺寸永久固定，零抖动）：
 * - 横排：316dp × 80dp，2 行 × 7 列
 * - 竖排：96dp × 316dp，2 列 × 7 行
 */
@Composable
internal fun ScreenDrawMainToolbarCompose(
    orientation: ScreenDrawOrientation,
    passthrough: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    currentTool: ScreenDrawTool,
    shape: ScreenDrawShape,
    isFinishActionsShowing: Boolean = false,
    onToolClick: (ScreenDrawTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onTogglePassthrough: () -> Unit,
    onToggleOrientation: () -> Unit,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
    onBlurEnabled: (Boolean) -> Unit,
    onMoveBy: (Float, Float) -> Unit,
    onMoveFinished: () -> Unit,
) {
    val blurEnabled = rememberOverlayBlurEnabled()
    LaunchedEffect(blurEnabled) { onBlurEnabled(blurEnabled) }

    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) {
        if (blurEnabled) Color(0xFF1E2024).copy(alpha = 0.65f) else Color(0xFF22242A).copy(alpha = 0.92f)
    } else {
        if (blurEnabled) Color(0xFFF0F2F5).copy(alpha = 0.82f) else Color(0xFFF7F8FA).copy(alpha = 0.96f)
    }
    val borderColor = scheme.outline.copy(alpha = 0.18f)
    val capsuleShadow = if (blurEnabled) 18.dp else 12.dp

    val haptic = LocalHapticFeedback.current
    val dragModifier = Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onDragEnd = { onMoveFinished() },
            onDragCancel = { onMoveFinished() },
        ) { change, dragAmount ->
            change.consume()
            onMoveBy(dragAmount.x, dragAmount.y)
        }
    }

    GlassCapsule(containerColor, borderColor, capsuleShadow) {
        if (orientation == ScreenDrawOrientation.VERTICAL) {
            // 竖排：2 列 × 7 行（宽 86dp，高 316dp，两列宽各 34dp，左右 padding 各 7dp）
            Row(
                modifier = Modifier
                    .width(86.dp)
                    .height(316.dp)
                    .padding(horizontal = 7.dp, vertical = 8.dp)
                    .then(dragModifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ToolButton(painterResource(R.drawable.ic_iconoir_edit_pencil), currentTool == ScreenDrawTool.PEN) {
                        onToolClick(ScreenDrawTool.PEN)
                    }
                    ToolButton(painterResource(R.drawable.ic_remix_eraser), currentTool == ScreenDrawTool.ERASER) {
                        onToolClick(ScreenDrawTool.ERASER)
                    }
                    ToolButton(shapePainter(shape), currentTool == ScreenDrawTool.SHAPE) {
                        onToolClick(ScreenDrawTool.SHAPE)
                    }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_text), currentTool == ScreenDrawTool.TEXT) {
                        onToolClick(ScreenDrawTool.TEXT)
                    }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_mosaic), currentTool == ScreenDrawTool.MOSAIC) {
                        onToolClick(ScreenDrawTool.MOSAIC)
                    }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_number), currentTool == ScreenDrawTool.NUMBER) {
                        onToolClick(ScreenDrawTool.NUMBER)
                    }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_frame), currentTool == ScreenDrawTool.BOX_SELECT) {
                        onToolClick(ScreenDrawTool.BOX_SELECT)
                    }
                }
                Column(
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ToolButton(painterResource(R.drawable.ic_iconoir_trash), false) { onClear() }
                    ToolButton(painterResource(R.drawable.ic_iconoir_undo), false, enabled = canUndo) { onUndo() }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_redo), false, enabled = canRedo) { onRedo() }
                    ToolButton(
                        icon = painterResource(R.drawable.ic_iconoir_reduce),
                        selected = false,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleOrientation()
                        },
                        onClick = { onMinimize() },
                    )
                    ToolButton(painterResource(R.drawable.ic_iconoir_one_finger_gesture), passthrough) {
                        onTogglePassthrough()
                    }
                    ToolButton(painterResource(R.drawable.ic_iconoir_check), isFinishActionsShowing) { onFinish() }
                    ToolButton(painterResource(R.drawable.ic_iconoir_xmark), false) { onExit() }
                }
            }
        } else {
            // 横排：2 行 × 7 列（宽 316dp，高 86dp，两行高各 34dp，上下 padding 各 7dp，上下严格对称）
            Column(
                modifier = Modifier
                    .width(316.dp)
                    .height(86.dp)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
                    .then(dragModifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 第一行：7 个绘制工具（高 34dp）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolButton(painterResource(R.drawable.ic_iconoir_edit_pencil), currentTool == ScreenDrawTool.PEN) {
                        onToolClick(ScreenDrawTool.PEN)
                    }
                    ToolButton(painterResource(R.drawable.ic_remix_eraser), currentTool == ScreenDrawTool.ERASER) {
                        onToolClick(ScreenDrawTool.ERASER)
                    }
                    ToolButton(shapePainter(shape), currentTool == ScreenDrawTool.SHAPE) {
                        onToolClick(ScreenDrawTool.SHAPE)
                    }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_text), currentTool == ScreenDrawTool.TEXT) {
                        onToolClick(ScreenDrawTool.TEXT)
                    }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_mosaic), currentTool == ScreenDrawTool.MOSAIC) {
                        onToolClick(ScreenDrawTool.MOSAIC)
                    }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_number), currentTool == ScreenDrawTool.NUMBER) {
                        onToolClick(ScreenDrawTool.NUMBER)
                    }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_frame), currentTool == ScreenDrawTool.BOX_SELECT) {
                        onToolClick(ScreenDrawTool.BOX_SELECT)
                    }
                }

                // 第二行：7 个操作与控制按钮（高 34dp）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolButton(painterResource(R.drawable.ic_iconoir_trash), false) { onClear() }
                    ToolButton(painterResource(R.drawable.ic_iconoir_undo), false, enabled = canUndo) { onUndo() }
                    ToolButton(painterResource(R.drawable.ic_screen_draw_redo), false, enabled = canRedo) { onRedo() }
                    ToolButton(
                        icon = painterResource(R.drawable.ic_iconoir_reduce),
                        selected = false,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleOrientation()
                        },
                        onClick = { onMinimize() },
                    )
                    ToolButton(painterResource(R.drawable.ic_iconoir_one_finger_gesture), passthrough) {
                        onTogglePassthrough()
                    }
                    ToolButton(painterResource(R.drawable.ic_iconoir_check), isFinishActionsShowing) { onFinish() }
                    ToolButton(painterResource(R.drawable.ic_iconoir_xmark), false) { onExit() }
                }
            }
        }
    }
}

/**
 * 屏幕绘制完成导出操作栏（毛玻璃单行操作行）：
 * 包含 6 个动作按钮：
 * 1. 进入内置图片编辑器编辑
 * 2. 当前屏幕绘制进入取词面板
 * 3. 复制到剪贴板
 * 4. 保存（点击保存，长按临时保存）
 * 5. 分享
 * 6. 钉住
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScreenDrawFinishActionsCard(
    orientation: ScreenDrawOrientation,
    onAction: (ScreenDrawFinishAction) -> Unit,
) {
    val blurEnabled = rememberOverlayBlurEnabled()
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) {
        if (blurEnabled) Color(0xFF1E2024).copy(alpha = 0.65f) else Color(0xFF22242A).copy(alpha = 0.92f)
    } else {
        if (blurEnabled) Color(0xFFF0F2F5).copy(alpha = 0.82f) else Color(0xFFF7F8FA).copy(alpha = 0.96f)
    }
    val borderColor = scheme.outline.copy(alpha = 0.18f)
    val capsuleShadow = if (blurEnabled) 16.dp else 10.dp
    val isVertical = orientation == ScreenDrawOrientation.VERTICAL

    val panelWidth = if (isVertical) 52.dp else 264.dp
    val panelHeight = if (isVertical) 264.dp else 52.dp

    val haptic = LocalHapticFeedback.current

    val imageEditorIcon = rememberVectorPainter(ImageEditorActionIcon)
    val ocrScanIcon = rememberVectorPainter(OcrScanActionIcon)
    val copyIcon = painterResource(R.drawable.ic_baseline_content_copy_24)
    val saveIcon = rememberVectorPainter(SaveActionIcon)
    val shareIcon = rememberVectorPainter(Icons.Outlined.Share)
    val pinIcon = rememberVectorPainter(Icons.Outlined.PushPin)

    GlassCapsule(containerColor, borderColor, capsuleShadow) {
        if (isVertical) {
            Column(
                modifier = Modifier
                    .width(panelWidth)
                    .height(panelHeight)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ActionIconButton(imageEditorIcon, "图片编辑") { onAction(ScreenDrawFinishAction.EDIT) }
                ActionIconButton(ocrScanIcon, "取词") { onAction(ScreenDrawFinishAction.OCR) }
                ActionIconButton(copyIcon, "复制") { onAction(ScreenDrawFinishAction.COPY) }
                ActionIconButton(
                    icon = saveIcon,
                    contentDescription = "保存（长按临时保存）",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAction(ScreenDrawFinishAction.SAVE_TEMP)
                    },
                    onClick = { onAction(ScreenDrawFinishAction.SAVE) },
                )
                ActionIconButton(shareIcon, "分享") { onAction(ScreenDrawFinishAction.SHARE) }
                ActionIconButton(pinIcon, "钉住") { onAction(ScreenDrawFinishAction.PIN) }
            }
        } else {
            Row(
                modifier = Modifier
                    .width(panelWidth)
                    .height(panelHeight)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconButton(imageEditorIcon, "图片编辑") { onAction(ScreenDrawFinishAction.EDIT) }
                ActionIconButton(ocrScanIcon, "取词") { onAction(ScreenDrawFinishAction.OCR) }
                ActionIconButton(copyIcon, "复制") { onAction(ScreenDrawFinishAction.COPY) }
                ActionIconButton(
                    icon = saveIcon,
                    contentDescription = "保存（长按临时保存）",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAction(ScreenDrawFinishAction.SAVE_TEMP)
                    },
                    onClick = { onAction(ScreenDrawFinishAction.SAVE) },
                )
                ActionIconButton(shareIcon, "分享") { onAction(ScreenDrawFinishAction.SHARE) }
                ActionIconButton(pinIcon, "钉住") { onAction(ScreenDrawFinishAction.PIN) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionIconButton(
    icon: Painter,
    contentDescription: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = scheme.onSurface.copy(alpha = 0.88f),
        )
    }
}

private val OcrScanActionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "OcrScanAction",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 左上扫描角
            moveTo(4f, 8f); lineTo(4f, 5f); curveTo(4f, 4.45f, 4.45f, 4f, 5f, 4f); lineTo(8f, 4f)
            // 右上扫描角
            moveTo(16f, 4f); lineTo(19f, 4f); curveTo(19.55f, 4f, 20f, 4.45f, 20f, 5f); lineTo(20f, 8f)
            // 左下扫描角
            moveTo(4f, 16f); lineTo(4f, 19f); curveTo(4f, 19.55f, 4.45f, 20f, 5f, 20f); lineTo(8f, 20f)
            // 右下扫描角
            moveTo(16f, 20f); lineTo(19f, 20f); curveTo(19.55f, 20f, 20f, 19.55f, 20f, 19f); lineTo(20f, 16f)
            // 中心字母 T
            moveTo(7.5f, 8.5f); lineTo(16.5f, 8.5f)
            moveTo(12f, 8.5f); lineTo(12f, 16.5f)
        }
    }.build()
}

private val ImageEditorActionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ImageEditorAction",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 1. 完整的外相框（闭合圆角矩形，绝不残缺）
            moveTo(5f, 4f)
            lineTo(19f, 4f)
            curveTo(19.8f, 4f, 20.5f, 4.7f, 20.5f, 5.5f)
            lineTo(20.5f, 18.5f)
            curveTo(20.5f, 19.3f, 19.8f, 20f, 19f, 20f)
            lineTo(5f, 20f)
            curveTo(4.2f, 20f, 3.5f, 19.3f, 3.5f, 18.5f)
            lineTo(3.5f, 5.5f)
            curveTo(3.5f, 4.7f, 4.2f, 4f, 5f, 4f)
            close()
            // 2. 内部山丘
            moveTo(3.5f, 15f)
            lineTo(8f, 10.5f)
            lineTo(12.5f, 15f)
            // 3. 完整的倾斜编辑画笔
            moveTo(12f, 17.5f)
            lineTo(17.5f, 12f)
            curveTo(18.2f, 11.3f, 19.3f, 11.3f, 20f, 12f)
            curveTo(20.7f, 12.7f, 20.7f, 13.8f, 20f, 14.5f)
            lineTo(14.5f, 20f)
            lineTo(11.5f, 20.5f)
            lineTo(12f, 17.5f)
        }
    }.build()
}

private val SaveActionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SaveAction",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 下方空心托盘
            moveTo(4.5f, 14.5f)
            lineTo(4.5f, 19f)
            curveTo(4.5f, 19.55f, 4.95f, 20f, 5.5f, 20f)
            lineTo(18.5f, 20f)
            curveTo(19.05f, 20f, 19.5f, 19.55f, 19.5f, 19f)
            lineTo(19.5f, 14.5f)
            // 竖向向下箭头
            moveTo(12f, 4f)
            lineTo(12f, 14.5f)
            moveTo(8f, 10.5f)
            lineTo(12f, 14.5f)
            lineTo(16f, 10.5f)
        }
    }.build()
}

/**
 * 动态计算各工具配置面板的实际适配合适高度：
 * - 马赛克：单行滑杆，56dp
 * - 画笔/橡皮擦/文字/激光笔：双行控件，96dp
 * - 序号/图形：三行控件，142dp
 * - 竖排模式：与工具栏齐高 316dp
 */
internal fun getOptionsPanelHeightDp(
    orientation: ScreenDrawOrientation,
    tool: ScreenDrawTool?,
    isFinishActions: Boolean = false,
): Float {
    if (isFinishActions) {
        return if (orientation == ScreenDrawOrientation.VERTICAL) 264f else 52f
    }
    if (orientation == ScreenDrawOrientation.VERTICAL) {
        return 316f
    }
    return when (tool) {
        ScreenDrawTool.MOSAIC -> 56f
        ScreenDrawTool.PEN, ScreenDrawTool.ERASER, ScreenDrawTool.TEXT, ScreenDrawTool.LASER -> 96f
        ScreenDrawTool.NUMBER, ScreenDrawTool.SHAPE -> 142f
        else -> 96f
    }
}

/**
 * 独立的配置面板卡片：
 * - 横排模式：尺寸根据实际工具内容高度自适应（56dp ~ 142dp）
 * - 竖排模式：尺寸为 148dp × 316dp（高度匹配竖排工具栏，内容垂直排布）
 * - 内部 Crossfade 淡入淡出切换选项
 */
@Composable
internal fun ScreenDrawOptionsPanelCompose(
    orientation: ScreenDrawOrientation,
    activeTool: ScreenDrawTool?,
    shape: ScreenDrawShape,
    color: Int,
    widthDp: Float,
    textFontSizeSp: Float,
    numberStyle: ScreenDrawNumberStyle,
    numberSizeDp: Float,
    mosaicRadiusDp: Float,
    eraserMode: ScreenDrawEraserMode,
    eraserRadiusDp: Float,
    onShape: (ScreenDrawShape) -> Unit,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onTextFontSize: (Float) -> Unit,
    onNumberStyle: (ScreenDrawNumberStyle) -> Unit,
    onNumberSize: (Float) -> Unit,
    onMosaicRadius: (Float) -> Unit,
    onEraserMode: (ScreenDrawEraserMode) -> Unit,
    onEraserRadius: (Float) -> Unit,
) {
    if (activeTool == null || activeTool == ScreenDrawTool.BOX_SELECT) return

    val blurEnabled = rememberOverlayBlurEnabled()
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (blurEnabled) {
        scheme.surface.copy(alpha = 0.65f)
    } else {
        scheme.surface.copy(alpha = 0.90f)
    }
    val borderColor = scheme.outline.copy(alpha = 0.18f)
    val capsuleShadow = if (blurEnabled) 16.dp else 10.dp
    val isVertical = orientation == ScreenDrawOrientation.VERTICAL

    val panelWidth = if (isVertical) 148.dp else 316.dp
    val panelHeight = getOptionsPanelHeightDp(orientation, activeTool, false).dp

    GlassCapsule(containerColor, borderColor, capsuleShadow) {
        Box(
            modifier = Modifier
                .width(panelWidth)
                .height(panelHeight)
                .animateContentSize(animationSpec = tween(140))
                .padding(
                    horizontal = if (isVertical) 12.dp else 14.dp,
                    vertical = if (isVertical) 14.dp else 10.dp,
                ),
            contentAlignment = if (isVertical) Alignment.TopCenter else Alignment.Center,
        ) {
            Crossfade(targetState = activeTool, animationSpec = tween(140)) { target ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isVertical) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(if (isVertical) 12.dp else 8.dp),
                ) {
                    when (target) {
                        ScreenDrawTool.PEN -> {
                            ColorPickerGroup(color, isVertical, onColor)
                            OptionSliderRow("粗细", widthDp, 1f..24f, isVertical, onWidth)
                        }
                        ScreenDrawTool.ERASER -> {
                            SegmentedButtonsGroup(
                                items = listOf(
                                    "整笔擦除" to ScreenDrawEraserMode.STROKE,
                                    "像素擦除" to ScreenDrawEraserMode.PIXEL,
                                ),
                                selected = eraserMode,
                                isVertical = isVertical,
                                onSelect = onEraserMode,
                            )
                            OptionSliderRow("半径", eraserRadiusDp, 6f..50f, isVertical, onEraserRadius)
                        }
                        ScreenDrawTool.SHAPE -> {
                            ShapeSelectorGroup(shape, isVertical, onShape)
                            ColorPickerGroup(color, isVertical, onColor)
                            OptionSliderRow("粗细", widthDp, 1f..20f, isVertical, onWidth)
                        }
                        ScreenDrawTool.TEXT -> {
                            ColorPickerGroup(color, isVertical, onColor)
                            OptionSliderRow("字号", textFontSizeSp, 12f..72f, isVertical, onTextFontSize)
                        }
                        ScreenDrawTool.MOSAIC -> {
                            OptionSliderRow("半径", mosaicRadiusDp, 10f..50f, isVertical, onMosaicRadius)
                        }
                        ScreenDrawTool.NUMBER -> {
                            NumberStyleSelectorGroup(numberStyle, isVertical, onNumberStyle)
                            ColorPickerGroup(color, isVertical, onColor)
                            OptionSliderRow("大小", numberSizeDp, 24f..72f, isVertical, onNumberSize)
                        }
                        ScreenDrawTool.LASER -> {
                            ColorPickerGroup(color, isVertical, onColor)
                            OptionSliderRow("粗细", widthDp, 2f..20f, isVertical, onWidth)
                        }
                        ScreenDrawTool.BOX_SELECT -> Unit
                    }
                }
            }
        }
    }
}

// ================= 组件子项 =================

@Composable
private fun GlassCapsule(
    containerColor: Color,
    borderColor: Color,
    shadowElevation: Dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val isDark = isSystemInDarkTheme()
    // 柔光微透双轨高光边框：暗色模式采用晶莹白光，亮色模式下沿引入深色环境折射，解决纯白背景下隐形无边框感的问题
    val softGlassBorder = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.08f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.90f),
                Color(0xFF8E8E93).copy(alpha = 0.22f),
                Color.Black.copy(alpha = 0.18f),
            )
        )
    }
    // 柔光表面微透反射渐变
    val softGlassSurface = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.03f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.04f),
            )
        )
    }

    Box(
        modifier = Modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.12f else 0.16f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.18f else 0.26f),
            )
            .clip(shape)
            .border(BorderStroke(1.dp, softGlassBorder), shape),
    ) {
        LocalFrostedGlassBackdrop(
            modifier = Modifier.matchParentSize(),
            cornerRadiusPx = with(LocalDensity.current) { 24.dp.toPx() },
            solidFallbackColor = MaterialTheme.colorScheme.surface.toArgb(),
        )
        // 柔光漫反射覆层
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(softGlassSurface)
        )
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(
    icon: Painter,
    selected: Boolean,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val activeColor = scheme.primary
    val contentColor = if (selected) Color.White else scheme.onSurface.copy(alpha = if (enabled) 0.88f else 0.35f)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) activeColor.copy(alpha = 0.92f) else Color.Transparent)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = contentColor,
        )
    }
}

@Composable
private fun ColorCircleItem(
    color: Int,
    isSelected: Boolean,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable { onSelect(color) },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(2.dp, scheme.primary, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(if (isSelected) 20.dp else 24.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    0.8.dp,
                    if (color == 0xFFFFFFFF.toInt()) scheme.onSurface.copy(alpha = 0.25f) else scheme.outline.copy(alpha = 0.15f),
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun ColorPickerGroup(
    selectedColor: Int,
    isVertical: Boolean,
    onSelect: (Int) -> Unit,
) {
    if (!isVertical) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PRESET_COLORS.forEach { c ->
                ColorCircleItem(c, selectedColor == c, onSelect)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PRESET_COLORS.toList().chunked(4).forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowColors.forEach { c ->
                        ColorCircleItem(c, selectedColor == c, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isVertical: Boolean,
    onValueChange: (Float) -> Unit,
) {
    var sliderPos by remember(value) { mutableFloatStateOf(value) }
    val scheme = MaterialTheme.colorScheme

    if (isVertical) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = scheme.onSurface.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${sliderPos.roundToInt()}",
                    color = scheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Slider(
                value = sliderPos.coerceIn(range.start, range.endInclusive),
                onValueChange = {
                    sliderPos = it
                    onValueChange(it)
                },
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.sliderColors(
                    foregroundColor = scheme.primary,
                    backgroundColor = scheme.onSurface.copy(alpha = 0.14f),
                ),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                color = scheme.onSurface.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(32.dp),
            )
            Slider(
                value = sliderPos.coerceIn(range.start, range.endInclusive),
                onValueChange = {
                    sliderPos = it
                    onValueChange(it)
                },
                valueRange = range,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.sliderColors(
                    foregroundColor = scheme.primary,
                    backgroundColor = scheme.onSurface.copy(alpha = 0.14f),
                ),
            )
            Text(
                text = sliderPos.roundToInt().toString(),
                color = scheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(28.dp),
            )
        }
    }
}

@Composable
private fun <T> SegmentedButtonsGroup(
    items: List<Pair<String, T>>,
    selected: T,
    isVertical: Boolean,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    if (!isVertical) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.onSurface.copy(alpha = 0.08f))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { (title, value) ->
                val isSelected = selected == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(shape)
                        .background(if (isSelected) scheme.primary else Color.Transparent)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else scheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.onSurface.copy(alpha = 0.08f))
                .padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { (title, value) ->
                val isSelected = selected == value
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(shape)
                        .background(if (isSelected) scheme.primary else Color.Transparent)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else scheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShapeItem(
    shape: ScreenDrawShape,
    icon: Painter,
    isSelected: Boolean,
    onSelect: (ScreenDrawShape) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) scheme.primary.copy(alpha = 0.85f) else scheme.onSurface.copy(alpha = 0.08f))
            .clickable { onSelect(shape) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) Color.White else scheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ShapeSelectorGroup(
    current: ScreenDrawShape,
    isVertical: Boolean,
    onSelect: (ScreenDrawShape) -> Unit,
) {
    val list = listOf(
        ScreenDrawShape.RECTANGLE to painterResource(R.drawable.ic_iconoir_square),
        ScreenDrawShape.OVAL to rememberVectorPainter(Icons.Outlined.Circle),
        ScreenDrawShape.TRIANGLE to painterResource(R.drawable.ic_screen_draw_triangle),
        ScreenDrawShape.LINE to rememberVectorPainter(Icons.Outlined.Remove),
        ScreenDrawShape.DASHED_LINE to painterResource(R.drawable.ic_screen_draw_dashed_line),
        ScreenDrawShape.ARROW to rememberVectorPainter(Icons.AutoMirrored.Outlined.ArrowForward),
    )
    if (!isVertical) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            list.forEach { (shape, icon) ->
                ShapeItem(shape, icon, current == shape, onSelect)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            list.chunked(3).forEach { rowShapes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowShapes.forEach { (shape, icon) ->
                        ShapeItem(shape, icon, current == shape, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberStyleSelectorGroup(
    current: ScreenDrawNumberStyle,
    isVertical: Boolean,
    onSelect: (ScreenDrawNumberStyle) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val list = listOf(
        ScreenDrawNumberStyle.SOLID_CIRCLE to "❶",
        ScreenDrawNumberStyle.HOLLOW_CIRCLE to "①",
        ScreenDrawNumberStyle.PURE_NUMBER to "1",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isVertical) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        list.forEach { (style, label) ->
            val isSelected = current == style
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) scheme.primary.copy(alpha = 0.85f) else scheme.onSurface.copy(alpha = 0.08f))
                    .clickable { onSelect(style) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else scheme.onSurface.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun shapePainter(shape: ScreenDrawShape): Painter = when (shape) {
    ScreenDrawShape.RECTANGLE -> painterResource(R.drawable.ic_iconoir_square)
    ScreenDrawShape.OVAL -> rememberVectorPainter(Icons.Outlined.Circle)
    ScreenDrawShape.TRIANGLE -> painterResource(R.drawable.ic_screen_draw_triangle)
    ScreenDrawShape.LINE -> rememberVectorPainter(Icons.Outlined.Remove)
    ScreenDrawShape.DASHED_LINE -> painterResource(R.drawable.ic_screen_draw_dashed_line)
    ScreenDrawShape.ARROW -> rememberVectorPainter(Icons.AutoMirrored.Outlined.ArrowForward)
}

/**
 * 屏幕绘制缩小后的精致小巧柔光玻璃悬浮球：
 * - 尺寸 48dp
 * - 背景柔光高斯毛玻璃，高透微光漫反射
 * - 中心根据当前工具展示对应图标
 * - 右下角带白色微描边的当前颜色指示圆点
 */
@Composable
internal fun ScreenDrawMinimizedBallCompose(
    currentTool: ScreenDrawTool,
    shape: ScreenDrawShape,
    color: Int,
    onClick: () -> Unit,
) {
    val blurEnabled = rememberOverlayBlurEnabled()
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) {
        if (blurEnabled) Color(0xFF1E2024).copy(alpha = 0.65f) else Color(0xFF22242A).copy(alpha = 0.92f)
    } else {
        if (blurEnabled) Color(0xFFF0F2F5).copy(alpha = 0.82f) else Color(0xFFF7F8FA).copy(alpha = 0.96f)
    }
    val iconPainter = when (currentTool) {
        ScreenDrawTool.PEN -> painterResource(R.drawable.ic_iconoir_edit_pencil)
        ScreenDrawTool.ERASER -> painterResource(R.drawable.ic_remix_eraser)
        ScreenDrawTool.LASER -> painterResource(R.drawable.ic_remix_magic_line)
        ScreenDrawTool.SHAPE -> shapePainter(shape)
        ScreenDrawTool.TEXT -> painterResource(R.drawable.ic_screen_draw_text)
        ScreenDrawTool.MOSAIC -> painterResource(R.drawable.ic_screen_draw_mosaic)
        ScreenDrawTool.NUMBER -> painterResource(R.drawable.ic_screen_draw_number)
        ScreenDrawTool.BOX_SELECT -> painterResource(R.drawable.ic_screen_draw_frame)
    }

    val hasColorBadge = when (currentTool) {
        ScreenDrawTool.PEN, ScreenDrawTool.SHAPE, ScreenDrawTool.TEXT, ScreenDrawTool.NUMBER -> true
        else -> false
    }

    val softGlassBorder = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.10f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.92f),
                Color(0xFF8E8E93).copy(alpha = 0.25f),
                Color.Black.copy(alpha = 0.22f),
            )
        )
    }
    val softGlassSurface = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.04f),
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.06f),
            )
        )
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(
                elevation = if (blurEnabled) 16.dp else 10.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.12f else 0.18f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.20f else 0.28f),
            )
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(BorderStroke(1.2.dp, softGlassBorder), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        LocalFrostedGlassBackdrop(
            modifier = Modifier.matchParentSize(),
            cornerRadiusPx = with(LocalDensity.current) { 24.dp.toPx() },
            solidFallbackColor = MaterialTheme.colorScheme.surface.toArgb(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(softGlassSurface)
        )

        Icon(
            painter = iconPainter,
            contentDescription = "恢复屏幕绘制",
            modifier = Modifier.size(22.dp),
            tint = scheme.onSurface.copy(alpha = 0.88f),
        )

        if (hasColorBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .size(11.dp)
                    .shadow(1.5.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .padding(1.2.dp)
                    .background(Color(color), CircleShape)
            )
        }
    }
}
