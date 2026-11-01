package com.slideindex.app.overlay

import com.slideindex.app.settings.FloatBallTranslateEngine

/** 「点了翻译之后去哪儿」：引擎与「即时翻译」开关两两组合后的唯一判定结果。 */
internal enum class TranslateLanding {
    /** 交给用户在设置里指定的本地翻译 App。 */
    EXTERNAL_APP,

    /** 跳转翻译：装了就交给翻译 App，否则打开网页（与「即时翻译」关闭时的老行为一致）。 */
    JUMP_TO_WEB,

    /** 在取词结果面板里用所选引擎翻译。 */
    IN_PANEL,
}

/**
 * 翻译落地方式的判定顺序（纯函数，便于单测）。
 *
 * 顺序本身就是产品语义，三处入口（取词面板、全局复制、拖拽流转栏）都读它，改动等于改行为：
 * 1. 「本地 App」优先于开关 —— 译文只能由那个 App 显示，即时浮窗不适用；
 * 2. 关闭「即时翻译」= 跳转翻译，引擎（Google/ML Kit/云端）在这一档不参与；
 * 3. 其余才是在取词结果面板里用所选引擎翻译。
 */
internal fun resolveTranslateLanding(
    engine: FloatBallTranslateEngine,
    instantTranslate: Boolean,
): TranslateLanding = when {
    engine == FloatBallTranslateEngine.LOCAL_APP -> TranslateLanding.EXTERNAL_APP
    !instantTranslate -> TranslateLanding.JUMP_TO_WEB
    else -> TranslateLanding.IN_PANEL
}
