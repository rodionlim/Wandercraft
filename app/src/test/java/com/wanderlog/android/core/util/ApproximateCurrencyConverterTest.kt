package com.wanderlog.android.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproximateCurrencyConverterTest {

    @Test
    fun `TWD is supported in display currencies and converts to SGD`() {
        assertEquals("TWD", BudgetDisplayCurrencies.sanitize("twd"))
        assertEquals("New Taiwan Dollar", BudgetDisplayCurrencies.labelFor("TWD"))
        assertTrue(ApproximateCurrencyConverter.canConvert("TWD", "SGD"))

        val converted = ApproximateCurrencyConverter.convert(1000.0, "TWD", "SGD")
        assertEquals(1000.0 * 0.031 / 0.74, converted, 0.001)
    }

    @Test
    fun `labelFor does not mislabel unknown currency codes as Singapore Dollar`() {
        assertNotEquals("Singapore Dollar", BudgetDisplayCurrencies.labelFor("AED"))
    }
}
