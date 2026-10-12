package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.time.Duration
import kotlin.time.TimeSource

/** Elapsed time on a clock that never goes backward. Wall-clock time is never used for penalties. */
internal interface MonotonicClock {
    fun now(): Duration
}

internal object SystemMonotonicClock : MonotonicClock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun now(): Duration = origin.elapsedNow()
}

/** Cryptographically secure bytes from libsodium's `randombytes`. No application PRNG is used. */
internal interface SecureRandom {
    fun bytes(size: Int): ByteArray
}

/** Password-based key derivation with persisted Argon2id parameters. */
internal interface PinKdf {
    suspend fun derive(pin: ByteArray, salt: ByteArray, params: KdfParams): Outcome<SecretBytes, SecurityError>
}

/** Authenticated encryption, XChaCha20-Poly1305-IETF. The output is ciphertext followed by a 16-byte tag. */
internal interface Aead {
    fun seal(key: ByteArray, nonce: ByteArray, associatedData: ByteArray, plaintext: ByteArray): ByteArray

    /** Returns null when authentication fails. Wrong keys and tampered bytes are indistinguishable here. */
    fun open(key: ByteArray, nonce: ByteArray, associatedData: ByteArray, ciphertext: ByteArray): ByteArray?
}

/**
 * Seals the PIN envelope with a non-exportable hardware key. The sealed bytes are opaque to this module.
 * [prepare] creates the device key when it is missing and never replaces an existing one.
 */
internal interface PlatformEnvelopeProtector {
    suspend fun prepare(): Outcome<Unit, SecurityError>

    suspend fun seal(plaintext: ByteArray): Outcome<ByteArray, SecurityError>

    suspend fun unseal(sealed: ByteArray): Outcome<ByteArray, SecurityError>
}

/** Durable storage for the sealed PIN envelope. [replace] is atomic. */
internal interface SecurityRecordStore {
    suspend fun read(): Outcome<ByteArray?, SecurityError>

    /** Writes only when no record exists yet. Returns false when one already exists. */
    suspend fun createIfAbsent(record: ByteArray): Outcome<Boolean, SecurityError>

    suspend fun replace(record: ByteArray): Outcome<Unit, SecurityError>
}

/** Durable storage for the encoded backoff record. Each write is atomic and durable. */
internal interface BackoffStore {
    suspend fun prepare(): Outcome<Unit, SecurityError>

    suspend fun read(): Outcome<ByteArray?, SecurityError>

    suspend fun write(record: ByteArray): Outcome<Unit, SecurityError>
}

/** Result of checking a PIN against the stored envelope. */
internal sealed interface AttemptResult {
    /** The PIN opened the envelope. The coordinator passes the DEK to the caller's finish step. */
    class Verified(val secret: SecretBytes) : AttemptResult

    /** The PIN was wrong. The attempt counts and its penalty applies. */
    data object WrongPin : AttemptResult

    /** Something other than the PIN failed. Infrastructure failures roll the attempt back. */
    class Failed(val error: SecurityError) : AttemptResult
}

/** Serializes every PIN attempt and owns the persistent backoff state. */
internal interface UnlockCoordinator {
    /** Runs [block] under the same lock as attempts. [initialize] is only valid inside it. */
    suspend fun <T> exclusive(block: suspend () -> T): T

    /** Writes a fresh backoff record for a new vault. Call inside [exclusive]. */
    suspend fun initialize(vaultId: ByteArray): Outcome<Unit, SecurityError>

    /**
     * Gates, reserves, verifies and settles one PIN attempt. [finish] takes ownership of a verified secret and must
     * wipe it or transfer it to a lease. Throttled attempts return before reserving anything.
     */
    suspend fun <T> attempt(
        verify: suspend (BackoffRecord) -> AttemptResult,
        finish: suspend (SecretBytes) -> Outcome<T, SecurityError>,
    ): Outcome<T, SecurityError>

    /** Remaining penalty, or null when attempts are open. */
    suspend fun throttle(): Outcome<Duration?, SecurityError>
}
