package com.slideindex.app.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.FloatingPointerAreaPreviewOverlay
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import com.slideindex.app.overlay.preview.AccessibilityOverlayPreviewPort
import com.slideindex.app.overlay.preview.OverlayPreviewPort
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.navigation.NavPermissionStates
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 设置页里所有"实时预览浮层"的入口，全部同进程直连浮层宿主。
 *
 * 历史上设置界面与浮层宿主不在同一个进程，每个动作只能打包成 Intent 交给 [OverlayService]，
 * 再由它在 `onStartCommand` 里分发一次。回单进程之后这就是"自己给自己发消息"：功能正常，
 * 但读写要多跳三层，还多一处能静默丢失的中间人，因此改成直接调用静态入口。
 *
 * [permissionStates] 的 `accessibilityGranted` 是唯一的门禁：没有无障碍实例就没有浮层宿主，
 * 任何预览都落不了地。
 */
class OverlayServiceController(
    private val context: Context,
    private val permissionStates: NavPermissionStates,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    /**
     * 预览能力走接口而不是直接点 [SlideIndexAccessibilityService] 的静态入口：
     * 这样预览逻辑能在没有 AccessibilityService 实例的单测里用假实现验证。
     */
    private val previewPort: OverlayPreviewPort = AccessibilityOverlayPreviewPort,
) {
    private var lastPreviewDropNoticeMs = 0L

    // —— 布局预览（触钮 / 索引高度 / 手势角度） ——

    fun startLayoutPreview(
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        if (!permissionStates.accessibilityGranted.value) return
        previewPort.setPreviewMode(true, content, focus)
    }

    fun stopLayoutPreview() {
        if (!permissionStates.accessibilityGranted.value) return
        previewPort.setPreviewMode(false)
    }

    /** 触钮外观/位置预览：为 null 的项表示"这一项沿用当前值"。 */
    fun previewTriggerHandle(
        side: PanelSide,
        handleId: String,
        edgeWidthDp: Float? = null,
        topFraction: Float? = null,
        bottomFraction: Float? = null,
        shortSwipeDistanceDp: Float? = null,
        longSwipeDistanceDp: Float? = null,
        design: TriggerHandleDesign? = null,
    ) = withOverlayPreview("previewTriggerHandle") {
        previewPort.mergeTriggerHandleLayoutPreview(
            side = side,
            handleId = handleId,
            edgeWidthDp = edgeWidthDp,
            topFraction = topFraction,
            bottomFraction = bottomFraction,
            shortSwipeDistanceDp = shortSwipeDistanceDp,
            longSwipeDistanceDp = longSwipeDistanceDp,
            design = design,
        )
    }

    fun previewIndexHeightFraction(fraction: Float) =
        withOverlayPreview("previewIndexHeightFraction") {
            previewPort.previewIndexHeightFraction(fraction)
        }

    fun clearIndexHeightPreview() =
        withOverlayPreview("clearIndexHeightPreview") {
            previewPort.clearIndexHeightPreview()
        }

    /**
     * 松手提交索引高度预览：保留预览值直到设置落盘回流追上，避免"先跳回旧值再跳到新值"。
     * 真正离开页面时用 [clearOverlayLayoutPreview] 硬清。
     */
    fun commitIndexHeightPreview() =
        withOverlayPreview("commitIndexHeightPreview") {
            previewPort.commitIndexHeightPreview()
        }

    fun previewIndexVerticalPositionFraction(fraction: Float) =
        withOverlayPreview("previewIndexVerticalPositionFraction") {
            previewPort.previewIndexVerticalPositionFraction(fraction)
        }

    fun clearIndexVerticalPositionPreview() =
        withOverlayPreview("clearIndexVerticalPositionPreview") {
            previewPort.clearIndexVerticalPositionPreview()
        }

    /** 松手提交索引位置预览：语义同 [commitIndexHeightPreview]。 */
    fun commitIndexVerticalPositionPreview() =
        withOverlayPreview("commitIndexVerticalPositionPreview") {
            previewPort.commitIndexVerticalPositionPreview()
        }

    /** 索引页滑条松手（长度/位置共用）：只提交最近拖过的那一个。 */
    fun commitIndexSliderPreviews() =
        withOverlayPreview("commitIndexSliderPreviews") {
            previewPort.commitIndexSliderPreviews()
        }

    fun setCornerZonePreviewActive(active: Boolean) =
        withOverlayPreview("setCornerZonePreviewActive") {
            previewPort.setCornerZonePreviewActive(active)
        }

    /** 改角区尺寸的调用点都在"预览已激活"期间，所以这里顺带把预览置为激活。 */
    fun applyCornerZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) = withOverlayPreview("applyCornerZonePreviewDimensions") {
        previewPort.setCornerZonePreviewActive(true)
        previewPort.applyCornerZonePreviewDimensions(
            verticalEdgeWidthDp = verticalEdgeWidthDp,
            verticalEdgeHeightDp = verticalEdgeHeightDp,
            horizontalEdgeWidthDp = horizontalEdgeWidthDp,
            horizontalEdgeHeightDp = horizontalEdgeHeightDp,
        )
    }

    /** 手势角度预览：null 表示清掉预览。 */
    fun setGestureAnglesPreview(angles: GestureAngles?) =
        withOverlayPreview("setGestureAnglesPreview") {
            previewPort.setGestureAnglesPreview(angles)
        }

    // —— 悬浮球（触钮条 / 位置 / 外观） ——

    fun setFloatBallStripZonePreview(active: Boolean) =
        withOverlayPreview("setFloatBallStripZonePreview") {
            previewPort.setFloatBallStripZonePreview(active)
        }

    fun previewFloatBallPositionYFraction(fraction: Float) =
        withOverlayPreview("previewFloatBallPositionYFraction") {
            previewPort.previewFloatBallPositionYFraction(fraction)
        }

    fun endFloatBallPositionYPreview(restoreIfNeeded: Boolean) =
        withOverlayPreview("endFloatBallPositionYPreview") {
            previewPort.endFloatBallPositionYPreview(restoreIfNeeded)
        }

    fun clearFloatBallPositionYPreviewRestore() =
        withOverlayPreview("clearFloatBallPositionYPreviewRestore") {
            previewPort.clearFloatBallPositionYPreviewRestore()
        }

    fun previewFloatBallAppearance(
        sizeDp: Float? = null,
        opacity: Float? = null,
        visibleFraction: Float? = null,
        lineHeightFraction: Float? = null,
        lineWidthDp: Float? = null,
        lineOpacity: Float? = null,
    ) = withOverlayPreview("previewFloatBallAppearance") {
        previewPort.previewFloatBallAppearance(
            sizeDp = sizeDp,
            opacity = opacity,
            visibleFraction = visibleFraction,
            lineHeightFraction = lineHeightFraction,
            lineWidthDp = lineWidthDp,
            lineOpacity = lineOpacity,
        )
    }

    fun endFloatBallAppearancePreview(restoreIfNeeded: Boolean) =
        withOverlayPreview("endFloatBallAppearancePreview") {
            previewPort.endFloatBallAppearancePreview(restoreIfNeeded)
        }

    fun clearFloatBallAppearancePreviewRestore() =
        withOverlayPreview("clearFloatBallAppearancePreviewRestore") {
            previewPort.clearFloatBallAppearancePreviewRestore()
        }

    /**
     * 悬浮指针"行程范围预览"：窗口与跟手位置都由本进程的边缘触摸驱动。
     *
     * `sensitivity == null` 表示不改灵敏度，`Float.NaN` 表示松手（回到已保存设置）。
     */
    fun setFloatingPointerAreaPreview(active: Boolean, sensitivity: Float? = null) {
        if (!active) FloatingPointerAreaPreviewOverlay.hide()
        withOverlayPreview("setFloatingPointerAreaPreview") {
            if (!active) return@withOverlayPreview
            FloatingPointerAreaPreviewOverlay.show(settingsRepository)
            if (sensitivity != null) {
                FloatingPointerAreaPreviewOverlay.previewSensitivity(
                    sensitivity.takeUnless { it.isNaN() }
                )
            }
        }
    }

    /** 设置里的小组件编辑器点"预览"：把小组件面板浮出来，让用户看到真身。 */
    fun showWidgetPanelPreview() =
        withOverlayPreview("showWidgetPanelPreview") {
            WidgetPopupOverlayWindow.show(
                context = context,
                settings = settingsRepository.readSnapshot(),
            )
        }

    fun clearOverlayLayoutPreview() =
        withOverlayPreview("clearOverlayLayoutPreview") {
            previewPort.clearOverlayLayoutPreview()
        }

    /** 松手提交触钮预览：同 [commitIndexHeightPreview]。 */
    fun commitTriggerHandleLayoutPreview() =
        withOverlayPreview("commitTriggerHandleLayoutPreview") {
            previewPort.commitTriggerHandleLayoutPreview()
        }

    /** 离开设置页：停掉布局预览与行程范围预览。 */
    fun stopPreviewOnPause() {
        stopLayoutPreview()
        setFloatingPointerAreaPreview(false)
    }

    /**
     * 预览发不出去时必须可见。
     *
     * 这类失败以前是静默的（`instance?.` 命中 null 直接丢弃），用户只会觉得"功能坏了"；
     * 现在至少留下一条日志，并按节流给用户一次提示。
     */
    private fun withOverlayPreview(action: String, block: () -> Unit) {
        if (!permissionStates.accessibilityGranted.value) {
            notifyPreviewUnavailable(action)
            return
        }
        block()
    }

    private fun notifyPreviewUnavailable(action: String) {
        Log.w(TAG, "浮层预览不可用（无障碍未连接）: $action")
        val now = SystemClock.uptimeMillis()
        if (now - lastPreviewDropNoticeMs < PREVIEW_DROP_NOTICE_INTERVAL_MS) return
        lastPreviewDropNoticeMs = now
        runCatching {
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.preview_unavailable_hint),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun refreshPermissionState() {
        permissionStates.overlayGranted.value = PermissionHelper.canDrawOverlays(context)
        permissionStates.notificationGranted.value = PermissionHelper.hasNotificationPermission(context)
        permissionStates.usageAccessGranted.value = PermissionHelper.hasUsageAccess(context)
        permissionStates.shizukuGranted.value = TaskManagerUtil.hasPrivilegedAccess()
        permissionStates.accessibilityGranted.value =
            PermissionHelper.isAccessibilityServiceEnabled(context)
        permissionStates.batteryOptimizationExempt.value =
            PermissionHelper.isBatteryOptimizationExempt(context)
        permissionStates.writeSecureSettingsGranted.value =
            SecureSettingsHelper.hasWriteSecureSettings(context)
        val listenerEnabled = com.slideindex.app.util.MediaSessionHelper.isNotificationListenerEnabled(context)
        permissionStates.notificationListenerEnabled.value = listenerEnabled
        if (listenerEnabled) {
            com.slideindex.app.util.MediaSessionHelper.ensureNotificationListenerConnected(context)
        }
        if (TaskManagerUtil.hasPrivilegedAccess()) {
            TaskManagerUtil.warmUpPrivilegedBackend()
        }
    }

    fun refreshServiceState() {
        scope.launch {
            OverlayServiceLifecycle.syncFromSettings(
                context,
                settingsRepository,
                accessibilityRecoverRetries = true,
            )
            permissionStates.accessibilityGranted.value =
                PermissionHelper.isAccessibilityServiceEnabled(context)
        }
    }

    private companion object {
        const val TAG = "OverlayServiceController"

        /** 同一个提示 10s 内只弹一次，避免拖动滑条时刷屏。 */
        const val PREVIEW_DROP_NOTICE_INTERVAL_MS = 10_000L
    }
}
