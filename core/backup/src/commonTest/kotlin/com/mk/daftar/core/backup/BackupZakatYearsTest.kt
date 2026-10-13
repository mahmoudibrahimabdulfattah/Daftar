package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.ZakatYear
import com.mk.daftar.core.security.createPasswordCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

/**
 * Version-two payloads: declarations, the tracking start and payment links round-trip, version-one payloads still
 * decode with no declarations, and invalid declarations are rejected whole. The invalid cases are sealed by hand, with
 * the same primitives the codec uses, so each one is an authentic file whose content is wrong.
 */
class BackupZakatYearsTest {

    private val crypto = createPasswordCrypto()
    private val codec = BackupCodec(crypto)
    private val password = "correct horse battery"
    private val salt = ByteArray(16) { (it + 1).toByte() }
    private val nonce = ByteArray(24) { (it + 3).toByte() }

    private fun withYears(): BackupSnapshot {
        val base = BackupFixtures.snapshot()
        return base.copy(
            zakatPayments = base.zakatPayments.map { it.copy(forHijriYear = 1447) } +
                base.zakatPayments.single().copy(
                    id = com.mk.daftar.core.model.ZakatPaymentId("zakat-2"),
                    forHijriYear = null,
                ),
            zakatYears = listOf(
                ZakatYear(1447, paid = true, markedAt = BackupFixtures.updated, overrideDays = 0, userAdjusted = false),
                ZakatYear(1448, paid = false, markedAt = null, overrideDays = -1, userAdjusted = true),
            ),
            zakatTrackingStartYear = 1447,
        )
    }

    private fun canonicalText(snapshot: BackupSnapshot): String = BackupJson.encodeToString(
        PayloadDocument.serializer(),
        snapshot.toDocument(BackupFixtures.exportedAt, "1.0"),
    )

    /** Seals [text] as the payload of a file, with the codec's header, key derivation and cipher. */
    private suspend fun sealed(text: String): ByteArray {
        val key = assertIs<Outcome.Success<com.mk.daftar.core.security.SecretBytes>>(
            crypto.deriveKey(password.encodeToByteArray(), salt, BackupFormat.MEMORY_KIB, BackupFormat.PASSES),
        ).data
        val plaintext = text.encodeToByteArray()
        val header = BackupHeader(salt, nonce, plaintext.size + BackupFormat.TAG_BYTES).encode()
        return header + crypto.seal(key, nonce, header, plaintext)
    }

    private suspend fun decodeText(text: String): Outcome<DecodedBackup, BackupError> =
        codec.decode(sealed(text), password.encodeToByteArray())

    private suspend fun assertRejected(text: String, error: BackupError = BackupError.CorruptPayload) {
        assertEquals(Outcome.Failure(error), decodeText(text))
    }

    @Test
    fun versionTwoRoundTripKeepsDeclarationsTrackingAndPaymentLinks() = runTest {
        val snapshot = withYears()
        val file = assertIs<Outcome.Success<ByteArray>>(
            codec.encode(snapshot, password.encodeToByteArray(), BackupFixtures.exportedAt, "1.0"),
        ).data
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(
            codec.decode(file, password.encodeToByteArray()),
        ).data
        assertEquals(snapshot, decoded.snapshot)
        assertEquals(listOf(1447, 1448), decoded.snapshot.zakatYears.map { it.hijriYear })
        assertEquals(1447, decoded.snapshot.zakatTrackingStartYear)
        assertEquals(null, decoded.snapshot.zakatPayments.last().forHijriYear)
    }

    @Test
    fun versionOneBackupsDecodeWithoutDeclarationsOrPaymentLinks() = runTest {
        val base = BackupFixtures.snapshot()
        val current = base.toDocument(BackupFixtures.exportedAt, "1.0")
        val versionOne = PayloadDocumentV1(
            schemaVersion = 1,
            exportedAt = current.exportedAt,
            appVersion = current.appVersion,
            preferences = current.preferences,
            goldHoldings = current.goldHoldings,
            banks = current.banks,
            bankAccounts = current.bankAccounts,
            cashBalances = current.cashBalances,
            zakatPayments = current.zakatPayments.map {
                ZakatPayloadV1(it.id, it.paidOn, it.amount, it.currency, it.note, it.createdAt, it.updatedAt)
            },
            personalNotes = current.personalNotes,
            remoteQuotes = current.remoteQuotes,
            manualQuotes = current.manualQuotes,
        )
        val text = BackupJson.encodeToString(PayloadDocumentV1.serializer(), versionOne)
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(decodeText(text)).data
        assertEquals(base, decoded.snapshot)
        assertEquals(emptyList(), decoded.snapshot.zakatYears)
        assertEquals(null, decoded.snapshot.zakatTrackingStartYear)
        assertEquals(null, decoded.snapshot.zakatPayments.single().forHijriYear)
    }

    @Test
    fun aPayloadNewerThanVersionTwoIsRefused() = runTest {
        val text = canonicalText(withYears()).replace("\"schemaVersion\":2", "\"schemaVersion\":3")
        assertRejected(text, BackupError.UnsupportedFormat)
    }

    @Test
    fun aYearOutsideTheSupportedRangeIsRejected() = runTest {
        assertRejected(canonicalText(withYears()).replace("\"hijriYear\":1447", "\"hijriYear\":1601"))
        assertRejected(canonicalText(withYears()).replace("\"hijriYear\":1448", "\"hijriYear\":1299"))
    }

    @Test
    fun duplicateYearsAreRejected() = runTest {
        assertRejected(canonicalText(withYears()).replace("\"hijriYear\":1448", "\"hijriYear\":1447"))
    }

    @Test
    fun anInvalidDateAdjustmentIsRejected() = runTest {
        assertRejected(canonicalText(withYears()).replace("\"overrideDays\":-1", "\"overrideDays\":2"))
    }

    @Test
    fun aPaidYearWithoutAMarkTimeIsRejected() = runTest {
        val text = canonicalText(withYears()).replace(Regex("\"markedAt\":\"[^\"]*\""), "\"markedAt\":null")
        assertRejected(text)
    }

    @Test
    fun aPaymentLinkOutsideTheSupportedRangeIsRejected() = runTest {
        assertRejected(canonicalText(withYears()).replace("\"forHijriYear\":1447", "\"forHijriYear\":1200"))
    }

    @Test
    fun declarationsWithoutATrackingStartAreRejected() = runTest {
        assertRejected(
            canonicalText(withYears()).replace("\"zakatTrackingStartYear\":1447", "\"zakatTrackingStartYear\":null"),
        )
    }

    @Test
    fun aRejectedPayloadProducesNoSnapshot() = runTest {
        val result = decodeText(canonicalText(withYears()).replace("\"hijriYear\":1448", "\"hijriYear\":1447"))
        assertEquals(null, (result as? Outcome.Success<DecodedBackup>)?.data)
        assertNotNull(result as? Outcome.Failure<BackupError>)
    }
}
