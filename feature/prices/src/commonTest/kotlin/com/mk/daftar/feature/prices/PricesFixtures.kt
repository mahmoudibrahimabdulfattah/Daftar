package com.mk.daftar.feature.prices

import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.model.CashBalance
import com.mk.daftar.core.model.CashBalanceId
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Instant

internal val FIXTURE_NOW: Instant = Instant.parse("2026-10-12T12:00:00Z")

internal fun dec(text: String): Decimal = Decimal.parseCanonical(text)

internal fun sellKey(karat: GoldKarat): QuoteKey.Gold = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)

internal fun buyKey(karat: GoldKarat): QuoteKey.Gold = QuoteKey.Gold(karat, MerchantSide.BUYS_FROM_CUSTOMER)

private const val ISAGHA_URL = "https://market.isagha.com/prices"
private const val EXCHANGE_URL = "https://open.er-api.com/v6/latest/USD"
private const val FAWAZ_URL = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"

/** A remote gold quote as the iSagha source returns it, live or restored from the cache. */
internal fun remoteGold(
    karat: GoldKarat,
    price: String,
    side: MerchantSide = MerchantSide.SELLS_TO_CUSTOMER,
    fetchedAt: Instant = FIXTURE_NOW,
    cached: Boolean = false,
    sourceTime: SourceTime = SourceTime.Timestamp(fetchedAt),
): RemoteQuoteRecord {
    val live = QuoteProvenance.live(QuoteProvider.ISAGHA, ISAGHA_URL, sourceTime, fetchedAt)
    val provenance = if (cached) live.restoredFromStorage() else live
    return RemoteQuoteRecord(
        quote = GoldQuote(QuoteKey.Gold(karat, side), GoldPrice(dec(price)), provenance),
        nextUpdateAt = null,
        attributionUrl = null,
    )
}

/** A remote USD rate from the exchange-rate provider, which carries its attribution. */
internal fun remoteUsd(rate: String, fetchedAt: Instant = FIXTURE_NOW, cached: Boolean = false): RemoteQuoteRecord {
    val live = QuoteProvenance.live(
        QuoteProvider.EXCHANGE_RATE_API,
        EXCHANGE_URL,
        SourceTime.Timestamp(fetchedAt),
        fetchedAt,
    )
    val provenance = if (cached) live.restoredFromStorage() else live
    return RemoteQuoteRecord(
        quote = FxQuote(FxRate(dec(rate)), provenance),
        nextUpdateAt = null,
        attributionUrl = "https://www.exchangerate-api.com",
    )
}

/** The fallback USD source, which dates its rate and carries no exchange-rate attribution. */
internal fun fallbackUsd(rate: String, fetchedAt: Instant = FIXTURE_NOW): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        QuoteProvider.FAWAZ_CURRENCY_API,
        FAWAZ_URL,
        SourceTime.DateOnly(kotlinx.datetime.LocalDate(2026, 10, 12)),
        fetchedAt,
    )
    return RemoteQuoteRecord(FxQuote(FxRate(dec(rate)), provenance), nextUpdateAt = null, attributionUrl = null)
}

/** A manual gold sell price or USD rate, with its revision and pinned state. */
internal fun manualGold(
    karat: GoldKarat,
    price: String,
    revision: Long,
    pinned: Boolean,
    recordedAt: Instant = FIXTURE_NOW,
    note: String? = null,
): Stored<PriceQuote> = Stored(
    GoldQuote(
        QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER),
        GoldPrice(dec(price)),
        manualProvenance(pinned, recordedAt, note),
    ),
    revision,
)

internal fun manualUsd(
    rate: String,
    revision: Long,
    pinned: Boolean,
    recordedAt: Instant = FIXTURE_NOW,
): Stored<PriceQuote> = Stored(FxQuote(FxRate(dec(rate)), manualProvenance(pinned, recordedAt, null)), revision)

private fun manualProvenance(pinned: Boolean, recordedAt: Instant, note: String?): QuoteProvenance = QuoteProvenance(
    provider = QuoteProvider.USER,
    sourceUrl = null,
    sourceTime = SourceTime.Unknown,
    fetchedAt = null,
    recordedAt = recordedAt,
    kind = QuoteKind.MANUAL,
    manualPinned = pinned,
    note = note,
)

internal fun goldHolding(id: String, karat: GoldKarat, form: GoldForm, grams: String): GoldHolding = GoldHolding.create(
    id = GoldHoldingId(id),
    karat = karat,
    form = form,
    weight = GoldWeight(dec(grams)),
    note = null,
    createdAt = FIXTURE_NOW,
    updatedAt = FIXTURE_NOW,
)

internal fun usdCash(id: String, amount: String): CashBalance = CashBalance.create(
    id = CashBalanceId(id),
    name = "USD cash",
    kind = CashKind.CASH,
    balance = Money(dec(amount), Currency.USD),
    note = null,
    createdAt = FIXTURE_NOW,
    updatedAt = FIXTURE_NOW,
)

/** A snapshot with every sell and buy price, and the USD rate, all live. */
internal fun fullSnapshot(): com.mk.daftar.core.domain.repository.QuoteSnapshot =
    com.mk.daftar.core.domain.repository.QuoteSnapshot(
        remote = listOf(
            remoteGold(GoldKarat.K24, "7069"),
            remoteGold(GoldKarat.K21, "6185"),
            remoteGold(GoldKarat.K18, "5301"),
            remoteGold(GoldKarat.K24, "7012", MerchantSide.BUYS_FROM_CUSTOMER),
            remoteGold(GoldKarat.K21, "6135", MerchantSide.BUYS_FROM_CUSTOMER),
            remoteGold(GoldKarat.K18, "5259", MerchantSide.BUYS_FROM_CUSTOMER),
            remoteUsd("48.50"),
        ),
        manual = emptyList(),
    )
