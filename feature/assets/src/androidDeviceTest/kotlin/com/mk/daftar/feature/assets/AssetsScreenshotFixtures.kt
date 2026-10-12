package com.mk.daftar.feature.assets

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.valuation.PortfolioInput
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
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Instant

/* Screenshot-host fixtures: the approved figures through the real valuation path, as the Home host does. */

private val NOW: Instant = Instant.parse("2026-10-12T12:00:00Z")

private fun dec(text: String): Decimal = Decimal.parseCanonical(text)

private fun holding(id: String, karat: GoldKarat, grams: String) = GoldHolding.create(
    GoldHoldingId(id),
    karat,
    GoldForm.BAR,
    GoldWeight(dec(grams)),
    null,
    NOW,
    NOW,
)

private fun account(id: String, bank: String, egp: String) = BankAccount.create(
    BankAccountId(id),
    BankId(bank),
    "Account $id",
    Money(dec(egp), Currency.EGP),
    null,
    NOW,
    NOW,
)

private fun cash(id: String, egp: String) = CashBalance.create(
    CashBalanceId(id),
    "Cash $id",
    CashKind.CASH,
    Money(dec(egp), Currency.EGP),
    null,
    NOW,
    NOW,
)

private fun remoteGold(karat: GoldKarat, price: String): RemoteQuoteRecord {
    val key = QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER)
    val provenance = QuoteProvenance.live(
        QuoteProvider.ISAGHA,
        "https://market.isagha.com/prices",
        SourceTime.Unknown,
        NOW,
    )
    return RemoteQuoteRecord(GoldQuote(key, GoldPrice(dec(price)), provenance), null, null)
}

private fun remoteUsd(rate: String): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        QuoteProvider.EXCHANGE_RATE_API,
        "https://open.er-api.com/v6/latest/USD",
        SourceTime.Unknown,
        NOW,
    )
    return RemoteQuoteRecord(FxQuote(FxRate(dec(rate)), provenance), null, "https://www.exchangerate-api.com/docs/free")
}

private fun quotes(vararg records: RemoteQuoteRecord): SelectedQuotes =
    selectQuotes(QuoteSnapshot(remote = records.toList(), manual = emptyList()), NOW)

private fun input(): PortfolioInput = PortfolioInput(
    gold = listOf(
        holding("g1", GoldKarat.K21, "80"),
        holding("g2", GoldKarat.K21, "26.77"),
        holding("g3", GoldKarat.K18, "3.29"),
    ),
    bankAccounts = listOf(
        account("a1", "misr", "163500"),
        account("a2", "ahly", "180000"),
        account("a3", "cib", "45000"),
    ),
    cash = listOf(cash("c1", "4900"), cash("c2", "5000")),
)

private fun readyQuotes(): SelectedQuotes = quotes(
    remoteGold(GoldKarat.K24, "7069"),
    remoteGold(GoldKarat.K21, "6185"),
    remoteGold(GoldKarat.K18, "5301"),
    remoteUsd("48.50"),
)

fun readyAssets(): AssetsFigures = assetsFiguresOf(valuePortfolio(input(), readyQuotes()), bankCount = 3)

fun incompleteAssets(): AssetsFigures {
    val partial = quotes(remoteGold(GoldKarat.K24, "7069"), remoteGold(GoldKarat.K21, "6185"), remoteUsd("48.50"))
    return assetsFiguresOf(valuePortfolio(input(), partial), bankCount = 3)
}

fun emptyAssets(): AssetsFigures = assetsFiguresOf(
    valuePortfolio(PortfolioInput(emptyList(), emptyList(), emptyList()), SelectedQuotes.EMPTY),
    bankCount = 0,
)
