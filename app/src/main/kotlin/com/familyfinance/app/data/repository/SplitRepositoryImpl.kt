package com.familyfinance.app.data.repository

import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.data.local.dao.SplitDao
import com.familyfinance.app.data.local.toDomain
import com.familyfinance.app.data.local.toEntity
import com.familyfinance.app.data.remote.RemoteSyncEngine
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.FamilyExpenseSplit
import com.familyfinance.app.domain.model.FamilySettlement
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.repository.SplitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared expense splits.
 *
 * Unlike personal transactions, splits are family-shared by design: they only make sense as a group.
 * Writes are local-first and mirrored to the family document; a member can record an expense even if
 * another member paid for it.
 */
class SplitRepositoryImpl @Inject constructor(
    private val splitDao: SplitDao,
    private val engine: RemoteSyncEngine,
    private val session: SessionManager,
) : SplitRepository {

    override fun observeSplits(familyId: String): Flow<List<FamilyExpenseSplit>> = channelFlow {
        launch {
            splitDao.observeSplits(familyId).map { rows -> rows.map { it.toDomain() } }.collect { send(it) }
        }
        if (engine.isConfigured) {
            launch {
                engine.observeSplits(familyId).collect { splits ->
                    splitDao.upsertSplits(splits.map { it.toEntity() })
                }
            }
        }
    }

    override fun observeSettlements(familyId: String): Flow<List<FamilySettlement>> = channelFlow {
        launch {
            splitDao.observeSettlements(familyId).map { rows -> rows.map { it.toDomain() } }.collect { send(it) }
        }
        if (engine.isConfigured) {
            launch {
                engine.observeSettlements(familyId).collect { rows ->
                    splitDao.upsertSettlements(rows.map { it.toEntity() })
                }
            }
        }
    }

    override suspend fun addSplit(split: FamilyExpenseSplit): Outcome<FamilyExpenseSplit> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission("Please sign in again."))
        if (user.familyId != split.familyId || user.status != MemberStatus.ACTIVE) {
            return Outcome.Failure(AppError.permission())
        }
        if (split.title.isBlank()) return Outcome.Failure(AppError.validation("Give the expense a name."))
        if (!split.total.isPositive) return Outcome.Failure(AppError.validation("Amount must be greater than zero."))
        if (split.shares.isEmpty()) return Outcome.Failure(AppError.validation("Choose at least one participant."))
        val shareSum = split.shares.values.fold(0L) { acc, money -> acc + money.minor }
        if (shareSum != split.total.minor) {
            return Outcome.Failure(AppError.validation("Shares must add up to the total."))
        }

        splitDao.upsertSplit(split.toEntity())
        if (engine.isConfigured) {
            val pushed = engine.pushSplit(split)
            if (pushed.isFailure) {
                splitDao.deleteSplit(split.familyId, split.id)
                return Outcome.Failure(pushed.errorOrNull() ?: AppError.network("Could not save the split."))
            }
        }
        return Outcome.Success(split)
    }

    override suspend fun deleteSplit(familyId: String, splitId: String): Outcome<Unit> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission("Please sign in again."))
        if (user.familyId != familyId) return Outcome.Failure(AppError.permission())
        splitDao.deleteSplit(familyId, splitId)
        if (engine.isConfigured) engine.deleteSplit(familyId, splitId)
        return Outcome.Success(Unit)
    }

    override suspend fun addSettlement(settlement: FamilySettlement): Outcome<FamilySettlement> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission("Please sign in again."))
        if (user.familyId != settlement.familyId || user.status != MemberStatus.ACTIVE) {
            return Outcome.Failure(AppError.permission())
        }
        if (settlement.fromUid == settlement.toUid) return Outcome.Failure(AppError.validation("Pick two different members."))
        if (!settlement.amount.isPositive) return Outcome.Failure(AppError.validation("Amount must be greater than zero."))

        splitDao.upsertSettlement(settlement.toEntity())
        if (engine.isConfigured) {
            val pushed = engine.pushSettlement(settlement)
            if (pushed.isFailure) {
                splitDao.deleteSettlement(settlement.familyId, settlement.id)
                return Outcome.Failure(pushed.errorOrNull() ?: AppError.network("Could not record the settlement."))
            }
        }
        return Outcome.Success(settlement)
    }
}
