package com.slideindex.app.freezer

/**
 * 冰箱成员的三种状态。
 *
 * - [ACTIVE] 正常使用中。
 * - [FROZEN] 已冻结：`pm disable` / `setApplicationEnabledSetting`，应用被停用，**桌面图标会消失**。
 * - [PAUSED] 已暂停：应用挂起（`pm suspend` / `setPackagesSuspended`），应用仍安装且 enabled，
 *   **桌面图标保留但变灰**，点击弹系统「应用已暂停」对话框。
 */
enum class FreezerAppState {
    ACTIVE,
    FROZEN,
    PAUSED;

    val isActive: Boolean get() = this == ACTIVE

    val isFrozen: Boolean get() = this == FROZEN

    val isPaused: Boolean get() = this == PAUSED

    companion object {
        /**
         * 冻结优先：同时被停用且被挂起时，桌面表现由停用决定（图标消失），按冻结呈现。
         */
        fun of(enabled: Boolean, suspended: Boolean): FreezerAppState = when {
            !enabled -> FROZEN
            suspended -> PAUSED
            else -> ACTIVE
        }
    }
}
