package com.slideindex.app.settings

/**
 * 冰箱成员的目标态：用户希望这个应用「被冻结」还是「被暂停」。
 *
 * 系统只暴露当前状态（enabled / suspended），没有「上次用的是冻结还是暂停」这个概念，
 * 所以这张意图表由我们自己在每次冻结 / 暂停时记录；批量动作（含「重冻应用」手势）
 * 优先按意图还原，没有意图的成员再回落到全局 [FreezerWorkMode]。
 *
 * - [FROZEN] 停用（`pm disable`）：图标从桌面消失。
 * - [PAUSE] 挂起（`pm suspend`）：图标留在桌面但变灰。
 */
enum class FreezerAppIntent {
    FROZEN,
    PAUSE,
    ;

    companion object {
        fun fromStorageValue(value: String?): FreezerAppIntent? =
            entries.firstOrNull { it.storageValue == value }
    }

    val storageValue: String
        get() = when (this) {
            FROZEN -> "f"
            PAUSE -> "p"
        }

    val isPause: Boolean get() = this == PAUSE
}

/** 意图表的持久化编解码：一行一个「包名 + 分隔符 + 单字符档位」。 */
object FreezerAppIntentCodec {
    private const val LINE_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = '\u0001'

    fun encode(intents: Map<String, FreezerAppIntent>): String =
        intents.entries
            .filter { it.key.isNotBlank() }
            .sortedBy { it.key }
            .joinToString(LINE_SEPARATOR) { (packageName, intent) ->
                packageName + FIELD_SEPARATOR + intent.storageValue
            }

    fun decode(raw: String?): Map<String, FreezerAppIntent> {
        val value = raw?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return buildMap {
            value.split(LINE_SEPARATOR).forEach { line ->
                val separator = line.lastIndexOf(FIELD_SEPARATOR)
                if (separator <= 0) return@forEach
                val packageName = line.substring(0, separator)
                val intent = FreezerAppIntent.fromStorageValue(line.substring(separator + 1))
                    ?: return@forEach
                put(packageName, intent)
            }
        }
    }
}
