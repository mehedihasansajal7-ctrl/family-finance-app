package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.testutil.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Balance and transfer arithmetic.
 *
 * The transfer cases are the important ones: an internal transfer must move value between two
 * accounts without changing any income or expense total, personal or family.
 */
class BalanceCalculatorTest {

    @Test
    fun `an account with no transactions has its opening balance`() {
        val account = TestData.account("acc-1", openingTaka = 5_000)
        assertThat(AccountBalanceCalculator.balance(account, emptyList())).isEqualTo(Money.ofBdt(5_000))
    }

    @Test
    fun `income increases the destination account`() {
        val account = TestData.account("acc-1", openingTaka = 1_000)
        val transactions = listOf(TestData.income(taka = 500, accountId = "acc-1"))
        assertThat(AccountBalanceCalculator.balance(account, transactions)).isEqualTo(Money.ofBdt(1_500))
    }

    @Test
    fun `expense decreases the payment account`() {
        val account = TestData.account("acc-1", openingTaka = 1_000)
        val transactions = listOf(TestData.expense(taka = 250, accountId = "acc-1"))
        assertThat(AccountBalanceCalculator.balance(account, transactions)).isEqualTo(Money.ofBdt(750))
    }

    @Test
    fun `an expense on another account does not affect this one`() {
        val account = TestData.account("acc-1", openingTaka = 1_000)
        val transactions = listOf(TestData.expense(taka = 250, accountId = "acc-2"))
        assertThat(AccountBalanceCalculator.balance(account, transactions)).isEqualTo(Money.ofBdt(1_000))
    }

    @Test
    fun `a transfer debits the source and credits the destination by the same amount`() {
        val from = TestData.account("acc-dbbl", openingTaka = 10_000)
        val to = TestData.account("acc-bkash", openingTaka = 2_000)
        val transactions = listOf(TestData.transfer(taka = 5_000, from = "acc-dbbl", to = "acc-bkash"))

        assertThat(AccountBalanceCalculator.balance(from, transactions)).isEqualTo(Money.ofBdt(5_000))
        assertThat(AccountBalanceCalculator.balance(to, transactions)).isEqualTo(Money.ofBdt(7_000))
    }

    @Test
    fun `a transfer does not change the combined balance of the two accounts`() {
        val from = TestData.account("acc-dbbl", openingTaka = 10_000)
        val to = TestData.account("acc-bkash", openingTaka = 2_000)
        val transactions = listOf(TestData.transfer(taka = 5_000, from = "acc-dbbl", to = "acc-bkash"))

        val balances = AccountBalanceCalculator.balances(listOf(from, to), transactions)
        val before = Money.ofBdt(12_000)
        assertThat(AccountBalanceCalculator.totalBalance(balances)).isEqualTo(before)
    }

    @Test
    fun `a transfer is neither income nor expense in any summary`() {
        val transactions = listOf(
            TestData.income(id = "i1", taka = 1_000, accountId = "acc-dbbl"),
            TestData.transfer(taka = 5_000, from = "acc-dbbl", to = "acc-bkash"),
        )
        val income = PersonalSummaryCalculator.sumByType(transactions, com.familyfinance.app.domain.model.TransactionType.INCOME)
        val expense = PersonalSummaryCalculator.sumByType(transactions, com.familyfinance.app.domain.model.TransactionType.EXPENSE)

        assertThat(income).isEqualTo(Money.ofBdt(1_000))
        assertThat(expense).isEqualTo(Money.ZERO)
    }

    @Test
    fun `a transaction owned by someone else is ignored even if it names this account`() {
        // Defence in depth: a mis-scoped query must not be able to corrupt a balance.
        val account = TestData.account("acc-shared-id", ownerUid = TestData.ALICE, openingTaka = 1_000)
        val foreign = TestData.expense(
            id = "tx-foreign",
            ownerUid = TestData.BOB,
            taka = 900,
            accountId = "acc-shared-id",
        )
        assertThat(AccountBalanceCalculator.balance(account, listOf(foreign))).isEqualTo(Money.ofBdt(1_000))
    }

    @Test
    fun `balances maps every account and totals active accounts only`() {
        val active = TestData.account("acc-a", openingTaka = 1_000, active = true)
        val inactive = TestData.account("acc-b", openingTaka = 9_000, active = false)
        val balances = AccountBalanceCalculator.balances(listOf(active, inactive), emptyList())

        assertThat(balances).hasSize(2)
        assertThat(AccountBalanceCalculator.totalBalance(balances)).isEqualTo(Money.ofBdt(1_000))
    }

    @Test
    fun `a credit card carrying debt reduces the total`() {
        val bank = TestData.account("acc-bank", openingTaka = 10_000)
        val card = TestData.account("acc-card", openingTaka = 0, type = com.familyfinance.app.domain.model.AccountType.CREDIT_CARD)
        val transactions = listOf(TestData.expense(taka = 3_000, accountId = "acc-card"))

        val balances = AccountBalanceCalculator.balances(listOf(bank, card), transactions)
        assertThat(AccountBalanceCalculator.totalBalance(balances)).isEqualTo(Money.ofBdt(7_000))
    }

    @Test
    fun `transaction effect reports which accounts it touches`() {
        val transfer = TestData.transfer(taka = 100, from = "a", to = "b")
        assertThat(TransactionEffect.affectsAccount(transfer, "a")).isTrue()
        assertThat(TransactionEffect.affectsAccount(transfer, "b")).isTrue()
        assertThat(TransactionEffect.affectsAccount(transfer, "c")).isFalse()
        assertThat(TransactionEffect.delta(transfer, "a")).isEqualTo(Money.ofBdt(-100))
        assertThat(TransactionEffect.delta(transfer, "b")).isEqualTo(Money.ofBdt(100))
        assertThat(TransactionEffect.delta(transfer, "c")).isEqualTo(Money.ZERO)
    }
}
