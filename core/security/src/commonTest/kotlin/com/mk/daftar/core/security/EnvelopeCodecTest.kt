package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvelopeCodecTest {

    private val vaultId = ByteArray(16) { (it + 1).toByte() }
    private val dekId = ByteArray(16) { (0xA0 + it).toByte() }
    private val salt = ByteArray(16) { (0x30 + it).toByte() }
    private val nonce = ByteArray(24) { (0x50 + it).toByte() }
    private val inner = ByteArray(48) { (0x70 + it).toByte() }
    private val header = EnvelopeHeader(
        generation = 7L,
        vaultId = vaultId,
        dekId = dekId,
        kdf = KdfParams(KdfPolicy.MEMORY_KIB, 4, KdfPolicy.LANES),
        salt = salt,
    )

    private fun plain(): ByteArray = EnvelopeCodec.encode(EnvelopeCodec.encodeHeader(header), nonce, inner)

    @Test
    fun headerHasTheCanonicalLayout() {
        val bytes = EnvelopeCodec.encodeHeader(header)
        assertEquals(EnvelopeCodec.HEADER_BYTES, bytes.size)
        assertEquals("DFSE", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals(1, bytes[4].toInt())
        assertEquals(1, bytes[5].toInt())
        assertEquals(1, bytes[6].toInt())
        assertEquals(1, bytes[7].toInt())
        assertEquals(7, bytes[11].toInt())
        assertEquals(0x0001_0000, readU32(bytes, 44))
        assertEquals(4, bytes[48].toInt())
        assertEquals(1, bytes[49].toInt())
        assertEquals(16, bytes[50].toInt())
        assertEquals(32, bytes[67].toInt())
        assertEquals(24, bytes[68].toInt())
    }

    @Test
    fun plainEnvelopeRoundTrips() {
        val parsed = (EnvelopeCodec.decode(plain()) as Outcome.Success).data
        assertEquals(7L, parsed.header.generation)
        assertTrue(parsed.header.vaultId.contentEquals(vaultId))
        assertTrue(parsed.header.dekId.contentEquals(dekId))
        assertEquals(KdfPolicy.MEMORY_KIB, parsed.header.kdf.memoryKiB)
        assertEquals(4, parsed.header.kdf.passes)
        assertTrue(parsed.header.salt.contentEquals(salt))
        assertTrue(parsed.nonce.contentEquals(nonce))
        assertTrue(parsed.inner.contentEquals(inner))
        assertTrue(parsed.headerBytes.contentEquals(EnvelopeCodec.encodeHeader(header)))
    }

    @Test
    fun envelopeLengthIsFixed() {
        assertEquals(EnvelopeCodec.PLAIN_BYTES, plain().size)
        assertEquals(141, EnvelopeCodec.PLAIN_BYTES)
    }

    @Test
    fun rejectsEveryWrongLengthAsCorrupt() {
        assertEquals(SecurityError.CorruptState, EnvelopeCodec.decode(plain().copyOf(140)).requireFailure())
        assertEquals(SecurityError.CorruptState, EnvelopeCodec.decode(plain() + byteArrayOf(0)).requireFailure())
        assertEquals(SecurityError.CorruptState, EnvelopeCodec.decode(ByteArray(0)).requireFailure())
    }

    @Test
    fun rejectsBadMagicAsCorrupt() {
        val bytes = plain()
        bytes[0] = 0
        assertEquals(SecurityError.CorruptState, EnvelopeCodec.decode(bytes).requireFailure())
    }

    @Test
    fun rejectsUnknownVersionsAndIdentifiersAsUnsupported() {
        assertUnsupported { it[4] = 2 }
        assertUnsupported { it[5] = 2 }
        assertUnsupported { it[6] = 2 }
        assertUnsupported { it[7] = 2 }
    }

    @Test
    fun rejectsFieldsOutsideTheFixedLengthsAsCorrupt() {
        assertCorrupt {
            it[8] = 0
            it[9] = 0
            it[10] = 0
            it[11] = 0
        }
        assertCorrupt { it[50] = 15 }
        assertCorrupt { it[67] = 31 }
        assertCorrupt { it[68] = 23 }
    }

    @Test
    fun rejectsKdfParametersOutsideTheAllowlistBeforeAnyAllocation() {
        assertUnsupported { writeU32(it, 44, 32 * 1024) }
        assertUnsupported { it[48] = 2 }
        assertUnsupported { it[48] = 7 }
        assertUnsupported { it[49] = 2 }
    }

    @Test
    fun acceptsEveryAllowedPassCount() {
        for (passes in KdfPolicy.MIN_PASSES..KdfPolicy.MAX_PASSES) {
            val bytes = plain()
            bytes[48] = passes.toByte()
            assertTrue(EnvelopeCodec.decode(bytes) is Outcome.Success, "passes $passes")
        }
    }

    @Test
    fun encodingRejectsMalformedFieldsAsProgrammingErrors() {
        var rejected = false
        try {
            EnvelopeCodec.encode(ByteArray(10), nonce, inner)
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun boundsCoverTheRealEnvelopeWithRoom() {
        assertTrue(EnvelopeCodec.MAX_SEALED_BYTES > EnvelopeCodec.PLAIN_BYTES + 16 + 24)
    }

    private fun assertUnsupported(edit: (ByteArray) -> Unit) {
        val bytes = plain()
        edit(bytes)
        assertEquals(SecurityError.UnsupportedFormat, EnvelopeCodec.decode(bytes).requireFailure())
    }

    private fun assertCorrupt(edit: (ByteArray) -> Unit) {
        val bytes = plain()
        edit(bytes)
        assertEquals(SecurityError.CorruptState, EnvelopeCodec.decode(bytes).requireFailure())
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)
}
