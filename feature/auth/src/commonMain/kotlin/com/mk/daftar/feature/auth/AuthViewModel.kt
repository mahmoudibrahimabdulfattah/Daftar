package com.mk.daftar.feature.auth

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.security.PinEntryBuffer
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.security.SecurityError
import com.mk.daftar.core.security.SecurityStatus
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The authentication screens: create, confirm, the optional biometric offer, unlock, recovery and the unavailable states.
 *
 * The PIN lives only in [PinEntryBuffer] and [SecretBytes], never in [AuthUiState]. Digits are processed synchronously,
 * so a digit is never queued behind a running operation. Status reads and submissions are serialized: while one runs,
 * a second is ignored. Every operation owns its submission copy and wipes it in `finally`.
 *
 * Biometric prompts start only from an explicit tap. A cancelled prompt returns to the PIN without a message or a second
 * prompt. A prompt also deactivates the app, so privacy events wipe drafts but leave a biometric operation running; an
 * actual background or lock cancels it through the session.
 *
 * Success is not announced here. The vault opens, the gate renders the new generation, and this model only wipes its
 * drafts.
 */
class AuthViewModel(
    private val gateway: AuthGateway,
    private val clock: MonotonicTime,
    private val drafts: AuthDraftRegistry,
) : MviViewModel<AuthUiState, AuthIntent, AuthEffect>(AuthUiState()) {

    private var entry = PinEntryBuffer()

    /** The PIN the user chose. Owned here until creation takes it. */
    private var candidate: SecretBytes? = null

    /**
     * A permanent fault that a failed open reported. A later status read cannot replace it: the gate rechecks the status
     * on every state change and resume, and a failed create leaves the status "not initialized", which would otherwise
     * hide the reason. The state is shown until the view model ends.
     */
    private var pinnedUnavailable: AuthUnavailableReason? = null

    private var work: Job? = null
    private var workToken = 0
    private var statusJob: Job? = null
    private var offerJob: Job? = null
    private var countdown: Job? = null
    private val unregister: () -> Unit = drafts.register { onIntent(AuthIntent.PrivacyInvalidated) }

    init {
        refreshStatus()
    }

    override fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.Digit -> digit(intent.codePoint)
            AuthIntent.Erase -> erase()
            AuthIntent.Submit -> submit()
            AuthIntent.Back -> back()
            AuthIntent.RestartCreation -> restartCreation()
            AuthIntent.EnableBiometrics -> enableBiometrics()
            AuthIntent.SkipBiometrics -> createVault()
            AuthIntent.BiometricUnlock -> biometricUnlock()
            AuthIntent.ShowRecovery -> showRecovery()
            AuthIntent.LeaveRecovery -> leaveRecovery()
            AuthIntent.Recheck -> refreshStatus()
            AuthIntent.PrivacyInvalidated -> invalidate()
            AuthIntent.OpenRestore -> Unit
        }
    }

    override fun onCleared() {
        unregister()
        work?.cancel()
        statusJob?.cancel()
        offerJob?.cancel()
        countdown?.cancel()
        entry.wipe()
        candidate?.wipe()
        candidate = null
        super.onCleared()
    }

    private fun digit(codePoint: Int) {
        if (!currentState.inputEnabled) return
        if (!entry.append(codePoint)) return
        reduce {
            copy(enteredCount = entry.size, error = error.takeIf { it == AuthError.PinMismatch })
        }
    }

    private fun erase() {
        if (!currentState.inputEnabled) return
        if (!entry.erase()) return
        reduce { copy(enteredCount = entry.size, error = error.takeIf { it == AuthError.PinMismatch }) }
    }

    private fun submit() {
        val state = currentState
        if (!state.submitEnabled) return
        when (state.phase) {
            AuthPhase.Create -> chooseCandidate()
            AuthPhase.Confirm -> confirmCandidate()
            AuthPhase.Unlock -> unlock()
            else -> Unit
        }
    }

    private fun chooseCandidate() {
        val chosen = entry.snapshot() ?: return
        candidate?.wipe()
        candidate = chosen
        entry.clear()
        reduce { copy(phase = AuthPhase.Confirm, enteredCount = 0, error = null) }
    }

    private fun confirmCandidate() {
        val expected = candidate
        if (expected == null) {
            restartCreation()
            return
        }
        val matched = entry.matches(expected)
        entry.clear()
        if (matched) {
            reduce {
                copy(
                    phase = AuthPhase.Biometric,
                    enteredCount = 0,
                    error = null,
                    biometricOffer = BiometricOffer.Checking,
                )
            }
            offerBiometrics()
        } else {
            // Only the confirmation is cleared. The candidate stays, so the user can re-enter the confirmation.
            reduce { copy(enteredCount = 0, error = AuthError.PinMismatch) }
        }
    }

    /** Asks the platform what the offer can say. The offer stays [BiometricOffer.Checking] until it answers. */
    private fun offerBiometrics() {
        offerJob?.cancel()
        offerJob = viewModelScope.launch {
            val capability = gateway.biometricCapability()
            coroutineContext.ensureActive()
            if (currentState.phase == AuthPhase.Biometric) {
                reduce { copy(biometricOffer = BiometricOffer.from(capability)) }
            }
        }
    }

    private fun back() {
        if (currentState.busy != null) return
        when (currentState.phase) {
            AuthPhase.Confirm, AuthPhase.Biometric -> restartCreation()

            AuthPhase.Create, AuthPhase.Unlock -> {
                entry.clear()
                reduce { copy(enteredCount = 0, error = null) }
            }

            AuthPhase.Recovery -> leaveRecovery()

            else -> Unit
        }
    }

    /** Wipes the candidate and the entry, then returns to an empty Create. */
    private fun restartCreation() {
        if (currentState.busy != null) return
        wipeDrafts()
        reduce { copy(phase = AuthPhase.Create, error = null, retrySeconds = null, notice = null) }
    }

    /** "Not now": creates a PIN-only vault from the candidate. */
    private fun createVault() {
        val state = currentState
        if (state.phase != AuthPhase.Biometric || state.busy != null) return
        // The operation takes ownership of the candidate, so a later privacy event cannot wipe it under the KDF.
        val pin = candidate ?: return restartCreation()
        candidate = null
        run(AuthBusy.Creating) {
            val result = try {
                gateway.create(pin)
            } finally {
                pin.wipe()
            }
            coroutineContext.ensureActive()
            when (result) {
                AuthResult.Opened -> wipeDrafts()
                is AuthResult.Security -> afterFailedOpen(result.error, AuthError.OpenFailed)
                is AuthResult.Storage -> recheckAfterFailure(AuthError.OpenFailed)
                else -> refreshNow()
            }
        }
    }

    /**
     * "Enable biometrics": creates the vault, enrolls the same key, and opens only once both are done. When enrollment
     * fails, the vault is still created, so the user lands on the PIN screen with a notice. Creation is never repeated.
     */
    private fun enableBiometrics() {
        val state = currentState
        if (state.phase != AuthPhase.Biometric || state.busy != null) return
        if (state.biometricOffer != BiometricOffer.Available) return
        val pin = candidate ?: return restartCreation()
        candidate = null
        run(AuthBusy.Enrolling) {
            val result = try {
                gateway.createWithBiometrics(pin)
            } finally {
                pin.wipe()
            }
            coroutineContext.ensureActive()
            when (result) {
                AuthResult.Opened -> wipeDrafts()

                is AuthResult.EnrollmentFailed -> {
                    wipeDrafts()
                    refreshNow(notice = AuthNotice.BiometricsNotEnabled)
                }

                is AuthResult.Security -> afterFailedOpen(result.error, AuthError.OpenFailed)

                is AuthResult.Storage -> recheckAfterFailure(AuthError.OpenFailed)

                else -> refreshNow()
            }
        }
    }

    private fun unlock() {
        if (currentState.busy != null || work?.isActive == true) return
        val pin = entry.snapshot() ?: return
        run(AuthBusy.Unlocking) {
            val result = try {
                gateway.unlock(pin)
            } finally {
                pin.wipe()
            }
            coroutineContext.ensureActive()
            when (result) {
                AuthResult.Opened -> wipeDrafts()

                is AuthResult.Security -> when (result.error) {
                    SecurityError.AuthenticationFailed -> {
                        entry.clear()
                        reduce { copy(enteredCount = 0, error = AuthError.IncorrectPin) }
                        refreshNow()
                    }

                    is SecurityError.Throttled -> {
                        entry.clear()
                        reduce { copy(enteredCount = 0, error = null) }
                        startCountdown(result.error.retryAfter)
                    }

                    SecurityError.SessionSuperseded -> {
                        entry.clear()
                        reduce { copy(enteredCount = 0) }
                    }

                    else -> afterFailedOpen(result.error, AuthError.OpenFailed)
                }

                is AuthResult.Storage -> {
                    entry.clear()
                    reduce { copy(enteredCount = 0, error = AuthError.OpenFailed) }
                    refreshNow()
                }

                AuthResult.AlreadyOpen -> refreshNow()

                AuthResult.Superseded -> {
                    entry.clear()
                    reduce { copy(enteredCount = 0) }
                }

                // A biometric result never reaches the PIN path. Re-reading the status is the safe answer.
                is AuthResult.Biometric, is AuthResult.EnrollmentFailed -> refreshNow()
            }
        }
    }

    /**
     * The biometric key. The prompt starts from this tap only. A cancelled prompt returns to the PIN silently, and it is
     * never shown again until the user taps the key. The PIN counter is never touched.
     */
    private fun biometricUnlock() {
        val state = currentState
        if (state.phase != AuthPhase.Unlock || state.busy != null || !state.biometricReady) return
        if (state.retrySeconds != null || work?.isActive == true) return
        run(AuthBusy.UnlockingBiometric) {
            val result = gateway.unlockWithBiometrics()
            coroutineContext.ensureActive()
            when (result) {
                AuthResult.Opened -> wipeDrafts()

                is AuthResult.Biometric -> afterBiometricFailure(result.reason)

                is AuthResult.Security -> afterFailedOpen(result.error, AuthError.OpenFailed)

                is AuthResult.Storage -> {
                    entry.clear()
                    reduce { copy(enteredCount = 0, error = AuthError.OpenFailed) }
                    refreshNow()
                }

                AuthResult.AlreadyOpen -> refreshNow()

                // The lock came first: its result is the only one that counts, so nothing is shown here.
                AuthResult.Superseded -> Unit

                is AuthResult.EnrollmentFailed -> refreshNow()
            }
        }
    }

    private suspend fun afterBiometricFailure(reason: AuthBiometricFailure) {
        when (reason) {
            AuthBiometricFailure.Cancelled, AuthBiometricFailure.Superseded -> Unit

            AuthBiometricFailure.LockedOut -> refreshNow(notice = AuthNotice.BiometricsLockedOut)

            AuthBiometricFailure.Unavailable,
            AuthBiometricFailure.EnrollmentChanged,
            AuthBiometricFailure.StorageFailure,
            -> refreshNow(notice = AuthNotice.BiometricsUnavailable)
        }
    }

    /** A failed open: permanent faults become the unavailable state, everything else is read from a fresh status. */
    private suspend fun afterFailedOpen(error: SecurityError, failure: AuthError) {
        val reason = error.unavailableReasonOrNull()
        if (reason != null) {
            pinnedUnavailable = reason
            wipeDrafts()
            reduce { copy(phase = AuthPhase.Unavailable(reason), error = null, retrySeconds = null) }
        } else {
            recheckAfterFailure(failure)
        }
    }

    /**
     * After a failed create or unlock the security envelope may already be committed, so the status is read again and
     * create is never repeated blindly.
     */
    private suspend fun recheckAfterFailure(failure: AuthError) {
        entry.clear()
        reduce { copy(enteredCount = 0) }
        refreshNow(failure = failure)
    }

    private suspend fun refreshNow(failure: AuthError? = null, notice: AuthNotice? = null) {
        val status = gateway.status()
        coroutineContext.ensureActive()
        val pinned = pinnedUnavailable
        if (pinned != null && status !is SecurityStatus.Unlocked) {
            wipeDrafts()
            reduce {
                copy(
                    phase = AuthPhase.Unavailable(pinned),
                    error = null,
                    retrySeconds = null,
                    biometricReady = false,
                    notice = null,
                )
            }
            return
        }
        val ready = if (status is SecurityStatus.Locked) gateway.biometricReady() else false
        applyStatus(status, failure, notice, ready)
    }

    private fun showRecovery() {
        if (currentState.phase != AuthPhase.Unlock || currentState.busy != null) return
        entry.clear()
        reduce { copy(phase = AuthPhase.Recovery, enteredCount = 0, error = null) }
    }

    private fun leaveRecovery() {
        if (currentState.phase != AuthPhase.Recovery) return
        entry.clear()
        reduce { copy(phase = AuthPhase.Unlock, enteredCount = 0, error = null) }
        refreshStatus()
    }

    /** Reads the vault status once, unless a read or an operation is already running. */
    private fun refreshStatus() {
        if (statusJob?.isActive == true || currentState.busy != null) return
        statusJob = viewModelScope.launch { refreshNow() }
    }

    /** Moves to the screen the security state calls for. Only [AuthPhase.Unlock] keeps typed digits. */
    private fun applyStatus(status: SecurityStatus, failure: AuthError?, notice: AuthNotice?, ready: Boolean) {
        when (status) {
            SecurityStatus.NotInitialized -> {
                wipeDrafts()
                reduce {
                    copy(
                        phase = AuthPhase.Create,
                        error = failure,
                        retrySeconds = null,
                        biometricReady = false,
                        notice = null,
                    )
                }
            }

            is SecurityStatus.Locked -> {
                val previous = currentState.phase
                if (previous != AuthPhase.Unlock) wipeDrafts()
                reduce {
                    copy(
                        phase = AuthPhase.Unlock,
                        error = failure ?: error.takeIf { previous == AuthPhase.Unlock },
                        biometricReady = ready,
                        notice = notice ?: this.notice.takeIf { previous == AuthPhase.Unlock },
                    )
                }
                val retry = status.retryAfter
                if (retry != null) startCountdown(retry) else stopCountdown()
            }

            SecurityStatus.Unlocked -> {
                stopCountdown()
                wipeDrafts()
                reduce {
                    copy(
                        phase = AuthPhase.Checking,
                        error = null,
                        retrySeconds = null,
                        biometricReady = false,
                        notice = null,
                    )
                }
            }

            is SecurityStatus.Unavailable -> {
                stopCountdown()
                wipeDrafts()
                val reason = status.error.unavailableReasonOrNull() ?: AuthUnavailableReason.Transient
                reduce {
                    copy(
                        phase = AuthPhase.Unavailable(reason),
                        error = null,
                        retrySeconds = null,
                        biometricReady = false,
                        notice = null,
                    )
                }
            }
        }
    }

    /**
     * Runs one operation that owns the screen's input. A second operation is refused while one is in flight. The busy
     * marker is cleared only by the operation that set it, so a cancelled job cannot clear a newer one.
     */
    private fun run(busy: AuthBusy, block: suspend () -> Unit) {
        if (work?.isActive == true) return
        val token = ++workToken
        reduce { copy(busy = busy, error = null, notice = null) }
        work = viewModelScope.launch {
            try {
                block()
            } finally {
                if (token == workToken) reduce { copy(busy = null) }
            }
        }
    }

    /**
     * Privacy: drop the drafts, cancel work whose result must not be shown, and leave no busy marker. A biometric
     * operation is not cancelled here: the prompt deactivates the app, and an actual lock cancels it in the session.
     */
    private fun invalidate() {
        wipeDrafts()
        if (currentState.busy?.isBiometric == true) return
        workToken++
        work?.cancel()
        statusJob?.cancel()
        offerJob?.cancel()
        val creating = currentState.phase.isCreation
        reduce {
            copy(
                phase = if (creating) AuthPhase.Create else phase,
                busy = null,
                error = null,
                retrySeconds = retrySeconds,
            )
        }
    }

    /** Clears the entry and the candidate, and resets the count. Does not change the phase. */
    private fun wipeDrafts() {
        entry.clear()
        candidate?.wipe()
        candidate = null
        reduce { copy(enteredCount = 0) }
    }

    private fun startCountdown(retryAfter: Duration) {
        countdown?.cancel()
        val deadline = clock.now() + retryAfter
        countdown = viewModelScope.launch {
            while (true) {
                val remaining = deadline - clock.now()
                if (remaining <= Duration.ZERO) break
                reduce { copy(retrySeconds = ceilSeconds(remaining)) }
                delay(minOf(remaining, ONE_SECOND))
            }
            reduce { copy(retrySeconds = null) }
            // Security stays authoritative: the status is read again before input is enabled.
            refreshStatus()
        }
    }

    private fun stopCountdown() {
        countdown?.cancel()
        countdown = null
        reduce { copy(retrySeconds = null) }
    }

    private companion object {
        val ONE_SECOND: Duration = 1000.milliseconds

        fun ceilSeconds(remaining: Duration): Int = ((remaining.inWholeMilliseconds + 999) / 1000).toInt()
    }
}

private val AuthPhase.isCreation: Boolean
    get() = this == AuthPhase.Create || this == AuthPhase.Confirm || this == AuthPhase.Biometric

/** The reason a security error makes the vault unusable, or null when it may pass with a retry or a status read. */
internal fun SecurityError.unavailableReasonOrNull(): AuthUnavailableReason? = when (this) {
    SecurityError.UnsupportedHardware -> AuthUnavailableReason.UnsupportedHardware
    SecurityError.DeviceKeyUnavailable -> AuthUnavailableReason.DeviceKeyUnavailable
    SecurityError.CorruptState -> AuthUnavailableReason.CorruptState
    SecurityError.UnsupportedFormat -> AuthUnavailableReason.UnsupportedFormat
    else -> null
}
