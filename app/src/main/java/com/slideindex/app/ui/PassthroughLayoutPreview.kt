package com.slideindex.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.TriggerZonePreviewRenderer
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.withSyncedTriggerHandleDesign
import com.slideindex.app.ui.navigation.AppNavKey

private val LocalFocusedTriggerPreview = compositionLocalOf<FocusedTriggerDrawable?> { null }

@Composable
internal fun FocusedSideTriggerLayoutPreviewHost(
    focus: AppNavKey.HomeSideGestures?,
    content: @Composable () -> Unit,
) {
    val decor = LocalActivity.current?.window?.decorView
    val drawable = remember(decor, focus) {
        if (decor != null && focus != null) FocusedTriggerDrawable(decor) else null
    }
    if (decor != null && drawable != null) {
        DisposableEffect(decor, drawable) {
            val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                drawable.setBounds(0, 0, decor.width, decor.height)
            }
            drawable.setBounds(0, 0, decor.width, decor.height)
            decor.overlay.add(drawable)
            decor.addOnLayoutChangeListener(listener)
            onDispose {
                decor.removeOnLayoutChangeListener(listener)
                decor.overlay.remove(drawable)
            }
        }
    }
    CompositionLocalProvider(LocalFocusedTriggerPreview provides drawable, content = content)
}

@Composable
internal fun FocusedSideTriggerLayoutPreview(
    settings: AppSettings?,
    side: PanelSide,
    handleId: String,
    draftWidth: Float? = null,
    draftSpan: ClosedFloatingPointRange<Float>? = null,
    swipeDistances: Pair<Float, Float>? = null,
    designDraft: TriggerHandleDesign? = null,
    showRange: Boolean = true,
) {
    val drawable = LocalFocusedTriggerPreview.current
    val designSettings = if (designDraft != null) settings?.withSyncedTriggerHandleDesign(side, handleId, designDraft) else null
    val showOppositeRange = showRange && settings?.triggerHandle(side, handleId)?.alignOppositeDesign == true
    SideEffect {
        drawable?.update { canvas, zone, width, height, density ->
            if (settings != null) {
                val bounds = focusedSideTriggerBounds(settings, side, handleId, width, height, density, draftWidth, draftSpan)
                if (bounds != null) {
                    zone.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    if (showRange) TriggerZonePreviewRenderer.drawLayoutBounds(canvas, zone, density)
                    swipeDistances?.let { (shortDistance, longDistance) ->
                        TriggerZonePreviewRenderer.drawSwipeDistancePreview(canvas, side, zone, shortDistance, longDistance) {
                            it * density
                        }
                    }
                }
                if (designSettings != null) {
                    drawTriggerDesign(canvas, zone, designSettings, side, handleId, width, height, density)
                    if (side.isHorizontalEdge) {
                        drawTriggerDesign(
                            canvas, zone, designSettings, side.opposite(), handleId, width, height, density,
                            showRange = showOppositeRange,
                        )
                    }
                }
            }
        }
    }
}

private fun drawTriggerDesign(
    canvas: Canvas,
    zone: RectF,
    settings: AppSettings,
    side: PanelSide,
    handleId: String,
    width: Int,
    height: Int,
    density: Float,
    showRange: Boolean = false,
) {
    val handle = settings.triggerHandle(side, handleId) ?: return
    val bounds = focusedSideTriggerBounds(settings, side, handleId, width, height, density) ?: return
    zone.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
    if (showRange) TriggerZonePreviewRenderer.drawLayoutBounds(canvas, zone, density)
    TriggerZonePreviewRenderer.drawHandleDesign(canvas, side, handle.design, zone, density)
}

private class FocusedTriggerDrawable(private val decor: View) : Drawable() {
    private val zone = RectF()
    private var drawPreview: ((Canvas, RectF, Int, Int, Float) -> Unit)? = null

    fun update(drawPreview: (Canvas, RectF, Int, Int, Float) -> Unit) {
        this.drawPreview = drawPreview
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val density = decor.resources.displayMetrics.density
        drawPreview?.invoke(canvas, zone, bounds.width(), bounds.height(), density)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
