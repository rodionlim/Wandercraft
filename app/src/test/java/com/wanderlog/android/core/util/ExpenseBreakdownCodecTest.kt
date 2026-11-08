package com.wanderlog.android.core.util

import com.wanderlog.android.domain.model.Expense
import com.wanderlog.android.domain.model.ExpenseBreakdownItem
import com.wanderlog.android.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseBreakdownCodecTest {

    @Test
    fun `encode and decode round trip preserves breakdown labels amounts and active flags`() {
        val original = listOf(
            ExpenseBreakdownItem(label = "parents", amount = 700.0, isActive = false),
            ExpenseBreakdownItem(label = "our family", amount = 300.0, isActive = true)
        )

        val encoded = ExpenseBreakdownCodec.encode(original)
        val decoded = ExpenseBreakdownCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `decode returns empty list for blank or legacy values`() {
        assertTrue(ExpenseBreakdownCodec.decode(null).isEmpty())
        assertTrue(ExpenseBreakdownCodec.decode("").isEmpty())
        assertTrue(ExpenseBreakdownCodec.decode("   ").isEmpty())
        assertTrue(ExpenseBreakdownCodec.decode("not-json").isEmpty())
    }

    @Test
    fun `expense activeAmount maintains backwards compatibility when breakdown is empty and excludes inactive breakdown items`() {
        val legacyExpense = Expense(
            id = "exp-1",
            tripId = "trip-1",
            title = "Hotel",
            amount = 1000.0,
            currencyCode = "SGD",
            category = ExpenseCategory.ACCOMMODATION
        )
        assertEquals(1000.0, legacyExpense.activeAmount, 0.001)

        val splitExpense = legacyExpense.copy(
            breakdown = listOf(
                ExpenseBreakdownItem(label = "parents", amount = 700.0, isActive = false),
                ExpenseBreakdownItem(label = "our family", amount = 300.0, isActive = true)
            )
        )
        assertEquals(1000.0, splitExpense.amount, 0.001)
        assertEquals(300.0, splitExpense.activeAmount, 0.001)

        val allInactiveExpense = legacyExpense.copy(
            breakdown = listOf(
                ExpenseBreakdownItem(label = "parents", amount = 700.0, isActive = false),
                ExpenseBreakdownItem(label = "our family", amount = 300.0, isActive = false)
            )
        )
        assertEquals(0.0, allInactiveExpense.activeAmount, 0.001)
    }
}
