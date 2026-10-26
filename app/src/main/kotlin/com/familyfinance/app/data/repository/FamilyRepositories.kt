package com.familyfinance.app.data.repository

import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.dao.FamilyDao
import com.familyfinance.app.data.local.dao.FamilyFinanceDao
import com.familyfinance.app.data.local.dao.PendingOpDao
import com.familyfinance.app.data.local.dao.TransactionDao
import com.familyfinance.app.data.local.entity.MemberProfileEntity
import com.familyfinance.app.data.local.toDomain
import com.familyfinance.app.data.local.toEntity
import com.familyfinance.app.data.local.toMemberView
import com.familyfinance.app.data.remote.RemoteSyncEngine
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.data.sync.SyncCoordinator
import com.familyfinance.app.data.sync.SyncOp
import com.familyfinance.app.domain.model.CategoryTotal
import com.familyfinance.app.domain.model.Family
import com.familyfinance.app.domain.model.FamilyAggregate
import com.familyfinance.app.domain.model.FamilyBill
import com.familyfinance.app.domain.model.FamilyBudget
import com.familyfinance.app.domain.model.FamilyGoal
import com.familyfinance.app.domain.model.FamilyInvitation
import com.familyfinance.app.domain.model.FamilyMemberView
import com.familyfinance.app.domain.model.MemberProfile
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.MonthlyPoint
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.domain.model.UserProfile
import com.familyfinance.app.domain.repository.FamilyFinanceRepository
import com.familyfinance.app.domain.repository.FamilyRepository
import com.familyfinance.app.domain.repository.ProfileRepository
import com.familyfinance.app.domain.usecase.FamilyAggregateCalculator
import com.familyfinance.app.domain.usecase.MemberContributionSource
import com.familyfinance.app.domain.usecase.PersonalSummaryCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class FamilyRepositoryImpl @Inject constructor(
    private val familyDao: FamilyDao,
    private val engine: RemoteSyncEngine,
    private val sync: SyncCoordinator,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : FamilyRepository {

    override fun observeFamily(familyId: String): Flow<Family?> =
        familyDao.observeFamily(familyId).map { it?.toDomain() }

    override fun observeMembers(familyId: String): Flow<List<FamilyMemberView>> =
        familyDao.observeMembers(familyId).map { rows -> rows.map { it.toMemberView() } }

    override suspend fun createFamily(name: String, ownerUid: String): Outcome<Family> {
        val user = session.currentUserOnce()
            ?: return Outcome.Failure(AppError.permission("Please sign in again."))
        if (user.uid != ownerUid) return Outcome.Failure(AppError.permission())
        if (name.isBlank()) return Outcome.Failure(AppError.validation("Family name is required."))

        val familyId = Ids.newId()
        val now = time.nowMillis()
        val family = Family(
            id = familyId,
            name = name.trim(),
            currencyCode = "BDT",
            timezone = AppTime.ZONE.id,
            ownerUid = ownerUid,
            createdAt = now,
        )
        familyDao.upsert(family.toEntity())

        // The creator becomes OWNER and gets a profile row immediately, so the member directory is
        // never empty and role checks always have something to read.
        familyDao.upsertMember(
            MemberProfileEntity(
                uid = ownerUid,
                familyId = familyId,
                fullName = user.displayName.ifBlank { user.email.substringBefore('@') },
                photoUrl = user.photoUrl,
                dateOfBirthEpochDay = null,
                mobile = null,
                email = user.email,
                relationship = "Self",
                occupation = null,
                notes = null,
                shareProfileWithFamily = true,
                roleId = Role.OWNER.id,
                statusId = MemberStatus.ACTIVE.id,
                updatedAt = now,
            )
        )

        session.saveUser(user.copy(familyId = familyId, role = Role.OWNER))
        if (engine.isConfigured) {
            engine.pushFamily(family)
            // Write the owner's membership too — the rules recognise the owner from this document, so
            // without it the creator would be locked out of the family's shared data.
            engine.pushMemberProfile(
                MemberProfile(
                    uid = ownerUid,
                    familyId = familyId,
                    fullName = user.displayName.ifBlank { user.email.substringBefore('@') },
                    photoUrl = user.photoUrl,
                    email = user.email,
                    relationship = "Self",
                    shareProfileWithFamily = true,
                    updatedAt = now,
                ),
                Role.OWNER,
                MemberStatus.ACTIVE,
            )
        }
        return Outcome.Success(family)
    }

    override suspend fun updateFamily(family: Family): Outcome<Family> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (user.familyId != family.id) return Outcome.Failure(AppError.permission())
        if (!user.role.canManageFamily) {
            return Outcome.Failure(AppError.permission("Only the owner or an admin can change family settings."))
        }
        val updated = family.copy(name = family.name.trim())
        familyDao.upsert(updated.toEntity())
        if (engine.isConfigured) engine.pushFamily(updated)
        return Outcome.Success(updated)
    }

    override suspend fun createInvitation(
        familyId: String,
        createdByUid: String,
        invitedEmail: String?,
        validForHours: Int,
    ): Outcome<FamilyInvitation> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (user.uid != createdByUid || user.familyId != familyId) return Outcome.Failure(AppError.permission())
        if (!user.role.canManageFamily) {
            return Outcome.Failure(AppError.permission("Only the owner or an admin can invite members."))
        }
        val now = time.nowMillis()
        val invitation = FamilyInvitation(
            id = Ids.newId(),
            familyId = familyId,
            code = Ids.newInviteCode(),
            createdByUid = createdByUid,
            createdAt = now,
            expiresAt = now + validForHours.coerceIn(1, 24 * 30).toLong() * 3_600_000L,
            maxUses = 1,
            invitedEmail = invitedEmail?.trim()?.takeIf { it.isNotBlank() },
        )
        familyDao.upsertInvitation(invitation.toEntity())
        return Outcome.Success(invitation)
    }

    override suspend fun revokeInvitation(invitationId: String): Outcome<Unit> {
        familyDao.revokeInvitation(invitationId, time.nowMillis())
        return Outcome.Success(Unit)
    }

    override suspend fun redeemInvitation(code: String, uid: String): Outcome<Family> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (user.uid != uid) return Outcome.Failure(AppError.permission())
        if (user.familyId != null) {
            return Outcome.Failure(AppError.conflict("You already belong to a family."))
        }
        val invitation = familyDao.findInvitationByCode(code.trim().uppercase())?.toDomain()
            ?: return Outcome.Failure(AppError.notFound("That invitation code is not valid."))
        if (!invitation.isRedeemable(time.nowMillis())) {
            return Outcome.Failure(AppError.validation("That invitation has expired or been revoked."))
        }
        val family = familyDao.find(invitation.familyId)?.toDomain()
            ?: return Outcome.Failure(AppError.notFound("The family no longer exists."))

        familyDao.upsertMember(
            MemberProfileEntity(
                uid = uid,
                familyId = family.id,
                fullName = user.displayName.ifBlank { user.email.substringBefore('@') },
                photoUrl = user.photoUrl,
                dateOfBirthEpochDay = null,
                mobile = null,
                email = user.email,
                relationship = null,
                occupation = null,
                notes = null,
                shareProfileWithFamily = true,
                roleId = Role.MEMBER.id,
                statusId = MemberStatus.ACTIVE.id,
                updatedAt = time.nowMillis(),
            )
        )
        familyDao.incrementInvitationUse(invitation.id)
        session.saveUser(user.copy(familyId = family.id, role = Role.MEMBER))
        return Outcome.Success(family)
    }

    override suspend fun joinFamily(familyId: String, uid: String): Outcome<Family> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (user.uid != uid) return Outcome.Failure(AppError.permission())
        if (!user.familyId.isNullOrBlank()) {
            return Outcome.Failure(AppError.conflict("You already belong to a family."))
        }
        val id = familyId.trim()
        if (id.isBlank()) return Outcome.Failure(AppError.validation("Paste the family ID."))

        val now = time.nowMillis()
        val profile = MemberProfile(
            uid = uid,
            familyId = id,
            fullName = user.displayName.ifBlank { user.email.substringBefore('@') },
            photoUrl = user.photoUrl,
            email = user.email,
            shareProfileWithFamily = true,
            updatedAt = now,
        )

        if (engine.isConfigured) {
            // Writing the membership first is what grants read access to the family document.
            val pushed = engine.pushMemberProfile(profile, Role.MEMBER, MemberStatus.ACTIVE)
            if (pushed.isFailure) {
                return Outcome.Failure(AppError.validation("Could not join. Check the family ID."))
            }
        }

        val remote = if (engine.isConfigured) engine.fetchFamily(id) else null
        if (engine.isConfigured && remote == null) {
            return Outcome.Failure(AppError.notFound("No family with that ID."))
        }
        val family = remote ?: Family(
            id = id,
            name = "Family",
            currencyCode = "BDT",
            timezone = AppTime.ZONE.id,
            ownerUid = "",
            createdAt = now,
        )

        familyDao.upsert(family.toEntity())
        familyDao.upsertMember(profile.toEntity(Role.MEMBER, MemberStatus.ACTIVE))
        session.saveUser(user.copy(familyId = id, role = Role.MEMBER))
        return Outcome.Success(family)
    }

    override suspend fun ensureMembership() {
        if (!engine.isConfigured) return
        val user = session.currentUserOnce() ?: return
        val familyId = user.familyId ?: return
        val member = familyDao.findMember(user.uid) ?: return
        familyDao.find(familyId)?.toDomain()?.let { engine.pushFamily(it) }
        engine.pushMemberProfile(
            member.toDomain(),
            Role.fromId(member.roleId),
            MemberStatus.fromId(member.statusId),
        )
    }

    override suspend fun removeMember(familyId: String, uid: String): Outcome<Unit> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        val target = familyDao.findMember(uid)?.toMemberView()
            ?: return Outcome.Failure(AppError.notFound("Member not found."))
        val family = familyDao.find(familyId)
            ?: return Outcome.Failure(AppError.notFound("Family not found."))

        val allowed = com.familyfinance.app.domain.security.FamilyAccessPolicy.canRemoveMember(
            viewer = user.toViewer(),
            familyId = familyId,
            targetUid = uid,
            targetRole = target.role,
            ownerUid = family.ownerUid,
        )
        if (!allowed) return Outcome.Failure(AppError.permission("You cannot remove this member."))

        familyDao.deleteMember(uid)
        if (uid == user.uid) session.updateFamily(null)
        return Outcome.Success(Unit)
    }

    override suspend fun updateRole(familyId: String, uid: String, role: Role): Outcome<Unit> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        val family = familyDao.find(familyId) ?: return Outcome.Failure(AppError.notFound("Family not found."))
        val allowed = com.familyfinance.app.domain.security.FamilyAccessPolicy.canChangeRole(
            viewer = user.toViewer(), familyId = familyId, targetUid = uid, ownerUid = family.ownerUid,
        )
        if (!allowed) return Outcome.Failure(AppError.permission("Only the family owner can change roles."))
        familyDao.updateRole(uid, role.id)
        return Outcome.Success(Unit)
    }
}

class ProfileRepositoryImpl @Inject constructor(
    private val familyDao: FamilyDao,
    private val engine: RemoteSyncEngine,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : ProfileRepository {

    override fun observeProfile(uid: String): Flow<MemberProfile?> =
        familyDao.observeMember(uid).map { it?.toDomain() }

    override suspend fun updateProfile(profile: MemberProfile): Outcome<MemberProfile> {
        if (session.currentUserOnce()?.uid != profile.uid) return Outcome.Failure(AppError.permission())
        val existing = familyDao.findMember(profile.uid)
        val updated = profile.copy(
            fullName = profile.fullName.trim().ifBlank { existing?.fullName.orEmpty() },
            updatedAt = time.nowMillis(),
        )
        familyDao.upsertMember(
            updated.toEntity(
                role = Role.fromId(existing?.roleId),
                status = MemberStatus.fromId(existing?.statusId),
            )
        )
        if (engine.isConfigured) {
            engine.pushMemberProfile(
                updated,
                Role.fromId(existing?.roleId),
                MemberStatus.fromId(existing?.statusId),
            )
        }
        return Outcome.Success(updated)
    }
}

/**
 * Family-level finance: budgets, goals, bills, and the **aggregate-only** dashboard payload.
 *
 * The aggregate is computed in one of two ways:
 *  - **Production (Firebase configured):** read the server-written aggregate document. Private
 *    transactions never leave their owner's device for this purpose.
 *  - **Local demo mode:** compute on-device. This is only sound because demo mode is a single
 *    device where every member's data already resides in the same database.
 */
class FamilyFinanceRepositoryImpl @Inject constructor(
    private val familyDao: FamilyDao,
    private val familyFinanceDao: FamilyFinanceDao,
    private val transactionDao: TransactionDao,
    private val pendingOpDao: PendingOpDao,
    private val engine: RemoteSyncEngine,
    private val sync: SyncCoordinator,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : FamilyFinanceRepository {

    override fun observeAggregate(familyId: String, month: YearMonth): Flow<FamilyAggregate> {
        if (engine.isConfigured) return engine.observeFamilyAggregate(familyId, month)
        return flow { emit(computeLocalAggregate(familyId, month)) }
    }

    override fun observeBudgets(familyId: String): Flow<List<FamilyBudget>> =
        familyFinanceDao.observeBudgets(familyId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertBudget(budget: FamilyBudget): Outcome<FamilyBudget> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (user.familyId != budget.familyId) return Outcome.Failure(AppError.permission())
        if (!user.role.canManageFamily) {
            return Outcome.Failure(AppError.permission("Only the owner or an admin can edit budgets."))
        }
        if (!budget.limit.isPositive) return Outcome.Failure(AppError.validation("Budget limit must be greater than zero."))
        val prepared = budget.copy(updatedAt = time.nowMillis())
        familyFinanceDao.upsertBudget(prepared.toEntity())
        if (engine.isConfigured) {
            val pushed = engine.pushBudget(prepared)
            if (pushed.isFailure) sync.enqueue(user.uid, SyncOp.UPSERT_BUDGET, prepared.id, prepared.familyId)
        }
        return Outcome.Success(prepared)
    }

    override suspend fun deleteBudget(familyId: String, budgetId: String): Outcome<Unit> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (!user.role.canManageFamily || user.familyId != familyId) return Outcome.Failure(AppError.permission())
        familyFinanceDao.deleteBudget(familyId, budgetId)
        if (engine.isConfigured) {
            val pushed = engine.deleteBudget(budgetId)
            if (pushed.isFailure) sync.enqueue(user.uid, SyncOp.DELETE_BUDGET, budgetId, familyId)
        }
        return Outcome.Success(Unit)
    }

    override fun observeGoals(familyId: String): Flow<List<FamilyGoal>> =
        familyFinanceDao.observeGoals(familyId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertGoal(goal: FamilyGoal): Outcome<FamilyGoal> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (user.familyId != goal.familyId) return Outcome.Failure(AppError.permission())
        if (!user.role.canManageFamily) {
            return Outcome.Failure(AppError.permission("Only the owner or an admin can edit family goals."))
        }
        if (!goal.targetAmount.isPositive) {
            return Outcome.Failure(AppError.validation("Target amount must be greater than zero."))
        }
        val prepared = goal.copy(updatedAt = time.nowMillis())
        familyFinanceDao.upsertGoal(prepared.toEntity())
        if (engine.isConfigured) {
            val pushed = engine.pushGoal(prepared)
            if (pushed.isFailure) sync.enqueue(user.uid, SyncOp.UPSERT_GOAL, prepared.id, prepared.familyId)
        }
        return Outcome.Success(prepared)
    }

    override suspend fun deleteGoal(familyId: String, goalId: String): Outcome<Unit> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (!user.role.canManageFamily || user.familyId != familyId) return Outcome.Failure(AppError.permission())
        familyFinanceDao.deleteGoal(familyId, goalId)
        if (engine.isConfigured) {
            val pushed = engine.deleteGoal(goalId)
            if (pushed.isFailure) sync.enqueue(user.uid, SyncOp.DELETE_GOAL, goalId, familyId)
        }
        return Outcome.Success(Unit)
    }

    override fun observeBills(familyId: String): Flow<List<FamilyBill>> =
        familyFinanceDao.observeBills(familyId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertBill(bill: FamilyBill): Outcome<FamilyBill> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (user.familyId != bill.familyId) return Outcome.Failure(AppError.permission())
        if (!user.role.canManageFamily) {
            return Outcome.Failure(AppError.permission("Only the owner or an admin can edit family bills."))
        }
        val prepared = bill.copy(updatedAt = time.nowMillis())
        familyFinanceDao.upsertBill(prepared.toEntity())
        if (engine.isConfigured) {
            val pushed = engine.pushBill(prepared)
            if (pushed.isFailure) sync.enqueue(user.uid, SyncOp.UPSERT_BILL, prepared.id, prepared.familyId)
        }
        return Outcome.Success(prepared)
    }

    override suspend fun deleteBill(familyId: String, billId: String): Outcome<Unit> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission())
        if (!user.role.canManageFamily || user.familyId != familyId) return Outcome.Failure(AppError.permission())
        familyFinanceDao.deleteBill(familyId, billId)
        if (engine.isConfigured) {
            val pushed = engine.deleteBill(billId)
            if (pushed.isFailure) sync.enqueue(user.uid, SyncOp.DELETE_BILL, billId, familyId)
        }
        return Outcome.Success(Unit)
    }

    /** Local demo aggregate. See the class doc for why this is only valid without a backend. */
    private suspend fun computeLocalAggregate(familyId: String, month: YearMonth): FamilyAggregate {
        val family = familyDao.find(familyId)
        val memberIds = familyDao.members(familyId).map { it.uid }
        val transactions = transactionDao.allForFamilyLocalDemo(familyId).map { it.toDomain() }
        val range = AppTime.monthRange(month)
        val inMonth = transactions.filter { it.dateEpochDay in range }

        val contributions = memberIds.map { uid ->
            val mine = inMonth.filter { it.ownerUid == uid }
            MemberContributionSource(
                memberKey = Ids.memberKey(uid, familyId),
                income = PersonalSummaryCalculator.sumByType(mine, TransactionType.INCOME),
                expense = PersonalSummaryCalculator.sumByType(mine, TransactionType.EXPENSE),
            )
        }

        val previousRange = AppTime.monthRange(month.minusMonths(1))
        val inPrevious = transactions.filter { it.dateEpochDay in previousRange }

        val trend = (5 downTo 0).map { offset ->
            val m = month.minusMonths(offset.toLong())
            val r = AppTime.monthRange(m)
            val rows = transactions.filter { it.dateEpochDay in r }
            val income = PersonalSummaryCalculator.sumByType(rows, TransactionType.INCOME)
            val expense = PersonalSummaryCalculator.sumByType(rows, TransactionType.EXPENSE)
            MonthlyPoint(m, income, expense, income - expense)
        }

        return FamilyAggregateCalculator.aggregateFromMembers(
            month = month,
            members = contributions,
            expenseByCategory = PersonalSummaryCalculator.categoryTotals(inMonth, TransactionType.EXPENSE),
            monthlyTrend = trend,
            previousMonthIncome = PersonalSummaryCalculator.sumByType(inPrevious, TransactionType.INCOME),
            previousMonthExpense = PersonalSummaryCalculator.sumByType(inPrevious, TransactionType.EXPENSE),
            // Net worth is opt-in: the tile is hidden unless the family enabled it.
            netWorth = if (family?.netWorthEnabled == true) {
                Money.sum(contributions.map { it.savings })
            } else {
                null
            },
        )
    }
}

private fun UserProfile.toViewer() = com.familyfinance.app.domain.security.Viewer(
    uid = uid,
    familyId = familyId,
    role = role,
    status = status,
)
