package com.familyfinance.app.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Biometric app lock.
 *
 * This is a **convenience lock, not the security boundary**. It exists so that someone holding an
 * unlocked phone cannot browse the user's finances. The actual access control is server-side
 * (Firebase Auth + Firestore rules); a bypassed biometric prompt gains an attacker nothing beyond
 * what an already-unlocked device with a live session would give them.
 *
 * When the device has no enrolled biometric or no hardware, [canAuthenticate] returns false and the
 * app lock is simply unavailable rather than blocking the user out of their own data.
 */
@Singleton
class BiometricLockManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {

    fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(context)
        return when (
            manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /** Shows the prompt. Returns true when the user authenticated. */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Family Finance",
        subtitle: String = "Your financial data is private to this device.",
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }

                override fun onAuthenticationFailed() {
                    // A single mismatch is not terminal — the prompt stays up and the user retries.
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
