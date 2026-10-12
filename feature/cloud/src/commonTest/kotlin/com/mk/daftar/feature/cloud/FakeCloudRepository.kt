package com.mk.daftar.feature.cloud

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudBackupRepository
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudDownload
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudListing
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.domain.cloud.CloudUploadPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A scripted repository. Each operation records itself; a [gate] holds the operation until the test releases it. */
class FakeCloudRepository(
    override val availability: CloudAvailability = CloudAvailability.AVAILABLE,
    signedIn: Boolean = true,
) : CloudBackupRepository {
    val accountState = MutableStateFlow(if (signedIn) CloudAccount("uid-1", CloudProvider.APPLE) else null)
    override val provider: CloudProvider = CloudProvider.APPLE
    override val account: Flow<CloudAccount?> get() = accountState

    var listing: Outcome<CloudListing, CloudError> = Outcome.Success(emptyListing())
    var signInResult: Outcome<CloudAccount, CloudError> = Outcome.Success(CloudAccount("uid-1", CloudProvider.APPLE))
    var deleteVersionResult: Outcome<Unit, CloudError> = Outcome.Success(Unit)
    var deleteAccountResult: Outcome<Unit, CloudError> = Outcome.Success(Unit)
    var downloadResult: Outcome<CloudDownload, CloudError>? = null
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<String>()

    private suspend fun await(name: String) {
        calls += name
        gate?.await()
    }

    override suspend fun signIn(): Outcome<CloudAccount, CloudError> {
        await("signIn")
        val result = signInResult
        if (result is Outcome.Success) accountState.value = result.data
        return result
    }

    override suspend fun signOut() {
        calls += "signOut"
        accountState.value = null
    }

    override suspend fun listVersions(): Outcome<CloudListing, CloudError> {
        await("list")
        return listing
    }

    override suspend fun download(version: CloudBackupVersion): Outcome<CloudDownload, CloudError> {
        await("download")
        return downloadResult ?: Outcome.Failure(CloudError.FAILED)
    }

    override suspend fun deleteVersion(version: CloudBackupVersion): Outcome<Unit, CloudError> {
        await("deleteVersion")
        return deleteVersionResult
    }

    override suspend fun upload(
        password: ByteArray,
        onPhase: (CloudUploadPhase) -> Unit,
    ): Outcome<CloudBackupVersion, CloudError> = Outcome.Failure(CloudError.FAILED)

    override suspend fun deleteAccount(): Outcome<Unit, CloudError> {
        await("deleteAccount")
        return deleteAccountResult
    }
}

fun emptyListing(state: CloudAccountState = CloudAccountState.ACTIVE): CloudListing =
    CloudListing(CloudAccount("uid-1", CloudProvider.APPLE), state, listOf(null, null, null))

fun version(slot: Int, id: String = "id-$slot"): CloudBackupVersion =
    CloudBackupVersion(slot = slot, backupId = id, size = 200, sha256 = "0".repeat(64), createdAtMillis = 1L)

fun listingWith(
    vararg versions: CloudBackupVersion?,
    state: CloudAccountState = CloudAccountState.ACTIVE,
): CloudListing = CloudListing(CloudAccount("uid-1", CloudProvider.APPLE), state, versions.toList())
