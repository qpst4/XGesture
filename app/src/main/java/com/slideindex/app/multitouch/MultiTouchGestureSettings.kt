package com.slideindex.app.multitouch

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.MultiTouchGestureEntry
import com.slideindex.app.gesture.multitouch.PathPoint
import com.slideindex.app.gesture.multitouch.PathStroke
import com.slideindex.app.launcher.QuickLauncherItemCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 多指手势设置：手势条目列表（类型 + 接触点数 + 动作）。
 *
 * 每个条目只声明一种类型，识别器按条目匹配，不存在“谁优先”的全局开关。
 */
data class MultiTouchGestureSettings(
    val enabled: Boolean = false,
    val entries: List<MultiTouchGestureEntry> = defaultEntries(),
) {
    companion object {
        fun defaultEntries(): List<MultiTouchGestureEntry> = listOf(
            entry("pinch3", MultiPointerGesture.PINCH, 3, GestureAction.Home),
            entry("spread3", MultiPointerGesture.SPREAD, 3, GestureAction.Screenshot),
            entry("down3", MultiPointerGesture.SWIPE_DOWN, 3, GestureAction.Back),
            entry("up3", MultiPointerGesture.SWIPE_UP, 3, GestureAction.OpenNotifications),
            entry("right3", MultiPointerGesture.SWIPE_RIGHT, 3, GestureAction.PreviousApp),
            entry("pinch4", MultiPointerGesture.PINCH, 4, GestureAction.Recents),
            entry("ccw4", MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE, 4, GestureAction.LockScreen),
        )

        private fun entry(
            id: String,
            kind: MultiPointerGesture,
            fingers: Int,
            action: GestureAction,
        ): MultiTouchGestureEntry = MultiTouchGestureEntry(
            id = "default_$id",
            kind = kind,
            fingerCount = fingers,
            action = action,
        )
    }
}

/** 可以在设置页选择的预设手势类型（路径单独走录制页）。 */
val multiTouchPresetKinds: List<MultiPointerGesture> = listOf(
    MultiPointerGesture.SWIPE_LEFT,
    MultiPointerGesture.SWIPE_RIGHT,
    MultiPointerGesture.SWIPE_UP,
    MultiPointerGesture.SWIPE_DOWN,
    MultiPointerGesture.SWIPE_UP_LEFT,
    MultiPointerGesture.SWIPE_UP_RIGHT,
    MultiPointerGesture.SWIPE_DOWN_LEFT,
    MultiPointerGesture.SWIPE_DOWN_RIGHT,
    MultiPointerGesture.PINCH,
    MultiPointerGesture.SPREAD,
    MultiPointerGesture.ROTATE_CLOCKWISE,
    MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE,
    MultiPointerGesture.TAP,
    MultiPointerGesture.DOUBLE_TAP,
    MultiPointerGesture.TRIPLE_TAP,
    MultiPointerGesture.LONG_PRESS,
    MultiPointerGesture.TOUCH,
)

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

    /**
     * 保存/更新一个条目。
     *
     * 预设类型按「类型 + 接触点数」唯一（重复没有意义）；绘制手势（PATH）允许同触点数多条，
     * 只有带 [entryId] 编辑时才按 id 覆盖，否则总是新增。
     */
    fun applyEntry(
        kind: MultiPointerGesture,
        fingerCount: Int,
        pendingStrokes: List<PathStroke>,
        action: GestureAction,
        entryId: String = "",
    ) {
        val current = _state.value
        val strokes = if (kind == MultiPointerGesture.PATH) pendingStrokes else emptyList()
        val byId = entryId.takeIf { it.isNotBlank() }
            ?.let { id -> current.entries.firstOrNull { it.id == id } }
        val existing = byId ?: if (kind == MultiPointerGesture.PATH) {
            null
        } else {
            current.entries.firstOrNull {
                it.kind == kind && it.fingerCount == fingerCount
            }
        }
        val entry = if (existing != null) {
            existing.copy(
                kind = kind,
                fingerCount = fingerCount,
                action = action,
                pathStrokes = strokes,
            )
        } else {
            MultiTouchGestureEntry(
                id = "entry_${System.currentTimeMillis()}",
                kind = kind,
                fingerCount = fingerCount,
                action = action,
                pathStrokes = strokes,
            )
        }
        val updated = if (existing != null) {
            current.entries.map { if (it.id == existing.id) entry else it }
        } else {
            current.entries + entry
        }
        val sorted = sortEntries(updated)
        persist(sorted)
        _state.value = current.copy(entries = sorted)
    }

    /** 重录路径手势：只覆盖轨迹，动作保留。 */
    fun updatePathStrokes(entryId: String, strokes: List<PathStroke>) {
        val current = _state.value
        if (current.entries.none { it.id == entryId }) return
        val updated = current.entries.map { entry ->
            if (entry.id == entryId) {
                entry.copy(pathStrokes = strokes, fingerCount = strokes.size)
            } else {
                entry
            }
        }
        val sorted = sortEntries(updated)
        persist(sorted)
        _state.value = current.copy(entries = sorted)
    }

    fun removeEntry(id: String) {
        val current = _state.value
        val updated = current.entries.filterNot { it.id == id }
        if (updated.size == current.entries.size) return
        val sorted = sortEntries(updated)
        persist(sorted)
        _state.value = current.copy(entries = sorted)
    }

    /** 按接触点数升序；同数量按类型固定顺序。 */
    private fun sortEntries(entries: List<MultiTouchGestureEntry>): List<MultiTouchGestureEntry> =
        entries.sortedWith(compareBy({ it.fingerCount }, { kindOrder(it.kind) }))

    private fun kindOrder(kind: MultiPointerGesture): Int = when (kind) {
        MultiPointerGesture.PINCH -> 0
        MultiPointerGesture.SPREAD -> 1
        MultiPointerGesture.SWIPE_UP -> 2
        MultiPointerGesture.SWIPE_DOWN -> 3
        MultiPointerGesture.SWIPE_LEFT -> 4
        MultiPointerGesture.SWIPE_RIGHT -> 5
        MultiPointerGesture.SWIPE_UP_LEFT -> 6
        MultiPointerGesture.SWIPE_UP_RIGHT -> 7
        MultiPointerGesture.SWIPE_DOWN_LEFT -> 8
        MultiPointerGesture.SWIPE_DOWN_RIGHT -> 9
        MultiPointerGesture.ROTATE_CLOCKWISE -> 10
        MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE -> 11
        MultiPointerGesture.TAP -> 12
        MultiPointerGesture.DOUBLE_TAP -> 13
        MultiPointerGesture.TRIPLE_TAP -> 14
        MultiPointerGesture.LONG_PRESS -> 15
        MultiPointerGesture.TOUCH -> 16
        MultiPointerGesture.PATH -> 17
    }

    private fun persist(entries: List<MultiTouchGestureEntry>) {
        prefs.edit().putString(KEY_ENTRIES, serialize(entries)).apply()
    }

    private fun read(): MultiTouchGestureSettings {
        val all = prefs.all
        val raw = all[KEY_ENTRIES] as? String
        val entries = if (!raw.isNullOrBlank()) parse(raw) else migrateLegacy(all)
        // 首次从旧版本升级时补一次默认条目（补过就标记，用户删除后不会复活）。
        val defaultsMerged = all[KEY_DEFAULTS_MERGED] == true
        val merged = if (defaultsMerged) {
            entries
        } else {
            mergeDefaults(entries).also { value ->
                if (raw.isNullOrBlank()) persist(value)
                prefs.edit().putBoolean(KEY_DEFAULTS_MERGED, true).apply()
            }
        }
        return MultiTouchGestureSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            entries = sortEntries(merged).ifEmpty { MultiTouchGestureSettings.defaultEntries() },
        )
    }

    /** 迁移后把缺失的默认条目补回来（旧版本默认就是这些绑定）。 */
    private fun mergeDefaults(
        entries: List<MultiTouchGestureEntry>,
    ): List<MultiTouchGestureEntry> {
        if (entries.isEmpty()) return MultiTouchGestureSettings.defaultEntries()
        val existing = entries.map { it.kind to it.fingerCount }.toSet()
        val defaults = MultiTouchGestureSettings.defaultEntries()
            .filter { (it.kind to it.fingerCount) !in existing }
        return entries + defaults
    }

    /** 旧版本存的是固定绑定表 + 路径列表；这里一次性转成条目。 */
    private fun migrateLegacy(all: Map<String, *>): List<MultiTouchGestureEntry> {
        val entries = ArrayList<MultiTouchGestureEntry>()
        val bindingKinds = mapOf(
            "PINCH" to MultiPointerGesture.PINCH,
            "SPREAD" to MultiPointerGesture.SPREAD,
            "SWIPE_UP" to MultiPointerGesture.SWIPE_UP,
            "SWIPE_DOWN" to MultiPointerGesture.SWIPE_DOWN,
            "SWIPE_LEFT" to MultiPointerGesture.SWIPE_LEFT,
            "SWIPE_RIGHT" to MultiPointerGesture.SWIPE_RIGHT,
            "ROTATE_CLOCKWISE" to MultiPointerGesture.ROTATE_CLOCKWISE,
            "ROTATE_COUNTER_CLOCKWISE" to MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE,
        )
        for ((key, value) in all) {
            if (!key.startsWith("action_")) continue
            val binding = key.removePrefix("action_")
            val parts = binding.split(':')
            if (parts.size != 2) continue
            val fingers = parts[0].toIntOrNull() ?: continue
            val kind = bindingKinds[parts[1]] ?: continue
            val action = decodeAction(value) ?: continue
            entries += MultiTouchGestureEntry(
                id = "migrated_$binding",
                kind = kind,
                fingerCount = fingers,
                action = action,
            )
        }
        val rawPaths = all["path_gestures"] as? String
        if (!rawPaths.isNullOrBlank()) {
            entries += parseLegacyPaths(rawPaths)
        }
        return entries
    }

    private fun serialize(entries: List<MultiTouchGestureEntry>): String {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("kind", entry.kind.name)
            obj.put("fingers", entry.fingerCount)
            obj.put("action", encodeAction(entry.action))
            if (entry.pathStrokes.isNotEmpty()) {
                obj.put("strokes", encodePathStrokes(entry.pathStrokes))
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parse(raw: String): List<MultiTouchGestureEntry> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val kind = MultiPointerGesture.fromName(obj.optString("kind")) ?: return@mapNotNull null
            val fingers = obj.optInt("fingers", 3).coerceIn(1, 5)
            val action = decodeAction(obj.optString("action").ifBlank { null })
                ?: GestureAction.None
            val strokes = decodePathStrokes(obj.optString("strokes"))
            MultiTouchGestureEntry(
                id = obj.optString("id").ifBlank { "entry_${index}" },
                kind = kind,
                fingerCount = fingers,
                action = action,
                pathStrokes = strokes,
            )
        }
    }.getOrDefault(emptyList())

    private fun parseLegacyPaths(raw: String): List<MultiTouchGestureEntry> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val strokes = decodePathStrokes(obj.optString("strokes"))
            if (strokes.size < 2) return@mapNotNull null
            MultiTouchGestureEntry(
                id = obj.optString("id").ifBlank { "path_$index" },
                kind = MultiPointerGesture.PATH,
                fingerCount = strokes.size,
                action = decodeAction(obj.optString("action").ifBlank { null })
                    ?: GestureAction.None,
                pathStrokes = strokes,
            )
        }
    }.getOrDefault(emptyList())

    /** 完整动作编码：`类型 id + 动作正文`。 */
    private fun encodeAction(action: GestureAction): String =
        "${action.type.id}\u001F${QuickLauncherItemCodec.encodeActionBody(action)}"

    private fun decodeAction(raw: Any?): GestureAction? = when (raw) {
        is String -> decodeActionString(raw)
        is Int -> gestureActionFromTypeId(raw)
        else -> null
    }

    private fun decodeActionString(raw: String): GestureAction {
        if (raw.isBlank()) return GestureAction.None
        val separator = raw.indexOf('\u001F')
        if (separator < 0) {
            return gestureActionFromTypeId(raw.toIntOrNull() ?: 0)
        }
        val type = GestureActionType.fromId(raw.substring(0, separator).toIntOrNull() ?: 0)
        return QuickLauncherItemCodec.decodeActionBody(type, raw.substring(separator + 1))
            ?: GestureAction.None
    }

    private companion object {
        const val PREFS_NAME = "multitouch_gesture_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_ENTRIES = "gesture_entries"
        const val KEY_DEFAULTS_MERGED = "defaults_merged"
    }
}

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

/** 路径草稿的导航编码：轨迹之间 `|`，点之间 `;`，坐标 `x,y`。 */
fun encodePathStrokes(strokes: List<PathStroke>): String =
    strokes.joinToString("|") { stroke ->
        stroke.points.joinToString(";") { point -> "${point.x},${point.y}" }
    }

fun decodePathStrokes(raw: String): List<PathStroke> {
    if (raw.isBlank()) return emptyList()
    return raw.split('|').mapNotNull { strokeRaw ->
        val points = strokeRaw.split(';').mapNotNull { item ->
            val separator = item.indexOf(',')
            if (separator <= 0) return@mapNotNull null
            val x = item.substring(0, separator).toFloatOrNull() ?: return@mapNotNull null
            val y = item.substring(separator + 1).toFloatOrNull() ?: return@mapNotNull null
            PathPoint(x, y)
        }
        if (points.size < 2) null else PathStroke(points)
    }
}
