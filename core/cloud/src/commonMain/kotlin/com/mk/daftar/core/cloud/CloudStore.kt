package com.mk.daftar.core.cloud

/**
 * The stored record of the account control document: `users/{uid}`. [state] is `active` or `deleting` and [revision]
 * increases by exactly one with every backup mutation. The server writes `updatedAt`; the client never sends a clock.
 */
data class ControlRecord(val state: String, val revision: Long)

/**
 * One storage slot: `users/{uid}/backups/{slot}`. [ciphertext] is the complete `.dftar` file as raw bytes. [createdAtMillis]
 * is the server time read back from the document; a write ignores it, because the server stamps `createdAt`.
 */
class SlotRecord(
    val backupId: String,
    val formatVersion: Long,
    val size: Long,
    val sha256: String,
    val createdAtMillis: Long?,
    val ciphertext: ByteArray?,
)

/**
 * The reads and writes one transaction may make. A transaction body runs with a fresh instance and may run more than
 * once, so it must derive every decision from these reads alone.
 */
interface CloudTransaction {
    fun readControl(): ControlRecord?

    /** Reads slot [slot], which is 0, 1 or 2. */
    fun readSlot(slot: Int): SlotRecord?

    fun writeControl(record: ControlRecord)

    /** Writes slot [slot]. The server sets `createdAt`. */
    fun writeSlot(slot: Int, record: SlotRecord)

    fun deleteSlot(slot: Int)
}

/** Why a store operation failed. None of these carries a message from the SDK. */
enum class CloudStoreFailure {
    /** The network is unavailable, so nothing was committed. */
    OFFLINE,

    /** The project's free quota was exhausted. */
    QUOTA,

    /** The request was refused: the sign-in is not accepted, or App Check refused the app. */
    DENIED,

    /** The store is not configured in this build. */
    UNAVAILABLE,

    /** The commit reached the server or timed out, so it may or may not have been applied. */
    UNKNOWN_OUTCOME,
}

class CloudStoreException(val failure: CloudStoreFailure) : RuntimeException(failure.name)

/** The Firestore transport. Platforms implement it; common code decides everything inside [runTransaction]. */
interface CloudStore {
    /**
     * Runs [body] inside one transaction for `users/{uid}` and returns its result. Throws [CloudStoreException] when the
     * store cannot be reached, is refused, or fails after the commit may have started.
     */
    suspend fun <T> runTransaction(uid: String, body: (CloudTransaction) -> T): T
}
