package com.familyfinance.app.domain.repository

import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.domain.model.FamilyExpenseSplit
import com.familyfinance.app.domain.model.FamilySettlement
import kotlinx.coroutines.flow.Flow

/** Shared family expense splitting and settlement. */
interface SplitRepository {
    fun observeSplits(familyId: String): Flow<List<FamilyExpenseSplit>>
    fun observeSettlements(familyId: String): Flow<List<FamilySettlement>>

    suspend fun addSplit(split: FamilyExpenseSplit): Outcome<FamilyExpenseSplit>
    suspend fun deleteSplit(familyId: String, splitId: String): Outcome<Unit>
    suspend fun addSettlement(settlement: FamilySettlement): Outcome<FamilySettlement>
}
