package com.slideindex.app.settings

/** 应用界面语言（与系统语言解耦时可覆盖）。 */
enum class AppUiLanguage {
    SYSTEM,
    ZH,
    EN,
    ES,
    JA,
    AR,
    RU,
    ;

    fun toStorageTag(): String = when (this) {
        SYSTEM -> ""
        ZH -> "zh"
        EN -> "en"
        ES -> "es"
        JA -> "ja"
        AR -> "ar"
        RU -> "ru"
    }

    fun toLanguageTags(): String? = when (this) {
        SYSTEM -> null
        ZH -> "zh"
        EN -> "en"
        ES -> "es"
        JA -> "ja"
        AR -> "ar"
        RU -> "ru"
    }

    companion object {
        fun fromStorageTag(tag: String?): AppUiLanguage {
            val normalized = tag?.trim()?.lowercase().orEmpty()
            return when {
                normalized.isEmpty() -> SYSTEM
                normalized == "zh" || normalized == "zh-cn" || normalized == "zh-hans" -> ZH
                normalized == "en" -> EN
                normalized == "es" || normalized.startsWith("es-") -> ES
                normalized == "ja" -> JA
                normalized == "ar" -> AR
                normalized == "ru" -> RU
                else -> SYSTEM
            }
        }
    }
}
