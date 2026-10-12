package com.mk.daftar.core.cloud

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Transaction
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The Android Firestore transport. The cache is memory-only and is configured before the first use, so no document
 * outlives the process. When Firebase is not configured every call fails with [CloudStoreFailure.UNAVAILABLE].
 */
internal class FirestoreCloudStore(private val context: Context) : CloudStore {

    private val firestore: FirebaseFirestore? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) {
            null
        } else {
            FirebaseFirestore.getInstance().apply {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }

    override suspend fun <T> runTransaction(uid: String, body: (CloudTransaction) -> T): T {
        val database = firestore ?: throw CloudStoreException(CloudStoreFailure.UNAVAILABLE)
        val root = database.collection(USERS).document(uid)
        return try {
            database.runTransaction { transaction ->
                body(FirestoreCloudTransaction(transaction, root))
            }.awaitTask()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: CloudStoreException) {
            throw failure
        } catch (failure: FirebaseFirestoreException) {
            throw CloudStoreException(failure.code.toStoreFailure())
        } catch (_: Exception) {
            throw CloudStoreException(CloudStoreFailure.UNKNOWN_OUTCOME)
        }
    }

    private companion object {
        const val USERS = "users"
    }
}

/** Typed reads and writes over one Firestore transaction. Reads come before writes, as Firestore requires. */
private class FirestoreCloudTransaction(private val transaction: Transaction, private val root: DocumentReference) :
    CloudTransaction {

    override fun readControl(): ControlRecord? {
        val snapshot = transaction.get(root)
        if (!snapshot.exists()) return null
        return ControlRecord(
            state = snapshot.getString("state").orEmpty(),
            revision = snapshot.getLong("revision") ?: 0L,
        )
    }

    override fun readSlot(slot: Int): SlotRecord? {
        val snapshot = transaction.get(root.collection(BACKUPS).document(slot.toString()))
        if (!snapshot.exists()) return null
        return SlotRecord(
            backupId = snapshot.getString("backupId").orEmpty(),
            formatVersion = snapshot.getLong("formatVersion") ?: -1L,
            size = snapshot.getLong("size") ?: -1L,
            sha256 = snapshot.getString("sha256").orEmpty(),
            createdAtMillis = snapshot.getTimestamp("createdAt")?.toDate()?.time,
            ciphertext = snapshot.getBlob("ciphertext")?.toBytes(),
        )
    }

    override fun writeControl(record: ControlRecord) {
        transaction.set(
            root,
            mapOf<String, Any>(
                "schemaVersion" to 1L,
                "state" to record.state,
                "revision" to record.revision,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    override fun writeSlot(slot: Int, record: SlotRecord) {
        val ciphertext = checkNotNull(record.ciphertext) { "A backup write needs ciphertext" }
        transaction.set(
            root.collection(BACKUPS).document(slot.toString()),
            mapOf<String, Any>(
                "backupId" to record.backupId,
                "formatVersion" to record.formatVersion,
                "size" to record.size,
                "sha256" to record.sha256,
                "createdAt" to FieldValue.serverTimestamp(),
                "ciphertext" to Blob.fromBytes(ciphertext),
            ),
        )
    }

    override fun deleteSlot(slot: Int) {
        transaction.delete(root.collection(BACKUPS).document(slot.toString()))
    }

    private companion object {
        const val BACKUPS = "backups"
    }
}

private fun FirebaseFirestoreException.Code.toStoreFailure(): CloudStoreFailure = when (this) {
    FirebaseFirestoreException.Code.UNAVAILABLE -> CloudStoreFailure.OFFLINE

    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> CloudStoreFailure.QUOTA

    FirebaseFirestoreException.Code.PERMISSION_DENIED,
    FirebaseFirestoreException.Code.UNAUTHENTICATED,
    -> CloudStoreFailure.DENIED

    else -> CloudStoreFailure.UNKNOWN_OUTCOME
}

/** Waits for a Firestore [Task] and rethrows its failure. */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: IllegalStateException("Firestore task failed"))
        }
    }
}
