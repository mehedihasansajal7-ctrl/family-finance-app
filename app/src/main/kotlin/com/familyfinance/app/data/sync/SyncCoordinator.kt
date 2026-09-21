package com.familyfinance.app.data.sync

import com.familyfinance.app.core.logging.AppLog
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.dao.AccountDao
import com.familyfinance.app.data.local.dao.FamilyFinanceDao
import com.familyfinance.app.data.local.dao.PendingOpDao
import com.familyfinance.app.data.local.dao.PersonalFinanceDao
import com.familyfinance.app.data.local.dao.RecurringRuleDao
import com.familyfinance.app.data.local.dao.TransactionDao
import com.familyfinance.app.data.local.entity.PendingOpEntity
import com.familyfinance.app.data.local.toDomain
import com.familyfinance.app.data.remote.RemoteSyncEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class OpPayload(
    val id: String,
    /** Present for family-scoped records, which are keyed by family on the backend. */
    val familyId: String? = null,
)

/** The operations the queue knows how to replay. */
enum class SyncOp(val id: String) {
    UPSERT_TRANSACTION("upsert_transaction"),
    DELETE_TRANSACTION("delete_transaction"),
    UPSERT_ACCOUNT("upsert_account"),
    DELETE_ACCOUNT("delete_account"),
    UPSERT_RULE("upsert_rule"),
    DELETE_RULE("delete_rule"),
    UPSERT_BUDGET("upsert_budget"),
    DELETE_BUDGET("delete_budget"),
    UPSERT_GOAL("upsert_goal"),
    DELETE_GOAL("delete_goal"),
    UPSERT_BILL("upsert_bill"),
    DELETE_BILL("delete_bill"),
    UPSERT_SAVINGS_GOAL("upsert_savings_goal"),
    DELETE_SAVINGS_GOAL("delete_savings_goal"),
    UPSERT_LOAN("upsert_loan"),
    DELETE_LOAN("delete_loan");

    companion object {
        fun fromId(id: String): SyncOp? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Offline write queue and its drain loop.
 *
 * Writes commit to the local database first, then this coordinator mirrors them to the backend.
 * A failed push stays queued with an attempt counter and is retried on the next drain — the user's
 * ledger is never the thing that gets lost.
 *
 * Ordering is preserved (oldest first) so an upsert followed by a delete replays in that order.
 */
class SyncCoordinator(
    private val pendingOpDao: PendingOpDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val familyFinanceDao: FamilyFinanceDao,
    private val personalFinanceDao: PersonalFinanceDao,
    private val engine: RemoteSyncEngine,
    private val time: TimeProvider = TimeProvider.System,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enqueue(ownerUid: String, op: SyncOp, recordId: String, familyId: String? = null) {
        pendingOpDao.enqueue(
            PendingOpEntity(
                id = Ids.newId(),
                ownerUid = ownerUid,
                kind = op.id,
                payloadJson = json.encodeToString(OpPayload(recordId, familyId)),
                createdAt = time.nowMillis(),
            )
        )
    }

    /** Replays the queue. Returns the number of operations that succeeded. */
    suspend fun drain(ownerUid: String): Outcome<Int> {
        if (!engine.isConfigured) return Outcome.Success(0)
        var succeeded = 0
        for (op in pendingOpDao.forOwner(ownerUid)) {
            val payload = runCatching { json.decodeFromString<OpPayload>(op.payloadJson) }.getOrNull()
            if (payload == null) {
                pendingOpDao.remove(op.id)
                continue
            }
            when (val result = replay(ownerUid, op.kind, payload)) {
                is Outcome.Success -> {
                    pendingOpDao.remove(op.id)
                    succeeded++
                }
                is Outcome.Failure -> {
                    pendingOpDao.recordFailure(op.id, result.error.code.name)
                    AppLog.w("Sync op %s failed with %s", op.kind, result.error.code.name)
                }
            }
        }
        return Outcome.Success(succeeded)
    }

    /**
     * A record that has since been deleted locally replays as a no-op success: the intent of the
     * queued write is already satisfied, so retrying forever would be wrong.
     */
    private suspend fun replay(ownerUid: String, kind: String, payload: OpPayload): Outcome<Unit> {
        val op = SyncOp.fromId(kind) ?: return Outcome.Success(Unit)
        val familyId = payload.familyId
        return when (op) {
            SyncOp.UPSERT_TRANSACTION ->
                transactionDao.find(ownerUid, payload.id)?.let { engine.pushTransaction(it.toDomain()) }
                    ?: Outcome.Success(Unit)
            SyncOp.DELETE_TRANSACTION -> engine.deleteTransaction(payload.id)

            SyncOp.UPSERT_ACCOUNT ->
                accountDao.find(ownerUid, payload.id)?.let { engine.pushAccount(it.toDomain()) }
                    ?: Outcome.Success(Unit)
            SyncOp.DELETE_ACCOUNT -> engine.deleteAccount(payload.id)

            SyncOp.UPSERT_RULE ->
                recurringRuleDao.find(ownerUid, payload.id)?.let { engine.pushRecurringRule(it.toDomain()) }
                    ?: Outcome.Success(Unit)
            SyncOp.DELETE_RULE -> engine.deleteRecurringRule(payload.id)

            SyncOp.UPSERT_BUDGET ->
                familyId?.let { familyFinanceDao.findBudget(it, payload.id) }
                    ?.let { engine.pushBudget(it.toDomain()) } ?: Outcome.Success(Unit)
            SyncOp.DELETE_BUDGET -> engine.deleteBudget(payload.id)

            SyncOp.UPSERT_GOAL ->
                familyId?.let { familyFinanceDao.findGoal(it, payload.id) }
                    ?.let { engine.pushGoal(it.toDomain()) } ?: Outcome.Success(Unit)
            SyncOp.DELETE_GOAL -> engine.deleteGoal(payload.id)

            SyncOp.UPSERT_BILL ->
                familyId?.let { familyFinanceDao.findBill(it, payload.id) }
                    ?.let { engine.pushBill(it.toDomain()) } ?: Outcome.Success(Unit)
            SyncOp.DELETE_BILL -> engine.deleteBill(payload.id)

            SyncOp.UPSERT_SAVINGS_GOAL ->
                personalFinanceDao.findSavingsGoal(ownerUid, payload.id)
                    ?.let { engine.pushSavingsGoal(it.toDomain()) } ?: Outcome.Success(Unit)
            SyncOp.DELETE_SAVINGS_GOAL -> engine.deleteSavingsGoal(payload.id)

            SyncOp.UPSERT_LOAN ->
                personalFinanceDao.findLoan(ownerUid, payload.id)
                    ?.let { engine.pushLoan(it.toDomain()) } ?: Outcome.Success(Unit)
            SyncOp.DELETE_LOAN -> engine.deleteLoan(payload.id)
        }
    }

    suspend fun pendingCount(ownerUid: String): Int = pendingOpDao.forOwner(ownerUid).size

    fun observePendingCount(ownerUid: String) = pendingOpDao.observeCount(ownerUid)
}
