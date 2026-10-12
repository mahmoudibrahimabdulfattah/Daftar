package com.mk.daftar.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import com.mk.daftar.core.common.Outcome
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The platform crypto for one biometric operation: an initialized AES-GCM [Cipher] for the Keystore key. The
 * BiometricPrompt adapter authorizes this exact cipher through its `CryptoObject`.
 */
class AndroidBiometricCrypto(val cipher: Cipher) : BiometricCrypto

/**
 * AES-256-GCM keys in the Android Keystore that require strong biometric authentication for every use. Each key is
 * invalidated when biometric enrollment changes, so a changed fingerprint set can never unwrap an old slot.
 *
 * Keys are created only by explicit enrollment, never regenerated silently. Production accepts StrongBox or a
 * hardware-backed TEE, and only the debug policy accepts software keys.
 */
internal class AndroidBiometricKeyStore(private val context: Context, private val policy: KeystorePolicy) :
    BiometricKeyStore {

    override suspend fun capability(): BiometricCapability = withContext(Dispatchers.Default) {
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NotEnrolled
            else -> BiometricCapability.Unavailable
        }
    }

    override suspend fun createKey(keyId: ByteArray): Outcome<Unit, BiometricError> = withContext(Dispatchers.IO) {
        val alias = aliasFor(keyId)
        try {
            if (keyStore().getKey(alias, null) != null) return@withContext Outcome.Failure(BiometricError.Unavailable)
            val key = generate(alias)
            if (policy.accepts(keystoreLevelOf(key))) {
                Outcome.Success(Unit)
            } else {
                keyStore().deleteEntry(alias)
                Outcome.Failure(BiometricError.Unavailable)
            }
        } catch (failure: InvalidAlgorithmParameterException) {
            // The key requires a secure lock screen and enrolled biometrics, which the device does not provide.
            Outcome.Failure(BiometricError.NotEnrolled)
        } catch (failure: GeneralSecurityException) {
            Outcome.Failure(BiometricError.Unavailable)
        } catch (failure: RuntimeException) {
            Outcome.Failure(BiometricError.Unavailable)
        }
    }

    override suspend fun deleteKey(keyId: ByteArray): Outcome<Unit, BiometricError> = withContext(Dispatchers.IO) {
        try {
            keyStore().deleteEntry(aliasFor(keyId))
            Outcome.Success(Unit)
        } catch (failure: GeneralSecurityException) {
            Outcome.Failure(BiometricError.Unavailable)
        } catch (failure: RuntimeException) {
            Outcome.Failure(BiometricError.Unavailable)
        }
    }

    override suspend fun wrap(
        keyId: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> = withContext(Dispatchers.IO) {
        val key = loadKey(keyId) ?: return@withContext Outcome.Failure(BiometricError.EnrollmentChanged)
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (failure: KeyPermanentlyInvalidatedException) {
            return@withContext Outcome.Failure(BiometricError.EnrollmentChanged)
        } catch (failure: GeneralSecurityException) {
            return@withContext Outcome.Failure(BiometricError.Unavailable)
        }
        val result = prompter.authorize(AndroidBiometricCrypto(cipher))
        result.failureOrNull()?.let { return@withContext Outcome.Failure(it) }
        val authorized = ((result as BiometricAuthResult.Authenticated).crypto as AndroidBiometricCrypto).cipher
        try {
            authorized.updateAAD(aad)
            val body = authorized.doFinal(plaintext)
            Outcome.Success(authorized.iv + body)
        } catch (failure: KeyPermanentlyInvalidatedException) {
            Outcome.Failure(BiometricError.EnrollmentChanged)
        } catch (failure: GeneralSecurityException) {
            Outcome.Failure(BiometricError.AuthenticationFailed)
        }
    }

    override suspend fun unwrap(
        keyId: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> = withContext(Dispatchers.IO) {
        if (sealed.size <= GCM_IV_BYTES) return@withContext Outcome.Failure(BiometricError.CorruptSlot)
        val key = loadKey(keyId) ?: return@withContext Outcome.Failure(BiometricError.EnrollmentChanged)
        val iv = sealed.copyOfRange(0, GCM_IV_BYTES)
        val body = sealed.copyOfRange(GCM_IV_BYTES, sealed.size)
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        } catch (failure: KeyPermanentlyInvalidatedException) {
            return@withContext Outcome.Failure(BiometricError.EnrollmentChanged)
        } catch (failure: InvalidKeyException) {
            return@withContext Outcome.Failure(BiometricError.Unavailable)
        } catch (failure: GeneralSecurityException) {
            return@withContext Outcome.Failure(BiometricError.Unavailable)
        }
        val result = prompter.authorize(AndroidBiometricCrypto(cipher))
        result.failureOrNull()?.let { return@withContext Outcome.Failure(it) }
        val authorized = ((result as BiometricAuthResult.Authenticated).crypto as AndroidBiometricCrypto).cipher
        try {
            authorized.updateAAD(aad)
            Outcome.Success(authorized.doFinal(body))
        } catch (failure: KeyPermanentlyInvalidatedException) {
            Outcome.Failure(BiometricError.EnrollmentChanged)
        } catch (failure: AEADBadTagException) {
            Outcome.Failure(BiometricError.CorruptSlot)
        } catch (failure: GeneralSecurityException) {
            Outcome.Failure(BiometricError.AuthenticationFailed)
        }
    }

    private fun loadKey(keyId: ByteArray): SecretKey? = try {
        keyStore().getKey(aliasFor(keyId), null) as? SecretKey
    } catch (failure: GeneralSecurityException) {
        null
    } catch (failure: RuntimeException) {
        null
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun generate(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generator.init(keySpec(alias, strongBox = true))
                return generator.generateKey()
            } catch (unavailable: Exception) {
                // StrongBox is absent or refused this key. The TEE below is the next hardware option.
            }
        }
        generator.init(keySpec(alias, strongBox = false))
        return generator.generateKey()
    }

    private fun keySpec(alias: String, strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Authentication is required for every use, and only strong biometrics satisfy it.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val ALIAS_PREFIX = "com.mk.daftar.security.biometric.v1."

        fun aliasFor(keyId: ByteArray): String = ALIAS_PREFIX + keyId.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}
