package com.mk.daftar.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

sealed interface QuoteKey {
    /** A merchant price for one karat, on the side of the merchant's trade. */
    data class Gold(val karat: GoldKarat, val side: MerchantSide) : QuoteKey

    /** The USD to EGP rate. */
    data object UsdEgp : QuoteKey
}

/** Explicit merchant direction. Never inferred from the magnitude of the two numbers. */
enum class MerchantSide {
    SELLS_TO_CUSTOMER,
    BUYS_FROM_CUSTOMER,
}

/**
 * How a quote came to exist.
 * - [LIVE]: retrieved from a remote provider in this fetch. It does not imply the data is fresh.
 * - [CACHED]: a remote quote restored from storage.
 * - [MANUAL]: entered by the user.
 * - [ESTIMATE]: derived locally, never fetched.
 */
enum class QuoteKind { LIVE, CACHED, MANUAL, ESTIMATE }

enum class QuoteProvider {
    ISAGHA,
    BANQUE_MISR,
    EXCHANGE_RATE_API,
    FAWAZ_CURRENCY_API,
    USER,
}

/**
 * What a USD to EGP rate measures. It is a disclosure, not a promise of execution.
 * - [BANK_TRANSFER_BUY]: the bank's transfer buying rate for USD (Banque Misr).
 * - [MID_MARKET_ESTIMATE]: an estimate from a market-rate provider, never labelled as a bank rate.
 * - [MANUAL]: entered by the user.
 * - [LEGACY_UNKNOWN]: a stored remote rate whose provider identity does not prove its basis.
 */
enum class FxBasis {
    BANK_TRANSFER_BUY,
    MID_MARKET_ESTIMATE,
    MANUAL,
    LEGACY_UNKNOWN,
    ;

    /** Whether this basis may describe a quote from [provider]. */
    fun isAllowedFor(provider: QuoteProvider): Boolean = when (provider) {
        QuoteProvider.BANQUE_MISR -> this == BANK_TRANSFER_BUY

        QuoteProvider.EXCHANGE_RATE_API, QuoteProvider.FAWAZ_CURRENCY_API ->
            this == MID_MARKET_ESTIMATE ||
                this == LEGACY_UNKNOWN

        QuoteProvider.USER -> this == MANUAL

        QuoteProvider.ISAGHA -> this == LEGACY_UNKNOWN
    }

    companion object {
        /** The basis that a provider's identity proves. Only an unproven stored row uses [LEGACY_UNKNOWN]. */
        fun defaultFor(provider: QuoteProvider): FxBasis = when (provider) {
            QuoteProvider.BANQUE_MISR -> BANK_TRANSFER_BUY
            QuoteProvider.EXCHANGE_RATE_API, QuoteProvider.FAWAZ_CURRENCY_API -> MID_MARKET_ESTIMATE
            QuoteProvider.USER -> MANUAL
            QuoteProvider.ISAGHA -> LEGACY_UNKNOWN
        }
    }
}

/** The time the provider says its data describes. Kept separate from the time we fetched it. */
sealed interface SourceTime {
    data class Timestamp(val value: Instant) : SourceTime

    /** A date without a time of day. Never converted to an exact instant. */
    data class DateOnly(val value: LocalDate) : SourceTime

    data object Unknown : SourceTime
}

/**
 * Where a quote came from and how it was classified.
 *
 * Remote kinds ([QuoteKind.LIVE], [QuoteKind.CACHED]) require a remote provider, a fetch time and a source URL.
 * Manual and estimated quotes use [QuoteProvider.USER] and have no fetch time or source URL.
 * Only manual quotes may be pinned.
 */
data class QuoteProvenance(
    val provider: QuoteProvider,
    val sourceUrl: String?,
    val sourceTime: SourceTime,
    val fetchedAt: Instant?,
    val recordedAt: Instant,
    val kind: QuoteKind,
    val manualPinned: Boolean,
    val note: String?,
) {
    init {
        when (kind) {
            QuoteKind.LIVE, QuoteKind.CACHED -> {
                require(provider != QuoteProvider.USER) { "remote quotes need a remote provider" }
                require(fetchedAt != null) { "remote quotes need a fetch time" }
                require(sourceUrl != null) { "remote quotes need a source URL" }
            }

            QuoteKind.MANUAL, QuoteKind.ESTIMATE -> {
                require(provider == QuoteProvider.USER) { "$kind quotes come from the user" }
                require(fetchedAt == null) { "$kind quotes are never fetched" }
                require(sourceUrl == null) { "$kind quotes have no source URL" }
            }
        }
        require(manualPinned.not() || kind == QuoteKind.MANUAL) { "only manual quotes may be pinned" }
        sourceUrl?.let { require(it.startsWith("https://")) { "source URL must be HTTPS" } }
    }

    /** Remote quotes read back from storage become [QuoteKind.CACHED]; manual and estimated quotes are unchanged. */
    fun restoredFromStorage(): QuoteProvenance = if (kind == QuoteKind.LIVE) copy(kind = QuoteKind.CACHED) else this

    companion object {
        /** Provenance for a remote quote fetched now from [provider]. */
        fun live(
            provider: QuoteProvider,
            sourceUrl: String,
            sourceTime: SourceTime,
            fetchedAt: Instant,
        ): QuoteProvenance = QuoteProvenance(
            provider = provider,
            sourceUrl = sourceUrl,
            sourceTime = sourceTime,
            fetchedAt = fetchedAt,
            recordedAt = fetchedAt,
            kind = QuoteKind.LIVE,
            manualPinned = false,
            note = null,
        )
    }
}

sealed interface PriceQuote {
    val provenance: QuoteProvenance
}

data class GoldQuote(val key: QuoteKey.Gold, val price: GoldPrice, override val provenance: QuoteProvenance) :
    PriceQuote

data class FxQuote(
    val rate: FxRate,
    override val provenance: QuoteProvenance,
    val basis: FxBasis = FxBasis.defaultFor(provenance.provider),
) : PriceQuote {
    init {
        require(basis.isAllowedFor(provenance.provider)) { "basis does not match the quote provider" }
    }

    val key: QuoteKey.UsdEgp
        get() = QuoteKey.UsdEgp
}
