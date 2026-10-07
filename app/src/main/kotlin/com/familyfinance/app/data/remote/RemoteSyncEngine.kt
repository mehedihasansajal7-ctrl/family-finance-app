package com.familyfinance.app.data.remote

import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AppNotification
import com.familyfinance.app.domain.model.AuditLog
import com.familyfinance.app.domain.model.Family
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyChatMessage
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.MemberProfile
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.PersonalLoan
import com.familyfinance.app.domain.model.RecurringRule
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.domain.model.Transaction
import java.time.YearMonth

/**
 * Outbound sync.
 *
 * The local database is the source of truth for the UI; this engine mirrors writes to the backend.
 * A failed push is queued (never dropped) and retried, so a transaction can never be silently lost.
 *
 * Implementations must derive identity from the authenticated session — no method accepts an
 * "acting as" uid.
 */
interface RemoteSyncEngine {

    /** False in local demo mode, or when the user is offline. */
    val isConfigured: Boolean

    suspend fun pushTransaction(transaction: Transaction): Outcome<Unit>
    suspend fun deleteTransaction(transactionId: String): Outcome<Unit>

    suspend fun pushAccount(account: Account): Outcome<Unit>
    suspend fun deleteAccount(accountId: String): Outcome<Unit>

    suspend fun pushRecurringRule(rule: RecurringRule): Outcome<Unit>
    suspend fun deleteRecurringRule(ruleId: String): Outcome<Unit>

    suspend fun pushFamily(family: Family): Outcome<Unit>
    suspend fun pushMemberProfile(profile: MemberProfile, role: Role, status: MemberStatus): Outcome<Unit>

    suspend fun pushChatMessage(message: FamilyChatMessage): Outcome<Unit>
    suspend fun deleteChatMessage(familyId: String, messageId: String): Outcome<Unit>
    fun observeFamilyChat(familyId: String): kotlinx.coroutines.flow.Flow<List<FamilyChatMessage>> =
        kotlinx.coroutines.flow.emptyFlow()

    suspend fun pushBudget(budget: FamilyBudget): Outcome<Unit>
    suspend fun deleteBudget(budgetId: String): Outcome<Unit>

    suspend fun pushGoal(goal: FamilyGoal): Outcome<Unit>
    suspend fun deleteGoal(goalId: String): Outcome<Unit>

    suspend fun pushBill(bill: FamilyBill): Outcome<Unit>
    suspend fun deleteBill(billId: String): Outcome<Unit>

    suspend fun pushSavingsGoal(goal: SavingsGoal): Outcome<Unit>
    suspend fun deleteSavingsGoal(goalId: String): Outcome<Unit>

    suspend fun pushLoan(loan: PersonalLoan): Outcome<Unit>
    suspend fun deleteLoan(loanId: String): Outcome<Unit>

    suspend fun pushNotification(notification: AppNotification): Outcome<Unit>
    suspend fun pushAuditLog(log: AuditLog): Outcome<Unit>

    suspend fun uploadAttachment(
        ownerUid: String,
        familyId: String,
        attachmentId: String,
        storagePath: String,
        bytes: ByteArray,
        familyVisible: Boolean,
    ): Outcome<String>

    suspend fun deleteAttachment(storagePath: String): Outcome<Unit>

    /** Uploads a chat photo to the family-shared storage path and returns its download URL. */
    suspend fun uploadChatPhoto(
        ownerUid: String,
        familyId: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<String>

    /** Fetches the server-computed, aggregate-only family dashboard payload. */
    suspend fun fetchFamilyAggregate(familyId: String, month: YearMonth): Outcome<FamilyAggregate>

    /**
     * Live aggregate stream. The default is an empty stream so demo mode and tests need no special
     * casing; the Firebase implementation subscribes to the server-written aggregate document.
     */
    fun observeFamilyAggregate(familyId: String, month: YearMonth): kotlinx.coroutines.flow.Flow<FamilyAggregate> =
        kotlinx.coroutines.flow.emptyFlow()

    /** Replays queued operations. Returns how many succeeded. */
    suspend fun drainQueue(ownerUid: String): Outcome<Int>
}

/**
 * Used when no backend is configured (local demo mode) and in unit tests.
 * Every push is a no-op success so the offline-first write path behaves identically.
 */
class NoOpRemoteSyncEngine : RemoteSyncEngine {
    override val isConfigured: Boolean = false

    override suspend fun pushTransaction(transaction: Transaction) = Outcome.Success(Unit)
    override suspend fun deleteTransaction(transactionId: String) = Outcome.Success(Unit)
    override suspend fun pushAccount(account: Account) = Outcome.Success(Unit)
    override suspend fun deleteAccount(accountId: String) = Outcome.Success(Unit)
    override suspend fun pushRecurringRule(rule: RecurringRule) = Outcome.Success(Unit)
    override suspend fun deleteRecurringRule(ruleId: String) = Outcome.Success(Unit)
    override suspend fun pushFamily(family: Family) = Outcome.Success(Unit)
    override suspend fun pushMemberProfile(profile: MemberProfile, role: Role, status: MemberStatus) = Outcome.Success(Unit)
    override suspend fun pushChatMessage(message: FamilyChatMessage) = Outcome.Success(Unit)
    override suspend fun deleteChatMessage(familyId: String, messageId: String) = Outcome.Success(Unit)
    override suspend fun pushBudget(budget: FamilyBudget) = Outcome.Success(Unit)
    override suspend fun deleteBudget(budgetId: String) = Outcome.Success(Unit)
    override suspend fun pushGoal(goal: FamilyGoal) = Outcome.Success(Unit)
    override suspend fun deleteGoal(goalId: String) = Outcome.Success(Unit)
    override suspend fun pushBill(bill: FamilyBill) = Outcome.Success(Unit)
    override suspend fun deleteBill(billId: String) = Outcome.Success(Unit)
    override suspend fun pushSavingsGoal(goal: SavingsGoal) = Outcome.Success(Unit)
    override suspend fun deleteSavingsGoal(goalId: String) = Outcome.Success(Unit)
    override suspend fun pushLoan(loan: PersonalLoan) = Outcome.Success(Unit)
    override suspend fun deleteLoan(loanId: String) = Outcome.Success(Unit)
    override suspend fun pushNotification(notification: AppNotification) = Outcome.Success(Unit)
    override suspend fun pushAuditLog(log: AuditLog) = Outcome.Success(Unit)
    override suspend fun uploadAttachment(
        ownerUid: String,
        familyId: String,
        attachmentId: String,
        storagePath: String,
        bytes: ByteArray,
        familyVisible: Boolean,
    ) = Outcome.Success(storagePath)

    override suspend fun deleteAttachment(storagePath: String) = Outcome.Success(Unit)

    override suspend fun uploadChatPhoto(
        ownerUid: String,
        familyId: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
    ) = Outcome.Failure(com.familyfinance.app.core.result.AppError.notFound("No backend configured."))

    override suspend fun fetchFamilyAggregate(familyId: String, month: YearMonth) =
        Outcome.Failure(com.familyfinance.app.core.result.AppError.notFound("No backend configured."))

    override suspend fun drainQueue(ownerUid: String) = Outcome.Success(0)
}
