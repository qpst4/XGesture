package com.slideindex.app.quickmenu

import java.security.SecureRandom
import kotlin.math.ln
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * 密码与摘要生成（「快捷菜单 → 密码生成」的计算核心）。
 *
 * 参考：QuickDash（https://github.com/balajitechlabs/quickdash，GPL-3.0）的 QuickPasswordScreen
 * 提供过同类能力（长度 8~32 + 数字/符号开关 + SHA-256 摘要）。本实现按其行为规格独立重写，
 * 并补上它缺的三件事：
 * 1. **字符类保证**。参考实现是"从合并字符池里纯随机抽"，即使打开了「包含数字」，生成的密码
 *    也可能一个数字都没有；本实现在抽满之后**强制每类至少出现一次**（长度不足时自动抬高到
 *    类别数），再整体洗牌。
 * 2. **强度/熵**。参考实现的文档写着 "entropy scoring"，但源码里并没有实现；本实现给出
 *    [entropyBits] 与 [strengthOf]，UI 可以直接显示强度条。
 * 3. **形近字符规避**。可选的 [Options.avoidAmbiguous] 去掉 `0 O 1 l I`，手抄密码时不被坑。
 *
 * 未实现（有意）：口令（passphrase）与 PIN。参考实现的注释提到过 PIN，源码里没有。
 *
 * 纯 JVM 逻辑，可脱离 Android 直接单测（[generate] 支持注入 [Random] 以便断言）。
 */
object PasswordGenerator {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 64

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?"

    /** 形近字符：`0/O/o`、`1/l/I`、`5/S`、`2/Z` 这类在等宽字体下容易看错的字符。 */
    private const val AMBIGUOUS = "0Oo1lI5S2Z8B"

    data class Options(
        val length: Int = 16,
        val uppercase: Boolean = true,
        val lowercase: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = false,
        val avoidAmbiguous: Boolean = false,
    ) {
        /** 一个字符类都没开时无法生成，UI 需要拦住这种状态。 */
        val hasNoCharacterClass: Boolean
            get() = !uppercase && !lowercase && !digits && !symbols
    }

    enum class Strength {
        WEAK,
        FAIR,
        STRONG,
        VERY_STRONG,
    }

    /**
     * 生成随机密码。
     *
     * @param random 随机源。默认 [SecureRandom]；单测可注入固定种子的 [Random] 以复现结果——
     *   **业务代码不要传非密码学随机的实现**。
     * @return 长度等于「夹取后的 [Options.length] 与已启用类别数中的较大者」的密码；
     *   一个类别都没启用时返回空串。
     */
    fun generate(options: Options = Options(), random: Random = SecureRandom().asKotlinRandom()): String {
        if (options.hasNoCharacterClass) return ""

        val pools = buildPools(options)
        if (pools.isEmpty()) return ""
        val pool = pools.joinToString("")
        // 至少要能放下"每类一个"，否则字符类保证不成立。
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH).coerceAtLeast(pools.size)

        val chars = ArrayList<Char>(length)
        // 1. 每类先各占一位，保证强度下限。
        for (classPool in pools) {
            chars += classPool[random.nextInt(classPool.length)]
        }
        // 2. 其余位置从合并池抽。
        repeat(length - chars.size) {
            chars += pool[random.nextInt(pool.length)]
        }
        // 3. Fisher–Yates 洗牌，否则前几位的类别可预测。
        for (index in chars.indices.reversed()) {
            val swapWith = random.nextInt(index + 1)
            if (index == swapWith) continue
            val tmp = chars[index]
            chars[index] = chars[swapWith]
            chars[swapWith] = tmp
        }
        return chars.joinToString("")
    }

    /** 候选字符池的总大小（用于熵估算）；一个类别都没开时为 0。 */
    fun poolSize(options: Options): Int = buildPools(options).sumOf { it.length }

    /**
     * 信息熵（比特）：`长度 × log2(池大小)`。
     *
     * 注意这是**理论上限**：它假设每个位置独立均匀分布。本实现在 `长度 == 类别数` 时会退化
     * （每类恰好一位），实际熵低于该值，因此 UI 只应把它当作强度提示而非精确度量。
     */
    fun entropyBits(options: Options): Double {
        val pool = poolSize(options)
        if (pool <= 1) return 0.0
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        return length * (ln(pool.toDouble()) / ln(2.0))
    }

    fun strengthOf(options: Options): Strength = when {
        entropyBits(options) < 40.0 -> Strength.WEAK
        entropyBits(options) < 60.0 -> Strength.FAIR
        entropyBits(options) < 80.0 -> Strength.STRONG
        else -> Strength.VERY_STRONG
    }

    /** 已启用的字符池（每个类别一个池），顺序即"先保证哪一类"。 */
    private fun buildPools(options: Options): List<String> {
        val ambiguous = if (options.avoidAmbiguous) AMBIGUOUS.toSet() else emptySet()
        fun filter(source: String): String = if (ambiguous.isEmpty()) source else source.filterNot { it in ambiguous }

        return buildList {
            if (options.uppercase) add(filter(UPPERCASE))
            if (options.lowercase) add(filter(LOWERCASE))
            if (options.digits) add(filter(DIGITS))
            if (options.symbols) add(filter(SYMBOLS))
        }.filter { it.isNotEmpty() }
        // 不需要额外兜底：AMBIGUOUS 与四个常量池都不是对方的子集，过滤后各池至少还剩 2 个字符；
        // 而"四个类别全关"的情况已由 hasNoCharacterClass 拦掉。
    }
}
