package com.slideindex.app.overlay

/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.HoneycombDisplaySettings
import com.slideindex.app.settings.resolveHoneycombLongPressArmed
import com.slideindex.app.util.PermissionHelper

@SuppressLint("StaticFieldLeak") // Overlay singleton; controller cleared in releaseOverlayState()
object HoneycombAppPickerOverlayWindow {
    private const val TAG = "HoneycombOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var controller: HoneycombOverlayController? = null
    private var appContext: Context? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
    private var externalTracking = false
    /** Browse mode stays open after the edge gesture session ends. */
    private var persistAfterSessionEnd = false
    private var forceBrowseMode = false

    val isShowing: Boolean get() = controller?.isVisible == true

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        externalTracking: Boolean,
        forceBrowseMode: Boolean = false,
        onLaunch: (QuickLauncherItem, Boolean) -> Unit
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(
                    context,
                    settings,
                    anchorRawX,
                    anchorRawY,
                    externalTracking,
                    forceBrowseMode,
                    onLaunch
                )
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }

        val items = settings.honeycombLauncher.honeycombRuntimeItems()
        if (items.isEmpty()) {
            Log.w(TAG, "show: honeycomb launcher list is empty")
            return false
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }

        val hostContext = overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return false
            }
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext)
        val appRepository = deps?.appRepository
        val apps = appRepository?.getCachedApps().orEmpty()
        val appsByPackage = apps.associateBy { it.packageName }
        val targets = HoneycombTargetResolver.resolve(
            hostContext,
            items,
            appsByPackage,
            appRepository,
            settings.activityShortcuts,
            settings.shellCommands
        )
        if (targets.isEmpty()) {
            Log.w(TAG, "show: no resolvable honeycomb targets")
            return false
        }

        val displayConfig = HoneycombDisplayConfig.from(settings)
        val browseMode = forceBrowseMode ||
            displayConfig.honeycombMode == HoneycombDisplayConfig.MODE_BROWSE
        val effectiveConfig = if (forceBrowseMode) {
            displayConfig.copy(honeycombMode = HoneycombDisplayConfig.MODE_BROWSE)
        } else {
            displayConfig
        }
        val screenWidth = hostContext.resources.displayMetrics.widthPixels
        val corner = if (anchorRawX < screenWidth * 0.5f) {
            HoneycombCorner.LEFT
        } else {
            HoneycombCorner.RIGHT
        }

        val overlayController = controller ?: HoneycombOverlayController(hostContext, mainHandler).also {
            controller = it
        }
        this.externalTracking = externalTracking
        this.forceBrowseMode = forceBrowseMode
        persistAfterSessionEnd = browseMode || externalTracking

        val launchCallback = onLaunch
        if (appRepository != null) {
            val iconSizePx = (
                displayConfig.honeycombIconSizeDp *
                    hostContext.resources.displayMetrics.density
                ).toInt()
            HoneycombIconLoader.warmAppIcons(appRepository, items, iconSizePx)
        }
        val shown = overlayController.show(
            targets,
            corner,
            anchorRawX,
            anchorRawY,
            effectiveConfig,
            externalTracking,
            object : HoneycombOverlayController.Listener {
                override fun onLaunch(target: HoneycombRuntimeTarget, selectionPressDurationMs: Long) {
                    val longPressArmed = resolveLaunchLongPressArmed(
                        settings = settings,
                        selectionPressDurationMs = selectionPressDurationMs
                    )
                    screenOffDismissReceiver.unregister()
                    releaseOverlayState()
                    launchCallback(target.item, longPressArmed)
                }

                override fun onClosed() {
                    screenOffDismissReceiver.unregister()
                    releaseOverlayState()
                }
            }
        )
        if (!shown) return false

        appContext = hostContext
        screenOffDismissReceiver.register(hostContext)
        if (externalTracking) {
            overlayController.externalMove(anchorRawX, anchorRawY)
        }
        if (appRepository != null && targets.any { it.icon == null }) {
            HoneycombIconLoader.loadMissingIconsAsync(
                context = hostContext,
                targets = targets,
                appsByPackage = appsByPackage,
                appRepository = appRepository,
                activityShortcuts = settings.activityShortcuts,
                shellCommands = settings.shellCommands,
                onIconsReady = {
                    if (controller === overlayController && overlayController.isVisible) {
                        overlayController.refreshIcons()
                    }
                }
            )
        }
        return true
    }

    fun updatePointer(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePointer(rawX, rawY) }
            return
        }
        controller?.externalMove(rawX, rawY)
    }

    fun confirmSelection(
        rawX: Float,
        rawY: Float,
        actionExecutor: ActionExecutor,
        settings: AppSettings
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { confirmSelection(rawX, rawY, actionExecutor, settings) }
            return
        }
        val browseMode = forceBrowseMode ||
            settings.honeycombDisplay.mode == HoneycombDisplaySettings.MODE_BROWSE
        updatePointer(rawX, rawY)
        controller?.externalUp(rawX, rawY, false)
        if (browseMode && externalTracking) {
            externalTracking = false
            persistAfterSessionEnd = true
            controller?.enableDirectTouch()
        }
    }

    /** Called when the edge gesture session ends; browse-mode overlays may stay open. */
    fun onGestureSessionEnd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onGestureSessionEnd() }
            return
        }
        if (persistAfterSessionEnd) return
        dismiss()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        controller?.removeNow()
        screenOffDismissReceiver.unregister()
        releaseOverlayState()
    }

    private fun releaseOverlayState() {
        controller = null
        appContext = null
        externalTracking = false
        persistAfterSessionEnd = false
        forceBrowseMode = false
        BlurredWallpaperCache.clear()
    }

    private fun resolveLaunchLongPressArmed(
        settings: AppSettings,
        selectionPressDurationMs: Long
    ): Boolean = settings.resolveHoneycombLongPressArmed(selectionPressDurationMs.coerceAtLeast(0L))
}
