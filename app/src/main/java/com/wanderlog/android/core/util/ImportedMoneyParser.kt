package com.wanderlog.android.core.util

import java.math.BigDecimal
import java.math.RoundingMode

object ImportedMoneyParser {

    private val amountRegex = Regex("""\d[\d,.\s]*\d|\d""")

    fun parseAmount(priceText: String?): Double? {
        val candidate = priceText?.trim().orEmpty()
        if (candidate.isBlank()) return null

        val rawAmount = amountRegex.findAll(candidate)
            .map { it.value.trim() }
            .filter { it.any(Char::isDigit) }
            .maxByOrNull { it.count(Char::isDigit) }
            ?: return null

        val normalized = normalizeSeparators(rawAmount)
        return normalized.toDoubleOrNull()
    }

    private fun normalizeSeparators(value: String): String {
        val compact = value.replace(" ", "")
        val lastDot = compact.lastIndexOf('.')
        val lastComma = compact.lastIndexOf(',')

        return when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimalSeparator = if (lastDot > lastComma) '.' else ','
                compact.mapNotNull { char ->
                    when {
                        char.isDigit() -> char
                        char == decimalSeparator -> '.'
                        else -> null
                    }
                }.joinToString("")
            }

            lastComma >= 0 -> {
                val digitsAfterComma = compact.length - lastComma - 1
                if (digitsAfterComma in 1..2) {
                    compact.replace(".", "").replace(',', '.')
                } else {
                    compact.replace(",", "")
                }
            }

            else -> compact.replace(",", "")
        }
    }
}

fun Double.roundToTwoDecimals(): Double =
    BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).toDouble()

fun Double.toEditableAmount(): String =
    BigDecimal.valueOf(this)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

fun String.capAmountToTwoDecimals(): String {
    val dotIndex = indexOf('.')
    return if (dotIndex >= 0 && length > dotIndex + 3) {
        substring(0, dotIndex + 3)
    } else {
        this
    }
}
