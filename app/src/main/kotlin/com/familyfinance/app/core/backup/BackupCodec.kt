package com.familyfinance.app.core.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based encryption for the backup file.
 *
 * AES-256-GCM with a PBKDF2-HMAC-SHA256 key. The file format is:
 *
 * ```
 * "FFBK1" | salt(16) | iv(12) | ciphertext+tag
 * ```
 *
 * GCM authenticates the ciphertext, so a wrong passphrase or any tampering makes [decrypt] throw
 * rather than return garbage. Pure JVM crypto (no Android APIs), so it is unit-tested directly.
 */
object BackupCodec {

    private const val MAGIC = "FFBK1"
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val TRANSFORM = "AES/GCM/NoPadding"

    class WrongPassphrase : Exception("Incorrect passphrase or corrupted backup file.")

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(plain)

        return MAGIC.toByteArray(Charsets.UTF_8) + salt + iv + cipherText
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        val magicBytes = MAGIC.toByteArray(Charsets.UTF_8)
        if (data.size < magicBytes.size + SALT_LEN + IV_LEN) throw WrongPassphrase()
        for (i in magicBytes.indices) if (data[i] != magicBytes[i]) throw WrongPassphrase()

        var offset = magicBytes.size
        val salt = data.copyOfRange(offset, offset + SALT_LEN).also { offset += SALT_LEN }
        val iv = data.copyOfRange(offset, offset + IV_LEN).also { offset += IV_LEN }
        val cipherText = data.copyOfRange(offset, data.size)

        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(cipherText)
        } catch (error: Exception) {
            throw WrongPassphrase()
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }
}
