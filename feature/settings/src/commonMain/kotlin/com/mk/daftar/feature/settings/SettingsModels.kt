package com.mk.daftar.feature.settings

import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.domain.repository.UserPreferences
import com.mk.daftar.core.security.BiometricCapability
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import kotlin.time.Duration

/** The app's version as the build reports it. Shared reads it from the platform package; nothing is invented here. */
data class AppBuildInfo(val versionName: String, val buildNumber: String)

/** The slot state the settings screen can read without opening the vault. [Unreadable] means the read itself failed. */
enum class BiometricSlotFact { Absent, Valid, Invalid, Unreadable }

/** Raw biometric facts from the platform and the security layer. The screen never decides availability from them alone. */
data class BiometricFacts(val slot: BiometricSlotFact, val capability: BiometricCapability)

/**
 * Whether biometrics can be used now. Platform enrollment comes first, then the stored key, so an unenrolled device is
 * never mistaken for a broken key. [Unavailable] is temporary: hardware busy, locked out, or unreadable state.
 */
enum class BiometricAvailability { Ready, NotEnrolled, Unavailable, Invalidated }

/** Whether biometric unlock is on, and what the platform says about it right now. */
data class BiometricSettings(val enabled: Boolean, val availability: BiometricAvailability) {
    /** Enabling asks for the PIN and the biometric prompt. It needs an enrolled device and a valid platform. */
    val canEnable: Boolean
        get() = availability == BiometricAvailability.Ready || availability == BiometricAvailability.Invalidated
}

/**
 * Reconciles the stored preference with the stored key and the platform. The preference alone never claims that
 * biometrics work: it counts only when the key slot is valid.
 */
fun biometricSettingsFor(preferenceEnabled: Boolean, facts: BiometricFacts): BiometricSettings {
    val availability = when {
        facts.capability == BiometricCapability.NotEnrolled -> BiometricAvailability.NotEnrolled
        facts.capability == BiometricCapability.Unavailable -> BiometricAvailability.Unavailable
        facts.slot == BiometricSlotFact.Unreadable -> BiometricAvailability.Unavailable
        facts.slot == BiometricSlotFact.Invalid -> BiometricAvailability.Invalidated
        else -> BiometricAvailability.Ready
    }
    return BiometricSettings(
        enabled = preferenceEnabled && facts.slot == BiometricSlotFact.Valid,
        availability = availability,
    )
}

/**
 * Why a security action did not complete, in the settings feature's terms. It never carries a PIN, a key or a platform
 * message. [SessionEnded] means the vault locked first, so the screen gives way to the auth screens.
 */
sealed interface SecurityActionFailure {
    /** The current PIN is wrong. The persistent backoff applies to the next attempt. */
    data object WrongPin : SecurityActionFailure

    /** Attempts are paused until [retryAfter] has elapsed. */
    data class Throttled(val retryAfter: Duration) : SecurityActionFailure

    /** The user dismissed the biometric prompt. The PIN still works and nothing changed. */
    data object Cancelled : SecurityActionFailure

    data object BiometricLockedOut : SecurityActionFailure

    data object BiometricNotEnrolled : SecurityActionFailure

    data object BiometricUnavailable : SecurityActionFailure

    /** The device's enrollment changed, or the app's key was invalidated. The slot was removed. */
    data object BiometricInvalidated : SecurityActionFailure

    /** Security state could not be written. The previous state stays in effect. */
    data object StorageFailure : SecurityActionFailure

    /** Argon2id could not allocate its memory. Nothing changed, and it never counts as a wrong PIN. */
    data object InsufficientMemory : SecurityActionFailure

    /** The vault locked while the action was in flight. Nothing new was committed. */
    data object SessionEnded : SecurityActionFailure

    /** Any other failure. The screen shows a neutral message and changes nothing. */
    data object Failed : SecurityActionFailure
}

/** The preference sheets on the preferences screen. A sheet is open only while its value is being chosen. */
enum class PreferenceSheet { Theme, Language, Timeout }

data class PreferencesUiState(
    /** The last committed preferences. Null until the first read; the screen shows defaults until then. */
    val preferences: UserPreferences? = null,
    val unreadable: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val sheet: PreferenceSheet? = null,
) : UiState {
    val shown: UserPreferences get() = preferences ?: UserPreferences()
}

sealed interface PreferencesIntent : UiIntent {
    data class OpenSheet(val sheet: PreferenceSheet) : PreferencesIntent

    data object CloseSheet : PreferencesIntent

    data class SelectTheme(val mode: ThemeMode) : PreferencesIntent

    data class SelectLanguage(val language: AppLanguage) : PreferencesIntent

    data class SelectTimeout(val timeout: InactivityTimeout) : PreferencesIntent

    data object DismissSaveFailure : PreferencesIntent
}

/** No one-shot effects: a choice applies through the committed preference, and Done only returns. */
typealias PreferencesEffect = Nothing

/** The biometric PIN prompt, shown only while enabling biometrics from settings. */
data class BiometricPinSheet(
    val enteredCount: Int = 0,
    val failure: SecurityActionFailure? = null,
    val retrySeconds: Int? = null,
    val busy: Boolean = false,
) {
    val inputEnabled: Boolean
        get() = !busy && retrySeconds == null

    val submitEnabled: Boolean
        get() = inputEnabled && enteredCount in MIN_PIN_DIGITS..MAX_PIN_DIGITS
}

data class SettingsUiState(
    val preferences: UserPreferences? = null,
    val unreadable: Boolean = false,
    /** Null until the platform and the stored key have been checked. */
    val biometric: BiometricSettings? = null,
    val biometricBusy: Boolean = false,
    /** The last biometric result that the row should explain. Null when there is nothing to say. */
    val biometricFailure: SecurityActionFailure? = null,
    val biometricSheet: BiometricPinSheet? = null,
    /** Whether a recovery key is set up. Null until read, or when it cannot be read. */
    val recoveryActive: Boolean? = null,
) : UiState

sealed interface SettingsIntent : UiIntent {
    /** The switch was turned on (opens the PIN prompt) or off (turns biometrics off at once). */
    data class SetBiometricEnabled(val enabled: Boolean) : SettingsIntent

    data class Digit(val codePoint: Int) : SettingsIntent

    data object Erase : SettingsIntent

    data object Submit : SettingsIntent

    data object DismissBiometricSheet : SettingsIntent

    /** Reads the platform again, for example after the user returns from device settings. */
    data object Refresh : SettingsIntent

    data object PrivacyInvalidated : SettingsIntent
}

typealias SettingsEffect = Nothing

/** The PIN change steps. Each is internal state of the screen, never a route. */
sealed interface ChangePinStage {
    /** Step 1: the current PIN. The verified value is kept until the final submission. */
    data object Verify : ChangePinStage

    /** Step 2: the new PIN, at least six digits. */
    data object NewPin : ChangePinStage

    /** Step 3: the new PIN again. A mismatch keeps the candidate and asks for the confirmation only. */
    data object ConfirmPin : ChangePinStage

    /** The change is being committed. Input is paused. */
    data object Saving : ChangePinStage

    /**
     * The change did not complete. [previousPinStillWorks] is true only for a failure confirmed before the envelope was
     * replaced, so the old PIN is known to work.
     */
    data class Failed(val previousPinStillWorks: Boolean) : ChangePinStage

    /** Argon2id could not allocate its memory. The PIN has not been changed; the screen offers a retry from step one. */
    data object MemoryUnavailable : ChangePinStage
}

data class ChangePinUiState(
    val stage: ChangePinStage = ChangePinStage.Verify,
    val enteredCount: Int = 0,
    val busy: Boolean = false,
    val failure: SecurityActionFailure? = null,
    val mismatch: Boolean = false,
    val retrySeconds: Int? = null,
) : UiState {
    private val acceptsDigits: Boolean
        get() = stage == ChangePinStage.Verify || stage == ChangePinStage.NewPin || stage == ChangePinStage.ConfirmPin

    val inputEnabled: Boolean
        get() = acceptsDigits && !busy && retrySeconds == null

    val submitEnabled: Boolean
        get() = inputEnabled && enteredCount in MIN_PIN_DIGITS..MAX_PIN_DIGITS
}

sealed interface ChangePinIntent : UiIntent {
    data class Digit(val codePoint: Int) : ChangePinIntent

    data object Erase : ChangePinIntent

    data object Submit : ChangePinIntent

    /** "Try again" after a failed change: starts again at the current PIN. */
    data object Restart : ChangePinIntent

    /** The app is covered or locking: drops every digit and buffer, unless the commit is already running. */
    data object PrivacyInvalidated : ChangePinIntent
}

typealias ChangePinEffect = Nothing

/** The PIN length rules the keypad and the security layer share. */
internal const val MIN_PIN_DIGITS: Int = 6
internal const val MAX_PIN_DIGITS: Int = 16
