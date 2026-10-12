package com.mk.daftar.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenerationPointerTest {

    private val fresh = VaultGeneration.fresh(ByteArray(VaultGeneration.RANDOM_BYTES) { (it + 1).toByte() })

    @Test
    fun legacyNamesAreExactlyThePurpose() {
        assertEquals("pin-envelope.v1", VaultGeneration.LEGACY.name("pin-envelope.v1"))
        assertEquals("legacy", VaultGeneration.LEGACY.id)
        assertTrue(VaultGeneration.LEGACY.isLegacy)
    }

    @Test
    fun freshGenerationsArePrefixedAndNeverLegacy() {
        assertTrue(fresh.id.matches(Regex("g[0-9a-f]{32}")))
        assertEquals(fresh.id + ".pin-envelope.v1", fresh.name("pin-envelope.v1"))
        assertTrue(!fresh.isLegacy)
    }

    @Test
    fun parseAcceptsOnlyTheTwoShapes() {
        assertEquals(VaultGeneration.LEGACY, VaultGeneration.parse("legacy"))
        assertEquals(fresh, VaultGeneration.parse(fresh.id))
        assertNull(VaultGeneration.parse("g" + "A".repeat(32)))
        assertNull(VaultGeneration.parse("legacy2"))
        assertNull(VaultGeneration.parse(""))
    }

    @Test
    fun roundTripsEveryField() {
        val pointer = GenerationPointer(
            active = fresh,
            previous = VaultGeneration.LEGACY,
            staging = null,
            preferences = "theme=DARK;language=EN;timeout=300",
        )
        val decoded = GenerationPointerCodec.decode(GenerationPointerCodec.encode(pointer))
        assertEquals(pointer, decoded)
    }

    @Test
    fun defaultPointerIsTheLegacyVault() {
        val decoded = GenerationPointerCodec.decode(GenerationPointerCodec.encode(GenerationPointer.DEFAULT))
        assertEquals(GenerationPointer.DEFAULT, decoded)
        assertEquals(VaultGeneration.LEGACY, decoded?.active)
    }

    @Test
    fun rejectsDamagedText() {
        val valid = GenerationPointerCodec.encode(GenerationPointer(fresh)).decodeToString()
        val cases = listOf(
            "",
            valid.replace("DFTR-GEN v1", "DFTR-GEN v2"),
            valid.replace("active=", "active= "),
            valid.replace("previous=-", "previous=legacy2"),
            valid + "extra\n",
            valid.replace("preferences=-", "preferences=theme=é"),
            valid.replace("staging=-", "staging=" + fresh.id),
            valid.replace("previous=-", "previous=" + fresh.id),
        )
        for (text in cases) {
            assertNull(GenerationPointerCodec.decode(text.encodeToByteArray()), "accepted: $text")
        }
    }

    @Test
    fun rejectsInvalidUtf8AndOversizeBytes() {
        // An incomplete multi-byte sequence inside the header decodes to a replacement character, so re-encoding
        // differs from the stored bytes, and the pointer is refused.
        val damaged = GenerationPointerCodec.encode(GenerationPointer.DEFAULT).copyOf()
        damaged[5] = 0xC3.toByte()
        assertNull(GenerationPointerCodec.decode(damaged))
        assertNull(GenerationPointerCodec.decode(ByteArray(2048) { 'a'.code.toByte() }))
    }

    @Test
    fun preferencesMayNotCarryLineBreaksOrSpaces() {
        val malformed = GenerationPointer(VaultGeneration.LEGACY, preferences = "theme=DARK\nlanguage=EN")
        val error = runCatching { GenerationPointerCodec.encode(malformed) }.exceptionOrNull()
        assertNotNull(error)
    }
}
