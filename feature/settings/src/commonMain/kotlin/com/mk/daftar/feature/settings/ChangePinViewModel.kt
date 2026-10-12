package com.mk.daftar.feature.settings

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.MonotonicTime
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.security.PinEntryBuffer
import com.mk.daftar.core.security.SecretBytes
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Changing the Daftar PIN in three steps: the current PIN, the new PIN, and its confirmation. The current PIN is checked
 * first through the security gateway, which counts wrong attempts toward the same penalty as unlock. A verified current PIN
 * is moved into an owned buffer and kept only until the final submission, which runs the change again through the gateway.
 *
 * Every digit lives in [PinEntryBuffer] or [SecretBytes], never in state. Success locks the vault, so the user returns to
 * the auth screens and opens the vault with the new PIN. A privacy event drops every buffer and the work that has not
 * committed. A commit that is already running is not cancelled: the security layer decides it atomically.
 */
class ChangePinViewModel(
    private val gateway: SettingsSecurityGateway,
    private val clock: MonotonicTime,
    private val drafts: SettingsDraftRegistry,
) : MviViewModel<ChangePinUiState, ChangePinIntent, ChangePinEffect>(ChangePinUiState()) {

    /** The digits being typed on the current step. */
    private var entry = PinEntryBuffer()

    /** The current PIN after step one, owned here until the final submission. */
    private var verifiedCurrent: PinEntryBuffer? = null

    /** The new PIN after step two. Confirmation compares against it, and the commit consumes it. */
    private var candidate: SecretBytes? = null

    private var work: Job? = null
    private var countdown: Job? = null
    private val unregister: () -> Unit = drafts.register { onIntent(ChangePinIntent.PrivacyInvalidated) }

    override fun onIntent(intent: ChangePinIntent) {
        when (intent) {
            is ChangePinIntent.Digit -> digit(intent.codePoint)
            ChangePinIntent.Erase -> erase()
            ChangePinIntent.Submit -> submit()
            ChangePinIntent.Restart -> restart()
            ChangePinIntent.PrivacyInvalidated -> invalidate()
        }
    }

    override fun onCleared() {
        unregister()
        work?.cancel()
        countdown?.cancel()
        wipeSecrets()
        super.onCleared()
    }

    private fun digit(codePoint: Int) {
        if (!currentState.inputEnabled) return
        if (!entry.append(codePoint)) return
        reduce { copy(enteredCount = entry.size, failure = null, mismatch = false) }
    }

    private fun erase() {
        if (!currentState.inputEnabled) return
        if (!entry.erase()) return
        reduce { copy(enteredCount = entry.size) }
    }

    private fun submit() {
        val state = currentState
        if (!state.submitEnabled) return
        when (state.stage) {
            ChangePinStage.Verify -> verifyCurrent()
            ChangePinStage.NewPin -> chooseNew()
            ChangePinStage.ConfirmPin -> confirmNew()
            ChangePinStage.Saving, is ChangePinStage.Failed -> Unit
        }
    }

    /** Step one: checks the typed current PIN. A correct PIN moves its buffer into [verifiedCurrent]. */
    private fun verifyCurrent() {
        val pin = entry.snapshot() ?: return
        reduce { copy(busy = true, failure = null) }
        work = viewModelScope.launch {
            val outcome = try {
                gateway.verifyCurrentPin(pin)
            } finally {
                pin.wipe()
            }
            coroutineContext.ensureActive()
            when (outcome) {
                is Outcome.Success -> {
                    verifiedCurrent?.wipe()
                    verifiedCurrent = entry
                    entry = PinEntryBuffer()
                    reduce { copy(stage = ChangePinStage.NewPin, enteredCount = 0, busy = false, failure = null) }
                }

                is Outcome.Failure -> {
                    entry.clear()
                    reduce { copy(enteredCount = 0, busy = false) }
                    showVerifyFailure(outcome.error)
                }
            }
        }
    }

    private fun showVerifyFailure(failure: SecurityActionFailure) {
        when (failure) {
            is SecurityActionFailure.Throttled -> {
                reduce { copy(failure = null) }
                startCountdown(failure.retryAfter)
            }

            // The vault locked while the check was running. The gate takes over, so nothing is announced here.
            SecurityActionFailure.SessionEnded -> Unit

            else -> reduce { copy(failure = failure) }
        }
    }

    /** Step two: keeps the new PIN, and clears the entry for its confirmation. */
    private fun chooseNew() {
        val chosen = entry.snapshot() ?: return
        candidate?.wipe()
        candidate = chosen
        entry.clear()
        reduce { copy(stage = ChangePinStage.ConfirmPin, enteredCount = 0, failure = null, mismatch = false) }
    }

    /** Step three: a mismatch keeps the new PIN and asks again. A match commits the change. */
    private fun confirmNew() {
        val expected = candidate
        if (expected == null) {
            restart()
            return
        }
        val matched = entry.matches(expected)
        entry.clear()
        if (!matched) {
            reduce { copy(enteredCount = 0, mismatch = true) }
            return
        }
        commit(expected)
    }

    /** The final submission. The current PIN is verified again inside the security layer and the envelope is replaced. */
    private fun commit(newPin: SecretBytes) {
        val current = verifiedCurrent?.snapshot()
        if (current == null) {
            restart()
            return
        }
        reduce { copy(stage = ChangePinStage.Saving, enteredCount = 0, busy = true, failure = null, mismatch = false) }
        work = viewModelScope.launch {
            val outcome = try {
                gateway.changePin(current, newPin)
            } finally {
                current.wipe()
            }
            coroutineContext.ensureActive()
            onCommitted(outcome)
        }
    }

    private fun onCommitted(outcome: Outcome<Unit, SecurityActionFailure>) {
        wipeSecrets()
        when (outcome) {
            is Outcome.Success -> reduce { copy(busy = false) }

            is Outcome.Failure -> when (val failure = outcome.error) {
                // A storage failure is confirmed before the envelope was replaced, so the previous PIN still works.
                SecurityActionFailure.StorageFailure -> reduce {
                    copy(stage = ChangePinStage.Failed(previousPinStillWorks = true), busy = false)
                }

                // The session ended before anything was replaced. The gate shows the auth screens instead.
                SecurityActionFailure.SessionEnded -> reduce {
                    copy(stage = ChangePinStage.Verify, busy = false, failure = null)
                }

                is SecurityActionFailure.Throttled -> {
                    reduce { copy(stage = ChangePinStage.Verify, busy = false, failure = null) }
                    startCountdown(failure.retryAfter)
                }

                SecurityActionFailure.WrongPin -> reduce {
                    copy(stage = ChangePinStage.Verify, busy = false, failure = failure)
                }

                // Anything else is not confirmed: the new PIN may or may not have been activated.
                else -> reduce { copy(stage = ChangePinStage.Failed(previousPinStillWorks = false), busy = false) }
            }
        }
    }

    /** "Try again" after a failed change: back to step one with nothing kept. */
    private fun restart() {
        if (currentState.busy) return
        wipeSecrets()
        countdown?.cancel()
        countdown = null
        reduce { ChangePinUiState(retrySeconds = null) }
    }

    /**
     * Privacy: drops every digit and buffer, and the steps that have not committed. A running commit keeps running, since
     * the security layer decides it atomically; a lock makes it superseded, so the old PIN stays in effect.
     */
    private fun invalidate() {
        if (currentState.stage == ChangePinStage.Saving) {
            entry.clear()
            return
        }
        work?.cancel()
        countdown?.cancel()
        countdown = null
        wipeSecrets()
        reduce {
            copy(
                stage = if (stage is ChangePinStage.Failed) stage else ChangePinStage.Verify,
                enteredCount = 0,
                busy = false,
                failure = null,
                mismatch = false,
                retrySeconds = null,
            )
        }
    }

    private fun wipeSecrets() {
        entry.wipe()
        entry = PinEntryBuffer()
        verifiedCurrent?.wipe()
        verifiedCurrent = null
        candidate?.wipe()
        candidate = null
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
        }
    }

    private companion object {
        val ONE_SECOND: Duration = 1000.milliseconds

        fun ceilSeconds(remaining: Duration): Int = ((remaining.inWholeMilliseconds + 999) / 1000).toInt()
    }
}
