package com.familyfinance.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.familyfinance.app.data.local.entity.AccountEntity
import com.familyfinance.app.data.local.entity.AttachmentEntity
import com.familyfinance.app.data.local.entity.AuditLogEntity
import com.familyfinance.app.data.local.entity.FamilyBillEntity
import com.familyfinance.app.data.local.entity.FamilyBudgetEntity
import com.familyfinance.app.data.local.entity.FamilyEntity
import com.familyfinance.app.data.local.entity.FamilyGoalEntity
import com.familyfinance.app.data.local.entity.InvitationEntity
import com.familyfinance.app.data.local.entity.MemberProfileEntity
import com.familyfinance.app.data.local.entity.NotificationEntity
import com.familyfinance.app.data.local.entity.PendingOpEntity
import com.familyfinance.app.data.local.entity.PersonalLoanEntity
import com.familyfinance.app.data.local.entity.RecurringRuleEntity
import com.familyfinance.app.data.local.entity.SavingsGoalEntity
import com.familyfinance.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAOs.
 *
 * Invariant: every method that can return a *personal* row takes `ownerUid` and filters on it in
 * SQL. There is deliberately no `getAllTransactions()` anywhere in this file — a query that cannot
 * be written cannot be misused.
 */

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE ownerUid = :ownerUid ORDER BY isActive DESC, name ASC")
    fun observeForOwner(ownerUid: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE ownerUid = :ownerUid AND isActive = 1 ORDER BY name ASC")
    suspend fun activeForOwner(ownerUid: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE ownerUid = :ownerUid AND id = :accountId LIMIT 1")
    suspend fun find(ownerUid: String, accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE ownerUid = :ownerUid AND id = :accountId")
    suspend fun delete(ownerUid: String, accountId: String)
}

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE ownerUid = :ownerUid
          AND (:typeId IS NULL OR typeId = :typeId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:accountId IS NULL OR accountId = :accountId
               OR transferFromAccountId = :accountId OR transferToAccountId = :accountId)
          AND (:fromEpochDay IS NULL OR dateEpochDay >= :fromEpochDay)
          AND (:toEpochDay IS NULL OR dateEpochDay <= :toEpochDay)
          AND (:minMinor IS NULL OR amountMinor >= :minMinor)
          AND (:maxMinor IS NULL OR amountMinor <= :maxMinor)
          AND (:keyword IS NULL OR note LIKE '%' || :keyword || '%')
        ORDER BY dateEpochDay DESC, createdAt DESC
        """,
    )
    fun observeFiltered(
        ownerUid: String,
        typeId: String?,
        categoryId: String?,
        accountId: String?,
        fromEpochDay: Long?,
        toEpochDay: Long?,
        minMinor: Long?,
        maxMinor: Long?,
        keyword: String?,
    ): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE ownerUid = :ownerUid " +
            "ORDER BY dateEpochDay DESC, createdAt DESC LIMIT :limit",
    )
    fun observeRecent(ownerUid: String, limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE ownerUid = :ownerUid ORDER BY dateEpochDay DESC, createdAt DESC")
    suspend fun allForOwner(ownerUid: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE ownerUid = :ownerUid AND dateEpochDay BETWEEN :from AND :to")
    suspend fun inRange(ownerUid: String, from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE ownerUid = :ownerUid AND id = :transactionId LIMIT 1")
    suspend fun find(ownerUid: String, transactionId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE clientId = :clientId LIMIT 1")
    suspend fun findByClientId(clientId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE ownerUid = :ownerUid AND recurringRuleId = :ruleId")
    suspend fun forRule(ownerUid: String, ruleId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE ownerUid = :ownerUid AND id = :transactionId")
    suspend fun delete(ownerUid: String, transactionId: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE ownerUid = :ownerUid")
    suspend fun countForOwner(ownerUid: String): Int

    /**
     * **LOCAL DEMO MODE ONLY — never call this on a synced device.**
     *
     * The family dashboard must not be built by pulling every member's private transactions to every
     * device (see docs/SECURITY.md § aggregate privacy). On a real deployment the aggregate is
     * computed by a Cloud Function and only the aggregate document is read. This query exists solely
     * for the single-device demo mode, where every member's data already lives in this database and
     * there is no trust boundary to cross. The name is deliberately explicit so it cannot be reached
     * for by accident.
     */
    @Query("SELECT * FROM transactions WHERE familyId = :familyId")
    suspend fun allForFamilyLocalDemo(familyId: String): List<TransactionEntity>
}

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules WHERE ownerUid = :ownerUid ORDER BY isActive DESC, startEpochDay DESC")
    fun observeForOwner(ownerUid: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE ownerUid = :ownerUid AND isActive = 1")
    suspend fun activeForOwner(ownerUid: String): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE ownerUid = :ownerUid AND id = :ruleId LIMIT 1")
    suspend fun find(ownerUid: String, ruleId: String): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE ownerUid = :ownerUid AND id = :ruleId")
    suspend fun delete(ownerUid: String, ruleId: String)
}

@Dao
interface FamilyDao {

    @Query("SELECT * FROM families WHERE id = :familyId LIMIT 1")
    fun observeFamily(familyId: String): Flow<FamilyEntity?>

    @Query("SELECT * FROM families WHERE id = :familyId LIMIT 1")
    suspend fun find(familyId: String): FamilyEntity?

    @Query("SELECT * FROM families LIMIT 1")
    suspend fun findAny(): FamilyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(family: FamilyEntity)

    @Query("SELECT * FROM member_profiles WHERE familyId = :familyId ORDER BY fullName ASC")
    fun observeMembers(familyId: String): Flow<List<MemberProfileEntity>>

    @Query("SELECT * FROM member_profiles WHERE familyId = :familyId ORDER BY fullName ASC")
    suspend fun members(familyId: String): List<MemberProfileEntity>

    @Query("SELECT * FROM member_profiles WHERE uid = :uid LIMIT 1")
    fun observeMember(uid: String): Flow<MemberProfileEntity?>

    @Query("SELECT * FROM member_profiles WHERE uid = :uid LIMIT 1")
    suspend fun findMember(uid: String): MemberProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: MemberProfileEntity)

    @Query("DELETE FROM member_profiles WHERE uid = :uid")
    suspend fun deleteMember(uid: String)

    @Query("UPDATE member_profiles SET roleId = :roleId WHERE uid = :uid")
    suspend fun updateRole(uid: String, roleId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvitation(invitation: InvitationEntity)

    @Query("SELECT * FROM invitations WHERE code = :code LIMIT 1")
    suspend fun findInvitationByCode(code: String): InvitationEntity?

    @Query("SELECT * FROM invitations WHERE familyId = :familyId ORDER BY createdAt DESC")
    fun observeInvitations(familyId: String): Flow<List<InvitationEntity>>

    @Query("UPDATE invitations SET revokedAt = :revokedAt WHERE id = :id")
    suspend fun revokeInvitation(id: String, revokedAt: Long)

    @Query("UPDATE invitations SET usedCount = usedCount + 1 WHERE id = :id")
    suspend fun incrementInvitationUse(id: String)
}

@Dao
interface FamilyFinanceDao {

    @Query("SELECT * FROM family_budgets WHERE familyId = :familyId ORDER BY periodEpochDayStart DESC")
    fun observeBudgets(familyId: String): Flow<List<FamilyBudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: FamilyBudgetEntity)

    @Query("DELETE FROM family_budgets WHERE familyId = :familyId AND id = :id")
    suspend fun deleteBudget(familyId: String, id: String)

    @Query("SELECT * FROM family_goals WHERE familyId = :familyId ORDER BY createdAt DESC")
    fun observeGoals(familyId: String): Flow<List<FamilyGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: FamilyGoalEntity)

    @Query("DELETE FROM family_goals WHERE familyId = :familyId AND id = :id")
    suspend fun deleteGoal(familyId: String, id: String)

    @Query("SELECT * FROM family_bills WHERE familyId = :familyId ORDER BY dueEpochDay ASC")
    fun observeBills(familyId: String): Flow<List<FamilyBillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBill(bill: FamilyBillEntity)

    @Query("DELETE FROM family_bills WHERE familyId = :familyId AND id = :id")
    suspend fun deleteBill(familyId: String, id: String)

    @Query("SELECT * FROM family_budgets WHERE familyId = :familyId")
    suspend fun budgets(familyId: String): List<FamilyBudgetEntity>

    @Query("SELECT * FROM family_budgets WHERE familyId = :familyId AND id = :id LIMIT 1")
    suspend fun findBudget(familyId: String, id: String): FamilyBudgetEntity?

    @Query("SELECT * FROM family_goals WHERE familyId = :familyId")
    suspend fun goals(familyId: String): List<FamilyGoalEntity>

    @Query("SELECT * FROM family_goals WHERE familyId = :familyId AND id = :id LIMIT 1")
    suspend fun findGoal(familyId: String, id: String): FamilyGoalEntity?

    @Query("SELECT * FROM family_bills WHERE familyId = :familyId")
    suspend fun bills(familyId: String): List<FamilyBillEntity>

    @Query("SELECT * FROM family_bills WHERE familyId = :familyId AND id = :id LIMIT 1")
    suspend fun findBill(familyId: String, id: String): FamilyBillEntity?
}

@Dao
interface PersonalFinanceDao {

    @Query("SELECT * FROM savings_goals WHERE ownerUid = :ownerUid ORDER BY createdAt DESC")
    fun observeSavingsGoals(ownerUid: String): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE ownerUid = :ownerUid AND id = :id LIMIT 1")
    suspend fun findSavingsGoal(ownerUid: String, id: String): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavingsGoal(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE ownerUid = :ownerUid AND id = :id")
    suspend fun deleteSavingsGoal(ownerUid: String, id: String)

    @Query("SELECT * FROM personal_loans WHERE ownerUid = :ownerUid ORDER BY startEpochDay DESC")
    fun observeLoans(ownerUid: String): Flow<List<PersonalLoanEntity>>

    @Query("SELECT * FROM personal_loans WHERE ownerUid = :ownerUid AND id = :id LIMIT 1")
    suspend fun findLoan(ownerUid: String, id: String): PersonalLoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoan(loan: PersonalLoanEntity)

    @Query("DELETE FROM personal_loans WHERE ownerUid = :ownerUid AND id = :id")
    suspend fun deleteLoan(ownerUid: String, id: String)

    @Query("SELECT * FROM attachments WHERE ownerUid = :ownerUid ORDER BY createdAt DESC")
    fun observeAttachments(ownerUid: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerUid = :ownerUid AND transactionId = :transactionId ORDER BY createdAt DESC")
    fun observeAttachmentsForTransaction(ownerUid: String, transactionId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerUid = :ownerUid AND id = :id LIMIT 1")
    suspend fun findAttachment(ownerUid: String, id: String): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE ownerUid = :ownerUid AND id = :id")
    suspend fun deleteAttachment(ownerUid: String, id: String)
}

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications WHERE ownerUid = :ownerUid ORDER BY createdAt DESC LIMIT 100")
    fun observeForOwner(ownerUid: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE ownerUid = :ownerUid AND readAt IS NULL")
    fun observeUnreadCount(ownerUid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: NotificationEntity)

    @Query("UPDATE notifications SET readAt = :readAt WHERE ownerUid = :ownerUid AND id = :id")
    suspend fun markRead(ownerUid: String, id: String, readAt: Long)
}

@Dao
interface AuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE familyId = :familyId ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(familyId: String, limit: Int): Flow<List<AuditLogEntity>>
}

@Dao
interface PendingOpDao {

    @Query("SELECT * FROM pending_ops WHERE ownerUid = :ownerUid ORDER BY createdAt ASC")
    suspend fun forOwner(ownerUid: String): List<PendingOpEntity>

    @Query("SELECT COUNT(*) FROM pending_ops WHERE ownerUid = :ownerUid")
    fun observeCount(ownerUid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(op: PendingOpEntity)

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun remove(id: String)

    @Query("UPDATE pending_ops SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: String, error: String)
}

/**
 * Cross-cutting maintenance. Kept separate so the destructive "wipe everything for this uid"
 * operation is impossible to call by accident from feature code.
 */
@Dao
interface MaintenanceDao {

    @Query("DELETE FROM transactions WHERE ownerUid = :ownerUid")
    suspend fun deleteTransactions(ownerUid: String)

    @Query("DELETE FROM accounts WHERE ownerUid = :ownerUid")
    suspend fun deleteAccounts(ownerUid: String)

    @Query("DELETE FROM recurring_rules WHERE ownerUid = :ownerUid")
    suspend fun deleteRules(ownerUid: String)

    @Query("DELETE FROM savings_goals WHERE ownerUid = :ownerUid")
    suspend fun deleteSavingsGoals(ownerUid: String)

    @Query("DELETE FROM personal_loans WHERE ownerUid = :ownerUid")
    suspend fun deleteLoans(ownerUid: String)

    @Query("DELETE FROM attachments WHERE ownerUid = :ownerUid")
    suspend fun deleteAttachments(ownerUid: String)

    @Query("DELETE FROM notifications WHERE ownerUid = :ownerUid")
    suspend fun deleteNotifications(ownerUid: String)

    @Query("DELETE FROM pending_ops WHERE ownerUid = :ownerUid")
    suspend fun deletePendingOps(ownerUid: String)

    @Query("DELETE FROM member_profiles WHERE uid = :uid")
    suspend fun deleteProfile(uid: String)

    @Transaction
    suspend fun wipePersonalData(ownerUid: String) {
        deleteTransactions(ownerUid)
        deleteAccounts(ownerUid)
        deleteRules(ownerUid)
        deleteSavingsGoals(ownerUid)
        deleteLoans(ownerUid)
        deleteAttachments(ownerUid)
        deleteNotifications(ownerUid)
        deletePendingOps(ownerUid)
        deleteProfile(ownerUid)
    }
}
