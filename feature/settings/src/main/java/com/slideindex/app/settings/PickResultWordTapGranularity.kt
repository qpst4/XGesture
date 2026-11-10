package com.slideindex.app.settings

/**
 * 取词面板「点词」模式的粒度。
 *
 * [WORD] 保持历史行为：按词切分，每个词一颗可见 chip；
 * [CHARACTER] 仍然按「点词」选，但每个字符一个不可见的命中单元，正文按普通文字排版，
 * 选中只画高亮、不画 chip 边框/底色。
 */
enum class PickResultWordTapGranularity(val storageKey: String) {
    WORD("word"),
    CHARACTER("character"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PickResultWordTapGranularity =
            when (key) {
                CHARACTER.storageKey -> CHARACTER
                else -> WORD
            }
    }
}
