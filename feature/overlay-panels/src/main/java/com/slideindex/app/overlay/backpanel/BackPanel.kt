package com.slideindex.app.overlay.backpanel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.min

/** Ported from AOSP SystemUI `BackPanel` (Apache-2.0). */
class BackPanel(context: Context) : View(context) {

    var arrowsPointLeft = false
        set(value) {
            if (field != value) {
                invalidate()
                field = value
            }
        }

    private val arrowPath = Path()
    private val arrowPaint = Paint()
    private var arrowBackgroundRect = RectF()
    private var arrowBackgroundPaint = Paint()

    var isLeftPanel = false

    private var arrowLength = AnimatedFloat(
        name = "arrowLength",
        minimumVisibleChange = SpringAnimation.MIN_VISIBLE_CHANGE_PIXELS,
    )

    var arrowHeight = AnimatedFloat(
        name = "arrowHeight",
        minimumVisibleChange = SpringAnimation.MIN_VISIBLE_CHANGE_ROTATION_DEGREES,
    )

    val backgroundWidth = AnimatedFloat(
        name = "backgroundWidth",
        minimumVisibleChange = SpringAnimation.MIN_VISIBLE_CHANGE_PIXELS,
        minimumValue = 0f,
    )

    val backgroundHeight = AnimatedFloat(
        name = "backgroundHeight",
        minimumVisibleChange = SpringAnimation.MIN_VISIBLE_CHANGE_PIXELS,
        minimumValue = 0f,
    )

    val backgroundEdgeCornerRadius = AnimatedFloat("backgroundEdgeCornerRadius")
    val backgroundFarCornerRadius = AnimatedFloat("backgroundFarCornerRadius")

    var scale = AnimatedFloat(
        name = "scale",
        minimumVisibleChange = SpringAnimation.MIN_VISIBLE_CHANGE_SCALE,
        minimumValue = 0f,
    )

    val scalePivotX = AnimatedFloat(
        name = "scalePivotX",
        minimumVisibleChange = SpringAnimation.MIN_VISIBLE_CHANGE_PIXELS,
        minimumValue = backgroundWidth.pos / 2,
    )

    var horizontalTranslation = AnimatedFloat(name = "horizontalTranslation")

    var arrowAlpha = AnimatedFloat(
        name = "arrowAlpha",
        minimumVisibleChange = SpringAnimation.MIN_VISIBLE_CHANGE_ALPHA,
        minimumValue = 0f,
        maximumValue = 1f,
    )

    val backgroundAlpha = AnimatedFloat(
        name = "backgroundAlpha",
        minimumVisibleChange = SpringAnimation.MIN_VISIBLE_CHANGE_ALPHA,
        minimumValue = 0f,
        maximumValue = 1f,
    )

    private val allAnimatedFloat = setOf(
        arrowLength,
        arrowHeight,
        backgroundWidth,
        backgroundEdgeCornerRadius,
        backgroundFarCornerRadius,
        scalePivotX,
        scale,
        horizontalTranslation,
        arrowAlpha,
        backgroundAlpha,
    )

    var verticalTranslation = AnimatedFloat("verticalTranslation")

    inner class AnimatedFloat(
        name: String,
        private val minimumVisibleChange: Float? = null,
        private val minimumValue: Float? = null,
        private val maximumValue: Float? = null,
    ) {
        private var restingPosition = 0f
        var pos = 0f
            private set(v) {
                if (field != v) {
                    field = v
                    invalidate()
                }
            }

        private val animation: SpringAnimation
        var spring: SpringForce
            get() = animation.spring
            set(value) {
                animation.cancel()
                animation.spring = value
            }

        val isRunning: Boolean
            get() = animation.isRunning

        fun addEndListener(listener: DynamicAnimation.OnAnimationEndListener) {
            animation.addEndListener(listener)
        }

        init {
            val floatProp = object : FloatPropertyCompat<AnimatedFloat>(name) {
                override fun setValue(animatedFloat: AnimatedFloat, value: Float) {
                    animatedFloat.pos = value
                }

                override fun getValue(animatedFloat: AnimatedFloat): Float = animatedFloat.pos
            }
            animation = SpringAnimation(this, floatProp).apply {
                spring = SpringForce()
                this@AnimatedFloat.minimumValue?.let { setMinValue(it) }
                this@AnimatedFloat.maximumValue?.let { setMaxValue(it) }
                this@AnimatedFloat.minimumVisibleChange?.let { minimumVisibleChange = it }
            }
        }

        fun snapTo(newPosition: Float) {
            animation.cancel()
            restingPosition = newPosition
            animation.spring.finalPosition = newPosition
            pos = newPosition
        }

        fun snapToRestingPosition() {
            snapTo(restingPosition)
        }

        fun stretchTo(
            stretchAmount: Float,
            startingVelocity: Float? = null,
            springForce: SpringForce? = null,
        ) {
            animation.apply {
                startingVelocity?.let {
                    cancel()
                    setStartVelocity(it)
                }
                springForce?.let { spring = springForce }
                animateToFinalPosition(restingPosition + stretchAmount)
            }
        }

        fun stretchBy(finalPosition: Float?, amount: Float) {
            val stretchedAmount = amount * ((finalPosition ?: 0f) - restingPosition)
            animation.animateToFinalPosition(restingPosition + stretchedAmount)
        }

        fun updateRestingPosition(pos: Float?, animated: Boolean = true) {
            if (pos == null) return
            restingPosition = pos
            if (animated) {
                animation.animateToFinalPosition(restingPosition)
            } else {
                snapTo(restingPosition)
            }
        }

        fun cancel() {
            animation.cancel()
        }
    }

    init {
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        arrowPaint.apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.SQUARE
        }
        arrowBackgroundPaint.apply {
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = 0xFFC4C6D0.toInt()
        }
        arrowPaint.color = 0xFF1A1B21.toInt()
    }

    internal fun updateArrowPaint(arrowThickness: Float) {
        arrowPaint.strokeWidth = arrowThickness
        invalidate()
    }

    fun setPaintColors(background: Int, arrow: Int) {
        arrowBackgroundPaint.color = background
        arrowPaint.color = arrow
        invalidate()
    }

    private fun calculateArrowPath(dx: Float, dy: Float): Path {
        arrowPath.reset()
        arrowPath.moveTo(dx, -dy)
        arrowPath.lineTo(0f, 0f)
        arrowPath.lineTo(dx, dy)
        arrowPath.moveTo(dx, -dy)
        return arrowPath
    }

    fun addAnimationEndListener(
        animatedFloat: AnimatedFloat,
        endListener: BackPanelController.DelayedOnAnimationEndListener,
    ): Boolean {
        return if (animatedFloat.isRunning) {
            animatedFloat.addEndListener(endListener)
            true
        } else {
            endListener.run()
            false
        }
    }

    fun cancelAnimations() {
        allAnimatedFloat.forEach { it.cancel() }
        verticalTranslation.cancel()
    }

    fun setStretch(
        horizontalTranslationStretchAmount: Float,
        arrowStretchAmount: Float,
        arrowAlphaStretchAmount: Float,
        backgroundAlphaStretchAmount: Float,
        backgroundWidthStretchAmount: Float,
        backgroundHeightStretchAmount: Float,
        edgeCornerStretchAmount: Float,
        farCornerStretchAmount: Float,
        fullyStretchedDimens: EdgePanelParams.BackIndicatorDimens,
    ) {
        horizontalTranslation.stretchBy(
            finalPosition = fullyStretchedDimens.horizontalTranslation,
            amount = horizontalTranslationStretchAmount,
        )
        arrowLength.stretchBy(
            finalPosition = fullyStretchedDimens.arrowDimens.length,
            amount = arrowStretchAmount,
        )
        arrowHeight.stretchBy(
            finalPosition = fullyStretchedDimens.arrowDimens.height,
            amount = arrowStretchAmount,
        )
        arrowAlpha.stretchBy(
            finalPosition = fullyStretchedDimens.arrowDimens.alpha,
            amount = arrowAlphaStretchAmount,
        )
        backgroundAlpha.stretchBy(
            finalPosition = fullyStretchedDimens.backgroundDimens.alpha,
            amount = backgroundAlphaStretchAmount,
        )
        backgroundWidth.stretchBy(
            finalPosition = fullyStretchedDimens.backgroundDimens.width,
            amount = backgroundWidthStretchAmount,
        )
        backgroundHeight.stretchBy(
            finalPosition = fullyStretchedDimens.backgroundDimens.height,
            amount = backgroundHeightStretchAmount,
        )
        backgroundEdgeCornerRadius.stretchBy(
            finalPosition = fullyStretchedDimens.backgroundDimens.edgeCornerRadius,
            amount = edgeCornerStretchAmount,
        )
        backgroundFarCornerRadius.stretchBy(
            finalPosition = fullyStretchedDimens.backgroundDimens.farCornerRadius,
            amount = farCornerStretchAmount,
        )
    }

    fun popOffEdge(startingVelocity: Float) {
        scale.stretchTo(stretchAmount = 0f, startingVelocity = startingVelocity * -.8f)
        horizontalTranslation.stretchTo(stretchAmount = 0f, startingVelocity * 200f)
    }

    fun popScale(startingVelocity: Float) {
        scalePivotX.snapTo(backgroundWidth.pos / 2)
        scale.stretchTo(stretchAmount = 0f, startingVelocity = startingVelocity)
    }

    fun popArrowAlpha(startingVelocity: Float, springForce: SpringForce? = null) {
        arrowAlpha.stretchTo(
            stretchAmount = 0f,
            startingVelocity = startingVelocity,
            springForce = springForce,
        )
    }

    fun resetStretch() {
        backgroundAlpha.snapTo(1f)
        verticalTranslation.snapTo(0f)
        scale.snapTo(1f)
        horizontalTranslation.snapToRestingPosition()
        arrowLength.snapToRestingPosition()
        arrowHeight.snapToRestingPosition()
        arrowAlpha.snapToRestingPosition()
        backgroundWidth.snapToRestingPosition()
        backgroundHeight.snapToRestingPosition()
        backgroundEdgeCornerRadius.snapToRestingPosition()
        backgroundFarCornerRadius.snapToRestingPosition()
    }

    internal fun setRestingDimens(
        restingParams: EdgePanelParams.BackIndicatorDimens,
        animate: Boolean = true,
    ) {
        horizontalTranslation.updateRestingPosition(restingParams.horizontalTranslation)
        scale.updateRestingPosition(restingParams.scale)
        backgroundAlpha.updateRestingPosition(restingParams.backgroundDimens.alpha)
        arrowAlpha.updateRestingPosition(restingParams.arrowDimens.alpha, animate)
        arrowLength.updateRestingPosition(restingParams.arrowDimens.length, animate)
        arrowHeight.updateRestingPosition(restingParams.arrowDimens.height, animate)
        scalePivotX.updateRestingPosition(restingParams.scalePivotX, animate)
        backgroundWidth.updateRestingPosition(restingParams.backgroundDimens.width, animate)
        backgroundHeight.updateRestingPosition(restingParams.backgroundDimens.height, animate)
        backgroundEdgeCornerRadius.updateRestingPosition(
            restingParams.backgroundDimens.edgeCornerRadius,
            animate,
        )
        backgroundFarCornerRadius.updateRestingPosition(
            restingParams.backgroundDimens.farCornerRadius,
            animate,
        )
    }

    fun animateVertically(yPos: Float) = verticalTranslation.stretchTo(yPos)

    fun setSpring(
        horizontalTranslation: SpringForce? = null,
        verticalTranslation: SpringForce? = null,
        scale: SpringForce? = null,
        arrowLength: SpringForce? = null,
        arrowHeight: SpringForce? = null,
        arrowAlpha: SpringForce? = null,
        backgroundAlpha: SpringForce? = null,
        backgroundFarCornerRadius: SpringForce? = null,
        backgroundEdgeCornerRadius: SpringForce? = null,
        backgroundWidth: SpringForce? = null,
        backgroundHeight: SpringForce? = null,
    ) {
        arrowLength?.let { this.arrowLength.spring = it }
        arrowHeight?.let { this.arrowHeight.spring = it }
        arrowAlpha?.let { this.arrowAlpha.spring = it }
        backgroundAlpha?.let { this.backgroundAlpha.spring = it }
        backgroundFarCornerRadius?.let { this.backgroundFarCornerRadius.spring = it }
        backgroundEdgeCornerRadius?.let { this.backgroundEdgeCornerRadius.spring = it }
        scale?.let { this.scale.spring = it }
        backgroundWidth?.let { this.backgroundWidth.spring = it }
        backgroundHeight?.let { this.backgroundHeight.spring = it }
        horizontalTranslation?.let { this.horizontalTranslation.spring = it }
        verticalTranslation?.let { this.verticalTranslation.spring = it }
    }

    override fun hasOverlappingRendering() = false

    override fun onDraw(canvas: Canvas) {
        val edgeCorner = backgroundEdgeCornerRadius.pos
        val farCorner = backgroundFarCornerRadius.pos
        val halfHeight = backgroundHeight.pos / 2
        val canvasWidth = width
        val backgroundWidth = backgroundWidth.pos
        val scalePivotX = scalePivotX.pos

        canvas.save()
        if (!isLeftPanel) canvas.scale(-1f, 1f, canvasWidth / 2.0f, 0f)
        canvas.translate(horizontalTranslation.pos, height * 0.5f + verticalTranslation.pos)
        canvas.scale(scale.pos, scale.pos, scalePivotX, 0f)

        val arrowBackground = arrowBackgroundRect
            .apply {
                left = 0f
                top = -halfHeight
                right = backgroundWidth
                bottom = halfHeight
            }
            .toPathWithRoundCorners(
                topLeft = edgeCorner,
                bottomLeft = edgeCorner,
                topRight = farCorner,
                bottomRight = farCorner,
            )
        canvas.drawPath(
            arrowBackground,
            arrowBackgroundPaint.apply { alpha = (255 * backgroundAlpha.pos).toInt() },
        )

        val dx = arrowLength.pos
        val dy = arrowHeight.pos
        val arrowOffset = (backgroundWidth - dx) / 2
        canvas.translate(arrowOffset, 0f)

        val arrowPointsAwayFromEdge = !arrowsPointLeft.xor(isLeftPanel)
        if (arrowPointsAwayFromEdge) {
            canvas.scale(-1f, 1f, 0f, 0f)
            canvas.translate(-dx, 0f)
        }

        val path = calculateArrowPath(dx = dx, dy = dy)
        val paint = arrowPaint.apply {
            alpha = (255 * min(arrowAlpha.pos, backgroundAlpha.pos)).toInt()
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun RectF.toPathWithRoundCorners(
        topLeft: Float = 0f,
        topRight: Float = 0f,
        bottomRight: Float = 0f,
        bottomLeft: Float = 0f,
    ): Path = Path().apply {
        val corners = floatArrayOf(
            topLeft, topLeft,
            topRight, topRight,
            bottomRight, bottomRight,
            bottomLeft, bottomLeft,
        )
        addRoundRect(this@toPathWithRoundCorners, corners, Path.Direction.CW)
    }
}
