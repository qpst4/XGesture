package com.slideindex.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.BottomNavBlurDefaults
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.slideindex.app.ui.a11y.cdBottomNavExtension
import com.slideindex.app.ui.a11y.cdBottomNavHome
import com.slideindex.app.ui.a11y.cdBottomNavNotification
import com.slideindex.app.ui.a11y.cdBottomNavShake
import kotlin.math.min
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class MainBottomNavDestination {
    Home,
    Shake,
    Notification,
    Extension,
}

val MainBottomNavHeight = 72.dp
val MainBottomNavIconOnlyHeight = 48.dp
val MainBottomNavOuterPadding = 16.dp
val MainBottomNavHorizontalPadding = 24.dp

fun classicBottomNavBarHeight(showLabels: Boolean): Dp =
    if (showLabels) MainBottomNavHeight else MainBottomNavIconOnlyHeight

fun classicNavRailWidth(showLabels: Boolean): Dp =
    if (showLabels) MainNavRailWidth else MainNavRailIconOnlyWidth

@Composable
private fun rememberClassicSideNavStartInset(): Dp {
    val layoutDirection = LocalLayoutDirection.current
    return WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Start)
        .asPaddingValues()
        .calculateStartPadding(layoutDirection)
}

@Composable
fun classicFloatingSideNavRailSlotWidth(showLabels: Boolean = true): Dp {
    return rememberClassicSideNavStartInset() +
        MainBottomNavOuterPadding +
        classicNavRailWidth(showLabels)
}

/**
 * 经典毛玻璃宽屏侧栏：全屏 overlay 悬浮胶囊，不铺通高填色以免顶进状态栏。
 */
@Composable
fun ClassicFloatingSideNavRailOverlay(
    hazeState: HazeState,
    glassEnabled: Boolean,
    selected: MainBottomNavDestination,
    blurRadiusDp: Float,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val startInset = rememberClassicSideNavStartInset()
    Box(
        modifier = modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .padding(start = startInset)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(start = MainBottomNavOuterPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        FloatingSideNavRail(
            hazeState = hazeState,
            glassEnabled = glassEnabled,
            selected = selected,
            blurRadiusDp = blurRadiusDp,
            showLabels = showLabels,
            onDestinationSelected = onDestinationSelected,
        )
    }
}

/**
 * 经典毛玻璃的着色不透明度。
 *
 * 着色取容器色（`MiuixTheme.colorScheme.surfaceContainer`）而非正文色：
 * 正文色在浅色主题下接近黑色，8% 的暗纱会把模糊层压成一片灰（实测 #E4E4E4），
 * 比页面背景 #F7F7F7 还暗，看起来就是"脏"。
 */
private const val MainBottomNavGlassTintAlpha = 0.5f
/** 极细噪点，避免大半径模糊出现色带/斑块。 */
private const val MainBottomNavGlassNoiseFactor = 0.08f
private val MainBottomNavIndicatorInset = 4.dp
private const val MainBottomNavPressOverlayAlpha = 0.08f
private val MainBottomNavIconSize = 24.dp
private val MainBottomNavIndicatorSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
)
private const val MainBottomNavItemTweenDurationMs = 180
private val MainBottomNavItemTween = tween<Float>(
    durationMillis = MainBottomNavItemTweenDurationMs,
    easing = FastOutSlowInEasing,
)
private val MainBottomNavItemColorTween = tween<Color>(
    durationMillis = MainBottomNavItemTweenDurationMs,
    easing = FastOutSlowInEasing,
)

@Composable
private fun rememberBottomNavGlassStyle(blurRadius: Dp) = HazeDefaults.style(
    backgroundColor = MiuixTheme.colorScheme.background,
    tint = HazeTint(
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = MainBottomNavGlassTintAlpha),
    ),
    blurRadius = blurRadius,
    noiseFactor = MainBottomNavGlassNoiseFactor,
)

private fun DrawScope.drawNavItemCapsule(
    itemIndex: Float,
    itemCount: Int,
    color: Color,
    vertical: Boolean,
    hugIcon: Boolean,
    inset: Dp = MainBottomNavIndicatorInset,
) {
    if (itemCount <= 0 || size.width <= 0f || size.height <= 0f) return
    val insetPx = inset.toPx()
    if (vertical) {
        val itemHeightPx = size.height / itemCount
        if (hugIcon) {
            val side = (size.width - insetPx * 2f).coerceAtLeast(0f)
            val left = insetPx
            val top = itemHeightPx * itemIndex + (itemHeightPx - side) / 2f
            val radius = side / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(side, side),
                cornerRadius = CornerRadius(radius, radius),
            )
        } else {
            val capInsetPx = insetPx / 2f
            val innerRadius = (size.width / 2f - capInsetPx).coerceAtLeast(0f)
            val topCap = (1f - itemIndex).coerceIn(0f, 1f)
            val bottomCap = (itemIndex - (itemCount - 1f)).coerceIn(0f, 1f)
            val bandTop = itemHeightPx * itemIndex + insetPx * (1f - topCap)
            val bandBottom = itemHeightPx * (itemIndex + 1f) - insetPx * (1f - bottomCap)
            val bandHeight = (bandBottom - bandTop).coerceAtLeast(0f)
            if (bandHeight <= 0f) return
            val cutRadius = min(size.width - capInsetPx * 2f, bandHeight) / 2f
            val innerStadium = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = capInsetPx,
                        top = capInsetPx,
                        right = size.width - capInsetPx,
                        bottom = size.height - capInsetPx,
                        radiusX = innerRadius,
                        radiusY = innerRadius,
                    ),
                )
            }
            val band = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = bandTop,
                        right = size.width,
                        bottom = bandBottom,
                        topLeftCornerRadius = CornerRadius(cutRadius * (1f - topCap)),
                        topRightCornerRadius = CornerRadius(cutRadius * (1f - topCap)),
                        bottomRightCornerRadius = CornerRadius(cutRadius * (1f - bottomCap)),
                        bottomLeftCornerRadius = CornerRadius(cutRadius * (1f - bottomCap)),
                    ),
                )
            }
            drawPath(
                path = Path().apply { op(innerStadium, band, PathOperation.Intersect) },
                color = color,
            )
        }
    } else {
        val itemWidthPx = size.width / itemCount
        if (hugIcon) {
            val side = (size.height - insetPx * 2f).coerceAtLeast(0f)
            val left = itemWidthPx * itemIndex + (itemWidthPx - side) / 2f
            val top = insetPx
            val radius = side / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(side, side),
                cornerRadius = CornerRadius(radius, radius),
            )
        } else {
            val capsuleWidthPx = (itemWidthPx - insetPx * 2f).coerceAtLeast(0f)
            val capsuleHeightPx = (size.height - insetPx * 2f).coerceAtLeast(0f)
            val left = itemWidthPx * itemIndex + insetPx
            val top = insetPx
            val radius = capsuleHeightPx / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(capsuleWidthPx, capsuleHeightPx),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}

private fun DrawScope.drawNavSelection(
    pressed: Int?,
    selectedIndex: Int,
    indicatorOffset: Float,
    itemCount: Int,
    indicatorColor: Color,
    pressOverlayColor: Color,
    vertical: Boolean,
    hugIcon: Boolean,
) {
    fun drawAt(index: Float, color: Color) {
        drawNavItemCapsule(
            itemIndex = index,
            itemCount = itemCount,
            color = color,
            vertical = vertical,
            hugIcon = hugIcon,
        )
    }
    when {
        pressed == null -> drawAt(indicatorOffset, indicatorColor)
        pressed == selectedIndex -> {
            drawAt(pressed.toFloat(), indicatorColor)
            drawAt(pressed.toFloat(), pressOverlayColor)
        }
        else -> {
            drawAt(indicatorOffset, indicatorColor)
            drawAt(pressed.toFloat(), indicatorColor)
            drawAt(pressed.toFloat(), pressOverlayColor)
        }
    }
}

@Composable
fun FloatingBottomNavBar(
    hazeState: HazeState,
    glassEnabled: Boolean,
    selected: MainBottomNavDestination,
    blurRadiusDp: Float,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val selectedIndex = destinations.indexOf(selected).coerceAtLeast(0)
    val itemCount = destinations.size
    val barHeight = classicBottomNavBarHeight(showLabels)
    val barShape = RoundedCornerShape(barHeight / 2)
    val hugIcon = !showLabels
    val barBackground = MiuixTheme.colorScheme.background
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val pressOverlayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MainBottomNavPressOverlayAlpha)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val blurRadius = blurRadiusDp.coerceIn(
        BottomNavBlurDefaults.MIN_RADIUS_DP,
        BottomNavBlurDefaults.MAX_RADIUS_DP,
    ).dp
    val glassStyle = rememberBottomNavGlassStyle(blurRadius)
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MainBottomNavIndicatorSpring,
        label = "bottomNavIndicatorOffset",
    )

    Box(
        modifier = modifier
            .then(
                if (showLabels) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.width(barHeight * destinations.size)
                }
            )
            .height(barHeight)
            .shadow(4.dp, barShape, clip = false)
            .clip(barShape),
    ) {
        if (glassEnabled) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = barShape,
                color = barBackground,
            ) {}
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = glassStyle),
            )
        } else {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = barShape,
                color = barBackground,
            ) {}
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 0.5.dp, color = borderColor, shape = barShape),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawNavSelection(
                        pressed = pressedIndex,
                        selectedIndex = selectedIndex,
                        indicatorOffset = indicatorOffset,
                        itemCount = itemCount,
                        indicatorColor = indicatorColor,
                        pressOverlayColor = pressOverlayColor,
                        vertical = false,
                        hugIcon = hugIcon,
                    )
                },
        )
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Home,
                    showLabel = showLabels,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 0
                            pressedIndex == 0 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Home)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) classicNavFilledSymbol("Home") else classicNavSymbol("Home"),
                            contentDescription = cdBottomNavHome(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_home),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Shake,
                    showLabel = showLabels,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 1
                            pressedIndex == 1 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Shake)
                    },
                    icon = { isSelected ->
                        Icon(
                            painter = painterResource(
                                if (isSelected) R.drawable.ic_nav_shake else R.drawable.ic_nav_shake_outlined,
                            ),
                            contentDescription = cdBottomNavShake(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_shake),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Notification,
                    showLabel = showLabels,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 2
                            pressedIndex == 2 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Notification)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) classicNavFilledSymbol("Notifications") else classicNavSymbol("Notifications"),
                            contentDescription = cdBottomNavNotification(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_notification),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Extension,
                    showLabel = showLabels,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 3
                            pressedIndex == 3 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Extension)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) classicNavFilledSymbol("Widgets") else classicNavSymbol("Widgets"),
                            contentDescription = cdBottomNavExtension(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_extension),
                )
            }
    }
}

@Composable
fun FloatingSideNavRail(
    hazeState: HazeState,
    glassEnabled: Boolean,
    selected: MainBottomNavDestination,
    blurRadiusDp: Float,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val selectedIndex = destinations.indexOf(selected).coerceAtLeast(0)
    val itemCount = destinations.size
    val railWidth = classicNavRailWidth(showLabels)
    val barShape = RoundedCornerShape(railWidth / 2)
    val hugIcon = !showLabels
    val barBackground = MiuixTheme.colorScheme.background
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val pressOverlayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MainBottomNavPressOverlayAlpha)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val blurRadius = blurRadiusDp.coerceIn(
        BottomNavBlurDefaults.MIN_RADIUS_DP,
        BottomNavBlurDefaults.MAX_RADIUS_DP,
    ).dp
    val glassStyle = rememberBottomNavGlassStyle(blurRadius)
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MainBottomNavIndicatorSpring,
        label = "sideNavIndicatorOffset",
    )

    Box(
        modifier = modifier
            .width(railWidth)
            .wrapContentHeight()
            .shadow(2.dp, barShape, clip = false)
            .clip(barShape),
    ) {
        if (glassEnabled) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = barShape,
                color = barBackground,
            ) {}
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = glassStyle),
            )
        } else {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = barShape,
                color = barBackground,
            ) {}
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 0.5.dp, color = borderColor, shape = barShape),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawNavSelection(
                        pressed = pressedIndex,
                        selectedIndex = selectedIndex,
                        indicatorOffset = indicatorOffset,
                        itemCount = itemCount,
                        indicatorColor = indicatorColor,
                        pressOverlayColor = pressOverlayColor,
                        vertical = true,
                        hugIcon = hugIcon,
                    )
                },
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            FloatingSideNavItem(
                selected = selected == MainBottomNavDestination.Home,
                showLabel = showLabels,
                onPressedChange = { isPressed ->
                    pressedIndex = when {
                        isPressed -> 0
                        pressedIndex == 0 -> null
                        else -> pressedIndex
                    }
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onDestinationSelected(MainBottomNavDestination.Home)
                },
                icon = { isSelected ->
                    Icon(
                        imageVector = if (isSelected) classicNavFilledSymbol("Home") else classicNavSymbol("Home"),
                        contentDescription = cdBottomNavHome(),
                        modifier = Modifier.size(MainBottomNavIconSize),
                    )
                },
                label = stringResource(R.string.main_nav_home),
            )
            FloatingSideNavItem(
                selected = selected == MainBottomNavDestination.Shake,
                showLabel = showLabels,
                onPressedChange = { isPressed ->
                    pressedIndex = when {
                        isPressed -> 1
                        pressedIndex == 1 -> null
                        else -> pressedIndex
                    }
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onDestinationSelected(MainBottomNavDestination.Shake)
                },
                icon = { isSelected ->
                    Icon(
                        painter = painterResource(
                            if (isSelected) R.drawable.ic_nav_shake else R.drawable.ic_nav_shake_outlined,
                        ),
                        contentDescription = cdBottomNavShake(),
                        modifier = Modifier.size(MainBottomNavIconSize),
                    )
                },
                label = stringResource(R.string.main_nav_shake),
            )
            FloatingSideNavItem(
                selected = selected == MainBottomNavDestination.Notification,
                showLabel = showLabels,
                onPressedChange = { isPressed ->
                    pressedIndex = when {
                        isPressed -> 2
                        pressedIndex == 2 -> null
                        else -> pressedIndex
                    }
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onDestinationSelected(MainBottomNavDestination.Notification)
                },
                icon = { isSelected ->
                    Icon(
                        imageVector = if (isSelected) classicNavFilledSymbol("Notifications") else classicNavSymbol("Notifications"),
                        contentDescription = cdBottomNavNotification(),
                        modifier = Modifier.size(MainBottomNavIconSize),
                    )
                },
                label = stringResource(R.string.main_nav_notification),
            )
            FloatingSideNavItem(
                selected = selected == MainBottomNavDestination.Extension,
                showLabel = showLabels,
                onPressedChange = { isPressed ->
                    pressedIndex = when {
                        isPressed -> 3
                        pressedIndex == 3 -> null
                        else -> pressedIndex
                    }
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onDestinationSelected(MainBottomNavDestination.Extension)
                },
                icon = { isSelected ->
                    Icon(
                        imageVector = if (isSelected) classicNavFilledSymbol("Widgets") else classicNavSymbol("Widgets"),
                        contentDescription = cdBottomNavExtension(),
                        modifier = Modifier.size(MainBottomNavIconSize),
                    )
                },
                label = stringResource(R.string.main_nav_extension),
            )
        }
    }
}

@Composable
private fun FloatingSideNavItem(
    selected: Boolean,
    showLabel: Boolean = true,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    icon: @Composable (selected: Boolean) -> Unit,
    label: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        onPressedChange(pressed)
    }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MainBottomNavItemColorTween,
        label = "sideNavItemColor",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!showLabel) Modifier.height(MainNavRailIconOnlyWidth) else Modifier
            )
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(MainBottomNavIndicatorInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = MainBottomNavItemTween,
            label = "sideNavIcon",
        ) { isSelected ->
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon(isSelected)
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RowScope.FloatingBottomNavItem(
    selected: Boolean,
    showLabel: Boolean = true,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    icon: @Composable (selected: Boolean) -> Unit,
    label: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        onPressedChange(pressed)
    }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MainBottomNavItemColorTween,
        label = "bottomNavItemColor",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(MainBottomNavIndicatorInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = MainBottomNavItemTween,
            label = "bottomNavIcon",
        ) { isSelected ->
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon(isSelected)
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/** 经典底栏与液态玻璃/Miuix 底栏共用 Material Symbols（wght=350）。 */
private fun classicNavSymbol(name: String): ImageVector =
    requireNotNull(MaterialSymbolVectors.get(name)) { "unknown Material Symbol: $name" }

/** 选中态使用同一套 Material Symbols 的 FILL=1 变体，避免切换时换几何。 */
private fun classicNavFilledSymbol(name: String): ImageVector =
    requireNotNull(MaterialSymbolVectors.getFilled(name)) { "unknown filled Material Symbol: $name" }
