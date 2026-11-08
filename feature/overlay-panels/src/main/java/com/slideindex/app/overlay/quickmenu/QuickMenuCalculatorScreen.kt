package com.slideindex.app.overlay.quickmenu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.search.calculator.CalculatorUtils
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * 「快捷菜单」的计算器页：**正经计算器键盘**（照 QuickDash 的 `QuickCalculatorScreen` 的布局来做）。
 *
 * 两件事分开摆：
 *
 * 1. **纯逻辑在 [QuickMenuCalcPad] / [formatCalcResult] 里**（本文件顶部），没有任何 Compose 依赖，
 *    因此可以在 `QuickMenuCalculatorScreenTest` 里逐个键地单测（本模块既有的做法：
 *    页面只做"状态 + 渲染"，能验证的部分全抽成纯件）。
 * 2. **求值仍然复用搜索面板那套引擎** `CalculatorUtils.evaluateExpression`，不重写解析器 ——
 *    QuickDash 自带一个递归下降 `Parser`，我们已经有等价能力（还多了 `% of/off` 短语），
 *    但它的**键盘布局与交互**才是本次要照搬的东西，所以只搬布局，不搬引擎。
 *
 * 与 QuickDash 的差异（详见文件末尾的说明）主要有三处：去掉科学模式、`.5` 这类前导小数点照收、
 * 百分号按"把当前数除以 100"处理（QuickDash 那个 `Regex.replace` 会把整个表达式替换掉，是个 bug）。
 */

// ---------------------------------------------------------------- 纯逻辑：键盘状态机

/** 计算器上的一个按键。[text] 同时是键面文案与输入字符（`×` / `÷` 由引擎自己认）。 */
sealed interface CalcKey {
    data class Digit(val text: String) : CalcKey
    data class Op(val text: String) : CalcKey
    object Dot : CalcKey
    object OpenParen : CalcKey
    object CloseParen : CalcKey
    object Clear : CalcKey
    object Backspace : CalcKey
    object ToggleSign : CalcKey
    object Percent : CalcKey
    object Sqrt : CalcKey
    object Equals : CalcKey
}

/**
 * 「上一次计算」：一条 `算式 = 结果` 的快照。
 *
 * 存**两个**字段而不是只存结果，是因为显示区要按用户要求写成 `12+5 = 17` 的完整形态；
 * 只存结果的话，按下等号之后 `expression` 已经被结果覆盖，就再也拼不出左边那半截了。
 */
data class CalcLastEntry(val expression: String, val result: String) {
    /** 显示文案：`12+5 = 17`。 */
    val label: String get() = "$expression = $result"
}

/**
 * 计算器的输入状态。
 *
 * [expression] 是喂给求值引擎的完整表达式（运算符一律用 `×` / `÷`，与引擎的 `cleanExpression` 对应）；
 * [display] 是主显示区当前那一个数的文本。两者分开，是因为"显示"与"表达式"在真实计算器上语义不同：
 * 按 `+` 之后主显示该清成 0，但表达式要留着 `12+`。按下等号时**只把结果写进 [display]**，
 * `expression` 继续留着刚算完的那个算式 —— 下一次按运算符时正是靠它拼出 `12+5 = 17` 那一条记录
 * （见 [QuickMenuCalcPad.pressOperator]；若这里用结果把 `expression` 覆盖掉，左半截就找不回来了）。
 *
 * [lastCalc] 是显示区最上方那一行"上一次计算"（`12+5 = 17`），点它可把上次结果填回算式。
 * 它**由状态机在按下 `=` 确认收尾时归档**（见 [QuickMenuCalcPad.equals]），不算不出来/`算式 == 结果`
 * 的情况不归档；归档后一直留着方便回去取，只有 `C` 会清掉。
 *
 * 不变量：除"刚求值完"（此时 [display] 是结果、[expression] 是刚算完的算式）与"表达式为空"这两种
 * 情形外，[expression] 一定以 [display] 结尾。
 *
 * 刚按下运算符、或运算符后面只按了一个负号（`12+-`）时，[display] 会是 `0` / `-` 这种"还没有数字"的形态。
 */
data class CalcPadState(
    val expression: String = "",
    val display: String = "0",
    val justEvaluated: Boolean = false,
    val lastCalc: CalcLastEntry? = null,
)

/** 一次按键的产物：新状态，以及（按下等号且算成功时）这次的结果快照。 */
data class CalcPadOutcome(val state: CalcPadState, val lastResult: String? = null)

/** 显示的位数上限：超出就截断，免得一个 `1/3` 把面板撑成一行乱码。 */
private const val MAX_DISPLAY_CHARS = 24

/**
 * 结果文本的角色：数字才能继续参与运算，也才允许复制。
 *
 * `CalculatorUtils.formatResult` 在"末位被截断"时会追加一个 `…`（例如 `1/3 → 0.333…`），
 * 带省略号的值是**展示用的近似值**，不能再当表达式的一部分用，否则下一键就算出个错数。
 */
private fun isNumericResult(text: String): Boolean {
    // 开头的负号是数的一部分（`0-5` 的结果就是 `-5`），后面的字符只许是数字或小数点。
    val body = text.removePrefix("-")
    return body.isNotEmpty() && body.all { it.isDigit() || it == '.' }
}

/**
 * 把引擎给出的结果整成主显示区能用的文本；不是数（含"末位被截断"的近似值）时返回 null。
 *
 * null 在界面上表现为"当前无法求值"，不是错误弹窗 —— 边打边算时表达式本来就大半是半成品。
 */
fun formatCalcResult(raw: String?): String? {
    val cleaned = raw?.trim()?.replace(" ", "")?.takeIf { it.isNotEmpty() } ?: return null
    if (cleaned.length > MAX_DISPLAY_CHARS) return null
    return cleaned.takeIf(::isNumericResult)
}

/** 二元运算符（`-` 既可能是减号也可能是下一个操作数的负号，由它所处的位置决定）。 */
private fun isBinOpChar(ch: Char): Boolean = ch == '+' || ch == '-' || ch == '×' || ch == '÷'

/**
 * 表达式末尾那个"当前操作数"的起点。
 *
 * 只吃数字与小数点，负号归运算符那一段：`12+-7` 的末尾操作数是 `7`，`-` 单独处理。
 * 空串表示光标正停在运算符/括号后面，此时"没有操作数可翻/可百分号化"。
 */
private fun currentNumberStart(expression: String): Int {
    var index = expression.length
    while (index > 0) {
        val ch = expression[index - 1]
        if (ch.isDigit() || ch == '.') index-- else break
    }
    return index
}

/**
 * 末尾那个**带符号**操作数的起点：在 [currentNumberStart] 基础上，把紧挨着的负号一起吃进来。
 *
 * 判据是"负号前面是另一个运算符/左括号/什么都没有"，也就是它确实是**这个操作数的符号**，
 * 而不是 `12-7` 里那种二元减号 —— 后者不作数，正负号键会另外补一个负号变成 `12--7`
 *（引擎按 12 - (-7) 读，得 19；QuickDash 也是这个路子）。
 */
private fun signedNumberStart(expression: String): Int {
    val numberStart = currentNumberStart(expression)
    if (numberStart <= 0 || expression[numberStart - 1] != '-') return numberStart
    val signIndex = numberStart - 1
    val before = if (signIndex > 0) expression[signIndex - 1] else null
    return if (before == null || before == '(' || isBinOpChar(before)) signIndex else numberStart
}

/** 末尾"带符号操作数"的信息：`12+-7` → prefix=`12+`、signed=`-7`；`12+` → prefix=`12+`、signed=``。 */
private data class TrailingSignedNumber(val prefix: String, val signed: String) {
    /** 光是一个负号、或什么都没有：还没有真正的数字，`±` 只能挂上/摘掉那个负号。 */
    val hasDigits: Boolean get() = signed.any { it.isDigit() }
}

private fun trailingSignedNumber(expression: String): TrailingSignedNumber {
    val start = signedNumberStart(expression)
    return TrailingSignedNumber(expression.substring(0, start), expression.substring(start))
}

/** 表达式末尾那个"当前操作数"的文本（可能是空串，例如刚按下运算符）。 */
private fun currentNumberText(expression: String): String = expression.substring(currentNumberStart(expression))

/**
 * 主显示区该显示的那个数的文本：末尾那个操作数，**带上它自己的负号**。
 *
 * 与 [currentNumberText] 只差一个符号：`12-7` 里那个 `-` 是二元减号（末尾操作数是 `7`），
 * 而 `5×-3` 里那个 `-` 是 3 的符号（末尾操作数是 `-3`）。主显示区显示的是"用户正在输入的那个数"，
 * 所以符号得跟着一起显示 —— 早先这里一律用 [currentNumberText]，于是按 `±` 再输数字时显示区会
 * 把负号丢掉（`5×-3` 显示成 `3`、`5×-0.5` 显示成 `0.5`），用户看到的数和真正参与运算的数对不上。
 */
private fun trailingOperandText(expression: String): String = expression.substring(signedNumberStart(expression))

/** 把末尾那个操作数整体换成 [replacement]（百分号、正负号、退格都靠它）。 */
private fun replaceCurrentNumber(expression: String, replacement: String): String =
    expression.substring(0, currentNumberStart(expression)) + replacement

/** 操作数是不是"普通"数字（排除空串、`-` 光杆负号、含 `%` 这类已经变换过的形态）。 */
private fun isPlainNumber(text: String): Boolean {
    val digits = if (text.startsWith("-")) text.drop(1) else text
    return digits.isNotEmpty() && digits.all { it.isDigit() || it == '.' }
}

/**
 * 表达式里还有没有**未配对**的左括号（从右往左扫：闭括号先抵消掉，遇到第一个左括号就是它）。
 *
 * 按 `)` 前先问这个：没有未配对的 `(` 就不该插一个孤零零的右括号。
 */
private fun hasUnmatchedOpenParen(expression: String): Boolean {
    var depth = 0
    for (index in expression.indices.reversed()) {
        when (expression[index]) {
            ')' -> depth++
            '(' -> if (depth == 0) return true else depth--
        }
    }
    return false
}

/**
 * 计算器的按键状态机：`(当前状态, 按键) -> 新状态`。
 *
 * 纯函数（没有 Android / Compose 依赖、没有随机、没有 IO），所以每个键的行为都能在 JVM 单测里钉住。
 */
object QuickMenuCalcPad {

    /**
     * 只换算式与主显示、**其余字段原样保留**（尤其是「上一次计算」那一行）。
     *
     * 用它而不是各处手写 `CalcPadState(...)`：早先每个按键都从头 new 一个状态，凡是新增字段
     * （比如这次的 `lastCalc`）就会在漏改的那几处被悄悄清掉。现在"保留"是默认行为，
     * 只有明确要清空的地方（`C`）才显式写 `CalcPadState()`。
     */
    private fun CalcPadState.with(expression: String, display: String, justEvaluated: Boolean = false): CalcPadState =
        copy(expression = expression, display = display, justEvaluated = justEvaluated)

    private fun outcome(state: CalcPadState, expression: String, display: String): CalcPadOutcome =
        CalcPadOutcome(state.with(expression, display))

    fun press(state: CalcPadState, key: CalcKey): CalcPadOutcome = when (key) {
        is CalcKey.Digit -> startTyping(state, key.text)
        CalcKey.Dot -> pressDot(state)
        is CalcKey.Op -> pressOperator(state, key.text)
        CalcKey.OpenParen -> pressOpenParen(state)
        CalcKey.CloseParen -> pressCloseParen(state)
        // `C` 是唯一会清掉「上一次计算」的键：用户按它就是想从头来，还挂着旧结果会莫名其妙
        CalcKey.Clear -> CalcPadOutcome(CalcPadState())
        CalcKey.Backspace -> CalcPadOutcome(backspace(state))
        CalcKey.ToggleSign -> CalcPadOutcome(toggleSign(state))
        CalcKey.Percent -> CalcPadOutcome(percent(state))
        CalcKey.Sqrt -> CalcPadOutcome(squareRoot(state))
        CalcKey.Equals -> equals(state)
    }

    /** 数字键：求值之后是"另起一个新算式"，否则接到当前操作数后面。 */
    private fun startTyping(state: CalcPadState, digit: String): CalcPadOutcome {
        val expression = if (state.justEvaluated) "" else state.expression
        // 只在"当前操作数正好是 0"时改写它，避免 `0` 后面直接接出 `05`（`10` 里的 0 不受影响：
        // 那时末尾操作数是 `10`，不是 `0`）。
        val next = if (currentNumberText(expression) == "0") {
            replaceCurrentNumber(expression, digit)
        } else {
            expression + digit
        }
        return outcome(state, next, trailingOperandText(next))
    }

    /**
     * 小数点。
     *
     * 与 QuickDash 不同：它用 `if (!display.contains('.'))` 挡前导小数点，于是 `5×.` 按不出 `.5`
     * （display 此时是 `0`、表达式已经是 `5×`）。这里改成只看**末尾那个操作数**，既挡住
     * `1.2.3`，也允许 `5×.5`。
     */
    private fun pressDot(state: CalcPadState): CalcPadOutcome {
        val expression = if (state.justEvaluated) "" else state.expression
        val current = currentNumberText(expression)
        // 已经有点、或末尾还挂着 `%`（不再是纯数字形态）时不再插点。
        if (current.contains('.') || current.endsWith("%")) {
            return outcome(state, expression, trailingOperandText(expression).ifEmpty { "0" })
        }
        // 末尾还不是数字（表达式为空、或以运算符/左括号结尾）：先补一个 `0`，`5×.5` 才成立。
        // 判据看**不带符号**的末尾数字（`5×-` 的当前操作数是空串），补出来的 `0.` 接在负号后面
        // （`5×-` + `.` → `5×-0.`），负号本来就是表达式的一部分，不会被 `.` 顶掉。
        val next = if (current.isEmpty()) expression + "0." else "$expression."
        return outcome(state, next, trailingOperandText(next))
    }

    /**
     * 运算符键：连按只保留最后一个（QuickDash 的 `trimEnd` 行为），求值之后则在结果上接着算。
     *
     * 只顶掉**一个**末尾运算符，而不是 `trimEnd` 掉一串：`5×-` 里的 `-` 是下一个操作数的负号，
     * 一口气吃掉 `-` 和 `×` 会把它变成孤零零的 `5+`。
     */
    private fun pressOperator(state: CalcPadState, op: String): CalcPadOutcome {
        // 求值完成后按下运算符：把结果当新表达式的起点（`2+3=` 再按 `×` → `5×`）。
        val base = if (state.justEvaluated) state.display else state.expression
        val last = base.lastOrNull()
        val trimmed = if (last != null && isBinOpChar(last)) base.dropLast(1) else base
        val expression = trimmed + op
        return CalcPadOutcome(state.with(expression = expression, display = "0"))
    }

    /**
     * 点「上一次计算」那一行：把上次的结果当作当前算式填回去（"接着算"）。
     *
     * 结果状态与按下等号之后一致（`justEvaluated = true`），所以接着按运算符就能从这个结果继续算；
     * 那一行本身保留不动，可以反复取用。
     */
    fun openLastCalculation(state: CalcPadState): CalcPadState {
        val entry = state.lastCalc ?: return state
        return state.with(expression = entry.result, display = entry.result, justEvaluated = true)
    }

    private fun pressOpenParen(state: CalcPadState): CalcPadOutcome {
        val base = if (state.justEvaluated) "" else state.expression
        val expression = base + "("
        return outcome(state, expression, "0")
    }

    /**
     * 右括号只在"有未配对的左括号"时才插；插完主显示归 0，等待下一个操作数。
     *
     * 没有未配对的 `(` 时**整个状态原样返回**（既不算"开始新输入"、也不改显示）：早先这里仍旧
     * 走一遍 `outcome(..., "0")`，于是 `12` 按 `)` 会变成"算式还是 `12`、主显示却清成 `0`"，
     * 与"主显示永远等于算式末尾操作数"这条约定打架；求值完之后按它更会被当成开始新算式。
     */
    private fun pressCloseParen(state: CalcPadState): CalcPadOutcome {
        val base = if (state.justEvaluated) "" else state.expression
        if (!hasUnmatchedOpenParen(base)) return CalcPadOutcome(state)
        return outcome(state, "$base)", "0")
    }

    /**
     * 退格：求值完后相当于清空重来；否则删掉表达式最后一个字符，并把主显示重算成新的末尾操作数。
     *
     * QuickDash 是"display 与 expression 各删一个字符"，两者会越走越偏（`12+3` 退格后
     * display 变成 `1`、expression 变成 `12+`，却谁也说不清主显示到底代表什么）。这里只改表达式，
     * 主显示始终是表达式的末尾操作数，所以按下 `+` 之后退格会正确地回到上一个数。
     */
    private fun backspace(state: CalcPadState): CalcPadState {
        // 求值完之后退格：算式归零重来，但**保留**「上一次计算」那一行（刚归档的那条正是拿来接着算的）
        if (state.justEvaluated) return state.with(expression = "", display = "0")
        val expression = state.expression.dropLast(1)
        // 整段退空了，或只剩一个孤零零的负号（`-`）：算式回到空，同样不清「上一次计算」。
        // 注意只在**整个表达式**就是 `-` 时才复位：`5×-` 退格应当得到 `5×`，不是全清。
        if (expression.isEmpty() || expression == "-") return state.with(expression = "", display = "0")
        return state.with(expression = expression, display = trailingOperandText(expression).ifEmpty { "0" })
    }

    /**
     * 正负号：只翻当前操作数的符号。
     *
     * 三种情形分开处理（早先的版本把它们混在一起，于是 `5×±` 变成空操作、`12-7` 按一次会看成
     * "已经有负号了、取消掉"，结果变成 `127`）：
     * - 还没有数字、也没有负号（`12+`）：补一个负号 → `12+-`，等用户接着输数字；
     * - 已经有负号（`-9` / `12+-7` / `5×-`）：取消它（`5×-` 是"只挂着一个负号、还没有数字"的形态，
     *   同样算"已经有负号"，再按一次回到 `5×` —— 否则这个键按下去毫无反应，成了死键）；
     * - 普通数字（`7` / `12-7`）：补一个负号 → `12+-7` / `12--7`（引擎按 12-(-7) 读，等于 19）。
     *
     * 判据必须落在"末尾这个操作数**自己**有没有负号"（[signedNumberStart] 已经排除了二元减号），
     * 不能去看它前面那个字符：`12-7` 前面是减号，可它并不是 7 的符号。
     */
    private fun toggleSign(state: CalcPadState): CalcPadState {
        val expression = if (state.justEvaluated) "" else state.expression
        if (expression.isEmpty()) return state

        val tail = trailingSignedNumber(expression)
        val next = when {
            // 末尾是运算符/左括号、还没有任何数字：`±` 是"翻符号"，挂上负号；已经挂着一个就取消它
            //（既不能攒成 `12+--`，也不能让这一按变成空操作）
            !tail.hasDigits -> if (tail.signed == "-") tail.prefix else tail.prefix + "-"
            // 已经有负号：取消它（`12+-7` → `12+7`）
            tail.signed.startsWith("-") -> tail.prefix + tail.signed.drop(1)
            // 还没有负号：补一个（`12+7` → `12+-7`）
            else -> tail.prefix + "-" + tail.signed
        }
        return state.with(expression = next, display = trailingOperandText(next))
    }

    /**
     * 百分号：把**当前操作数**除以 100。
     *
     * 这里刻意不学 QuickDash：它用 `Regex("""(-?\d+\.?\d*)$""").replace(expression) { display }`
     * 替换末尾，`display` 又被赋值成百分号化之后的整个数，于是 `100+50` 按 `%` 会得到 `0.5`
     * （整个算式被替换掉）。我们只换末尾操作数，`100+50` → `100+0.5`，行为可预期。
     */
    private fun percent(state: CalcPadState): CalcPadState {
        val expression = if (state.justEvaluated) "" else state.expression
        val number = currentNumberText(expression)
        if (!isPlainNumber(number)) return state

        val value = number.toBigDecimalOrNull() ?: return state
        val scaled = value.movePointLeft(2).stripTrailingZeros().toPlainString()
        val next = replaceCurrentNumber(expression, scaled)
        // 负号留在表达式里（`replaceCurrentNumber` 只换末尾那串数字），所以显示要按带符号的操作数取。
        return state.with(expression = next, display = trailingOperandText(next))
    }

    /** 开方：整段表达式先求值再开方（QuickDash 是对主显示那个数开方，两者在单操作数下等价）。 */
    private fun squareRoot(state: CalcPadState): CalcPadState {
        val expression = if (state.justEvaluated) "" else state.expression
        if (expression.isEmpty()) return state

        val value = CalculatorUtils.evaluateExpression(expression)?.toDoubleOrNull() ?: return state
        if (!value.isFinite() || value < 0.0) return state

        // `kotlin.math.sqrt(9.0).toString()` 是 `3.0`：整数结果去掉那个 `.0`，和引擎的写法一致
        //（引擎 `9÷3` 给的是 `3`），也免得显示区出现 `3.0 = 3.0` 这种废话。
        val rootText = kotlin.math.sqrt(value).toString().removeSuffix(".0")
        val root = formatCalcResult(rootText) ?: return state
        return state.with(expression = root, display = root, justEvaluated = true)
    }

    /**
     * 等号：表达式不完整（末尾是运算符）或引擎算不出来时，保持原样（也就不归档）。
     *
     * `=` 是**确认收尾**，不是"算出结果的必要动作"：结果早在算式完整时就实时显示在大数字上了。
     * 它做三件事：① 把 `算式 = 结果` 归档成显示区最顶那一行「上一次计算」；② 大数字保持结果；
     * ③ 界面据此把复制按钮放出来（实时结果不算"确认过的结果"）。
     * 结果写进 [CalcPadState.display]，而 [CalcPadState.expression] **保留刚算完的那个算式** ——
     * 归档行左边那半截就靠它（若用结果把 `expression` 覆盖掉，就只剩 `1.5 = 1.5` 了）。
     *
     * 不归档 `算式 == 结果` 的情况（`5=`、以及 `√9` 之后再按 `=`）：那会写出 `5 = 5`、`3 = 3` 这种
     * 毫无信息的垃圾行，还会把上一条有用的记录挤掉。
     * [CalcPadOutcome.lastResult] 只用来让调用方（界面/测试）知道"这一次真的算出来了"。
     */
    private fun equals(state: CalcPadState): CalcPadOutcome {
        val expression = state.expression
        if (expression.isEmpty() || isBinOpChar(expression.last())) return CalcPadOutcome(state)

        val result = formatCalcResult(CalculatorUtils.evaluateExpression(expression))
            ?: return CalcPadOutcome(state)
        val archived = if (expression != result) {
            CalcLastEntry(expression = expression, result = result)
        } else {
            state.lastCalc
        }
        return CalcPadOutcome(
            state = state.copy(
                expression = expression,
                display = result,
                justEvaluated = true,
                lastCalc = archived,
            ),
            lastResult = result,
        )
    }
}

// ---------------------------------------------------------------- 显示区模型

/**
 * 显示区该画什么（最多三行 + 复制按钮的开关），**由纯逻辑算好**，[QuickMenuCalculatorScreen] 只负责画。
 *
 * 抽出来的原因：显示区曾经同时堆过三个数字（算式 `6÷4` + 大数字 `4` + 实时预览 `= 1.5`），
 * 用户根本看不出哪个才是结果。这不是"要不要实时结果"的问题，而是**层级**问题：算式是配角的细字、
 * 结果是主角的大字，同一个数字不重复出现两遍。规则统一收到这里之后能在 JVM 单测里钉住
 *（见 `QuickMenuCalculatorScreenTest.display_*`）。
 */
data class CalcDisplayModel(
    /** 最顶上那一行「上一次计算」（`12+5 = 17`）；null = 没有，整行不渲染、不占位。 */
    val lastCalcLabel: String?,
    /**
     * 算式行（小字配角）；null = 这一行不渲染、不占位。只在"已经输入过运算符/左括号"时才有，
     * 按下 `=` 之后也没有 —— 那时 `算式 = 结果` 已经由最顶那一行的归档接管了，别再重复一遍。
     */
    val expressionLine: String?,
    /** 大数字（主角）：算式完整可求值 → 实时结果；否则 → 正在输入的那个数。 */
    val bigNumber: String,
    /**
     * 复制按钮：**大数字是"算出来的值"就给**（实时结果、按 `=` 之后、`√` 的结果），
     * 大数字只是"用户正在输入的那个数"就不给（输入不是结果），结果 `0` 也不给。
     */
    val showCopy: Boolean,
)

/**
 * 由状态机状态算出显示区该画什么（逐态规则，对齐 Google 计算器的语义）：
 *
 * | 状态 | 算式行 | 大数字 | 复制按钮 |
 * | --- | --- | --- | --- |
 * | ① 只打了数字（`6`） | 不显示 | `6`（正在输入的数） | 无 |
 * | ② 输入过运算符、算式没算完（`6÷` / `6+` / `6÷(`） | `6÷` | 还在输入的那个数；末尾还没开始输就回显左边那半截的值（`6`），**绝不显示半成品结果** | 无 |
 * | ③ 算式完整可求值（`6÷4`） | `6÷4` | `1.5`（实时结果，不用按 `=`） | **有** |
 * | ④ 按了 `=` | 不显示（归档行接管） | `1.5`（结果） | 有 |
 *
 * 大数字里那个"正在输入的数"永远优先于"实时结果"的一种情形：末尾操作数带显式负号（用户刚按过 `±`
 * 在输一个负数，如 `5×-3`）—— 那时显示区要跟着那个负数走（`-3`），否则 `±` 按下去用户看不到反馈。
 */
fun calcDisplayModel(state: CalcPadState): CalcDisplayModel {
    val big = bigNumber(state)
    return CalcDisplayModel(
        lastCalcLabel = state.lastCalc?.label,
        // 刚求值完（`=` / `√`）：`算式 = 结果` 已经归档到最顶那一行了，算式行不再重复；`√9` 那种
        // 没归档的情况（expression == display）算式行也没必要再显示一遍同一个数。
        // 没求值完：只有"输入过运算符/左括号"才把算式行拿出来（只打数字时不显示、不占位）。
        expressionLine = when {
            state.justEvaluated -> null
            hasEnteredOperator(state.expression) -> state.expression
            else -> null
        },
        bigNumber = big.text,
        // 判据是"这个数从哪来"，不是"按没按过 `=`"：算出来的值才可复制，正在输入的数不可复制；
        // 结果 0 没什么可复制的，也不给。
        showCopy = big.fromEvaluation && big.text != "0",
    )
}

/** 用户是不是已经输入过运算符（或左括号）——决定算式行要不要出现。`12+` 退格回 `12` 时又会收起来。 */
private fun hasEnteredOperator(expression: String): Boolean =
    expression.any { isBinOpChar(it) || it == '(' }

/** 大数字的值，外加"它是算出来的还是用户正在输入的"——复制按钮就靠这个标志（[CalcDisplayModel.showCopy]）。 */
private data class BigNumberValue(val text: String, val fromEvaluation: Boolean)

/**
 * 大数字该显示什么、以及它是不是"算出来的值"：**算式完整可求值就显示实时结果，否则显示正在输入的那个数**。
 *
 * 按优先级来：
 * 1. 刚求值完（`=` / `√`）：[CalcPadState.display] 里就是结果 —— 算出来的值；
 * 2. 末尾操作数带显式负号（`5×-3`）：回显它（用户按 `±` 就是在改这个数，不能换成整个算式的结果），
 *    这是"正在输入的数"；
 * 3. 算式算得出来：实时结果（`6÷4` 没按 `=` 也直接看到 `1.5`）—— 只有算式里**真的有运算符/左括号**
 *    才算"算出来的值"：光打一个 `6` 时它的求值结果就是那个数本身，那是用户输入，不该给复制按钮；
 * 4. 算不出来：回显末尾操作数（`6÷0` 就是 `0`），还是"正在输入的数"；末尾还没开始输（`6÷` / `6÷(`）
 *    就回显左边那半截的值（`6`）—— 算式都没算完，同样算输入态，用户刚输的数不会从屏幕上消失；
 * 5. 连左边都算不出来（`(`）：退回状态里的 display（按下运算符时它就是 `0`）。
 */
private fun bigNumber(state: CalcPadState): BigNumberValue {
    if (state.justEvaluated) return BigNumberValue(state.display.ifEmpty { "0" }, fromEvaluation = true)

    val expression = state.expression
    val operand = trailingOperandText(expression)
    if (operand.startsWith("-") && isOperandEcho(operand)) {
        return BigNumberValue(operand, fromEvaluation = false)
    }

    formatLiveResult(CalculatorUtils.evaluateExpression(expression))?.let {
        return BigNumberValue(it, fromEvaluation = hasEnteredOperator(expression))
    }
    if (isOperandEcho(operand)) return BigNumberValue(operand, fromEvaluation = false)
    valueBeforeTrailingOperators(expression)?.let { return BigNumberValue(it, fromEvaluation = false) }
    return BigNumberValue(state.display.ifEmpty { "0" }, fromEvaluation = false)
}

/** 末尾操作数是不是"用户正在输入的那个数"的形态：`4` / `0.` / `-3`，或光杆负号 `-`。 */
private fun isOperandEcho(text: String): Boolean =
    text == "-" || (text.any { it.isDigit() } && text.all { it.isDigit() || it == '.' || it == '-' })

/**
 * 末尾那个操作数还没开始输（刚按过运算符 / `(`）时，大数字要回显的值：把末尾这些运算符与左括号去掉，
 * 看左边那半截值多少 —— `6÷` 显示 `6`、`(1+2)×` 显示 `3`。连左边都算不出来（`(`）时返回 null。
 */
private fun valueBeforeTrailingOperators(expression: String): String? {
    var end = expression.length
    while (end > 0 && (isBinOpChar(expression[end - 1]) || expression[end - 1] == '(')) end--
    if (end == 0) return null
    return formatLiveResult(CalculatorUtils.evaluateExpression(expression.substring(0, end)))
}

/**
 * 大数字能显示的实时结果：引擎给出来的数都行，包括"末位被截断"的近似值（`1÷3 → 0.333333333333…`）。
 *
 * 比 [formatCalcResult] 宽松是故意的：那个更严 —— 它管的是"这个结果还能不能当表达式继续参与运算"
 * （带 `…` 的近似值不能再算），而显示区只是给人看，`1÷3` 应该看到 `0.333…` 而不是什么也看不到。
 * 报错（`NaN` / 异常）在引擎里返回 null，压根到不了这里。
 */
private fun formatLiveResult(raw: String?): String? {
    val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (cleaned.length > MAX_DISPLAY_CHARS) return null
    val digits = cleaned.removeSuffix("…").removePrefix("-")
    return cleaned.takeIf { digits.isNotEmpty() && digits.all { it.isDigit() || it == '.' } }
}

// ---------------------------------------------------------------- 屏幕

/**
 * 计算器页。显示区在上方（一律右对齐），网格键盘在下。逐态规则（对齐 Google 计算器的语义）
 * 全在纯逻辑 [calcDisplayModel] 里，这里只负责画：
 * ```
 * ① 只打了数字          ② 输入过运算符、还没算完   ③ 算式完整可求值        ④ 按了 `=`
 *    7×2 = 14              7×2 = 14                 7×2 = 14              6÷4 = 1.5
 *    6                     6÷                       6÷4        [复制]     1.5  [复制]
 *                          6                        1.5
 * ```
 * 要点：算式行**只在输入过运算符之后才出现**（只打数字时不渲染、不占位）；大数字是**算式完整可求值
 * 时的实时结果**，否则是正在输入的那个数（算式没算完绝不给半成品结果）；复制按钮的判据是**大数字
 * 从哪来** —— 算出来的值（实时结果 / `=` 之后 / `√`）才给，正在输入的数不给；`=` 只做"确认收尾"，
 * 把 `算式 = 结果` 归档到最顶那一行（那时算式行收起，避免同一个结果写两遍），大数字保持结果。
 *
 * 键盘布局（每行 4 格，等号占两格）：
 * ```
 *  C    (    )    ⌫
 *  7    8    9    ÷
 *  4    5    6    ×
 *  1    2    3    -
 *  0    .    ±    %
 *  ==========   +    √
 * ```
 * 数字键（含 `.`）用最高一档表面色、功能键（`C` `(` `)` `⌫` `√` `±` `%`）用次一级容器色、
 * 四则运算符用主容器色、等号用 primary 实心 —— 一眼能分出"数"和"算"。
 */
@Composable
fun QuickMenuCalculatorScreen() {
    val context = LocalContext.current
    var pad by remember { mutableStateOf(CalcPadState()) }

    fun press(key: CalcKey) {
        pad = QuickMenuCalcPad.press(pad, key).state
    }

    // 显示区每一行的内容与开关全部由纯逻辑（calcDisplayModel）算好，这里只负责画。
    val model = calcDisplayModel(pad)

    ToolScaffold {
        // ── 显示区 ────────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 「上一次计算」：只有记下过一条时才渲染这一行（没有就不占位、不留空白）。
                // 点它 = 把上次的结果填回算式接着算。整行可点，所以给足上下内边距（别让手指点在字缝里）。
                if (model.lastCalcLabel != null) {
                    Text(
                        text = model.lastCalcLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { pad = QuickMenuCalcPad.openLastCalculation(pad) }
                            .semantics {
                                contentDescription = "上一次计算 ${model.lastCalcLabel}，点击把结果填回算式"
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    )
                }
                // 算式行（配角小字）：只在"已经输入过运算符/左括号"时才有，只打数字时不渲染、不占位；
                // 按下 `=` 之后也没有 —— 那时 `算式 = 结果` 由最顶那一行的归档写着，不再重复一遍。
                // 复制按钮**不挂这一行**（复制半成品算式没有意义），它跟在大数字那一行上。
                if (model.expressionLine != null) {
                    Text(
                        text = model.expressionLine,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = model.bigNumber,
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // 只有大数字是"算出来的值"时才给复制按钮（正在输入的数、以及结果 0 都不给）。
                    if (model.showCopy) {
                        IconButton(
                            onClick = { copyCalcResult(context, model.bigNumber) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Medium.Copy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        // 占位：不出按钮时也留出同样宽的位置，免得数字随状态左右抖一下。
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        // ── 键盘 ──────────────────────────────────────────────────────────
        // 5 行 × 4 格，外加一行"等号占两格 + `+` + `√`"。每个键都必须在这一串里出现一次，
        // 否则状态机里实现了却没有入口。
        val rows = listOf(
            listOf<CalcKey>(
                CalcKey.Clear,
                CalcKey.OpenParen,
                CalcKey.CloseParen,
                CalcKey.Backspace,
            ),
            listOf(CalcKey.Digit("7"), CalcKey.Digit("8"), CalcKey.Digit("9"), CalcKey.Op("÷")),
            listOf(CalcKey.Digit("4"), CalcKey.Digit("5"), CalcKey.Digit("6"), CalcKey.Op("×")),
            listOf(CalcKey.Digit("1"), CalcKey.Digit("2"), CalcKey.Digit("3"), CalcKey.Op("-")),
            listOf(CalcKey.Digit("0"), CalcKey.Dot, CalcKey.ToggleSign, CalcKey.Percent),
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
            ) {
                row.forEach { key -> CalcKeyButton(key = key, modifier = Modifier.weight(1f)) { press(key) } }
            }
        }

        // 等号占两格：与"每行 4 格"的网格对齐（2 格等号 + `+` + `√`），同时比别的键宽一倍 ——
        // QuickDash 的等号是圆钮，我们这里用"更宽 + primary 实心"让它醒目。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
        ) {
            CalcKeyButton(
                key = CalcKey.Equals,
                modifier = Modifier.weight(2f),
                onClick = { press(CalcKey.Equals) },
            )
            CalcKeyButton(
                key = CalcKey.Op("+"),
                modifier = Modifier.weight(1f),
                onClick = { press(CalcKey.Op("+")) },
            )
            CalcKeyButton(
                key = CalcKey.Sqrt,
                modifier = Modifier.weight(1f),
                onClick = { press(CalcKey.Sqrt) },
            )
        }
    }
}

/** 键高：面板是"聚合浮层"，键要够大才好点，但不给 aspectRatio（浮层宽度不定，方键会撑高整页）。 */
private val KEY_HEIGHT = 56.dp

/** 键间距：卡片之间留缝，视觉上才是"网格"而不是"一整块"。 */
private val KEY_GAP = 8.dp

/** 键面文案：一律用符号/数字/图标，不新增字符串资源（`⌫` 用图标，其余都是国际通用符号）。 */
private fun keyLabelOf(key: CalcKey): String = when (key) {
    CalcKey.Clear -> "C"
    CalcKey.Backspace -> ""
    CalcKey.OpenParen -> "("
    CalcKey.CloseParen -> ")"
    CalcKey.Percent -> "%"
    CalcKey.Sqrt -> "√"
    CalcKey.Dot -> "."
    CalcKey.ToggleSign -> "±"
    CalcKey.Equals -> "="
    is CalcKey.Digit -> key.text
    is CalcKey.Op -> key.text
}

/**
 * 一颗计算器键。
 *
 * 用 [Surface] 而不是 `Button`：Material 的 Button 有 40dp 最小高度约束，想做成"大键"得跟它对着改；
 * Surface 自带 `onClick` 且沿用 Material 的波纹反馈与形变，不引入新的交互语义。
 */
@Composable
private fun RowScope.CalcKeyButton(
    key: CalcKey,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = when {
        key is CalcKey.Equals -> MaterialTheme.colorScheme.primary
        key is CalcKey.Op -> MaterialTheme.colorScheme.primaryContainer
        key is CalcKey.Clear || key is CalcKey.Backspace ||
            key is CalcKey.OpenParen || key is CalcKey.CloseParen ||
            key is CalcKey.Percent || key is CalcKey.Sqrt || key is CalcKey.ToggleSign ->
            MaterialTheme.colorScheme.secondaryContainer

        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        key is CalcKey.Equals -> MaterialTheme.colorScheme.onPrimary
        key is CalcKey.Op -> MaterialTheme.colorScheme.onPrimaryContainer
        key is CalcKey.Clear || key is CalcKey.Backspace ||
            key is CalcKey.OpenParen || key is CalcKey.CloseParen ||
            key is CalcKey.Percent || key is CalcKey.Sqrt || key is CalcKey.ToggleSign ->
            MaterialTheme.colorScheme.onSecondaryContainer

        else -> MaterialTheme.colorScheme.onSurface
    }
    val label = keyLabelOf(key)
    // 无障碍：`⌫` 是图标、`C` 是缩写，读屏需要有说法。这里只给 contentDescription（不是字符串资源）。
    val description = when (key) {
        CalcKey.Clear -> "clear"
        CalcKey.Backspace -> "backspace"
        else -> null
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = modifier
            .height(KEY_HEIGHT)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (key is CalcKey.Backspace) {
                // 用图标而不是 `⌫` 字形：某些机型的中文字体没有这个码位，会渲染成豆腐块。
                @Suppress("DEPRECATION")
                Icon(
                    imageVector = Icons.Filled.Backspace,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = label,
                    fontSize = KEY_LABEL_FONT_SIZE,
                    fontWeight = if (key is CalcKey.Equals) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** 键面字号：在 56dp 高的键里够醒目，又不至于让 `√` / `%` 这种单字符撑破键面。 */
private val KEY_LABEL_FONT_SIZE = 24.sp

/** 复制结果：函数名与 `QuickMenuToolScreens` 里那个私有同名函数不同名，避免同包重名（那边是 private）。 */
private fun copyCalcResult(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("calc", text)) }
}

// ---------------------------------------------------------------- 与 QuickDash 的差异（备忘）
//
// 布局：QuickDash 基础模式是 5 行 × 4 列等宽圆钮（`size` 58/70dp），退格**不在网格里**——它是显示区
// 右上角一颗 36dp 的图标钮；清空是网格第一格 `C`。本实现把退格收回网格（第一行第二格），
// 因为浮层面板没有独立工具条，退格放显示区会挤掉数字的展示宽度；等号由"圆钮"改成"占两格的宽键"。
//
// 求值：QuickDash 自带递归下降 `Parser`（支持 sin/cos/tan/ln/log/π/e/^，科学模式一排排键）；
// 我们复用 `CalculatorUtils.evaluateExpression`，因此**没有科学模式**，`√` 也退化成"对整段表达式开方"
// 而不是"对当前数开方"，并补上 `(` `)` `%` 这三个键（QuickDash 把括号藏在科学模式里）。
//
// 交互：修掉了 QuickDash 的两个坑 —— ① `%` 会用 `Regex.replace` 把整个表达式替换成百分号化后的数；
// ② 小数点用 `display.contains('.')` 判断，导致 `5×.` 出不来 `.5`。退格也改成"只删表达式、
// 主显示永远等于表达式末尾操作数"，避免 display 与 expression 互相漂移。
