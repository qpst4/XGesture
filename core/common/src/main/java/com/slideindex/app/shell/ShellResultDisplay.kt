package com.slideindex.app.shell

enum class ShellResultDisplay(val storageValue: String) {
    NONE("none"),
    TOAST("toast"),
    DIALOG("dialog"),
    ;

    companion object {
        fun fromStorage(value: String?): ShellResultDisplay =
            entries.firstOrNull { it.storageValue == value } ?: DIALOG
    }
}
