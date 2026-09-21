package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.RecurrenceFrequency
import com.familyfinance.app.domain.model.RecurringRule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Materialises recurring rules into concrete dates.
 *
 * Two properties matter for correctness:
 *  1. **No duplicates** — generation always resumes strictly after [RecurringRule.lastGeneratedEpochDay],
 *     so running the job twice produces nothing the second time.
 *  2. **Month-end clamping** — a monthly rule on the 31st fires on the 30th in April, not on the 1st
 *     of May, and never skips a month.
 */
object RecurrenceCalculator {

    /** All occurrences strictly after [afterExclusive] and not later than [untilInclusive]. */
    fun occurrencesBetween(
        rule: RecurringRule,
        afterExclusive: Long?,
        untilInclusive: Long,
    ): List<Long> {
        if (!rule.isActive) return emptyList()
        val results = mutableListOf<Long>()
        var cursor = nextOccurrence(rule, afterExclusive) ?: return emptyList()
        var guard = 0
        while (cursor <= untilInclusive && guard < MAX_ITERATIONS) {
            if (rule.endEpochDay != null && cursor > rule.endEpochDay) break
            results += cursor
            cursor = nextOccurrence(rule, cursor) ?: break
            guard++
        }
        return results
    }

    /** The first occurrence strictly after [afterExclusive], or null when the rule is exhausted. */
    fun nextOccurrence(rule: RecurringRule, afterExclusive: Long?): Long? {
        if (!rule.isActive) return null
        val start = rule.startEpochDay
        val floor = afterExclusive ?: (start - 1)
        if (floor < start - 1) return start
        val candidate = advance(rule, LocalDate.ofEpochDay(maxOf(floor + 1, start)))
        if (rule.endEpochDay != null && candidate > rule.endEpochDay) return null
        return candidate
    }

    private fun advance(rule: RecurringRule, from: LocalDate): Long {
        val anchor = LocalDate.ofEpochDay(rule.startEpochDay)
        if (from <= anchor) return anchor.toEpochDay()
        return when (rule.frequency) {
            RecurrenceFrequency.DAILY -> from.toEpochDay()
            RecurrenceFrequency.WEEKLY -> weeklyOnOrAfter(anchor, from).toEpochDay()
            RecurrenceFrequency.MONTHLY -> monthlyOnOrAfter(anchor, from).toEpochDay()
            RecurrenceFrequency.YEARLY -> yearlyOnOrAfter(anchor, from).toEpochDay()
            RecurrenceFrequency.CUSTOM -> customOnOrAfter(anchor, from, rule.customIntervalDays ?: 1).toEpochDay()
        }
    }

    /** The first occurrence on the anchor's weekday on or after [from], stepping in whole weeks. */
    fun weeklyOnOrAfter(anchor: LocalDate, from: LocalDate): LocalDate {
        val elapsed = ChronoUnit.DAYS.between(anchor, from)
        if (elapsed <= 0L) return anchor
        val weeks = (elapsed + 6L) / 7L
        return anchor.plusWeeks(weeks)
    }

    /**
     * The first month-end-clamped occurrence on or after [from].
     * E.g. anchor = 31 Jan: Feb -> 28/29, Mar -> 31, Apr -> 30.
     */
    fun monthlyOnOrAfter(anchor: LocalDate, from: LocalDate): LocalDate {
        val anchorDay = anchor.dayOfMonth
        var year = from.year
        var month = from.monthValue
        repeat(MAX_ITERATIONS) {
            val length = LocalDate.of(year, month, 1).lengthOfMonth()
            val candidate = LocalDate.of(year, month, minOf(anchorDay, length))
            if (!candidate.isBefore(from)) return candidate
            if (month == 12) {
                month = 1
                year++
            } else {
                month++
            }
        }
        return from
    }

    fun yearlyOnOrAfter(anchor: LocalDate, from: LocalDate): LocalDate {
        var year = from.year
        repeat(MAX_ITERATIONS) {
            val length = LocalDate.of(year, anchor.monthValue, 1).lengthOfMonth()
            val candidate = LocalDate.of(year, anchor.monthValue, minOf(anchor.dayOfMonth, length))
            if (!candidate.isBefore(from)) return candidate
            year++
        }
        return from
    }

    fun customOnOrAfter(anchor: LocalDate, from: LocalDate, intervalDays: Int): LocalDate {
        val step = intervalDays.coerceAtLeast(1).toLong()
        val elapsed = ChronoUnit.DAYS.between(anchor, from)
        if (elapsed <= 0L) return anchor
        val steps = (elapsed + step - 1) / step
        return anchor.plusDays(steps * step)
    }

    /**
     * Idempotency guard for the generator: a rule may only produce an occurrence once.
     * Returns true when [occurrence] has not already been generated for this rule.
     */
    fun shouldGenerate(rule: RecurringRule, occurrence: Long): Boolean {
        val last = rule.lastGeneratedEpochDay ?: return true
        return occurrence > last
    }

    /** True when the rule should be considered finished (past its end date). */
    fun isExhausted(rule: RecurringRule, today: LocalDate = AppTime.today()): Boolean =
        rule.endEpochDay?.let { today.toEpochDay() > it } ?: false

    private const val MAX_ITERATIONS = 2400
}
