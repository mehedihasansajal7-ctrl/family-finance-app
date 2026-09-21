package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.testutil.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class TransactionValidatorTest {

    private val today = LocalDate.of(2026, 9, 20)
    private val owned = setOf("acc-1", "acc-2")

    private fun validate(
        transaction: Transaction,
        ownedAccountIds: Set<String> = owned,
        balances: Map<String, Money> = emptyMap(),
        enforce: Boolean = false,
    ) = TransactionValidator.validate(transaction, ownedAccountIds, balances, today, enforce)

    @Test
    fun `a well-formed expense is valid`() {
        val result = validate(TestData.expense(taka = 500, accountId = "acc-1", epochDay = today.toEpochDay()))
        assertThat(result.isValid).isTrue()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun `a well-formed transfer is valid`() {
        val result = validate(TestData.transfer(taka = 500, from = "acc-1", to = "acc-2", epochDay = today.toEpochDay()))
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `amount must be greater than zero`() {
        assertThat(validate(TestData.expense(taka = 0, accountId = "acc-1", epochDay = today.toEpochDay())).isValid).isFalse()
    }

    @Test
    fun `an absurdly large amount is rejected`() {
        val huge = TestData.expense(taka = 1, accountId = "acc-1", epochDay = today.toEpochDay())
            .copy(amount = Money.ofMinor(TransactionValidator.MAX_AMOUNT_MINOR + 1))
        assertThat(validate(huge).isValid).isFalse()
    }

    @Test
    fun `an income or expense must name an account`() {
        val noAccount = TestData.expense(taka = 100, accountId = "acc-1", epochDay = today.toEpochDay()).copy(accountId = null)
        assertThat(validate(noAccount).isValid).isFalse()
    }

    @Test
    fun `an account that does not belong to the user is rejected`() {
        val foreign = TestData.expense(taka = 100, accountId = "someone-elses-account", epochDay = today.toEpochDay())
        val result = validate(foreign)
        assertThat(result.isValid).isFalse()
        assertThat(result.firstError()).contains("does not belong")
    }

    @Test
    fun `a category is required for income and expense`() {
        val noCategory = TestData.expense(taka = 100, accountId = "acc-1", epochDay = today.toEpochDay()).copy(categoryId = null)
        assertThat(validate(noCategory).isValid).isFalse()
    }

    @Test
    fun `a transfer may not use the same account twice`() {
        val sameAccount = TestData.transfer(taka = 100, from = "acc-1", to = "acc-1", epochDay = today.toEpochDay())
        val result = validate(sameAccount)
        assertThat(result.isValid).isFalse()
        assertThat(result.firstError()).contains("different accounts")
    }

    @Test
    fun `a transfer must name both accounts`() {
        val missingTo = TestData.transfer(taka = 100, from = "acc-1", to = "acc-2", epochDay = today.toEpochDay())
            .copy(transferToAccountId = null)
        assertThat(validate(missingTo).isValid).isFalse()
    }

    @Test
    fun `a transfer may not reference an account the user does not own`() {
        val foreign = TestData.transfer(taka = 100, from = "acc-1", to = "acc-other", epochDay = today.toEpochDay())
        assertThat(validate(foreign).isValid).isFalse()
    }

    @Test
    fun `a transfer must not carry a category or a single account`() {
        val withCategory = TestData.transfer(taka = 100, from = "acc-1", to = "acc-2", epochDay = today.toEpochDay())
            .copy(categoryId = "food_grocery")
        val withAccount = TestData.transfer(taka = 100, from = "acc-1", to = "acc-2", epochDay = today.toEpochDay())
            .copy(accountId = "acc-1")

        assertThat(validate(withCategory).isValid).isFalse()
        assertThat(validate(withAccount).isValid).isFalse()
    }

    @Test
    fun `an income may not reference transfer accounts`() {
        val hybrid = TestData.income(taka = 100, accountId = "acc-1", epochDay = today.toEpochDay())
            .copy(transferFromAccountId = "acc-2")
        assertThat(validate(hybrid).isValid).isFalse()
    }

    @Test
    fun `dates before 1971 and far in the future are rejected`() {
        val ancient = TestData.expense(taka = 100, accountId = "acc-1", epochDay = LocalDate.of(1969, 1, 1).toEpochDay())
        val farFuture = TestData.expense(taka = 100, accountId = "acc-1", epochDay = today.plusYears(11).toEpochDay())
        assertThat(validate(ancient).isValid).isFalse()
        assertThat(validate(farFuture).isValid).isFalse()
    }

    @Test
    fun `a future-dated transaction within range is allowed`() {
        // Scheduled entries are legitimate; only absurd ranges are blocked.
        val future = TestData.expense(taka = 100, accountId = "acc-1", epochDay = today.plusDays(5).toEpochDay())
        assertThat(validate(future).isValid).isTrue()
    }

    @Test
    fun `an over-long note is rejected`() {
        val verbose = TestData.expense(taka = 100, accountId = "acc-1", epochDay = today.toEpochDay())
            .copy(note = "x".repeat(TransactionValidator.MAX_NOTE_LENGTH + 1))
        assertThat(validate(verbose).isValid).isFalse()
    }

    @Test
    fun `an expense exceeding the balance is a warning by default`() {
        val result = validate(
            TestData.expense(taka = 5_000, accountId = "acc-1", epochDay = today.toEpochDay()),
            balances = mapOf("acc-1" to Money.ofBdt(1_000)),
        )
        assertThat(result.isValid).isTrue()
        assertThat(result.warnings).hasSize(1)
    }

    @Test
    fun `an expense exceeding the balance is an error when the caller opts in`() {
        val result = validate(
            TestData.expense(taka = 5_000, accountId = "acc-1", epochDay = today.toEpochDay()),
            balances = mapOf("acc-1" to Money.ofBdt(1_000)),
            enforce = true,
        )
        assertThat(result.isValid).isFalse()
    }

    @Test
    fun `a transfer exceeding the source balance is only an error when enforced`() {
        val transfer = TestData.transfer(taka = 5_000, from = "acc-1", to = "acc-2", epochDay = today.toEpochDay())
        val balances = mapOf("acc-1" to Money.ofBdt(100), "acc-2" to Money.ofBdt(0))

        assertThat(validate(transfer, balances = balances, enforce = false).isValid).isTrue()
        assertThat(validate(transfer, balances = balances, enforce = true).isValid).isFalse()
    }

    @Test
    fun `a transaction without an owner is rejected`() {
        val orphan = TestData.expense(taka = 100, accountId = "acc-1", epochDay = today.toEpochDay())
            .copy(ownerUid = "")
        assertThat(validate(orphan).isValid).isFalse()
    }

    @Test
    fun `forbidden credential fields are detected`() {
        assertThat(TransactionValidator.containsForbiddenFields(listOf("amount", "note"))).isFalse()
        assertThat(TransactionValidator.containsForbiddenFields(listOf("cardCvv"))).isTrue()
        assertThat(TransactionValidator.containsForbiddenFields(listOf("bkash_pin"))).isTrue()
        assertThat(TransactionValidator.containsForbiddenFields(listOf("accountPassword"))).isTrue()
        assertThat(TransactionValidator.containsForbiddenFields(listOf("otp"))).isTrue()
    }

    @Test
    fun `account ownership is checked against the account record, not the payload`() {
        val account = TestData.account("acc-1", ownerUid = TestData.ALICE)
        assertThat(TransactionValidator.validateAccountOwnership(account, TestData.ALICE)).isTrue()
        assertThat(TransactionValidator.validateAccountOwnership(account, TestData.BOB)).isFalse()
        assertThat(TransactionValidator.validateAccountOwnership(null, TestData.ALICE)).isFalse()
    }

    @Test
    fun `every transaction type is exercised at least once`() {
        // Guards against a future refactor that silently drops a branch of the when-expression.
        assertThat(TransactionType.entries).hasSize(3)
        TransactionType.entries.forEach { type ->
            val sample = when (type) {
                TransactionType.INCOME -> TestData.income(taka = 1, accountId = "acc-1", epochDay = today.toEpochDay())
                TransactionType.EXPENSE -> TestData.expense(taka = 1, accountId = "acc-1", epochDay = today.toEpochDay())
                TransactionType.TRANSFER -> TestData.transfer(taka = 1, from = "acc-1", to = "acc-2", epochDay = today.toEpochDay())
            }
            assertThat(validate(sample).isValid).isTrue()
        }
    }
}
