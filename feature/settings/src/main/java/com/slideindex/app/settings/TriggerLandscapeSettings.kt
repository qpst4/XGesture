package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide

/** 横屏触钮布局与手势是否已从竖屏完成一次性初始化。 */
fun AppSettings.hasLandscapeTriggerProfile(): Boolean = landscapeTriggersInitialized

fun AppSettings.hasStoredLandscapeTriggerHandles(): Boolean =
    leftTriggerHandlesLandscape.isNotEmpty() ||
        rightTriggerHandlesLandscape.isNotEmpty() ||
        bottomTriggerHandlesLandscape.isNotEmpty() ||
        topTriggerHandlesLandscape.isNotEmpty()

fun AppSettings.storedLandscapeTriggerHandles(side: PanelSide): List<TriggerHandle> = when (side) {
    PanelSide.LEFT -> leftTriggerHandlesLandscape
    PanelSide.RIGHT -> rightTriggerHandlesLandscape
    PanelSide.BOTTOM -> bottomTriggerHandlesLandscape
    PanelSide.TOP -> topTriggerHandlesLandscape
}

/** 将横屏存储映射到主 handle 字段，复用现有编辑/变更逻辑。 */
fun AppSettings.forLandscapeHandleEditing(): AppSettings = copy(
    edgeTrigger = edgeTrigger.copy(
        leftTriggerHandles = storedLandscapeTriggerHandles(PanelSide.LEFT)
            .ifEmpty { leftTriggerHandles },
        rightTriggerHandles = storedLandscapeTriggerHandles(PanelSide.RIGHT)
            .ifEmpty { rightTriggerHandles },
        bottomTriggerHandles = storedLandscapeTriggerHandles(PanelSide.BOTTOM)
            .ifEmpty { bottomTriggerHandles },
        topTriggerHandles = storedLandscapeTriggerHandles(PanelSide.TOP)
            .ifEmpty { topTriggerHandles },
    ),
)

/** 横屏编辑态：布局 + 手势规则均映射到主字段，与竖屏编辑互不干扰。 */
fun AppSettings.forLandscapeEditing(): AppSettings = forLandscapeHandleEditing().let { base ->
    base.copy(
        edgeTrigger = base.edgeTrigger.copy(
            leftDefaultTriggerMode = leftDefaultTriggerModeLandscape,
            rightDefaultTriggerMode = rightDefaultTriggerModeLandscape,
            bottomDefaultTriggerMode = bottomDefaultTriggerModeLandscape,
            topDefaultTriggerMode = topDefaultTriggerModeLandscape,
        ),
        launcher = base.launcher.copy(gestureRules = gestureRulesLandscape),
    )
}

/** 把编辑后的主字段写回横屏存储（布局 + 手势）。 */
fun AppSettings.mergeLandscapeEdits(edited: AppSettings): AppSettings = copy(
    edgeTrigger = edgeTrigger.copy(
        leftTriggerHandlesLandscape = edited.leftTriggerHandles,
        rightTriggerHandlesLandscape = edited.rightTriggerHandles,
        bottomTriggerHandlesLandscape = edited.bottomTriggerHandles,
        topTriggerHandlesLandscape = edited.topTriggerHandles,
        gestureRulesLandscape = edited.gestureRules,
        leftDefaultTriggerModeLandscape = edited.leftDefaultTriggerMode,
        rightDefaultTriggerModeLandscape = edited.rightDefaultTriggerMode,
        bottomDefaultTriggerModeLandscape = edited.bottomDefaultTriggerMode,
        topDefaultTriggerModeLandscape = edited.topDefaultTriggerMode,
    ),
)

/** @deprecated 仅布局；新手势请用 [mergeLandscapeEdits]。 */
fun AppSettings.mergeLandscapeHandleEdits(edited: AppSettings): AppSettings = mergeLandscapeEdits(edited)

/** 首次进横屏：复制竖屏布局（均匀分布）与手势规则，此后与竖屏无任何同步。 */
fun AppSettings.withLandscapeCopiedFromPortrait(): AppSettings = copy(
    edgeTrigger = edgeTrigger.copy(
        landscapeTriggersInitialized = true,
        leftTriggerHandlesLandscape = redistributeLandscapeSideHandles(leftTriggerHandles),
        rightTriggerHandlesLandscape = redistributeLandscapeSideHandles(rightTriggerHandles),
        bottomTriggerHandlesLandscape = redistributeLandscapeSideHandles(bottomTriggerHandles),
        topTriggerHandlesLandscape = redistributeLandscapeSideHandles(topTriggerHandles),
        gestureRulesLandscape = gestureRules.map { it.copy() },
        leftDefaultTriggerModeLandscape = leftDefaultTriggerMode,
        rightDefaultTriggerModeLandscape = rightDefaultTriggerMode,
        bottomDefaultTriggerModeLandscape = bottomDefaultTriggerMode,
        topDefaultTriggerModeLandscape = topDefaultTriggerMode,
    ),
)

/** 已有横屏布局但未迁移手势存储时，从竖屏复制一份手势（仅当横屏手势为空）。 */
fun AppSettings.withLandscapeGesturesMigratedIfNeeded(): AppSettings {
    if (!landscapeTriggersInitialized && !hasStoredLandscapeTriggerHandles()) return this
    if (gestureRulesLandscape.isNotEmpty()) return this
    return copy(
        edgeTrigger = edgeTrigger.copy(
            landscapeTriggersInitialized = true,
            gestureRulesLandscape = gestureRules.map { it.copy() },
            leftDefaultTriggerModeLandscape = leftDefaultTriggerMode,
            rightDefaultTriggerModeLandscape = rightDefaultTriggerMode,
            bottomDefaultTriggerModeLandscape = bottomDefaultTriggerMode,
            topDefaultTriggerModeLandscape = topDefaultTriggerMode,
        ),
    )
}

/** 若横屏触钮区间重叠，按数量等分短边后写回（保留 id/外观/手势相关字段）。 */
fun AppSettings.withRepairedLandscapeHandleLayoutIfOverlapping(): AppSettings {
    fun repair(stored: List<TriggerHandle>, portrait: List<TriggerHandle>): List<TriggerHandle> {
        val source = stored.ifEmpty { portrait }
        if (source.size <= 1 || !source.hasOverlappingSpans()) return stored
        return redistributeLandscapeSideHandles(source)
    }
    return copy(
        edgeTrigger = edgeTrigger.copy(
            leftTriggerHandlesLandscape = repair(leftTriggerHandlesLandscape, leftTriggerHandles),
            rightTriggerHandlesLandscape = repair(rightTriggerHandlesLandscape, rightTriggerHandles),
            bottomTriggerHandlesLandscape = repair(bottomTriggerHandlesLandscape, bottomTriggerHandles),
            topTriggerHandlesLandscape = repair(topTriggerHandlesLandscape, topTriggerHandles),
        ),
    )
}

/** 横屏运行态：已初始化则用横屏布局与手势（允许空列表），否则回退竖屏。 */
fun AppSettings.withRuntimeLandscapeSettings(isLandscape: Boolean): AppSettings {
    if (!isLandscape || !landscapeTriggersInitialized) return this
    return copy(
        edgeTrigger = edgeTrigger.copy(
            leftTriggerHandles = leftTriggerHandlesLandscape,
            rightTriggerHandles = rightTriggerHandlesLandscape,
            bottomTriggerHandles = bottomTriggerHandlesLandscape,
            topTriggerHandles = topTriggerHandlesLandscape,
            leftDefaultTriggerMode = leftDefaultTriggerModeLandscape,
            rightDefaultTriggerMode = rightDefaultTriggerModeLandscape,
            bottomDefaultTriggerMode = bottomDefaultTriggerModeLandscape,
            topDefaultTriggerMode = topDefaultTriggerModeLandscape,
        ),
        launcher = launcher.copy(gestureRules = gestureRulesLandscape),
    )
}

fun AppSettings.withRuntimeTriggerHandles(isLandscape: Boolean): AppSettings =
    withRuntimeLandscapeSettings(isLandscape)

/**
 * 「复制竖屏设置」的合并结果：各侧的横屏触钮与要落盘的手势规则全量。
 *
 * 触钮规则不做跨侧改写，因此整体交给 `gestureRulesLandscape`，不走 `launcher.gestureRules`
 * （后者在有「左右对齐」时会被镜像逻辑改写）。
 */
data class MergedLandscapeProfile(
    val handles: Map<PanelSide, List<TriggerHandle>> = emptyMap(),
    val rules: List<GestureRule> = emptyList(),
)

/** 竖屏某个触钮上"已经配过"的槽位。 */
private data class SlotConfig(
    val trigger: GestureTriggerType,
    val action: GestureAction,
    val mode: GestureTriggerMode,
)

/**
 * 「复制竖屏设置」：把竖屏的触钮与手势动作合并进横屏（[this] 为竖屏配置，[target] 为横屏配置）。
 *
 * - 触钮按「同侧 + 同序号」配对，不看 id（两边自加的触钮 id 不同，靠 id 会配错）：
 *   两边都有的同步动作，竖屏多出来的补到横屏，横屏独有的原样保留。
 * - 触钮位置按横屏短边重排（横屏本来就这么干）；外观沿用横屏自己的。
 */
fun AppSettings.withPortraitCopiedToLandscape(target: AppSettings): MergedLandscapeProfile {
    if (!target.landscapeTriggersInitialized) return MergedLandscapeProfile()

    val sides = listOf(PanelSide.LEFT, PanelSide.RIGHT, PanelSide.BOTTOM, PanelSide.TOP)
    val handles = mutableMapOf<PanelSide, List<TriggerHandle>>()
    val rules = mutableListOf<GestureRule>()

    for (side in sides) {
        val portraitHandles = allTriggerHandles(side)
        val landscapeHandles = target.landscapeHandlesFor(side)
        val landscapeRules = target.gestureRulesLandscape.filter { it.side == side }

        // 1) 配上的触钮：动作与触发模式跟竖屏；外观沿用横屏自己的，混用会造出两边都不像的样式。
        landscapeHandles.forEachIndexed { index, current ->
            val source = portraitHandles.getOrNull(index) ?: return@forEachIndexed
            for (slot in portraitSlots(side, source.id)) {
                rules += landscapeRules
                    .firstOrNull { it.handleId == current.id && it.trigger == slot.trigger }
                    ?.copy(action = slot.action, triggerMode = slot.mode)
                    ?: newSlotRule(side, current.id, slot)
            }
        }

        // 2) 竖屏多出来的触钮补到横屏（连同它的槽位动作）；横屏独有的原样保留。
        val appended = portraitHandles.drop(landscapeHandles.size)
        if (appended.isNotEmpty()) {
            val merged = landscapeHandles + appended.map { it.copy() }
            handles[side] = redistributeLandscapeSideHandles(merged)
            for (source in appended) {
                for (slot in portraitSlots(side, source.id)) {
                    rules += newSlotRule(side, source.id, slot)
                }
            }
        }

        // 3) 横屏本侧剩下的规则（没配上的触钮、独有的触钮）原样保留，已同步过的槽位不重复添加。
        val syncedKeys = landscapeRules
            .filter { existing -> rules.any { it.id == existing.id } }
            .map { it.id }
            .toSet()
        rules += landscapeRules.filterNot { it.id in syncedKeys }
    }

    return MergedLandscapeProfile(handles = handles, rules = rules)
}

/**
 * 竖屏某个触钮的槽位配置（动作 + 触发模式）。
 *
 * 覆盖 [sideGestureSlotTriggers] 里的全部槽位而不是"只挑显式配过的"：竖屏某个槽位没显式设过时，
 * [actionFor] 会给出出厂动作，必须把这个结果也带过去，否则横屏会留着上一轮复制留下的旧动作。
 */
private fun AppSettings.portraitSlots(side: PanelSide, handleId: String): List<SlotConfig> =
    sideGestureSlotTriggers().map { trigger ->        SlotConfig(
            trigger = trigger,
            action = actionFor(side, trigger, handleId),
            mode = slotTriggerMode(side, trigger, handleId),
        )
    }

private fun newSlotRule(
    side: PanelSide,
    handleId: String,
    slot: SlotConfig,
): GestureRule = GestureRule(
    id = GestureRule.slotId(side, slot.trigger, handleId),
    side = side,
    trigger = slot.trigger,
    action = slot.action,
    triggerMode = slot.mode,
    handleId = handleId,
)

/** 横屏某个侧的触钮。 */
internal fun AppSettings.landscapeHandlesFor(side: PanelSide): List<TriggerHandle> = when (side) {
    PanelSide.LEFT -> leftTriggerHandlesLandscape
    PanelSide.RIGHT -> rightTriggerHandlesLandscape
    PanelSide.BOTTOM -> bottomTriggerHandlesLandscape
    PanelSide.TOP -> topTriggerHandlesLandscape
}

private const val LANDSCAPE_SPAN_MIN = 0.05f
private const val LANDSCAPE_SPAN_MAX = 0.95f
private const val LANDSCAPE_USABLE = LANDSCAPE_SPAN_MAX - LANDSCAPE_SPAN_MIN
private const val LANDSCAPE_SLOT_GAP = 0.02f
private const val LANDSCAPE_MIN_HEIGHT_FRACTION = 0.12f
private const val LANDSCAPE_MAX_HEIGHT_FRACTION = 0.36f

/**
 * 将同侧多个触钮按比例分布在可用边长上，避免横屏短边高度不足时叠在一起。
 * 保持原有顺序与 id，仅调整 topFraction / heightFraction。
 */
internal fun redistributeLandscapeSideHandles(handles: List<TriggerHandle>): List<TriggerHandle> {
    if (handles.isEmpty()) return emptyList()
    if (handles.size == 1) {
        val only = handles.first()
        val height = only.heightFraction.coerceIn(LANDSCAPE_MIN_HEIGHT_FRACTION, LANDSCAPE_MAX_HEIGHT_FRACTION)
        val top = (0.5f - height / 2f).coerceIn(LANDSCAPE_SPAN_MIN, LANDSCAPE_SPAN_MAX - height)
        return listOf(only.copy(topFraction = top, heightFraction = height))
    }
    val count = handles.size
    val totalGap = LANDSCAPE_SLOT_GAP * (count - 1)
    val span = ((LANDSCAPE_USABLE - totalGap) / count)
        .coerceIn(LANDSCAPE_MIN_HEIGHT_FRACTION, LANDSCAPE_MAX_HEIGHT_FRACTION)
    val groupHeight = span * count + totalGap
    val start = LANDSCAPE_SPAN_MIN + (LANDSCAPE_USABLE - groupHeight) / 2f
    return handles.mapIndexed { index, handle ->
        val top = start + index * (span + LANDSCAPE_SLOT_GAP)
        handle.copy(
            topFraction = top.coerceIn(LANDSCAPE_SPAN_MIN, LANDSCAPE_SPAN_MAX - span),
            heightFraction = span,
        )
    }
}

private fun List<TriggerHandle>.hasOverlappingSpans(): Boolean {
    if (size <= 1) return false
    val sorted = sortedBy { it.topFraction }
    for (i in 1 until sorted.size) {
        if (sorted[i].topFraction < sorted[i - 1].bottomFraction - 0.001f) return true
    }
    return false
}

fun AppSettings.runtimeTriggerHandles(side: PanelSide, isLandscape: Boolean): List<TriggerHandle> {
    if (!isLandscape || !landscapeTriggersInitialized) return triggerHandles(side)
    return storedLandscapeTriggerHandles(side).filter { it.enabled }
}

fun AppSettings.runtimeAllTriggerHandles(side: PanelSide, isLandscape: Boolean): List<TriggerHandle> {
    if (!isLandscape || !landscapeTriggersInitialized) return allTriggerHandles(side)
    return storedLandscapeTriggerHandles(side)
}

fun AppSettings.runtimeTriggerHandle(
    side: PanelSide,
    handleId: String,
    isLandscape: Boolean,
): TriggerHandle? = runtimeAllTriggerHandles(side, isLandscape).firstOrNull { it.id == handleId }

fun AppSettings.runtimeGestureRules(isLandscape: Boolean): List<GestureRule> {
    if (!isLandscape || !landscapeTriggersInitialized) return gestureRules
    return gestureRulesLandscape
}

fun AppSettings.runtimeDefaultTriggerMode(side: PanelSide, isLandscape: Boolean): GestureTriggerMode {
    if (!isLandscape || !landscapeTriggersInitialized) return defaultTriggerModeFor(side)
    return when (side) {
        PanelSide.LEFT -> leftDefaultTriggerModeLandscape
        PanelSide.RIGHT -> rightDefaultTriggerModeLandscape
        PanelSide.BOTTOM -> bottomDefaultTriggerModeLandscape
        PanelSide.TOP -> topDefaultTriggerModeLandscape
    }
}
