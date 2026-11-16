package com.slideindex.app.settings

/**
 * 摇杆「松手时做什么」的三种模式。
 *
 * 以前是两个独立开关（`releaseClickAndDismiss` + `releaseClickKeepJoystick`），同时打开时语义
 * 不明确；现在收成一个三选一，UI 用分段控件。
 */
enum class FloatingPointerReleaseClickMode(val id: String) {
    /** 松手什么都不做（原有默认）。 */
    OFF("off"),

    /** 松手在指针处点一下，并关闭摇杆（原有「松手单击」）。 */
    CLICK_AND_DISMISS("click_and_dismiss"),

    /** 松手在指针处点一下，但保留摇杆以便继续拖（新增）。 */
    CLICK_KEEP("click_keep"),
    ;

    companion object {
        fun fromId(id: String?): FloatingPointerReleaseClickMode? =
            entries.firstOrNull { it.id == id }

        /** 旧数据迁移：两个旧开关 → 一个模式。保留优先。 */
        fun fromLegacyBooleans(dismiss: Boolean, keep: Boolean): FloatingPointerReleaseClickMode =
            when {
                keep -> CLICK_KEEP
                dismiss -> CLICK_AND_DISMISS
                else -> OFF
            }
    }
}
