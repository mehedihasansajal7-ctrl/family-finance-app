package com.familyfinance.app.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * All date logic runs in the family's local zone (`Asia/Dhaka` by default), never UTC.
 *
 * A transaction date is a *calendar date*, not an instant — "rent paid on 1 Sep" means that date
 * for the family, regardless of the device's clock zone. Dates are therefore modelled as
 * [LocalDate] and persisted as epoch-days (a stable integer that sorts and ranges correctly).
 */
object AppTime {

    val ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

    private val DISPLAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val DISPLAY_WITH_DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    fun now(): Instant = Instant.now()

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun currentMonth(): YearMonth = YearMonth.now(ZONE)

    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()

    fun fromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun todayEpochDay(): Long = today().toEpochDay()

    fun format(date: LocalDate, withDay: Boolean = false, bengaliDigits: Boolean = false): String {
        val text = (if (withDay) DISPLAY_WITH_DAY else DISPLAY).format(date)
        return if (bengaliDigits) bengaliDigitsOf(text) else text
    }

    fun formatEpochDay(epochDay: Long, withDay: Boolean = false, bengaliDigits: Boolean = false): String =
        format(fromEpochDay(epochDay), withDay, bengaliDigits)

    /** e.g. "September 2026" */
    fun monthLabel(month: YearMonth): String =
        "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}"

    /** e.g. "Sep 2026" */
    fun monthLabelShort(month: YearMonth): String =
        "${month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${month.year}"

    /** Inclusive epoch-day range covering the whole month. */
    fun monthRange(month: YearMonth): LongRange =
        month.atDay(1).toEpochDay()..month.atEndOfMonth().toEpochDay()

    fun monthOf(epochDay: Long): YearMonth = YearMonth.from(fromEpochDay(epochDay))

    fun isSameMonth(epochDay: Long, month: YearMonth): Boolean = monthOf(epochDay) == month

    /** Number of days in the month containing [epochDay] — used by recurring rules. */
    fun daysInMonthOf(epochDay: Long): Int = monthOf(epochDay).lengthOfMonth()

    /**
     * The Saturday on or before [date].
     *
     * The Bangladeshi working week starts on Saturday, so "this week" runs Sat → Fri. Note that
     * `LocalDate.with(DayOfWeek.SATURDAY)` is *not* correct here: it sets the ISO day-of-week field
     * and therefore moves forward to the coming Saturday for most weekdays.
     */
    fun startOfWeek(date: LocalDate): LocalDate =
        date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    fun isOverdue(dueEpochDay: Long, reference: LocalDate = today()): Boolean =
        dueEpochDay < reference.toEpochDay()

    private fun bengaliDigitsOf(input: String): String = buildString(input.length) {
        val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        for (ch in input) if (ch in '0'..'9') append(digits[ch - '0']) else append(ch)
    }
}
