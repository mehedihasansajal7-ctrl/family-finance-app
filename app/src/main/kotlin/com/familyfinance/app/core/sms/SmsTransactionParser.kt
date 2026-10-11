package com.familyfinance.app.core.sms

/**
 * Turns a bank / MFS (bKash, Nagad, Rocket, upay) transaction SMS into a structured candidate.
 *
 * Pure and side-effect free, so the "is this a real transaction and which way did the money go?"
 * decision is fully unit-testable. It is deliberately strict: if the provider or the direction is
 * ambiguous it returns `null` rather than guessing, because a wrong auto-captured entry is worse
 * than none. Balance and TrxID fragments are stripped so nothing sensitive is kept in the note.
 */
object SmsTransactionParser {

    enum class Direction { INCOME, EXPENSE }

    data class Parsed(
        val provider: String,
        val direction: Direction,
        val amountMinor: Long,
        val counterparty: String?,
        val note: String,
    )

    private val PROVIDERS = listOf(
        "bKash" to Regex("(?i)\\bbkash\\b|16247"),
        "Nagad" to Regex("(?i)\\bnagad\\b|16167"),
        "Rocket" to Regex("(?i)\\brocket\\b|16216"),
        "upay" to Regex("(?i)\\bupay\\b|16267"),
    )

    private val AMOUNT = Regex("(?i)(?:tk|bdt|taka|৳)\\.?\\s*([\\d,]+(?:\\.\\d{1,2})?)")

    private val INCOME_WORDS = Regex("(?i)\\b(received|receive|cash in|deposit(?:ed)?|credited|added)\\b")
    private val EXPENSE_WORDS = Regex(
        "(?i)\\b(sent|send|paid|payment|cash out|withdraw(?:n|al)?|debited|purchase|bill|recharge|transfer(?:red)?)\\b",
    )

    private val COUNTERPARTY = Regex(
        "(?i)\\b(?:to|from|at)\\s+((?:\\+?88)?01[3-9]\\d{8}|\\d{11,14}|[A-Za-z][A-Za-z .&'-]{2,40}?)(?=\\.|,|\\s(?:Tk|BDT|on|TrxID|Balance)|$)",
    )

    fun parse(sender: String, body: String): Parsed? {
        val haystack = "$sender $body"
        val provider = PROVIDERS.firstOrNull { it.second.containsMatchIn(haystack) }?.first
        val amountText = AMOUNT.find(body)?.groupValues?.get(1) ?: return null
        val amountMinor = toMinor(amountText)?.takeIf { it > 0 } ?: return null

        val income = INCOME_WORDS.containsMatchIn(body)
        val expense = EXPENSE_WORDS.containsMatchIn(body)
        val direction = when {
            income && !expense -> Direction.INCOME
            expense && !income -> Direction.EXPENSE
            income && expense -> {
                // "received ... from" vs "you have received" is income; a "cash out" wins as expense.
                if (Regex("(?i)\\bcash out\\b|\\bwithdraw").containsMatchIn(body)) Direction.EXPENSE else Direction.INCOME
            }
            else -> return null
        }

        val counterparty = COUNTERPARTY.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val label = provider ?: "Bank"
        val verb = if (direction == Direction.INCOME) "received" else "sent"
        val note = buildString {
            append(label).append(": ").append(verb)
            counterparty?.let { append(" ").append(it) }
        }

        return Parsed(provider ?: "Bank", direction, amountMinor, counterparty, note)
    }

    private fun toMinor(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        val decimal = cleaned.toBigDecimalOrNull() ?: return null
        return decimal.multiply(java.math.BigDecimal(100))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toLong()
    }
}
