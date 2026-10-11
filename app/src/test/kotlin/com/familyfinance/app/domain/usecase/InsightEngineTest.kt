package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.domain.model.CategoryTotal
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.Insight
import com.familyfinance.app.domain.model.MonthlyPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.YearMonth

class InsightEngineTest {

    private val month = YearMonth.of(2026, 10)
    private val label: (String) -> String = { it.replace('_', ' ') }

    private fun aggregate(
        income: Long,
        expense: Long,
        savings: Long,
        prevIncome: Long,
        prevExpense: Long,
        categories: List<CategoryTotal> = emptyList(),
        trend: List<MonthlyPoint> = emptyList(),
    ) = FamilyAggregate(
        month = month,
        totalIncome = Money.ofMinor(income),
        totalExpense = Money.ofMinor(expense),
        totalSavings = Money.ofMinor(savings),
        netWorth = null,
        memberCount = 2,
        expenseByCategory = categories,
        monthlyTrend = trend,
        previousMonthIncome = Money.ofMinor(prevIncome),
        previousMonthExpense = Money.ofMinor(prevExpense),
    )

    @Test
    fun `reports spending and income changes over the threshold`() {
        val insights = InsightEngine.forAggregate(
            aggregate(income = 30000, expense = 15000, savings = 15000, prevIncome = 20000, prevExpense = 10000),
            label,
        )
        assertThat(insights.any { it.kind == Insight.Kind.UP && it.title.contains("Spending is up 50%") }).isTrue()
        assertThat(insights.any { it.title.contains("Income is up 50%") }).isTrue()
        assertThat(insights.any { it.title.contains("Savings rate 50%") }).isTrue()
    }

    @Test
    fun `ignores small changes`() {
        val insights = InsightEngine.forAggregate(
            aggregate(income = 10000, expense = 10500, savings = -500, prevIncome = 10000, prevExpense = 10000),
            label,
        )
        assertThat(insights.none { it.title.contains("Spending is") }).isTrue()
    }

    @Test
    fun `flags unusually high spending versus the recent average`() {
        val trend = listOf(
            MonthlyPoint(YearMonth.of(2026, 8), Money.ofMinor(0), Money.ofMinor(8000), Money.ofMinor(0)),
            MonthlyPoint(YearMonth.of(2026, 9), Money.ofMinor(0), Money.ofMinor(8000), Money.ofMinor(0)),
            MonthlyPoint(month, Money.ofMinor(0), Money.ofMinor(15000), Money.ofMinor(0)),
        )
        val insights = InsightEngine.forAggregate(
            aggregate(income = 0, expense = 15000, savings = 0, prevIncome = 0, prevExpense = 8000, trend = trend),
            label,
        )
        assertThat(insights.any { it.kind == Insight.Kind.WARNING && it.title.contains("Unusually high") }).isTrue()
    }

    @Test
    fun `reports the biggest spending category with the provided label`() {
        val insights = InsightEngine.forAggregate(
            aggregate(
                income = 0, expense = 9000, savings = 0, prevIncome = 0, prevExpense = 0,
                categories = listOf(CategoryTotal("food_grocery", Money.ofMinor(9000))),
            ),
            label,
        )
        assertThat(insights.any { it.title.contains("Biggest category: food grocery") }).isTrue()
    }
}
