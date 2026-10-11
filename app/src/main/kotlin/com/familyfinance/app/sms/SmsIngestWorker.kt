package com.familyfinance.app.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.sms.SmsTransactionParser
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.SyncState
import com.familyfinance.app.domain.model.Transaction
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.domain.repository.AccountRepository
import com.familyfinance.app.domain.repository.NotificationRepository
import com.familyfinance.app.domain.repository.TransactionRepository
import com.familyfinance.app.domain.usecase.TransactionValidator
import com.familyfinance.app.reminder.ReminderNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Turns a financial SMS into a transaction.
 *
 * Only runs when the member has explicitly enabled SMS capture in Settings. If a default account is
 * set, the transaction is saved automatically; otherwise the member is notified and asked to pick an
 * account. The transaction id is derived from a hash of the SMS, so a re-delivered message upserts
 * the same row instead of creating a duplicate.
 */
@HiltWorker
class SmsIngestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val session: SessionManager,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val notifications: NotificationRepository,
    private val notifier: ReminderNotifier,
    private val time: TimeProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        if (body.isBlank()) return Result.success()
        if (!session.captureFromSms.first()) return Result.success()

        val parsed = SmsTransactionParser.parse(sender, body) ?: return Result.success()
        val user = session.currentUserOnce() ?: return Result.success()
        val familyId = user.familyId ?: return Result.success()

        val accountId = session.defaultSmsAccountId.first()
        if (accountId.isNullOrBlank()) {
            val text = "${parsed.note}. Set a default account in Settings to add it automatically."
            notifications.notify(user.uid, familyId, CATEGORY, "SMS transaction detected", text)
            notifier.post("sms_unassigned".hashCode(), "SMS transaction detected", text)
            return Result.success()
        }

        val ownedAccounts = runCatching { accounts.observeAccounts(user.uid).first() }.getOrDefault(emptyList())
        if (ownedAccounts.none { it.id == accountId }) return Result.success()

        val signature = "$sender|$body".hashCode().toUInt().toString(16)
        val draft = Transaction(
            id = "sms-$signature",
            clientId = "sms-$signature",
            ownerUid = user.uid,
            familyId = familyId,
            type = if (parsed.direction == SmsTransactionParser.Direction.INCOME) {
                TransactionType.INCOME
            } else {
                TransactionType.EXPENSE
            },
            amount = Money.ofMinor(parsed.amountMinor),
            categoryId = "other",
            accountId = accountId,
            dateEpochDay = AppTime.todayEpochDay(),
            note = parsed.note,
            createdAt = time.nowMillis(),
            updatedAt = time.nowMillis(),
            syncState = SyncState.PENDING,
        )

        val owned = ownedAccounts.map { it.id }.toSet()
        if (!TransactionValidator.validate(draft, owned).isValid) return Result.success()

        transactions.upsertTransaction(draft)
        notifications.notify(user.uid, familyId, CATEGORY, "Transaction captured", parsed.note)
        notifier.post("sms_captured".hashCode(), "Transaction captured", parsed.note)
        return Result.success()
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val CATEGORY = "sms_capture"
    }
}
