package com.familyfinance.app.domain.model

import com.familyfinance.app.core.money.Money
import java.time.YearMonth

/**
 * A per-member roll-up produced **server-side** (Cloud Function) from that member's private
 * transactions.
 *
 * This is the ONLY shape in which an individual's numbers ever cross the trust boundary, and even
 * then it is scoped to the owning member. The family aggregate is built from these by summing; no
 * member key survives into [FamilyAggregate].
 */
data class MemberAggregateContribution(
    /** Opaque per-member key used only to de-duplicate contributions. Never rendered. */
    val memberKey: String,
    val income: Money,
    val expense: Money,
    val savings: Money,
)

data class CategoryTotal(
    val categoryId: String,
    val total: Money,
)

data class MonthlyPoint(
    val month: YearMonth,
    val income: Money,
    val expense: Money,
    val savings: Money,
)

/**
 * The common family dashboard payload.
 *
 * NOTE: there is deliberately no member-level field on this type. Adding one would make the
 * privacy guarantee unenforceable, so `FamilyAggregateTest` asserts the shape stays aggregate-only.
 */
data class FamilyAggregate(
    val month: YearMonth,
    val totalIncome: Money,
    val totalExpense: Money,
    val totalSavings: Money,
    /** Null when the family has not opted in to net-worth reporting. */
    val netWorth: Money? = null,
    val memberCount: Int = 0,
    val expenseByCategory: List<CategoryTotal> = emptyList(),
    val monthlyTrend: List<MonthlyPoint> = emptyList(),
    val previousMonthIncome: Money = Money.ZERO,
    val previousMonthExpense: Money = Money.ZERO,
) {
    val incomeChangePercent: Int
        get() = if (previousMonthIncome.isZero) 0
        else com.familyfinance.app.core.money.MoneyMath.signedPercentageOf(
            totalIncome - previousMonthIncome, previousMonthIncome
        )

    val expenseChangePercent: Int
        get() = if (previousMonthExpense.isZero) 0
        else com.familyfinance.app.core.money.MoneyMath.signedPercentageOf(
            totalExpense - previousMonthExpense, previousMonthExpense
        )

    companion object {
        fun empty(month: YearMonth) = FamilyAggregate(
            month = month,
            totalIncome = Money.ZERO,
            totalExpense = Money.ZERO,
            totalSavings = Money.ZERO,
        )
    }
}

/** One account and its derived live balance (opening balance ± all movements). */
data class AccountBalance(
    val account: Account,
    val balance: Money,
)

/** The private "My Finance" summary. Only ever computed for the signed-in owner. */
data class PersonalSummary(
    val totalBalance: Money,
    val monthIncome: Money,
    val monthExpense: Money,
    val monthSavings: Money,
    val accountBalances: List<AccountBalance> = emptyList(),
    val expenseByCategory: List<CategoryTotal> = emptyList(),
    val incomeByCategory: List<CategoryTotal> = emptyList(),
    val monthlyTrend: List<MonthlyPoint> = emptyList(),
) {
    companion object {
        val EMPTY = PersonalSummary(
            totalBalance = Money.ZERO,
            monthIncome = Money.ZERO,
            monthExpense = Money.ZERO,
            monthSavings = Money.ZERO,
        )
    }
}

/** Budget vs actual, computed from family-wide totals only. */
data class BudgetStatus(
    val budget: FamilyBudget,
    val actual: Money,
    val remaining: Money,
    val usedPercent: Int,
    val alert: BudgetAlert,
)

/** Goal progress for either a family goal or a private savings goal. */
data class GoalProgress(
    val name: String,
    val target: Money,
    val current: Money,
    val remaining: Money,
    val percent: Int,
    val isAchieved: Boolean,
    val targetEpochDay: Long? = null,
    val daysRemaining: Long? = null,
    val requiredPerMonth: Money? = null,
)
