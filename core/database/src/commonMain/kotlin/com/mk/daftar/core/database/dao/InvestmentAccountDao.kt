package com.mk.daftar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mk.daftar.core.database.entity.CashBalanceEntity
import com.mk.daftar.core.database.entity.InvestmentAccountEntity
import com.mk.daftar.core.database.mapper.CashConversionRequest
import com.mk.daftar.core.database.mapper.ConvertedRow
import com.mk.daftar.core.database.mapper.toConvertedInvestment
import kotlinx.coroutines.flow.Flow

/** The result of one conversion transaction. Only [Converted] wrote anything. */
sealed interface CashConversionRow {
    /** The investment that now holds the entry. */
    data class Converted(val investment: InvestmentAccountEntity) : CashConversionRow

    /** An investment already records this source. Nothing was written. */
    data class AlreadyConverted(val investmentId: String) : CashConversionRow

    /** The source cash entry no longer exists and no investment records it. */
    data object SourceMissing : CashConversionRow

    /** The source changed since the user reviewed it. */
    data object SourceChanged : CashConversionRow

    data object UnsupportedCurrency : CashConversionRow

    data object AmountOutOfRange : CashConversionRow
}

@Dao
abstract class InvestmentAccountDao {
    @Query("SELECT * FROM investment_accounts ORDER BY created_seconds ASC, created_nanos ASC, id ASC")
    abstract fun observeAll(): Flow<List<InvestmentAccountEntity>>

    @Query("SELECT * FROM investment_accounts WHERE id = :id")
    abstract fun observe(id: String): Flow<InvestmentAccountEntity?>

    @Query("SELECT * FROM investment_accounts WHERE id = :id")
    abstract suspend fun get(id: String): InvestmentAccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: InvestmentAccountEntity): Long

    @Update
    protected abstract suspend fun updateRow(entity: InvestmentAccountEntity): Int

    @Query("SELECT revision FROM investment_accounts WHERE id = :id")
    protected abstract suspend fun revisionOf(id: String): Long?

    @Transaction
    open suspend fun updateIfRevision(entity: InvestmentAccountEntity, expectedRevision: Long): Int {
        if (revisionOf(entity.id) != expectedRevision) return 0
        return updateRow(entity)
    }

    @Query("DELETE FROM investment_accounts WHERE id = :id AND revision = :expectedRevision")
    abstract suspend fun deleteIfRevision(id: String, expectedRevision: Long): Int

    @Query("SELECT * FROM investment_accounts WHERE converted_from_cash_id = :sourceId")
    protected abstract suspend fun investmentFromSource(sourceId: String): InvestmentAccountEntity?

    @Query("SELECT * FROM cash_balances WHERE id = :id")
    protected abstract suspend fun cashRow(id: String): CashBalanceEntity?

    @Query("DELETE FROM cash_balances WHERE id = :id AND revision = :expectedRevision")
    protected abstract suspend fun deleteCashIfRevision(id: String, expectedRevision: Long): Int

    /**
     * Moves one EGP cash entry into the investments in one transaction, in this order: an existing investment for the
     * source wins (idempotent retry); otherwise the source must still exist at the reviewed revision, build the row, insert
     * it, and delete the source with a compare-and-set. Any throw rolls every write back. A source that is refused leaves
     * the cash table untouched.
     */
    @Transaction
    open suspend fun convertCash(request: CashConversionRequest): CashConversionRow {
        investmentFromSource(request.sourceId)?.let { return CashConversionRow.AlreadyConverted(it.id) }
        val source = cashRow(request.sourceId) ?: return CashConversionRow.SourceMissing
        if (source.revision != request.expectedRevision) return CashConversionRow.SourceChanged
        val built = when (val result = source.toConvertedInvestment(request)) {
            is ConvertedRow.Built -> result.row
            ConvertedRow.UnsupportedCurrency -> return CashConversionRow.UnsupportedCurrency
            ConvertedRow.AmountOutOfRange -> return CashConversionRow.AmountOutOfRange
        }
        insert(built)
        // The compare-and-set cannot miss inside this transaction; a miss means the write is no longer one unit, so it
        // throws and every row written above is rolled back.
        check(deleteCashIfRevision(source.id, source.revision) == 1) { "conversion source changed mid-transaction" }
        return CashConversionRow.Converted(built)
    }
}
