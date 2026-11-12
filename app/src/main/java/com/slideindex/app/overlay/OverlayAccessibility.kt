package com.slideindex.app.overlay

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import androidx.core.view.AccessibilityDelegateCompat
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherLabels
import com.slideindex.app.shell.ShellCommand

internal data class OverlayVirtualNode(
    val description: String,
    val boundsInParent: RectF,
    val clickable: Boolean = true
)

internal data class OverlayAccessibilitySnapshot(
    val panelTitle: String?,
    val nodes: List<OverlayVirtualNode>
)

internal object OverlayTriggerAccessibility {
    fun applyTriggerVisual(view: View, side: PanelSide, triggerIndex: Int) {
        view.contentDescription = triggerDescription(view, side, triggerIndex)
        view.isFocusable = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun applyTouchCapture(view: View, side: PanelSide, triggerIndex: Int) {
        view.contentDescription = captureDescription(view, side, triggerIndex)
        view.isFocusable = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun triggerDescription(view: View, side: PanelSide, triggerIndex: Int): String {
        val template = when (side) {
            PanelSide.LEFT -> R.string.cd_edge_trigger_left
            PanelSide.RIGHT -> R.string.cd_edge_trigger_right
            PanelSide.BOTTOM -> R.string.cd_edge_trigger_bottom
            PanelSide.TOP -> R.string.cd_edge_trigger_top
        }
        return view.context.getString(template, triggerIndex + 1)
    }

    private fun captureDescription(view: View, side: PanelSide, triggerIndex: Int): String {
        val template = when (side) {
            PanelSide.LEFT -> R.string.cd_edge_touch_capture_left
            PanelSide.RIGHT -> R.string.cd_edge_touch_capture_right
            PanelSide.BOTTOM -> R.string.cd_edge_touch_capture_bottom
            PanelSide.TOP -> R.string.cd_edge_touch_capture_top
        }
        return view.context.getString(template, triggerIndex + 1)
    }
}

internal class OverlayAccessibilityDelegate(
    private val host: View,
    private val snapshotProvider: () -> OverlayAccessibilitySnapshot,
    private val onActivate: (localX: Float, localY: Float) -> Boolean
) : AccessibilityDelegateCompat() {
    private val provider = OverlayVirtualNodeProvider(host, snapshotProvider, onActivate)

    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        val snapshot = snapshotProvider()
        info.className = host.javaClass.name
        info.contentDescription = snapshot.panelTitle
            ?: host.context.getString(R.string.cd_overlay_edge_gesture_surface)
        if (snapshot.nodes.isNotEmpty()) {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }
    }

    override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProviderCompat = provider

    fun notifyStructureChanged() {
        host.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }
}

private class OverlayVirtualNodeProvider(
    private val host: View,
    private val snapshotProvider: () -> OverlayAccessibilitySnapshot,
    private val onActivate: (localX: Float, localY: Float) -> Boolean
) : AccessibilityNodeProviderCompat() {
    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfoCompat? {
        if (virtualViewId == View.NO_ID) {
            return AccessibilityNodeInfoCompat.obtain(host)
        }
        val snapshot = snapshotProvider()
        val node = snapshot.nodes.getOrNull(virtualViewId - 1) ?: return null
        val info = AccessibilityNodeInfoCompat.obtain(host, virtualViewId)
        info.className = android.widget.Button::class.java.name
        info.contentDescription = node.description
        info.isClickable = node.clickable
        info.isFocusable = node.clickable
        info.isEnabled = true
        @Suppress("DEPRECATION")
        info.setBoundsInParent(rectFrom(node.boundsInParent))
        info.setBoundsInScreen(boundsInScreen(node.boundsInParent))
        if (node.clickable) {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }
        return info
    }

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
        val node = snapshotProvider().nodes.getOrNull(virtualViewId - 1) ?: return false
        if (!node.clickable) return false
        return onActivate(node.boundsInParent.centerX(), node.boundsInParent.centerY())
    }

    private fun boundsInScreen(boundsInParent: RectF): Rect {
        val location = IntArray(2)
        host.getLocationOnScreen(location)
        return Rect(
            (location[0] + boundsInParent.left).toInt(),
            (location[1] + boundsInParent.top).toInt(),
            (location[0] + boundsInParent.right).toInt(),
            (location[1] + boundsInParent.bottom).toInt()
        )
    }

    private fun rectFrom(bounds: RectF): Rect =
        Rect(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt()
        )
}

internal object EdgeGestureOverlayAccessibilityCollector {
    fun collect(
        context: android.content.Context,
        side: PanelSide,
        panelMode: OverlayPanelMode,
        zoneLayout: GestureZoneLayout,
        indexSession: SlideAlongRailSession,
        panelGridSession: PanelGridSession,
        quickLauncherController: QuickLauncherOverlayController,
        taskSwitcherController: TaskSwitcherOverlayController,
        shellPanelController: ShellCommandPanelController,
        appsByPackage: Map<String, AppInfo>
    ): OverlayAccessibilitySnapshot {
        val panelTitle = panelTitle(context, panelMode)
        val nodes = when (panelMode) {
            OverlayPanelMode.INDEX -> collectIndexNodes(context, zoneLayout, indexSession)
            OverlayPanelMode.QUICK_LAUNCHER ->
                collectQuickLauncherNodes(context, panelGridSession, quickLauncherController, appsByPackage)
            OverlayPanelMode.TASK_SWITCHER ->
                collectTaskSwitcherNodes(context, taskSwitcherController)
            OverlayPanelMode.SHELL_COMMANDS ->
                collectShellNodes(context, shellPanelController)
            OverlayPanelMode.NONE -> emptyList()
        }
        return OverlayAccessibilitySnapshot(panelTitle = panelTitle, nodes = nodes)
    }

    private fun panelTitle(context: android.content.Context, mode: OverlayPanelMode): String? =
        when (mode) {
            OverlayPanelMode.INDEX -> context.getString(R.string.cd_overlay_panel_index)
            OverlayPanelMode.QUICK_LAUNCHER -> context.getString(R.string.cd_overlay_panel_quick_launcher)
            OverlayPanelMode.TASK_SWITCHER -> context.getString(R.string.cd_overlay_panel_task_switcher)
            OverlayPanelMode.SHELL_COMMANDS -> context.getString(R.string.cd_overlay_panel_shell_commands)
            OverlayPanelMode.NONE -> null
        }

    private fun collectIndexNodes(
        context: android.content.Context,
        zoneLayout: GestureZoneLayout,
        indexSession: SlideAlongRailSession
    ): List<OverlayVirtualNode> {
        val rail = zoneLayout.indexRailRect()
        if (rail.isEmpty) return emptyList()
        val letters = indexSession.currentRailLetters()
        if (letters.isEmpty()) return emptyList()
        val slotHeight = rail.height() / letters.size.coerceAtLeast(1)
        val letterNodes = letters.mapIndexed { index, letter ->
            val top = rail.top + slotHeight * index
            OverlayVirtualNode(
                description = context.getString(R.string.cd_overlay_index_letter, letter),
                boundsInParent = RectF(rail.left, top, rail.right, top + slotHeight)
            )
        }
        val appNodes = indexSession.gridCellBounds.map { (app, bounds) ->
            OverlayVirtualNode(
                description = context.getString(R.string.cd_overlay_launch_app, app.label),
                boundsInParent = RectF(bounds)
            )
        }
        return letterNodes + appNodes
    }

    private fun collectQuickLauncherNodes(
        context: android.content.Context,
        panelGridSession: PanelGridSession,
        quickLauncherController: QuickLauncherOverlayController,
        appsByPackage: Map<String, AppInfo>
    ): List<OverlayVirtualNode> {
        val itemNodes = panelGridSession.cellBounds.mapNotNull { (payload, bounds) ->
            val item = payload as? QuickLauncherItem ?: return@mapNotNull null
            val label = QuickLauncherLabels.resolveLabel(
                context,
                item,
                appsByPackage,
                shellCommands = quickLauncherController.quickLauncherShellCommands(),
            )
            OverlayVirtualNode(
                description = context.getString(R.string.cd_overlay_quick_launcher_item, label),
                boundsInParent = RectF(bounds)
            )
        }
        val panelRect = quickLauncherController.quickLauncherPanelRect()
        val toolbarNodes = quickLauncherController.collectToolbarAccessibilityNodes(context, panelRect)
        return itemNodes + toolbarNodes
    }

    private fun collectTaskSwitcherNodes(
        context: android.content.Context,
        taskSwitcherController: TaskSwitcherOverlayController
    ): List<OverlayVirtualNode> =
        taskSwitcherController.collectAccessibilityNodes(context)

    private fun collectShellNodes(
        context: android.content.Context,
        shellPanelController: ShellCommandPanelController
    ): List<OverlayVirtualNode> =
        shellPanelController.collectAccessibilityNodes(context)
}

internal fun EdgeGestureOverlayView.dispatchOverlayAccessibilityClick(localX: Float, localY: Float): Boolean {
    val downTime = System.currentTimeMillis()
    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, localX, localY, 0)
    val up = MotionEvent.obtain(downTime, downTime + 50L, MotionEvent.ACTION_UP, localX, localY, 0)
    val handled = handleOverlayTouch(down)
    if (handled) {
        handleOverlayTouch(up)
    }
    down.recycle()
    up.recycle()
    return handled
}

internal fun installEdgeGestureOverlayAccessibility(
    view: EdgeGestureOverlayView,
    snapshotProvider: () -> OverlayAccessibilitySnapshot
): OverlayAccessibilityDelegate {
    val delegate = OverlayAccessibilityDelegate(
        host = view,
        snapshotProvider = snapshotProvider,
        onActivate = { x, y -> view.dispatchOverlayAccessibilityClick(x, y) }
    )
    ViewCompat.setAccessibilityDelegate(view, delegate)
    return delegate
}
