package com.familyfinance.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Exact monetary value.
 *
 * Money is stored as a whole number of **minor units** (poisha for BDT; 1 BDT = 100 poisha).
 * All arithmetic is integer arithmetic, so totals never drift the way `Double`/`Float` would.
 * Percentages are the only place a division happens, and those round explicitly (HALF_UP).
 *
 * This type is deliberately dependency-free so it can be unit-tested on the JVM without Android.
 */
@JvmInline
value class Money private constructor(val minor: Long) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    operator fun plus(other: Money): Money = Money(Math.addExact(minor, other.minor))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(minor, other.minor))

    operator fun unaryMinus(): Money = Money(Math.negateExact(minor))

    /** Integer scaling, e.g. a quantity of items. */
    operator fun times(factor: Int): Money = Money(Math.multiplyExact(minor, factor.toLong()))

    /** Exact scaling by a decimal factor, rounded HALF_UP to the nearest poisha. */
    fun times(factor: BigDecimal): Money =
        Money(BigDecimal.valueOf(minor).multiply(factor).setScale(0, RoundingMode.HALF_UP).longValueExact())

    fun abs(): Money = if (minor == Long.MIN_VALUE) throw ArithmeticException("overflow") else Money(kotlin.math.abs(minor))

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    /** Exact decimal representation, e.g. 123456 minor -> 1234.56 */
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(minor, SCALE)

    override fun toString(): String = toBigDecimal().toPlainString()

    companion object {
        const val SCALE: Int = 2
        private val POISHA_PER_BDT: Long = 100L

        val ZERO: Money = Money(0L)

        fun ofMinor(minor: Long): Money = Money(minor)

        /** Whole taka, e.g. ofBdt(1500) == ৳1,500.00 */
        fun ofBdt(amount: Long): Money = Money(Math.multiplyExact(amount, POISHA_PER_BDT))

        /** taka + poisha, e.g. ofBdt(1500, 50) == ৳1,500.50 */
        fun ofBdt(taka: Long, poisha: Int): Money {
            require(poisha in 0..99) { "poisha must be 0..99, was $poisha" }
            return Money(Math.addExact(Math.multiplyExact(taka, POISHA_PER_BDT), poisha.toLong()))
        }

        /**
         * Parses user input into an exact Money value.
         *
         * Accepts: "1234", "1234.5", "1234.56", "1,234.56", "৳1,234.56", "-50".
         * More than two decimal places is rejected rather than silently rounded, so the user is
         * forced to confirm the precision of their own money.
         */
        fun parse(input: String): Money =
            parseOrNull(input) ?: throw IllegalArgumentException("Not a valid amount: '$input'")

        fun parseOrNull(input: String): Money? {
            val cleaned = input
                .trim()
                .replace("\u09F3", "")
                .replace("Tk", "", ignoreCase = true)
                .replace("BDT", "", ignoreCase = true)
                .replace(",", "")
                .replace("\u00A0", "")
                .replace(" ", "")
            if (cleaned.isEmpty()) return null
            if (!cleaned.matches(Regex("-?\\d+(\\.\\d+)?"))) return null
            val decimal = try {
                BigDecimal(cleaned)
            } catch (_: NumberFormatException) {
                return null
            }
            if (decimal.scale() > SCALE) return null
            return try {
                Money(decimal.movePointRight(SCALE).longValueExact())
            } catch (_: ArithmeticException) {
                null
            }
        }

        /** Sums a collection without boxing surprises; empty input is ZERO. */
        fun sum(values: Iterable<Money>): Money {
            var total = 0L
            for (value in values) total = Math.addExact(total, value.minor)
            return Money(total)
        }

        fun max(a: Money, b: Money): Money = if (a >= b) a else b
        fun min(a: Money, b: Money): Money = if (a <= b) a else b
    }
}
