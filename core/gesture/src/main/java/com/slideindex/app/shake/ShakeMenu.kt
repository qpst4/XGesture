package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker
import com.slideindex.app.launcher.QuickLauncherItemCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一套「晃动菜单」。
 *
 * 一个菜单就是一张**方向 → 动作**的映射表：晃动触发菜单后，浮层按这张表提示用户
 * 再往哪个方向晃会执行什么。它不改变可用的方向数量（仍然是 [ShakeGestureType] 那 6 个），
 * 只是把同一批方向在「菜单状态」里换一整套绑定。
 *
 * @property id 稳定 id，[GestureAction.OpenShakeMenu] 通过它引用菜单。
 * @property name 显示名；空串时由 UI 回落到「菜单 N」。
 * @property actions 方向 → 动作；没有条目或 [GestureAction.None] 都视为「无」。
 */
data class ShakeMenu(
    val id: String,
    val name: String = "",
    val actions: Map<ShakeGestureType, GestureAction> = emptyMap(),
) {
    /** 已配置的方向（不含「无」），顺序固定为 [ShakeGestureType] 声明顺序。 */
    val configuredDirections: List<ShakeGestureType>
        get() = ShakeGestureType.entries.filter { type ->
            val action = actions[type]
            action != null && action != GestureAction.None
        }

    fun actionFor(type: ShakeGestureType): GestureAction =
        actions[type]?.takeIf { it != GestureAction.None } ?: GestureAction.None
}

object ShakeMenuLimits {
    /** 与「快速启动器面板」同级的上限，避免设置页无限膨胀。 */
    const val MAX_MENUS = 12

    /** 菜单数量上限内剪裁；空 id 的菜单在解析层就被丢掉，这里只保证顺序稳定。 */
    fun clamp(menus: List<ShakeMenu>): List<ShakeMenu> = menus.take(MAX_MENUS)

    /** 菜单显示名的兜底：名字为空时用序号，避免设置页出现空行。 */
    fun displayName(menu: ShakeMenu, index: Int): String =
        menu.name.ifBlank { "菜单 ${index + 1}" }
}

/**
 * 「晃动菜单」的持久化编解码。
 *
 * 存储形态：一个 JSON 数组字符串（键名见 `SettingsPreferenceKeys.SHAKE_MENUS`）。
 * 与 [com.slideindex.app.macro.MacroJson] 一样走「坏数据不致命」策略：
 * 整串解析失败 → 空列表；单条菜单缺 id → 只丢这一条；单个方向解析失败 → 只丢这一项。
 */
object ShakeMenuCodec {
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_ACTIONS = "actions"
    private const val KEY_DIRECTION = "dir"
    private const val KEY_TYPE = "type"
    private const val KEY_BODY = "body"

    fun encode(menus: List<ShakeMenu>): String {
        val array = JSONArray()
        for (menu in ShakeMenuLimits.clamp(menus)) {
            val item = JSONObject()
            item.put(KEY_ID, menu.id)
            item.put(KEY_NAME, menu.name)
            val actions = JSONArray()
            for ((type, action) in menu.actions) {
                if (action == GestureAction.None) continue
                val entry = JSONObject()
                entry.put(KEY_DIRECTION, type.id)
                entry.put(KEY_TYPE, action.type.id)
                entry.put(KEY_BODY, QuickLauncherItemCodec.encodeActionBody(action))
                actions.put(entry)
            }
            item.put(KEY_ACTIONS, actions)
            array.put(item)
        }
        return array.toString()
    }

    fun decode(raw: String?): List<ShakeMenu> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(text)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    decodeMenu(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun decodeMenu(item: JSONObject?): ShakeMenu? {
        if (item == null) return null
        val id = item.optString(KEY_ID).trim()
        if (id.isEmpty()) return null
        val name = item.optString(KEY_NAME)
        val actionsJson = item.optJSONArray(KEY_ACTIONS) ?: JSONArray()
        val actions = LinkedHashMap<ShakeGestureType, GestureAction>()
        for (index in 0 until actionsJson.length()) {
            val entry = actionsJson.optJSONObject(index) ?: continue
            val type = ShakeGestureType.fromId(entry.optInt(KEY_DIRECTION, -1)) ?: continue
            val actionType = GestureActionType.fromId(entry.optInt(KEY_TYPE, -1))
            if (actionType == GestureActionType.NONE) continue
            val body = entry.optString(KEY_BODY)
            val action = QuickLauncherItemCodec.decodeActionBody(actionType, body) ?: continue
            if (action == GestureAction.None) continue
            actions[type] = action.sanitizeForSlotPicker(SlotPickerKind.ShakeGesture)
        }
        return ShakeMenu(id = id, name = name, actions = actions)
    }
}

/**
 * 清掉指向已删除菜单的悬空引用：
 * 记的是 [removedMenuId] 就改成 [fallbackMenuId]；没有可回落的菜单则退化成「无」。
 */
fun GestureAction.remapShakeMenuReference(
    removedMenuId: String,
    fallbackMenuId: String?,
): GestureAction {
    if (this !is GestureAction.OpenShakeMenu || menuId != removedMenuId) return this
    return if (fallbackMenuId != null) {
        GestureAction.OpenShakeMenu(fallbackMenuId)
    } else {
        GestureAction.None
    }
}
