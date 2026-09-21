package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.RecurrenceFrequency
import com.familyfinance.app.domain.model.RecurringRule
import com.familyfinance.app.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Recurring-rule generation.
 *
 * Two failure modes matter most and are covered explicitly: **duplicate posting** (running the
 * generator twice must not create two entries) and **month-end drift** (a rule on the 31st must not
 * skip February or slide into March).
 */
class RecurrenceCalculatorTest {

    private fun rule(
        frequency: RecurrenceFrequency,
        start: LocalDate,
        end: LocalDate? = null,
        lastGenerated: Long? = null,
        customIntervalDays: Int? = null,
        active: Boolean = true,
    ) = RecurringRule(
        id = "rule-1",
        ownerUid = "uid-alice",
        familyId = "family-1",
        type = TransactionType.EXPENSE,
        amount = Money.ofBdt(1_000),
        categoryId = "housing_rent",
        accountId = "acc-1",
        frequency = frequency,
        customIntervalDays = customIntervalDays,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end?.toEpochDay(),
        lastGeneratedEpochDay = lastGenerated,
        isActive = active,
    )

    @Test
    fun `the first occurrence is the start date`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 10))
        assertThat(RecurrenceCalculator.nextOccurrence(r, null))
            .isEqualTo(LocalDate.of(2026, 1, 10).toEpochDay())
    }

    @Test
    fun `monthly advances by one month on the same day`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 10))
        val after = LocalDate.of(2026, 1, 10).toEpochDay()
        assertThat(RecurrenceCalculator.nextOccurrence(r, after))
            .isEqualTo(LocalDate.of(2026, 2, 10).toEpochDay())
    }

    @Test
    fun `a rule on the 31st clamps to the end of a shorter month without skipping it`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 31))

        val february = RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2026, 1, 31).toEpochDay())
        assertThat(AppTime.fromEpochDay(february!!)).isEqualTo(LocalDate.of(2026, 2, 28))

        val march = RecurrenceCalculator.nextOccurrence(r, february)
        assertThat(AppTime.fromEpochDay(march!!)).isEqualTo(LocalDate.of(2026, 3, 31))

        val april = RecurrenceCalculator.nextOccurrence(r, march)
        assertThat(AppTime.fromEpochDay(april!!)).isEqualTo(LocalDate.of(2026, 4, 30))
    }

    @Test
    fun `a leap year February uses the 29th`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2028, 1, 31))
        val february = RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2028, 1, 31).toEpochDay())
        assertThat(AppTime.fromEpochDay(february!!)).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `occurrencesBetween never returns a date at or before the last generated one`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1))
        val lastGenerated = LocalDate.of(2026, 3, 1).toEpochDay()
        val until = LocalDate.of(2026, 6, 15).toEpochDay()

        val occurrences = RecurrenceCalculator.occurrencesBetween(r, lastGenerated, until)

        assertThat(occurrences).isNotEmpty()
        assertThat(occurrences.all { it > lastGenerated }).isTrue()
        assertThat(occurrences.map { AppTime.fromEpochDay(it) }).containsExactly(
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 6, 1),
        ).inOrder()
    }

    @Test
    fun `running the generator twice produces nothing the second time`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1))
        val until = LocalDate.of(2026, 4, 30).toEpochDay()

        val first = RecurrenceCalculator.occurrencesBetween(r, null, until)
        val lastGenerated = first.last()

        val second = RecurrenceCalculator.occurrencesBetween(r, lastGenerated, until)

        assertThat(first).hasSize(4)
        assertThat(second).isEmpty()
    }

    @Test
    fun `shouldGenerate is false for an already generated occurrence`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1), lastGenerated = 20_000L)
        assertThat(RecurrenceCalculator.shouldGenerate(r, 20_000L)).isFalse()
        assertThat(RecurrenceCalculator.shouldGenerate(r, 19_999L)).isFalse()
        assertThat(RecurrenceCalculator.shouldGenerate(r, 20_001L)).isTrue()
    }

    @Test
    fun `a rule with no history may generate any occurrence`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1), lastGenerated = null)
        assertThat(RecurrenceCalculator.shouldGenerate(r, 1L)).isTrue()
    }

    @Test
    fun `generation stops at the rule's end date`() {
        val r = rule(
            RecurrenceFrequency.MONTHLY,
            LocalDate.of(2026, 1, 1),
            end = LocalDate.of(2026, 3, 1),
        )
        val occurrences = RecurrenceCalculator.occurrencesBetween(r, null, LocalDate.of(2026, 12, 31).toEpochDay())
        assertThat(occurrences).hasSize(3)
    }

    @Test
    fun `an inactive rule generates nothing`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 1, 1), active = false)
        assertThat(RecurrenceCalculator.occurrencesBetween(r, null, LocalDate.of(2026, 12, 31).toEpochDay())).isEmpty()
        assertThat(RecurrenceCalculator.nextOccurrence(r, null)).isNull()
    }

    @Test
    fun `weekly advances by seven days`() {
        val r = rule(RecurrenceFrequency.WEEKLY, LocalDate.of(2026, 1, 1))
        assertThat(AppTime.fromEpochDay(RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2026, 1, 1).toEpochDay())!!))
            .isEqualTo(LocalDate.of(2026, 1, 8))
    }

    @Test
    fun `yearly clamps a 29 February start in a non-leap year`() {
        val r = rule(RecurrenceFrequency.YEARLY, LocalDate.of(2028, 2, 29))
        val next = RecurrenceCalculator.nextOccurrence(r, LocalDate.of(2028, 2, 29).toEpochDay())
        assertThat(AppTime.fromEpochDay(next!!)).isEqualTo(LocalDate.of(2029, 2, 28))
    }

    @Test
    fun `custom interval advances by the configured number of days`() {
        val r = rule(RecurrenceFrequency.CUSTOM, LocalDate.of(2026, 1, 1), customIntervalDays = 10)
        val occurrences = RecurrenceCalculator.occurrencesBetween(r, null, LocalDate.of(2026, 2, 1).toEpochDay())
        assertThat(occurrences.map { AppTime.fromEpochDay(it) }).containsExactly(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 11),
            LocalDate.of(2026, 1, 21),
            LocalDate.of(2026, 1, 31),
        ).inOrder()
    }

    @Test
    fun `a custom interval of zero is treated as one day rather than looping forever`() {
        val r = rule(RecurrenceFrequency.CUSTOM, LocalDate.of(2026, 1, 1), customIntervalDays = 0)
        val occurrences = RecurrenceCalculator.occurrencesBetween(r, null, LocalDate.of(2026, 1, 5).toEpochDay())
        assertThat(occurrences).hasSize(5)
    }

    @Test
    fun `an exhausted rule is reported as such`() {
        val r = rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2020, 1, 1), end = LocalDate.of(2021, 1, 1))
        assertThat(RecurrenceCalculator.isExhausted(r, LocalDate.of(2026, 1, 1))).isTrue()
        assertThat(RecurrenceCalculator.isExhausted(rule(RecurrenceFrequency.MONTHLY, LocalDate.of(2020, 1, 1)), LocalDate.of(2026, 1, 1)))
            .isFalse()
    }

    @Test
    fun `daily generation over a year terminates`() {
        val r = rule(RecurrenceFrequency.DAILY, LocalDate.of(2026, 1, 1))
        val occurrences = RecurrenceCalculator.occurrencesBetween(r, null, LocalDate.of(2026, 12, 31).toEpochDay())
        assertThat(occurrences).hasSize(365)
    }
}
