package com.mk.daftar.feature.prices

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
import kotlinx.coroutines.flow.map

/** No investments, read in step with [follow]: the same lock state and generation, so valuations stay complete. */
internal class NoInvestments(private val follow: Flow<VaultRead<*>>) : InvestmentRepository {
    override fun observeAll(): Flow<VaultRead<List<Stored<InvestmentAccount>>>> = follow.map { read ->
        when (read) {
            is VaultRead.Ready -> VaultRead.Ready(read.generation, emptyList())
            VaultRead.Locked -> VaultRead.Locked
            VaultRead.Loading -> VaultRead.Loading
            is VaultRead.Failed -> read
        }
    }

    override fun observe(id: InvestmentAccountId): Flow<VaultRead<Stored<InvestmentAccount>?>> = error("unused")

    override suspend fun create(input: InvestmentInput): Outcome<Stored<InvestmentAccount>, StorageError> =
        error("unused")

    override suspend fun update(
        id: InvestmentAccountId,
        expectedRevision: Long,
        input: InvestmentInput,
    ): Outcome<Stored<InvestmentAccount>, StorageError> = error("unused")

    override suspend fun delete(id: InvestmentAccountId, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")

    override suspend fun convertCashToInvestment(
        sourceId: CashBalanceId,
        expectedRevision: Long,
        draftId: InvestmentAccountId,
        providerId: InstitutionId?,
        valueAsOf: Instant,
    ): Outcome<InvestmentConversion, InvestmentConversionFailure> = error("unused")
}
