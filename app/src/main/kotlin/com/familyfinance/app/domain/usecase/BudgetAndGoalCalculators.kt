package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyMath
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.BudgetAlert
import com.familyfinance.app.domain.model.BudgetStatus
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.GoalProgress
import com.familyfinance.app.domain.model.GoalStatus
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Budget vs actual.
 *
 * `actual` is always a **family-wide** total for the budget's category and period. The calculator
 * never receives member-attributed data, so a budget row can never disclose who spent.
 */
object BudgetStatusCalculator {

    const val WARNING_THRESHOLD = 75
    const val CRITICAL_THRESHOLD = 90

    fun status(budget: FamilyBudget, actual: Money): BudgetStatus {
        val used = MoneyMath.percentageOf(actual, budget.limit)
        val remaining = budget.limit - actual
        val alert = when {
            used >= 100 -> BudgetAlert.EXCEEDED
            used >= CRITICAL_THRESHOLD -> BudgetAlert.CRITICAL
            used >= WARNING_THRESHOLD -> BudgetAlert.WARNING
            else -> BudgetAlert.NONE
        }
        return BudgetStatus(
            budget = budget,
            actual = actual,
            remaining = remaining,
            usedPercent = used,
            alert = alert,
        )
    }

    /**
     * Family-wide spend for a budget window, computed from transactions that have already been
     * scoped to the family. Member identity is never read.
     */
    fun actualFor(budget: FamilyBudget, familyTransactions: List<Transaction>): Money {
        val matching = familyTransactions.filter { transaction ->
            transaction.type == TransactionType.EXPENSE &&
                transaction.dateEpochDay in budget.periodEpochDayStart..budget.periodEpochDayEnd &&
                (budget.categoryId == null || transaction.categoryId == budget.categoryId)
        }
        return Money.sum(matching.map { it.amount })
    }

    fun monthlyWindow(month: YearMonth): Pair<Long, Long> {
        val range = AppTime.monthRange(month)
        return range.first to range.last
    }
}

/** Progress for family goals and private savings goals. */
object GoalProgressCalculator {

    fun familyGoal(goal: FamilyGoal, today: LocalDate = AppTime.today()): GoalProgress =
        progress(
            name = goal.name,
            target = goal.targetAmount,
            current = goal.currentAmount,
            targetEpochDay = goal.targetEpochDay,
            isAchievedStatus = goal.status == GoalStatus.ACHIEVED,
            today = today,
        )

    fun savingsGoal(goal: SavingsGoal, today: LocalDate = AppTime.today()): GoalProgress =
        progress(
            name = goal.name,
            target = goal.targetAmount,
            current = goal.currentAmount,
            targetEpochDay = goal.targetEpochDay,
            isAchievedStatus = goal.status == GoalStatus.ACHIEVED,
            today = today,
        )

    fun progress(
        name: String,
        target: Money,
        current: Money,
        targetEpochDay: Long? = null,
        isAchievedStatus: Boolean = false,
        today: LocalDate = AppTime.today(),
    ): GoalProgress {
        val safeCurrent = if (current.isNegative) Money.ZERO else current
        val remaining = (target - safeCurrent).let { if (it.isNegative) Money.ZERO else it }
        val percent = MoneyMath.percentageOf(safeCurrent, target)

        val daysRemaining = targetEpochDay?.let { ChronoUnit.DAYS.between(today, AppTime.fromEpochDay(it)) }

        // Months left is rounded up so the required monthly amount is never understated.
        val monthsRemaining = daysRemaining?.let { days ->
            if (days <= 0L) 0 else ((days + 29L) / 30L).toInt()
        }
        val requiredPerMonth = when {
            remaining.isZero -> Money.ZERO
            monthsRemaining == null -> null
            monthsRemaining <= 0 -> remaining
            else -> MoneyMath.divide(remaining, monthsRemaining)
        }

        return GoalProgress(
            name = name,
            target = target,
            current = safeCurrent,
            remaining = remaining,
            percent = percent,
            isAchieved = isAchievedStatus || (target.isPositive && safeCurrent >= target),
            targetEpochDay = targetEpochDay,
            daysRemaining = daysRemaining,
            requiredPerMonth = requiredPerMonth,
        )
    }
}

/** Family bill helpers: due-date urgency without exposing who is going to pay. */
object BillStatusCalculator {

    fun effectiveStatus(status: com.familyfinance.app.domain.model.BillStatus, dueEpochDay: Long, today: LocalDate = AppTime.today()): com.familyfinance.app.domain.model.BillStatus {
        if (status == com.familyfinance.app.domain.model.BillStatus.PAID) return status
        if (status == com.familyfinance.app.domain.model.BillStatus.SKIPPED) return status
        return if (dueEpochDay < today.toEpochDay()) {
            com.familyfinance.app.domain.model.BillStatus.OVERDUE
        } else {
            com.familyfinance.app.domain.model.BillStatus.PENDING
        }
    }

    fun isDueSoon(dueEpochDay: Long, withinDays: Int = 7, today: LocalDate = AppTime.today()): Boolean {
        val days = ChronoUnit.DAYS.between(today, AppTime.fromEpochDay(dueEpochDay))
        return days in 0..withinDays.toLong()
    }
}
