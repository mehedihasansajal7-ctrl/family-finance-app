package com.familyfinance.app.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RedactorTest {

    @Test
    fun `mask hides everything by default`() {
        assertThat(Redactor.mask("1234567890")).isEqualTo(Redactor.PLACEHOLDER)
        assertThat(Redactor.mask(null)).isEqualTo(Redactor.PLACEHOLDER)
        assertThat(Redactor.mask("")).isEqualTo(Redactor.PLACEHOLDER)
    }

    @Test
    fun `mask with a keepLast reveals only the tail`() {
        assertThat(Redactor.mask("1234567890", keepLast = 4)).endsWith("7890")
        assertThat(Redactor.mask("1234567890", keepLast = 4)).doesNotContain("123456")
    }

    @Test
    fun `redactMap strips values under sensitive keys`() {
        val redacted = Redactor.redactMap(
            mapOf(
                "name" to "Mehedi",
                "password" to "hunter2",
                "bkashPin" to "1234",
                "cardCvv" to "999",
                "balance" to 500_000L,
                "accountNumber" to "1234567890123456",
            )
        )

        assertThat(redacted["name"]).isEqualTo("Mehedi")
        assertThat(redacted["password"]).isEqualTo(Redactor.PLACEHOLDER)
        assertThat(redacted["bkashPin"]).isEqualTo(Redactor.PLACEHOLDER)
        assertThat(redacted["cardCvv"]).isEqualTo(Redactor.PLACEHOLDER)
        assertThat(redacted["balance"]).isEqualTo(Redactor.PLACEHOLDER)
        assertThat(redacted["accountNumber"]).isEqualTo(Redactor.PLACEHOLDER)
    }

    @Test
    fun `scrub removes credentials and long digit runs from a log line`() {
        val scrubbed = Redactor.scrub("login password=hunter2 for account 1234567890123456")
        assertThat(scrubbed).doesNotContain("hunter2")
        assertThat(scrubbed).doesNotContain("1234567890123456")
    }

    @Test
    fun `scrub leaves ordinary text alone`() {
        val line = "Saved 1 transaction for the Hasan family"
        assertThat(Redactor.scrub(line)).isEqualTo(line)
    }

    @Test
    fun `a phone number is redacted because it is personal data`() {
        // Member mobile numbers are PII. The scrubber deliberately treats a long digit run as
        // sensitive, so a phone number never lands in a log sink either.
        val scrubbed = Redactor.scrub("mobile 01712345678")
        assertThat(scrubbed).doesNotContain("01712345678")
        assertThat(scrubbed).contains("mobile")
    }
}

class SensitiveInputRulesTest {

    @Test
    fun `recognises the credential field names the app refuses to store`() {
        listOf("pin", "cvv", "cvc", "password", "otp", "mpin", "transaction_password", "transaction-password")
            .forEach { key ->
                assertThat(SensitiveInputRules.isForbiddenField(key)).isTrue()
            }
    }

    @Test
    fun `allows ordinary field names`() {
        listOf("amount", "note", "categoryId", "accountName", "last4").forEach { key ->
            assertThat(SensitiveInputRules.isForbiddenField(key)).isFalse()
        }
    }

    @Test
    fun `normalises an identifier by stripping separators`() {
        assertThat(SensitiveInputRules.normaliseIdentifier("1234 5678 9012 3456")).isEqualTo("1234567890123456")
        assertThat(SensitiveInputRules.normaliseIdentifier("01712-345678")).isEqualTo("01712345678")
    }

    @Test
    fun `rejects identifiers that are too short or too long`() {
        assertThat(SensitiveInputRules.normaliseIdentifier("123")).isNull()
        assertThat(SensitiveInputRules.normaliseIdentifier("12345678901234567890123")).isNull()
    }

    @Test
    fun `luhn check accepts a valid card number and rejects a typo`() {
        assertThat(SensitiveInputRules.passesLuhn("4532015112830366")).isTrue()
        assertThat(SensitiveInputRules.passesLuhn("4532015112830367")).isFalse()
    }

    @Test
    fun `luhn is skipped for numbers that are not card length`() {
        assertThat(SensitiveInputRules.passesLuhn("1234")).isTrue()
    }
}
