package com.familyfinance.app.data.local

import android.content.Context
import com.familyfinance.app.core.logging.AppLog
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream

/**
 * One-time conversion of a plaintext database to a SQLCipher-encrypted one.
 *
 * A fresh install writes an encrypted database directly (via the open-helper factory). An install
 * that already has a plaintext `family_finance.db` is upgraded in place with SQLCipher's
 * `sqlcipher_export`, so existing data is preserved rather than lost.
 */
object DatabaseEncryption {

    private const val MAGIC = "SQLite format 3"

    fun migrateIfNeeded(context: Context, dbName: String, passphrase: ByteArray) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists() || !isPlaintext(dbFile)) return

        val target = File(dbFile.parentFile, "$dbName.encrypting")
        listOf(target, File(dbFile.parentFile, "$dbName-wal"), File(dbFile.parentFile, "$dbName-shm"))
            .forEach { runCatching { it.delete() } }

        val password = String(passphrase, Charsets.US_ASCII)
        val database = SQLiteDatabase.openOrCreateDatabase(
            dbFile,
            "",
            null as SQLiteDatabase.CursorFactory?,
            null as net.zetetic.database.DatabaseErrorHandler?,
        )
        try {
            database.rawExecSQL("ATTACH DATABASE ? AS encrypted KEY ?", target.absolutePath, password)
            database.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            database.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            database.close()
        }

        if (!dbFile.delete()) AppLog.w("Could not remove plaintext database after export")
        if (!target.renameTo(dbFile)) {
            throw IllegalStateException("Could not finalize the encrypted database")
        }
        File(dbFile.parentFile, "$dbName-wal").delete()
        File(dbFile.parentFile, "$dbName-shm").delete()
    }

    /** True when the file still has the plaintext SQLite header. */
    fun isPlaintext(file: File): Boolean {
        val header = ByteArray(16)
        FileInputStream(file).use { stream ->
            if (stream.read(header) < 16) return false
        }
        return String(header, Charsets.US_ASCII).startsWith(MAGIC)
    }
}
