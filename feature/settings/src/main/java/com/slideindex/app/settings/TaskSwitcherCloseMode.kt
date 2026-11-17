package com.slideindex.app.settings

enum class TaskSwitcherCloseMode(val id: Int) {
    DISMISS_RECENTS(0),
    FORCE_STOP(1),
    BOTH(2);

    companion object {
        fun fromId(id: Int): TaskSwitcherCloseMode =
            entries.firstOrNull { it.id == id } ?: DISMISS_RECENTS
    }
}
