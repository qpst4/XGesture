package com.slideindex.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
enum class ShakeActionPickTarget {
    BASIC,
    LOCK_SCREEN,
    PER_APP,
    FACE_DOWN,

    /** 「晃动菜单」编辑页里改某个方向的动作；菜单 id 走对应 [AppNavKey] 的 `menuId` 字段。 */
    MENU,
}

fun ShakeActionPickTarget.returnNavKey(
    packageName: String = "",
    menuId: String = "",
): AppNavKey = when (this) {
    ShakeActionPickTarget.BASIC -> AppNavKey.ShakeGestures
    ShakeActionPickTarget.FACE_DOWN -> AppNavKey.ShakeFaceDownSettings
    ShakeActionPickTarget.LOCK_SCREEN -> AppNavKey.ShakeLockScreenSettings
    ShakeActionPickTarget.PER_APP -> AppNavKey.ShakePerAppActions(packageName)
    ShakeActionPickTarget.MENU -> AppNavKey.ShakeMenuEdit(menuId)
}
