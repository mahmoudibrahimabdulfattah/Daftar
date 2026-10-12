package com.mk.daftar.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ModelTest {

    private val t0: Instant = Instant.parse("2026-10-12T07:00:00Z")
    private val t1: Instant = Instant.parse("2026-10-12T08:00:00Z")

    private fun d(text: String) = Decimal.parseCanonical(text)
    private fun egp(text: String) = Money(d(text), Currency.EGP)

    @Test
    fun goldWeightMustBePositiveWithAtMostTwoDecimals() {
        assertEquals("3.25", GoldWeight(d("3.25")).grams.toPlainString())
        assertFailsWith<IllegalArgumentException> { GoldWeight(d("0")) }
        assertFailsWith<IllegalArgumentException> { GoldWeight(d("-1")) }
        assertFailsWith<IllegalArgumentException> { GoldWeight(d("1.001")) }
    }

    @Test
    fun goldPriceAndFxRateMustBePositive() {
        assertFailsWith<IllegalArgumentException> { GoldPrice(Decimal.ZERO) }
        assertFailsWith<IllegalArgumentException> { FxRate(d("-50")) }
        assertEquals(d("50.123456789012"), FxRate(d("50.123456789012")).egpPerUsd)
    }

    @Test
    fun moneyKeepsExactComputedPrecision() {
        val value = GoldWeight(d("3.29")).grams * d("5301.5")
        assertEquals("17441.935", Money(value, Currency.EGP).amount.toPlainString())
    }

    @Test
    fun identifiersMustBeNonBlank() {
        assertFailsWith<IllegalArgumentException> { GoldHoldingId(" ") }
        assertFailsWith<IllegalArgumentException> { BankId("") }
        assertEquals("abc", BankAccountId("abc").value)
    }

    @Test
    fun goldHoldingTrimsAndKeepsTheNoteOptional() {
        val holding = GoldHolding.create(
            id = GoldHoldingId("g1"),
            karat = GoldKarat.K21,
            form = GoldForm.JEWELRY,
            weight = GoldWeight(d("12.5")),
            note = "   ",
            createdAt = t0,
            updatedAt = t0,
        )
        assertNull(holding.note)
        assertEquals(GoldKarat.K21, holding.karat)
        assertEquals("12.5", holding.weight.grams.toPlainString())
    }

    @Test
    fun goldHoldingRejectsUpdatedBeforeCreated() {
        assertFailsWith<IllegalArgumentException> {
            GoldHolding(GoldHoldingId("g1"), GoldKarat.K24, GoldForm.BAR, GoldWeight(d("1")), null, t1, t0)
        }
    }

    @Test
    fun bankTrimsItsNameAndRejectsBlankNames() {
        val bank = Bank.create(BankId("b1"), "  بنك مصر  ", t0, t0)
        assertEquals("بنك مصر", bank.name)
        assertFailsWith<IllegalArgumentException> { Bank.create(BankId("b1"), "   ", t0, t0) }
    }

    @Test
    fun nameLengthCountsCodePoints() {
        val atLimit = "a".repeat(120)
        assertEquals(atLimit, Bank.create(BankId("b1"), atLimit, t0, t0).name)
        assertFailsWith<IllegalArgumentException> { Bank.create(BankId("b1"), "a".repeat(121), t0, t0) }
        // Astral symbols are two UTF-16 units but one code point, so 120 of them fit.
        val astral = "😀".repeat(120)
        assertEquals(astral, Bank.create(BankId("b1"), astral, t0, t0).name)
        assertFailsWith<IllegalArgumentException> { Bank.create(BankId("b1"), "😀".repeat(121), t0, t0) }
    }

    @Test
    fun bankAccountPreservesItsOriginalCurrency() {
        val account = BankAccount.create(
            id = BankAccountId("a1"),
            bankId = BankId("b1"),
            name = "Dollar account",
            balance = Money(d("1250.75"), Currency.USD),
            note = null,
            createdAt = t0,
            updatedAt = t0,
        )
        assertEquals(Currency.USD, account.balance.currency)
        assertEquals("1250.75", account.balance.amount.toPlainString())
    }

    @Test
    fun bankAccountBalanceIsNonNegativeWithCents() {
        assertFailsWith<IllegalArgumentException> {
            BankAccount.create(BankAccountId("a1"), BankId("b1"), "x", egp("-1"), null, t0, t0)
        }
        assertFailsWith<IllegalArgumentException> {
            BankAccount.create(BankAccountId("a1"), BankId("b1"), "x", egp("1.001"), null, t0, t0)
        }
        assertEquals(
            "0",
            BankAccount.create(
                BankAccountId("a1"),
                BankId("b1"),
                "x",
                egp("0"),
                null,
                t0,
                t0,
            ).balance.amount.toPlainString(),
        )
    }

    @Test
    fun bankAccountNoteIsTrimmedAndOptional() {
        val account = BankAccount.create(BankAccountId("a1"), BankId("b1"), "Main", egp("10"), "  ", t0, t0)
        assertNull(account.note)
        val withNote = BankAccount.create(BankAccountId("a1"), BankId("b1"), "Main", egp("10"), " savings ", t0, t0)
        assertEquals("savings", withNote.note)
    }

    @Test
    fun cashBalanceRejectsNegativeBalances() {
        assertFailsWith<IllegalArgumentException> {
            CashBalance.create(CashBalanceId("c1"), "Wallet", CashKind.WALLET, egp("-0.01"), null, t0, t0)
        }
        val cash = CashBalance.create(CashBalanceId("c1"), " Pocket ", CashKind.CASH, egp("200"), null, t0, t0)
        assertEquals("Pocket", cash.name)
        assertEquals(CashKind.CASH, cash.kind)
    }

    @Test
    fun zakatPaymentMustBePositiveEgpWithCents() {
        val paid = ZakatPayment.create(
            id = ZakatPaymentId("z1"),
            paidOn = LocalDate(2026, 10, 1),
            amount = egp("1500.5"),
            note = null,
            createdAt = t0,
            updatedAt = t0,
        )
        assertEquals("1500.5", paid.amount.amount.toPlainString())
        assertFailsWith<IllegalArgumentException> {
            ZakatPayment.create(
                ZakatPaymentId("z1"),
                LocalDate(2026, 10, 1),
                Money(d("10"), Currency.USD),
                null,
                t0,
                t0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ZakatPayment.create(ZakatPaymentId("z1"), LocalDate(2026, 10, 1), egp("0"), null, t0, t0)
        }
        assertFailsWith<IllegalArgumentException> {
            ZakatPayment.create(ZakatPaymentId("z1"), LocalDate(2026, 10, 1), egp("2.001"), null, t0, t0)
        }
    }

    @Test
    fun personalNoteBodyIsRequiredAndBounded() {
        val note = PersonalNote.create(NoteId("n1"), " Reminder ", "Check the sell price", t0, t0)
        assertEquals("Reminder", note.title)
        assertFailsWith<IllegalArgumentException> { PersonalNote.create(NoteId("n1"), "T", "   ", t0, t0) }
        assertFailsWith<IllegalArgumentException> {
            PersonalNote.create(NoteId("n1"), "T", "x".repeat(10_001), t0, t0)
        }
        assertEquals(10_000, PersonalNote.create(NoteId("n1"), "T", "x".repeat(10_000), t0, t0).body.length)
    }

    @Test
    fun noteLengthIsBoundedAfterTrimming() {
        assertFailsWith<IllegalArgumentException> {
            GoldHolding.create(
                GoldHoldingId("g1"),
                GoldKarat.K18,
                GoldForm.BAR,
                GoldWeight(d("1")),
                "n".repeat(4_001),
                t0,
                t0,
            )
        }
    }

    @Test
    fun remoteQuotesNeedAFetchTimeAndSource() {
        val provenance = QuoteProvenance.live(
            QuoteProvider.ISAGHA,
            "https://market.isagha.com/prices",
            SourceTime.Unknown,
            t0,
        )
        assertEquals(QuoteKind.LIVE, provenance.kind)
        assertEquals(t0, provenance.fetchedAt)
        assertFailsWith<IllegalArgumentException> {
            QuoteProvenance(QuoteProvider.ISAGHA, null, SourceTime.Unknown, t0, t0, QuoteKind.LIVE, false, null)
        }
        assertFailsWith<IllegalArgumentException> {
            QuoteProvenance(
                QuoteProvider.ISAGHA,
                "https://market.isagha.com/prices",
                SourceTime.Unknown,
                null,
                t0,
                QuoteKind.LIVE,
                false,
                null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            QuoteProvenance(
                QuoteProvider.USER,
                "https://market.isagha.com/prices",
                SourceTime.Unknown,
                t0,
                t0,
                QuoteKind.LIVE,
                false,
                null,
            )
        }
    }

    @Test
    fun remoteSourceUrlsMustBeHttps() {
        assertFailsWith<IllegalArgumentException> {
            QuoteProvenance(
                QuoteProvider.ISAGHA,
                "http://market.isagha.com/prices",
                SourceTime.Unknown,
                t0,
                t0,
                QuoteKind.LIVE,
                false,
                null,
            )
        }
    }

    @Test
    fun manualQuotesAreUserEnteredAndNeverFetched() {
        val manual =
            QuoteProvenance(QuoteProvider.USER, null, SourceTime.Unknown, null, t0, QuoteKind.MANUAL, true, "from shop")
        assertEquals(true, manual.manualPinned)
        assertFailsWith<IllegalArgumentException> {
            QuoteProvenance(QuoteProvider.USER, null, SourceTime.Unknown, t0, t0, QuoteKind.MANUAL, false, null)
        }
        assertFailsWith<IllegalArgumentException> {
            QuoteProvenance(QuoteProvider.ISAGHA, null, SourceTime.Unknown, null, t0, QuoteKind.MANUAL, false, null)
        }
    }

    @Test
    fun onlyManualQuotesMayBePinned() {
        assertFailsWith<IllegalArgumentException> {
            QuoteProvenance(QuoteProvider.USER, null, SourceTime.Unknown, null, t0, QuoteKind.ESTIMATE, true, null)
        }
        assertFailsWith<IllegalArgumentException> {
            QuoteProvenance(
                QuoteProvider.ISAGHA,
                "https://market.isagha.com/prices",
                SourceTime.Unknown,
                t0,
                t0,
                QuoteKind.LIVE,
                true,
                null,
            )
        }
    }

    @Test
    fun remoteQuotesBecomeCachedWhenReadBackButManualQuotesDoNot() {
        val live = QuoteProvenance.live(
            QuoteProvider.FAWAZ_CURRENCY_API,
            "https://example.test",
            SourceTime.Unknown,
            t0,
        )
        assertEquals(QuoteKind.CACHED, live.restoredFromStorage().kind)
        val manual =
            QuoteProvenance(QuoteProvider.USER, null, SourceTime.Unknown, null, t0, QuoteKind.MANUAL, false, null)
        assertEquals(QuoteKind.MANUAL, manual.restoredFromStorage().kind)
    }

    @Test
    fun dateOnlySourceTimeStaysDateOnly() {
        val time = SourceTime.DateOnly(LocalDate(2026, 10, 12))
        assertEquals(LocalDate(2026, 10, 12), time.value)
    }

    @Test
    fun fxQuoteExposesTheFixedUsdEgpKey() {
        val quote = FxQuote(
            FxRate(d("50.25")),
            QuoteProvenance.live(
                QuoteProvider.EXCHANGE_RATE_API,
                "https://open.er-api.com/v6/latest/USD",
                SourceTime.Unknown,
                t0,
            ),
        )
        assertEquals(QuoteKey.UsdEgp, quote.key)
    }

    @Test
    fun goldQuoteCarriesItsMerchantDirection() {
        val sell = QuoteKey.Gold(GoldKarat.K21, MerchantSide.SELLS_TO_CUSTOMER)
        val buy = QuoteKey.Gold(GoldKarat.K21, MerchantSide.BUYS_FROM_CUSTOMER)
        assertEquals(MerchantSide.SELLS_TO_CUSTOMER, sell.side)
        assertEquals(MerchantSide.BUYS_FROM_CUSTOMER, buy.side)
    }
}
