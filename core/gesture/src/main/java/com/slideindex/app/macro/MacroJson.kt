package com.slideindex.app.macro

import org.json.JSONArray
import org.json.JSONObject

/**
 * 宏列表的 JSON 编解码。
 *
 * 存储形态：SharedPreferences 里的**一个字符串**，内容是 JSON 数组（键名 [PREF_KEY]）。
 * 键名与字段名都刻意保持稳定，便于和其它同类工具的宏数据互相导入导出。
 *
 * 解析策略是「尽量读出来、坏数据不致命」：
 * - 整串解析失败 → 空列表（不抛异常，避免一条坏数据让整个列表消失）
 * - 单条宏缺 `id` / `targetPackage` → 只丢这一条
 * - 不认识的步骤类型 → 只跳过这一步
 * - 解析阶段**不做数值裁剪**，越界值原样带出，执行期才收进 [MacroLimits]
 */
object MacroJson {

    /** SharedPreferences 键名（值是一个 JSON 数组字符串）。 */
    const val PREF_KEY = "macros_v2_json"

    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_TARGET_PACKAGE = "targetPackage"
    private const val KEY_SCOPE = "scope"
    private const val KEY_CREATED_AT = "createdAtMs"
    private const val KEY_UPDATED_AT = "updatedAtMs"
    private const val KEY_STEPS = "steps"
    private const val KEY_TYPE = "type"
    private const val KEY_DURATION_MS = "durationMs"
    private const val KEY_START_X = "startX"
    private const val KEY_START_Y = "startY"
    private const val KEY_END_X = "endX"
    private const val KEY_END_Y = "endY"
    private const val KEY_POINTS = "points"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"

    private const val TYPE_DELAY = "delay"
    private const val TYPE_TAP = "tap"
    private const val TYPE_SWIPE = "swipe"

    /** 早期版本用过 `holdMs` 表示点击时长，读的时候兼容一下。 */
    private const val KEY_TAP_DURATION_LEGACY = "holdMs"

    private const val DEFAULT_X = 0.5
    private const val DEFAULT_Y = 0.5
    private const val DEFAULT_START_Y = 0.8
    private const val DEFAULT_END_X = 0.5
    private const val DEFAULT_END_Y = 0.2

    /** 解析整份宏列表；任何异常都退化成空列表。 */
    fun decode(raw: String?): List<Macro> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(text)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    decodeMacro(item)?.let(::add)
                }
            }
        }.getOrElse { emptyList() }
    }

    /** 序列化整份宏列表（压缩输出，键序固定）。 */
    fun encode(macros: List<Macro>): String {
        val array = JSONArray()
        macros.forEach { macro ->
            val steps = JSONArray()
            macro.steps.forEach { step -> steps.put(encodeStep(step)) }
            array.put(
                JSONObject()
                    .put(KEY_SCHEMA_VERSION, macro.schemaVersion)
                    .put(KEY_ID, macro.id)
                    .put(KEY_NAME, macro.name)
                    .put(KEY_TARGET_PACKAGE, macro.targetPackage)
                    .put(KEY_SCOPE, macro.scope.name)
                    .put(KEY_CREATED_AT, macro.createdAtMs)
                    .put(KEY_UPDATED_AT, macro.updatedAtMs)
                    .put(KEY_STEPS, steps),
            )
        }
        return array.toString()
    }

    private fun decodeMacro(item: JSONObject): Macro? {
        val id = item.optString(KEY_ID).takeIf { it.isNotBlank() } ?: return null
        // 注意：`targetPackage` **允许为空** —— 新建宏的流程是"先建一条占位宏，再去选目标 App"，
        // 所以这里不能像"外来数据校验"那样把空目标包当作坏数据丢掉，否则新建完就立刻读不到了。
        val now = System.currentTimeMillis()
        return Macro(
            id = id,
            name = item.optString(KEY_NAME).takeIf { it.isNotBlank() } ?: Macro.FALLBACK_NAME,
            targetPackage = item.optString(KEY_TARGET_PACKAGE),
            createdAtMs = item.optLong(KEY_CREATED_AT, now),
            updatedAtMs = item.optLong(KEY_UPDATED_AT, now),
            schemaVersion = item.optInt(KEY_SCHEMA_VERSION, Macro.CURRENT_SCHEMA_VERSION),
            steps = decodeSteps(item.optJSONArray(KEY_STEPS)),
            scope = MacroScope.fromName(item.optString(KEY_SCOPE).takeIf { it.isNotBlank() }),
        )
    }

    private fun decodeSteps(array: JSONArray?): List<MacroStep> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                decodeStep(item)?.let(::add)
            }
        }
    }

    private fun decodeStep(item: JSONObject): MacroStep? = when (item.optString(KEY_TYPE)) {
        TYPE_DELAY -> MacroStep.Wait(
            durationMs = item.optLong(KEY_DURATION_MS, MacroStep.DEFAULT_WAIT_MS),
        )

        TYPE_TAP -> MacroStep.Tap(
            x = item.optDouble(KEY_X, DEFAULT_X).toFloat(),
            y = item.optDouble(KEY_Y, DEFAULT_Y).toFloat(),
            durationMs = decodeTapDuration(item),
        )

        TYPE_SWIPE -> MacroStep.Swipe(
            startX = item.optDouble(KEY_START_X, DEFAULT_X).toFloat(),
            startY = item.optDouble(KEY_START_Y, DEFAULT_START_Y).toFloat(),
            endX = item.optDouble(KEY_END_X, DEFAULT_END_X).toFloat(),
            endY = item.optDouble(KEY_END_Y, DEFAULT_END_Y).toFloat(),
            durationMs = item.optLong(KEY_DURATION_MS, MacroStep.DEFAULT_SWIPE_MS),
            points = decodePoints(item.optJSONArray(KEY_POINTS)),
        )

        // 不认识的类型：跳过这一步，不影响同一宏里其它步骤
        else -> null
    }

    private fun decodeTapDuration(item: JSONObject): Long {
        if (item.has(KEY_DURATION_MS)) {
            return item.optLong(KEY_DURATION_MS, MacroStep.DEFAULT_TAP_MS)
        }
        return item.optLong(KEY_TAP_DURATION_LEGACY, MacroStep.DEFAULT_TAP_MS)
    }

    private fun decodePoints(array: JSONArray?): List<MacroStep.Swipe.PathPoint> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    MacroStep.Swipe.PathPoint(
                        x = item.optDouble(KEY_X, DEFAULT_X).toFloat(),
                        y = item.optDouble(KEY_Y, DEFAULT_Y).toFloat(),
                    ),
                )
            }
        }
    }

    private fun encodeStep(step: MacroStep): JSONObject = when (step) {
        is MacroStep.Wait -> JSONObject()
            .put(KEY_TYPE, TYPE_DELAY)
            .put(KEY_DURATION_MS, step.durationMs)

        is MacroStep.Tap -> JSONObject()
            .put(KEY_TYPE, TYPE_TAP)
            .put(KEY_X, step.x)
            .put(KEY_Y, step.y)
            .put(KEY_DURATION_MS, step.durationMs)

        is MacroStep.Swipe -> {
            val points = JSONArray()
            step.points.forEach { point ->
                points.put(JSONObject().put(KEY_X, point.x).put(KEY_Y, point.y))
            }
            JSONObject()
                .put(KEY_TYPE, TYPE_SWIPE)
                .put(KEY_START_X, step.startX)
                .put(KEY_START_Y, step.startY)
                .put(KEY_END_X, step.endX)
                .put(KEY_END_Y, step.endY)
                .put(KEY_DURATION_MS, step.durationMs)
                .put(KEY_POINTS, points)
        }
    }
}
