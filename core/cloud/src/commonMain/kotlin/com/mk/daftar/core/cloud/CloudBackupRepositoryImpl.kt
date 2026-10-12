package com.mk.daftar.core.cloud

import com.mk.daftar.core.backup.BackupEncryptor
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupSnapshotSource
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountGateway
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudAuthOutcome
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudBackupLimits
import com.mk.daftar.core.domain.cloud.CloudBackupRepository
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudDownload
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudListing
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.domain.cloud.CloudUploadPhase
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

/**
 * The cloud facade. Every operation captures the account generation before it suspends; a result that arrives after a
 * sign-out or a different sign-in is reported as [CloudError.NOT_SIGNED_IN] and is never shown for the new session.
 * A transaction that already committed is reconciled on the next listing, not reported here.
 */
class CloudBackupRepositoryImpl(
    private val gateway: CloudAccountGateway,
    private val service: CloudBackupService,
    private val snapshots: BackupSnapshotSource,
    private val encryptor: BackupEncryptor,
    private val appVersion: String,
    private val clock: Clock,
) : CloudBackupRepository {

    @Volatile
    private var generation: Long = 0

    override val availability: CloudAvailability
        get() = gateway.availability

    override val provider: CloudProvider
        get() = gateway.provider

    override val account: Flow<CloudAccount?>
        get() = gateway.account

    override suspend fun signIn(): Outcome<CloudAccount, CloudError> = when (val outcome = gateway.signIn()) {
        is CloudAuthOutcome.SignedIn -> {
            generation += 1
            Outcome.Success(outcome.account)
        }

        CloudAuthOutcome.Cancelled -> Outcome.Failure(CloudError.CANCELLED)

        CloudAuthOutcome.NoNetwork -> Outcome.Failure(CloudError.OFFLINE)

        CloudAuthOutcome.Unavailable -> Outcome.Failure(CloudError.UNAVAILABLE)

        CloudAuthOutcome.Failed, CloudAuthOutcome.Deleted, CloudAuthOutcome.ReauthRequired ->
            Outcome.Failure(CloudError.FAILED)
    }

    override suspend fun signOut() {
        generation += 1
        gateway.signOut()
    }

    override suspend fun listVersions(): Outcome<CloudListing, CloudError> {
        val account = gateway.currentAccount() ?: return notSignedIn()
        val captured = generation
        return service.listing(account.uid, account).current(captured)
    }

    override suspend fun download(version: CloudBackupVersion): Outcome<CloudDownload, CloudError> {
        val account = gateway.currentAccount() ?: return notSignedIn()
        val captured = generation
        return service.download(account.uid, version).current(captured)
    }

    override suspend fun deleteVersion(version: CloudBackupVersion): Outcome<Unit, CloudError> {
        val account = gateway.currentAccount() ?: return notSignedIn()
        val captured = generation
        return service.deleteVersion(account.uid, version).current(captured)
    }

    override suspend fun upload(
        password: ByteArray,
        onPhase: (CloudUploadPhase) -> Unit,
    ): Outcome<CloudBackupVersion, CloudError> = try {
        uploadWith(password, onPhase)
    } finally {
        password.fill(0)
    }

    private suspend fun uploadWith(
        password: ByteArray,
        onPhase: (CloudUploadPhase) -> Unit,
    ): Outcome<CloudBackupVersion, CloudError> {
        val account = gateway.currentAccount() ?: return notSignedIn()
        val captured = generation
        onPhase(CloudUploadPhase.PREPARING)
        val control = service.ensureControl(account.uid)
        if (control is Outcome.Failure) return control
        if ((control as Outcome.Success).data == CloudAccountState.DELETING) {
            return Outcome.Failure(CloudError.DELETING)
        }
        val snapshot = when (val read = snapshots.read()) {
            is Outcome.Failure -> return Outcome.Failure(CloudError.FAILED)
            is Outcome.Success -> read.data
        }
        // The check happens before any cloud write, so an oversize file never replaces a stored version.
        val ciphertext = when (val encoded = encryptor.encode(snapshot, password, clock.now(), appVersion)) {
            is Outcome.Failure -> return Outcome.Failure(encoded.error.toUploadError())
            is Outcome.Success -> encoded.data
        }
        if (ciphertext.size > CloudBackupLimits.MAX_CIPHERTEXT_BYTES) return Outcome.Failure(CloudError.OVERSIZE)
        if (ciphertext.size < CloudBackupLimits.MIN_CIPHERTEXT_BYTES) return Outcome.Failure(CloudError.FAILED)
        val backupId = service.newBackupId() ?: return Outcome.Failure(CloudError.FAILED)
        if (captured != generation) return notSignedIn()
        onPhase(CloudUploadPhase.COMMITTING)
        val digest = CloudSha256.hexDigest(ciphertext)
        return service.upload(account.uid, backupId, ciphertext, digest).current(captured)
    }

    override suspend fun deleteAccount(): Outcome<Unit, CloudError> {
        val account = gateway.currentAccount() ?: return notSignedIn()
        val captured = generation
        when (val reauth = gateway.reauthenticate()) {
            is CloudAuthOutcome.SignedIn -> if (reauth.account.uid != account.uid) {
                // Another account answered the sign-in. Nothing of the original account is touched.
                return Outcome.Failure(CloudError.CHANGED)
            }

            CloudAuthOutcome.Cancelled -> return Outcome.Failure(CloudError.CANCELLED)

            CloudAuthOutcome.NoNetwork -> return Outcome.Failure(CloudError.OFFLINE)

            CloudAuthOutcome.Unavailable -> return Outcome.Failure(CloudError.UNAVAILABLE)

            CloudAuthOutcome.Failed, CloudAuthOutcome.Deleted, CloudAuthOutcome.ReauthRequired ->
                return Outcome.Failure(CloudError.FAILED)
        }
        // Step 2: the marker and every backup go together. A failure here leaves the account untouched.
        val backups = service.deleteAllBackups(account.uid)
        if (backups is Outcome.Failure) return backups.current(captured)
        // Step 3: Apple authorization first, then the Firebase account. A refused revocation stops here, still resumable.
        if (!gateway.revokeProviderAuthorization()) return Outcome.Failure(CloudError.FAILED)
        return when (gateway.deleteAuthAccount()) {
            CloudAuthOutcome.Deleted -> {
                // Step 4: local sign-in state is cleared; the vault is untouched.
                generation += 1
                gateway.signOut()
                Outcome.Success(Unit)
            }

            CloudAuthOutcome.ReauthRequired -> Outcome.Failure(CloudError.REAUTH_REQUIRED)

            else -> Outcome.Failure(CloudError.FAILED)
        }
    }

    private fun notSignedIn(): Outcome<Nothing, CloudError> = Outcome.Failure(CloudError.NOT_SIGNED_IN)

    private fun <T> Outcome<T, CloudError>.current(captured: Long): Outcome<T, CloudError> =
        if (captured == generation) this else notSignedIn()
}

internal fun BackupError.toUploadError(): CloudError = when (this) {
    BackupError.TooLarge, BackupError.TooManyRecords -> CloudError.OVERSIZE
    else -> CloudError.FAILED
}
