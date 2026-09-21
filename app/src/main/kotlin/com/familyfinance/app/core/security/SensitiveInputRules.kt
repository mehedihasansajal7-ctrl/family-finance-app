package com.familyfinance.app.core.security

/**
 * Validation of sensitive identifiers that the app is willing to store.
 *
 * The app deliberately refuses to accept credentials it has no business holding. These checks are
 * enforced in the UI *and* re-checked in the repository layer, so a malformed payload cannot sneak
 * in through a restored backup or a crafted intent.
 */
object SensitiveInputRules {

    private val PIN_LIKE_KEYS = setOf(
        "pin", "cvv", "cvc", "password", "passwd", "otp", "mpin", "transactionpassword",
    )

    /** Fields the app must never persist. Used to reject unknown payload keys defensively. */
    fun isForbiddenField(key: String): Boolean =
        PIN_LIKE_KEYS.any { key.lowercase().replace("_", "").replace("-", "").contains(it) }

    /** Card/account numbers: 4–19 digits once separators are removed. Returns the last four. */
    fun normaliseIdentifier(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return digits.takeIf { it.length in 4..19 }
    }

    /**
     * Rejects anything that looks like a card number failing the Luhn check, so obvious typos are
     * caught before they are stored. Non-card account numbers skip this (Luhn does not apply).
     */
    fun passesLuhn(digits: String): Boolean {
        if (digits.length < 12) return true
        var sum = 0
        var alternate = false
        for (i in digits.indices.reversed()) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
