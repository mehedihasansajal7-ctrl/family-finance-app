package com.familyfinance.app.reminder

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.BillStatus
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyBudget
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReminderPlannerTest {

    private val today = AppTime.today()
    private val todayEpoch = today.toEpochDay()

    private fun bill(
        id: String = "b1",
        dueInDays: Long = 2,
        status: BillStatus = BillStatus.PENDING,
        reminderEnabled: Boolean = true,
        reminderDaysBefore: Int = 3,
    ) = FamilyBill(
        id = id,
        familyId = "fam",
        name = "Electricity",
        categoryId = "electricity",
        expectedAmount = Money.ofMinor(120000),
        dueEpochDay = todayEpoch + dueInDays,
        status = status,
        reminderEnabled = reminderEnabled,
        reminderDaysBefore = reminderDaysBefore,
        createdByUid = "u1",
    )

    private fun budget(
        id: String = "bud1",
        categoryId: String? = "food_grocery",
        limitMinor: Long = 100000,
        periodStart: Long = todayEpoch - 5,
        periodEnd: Long = todayEpoch + 5,
    ) = FamilyBudget(
        id = id,
        familyId = "fam",
        name = "Food",
        categoryId = categoryId,
        periodEpochDayStart = periodStart,
        periodEpochDayEnd = periodEnd,
        limit = Money.ofMinor(limitMinor),
        createdByUid = "u1",
    )

    @Test
    fun `reminds a bill that is due inside its window`() {
        val reminders = ReminderPlanner.dueBills(listOf(bill(dueInDays = 2)), today)
        assertThat(reminders).hasSize(1)
        assertThat(reminders.first().categoryId).isEqualTo(ReminderPlanner.CATEGORY_BILL_DUE)
        assertThat(reminders.first().relatedId).isEqualTo("b1")
    }

    @Test
    fun `reminds a bill due today and tomorrow`() {
        assertThat(ReminderPlanner.dueBills(listOf(bill(id = "t", dueInDays = 0)), today)).hasSize(1)
        assertThat(ReminderPlanner.dueBills(listOf(bill(id = "m", dueInDays = 1)), today)).hasSize(1)
    }

    @Test
    fun `skips a bill that is too far away`() {
        val reminders = ReminderPlanner.dueBills(listOf(bill(dueInDays = 30, reminderDaysBefore = 3)), today)
        assertThat(reminders).isEmpty()
    }

    @Test
    fun `skips paid, skipped, disabled and already-past-window bills`() {
        assertThat(ReminderPlanner.dueBills(listOf(bill(status = BillStatus.PAID)), today)).isEmpty()
        assertThat(ReminderPlanner.dueBills(listOf(bill(status = BillStatus.SKIPPED)), today)).isEmpty()
        assertThat(ReminderPlanner.dueBills(listOf(bill(reminderEnabled = false)), today)).isEmpty()
        assertThat(ReminderPlanner.dueBills(listOf(bill(dueInDays = -10)), today)).isEmpty()
    }

    @Test
    fun `nudges once for a bill that is one day overdue`() {
        assertThat(ReminderPlanner.dueBills(listOf(bill(dueInDays = -1)), today)).hasSize(1)
    }

    @Test
    fun `no budget alert when spending is under the warning threshold`() {
        val alerts = ReminderPlanner.budgetAlerts(
            budgets = listOf(budget()),
            actualByBudgetId = mapOf("bud1" to Money.ofMinor(50000)), // 50%
            today = today,
        )
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `warns, then escalates at critical and exceeded thresholds`() {
        val warn = ReminderPlanner.budgetAlerts(
            listOf(budget()),
            mapOf("bud1" to Money.ofMinor(80000)), // 80%
            today,
        )
        assertThat(warn).hasSize(1)
        assertThat(warn.first().categoryId).endsWith("WARNING")

        val critical = ReminderPlanner.budgetAlerts(
            listOf(budget()),
            mapOf("bud1" to Money.ofMinor(95000)), // 95%
            today,
        )
        assertThat(critical.first().categoryId).endsWith("CRITICAL")

        val exceeded = ReminderPlanner.budgetAlerts(
            listOf(budget()),
            mapOf("bud1" to Money.ofMinor(120000)), // 120%
            today,
        )
        assertThat(exceeded.first().categoryId).endsWith("EXCEEDED")
    }

    @Test
    fun `ignores a budget whose period does not cover today`() {
        val past = budget(periodStart = todayEpoch - 40, periodEnd = todayEpoch - 30)
        val alerts = ReminderPlanner.budgetAlerts(listOf(past), mapOf("bud1" to Money.ofMinor(999999)), today)
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `reminder body never contains a currency amount`() {
        val reminders = ReminderPlanner.dueBills(listOf(bill(dueInDays = 1)), today)
        assertThat(reminders.first().body).doesNotContain("৳")
        assertThat(reminders.first().body).doesNotContain("BDT")
    }
}
