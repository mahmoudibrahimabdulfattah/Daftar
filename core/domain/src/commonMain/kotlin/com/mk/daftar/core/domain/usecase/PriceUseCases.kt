package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.model.QuoteKey

// Refresh, fallback and persistence stay in PriceRepository. These use cases only expose them.

class RefreshPricesUseCase(private val repository: PriceRepository) {
    suspend operator fun invoke(): Outcome<RefreshReport, StorageError> = repository.refresh()
}

class SetManualQuoteUseCase(private val repository: PriceRepository) {
    /** `expectedRevision` null means create-only; see [PriceRepository.setManual]. */
    suspend operator fun invoke(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> =
        repository.setManual(input, expectedRevision)
}

/** Unpins the manual value. It stays available as a last-resort candidate. */
class RemoveOverrideUseCase(private val repository: PriceRepository) {
    suspend operator fun invoke(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.removeOverride(key, expectedRevision)
}

class DeleteManualQuoteUseCase(private val repository: PriceRepository) {
    suspend operator fun invoke(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.deleteManual(key, expectedRevision)
}
