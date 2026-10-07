package com.familyfinance.app.domain.repository

import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.domain.model.FamilyChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(familyId: String): Flow<List<FamilyChatMessage>>
    suspend fun sendMessage(familyId: String, text: String): Outcome<FamilyChatMessage>
    suspend fun sendImageMessage(
        familyId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Outcome<FamilyChatMessage>
    suspend fun deleteMessage(familyId: String, messageId: String): Outcome<Unit>
}
