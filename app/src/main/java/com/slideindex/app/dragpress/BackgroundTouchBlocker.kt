/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/BackgroundTouchBlocker.kt
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.dragpress

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 触摸屏蔽窗：在 Shizuku 打断底层应用后挂载，吞掉后续单指和多指触摸，防止拖拽过程中误点到底层应用的按钮或列表。
 */
internal class BackgroundTouchBlocker(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var current: View? = null
    private var closed = false

    fun start(stillCurrent: () -> Boolean) {
        if (closed || !stillCurrent() || current != null) return
        val view = View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnTouchListener { _, _ -> true }
        }
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            title = "SlideIndex drag touch shield"
        }
        val accepted = runCatching {
            windowManager.addView(view, params)
            true
        }.getOrDefault(false)

        if (accepted) {
            current = view
        } else {
            Log.w("BackgroundTouchBlocker", "Shield attach rejected")
        }
    }

    fun stop() {
        val view = current ?: return
        current = null
        runCatching { windowManager.removeView(view) }
    }

    fun close() {
        closed = true
        stop()
    }
}
