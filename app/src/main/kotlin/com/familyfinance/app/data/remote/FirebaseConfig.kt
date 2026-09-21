package com.familyfinance.app.data.remote

import android.content.Context
import com.familyfinance.app.R

/**
 * Detects whether a real Firebase project has been wired up.
 *
 * The repository ships with a *placeholder* `google-services.json` so the project builds and runs
 * out of the box. When the placeholder is still in place the app runs in **local demo mode**: every
 * feature works against the on-device database with seeded data, but nothing is synced. Dropping in
 * the real `google-services.json` flips the app to the production Firebase backend with no code
 * change.
 */
object FirebaseConfig {

    private const val PLACEHOLDER_PROJECT = "family-finance-placeholder"

    fun isConfigured(context: Context): Boolean {
        val projectId = runCatching { context.getString(R.string.project_id) }.getOrNull()
            ?: return false
        val appId = runCatching { context.getString(R.string.google_app_id) }.getOrNull().orEmpty()
        if (projectId.isBlank() || appId.isBlank()) return false
        if (projectId == PLACEHOLDER_PROJECT) return false
        if (projectId.startsWith("YOUR_")) return false
        if (appId.contains("000000000000")) return false
        return true
    }

    fun projectId(context: Context): String? =
        runCatching { context.getString(R.string.project_id) }.getOrNull()
}
