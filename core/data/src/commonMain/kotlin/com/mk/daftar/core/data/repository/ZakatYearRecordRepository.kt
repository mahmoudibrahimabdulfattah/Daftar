package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.database.DaftarDatabase
import com.mk.daftar.core.database.mapper.startYearRow
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toModel
import com.mk.daftar.core.database.mapper.trackingStartYear
import com.mk.daftar.core.domain.repository.ZakatTrackingState
import com.mk.daftar.core.domain.repository.ZakatYearRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.domain.zakat.ZakatYearRules
import com.mk.daftar.core.model.HIJRI_MAX_OVERRIDE_DAYS
import com.mk.daftar.core.model.HIJRI_MAX_YEAR
import com.mk.daftar.core.model.HIJRI_MIN_YEAR
import com.mk.daftar.core.model.ZakatYear
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/**
 * The declarations and their tracking row. Each mutation is a single statement or one transaction, so a failure leaves
 * the committed state unchanged. The clock is read only for the time of a mark.
 */
internal class ZakatYearRecordRepository(private val host: VaultHost) : ZakatYearRepository {
    override fun observeTracking(): Flow<VaultRead<ZakatTrackingState>> = host.observe { db ->
        val dao = db.zakatYearDao()
        combine(dao.observeYears(), dao.observeTracking()) { years, tracking ->
            ZakatTrackingState(tracking.trackingStartYear(), years.map { it.toModel() })
        }
    }

    override suspend fun ensureTracking(today: LocalDate): Outcome<ZakatTrackingState, StorageError> =
        host.write { db ->
            val dao = db.zakatYearDao()
            val years = dao.years().map { it.toModel() }
            val stored = dao.tracking().trackingStartYear()
            val start = stored ?: ZakatYearRules.initialStartYear(today)
            val created = ZakatYearRules.yearsToCreate(start, today, years).map { ZakatYear.unchecked(it) }
            dao.applyTrackingPlan(
                tracking = if (stored == null) startYearRow(start) else null,
                years = created.map { it.toEntity() },
            )
            Outcome.Success(currentState(db))
        }

    override suspend fun setPaid(hijriYear: Int, paid: Boolean): Outcome<ZakatYear, StorageError> = host.write { db ->
        val dao = db.zakatYearDao()
        val current = dao.year(hijriYear)?.toModel() ?: return@write Outcome.Failure(StorageError.NotFound)
        if (current.paid == paid) return@write Outcome.Success(current)
        val next = current.copy(paid = paid, markedAt = if (paid) host.now() else null)
        if (dao.updateYear(next.toEntity()) != 1) return@write Outcome.Failure(StorageError.NotFound)
        Outcome.Success(next)
    }

    override suspend fun setDateAdjustment(hijriYear: Int, overrideDays: Int): Outcome<ZakatYear, StorageError> =
        host.write { db ->
            if (overrideDays !in -HIJRI_MAX_OVERRIDE_DAYS..HIJRI_MAX_OVERRIDE_DAYS) {
                return@write Outcome.Failure(StorageError.InvalidInput)
            }
            val dao = db.zakatYearDao()
            val current = dao.year(hijriYear)?.toModel() ?: return@write Outcome.Failure(StorageError.NotFound)
            val next = current.copy(overrideDays = overrideDays, userAdjusted = overrideDays != 0)
            if (dao.updateYear(next.toEntity()) != 1) return@write Outcome.Failure(StorageError.NotFound)
            Outcome.Success(next)
        }

    override suspend fun addYear(hijriYear: Int, today: LocalDate): Outcome<ZakatYear, StorageError> =
        host.write { db ->
            if (hijriYear !in HIJRI_MIN_YEAR..HIJRI_MAX_YEAR) return@write Outcome.Failure(StorageError.InvalidInput)
            val dao = db.zakatYearDao()
            val years = dao.years().map { it.toModel() }
            if (years.any { it.hijriYear == hijriYear }) return@write Outcome.Failure(StorageError.Conflict)
            if (!ZakatYearRules.canAddYear(
                    hijriYear,
                    today,
                    years,
                )
            ) {
                return@write Outcome.Failure(StorageError.InvalidInput)
            }
            val added = ZakatYear.unchecked(hijriYear)
            // Years are never tracked without a start, so an add on untracked vault starts tracking in the same transaction.
            val tracking = if (dao.tracking().isEmpty()) {
                startYearRow(ZakatYearRules.initialStartYear(today))
            } else {
                null
            }
            dao.applyTrackingPlan(tracking, listOf(added.toEntity()))
            Outcome.Success(added)
        }

    private suspend fun currentState(db: DaftarDatabase): ZakatTrackingState {
        val dao = db.zakatYearDao()
        return ZakatTrackingState(dao.tracking().trackingStartYear(), dao.years().map { it.toModel() })
    }
}
