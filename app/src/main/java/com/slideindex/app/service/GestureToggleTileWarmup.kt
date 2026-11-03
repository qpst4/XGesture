package com.slideindex.app.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Proactively binds the QS tiles ([GestureToggleTileService] + [ShakeToggleTileService]) so
 * SystemUI delivers tile clicks without cold-bind delay.
 */
object GestureToggleTileWarmup {
    fun requestListening(context: Context, reason: String) {
        request(
            context = context,
            serviceClass = GestureToggleTileService::class.java,
            logTag = GestureToggleTileService.LOG_TAG,
            reason = reason,
        )
        request(
            context = context,
            serviceClass = ShakeToggleTileService::class.java,
            logTag = ShakeToggleTileService.LOG_TAG,
            reason = reason,
        )
    }

    private fun request(
        context: Context,
        serviceClass: Class<out TileService>,
        logTag: String,
        reason: String,
    ) {
        val appContext = context.applicationContext
        val component = ComponentName(appContext, serviceClass)
        runCatching {
            TileService.requestListeningState(appContext, component)
            Log.i(logTag, "requestListeningState: reason=$reason")
        }.onFailure { error ->
            Log.w(logTag, "requestListeningState failed: reason=$reason", error)
        }
    }
}
