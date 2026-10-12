package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BiometricSlotCodecTest {
    private val keyId = ByteArray(16) { (it + 1).toByte() }
    private val vaultId = ByteArray(16) { (it + 33).toByte() }
    private val dekId = ByteArray(16) { (it + 65).toByte() }
    private val sealed = ByteArray(60) { (it * 3).toByte() }

    private fun encoded(): ByteArray =
        (BiometricSlotCodec.encode(keyId, vaultId, dekId, sealed) as Outcome.Success).data

    @Test
    fun roundTripKeepsEveryIdentifierAndTheSealedBytes() {
        val decoded = (BiometricSlotCodec.decode(encoded()) as Outcome.Success).data
        assertContentEquals(keyId, decoded.keyId)
        assertContentEquals(vaultId, decoded.vaultId)
        assertContentEquals(dekId, decoded.dekId)
        assertContentEquals(sealed, decoded.sealed)
        assertContentEquals(BiometricSlotCodec.aad(keyId, vaultId, dekId), decoded.aad)
        assertEquals(BiometricSlotCodec.AAD_BYTES, decoded.aad.size)
    }

    @Test
    fun everyHeaderFieldBindsTheAssociatedData() {
        val base = BiometricSlotCodec.aad(keyId, vaultId, dekId)
        assertFalse(base.contentEquals(BiometricSlotCodec.aad(keyId, ByteArray(16) { 9 }, dekId)))
        assertFalse(base.contentEquals(BiometricSlotCodec.aad(keyId, vaultId, ByteArray(16) { 9 })))
        assertFalse(base.contentEquals(BiometricSlotCodec.aad(ByteArray(16) { 9 }, vaultId, dekId)))
    }

    @Test
    fun malformedSlotsAreCorruptNotGuessed() {
        val good = encoded()
        val cases = mapOf(
            "empty" to ByteArray(0),
            "truncated" to good.copyOf(good.size - 1),
            "trailing byte" to good + byteArrayOf(0),
            "bad magic" to good.copyOf().also { it[0] = 0 },
            "unknown version" to good.copyOf().also { it[4] = 2 },
            "wrong purpose" to good.copyOf().also { it[5] = 1 },
            "wrong cipher" to good.copyOf().also { it[6] = 9 },
            "length mismatch" to good.copyOf().also { it[BiometricSlotCodec.AAD_BYTES + 1] = 1 },
            "zero length" to good.copyOf().also {
                it[BiometricSlotCodec.AAD_BYTES] = 0
                it[BiometricSlotCodec.AAD_BYTES + 1] = 0
            },
        )
        for ((name, bytes) in cases) {
            val result = BiometricSlotCodec.decode(bytes)
            assertEquals(BiometricError.CorruptSlot, (result as? Outcome.Failure)?.error, name)
        }
    }

    @Test
    fun encodeRefusesOversizedOrEmptySealedBytes() {
        assertEquals(
            BiometricError.CorruptSlot,
            (BiometricSlotCodec.encode(keyId, vaultId, dekId, ByteArray(0)) as Outcome.Failure).error,
        )
        assertEquals(
            BiometricError.CorruptSlot,
            (
                BiometricSlotCodec.encode(
                    keyId,
                    vaultId,
                    dekId,
                    ByteArray(BiometricSlotCodec.MAX_SEALED_BYTES + 1),
                ) as Outcome.Failure
                ).error,
        )
    }
}
