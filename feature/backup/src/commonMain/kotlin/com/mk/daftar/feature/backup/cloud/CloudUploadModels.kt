package com.mk.daftar.feature.backup.cloud

import com.mk.daftar.core.backup.BackupPasswordPolicy
import com.mk.daftar.core.backup.BackupPasswordProblem
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/** The steps before an upload: sign-in when no account is signed in, then the backup password. */
enum class CloudUploadStep { SIGN_IN, PASSWORD }

/** What the process is doing now. Indeterminate for both phases: Firestore has no byte progress. */
sealed interface CloudUploadOperation {
    data object Idle : CloudUploadOperation

    data object Preparing : CloudUploadOperation

    data object Committing : CloudUploadOperation

    data class Complete(val version: CloudBackupVersion) : CloudUploadOperation

    data class Failed(val error: CloudError) : CloudUploadOperation
}

/** The password draft lives here only until the upload starts. It is cleared at once and the repository wipes the bytes. */
data class CloudUploadUiState(
    val step: CloudUploadStep = CloudUploadStep.PASSWORD,
    val signingIn: Boolean = false,
    val signInProblem: CloudError? = null,
    val password: String = "",
    val confirm: String = "",
    val submitAttempted: Boolean = false,
    val operation: CloudUploadOperation = CloudUploadOperation.Idle,
) : UiState {
    val passwordProblem: BackupPasswordProblem?
        get() = if (password.isEmpty()) null else BackupPasswordPolicy.problem(password)

    val showPasswordError: Boolean
        get() = passwordProblem != null && (submitAttempted || passwordProblem != BackupPasswordProblem.TOO_SHORT)

    val confirmMismatch: Boolean
        get() = confirm.isNotEmpty() && confirm != password

    val canSubmit: Boolean
        get() = passwordProblem == null && confirm == password && password.isNotEmpty()
}

sealed interface CloudUploadIntent : UiIntent {
    data object SignIn : CloudUploadIntent

    data class PasswordChanged(val text: String) : CloudUploadIntent

    data class ConfirmChanged(val text: String) : CloudUploadIntent

    data object Submit : CloudUploadIntent

    data object Back : CloudUploadIntent

    /** Leaves a finished upload, or clears a failure so the upload can be tried again. */
    data object Acknowledge : CloudUploadIntent

    /** Background or lock: the password drafts are dropped. An upload already committing is not interrupted. */
    data object PrivacyInvalidated : CloudUploadIntent
}

sealed interface CloudUploadEffect : UiEffect {
    data object Close : CloudUploadEffect
}
