package com.mk.daftar.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.mk.daftar.core.common.Outcome
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How strictly the hardware security level of the key is checked. Production always uses [RequireHardware]. */
internal enum class KeystorePolicy {
    /** StrongBox or a hardware-backed TEE. Software-backed keys are refused. */
    RequireHardware,

    /**
     * Debug builds only, on emulators whose keys are software-backed. Selected by [resolveKeystorePolicy] when the
     * configuration asks for it and the installed application is debuggable. Release builds never reach this value.
     */
    AllowSoftwareForDebug,
}

internal enum class KeystoreLevel {
    StrongBox,
    TrustedEnvironment,
    Software,
    Unknown,
}

/**
 * Seals with a non-exportable AES-256-GCM key in the Android Keystore. The key is preferred in StrongBox and falls
 * back to the TEE. The IV is generated fresh by the Keystore for every seal. Sealed layout: IV length (1 byte, 12)
 * | IV | ciphertext and tag.
 *
 * A missing or invalidated key is [SecurityError.DeviceKeyUnavailable], and a replacement is never generated here.
 */
internal class AndroidKeystoreProtector(private val alias: String, private val policy: KeystorePolicy) :
    PlatformEnvelopeProtector {

    override suspend fun prepare(): Outcome<Unit, SecurityError> = withContext(Dispatchers.IO) {
        try {
            val existing = loadKey()
            if (existing != null) {
                if (acceptable(
                        levelOf(existing),
                    )
                ) {
                    Outcome.Success(Unit)
                } else {
                    Outcome.Failure(SecurityError.UnsupportedHardware)
                }
            } else {
                val created = generateKey()
                val level = levelOf(created)
                if (acceptable(level)) {
                    Outcome.Success(Unit)
                } else {
                    deleteKey()
                    Outcome.Failure(SecurityError.UnsupportedHardware)
                }
            }
        } catch (failure: GeneralSecurityException) {
            Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        } catch (failure: RuntimeException) {
            Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        }
    }

    override suspend fun seal(plaintext: ByteArray): Outcome<ByteArray, SecurityError> = withContext(Dispatchers.IO) {
        val key = when (val usable = usableKey()) {
            is Outcome.Failure -> return@withContext Outcome.Failure(usable.error)
            is Outcome.Success -> usable.data
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val body = cipher.doFinal(plaintext)
            Outcome.Success(byteArrayOf(iv.size.toByte()) + iv + body)
        } catch (failure: GeneralSecurityException) {
            Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        }
    }

    override suspend fun unseal(sealed: ByteArray): Outcome<ByteArray, SecurityError> = withContext(Dispatchers.IO) {
        if (sealed.size <= 1 + GCM_IV_BYTES) return@withContext Outcome.Failure(SecurityError.CorruptState)
        if ((sealed[0].toInt() and 0xFF) != GCM_IV_BYTES) return@withContext Outcome.Failure(SecurityError.CorruptState)
        val key = when (val usable = usableKey()) {
            is Outcome.Failure -> return@withContext Outcome.Failure(usable.error)
            is Outcome.Success -> usable.data
        }
        val iv = sealed.copyOfRange(1, 1 + GCM_IV_BYTES)
        val body = sealed.copyOfRange(1 + GCM_IV_BYTES, sealed.size)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            Outcome.Success(cipher.doFinal(body))
        } catch (failure: KeyPermanentlyInvalidatedException) {
            Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        } catch (failure: AEADBadTagException) {
            Outcome.Failure(SecurityError.CorruptState)
        } catch (failure: InvalidKeyException) {
            Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        } catch (failure: UnrecoverableKeyException) {
            Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        } catch (failure: GeneralSecurityException) {
            Outcome.Failure(SecurityError.CorruptState)
        }
    }

    /** The key's hardware level, for device tests and diagnostics. */
    suspend fun securityLevel(): Outcome<KeystoreLevel, SecurityError> = withContext(Dispatchers.IO) {
        val key = loadKey() ?: return@withContext Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        Outcome.Success(levelOf(key))
    }

    /** Removes the key. Used only by device tests to clean up their own aliases. */
    suspend fun deleteKeyForTests() = withContext(Dispatchers.IO) { deleteKey() }

    private fun usableKey(): Outcome<SecretKey, SecurityError> {
        val key = try {
            loadKey()
        } catch (failure: GeneralSecurityException) {
            return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        } catch (failure: RuntimeException) {
            return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        } ?: return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        return if (acceptable(
                levelOf(key),
            )
        ) {
            Outcome.Success(key)
        } else {
            Outcome.Failure(SecurityError.UnsupportedHardware)
        }
    }

    private fun acceptable(level: KeystoreLevel): Boolean = policy.accepts(level)

    private fun loadKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun deleteKey() {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generator.init(keySpec(strongBox = true))
                return generator.generateKey()
            } catch (unavailable: Exception) {
                // StrongBox is absent or refused this key. The TEE below is the next hardware option.
            }
        }
        generator.init(keySpec(strongBox = false))
        return generator.generateKey()
    }

    private fun keySpec(strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .setInvalidatedByBiometricEnrollment(false)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    private fun levelOf(key: SecretKey): KeystoreLevel = keystoreLevelOf(key)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}

/** The hardware level the platform reports for [key]. */
internal fun keystoreLevelOf(key: SecretKey): KeystoreLevel {
    val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
        .getKeySpec(key, KeyInfo::class.java) as KeyInfo
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeystoreLevel.StrongBox
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeystoreLevel.TrustedEnvironment
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeystoreLevel.Software
            else -> KeystoreLevel.Unknown
        }
    } else {
        @Suppress("DEPRECATION")
        if (info.isInsideSecureHardware) KeystoreLevel.TrustedEnvironment else KeystoreLevel.Software
    }
}

/** Production accepts only hardware-backed levels. Software keys are accepted only under the debug policy. */
internal fun KeystorePolicy.accepts(level: KeystoreLevel): Boolean = when (this) {
    KeystorePolicy.RequireHardware -> level == KeystoreLevel.StrongBox || level == KeystoreLevel.TrustedEnvironment
    KeystorePolicy.AllowSoftwareForDebug -> true
}
