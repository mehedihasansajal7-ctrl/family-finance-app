package com.familyfinance.app.core.security

/**
 * Redaction helpers.
 *
 * Rule: passwords, PINs, OTPs, CVV, full account/card numbers and balances must never reach a log
 * sink, an analytics event, or a crash report. Anything that might is routed through here first.
 */
object Redactor {

    const val PLACEHOLDER = "[redacted]"

    private val SENSITIVE_KEYS = setOf(
        "password", "pass", "pin", "otp", "cvv", "cvc", "token", "secret",
        "accountnumber", "account_number", "cardnumber", "card_number",
        "authorization", "apikey", "api_key", "idtoken", "refreshtoken",
        "balance", "amount", "income", "salary",
    )

    /** Masks a value completely, keeping at most the last [keepLast] characters. */
    fun mask(value: String?, keepLast: Int = 0): String {
        if (value.isNullOrEmpty()) return PLACEHOLDER
        if (keepLast <= 0) return PLACEHOLDER
        if (value.length <= keepLast) return PLACEHOLDER
        return "•".repeat((value.length - keepLast).coerceAtMost(12)) + value.takeLast(keepLast)
    }

    /** Redacts the value of any key that looks sensitive; used before logging a map. */
    fun redactMap(input: Map<String, Any?>): Map<String, Any?> = input.mapValues { (key, value) ->
        val normalised = key.lowercase().replace("-", "").replace(" ", "")
        when {
            SENSITIVE_KEYS.any { normalised.contains(it) } -> PLACEHOLDER
            value is Map<*, *> -> PLACEHOLDER
            else -> value
        }
    }

    /**
     * Scrubs a free-text log line for anything that looks like a credential or a long digit run
     * (account/card numbers). Deliberately conservative.
     */
    fun scrub(message: String): String {
        var out = message
        out = out.replace(Regex("(?i)(password|pin|otp|cvv|token|secret)\\s*[=:]\\s*\\S+"), "$1=$PLACEHOLDER")
        out = out.replace(Regex("\\b\\d{10,}\\b"), PLACEHOLDER)
        return out
    }
}
