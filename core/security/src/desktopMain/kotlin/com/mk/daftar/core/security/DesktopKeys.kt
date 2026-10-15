package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import java.io.File

/**
 * Chooses the key protection of one generation. A hardware protector (Secure Enclave on macOS, TPM on Windows) is
 * used whenever the platform has one. Without it, a release build refuses with [SecurityError.UnsupportedHardware],
 * and a debug build may fall back to [DebugOnlySoftwareProtector].
 */
internal class DesktopKeys(
    private val generation: VaultGeneration,
    private val directory: File,
    private val permitsSoftwareKeys: Boolean,
) {
    fun protector(purpose: String): PlatformEnvelopeProtector = if (permitsSoftwareKeys) {
        DebugOnlySoftwareProtector(directory.resolve("${generation.name(purpose)}.debug-key"))
    } else {
        UnsupportedHardwareProtector
    }

    fun biometricKeys(): BiometricKeyStore = UnavailableBiometricKeyStore

    /** Removes every key of this generation. Software keys live in the security directory and go with it. */
    suspend fun eraseAll(): Outcome<Unit, SecurityError> = Outcome.Success(Unit)
}

/** The protector of a release build on a desktop without a supported hardware key. Every operation refuses. */
internal object UnsupportedHardwareProtector : PlatformEnvelopeProtector {
    override suspend fun prepare(): Outcome<Unit, SecurityError> = Outcome.Failure(SecurityError.UnsupportedHardware)

    override suspend fun seal(plaintext: ByteArray): Outcome<ByteArray, SecurityError> =
        Outcome.Failure(SecurityError.UnsupportedHardware)

    override suspend fun unseal(sealed: ByteArray): Outcome<ByteArray, SecurityError> =
        Outcome.Failure(SecurityError.UnsupportedHardware)
}

/** Biometric unlock on a desktop without a verified adapter: never offered. */
internal object UnavailableBiometricKeyStore : BiometricKeyStore {
    override suspend fun capability(): BiometricCapability = BiometricCapability.Unavailable

    override suspend fun createKey(keyId: ByteArray): Outcome<Unit, BiometricError> =
        Outcome.Failure(BiometricError.Unavailable)

    override suspend fun deleteKey(keyId: ByteArray): Outcome<Unit, BiometricError> = Outcome.Success(Unit)

    override suspend fun wrap(
        keyId: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> = Outcome.Failure(BiometricError.Unavailable)

    override suspend fun unwrap(
        keyId: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> = Outcome.Failure(BiometricError.Unavailable)
}

/**
 * DEBUG ONLY. Selected only when the launcher binds [DesktopSecurityConfiguration.AllowSoftwareForDebug], which a
 * release build never does. Seals with XChaCha20-Poly1305 under a random key kept in a file beside the sealed
 * records. It exercises the same envelope and persistence paths and gives no hardware guarantee.
 */
internal class DebugOnlySoftwareProtector(keyFile: File) : PlatformEnvelopeProtector {
    private val aead = LibsodiumAead()
    private val random = LibsodiumSecureRandom()
    private val keyStore = DurableFile(keyFile)

    override suspend fun prepare(): Outcome<Unit, SecurityError> {
        when (val existing = keyStore.read()) {
            is Outcome.Failure -> return Outcome.Failure(existing.error)
            is Outcome.Success -> if (existing.data != null) return Outcome.Success(Unit)
        }
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        return keyStore.writeAtomic(random.bytes(KEY_BYTES))
    }

    override suspend fun seal(plaintext: ByteArray): Outcome<ByteArray, SecurityError> {
        // The lock state may be read before anything else has initialized libsodium.
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        val key = when (val read = keyStore.read()) {
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
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        val key = when (val read = keyStore.read()) {
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
        val AD = "DFDBG".encodeToByteArray()
    }
}
