package com.slideindex.app.overlay.pickresult

/**
 * 取词面板的**内容级** RTL 判定。
 *
 * 不能用系统 locale：用户可能是中文系统，但 OCR 出来的是阿拉伯语 / 希伯来语。
 * 规则与 Unicode 双向算法一致——取**首个强方向字符**决定方向；数字、标点、空白是
 * 弱字符/中性字符，跳过。这样「Miui 123」判定为 LTR，「النص」判定为 RTL。
 */
internal object PickResultTextDirection {

    fun isRtl(tokens: List<String>): Boolean {
        for (token in tokens) {
            var index = 0
            while (index < token.length) {
                val codePoint = token.codePointAt(index)
                when (Character.getDirectionality(codePoint)) {
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                    -> return true

                    Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                    Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                    Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                    -> return false
                }
                index += Character.charCount(codePoint)
            }
        }
        return false
    }
}
