package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome

/**
 * TEST ONLY. Compiled into the iOS simulator target and nowhere else, so a device binary cannot select it.
 *
 * The simulator has no Secure Enclave. This protector seals with an XChaCha20-Poly1305 key kept in the simulator
 * Keychain. It exercises the same envelope and persistence paths, and it gives no hardware guarantee.
 */
internal class SimulatorOnlyTestEnvelopeProtector(private val keyAccount: String) : PlatformEnvelopeProtector {
    private val aead = LibsodiumAead()
    private val random = LibsodiumSecureRandom()
    private val keyItem = KeychainItem(keyAccount)

    override suspend fun prepare(): Outcome<Unit, SecurityError> {
        when (val existing = keyItem.read()) {
            is Outcome.Failure -> return Outcome.Failure(existing.error)
            is Outcome.Success -> if (existing.data != null) return Outcome.Success(Unit)
        }
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        return keyItem.write(random.bytes(KEY_BYTES))
    }

    override suspend fun seal(plaintext: ByteArray): Outcome<ByteArray, SecurityError> {
        val key = when (val read = keyItem.read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data ?: return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        }
        val nonce = random.bytes(NONCE_BYTES)
        val body = aead.seal(key, nonce, AD, plaintext)
        key.fill(0)
        return Outcome.Success(nonce + body)
    }

    override suspend fun unseal(sealed: ByteArray): Outcome<ByteArray, SecurityError> {
        if (sealed.size <= NONCE_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        val key = when (val read = keyItem.read()) {
            is Outcome.Failure -> return Outcome.Failure(read.error)
            is Outcome.Success -> read.data ?: return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        }
        val opened = aead.open(
            key,
            sealed.copyOfRange(0, NONCE_BYTES),
            AD,
            sealed.copyOfRange(NONCE_BYTES, sealed.size),
        )
        key.fill(0)
        return if (opened == null) Outcome.Failure(SecurityError.CorruptState) else Outcome.Success(opened)
    }

    private companion object {
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 24
        val AD = "DFSIM".encodeToByteArray()
    }
}
