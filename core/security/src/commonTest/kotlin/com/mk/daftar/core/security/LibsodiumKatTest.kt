package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Known-answer vectors from libsodium 1.0.x (via PyNaCl), computed for these exact inputs. They bind the bindings
 * to the reference implementation, on the host and on the iOS simulator alike.
 */
class LibsodiumKatTest {

    private val salt = ByteArray(16) { it.toByte() }
    private val baseline = KdfParams(KdfPolicy.MEMORY_KIB, 3, KdfPolicy.LANES)

    @Test
    fun argon2idThreePassesMatchesTheReferenceVector() = runTest {
        assertArgon2("123456", baseline, "9b0a9f24be07d83998f657d86a216621de37af21b9d3133fab03035e94fd58ec")
    }

    @Test
    fun argon2idKeepsLeadingZeroesInThePasswordBytes() = runTest {
        assertArgon2("0012345678901234", baseline, "d635554133190f36e952aefe9591d046cead5dfe0579d3f8732b150d48255997")
    }

    @Test
    fun argon2idFourPassesMatchesTheReferenceVector() = runTest {
        assertArgon2(
            "123456",
            KdfParams(KdfPolicy.MEMORY_KIB, 4, KdfPolicy.LANES),
            "cf808ae950369677e8cad329d8197fa1d4779f86f06f8a146691470cb91b6dd4",
        )
    }

    @Test
    fun argon2idRejectsParametersOutsideTheAllowlistBeforeAllocating() = runTest {
        val result = LibsodiumPinKdf().derive(
            "123456".encodeToByteArray(),
            salt,
            KdfParams(KdfPolicy.MEMORY_KIB * 2, 3, 1),
        )
        assertEquals(SecurityError.UnsupportedFormat, result.requireFailure())
        val tooFewPasses = LibsodiumPinKdf().derive(
            "123456".encodeToByteArray(),
            salt,
            KdfParams(KdfPolicy.MEMORY_KIB, 2, 1),
        )
        assertEquals(SecurityError.UnsupportedFormat, tooFewPasses.requireFailure())
    }

    @Test
    fun xChaChaSealMatchesTheReferenceVector() = runTest {
        Libsodium.ensureReady()
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(24) { it.toByte() }
        val associated = byteArrayOf(0x44, 0x46, 0x53, 0x45, 'A'.code.toByte(), 'D'.code.toByte())
        val message = ByteArray(32) { it.toByte() }
        val sealed = LibsodiumAead().seal(key, nonce, associated, message)
        assertEquals(
            "9ec30d7c94d78ba93b4d2cc5c75fa6e75b563ab6e9c30bfc670325ad21b0eb4646d094e126bb1a5a5bd9e17c32666f11",
            sealed.toHex(),
        )
        assertContentEquals(message, LibsodiumAead().open(key, nonce, associated, sealed))
    }

    @Test
    fun xChaChaSealsAnEmptyMessageToTheTagAlone() = runTest {
        Libsodium.ensureReady()
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(24) { it.toByte() }
        assertEquals(
            "2d351a65cd1abe591f22dc1269c82d90",
            LibsodiumAead().seal(key, nonce, ByteArray(0), ByteArray(0)).toHex(),
        )
    }

    @Test
    fun xChaChaRejectsTamperedCiphertextAssociatedDataAndKeys() = runTest {
        Libsodium.ensureReady()
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(24) { it.toByte() }
        val associated = "header".encodeToByteArray()
        val sealed = LibsodiumAead().seal(key, nonce, associated, ByteArray(32) { 9 })

        val flipped = sealed.copyOf()
        flipped[0] = (flipped[0] + 1).toByte()
        assertNull(LibsodiumAead().open(key, nonce, associated, flipped))
        assertNull(LibsodiumAead().open(key, nonce, "HEADER".encodeToByteArray(), sealed))
        assertNull(LibsodiumAead().open(ByteArray(32) { 1 }, nonce, associated, sealed))
        assertNull(LibsodiumAead().open(key, nonce, associated, sealed.copyOf(sealed.size - 1)))
    }

    @Test
    fun randomBytesAreFreshAndSized() = runTest {
        Libsodium.ensureReady()
        val random = LibsodiumSecureRandom()
        val first = random.bytes(32)
        val second = random.bytes(32)
        assertEquals(32, first.size)
        assertEquals(false, first.contentEquals(second))
    }

    private suspend fun assertArgon2(pin: String, params: KdfParams, expectedHex: String) {
        val derived = LibsodiumPinKdf().derive(pin.encodeToByteArray(), salt, params)
        val key = (derived as Outcome.Success).data
        key.use { assertEquals(expectedHex, it.toHex()) }
        key.wipe()
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
