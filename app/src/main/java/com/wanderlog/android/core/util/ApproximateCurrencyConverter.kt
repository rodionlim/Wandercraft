package com.wanderlog.android.core.util

import java.util.Currency

data class CurrencyOption(
    val code: String,
    val label: String
)

object BudgetDisplayCurrencies {
    const val DEFAULT = "SGD"

    val options = listOf(
        CurrencyOption("SGD", "Singapore Dollar"),
        CurrencyOption("USD", "US Dollar"),
        CurrencyOption("TWD", "New Taiwan Dollar"),
        CurrencyOption("AUD", "Australian Dollar"),
        CurrencyOption("EUR", "Euro"),
        CurrencyOption("GBP", "British Pound"),
        CurrencyOption("JPY", "Japanese Yen"),
        CurrencyOption("CNY", "Chinese Yuan"),
        CurrencyOption("HKD", "Hong Kong Dollar"),
        CurrencyOption("CAD", "Canadian Dollar"),
        CurrencyOption("NZD", "New Zealand Dollar"),
        CurrencyOption("MYR", "Malaysian Ringgit"),
        CurrencyOption("THB", "Thai Baht"),
        CurrencyOption("IDR", "Indonesian Rupiah"),
        CurrencyOption("KRW", "South Korean Won"),
        CurrencyOption("INR", "Indian Rupee"),
        CurrencyOption("VND", "Vietnamese Dong"),
        CurrencyOption("PHP", "Philippine Peso"),
        CurrencyOption("CHF", "Swiss Franc")
    )

    private val supported = options.mapTo(linkedSetOf()) { it.code }

    fun sanitize(code: String?): String {
        val normalized = code?.trim()?.uppercase().orEmpty()
        return if (normalized in supported) normalized else DEFAULT
    }

    fun labelFor(code: String): String {
        val normalized = code.trim().uppercase()
        return options.firstOrNull { it.code == normalized }?.label
            ?: runCatching { Currency.getInstance(normalized).displayName }.getOrNull()
            ?: normalized.ifBlank { DEFAULT }
    }
}

object ApproximateCurrencyConverter {
    // Approximate USD value per 1 unit of the currency. Offline and intentionally rough.
    private val usdPerUnit = mapOf(
        "USD" to 1.0,
        "SGD" to 0.74,
        "TWD" to 0.031,
        "AUD" to 0.66,
        "EUR" to 1.09,
        "GBP" to 1.27,
        "JPY" to 0.0067,
        "CNY" to 0.14,
        "HKD" to 0.128,
        "CAD" to 0.73,
        "NZD" to 0.61,
        "MYR" to 0.21,
        "THB" to 0.027,
        "IDR" to 0.000061,
        "KRW" to 0.00074,
        "INR" to 0.012,
        "VND" to 0.000040,
        "PHP" to 0.017,
        "CHF" to 1.13
    )

    private fun normalize(code: String): String = code.trim().uppercase()

    fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        val from = normalize(fromCurrency)
        val to = normalize(toCurrency)
        if (from == to) return amount

        val fromUsd = usdPerUnit[from] ?: return amount
        val toUsd = usdPerUnit[to] ?: return amount
        return amount * fromUsd / toUsd
    }

    fun canConvert(fromCurrency: String, toCurrency: String): Boolean {
        val from = normalize(fromCurrency)
        val to = normalize(toCurrency)
        return from == to || (usdPerUnit.containsKey(from) && usdPerUnit.containsKey(to))
    }
}
