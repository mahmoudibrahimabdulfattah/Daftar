package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.Money
import kotlinx.coroutines.flow.Flow

/**
 * An explicit identity choice made in the directory or as a custom bank. [institutionId] is null for "Other". Every
 * choice is reviewed, so automatic matching never revisits it.
 */
data class BankIdentityChoice(val institutionId: InstitutionId?)

/**
 * The editable fields of a bank. A null [identity] keeps the stored identity on update, and creates an unreviewed
 * custom bank. Editing the display name never changes the identity.
 */
data class BankInput(val name: String, val identity: BankIdentityChoice? = null)

data class BankAccountInput(
    /** Editing may move an account only to an existing bank. */
    val bankId: BankId,
    val name: String,
    val balance: Money,
    val note: String?,
)

/** A bank with its accounts, read in one observation so the two cannot disagree. */
data class BankDetail(val bank: Stored<Bank>, val accounts: List<Stored<BankAccount>>)

interface BankRepository : RecordRepository<BankId, Bank, BankInput> {
    /** The bank and its accounts, or `Ready(null)` when the bank does not exist. */
    fun observeDetail(id: BankId): Flow<VaultRead<BankDetail?>>

    /** Every account across banks, in creation order. */
    fun observeAccounts(): Flow<VaultRead<List<Stored<BankAccount>>>>

    fun observeAccount(id: BankAccountId): Flow<VaultRead<Stored<BankAccount>?>>

    suspend fun createAccount(input: BankAccountInput): Outcome<Stored<BankAccount>, StorageError>

    suspend fun updateAccount(
        id: BankAccountId,
        expectedRevision: Long,
        input: BankAccountInput,
    ): Outcome<Stored<BankAccount>, StorageError>

    suspend fun deleteAccount(id: BankAccountId, expectedRevision: Long): Outcome<Unit, StorageError>
}
