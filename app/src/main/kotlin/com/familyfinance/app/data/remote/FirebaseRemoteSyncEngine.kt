package com.familyfinance.app.data.remote

import com.familyfinance.app.core.logging.AppLog
import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.Account
import com.familyfinance.app.domain.model.AppNotification
import com.familyfinance.app.domain.model.AuditLog
import com.familyfinance.app.domain.model.CategoryTotal
import com.familyfinance.app.domain.model.Family
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyChatMessage
import com.familyfinance.app.domain.model.ChatReadState
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.MemberProfile
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.MonthlyPoint
import com.familyfinance.app.domain.model.PersonalLoan
import com.familyfinance.app.domain.model.RecurringRule
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.domain.model.Transaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore / Cloud Storage mirror.
 *
 * ## Identity
 * Every write targets a path derived from the **authenticated** uid (`FirebaseAuth.currentUser`).
 * No method accepts an "acting as" uid, so a caller cannot address another member's document even
 * if it wanted to. `backend/firestore.rules` is the actual enforcement point; this class simply does
 * not attempt anything the rules would reject.
 *
 * ## Layout
 * ```
 * families/{familyId}                              family document (shared)
 * families/{familyId}/members/{uid}                membership: role, status (shared)
 * families/{familyId}/budgets|goals|bills/{id}     shared family finance
 * families/{familyId}/invitations/{id}             invite codes
 * families/{familyId}/aggregates/{yyyy-MM}         SERVER-WRITTEN, aggregate-only, client-read-only
 * users/{uid}/profile/main                         private profile
 * users/{uid}/accounts/{accountId}                 PRIVATE
 * users/{uid}/transactions/{transactionId}         PRIVATE
 * users/{uid}/recurringRules/{ruleId}              PRIVATE
 * users/{uid}/savingsGoals/{id}                    PRIVATE
 * users/{uid}/loans/{id}                           PRIVATE
 * users/{uid}/notifications/{id}                   PRIVATE
 * users/{uid}/audit/{id}                           PRIVATE, append-only
 * ```
 * Note what is absent: nothing private is ever written under the `families` collection. Private
 * records live in per-user subcollections, which is what makes a single rule shape sufficient to
 * protect all of them.
 */
@Singleton
class FirebaseRemoteSyncEngine @Inject constructor(
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : RemoteSyncEngine {

    // Firebase singletons are resolved lazily: constructing this class in demo mode must not throw.
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    private val uid: String? get() = auth.currentUser?.uid

    override val isConfigured: Boolean get() = uid != null

    // ------------------------------------------------------------------ private: user tree

    private fun mine(collection: String): com.google.firebase.firestore.CollectionReference? =
        uid?.let { db.collection("users").document(it).collection(collection) }

    override suspend fun pushTransaction(transaction: Transaction): Outcome<Unit> = guard(transaction.ownerUid) {
        mine(COL_TRANSACTIONS)?.document(transaction.id)?.set(transaction.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteTransaction(transactionId: String): Outcome<Unit> = guard(null) {
        mine(COL_TRANSACTIONS)?.document(transactionId)?.delete()?.await()
        Unit
    }

    override suspend fun pushAccount(account: Account): Outcome<Unit> = guard(account.ownerUid) {
        mine(COL_ACCOUNTS)?.document(account.id)?.set(account.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteAccount(accountId: String): Outcome<Unit> = guard(null) {
        mine(COL_ACCOUNTS)?.document(accountId)?.delete()?.await()
        Unit
    }

    override suspend fun pushRecurringRule(rule: RecurringRule): Outcome<Unit> = guard(rule.ownerUid) {
        mine(COL_RULES)?.document(rule.id)?.set(rule.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteRecurringRule(ruleId: String): Outcome<Unit> = guard(null) {
        mine(COL_RULES)?.document(ruleId)?.delete()?.await()
        Unit
    }

    override suspend fun pushSavingsGoal(goal: SavingsGoal): Outcome<Unit> = guard(goal.ownerUid) {
        mine(COL_SAVINGS)?.document(goal.id)?.set(goal.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteSavingsGoal(goalId: String): Outcome<Unit> = guard(null) {
        mine(COL_SAVINGS)?.document(goalId)?.delete()?.await()
        Unit
    }

    override suspend fun pushLoan(loan: PersonalLoan): Outcome<Unit> = guard(loan.ownerUid) {
        mine(COL_LOANS)?.document(loan.id)?.set(loan.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteLoan(loanId: String): Outcome<Unit> = guard(null) {
        mine(COL_LOANS)?.document(loanId)?.delete()?.await()
        Unit
    }

    override suspend fun pushNotification(notification: AppNotification): Outcome<Unit> =
        guard(notification.ownerUid) {
            mine(COL_NOTIFICATIONS)?.document(notification.id)
                ?.set(notification.toMap(), SetOptions.merge())?.await()
            Unit
        }

    override suspend fun pushAuditLog(log: AuditLog): Outcome<Unit> = guard(log.actorUid) {
        mine(COL_AUDIT)?.document(log.id)?.set(log.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun pushMemberProfile(
        profile: MemberProfile,
        role: Role,
        status: MemberStatus,
    ): Outcome<Unit> = guard(profile.uid) {
        // The full profile is private to its owner...
        mine(COL_PROFILE)?.document("main")?.set(profile.toMap(), SetOptions.merge())?.await()
        // ...while only the shareable subset plus membership metadata goes to the family.
        db.collection("families").document(profile.familyId)
            .collection("members").document(profile.uid)
            .set(
                mapOf(
                    "uid" to profile.uid,
                    "fullName" to profile.fullName,
                    "photoUrl" to profile.photoUrl,
                    "relationship" to profile.relationship,
                    "role" to role.id,
                    "status" to status.id,
                    "shareProfileWithFamily" to profile.shareProfileWithFamily,
                    "updatedAt" to time.nowMillis(),
                ),
                SetOptions.merge(),
            ).await()
        Unit
    }

    // ------------------------------------------------------------------ shared: family tree

    override suspend fun pushFamily(family: Family): Outcome<Unit> = guard(null) {
        db.collection("families").document(family.id)
            .set(family.toMap(), SetOptions.merge()).await()
        Unit
    }

    override suspend fun pushChatMessage(message: FamilyChatMessage): Outcome<Unit> = guard(message.senderUid) {
        familyCollection(message.familyId, COL_CHAT)?.document(message.id)
            ?.set(message.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteChatMessage(familyId: String, messageId: String): Outcome<Unit> = guard(null) {
        familyCollection(familyId, COL_CHAT)?.document(messageId)?.delete()?.await()
        Unit
    }

    override fun observeFamilyChat(familyId: String): Flow<List<FamilyChatMessage>> = callbackFlow {
        val registration = familyCollection(familyId, COL_CHAT)
            ?.orderBy("createdAt")
            ?.limitToLast(300)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLog.w("Chat listener error: %s", error.code)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents.orEmpty().mapNotNull { it.toFamilyChatMessage() }
                trySend(messages)
            }
        awaitClose { registration?.remove() }
    }

    override suspend fun pushChatRead(familyId: String, uid: String, lastReadAt: Long): Outcome<Unit> = guard(uid) {
        familyCollection(familyId, COL_CHAT_READS)?.document(uid)
            ?.set(mapOf("uid" to uid, "lastReadAt" to lastReadAt), SetOptions.merge())?.await()
        Unit
    }

    override fun observeChatReads(familyId: String): Flow<List<ChatReadState>> = callbackFlow {
        val registration = familyCollection(familyId, COL_CHAT_READS)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLog.w("Chat reads listener error: %s", error.code)
                    return@addSnapshotListener
                }
                val reads = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: return@mapNotNull null
                    val lastReadAt = doc.getLong("lastReadAt") ?: return@mapNotNull null
                    ChatReadState(familyId = familyId, uid = uid, lastReadAt = lastReadAt)
                }
                trySend(reads)
            }
        awaitClose { registration?.remove() }
    }

    override suspend fun pushBudget(budget: FamilyBudget): Outcome<Unit> = guard(null) {
        familyCollection(budget.familyId, COL_BUDGETS)?.document(budget.id)
            ?.set(budget.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteBudget(budgetId: String): Outcome<Unit> = guard(null) {
        familyCollection(session.currentUserOnce()?.familyId, COL_BUDGETS)?.document(budgetId)?.delete()?.await()
        Unit
    }

    override suspend fun pushGoal(goal: FamilyGoal): Outcome<Unit> = guard(null) {
        familyCollection(goal.familyId, COL_GOALS)?.document(goal.id)
            ?.set(goal.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteGoal(goalId: String): Outcome<Unit> = guard(null) {
        familyCollection(session.currentUserOnce()?.familyId, COL_GOALS)?.document(goalId)?.delete()?.await()
        Unit
    }

    override suspend fun pushBill(bill: FamilyBill): Outcome<Unit> = guard(null) {
        familyCollection(bill.familyId, COL_BILLS)?.document(bill.id)
            ?.set(bill.toMap(), SetOptions.merge())?.await()
        Unit
    }

    override suspend fun deleteBill(billId: String): Outcome<Unit> = guard(null) {
        familyCollection(session.currentUserOnce()?.familyId, COL_BILLS)?.document(billId)?.delete()?.await()
        Unit
    }

    // ------------------------------------------------------------------ attachments

    override suspend fun uploadAttachment(
        ownerUid: String,
        familyId: String,
        attachmentId: String,
        storagePath: String,
        bytes: ByteArray,
        familyVisible: Boolean,
    ): Outcome<String> = guard(ownerUid) {
        // A family-visible file goes to the shared path so `storage.rules` can grant members read
        // access; anything else stays under the owner's own prefix and is unreadable to everyone else.
        val path = if (familyVisible) {
            "families/$familyId/shared/$ownerUid/$storagePath"
        } else {
            "users/$ownerUid/$storagePath"
        }
        val ref = storage.reference.child(path)
        ref.putBytes(bytes).await()
        ref.downloadUrl.await().toString()
    }

    override suspend fun deleteAttachment(storagePath: String): Outcome<Unit> = guard(null) {
        val owner = uid ?: return@guard Unit
        runCatching { storage.reference.child("users/$owner/$storagePath").delete().await() }
        Unit
    }

    override suspend fun uploadChatPhoto(
        ownerUid: String,
        familyId: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<String> = guard(ownerUid) {
        val ref = storage.reference.child("families/$familyId/shared/$ownerUid/$storagePath")
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType(mimeType)
            .build()
        ref.putBytes(bytes, metadata).await()
        ref.downloadUrl.await().toString()
    }

    // ------------------------------------------------------------------ aggregate (read-only)

    override suspend fun fetchFamilyAggregate(familyId: String, month: YearMonth): Outcome<FamilyAggregate> =
        guard(null) {
            val snapshot = db.collection("families").document(familyId)
                .collection(COL_AGGREGATES).document(monthKey(month))
                .get().await()
            snapshot.toFamilyAggregate(month)
                ?: throw NoSuchElementException("Aggregate not computed yet.")
        }

    /**
     * Subscribes to the server-written aggregate document.
     *
     * This is the only family-wide financial data the client ever receives: totals, family category
     * totals and a trend series. No member attribution exists in the document to leak.
     */
    override fun observeFamilyAggregate(familyId: String, month: YearMonth): Flow<FamilyAggregate> = callbackFlow {
        val registration = db.collection("families").document(familyId)
            .collection(COL_AGGREGATES).document(monthKey(month))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLog.w("Aggregate listener error: %s", error.code)
                    return@addSnapshotListener
                }
                snapshot?.toFamilyAggregate(month)?.let { trySend(it) }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun drainQueue(ownerUid: String): Outcome<Int> {
        // Draining is owned by SyncCoordinator, which holds the local queue. Nothing to do here.
        return Outcome.Success(0)
    }

    // ------------------------------------------------------------------ helpers

    private fun familyCollection(familyId: String?, name: String) =
        familyId?.let { db.collection("families").document(it).collection(name) }

    /**
     * Refuses any operation whose payload claims an owner other than the authenticated user.
     * The rules would reject it anyway; failing here keeps the error local and cheap.
     */
    private inline fun <T> guard(claimedOwnerUid: String?, block: () -> T): Outcome<T> {
        val current = uid ?: return Outcome.Failure(AppError.permission("Not signed in."))
        if (claimedOwnerUid != null && claimedOwnerUid != current) {
            AppLog.w("Blocked cross-owner write attempt")
            return Outcome.Failure(AppError.permission())
        }
        return try {
            Outcome.Success(block())
        } catch (error: Exception) {
            AppLog.e("Remote write failed", error)
            Outcome.Failure(AppError.network())
        }
    }

    private fun monthKey(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)

    companion object {
        private const val COL_ACCOUNTS = "accounts"
        private const val COL_TRANSACTIONS = "transactions"
        private const val COL_RULES = "recurringRules"
        private const val COL_SAVINGS = "savingsGoals"
        private const val COL_LOANS = "loans"
        private const val COL_NOTIFICATIONS = "notifications"
        private const val COL_AUDIT = "audit"
        private const val COL_PROFILE = "profile"

        private const val COL_BUDGETS = "budgets"
        private const val COL_GOALS = "goals"
        private const val COL_BILLS = "bills"
        private const val COL_AGGREGATES = "aggregates"
        private const val COL_CHAT = "chatMessages"
        private const val COL_CHAT_READS = "chatReads"
    }
}

// ------------------------------------------------------------------ document mapping
// Money crosses the wire as integer minor units, never as a floating-point number.

private fun Transaction.toMap(): Map<String, Any?> = mapOf(
    "transactionId" to id,
    "clientId" to clientId,
    "ownerUid" to ownerUid,
    "familyId" to familyId,
    "type" to type.id,
    "amountMinor" to amount.minor,
    "categoryId" to categoryId,
    "subCategoryId" to subCategoryId,
    "accountId" to accountId,
    "transferFromAccountId" to transferFromAccountId,
    "transferToAccountId" to transferToAccountId,
    "dateEpochDay" to dateEpochDay,
    "note" to note,
    "attachmentIds" to attachmentIds,
    "recurringRuleId" to recurringRuleId,
    "recurrenceOccurrenceEpochDay" to recurrenceOccurrenceEpochDay,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

private fun Account.toMap(): Map<String, Any?> = mapOf(
    "accountId" to id,
    "ownerUid" to ownerUid,
    "familyId" to familyId,
    "name" to name,
    "type" to type.id,
    "institution" to institution,
    "identifier" to identifier,
    "last4" to last4,
    "openingBalanceMinor" to openingBalance.minor,
    "currencyCode" to currencyCode,
    "isActive" to isActive,
    "notes" to notes,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
)

private fun RecurringRule.toMap(): Map<String, Any?> = mapOf(
    "ruleId" to id,
    "ownerUid" to ownerUid,
    "familyId" to familyId,
    "type" to type.id,
    "amountMinor" to amount.minor,
    "categoryId" to categoryId,
    "subCategoryId" to subCategoryId,
    "accountId" to accountId,
    "transferFromAccountId" to transferFromAccountId,
    "transferToAccountId" to transferToAccountId,
    "note" to note,
    "frequency" to frequency.id,
    "customIntervalDays" to customIntervalDays,
    "startEpochDay" to startEpochDay,
    "endEpochDay" to endEpochDay,
    "lastGeneratedEpochDay" to lastGeneratedEpochDay,
    "isActive" to isActive,
    "reminderEnabled" to reminderEnabled,
    "updatedAt" to updatedAt,
)

private fun SavingsGoal.toMap(): Map<String, Any?> = mapOf(
    "goalId" to id,
    "ownerUid" to ownerUid,
    "familyId" to familyId,
    "name" to name,
    "targetAmountMinor" to targetAmount.minor,
    "currentAmountMinor" to currentAmount.minor,
    "targetEpochDay" to targetEpochDay,
    "note" to note,
    "status" to status.id,
    "updatedAt" to updatedAt,
)

private fun PersonalLoan.toMap(): Map<String, Any?> = mapOf(
    "loanId" to id,
    "ownerUid" to ownerUid,
    "familyId" to familyId,
    "direction" to direction.id,
    "counterparty" to counterparty,
    "principalMinor" to principal.minor,
    "interestRatePercent" to interestRatePercent,
    "installmentAmountMinor" to installmentAmount?.minor,
    "totalInstallments" to totalInstallments,
    "paidInstallments" to paidInstallments,
    "repaidAmountMinor" to repaidAmount.minor,
    "startEpochDay" to startEpochDay,
    "dueEpochDay" to dueEpochDay,
    "note" to note,
    "isClosed" to isClosed,
    "updatedAt" to updatedAt,
)

private fun Family.toMap(): Map<String, Any?> = mapOf(
    "familyId" to id,
    "name" to name,
    "photoUrl" to photoUrl,
    "currencyCode" to currencyCode,
    "timezone" to timezone,
    "ownerUid" to ownerUid,
    "netWorthEnabled" to netWorthEnabled,
    "createdAt" to createdAt,
)

private fun FamilyBudget.toMap(): Map<String, Any?> = mapOf(
    "budgetId" to id,
    "familyId" to familyId,
    "name" to name,
    "categoryId" to categoryId,
    "periodEpochDayStart" to periodEpochDayStart,
    "periodEpochDayEnd" to periodEpochDayEnd,
    "limitMinor" to limit.minor,
    "createdByUid" to createdByUid,
    "updatedAt" to updatedAt,
)

private fun FamilyGoal.toMap(): Map<String, Any?> = mapOf(
    "goalId" to id,
    "familyId" to familyId,
    "name" to name,
    "categoryId" to categoryId,
    "targetAmountMinor" to targetAmount.minor,
    "currentAmountMinor" to currentAmount.minor,
    "targetEpochDay" to targetEpochDay,
    "description" to description,
    "status" to status.id,
    "updatedAt" to updatedAt,
)

private fun FamilyBill.toMap(): Map<String, Any?> = mapOf(
    "billId" to id,
    "familyId" to familyId,
    "name" to name,
    "categoryId" to categoryId,
    "expectedAmountMinor" to expectedAmount.minor,
    "dueEpochDay" to dueEpochDay,
    "frequency" to frequency.id,
    "status" to status.id,
    "reminderEnabled" to reminderEnabled,
    "reminderDaysBefore" to reminderDaysBefore,
    "updatedAt" to updatedAt,
)

private fun AppNotification.toMap(): Map<String, Any?> = mapOf(
    "notificationId" to id,
    "ownerUid" to ownerUid,
    "familyId" to familyId,
    "categoryId" to categoryId,
    "title" to title,
    "body" to body,
    "relatedId" to relatedId,
    "createdAt" to createdAt,
    "readAt" to readAt,
)

private fun AuditLog.toMap(): Map<String, Any?> = mapOf(
    "logId" to id,
    "familyId" to familyId,
    "actorUid" to actorUid,
    "action" to action,
    "context" to context,
    "createdAt" to createdAt,
)

private fun FamilyChatMessage.toMap(): Map<String, Any?> = mapOf(
    "messageId" to id,
    "familyId" to familyId,
    "senderUid" to senderUid,
    "senderName" to senderName,
    "text" to text,
    "createdAt" to createdAt,
    "attachmentUrl" to attachmentUrl,
    "attachmentContentType" to attachmentContentType,
    "replyToId" to replyToId,
    "replyPreview" to replyPreview,
    "reactions" to reactions,
)

private fun MemberProfile.toMap(): Map<String, Any?> = mapOf(
    "uid" to uid,
    "familyId" to familyId,
    "fullName" to fullName,
    "photoUrl" to photoUrl,
    "dateOfBirthEpochDay" to dateOfBirthEpochDay,
    "mobile" to mobile,
    "email" to email,
    "relationship" to relationship,
    "occupation" to occupation,
    "notes" to notes,
    "shareProfileWithFamily" to shareProfileWithFamily,
    "updatedAt" to updatedAt,
)

private fun DocumentSnapshot.toFamilyChatMessage(): FamilyChatMessage? {
    if (!exists()) return null
    val id = getString("messageId") ?: id
    val familyId = getString("familyId") ?: return null
    val senderUid = getString("senderUid") ?: return null
    val senderName = getString("senderName") ?: return null
    val text = getString("text") ?: return null
    val createdAt = getLong("createdAt") ?: return null
    val attachmentUrl = getString("attachmentUrl")
    val attachmentContentType = getString("attachmentContentType")
    val replyToId = getString("replyToId")
    val replyPreview = getString("replyPreview")
    val reactions = (get("reactions") as? Map<*, *>)
        ?.mapNotNull { (key, value) ->
            val k = key as? String ?: return@mapNotNull null
            val v = value as? String ?: return@mapNotNull null
            k to v
        }
        ?.toMap()
        .orEmpty()
    return FamilyChatMessage(
        id, familyId, senderUid, senderName, text, createdAt,
        attachmentUrl, attachmentContentType, replyToId, replyPreview, reactions,
    )
}

private fun DocumentSnapshot.toFamilyAggregate(month: YearMonth): FamilyAggregate? {
    if (!exists()) return null
    fun money(key: String) = com.familyfinance.app.core.money.Money.ofMinor(getLong(key) ?: 0L)

    val categories = (get("expenseByCategory") as? List<*>)
        ?.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            val id = map["categoryId"] as? String ?: return@mapNotNull null
            CategoryTotal(id, com.familyfinance.app.core.money.Money.ofMinor((map["totalMinor"] as? Number)?.toLong() ?: 0L))
        }
        .orEmpty()

    val trend = (get("monthlyTrend") as? List<*>)
        ?.mapNotNull { row ->
            val map = row as? Map<*, *> ?: return@mapNotNull null
            val key = map["month"] as? String ?: return@mapNotNull null
            val parsed = runCatching { YearMonth.parse(key) }.getOrNull() ?: return@mapNotNull null
            MonthlyPoint(
                month = parsed,
                income = com.familyfinance.app.core.money.Money.ofMinor((map["incomeMinor"] as? Number)?.toLong() ?: 0L),
                expense = com.familyfinance.app.core.money.Money.ofMinor((map["expenseMinor"] as? Number)?.toLong() ?: 0L),
                savings = com.familyfinance.app.core.money.Money.ofMinor((map["savingsMinor"] as? Number)?.toLong() ?: 0L),
            )
        }
        .orEmpty()

    return FamilyAggregate(
        month = month,
        totalIncome = money("totalIncomeMinor"),
        totalExpense = money("totalExpenseMinor"),
        totalSavings = money("totalSavingsMinor"),
        netWorth = if (contains("netWorthMinor")) money("netWorthMinor") else null,
        memberCount = (getLong("memberCount") ?: 0L).toInt(),
        expenseByCategory = categories,
        monthlyTrend = trend,
        previousMonthIncome = money("previousMonthIncomeMinor"),
        previousMonthExpense = money("previousMonthExpenseMinor"),
    )
}
