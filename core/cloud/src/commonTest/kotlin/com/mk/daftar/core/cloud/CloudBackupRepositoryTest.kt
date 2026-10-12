@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.cloud

import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAuthOutcome
import com.mk.daftar.core.domain.cloud.CloudBackupLimits
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.domain.cloud.CloudUploadPhase
import com.mk.daftar.core.domain.storage.StorageError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private const val UID = "uid-1"

private class Harness(val store: CloudStore, val fakeStore: FakeCloudStore) {
    val gateway = FakeAccountGateway(UID)
    val snapshots = FakeSnapshotSource()
    val encryptor = FakeEncryptor()
    val repository = CloudBackupRepositoryImpl(
        gateway = gateway,
        service = CloudBackupService(store, fixedRandom()),
        snapshots = snapshots,
        encryptor = encryptor,
        appVersion = "test",
        clock = Clock.System,
    )

    suspend fun seedOneBackup() {
        encryptor.size = 200
        repository.upload("correct horse battery".encodeToByteArray()) {}
    }
}

private fun harness(): Harness {
    val store = FakeCloudStore()
    return Harness(store, store)
}

private fun passwordBytes(): ByteArray = "correct horse battery".encodeToByteArray()

class CloudBackupRepositoryTest {

    @Test
    fun deletionRunsBackupsThenRevocationThenAuthDeletion() = runTest {
        val h = harness()
        h.seedOneBackup()
        assertNotNull(h.repository.deleteAccount().dataOrNull())
        assertEquals(listOf("reauthenticate", "revoke", "deleteAuth", "signOut"), h.gateway.calls)
        assertTrue(h.fakeStore.slots.all { it == null })
        assertEquals("deleting", h.fakeStore.control?.state)
    }

    @Test
    fun anInterruptedDeletionLeavesTheMarkerAndNeedsReauthOnResume() = runTest {
        val h = harness()
        h.seedOneBackup()
        h.gateway.deleteOutcome = CloudAuthOutcome.ReauthRequired
        assertEquals(CloudError.REAUTH_REQUIRED, h.repository.deleteAccount().errorOrNull())
        assertEquals("deleting", h.fakeStore.control?.state)
        assertTrue(h.fakeStore.slots.all { it == null })
    }

    @Test
    fun aRefusedAppleRevocationStopsBeforeTheAccountIsDeleted() = runTest {
        val h = harness()
        h.seedOneBackup()
        h.gateway.revokeOk = false
        assertEquals(CloudError.FAILED, h.repository.deleteAccount().errorOrNull())
        assertTrue("deleteAuth" !in h.gateway.calls)
        assertEquals("deleting", h.fakeStore.control?.state)
    }

    @Test
    fun reauthenticationAsAnotherAccountTouchesNothing() = runTest {
        val h = harness()
        h.seedOneBackup()
        h.gateway.reauthOutcome = CloudAuthOutcome.SignedIn(CloudAccount("someone-else", CloudProvider.APPLE))
        assertEquals(CloudError.CHANGED, h.repository.deleteAccount().errorOrNull())
        assertEquals("active", h.fakeStore.control?.state)
        assertTrue(h.fakeStore.slots.any { it != null })
        assertEquals(listOf("reauthenticate"), h.gateway.calls)
    }

    @Test
    fun aCancelledReauthenticationIsNotAnError() = runTest {
        val h = harness()
        h.seedOneBackup()
        h.gateway.reauthOutcome = CloudAuthOutcome.Cancelled
        assertEquals(CloudError.CANCELLED, h.repository.deleteAccount().errorOrNull())
        assertEquals("active", h.fakeStore.control?.state)
    }

    @Test
    fun anOversizeFileIsRefusedBeforeAnyCloudReplacement() = runTest {
        val h = harness()
        h.seedOneBackup()
        val before = h.fakeStore.slots.toList()
        h.encryptor.size = CloudBackupLimits.MAX_CIPHERTEXT_BYTES + 1
        assertEquals(CloudError.OVERSIZE, h.repository.upload(passwordBytes()) {}.errorOrNull())
        assertEquals(before, h.fakeStore.slots.toList())
    }

    @Test
    fun theExactLimitIsAccepted() = runTest {
        val h = harness()
        h.encryptor.size = CloudBackupLimits.MAX_CIPHERTEXT_BYTES
        assertNotNull(h.repository.upload(passwordBytes()) {}.dataOrNull())
    }

    @Test
    fun aPasswordIsWipedWhateverTheOutcome() = runTest {
        val h = harness()
        h.encryptor.failure = BackupError.AuthenticationFailed
        val password = passwordBytes()
        h.repository.upload(password) {}
        assertTrue(password.all { it == 0.toByte() })
    }

    @Test
    fun uploadReportsItsPhases() = runTest {
        val h = harness()
        val phases = mutableListOf<CloudUploadPhase>()
        h.repository.upload(passwordBytes()) { phases += it }
        assertEquals(listOf(CloudUploadPhase.PREPARING, CloudUploadPhase.COMMITTING), phases)
    }

    @Test
    fun aFailedSnapshotIsReportedWithoutStoredVersions() = runTest {
        val h = harness()
        h.snapshots.result = Outcome.Failure(BackupError.Storage(StorageError.Locked))
        assertEquals(CloudError.FAILED, h.repository.upload(passwordBytes()) {}.errorOrNull())
        assertTrue(h.fakeStore.slots.all { it == null })
    }

    @Test
    fun aResultThatArrivesAfterSignOutIsNotSignedIn() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeCloudStore()
        val gated = object : CloudStore {
            override suspend fun <T> runTransaction(uid: String, body: (CloudTransaction) -> T): T {
                gate.await()
                return fake.runTransaction(uid, body)
            }
        }
        val h = Harness(gated, fake)
        val pending = async { h.repository.listVersions() }
        runCurrent()
        h.repository.signOut()
        gate.complete(Unit)
        assertEquals(CloudError.NOT_SIGNED_IN, pending.await().errorOrNull())
    }

    @Test
    fun aSignedOutSessionCannotListOrUpload() = runTest {
        val h = harness()
        h.repository.signOut()
        assertEquals(CloudError.NOT_SIGNED_IN, h.repository.listVersions().errorOrNull())
        assertEquals(CloudError.NOT_SIGNED_IN, h.repository.upload(passwordBytes()) {}.errorOrNull())
    }

    @Test
    fun signInSucceedsAndCancellationIsTyped() = runTest {
        val h = harness()
        h.gateway.signedIn = false
        assertEquals(CloudProvider.APPLE, assertNotNull(h.repository.signIn().dataOrNull()).provider)
        h.gateway.signedIn = false
        h.gateway.reauthOutcome = null
        assertEquals(CloudProvider.APPLE, h.repository.provider)
    }
}
