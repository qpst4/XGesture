package com.slideindex.app.search.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 *
 * Lightweight math expression evaluator.
 */
object CalculatorUtils {
    private val mathContext = MathContext.DECIMAL128
    private val hundred = BigDecimal("100")
    private val percentPhraseRegex =
        Regex(
            pattern = """^\s*([+-]?\d+(?:\.\d+)?)\s*%\s*(off|of)\s*([+-]?\d+(?:\.\d+)?)\s*$""",
            option = RegexOption.IGNORE_CASE,
        )

    fun isMathExpression(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        if (isPercentPhrase(trimmed)) return true
        val hasOperator =
            trimmed.contains('+') ||
                trimmed.contains('-') ||
                trimmed.contains('*') ||
                trimmed.contains('/') ||
                trimmed.contains('(') ||
                trimmed.contains(')') ||
                trimmed.contains('%') ||
                trimmed.contains('×') ||
                trimmed.contains('÷')
        if (!hasOperator) return false
        val validChars = trimmed.all { char ->
            char.isDigit() ||
                char == '+' ||
                char == '-' ||
                char == '*' ||
                char == '/' ||
                char == '(' ||
                char == ')' ||
                char == '.' ||
                char == ' ' ||
                char == '%' ||
                char == '×' ||
                char == '÷' ||
                char == '·'
        }
        return validChars && trimmed.length >= 2
    }

    fun evaluateExpression(expression: String): String? =
        try {
            evaluatePercentPhrase(expression)?.let { return formatResult(it) }
            val cleaned = cleanExpression(expression)
            formatResult(evaluate(cleaned))
        } catch (_: Exception) {
            null
        }

    private fun isPercentPhrase(query: String): Boolean = percentPhraseRegex.matches(query)

    private fun evaluatePercentPhrase(expression: String): BigDecimal? {
        val match = percentPhraseRegex.matchEntire(expression) ?: return null
        val percent = match.groupValues[1].toBigDecimalOrNull() ?: return null
        val operation = match.groupValues[2].lowercase(Locale.US)
        val baseValue = match.groupValues[3].toBigDecimalOrNull() ?: return null
        val percentValue = baseValue.multiply(percent).divide(hundred, mathContext)
        return when (operation) {
            "off" -> baseValue.subtract(percentValue)
            "of" -> percentValue
            else -> null
        }
    }

    private fun cleanExpression(expression: String): String =
        expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("·", "*")
            .replace(" ", "")
            .trim()

    private fun evaluate(expression: String): BigDecimal {
        var index = 0

        fun parseNumber(): BigDecimal {
            var numStr = ""
            while (index < expression.length &&
                (expression[index].isDigit() || expression[index] == '.')
            ) {
                numStr += expression[index]
                index++
            }
            return numStr.toBigDecimalOrNull() ?: throw IllegalArgumentException("Invalid number")
        }

        lateinit var parseExpression: () -> BigDecimal

        fun parseFactor(): BigDecimal {
            if (index >= expression.length) {
                throw IllegalArgumentException("Unexpected end of expression")
            }
            return when (expression[index]) {
                '(' -> {
                    index++
                    val result = parseExpression()
                    if (index >= expression.length || expression[index] != ')') {
                        throw IllegalArgumentException("Missing closing parenthesis")
                    }
                    index++
                    result
                }
                '-' -> {
                    index++
                    parseFactor().negate()
                }
                '+' -> {
                    index++
                    parseFactor()
                }
                else -> parseNumber()
            }
        }

        fun parseTerm(): BigDecimal {
            var result = parseFactor()
            while (index < expression.length) {
                when (expression[index]) {
                    '*' -> {
                        index++
                        result = result.multiply(parseFactor(), mathContext)
                    }
                    '/' -> {
                        index++
                        val divisor = parseFactor()
                        if (divisor.signum() == 0) throw ArithmeticException("Division by zero")
                        result = result.divide(divisor, mathContext)
                    }
                    else -> break
                }
            }
            return result
        }

        parseExpression = {
            var result = parseTerm()
            while (index < expression.length) {
                when (expression[index]) {
                    '+' -> {
                        index++
                        result = result.add(parseTerm(), mathContext)
                    }
                    '-' -> {
                        index++
                        result = result.subtract(parseTerm(), mathContext)
                    }
                    else -> break
                }
            }
            result
        }

        val result = parseExpression()
        if (index < expression.length) {
            throw IllegalArgumentException("Unexpected character at position $index")
        }
        return result
    }

    private fun formatResult(result: BigDecimal): String {
        val displayScale = 12
        val rounded = result.setScale(displayScale, RoundingMode.HALF_UP)
        val hasMoreDigits = rounded.compareTo(result) != 0
        val text = rounded.stripTrailingZeros().toPlainString()
        return if (hasMoreDigits) "$text…" else text
    }
}
