package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackoffCodecTest {
    private val vaultId = ByteArray(16) { (0x40 + it).toByte() }

    @Test
    fun penaltyScheduleMatchesTheSpecExactly() {
        val expected = listOf(0L, 0L, 0L, 0L, 0L, 30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L)
        val actual = (0..11).map { BackoffRecord.penaltyMillisFor(it) }
        assertEquals(expected, actual)
    }

    @Test
    fun penaltyCapsAtFifteenMinutesForever() {
        assertEquals(900_000L, BackoffRecord.penaltyMillisFor(10))
        assertEquals(900_000L, BackoffRecord.penaltyMillisFor(1_000))
        assertEquals(900_000L, BackoffRecord.penaltyMillisFor(BackoffRecord.MAX_FAILURES))
    }

    @Test
    fun freshRecordHasNoPenaltyAndNoPendingAttempt() {
        val fresh = BackoffRecord.fresh(vaultId)
        assertEquals(0, fresh.failures)
        assertEquals(0L, fresh.penaltyMillis)
        assertTrue(!fresh.penaltyOpen && !fresh.pending)
    }

    @Test
    fun roundTripsEveryFlagCombination() {
        val cases = listOf(
            BackoffRecord(vaultId, 0, 0L, penaltyOpen = false, pending = false),
            BackoffRecord(vaultId, 5, 30_000L, penaltyOpen = true, pending = false),
            BackoffRecord(vaultId, 5, 30_000L, penaltyOpen = false, pending = false),
            BackoffRecord(vaultId, 6, 60_000L, penaltyOpen = false, pending = true),
        )
        for (record in cases) {
            val encoded = BackoffCodec.encode(record)
            assertEquals(BackoffCodec.RECORD_BYTES, encoded.size)
            val decoded = (BackoffCodec.decode(encoded) as Outcome.Success).data
            assertEquals(record.failures, decoded.failures)
            assertEquals(record.penaltyMillis, decoded.penaltyMillis)
            assertEquals(record.penaltyOpen, decoded.penaltyOpen)
            assertEquals(record.pending, decoded.pending)
            assertTrue(record.vaultId.contentEquals(decoded.vaultId))
        }
    }

    @Test
    fun rejectsTruncatedOrForeignBytesAsCorrupt() {
        val encoded = BackoffCodec.encode(BackoffRecord(vaultId, 1, 0L, penaltyOpen = false, pending = true))
        assertEquals(SecurityError.CorruptState, BackoffCodec.decode(encoded.copyOf(29)).requireFailure())
        val badMagic = encoded.copyOf()
        badMagic[0] = 0
        assertEquals(SecurityError.CorruptState, BackoffCodec.decode(badMagic).requireFailure())
    }

    @Test
    fun rejectsUnknownVersionAsUnsupported() {
        val encoded = BackoffCodec.encode(BackoffRecord.fresh(vaultId))
        encoded[4] = 2
        assertEquals(SecurityError.UnsupportedFormat, BackoffCodec.decode(encoded).requireFailure())
    }

    @Test
    fun rejectsInconsistentFieldsAsCorrupt() {
        // A penalty that does not match the failure count is an edit, not a state the code writes.
        val mismatched = BackoffCodec.encode(BackoffRecord(vaultId, 5, 60_000L, penaltyOpen = true, pending = false))
        assertEquals(SecurityError.CorruptState, BackoffCodec.decode(mismatched).requireFailure())

        // An open penalty with no penalty duration, and a pending attempt with no failures, are both impossible.
        val openWithoutPenalty = BackoffCodec.encode(BackoffRecord(vaultId, 2, 0L, penaltyOpen = true, pending = false))
        assertEquals(SecurityError.CorruptState, BackoffCodec.decode(openWithoutPenalty).requireFailure())
        val pendingWithoutFailures = BackoffCodec.encode(
            BackoffRecord(vaultId, 0, 0L, penaltyOpen = false, pending = true),
        )
        assertEquals(SecurityError.CorruptState, BackoffCodec.decode(pendingWithoutFailures).requireFailure())

        val unknownFlag = BackoffCodec.encode(BackoffRecord.fresh(vaultId))
        unknownFlag[29] = 0x04
        assertEquals(SecurityError.CorruptState, BackoffCodec.decode(unknownFlag).requireFailure())
    }

    @Test
    fun rejectsFailureCountsAboveTheSaturationLimit() {
        val encoded = BackoffCodec.encode(BackoffRecord.fresh(vaultId))
        val over = BackoffRecord.MAX_FAILURES + 1
        encoded[21] = (over ushr 24).toByte()
        encoded[22] = (over ushr 16).toByte()
        encoded[23] = (over ushr 8).toByte()
        encoded[24] = over.toByte()
        assertEquals(SecurityError.CorruptState, BackoffCodec.decode(encoded).requireFailure())
    }
}
