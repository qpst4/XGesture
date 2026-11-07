package com.slideindex.app.overlay.pickresult

import com.slideindex.app.settings.PickResultTextModeDefault

/**
 * 取词面板「点词」状态的持久化编解码。
 *
 * 只有两种「进入面板」的模式需要记住：[PickResultTextMode.WORD_TAP]（点词开启）与
 * [PickResultTextMode.SELECT]（点词关闭）。[PickResultTextMode.EDIT] 只在本会话内由编辑入口
 * 进入，不作为下次进入面板的默认值，统一按 WORD_TAP 记录。
 */
object PickResultTextModeStore {
    const val WORD_TAP = "word_tap"
    const val SELECT = "select"

    fun toStorageKey(mode: PickResultTextMode): String = when (mode) {
        PickResultTextMode.SELECT -> SELECT
        PickResultTextMode.WORD_TAP, PickResultTextMode.EDIT -> WORD_TAP
    }

    /** 未知/空值返回 null，由调用方决定回落值。 */
    fun fromStorageKey(key: String?): PickResultTextMode? = when (key) {
        WORD_TAP -> PickResultTextMode.WORD_TAP
        SELECT -> PickResultTextMode.SELECT
        else -> null
    }
}

/**
 * 计算取词面板进入时的文本模式。
 *
 * - [explicit]（截图 OCR / 通用复制等入口显式传入）优先级最高，不受设置影响；
 * - [PickResultTextModeDefault.REMEMBER_LAST]：取上次退出面板时的模式，读不到（老用户/首次）时回落 WORD_TAP；
 * - [PickResultTextModeDefault.ALWAYS_ON]：WORD_TAP；
 * - [PickResultTextModeDefault.ALWAYS_OFF]：SELECT。
 */
fun resolvePickResultEnterTextMode(
    explicit: PickResultTextMode?,
    defaultState: PickResultTextModeDefault,
    lastStoredMode: String?,
): PickResultTextMode = explicit ?: when (defaultState) {
    PickResultTextModeDefault.REMEMBER_LAST ->
        PickResultTextModeStore.fromStorageKey(lastStoredMode) ?: PickResultTextMode.WORD_TAP
    PickResultTextModeDefault.ALWAYS_ON -> PickResultTextMode.WORD_TAP
    PickResultTextModeDefault.ALWAYS_OFF -> PickResultTextMode.SELECT
}
