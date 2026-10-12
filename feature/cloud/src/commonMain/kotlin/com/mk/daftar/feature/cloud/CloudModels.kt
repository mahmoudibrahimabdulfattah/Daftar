package com.mk.daftar.feature.cloud

import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudDownload
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.ui.mvi.UiEffect
import com.mk.daftar.core.ui.mvi.UiIntent
import com.mk.daftar.core.ui.mvi.UiState

/**
 * Where the cloud screen is opened from. [SETTINGS] runs with an open vault and offers upload and deletion.
 * [RECOVERY] runs on the locked surface, from forgotten-PIN recovery, and offers download to restore only.
 */
enum class CloudMode { SETTINGS, RECOVERY }

/** The main content of the screen. Each value says what the screen shows; no value carries a secret. */
sealed interface CloudView {
    /** Firebase is not configured in this build. */
    data object Unavailable : CloudView

    /** No account is signed in on this device. */
    data object SignedOut : CloudView

    /** The versions are being read. */
    data object Loading : CloudView

    /** Signed in, with no stored version. */
    data object Empty : CloudView

    /** Signed in, with at least one stored version. */
    data object Versions : CloudView

    /** A deletion stopped part way. The backups are gone; the account must be deleted to finish. */
    data object DeletionIncomplete : CloudView

    /** The versions could not be read. [CloudUiState.problem] says why. */
    data class Problem(val error: CloudError) : CloudView
}

/** A long action that is in flight. The screen shows an indeterminate indicator; nothing here is a byte count. */
enum class CloudBusy { SIGNING_IN, DOWNLOADING, DELETING_VERSION, DELETING_ACCOUNT }

data class CloudUiState(
    val mode: CloudMode,
    val availability: CloudAvailability,
    val provider: CloudProvider,
    val view: CloudView = CloudView.Loading,
    val versions: List<CloudBackupVersion> = emptyList(),
    val accountState: CloudAccountState = CloudAccountState.ACTIVE,
    val busy: CloudBusy? = null,
    /** A non-blocking failure shown above the content. Null when the last action succeeded. */
    val problem: CloudError? = null,
    /** The version waiting for its one confirmation. */
    val pendingVersion: CloudBackupVersion? = null,
    val confirmAccountDelete: Boolean = false,
) : UiState {
    val canUpload: Boolean
        get() = mode == CloudMode.SETTINGS && availability == CloudAvailability.AVAILABLE

    val canRestore: Boolean
        get() = mode == CloudMode.RECOVERY
}

sealed interface CloudIntent : UiIntent {
    data object Refresh : CloudIntent

    data object SignIn : CloudIntent

    data object SignOut : CloudIntent

    data class RequestDeleteVersion(val version: CloudBackupVersion) : CloudIntent

    data object ConfirmDeleteVersion : CloudIntent

    data object RequestDeleteAccount : CloudIntent

    data object ConfirmDeleteAccount : CloudIntent

    /** Resumes an interrupted deletion. Re-authentication still happens. */
    data object FinishDeletion : CloudIntent

    data class Download(val version: CloudBackupVersion) : CloudIntent

    data object OpenUpload : CloudIntent

    /** The screen is shown again; the listing reloads when an upload was opened from it. */
    data object Returned : CloudIntent

    data object DismissConfirmation : CloudIntent

    data object DismissProblem : CloudIntent

    /** The app is covered or locked: every confirmation and banner is dropped. */
    data object PrivacyInvalidated : CloudIntent
}

sealed interface CloudEffect : UiEffect {
    /** Opens the upload form. */
    data object OpenUpload : CloudEffect

    /** A verified download is ready for the restore wizard. The receiver takes ownership of its bytes. */
    data class RestoreReady(val download: CloudDownload) : CloudEffect
}
