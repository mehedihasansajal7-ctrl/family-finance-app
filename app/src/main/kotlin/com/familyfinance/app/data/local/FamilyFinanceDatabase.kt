package com.familyfinance.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.familyfinance.app.data.local.dao.AccountDao
import com.familyfinance.app.data.local.dao.AuditDao
import com.familyfinance.app.data.local.dao.FamilyDao
import com.familyfinance.app.data.local.dao.FamilyFinanceDao
import com.familyfinance.app.data.local.dao.MaintenanceDao
import com.familyfinance.app.data.local.dao.NotificationDao
import com.familyfinance.app.data.local.dao.PendingOpDao
import com.familyfinance.app.data.local.dao.PersonalFinanceDao
import com.familyfinance.app.data.local.dao.RecurringRuleDao
import com.familyfinance.app.data.local.dao.TransactionDao
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

/**
 * The on-device database.
 *
 * It serves three purposes: the offline-first write path, the read cache, and the store behind
 * **local demo mode** (used when no Firebase project is configured, so the app is runnable and
 * testable without any external account).
 *
 * No destructive migration fallback is used: a schema change must ship a real migration, because
 * silently dropping a ledger is not an acceptable failure mode for a finance app.
 */
@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        FamilyEntity::class,
        MemberProfileEntity::class,
        InvitationEntity::class,
        FamilyBudgetEntity::class,
        FamilyGoalEntity::class,
        FamilyBillEntity::class,
        SavingsGoalEntity::class,
        PersonalLoanEntity::class,
        AttachmentEntity::class,
        NotificationEntity::class,
        AuditLogEntity::class,
        PendingOpEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FamilyFinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun familyDao(): FamilyDao
    abstract fun familyFinanceDao(): FamilyFinanceDao
    abstract fun personalFinanceDao(): PersonalFinanceDao
    abstract fun notificationDao(): NotificationDao
    abstract fun auditDao(): AuditDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val NAME = "family_finance.db"
    }
}
