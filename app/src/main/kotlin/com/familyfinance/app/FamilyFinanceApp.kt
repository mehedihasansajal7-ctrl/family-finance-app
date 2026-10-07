package com.familyfinance.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.familyfinance.app.core.logging.AppLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FamilyFinanceApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLog.install(isDebug = BuildConfig.DEBUG)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Bill due dates, budget alerts and recurring entries."
        }

        val security = NotificationChannel(
            CHANNEL_SECURITY,
            "Security",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Sign-in and account-security events."
        }

        manager.createNotificationChannels(listOf(reminders, security))
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_SECURITY = "security"
    }
}
