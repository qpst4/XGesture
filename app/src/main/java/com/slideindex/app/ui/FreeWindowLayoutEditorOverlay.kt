package com.slideindex.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.FreeWindowLayoutDp
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAB_PORTRAIT = 0
private const val TAB_LANDSCAPE = 1

/**
 * 小窗物理尺寸位置编辑层（以 dp 为单位，直接对齐 GestureEVO 交互规范）。
 *
 * - 挂在 App 根部，盖住整屏，半透明黑底 + 可拖拽/缩放的窗口预览；
 * - 顶部切竖屏/横屏会把宿主 Activity 真实旋转，画布就是真实屏幕物理空间；
 * - 拖动窗口改位置 (leftDp, topDp)、拖右下角手柄改尺寸 (widthDp, heightDp)；
 * - 点“完成并保存”一次性写回两套 dp 参数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeWindowLayoutEditorOverlay(
    portrait: FreeWindowLayoutDp,
    landscape: FreeWindowLayoutDp,
    onDismiss: () -> Unit,
    onSave: (portrait: FreeWindowLayoutDp, landscape: FreeWindowLayoutDp) -> Unit,
) {
    val activity = LocalActivity.current
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_PORTRAIT) }
    val portraitState = remember(portrait) { mutableStateOf(portrait) }
    val landscapeState = remember(landscape) { mutableStateOf(landscape) }
    val isLandscape = selectedTab == TAB_LANDSCAPE
    val currentLayout = if (isLandscape) landscapeState.value else portraitState.value

    fun applyLayout(transform: (FreeWindowLayoutDp) -> FreeWindowLayoutDp) {
        val target = if (isLandscape) landscapeState else portraitState
        target.value = transform(target.value)
    }

    DisposableEffect(selectedTab) {
        FreeWindowPreviewOrientationSession.apply(isLandscape, activity)
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose { FreeWindowPreviewOrientationSession.scheduleRelease(activity) }
    }
    BackHandler(enabled = true) { onDismiss() }

    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MiuixTheme.colorScheme.primary,
        activeContentColor = MiuixTheme.colorScheme.onPrimary,
        inactiveContainerColor = MiuixTheme.colorScheme.surfaceContainer,
        inactiveContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val canvasWidthPx = constraints.maxWidth.toFloat()
            val canvasHeightPx = constraints.maxHeight.toFloat()
            val canvasWidthDp = with(density) { canvasWidthPx.toDp().value }.roundToInt()
            val canvasHeightDp = with(density) { canvasHeightPx.toDp().value }.roundToInt()

            val wPx = with(density) { currentLayout.widthDp.dp.toPx() }
            val hPx = with(density) { currentLayout.heightDp.dp.toPx() }
            val maxLeftPx = (canvasWidthPx - wPx).coerceAtLeast(0f)
            val maxTopPx = (canvasHeightPx - hPx).coerceAtLeast(0f)

            val offsetXPx = with(density) { currentLayout.leftDp.dp.toPx() }.coerceIn(0f, maxLeftPx)
            val offsetYPx = with(density) { currentLayout.topDp.dp.toPx() }.coerceIn(0f, maxTopPx)

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
                    .size(
                        width = with(density) { wPx.toDp() },
                        height = with(density) { hPx.toDp() },
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.22f))
                    .border(2.5.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .pointerInput(selectedTab, canvasWidthPx, canvasHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            applyLayout { layout ->
                                val dragAmountDpX = with(density) { dragAmount.x.toDp().value }.roundToInt()
                                val dragAmountDpY = with(density) { dragAmount.y.toDp().value }.roundToInt()
                                val maxLeftDp = (canvasWidthDp - layout.widthDp).coerceAtLeast(0)
                                val maxTopDp = (canvasHeightDp - layout.heightDp).coerceAtLeast(0)
                                val newLeftDp = (layout.leftDp + dragAmountDpX).coerceIn(0, maxLeftDp)
                                val newTopDp = (layout.topDp + dragAmountDpY).coerceIn(0, maxTopDp)
                                val rightInsetDp = (canvasWidthDp - newLeftDp - layout.widthDp).coerceAtLeast(0)
                                val edgeMarginDp = minOf(newLeftDp, rightInsetDp)
                                layout.copy(
                                    leftDp = newLeftDp,
                                    topDp = newTopDp,
                                    edgeMarginDp = edgeMarginDp,
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${currentLayout.widthDp} × ${currentLayout.heightDp} dp",
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(48.dp)
                        .pointerInput(selectedTab, canvasWidthPx, canvasHeightPx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                applyLayout { layout ->
                                    val dragAmountDpX = with(density) { dragAmount.x.toDp().value }.roundToInt()
                                    val dragAmountDpY = with(density) { dragAmount.y.toDp().value }.roundToInt()
                                    val maxAllowedWDp = (canvasWidthDp - layout.leftDp).coerceAtLeast(100)
                                    val maxAllowedHDp = (canvasHeightDp - layout.topDp).coerceAtLeast(100)
                                    val newWDp = (layout.widthDp + dragAmountDpX).coerceIn(100, maxAllowedWDp)
                                    val newHDp = (layout.heightDp + dragAmountDpY).coerceIn(100, maxAllowedHDp)
                                    val maxLeftDp = (canvasWidthDp - newWDp).coerceAtLeast(0)
                                    val maxTopDp = (canvasHeightDp - newHDp).coerceAtLeast(0)
                                    val clampedLeftDp = layout.leftDp.coerceIn(0, maxLeftDp)
                                    val clampedTopDp = layout.topDp.coerceIn(0, maxTopDp)
                                    val rightInsetDp = (canvasWidthDp - clampedLeftDp - newWDp).coerceAtLeast(0)
                                    val edgeMarginDp = minOf(clampedLeftDp, rightInsetDp)
                                    layout.copy(
                                        widthDp = newWDp,
                                        heightDp = newHDp,
                                        leftDp = clampedLeftDp,
                                        topDp = clampedTopDp,
                                        edgeMarginDp = edgeMarginDp,
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .size(18.dp)
                            .border(
                                width = 3.dp,
                                color = MiuixTheme.colorScheme.primary,
                                shape = RoundedCornerShape(bottomEnd = 5.dp),
                            )
                    )
                }
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 380.dp),
        ) {
            listOf(
                stringResource(R.string.free_window_portrait_preview),
                stringResource(R.string.free_window_landscape_preview),
            ).forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    colors = segmentedColors,
                ) {
                    Text(text = label, maxLines = 1)
                }
            }
        }

        Button(
            onClick = {
                onSave(portraitState.value, landscapeState.value)
                onDismiss()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MiuixTheme.colorScheme.primary,
                contentColor = MiuixTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_action_confirm))
            Text(
                text = stringResource(R.string.free_window_preview_save),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
