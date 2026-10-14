package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/** Whether this vault has a recovery key. */
enum class RecoveryState {
    Absent,
    Active,
}

/**
 * The recovery slot: the vault's DEK sealed to the recovery key's public key, beside that public key, all sealed again
 * by the same hardware key as the PIN envelope. Opening it therefore needs both this device and the recovery key; the
 * app alone can write it but never read the DEK from it.
 *
 * Plain record, 181 bytes:
 * magic "DFRK" | version 1 | vaultId 16 | dekId 16 | publicKey 32 | sealed(vaultId 16 | dekId 16 | dek 32) 112
 */
internal object RecoverySlotCodec {
    private val MAGIC = byteArrayOf(0x44, 0x46, 0x52, 0x4B)
    private const val VERSION = 1
    const val ID_BYTES = 16
    const val KEY_BYTES = 32
    private const val SEAL_OVERHEAD = 48
    const val INNER_BYTES = ID_BYTES * 2 + KEY_BYTES
    private const val SEALED_BYTES = INNER_BYTES + SEAL_OVERHEAD
    const val PLAIN_BYTES = 4 + 1 + ID_BYTES * 2 + KEY_BYTES + SEALED_BYTES

    class Slot(val vaultId: ByteArray, val dekId: ByteArray, val publicKey: ByteArray, val sealed: ByteArray)

    fun encode(slot: Slot): ByteArray {
        require(slot.vaultId.size == ID_BYTES && slot.dekId.size == ID_BYTES)
        require(slot.publicKey.size == KEY_BYTES && slot.sealed.size == SEALED_BYTES)
        return MAGIC + byteArrayOf(VERSION.toByte()) + slot.vaultId + slot.dekId + slot.publicKey + slot.sealed
    }

    fun decode(bytes: ByteArray): Outcome<Slot, SecurityError> {
        if (bytes.size != PLAIN_BYTES || !bytes.copyOfRange(0, 4).contentEquals(MAGIC)) {
            return Outcome.Failure(SecurityError.CorruptState)
        }
        if (bytes[4].toInt() != VERSION) return Outcome.Failure(SecurityError.UnsupportedFormat)
        var offset = 5
        fun take(size: Int): ByteArray = bytes.copyOfRange(offset, offset + size).also { offset += size }
        return Outcome.Success(Slot(take(ID_BYTES), take(ID_BYTES), take(KEY_BYTES), take(SEALED_BYTES)))
    }
}

/**
 * Reads and writes the recovery slot of one generation. A new slot replaces the previous one atomically, so a
 * superseded recovery key stops opening this vault as soon as the new one is committed.
 */
internal class RecoverySlots(
    private val protector: PlatformEnvelopeProtector,
    private val store: SecurityRecordStore,
) {
    suspend fun state(): Outcome<RecoveryState, SecurityError> = when (val read = store.read()) {
        is Outcome.Failure -> Outcome.Failure(read.error)
        is Outcome.Success -> Outcome.Success(if (read.data == null) RecoveryState.Absent else RecoveryState.Active)
    }

    /** Seals [dek] to the key pair of [secret] and commits the slot. Proved by opening it again before the commit. */
    suspend fun write(
        vaultId: ByteArray,
        dekId: ByteArray,
        dek: ByteArray,
        secret: ByteArray,
    ): Outcome<Unit, SecurityError> {
        val pair = RecoveryKeyPair.derive(secret)
        try {
            val inner = vaultId + dekId + dek
            val sealed = sealToRecoveryKey(inner, pair.publicKey)
            val reopened = pair.open(sealed)
            val proved = reopened != null && reopened.contentEquals(inner)
            inner.fill(0)
            reopened?.fill(0)
            if (!proved) return Outcome.Failure(SecurityError.CorruptState)
            val plain = RecoverySlotCodec.encode(RecoverySlotCodec.Slot(vaultId, dekId, pair.publicKey, sealed))
            val record = when (val result = protector.seal(plain)) {
                is Outcome.Failure -> return Outcome.Failure(result.error)
                is Outcome.Success -> result.data
            }
            return store.replace(record)
        } finally {
            pair.wipe()
        }
    }

    /**
     * Opens the slot with [secret] and returns the DEK, after checking it belongs to [vaultId] and [dekId]. A key that
     * is not this vault's recovery key fails with [SecurityError.RecoveryKeyRejected].
     */
    suspend fun open(secret: ByteArray, vaultId: ByteArray, dekId: ByteArray): Outcome<SecretBytes, SecurityError> {
        val slot = when (val read = read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data ?: return Outcome.Failure(SecurityError.RecoveryNotSetUp)
        }
        if (!slot.vaultId.contentEquals(vaultId) || !slot.dekId.contentEquals(dekId)) {
            return Outcome.Failure(SecurityError.CorruptState)
        }
        val pair = RecoveryKeyPair.derive(secret)
        try {
            if (!pair.publicKey.contentEquals(slot.publicKey)) return Outcome.Failure(SecurityError.RecoveryKeyRejected)
            val inner = pair.open(slot.sealed) ?: return Outcome.Failure(SecurityError.CorruptState)
            val idsMatch = inner.copyOfRange(0, RecoverySlotCodec.ID_BYTES).contentEquals(vaultId) &&
                inner.copyOfRange(RecoverySlotCodec.ID_BYTES, RecoverySlotCodec.ID_BYTES * 2).contentEquals(dekId)
            val dek = inner.copyOfRange(RecoverySlotCodec.ID_BYTES * 2, RecoverySlotCodec.INNER_BYTES)
            inner.fill(0)
            if (!idsMatch) {
                dek.fill(0)
                return Outcome.Failure(SecurityError.CorruptState)
            }
            return Outcome.Success(SecretBytes.owned(dek))
        } finally {
            pair.wipe()
        }
    }

    /** The recovery public key, so other keys (such as a backup's) can be sealed to it. Null when absent. */
    suspend fun publicKey(): Outcome<ByteArray?, SecurityError> = when (val read = read()) {
        is Outcome.Failure -> Outcome.Failure(read.error)
        is Outcome.Success -> Outcome.Success(read.data?.publicKey)
    }

    private suspend fun read(): Outcome<RecoverySlotCodec.Slot?, SecurityError> {
        val record = when (val read = store.read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data ?: return Outcome.Success(null)
        }
        if (record.size > EnvelopeCodec.MAX_SEALED_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        val plain = when (val unsealed = protector.unseal(record)) {
            is Outcome.Failure -> return Outcome.Failure(unsealed.error)
            is Outcome.Success -> unsealed.data
        }
        val decoded = RecoverySlotCodec.decode(plain)
        plain.fill(0)
        return when (decoded) {
            is Outcome.Failure -> Outcome.Failure(decoded.error)
            is Outcome.Success -> Outcome.Success(decoded.data)
        }
    }
}
