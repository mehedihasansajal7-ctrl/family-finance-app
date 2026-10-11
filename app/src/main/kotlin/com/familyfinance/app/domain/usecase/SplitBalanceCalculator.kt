package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.domain.model.FamilyExpenseSplit
import com.familyfinance.app.domain.model.FamilySettlement
import com.familyfinance.app.domain.model.MemberBalance
import com.familyfinance.app.domain.model.SettlementSuggestion

/**
 * "Who owes whom" for the family.
 *
 * Net position of a member = (what they paid for others) − (what they owe for others), adjusted by
 * settlements. Pure arithmetic on integer minor units, so it is exact and unit-testable.
 *
 * It contains no notion of *why* a balance exists beyond the split title, and it is family-shared by
 * design (splitting is inherently a group activity) — unlike personal transactions, which stay private.
 */
object SplitBalanceCalculator {

    fun netMinor(
        splits: List<FamilyExpenseSplit>,
        settlements: List<FamilySettlement>,
    ): Map<String, Long> {
        val balances = mutableMapOf<String, Long>()
        fun add(uid: String, delta: Long) {
            balances[uid] = (balances[uid] ?: 0L) + delta
        }

        splits.forEach { split ->
            val total = split.total.minor
            val payerShare = split.shares[split.paidByUid]?.minor ?: 0L
            // The payer covered everyone's shares, so the rest is owed back to them.
            add(split.paidByUid, total - payerShare)
            split.shares.forEach { (uid, share) ->
                if (uid != split.paidByUid) add(uid, -share.minor)
            }
        }

        settlements.forEach { settlement ->
            add(settlement.fromUid, settlement.amount.minor)
            add(settlement.toUid, -settlement.amount.minor)
        }

        return balances
    }

    fun balances(
        splits: List<FamilyExpenseSplit>,
        settlements: List<FamilySettlement>,
    ): List<MemberBalance> =
        netMinor(splits, settlements)
            .map { (uid, minor) -> MemberBalance(uid, Money.ofMinor(minor)) }
            .sortedByDescending { it.net.minor }

    /**
     * Greedy "minimum number of payments" settlement plan: repeatedly match the biggest debtor with
     * the biggest creditor. Produces at most (n − 1) transfers for n members with a non-zero balance.
     */
    fun suggestions(net: Map<String, Long>): List<SettlementSuggestion> {
        val creditors = net.filterValues { it > 0 }.mapValues { it.value }.toMutableMap()
        val debtors = net.filterValues { it < 0 }.mapValues { -it.value }.toMutableMap()
        val result = mutableListOf<SettlementSuggestion>()

        while (true) {
            val creditor = creditors.maxByOrNull { it.value } ?: break
            val debtor = debtors.maxByOrNull { it.value } ?: break
            if (creditor.value <= 0 || debtor.value <= 0) break

            val amount = minOf(creditor.value, debtor.value)
            result += SettlementSuggestion(
                fromUid = debtor.key,
                toUid = creditor.key,
                amount = Money.ofMinor(amount),
            )
            creditors[creditor.key] = creditor.value - amount
            debtors[debtor.key] = debtor.value - amount
            if (creditors[creditor.key] == 0L) creditors.remove(creditor.key)
            if (debtors[debtor.key] == 0L) debtors.remove(debtor.key)
        }
        return result
    }

    fun suggestions(
        splits: List<FamilyExpenseSplit>,
        settlements: List<FamilySettlement>,
    ): List<SettlementSuggestion> = suggestions(netMinor(splits, settlements))
}
