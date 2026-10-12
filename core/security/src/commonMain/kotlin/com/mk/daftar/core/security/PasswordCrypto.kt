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

    private companion object {
        /** Largest random block requested through this facade. Salts and nonces are far below it. */
        const val MAX_RANDOM_BYTES = 64
    }
}

/** A password-crypto facade backed by libsodium. Libsodium initializes on first use and is reported when it fails. */
fun createPasswordCrypto(): PasswordCrypto = PasswordCrypto(
    kdf = LibsodiumPinKdf(),
    aead = LibsodiumAead(),
    random = LibsodiumSecureRandom(),
)
