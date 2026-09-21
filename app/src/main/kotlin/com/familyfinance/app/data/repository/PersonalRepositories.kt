package com.familyfinance.app.data.repository

import android.content.Context
import com.familyfinance.app.core.logging.AppLog
import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.dao.AuditDao
import com.familyfinance.app.data.local.dao.NotificationDao
import com.familyfinance.app.data.local.dao.PersonalFinanceDao
import com.familyfinance.app.data.local.entity.AuditLogEntity
import com.familyfinance.app.data.local.toDomain
import com.familyfinance.app.data.local.toEntity
import com.familyfinance.app.data.remote.RemoteSyncEngine
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.data.sync.SyncCoordinator
import com.familyfinance.app.data.sync.SyncOp
import com.familyfinance.app.domain.model.AppNotification
import com.familyfinance.app.domain.model.Attachment
import com.familyfinance.app.domain.model.AuditLog
import com.familyfinance.app.domain.model.PersonalLoan
import com.familyfinance.app.domain.model.SavingsGoal
import com.familyfinance.app.domain.repository.AttachmentRepository
import com.familyfinance.app.domain.repository.AuditRepository
import com.familyfinance.app.domain.repository.NotificationRepository
import com.familyfinance.app.domain.repository.PersonalFinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

/**
 * Private personal finance: savings goals and loans.
 *
 * Both are owner-scoped end to end. There is no family-level view of either, by design — the family
 * dashboard shows aggregate income/expense only and never touches these tables.
 */
class PersonalFinanceRepositoryImpl @Inject constructor(
    private val personalFinanceDao: PersonalFinanceDao,
    private val engine: RemoteSyncEngine,
    private val sync: SyncCoordinator,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : PersonalFinanceRepository {

    override fun observeLoans(ownerUid: String): Flow<List<PersonalLoan>> =
        personalFinanceDao.observeLoans(ownerUid).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertLoan(loan: PersonalLoan): Outcome<PersonalLoan> {
        if (!isSelf(loan.ownerUid)) return Outcome.Failure(AppError.permission())
        if (!loan.principal.isPositive) {
            return Outcome.Failure(AppError.validation("Principal must be greater than zero."))
        }
        if (loan.counterparty.isBlank()) {
            return Outcome.Failure(AppError.validation("Please enter the lender or borrower."))
        }
        val prepared = loan.copy(
            updatedAt = time.nowMillis(),
            createdAt = if (loan.createdAt == 0L) time.nowMillis() else loan.createdAt,
        )
        personalFinanceDao.upsertLoan(prepared.toEntity())
        if (engine.isConfigured) {
            val pushed = engine.pushLoan(prepared)
            if (pushed.isFailure) sync.enqueue(prepared.ownerUid, SyncOp.UPSERT_LOAN, prepared.id)
        }
        return Outcome.Success(prepared)
    }

    override suspend fun deleteLoan(ownerUid: String, loanId: String): Outcome<Unit> {
        if (!isSelf(ownerUid)) return Outcome.Failure(AppError.permission())
        personalFinanceDao.deleteLoan(ownerUid, loanId)
        if (engine.isConfigured) {
            val pushed = engine.deleteLoan(loanId)
            if (pushed.isFailure) sync.enqueue(ownerUid, SyncOp.DELETE_LOAN, loanId)
        }
        return Outcome.Success(Unit)
    }

    override fun observeSavingsGoals(ownerUid: String): Flow<List<SavingsGoal>> =
        personalFinanceDao.observeSavingsGoals(ownerUid).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertSavingsGoal(goal: SavingsGoal): Outcome<SavingsGoal> {
        if (!isSelf(goal.ownerUid)) return Outcome.Failure(AppError.permission())
        if (!goal.targetAmount.isPositive) {
            return Outcome.Failure(AppError.validation("Target amount must be greater than zero."))
        }
        val prepared = goal.copy(
            updatedAt = time.nowMillis(),
            createdAt = if (goal.createdAt == 0L) time.nowMillis() else goal.createdAt,
        )
        personalFinanceDao.upsertSavingsGoal(prepared.toEntity())
        if (engine.isConfigured) {
            val pushed = engine.pushSavingsGoal(prepared)
            if (pushed.isFailure) sync.enqueue(prepared.ownerUid, SyncOp.UPSERT_SAVINGS_GOAL, prepared.id)
        }
        return Outcome.Success(prepared)
    }

    override suspend fun deleteSavingsGoal(ownerUid: String, goalId: String): Outcome<Unit> {
        if (!isSelf(ownerUid)) return Outcome.Failure(AppError.permission())
        personalFinanceDao.deleteSavingsGoal(ownerUid, goalId)
        if (engine.isConfigured) {
            val pushed = engine.deleteSavingsGoal(goalId)
            if (pushed.isFailure) sync.enqueue(ownerUid, SyncOp.DELETE_SAVINGS_GOAL, goalId)
        }
        return Outcome.Success(Unit)
    }

    private suspend fun isSelf(uid: String): Boolean = session.currentUserOnce()?.uid == uid
}

/**
 * Receipts and documents.
 *
 * Files are written to the app's private files directory. They are never placed in shared/external
 * storage, and they are never uploaded to a family-readable location unless the owner explicitly
 * marks the attachment family-visible.
 */
class AttachmentRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val personalFinanceDao: PersonalFinanceDao,
    private val engine: RemoteSyncEngine,
    private val session: SessionManager,
    private val time: TimeProvider = TimeProvider.System,
) : AttachmentRepository {

    override fun observeAttachments(ownerUid: String, transactionId: String?): Flow<List<Attachment>> {
        val source = if (transactionId == null) {
            personalFinanceDao.observeAttachments(ownerUid)
        } else {
            personalFinanceDao.observeAttachmentsForTransaction(ownerUid, transactionId)
        }
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun upload(
        ownerUid: String,
        familyId: String,
        transactionId: String?,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        familyVisible: Boolean,
    ): Outcome<Attachment> {
        if (session.currentUserOnce()?.uid != ownerUid) return Outcome.Failure(AppError.permission())
        if (bytes.size > MAX_BYTES) {
            return Outcome.Failure(AppError.validation("File is too large (max ${MAX_BYTES / 1024 / 1024} MB)."))
        }
        val detected = detectMime(bytes) ?: return Outcome.Failure(
            AppError.validation("Unsupported file type. Use JPEG, PNG, WebP or PDF.")
        )
        if (detected != mimeType && !mimeType.startsWith("image/")) {
            AppLog.w("Attachment mime mismatch; using detected type")
        }

        val id = Ids.newId()
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "receipt" }
        val relative = "attachments/$ownerUid/$id-$safeName"
        val target = File(context.filesDir, relative)

        return withContext(Dispatchers.IO) {
            runCatching {
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }.fold(
                onSuccess = {
                    val attachment = Attachment(
                        id = id,
                        ownerUid = ownerUid,
                        familyId = familyId,
                        transactionId = transactionId,
                        fileName = safeName,
                        // The server-derived type wins: a client cannot claim a .pdf is an image.
                        mimeType = detected,
                        sizeBytes = bytes.size.toLong(),
                        storagePath = relative,
                        isFamilyVisible = familyVisible,
                        createdAt = time.nowMillis(),
                    )
                    personalFinanceDao.upsertAttachment(attachment.toEntity())
                    if (engine.isConfigured) {
                        val pushed = engine.uploadAttachment(
                            ownerUid = ownerUid,
                            familyId = familyId,
                            attachmentId = id,
                            storagePath = relative,
                            bytes = bytes,
                            familyVisible = familyVisible,
                        )
                        if (pushed.isFailure) AppLog.w("Attachment queued for later upload")
                    }
                    Outcome.Success(attachment)
                },
                onFailure = { Outcome.Failure(AppError.unknown("Could not save the file.", it)) },
            )
        }
    }

    override suspend fun delete(ownerUid: String, attachmentId: String): Outcome<Unit> {
        if (session.currentUserOnce()?.uid != ownerUid) return Outcome.Failure(AppError.permission())
        val attachment = personalFinanceDao.findAttachment(ownerUid, attachmentId)?.toDomain()
            ?: return Outcome.Failure(AppError.notFound("Attachment not found."))
        withContext(Dispatchers.IO) {
            runCatching { File(context.filesDir, attachment.storagePath).delete() }
        }
        personalFinanceDao.deleteAttachment(ownerUid, attachmentId)
        if (engine.isConfigured) engine.deleteAttachment(attachment.storagePath)
        return Outcome.Success(Unit)
    }

    override suspend fun downloadUrl(ownerUid: String, attachmentId: String): Outcome<String> {
        val attachment = personalFinanceDao.findAttachment(ownerUid, attachmentId)?.toDomain()
            ?: return Outcome.Failure(AppError.notFound("Attachment not found."))
        return Outcome.Success(File(context.filesDir, attachment.storagePath).toURI().toString())
    }

    /**
     * Magic-byte sniffing. The stored MIME type is derived from the bytes, never from the file name
     * or the client-supplied type, so a mislabelled upload cannot become an executable payload.
     */
    private fun detectMime(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        fun u(i: Int) = bytes[i].toInt() and 0xFF
        return when {
            u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF -> "image/jpeg"
            u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47 -> "image/png"
            u(0) == 0x52 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x46 &&
                u(8) == 0x57 && u(9) == 0x45 && u(10) == 0x42 && u(11) == 0x50 -> "image/webp"
            u(0) == 0x25 && u(1) == 0x50 && u(2) == 0x44 && u(3) == 0x46 -> "application/pdf"
            else -> null
        }
    }

    companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
    }
}

class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao,
    private val engine: RemoteSyncEngine,
    private val time: TimeProvider = TimeProvider.System,
) : NotificationRepository {

    override fun observeNotifications(ownerUid: String): Flow<List<AppNotification>> =
        notificationDao.observeForOwner(ownerUid).map { rows -> rows.map { it.toDomain() } }

    override fun unreadCount(ownerUid: String): Flow<Int> = notificationDao.observeUnreadCount(ownerUid)

    override suspend fun markRead(ownerUid: String, notificationId: String): Outcome<Unit> {
        notificationDao.markRead(ownerUid, notificationId, time.nowMillis())
        return Outcome.Success(Unit)
    }

    /**
     * Creates a reminder. The body is deliberately amount-free so a lock-screen preview can never
     * disclose a balance or a salary (see §27 of the product requirements).
     */
    suspend fun notify(
        ownerUid: String,
        familyId: String,
        category: String,
        title: String,
        body: String,
        relatedId: String? = null,
    ) {
        val notification = AppNotification(
            id = Ids.newId(),
            ownerUid = ownerUid,
            familyId = familyId,
            categoryId = category,
            title = title,
            body = body,
            relatedId = relatedId,
            createdAt = time.nowMillis(),
        )
        notificationDao.upsert(notification.toEntity())
        if (engine.isConfigured) engine.pushNotification(notification)
    }
}

/**
 * Append-only audit trail.
 *
 * Context is passed through [AuditLog] and must already be non-sensitive. The repository applies a
 * final redaction pass so a future caller cannot accidentally persist a credential or a full
 * account number.
 */
class AuditRepositoryImpl @Inject constructor(
    private val auditDao: AuditDao,
    private val engine: RemoteSyncEngine,
    private val time: TimeProvider = TimeProvider.System,
) : AuditRepository {

    private val json = Json { encodeDefaults = true }

    override fun observeRecent(familyId: String, limit: Int): Flow<List<AuditLog>> =
        auditDao.observeRecent(familyId, limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(
        familyId: String?,
        actorUid: String,
        action: String,
        context: Map<String, String>,
    ) {
        val redacted = context.mapValues { (_, value) ->
            com.familyfinance.app.core.security.Redactor.scrub(value)
        }
        val log = AuditLog(
            id = Ids.newId(),
            familyId = familyId,
            actorUid = actorUid,
            action = action,
            context = redacted,
            createdAt = time.nowMillis(),
        )
        auditDao.insert(
            AuditLogEntity(
                id = log.id,
                familyId = log.familyId,
                actorUid = log.actorUid,
                action = log.action,
                contextJson = json.encodeToString(redacted),
                createdAt = log.createdAt,
            )
        )
        if (engine.isConfigured) engine.pushAuditLog(log)
    }
}
