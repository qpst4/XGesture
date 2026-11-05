package com.slideindex.app.gesture

/** 侧滑手势配置等非点选槽位场景不使用。 */
enum class SlotPickerKind {
    /** 快速启动器、圆环启动器、蜂窝、音量面板扩展槽、悬浮指针边缘条等 overlay 点选槽位 */
    OverlayTap,
    /** 边角轮盘槽位 */
    CornerWheel,
    /** 摇杆功能环槽位 */
    FloatingPointerRadialSlot,
    /** 摇杆长按动作（允许 [GestureAction.OpenFloatingPointerRadialMenu]） */
    FloatingPointerRadialLongPress,
    /** 指尖环槽位 */
    FingertipRing,
    /** 边角手势内区动作 */
    CornerInnerZone,

    /**
     * 晃动方向槽位（基础 / 锁屏 / 独立应用 / 晃动菜单内部）。
     *
     * 与其它槽位的最大区别：只有这里允许 [GestureAction.OpenShakeMenu]。
     * 把「晃动菜单」绑到侧边栏 / 悬浮球没有意义（点开之后还得晃手机），所以在别的槽位一律剔除。
     */
    ShakeGesture,
}

/** 动作选择页列表策略；[EdgeGesture] 与 [Slot] 必须显式择一，避免漏接槽位过滤。 */
sealed interface ActionPickerCatalogPolicy {
    data class Slot(val kind: SlotPickerKind) : ActionPickerCatalogPolicy

    /** 侧滑手势槽位：保留 requiresContinuousTriggerOnly 等动作。 */
    data object EdgeGesture : ActionPickerCatalogPolicy
}

fun ActionPickerCatalogPolicy.slotPickerKindOrNull(): SlotPickerKind? = when (this) {
    is ActionPickerCatalogPolicy.Slot -> kind
    ActionPickerCatalogPolicy.EdgeGesture -> null
}

fun GestureAction.isEligibleForSlotPicker(kind: SlotPickerKind): Boolean {
    if (requiresContinuousTriggerOnly()) return false
    if (this is GestureAction.FloatingPointer) return false
    // 指尖环仅允许绑定侧滑触钮（ActionPickerCatalogPolicy.EdgeGesture），所有槽位点选场景禁止。
    if (this is GestureAction.FingertipRing) return false
    // 快速启动轮盘同样只允许绑定侧滑触钮，避免轮盘槽位内再嵌轮盘造成递归装配。
    if (this is GestureAction.QuickWheel) return false
    // 「晃动菜单」只属于晃动槽位：其它槽位点开它没有可执行的方向语义。
    if (this is GestureAction.OpenShakeMenu && kind != SlotPickerKind.ShakeGesture) return false

    return when (kind) {
        SlotPickerKind.OverlayTap,
        SlotPickerKind.CornerWheel,
        -> !isCornerInnerZoneOnly() &&
            this !is GestureAction.OpenFloatingPointerRadialMenu

        SlotPickerKind.FloatingPointerRadialSlot ->
            this !is GestureAction.OpenFloatingPointerRadialMenu

        SlotPickerKind.FloatingPointerRadialLongPress -> true

        SlotPickerKind.FingertipRing ->
            this !is GestureAction.OpenFloatingPointerRadialMenu

        SlotPickerKind.CornerInnerZone ->
            this !is GestureAction.OpenFloatingPointerRadialMenu &&
            this !is GestureAction.None

        // 晃动方向是「方向 → 动作」的入口，动作表本身不做额外限制；
        // 菜单内能不能再套菜单由选择页的 excludeActionTypes 控制（避免无限递归）。
        SlotPickerKind.ShakeGesture -> true
    }
}

fun GestureAction.sanitizeForSlotPicker(kind: SlotPickerKind): GestureAction =
    if (isEligibleForSlotPicker(kind)) {
        this
    } else {
        when (kind) {
            SlotPickerKind.CornerInnerZone -> GestureAction.CornerInnerCancel
            else -> GestureAction.None
        }
    }
