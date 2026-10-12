package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/** Whether the stored slot is absent, decodes and matches the expected format, or is unusable. */
enum class BiometricSlotState {
    Absent,
    Valid,
    Invalid,
}

/**
 * Owns the single biometric slot. Enrollment writes a new key and a new slot, commits the slot atomically, and only
 * then deletes the previous key, so a cancelled or failed enrollment keeps the existing enrollment. Removal deletes the
 * slot before its key, so a slot never points at a deleted key.
 */
internal class BiometricSlots(
    private val keys: BiometricKeyStore,
    private val store: BiometricSlotStore,
    private val random: SecureRandom,
) {
    suspend fun capability(): BiometricCapability = keys.capability()

    suspend fun state(): Outcome<BiometricSlotState, BiometricError> = when (val read = store.read()) {
        is Outcome.Failure -> Outcome.Failure(read.error)

        is Outcome.Success -> when (val bytes = read.data) {
            null -> Outcome.Success(BiometricSlotState.Absent)

            else -> when (BiometricSlotCodec.decode(bytes)) {
                is Outcome.Success -> Outcome.Success(BiometricSlotState.Valid)
                is Outcome.Failure -> Outcome.Success(BiometricSlotState.Invalid)
            }
        }
    }

    /**
     * Wraps [dek] under a new key bound to the vault and DEK identifiers, after the platform authorizes the operation.
     * The slot is committed only when it has been written; a failure before that deletes the new key.
     */
    suspend fun enroll(
        vaultId: ByteArray,
        dekId: ByteArray,
        dek: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<Unit, BiometricError> {
        if (dek.size != BiometricSlotCodec.DEK_BYTES) return Outcome.Failure(BiometricError.CorruptSlot)
        val previousKeyId = when (val read = committed()) {
            is Outcome.Failure -> if (read.error ==
                BiometricError.CorruptSlot
            ) {
                null
            } else {
                return Outcome.Failure(read.error)
            }

            is Outcome.Success -> read.data?.keyId
        }
        val keyId = random.bytes(BiometricSlotCodec.ID_BYTES)
        when (val created = keys.createKey(keyId)) {
            is Outcome.Failure -> return created
            is Outcome.Success -> Unit
        }
        val aad = BiometricSlotCodec.aad(keyId, vaultId, dekId)
        val sealed = when (val wrapped = keys.wrap(keyId, aad, dek, prompter)) {
            is Outcome.Failure -> {
                keys.deleteKey(keyId)
                return Outcome.Failure(wrapped.error)
            }

            is Outcome.Success -> wrapped.data
        }
        val encoded = when (val result = BiometricSlotCodec.encode(keyId, vaultId, dekId, sealed)) {
            is Outcome.Failure -> {
                keys.deleteKey(keyId)
                return Outcome.Failure(result.error)
            }

            is Outcome.Success -> result.data
        }
        when (val written = store.write(encoded)) {
            is Outcome.Failure -> {
                keys.deleteKey(keyId)
                return Outcome.Failure(written.error)
            }

            is Outcome.Success -> Unit
        }
        // The new slot is committed. The previous key is no longer referenced, so its removal is best effort.
        previousKeyId?.let { keys.deleteKey(it) }
        return Outcome.Success(Unit)
    }

    /**
     * Unwraps the DEK for the vault and DEK identifiers. An invalidated key or a damaged slot is removed here, so a
     * stale enrollment is never used again. A cancelled or locked-out prompt keeps the slot.
     */
    suspend fun unwrap(
        vaultId: ByteArray,
        dekId: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        val slot = when (val read = committed()) {
            is Outcome.Failure -> {
                if (read.error == BiometricError.CorruptSlot) remove()
                return Outcome.Failure(read.error)
            }

            is Outcome.Success -> read.data ?: return Outcome.Failure(BiometricError.NotEnrolled)
        }
        if (!slot.vaultId.contentEquals(vaultId) || !slot.dekId.contentEquals(dekId)) {
            remove()
            return Outcome.Failure(BiometricError.CorruptSlot)
        }
        val plain = when (val unwrapped = keys.unwrap(slot.keyId, slot.aad, slot.sealed, prompter)) {
            is Outcome.Failure -> {
                when (unwrapped.error) {
                    BiometricError.EnrollmentChanged, BiometricError.CorruptSlot -> remove()
                    else -> Unit
                }
                return unwrapped
            }

            is Outcome.Success -> unwrapped.data
        }
        if (plain.size != BiometricSlotCodec.DEK_BYTES) {
            plain.fill(0)
            remove()
            return Outcome.Failure(BiometricError.CorruptSlot)
        }
        return Outcome.Success(plain)
    }

    /** Deletes the slot, then its key. Idempotent, and it also removes a slot that no longer decodes. */
    suspend fun remove(): Outcome<Unit, BiometricError> {
        val keyId = (committed() as? Outcome.Success)?.data?.keyId
        when (val deleted = store.delete()) {
            is Outcome.Failure -> return deleted
            is Outcome.Success -> Unit
        }
        keyId?.let { keys.deleteKey(it) }
        return Outcome.Success(Unit)
    }

    /** The decoded slot, or null when absent. A slot that does not decode is [BiometricError.CorruptSlot]. */
    private suspend fun committed(): Outcome<BiometricSlot?, BiometricError> {
        val bytes = when (val read = store.read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data ?: return Outcome.Success(null)
        }
        return when (val decoded = BiometricSlotCodec.decode(bytes)) {
            is Outcome.Failure -> Outcome.Failure(decoded.error)
            is Outcome.Success -> Outcome.Success(decoded.data)
        }
    }
}
