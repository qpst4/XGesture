package com.slideindex.app.multitouch

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.multitouch.MultiPointerBinding
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.PathGestureTemplate
import com.slideindex.app.gesture.multitouch.PathPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 多指手势设置（Shizuku 观察模式）。
 *
 * 绑定以「指数量 + 手势」为键；只存动作类型 id（系统动作无参数），恢复时映射回
 * [GestureAction] 的 data object。带参数的复杂动作（启动应用等）暂不开放给多指手势。
 */
data class MultiTouchGestureSettings(
    val enabled: Boolean = false,
    val bindings: Map<MultiPointerBinding, GestureAction> = defaultBindings(),
    val pathGestures: List<PathGestureTemplate> = emptyList(),
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

    fun addPathGesture(template: PathGestureTemplate) {
        val current = _state.value
        val updated = current.pathGestures + template
        prefs.edit().putString(KEY_PATH_GESTURES, serializePathGestures(updated)).apply()
        _state.value = current.copy(pathGestures = updated)
    }

    fun removePathGesture(id: String) {
        val current = _state.value
        val updated = current.pathGestures.filterNot { it.id == id }
        if (updated.size == current.pathGestures.size) return
        prefs.edit().putString(KEY_PATH_GESTURES, serializePathGestures(updated)).apply()
        _state.value = current.copy(pathGestures = updated)
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
            pathGestures = parsePathGestures(prefs.getString(KEY_PATH_GESTURES, null)),
        )
    }

    private fun serializePathGestures(templates: List<PathGestureTemplate>): String {
        val array = JSONArray()
        for (template in templates) {
            val obj = JSONObject()
            obj.put("id", template.id)
            obj.put("name", template.name)
            obj.put("fingers", template.fingerCount)
            obj.put("action", template.action.toMultiTouchTypeId())
            obj.put(
                "points",
                template.points.joinToString(";") { point -> "${point.x},${point.y}" },
            )
            array.put(obj)
        }
        return array.toString()
    }

    private fun parsePathGestures(raw: String?): List<PathGestureTemplate> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id").ifBlank { return@mapNotNull null }
                val name = obj.optString("name").ifBlank { id }
                val fingers = obj.optInt("fingers", 3).coerceIn(3, 5)
                val action = gestureActionFromTypeId(obj.optInt("action", 0))
                val points = obj.optString("points").split(';').mapNotNull { item ->
                    val separator = item.indexOf(',')
                    if (separator <= 0) return@mapNotNull null
                    val x = item.substring(0, separator).toFloatOrNull() ?: return@mapNotNull null
                    val y = item.substring(separator + 1).toFloatOrNull() ?: return@mapNotNull null
                    PathPoint(x, y)
                }
                if (points.size < 2) {
                    null
                } else {
                    PathGestureTemplate(id, name, fingers, points, action)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun actionKey(binding: MultiPointerBinding): String = "action_${binding.storageKey}"

    private companion object {
        const val PREFS_NAME = "multitouch_gesture_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_PATH_GESTURES = "path_gestures"
    }
}
