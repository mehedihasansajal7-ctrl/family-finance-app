package com.familyfinance.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.familyfinance.app.domain.model.MemberStatus
import com.familyfinance.app.domain.model.Role
import com.familyfinance.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Session and preference state.
 *
 * Only **non-secret** state lives here (who is signed in, their role, UI preferences, the last
 * activity timestamp used for the idle timeout). Credentials and tokens are held by Firebase Auth,
 * which keeps them in the platform keystore — this class never sees a password or an ID token.
 */
class SessionManager(private val context: Context) {

    private object Keys {
        val UID = stringPreferencesKey("uid")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PHOTO_URL = stringPreferencesKey("photo_url")
        val FAMILY_ID = stringPreferencesKey("family_id")
        val ROLE = stringPreferencesKey("role")
        val STATUS = stringPreferencesKey("status")

        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val BENGALI_DIGITS = booleanPreferencesKey("bengali_digits")
        val CAPTURE_SMS = booleanPreferencesKey("capture_sms")
        val DEFAULT_SMS_ACCOUNT = stringPreferencesKey("default_sms_account")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_ACTIVE = longPreferencesKey("last_active_at")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
    }

    val currentUser: Flow<UserProfile?> = context.sessionDataStore.data.map { prefs ->
        val uid = prefs[Keys.UID] ?: return@map null
        UserProfile(
            uid = uid,
            email = prefs[Keys.EMAIL].orEmpty(),
            displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
            photoUrl = prefs[Keys.PHOTO_URL],
            familyId = prefs[Keys.FAMILY_ID],
            role = Role.fromId(prefs[Keys.ROLE]),
            status = MemberStatus.fromId(prefs[Keys.STATUS]),
        )
    }

    val biometricLockEnabled: Flow<Boolean> = context.sessionDataStore.data.map { it[Keys.BIOMETRIC_LOCK] ?: false }
    val bengaliDigitsEnabled: Flow<Boolean> = context.sessionDataStore.data.map { it[Keys.BENGALI_DIGITS] ?: false }
    val captureFromSms: Flow<Boolean> = context.sessionDataStore.data.map { it[Keys.CAPTURE_SMS] ?: false }
    val defaultSmsAccountId: Flow<String?> = context.sessionDataStore.data.map { it[Keys.DEFAULT_SMS_ACCOUNT] }
    val themeMode: Flow<String> = context.sessionDataStore.data.map { it[Keys.THEME_MODE] ?: THEME_SYSTEM }
    val lastActiveAt: Flow<Long> = context.sessionDataStore.data.map { it[Keys.LAST_ACTIVE] ?: 0L }
    val lastSyncAt: Flow<Long> = context.sessionDataStore.data.map { it[Keys.LAST_SYNC] ?: 0L }

    suspend fun currentUserOnce(): UserProfile? = currentUser.first()

    suspend fun saveUser(user: UserProfile) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.UID] = user.uid
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.DISPLAY_NAME] = user.displayName
            user.photoUrl?.let { prefs[Keys.PHOTO_URL] = it } ?: prefs.remove(Keys.PHOTO_URL)
            user.familyId?.let { prefs[Keys.FAMILY_ID] = it } ?: prefs.remove(Keys.FAMILY_ID)
            prefs[Keys.ROLE] = user.role.id
            prefs[Keys.STATUS] = user.status.id
            prefs[Keys.LAST_ACTIVE] = System.currentTimeMillis()
        }
    }

    suspend fun updateFamily(familyId: String?) {
        context.sessionDataStore.edit { prefs ->
            familyId?.let { prefs[Keys.FAMILY_ID] = it } ?: prefs.remove(Keys.FAMILY_ID)
        }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.sessionDataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setBengaliDigits(enabled: Boolean) {
        context.sessionDataStore.edit { it[Keys.BENGALI_DIGITS] = enabled }
    }

    suspend fun setCaptureFromSms(enabled: Boolean) {
        context.sessionDataStore.edit { it[Keys.CAPTURE_SMS] = enabled }
    }

    suspend fun setDefaultSmsAccountId(accountId: String?) {
        context.sessionDataStore.edit { prefs ->
            accountId?.let { prefs[Keys.DEFAULT_SMS_ACCOUNT] = it } ?: prefs.remove(Keys.DEFAULT_SMS_ACCOUNT)
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.sessionDataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun touch() {
        context.sessionDataStore.edit { it[Keys.LAST_ACTIVE] = System.currentTimeMillis() }
    }

    suspend fun markSynced(at: Long = System.currentTimeMillis()) {
        context.sessionDataStore.edit { it[Keys.LAST_SYNC] = at }
    }

    /** True when the session has been idle for longer than [timeoutMillis]. */
    suspend fun isIdleExpired(timeoutMillis: Long): Boolean {
        val last = lastActiveAt.first()
        if (last == 0L) return false
        return System.currentTimeMillis() - last > timeoutMillis
    }

    /** Clears identity but keeps device preferences (theme, digits, lock setting). */
    suspend fun clearIdentity() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.UID)
            prefs.remove(Keys.EMAIL)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.PHOTO_URL)
            prefs.remove(Keys.FAMILY_ID)
            prefs.remove(Keys.ROLE)
            prefs.remove(Keys.STATUS)
            prefs.remove(Keys.LAST_ACTIVE)
        }
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        /** Idle timeout before the app re-locks. */
        const val IDLE_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}
