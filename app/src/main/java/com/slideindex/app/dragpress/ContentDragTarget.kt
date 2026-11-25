/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/ContentDragTarget.kt
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.dragpress

import android.content.ClipData

/**
 * 拖拽目标消费接口，解除具体窗口类型依赖。
 */
interface ContentDragTarget {
    fun beginContentDrag(
        clip: ClipData,
        x: Float,
        y: Float,
        stillCurrent: () -> Boolean,
        onReady: () -> Unit,
        onFailed: (String) -> Unit,
    ): Boolean

    fun updateContentDrag(x: Float, y: Float, release: Boolean = false)

    fun cancelContentDrag()

    fun setOnContentConvertedListener(listener: ((ClipData, android.graphics.Bitmap?) -> Unit)?) {}
}
