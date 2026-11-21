package com.slideindex.app.link

import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

data class AccessibilityCharacterLocations(
    val bounds: Array<RectF?>? = null,
    val textChanged: Boolean = false,
)

/**
 * 查询无障碍节点的逐字符屏幕像素坐标。
 */
fun readAccessibilityCharacterLocations(
    node: AccessibilityNodeInfo,
    expectedText: String,
): AccessibilityCharacterLocations {
    if (node.text?.toString() != expectedText) return AccessibilityCharacterLocations(textChanged = true)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || expectedText.length !in 1..20_000) {
        return AccessibilityCharacterLocations()
    }
    val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
    if (key !in node.availableExtraData) return AccessibilityCharacterLocations()
    val arguments = Bundle().apply {
        putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
        putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, expectedText.length)
    }
    if (!node.refreshWithExtraData(key, arguments)) return AccessibilityCharacterLocations()
    if (node.text?.toString() != expectedText) return AccessibilityCharacterLocations(textChanged = true)
    @Suppress("DEPRECATION")
    val data = node.extras.getParcelableArray(key) ?: return AccessibilityCharacterLocations()
    if (data.size != expectedText.length || data.any { it != null && it !is RectF }) {
        return AccessibilityCharacterLocations()
    }
    return AccessibilityCharacterLocations(Array(data.size) { (data[it] as? RectF)?.let(::RectF) })
}
