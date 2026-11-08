package com.slideindex.app.quickmenu

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 「文本工具」的计算核心：URL 编解码、Base64 编解码、SHA-256 摘要、正则匹配、AES-GCM 加解密。
 *
 * 纯 JVM 逻辑（不碰 Android API），可以直接单测；UI 只负责取值与展示。
 *
 * 为什么独立成一个工具：这些操作的对象都是"一段文本"，与生成密码无关。quickdash 把 SHA-256
 * 放在密码页里，我一开始照搬了；但那个位置确实不合理，所以拆出来。
 */
object TextToolkit {

    /** 解码结果。把"不是合法编码串"和"能解出字节但不是文本"分开，是为了能给出有意义的提示。 */
    sealed interface DecodeResult {
        data class Ok(val text: String) : DecodeResult

        /** 输入不是合法的编码串（例如 base64 里的非法字符、URL 里残缺的 `%`）。 */
        data object Malformed : DecodeResult

        /** 能解出字节，但解出来不是 UTF-8 文本（例如 base64 里其实是二进制/图片）。 */
        data object NotText : DecodeResult
    }

    // ---------------------------------------------------------------- URL

    fun urlEncode(input: String): String = java.net.URLEncoder.encode(input, Charsets.UTF_8.name())

    fun urlDecode(input: String): DecodeResult =
        runCatching { java.net.URLDecoder.decode(input, Charsets.UTF_8.name()) }
            .fold(
                onSuccess = { DecodeResult.Ok(it) },
                onFailure = { DecodeResult.Malformed },
            )

    // ---------------------------------------------------------------- Base64

    fun base64Encode(input: String): String =
        Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))

    fun base64Decode(input: String): DecodeResult {
        val bytes = runCatching { Base64.getDecoder().decode(input.trim()) }
            .getOrElse { return DecodeResult.Malformed }
        // 合法 base64 未必是文本：解出来再按 UTF-8 读，读不回去就说明它是二进制。
        // （String(bytes, UTF_8) 不会抛异常，只会塞替换字符，所以必须回读校验。）
        val text = String(bytes, Charsets.UTF_8)
        if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return DecodeResult.NotText
        return DecodeResult.Ok(text)
    }

    // ---------------------------------------------------------------- 文本对比

    /**
     * 两段文本的对比结论。
     *
     * **刻意不走哈希**：哈希只能说"同/不同"，说不出差在哪。直接比能给出位置、长度差，以及
     * "其实只差空白 / 只差大小写"这类最常命中的结论。
     */
    sealed interface TextCompareResult {
        /** 按当前口径完全相同。 */
        data object Identical : TextCompareResult

        /** 至少一侧为空，还没得比。 */
        data object EmptySide : TextCompareResult

        /**
         * 有差异。
         *
         * @param index 第一个不同字符的位置（0 基；一方是另一方前缀时等于较短串的长度）。
         * @param lengthDelta A 的长度 − B 的长度。
         * @param identicalIgnoringWhitespace 去掉空白与换行后相同（"看着一样其实尾部多了个换行"）。
         * @param identicalIgnoringCase 忽略大小写后相同。
         */
        data class Different(
            val index: Int,
            val lengthDelta: Int,
            val identicalIgnoringWhitespace: Boolean,
            val identicalIgnoringCase: Boolean,
        ) : TextCompareResult
    }

    /**
     * 直接比较两段文本。
     *
     * @param ignoreWhitespace 忽略空白与换行后再判定。注意 [TextCompareResult.Different.index]
     *   **始终基于原文**——那才是用户能在屏幕上对上的位置。
     */
    fun compareText(a: String, b: String, ignoreWhitespace: Boolean = false): TextCompareResult {
        if (a.isEmpty() || b.isEmpty()) return TextCompareResult.EmptySide

        val normalizedA = normalizeForCompare(a, ignoreWhitespace)
        val normalizedB = normalizeForCompare(b, ignoreWhitespace)
        if (normalizedA == normalizedB) return TextCompareResult.Identical

        return TextCompareResult.Different(
            index = firstDifferenceIndex(a, b),
            lengthDelta = a.length - b.length,
            identicalIgnoringWhitespace =
                normalizeForCompare(a, true) == normalizeForCompare(b, true),
            identicalIgnoringCase = normalizedA.equals(normalizedB, ignoreCase = true),
        )
    }

    private fun normalizeForCompare(text: String, ignoreWhitespace: Boolean): String =
        if (ignoreWhitespace) text.filterNot { it.isWhitespace() } else text

    private fun firstDifferenceIndex(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        for (index in 0 until limit) {
            if (a[index] != b[index]) return index
        }
        return limit
    }

    // ---------------------------------------------------------------- 正则

    /** 最多展示多少条匹配：防止 `.*` 之类在长文本上刷出上万条把界面拖死。 */
    const val MAX_REGEX_MATCHES = 50

    data class RegexMatch(
        val value: String,
        /** 在输入文本中的下标区间（闭区间，来自 [MatchResult.range]）。 */
        val range: IntRange,
        /** 捕获组 1..n 的值；没参与匹配的组为空串（与正则语义一致）。 */
        val groups: List<String>,
    )

    sealed interface RegexResult {
        data class Matches(val matches: List<RegexMatch>, val truncated: Boolean) : RegexResult

        data object NoMatch : RegexResult

        /** 正则本身写错了（例如 `[` 没闭合）。 */
        data class InvalidPattern(val message: String) : RegexResult
    }

    fun regexFind(pattern: String, input: String, ignoreCase: Boolean = false): RegexResult {
        if (pattern.isEmpty()) return RegexResult.NoMatch
        val regex = runCatching {
            Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }.getOrElse { error ->
            return RegexResult.InvalidPattern(error.message ?: "invalid pattern")
        }
        val found = regex.findAll(input).take(MAX_REGEX_MATCHES + 1).toList()
        if (found.isEmpty()) return RegexResult.NoMatch
        val truncated = found.size > MAX_REGEX_MATCHES
        return RegexResult.Matches(
            matches = found.take(MAX_REGEX_MATCHES).map { match ->
                RegexMatch(
                    value = match.value,
                    range = match.range,
                    groups = match.groupValues.drop(1),
                )
            },
            truncated = truncated,
        )
    }

    // ---------------------------------------------------------------- AES

    /**
     * ## 存储格式（v1）
     *
     * ```
     * v1:<base64( salt(16 字节) || iv(12 字节) || AES-GCM 密文与认证标签 )>
     * ```
     *
     * - 密钥：PBKDF2-HMAC-SHA256，`iterations = 120000`，输出 256 bit；
     * - 模式：`AES/GCM/NoPadding`，认证标签 128 bit —— **密文被改动会解密失败**，不会解出垃圾明文；
     * - salt 与 iv 每次加密都重新随机（因此同一明文两次加密结果不同），并随密文一起存放；
     * - `v1:` 前缀用于以后调整参数时识别旧数据。
     *
     * 只保证**本应用内部**往返一致；要与别的工具互通，需按上面的参数手工拼装。
     *
     * ⚠️ PBKDF2 迭代 12 万次在手机上约几百毫秒，所以界面必须"点按钮才算"，不能随输入实时算。
     */
    sealed interface AesResult {
        data class Ok(val text: String) : AesResult

        /** 载荷格式不对（前缀/base64/长度）。 */
        data object Malformed : AesResult

        /** 口令错，或密文被改动（GCM 认证失败，两者无法区分也不该区分）。 */
        data object WrongPasswordOrTampered : AesResult
    }

    private const val AES_VERSION = "v1"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_BITS = 256
    private const val MIN_PAYLOAD_BYTES = SALT_BYTES + IV_BYTES + 16

    private val secureRandom = SecureRandom()

    /**
     * @param salt / [iv] 仅测试注入用（固定后输出稳定，便于锁住格式）；正常调用不传，走随机。
     */
    fun aesEncrypt(
        password: String,
        plaintext: String,
        salt: ByteArray? = null,
        iv: ByteArray? = null,
    ): String {
        val usedSalt = salt ?: ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val usedIv = iv ?: ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, usedSalt),
            GCMParameterSpec(GCM_TAG_BITS, usedIv),
        )
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "$AES_VERSION:" +
            Base64.getEncoder().encodeToString(usedSalt + usedIv + cipherText)
    }

    fun aesDecrypt(password: String, payload: String): AesResult {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("$AES_VERSION:")) return AesResult.Malformed
        val packed = runCatching {
            Base64.getDecoder().decode(trimmed.removePrefix("$AES_VERSION:"))
        }.getOrElse { return AesResult.Malformed }
        if (packed.size < MIN_PAYLOAD_BYTES) return AesResult.Malformed

        val salt = packed.copyOfRange(0, SALT_BYTES)
        val iv = packed.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val cipherText = packed.copyOfRange(SALT_BYTES + IV_BYTES, packed.size)
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.fold(
            onSuccess = { AesResult.Ok(it) },
            // AEADBadTagException 在这里，就是"口令错或密文被改"。
            onFailure = { AesResult.WrongPasswordOrTampered },
        )
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
