package com.familyfinance.app.data.local

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * The SQLCipher passphrase for the local database.
 *
 * A random 256-bit key is generated once and stored wrapped by the Android Keystore via
 * `EncryptedSharedPreferences`. It is never derived from a user password (there is no master
 * password to forget) and never leaves the device.
 */
object DatabasePassphrase {

    private const val PREFS = "ff_db_secure"
    private const val KEY = "db_passphrase"
    private const val KEY_BYTES = 32

    @Volatile
    private var cached: ByteArray? = null

    fun get(context: Context): ByteArray {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val base64 = prefs.getString(KEY, null) ?: run {
                val generated = Base64.encodeToString(
                    ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) },
                    Base64.NO_WRAP,
                )
                prefs.edit().putString(KEY, generated).apply()
                generated
            }
            // Kept as ASCII so the same bytes are used whether SQLCipher is given a String or a byte[].
            val bytes = base64.toByteArray(Charsets.US_ASCII)
            cached = bytes
            return bytes
        }
    }
}
