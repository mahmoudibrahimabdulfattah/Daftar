package com.mk.daftar.core.backup

import com.mk.daftar.core.domain.repository.AppLanguage
import com.mk.daftar.core.domain.repository.InactivityTimeout
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.repository.ThemeMode
import com.mk.daftar.core.model.Bank
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
import com.mk.daftar.core.model.NoteId
import com.mk.daftar.core.model.PersonalNote
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.model.ZakatPayment
import com.mk.daftar.core.model.ZakatPaymentId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Deterministic records covering every collection, Unicode text, both quote kinds and both source-time shapes. */
internal object BackupFixtures {
    val created: Instant = Instant.parse("2026-10-10T09:00:00Z")
    val updated: Instant = Instant.parse("2026-10-11T10:30:00.5Z")
    val exportedAt: Instant = Instant.parse("2026-10-12T09:45:00Z")

    fun preferences(): BackupPreferences = BackupPreferences(
        themeMode = ThemeMode.DARK,
        language = AppLanguage.EN,
        inactivityTimeout = InactivityTimeout.SECONDS_300,
    )

    fun snapshot(): BackupSnapshot = BackupSnapshot(
        preferences = preferences(),
        goldHoldings = listOf(
            GoldHolding.create(
                id = GoldHoldingId("gold-1"),
                karat = GoldKarat.K21,
                form = GoldForm.BAR,
                weight = GoldWeight(Decimal.parseCanonical("3.25")),
                note = "خزنة المنزل",
                createdAt = created,
                updatedAt = updated,
            ),
        ),
        banks = listOf(Bank.create(BankId("bank-1"), "بنك مصر", created, created)),
        bankAccounts = listOf(
            BankAccount.create(
                id = BankAccountId("account-1"),
                bankId = BankId("bank-1"),
                name = "Current",
                balance = Money(Decimal.parseCanonical("12345.67"), Currency.EGP),
                note = null,
                createdAt = created,
                updatedAt = updated,
            ),
        ),
        cashBalances = listOf(
            CashBalance.create(
                id = CashBalanceId("cash-1"),
                name = "Wallet",
                kind = CashKind.WALLET,
                balance = Money(Decimal.parseCanonical("900"), Currency.EGP),
                note = "\"quoted\" line\nbreak",
                createdAt = created,
                updatedAt = created,
            ),
        ),
        zakatPayments = listOf(
            ZakatPayment.create(
                id = ZakatPaymentId("zakat-1"),
                paidOn = LocalDate(2026, 10, 1),
                amount = Money(Decimal.parseCanonical("250.5"), Currency.EGP),
                note = null,
                createdAt = created,
                updatedAt = created,
            ),
        ),
        personalNotes = listOf(PersonalNote.create(NoteId("note-1"), "ملاحظة", "نص 🙂", created, updated)),
        remoteQuotes = listOf(
            RemoteQuoteRecord(
                quote = GoldQuote(
                    key = QuoteKey.Gold(GoldKarat.K21, MerchantSide.BUYS_FROM_CUSTOMER),
                    price = GoldPrice(Decimal.parseCanonical("5301.25")),
                    provenance = QuoteProvenance(
                        provider = QuoteProvider.ISAGHA,
                        sourceUrl = "https://market.isagha.com/prices",
                        sourceTime = SourceTime.Timestamp(created),
                        fetchedAt = updated,
                        recordedAt = updated,
                        kind = QuoteKind.CACHED,
                        manualPinned = false,
                        note = null,
                    ),
                ),
                nextUpdateAt = null,
                attributionUrl = "https://market.isagha.com/prices",
            ),
            RemoteQuoteRecord(
                quote = FxQuote(
                    rate = FxRate(Decimal.parseCanonical("48.5")),
                    provenance = QuoteProvenance(
                        provider = QuoteProvider.EXCHANGE_RATE_API,
                        sourceUrl = "https://open.er-api.com/v6/latest/USD",
                        sourceTime = SourceTime.DateOnly(LocalDate(2026, 10, 10)),
                        fetchedAt = updated,
                        recordedAt = updated,
                        kind = QuoteKind.CACHED,
                        manualPinned = false,
                        note = null,
                    ),
                ),
                nextUpdateAt = updated,
                attributionUrl = null,
            ),
        ),
        manualQuotes = listOf(
            GoldQuote(
                key = QuoteKey.Gold(GoldKarat.K21, MerchantSide.SELLS_TO_CUSTOMER),
                price = GoldPrice(Decimal.parseCanonical("6185.5")),
                provenance = QuoteProvenance(
                    provider = QuoteProvider.USER,
                    sourceUrl = null,
                    sourceTime = SourceTime.Timestamp(updated),
                    fetchedAt = null,
                    recordedAt = updated,
                    kind = QuoteKind.MANUAL,
                    manualPinned = true,
                    note = "Local goldsmith",
                ),
            ),
        ),
    )

    /** A small snapshot whose encoded form is short enough to pin as a golden vector. */
    fun minimalSnapshot(): BackupSnapshot = BackupSnapshot(
        preferences = preferences(),
        goldHoldings = listOf(
            GoldHolding.create(
                id = GoldHoldingId("gold-1"),
                karat = GoldKarat.K18,
                form = GoldForm.JEWELRY,
                weight = GoldWeight(Decimal.parseCanonical("3.29")),
                note = null,
                createdAt = created,
                updatedAt = created,
            ),
        ),
        banks = emptyList(),
        bankAccounts = emptyList(),
        cashBalances = emptyList(),
        zakatPayments = emptyList(),
        personalNotes = emptyList(),
        remoteQuotes = emptyList(),
        manualQuotes = emptyList(),
    )
}
