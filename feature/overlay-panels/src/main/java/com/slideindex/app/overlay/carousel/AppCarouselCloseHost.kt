package com.slideindex.app.overlay.carousel

import com.slideindex.app.settings.TaskSwitcherCloseMode

/**
 * 一张卡片的关闭目标。
 *
 * [taskId] = 0 表示没有对应的真实任务（最近任务模式里由 UsageStats / 已安装应用补出来的卡），
 * 此时只能按包名尝试划掉。
 */
data class AppCarouselCloseTarget(
    val packageName: String,
    val taskId: Int,
)

/**
 * 关闭动作的执行方，由 `:app` 在 `show()` 时注入。
 *
 * 面板只表达“关哪些、怎么关”：强停与划掉都要特权权限，而 `:feature` 模块不能依赖 `:app`，
 * 又不能再往 [com.slideindex.app.overlay.panels.PanelRuntimeHooks] 里加静态槽位
 * （AGENTS.md 4.1 / 4.4），所以改用不可变接口随调用传入，而不是全局钩子。
 */
interface AppCarouselCloseHost {
    fun close(targets: List<AppCarouselCloseTarget>, mode: TaskSwitcherCloseMode)
}
