package com.familyfinance.app.testutil

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AccountType
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType

/**
 * Test fixtures.
 *
 * Amounts are round numbers and every id is obviously synthetic, so a failing assertion is easy to
 * read and no test can accidentally depend on production-shaped data.
 */
object TestData {

    const val FAMILY_ID = "family-1"
    const val ALICE = "uid-alice"
    const val BOB = "uid-bob"

    fun account(
        id: String,
        ownerUid: String = ALICE,
        openingTaka: Long = 0,
        type: AccountType = AccountType.BANK,
        active: Boolean = true,
    ) = Account(
        id = id,
        ownerUid = ownerUid,
        familyId = FAMILY_ID,
        name = "Account $id",
        type = type,
        openingBalance = Money.ofBdt(openingTaka),
        isActive = active,
    )

    fun income(
        id: String = "tx-income",
        ownerUid: String = ALICE,
        taka: Long,
        accountId: String,
        categoryId: String = "salary",
        epochDay: Long = 20_000L,
    ) = Transaction(
        id = id,
        clientId = "client-$id",
        ownerUid = ownerUid,
        familyId = FAMILY_ID,
        type = TransactionType.INCOME,
        amount = Money.ofBdt(taka),
        categoryId = categoryId,
        accountId = accountId,
        dateEpochDay = epochDay,
    )

    fun expense(
        id: String = "tx-expense",
        ownerUid: String = ALICE,
        taka: Long,
        accountId: String,
        categoryId: String = "food_grocery",
        epochDay: Long = 20_000L,
    ) = Transaction(
        id = id,
        clientId = "client-$id",
        ownerUid = ownerUid,
        familyId = FAMILY_ID,
        type = TransactionType.EXPENSE,
        amount = Money.ofBdt(taka),
        categoryId = categoryId,
        accountId = accountId,
        dateEpochDay = epochDay,
    )

    fun transfer(
        id: String = "tx-transfer",
        ownerUid: String = ALICE,
        taka: Long,
        from: String,
        to: String,
        epochDay: Long = 20_000L,
    ) = Transaction(
        id = id,
        clientId = "client-$id",
        ownerUid = ownerUid,
        familyId = FAMILY_ID,
        type = TransactionType.TRANSFER,
        amount = Money.ofBdt(taka),
        transferFromAccountId = from,
        transferToAccountId = to,
        dateEpochDay = epochDay,
    )

    fun budget(
        id: String = "budget-1",
        categoryId: String? = "food_grocery",
        limitTaka: Long = 20_000,
        startEpochDay: Long = 20_000L,
        endEpochDay: Long = 20_030L,
    ) = FamilyBudget(
        id = id,
        familyId = FAMILY_ID,
        name = "Budget $id",
        categoryId = categoryId,
        periodEpochDayStart = startEpochDay,
        periodEpochDayEnd = endEpochDay,
        limit = Money.ofBdt(limitTaka),
        createdByUid = ALICE,
    )

    fun goal(
        id: String = "goal-1",
        targetTaka: Long = 100_000,
        currentTaka: Long = 40_000,
        targetEpochDay: Long? = null,
    ) = FamilyGoal(
        id = id,
        familyId = FAMILY_ID,
        name = "Goal $id",
        categoryId = "emergency_fund",
        targetAmount = Money.ofBdt(targetTaka),
        currentAmount = Money.ofBdt(currentTaka),
        targetEpochDay = targetEpochDay,
        createdByUid = ALICE,
    )
}
