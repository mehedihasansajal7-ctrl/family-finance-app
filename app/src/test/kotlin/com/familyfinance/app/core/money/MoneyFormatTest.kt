package com.familyfinance.app.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun `uses south-asian grouping, not western grouping`() {
        // Bangladesh groups as 12,34,567 — this is the single most visible correctness detail.
        assertThat(MoneyFormat.bdt(Money.parse("1234567"))).isEqualTo("\u09F312,34,567.00")
        assertThat(MoneyFormat.bdt(Money.parse("100000"))).isEqualTo("\u09F31,00,000.00")
        assertThat(MoneyFormat.bdt(Money.parse("1000"))).isEqualTo("\u09F31,000.00")
        assertThat(MoneyFormat.bdt(Money.parse("999"))).isEqualTo("\u09F3999.00")
    }

    @Test
    fun `renders the taka symbol and two decimal places`() {
        assertThat(MoneyFormat.bdt(Money.parse("1500.5"))).isEqualTo("\u09F31,500.50")
    }

    @Test
    fun `renders negatives with a leading minus`() {
        assertThat(MoneyFormat.bdt(Money.parse("-2500"))).isEqualTo("-\u09F32,500.00")
    }

    @Test
    fun `can render bengali numerals`() {
        val formatted = MoneyFormat.bdt(Money.parse("12345.67"), bengaliDigits = true)
        assertThat(formatted).isEqualTo("\u09F3\u09E7\u09E8,\u09E9\u09EA\u09EB.\u09EC\u09ED")
    }

    @Test
    fun `compact form uses lakh and crore`() {
        assertThat(MoneyFormat.bdtCompact(Money.parse("950"))).isEqualTo("\u09F3950")
        assertThat(MoneyFormat.bdtCompact(Money.parse("12500"))).isEqualTo("\u09F312.5K")
        assertThat(MoneyFormat.bdtCompact(Money.parse("340000"))).isEqualTo("\u09F33.4L")
        assertThat(MoneyFormat.bdtCompact(Money.parse("12000000"))).isEqualTo("\u09F31.2Cr")
    }

    @Test
    fun `masks identifiers keeping only the last four`() {
        assertThat(MoneyFormat.maskIdentifier("1234")).isEqualTo("•••• •••• 1234")
        assertThat(MoneyFormat.maskIdentifier(null)).isEqualTo("•••• •••• ••••")
        assertThat(MoneyFormat.maskIdentifier("")).isEqualTo("•••• •••• ••••")
    }

    @Test
    fun `masking never reveals more than four characters`() {
        val masked = MoneyFormat.maskIdentifier("1234567890123456")
        assertThat(masked).doesNotContain("123456789012")
        assertThat(masked).endsWith("3456")
    }

    @Test
    fun `last4Of extracts only when at least four digits exist`() {
        assertThat(MoneyFormat.last4Of("01712-345678")).isEqualTo("5678")
        assertThat(MoneyFormat.last4Of("12")).isNull()
        assertThat(MoneyFormat.last4Of(null)).isNull()
        assertThat(MoneyFormat.last4Of("no digits here")).isNull()
    }
}
