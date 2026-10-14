package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mk.daftar.core.database.entity.BankAccountEntity
import com.mk.daftar.core.database.entity.BankEntity
import com.mk.daftar.core.database.entity.CashBalanceEntity
import com.mk.daftar.core.database.entity.GoldHoldingEntity
import com.mk.daftar.core.database.entity.InvestmentAccountEntity
import com.mk.daftar.core.database.entity.ManualQuoteEntity
import com.mk.daftar.core.database.entity.PersonalNoteEntity
import com.mk.daftar.core.database.entity.RemoteQuoteEntity
import com.mk.daftar.core.database.entity.ZakatPaymentEntity
import com.mk.daftar.core.database.entity.ZakatTrackingEntity
import com.mk.daftar.core.database.entity.ZakatYearEntity

/** Every row of a restored vault, in the order the foreign keys need: banks before their accounts. */
data class RestoreRows(
    val banks: List<BankEntity>,
    val bankAccounts: List<BankAccountEntity>,
    val goldHoldings: List<GoldHoldingEntity>,
    val cashBalances: List<CashBalanceEntity>,
    val zakatPayments: List<ZakatPaymentEntity>,
    val personalNotes: List<PersonalNoteEntity>,
    val remoteQuotes: List<RemoteQuoteEntity>,
    val manualQuotes: List<ManualQuoteEntity>,
    val zakatYears: List<ZakatYearEntity>,
    val zakatTracking: ZakatTrackingEntity?,
    val investmentAccounts: List<InvestmentAccountEntity> = emptyList(),
)

/** The number of rows in each table. Used to confirm a staged vault before it can be committed. */
data class RestoreCounts(
    val banks: Int,
    val bankAccounts: Int,
    val goldHoldings: Int,
    val cashBalances: Int,
    val zakatPayments: Int,
    val personalNotes: Int,
    val remoteQuotes: Int,
    val manualQuotes: Int,
    val zakatYears: Int,
    val zakatTracking: Int,
    val investmentAccounts: Int,
)

/**
 * The write side of a restore. It runs only on a staging database that no session has published: every insert is
 * create-only, so a duplicate key aborts the whole restore instead of replacing a row.
 */
@Dao
abstract class RestoreWriteDao {
    /** One transaction. A single rejected row, such as a validation trigger or a duplicate key, rolls everything back. */
    @Transaction
    open suspend fun writeAll(rows: RestoreRows) {
        rows.banks.forEach { insertBank(it) }
        rows.bankAccounts.forEach { insertBankAccount(it) }
        rows.goldHoldings.forEach { insertGoldHolding(it) }
        rows.cashBalances.forEach { insertCashBalance(it) }
        rows.zakatPayments.forEach { insertZakatPayment(it) }
        rows.personalNotes.forEach { insertPersonalNote(it) }
        rows.remoteQuotes.forEach { insertRemoteQuote(it) }
        rows.manualQuotes.forEach { insertManualQuote(it) }
        rows.zakatYears.forEach { insertZakatYear(it) }
        rows.zakatTracking?.let { insertZakatTracking(it) }
        rows.investmentAccounts.forEach { insertInvestmentAccount(it) }
    }

    @Query(
        "SELECT " +
            "(SELECT COUNT(*) FROM banks) AS banks, " +
            "(SELECT COUNT(*) FROM bank_accounts) AS bankAccounts, " +
            "(SELECT COUNT(*) FROM gold_holdings) AS goldHoldings, " +
            "(SELECT COUNT(*) FROM cash_balances) AS cashBalances, " +
            "(SELECT COUNT(*) FROM zakat_payments) AS zakatPayments, " +
            "(SELECT COUNT(*) FROM personal_notes) AS personalNotes, " +
            "(SELECT COUNT(*) FROM remote_quotes) AS remoteQuotes, " +
            "(SELECT COUNT(*) FROM manual_quotes) AS manualQuotes, " +
            "(SELECT COUNT(*) FROM zakat_years) AS zakatYears, " +
            "(SELECT COUNT(*) FROM zakat_tracking) AS zakatTracking, " +
            "(SELECT COUNT(*) FROM investment_accounts) AS investmentAccounts",
    )
    abstract suspend fun counts(): RestoreCounts

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertBank(entity: BankEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertBankAccount(entity: BankAccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertGoldHolding(entity: GoldHoldingEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCashBalance(entity: CashBalanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertZakatPayment(entity: ZakatPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPersonalNote(entity: PersonalNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRemoteQuote(entity: RemoteQuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertManualQuote(entity: ManualQuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertZakatYear(entity: ZakatYearEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertZakatTracking(entity: ZakatTrackingEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertInvestmentAccount(entity: InvestmentAccountEntity): Long
}
