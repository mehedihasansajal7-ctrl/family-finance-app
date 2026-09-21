package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyMath
import com.familyfinance.app.domain.model.CategoryTotal
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.MemberAggregateContribution
import com.familyfinance.app.domain.model.MonthlyPoint
import java.time.YearMonth

/**
 * Input to the family aggregate.
 *
 * Per-member contributions are supplied by the backend aggregate function. They are summed here and
 * then discarded — [FamilyAggregate] has no member-level field, so nothing member-specific can
 * reach the UI, the cache, or the wire.
 */
data class FamilyAggregateInput(
    val month: YearMonth,
    val contributions: List<MemberAggregateContribution> = emptyList(),
    /** Family-wide category totals, already stripped of member attribution by the backend. */
    val expenseByCategory: List<CategoryTotal> = emptyList(),
    val monthlyTrend: List<MonthlyPoint> = emptyList(),
    val previousMonthIncome: Money = Money.ZERO,
    val previousMonthExpense: Money = Money.ZERO,
    /** Only present when the family enabled net-worth reporting. */
    val netWorth: Money? = null,
)

/**
 * Builds the common family dashboard payload.
 *
 * ## Privacy contract
 * The output type contains no uid, no member name, no per-member amount and no account identifier.
 * `FamilyAggregateCalculatorTest` asserts this shape so a future edit cannot quietly widen it.
 */
object FamilyAggregateCalculator {

    fun aggregate(input: FamilyAggregateInput): FamilyAggregate {
        val totalIncome = Money.sum(input.contributions.map { it.income })
        val totalExpense = Money.sum(input.contributions.map { it.expense })
        val totalSavings = Money.sum(input.contributions.map { it.savings })

        return FamilyAggregate(
            month = input.month,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            totalSavings = totalSavings,
            netWorth = input.netWorth,
            memberCount = input.contributions.size,
            expenseByCategory = input.expenseByCategory.sortedByDescending { it.total.minor },
            monthlyTrend = input.monthlyTrend,
            previousMonthIncome = input.previousMonthIncome,
            previousMonthExpense = input.previousMonthExpense,
        )
    }

    /** Convenience for the local/demo path, where per-member figures are available on-device. */
    fun aggregateFromMembers(
        month: YearMonth,
        members: List<MemberContributionSource>,
        expenseByCategory: List<CategoryTotal> = emptyList(),
        monthlyTrend: List<MonthlyPoint> = emptyList(),
        previousMonthIncome: Money = Money.ZERO,
        previousMonthExpense: Money = Money.ZERO,
        netWorth: Money? = null,
    ): FamilyAggregate = aggregate(
        FamilyAggregateInput(
            month = month,
            contributions = members.map {
                MemberAggregateContribution(it.memberKey, it.income, it.expense, it.savings)
            },
            expenseByCategory = expenseByCategory,
            monthlyTrend = monthlyTrend,
            previousMonthIncome = previousMonthIncome,
            previousMonthExpense = previousMonthExpense,
            netWorth = netWorth,
        )
    )

    /**
     * Contribution share of each category, as a percentage of total family spending.
     * Category totals are family-wide, so this reveals nothing about who spent.
     */
    fun categoryShares(aggregate: FamilyAggregate): List<Pair<String, Int>> =
        aggregate.expenseByCategory.map { it.categoryId to MoneyMath.percentageOf(it.total, aggregate.totalExpense) }
}

/** A single member's roll-up, as held on the server before it is folded into the aggregate. */
data class MemberContributionSource(
    val memberKey: String,
    val income: Money,
    val expense: Money,
) {
    val savings: Money get() = income - expense
}
