package com.slideindex.app.physicalkey

import android.view.KeyEvent

/**
 * 无障碍服务与物理按键过滤逻辑的桥接器。
 */
object PhysicalKeyFilterBridge {
    @Volatile
    var keyEventFilter: ((KeyEvent) -> Boolean)? = null

    fun shouldIntercept(event: KeyEvent): Boolean {
        return keyEventFilter?.invoke(event) ?: false
    }
}
