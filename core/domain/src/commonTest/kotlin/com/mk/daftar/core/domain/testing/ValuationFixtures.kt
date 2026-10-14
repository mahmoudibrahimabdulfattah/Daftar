package com.mk.daftar.core.domain.testing

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.valuation.PortfolioInput
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
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
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.InvestmentAccount
import com.mk.daftar.core.model.InvestmentAccountId
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.PriceQuote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Instant

internal val NOW: Instant = Instant.parse("2026-10-12T12:00:00Z")

internal val USD_EGP: QuoteKey = QuoteKey.UsdEgp

internal fun dec(text: String): Decimal = Decimal.parseCanonical(text)

internal fun sell(karat: GoldKarat): QuoteKey.Gold = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)

internal fun buy(karat: GoldKarat): QuoteKey.Gold = QuoteKey.Gold(karat, MerchantSide.BUYS_FROM_CUSTOMER)

internal fun egp(amount: String): Money = Money(dec(amount), Currency.EGP)

internal fun usd(amount: String): Money = Money(dec(amount), Currency.USD)

private fun quoteOf(key: QuoteKey, value: String, provenance: QuoteProvenance): PriceQuote = when (key) {
    is QuoteKey.Gold -> GoldQuote(key, GoldPrice(dec(value)), provenance)
    QuoteKey.UsdEgp -> FxQuote(FxRate(dec(value)), provenance)
}

/** A remote quote as the cache holds it: [cached] restores LIVE to CACHED, as storage does. */
internal fun remote(
    key: QuoteKey,
    value: String,
    fetchedAt: Instant,
    provider: QuoteProvider = if (key is QuoteKey.Gold) QuoteProvider.ISAGHA else QuoteProvider.EXCHANGE_RATE_API,
    cached: Boolean = false,
    sourceTime: SourceTime = SourceTime.Unknown,
    nextUpdateAt: Instant? = null,
    attributionUrl: String? = "https://example.test/attribution",
): RemoteQuoteRecord {
    val live = QuoteProvenance.live(provider, "https://example.test/${provider.name}", sourceTime, fetchedAt)
    val provenance = if (cached) live.restoredFromStorage() else live
    return RemoteQuoteRecord(quoteOf(key, value, provenance), nextUpdateAt, attributionUrl)
}

/** A user-entered quote. Estimates use the same shape with [kind] set to ESTIMATE and never pinned. */
internal fun manual(
    key: QuoteKey,
    value: String,
    recordedAt: Instant,
    pinned: Boolean = false,
    kind: QuoteKind = QuoteKind.MANUAL,
    revision: Long = 1L,
): Stored<PriceQuote> {
    val provenance = QuoteProvenance(
        provider = QuoteProvider.USER,
        sourceUrl = null,
        sourceTime = SourceTime.Unknown,
        fetchedAt = null,
        recordedAt = recordedAt,
        kind = kind,
        manualPinned = pinned,
        note = null,
    )
    return Stored(quoteOf(key, value, provenance), revision)
}

internal fun snapshot(
    remote: List<RemoteQuoteRecord> = emptyList(),
    manual: List<Stored<PriceQuote>> = emptyList(),
): QuoteSnapshot = QuoteSnapshot(remote = remote, manual = manual)

/** The design quotes: 21K sell 6,185, 18K sell 5,301, USD 48.50, all fetched now. */
internal fun designQuotes(includeEighteen: Boolean = true): QuoteSnapshot = snapshot(
    remote = buildList {
        add(remote(sell(GoldKarat.K21), "6185", NOW))
        if (includeEighteen) add(remote(sell(GoldKarat.K18), "5301", NOW))
        add(remote(USD_EGP, "48.50", NOW))
    },
)

internal fun holding(id: String, karat: GoldKarat, form: GoldForm, grams: String): GoldHolding = GoldHolding.create(
    id = GoldHoldingId(id),
    karat = karat,
    form = form,
    weight = GoldWeight(dec(grams)),
    note = null,
    createdAt = NOW,
    updatedAt = NOW,
)

internal fun account(id: String, name: String, balance: Money): BankAccount = BankAccount.create(
    id = BankAccountId(id),
    bankId = BankId("bank-$id"),
    name = name,
    balance = balance,
    note = null,
    createdAt = NOW,
    updatedAt = NOW,
)

internal fun cashBalance(id: String, name: String, balance: Money): CashBalance = CashBalance.create(
    id = CashBalanceId(id),
    name = name,
    kind = CashKind.CASH,
    balance = balance,
    note = null,
    createdAt = NOW,
    updatedAt = NOW,
)

/** A broker account with one EGP value, valued at [NOW]. */
internal fun investment(id: String, amount: String, providerId: InstitutionId? = null): InvestmentAccount =
    InvestmentAccount.create(
        id = InvestmentAccountId(id),
        providerId = providerId,
        name = "Broker $id",
        amount = egp(amount),
        note = null,
        valueAsOf = NOW,
        convertedFromCashId = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

/** The illustrative design portfolio from the approved design. */
internal fun designInput(): PortfolioInput = PortfolioInput(
    gold = listOf(
        holding("bars-21", GoldKarat.K21, GoldForm.BAR, "80"),
        holding("jewelry-21", GoldKarat.K21, GoldForm.JEWELRY, "26.77"),
        holding("jewelry-18", GoldKarat.K18, GoldForm.JEWELRY, "3.29"),
    ),
    bankAccounts = listOf(
        account("misr-usd", "Misr savings", usd("3000")),
        account("misr-egp", "Misr current", egp("18000")),
        account("alahli", "AlAhli", egp("180000")),
        account("cib", "CIB", egp("45000")),
    ),
    cash = listOf(
        cashBalance("home", "Home cash", egp("2400")),
        cashBalance("thundr", "Thundr wallet", egp("7500")),
    ),
)
