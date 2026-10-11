package com.familyfinance.app.notifications

import android.content.Context
import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.data.remote.FirebaseConfig
import com.familyfinance.app.data.remote.RemoteSyncEngine
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers this device's FCM token under the signed-in user so the chat Cloud Function can push to
 * it. A no-op in demo mode (no Firebase project configured).
 */
@Singleton
class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: RemoteSyncEngine,
) {

    suspend fun register(): Outcome<Unit> {
        if (!FirebaseConfig.isConfigured(context)) return Outcome.Success(Unit)
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
            ?: return Outcome.Failure(AppError.network("Could not obtain a push token."))
        return engine.registerDeviceToken(token)
    }
}
