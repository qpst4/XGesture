package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import androidx.core.graphics.withTranslation
import com.slideindex.app.overlay.panels.R
import kotlin.math.roundToInt

object AdjustLevelIndicatorRenderer {
    fun draw(
        canvas: Canvas,
        layout: AdjustLevelIndicatorLayout,
        mode: com.slideindex.app.overlay.panels.PanelAdjustMode,
        fraction: Float,
        enterProgress: Float,
        density: Float,
        side: PanelSide,
        recede: Boolean = false,
        volumePanel: VolumePanelVisual? = null,
        brightnessPanel: BrightnessPanelVisual? = null,
        context: Context? = null
    ) {
        if (enterProgress <= 0f) return
        val t = enterProgress.coerceIn(0f, 1f)
        val travel = if (recede) easeInCubic(t) else t
        val alphaScale = if (recede) t else t
        val slideRemaining = 1f - travel
        val slideDistance = enterSlideDistancePx(density)
        val slideX = when (side) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> -slideDistance * slideRemaining
            PanelSide.RIGHT -> slideDistance * slideRemaining
        }

        canvas.withTranslation(slideX, 0f) {
            val shadowAlphaScale = if (recede) alphaScale * alphaScale else alphaScale
            val corner = AdjustLevelIndicator.PILL_CORNER_DP * density
            if (mode == com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME) {
                layout.topPill?.let { topPill ->
                    drawTopDndPill(
                        canvas = this,
                        bounds = topPill,
                        corner = corner,
                        density = density,
                        alphaScale = alphaScale,
                        shadowAlphaScale = shadowAlphaScale,
                        dndEnabled = volumePanel?.dndEnabled == true,
                        context = context
                    )
                }
            }
            if (mode == com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS) {
                layout.topPill?.let { topPill ->
                    drawTopAutoBrightnessPill(
                        canvas = this,
                        bounds = topPill,
                        corner = corner,
                        density = density,
                        alphaScale = alphaScale,
                        shadowAlphaScale = shadowAlphaScale,
                        enabled = brightnessPanel?.autoBrightnessEnabled == true
                    )
                }
            }
            drawShadow(this, layout.bounds, corner, shadowAlphaScale)
            drawPillBackground(this, layout.bounds, corner, alphaScale)
            drawTrack(this, layout.track, mode, fraction.coerceIn(0f, 1f), density, alphaScale)
            drawIcon(
                canvas = this,
                bounds = layout.bounds,
                trackTop = layout.track.top,
                mode = mode,
                fraction = fraction.coerceIn(0f, 1f),
                density = density,
                alphaScale = alphaScale
            )
            drawPercentLabel(this, layout.bounds, fraction.coerceIn(0f, 1f), density, alphaScale)
            if (mode == com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME) {
                volumePanel?.let { panel ->
                    if (panel.expanded) {
                        layout.ringPill?.let { pill ->
                            layout.ringTrack?.let { track ->
                                drawSecondaryVolumePill(
                                    canvas = this,
                                    pill = pill,
                                    track = track,
                                    fraction = panel.ringFraction,
                                    label = context?.getString(R.string.adjust_volume_ring_label) ?: "Ring",
                                    accentStart = Color.argb((230 * alphaScale).roundToInt(), 126, 87, 194),
                                    accentEnd = Color.argb((255 * alphaScale).roundToInt(), 186, 150, 255),
                                    corner = corner,
                                    density = density,
                                    alphaScale = alphaScale,
                                    shadowAlphaScale = shadowAlphaScale
                                )
                            }
                        }
                        layout.notificationPill?.let { pill ->
                            layout.notificationTrack?.let { track ->
                                drawSecondaryVolumePill(
                                    canvas = this,
                                    pill = pill,
                                    track = track,
                                    fraction = panel.notificationFraction,
                                    label = context?.getString(R.string.adjust_volume_notification_label) ?: "Notif",
                                    accentStart = Color.argb((230 * alphaScale).roundToInt(), 38, 166, 154),
                                    accentEnd = Color.argb((255 * alphaScale).roundToInt(), 128, 223, 208),
                                    corner = corner,
                                    density = density,
                                    alphaScale = alphaScale,
                                    shadowAlphaScale = shadowAlphaScale
                                )
                            }
                        }
                    }
                }
                layout.bottomPill?.let { bottomPill ->
                    drawBottomPill(
                        canvas = this,
                        bounds = bottomPill,
                        corner = corner,
                        density = density,
                        alphaScale = alphaScale,
                        shadowAlphaScale = shadowAlphaScale,
                        ringerMode = volumePanel?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL,
                        expanded = volumePanel?.expanded == true,
                        context = context
                    )
                }
            }
            if (mode == com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS) {
                layout.bottomPill?.let { bottomPill ->
                    drawCompactChromePill(
                        canvas = this,
                        bounds = bottomPill,
                        corner = corner,
                        density = density,
                        alphaScale = alphaScale,
                        shadowAlphaScale = shadowAlphaScale,
                        context = context
                    ) {
                        BrightnessIconRenderer.drawDarkMode(
                            context = it,
                            canvas = this@withTranslation,
                            cx = bottomPill.centerX(),
                            cy = bottomPill.centerY(),
                            sizePx = minOf(bottomPill.width() * 0.38f, bottomPill.height() * 0.46f),
                            darkModeEnabled = brightnessPanel?.darkModeEnabled == true,
                            alphaScale = alphaScale
                        )
                    }
                }
            }
        }
    }

    private fun drawShadow(canvas: Canvas, bounds: RectF, corner: Float, alphaScale: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((55 * alphaScale).roundToInt(), 0, 0, 0)
        }
        val spread = 6f
        canvas.drawRoundRect(
            bounds.left - spread,
            bounds.top - spread + 2f,
            bounds.right + spread,
            bounds.bottom + spread + 4f,
            corner + 4f,
            corner + 4f,
            shadowPaint
        )
    }

    private fun drawPillBackground(canvas: Canvas, bounds: RectF, corner: Float, alphaScale: Float) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((210 * alphaScale).roundToInt(), 22, 24, 30)
        }
        canvas.drawRoundRect(bounds, corner, corner, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.argb((70 * alphaScale).roundToInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(bounds, corner, corner, strokePaint)
    }

    private fun drawTrack(
        canvas: Canvas,
        track: RectF,
        mode: com.slideindex.app.overlay.panels.PanelAdjustMode,
        fraction: Float,
        density: Float,
        alphaScale: Float
    ) {
        val corner = 8f * density
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((45 * alphaScale).roundToInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(track, corner, corner, trackPaint)

        if (fraction <= 0f) return

        val fillHeight = track.height() * fraction
        val fillTop = track.bottom - fillHeight
        val fillRect = RectF(track.left, fillTop, track.right, track.bottom)

        val (startColor, endColor) = when (mode) {
            com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME -> {
                Color.argb((230 * alphaScale).roundToInt(), 66, 133, 244) to
                    Color.argb((255 * alphaScale).roundToInt(), 120, 190, 255)
            }
            com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS -> {
                Color.argb((230 * alphaScale).roundToInt(), 255, 183, 77) to
                    Color.argb((255 * alphaScale).roundToInt(), 255, 236, 179)
            }
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                fillRect.left,
                fillRect.bottom,
                fillRect.left,
                fillRect.top,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(fillRect, corner, corner, fillPaint)

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((35 * alphaScale).roundToInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(
            fillRect.left + 2f * density,
            fillTop + 2f * density,
            fillRect.right - 2f * density,
            fillTop + 8f * density,
            corner,
            corner,
            highlightPaint
        )
    }

    private data class IconAccent(
        val primary: Int,
        val secondary: Int,
        val glow: Int
    )

    private fun iconAccent(mode: com.slideindex.app.overlay.panels.PanelAdjustMode, level: Float, alphaScale: Float): IconAccent {
        val a = alphaScale.coerceIn(0f, 1f)
        return when (mode) {
            com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME -> IconAccent(
                primary = scaledColor(160, 210, 255, a),
                secondary = scaledColor(90, 165, 255, a),
                glow = scaledColor(66, 133, 244, a * (0.35f + 0.65f * level))
            )
            com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS -> IconAccent(
                primary = scaledColor(255, 236, 170, a),
                secondary = scaledColor(255, 196, 90, a),
                glow = scaledColor(255, 183, 77, a * (0.30f + 0.70f * level))
            )
        }
    }

    private fun drawIcon(
        canvas: Canvas,
        bounds: RectF,
        trackTop: Float,
        mode: com.slideindex.app.overlay.panels.PanelAdjustMode,
        fraction: Float,
        density: Float,
        alphaScale: Float
    ) {
        val level = fraction.coerceIn(0f, 1f)
        val iconSize = 19f * density
        val cx = bounds.centerX()
        val cy = bounds.top + (trackTop - bounds.top) / 2f
        val accent = iconAccent(mode, level, alphaScale)

        drawIconHeaderGlow(canvas, cx, cy, bounds.width(), trackTop - bounds.top, accent, level, density, alphaScale)
        when (mode) {
            com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME -> drawVolumeIcon(canvas, cx, cy, iconSize, level, accent, density, alphaScale)
            com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS -> drawBrightnessIcon(canvas, cx, cy, iconSize, level, accent, density)
        }
    }

    private fun drawIconHeaderGlow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        width: Float,
        headerHeight: Float,
        accent: IconAccent,
        level: Float,
        density: Float,
        alphaScale: Float
    ) {
        if (level <= 0.02f) return
        val radius = (width.coerceAtMost(headerHeight) * 0.62f) * (0.85f + 0.15f * level)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy,
                radius,
                Color.argb((110 * alphaScale * (0.35f + 0.65f * level)).roundToInt(), Color.red(accent.glow), Color.green(accent.glow), Color.blue(accent.glow)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius, glowPaint)
    }

    private fun drawVolumeIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        level: Float,
        accent: IconAccent,
        density: Float,
        alphaScale: Float
    ) {
        val halfH = size * 0.36f
        val bodyInset = size * 0.04f
        val speakerWidth = size * 0.52f
        val waveCount = volumeWaveCount(level)
        val waveOffsets = floatArrayOf(0.18f, 0.36f, 0.54f)
        val waveRadii = floatArrayOf(0.17f, 0.27f, 0.36f)

        val contentWidth = when {
            level <= 0.02f -> size * 0.88f
            waveCount == 0 -> bodyInset + speakerWidth + bodyInset
            else -> {
                val last = waveCount - 1
                bodyInset + speakerWidth + size * (waveOffsets[last] + waveRadii[last])
            }
        }
        val groupLeft = cx - contentWidth / 2f
        val bodyLeft = groupLeft + bodyInset
        val speakerRight = bodyLeft + speakerWidth

        val iconAlpha = (210f * alphaScale).roundToInt()
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.65f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(iconAlpha, 245, 247, 250)
        }

        val speakerPath = Path().apply {
            moveTo(bodyLeft + halfH * 0.55f, cy - halfH * 0.62f)
            lineTo(bodyLeft, cy - halfH * 0.62f)
            lineTo(bodyLeft, cy + halfH * 0.62f)
            lineTo(bodyLeft + halfH * 0.55f, cy + halfH * 0.62f)
            lineTo(speakerRight, cy + halfH * 1.05f)
            lineTo(speakerRight, cy - halfH * 1.05f)
            close()
        }
        canvas.drawPath(speakerPath, strokePaint)

        if (level <= 0.02f) {
            val mutePaint = Paint(strokePaint).apply {
                color = Color.argb((150f * alphaScale).roundToInt(), 190, 194, 202)
                strokeWidth = 1.5f * density
            }
            val slashPad = size * 0.14f
            canvas.drawLine(
                groupLeft + slashPad,
                cy - halfH * 0.75f,
                groupLeft + contentWidth - slashPad,
                cy + halfH * 0.75f,
                mutePaint
            )
            return
        }

        if (waveCount == 0) return

        val wavePaint = Paint(strokePaint).apply {
            strokeWidth = 1.55f * density
            color = Color.argb(
                iconAlpha,
                Color.red(accent.primary),
                Color.green(accent.primary),
                Color.blue(accent.primary)
            )
        }
        for (i in 0 until waveCount) {
            val r = size * waveRadii[i]
            val waveCenter = speakerRight + size * waveOffsets[i]
            canvas.drawArc(
                waveCenter - r,
                cy - r * 1.15f,
                waveCenter + r,
                cy + r * 1.15f,
                -68f,
                136f,
                false,
                wavePaint
            )
        }
    }

    private fun volumeWaveCount(level: Float): Int = when {
        level <= 0.02f -> 0
        level < 0.08f -> 0
        level < 0.45f -> 1
        level < 0.80f -> 2
        else -> 3
    }

    private fun drawBrightnessIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        level: Float,
        accent: IconAccent,
        density: Float
    ) {
        val coreRadius = size * (0.22f + 0.08f * level)
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - coreRadius * 0.2f,
                cy - coreRadius * 0.2f,
                coreRadius * 1.2f,
                blendColors(scaledColor(255, 255, 245, 1f), accent.primary, 0.25f + 0.55f * level),
                accent.secondary,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        val coreRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.55f * density
            color = blendColors(accent.primary, scaledColor(255, 255, 255, 1f), 0.35f + 0.45f * level)
            alpha = (140 + 80 * level).roundToInt()
        }
        canvas.drawCircle(cx, cy, coreRadius, coreRing)

        if (level <= 0.03f) return

        val visibleRays = when {
            level < 0.20f -> 4
            level < 0.55f -> 6
            else -> 8
        }
        val rayInner = coreRadius + 2.5f * density
        val rayOuter = size * (0.32f + 0.22f * level)
        for (i in 0 until visibleRays) {
            val angle = Math.toRadians((i * (360.0 / visibleRays) - 90.0))
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = (1.45f + 0.55f * level) * density
                color = blendColors(accent.secondary, accent.primary, level)
                alpha = (110 + 120 * level).roundToInt()
            }
            canvas.drawLine(
                cx + cos * rayInner,
                cy + sin * rayInner,
                cx + cos * rayOuter,
                cy + sin * rayOuter,
                rayPaint
            )
        }
    }

    private fun scaledColor(r: Int, g: Int, b: Int, alphaScale: Float): Int =
        Color.argb((255f * alphaScale.coerceIn(0f, 1f)).roundToInt(), r, g, b)

    private fun blendColors(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt()
        )
    }

    private fun drawSecondaryVolumePill(
        canvas: Canvas,
        pill: RectF,
        track: RectF,
        fraction: Float,
        label: String,
        accentStart: Int,
        accentEnd: Int,
        corner: Float,
        density: Float,
        alphaScale: Float,
        shadowAlphaScale: Float
    ) {
        drawShadow(canvas, pill, corner, shadowAlphaScale)
        drawPillBackground(canvas, pill, corner, alphaScale)
        drawColoredTrack(canvas, track, fraction.coerceIn(0f, 1f), accentStart, accentEnd, density, alphaScale)
        drawMiniLabel(canvas, pill.centerX(), pill.top + 14f * density, label, density, alphaScale)
        drawPercentLabel(canvas, pill, fraction.coerceIn(0f, 1f), density, alphaScale, textSizeDp = 10f)
    }

    private fun drawColoredTrack(
        canvas: Canvas,
        track: RectF,
        fraction: Float,
        startColor: Int,
        endColor: Int,
        density: Float,
        alphaScale: Float
    ) {
        val corner = 7f * density
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((45 * alphaScale).roundToInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(track, corner, corner, trackPaint)
        if (fraction <= 0f) return
        val fillHeight = track.height() * fraction
        val fillTop = track.bottom - fillHeight
        val fillRect = RectF(track.left, fillTop, track.right, track.bottom)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                fillRect.left,
                fillRect.bottom,
                fillRect.left,
                fillRect.top,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(fillRect, corner, corner, fillPaint)
    }

    private fun drawMiniLabel(
        canvas: Canvas,
        cx: Float,
        baselineY: Float,
        text: String,
        density: Float,
        alphaScale: Float
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f * density
            color = Color.argb((180 * alphaScale).roundToInt(), 255, 255, 255)
        }
        canvas.drawText(text, cx, baselineY, textPaint)
    }

    private fun drawCompactChromePill(
        canvas: Canvas,
        bounds: RectF,
        corner: Float,
        density: Float,
        alphaScale: Float,
        shadowAlphaScale: Float,
        context: Context?,
        drawIcon: (Context) -> Unit
    ) {
        drawShadow(canvas, bounds, corner, shadowAlphaScale)
        drawPillBackground(canvas, bounds, corner, alphaScale)
        context?.let(drawIcon)
    }

    private fun drawTopAutoBrightnessPill(
        canvas: Canvas,
        bounds: RectF,
        corner: Float,
        density: Float,
        alphaScale: Float,
        shadowAlphaScale: Float,
        enabled: Boolean
    ) {
        drawShadow(canvas, bounds, corner, shadowAlphaScale)
        drawPillBackground(canvas, bounds, corner, alphaScale)
        if (enabled) {
            val accentStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.4f * density
                color = Color.argb((95 * alphaScale).roundToInt(), 255, 184, 0)
            }
            canvas.drawRoundRect(bounds, corner, corner, accentStroke)
        }
        val iconSize = minOf(bounds.width(), bounds.height()) * 0.56f
        BrightnessIconRenderer.drawAutoBrightnessIcon(
            canvas = canvas,
            cx = bounds.centerX(),
            cy = bounds.centerY(),
            sizePx = iconSize,
            enabled = enabled,
            alphaScale = alphaScale
        )
    }

    private fun drawTopDndPill(
        canvas: Canvas,
        bounds: RectF,
        corner: Float,
        density: Float,
        alphaScale: Float,
        shadowAlphaScale: Float,
        dndEnabled: Boolean,
        context: Context?
    ) {
        val iconSize = minOf(bounds.width() * 0.38f, bounds.height() * 0.46f)
        drawCompactChromePill(
            canvas = canvas,
            bounds = bounds,
            corner = corner,
            density = density,
            alphaScale = alphaScale,
            shadowAlphaScale = shadowAlphaScale,
            context = context
        ) {
            DndIconRenderer.draw(
                context = it,
                canvas = canvas,
                cx = bounds.centerX(),
                cy = bounds.centerY(),
                sizePx = iconSize,
                dndEnabled = dndEnabled,
                alphaScale = alphaScale
            )
        }
    }

    private fun drawBottomPill(
        canvas: Canvas,
        bounds: RectF,
        corner: Float,
        density: Float,
        alphaScale: Float,
        shadowAlphaScale: Float,
        ringerMode: Int,
        expanded: Boolean,
        context: Context?
    ) {
        drawShadow(canvas, bounds, corner, shadowAlphaScale)
        drawPillBackground(canvas, bounds, corner, alphaScale)

        val dividerY = bounds.centerY()
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((32 * alphaScale).roundToInt(), 255, 255, 255)
            strokeWidth = 0.8f * density
        }
        canvas.drawLine(
            bounds.left + 10f * density,
            dividerY,
            bounds.right - 10f * density,
            dividerY,
            dividerPaint
        )

        val iconSize = minOf(bounds.width() * 0.38f, bounds.height() * 0.30f)
        val cx = bounds.centerX()
        val halfHeight = bounds.height() / 2f
        val topCy = bounds.top + halfHeight / 2f
        val bottomCy = bounds.top + halfHeight + halfHeight / 2f
        context?.let {
            RingerModeIconRenderer.draw(it, canvas, cx, topCy, iconSize, ringerMode, alphaScale)
        }
        drawExpandPanelIcon(canvas, cx, bottomCy, iconSize, expanded, density, alphaScale)
    }

    private fun drawExpandPanelIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        expanded: Boolean,
        density: Float,
        alphaScale: Float
    ) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.55f * density
            strokeCap = Paint.Cap.ROUND
            color = Color.argb((210f * alphaScale).roundToInt(), 245, 247, 250)
        }
        if (expanded) {
            val arm = size * 0.22f
            canvas.drawLine(cx - arm, cy + arm * 0.35f, cx, cy - arm * 0.55f, strokePaint)
            canvas.drawLine(cx + arm, cy + arm * 0.35f, cx, cy - arm * 0.55f, strokePaint)
            return
        }
        val lineHalf = size * 0.30f
        val gap = size * 0.18f
        for (i in -1..1) {
            val y = cy + i * gap
            canvas.drawLine(cx - lineHalf, y, cx + lineHalf, y, strokePaint)
            val knobRadius = 1.35f * density
            val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((210f * alphaScale).roundToInt(), 245, 247, 250)
            }
            val knobX = when (i) {
                -1 -> cx + lineHalf * 0.35f
                0 -> cx - lineHalf * 0.25f
                else -> cx + lineHalf * 0.15f
            }
            canvas.drawCircle(knobX, y, knobRadius, knobPaint)
        }
    }

    private fun drawPercentLabel(
        canvas: Canvas,
        bounds: RectF,
        fraction: Float,
        density: Float,
        alphaScale: Float,
        textSizeDp: Float = 11f
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = textSizeDp * density
            color = Color.argb((200 * alphaScale).roundToInt(), 255, 255, 255)
        }
        val label = "${(fraction * 100f).roundToInt()}%"
        val baseline = bounds.bottom - 8f * density
        canvas.drawText(label, bounds.centerX(), baseline, textPaint)
    }

    private fun enterSlideDistancePx(density: Float): Float {
        val edgeInset = 18f * density
        val edgeMargin = 8f * density
        return AdjustLevelIndicator.PILL_WIDTH_DP * density + edgeInset + edgeMargin
    }

    private fun easeInCubic(t: Float): Float = t * t * t
}
