package com.familyfinance.app.domain.model

import com.familyfinance.app.core.money.Money

/** A shared family budget. Visibility is family-wide, but it is aggregate-only by construction. */
data class FamilyBudget(
    val id: String,
    val familyId: String,
    val name: String,
    /** Null = an overall budget for the period rather than a single category. */
    val categoryId: String? = null,
    val periodEpochDayStart: Long,
    val periodEpochDayEnd: Long,
    val limit: Money,
    val createdByUid: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun coversDate(epochDay: Long): Boolean = epochDay in periodEpochDayStart..periodEpochDayEnd
}

/** A shared family savings goal. Contribution *details* stay private; only the total is shared. */
data class FamilyGoal(
    val id: String,
    val familyId: String,
    val name: String,
    val categoryId: String,
    val targetAmount: Money,
    val currentAmount: Money,
    val targetEpochDay: Long? = null,
    val description: String? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdByUid: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** A shared recurring bill. Deliberately has no "paid by" field. */
data class FamilyBill(
    val id: String,
    val familyId: String,
    val name: String,
    val categoryId: String,
    val expectedAmount: Money,
    val dueEpochDay: Long,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val status: BillStatus = BillStatus.PENDING,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Int = 3,
    val notes: String? = null,
    val createdByUid: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Private personal savings goal. Only the owner can ever read this document. */
data class SavingsGoal(
    val id: String,
    val ownerUid: String,
    val familyId: String,
    val name: String,
    val targetAmount: Money,
    val currentAmount: Money = Money.ZERO,
    val targetEpochDay: Long? = null,
    val note: String? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Private personal loan (borrowed or lent). Never surfaced on the family dashboard. */
data class PersonalLoan(
    val id: String,
    val ownerUid: String,
    val familyId: String,
    val direction: LoanDirection,
    val counterparty: String,
    val principal: Money,
    val interestRatePercent: Int = 0,
    val installmentAmount: Money? = null,
    val totalInstallments: Int? = null,
    val paidInstallments: Int = 0,
    val repaidAmount: Money = Money.ZERO,
    val startEpochDay: Long,
    val dueEpochDay: Long? = null,
    val note: String? = null,
    val isClosed: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val outstanding: Money get() = (principal - repaidAmount).let { if (it.isNegative) Money.ZERO else it }
}

/** In-app notification. Amounts are never placed in the notification body. */
data class AppNotification(
    val id: String,
    val ownerUid: String,
    val familyId: String,
    val categoryId: String,
    val title: String,
    val body: String,
    val relatedId: String? = null,
    val createdAt: Long,
    val readAt: Long? = null,
)

/** Append-only security/account audit trail. Never contains credentials or full identifiers. */
data class AuditLog(
    val id: String,
    val familyId: String?,
    val actorUid: String,
    val action: String,
    /** Non-sensitive, structured context. Redacted before it is written. */
    val context: Map<String, String> = emptyMap(),
    val createdAt: Long,
)
