package com.slideindex.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.slideindex.app.gesture.ToggleMode
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
 * ```
 */
class HotspotDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mode = when (intent.getStringExtra("mode")) {
            "on" -> ToggleMode.ON
            "off" -> ToggleMode.OFF
            else -> ToggleMode.TOGGLE
        }
        val appContext = context.applicationContext
        val pending = goAsync()

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
