package com.mk.daftar.core.cloud

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudBackupLimits
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudDownload
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudListing

/** Supplies random bytes for identifiers. Production draws them from the security layer's RNG. */
fun interface CloudRandom {
    /** Returns [size] random bytes, or null when the platform RNG is unavailable. */
    suspend fun bytes(size: Int): ByteArray?
}

/**
 * The retention rules, pure and tested without a store. Empty slots are used first; otherwise the oldest `createdAt`
 * is replaced, with the slot number as the tie-breaker, so the choice is deterministic on every device.
 */
object CloudSlotPlanner {
    /** The slot the next upload replaces. [slots] has one entry per slot, and null marks an empty slot. */
    fun chooseSlot(slots: List<SlotRecord?>): Int {
        require(slots.size == CloudBackupLimits.SLOT_COUNT)
        val empty = slots.indexOfFirst { it == null }
        if (empty >= 0) return empty
        return slots.indices.minWith(
            compareBy<Int>({ slots[it]?.createdAtMillis ?: Long.MIN_VALUE }).thenBy { it },
        )
    }
}

/** What an upload transaction decided, so the caller reports exactly what the server did. */
internal sealed interface UploadDecision {
    data class Stored(val slot: Int, val revision: Long) : UploadDecision
    data object NeedsControl : UploadDecision
    data object Deleting : UploadDecision
}

/**
 * Every Firestore operation of the cloud feature, expressed as transactions over [CloudStore]. Each method returns a
 * typed [Outcome] and never throws a store exception to the caller. Ciphertext is only ever stored and verified here.
 */
class CloudBackupService(private val store: CloudStore, private val random: CloudRandom) {
    /** Creates the control document once, at revision zero, unless it already exists. */
    suspend fun ensureControl(uid: String): Outcome<CloudAccountState, CloudError> = guarded {
        store.runTransaction(uid) { tx ->
            val existing = tx.readControl()
            if (existing == null) {
                tx.writeControl(ControlRecord(ACTIVE, 0))
                CloudAccountState.ACTIVE
            } else {
                existing.accountState()
            }
        }.let { Outcome.Success(it) }
    }

    /** Reads the account state and the three slots, without writing anything. */
    suspend fun listing(uid: String, account: CloudAccount): Outcome<CloudListing, CloudError> = guarded {
        val read = store.runTransaction(uid) { tx ->
            val state = tx.readControl()?.accountState() ?: CloudAccountState.ACTIVE
            // A deleting account has no readable backups under the rules, so only its control document is read.
            val versions: List<CloudBackupVersion?> = if (state == CloudAccountState.DELETING) {
                List(CloudBackupLimits.SLOT_COUNT) { null }
            } else {
                (0 until CloudBackupLimits.SLOT_COUNT).map { slot -> tx.readSlot(slot)?.toVersion(slot) }
            }
            ReadResult(state, versions)
        }
        Outcome.Success(CloudListing(account, read.state, read.versions))
    }

    /** The control revision now, used to decide whether an uncertain write may be retried. */
    suspend fun controlRevision(uid: String): Outcome<Long?, CloudError> = guarded {
        Outcome.Success(store.runTransaction(uid) { tx -> tx.readControl()?.revision })
    }

    /**
     * Stores [ciphertext] as a new version and replaces the chosen slot, in one transaction with the revision increment.
     * An uncertain commit is reconciled by [backupId]: when that version is present it is success; when it is absent
     * and no newer write happened, the same version is retried once; otherwise the outcome is reported as unknown.
     */
    suspend fun upload(
        uid: String,
        backupId: String,
        ciphertext: ByteArray,
        sha256: String,
    ): Outcome<CloudBackupVersion, CloudError> {
        val revisionBefore = when (val before = controlRevision(uid)) {
            is Outcome.Failure -> return Outcome.Failure(before.error)
            is Outcome.Success -> before.data
        }
        return when (val first = attemptUpload(uid, backupId, ciphertext, sha256)) {
            is Outcome.Success -> first

            is Outcome.Failure -> if (first.error != CloudError.UNKNOWN_OUTCOME) {
                first
            } else {
                reconcileUpload(uid, backupId, ciphertext, sha256, revisionBefore)
            }
        }
    }

    private suspend fun reconcileUpload(
        uid: String,
        backupId: String,
        ciphertext: ByteArray,
        sha256: String,
        revisionBefore: Long?,
    ): Outcome<CloudBackupVersion, CloudError> {
        val current = controlRevision(uid)
        if (current is Outcome.Failure) return Outcome.Failure(CloudError.UNKNOWN_OUTCOME)
        val listed = findVersion(uid, backupId)
        if (listed is Outcome.Failure) return Outcome.Failure(CloudError.UNKNOWN_OUTCOME)
        val found = (listed as Outcome.Success).data
        if (found != null) return Outcome.Success(found)
        val revisionNow = (current as Outcome.Success).data
        // Nothing newer was written, so the ambiguous attempt can be replayed with the same version and bytes once.
        if (revisionNow == revisionBefore) {
            return when (val retry = attemptUpload(uid, backupId, ciphertext, sha256)) {
                is Outcome.Success -> retry

                is Outcome.Failure -> if (retry.error == CloudError.UNKNOWN_OUTCOME) {
                    Outcome.Failure(CloudError.UNKNOWN_OUTCOME)
                } else {
                    retry
                }
            }
        }
        // Newer writes happened after an ambiguous attempt. The old attempt is never replayed.
        return Outcome.Failure(CloudError.UNKNOWN_OUTCOME)
    }

    private suspend fun findVersion(uid: String, backupId: String): Outcome<CloudBackupVersion?, CloudError> = guarded {
        val found = store.runTransaction(uid) { tx ->
            (0 until CloudBackupLimits.SLOT_COUNT).firstNotNullOfOrNull { slot ->
                tx.readSlot(slot)?.takeIf { it.backupId == backupId }?.toVersion(slot)
            }
        }
        Outcome.Success(found)
    }

    private suspend fun attemptUpload(
        uid: String,
        backupId: String,
        ciphertext: ByteArray,
        sha256: String,
    ): Outcome<CloudBackupVersion, CloudError> = guarded {
        val decision = store.runTransaction(uid) { tx ->
            val control = tx.readControl() ?: return@runTransaction UploadDecision.NeedsControl
            if (control.accountState() != CloudAccountState.ACTIVE) return@runTransaction UploadDecision.Deleting
            val slots = (0 until CloudBackupLimits.SLOT_COUNT).map { tx.readSlot(it) }
            val slot = CloudSlotPlanner.chooseSlot(slots)
            val revision = control.revision + 1
            tx.writeSlot(
                slot,
                SlotRecord(
                    backupId = backupId,
                    formatVersion = CloudBackupLimits.FORMAT_VERSION.toLong(),
                    size = ciphertext.size.toLong(),
                    sha256 = sha256,
                    createdAtMillis = null,
                    ciphertext = ciphertext,
                ),
            )
            tx.writeControl(ControlRecord(ACTIVE, revision))
            UploadDecision.Stored(slot, revision)
        }
        when (decision) {
            is UploadDecision.Stored -> Outcome.Success(
                CloudBackupVersion(
                    slot = decision.slot,
                    backupId = backupId,
                    size = ciphertext.size,
                    sha256 = sha256,
                    createdAtMillis = null,
                ),
            )

            UploadDecision.NeedsControl -> Outcome.Failure(CloudError.NOT_SIGNED_IN)

            UploadDecision.Deleting -> Outcome.Failure(CloudError.DELETING)
        }
    }

    /**
     * Reads [version] again and verifies it before returning the bytes. A version replaced on another device is
     * [CloudError.CHANGED]; bytes that fail the size, digest or format check are [CloudError.UNVERIFIED].
     */
    suspend fun download(uid: String, version: CloudBackupVersion): Outcome<CloudDownload, CloudError> = guarded {
        val record = store.runTransaction(uid) { tx -> tx.readSlot(version.slot) }
        if (record == null || record.backupId != version.backupId) return@guarded Outcome.Failure(CloudError.CHANGED)
        val bytes = record.ciphertext ?: return@guarded Outcome.Failure(CloudError.UNVERIFIED)
        val verified = record.formatVersion == CloudBackupLimits.FORMAT_VERSION.toLong() &&
            bytes.size in CloudBackupLimits.MIN_CIPHERTEXT_BYTES..CloudBackupLimits.MAX_CIPHERTEXT_BYTES &&
            record.size == bytes.size.toLong() &&
            record.sha256 == CloudSha256.hexDigest(bytes)
        if (!verified) return@guarded Outcome.Failure(CloudError.UNVERIFIED)
        val current = record.toVersion(version.slot)
        Outcome.Success(CloudDownload(current, bytes))
    }

    /** Deletes [version] only when it is still the stored version in its slot. */
    suspend fun deleteVersion(uid: String, version: CloudBackupVersion): Outcome<Unit, CloudError> = guarded {
        val outcome = store.runTransaction(uid) { tx ->
            val control = tx.readControl() ?: return@runTransaction DeleteDecision.CHANGED
            if (control.accountState() != CloudAccountState.ACTIVE) return@runTransaction DeleteDecision.DELETING
            val record = tx.readSlot(version.slot)
            if (record == null || record.backupId != version.backupId) return@runTransaction DeleteDecision.CHANGED
            tx.deleteSlot(version.slot)
            tx.writeControl(ControlRecord(ACTIVE, control.revision + 1))
            DeleteDecision.DELETED
        }
        when (outcome) {
            DeleteDecision.DELETED -> Outcome.Success(Unit)
            DeleteDecision.CHANGED -> Outcome.Failure(CloudError.CHANGED)
            DeleteDecision.DELETING -> Outcome.Failure(CloudError.DELETING)
        }
    }

    /**
     * Marks the account [CloudAccountState.DELETING] and removes every backup in one transaction. Once the marker is
     * set, repeating the call only removes what remains, so an interrupted deletion can be resumed.
     */
    suspend fun deleteAllBackups(uid: String): Outcome<Unit, CloudError> = guarded {
        store.runTransaction(uid) { tx ->
            val control = tx.readControl()
            // Resuming an interrupted deletion: the marker is already set and no backup can remain, so nothing else is read.
            if (control != null && control.accountState() == CloudAccountState.DELETING) return@runTransaction
            val slots = (0 until CloudBackupLimits.SLOT_COUNT).map { tx.readSlot(it) }
            if (control != null && control.accountState() == CloudAccountState.ACTIVE) {
                tx.writeControl(ControlRecord(DELETING, control.revision + 1))
            }
            slots.forEachIndexed { slot, record -> if (record != null) tx.deleteSlot(slot) }
        }
        Outcome.Success(Unit)
    }

    /** Random bytes for version identifiers. Returns null when the platform RNG is unavailable. */
    suspend fun newBackupId(): String? {
        val bytes = random.bytes(UUID_BYTES) ?: return null
        bytes[UUID_VERSION_INDEX] = ((bytes[UUID_VERSION_INDEX].toInt() and 0x0f) or 0x40).toByte()
        bytes[UUID_VARIANT_INDEX] = ((bytes[UUID_VARIANT_INDEX].toInt() and 0x3f) or 0x80).toByte()
        val hex = CloudSha256.hex(bytes)
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private suspend fun <T> guarded(block: suspend () -> Outcome<T, CloudError>): Outcome<T, CloudError> = try {
        block()
    } catch (failure: CloudStoreException) {
        Outcome.Failure(failure.failure.toCloudError())
    }

    private enum class DeleteDecision { DELETED, CHANGED, DELETING }

    private class ReadResult(val state: CloudAccountState, val versions: List<CloudBackupVersion?>)

    private companion object {
        const val UUID_BYTES = 16
        const val UUID_VERSION_INDEX = 6
        const val UUID_VARIANT_INDEX = 8
    }
}

internal const val ACTIVE = "active"
internal const val DELETING = "deleting"

internal fun ControlRecord.accountState(): CloudAccountState = when (state) {
    ACTIVE -> CloudAccountState.ACTIVE
    DELETING -> CloudAccountState.DELETING
    else -> CloudAccountState.DELETING
}

internal fun SlotRecord.toVersion(slot: Int): CloudBackupVersion = CloudBackupVersion(
    slot = slot,
    backupId = backupId,
    size = size.toInt(),
    sha256 = sha256,
    createdAtMillis = createdAtMillis,
)

internal fun CloudStoreFailure.toCloudError(): CloudError = when (this) {
    CloudStoreFailure.OFFLINE -> CloudError.OFFLINE
    CloudStoreFailure.QUOTA -> CloudError.QUOTA_EXCEEDED
    CloudStoreFailure.DENIED -> CloudError.UNAVAILABLE
    CloudStoreFailure.UNAVAILABLE -> CloudError.UNAVAILABLE
    CloudStoreFailure.UNKNOWN_OUTCOME -> CloudError.UNKNOWN_OUTCOME
}
