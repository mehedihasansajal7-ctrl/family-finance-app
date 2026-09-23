package com.familyfinance.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities.
 *
 * Money is stored as `Long` minor units (poisha) so SQLite never sees a float. Enums are stored as
 * their stable string ids. Every personal table carries `ownerUid` and every query that returns
 * personal rows filters on it — the index on `ownerUid` exists to make that the cheap path.
 */

@Entity(tableName = "accounts", indices = [Index("ownerUid"), Index(value = ["ownerUid", "isActive"])])
data class AccountEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val familyId: String,
    val name: String,
    val typeId: String,
    val institution: String?,
    val identifier: String?,
    val last4: String?,
    val openingBalanceMinor: Long,
    val currencyCode: String,
    val isActive: Boolean,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStateId: String,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("ownerUid"),
        Index(value = ["ownerUid", "dateEpochDay"]),
        Index(value = ["ownerUid", "typeId"]),
        Index(value = ["ownerUid", "accountId"]),
        Index(value = ["familyId", "dateEpochDay"]),
        Index(value = ["clientId"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val ownerUid: String,
    val familyId: String,
    val typeId: String,
    val amountMinor: Long,
    val categoryId: String?,
    val subCategoryId: String?,
    val accountId: String?,
    val transferFromAccountId: String?,
    val transferToAccountId: String?,
    val dateEpochDay: Long,
    val note: String,
    val attachmentIds: List<String>,
    val recurringRuleId: String?,
    val recurrenceOccurrenceEpochDay: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStateId: String,
)

@Entity(tableName = "recurring_rules", indices = [Index("ownerUid")])
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val familyId: String,
    val typeId: String,
    val amountMinor: Long,
    val categoryId: String?,
    val subCategoryId: String?,
    val accountId: String?,
    val transferFromAccountId: String?,
    val transferToAccountId: String?,
    val note: String,
    val frequencyId: String,
    val customIntervalDays: Int?,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val lastGeneratedEpochDay: Long?,
    val isActive: Boolean,
    val reminderEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val photoUrl: String?,
    val currencyCode: String,
    val timezone: String,
    val ownerUid: String,
    val createdAt: Long,
    val netWorthEnabled: Boolean,
)

@Entity(tableName = "member_profiles", indices = [Index("familyId")])
data class MemberProfileEntity(
    @PrimaryKey val uid: String,
    val familyId: String,
    val fullName: String,
    val photoUrl: String?,
    val dateOfBirthEpochDay: Long?,
    val mobile: String?,
    val email: String?,
    val relationship: String?,
    val occupation: String?,
    val notes: String?,
    val shareProfileWithFamily: Boolean,
    val roleId: String,
    val statusId: String,
    val updatedAt: Long,
)

@Entity(tableName = "invitations", indices = [Index("familyId")])
data class InvitationEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val code: String,
    val createdByUid: String,
    val createdAt: Long,
    val expiresAt: Long,
    val revokedAt: Long?,
    val maxUses: Int,
    val usedCount: Int,
    val invitedEmail: String?,
)

@Entity(tableName = "family_budgets", indices = [Index("familyId")])
data class FamilyBudgetEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val categoryId: String?,
    val periodEpochDayStart: Long,
    val periodEpochDayEnd: Long,
    val limitMinor: Long,
    val createdByUid: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "family_goals", indices = [Index("familyId")])
data class FamilyGoalEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val categoryId: String,
    val targetAmountMinor: Long,
    val currentAmountMinor: Long,
    val targetEpochDay: Long?,
    val description: String?,
    val statusId: String,
    val createdByUid: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "family_bills", indices = [Index("familyId")])
data class FamilyBillEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val name: String,
    val categoryId: String,
    val expectedAmountMinor: Long,
    val dueEpochDay: Long,
    val frequencyId: String,
    val statusId: String,
    val reminderEnabled: Boolean,
    val reminderDaysBefore: Int,
    val notes: String?,
    val createdByUid: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "savings_goals", indices = [Index("ownerUid")])
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val familyId: String,
    val name: String,
    val targetAmountMinor: Long,
    val currentAmountMinor: Long,
    val targetEpochDay: Long?,
    val note: String?,
    val statusId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "personal_loans", indices = [Index("ownerUid")])
data class PersonalLoanEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val familyId: String,
    val directionId: String,
    val counterparty: String,
    val principalMinor: Long,
    val interestRatePercent: Int,
    val installmentAmountMinor: Long?,
    val totalInstallments: Int?,
    val paidInstallments: Int,
    val repaidAmountMinor: Long,
    val startEpochDay: Long,
    val dueEpochDay: Long?,
    val note: String?,
    val isClosed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "attachments", indices = [Index("ownerUid"), Index("transactionId")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val familyId: String,
    val transactionId: String?,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val storagePath: String,
    val isFamilyVisible: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "notifications", indices = [Index("ownerUid")])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val familyId: String,
    val categoryId: String,
    val title: String,
    val body: String,
    val relatedId: String?,
    val createdAt: Long,
    val readAt: Long?,
)

@Entity(tableName = "audit_logs", indices = [Index("familyId")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val familyId: String?,
    val actorUid: String,
    val action: String,
    val contextJson: String,
    val createdAt: Long,
)

/**
 * Offline write queue.
 *
 * Writes are applied locally first (so the UI is immediate and the ledger stays consistent), then
 * drained to the backend. Each op carries the record's `clientId`, which the backend treats as an
 * idempotency key — replaying the queue cannot double-post a transaction.
 */
@Entity(tableName = "pending_ops", indices = [Index("ownerUid")])
data class PendingOpEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val kind: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "family_chat_messages", indices = [Index("familyId"), Index("createdAt")])
data class FamilyChatMessageEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val senderUid: String,
    val senderName: String,
    val text: String,
    val createdAt: Long,
)
