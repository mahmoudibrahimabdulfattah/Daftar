package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.InvestmentConversion
import com.mk.daftar.core.domain.repository.InvestmentConversionFailure
import com.mk.daftar.core.domain.repository.InvestmentInput
import com.mk.daftar.core.domain.repository.InvestmentRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

// Investment accounts delegate to their repository unchanged, like every other record type.

class ObserveInvestmentsUseCase(private val repository: InvestmentRepository) {
    operator fun invoke(): Flow<VaultRead<List<Stored<InvestmentAccount>>>> = repository.observeAll()
}

class ObserveInvestmentUseCase(private val repository: InvestmentRepository) {
    operator fun invoke(id: InvestmentAccountId): Flow<VaultRead<Stored<InvestmentAccount>?>> = repository.observe(id)
}

class CreateInvestmentUseCase(private val repository: InvestmentRepository) {
    suspend operator fun invoke(input: InvestmentInput): Outcome<Stored<InvestmentAccount>, StorageError> =
        repository.create(input)
}

class UpdateInvestmentUseCase(private val repository: InvestmentRepository) {
    suspend operator fun invoke(
        id: InvestmentAccountId,
        expectedRevision: Long,
        input: InvestmentInput,
    ): Outcome<Stored<InvestmentAccount>, StorageError> = repository.update(id, expectedRevision, input)
}

class DeleteInvestmentUseCase(private val repository: InvestmentRepository) {
    suspend operator fun invoke(id: InvestmentAccountId, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.delete(id, expectedRevision)
}

/** The explicit reclassification of one EGP cash entry. Nothing is converted unless the user confirms it. */
class ConvertCashToInvestmentUseCase(private val repository: InvestmentRepository) {
    suspend operator fun invoke(
        sourceId: CashBalanceId,
        expectedRevision: Long,
        draftId: InvestmentAccountId,
        providerId: InstitutionId?,
        valueAsOf: Instant,
    ): Outcome<InvestmentConversion, InvestmentConversionFailure> =
        repository.convertCashToInvestment(sourceId, expectedRevision, draftId, providerId, valueAsOf)
}
