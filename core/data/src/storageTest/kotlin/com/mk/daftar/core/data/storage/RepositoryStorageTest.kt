package com.mk.daftar.core.data.storage

import com.mk.daftar.core.common.DataError
import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.PayloadFailure
import com.mk.daftar.core.data.session.EngineOpen
import com.mk.daftar.core.domain.repository.BankAccountInput
import com.mk.daftar.core.domain.repository.BankInput
import com.mk.daftar.core.domain.repository.CashBalanceInput
import com.mk.daftar.core.domain.repository.GoldHoldingInput
import com.mk.daftar.core.domain.repository.ManualQuoteInput
import com.mk.daftar.core.domain.repository.PersonalNoteInput
import com.mk.daftar.core.domain.repository.RefreshStepResult
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.domain.repository.ZakatPaymentInput
import com.mk.daftar.core.domain.storage.StorageError
import com.mk.daftar.core.domain.storage.VaultRead
import com.mk.daftar.core.model.CashKind
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldPrice
import com.mk.daftar.core.model.GoldQuote
import com.mk.daftar.core.model.GoldWeight
import com.mk.daftar.core.model.MerchantSide
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.model.QuoteKey
import com.mk.daftar.core.model.QuoteKind
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.network.FxAttemptFailure
import com.mk.daftar.core.network.FxFetch
import com.mk.daftar.core.network.FxFetchFailure
import com.mk.daftar.core.network.GoldFetch
import com.mk.daftar.core.network.MerchantGoldQuotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * Real Room over SQLCipher, through the production engine and repositories. Each test owns its database location
 * and locks the vault before removing it.
 */
class RepositoryStorageTest {

    private val sourceUrl = "https://example.com/source"
    private val attributionUrl = "https://example.com/attribution"

    private fun withVault(block: suspend (StorageFixture) -> Unit) = runTest {
        val location = newTestLocation()
        val fixture =
            StorageFixture(
                location,
                FakeGold(Outcome.Failure(DataError.Transport)),
                FakeFx(Outcome.Failure(FxFetchFailure(emptyList()))),
            )
        try {
            block(fixture)
        } finally {
            fixture.engine.lock()
            location.remove()
        }
    }

    private suspend fun StorageFixture.open(byte: Int = 1) = engine.unlock { Outcome.Success(key(byte)) }

    private fun egp(amount: String) = Money(Decimal.parseCanonical(amount), Currency.EGP)

    private fun goldWeight(grams: String) = GoldWeight(Decimal.parseCanonical(grams))

    @Test
    fun lockedRepositoriesExposeNoDataAndRefuseWrites() = withVault { f ->
        assertEquals(VaultRead.Locked, f.goldRecords.observeAll().first())
        assertEquals(
            StorageError.Locked,
            assertIs<Outcome.Failure<StorageError>>(
                f.goldRecords.create(GoldHoldingInput(GoldKarat.K24, GoldForm.BAR, goldWeight("1"), null)),
            ).error,
        )
    }

    @Test
    fun goldRecordsRoundTripWithRevisionsAndProtectCreationTime() = withVault { f ->
        assertIs<EngineOpen.Opened>(f.open())
        val created = f.goldRecords.create(GoldHoldingInput(GoldKarat.K21, GoldForm.BAR, goldWeight("12.5"), "vault"))
            .requireSuccess()
        assertEquals(1L, created.revision)

        val updated = f.goldRecords.update(
            created.value.id,
            expectedRevision = 1,
            input = GoldHoldingInput(GoldKarat.K21, GoldForm.JEWELRY, goldWeight("13"), null),
        ).requireSuccess()
        assertEquals(2L, updated.revision)
        assertEquals(created.value.createdAt, updated.value.createdAt)
        assertTrue(updated.value.updatedAt >= created.value.updatedAt)

        assertEquals(
            StorageError.Conflict,
            assertIs<Outcome.Failure<StorageError>>(
                f.goldRecords.update(
                    created.value.id,
                    1,
                    GoldHoldingInput(GoldKarat.K18, GoldForm.BAR, goldWeight("1"), null),
                ),
            ).error,
        )
        assertEquals(Outcome.Success(Unit), f.goldRecords.delete(created.value.id, expectedRevision = 2))
        assertEquals(
            StorageError.NotFound,
            assertIs<Outcome.Failure<StorageError>>(f.goldRecords.delete(created.value.id, expectedRevision = 2)).error,
        )
    }

    @Test
    fun aBankWithAccountsCannotBeDeletedAndItsDetailKeepsZeroAccountBanks() = withVault { f ->
        f.open()
        val bank = f.banks.create(BankInput("Bank A")).requireSuccess()
        val empty = f.banks.create(BankInput("Bank B")).requireSuccess()
        val account = f.banks.createAccount(
            BankAccountInput(bank.value.id, "Current", egp("1234567.89"), null),
        ).requireSuccess()

        assertEquals(
            StorageError.BankHasAccounts,
            assertIs<Outcome.Failure<StorageError>>(f.banks.delete(bank.value.id, bank.revision)).error,
        )

        val detail = f.banks.observeDetail(bank.value.id).first().requireReady()
        assertEquals(1, detail?.accounts?.size)
        assertEquals("1234567.89", detail?.accounts?.single()?.value?.balance?.amount?.toPlainString())

        val emptyDetail = f.banks.observeDetail(empty.value.id).first().requireReady()
        assertEquals(0, emptyDetail?.accounts?.size)
        assertEquals("Bank B", emptyDetail?.bank?.value?.name)

        assertEquals(Outcome.Success(Unit), f.banks.deleteAccount(account.value.id, account.revision))
        assertEquals(Outcome.Success(Unit), f.banks.delete(bank.value.id, bank.revision))
    }

    @Test
    fun concurrentAccountInsertsAndDeletesAreVisibleThroughTheFlow() = withVault { f ->
        f.open()
        val bank = f.banks.create(BankInput("Shared")).requireSuccess()
        val created = coroutineScope {
            (1..6).map { index ->
                async {
                    f.banks.createAccount(BankAccountInput(bank.value.id, "Account $index", egp("$index"), null))
                        .requireSuccess()
                }
            }.awaitAll()
        }
        created.take(2).forEach { account ->
            assertEquals(Outcome.Success(Unit), f.banks.deleteAccount(account.value.id, account.revision))
        }

        val remaining = f.banks.observeAccounts().awaitReady { accounts -> accounts.size == 4 }
        assertEquals(4, remaining.size)
    }

    @Test
    fun recordsPersistAcrossALockAndReopen() = withVault { f ->
        f.open(byte = 7)
        val cash = f.cash.create(CashBalanceInput("Wallet", CashKind.WALLET, egp("250.10"), "note")).requireSuccess()
        f.engine.lock()

        assertIs<EngineOpen.Opened>(f.open(byte = 7))
        val reread = f.cash.observe(cash.value.id).awaitReady { it != null }
        assertEquals("250.1", reread?.value?.balance?.amount?.toPlainString())
        assertEquals(cash.revision, 1L)
    }

    @Test
    fun zakatPaymentsAreListedNewestFirst() = withVault { f ->
        f.open()
        f.journal.zakat.create(ZakatPaymentInput(LocalDate(2025, 1, 5), egp("100"), null)).requireSuccess()
        f.journal.zakat.create(ZakatPaymentInput(LocalDate(2026, 3, 1), egp("200"), null)).requireSuccess()
        val list = f.journal.zakat.observeAll().awaitReady { it.size == 2 }
        assertEquals(LocalDate(2026, 3, 1), list.first().value.paidOn)
    }

    @Test
    fun aNoteCreatedAndEditedKeepsItsRevisionOrder() = withVault { f ->
        f.open()
        val note = f.journal.notes.create(PersonalNoteInput("Title", "Body")).requireSuccess()
        val edited = f.journal.notes.update(note.value.id, 1, PersonalNoteInput("Title", "Changed")).requireSuccess()
        assertEquals(2L, edited.revision)
        assertEquals(note.value.createdAt, edited.value.createdAt)
    }

    @Test
    fun manualQuotesAreCreateOnlyOverridesAndRefreshNeverTouchesThem() = withVault { f ->
        f.open()
        val manual = ManualQuoteInput(QuoteKey.UsdEgp, Decimal.parseCanonical("49.5"), note = "bank rate")
        assertEquals(Outcome.Success(Unit), f.prices.setManual(manual, expectedRevision = null))
        assertEquals(
            StorageError.Conflict,
            assertIs<Outcome.Failure<StorageError>>(f.prices.setManual(manual, expectedRevision = null)).error,
        )

        val now = Clock.System.now()
        f.fx.result = Outcome.Success(fxFetch(QuoteProvider.EXCHANGE_RATE_API, "50.2", now))
        f.prices.refresh().requireSuccess()

        val snapshot = f.prices.observeQuotes().awaitReady { it.remote.isNotEmpty() }
        assertEquals(1, snapshot.remote.size)
        val pinned = snapshot.manual.single()
        assertEquals("49.5", (pinned.value as com.mk.daftar.core.model.FxQuote).rate.egpPerUsd.toPlainString())
        assertTrue(pinned.value.provenance.manualPinned)

        assertEquals(Outcome.Success(Unit), f.prices.removeOverride(QuoteKey.UsdEgp, expectedRevision = 1))
        val unpinned = f.prices.observeQuotes().awaitReady {
            it.manual.isNotEmpty() &&
                !it.manual.single().value.provenance.manualPinned
        }
        assertEquals(2L, unpinned.manual.single().revision)
    }

    @Test
    fun aFailedFxFetchPreservesTheLastGoodRateAndGoldRefreshIsAtomicPerKarat() = withVault { f ->
        f.open()
        val now = Clock.System.now()
        f.gold.result = Outcome.Success(
            GoldFetch(
                quotes = mapOf(GoldKarat.K24 to goldPair(GoldKarat.K24, "4000", "3900", now)),
                rejected = mapOf(GoldKarat.K21 to DataError.InvalidPayload(PayloadFailure.Malformed)),
            ),
        )
        f.fx.result = Outcome.Success(fxFetch(QuoteProvider.EXCHANGE_RATE_API, "50", now))
        val first = f.prices.refresh().requireSuccess()
        assertIs<RefreshStepResult.Accepted>(first.gold.getValue(GoldKarat.K24))
        val afterFirst = f.prices.observeQuotes().awaitReady { it.remote.size == 3 }.remote
        assertTrue(afterFirst.all { it.quote.provenance.kind == QuoteKind.CACHED })

        f.gold.result = Outcome.Failure(DataError.Transport)
        f.fx.result =
            Outcome.Failure(
                FxFetchFailure(listOf(FxAttemptFailure(QuoteProvider.EXCHANGE_RATE_API, "a", DataError.Timeout))),
            )
        val second = f.prices.refresh().requireSuccess()
        assertIs<RefreshStepResult.Kept>(second.gold.getValue(GoldKarat.K24))
        assertIs<RefreshStepResult.Kept>(second.fx.getValue(QuoteProvider.EXCHANGE_RATE_API))

        val remote = f.prices.observeQuotes().awaitReady { it.remote.isNotEmpty() }.remote
        assertEquals(3, remote.size)
        assertNull(remote.firstOrNull { it.quote.provenance.provider == QuoteProvider.USER })
    }

    private fun fxFetch(provider: QuoteProvider, rate: String, at: Instant) = FxFetch(
        quote = FxQuote(
            rate = FxRate(Decimal.parseCanonical(rate)),
            provenance = QuoteProvenance.live(provider, sourceUrl, SourceTime.Timestamp(at), at),
        ),
        nextUpdateAt = null,
        attributionUrl = attributionUrl,
        precedingFailures = emptyList(),
    )

    private fun goldPair(karat: GoldKarat, sell: String, buy: String, at: Instant) = MerchantGoldQuotes(
        sell = GoldQuote(
            QuoteKey.Gold(karat, MerchantSide.SELLS_TO_CUSTOMER),
            GoldPrice(Decimal.parseCanonical(sell)),
            QuoteProvenance.live(QuoteProvider.ISAGHA, sourceUrl, SourceTime.Unknown, at),
        ),
        buy = GoldQuote(
            QuoteKey.Gold(karat, MerchantSide.BUYS_FROM_CUSTOMER),
            GoldPrice(Decimal.parseCanonical(buy)),
            QuoteProvenance.live(QuoteProvider.ISAGHA, sourceUrl, SourceTime.Unknown, at),
        ),
    )
}
