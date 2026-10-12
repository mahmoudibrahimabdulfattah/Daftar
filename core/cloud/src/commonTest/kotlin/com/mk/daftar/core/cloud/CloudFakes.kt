package com.mk.daftar.core.cloud

import com.mk.daftar.core.backup.BackupEncryptor
import com.mk.daftar.core.backup.BackupError
import com.mk.daftar.core.backup.BackupPreferences
import com.mk.daftar.core.backup.BackupSnapshot
import com.mk.daftar.core.backup.BackupSnapshotSource
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountGateway
import com.mk.daftar.core.domain.cloud.CloudAuthOutcome
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.ThemeMode
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory Firestore. A transaction body reads the current state; a conflict re-runs the body after a racing write,
 * as the SDK retries. The server stamps `createdAt` from a counter, so ordering is deterministic.
 */
class FakeCloudStore : CloudStore {
    var control: ControlRecord? = null
    val slots: Array<SlotRecord?> = arrayOfNulls(3)
    var serverClock: Long = 1_000
    var writeCount: Int = 0
    var transactionCount: Int = 0

    /** The next transaction fails before it runs. */
    var failNext: CloudStoreFailure? = null

    /** The next transaction that writes commits, then reports an unknown outcome. */
    var uncertainAfterCommit: Boolean = false

    /** The next transaction that writes is abandoned with an unknown outcome; [afterUncertain] runs first. */
    var uncertainOnWrite: Boolean = false

    /** Runs once after an abandoned write, as a concurrent device would. */
    var afterUncertain: (FakeCloudStore.() -> Unit)? = null

    /** Runs once before the body re-executes after a conflict; the body then sees this write. */
    var racingWrite: (FakeCloudStore.() -> Unit)? = null

    override suspend fun <T> runTransaction(uid: String, body: (CloudTransaction) -> T): T {
        transactionCount += 1
        failNext?.let {
            failNext = null
            throw CloudStoreException(it)
        }
        val race = racingWrite
        if (race != null) {
            racingWrite = null
            // The first attempt read the old state. Its buffered writes are dropped, then the body runs on new state.
            body(Tx(this))
            race()
        }
        val tx = Tx(this)
        val result = body(tx)
        if (tx.hasWrites && uncertainOnWrite) {
            uncertainOnWrite = false
            afterUncertain?.invoke(this)
            afterUncertain = null
            throw CloudStoreException(CloudStoreFailure.UNKNOWN_OUTCOME)
        }
        tx.commit()
        if (tx.hasWrites && uncertainAfterCommit) {
            uncertainAfterCommit = false
            throw CloudStoreException(CloudStoreFailure.UNKNOWN_OUTCOME)
        }
        return result
    }

    /** Reads see the store; writes are buffered and applied together at commit, as Firestore does. */
    private class Tx(private val store: FakeCloudStore) : CloudTransaction {
        private val pending = mutableListOf<() -> Unit>()

        val hasWrites: Boolean
            get() = pending.isNotEmpty()

        fun commit() {
            pending.forEach { it() }
        }

        override fun readControl(): ControlRecord? = store.control

        /** The rules read the control document to authorize a slot read, so a missing one denies it. */
        override fun readSlot(slot: Int): SlotRecord? {
            if (store.control == null) throw CloudStoreException(CloudStoreFailure.DENIED)
            return store.slots[slot]
        }

        override fun writeControl(record: ControlRecord) {
            pending += {
                store.writeCount += 1
                store.control = record
            }
        }

        override fun writeSlot(slot: Int, record: SlotRecord) {
            pending += {
                store.writeCount += 1
                store.serverClock += 1
                store.slots[slot] = SlotRecord(
                    backupId = record.backupId,
                    formatVersion = record.formatVersion,
                    size = record.size,
                    sha256 = record.sha256,
                    createdAtMillis = store.serverClock,
                    ciphertext = record.ciphertext,
                )
            }
        }

        override fun deleteSlot(slot: Int) {
            pending += {
                store.writeCount += 1
                store.slots[slot] = null
            }
        }
    }
}

/** Scripted sign-in results and a log of every gateway call, in order. */
class FakeAccountGateway(private val uid: String = "uid-1") : CloudAccountGateway {
    val calls = mutableListOf<String>()
    var signedIn: Boolean = true
    var reauthOutcome: CloudAuthOutcome? = null
    var revokeOk: Boolean = true
    var deleteOutcome: CloudAuthOutcome = CloudAuthOutcome.Deleted
    private val state = MutableStateFlow<CloudAccount?>(CloudAccount(uid, CloudProvider.APPLE))

    override val availability: CloudAvailability = CloudAvailability.AVAILABLE
    override val provider: CloudProvider = CloudProvider.APPLE
    override val account: Flow<CloudAccount?> get() = state

    override fun currentAccount(): CloudAccount? = if (signedIn) CloudAccount(uid, provider) else null

    override suspend fun signIn(): CloudAuthOutcome {
        calls += "signIn"
        signedIn = true
        return CloudAuthOutcome.SignedIn(CloudAccount(uid, provider))
    }

    override suspend fun reauthenticate(): CloudAuthOutcome {
        calls += "reauthenticate"
        return reauthOutcome ?: CloudAuthOutcome.SignedIn(CloudAccount(uid, provider))
    }

    override suspend fun revokeProviderAuthorization(): Boolean {
        calls += "revoke"
        return revokeOk
    }

    override suspend fun deleteAuthAccount(): CloudAuthOutcome {
        calls += "deleteAuth"
        return deleteOutcome
    }

    override suspend fun signOut() {
        calls += "signOut"
        signedIn = false
    }
}

/** A snapshot with no records; the store and the encryptor are what the tests exercise. */
fun emptySnapshot(): BackupSnapshot = BackupSnapshot(
    preferences = BackupPreferences(
        themeMode = ThemeMode.DARK,
        language = AppLanguage.EN,
        inactivityTimeout = InactivityTimeout.SECONDS_300,
    ),
    goldHoldings = emptyList(),
    banks = emptyList(),
    bankAccounts = emptyList(),
    cashBalances = emptyList(),
    zakatPayments = emptyList(),
    personalNotes = emptyList(),
    remoteQuotes = emptyList(),
    manualQuotes = emptyList(),
)

class FakeSnapshotSource(var result: Outcome<BackupSnapshot, BackupError> = Outcome.Success(emptySnapshot())) :
    BackupSnapshotSource {
    override suspend fun read(): Outcome<BackupSnapshot, BackupError> = result
}

/** Produces a `.dftar`-shaped file of [size] bytes and wipes the password, as the real codec does. */
class FakeEncryptor(var size: Int = 200) : BackupEncryptor {
    var failure: BackupError? = null
    var lastPassword: ByteArray? = null

    override suspend fun encode(
        snapshot: BackupSnapshot,
        password: ByteArray,
        exportedAt: Instant,
        appVersion: String,
    ): Outcome<ByteArray, BackupError> {
        lastPassword = password
        password.fill(0)
        failure?.let { return Outcome.Failure(it) }
        return Outcome.Success(ByteArray(size) { index -> (index % 251).toByte() })
    }
}

/** The success value, or null. */
fun <D, E> Outcome<D, E>.dataOrNull(): D? = when (this) {
    is Outcome.Success -> data
    is Outcome.Failure -> null
}

/** The failure value, or null. */
fun <D, E> Outcome<D, E>.errorOrNull(): E? = when (this) {
    is Outcome.Success -> null
    is Outcome.Failure -> error
}

/** Random bytes that differ on every call, so every generated identifier is distinct. */
fun fixedRandom(): CloudRandom {
    var calls = 0
    return CloudRandom { size ->
        calls += 1
        ByteArray(size) { index -> (calls * 7 + index * 13).toByte() }
    }
}
