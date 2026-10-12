package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvider
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** A cached remote quote with the attribution and next-update time its provider supplied. */
data class RemoteQuoteRecord(val quote: PriceQuote, val nextUpdateAt: Instant?, val attributionUrl: String?)

/**
 * Remote and manual candidates, unranked. Choosing between them (pinned manual, then remote, then unpinned manual)
 * and judging freshness is domain policy, not storage.
 */
data class QuoteSnapshot(
    /** Cached remote quotes, one per provider and key, with their provenance. */
    val remote: List<RemoteQuoteRecord>,
    /** Manual quotes. Each carries its pinned state in its provenance. */
    val manual: List<Stored<PriceQuote>>,
)

/** A manually entered price. Only gold sell prices and USD to EGP can be entered by hand. */
data class ManualQuoteInput(val key: QuoteKey, val value: Decimal, val note: String?) {
    init {
        require(key.isManuallyEntered()) { "only gold sell prices and USD to EGP are entered manually" }
        require(value > Decimal.ZERO) { "manual value must be positive" }
    }
}

/** True for the keys a user may enter by hand. Buy-side gold prices come only from remote providers. */
fun QuoteKey.isManuallyEntered(): Boolean = when (this) {
    is QuoteKey.Gold -> side == MerchantSide.SELLS_TO_CUSTOMER
    QuoteKey.UsdEgp -> true
}

/** Outcome of one provider or karat in a refresh. Accepted values are fresh, LIVE provenance. */
sealed interface RefreshStepResult {
    /** Accepted quotes, to be cached in the same commit as every other accepted quote in this refresh. */
    data class Accepted(val quotes: List<PriceQuote>) : RefreshStepResult

    /** The cached value stayed as it was. */
    data class Kept(val reason: RefreshRejection) : RefreshStepResult
}

enum class RefreshRejection {
    /** Connection, DNS, TLS, timeout or an HTTP error. */
    Unreachable,
    RateLimited,
    InvalidPayload,

    /** The provider did not include this quote. */
    Missing,

    /** The source time is older than the cached quote's, so the cache is not replaced. */
    OlderThanStored,
}

data class RefreshReport(
    val gold: Map<GoldKarat, RefreshStepResult>,
    /** Each FX provider is reported independently; a fallback never erases the primary's last quote. */
    val fx: Map<QuoteProvider, RefreshStepResult>,
)

interface PriceRepository {
    /** Remote and manual candidates. Emits [VaultRead.Locked] while no session is open. */
    fun observeQuotes(): Flow<VaultRead<QuoteSnapshot>>

    /** Fetches outside any transaction, then commits accepted quotes together. Cancelled refreshes never write. */
    suspend fun refresh(): Outcome<RefreshReport, StorageError>

    /** Replaces the slot and pins it. `null` means create-only: fails with [StorageError.Conflict] if one exists. */
    suspend fun setManual(input: ManualQuoteInput, expectedRevision: Long?): Outcome<Unit, StorageError>

    /** Unpins the manual value and keeps it as a last-resort candidate. */
    suspend fun removeOverride(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError>

    suspend fun deleteManual(key: QuoteKey, expectedRevision: Long): Outcome<Unit, StorageError>
}
