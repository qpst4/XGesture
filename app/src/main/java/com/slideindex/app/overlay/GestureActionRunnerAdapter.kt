package com.slideindex.app.overlay

import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.overlay.panels.GestureActionRunner

/**
 * `:app` 的 [ActionExecutor] → 面板模块的 [GestureActionRunner]。
 *
 * 面板（quickwheel / fingertip 等）只表达"执行哪个动作"，真正的执行仍在 `:app`。
 */
internal fun ActionExecutor.asGestureActionRunner(): GestureActionRunner =
    GestureActionRunner { action, settings, longPressArmed, anchorRawX, anchorRawY, panelSide ->
        execute(
            action = action,
            settings = settings,
            longPressArmed = longPressArmed,
            anchorRawX = anchorRawX,
            anchorRawY = anchorRawY,
            panelSide = panelSide,
        )
    }
