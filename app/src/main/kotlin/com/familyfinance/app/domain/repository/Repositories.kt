package com.familyfinance.app.domain.repository

import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AppNotification
import com.familyfinance.app.domain.model.Attachment
import com.familyfinance.app.domain.model.Family
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.FamilyInvitation
import com.familyfinance.app.domain.model.FamilyMemberView
import com.familyfinance.app.domain.model.MemberProfile
import com.familyfinance.app.domain.model.PersonalLoan
import com.familyfinance.app.domain.model.RecurringRule
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * Repository contracts.
 *
 * Two rules hold across every interface here:
 *
 * 1. **Every personal-data method takes the owner uid explicitly** and returns only that owner's
 *    rows. No method anywhere returns personal rows for more than one uid — that is what makes the
 *    privacy guarantee auditable by reading the interface rather than the implementation.
 * 2. **Mutations are `suspend` and return an [Outcome].** A write is a real operation that can fail;
 *    it is never silently dropped, and it is never represented as a bare `Unit`.
 *
 * Reads that back a screen are `Flow`s so the UI updates itself when local data changes (including
 * changes replayed from the offline queue).
 */
interface AuthRepository {
    /** Emits the currently signed-in user, or null. */
    val currentUser: Flow<UserProfile?>

    suspend fun signIn(email: String, password: String): Outcome<UserProfile>
    suspend fun signUp(email: String, password: String, displayName: String): Outcome<UserProfile>
    suspend fun signInWithGoogle(idToken: String): Outcome<UserProfile>
    suspend fun sendPasswordReset(email: String): Outcome<Unit>
    suspend fun signOut(): Outcome<Unit>
    suspend fun deleteAccount(): Outcome<Unit>
}

interface FamilyRepository {
    fun observeFamily(familyId: String): Flow<Family?>

    suspend fun createFamily(name: String, ownerUid: String): Outcome<Family>
    suspend fun updateFamily(family: Family): Outcome<Family>

    /** Members of the family: shareable profile fields plus role and status. No financial fields. */
    fun observeMembers(familyId: String): Flow<List<FamilyMemberView>>

    suspend fun createInvitation(
        familyId: String,
        createdByUid: String,
        invitedEmail: String?,
        validForHours: Int,
    ): Outcome<FamilyInvitation>

    suspend fun revokeInvitation(invitationId: String): Outcome<Unit>
    suspend fun redeemInvitation(code: String, uid: String): Outcome<Family>

    /** Joins a family directly by its ID (the cross-device path that needs no Cloud Function). */
    suspend fun joinFamily(familyId: String, uid: String): Outcome<Family>
    suspend fun removeMember(familyId: String, uid: String): Outcome<Unit>
    suspend fun updateRole(familyId: String, uid: String, role: Role): Outcome<Unit>
}

interface ProfileRepository {
    fun observeProfile(uid: String): Flow<MemberProfile?>
    suspend fun updateProfile(profile: MemberProfile): Outcome<MemberProfile>
}

interface AccountRepository {
    fun observeAccounts(ownerUid: String): Flow<List<Account>>
    suspend fun getAccount(ownerUid: String, accountId: String): Outcome<Account>
    suspend fun upsertAccount(account: Account): Outcome<Account>
    suspend fun deleteAccount(ownerUid: String, accountId: String): Outcome<Unit>
}

interface TransactionRepository {
    fun observeTransactions(ownerUid: String, filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>>
    fun observeRecent(ownerUid: String, limit: Int): Flow<List<Transaction>>
    suspend fun getTransaction(ownerUid: String, transactionId: String): Outcome<Transaction>
    suspend fun upsertTransaction(transaction: Transaction): Outcome<Transaction>
    suspend fun deleteTransaction(ownerUid: String, transactionId: String): Outcome<Unit>

    /** Replays queued offline writes. Idempotent via [Transaction.clientId]. */
    suspend fun syncPending(ownerUid: String): Outcome<Int>
}

interface RecurringRepository {
    fun observeRules(ownerUid: String): Flow<List<RecurringRule>>
    suspend fun upsertRule(rule: RecurringRule): Outcome<RecurringRule>
    suspend fun deleteRule(ownerUid: String, ruleId: String): Outcome<Unit>

    /** Materialises due occurrences. Safe to call repeatedly — it cannot double-post. */
    suspend fun generateDue(ownerUid: String, upToEpochDay: Long): Outcome<Int>
}

interface FamilyFinanceRepository {
    /** Family-level totals only; never member-attributed. */
    fun observeAggregate(familyId: String, month: YearMonth): Flow<FamilyAggregate>

    fun observeBudgets(familyId: String): Flow<List<FamilyBudget>>
    suspend fun upsertBudget(budget: FamilyBudget): Outcome<FamilyBudget>
    suspend fun deleteBudget(familyId: String, budgetId: String): Outcome<Unit>

    fun observeGoals(familyId: String): Flow<List<FamilyGoal>>
    suspend fun upsertGoal(goal: FamilyGoal): Outcome<FamilyGoal>
    suspend fun deleteGoal(familyId: String, goalId: String): Outcome<Unit>

    fun observeBills(familyId: String): Flow<List<FamilyBill>>
    suspend fun upsertBill(bill: FamilyBill): Outcome<FamilyBill>
    suspend fun deleteBill(familyId: String, billId: String): Outcome<Unit>
}

interface PersonalFinanceRepository {
    fun observeLoans(ownerUid: String): Flow<List<PersonalLoan>>
    suspend fun upsertLoan(loan: PersonalLoan): Outcome<PersonalLoan>
    suspend fun deleteLoan(ownerUid: String, loanId: String): Outcome<Unit>

    fun observeSavingsGoals(ownerUid: String): Flow<List<SavingsGoal>>
    suspend fun upsertSavingsGoal(goal: SavingsGoal): Outcome<SavingsGoal>
    suspend fun deleteSavingsGoal(ownerUid: String, goalId: String): Outcome<Unit>
}

interface AttachmentRepository {
    fun observeAttachments(ownerUid: String, transactionId: String? = null): Flow<List<Attachment>>

    suspend fun upload(
        ownerUid: String,
        familyId: String,
        transactionId: String?,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        familyVisible: Boolean,
    ): Outcome<Attachment>

    suspend fun delete(ownerUid: String, attachmentId: String): Outcome<Unit>
    suspend fun downloadUrl(ownerUid: String, attachmentId: String): Outcome<String>
}

interface NotificationRepository {
    fun observeNotifications(ownerUid: String): Flow<List<AppNotification>>
    fun unreadCount(ownerUid: String): Flow<Int>
    suspend fun markRead(ownerUid: String, notificationId: String): Outcome<Unit>
    suspend fun notify(
        ownerUid: String,
        familyId: String,
        category: String,
        title: String,
        body: String,
        relatedId: String? = null,
    )
}

interface AuditRepository {
    suspend fun record(
        familyId: String?,
        actorUid: String,
        action: String,
        context: Map<String, String> = emptyMap(),
    )

    fun observeRecent(familyId: String, limit: Int): Flow<List<com.familyfinance.app.domain.model.AuditLog>>

    /**
     * Audit action names. Kept as constants on the interface so callers cannot invent a new action
     * string (or misspell an existing one) and quietly create an unqueryable event.
     */
    companion object {
        const val ACTION_SIGN_IN = "sign_in"
        const val ACTION_SIGN_OUT = "sign_out"
        const val ACTION_SIGN_UP = "sign_up"
        const val ACTION_FAMILY_CREATED = "family_created"
        const val ACTION_FAMILY_JOINED = "family_joined"
        const val ACTION_INVITATION_CREATED = "invitation_created"
        const val ACTION_INVITATION_REVOKED = "invitation_revoked"
        const val ACTION_MEMBER_REMOVED = "member_removed"
        const val ACTION_ROLE_CHANGED = "role_changed"
        const val ACTION_ACCOUNT_CREATED = "account_created"
        const val ACTION_ACCOUNT_UPDATED = "account_updated"
        const val ACTION_ACCOUNT_DELETED = "account_deleted"
        const val ACTION_PROFILE_UPDATED = "profile_updated"
        const val ACTION_TRANSACTION_CREATED = "transaction_created"
        const val ACTION_TRANSACTION_UPDATED = "transaction_updated"
        const val ACTION_TRANSACTION_DELETED = "transaction_deleted"
        const val ACTION_ATTACHMENT_ADDED = "attachment_added"
        const val ACTION_ATTACHMENT_DELETED = "attachment_deleted"
    }
}

/**
 * Personal-transaction search. Every field is optional; `null` means "do not filter on this".
 * There is deliberately no member/owner filter: the query is already scoped to one owner by the
 * repository method it is passed to.
 */
data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val keyword: String? = null,
) {
    val isActive: Boolean
        get() = type != null || categoryId != null || accountId != null || fromEpochDay != null ||
            toEpochDay != null || minAmountMinor != null || maxAmountMinor != null || !keyword.isNullOrBlank()

    fun cleared(): TransactionFilter = TransactionFilter()
}
