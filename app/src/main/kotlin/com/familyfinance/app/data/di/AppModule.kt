package com.familyfinance.app.data.di

import android.content.Context
import androidx.room.Room
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.FamilyFinanceDatabase
import com.familyfinance.app.data.local.dao.AccountDao
import com.familyfinance.app.data.local.dao.AuditDao
import com.familyfinance.app.data.local.dao.ChatDao
import com.familyfinance.app.data.local.dao.FamilyDao
import com.familyfinance.app.data.local.dao.FamilyFinanceDao
import com.familyfinance.app.data.local.dao.MaintenanceDao
import com.familyfinance.app.data.local.dao.NotificationDao
import com.familyfinance.app.data.local.dao.PendingOpDao
import com.familyfinance.app.data.local.dao.PersonalFinanceDao
import com.familyfinance.app.data.local.dao.RecurringRuleDao
import com.familyfinance.app.data.local.dao.TransactionDao
import com.familyfinance.app.data.remote.FirebaseConfig
import com.familyfinance.app.data.remote.FirebaseRemoteSyncEngine
import com.familyfinance.app.data.remote.NoOpRemoteSyncEngine
import com.familyfinance.app.data.remote.RemoteSyncEngine
import com.familyfinance.app.data.repository.AccountRepositoryImpl
import com.familyfinance.app.data.repository.AttachmentRepositoryImpl
import com.familyfinance.app.data.repository.AuditRepositoryImpl
import com.familyfinance.app.data.repository.AuthRepositoryRouter
import com.familyfinance.app.data.repository.FamilyFinanceRepositoryImpl
import com.familyfinance.app.data.repository.FamilyRepositoryImpl
import com.familyfinance.app.data.repository.ChatRepositoryImpl
import com.familyfinance.app.data.repository.LocalDemoAuthRepository
import com.familyfinance.app.data.repository.NotificationRepositoryImpl
import com.familyfinance.app.data.repository.PersonalFinanceRepositoryImpl
import com.familyfinance.app.data.repository.ProfileRepositoryImpl
import com.familyfinance.app.data.repository.RecurringRepositoryImpl
import com.familyfinance.app.data.repository.TransactionRepositoryImpl
import com.familyfinance.app.data.seed.DemoSeeder
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.data.sync.SyncCoordinator
import com.familyfinance.app.domain.repository.AccountRepository
import com.familyfinance.app.domain.repository.AttachmentRepository
import com.familyfinance.app.domain.repository.AuditRepository
import com.familyfinance.app.domain.repository.AuthRepository
import com.familyfinance.app.domain.repository.FamilyFinanceRepository
import com.familyfinance.app.domain.repository.FamilyRepository
import com.familyfinance.app.domain.repository.ChatRepository
import com.familyfinance.app.domain.repository.NotificationRepository
import com.familyfinance.app.domain.repository.PersonalFinanceRepository
import com.familyfinance.app.domain.repository.ProfileRepository
import com.familyfinance.app.domain.repository.RecurringRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider.System

    /**
     * `FirebaseAuth.getInstance()` throws when no Firebase project is configured, so this binding is
     * only ever resolved lazily — inside the Firebase auth repository's methods, which are unreachable
     * in demo mode.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): com.google.firebase.auth.FirebaseAuth =
        com.google.firebase.auth.FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager = SessionManager(context)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FamilyFinanceDatabase =
        Room.databaseBuilder(context, FamilyFinanceDatabase::class.java, FamilyFinanceDatabase.NAME)
            // No fallbackToDestructiveMigration: silently dropping a ledger is never acceptable.
            .addMigrations(FamilyFinanceDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideAccountDao(db: FamilyFinanceDatabase): AccountDao = db.accountDao()
    @Provides fun provideTransactionDao(db: FamilyFinanceDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideRecurringRuleDao(db: FamilyFinanceDatabase): RecurringRuleDao = db.recurringRuleDao()
    @Provides fun provideFamilyDao(db: FamilyFinanceDatabase): FamilyDao = db.familyDao()
    @Provides fun provideFamilyFinanceDao(db: FamilyFinanceDatabase): FamilyFinanceDao = db.familyFinanceDao()
    @Provides fun providePersonalFinanceDao(db: FamilyFinanceDatabase): PersonalFinanceDao = db.personalFinanceDao()
    @Provides fun provideNotificationDao(db: FamilyFinanceDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideAuditDao(db: FamilyFinanceDatabase): AuditDao = db.auditDao()
    @Provides fun provideChatDao(db: FamilyFinanceDatabase): ChatDao = db.chatDao()
    @Provides fun providePendingOpDao(db: FamilyFinanceDatabase): PendingOpDao = db.pendingOpDao()
    @Provides fun provideMaintenanceDao(db: FamilyFinanceDatabase): MaintenanceDao = db.maintenanceDao()

    /**
     * Chooses the sync engine once, at graph construction.
     *
     * With a real `google-services.json` this is Firestore/Storage; otherwise every push is a no-op
     * and the app is a fully functional offline ledger (demo mode).
     */
    @Provides
    @Singleton
    fun provideRemoteSyncEngine(
        firebase: FirebaseRemoteSyncEngine,
        @ApplicationContext context: Context,
    ): RemoteSyncEngine =
        if (FirebaseConfig.isConfigured(context)) firebase else NoOpRemoteSyncEngine()

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebase: com.familyfinance.app.data.repository.FirebaseAuthRepository,
        demo: LocalDemoAuthRepository,
        @ApplicationContext context: Context,
    ): AuthRepository = AuthRepositoryRouter(firebase, demo, context)

    @Provides
    @Singleton
    fun provideSyncCoordinator(
        pendingOpDao: PendingOpDao,
        transactionDao: TransactionDao,
        accountDao: AccountDao,
        recurringRuleDao: RecurringRuleDao,
        familyFinanceDao: FamilyFinanceDao,
        personalFinanceDao: PersonalFinanceDao,
        engine: RemoteSyncEngine,
        time: TimeProvider,
    ): SyncCoordinator = SyncCoordinator(
        pendingOpDao = pendingOpDao,
        transactionDao = transactionDao,
        accountDao = accountDao,
        recurringRuleDao = recurringRuleDao,
        familyFinanceDao = familyFinanceDao,
        personalFinanceDao = personalFinanceDao,
        engine = engine,
        time = time,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    abstract fun bindRecurringRepository(impl: RecurringRepositoryImpl): RecurringRepository

    @Binds
    abstract fun bindFamilyRepository(impl: FamilyRepositoryImpl): FamilyRepository

    @Binds
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    abstract fun bindFamilyFinanceRepository(impl: FamilyFinanceRepositoryImpl): FamilyFinanceRepository

    @Binds
    abstract fun bindPersonalFinanceRepository(impl: PersonalFinanceRepositoryImpl): PersonalFinanceRepository

    @Binds
    abstract fun bindAttachmentRepository(impl: AttachmentRepositoryImpl): AttachmentRepository

    @Binds
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository

    @Binds
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
