package com.familyfinance.app.core.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsTransactionParserTest {

    @Test
    fun `parses a bKash received message as income`() {
        val sms = "You have received Tk 1,000.00 from 01712345678. Fee Tk 0.00. " +
            "Balance Tk 5,000.00. TrxID ABC123XYZ"
        val parsed = SmsTransactionParser.parse("bKash", sms)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.provider).isEqualTo("bKash")
        assertThat(parsed.direction).isEqualTo(SmsTransactionParser.Direction.INCOME)
        assertThat(parsed.amountMinor).isEqualTo(100000L)
        assertThat(parsed.counterparty).isEqualTo("01712345678")
    }

    @Test
    fun `parses a bKash send money message as expense`() {
        val sms = "You have sent Tk 500.00 to 01812345678. Balance Tk 1,000.00. TrxID QWE987"
        val parsed = SmsTransactionParser.parse("bKash", sms)
        assertThat(parsed!!.direction).isEqualTo(SmsTransactionParser.Direction.EXPENSE)
        assertThat(parsed.amountMinor).isEqualTo(50000L)
    }

    @Test
    fun `parses cash out as expense`() {
        val sms = "Cash Out Tk 2,000.00 from 01712345678. Balance Tk 3,000.00."
        val parsed = SmsTransactionParser.parse("bKash", sms)
        assertThat(parsed!!.direction).isEqualTo(SmsTransactionParser.Direction.EXPENSE)
        assertThat(parsed.amountMinor).isEqualTo(200000L)
    }

    @Test
    fun `parses a Nagad received message`() {
        val sms = "Nagad: You have received Tk 200.00 from 01611111111."
        val parsed = SmsTransactionParser.parse("16167", sms)
        assertThat(parsed!!.provider).isEqualTo("Nagad")
        assertThat(parsed.direction).isEqualTo(SmsTransactionParser.Direction.INCOME)
        assertThat(parsed.amountMinor).isEqualTo(20000L)
    }

    @Test
    fun `parses a bank debit with the taka symbol`() {
        val sms = "Dear customer, your account has been debited by \u09F3 1,500.00 on 07-10-2026."
        val parsed = SmsTransactionParser.parse("BankAlert", sms)
        assertThat(parsed!!.direction).isEqualTo(SmsTransactionParser.Direction.EXPENSE)
        assertThat(parsed.amountMinor).isEqualTo(150000L)
    }

    @Test
    fun `ignores a non-financial sms`() {
        assertThat(SmsTransactionParser.parse("12345", "Your OTP is 123456. Do not share.")).isNull()
    }

    @Test
    fun `ignores an ambiguous message with no direction`() {
        assertThat(SmsTransactionParser.parse("bKash", "Tk 500.00 transaction update.")).isNull()
    }

    @Test
    fun `note never contains the balance`() {
        val sms = "You have received Tk 100.00 from 01712345678. Balance Tk 9,999.00."
        val parsed = SmsTransactionParser.parse("bKash", sms)!!
        assertThat(parsed.note).doesNotContain("9,999")
        assertThat(parsed.note).isEqualTo("bKash: received 01712345678")
    }
}
