package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * Display 层根容器：线条（Compose）、球体与取词准星（原生 View）。
 *
 * 对齐 v1.50.0 的结构：**球在这一层里按坐标 layout，触摸窗是另一个窗、按命中区单独设尺寸**。
 * 两者解耦，所以「两侧都是线」模式下命中区是整条、球还是原来的小球。
 */
@SuppressLint("ViewConstructor")
internal class FloatBallDisplayHost(
    context: Context,
    private val lineChromeOwner: OverlayComposeOwner,
    val ballIconView: FloatBallIconView,
    val cursorPreviewView: FloatBallCursorPreviewView
) : FrameLayout(context) {

    private var lastBallLeft = 0
    private var lastBallTop = 0
    private var lastBallWidth = 0
    private var lastBallHeight = 0
    private var hasBallLayout = false

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipChildren = false
        OverlayCompose.bindOwners(this, lineChromeOwner)

        ballIconView.layoutParams = LayoutParams(0, 0)
        addView(ballIconView)

        cursorPreviewView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(cursorPreviewView)
    }

    /** 原生定位球体；热路径直接 layout，不触发整树 requestLayout。 */
    fun layoutBall(left: Int, top: Int, widthPx: Int, heightPx: Int) {
        if (
            hasBallLayout &&
            left == lastBallLeft &&
            top == lastBallTop &&
            widthPx == lastBallWidth &&
            heightPx == lastBallHeight
        ) return
        val sizeChanged = widthPx != lastBallWidth || heightPx != lastBallHeight
        hasBallLayout = widthPx > 0 && heightPx > 0
        lastBallLeft = left
        lastBallTop = top
        lastBallWidth = widthPx
        lastBallHeight = heightPx
        if (width <= 0 || height <= 0) {
            requestLayout()
            return
        }
        if (sizeChanged) {
            ballIconView.measure(
                MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY)
            )
        }
        ballIconView.layout(left, top, left + widthPx, top + heightPx)
    }

    fun setBallVisible(visible: Boolean) {
        ballIconView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val fullWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val fullHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        cursorPreviewView.measure(fullWidthSpec, fullHeightSpec)
        if (hasBallLayout && lastBallWidth > 0 && lastBallHeight > 0) {
            ballIconView.measure(
                MeasureSpec.makeMeasureSpec(lastBallWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(lastBallHeight, MeasureSpec.EXACTLY)
            )
        } else {
            ballIconView.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        cursorPreviewView.layout(0, 0, width, height)
        if (hasBallLayout && lastBallWidth > 0 && lastBallHeight > 0) {
            ballIconView.layout(
                lastBallLeft,
                lastBallTop,
                lastBallLeft + lastBallWidth,
                lastBallTop + lastBallHeight
            )
        } else {
            ballIconView.layout(0, 0, 0, 0)
        }
    }

    fun dispose() {
        // 线条已改为原生窄窗，display 层不再持有 Compose 内容。
    }
}
