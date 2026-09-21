package com.familyfinance.app.data.repository

import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.dao.AccountDao
import com.familyfinance.app.data.local.dao.RecurringRuleDao
import com.familyfinance.app.data.local.dao.TransactionDao
import com.familyfinance.app.data.local.toDomain
import com.familyfinance.app.data.local.toEntity
import com.familyfinance.app.data.remote.RemoteSyncEngine
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.data.sync.SyncCoordinator
import com.familyfinance.app.data.sync.SyncOp
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.RecurringRule
import com.familyfinance.app.domain.model.SyncState
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.repository.AccountRepository
import com.familyfinance.app.domain.repository.RecurringRepository
import com.familyfinance.app.domain.repository.TransactionFilter
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.domain.usecase.AccountBalanceCalculator
import com.familyfinance.app.domain.usecase.RecurrenceCalculator
import com.familyfinance.app.domain.usecase.TransactionValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Offline-first implementations.
 *
 * Every mutation commits locally first, then attempts a push. A failed push is queued rather than
 * surfaced as an error, because losing the user's entry is far worse than a delayed sync. The one
 * thing that *is* hard-blocked is writing to someone else's data: each method re-checks the
 * authenticated uid before touching a row.
 */
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val engine: RemoteSyncEngine,
    private val sync: SyncCoordinator,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : AccountRepository {

    override fun observeAccounts(ownerUid: String): Flow<List<Account>> =
        accountDao.observeForOwner(ownerUid).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAccount(ownerUid: String, accountId: String): Outcome<Account> {
        val row = accountDao.find(ownerUid, accountId)
            ?: return Outcome.Failure(AppError.notFound("Account not found."))
        return Outcome.Success(row.toDomain())
    }

    override suspend fun upsertAccount(account: Account): Outcome<Account> {
        if (!isSelf(account.ownerUid)) return Outcome.Failure(AppError.permission())
        val sanitised = account.copy(
            updatedAt = time.nowMillis(),
            createdAt = if (account.createdAt == 0L) time.nowMillis() else account.createdAt,
            syncState = SyncState.PENDING,
        )
        accountDao.upsert(sanitised.toEntity())
        pushAccount(sanitised)
        return Outcome.Success(sanitised)
    }

    override suspend fun deleteAccount(ownerUid: String, accountId: String): Outcome<Unit> {
        if (!isSelf(ownerUid)) return Outcome.Failure(AppError.permission())
        val account = accountDao.find(ownerUid, accountId)
            ?: return Outcome.Failure(AppError.notFound("Account not found."))

        // Refuse to delete an account that still has movements: doing so would silently change the
        // user's totals. They must move or delete the transactions first.
        val movements = transactionDao.allForOwner(ownerUid).count {
            it.accountId == accountId || it.transferFromAccountId == accountId || it.transferToAccountId == accountId
        }
        if (movements > 0) {
            return Outcome.Failure(
                AppError.validation("This account has $movements transaction(s). Delete or move them first.")
            )
        }
        accountDao.delete(ownerUid, accountId)
        if (engine.isConfigured) {
            val pushed = engine.deleteAccount(accountId)
            if (pushed.isFailure) sync.enqueue(ownerUid, SyncOp.DELETE_ACCOUNT, accountId)
        }
        return Outcome.Success(Unit)
    }

    /** Live balance for one account, derived from the owner's transactions. */
    suspend fun balanceOf(ownerUid: String, accountId: String): Outcome<com.familyfinance.app.core.money.Money> {
        val account = accountDao.find(ownerUid, accountId)?.toDomain()
            ?: return Outcome.Failure(AppError.notFound("Account not found."))
        val transactions = transactionDao.allForOwner(ownerUid).map { it.toDomain() }
        return Outcome.Success(AccountBalanceCalculator.balance(account, transactions))
    }

    private suspend fun pushAccount(account: Account) {
        if (!engine.isConfigured) return
        val result = engine.pushAccount(account)
        if (result.isSuccess) {
            accountDao.upsert(account.copy(syncState = SyncState.SYNCED).toEntity())
        } else {
            sync.enqueue(account.ownerUid, SyncOp.UPSERT_ACCOUNT, account.id)
        }
    }

    private suspend fun isSelf(uid: String): Boolean = session.currentUserOnce()?.uid == uid
}

class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val engine: RemoteSyncEngine,
    private val sync: SyncCoordinator,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : TransactionRepository {

    override fun observeTransactions(ownerUid: String, filter: TransactionFilter): Flow<List<Transaction>> =
        transactionDao.observeFiltered(
            ownerUid = ownerUid,
            typeId = filter.type?.id,
            categoryId = filter.categoryId,
            accountId = filter.accountId,
            fromEpochDay = filter.fromEpochDay,
            toEpochDay = filter.toEpochDay,
            minMinor = filter.minAmountMinor,
            maxMinor = filter.maxAmountMinor,
            keyword = filter.keyword?.takeIf { it.isNotBlank() },
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(ownerUid: String, limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecent(ownerUid, limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertTransaction(transaction: Transaction): Outcome<Transaction> {
        val uid = session.currentUserOnce()?.uid
            ?: return Outcome.Failure(AppError.permission("Please sign in again."))
        if (uid != transaction.ownerUid) return Outcome.Failure(AppError.permission())

        // Re-validate server-side rules locally: the account must belong to the signed-in user.
        val ownedAccountIds = accountDao.activeForOwner(uid).map { it.id }.toSet() +
            transactionDao.allForOwner(uid).mapNotNull { it.accountId }.toSet()
        val validation = TransactionValidator.validate(transaction, ownedAccountIds)
        if (!validation.isValid) {
            return Outcome.Failure(AppError.validation(validation.firstError() ?: "Invalid transaction."))
        }

        val now = time.nowMillis()
        val prepared = transaction.copy(
            ownerUid = uid,
            createdAt = if (transaction.createdAt == 0L) now else transaction.createdAt,
            updatedAt = now,
            syncState = SyncState.PENDING,
        )
        transactionDao.upsert(prepared.toEntity())

        if (engine.isConfigured) {
            val pushed = engine.pushTransaction(prepared)
            if (pushed.isSuccess) {
                val synced = prepared.copy(syncState = SyncState.SYNCED)
                transactionDao.upsert(synced.toEntity())
                return Outcome.Success(synced)
            }
            sync.enqueue(uid, SyncOp.UPSERT_TRANSACTION, prepared.id, prepared.familyId)
        }
        return Outcome.Success(prepared)
    }

    override suspend fun deleteTransaction(ownerUid: String, transactionId: String): Outcome<Unit> {
        if (session.currentUserOnce()?.uid != ownerUid) return Outcome.Failure(AppError.permission())
        transactionDao.delete(ownerUid, transactionId)
        if (engine.isConfigured) {
            val pushed = engine.deleteTransaction(transactionId)
            if (pushed.isFailure) sync.enqueue(ownerUid, SyncOp.DELETE_TRANSACTION, transactionId)
        }
        return Outcome.Success(Unit)
    }

    override suspend fun syncPending(ownerUid: String): Outcome<Int> = sync.drain(ownerUid)
}

class RecurringRepositoryImpl @Inject constructor(
    private val recurringRuleDao: RecurringRuleDao,
    private val transactionDao: TransactionDao,
    private val engine: RemoteSyncEngine,
    private val sync: SyncCoordinator,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : RecurringRepository {

    override fun observeRules(ownerUid: String): Flow<List<RecurringRule>> =
        recurringRuleDao.observeForOwner(ownerUid).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertRule(rule: RecurringRule): Outcome<RecurringRule> {
        if (session.currentUserOnce()?.uid != rule.ownerUid) return Outcome.Failure(AppError.permission())
        val prepared = rule.copy(
            updatedAt = time.nowMillis(),
            createdAt = if (rule.createdAt == 0L) time.nowMillis() else rule.createdAt,
        )
        recurringRuleDao.upsert(prepared.toEntity())
        if (engine.isConfigured) {
            val pushed = engine.pushRecurringRule(prepared)
            if (pushed.isFailure) sync.enqueue(prepared.ownerUid, SyncOp.UPSERT_RULE, prepared.id)
        }
        return Outcome.Success(prepared)
    }

    override suspend fun deleteRule(ownerUid: String, ruleId: String): Outcome<Unit> {
        if (session.currentUserOnce()?.uid != ownerUid) return Outcome.Failure(AppError.permission())
        recurringRuleDao.delete(ownerUid, ruleId)
        if (engine.isConfigured) {
            val pushed = engine.deleteRecurringRule(ruleId)
            if (pushed.isFailure) sync.enqueue(ownerUid, SyncOp.DELETE_RULE, ruleId)
        }
        return Outcome.Success(Unit)
    }

    /**
     * Materialises every occurrence that has come due since the rule was last generated.
     *
     * Idempotent by construction: generation resumes strictly after `lastGeneratedEpochDay`, and each
     * generated row records the occurrence date it came from. Running this twice in a row produces
     * zero new rows the second time.
     */
    override suspend fun generateDue(ownerUid: String, upToEpochDay: Long): Outcome<Int> {
        if (session.currentUserOnce()?.uid != ownerUid) return Outcome.Failure(AppError.permission())
        var created = 0
        for (row in recurringRuleDao.activeForOwner(ownerUid)) {
            val rule = row.toDomain()
            val occurrences = RecurrenceCalculator.occurrencesBetween(rule, rule.lastGeneratedEpochDay, upToEpochDay)
            if (occurrences.isEmpty()) continue

            val existing = transactionDao.forRule(ownerUid, rule.id)
                .mapNotNull { it.recurrenceOccurrenceEpochDay }
                .toSet()

            for (occurrence in occurrences) {
                if (occurrence in existing) continue
                if (!RecurrenceCalculator.shouldGenerate(rule, occurrence)) continue
                val transaction = Transaction(
                    id = Ids.newId(),
                    clientId = Ids.newClientId(),
                    ownerUid = ownerUid,
                    familyId = rule.familyId,
                    type = rule.type,
                    amount = rule.amount,
                    categoryId = rule.categoryId,
                    subCategoryId = rule.subCategoryId,
                    accountId = rule.accountId,
                    transferFromAccountId = rule.transferFromAccountId,
                    transferToAccountId = rule.transferToAccountId,
                    dateEpochDay = occurrence,
                    note = rule.note.ifBlank { "Recurring: ${rule.frequency.label}" },
                    recurringRuleId = rule.id,
                    recurrenceOccurrenceEpochDay = occurrence,
                    createdAt = time.nowMillis(),
                    updatedAt = time.nowMillis(),
                    syncState = SyncState.PENDING,
                )
                transactionDao.upsert(transaction.toEntity())
                if (engine.isConfigured) {
                    val pushed = engine.pushTransaction(transaction)
                    if (pushed.isFailure) sync.enqueue(ownerUid, SyncOp.UPSERT_TRANSACTION, transaction.id, rule.familyId)
                }
                created++
            }

            val lastOccurrence = occurrences.last()
            recurringRuleDao.upsert(rule.copy(lastGeneratedEpochDay = lastOccurrence).toEntity())
        }
        return Outcome.Success(created)
    }
}
