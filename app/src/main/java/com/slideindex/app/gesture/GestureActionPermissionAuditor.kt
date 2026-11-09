package com.slideindex.app.gesture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerEdgeSide
import com.slideindex.app.ui.gesturepicker.gestureActionDescriptionText
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.gesturepicker.gestureActionPermissionHintText
import com.slideindex.app.ui.gesturepicker.isGestureActionEnabledOnDevice
import com.slideindex.app.ui.gesturepicker.requestPermissionForAdjustAction
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.WallpaperPermissionLauncher
import com.slideindex.app.settings.HoneycombDisplaySettings
import com.slideindex.app.settings.HolographicLauncherSettings
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.R

data class MissingGesturePermission(
    val action: GestureAction,
    val actionLabel: String,
    val actionDescription: String?,
    val permissionHint: String,
    val requestTag: String? = null
)

object GestureActionPermissionAuditor {
    const val REQUEST_CLIPBOARD_MEDIA_READ = "clipboard_media_read"
    const val REQUEST_WALLPAPER_BLUR_ALL_FILES = "wallpaper_blur_all_files"
    const val REQUEST_NOTIFICATION_LISTENER = "notification_listener"
    fun collectConfiguredActions(settings: AppSettings): List<GestureAction> {
        val actions = linkedSetOf<GestureAction>()
        fun add(action: GestureAction) {
            if (action.type != GestureActionType.NONE) {
                actions += action
            }
        }

        settings.gestureRules.filter { it.enabled }.forEach { add(it.action) }
        // 侧滑手势「按应用分档」：每档是一整片独立快照，档内的规则同样要能审计到权限。
        if (settings.independentAppGestureEnabled) {
            settings.gestureProfiles.forEach { profile ->
                profile.snapshot.gestureRules.filter { it.enabled }.forEach { add(it.action) }
                profile.snapshot.gestureRulesLandscape.filter { it.enabled }.forEach { add(it.action) }
            }
        }
        settings.floatBallGestureActions.values.forEach(::add)

        val shake = settings.shakeGestureSettings
        if (shake.enabled) {
            shake.basicActions.values.forEach(::add)
            shake.lockScreenActions.values.forEach(::add)
            if (shake.independentAppShakeEnabled) {
                shake.perAppActions.values.forEach { perApp ->
                    perApp.values.forEach(::add)
                }
            }
        }

        if (settings.faceDownGestureSettings.enabled) {
            add(settings.faceDownGestureSettings.action)
        }

        if (settings.backTapSettings.enabled) {
            add(settings.backTapSettings.action)
        }

        add(settings.floatingPointerJoystickLongPressAction)
        settings.floatingPointerRadialSlotActions.forEach(::add)
        val edgeConfig = settings.floatingPointerEdgeActionsConfig
        FloatingPointerEdgeSide.entries.forEach { side ->
            val bar = edgeConfig.bar(side)
            if (bar.enabled) {
                bar.layoutSlots().forEach { slot -> add(slot.action) }
            }
        }

        settings.quickLauncherPanels
            .flatMap { it.items }
            .filter { it.type == QuickLauncherItemType.ACTION }
            .forEach { item ->
                QuickLauncherItemCodec.parseActionPayload(item.payload)?.let(::add)
            }

        return actions.toList()
    }

    fun auditMissingPermissions(context: Context, settings: AppSettings): List<MissingGesturePermission> {
        val gestureMissing = collectConfiguredActions(settings)
            .filter { isGestureActionEnabledOnDevice(it) }
            .mapNotNull { action ->
                val hint = gestureActionPermissionHintText(context, action) ?: return@mapNotNull null
                MissingGesturePermission(
                    action = action,
                    actionLabel = gestureActionLabelText(context, action),
                    actionDescription = gestureActionDescriptionText(context, action),
                    permissionHint = hint
                )
            }

        val featureMissing = buildList {
            if (settings.clipboardScreenshotMonitoring &&
                !ClipboardPermissionHelper.hasMediaReadPermission(context)
            ) {
                add(
                    MissingGesturePermission(
                        action = GestureAction.None,
                        actionLabel = context.getString(R.string.clipboard_screenshot_monitoring_title),
                        actionDescription = context.getString(R.string.clipboard_screenshot_monitoring_desc),
                        permissionHint = context.getString(R.string.clipboard_media_read_status_denied),
                        requestTag = REQUEST_CLIPBOARD_MEDIA_READ
                    )
                )
            }

            val usesWallpaperBlur = settings.honeycombDisplay.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR ||
                settings.holographicLauncher.backgroundStyle == HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR ||
                settings.searchPanelBackgroundStyle == SearchPanelBackgroundStyle.WALLPAPER_BLUR

            if (usesWallpaperBlur && !SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)) {
                add(
                    MissingGesturePermission(
                        action = GestureAction.None,
                        actionLabel = context.getString(R.string.wallpaper_blur_permission_title),
                        actionDescription = context.getString(R.string.honeycomb_background_wallpaper_blur),
                        permissionHint = context.getString(R.string.wallpaper_blur_permission_missing),
                        requestTag = REQUEST_WALLPAPER_BLUR_ALL_FILES
                    )
                )
            }

            if (settings.messageReminderSettings.enabled &&
                !com.slideindex.app.util.MediaSessionHelper.isNotificationListenerEnabled(context)
            ) {
                add(
                    MissingGesturePermission(
                        action = GestureAction.None,
                        actionLabel = context.getString(R.string.message_reminder_title),
                        actionDescription = context.getString(R.string.message_reminder_permission_listener_desc),
                        permissionHint = context.getString(R.string.message_reminder_permission_listener_title),
                        requestTag = REQUEST_NOTIFICATION_LISTENER
                    )
                )
            }
        }

        return (gestureMissing + featureMissing)
            .distinctBy { it.permissionHint to it.actionLabel }
    }

    fun requestPermission(context: Context, item: MissingGesturePermission) {
        when (item.requestTag) {
            REQUEST_CLIPBOARD_MEDIA_READ -> {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            REQUEST_WALLPAPER_BLUR_ALL_FILES -> {
                SystemWallpaperBlurHelper.requestWallpaperPermission(context)
            }
            REQUEST_NOTIFICATION_LISTENER -> {
                com.slideindex.app.util.MediaSessionHelper.openNotificationListenerSettings(context)
            }
            else -> requestPermissionForAdjustAction(context, item.action)
        }
    }

    fun requestPermission(context: Context, action: GestureAction) {
        requestPermissionForAdjustAction(context, action)
    }
}
