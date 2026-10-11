package com.familyfinance.app.core.backup

import android.content.Context
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AccountType
import com.familyfinance.app.domain.model.GoalStatus
import com.familyfinance.app.domain.model.LoanDirection
import com.familyfinance.app.domain.model.PersonalLoan
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.domain.model.SyncState
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.domain.repository.AccountRepository
import com.familyfinance.app.domain.repository.PersonalFinanceRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted export/import of the signed-in member's own records.
 *
 * Export writes an AES-GCM encrypted file the member can store anywhere; import reads it back and
 * re-owners every row to the current uid/family, so restoring into a fresh install works. Family-shared
 * data is out of scope by design.
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val personal: PersonalFinanceRepository,
    private val session: SessionManager,
    private val time: TimeProvider,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(password: CharArray): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val user = session.currentUserOnce() ?: error("Please sign in again.")
            val export = BackupPayload(
                version = 1,
                exportedAt = time.nowMillis(),
                uid = user.uid,
                accounts = accounts.observeAccounts(user.uid).first().map { it.toDto() },
                transactions = transactions.observeTransactions(user.uid).first().map { it.toDto() },
                savingsGoals = personal.observeSavingsGoals(user.uid).first().map { it.toDto() },
                loans = personal.observeLoans(user.uid).first().map { it.toDto() },
            )
            val clear = json.encodeToString(BackupPayload.serializer(), export).toByteArray(Charsets.UTF_8)
            val encrypted = BackupCodec.encrypt(clear, password)
            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(dir, "family-finance-backup-${LocalDate.now()}.ffbk")
            file.writeBytes(encrypted)
            file
        }
    }

    /** Restores a backup. Throws [BackupCodec.WrongPassphrase] when the password is wrong. */
    suspend fun import(bytes: ByteArray, password: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val user = session.currentUserOnce() ?: error("Please sign in again.")
            val clear = BackupCodec.decrypt(bytes, password)
            val payload = json.decodeFromString(BackupPayload.serializer(), String(clear, Charsets.UTF_8))
            val familyId = user.familyId.orEmpty()

            var count = 0
            payload.accounts.forEach { accounts.upsertAccount(it.toDomain(user.uid, familyId)); count++ }
            payload.transactions.forEach { transactions.upsertTransaction(it.toDomain(user.uid, familyId)); count++ }
            payload.savingsGoals.forEach { personal.upsertSavingsGoal(it.toDomain(user.uid, familyId)); count++ }
            payload.loans.forEach { personal.upsertLoan(it.toDomain(user.uid, familyId)); count++ }
            count
        }
    }

    // ------------------------------------------------------------------ domain -> dto

    private fun Account.toDto() = AccountDto(
        id = id, familyId = familyId, name = name, typeId = type.id,
        institution = institution, identifier = identifier, last4 = last4,
        openingBalanceMinor = openingBalance.minor, currencyCode = currencyCode,
        isActive = isActive, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun Transaction.toDto() = TransactionDto(
        id = id, clientId = clientId, familyId = familyId, typeId = type.id,
        amountMinor = amount.minor, categoryId = categoryId, subCategoryId = subCategoryId,
        accountId = accountId, transferFromAccountId = transferFromAccountId,
        transferToAccountId = transferToAccountId, dateEpochDay = dateEpochDay, note = note,
        recurringRuleId = recurringRuleId, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun SavingsGoal.toDto() = SavingsGoalDto(
        id = id, familyId = familyId, name = name, targetMinor = targetAmount.minor,
        currentMinor = currentAmount.minor, targetEpochDay = targetEpochDay, note = note,
        statusId = status.id, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun PersonalLoan.toDto() = LoanDto(
        id = id, familyId = familyId, directionId = direction.id, counterparty = counterparty,
        principalMinor = principal.minor, interestRatePercent = interestRatePercent,
        installmentMinor = installmentAmount?.minor, totalInstallments = totalInstallments,
        paidInstallments = paidInstallments, repaidMinor = repaidAmount.minor,
        startEpochDay = startEpochDay, dueEpochDay = dueEpochDay, note = note,
        isClosed = isClosed, createdAt = createdAt, updatedAt = updatedAt,
    )

    // ------------------------------------------------------------------ dto -> domain

    private fun AccountDto.toDomain(uid: String, familyId: String) = Account(
        id = id, ownerUid = uid, familyId = familyId, name = name,
        type = AccountType.fromId(typeId), institution = institution, identifier = identifier,
        last4 = last4, openingBalance = Money.ofMinor(openingBalanceMinor), currencyCode = currencyCode,
        isActive = isActive, notes = notes, createdAt = createdAt, updatedAt = updatedAt,
        syncState = SyncState.PENDING,
    )

    private fun TransactionDto.toDomain(uid: String, familyId: String) = Transaction(
        id = id, clientId = clientId, ownerUid = uid, familyId = familyId,
        type = TransactionType.fromId(typeId) ?: TransactionType.EXPENSE,
        amount = Money.ofMinor(amountMinor), categoryId = categoryId, subCategoryId = subCategoryId,
        accountId = accountId, transferFromAccountId = transferFromAccountId,
        transferToAccountId = transferToAccountId, dateEpochDay = dateEpochDay, note = note,
        recurringRuleId = recurringRuleId, createdAt = createdAt, updatedAt = updatedAt,
        syncState = SyncState.PENDING,
    )

    private fun SavingsGoalDto.toDomain(uid: String, familyId: String) = SavingsGoal(
        id = id, ownerUid = uid, familyId = familyId, name = name,
        targetAmount = Money.ofMinor(targetMinor), currentAmount = Money.ofMinor(currentMinor),
        targetEpochDay = targetEpochDay, note = note, status = GoalStatus.fromId(statusId),
        createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun LoanDto.toDomain(uid: String, familyId: String) = PersonalLoan(
        id = id, ownerUid = uid, familyId = familyId, direction = LoanDirection.fromId(directionId),
        counterparty = counterparty, principal = Money.ofMinor(principalMinor),
        interestRatePercent = interestRatePercent, installmentAmount = installmentMinor?.let { Money.ofMinor(it) },
        totalInstallments = totalInstallments, paidInstallments = paidInstallments,
        repaidAmount = Money.ofMinor(repaidMinor), startEpochDay = startEpochDay,
        dueEpochDay = dueEpochDay, note = note, isClosed = isClosed,
        createdAt = createdAt, updatedAt = updatedAt,
    )
}
