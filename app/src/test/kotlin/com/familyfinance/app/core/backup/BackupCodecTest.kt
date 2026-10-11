package com.familyfinance.app.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupCodecTest {

    private val payload = "Family Finance backup \u09F3 12,345.67".toByteArray(Charsets.UTF_8)

    @Test
    fun `encrypt then decrypt returns the original bytes`() {
        val encrypted = BackupCodec.encrypt(payload, "correct horse".toCharArray())
        val decrypted = BackupCodec.decrypt(encrypted, "correct horse".toCharArray())
        assertThat(decrypted).isEqualTo(payload)
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val encrypted = BackupCodec.encrypt(payload, "pw123456".toCharArray())
        assertThat(String(encrypted, Charsets.ISO_8859_1)).doesNotContain("Family Finance")
    }

    @Test(expected = BackupCodec.WrongPassphrase::class)
    fun `a wrong passphrase fails`() {
        val encrypted = BackupCodec.encrypt(payload, "right".toCharArray())
        BackupCodec.decrypt(encrypted, "wrong".toCharArray())
    }

    @Test(expected = BackupCodec.WrongPassphrase::class)
    fun `tampering with the ciphertext fails`() {
        val encrypted = BackupCodec.encrypt(payload, "pw".toCharArray())
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()
        BackupCodec.decrypt(encrypted, "pw".toCharArray())
    }

    @Test(expected = BackupCodec.WrongPassphrase::class)
    fun `a file without the magic header fails`() {
        BackupCodec.decrypt(ByteArray(50), "pw".toCharArray())
    }

    @Test
    fun `each encryption uses a fresh salt and iv`() {
        val a = BackupCodec.encrypt(payload, "pw".toCharArray())
        val b = BackupCodec.encrypt(payload, "pw".toCharArray())
        assertThat(a).isNotEqualTo(b)
    }
}
