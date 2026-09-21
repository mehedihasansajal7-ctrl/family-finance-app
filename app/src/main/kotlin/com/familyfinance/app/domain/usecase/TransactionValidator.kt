package com.familyfinance.app.domain.usecase

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import java.time.LocalDate

enum class Severity { ERROR, WARNING }

data class ValidationIssue(
    val field: String,
    val message: String,
    val severity: Severity = Severity.ERROR,
)

data class ValidationResult(val issues: List<ValidationIssue> = emptyList()) {
    val isValid: Boolean get() = issues.none { it.severity == Severity.ERROR }
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == Severity.ERROR }
    val warnings: List<ValidationIssue> get() = issues.filter { it.severity == Severity.WARNING }
    fun firstError(): String? = errors.firstOrNull()?.message
}

/**
 * Transaction validation.
 *
 * Ownership is validated against the caller-supplied set of the *authenticated* user's account ids,
 * never against anything in the payload. A client cannot claim an account it does not own.
 */
object TransactionValidator {

    const val MAX_NOTE_LENGTH = 500
    const val MAX_AMOUNT_MINOR = 100_000_000_000L // ৳1,000,000,000.00 — sanity ceiling

    fun validate(
        transaction: Transaction,
        ownedAccountIds: Set<String>,
        accountBalances: Map<String, Money> = emptyMap(),
        today: LocalDate = AppTime.today(),
        enforceSufficientBalance: Boolean = false,
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (transaction.amount.isZero) {
            issues += ValidationIssue("amount", "Amount must be greater than zero.")
        } else if (transaction.amount.isNegative) {
            issues += ValidationIssue("amount", "Amount cannot be negative.")
        } else if (transaction.amount.minor > MAX_AMOUNT_MINOR) {
            issues += ValidationIssue("amount", "Amount looks unrealistically large.")
        }

        val earliest = LocalDate.of(1971, 1, 1).toEpochDay()
        val latest = today.plusYears(10).toEpochDay()
        if (transaction.dateEpochDay < earliest || transaction.dateEpochDay > latest) {
            issues += ValidationIssue("date", "Please choose a valid date.")
        }

        if (transaction.note.length > MAX_NOTE_LENGTH) {
            issues += ValidationIssue("note", "Note is too long (max $MAX_NOTE_LENGTH characters).")
        }

        if (transaction.ownerUid.isBlank() || transaction.familyId.isBlank()) {
            issues += ValidationIssue("owner", "Missing ownership information.")
        }

        when (transaction.type) {
            TransactionType.INCOME, TransactionType.EXPENSE -> {
                val accountId = transaction.accountId
                if (accountId.isNullOrBlank()) {
                    issues += ValidationIssue("account", "Please choose an account.")
                } else if (accountId !in ownedAccountIds) {
                    issues += ValidationIssue("account", "That account does not belong to you.")
                }
                if (transaction.categoryId.isNullOrBlank()) {
                    issues += ValidationIssue("category", "Please choose a category.")
                }
                if (transaction.transferFromAccountId != null || transaction.transferToAccountId != null) {
                    issues += ValidationIssue("type", "An income or expense cannot reference a transfer.")
                }
            }

            TransactionType.TRANSFER -> {
                val from = transaction.transferFromAccountId
                val to = transaction.transferToAccountId
                if (from.isNullOrBlank() || to.isNullOrBlank()) {
                    issues += ValidationIssue("transfer", "Choose both the source and destination account.")
                } else if (from == to) {
                    issues += ValidationIssue("transfer", "Source and destination must be different accounts.")
                } else {
                    if (from !in ownedAccountIds) {
                        issues += ValidationIssue("transfer", "The source account does not belong to you.")
                    }
                    if (to !in ownedAccountIds) {
                        issues += ValidationIssue("transfer", "The destination account does not belong to you.")
                    }
                }
                if (transaction.categoryId != null) {
                    issues += ValidationIssue("category", "A transfer has no category.")
                }
                if (transaction.accountId != null) {
                    issues += ValidationIssue("account", "A transfer has no single account.")
                }
            }
        }

        // Balance checks are advisory by default: a user may legitimately record an overdraft or a
        // correction. Callers that need a hard block (e.g. a family-transfer feature) can opt in.
        if (transaction.type == TransactionType.EXPENSE && transaction.accountId != null) {
            val balance = accountBalances[transaction.accountId]
            if (balance != null && balance < transaction.amount) {
                issues += ValidationIssue(
                    field = "amount",
                    message = "This exceeds the account balance. It will be recorded as a negative balance.",
                    severity = if (enforceSufficientBalance) Severity.ERROR else Severity.WARNING,
                )
            }
        }

        if (transaction.type == TransactionType.TRANSFER &&
            transaction.transferFromAccountId != null &&
            enforceSufficientBalance
        ) {
            val balance = accountBalances[transaction.transferFromAccountId]
            if (balance != null && balance < transaction.amount) {
                issues += ValidationIssue("amount", "Not enough balance in the source account.")
            }
        }

        return ValidationResult(issues)
    }

    fun validateAccountOwnership(account: Account?, uid: String): Boolean = account != null && account.ownerUid == uid

    /**
     * Rejects a payload carrying keys the app refuses to store (PIN/CVV/password/OTP).
     * Defence in depth against a crafted or restored payload.
     */
    fun containsForbiddenFields(keys: Iterable<String>): Boolean =
        keys.any { com.familyfinance.app.core.security.SensitiveInputRules.isForbiddenField(it) }
}
