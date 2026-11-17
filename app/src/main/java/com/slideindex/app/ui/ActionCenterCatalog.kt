package com.slideindex.app.ui

import androidx.annotation.StringRes
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCategory

/** 动作只在某个入口里才有意义的标记；详情页据此禁用测试并给提示。 */
enum class ActionSpecialMark(@StringRes val badgeRes: Int) {
    POINTER(R.string.action_center_badge_pointer),
    CORNER(R.string.action_center_badge_corner),
    SINGLE_TAP(R.string.action_center_badge_single_tap),
    SHAKE(R.string.action_center_badge_shake),
}

/** 详情页“测试”区怎么渲染。 */
enum class ActionTestKind {
    DIRECT,
    OPEN_LINK,
    SHELL,
    KEY,
    REMIND,
    DISABLED,
}

/** 动作中心顶部的筛选。 */
enum class ActionCenterFilter {
    ALL,
    PANEL,
    CONFIGURABLE,
}

/** 动作中心的分段；分类沿用选择器，只有“仅特定入口”是动作中心自己的。 */
enum class ActionCenterSection(@StringRes val titleRes: Int) {
    NAVIGATION(R.string.action_category_navigation),
    CONTROLS(R.string.action_category_controls),
    INTELLIGENCE(R.string.action_category_intelligence),
    PANEL(R.string.action_category_panel),
    POINTER(R.string.action_category_pointer),
    SPECIAL(R.string.action_center_section_special),
    OTHER(R.string.action_category_other),
    ;

    companion object {
        fun fromCategory(category: GestureActionCategory): ActionCenterSection = when (category) {
            GestureActionCategory.Navigation -> NAVIGATION
            GestureActionCategory.Controls -> CONTROLS
            GestureActionCategory.Intelligence -> INTELLIGENCE
            GestureActionCategory.Panel -> PANEL
            GestureActionCategory.Pointer -> POINTER
            GestureActionCategory.Other -> OTHER
        }
    }
}

/**
 * 动作中心的一格。
 *
 * 这里刻意不维护“动作清单”：从 [GestureActionType] 全量生成，新增动作类型会自动出现；
 * sample 用 [GestureAction.from] 的默认值，只用于展示与测试，不写回绑定。
 */
data class BuiltInActionEntry(
    val type: GestureActionType,
    val sample: GestureAction,
    val category: GestureActionCategory,
    val special: ActionSpecialMark?,
    val needsParams: Boolean,
    val testKind: ActionTestKind,
    @StringRes val testDisabledReasonRes: Int? = null,
) {
    val section: ActionCenterSection
        get() = when (special) {
            ActionSpecialMark.CORNER,
            ActionSpecialMark.SINGLE_TAP,
            ActionSpecialMark.SHAKE,
            -> ActionCenterSection.SPECIAL
            else -> ActionCenterSection.fromCategory(category)
        }
}

object ActionCenterCatalog {

    /** 明确不进动作中心的类型：无操作、需要目标选择器的启动动作、提醒的旧变体。 */
    private val excludedTypes: Set<GestureActionType> = buildSet {
        add(GestureActionType.NONE)
        add(GestureActionType.LAUNCH_APP)
        add(GestureActionType.LAUNCH_SHORTCUT)
        addAll(GestureAction.legacyRemindTypes)
    }

    /** 「可配置」筛选：详情页「打开配置」有独立设置页的动作（与导航入口保持一致）。 */
    private val configurableTypes: Set<GestureActionType> = setOf(
        GestureActionType.OPEN_INDEX,
        GestureActionType.FLOATING_POINTER,
        GestureActionType.FREE_WINDOW_CURRENT_APP,
        GestureActionType.SHELL_COMMAND_PANEL,
        GestureActionType.QUICK_LAUNCHER,
        GestureActionType.HONEYCOMB_LAUNCHER,
        GestureActionType.APP_RING_LAUNCHER,
        GestureActionType.HOLOGRAPHIC_LAUNCHER,
        GestureActionType.WIDGET_POPUP_OVERLAY,
        GestureActionType.QUICK_WHEEL,
        GestureActionType.RUN_MACRO,
        GestureActionType.SHAKE_MENU,
        GestureActionType.SEARCH_PANEL,
        GestureActionType.FREEZER_PANEL,
        GestureActionType.OPEN_STASH_PANEL,
        GestureActionType.OPEN_CLIPBOARD_PANEL,
        GestureActionType.CLIPBOARD_PICK,
        GestureActionType.CLIPBOARD_PASTE,
        GestureActionType.OPEN_CLIPBOARD_FLOAT,
        GestureActionType.CORNER_INNER_CANCEL,
        GestureActionType.CORNER_INNER_PIN_WHEEL,
        GestureActionType.SIMULATE_POINTER_SWIPE,
        GestureActionType.POINTER_GESTURE_RECORDER,
        GestureActionType.POINTER_REALTIME_GESTURE,
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU,
        GestureActionType.REALTIME_PINCH,
        GestureActionType.REALTIME_SCROLL,
        // 详情页内联配置、没有独立导航入口的动作。
        GestureActionType.APP_CAROUSEL_SWITCHER,
        GestureActionType.TASK_SWITCHER,
        GestureActionType.PAGE_SCROLL,
        GestureActionType.REMIND,
    )

    /** 只在特定入口里有意义的动作。 */
    private val specialMarks: Map<GestureActionType, ActionSpecialMark> = mapOf(
        GestureActionType.SIMULATE_POINTER_SWIPE to ActionSpecialMark.POINTER,
        GestureActionType.POINTER_GESTURE_RECORDER to ActionSpecialMark.POINTER,
        GestureActionType.POINTER_REALTIME_GESTURE to ActionSpecialMark.POINTER,
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU to ActionSpecialMark.POINTER,
        GestureActionType.REALTIME_PINCH to ActionSpecialMark.POINTER,
        GestureActionType.REALTIME_SCROLL to ActionSpecialMark.POINTER,
        GestureActionType.CORNER_INNER_CANCEL to ActionSpecialMark.CORNER,
        GestureActionType.CORNER_INNER_PIN_WHEEL to ActionSpecialMark.CORNER,
        GestureActionType.CLICK_PASSTHROUGH to ActionSpecialMark.SINGLE_TAP,
        GestureActionType.SHAKE_MENU to ActionSpecialMark.SHAKE,
    )

    /** 详情页需要先填参数、且能填参数的动作。 */
    private val paramTypes: Set<GestureActionType> = setOf(
        GestureActionType.OPEN_LINK,
        GestureActionType.EXECUTE_SHELL_COMMAND,
        GestureActionType.SIMULATE_KEY_EVENT,
        GestureActionType.RUN_MACRO,
        GestureActionType.QUICK_LAUNCHER,
        GestureActionType.QUICK_WHEEL,
        GestureActionType.WIDGET_POPUP_OVERLAY,
    )

    /** 只能由特定入口执行、通用执行器会直接返回 false 的动作。 */
    private val contextOnlyTypes: Set<GestureActionType> = setOf(
        GestureActionType.FINGERTIP_RING,
        GestureActionType.REGIONAL_SCREENSHOT_PICK,
    )

    val entries: List<BuiltInActionEntry> = GestureActionType.entries
        .asSequence()
        .filterNot { it in excludedTypes }
        .map(::buildEntry)
        .toList()

    private val byType: Map<GestureActionType, BuiltInActionEntry> =
        entries.associateBy { it.type }

    fun entryOf(type: GestureActionType): BuiltInActionEntry? = byType[type]

    fun matches(entry: BuiltInActionEntry, filter: ActionCenterFilter): Boolean = when (filter) {
        ActionCenterFilter.ALL -> true
        ActionCenterFilter.PANEL -> entry.section == ActionCenterSection.PANEL ||
            entry.section == ActionCenterSection.POINTER ||
            entry.section == ActionCenterSection.SPECIAL
        ActionCenterFilter.CONFIGURABLE -> entry.needsParams || entry.type in configurableTypes
    }

    private fun buildEntry(type: GestureActionType): BuiltInActionEntry {
        val sample = GestureAction.from(type, "")
        val special = specialMarks[type]
        val category = when {
            // 提醒在选择器里被归到“指针与高级”，动作中心放“其他”更直观。
            type == GestureActionType.REMIND -> GestureActionCategory.Other
            // 指针家族统一归到“指针与高级”，避免实时缩放/滚动落到“其他”。
            special == ActionSpecialMark.POINTER -> GestureActionCategory.Pointer
            else -> GestureActionCatalog.categoryOf(sample)
        }
        val testKind = when (type) {
            GestureActionType.OPEN_LINK -> ActionTestKind.OPEN_LINK
            GestureActionType.EXECUTE_SHELL_COMMAND -> ActionTestKind.SHELL
            GestureActionType.SIMULATE_KEY_EVENT -> ActionTestKind.KEY
            GestureActionType.REMIND -> ActionTestKind.REMIND
            else -> ActionTestKind.DIRECT
        }
        val disabledReason = when {
            special == ActionSpecialMark.POINTER -> R.string.action_center_test_pointer_reason
            special == ActionSpecialMark.CORNER -> R.string.action_center_test_corner_reason
            special == ActionSpecialMark.SINGLE_TAP -> R.string.action_center_test_single_tap_reason
            special == ActionSpecialMark.SHAKE -> R.string.action_center_test_shake_reason
            type == GestureActionType.RUN_MACRO -> R.string.action_center_test_macro_reason
            type in contextOnlyTypes -> R.string.action_center_test_context_reason
            else -> null
        }
        return BuiltInActionEntry(
            type = type,
            sample = sample,
            category = category,
            special = special,
            needsParams = type in paramTypes,
            testKind = if (disabledReason != null) ActionTestKind.DISABLED else testKind,
            testDisabledReasonRes = disabledReason,
        )
    }
}
