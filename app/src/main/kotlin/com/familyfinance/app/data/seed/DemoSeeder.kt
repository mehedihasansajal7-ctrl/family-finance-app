package com.familyfinance.app.data.seed

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.dao.AccountDao
import com.familyfinance.app.data.local.dao.FamilyDao
import com.familyfinance.app.data.local.dao.FamilyFinanceDao
import com.familyfinance.app.data.local.dao.TransactionDao
import com.familyfinance.app.data.local.entity.AccountEntity
import com.familyfinance.app.data.local.entity.FamilyBillEntity
import com.familyfinance.app.data.local.entity.FamilyBudgetEntity
import com.familyfinance.app.data.local.entity.FamilyEntity
import com.familyfinance.app.data.local.entity.FamilyGoalEntity
import com.familyfinance.app.data.local.entity.MemberProfileEntity
import com.familyfinance.app.data.local.entity.TransactionEntity
import com.familyfinance.app.domain.model.AccountType
import com.familyfinance.app.domain.model.BillStatus
import com.familyfinance.app.domain.model.ExpenseCategory
import com.familyfinance.app.domain.model.GoalStatus
import com.familyfinance.app.domain.model.IncomeCategory
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.RecurrenceFrequency
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.SyncState
import com.familyfinance.app.domain.model.TransactionType
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Demo data.
 *
 * Used **only** in local demo mode (no Firebase project configured) so the app opens onto a
 * populated, believable dashboard instead of empty states. Every value is obviously fake and the
 * amounts are round numbers. No real financial information is ever seeded.
 *
 * Seeding is idempotent: it runs once per demo family and does nothing on subsequent sign-ins.
 */
@Singleton
class DemoSeeder @Inject constructor(
    private val familyDao: FamilyDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val familyFinanceDao: FamilyFinanceDao,
    private val time: TimeProvider = TimeProvider.System,
) {

    companion object {
        const val DEMO_FAMILY_NAME = "Hasan Family (Demo)"
        private const val DEMO_MARKER = "demo_seeded"
    }

    /** Creates the demo family for [uid] if it does not exist yet. Returns the family id. */
    suspend fun seedFor(uid: String, displayName: String): String {
        val existingFamily = familyDao.findAny()
        if (existingFamily != null) return existingFamily.id

        val now = time.nowMillis()
        val familyId = Ids.newId()
        val thisMonth = AppTime.currentMonth()

        familyDao.upsert(
            FamilyEntity(
                id = familyId,
                name = DEMO_FAMILY_NAME,
                photoUrl = null,
                currencyCode = "BDT",
                timezone = AppTime.ZONE.id,
                ownerUid = uid,
                createdAt = now,
                netWorthEnabled = true,
            )
        )

        // A believable multi-member household. The signed-in user is the owner; the others exist so
        // the aggregate dashboard has more than one contributor.
        val members = listOf(
            MemberRow(uid, displayName.ifBlank { "Mehedi" }, "Self", "Service", Role.OWNER),
            MemberRow("demo-father-uid", "Abdul Hasan", "Father", "Retired government officer", Role.MEMBER),
            MemberRow("demo-mother-uid", "Rahima Hasan", "Mother", "Homemaker", Role.MEMBER),
            MemberRow("demo-sister-uid", "Nusrat Hasan", "Sister", "University student", Role.MEMBER),
        )
        for (member in members) {
            familyDao.upsertMember(
                MemberProfileEntity(
                    uid = member.uid,
                    familyId = familyId,
                    fullName = member.name,
                    photoUrl = null,
                    dateOfBirthEpochDay = null,
                    mobile = null,
                    email = if (member.uid == uid) null else "${member.uid}@example.com",
                    relationship = member.relationship,
                    occupation = member.occupation,
                    notes = null,
                    shareProfileWithFamily = true,
                    roleId = member.role.id,
                    statusId = MemberStatus.ACTIVE.id,
                    updatedAt = now,
                )
            )
        }

        seedAccounts(familyId, uid, now)
        seedTransactions(familyId, uid, thisMonth, now)
        seedFamilyFinance(familyId, uid, thisMonth, now)
        return familyId
    }

    private suspend fun seedAccounts(familyId: String, uid: String, now: Long) {
        val accounts = listOf(
            AccountSeed("DBBL Savings", AccountType.BANK, "Dutch-Bangla Bank", "1234567890123456", 185_000),
            AccountSeed("bKash Personal", AccountType.BKASH, "bKash", "01712345678", 12_400),
            AccountSeed("Nagad", AccountType.NAGAD, "Nagad", "01812345678", 4_250),
            AccountSeed("City Bank Credit Card", AccountType.CREDIT_CARD, "City Bank", "4532015112830366", 0),
            AccountSeed("Cash in hand", AccountType.CASH, null, null, 8_600),
        )
        for (seed in accounts) {
            accountDao.upsert(
                AccountEntity(
                    id = Ids.newId(),
                    ownerUid = uid,
                    familyId = familyId,
                    name = seed.name,
                    typeId = seed.type.id,
                    institution = seed.institution,
                    identifier = seed.identifier,
                    last4 = seed.identifier?.takeLast(4),
                    openingBalanceMinor = Money.ofBdt(seed.openingTaka).minor,
                    currencyCode = "BDT",
                    isActive = true,
                    notes = null,
                    createdAt = now,
                    updatedAt = now,
                    syncStateId = SyncState.SYNCED.id,
                )
            )
        }
    }

    private suspend fun seedTransactions(familyId: String, uid: String, month: YearMonth, now: Long) {
        val accounts = accountDao.activeForOwner(uid)
        val bank = accounts.firstOrNull { it.typeId == AccountType.BANK.id }?.id
        val wallet = accounts.firstOrNull { it.typeId == AccountType.BKASH.id }?.id
        val cash = accounts.firstOrNull { it.typeId == AccountType.CASH.id }?.id

        val rows = mutableListOf<TransactionEntity>()

        fun add(
            owner: String,
            type: TransactionType,
            amountTaka: Long,
            categoryId: String?,
            accountId: String?,
            day: Int,
            note: String,
        ) {
            val date = month.atDay(day.coerceIn(1, month.lengthOfMonth()))
            rows += TransactionEntity(
                id = Ids.newId(),
                clientId = Ids.newClientId(),
                ownerUid = owner,
                familyId = familyId,
                typeId = type.id,
                amountMinor = Money.ofBdt(amountTaka).minor,
                categoryId = categoryId,
                subCategoryId = null,
                accountId = accountId,
                transferFromAccountId = null,
                transferToAccountId = null,
                dateEpochDay = date.toEpochDay(),
                note = note,
                attachmentIds = emptyList(),
                recurringRuleId = null,
                recurrenceOccurrenceEpochDay = null,
                createdAt = now,
                updatedAt = now,
                syncStateId = SyncState.SYNCED.id,
            )
        }

        // Owner
        add(uid, TransactionType.INCOME, 72_000, IncomeCategory.SALARY.id, bank, 1, "Monthly salary")
        add(uid, TransactionType.INCOME, 14_500, IncomeCategory.FREELANCING.id, wallet, 12, "Freelance project")
        add(uid, TransactionType.EXPENSE, 18_000, ExpenseCategory.HOUSING_RENT.id, bank, 2, "House rent")
        add(uid, TransactionType.EXPENSE, 9_400, ExpenseCategory.FOOD_GROCERY.id, cash, 5, "Monthly groceries")
        add(uid, TransactionType.EXPENSE, 3_250, ExpenseCategory.ELECTRICITY.id, bank, 7, "Electricity bill")
        add(uid, TransactionType.EXPENSE, 1_200, ExpenseCategory.INTERNET.id, bank, 8, "Broadband")
        add(uid, TransactionType.EXPENSE, 2_800, ExpenseCategory.TRANSPORT.id, cash, 11, "Fuel and CNG")
        add(uid, TransactionType.EXPENSE, 5_600, ExpenseCategory.EDUCATION.id, bank, 14, "Tuition fees")
        add(uid, TransactionType.EXPENSE, 2_100, ExpenseCategory.MEDICAL.id, cash, 17, "Pharmacy")
        add(uid, TransactionType.EXPENSE, 1_500, ExpenseCategory.DONATION_CHARITY.id, cash, 20, "Sadaqah")

        // Other members contribute to the family aggregate.
        add("demo-father-uid", TransactionType.INCOME, 32_000, IncomeCategory.OTHER.id, bank, 3, "Pension")
        add("demo-father-uid", TransactionType.EXPENSE, 4_200, ExpenseCategory.GAS.id, cash, 6, "Gas bill")
        add("demo-mother-uid", TransactionType.EXPENSE, 6_800, ExpenseCategory.FOOD_GROCERY.id, cash, 9, "Kitchen expenses")
        add("demo-mother-uid", TransactionType.EXPENSE, 2_400, ExpenseCategory.CLOTHING.id, cash, 15, "Clothing")
        add("demo-sister-uid", TransactionType.INCOME, 8_000, IncomeCategory.OTHER.id, wallet, 10, "Tuition income")
        add("demo-sister-uid", TransactionType.EXPENSE, 1_800, ExpenseCategory.EDUCATION.id, wallet, 18, "Books")

        transactionDao.upsertAll(rows)
    }

    private suspend fun seedFamilyFinance(familyId: String, uid: String, month: YearMonth, now: Long) {
        val range = AppTime.monthRange(month)
        val budgets = listOf(
            Triple("Food & Grocery", ExpenseCategory.FOOD_GROCERY.id, 20_000L),
            Triple("Education", ExpenseCategory.EDUCATION.id, 10_000L),
            Triple("Medical", ExpenseCategory.MEDICAL.id, 5_000L),
            Triple("Transport", ExpenseCategory.TRANSPORT.id, 8_000L),
        )
        for ((name, categoryId, limit) in budgets) {
            familyFinanceDao.upsertBudget(
                FamilyBudgetEntity(
                    id = Ids.newId(),
                    familyId = familyId,
                    name = name,
                    categoryId = categoryId,
                    periodEpochDayStart = range.first,
                    periodEpochDayEnd = range.last,
                    limitMinor = Money.ofBdt(limit).minor,
                    createdByUid = uid,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        familyFinanceDao.upsertGoal(
            FamilyGoalEntity(
                id = Ids.newId(),
                familyId = familyId,
                name = "Emergency Fund",
                categoryId = "emergency_fund",
                targetAmountMinor = Money.ofBdt(500_000).minor,
                currentAmountMinor = Money.ofBdt(400_000).minor,
                targetEpochDay = month.plusMonths(8).atDay(1).toEpochDay(),
                description = "Six months of household expenses.",
                statusId = GoalStatus.ACTIVE.id,
                createdByUid = uid,
                createdAt = now,
                updatedAt = now,
            )
        )
        familyFinanceDao.upsertGoal(
            FamilyGoalEntity(
                id = Ids.newId(),
                familyId = familyId,
                name = "Hajj Fund",
                categoryId = "hajj_umrah",
                targetAmountMinor = Money.ofBdt(1_200_000).minor,
                currentAmountMinor = Money.ofBdt(310_000).minor,
                targetEpochDay = month.plusMonths(24).atDay(1).toEpochDay(),
                description = "Hajj for both parents.",
                statusId = GoalStatus.ACTIVE.id,
                createdByUid = uid,
                createdAt = now,
                updatedAt = now,
            )
        )

        val bills = listOf(
            Triple("Electricity", ExpenseCategory.ELECTRICITY.id, 3_400L),
            Triple("Internet", ExpenseCategory.INTERNET.id, 1_200L),
            Triple("House Rent", ExpenseCategory.HOUSING_RENT.id, 18_000L),
            Triple("School Fees", ExpenseCategory.EDUCATION.id, 5_600L),
        )
        bills.forEachIndexed { index, (name, categoryId, amount) ->
            val due = month.atDay(minOf(25 + index * 2, month.lengthOfMonth()))
            familyFinanceDao.upsertBill(
                FamilyBillEntity(
                    id = Ids.newId(),
                    familyId = familyId,
                    name = name,
                    categoryId = categoryId,
                    expectedAmountMinor = Money.ofBdt(amount).minor,
                    dueEpochDay = due.toEpochDay(),
                    frequencyId = RecurrenceFrequency.MONTHLY.id,
                    statusId = BillStatus.PENDING.id,
                    reminderEnabled = true,
                    reminderDaysBefore = 3,
                    notes = null,
                    createdByUid = uid,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    private data class MemberRow(
        val uid: String,
        val name: String,
        val relationship: String,
        val occupation: String,
        val role: Role,
    )

    private data class AccountSeed(
        val name: String,
        val type: AccountType,
        val institution: String?,
        val identifier: String?,
        val openingTaka: Long,
    )
}
