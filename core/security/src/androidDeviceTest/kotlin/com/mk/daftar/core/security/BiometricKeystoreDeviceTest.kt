package com.mk.daftar.core.security

import android.security.keystore.KeyInfo
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.platform.app.InstrumentationRegistry
import com.mk.daftar.core.common.Outcome
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue

/**
 * The biometric Keystore key on the device under test, with the real platform. Each test creates its own key and
 * deletes it. Authentication itself needs a finger, so these tests prove the key refuses unauthenticated use and that a
 * prompter cannot fake success. The emulator flow in the verification notes covers the real prompt.
 */
class BiometricKeystoreDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val policy = KeystorePolicy.AllowSoftwareForDebug

    /** A prompter that claims success without any biometric check. The key must still refuse. */
    private class CheatingPrompter : BiometricPrompter {
        override suspend fun authorize(crypto: BiometricCrypto): BiometricAuthResult =
            BiometricAuthResult.Authenticated(crypto)
    }

    private fun keyIdentifier(): ByteArray = ByteArray(16) { (it * 7 + 3).toByte() }

    private fun keyFor(keyId: ByteArray): SecretKey? = KeyStore.getInstance("AndroidKeyStore")
        .apply { load(null) }
        .getKey(
            "com.mk.daftar.security.biometric.v1." + keyId.joinToString("") {
                "%02x".format(it)
            },
            null,
        ) as? SecretKey

    @Test
    fun theKeyRequiresStrongBiometricAuthenticationForEveryUse() = runTest {
        val store = AndroidBiometricKeyStore(context, policy)
        val keyId = keyIdentifier()
        store.deleteKey(keyId)
        val created = store.createKey(keyId)
        // Without an enrolled lock screen the platform refuses the key. That is reported, not treated as a pass.
        assumeTrue("the device has no secure lock screen", created is Outcome.Success)
        try {
            val key = assertIs<SecretKey>(keyFor(keyId))
            val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            assertTrue(info.isUserAuthenticationRequired, "every use must be authenticated")
            assertTrue(info.isInvalidatedByBiometricEnrollment, "enrollment changes must invalidate the key")

            // The key opens for a cipher, but no data flows until the platform has authenticated it.
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
            // Per-use keys report the missing authentication as UserNotAuthenticatedException on older releases and
            // as IllegalBlockSizeException (wrapping a KeyStoreException) on newer ones; either way nothing is encrypted.
            val failure = assertFails { cipher.doFinal(ByteArray(32)) }
            assertTrue(
                failure is UserNotAuthenticatedException || failure is IllegalBlockSizeException,
                "unauthenticated use must fail, got ${failure::class.simpleName}",
            )
        } finally {
            store.deleteKey(keyId)
        }
        assertEquals(null, keyFor(keyId))
    }

    @Test
    fun aPrompterThatSkipsTheCheckCannotWrapOrUnwrap() = runTest {
        val store = AndroidBiometricKeyStore(context, policy)
        val keyId = keyIdentifier()
        store.deleteKey(keyId)
        assumeTrue("the device has no secure lock screen", store.createKey(keyId) is Outcome.Success)
        try {
            val aad = ByteArray(BiometricSlotCodec.AAD_BYTES) { 5 }
            val wrapped = store.wrap(keyId, aad, ByteArray(32) { 9 }, CheatingPrompter())
            assertEquals(BiometricError.AuthenticationFailed, (wrapped as Outcome.Failure).error)
        } finally {
            store.deleteKey(keyId)
        }
    }

    @Test
    fun aCancelledPromptNeverReachesTheKey() = runTest {
        val store = AndroidBiometricKeyStore(context, policy)
        val keyId = keyIdentifier()
        store.deleteKey(keyId)
        assumeTrue("the device has no secure lock screen", store.createKey(keyId) is Outcome.Success)
        try {
            val cancelled = object : BiometricPrompter {
                override suspend fun authorize(crypto: BiometricCrypto): BiometricAuthResult =
                    BiometricAuthResult.Cancelled
            }
            val result = store.unwrap(keyId, ByteArray(BiometricSlotCodec.AAD_BYTES), ByteArray(60), cancelled)
            assertEquals(BiometricError.Cancelled, (result as Outcome.Failure).error)
        } finally {
            store.deleteKey(keyId)
        }
    }

    @Test
    fun aMissingKeyReportsEnrollmentChangedAndDeleteIsIdempotent() = runTest {
        val store = AndroidBiometricKeyStore(context, policy)
        val keyId = keyIdentifier()
        store.deleteKey(keyId)
        val result = store.unwrap(keyId, ByteArray(BiometricSlotCodec.AAD_BYTES), ByteArray(60), CheatingPrompter())
        assertEquals(BiometricError.EnrollmentChanged, (result as Outcome.Failure).error)
        assertIs<Outcome.Success<Unit>>(store.deleteKey(keyId))
    }
}
