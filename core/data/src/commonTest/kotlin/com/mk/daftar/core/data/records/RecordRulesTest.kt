package com.mk.daftar.core.data.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecordRulesTest {
    private val earlier = Instant.fromEpochSeconds(1_700_000_000, 0)
    private val later = Instant.fromEpochSeconds(1_700_000_100, 0)

    @Test
    fun newRecordsStartAtRevisionOne() {
        assertEquals(1L, RecordRules.FIRST_REVISION)
    }

    @Test
    fun aRevisionMatchesOnlyItself() {
        assertTrue(RecordRules.matches(stored = 3, expected = 3))
        assertFalse(RecordRules.matches(stored = 4, expected = 3))
    }

    @Test
    fun aRevisionBelowOneNeverMatches() {
        assertFalse(RecordRules.matches(stored = 0, expected = 0))
    }

    @Test
    fun editsIncrementTheRevisionByExactlyOne() {
        assertEquals(2L, RecordRules.nextRevision(1L))
        assertEquals(8L, RecordRules.nextRevision(7L))
        assertFailsWith<IllegalArgumentException> { RecordRules.nextRevision(0L) }
    }

    @Test
    fun anEditTakesTheClockWhenItIsLater() {
        assertEquals(later, RecordRules.editedAt(now = later, previousUpdate = earlier))
    }

    @Test
    fun anEditNeverMovesTheUpdateTimeBackwards() {
        // A clock that reads behind the stored update time must not rewind the record.
        assertEquals(later, RecordRules.editedAt(now = earlier, previousUpdate = later))
    }
}
