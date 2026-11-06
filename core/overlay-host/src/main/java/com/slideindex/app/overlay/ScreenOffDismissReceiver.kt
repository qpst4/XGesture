package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * 在单一 [Context] 上注册 [Intent.ACTION_SCREEN_OFF]，保证 unregister 与 register 配对。
 */
class ScreenOffDismissReceiver(
    private val onScreenOff: () -> Unit
) {
    private companion object {
        private const val TAG = "ScreenOffDismissReceiver"
    }
    private var receiver: BroadcastReceiver? = null
    private var registeredAppContext: android.app.Application? = null

    fun register(context: Context) {
        unregister()
        val appContext = context.applicationContext as android.app.Application
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
            }
        }
        receiver = r
        registeredAppContext = appContext
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(r, filter)
            }
        }.onFailure { error ->
            Log.w(TAG, "register screen-off receiver failed", error)
            receiver = null
            registeredAppContext = null
        }
    }

    fun unregister() {
        val r = receiver ?: return
        registeredAppContext?.let { host -> runCatching { host.unregisterReceiver(r) } }
        receiver = null
        registeredAppContext = null
    }
}
