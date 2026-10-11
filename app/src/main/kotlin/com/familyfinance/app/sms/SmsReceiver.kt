package com.familyfinance.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Receives incoming SMS and hands each message to [SmsIngestWorker].
 *
 * The receiver does no parsing or DB work itself (a broadcast receiver must return quickly); it only
 * forwards the sender and body to WorkManager. The receiver is protected by
 * `android.permission.BROADCAST_SMS`, so only the system can deliver to it.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()
            ?: return
        messages.groupBy { it.originatingAddress.orEmpty() }.forEach { (sender, parts) ->
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            if (body.isBlank()) return@forEach
            val request = OneTimeWorkRequestBuilder<SmsIngestWorker>()
                .setInputData(
                    workDataOf(
                        SmsIngestWorker.KEY_SENDER to sender,
                        SmsIngestWorker.KEY_BODY to body,
                    ),
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
