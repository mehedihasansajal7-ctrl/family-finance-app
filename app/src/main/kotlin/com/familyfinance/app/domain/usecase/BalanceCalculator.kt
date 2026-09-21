package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AccountBalance
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType

/**
 * The effect of a single transaction on a single account.
 *
 * This is the double-entry core. Internal transfers move value between two accounts of the *same*
 * owner and are therefore neither income nor expense — a transfer never appears in any
 * income/expense total, personal or family.
 */
object TransactionEffect {

    fun affectsAccount(transaction: Transaction, accountId: String): Boolean = when (transaction.type) {
        TransactionType.INCOME, TransactionType.EXPENSE -> transaction.accountId == accountId
        TransactionType.TRANSFER ->
            transaction.transferFromAccountId == accountId || transaction.transferToAccountId == accountId
    }

    /** Signed change this transaction applies to [accountId]. Zero when it does not touch it. */
    fun delta(transaction: Transaction, accountId: String): Money = when (transaction.type) {
        TransactionType.INCOME ->
            if (transaction.accountId == accountId) transaction.amount else Money.ZERO

        TransactionType.EXPENSE ->
            if (transaction.accountId == accountId) -transaction.amount else Money.ZERO

        TransactionType.TRANSFER -> when (accountId) {
            transaction.transferFromAccountId -> -transaction.amount
            transaction.transferToAccountId -> transaction.amount
            else -> Money.ZERO
        }
    }
}

/**
 * Derives live account balances:
 *
 *   opening + income + transferIn - expense - transferOut = current
 *
 * Transactions are filtered by owner as a defence-in-depth measure even when the caller has already
 * scoped the query — a mis-scoped repository must not be able to produce a wrong balance.
 */
object AccountBalanceCalculator {

    fun balance(account: Account, transactions: List<Transaction>): Money {
        var total = account.openingBalance
        for (transaction in transactions) {
            if (transaction.ownerUid != account.ownerUid) continue
            if (!TransactionEffect.affectsAccount(transaction, account.id)) continue
            total += TransactionEffect.delta(transaction, account.id)
        }
        return total
    }

    fun balances(accounts: List<Account>, transactions: List<Transaction>): List<AccountBalance> {
        val byAccount: Map<String, List<Transaction>> = transactions
            .flatMap { transaction ->
                val touched = when (transaction.type) {
                    TransactionType.INCOME, TransactionType.EXPENSE ->
                        listOfNotNull(transaction.accountId)
                    TransactionType.TRANSFER ->
                        listOfNotNull(transaction.transferFromAccountId, transaction.transferToAccountId)
                }
                touched.map { it to transaction }
            }
            .groupBy({ it.first }, { it.second })
        return accounts.map { account ->
            val relevant = byAccount[account.id].orEmpty()
            AccountBalance(account, balance(account, relevant))
        }
    }

    /** Sum of every active account. Credit lines are subtracted, since they are liabilities. */
    fun totalBalance(balances: List<AccountBalance>): Money =
        Money.sum(balances.filter { it.account.isActive }.map { it.balance })
}
