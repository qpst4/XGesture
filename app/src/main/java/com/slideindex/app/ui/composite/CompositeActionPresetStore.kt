package com.slideindex.app.ui.composite

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 可命名保存的组合动作（动作选择器里选中后以内联快照绑定）。 */
data class CompositeActionPreset(
    val id: String,
    val name: String,
    val actions: List<GestureAction>,
)

/**
 * 组合动作库存储。
 *
 * 用独立 SharedPreferences 存 JSON；动作本身复用 Composite 的 payload 编码，
 * 不引入新的序列化格式。
 */
object CompositeActionPresetStore {

    private const val PREFS = "composite_action_presets"
    private const val KEY = "presets"

    fun newId(): String = UUID.randomUUID().toString()

    fun load(context: Context): List<CompositeActionPreset> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name")
                    val payload = obj.optString("actions")
                    add(
                        CompositeActionPreset(
                            id = id,
                            name = name,
                            actions = GestureAction.Composite.parse(payload).actions,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsert(context: Context, preset: CompositeActionPreset) {
        val next = load(context).filterNot { it.id == preset.id } + preset
        save(context, next)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun save(context: Context, presets: List<CompositeActionPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("actions", GestureAction.Composite(preset.actions).payload),
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
