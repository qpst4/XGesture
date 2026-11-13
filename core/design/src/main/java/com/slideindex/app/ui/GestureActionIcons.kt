package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.RingerMode
import com.slideindex.app.overlay.PanelSide

fun panelSideSwipeIcon(side: PanelSide, outlined: Boolean = false): ImageVector =
    if (outlined) panelSideSwipeOutlinedIcon(side) else when (side) {
        PanelSide.LEFT -> Icons.Default.SwipeLeft
        PanelSide.RIGHT -> Icons.Default.SwipeRight
        PanelSide.TOP -> Icons.Default.SwipeUp
        PanelSide.BOTTOM -> Icons.Default.SwipeDown
    }

fun gestureActionImageVector(action: GestureAction, outlined: Boolean = false): ImageVector =
    when (action) {
        is GestureAction.LaunchApp -> ThinActionIcons.Apps
        is GestureAction.LaunchShortcut -> ThinActionIcons.Shortcut
        is GestureAction.SimulatePointerSwipe ->
            if (outlined) {
                pointerSwipeDirectionOutlinedIcon(action.config.direction)
            } else {
                pointerSwipeDirectionThinIcon(action.config.direction)
            }
        is GestureAction.ExecuteShellCommand -> ThinActionIcons.Code
        is GestureAction.OpenLink -> Icons.Outlined.Link
        is GestureAction.SimulateKeyEvent -> ThinActionIcons.Keyboard
        // 声音模式按配置的目标档位换图标：响铃=铃铛、振动=铃铛+波、静音=铃铛+斜杠。
        is GestureAction.ToggleMute -> when (action.ringerMode) {
            RingerMode.RING -> MaterialSymbolThinIcons.Notifications
            RingerMode.VIBRATE -> MaterialSymbolThinIcons.RingerVibrate
            RingerMode.SILENT -> MaterialSymbolThinIcons.NotificationsOff
            RingerMode.TOGGLE -> MaterialSymbolThinIcons.Notifications
        }
        else ->
            if (outlined) {
                gestureActionTypeOutlinedIcon(action.type)
            } else {
                gestureActionTypeIcon(action.type)
            }
    }

fun pointerSwipeDirectionThinIcon(
    direction: com.slideindex.app.gesture.PointerSwipeDirection
): ImageVector = pointerSwipeDirectionOutlinedIcon(direction)

@Composable
fun gestureActionIcon(action: GestureAction, outlined: Boolean = false): ImageVector =
    gestureActionImageVector(action, outlined)

@Suppress("DEPRECATION")
fun gestureActionTypeIcon(type: GestureActionType): ImageVector = gestureActionTypeThinIcon(type)
