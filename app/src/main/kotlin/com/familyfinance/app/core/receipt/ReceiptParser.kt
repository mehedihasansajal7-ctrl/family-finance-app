package com.familyfinance.app.core.receipt

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Pulls an amount and a date out of raw receipt text (as produced by OCR).
 *
 * Pure and deterministic so it is fully unit-testable on the JVM. It is deliberately conservative:
 * when nothing looks like a total or a date it returns `null` rather than guessing, because a wrong
 * auto-filled amount in a finance app is worse than leaving the field blank.
 */
object ReceiptParser {

    data class Parsed(val amountMinor: Long?, val dateEpochDay: Long?)

    private val NUMBER = Regex("""\d[\d,]*(?:\.\d{1,2})?""")

    fun parse(text: String, today: LocalDate = LocalDate.now()): Parsed =
        Parsed(
            amountMinor = parseAmountMinor(text),
            dateEpochDay = parseDateEpochDay(text, today),
        )

    // ------------------------------------------------------------------ amount

    fun parseAmountMinor(text: String): Long? {
        // Prefer the number on a line that mentions a total/grand total/amount due.
        val totalLines = text.lines().filter {
            it.contains("grand total", true) ||
                it.contains("total", true) ||
                it.contains("amount due", true) ||
                it.contains("net payable", true) ||
                it.contains("payable", true)
        }
        val fromTotals = totalLines
            .flatMap { line -> NUMBER.findAll(line).mapNotNull { toMinor(it.value) }.toList() }
            .filter { it > 0 }
        if (fromTotals.isNotEmpty()) return fromTotals.max()

        // Otherwise only trust numbers that carry a currency hint.
        val hinted = CURRENCY_HINT.findAll(text).mapNotNull { toMinor(it.groupValues[1]) }.filter { it > 0 }
        return hinted.maxOrNull()
    }

    private val CURRENCY_HINT = Regex(
        """(?i)(?:৳|tk\.?|bdt|rs\.?|\$|usd|amount|price)\s*:?\s*(?:৳|tk\.?|bdt|rs\.?|\$)?\s*(\d[\d,]*(?:\.\d{1,2})?)""",
    )

    private fun toMinor(raw: String): Long? {
        val cleaned = raw.replace(",", "").replace(" ", "")
        val decimal = cleaned.toBigDecimalOrNull() ?: return null
        return decimal.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    // ------------------------------------------------------------------ date

    private val YEAR_FIRST = Regex("""\b(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})\b""")
    private val DAY_FIRST = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b""")
    private val NAMED_MONTH = Regex(
        """(?i)\b(\d{1,2})\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(\d{2,4})\b""",
    )

    fun parseDateEpochDay(text: String, today: LocalDate = LocalDate.now()): Long? {
        YEAR_FIRST.find(text)?.let { m ->
            candidate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), today)
                ?.let { return it }
        }
        DAY_FIRST.find(text)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val year = normalizeYear(m.groupValues[3].toInt())
            // Day-first is the local convention; fall back to month-first only if day-first is invalid.
            candidate(year, b, a, today)?.let { return it }
            candidate(year, a, b, today)?.let { return it }
        }
        NAMED_MONTH.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = monthNumber(m.groupValues[2])
            val year = normalizeYear(m.groupValues[3].toInt())
            if (month != null) candidate(year, month, day, today)?.let { return it }
        }
        return null
    }

    private fun normalizeYear(raw: Int): Int = if (raw < 100) 2000 + raw else raw

    private fun monthNumber(token: String): Int? =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            .indexOf(token.lowercase().take(3))
            .takeIf { it >= 0 }
            ?.plus(1)

    /** Keeps only sane dates (within ~2 years past / ~1 year future) so IDs and phone numbers are ignored. */
    private fun candidate(year: Int, month: Int, day: Int, today: LocalDate): Long? {
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        return if (days in -730..366) date.toEpochDay() else null
    }
}
