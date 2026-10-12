package com.mk.daftar.core.cloud

import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudListing
import com.mk.daftar.core.domain.cloud.CloudProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CloudBackupServiceTest {
    private val uid = "uid-1"
    private val account = CloudAccount(uid, CloudProvider.APPLE)

    private fun service(store: FakeCloudStore) = CloudBackupService(store, fixedRandom())

    private fun bytes(seed: Int, size: Int = 200): ByteArray = ByteArray(size) { (it + seed).toByte() }

    private suspend fun CloudBackupService.put(seed: Int, size: Int = 200): CloudBackupVersion {
        val data = bytes(seed, size)
        val id = assertNotNull(newBackupId())
        return assertNotNull(upload(uid, id, data, CloudSha256.hexDigest(data)).dataOrNull())
    }

    private suspend fun CloudBackupService.listed(): CloudListing = assertNotNull(listing(uid, account).dataOrNull())

    @Test
    fun firstUploadsFillTheThreeSlotsInOrder() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        assertEquals(listOf(0, 1, 2), listOf(service.put(1).slot, service.put(2).slot, service.put(3).slot))
        assertEquals(3L, store.control?.revision)
    }

    @Test
    fun aFourthUploadReplacesTheOldestVersion() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        val first = service.put(1)
        val second = service.put(2)
        service.put(3)
        val fourth = service.put(4)
        assertEquals(first.slot, fourth.slot)
        val listing = service.listed()
        assertTrue(listing.versions.none { it?.backupId == first.backupId })
        assertTrue(listing.versions.any { it?.backupId == second.backupId })
        assertEquals(3, listing.versions.count { it != null })
    }

    @Test
    fun uploadIsRefusedWhileTheAccountIsDeleting() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        service.deleteAllBackups(uid)
        val data = bytes(1)
        val id = assertNotNull(service.newBackupId())
        assertEquals(CloudError.DELETING, service.upload(uid, id, data, CloudSha256.hexDigest(data)).errorOrNull())
    }

    @Test
    fun aConcurrentWriteIsSeenByTheRetriedUpload() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        service.put(1)
        // Another device commits between this device's read and its write: the body runs again on the new state.
        store.racingWrite = {
            val other = bytes(9)
            control = ControlRecord("active", (control?.revision ?: 0) + 1)
            slots[1] =
                SlotRecord("11111111-2222-4333-8444-555555555555", 1, 200, CloudSha256.hexDigest(other), 50, other)
        }
        val version = service.put(2)
        assertEquals(version.slot, store.slots.indexOfFirst { it?.backupId == version.backupId })
        // Two other writes happened (the racing one and this one), so the revision is one above the racing write.
        assertEquals(3L, store.control?.revision)
        assertEquals(3, store.slots.count { it != null })
    }

    @Test
    fun anUncertainCommitThatLandedIsReconciledWithoutAnotherWrite() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        store.uncertainAfterCommit = true
        val data = bytes(4)
        val id = assertNotNull(service.newBackupId())
        val result = service.upload(uid, id, data, CloudSha256.hexDigest(data)).dataOrNull()
        assertEquals(id, assertNotNull(result).backupId)
        assertEquals(1, store.slots.count { it != null })
        assertEquals(1L, store.control?.revision)
    }

    @Test
    fun anAbandonedUncertainCommitIsRetriedOnceWithTheSameIdentifier() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        store.uncertainOnWrite = true
        val data = bytes(5)
        val id = assertNotNull(service.newBackupId())
        val result = service.upload(uid, id, data, CloudSha256.hexDigest(data)).dataOrNull()
        assertEquals(id, assertNotNull(result).backupId)
        assertEquals(1, store.slots.count { it != null })
        assertEquals(1L, store.control?.revision)
    }

    @Test
    fun anUncertainCommitDisplacedByNewerWritesIsNotReplayed() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        service.put(1)
        store.uncertainOnWrite = true
        // Another device writes after the ambiguous attempt: the old attempt must not be replayed over newer state.
        store.afterUncertain = { control = ControlRecord("active", (control?.revision ?: 0) + 1) }
        val data = bytes(6)
        val id = assertNotNull(service.newBackupId())
        val writesBefore = store.writeCount
        val error = service.upload(uid, id, data, CloudSha256.hexDigest(data)).errorOrNull()
        assertEquals(CloudError.UNKNOWN_OUTCOME, error)
        assertEquals(writesBefore, store.writeCount)
        assertTrue(store.slots.none { it?.backupId == id })
    }

    @Test
    fun aDownloadIsVerifiedBeforeItIsReturned() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        val version = service.put(7)
        assertNotNull(service.download(uid, version).dataOrNull())
        val slot = assertNotNull(store.slots[version.slot])
        store.slots[version.slot] = SlotRecord(slot.backupId, 1, slot.size, "f".repeat(64), null, slot.ciphertext)
        assertEquals(CloudError.UNVERIFIED, service.download(uid, version).errorOrNull())
        store.slots[version.slot] = SlotRecord(slot.backupId, 1, 99, slot.sha256, null, slot.ciphertext)
        assertEquals(CloudError.UNVERIFIED, service.download(uid, version).errorOrNull())
        store.slots[version.slot] = SlotRecord(slot.backupId, 2, slot.size, slot.sha256, null, slot.ciphertext)
        assertEquals(CloudError.UNVERIFIED, service.download(uid, version).errorOrNull())
    }

    @Test
    fun aReplacedVersionIsReportedAsChanged() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        val version = service.put(8)
        service.put(9)
        val slot = assertNotNull(store.slots[version.slot])
        store.slots[version.slot] =
            SlotRecord("other-id", 1, slot.size, slot.sha256, slot.createdAtMillis, slot.ciphertext)
        assertEquals(CloudError.CHANGED, service.download(uid, version).errorOrNull())
        assertEquals(CloudError.CHANGED, service.deleteVersion(uid, version).errorOrNull())
    }

    @Test
    fun deletingOneVersionBumpsTheRevisionAndLeavesTheOthers() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        val first = service.put(1)
        val second = service.put(2)
        assertNotNull(service.deleteVersion(uid, first).dataOrNull())
        assertNull(store.slots[first.slot])
        assertEquals(second.backupId, store.slots[second.slot]?.backupId)
        assertEquals(3L, store.control?.revision)
    }

    @Test
    fun deleteAllBackupsMarksDeletingAndRemovesEverySlot() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        service.put(1)
        service.put(2)
        assertNotNull(service.deleteAllBackups(uid).dataOrNull())
        assertEquals("deleting", store.control?.state)
        assertTrue(store.slots.all { it == null })
        assertEquals(CloudAccountState.DELETING, service.listed().state)
    }

    @Test
    fun aResumedDeletionDoesNotBumpTheRevisionAgain() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        service.ensureControl(uid)
        service.put(1)
        service.deleteAllBackups(uid)
        val revision = store.control?.revision
        assertNotNull(service.deleteAllBackups(uid).dataOrNull())
        assertEquals(revision, store.control?.revision)
    }

    @Test
    fun transportFailuresAreMappedToTypedErrors() = runTest {
        val store = FakeCloudStore()
        val service = service(store)
        store.failNext = CloudStoreFailure.OFFLINE
        assertEquals(CloudError.OFFLINE, service.listing(uid, account).errorOrNull())
        store.failNext = CloudStoreFailure.QUOTA
        assertEquals(CloudError.QUOTA_EXCEEDED, service.listing(uid, account).errorOrNull())
        store.failNext = CloudStoreFailure.DENIED
        assertEquals(CloudError.UNAVAILABLE, service.listing(uid, account).errorOrNull())
    }

    @Test
    fun backupIdentifiersAreVersionFourUuids() = runTest {
        val id = assertNotNull(service(FakeCloudStore()).newBackupId())
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id), id)
    }
}
