package org.totschnig.myexpenses.compose.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorEngineTest {

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal?) {
        assertNotNull("Expected $expected but was null", actual)
        assertTrue(
            "Expected $expected but was ${actual?.toPlainString()}",
            BigDecimal(expected).compareTo(actual) == 0
        )
    }

    @Test
    fun testSimpleAddition() {
        val result = CalculatorEngine.calculate("150 + 45")
        assertBigDecimalEquals("195", result)
    }

    @Test
    fun testSimpleSubtraction() {
        val result = CalculatorEngine.calculate("200 - 50.25")
        assertBigDecimalEquals("149.75", result)
    }

    @Test
    fun testMultiplicationAndDivision() {
        val mult = CalculatorEngine.calculate("12 × 5")
        assertBigDecimalEquals("60", mult)

        val div = CalculatorEngine.calculate("100 ÷ 4")
        assertBigDecimalEquals("25", div)
    }

    @Test
    fun testOrderOfOperations() {
        val result = CalculatorEngine.calculate("10 + 20 × 3")
        assertBigDecimalEquals("70", result)
    }

    @Test
    fun testParentheses() {
        val result = CalculatorEngine.calculate("(10 + 20) × 3")
        assertBigDecimalEquals("90", result)
    }

    @Test
    fun testDivisionByZeroReturnsNull() {
        val result = CalculatorEngine.calculate("100 ÷ 0")
        assertNull(result)
    }

    @Test
    fun testSingleNumber() {
        val result = CalculatorEngine.calculate("411")
        assertBigDecimalEquals("411", result)
    }

    @Test
    fun testDecimalAddition() {
        val result = CalculatorEngine.calculate("3.5 + 4.25")
        assertBigDecimalEquals("7.75", result)
    }
}
