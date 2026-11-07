package com.slideindex.app.overlay.corner

import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerSlotSubMenuConfig
import com.slideindex.app.gesture.GestureAction

fun CornerGestureSettings.slotsFor(anchor: CornerAnchor): List<GestureAction> =
    if (unifiedSlots) {
        leftSlots
    } else {
        when (anchor) {
            CornerAnchor.LEFT -> leftSlots
            CornerAnchor.RIGHT -> rightSlots
        }
    }

fun CornerGestureSettings.slotSubMenusFor(anchor: CornerAnchor): List<CornerSlotSubMenuConfig> =
    if (unifiedSlots) {
        leftSlotSubMenus
    } else {
        when (anchor) {
            CornerAnchor.LEFT -> leftSlotSubMenus
            CornerAnchor.RIGHT -> rightSlotSubMenus
        }
    }

fun CornerGestureSettings.slotSubMenuFor(anchor: CornerAnchor, slot: Int): CornerSlotSubMenuConfig =
    slotSubMenusFor(anchor).getOrElse(slot) { CornerSlotSubMenuConfig() }
