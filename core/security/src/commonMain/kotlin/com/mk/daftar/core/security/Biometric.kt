package com.mk.daftar.core.security

/**
 * An opaque platform handle for one biometric-bound cryptographic operation. Common code never inspects it; only the
 * platform prompt adapter that presents the prompt for it does.
 */
interface BiometricCrypto

/** What the platform reported for one prompt. Only [Authenticated] lets the operation continue. */
sealed interface BiometricAuthResult {
    /** The user authenticated. [crypto] is the authorized operation, which the security layer completes. */
    class Authenticated(val crypto: BiometricCrypto) : BiometricAuthResult

    data object Cancelled : BiometricAuthResult

    /** Temporary or permanent platform lockout. The app's PIN counter is never affected. */
    data object LockedOut : BiometricAuthResult

    data object NotEnrolled : BiometricAuthResult

    data object Unavailable : BiometricAuthResult

    /** Any other terminal failure reported by the platform. */
    data object Failed : BiometricAuthResult
}

/**
 * Presents the platform biometric prompt for one operation and returns only after the user's decision or a terminal
 * error. Implementations use `BIOMETRIC_STRONG` only and never offer a device credential.
 */
fun interface BiometricPrompter {
    suspend fun authorize(crypto: BiometricCrypto): BiometricAuthResult
}

/** Whether the platform can authenticate with a strong biometric right now. */
enum class BiometricCapability {
    Available,
    NotEnrolled,
    Unavailable,
}

/** Why a biometric operation did not complete. Never carries key, slot or PIN bytes. */
sealed interface BiometricError {
    data object Cancelled : BiometricError

    /** Temporary or permanent platform lockout. The app's PIN counter is not affected. */
    data object LockedOut : BiometricError

    data object NotEnrolled : BiometricError

    data object Unavailable : BiometricError

    /** The biometric key was invalidated, removed, or the device's biometric enrollment changed. The slot is removed. */
    data object EnrollmentChanged : BiometricError

    /** The slot is malformed, belongs to another vault, or fails authentication. The slot is removed. */
    data object CorruptSlot : BiometricError

    data object AuthenticationFailed : BiometricError

    /** Persisting the slot failed. The previously committed slot remains in effect. */
    data object StorageFailure : BiometricError

    /** The session was locked while this operation was in flight, so its result was discarded. */
    data object Superseded : BiometricError

    /** Enrollment needs an app-PIN-proven session, and this one was opened with biometrics. */
    data object PinRequired : BiometricError

    /** A PIN-authenticated step failed. The PIN rules of [SecurityError] apply unchanged. */
    data class Security(val error: SecurityError) : BiometricError
}
