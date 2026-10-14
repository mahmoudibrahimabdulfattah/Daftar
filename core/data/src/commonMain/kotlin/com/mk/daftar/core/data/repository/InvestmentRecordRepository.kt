package com.mk.daftar.core.data.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.data.records.RecordRules
import com.mk.daftar.core.data.session.VaultHost
import com.mk.daftar.core.data.session.missingOrConflict
import com.mk.daftar.core.database.dao.CashConversionRow
import com.mk.daftar.core.database.mapper.CashConversionRequest
import com.mk.daftar.core.database.mapper.toEntity
import com.mk.daftar.core.database.mapper.toStored
import com.mk.daftar.core.domain.repository.InvestmentConversion
import com.mk.daftar.core.domain.repository.InvestmentConversionFailure
import com.mk.daftar.core.domain.repository.InvestmentInput
import com.mk.daftar.core.domain.repository.InvestmentRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.EntryLimits
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.model.Money
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class InvestmentRecordRepository(private val host: VaultHost) : InvestmentRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<InvestmentAccount>>>> =
        host.observe { db -> db.investmentAccountDao().observeAll().map { rows -> rows.map { it.toStored() } } }

    override fun observe(id: InvestmentAccountId): Flow<VaultRead<Stored<InvestmentAccount>?>> =
        host.observe { db -> db.investmentAccountDao().observe(id.value).map { row -> row?.toStored() } }

    override suspend fun create(input: InvestmentInput): Outcome<Stored<InvestmentAccount>, StorageError> =
        host.write { db ->
            val now = host.now()
            if (!input.isAcceptable(now, valueAsOfIsNew = true)) return@write Outcome.Failure(StorageError.InvalidInput)
            val account = InvestmentAccount.create(
                id = InvestmentAccountId(host.ids.nextId()),
                providerId = input.providerId,
                name = input.name,
                amount = Money(input.amount, Currency.EGP),
                note = input.note,
                valueAsOf = input.valueAsOf,
                convertedFromCashId = null,
                createdAt = now,
                updatedAt = now,
            )
            db.investmentAccountDao().insert(account.toEntity(RecordRules.FIRST_REVISION))
            Outcome.Success(Stored(account, RecordRules.FIRST_REVISION))
        }

    override suspend fun update(
        id: InvestmentAccountId,
        expectedRevision: Long,
        input: InvestmentInput,
    ): Outcome<Stored<InvestmentAccount>, StorageError> = host.write { db ->
        val dao = db.investmentAccountDao()
        val previous = dao.get(id.value)?.toStored() ?: return@write Outcome.Failure(StorageError.NotFound)
        if (!RecordRules.matches(previous.revision, expectedRevision)) {
            return@write Outcome.Failure(StorageError.Conflict)
        }
        val now = host.now()
        // A stored as-of time is only re-checked when the user changes it, so a restored future date stays editable.
        val changedAsOf = input.valueAsOf != previous.value.valueAsOf
        if (!input.isAcceptable(now, valueAsOfIsNew = changedAsOf)) {
            return@write Outcome.Failure(StorageError.InvalidInput)
        }
        val updated = InvestmentAccount.create(
            id = id,
            providerId = input.providerId,
            name = input.name,
            amount = Money(input.amount, Currency.EGP),
            note = input.note,
            valueAsOf = input.valueAsOf,
            // The conversion provenance is immutable and survives every later edit.
            convertedFromCashId = previous.value.convertedFromCashId,
            createdAt = previous.value.createdAt,
            updatedAt = RecordRules.editedAt(now, previous.value.updatedAt),
        )
        val next = RecordRules.nextRevision(expectedRevision)
        if (dao.updateIfRevision(updated.toEntity(next), expectedRevision) == 1) {
            Outcome.Success(Stored(updated, next))
        } else {
            Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
        }
    }

    override suspend fun delete(id: InvestmentAccountId, expectedRevision: Long): Outcome<Unit, StorageError> =
        host.write { db ->
            val dao = db.investmentAccountDao()
            if (dao.deleteIfRevision(id.value, expectedRevision) == 1) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(missingOrConflict(exists = dao.get(id.value) != null))
            }
        }

    override suspend fun convertCashToInvestment(
        sourceId: CashBalanceId,
        expectedRevision: Long,
        draftId: InvestmentAccountId,
        providerId: InstitutionId?,
        valueAsOf: Instant,
    ): Outcome<InvestmentConversion, InvestmentConversionFailure> {
        if (expectedRevision < RecordRules.FIRST_REVISION) {
            return Outcome.Failure(InvestmentConversionFailure.Storage(StorageError.InvalidInput))
        }
        val now = host.now()
        if (valueAsOf < InvestmentAccount.EARLIEST_VALUE_AS_OF || valueAsOf > now) {
            return Outcome.Failure(InvestmentConversionFailure.InvalidValueAsOf)
        }
        val request = CashConversionRequest(
            sourceId = sourceId.value,
            expectedRevision = expectedRevision,
            draftId = draftId.value,
            providerId = providerId,
            valueAsOf = valueAsOf,
            now = now,
        )
        return when (
            val result = host.write { db ->
                Outcome.Success(db.investmentAccountDao().convertCash(request))
            }
        ) {
            is Outcome.Failure -> Outcome.Failure(InvestmentConversionFailure.Storage(result.error))

            is Outcome.Success -> when (val row = result.data) {
                is CashConversionRow.Converted ->
                    Outcome.Success(InvestmentConversion.Converted(row.investment.toStored()))

                is CashConversionRow.AlreadyConverted ->
                    Outcome.Success(InvestmentConversion.AlreadyConverted(InvestmentAccountId(row.investmentId)))

                CashConversionRow.SourceMissing -> Outcome.Failure(InvestmentConversionFailure.SourceUnavailable)

                CashConversionRow.SourceChanged -> Outcome.Failure(InvestmentConversionFailure.SourceChanged)

                CashConversionRow.UnsupportedCurrency ->
                    Outcome.Failure(InvestmentConversionFailure.UnsupportedCurrency)

                CashConversionRow.AmountOutOfRange -> Outcome.Failure(InvestmentConversionFailure.AmountOutOfRange)
            }
        }
    }
}

/**
 * The rules a user-entered investment must meet before it is stored. The as-of time is checked against the clock only
 * when it is new or changed, so the clock never invalidates a stored restored timestamp.
 */
private fun InvestmentInput.isAcceptable(now: Instant, valueAsOfIsNew: Boolean): Boolean {
    if (!EntryLimits.isValidName(name) || !EntryLimits.isValidNote(note.orEmpty())) return false
    if (!InvestmentAccount.isValidAmount(amount)) return false
    if (!valueAsOfIsNew) return true
    return valueAsOf >= InvestmentAccount.EARLIEST_VALUE_AS_OF && valueAsOf <= now
}
