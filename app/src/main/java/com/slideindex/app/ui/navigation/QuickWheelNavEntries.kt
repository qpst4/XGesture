package com.slideindex.app.ui.navigation

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.overlay.asGestureActionRunner
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.settings.QuickWheelCodec
import com.slideindex.app.settings.updateSlot
import com.slideindex.app.ui.QuickWheelConfigScreen
import com.slideindex.app.ui.QuickWheelListScreen
import com.slideindex.app.ui.QuickWheelSlotEditorScreen
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.quickWheelPreviewAnchor
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder

/**
 * 「刚新建、还没真正保存」的轮盘 id（同一时刻只可能有一个）。
 *
 * 新建时它已经写进设置（配置页 / 容器编辑页都按 id 从设置里取轮盘），但只要用户没点「保存」
 * 就离开配置页，这里会把它删掉 = "没保存的新轮盘不存在"。
 * 一旦保存、或进了容器编辑子页（那边改动本来就立即写回），就清掉标记，不再回收。
 *
 * ⚠️ 必须是 Compose 状态：配置页的 `isNewWheel` 由它推导，点「保存」清掉标记后要**立刻重组**，
 * 否则"新建"永远算未保存（保存按钮点了没反馈、退出还会弹未保存确认）。
 */
private var provisionalNewWheelId by mutableStateOf<String?>(null)

/**
 * 「快捷轮盘」：列表页 → 配置页 →（功能设置编辑层）→ 容器编辑页。
 *
 * 容器编辑页是**草稿 + 底部取消/保存**，动作选择在页内复用现成的 [GestureActionPickerScreen]，
 * 因此这里只需要一个导航目的地（不再需要动作选择相关的一堆子页）。
 */
fun NavEntryBuilder.quickWheelNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.QuickWheelList> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val wheels = settings.launcher.quickWheels
        // 默认三容器（返回/主屏幕/多任务）的名称必须与「用户自己新建容器后设置同一动作」
        // 完全一致：容器编辑页选中动作时会把名称自动填成动作文案，这里用同一套文案。
        val context = LocalContext.current
        QuickWheelListScreen(
            wheels = wheels,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onCreate = {
                val wheel = QuickWheelCodec.newWheel(
                    id = UUID.randomUUID().toString(),
                    ordinal = wheels.size + 1,
                    order = wheels.size,
                    slotLabel = { action -> gestureActionLabelText(context, action) },
                )
                // 先落盘（配置页 / 容器编辑页都按 id 取它），但记成"待保存"：
                // 没保存就退出配置页时会被回收掉。
                provisionalNewWheelId = wheel.id
                viewModel.setQuickWheels(wheels + wheel)
                ctx.navigate(AppNavKey.QuickWheelConfig(wheel.id))
            },
            onOpenWheel = { wheelId ->
                // 打开已有轮盘：确保不残留"待保存"标记。
                provisionalNewWheelId = null
                ctx.navigate(AppNavKey.QuickWheelConfig(wheelId))
            },
            onUpdateWheels = { updated -> viewModel.setQuickWheels(updated) },
        )
    }

    hiltEntry<AppNavKey.QuickWheelConfig> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val wheels = settings.launcher.quickWheels
        val wheel = wheels.firstOrNull { it.id == key.wheelId }
        if (wheel == null) {
            LaunchedEffect(key.wheelId) { ctx.navigateBackTo(AppNavKey.QuickWheelList) }
        } else {
            QuickWheelConfigScreen(
                settingsWheel = wheel,
                isNewWheel = provisionalNewWheelId == wheel.id,
                onBack = {
                    // 新建后没保存就退出：把这个轮盘删掉（等于"没保存过"）。
                    if (provisionalNewWheelId == wheel.id) {
                        provisionalNewWheelId = null
                        viewModel.setQuickWheels(wheels.filterNot { it.id == wheel.id })
                    }
                    ctx.navigateBackTo(AppNavKey.QuickWheelList)
                },
                onPatch = { updated ->
                    // 任何写回（点保存 / 编辑层改容器）= 已确立，不再回收。
                    provisionalNewWheelId = null
                    viewModel.setQuickWheels(wheels.map { if (it.id == updated.id) updated else it })
                },
                onPreview = { draft -> showQuickWheelPreview(context, settings, draft, scope) },
                onOpenSlot = { path ->
                    // 容器编辑子页的改动本来就走 updateSlot 立即写回 → 视为已确立。
                    provisionalNewWheelId = null
                    ctx.navigate(AppNavKey.QuickWheelSlotEditor(wheel.id, path))
                },
            )
        }
    }

    hiltEntry<AppNavKey.QuickWheelSlotEditor> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val wheels = settings.launcher.quickWheels
        val wheel = wheels.firstOrNull { it.id == key.wheelId }
        val editorReturnKey = AppNavKey.QuickWheelConfig(key.wheelId)
        if (wheel == null) {
            LaunchedEffect(key.wheelId) { ctx.navigateBackTo(AppNavKey.QuickWheelList) }
        } else {
            QuickWheelSlotEditorScreen(
                wheel = wheel,
                path = key.path,
                appSettings = settings,
                onExit = { ctx.navigateBackTo(editorReturnKey) },
                onSave = { updated ->
                    // `index == slots.size`（末尾「+」）由 updateSlot 追加，其余就地覆盖。
                    viewModel.setQuickWheels(
                        wheels.map { item ->
                            if (item.id != wheel.id) item
                            else item.updateSlot(key.path) { updated }
                        },
                    )
                    ctx.navigateBackTo(editorReturnKey)
                },
            )
        }
    }
}

/**
 * 配置页「轮盘预览」：以草稿数据原地呼出真实轮盘。
 *
 * `ActionExecutor` 按 `VolumePanelContent` 的范式从 Overlay 依赖图构造，
 * 使预览里的单击 / 长按动作也能真实执行。
 */
private fun showQuickWheelPreview(
    context: Context,
    settings: AppSettings,
    wheel: QuickWheel,
    scope: CoroutineScope,
) {
    val dependencies = OverlayDependencyAccess.overlayDependencies(context) ?: return
    val overlayContext = OverlayDependencyAccess.overlayHostContext() ?: context
    val executor = ActionExecutor(
        context = overlayContext,
        appRepository = dependencies.appRepository,
        onShellCommandsPersist = { commands ->
            scope.launch { dependencies.settingsRepository.setShellCommands(commands) }
        },
    )
    val metrics = overlayContext.resources.displayMetrics
    val density = if (metrics.density > 0f) metrics.density else 1f
    val screenWidthPx = metrics.widthPixels.toFloat()
    val screenHeightPx = metrics.heightPixels.toFloat()
    // 与配置页"调参实时预览"完全一致：圆形按所选扇区反推理论位置（左半圆 → 右边缘…）；
    // 矩形用屏幕正中。真实手势呼出则改用触发点 + 运行时自适应。
    val previewWheel = wheel
    val (anchorX, anchorY) = quickWheelPreviewAnchor(
        wheel = previewWheel,
        density = density,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
    )
    QuickWheelOverlayWindow.showPreview(
        context = overlayContext,
        settings = settings,
        wheel = previewWheel,
        anchorRawX = anchorX,
        anchorRawY = anchorY,
        actionExecutor = executor.asGestureActionRunner(),
    )
}
