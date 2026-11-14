package com.slideindex.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.slideindex.app.clipboard.ClipboardEntryType
import com.slideindex.app.clipboard.ClipboardPayload
import com.slideindex.app.clipboardoverlay.ClipboardOverlayWindow
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ToggleMode
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.util.LockScreenState
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.QuickToolsHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlin.concurrent.thread

/**
 * 仅 debug 构建：不经手势，直接用 adb 触发热点动作，便于真机定位特权链路。
 *
 * ```
 * adb shell am broadcast -a com.slideindex.app.DEBUG_HOTSPOT --es mode on
 * adb shell am broadcast -a com.slideindex.app.DEBUG_HOTSPOT --es mode off
 * adb shell am broadcast -a com.slideindex.app.DEBUG_HOTSPOT --es mode toggle
 * adb shell am broadcast -a com.slideindex.app.DEBUG_HOTSPOT --es mode overlay
 * ```
 */
class HotspotDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()

        if (intent.getStringExtra("mode") == "save") {
            thread(name = "HotspotDebug") {
                try {
                    val bmp = android.graphics.Bitmap.createBitmap(320, 180, android.graphics.Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.rgb(220, 40, 40))
                    Log.i(TAG, "save: repo=${com.slideindex.app.clipboard.ClipboardAccess.repository != null}")
                    val ok = com.slideindex.app.overlay.FloatBallTextPick.saveScreenshot(appContext, bmp)
                    Log.i(TAG, "save: result=$ok")
                } catch (t: Throwable) {
                    Log.e(TAG, "save failed", t)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.getStringExtra("mode") == "overlay") {
            thread(name = "HotspotDebug") {
                try {
                    val lock = LockScreenState.isActive(appContext)
                    val host = MessageOverlayHost.resolveHostContext(appContext)
                    val canDraw = host?.let { PermissionHelper.canDrawOverlays(it) }
                    val enabled = OverlayDependencyAccess.overlayDependencies(appContext)
                        ?.settingsRepository?.readSnapshot()?.clipboardOverlayEnabled
                    Log.i(TAG, "overlay: lock=$lock host=${host?.javaClass?.simpleName} canDraw=$canDraw enabled=$enabled")
                    ClipboardOverlayWindow.show(
                        appContext,
                        ClipboardPayload(type = ClipboardEntryType.TEXT, text = "浮窗自测 Overlay"),
                    )
                    Log.i(TAG, "overlay: show() returned")
                } catch (t: Throwable) {
                    Log.e(TAG, "overlay failed", t)
                } finally {
                    pending.finish()
                }
            }
            return
        }

        val mode = when (intent.getStringExtra("mode")) {
            "on" -> ToggleMode.ON
            "off" -> ToggleMode.OFF
            else -> ToggleMode.TOGGLE
        }

        thread(name = "HotspotDebug") {
            try {
                Log.i(TAG, "start mode=$mode permission=${TaskManagerUtil.hasPermission()}")
                Log.i(TAG, "stateBefore=${QuickToolsHelper.readHotspotEnabled()}")
                val result = QuickToolsHelper.applyHotspot(appContext, mode)
                Log.i(TAG, "apply=$result stateAfter=${QuickToolsHelper.readHotspotEnabled()}")
            } catch (t: Throwable) {
                Log.e(TAG, "failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        private const val TAG = "HotspotDebug"
    }
}
