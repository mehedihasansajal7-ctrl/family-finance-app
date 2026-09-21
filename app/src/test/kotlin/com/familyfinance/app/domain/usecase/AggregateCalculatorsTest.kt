package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.domain.model.MemberAggregateContribution
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.testutil.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.YearMonth

class PersonalSummaryCalculatorTest {

    private val month = YearMonth.of(2026, 9)
    private val epochDay = month.atDay(15).toEpochDay()

    @Test
    fun `sums only the owner's transactions`() {
        val mine = TestData.expense(id = "mine", ownerUid = TestData.ALICE, taka = 500, accountId = "a", epochDay = epochDay)
        val theirs = TestData.expense(id = "theirs", ownerUid = TestData.BOB, taka = 9_000, accountId = "b", epochDay = epochDay)

        val summary = PersonalSummaryCalculator.compute(
            ownerUid = TestData.ALICE,
            accounts = emptyList(),
            transactions = listOf(mine, theirs),
            month = month,
        )

        assertThat(summary.monthExpense).isEqualTo(Money.ofBdt(500))
        assertThat(summary.monthIncome).isEqualTo(Money.ZERO)
    }

    @Test
    fun `only counts transactions inside the requested month`() {
        val inMonth = TestData.expense(id = "in", taka = 100, accountId = "a", epochDay = month.atDay(1).toEpochDay())
        val before = TestData.expense(id = "before", taka = 700, accountId = "a", epochDay = month.minusMonths(1).atDay(20).toEpochDay())
        val after = TestData.expense(id = "after", taka = 900, accountId = "a", epochDay = month.plusMonths(1).atDay(2).toEpochDay())

        val summary = PersonalSummaryCalculator.compute(
            ownerUid = TestData.ALICE,
            accounts = emptyList(),
            transactions = listOf(inMonth, before, after),
            month = month,
        )

        assertThat(summary.monthExpense).isEqualTo(Money.ofBdt(100))
    }

    @Test
    fun `savings is income minus expense and may be negative`() {
        val income = TestData.income(id = "i", taka = 1_000, accountId = "a", epochDay = epochDay)
        val expense = TestData.expense(id = "e", taka = 2_500, accountId = "a", epochDay = epochDay)

        val summary = PersonalSummaryCalculator.compute(TestData.ALICE, emptyList(), listOf(income, expense), month)

        assertThat(summary.monthSavings).isEqualTo(Money.ofBdt(-1_500))
    }

    @Test
    fun `category totals exclude transfers and sort by size`() {
        val transactions = listOf(
            TestData.expense(id = "e1", taka = 100, accountId = "a", categoryId = "food_grocery", epochDay = epochDay),
            TestData.expense(id = "e2", taka = 500, accountId = "a", categoryId = "housing_rent", epochDay = epochDay),
            TestData.expense(id = "e3", taka = 300, accountId = "a", categoryId = "food_grocery", epochDay = epochDay),
            TestData.transfer(id = "t1", taka = 9_999, from = "a", to = "b", epochDay = epochDay),
        )

        val totals = PersonalSummaryCalculator.categoryTotals(transactions, TransactionType.EXPENSE)

        assertThat(totals.map { it.categoryId }).containsExactly("housing_rent", "food_grocery").inOrder()
        assertThat(totals.first { it.categoryId == "food_grocery" }.total).isEqualTo(Money.ofBdt(400))
        assertThat(Money.sum(totals.map { it.total })).isEqualTo(Money.ofBdt(900))
    }

    @Test
    fun `categoryTotals rejects transfers because they have no category`() {
        val failure = runCatching {
            PersonalSummaryCalculator.categoryTotals(emptyList(), TransactionType.TRANSFER)
        }
        assertThat(failure.isFailure).isTrue()
    }

    @Test
    fun `trend returns one point per month ending at the requested month`() {
        val transactions = listOf(
            TestData.income(id = "i", taka = 1_000, accountId = "a", epochDay = month.atDay(3).toEpochDay()),
            TestData.expense(id = "e", taka = 400, accountId = "a", epochDay = month.minusMonths(2).atDay(3).toEpochDay()),
        )

        val trend = PersonalSummaryCalculator.trend(transactions, month, months = 3)

        assertThat(trend).hasSize(3)
        assertThat(trend.last().month).isEqualTo(month)
        assertThat(trend.last().income).isEqualTo(Money.ofBdt(1_000))
        assertThat(trend.first().expense).isEqualTo(Money.ofBdt(400))
    }

    @Test
    fun `an empty ledger yields an empty-but-valid summary`() {
        val summary = PersonalSummaryCalculator.compute(TestData.ALICE, emptyList(), emptyList(), month)
        assertThat(summary.totalBalance).isEqualTo(Money.ZERO)
        assertThat(summary.monthIncome).isEqualTo(Money.ZERO)
        assertThat(summary.expenseByCategory).isEmpty()
    }
}

class FamilyAggregateCalculatorTest {

    private val month = YearMonth.of(2026, 9)

    @Test
    fun `sums member contributions into family totals`() {
        val aggregate = FamilyAggregateCalculator.aggregate(
            FamilyAggregateInput(
                month = month,
                contributions = listOf(
                    MemberAggregateContribution("key-a", Money.ofBdt(70_000), Money.ofBdt(30_000), Money.ofBdt(40_000)),
                    MemberAggregateContribution("key-b", Money.ofBdt(30_000), Money.ofBdt(25_000), Money.ofBdt(5_000)),
                ),
            )
        )

        assertThat(aggregate.totalIncome).isEqualTo(Money.ofBdt(100_000))
        assertThat(aggregate.totalExpense).isEqualTo(Money.ofBdt(55_000))
        assertThat(aggregate.totalSavings).isEqualTo(Money.ofBdt(45_000))
        assertThat(aggregate.memberCount).isEqualTo(2)
    }

    /**
     * The privacy guarantee, asserted directly.
     *
     * If someone later adds a `memberBreakdown` (or any other member-attributed field) to
     * [com.familyfinance.app.domain.model.FamilyAggregate], this test fails: the rendered aggregate
     * would then contain a member key. That is the point.
     */
    @Test
    fun `the aggregate never carries member attribution`() {
        val secretKeys = listOf("member-key-alice-9182", "member-key-bob-4417")
        val aggregate = FamilyAggregateCalculator.aggregate(
            FamilyAggregateInput(
                month = month,
                contributions = secretKeys.map {
                    MemberAggregateContribution(it, Money.ofBdt(50_000), Money.ofBdt(20_000), Money.ofBdt(30_000))
                },
            )
        )

        val rendered = aggregate.toString()
        secretKeys.forEach { key ->
            assertThat(rendered).doesNotContain(key)
        }
        // Nothing that even looks like an identifier may appear.
        assertThat(rendered).doesNotContain("uid")
        assertThat(rendered).doesNotContain("memberKey")
        assertThat(rendered).doesNotContain("ownerUid")
    }

    @Test
    fun `category totals are sorted by size and carry no member field`() {
        val aggregate = FamilyAggregateCalculator.aggregate(
            FamilyAggregateInput(
                month = month,
                contributions = listOf(MemberAggregateContribution("k", Money.ofBdt(1), Money.ofBdt(1), Money.ZERO)),
                expenseByCategory = listOf(
                    com.familyfinance.app.domain.model.CategoryTotal("food_grocery", Money.ofBdt(500)),
                    com.familyfinance.app.domain.model.CategoryTotal("housing_rent", Money.ofBdt(2_000)),
                ),
            )
        )

        assertThat(aggregate.expenseByCategory.map { it.categoryId })
            .containsExactly("housing_rent", "food_grocery").inOrder()
    }

    @Test
    fun `change percentages compare against the previous month`() {
        val aggregate = FamilyAggregateCalculator.aggregate(
            FamilyAggregateInput(
                month = month,
                contributions = listOf(MemberAggregateContribution("k", Money.ofBdt(110_000), Money.ofBdt(50_000), Money.ZERO)),
                previousMonthIncome = Money.ofBdt(100_000),
                previousMonthExpense = Money.ofBdt(100_000),
            )
        )

        assertThat(aggregate.incomeChangePercent).isEqualTo(10)
        assertThat(aggregate.expenseChangePercent).isEqualTo(-50)
    }

    @Test
    fun `an empty family produces a zeroed aggregate, not a crash`() {
        val aggregate = FamilyAggregateCalculator.aggregate(FamilyAggregateInput(month))
        assertThat(aggregate.totalIncome).isEqualTo(Money.ZERO)
        assertThat(aggregate.memberCount).isEqualTo(0)
        assertThat(aggregate.netWorth).isNull()
    }

    @Test
    fun `net worth is absent unless explicitly enabled`() {
        val withOut = FamilyAggregateCalculator.aggregate(
            FamilyAggregateInput(month, listOf(MemberAggregateContribution("k", Money.ofBdt(1), Money.ZERO, Money.ofBdt(1))))
        )
        val withNetWorth = FamilyAggregateCalculator.aggregate(
            FamilyAggregateInput(
                month,
                listOf(MemberAggregateContribution("k", Money.ofBdt(1), Money.ZERO, Money.ofBdt(1))),
                netWorth = Money.ofBdt(500_000),
            )
        )
        assertThat(withOut.netWorth).isNull()
        assertThat(withNetWorth.netWorth).isEqualTo(Money.ofBdt(500_000))
    }

    @Test
    fun `category shares sum to one hundred percent`() {
        val aggregate = FamilyAggregateCalculator.aggregate(
            FamilyAggregateInput(
                month,
                listOf(MemberAggregateContribution("k", Money.ZERO, Money.ofBdt(400), Money.ZERO)),
                expenseByCategory = listOf(
                    com.familyfinance.app.domain.model.CategoryTotal("a", Money.ofBdt(100)),
                    com.familyfinance.app.domain.model.CategoryTotal("b", Money.ofBdt(300)),
                ),
            )
        )
        val shares = FamilyAggregateCalculator.categoryShares(aggregate)
        assertThat(shares.map { it.second }.sum()).isEqualTo(100)
    }
}
