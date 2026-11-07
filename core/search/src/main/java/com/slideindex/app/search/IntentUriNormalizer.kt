package com.slideindex.app.search

/**
 * 补全「只给了 `#Intent;…;end` 片段、却没带 `intent:` 前缀」的快捷方式串。
 *
 * 酷狗、支付宝这类 App 分享出来的快捷方式地址常常长这样：
 *
 * ```
 * kugousc://search#Intent;action=…;component=…;launchFlags=…;end
 * ```
 *
 * 它其实是 [android.content.Intent.toUri] 的输出被截掉了开头 7 个字符的形态
 * （完整形态就是同一个串带上 `intent:` 前缀）。而
 * [android.content.Intent.parseUri] 在带 `URI_INTENT_SCHEME` 时只接受 `intent:` 开头的串，
 * 否则整串会被当成普通 data 退化成 ACTION_VIEW；偏偏目标 App 通常没注册这个自定义 scheme，
 * 于是必然解析不到接收方，最后弹「操作失败」。
 *
 * 这里只补前缀、不改解析规则：调用方应先按原样解析，只有解析不到接收方时才用
 * [normalize] 再试一次，这样本来就能打开的链接不受影响。
 */
object IntentUriNormalizer {
    private const val INTENT_SCHEME = "intent:"
    private const val INTENT_FRAGMENT = "#Intent;"
    private const val INTENT_FRAGMENT_END = ";end"

    /** 片段前面的 data 部分必须是一条完整的 URI（`scheme://…`），否则多半是「句子 + 一条串」。 */
    private val DATA_PREFIX_REGEX = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*://""")

    /**
     * 形如 `kugousc://search#Intent;…;end` 的串补成 `intent:kugousc://search#Intent;…;end`。
     *
     * 已带 `intent:` / `android-app:` 前缀、网页链接、普通文本、Intent 片段不完整时原样返回。
     */
    fun normalize(uri: String): String {
        val candidate = uri.trim()
        if (candidate.isEmpty()) return candidate
        val scheme = schemeOf(candidate)
        // 已经是 intent URI 或 android-app URI，不能重复加前缀
        if (scheme == "intent" || scheme == "android-app") return candidate
        // 网页链接该交给浏览器打开，不当成缺前缀的 Intent 串
        if (scheme == "http" || scheme == "https") return candidate
        if (!isBareIntentFragment(candidate)) return candidate
        return INTENT_SCHEME + candidate
    }

    /**
     * 整串是否以 Intent 片段收尾：`#Intent;` 是最后一个 `#` 起始，内容一路写到 `;end` 结束。
     *
     * 片段里出现空白就说明这是整段文本而不是一条完整的串（例如用户粘了一句话进输入框）。
     */
    fun isBareIntentFragment(uri: String): Boolean {
        if (!uri.endsWith(INTENT_FRAGMENT_END)) return false
        val fragmentStart = uri.lastIndexOf('#')
        if (fragmentStart < 0) return false
        if (!uri.startsWith(INTENT_FRAGMENT, fragmentStart)) return false
        val data = uri.substring(0, fragmentStart)
        // data 要么为空，要么是一条完整 URI；否则是「句子 + 一条串」，补前缀只会拼出坏串
        if (data.isNotEmpty() && !DATA_PREFIX_REGEX.containsMatchIn(data)) return false
        return uri.substring(fragmentStart).none { it.isWhitespace() }
    }

    private fun schemeOf(uri: String): String =
        uri.substringBefore(':', missingDelimiterValue = "").lowercase()
}
