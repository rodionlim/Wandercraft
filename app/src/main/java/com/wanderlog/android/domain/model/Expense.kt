package com.wanderlog.android.domain.model

import com.squareup.moshi.JsonClass
import java.time.LocalDate

@JsonClass(generateAdapter = true)
data class ExpenseBreakdownItem(
    val label: String,
    val amount: Double,
    val isActive: Boolean = true
)

data class Expense(
    val id: String,
    val tripId: String,
    val title: String,
    val amount: Double,
    val currencyCode: String,
    val category: ExpenseCategory,
    val date: LocalDate? = null,
    val notes: String? = null,
    val breakdown: List<ExpenseBreakdownItem> = emptyList()
) {
    val activeAmount: Double
        get() = if (breakdown.isEmpty()) {
            amount
        } else {
            breakdown.filter(ExpenseBreakdownItem::isActive).sumOf(ExpenseBreakdownItem::amount)
        }
}

enum class ExpenseCategory {
    TRANSPORT, ACCOMMODATION, FOOD, GROCERIES, ACTIVITY, SHOPPING, OTHER
}
