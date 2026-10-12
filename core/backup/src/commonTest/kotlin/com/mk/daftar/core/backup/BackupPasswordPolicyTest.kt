package com.mk.daftar.core.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupPasswordPolicyTest {

    @Test
    fun elevenCharactersAreTooShortAndTwelveAreAccepted() {
        assertEquals(BackupPasswordProblem.TOO_SHORT, BackupPasswordPolicy.problem("a".repeat(11)))
        assertNull(BackupPasswordPolicy.problem("a".repeat(12)))
    }

    @Test
    fun codePointsCountEmojiOnce() {
        // Twelve emoji are twelve code points, so they satisfy the minimum even though they are 48 UTF-8 bytes.
        assertNull(BackupPasswordPolicy.problem("🙂".repeat(12)))
        assertEquals(BackupPasswordProblem.TOO_SHORT, BackupPasswordPolicy.problem("🙂".repeat(11)))
    }

    @Test
    fun twoHundredFiftySixCodePointsAreTheLimit() {
        assertNull(BackupPasswordPolicy.problem("x".repeat(256)))
        assertEquals(BackupPasswordProblem.TOO_LONG, BackupPasswordPolicy.problem("x".repeat(257)))
    }

    @Test
    fun utf8LengthIsCountedInBytes() {
        // 256 Arabic letters are 512 bytes, inside the 1,024-byte bound; four-byte emoji reach exactly 1,024.
        assertNull(BackupPasswordPolicy.problem("ب".repeat(256)))
        assertNull(BackupPasswordPolicy.problem("🙂".repeat(256)))
        assertEquals(1024, BackupPasswordPolicy.utf8Length("🙂".repeat(256)))
    }

    @Test
    fun textIsNeverTrimmedOrNormalized() {
        // Leading spaces count, and Arabic-Indic digits stay as typed rather than becoming ASCII digits.
        val typed = "  ١٢٣٤٥٦٧٨٩٠ abc"
        assertNull(BackupPasswordPolicy.problem(typed))
        assertContentEquals(typed.encodeToByteArray(), BackupPasswordPolicy.utf8(typed))
    }

    @Test
    fun unpairedSurrogateIsRejected() {
        assertEquals(BackupPasswordProblem.INVALID_CHARACTERS, BackupPasswordPolicy.problem("abcdefghijk\uD83D"))
        assertEquals(BackupPasswordProblem.INVALID_CHARACTERS, BackupPasswordPolicy.problem("\uDE00abcdefghijkl"))
    }

    @Test
    fun caseIsNotFolded() {
        assertEquals(
            "Password-Phrase1".encodeToByteArray().toList(),
            BackupPasswordPolicy.utf8("Password-Phrase1").toList(),
        )
    }
}
