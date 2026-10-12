package com.mk.daftar.feature.backup.cloud

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.backup.BackupPasswordPolicy
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudBackupRepository
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudUploadPhase
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The cloud upload form. It signs in when needed, takes the backup password through the same policy as the local export,
 * and starts one upload. The repository encrypts, checks the cloud limit and performs the transaction; this view model
 * only mirrors its outcome.
 */
class CloudUploadViewModel(private val repository: CloudBackupRepository) :
    MviViewModel<CloudUploadUiState, CloudUploadIntent, CloudUploadEffect>(CloudUploadUiState()) {

    private var work: Job? = null

    init {
        viewModelScope.launch {
            val account = repository.account.first()
            reduce { copy(step = if (account == null) CloudUploadStep.SIGN_IN else CloudUploadStep.PASSWORD) }
        }
    }

    override fun onIntent(intent: CloudUploadIntent) {
        when (intent) {
            CloudUploadIntent.SignIn -> signIn()

            is CloudUploadIntent.PasswordChanged -> reduce { copy(password = intent.text) }

            is CloudUploadIntent.ConfirmChanged -> reduce { copy(confirm = intent.text) }

            CloudUploadIntent.Submit -> submit()

            CloudUploadIntent.Back -> back()

            CloudUploadIntent.Acknowledge -> acknowledge()

            CloudUploadIntent.PrivacyInvalidated -> reduce {
                copy(password = "", confirm = "", submitAttempted = false)
            }
        }
    }

    private fun signIn() {
        if (currentState.signingIn) return
        reduce { copy(signingIn = true, signInProblem = null) }
        viewModelScope.launch {
            when (val result = repository.signIn()) {
                is Outcome.Success -> reduce { copy(signingIn = false, step = CloudUploadStep.PASSWORD) }

                is Outcome.Failure -> reduce {
                    copy(signingIn = false, signInProblem = result.error.takeIf { it != CloudError.CANCELLED })
                }
            }
        }
    }

    private fun submit() {
        val state = currentState
        if (state.operation != CloudUploadOperation.Idle || state.step != CloudUploadStep.PASSWORD) return
        if (!state.canSubmit) {
            reduce { copy(submitAttempted = true) }
            return
        }
        // The repository takes ownership of these bytes and wipes them; the typed draft is cleared at once.
        val bytes = BackupPasswordPolicy.utf8(state.password)
        reduce {
            copy(password = "", confirm = "", submitAttempted = false, operation = CloudUploadOperation.Preparing)
        }
        work = viewModelScope.launch {
            val result = repository.upload(bytes) { phase ->
                reduce {
                    copy(
                        operation = when (phase) {
                            CloudUploadPhase.PREPARING -> CloudUploadOperation.Preparing
                            CloudUploadPhase.COMMITTING -> CloudUploadOperation.Committing
                        },
                    )
                }
            }
            when (result) {
                is Outcome.Success -> reduce { copy(operation = CloudUploadOperation.Complete(result.data)) }
                is Outcome.Failure -> reduce { copy(operation = CloudUploadOperation.Failed(result.error)) }
            }
        }
    }

    private fun back() {
        when (currentState.operation) {
            is CloudUploadOperation.Complete, is CloudUploadOperation.Failed -> {
                reduce { copy(operation = CloudUploadOperation.Idle) }
                emitEffect(CloudUploadEffect.Close)
            }

            CloudUploadOperation.Idle -> when (currentState.step) {
                CloudUploadStep.PASSWORD -> emitEffect(CloudUploadEffect.Close)
                CloudUploadStep.SIGN_IN -> emitEffect(CloudUploadEffect.Close)
            }

            else -> Unit
        }
    }

    private fun acknowledge() {
        when (currentState.operation) {
            is CloudUploadOperation.Complete -> {
                reduce { copy(operation = CloudUploadOperation.Idle) }
                emitEffect(CloudUploadEffect.Close)
            }

            is CloudUploadOperation.Failed -> reduce { copy(operation = CloudUploadOperation.Idle) }

            else -> Unit
        }
    }

    override fun onCleared() {
        work?.cancel()
        super.onCleared()
    }
}
