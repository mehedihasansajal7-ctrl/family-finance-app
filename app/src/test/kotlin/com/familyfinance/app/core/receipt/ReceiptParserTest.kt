package com.familyfinance.app.core.receipt

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    private val today = LocalDate.of(2026, 10, 7)

    @Test
    fun `reads a total with a decimal`() {
        val parsed = ReceiptParser.parse("Rice 60.00\nTotal 1,250.00", today)
        assertThat(parsed.amountMinor).isEqualTo(125000L)
    }

    @Test
    fun `prefers the grand total over smaller line amounts`() {
        val text = "Milk 120.00\nBread 80.00\nGrand Total 200.00"
        assertThat(ReceiptParser.parseAmountMinor(text)).isEqualTo(20000L)
    }

    @Test
    fun `reads a currency-hinted amount when there is no total line`() {
        assertThat(ReceiptParser.parseAmountMinor("Paid Tk 1250 today")).isEqualTo(125000L)
    }

    @Test
    fun `returns null when nothing looks like an amount`() {
        assertThat(ReceiptParser.parseAmountMinor("Invoice number 12345 with 3 items")).isNull()
    }

    @Test
    fun `parses a day-first date`() {
        val parsed = ReceiptParser.parse("Date 12/09/2026 Total 100.00", today)
        assertThat(parsed.dateEpochDay).isEqualTo(LocalDate.of(2026, 9, 12).toEpochDay())
    }

    @Test
    fun `parses a year-first date`() {
        val parsed = ReceiptParser.parseDateEpochDay("2026-09-12", today)
        assertThat(parsed).isEqualTo(LocalDate.of(2026, 9, 12).toEpochDay())
    }

    @Test
    fun `parses a named-month date`() {
        val parsed = ReceiptParser.parseDateEpochDay("5 Sep 2026", today)
        assertThat(parsed).isEqualTo(LocalDate.of(2026, 9, 5).toEpochDay())
    }

    @Test
    fun `ignores an implausible date`() {
        assertThat(ReceiptParser.parseDateEpochDay("01/01/1999", today)).isNull()
        assertThat(ReceiptParser.parseDateEpochDay("0123456789", today)).isNull()
    }
}
