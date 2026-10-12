package com.mk.daftar.feature.auth

import com.mk.daftar.core.security.BiometricCapability
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

    /**
     * First run, step 3: the optional biometric offer. The offer depends on [AuthUiState.biometricOffer]. "Not now"
     * creates a PIN-only vault.
     */
    data object Biometric : AuthPhase

    /** The vault is locked: enter the PIN to open it. The biometric key appears when [AuthUiState.biometricReady]. */
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

/** An informative message about biometrics. It never blocks the PIN path and is never an error toast. */
enum class AuthNotice {
    /** Biometrics are unavailable now, or the device's enrollment changed. The PIN still works. */
    BiometricsUnavailable,

    /** Biometrics are locked out for now. The PIN still works. */
    BiometricsLockedOut,

    /** Enabling biometrics did not complete. The vault was created and opens with the PIN. */
    BiometricsNotEnabled,
}

/** What the biometric offer can say on the first-run screen. */
enum class BiometricOffer {
    /** The platform is being asked. Enabling is not offered yet. */
    Checking,
    Available,
    NotEnrolled,
    Unavailable,
    ;

    companion object {
        fun from(capability: BiometricCapability): BiometricOffer = when (capability) {
            BiometricCapability.Available -> Available
            BiometricCapability.NotEnrolled -> NotEnrolled
            BiometricCapability.Unavailable -> Unavailable
        }
    }
}

/** A long-running operation that owns the screen's input until it ends. */
enum class AuthBusy {
    Creating,
    Unlocking,

    /** Creating the vault and enrolling biometrics for the same key. */
    Enrolling,

    /** The platform biometric prompt is on screen. Its result is owned by the operation, not the screen. */
    UnlockingBiometric,
    ;

    /** Biometric operations survive the prompt's temporary deactivation of the app. */
    val isBiometric: Boolean
        get() = this == Enrolling || this == UnlockingBiometric
}

data class AuthUiState(
    val phase: AuthPhase = AuthPhase.Checking,
    /** Digits entered on the current screen. Never the digits themselves. */
    val enteredCount: Int = 0,
    val busy: AuthBusy? = null,
    val error: AuthError? = null,
    /** Whole seconds left in a backoff penalty, rounded up. Non-null only while the penalty is running. */
    val retrySeconds: Int? = null,
    val biometricOffer: BiometricOffer = BiometricOffer.Checking,
    /** True when the lock screen may offer the biometric key. Never true while a penalty blocks the keypad. */
    val biometricReady: Boolean = false,
    val notice: AuthNotice? = null,
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

    /** Back: from confirmation or the biometric offer to a clean creation. Elsewhere it clears the entry. */
    data object Back : AuthIntent

    /** "Choose another PIN": wipes the candidate and returns to an empty Create. */
    data object RestartCreation : AuthIntent

    /** "Enable biometrics" on the offer: creates the vault and enrolls the same key. */
    data object EnableBiometrics : AuthIntent

    /** "Not now" on the offer: creates a PIN-only vault. */
    data object SkipBiometrics : AuthIntent

    /** The biometric key on the lock screen, tapped explicitly. */
    data object BiometricUnlock : AuthIntent

    /** Forgot PIN? */
    data object ShowRecovery : AuthIntent

    /** "Try PIN again" from Recovery: returns to an empty, status-rechecked Unlock screen. */
    data object LeaveRecovery : AuthIntent

    /** Retry after an unavailable state, or a status read after a return to the screen. */
    data object Recheck : AuthIntent

    /**
     * "Restore encrypted backup" on the forgotten-PIN screen. The restore wizard lives outside this feature, so the
     * gate handles this intent and this view model ignores it.
     */
    data object OpenRestore : AuthIntent

    /** Opens the cloud recovery screen: sign in, choose a stored version, and download it to restore. */
    data object OpenCloudRestore : AuthIntent

    /** Background, inactivity, developer entry: drop every draft now and cancel work that has not finished. */
    data object PrivacyInvalidated : AuthIntent
}

/** No one-shot effects: success is the gate rendering the new generation, not a navigation command. */
typealias AuthEffect = Nothing
