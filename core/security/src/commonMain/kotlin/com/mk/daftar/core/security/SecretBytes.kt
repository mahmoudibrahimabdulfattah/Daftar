package com.mk.daftar.core.security

/**
 * An owned, mutable secret buffer. It is wiped by [wipe], never printed, and deliberately not a data class so
 * it cannot be copied by value. Zeroing is best effort: the runtime can keep copies this class cannot reach.
 */
class SecretBytes private constructor(val size: Int, private var bytes: ByteArray?) {
    val isWiped: Boolean
        get() = bytes == null

    /** Runs [block] with the raw bytes. The array must not escape the block. Inline, so suspending work can run inside. */
    inline fun <R> use(block: (ByteArray) -> R): R = block(bytesForUse())

    fun wipe() {
        bytes?.fill(0)
        bytes = null
    }

    override fun toString(): String = "SecretBytes(redacted)"

    @PublishedApi
    internal fun bytesForUse(): ByteArray = checkNotNull(bytes) { "secret has been wiped" }

    companion object {
        /** Takes ownership of [bytes]. The caller must not keep or reuse the array. */
        internal fun owned(bytes: ByteArray): SecretBytes = SecretBytes(bytes.size, bytes)
    }
}

/**
 * The unlocked vault key. Phase 4 holds it for the lifetime of a session and closes it on lock. [useDatabaseKey]
 * is the only path to the raw key, and the bytes are valid only inside the block.
 */
class KeyLease internal constructor(private val key: SecretBytes, val generation: Long) {
    val isClosed: Boolean
        get() = key.isWiped

    /** Bounded accessor for the database factory. Features never receive the raw key. */
    fun <R> useDatabaseKey(block: (ByteArray) -> R): R = key.use(block)

    /** Wipes the key. Later [useDatabaseKey] calls fail. */
    fun close() {
        key.wipe()
    }

    override fun toString(): String = "KeyLease(redacted)"
}
