package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlinx.coroutines.CancellationException

/**
 * Password-based primitives for encrypted exports. It is a narrow facade over the same libsodium adapters as the PIN
 * envelope, so no second cipher implementation exists. Callers never receive a PIN or a vault key from it.
 *
 * Key derivation uses the Argon2id v1.3 policy checked by [KdfPolicy]: the memory and pass count are validated before
 * any allocation, and a failure is reported, never silently downgraded.
 */
class PasswordCrypto internal constructor(
    private val kdf: PinKdf,
    private val aead: Aead,
    private val random: SecureRandom,
) {
    /** Argon2id v1.3 of [password] (UTF-8 bytes). The caller keeps ownership of [password] and wipes it. */
    suspend fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        passes: Int,
    ): Outcome<SecretBytes, SecurityError> = kdf.derive(password, salt, KdfParams(memoryKiB, passes, KdfPolicy.LANES))

    /** Bytes from the platform CSPRNG. Used for fresh salts and nonces only. */
    suspend fun randomBytes(size: Int): Outcome<ByteArray, SecurityError> {
        require(size in 1..MAX_RANDOM_BYTES) { "random size out of range" }
        return when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> Outcome.Failure(ready.error)

            is Outcome.Success -> try {
                Outcome.Success(random.bytes(size))
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Outcome.Failure(SecurityError.ResourceUnavailable)
            }
        }
    }

    /** XChaCha20-Poly1305-IETF seal. Returns ciphertext with the 16-byte tag appended. */
    fun seal(key: SecretBytes, nonce: ByteArray, associatedData: ByteArray, plaintext: ByteArray): ByteArray =
        key.use { aead.seal(it, nonce, associatedData, plaintext) }

    /** Returns the plaintext, or null when authentication fails. Wrong keys and tampered bytes are not told apart. */
    fun open(key: SecretBytes, nonce: ByteArray, associatedData: ByteArray, ciphertext: ByteArray): ByteArray? =
        key.use { aead.open(it, nonce, associatedData, ciphertext) }

    /** [seal] with a random content key the caller owns and wipes, such as a backup's own key. */
    fun sealWithKey(key: ByteArray, nonce: ByteArray, associatedData: ByteArray, plaintext: ByteArray): ByteArray =
        aead.seal(key, nonce, associatedData, plaintext)

    /** [open] with a content key the caller owns and wipes. */
    fun openWithKey(key: ByteArray, nonce: ByteArray, associatedData: ByteArray, ciphertext: ByteArray): ByteArray? =
        aead.open(key, nonce, associatedData, ciphertext)

    /**
     * Seals [message] to a recovery public key, so only the holder of that recovery key can open it. Used for the key
     * of a backup, never for a PIN or the vault key.
     */
    suspend fun sealToRecovery(message: ByteArray, publicKey: ByteArray): Outcome<ByteArray, SecurityError> =
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> Outcome.Failure(ready.error)

            is Outcome.Success -> try {
                Outcome.Success(sealToRecoveryKey(message, publicKey))
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Outcome.Failure(SecurityError.ResourceUnavailable)
            }
        }

    /**
     * Opens a box sealed by [sealToRecovery] with the typed [recoveryKey]. Fails with
     * [SecurityError.RecoveryKeyRejected] when the key is malformed or is not the one the box was sealed to.
     */
    suspend fun openWithRecovery(sealed: ByteArray, recoveryKey: String): Outcome<ByteArray, SecurityError> {
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        val secret = RecoveryKeyFormat.parse(recoveryKey) ?: return Outcome.Failure(SecurityError.RecoveryKeyRejected)
        val pair = RecoveryKeyPair.derive(secret)
        secret.fill(0)
        return try {
            pair.open(sealed)?.let { Outcome.Success(it) } ?: Outcome.Failure(SecurityError.RecoveryKeyRejected)
        } finally {
            pair.wipe()
        }
    }

    /** The public key of [recoveryKey], or null when it is malformed. The secret half is wiped at once. */
    suspend fun recoveryPublicKeyOf(recoveryKey: String): ByteArray? {
        if (Libsodium.ensureReady() is Outcome.Failure) return null
        val secret = RecoveryKeyFormat.parse(recoveryKey) ?: return null
        val pair = RecoveryKeyPair.derive(secret)
        secret.fill(0)
        pair.wipe()
        return pair.publicKey
    }

    private companion object {
        /** Largest random block requested through this facade. Salts and nonces are far below it. */
        const val MAX_RANDOM_BYTES = 64
    }
}

/**
 * The Argon2id step behind [PasswordCrypto]. Production uses [LibsodiumPasswordKdf]. Tests substitute it to simulate an
 * allocation failure at chosen parameters, so the export fallback can be exercised without exhausting memory.
 */
fun interface PasswordKdf {
    suspend fun derive(
        password: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        passes: Int,
    ): Outcome<SecretBytes, SecurityError>
}

/** The libsodium Argon2id step, with the same allowlist and allocation-failure mapping as the PIN envelope. */
val LibsodiumPasswordKdf: PasswordKdf = PasswordKdf { password, salt, memoryKiB, passes ->
    LibsodiumPinKdf().derive(password, salt, KdfParams(memoryKiB, passes, KdfPolicy.LANES))
}

/** A password-crypto facade backed by libsodium. Libsodium initializes on first use and is reported when it fails. */
fun createPasswordCrypto(kdf: PasswordKdf = LibsodiumPasswordKdf): PasswordCrypto = PasswordCrypto(
    kdf = object : PinKdf {
        override suspend fun derive(
            pin: ByteArray,
            salt: ByteArray,
            params: KdfParams,
        ): Outcome<SecretBytes, SecurityError> = kdf.derive(pin, salt, params.memoryKiB, params.passes)
    },
    aead = LibsodiumAead(),
    random = LibsodiumSecureRandom(),
)
