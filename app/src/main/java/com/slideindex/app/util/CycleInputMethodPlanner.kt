package com.slideindex.app.util

/**
 * 「循环切换输入法」一次触发的决策结果。
 *
 * @property switchToImeId 要切换到的输入法；null 表示本次不切换
 * @property rememberPreviousImeId 切换成功后要记住的「切换前输入法」，供下一次在目标上按回
 * @property clearPrevious 切换成功后清掉记住的输入法（已经切回去了）
 * @property singleEnabledImeId 只启用了一个输入法时带出它的 id，用于沿用旧的提示文案
 */
internal data class CycleInputMethodDecision(
    val switchToImeId: String? = null,
    val rememberPreviousImeId: String? = null,
    val clearPrevious: Boolean = false,
    val singleEnabledImeId: String? = null,
)

/**
 * 目标为空/已失效 → 按启用顺序轮换（旧行为）；
 * 目标有效 → 在当前输入法与目标之间来回切。
 *
 * [excludedImeIds] 只影响「轮换」的候选集合，明确配置了目标输入法时不受它影响。
 * 如果全被排除，轮换退回全部已启用输入法，避免动作彻底失效。
 */
internal object CycleInputMethodPlanner {

    fun decide(
        enabledImeIds: List<String>,
        currentImeId: String,
        targetImeId: String,
        previousImeId: String,
        excludedImeIds: Set<String> = emptySet(),
    ): CycleInputMethodDecision {
        if (enabledImeIds.isEmpty()) return CycleInputMethodDecision()

        // 只有一个已启用输入法：保持原来的「仅启用一个」提示，过滤没有意义。
        if (enabledImeIds.size == 1) {
            return CycleInputMethodDecision(singleEnabledImeId = enabledImeIds.single())
        }

        val rotationImeIds = enabledImeIds
            .filterNot { it in excludedImeIds }
            .ifEmpty { enabledImeIds }

        val target = targetImeId.takeIf { it in enabledImeIds }
        if (target != null) {
            if (currentImeId != target) {
                return CycleInputMethodDecision(
                    switchToImeId = target,
                    rememberPreviousImeId = currentImeId
                        .takeIf { it in enabledImeIds && it != target },
                )
            }
            // 已经在目标上：切回上一次进来的那个输入法；没有记录就退回轮换。
            val previous = previousImeId.takeIf { it in enabledImeIds && it != target }
            return if (previous != null) {
                CycleInputMethodDecision(
                    switchToImeId = previous,
                    clearPrevious = true,
                )
            } else {
                rotate(rotationImeIds, target)
            }
        }

        return rotate(rotationImeIds, currentImeId)
    }

    private fun rotate(
        rotationImeIds: List<String>,
        currentImeId: String,
    ): CycleInputMethodDecision {
        if (rotationImeIds.size == 1) {
            val only = rotationImeIds.single()
            return if (only == currentImeId) {
                // 只剩一个候选且已经在这上面：没有可切换的目标，静默不动作。
                CycleInputMethodDecision()
            } else {
                CycleInputMethodDecision(switchToImeId = only)
            }
        }
        val index = rotationImeIds.indexOf(currentImeId)
        val next = if (index >= 0) (index + 1) % rotationImeIds.size else 0
        return CycleInputMethodDecision(switchToImeId = rotationImeIds[next])
    }
}
