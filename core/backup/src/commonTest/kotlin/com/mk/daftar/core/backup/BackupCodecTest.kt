@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.backup

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.model.Currency
import com.mk.daftar.core.model.Decimal
import com.mk.daftar.core.model.Money
import com.mk.daftar.core.security.createPasswordCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The codec is exercised with the real libsodium primitives: Argon2id at 64 MiB and XChaCha20-Poly1305, so every
 * test here is a real round trip rather than a model of one.
 */
class BackupCodecTest {

    private val crypto = createPasswordCrypto()
    private val codec = BackupCodec(crypto)
    private val password = "correct horse battery"

    private fun pw(): ByteArray = password.encodeToByteArray()

    private suspend fun encode(snapshot: BackupSnapshot = BackupFixtures.snapshot()): ByteArray =
        assertIs<Outcome.Success<ByteArray>>(
            codec.encode(snapshot, pw(), BackupFixtures.exportedAt, "1.0"),
        ).data

    private suspend fun decodeFailure(file: ByteArray, pass: String = password): BackupError =
        assertIs<Outcome.Failure<BackupError>>(codec.decode(file, pass.encodeToByteArray())).error

    @Test
    fun roundTripRestoresEveryRecordPreferenceAndTimestamp() = runTest {
        val snapshot = BackupFixtures.snapshot()
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(codec.decode(encode(snapshot), pw())).data
        assertEquals(snapshot, decoded.snapshot)
        assertEquals(BackupFixtures.exportedAt, decoded.exportedAt)
        assertEquals("1.0", decoded.appVersion)
    }

    @Test
    fun freshSaltAndNonceForEveryExport() = runTest {
        val first = encode()
        val second = encode()
        val saltRange = 23 until 39
        val nonceRange = 40 until 64
        assertFalse(first.copyOfRange(saltRange.first, saltRange.last + 1).contentEquals(second.copyOfRange(23, 39)))
        assertFalse(first.copyOfRange(40, 64).contentEquals(second.copyOfRange(40, 64)))
        assertNotEquals(first.toList(), second.toList())
        assertTrue(saltRange.count() == 16 && nonceRange.count() == 24)
    }

    @Test
    fun samePlaintextAndKnownSaltAndNonceProduceTheSameBytes() = runTest {
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(24) { (0x20 + it).toByte() }
        val encode: suspend () -> Outcome<ByteArray, BackupError> = {
            codec.encodeWith(BackupFixtures.minimalSnapshot(), pw(), BackupFixtures.exportedAt, "1.0", salt, nonce)
        }
        val a = assertIs<Outcome.Success<ByteArray>>(encode()).data
        val b = assertIs<Outcome.Success<ByteArray>>(encode()).data
        assertContentEquals(a, b)
    }

    @Test
    fun goldenVectorDecodesToTheDocumentedSnapshot() = runTest {
        val file = GOLDEN_VECTOR_V1_HEX.hexToBytes()
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(codec.decode(file, pw())).data
        assertEquals(BackupFixtures.minimalSnapshot(), decoded.snapshot)
        assertEquals(BackupFixtures.exportedAt, decoded.exportedAt)
    }

    @Test
    fun goldenVersionTwoVectorStillDecodesToTheSameSnapshot() = runTest {
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(
            codec.decode(GOLDEN_VECTOR_V2_HEX.hexToBytes(), pw()),
        ).data
        assertEquals(BackupFixtures.minimalSnapshot(), decoded.snapshot)
    }

    @Test
    fun goldenVersionThreeVectorStillDecodesWithNoInvestmentAccounts() = runTest {
        val decoded = assertIs<Outcome.Success<DecodedBackup>>(
            codec.decode(GOLDEN_VECTOR_V3_HEX.hexToBytes(), pw()),
        ).data
        assertEquals(BackupFixtures.minimalSnapshot(), decoded.snapshot)
        assertTrue(decoded.snapshot.investmentAccounts.isEmpty())
    }

    @Test
    fun goldenVectorIsReproducedByTheFixedInputs() = runTest {
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(24) { (0x20 + it).toByte() }
        val file = assertIs<Outcome.Success<ByteArray>>(
            codec.encodeWith(BackupFixtures.minimalSnapshot(), pw(), BackupFixtures.exportedAt, "1.0", salt, nonce),
        ).data
        assertEquals(GOLDEN_VECTOR_HEX, file.toHex(), "golden vector changed; the format is frozen")
    }

    @Test
    fun wrongPasswordIsAuthenticationFailure() = runTest {
        assertEquals(BackupError.AuthenticationFailed, decodeFailure(encode(), "correct horse batter"))
    }

    @Test
    fun tamperedSaltIsRejected() = runTest {
        val file = encode()
        file[23] = (file[23].toInt() xor 0x01).toByte()
        assertEquals(BackupError.AuthenticationFailed, decodeFailure(file))
    }

    @Test
    fun tamperedNonceIsRejected() = runTest {
        val file = encode()
        file[40] = (file[40].toInt() xor 0x01).toByte()
        assertEquals(BackupError.AuthenticationFailed, decodeFailure(file))
    }

    @Test
    fun tamperedCiphertextAndTagAreRejected() = runTest {
        val file = encode()
        val middle = BackupFormat.HEADER_BYTES + 10
        file[middle] = (file[middle].toInt() xor 0x40).toByte()
        assertEquals(BackupError.AuthenticationFailed, decodeFailure(file))

        val tagged = encode()
        tagged[tagged.lastIndex] = (tagged.last().toInt() xor 0x01).toByte()
        assertEquals(BackupError.AuthenticationFailed, decodeFailure(tagged))
    }

    @Test
    fun tamperedVersionIsRejectedBeforeAnyKeyDerivation() = runTest {
        val file = encode()
        file[9] = 2
        assertEquals(BackupError.UnsupportedFormat, decodeFailure(file))
    }

    @Test
    fun truncatedFileIsNotABackup() = runTest {
        val file = encode()
        assertEquals(BackupError.NotABackup, decodeFailure(file.copyOf(file.size - 5)))
    }

    @Test
    fun payloadCarriesNoDeviceKeyOrUnlockMaterial() {
        val document = BackupFixtures.snapshot().toDocument(BackupFixtures.exportedAt, "1.0")
        val text = BackupJson.encodeToString(PayloadDocument.serializer(), document)
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(
            setOf(
                "schemaVersion",
                "exportedAt",
                "appVersion",
                "preferences",
                "goldHoldings",
                "banks",
                "bankAccounts",
                "cashBalances",
                "zakatPayments",
                "personalNotes",
                "remoteQuotes",
                "manualQuotes",
                "zakatTrackingStartYear",
                "zakatYears",
                "investmentAccounts",
            ),
            root.keys,
        )
        val preferences = root.getValue("preferences").jsonObject
        assertEquals(setOf("themeMode", "language", "inactivityTimeoutSeconds"), preferences.keys)
        // Exact keys only: "pinned" is the legitimate manual-quote state and is not a PIN.
        val keys = collectKeys(root)
        val forbidden = listOf(
            "pin", "dek", "dekId", "biometric", "biometricEnabled", "envelope", "backoff",
            "salt", "nonce", "password", "session",
        )
        for (name in forbidden) {
            assertFalse(name in keys, "payload must not carry $name")
        }
    }

    @Test
    fun payloadSerializesToTheSameBytesForTheSameSnapshot() {
        val first = BackupJson.encodeToString(
            PayloadDocument.serializer(),
            BackupFixtures.snapshot().toDocument(BackupFixtures.exportedAt, "1.0"),
        )
        val reversed = BackupFixtures.snapshot().copy(
            bankAccounts = BackupFixtures.snapshot().bankAccounts.reversed(),
            remoteQuotes = BackupFixtures.snapshot().remoteQuotes.reversed(),
        )
        val second = BackupJson.encodeToString(
            PayloadDocument.serializer(),
            reversed.toDocument(BackupFixtures.exportedAt, "1.0"),
        )
        assertEquals(first, second, "record order must not change the canonical payload")
    }

    @Test
    fun exceedingTheRecordLimitFailsBeforeEncryption() = runTest {
        val cash = BackupFixtures.snapshot().cashBalances.single()
        val many = BackupFixtures.minimalSnapshot().copy(
            cashBalances = List(BackupFormat.MAX_RECORDS + 1) { index ->
                cash.copy(id = com.mk.daftar.core.model.CashBalanceId("cash-$index"))
            },
        )
        val failure = assertIs<Outcome.Failure<BackupError>>(
            codec.encode(many, pw(), BackupFixtures.exportedAt, "1.0"),
        )
        assertEquals(BackupError.TooManyRecords, failure.error)
    }

    @Test
    fun exceedingThePlaintextLimitFailsExplicitly() = runTest {
        val note = BackupFixtures.snapshot().personalNotes.single()
        val body = "ب".repeat(10_000)
        val large = BackupFixtures.minimalSnapshot().copy(
            personalNotes = List(1_800) { index ->
                note.copy(id = com.mk.daftar.core.model.NoteId("note-$index"), body = body)
            },
        )
        val failure = assertIs<Outcome.Failure<BackupError>>(
            codec.encode(large, pw(), BackupFixtures.exportedAt, "1.0"),
        )
        assertEquals(BackupError.TooLarge, failure.error)
    }

    @Test
    fun passwordBytesAreWipedAfterEncoding() = runTest {
        val bytes = pw()
        codec.encode(BackupFixtures.minimalSnapshot(), bytes, BackupFixtures.exportedAt, "1.0")
        assertTrue(bytes.all { it == 0.toByte() }, "the caller's password buffer must be wiped")
    }

    @Test
    fun moneyAmountsKeepTheirExactText() {
        val money = Money(Decimal.parseCanonical("12345.67"), Currency.EGP)
        assertEquals("12345.67", money.amount.toPlainString())
    }

    private fun collectKeys(element: JsonElement): Set<String> = when (element) {
        is JsonObject -> element.keys + element.values.flatMap { collectKeys(it) }
        is JsonArray -> element.flatMap { collectKeys(it) }.toSet()
        else -> emptySet()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        "0123456789abcdef"[value shr 4].toString() + "0123456789abcdef"[value and 0x0F]
    }

    private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private companion object {
        /** Frozen version-one vector, kept so that version-one backups still decode to the same snapshot. */
        const val GOLDEN_VECTOR_V1_HEX: String =
            "4446545242414b00000101010001000000000003012010000102030405060708090a0b0c0d0e0f182021222324252627" +
                "28292a2b2c2d2e2f3031323334353637000001ccf4222d42449dacacda3b44d13135cff4fe0c7d7ca990f73737eb9c08" +
                "0263d24df3d8973042c0d9ea1673ba03640ad912cd6ef2d8cd1a4ebee801b224d5ac0fb15720f8bfc12c0d5a15f77f45" +
                "f492e09befa490c1af5b4c6edef05fa9ae1fb014bfebae6c64575e9b95012302435392c65549ecceb0d4d88233498e65" +
                "d907d7c17c4d4afe0d0f604f88303e4b71456803f7f04d8ece4c55514b66273e95c6c4b6246d49296fa51113edcfeb0c" +
                "ba0cf6560d50b6cc640170fa7cd413c55ef65b3364774675c51838df9b1c710205c63e6458fe5300810da05693ff0652" +
                "a92e2e1363a6a0064b3ae060933984f37d381ccc3087bef46efe6f30aac551f24ee40ae2460a9b287a0f97f4b207efa2" +
                "1e08436604917eb538bf326973ffb8f91f29458180bfafd836e9382c01c49cc60802b0b0e47a07ae8eeaa1a00b210694" +
                "8ce29931f83f1defc2257000cc6202d41e7a95327676bb361db603708c10499a66a74a5919f317e2503f819c392ec3dc" +
                "bdd5a65cb6b7ea9a47914f3ec7721425ffde11cc953c28b1b03dc76522470c19ce56ffdb7956ee2560adf3e41be566b4" +
                "c87b0417ca279a4f821f7ee8972fae245a5dd61f979c503ee7d389afb875b35917e1c3f9a5807da8216ed6ec2588dbac"

        /** Version-two vector: [BackupFixtures.minimalSnapshot] under the fixed salt and nonce, as version two wrote it. */
        const val GOLDEN_VECTOR_V2_HEX: String =
            "4446545242414b00000101010001000000000003012010000102030405060708090a0b0c0d0e0f182021222324252627" +
                "28292a2b2c2d2e2f3031323334353637000001faf4222d42449dacacda3b44d13135cff4fe0f7d7ca990f73737eb9c08" +
                "0263d24df3d8973042c0d9ea1673ba03640ad912cd6ef2d8cd1a4ebee801b224d5ac0fb15720f8bfc12c0d5a15f77f45" +
                "f492e09befa490c1af5b4c6edef05fa9ae1fb014bfebae6c64575e9b95012302435392c65549ecceb0d4d88233498e65" +
                "d907d7c17c4d4afe0d0f604f88303e4b71456803f7f04d8ece4c55514b66273e95c6c4b6246d49296fa51113edcfeb0c" +
                "ba0cf6560d50b6cc640170fa7cd413c55ef65b3364774675c51838df9b1c710205c63e6458fe5300810da05693ff0652" +
                "a92e2e1363a6a0064b3ae060933984f37d381ccc3087bef46efe6f30aac551f24ee40ae2460a9b287a0f97f4b207efa2" +
                "1e08436604917eb538bf326973ffb8f91f29458180bfafd836e9382c01c49cc60802b0b0e47a07ae8eeaa1a00b210694" +
                "8ce29931f83f1defc2257000cc6202d41e7a95327676bb361db603708c10499a66a74a5919f317e2503f819c392ec3dc" +
                "bdd5a65cb6b7ea9a47914f3ec7721425ffde11cc953c28b1b03dc76522470c19ce56ffdb7956ee2560adf3e41be566b4" +
                "c87b0417ca279a4f821f7ee8972fae245a5dd61f979c503ee7d389afb875b3081c8062ed6592fb2c12158e5a541a15b1" +
                "271b10f4c36f3be9c7010bfad8ba59a7434a2dca1c4edc45bd5abdea0fee5ebde43a7bdc359437f2058dbc5a9443"

        /** Version-three vector: [BackupFixtures.minimalSnapshot] under the fixed salt and nonce, as version three wrote it. */
        const val GOLDEN_VECTOR_V3_HEX: String =
            "4446545242414b00000101010001000000000003012010000102030405060708090a0b0c0d0e0f1820212223242526272829" +
                "2a2b2c2d2e2f3031323334353637000001faf4222d42449dacacda3b44d13135cff4fe0e7d7ca990f73737eb9c080263d24d" +
                "f3d8973042c0d9ea1673ba03640ad912cd6ef2d8cd1a4ebee801b224d5ac0fb15720f8bfc12c0d5a15f77f45f492e09befa4" +
                "90c1af5b4c6edef05fa9ae1fb014bfebae6c64575e9b95012302435392c65549ecceb0d4d88233498e65d907d7c17c4d4afe" +
                "0d0f604f88303e4b71456803f7f04d8ece4c55514b66273e95c6c4b6246d49296fa51113edcfeb0cba0cf6560d50b6cc6401" +
                "70fa7cd413c55ef65b3364774675c51838df9b1c710205c63e6458fe5300810da05693ff0652a92e2e1363a6a0064b3ae060" +
                "933984f37d381ccc3087bef46efe6f30aac551f24ee40ae2460a9b287a0f97f4b207efa21e08436604917eb538bf326973ff" +
                "b8f91f29458180bfafd836e9382c01c49cc60802b0b0e47a07ae8eeaa1a00b2106948ce29931f83f1defc2257000cc6202d4" +
                "1e7a95327676bb361db603708c10499a66a74a5919f317e2503f819c392ec3dcbdd5a65cb6b7ea9a47914f3ec7721425ffde" +
                "11cc953c28b1b03dc76522470c19ce56ffdb7956ee2560adf3e41be566b4c87b0417ca279a4f821f7ee8972fae245a5dd61f" +
                "979c503ee7d389afb875b3081c8062ed6592fb2c12158e5a541a15b1271b10f4c36f3be9c7010bfad8ba59a7434a2dca1c4e" +
                "dc45bd5abdea0feebcc4731f3058e52374da74bf66c21b95"

        /** Version-four vector: [BackupFixtures.minimalSnapshot] under the fixed salt and nonce, as version four writes it. */
        const val GOLDEN_VECTOR_HEX: String =
            "4446545242414b00000101010001000000000003012010000102030405060708090a0b0c0d0e0f1820212223242526272829" +
                "2a2b2c2d2e2f303132333435363700000212f4222d42449dacacda3b44d13135cff4fe097d7ca990f73737eb9c080263d24d" +
                "f3d8973042c0d9ea1673ba03640ad912cd6ef2d8cd1a4ebee801b224d5ac0fb15720f8bfc12c0d5a15f77f45f492e09befa4" +
                "90c1af5b4c6edef05fa9ae1fb014bfebae6c64575e9b95012302435392c65549ecceb0d4d88233498e65d907d7c17c4d4afe" +
                "0d0f604f88303e4b71456803f7f04d8ece4c55514b66273e95c6c4b6246d49296fa51113edcfeb0cba0cf6560d50b6cc6401" +
                "70fa7cd413c55ef65b3364774675c51838df9b1c710205c63e6458fe5300810da05693ff0652a92e2e1363a6a0064b3ae060" +
                "933984f37d381ccc3087bef46efe6f30aac551f24ee40ae2460a9b287a0f97f4b207efa21e08436604917eb538bf326973ff" +
                "b8f91f29458180bfafd836e9382c01c49cc60802b0b0e47a07ae8eeaa1a00b2106948ce29931f83f1defc2257000cc6202d4" +
                "1e7a95327676bb361db603708c10499a66a74a5919f317e2503f819c392ec3dcbdd5a65cb6b7ea9a47914f3ec7721425ffde" +
                "11cc953c28b1b03dc76522470c19ce56ffdb7956ee2560adf3e41be566b4c87b0417ca279a4f821f7ee8972fae245a5dd61f" +
                "979c503ee7d389afb875b3081c8062ed6592fb2c12158e5a541a15b1271b10f4c36f3be9c7010bfad8ba59a7434a2dca1c4e" +
                "dc45bd5abdea0fbf871645f9e055c3f278168387a7e8b8fbea3198dc65c4e63e4ad41d34fd61d66dcb09035f9c311457"
    }
}
