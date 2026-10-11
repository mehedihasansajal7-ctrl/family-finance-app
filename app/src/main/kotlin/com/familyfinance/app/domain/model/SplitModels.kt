package com.familyfinance.app.domain.model

import com.familyfinance.app.core.money.Money

/**
 * A shared family expense that is split between members.
 *
 * [shares] is the amount each participant owes for this expense (uid -> amount). The payer's own
 * share is included, so `shares.values.sum() == total`. Storing the concrete per-member share (rather
 * than only a split rule) means a later change to a member's participation never rewrites history.
 */
data class FamilyExpenseSplit(
    val id: String,
    val familyId: String,
    val title: String,
    val total: Money,
    val paidByUid: String,
    val shares: Map<String, Money>,
    val dateEpochDay: Long,
    val createdByUid: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** A settlement payment: [fromUid] paid [toUid] to reduce what they owed. */
data class FamilySettlement(
    val id: String,
    val familyId: String,
    val fromUid: String,
    val toUid: String,
    val amount: Money,
    val dateEpochDay: Long,
    val createdAt: Long = 0L,
)

/** A member's net position. Positive = the family owes them; negative = they owe the family. */
data class MemberBalance(
    val uid: String,
    val net: Money,
)

/** A suggested transfer that would settle the group with the fewest payments. */
data class SettlementSuggestion(
    val fromUid: String,
    val toUid: String,
    val amount: Money,
)
