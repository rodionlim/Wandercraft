package com.wanderlog.android.core.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.wanderlog.android.domain.model.ExpenseBreakdownItem

object ExpenseBreakdownCodec {

    private val listAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val type = Types.newParameterizedType(List::class.java, ExpenseBreakdownItem::class.java)
        Moshi.Builder().build().adapter<List<ExpenseBreakdownItem>>(type)
    }

    fun encode(items: List<ExpenseBreakdownItem>): String {
        if (items.isEmpty()) return ""
        return listAdapter.toJson(items)
    }

    fun decode(raw: String?): List<ExpenseBreakdownItem> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank() || !trimmed.startsWith("[")) return emptyList()

        return runCatching {
            listAdapter.fromJson(trimmed)
                .orEmpty()
                .filter { it.amount.isFinite() && it.amount >= 0.0 }
                .map { it.copy(label = it.label.trim()) }
        }.getOrDefault(emptyList())
    }
}
