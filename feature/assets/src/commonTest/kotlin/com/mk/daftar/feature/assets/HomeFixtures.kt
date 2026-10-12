package com.mk.daftar.feature.assets

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.storage.Stored
import com.mk.daftar.core.domain.valuation.PortfolioInput
import com.mk.daftar.core.domain.valuation.PortfolioValuation
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.domain.valuation.valuePortfolio
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

internal fun egpAmount(text: String): Money = Money(dec(text), Currency.EGP)

internal fun usdAmount(text: String): Money = Money(dec(text), Currency.USD)

internal fun goldHolding(id: String, karat: GoldKarat, grams: String): GoldHolding = GoldHolding.create(
    id = GoldHoldingId(id),
    karat = karat,
    form = GoldForm.BAR,
    weight = GoldWeight(dec(grams)),
    note = null,
    createdAt = FIXTURE_NOW,
    updatedAt = FIXTURE_NOW,
)

internal fun bankAccount(id: String, bank: String, balance: Money): BankAccount = BankAccount.create(
    id = BankAccountId(id),
    bankId = BankId(bank),
    name = "Account $id",
    balance = balance,
    note = null,
    createdAt = FIXTURE_NOW,
    updatedAt = FIXTURE_NOW,
)

internal fun cashBalance(id: String, balance: Money): CashBalance = CashBalance.create(
    id = CashBalanceId(id),
    name = "Cash $id",
    kind = CashKind.CASH,
    balance = balance,
    note = null,
    createdAt = FIXTURE_NOW,
    updatedAt = FIXTURE_NOW,
)

internal fun sellKey(karat: GoldKarat): QuoteKey.Gold = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)

/** A remote quote fetched at [fetchedAt], as the refresh would have cached it. */
internal fun remoteGold(karat: GoldKarat, price: String, fetchedAt: Instant = FIXTURE_NOW): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        provider = QuoteProvider.ISAGHA,
        sourceUrl = "https://market.isagha.com/prices",
        sourceTime = SourceTime.Unknown,
        fetchedAt = fetchedAt,
    )
    return RemoteQuoteRecord(GoldQuote(sellKey(karat), GoldPrice(dec(price)), provenance), null, null)
}

internal fun remoteUsd(rate: String, fetchedAt: Instant = FIXTURE_NOW): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        provider = QuoteProvider.EXCHANGE_RATE_API,
        sourceUrl = "https://open.er-api.com/v6/latest/USD",
        sourceTime = SourceTime.Unknown,
        fetchedAt = fetchedAt,
    )
    return RemoteQuoteRecord(
        FxQuote(FxRate(dec(rate)), provenance),
        nextUpdateAt = null,
        attributionUrl = "https://www.exchangerate-api.com/docs/free",
    )
}

/** A pinned manual gold quote, recorded at [recordedAt]. */
internal fun pinnedManualGold(karat: GoldKarat, price: String, recordedAt: Instant): Stored<PriceQuote> {
    val provenance = QuoteProvenance(
        provider = QuoteProvider.USER,
        sourceUrl = null,
        sourceTime = SourceTime.Unknown,
        fetchedAt = null,
        recordedAt = recordedAt,
        kind = QuoteKind.MANUAL,
        manualPinned = true,
        note = null,
    )
    return Stored(GoldQuote(sellKey(karat), GoldPrice(dec(price)), provenance), revision = 1)
}

internal fun quotesOf(
    vararg remote: RemoteQuoteRecord,
    manual: List<Stored<PriceQuote>> = emptyList(),
): SelectedQuotes = selectQuotes(QuoteSnapshot(remote = remote.toList(), manual = manual), FIXTURE_NOW)

/** The approved fixture: gold 677,812.74, banks 388,500.00, cash 9,900.00. */
internal fun readyInput(): PortfolioInput = PortfolioInput(
    gold = listOf(
        goldHolding("g1", GoldKarat.K21, "80"),
        goldHolding("g2", GoldKarat.K21, "26.77"),
        goldHolding("g3", GoldKarat.K18, "3.29"),
    ),
    bankAccounts = listOf(
        bankAccount("a1", "bank-misr", egpAmount("163500")),
        bankAccount("a2", "alahli", egpAmount("180000")),
        bankAccount("a3", "cib", egpAmount("45000")),
    ),
    cash = listOf(
        cashBalance("c1", egpAmount("4900")),
        cashBalance("c2", egpAmount("5000")),
    ),
)

internal fun readyQuotes(): SelectedQuotes = quotesOf(
    remoteGold(GoldKarat.K24, "7069"),
    remoteGold(GoldKarat.K21, "6185"),
    remoteGold(GoldKarat.K18, "5301"),
    remoteUsd("48.50"),
)

internal fun valuationOf(input: PortfolioInput, quotes: SelectedQuotes): PortfolioValuation =
    valuePortfolio(input, quotes)
