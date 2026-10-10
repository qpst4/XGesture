package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SideGestureDefaults
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.gesture.requiresContinuousTriggerOnly
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerHandlePairEntry
import com.slideindex.app.gesture.TriggerCollectionEntry
import com.slideindex.app.overlay.PanelSide
import kotlin.math.roundToInt

internal fun AppSettings.withGestureRules(rules: List<GestureRule>): AppSettings =
    copy(launcher = launcher.copy(gestureRules = rules))

fun AppSettings.rulesForSide(side: PanelSide): List<GestureRule> =
    gestureRules.filter { it.enabled && it.side == side }.sortedByDescending { it.priority }

fun AppSettings.withSlotAction(
    side: PanelSide,
    trigger: GestureTriggerType,
    action: GestureAction,
    handleId: String = TriggerHandle.DEFAULT_ID,
): AppSettings {
    val slotId = GestureRule.slotId(side, trigger, handleId)
    val existing = gestureRules.firstOrNull { it.id == slotId }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }
        } else {
            null
        }
    val others = gestureRules.filterNot { it.id == slotId || it.id == existing?.id }
    if (action.type == GestureActionType.NONE) {
        return withGestureRules(
            others + GestureRule(
                id = slotId,
                side = side,
                trigger = trigger,
                action = GestureAction.None,
                triggerMode = existing?.triggerMode ?: GestureTriggerMode.DEFAULT,
                handleId = handleId,
            ),
        )
    }
    return withGestureRules(
        others + GestureRule(
            id = slotId,
            side = side,
            trigger = trigger,
            action = action,
            triggerMode = existing?.triggerMode ?: GestureTriggerMode.DEFAULT,
            handleId = handleId,
        ),
    )
}

fun AppSettings.withSlotTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    triggerMode: GestureTriggerMode,
    handleId: String = TriggerHandle.DEFAULT_ID,
    explicitAction: GestureAction? = null,
): AppSettings {
    val slotId = GestureRule.slotId(side, trigger, handleId)
    val existing = gestureRules.firstOrNull { it.id == slotId }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }
        } else {
            null
        }
    val others = gestureRules.filterNot { it.id == slotId || it.id == existing?.id }
    val action = explicitAction ?: existing?.action ?: actionFor(side, trigger, handleId)
    if (triggerMode == GestureTriggerMode.DEFAULT &&
        action.type == GestureActionType.NONE
    ) {
        if (existing == null) {
            return withGestureRules(others)
        }
        return withGestureRules(
            others + existing.copy(
                triggerMode = GestureTriggerMode.DEFAULT,
                action = GestureAction.None,
            ),
        )
    }
    if (triggerMode == GestureTriggerMode.DEFAULT && existing != null &&
        existing.action.type != GestureActionType.NONE
    ) {
        return withGestureRules(
            others + existing.copy(triggerMode = GestureTriggerMode.DEFAULT),
        )
    }
    if (triggerMode == GestureTriggerMode.DEFAULT && existing == null) {
        return withGestureRules(others)
    }
    return withGestureRules(
        others + GestureRule(
            id = slotId,
            side = side,
            trigger = trigger,
            action = action,
            triggerMode = triggerMode,
            handleId = handleId,
        ),
    )
}

/** 本组左右触钮是否共用同一套手势槽位配置。 */
fun AppSettings.oppositeGesturesSyncedForHandle(handleId: String): Boolean {
    if (triggerHandle(PanelSide.LEFT, handleId) == null) return false
    if (triggerHandle(PanelSide.RIGHT, handleId) == null) return false
    val left = triggerHandle(PanelSide.LEFT, handleId)!!
    val right = triggerHandle(PanelSide.RIGHT, handleId)!!
    return left.alignOppositeGestures != false && right.alignOppositeGestures != false
}

/** 读取手势槽位/默认模式时使用的 side。对齐开启时左右应已同步，直接读当前 side。 */
fun AppSettings.gestureConfigSide(side: PanelSide, @Suppress("UNUSED_PARAMETER") handleId: String): PanelSide = side

/**
 * 对齐开启但左右槽位不一致时，按「自定义规则更多的一侧」镜像合并。
 * 供 [EdgeSettingsMutator.persistOppositeGestureSlotRepairIfNeeded] 写入存储。
 */
fun AppSettings.withRepairedOppositeGestureSlotsIfNeeded(): AppSettings {
    var updated = this
    for (entry in triggerCollectionEntries()) {
        val handleId = entry.handleId
        if (!oppositeGesturesSyncedForHandle(handleId)) continue
        if (!oppositeGestureSlotsDiffer(handleId)) continue
        val sourceSide = preferredOppositeGestureMirrorSource(handleId)
        updated = updated.withGestureSlotsMirroredFromSide(sourceSide, handleId)
    }
    return updated
}

/** 是否存在「对齐对侧手势」开启但左右槽位配置不一致的触钮组。 */
fun AppSettings.hasOppositeGestureSlotDesync(): Boolean {
    for (entry in triggerCollectionEntries()) {
        val handleId = entry.handleId
        if (!oppositeGesturesSyncedForHandle(handleId)) continue
        if (oppositeGestureSlotsDiffer(handleId)) return true
    }
    return false
}

private fun AppSettings.oppositeGestureSlotsDiffer(handleId: String): Boolean {
    for (trigger in sideGestureSlotTriggers()) {
        if (slotConfigForMirror(PanelSide.LEFT, trigger, handleId) !=
            slotConfigForMirror(PanelSide.RIGHT, trigger, handleId)
        ) {
            return true
        }
    }
    return defaultTriggerModeFor(PanelSide.LEFT) != defaultTriggerModeFor(PanelSide.RIGHT)
}

private fun AppSettings.preferredOppositeGestureMirrorSource(handleId: String): PanelSide {
    val leftCustom = persistedGestureSlotCount(PanelSide.LEFT, handleId)
    val rightCustom = persistedGestureSlotCount(PanelSide.RIGHT, handleId)
    return if (rightCustom > leftCustom) PanelSide.RIGHT else PanelSide.LEFT
}

private fun AppSettings.persistedGestureSlotCount(side: PanelSide, handleId: String): Int {
    val triggers = sideGestureSlotTriggers().toSet()
    return gestureRules.count { rule ->
        rule.enabled &&
            rule.side == side &&
            rule.handleId == handleId &&
            rule.trigger in triggers &&
            rule.action.type != GestureActionType.NONE
    }
}

internal fun sideGestureSlotTriggers(): List<GestureTriggerType> =
    GestureTriggerType.shortDistanceEntries() +
        GestureTriggerType.compoundGestureEntries() +
        GestureTriggerType.hoverSwipeEntries() +
        GestureTriggerType.pressTapEntries() +
        GestureTriggerType.longDistanceEntries()

private fun AppSettings.slotConfigForMirror(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String,
): Pair<GestureAction, GestureTriggerMode> {
    val action = actionFor(side, trigger, handleId)
    val mode = slotTriggerMode(side, trigger, handleId).takeIf { it != GestureTriggerMode.DEFAULT }
        ?: effectiveRule(side, trigger, handleId)?.triggerMode
        ?: GestureTriggerMode.DEFAULT
    return action to mode
}

private fun AppSettings.applySlotConfig(
    side: PanelSide,
    trigger: GestureTriggerType,
    action: GestureAction,
    triggerMode: GestureTriggerMode,
    handleId: String,
): AppSettings {
    val afterAction = withSlotAction(side, trigger, action, handleId)
    return afterAction.withSlotTriggerMode(
        side = side,
        trigger = trigger,
        triggerMode = triggerMode,
        handleId = handleId,
        explicitAction = action,
    )
}

private fun AppSettings.mirrorSlotConfigToOppositeIfAligned(
    sourceSide: PanelSide,
    handleId: String,
    trigger: GestureTriggerType,
    action: GestureAction,
    triggerMode: GestureTriggerMode,
): AppSettings {
    val sourceHandle = triggerHandle(sourceSide, handleId) ?: return this
    if (!sourceSide.isHorizontalEdge || sourceHandle.alignOppositeGestures == false) return this
    val otherSide = sourceSide.opposite()
    if (!otherSide.isHorizontalEdge || triggerHandle(otherSide, handleId) == null) return this
    return applySlotConfig(otherSide, trigger, action, triggerMode, handleId)
}

fun AppSettings.withSlotConfigSynced(
    side: PanelSide,
    trigger: GestureTriggerType,
    action: GestureAction,
    triggerMode: GestureTriggerMode,
    handleId: String = TriggerHandle.DEFAULT_ID,
): AppSettings {
    val updated = applySlotConfig(side, trigger, action, triggerMode, handleId)
    return updated.mirrorSlotConfigToOppositeIfAligned(
        sourceSide = side,
        handleId = handleId,
        trigger = trigger,
        action = action,
        triggerMode = triggerMode,
    )
}

fun AppSettings.withSlotActionSynced(
    side: PanelSide,
    trigger: GestureTriggerType,
    action: GestureAction,
    handleId: String = TriggerHandle.DEFAULT_ID,
): AppSettings {
    val updated = withSlotAction(side, trigger, action, handleId)
    val mode = updated.slotTriggerMode(side, trigger, handleId)
    return updated.mirrorSlotConfigToOppositeIfAligned(
        sourceSide = side,
        handleId = handleId,
        trigger = trigger,
        action = action,
        triggerMode = mode,
    )
}

fun AppSettings.withSlotTriggerModeSynced(
    side: PanelSide,
    trigger: GestureTriggerType,
    triggerMode: GestureTriggerMode,
    handleId: String = TriggerHandle.DEFAULT_ID,
): AppSettings {
    val updated = withSlotTriggerMode(side, trigger, triggerMode, handleId)
    val action = updated.actionFor(side, trigger, handleId)
    return updated.mirrorSlotConfigToOppositeIfAligned(
        sourceSide = side,
        handleId = handleId,
        trigger = trigger,
        action = action,
        triggerMode = triggerMode,
    )
}

fun AppSettings.withDefaultTriggerModeSynced(
    side: PanelSide,
    mode: GestureTriggerMode,
    handleId: String,
): AppSettings {
    val resolved = if (mode == GestureTriggerMode.DEFAULT) GestureTriggerMode.ON_RELEASE else mode
    var updated = when (side) {
        PanelSide.LEFT -> copy(edgeTrigger = edgeTrigger.copy(leftDefaultTriggerMode = resolved))
        PanelSide.RIGHT -> copy(edgeTrigger = edgeTrigger.copy(rightDefaultTriggerMode = resolved))
        PanelSide.BOTTOM -> copy(edgeTrigger = edgeTrigger.copy(bottomDefaultTriggerMode = resolved))
        PanelSide.TOP -> copy(edgeTrigger = edgeTrigger.copy(topDefaultTriggerMode = resolved))
    }
    val handle = updated.triggerHandle(side, handleId) ?: return updated
    if (!side.isHorizontalEdge || handle.alignOppositeGestures == false) return updated
    val otherSide = side.opposite()
    if (!otherSide.isHorizontalEdge || updated.triggerHandle(otherSide, handleId) == null) return updated
    return when (otherSide) {
        PanelSide.LEFT -> updated.copy(edgeTrigger = updated.edgeTrigger.copy(leftDefaultTriggerMode = resolved))
        PanelSide.RIGHT -> updated.copy(edgeTrigger = updated.edgeTrigger.copy(rightDefaultTriggerMode = resolved))
        else -> updated
    }
}

/** 将 [sourceSide] 上本 [handleId] 的槽位配置复制到对侧（开启「对齐手势」时）。 */
fun AppSettings.withGestureSlotsMirroredFromSide(
    sourceSide: PanelSide,
    handleId: String,
): AppSettings {
    if (!sourceSide.isHorizontalEdge) return this
    val otherSide = sourceSide.opposite()
    if (triggerHandle(otherSide, handleId) == null) return this
    var updated = this
    for (trigger in sideGestureSlotTriggers()) {
        val (action, mode) = slotConfigForMirror(sourceSide, trigger, handleId)
        updated = updated.applySlotConfig(otherSide, trigger, action, mode, handleId)
    }
    val defaultMode = defaultTriggerModeFor(sourceSide)
    updated = updated.withDefaultTriggerModeSynced(sourceSide, defaultMode, handleId)
    return updated
}

fun AppSettings.withTriggerAlignOppositeGestures(
    handleId: String,
    alignOppositeGestures: Boolean,
): AppSettings {
    fun mapSide(side: PanelSide): List<TriggerHandle> =
        allTriggerHandles(side).map { handle ->
            if (handle.id == handleId) {
                handle.copy(alignOppositeGestures = alignOppositeGestures)
            } else {
                handle
            }
        }
    return copy(
        edgeTrigger = edgeTrigger.copy(
            leftTriggerHandles = mapSide(PanelSide.LEFT),
            rightTriggerHandles = mapSide(PanelSide.RIGHT),
        ),
    )
}

fun AppSettings.shortcutGesturesConfiguredCount(): Int =
    gestureRules.count {
        it.enabled && it.action.type == GestureActionType.LAUNCH_APP &&
            it.trigger == GestureTriggerType.SHORT_SWIPE_IN
    }

fun AppSettings.effectiveRule(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureRule? {
    val newSlotId = GestureRule.slotId(side, trigger, handleId)
    val custom = gestureRules
        .filter { it.enabled && it.side == side && it.trigger == trigger && it.handleId == handleId }
        .maxByOrNull { it.priority }
        ?: gestureRules.firstOrNull {
            it.enabled &&
                it.side == side &&
                it.trigger == trigger &&
                it.id == newSlotId
        }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull {
                it.enabled &&
                    it.side == side &&
                    it.trigger == trigger &&
                    it.id == GestureRule.legacySlotId(side, trigger)
            }
        } else {
            null
        }
    if (custom != null) return custom
    // 兜底只到"出厂默认"这一层，**不再回退到别的触钮组**：
    // 以前非 default 的组会整段借用 `default` 组的动作，于是新组的槽位看起来"全是动作"，
    // 而且改/删 `default` 组会连带改掉新组里没亲手设过的槽位，用户根本看不出动作属于谁。
    // 保留出厂这一层是为了"没配过的槽位仍有一个可预期的动作"，它与任何组的数据无关。
    return SideGestureDefaults.rulesFor(side)
        .firstOrNull { it.trigger == trigger && it.action.isEffective() }
}

fun AppSettings.actionFor(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureAction {
    return effectiveRule(side, trigger, handleId)?.action ?: GestureAction.None
}

fun AppSettings.slotAction(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureAction = actionFor(side, trigger, handleId)

fun AppSettings.slotTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureTriggerMode {
    val newSlotId = GestureRule.slotId(side, trigger, handleId)
    return gestureRules.firstOrNull { it.id == newSlotId }?.triggerMode
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }?.triggerMode
        } else {
            null
        }
        ?: GestureTriggerMode.DEFAULT
}

fun AppSettings.resolvedTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureTriggerMode {
    val action = actionFor(side, trigger, handleId)
    if (action.requiresContinuousTriggerOnly() && !trigger.isPressOrTap) {
        return GestureTriggerMode.CONTINUOUS
    }
    // 单击/双击固定松手触发：不读槽位存储的模式，也不跟随侧边「默认触发模式」。否则用户把侧边默认
    // 设成「即时触发」时，双击会被 onTouchUp 的 IMMEDIATE 分支提前 return，单击的「点击穿透」也会被
    // dispatchMoveTimeGesture 静默丢弃。详见 GestureTriggerType.isTapFixedOnRelease。
    if (trigger.isTapFixedOnRelease) return GestureTriggerMode.ON_RELEASE
    val customMode = slotTriggerMode(side, trigger, handleId)
    if (customMode != GestureTriggerMode.DEFAULT) return customMode
    val ruleMode = effectiveRule(side, trigger, handleId)?.triggerMode
    if (ruleMode != null && ruleMode != GestureTriggerMode.DEFAULT) return ruleMode
    return defaultTriggerModeFor(side)
}

/** UI 展示用，与 [resolvedTriggerMode] 一致，避免未持久化时显示与运行时行为不符。 */
fun AppSettings.displayTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureTriggerMode = resolvedTriggerMode(side, trigger, handleId)

fun AppSettings.defaultTriggerModeFor(side: PanelSide): GestureTriggerMode =
    when (side) {
        PanelSide.LEFT -> leftDefaultTriggerMode
        PanelSide.RIGHT -> rightDefaultTriggerMode
        PanelSide.BOTTOM -> bottomDefaultTriggerMode
        PanelSide.TOP -> topDefaultTriggerMode
    }

fun AppSettings.triggerHandles(side: PanelSide): List<TriggerHandle> = when (side) {
    PanelSide.LEFT -> leftTriggerHandles
    PanelSide.RIGHT -> rightTriggerHandles
    PanelSide.BOTTOM -> bottomTriggerHandles
    PanelSide.TOP -> topTriggerHandles
}.filter { it.enabled }

fun AppSettings.allTriggerHandles(side: PanelSide): List<TriggerHandle> = when (side) {
    PanelSide.LEFT -> leftTriggerHandles
    PanelSide.RIGHT -> rightTriggerHandles
    PanelSide.BOTTOM -> bottomTriggerHandles
    PanelSide.TOP -> topTriggerHandles
}

fun AppSettings.primaryTriggerHandle(side: PanelSide): TriggerHandle =
    triggerHandles(side).firstOrNull()
        ?: allTriggerHandles(side).firstOrNull()
        ?: when (side) {
            PanelSide.BOTTOM -> TriggerHandle.bottomDefault()
            PanelSide.TOP -> TriggerHandle.topDefault()
            else -> TriggerHandle.default()
        }

fun AppSettings.triggerHandle(side: PanelSide, handleId: String): TriggerHandle? =
    allTriggerHandles(side).firstOrNull { it.id == handleId }

fun AppSettings.withTriggerHandles(
    side: PanelSide,
    handles: List<TriggerHandle>,
): AppSettings = withSideTriggerHandles(side, handles, allowEmpty = false)

private fun AppSettings.withSideTriggerHandles(
    side: PanelSide,
    handles: List<TriggerHandle>,
    allowEmpty: Boolean,
): AppSettings {
    val resolved = if (handles.isEmpty() && !allowEmpty) {
        when (side) {
            PanelSide.BOTTOM -> listOf(TriggerHandle.bottomDefault())
            PanelSide.TOP -> listOf(TriggerHandle.topDefault())
            else -> listOf(TriggerHandle.default())
        }
    } else {
        handles
    }
    return when (side) {
        PanelSide.LEFT -> copy(edgeTrigger = edgeTrigger.copy(leftTriggerHandles = resolved))
        PanelSide.RIGHT -> copy(edgeTrigger = edgeTrigger.copy(rightTriggerHandles = resolved))
        PanelSide.BOTTOM -> copy(edgeTrigger = edgeTrigger.copy(bottomTriggerHandles = resolved))
        PanelSide.TOP -> copy(edgeTrigger = edgeTrigger.copy(topTriggerHandles = resolved))
    }
}

fun AppSettings.withUpdatedTriggerHandleEdgeWidth(
    side: PanelSide,
    handleId: String,
    edgeWidthDp: Float,
): AppSettings {
    val width = edgeWidthDp.coerceIn(
        TriggerHandle.MIN_EDGE_WIDTH_DP,
        side.maxTriggerEdgeWidthDp(),
    )
    var updated = withSideTriggerHandleEdgeWidth(side, handleId, width)
    val sourceHandle = updated.triggerHandle(side, handleId)
    if (side.isHorizontalEdge && sourceHandle?.alignOppositeSide != false) {
        val otherSide = side.opposite()
        if (otherSide.isHorizontalEdge && updated.triggerHandle(otherSide, handleId) != null) {
            updated = updated.withSideTriggerHandleEdgeWidth(otherSide, handleId, width)
        }
    }
    return updated
}

fun AppSettings.withUpdatedTriggerHandleEnabled(
    side: PanelSide,
    handleId: String,
    enabled: Boolean,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            handle.copy(enabled = enabled)
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

private fun AppSettings.withSideTriggerHandleEdgeWidth(
    side: PanelSide,
    handleId: String,
    width: Float,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            handle.copy(edgeWidthDp = width)
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withUpdatedTriggerHandleDistances(
    side: PanelSide,
    handleId: String,
    shortSwipeDistanceDp: Float? = null,
    longSwipeDistanceDp: Float? = null,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            val short = shortSwipeDistanceDp?.roundToInt()?.toFloat()
                ?.coerceIn(0f, 160f) ?: handle.shortSwipeDistanceDp
            val longMin = if (short <= 0f) 16f else short + 16f
            var long = longSwipeDistanceDp?.roundToInt()?.toFloat()
                ?.coerceIn(longMin, 240f) ?: handle.longSwipeDistanceDp
            if (long < longMin) {
                long = longMin.coerceAtMost(240f)
            }
            handle.copy(
                shortSwipeDistanceDp = short,
                longSwipeDistanceDp = long,
            )
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withUpdatedTriggerHandle(
    side: PanelSide,
    handleId: String,
    topFraction: Float,
    heightFraction: Float,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            handle.copy(topFraction = topFraction, heightFraction = heightFraction)
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withTriggerAlignOppositeDesign(
    handleId: String,
    alignOppositeDesign: Boolean,
): AppSettings {
    fun mapSide(side: PanelSide): List<TriggerHandle> =
        allTriggerHandles(side).map { handle ->
            if (handle.id == handleId) {
                handle.copy(alignOppositeDesign = alignOppositeDesign)
            } else {
                handle
            }
        }
    return copy(
        edgeTrigger = edgeTrigger.copy(
            leftTriggerHandles = mapSide(PanelSide.LEFT),
            rightTriggerHandles = mapSide(PanelSide.RIGHT),
        ),
    )
}

fun AppSettings.withTriggerAlignOppositeSide(
    handleId: String,
    alignOppositeSide: Boolean,
): AppSettings {
    fun mapSide(side: PanelSide): List<TriggerHandle> =
        allTriggerHandles(side).map { handle ->
            if (handle.id == handleId) {
                handle.copy(alignOppositeSide = alignOppositeSide)
            } else {
                handle
            }
        }
    return copy(
        edgeTrigger = edgeTrigger.copy(
            leftTriggerHandles = mapSide(PanelSide.LEFT),
            rightTriggerHandles = mapSide(PanelSide.RIGHT),
        ),
    )
}

/**
 * 给新建的触钮落一份**出厂默认**手势规则（写进它自己的 handleId）。
 *
 * 为什么必须落盘：`effectiveRule` 对非 [TriggerHandle.DEFAULT_ID] 的 handleId 会回退到
 * `default` 那组的动作，于是新组的槽位会"跟着别人的组变"，删掉那组还会掉回出厂默认 ——
 * 用户看不出这些槽位到底属于谁。新建时写入自己的规则后，新组从出生就是独立的一份，
 * 改任何其他组都不再连带影响它。
 */
private fun AppSettings.withFactoryGestureSlots(
    handleId: String,
    sides: List<PanelSide>,
): AppSettings {
    val added = sides.flatMap { side ->
        SideGestureDefaults.rulesFor(side).map { rule ->
            rule.copy(id = GestureRule.slotId(side, rule.trigger, handleId), handleId = handleId)
        }
    }
    if (added.isEmpty()) return this
    val targetSides = sides.toSet()
    return withGestureRules(
        gestureRules.filterNot { it.handleId == handleId && it.side in targetSides } + added,
    )
}

/** 仅当该侧还没有这个 handleId 的规则时，补一份出厂默认（补齐对侧时用）。 */
private fun AppSettings.withFactoryGestureSlotsIfMissing(
    handleId: String,
    side: PanelSide,
): AppSettings {
    if (gestureRules.any { it.handleId == handleId && it.side == side }) return this
    return withFactoryGestureSlots(handleId, listOf(side))
}

fun AppSettings.withAddedTriggerHandlePair(): AppSettings {
    // 若某组只剩一侧，优先补齐对侧并复制手势，避免「删一侧后再添加」变成又一对新触钮。
    val incomplete = triggerCollectionEntries().firstOrNull { entry ->
        (entry.left == null) xor (entry.right == null)
    }
    if (incomplete != null) {
        val source = incomplete.left ?: incomplete.right!!
        val missingSide = if (incomplete.left == null) PanelSide.LEFT else PanelSide.RIGHT
        val sourceSide = missingSide.opposite()
        val restored = source.copy(id = incomplete.handleId)
        var updated = when (missingSide) {
            PanelSide.LEFT -> copy(edgeTrigger = edgeTrigger.copy(leftTriggerHandles = leftTriggerHandles + restored))
            PanelSide.RIGHT ->
                copy(edgeTrigger = edgeTrigger.copy(rightTriggerHandles = rightTriggerHandles + restored))
            else -> this
        }
        if (source.alignOppositeGestures != false) {
            updated = updated.withGestureSlotsMirroredFromSide(sourceSide, incomplete.handleId)
        }
        // 没镜像（对齐关闭）时，补回的那侧也要有自己的出厂动作，不能靠借用别组。
        return updated.withFactoryGestureSlotsIfMissing(incomplete.handleId, missingSide)
    }

    val pairId = TriggerHandle.newId()
    val leftNew = suggestNextTriggerHandle(leftTriggerHandles).copy(id = pairId)
    val rightNew = if (leftNew.alignOppositeSide) {
        leftNew
    } else {
        suggestNextTriggerHandle(rightTriggerHandles).copy(id = pairId)
    }
    return copy(
        edgeTrigger = edgeTrigger.copy(
            leftTriggerHandles = leftTriggerHandles + leftNew,
            rightTriggerHandles = rightTriggerHandles + rightNew,
        ),
    ).withFactoryGestureSlots(pairId, listOf(PanelSide.LEFT, PanelSide.RIGHT))
}

fun AppSettings.withAddedBottomTriggerHandle(): AppSettings {
    if (bottomTriggerHandles.size >= 10) return this
    val added = suggestNextBottomTriggerHandle(bottomTriggerHandles)
    return copy(
        edgeTrigger = edgeTrigger.copy(bottomTriggerHandles = bottomTriggerHandles + added),
    ).withFactoryGestureSlots(added.id, listOf(PanelSide.BOTTOM))
}

fun AppSettings.withAddedTopTriggerHandle(): AppSettings {
    if (topTriggerHandles.size >= 10) return this
    val added = suggestNextTopTriggerHandle(topTriggerHandles)
    return copy(
        edgeTrigger = edgeTrigger.copy(topTriggerHandles = topTriggerHandles + added),
    ).withFactoryGestureSlots(added.id, listOf(PanelSide.TOP))
}

fun AppSettings.withRemovedTriggerHandle(side: PanelSide, handleId: String): AppSettings {
    if (side == PanelSide.TOP && handleId == TriggerHandle.DEFAULT_ID) return this
    if (triggerHandle(side, handleId) == null) return this
    val updated = withSideTriggerHandles(
        side = side,
        handles = allTriggerHandles(side).filterNot { it.id == handleId },
        allowEmpty = true,
    )
    return updated.withGestureRules(
        gestureRules.filterNot { rule ->
            rule.handleId == handleId && rule.side == side
        },
    )
}

/** 仅移除布局触钮，不改动手势槽位（横屏独立布局用）。 */
fun AppSettings.withRemovedTriggerHandleLayoutOnly(side: PanelSide, handleId: String): AppSettings {
    if (triggerHandle(side, handleId) == null) return this
    return withSideTriggerHandles(
        side = side,
        handles = allTriggerHandles(side).filterNot { it.id == handleId },
        allowEmpty = true,
    )
}

fun AppSettings.sideTriggerPairs(): List<TriggerHandlePairEntry> =
    leftTriggerHandles.mapIndexed { index, left ->
        val right = rightTriggerHandles.getOrNull(index)?.takeIf { it.id == left.id }
            ?: rightTriggerHandles.firstOrNull { it.id == left.id }
        TriggerHandlePairEntry(index = index, handleId = left.id, left = left, right = right)
    }

fun AppSettings.triggerCollectionEntries(): List<TriggerCollectionEntry> {
    val orderedIds = buildList {
        leftTriggerHandles.forEach { if (it.id !in this) add(it.id) }
        rightTriggerHandles.forEach { if (it.id !in this) add(it.id) }
    }
    return orderedIds.map { handleId ->
        TriggerCollectionEntry(
            handleId = handleId,
            left = leftTriggerHandles.firstOrNull { it.id == handleId },
            right = rightTriggerHandles.firstOrNull { it.id == handleId },
        )
    }
}

fun AppSettings.withReplacedTriggerHandle(
    side: PanelSide,
    handleId: String,
    handle: TriggerHandle,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { existing ->
        if (!matched && existing.id == handleId) {
            matched = true
            handle
        } else {
            existing
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withSyncedTriggerHandle(
    sourceSide: PanelSide,
    handleId: String,
    handle: TriggerHandle,
): AppSettings {
    var updated = withReplacedTriggerHandle(sourceSide, handleId, handle)
    if (handle.alignOppositeSide != false) {
        val otherSide = sourceSide.opposite()
        if (updated.triggerHandle(otherSide, handleId) != null) {
            updated = updated.withReplacedTriggerHandle(otherSide, handleId, handle)
        }
    }
    return updated
}

fun AppSettings.withUpdatedTriggerHandleDesign(
    side: PanelSide,
    handleId: String,
    design: TriggerHandleDesign,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            handle.copy(design = design)
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withSyncedTriggerHandleDesignState(
    sourceSide: PanelSide,
    handleId: String,
    sourceHandle: TriggerHandle,
): AppSettings {
    var updated = withReplacedTriggerHandle(sourceSide, handleId, sourceHandle)
    if (!sourceSide.isHorizontalEdge || sourceHandle.alignOppositeDesign == false) return updated
    val otherSide = sourceSide.opposite()
    val other = updated.triggerHandle(otherSide, handleId) ?: return updated
    return updated.withReplacedTriggerHandle(
        otherSide,
        handleId,
        other.copy(
            design = sourceHandle.design,
            rectanglePresetState = sourceHandle.rectanglePresetState,
        ),
    )
}

fun AppSettings.withSyncedTriggerHandleDesign(
    sourceSide: PanelSide,
    handleId: String,
    design: TriggerHandleDesign,
): AppSettings {
    var updated = withUpdatedTriggerHandleDesign(sourceSide, handleId, design)
    val sourceHandle = updated.triggerHandle(sourceSide, handleId)
    if (sourceSide.isHorizontalEdge && sourceHandle?.alignOppositeDesign != false) {
        val otherSide = sourceSide.opposite()
        if (updated.triggerHandle(otherSide, handleId) != null) {
            updated = updated.withUpdatedTriggerHandleDesign(otherSide, handleId, design)
        }
    }
    return updated
}

private fun suggestNextTopTriggerHandle(existing: List<TriggerHandle>): TriggerHandle =
    suggestNextBottomTriggerHandle(existing)

private fun suggestNextBottomTriggerHandle(existing: List<TriggerHandle>): TriggerHandle {
    val occupied = existing.map { it.topFraction to it.bottomFraction }
    val minSpan = 0.15f
    val candidates = buildList {
        add(0.05f to 0.95f)
        val slotCount = (existing.size + 2).coerceAtMost(8)
        val step = 0.90f / slotCount
        for (i in 0 until slotCount) {
            val start = 0.05f + i * step
            add(start to (start + step * 0.85f).coerceAtMost(0.95f))
        }
    }
    val (start, end) = candidates.firstOrNull { (start, end) ->
        (end - start) >= minSpan &&
            occupied.none { (otherStart, otherEnd) ->
                start < otherEnd && end > otherStart
            }
    } ?: (0.05f to 0.95f)
    return TriggerHandle(
        id = TriggerHandle.newId(),
        topFraction = start,
        heightFraction = (end - start).coerceAtLeast(minSpan),
        enabled = true,
        alignOppositeSide = false,
        alignOppositeDesign = false,
        shortSwipeDistanceDp = existing.lastOrNull()?.shortSwipeDistanceDp
            ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP,
        longSwipeDistanceDp = existing.lastOrNull()?.longSwipeDistanceDp
            ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP,
    )
}

private fun suggestNextTriggerHandle(existing: List<TriggerHandle>): TriggerHandle {
    val occupied = existing.map { it.topFraction to it.bottomFraction }
    val minHeight = 0.15f
    val candidates = buildList {
        add(0.08f to 0.24f)
        add(0.55f to 0.83f)
        add(0.30f to 0.68f)
        val slotCount = (existing.size + 2).coerceAtMost(16)
        val step = 0.85f / slotCount
        for (i in 0 until slotCount) {
            val top = 0.05f + i * step
            add(top to (top + step * 0.85f).coerceAtMost(0.95f))
        }
    }
    val (top, bottom) = candidates.firstOrNull { (top, bottom) ->
        (bottom - top) >= minHeight &&
            occupied.none { (otherTop, otherBottom) ->
                top < otherBottom && bottom > otherTop
            }
    } ?: run {
        val top = (0.05f + existing.size * 0.1f).coerceAtMost(0.95f - minHeight)
        top to (top + minHeight)
    }
    return TriggerHandle(
        id = TriggerHandle.newId(),
        topFraction = top,
        heightFraction = (bottom - top).coerceAtLeast(minHeight),
        shortSwipeDistanceDp = existing.lastOrNull()?.shortSwipeDistanceDp
            ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP,
        longSwipeDistanceDp = existing.lastOrNull()?.longSwipeDistanceDp
            ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP,
    )
}
