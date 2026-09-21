package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.BudgetAlert
import com.familyfinance.app.domain.model.GoalStatus
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.testutil.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetStatusCalculatorTest {

    @Test
    fun `reports remaining and percentage used`() {
        val budget = TestData.budget(limitTaka = 20_000)
        val status = BudgetStatusCalculator.status(budget, Money.ofBdt(5_000))

        assertThat(status.actual).isEqualTo(Money.ofBdt(5_000))
        assertThat(status.remaining).isEqualTo(Money.ofBdt(15_000))
        assertThat(status.usedPercent).isEqualTo(25)
        assertThat(status.alert).isEqualTo(BudgetAlert.NONE)
    }

    @Test
    fun `raises the warning alert at exactly seventy five percent`() {
        val status = BudgetStatusCalculator.status(TestData.budget(limitTaka = 20_000), Money.ofBdt(15_000))
        assertThat(status.usedPercent).isEqualTo(75)
        assertThat(status.alert).isEqualTo(BudgetAlert.WARNING)
    }

    @Test
    fun `raises the critical alert at exactly ninety percent`() {
        val status = BudgetStatusCalculator.status(TestData.budget(limitTaka = 20_000), Money.ofBdt(18_000))
        assertThat(status.alert).isEqualTo(BudgetAlert.CRITICAL)
    }

    @Test
    fun `flags an exceeded budget and shows a negative remaining`() {
        val status = BudgetStatusCalculator.status(TestData.budget(limitTaka = 20_000), Money.ofBdt(25_000))
        assertThat(status.alert).isEqualTo(BudgetAlert.EXCEEDED)
        assertThat(status.remaining).isEqualTo(Money.ofBdt(-5_000))
        assertThat(status.usedPercent).isEqualTo(125)
    }

    @Test
    fun `a budget with no spending is zero percent, not a divide by zero`() {
        val status = BudgetStatusCalculator.status(TestData.budget(limitTaka = 20_000), Money.ZERO)
        assertThat(status.usedPercent).isEqualTo(0)
        assertThat(status.alert).isEqualTo(BudgetAlert.NONE)
    }

    @Test
    fun `actual spending is family-wide and filtered by category and window`() {
        val budget = TestData.budget(categoryId = "food_grocery", startEpochDay = 100, endEpochDay = 200)
        val transactions = listOf(
            TestData.expense(id = "in-cat-in-window", ownerUid = TestData.ALICE, taka = 100, accountId = "a", categoryId = "food_grocery", epochDay = 150),
            TestData.expense(id = "in-cat-in-window-2", ownerUid = TestData.BOB, taka = 200, accountId = "b", categoryId = "food_grocery", epochDay = 199),
            TestData.expense(id = "wrong-cat", taka = 500, accountId = "a", categoryId = "housing_rent", epochDay = 150),
            TestData.expense(id = "outside-window", taka = 900, accountId = "a", categoryId = "food_grocery", epochDay = 500),
            TestData.income(id = "income", taka = 1_000, accountId = "a", categoryId = "salary", epochDay = 150),
        )

        val actual = BudgetStatusCalculator.actualFor(budget, transactions)

        // Two different members contributed, and the budget sees only the combined figure.
        assertThat(actual).isEqualTo(Money.ofBdt(300))
    }

    @Test
    fun `a budget with no category covers all spending in the window`() {
        val budget = TestData.budget(categoryId = null, startEpochDay = 100, endEpochDay = 200)
        val transactions = listOf(
            TestData.expense(id = "a", taka = 100, accountId = "x", categoryId = "food_grocery", epochDay = 150),
            TestData.expense(id = "b", taka = 250, accountId = "x", categoryId = "medical", epochDay = 160),
        )
        assertThat(BudgetStatusCalculator.actualFor(budget, transactions)).isEqualTo(Money.ofBdt(350))
    }

    @Test
    fun `monthly window covers the first and last day of the month`() {
        val month = YearMonth.of(2026, 2)
        val (start, end) = BudgetStatusCalculator.monthlyWindow(month)
        assertThat(AppTime.fromEpochDay(start)).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(AppTime.fromEpochDay(end)).isEqualTo(LocalDate.of(2026, 2, 28))
    }
}

class GoalProgressCalculatorTest {

    private val today = LocalDate.of(2026, 9, 1)

    @Test
    fun `computes percentage and remaining`() {
        val progress = GoalProgressCalculator.progress(
            name = "Emergency Fund",
            target = Money.ofBdt(100_000),
            current = Money.ofBdt(40_000),
            today = today,
        )

        assertThat(progress.percent).isEqualTo(40)
        assertThat(progress.remaining).isEqualTo(Money.ofBdt(60_000))
        assertThat(progress.isAchieved).isFalse()
    }

    @Test
    fun `a fully funded goal is achieved and needs nothing more`() {
        val progress = GoalProgressCalculator.progress(
            name = "Bike",
            target = Money.ofBdt(200_000),
            current = Money.ofBdt(200_000),
            today = today,
        )

        assertThat(progress.percent).isEqualTo(100)
        assertThat(progress.isAchieved).isTrue()
        assertThat(progress.remaining).isEqualTo(Money.ZERO)
    }

    @Test
    fun `over-funding clamps the percentage display but reports achieved`() {
        val progress = GoalProgressCalculator.progress(
            name = "Travel",
            target = Money.ofBdt(50_000),
            current = Money.ofBdt(75_000),
            today = today,
        )
        assertThat(progress.isAchieved).isTrue()
        assertThat(progress.remaining).isEqualTo(Money.ZERO)
    }

    @Test
    fun `a negative current amount is treated as zero, never as a deficit`() {
        val progress = GoalProgressCalculator.progress(
            name = "Car",
            target = Money.ofBdt(1_000_000),
            current = Money.ofBdt(-5_000),
            today = today,
        )
        assertThat(progress.current).isEqualTo(Money.ZERO)
        assertThat(progress.remaining).isEqualTo(Money.ofBdt(1_000_000))
    }

    @Test
    fun `derives days remaining and a monthly amount from the target date`() {
        val target = today.plusDays(90)
        val progress = GoalProgressCalculator.progress(
            name = "House",
            target = Money.ofBdt(90_000),
            current = Money.ZERO,
            targetEpochDay = target.toEpochDay(),
            today = today,
        )

        assertThat(progress.daysRemaining).isEqualTo(90L)
        // 90 days rounds up to 3 months, so 90,000 / 3 = 30,000 per month.
        assertThat(progress.requiredPerMonth).isEqualTo(Money.ofBdt(30_000))
    }

    @Test
    fun `a past target date requires the whole remainder now`() {
        val progress = GoalProgressCalculator.progress(
            name = "Late",
            target = Money.ofBdt(10_000),
            current = Money.ofBdt(1_000),
            targetEpochDay = today.minusDays(10).toEpochDay(),
            today = today,
        )
        assertThat(progress.daysRemaining).isEqualTo(-10L)
        assertThat(progress.requiredPerMonth).isEqualTo(Money.ofBdt(9_000))
    }

    @Test
    fun `a target of zero never divides by zero`() {
        val progress = GoalProgressCalculator.progress(
            name = "Unset",
            target = Money.ZERO,
            current = Money.ZERO,
            today = today,
        )
        assertThat(progress.percent).isEqualTo(0)
        assertThat(progress.isAchieved).isFalse()
    }

    @Test
    fun `family goal and savings goal use the same maths`() {
        val familyProgress = GoalProgressCalculator.familyGoal(TestData.goal(targetTaka = 100_000, currentTaka = 25_000), today)
        val savingsProgress = GoalProgressCalculator.savingsGoal(
            SavingsGoal(
                id = "s1",
                ownerUid = TestData.ALICE,
                familyId = TestData.FAMILY_ID,
                name = "Personal",
                targetAmount = Money.ofBdt(100_000),
                currentAmount = Money.ofBdt(25_000),
                status = GoalStatus.ACTIVE,
            ),
            today,
        )
        assertThat(familyProgress.percent).isEqualTo(savingsProgress.percent)
        assertThat(familyProgress.remaining).isEqualTo(savingsProgress.remaining)
    }
}

class BillStatusCalculatorTest {

    private val today = LocalDate.of(2026, 9, 20)

    @Test
    fun `an unpaid past-due bill reads as overdue`() {
        val status = BillStatusCalculator.effectiveStatus(
            com.familyfinance.app.domain.model.BillStatus.PENDING,
            dueEpochDay = today.minusDays(1).toEpochDay(),
            today = today,
        )
        assertThat(status).isEqualTo(com.familyfinance.app.domain.model.BillStatus.OVERDUE)
    }

    @Test
    fun `a paid bill is never reported overdue`() {
        val status = BillStatusCalculator.effectiveStatus(
            com.familyfinance.app.domain.model.BillStatus.PAID,
            dueEpochDay = today.minusDays(30).toEpochDay(),
            today = today,
        )
        assertThat(status).isEqualTo(com.familyfinance.app.domain.model.BillStatus.PAID)
    }

    @Test
    fun `a bill due today is pending, not overdue`() {
        val status = BillStatusCalculator.effectiveStatus(
            com.familyfinance.app.domain.model.BillStatus.PENDING,
            dueEpochDay = today.toEpochDay(),
            today = today,
        )
        assertThat(status).isEqualTo(com.familyfinance.app.domain.model.BillStatus.PENDING)
    }

    @Test
    fun `due soon covers the next seven days inclusive`() {
        assertThat(BillStatusCalculator.isDueSoon(today.toEpochDay(), today = today)).isTrue()
        assertThat(BillStatusCalculator.isDueSoon(today.plusDays(7).toEpochDay(), today = today)).isTrue()
        assertThat(BillStatusCalculator.isDueSoon(today.plusDays(8).toEpochDay(), today = today)).isFalse()
        assertThat(BillStatusCalculator.isDueSoon(today.minusDays(1).toEpochDay(), today = today)).isFalse()
    }
}
