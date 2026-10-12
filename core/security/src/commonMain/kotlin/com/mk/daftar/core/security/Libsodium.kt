@file:OptIn(ExperimentalUnsignedTypes::class)

package com.mk.daftar.core.security

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.aead.AeadCorrupedOrTamperedDataException
import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.PasswordHashingFailed
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_argon2id_ALG_ARGON2ID13
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import com.mk.daftar.core.common.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One-time libsodium initialization. Every native failure is reported, never ignored. */
internal object Libsodium {
    private val mutex = Mutex()
    private var ready = false

    suspend fun ensureReady(): Outcome<Unit, SecurityError> = mutex.withLock {
        if (ready) return Outcome.Success(Unit)
        try {
            if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
            ready = true
            Outcome.Success(Unit)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Outcome.Failure(SecurityError.ResourceUnavailable)
        }
    }
}

/**
 * Argon2id v1.3 through libsodium's `crypto_pwhash`, pinned to the ARGON2ID13 algorithm and never the algorithm
 * default. The derivation runs on [dispatcher], off the caller's thread.
 *
 * The binding takes the password as a Kotlin String, so the PIN is copied into a String that cannot be wiped. The
 * PIN is only ASCII digits, and the copy lives until the garbage collector reclaims it. This is a known limitation of
 * the binding and is documented rather than hidden.
 */
internal class LibsodiumPinKdf(private val dispatcher: CoroutineDispatcher = Dispatchers.Default) : PinKdf {
    override suspend fun derive(
        pin: ByteArray,
        salt: ByteArray,
        params: KdfParams,
    ): Outcome<SecretBytes, SecurityError> {
        if (!KdfPolicy.allows(params)) return Outcome.Failure(SecurityError.UnsupportedFormat)
        if (salt.size != KdfPolicy.SALT_BYTES) return Outcome.Failure(SecurityError.CorruptState)
        when (val ready = Libsodium.ensureReady()) {
            is Outcome.Failure -> return Outcome.Failure(ready.error)
            is Outcome.Success -> Unit
        }
        return withContext(dispatcher) {
            val password = pin.decodeToString()
            val saltUnsigned = salt.toUnsigned()
            try {
                val output = PasswordHash.pwhash(
                    KdfPolicy.OUTPUT_BYTES,
                    password,
                    saltUnsigned,
                    params.passes.toULong(),
                    params.memoryKiB * 1024,
                    crypto_pwhash_argon2id_ALG_ARGON2ID13,
                )
                val key = output.toSigned()
                output.fill(0u)
                Outcome.Success(SecretBytes.owned(key))
            } catch (failure: PasswordHashingFailed) {
                Outcome.Failure(SecurityError.ResourceUnavailable)
            } finally {
                saltUnsigned.fill(0u)
            }
        }
    }
}

/** XChaCha20-Poly1305-IETF with the 192-bit nonce from the platform RNG. The associated data is authenticated only. */
internal class LibsodiumAead : Aead {
    override fun seal(key: ByteArray, nonce: ByteArray, associatedData: ByteArray, plaintext: ByteArray): ByteArray {
        val message = plaintext.toUnsigned()
        val ad = associatedData.toUnsigned()
        val keyUnsigned = key.toUnsigned()
        val nonceUnsigned = nonce.toUnsigned()
        try {
            val sealed = AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfEncrypt(
                message,
                ad,
                nonceUnsigned,
                keyUnsigned,
            )
            return sealed.toSigned()
        } finally {
            message.fill(0u)
            keyUnsigned.fill(0u)
        }
    }

    override fun open(key: ByteArray, nonce: ByteArray, associatedData: ByteArray, ciphertext: ByteArray): ByteArray? {
        val keyUnsigned = key.toUnsigned()
        try {
            val opened = AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfDecrypt(
                ciphertext.toUnsigned(),
                associatedData.toUnsigned(),
                nonce.toUnsigned(),
                keyUnsigned,
            )
            val bytes = opened.toSigned()
            opened.fill(0u)
            return bytes
        } catch (failure: AeadCorrupedOrTamperedDataException) {
            return null
        } finally {
            keyUnsigned.fill(0u)
        }
    }
}

/** Platform CSPRNG via libsodium `randombytes_buf`. Requires [Libsodium.ensureReady] to have succeeded. */
internal class LibsodiumSecureRandom : SecureRandom {
    override fun bytes(size: Int): ByteArray {
        val raw = LibsodiumRandom.buf(size)
        val bytes = raw.toSigned()
        raw.fill(0u)
        return bytes
    }
}

internal fun ByteArray.toUnsigned(): UByteArray = UByteArray(size) { this[it].toUByte() }

internal fun UByteArray.toSigned(): ByteArray = ByteArray(size) { this[it].toByte() }
