package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.domain.model.FamilyExpenseSplit
import com.familyfinance.app.domain.model.FamilySettlement
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitBalanceCalculatorTest {

    private fun split(total: Long, payer: String, shares: Map<String, Long>) = FamilyExpenseSplit(
        id = "s-${payer}-$total",
        familyId = "fam",
        title = "Dinner",
        total = Money.ofMinor(total),
        paidByUid = payer,
        shares = shares.mapValues { Money.ofMinor(it.value) },
        dateEpochDay = 0,
        createdByUid = payer,
    )

    private fun settlement(from: String, to: String, amount: Long) = FamilySettlement(
        id = "set-$from-$to-$amount",
        familyId = "fam",
        fromUid = from,
        toUid = to,
        amount = Money.ofMinor(amount),
        dateEpochDay = 0,
    )

    @Test
    fun `equal three-way split leaves the payer owed the other two shares`() {
        val net = SplitBalanceCalculator.netMinor(
            splits = listOf(split(30000, "A", mapOf("A" to 10000, "B" to 10000, "C" to 10000))),
            settlements = emptyList(),
        )
        assertThat(net["A"]).isEqualTo(20000L)
        assertThat(net["B"]).isEqualTo(-10000L)
        assertThat(net["C"]).isEqualTo(-10000L)
    }

    @Test
    fun `a settlement reduces the debt`() {
        val net = SplitBalanceCalculator.netMinor(
            splits = listOf(split(30000, "A", mapOf("A" to 10000, "B" to 10000, "C" to 10000))),
            settlements = listOf(settlement("B", "A", 10000)),
        )
        assertThat(net["A"]).isEqualTo(10000L)
        assertThat(net["B"]).isEqualTo(0L)
        assertThat(net["C"]).isEqualTo(-10000L)
    }

    @Test
    fun `suggests a single transfer to settle a simple debt`() {
        val net = mapOf("A" to 10000L, "C" to -10000L)
        val plan = SplitBalanceCalculator.suggestions(net)
        assertThat(plan).hasSize(1)
        assertThat(plan.first().fromUid).isEqualTo("C")
        assertThat(plan.first().toUid).isEqualTo("A")
        assertThat(plan.first().amount.minor).isEqualTo(10000L)
    }

    @Test
    fun `settles a group with the minimum number of payments`() {
        // B and C each owe 50; A is owed 100.
        val net = mapOf("A" to 10000L, "B" to -5000L, "C" to -5000L)
        val plan = SplitBalanceCalculator.suggestions(net)
        assertThat(plan).hasSize(2)
        assertThat(plan.all { it.toUid == "A" }).isTrue()
        assertThat(plan.sumOf { it.amount.minor }).isEqualTo(10000L)
    }

    @Test
    fun `already settled group needs no payments`() {
        val net = mapOf("A" to 0L, "B" to 0L)
        assertThat(SplitBalanceCalculator.suggestions(net)).isEmpty()
    }
}
