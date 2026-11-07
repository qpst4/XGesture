package com.slideindex.app.overlay.holographic

import android.graphics.drawable.Drawable

/**
 * 球面上的一个应用。
 *
 * [icon] 是 `var`：球面先按缓存里已有的图标铺点，缺图的由
 * [HolographicLauncherIconLoader] 在后台补齐后原地写回（列表本身不变，位置也就不会跳）。
 */
data class HolographicLauncherApp(
    val packageName: String,
    val label: String,
    var icon: Drawable?,
    /** 应用分身所属 user（0 = 主实例）。 */
    val cloneUserId: Int = 0,
)
