package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.mk.daftar.core.database.entity.BankAccountEntity
import com.mk.daftar.core.database.entity.BankEntity
import com.mk.daftar.core.database.entity.CashBalanceEntity
import com.mk.daftar.core.database.entity.GoldHoldingEntity
import com.mk.daftar.core.database.entity.ManualQuoteEntity
import com.mk.daftar.core.database.entity.PersonalNoteEntity
import com.mk.daftar.core.database.entity.RemoteQuoteEntity
import com.mk.daftar.core.database.entity.ZakatPaymentEntity

/** Every stored row of one snapshot, read inside one transaction so no write can fall between two tables. */
data class BackupRows(
    val goldHoldings: List<GoldHoldingEntity>,
    val banks: List<BankEntity>,
    val bankAccounts: List<BankAccountEntity>,
    val cashBalances: List<CashBalanceEntity>,
    val zakatPayments: List<ZakatPaymentEntity>,
    val personalNotes: List<PersonalNoteEntity>,
    val remoteQuotes: List<RemoteQuoteEntity>,
    val manualQuotes: List<ManualQuoteEntity>,
)

/** The read side of a backup. It only reads; writes never go through this DAO. */
@Dao
abstract class BackupSnapshotDao {
    /** One transaction over all eight tables. Room runs it on the write connection, so the rows agree with each other. */
    @Transaction
    open suspend fun readAll(): BackupRows = BackupRows(
        goldHoldings = goldHoldings(),
        banks = banks(),
        bankAccounts = bankAccounts(),
        cashBalances = cashBalances(),
        zakatPayments = zakatPayments(),
        personalNotes = personalNotes(),
        remoteQuotes = remoteQuotes(),
        manualQuotes = manualQuotes(),
    )

    @Query("SELECT * FROM gold_holdings ORDER BY id ASC")
    protected abstract suspend fun goldHoldings(): List<GoldHoldingEntity>

    @Query("SELECT * FROM banks ORDER BY id ASC")
    protected abstract suspend fun banks(): List<BankEntity>

    @Query("SELECT * FROM bank_accounts ORDER BY id ASC")
    protected abstract suspend fun bankAccounts(): List<BankAccountEntity>

    @Query("SELECT * FROM cash_balances ORDER BY id ASC")
    protected abstract suspend fun cashBalances(): List<CashBalanceEntity>

    @Query("SELECT * FROM zakat_payments ORDER BY id ASC")
    protected abstract suspend fun zakatPayments(): List<ZakatPaymentEntity>

    @Query("SELECT * FROM personal_notes ORDER BY id ASC")
    protected abstract suspend fun personalNotes(): List<PersonalNoteEntity>

    @Query("SELECT * FROM remote_quotes ORDER BY quote_key ASC, provider ASC")
    protected abstract suspend fun remoteQuotes(): List<RemoteQuoteEntity>

    @Query("SELECT * FROM manual_quotes ORDER BY quote_key ASC")
    protected abstract suspend fun manualQuotes(): List<ManualQuoteEntity>
}
