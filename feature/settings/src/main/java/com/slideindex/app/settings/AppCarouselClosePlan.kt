package com.slideindex.app.settings

/**
 * 应用切换器「关闭」胶囊的行为决策（纯函数，面板与 `:app` 的执行侧共用，便于单测）。
 *
 * 最近任务模式的卡片可能只是使用记录、没有真实任务，划掉无从谈起，因此固定为仅强停；
 * 三个选项只在后台任务模式生效。
 */
object AppCarouselClosePlan {

    fun effectiveMode(
        listSource: AppCarouselListSource,
        configured: TaskSwitcherCloseMode,
    ): TaskSwitcherCloseMode =
        if (listSource == AppCarouselListSource.BACKGROUND_TASKS) {
            configured
        } else {
            TaskSwitcherCloseMode.FORCE_STOP
        }

    fun shouldForceStop(mode: TaskSwitcherCloseMode): Boolean =
        mode == TaskSwitcherCloseMode.FORCE_STOP || mode == TaskSwitcherCloseMode.BOTH

    /** 是否要从系统最近任务里划掉卡片。 */
    fun shouldDismissTask(mode: TaskSwitcherCloseMode): Boolean =
        mode == TaskSwitcherCloseMode.DISMISS_RECENTS || mode == TaskSwitcherCloseMode.BOTH
}
