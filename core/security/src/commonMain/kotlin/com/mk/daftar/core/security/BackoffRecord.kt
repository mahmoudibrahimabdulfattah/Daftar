package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/**
 * Persisted attempt state. [failures] counts consecutive wrong attempts and saturates. [penaltyMillis] always
 * equals [penaltyMillisFor] of [failures], and [penaltyOpen] tells whether that penalty has not yet completed.
 * [pending] marks an attempt that was reserved before its KDF ran and has not been settled, so it counts.
 */
internal data class BackoffRecord(
    val vaultId: ByteArray,
    val failures: Int,
    val penaltyMillis: Long,
    val penaltyOpen: Boolean,
    val pending: Boolean,
) {
    /** The state after a successful unlock. */
    fun reset(): BackoffRecord = copy(failures = 0, penaltyMillis = 0, penaltyOpen = false, pending = false)

    companion object {
        const val MAX_FAILURES = 1_000_000

        /** The penalty applied after [failures] consecutive wrong attempts. */
        fun penaltyMillisFor(failures: Int): Long = when {
            failures < 5 -> 0L
            failures == 5 -> 30_000L
            failures == 6 -> 60_000L
            failures == 7 -> 120_000L
            failures == 8 -> 240_000L
            failures == 9 -> 480_000L
            else -> 900_000L
        }

        fun fresh(vaultId: ByteArray): BackoffRecord = BackoffRecord(
            vaultId = vaultId,
            failures = 0,
            penaltyMillis = 0L,
            penaltyOpen = false,
            pending = false,
        )
    }
}

/**
 * Fixed binary encoding of [BackoffRecord]: magic "DFBS" | version 1 | vaultId 16 | failures u32 | penaltyMillis
 * u32 | flags u8 (bit 0 penaltyOpen, bit 1 pending). Consistency between the fields is checked on decode, which
 * detects corruption on stores that have no integrity of their own.
 */
internal object BackoffCodec {
    const val RECORD_BYTES = 30
    private const val VERSION = 1
    private const val FLAG_PENALTY_OPEN = 0x01
    private const val FLAG_PENDING = 0x02
    private val MAGIC = byteArrayOf(0x44, 0x46, 0x42, 0x53)

    fun encode(record: BackoffRecord): ByteArray {
        require(record.vaultId.size == 16)
        val out = ByteArray(RECORD_BYTES)
        MAGIC.copyInto(out, 0)
        out[4] = VERSION.toByte()
        record.vaultId.copyInto(out, 5)
        putU32(out, 21, record.failures.toLong())
        putU32(out, 25, record.penaltyMillis)
        var flags = 0
        if (record.penaltyOpen) flags = flags or FLAG_PENALTY_OPEN
        if (record.pending) flags = flags or FLAG_PENDING
        out[29] = flags.toByte()
        return out
    }

    fun decode(bytes: ByteArray): Outcome<BackoffRecord, SecurityError> {
        if (bytes.size != RECORD_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC)) return Outcome.Failure(SecurityError.CorruptState)
        if (bytes[4].toInt() != VERSION) return Outcome.Failure(SecurityError.UnsupportedFormat)
        val failures = readU32(bytes, 21)
        val penaltyMillis = readU32(bytes, 25)
        val flags = bytes[29].toInt() and 0xFF
        if (flags and (FLAG_PENALTY_OPEN or FLAG_PENDING).inv() != 0) return Outcome.Failure(SecurityError.CorruptState)
        if (failures > BackoffRecord.MAX_FAILURES) return Outcome.Failure(SecurityError.CorruptState)
        if (penaltyMillis != BackoffRecord.penaltyMillisFor(failures.toInt())) {
            return Outcome.Failure(SecurityError.CorruptState)
        }
        val penaltyOpen = flags and FLAG_PENALTY_OPEN != 0
        val pending = flags and FLAG_PENDING != 0
        if (penaltyOpen && penaltyMillis == 0L) return Outcome.Failure(SecurityError.CorruptState)
        if (pending && failures == 0L) return Outcome.Failure(SecurityError.CorruptState)
        return Outcome.Success(
            BackoffRecord(
                vaultId = bytes.copyOfRange(5, 21),
                failures = failures.toInt(),
                penaltyMillis = penaltyMillis,
                penaltyOpen = penaltyOpen,
                pending = pending,
            ),
        )
    }

    private fun putU32(out: ByteArray, offset: Int, value: Long) {
        out[offset] = (value ushr 24).toByte()
        out[offset + 1] = (value ushr 16).toByte()
        out[offset + 2] = (value ushr 8).toByte()
        out[offset + 3] = value.toByte()
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long = ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)
}
