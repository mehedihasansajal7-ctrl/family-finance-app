package com.familyfinance.app.core.util

import java.util.UUID

/** Identifier generation, isolated so tests can inject deterministic ids. */
object Ids {

    fun newId(): String = UUID.randomUUID().toString()

    /**
     * Device-generated idempotency key for a write.
     *
     * A transaction carries this from the moment it is created. Replaying an offline queue, or a
     * retry after a timeout, therefore upserts the same document instead of creating a duplicate.
     */
    fun newClientId(): String = UUID.randomUUID().toString()

    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I/O/0/1 confusion

    /** Human-typable family invitation code, e.g. "K7QP-3M2X". */
    fun newInviteCode(): String {
        val raw = (1..8).map { CODE_ALPHABET.random() }.joinToString("")
        return "${raw.take(4)}-${raw.drop(4)}"
    }

    /** Opaque per-member key used by the aggregate pipeline. Never displayed. */
    fun memberKey(uid: String, familyId: String): String =
        "$familyId:$uid".hashCode().toUInt().toString(16)
}

/** Injectable clock so date-sensitive logic can be tested without sleeping. */
fun interface TimeProvider {
    fun nowMillis(): Long

    companion object {
        val System = TimeProvider { java.lang.System.currentTimeMillis() }
    }
}
