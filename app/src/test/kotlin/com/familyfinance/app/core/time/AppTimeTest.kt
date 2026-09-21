package com.familyfinance.app.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Date logic runs in the family's local zone (`Asia/Dhaka`), not UTC.
 *
 * The tests that matter are the boundary ones: the first and last day of a month, a leap February,
 * and the fact that the zone is Dhaka rather than the JVM default.
 */
class AppTimeTest {

    @Test
    fun `the zone is Asia Dhaka`() {
        assertThat(AppTime.ZONE).isEqualTo(ZoneId.of("Asia/Dhaka"))
        assertThat(AppTime.ZONE.id).isEqualTo("Asia/Dhaka")
    }

    @Test
    fun `epoch day round-trips`() {
        val date = LocalDate.of(2026, 9, 21)
        assertThat(AppTime.fromEpochDay(AppTime.toEpochDay(date))).isEqualTo(date)
    }

    @Test
    fun `month range starts on the first and ends on the last day`() {
        val range = AppTime.monthRange(YearMonth.of(2026, 9))
        assertThat(AppTime.fromEpochDay(range.first)).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(AppTime.fromEpochDay(range.last)).isEqualTo(LocalDate.of(2026, 9, 30))
    }

    @Test
    fun `month range handles a leap February`() {
        val range = AppTime.monthRange(YearMonth.of(2028, 2))
        assertThat(AppTime.fromEpochDay(range.last)).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `month range handles a non-leap February`() {
        val range = AppTime.monthRange(YearMonth.of(2027, 2))
        assertThat(AppTime.fromEpochDay(range.last)).isEqualTo(LocalDate.of(2027, 2, 28))
    }

    @Test
    fun `a day on the last of the month belongs to that month`() {
        val lastDay = LocalDate.of(2026, 9, 30).toEpochDay()
        assertThat(AppTime.monthOf(lastDay)).isEqualTo(YearMonth.of(2026, 9))
        assertThat(AppTime.isSameMonth(lastDay, YearMonth.of(2026, 9))).isTrue()
        assertThat(AppTime.isSameMonth(lastDay, YearMonth.of(2026, 10))).isFalse()
    }

    @Test
    fun `a day on the first of the month belongs to that month`() {
        val firstDay = LocalDate.of(2026, 10, 1).toEpochDay()
        assertThat(AppTime.monthOf(firstDay)).isEqualTo(YearMonth.of(2026, 10))
    }

    @Test
    fun `days in month is correct across leap and non-leap years`() {
        assertThat(AppTime.daysInMonthOf(LocalDate.of(2028, 2, 1).toEpochDay())).isEqualTo(29)
        assertThat(AppTime.daysInMonthOf(LocalDate.of(2027, 2, 1).toEpochDay())).isEqualTo(28)
        assertThat(AppTime.daysInMonthOf(LocalDate.of(2026, 4, 1).toEpochDay())).isEqualTo(30)
        assertThat(AppTime.daysInMonthOf(LocalDate.of(2026, 1, 1).toEpochDay())).isEqualTo(31)
    }

    @Test
    fun `overdue is strict so a bill due today is not overdue`() {
        val today = LocalDate.of(2026, 9, 20)
        assertThat(AppTime.isOverdue(today.toEpochDay(), today)).isFalse()
        assertThat(AppTime.isOverdue(today.minusDays(1).toEpochDay(), today)).isTrue()
        assertThat(AppTime.isOverdue(today.plusDays(1).toEpochDay(), today)).isFalse()
    }

    @Test
    fun `month labels are human readable`() {
        assertThat(AppTime.monthLabel(YearMonth.of(2026, 9))).isEqualTo("September 2026")
        assertThat(AppTime.monthLabelShort(YearMonth.of(2026, 9))).isEqualTo("Sep 2026")
    }

    @Test
    fun `format renders an unambiguous date`() {
        assertThat(AppTime.format(LocalDate.of(2026, 9, 25))).isEqualTo("25 Sep 2026")
        assertThat(AppTime.format(LocalDate.of(2026, 9, 25), withDay = true)).contains("Sep 2026")
    }

    @Test
    fun `format can render bengali numerals`() {
        val formatted = AppTime.format(LocalDate.of(2026, 9, 25), bengaliDigits = true)
        assertThat(formatted).isEqualTo("\u09E8\u09EB Sep \u09E8\u09E6\u09E8\u09EC")
    }

    @Test
    fun `days between is signed and exact`() {
        val a = LocalDate.of(2026, 9, 1)
        val b = LocalDate.of(2026, 9, 21)
        assertThat(AppTime.daysBetween(a, b)).isEqualTo(20L)
        assertThat(AppTime.daysBetween(b, a)).isEqualTo(-20L)
    }

    @Test
    fun `start of week is Saturday in Bangladesh`() {
        // The Bangladeshi working week starts on Saturday, not Monday.
        val wednesday = LocalDate.of(2026, 9, 23)
        assertThat(AppTime.startOfWeek(wednesday)).isEqualTo(LocalDate.of(2026, 9, 19))
    }
}
