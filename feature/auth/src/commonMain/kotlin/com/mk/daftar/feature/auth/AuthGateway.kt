package com.mk.daftar.feature.auth

import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.security.BiometricCapability
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.SecurityStatus

/**
 * The only way authentication reaches the vault. `shared` binds it to the security layer and the authenticator, so this
 * feature never sees a key lease or the data layer. The caller owns the [SecretBytes] it passes and wipes them.
 */
interface AuthGateway {
    /** A snapshot of the vault's state. It is not a stream: callers ask again when they need an answer. */
    suspend fun status(): SecurityStatus

    /** Creates the vault and opens it. Never retried blindly: after a failure the caller rechecks [status]. */
    suspend fun create(pin: SecretBytes): AuthResult

    /**
     * Creates the vault, enrolls biometrics for the same key, then opens it. An enrollment failure leaves the PIN vault
     * closed and usable, and reports [AuthResult.EnrollmentFailed].
     */
    suspend fun createWithBiometrics(pin: SecretBytes): AuthResult

    suspend fun unlock(pin: SecretBytes): AuthResult

    /** Opens the vault after the platform prompt. Prompts start only from an explicit tap on the lock screen. */
    suspend fun unlockWithBiometrics(): AuthResult

    /** Whether the platform can authenticate with a strong biometric now. Used for the onboarding offer. */
    suspend fun biometricCapability(): BiometricCapability

    /** Whether the lock screen may offer biometrics: preference, valid slot and platform capability agree. */
    suspend fun biometricReady(): Boolean

    /**
     * Whether this lock episode has already spent its automatic biometric request. Non-sensitive storage: it holds only
     * a flag. Reads that fail report true, so a broken store never causes an automatic prompt.
     */
    suspend fun autoAttemptConsumed(): Boolean

    /**
     * Persists the consumed flag before a request reaches the platform. Returns false when the write failed; the caller
     * must then not start the request.
     */
    suspend fun consumeAutoAttempt(): Boolean

    /** Clears the consumed flag after a successful unlock, so the next lock episode is eligible again. */
    suspend fun clearAutoAttempt()
}

/** The outcome of one create or unlock, in terms the feature owns. */
sealed interface AuthResult {
    /** The vault is open. The gate shows the private shell for the new generation. */
    data object Opened : AuthResult

    /** The security layer refused or failed. Carries no PIN or key material. */
    data class Security(val error: SecurityError) : AuthResult

    /** Opening the database failed after the security layer succeeded. */
    data class Storage(val error: StorageError) : AuthResult

    /** A biometric unlock did not open the vault. The PIN path stays available. */
    data class Biometric(val reason: AuthBiometricFailure) : AuthResult

    /** The vault was created, but biometrics were not enabled. The vault is closed and opens with the PIN. */
    data class EnrollmentFailed(val reason: AuthBiometricFailure) : AuthResult

    /** A session is already open, so this attempt was not made. */
    data object AlreadyOpen : AuthResult

    /** A lock arrived while the attempt was in flight. The late result was discarded silently. */
    data object Superseded : AuthResult
}

/** Why a biometric step did not complete, in the feature's own terms. Never a platform message. */
enum class AuthBiometricFailure {
    /** The user dismissed the prompt. Not an error: the PIN screen simply stays. */
    Cancelled,

    /** The platform locked biometrics out. The app's PIN counter is unaffected. */
    LockedOut,

    /** Biometrics are not available now, or were never enrolled. */
    Unavailable,

    /** The device's biometric enrollment changed, so the app slot was removed. */
    EnrollmentChanged,

    StorageFailure,

    /** The app was locked while the prompt was on screen. */
    Superseded,
}
