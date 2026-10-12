package com.mk.daftar.feature.auth

import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/** Which screen of the auth flow is showing. Each value carries only what its screen needs, never a secret. */
sealed interface AuthPhase {
    /** Reading the vault's status. Shows neutral progress, no input. */
    data object Checking : AuthPhase

    /** First run, step 1: choose the PIN. */
    data object Create : AuthPhase

    /** First run, step 2: enter the same PIN again. A mismatch keeps the candidate and sets [AuthError.PinMismatch]. */
    data object Confirm : AuthPhase

    /** First run, step 3: the optional biometric step. Enabling is not available yet. "Not now" creates the vault. */
    data object BiometricStub : AuthPhase

    /** The vault is locked: enter the PIN to open it. */
    data object Unlock : AuthPhase

    /** The forgotten-PIN explanation. Nothing here changes the vault. */
    data object Recovery : AuthPhase

    /** The vault cannot be opened right now or at all. The reason decides the copy and whether retry is offered. */
    data class Unavailable(val reason: AuthUnavailableReason) : AuthPhase
}

/** Why the vault cannot be used. Mapped from security and storage errors; never a raw engine message. */
enum class AuthUnavailableReason {
    UnsupportedHardware,
    DeviceKeyUnavailable,
    CorruptState,
    UnsupportedFormat,

    /** A resource, persistence or storage failure that may succeed on a later attempt. */
    Transient,
}

/** A typed, user-facing failure on the current screen. */
enum class AuthError {
    /** Confirmation did not match the candidate. */
    PinMismatch,

    /** The PIN was wrong and no penalty is running. */
    IncorrectPin,

    /** The vault could not be opened or created right now. The user may try again. */
    OpenFailed,
}

/** A long-running operation that owns the screen's input until it ends. */
enum class AuthBusy {
    Creating,
    Unlocking,
}

data class AuthUiState(
    val phase: AuthPhase = AuthPhase.Checking,
    /** Digits entered on the current screen. Never the digits themselves. */
    val enteredCount: Int = 0,
    val busy: AuthBusy? = null,
    val error: AuthError? = null,
    /** Whole seconds left in a backoff penalty, rounded up. Non-null only while the penalty is running. */
    val retrySeconds: Int? = null,
) : UiState {
    /** Input is accepted only when no operation is running and no penalty blocks the keypad. */
    val inputEnabled: Boolean
        get() = busy == null && retrySeconds == null && phase.acceptsDigits

    val submitEnabled: Boolean
        get() = inputEnabled && enteredCount in MIN_PIN_DIGITS..MAX_PIN_DIGITS

    companion object {
        const val MIN_PIN_DIGITS = 6
        const val MAX_PIN_DIGITS = 16
    }
}

private val AuthPhase.acceptsDigits: Boolean
    get() = this == AuthPhase.Create || this == AuthPhase.Confirm || this == AuthPhase.Unlock

sealed interface AuthIntent : UiIntent {
    /** A digit typed on the keypad or a hardware key, as a code point in any supported numeral script. */
    data class Digit(val codePoint: Int) : AuthIntent

    /** Removes the last digit. */
    data object Erase : AuthIntent

    /** Continue, Confirm, or Unlock, depending on the screen. Never sent automatically. */
    data object Submit : AuthIntent

    /** Back: from confirmation or the biometric step to a clean creation. Elsewhere it clears the entry. */
    data object Back : AuthIntent

    /** "Choose another PIN": wipes the candidate and returns to an empty Create. */
    data object RestartCreation : AuthIntent

    /** "Not now" on the biometric step: creates the vault. */
    data object SkipBiometrics : AuthIntent

    /** Forgot PIN? */
    data object ShowRecovery : AuthIntent

    /** "Try PIN again" from Recovery: returns to an empty, status-rechecked Unlock screen. */
    data object LeaveRecovery : AuthIntent

    /** Retry after an unavailable state, or a status read after a return to the screen. */
    data object Recheck : AuthIntent

    /** Background, inactivity, developer entry: drop every draft now and cancel work that has not finished. */
    data object PrivacyInvalidated : AuthIntent
}

/** No one-shot effects: success is the gate rendering the new generation, not a navigation command. */
typealias AuthEffect = Nothing
