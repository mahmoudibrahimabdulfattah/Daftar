@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The export facade over the same libsodium adapters as the PIN envelope. Parameters outside the policy are refused.
 */
class PasswordCryptoTest {

    private val crypto = createPasswordCrypto()
    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun argon2idPolicyParametersDeriveA32ByteKey() = runTest {
        val key = assertIs<Outcome.Success<SecretBytes>>(
            crypto.deriveKey("correct horse battery".encodeToByteArray(), salt, KdfPolicy.MEMORY_KIB, 3),
        ).data
        key.use { assertEquals(32, it.size) }
        key.wipe()
    }

    @Test
    fun parametersOutsideThePolicyAreRefusedBeforeAllocation() = runTest {
        // A cheaper memory cost is refused, never silently used as a downgrade.
        val refused = assertIs<Outcome.Failure<SecurityError>>(
            crypto.deriveKey("correct horse battery".encodeToByteArray(), salt, 1024, 3),
        ).error
        assertEquals(SecurityError.UnsupportedFormat, refused)
    }

    @Test
    fun randomBytesHaveTheRequestedLength() = runTest {
        assertEquals(16, assertIs<Outcome.Success<ByteArray>>(crypto.randomBytes(16)).data.size)
        assertEquals(24, assertIs<Outcome.Success<ByteArray>>(crypto.randomBytes(24)).data.size)
    }

    @Test
    fun sealOpensWithTheSameKeyAndAssociatedDataOnly() = runTest {
        val key = assertIs<Outcome.Success<SecretBytes>>(
            crypto.deriveKey("correct horse battery".encodeToByteArray(), salt, KdfPolicy.MEMORY_KIB, 3),
        ).data
        val nonce = assertIs<Outcome.Success<ByteArray>>(crypto.randomBytes(24)).data
        val sealed = crypto.seal(key, nonce, "header".encodeToByteArray(), "payload".encodeToByteArray())
        assertContentEquals(
            "payload".encodeToByteArray(),
            crypto.open(key, nonce, "header".encodeToByteArray(), sealed),
        )
        assertNull(crypto.open(key, nonce, "other".encodeToByteArray(), sealed))
        key.wipe()
    }
}
