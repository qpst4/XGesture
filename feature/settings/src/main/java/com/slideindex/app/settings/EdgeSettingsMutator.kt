package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.util.ServiceEnabledStore
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureRuleCodec
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleCodec
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerRectanglePresetLogic
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.coerceInLimits
import com.slideindex.app.overlay.PanelSide
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeSettingsMutator @Inject constructor(
  private val editor: SettingsPreferencesEditor,
  @ApplicationContext private val context: Context,
) {
    suspend fun setServiceEnabled(enabled: Boolean): Result<Unit> =
        editor.edit { it[SettingsPreferenceKeys.SERVICE_ENABLED] = enabled }
            .also { result ->
                if (result.isSuccess) {
                    ServiceEnabledStore.write(context, enabled)
                }
            }
    suspend fun setOnboardingCompleted(completed: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.ONBOARDING_COMPLETED] = completed }
    suspend fun setLeftEdgeEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.LEFT_EDGE_ENABLED] = enabled }
    suspend fun setRightEdgeEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.RIGHT_EDGE_ENABLED] = enabled }

    /** 对齐开启但左右手势槽位 desync 时，合并并写入 DataStore（已一致则 no-op）。 */
    suspend fun persistOppositeGestureSlotRepairIfNeeded(): Result<Unit> = editor.edit { prefs ->
        val current = SettingsSnapshotReader.read(prefs, context)
        if (!current.hasOppositeGestureSlotDesync()) return@edit
        val repaired = current.withRepairedOppositeGestureSlotsIfNeeded()
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(repaired.gestureRules)
        prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE] = repaired.leftDefaultTriggerMode.id
        prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE] = repaired.rightDefaultTriggerMode.id
    }

    suspend fun setEdgeTriggerWidthDp(side: PanelSide, value: Float) = editor.edit { prefs ->
        val width = value.coerceIn(TriggerHandle.MIN_EDGE_WIDTH_DP, side.maxTriggerEdgeWidthDp())
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_EDGE_TRIGGER_WIDTH] = width
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_EDGE_TRIGGER_WIDTH] = width
            PanelSide.BOTTOM -> prefs[SettingsPreferenceKeys.BOTTOM_EDGE_TRIGGER_WIDTH] = width
            PanelSide.TOP -> prefs[SettingsPreferenceKeys.TOP_EDGE_TRIGGER_WIDTH] = width
        }
    }

    suspend fun setTriggerEdgeWidthDp(side: PanelSide, handleId: String, value: Float, landscape: Boolean = false) =
        editTriggerHandleProfile(landscape) { current ->
            if (current.triggerHandle(side, handleId) == null) return@editTriggerHandleProfile current
            current.withUpdatedTriggerHandleEdgeWidth(side, handleId, value)
        }

    suspend fun setTriggerTopFraction(side: PanelSide, value: Float) = editor.edit { prefs ->
        val top = value.coerceIn(0.05f, 0.80f)
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = top
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = top
            PanelSide.BOTTOM -> Unit
            PanelSide.TOP -> Unit
        }
    }

    suspend fun setTriggerHeightFraction(side: PanelSide, value: Float) = editor.edit { prefs ->
        val height = value.coerceIn(0.15f, 0.90f)
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] = height
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] = height
            PanelSide.BOTTOM -> Unit
            PanelSide.TOP -> Unit
        }
    }

    suspend fun setTriggerVerticalRange(
        side: PanelSide,
        handleId: String,
        topFraction: Float,
        bottomFraction: Float,
        landscape: Boolean = false,
    ) = editTriggerHandleProfile(landscape) { current ->
        val minBound = 0f
        val maxBound = 1f
        var top = topFraction.coerceIn(minBound, maxBound)
        var bottom = bottomFraction.coerceIn(minBound, maxBound)
        if (bottom < top) {
            val swap = top
            top = bottom
            bottom = swap
        }
        val height = bottom - top
        val sourceHandle = current.triggerHandle(side, handleId)
        var updated = current.withUpdatedTriggerHandle(side, handleId, top, height)
        if (side.isHorizontalEdge && sourceHandle?.alignOppositeSide != false) {
            val otherSide = side.opposite()
            if (otherSide.isHorizontalEdge && updated.triggerHandle(otherSide, handleId) != null) {
                updated = updated.withUpdatedTriggerHandle(otherSide, handleId, top, height)
            }
        }
        updated
    }

    suspend fun setTriggerHandleEnabled(
        side: PanelSide,
        handleId: String,
        enabled: Boolean,
        landscape: Boolean = false,
    ) = editTriggerHandleProfile(landscape) { current ->
        if (current.triggerHandle(side, handleId) == null) return@editTriggerHandleProfile current
        current.withUpdatedTriggerHandleEnabled(side, handleId, enabled)
    }

    suspend fun addBottomTriggerHandle(landscape: Boolean = false) =
        if (landscape) {
            editLandscapeProfile { it.withAddedBottomTriggerHandle() }
        } else {
            editTriggerHandleProfile(landscape = false, writeGestureRules = true) {
                it.withAddedBottomTriggerHandle()
            }
        }

    suspend fun addTopTriggerHandle(landscape: Boolean = false) =
        if (landscape) {
            editLandscapeProfile { it.withAddedTopTriggerHandle() }
        } else {
            editTriggerHandleProfile(landscape = false, writeGestureRules = true) {
                it.withAddedTopTriggerHandle()
            }
        }

    suspend fun addTriggerHandlePair(landscape: Boolean = false) =
        if (landscape) {
            editLandscapeProfile { it.withAddedTriggerHandlePair() }
        } else {
            editTriggerHandleProfile(landscape = false, writeGestureRules = true) {
                it.withAddedTriggerHandlePair()
            }
        }

    suspend fun removeTriggerHandle(
        side: PanelSide,
        handleId: String,
        landscape: Boolean = false,
    ) = if (landscape) {
        editLandscapeProfile { current ->
            current.withRemovedTriggerHandle(side, handleId)
        }
    } else {
        editor.edit { prefs ->
            val current = SettingsTriggerStore.readTriggerSettings(prefs)
            val updated = current.withRemovedTriggerHandle(side, handleId)
            SettingsTriggerStore.writeTriggerHandles(prefs, updated)
            prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
            val primary = updated.allTriggerHandles(side).firstOrNull()
            when (side) {
                PanelSide.LEFT -> primary?.let {
                    prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = it.topFraction
                    prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] = it.heightFraction
                }
                PanelSide.RIGHT -> primary?.let {
                    prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = it.topFraction
                    prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] = it.heightFraction
                }
                PanelSide.BOTTOM, PanelSide.TOP -> Unit
            }
        }
    }

    suspend fun ensureLandscapeTriggerHandlesInitialized() = editor.edit { prefs ->
        val current = SettingsSnapshotReader.read(prefs, context)
        val updated = when {
            !current.landscapeTriggersInitialized && !current.hasStoredLandscapeTriggerHandles() ->
                current.withLandscapeCopiedFromPortrait()
            else ->
                current.withLandscapeGesturesMigratedIfNeeded()
                    .withRepairedLandscapeHandleLayoutIfOverlapping()
        }
        if (updated != current) {
            SettingsTriggerStore.writeLandscapeSettings(prefs, updated)
        }
    }

    /**
     * 「复制竖屏设置」：把竖屏当前的触钮与手势动作合并进横屏。
     *
     * 横屏没初始化过时先做一次初始化（否则横屏运行态仍会回退竖屏，复制看起来不生效）；
     * 已配过槽位的触钮按「同侧 + 同序号」同步，横屏独有的触钮与其规则保留不动。
     */
    suspend fun copyPortraitToLandscape(): Result<Unit> = editor.edit { prefs ->
        val snapshot = SettingsTriggerStore.readTriggerSettings(prefs)
        val current = if (snapshot.landscapeTriggersInitialized) snapshot else snapshot.withLandscapeCopiedFromPortrait()
        val merged = current.withPortraitCopiedToLandscape(current)
        merged.handles.forEach { (side, handles) ->
            prefs[landscapeHandlesKey(side)] = TriggerHandleCodec.encodeAll(handles)
        }
        prefs[SettingsPreferenceKeys.GESTURE_RULES_LANDSCAPE] = GestureRuleCodec.encodeAll(merged.rules)
        prefs[SettingsPreferenceKeys.LANDSCAPE_TRIGGERS_INITIALIZED] = true
    }

    private fun landscapeHandlesKey(side: PanelSide) = when (side) {
        PanelSide.LEFT -> SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES_LANDSCAPE
        PanelSide.RIGHT -> SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES_LANDSCAPE
        PanelSide.BOTTOM -> SettingsPreferenceKeys.BOTTOM_TRIGGER_HANDLES_LANDSCAPE
        PanelSide.TOP -> SettingsPreferenceKeys.TOP_TRIGGER_HANDLES_LANDSCAPE
    }

    suspend fun setTriggerAlignOppositeSide(
        handleId: String,
        sourceSide: PanelSide,
        enabled: Boolean,
        landscape: Boolean = false,
    ) = editTriggerHandleProfile(landscape) { current ->
        var updated = current.withTriggerAlignOppositeSide(handleId, enabled)
        if (enabled && sourceSide.isHorizontalEdge) {
            val source = updated.triggerHandle(sourceSide, handleId)
            if (source != null) {
                val otherSide = sourceSide.opposite()
                if (otherSide.isHorizontalEdge && updated.triggerHandle(otherSide, handleId) != null) {
                    updated = updated.withUpdatedTriggerHandle(
                        side = otherSide,
                        handleId = handleId,
                        topFraction = source.topFraction,
                        heightFraction = source.heightFraction,
                    )
                    updated = updated.withSyncedTriggerHandle(
                        sourceSide = sourceSide,
                        handleId = handleId,
                        handle = source,
                    )
                }
            }
        }
        updated
    }

    suspend fun setTriggerAlignOppositeDesign(
        handleId: String,
        sourceSide: PanelSide,
        enabled: Boolean,
        landscape: Boolean = false,
    ) = editTriggerHandleProfile(landscape) { current ->
        var updated = current.withTriggerAlignOppositeDesign(handleId, enabled)
        if (enabled) {
            val source = updated.triggerHandle(sourceSide, handleId) ?: return@editTriggerHandleProfile updated
            updated = updated.withSyncedTriggerHandleDesignState(
                sourceSide = sourceSide,
                handleId = handleId,
                sourceHandle = source,
            )
        }
        updated
    }

    suspend fun setTriggerAlignOppositeGestures(
        handleId: String,
        sourceSide: PanelSide,
        enabled: Boolean,
        landscape: Boolean = false,
    ) = if (landscape) {
        editLandscapeProfile { current ->
            var updated = current.withTriggerAlignOppositeGestures(handleId, enabled)
            if (enabled && sourceSide.isHorizontalEdge) {
                updated = updated.withGestureSlotsMirroredFromSide(sourceSide, handleId)
            }
            updated
        }
    } else {
        editor.edit { prefs ->
            var current = SettingsTriggerStore.readTriggerSettings(prefs)
                .withTriggerAlignOppositeGestures(handleId, enabled)
            if (enabled && sourceSide.isHorizontalEdge) {
                current = current.withGestureSlotsMirroredFromSide(sourceSide, handleId)
            }
            SettingsTriggerStore.writeTriggerHandles(prefs, current)
            prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(current.gestureRules)
            if (enabled && sourceSide.isHorizontalEdge) {
                val resolved = current.defaultTriggerModeFor(sourceSide)
                current = current.withDefaultTriggerModeSynced(sourceSide, resolved, handleId)
                prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE] = current.leftDefaultTriggerMode.id
                prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE] = current.rightDefaultTriggerMode.id
            }
        }
    }

    suspend fun setTriggerHandleDesign(
        side: PanelSide,
        handleId: String,
        design: TriggerHandleDesign,
        landscape: Boolean = false,
    ) = editTriggerHandleProfile(landscape) { current ->
        val sourceHandle = current.triggerHandle(side, handleId) ?: return@editTriggerHandleProfile current
        val updatedHandle = TriggerRectanglePresetLogic.updateDesign(sourceHandle, design)
        current.withSyncedTriggerHandleDesignState(
            sourceSide = side,
            handleId = handleId,
            sourceHandle = updatedHandle,
        )
    }

    suspend fun applyTriggerDesignPreset(
        side: PanelSide,
        handleId: String,
        preset: TriggerDesignPreset,
        landscape: Boolean = false,
    ) = editTriggerHandleProfile(landscape) { current ->
        val sourceHandle = current.triggerHandle(side, handleId) ?: return@editTriggerHandleProfile current
        val updatedHandle = TriggerRectanglePresetLogic.switchPreset(sourceHandle, preset)
        current.withSyncedTriggerHandleDesignState(
            sourceSide = side,
            handleId = handleId,
            sourceHandle = updatedHandle,
        )
    }

    suspend fun setInterceptSystemBackGesture(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.INTERCEPT_SYSTEM_BACK] = enabled }

    suspend fun setSystemGestureTakeoverTop(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.SYSTEM_GESTURE_TAKEOVER_TOP] = enabled }

    suspend fun setSystemGestureTakeoverSides(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.SYSTEM_GESTURE_TAKEOVER_SIDES] = enabled }

    suspend fun setSystemGestureTakeoverBottom(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.SYSTEM_GESTURE_TAKEOVER_BOTTOM] = enabled }
    suspend fun setLimitMaxInterceptLength(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.LIMIT_MAX_INTERCEPT_LENGTH] = enabled }

    suspend fun setDefaultTriggerMode(
        side: PanelSide,
        mode: GestureTriggerMode,
        handleId: String = TriggerHandle.DEFAULT_ID,
        landscape: Boolean = false,
    ) = if (landscape) {
        editLandscapeProfile { current ->
            val resolved = if (mode == GestureTriggerMode.DEFAULT) GestureTriggerMode.ON_RELEASE else mode
            current.withDefaultTriggerModeSynced(side, resolved, handleId)
        }
    } else {
        editor.edit { prefs ->
            val resolved = if (mode == GestureTriggerMode.DEFAULT) GestureTriggerMode.ON_RELEASE else mode
            val current = SettingsTriggerStore.readTriggerSettings(prefs)
            val updated = current.withDefaultTriggerModeSynced(side, resolved, handleId)
            prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE] = updated.leftDefaultTriggerMode.id
            prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE] = updated.rightDefaultTriggerMode.id
            prefs[SettingsPreferenceKeys.BOTTOM_DEFAULT_TRIGGER_MODE] = updated.bottomDefaultTriggerMode.id
            prefs[SettingsPreferenceKeys.TOP_DEFAULT_TRIGGER_MODE] = updated.topDefaultTriggerMode.id
        }
    }

    suspend fun setShortSwipeDistanceDp(
        side: PanelSide,
        handleId: String,
        value: Float,
        landscape: Boolean = false,
    ) = editor.edit { prefs ->
        SettingsTriggerStore.updateTriggerSwipeDistances(
            prefs,
            context,
            side,
            handleId,
            shortSwipeDistanceDp = value,
            landscape = landscape,
        )
    }

    suspend fun setLongSwipeDistanceDp(
        side: PanelSide,
        handleId: String,
        value: Float,
        landscape: Boolean = false,
    ) = editor.edit { prefs ->
        SettingsTriggerStore.updateTriggerSwipeDistances(
            prefs,
            context,
            side,
            handleId,
            longSwipeDistanceDp = value,
            landscape = landscape,
        )
    }

    suspend fun setGestureHintEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.GESTURE_HINT_ENABLED] = enabled }

    suspend fun setGestureHintStyle(style: GestureHintStyle) = editor.edit {
        it[SettingsPreferenceKeys.GESTURE_HINT_STYLE] = style.id
        style.toAnimationType()?.let { type ->
            val current = AnimationStyleCodec.decode(it[SettingsPreferenceKeys.ANIMATION_STYLES])
            it[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(current.selectType(type))
        }
    }

    suspend fun setGestureHintFingerOffsetDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.GESTURE_HINT_FINGER_OFFSET_DP] = value
    }

    suspend fun setAndroidBackColorSource(source: AndroidBackColorSource) = editor.edit {
        it[SettingsPreferenceKeys.ANDROID_BACK_COLOR_SOURCE] = source.id
    }

    suspend fun setSwipeHoverDurationMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.SWIPE_HOVER_DURATION_MS] =
            value.coerceIn(
                SwipeHoverDurationLimits.MIN_MS,
                SwipeHoverDurationLimits.MAX_MS,
            )
    }

    suspend fun setTriggerDoubleTapIntervalMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.TRIGGER_DOUBLE_TAP_INTERVAL_MS] = value.coerceIn(0, 800)
    }

    suspend fun setAnimationStyles(styles: AnimationStyles) = editor.edit {
        it[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(styles)
    }

    suspend fun updateWaveStyle(style: WaveStyle) = editor.edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_WAVE, AnimationStyleCodec.encodeWave(style)),
        )
    }

    suspend fun updateCapsuleStyle(style: CapsuleStyle) = editor.edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_CAPSULE, AnimationStyleCodec.encodeCapsule(style)),
        )
    }

    suspend fun updateBubbleStyle(style: BubbleStyle) = editor.edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_BUBBLE, AnimationStyleCodec.encodeBubble(style)),
        )
    }

    suspend fun setGestureAngles(angles: GestureAngles) = editor.edit { prefs ->
        GestureAnglesCodec.writeAngles(prefs, angles)
    }

    suspend fun setIndexHeightFraction(value: Float) = editor.edit { it[SettingsPreferenceKeys.INDEX_HEIGHT] = value }
    suspend fun setHideEmptyIndexLetters(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.HIDE_EMPTY_INDEX_LETTERS] = enabled }
    suspend fun setAppsPerRow(value: Int) = editor.edit { it[SettingsPreferenceKeys.APPS_PER_ROW] = value.coerceIn(2, 5) }

    suspend fun setQuickLauncherColumnsPerPage(value: Int) =
        editor.edit { it[SettingsPreferenceKeys.QUICK_LAUNCHER_COLUMNS_PER_PAGE] = value.coerceIn(2, 6) }

    suspend fun setQuickLauncherRowsPerPage(value: Int) =
        editor.edit { it[SettingsPreferenceKeys.QUICK_LAUNCHER_ROWS_PER_PAGE] = value.coerceIn(2, 9) }

    suspend fun setPanelOpacity(value: Float) = editor.edit { it[SettingsPreferenceKeys.PANEL_OPACITY] = value }
    suspend fun setHapticEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.HAPTIC_ENABLED] = enabled }
    suspend fun setHideFromRecents(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.HIDE_FROM_RECENTS] = enabled }
    suspend fun setPredictiveBackEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.PREDICTIVE_BACK_ENABLED] = enabled }

    suspend fun setAppUiLanguage(language: AppUiLanguage) =
        editor.edit { it[SettingsPreferenceKeys.APP_UI_LANGUAGE_TAG] = language.toStorageTag() }

    suspend fun setSwipeDismissEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.SWIPE_DISMISS_ENABLED] = enabled }

    suspend fun setAccessibilityKeepAliveEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.ACCESSIBILITY_KEEP_ALIVE] = enabled }

    suspend fun setHapticStrengthLevel(level: Int) = editor.edit {
        it[SettingsPreferenceKeys.HAPTIC_STRENGTH] = level.coerceIn(
            HapticStrength.LIGHT.level,
            HapticStrength.STRONG.level,
        )
    }

    suspend fun setHapticGestureTriggerEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.HAPTIC_GESTURE_TRIGGER_ENABLED] = enabled
    }

    suspend fun setHapticSlotSelectionEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.HAPTIC_SLOT_SELECTION_ENABLED] = enabled
    }

    suspend fun setHapticConfirmLaunchEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.HAPTIC_CONFIRM_LAUNCH_ENABLED] = enabled
    }

    suspend fun addHiddenApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES] = current
    }

    suspend fun removeHiddenApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES] = current
    }

    suspend fun setFreezerAppPackages(packages: Set<String>) = editor.edit {
        it[SettingsPreferenceKeys.FREEZER_APP_PACKAGES] = packages
    }

    suspend fun addFreezerApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.FREEZER_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.FREEZER_APP_PACKAGES] = current
        val excluded = it[SettingsPreferenceKeys.FREEZER_BOOTSTRAP_EXCLUDED_PACKAGES]?.toMutableSet()
            ?: mutableSetOf()
        excluded.remove(packageName)
        it[SettingsPreferenceKeys.FREEZER_BOOTSTRAP_EXCLUDED_PACKAGES] = excluded
    }

    suspend fun removeFreezerApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.FREEZER_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.remove(packageName)
        it[SettingsPreferenceKeys.FREEZER_APP_PACKAGES] = current
        val excluded = it[SettingsPreferenceKeys.FREEZER_BOOTSTRAP_EXCLUDED_PACKAGES]?.toMutableSet()
            ?: mutableSetOf()
        excluded.add(packageName)
        it[SettingsPreferenceKeys.FREEZER_BOOTSTRAP_EXCLUDED_PACKAGES] = excluded
    }

    suspend fun setFreezerShowInLauncher(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FREEZER_SHOW_IN_LAUNCHER] = enabled
    }

    suspend fun setFreezerWorkMode(modeId: Int) = editor.edit {
        it[SettingsPreferenceKeys.FREEZER_WORK_MODE] = modeId
    }

    suspend fun setExpandPanelSlotAction(index: Int, action: com.slideindex.app.gesture.GestureAction?) = editor.edit {
        val current = readExpandPanelSlotActions(it).toMutableList()
        if (index !in current.indices) return@edit
        current[index] = action?.takeUnless { it is com.slideindex.app.gesture.GestureAction.None }
        it[SettingsPreferenceKeys.EXPAND_PANEL_SHORTCUTS] =
            com.slideindex.app.launcher.ExpandPanelSlotCodec.encode(current)
    }

    private fun readExpandPanelSlotActions(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
    ): List<com.slideindex.app.gesture.GestureAction?> =
        com.slideindex.app.launcher.ExpandPanelSlotCodec.decode(
            prefs[SettingsPreferenceKeys.EXPAND_PANEL_SHORTCUTS],
        )

    suspend fun addPreviousAppExcludedPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.PREVIOUS_APP_EXCLUDED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.PREVIOUS_APP_EXCLUDED_PACKAGES] = current
    }

    suspend fun removePreviousAppExcludedPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.PREVIOUS_APP_EXCLUDED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.PREVIOUS_APP_EXCLUDED_PACKAGES] = current
    }

    suspend fun addExcludedTriggerApp(packageName: String) = editor.edit { prefs ->
        val defaults = readExcludedAppDefaultScopes(prefs)
        val current = ExcludedAppScopesCodec.decode(prefs[SettingsPreferenceKeys.EXCLUDED_APP_SCOPES]).toMutableMap()
        current[packageName] = defaults
        prefs[SettingsPreferenceKeys.EXCLUDED_APP_SCOPES] = ExcludedAppScopesCodec.encode(current)
        prefs[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] = current.keys
    }

    suspend fun removeExcludedTriggerApp(packageName: String) = editor.edit { prefs ->
        val current = ExcludedAppScopesCodec.decode(prefs[SettingsPreferenceKeys.EXCLUDED_APP_SCOPES]).toMutableMap()
        if (current.remove(packageName) == null) return@edit
        prefs[SettingsPreferenceKeys.EXCLUDED_APP_SCOPES] = ExcludedAppScopesCodec.encode(current)
        prefs[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] = current.keys
    }

    suspend fun setExcludedAppScopes(packageName: String, scopes: ExcludedAppScopes) = editor.edit { prefs ->
        val current = ExcludedAppScopesCodec.decode(prefs[SettingsPreferenceKeys.EXCLUDED_APP_SCOPES]).toMutableMap()
        if (!scopes.hasAny()) {
            current.remove(packageName)
        } else {
            current[packageName] = scopes
        }
        prefs[SettingsPreferenceKeys.EXCLUDED_APP_SCOPES] = ExcludedAppScopesCodec.encode(current)
        prefs[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] = current.keys
    }

    suspend fun setExcludedAppDefaultScopes(scopes: ExcludedAppScopes) = editor.edit {
        it[SettingsPreferenceKeys.EXCLUDED_APP_DEFAULT_SCOPES] = encodeDefaultScopesFlagString(scopes)
    }

    suspend fun setExcludedAppDefaultSuppressTriggers(enabled: Boolean) = editor.edit { prefs ->
        val current = readExcludedAppDefaultScopes(prefs).copy(suppressTriggers = enabled)
        prefs[SettingsPreferenceKeys.EXCLUDED_APP_DEFAULT_SCOPES] = encodeDefaultScopesFlagString(current)
    }

    suspend fun setExcludedAppDefaultSuppressCornerWheel(enabled: Boolean) = editor.edit { prefs ->
        val current = readExcludedAppDefaultScopes(prefs).copy(suppressCornerWheel = enabled)
        prefs[SettingsPreferenceKeys.EXCLUDED_APP_DEFAULT_SCOPES] = encodeDefaultScopesFlagString(current)
    }

    suspend fun setExcludedAppDefaultSuppressFloatBall(enabled: Boolean) = editor.edit { prefs ->
        val current = readExcludedAppDefaultScopes(prefs).copy(suppressFloatBall = enabled)
        prefs[SettingsPreferenceKeys.EXCLUDED_APP_DEFAULT_SCOPES] = encodeDefaultScopesFlagString(current)
    }

    private fun readExcludedAppDefaultScopes(prefs: androidx.datastore.preferences.core.MutablePreferences): ExcludedAppScopes {
        prefs[SettingsPreferenceKeys.EXCLUDED_APP_DEFAULT_SCOPES]?.let { value ->
            val flags = value.split(',')
            if (flags.size == 3) {
                return ExcludedAppScopes(
                    suppressTriggers = flags[0] == "1",
                    suppressCornerWheel = flags[1] == "1",
                    suppressFloatBall = flags[2] == "1",
                )
            }
        }
        return ExcludedAppScopes.ALL
    }

    private fun encodeDefaultScopesFlagString(scopes: ExcludedAppScopes): String =
        listOf(
            if (scopes.suppressTriggers) "1" else "0",
            if (scopes.suppressCornerWheel) "1" else "0",
            if (scopes.suppressFloatBall) "1" else "0",
        ).joinToString(",")

    suspend fun setKeyboardTriggerBehaviorPortrait(behavior: KeyboardTriggerBehavior) = editor.edit {
        it[SettingsPreferenceKeys.KEYBOARD_TRIGGER_BEHAVIOR_PORTRAIT] = behavior.storageKey
    }

    suspend fun setKeyboardTriggerBehaviorLandscape(behavior: KeyboardTriggerBehavior) = editor.edit {
        it[SettingsPreferenceKeys.KEYBOARD_TRIGGER_BEHAVIOR_LANDSCAPE] = behavior.storageKey
    }

    suspend fun setKeyboardTriggerNarrowPercentPortrait(percent: Int) = editor.edit {
        it[SettingsPreferenceKeys.KEYBOARD_TRIGGER_NARROW_PERCENT_PORTRAIT] =
            KeyboardTriggerNarrowPercents.coerce(percent)
    }

    suspend fun setKeyboardTriggerNarrowPercentLandscape(percent: Int) = editor.edit {
        it[SettingsPreferenceKeys.KEYBOARD_TRIGGER_NARROW_PERCENT_LANDSCAPE] =
            KeyboardTriggerNarrowPercents.coerce(percent)
    }

    suspend fun setHideTriggerInLandscape(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LANDSCAPE] = enabled }

    suspend fun setHideTriggerOnLockScreen(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LOCK_SCREEN] = enabled }

    suspend fun setHideTriggerOnLauncher(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LAUNCHER] = enabled }

    suspend fun upsertGestureRule(rule: GestureRule) = editor.edit { prefs ->
        val current = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet())
            .filterNot { it.id == rule.id }
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(current + rule)
    }

    suspend fun removeGestureRule(id: String) = editor.edit { prefs ->
        val current = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet())
            .filterNot { it.id == id }
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(current)
    }

    suspend fun setSlotAction(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        landscape: Boolean = false,
    ) = if (landscape) {
        editLandscapeProfile { it.withSlotActionSynced(side, trigger, action) }
    } else {
        editor.edit { prefs ->
            val current = SettingsTriggerStore.readTriggerSettings(prefs)
            val updated = current.withSlotActionSynced(side, trigger, action)
            prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
        }
    }

    suspend fun setSlotTriggerMode(
        side: PanelSide,
        trigger: GestureTriggerType,
        triggerMode: GestureTriggerMode,
        landscape: Boolean = false,
    ) = if (landscape) {
        editLandscapeProfile { it.withSlotTriggerModeSynced(side, trigger, triggerMode) }
    } else {
        editor.edit { prefs ->
            val current = SettingsTriggerStore.readTriggerSettings(prefs)
            val updated = current.withSlotTriggerModeSynced(side, trigger, triggerMode)
            prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
        }
    }

    suspend fun setSlotConfig(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        triggerMode: GestureTriggerMode,
        handleId: String = TriggerHandle.DEFAULT_ID,
        landscape: Boolean = false,
    ) = if (landscape) {
        editLandscapeProfile {
            it.withSlotConfigSynced(side, trigger, action, triggerMode, handleId)
        }
    } else {
        editor.edit { prefs ->
            val current = SettingsTriggerStore.readTriggerSettings(prefs)
            val updated = current.withSlotConfigSynced(side, trigger, action, triggerMode, handleId)
            prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
        }
    }

    private suspend fun editLandscapeProfile(
        block: (AppSettings) -> AppSettings,
    ): Result<Unit> = editor.edit { prefs ->
        val snapshot = SettingsSnapshotReader.read(prefs, context)
        val working = snapshot.forLandscapeEditing()
        val updated = block(working)
        SettingsTriggerStore.writeLandscapeSettings(prefs, snapshot.mergeLandscapeEdits(updated))
    }

    /**
     * 编辑触钮布局。[writeGestureRules] 用于"同时改了手势规则"的入口（新增触钮会顺便给新触钮
     * 落一份出厂动作、补齐对侧时会镜像手势）：这些入口必须把 GESTURE_RULES 一并写回，
     * 否则规则只进内存、落盘丢失（横屏走 [editLandscapeProfile]，规则由
     * [SettingsTriggerStore.writeLandscapeSettings] 一并写入）。
     */
    private suspend fun editTriggerHandleProfile(
        landscape: Boolean,
        writeGestureRules: Boolean = false,
        block: (AppSettings) -> AppSettings,
    ): Result<Unit> = editor.edit { prefs ->
        val snapshot = SettingsSnapshotReader.read(prefs, context)
        val working = if (landscape) snapshot.forLandscapeHandleEditing() else snapshot
        val updated = block(working)
        if (landscape) {
            SettingsTriggerStore.writeLandscapeTriggerHandles(
                prefs,
                SettingsTriggerStore.mergeLandscapeFromEditing(snapshot, updated),
            )
        } else {
            SettingsTriggerStore.writeTriggerHandles(prefs, updated)
            if (writeGestureRules) {
                prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
            }
            updated.leftTriggerHandles.firstOrNull()?.let {
                prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = it.topFraction
                prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] = it.heightFraction
            }
            updated.rightTriggerHandles.firstOrNull()?.let {
                prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = it.topFraction
                prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] = it.heightFraction
            }
        }
    }
}
