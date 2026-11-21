package com.slideindex.app.dragpress

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.service.ScreenshotCaptureOutcome
import com.slideindex.app.service.SlideIndexAccessibilityService
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 屏幕长按内容捕获器：安全判定源应用合法性，并通过无障碍节点树提取文本或图片区域。
 */
internal class PressContentCapture(
    private val service: AccessibilityService,
) {
    fun excludedPackage(packageName: String): Boolean {
        if (packageName == service.packageName || packageName == "com.android.systemui") return true
        val keyboard = runCatching {
            android.content.ComponentName.unflattenFromString(
                Settings.Secure.getString(service.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            )?.packageName
        }.getOrNull()
        val launcher = runCatching {
            service.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        }.getOrNull()
        return packageName == keyboard || packageName == launcher
    }

    @Suppress("DEPRECATION")
    fun sourcePackageAt(x: Float, y: Float): String? {
        val windows = service.windows ?: return null
        try {
            for (window in windows) {
                val bounds = Rect().also(window::getBoundsInScreen)
                if (!bounds.contains(x.toInt(), y.toInt())) continue
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return null
                if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                    val overlayRoot = window.root
                    try {
                        if (overlayRoot?.packageName?.toString() == service.packageName) return null
                    } finally {
                        overlayRoot?.recycle()
                    }
                }
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isActive) continue
                val root = window.root ?: continue
                try {
                    if (!root.isVisibleToUser) return null
                    return root.packageName?.toString() ?: continue
                } finally {
                    root.recycle()
                }
            }
            return null
        } finally {
            windows.forEach { it.recycle() }
        }
    }

    @Suppress("DEPRECATION")
    fun isSourceAppCurrent(packageName: String): Boolean {
        val windows = service.windows ?: return true
        try {
            val applications = windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
                .sortedByDescending { it.isActive }
            for (window in applications) {
                val root = window.root ?: continue
                try {
                    val current = root.packageName?.toString() ?: continue
                    return current == packageName
                } finally {
                    root.recycle()
                }
            }
            return true
        } finally {
            windows.forEach { it.recycle() }
        }
    }

    suspend fun capture(x: Float, y: Float): ClipData? = withContext(Dispatchers.IO) {
        val root = service.rootInActiveWindow ?: return@withContext null
        try {
            val targetNode = findSmallestNodeAt(root, x.toInt(), y.toInt()) ?: root
            val text = extractText(targetNode)
            if (!text.isNullOrBlank()) {
                return@withContext ClipData.newPlainText("drag_text", text)
            }

            // 无文本时（如长按图片控件）：裁切当前控件区域并生成图片 URI
            val bounds = Rect()
            targetNode.getBoundsInScreen(bounds)
            if (bounds.width() >= 16 && bounds.height() >= 16) {
                val screenBitmap = captureScreen()
                if (screenBitmap != null) {
                    try {
                        val cropLeft = bounds.left.coerceIn(0, screenBitmap.width - 1)
                        val cropTop = bounds.top.coerceIn(0, screenBitmap.height - 1)
                        val cropRight = bounds.right.coerceIn(cropLeft + 1, screenBitmap.width)
                        val cropBottom = bounds.bottom.coerceIn(cropTop + 1, screenBitmap.height)
                        val cropWidth = cropRight - cropLeft
                        val cropHeight = cropBottom - cropTop
                        if (cropWidth >= 16 && cropHeight >= 16) {
                            val cropped = Bitmap.createBitmap(screenBitmap, cropLeft, cropTop, cropWidth, cropHeight)
                            val uri = com.slideindex.app.overlay.screenshot.ScreenshotStorage.createShareUri(service, cropped)
                            if (uri != null) {
                                return@withContext ClipData.newUri(service.contentResolver, "drag_image", uri)
                            }
                        }
                    } finally {
                        screenBitmap.recycle()
                    }
                }
            }
            null
        } finally {
            root.recycle()
        }
    }

    private suspend fun captureScreen(): Bitmap? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val initiated = runCatching {
            SlideIndexAccessibilityService.captureDisplayBitmap { outcome ->
                if (cont.isActive) {
                    val bitmap = if (outcome is ScreenshotCaptureOutcome.Success) outcome.bitmap else null
                    cont.resume(bitmap)
                }
            }
        }.getOrDefault(false)
        if (!initiated && cont.isActive) {
            cont.resume(null)
        }
    }

    @Suppress("DEPRECATION")
    fun longClickText(event: AccessibilityEvent, x: Float, y: Float, origin: String): String? {
        if (event.isPassword) return null
        val node = event.source ?: return null
        try {
            val bounds = Rect().also(node::getBoundsInScreen)
            if (node.isPassword || node.packageName?.toString() != origin || !bounds.contains(x.toInt(), y.toInt())) {
                return null
            }
            return node.text?.toString()?.takeIf(String::isNotBlank)
                ?: event.text.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.joinToString("\n").takeIf(String::isNotBlank)
        } finally {
            node.recycle()
        }
    }

    private fun findSmallestNodeAt(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) return null

        var smallest: AccessibilityNodeInfo? = null
        var minArea = Int.MAX_VALUE

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSmallestNodeAt(child, x, y)
            if (found != null) {
                val foundBounds = Rect()
                found.getBoundsInScreen(foundBounds)
                val area = foundBounds.width() * foundBounds.height()
                if (area in 1 until minArea) {
                    smallest?.recycle()
                    smallest = found
                    minArea = area
                } else if (found !== child) {
                    found.recycle()
                }
            }
            if (found !== child) {
                child.recycle()
            }
        }
        return smallest ?: AccessibilityNodeInfo.obtain(node)
    }

    private fun extractText(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) return text

        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank()) return desc

        val sb = StringBuilder()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = extractText(child)
            child.recycle()
            if (!childText.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(childText)
            }
        }
        return sb.toString().takeIf(String::isNotBlank)
    }
}
