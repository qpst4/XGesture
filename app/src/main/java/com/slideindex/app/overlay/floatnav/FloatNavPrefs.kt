package com.slideindex.app.overlay.floatnav

import android.content.Context

/** 浮条布局形态。 */
internal enum class FloatNavForm {
    HORIZONTAL,
    VERTICAL,
}

/** 内置动作页里的初始形态选项。 */
internal enum class FloatNavFormSetting {
    HORIZONTAL,
    VERTICAL,
    MEMORY,
}

/**
 * 浮动导航条的动作级偏好。
 *
 * [FloatNavFormSetting.MEMORY] 时读取上一次实际形态；长按把柄切换形态会同步写 last。
 */
internal object FloatNavPrefs {
    const val PREFS_NAME = "float_nav"
    const val KEY_INITIAL_FORM = "initial_form"
    const val KEY_LAST_FORM = "last_form"

    fun readInitialForm(context: Context): FloatNavFormSetting = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_INITIAL_FORM, FloatNavFormSetting.HORIZONTAL.name)
        FloatNavFormSetting.valueOf(raw ?: FloatNavFormSetting.HORIZONTAL.name)
    }.getOrDefault(FloatNavFormSetting.HORIZONTAL)

    fun writeInitialForm(context: Context, value: FloatNavFormSetting) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_INITIAL_FORM, value.name).apply()
        }
    }

    fun readLastForm(context: Context): FloatNavForm = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_FORM, FloatNavForm.HORIZONTAL.name)
        FloatNavForm.valueOf(raw ?: FloatNavForm.HORIZONTAL.name)
    }.getOrDefault(FloatNavForm.HORIZONTAL)

    fun writeLastForm(context: Context, value: FloatNavForm) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_FORM, value.name).apply()
        }
    }

    fun resolveInitialForm(context: Context): FloatNavForm = when (readInitialForm(context)) {
        FloatNavFormSetting.HORIZONTAL -> FloatNavForm.HORIZONTAL
        FloatNavFormSetting.VERTICAL -> FloatNavForm.VERTICAL
        FloatNavFormSetting.MEMORY -> readLastForm(context)
    }
}
