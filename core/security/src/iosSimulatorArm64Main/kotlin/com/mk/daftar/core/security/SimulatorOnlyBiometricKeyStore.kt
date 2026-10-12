package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import platform.LocalAuthentication.LAContext

/**
 * TEST ONLY. Compiled into the iOS simulator target and nowhere else, so a device binary cannot select it.
 *
 * The simulator has no Secure Enclave and no enrolled biometrics. This key store keeps a random key in the simulator
 * Keychain, seals with XChaCha20-Poly1305, and still goes through the prompter, so the UI and slot paths run. It gives
 * no hardware or biometric guarantee.
 */
internal class SimulatorOnlyBiometricKeyStore(private val accountPrefix: String) : BiometricKeyStore {
    private val aead = LibsodiumAead()
    private val random = LibsodiumSecureRandom()

    override suspend fun capability(): BiometricCapability = BiometricCapability.Available

    override suspend fun createKey(keyId: ByteArray): Outcome<Unit, BiometricError> {
        val item = KeychainItem(accountFor(keyId))
        when (val existing = item.read()) {
            is Outcome.Failure -> return Outcome.Failure(BiometricError.Unavailable)
            is Outcome.Success -> if (existing.data != null) return Outcome.Failure(BiometricError.Unavailable)
        }
        return when (item.write(random.bytes(KEY_BYTES))) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.Unavailable)
            is Outcome.Success -> Outcome.Success(Unit)
        }
    }

    override suspend fun deleteKey(keyId: ByteArray): Outcome<Unit, BiometricError> =
        when (KeychainItem(accountFor(keyId)).delete()) {
            is Outcome.Failure -> Outcome.Failure(BiometricError.Unavailable)
            is Outcome.Success -> Outcome.Success(Unit)
        }

    override suspend fun wrap(
        keyId: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        val key = loadKey(keyId) ?: return Outcome.Failure(BiometricError.EnrollmentChanged)
        prompter.authorize(IosBiometricCrypto(LAContext())).failureOrNull()?.let { return Outcome.Failure(it) }
        val nonce = random.bytes(NONCE_BYTES)
        val sealed = nonce + aead.seal(key, nonce, aad, plaintext)
        key.fill(0)
        return Outcome.Success(sealed)
    }

    override suspend fun unwrap(
        keyId: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        if (sealed.size <= NONCE_BYTES) return Outcome.Failure(BiometricError.CorruptSlot)
        val key = loadKey(keyId) ?: return Outcome.Failure(BiometricError.EnrollmentChanged)
        prompter.authorize(IosBiometricCrypto(LAContext())).failureOrNull()?.let {
            key.fill(0)
            return Outcome.Failure(it)
        }
        val opened = aead.open(
            key,
            sealed.copyOfRange(0, NONCE_BYTES),
            aad,
            sealed.copyOfRange(NONCE_BYTES, sealed.size),
        )
        key.fill(0)
        return if (opened == null) Outcome.Failure(BiometricError.CorruptSlot) else Outcome.Success(opened)
    }

    private suspend fun loadKey(keyId: ByteArray): ByteArray? =
        when (val read = KeychainItem(accountFor(keyId)).read()) {
            is Outcome.Failure -> null
            is Outcome.Success -> read.data
        }

    private fun accountFor(keyId: ByteArray): String =
        accountPrefix + keyId.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private companion object {
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 24
    }
}
