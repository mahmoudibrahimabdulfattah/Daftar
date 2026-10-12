package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinNormalizerTest {

    @Test
    fun keepsLeadingZerosInAsciiDigits() {
        val normalized = PinNormalizer.normalize("000123").requireSuccess()
        assertEquals("000123", normalized.use { it.decodeToString() })
    }

    @Test
    fun mapsArabicIndicDigitsToAsciiInAnOwnedBuffer() {
        val normalized = PinNormalizer.normalize("٠٠١٢٣٤").requireSuccess()
        assertEquals("001234", normalized.use { it.decodeToString() })
        assertEquals(6, normalized.size)
    }

    @Test
    fun mapsPersianDigitsToAscii() {
        val normalized = PinNormalizer.normalize("۱۲۳۴۵۶").requireSuccess()
        assertEquals("123456", normalized.use { it.decodeToString() })
    }

    @Test
    fun acceptsMixedNumeralScriptsWithinOneValue() {
        val normalized = PinNormalizer.normalize("1٢3۴56").requireSuccess()
        assertEquals("123456", normalized.use { it.decodeToString() })
    }

    @Test
    fun acceptsSixAndSixteenDigitsOnly() {
        PinNormalizer.normalize("123456").requireSuccess()
        PinNormalizer.normalize("1234567890123456").requireSuccess()
        assertEquals(SecurityError.InvalidPinFormat, PinNormalizer.normalize("12345").requireFailure())
        assertEquals(SecurityError.InvalidPinFormat, PinNormalizer.normalize("12345678901234567").requireFailure())
        assertEquals(SecurityError.InvalidPinFormat, PinNormalizer.normalize("").requireFailure())
    }

    @Test
    fun rejectsNonDigitCharactersWithoutEchoingThem() {
        assertEquals(SecurityError.InvalidPinFormat, PinNormalizer.normalize("12a456").requireFailure())
        assertEquals(SecurityError.InvalidPinFormat, PinNormalizer.normalize("12 456").requireFailure())
        assertEquals(SecurityError.InvalidPinFormat, PinNormalizer.normalize("12٫456").requireFailure())
        assertEquals(SecurityError.InvalidPinFormat, PinNormalizer.normalize("+123456").requireFailure())
    }

    @Test
    fun validatesDigitBytesOfAnOwnedBuffer() {
        assertTrue(PinNormalizer.isValidDigits("0123456789".encodeToByteArray()))
        assertFalse(PinNormalizer.isValidDigits("12345".encodeToByteArray()))
        assertFalse(PinNormalizer.isValidDigits("12345x".encodeToByteArray()))
    }

    @Test
    fun secretBytesRedactsItsContentsInToString() {
        val secret = PinNormalizer.normalize("123456").requireSuccess()
        assertEquals("SecretBytes(redacted)", secret.toString())
        secret.wipe()
        assertTrue(secret.isWiped)
    }

    @Test
    fun wipedSecretCannotBeUsed() {
        val secret = SecretBytes.owned(byteArrayOf(1, 2, 3))
        secret.wipe()
        var failed = false
        try {
            secret.use { }
        } catch (expected: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun outcomeHelpersStayUsable() {
        val failure: Outcome<Int, SecurityError> = Outcome.Failure(SecurityError.InvalidPinFormat)
        assertEquals(SecurityError.InvalidPinFormat, failure.requireFailure())
    }
}
