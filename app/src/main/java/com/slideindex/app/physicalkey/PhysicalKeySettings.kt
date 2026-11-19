package com.slideindex.app.physicalkey

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.launcher.QuickLauncherItemCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PhysicalKeyEntry(
    val gesture: PhysicalKeyGesture,
    val enabled: Boolean = true,
    val action: GestureAction = GestureAction.None,
)

data class PhysicalKeySettings(
    val enabled: Boolean = false,
    val entries: Map<PhysicalKeyGesture, PhysicalKeyEntry> = defaultEntries(),
    val longPressTimeoutMs: Long = 500L,
    val doubleClickTimeoutMs: Long = 350L,
    val mediaPlaybackPrioritized: Boolean = true,
    val interceptNativeEvents: Boolean = false,
    val screenOffTriggerEnabled: Boolean = true,
) {
    companion object {
        fun defaultEntries(): Map<PhysicalKeyGesture, PhysicalKeyEntry> {
            return mapOf(
                PhysicalKeyGesture.POWER_LONG_PRESS to PhysicalKeyEntry(
                    gesture = PhysicalKeyGesture.POWER_LONG_PRESS,
                    enabled = true,
                    action = GestureAction.Flashlight(),
                ),
                PhysicalKeyGesture.POWER_DOUBLE_PRESS to PhysicalKeyEntry(
                    gesture = PhysicalKeyGesture.POWER_DOUBLE_PRESS,
                    enabled = false,
                    action = GestureAction.None,
                ),
                PhysicalKeyGesture.VOLUME_UP_LONG_PRESS to PhysicalKeyEntry(
                    gesture = PhysicalKeyGesture.VOLUME_UP_LONG_PRESS,
                    enabled = true,
                    action = GestureAction.MediaNext,
                ),
                PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS to PhysicalKeyEntry(
                    gesture = PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS,
                    enabled = false,
                    action = GestureAction.None,
                ),
                PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS to PhysicalKeyEntry(
                    gesture = PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS,
                    enabled = true,
                    action = GestureAction.Screenshot,
                ),
                PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS to PhysicalKeyEntry(
                    gesture = PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS,
                    enabled = false,
                    action = GestureAction.None,
                ),
                PhysicalKeyGesture.VOLUME_BOTH_PRESS to PhysicalKeyEntry(
                    gesture = PhysicalKeyGesture.VOLUME_BOTH_PRESS,
                    enabled = true,
                    action = GestureAction.SearchPanel,
                ),
            )
        }
    }
}

@Singleton
class PhysicalKeySettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<PhysicalKeySettings> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (_state.value.enabled == enabled) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setLongPressTimeoutMs(ms: Long) {
        val clamped = ms.coerceIn(200L, 2000L)
        if (_state.value.longPressTimeoutMs == clamped) return
        prefs.edit().putLong(KEY_LONG_PRESS_TIMEOUT, clamped).apply()
        _state.value = _state.value.copy(longPressTimeoutMs = clamped)
    }

    fun setDoubleClickTimeoutMs(ms: Long) {
        val clamped = ms.coerceIn(150L, 800L)
        if (_state.value.doubleClickTimeoutMs == clamped) return
        prefs.edit().putLong(KEY_DOUBLE_CLICK_TIMEOUT, clamped).apply()
        _state.value = _state.value.copy(doubleClickTimeoutMs = clamped)
    }

    fun setMediaPlaybackPrioritized(prioritized: Boolean) {
        if (_state.value.mediaPlaybackPrioritized == prioritized) return
        prefs.edit().putBoolean(KEY_MEDIA_PRIORITIZED, prioritized).apply()
        _state.value = _state.value.copy(mediaPlaybackPrioritized = prioritized)
    }

    fun setInterceptNativeEvents(intercept: Boolean) {
        if (_state.value.interceptNativeEvents == intercept) return
        prefs.edit().putBoolean(KEY_INTERCEPT_NATIVE, intercept).apply()
        _state.value = _state.value.copy(interceptNativeEvents = intercept)
    }

    fun setScreenOffTriggerEnabled(enabled: Boolean) {
        if (_state.value.screenOffTriggerEnabled == enabled) return
        prefs.edit().putBoolean(KEY_SCREEN_OFF_TRIGGER, enabled).apply()
        _state.value = _state.value.copy(screenOffTriggerEnabled = enabled)
    }

    fun updateEntry(gesture: PhysicalKeyGesture, enabled: Boolean, action: GestureAction) {
        val currentEntries = _state.value.entries.toMutableMap()
        val isEffectiveEnabled = action !is GestureAction.None
        currentEntries[gesture] = PhysicalKeyEntry(
            gesture = gesture,
            enabled = isEffectiveEnabled,
            action = action,
        )
        val serialized = serialize(currentEntries)
        prefs.edit().putString(KEY_ENTRIES, serialized).apply()
        _state.value = _state.value.copy(entries = currentEntries)
    }

    private fun read(): PhysicalKeySettings {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val longPressTimeout = prefs.getLong(KEY_LONG_PRESS_TIMEOUT, 500L)
        val doubleClickTimeout = prefs.getLong(KEY_DOUBLE_CLICK_TIMEOUT, 350L)
        val mediaPrioritized = prefs.getBoolean(KEY_MEDIA_PRIORITIZED, true)
        val interceptNative = prefs.getBoolean(KEY_INTERCEPT_NATIVE, false)
        val screenOffTrigger = prefs.getBoolean(KEY_SCREEN_OFF_TRIGGER, true)
        val entriesRaw = prefs.getString(KEY_ENTRIES, null)
        val entries = if (entriesRaw.isNullOrBlank()) {
            PhysicalKeySettings.defaultEntries()
        } else {
            mergeDefaults(parse(entriesRaw))
        }

        return PhysicalKeySettings(
            enabled = enabled,
            entries = entries,
            longPressTimeoutMs = longPressTimeout,
            doubleClickTimeoutMs = doubleClickTimeout,
            mediaPlaybackPrioritized = mediaPrioritized,
            interceptNativeEvents = interceptNative,
            screenOffTriggerEnabled = screenOffTrigger,
        )
    }

    private fun mergeDefaults(parsed: Map<PhysicalKeyGesture, PhysicalKeyEntry>): Map<PhysicalKeyGesture, PhysicalKeyEntry> {
        val result = PhysicalKeySettings.defaultEntries().toMutableMap()
        result.putAll(parsed)
        return result
    }

    private fun serialize(entries: Map<PhysicalKeyGesture, PhysicalKeyEntry>): String {
        val array = JSONArray()
        for ((_, entry) in entries) {
            val obj = JSONObject()
            obj.put("gesture", entry.gesture.name)
            obj.put("enabled", entry.enabled)
            obj.put("action", encodeAction(entry.action))
            array.put(obj)
        }
        return array.toString()
    }

    private fun parse(raw: String): Map<PhysicalKeyGesture, PhysicalKeyEntry> = runCatching {
        val array = JSONArray(raw)
        val map = mutableMapOf<PhysicalKeyGesture, PhysicalKeyEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val gestureName = obj.optString("gesture")
            val gesture = runCatching { PhysicalKeyGesture.valueOf(gestureName) }.getOrNull() ?: continue
            val action = decodeAction(obj.optString("action").ifBlank { null }) ?: GestureAction.None
            val enabled = action !is GestureAction.None
            map[gesture] = PhysicalKeyEntry(
                gesture = gesture,
                enabled = enabled,
                action = action,
            )
        }
        map
    }.getOrDefault(emptyMap())

    private fun encodeAction(action: GestureAction): String {
        val extra = QuickLauncherItemCodec.encodeActionBody(action)
        return if (extra.isNullOrEmpty()) {
            action.type.id.toString()
        } else {
            "${action.type.id}:$extra"
        }
    }

    private fun decodeAction(raw: String?): GestureAction? {
        if (raw.isNullOrBlank()) return null
        val separator = raw.indexOf(':')
        if (separator < 0) {
            return gestureActionFromTypeId(raw.toIntOrNull() ?: 0)
        }
        val type = GestureActionType.fromId(raw.substring(0, separator).toIntOrNull() ?: 0)
        return QuickLauncherItemCodec.decodeActionBody(type, raw.substring(separator + 1))
            ?: GestureAction.None
    }

    private fun gestureActionFromTypeId(id: Int): GestureAction = when (GestureActionType.fromId(id)) {
        GestureActionType.BACK -> GestureAction.Back
        GestureActionType.HOME -> GestureAction.Home
        GestureActionType.RECENTS -> GestureAction.Recents
        GestureActionType.PREVIOUS_APP -> GestureAction.PreviousApp
        GestureActionType.SCREENSHOT -> GestureAction.Screenshot
        GestureActionType.LOCK_SCREEN -> GestureAction.LockScreen
        GestureActionType.FLASHLIGHT -> GestureAction.Flashlight()
        GestureActionType.SEARCH_PANEL -> GestureAction.SearchPanel
        GestureActionType.MEDIA_PREVIOUS -> GestureAction.MediaPrevious
        GestureActionType.MEDIA_NEXT -> GestureAction.MediaNext
        GestureActionType.MEDIA_PLAY_PAUSE -> GestureAction.MediaPlayPause
        GestureActionType.VOLUME_PANEL -> GestureAction.VolumePanel
        else -> GestureAction.None
    }

    companion object {
        private const val PREFS_NAME = "physical_key_gesture_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_LONG_PRESS_TIMEOUT = "long_press_timeout"
        private const val KEY_DOUBLE_CLICK_TIMEOUT = "double_click_timeout"
        private const val KEY_MEDIA_PRIORITIZED = "media_prioritized"
        private const val KEY_INTERCEPT_NATIVE = "intercept_native"
        private const val KEY_SCREEN_OFF_TRIGGER = "screen_off_trigger"
    }
}
