package com.slideindex.app.overlay

import com.slideindex.app.overlay.layout.FvIconShape
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureSettings
import com.slideindex.app.settings.OverlayBlurSettings
import com.slideindex.app.settings.OverlaySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy

/** 用同一帧完整快照更新宿主；不依赖另一个协程何时刷新仓库缓存。 */
internal fun Flow<AppSettings>.distinctOverlaySettings(): Flow<AppSettings> =
    distinctUntilChangedBy {
        OverlaySettingsFlowKey(
            gestureSettings = GestureSettings.from(it),
            overlaySettings = OverlaySettings.from(it),
            globalIconShape = it.globalIconShape,
            overlayBlur = it.overlayBlur
        )
    }

private data class OverlaySettingsFlowKey(
    val gestureSettings: GestureSettings,
    val overlaySettings: OverlaySettings,
    val globalIconShape: FvIconShape,
    val overlayBlur: OverlayBlurSettings
)
