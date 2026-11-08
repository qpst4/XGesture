package com.slideindex.app.overlay.quickmenu

import android.content.Context
import com.slideindex.app.settings.AppSettings

/**
 * 快捷菜单（quickmenu）与 `:app` 的接线点。
 *
 * 菜单里有些条目直接复用 app 侧既有入口（前台 Activity、冰箱、Shell 命令面板、
 * 搜索面板、剪贴板面板……），模块侧只按 [QuickMenuTool] 请求一次。
 */
interface QuickMenuHost {

    /** 运行一个"外部入口"类型工具；返回是否成功启动。 */
    fun runEntry(context: Context, tool: QuickMenuTool, settings: AppSettings): Boolean

    /** 设置降级闹钟（提醒用）。 */
    fun setReminderAlarm(
        alarmManager: android.app.AlarmManager,
        triggerAtMillis: Long,
        operation: android.app.PendingIntent,
    )

    /** 抓一张全屏截图（取色用）。 */
    suspend fun captureScreenshot(): com.slideindex.app.service.ScreenshotCaptureOutcome?

    /** 执行一次宏动作；返回是否执行成功。 */
    fun runMacro(context: Context, macroId: String): Boolean
}

/** 模块内服务定位点：`:app` 启动时注入。 */
object QuickMenuHostAccess {

    @Volatile
    var host: QuickMenuHost? = null
}
