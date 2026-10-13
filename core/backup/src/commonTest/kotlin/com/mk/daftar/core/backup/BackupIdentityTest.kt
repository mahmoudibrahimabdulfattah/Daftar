package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.repository.RemoteQuoteRecord
import com.mk.daftar.core.model.Bank
import com.mk.daftar.core.model.BankId
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.FxBasis
import com.mk.daftar.core.model.FxQuote
import com.mk.daftar.core.model.FxRate
import com.mk.daftar.core.model.InstitutionId
import com.mk.daftar.core.model.QuoteProvenance
import com.mk.daftar.core.model.QuoteProvider
import com.mk.daftar.core.model.SourceTime
import com.mk.daftar.core.security.createPasswordCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Version three: bank identity and the FX basis, through the real codec and through the payload conversions. */
class BackupIdentityTest {

    private val codec = BackupCodec(createPasswordCrypto())
    private val password = "correct horse battery".encodeToByteArray()
    private val fetched = Instant.parse("2026-10-12T04:33:50Z")

    private fun identifiedSnapshot(): BackupSnapshot {
        val base = BackupFixtures.snapshot()
        val banks = listOf(
            Bank(
                id = BankId("bank-1"),
                name = "الأهلي الكبير",
                createdAt = fetched,
                updatedAt = fetched,
                institutionId = InstitutionId("eg.nbe"),
                identityReviewed = false,
            ),
            Bank(
                id = BankId("bank-2"),
                name = "Other one",
                createdAt = fetched,
                updatedAt = fetched,
                institutionId = InstitutionId("sa.future-bank"),
                identityReviewed = true,
            ),
            Bank(
                id = BankId("bank-3"),
                name = "Custom",
                createdAt = fetched,
                updatedAt = fetched,
                institutionId = null,
                identityReviewed = true,
            ),
        )
        val bankRemote = RemoteQuoteRecord(
            quote = FxQuote(
                rate = FxRate(Decimal.parseCanonical("52.77")),
                provenance = QuoteProvenance.live(
                    QuoteProvider.BANQUE_MISR,
                    "https://www.banquemisr.com/Home/CAPITAL%20MARKETS/Exchange%20Rates%20and%20Currencies",
                    SourceTime.Timestamp(Instant.parse("2026-10-12T03:00:00Z")),
                    fetched,
                ).restoredFromStorage(),
                basis = FxBasis.BANK_TRANSFER_BUY,
            ),
            nextUpdateAt = null,
            attributionUrl = null,
        )
        return base.copy(banks = banks, remoteQuotes = base.remoteQuotes + bankRemote)
    }

    @Test
    fun identityAndBasisRoundTripThroughVersionThree() = runTest {
        val snapshot = identifiedSnapshot()
        val file = assertIs<Outcome.Success<ByteArray>>(
            codec.encode(snapshot, password.copyOf(), BackupFixtures.exportedAt, "1.0"),
        ).data
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(
            codec.decode(file, password.copyOf()),
        ).data

        assertEquals(snapshot.banks, decoded.snapshot.banks)
        // Remote quotes are written in key order, so the comparison is by content, not by list position.
        assertEquals(snapshot.remoteQuotes.toSet(), decoded.snapshot.remoteQuotes.toSet())
        assertEquals(snapshot.copy(remoteQuotes = emptyList()), decoded.snapshot.copy(remoteQuotes = emptyList()))
    }

    @Test
    fun aVersionThreeDocumentWritesTheIdentityAndBasisFields() {
        val document = identifiedSnapshot().toDocument(BackupFixtures.exportedAt, "1.0")
        val text = BackupJson.encodeToString(PayloadDocument.serializer(), document)

        assertEquals(3, document.schemaVersion)
        assertEquals(true, text.contains("\"institutionId\":\"eg.nbe\",\"identityReviewed\":false"))
        assertEquals(true, text.contains("\"basis\":\"BANK_TRANSFER_BUY\""))
    }

    @Test
    fun aMalformedInstitutionIdIsRejectedWithTheWholePayload() {
        assertFailsWith<CorruptPayloadException> { withBankInstitution("EG.NBE").toSnapshot() }
    }

    @Test
    fun aTooLongInstitutionIdIsRejected() {
        assertFailsWith<CorruptPayloadException> { withBankInstitution("a".repeat(65)).toSnapshot() }
    }

    private fun withBankInstitution(institutionId: String): PayloadDocument {
        val document = identifiedSnapshot().toDocument(BackupFixtures.exportedAt, "1.0")
        val banks = document.banks.map {
            if (it.id == "bank-1") BankPayload(it.id, it.name, it.createdAt, it.updatedAt, institutionId, false) else it
        }
        return PayloadDocument(
            schemaVersion = document.schemaVersion,
            exportedAt = document.exportedAt,
            appVersion = document.appVersion,
            preferences = document.preferences,
            goldHoldings = document.goldHoldings,
            banks = banks,
            bankAccounts = document.bankAccounts,
            cashBalances = document.cashBalances,
            zakatPayments = document.zakatPayments,
            personalNotes = document.personalNotes,
            remoteQuotes = document.remoteQuotes,
            manualQuotes = document.manualQuotes,
            zakatTrackingStartYear = document.zakatTrackingStartYear,
            zakatYears = document.zakatYears,
        )
    }

    @Test
    fun aVersionTwoBankHasNoIdentityAndAVersionTwoRateGetsItsProvenBasis() {
        val version = identifiedSnapshot().toDocument(BackupFixtures.exportedAt, "1.0")
        val v2 = PayloadDocumentV2(
            schemaVersion = 2,
            exportedAt = version.exportedAt,
            appVersion = version.appVersion,
            preferences = version.preferences,
            goldHoldings = version.goldHoldings,
            banks = listOf(BankPayloadV2("bank-9", "Legacy", "2026-10-12T04:33:50Z", "2026-10-12T04:33:50Z")),
            bankAccounts = version.bankAccounts,
            cashBalances = version.cashBalances,
            zakatPayments = version.zakatPayments,
            personalNotes = version.personalNotes,
            remoteQuotes = listOf(
                legacyRemote("USD_EGP", "EXCHANGE_RATE_API"),
                legacyRemote("USD_EGP", "FAWAZ_CURRENCY_API"),
            ),
            manualQuotes = version.manualQuotes,
            zakatTrackingStartYear = version.zakatTrackingStartYear,
            zakatYears = version.zakatYears,
        )

        val current = v2.toCurrent()

        assertEquals(null, current.banks.single().institutionId)
        assertEquals(false, current.banks.single().identityReviewed)
        assertEquals(listOf("MID_MARKET_ESTIMATE", "MID_MARKET_ESTIMATE"), current.remoteQuotes.map { it.basis })
    }

    @Test
    fun anFxRateWithoutProvenIdentityIsLegacyUnknownNotMidMarket() {
        assertEquals("LEGACY_UNKNOWN", legacyRemote("USD_EGP", "ISAGHA").toCurrentForTest().basis)
        assertEquals(null, legacyRemote("GOLD_24_SELL", "ISAGHA").toCurrentForTest().basis)
    }

    private fun legacyRemote(key: String, provider: String) = RemoteQuotePayloadV2(
        quoteKey = key,
        provider = provider,
        value = "52.5",
        sourceUrl = "https://example.com/source",
        sourceTimeKind = "UNKNOWN",
        sourceTime = null,
        fetchedAt = "2026-10-12T04:33:50Z",
        recordedAt = "2026-10-12T04:33:50Z",
        note = null,
        nextUpdateAt = null,
        attributionUrl = null,
    )

    /** Runs the version-two conversion on one row, through a one-row document. */
    private fun RemoteQuotePayloadV2.toCurrentForTest(): RemoteQuotePayload {
        val document = PayloadDocumentV2(
            schemaVersion = 2,
            exportedAt = "2026-10-12T04:33:50Z",
            appVersion = "1.0",
            preferences = PreferencesPayload("SYSTEM", "EN", 60),
            goldHoldings = emptyList(),
            banks = emptyList(),
            bankAccounts = emptyList(),
            cashBalances = emptyList(),
            zakatPayments = emptyList(),
            personalNotes = emptyList(),
            remoteQuotes = listOf(this),
            manualQuotes = emptyList(),
            zakatTrackingStartYear = null,
            zakatYears = emptyList(),
        )
        return document.toCurrent().remoteQuotes.single()
    }
}
