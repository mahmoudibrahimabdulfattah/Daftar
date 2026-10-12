@file:OptIn(ExperimentalForeignApi::class)

package com.mk.daftar.core.security

import com.mk.daftar.core.common.Outcome
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberSInt32Type
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCopyPublicKey as copyPublicKey
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyIsAlgorithmSupported
import platform.Security.SecKeyRef
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAccessControlPrivateKeyUsage
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeySizeInBits
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

/**
 * Seals the PIN envelope with a non-exportable P-256 key in the Secure Enclave, using Security.framework ECIES
 * (cofactor, X9.63, SHA-256, AES-GCM). The key is device-only, never synchronized, and has no biometric or
 * presence requirement, so the app PIN is the only gate on the PIN path. A missing key is
 * [SecurityError.DeviceKeyUnavailable], and [prepare] creates one only when none exists.
 */
internal class SecureEnclaveProtector(keyTag: String) : PlatformEnvelopeProtector {
    private val tag = keyTag.encodeToByteArray()

    override suspend fun prepare(): Outcome<Unit, SecurityError> {
        val existing = loadPrivateKey()
        if (existing != null) {
            val supported = supportsEncryption(existing)
            CFRelease(existing)
            return if (supported) Outcome.Success(Unit) else Outcome.Failure(SecurityError.UnsupportedHardware)
        }
        return createPrivateKey()
    }

    override suspend fun seal(plaintext: ByteArray): Outcome<ByteArray, SecurityError> {
        val privateKey = loadPrivateKey() ?: return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        val publicKey = copyPublicKey(privateKey)
        CFRelease(privateKey)
        if (publicKey == null) return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        val input = cfData(plaintext)
        val output = SecKeyCreateEncryptedData(publicKey, ALGORITHM, input, null)
        CFRelease(input)
        CFRelease(publicKey)
        if (output == null) return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        val bytes = output.toByteArray()
        CFRelease(output)
        return Outcome.Success(bytes)
    }

    override suspend fun unseal(sealed: ByteArray): Outcome<ByteArray, SecurityError> {
        val privateKey = loadPrivateKey() ?: return Outcome.Failure(SecurityError.DeviceKeyUnavailable)
        val input = cfData(sealed)
        val output = SecKeyCreateDecryptedData(privateKey, ALGORITHM, input, null)
        CFRelease(input)
        CFRelease(privateKey)
        if (output == null) return Outcome.Failure(SecurityError.CorruptState)
        val bytes = output.toByteArray()
        CFRelease(output)
        return Outcome.Success(bytes)
    }

    private fun supportsEncryption(privateKey: SecKeyRef): Boolean {
        val publicKey = copyPublicKey(privateKey) ?: return false
        val supported = SecKeyIsAlgorithmSupported(publicKey, kSecKeyOperationTypeEncrypt, ALGORITHM) &&
            SecKeyIsAlgorithmSupported(privateKey, kSecKeyOperationTypeDecrypt, ALGORITHM)
        CFRelease(publicKey)
        return supported
    }

    private fun createPrivateKey(): Outcome<Unit, SecurityError> = memScoped {
        val accessControl = SecAccessControlCreateWithFlags(
            null,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            kSecAccessControlPrivateKeyUsage,
            null,
        ) ?: return@memScoped Outcome.Failure(SecurityError.UnsupportedHardware)
        val tagData = cfData(tag)
        val privateAttributes =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(privateAttributes, kSecAttrIsPermanent, kCFBooleanTrue)
        CFDictionarySetValue(privateAttributes, kSecAttrApplicationTag, tagData)
        CFDictionarySetValue(privateAttributes, kSecAttrAccessControl, accessControl)
        val keySize = alloc<IntVar>()
        keySize.value = KEY_BITS
        val keySizeNumber = CFNumberCreate(null, kCFNumberSInt32Type, keySize.ptr)
        val attributes =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionarySetValue(attributes, kSecAttrKeySizeInBits, keySizeNumber)
        CFDictionarySetValue(attributes, kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
        CFDictionarySetValue(attributes, kSecPrivateKeyAttrs, privateAttributes)
        val key = SecKeyCreateRandomKey(attributes, null)
        CFRelease(attributes)
        CFRelease(privateAttributes)
        CFRelease(keySizeNumber)
        CFRelease(tagData)
        CFRelease(accessControl)
        if (key == null) return@memScoped Outcome.Failure(SecurityError.UnsupportedHardware)
        val usable = supportsEncryption(key)
        CFRelease(key)
        if (usable) Outcome.Success(Unit) else Outcome.Failure(SecurityError.UnsupportedHardware)
    }

    private fun loadPrivateKey(): SecKeyRef? = memScoped {
        val query = keyQuery()
        CFDictionarySetValue(query, kSecReturnRef, kCFBooleanTrue)
        val result = alloc<COpaquePointerVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        result.value?.reinterpret()
    }

    private fun keyQuery(): CFMutableDictionaryRef? {
        val query =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val tagData = cfData(tag)
        CFDictionarySetValue(query, kSecClass, kSecClassKey)
        CFDictionarySetValue(query, kSecAttrApplicationTag, tagData)
        CFDictionarySetValue(query, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionarySetValue(query, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        CFRelease(tagData)
        return query
    }

    companion object {
        /** The application tag of the legacy generation's key. */
        const val LEGACY_KEY_TAG = "com.mk.daftar.security.pin-envelope.secure-enclave.v1"

        const val KEY_BITS = 256
        val ALGORITHM = kSecKeyAlgorithmECIESEncryptionCofactorX963SHA256AESGCM
    }
}

/** Removes the Secure Enclave key with [keyTag]. Idempotent: a missing key is success. */
internal fun deleteSecureEnclaveKey(keyTag: String): Outcome<Unit, SecurityError> {
    val query =
        CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    val tagData = cfData(keyTag.encodeToByteArray())
    CFDictionarySetValue(query, kSecClass, kSecClassKey)
    CFDictionarySetValue(query, kSecAttrApplicationTag, tagData)
    CFDictionarySetValue(query, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
    CFDictionarySetValue(query, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
    val status = SecItemDelete(query)
    CFRelease(tagData)
    CFRelease(query)
    return if (status == errSecSuccess || status == errSecItemNotFound) {
        Outcome.Success(Unit)
    } else {
        Outcome.Failure(SecurityError.PersistenceFailure)
    }
}
