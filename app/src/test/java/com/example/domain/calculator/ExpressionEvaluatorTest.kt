package com.example.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionEvaluatorTest {

    @Test
    fun testBasicArithmetic() {
        assertEquals(7.0, ExpressionEvaluator.evaluate("3 + 4")!!, 0.0001)
        assertEquals(-1.0, ExpressionEvaluator.evaluate("3 - 4")!!, 0.0001)
        assertEquals(12.0, ExpressionEvaluator.evaluate("3 * 4")!!, 0.0001)
        assertEquals(2.5, ExpressionEvaluator.evaluate("5 / 2")!!, 0.0001)
    }

    @Test
    fun testOrderOfOperations() {
        // Multiplication before addition: 2 + (3 * 4) = 14
        assertEquals(14.0, ExpressionEvaluator.evaluate("2 + 3 * 4")!!, 0.0001)
        // Division before subtraction: 20 - (10 / 2) = 15
        assertEquals(15.0, ExpressionEvaluator.evaluate("20 - 10 / 2")!!, 0.0001)
        // Mixed: 10 + 2 * 6 - 8 / 4 = 10 + 12 - 2 = 20
        assertEquals(20.0, ExpressionEvaluator.evaluate("10 + 2 * 6 - 8 / 4")!!, 0.0001)
    }

    @Test
    fun testParentheses() {
        assertEquals(20.0, ExpressionEvaluator.evaluate("(2 + 3) * 4")!!, 0.0001)
        assertEquals(5.0, ExpressionEvaluator.evaluate("(20 - 10) / 2")!!, 0.0001)
        assertEquals(24.0, ExpressionEvaluator.evaluate("((2 + 4) * (8 - 4))")!!, 0.0001)
    }

    @Test
    fun testDecimalsAndWhitespace() {
        assertEquals(15.75, ExpressionEvaluator.evaluate(" 10.5  +  5.25 ")!!, 0.0001)
        assertEquals(3.14, ExpressionEvaluator.evaluate("3.14")!!, 0.0001)
    }

    @Test
    fun testNegativeNumbersAndUnaryMinus() {
        assertEquals(-5.0, ExpressionEvaluator.evaluate("-5")!!, 0.0001)
        assertEquals(5.0, ExpressionEvaluator.evaluate("10 + -5")!!, 0.0001)
        assertEquals(-15.0, ExpressionEvaluator.evaluate("-10 - 5")!!, 0.0001)
    }

    @Test
    fun testDivisionByZero() {
        assertNull(ExpressionEvaluator.evaluate("10 / 0"))
        assertNull(ExpressionEvaluator.evaluate("5 / (2 - 2)"))
    }

    @Test
    fun testMalformedExpressions() {
        assertNull(ExpressionEvaluator.evaluate(""))
        assertNull(ExpressionEvaluator.evaluate("   "))
        assertNull(ExpressionEvaluator.evaluate("2 +"))
        assertNull(ExpressionEvaluator.evaluate("* 5"))
        assertNull(ExpressionEvaluator.evaluate("(2 + 3"))
        assertNull(ExpressionEvaluator.evaluate("abc"))
    }

    @Test
    fun testCommaStrippingAndTrailingEquals() {
        assertEquals(1200.00, ExpressionEvaluator.evaluate("1,000 + 200 =")!!, 0.0001)
        assertEquals(100000.00, ExpressionEvaluator.evaluate("1,00,000")!!, 0.0001)
    }
}
