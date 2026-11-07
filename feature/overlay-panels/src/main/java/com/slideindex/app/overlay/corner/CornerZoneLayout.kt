package com.slideindex.app.overlay.corner



import android.graphics.RectF

import com.slideindex.app.settings.CornerGestureSettings



data class CornerZoneShape(

    val verticalRect: RectF,

    val horizontalRect: RectF,

    val bounds: RectF,

)



class CornerZoneLayout {

    private var screenWidthPx: Int = 0

    private var screenHeightPx: Int = 0

    private var density: Float = 1f

    private var settings: CornerGestureSettings = CornerGestureSettings()



    fun update(

        screenWidthPx: Int,

        screenHeightPx: Int,

        density: Float,

        settings: CornerGestureSettings,

    ) {

        this.screenWidthPx = screenWidthPx

        this.screenHeightPx = screenHeightPx

        this.density = density

        this.settings = settings

    }



    fun zoneShape(anchor: CornerAnchor): CornerZoneShape {

        val verticalWidth = settings.verticalEdgeWidthDp * density

        val verticalHeight = settings.verticalEdgeHeightDp * density

        val horizontalWidth = settings.horizontalEdgeWidthDp * density

        val horizontalHeight = settings.horizontalEdgeHeightDp * density

        val screenW = screenWidthPx.toFloat()

        val screenH = screenHeightPx.toFloat()



        return when (anchor) {

            CornerAnchor.LEFT -> {

                val vertical = RectF(0f, screenH - verticalHeight, verticalWidth, screenH)

                val horizontal = RectF(0f, screenH - horizontalHeight, horizontalWidth, screenH)

                CornerZoneShape(

                    verticalRect = vertical,

                    horizontalRect = horizontal,

                    bounds = RectF(

                        0f,

                        screenH - maxOf(verticalHeight, horizontalHeight),

                        maxOf(verticalWidth, horizontalWidth),

                        screenH,

                    ),

                )

            }

            CornerAnchor.RIGHT -> {

                val vertical = RectF(screenW - verticalWidth, screenH - verticalHeight, screenW, screenH)

                val horizontal = RectF(screenW - horizontalWidth, screenH - horizontalHeight, screenW, screenH)

                CornerZoneShape(

                    verticalRect = vertical,

                    horizontalRect = horizontal,

                    bounds = RectF(

                        screenW - maxOf(verticalWidth, horizontalWidth),

                        screenH - maxOf(verticalHeight, horizontalHeight),

                        screenW,

                        screenH,

                    ),

                )

            }

        }

    }



    fun zoneRect(anchor: CornerAnchor): RectF = zoneShape(anchor).bounds

    fun stripRect(anchor: CornerAnchor, strip: CornerZoneStrip): RectF? {
        val shape = zoneShape(anchor)
        val rect = when (strip) {
            CornerZoneStrip.VERTICAL -> shape.verticalRect
            CornerZoneStrip.HORIZONTAL -> shape.horizontalRect
        }
        return rect.takeIf { it.width() > 0f && it.height() > 0f }
    }



    fun anchorCenter(anchor: CornerAnchor): Pair<Float, Float> = when (anchor) {

        CornerAnchor.LEFT -> 0f to screenHeightPx.toFloat()

        CornerAnchor.RIGHT -> screenWidthPx.toFloat() to screenHeightPx.toFloat()

    }



    fun contains(anchor: CornerAnchor, rawX: Float, rawY: Float): Boolean {
        val shape = zoneShape(anchor)
        return rectContains(shape.verticalRect, rawX, rawY) ||
            rectContains(shape.horizontalRect, rawX, rawY)
    }

    private fun rectContains(rect: RectF, x: Float, y: Float): Boolean {
        if (rect.width() <= 0f || rect.height() <= 0f) return false
        return rect.contains(x, y)
    }



    fun hitAnchor(rawX: Float, rawY: Float): CornerAnchor? {

        if (settings.leftEnabled && contains(CornerAnchor.LEFT, rawX, rawY)) return CornerAnchor.LEFT

        if (settings.rightEnabled && contains(CornerAnchor.RIGHT, rawX, rawY)) return CornerAnchor.RIGHT

        return null

    }

}


