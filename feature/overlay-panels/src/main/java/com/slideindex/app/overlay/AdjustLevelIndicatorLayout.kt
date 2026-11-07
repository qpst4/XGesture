package com.slideindex.app.overlay

import android.graphics.RectF

object AdjustLevelIndicatorLayoutEngine {
    fun layout(
        viewWidth: Int,
        viewHeight: Int,
        side: PanelSide,
        anchorY: Float,
        density: Float,
        viewScreenX: Int = 0,
        screenWidthPx: Int = viewWidth,
        chrome: AdjustPanelChrome = AdjustPanelChrome.NONE,
        volumeExpanded: Boolean = false
    ): AdjustLevelIndicatorLayout {
        val pillWidth = AdjustLevelIndicator.PILL_WIDTH_DP * density
        val pillHeight = AdjustLevelIndicator.PILL_HEIGHT_DP * density
        val expandedPillHeight = AdjustLevelIndicator.EXPANDED_PILL_HEIGHT_DP * density
        val expandedGap = AdjustLevelIndicator.EXPANDED_PILL_GAP_DP * density
        val bottomPillHeightPx = when (chrome) {
            AdjustPanelChrome.VOLUME -> AdjustLevelIndicator.BOTTOM_PILL_HEIGHT_DP * density
            AdjustPanelChrome.BRIGHTNESS -> AdjustLevelIndicator.TOP_PILL_HEIGHT_DP * density
            AdjustPanelChrome.NONE -> 0f
        }
        val bottomPillGap = AdjustLevelIndicator.BOTTOM_PILL_GAP_DP * density
        val topPillHeight = AdjustLevelIndicator.TOP_PILL_HEIGHT_DP * density
        val topPillGap = AdjustLevelIndicator.TOP_PILL_GAP_DP * density
        var blockBelowMedia = 0f
        if (volumeExpanded) {
            blockBelowMedia += expandedGap + expandedPillHeight + expandedGap + expandedPillHeight
        }
        val topBlock = if (chrome != AdjustPanelChrome.NONE) topPillHeight + topPillGap else 0f
        val bottomBlock = if (chrome != AdjustPanelChrome.NONE) bottomPillGap + bottomPillHeightPx else 0f
        val totalHeight = topBlock + pillHeight + blockBelowMedia + bottomBlock
        val edgeInset = 18f * density
        val marginY = 24f * density
        val minY = marginY + totalHeight / 2f
        val maxY = viewHeight - marginY - totalHeight / 2f
        val centerY = if (minY <= maxY) {
            anchorY.coerceIn(minY, maxY)
        } else {
            viewHeight / 2f
        }
        val left = when (side) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> edgeInset - viewScreenX
            PanelSide.RIGHT -> screenWidthPx - edgeInset - pillWidth - viewScreenX
        }
        val stackTop = centerY - totalHeight / 2f
        val topPill = if (chrome != AdjustPanelChrome.NONE) {
            RectF(left, stackTop, left + pillWidth, stackTop + topPillHeight)
        } else {
            null
        }
        val mediaTop = stackTop + topBlock
        val bounds = RectF(left, mediaTop, left + pillWidth, mediaTop + pillHeight)
        val inset = 10f * density
        val iconArea = 36f * density
        val labelArea = 22f * density
        val track = RectF(
            bounds.left + inset,
            bounds.top + iconArea,
            bounds.right - inset,
            bounds.bottom - labelArea
        )

        var cursor = bounds.bottom
        val ringPill: RectF?
        val ringTrack: RectF?
        val notificationPill: RectF?
        val notificationTrack: RectF?
        if (volumeExpanded) {
            cursor += expandedGap
            ringPill = RectF(left, cursor, left + pillWidth, cursor + expandedPillHeight)
            cursor += expandedPillHeight
            ringTrack = secondaryTrack(ringPill, density)
            cursor += expandedGap
            notificationPill = RectF(left, cursor, left + pillWidth, cursor + expandedPillHeight)
            cursor += expandedPillHeight
            notificationTrack = secondaryTrack(notificationPill, density)
        } else {
            ringPill = null
            ringTrack = null
            notificationPill = null
            notificationTrack = null
        }

        val bottomPill = if (chrome != AdjustPanelChrome.NONE) {
            val pillTop = cursor + bottomPillGap
            RectF(left, pillTop, left + pillWidth, pillTop + bottomPillHeightPx)
        } else {
            null
        }
        val ringerButton = if (chrome == AdjustPanelChrome.VOLUME) {
            bottomPill?.let { RectF(it.left, it.top, it.right, it.centerY()) }
        } else {
            null
        }
        val expandButton = if (chrome == AdjustPanelChrome.VOLUME) {
            bottomPill?.let { RectF(it.left, it.centerY(), it.right, it.bottom) }
        } else {
            null
        }
        return AdjustLevelIndicatorLayout(
            bounds = bounds,
            track = track,
            ringPill = ringPill,
            ringTrack = ringTrack,
            notificationPill = notificationPill,
            notificationTrack = notificationTrack,
            topPill = topPill,
            bottomPill = bottomPill,
            ringerButton = ringerButton,
            expandButton = expandButton
        )
    }

    fun hitBounds(layout: AdjustLevelIndicatorLayout, side: PanelSide, density: Float): RectF {
        val verticalPad = 14f * density
        val innerPad = 10f * density
        return RectF(layout.bounds).apply {
            top -= verticalPad
            bottom += verticalPad
            when (side) {
                PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> {
                    left = layout.bounds.left
                    right = layout.bounds.right + innerPad
                }
                PanelSide.RIGHT -> {
                    left = layout.bounds.left - innerPad
                    right = layout.bounds.right
                }
            }
        }
    }

    fun containsTouch(
        layout: AdjustLevelIndicatorLayout,
        side: PanelSide,
        localX: Float,
        localY: Float,
        density: Float
    ): Boolean = hitVolumeTarget(layout, side, localX, localY, density) == VolumeHitTarget.MEDIA

    fun hitVolumeTarget(
        layout: AdjustLevelIndicatorLayout,
        side: PanelSide,
        localX: Float,
        localY: Float,
        density: Float
    ): VolumeHitTarget {
        val touchPad = 8f * density
        layout.topPill?.let { pill ->
            if (pill.contains(localX, localY)) {
                return VolumeHitTarget.DND
            }
        }
        layout.bottomPill?.let { pill ->
            layout.ringerButton?.let { button ->
                if (containsBottomButton(button, pill, localX, localY)) {
                    return VolumeHitTarget.RINGER
                }
            }
            layout.expandButton?.let { button ->
                if (containsBottomButton(button, pill, localX, localY)) {
                    return VolumeHitTarget.EXPAND
                }
            }
        }
        layout.ringPill?.let { pill ->
            layout.ringTrack?.let { track ->
                if (containsTrackTouch(track, pill, side, localX, localY, touchPad)) {
                    return VolumeHitTarget.RING
                }
            }
        }
        layout.notificationPill?.let { pill ->
            layout.notificationTrack?.let { track ->
                if (containsTrackTouch(track, pill, side, localX, localY, touchPad)) {
                    return VolumeHitTarget.NOTIFICATION
                }
            }
        }
        if (hitBounds(layout, side, density).contains(localX, localY)) {
            return VolumeHitTarget.MEDIA
        }
        return VolumeHitTarget.NONE
    }

    fun hitBrightnessTarget(
        layout: AdjustLevelIndicatorLayout,
        side: PanelSide,
        localX: Float,
        localY: Float,
        density: Float
    ): BrightnessHitTarget {
        layout.topPill?.let { pill ->
            if (pill.contains(localX, localY)) {
                return BrightnessHitTarget.AUTO_BRIGHTNESS
            }
        }
        layout.bottomPill?.let { pill ->
            if (pill.contains(localX, localY)) {
                return BrightnessHitTarget.DARK_MODE
            }
        }
        if (hitBounds(layout, side, density).contains(localX, localY)) {
            return BrightnessHitTarget.BRIGHTNESS
        }
        return BrightnessHitTarget.NONE
    }

    private fun secondaryTrack(pill: RectF, density: Float): RectF {
        val inset = 9f * density
        val iconArea = 28f * density
        val labelArea = 18f * density
        return RectF(
            pill.left + inset,
            pill.top + iconArea,
            pill.right - inset,
            pill.bottom - labelArea
        )
    }

    private fun containsBottomButton(button: RectF, pill: RectF, x: Float, y: Float): Boolean =
        x >= pill.left && x <= pill.right && y >= button.top && y <= button.bottom

    private fun containsTrackTouch(
        track: RectF,
        pill: RectF,
        side: PanelSide,
        x: Float,
        y: Float,
        pad: Float
    ): Boolean {
        if (x < pill.left || x > pill.right) return false
        var left = track.left
        var right = track.right
        when (side) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> right = minOf(pill.right, right + pad)
            PanelSide.RIGHT -> left = maxOf(pill.left, left - pad)
        }
        val top = maxOf(pill.top, track.top - pad)
        val bottom = minOf(pill.bottom, track.bottom + pad)
        return x >= left && x <= right && y >= top && y <= bottom
    }
}
