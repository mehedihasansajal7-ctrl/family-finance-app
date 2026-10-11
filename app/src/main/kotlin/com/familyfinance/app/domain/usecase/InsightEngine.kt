package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.MoneyFormat
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.Insight
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Builds the monthly "insight digest" and anomaly flags from the aggregate.
 *
 * It reads only aggregate data (totals + category totals + trend), never a member's private rows, so
 * insights can never attribute spending to a person. Pure and deterministic, so it is unit-tested.
 */
object InsightEngine {

    private const val MIN_CHANGE_PERCENT = 15
    private const val ANOMALY_MULTIPLIER = 1.5

    fun forAggregate(a: FamilyAggregate, categoryLabel: (String) -> String): List<Insight> {
        val out = mutableListOf<Insight>()

        spendChange(a.previousMonthExpense.minor, a.totalExpense.minor)?.let { pct ->
            out += Insight(
                kind = if (pct > 0) Insight.Kind.UP else Insight.Kind.DOWN,
                title = if (pct > 0) "Spending is up $pct%" else "Spending is down ${-pct}%",
                detail = "compared with last month's total.",
            )
        }

        incomeChange(a.previousMonthIncome.minor, a.totalIncome.minor)?.let { pct ->
            if (pct > 0) {
                out += Insight(Insight.Kind.UP, "Income is up $pct%", "compared with last month.")
            }
        }

        if (a.totalIncome.minor > 0) {
            val rate = (a.totalSavings.minor * 100 / a.totalIncome.minor).toInt()
            out += Insight(
                kind = if (rate < 0) Insight.Kind.WARNING else Insight.Kind.INFO,
                title = "Savings rate $rate%",
                detail = "of this month's income was kept.",
            )
        }

        a.expenseByCategory.maxByOrNull { it.total.minor }?.takeIf { it.total.minor > 0 }?.let { top ->
            out += Insight(
                kind = Insight.Kind.INFO,
                title = "Biggest category: ${categoryLabel(top.categoryId)}",
                detail = MoneyFormat.bdt(top.total) + " this month.",
            )
        }

        val priorExpenses = a.monthlyTrend
            .filter { it.month != a.month }
            .map { it.expense.minor }
            .filter { it > 0 }
        if (priorExpenses.isNotEmpty() && a.totalExpense.minor > 0) {
            val average = priorExpenses.average()
            if (average > 0 && a.totalExpense.minor > average * ANOMALY_MULTIPLIER) {
                out += Insight(
                    kind = Insight.Kind.WARNING,
                    title = "Unusually high spending",
                    detail = "This month is well above your recent average.",
                )
            }
        }

        return out
    }

    private fun spendChange(previousMinor: Long, currentMinor: Long): Int? {
        if (previousMinor <= 0 || currentMinor <= 0) return null
        return changePercent(previousMinor, currentMinor)
    }

    private fun incomeChange(previousMinor: Long, currentMinor: Long): Int? {
        if (previousMinor <= 0 || currentMinor <= 0) return null
        return changePercent(previousMinor, currentMinor)
    }

    private fun changePercent(previousMinor: Long, currentMinor: Long): Int? {
        val pct = (((currentMinor - previousMinor).toDouble() / previousMinor) * 100).roundToInt()
        return if (abs(pct) >= MIN_CHANGE_PERCENT) pct else null
    }
}
