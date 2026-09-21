package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.CategoryTotal
import com.familyfinance.app.domain.model.MonthlyPoint
import com.familyfinance.app.domain.model.PersonalSummary
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import java.time.YearMonth

/**
 * Computes the private "My Finance" summary for exactly one owner.
 *
 * Every entry point takes the owner's uid and filters on it. There is no code path that produces a
 * [PersonalSummary] spanning more than one uid — the type simply cannot represent it.
 */
object PersonalSummaryCalculator {

    fun compute(
        ownerUid: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        month: YearMonth = AppTime.currentMonth(),
        trendMonths: Int = 6,
    ): PersonalSummary {
        val mine = transactions.filter { it.ownerUid == ownerUid }
        val myAccounts = accounts.filter { it.ownerUid == ownerUid }

        val balances = AccountBalanceCalculator.balances(myAccounts, mine)
        val monthRange = AppTime.monthRange(month)
        val inMonth = mine.filter { it.dateEpochDay in monthRange }

        val monthIncome = sumByType(inMonth, TransactionType.INCOME)
        val monthExpense = sumByType(inMonth, TransactionType.EXPENSE)

        return PersonalSummary(
            totalBalance = AccountBalanceCalculator.totalBalance(balances),
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            monthSavings = monthIncome - monthExpense,
            accountBalances = balances,
            expenseByCategory = categoryTotals(inMonth, TransactionType.EXPENSE),
            incomeByCategory = categoryTotals(inMonth, TransactionType.INCOME),
            monthlyTrend = trend(mine, month, trendMonths),
        )
    }

    fun sumByType(transactions: List<Transaction>, type: TransactionType): Money =
        Money.sum(transactions.filter { it.type == type }.map { it.amount })

    /** Category totals. Transfers are excluded — they are not a category of spending. */
    fun categoryTotals(transactions: List<Transaction>, type: TransactionType): List<CategoryTotal> {
        require(type != TransactionType.TRANSFER) { "transfers have no category" }
        return transactions
            .asSequence()
            .filter { it.type == type }
            .groupBy { it.categoryId ?: UNCATEGORISED }
            .map { (categoryId, rows) -> CategoryTotal(categoryId, Money.sum(rows.map { it.amount })) }
            .sortedByDescending { it.total.minor }
    }

    fun trend(transactions: List<Transaction>, endMonth: YearMonth, months: Int): List<MonthlyPoint> {
        if (months <= 0) return emptyList()
        val points = ArrayList<MonthlyPoint>(months)
        for (offset in months - 1 downTo 0) {
            val month = endMonth.minusMonths(offset.toLong())
            val range = AppTime.monthRange(month)
            val inMonth = transactions.filter { it.dateEpochDay in range }
            val income = sumByType(inMonth, TransactionType.INCOME)
            val expense = sumByType(inMonth, TransactionType.EXPENSE)
            points += MonthlyPoint(month, income, expense, income - expense)
        }
        return points
    }

    const val UNCATEGORISED = "uncategorised"
}
