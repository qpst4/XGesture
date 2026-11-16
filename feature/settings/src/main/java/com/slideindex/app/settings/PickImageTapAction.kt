package com.slideindex.app.settings

/** 取词面板里「单击图片」执行的动作。 */
enum class PickImageTapAction(val id: String) {
    /** 查看大图（按"默认看图应用"打开）。 */
    VIEW("view"),

    /** 打开内置编辑器。 */
    EDIT("edit"),

    /** 保存当前图片。 */
    SAVE("save"),

    /** 分享当前图片。 */
    SHARE("share"),
    ;

    companion object {
        fun fromId(id: String?): PickImageTapAction =
            entries.firstOrNull { it.id == id } ?: VIEW
    }
}
