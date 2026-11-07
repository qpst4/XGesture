package com.slideindex.app.overlay

/**
 * QC `m81.s` touch session phase — separates tap, drag, and ring-slot paths so inject
 * coordinates never mix with finger-on-ring handling.
 */
enum class FloatingPointerTouchPhase {
    /** QC s=1: down, not yet moved beyond click slop — UP may inject at pointer. */
    PendingTapOrDrag,
    /** QC s=2: finger moved beyond slop — joystick / pointer drag. */
    Dragging,
    /** QC s=5: finger on an always-visible radial slot while down. */
    AlwaysVisibleRingSlot,
    /** Single tap on always-visible slot (consumes until UP). */
    AlwaysVisibleSlotTap,
    /** Tap outside idle radial menu to dismiss it. */
    RadialDismissOnly,
    /** Long-press radial menu highlight / selection. */
    RadialMenu,
}

fun FloatingPointerTouchPhase.isDragging(): Boolean =
    this == FloatingPointerTouchPhase.Dragging ||
        this == FloatingPointerTouchPhase.AlwaysVisibleRingSlot

fun FloatingPointerTouchPhase.allowsCenterTapOnUp(): Boolean =
    this == FloatingPointerTouchPhase.PendingTapOrDrag
