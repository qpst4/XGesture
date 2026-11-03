package com.slideindex.app.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.ServiceEnabledStore
import com.slideindex.app.util.ShakeEnabledStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile mirroring the shake master switch (`shakeGestureSettings.enabled`).
 *
 * Deliberately a toggle, not navigation: clicking it is equivalent to the switch at the top of
 * the shake page and to the [com.slideindex.app.gesture.GestureAction.ToggleShakeGesture]
 * action. While the XGesture service or its accessibility service is off, shake cannot run at
 * all, so the tile is shown as unavailable instead of pretending to be "on".
 *
 * The QS panel may bind this service while the process is cold. [ShakeEnabledStore] provides a
 * synchronous first-frame answer; the settings flow then keeps the tile honest.
 */
@AndroidEntryPoint
class ShakeToggleTileService : TileService() {

    @Inject
    lateinit var deps: AppDependencies

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null

    @Volatile
    private var pendingUserToggle = false

    @Volatile
    private var lastClickUptimeMs = 0L

    override fun onTileAdded() {
        super.onTileAdded()
        Log.i(LOG_TAG, "onTileAdded")
        GestureToggleTileWarmup.requestListening(this, "shakeTileAdded")
    }

    override fun onStartListening() {
        super.onStartListening()
        settingsJob?.cancel()
        if (!pendingUserToggle) refreshTileState()
        settingsJob = deps.settingsRepository.settings
            .map { it.shakeGestureSettings.enabled to it.serviceEnabled }
            .distinctUntilChanged()
            .onEach { (shakeEnabled, serviceEnabled) ->
                if (pendingUserToggle) return@onEach
                if (serviceEnabled && isAccessibilityReady()) {
                    updateTileState(shakeEnabled)
                } else {
                    updateUnavailableState()
                }
            }
            .launchIn(serviceScope)
    }

    override fun onStopListening() {
        settingsJob?.cancel()
        settingsJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        lastClickUptimeMs = SystemClock.uptimeMillis()
        Log.i(
            LOG_TAG,
            "onClick: thread=${Thread.currentThread().name} locked=$isLocked " +
                "uptime=$lastClickUptimeMs",
        )
        // A settings write doesn't need an unlocked device, so don't route through
        // unlockAndRun(): on a secure lock screen that would defeat the point of a quick
        // "turn shake off" toggle. Some OEMs may still refuse to deliver tile clicks there.
        mainHandler.post { handleClick() }
    }

    private fun handleClick() {
        val serviceEnabled = readServiceEnabled()
        val accessibilityReady = isAccessibilityReady()
        if (!serviceEnabled || !accessibilityReady) {
            Log.i(
                LOG_TAG,
                "handleClick ignored: serviceEnabled=$serviceEnabled " +
                    "a11yReady=$accessibilityReady",
            )
            Toast.makeText(this, R.string.tile_shake_unavailable_hint, Toast.LENGTH_SHORT).show()
            refreshTileState()
            return
        }

        val current = readCurrentEnabled()
        val newState = !current
        Log.i(
            LOG_TAG,
            "handleClick: current=$current newState=$newState " +
                "elapsed=${SystemClock.uptimeMillis() - lastClickUptimeMs}ms",
        )
        pendingUserToggle = true
        // Optimistic mirror + tile update: SystemUI needs an answer before DataStore commits.
        ShakeEnabledStore.write(applicationContext, newState)
        updateTileState(newState)

        // Persist on the application scope: SystemUI may unbind this service right after the
        // click, and the DataStore write must not be cancelled with the tile binding.
        deps.applicationScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    deps.settingsRepository.setShakeGesturesEnabled(newState)
                }
                Log.i(LOG_TAG, "persist done: enabled=$newState")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(LOG_TAG, "persist failed: enabled=$newState", error)
                ShakeEnabledStore.write(applicationContext, current)
                refreshTileState()
            } finally {
                pendingUserToggle = false
            }
        }
    }

    private fun refreshTileState() {
        if (!readServiceEnabled() || !isAccessibilityReady()) {
            updateUnavailableState()
            return
        }
        updateTileState(readCurrentEnabled())
    }

    private fun readCurrentEnabled(): Boolean =
        if (ShakeEnabledStore.hasPersistedValue(this)) {
            ShakeEnabledStore.read(this)
        } else {
            deps.settingsRepository.readSnapshot().shakeGestureSettings.enabled
        }

    private fun readServiceEnabled(): Boolean =
        if (ServiceEnabledStore.hasPersistedValue(this)) {
            ServiceEnabledStore.read(this)
        } else {
            deps.settingsRepository.readSnapshot().serviceEnabled
        }

    private fun isAccessibilityReady(): Boolean =
        SlideIndexAccessibilityService.isConnected() ||
            PermissionHelper.isAccessibilityServiceEnabled(this)

    private fun updateTileState(enabled: Boolean) {
        val tile = qsTile
        if (tile == null) {
            Log.w(LOG_TAG, "updateTileState: qsTile is null, enabled=$enabled")
            return
        }
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_shake_switch)
        tile.updateTile()
        Log.d(LOG_TAG, "updateTileState: enabled=$enabled state=${tile.state}")
    }

    private fun updateUnavailableState() {
        val tile = qsTile
        if (tile == null) {
            Log.w(LOG_TAG, "updateUnavailableState: qsTile is null")
            return
        }
        tile.state = Tile.STATE_UNAVAILABLE
        tile.label = getString(R.string.tile_shake_switch)
        tile.updateTile()
        Log.d(LOG_TAG, "updateUnavailableState")
    }

    internal companion object {
        const val LOG_TAG = "ShakeToggleTile"
    }
}
