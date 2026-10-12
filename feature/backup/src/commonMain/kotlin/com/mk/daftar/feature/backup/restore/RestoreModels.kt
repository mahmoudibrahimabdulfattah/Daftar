package com.mk.daftar.feature.backup.restore

import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState
import kotlin.time.Instant

/** Where the restore wizard is. Each value carries only what its screen shows, never a secret. */
sealed interface RestorePhase {
    /** Nothing is open. The next step is picking a file. */
    data object ChooseFile : RestorePhase

    /** A file was copied. The export password is typed here, and only here. */
    data object Password : RestorePhase

    /** The copied file is being decrypted and validated. */
    data object Decrypting : RestorePhase

    /** The backup validated. The review shows counts and the date only, never the records. */
    data object Review : RestorePhase

    /**
     * The new PIN. [confirming] is false while the first PIN is typed and true while it is confirmed. A mismatch keeps
     * the candidate and only clears the confirmation.
     */
    data class NewPin(val confirming: Boolean) : RestorePhase

    /** The replacement needs one explicit confirmation. Cancel is the safe action. */
    data object ConfirmReplacement : RestorePhase

    /** The new vault is being built beside the original. Cancelling here leaves the original untouched. */
    data object Preparing : RestorePhase

    /** The replacement is being committed. It cannot be cancelled. */
    data object Committing : RestorePhase

    /** The original was replaced. The vault stays locked until the new PIN is entered. */
    data object Complete : RestorePhase

    /** The restore stopped. [problem] decides the copy; the original state is in the problem's meaning. */
    data class Failed(val problem: RestoreProblem) : RestorePhase
}

/** Why a restore stopped, in the screen's terms. Wrong password and a damaged file share one value on purpose. */
enum class RestoreProblem {
    /** The password is wrong, or the file is damaged or not a backup. The two are not told apart. */
    Unreadable,

    /** The file was written by a newer Daftar. */
    NewerFormat,

    /** The file could not be opened or copied. Nothing was read. */
    FileUnavailable,

    /** The new vault could not be built or verified. The original data is unchanged. */
    NotReplaced,

    /** The replacement may have taken effect. The next start resolves it; the screen must not claim either outcome. */
    RecoveryRequired,
}

/** The validated facts a review shows: counts and the export date. */
data class RestoreSummary(
    val exportedAt: Instant,
    val goldHoldings: Int,
    val banks: Int,
    val bankAccounts: Int,
    val cashBalances: Int,
    val zakatPayments: Int,
    val personalNotes: Int,
    val remoteQuotes: Int,
    val manualQuotes: Int,
)

data class RestoreUiState(
    val phase: RestorePhase = RestorePhase.ChooseFile,
    /** The export password as typed. Cleared as soon as the decryption starts. */
    val password: String = "",
    val summary: RestoreSummary? = null,
    /** Digits entered on the current PIN screen. Never the digits themselves. */
    val pinCount: Int = 0,
    /** Set when the confirmation did not match the first PIN. */
    val pinMismatch: Boolean = false,
) : UiState {
    /** The password can be submitted once it has at least one character; the decryption decides the rest. */
    val canSubmitPassword: Boolean
        get() = password.isNotEmpty() && phase == RestorePhase.Password

    /** A new PIN is submitted at six digits or more. */
    val canSubmitPin: Boolean
        get() = phase is RestorePhase.NewPin && pinCount in MIN_PIN_DIGITS..MAX_PIN_DIGITS

    companion object {
        const val MIN_PIN_DIGITS = 6
        const val MAX_PIN_DIGITS = 16
    }
}

sealed interface RestoreIntent : UiIntent {
    /** Opens the system picker. Only available before a file is copied. */
    data object ChooseFile : RestoreIntent

    data class PasswordChanged(val text: String) : RestoreIntent

    data object SubmitPassword : RestoreIntent

    /** Continues from the review to the new PIN. */
    data object ContinueToPin : RestoreIntent

    data class Digit(val codePoint: Int) : RestoreIntent

    data object Erase : RestoreIntent

    /** Continue on the first PIN, Confirm on the second. Never sent automatically. */
    data object SubmitPin : RestoreIntent

    /** The one explicit confirmation of the replacement. */
    data object ConfirmReplacement : RestoreIntent

    /** Cancels the replacement at the confirmation step, returning to the review. */
    data object CancelReplacement : RestoreIntent

    /** Cancels a restore that is still being prepared. The original vault is untouched. */
    data object CancelPreparing : RestoreIntent

    /** Back: one step earlier wherever a step has a previous one. */
    data object Back : RestoreIntent

    /** Leaves a finished restore, or clears a failure so a different file can be chosen. */
    data object Acknowledge : RestoreIntent

    /** Leaves the wizard from any step that is not committing. */
    data object Close : RestoreIntent

    /** Background, lock or cover: drop every draft now and stop work that has not reached the commit. */
    data object PrivacyInvalidated : RestoreIntent
}

sealed interface RestoreEffect : UiEffect {
    /** The wizard is finished or was closed. The gate returns to the locked screen. */
    data object Close : RestoreEffect
}
