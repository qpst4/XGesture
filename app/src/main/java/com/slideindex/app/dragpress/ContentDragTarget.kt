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
