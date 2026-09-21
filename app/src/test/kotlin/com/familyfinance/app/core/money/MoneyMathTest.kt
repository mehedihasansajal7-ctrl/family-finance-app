package com.familyfinance.app.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyMathTest {

    @Test
    fun `percentage of a whole rounds half up`() {
        assertThat(MoneyMath.percentageOf(Money.ofBdt(75), Money.ofBdt(100))).isEqualTo(75)
        assertThat(MoneyMath.percentageOf(Money.ofBdt(90), Money.ofBdt(100))).isEqualTo(90)
        assertThat(MoneyMath.percentageOf(Money.ofBdt(100), Money.ofBdt(100))).isEqualTo(100)
        assertThat(MoneyMath.percentageOf(Money.ofBdt(120), Money.ofBdt(100))).isEqualTo(120)
    }

    @Test
    fun `percentage of a zero or negative whole is zero, never a crash`() {
        assertThat(MoneyMath.percentageOf(Money.ofBdt(50), Money.ZERO)).isEqualTo(0)
        assertThat(MoneyMath.percentageOf(Money.ofBdt(50), Money.ofBdt(-10))).isEqualTo(0)
    }

    @Test
    fun `percentOf returns an exact amount`() {
        assertThat(MoneyMath.percentOf(Money.ofBdt(20_000), 75)).isEqualTo(Money.ofBdt(15_000))
        assertThat(MoneyMath.percentOf(Money.ofBdt(1_000), 0)).isEqualTo(Money.ZERO)
    }

    @Test
    fun `allocate always sums back to the original total`() {
        val total = Money.ofMinor(1_000) // 10.00 in 1000 poisha
        val parts = MoneyMath.allocate(total, listOf(1, 1, 1))
        assertThat(Money.sum(parts)).isEqualTo(total)
        // Remainder is spread one poisha at a time; no part may absorb it all.
        assertThat(parts.map { it.minor }).containsExactly(334L, 333L, 333L)
    }

    @Test
    fun `allocate preserves the invariant across many awkward splits`() {
        for (totalMinor in listOf(1L, 7L, 99L, 100L, 1_001L, 999_999L)) {
            for (weights in listOf(listOf(1L, 1L), listOf(1L, 2L, 3L), listOf(5L, 3L, 2L, 1L))) {
                val total = Money.ofMinor(totalMinor)
                val parts = MoneyMath.allocate(total, weights)
                assertThat(Money.sum(parts)).isEqualTo(total)
            }
        }
    }

    @Test
    fun `allocate handles a negative total`() {
        val total = Money.ofMinor(-1_000)
        val parts = MoneyMath.allocate(total, listOf(1, 1))
        assertThat(Money.sum(parts)).isEqualTo(total)
    }

    @Test
    fun `allocate with degenerate weights puts everything in the first bucket`() {
        val total = Money.ofBdt(500)
        val parts = MoneyMath.allocate(total, listOf(0, 0))
        assertThat(Money.sum(parts)).isEqualTo(total)
        assertThat(parts.first()).isEqualTo(total)
    }

    @Test
    fun `divide rounds to the nearest poisha`() {
        // ৳100.00 / 3 = ৳33.33 (10,000 poisha / 3 = 3,333.33 -> 3,333).
        assertThat(MoneyMath.divide(Money.ofBdt(100), 3)).isEqualTo(Money.ofMinor(3_333L))
        assertThat(MoneyMath.divide(Money.ofBdt(10), 4)).isEqualTo(Money.ofMinor(250L))
        assertThat(MoneyMath.divide(Money.ofBdt(9), 3)).isEqualTo(Money.ofBdt(3))
    }

    @Test
    fun `percentageOf clamps negatives because it drives progress bars`() {
        assertThat(MoneyMath.percentageOf(Money.ofBdt(-50), Money.ofBdt(100))).isEqualTo(0)
    }

    @Test
    fun `signedPercentageOf preserves a decrease`() {
        // The distinction matters: "spending fell 50%" must not render as "0%".
        assertThat(MoneyMath.signedPercentageOf(Money.ofBdt(-50), Money.ofBdt(100))).isEqualTo(-50)
        assertThat(MoneyMath.signedPercentageOf(Money.ofBdt(50), Money.ofBdt(100))).isEqualTo(50)
        assertThat(MoneyMath.signedPercentageOf(Money.ofBdt(-50), Money.ZERO)).isEqualTo(0)
    }

    @Test
    fun `ratio clamps between zero and one and survives a zero denominator`() {
        assertThat(MoneyMath.ratio(Money.ofBdt(50), Money.ofBdt(100))).isWithin(0.001f).of(0.5f)
        assertThat(MoneyMath.ratio(Money.ofBdt(150), Money.ofBdt(100))).isEqualTo(1f)
        assertThat(MoneyMath.ratio(Money.ofBdt(50), Money.ZERO)).isEqualTo(0f)
    }
}
