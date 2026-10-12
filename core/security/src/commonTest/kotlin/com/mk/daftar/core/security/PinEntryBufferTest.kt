package com.mk.daftar.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinEntryBufferTest {

    private fun PinEntryBuffer.type(text: String): PinEntryBuffer = apply {
        text.map { it.code }.forEach { assertTrue(append(it), "digit $it accepted") }
    }

    @Test
    fun acceptsAsciiArabicIndicAndPersianDigitsAsAsciiSubmission() {
        val buffer = PinEntryBuffer().apply {
            assertTrue(append('٠'.code))
            assertTrue(append('۱'.code))
            assertTrue(append('2'.code))
            assertTrue(append('٣'.code))
            assertTrue(append('۴'.code))
            assertTrue(append('5'.code))
        }
        val submission = assertNotNull(buffer.snapshot())
        assertEquals("012345", submission.use { it.decodeToString() })
        assertEquals(6, submission.size)
        submission.wipe()
    }

    @Test
    fun keepsLeadingZerosAndRejectsOtherCharacters() {
        val buffer = PinEntryBuffer().type("000000")
        assertEquals(6, buffer.size)
        assertFalse(buffer.append('a'.code))
        assertFalse(buffer.append(' '.code))
        assertFalse(buffer.append('٫'.code))
        assertFalse(buffer.append(0x0661 + 100))
        assertEquals(6, buffer.size)
        buffer.wipe()
    }

    @Test
    fun rejectsSeventeenthDigitAndAllowsSixteen() {
        val buffer = PinEntryBuffer().type("1234567890123456")
        assertEquals(16, buffer.size)
        assertFalse(buffer.append('7'.code))
        assertEquals(16, buffer.size)
        buffer.wipe()
    }

    @Test
    fun submissionRequiresSixDigitsAndIsNeverProducedShort() {
        val buffer = PinEntryBuffer().type("12345")
        assertFalse(buffer.isSubmittable)
        assertNull(buffer.snapshot())
        buffer.append('6'.code)
        assertTrue(buffer.isSubmittable)
        assertNotNull(buffer.snapshot()).wipe()
        buffer.wipe()
    }

    @Test
    fun eraseRemovesTheLastDigitAndNothingBeyondTheStart() {
        val buffer = PinEntryBuffer().type("1234")
        assertTrue(buffer.erase())
        assertEquals(3, buffer.size)
        buffer.erase()
        buffer.erase()
        buffer.erase()
        assertFalse(buffer.erase())
        assertEquals(0, buffer.size)
        buffer.wipe()
    }

    @Test
    fun matchesComparesTheWholeSubmissionAndLengthOnly() {
        val expected = assertNotNull(PinEntryBuffer().type("123456").snapshot())
        assertTrue(PinEntryBuffer().type("123456").matches(expected))
        assertFalse(PinEntryBuffer().type("123457").matches(expected))
        assertFalse(PinEntryBuffer().type("12345").matches(expected))
        assertFalse(PinEntryBuffer().type("1234567").matches(expected))
        expected.wipe()
    }

    @Test
    fun clearAndWipeLeaveNoDigitsBehind() {
        val buffer = PinEntryBuffer().type("987654")
        buffer.clear()
        assertEquals(0, buffer.size)
        assertFalse(buffer.isSubmittable)
        buffer.type("111111")
        buffer.wipe()
        assertEquals(0, buffer.size)
        assertFailsWith<IllegalStateException> { buffer.append('1'.code) }
        assertFailsWith<IllegalStateException> { buffer.snapshot() }
    }

    @Test
    fun digitValueIsDefinedOnlyForTheThreeNumeralScripts() {
        assertEquals(9, PinEntryBuffer.digitValue('9'.code))
        assertEquals(0, PinEntryBuffer.digitValue(0x0660))
        assertEquals(9, PinEntryBuffer.digitValue(0x06F9))
        assertEquals(-1, PinEntryBuffer.digitValue(0x0669 + 1))
        assertEquals(-1, PinEntryBuffer.digitValue('x'.code))
    }
}
