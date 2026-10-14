package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import kotlin.time.Instant

/**
 * The user's entry for a broker account. [amount] is the current total EGP value, and [valueAsOf] its effective time.
 * The data layer assigns the ID, timestamps and revision; this input carries none of them.
 */
data class InvestmentInput(
    val providerId: InstitutionId?,
    val name: String,
    val amount: Decimal,
    val note: String?,
    val valueAsOf: Instant,
)

/** What a conversion did. A retry of an already completed conversion reports [AlreadyConverted] and writes nothing. */
sealed interface InvestmentConversion {
    data class Converted(val investment: Stored<InvestmentAccount>) : InvestmentConversion

    data class AlreadyConverted(val investmentId: InvestmentAccountId) : InvestmentConversion
}

/** Why a conversion did not happen. Every refusal leaves the source cash entry exactly as it was. */
sealed interface InvestmentConversionFailure {
    data class Storage(val error: StorageError) : InvestmentConversionFailure

    /** The source entry is gone and no investment records it. A stale draft is never recreated from. */
    data object SourceUnavailable : InvestmentConversionFailure

    /** The entry changed after the user reviewed it. The review must be repeated. */
    data object SourceChanged : InvestmentConversionFailure

    /** Only EGP entries convert. A USD entry is never converted or reduced. */
    data object UnsupportedCurrency : InvestmentConversionFailure

    /** The entry's amount is outside the investment bounds. It is kept and never rounded or clamped. */
    data object AmountOutOfRange : InvestmentConversionFailure

    /** The value date is before 1 January 1900 or after now. */
    data object InvalidValueAsOf : InvestmentConversionFailure
}

/** Mints the investment ID of one conversion draft. The draft asks once, when its review opens, and reuses the ID on retry. */
fun interface InvestmentDraftIds {
    fun newDraftId(): InvestmentAccountId
}

interface InvestmentRepository : RecordRepository<InvestmentAccountId, InvestmentAccount, InvestmentInput> {
    /**
     * Moves one EGP cash entry into the investments in a single transaction. The amount, name and note are copied exactly,
     * and the source row is deleted by compare-and-set against [expectedRevision]. [draftId] is the investment ID the
     * draft was created with, so a retry after process death reuses it. Any failure rolls everything back.
     */
    suspend fun convertCashToInvestment(
        sourceId: CashBalanceId,
        expectedRevision: Long,
        draftId: InvestmentAccountId,
        providerId: InstitutionId?,
        valueAsOf: Instant,
    ): Outcome<InvestmentConversion, InvestmentConversionFailure>
}
