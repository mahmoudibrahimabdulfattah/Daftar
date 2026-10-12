package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.common.map

/** The biometric slot as one device-only, nonsynchronizing Keychain item. Atomic replacement comes from the item update. */
internal class KeychainBiometricSlotStore : BiometricSlotStore {
    private val item = KeychainItem(SLOT_ACCOUNT)

    override suspend fun read(): Outcome<ByteArray?, BiometricError> = when (val read = item.read()) {
        is Outcome.Failure -> Outcome.Failure(read.error.toBiometricError())
        is Outcome.Success -> Outcome.Success(read.data)
    }

    override suspend fun write(slot: ByteArray): Outcome<Unit, BiometricError> =
        item.write(slot).map { }.let { result ->
            when (result) {
                is Outcome.Failure -> Outcome.Failure(BiometricError.StorageFailure)
                is Outcome.Success -> Outcome.Success(Unit)
            }
        }

    override suspend fun delete(): Outcome<Unit, BiometricError> = when (val deleted = item.delete()) {
        is Outcome.Failure -> Outcome.Failure(BiometricError.StorageFailure)
        is Outcome.Success -> Outcome.Success(Unit)
    }

    private companion object {
        const val SLOT_ACCOUNT = "biometric-slot.v1"
    }
}

/** The platform crypto for one biometric operation on iOS: the fresh [context] that the Secure Enclave key uses. */
class IosBiometricCrypto internal constructor(val context: platform.LocalAuthentication.LAContext) : BiometricCrypto
