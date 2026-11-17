package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker
import com.slideindex.app.launcher.QuickLauncherItemCodec

enum class ShakeGestureType(val id: Int) {
    LEFT_FLIP(0),
    RIGHT_FLIP(1),
    FORWARD_FLIP(2),
    BACKWARD_FLIP(3),
    LEFT_FLICK(4),
    RIGHT_FLICK(5),
    ;

    companion object {
        fun fromId(id: Int): ShakeGestureType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 晃动在哪种锁屏/屏幕状态下生效。
 *
 * 可多选，见 [ShakeWorkModes]。[legacyId] 是旧版单选模型写入 `shake_work_mode` 的值，
 * 只用于升级迁移，不要再用它持久化。
 */
enum class ShakeWorkMode(val legacyId: Int, internal val bit: Int) {
    UNLOCKED(0, 1),
    LOCKED_SCREEN_ON(3, 2),
    SCREEN_OFF(1, 4),
}

/** [ShakeWorkMode] 的集合编解码与旧版单选值迁移。 */
object ShakeWorkModes {
    val DEFAULT: Set<ShakeWorkMode> = setOf(ShakeWorkMode.UNLOCKED)
    val ALL: Set<ShakeWorkMode> = ShakeWorkMode.entries.toSet()

    fun encode(modes: Set<ShakeWorkMode>): Int =
        modes.fold(0) { mask, mode -> mask or mode.bit }

    /** 空值回落到 [DEFAULT]，避免出现“一项都没勾、晃动永远不响应”的死配置。 */
    fun decode(mask: Int): Set<ShakeWorkMode> {
        val decoded = ShakeWorkMode.entries.filterTo(mutableSetOf()) { mask and it.bit != 0 }
        return decoded.ifEmpty { DEFAULT }
    }

    /** 旧版 `shake_work_mode`：0=仅解锁，1=仅熄屏，2=始终，3=仅锁屏亮屏；未知值按仅解锁。 */
    fun fromLegacyId(id: Int): Set<ShakeWorkMode> = when (id) {
        1 -> setOf(ShakeWorkMode.SCREEN_OFF)
        2 -> ALL
        3 -> setOf(ShakeWorkMode.LOCKED_SCREEN_ON)
        else -> DEFAULT
    }

    /** 日志用：按固定顺序拼出已勾选的状态。 */
    fun describe(modes: Set<ShakeWorkMode>): String =
        ShakeWorkMode.entries.filter { it in modes }.joinToString("+") { it.name }
}

/** 当前屏幕状态是否落在勾选的生效范围内。 */
fun Set<ShakeWorkMode>.allows(screenOn: Boolean, keyguardLocked: Boolean): Boolean = when {
    !screenOn -> ShakeWorkMode.SCREEN_OFF in this
    keyguardLocked -> ShakeWorkMode.LOCKED_SCREEN_ON in this
    else -> ShakeWorkMode.UNLOCKED in this
}

val Set<ShakeWorkMode>.includesScreenOff: Boolean
    get() = ShakeWorkMode.SCREEN_OFF in this

data class ShakeGestureSettings(
    val enabled: Boolean = false,
    /** 勾选的生效状态，至少一项。 */
    val workModes: Set<ShakeWorkMode> = ShakeWorkModes.DEFAULT,
    val basicActions: Map<ShakeGestureType, GestureAction> = defaultBasicActions(),
    /** 锁屏或熄屏时生效；未设置的项视为“无”，不回退到基础动作。 */
    val lockScreenActions: Map<ShakeGestureType, GestureAction> = emptyMap(),
    val independentAppShakeEnabled: Boolean = false,
    val perAppActions: Map<String, Map<ShakeGestureType, GestureAction>> = emptyMap(),
    val globalSensitivity: Float = ShakeSensitivityScale.DEFAULT_UI,
    val independentSensitivityEnabled: Boolean = false,
    val perDirectionSensitivity: Map<ShakeGestureType, Float> = emptyMap(),
    val vibrationFeedbackEnabled: Boolean = true,
    val animationFeedbackEnabled: Boolean = false,
    val animationColorArgb: Int = 0xFF424242.toInt(),
    val disableInLandscape: Boolean = false,
    val blacklistedPackages: Set<String> = emptySet(),
    /** 用户自建的「晃动菜单」；[GestureAction.OpenShakeMenu] 通过 id 引用，空 id 取第一个。 */
    val menus: List<ShakeMenu> = emptyList(),
    /** 菜单激活后的入场冷却（毫秒）：这段时间内的方向晃动一律忽略，防止打开菜单的那一下被当成执行。 */
    val menuGuardMs: Long = ShakeMenuRuntime.DEFAULT_GUARD_MS,
    /** 菜单浮层的延迟显示（毫秒）；0 = 立即显示。 */
    val menuDisplayDelayMs: Long = ShakeMenuRuntime.DEFAULT_DISPLAY_DELAY_MS,
    /** 是否显示菜单浮层；关闭后菜单仍然生效，只是不给视觉提示（盲操作）。 */
    val showMenuOverlay: Boolean = true,
) {
    fun actionFor(type: ShakeGestureType): GestureAction =
        basicActions[type] ?: GestureAction.None

    /** 按 id 找菜单；没有菜单时返回 `null`。 */
    fun menuFor(id: String): ShakeMenu? = ShakeMenuRuntime.resolveMenu(menus, id)

    fun sensitivityFor(type: ShakeGestureType): Float =
        if (independentSensitivityEnabled) {
            perDirectionSensitivity[type] ?: globalSensitivity
        } else {
            globalSensitivity
        }

    companion object {
        fun defaultBasicActions(): Map<ShakeGestureType, GestureAction> = mapOf(
            ShakeGestureType.LEFT_FLIP to GestureAction.Back,
            ShakeGestureType.RIGHT_FLIP to GestureAction.Recents,
            ShakeGestureType.FORWARD_FLIP to GestureAction.None,
            ShakeGestureType.BACKWARD_FLIP to GestureAction.None,
            ShakeGestureType.LEFT_FLICK to GestureAction.None,
            ShakeGestureType.RIGHT_FLICK to GestureAction.None,
        )
    }
}

object ShakeGestureCodec {
    private const val SEP = "\u001E"
    private const val APP_SEP = "\u001F"

    fun encodeAction(type: ShakeGestureType, action: GestureAction): String =
        "${type.id}$SEP${QuickLauncherItemCodec.encodeActionPayload(sanitizeAction(action))}"

    fun decodeAction(raw: String): Pair<ShakeGestureType, GestureAction>? {
        val index = raw.indexOf(SEP)
        if (index <= 0) return null
        val type = ShakeGestureType.fromId(raw.substring(0, index).toIntOrNull() ?: return null)
            ?: return null
        val action = QuickLauncherItemCodec.parseActionPayload(raw.substring(index + 1))
            ?.let(::sanitizeAction)
            ?: return null
        return type to action
    }

    fun encodeAllActions(actions: Map<ShakeGestureType, GestureAction>): Set<String> =
        actions.map { (type, action) -> encodeAction(type, sanitizeAction(action)) }.toSet()

    private fun sanitizeAction(action: GestureAction): GestureAction =
        action.sanitizeForSlotPicker(SlotPickerKind.ShakeGesture)

    /** 严格解析：不在 [raw] 里的方向保持缺失（视为“无”），不补基础动作。 */
    fun decodeActions(raw: Set<String>): Map<ShakeGestureType, GestureAction> =
        raw.mapNotNull { decodeAction(it) }.toMap()

    fun decodeAllActions(raw: Set<String>): Map<ShakeGestureType, GestureAction> =
        ShakeGestureSettings.defaultBasicActions() + decodeActions(raw)

    fun encodePerAppActions(perApp: Map<String, Map<ShakeGestureType, GestureAction>>): Set<String> =
        perApp.map { (packageName, actions) ->
            "$packageName$APP_SEP${encodeAllActions(actions).joinToString("\u001D")}"
        }.toSet()

    fun decodePerAppActions(raw: Set<String>): Map<String, Map<ShakeGestureType, GestureAction>> =
        raw.mapNotNull { entry ->
            val index = entry.indexOf(APP_SEP)
            if (index <= 0) return@mapNotNull null
            val packageName = entry.substring(0, index)
            val actionsRaw = entry.substring(index + 1)
            if (actionsRaw.isEmpty()) return@mapNotNull packageName to emptyMap()
            val actions = actionsRaw.split("\u001D").mapNotNull { decodeAction(it) }.toMap()
            packageName to actions
        }.toMap()

    fun encodePerDirectionSensitivity(values: Map<ShakeGestureType, Float>): Set<String> =
        values.map { (type, value) -> "${type.id}$SEP$value" }.toSet()

    fun decodePerDirectionSensitivity(raw: Set<String>): Map<ShakeGestureType, Float> =
        raw.mapNotNull { entry ->
            val index = entry.indexOf(SEP)
            if (index <= 0) return@mapNotNull null
            val type = ShakeGestureType.fromId(entry.substring(0, index).toIntOrNull() ?: return@mapNotNull null)
                ?: return@mapNotNull null
            val value = entry.substring(index + 1).toFloatOrNull() ?: return@mapNotNull null
            type to ShakeSensitivityScale.clampUi(value)
        }.toMap()
}
