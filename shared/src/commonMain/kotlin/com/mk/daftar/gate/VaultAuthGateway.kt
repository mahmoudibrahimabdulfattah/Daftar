package com.mk.daftar.gate

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.data.session.VaultOpenFailure
import com.mk.daftar.core.security.BiometricCapability
import com.mk.daftar.core.security.BiometricError
import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityStatus
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.feature.auth.AuthBiometricFailure
import com.mk.daftar.feature.auth.AuthGateway
import com.mk.daftar.feature.auth.AuthResult

/**
 * The composition-facing bridge from the auth feature to the vault. Status comes straight from the security layer, and
 * create and unlock go through the authenticator, which owns the key lease. The platform prompt is supplied here and
 * never reaches the feature. Nothing here keeps a PIN or a lease.
 */
internal class VaultAuthGateway(
    private val security: VaultSecurity,
    private val authenticator: VaultAuthenticator,
    private val prompter: BiometricPrompter,
) : AuthGateway {
    override suspend fun status(): SecurityStatus = security.status()

    override suspend fun create(pin: SecretBytes): AuthResult = authenticator.create(pin).toAuthResult()

    override suspend fun createWithBiometrics(pin: SecretBytes): AuthResult =
        authenticator.createWithBiometrics(pin, prompter).toAuthResult()

    override suspend fun unlock(pin: SecretBytes): AuthResult = authenticator.unlock(pin).toAuthResult()

    override suspend fun unlockWithBiometrics(): AuthResult =
        authenticator.unlockWithBiometrics(prompter).toAuthResult()

    override suspend fun biometricCapability(): BiometricCapability = authenticator.biometricCapability()

    override suspend fun biometricReady(): Boolean = authenticator.biometricReady()
}

internal fun Outcome<Unit, VaultOpenFailure>.toAuthResult(): AuthResult = when (this) {
    is Outcome.Success -> AuthResult.Opened

    is Outcome.Failure -> when (val failure = error) {
        is VaultOpenFailure.Security -> AuthResult.Security(failure.error)
        is VaultOpenFailure.Storage -> AuthResult.Storage(failure.error)
        is VaultOpenFailure.Biometric -> failure.error.toAuthResult()
        is VaultOpenFailure.Enrollment -> AuthResult.EnrollmentFailed(failure.error.toBiometricFailure())
        VaultOpenFailure.AlreadyOpen -> AuthResult.AlreadyOpen
        VaultOpenFailure.Superseded -> AuthResult.Superseded
    }
}

/** A PIN-level error keeps its meaning; every biometric failure becomes the feature's own reason. */
internal fun BiometricError.toAuthResult(): AuthResult = when (this) {
    is BiometricError.Security -> AuthResult.Security(error)
    else -> AuthResult.Biometric(toBiometricFailure())
}

internal fun BiometricError.toBiometricFailure(): AuthBiometricFailure = when (this) {
    BiometricError.Cancelled -> AuthBiometricFailure.Cancelled

    BiometricError.LockedOut -> AuthBiometricFailure.LockedOut

    BiometricError.EnrollmentChanged -> AuthBiometricFailure.EnrollmentChanged

    BiometricError.StorageFailure -> AuthBiometricFailure.StorageFailure

    BiometricError.Superseded -> AuthBiometricFailure.Superseded

    BiometricError.NotEnrolled,
    BiometricError.Unavailable,
    BiometricError.CorruptSlot,
    BiometricError.AuthenticationFailed,
    BiometricError.PinRequired,
    is BiometricError.Security,
    -> AuthBiometricFailure.Unavailable
}
