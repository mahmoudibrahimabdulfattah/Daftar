@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorGetCode
import platform.CoreFoundation.CFErrorRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAError
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyIsAlgorithmSupported
import platform.Security.SecKeyRef
import platform.Security.SecKeyRef as KeyRef
import platform.Security.errSecAuthFailed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAccessControlPrivateKeyUsage
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrApplicationTag as applicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrTokenID
import platform.Security.kSecAttrTokenIDSecureEnclave
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECIESEncryptionCofactorX963SHA256AESGCM
import platform.Security.kSecKeyOperationTypeDecrypt
import platform.Security.kSecKeyOperationTypeEncrypt
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef
import platform.Security.kSecReturnRef as returnRef
import platform.Security.kSecUseAuthenticationContext

/**
 * The biometric slot's key, held in the Secure Enclave with `biometryCurrentSet`: a new biometric enrollment makes the key
 * unusable, and an old slot can then never unwrap. There is no passcode or presence fallback. Every operation that needs
 * the key runs with a fresh [LAContext].
 *
 * Verified by construction only: the Secure Enclave and the biometric prompt need a physical device.
 */
internal class SecureEnclaveBiometricKeyStore : BiometricKeyStore {

    override suspend fun capability(): BiometricCapability {
        val context = LAContext()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)) {
                return BiometricCapability.Available
            }
            return when (error.value?.code) {
                LAErrorBiometryNotEnrolled -> BiometricCapability.NotEnrolled
                else -> BiometricCapability.Unavailable
            }
        }
    }

    override suspend fun createKey(keyId: ByteArray): Outcome<Unit, BiometricError> {
        if (loadPrivateKey(keyId, null) != null) return Outcome.Failure(BiometricError.Unavailable)
        val accessControl = SecAccessControlCreateWithFlags(
            null,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            kSecAccessControlPrivateKeyUsage or kSecAccessControlBiometryCurrentSet,
            null,
        ) ?: return Outcome.Failure(BiometricError.Unavailable)
        val tag = cfData(tagFor(keyId))
        val privateAttributes =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(privateAttributes, kSecAttrIsPermanent, kCFBooleanTrue)
        CFDictionarySetValue(privateAttributes, applicationTag, tag)
        CFDictionarySetValue(privateAttributes, kSecAttrAccessControl, accessControl)
        val attributes =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionarySetValue(attributes, kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
        CFDictionarySetValue(attributes, kSecPrivateKeyAttrs, privateAttributes)
        val key = SecKeyCreateRandomKey(attributes, null)
        CFRelease(attributes)
        CFRelease(privateAttributes)
        CFRelease(tag)
        CFRelease(accessControl)
        if (key == null) return Outcome.Failure(BiometricError.Unavailable)
        val usable = supportsEncryption(key)
        CFRelease(key)
        return if (usable) Outcome.Success(Unit) else Outcome.Failure(BiometricError.Unavailable)
    }

    override suspend fun deleteKey(keyId: ByteArray): Outcome<Unit, BiometricError> {
        val query =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val tag = cfData(tagFor(keyId))
        CFDictionarySetValue(query, kSecClass, kSecClassKey)
        CFDictionarySetValue(query, applicationTag, tag)
        val status = SecItemDelete(query)
        CFRelease(query)
        CFRelease(tag)
        return if (status == errSecSuccess || status == errSecItemNotFound) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure(BiometricError.Unavailable)
        }
    }

    override suspend fun wrap(
        keyId: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        val publicKey = loadPublicKey(keyId) ?: return Outcome.Failure(BiometricError.EnrollmentChanged)
        // The header travels inside the ciphertext, so decryption proves which vault and DEK the slot belongs to.
        val payload = aad + plaintext
        val input = cfData(payload)
        val output = SecKeyCreateEncryptedData(publicKey, ALGORITHM, input, null)
        CFRelease(input)
        CFRelease(publicKey)
        if (output == null) return Outcome.Failure(BiometricError.Unavailable)
        val sealed = output.toByteArray()
        CFRelease(output)
        // Public-key encryption proves nothing. Enrollment commits only after an authenticated unwrap matches.
        val verified = unwrap(keyId, aad, sealed, prompter)
        return when (verified) {
            is Outcome.Failure -> Outcome.Failure(verified.error)

            is Outcome.Success -> if (verified.data.contentEquals(plaintext)) {
                Outcome.Success(sealed)
            } else {
                Outcome.Failure(BiometricError.CorruptSlot)
            }
        }
    }

    override suspend fun unwrap(
        keyId: ByteArray,
        aad: ByteArray,
        sealed: ByteArray,
        prompter: BiometricPrompter,
    ): Outcome<ByteArray, BiometricError> {
        val context = LAContext()
        context.localizedFallbackTitle = ""
        val crypto = IosBiometricCrypto(context)
        val authorized = prompter.authorize(crypto)
        authorized.failureOrNull()?.let { return Outcome.Failure(it) }
        val privateKey = loadPrivateKey(keyId, context) ?: return Outcome.Failure(BiometricError.EnrollmentChanged)
        val input = cfData(sealed)
        val payload = memScoped {
            val error = alloc<CFErrorRefVar>()
            val output = SecKeyCreateDecryptedData(privateKey, ALGORITHM, input, error.ptr)
            if (output == null) {
                val code = error.value?.let { CFErrorGetCode(it).toInt() }
                CFRelease(input)
                CFRelease(privateKey)
                return Outcome.Failure(code.toBiometricFailure())
            }
            output
        }
        CFRelease(input)
        CFRelease(privateKey)
        context.invalidate()
        val bytes = payload.toByteArray()
        CFRelease(payload)
        if (bytes.size < aad.size || !bytes.copyOfRange(0, aad.size).contentEquals(aad)) {
            return Outcome.Failure(BiometricError.CorruptSlot)
        }
        return Outcome.Success(bytes.copyOfRange(aad.size, bytes.size))
    }

    private fun Int?.toBiometricFailure(): BiometricError = when (this) {
        LAErrorUserCancel.toInt(), LAErrorSystemCancel.toInt(), LAErrorAppCancel.toInt() -> BiometricError.Cancelled
        LAErrorBiometryLockout.toInt() -> BiometricError.LockedOut
        LAErrorBiometryNotEnrolled.toInt() -> BiometricError.NotEnrolled
        LAErrorBiometryNotAvailable.toInt() -> BiometricError.Unavailable
        errSecAuthFailed, errSecItemNotFound -> BiometricError.EnrollmentChanged
        else -> BiometricError.AuthenticationFailed
    }

    private fun loadPrivateKey(keyId: ByteArray, context: LAContext?): SecKeyRef? = memScoped {
        val query =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val tag = cfData(tagFor(keyId))
        CFDictionarySetValue(query, kSecClass, kSecClassKey)
        CFDictionarySetValue(query, applicationTag, tag)
        CFDictionarySetValue(query, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionarySetValue(query, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        CFDictionarySetValue(query, kSecReturnRef, kCFBooleanTrue)
        if (context != null) CFDictionarySetValue(query, kSecUseAuthenticationContext, CFBridgingRetain(context))
        val result = alloc<kotlinx.cinterop.COpaquePointerVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        CFRelease(tag)
        if (status != errSecSuccess) return@memScoped null
        result.value?.let { it.reinterpret() }
    }

    private fun loadPublicKey(keyId: ByteArray): SecKeyRef? {
        val privateKey = loadPrivateKey(keyId, null) ?: return null
        val publicKey = SecKeyCopyPublicKey(privateKey)
        CFRelease(privateKey)
        return publicKey
    }

    private fun supportsEncryption(privateKey: SecKeyRef): Boolean {
        val publicKey = SecKeyCopyPublicKey(privateKey) ?: return false
        val supported = SecKeyIsAlgorithmSupported(publicKey, kSecKeyOperationTypeEncrypt, ALGORITHM) &&
            SecKeyIsAlgorithmSupported(privateKey, kSecKeyOperationTypeDecrypt, ALGORITHM)
        CFRelease(publicKey)
        return supported
    }

    private companion object {
        val ALGORITHM = kSecKeyAlgorithmECIESEncryptionCofactorX963SHA256AESGCM
        const val TAG_PREFIX = "com.mk.daftar.security.biometric.v1."

        fun tagFor(keyId: ByteArray): ByteArray = (
            TAG_PREFIX + keyId.joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
            ).encodeToByteArray()
    }
}
