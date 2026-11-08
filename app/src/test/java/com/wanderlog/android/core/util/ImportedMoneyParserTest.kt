package com.wanderlog.android.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImportedMoneyParserTest {

    @Test
    fun `parseAmount keeps four digit totals with decimals`() {
        val parsed = ImportedMoneyParser.parseAmount("AUD 1962.20")
        assertNotNull(parsed)
        assertEquals(1962.20, parsed!!, 0.0001)
    }

    @Test
    fun `parseAmount handles thousands separators`() {
        val parsed = ImportedMoneyParser.parseAmount("Total: 1,962.20")
        assertNotNull(parsed)
        assertEquals(1962.20, parsed!!, 0.0001)
    }

    @Test
    fun `parseAmount returns null when no amount exists`() {
        assertNull(ImportedMoneyParser.parseAmount("price unavailable"))
    }

    @Test
    fun `toEditableAmount and capAmountToTwoDecimals cap at 2 decimal places`() {
        assertEquals("1000", 1000.0.toEditableAmount())
        assertEquals("41.89", (1000.0 * 0.031 / 0.74).toEditableAmount())
        assertEquals("12.5", 12.500000001.toEditableAmount())
        assertEquals("12.34", "12.3456".capAmountToTwoDecimals())
        assertEquals("12.3", "12.3".capAmountToTwoDecimals())
    }
}
