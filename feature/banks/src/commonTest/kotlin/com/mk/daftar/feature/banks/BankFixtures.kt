package com.mk.daftar.feature.banks

import com.mk.daftar.core.domain.repository.QuoteSnapshot
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.valuation.SelectedQuotes
import com.mk.daftar.core.domain.valuation.selectQuotes
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankAccount
import com.mk.daftar.core.model.BankAccountId
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import kotlin.time.Instant

internal val FIXTURE_NOW: Instant = Instant.parse("2026-10-12T12:00:00Z")

internal fun dec(text: String): Decimal = Decimal.parseCanonical(text)

internal fun egp(text: String): Money = Money(dec(text), Currency.EGP)

internal fun usd(text: String): Money = Money(dec(text), Currency.USD)

internal fun bank(id: String, name: String): Bank = Bank.create(BankId(id), name, FIXTURE_NOW, FIXTURE_NOW)

internal fun account(id: String, bankId: String, name: String, balance: Money, note: String? = null): BankAccount =
    BankAccount.create(
        id = BankAccountId(id),
        bankId = BankId(bankId),
        name = name,
        balance = balance,
        note = note,
        createdAt = FIXTURE_NOW,
        updatedAt = FIXTURE_NOW,
    )

private val sellKey: (GoldKarat) -> QuoteKey.Gold = { QuoteKey.Gold(it, MerchantSide.SELLS_TO_CUSTOMER) }

/** A remote gold sell quote fetched at [FIXTURE_NOW], as the refresh caches it. */
internal fun remoteGold(karat: GoldKarat, price: String): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        provider = QuoteProvider.ISAGHA,
        sourceUrl = "https://market.isagha.com/prices",
        sourceTime = SourceTime.Unknown,
        fetchedAt = FIXTURE_NOW,
    )
    return RemoteQuoteRecord(GoldQuote(sellKey(karat), GoldPrice(dec(price)), provenance), null, null)
}

/** A remote USD to EGP quote fetched at [FIXTURE_NOW]. */
internal fun remoteUsd(rate: String): RemoteQuoteRecord {
    val provenance = QuoteProvenance.live(
        provider = QuoteProvider.EXCHANGE_RATE_API,
        sourceUrl = "https://open.er-api.com/v6/latest/USD",
        sourceTime = SourceTime.Unknown,
        fetchedAt = FIXTURE_NOW,
    )
    return RemoteQuoteRecord(FxQuote(FxRate(dec(rate)), provenance), nextUpdateAt = null, attributionUrl = null)
}

/** Every remote quote the approved fixture needs: a USD rate of 48.50. */
internal fun quotesWithUsd(): SelectedQuotes = selectQuotes(
    QuoteSnapshot(
        remote = listOf(remoteUsd("48.50")),
        manual = emptyList(),
    ),
    FIXTURE_NOW,
)

/** The approved fixture's quotes: a USD rate, and no gold, which bank valuation does not need. */
internal fun noQuotes(): SelectedQuotes =
    selectQuotes(QuoteSnapshot(remote = emptyList(), manual = emptyList()), FIXTURE_NOW)

/** Misr USD 3,000 at 48.50 plus EGP 18,000; AlAhli EGP 180,000; CIB EGP 45,000. The banks total 388,500 EGP. */
internal fun seedApprovedFixture(repository: FakeBankRepository) {
    repository.seed(bank("bank-misr", "Bank Misr"))
    repository.seed(bank("alahli", "AlAhli"))
    repository.seed(bank("cib", "CIB"))
    repository.seedAccount(account("a-usd", "bank-misr", "USD savings", usd("3000")))
    repository.seedAccount(account("a-egp", "bank-misr", "Current account", egp("18000")))
    repository.seedAccount(account("a-alahli", "alahli", "Main", egp("180000")))
    repository.seedAccount(account("a-cib", "cib", "Savings", egp("45000")))
}
