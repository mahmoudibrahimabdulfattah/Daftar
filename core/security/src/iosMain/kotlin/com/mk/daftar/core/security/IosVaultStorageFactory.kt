package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/**
 * The state of one generation on iOS. Each generation has its own Keychain accounts and its own key tags, so erasing a
 * generation removes exactly its own items. The legacy generation keeps the first release's names.
 */
internal class IosVaultStorageFactory : PlatformVaultStorageFactory {
    override val pointers: GenerationPointerStore = KeychainPointerStore()

    override fun forGeneration(generation: VaultGeneration): PlatformVaultStorage {
        val envelopeAccount = generation.name(KeychainRecordStore.PIN_ENVELOPE_ACCOUNT)
        val backoffAccount = generation.name(KeychainBackoffStore.BACKOFF_ACCOUNT)
        val slotAccount = generation.name(KeychainBiometricSlotStore.SLOT_ACCOUNT)
        val biometricKeys = createPlatformBiometricKeyStore(generation)
        return PlatformVaultStorage(
            protector = createPlatformEnvelopeProtector(generation),
            records = KeychainRecordStore(envelopeAccount),
            backoff = KeychainBackoffStore(backoffAccount),
            biometricKeys = biometricKeys,
            biometricSlots = KeychainBiometricSlotStore(slotAccount),
            eraseSecurity = {
                eraseIosGeneration(
                    generation = generation,
                    accounts = listOf(envelopeAccount, backoffAccount, slotAccount),
                    slotAccount = slotAccount,
                    biometricKeys = biometricKeys,
                )
            },
        )
    }

    private suspend fun eraseIosGeneration(
        generation: VaultGeneration,
        accounts: List<String>,
        slotAccount: String,
        biometricKeys: BiometricKeyStore,
    ): Outcome<Unit, SecurityError> {
        // A committed slot names its biometric key. The key is removed first, so the slot never outlives it.
        val slot = when (val read = KeychainItem(slotAccount).read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data
        }
        if (slot != null) {
            val decoded = BiometricSlotCodec.decode(slot)
            if (decoded is Outcome.Success) biometricKeys.deleteKey(decoded.data.keyId)
        }
        for (account in accounts) {
            when (val deleted = KeychainItem(account).delete()) {
                is Outcome.Failure -> return deleted
                is Outcome.Success -> Unit
            }
        }
        return erasePlatformEnvelopeKey(generation)
    }
}
