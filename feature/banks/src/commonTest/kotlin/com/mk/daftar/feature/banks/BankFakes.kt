@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.feature.banks

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankDetail
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.BankRepository
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.PriceRepository
import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RefreshReport
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.QuoteKey
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A session the test drives. Only [VaultSessionState.Open] exposes data, as the real session does. */
internal class FakeSession : VaultSession {
    private val mutable = MutableStateFlow<VaultSessionState>(VaultSessionState.Locked)
    override val state: StateFlow<VaultSessionState> = mutable.asStateFlow()

    fun set(next: VaultSessionState) {
        mutable.value = next
    }

    override suspend fun lock() {
        mutable.value = VaultSessionState.Locked
    }
}

internal object FixedClock : Clock {
    override fun now(): Instant = FIXTURE_NOW
}

/** Quotes the test publishes. Every observer sees the same snapshot value; manual writes are not used here. */
internal class FakePrices : PriceRepository {
    val quotes = MutableStateFlow<VaultRead<QuoteSnapshot>>(VaultRead.Locked)
    override fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>> = quotes
    override suspend fun refresh(): Outcome<RefreshReport, StorageError> = error("unused")
    override suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError> =
        error("unused")
    override suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
    override suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError> =
        error("unused")
}

/**
 * An in-memory bank store with the repository contract: revisions start at 1 and grow on every edit, updates and
 * deletes name the revision they read, a bank with accounts cannot be deleted, and observations follow the session.
 * Failures, gates and external edits are injected per test.
 */
internal class FakeBankRepository : BankRepository {
    private val banks = MutableStateFlow<List<Stored<Bank>>>(emptyList())
    private val accounts = MutableStateFlow<List<Stored<BankAccount>>>(emptyList())
    private val locked = MutableStateFlow(false)
    private var counter = 0

    val createdBankInputs = mutableListOf<BankInput>()
    val updatedBankRevisions = mutableListOf<Long>()
    val deletedBankRevisions = mutableListOf<Long>()
    val createdAccountInputs = mutableListOf<BankAccountInput>()
    val updatedAccountRevisions = mutableListOf<Long>()
    val deletedAccountRevisions = mutableListOf<Long>()

    /** Fails the next bank write with this error. */
    var bankWriteFailure: StorageError? = null

    /** Fails the next account write with this error. */
    var accountWriteFailure: StorageError? = null

    /** Writes wait on this gate after they start, so a test can act while one is in flight. */
    var writeGate: CompletableDeferred<Unit>? = null

    fun seed(bank: Bank, revision: Long = 1) {
        banks.update { it + Stored(bank, revision) }
    }

    fun seedAccount(account: BankAccount, revision: Long = 1) {
        accounts.update { it + Stored(account, revision) }
    }

    /** Another writer renames the bank: the revision moves on and every observer sees the change. */
    fun externalRename(id: String, name: String) {
        banks.update { list ->
            list.map { stored ->
                if (stored.value.id.value == id) {
                    Stored(
                        Bank.create(stored.value.id, name, stored.value.createdAt, FIXTURE_NOW),
                        stored.revision + 1,
                    )
                } else {
                    stored
                }
            }
        }
    }

    /** Another session removes the bank. Its observers see the record missing. */
    fun externalDeleteBank(id: String) {
        banks.update { list -> list.filterNot { it.value.id.value == id } }
    }

    /** Another session removes the account. Its observers see the record missing. */
    fun externalDeleteAccount(id: String) {
        accounts.update { list -> list.filterNot { it.value.id.value == id } }
    }

    fun setLocked(value: Boolean) {
        locked.value = value
    }

    fun bankNames(): List<String> = banks.value.map { it.value.name }

    fun accountList(): List<BankAccount> = accounts.value.map { it.value }

    override fun observeAll(): Flow<VaultRead<List<Stored<Bank>>>> = combine(banks, locked) { list, isLocked ->
        if (isLocked) VaultRead.Locked else VaultRead.Ready(GENERATION, list)
    }

    override fun observe(id: BankId): Flow<VaultRead<Stored<Bank>?>> = combine(banks, locked) { list, isLocked ->
        if (isLocked) VaultRead.Locked else VaultRead.Ready(GENERATION, list.firstOrNull { it.value.id == id })
    }

    override fun observeDetail(id: BankId): Flow<VaultRead<BankDetail?>> =
        combine(banks, accounts, locked) { bankList, accountList, isLocked ->
            if (isLocked) {
                VaultRead.Locked
            } else {
                val bank = bankList.firstOrNull { it.value.id == id }
                VaultRead.Ready(
                    GENERATION,
                    bank?.let { BankDetail(it, accountList.filter { account -> account.value.bankId == id }) },
                )
            }
        }

    override fun observeAccounts(): Flow<VaultRead<List<Stored<BankAccount>>>> =
        combine(accounts, locked) { list, isLocked ->
            if (isLocked) VaultRead.Locked else VaultRead.Ready(GENERATION, list)
        }

    override fun observeAccount(id: BankAccountId): Flow<VaultRead<Stored<BankAccount>?>> =
        combine(accounts, locked) { list, isLocked ->
            if (isLocked) VaultRead.Locked else VaultRead.Ready(GENERATION, list.firstOrNull { it.value.id == id })
        }

    override suspend fun create(input: BankInput): Outcome<Stored<Bank>, StorageError> {
        createdBankInputs += input
        writeGate?.await()
        bankWriteFailure?.let { return Outcome.Failure(it) }
        counter++
        val bank = Bank.create(BankId("bank-$counter"), input.name, FIXTURE_NOW, FIXTURE_NOW)
        val stored = Stored(bank, 1)
        banks.update { it + stored }
        return Outcome.Success(stored)
    }

    override suspend fun update(
        id: BankId,
        expectedRevision: Long,
        input: BankInput,
    ): Outcome<Stored<Bank>, StorageError> {
        updatedBankRevisions += expectedRevision
        writeGate?.await()
        bankWriteFailure?.let { return Outcome.Failure(it) }
        val current = banks.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        val updated = Stored(
            Bank.create(id, input.name, current.value.createdAt, FIXTURE_NOW),
            current.revision + 1,
        )
        banks.update { list -> list.map { if (it === current) updated else it } }
        return Outcome.Success(updated)
    }

    override suspend fun delete(id: BankId, expectedRevision: Long): Outcome<Unit, StorageError> {
        deletedBankRevisions += expectedRevision
        writeGate?.await()
        bankWriteFailure?.let { return Outcome.Failure(it) }
        val current = banks.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        if (accounts.value.any { it.value.bankId == id }) return Outcome.Failure(StorageError.BankHasAccounts)
        banks.update { list -> list.filterNot { it === current } }
        return Outcome.Success(Unit)
    }

    override suspend fun createAccount(input: BankAccountInput): Outcome<Stored<BankAccount>, StorageError> {
        createdAccountInputs += input
        writeGate?.await()
        accountWriteFailure?.let { return Outcome.Failure(it) }
        if (banks.value.none { it.value.id == input.bankId }) return Outcome.Failure(StorageError.NotFound)
        counter++
        val account = BankAccount.create(
            id = BankAccountId("account-$counter"),
            bankId = input.bankId,
            name = input.name,
            balance = input.balance,
            note = input.note,
            createdAt = FIXTURE_NOW,
            updatedAt = FIXTURE_NOW,
        )
        val stored = Stored(account, 1)
        accounts.update { it + stored }
        return Outcome.Success(stored)
    }

    override suspend fun updateAccount(
        id: BankAccountId,
        expectedRevision: Long,
        input: BankAccountInput,
    ): Outcome<Stored<BankAccount>, StorageError> {
        updatedAccountRevisions += expectedRevision
        writeGate?.await()
        accountWriteFailure?.let { return Outcome.Failure(it) }
        val current = accounts.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        val updated = Stored(
            BankAccount.create(
                id = id,
                bankId = input.bankId,
                name = input.name,
                balance = input.balance,
                note = input.note,
                createdAt = current.value.createdAt,
                updatedAt = FIXTURE_NOW,
            ),
            current.revision + 1,
        )
        accounts.update { list -> list.map { if (it === current) updated else it } }
        return Outcome.Success(updated)
    }

    override suspend fun deleteAccount(id: BankAccountId, expectedRevision: Long): Outcome<Unit, StorageError> {
        deletedAccountRevisions += expectedRevision
        writeGate?.await()
        accountWriteFailure?.let { return Outcome.Failure(it) }
        val current = accounts.value.firstOrNull { it.value.id == id } ?: return Outcome.Failure(StorageError.NotFound)
        if (current.revision != expectedRevision) return Outcome.Failure(StorageError.Conflict)
        accounts.update { list -> list.filterNot { it === current } }
        return Outcome.Success(Unit)
    }

    /** Another writer adds an account to the bank, as a concurrent session would. */
    fun externalAccount(account: BankAccount) {
        accounts.update { it + Stored(account, 1) }
    }

    private companion object {
        const val GENERATION: Long = 1
    }
}
