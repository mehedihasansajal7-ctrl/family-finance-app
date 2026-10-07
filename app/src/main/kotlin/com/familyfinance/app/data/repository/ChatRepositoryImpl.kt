package com.familyfinance.app.data.repository

import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.dao.ChatDao
import com.familyfinance.app.data.local.toDomain
import com.familyfinance.app.data.local.toEntity
import com.familyfinance.app.data.remote.RemoteSyncEngine
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.FamilyChatMessage
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val engine: RemoteSyncEngine,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : ChatRepository {

    override fun observeMessages(familyId: String): Flow<List<FamilyChatMessage>> = channelFlow {
        launch {
            chatDao.observeMessages(familyId).map { rows -> rows.map { it.toDomain() } }.collect { send(it) }
        }
        if (engine.isConfigured) {
            launch {
                engine.observeFamilyChat(familyId).collect { messages ->
                    chatDao.upsertAll(messages.map { it.toEntity() })
                }
            }
        }
    }

    override suspend fun sendMessage(familyId: String, text: String): Outcome<FamilyChatMessage> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission("Please sign in again."))
        if (user.familyId != familyId || user.status != MemberStatus.ACTIVE) return Outcome.Failure(AppError.permission())
        val clean = text.trim()
        if (clean.isBlank()) return Outcome.Failure(AppError.validation("Write a message first."))
        if (clean.length > MAX_MESSAGE_LENGTH) {
            return Outcome.Failure(AppError.validation("Messages can be up to $MAX_MESSAGE_LENGTH characters."))
        }

        val message = FamilyChatMessage(
            id = Ids.newId(),
            familyId = familyId,
            senderUid = user.uid,
            senderName = user.displayName.ifBlank { user.email.substringBefore('@') },
            text = clean,
            createdAt = time.nowMillis(),
        )
        val pushed = if (engine.isConfigured) engine.pushChatMessage(message) else Outcome.Success(Unit)
        if (pushed.isFailure) return Outcome.Failure(pushed.errorOrNull() ?: AppError.network("Message could not be sent."))
        chatDao.upsert(message.toEntity())
        return Outcome.Success(message)
    }

    override suspend fun deleteMessage(familyId: String, messageId: String): Outcome<Unit> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission("Please sign in again."))
        if (user.familyId != familyId || user.status != MemberStatus.ACTIVE) return Outcome.Failure(AppError.permission())
        if (engine.isConfigured) {
            val deleted = engine.deleteChatMessage(familyId, messageId)
            if (deleted.isFailure) return deleted
        }
        chatDao.delete(familyId, messageId)
        return Outcome.Success(Unit)
    }

    override suspend fun sendImageMessage(
        familyId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Outcome<FamilyChatMessage> {
        val user = session.currentUserOnce() ?: return Outcome.Failure(AppError.permission("Please sign in again."))
        if (user.familyId != familyId || user.status != MemberStatus.ACTIVE) return Outcome.Failure(AppError.permission())
        if (!engine.isConfigured) return Outcome.Failure(AppError.network("Photos require an active account."))
        if (bytes.size > MAX_IMAGE_BYTES) {
            return Outcome.Failure(AppError.validation("Photo is too large (max ${MAX_IMAGE_BYTES / 1024 / 1024} MB)."))
        }
        if (mimeType !in ALLOWED_IMAGE_TYPES) {
            return Outcome.Failure(AppError.validation("Only JPEG, PNG or WebP photos are supported."))
        }

        val id = Ids.newId()
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "photo" }
        val storagePath = "chat/$id-$safeName"

        val upload = engine.uploadChatPhoto(
            ownerUid = user.uid,
            familyId = familyId,
            storagePath = storagePath,
            bytes = bytes,
            mimeType = mimeType,
        )
        if (upload.isFailure) return Outcome.Failure(upload.errorOrNull() ?: AppError.network("Could not upload the photo."))

        val message = FamilyChatMessage(
            id = id,
            familyId = familyId,
            senderUid = user.uid,
            senderName = user.displayName.ifBlank { user.email.substringBefore('@') },
            text = "",
            createdAt = time.nowMillis(),
            attachmentUrl = upload.getOrNull(),
            attachmentContentType = mimeType,
        )
        val pushed = engine.pushChatMessage(message)
        if (pushed.isFailure) return Outcome.Failure(pushed.errorOrNull() ?: AppError.network("Message could not be sent."))
        chatDao.upsert(message.toEntity())
        return Outcome.Success(message)
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 1000
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
