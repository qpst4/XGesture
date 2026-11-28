package com.slideindex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.GestureProfile
import com.slideindex.app.settings.extractGestureProfileSnapshot
import com.slideindex.app.settings.gestureProfileById
import com.slideindex.app.settings.gestureSnapshot
import com.slideindex.app.settings.hasStoredLandscapeTriggerHandles
import com.slideindex.app.settings.materializeGestureProfile
import com.slideindex.app.settings.profileIdOwning
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.settings.withAddedBottomTriggerHandle
import com.slideindex.app.settings.withAddedTopTriggerHandle
import com.slideindex.app.settings.withAddedTriggerHandlePair
import com.slideindex.app.settings.withLandscapeCopiedFromPortrait
import com.slideindex.app.settings.withLandscapeGesturesMigratedIfNeeded
import com.slideindex.app.settings.withRemovedTriggerHandle
import com.slideindex.app.settings.withRepairedLandscapeHandleLayoutIfOverlapping
import com.slideindex.app.settings.withUpdatedTriggerHandleEnabled
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.ui.GestureAppProfilesScreen
import com.slideindex.app.ui.TriggerCollectionScreen
import com.slideindex.app.ui.rememberAppPackageEntries
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.trigger.TriggerSettingsLandscapeSession
import com.slideindex.app.ui.viewmodel.HomeDetailSettingsViewModel

/**
 * 侧滑手势「按应用分档」（GitHub issue #26 v2）的导航条目。
 *
 * 只有三个**档管理**页：档列表 → 档内整页（= 触钮集合页）→ 绑定应用。
 * 档内更深的那一串编辑页（某侧手势 / 槽位 / 外观 / 设计 / 动作选择…）**完全复用全局那套路由**，
 * 只是那几个 key 上多带一个 `profileId`（见 `AppNavKey` 与 [TriggerEditScope]）。
 */
fun NavEntryBuilder.gestureAppProfileNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.HomeGestureAppProfiles> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        GestureAppProfilesScreen(
            enabled = gestureSettings.independentAppGestureEnabled,
            profiles = gestureSettings.gestureProfiles,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = viewModel::setIndependentAppGestureEnabled,
            onOpenProfile = { profileId ->
                ctx.navigate(AppNavKey.HomeGestureAppProfileDetail(profileId))
            },
            onCreateProfile = { rawName ->
                // 建档 = 把**当时的全局**整份复制进档（含横屏那套）；此后不跟随全局。
                val global = gestureSettings.toMinimalAppSettings()
                val name = rawName.trim().ifBlank {
                    ctx.activity.getString(
                        R.string.gesture_app_profiles_default_name,
                        global.gestureProfiles.size + 1,
                    )
                }
                val profile = GestureProfile(
                    id = GestureProfile.newId(),
                    name = name,
                    snapshot = global.gestureSnapshot(),
                )
                viewModel.upsertGestureProfile(profile)
                ctx.navigate(AppNavKey.HomeGestureAppProfileDetail(profile.id))
            },
            onRemoveProfile = viewModel::removeGestureProfile,
        )
    }

    hiltEntry<AppNavKey.HomeGestureAppProfileDetail> { key ->
        GestureAppProfileDetailRoute(ctx, key.profileId)
    }

    hiltEntry<AppNavKey.HomeGestureAppProfileBind> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val globalSettings = remember(gestureSettings) { gestureSettings.toMinimalAppSettings() }
        val profile = globalSettings.gestureProfileById(key.profileId)
        val detailKey = AppNavKey.HomeGestureAppProfileDetail(key.profileId)
        ActivityShortcutPickAppScreen(
            titleResId = R.string.gesture_app_profiles_add,
            excludePackageNames = profile?.packageNames.orEmpty(),
            // 按应用分档按包名匹配前台，无法区分分身：显示但不可选。
            cloneMode = com.slideindex.app.ui.picker.AppClonePickMode.DISABLED,
            onBack = { ctx.navigateBackTo(detailKey) },
            onSelectApp = { app ->
                val current = globalSettings.gestureProfileById(key.profileId)
                val ownerId = globalSettings.profileIdOwning(app.packageName)
                when {
                    current == null -> ctx.navigateBackTo(detailKey)
                    app.packageName in current.packageNames -> ctx.navigateBackTo(detailKey)
                    // 一个 App 只能属于一个档：已被别的档绑了就只提示，不抢不过来。
                    ownerId != null && ownerId != key.profileId -> {
                        val ownerName = globalSettings.gestureProfileById(ownerId)?.name ?: ownerId
                        ctx.deps.userMessageBus.showError(
                            ctx.activity.getString(
                                R.string.gesture_app_profiles_already_bound,
                                ownerName,
                            ),
                        )
                    }
                    else -> {
                        viewModel.updateGestureProfile(current.withPackage(app.packageName))
                        ctx.navigateBackTo(detailKey)
                    }
                }
            },
        )
    }
}

/**
 * 档内不该到达的"全局设置"页兜底：直接退回该档的触钮集合页。
 *
 * 正常路径上这些页在档内根本不可达（入口被置灰并注明「全局设置，档内不适用」），
 * 这里只是保证"万一走到了，也绝不把改动写进全局"。
 */
@Composable
internal fun GestureProfileGlobalOnlyRedirect(ctx: MainNavContext, profileId: String) {
    val detailKey = AppNavKey.HomeGestureAppProfileDetail(profileId)
    LaunchedEffect(profileId) {
        if (detailKey in ctx.backStack) {
            ctx.navigateBackTo(detailKey)
        } else {
            ctx.navigateBackTo(AppNavKey.HomeGestureAppProfiles)
        }
    }
}

/**
 * 档内整页：与全局「触钮集合」页同一批组件、同一套读写，只是作用域换成该档。
 *
 * 档被删掉（例如在管理页删了又退回）时直接回管理页，不留在空页面上。
 */
@Composable
private fun GestureAppProfileDetailRoute(ctx: MainNavContext, profileId: String) {
    val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
    val triggerScope = rememberTriggerEditScope(viewModel, profileId)
    val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
    val globalSettings = remember(gestureSettings) { gestureSettings.toMinimalAppSettings() }
    val profile = globalSettings.gestureProfileById(profileId)
    val boundEntries = rememberAppPackageEntries(profile?.packageNames.orEmpty())
    val baseScope = triggerScope.profileScope

    // 与全局集合页一样登记方向锁：离开整个触钮编辑流（含手势/系统返回）时兜底释放。
    DisposableEffect(Unit) {
        TriggerSettingsLandscapeSession.onEditorScreenEnter()
        onDispose { TriggerSettingsLandscapeSession.onEditorScreenExit(ctx.activity) }
    }

    if (baseScope == null || profile == null) {
        LaunchedEffect(profileId) { ctx.navigateBackTo(AppNavKey.HomeGestureAppProfiles) }
        return
    }

    TriggerCollectionScreen(
        // 集合页自己按当前展示方向做 forLandscapeEditing()，所以这里交原样设置（与全局集合页一致）。
        settings = triggerScope.rawSettings,
        serviceEnabled = true,
        layoutPreviewVisible = ctx.backStack.currentAppNavKey() == AppNavKey.HomeGestureAppProfileDetail(profileId),
        onLayoutPreviewEnabledChange = viewModel::setTriggerLayoutPreviewEnabled,
        profileScope = baseScope.copy(
            boundAppEntries = boundEntries,
            onOpenBindApp = { ctx.navigate(AppNavKey.HomeGestureAppProfileBind(profileId)) },
            onUnbindApp = { packageName ->
                viewModel.updateGestureProfile(profile.withoutPackage(packageName))
            },
            onRestoreFromGlobal = {
                // 「恢复为全局」= 整档按当前全局重新复制一份（含横屏那套）。这是**唯一**的恢复入口：
                // 早先还有一个"每组恢复为全局"，但它按侧整份换几何，会把同侧别的组一起冲掉、
                // 还会凭空多出/少掉触钮（触钮几何在存储里就是按侧一份数组），所以整块去掉了。
                viewModel.setGestureProfileSnapshot(profileId, globalSettings.gestureSnapshot())
            },
        ),
        onEnsureLandscapeInitialized = {
            // 档内横屏那套第一次被打开时才复制竖屏（与全局 ensureLandscapeTriggerHandlesInitialized 同规则）。
            val working = globalSettings.materializeGestureProfile(profileId)
            val updated = when {
                !working.landscapeTriggersInitialized && !working.hasStoredLandscapeTriggerHandles() ->
                    working.withLandscapeCopiedFromPortrait()
                !working.landscapeTriggersInitialized -> working.withLandscapeGesturesMigratedIfNeeded()
                    .withRepairedLandscapeHandleLayoutIfOverlapping()
                else -> working.withLandscapeGesturesMigratedIfNeeded()
            }
            if (updated != working) {
                viewModel.setGestureProfileSnapshot(
                    profileId,
                    updated.extractGestureProfileSnapshot(),
                )
            }
        },
        onCopyPortraitToLandscape = {
            // 与全局入口共用全量复制逻辑，来源是该档自己的竖屏配置。
            val working = globalSettings.materializeGestureProfile(profileId)
            val next = working.withLandscapeCopiedFromPortrait()
            viewModel.setGestureProfileSnapshot(profileId, next.extractGestureProfileSnapshot())
        },
        onBack = {
            TriggerSettingsLandscapeSession.releaseForExit(ctx.activity)
            ctx.navigateBackTo(AppNavKey.HomeGestureAppProfiles)
        },
        onOpenLeftTrigger = { handleId ->
            ctx.navigate(
                AppNavKey.HomeSideGestures(PanelSide.LEFT.toNavSide(), handleId, profileId),
            )
        },
        onOpenRightTrigger = { handleId ->
            ctx.navigate(
                AppNavKey.HomeSideGestures(PanelSide.RIGHT.toNavSide(), handleId, profileId),
            )
        },
        onOpenBottomTrigger = { handleId ->
            ctx.navigate(
                AppNavKey.HomeSideGestures(PanelSide.BOTTOM.toNavSide(), handleId, profileId),
            )
        },
        onOpenTopTrigger = { handleId ->
            ctx.navigate(
                AppNavKey.HomeSideGestures(PanelSide.TOP.toNavSide(), handleId, profileId),
            )
        },
        onAddTriggerPair = {
            viewModel.editGestureProfile(profileId, optimistic = true) {
                it.withAddedTriggerHandlePair()
            }
        },
        onAddBottomTrigger = {
            viewModel.editGestureProfile(profileId, optimistic = true) {
                it.withAddedBottomTriggerHandle()
            }
        },
        onAddTopTrigger = {
            viewModel.editGestureProfile(profileId, optimistic = true) {
                it.withAddedTopTriggerHandle()
            }
        },
        onRemoveTriggerHandle = { side, handleId ->
            viewModel.editGestureProfile(profileId, optimistic = true) {
                it.withRemovedTriggerHandle(side, handleId)
            }
        },
        onTriggerHandleEnabledChange = { side, handleId, enabled ->
            viewModel.editGestureProfile(profileId) { working ->
                if (working.triggerHandle(side, handleId) == null) {
                    working
                } else {
                    working.withUpdatedTriggerHandleEnabled(side, handleId, enabled)
                }
            }
        },
    )
}
