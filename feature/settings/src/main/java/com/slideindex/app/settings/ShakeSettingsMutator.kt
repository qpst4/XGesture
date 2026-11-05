package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker
import com.slideindex.app.shake.FaceDownGestureCodec
import com.slideindex.app.shake.FaceDownGestureSettings
import com.slideindex.app.shake.ShakeGestureCodec
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.shake.ShakeMenu
import com.slideindex.app.shake.ShakeMenuCodec
import com.slideindex.app.shake.ShakeMenuLimits
import com.slideindex.app.shake.ShakeMenuRuntime
import com.slideindex.app.shake.ShakeSensitivityScale
import com.slideindex.app.shake.ShakeWorkMode
import com.slideindex.app.shake.ShakeWorkModes
import com.slideindex.app.shake.remapShakeMenuReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShakeSettingsMutator @Inject constructor(
    private val editor: SettingsPreferencesEditor,
) {
    suspend fun setShakeGesturesEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.SHAKE_GESTURES_ENABLED] = enabled }

    suspend fun setShakeWorkModes(modes: Set<ShakeWorkMode>) = editor.edit {
        it[SettingsPreferenceKeys.SHAKE_WORK_MODES] =
            ShakeWorkModes.encode(modes.ifEmpty { ShakeWorkModes.DEFAULT })
    }

    suspend fun setShakeGestureAction(type: ShakeGestureType, action: GestureAction) = editor.edit { prefs ->
        val current = SettingsSnapshotReader.readShakeGestureSettings(prefs)
        val updated = current.basicActions.toMutableMap().apply { put(type, action) }
        prefs[SettingsPreferenceKeys.SHAKE_GESTURE_ACTIONS] = ShakeGestureCodec.encodeAllActions(updated)
    }

    // ---- 晃动菜单 ----

    suspend fun addShakeMenu(menu: ShakeMenu) = editor.edit { prefs ->
        val current = SettingsSnapshotReader.readShakeGestureSettings(prefs).menus
        if (current.size >= ShakeMenuLimits.MAX_MENUS) return@edit
        if (current.any { it.id == menu.id }) return@edit
        prefs[SettingsPreferenceKeys.SHAKE_MENUS] =
            ShakeMenuCodec.encode(ShakeMenuLimits.clamp(current + menu))
    }

    suspend fun updateShakeMenu(menu: ShakeMenu) = editor.edit { prefs ->
        val current = SettingsSnapshotReader.readShakeGestureSettings(prefs).menus
        if (current.none { it.id == menu.id }) return@edit
        prefs[SettingsPreferenceKeys.SHAKE_MENUS] = ShakeMenuCodec.encode(
            current.map { if (it.id == menu.id) menu else it },
        )
    }

    /** 改某个方向绑定的动作；选「无」表示移除该方向的绑定。 */
    suspend fun setShakeMenuAction(menuId: String, type: ShakeGestureType, action: GestureAction) =
        editor.edit { prefs ->
            val current = SettingsSnapshotReader.readShakeGestureSettings(prefs).menus
            val target = current.firstOrNull { it.id == menuId } ?: return@edit
            val sanitized = action.sanitizeForSlotPicker(SlotPickerKind.ShakeGesture)
            val actions = target.actions.toMutableMap()
            if (sanitized == GestureAction.None) actions.remove(type) else actions[type] = sanitized
            prefs[SettingsPreferenceKeys.SHAKE_MENUS] = ShakeMenuCodec.encode(
                current.map { if (it.id == menuId) target.copy(actions = actions) else it },
            )
        }

    /**
     * 删除菜单，并把所有指向它的绑定改成剩余的第一个菜单；一个都不剩时退化成「无」，
     * 避免留下点了没反应的悬空引用。
     */
    suspend fun removeShakeMenu(menuId: String) = editor.edit { prefs ->
        val current = SettingsSnapshotReader.readShakeGestureSettings(prefs)
        if (current.menus.none { it.id == menuId }) return@edit
        val remaining = current.menus.filterNot { it.id == menuId }
        val fallbackId = remaining.firstOrNull()?.id
        prefs[SettingsPreferenceKeys.SHAKE_MENUS] = ShakeMenuCodec.encode(remaining)

        val basic = current.basicActions.mapValues { (_, action) ->
            action.remapShakeMenuReference(menuId, fallbackId)
        }
        prefs[SettingsPreferenceKeys.SHAKE_GESTURE_ACTIONS] = ShakeGestureCodec.encodeAllActions(basic)

        if (current.lockScreenActions.isNotEmpty()) {
            val lockScreen = current.lockScreenActions.mapValues { (_, action) ->
                action.remapShakeMenuReference(menuId, fallbackId)
            }
            prefs[SettingsPreferenceKeys.SHAKE_LOCK_SCREEN_ACTIONS] =
                ShakeGestureCodec.encodeAllActions(lockScreen)
        }
        if (current.perAppActions.isNotEmpty()) {
            val perApp = current.perAppActions.mapValues { (_, actions) ->
                actions.mapValues { (_, action) -> action.remapShakeMenuReference(menuId, fallbackId) }
            }
            prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
        }
    }

    suspend fun setShakeMenuGuardMs(valueMs: Long) = editor.edit {
        it[SettingsPreferenceKeys.SHAKE_MENU_GUARD_MS] = ShakeMenuRuntime.clampGuardMs(valueMs)
    }

    suspend fun setShakeMenuDisplayDelayMs(valueMs: Long) = editor.edit {
        it[SettingsPreferenceKeys.SHAKE_MENU_DISPLAY_DELAY_MS] =
            ShakeMenuRuntime.clampDisplayDelayMs(valueMs)
    }

    suspend fun setShowShakeMenuOverlay(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SHAKE_MENU_ENABLED] = enabled
    }

    suspend fun setLockScreenShakeAction(type: ShakeGestureType, action: GestureAction) = editor.edit { prefs ->        // 用户一旦编辑锁屏动作，就切到新模型：未设置的项按“无”处理，不再回退。
        if (prefs[SettingsPreferenceKeys.SHAKE_WORK_MODES] == null &&
            prefs[SettingsPreferenceKeys.SHAKE_WORK_MODE] == null
        ) {
            prefs[SettingsPreferenceKeys.SHAKE_WORK_MODES] = ShakeWorkModes.encode(ShakeWorkModes.DEFAULT)
        }
        val current = SettingsSnapshotReader.readShakeGestureSettings(prefs)
        val updated = current.lockScreenActions.toMutableMap().apply { put(type, action) }
        prefs[SettingsPreferenceKeys.SHAKE_LOCK_SCREEN_ACTIONS] = ShakeGestureCodec.encodeAllActions(updated)
    }

    suspend fun setPerAppShakeAction(packageName: String, type: ShakeGestureType, action: GestureAction) =
        editor.edit { prefs ->
            val current = SettingsSnapshotReader.readShakeGestureSettings(prefs)
            val perApp = current.perAppActions.toMutableMap()
            val appActions = perApp[packageName].orEmpty().toMutableMap().apply { put(type, action) }
            perApp[packageName] = appActions
            prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
        }

    suspend fun addPerAppShakeConfig(packageName: String) = editor.edit { prefs ->
        val current = SettingsSnapshotReader.readShakeGestureSettings(prefs)
        if (packageName in current.perAppActions) return@edit
        val perApp = current.perAppActions.toMutableMap()
        perApp[packageName] = emptyMap()
        prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
    }

    suspend fun removePerAppShakeConfig(packageName: String) = editor.edit { prefs ->
        val current = SettingsSnapshotReader.readShakeGestureSettings(prefs)
        val perApp = current.perAppActions.toMutableMap()
        perApp.remove(packageName)
        prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
    }

    suspend fun setShakeDirectionSensitivity(type: ShakeGestureType, value: Float) = editor.edit { prefs ->
        val current = SettingsSnapshotReader.readShakeGestureSettings(prefs)
        val updated = current.perDirectionSensitivity.toMutableMap().apply {
            put(type, ShakeSensitivityScale.clampUi(value))
        }
        prefs[SettingsPreferenceKeys.SHAKE_SENSITIVITY_V2_MIGRATED] = true
        prefs[SettingsPreferenceKeys.SHAKE_SENSITIVITY_V3_MIGRATED] = true
        prefs[SettingsPreferenceKeys.SHAKE_PER_DIRECTION_SENSITIVITY] = ShakeGestureCodec.encodePerDirectionSensitivity(updated)
    }

    suspend fun setIndependentAppShakeEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.INDEPENDENT_APP_SHAKE_ENABLED] = enabled }

    suspend fun setShakeGlobalSensitivity(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.SHAKE_SENSITIVITY_V2_MIGRATED] = true
        it[SettingsPreferenceKeys.SHAKE_SENSITIVITY_V3_MIGRATED] = true
        it[SettingsPreferenceKeys.SHAKE_GLOBAL_SENSITIVITY] = ShakeSensitivityScale.clampUi(value)
    }

    suspend fun setShakeIndependentSensitivityEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.SHAKE_INDEPENDENT_SENSITIVITY_ENABLED] = enabled }

    suspend fun setShakeVibrationFeedbackEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.SHAKE_VIBRATION_FEEDBACK_ENABLED] = enabled }

    suspend fun setShakeAnimationFeedbackEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.SHAKE_ANIMATION_FEEDBACK_ENABLED] = enabled }

    suspend fun setShakeAnimationColor(argb: Int) = editor.edit { it[SettingsPreferenceKeys.SHAKE_ANIMATION_COLOR] = argb }

    suspend fun setShakeDisableInLandscape(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.SHAKE_DISABLE_IN_LANDSCAPE] = enabled }

    suspend fun addShakeBlacklistedApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES] = current
    }

    suspend fun removeShakeBlacklistedApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES] = current
    }

    suspend fun setFaceDownGestureEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.FACE_DOWN_GESTURE_ENABLED] = enabled }

    suspend fun setFaceDownGestureAction(action: GestureAction) = editor.edit {
        it[SettingsPreferenceKeys.FACE_DOWN_GESTURE_ACTION] = FaceDownGestureCodec.encodeAction(action)
    }

    suspend fun setFaceDownHoldDurationMs(value: Long) = editor.edit {
        it[SettingsPreferenceKeys.FACE_DOWN_HOLD_DURATION_MS] =
            FaceDownGestureSettings.clampHoldDurationMs(value)
    }

    suspend fun setFaceDownRequireProximity(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.FACE_DOWN_REQUIRE_PROXIMITY] = enabled }

    suspend fun setFaceDownCooldownMs(value: Long) = editor.edit {
        it[SettingsPreferenceKeys.FACE_DOWN_COOLDOWN_MS] =
            FaceDownGestureSettings.clampCooldownMs(value)
    }

    suspend fun setFaceDownDisableInLandscape(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.FACE_DOWN_DISABLE_IN_LANDSCAPE] = enabled }

    suspend fun setFaceDownVibrationFeedbackEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.FACE_DOWN_VIBRATION_FEEDBACK_ENABLED] = enabled }

    suspend fun setFaceDownAudioFeedbackEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.FACE_DOWN_AUDIO_FEEDBACK_ENABLED] = enabled }

    suspend fun setFaceDownAudioFeedbackVolume(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FACE_DOWN_AUDIO_FEEDBACK_VOLUME] =
            FaceDownGestureSettings.clampAudioFeedbackVolume(value)
    }

    suspend fun setBackTapEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.BACK_TAP_ENABLED] = enabled }

    suspend fun setBackTapSensitivity(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.BACK_TAP_SENSITIVITY] = value.coerceIn(1, 10)
    }

    suspend fun setBackTapRange(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.BACK_TAP_RANGE] = value.coerceIn(1, 10)
    }

    suspend fun setBackTapMode(mode: com.slideindex.app.backtap.BackTapMode) = editor.edit {
        it[SettingsPreferenceKeys.BACK_TAP_MODE] = mode.storageId
    }

    suspend fun setBackTapPauseWhileCharging(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.BACK_TAP_PAUSE_CHARGING] = enabled
    }

    suspend fun setBackTapVibrationFeedbackEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.BACK_TAP_VIBRATION_FEEDBACK_ENABLED] = enabled
    }

    suspend fun setBackTapAction(action: GestureAction) = editor.edit {
        val sanitized = action.sanitizeForSlotPicker(SlotPickerKind.OverlayTap)
        it[SettingsPreferenceKeys.BACK_TAP_ACTION_TYPE] = sanitized.type.id
        it[SettingsPreferenceKeys.BACK_TAP_ACTION_PAYLOAD] = sanitized.payload
    }
}
