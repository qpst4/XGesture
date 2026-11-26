package com.slideindex.app.screendraw

import android.content.Context

/**
 * 「屏幕绘制」默认工具偏好。
 *
 * 用户需求（反馈 #401 之后新增）：内置动作-屏幕绘制页面里给一个「默认工具」设置，
 * 决定下次/每次打开绘制时初始选中的工具。
 *
 * - [MEMORY] = 「记忆」：不固定工具，沿用上一次实际用过的那个；
 * - 其余值存 [ScreenDrawTool] 的枚举名，读回时解析失败一律回退涂鸦，
 *   保证老版本写入的脏值不会让绘制打不开。
 */
internal object ScreenDrawPrefs {
    const val PREFS_NAME = "screen_draw"
    const val KEY_DEFAULT_TOOL = "default_tool"
    const val KEY_LAST_TOOL = "last_tool"
    const val KEY_INITIAL_ORIENTATION = "initial_orientation"
    const val KEY_LAST_ORIENTATION = "last_orientation"

    /** 「记忆」选项的存储值；不是 [ScreenDrawTool] 的枚举名。 */
    const val MEMORY = "MEMORY"

    enum class InitialOrientation {
        HORIZONTAL,
        VERTICAL,
        MEMORY,
    }

    fun readDefaultTool(context: Context): String = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_TOOL, ScreenDrawTool.PEN.name)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: ScreenDrawTool.PEN.name

    fun writeDefaultTool(context: Context, value: String) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_DEFAULT_TOOL, value).apply()
        }
    }

    fun readLastTool(context: Context): ScreenDrawTool = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_TOOL, ScreenDrawTool.PEN.name)
        ScreenDrawTool.valueOf(raw ?: ScreenDrawTool.PEN.name)
    }.getOrDefault(ScreenDrawTool.PEN)

    fun writeLastTool(context: Context, tool: ScreenDrawTool) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_TOOL, tool.name).apply()
        }
    }

    /** 打开绘制时应该选中的工具。 */
    fun resolveInitialTool(context: Context): ScreenDrawTool {
        val configured = readDefaultTool(context)
        if (configured == MEMORY) return readLastTool(context)
        return runCatching { ScreenDrawTool.valueOf(configured) }
            .getOrDefault(ScreenDrawTool.PEN)
    }

    fun readInitialOrientation(context: Context): InitialOrientation = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_INITIAL_ORIENTATION, InitialOrientation.HORIZONTAL.name)
        InitialOrientation.valueOf(raw ?: InitialOrientation.HORIZONTAL.name)
    }.getOrDefault(InitialOrientation.HORIZONTAL)

    fun writeInitialOrientation(context: Context, value: InitialOrientation) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_INITIAL_ORIENTATION, value.name).apply()
        }
    }

    fun readLastOrientation(context: Context): ScreenDrawOrientation = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ORIENTATION, ScreenDrawOrientation.HORIZONTAL.name)
        ScreenDrawOrientation.valueOf(raw ?: ScreenDrawOrientation.HORIZONTAL.name)
    }.getOrDefault(ScreenDrawOrientation.HORIZONTAL)

    fun writeLastOrientation(context: Context, value: ScreenDrawOrientation) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_ORIENTATION, value.name).apply()
        }
    }

    fun resolveInitialOrientation(context: Context): ScreenDrawOrientation =
        when (readInitialOrientation(context)) {
            InitialOrientation.HORIZONTAL -> ScreenDrawOrientation.HORIZONTAL
            InitialOrientation.VERTICAL -> ScreenDrawOrientation.VERTICAL
            InitialOrientation.MEMORY -> readLastOrientation(context)
        }
}
