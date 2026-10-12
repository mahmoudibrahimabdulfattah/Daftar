package com.mk.daftar.core.data.storage

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.ids.UuidV4Generator
import com.mk.daftar.core.data.repository.BankRecordRepository
import com.mk.daftar.core.data.repository.CashRecordRepository
import com.mk.daftar.core.data.repository.GoldRecordRepository
import com.mk.daftar.core.data.repository.JournalRecordRepository
import com.mk.daftar.core.data.repository.PriceRecordRepository
import com.mk.daftar.core.data.session.DaftarDatabaseBuilderProvider
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.data.session.VaultSessionEngine
import com.mk.daftar.core.database.DaftarDatabase
import com.mk.daftar.core.database.buildDaftarDatabase
import com.mk.daftar.core.database.driver.DatabaseKeyAccess
import com.mk.daftar.core.database.driver.createSqlCipherDriver
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.network.FxFetch
import com.mk.daftar.core.network.FxFetchFailure
import com.mk.daftar.core.network.FxNetworkDataSource
import com.mk.daftar.core.network.GoldFetch
import com.mk.daftar.core.network.GoldNetworkDataSource
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A key handed to the engine. The bytes are wiped when the engine releases it. */
internal class TestKey(val bytes: ByteArray)

/** Where one test's database lives, and how to remove it afterwards. */
internal class TestLocation(val provider: DaftarDatabaseBuilderProvider, val remove: () -> Unit)

/** The engine as production wires it, with the key supplied directly instead of by the Keystore. */
internal fun storageEngine(provider: DaftarDatabaseBuilderProvider): VaultSessionEngine<TestKey, DaftarDatabase> =
    VaultSessionEngine(
        openDatabase = { key ->
            val access = object : DatabaseKeyAccess {
                override fun <R> useKey(block: (ByteArray) -> R): R = block(key.bytes.copyOf())
            }
            val database = buildDaftarDatabase(provider.builder(), createSqlCipherDriver(access))
            try {
                database.manualQuoteDao().get("USD_EGP")
                database
            } catch (error: Throwable) {
                database.close()
                throw error
            }
        },
        closeDatabase = { database -> database.close() },
        releaseLease = { key -> key.bytes.fill(0) },
        revokeAuthentication = {},
        isUnavailable = { false },
        sessionDispatcher = Dispatchers.Default,
    )

internal class FakeGold(var result: Outcome<GoldFetch, DataError>) : GoldNetworkDataSource {
    override suspend fun fetch(): Outcome<GoldFetch, DataError> = result
}

internal class FakeFx(var result: Outcome<FxFetch, FxFetchFailure>) : FxNetworkDataSource {
    override suspend fun fetch(): Outcome<FxFetch, FxFetchFailure> = result
}

/** The repositories over one engine, with controllable network sources for refresh. */
internal class StorageFixture(val location: TestLocation, val gold: FakeGold, val fx: FakeFx) {
    val engine: VaultSessionEngine<TestKey, DaftarDatabase> = storageEngine(location.provider)
    val host = VaultHost(engine, Clock.System, UuidV4Generator())
    val goldRecords = GoldRecordRepository(host)
    val banks = BankRecordRepository(host)
    val cash = CashRecordRepository(host)
    val journal = JournalRecordRepository(host)
    val prices = PriceRecordRepository(host, gold, fx)

    fun key(byte: Int) = TestKey(ByteArray(32) { (byte + it).toByte() })
}

internal fun <T> Outcome<T, *>.requireSuccess(): T {
    assertIs<Outcome.Success<T>>(this)
    return data
}

internal fun <T> VaultRead<T>.requireReady(): T {
    assertIs<VaultRead.Ready<T>>(this)
    return value
}

/** Waits for the observed value to satisfy [predicate]. Flows invalidate on every write, so this converges. */
internal suspend fun <T> kotlinx.coroutines.flow.Flow<VaultRead<T>>.awaitReady(predicate: (T) -> Boolean): T =
    // Room works on real threads, so the wait uses the real clock rather than runTest's virtual one.
    withContext(Dispatchers.Default) {
        withTimeout(10.seconds) {
            first { read -> read is VaultRead.Ready && predicate(read.value) }.requireReady()
        }
    }

internal fun <T> Stored<T>.unwrap(): T = value
