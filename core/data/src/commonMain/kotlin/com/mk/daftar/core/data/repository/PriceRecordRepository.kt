package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.records.RecordRules
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.data.session.missingOrConflict
import com.mk.daftar.core.database.mapper.QuoteKeys
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toRecord
import com.mk.daftar.core.database.mapper.toRemoteEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.repository.isManuallyEntered
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.network.FxNetworkDataSource
import com.mk.daftar.core.network.GoldNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remote quotes and manual quotes. Refreshes are serialized: a second refresh waits for the first, then runs its own
 * fetch against the newer cache. Fetches never run inside a transaction. Manual quotes are never touched by a refresh.
 */
internal class PriceRecordRepository(
    private val host: VaultHost,
    private val gold: GoldNetworkDataSource,
    private val fx: FxNetworkDataSource,
) : PriceRepository {
    private val refreshes = Mutex()

    /**
     * Remote and manual rows are read by two statements. Each is internally consistent; the pair is not a snapshot,
     * so callers must not treat the two halves as one moment in time.
     */
    override fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>> = host.observe { db ->
        combine(db.remoteQuoteDao().observeAll(), db.manualQuoteDao().observeAll()) { remote, manual ->
            QuoteSnapshot(
                remote = remote.map { it.toRecord() },
                manual = manual.map { it.toStored() },
            )
        }
    }

    override suspend fun refresh(): Outcome<RefreshReport, StorageError> = refreshes.withLock {
        host.run { db, stillCurrent ->
            val goldFetch = gold.fetch()
            val fxFetch = fx.fetch()
            if (!stillCurrent()) return@run Outcome.Failure(StorageError.SessionEnded)
            val cached = db.remoteQuoteDao().observeAll().first().map { it.toRecord() }
            val plan = RefreshPlanner.plan(goldFetch, fxFetch, cached)
            if (!stillCurrent()) return@run Outcome.Failure(StorageError.SessionEnded)
            if (plan.writes.isNotEmpty()) {
                db.remoteQuoteDao().upsertAll(
                    plan.writes.map { it.quote.toRemoteEntity(it.nextUpdateAt, it.attributionUrl) },
                )
            }
            Outcome.Success(plan.report)
        }
    }

    override suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> =
        host.write { db ->
            val dao = db.manualQuoteDao()
            val code = QuoteKeys.encode(input.key)
            val now = host.now()
            if (expectedRevision == null) {
                // Create-only: the insert aborts on an existing row, which maps to a conflict.
                dao.insert(input.toEntity(enteredAt = now, pinned = true, revision = RecordRules.FIRST_REVISION))
                return@write Outcome.Success(Unit)
            }
            if (expectedRevision < RecordRules.FIRST_REVISION) return@write Outcome.Failure(StorageError.Conflict)
            val replaced = dao.replaceIfRevision(
                quoteKey = code,
                expectedRevision = expectedRevision,
                value = input.value.toPlainString(),
                enteredSeconds = now.epochSeconds,
                enteredNanos = now.nanosecondsOfSecond,
                note = input.note?.trim()?.ifEmpty { null },
            )
            if (replaced == 1) Outcome.Success(Unit) else Outcome.Failure(missingOrConflict(dao.get(code) != null))
        }

    override suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        host.write { db ->
            if (!key.isManuallyEntered()) return@write Outcome.Failure(StorageError.InvalidInput)
            val dao = db.manualQuoteDao()
            val code = QuoteKeys.encode(key)
            if (dao.unpinIfRevision(code, expectedRevision) == 1) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(missingOrConflict(dao.get(code) != null))
            }
        }

    override suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        host.write { db ->
            if (!key.isManuallyEntered()) return@write Outcome.Failure(StorageError.InvalidInput)
            val dao = db.manualQuoteDao()
            val code = QuoteKeys.encode(key)
            if (dao.deleteIfRevision(code, expectedRevision) == 1) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(missingOrConflict(dao.get(code) != null))
            }
        }
}
