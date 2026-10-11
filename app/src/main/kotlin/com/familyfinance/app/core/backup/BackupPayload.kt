package com.familyfinance.app.core.backup

import kotlinx.serialization.Serializable

/**
 * The plaintext contents of a backup, before encryption.
 *
 * It holds only the signed-in member's OWN records (accounts, transactions, savings goals, loans) —
 * the same rows that are already private to them on device. Family-shared data is not included, so a
 * backup file can never carry another member's finances.
 */
@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val uid: String = "",
    val accounts: List<AccountDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    val savingsGoals: List<SavingsGoalDto> = emptyList(),
    val loans: List<LoanDto> = emptyList(),
)

@Serializable
data class AccountDto(
    val id: String,
    val familyId: String,
    val name: String,
    val typeId: String,
    val institution: String? = null,
    val identifier: String? = null,
    val last4: String? = null,
    val openingBalanceMinor: Long,
    val currencyCode: String,
    val isActive: Boolean,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TransactionDto(
    val id: String,
    val clientId: String,
    val familyId: String,
    val typeId: String,
    val amountMinor: Long,
    val categoryId: String? = null,
    val subCategoryId: String? = null,
    val accountId: String? = null,
    val transferFromAccountId: String? = null,
    val transferToAccountId: String? = null,
    val dateEpochDay: Long,
    val note: String = "",
    val recurringRuleId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SavingsGoalDto(
    val id: String,
    val familyId: String,
    val name: String,
    val targetMinor: Long,
    val currentMinor: Long,
    val targetEpochDay: Long? = null,
    val note: String? = null,
    val statusId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class LoanDto(
    val id: String,
    val familyId: String,
    val directionId: String,
    val counterparty: String,
    val principalMinor: Long,
    val interestRatePercent: Int,
    val installmentMinor: Long? = null,
    val totalInstallments: Int? = null,
    val paidInstallments: Int,
    val repaidMinor: Long,
    val startEpochDay: Long,
    val dueEpochDay: Long? = null,
    val note: String? = null,
    val isClosed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
