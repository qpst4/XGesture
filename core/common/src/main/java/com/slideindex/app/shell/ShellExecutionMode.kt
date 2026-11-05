package com.slideindex.app.shell

enum class ShellExecutionMode(val storageValue: String) {
    ROOT("root"),
    SHIZUKU("shizuku"),
    ;

    companion object {
        fun fromStorage(value: String?): ShellExecutionMode? =
            entries.firstOrNull { it.storageValue == value }
    }
}
