package com.familyfinance.app.domain.repository

import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.domain.model.ChatReadState
import com.familyfinance.app.domain.model.FamilyChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(familyId: String): Flow<List<FamilyChatMessage>>
    fun observeReads(familyId: String): Flow<List<ChatReadState>>

    suspend fun sendMessage(
        familyId: String,
        text: String,
        replyTo: FamilyChatMessage? = null,
    ): Outcome<FamilyChatMessage>

    suspend fun sendImageMessage(
        familyId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Outcome<FamilyChatMessage>

    suspend fun toggleReaction(familyId: String, messageId: String, emoji: String): Outcome<Unit>
    suspend fun deleteMessage(familyId: String, messageId: String): Outcome<Unit>

    /** Marks everything up to now as read by the current member (drives "seen" state). */
    suspend fun markRead(familyId: String): Outcome<Unit>
}
