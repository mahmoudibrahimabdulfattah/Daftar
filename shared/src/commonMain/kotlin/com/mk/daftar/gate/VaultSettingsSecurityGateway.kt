package com.mk.daftar.gate

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.session.VaultAuthenticator
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.security.BiometricError
import com.mk.daftar.core.security.BiometricPrompter
import com.mk.daftar.core.security.BiometricSlotState
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.VaultSecurity
import com.mk.daftar.feature.settings.BiometricFacts
import com.mk.daftar.feature.settings.BiometricSlotFact
import com.mk.daftar.feature.settings.SecurityActionFailure
import com.mk.daftar.feature.settings.SettingsSecurityGateway
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The settings feature's bridge to the vault. Biometric and PIN actions go through the same authenticator and security
 * layer as unlock, so their rules, backoff and persistence are unchanged here. A successful PIN change locks the vault:
 * the new PIN is authoritative, and the user opens the vault again with it.
 */
internal class VaultSettingsSecurityGateway(
    private val security: VaultSecurity,
    private val authenticator: VaultAuthenticator,
    private val session: VaultSession,
    private val prompter: BiometricPrompter,
) : SettingsSecurityGateway {

    override suspend fun biometricFacts(): BiometricFacts {
        val slot = when (val state = security.biometricSlotState()) {
            is Outcome.Failure -> BiometricSlotFact.Unreadable

            is Outcome.Success -> when (state.data) {
                BiometricSlotState.Absent -> BiometricSlotFact.Absent
                BiometricSlotState.Valid -> BiometricSlotFact.Valid
                BiometricSlotState.Invalid -> BiometricSlotFact.Invalid
            }
        }
        return BiometricFacts(slot = slot, capability = security.biometricCapability())
    }

    override suspend fun verifyCurrentPin(pin: SecretBytes): Outcome<Unit, SecurityActionFailure> =
        security.verifyCurrentPin(pin).toSettingsOutcome()

    override suspend fun changePin(currentPin: SecretBytes, newPin: SecretBytes): Outcome<Unit, SecurityActionFailure> {
        val result = security.changePin(currentPin, newPin)
        if (result is Outcome.Success) {
            // The session holds the old key. It closes even when the caller is cancelled, so no stale key stays open.
            withContext(NonCancellable) { session.lock() }
        }
        return result.toSettingsOutcome()
    }

    override suspend fun reenrollBiometrics(pin: SecretBytes): Outcome<Unit, SecurityActionFailure> =
        authenticator.reenrollBiometrics(pin, prompter).toBiometricSettingsOutcome()

    override suspend fun disableBiometrics(): Outcome<Unit, SecurityActionFailure> =
        authenticator.disableBiometrics().toBiometricSettingsOutcome()
}

/** A PIN-level failure in the settings feature's terms. Storage and session failures keep their meaning. */
internal fun SecurityError.toSettingsFailure(): SecurityActionFailure = when (this) {
    SecurityError.AuthenticationFailed -> SecurityActionFailure.WrongPin
    is SecurityError.Throttled -> SecurityActionFailure.Throttled(retryAfter)
    SecurityError.PersistenceFailure -> SecurityActionFailure.StorageFailure
    SecurityError.SessionSuperseded -> SecurityActionFailure.SessionEnded
    else -> SecurityActionFailure.Failed
}

/** A biometric failure in the settings feature's terms. A PIN problem inside a biometric step keeps its PIN meaning. */
internal fun BiometricError.toSettingsFailure(): SecurityActionFailure = when (this) {
    BiometricError.Cancelled -> SecurityActionFailure.Cancelled
    BiometricError.LockedOut -> SecurityActionFailure.BiometricLockedOut
    BiometricError.NotEnrolled -> SecurityActionFailure.BiometricNotEnrolled
    BiometricError.Unavailable -> SecurityActionFailure.BiometricUnavailable
    BiometricError.EnrollmentChanged, BiometricError.CorruptSlot -> SecurityActionFailure.BiometricInvalidated
    BiometricError.StorageFailure -> SecurityActionFailure.StorageFailure
    BiometricError.Superseded -> SecurityActionFailure.SessionEnded
    BiometricError.AuthenticationFailed, BiometricError.PinRequired -> SecurityActionFailure.Failed
    is BiometricError.Security -> error.toSettingsFailure()
}

private fun Outcome<Unit, SecurityError>.toSettingsOutcome(): Outcome<Unit, SecurityActionFailure> = when (this) {
    is Outcome.Success -> Outcome.Success(Unit)
    is Outcome.Failure -> Outcome.Failure(error.toSettingsFailure())
}

private fun Outcome<Unit, BiometricError>.toBiometricSettingsOutcome(): Outcome<Unit, SecurityActionFailure> =
    when (this) {
        is Outcome.Success -> Outcome.Success(Unit)
        is Outcome.Failure -> Outcome.Failure(error.toSettingsFailure())
    }
