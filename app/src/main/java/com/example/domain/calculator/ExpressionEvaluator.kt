package com.example.domain.calculator

object ExpressionEvaluator {

    fun evaluate(expression: String): Double? {
        val clean = expression.replace(",", "").trim().removeSuffix("=").trim()
        if (clean.isEmpty()) return null

        return try {
            val tokens = tokenize(clean) ?: return null
            val parser = Parser(tokens)
            val result = parser.parse()
            if (result != null && result.isFinite()) result else null
        } catch (_: Exception) {
            null
        }
    }

    private sealed class Token {
        data class Number(val value: Double) : Token()
        data object Plus : Token()
        data object Minus : Token()
        data object Multiply : Token()
        data object Divide : Token()
        data object Percent : Token()
        data object LParen : Token()
        data object RParen : Token()
    }

    private fun tokenize(expr: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = expr.length

        while (i < len) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c == '+' -> { tokens.add(Token.Plus); i++ }
                c == '-' -> { tokens.add(Token.Minus); i++ }
                c == '*' || c == 'x' || c == 'X' || c == '×' -> { tokens.add(Token.Multiply); i++ }
                c == '/' || c == '÷' -> { tokens.add(Token.Divide); i++ }
                c == '%' -> { tokens.add(Token.Percent); i++ }
                c == '(' -> { tokens.add(Token.LParen); i++ }
                c == ')' -> { tokens.add(Token.RParen); i++ }
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    var decimalCount = 0
                    while (i < len && (expr[i].isDigit() || expr[i] == '.')) {
                        if (expr[i] == '.') {
                            decimalCount++
                            if (decimalCount > 1) return null
                        }
                        sb.append(expr[i])
                        i++
                    }
                    val num = sb.toString().toDoubleOrNull() ?: return null
                    tokens.add(Token.Number(num))
                }
                else -> return null // Invalid character (garbage)
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun parse(): Double? {
            if (tokens.isEmpty()) return null
            val value = parseExpression() ?: return null
            if (pos < tokens.size) return null // Unconsumed tokens
            return value
        }

        // expression = term ( ('+' | '-') term )*
        private fun parseExpression(): Double? {
            var left = parseTerm() ?: return null

            while (pos < tokens.size) {
                when (tokens[pos]) {
                    is Token.Plus -> {
                        pos++
                        val right = parseTerm() ?: return null
                        left += right
                    }
                    is Token.Minus -> {
                        pos++
                        val right = parseTerm() ?: return null
                        left -= right
                    }
                    else -> break
                }
            }
            return left
        }

        // term = factor ( ('*' | '/') factor )*
        private fun parseTerm(): Double? {
            var left = parseFactor() ?: return null

            while (pos < tokens.size) {
                when (tokens[pos]) {
                    is Token.Multiply -> {
                        pos++
                        val right = parseFactor() ?: return null
                        left *= right
                    }
                    is Token.Divide -> {
                        pos++
                        val right = parseFactor() ?: return null
                        if (kotlin.math.abs(right) < 1e-15) return null // Divide by zero
                        left /= right
                    }
                    else -> break
                }
            }
            return left
        }

        // factor = ('+' | '-')* primary
        private fun parseFactor(): Double? {
            if (pos >= tokens.size) return null

            var sign = 1.0
            while (pos < tokens.size && (tokens[pos] is Token.Plus || tokens[pos] is Token.Minus)) {
                if (tokens[pos] is Token.Minus) {
                    sign = -sign
                }
                pos++
            }

            val primary = parsePrimary() ?: return null
            return sign * primary
        }

        // primary = ( number | '(' expression ')' ) ('%')*
        private fun parsePrimary(): Double? {
            if (pos >= tokens.size) return null

            var value: Double = when (val token = tokens[pos]) {
                is Token.Number -> {
                    pos++
                    token.value
                }
                is Token.LParen -> {
                    pos++
                    val exprVal = parseExpression() ?: return null
                    if (pos >= tokens.size || tokens[pos] !is Token.RParen) return null
                    pos++ // Consume RParen
                    exprVal
                }
                else -> return null
            }

            // Postfix percent: percent = value / 100
            while (pos < tokens.size && tokens[pos] is Token.Percent) {
                pos++
                value /= 100.0
            }

            return value
        }
    }
}
