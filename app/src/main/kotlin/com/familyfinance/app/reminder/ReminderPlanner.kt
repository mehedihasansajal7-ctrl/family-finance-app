package com.familyfinance.app.reminder

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.BillStatus
import com.familyfinance.app.domain.model.BudgetAlert
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.usecase.BudgetStatusCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A reminder the worker should surface. Contains no member-attributed or lock-screen-sensitive data. */
data class ReminderRequest(
    val categoryId: String,
    val relatedId: String,
    val title: String,
    val body: String,
)

/**
 * Decides which bill and budget reminders are due.
 *
 * Pure and side-effect free so the "should we remind today?" decision is fully unit-testable without
 * a device. Amounts are deliberately kept out of the notification body (matching the app's
 * lock-screen privacy rule): a reminder says *what* is due and *when*, never a balance.
 */
object ReminderPlanner {

    const val CATEGORY_BILL_DUE = "bill_due"
    const val CATEGORY_BUDGET_ALERT = "budget_alert"

    /** Bills that are unpaid and fall inside their own reminder window (or are one day overdue). */
    fun dueBills(bills: List<FamilyBill>, today: LocalDate = AppTime.today()): List<ReminderRequest> =
        bills.filter { isWithinReminderWindow(it, today) }.map { bill ->
            val days = ChronoUnit.DAYS.between(today, AppTime.fromEpochDay(bill.dueEpochDay))
            val whenText = when {
                days < 0L -> "was due ${AppTime.formatEpochDay(bill.dueEpochDay)}"
                days == 0L -> "is due today"
                days == 1L -> "is due tomorrow"
                else -> "is due in $days days (${AppTime.formatEpochDay(bill.dueEpochDay)})"
            }
            ReminderRequest(
                categoryId = CATEGORY_BILL_DUE,
                relatedId = bill.id,
                title = "Bill reminder",
                body = "\"${bill.name}\" $whenText.",
            )
        }

    private fun isWithinReminderWindow(bill: FamilyBill, today: LocalDate): Boolean {
        if (!bill.reminderEnabled) return false
        if (bill.status == BillStatus.PAID || bill.status == BillStatus.SKIPPED) return false
        val days = ChronoUnit.DAYS.between(today, AppTime.fromEpochDay(bill.dueEpochDay))
        val window = bill.reminderDaysBefore.coerceIn(1, 60).toLong()
        // Fire from `window` days before through one day after, so a missed day still nudges once.
        return days in -1..window
    }

    /**
     * Budgets whose current-period spend has crossed a warning/critical/exceeded threshold.
     * [actualByBudgetId] is the family-wide spend for each budget, as supplied by the aggregate.
     */
    fun budgetAlerts(
        budgets: List<FamilyBudget>,
        actualByBudgetId: Map<String, Money>,
        today: LocalDate = AppTime.today(),
    ): List<ReminderRequest> =
        budgets.filter { it.coversDate(today.toEpochDay()) }.mapNotNull { budget ->
            val actual = actualByBudgetId[budget.id] ?: Money.ZERO
            val status = BudgetStatusCalculator.status(budget, actual)
            if (status.alert == BudgetAlert.NONE) return@mapNotNull null
            val body = when (status.alert) {
                BudgetAlert.EXCEEDED ->
                    "\"${budget.name}\" is over its limit (${status.usedPercent}% used this period)."
                BudgetAlert.CRITICAL ->
                    "\"${budget.name}\" is close to its limit (${status.usedPercent}% used this period)."
                else ->
                    "\"${budget.name}\" has used ${status.usedPercent}% of its limit this period."
            }
            ReminderRequest(
                categoryId = "$CATEGORY_BUDGET_ALERT:${status.alert.name}",
                relatedId = budget.id,
                title = "Budget alert",
                body = body,
            )
        }
}
