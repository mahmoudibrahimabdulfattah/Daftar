package com.mk.daftar.feature.backup

import com.mk.daftar.core.backup.BackupExportState
import com.mk.daftar.core.backup.BackupPasswordPolicy
import com.mk.daftar.core.backup.BackupPasswordProblem
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/** The two input steps before an export starts. Operation states replace them while an export runs. */
enum class ExportStep { INTRO, PASSWORD }

/**
 * The export screen's state. The password draft lives here only until the export starts; it is then cleared, and the
 * controller takes the bytes. [operation] mirrors the export controller, which outlives this screen.
 */
data class ExportUiState(
    val step: ExportStep = ExportStep.INTRO,
    val password: String = "",
    val confirm: String = "",
    val submitAttempted: Boolean = false,
    val operation: BackupExportState = BackupExportState.Idle,
) : UiState {
    /** The first rule the typed password breaks, or null. */
    val passwordProblem: BackupPasswordProblem?
        get() = if (password.isEmpty()) null else BackupPasswordPolicy.problem(password)

    /** Shown as an error only after a submit attempt, so typing a long password is not interrupted by a warning. */
    val showPasswordError: Boolean
        get() = passwordProblem != null && (submitAttempted || passwordProblem != BackupPasswordProblem.TOO_SHORT)

    val confirmMismatch: Boolean
        get() = confirm.isNotEmpty() && confirm != password

    val canSubmit: Boolean
        get() = passwordProblem == null && confirm == password && password.isNotEmpty()
}

sealed interface ExportIntent : UiIntent {
    data object Continue : ExportIntent

    data class PasswordChanged(val text: String) : ExportIntent

    data class ConfirmChanged(val text: String) : ExportIntent

    data object Submit : ExportIntent

    /** Back from the password step returns to the intro; back from the intro leaves the screen. */
    data object Back : ExportIntent

    /** Cancels while the data is being prepared. The picker is modal and is dismissed by the system. */
    data object CancelPreparing : ExportIntent

    /** Leaves a finished export, or clears a failure so a new location can be chosen. */
    data object Acknowledge : ExportIntent
}

sealed interface ExportEffect : UiEffect {
    /** Leaves the export screen. */
    data object Close : ExportEffect
}
