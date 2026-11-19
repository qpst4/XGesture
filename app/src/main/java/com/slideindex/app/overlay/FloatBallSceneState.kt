package com.slideindex.app.overlay

import android.graphics.Rect
import android.util.DisplayMetrics
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.roundToInt

/**
 * 悬浮球 Display 层集中状态：球心、十字、线条与各 layer 可见性。
 * [ballCenterPx] 为 null 时表示停靠布局，非 null 时为拖拽中的绝对球心（屏幕坐标 px）。
 */
internal class FloatBallSceneState(initialSettings: AppSettings) {
    val settingsState: MutableState<AppSettings> = mutableStateOf(initialSettings)
    val ballDragging: MutableState<Boolean> = mutableStateOf(false)
    val stripZonePreview: MutableState<Boolean> = mutableStateOf(false)
    val styleVisualGeneration = mutableIntStateOf(0)
    /** 屏幕旋转等触发布局刷新时递增，驱动 Display 层按最新屏幕尺寸重绘。 */
    val screenLayoutGeneration = mutableIntStateOf(0)
    val cursorVisible: MutableState<Boolean> = mutableStateOf(false)
    val cursorPaused: MutableState<Boolean> = mutableStateOf(false)
    val cursorAnchor: MutableState<Offset> = mutableStateOf(Offset.Zero)
    val selectionStart: MutableState<Offset?> = mutableStateOf(null)
    val selectionPreviewBounds: MutableState<Rect?> = mutableStateOf(null)
    /** null = 停靠；非 null = 拖拽球心（屏幕绝对坐标）。 */
    val ballCenterPx: MutableState<Offset?> = mutableStateOf(null)
    val chromeVisible: MutableState<Boolean> = mutableStateOf(true)
    val lineVisible: MutableState<Boolean> = mutableStateOf(false)
    val ballVisible: MutableState<Boolean> = mutableStateOf(true)
    /** 闲置隐身：true = 球按 [ballIdleOpacity] 绘制（0 = 完全看不见）。 */
    val ballIdleHidden: MutableState<Boolean> = mutableStateOf(false)
    /** 是否处于闲置（超过判定时长且未触摸）：与隐身无关，「闲置时停止动图」也读它。 */
    val ballIdle: MutableState<Boolean> = mutableStateOf(false)
    val ballIdleOpacity: MutableState<Float> = mutableStateOf(0f)

    fun resolvedActiveSide(
        settings: AppSettings = settingsState.value,
        dragActiveSideOverride: FloatBallSide? = null
    ): FloatBallSide = dragActiveSideOverride ?: FloatBallLayout.resolvedActiveSide(settings)

    /** 停靠球心（屏幕坐标）。 */
    fun dockBallCenter(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
        isLandscape: Boolean = false,
    ): Offset {
        val ballSize = FloatBallLayout.ballSize(settings, metrics.density)
        val (left, top) = FloatBallLayout.keyboardAdjustedBallTopLeft(
            settings = settings,
            metrics = metrics,
            activeSide = activeSide,
            isLandscape = isLandscape,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
        return Offset(left + ballSize.widthPx / 2f, top + ballSize.heightPx / 2f)
    }

    /** 当前球心：拖拽优先，否则停靠。 */
    fun resolveBallCenter(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
        isLandscape: Boolean = false,
    ): Offset = ballCenterPx.value ?: dockBallCenter(
        settings,
        metrics,
        activeSide,
        screenWidthPx,
        screenHeightPx,
        isLandscape,
    )

    /** 球体窗口左上角，用于 Display 层定位（含半隐藏停靠）。 */
    fun ballWindowTopLeft(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        center: Offset,
        screenHeightPx: Int = metrics.heightPixels
    ): Pair<Int, Int> {
        val ballSize = FloatBallLayout.ballSize(settings, metrics.density)
        return if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            (center.x - ballSize.widthPx / 2f).roundToInt() to
                (center.y - ballSize.heightPx / 2f).roundToInt()
        } else {
            FloatBallLayout.stripWindowOriginForBallCenter(
                settings = settings,
                metrics = metrics,
                activeSide = activeSide,
                ballCenterX = center.x,
                ballCenterY = center.y,
                screenHeightPx = screenHeightPx
            )
        }
    }

    fun ballHitRect(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
        isLandscape: Boolean = false,
    ): Rect {
        if (FloatBallLayout.isBothLines(settings)) {
            // 「两侧都是线」：球侧空闲时不显示球，触发区与对侧线条同规格。
            return FloatBallLayout.keyboardAdjustedLineStripBounds(
                settings = settings,
                metrics = metrics,
                side = activeSide,
                isLandscape = isLandscape,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
        }
        val center = resolveBallCenter(
            settings,
            metrics,
            activeSide,
            screenWidthPx,
            screenHeightPx,
            isLandscape,
        )
        val ballSize = FloatBallLayout.ballSize(settings, metrics.density)
        val (left, top) = ballWindowTopLeft(settings, metrics, activeSide, center, screenHeightPx)
        return Rect(left, top, left + ballSize.widthPx, top + ballSize.heightPx)
    }

    fun lineHitRect(
        settings: AppSettings,
        metrics: DisplayMetrics,
        inactiveSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
        isLandscape: Boolean = false,
    ): Rect = FloatBallLayout.keyboardAdjustedLineStripBounds(
        settings = settings,
        metrics = metrics,
        side = inactiveSide,
        isLandscape = isLandscape,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
    )

    /** 空闲态球体触摸窗 bounds（仅球区；线条由独立触摸窗覆盖）。 */
    fun ballTouchBounds(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
        isLandscape: Boolean = false,
    ): Rect = ballHitRect(settings, metrics, activeSide, screenWidthPx, screenHeightPx, isLandscape)
}
