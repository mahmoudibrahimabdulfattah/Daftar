package com.mk.daftar.core.security

import kotlin.time.Duration

/** Why a security operation failed. Never carries a PIN, key, salt, envelope or other sensitive payload. */
sealed interface SecurityError {
    /** The PIN is not 6 to 16 decimal digits after normalization. */
    data object InvalidPinFormat : SecurityError

    /** The PIN did not open the vault. Counted toward the backoff schedule. */
    data object AuthenticationFailed : SecurityError

    /** Attempts are blocked until [retryAfter] has elapsed. */
    data class Throttled(val retryAfter: Duration) : SecurityError

    /** The device cannot provide a hardware-backed key that meets policy. */
    data object UnsupportedHardware : SecurityError

    /** A device key is missing, invalidated or refused use. A replacement is never generated over a vault. */
    data object DeviceKeyUnavailable : SecurityError

    /** Stored security state is missing, malformed or fails authentication. */
    data object CorruptState : SecurityError

    /** A stored record uses an unknown format version, purpose or KDF parameters outside the allowlist. */
    data object UnsupportedFormat : SecurityError

    /** Memory or another required resource could not be obtained. */
    data object ResourceUnavailable : SecurityError

    /**
     * Argon2id could not allocate its memory (libsodium `crypto_pwhash` returned -1). Nothing was changed. It is never a
     * wrong credential, so it never counts toward the PIN backoff.
     */
    data object InsufficientMemory : SecurityError

    /** Security state could not be written durably. The previously committed state remains in effect. */
    data object PersistenceFailure : SecurityError

    /** No vault exists yet. */
    data object NotInitialized : SecurityError

    /** A vault already exists. It is never replaced by create. */
    data object AlreadyInitialized : SecurityError

    /** The session was locked while this unlock was in flight, so the late result was discarded. */
    data object SessionSuperseded : SecurityError

    /** The recovery key is malformed or is not this vault's recovery key. */
    data object RecoveryKeyRejected : SecurityError

    /** This vault has no recovery key. */
    data object RecoveryNotSetUp : SecurityError
}

/** Startup-visible state of the vault. */
sealed interface SecurityStatus {
    data object NotInitialized : SecurityStatus

    /** Locked. [retryAfter] is set only while a backoff penalty is still running. */
    data class Locked(val retryAfter: Duration?) : SecurityStatus

    data object Unlocked : SecurityStatus

    data class Unavailable(val error: SecurityError) : SecurityStatus
}
