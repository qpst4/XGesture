package com.slideindex.app.ui.navigation

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.KeepAliveHelper
import com.slideindex.app.util.MediaSessionHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Stable
class MainNavContext(
    val activity: MainActivity,
    val deps: AppDependencies,
    val backStack: NavBackStack,
    val permissionStates: NavPermissionStates,
    val floatingPointerAreaPreviewEnabledState: MutableState<Boolean>,
    val rootBottomContentPadding: Dp,
    val bottomNavReselectCount: Int = 0,
    val onBottomNavBlurPreviewChange: (Float) -> Unit = {},
    val onBottomNavBlurPreviewStop: () -> Unit = {},
) {
    @Composable
    fun collectAppSettings(): AppSettings {
        val settings by deps.settingsRepository.settings.collectAsStateWithLifecycle(
            initialValue = deps.settingsRepository.readSnapshot(),
        )
        return settings
    }

    @Composable
    fun collectPermissions(): NavPermissionSnapshot = permissionStates.collect()

    @Composable
    fun collectAreaPreviewEnabled(): Boolean {
        val enabled by floatingPointerAreaPreviewEnabledState
        return enabled
    }

    fun setFloatingPointerAreaPreviewEnabled(enabled: Boolean) {
        floatingPointerAreaPreviewEnabledState.value = enabled
    }

    fun gestureActive(settings: AppSettings, permissions: NavPermissionSnapshot): Boolean =
        gestureActive(settings.serviceEnabled, permissions)

    fun gestureActive(serviceEnabled: Boolean, permissions: NavPermissionSnapshot): Boolean =
        serviceEnabled && permissions.accessibilityGranted && permissions.notificationGranted

    private var deferredNavigateJob: Job? = null

    /** 推迟一帧再入栈，避免 Hub 行在 Miuix 按压高亮绘制前就被 Nav 转场卸掉。 */
    fun navigate(key: AppNavKey) {
        deferredNavigateJob?.cancel()
        deferredNavigateJob = activity.lifecycleScope.launch {
            awaitFrame()
            backStack.navigate(key)
        }
    }

    fun navigateBackTo(key: AppNavKey) = backStack.navigateBackTo(key)

    /** 只回退一层（回来源页）；不要用 [navigateBackTo] 代替，它会跳过中间页。 */
    fun navigateBack() = backStack.pop()

    fun replaceRoot(key: AppNavKey) = backStack.replaceRoot(key)

    fun launch(block: suspend () -> Unit) {
        activity.lifecycleScope.launch { block() }
    }

    /** 布局预览总开关（触钮 / 索引高度 / 手势角度）。 */
    fun startLayoutPreview(
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        activity.overlayServiceController.startLayoutPreview(content, focus)
    }

    fun stopLayoutPreview() {
        activity.overlayServiceController.stopLayoutPreview()
    }

    fun startFocusedTriggerPreview(
        side: PanelSide,
        handleId: String,
        showPairedGroup: Boolean = false,
    ) {
        retainFocusedTriggerPreview(
            triggerPreviewFocus(
                side = side,
                handleId = handleId,
                showSwipeDistances = false,
                showPairedGroup = showPairedGroup,
            ),
        )
    }

    fun startSwipeDistancePreview(
        side: PanelSide,
        handleId: String,
        showPairedGroup: Boolean = false,
    ) {
        retainFocusedTriggerPreview(
            triggerPreviewFocus(
                side = side,
                handleId = handleId,
                showSwipeDistances = true,
                showPairedGroup = showPairedGroup,
            ),
        )
    }

    fun startTriggerDesignPreview(side: PanelSide, handleId: String) {
        retainFocusedTriggerPreview(
            LayoutPreviewFocus(
                side = side,
                handleId = handleId,
                showSwipeDistances = false,
                showPairedGroup = side.isHorizontalEdge,
            ),
        )
    }

    fun refreshFocusedTriggerPreview(
        side: PanelSide,
        handleId: String,
        showPairedGroup: Boolean = false,
    ) {
        activity.overlayServiceController.startLayoutPreview(
            content = LayoutPreviewContent.TRIGGER_ONLY,
            focus = triggerPreviewFocus(
                side = side,
                handleId = handleId,
                showSwipeDistances = false,
                showPairedGroup = showPairedGroup,
            ),
        )
    }

    fun refreshSwipeDistancePreview(
        side: PanelSide,
        handleId: String,
        showPairedGroup: Boolean = false,
    ) {
        activity.overlayServiceController.startLayoutPreview(
            content = LayoutPreviewContent.TRIGGER_ONLY,
            focus = triggerPreviewFocus(
                side = side,
                handleId = handleId,
                showSwipeDistances = true,
                showPairedGroup = showPairedGroup,
            ),
        )
    }

    fun releaseFocusedTriggerPreview() {
        cancelPendingTriggerPreviewStop()
        focusedTriggerPreviewRetainCount = (focusedTriggerPreviewRetainCount - 1).coerceAtLeast(0)
        if (focusedTriggerPreviewRetainCount > 0) return
        scheduleTriggerPreviewStop()
    }

    fun stopGestureAnglesPreview() {
        clearGestureAnglesPreview()
    }

    fun startGestureAnglesPreview(angles: GestureAngles) {
        previewGestureAngles(angles)
    }

    fun updateGestureAnglesPreview(angles: GestureAngles) {
        previewGestureAngles(angles)
    }

    // 悬浮指针"行程范围预览"：窗口与跟手位置都在本进程，拖动期只推实时灵敏度。
    fun previewFloatingPointerAreaSensitivityStart() {
        activity.overlayServiceController.setFloatingPointerAreaPreview(true)
    }

    fun previewFloatingPointerAreaSensitivity(fraction: Float) {
        activity.overlayServiceController.setFloatingPointerAreaPreview(true, fraction)
    }

    /** 松手：清掉临时灵敏度，预览继续跟随已保存设置。 */
    fun previewFloatingPointerAreaSensitivityEnd() {
        activity.overlayServiceController.setFloatingPointerAreaPreview(true, Float.NaN)
    }

    /** 小组件编辑器点"预览"：弹出小组件面板显示真身。 */
    fun showWidgetPanelPreview() {
        activity.overlayServiceController.showWidgetPanelPreview()
    }

    fun stopTriggerPreview() {
        cancelPendingTriggerPreviewStop()
        focusedTriggerPreviewRetainCount = 0
        activity.overlayServiceController.stopLayoutPreview()
    }

    fun startFloatBallStripZonePreview() {
        activity.overlayServiceController.setFloatBallStripZonePreview(true)
    }

    fun stopFloatBallStripZonePreview() {
        activity.overlayServiceController.setFloatBallStripZonePreview(false)
    }

    fun previewFloatBallPositionY(fraction: Float) {
        activity.overlayServiceController.previewFloatBallPositionYFraction(fraction)
    }

    fun endFloatBallPositionYPreview(restoreIfNeeded: Boolean) {
        activity.overlayServiceController.endFloatBallPositionYPreview(restoreIfNeeded)
    }

    fun clearFloatBallPositionYPreviewRestore() {
        activity.overlayServiceController.clearFloatBallPositionYPreviewRestore()
    }

    fun previewFloatBallAppearance(
        sizeDp: Float? = null,
        opacity: Float? = null,
        visibleFraction: Float? = null,
        lineHeightFraction: Float? = null,
        lineWidthDp: Float? = null,
        lineOpacity: Float? = null,
    ) {
        activity.overlayServiceController.previewFloatBallAppearance(
            sizeDp = sizeDp,
            opacity = opacity,
            visibleFraction = visibleFraction,
            lineHeightFraction = lineHeightFraction,
            lineWidthDp = lineWidthDp,
            lineOpacity = lineOpacity,
        )
    }

    fun endFloatBallAppearancePreview(restoreIfNeeded: Boolean) {
        activity.overlayServiceController.endFloatBallAppearancePreview(restoreIfNeeded)
    }

    fun clearFloatBallAppearancePreviewRestore() {
        activity.overlayServiceController.clearFloatBallAppearancePreviewRestore()
    }

    fun startCornerZonePreview() {
        activity.overlayServiceController.setCornerZonePreviewActive(true)
    }

    fun stopCornerZonePreview() {
        activity.overlayServiceController.setCornerZonePreviewActive(false)
    }

    fun updateCornerZonePreview(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) {
        activity.overlayServiceController.applyCornerZonePreviewDimensions(
            verticalEdgeWidthDp = verticalEdgeWidthDp,
            verticalEdgeHeightDp = verticalEdgeHeightDp,
            horizontalEdgeWidthDp = horizontalEdgeWidthDp,
            horizontalEdgeHeightDp = horizontalEdgeHeightDp,
        )
    }

    fun previewIndexHeightFraction(fraction: Float) {
        activity.overlayServiceController.previewIndexHeightFraction(fraction)
    }

    fun clearIndexHeightPreview() {
        activity.overlayServiceController.clearIndexHeightPreview()
    }

    /** 松手提交索引高度预览（等设置落盘再撤，避免跳回旧值）。 */
    fun commitIndexHeightPreview() {
        activity.overlayServiceController.commitIndexHeightPreview()
    }

    /**
     * 索引页上的两个滑条（长度 / 位置）共用一个预览会话：松手时只提交**刚刚拖过的那一个**，
     * 另一个直接丢弃。否则任何一个滑条松手都会把两个"待确认"值同时顶起来 2 秒。
     */
    fun commitIndexSliderPreviews() {
        activity.overlayServiceController.commitIndexSliderPreviews()
    }

    fun previewIndexVerticalPositionFraction(fraction: Float) {
        activity.overlayServiceController.previewIndexVerticalPositionFraction(fraction)
    }

    fun clearIndexVerticalPositionPreview() {
        activity.overlayServiceController.clearIndexVerticalPositionPreview()
    }

    /** 松手提交索引位置预览（语义同 [commitIndexHeightPreview]）。 */
    fun commitIndexVerticalPositionPreview() {
        activity.overlayServiceController.commitIndexVerticalPositionPreview()
    }

    fun previewTriggerHandleEdgeWidth(side: PanelSide, handleId: String, edgeWidthDp: Float) {
        activity.overlayServiceController.previewTriggerHandle(
            side = side,
            handleId = handleId,
            edgeWidthDp = edgeWidthDp,
        )
    }

    fun previewTriggerHandleVerticalRange(
        side: PanelSide,
        handleId: String,
        topFraction: Float,
        bottomFraction: Float,
    ) {
        activity.overlayServiceController.previewTriggerHandle(
            side = side,
            handleId = handleId,
            topFraction = topFraction,
            bottomFraction = bottomFraction,
        )
    }

    fun previewTriggerHandleSwipeDistances(
        side: PanelSide,
        handleId: String,
        shortSwipeDistanceDp: Float? = null,
        longSwipeDistanceDp: Float? = null,
    ) {
        activity.overlayServiceController.previewTriggerHandle(
            side = side,
            handleId = handleId,
            shortSwipeDistanceDp = shortSwipeDistanceDp,
            longSwipeDistanceDp = longSwipeDistanceDp,
        )
    }

    fun previewTriggerHandleDesign(
        side: PanelSide,
        handleId: String,
        design: com.slideindex.app.gesture.TriggerHandleDesign,
    ) {
        activity.overlayServiceController.previewTriggerHandle(
            side = side,
            handleId = handleId,
            design = design,
        )
    }

    /** 手势角度预览。 */
    fun previewGestureAngles(angles: com.slideindex.app.gesture.GestureAngles) {
        activity.overlayServiceController.setGestureAnglesPreview(angles)
    }

    fun clearGestureAnglesPreview() {
        activity.overlayServiceController.setGestureAnglesPreview(null)
    }

    fun clearTriggerHandleLayoutPreview() {
        activity.overlayServiceController.clearOverlayLayoutPreview()
    }

    /** 松手提交触钮预览（等设置落盘再撤，避免跳回旧值）。 */
    fun commitTriggerHandleLayoutPreview() {
        activity.overlayServiceController.commitTriggerHandleLayoutPreview()
    }

    fun clearOverlayLayoutPreview() {
        activity.overlayServiceController.clearOverlayLayoutPreview()
    }

    private fun triggerPreviewFocus(
        side: PanelSide,
        handleId: String,
        showSwipeDistances: Boolean,
        showPairedGroup: Boolean,
    ): LayoutPreviewFocus = LayoutPreviewFocus(
        side = side,
        handleId = handleId,
        showSwipeDistances = showSwipeDistances,
        showPairedGroup = showPairedGroup,
    )

    private fun retainFocusedTriggerPreview(focus: LayoutPreviewFocus) {
        cancelPendingTriggerPreviewStop()
        focusedTriggerPreviewRetainCount++
        activity.overlayServiceController.startLayoutPreview(
            content = LayoutPreviewContent.TRIGGER_ONLY,
            focus = focus,
        )
    }

    private fun scheduleTriggerPreviewStop() {
        cancelPendingTriggerPreviewStop()
        pendingTriggerPreviewStop = Runnable {
            if (focusedTriggerPreviewRetainCount == 0) {
                activity.overlayServiceController.stopLayoutPreview()
            }
        }
        triggerPreviewHandler.postDelayed(pendingTriggerPreviewStop!!, TRIGGER_PREVIEW_HANDOFF_MS)
    }

    private fun cancelPendingTriggerPreviewStop() {
        pendingTriggerPreviewStop?.let(triggerPreviewHandler::removeCallbacks)
        pendingTriggerPreviewStop = null
    }

    companion object {
        private const val TRIGGER_PREVIEW_HANDOFF_MS = 80L
        private val triggerPreviewHandler = Handler(Looper.getMainLooper())
        private var focusedTriggerPreviewRetainCount = 0
        private var pendingTriggerPreviewStop: Runnable? = null
    }

    fun startActivity(intent: Intent) {
        activity.startActivity(intent)
    }

    fun requestNotificationPermission() {
        activity.requestNotificationPermission()
    }

    fun requestShizuku() {
        TaskManagerUtil.requestPermission(activity)
    }

    fun refreshServiceState() {
        activity.refreshServiceState()
    }

    fun refreshPermissionState() {
        activity.refreshPermissionState()
    }

    fun previewHaptic(enabled: Boolean = true, strengthLevel: Int? = null) {
        launch {
            val latest = deps.settingsRepository.settings.first()
            HapticHelper.preview(
                activity.window.decorView,
                latest.copy(
                    hapticEnabled = enabled,
                    hapticStrengthLevel = strengthLevel ?: latest.hapticStrengthLevel,
                ),
            )
        }
    }

    fun openAccessibilitySettings() {
        startActivity(PermissionHelper.accessibilitySettingsIntent())
    }

    fun openOverlaySettings() {
        startActivity(PermissionHelper.overlaySettingsIntent(activity))
    }

    fun openNotificationListenerSettings() {
        MediaSessionHelper.openNotificationListenerSettings(activity)
    }

    fun openUsageAccessSettings() {
        PermissionHelper.requestUsageAccess(activity)
    }

    fun requestBatteryOptimization() {
        if (!PermissionHelper.requestBatteryOptimizationAccess(activity)) {
            deps.userMessageBus.showError(
                activity.getString(R.string.battery_optimization_request_failed),
            )
        }
    }

    fun openAutoStartSettings() {
        KeepAliveHelper.gotoSettings(activity)
    }

    fun requestSecureSettingsGrant(): Boolean {
        val granted = SecureSettingsHelper.grantViaShizuku(activity)
        refreshPermissionState()
        val messageRes = if (granted) {
            R.string.secure_settings_grant_success
        } else {
            R.string.secure_settings_grant_failed
        }
        val message = activity.getString(messageRes)
        if (granted) {
            deps.userMessageBus.showSuccess(message)
        } else {
            deps.userMessageBus.showError(message)
        }
        return granted
    }

    fun requestReadLogsGrant(): Boolean {
        val granted = ClipboardPermissionHelper.grantViaShizuku(activity)
        val messageRes = if (granted) {
            R.string.secure_settings_grant_success
        } else {
            R.string.secure_settings_grant_failed
        }
        val message = activity.getString(messageRes)
        if (granted) {
            deps.userMessageBus.showSuccess(message)
        } else {
            deps.userMessageBus.showError(message)
        }
        return granted
    }
}
