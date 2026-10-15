package com.mk.daftar.core.domain.cloud

import com.mk.daftar.core.common.Outcome
import kotlinx.coroutines.flow.Flow

// The optional cloud backup contracts. Features depend only on these types: they never see a Firebase object, a
// document path or a raw SDK message. Only ciphertext ever crosses this boundary.

/** The sign-in provider of this platform: Google on Android and desktop. Accounts are never linked. */
enum class CloudProvider { GOOGLE, APPLE }

/**
 * An authenticated account. The UID is the only identity used. [email] is set only where the platform shows which
 * account is signed in (desktop, where sign-in happens in the browser); it is never stored.
 */
data class CloudAccount(val uid: String, val provider: CloudProvider, val email: String? = null)

/**
 * How this platform signs in. [IN_APP] is a system sheet over the app (Android). [BROWSER] hands off to the system
 * browser and waits, cancellably, for it to return (desktop).
 */
enum class CloudSignInHandoff { IN_APP, BROWSER }

/** Whether cloud backups can run in this build. Missing Firebase configuration is [Unavailable], never a crash. */
enum class CloudAvailability { AVAILABLE, UNAVAILABLE }

/** The result of a sign-in or a re-authentication. */
sealed interface CloudAuthOutcome {
    data class SignedIn(val account: CloudAccount) : CloudAuthOutcome
    data object Cancelled : CloudAuthOutcome
    data object NoNetwork : CloudAuthOutcome
    data object Unavailable : CloudAuthOutcome
    data object Failed : CloudAuthOutcome

    /** The Firebase account was deleted. Only [CloudAccountGateway.deleteAuthAccount] returns this. */
    data object Deleted : CloudAuthOutcome

    /** The provider needs a fresh sign-in before the account can be deleted. */
    data object ReauthRequired : CloudAuthOutcome

    /** Browser hand-off only: the browser did not come back in time. */
    data object TimedOut : CloudAuthOutcome

    /** Browser hand-off only: no browser could be opened. */
    data object BrowserUnavailable : CloudAuthOutcome

    /** Browser hand-off only: the owner declined the consent page. */
    data object Declined : CloudAuthOutcome
}

/** The account control state. [DELETING] blocks uploads and is never reversed. */
enum class CloudAccountState { ACTIVE, DELETING }

/**
 * One stored version. [slot] is one of the three fixed storage slots, [backupId] identifies this version, and [size]
 * and [sha256] describe the complete `.dftar` file.
 */
data class CloudBackupVersion(
    val slot: Int,
    val backupId: String,
    val size: Int,
    val sha256: String,
    val createdAtMillis: Long?,
)

/** The bounds the client and the security rules both enforce. */
object CloudBackupLimits {
    /** The largest `.dftar` file stored in the cloud, including its header and tag. */
    const val MAX_CIPHERTEXT_BYTES: Int = 786_432

    /** The smallest possible `.dftar` file: the 68-byte header plus the 16-byte tag. */
    const val MIN_CIPHERTEXT_BYTES: Int = 84

    /** The fixed number of retained versions. */
    const val SLOT_COUNT: Int = 3

    /** The version of the stored document layout. */
    const val FORMAT_VERSION: Int = 1
}

/** The account and its three slots, read together. A null entry is an empty slot. */
data class CloudListing(
    val account: CloudAccount,
    val state: CloudAccountState,
    val versions: List<CloudBackupVersion?>,
) {
    val isEmpty: Boolean
        get() = versions.all { it == null }
}

/** A downloaded, verified `.dftar` file. Its bytes belong to the caller, who must wipe them after use. */
class CloudDownload(val version: CloudBackupVersion, val ciphertext: ByteArray)

/** The phases of an upload, for an indeterminate progress indicator. Firestore has no byte-level progress. */
enum class CloudUploadPhase { PREPARING, COMMITTING }

/** Typed failures. Each value names the situation, not the SDK that reported it. */
enum class CloudError {
    /** The user cancelled a sign-in or a re-authentication. Not shown as an error. */
    CANCELLED,

    /** The device has no network, or the server could not be reached. The local data is unchanged. */
    OFFLINE,

    /** The shared project's free quota is exhausted for now. */
    QUOTA_EXCEEDED,

    /** No account is signed in, or the account changed while an operation ran. */
    NOT_SIGNED_IN,

    /** Cloud backups are not configured or the service refused the app (App Check). */
    UNAVAILABLE,

    /** The encrypted file is larger than the cloud limit. Nothing was sent. */
    OVERSIZE,

    /** The selected version was replaced or deleted on another device. */
    CHANGED,

    /** The downloaded bytes do not match the stored size or digest. */
    UNVERIFIED,

    /** The account is being deleted. Uploads and restores are blocked. */
    DELETING,

    /** A write may or may not have been applied. The list must be refreshed before anything else. */
    UNKNOWN_OUTCOME,

    /** The provider needs a fresh sign-in before the account can be deleted. */
    REAUTH_REQUIRED,

    /** Anything else. Nothing about the failure is shown beyond this value. */
    FAILED,

    /** Browser hand-off only: the sign-in attempt expired before the browser returned. */
    TIMED_OUT,

    /** Browser hand-off only: the system browser could not be opened. */
    BROWSER_UNAVAILABLE,

    /** Browser hand-off only: the owner declined the consent page. */
    DECLINED,
}

/** Signs the platform account in and out. Implemented per platform; the repository is the only caller. */
interface CloudAccountGateway {
    val availability: CloudAvailability

    /** The provider this platform offers. */
    val provider: CloudProvider

    /** How sign-in reaches the provider on this platform. */
    val signInHandoff: CloudSignInHandoff
        get() = CloudSignInHandoff.IN_APP

    /** The signed-in account, or null. Emits on every change. */
    val account: Flow<CloudAccount?>

    fun currentAccount(): CloudAccount?

    suspend fun signIn(): CloudAuthOutcome

    /** Signs the same provider in again, to prove a fresh session for the same UID before deleting. */
    suspend fun reauthenticate(): CloudAuthOutcome

    /** Revokes the provider's authorization of this app (Apple). Returns true once it is revoked or not needed. */
    suspend fun revokeProviderAuthorization(): Boolean

    /** Deletes the Firebase account. Returns [CloudAuthOutcome.Deleted] or [CloudAuthOutcome.ReauthRequired]/Failed. */
    suspend fun deleteAuthAccount(): CloudAuthOutcome

    /** Signs out and clears platform sign-in state. Never touches local data. */
    suspend fun signOut()
}

/** The use-case facade used by the cloud and backup features. Every method is safe to call from the main thread. */
interface CloudBackupRepository {
    val availability: CloudAvailability
    val provider: CloudProvider
    val signInHandoff: CloudSignInHandoff
        get() = CloudSignInHandoff.IN_APP
    val account: Flow<CloudAccount?>

    suspend fun signIn(): Outcome<CloudAccount, CloudError>

    suspend fun signOut()

    /** Reads the account state and the three slots. Ciphertext is read but never kept beyond the listing. */
    suspend fun listVersions(): Outcome<CloudListing, CloudError>

    /** Re-reads [version]'s slot and verifies its size, digest and format before returning the bytes. */
    suspend fun download(version: CloudBackupVersion): Outcome<CloudDownload, CloudError>

    /** Deletes [version] only when it is still the stored version. */
    suspend fun deleteVersion(version: CloudBackupVersion): Outcome<Unit, CloudError>

    /**
     * Encrypts a fresh snapshot with [password] and stores it, replacing the oldest version when all slots are used.
     * [password] is wiped before this returns, whatever the outcome.
     */
    suspend fun upload(
        password: ByteArray,
        onPhase: (CloudUploadPhase) -> Unit,
    ): Outcome<CloudBackupVersion, CloudError>

    /** Deletes every version, then the account. Re-authenticates first and can be resumed after an interruption. */
    suspend fun deleteAccount(): Outcome<Unit, CloudError>
}
