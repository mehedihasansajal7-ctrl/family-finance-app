package com.familyfinance.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.familyfinance.app.data.local.entity.FamilyChatMessageEntity
import com.familyfinance.app.data.local.entity.ChatReadEntity
import com.familyfinance.app.data.local.entity.ExpenseSplitEntity
import com.familyfinance.app.data.local.entity.SettlementEntity
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
        FamilyChatMessageEntity::class,
        ChatReadEntity::class,
        ExpenseSplitEntity::class,
        SettlementEntity::class,
    ],
    version = 5,
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
    abstract fun chatDao(): com.familyfinance.app.data.local.dao.ChatDao
    abstract fun splitDao(): com.familyfinance.app.data.local.dao.SplitDao

    companion object {
        const val NAME = "family_finance.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS family_chat_messages (" +
                        "id TEXT NOT NULL, familyId TEXT NOT NULL, senderUid TEXT NOT NULL, " +
                        "senderName TEXT NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_family_chat_messages_familyId ON family_chat_messages(familyId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_family_chat_messages_createdAt ON family_chat_messages(createdAt)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE family_chat_messages ADD COLUMN attachmentUrl TEXT")
                database.execSQL("ALTER TABLE family_chat_messages ADD COLUMN attachmentContentType TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE family_chat_messages ADD COLUMN replyToId TEXT")
                database.execSQL("ALTER TABLE family_chat_messages ADD COLUMN replyPreview TEXT")
                database.execSQL("ALTER TABLE family_chat_messages ADD COLUMN reactionsJson TEXT")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS chat_reads (" +
                        "familyId TEXT NOT NULL, uid TEXT NOT NULL, lastReadAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(familyId, uid))",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS expense_splits (" +
                        "id TEXT NOT NULL, familyId TEXT NOT NULL, title TEXT NOT NULL, " +
                        "totalMinor INTEGER NOT NULL, paidByUid TEXT NOT NULL, sharesJson TEXT NOT NULL, " +
                        "dateEpochDay INTEGER NOT NULL, createdByUid TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_expense_splits_familyId ON expense_splits(familyId)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS settlements (" +
                        "id TEXT NOT NULL, familyId TEXT NOT NULL, fromUid TEXT NOT NULL, toUid TEXT NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, dateEpochDay INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_settlements_familyId ON settlements(familyId)")
            }
        }
    }
}
