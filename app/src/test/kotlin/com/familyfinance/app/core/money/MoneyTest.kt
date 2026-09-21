package com.familyfinance.app.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Money arithmetic.
 *
 * These tests exist to prove the property that matters most in a finance app: totals are exact.
 * Several of them are written specifically to fail if someone ever swaps the integer implementation
 * for a `Double`.
 */
class MoneyTest {

    @Test
    fun `parses whole and decimal amounts`() {
        assertThat(Money.parse("1500").minor).isEqualTo(150_000L)
        assertThat(Money.parse("1500.50").minor).isEqualTo(150_050L)
        assertThat(Money.parse("1500.5").minor).isEqualTo(150_050L)
    }

    @Test
    fun `parses formatted input including symbol and separators`() {
        assertThat(Money.parse("\u09F31,23,456.78").minor).isEqualTo(12_345_678L)
        assertThat(Money.parse("Tk 1,234").minor).isEqualTo(123_400L)
        assertThat(Money.parse(" -50 ").minor).isEqualTo(-5_000L)
    }

    @Test
    fun `rejects more than two decimal places instead of rounding silently`() {
        assertThat(Money.parseOrNull("100.999")).isNull()
        assertThat(Money.parseOrNull("0.001")).isNull()
    }

    @Test
    fun `rejects malformed input`() {
        assertThat(Money.parseOrNull("")).isNull()
        assertThat(Money.parseOrNull("abc")).isNull()
        assertThat(Money.parseOrNull("1.2.3")).isNull()
        assertThat(Money.parseOrNull("--5")).isNull()
    }

    @Test
    fun `addition and subtraction are exact`() {
        val a = Money.parse("0.10")
        val b = Money.parse("0.20")
        // The canonical floating-point trap: 0.1 + 0.2 != 0.3 in binary floating point.
        assertThat((a + b).toBigDecimal().toPlainString()).isEqualTo("0.30")
    }

    @Test
    fun `summing a long list of small amounts stays exact`() {
        val amounts = List(10_000) { Money.ofMinor(1) } // 10,000 poisha = 100.00
        assertThat(Money.sum(amounts).minor).isEqualTo(10_000L)
        assertThat(Money.sum(amounts).toBigDecimal().toPlainString()).isEqualTo("100.00")
    }

    @Test
    fun `a thousand additions of one poisha never drifts`() {
        var total = Money.ZERO
        repeat(1_000) { total += Money.ofMinor(1) }
        assertThat(total.minor).isEqualTo(1_000L)
    }

    @Test
    fun `unary minus and abs behave`() {
        val value = Money.parse("250.75")
        assertThat((-value).minor).isEqualTo(-25_075L)
        assertThat((-value).abs().minor).isEqualTo(25_075L)
        assertThat(value.isPositive).isTrue()
        assertThat((-value).isNegative).isTrue()
        assertThat(Money.ZERO.isZero).isTrue()
    }

    @Test
    fun `integer scaling multiplies exactly`() {
        assertThat((Money.parse("19.99") * 3).toBigDecimal().toPlainString()).isEqualTo("59.97")
    }

    @Test
    fun `decimal scaling rounds half up to the nearest poisha`() {
        // 10.005 * 1 = 10.005 -> 10.01 (HALF_UP), never 10.00.
        assertThat(Money.ofMinor(1_000).times(java.math.BigDecimal("1.0005")).minor).isEqualTo(1_001L)
    }

    @Test
    fun `comparison and min max work`() {
        val small = Money.ofBdt(10)
        val large = Money.ofBdt(20)
        assertThat(small < large).isTrue()
        assertThat(Money.max(small, large)).isEqualTo(large)
        assertThat(Money.min(small, large)).isEqualTo(small)
    }

    @Test
    fun `ofBdt validates poisha range`() {
        assertThat(Money.ofBdt(10, 99).minor).isEqualTo(1_099L)
        val failure = runCatching { Money.ofBdt(10, 100) }
        assertThat(failure.isFailure).isTrue()
    }

    @Test
    fun `toString round-trips through parse`() {
        val original = Money.parse("98765.43")
        assertThat(Money.parse(original.toString())).isEqualTo(original)
    }
}
