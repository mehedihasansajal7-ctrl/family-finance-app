package com.familyfinance.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Sign-In via Android's Credential Manager.
 *
 * Returns a Google ID token, which the Firebase auth repository exchanges for a Firebase session.
 * The "server client ID" must be the project's **Web** OAuth client (not the Android one) — that is
 * what makes Google issue an ID token Firebase accepts.
 */
@Singleton
class GoogleSignInManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Must be called with an **Activity** context (it shows the account picker UI). */
    suspend fun idToken(activityContext: Context): String {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context = activityContext, request = request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }

    companion object {
        /**
         * OAuth 2.0 "Web application" client for project family-finance-98333. This is public
         * client configuration (it ships in google-services.json), not a secret.
         */
        const val WEB_CLIENT_ID =
            "484447306751-hfjg12vsnlnbcfrltmhl7enmlj6mrmaq.apps.googleusercontent.com"
    }
}
