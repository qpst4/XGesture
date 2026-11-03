package com.slideindex.app.multitouch

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.multitouch.MultiPointerBinding
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 多指手势设置（Shizuku 观察模式）。
 *
 * 绑定以「指数量 + 手势」为键；只存动作类型 id（系统动作无参数），恢复时映射回
 * [GestureAction] 的 data object。带参数的复杂动作（启动应用等）暂不开放给多指手势。
 */
data class MultiTouchGestureSettings(
    val enabled: Boolean = false,
    val bindings: Map<MultiPointerBinding, GestureAction> = defaultBindings(),
) {
    companion object {
        fun defaultBindings(): Map<MultiPointerBinding, GestureAction> = linkedMapOf(
            // 默认映射：三指捏合回主屏、三指张开截图、三指下滑返回、右滑上一个应用。
            MultiPointerBinding(3, MultiPointerGesture.PINCH) to GestureAction.Home,
            MultiPointerBinding(3, MultiPointerGesture.SPREAD) to GestureAction.Screenshot,
            MultiPointerBinding(3, MultiPointerGesture.SWIPE_DOWN) to GestureAction.Back,
            MultiPointerBinding(3, MultiPointerGesture.SWIPE_UP) to GestureAction.OpenNotifications,
            MultiPointerBinding(3, MultiPointerGesture.SWIPE_LEFT) to GestureAction.None,
            MultiPointerBinding(3, MultiPointerGesture.SWIPE_RIGHT) to GestureAction.PreviousApp,
            // 四指：捏合最近任务、逆时针旋转关屏（向左转动旋钮）。
            MultiPointerBinding(4, MultiPointerGesture.PINCH) to GestureAction.Recents,
            MultiPointerBinding(4, MultiPointerGesture.SPREAD) to GestureAction.None,
            MultiPointerBinding(4, MultiPointerGesture.ROTATE_CLOCKWISE) to GestureAction.None,
            MultiPointerBinding(4, MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE) to GestureAction.LockScreen,
        )

        /** 设置页按这组键展示（4 指滑动默认不开放，避免与游戏多指冲突）。 */
        val configurableBindings: List<MultiPointerBinding> = listOf(
            MultiPointerBinding(3, MultiPointerGesture.PINCH),
            MultiPointerBinding(3, MultiPointerGesture.SPREAD),
            MultiPointerBinding(3, MultiPointerGesture.SWIPE_UP),
            MultiPointerBinding(3, MultiPointerGesture.SWIPE_DOWN),
            MultiPointerBinding(3, MultiPointerGesture.SWIPE_LEFT),
            MultiPointerBinding(3, MultiPointerGesture.SWIPE_RIGHT),
            MultiPointerBinding(4, MultiPointerGesture.PINCH),
            MultiPointerBinding(4, MultiPointerGesture.SPREAD),
            MultiPointerBinding(4, MultiPointerGesture.ROTATE_CLOCKWISE),
            MultiPointerBinding(4, MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE),
        )
    }
}

/** 可以在多指手势里选择的动作（系统动作，无需额外参数）。 */
val multiTouchSelectableActions: List<GestureAction> = listOf(
    GestureAction.None,
    GestureAction.Back,
    GestureAction.Home,
    GestureAction.Recents,
    GestureAction.PreviousApp,
    GestureAction.Screenshot,
    GestureAction.LockScreen,
    GestureAction.OpenNotifications,
    GestureAction.OpenQuickSettings,
)

fun gestureActionFromTypeId(id: Int): GestureAction = when (GestureActionType.fromId(id)) {
    GestureActionType.BACK -> GestureAction.Back
    GestureActionType.HOME -> GestureAction.Home
    GestureActionType.RECENTS -> GestureAction.Recents
    GestureActionType.PREVIOUS_APP -> GestureAction.PreviousApp
    GestureActionType.SCREENSHOT -> GestureAction.Screenshot
    GestureActionType.LOCK_SCREEN -> GestureAction.LockScreen
    GestureActionType.OPEN_NOTIFICATIONS -> GestureAction.OpenNotifications
    GestureActionType.OPEN_QUICK_SETTINGS -> GestureAction.OpenQuickSettings
    else -> GestureAction.None
}

fun GestureAction.toMultiTouchTypeId(): Int = type.id

/**
 * SharedPreferences 版存储（不接入主设置仓库：多指手势属于实验功能，独立开关/独立备份更安全）。
 */
@Singleton
class MultiTouchGestureSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<MultiTouchGestureSettings> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (_state.value.enabled == enabled) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setBinding(binding: MultiPointerBinding, action: GestureAction) {
        val current = _state.value
        if (current.bindings[binding] == action) return
        prefs.edit().putInt(actionKey(binding), action.toMultiTouchTypeId()).apply()
        _state.value = current.copy(bindings = current.bindings + (binding to action))
    }

    private fun read(): MultiTouchGestureSettings {
        val defaults = MultiTouchGestureSettings.defaultBindings()
        val bindings = MultiTouchGestureSettings.configurableBindings.associateWith { binding ->
            val stored = prefs.getInt(actionKey(binding), Int.MIN_VALUE)
            if (stored == Int.MIN_VALUE) {
                defaults[binding] ?: GestureAction.None
            } else {
                gestureActionFromTypeId(stored)
            }
        }
        return MultiTouchGestureSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            bindings = bindings,
        )
    }

    private fun actionKey(binding: MultiPointerBinding): String = "action_${binding.storageKey}"

    private companion object {
        const val PREFS_NAME = "multitouch_gesture_settings"
        const val KEY_ENABLED = "enabled"
    }
}
