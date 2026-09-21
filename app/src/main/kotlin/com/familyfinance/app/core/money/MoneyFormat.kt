package com.familyfinance.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * BDT display formatting.
 *
 * Bangladesh uses the South-Asian grouping system (12,34,567) rather than the western 1,234,567,
 * and Bengali digits (০১২৩৪৫৬৭৮৯) are common in day-to-day use. Both are supported.
 */
object MoneyFormat {

    private val BENGALI_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    /** e.g. ৳12,34,567.50 */
    fun bdt(money: Money, bengaliDigits: Boolean = false, showSymbol: Boolean = true): String {
        val negative = money.isNegative
        val decimal = money.abs().toBigDecimal().setScale(Money.SCALE, RoundingMode.UNNECESSARY)
        val plain = decimal.toPlainString()
        val dot = plain.indexOf('.')
        val intPart = plain.substring(0, dot)
        val fracPart = plain.substring(dot + 1)

        val grouped = groupSouthAsian(intPart)
        val body = "$grouped.$fracPart"
        val withDigits = if (bengaliDigits) toBengaliDigits(body) else body
        val symbol = if (showSymbol) "\u09F3" else ""
        return buildString {
            if (negative) append('-')
            append(symbol)
            append(withDigits)
        }
    }

    /**
     * Short human form for chart labels and dashboard tiles:
     * ৳950 · ৳12.5K · ৳3.4L (lakh) · ৳1.2Cr (crore)
     */
    fun bdtCompact(money: Money, bengaliDigits: Boolean = false): String {
        val negative = money.isNegative
        val abs = money.abs().toBigDecimal()
        val oneLakh = BigDecimal(100_000)
        val oneCrore = BigDecimal(10_000_000)
        val oneThousand = BigDecimal(1_000)

        val (value, suffix) = when {
            abs >= oneCrore -> abs.divide(oneCrore, 2, RoundingMode.HALF_UP) to "Cr"
            abs >= oneLakh -> abs.divide(oneLakh, 2, RoundingMode.HALF_UP) to "L"
            abs >= oneThousand -> abs.divide(oneThousand, 1, RoundingMode.HALF_UP) to "K"
            else -> abs.setScale(0, RoundingMode.HALF_UP) to ""
        }
        val trimmed = value.stripTrailingZeros().toPlainString()
        val body = "\u09F3$trimmed$suffix"
        val withDigits = if (bengaliDigits) toBengaliDigits(body) else body
        return if (negative) "-$withDigits" else withDigits
    }

    /** Masks an account/card identifier, keeping only the last four characters visible. */
    fun maskIdentifier(last4: String?): String {
        val tail = last4?.trim()?.takeLast(4)?.takeIf { it.isNotEmpty() } ?: return "•••• •••• ••••"
        return "•••• •••• $tail"
    }

    /** Extracts the last four characters of an identifier, or null when it is too short to be useful. */
    fun last4Of(identifier: String?): String? {
        val digits = identifier?.filter { it.isDigit() } ?: return null
        return if (digits.length >= 4) digits.takeLast(4) else null
    }

    private fun groupSouthAsian(intPart: String): String {
        if (intPart.length <= 3) return intPart
        val head = intPart.dropLast(3)
        val tail = intPart.takeLast(3)
        val chunks = ArrayDeque<String>()
        var cursor = head.length
        while (cursor > 0) {
            val start = (cursor - 2).coerceAtLeast(0)
            chunks.addFirst(head.substring(start, cursor))
            cursor = start
        }
        return chunks.joinToString(",") + "," + tail
    }

    fun toBengaliDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            if (ch in '0'..'9') append(BENGALI_DIGITS[ch - '0']) else append(ch)
        }
    }
}
