package com.slideindex.app.overlay.carousel

import android.util.Log
import com.slideindex.app.overlay.panels.PanelRuntimeHooks
import com.slideindex.app.settings.AppCarouselClosePlan
import com.slideindex.app.settings.TaskSwitcherCloseMode
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TaskSwitcherLockStore

/**
 * 应用切换器的关闭动作执行方。
 *
 * 面板只表达「关谁、怎么关」，真正的强停 / 划掉在这里做：这两件事都要特权权限，
 * 而 `:feature:overlay-panels` 不能依赖 `:app`，所以用 [AppCarouselCloseHost] 接口随 `show()`
 * 注入，而不是新增 `PanelRuntimeHooks` 静态槽位（AGENTS.md 4.1 / 4.4）。
 *
 * 顺序与任务切换器一致：先强停、再划掉；锁定的包跳过。划掉优先按 taskId（分身能精确到一张卡），
 * taskId 缺失时退回按包名。
 */
object AppCarouselCloseDispatcher : AppCarouselCloseHost {

    private const val TAG = "AppCarouselClose"

    override fun close(targets: List<AppCarouselCloseTarget>, mode: TaskSwitcherCloseMode) {
        if (targets.isEmpty()) return
        Thread {
            runCatching {
                val context = TaskManagerUtil.applicationContext()
                val locked = TaskSwitcherLockStore.lockedPackages(context)
                val forceStop = AppCarouselClosePlan.shouldForceStop(mode)
                val dismissTask = AppCarouselClosePlan.shouldDismissTask(mode)
                for (target in targets) {
                    if (target.packageName.isBlank() || target.packageName in locked) continue
                    if (forceStop) {
                        TaskManagerUtil.forceStopPackage(target.packageName)
                    }
                    if (dismissTask) {
                        val removed = if (target.taskId > 0) {
                            TaskManagerUtil.removeTaskById(target.taskId)
                        } else {
                            TaskManagerUtil.removeTaskByPackage(target.packageName)
                        }
                        if (!removed) {
                            Log.w(
                                TAG,
                                "dismiss failed pkg=${target.packageName} taskId=${target.taskId}",
                            )
                        }
                    }
                }
                // 让任务缓存与系统重新对齐：圆环启动器等消费方也用这份缓存。
                PanelRuntimeHooks.appRepository?.invoke(context)?.let { repo ->
                    RecentTasksLoader.requestRefreshAfterSwitch(repo)
                }
            }.onFailure { Log.e(TAG, "close failed", it) }
        }.start()
    }
}
