package com.mk.daftar.feature.cloud

import androidx.lifecycle.viewModelScope
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudBackupRepository
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudListing
import com.mk.daftar.core.ui.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The cloud backup screen. It owns sign-in state, the listing and the confirmations, and asks the repository for every
 * network action. Results from a session that has since ended are dropped by the repository, so this view model only
 * mirrors what the repository reports.
 */
class CloudViewModel(private val repository: CloudBackupRepository, mode: CloudMode) :
    MviViewModel<CloudUiState, CloudIntent, CloudEffect>(
        CloudUiState(
            mode = mode,
            availability = repository.availability,
            provider = repository.provider,
        ),
    ) {
    private var work: Job? = null

    init {
        if (repository.availability == CloudAvailability.UNAVAILABLE) {
            reduce { copy(view = CloudView.Unavailable) }
        } else {
            start { loadCurrentAccount() }
        }
    }

    override fun onIntent(intent: CloudIntent) {
        when (intent) {
            CloudIntent.Refresh -> start { loadListing() }

            CloudIntent.SignIn -> start { signIn() }

            CloudIntent.SignOut -> start { signOut() }

            is CloudIntent.RequestDeleteVersion -> reduce {
                copy(pendingVersion = intent.version, confirmAccountDelete = false)
            }

            CloudIntent.ConfirmDeleteVersion -> start { deleteVersion() }

            CloudIntent.RequestDeleteAccount -> reduce { copy(confirmAccountDelete = true, pendingVersion = null) }

            CloudIntent.ConfirmDeleteAccount -> start { deleteAccount() }

            CloudIntent.FinishDeletion -> start { deleteAccount() }

            // Download is offered only on the recovery surface and upload only from Settings, so a stray intent is ignored.
            is CloudIntent.Download -> if (currentState.canRestore) start { download(intent.version) }

            CloudIntent.OpenUpload -> if (currentState.canUpload) emitEffect(CloudEffect.OpenUpload)

            CloudIntent.DismissConfirmation -> reduce { copy(pendingVersion = null, confirmAccountDelete = false) }

            CloudIntent.DismissProblem -> reduce { copy(problem = null) }

            CloudIntent.PrivacyInvalidated -> reduce { copy(pendingVersion = null, confirmAccountDelete = false) }
        }
    }

    /** Runs one action at a time. A new action replaces a waiting one, so a stale result never overwrites a newer one. */
    private fun start(block: suspend () -> Unit) {
        if (currentState.busy != null && work?.isActive == true) return
        work = viewModelScope.launch { block() }
    }

    private suspend fun loadCurrentAccount() {
        val account = repository.account.first()
        if (account == null) {
            showSignedOut()
        } else {
            loadListing()
        }
    }

    /** Reloads the listing. [keepProblem] keeps the failure that caused the reload visible above the content. */
    private suspend fun loadListing(keepProblem: Boolean = false) {
        reduce {
            copy(
                view = if (versions.isEmpty()) CloudView.Loading else view,
                problem = if (keepProblem) problem else null,
            )
        }
        when (val result = repository.listVersions()) {
            is Outcome.Success -> showListing(result.data)
            is Outcome.Failure -> failListing(result.error)
        }
    }

    private fun showListing(listing: CloudListing) {
        val present = listing.versions.filterNotNull()
        val view = when {
            listing.state == CloudAccountState.DELETING -> CloudView.DeletionIncomplete
            present.isEmpty() -> CloudView.Empty
            else -> CloudView.Versions
        }
        reduce {
            copy(
                view = view,
                versions = present,
                accountState = listing.state,
                busy = null,
            )
        }
    }

    private fun failListing(error: CloudError) {
        when (error) {
            CloudError.NOT_SIGNED_IN -> showSignedOut()

            CloudError.OFFLINE, CloudError.QUOTA_EXCEEDED, CloudError.UNKNOWN_OUTCOME, CloudError.UNAVAILABLE,
            CloudError.FAILED,
            -> reduce { copy(view = CloudView.Problem(error), problem = error, busy = null) }

            else -> reduce { copy(problem = error, busy = null) }
        }
    }

    private fun showSignedOut() {
        reduce {
            copy(
                view = CloudView.SignedOut,
                versions = emptyList(),
                accountState = CloudAccountState.ACTIVE,
                busy = null,
                pendingVersion = null,
                confirmAccountDelete = false,
            )
        }
    }

    private suspend fun signIn() {
        reduce { copy(busy = CloudBusy.SIGNING_IN, problem = null) }
        when (val result = repository.signIn()) {
            is Outcome.Success -> loadListing()

            is Outcome.Failure -> when (result.error) {
                // A cancelled sheet is a choice, not a failure.
                CloudError.CANCELLED -> reduce { copy(busy = null) }

                else -> reduce { copy(busy = null, problem = result.error) }
            }
        }
    }

    private suspend fun signOut() {
        repository.signOut()
        showSignedOut()
    }

    private suspend fun deleteVersion() {
        val version = currentState.pendingVersion ?: return
        reduce { copy(pendingVersion = null, busy = CloudBusy.DELETING_VERSION, problem = null) }
        when (val result = repository.deleteVersion(version)) {
            is Outcome.Success -> loadListing()

            is Outcome.Failure -> {
                reduce { copy(busy = null, problem = result.error) }
                // A replaced or deleted version means the list is stale: reload so the user sees the current state.
                if (result.error == CloudError.CHANGED || result.error == CloudError.UNKNOWN_OUTCOME) {
                    loadListing(keepProblem = true)
                }
            }
        }
    }

    private suspend fun deleteAccount() {
        reduce { copy(confirmAccountDelete = false, busy = CloudBusy.DELETING_ACCOUNT, problem = null) }
        when (val result = repository.deleteAccount()) {
            is Outcome.Success -> showSignedOut()

            is Outcome.Failure -> when (result.error) {
                CloudError.CANCELLED -> reduce { copy(busy = null) }

                CloudError.NOT_SIGNED_IN -> showSignedOut()

                else -> {
                    reduce { copy(busy = null, problem = result.error) }
                    // An interruption after the backups were removed is shown as an incomplete deletion.
                    loadListing()
                }
            }
        }
    }

    private suspend fun download(version: CloudBackupVersion) {
        reduce { copy(busy = CloudBusy.DOWNLOADING, problem = null) }
        when (val result = repository.download(version)) {
            is Outcome.Success -> {
                reduce { copy(busy = null) }
                emitEffect(CloudEffect.RestoreReady(result.data))
            }

            is Outcome.Failure -> {
                reduce { copy(busy = null, problem = result.error) }
                if (result.error == CloudError.CHANGED) loadListing(keepProblem = true)
            }
        }
    }

    override fun onCleared() {
        work?.cancel()
        super.onCleared()
    }
}
