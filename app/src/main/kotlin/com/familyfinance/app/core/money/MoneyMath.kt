package com.familyfinance.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Percentage / allocation helpers. Division is the only lossy operation in the money model, so it
 * is isolated here and every rounding decision is explicit and unit-tested.
 */
object MoneyMath {

    /**
     * part as a percentage of whole, rounded HALF_UP, clamped to [0, Int.MAX_VALUE].
     *
     * The clamp is right for *progress* displays (a budget cannot be -20% used, a goal cannot be
     * -5% funded). For comparisons where the sign is the point — "spending fell 50%" — use
     * [signedPercentageOf] instead, because clamping here would silently turn a decrease into 0.
     */
    fun percentageOf(part: Money, whole: Money): Int {
        if (whole.minor <= 0L) return 0
        val pct = BigDecimal.valueOf(part.minor)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(whole.minor), 0, RoundingMode.HALF_UP)
            .toLong()
        return pct.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * part as a percentage of whole, preserving the sign and clamping only to the Int range.
     * Returns 0 when [whole] is zero or negative (there is no meaningful base to compare against).
     */
    fun signedPercentageOf(part: Money, whole: Money): Int {
        if (whole.minor <= 0L) return 0
        val pct = BigDecimal.valueOf(part.minor)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(whole.minor), 0, RoundingMode.HALF_UP)
            .toLong()
        return pct.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    /** pct percent of [base], rounded HALF_UP to the nearest poisha. */
    fun percentOf(base: Money, pct: Int): Money =
        base.times(BigDecimal.valueOf(pct.toLong()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))

    /**
     * Splits [total] into [weights] parts that sum **exactly** to [total].
     *
     * Rounding remainder is distributed one poisha at a time to the largest fractional parts, so
     * `allocate(x, w).sum() == x` always holds. Used by the family aggregate fan-out so that
     * family totals can never disagree with the sum of their parts.
     */
    fun allocate(total: Money, weights: List<Long>): List<Money> {
        if (weights.isEmpty()) return emptyList()
        val weightSum = weights.sum()
        if (weightSum <= 0L) {
            // Degenerate weights: everything to the first bucket so the invariant still holds.
            return weights.mapIndexed { i, _ -> if (i == 0) total else Money.ZERO }
        }
        val sign = if (total.minor < 0) -1L else 1L
        val magnitude = kotlin.math.abs(total.minor)

        val base = LongArray(weights.size)
        val remainders = ArrayList<Pair<Int, Long>>(weights.size)
        var assigned = 0L
        for (i in weights.indices) {
            val exact = magnitude * weights[i]
            val share = exact / weightSum
            base[i] = share
            assigned += share
            remainders += i to (exact % weightSum)
        }
        var leftover = magnitude - assigned
        remainders.sortedByDescending { it.second }.forEach { (index, _) ->
            if (leftover > 0L) {
                base[index] += 1L
                leftover -= 1L
            }
        }
        return base.map { Money.ofMinor(it * sign) }
    }

    /** [total] divided by [by], rounded HALF_UP to the nearest poisha. */
    fun divide(total: Money, by: Int): Money {
        require(by != 0) { "division by zero" }
        return total.times(
            BigDecimal.ONE.divide(BigDecimal.valueOf(by.toLong()), 10, RoundingMode.HALF_UP)
        )
    }

    /** Safe ratio for progress bars; 0.0f when the denominator is non-positive. */
    fun ratio(part: Money, whole: Money): Float {
        if (whole.minor <= 0L) return 0f
        return (part.minor.toDouble() / whole.minor.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}
