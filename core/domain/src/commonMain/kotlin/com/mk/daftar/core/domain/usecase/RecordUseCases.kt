package com.mk.daftar.core.domain.usecase

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.CashRepository
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.GoldRepository
import com.mk.daftar.core.domain.repository.NoteRepository
import com.mk.daftar.core.domain.repository.PersonalNoteInput
import com.mk.daftar.core.domain.repository.ZakatPaymentInput
import com.mk.daftar.core.domain.repository.ZakatRepository
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import kotlinx.coroutines.flow.Flow

// Each use case delegates to its repository unchanged: IDs, expectedRevision, Outcome and every StorageError pass
// through as the data layer reports them. Validation lives in the model constructors.

class ObserveGoldHoldingsUseCase(private val repository: GoldRepository) {
    operator fun invoke(): Flow<VaultRead<List<Stored<GoldHolding>>>> = repository.observeAll()
}

class ObserveGoldHoldingUseCase(private val repository: GoldRepository) {
    operator fun invoke(id: GoldHoldingId): Flow<VaultRead<Stored<GoldHolding>?>> = repository.observe(id)
}

class CreateGoldHoldingUseCase(private val repository: GoldRepository) {
    suspend operator fun invoke(input: GoldHoldingInput): Outcome<Stored<GoldHolding>, StorageError> =
        repository.create(input)
}

class UpdateGoldHoldingUseCase(private val repository: GoldRepository) {
    suspend operator fun invoke(
        id: GoldHoldingId,
        expectedRevision: Long,
        input: GoldHoldingInput,
    ): Outcome<Stored<GoldHolding>, StorageError> = repository.update(id, expectedRevision, input)
}

class DeleteGoldHoldingUseCase(private val repository: GoldRepository) {
    suspend operator fun invoke(id: GoldHoldingId, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.delete(id, expectedRevision)
}

class ObserveBanksUseCase(private val repository: BankRepository) {
    operator fun invoke(): Flow<VaultRead<List<Stored<Bank>>>> = repository.observeAll()
}

class ObserveBankUseCase(private val repository: BankRepository) {
    operator fun invoke(id: BankId): Flow<VaultRead<Stored<Bank>?>> = repository.observe(id)
}

class ObserveBankDetailUseCase(private val repository: BankRepository) {
    operator fun invoke(id: BankId): Flow<VaultRead<BankDetail?>> = repository.observeDetail(id)
}

class ObserveBankAccountsUseCase(private val repository: BankRepository) {
    operator fun invoke(): Flow<VaultRead<List<Stored<BankAccount>>>> = repository.observeAccounts()
}

class ObserveBankAccountUseCase(private val repository: BankRepository) {
    operator fun invoke(id: BankAccountId): Flow<VaultRead<Stored<BankAccount>?>> = repository.observeAccount(id)
}

class CreateBankUseCase(private val repository: BankRepository) {
    suspend operator fun invoke(input: BankInput): Outcome<Stored<Bank>, StorageError> = repository.create(input)
}

class UpdateBankUseCase(private val repository: BankRepository) {
    suspend operator fun invoke(
        id: BankId,
        expectedRevision: Long,
        input: BankInput,
    ): Outcome<Stored<Bank>, StorageError> = repository.update(id, expectedRevision, input)
}

/** Fails with [StorageError.BankHasAccounts] while accounts still reference the bank. */
class DeleteBankUseCase(private val repository: BankRepository) {
    suspend operator fun invoke(id: BankId, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.delete(id, expectedRevision)
}

class CreateBankAccountUseCase(private val repository: BankRepository) {
    suspend operator fun invoke(input: BankAccountInput): Outcome<Stored<BankAccount>, StorageError> =
        repository.createAccount(input)
}

class UpdateBankAccountUseCase(private val repository: BankRepository) {
    suspend operator fun invoke(
        id: BankAccountId,
        expectedRevision: Long,
        input: BankAccountInput,
    ): Outcome<Stored<BankAccount>, StorageError> = repository.updateAccount(id, expectedRevision, input)
}

class DeleteBankAccountUseCase(private val repository: BankRepository) {
    suspend operator fun invoke(id: BankAccountId, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.deleteAccount(id, expectedRevision)
}

class ObserveCashBalancesUseCase(private val repository: CashRepository) {
    operator fun invoke(): Flow<VaultRead<List<Stored<CashBalance>>>> = repository.observeAll()
}

class ObserveCashBalanceUseCase(private val repository: CashRepository) {
    operator fun invoke(id: CashBalanceId): Flow<VaultRead<Stored<CashBalance>?>> = repository.observe(id)
}

class CreateCashBalanceUseCase(private val repository: CashRepository) {
    suspend operator fun invoke(input: CashBalanceInput): Outcome<Stored<CashBalance>, StorageError> =
        repository.create(input)
}

class UpdateCashBalanceUseCase(private val repository: CashRepository) {
    suspend operator fun invoke(
        id: CashBalanceId,
        expectedRevision: Long,
        input: CashBalanceInput,
    ): Outcome<Stored<CashBalance>, StorageError> = repository.update(id, expectedRevision, input)
}

class DeleteCashBalanceUseCase(private val repository: CashRepository) {
    suspend operator fun invoke(id: CashBalanceId, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.delete(id, expectedRevision)
}

/** Journal entries are kept apart from wealth: nothing here feeds the valuation. */
class ObserveZakatPaymentsUseCase(private val repository: ZakatRepository) {
    operator fun invoke(): Flow<VaultRead<List<Stored<ZakatPayment>>>> = repository.observeAll()
}

class ObserveZakatPaymentUseCase(private val repository: ZakatRepository) {
    operator fun invoke(id: ZakatPaymentId): Flow<VaultRead<Stored<ZakatPayment>?>> = repository.observe(id)
}

class CreateZakatPaymentUseCase(private val repository: ZakatRepository) {
    suspend operator fun invoke(input: ZakatPaymentInput): Outcome<Stored<ZakatPayment>, StorageError> =
        repository.create(input)
}

class UpdateZakatPaymentUseCase(private val repository: ZakatRepository) {
    suspend operator fun invoke(
        id: ZakatPaymentId,
        expectedRevision: Long,
        input: ZakatPaymentInput,
    ): Outcome<Stored<ZakatPayment>, StorageError> = repository.update(id, expectedRevision, input)
}

class DeleteZakatPaymentUseCase(private val repository: ZakatRepository) {
    suspend operator fun invoke(id: ZakatPaymentId, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.delete(id, expectedRevision)
}

class ObservePersonalNotesUseCase(private val repository: NoteRepository) {
    operator fun invoke(): Flow<VaultRead<List<Stored<PersonalNote>>>> = repository.observeAll()
}

class ObservePersonalNoteUseCase(private val repository: NoteRepository) {
    operator fun invoke(id: NoteId): Flow<VaultRead<Stored<PersonalNote>?>> = repository.observe(id)
}

class CreatePersonalNoteUseCase(private val repository: NoteRepository) {
    suspend operator fun invoke(input: PersonalNoteInput): Outcome<Stored<PersonalNote>, StorageError> =
        repository.create(input)
}

class UpdatePersonalNoteUseCase(private val repository: NoteRepository) {
    suspend operator fun invoke(
        id: NoteId,
        expectedRevision: Long,
        input: PersonalNoteInput,
    ): Outcome<Stored<PersonalNote>, StorageError> = repository.update(id, expectedRevision, input)
}

class DeletePersonalNoteUseCase(private val repository: NoteRepository) {
    suspend operator fun invoke(id: NoteId, expectedRevision: Long): Outcome<Unit, StorageError> =
        repository.delete(id, expectedRevision)
}
