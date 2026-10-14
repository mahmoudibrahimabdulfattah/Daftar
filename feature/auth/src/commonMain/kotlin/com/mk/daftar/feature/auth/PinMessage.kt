package com.mk.daftar.feature.auth

/**
 * The single message the PIN screens show in their message lane. Only one exists at a time, so the lane never stacks
 * panels or changes its rows.
 */
sealed interface PinMessage {
    /** A persistent PIN backoff with [seconds] left, rounded up. */
    data class Backoff(val seconds: Int) : PinMessage

    /** A PIN mismatch, a wrong PIN, or a failed open. */
    data class Failure(val error: AuthError) : PinMessage

    /** A running create or unlock. The caption replaces the explanation in place. */
    data class Busy(val busy: AuthBusy) : PinMessage

    /** A biometric notice on the lock screen. Informative only. */
    data class Notice(val notice: AuthNotice) : PinMessage

    /** The phase's own explanation, shown when nothing else needs the lane. */
    data class Explanation(val phase: AuthPhase, val recovering: Boolean = false) : PinMessage
}

/**
 * Chooses the message for [state], by priority: persistent backoff, then a PIN error, then a busy caption, then a
 * biometric notice, then the phase explanation. A notice is dropped once digits are entered, because a superseded
 * notice must not reappear during a PIN attempt. Phases without a PIN lane have no message.
 */
fun pinMessageFor(state: AuthUiState): PinMessage? {
    if (state.phase != AuthPhase.Create && state.phase != AuthPhase.Confirm && state.phase != AuthPhase.Unlock) {
        return null
    }
    state.retrySeconds?.let { return PinMessage.Backoff(it) }
    state.error?.let { return PinMessage.Failure(it) }
    when (state.busy) {
        AuthBusy.Creating, AuthBusy.Unlocking, AuthBusy.Recovering -> return PinMessage.Busy(state.busy)
        AuthBusy.Enrolling, AuthBusy.UnlockingBiometric, null -> Unit
    }
    state.notice?.takeIf { state.enteredCount == 0 }?.let { return PinMessage.Notice(it) }
    return PinMessage.Explanation(state.phase, state.recovering)
}
