package com.familyfinance.app.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.familyfinance.app.core.logging.AppLog
import com.familyfinance.app.core.result.AppError
import com.familyfinance.app.core.result.Outcome
import com.familyfinance.app.core.util.Ids
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.dao.FamilyDao
import com.familyfinance.app.data.local.dao.MaintenanceDao
import com.familyfinance.app.data.local.toDomain
import com.familyfinance.app.data.remote.FirebaseConfig
import com.familyfinance.app.data.seed.DemoSeeder
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.UserProfile
import com.familyfinance.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Chooses the authentication implementation.
 *
 * With a real `google-services.json` the app authenticates against Firebase. Without one it falls
 * back to [LocalDemoAuthRepository] so the app is fully runnable and testable offline. The fallback
 * is never a silent downgrade: the UI shows a persistent "Demo mode" banner.
 */
class AuthRepositoryRouter(
    private val firebase: FirebaseAuthRepository,
    private val demo: LocalDemoAuthRepository,
    context: Context,
) : AuthRepository {

    val isFirebaseConfigured: Boolean = FirebaseConfig.isConfigured(context)

    private val delegate: AuthRepository get() = if (isFirebaseConfigured) firebase else demo

    override val currentUser: Flow<UserProfile?> get() = delegate.currentUser
    override suspend fun signIn(email: String, password: String) = delegate.signIn(email, password)
    override suspend fun signUp(email: String, password: String, displayName: String) =
        delegate.signUp(email, password, displayName)
    override suspend fun signInWithGoogle(idToken: String) = delegate.signInWithGoogle(idToken)
    override suspend fun sendPasswordReset(email: String) = delegate.sendPasswordReset(email)
    override suspend fun signOut() = delegate.signOut()
    override suspend fun deleteAccount() = delegate.deleteAccount()
}

/**
 * Production authentication via Firebase Auth (email/password and Google).
 *
 * Identity comes only from `FirebaseAuth.currentUser.uid`. Nothing in this class accepts a uid from
 * the caller, so a client cannot assert who it is.
 */
class FirebaseAuthRepository @Inject constructor(
    /**
     * Injected lazily: `FirebaseAuth.getInstance()` throws when no Firebase project is configured,
     * and this class is still constructed in demo mode so the router has something to delegate to.
     */
    private val authProvider: dagger.Lazy<FirebaseAuth>,
    private val session: SessionManager,
    private val familyDao: FamilyDao,
    private val maintenanceDao: MaintenanceDao,
    private val time: TimeProvider = TimeProvider.System,
) : AuthRepository {

    private val auth: FirebaseAuth get() = authProvider.get()

    /**
     * The session store is the single source of truth for "who is signed in", so the UI observes
     * the same flow in demo and production mode.
     */
    override val currentUser: Flow<UserProfile?> = session.currentUser

    override suspend fun signIn(email: String, password: String): Outcome<UserProfile> = runAuth {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        persist(result.user)
    }

    override suspend fun signUp(email: String, password: String, displayName: String): Outcome<UserProfile> = runAuth {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw IllegalArgumentException("Password must be at least $MIN_PASSWORD_LENGTH characters.")
        }
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: throw IllegalStateException("Sign-up did not return a user.")
        val name = displayName.trim().ifBlank { email.substringBefore('@') }
        user.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        persist(user)
    }

    override suspend fun signInWithGoogle(idToken: String): Outcome<UserProfile> = runAuth {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        persist(result.user)
    }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> = runAuth {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override suspend fun signOut(): Outcome<Unit> = runAuth {
        auth.signOut()
        session.clearIdentity()
    }

    override suspend fun deleteAccount(): Outcome<Unit> = runAuth {
        val uid = auth.currentUser?.uid
        if (uid != null) maintenanceDao.wipePersonalData(uid)
        auth.currentUser?.delete()?.await()
        session.clearIdentity()
    }

    private suspend fun persist(user: FirebaseUser?): UserProfile {
        val uid = user?.uid ?: throw IllegalStateException("No authenticated user.")
        val existing = familyDao.findMember(uid)
        val profile = UserProfile(
            uid = uid,
            email = user.email.orEmpty(),
            displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@').orEmpty(),
            photoUrl = user.photoUrl?.toString(),
            familyId = existing?.familyId,
            role = Role.fromId(existing?.roleId),
            status = MemberStatus.fromId(existing?.statusId),
            createdAt = existing?.updatedAt ?: time.nowMillis(),
            updatedAt = time.nowMillis(),
        )
        session.saveUser(profile)
        return profile
    }

    private suspend inline fun <T> runAuth(crossinline block: suspend () -> T): Outcome<T> = try {        Outcome.Success(block())
    } catch (error: Exception) {
        AppLog.e("Auth operation failed", error)
        Outcome.Failure(mapError(error))
    }

    private fun mapError(error: Exception): AppError {
        val message = error.message.orEmpty()
        return when {
            message.contains("password is invalid", true) ||
                message.contains("credential is incorrect", true) ||
                message.contains("no user record", true) ->
                AppError.validation("Incorrect email or password.")

            message.contains("network", true) ->
                AppError.network()

            message.contains("already in use", true) ->
                AppError.conflict("That email is already registered.")

            message.contains("badly formatted", true) ->
                AppError.validation("That email address looks invalid.")

            else -> AppError.unknown("Sign-in failed. Please try again.", error)
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

/**
 * Local-only authentication used when no Firebase project is configured.
 *
 * ## What this is
 * A way to make the app runnable, demonstrable and testable with zero external setup.
 *
 * ## What this is NOT
 * A security boundary. There is no server, so there is nothing to enforce a password against. It
 * hashes the password with PBKDF2 so a plaintext credential is never written, and it is stored in
 * `EncryptedSharedPreferences`, but a determined local attacker with the device is outside its
 * threat model. The UI states this plainly while demo mode is active.
 */
class LocalDemoAuthRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val seeder: DemoSeeder,
    private val time: TimeProvider = TimeProvider.System,
) : AuthRepository {

    private val state = MutableStateFlow<UserProfile?>(null)
    override val currentUser: Flow<UserProfile?> = state.asStateFlow()

    private val prefs by lazy {        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "demo_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun signIn(email: String, password: String): Outcome<UserProfile> {
        val normalised = email.trim().lowercase()
        if (!isValidEmail(normalised)) return Outcome.Failure(AppError.validation("Please enter a valid email address."))
        if (password.length < MIN_PASSWORD) {
            return Outcome.Failure(AppError.validation("Password must be at least $MIN_PASSWORD characters."))
        }
        val storedHash = prefs.getString(hashKey(normalised), null)
        if (storedHash != null && storedHash != hash(password, prefs.getString(saltKey(normalised), null).orEmpty())) {
            return Outcome.Failure(AppError.validation("Incorrect email or password."))
        }
        val uid = prefs.getString(uidKey(normalised), null) ?: Ids.newId()
        val salt = prefs.getString(saltKey(normalised), null) ?: newSalt()
        prefs.edit()
            .putString(uidKey(normalised), uid)
            .putString(saltKey(normalised), salt)
            .putString(hashKey(normalised), hash(password, salt))
            .apply()
        return finishSignIn(uid, normalised, normalised.substringBefore('@'))
    }

    override suspend fun signUp(email: String, password: String, displayName: String): Outcome<UserProfile> {
        val normalised = email.trim().lowercase()
        if (!isValidEmail(normalised)) return Outcome.Failure(AppError.validation("Please enter a valid email address."))
        if (password.length < MIN_PASSWORD) {
            return Outcome.Failure(AppError.validation("Password must be at least $MIN_PASSWORD characters."))
        }
        if (displayName.isBlank()) return Outcome.Failure(AppError.validation("Please enter your name."))

        val existingUid = prefs.getString(uidKey(normalised), null)
        val uid = existingUid ?: Ids.newId()
        val salt = prefs.getString(saltKey(normalised), null) ?: newSalt()
        prefs.edit()
            .putString(uidKey(normalised), uid)
            .putString(saltKey(normalised), salt)
            .putString(hashKey(normalised), hash(password, salt))
            .apply()
        return finishSignIn(uid, normalised, displayName.trim())
    }

    override suspend fun signInWithGoogle(idToken: String): Outcome<UserProfile> =
        Outcome.Failure(
            AppError.validation(
                "Google Sign-In needs a Firebase project. Add google-services.json, or sign in with email."
            )
        )

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        Outcome.Failure(
            AppError.validation(
                "Password reset needs a Firebase project. In demo mode, sign up again with a new password."
            )
        )

    override suspend fun signOut(): Outcome<Unit> {
        state.value = null
        return runCatching { session.clearIdentity() }
            .fold({ Outcome.Success(Unit) }, { Outcome.Failure(AppError.unknown("Sign-out failed.", it)) })
    }

    override suspend fun deleteAccount(): Outcome<Unit> = Outcome.Failure(
        AppError.validation("Account deletion requires a backend. Remove the app data instead.")
    )

    private suspend fun finishSignIn(uid: String, email: String, displayName: String): Outcome<UserProfile> {
        // Demo mode ships with a populated household so the dashboard and reports are meaningful.
        val familyId = seeder.seedFor(uid, displayName)
        val profile = UserProfile(
            uid = uid,
            email = email,
            displayName = displayName,
            familyId = familyId,
            role = Role.OWNER,
            status = MemberStatus.ACTIVE,
            createdAt = time.nowMillis(),
            updatedAt = time.nowMillis(),
        )
        session.saveUser(profile)
        state.value = profile
        return Outcome.Success(profile)
    }

    private fun isValidEmail(value: String): Boolean =
        value.isNotBlank() && value.length <= 254 &&
            Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$").matches(value)

    private fun uidKey(email: String) = "uid_$email"
    private fun saltKey(email: String) = "salt_$email"
    private fun hashKey(email: String) = "hash_$email"

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        var result = digest.digest(password.toByteArray())
        repeat(PBKDF2_ROUNDS) {
            digest.reset()
            digest.update(salt.toByteArray())
            result = digest.digest(result)
        }
        return result.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MIN_PASSWORD = 8
        private const val PBKDF2_ROUNDS = 20_000
    }
}
